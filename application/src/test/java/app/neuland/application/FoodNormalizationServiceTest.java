package app.neuland.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.neuland.model.food.Meal;
import app.neuland.model.food.MealCategory;
import app.neuland.model.food.MealDay;
import app.neuland.model.food.MealPrice;
import app.neuland.model.food.MealVariation;
import app.neuland.model.food.Restaurant;
import app.neuland.model.food.raw.RawMeal;
import app.neuland.model.food.raw.RawMealDay;
import app.neuland.model.shared.Language;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class FoodNormalizationServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 23);

    private final FoodNormalizationService service = new FoodNormalizationService();

    private static RawMeal raw(String name, String category, Double student) {
        return new RawMeal(
                FoodNormalizationService.getMealDayHash(DAY, name),
                name,
                null,
                category,
                new MealPrice(student, student == null ? null : student + 1, student == null ? null : student + 2),
                List.of("A"),
                List.of("veg"),
                null,
                null,
                null,
                Restaurant.INGOLSTADT_MENSA,
                null,
                null);
    }

    // --- flags ---------------------------------------------------------------------------

    @Test
    void cleanMealFlagsRemovesContradictingFlags() {
        assertEquals(List.of("R"), service.cleanMealFlags(List.of("veg", "R")));
        assertEquals(List.of("S"), service.cleanMealFlags(List.of("V", "S")));
    }

    @Test
    void cleanMealFlagsKeepsConsistentFlags() {
        assertEquals(List.of("veg", "MSC2"), service.cleanMealFlags(List.of("veg", "MSC2")));
        assertEquals(List.of("R", "S"), service.cleanMealFlags(List.of("R", "S")));
        assertEquals(List.of(), service.cleanMealFlags(List.of()));
    }

    @Test
    void cleanMealFlagsReturnsNullForNull() {
        assertNull(service.cleanMealFlags(null));
    }

    // --- category ------------------------------------------------------------------------

    @Test
    void standardizeCategory() {
        assertEquals(MealCategory.MAIN, FoodNormalizationService.standardizeCategory("main"));
        assertEquals(MealCategory.SOUP, FoodNormalizationService.standardizeCategory("soup"));
        assertEquals(MealCategory.SALAD, FoodNormalizationService.standardizeCategory("salad"));
        assertEquals(MealCategory.SOUP, FoodNormalizationService.standardizeCategory("Suppe"));
        assertEquals(MealCategory.SALAD, FoodNormalizationService.standardizeCategory("Salat"));
        assertEquals(MealCategory.MAIN, FoodNormalizationService.standardizeCategory("Essen"));
        assertEquals(MealCategory.MAIN, FoodNormalizationService.standardizeCategory(null));
    }

    // --- hashes --------------------------------------------------------------------------

    @Test
    void xxHash32MatchesReferenceVectors() {
        assertEquals(0x02CC5D05, XxHash32.hash(new byte[0], 0));
        assertEquals(0x32D153FF, XxHash32.hash("abc"));
        assertEquals(0xE2293B2F, XxHash32.hash("Nobody inspects the spammish repetition"));
    }

    @Test
    void mealDayHash() {
        assertEquals("23194ao2", FoodNormalizationService.getMealDayHash("2026-11-23", "Currywurst mit Pommes"));
        assertEquals("231ducpr", FoodNormalizationService.getMealDayHash(DAY, "Gemüse-Käse Auflauf"));
        assertEquals("01uoqfow", FoodNormalizationService.getMealDayHash(LocalDate.of(2026, 1, 1),
                "{\"de\":\"Thai Curry\",\"en\":\"Thai Curry\"}"));
    }

    @Test
    void mealHashIsXxh3Base36() {
        assertEquals("ec4q3ey05wn4x2gc5vrau734t",
                FoodNormalizationService.getMealHash("Currywurst mit Pommes", MealCategory.MAIN,
                        Restaurant.INGOLSTADT_MENSA));
        assertNotEquals(
                FoodNormalizationService.getMealHash("Currywurst mit Pommes", MealCategory.MAIN, Restaurant.NEUBURG_MENSA),
                FoodNormalizationService.getMealHash("Currywurst mit Pommes", MealCategory.MAIN, Restaurant.INGOLSTADT_MENSA));
    }

    // --- number parsing ------------------------------------------------------------------

    @Test
    void parseGermanFloat() {
        assertEquals(1.5, FoodNormalizationService.parseGermanFloat("1,5"));
        assertEquals(3.15, FoodNormalizationService.parseGermanFloat(" 3,15 "));
        assertEquals(2.0, FoodNormalizationService.parseGermanFloat("2"));
        assertEquals(4.2, FoodNormalizationService.parseGermanFloat("4,2 €"));
        assertNull(FoodNormalizationService.parseGermanFloat(null));
        assertNull(FoodNormalizationService.parseGermanFloat("abc"));
        assertNull(FoodNormalizationService.parseGermanFloat(""));
    }

    @Test
    void parseXmlFloatDefaultsToZero() {
        assertEquals(12.5, FoodNormalizationService.parseXmlFloat("12,5"));
        assertEquals(0, FoodNormalizationService.parseXmlFloat(null));
        assertEquals(0, FoodNormalizationService.parseXmlFloat(""));
        assertEquals(0, FoodNormalizationService.parseXmlFloat("-"));
    }

    // --- merging -------------------------------------------------------------------------

    @Test
    void mergeDayEntriesMovesPrefixedMealsToVariantsWithPriceDifference() {
        List<RawMeal> merged = service.mergeDayEntries(List.of(
                raw("Schnitzel", "Essen", 3.9),
                raw("Schnitzel mit Pommes", "Essen", 4.9),
                raw("Gemüsesuppe", "Suppe", 1.0)));

        assertEquals(2, merged.size());
        RawMeal schnitzel = merged.get(0);
        assertEquals("Schnitzel", schnitzel.name());
        assertEquals(1, schnitzel.variants().size());
        RawMeal variant = schnitzel.variants().get(0);
        assertEquals("Pommes", variant.name()); // "mit" is a stop word
        assertEquals(1.0, variant.prices().student());
        assertEquals(1.0, variant.prices().employee());
        assertEquals(1.0, variant.prices().guest());
        assertTrue(variant.additional());
        assertEquals("Gemüsesuppe", merged.get(1).name());
        assertTrue(merged.get(1).variants().isEmpty());
    }

    @Test
    void mergeDayEntriesKeepsDifferentCategoriesSeparateAndRemovesDuplicates() {
        List<RawMeal> merged = service.mergeDayEntries(List.of(
                raw("Salat", "Essen", 3.0),
                raw("Salat Natur", "Salat", 2.0),
                raw("Salat", "Essen", 3.0)));

        assertEquals(2, merged.size());
        assertEquals("Salat", merged.get(0).name());
        assertTrue(merged.get(0).variants().isEmpty());
        assertEquals("Salat Natur", merged.get(1).name());
    }

    @Test
    void mergeDayEntriesHandlesMissingPrices() {
        List<RawMeal> merged = service.mergeDayEntries(List.of(
                raw("Burger", "Essen", null),
                raw("Burger Cheese", "Essen", 1.0)));

        assertNull(merged.get(0).variants().get(0).prices().student());
    }

    @Test
    void mergeMealVariantsWorksOnAllDays() {
        List<RawMealDay> merged = service.mergeMealVariants(List.of(
                new RawMealDay(DAY, List.of(raw("A", "Essen", 1.0), raw("A B", "Essen", 2.0)))));
        assertEquals(1, merged.get(0).meals().size());
    }

    // --- unification ---------------------------------------------------------------------

    @Test
    void unifyFoodEntriesProducesDomainModel() {
        RawMeal parent = raw("schnitzel", "Essen", 3.9)
                .withVariants(List.of(raw("pommes", "Essen", 1.0).withAdditional(true)));
        List<RawMealDay> translated = RawMealTranslations.apply(
                List.of(new RawMealDay(DAY, List.of(parent, raw("Gemüsesuppe", "Suppe", 1.0)))),
                java.util.Map.of("schnitzel", "schnitzel (en)"));

        List<MealDay> days = service.unifyFoodEntries(translated);

        assertEquals(1, days.size());
        MealDay day = days.get(0);
        assertEquals(DAY, day.date());
        Meal meal = day.meals().get(0);
        assertEquals("Schnitzel", meal.name().get(Language.DE));
        assertEquals("Schnitzel (en)", meal.name().get(Language.EN));
        assertEquals(MealCategory.MAIN, meal.category());
        assertEquals(Language.DE, meal.originalLanguage());
        assertEquals(Restaurant.INGOLSTADT_MENSA, meal.restaurant());
        assertEquals(List.of("veg"), meal.flags());
        assertEquals(3.9, meal.price().student());
        assertEquals(1, meal.variations().size());
        MealVariation variation = meal.variations().get(0);
        assertEquals(meal.id() + "/" + parent.variants().get(0).id(), variation.id());
        assertEquals(meal.id(), variation.parentMealId());
        assertTrue(variation.additional());
        assertEquals("Pommes", variation.name().get(Language.DE));
        assertEquals(MealCategory.SOUP, day.meals().get(1).category());
    }

    @Test
    void unifyFoodEntriesRequiresTranslatedNames() {
        List<RawMealDay> untranslated = List.of(new RawMealDay(DAY, List.of(raw("A", "Essen", 1.0))));
        assertThrows(IllegalArgumentException.class, () -> service.unifyFoodEntries(untranslated));
    }

    @Test
    void unifyFoodEntriesCleansContradictingFlags() {
        RawMeal meal = new RawMeal("id", "A", null, "Essen", null, null, List.of("veg", "R"), null, null, null,
                Restaurant.CANISIUS, null, null);
        List<MealDay> days = service.unifyFoodEntries(
                RawMealTranslations.identity(List.of(new RawMealDay(DAY, List.of(meal)))));
        assertEquals(List.of("R"), days.get(0).meals().get(0).flags());
        assertEquals(new MealPrice(null, null, null), days.get(0).meals().get(0).price());
    }

    @Test
    void unifyPreservesNullAndNonEmptyAllergens() {
        RawMeal known = raw("Schnitzel", "Essen", 3.9);
        RawMeal unknown = new RawMeal(
                FoodNormalizationService.getMealDayHash(DAY, "Thai Curry"),
                "Thai Curry", null, "Essen", new MealPrice(6.9, 7.9, 8.9),
                null, List.of(), null, null, true, Restaurant.REIMANNS, null, null);

        List<MealDay> days = service.unifyFoodEntries(
                RawMealTranslations.identity(List.of(new RawMealDay(DAY, List.of(known, unknown)))));

        assertEquals(List.of("A"), days.get(0).meals().get(0).allergens());
        assertNull(days.get(0).meals().get(1).allergens());
    }

    @Test
    void mergeAndUnifyDoNotDropAllergensOnParentOrVariant() {
        RawMeal parent = new RawMeal(
                FoodNormalizationService.getMealDayHash(DAY, "Spaghetti"),
                "Spaghetti", null, "Essen", new MealPrice(3.0, 4.0, 5.0),
                List.of("A", "C", "G", "I"), List.of(), null, null, null,
                Restaurant.INGOLSTADT_MENSA, null, null);
        RawMeal withCheese = new RawMeal(
                FoodNormalizationService.getMealDayHash(DAY, "Spaghetti mit Parmesan"),
                "Spaghetti mit Parmesan", null, "Essen", new MealPrice(3.5, 4.5, 5.5),
                List.of("G"), List.of(), null, null, null,
                Restaurant.INGOLSTADT_MENSA, null, null);

        List<RawMeal> merged = service.mergeDayEntries(List.of(parent, withCheese));
        assertEquals(1, merged.size());
        assertEquals(List.of("A", "C", "G", "I"), merged.get(0).allergens());
        assertEquals(List.of("G"), merged.get(0).variants().get(0).allergens());

        List<MealDay> days = service.unifyFoodEntries(
                RawMealTranslations.identity(List.of(new RawMealDay(DAY, merged))));
        Meal meal = days.get(0).meals().get(0);
        assertEquals(List.of("A", "C", "G", "I"), meal.allergens());
        assertEquals(List.of("G"), meal.variations().get(0).allergens());
    }
}
