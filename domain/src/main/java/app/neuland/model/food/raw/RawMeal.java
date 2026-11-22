package app.neuland.model.food.raw;

import app.neuland.model.food.MealPrice;
import app.neuland.model.food.Nutrition;
import app.neuland.model.food.Restaurant;
import app.neuland.model.shared.Language;
import java.util.List;
import java.util.Map;

/**
 * Scraped (not yet normalized) meal or variant of a meal.
 *
 * <p>{@code name} is the name in the original (German) language as scraped. {@code names} is only
 * populated after translation (or when the source is already bilingual, e.g. Reimanns).
 * Nullable fields follow the semantics of the legacy scraper: {@code null} means "not provided by
 * the source".
 *
 * @param id day-scoped id (see {@code FoodNormalizationService#getMealDayHash})
 * @param name name in the original language (pre-translation)
 * @param names translated names, {@code null} until translated
 * @param category raw category string of the source (e.g. "Essen", "Suppe", "Salat"), may be {@code null}
 * @param prices prices, may be {@code null}
 * @param allergens allergens, may be {@code null}
 * @param flags flags, may be {@code null}
 * @param nutrition nutrition, may be {@code null}
 * @param originalLanguage original language, may be {@code null} (defaults to German)
 * @param isStatic static flag, may be {@code null} (defaults to {@code false})
 * @param restaurant restaurant, may be {@code null} for variants (inherited from parent)
 * @param additional additional flag, may be {@code null} (defaults to {@code false})
 * @param variants variants of this meal, never {@code null}
 */
public record RawMeal(
        String id,
        String name,
        Map<Language, String> names,
        String category,
        MealPrice prices,
        List<String> allergens,
        List<String> flags,
        Nutrition nutrition,
        Language originalLanguage,
        Boolean isStatic,
        Restaurant restaurant,
        Boolean additional,
        List<RawMeal> variants
) {

    public RawMeal {
        variants = variants == null ? List.of() : List.copyOf(variants);
    }

    public RawMeal withName(String newName) {
        return new RawMeal(id, newName, names, category, prices, allergens, flags, nutrition,
                originalLanguage, isStatic, restaurant, additional, variants);
    }

    public RawMeal withNames(Map<Language, String> newNames) {
        return new RawMeal(id, name, newNames, category, prices, allergens, flags, nutrition,
                originalLanguage, isStatic, restaurant, additional, variants);
    }

    public RawMeal withPrices(MealPrice newPrices) {
        return new RawMeal(id, name, names, category, newPrices, allergens, flags, nutrition,
                originalLanguage, isStatic, restaurant, additional, variants);
    }

    public RawMeal withOriginalLanguage(Language newOriginalLanguage) {
        return new RawMeal(id, name, names, category, prices, allergens, flags, nutrition,
                newOriginalLanguage, isStatic, restaurant, additional, variants);
    }

    public RawMeal withAdditional(Boolean newAdditional) {
        return new RawMeal(id, name, names, category, prices, allergens, flags, nutrition,
                originalLanguage, isStatic, restaurant, newAdditional, variants);
    }

    public RawMeal withVariants(List<RawMeal> newVariants) {
        return new RawMeal(id, name, names, category, prices, allergens, flags, nutrition,
                originalLanguage, isStatic, restaurant, additional, newVariants);
    }
}
