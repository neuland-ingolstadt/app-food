package app.neuland.adapters.outbound.scraping;

import app.neuland.application.FoodNormalizationService;
import app.neuland.model.food.MealPrice;
import app.neuland.model.food.Restaurant;
import app.neuland.model.food.raw.RawMeal;
import app.neuland.model.food.raw.RawMealDay;
import app.neuland.model.shared.Language;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Heuristic parser for the text of the Canisius weekly PDF (port of the legacy {@code
 * canisius.ts}). Expects the plain text extracted from the PDF.
 */
public final class CanisiusTextParser {

    private static final Pattern DATE = Pattern.compile("([0-9]{1,2}).([0-9]{1,2}).([0-9]{4})");
    private static final Pattern NEW_LINE = Pattern.compile("(?:\\r\\n|\\r|\\n)");
    private static final Pattern DISH_SPLITTER = Pattern.compile(
            "\\s+€[ ]{1,}[0-9]+.[0-9]+\\s+€[ ]{1,}[0-9]+.[0-9]+\\s+€[ ]{1,}[0-9]+.[0-9]+\\s*");
    private static final Pattern PRICE = Pattern.compile("[\\d.]+");
    private static final String SALAD_SEPARATOR = "Salate der Saison vom Büfett";
    private static final int DAYS_PER_WEEK = 5;

    private record Block(String name, MealPrice prices) {
    }

    private CanisiusTextParser() {
    }

    /**
     * @return one day per weekday of the PDF; an empty list if the PDF is empty or the restaurant is
     *     closed (e.g. during the summer break)
     */
    public static List<RawMealDay> parse(String pdfText) {
        String text = NEW_LINE.matcher(pdfText == null ? "" : pdfText).replaceAll(" ");
        if (FoodNormalizationService.isEmpty(text)) {
            // during the summer break the pdf is completely empty
            return List.of();
        }

        List<int[]> dateMatches = new ArrayList<>(); // start, end
        List<LocalDate> dates = new ArrayList<>();
        Matcher matcher = DATE.matcher(text);
        while (dateMatches.size() < DAYS_PER_WEEK && matcher.find()) {
            dateMatches.add(new int[] {matcher.start(), matcher.end()});
            dates.add(LocalDate.of(
                    Integer.parseInt(matcher.group(3)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(1))));
        }
        if (dateMatches.size() < DAYS_PER_WEEK) {
            // not a full week (or "geschlossen")
            return List.of();
        }

        // split text at the dates to get day segments
        List<String> days = new ArrayList<>();
        for (int i = 0; i < dateMatches.size(); i++) {
            int start = dateMatches.get(i)[1];
            int end = i < dateMatches.size() - 1 ? dateMatches.get(i + 1)[0] : text.length();
            days.add(text.substring(start, end));
        }

        // split last day into friday and weekly salad menu
        List<Block> salads = List.of();
        String[] fridaySaladSplit = days.get(4).split(Pattern.quote(SALAD_SEPARATOR), -1);
        if (fridaySaladSplit.length >= 2) {
            days.set(4, fridaySaladSplit[0]);
            salads = mealsFromBlock(fridaySaladSplit[1]);
        }

        List<RawMealDay> result = new ArrayList<>();
        for (int i = 0; i < days.size(); i++) {
            LocalDate date = dates.get(i);
            List<Block> dishes = mealsFromBlock(days.get(i));
            List<RawMeal> meals = new ArrayList<>();
            if (!dishes.isEmpty()) {
                dishes.forEach(dish -> meals.add(meal(date, dish, "Essen")));
                salads.forEach(salad -> meals.add(meal(date, salad, "Salat")));
            }
            result.add(new RawMealDay(date, meals));
        }
        return result;
    }

    private static RawMeal meal(LocalDate date, Block block, String category) {
        return new RawMeal(
                FoodNormalizationService.getMealDayHash(date, block.name()),
                block.name(),
                null,
                category,
                block.prices(),
                null,
                null,
                null,
                Language.DE,
                null,
                Restaurant.CANISIUS,
                null,
                List.of());
    }

    private static List<Block> mealsFromBlock(String text) {
        String[] parts = DISH_SPLITTER.split(text.strip(), -1);
        // everything after the last price triple is not a dish
        int dishCount = Math.max(0, parts.length - 1);

        List<List<Double>> prices = new ArrayList<>();
        Matcher splitter = DISH_SPLITTER.matcher(text);
        while (splitter.find()) {
            prices.add(prices(splitter.group()));
        }

        List<Block> blocks = new ArrayList<>();
        for (int i = 0; i < dishCount; i++) {
            List<Double> dishPrices = i < prices.size() ? prices.get(i) : List.of();
            /*
             * There are three different prices for each dish:
             * 1. internal student price => not used for THI students
             * 2. external student price => used for THI students
             * 3. guest price => used for guests and THI employees
             */
            blocks.add(new Block(parts[i].strip(),
                    new MealPrice(price(dishPrices, 1), price(dishPrices, 2), price(dishPrices, 2))));
        }
        return blocks;
    }

    private static List<Double> prices(String text) {
        List<Double> prices = new ArrayList<>();
        Matcher matcher = PRICE.matcher(text);
        while (matcher.find()) {
            prices.add(FoodNormalizationService.parseGermanFloat(matcher.group()));
        }
        return prices;
    }

    private static Double price(List<Double> prices, int index) {
        return index < prices.size() ? prices.get(index) : null;
    }
}
