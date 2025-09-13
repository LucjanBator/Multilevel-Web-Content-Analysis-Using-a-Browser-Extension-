package extension.services;

import org.openqa.selenium.*;

import java.util.*;

public class ScrapingService {
    private final WebDriver driver;
    private final JavascriptExecutor js;

    public ScrapingService(WebDriver driver) {
        this.driver = driver;
        this.js = (JavascriptExecutor) driver;
    }

    public void navigateTo(String url) {
        driver.get(url);
    }

    public String capturePageSnapshot() {
        return (String) js.executeScript("return document.documentElement.outerHTML");
    }

    public List<WebElementData> extractPageContent() {
        List<WebElementData> elements = new ArrayList<>();
        elements.addAll(extractLinks());
        elements.addAll(extractTextElements());
        return elements;
    }

    public List<WebElementData> extractPageContentWithRetry(int maxRetries, int delayMs) {
        List<WebElementData> result = new ArrayList<>();
        int attempts = 0;

        while (attempts < maxRetries) {
            try {
                result = extractPageContent();
                break;
            } catch (StaleElementReferenceException e) {
                attempts++;
                if (attempts >= maxRetries) throw e;
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                }
            }
        }
        return result;
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
        // Odrzuć puste selektory lub wskazujące na całą stronę
        if (selector == null || selector.isEmpty() || selector.equals("html") || selector.equals("body")) {
            return false;
        }

        // Odrzuć zbyt ogólne selektory
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
        //1. Najpierw spróbuj ID
        String id = element.getAttribute("id");
        if (id != null && !id.isEmpty()) {
            return "#" + id;
        }

        // 2. Generuj pełną ścieżkę CSS z indeksami
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
                        "" +
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