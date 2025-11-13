package extension.exceptions;

public class ConfigFileNotFoundException extends RuntimeException {
    public ConfigFileNotFoundException(String fileName, String location) {
        super(String.format("Configuration file '%s' not found in: %s", fileName, location));
    }
}