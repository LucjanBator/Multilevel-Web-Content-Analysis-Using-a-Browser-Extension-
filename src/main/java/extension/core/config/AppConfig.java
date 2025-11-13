package extension.core.config;

import extension.exceptions.ConfigFileNotFoundException;
import extension.exceptions.ConfigLoadException;
import extension.exceptions.InvalidConfigException;
import extension.exceptions.MissingConfigException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class AppConfig {

    private static final Logger logger = LogManager.getLogger(AppConfig.class);
    private static final String CONFIG_FILE = "config.properties";
    private static final Properties properties;

    static {
        properties = new Properties();
        loadProperties();
        validateRequiredProperties();
    }

    private static void loadProperties() {
        try (InputStream input = AppConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                throw new ConfigFileNotFoundException(
                        CONFIG_FILE,
                        "classpath. Please ensure the file exists in src/main/resources/"
                );
            }
            properties.load(input);
            logger.info("Configuration loaded successfully from: {}", CONFIG_FILE);

        } catch (IOException e) {
            throw new ConfigLoadException(CONFIG_FILE, e);
        }
    }

    private static void validateRequiredProperties() {
        validateApiKeys();
        validateNumericProperties();
    }

    private static void validateApiKeys() {
        String[] apiKeys = {"openai.api.key", "gemini.api.key", "llama.api.key"};

        for (String key : apiKeys) {
            String apiKey = properties.getProperty(key);
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new MissingConfigException(key, CONFIG_FILE);
            }
        }
    }

    private static void validateNumericProperties() {
        String[] numericProperties = {
                "page.load.timeout",
                "openai.max_tokens", "gemini.max_tokens", "llama.max_tokens",
                "openai.temperature", "gemini.temperature", "llama.temperature"
        };

        for (String property : numericProperties) {
            String value = properties.getProperty(property);
            if (value != null) {
                try {
                    if (property.contains("temperature")) {
                        double temp = Double.parseDouble(value);
                        if (temp < 0.0 || temp > 2.0) {
                            throw new InvalidConfigException(property, "value should be between 0.0 and 2.0");
                        }
                    } else if (property.contains("max_tokens")) {
                        int tokens = Integer.parseInt(value);
                        if (tokens <= 0 || tokens > 100000) {
                            throw new InvalidConfigException(property, "value should be between 1 and 100000");
                        }
                    } else if (property.contains("timeout")) {
                        long timeout = Long.parseLong(value);
                        if (timeout <= 0) {
                            throw new InvalidConfigException(property, "value should be positive");
                        }
                    }
                } catch (NumberFormatException e) {
                    throw new InvalidConfigException(
                            property,
                            property.contains("temperature") ? "decimal number (0.0-2.0)" : "numeric value"
                    );
                }
            }
        }
    }

    public static String getOpenAIApiKey() {
        return getRequiredProperty("openai.api.key");
    }

    public static double getOpenAITemperature() {
        return getTemperature("openai.temperature", 0.1);
    }

    public static int getOpenAIMaxTokens() {
        return getMaxTokens("openai.max_tokens", 3000);
    }

    public static String getOpenAIModel() {
        return properties.getProperty("openai.model", "gpt-3.5-turbo");
    }

    public static String getGeminiApiKey() {
        return getRequiredProperty("gemini.api.key");
    }

    public static String getLlamaApiKey() {
        return getRequiredProperty("llama.api.key");
    }

    private static double getTemperature(String property, double defaultValue) {
        try {
            return Double.parseDouble(properties.getProperty(property, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            logger.warn("Invalid {} value, using default: {}", property, defaultValue);
            return defaultValue;
        }
    }

    private static int getMaxTokens(String property, int defaultValue) {
        try {
            return Integer.parseInt(properties.getProperty(property, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            logger.warn("Invalid {} value, using default: {}", property, defaultValue);
            return defaultValue;
        }
    }

    public static String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    public static String getRequiredProperty(String key) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new MissingConfigException(key, CONFIG_FILE);
        }
        return value.trim();
    }
}