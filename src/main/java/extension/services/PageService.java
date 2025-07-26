package extension.services;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;
import java.time.Duration;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PageService {
    private static final Logger logger = LogManager.getLogger(PageService.class);
    private final WebDriver driver;
    private final WebDriverWait wait;

    public PageService(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(10));
    }

    public boolean isElementPresent(By locator) {
        try {
            wait.until(ExpectedConditions.presenceOfElementLocated(locator));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isElementVisible(By locator) {
        try {
            wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public List<WebElement> findAllElements(By locator) {
        return wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(locator));
    }

    public void waitForElementToDisappear(By locator) {
        wait.until(ExpectedConditions.invisibilityOfElementLocated(locator));
    }

    public void waitForTextToBePresentInElement(By locator, String text) {
        wait.until(ExpectedConditions.textToBePresentInElementLocated(locator, text));
    }

    public String getCurrentUrl() {
        return driver.getCurrentUrl();
    }

    public void refreshPage() {
        driver.navigate().refresh();
    }

    public void goBack() {
        driver.navigate().back();
    }

    public void goForward() {
        driver.navigate().forward();
    }

    public void switchToFrame(By locator) {
        WebElement frameElement = wait.until(ExpectedConditions.presenceOfElementLocated(locator));
        driver.switchTo().frame(frameElement);
    }

    public void switchToDefaultContent() {
        driver.switchTo().defaultContent();
    }
}