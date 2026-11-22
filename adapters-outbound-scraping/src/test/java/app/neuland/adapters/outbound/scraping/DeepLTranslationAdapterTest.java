package app.neuland.adapters.outbound.scraping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.neuland.application.RawMealTranslations;
import app.neuland.model.food.Restaurant;
import app.neuland.model.food.raw.RawMeal;
import app.neuland.model.food.raw.RawMealDay;
import app.neuland.model.shared.Language;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DeepLTranslationAdapterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final LocalDate DAY = LocalDate.of(2026, 11, 23);

    private static RawMeal meal(String name, RawMeal... variants) {
        return new RawMeal("id-" + name, name, null, "Essen", null, null, null, null, null, null,
                Restaurant.CANISIUS, null, List.of(variants));
    }

    private static List<RawMealDay> plan(int meals) {
        List<RawMeal> list = new ArrayList<>();
        for (int i = 0; i < meals; i++) {
            list.add(meal("Gericht " + i));
        }
        return List.of(new RawMealDay(DAY, list));
    }

    private static void echoTranslations(TestHttpServer.Request request, TestHttpServer.Reply reply) throws Exception {
        JsonNode body = MAPPER.readTree(request.body());
        ObjectNode response = MAPPER.createObjectNode();
        ArrayNode translations = response.putArray("translations");
        body.get("text").forEach(text ->
                translations.addObject().put("text", "EN " + text.asText()).put("detected_source_language", "DE"));
        reply.body(MAPPER.writeValueAsString(response));
    }

    @Test
    void withoutApiKeyUsesGermanNameAsEnglish() {
        try (TestHttpServer server = new TestHttpServer(DeepLTranslationAdapterTest::echoTranslations)) {
            DeepLTranslationAdapter adapter = new DeepLTranslationAdapter(new HttpFetcher(), true,
                    Optional.empty(), Optional.of(server.url("")));

            List<RawMealDay> result = adapter.translate(plan(1));

            assertEquals("Gericht 0", result.get(0).meals().get(0).names().get(Language.EN));
            assertEquals("Gericht 0", result.get(0).meals().get(0).names().get(Language.DE));
            assertTrue(server.requests.isEmpty());
        }
    }

    @Test
    void disabledNeverCallsDeepL() {
        try (TestHttpServer server = new TestHttpServer(DeepLTranslationAdapterTest::echoTranslations)) {
            DeepLTranslationAdapter adapter = new DeepLTranslationAdapter(new HttpFetcher(), false,
                    Optional.of("key"), Optional.of(server.url("")));
            adapter.translate(plan(1));
            assertTrue(server.requests.isEmpty());
        }
    }

    @Test
    void translatesMealsAndVariantsInBatches() throws Exception {
        try (TestHttpServer server = new TestHttpServer(DeepLTranslationAdapterTest::echoTranslations)) {
            DeepLTranslationAdapter adapter = new DeepLTranslationAdapter(new HttpFetcher(), true,
                    Optional.of("secret"), Optional.of(server.url("")));
            List<RawMealDay> days = new ArrayList<>(plan(119));
            days.add(new RawMealDay(DAY.plusDays(1), List.of(meal("Suppe", meal("Brot")))));

            List<RawMealDay> result = adapter.translate(days);

            // 119 + "Suppe" + "Brot" = 121 distinct texts => 3 requests of max 50 texts
            assertEquals(3, server.requests.size());
            TestHttpServer.Request first = server.requests.get(0);
            assertEquals("POST", first.method());
            assertEquals("/v2/translate", first.path());
            assertEquals("DeepL-Auth-Key secret", first.headers().getFirst("Authorization"));
            JsonNode body = MAPPER.readTree(first.body());
            assertEquals("EN-GB", body.get("target_lang").asText());
            assertEquals("DE", body.get("source_lang").asText());
            assertEquals(50, body.get("text").size());

            assertEquals("EN Gericht 7", result.get(0).meals().get(7).names().get(Language.EN));
            RawMeal soup = result.get(1).meals().get(0);
            assertEquals("EN Suppe", soup.names().get(Language.EN));
            assertEquals("Suppe", soup.names().get(Language.DE));
            assertEquals("EN Brot", soup.variants().get(0).names().get(Language.EN));

            // translations are remembered, a second run does not call DeepL again
            adapter.translate(days);
            assertEquals(3, server.requests.size());
        }
    }

    @Test
    void failingDeepLFallsBackToGermanNames() {
        try (TestHttpServer server = new TestHttpServer((request, reply) -> reply.status(456).body("quota"))) {
            DeepLTranslationAdapter adapter = new DeepLTranslationAdapter(new HttpFetcher(), true,
                    Optional.of("secret"), Optional.of(server.url("")));

            List<RawMealDay> result = adapter.translate(plan(2));

            assertEquals("Gericht 1", result.get(0).meals().get(1).names().get(Language.EN));
        }
    }

    @Test
    void defaultServerUrlDependsOnKeyType() {
        // no request is sent, we only verify construction with free and pro keys works
        new DeepLTranslationAdapter(new HttpFetcher(), true, Optional.of("abc:fx"), Optional.empty());
        new DeepLTranslationAdapter(new HttpFetcher(), true, Optional.of("abc"), Optional.empty());
        assertEquals(List.of("a"), List.copyOf(RawMealTranslations.collectNames(
                List.of(new RawMealDay(DAY, List.of(meal("a"), meal("a")))))));
    }
}
