package extension.service;

import extension.core.WebDriverFactory;
import extension.core.config.AppConfig;
import extension.exceptions.*;
import org.openqa.selenium.WebDriver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.List;
import java.util.Scanner;

public class SessionManager {
    private static final Logger logger = LogManager.getLogger(SessionManager.class);
    private WebDriver driver;
    private static final String SNAPSHOTS_DIR = "snapshots";
    private final String apiKey;
    private String userUrl;
    private String userMessage;
    private final Scanner scanner;

    public SessionManager() {
        this.scanner = new Scanner(System.in);
        this.apiKey = AppConfig.getOpenAIApiKey();
    }

    public SessionManager(String userUrl, String userMessage) {
        this.userUrl = userUrl;
        this.userMessage = userMessage;
        this.scanner = new Scanner(System.in);
        try {
            this.apiKey = AppConfig.getOpenAIApiKey();
        } catch (MissingConfigException | InvalidConfigException e) {
            logger.error("Configuration error: {}", e.getMessage());
            logger.info("{}", e.getMessage());
            throw e;
        }
    }

    public void start() {
        try {
            driver = WebDriverFactory.createDriver();
            ScrapingService scrapingService = new ScrapingService(driver);

            scrapingService.setUserContext(userMessage);
            scrapingService.setApiKey(apiKey);

            runInteractiveFlow(scrapingService, userUrl);

        } catch (ConfigFileNotFoundException e) {
            logger.error("Configuration file problem: {}", e.getMessage());
            logger.info("Problem with configuration: {}", e.getMessage());
            logger.info("Please ensure config.properties exists in src/main/resources/");
        } catch (ConfigLoadException e) {
            logger.error("Error loading configuration: {}", e.getMessage());
            logger.info("Error loading configuration: {}", e.getMessage());
        } catch (Exception e) {
            logger.error("Error in extension runner", e);
        } finally {
            if (driver != null) {
                driver.quit();
            }
            if (scanner != null) {
                scanner.close();
            }
        }
    }

    private void runInteractiveFlow(ScrapingService scrapingService, String startUrl) {
        String currentUrl = startUrl;
        boolean continueSession = true;

        while (continueSession && !Thread.currentThread().isInterrupted()) {
            try {
                FilteredElements currentFilteredElements = processCurrentPage(scrapingService, currentUrl);

                String newUrl = handleUserSession(scrapingService, currentFilteredElements, currentUrl);

                if (newUrl != null) {
                    currentUrl = newUrl;
                } else {
                    continueSession = false;
                }

            } catch (ScrapingInterruptedException e) {
                logger.error("Session interrupted: {}", e.getMessage());
                logger.info("Sesja została przerwana.");
                continueSession = false;
            } catch (Exception e) {
                continueSession = handleProcessingError(e, startUrl);
            }
        }

        logger.info("\nDziękujemy za skorzystanie z aplikacji!");
    }

    private String handleLinkFollowing(ScrapingService scrapingService, List<ScrapingService.WebElementData> linkElements) {
        try {
            scrapingService.removeHighlights();
        } catch (Exception e) {
            logger.warn("Could not remove previous highlights", e);
        }

        String selectedLinkUrl = selectLinkToFollow(linkElements);
        if (selectedLinkUrl != null) {
            logger.info("Przechodzę do nowej strony...");
            return selectedLinkUrl;
        }
        return null;
    }

    private String handleUserSession(ScrapingService scrapingService,
                                     FilteredElements filteredElements,
                                     String currentUrl) {
        if (!filteredElements.linkElements().isEmpty() && askToContinueWithLinks()) {
            String newUrl = handleLinkFollowing(scrapingService, filteredElements.linkElements());
            if (newUrl != null) {
                return newUrl;
            }
        }

        if (askToSearchAgain()) {
            startNewSearchOnCurrentPage(scrapingService);
            return currentUrl;
        }

        return null;
    }

    private FilteredElements processCurrentPage(ScrapingService scrapingService, String url) throws IOException {
        logger.info("\nPrzechodzę na stronę: {}", url);
        scrapingService.navigateTo(url);

        scrapingService.injectHighlightStyles();

        String snapshot = scrapingService.capturePageSnapshot();
        String snapshotPath = ContentSaver.saveSnapshot(snapshot, SNAPSHOTS_DIR);
        logger.info("Page snapshot saved to: {}", snapshotPath);

        FilteredElements filteredElements = scrapingService.extractAndFilterContent();

        scrapingService.highlightElementsOnPage(filteredElements);
        ContentSaver.saveFilteredResults(filteredElements);
        displayResultsToUser(filteredElements);

        return filteredElements;
    }

