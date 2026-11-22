package app.neuland.adapters.outbound.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.neuland.model.food.MealDay;
import app.neuland.model.food.Restaurant;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryFoodCacheRepositoryTest {

    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-23T10:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void returnsEntriesUntilTtlExpires() {
        MutableClock clock = new MutableClock();
        InMemoryFoodCacheRepository cache = new InMemoryFoodCacheRepository(clock);
        List<MealDay> days = List.of(new MealDay(LocalDate.of(2026, 11, 23), List.of()));

        assertTrue(cache.get(Restaurant.REIMANNS).isEmpty());
        cache.put(Restaurant.REIMANNS, days, Duration.ofSeconds(1800));

        assertEquals(days, cache.get(Restaurant.REIMANNS).orElseThrow());
        assertTrue(cache.get(Restaurant.CANISIUS).isEmpty());

        clock.now = clock.now.plusSeconds(1799);
        assertTrue(cache.get(Restaurant.REIMANNS).isPresent());
        clock.now = clock.now.plusSeconds(1);
        assertTrue(cache.get(Restaurant.REIMANNS).isEmpty());
    }

    @Test
    void emptyListIsAValidCachedValue() {
        InMemoryFoodCacheRepository cache = new InMemoryFoodCacheRepository();
        cache.put(Restaurant.CANISIUS, List.of());
        assertEquals(List.of(), cache.get(Restaurant.CANISIUS).orElseThrow());
    }
}
