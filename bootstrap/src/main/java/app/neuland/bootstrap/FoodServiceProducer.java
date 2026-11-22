package app.neuland.bootstrap;

import app.neuland.application.FoodNormalizationService;
import app.neuland.application.FoodQueryService;
import app.neuland.ports.outbound.FoodCacheRepository;
import app.neuland.ports.outbound.MenuSource;
import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Wires the framework independent application services (hexagonal core) into CDI. */
@ApplicationScoped
public class FoodServiceProducer {

    @Produces
    @Singleton
    FoodNormalizationService foodNormalizationService() {
        return new FoodNormalizationService();
    }

    /**
     * The cache implementation is selected at runtime by {@code food.cache.in-memory} (see the
     * {@code LookupIfProperty} annotations on the repositories).
     */
    @Produces
    @Singleton
    FoodQueryService foodQueryService(
            Instance<FoodCacheRepository> cache,
            @All List<MenuSource> menuSources,
            @ConfigProperty(name = "food.cache.ttl-seconds", defaultValue = "1800") long ttlSeconds) {
        return new FoodQueryService(cache.get(), menuSources, Duration.ofSeconds(ttlSeconds));
    }

    void close(@Disposes FoodQueryService service) {
        service.close();
    }
}
