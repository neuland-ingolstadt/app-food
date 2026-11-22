package app.neuland.application;

import app.neuland.model.food.Meal;
import app.neuland.model.food.MealDay;
import app.neuland.model.food.Restaurant;
import app.neuland.ports.inbound.FoodQueryUseCase;
import app.neuland.ports.inbound.FoodRefreshUseCase;
import app.neuland.ports.outbound.FoodCacheRepository;
import app.neuland.ports.outbound.MenuSource;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Serves meals from the cache and falls back to the {@link MenuSource} of a restaurant on a cache
 * miss. Sources already return normalized meal days.
 */
public class FoodQueryService implements FoodQueryUseCase, FoodRefreshUseCase, AutoCloseable {

    private static final Logger LOG = Logger.getLogger(FoodQueryService.class.getName());

    private final FoodCacheRepository cache;
    private final Map<Restaurant, MenuSource> sources = new EnumMap<>(Restaurant.class);
    private final Map<Restaurant, ReentrantLock> locks = new EnumMap<>(Restaurant.class);
    private final Duration cacheTtl;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public FoodQueryService(FoodCacheRepository cache, Collection<MenuSource> menuSources, Duration cacheTtl) {
        this.cache = cache;
        this.cacheTtl = cacheTtl;
        for (MenuSource source : menuSources) {
            MenuSource previous = sources.put(source.restaurant(), source);
            if (previous != null) {
                LOG.warning("Multiple menu sources for " + source.restaurant() + ", using "
                        + source.getClass().getName());
            }
        }
        for (Restaurant restaurant : Restaurant.values()) {
            locks.put(restaurant, new ReentrantLock());
        }
    }

    public FoodQueryService(FoodCacheRepository cache, Collection<MenuSource> menuSources) {
        this(cache, menuSources, FoodCacheRepository.DEFAULT_TTL);
    }

    @Override
    public List<MealDay> listMeals(List<Restaurant> restaurants, LocalDate from, LocalDate to) {
        List<Restaurant> distinct = List.copyOf(new LinkedHashSet<>(restaurants));

        // load all restaurants concurrently, keep the requested order for the merge
        Map<Restaurant, CompletableFuture<List<MealDay>>> futures = new LinkedHashMap<>();
        for (Restaurant restaurant : distinct) {
            futures.put(restaurant, CompletableFuture.supplyAsync(() -> load(restaurant), executor));
        }

        TreeMap<LocalDate, List<Meal>> mealsByDate = new TreeMap<>();
        futures.forEach((restaurant, future) -> {
            for (MealDay day : future.join()) {
                if (day.date().isBefore(from) || day.date().isAfter(to)) {
                    continue;
                }
                mealsByDate.computeIfAbsent(day.date(), date -> new ArrayList<>()).addAll(day.meals());
            }
        });

        return mealsByDate.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .map(entry -> new MealDay(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
    }

    @Override
    public Optional<Meal> getMeal(String mealId) {
        for (Restaurant restaurant : Restaurant.values()) {
            for (MealDay day : load(restaurant)) {
                for (Meal meal : day.meals()) {
                    if (meal.id().equals(mealId)) {
                        return Optional.of(meal);
                    }
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public void refreshAll() {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Restaurant restaurant : Restaurant.values()) {
            futures.add(CompletableFuture.runAsync(() -> refresh(restaurant), executor));
        }
        futures.forEach(CompletableFuture::join);
    }

    /** Fetches the menu from the source and updates the cache (also if the cache is still warm). */
    void refresh(Restaurant restaurant) {
        ReentrantLock lock = locks.get(restaurant);
        lock.lock();
        try {
            fetchAndCache(restaurant);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the menu of the restaurant from cache, or fetches it from the source. A failing source
     * yields an empty (uncached) menu so that one broken restaurant does not break the others.
     */
    private List<MealDay> load(Restaurant restaurant) {
        Optional<List<MealDay>> cached = readCache(restaurant);
        if (cached.isPresent()) {
            return cached.get();
        }
        ReentrantLock lock = locks.get(restaurant);
        lock.lock();
        try {
            // somebody else may have filled the cache while we were waiting
            cached = readCache(restaurant);
            if (cached.isPresent()) {
                return cached.get();
            }
            return fetchAndCache(restaurant);
        } finally {
            lock.unlock();
        }
    }

    private List<MealDay> fetchAndCache(Restaurant restaurant) {
        MenuSource source = sources.get(restaurant);
        if (source == null) {
            LOG.warning("No menu source configured for " + restaurant);
            return List.of();
        }
        try {
            List<MealDay> days = List.copyOf(source.fetch());
            writeCache(restaurant, days);
            return days;
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Could not fetch menu of " + restaurant, e);
            return List.of();
        }
    }

    private Optional<List<MealDay>> readCache(Restaurant restaurant) {
        try {
            return cache.get(restaurant);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Could not read cache for " + restaurant, e);
            return Optional.empty();
        }
    }

    private void writeCache(Restaurant restaurant, List<MealDay> days) {
        try {
            cache.put(restaurant, days, cacheTtl);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Could not write cache for " + restaurant, e);
        }
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}
