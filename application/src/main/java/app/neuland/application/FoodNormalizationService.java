package app.neuland.application;

import app.neuland.model.food.Meal;
import app.neuland.model.food.MealCategory;
import app.neuland.model.food.MealDay;
import app.neuland.model.food.MealPrice;
import app.neuland.model.food.MealVariation;
import app.neuland.model.food.Restaurant;
import app.neuland.model.food.raw.RawMeal;
import app.neuland.model.food.raw.RawMealDay;
import app.neuland.model.shared.Language;
import com.dynatrace.hash4j.hashing.HashValue128;
import com.dynatrace.hash4j.hashing.Hashing;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Port of {@code food-utils.ts} of the legacy neuland.app-backend: cleans flags, merges meals with
 * a similar name into variants, hashes ids and unifies scraped plans to the domain model.
 */
public class FoodNormalizationService {

    private static final String FLAG_CONTRADICTIONS_RESOURCE = "/flag-contradictions.json";
    private static final String STOP_WORDS_RESOURCE = "/stop-words.json";
    private static final String DEFAULT_CATEGORY = "main";
    private static final Pattern GERMAN_FLOAT_PREFIX =
            Pattern.compile("^[+-]?(\\d+\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?");

    private final Map<String, List<String>> flagContradictions;
    private final Set<String> germanStopWords;

    public FoodNormalizationService() {
        ObjectMapper mapper = new ObjectMapper();
        this.flagContradictions = readResource(mapper, FLAG_CONTRADICTIONS_RESOURCE,
                new TypeReference<Map<String, List<String>>>() {
                });
        Map<String, Object> stopWords = readResource(mapper, STOP_WORDS_RESOURCE,
                new TypeReference<Map<String, Object>>() {
                });
        Set<String> de = new HashSet<>();
        if (stopWords.get("de") instanceof List<?> list) {
            list.forEach(word -> de.add(String.valueOf(word)));
        }
        this.germanStopWords = Set.copyOf(de);
    }

    private static <T> T readResource(ObjectMapper mapper, String resource, TypeReference<T> type) {
        try (InputStream in = FoodNormalizationService.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + resource);
            }
            return mapper.readValue(in, type);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read classpath resource " + resource, e);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Flags
    // ---------------------------------------------------------------------------------------

    /**
     * Removes wrong flags (e.g. "veg" (vegan) and "R" (beef) at the same time =&gt; remove "veg").
     *
     * @return cleaned flags or {@code null} if {@code flags} is {@code null}
     */
    public List<String> cleanMealFlags(List<String> flags) {
        if (flags == null) {
            return null;
        }
        Set<String> contradictions = new HashSet<>();
        for (String flag : flags) {
            List<String> contradicting = flagContradictions.get(flag);
            if (contradicting != null && contradicting.stream().anyMatch(flags::contains)) {
                contradictions.add(flag);
            }
        }
        return flags.stream().filter(flag -> !contradictions.contains(flag)).toList();
    }

    // ---------------------------------------------------------------------------------------
    // Merging of variants
    // ---------------------------------------------------------------------------------------

    /**
     * Merges meals with a similar name and the same category into one meal with variants.
     */
    public List<RawMealDay> mergeMealVariants(List<RawMealDay> days) {
        return days.stream().map(day -> day.withMeals(mergeDayEntries(day.meals()))).toList();
    }

    /**
     * Merges meals with a similar name and the same category into one meal with variants (single
     * day).
     */
    public List<RawMeal> mergeDayEntries(List<RawMeal> dayEntries) {
        List<List<RawMeal>> variantsPerEntry = new ArrayList<>(dayEntries.size());
        Set<String> variantNames = new HashSet<>();
        for (RawMeal meal : dayEntries) {
            List<RawMeal> variants = dayEntries.stream()
                    .filter(other -> !Objects.equals(other.name(), meal.name())
                            && other.name().startsWith(meal.name())
                            && Objects.equals(other.category(), meal.category()))
                    .toList();
            variantsPerEntry.add(variants);
            variants.forEach(variant -> variantNames.add(variant.name()));
        }

        List<RawMeal> result = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();
        for (int i = 0; i < dayEntries.size(); i++) {
            RawMeal meal = dayEntries.get(i);
            if (variantNames.contains(meal.name()) || !seenNames.add(meal.name())) {
                continue;
            }
            List<RawMeal> variants = variantsPerEntry.get(i);
            if (variants.isEmpty()) {
                result.add(meal);
            } else {
                result.add(meal.withVariants(variants.stream()
                        .map(variant -> toVariant(meal, variant))
                        .toList()));
            }
        }
        return result;
    }

