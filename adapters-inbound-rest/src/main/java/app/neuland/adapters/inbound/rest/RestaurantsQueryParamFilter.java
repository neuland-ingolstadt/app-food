package app.neuland.adapters.inbound.rest;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

/**
 * The contract defines {@code restaurants} as {@code style: form, explode: false}, i.e. a comma
 * separated list ({@code ?restaurants=INGOLSTADT_MENSA,REIMANNS}). JAX-RS binds repeated
 * parameters, so the comma separated form is expanded to repeated parameters before matching.
 */
public class RestaurantsQueryParamFilter {

    static final String PARAMETER = "restaurants";

    @ServerRequestFilter(preMatching = true)
    public void expandRestaurants(ContainerRequestContext requestContext) {
        URI uri = requestContext.getUriInfo().getRequestUri();
        String expanded = expand(uri.getRawQuery());
        if (expanded != null) {
            requestContext.setRequestUri(UriBuilder.fromUri(uri).replaceQuery(expanded).build());
        }
    }

    /** @return the rewritten raw query, or {@code null} if nothing has to be rewritten */
    static String expand(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return null;
        }
        boolean changed = false;
        List<String> pairs = new ArrayList<>();
        for (String pair : rawQuery.split("&")) {
            int separator = pair.indexOf('=');
            String name = separator < 0 ? pair : pair.substring(0, separator);
            String value = separator < 0 ? "" : pair.substring(separator + 1);
            if (PARAMETER.equals(name) && (value.contains(",") || value.toLowerCase().contains("%2c"))) {
                for (String part : value.split(",|%2[cC]")) {
                    if (!part.isEmpty()) {
                        pairs.add(PARAMETER + "=" + part);
                    }
                }
                changed = true;
            } else {
                pairs.add(pair);
            }
        }
        return changed ? String.join("&", pairs) : null;
    }
}
