package extension.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import extension.exceptions.AIResponseException;
import okhttp3.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GeminiClient implements AIClient {
    private static final Logger logger = LogManager.getLogger(GeminiClient.class);
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    private String model = "gemini-1.5-pro-latest";
    private double temperature = 0.1;
    private int maxOutputTokens = 2000;
    private final OkHttpClient client;
    private String apiKey;
    private long lastResponseTime;
    private String userContext;

    public GeminiClient() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
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
            String prompt = buildGeminiPrompt(elements, userQuery);
            logger.debug("Sending request to Gemini API with model: {}", model);
            String response = callGeminiAPI(prompt);
            logger.debug("Received response from Gemini API");
            FilteredElements result = parseGeminiResponse(response, elements);

            this.lastResponseTime = System.currentTimeMillis() - startTime;
            logger.info("Gemini processing completed in {} ms", lastResponseTime);
            return result;

        } catch (Exception e) {
            logger.error("Error filtering elements with Gemini API for model {}: {}", model, e.getMessage());
            this.lastResponseTime = System.currentTimeMillis() - startTime;
            return new FilteredElements(List.of(), List.of());
        }
    }

    private String buildGeminiPrompt(List<ScrapingService.WebElementData> elements, String userQuery) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("Jesteś asystentem analizującym elementy strony internetowej. ")
                .append("Użytkownik szuka odpowiedzi na pytanie: '").append(userQuery).append("'\n\n");

        int maxElements = Math.min(elements.size(), 150);
        for (int i = 0; i < maxElements; i++) {
            ScrapingService.WebElementData element = elements.get(i);
            prompt.append(i + 1).append(". [")
                    .append(element.getType())
                    .append("] \"")
                    .append(escapeText(element.getText()))
                    .append("\" (selector: ")
                    .append(element.getSelector())
                    .append(")\n");
        }

        prompt.append("\nINSTRUKCJE:\n")
                .append("1. Wybierz tylko elementy które bezpośrednio odpowiadają na pytanie użytkownika\n")
                .append("2. Zwróć wyniki w formacie JSON\n")
                .append("3. Zachowaj oryginalne selektory i tekst\n")
                .append("4. Jeśli nie ma pasujących elementów, zwróć puste listy\n\n")
                .append("FORMAT ODPOWIEDZI (tylko JSON, bez dodatkowego tekstu):\n")
                .append("{\n")
                .append("  \"text_elements\": [\n")
                .append("    {\"selector\": \"css.selector.here\", \"text\": \"tekst elementu\", \"type\": \"TEXT\"}\n")
                .append("  ],\n")
                .append("  \"link_elements\": [\n")
                .append("    {\"selector\": \"css.selector.here\", \"text\": \"tekst linku\", \"type\": \"LINK\"}\n")
                .append("  ]\n")
                .append("}");

        return prompt.toString();
    }

    private String escapeText(String text) {
        if (text == null) return "";
        return text.replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ")
                .trim();
    }

    private String callGeminiAPI(String prompt) throws IOException {
        JSONObject requestBody = new JSONObject();
        JSONArray contents = new JSONArray();
        JSONObject content = new JSONObject();
        JSONArray parts = new JSONArray();
        JSONObject part = new JSONObject();

        part.put("text", prompt);
        parts.put(part);
        content.put("parts", parts);
        contents.put(content);

        requestBody.put("contents", contents);

        JSONObject generationConfig = new JSONObject();
        generationConfig.put("temperature", temperature);
        generationConfig.put("maxOutputTokens", maxOutputTokens);
        generationConfig.put("topP", 0.8);
        generationConfig.put("topK", 40);
        requestBody.put("generationConfig", generationConfig);

        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new AIResponseException("Gemini API key is not set");
        }

        String url = API_URL + model + ":generateContent?key=" + apiKey;
        logger.debug("Calling Gemini API URL: {}", url.replace(apiKey, "***"));

        RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No response body";
                setLastParsingError(errorBody);
                throw new AIResponseException("Gemini API error: " + response.code() + " - " + response.message() + ". Body: " + errorBody);
            }

            assert response.body() != null;
            String responseBody = response.body().string();
            logger.debug("Raw Gemini response: {}", responseBody);

            JSONObject jsonResponse = new JSONObject(responseBody);

            if (!jsonResponse.has("candidates") || jsonResponse.getJSONArray("candidates").isEmpty()) {
                throw new AIResponseException("No candidates in Gemini response");
            }

            JSONObject candidate = jsonResponse.getJSONArray("candidates").getJSONObject(0);

            if (!candidate.has("content")) {
                throw new AIResponseException("No content in Gemini candidate");
            }

            JSONObject contentObj = candidate.getJSONObject("content");

            if (!contentObj.has("parts") || contentObj.getJSONArray("parts").isEmpty()) {
                throw new AIResponseException("No parts in Gemini content");
            }

            return contentObj.getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text");
        }
    }

    private FilteredElements parseGeminiResponse(String response, List<ScrapingService.WebElementData> allElements) {
        try {
            logger.debug("Parsing Gemini response, length: {}", response.length());

            String cleanResponse = cleanJsonResponse(response);

            if (cleanResponse.isEmpty()) {
                logger.warn("Empty response after cleaning");
                return new FilteredElements(List.of(), List.of());
            }

            logger.debug("Cleaned response: {}", cleanResponse);

            try {
                ObjectMapper mapper = new ObjectMapper();
                mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

                GeminiResponse result = mapper.readValue(cleanResponse, GeminiResponse.class);

                List<ScrapingService.WebElementData> textElements =
                        mapToRealElements(result.text_elements, allElements);
                List<ScrapingService.WebElementData> linkElements =
                        mapToRealElements(result.link_elements, allElements);

                logger.info("Gemini directly parsed: {} text elements and {} link elements",
                        textElements.size(), linkElements.size());

                return new FilteredElements(textElements, linkElements);

            } catch (Exception directError) {
                logger.debug("Direct parsing failed: {}, attempting validation...", directError.getMessage());

                if (!isValidJson(cleanResponse)) {
                    logger.warn("JSON validation failed, attempting to fix...");
                    String fixedResponse = fixJsonResponse(cleanResponse);

                    if (isValidJson(fixedResponse)) {
                        ObjectMapper mapper = new ObjectMapper();
                        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

                        GeminiResponse result = mapper.readValue(fixedResponse, GeminiResponse.class);

                        List<ScrapingService.WebElementData> textElements =
                                mapToRealElements(result.text_elements, allElements);
                        List<ScrapingService.WebElementData> linkElements =
                                mapToRealElements(result.link_elements, allElements);

                        logger.info("Gemini fixed parsing: {} text elements and {} link elements",
                                textElements.size(), linkElements.size());

                        return new FilteredElements(textElements, linkElements);
                    } else {
                        logger.warn("Could not fix JSON, using partial parsing");
                        return parsePartialGeminiResponse(response, allElements);
                    }
                } else {
                    logger.debug("JSON is valid but direct parse failed, retrying with different settings");
                    return parsePartialGeminiResponse(response, allElements);
                }
            }

        } catch (Exception e) {
            logger.error("Error parsing Gemini response: {}", e.getMessage());
            logger.debug("Failed response content: {}", response);

            return parsePartialGeminiResponse(response, allElements);
        }
    }

    private String cleanJsonResponse(String response) {
        if (response == null) return "";

        String cleaned = response.trim();

        cleaned = cleaned.replaceAll("```json", "")
                .replaceAll("```", "")
                .trim();

        int firstBrace = cleaned.indexOf('{');
        int lastBrace = cleaned.lastIndexOf('}');

        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            cleaned = cleaned.substring(firstBrace, lastBrace + 1);
        }

        cleaned = cleaned.replaceAll("\\s+", " ");

        return cleaned;
    }


    private String fixBrokenJsonResponse(String brokenJson) {
        if (brokenJson == null || brokenJson.trim().isEmpty()) {
            return "{\"text_elements\": [], \"link_elements\": []}";
        }

        String fixed = brokenJson.trim();

        if (fixed.equals("}") || fixed.equals("{}")) {
            return "{\"text_elements\": [], \"link_elements\": []}";
        }

        if (!fixed.contains("\"text_elements\"")) {
            if (fixed.contains("{")) {
                fixed = fixed.replaceFirst("\\{", "{\"text_elements\": [");
            } else {
                fixed = "{\"text_elements\": [" + fixed;
            }
        }

        if (!fixed.contains("\"link_elements\"")) {
            if (fixed.endsWith("}")) {
                fixed = fixed.substring(0, fixed.length() - 1) + ", \"link_elements\": []}";
            } else {
                fixed += ", \"link_elements\": []}";
            }
        }

        fixed = fixIncompleteArrays(fixed);

        if (!fixed.endsWith("}")) {
            fixed += "}";
        }

        logger.debug("Fixed JSON: {}", fixed);
        return fixed;
    }

    private String fixIncompleteArrays(String json) {
        if (json.contains("\"text_elements\"") && !json.contains("\"text_elements\"")) {
            int textStart = json.indexOf("\"text_elements\"");
            int bracketStart = json.indexOf("[", textStart);

            if (bracketStart != -1) {
                int bracketEnd = findMatchingBracket(json, bracketStart);
                if (bracketEnd == -1) {
                    int lastComma = json.lastIndexOf(",");
                    if (lastComma > bracketStart) {
                        json = json.substring(0, lastComma) + "]";
                    } else {
                        json = json.substring(0, bracketStart + 1) + "]";
                    }
                }
            } else {
                json = json.replaceFirst("\"text_elements\"", "\"text_elements\": []");
            }
        }

        if (json.contains("\"link_elements\"") && !json.contains("\"link_elements\"")) {
            int linkStart = json.indexOf("\"link_elements\"");
            int bracketStart = json.indexOf("[", linkStart);

            if (bracketStart != -1) {
                int bracketEnd = findMatchingBracket(json, bracketStart);
                if (bracketEnd == -1) {
                    int lastComma = json.lastIndexOf(",");
                    if (lastComma > bracketStart) {
                        json = json.substring(0, lastComma) + "]";
                    } else {
                        json = json.substring(0, bracketStart + 1) + "]";
                    }
                }
            } else {
                json = json.replaceFirst("\"link_elements\"", "\"link_elements\": []");
            }
        }

        return json;
    }

    private int findMatchingBracket(String str, int startIndex) {
        int count = 1;
        for (int i = startIndex + 1; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '[') count++;
            else if (c == ']') count--;

            if (count == 0) return i;
        }
        return -1;
    }

    private FilteredElements parsePartialGeminiResponse(String response, List<ScrapingService.WebElementData> allElements) {
        try {
            logger.debug("Attempting partial parsing of response");

            List<ScrapingService.WebElementData> textElements = new ArrayList<>();
            List<ScrapingService.WebElementData> linkElements = new ArrayList<>();

            extractSimpleElements(response, textElements, linkElements, allElements);


            if (textElements.isEmpty() && linkElements.isEmpty()) {
                extractAnyElements(response, textElements, linkElements, allElements);
            }

            logger.info("Partial parsing found {} text elements and {} link elements",
                    textElements.size(), linkElements.size());

            return new FilteredElements(textElements, linkElements);

        } catch (Exception e) {
            logger.error("Error in partial parsing: {}", e.getMessage());
            return new FilteredElements(List.of(), List.of());
        }
    }

    private void extractSimpleElements(String response,
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

                    logger.debug("Found element - selector: {}, text: {}, type: {}", selector, text, type);

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
                                    logger.debug("Unknown type: {}, adding to both lists", type);
                                    textElements.add(element);
                                    linkElements.add(element);
                                }
                            });

                } catch (Exception e) {
                    logger.debug("Failed to parse element from regex match");
                }
            }
        } catch (Exception e) {
            logger.debug("Error in simple element extraction: {}", e.getMessage());
        }
    }

    private void extractAnyElements(String response,
                                    List<ScrapingService.WebElementData> textElements,
                                    List<ScrapingService.WebElementData> linkElements,
                                    List<ScrapingService.WebElementData> allElements) {
        try {
            String[] lines = response.split("\n");
            for (String line : lines) {
                if (line.contains("\"selector\"") && line.contains("\"text\"")) {
                    Pattern selectorPattern = Pattern.compile("\"selector\"\\s*:\\s*\"([^\"]*)\"");
                    Pattern textPattern = Pattern.compile("\"text\"\\s*:\\s*\"([^\"]*)\"");

                    Matcher selectorMatcher = selectorPattern.matcher(line);
                    Matcher textMatcher = textPattern.matcher(line);

                    if (selectorMatcher.find() && textMatcher.find()) {
                        String selector = selectorMatcher.group(1);

                        allElements.stream()
                                .filter(real -> real.getSelector().equals(selector))
                                .findFirst()
                                .ifPresent(element -> {
                                    if (element.getType() == ScrapingService.ElementType.TEXT) {
                                        textElements.add(element);
                                    } else {
                                        linkElements.add(element);
                                    }
                                });
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Error in any element extraction: {}", e.getMessage());
        }
    }

    private boolean isValidJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }

        String trimmed = json.trim();

        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return false;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            mapper.readTree(json);
            return true;
        } catch (Exception e) {
            logger.debug("JSON validation failed: {}", e.getMessage());
            return false;
        }
    }

    private List<ScrapingService.WebElementData> mapToRealElements(
            List<ScrapingService.WebElementData> aiElements,
            List<ScrapingService.WebElementData> realElements) {

        List<ScrapingService.WebElementData> mapped = new ArrayList<>();
        if (aiElements == null) {
            return mapped;
        }

        for (ScrapingService.WebElementData aiElement : aiElements) {
            realElements.stream()
                    .filter(real -> real.getSelector().equals(aiElement.getSelector()))
                    .findFirst()
                    .ifPresent(mapped::add);
        }
        return mapped;
    }

    private String fixJsonResponse(String brokenJson) {
        StringBuilder fixed = new StringBuilder(brokenJson);

        if (brokenJson.contains("\"text_elements\"") && !brokenJson.contains("]")) {
            int lastComma = brokenJson.lastIndexOf(",");
            if (lastComma != -1) {
                fixed = new StringBuilder(brokenJson.substring(0, lastComma));
            }
            fixed.append("]");
        }

        if (brokenJson.contains("\"link_elements\"") && !brokenJson.contains("]")) {
            if (!fixed.toString().endsWith("]")) {
                fixed.append("]");
            }
        }

        if (!fixed.toString().endsWith("}")) {
            fixed.append("}");
        }

        if (fixed.toString().contains("\"text_elements\"") && !fixed.toString().contains("\"link_elements\"")) {
            String withLinks = fixed.toString().replaceFirst("\\s*\\}$",
                    ", \"link_elements\": [] }");
            fixed = new StringBuilder(withLinks);
        }

        logger.debug("Fixed JSON: {}", fixed);
        return fixed.toString();
    }

    @Override
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
        logger.debug("Gemini API key set");
    }

    @Override
    public void setModel(String model) {
        if (model.equals("gemini-pro")) {
            this.model = "gemini-1.0-pro-latest";
        } else if (model.equals("gemini-1.5-pro")) {
            this.model = "gemini-1.5-pro-latest";
        } else {
            this.model = model;
        }
        logger.debug("Gemini model set to: {}", this.model);
    }

    @Override
    public String getModel() {
        return model;
    }

    @Override
    public double getCostEstimation(int inputTokens, int outputTokens) {
        return (inputTokens * 0.0035 + outputTokens * 0.0105) / 1000;
    }

    @Override
    public long getLastResponseTime() {
        return lastResponseTime;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class GeminiResponse {
        public List<ScrapingService.WebElementData> text_elements = new ArrayList<>();
        public List<ScrapingService.WebElementData> link_elements = new ArrayList<>();
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