    private RawMeal toVariant(RawMeal meal, RawMeal variant) {
        String strippedName = variant.name().replaceFirst(Pattern.quote(meal.name()), "").strip();
        return variant
                .withName(cleanMealName(strippedName))
                .withPrices(priceDifference(variant.prices(), meal.prices()))
                .withAdditional(true)
                .withVariants(List.of());
    }

    private static MealPrice priceDifference(MealPrice variant, MealPrice meal) {
        MealPrice v = variant == null ? new MealPrice(null, null, null) : variant;
        MealPrice m = meal == null ? new MealPrice(null, null, null) : meal;
        return new MealPrice(
                difference(v.student(), m.student()),
                difference(v.employee(), m.employee()),
                difference(v.guest(), m.guest()));
    }

    private static Double difference(Double variant, Double meal) {
        if (variant == null || meal == null) {
            return null;
        }
        // round to cents to get rid of floating point noise (3.9 - 3.15 = 0.75..04)
        return Math.round((variant - meal) * 100.0) / 100.0;
    }

    /** Removes German stop words from the given name. */
    String cleanMealName(String name) {
        List<String> kept = new ArrayList<>();
        for (String word : name.split(" ", -1)) {
            if (!germanStopWords.contains(word)) {
                kept.add(word);
            }
        }
        return String.join(" ", kept);
    }

    // ---------------------------------------------------------------------------------------
    // Unification to the domain model
    // ---------------------------------------------------------------------------------------

    /**
     * Unifies translated meal plans to the domain model. Every meal and variant must have {@code
     * names} with German and English text.
     */
    public List<MealDay> unifyFoodEntries(List<RawMealDay> days) {
        return days.stream()
                .map(day -> new MealDay(day.date(), day.meals().stream().map(this::unifyMeal).toList()))
                .toList();
    }

    private Meal unifyMeal(RawMeal meal) {
        String category = meal.category() != null ? meal.category() : DEFAULT_CATEGORY;
        Restaurant restaurant = Objects.requireNonNull(meal.restaurant(),
                () -> "Restaurant of meal " + meal.name() + " is missing");
        List<MealVariation> variations = meal.variants().stream()
                .map(variant -> unifyVariant(variant, meal))
                .toList();
        return new Meal(
                meal.id(),
                capitalize(meal),
                standardizeCategory(category),
                priceOrEmpty(meal.prices()),
                nullToEmpty(meal.allergens()),
                nullToEmpty(cleanMealFlags(meal.flags())),
                meal.nutrition(),
                meal.originalLanguage() != null ? meal.originalLanguage() : Language.DE,
                Boolean.TRUE.equals(meal.isStatic()),
                restaurant,
                variations);
    }

    private MealVariation unifyVariant(RawMeal variant, RawMeal parent) {
        Restaurant restaurant = variant.restaurant() != null ? variant.restaurant() : parent.restaurant();
        return new MealVariation(
                parent.id() + "/" + variant.id(),
                capitalize(variant),
                Boolean.TRUE.equals(variant.additional()),
                priceOrEmpty(variant.prices()),
                nullToEmpty(variant.allergens()),
                nullToEmpty(cleanMealFlags(variant.flags())),
                variant.nutrition(),
                variant.originalLanguage() != null ? variant.originalLanguage() : Language.DE,
                Boolean.TRUE.equals(variant.isStatic()),
                restaurant,
                parent.id());
    }

    private static MealPrice priceOrEmpty(MealPrice price) {
        return price != null ? price : new MealPrice(null, null, null);
    }

    private static List<String> nullToEmpty(List<String> list) {
        return list != null ? list : List.of();
    }

