package app.neuland.ports.outbound;

import app.neuland.model.food.MealDay;
import app.neuland.model.food.Restaurant;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

public interface FoodCacheRepository {

    /** Default time to live of a cache entry. */
    Duration DEFAULT_TTL = Duration.ofSeconds(1800);

    /**
     * Returns the cached (non-expired) menu of the restaurant. An empty list is a valid cached value
     * (e.g. restaurant closed) and is distinct from {@link Optional#empty()} (cache miss).
     */
    Optional<List<MealDay>> get(Restaurant restaurant);

    /** Stores the menu of the restaurant, it expires after the given time to live. */
    void put(Restaurant restaurant, List<MealDay> days, Duration ttl);

    default void put(Restaurant restaurant, List<MealDay> days) {
        put(restaurant, days, DEFAULT_TTL);
    }
}
