package app.neuland.adapters.outbound.scraping;

import app.neuland.application.FoodNormalizationService;
import app.neuland.model.food.MealDay;
import app.neuland.model.food.MealPrice;
import app.neuland.model.food.Restaurant;
import app.neuland.model.food.raw.RawMeal;
import app.neuland.model.food.raw.RawMealDay;
import app.neuland.model.shared.Language;
import app.neuland.ports.outbound.MenuSource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.DefaultBean;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Fetches the Reimanns menu from its GraphQL API and adds the always available (static) meals. The
 * API is already bilingual, so no translation is needed.
 */
@Singleton
@DefaultBean
public class ReimannsGraphQlMenuSource implements MenuSource {

    public static final String DEFAULT_URL = "https://reimanns-api.neuland.app/graphql";
    static final String QUERY = "query { menu { days { date meals { name_de name_en } } } }";
    private static final String STATIC_MEALS_RESOURCE = "/reimanns-meals.json";
    private static final MealPrice PRICE = new MealPrice(6.9, 7.9, 8.9);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String url;
    private final HttpFetcher http;
    private final FoodNormalizationService normalization;
    private final JsonNode staticMeals;

    @Inject
    public ReimannsGraphQlMenuSource(
            HttpFetcher http,
            FoodNormalizationService normalization,
            @ConfigProperty(name = "food.reimanns.graphql-url", defaultValue = DEFAULT_URL) String url) {
        this.http = http;
        this.normalization = normalization;
        this.url = url;
        this.staticMeals = readStaticMeals();
    }

    @Override
    public Restaurant restaurant() {
        return Restaurant.REIMANNS;
    }

    @Override
    public List<MealDay> fetch() {
        String body;
        try {
            body = MAPPER.writeValueAsString(Map.of("query", QUERY));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        HttpFetcher.Response response = http.postJson(url, body, Map.of());
        if (!response.ok()) {
            throw new IllegalStateException("Reimanns API returned status " + response.status());
        }
        return normalization.unifyFoodEntries(parse(response.text()));
    }

    /** Parses the GraphQL response and adds the static meals to every day with at least one meal. */
    List<RawMealDay> parse(String responseJson) {
        JsonNode root;
        try {
            root = MAPPER.readTree(responseJson);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Reimanns API returned invalid JSON", e);
        }
        JsonNode days = root.path("data").path("menu").path("days");
        if (!days.isArray()) {
            throw new IllegalStateException("No data received from Reimanns");
        }

        List<RawMealDay> plan = new ArrayList<>();
        for (JsonNode dayNode : days) {
            if (!dayNode.path("date").isTextual() || !dayNode.path("meals").isArray()) {
                continue;
            }
            LocalDate date = parseDate(dayNode.get("date").asText());
            List<RawMeal> meals = new ArrayList<>();
            for (JsonNode mealNode : dayNode.get("meals")) {
                if (!mealNode.path("name_de").isTextual() || !mealNode.path("name_en").isTextual()) {
                    continue;
                }
                String de = mealNode.get("name_de").asText();
                String en = mealNode.get("name_en").asText();
                meals.add(new RawMeal(
                        FoodNormalizationService.getMealDayHash(date, de),
                        de,
                        FoodNormalizationService.names(de, en),
                        "Essen",
                        PRICE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        Restaurant.REIMANNS,
                        null,
                        List.of()));
            }
            // skip days without meals (restaurant probably closed)
            if (!meals.isEmpty()) {
                meals.addAll(staticMeals(date));
            }
            plan.add(new RawMealDay(date, meals));
        }
        return plan;
    }

    private static LocalDate parseDate(String value) {
        return LocalDate.parse(value.length() > 10 ? value.substring(0, 10) : value);
    }

    private List<RawMeal> staticMeals(LocalDate date) {
        List<RawMeal> meals = new ArrayList<>();
        for (JsonNode meal : staticMeals) {
            // the legacy scraper hashed the JSON serialized name object
            String nameJson = meal.get("name").toString();
            List<RawMeal> variants = new ArrayList<>();
            for (JsonNode variant : meal.path("variants")) {
                variants.add(new RawMeal(
                        FoodNormalizationService.getMealDayHash(date, variant.get("name").toString()),
                        variant.get("name").get("de").asText(),
                        names(variant.get("name")),
                        null,
                        prices(variant.get("prices")),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        variant.path("additional").asBoolean(false),
                        List.of()));
            }
            meals.add(new RawMeal(
                    FoodNormalizationService.getMealDayHash(date, nameJson),
                    meal.get("name").get("de").asText(),
                    names(meal.get("name")),
                    meal.path("category").asText("Essen"),
                    prices(meal.get("prices")),
                    null,
                    null,
                    null,
                    Language.fromCode(meal.path("originalLanguage").asText("de")),
                    meal.path("static").asBoolean(false),
                    Restaurant.REIMANNS,
                    null,
                    variants));
        }
        return meals;
    }

    private static Map<Language, String> names(JsonNode name) {
        return FoodNormalizationService.names(name.get("de").asText(), name.get("en").asText());
    }

    private static MealPrice prices(JsonNode prices) {
        return new MealPrice(
                prices.get("student").asDouble(),
                prices.get("employee").asDouble(),
                prices.get("guest").asDouble());
    }

    private static JsonNode readStaticMeals() {
        try (InputStream in = ReimannsGraphQlMenuSource.class.getResourceAsStream(STATIC_MEALS_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + STATIC_MEALS_RESOURCE);
            }
            return MAPPER.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
