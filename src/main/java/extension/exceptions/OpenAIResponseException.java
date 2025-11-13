package extension.exceptions;

public class OpenAIResponseException extends RuntimeException {
    public OpenAIResponseException(String message) {
        super(message);
    }

    public OpenAIResponseException(String message, Throwable cause) {
        super(message, cause);
    }

    public OpenAIResponseException(String operation, String details, Throwable cause) {
        super(String.format("OpenAI %s error: %s", operation, details), cause);
    }

    public OpenAIResponseException(String apiRequestFailed, String format) {
        super(String.format("OpenAI %s error: %s", apiRequestFailed, format));
    }
}