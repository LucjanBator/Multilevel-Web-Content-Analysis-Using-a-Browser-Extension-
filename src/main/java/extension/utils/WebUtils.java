package extension.utils;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class WebUtils {
    private static final Logger logger = LogManager.getLogger(WebUtils.class);

    public static void highlightElement(WebDriver driver, WebElement element) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("arguments[0].setAttribute('style', 'background: yellow; border: 2px solid red;');", element);
        } catch (Exception e) {
            logger.error("Error highlighting element", e);
        }
    }

    public static void scrollToElement(WebDriver driver, WebElement element) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("arguments[0].scrollIntoView(true);", element);
        } catch (Exception e) {
            logger.error("Error scrolling to element", e);
        }
    }

    public static void waitForPageLoad(WebDriver driver, int timeoutInSeconds) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            for (int i = 0; i < timeoutInSeconds * 2; i++) {
                if ("complete".equals(js.executeScript("return document.readyState"))) {
                    return;
                }
                Thread.sleep(500);
            }
        } catch (InterruptedException e) {
            logger.error("Error waiting for page load", e);
            Thread.currentThread().interrupt();
        }
    }
}