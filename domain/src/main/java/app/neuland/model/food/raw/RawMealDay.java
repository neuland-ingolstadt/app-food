package app.neuland.model.food.raw;

import java.time.LocalDate;
import java.util.List;

/**
 * Scraped (not yet normalized) meals of a single day.
 */
public record RawMealDay(LocalDate date, List<RawMeal> meals) {

    public RawMealDay {
        meals = meals == null ? List.of() : List.copyOf(meals);
    }

    public RawMealDay withMeals(List<RawMeal> newMeals) {
        return new RawMealDay(date, newMeals);
    }
}
