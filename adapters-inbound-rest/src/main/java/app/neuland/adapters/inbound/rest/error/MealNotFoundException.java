package app.neuland.adapters.inbound.rest.error;

public class MealNotFoundException extends RuntimeException {

    public MealNotFoundException(String mealId) {
        super("Meal '" + mealId + "' not found");
    }
}
