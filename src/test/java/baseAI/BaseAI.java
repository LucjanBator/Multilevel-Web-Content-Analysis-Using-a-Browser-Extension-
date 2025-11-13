package baseAI;

import extension.core.config.AppConfig;
import extension.service.AIClient;
import extension.service.GeminiClient;
import extension.service.LlamaClient;
import extension.service.OpenAIClient;
import org.junit.jupiter.api.BeforeAll;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.util.HashMap;
import java.util.Map;

public abstract class BaseAI {

    protected static Map<String, AIClient> clients;

    @BeforeAll
    static void setUpAllClients() {
        initializeAIClients();
    }

    private static void initializeAIClients() {
        clients = new HashMap<>();

        initializeOpenAIClients();

        initializeGeminiClients();

        initializeLlamaClients();
    }

    private static void initializeOpenAIClients() {
        try {
            String openAIApiKey = AppConfig.getOpenAIApiKey();
            if (!openAIApiKey.trim().isEmpty()) {
                // GPT-4o
                OpenAIClient gpt4 = new OpenAIClient();
                gpt4.setModel("gpt-4o");
                gpt4.setApiKey(openAIApiKey);
                clients.put("GPT-4o", gpt4);

                // GPT-3.5
                OpenAIClient gpt35 = new OpenAIClient();
                gpt35.setModel("gpt-3.5-turbo");
                gpt35.setApiKey(openAIApiKey);
                clients.put("GPT-3.5", gpt35);

            } else {
                System.out.println("OpenAI API key not found, skipping OpenAI clients");
            }
        } catch (Exception e) {
            System.out.println("Error initializing OpenAI clients: " + e.getMessage());
        }
    }

    private static void initializeGeminiClients() {
        try {
            String geminiApiKey = AppConfig.getGeminiApiKey();
            if (!geminiApiKey.trim().isEmpty()) {
                GeminiClient gemini = new GeminiClient();
                gemini.setModel("gemini-2.5-flash");
                gemini.setApiKey(geminiApiKey);
                clients.put("Gemini", gemini);
            } else {
                System.out.println("Gemini API key not found, skipping Gemini client");
            }
        } catch (Exception e) {
            System.out.println("Error initializing Gemini client: " + e.getMessage());
        }
    }

    private static void initializeLlamaClients() {
        try {
            String llamaApiKey = AppConfig.getLlamaApiKey();
            if (!llamaApiKey.trim().isEmpty()) {
                LlamaClient llama = new LlamaClient();
                llama.setModel("llama-3.3-70b-versatile");
                llama.setApiKey(llamaApiKey);
                clients.put("Llama-3", llama);
            } else {
                System.out.println("Llama API key not found, skipping Llama client");

            }
        } catch (Exception e) {
            System.out.println("Error initializing Llama client: " + e.getMessage());
        }
    }

    protected WebDriver createNewDriver() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--start-maximized");
        options.addArguments("--disable-extensions");
        options.addArguments("--window-size=1920,1080");

        return new ChromeDriver(options);
    }

    protected Map<String, AIClient> getAllClients() {
        return new HashMap<>(clients);
    }
}
