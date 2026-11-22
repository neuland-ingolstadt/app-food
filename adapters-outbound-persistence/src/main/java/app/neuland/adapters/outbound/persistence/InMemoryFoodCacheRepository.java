package app.neuland.adapters.outbound.persistence;

import app.neuland.model.food.MealDay;
import app.neuland.model.food.Restaurant;
import app.neuland.ports.outbound.FoodCacheRepository;
import io.quarkus.arc.lookup.LookupIfProperty;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process local cache for tests and local development. Active unless {@code
 * food.cache.in-memory=false}.
 */
@ApplicationScoped
@LookupIfProperty(name = "food.cache.in-memory", stringValue = "true", lookupIfMissing = true)
public class InMemoryFoodCacheRepository implements FoodCacheRepository {

    private record Entry(List<MealDay> days, Instant expiresAt) {
    }

    private final Map<Restaurant, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryFoodCacheRepository() {
        this(Clock.systemUTC());
    }

    InMemoryFoodCacheRepository(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Optional<List<MealDay>> get(Restaurant restaurant) {
        Entry entry = entries.get(restaurant);
        if (entry == null) {
            return Optional.empty();
        }
        if (!clock.instant().isBefore(entry.expiresAt())) {
            entries.remove(restaurant, entry);
            return Optional.empty();
        }
        return Optional.of(entry.days());
    }

    @Override
    public void put(Restaurant restaurant, List<MealDay> days, Duration ttl) {
        entries.put(restaurant, new Entry(List.copyOf(days), clock.instant().plus(ttl)));
    }
}
