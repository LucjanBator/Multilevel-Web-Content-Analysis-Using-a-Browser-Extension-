package extension.core;

import extension.exceptions.WebDriverInitializationException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

public class WebDriverFactory {
    private static final Logger logger = LogManager.getLogger(WebDriverFactory.class);

    private static final String CHROME_DRIVER_PATH = "webdriver.chrome.driver.path";
    private static final String IMPLICIT_WAIT_SECONDS = "webdriver.implicit.wait.seconds";
    private static final String HEADLESS_MODE = "webdriver.headless.mode";

    public static WebDriver createDriver() {
        ChromeDriver driver = null;
        try {
            configureChromeDriver();
            ChromeOptions options = createChromeOptions();

            logger.info("Creating ChromeDriver with options...");
            driver = new ChromeDriver(options);
            configureDriverTimeouts(driver);

            logger.info("ChromeDriver successfully created");
            return driver;

        } catch (Exception e) {
            logger.error("Error creating WebDriver", e);
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception quitException) {
                    logger.warn("Error quitting driver after failure", quitException);
                }
            }
            throw new WebDriverInitializationException("Failed to create WebDriver instance", e);
        }
    }

    private static void configureChromeDriver() {
        String driverPath = getChromeDriverPath();

        if (!Files.exists(Path.of(driverPath))) {
            throw new WebDriverInitializationException(
                    "ChromeDriver not found at: " + driverPath +
                            ". Please check 'webdriver.chrome.driver.path' in config.properties"
            );
        }

        System.setProperty("webdriver.chrome.driver", driverPath);
        logger.info("Using ChromeDriver at: {}", driverPath);
    }

    private static ChromeOptions createChromeOptions() {
        ChromeOptions options = new ChromeOptions();

        options.addArguments("--remote-allow-origins=*");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");

        options.addArguments("--window-size=1920,1080");
        options.addArguments("--disable-gpu");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--start-maximized");
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-plugins");

        options.addArguments("--log-level=3");
        options.addArguments("--silent");

        if (isHeadlessModeEnabled()) {
            options.addArguments("--headless=new");
            logger.info("Headless mode enabled");
        }

        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        options.setExperimentalOption("useAutomationExtension", false);

        logger.debug("ChromeOptions configured - using system Chrome");
        return options;
    }

    private static void configureDriverTimeouts(WebDriver driver) {
        int waitSeconds = getImplicitWaitSeconds();
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(waitSeconds));
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(30));

        logger.debug("Driver timeouts configured - implicit wait: {} seconds", waitSeconds);
    }

    private static String getChromeDriverPath() {
        String configuredPath = extension.core.config.AppConfig.getRequiredProperty(CHROME_DRIVER_PATH);
        if (!configuredPath.trim().isEmpty()) {
            return configuredPath.trim();
        }

        String defaultPath = Paths.get("").toAbsolutePath() + "/src/main/drivers/chromedriver.exe";
        logger.warn("Chrome driver path not configured, using default: {}", defaultPath);
        return defaultPath;
    }

    private static int getImplicitWaitSeconds() {
        try {
            return Integer.parseInt(extension.core.config.AppConfig.getProperty(IMPLICIT_WAIT_SECONDS, "10"));
        } catch (NumberFormatException e) {
            logger.warn("Invalid implicit wait configuration, using default: 10 seconds");
            return 10;
        }
    }

    private static boolean isHeadlessModeEnabled() {
        return Boolean.parseBoolean(extension.core.config.AppConfig.getProperty(HEADLESS_MODE, "false"));
    }

}