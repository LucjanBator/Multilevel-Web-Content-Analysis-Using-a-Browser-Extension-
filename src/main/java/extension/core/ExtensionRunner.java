package extension.core;

import extension.services.ScrapingService;
import extension.utils.ContentSaver;
import org.openqa.selenium.WebDriver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

public class ExtensionRunner {
    private static final Logger logger = LogManager.getLogger(ExtensionRunner.class);
    private WebDriver driver;
    private static final String OUTPUT_FILE = "page_content.json";

    public void start() {
        try {
            driver = WebDriverFactory.createDriver();
            ScrapingService scrapingService = new ScrapingService(driver);

            // Example usage
            scrapingService.navigateTo("https://pl.wikipedia.org/wiki/Wikipedia:Strona_główna");
//            String pageTitle = scrapingService.getPageTitle();
//            logger.info("Page title: " + pageTitle);
            // 1. Pobierz całą zawartość strony
            Map<String, ScrapingService.WebElementData> pageContent = scrapingService.extractPageContent();

            // 2. Zapisz dane do pliku JSON
            ContentSaver.saveToJson(pageContent, OUTPUT_FILE);
            logger.info("Content saved to {}", OUTPUT_FILE);

//            // 3. Przykład wczytania i użycia zapisanych danych
//            Map<String, WebElementData> loadedContent = ContentSaver.loadFromJson(OUTPUT_FILE);
//            logger.info("Loaded {} elements from file", loadedContent.size());
//
//            // 4. Przykład użycia - nawigacja do pierwszego linku
//            loadedContent.values().stream()
//                    .filter(e -> e.getType() == WebElementData.ElementType.LINK)
//                    .findFirst()
//                    .ifPresent(element -> {
//                        logger.info("Navigating to first link: {}", element.getText());
//                        scrapingService.navigateToElement(element.getSelector());
//                    });

        } catch (Exception e) {
            logger.error("Error in extension runner", e);
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }

    private void saveContent(Map<String, ScrapingService.WebElementData> content) {
        // Implementacja zapisu do pliku/DB
        content.forEach((selector, data) -> {
            logger.info("Saved element - Selector: {}, Text: {}, Type: {}",
                    selector, data.getText(), data.getType());
        });
    }
}