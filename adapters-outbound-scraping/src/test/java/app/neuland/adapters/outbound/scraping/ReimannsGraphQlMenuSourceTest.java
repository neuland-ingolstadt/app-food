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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReimannsGraphQlMenuSourceTest {

    private static final String RESPONSE = """
            {"data":{"menu":{"days":[
              {"date":"2026-11-23","meals":[
                {"name_de":"Rinderroulade","name_en":"Beef roulade"},
                {"name_de":null,"name_en":"broken"}]},
              {"date":"2026-11-24","meals":[]},
              {"date":null,"meals":[]}
            ]}}}
            """;

    private final FoodNormalizationService normalization = new FoodNormalizationService();

    @Test
    void postsGraphQlQueryAndMapsMeals() throws Exception {
        try (TestHttpServer server = new TestHttpServer((request, reply) -> reply.body(RESPONSE))) {
            ReimannsGraphQlMenuSource source = new ReimannsGraphQlMenuSource(new HttpFetcher(), normalization,
                    server.url("/graphql"));

            List<MealDay> days = source.fetch();

            TestHttpServer.Request request = server.requests.get(0);
            assertEquals("POST", request.method());
            assertEquals("/graphql", request.path());
            assertEquals(ReimannsGraphQlMenuSource.QUERY, new ObjectMapper().readTree(request.body()).get("query").asText());
            assertEquals("application/json", request.headers().getFirst("Content-Type"));

            assertEquals(Restaurant.REIMANNS, source.restaurant());
            assertEquals(2, days.size());
            List<Meal> meals = days.get(0).meals();
            Meal roulade = meals.get(0);
            assertEquals("Rinderroulade", roulade.name().get(Language.DE));
            assertEquals("Beef roulade", roulade.name().get(Language.EN));
            assertEquals(MealCategory.MAIN, roulade.category());
            assertEquals(6.9, roulade.price().student());
            assertEquals(7.9, roulade.price().employee());
            assertEquals(8.9, roulade.price().guest());
            assertEquals(FoodNormalizationService.getMealDayHash("2026-11-23", "Rinderroulade"), roulade.id());
            assertEquals(Restaurant.REIMANNS, roulade.restaurant());
            assertTrue(!roulade.isStatic());

            // static meals are appended for days with at least one meal
            assertEquals(1 + 6, meals.size());
            Meal thaiCurry = meals.get(1);
            assertEquals("Thai Curry", thaiCurry.name().get(Language.DE));
            assertTrue(thaiCurry.isStatic());
            assertEquals("23uoqfow", thaiCurry.id());
            assertEquals(6.9, thaiCurry.price().student());
            assertEquals(1, thaiCurry.variations().size());
            assertEquals("Chicken strips", thaiCurry.variations().get(0).name().get(Language.EN));
            assertTrue(thaiCurry.variations().get(0).additional());
            assertEquals(thaiCurry.id(), thaiCurry.variations().get(0).parentMealId());

            // closed day stays empty
            assertTrue(days.get(1).meals().isEmpty());
        }
    }

    @Test
    void failsWithoutData() {
        try (TestHttpServer server = new TestHttpServer((request, reply) -> reply.body("{\"data\":{\"menu\":null}}"))) {
            ReimannsGraphQlMenuSource source = new ReimannsGraphQlMenuSource(new HttpFetcher(), normalization,
                    server.url("/graphql"));
            assertThrows(IllegalStateException.class, source::fetch);
        }
    }

    @Test
    void failsOnHttpErrors() {
        try (TestHttpServer server = new TestHttpServer((request, reply) -> reply.status(502))) {
            ReimannsGraphQlMenuSource source = new ReimannsGraphQlMenuSource(new HttpFetcher(), normalization,
                    server.url("/graphql"));
            assertThrows(IllegalStateException.class, source::fetch);
        }
    }
}
