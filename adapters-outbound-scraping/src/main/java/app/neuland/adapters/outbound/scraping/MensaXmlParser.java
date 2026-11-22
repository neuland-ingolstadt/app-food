package app.neuland.adapters.outbound.scraping;

import app.neuland.application.FoodNormalizationService;
import app.neuland.model.food.MealPrice;
import app.neuland.model.food.Nutrition;
import app.neuland.model.food.Restaurant;
import app.neuland.model.food.raw.RawMeal;
import app.neuland.model.food.raw.RawMealDay;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.text.Collator;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Parses the max-manager.de XML meal plan of the Studierendenwerk (port of {@code
 * parseDataFromXml} in the legacy {@code mensa.ts}).
 */
public final class MensaXmlParser {

    static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private static final Pattern ADD_IN = Pattern.compile("\\s*\\((.*?)\\)\\s*");
    private static final Pattern FLAG = Pattern.compile("class='infomax-food-icon .*?'");

    private MensaXmlParser() {
    }

    public static List<RawMealDay> parse(String xml, Restaurant restaurant) {
        return parse(new InputSource(new StringReader(xml)), restaurant);
    }

    /** Parses the XML honoring the encoding declared in the XML prolog. */
    public static List<RawMealDay> parse(byte[] xml, Restaurant restaurant) {
        return parse(new InputSource(new ByteArrayInputStream(xml)), restaurant);
    }

    public static List<RawMealDay> parse(InputStream xml, Restaurant restaurant) {
        return parse(new InputSource(xml), restaurant);
    }

    private static List<RawMealDay> parse(InputSource source, Restaurant restaurant) {
        Document document = readDocument(source);
        Element root = document.getDocumentElement();
        if (root == null || !"speiseplan".equals(root.getTagName())) {
            return List.of();
        }

        List<RawMealDay> days = new ArrayList<>();
        for (Element dayElement : children(root, "tag")) {
            String timestamp = dayElement.getAttribute("timestamp");
            if (timestamp.isEmpty()) {
                continue;
            }
            LocalDate date = Instant.ofEpochSecond(Long.parseLong(timestamp.trim())).atZone(BERLIN).toLocalDate();
            List<RawMeal> meals = new ArrayList<>();
            for (Element item : children(dayElement, "item")) {
                meals.add(parseItem(item, date, restaurant));
            }
            days.add(new RawMealDay(date, meals));
        }
        return days;
    }

    private static RawMeal parseItem(Element item, LocalDate date, Restaurant restaurant) {
        // sometimes, the title is undefined (see legacy #123)
        String text = childText(item, "title");
        if (text == null) {
            text = "";
        }

        TreeSet<String> allergenSet = new TreeSet<>(Collator.getInstance(Locale.GERMAN));
        Matcher addIn = ADD_IN.matcher(text);
        while (addIn.find()) {
            for (String allergen : addIn.group(1).split(",", -1)) {
                String trimmed = allergen.strip();
                if (!trimmed.isEmpty()) {
                    allergenSet.add(trimmed);
                }
            }
            text = text.substring(0, addIn.start()) + " " + text.substring(addIn.end());
            addIn = ADD_IN.matcher(text);
        }

        // convert 'Suppe 1' -> 'Suppe', 'Essen 3' -> 'Essen', etc.
        String category = null;
        String categoryText = childText(item, "category");
        if (categoryText != null) {
            category = categoryText.split(" ", -1)[0];
        }

        List<String> flags = new ArrayList<>();
        String pictograms = childText(item, "piktogramme");
        if (pictograms != null) {
            Matcher matcher = FLAG.matcher(pictograms);
            while (matcher.find()) {
                String[] parts = matcher.group().split(" ");
                if (parts.length > 1 && parts[1].length() > 1) {
                    flags.add(parts[1].substring(0, parts[1].length() - 1));
                }
            }
        }

        Nutrition nutrition = new Nutrition(
                FoodNormalizationService.parseXmlFloat(childText(item, "kj")),
                FoodNormalizationService.parseXmlFloat(childText(item, "kcal")),
                FoodNormalizationService.parseXmlFloat(childText(item, "fett")),
                FoodNormalizationService.parseXmlFloat(childText(item, "gesfett")),
                FoodNormalizationService.parseXmlFloat(childText(item, "kh")),
                FoodNormalizationService.parseXmlFloat(childText(item, "zucker")),
                FoodNormalizationService.parseXmlFloat(childText(item, "ballaststoffe")),
                FoodNormalizationService.parseXmlFloat(childText(item, "eiweiss")),
                FoodNormalizationService.parseXmlFloat(childText(item, "salz")));

        MealPrice prices = new MealPrice(
                FoodNormalizationService.parseGermanFloat(childText(item, "preis1")),
                FoodNormalizationService.parseGermanFloat(childText(item, "preis2")),
                FoodNormalizationService.parseGermanFloat(childText(item, "preis3")));

        String name = text.strip();
        return new RawMeal(
                FoodNormalizationService.getMealDayHash(date, name),
                name,
                null,
                category,
                prices,
                List.copyOf(allergenSet),
                flags,
                nutrition,
                null,
                null,
                restaurant,
                null,
                List.of());
    }

    private static Document readDocument(InputSource source) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(null);
            return builder.parse(source);
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("XML parser is not configured correctly", e);
        } catch (SAXException e) {
            throw new IllegalArgumentException("Invalid Mensa XML: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && name.equals(element.getTagName())) {
                result.add(element);
            }
        }
        return result;
    }

    /** Text (or CDATA) content of the first child element with the given name, {@code null} if absent or empty. */
    private static String childText(Element parent, String name) {
        List<Element> elements = children(parent, name);
        if (elements.isEmpty()) {
            return null;
        }
        String text = elements.get(0).getTextContent();
        return text == null || text.isEmpty() ? null : text;
    }
}
