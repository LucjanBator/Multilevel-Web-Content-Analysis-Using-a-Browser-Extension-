package extension.service;

import java.util.List;

public interface AIClient {
    FilteredElements filterElements(List<ScrapingService.WebElementData> elements, String userQuery);
    void setApiKey(String apiKey);
    void setModel(String model);
    String getModel();
    double getCostEstimation(int inputTokens, int outputTokens);
    long getLastResponseTime();

    void setUserContext(String userContext);
    String getUserContext();

    String getLastParsingError();
    void setLastParsingError(String error);
    boolean hasParsingError();
}