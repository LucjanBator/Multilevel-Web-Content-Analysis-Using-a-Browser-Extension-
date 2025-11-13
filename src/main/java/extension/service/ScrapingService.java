package extension.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import extension.core.config.AppConfig;
import extension.exceptions.InvalidConfigException;
import extension.exceptions.MissingConfigException;
import extension.exceptions.ScrapingInterruptedException;
import org.openqa.selenium.Point;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ScrapingService {
    private final WebDriver driver;
    private final JavascriptExecutor js;

    private AIClient aiClient;

    private List<WebElementData> lastExtractedElements;

    Logger logger = LogManager.getLogger(ScrapingService.class.getName());

    public ScrapingService(WebDriver driver) {
        this.driver = driver;
        this.js = (JavascriptExecutor) driver;
        this.aiClient = createDefaultOpenAIClient();
    }

    public ScrapingService(WebDriver driver, AIClient aiClient) {
        this.driver = driver;
        this.js = (JavascriptExecutor) driver;
        this.aiClient = aiClient;
    }

    public List<WebElementData> getLastExtractedElements() {
        return lastExtractedElements != null ?
                new ArrayList<>(lastExtractedElements) : Collections.emptyList();
    }

    private AIClient createDefaultOpenAIClient() {
        OpenAIClient client = new OpenAIClient();
        try {
            client.setApiKey(AppConfig.getOpenAIApiKey());
            client.setModel(AppConfig.getOpenAIModel());
            client.setTemperature(AppConfig.getOpenAITemperature());
            client.setMaxTokens(AppConfig.getOpenAIMaxTokens());
            logger.info("Initialized default OpenAI client with model: {}", AppConfig.getOpenAIModel());
        } catch (MissingConfigException e) {
            logger.error("Missing configuration for OpenAI client: {}", e.getMessage());
            throw e;
        } catch (InvalidConfigException e) {
            logger.error("Invalid configuration for OpenAI client: {}", e.getMessage());
            throw e;
        }
        return client;
    }

    public void setUserContext(String userContext) {
        aiClient.setUserContext(userContext);
    }

    public void navigateTo(String url) {
        driver.get(url);
    }

    public void setApiKey(String apiKey) {
        aiClient.setApiKey(apiKey);
    }

    public String capturePageSnapshot() {
        return (String) js.executeScript("return document.documentElement.outerHTML");
    }

    private List<WebElementData> extractLinks() {
        List<WebElementData> links = new ArrayList<>();
        List<WebElement> allLinks = driver.findElements(By.tagName("a"));

        for (WebElement link : allLinks) {
            if (link.isDisplayed()) {
                String text = link.getText().trim();
                if (!text.isEmpty()) {
                    String selector = generateUniqueSelector(link);
                    links.add(new WebElementData(
                            selector, text, ElementType.LINK, link.getLocation()
                    ));
                }
            }
        }
        return links;
    }

    private boolean isSameAsParentText(WebElement element, String text) {
        try {
            WebElement parent = (WebElement) ((JavascriptExecutor) driver).executeScript(
                    "return arguments[0].parentNode;", element);
            return parent != null && text.equals(parent.getText().trim());
        } catch (Exception e) {
            return false;
        }
    }

    private List<WebElementData> extractTextElements() {
        List<WebElementData> textElements = new ArrayList<>();
        List<WebElement> allElements = driver.findElements(By.xpath("//*[not(self::a)][text()!='']"));

        for (WebElement element : allElements) {
            if (element.isDisplayed()) {
                String text = element.getText().trim();
                if (!text.isEmpty() && !isSameAsParentText(element, text)) {
                    String selector = generateUniqueSelector(element);
                    if (isValidSelector(selector)) {
                        textElements.add(new WebElementData(
                                selector, text, ElementType.TEXT, element.getLocation()
                        ));
                    }
                }
            }
        }
        return textElements;
    }

    private boolean isValidSelector(String selector) {
        if (selector == null || selector.isEmpty() || selector.equals("html") || selector.equals("body")) {
            return false;
        }

        String[] invalidPatterns = {
                "",
                "html > body",
                "body:nth-of-type(1)",
                "html:nth-of-type(1)",
                "body:first-of-type",
                "html:first-of-type"
        };

        for (String pattern : invalidPatterns) {
            if (selector.equals(pattern) || selector.startsWith(pattern + " >")) {
                return false;
            }
        }

        return true;
    }

    private String generateUniqueSelector(WebElement element) {
        String id = element.getAttribute("id");
        if (id != null && !id.isEmpty()) {
            return "#" + id;
        }

        return (String) js.executeScript(
                "function getExactSelector(el) {" +
                        "    function getIndex(node) {" +
                        "        let count = 1;" +
                        "        let sibling = node.previousSibling;" +
                        "        while (sibling) {" +
                        "            if (sibling.nodeType === Node.ELEMENT_NODE && " +
                        "                sibling.tagName === node.tagName) {" +
                        "                count++;" +
                        "            }" +
                        "            sibling = sibling.previousSibling;" +
                        "        }" +
                        "        return count;" +
                        "    }" +
                        "    let path = [];" +
                        "    while (el.parentNode && el.parentNode.nodeType === Node.ELEMENT_NODE) {" +
                        "        let selector = el.tagName.toLowerCase();" +
                        "        if (el.id) {" +
                        "            path.unshift('#' + el.id);" +
                        "            break;" +
                        "        }" +
                        "        let index = getIndex(el);" +
                        "        selector += ':nth-of-type(' + index + ')';" +
                        "        path.unshift(selector);" +
                        "        el = el.parentNode;" +
                        "    }" +
                        "    return path.join(' > ');" +
                        "}" +
                        "return getExactSelector(arguments[0]);",
                element
        );
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WebElementData {
        private String selector;
        private String text;
        private ElementType type;
        @JsonIgnore
        private Point location;

        @SuppressWarnings("unused")
        public WebElementData() {
        }

        public WebElementData(String selector, String text, ElementType type, Point location) {
            this.selector = Objects.requireNonNull(selector, "Selector cannot be null");
            this.text = Objects.requireNonNull(text, "Text cannot be null");
            this.type = Objects.requireNonNull(type, "Type cannot be null");
            this.location = Objects.requireNonNull(location, "Location cannot be null");
        }

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

    public List<ScrapingService.WebElementData> extractContent(String userContext) {
        try {
            List<ScrapingService.WebElementData> allElements = new ArrayList<>();
            allElements.addAll(extractLinks());
            allElements.addAll(extractTextElements());

            logger.info("Total elements found: {}", allElements.size());

            List<ScrapingService.WebElementData> preFiltered = preFilterElements(allElements, userContext);
            logger.info("After pre-filtering: {} elements", preFiltered.size());

            return preFiltered;
        } finally {
        }
    }

    private List<ScrapingService.WebElementData> preFilterElements(List<ScrapingService.WebElementData> elements, String userContext) {
        return elements.stream()
                .filter(element -> {
                    String text = element.getText().toLowerCase();
                    String userQuery = userContext.toLowerCase();

                    if (CommonFilterSteps.containsKeywords(text, userQuery)) {
                        return true;
                    }

                    if (CommonFilterSteps.isStructuralElement(element)) {
                        return true;
                    }
                    return !CommonFilterSteps.isNoiseElement(element);
                })
                .toList();
    }

    public FilteredElements extractAndFilterContent() {
        try {
            List<WebElementData> allElements = extractContent(aiClient.getUserContext());

            this.lastExtractedElements = new ArrayList<>(allElements);

            FilteredElements filtered = aiClient.filterElements(
                    allElements,
                    aiClient.getUserContext()
            );

            logger.info("AI filtering results:");
            logger.info("Text elements: {}", filtered.textElements().size());
            logger.info("Link elements: {}", filtered.linkElements().size());

            printFoundElements(filtered);
            return filtered;

        } catch (Exception e) {
            throw new ScrapingInterruptedException(e);
        }
    }

    private void printFoundElements(FilteredElements filtered) {
        logger.info("\n=== ZNALEZIONE ELEMENTY ===");

        if (filtered.textElements().isEmpty() && filtered.linkElements().isEmpty()) {
            logger.info("Nie znaleziono żadnych pasujących elementów");
            return;
        }

        if (!filtered.textElements().isEmpty()) {
            logger.info("\nZNALEZIONE TEKSTY ({}{}", filtered.textElements().size(), "):");
            for (int i = 0; i < filtered.textElements().size(); i++) {
                WebElementData element = filtered.textElements().get(i);
                logger.info("{}. \"{}{}", (i + 1),  element.getText(), "\"");
                logger.info("   Selektor: {}", element.getSelector());
                logger.info("   Typ: {}", element.getType());
                logger.info("   Lokalizacja: {}\n", element.getLocation());
            }
        }

        if (!filtered.linkElements().isEmpty()) {
            logger.info("\nZNALEZIONE LINKI ({}{}", filtered.linkElements().size(), "):");
            for (int i = 0; i < filtered.linkElements().size(); i++) {
                WebElementData element = filtered.linkElements().get(i);
                logger.info( "{}. \"{}{}", (i + 1), element.getText(), "\"");
                logger.info("   Selektor: {}", element.getSelector());
                logger.info("   Typ: {}", element.getType());
                logger.info("   Lokalizacja: {}\n", element.getLocation());
            }
        }

        logger.info("=== KONIEC ZNALEZIONYCH ELEMENTÓW ===\n");
    }

    public void highlightElementsOnPage(FilteredElements filteredElements) {
        try {
            for (ScrapingService.WebElementData element : filteredElements.textElements()) {
                highlightElement(element.getSelector(), "text-highlight");
            }

            for (ScrapingService.WebElementData element : filteredElements.linkElements()) {
                highlightElement(element.getSelector(), "link-highlight");
            }

            logger.info("Highlighted {} text elements and {} link elements",
                    filteredElements.textElements().size(),
                    filteredElements.linkElements().size());

        } catch (Exception e) {
            logger.error("Error highlighting elements on page", e);
        }
    }

    private void highlightElement(String selector, String highlightType) {
        try {
            String script = String.format(
                    "try {" +
                            "   const element = document.querySelector('%s');" +
                            "   if (element) {" +
                            "       element.classList.add('%s');" +
                            "   }" +
                            "} catch (e) { console.log('Could not highlight element: ' + e.message); }",
                    selector, highlightType
            );
            js.executeScript(script);
        } catch (Exception e) {
            logger.debug("Could not highlight element with selector: {}", selector);
        }
    }

    public void removeHighlights() {
        try {
            String script =
                    "document.querySelectorAll('.text-highlight, .link-highlight').forEach(el => {" +
                            "   el.classList.remove('text-highlight', 'link-highlight');" +
                            "});";
            js.executeScript(script);
        } catch (Exception e) {
            logger.error("Error removing highlights", e);
        }
    }

    public void injectHighlightStyles() {
        String cssStyles =
                "<style>" +
                        ".text-highlight {" +
                        "   background-color: rgba(76, 175, 80, 0.3) !important;" +
                        "   border: 2px solid #4CAF50 !important;" +
                        "   border-radius: 4px !important;" +
                        "   padding: 2px !important;" +
                        "   box-shadow: 0 0 5px rgba(76, 175, 80, 0.5) !important;" +
                        "}" +
                        ".link-highlight {" +
                        "   background-color: rgba(33, 150, 243, 0.3) !important;" +
                        "   border: 2px solid #2196F3 !important;" +
                        "   border-radius: 4px !important;" +
                        "   padding: 2px !important;" +
                        "   box-shadow: 0 0 5px rgba(33, 150, 243, 0.5) !important;" +
                        "}" +
                        ".text-highlight::before {" +
                        "   content: !important;" +
                        "   margin-right: 5px !important;" +
                        "   font-size: 12px !important;" +
                        "}" +
                        ".link-highlight::before {" +
                        "   content: !important;" +
                        "   margin-right: 5px !important;" +
                        "   font-size: 12px !important;" +
                        "}" +
                        "</style>";

        try {
            js.executeScript(
                    "if (!document.getElementById('ai-highlight-styles')) {" +
                            "   var styleElement = document.createElement('div');" +
                            "   styleElement.innerHTML = arguments[0];" +
                            "   document.head.appendChild(styleElement.firstChild);" +
                            "}",
                    cssStyles
            );
        } catch (Exception e) {
            logger.error("Error injecting highlight styles", e);
        }
    }
}