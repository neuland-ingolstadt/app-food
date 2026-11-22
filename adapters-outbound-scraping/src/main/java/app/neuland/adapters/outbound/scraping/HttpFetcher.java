package app.neuland.adapters.outbound.scraping;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/** Thin wrapper around the JDK {@link HttpClient} used by all scrapers. */
@ApplicationScoped
public class HttpFetcher {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final String USER_AGENT = "neuland-app-food";

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public record Response(int status, byte[] body) {

        public boolean ok() {
            return status >= 200 && status < 300;
        }

        public String text() {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    public Response get(String url) {
        return send(HttpRequest.newBuilder(URI.create(url)).GET(), Map.of());
    }

    public Response postJson(String url, String json, Map<String, String> headers) {
        return send(HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)), headers);
    }

    private Response send(HttpRequest.Builder builder, Map<String, String> headers) {
        builder.timeout(REQUEST_TIMEOUT).header("User-Agent", USER_AGENT);
        headers.forEach(builder::header);
        try {
            HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            return new Response(response.statusCode(), response.body());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedIOException(new IOException("Interrupted while requesting " + builder.build().uri(), e));
        }
    }
}