    /**
     * Capitalizes the first letter of the meal names.
     *
     * @throws IllegalArgumentException if the meal is not translated to German and English
     */
    private static Map<Language, String> capitalize(RawMeal meal) {
        Map<Language, String> names = meal.names();
        if (names == null || names.get(Language.DE) == null || names.get(Language.EN) == null) {
            throw new IllegalArgumentException("Meal names must have both de and en: " + meal.name());
        }
        Map<Language, String> capitalized = new EnumMap<>(Language.class);
        names.forEach((language, name) -> capitalized.put(language, capitalizeFirst(name)));
        return capitalized;
    }

    static String capitalizeFirst(String value) {
        if (value.isEmpty()) {
            return value;
        }
        int firstLength = Character.charCount(value.codePointAt(0));
        return value.substring(0, firstLength).toUpperCase() + value.substring(firstLength);
    }

    /**
     * Standardizes the (source specific) category.
     */
    public static MealCategory standardizeCategory(String category) {
        if (category == null) {
            return MealCategory.MAIN;
        }
        return switch (category) {
            case "main" -> MealCategory.MAIN;
            case "soup" -> MealCategory.SOUP;
            case "salad" -> MealCategory.SALAD;
            default -> {
                if (category.contains("Suppe")) {
                    yield MealCategory.SOUP;
                }
                if (category.contains("Salat")) {
                    yield MealCategory.SALAD;
                }
                yield MealCategory.MAIN;
            }
        };
    }

    // ---------------------------------------------------------------------------------------
    // Hashes
    // ---------------------------------------------------------------------------------------

    /**
     * Pseudo-unique, day-scoped id of a meal: the last two digits of the day (without dashes)
     * followed by the first six characters of the base 36 xxHash32 of the meal name.
     *
     * @param day day in ISO format (yyyy-MM-dd)
     */
    public static String getMealDayHash(String day, String mealName) {
        String compactDay = day.replace("-", "");
        String dayHash = compactDay.length() > 2 ? compactDay.substring(compactDay.length() - 2) : compactDay;
        String nameHash = Long.toString(Integer.toUnsignedLong(XxHash32.hash(mealName)), 36);
        return dayHash + nameHash.substring(0, Math.min(6, nameHash.length()));
    }

    public static String getMealDayHash(LocalDate day, String mealName) {
        return getMealDayHash(day.toString(), mealName);
    }

    /**
     * Stable id of a meal (name in the original language, category and restaurant): xxh3 128 bit as
     * base 36 string.
     */
    public static String getMealHash(String mealNameDe, MealCategory category, Restaurant restaurant) {
        String input = mealNameDe + "-" + category.name() + "-" + restaurant.name();
        HashValue128 hash = Hashing.xxh3_128().hashBytesTo128Bits(input.getBytes(StandardCharsets.UTF_8));
        BigInteger value = unsigned(hash.getMostSignificantBits())
                .shiftLeft(64)
                .or(unsigned(hash.getLeastSignificantBits()));
        return value.toString(36);
    }

    private static BigInteger unsigned(long value) {
        return new BigInteger(Long.toUnsignedString(value));
    }

    // ---------------------------------------------------------------------------------------
    // Number parsing
    // ---------------------------------------------------------------------------------------

    /**
     * Parses a float like "1,5" (lenient like JavaScript's {@code parseFloat}, trailing garbage is
     * ignored).
     *
     * @return the parsed value or {@code null} if the text is {@code null} or not a number
     */
    public static Double parseGermanFloat(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = GERMAN_FLOAT_PREFIX.matcher(text.replace(',', '.').stripLeading());
        if (!matcher.find()) {
            return null;
        }
        try {
            return Double.parseDouble(matcher.group());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parses the text of an XML node containing a float.
     *
     * @return the parsed value or {@code 0} if the node is missing, empty or not a number
     */
    public static double parseXmlFloat(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        Double parsed = parseGermanFloat(text);
        return parsed != null ? parsed : 0;
    }

    /** Checks whether a value is {@code null} or blank. */
    public static boolean isEmpty(String value) {
        return value == null || value.isBlank();
    }

    /** Helper to build ordered language maps. */
    public static Map<Language, String> names(String de, String en) {
        Map<Language, String> names = new LinkedHashMap<>();
        names.put(Language.DE, de);
        names.put(Language.EN, en);
        return names;
    }
}
