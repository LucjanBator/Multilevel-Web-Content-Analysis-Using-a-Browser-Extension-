package extension.exceptions;

public class OpenAIInvalidResponseException extends OpenAIResponseException {
    public OpenAIInvalidResponseException(String missingField, String responseBody) {
        super(String.format("Invalid OpenAI response - missing '%s' field. Response: %s",
                missingField, responseBody));
    }
}