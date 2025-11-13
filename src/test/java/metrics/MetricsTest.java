package metrics;

import baseAI.BaseAI;
import extension.service.AIClient;
import extension.service.FilteredElements;
import extension.service.ScrapingService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.*;
import org.openqa.selenium.WebDriver;
import utils.ExcelExporter;

import java.util.*;

/**
 * Testy walidacji dokładności dla różnych modeli
 */
@DisplayName("Testy dokładności i weryfikacji odpowiedzi")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MetricsTest extends BaseAI {

    private static final Logger logger = LogManager.getLogger(MetricsTest.class);

    @Test
    @DisplayName("Testy jakości ekstrakcji - metryki Precyzja, Czułość, F1-score")
    void testExtractionQualityMetrics() {
        // Przypadki testowe oparte na metodologii SQuAD
        Map<String, MetricsTest.ExtractionTestCase> testCases = Map.of(
                "https://pl.wikipedia.org/wiki/Smartfon",
                new MetricsTest.ExtractionTestCase(
                        "Jakie funkcje zawierają smartfony?",
                        List.of(
                                "Z biegiem lat, smartfony oprócz funkcji telefonu, zaczęły łączyć funkcje poczty elektronicznej, przeglądarki sieciowej, pagera, GPS, jak również cyfrowego aparatu fotograficznego, dyktafonu i kamery wideo, zarządzania informacjami osobistymi (ang. Personal Information Management) oraz obsługę dokumentów biurowych w formatach OOXML (tworzone np. przez Microsoft Office), ODF (np. LibreOffice) i PDF[potrzebny przypis]."
                        ),
                        Arrays.asList(
                                "zarządzania informacjami osobistymi",
                                "przeglądarki sieciowej",
                                "kamery wideo",
                                "cyfrowego aparatu fotograficznego",
                                "Systemy operacyjne",
                                "pagera",
                                "dyktafonu"
                        )
                ),
                "https://www.peugeot.pl/lacza/cenniki.html",
                new MetricsTest.ExtractionTestCase(
                        "Znajdz cennik peguot'a 408",
                        List.of(
                                "CENNIK E-408 I 408 (2025)",
                                "408\n" +
                                        "CENNIK E-408 I 408 (2025)"

                        ),
                        List.of(
                                "CENNIK E-408 I 408 (2025)"
                        )
                ),
                "https://pl.wikipedia.org/wiki/Bug",
                new MetricsTest.ExtractionTestCase(
                        "Wymień lewe dopływy rzeki Bug",
                        List.of(),
                        Arrays.asList(
                                "Pełtew",
                                "Udal",
                                "Bukowa",
                                "Huczwa",
                                "Wełnianka",
                                "Sołokija",
                                "Uherka",
                                "Włodawka",
                                "Krzna",
                                "Toczna",
                                "Cetynia",
                                "Liwiec",
                                "Grabar"
                        )
                )
        );

        System.out.println("\n=== TESTY JAKOŚCI EKSTRAKCJI - METRYKI SQuAD ===");

        Map<String, AIClient> testClients = getAllClients();
        Map<String, Map<String, ExcelExporter.QualityMetricsResult>> allResults = new HashMap<>();

        for (Map.Entry<String, AIClient> entry : testClients.entrySet()) {
            String modelName = entry.getKey();
            AIClient client = entry.getValue();

            System.out.printf("MODEL: %s%n", modelName);
            Map<String, ExcelExporter.QualityMetricsResult> modelResults = new HashMap<>();

            for (Map.Entry<String, MetricsTest.ExtractionTestCase> testCase : testCases.entrySet()) {
                String url = testCase.getKey();
                MetricsTest.ExtractionTestCase testData = testCase.getValue();

                WebDriver modelDriver = createNewDriver();
                List<String> errors = new ArrayList<>();

                try {
                    ScrapingService scrapingService = new ScrapingService(modelDriver, client);
                    scrapingService.setUserContext(testData.query());
                    scrapingService.navigateTo(url);

                    FilteredElements result = scrapingService.extractAndFilterContent();

                    MetricsTest.QualityMetrics metrics = calculateQualityMetrics(
                            result, testData.expectedTexts(), testData.expectedLinks()
                    );

                    if (client.hasParsingError()) {
                        errors.add("LLM Parsing Error: " + client.getLastParsingError());
                    }

                    ExcelExporter.QualityMetricsResult qualityResult =
                            new ExcelExporter.QualityMetricsResult(
                                    metrics.precision(),
                                    metrics.recall(),
                                    metrics.f1Score(),
                                    metrics.truePositiveTexts(),
                                    metrics.truePositiveLinks(),
                                    testData.expectedTexts().size(),
                                    testData.expectedLinks().size(),
                                    String.join("; ", errors),
                                    errors.size()
                            );

                    modelResults.put(url, qualityResult);

                    System.out.printf("   %s:%n", url);
                    System.out.printf("      Precyzja: %.3f%n", metrics.precision());
                    System.out.printf("      Czułość: %.3f%n", metrics.recall());
                    System.out.printf("      F1-Score: %.3f%n", metrics.f1Score());
                    System.out.printf("      TP: %d/%d tekstów, %d/%d linków%n",
                            metrics.truePositiveTexts(), testData.expectedTexts().size(),
                            metrics.truePositiveLinks(), testData.expectedLinks().size());

                    modelDriver.quit();
                } catch (Exception e) {
                    String errorMessage = "Błąd: " + e.getMessage();
                    errors.add(errorMessage);

                    ExcelExporter.QualityMetricsResult errorResult =
                            new ExcelExporter.QualityMetricsResult(
                                    0.0, 0.0, 0.0, 0, 0,
                                    testData.expectedTexts().size(), testData.expectedLinks().size(),
                                    String.join("; ", errors),
                                    errors.size()
                            );

                    modelResults.put(url, errorResult);
                    System.out.printf("   %s: Błąd - %s%n", url, errorMessage);
                    modelDriver.quit();
                }
            }
            allResults.put(modelName, modelResults);
            System.out.println("   ------------------------------------");
        }

        String fileName = ExcelExporter.exportQualityMetricsTest(allResults, "test_SQuAD_metrics");
        if (fileName != null) {
            System.out.println("Wyniki zapisane do: " + fileName);
        }
    }

    @Test
    @DisplayName("Testy Named Entity Recognition - identyfikacja encji")
    void testNamedEntityRecognition() {
        // Przypadki testowe dla NER oparte na metodologii Relation Extraction
        Map<String, MetricsTest.NERTestCase> nerTestCases = Map.of(
                "https://pl.wikipedia.org/wiki/Sto%C5%82eczne_Kr%C3%B3lewskie_Miasto_Krak%C3%B3w",
                new MetricsTest.NERTestCase(
                        "Wymień tytuły miasta Krakowa jakie miało na przestrzeni lat",
                        Arrays.asList("do 25 maja 1609: stolica Królestwa Polskiego",
                                "od 1621: Cracovia metropolis regni Poloniae",
                                "od 13 marca 1792: Królewskie, wolne, stołeczne i wydziałowe miasto Kraków",
                                "od 23 listopada 1793: Miasto Główne Rzeczypospolitej, wolne i wydziałowe Kraków",
                                "od 24 kwietnia 1794: Magistrat ogólny miasta stołecznego i wydziałowego Krakowa",
                                "od 15 czerwca 1794 (ponownie): Miasto Główne Rzeczypospolitej, wolne i wydziałowe Kraków",
                                "od 1794: Miasto Główne i Stołeczne Kraków",
                                "od 1796: K.K. Provinzial-Hauptstadt Krakau",
                                "die Königliche Hauptstadt Krakau",
                                "Wolnohandlowe miasto Kraków",
                                "Wolne, Niepodległe i Ściśle Neutralne Miasto Kraków i jego Okręg, inaczej Rzeczpospolita Krakowska",
                                "Administracja Tymczasowa Cywilna Wolnego Miasta i jego Okręgu",
                                "C.K. Główne Miasto Krajowe Kraków",
                                "od 1858: Królewskie Główne Miasto Kraków",
                                "od 1866: Stołeczne Królewskie Miasto Kraków",
                                "w czasie II wojny światowej pod okupacją niemiecką: Die Hauptstadt des Generalgouvernements Krakau",
                                "od 1950: Miasto Kraków"),
                        Arrays.asList("data", "lokalizacja")
                ),
                "https://pl.wikipedia.org/wiki/Iloraz_inteligencji",
                new MetricsTest.NERTestCase(
                        "Podaj prekursorów koncepcji ilorazu inteligencji",
                        Arrays.asList("Alfred Binet", "Théodorem Simonem", "Bineta-Simona",
                                "William Stern", "Lewis Terman", "David Wechsler"),
                        Arrays.asList("osoba", "naukowiec")
                ),
                "https://pl.wikipedia.org/wiki/Wis%C5%82ok",
                new MetricsTest.NERTestCase("Podaj dopływy rzeki Wisłok",
                        Arrays.asList("Moszczaniec (potok)",
                                "Wisłoczek (potok)",
                                "Pielnia",
                                "Rudzinka",
                                "Tabor (rzeka)",
                                "Lublica (potok)",
                                "Lubatówka (struga)",
                                "Lubcza",
                                "Olszyny (struga)",
                                "Pstrągówka (potok)",
                                "Stobnica",
                                "Strug (rzeka)",
                                "Mikośka",
                                "Mrowla (rzeka)",
                                "Sawa (dopływ Wisłoka)",
                                "Mleczka",
                                "Leszczynka (struga)",
                                "Przyrwa",
                                "Kopytko (potok)"),
                        Arrays.asList("lokalizacja", "rzeki")
                )
        );

        System.out.println("\n=== TESTY NAMED ENTITY RECOGNITION ===");

        Map<String, AIClient> testClients = getAllClients();
        Map<String, Map<String, ExcelExporter.NERMetricsResult>> allResults = new HashMap<>();

        for (Map.Entry<String, AIClient> entry : testClients.entrySet()) {
            String modelName = entry.getKey();
            AIClient client = entry.getValue();

            System.out.printf("MODEL: %s%n", modelName);
            Map<String, ExcelExporter.NERMetricsResult> modelResults = new HashMap<>();

            for (Map.Entry<String, MetricsTest.NERTestCase> testCase : nerTestCases.entrySet()) {
                String url = testCase.getKey();
                MetricsTest.NERTestCase testData = testCase.getValue();

                WebDriver modelDriver = createNewDriver();
                List<String> errors = new ArrayList<>();

                try {
                    ScrapingService scrapingService = new ScrapingService(modelDriver, client);
                    scrapingService.setUserContext(testData.query());
                    scrapingService.navigateTo(url);

                    FilteredElements result = scrapingService.extractAndFilterContent();

                    MetricsTest.NERMetrics metrics = calculateNERMetrics(result, testData.expectedEntities());

                    if (client.hasParsingError()) {
                        errors.add("LLM Parsing Error: " + client.getLastParsingError());
                    }

                    ExcelExporter.NERMetricsResult nerResult =
                            new ExcelExporter.NERMetricsResult(
                                    metrics.entityPrecision(),
                                    metrics.entityRecall(),
                                    metrics.entityF1Score(),
                                    metrics.truePositiveEntities(),
                                    testData.expectedEntities().size(),
                                    String.join(", ", testData.entityTypes()),
                                    String.join("; ", errors),
                                    errors.size()
                            );

                    modelResults.put(url, nerResult);

                    System.out.printf("   %s:%n", url);
                    System.out.printf("      Precyzja encji: %.3f%n", metrics.entityPrecision());
                    System.out.printf("      Czułość encji: %.3f%n", metrics.entityRecall());
                    System.out.printf("      F1-Score encji: %.3f%n", metrics.entityF1Score());
                    System.out.printf("      Znalezione encje: %d/%d%n",
                            metrics.truePositiveEntities(), testData.expectedEntities().size());

                    modelDriver.quit();
                } catch (Exception e) {
                    String errorMessage = "Błąd: " + e.getMessage();
                    errors.add(errorMessage);

                    ExcelExporter.NERMetricsResult errorResult =
                            new ExcelExporter.NERMetricsResult(
                                    0.0, 0.0, 0.0, 0, testData.expectedEntities().size(),
                                    String.join(", ", testData.entityTypes()),
                                    String.join("; ", errors),
                                    errors.size()
                            );

                    modelResults.put(url, errorResult);
                    System.out.printf("   %s: Błąd - %s%n", url, errorMessage);
                    modelDriver.quit();
                }
            }
            allResults.put(modelName, modelResults);
            System.out.println("   ------------------------------------");
        }

        String fileName = ExcelExporter.exportNERMetricsTest(allResults, "test_ner_metrics");
        if (fileName != null) {
            System.out.println("Wyniki zapisane do: " + fileName);
        }
    }

    private QualityMetrics calculateQualityMetrics(FilteredElements result,
                                                   List<String> expectedTexts,
                                                   List<String> expectedLinks) {

        int truePositiveTexts = 0;
        int truePositiveLinks = 0;
        int falsePositiveTexts = 0;
        int falsePositiveLinks = 0;
        int falseNegativeTexts;
        int falseNegativeLinks;

        List<String> foundExpectedTexts = new ArrayList<>();
        List<String> foundExpectedLinks = new ArrayList<>();

        for (ScrapingService.WebElementData element : result.textElements()) {
            String elementText = element.getText().trim();
            boolean foundMatch = false;

            for (String expected : expectedTexts) {
                if (elementText.toLowerCase().contains(expected.toLowerCase())) {
                    truePositiveTexts++;
                    foundExpectedTexts.add(expected);
                    foundMatch = true;
                    break;
                }
            }

            if (!foundMatch) {
                falsePositiveTexts++;
            }
        }

        for (ScrapingService.WebElementData element : result.linkElements()) {
            String elementText = element.getText().trim();
            boolean foundMatch = false;

            for (String expected : expectedLinks) {
                if (elementText.toLowerCase().contains(expected.toLowerCase())) {
                    truePositiveLinks++;
                    foundExpectedLinks.add(expected);
                    foundMatch = true;
                    break;
                }
            }

            if (!foundMatch) {
                falsePositiveLinks++;
            }
        }

        falseNegativeTexts = expectedTexts.size() - foundExpectedTexts.size();
        falseNegativeLinks = expectedLinks.size() - foundExpectedLinks.size();

        logger.info("Found text matches: {} out of {}: {}",
                foundExpectedTexts.size(), expectedTexts.size(), foundExpectedTexts);
        logger.info("Found link matches: {} out of {}: {}",
                foundExpectedLinks.size(), expectedLinks.size(), foundExpectedLinks);

        int totalTruePositives = truePositiveTexts + truePositiveLinks;
        int totalFalsePositives = falsePositiveTexts + falsePositiveLinks;
        int totalFalseNegatives = falseNegativeTexts + falseNegativeLinks;
        int totalExpected = expectedTexts.size() + expectedLinks.size();

        double precision = totalTruePositives + totalFalsePositives > 0 ?
                totalTruePositives / (double) (totalTruePositives + totalFalsePositives) : 0.0;
        double recall = totalExpected > 0 ? totalTruePositives / (double) totalExpected : 0.0;
        double f1Score = (precision + recall) > 0 ?
                2 * (precision * recall) / (precision + recall) : 0.0;

        logger.info("Final metrics - TP: {}, FP: {}, FN: {}, Precision: {:.3f}, Recall: {:.3f}, F1: {:.3f}",
                totalTruePositives, totalFalsePositives, totalFalseNegatives, precision, recall, f1Score);

        return new QualityMetrics(precision, recall, f1Score, truePositiveTexts, truePositiveLinks);
    }

    public record QualityMetrics(double precision, double recall, double f1Score,
                                 int truePositiveTexts, int truePositiveLinks) {
    }

    private MetricsTest.NERMetrics calculateNERMetrics(FilteredElements result, List<String> expectedEntities) {
        int truePositiveEntities = 0;
        int falsePositiveEntities = 0;
        List<String> foundEntities = new ArrayList<>();
        List<String> elementsWithMultipleEntities = new ArrayList<>();

        List<ScrapingService.WebElementData> allElements = new ArrayList<>();
        allElements.addAll(result.textElements());
        allElements.addAll(result.linkElements());

        System.out.printf("      DEBUG: Analiza %d elementów (teksty: %d, linki: %d)%n",
                allElements.size(), result.textElements().size(), result.linkElements().size());

        for (ScrapingService.WebElementData element : allElements) {
            String elementText = element.getText();
            if (elementText == null || elementText.trim().isEmpty()) {
                continue;
            }

            String elementTextLower = elementText.toLowerCase().trim();
            List<String> entitiesFoundInElement = new ArrayList<>();

            for (String expectedEntity : expectedEntities) {
                String expectedLower = expectedEntity.toLowerCase().trim();

                if (isEntityMatch(elementTextLower, expectedLower)) {
                    if (!foundEntities.contains(expectedEntity)) {
                        foundEntities.add(expectedEntity);
                    }
                    entitiesFoundInElement.add(expectedEntity);
                    truePositiveEntities++;
                }
            }

            if (entitiesFoundInElement.size() > 1) {
                elementsWithMultipleEntities.add(String.format("'%s' zawiera: %s",
                        elementText.length() > 30 ? elementText.substring(0, 27) + "..." : elementText,
                        entitiesFoundInElement));
            }

            if (entitiesFoundInElement.isEmpty()) {
                falsePositiveEntities++;
            }
        }

        truePositiveEntities = foundEntities.size();
        int falseNegativeEntities = expectedEntities.size() - truePositiveEntities;

        if (!elementsWithMultipleEntities.isEmpty()) {
            System.out.printf("      DEBUG: Elementy z wieloma encjami (%d):%n", elementsWithMultipleEntities.size());
            for (String multiEntityElement : elementsWithMultipleEntities) {
                System.out.printf("         - %s%n", multiEntityElement);
            }
        }

        System.out.printf("      DEBUG: Podsumowanie - TP=%d, FP=%d, FN=%d, Expected=%d%n",
                truePositiveEntities, falsePositiveEntities, falseNegativeEntities, expectedEntities.size());
        System.out.printf("      DEBUG: Znalezione encje: %s%n", foundEntities);

        double entityPrecision = (truePositiveEntities + falsePositiveEntities) > 0 ?
                truePositiveEntities / (double) (truePositiveEntities + falsePositiveEntities) : 0.0;
        double entityRecall = !expectedEntities.isEmpty() ?
                truePositiveEntities / (double) expectedEntities.size() : 0.0;
        double entityF1Score = (entityPrecision + entityRecall) > 0 ?
                2 * (entityPrecision * entityRecall) / (entityPrecision + entityRecall) : 0.0;

        return new MetricsTest.NERMetrics(entityPrecision, entityRecall, entityF1Score, truePositiveEntities);
    }

    private boolean isEntityMatch(String elementText, String expectedEntity) {
        if (elementText.equals(expectedEntity)) {
            return true;
        }

        if (elementText.contains(expectedEntity)) {
            return true;
        }

        if (expectedEntity.length() > 10 && elementText.contains(expectedEntity)) {
            return true;
        }

        String[] expectedWords = expectedEntity.split("\\s+");
        if (expectedWords.length > 2) {
            List<String> keyPhrases = extractKeyPhrases(expectedEntity);
            for (String phrase : keyPhrases) {
                if (elementText.contains(phrase.toLowerCase())) {
                    return true;
                }
            }

            long matchingKeywords = Arrays.stream(expectedWords)
                    .filter(word -> word.length() > 3 &&
                            !isCommonWord(word) &&
                            elementText.contains(word.toLowerCase()))
                    .count();
            return matchingKeywords >= Math.max(2, expectedWords.length * 0.5);
        }

        return false;
    }

    private List<String> extractKeyPhrases(String entity) {
        List<String> phrases = new ArrayList<>();
        String entityLower = entity.toLowerCase();

        if (entityLower.contains(":")) {
            String[] parts = entityLower.split(":");
            if (parts.length > 1) {
                phrases.add(parts[1].trim());
            }
        }

        if (entityLower.contains("\"")) {
            String[] quotedParts = entityLower.split("\"");
            for (String part : quotedParts) {
                if (part.length() > 5) {
                    phrases.add(part.trim());
                }
            }
        }

        if (entityLower.contains("(") && entityLower.contains(")")) {
            int start = entityLower.indexOf("(");
            int end = entityLower.indexOf(")");
            if (start < end) {
                phrases.add(entityLower.substring(start + 1, end).trim());
            }
        }

        if (entityLower.length() < 50) {
            phrases.add(entityLower);
        }

        return phrases;
    }

    private boolean isCommonWord(String word) {
        Set<String> commonWords = Set.of(
                "do", "od", "w", "na", "i", "oraz", "czyli", "inaczej",
                "the", "of", "and", "in", "on", "at", "to", "for"
        );
        return commonWords.contains(word.toLowerCase());
    }

    private record ExtractionTestCase(String query, List<String> expectedTexts, List<String> expectedLinks) {
    }

    private record NERTestCase(String query, List<String> expectedEntities, List<String> entityTypes) {
    }

    private record NERMetrics(double entityPrecision, double entityRecall, double entityF1Score,
                              int truePositiveEntities) {
    }
}