package extension.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import extension.exceptions.AIResponseException;
import okhttp3.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LlamaClient implements AIClient {
    private static final Logger logger = LogManager.getLogger(LlamaClient.class);
    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";

    private String model = "llama2-70b-4096";
    private double temperature = 0.1;
    private int maxTokens = 3000;
    private final OkHttpClient client;
    private String apiKey;
    private long lastResponseTime;

    private String userContext;

    public LlamaClient() {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    private FilteredElements parseLlamaResponse(String response, List<ScrapingService.WebElementData> allElements) {
        try {
            logger.debug("Parsing Llama response, length: {}", response.length());
            logger.debug("Raw response: {}", response);

            String cleanResponse = cleanJsonResponse(response);

            if (cleanResponse.isEmpty()) {
                logger.warn("Empty response after cleaning");
                return new FilteredElements(List.of(), List.of());
            }

            if (!isValidJson(cleanResponse)) {
                logger.warn("Invalid JSON response, attempting to fix...");
                cleanResponse = fixJsonResponse(cleanResponse);
            }

            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

            LlamaResponse result = mapper.readValue(cleanResponse, LlamaResponse.class);

            List<ScrapingService.WebElementData> textElements =
                    mapToRealElements(result.text_elements, allElements);
            List<ScrapingService.WebElementData> linkElements =
                    mapToRealElements(result.link_elements, allElements);

            logger.info("Llama found {} text elements and {} link elements",
                    textElements.size(), linkElements.size());

            return new FilteredElements(textElements, linkElements);

        } catch (Exception e) {
            logger.error("Error parsing Llama response: {}", e.getMessage());
            logger.debug("Failed response content: {}", response);
            return parsePartialLlamaResponse(response, allElements);
        }
    }

    private String cleanJsonResponse(String response) {
        if (response == null) return "";

        String cleaned = response.replaceAll("```json", "")
                .replaceAll("```", "")
                .trim();

        int firstBrace = cleaned.indexOf('{');
        int lastBrace = cleaned.lastIndexOf('}');

        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            cleaned = cleaned.substring(firstBrace, lastBrace + 1);
        }

        return cleaned;
    }

    private boolean isValidJson(String json) {
        try {
            new JSONObject(json);
            return true;
        } catch (JSONException e) {
            try {
                new JSONArray(json);
                return true;
            } catch (JSONException e2) {
                return false;
            }
        }
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

    private FilteredElements parsePartialLlamaResponse(String response, List<ScrapingService.WebElementData> allElements) {
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

    @Override
    public FilteredElements filterElements(List<ScrapingService.WebElementData> elements, String userQuery) {
        if (userQuery == null || userQuery.trim().isEmpty()) {
            logger.warn("User query is empty, returning all elements");
            return new FilteredElements(List.of(), List.of());
        }
        long startTime = System.currentTimeMillis();
        try {
            String prompt = buildLlamaPrompt(elements, userQuery);
            String response = callLlamaAPI(prompt);
            FilteredElements result = parseLlamaResponse(response, elements);

            this.lastResponseTime = System.currentTimeMillis() - startTime;
            return result;

        } catch (Exception e) {
            logger.error("Error filtering elements with Llama API for model {}: {}", model, e.getMessage());
            this.lastResponseTime = System.currentTimeMillis() - startTime;
            return new FilteredElements(List.of(), List.of());
        }
    }

    private String buildLlamaPrompt(List<ScrapingService.WebElementData> elements, String userQuery) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("<s>[INST] <<SYS>>\n")
                .append("Jesteś asystentem AI analizującym strony internetowe. ")
                .append("Zwróć TYLKO JSON bez żadnego dodatkowego tekstu.\n")
                .append("<</SYS>>\n\n")
                .append("Pytanie: ")
                .append(userQuery)
                .append("\n\nElementy strony:\n");

        int maxElements = Math.min(elements.size(), 150);
        for (int i = 0; i < maxElements; i++) {
            ScrapingService.WebElementData element = elements.get(i);
            prompt.append("- ")
                    .append(element.getType())
                    .append(": ")
                    .append(element.getText())
                    .append(" [")
                    .append(element.getSelector())
                    .append("]\n");
        }

        prompt.append("\nFormat odpowiedzi:\n")
                .append("{\n")
                .append("  \"text_elements\": [\n")
                .append("    {\"selector\": \"...\", \"text\": \"...\", \"type\": \"TEXT\"}\n")
                .append("  ],\n")
                .append("  \"link_elements\": [\n")
                .append("    {\"selector\": \"...\", \"text\": \"...\", \"type\": \"LINK\"}\n")
                .append("  ]\n")
                .append("}\n")
                .append("[/INST]");

        return prompt.toString();
    }

    private String callLlamaAPI(String prompt) throws IOException {
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", model);

        JSONArray messages = new JSONArray();
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", prompt);
        messages.put(message);

        requestBody.put("messages", messages);
        requestBody.put("temperature", temperature);
        requestBody.put("max_tokens", maxTokens);
        requestBody.put("stream", false);

        JSONObject responseFormat = new JSONObject();
        responseFormat.put("type", "json_object");
        requestBody.put("response_format", responseFormat);

        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new AIResponseException("Llama API key is not set");
        }

        String url = API_URL;
        logger.debug("Calling Llama API URL: {}", url);
        logger.debug("Using model: {}", model);

        RequestBody body = RequestBody.create(
                requestBody.toString(),
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No response body";
                setLastParsingError(errorBody);
                throw new AIResponseException("Llama API error: " + response.code() + " - " + response.message() + ". Body: " + errorBody);
            }

            assert response.body() != null;
            String responseBody = response.body().string();

            logger.debug("Full Llama API response: {}", responseBody);

            if (responseBody.trim().isEmpty()) {
                throw new AIResponseException("Empty response from Llama API");
            }

            JSONObject jsonResponse;
            try {
                jsonResponse = new JSONObject(responseBody);
            } catch (JSONException e) {
                logger.error("Invalid JSON response from Llama API: {}", responseBody);
                throw new AIResponseException("Invalid JSON response from Llama API");
            }

            if (!jsonResponse.has("choices") || jsonResponse.getJSONArray("choices").length() == 0) {
                logger.error("No choices in Llama response. Full response: {}", responseBody);
                throw new AIResponseException("No choices in Llama response");
            }

            JSONObject choice = jsonResponse.getJSONArray("choices").getJSONObject(0);

            if (!choice.has("message")) {
                logger.error("No message in Llama choice. Choice object: {}", choice.toString());
                throw new AIResponseException("No message in Llama choice");
            }

            JSONObject messageObj = choice.getJSONObject("message");

            if (!messageObj.has("content")) {
                logger.error("No content in Llama message. Message object: {}", messageObj.toString());
                throw new AIResponseException("No content in Llama message");
            }

            String content = messageObj.getString("content").trim();

            if (content.isEmpty()) {
                logger.error("Empty content in Llama response");
                throw new AIResponseException("Empty content in Llama response");
            }

            logger.debug("Llama response content length: {}", content.length());
            logger.debug("Llama response content preview: {}",
                    content.length() > 100 ? content.substring(0, 100) + "..." : content);

            return content;

        } catch (JSONException e) {
            logger.error("JSON parsing error in Llama API response: {}", e.getMessage());
            throw new AIResponseException("JSON parsing error in Llama API response: " + e.getMessage());
        }
    }

    private List<ScrapingService.WebElementData> mapToRealElements(
            List<ScrapingService.WebElementData> aiElements,
            List<ScrapingService.WebElementData> realElements) {

        List<ScrapingService.WebElementData> mapped = new ArrayList<>();
        for (ScrapingService.WebElementData aiElement : aiElements) {
            realElements.stream()
                    .filter(real -> real.getSelector().equals(aiElement.getSelector()))
                    .findFirst()
                    .ifPresent(mapped::add);
        }
        return mapped;
    }

    @Override
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public void setModel(String model) {
        this.model = model;
    }

    @Override
    public String getModel() {
        return model;
    }

    @Override
    public double getCostEstimation(int inputTokens, int outputTokens) {
        return switch (model) {
            case "llama2-70b-4096" -> (inputTokens * 0.0007 + outputTokens * 0.0008) / 1000;
            case "mixtral-8x7b-32768" -> (inputTokens * 0.00027 + outputTokens * 0.00027) / 1000;
            default -> (inputTokens * 0.0005 + outputTokens * 0.0005) / 1000;
        };
    }

    @Override
    public long getLastResponseTime() {
        return lastResponseTime;
    }

    public void setUserContext(String userContext) {
        this.userContext = userContext;
    }

    public String getUserContext() {
        return userContext;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class LlamaResponse {
        public List<ScrapingService.WebElementData> text_elements;
        public List<ScrapingService.WebElementData> link_elements;
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