package app.neuland.bootstrap;

import app.neuland.model.food.Meal;
import app.neuland.model.food.MealCategory;
import app.neuland.model.food.MealDay;
import app.neuland.model.food.MealPrice;
import app.neuland.model.food.MealVariation;
import app.neuland.model.food.Nutrition;
import app.neuland.model.food.Restaurant;
import app.neuland.model.shared.Language;
import app.neuland.ports.outbound.MenuSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Replaces the real menu sources in tests (the real ones are {@code @DefaultBean}s which back off
 * as soon as other {@link MenuSource} beans exist).
 */
@ApplicationScoped
public class StubMenuSources {

    public static final LocalDate MONDAY = LocalDate.of(2026, 11, 23);
    public static final LocalDate TUESDAY = MONDAY.plusDays(1);

    public static final Map<Restaurant, AtomicInteger> FETCHES = new EnumMap<>(Restaurant.class);

    static {
        for (Restaurant restaurant : Restaurant.values()) {
            FETCHES.put(restaurant, new AtomicInteger());
        }
    }

    private static Map<Language, String> name(String de, String en) {
        return Map.of(Language.DE, de, Language.EN, en);
    }

    static Meal meal(String id, String de, String en, Restaurant restaurant, boolean isStatic,
            List<MealVariation> variations) {
        return new Meal(id, name(de, en), MealCategory.MAIN, new MealPrice(3.9, 4.9, null), List.of("A", "G"),
                List.of("veg"), new Nutrition(1500, 358, 12.5, 4.2, 45.3, 5.1, 3.2, 15.8, 2.1), Language.DE,
                isStatic, restaurant, variations);
    }

    private static MenuSource source(Restaurant restaurant, List<MealDay> days) {
        return new MenuSource() {
            @Override
            public Restaurant restaurant() {
                return restaurant;
            }

            @Override
            public List<MealDay> fetch() {
                FETCHES.get(restaurant).incrementAndGet();
                return days;
            }
        };
    }

    @Produces
    @Singleton
    MenuSource ingolstadt() {
        MealVariation variation = new MealVariation("23aaaaaa/23bbbbbb", name("Parmesan", "Parmesan"), true,
                new MealPrice(0.75, 0.9, 1.0), List.of("G"), List.of(), null, Language.DE,
                false, Restaurant.INGOLSTADT_MENSA, "23aaaaaa");
        return source(Restaurant.INGOLSTADT_MENSA, List.of(
                new MealDay(MONDAY, List.of(
                        meal("23aaaaaa", "Spaghetti Bolognese", "Spaghetti Bolognese", Restaurant.INGOLSTADT_MENSA,
                                false, List.of(variation)))),
                new MealDay(TUESDAY, List.of(
                        meal("24cccccc", "Gemüsepfanne", "Vegetable stir-fry", Restaurant.INGOLSTADT_MENSA, false,
                                List.of())))));
    }

    @Produces
    @Singleton
    MenuSource neuburg() {
        return source(Restaurant.NEUBURG_MENSA, List.of());
    }

    @Produces
    @Singleton
    MenuSource reimanns() {
        return source(Restaurant.REIMANNS, List.of(
                new MealDay(MONDAY, List.of(
                        meal("23dddddd", "Rinderroulade", "Beef roulade", Restaurant.REIMANNS, false, List.of()),
                        meal("23eeeeee", "Thai Curry", "Thai Curry", Restaurant.REIMANNS, true, List.of()))),
                new MealDay(TUESDAY, List.of())));
    }

    @Produces
    @Singleton
    MenuSource canisius() {
        return source(Restaurant.CANISIUS, List.of());
    }
}
