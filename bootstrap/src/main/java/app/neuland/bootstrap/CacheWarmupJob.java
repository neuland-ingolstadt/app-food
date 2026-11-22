package app.neuland.bootstrap;

import app.neuland.ports.inbound.FoodRefreshUseCase;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Keeps the menu cache warm: the cache entries expire after 30 minutes, the job refreshes them
 * every 25 minutes (set {@code food.warmup.every=off} to disable).
 */
@ApplicationScoped
public class CacheWarmupJob {

    private final FoodRefreshUseCase refresh;

    @Inject
    public CacheWarmupJob(FoodRefreshUseCase refresh) {
        this.refresh = refresh;
    }

    @Scheduled(
            identity = "food-cache-warmup",
            every = "{food.warmup.every}",
            delayed = "{food.warmup.delay}",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void warmCache() {
        refresh.refreshAll();
    }
}
