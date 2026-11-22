package app.neuland.adapters.outbound.scraping;

import app.neuland.application.FoodNormalizationService;
import app.neuland.model.food.Restaurant;
import app.neuland.ports.outbound.TranslationPort;
import io.quarkus.arc.DefaultBean;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@Singleton
@DefaultBean
public class IngolstadtMensaMenuSource extends MensaMenuSource {

    public static final String DEFAULT_URL =
            "https://www.max-manager.de/daten-extern/sw-erlangen-nuernberg/xml/mensa-ingolstadt.xml";

    @Inject
    public IngolstadtMensaMenuSource(
            HttpFetcher http,
            FoodNormalizationService normalization,
            TranslationPort translation,
            @ConfigProperty(name = "food.mensa.ingolstadt.url", defaultValue = DEFAULT_URL) String url) {
        super(Restaurant.INGOLSTADT_MENSA, url, http, normalization, translation);
    }
}
