package app.neuland.adapters.outbound.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.neuland.model.food.Meal;
import app.neuland.model.food.MealCategory;
import app.neuland.model.food.MealDay;
import app.neuland.model.food.MealPrice;
import app.neuland.model.food.MealVariation;
import app.neuland.model.food.Nutrition;
import app.neuland.model.food.Restaurant;
import app.neuland.model.shared.Language;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MealDayJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Test
    void roundTripPreservesMealDays() {
        Map<Language, String> name = new EnumMap<>(Language.class);
        name.put(Language.DE, "Schnitzel");
        name.put(Language.EN, "Schnitzel (en)");
        MealVariation variation = new MealVariation("23abc/23def", name, true, new MealPrice(1.0, null, 3.0),
                List.of("A"), List.of(), null, Language.DE, false, Restaurant.INGOLSTADT_MENSA, "23abc");
        Meal meal = new Meal("23abc", name, MealCategory.MAIN, new MealPrice(3.9, 4.9, 5.9), List.of("A", "G"),
                List.of("veg"), new Nutrition(1, 2, 3, 4, 5, 6, 7, 8, 9), Language.DE, true,
                Restaurant.INGOLSTADT_MENSA, List.of(variation));
        List<MealDay> days = List.of(new MealDay(LocalDate.of(2026, 11, 23), List.of(meal)),
                new MealDay(LocalDate.of(2026, 11, 24), List.of()));

        MealDayJson json = new MealDayJson(mapper);
        String serialized = json.write(days);

        assertTrue(serialized.contains("\"2026-11-23\""));
        assertEquals(days, assertDoesNotThrowRead(json, serialized));
    }

    private static List<MealDay> assertDoesNotThrowRead(MealDayJson json, String serialized) {
        try {
            return json.read(serialized);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void keyUsesRestaurantName() {
        assertEquals("food:menu:REIMANNS", ValkeyFoodCacheRepository.key(Restaurant.REIMANNS));
    }
}
