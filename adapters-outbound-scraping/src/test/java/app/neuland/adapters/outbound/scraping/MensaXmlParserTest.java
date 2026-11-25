package app.neuland.adapters.outbound.scraping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.neuland.application.FoodNormalizationService;
import app.neuland.model.food.Restaurant;
import app.neuland.model.food.raw.RawMeal;
import app.neuland.model.food.raw.RawMealDay;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MensaXmlParserTest {

    static String fixture() throws IOException {
        try (InputStream in = MensaXmlParserTest.class.getResourceAsStream("/mensa-fixture.xml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void parsesDaysUsingBerlinDate() throws IOException {
        List<RawMealDay> days = MensaXmlParser.parse(fixture(), Restaurant.INGOLSTADT_MENSA);

        assertEquals(2, days.size());
        // timestamp is 2026-11-22T23:00:00Z, which is the 23rd in Berlin
        assertEquals(LocalDate.of(2026, 11, 23), days.get(0).date());
        assertEquals(LocalDate.of(2026, 11, 24), days.get(1).date());
        assertEquals(4, days.get(0).meals().size());
        assertEquals(1, days.get(1).meals().size());
    }

    @Test
    void parsesMealFields() throws IOException {
        RawMeal meal = MensaXmlParser.parse(fixture(), Restaurant.NEUBURG_MENSA).get(0).meals().get(0);

        assertEquals("Spaghetti Bolognese", meal.name());
        assertEquals(FoodNormalizationService.getMealDayHash("2026-11-23", "Spaghetti Bolognese"), meal.id());
        assertEquals("Essen", meal.category());
        assertEquals(List.of("A", "C", "G", "I"), meal.allergens());
        assertEquals(List.of("R", "MSC"), meal.flags());
        assertEquals(Restaurant.NEUBURG_MENSA, meal.restaurant());
        assertEquals(3.15, meal.prices().student());
        assertEquals(5.0, meal.prices().employee());
        assertEquals(6.0, meal.prices().guest());
        assertEquals(1500, meal.nutrition().kj());
        assertEquals(358.5, meal.nutrition().kcal());
        assertEquals(12.5, meal.nutrition().fat());
        assertEquals(4.2, meal.nutrition().fatSaturated());
        assertEquals(45.3, meal.nutrition().carbs());
        assertEquals(5.1, meal.nutrition().sugar());
        assertEquals(3.2, meal.nutrition().fiber());
        assertEquals(15.8, meal.nutrition().protein());
        assertEquals(2.1, meal.nutrition().salt());
    }

    @Test
    void extractsMultipleAllergenGroupsAndCdataFlags() throws IOException {
        RawMeal soup = MensaXmlParser.parse(fixture(), Restaurant.INGOLSTADT_MENSA).get(0).meals().get(2);

        assertEquals("tomatensuppe", soup.name());
        assertEquals("Suppe", soup.category());
        assertEquals(List.of("A", "F"), soup.allergens());
        assertEquals(List.of("veg"), soup.flags());
    }

    @Test
    void toleratesMissingFields() throws IOException {
        RawMeal empty = MensaXmlParser.parse(fixture(), Restaurant.INGOLSTADT_MENSA).get(0).meals().get(3);

        assertEquals("", empty.name());
        assertNull(empty.category());
        assertNull(empty.prices().student());
        assertNull(empty.prices().employee());
        assertEquals(0, empty.nutrition().kcal());
        assertTrue(empty.flags().isEmpty());
        assertTrue(empty.allergens().isEmpty());
    }

    @Test
    void returnsEmptyListWithoutDays() {
        assertTrue(MensaXmlParser.parse("<speiseplan></speiseplan>", Restaurant.INGOLSTADT_MENSA).isEmpty());
        assertTrue(MensaXmlParser.parse("<other/>", Restaurant.INGOLSTADT_MENSA).isEmpty());
    }

    @Test
    void rejectsInvalidXmlAndDoctypes() {
        assertThrows(IllegalArgumentException.class, () -> MensaXmlParser.parse("not xml", Restaurant.REIMANNS));
        assertThrows(IllegalArgumentException.class, () -> MensaXmlParser.parse(
                "<!DOCTYPE speiseplan [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><speiseplan>&x;</speiseplan>",
                Restaurant.REIMANNS));
    }

    @Test
    void honorsDeclaredEncodingOfBytes() {
        String xml = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><speiseplan><tag timestamp=\"1795388400\">"
                + "<item><title>Gemüse</title><category>Essen 1</category></item></tag></speiseplan>";
        List<RawMealDay> days = MensaXmlParser.parse(xml.getBytes(StandardCharsets.ISO_8859_1), Restaurant.REIMANNS);
        assertEquals("Gemüse", days.get(0).meals().get(0).name());
    }

    @Test
    void trimsWhitespaceAllergensAndDropsBlankTokens() {
        String xml = """
                <speiseplan><tag timestamp="1795388400">
                  <item>
                    <category>Essen 1</category>
                    <title>Curry ( A , , C ,G )</title>
                    <preis1>3,00</preis1>
                  </item>
                </tag></speiseplan>
                """;
        RawMeal meal = MensaXmlParser.parse(xml, Restaurant.INGOLSTADT_MENSA).get(0).meals().get(0);

        assertEquals("Curry", meal.name());
        assertEquals(List.of("A", "C", "G"), meal.allergens());
    }

    @Test
    void goldenAllergensSurviveMergeAndUnify() throws IOException {
        FoodNormalizationService normalization = new FoodNormalizationService();
        List<RawMealDay> parsed = MensaXmlParser.parse(fixture(), Restaurant.INGOLSTADT_MENSA);
        List<RawMealDay> merged = normalization.mergeMealVariants(parsed);
        List<RawMealDay> translated = app.neuland.application.RawMealTranslations.identity(merged);
        List<app.neuland.model.food.MealDay> days = normalization.unifyFoodEntries(translated);

        app.neuland.model.food.Meal spaghetti = days.get(0).meals().stream()
                .filter(m -> m.name().get(app.neuland.model.shared.Language.DE).startsWith("Spaghetti"))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("A", "C", "G", "I"), spaghetti.allergens());
        assertEquals(1, spaghetti.variations().size());
        // Variant title also lists (A,C,G,I) plus (G) — all codes must survive merge+unify.
        assertEquals(List.of("A", "C", "G", "I"), spaghetti.variations().get(0).allergens());

        app.neuland.model.food.Meal soup = days.get(0).meals().stream()
                .filter(m -> m.name().get(app.neuland.model.shared.Language.DE).equalsIgnoreCase("Tomatensuppe"))
                .findFirst()
                .orElseThrow();
        assertEquals(List.of("A", "F"), soup.allergens());
    }
}
