package app.neuland.adapters.inbound.rest;

import app.neuland.adapters.inbound.rest.error.MealNotFoundException;
import app.neuland.backend.food.api.v0.FoodApi;
import app.neuland.backend.food.api.v0.model.Meal;
import app.neuland.backend.food.api.v0.model.MealDay;
import app.neuland.backend.food.api.v0.model.Restaurant;
import app.neuland.ports.inbound.FoodQueryUseCase;
import jakarta.inject.Inject;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.jboss.resteasy.reactive.RestResponse;

public class FoodResource implements FoodApi {

    private final FoodQueryUseCase foodQueryUseCase;

    @Inject
    public FoodResource(FoodQueryUseCase foodQueryUseCase) {
        this.foodQueryUseCase = foodQueryUseCase;
    }

    @Override
    public RestResponse<List<MealDay>> listMeals(Set<Restaurant> restaurants, LocalDate mealDateFrom,
            LocalDate mealDateTo) {
        if (restaurants == null || restaurants.isEmpty()) {
            throw new IllegalArgumentException("At least one restaurant is required");
        }
        if (mealDateFrom == null || mealDateTo == null) {
            throw new IllegalArgumentException("mealDateFrom and mealDateTo are required");
        }
        if (mealDateFrom.isAfter(mealDateTo)) {
            throw new IllegalArgumentException("mealDateFrom must not be after mealDateTo");
        }
        List<app.neuland.model.food.Restaurant> domainRestaurants = restaurants.stream()
                .map(FoodMapper::toDomain)
                .sorted()
                .toList();
        return RestResponse.ok(FoodMapper.toApi(foodQueryUseCase.listMeals(domainRestaurants, mealDateFrom, mealDateTo)));
    }

    @Override
    public RestResponse<Meal> getMeal(String mealId) {
        return foodQueryUseCase.getMeal(mealId)
                .map(FoodMapper::toApi)
                .map(RestResponse::ok)
                .orElseThrow(() -> new MealNotFoundException(mealId));
    }
}
