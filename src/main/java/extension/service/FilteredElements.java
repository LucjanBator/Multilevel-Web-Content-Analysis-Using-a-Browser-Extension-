package extension.service;

import java.util.List;

public record FilteredElements(List<ScrapingService.WebElementData> textElements,
                               List<ScrapingService.WebElementData> linkElements) {
    public FilteredElements {
        textElements = textElements != null ? textElements : List.of();
        linkElements = linkElements != null ? linkElements : List.of();
    }
}
