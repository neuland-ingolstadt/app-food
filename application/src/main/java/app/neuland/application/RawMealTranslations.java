package app.neuland.application;

import app.neuland.model.food.raw.RawMeal;
import app.neuland.model.food.raw.RawMealDay;
import app.neuland.model.shared.Language;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

/** Helpers for {@link app.neuland.ports.outbound.TranslationPort} implementations. */
public final class RawMealTranslations {

    private RawMealTranslations() {
    }

    /** Collects the distinct German names of all meals and variants in encounter order. */
    public static Set<String> collectNames(List<RawMealDay> days) {
        Set<String> names = new LinkedHashSet<>();
        for (RawMealDay day : days) {
            for (RawMeal meal : day.meals()) {
                names.add(meal.name());
                meal.variants().forEach(variant -> names.add(variant.name()));
            }
        }
        return names;
    }

    /**
     * Populates the {@code names} of all meals and variants using the given translation function
     * (German name to English name). Missing translations fall back to the German name.
     */
    public static List<RawMealDay> apply(List<RawMealDay> days, UnaryOperator<String> toEnglish) {
        return days.stream()
                .map(day -> day.withMeals(day.meals().stream().map(meal -> translate(meal, toEnglish)).toList()))
                .toList();
    }

    public static List<RawMealDay> apply(List<RawMealDay> days, Map<String, String> translations) {
        return apply(days, name -> {
            String translated = translations.get(name);
            return translated == null || translated.isBlank() ? name : translated;
        });
    }

    /** Untranslated fallback: the English name equals the German name. */
    public static List<RawMealDay> identity(List<RawMealDay> days) {
        return apply(days, UnaryOperator.identity());
    }

    private static RawMeal translate(RawMeal meal, UnaryOperator<String> toEnglish) {
        return meal
                .withNames(FoodNormalizationService.names(meal.name(), toEnglish.apply(meal.name())))
                .withOriginalLanguage(Language.DE)
                .withVariants(meal.variants().stream().map(variant -> translate(variant, toEnglish)).toList());
    }
}
