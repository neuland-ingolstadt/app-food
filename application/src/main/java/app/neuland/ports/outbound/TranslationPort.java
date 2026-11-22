package app.neuland.ports.outbound;

import app.neuland.model.food.raw.RawMealDay;
import java.util.List;

public interface TranslationPort {

    /**
     * Translates the German names of all meals and their variants to English.
     *
     * <p>The returned plan has {@code names} (DE and EN) populated on every meal and variant.
     * Implementations must never fail: if translation is not possible the German name is used as
     * English name.
     */
    List<RawMealDay> translate(List<RawMealDay> days);
}
