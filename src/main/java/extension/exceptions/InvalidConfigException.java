package extension.exceptions;

public class InvalidConfigException extends RuntimeException {

    public InvalidConfigException(String key, String expectedFormat) {
        super(String.format("Invalid configuration for '%s'. Expected: %s", key, expectedFormat));
    }
}