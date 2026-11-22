package app.neuland.adapters.outbound.scraping;

import app.neuland.application.RawMealTranslations;
import app.neuland.model.food.raw.RawMealDay;
import app.neuland.ports.outbound.TranslationPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Translates German meal names to English with DeepL. Without API key (or when disabled) or if
 * DeepL fails, the German name is used as English name.
 */
@ApplicationScoped
public class DeepLTranslationAdapter implements TranslationPort {

    private static final Logger LOG = Logger.getLogger(DeepLTranslationAdapter.class.getName());
    private static final int MAX_TEXTS_PER_REQUEST = 50;
    private static final int MAX_CACHED_TRANSLATIONS = 5000;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpFetcher http;
    private final boolean enabled;
    private final String apiKey;
    private final String serverUrl;
    private final Map<String, String> translations = new ConcurrentHashMap<>();

    @Inject
    public DeepLTranslationAdapter(
            HttpFetcher http,
            @ConfigProperty(name = "food.deepl.enabled", defaultValue = "true") boolean enabled,
            @ConfigProperty(name = "food.deepl.api-key") Optional<String> apiKey,
            @ConfigProperty(name = "food.deepl.server-url") Optional<String> serverUrl) {
        this.http = http;
        this.enabled = enabled;
        this.apiKey = apiKey.map(String::strip).orElse("");
        this.serverUrl = serverUrl.filter(url -> !url.isBlank()).orElseGet(() ->
                this.apiKey.endsWith(":fx") ? "https://api-free.deepl.com" : "https://api.deepl.com");
    }

    @Override
    public List<RawMealDay> translate(List<RawMealDay> days) {
        if (!enabled || apiKey.isEmpty()) {
            return RawMealTranslations.identity(days);
        }
        List<String> missing = RawMealTranslations.collectNames(days).stream()
                .filter(name -> !translations.containsKey(name))
                .toList();
        try {
            for (int from = 0; from < missing.size(); from += MAX_TEXTS_PER_REQUEST) {
                translateChunk(missing.subList(from, Math.min(missing.size(), from + MAX_TEXTS_PER_REQUEST)));
            }
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "DeepL translation failed, falling back to untranslated meals", e);
            return RawMealTranslations.identity(days);
        }
        return RawMealTranslations.apply(days, translations);
    }

    private void translateChunk(List<String> texts) {
        ObjectNode request = MAPPER.createObjectNode();
        ArrayNode text = request.putArray("text");
        texts.forEach(text::add);
        request.put("target_lang", "EN-GB");
        request.put("source_lang", "DE");
        request.put("split_sentences", "1");

        HttpFetcher.Response response;
        try {
            response = http.postJson(serverUrl + "/v2/translate", MAPPER.writeValueAsString(request),
                    Map.of("Authorization", "DeepL-Auth-Key " + apiKey));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        if (!response.ok()) {
            throw new IllegalStateException("DeepL API request failed with status " + response.status());
        }

        JsonNode result;
        try {
            result = MAPPER.readTree(response.body());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("DeepL returned invalid JSON", e);
        }
        JsonNode items = result.path("translations");
        if (!items.isArray() || items.size() != texts.size()) {
            throw new IllegalStateException("DeepL returned an unexpected number of translations");
        }
        if (translations.size() > MAX_CACHED_TRANSLATIONS) {
            translations.clear();
        }
        List<String> translated = new ArrayList<>();
        items.forEach(item -> translated.add(item.path("text").asText("")));
        for (int i = 0; i < texts.size(); i++) {
            translations.put(texts.get(i), translated.get(i));
        }
    }
}
