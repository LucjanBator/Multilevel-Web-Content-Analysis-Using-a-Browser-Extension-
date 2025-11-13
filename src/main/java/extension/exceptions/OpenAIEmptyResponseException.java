package extension.exceptions;

public class OpenAIEmptyResponseException extends OpenAIResponseException {
    public OpenAIEmptyResponseException(String responseType, String responseBody) {
        super(String.format("Empty %s in OpenAI response. Response: %s", responseType, responseBody));
    }
}