    private void displayResultsToUser(FilteredElements filteredElements) {
        logger.info("\n=== WYNIKI WYSZUKIWANIA ===");
        logger.info("Pytanie: {}", userMessage);
        logger.info("\nBEZPOŚREDNIE ODPOWIEDZI:");

        if (filteredElements.textElements().isEmpty()) {
            logger.info("Brak bezpośrednich odpowiedzi na stronie.");
        } else {
            filteredElements.textElements().forEach(element -> {
                logger.info("\n{}", element.getText());
                logger.info("\nSelektor: {}", element.getSelector());
            });
        }

        logger.info("\nLINKI DO DALSZYCH INFORMACJI:");
        if (filteredElements.linkElements().isEmpty()) {
            logger.info("Brak sugerowanych linków.");
        } else {
            filteredElements.linkElements().forEach(element -> {
                logger.info("\n{}", element.getText());
                logger.info("\nSelektor: {}", element.getSelector());
            });
        }
    }

    private boolean handleProcessingError(Exception e, String currentUrl) {
        logger.error("Error processing page: {}", currentUrl, e);
        logger.info("Wystąpił błąd podczas przetwarzania strony: {}", currentUrl);
        return askToRetry();
    }

    private void startNewSearchOnCurrentPage(ScrapingService scrapingService) {
        String newUserMessage = askForNewQuery();
        scrapingService.setUserContext(newUserMessage);
        logger.info("Rozpoczynam nowe wyszukiwanie: {}", newUserMessage);
    }

    private boolean askToContinueWithLinks() {
        logger.info("\n---");
        logger.info("Czy chcesz kontynuować poszukiwania, klikając w jeden z linków? (t/n): ");
        String response = scanner.nextLine().trim().toLowerCase();
        return response.equals("t") || response.equals("tak") || response.equals("y") || response.equals("yes");
    }

    private String selectLinkToFollow(List<ScrapingService.WebElementData> linkElements) {
        logger.info("\nDostępne linki:");
        logger.info("====================");

        for (int i = 0; i < linkElements.size(); i++) {
            ScrapingService.WebElementData link = linkElements.get(i);
            logger.info("{}. {}", i + 1, link.getText());
            logger.info("Selektor:\n{}", link.getSelector());
        }

        logger.info("Wybierz numer linku (1-{}) lub '0' aby anulować: ", linkElements.size());

        try {
            int choice = Integer.parseInt(scanner.nextLine().trim());

            if (choice == 0) {
                logger.info("Anulowano wybór linku.");
                return null;
            }

            if (choice >= 1 && choice <= linkElements.size()) {
                ScrapingService.WebElementData selectedLink = linkElements.get(choice - 1);
                logger.info("Klikam w link: {}", selectedLink.getText());

                return clickLinkBySelector(selectedLink.getSelector());
            } else {
                logger.info("Nieprawidłowy wybór. Spróbuj ponownie.");
                return selectLinkToFollow(linkElements);
            }
        } catch (NumberFormatException e) {
            logger.info("Nieprawidłowy format. Wprowadź numer.");
            return selectLinkToFollow(linkElements);
        }
    }

    private String clickLinkBySelector(String selector) {
        try {
            String script = String.format(
                    "var element = document.querySelector('%s'); " +
                            "if (element && element.href) { " +
                            "   element.click(); " +
                            "   return element.href; " +
                            "} else { " +
                            "   return null; " +
                            "}", selector);

            String newUrl = (String) ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(script);

            if (newUrl != null) {
                logger.info("Pomyślnie kliknięto link. Nowy URL: {}", newUrl);
                waitForPageLoad();
                String currentUrl = driver.getCurrentUrl();
                logger.info("Aktualny URL po kliknięciu: {}", currentUrl);

                return currentUrl;
            } else {
                logger.info("Nie udało się kliknąć linku lub link nie ma URL.");
                return null;
            }
        } catch (Exception e) {
            logger.error("Error clicking link: {}", selector, e);
            logger.info("Błąd podczas klikania linku: {}", e.getMessage());
            return null;
        }
    }

    private void waitForPageLoad() {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ScrapingInterruptedException("Operacja wczytywania strony została przerwana", e);
        }
    }

    private boolean askToSearchAgain() {
        logger.info("\n---");
        logger.info("Czy chcesz wykonać nowe wyszukiwanie na tej stronie? (t/n): ");
        String response = scanner.nextLine().trim().toLowerCase();
        return response.equals("t") || response.equals("tak") || response.equals("y") || response.equals("yes");
    }

    private String askForNewQuery() {
        logger.info("\nPodaj nowe pytanie/wyszukiwanie: ");
        return scanner.nextLine().trim();
    }

    private boolean askToRetry() {
        logger.info("Czy chcesz spróbować ponownie? (t/n): ");
        String response = scanner.nextLine().trim().toLowerCase();
        return response.equals("t") || response.equals("tak") || response.equals("y") || response.equals("yes");
    }
}