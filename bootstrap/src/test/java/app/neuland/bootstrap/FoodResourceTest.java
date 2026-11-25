package app.neuland.bootstrap;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;

import app.neuland.model.food.Restaurant;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class FoodResourceTest {

    @Test
    void listsMealsMergedByDayForCommaSeparatedRestaurants() {
        given()
                .queryParam("restaurants", "INGOLSTADT_MENSA,REIMANNS")
                .queryParam("mealDateFrom", "2026-11-23")
                .queryParam("mealDateTo", "2026-11-24")
        .when()
                .get("/meals")
        .then()
                .statusCode(200)
                .contentType("application/json")
                // Tuesday only has the Mensa meal, Reimanns has no meals that day
                .body("", hasSize(2))
                .body("[0].date", equalTo("2026-11-23"))
                .body("[0].meals.id", contains("23aaaaaa", "23dddddd", "23eeeeee"))
                .body("[0].meals[0].name.de", equalTo("Spaghetti Bolognese"))
                .body("[0].meals[0].category", equalTo("MAIN"))
                .body("[0].meals[0].restaurant", equalTo("INGOLSTADT_MENSA"))
                .body("[0].meals[0].originalLanguage", equalTo("de"))
                .body("[0].meals[0].static", equalTo(false))
                .body("[0].meals[0].price.student", equalTo(3.9f))
                .body("[0].meals[0].price.guest", nullValue())
                .body("[0].meals[0].allergens", contains("A", "G"))
                .body("[0].meals[0].flags", contains("veg"))
                .body("[0].meals[0].nutrition.kcal", equalTo(358.0f))
                .body("[0].meals[0].variations", hasSize(1))
                .body("[0].meals[0].variations[0].parentMealId", equalTo("23aaaaaa"))
                .body("[0].meals[0].variations[0].additional", equalTo(true))
                .body("[0].meals[2].static", equalTo(true))
                .body("[1].date", equalTo("2026-11-24"))
                .body("[1].meals.id", contains("24cccccc"))
                .body("[1].meals[0].name.en", equalTo("Vegetable stir-fry"));
    }

    @Test
    void acceptsRepeatedRestaurantParameters() {
        given()
                .queryParam("restaurants", "REIMANNS", "NEUBURG_MENSA")
                .queryParam("mealDateFrom", "2026-11-23")
                .queryParam("mealDateTo", "2026-11-23")
        .when()
                .get("/meals")
        .then()
                .statusCode(200)
                .body("", hasSize(1))
                .body("[0].meals", hasSize(2));
    }

    @Test
    void filtersByDateRangeAndOmitsEmptyDays() {
        given()
                .queryParam("restaurants", "INGOLSTADT_MENSA")
                .queryParam("mealDateFrom", "2026-11-25")
                .queryParam("mealDateTo", "2026-11-30")
        .when()
                .get("/meals")
        .then()
                .statusCode(200)
                .body("", empty());
    }

    @Test
    void servesRepeatedRequestsFromTheCache() {
        for (int i = 0; i < 3; i++) {
            given()
                    .queryParam("restaurants", "CANISIUS,INGOLSTADT_MENSA")
                    .queryParam("mealDateFrom", "2026-11-23")
                    .queryParam("mealDateTo", "2026-11-24")
            .when()
                    .get("/meals")
            .then()
                    .statusCode(200);
        }
        for (Restaurant restaurant : new Restaurant[] {Restaurant.CANISIUS, Restaurant.INGOLSTADT_MENSA}) {
            org.hamcrest.MatcherAssert.assertThat(StubMenuSources.FETCHES.get(restaurant).get(), lessThanOrEqualTo(1));
        }
    }

    @Test
    void rejectsMissingParameters() {
        given()
                .queryParam("restaurants", "REIMANNS")
        .when()
                .get("/meals")
        .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", equalTo(400))
                .body("title", equalTo("Bad Request"));
    }

    @Test
    void rejectsInvalidDateRange() {
        given()
                .queryParam("restaurants", "REIMANNS")
                .queryParam("mealDateFrom", "2026-11-25")
                .queryParam("mealDateTo", "2026-11-23")
        .when()
                .get("/meals")
        .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", equalTo(400))
                .body("title", equalTo("Bad Request"))
                .body("detail", containsString("mealDateFrom"));
    }

    @Test
    void rejectsUnknownRestaurant() {
        given()
                .queryParam("restaurants", "UNKNOWN")
                .queryParam("mealDateFrom", "2026-11-23")
                .queryParam("mealDateTo", "2026-11-23")
        .when()
                .get("/meals")
        .then()
                .statusCode(400)
                .contentType("application/problem+json")
                .body("status", equalTo(400))
                .body("title", equalTo("Bad Request"));
    }

    @Test
    void getsMealById() {
        given()
        .when()
                .get("/meals/23dddddd")
        .then()
                .statusCode(200)
                .body("id", equalTo("23dddddd"))
                .body("name.en", equalTo("Beef roulade"))
                .body("restaurant", equalTo("REIMANNS"));
    }

    @Test
    void returnsProblemForUnknownMeal() {
        given()
        .when()
                .get("/meals/doesnotexist")
        .then()
                .statusCode(404)
                .contentType("application/problem+json")
                .body("status", equalTo(404))
                .body("title", equalTo("Not Found"))
                .body("detail", containsString("doesnotexist"));
    }
}
