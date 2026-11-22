package app.neuland.adapters.outbound.scraping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.neuland.model.food.Restaurant;
import app.neuland.model.food.raw.RawMeal;
import app.neuland.model.food.raw.RawMealDay;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CanisiusTextParserTest {

    private static final String WEEK = "Speiseplan Canisiuskonvikt\n"
            + "Montag 23.11.2026\nGulaschsuppe\n€ 4.50 € 5.50 € 6.50\n"
            + "Dienstag 24.11.2026\nPizza Margherita\n€ 4.20 € 5.20 € 6.20\nPizza Margherita mit Salami\n€ 4.70 € 5.70 € 6.70\n"
            + "Mittwoch 25.11.2026\nGeschlossen\n"
            + "Donnerstag 26.11.2026\nNudeln\n€ 3.00 € 4.00 € 5.00\n"
            + "Freitag 27.11.2026\nFisch\n€ 4.00 € 5.00 € 6.00\n"
            + "Salate der Saison vom Büfett\nGemischter Salat\n€ 1.00 € 2.00 € 3.00\n";

    @Test
    void parsesFiveDaysWithDishesAndSalads() {
        List<RawMealDay> days = CanisiusTextParser.parse(WEEK);

        assertEquals(5, days.size());
        assertEquals(LocalDate.of(2026, 11, 23), days.get(0).date());
        assertEquals(LocalDate.of(2026, 11, 27), days.get(4).date());

        RawMeal goulash = days.get(0).meals().get(0);
        assertEquals("Gulaschsuppe", goulash.name());
        assertEquals("Essen", goulash.category());
        assertEquals(Restaurant.CANISIUS, goulash.restaurant());
        assertEquals(5.5, goulash.prices().student());
        assertEquals(6.5, goulash.prices().employee());
        assertEquals(6.5, goulash.prices().guest());
        assertNull(goulash.allergens());

        // dishes of a day are followed by the weekly salads
        assertEquals(List.of("Gulaschsuppe", "Gemischter Salat"),
                days.get(0).meals().stream().map(RawMeal::name).toList());
        RawMeal salad = days.get(0).meals().get(1);
        assertEquals("Salat", salad.category());
        assertEquals(2.0, salad.prices().student());
        assertEquals(List.of("Pizza Margherita", "Pizza Margherita mit Salami", "Gemischter Salat"),
                days.get(1).meals().stream().map(RawMeal::name).toList());
        assertEquals(List.of("Fisch", "Gemischter Salat"),
                days.get(4).meals().stream().map(RawMeal::name).toList());
    }

    @Test
    void daysWithoutPricesHaveNoMeals() {
        List<RawMealDay> days = CanisiusTextParser.parse(WEEK);
        assertTrue(days.get(2).meals().isEmpty());
    }

    @Test
    void emptyOrIncompleteTextYieldsNoDays() {
        assertTrue(CanisiusTextParser.parse("").isEmpty());
        assertTrue(CanisiusTextParser.parse("  \n ").isEmpty());
        assertTrue(CanisiusTextParser.parse(null).isEmpty());
        assertTrue(CanisiusTextParser.parse("Wegen Ferien geschlossen").isEmpty());
        assertTrue(CanisiusTextParser.parse("Montag 23.11.2026 Dienstag 24.11.2026 geschlossen").isEmpty());
    }
}
