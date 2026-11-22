package app.neuland.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.neuland.model.food.Meal;
import app.neuland.model.food.MealCategory;
import app.neuland.model.food.MealDay;
import app.neuland.model.food.MealPrice;
import app.neuland.model.food.Restaurant;
import app.neuland.model.shared.Language;
import app.neuland.ports.outbound.FoodCacheRepository;
import app.neuland.ports.outbound.MenuSource;
import java.time.Duration;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class FoodQueryServiceTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 23);
    private static final LocalDate TUESDAY = MONDAY.plusDays(1);

    private static Meal meal(String id, Restaurant restaurant) {
        return new Meal(id, Map.of(Language.DE, id, Language.EN, id), MealCategory.MAIN,
                new MealPrice(1.0, 2.0, 3.0), List.of(), List.of(), null, Language.DE, false, restaurant, List.of());
    }

    private static final class CountingSource implements MenuSource {
        private final Restaurant restaurant;
        private final List<MealDay> days;
        private final AtomicInteger calls = new AtomicInteger();
        private final boolean failing;

        CountingSource(Restaurant restaurant, boolean failing, MealDay... days) {
            this.restaurant = restaurant;
            this.failing = failing;
            this.days = List.of(days);
        }

        @Override
        public Restaurant restaurant() {
            return restaurant;
        }

        @Override
        public List<MealDay> fetch() {
            calls.incrementAndGet();
            if (failing) {
                throw new IllegalStateException("boom");
            }
            return days;
        }
    }

    private static final class MapCache implements FoodCacheRepository {
        final Map<Restaurant, List<MealDay>> data = new EnumMap<>(Restaurant.class);

        @Override
        public Optional<List<MealDay>> get(Restaurant restaurant) {
            return Optional.ofNullable(data.get(restaurant));
        }

        @Override
        public void put(Restaurant restaurant, List<MealDay> days, Duration ttl) {
            data.put(restaurant, days);
        }
    }

    @Test
    void mergesMealsByDateAcrossRestaurantsAndFiltersRange() {
        CountingSource mensa = new CountingSource(Restaurant.INGOLSTADT_MENSA, false,
                new MealDay(MONDAY, List.of(meal("m1", Restaurant.INGOLSTADT_MENSA))),
                new MealDay(TUESDAY, List.of(meal("m2", Restaurant.INGOLSTADT_MENSA))),
                new MealDay(MONDAY.plusDays(10), List.of(meal("m3", Restaurant.INGOLSTADT_MENSA))));
        CountingSource reimanns = new CountingSource(Restaurant.REIMANNS, false,
                new MealDay(MONDAY, List.of(meal("r1", Restaurant.REIMANNS))),
                new MealDay(TUESDAY, List.of()));
        FoodQueryService service = new FoodQueryService(new MapCache(), List.of(mensa, reimanns));

        List<MealDay> days = service.listMeals(
                List.of(Restaurant.INGOLSTADT_MENSA, Restaurant.REIMANNS), MONDAY, TUESDAY);

        assertEquals(2, days.size());
        assertEquals(MONDAY, days.get(0).date());
        assertEquals(List.of("m1", "r1"), days.get(0).meals().stream().map(Meal::id).toList());
        assertEquals(List.of("m2"), days.get(1).meals().stream().map(Meal::id).toList());
    }

    @Test
    void omitsEmptyDays() {
        CountingSource reimanns = new CountingSource(Restaurant.REIMANNS, false, new MealDay(MONDAY, List.of()));
        FoodQueryService service = new FoodQueryService(new MapCache(), List.of(reimanns));
        assertTrue(service.listMeals(List.of(Restaurant.REIMANNS), MONDAY, MONDAY).isEmpty());
    }

    @Test
    void usesCacheAfterFirstFetch() {
        CountingSource mensa = new CountingSource(Restaurant.INGOLSTADT_MENSA, false,
                new MealDay(MONDAY, List.of(meal("m1", Restaurant.INGOLSTADT_MENSA))));
        MapCache cache = new MapCache();
        FoodQueryService service = new FoodQueryService(cache, List.of(mensa));

        service.listMeals(List.of(Restaurant.INGOLSTADT_MENSA), MONDAY, MONDAY);
        service.listMeals(List.of(Restaurant.INGOLSTADT_MENSA), MONDAY, MONDAY);

        assertEquals(1, mensa.calls.get());
        assertTrue(cache.data.containsKey(Restaurant.INGOLSTADT_MENSA));
    }

    @Test
    void failingSourceYieldsNoMealsAndIsNotCached() {
        CountingSource broken = new CountingSource(Restaurant.CANISIUS, true);
        CountingSource ok = new CountingSource(Restaurant.REIMANNS, false,
                new MealDay(MONDAY, List.of(meal("r1", Restaurant.REIMANNS))));
        MapCache cache = new MapCache();
        FoodQueryService service = new FoodQueryService(cache, List.of(broken, ok));

        List<MealDay> days = service.listMeals(List.of(Restaurant.CANISIUS, Restaurant.REIMANNS), MONDAY, MONDAY);

        assertEquals(1, days.size());
        assertEquals("r1", days.get(0).meals().get(0).id());
        assertTrue(cache.get(Restaurant.CANISIUS).isEmpty());
    }

    @Test
    void getMealScansAllRestaurants() {
        CountingSource mensa = new CountingSource(Restaurant.INGOLSTADT_MENSA, false,
                new MealDay(MONDAY, List.of(meal("m1", Restaurant.INGOLSTADT_MENSA))));
        CountingSource reimanns = new CountingSource(Restaurant.REIMANNS, false,
                new MealDay(MONDAY, List.of(meal("r1", Restaurant.REIMANNS))));
        FoodQueryService service = new FoodQueryService(new MapCache(), List.of(mensa, reimanns));

        assertEquals(Restaurant.REIMANNS, service.getMeal("r1").orElseThrow().restaurant());
        assertTrue(service.getMeal("unknown").isEmpty());
    }

    @Test
    void refreshAllUpdatesCache() {
        CountingSource mensa = new CountingSource(Restaurant.INGOLSTADT_MENSA, false,
                new MealDay(MONDAY, List.of(meal("m1", Restaurant.INGOLSTADT_MENSA))));
        CountingSource broken = new CountingSource(Restaurant.CANISIUS, true);
        MapCache cache = new MapCache();
        FoodQueryService service = new FoodQueryService(cache, List.of(mensa, broken));

        service.refreshAll();

        assertEquals(1, cache.data.get(Restaurant.INGOLSTADT_MENSA).size());
        assertTrue(cache.get(Restaurant.CANISIUS).isEmpty());
    }
}
