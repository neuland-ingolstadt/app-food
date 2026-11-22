package app.neuland.adapters.outbound.persistence;

import app.neuland.model.food.MealDay;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;

/** JSON (de)serialization of cached menus. */
final class MealDayJson {

    private static final TypeReference<List<MealDay>> TYPE = new TypeReference<>() {
    };

    private final ObjectMapper mapper;

    MealDayJson(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    String write(List<MealDay> days) {
        try {
            return mapper.writeValueAsString(days);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize meal days", e);
        }
    }

    List<MealDay> read(String json) throws JsonProcessingException {
        return mapper.readValue(json, TYPE);
    }
}
