package comparationTexts;

import extension.service.AIClient;
import baseAI.BaseAI;
import extension.service.FilteredElements;
import extension.service.ScrapingService;
import org.junit.jupiter.api.*;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import utils.ExcelExporter;

import java.util.*;
import java.util.stream.Stream;

@DisplayName("Kompleksowe testy porównawcze modeli")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AdaptabilityTest extends BaseAI {

    @Test
    @DisplayName("Testy adaptacyjności - różne typy treści")
    void testAdaptabilityToDifferentContentTypes() {
        Map<String, String> testCases = Map.of(
                "Kiedy przedstawiono teorię Inteligencji wielorakiej?", "https://pl.wikipedia.org/wiki/Inteligencja",
                "Znajdz cennik peguot'a 408", "https://www.peugeot.pl/lacza/cenniki.html",
                "Podaj wydarzenia w piwnicy pod baranami", "https://krakowskieforum.pl/",
                "Wskaż artykuły o sztucznej inteligencji", "https://ai.gov.pl/ai-dla-ciebie/artykuly",
                "Do kiedy krakow byl stolica polski", "https://pl.wikipedia.org/wiki/Sto%C5%82eczne_Kr%C3%B3lewskie_Miasto_Krak%C3%B3w"
        );

        System.out.println("\n=== TESTY ADAPTACYJNOŚCI ===");
        System.out.println("Różne typy treści: " + testCases.keySet());
        System.out.println();

        Map<String, AIClient> testClients = getAllClients();
        Map<String, Map<String, ExcelExporter.AdaptabilityResult>> allResults = new HashMap<>();

        for (Map.Entry<String, AIClient> entry : testClients.entrySet()) {
            String modelName = entry.getKey();
            AIClient client = entry.getValue();

            System.out.printf("MODEL: %s%n", modelName);
            Map<String, ExcelExporter.AdaptabilityResult> modelResults = new HashMap<>();

            for (Map.Entry<String, String> testCase : testCases.entrySet()) {
                String contentQuestion = testCase.getKey();
                String url = testCase.getValue();

                WebDriver modelDriver = createNewDriver();
                List<String> errors = new ArrayList<>();
                String llmResponseError = null;

                try {
                    ScrapingService scrapingService = new ScrapingService(modelDriver, client);
                    scrapingService.setUserContext(contentQuestion);
                    scrapingService.navigateTo(url);

                    FilteredElements result = scrapingService.extractAndFilterContent();

                    if (client.hasParsingError()) {
                        llmResponseError = client.getLastParsingError();
                        errors.add("LLM Response Error: " + llmResponseError);
                    }

                    RelevanceMetrics relevanceMetrics = calculateTwoStepRelevance(result, contentQuestion, modelDriver);

                    ExcelExporter.AdaptabilityResult adaptabilityResult =
                            new ExcelExporter.AdaptabilityResult(
                                    result.textElements().size(),
                                    result.linkElements().size(),
                                    relevanceMetrics.overallRelevance(),
                                    relevanceMetrics.selectorValidationScore(),
                                    relevanceMetrics.semanticRelevanceScore(),
                                    relevanceMetrics.validSelectorsCount(),
                                    relevanceMetrics.totalSelectorsCount(),
                                    String.join("; ", errors),
                                    errors.size()
                            );

                    modelResults.put(url, adaptabilityResult);

                    System.out.printf("   %s:%n", url);
                    System.out.printf("      Znalezione: %d tekstów, %d linków%n",
                            result.textElements().size(), result.linkElements().size());
                    System.out.printf("      Trafność ogólna: %.1f%%%n", relevanceMetrics.overallRelevance());
                    System.out.printf("      Poprawne selektory: %d/%d (%.1f%%)%n",
                            relevanceMetrics.validSelectorsCount(),
                            relevanceMetrics.totalSelectorsCount(),
                            relevanceMetrics.selectorValidationScore());
                    System.out.printf("      Trafność semantyczna: %.1f%%%n", relevanceMetrics.semanticRelevanceScore());

                    if (llmResponseError != null) {
                        System.out.printf("      Błąd parsowania LLM: %s%n", llmResponseError);
                    }

                    modelDriver.quit();
                } catch (Exception e) {
                    String errorMessage = "Krytyczny błąd: " + e.getMessage();
                    errors.add(errorMessage);

                    ExcelExporter.AdaptabilityResult errorResult =
                            new ExcelExporter.AdaptabilityResult(
                                    0, 0, 0.0, 0.0, 0.0, 0, 0,
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

        String fileName = ExcelExporter.exportAdaptabilityTest(allResults, "test_adaptacyjnosci");
        if (fileName != null) {
            System.out.println("Wyniki zapisane do: " + fileName);
        }
    }
    private RelevanceMetrics calculateTwoStepRelevance(FilteredElements result, String query, WebDriver driver) {
        if (result.textElements().isEmpty() && result.linkElements().isEmpty()) {
            return new RelevanceMetrics(0.0, 0.0, 0.0, 0, 0);
        }

        SelectorValidationResult selectorValidation = validateSelectors(result, driver);

        double semanticRelevance = calculateSemanticRelevance(result, query);

        double overallRelevance = (selectorValidation.validationScore() * 0.4) + (semanticRelevance * 0.6);

        return new RelevanceMetrics(
                overallRelevance,
                selectorValidation.validationScore(),
                semanticRelevance,
                selectorValidation.validSelectors(),
                selectorValidation.totalSelectors()
        );
    }

    private SelectorValidationResult validateSelectors(FilteredElements result, WebDriver driver) {
        int validSelectors = 0;
        int totalSelectors = 0;

        for (ScrapingService.WebElementData element : result.textElements()) {
            totalSelectors++;
            if (isSelectorValid(element.getSelector(), driver)) {
                validSelectors++;
            }
        }

        for (ScrapingService.WebElementData element : result.linkElements()) {
            totalSelectors++;
            if (isSelectorValid(element.getSelector(), driver)) {
                validSelectors++;
            }
        }

        double validationScore = totalSelectors > 0 ? (validSelectors * 100.0) / totalSelectors : 0.0;
        return new SelectorValidationResult(validationScore, validSelectors, totalSelectors);
    }

    private boolean isSelectorValid(String selector, WebDriver driver) {
        try {
            if (selector == null || selector.trim().isEmpty()) {
                return false;
            }

            List<WebElement> elements = driver.findElements(By.cssSelector(selector));
            boolean isValid = !elements.isEmpty() && elements.get(0).isDisplayed();

            if (isValid) {
                WebElement element = elements.get(0);
                String elementText = element.getText();
                if (elementText == null || elementText.trim().isEmpty()) {
                    return false;
                }
            }

            return isValid;

        } catch (Exception e) {
            return false;
        }
    }

    private double calculateSemanticRelevance(FilteredElements result, String query) {
        if (result.textElements().isEmpty() && result.linkElements().isEmpty()) {
            return 0.0;
        }

        long relevantElements = Stream.concat(
                        result.textElements().stream(),
                        result.linkElements().stream()
                )
                .filter(element -> {
                    String elementText = element.getText();
                    return isTextSemanticallyRelevant(elementText, query) &&
                            isTextContextuallyAppropriate(elementText);
                })
                .count();

        long totalElements = result.textElements().size() + result.linkElements().size();


        return totalElements > 0 ? (relevantElements * 100.0) / totalElements : 0.0;
    }

    private boolean isTextSemanticallyRelevant(String elementText, String query) {
        if (elementText == null || elementText.trim().isEmpty()) {
            return false;
        }

        String textLower = elementText.toLowerCase();
        String queryLower = query.toLowerCase();

        boolean hasKeywords = Arrays.stream(queryLower.split("\\s+"))
                .filter(word -> word.length() > 3)
                .anyMatch(textLower::contains);

        if (!hasKeywords) {
            return false;
        }

        return !isTextTooGeneric(elementText) && hasMeaningfulContent(elementText);
    }

    private boolean isTextContextuallyAppropriate(String text) {
        if (text == null || text.trim().length() < 10) {
            return false;
        }

        String[] genericPatterns = {
                "copyright", "all rights reserved", "privacy policy", "terms of service",
                "cookie policy", "login", "sign up", "subscribe", "navigation", "menu"
        };

        String textLower = text.toLowerCase();
        return Arrays.stream(genericPatterns)
                .noneMatch(textLower::contains);
    }

    private boolean isTextTooGeneric(String text) {
        if (text == null || text.trim().length() < 15) {
            return true;
        }

        String[] genericIndicators = {
                "click here", "read more", "learn more", "download now", "get started"
        };

        String textLower = text.toLowerCase();
        return Arrays.stream(genericIndicators)
                .anyMatch(textLower::contains) || textLower.split("\\s+").length < 4;
    }

    private boolean hasMeaningfulContent(String text) {
        if (text == null || text.trim().isEmpty()) {
            return false;
        }

        String cleanText = text.trim();
        return cleanText.length() >= 20 &&
                !cleanText.matches(".*[0-9]{10,}.*") &&
                !cleanText.matches("^[^a-zA-Z]*$");
    }

    private record SelectorValidationResult(double validationScore, int validSelectors, int totalSelectors) {
    }

    private record RelevanceMetrics(
            double overallRelevance,
            double selectorValidationScore,
            double semanticRelevanceScore,
            int validSelectorsCount,
            int totalSelectorsCount
    ) {
    }
}