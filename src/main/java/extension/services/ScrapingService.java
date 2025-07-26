package extension.services;

import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static extension.utils.WebUtils.waitForPageLoad;

public class ScrapingService {
    private static final Logger logger = LogManager.getLogger(ScrapingService.class);
    private final WebDriver driver;
    private final WebDriverWait wait;

    private final JavascriptExecutor js;
    private final Actions actions;

    public ScrapingService(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(10));
        this.js = (JavascriptExecutor) driver;
        this.actions = new Actions(driver);
    }

    public void navigateTo(String url) {
        driver.get(url);
    }

    public String getPageTitle() {
        return driver.getTitle();
    }

    // Metoda do przewijania i pobierania całej strony
    public Map<String, WebElementData> extractPageContent() {
        Map<String, WebElementData> elementsMap = new LinkedHashMap<>();
        Set<String> uniqueTexts = new HashSet<>();

        try {
            // 1. Pobierz wszystkie elementy za jednym razem
            List<WebElement> allElements = driver.findElements(
                    By.cssSelector("body *:not(script):not(style):not(noscript):not(svg):not(path)"));

            // 2. Filtruj elementy przed iteracją
            List<WebElement> filteredElements = allElements.stream()
                    .filter(e -> {
                        try {
                            return e.isDisplayed() &&
                                    !e.getText().trim().isEmpty() &&
                                    e.getRect().getHeight() > 0 &&
                                    e.getRect().getWidth() > 0;
                        } catch (Exception ex) {
                            return false;
                        }
                    })
                    .collect(Collectors.toList());

            // 3. Iteruj tylko przez widoczne elementy
            for (WebElement element : filteredElements) {
                try {
                    String text = element.getText().trim();
                    if (!uniqueTexts.contains(text)) {
                        String selector = generateOptimalSelector(element);

                        if (selector != null && !selector.isEmpty()) {
                            ElementType type = element.getTagName().equalsIgnoreCase("a")
                                    ? ElementType.LINK : ElementType.TEXT;

                            elementsMap.put(selector,
                                    new WebElementData(selector, text, type, element.getLocation()));
                            uniqueTexts.add(text);
                        }
                    }
                } catch (StaleElementReferenceException ignored) {
                    // Pomijamy elementy, które zniknęły
                }
            }
        } catch (Exception e) {
            logger.error("Error during optimized content extraction", e);
        }

        return elementsMap;
    }

    private String generateOptimalSelector(WebElement element) {
        try {
            // 1. Najpierw spróbuj ID
            String id = element.getAttribute("id");
            if (id != null && !id.isEmpty() && isSelectorUnique("#" + id)) {
                    return "#" + id;
                }


            // 2. Spróbuj kombinacji klas z tagiem
            String tag = element.getTagName().toLowerCase();
            String classes = element.getAttribute("class");
            if (classes != null && !classes.isEmpty()) {
                String[] classList = classes.split("\\s+");
                for (String cls : classList) {
                    if (!cls.isEmpty()) {
                        String selector = tag + "." + cls;
                        if (isSelectorUnique(selector)) {
                            return selector;
                        }
                    }
                }
            }

            // 3. Spróbuj atrybutów danych
            String[] dataAttrs = {"data-testid", "data-qa", "data-cy", "data-test", "data-id"};
            for (String attr : dataAttrs) {
                String value = element.getAttribute(attr);
                if (value != null && !value.isEmpty() && isSelectorUnique("[" + attr + "='" + value + "']")) {
                        return "[" + attr + "='" + value + "']";
                    }

            }

            // 4. Spróbuj atrybutów ARIA
            String ariaLabel = element.getAttribute("aria-label");
            if (ariaLabel != null && !ariaLabel.isEmpty() && isSelectorUnique("[aria-label='" + ariaLabel + "']")) {
                    return "[aria-label='" + ariaLabel + "']";
                }


            // 5. Generuj krótką ścieżkę CSS
            return generateShortCssPath(element);
        } catch (Exception e) {
            return generateFallbackSelector(element);
        }
    }

    private String generateShortCssPath(WebElement element) {
        return (String) js.executeScript(
                "function getCssPath(el) {" +
                        "    if (el.id) return '#' + el.id;" +
                        "    let path = [];" +
                        "    while (el.parentNode) {" +
                        "        let selector = el.tagName.toLowerCase();" +
                        "        if (el.className && typeof el.className === 'string') {" +
                        "            let classes = el.className.trim().split(/\\s+/);" +
                        "            if (classes.length > 0) selector += '.' + classes.join('.');" +
                        "        }" +
                        "        path.unshift(selector);" +
                        "        if (el.parentNode.querySelectorAll(selector).length === 1) break;" +
                        "        el = el.parentNode;" +
                        "    }" +
                        "    return path.join(' > ');" +
                        "}" +
                        "return getCssPath(arguments[0]);", element);
    }

    private String generateFallbackSelector(WebElement element) {
        // Minimalna ścieżka CSS jako fallback
        return element.getTagName().toLowerCase() +
                ":contains('" + element.getText().substring(0, Math.min(20, element.getText().length())) + "')";
    }

    private boolean isSelectorUnique(String selector) {
        try {
            return driver.findElements(By.cssSelector(selector)).size() == 1;
        } catch (Exception e) {
            return false;
        }
    }

    // Klasa pomocnicza do przechowywania danych elementu
    public static class WebElementData {
        private final String selector;
        private final String text;
        private final ElementType type;
        private final Point location;

        public WebElementData(String selector, String text, ElementType type, Point location) {
            this.selector = Objects.requireNonNull(selector, "Selector cannot be null");
            this.text = Objects.requireNonNull(text, "Text cannot be null");
            this.type = Objects.requireNonNull(type, "Type cannot be null");
            this.location = Objects.requireNonNull(location, "Location cannot be null");
        }

        // Gettery
        public String getSelector() {
            return selector;
        }

        public String getText() {
            return text;
        }

        public ElementType getType() {
            return type;
        }

        public Point getLocation() {
            return location;
        }

        @Override
        public String toString() {
            return "WebElementData{" +
                    "selector='" + selector + '\'' +
                    ", text='" + text + '\'' +
                    ", type=" + type +
                    ", location=" + location +
                    '}';
        }
    }
    public enum ElementType {
        TEXT, LINK
    }
}