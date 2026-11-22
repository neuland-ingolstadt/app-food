package app.neuland.adapters.outbound.scraping;

import app.neuland.application.FoodNormalizationService;
import app.neuland.model.food.MealDay;
import app.neuland.model.food.Restaurant;
import app.neuland.model.food.raw.RawMealDay;
import app.neuland.ports.outbound.MenuSource;
import app.neuland.ports.outbound.TranslationPort;
import io.quarkus.arc.DefaultBean;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.logging.Logger;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Downloads the weekly PDF of the Canisiusstiftung, extracts its text with PDFBox and parses it. */
@Singleton
@DefaultBean
public class CanisiusPdfMenuSource implements MenuSource {

    public static final String DEFAULT_URL = "http://www.canisiusstiftung.de/wp-content/uploads/Speiseplan/speiseplan.pdf";

    private static final Logger LOG = Logger.getLogger(CanisiusPdfMenuSource.class.getName());

    private final String url;
    private final HttpFetcher http;
    private final FoodNormalizationService normalization;
    private final TranslationPort translation;

    @Inject
    public CanisiusPdfMenuSource(
            HttpFetcher http,
            FoodNormalizationService normalization,
            TranslationPort translation,
            @ConfigProperty(name = "food.canisius.pdf-url", defaultValue = DEFAULT_URL) String url) {
        this.http = http;
        this.normalization = normalization;
        this.translation = translation;
        this.url = url;
    }

    @Override
    public Restaurant restaurant() {
        return Restaurant.CANISIUS;
    }

    @Override
    public List<MealDay> fetch() {
        HttpFetcher.Response response = http.get(url);
        if (response.status() == 404 || (response.ok() && response.body().length == 0)) {
            LOG.warning("Canisius PDF is not available (status " + response.status() + "), returning no meals");
            return List.of();
        }
        if (!response.ok()) {
            throw new IllegalStateException("Canisius PDF returned status " + response.status());
        }

        List<RawMealDay> plan = CanisiusTextParser.parse(extractText(response.body()));
        if (plan.isEmpty()) {
            LOG.warning("Canisius PDF is empty or restaurant is closed, returning no meals");
            return List.of();
        }
        return process(plan);
    }

    List<MealDay> process(List<RawMealDay> plan) {
        List<RawMealDay> merged = normalization.mergeMealVariants(plan);
        List<RawMealDay> translated = translation.translate(merged);
        return normalization.unifyFoodEntries(translated);
    }

    static String extractText(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read Canisius PDF", e);
        }
    }
}
