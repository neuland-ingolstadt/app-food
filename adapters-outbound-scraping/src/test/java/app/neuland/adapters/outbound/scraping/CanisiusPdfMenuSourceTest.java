package app.neuland.adapters.outbound.scraping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.neuland.application.FoodNormalizationService;
import app.neuland.model.food.MealCategory;
import app.neuland.model.food.MealDay;
import app.neuland.model.shared.Language;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class CanisiusPdfMenuSourceTest {

    private final HttpFetcher http = new HttpFetcher();
    private final FoodNormalizationService normalization = new FoodNormalizationService();
    private final DeepLTranslationAdapter translation =
            new DeepLTranslationAdapter(http, true, Optional.empty(), Optional.empty());

    private CanisiusPdfMenuSource source(TestHttpServer server) {
        return new CanisiusPdfMenuSource(http, normalization, translation, server.url("/speiseplan.pdf"));
    }

    private static byte[] pdf(String... lines) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                content.setLeading(14);
                content.newLineAtOffset(30, 750);
                for (String line : lines) {
                    content.showText(line);
                    content.newLine();
                }
                content.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void downloadsPdfAndExtractsMeals() throws IOException {
        byte[] pdf = pdf(
                "Montag 23.11.2026", "Gulaschsuppe", "\u20ac 4.50 \u20ac 5.50 \u20ac 6.50",
                "Dienstag 24.11.2026", "Pizza", "\u20ac 4.20 \u20ac 5.20 \u20ac 6.20",
                "Mittwoch 25.11.2026", "Pasta", "\u20ac 3.00 \u20ac 4.00 \u20ac 5.00",
                "Donnerstag 26.11.2026", "Nudeln", "\u20ac 3.00 \u20ac 4.00 \u20ac 5.00",
                "Freitag 27.11.2026", "Fisch", "\u20ac 4.00 \u20ac 5.00 \u20ac 6.00",
                "Salate der Saison vom B\u00fcfett", "Gemischter Salat", "\u20ac 1.00 \u20ac 2.00 \u20ac 3.00");
        try (TestHttpServer server = new TestHttpServer((request, reply) -> reply.body(pdf))) {
            List<MealDay> days = source(server).fetch();

            assertEquals(5, days.size());
            assertEquals("Gulaschsuppe", days.get(0).meals().get(0).name().get(Language.DE));
            assertEquals(MealCategory.MAIN, days.get(0).meals().get(0).category());
            assertEquals(5.5, days.get(0).meals().get(0).price().student());
            assertEquals("Gemischter Salat", days.get(4).meals().get(1).name().get(Language.DE));
            assertEquals(MealCategory.SALAD, days.get(4).meals().get(1).category());
        }
    }

    @Test
    void notFoundYieldsNoMeals() {
        try (TestHttpServer server = new TestHttpServer((request, reply) -> reply.status(404).body("nope"))) {
            assertTrue(source(server).fetch().isEmpty());
        }
    }

    @Test
    void emptyBodyYieldsNoMeals() {
        try (TestHttpServer server = new TestHttpServer((request, reply) -> reply.status(200))) {
            assertTrue(source(server).fetch().isEmpty());
        }
    }

    @Test
    void emptyPdfYieldsNoMeals() throws IOException {
        byte[] pdf = pdf("");
        try (TestHttpServer server = new TestHttpServer((request, reply) -> reply.body(pdf))) {
            assertTrue(source(server).fetch().isEmpty());
        }
    }

    @Test
    void serverErrorsAreFailures() {
        try (TestHttpServer server = new TestHttpServer((request, reply) -> reply.status(500).body("x"))) {
            assertThrows(IllegalStateException.class, () -> source(server).fetch());
        }
    }

    @Test
    void processesPlansToDomainModel() {
        try (TestHttpServer server = new TestHttpServer((request, reply) -> reply.status(404))) {
            List<MealDay> days = source(server).process(CanisiusTextParser.parse(
                    "Mo 23.11.2026 gulasch € 4.50 € 5.50 € 6.50 Di 24.11.2026 a € 1.0 € 2.0 € 3.0 "
                            + "Mi 25.11.2026 b € 1.0 € 2.0 € 3.0 Do 26.11.2026 c € 1.0 € 2.0 € 3.0 "
                            + "Fr 27.11.2026 d € 1.0 € 2.0 € 3.0 Salate der Saison vom Büfett Beilagensalat € 1.0 € 2.0 € 3.0"));

            assertEquals(5, days.size());
            assertEquals("Gulasch", days.get(0).meals().get(0).name().get(Language.DE));
            assertEquals(MealCategory.MAIN, days.get(0).meals().get(0).category());
            assertEquals(MealCategory.SALAD, days.get(0).meals().get(1).category());
        }
    }
}
