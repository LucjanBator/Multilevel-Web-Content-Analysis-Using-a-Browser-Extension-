package extension;

import extension.service.SessionManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Scanner;

public class MainApp {
    private static final Logger logger = LogManager.getLogger(MainApp.class);

    private static final String EQUALS = "==========================================";

    public static void main(String[] args) {
        try {
            logger.info("Starting Selenium Browser Extension");
            String userUrl = parseUserUrl(args);
            String userMessage = parseUserMessage(args);
            SessionManager sessionManager;
            if (userMessage != null && !userMessage.isEmpty()) {
                sessionManager = new SessionManager(userUrl, userMessage);
                logger.info("Running with AI filtering for: {}", userMessage);
            } else {
                sessionManager = new SessionManager();
                logger.info("Running in standard mode (no AI filtering)");
            }
            sessionManager.start();
        } catch (Exception e) {
            logger.error("Error in main application", e);
        }
    }

    private static String parseUserUrl(String[] args) {
        if (args.length > 1) {
            return args[0];
        }

        return promptUserForUrl();
    }

    private static String promptUserForUrl() {
        try {
            logger.info(EQUALS);
            logger.info("AI-Powered Web Content Extractor");
            logger.info(EQUALS);
            logger.info("Enter the URL to scrape:");
            logger.info("> ");

            Scanner scanner = new Scanner(System.in);
            String input = scanner.nextLine().trim();

            if (!input.isEmpty()) {
                logger.info("Using URL: {}", input);
                return input;
            }

        } catch (Exception e) {
            logger.warn("Could not read user input: {}", e.getMessage());
        }

        return null;
    }

    private static String parseUserMessage(String[] args) {
        if (args.length > 0) {
            return String.join(" ", args);
        }

        return promptUserForMessage();
    }

    private static String promptUserForMessage() {
        try {
            logger.info(EQUALS);
            logger.info("Enter your search query:");
            logger.info("> ");

            Scanner scanner = new Scanner(System.in);
            String input = scanner.nextLine().trim();

            if (!input.isEmpty()) {
                logger.info("Using AI filtering for: {}", input);
                return input;
            }

        } catch (Exception e) {
            logger.warn("Could not read user input: {}", e.getMessage());
        }

        return null;
    }
}