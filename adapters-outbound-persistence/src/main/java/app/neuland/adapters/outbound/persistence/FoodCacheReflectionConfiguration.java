package app.neuland.adapters.outbound.persistence;

import app.neuland.model.food.Meal;
import app.neuland.model.food.MealCategory;
import app.neuland.model.food.MealDay;
import app.neuland.model.food.MealPrice;
import app.neuland.model.food.MealVariation;
import app.neuland.model.food.Nutrition;
import app.neuland.model.food.Restaurant;
import app.neuland.model.shared.Language;
import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Domain models are Jackson-serialized into Redis. Native image needs explicit reflection
 * registration or {@link MealDayJson} fails with empty-bean serializer errors.
 */
@RegisterForReflection(targets = {
        MealDay.class,
        Meal.class,
        MealCategory.class,
        MealPrice.class,
        MealVariation.class,
        Nutrition.class,
        Restaurant.class,
        Language.class
})
public final class FoodCacheReflectionConfiguration {

    private FoodCacheReflectionConfiguration() {
    }
}
