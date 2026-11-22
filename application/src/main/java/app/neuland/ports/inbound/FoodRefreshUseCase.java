package app.neuland.ports.inbound;

/**
 * Forces a refresh of the cached menus (e.g. triggered by a scheduler to keep the cache warm).
 */
public interface FoodRefreshUseCase {

    /**
     * Fetches the menus of all restaurants from their sources and stores them in the cache.
     * Failures of single restaurants are logged and do not abort the refresh of the others.
     */
    void refreshAll();
}
