package app.neuland.ports.inbound;

import app.neuland.model.food.Meal;
import app.neuland.model.food.MealDay;
import app.neuland.model.food.Restaurant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FoodQueryUseCase {

    /**
     * Lists the meals of the given restaurants merged by date.
     *
     * @param restaurants restaurants to include
     * @param from first day (inclusive)
     * @param to last day (inclusive)
     * @return days within the range that have at least one meal, ordered by date
     */
    List<MealDay> listMeals(List<Restaurant> restaurants, LocalDate from, LocalDate to);

    /**
     * Finds a meal by its day-scoped id ({@link Meal#id()}).
     */
    Optional<Meal> getMeal(String mealId);
}
