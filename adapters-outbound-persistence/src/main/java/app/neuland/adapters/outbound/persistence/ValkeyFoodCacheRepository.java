package app.neuland.adapters.outbound.persistence;

import app.neuland.model.food.MealDay;
import app.neuland.model.food.Restaurant;
import app.neuland.ports.outbound.FoodCacheRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.lookup.LookupIfProperty;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Valkey/Redis backed cache. Key {@code food:menu:{Restaurant}}, value is the JSON serialized
 * {@code List<MealDay>}. Active if {@code food.cache.in-memory=false}.
 */
@ApplicationScoped
@LookupIfProperty(name = "food.cache.in-memory", stringValue = "false")
public class ValkeyFoodCacheRepository implements FoodCacheRepository {

    private static final Logger LOG = Logger.getLogger(ValkeyFoodCacheRepository.class.getName());
    static final String KEY_PREFIX = "food:menu:";

    private final ValueCommands<String, String> values;
    private final MealDayJson json;

    @Inject
    public ValkeyFoodCacheRepository(RedisDataSource dataSource, ObjectMapper mapper) {
        this.values = dataSource.value(String.class);
        this.json = new MealDayJson(mapper);
    }

    static String key(Restaurant restaurant) {
        return KEY_PREFIX + restaurant.name();
    }

    @Override
    public Optional<List<MealDay>> get(Restaurant restaurant) {
        String value = values.get(key(restaurant));
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(json.read(value));
        } catch (JsonProcessingException e) {
            LOG.log(Level.WARNING, "Ignoring unreadable cache entry for " + restaurant, e);
            return Optional.empty();
        }
    }

    @Override
    public void put(Restaurant restaurant, List<MealDay> days, Duration ttl) {
        values.setex(key(restaurant), Math.max(1, ttl.toSeconds()), json.write(days));
    }
}
