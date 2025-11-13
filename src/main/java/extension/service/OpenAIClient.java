package extension.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import extension.exceptions.OpenAIEmptyResponseException;
import extension.exceptions.OpenAIInvalidResponseException;
import extension.exceptions.OpenAIParsingException;
import extension.exceptions.OpenAIResponseException;
import okhttp3.OkHttpClient;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.openqa.selenium.Point;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OpenAIClient implements AIClient {
    private static final Logger logger = LogManager.getLogger(OpenAIClient.class);
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";
    private static final String CONTENT = "content";
    private String model = "gpt-3.5-turbo";
    private double temperature = 0.1;
    private int maxTokens = 3000;
    private final OkHttpClient client;
    private String apiKey;
    private String userContext;

    private long lastResponseTime;

    public OpenAIClient() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    public void setModel(String model) {
        if (model != null && !model.trim().isEmpty()) {
            this.model = model.trim();
            logger.debug("OpenAI model set to: {}", this.model);
        }
    }

    public void setTemperature(double temperature) {
        if (temperature >= 0.0 && temperature <= 2.0) {
            this.temperature = temperature;
            logger.debug("OpenAI temperature set to: {}", this.temperature);
        } else {
            logger.warn("Invalid temperature value: {}. Using default: 0.1", temperature);
            this.temperature = 0.1;
        }
    }

    public void setMaxTokens(int maxTokens) {
        if (maxTokens > 0 && maxTokens <= 4096) {
            this.maxTokens = maxTokens;
            logger.debug("OpenAI max tokens set to: {}", this.maxTokens);
        } else {
            logger.warn("Invalid max tokens value: {}. Using default: 3000", maxTokens);
            this.maxTokens = 3000;
        }
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setUserContext(String userContext) {
        this.userContext = userContext;
    }

    public String getUserContext() {
        return userContext;
    }

    @Override
    public FilteredElements filterElements(List<ScrapingService.WebElementData> elements, String userQuery) {
        if (userQuery == null || userQuery.trim().isEmpty()) {
            logger.warn("User query is empty, returning all elements");
            return new FilteredElements(List.of(), List.of());
        }
        long startTime = System.currentTimeMillis();
        try {
            String prompt = buildPrompt(elements, userQuery);
            String openAIResponse = callOpenAI(prompt);
            FilteredElements result = parseOpenAIResponse(openAIResponse, elements);

            this.lastResponseTime = System.currentTimeMillis() - startTime;
            return result;

        } catch (Exception e) {
            logger.error("Error filtering elements with OpenAI API for model {}: {}", model, e.getMessage());
            this.lastResponseTime = System.currentTimeMillis() - startTime;
            return new FilteredElements(List.of(), List.of());
        }
    }

    private String buildPrompt(List<ScrapingService.WebElementData> elements, String userQuery) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Jesteś asystentem pomagającym znaleźć informacje na stronie internetowej. ")
                .append("Otrzymasz listę elementów strony i pytanie użytkownika. ")
                .append("Twoim zadaniem jest zwrócenie JSON z dwoma listami:\n\n")
                .append("1. 'text_elements' - elementy typu TEXT które bezpośrednio odpowiadają na pytanie\n")
                .append("2. 'link_elements' - elementy typu LINK które mogą prowadzić do stron z odpowiedzią\n\n")
                .append("PYTANIE UŻYTKOWNIKA: ").append(userQuery).append("\n\n")
                .append("ELEMENTY STRONY:\n");

        int maxElements = Math.min(elements.size(), 150);
        createPrompt(elements, prompt, maxElements);

        prompt.append("\nINSTRUKCJE:\n")
                .append("- Zwróć tylko elementy które są RELEWANTNE do pytania\n")
                .append("- Dla 'text_elements': wybierz te które bezpośrednio odpowiadają na pytanie\n")
                .append("- Dla 'link_elements': wybierz linki które mogą prowadzić do odpowiedzi\n")
                .append("- Zachowaj ORYGINALNE dane elementów (selektor, text, type)\n")
                .append("- Jeśli nie ma pasujących elementów, zwróć puste listy\n\n")
                .append("Odpowiedz TYLKO w formacie JSON:\n")
                .append("{\n")
                .append("  \"text_elements\": [\n")
                .append("    {\"selector\": \"...\", \"text\": \"...\", \"type\": \"TEXT\"")
                .append("  ],\n")
                .append("  \"link_elements\": [\n")
                .append("    {\"selector\": \"...\", \"text\": \"...\", \"type\": \"LINK\"")
                .append("  ]\n")
                .append("}");

        return prompt.toString();
    }

    static void createPrompt(List<ScrapingService.WebElementData> elements, StringBuilder prompt, int maxElements) {
        for (int i = 0; i < maxElements; i++) {
            ScrapingService.WebElementData element = elements.get(i);
            prompt.append(i + 1).append(". ")
                    .append("TYPE: ").append(element.getType())
                    .append(", TEXT: ").append(element.getText())
                    .append(", SELECTOR: ").append(element.getSelector())
                    .append("\n");
        }
    }

    private String callOpenAI(String prompt) {
        String requestBody = buildRequestBody(prompt);

        logger.info("Calling OpenAI API with model: {}, max_tokens: {}, temperature: {}",
                model, maxTokens, temperature);

        RequestBody body = RequestBody.create(
                requestBody,
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url(API_URL)
                .post(body)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            assert response.body() != null;
            String responseBody = response.body().string();

            if (!response.isSuccessful()) {
                setLastParsingError(responseBody);

                if (responseBody.contains("model_not_found")) {
                    throw new OpenAIResponseException("Model not available: " + model,
                            String.format("HTTP %d: %s", response.code(), responseBody));
                } else if (responseBody.contains("max_tokens")) {
                    throw new OpenAIResponseException("max_tokens too high for model: " + model,
                            String.format("HTTP %d: %s", response.code(), responseBody));
                }

                throw new OpenAIResponseException(
                        "API request failed for model: " + model,
                        String.format("HTTP %d: %s - Body: %s", response.code(), response.message(), responseBody)
                );
            }

            return extractContentFromResponse(responseBody);

        } catch (IOException e) {
            throw new OpenAIResponseException("Network error during API call for model: " + model, e);
        }
    }

    private String buildRequestBody(String prompt) {
        try {
            org.json.JSONObject requestBody = new org.json.JSONObject();
            requestBody.put("model", model);
            requestBody.put("temperature", temperature);
            requestBody.put("max_tokens", maxTokens);

            org.json.JSONObject responseFormat = new org.json.JSONObject();
            responseFormat.put("type", "json_object");
            requestBody.put("response_format", responseFormat);

            JSONArray messages = getObjects(prompt);

            requestBody.put("messages", messages);

            return requestBody.toString();

        } catch (org.json.JSONException e) {
            throw new OpenAIResponseException("Error building OpenAI request", e);
        }
    }

    @NotNull
    private static JSONArray getObjects(String prompt) {
        JSONArray messages = new JSONArray();

        org.json.JSONObject systemMessage = new org.json.JSONObject();
        systemMessage.put("role", "system");
        systemMessage.put(CONTENT, "Zawsze zwracaj poprawny JSON. Nie dodawaj żadnego tekstu przed ani po JSON.");
        messages.put(systemMessage);

        org.json.JSONObject userMessage = new org.json.JSONObject();
        userMessage.put("role", "user");
        userMessage.put(CONTENT, prompt);
        messages.put(userMessage);
        return messages;
    }

    private String extractContentFromResponse(String responseBody) {
        try {
            logger.debug("Parsing OpenAI response: {}", responseBody);

            org.json.JSONObject jsonResponse = new org.json.JSONObject(responseBody);
            validateOpenAIResponse(jsonResponse, responseBody);

            org.json.JSONArray choices = jsonResponse.getJSONArray("choices");
            validateChoicesArray(choices, responseBody);

            org.json.JSONObject firstChoice = choices.getJSONObject(0);
            org.json.JSONObject message = getMessageFromChoice(firstChoice, responseBody);

            return extractContentFromMessage(message, responseBody);

        } catch (org.json.JSONException e) {
            throw new OpenAIParsingException(responseBody, e);
        }
    }

    private void validateOpenAIResponse(org.json.JSONObject jsonResponse, String responseBody) {
        if (!jsonResponse.has("choices")) {
            throw new OpenAIInvalidResponseException("choices", responseBody);
        }
    }

    private void validateChoicesArray(org.json.JSONArray choices, String responseBody) {
        if (choices.isEmpty()) {
            throw new OpenAIEmptyResponseException("choices array", responseBody);
        }
    }

    private org.json.JSONObject getMessageFromChoice(org.json.JSONObject choice, String responseBody) {
        if (!choice.has("message")) {
            throw new OpenAIInvalidResponseException("message", responseBody);
        }
        return choice.getJSONObject("message");
    }

    private String extractContentFromMessage(org.json.JSONObject message, String responseBody) {
        if (!message.has(CONTENT)) {
            throw new OpenAIInvalidResponseException(CONTENT, responseBody);
        }

        String content = message.getString(CONTENT);
        if (content == null || content.trim().isEmpty()) {
            throw new OpenAIEmptyResponseException(CONTENT, responseBody);
        }

        return content;
    }

    private FilteredElements parseOpenAIResponse(String response, List<ScrapingService.WebElementData> allElements) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            OpenAIFilterResult result = mapper.readValue(response, OpenAIFilterResult.class);

            List<ScrapingService.WebElementData> mappedTextElements =
                    mapOpenAIElementsToRealElements(result.getTextElements(), allElements);
            List<ScrapingService.WebElementData> mappedLinkElements =
                    mapOpenAIElementsToRealElements(result.getLinkElements(), allElements);

            logger.info("Mapped {} text elements and {} link elements",
                    mappedTextElements.size(), mappedLinkElements.size());

            return new FilteredElements(mappedTextElements, mappedLinkElements);

        } catch (Exception e) {
            logger.error("Error parsing OpenAI response: {}, using partial parsing", e.getMessage());
            logger.debug("Failed response content: {}", response);

            return parsePartialOpenAIResponse(response, allElements);
        }
    }

    private FilteredElements parsePartialOpenAIResponse(String response, List<ScrapingService.WebElementData> allElements) {
        try {
            logger.debug("Attempting partial parsing of OpenAI response");

            List<ScrapingService.WebElementData> textElements = new ArrayList<>();
            List<ScrapingService.WebElementData> linkElements = new ArrayList<>();

            extractOpenAIElements(response, textElements, linkElements, allElements);

            if (textElements.isEmpty() && linkElements.isEmpty()) {
                logger.debug("Partial parsing found no elements, using keyword filter");
                return simpleKeywordFilter(allElements, userContext);
            }

            logger.info("OpenAI partial parsing found {} text elements and {} link elements",
                    textElements.size(), linkElements.size());

            return new FilteredElements(textElements, linkElements);

        } catch (Exception e) {
            logger.error("Error in OpenAI partial parsing: {}", e.getMessage());
            return simpleKeywordFilter(allElements, userContext);
        }
    }

    private void extractOpenAIElements(String response,
                                       List<ScrapingService.WebElementData> textElements,
                                       List<ScrapingService.WebElementData> linkElements,
                                       List<ScrapingService.WebElementData> allElements) {
        try {
            Pattern elementPattern = Pattern.compile(
                    "\\{\\s*\"selector\"\\s*:\\s*\"([^\"]*)\"[^}]*\"text\"\\s*:\\s*\"([^\"]*)\"[^}]*\"type\"\\s*:\\s*\"([^\"]*)\"[^}]*\\}",
                    Pattern.DOTALL
            );

            Matcher matcher = elementPattern.matcher(response);
            while (matcher.find()) {
                try {
                    String selector = matcher.group(1);
                    String text = matcher.group(2);
                    String type = matcher.group(3);

                    logger.debug("Found OpenAI element - selector: {}, text: {}, type: {}", selector, text, type);

                    allElements.stream()
                            .filter(real -> real.getSelector().equals(selector))
                            .findFirst()
                            .ifPresent(element -> {
                                if ("LINK".equalsIgnoreCase(type)) {
                                    linkElements.add(element);
                                    logger.debug("Added to LINK elements: {}", text);
                                } else if ("TEXT".equalsIgnoreCase(type)) {
                                    textElements.add(element);
                                    logger.debug("Added to TEXT elements: {}", text);
                                } else {
                                    if (element.getType() == ScrapingService.ElementType.LINK) {
                                        linkElements.add(element);
                                    } else {
                                        textElements.add(element);
                                    }
                                    logger.debug("Added using original type: {}", element.getType());
                                }
                            });

                } catch (Exception e) {
                    logger.debug("Failed to parse OpenAI element from regex match");
                }
            }

            if (textElements.isEmpty() && linkElements.isEmpty()) {
                extractOpenAIElementsSimple(response, textElements, linkElements, allElements);
            }

        } catch (Exception e) {
            logger.debug("Error in OpenAI element extraction: {}", e.getMessage());
        }
    }

    private void extractOpenAIElementsSimple(String response,
                                             List<ScrapingService.WebElementData> textElements,
                                             List<ScrapingService.WebElementData> linkElements,
                                             List<ScrapingService.WebElementData> allElements) {
        try {
            String[] patterns = {
                    "\"selector\"\\s*:\\s*\"([^\"]*)\".*?\"text\"\\s*:\\s*\"([^\"]*)\".*?\"type\"\\s*:\\s*\"([^\"]]*)\"",
                    "\"selector\"\\s*:\\s*\"([^\"]*)\".*?\"text\"\\s*:\\s*\"([^\"]*)\"",
                    "selector[\\\"']?\\s*:\\s*[\\\"']([^\\\"']*)[\\\"'].*?text[\\\"']?\\s*:\\s*[\\\"']([^\\\"']*)[\\\"']"
            };

            for (String patternStr : patterns) {
                Pattern pattern = Pattern.compile(patternStr, Pattern.DOTALL);
                Matcher matcher = pattern.matcher(response);

                while (matcher.find()) {
                    try {
                        String selector = matcher.group(1);
                        String text = matcher.group(2);
                        String type = matcher.groupCount() >= 3 ? matcher.group(3) : "UNKNOWN";

                        logger.debug("Found simple OpenAI element - selector: {}, text: {}, type: {}", selector, text, type);

                        allElements.stream()
                                .filter(real -> real.getSelector().equals(selector))
                                .findFirst()
                                .ifPresent(element -> {
                                    if ("LINK".equalsIgnoreCase(type) || element.getType() == ScrapingService.ElementType.LINK) {
                                        linkElements.add(element);
                                    } else {
                                        textElements.add(element);
                                    }
                                });

                    } catch (Exception e) {
                        logger.debug("Failed to parse element with simple pattern");
                    }
                }

                if (!textElements.isEmpty() || !linkElements.isEmpty()) {
                    break;
                }
            }

        } catch (Exception e) {
            logger.debug("Error in simple OpenAI element extraction: {}", e.getMessage());
        }
    }


    private List<ScrapingService.WebElementData> mapOpenAIElementsToRealElements(
            List<ScrapingService.WebElementData> openAIElements,
            List<ScrapingService.WebElementData> realElements) {

        if (openAIElements == null || openAIElements.isEmpty()) {
            return List.of();
        }

        List<ScrapingService.WebElementData> mappedElements = new ArrayList<>();

        for (ScrapingService.WebElementData openAIElement : openAIElements) {
            ScrapingService.WebElementData realElement = findMatchingElementBySelector(
                    openAIElement.getSelector(), realElements);

            if (realElement != null) {
                mappedElements.add(realElement);
                logger.debug("Found matching element for selector: {}", openAIElement.getSelector());
            } else {
                logger.warn("No matching element found for selector: {}", openAIElement.getSelector());
                mappedElements.add(createElementFromOpenAI(openAIElement));
            }
        }

        return mappedElements;
    }

    private ScrapingService.WebElementData findMatchingElementBySelector(
            String selector, List<ScrapingService.WebElementData> realElements) {

        if (selector == null) return null;

        return realElements.stream()
                .filter(element -> selector.equals(element.getSelector()))
                .findFirst()
                .orElse(null);
    }

    private ScrapingService.WebElementData createElementFromOpenAI(ScrapingService.WebElementData openAIElement) {
        return new ScrapingService.WebElementData(
                openAIElement.getSelector(),
                openAIElement.getText(),
                openAIElement.getType(),
                new Point(0, 0)
        );
    }

    private FilteredElements simpleKeywordFilter(List<ScrapingService.WebElementData> elements, String query) {
        if (query == null || query.trim().isEmpty()) {
            return new FilteredElements(List.of(), List.of());
        }

        List<ScrapingService.WebElementData> textElements = elements.stream()
                .filter(e -> e.getType() == ScrapingService.ElementType.TEXT)
                .filter(e -> CommonFilterSteps.containsKeywords(e.getText(), query))
                .toList();

        List<ScrapingService.WebElementData> linkElements = elements.stream()
                .filter(e -> e.getType() == ScrapingService.ElementType.LINK)
                .filter(e -> CommonFilterSteps.containsKeywords(e.getText(), query))
                .toList();

        return new FilteredElements(textElements, linkElements);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class OpenAIFilterResult {
        @JsonProperty("text_elements")
        private List<ScrapingService.WebElementData> textElements;
        @JsonProperty("link_elements")
        private List<ScrapingService.WebElementData> linkElements;

        public List<ScrapingService.WebElementData> getTextElements() {
            return textElements != null ? textElements : List.of();
        }

        public List<ScrapingService.WebElementData> getLinkElements() {
            return linkElements != null ? linkElements : List.of();
        }
    }

    @Override
    public double getCostEstimation(int inputTokens, int outputTokens) {
        return switch (this.model) {
            case "gpt-4o" -> (inputTokens * 0.03 + outputTokens * 0.06) / 1000;
            case "gpt-3.5-turbo" -> (inputTokens * 0.0015 + outputTokens * 0.002) / 1000;
            default -> (inputTokens * 0.003 + outputTokens * 0.006) / 1000;
        };
    }

    @Override
    public long getLastResponseTime() {
        return lastResponseTime;
    }

    @Override
    public String getModel() {
        return null;
    }

    private String lastParsingError;

    @Override
    public String getLastParsingError() {
        return lastParsingError;
    }

    @Override
    public void setLastParsingError(String error) {
        this.lastParsingError = error;
    }

    @Override
    public boolean hasParsingError() {
        return lastParsingError != null && !lastParsingError.isEmpty();
    }
}
