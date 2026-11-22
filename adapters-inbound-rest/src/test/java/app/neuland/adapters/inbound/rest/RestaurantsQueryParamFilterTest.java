package app.neuland.adapters.inbound.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class RestaurantsQueryParamFilterTest {

    @Test
    void expandsCommaSeparatedValues() {
        assertEquals("restaurants=A&restaurants=B&mealDateFrom=2026-11-23",
                RestaurantsQueryParamFilter.expand("restaurants=A,B&mealDateFrom=2026-11-23"));
        assertEquals("restaurants=A&restaurants=B", RestaurantsQueryParamFilter.expand("restaurants=A%2CB"));
    }

    @Test
    void leavesOtherQueriesUntouched() {
        assertNull(RestaurantsQueryParamFilter.expand(null));
        assertNull(RestaurantsQueryParamFilter.expand(""));
        assertNull(RestaurantsQueryParamFilter.expand("restaurants=A&restaurants=B"));
        assertNull(RestaurantsQueryParamFilter.expand("other=a,b"));
    }
}
