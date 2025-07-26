package extension.core.config;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

public class AppConfig {
    private static final Logger logger = LogManager.getLogger(AppConfig.class);
    private static Configuration config;

    static {
        try {
            config = new Configurations().properties(new File("src/main/resources/config.properties"));
        } catch (ConfigurationException e) {
            logger.error("Error loading configuration", e);
            throw new RuntimeException("Failed to load configuration", e);
        }
    }

    public static String getDefaultUrl() {
        return config.getString("default.url");
    }

    public static int getTimeoutSeconds() {
        return config.getInt("timeout.seconds");
    }

    public static String getChromeDriverPath(String os) {
        if (os.contains("win")) {
            return config.getString("driver.chrome.win");
        } else if (os.contains("mac")) {
            return config.getString("driver.chrome.mac");
        } else {
            return config.getString("driver.chrome.linux");
        }
    }
}