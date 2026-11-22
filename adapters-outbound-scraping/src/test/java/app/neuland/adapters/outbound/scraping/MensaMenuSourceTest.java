package app.neuland.adapters.outbound.scraping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.neuland.application.FoodNormalizationService;
import app.neuland.model.food.Meal;
import app.neuland.model.food.MealCategory;
import app.neuland.model.food.MealDay;
import app.neuland.model.food.Restaurant;
import app.neuland.model.shared.Language;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MensaMenuSourceTest {

    private final HttpFetcher http = new HttpFetcher();
    private final FoodNormalizationService normalization = new FoodNormalizationService();
    private final DeepLTranslationAdapter translation =
            new DeepLTranslationAdapter(http, true, Optional.empty(), Optional.empty());

    @Test
    void downloadsParsesMergesAndUnifiesTheXml() throws IOException {
        String xml = MensaXmlParserTest.fixture();
        try (TestHttpServer server = new TestHttpServer((request, reply) -> reply.body(xml))) {
            IngolstadtMensaMenuSource source = new IngolstadtMensaMenuSource(http, normalization, translation,
                    server.url("/xml/mensa-ingolstadt.xml"));

            List<MealDay> days = source.fetch();

            assertEquals(Restaurant.INGOLSTADT_MENSA, source.restaurant());
            assertEquals("GET", server.requests.get(0).method());
            assertEquals("/xml/mensa-ingolstadt.xml", server.requests.get(0).path());
            assertEquals(2, days.size());

            List<Meal> meals = days.get(0).meals();
            // spaghetti (+ parmesan variation), soup and the empty-named item
            assertEquals(3, meals.size());
            Meal spaghetti = meals.get(0);
            assertEquals("Spaghetti Bolognese", spaghetti.name().get(Language.DE));
            assertEquals("Spaghetti Bolognese", spaghetti.name().get(Language.EN));
            assertEquals(MealCategory.MAIN, spaghetti.category());
            assertEquals(1, spaghetti.variations().size());
            assertEquals("Parmesan", spaghetti.variations().get(0).name().get(Language.DE));
            assertEquals(0.75, spaghetti.variations().get(0).price().student());
            assertEquals(spaghetti.id(), spaghetti.variations().get(0).parentMealId());
            assertEquals(MealCategory.SOUP, meals.get(1).category());
            assertEquals("Tomatensuppe", meals.get(1).name().get(Language.DE));
            assertEquals(MealCategory.SALAD, days.get(1).meals().get(0).category());
        }
    }

    @Test
    void neuburgUsesItsOwnRestaurant() throws IOException {
        String xml = MensaXmlParserTest.fixture();
        try (TestHttpServer server = new TestHttpServer((request, reply) -> reply.body(xml))) {
            NeuburgMensaMenuSource source =
                    new NeuburgMensaMenuSource(http, normalization, translation, server.url("/x.xml"));
            assertEquals(Restaurant.NEUBURG_MENSA, source.restaurant());
            assertTrue(source.fetch().stream().flatMap(day -> day.meals().stream())
                    .allMatch(meal -> meal.restaurant() == Restaurant.NEUBURG_MENSA));
        }
    }

    @Test
    void failsOnUpstreamErrors() {
        try (TestHttpServer server = new TestHttpServer((request, reply) -> reply.status(503).body("down"))) {
            IngolstadtMensaMenuSource source =
                    new IngolstadtMensaMenuSource(http, normalization, translation, server.url("/x.xml"));
            assertThrows(IllegalStateException.class, source::fetch);
        }
    }

    @Test
    void defaultUrlsPointToMaxManager() {
        assertEquals("https://www.max-manager.de/daten-extern/sw-erlangen-nuernberg/xml/mensa-ingolstadt.xml",
                IngolstadtMensaMenuSource.DEFAULT_URL);
        assertEquals("https://www.max-manager.de/daten-extern/sw-erlangen-nuernberg/xml/cafeteria-neuburg.xml",
                NeuburgMensaMenuSource.DEFAULT_URL);
    }
}
