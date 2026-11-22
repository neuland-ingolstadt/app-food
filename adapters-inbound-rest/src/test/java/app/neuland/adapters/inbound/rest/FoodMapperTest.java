package app.neuland.adapters.inbound.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.neuland.backend.food.api.v0.model.LanguageEnum;
import app.neuland.backend.food.api.v0.model.Meal;
import app.neuland.backend.food.api.v0.model.MealCategory;
import app.neuland.backend.food.api.v0.model.Restaurant;
import app.neuland.model.food.MealPrice;
import app.neuland.model.food.MealVariation;
import app.neuland.model.food.Nutrition;
import app.neuland.model.shared.Language;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FoodMapperTest {

    @Test
    void mapsMealWithVariation() {
        Map<Language, String> name = Map.of(Language.DE, "Schnitzel", Language.EN, "Cutlet");
        MealVariation variation = new MealVariation("d1/d2", name, true, new MealPrice(1.0, null, 3.0),
                null, List.of("veg"), null, Language.DE, false, app.neuland.model.food.Restaurant.CANISIUS, "d1");
        app.neuland.model.food.Meal meal = new app.neuland.model.food.Meal("d1", name,
                app.neuland.model.food.MealCategory.SALAD, new MealPrice(3.9, 4.9, 5.9), List.of("A"), List.of(),
                new Nutrition(1, 2, 3, 4, 5, 6, 7, 8, 9), Language.EN, true,
                app.neuland.model.food.Restaurant.REIMANNS, List.of(variation));

        Meal api = FoodMapper.toApi(meal);

        assertEquals("d1", api.getId());
        assertEquals("Schnitzel", api.getName().getDe());
        assertEquals("Cutlet", api.getName().getEn());
        assertEquals(MealCategory.SALAD, api.getCategory());
        assertEquals(3.9f, api.getPrice().getStudent());
        assertEquals(List.of("A"), api.getAllergens());
        assertEquals(9.0, api.getNutrition().getSalt());
        assertEquals(LanguageEnum.EN, api.getOriginalLanguage());
        assertTrue(api.getStatic());
        assertEquals(Restaurant.REIMANNS, api.getRestaurant());
        var apiVariation = api.getVariations().get(0);
        assertEquals("d1/d2", apiVariation.getId());
        assertEquals("d1", apiVariation.getParentMealId());
        assertTrue(apiVariation.getAdditional());
        assertNull(apiVariation.getPrice().getEmployee());
        assertEquals(List.of(), apiVariation.getAllergens());
        assertEquals(List.of("veg"), apiVariation.getFlags());
        assertNull(apiVariation.getNutrition());
    }

    @Test
    void mapsDays() {
        var day = new app.neuland.model.food.MealDay(LocalDate.of(2026, 11, 23), List.of());
        assertEquals(LocalDate.of(2026, 11, 23), FoodMapper.toApi(List.of(day)).get(0).getDate());
    }

    @Test
    void localizedStringFallsBackToOtherLanguage() {
        var localized = FoodMapper.toApi(Map.of(Language.DE, "Nur Deutsch"));
        assertEquals("Nur Deutsch", localized.getEn());
    }

    @Test
    void mapsRestaurantBack() {
        assertEquals(app.neuland.model.food.Restaurant.NEUBURG_MENSA, FoodMapper.toDomain(Restaurant.NEUBURG_MENSA));
    }
}
