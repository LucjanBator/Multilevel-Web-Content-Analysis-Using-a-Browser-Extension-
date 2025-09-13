package extension.core;

import extension.services.ScrapingService;
import extension.utils.ContentSaver;
import org.openqa.selenium.WebDriver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.List;

public class ExtensionRunner {
    private static final Logger logger = LogManager.getLogger(ExtensionRunner.class);
    private WebDriver driver;
    private static final String OUTPUT_FILE = "page_content.json";
    private static final String SNAPSHOTS_DIR = "snapshots";

    public void start() {
        try {
            driver = WebDriverFactory.createDriver();
            ScrapingService scrapingService = new ScrapingService(driver);

            // Example usage
            scrapingService.navigateTo("https://pl.wikipedia.org/wiki/Sto%C5%82eczne_Kr%C3%B3lewskie_Miasto_Krak%C3%B3w");

            // Capture and save HTML snapshot
            String snapshot = scrapingService.capturePageSnapshot();
            String snapshotPath = ContentSaver.saveSnapshot(snapshot, SNAPSHOTS_DIR);
            logger.info("Page snapshot saved to: {}", snapshotPath);

            // Extract content with retry mechanism
            List<ScrapingService.WebElementData> allElements =
                    scrapingService.extractPageContentWithRetry(3, 1000);

            // Save to JSON
            ContentSaver.saveToJson(allElements, OUTPUT_FILE);
            logger.info("Content saved to {}", OUTPUT_FILE);


        } catch (Exception e) {
            logger.error("Error in extension runner", e);
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }
}