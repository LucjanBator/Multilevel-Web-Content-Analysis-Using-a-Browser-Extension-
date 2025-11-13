package extension.exceptions;

public class OpenAIParsingException extends OpenAIResponseException {
    public OpenAIParsingException(String responseBody, Throwable cause) {
        super("Failed to parse OpenAI response", responseBody, cause);
    }
}