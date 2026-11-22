package app.neuland.adapters.outbound.scraping;

import app.neuland.application.FoodNormalizationService;
import app.neuland.model.food.MealDay;
import app.neuland.model.food.Restaurant;
import app.neuland.model.food.raw.RawMealDay;
import app.neuland.ports.outbound.MenuSource;
import app.neuland.ports.outbound.TranslationPort;
import java.util.List;

/**
 * Downloads the live max-manager.de XML meal plan of a Mensa and normalizes it: fetch XML, parse,
 * merge variants, translate, unify.
 */
public abstract class MensaMenuSource implements MenuSource {

    private final Restaurant restaurant;
    private final String url;
    private final HttpFetcher http;
    private final FoodNormalizationService normalization;
    private final TranslationPort translation;

    protected MensaMenuSource(Restaurant restaurant, String url, HttpFetcher http,
            FoodNormalizationService normalization, TranslationPort translation) {
        this.restaurant = restaurant;
        this.url = url;
        this.http = http;
        this.normalization = normalization;
        this.translation = translation;
    }

    @Override
    public Restaurant restaurant() {
        return restaurant;
    }

    @Override
    public List<MealDay> fetch() {
        HttpFetcher.Response response = http.get(url);
        if (!response.ok()) {
            throw new IllegalStateException(
                    "Data source " + url + " returned status " + response.status() + ": " + response.text());
        }
        return process(MensaXmlParser.parse(response.body(), restaurant));
    }

    /** Pipeline after parsing: merge variants, translate, unify. */
    List<MealDay> process(List<RawMealDay> parsed) {
        List<RawMealDay> merged = normalization.mergeMealVariants(parsed);
        List<RawMealDay> translated = translation.translate(merged);
        return normalization.unifyFoodEntries(translated);
    }
}
