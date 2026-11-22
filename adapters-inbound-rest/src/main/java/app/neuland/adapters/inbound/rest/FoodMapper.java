package app.neuland.adapters.inbound.rest;

import app.neuland.backend.food.api.v0.model.LanguageEnum;
import app.neuland.backend.food.api.v0.model.LocalizedString;
import app.neuland.backend.food.api.v0.model.MealCategory;
import app.neuland.backend.food.api.v0.model.MealDay;
import app.neuland.backend.food.api.v0.model.MealPrice;
import app.neuland.backend.food.api.v0.model.MealVariation;
import app.neuland.backend.food.api.v0.model.Meal;
import app.neuland.backend.food.api.v0.model.Nutrition;
import app.neuland.backend.food.api.v0.model.Restaurant;
import app.neuland.model.shared.Language;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Maps the domain model to the generated API models. */
final class FoodMapper {

    private FoodMapper() {
    }

    static List<MealDay> toApi(List<app.neuland.model.food.MealDay> days) {
        return days.stream().map(FoodMapper::toApi).toList();
    }

    static MealDay toApi(app.neuland.model.food.MealDay day) {
        return new MealDay()
                .date(day.date())
                .meals(day.meals().stream().map(FoodMapper::toApi).toList());
    }

    static Meal toApi(app.neuland.model.food.Meal meal) {
        return new Meal()
                .id(meal.id())
                .name(toApi(meal.name()))
                .category(toApi(meal.category()))
                .price(toApi(meal.price()))
                .allergens(new ArrayList<>(nullToEmpty(meal.allergens())))
                .flags(new ArrayList<>(nullToEmpty(meal.flags())))
                .nutrition(toApi(meal.nutrition()))
                .originalLanguage(toApi(meal.originalLanguage()))
                ._static(meal.isStatic())
                .restaurant(toApi(meal.restaurant()))
                .variations(nullToEmpty(meal.variations()).stream().map(FoodMapper::toApi).toList());
    }

    static MealVariation toApi(app.neuland.model.food.MealVariation variation) {
        return new MealVariation()
                .id(variation.id())
                .name(toApi(variation.name()))
                .additional(variation.additional())
                .price(toApi(variation.price()))
                .allergens(new ArrayList<>(nullToEmpty(variation.allergens())))
                .flags(new ArrayList<>(nullToEmpty(variation.flags())))
                .nutrition(toApi(variation.nutrition()))
                .originalLanguage(toApi(variation.originalLanguage()))
                ._static(variation.isStatic())
                .restaurant(toApi(variation.restaurant()))
                .parentMealId(variation.parentMealId());
    }

    static LocalizedString toApi(Map<Language, String> name) {
        String de = name.get(Language.DE);
        String en = name.get(Language.EN);
        return new LocalizedString()
                .de(de != null ? de : en)
                .en(en != null ? en : de);
    }

    static MealPrice toApi(app.neuland.model.food.MealPrice price) {
        if (price == null) {
            return new MealPrice();
        }
        return new MealPrice()
                .student(toFloat(price.student()))
                .employee(toFloat(price.employee()))
                .guest(toFloat(price.guest()));
    }

    static Nutrition toApi(app.neuland.model.food.Nutrition nutrition) {
        if (nutrition == null) {
            return null;
        }
        return new Nutrition()
                .kj(nutrition.kj())
                .kcal(nutrition.kcal())
                .fat(nutrition.fat())
                .fatSaturated(nutrition.fatSaturated())
                .carbs(nutrition.carbs())
                .sugar(nutrition.sugar())
                .fiber(nutrition.fiber())
                .protein(nutrition.protein())
                .salt(nutrition.salt());
    }

    static MealCategory toApi(app.neuland.model.food.MealCategory category) {
        return MealCategory.valueOf(category.name());
    }

    static Restaurant toApi(app.neuland.model.food.Restaurant restaurant) {
        return Restaurant.valueOf(restaurant.name());
    }

    static LanguageEnum toApi(Language language) {
        return LanguageEnum.valueOf(language.name());
    }

    static app.neuland.model.food.Restaurant toDomain(Restaurant restaurant) {
        return app.neuland.model.food.Restaurant.valueOf(restaurant.name());
    }

    private static Float toFloat(Double value) {
        return value == null ? null : value.floatValue();
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}
