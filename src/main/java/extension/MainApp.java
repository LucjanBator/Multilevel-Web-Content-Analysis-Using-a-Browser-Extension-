package extension;

import extension.core.ExtensionRunner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MainApp {
    private static final Logger logger = LogManager.getLogger(MainApp.class);

    public static void main(String[] args) {
        try {
            logger.info("Starting Selenium Browser Extension");
            ExtensionRunner extensionRunner = new ExtensionRunner();
            extensionRunner.start();
        } catch (Exception e) {
            logger.error("Error in main application", e);
        }
    }
}