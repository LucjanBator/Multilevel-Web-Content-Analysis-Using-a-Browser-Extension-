package extension.exceptions;

public class MissingConfigException extends RuntimeException {

    public MissingConfigException(String key) {
        super(String.format("Required configuration property '%s' is missing", key));
    }

    public MissingConfigException(String key, String configFile) {
        super(String.format("Required configuration property '%s' is missing in %s", key, configFile));
    }
}