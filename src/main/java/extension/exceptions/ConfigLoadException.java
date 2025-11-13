package extension.exceptions;

public class ConfigLoadException extends RuntimeException {
    public ConfigLoadException(String fileName, Throwable cause) {
        super(String.format("Error loading configuration file: %s", fileName), cause);
    }
}