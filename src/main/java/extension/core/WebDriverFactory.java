package extension.core;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Paths;
import java.time.Duration;

public class WebDriverFactory {
    private static final Logger logger = LogManager.getLogger(WebDriverFactory.class);

    public static WebDriver createDriver() {
        try {
            String driverPath = getChromeDriverPath();
            System.setProperty("webdriver.chrome.driver", driverPath);
            logger.info("Using ChromeDriver at: " + driverPath);

            ChromeOptions options = new ChromeOptions();
            options.addArguments("--remote-allow-origins=*");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--no-sandbox");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--disable-gpu");
//            options.addArguments("--headless"); // Run in headless mode
            options.addArguments("--disable-blink-features=AutomationControlled");
            options.addArguments("--start-maximized");
            options.setImplicitWaitTimeout(Duration.ofSeconds(10));

            // Dla Windows - jeśli Chrome nie jest w domyślnej lokalizacji
            options.setBinary("C:\\Users\\lucja\\Downloads\\chrome-win64\\chrome-win64\\chrome.exe");
            logger.info("Creating ChromeDriver with options: " + options);
            return new ChromeDriver(options);
        } catch (Exception e) {
            logger.error("Error creating WebDriver", e);
            throw new RuntimeException("Failed to create WebDriver", e);
        }
    }

    private static String getChromeDriverPath() {
        // Adjust path according to your system
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return Paths.get("").toAbsolutePath() +
                    "/src/main/drivers/chromedriver.exe";
        } else if (os.contains("mac")) {
            return "drivers/chromedriver_mac";
        } else {
            return "drivers/chromedriver_linux";
        }
    }
}