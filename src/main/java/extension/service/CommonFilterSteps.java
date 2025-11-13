package extension.service;

public class CommonFilterSteps {

    public static boolean isStructuralElement(ScrapingService.WebElementData element) {
        String text = element.getText().toLowerCase();
        String selector = element.getSelector().toLowerCase();

        if (selector.contains("h1") || selector.contains("h2") || selector.contains("h3") ||
                selector.contains("h4") || selector.contains("h5") || selector.contains("h6")) {
            return true;
        }

        return text.length() > 100;
    }

    public static boolean isNoiseElement(ScrapingService.WebElementData element) {
        String text = element.getText().toLowerCase();
        String selector = element.getSelector().toLowerCase();

        if (text.length() < 4) return true;
        if (text.matches("^[0-9\\W]+$")) return true;
        if (selector.contains("header") || selector.contains("footer") ||
                selector.contains("sidebar") || selector.contains("nav") ||
                selector.contains("menu") || selector.contains("ad")) {
            return true;
        }

        String[] noisePatterns = {
                "cookie", "privacy", "terms", "conditions", "copyright",
                "login", "sign up", "subscribe", "follow us", "share this",
                "advertisement", "ad", "sponsored", "promoted", "related articles"
        };

        for (String pattern : noisePatterns) {
            if (text.contains(pattern)) {
                return true;
            }
        }

        return false;
    }

    public static boolean containsKeywords(String text, String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }

        String[] keywords = query.split("\\s+");
        for (String keyword : keywords) {
            if (keyword.length() > 3) {
                String cleanKeyword = keyword.replaceAll("[^a-zA-ZąćęłńóśźżĄĆĘŁŃÓŚŹŻ0-9]", "").toLowerCase();
                String cleanText = text.toLowerCase();

                if (!cleanKeyword.isEmpty() && cleanText.contains(cleanKeyword)) {
                    return true;
                }
            }
        }
        return false;
    }
}
