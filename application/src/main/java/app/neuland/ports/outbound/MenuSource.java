package app.neuland.ports.outbound;

import app.neuland.model.food.MealDay;
import app.neuland.model.food.Restaurant;
import java.util.List;

/**
 * Source of the menu of exactly one restaurant. Implementations return already normalized,
 * translated meal days (see {@code FoodNormalizationService}).
 */
public interface MenuSource {

    Restaurant restaurant();

    /**
     * Fetches and normalizes the current menu.
     *
     * @throws RuntimeException if the upstream source is not reachable or returns invalid data
     */
    List<MealDay> fetch();
}
