package performance;

import extension.service.AIClient;
import baseAI.BaseAI;
import extension.service.ScrapingService;
import org.junit.jupiter.api.*;
import org.openqa.selenium.WebDriver;
import utils.CPUMonitor;
import utils.ExcelExporter;

import java.util.*;
import java.util.concurrent.TimeUnit;

@DisplayName("Testy porównawcze modeli językowych")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PerformanceTest extends BaseAI {

    private final CPUMonitor cpuMonitor = new CPUMonitor();

    @BeforeAll
    void setUp() {
        String testUrl = "https://pl.wikipedia.org/wiki/Smartfon";
        String query = "Jaki system operacyjny steruje urządzeniem?";
        for (int i = 0; i < 1; i++) {
            WebDriver warmupDriver = createNewDriver();
            ScrapingService warmupService = new ScrapingService(warmupDriver, clients.values().iterator().next());
            warmupService.setUserContext(query);
            warmupService.navigateTo(testUrl);
            warmupService.extractAndFilterContent();
            testUrlPerformance(clients.values().iterator().next(), testUrl, query, "Warmup");
            warmupDriver.quit();
        }
    }

    @Test
    @DisplayName("Testy wydajnościowe")
    void testPerformanceMetrics() {
        Map<String, String> testCases = Map.of(
                "https://www.melitta.com", "Strona statyczna",
                "https://algeriancoffeestores.com/", "Strona z JavaScript",
                "https://www.intelligentsia.com/", "Strona dynamiczna"
        );

        String query = "W jaki sposób sie skontaktować z firmą produkucjącą kawe?";

        System.out.println("\n=== TESTY WYDAJNOŚCIOWE ===");
        System.out.println("Liczba testowanych stron: " + testCases.size());
        System.out.println("Typy stron: " + String.join(", ", testCases.values()));
        System.out.println();

        Map<String, AIClient> testClients = getAllClients();
        Map<String, List<ExcelExporter.PerformanceStatsResult>> allResults = new HashMap<>();
        Map<String, ExcelExporter.PerformanceStatsResult> averageResults = new HashMap<>();

        for (Map.Entry<String, AIClient> entry : testClients.entrySet()) {
            String modelName = entry.getKey();
            AIClient client = entry.getValue();

            System.out.printf("MODEL: %s%n", modelName);
            List<ExcelExporter.PerformanceStatsResult> modelResults = new ArrayList<>();

            for (Map.Entry<String, String> testCase : testCases.entrySet()) {
                String url = testCase.getKey();
                String pageType = testCase.getValue();

                System.out.printf("%s" +
                        "\n(%s):%n", url, pageType);

                ExcelExporter.PerformanceStatsResult urlStats = testUrlPerformance(client, url, query, pageType);
                modelResults.add(urlStats);

                System.out.printf("      Czas odpowiedzi: %d ms%n", urlStats.responseTime());
                System.out.printf("      Elementy: %d%n", urlStats.elementsProcessed());
                System.out.printf("      Pamięć: %.1f MB%n", urlStats.memoryUsage());
                if (!urlStats.error().isEmpty()) {
                    System.out.printf("      Błędy: %s%n", urlStats.error());
                }
            }

            ExcelExporter.PerformanceStatsResult averageStats = calculateAverages(modelResults);

            allResults.put(modelName, modelResults);
            averageResults.put(modelName, averageStats);

            System.out.print("   ŚREDNIA WYDAJNOŚĆ:\n");
            System.out.printf("      Czas: %d ms" +
                    "\n", averageStats.responseTime());
            System.out.printf("      Elementy: %d" +
                    "\n", averageStats.elementsProcessed());
            System.out.printf("      Pamięć: %.1f MB" +
                    "\n", averageStats.memoryUsage());
            System.out.printf("      Elementy/s: %.1f" +
                            "\n",
                    averageStats.responseTime() > 0 ?
                            (averageStats.elementsProcessed() * 1000.0) / averageStats.responseTime() : 0.0);
            if (!averageStats.error().isEmpty()) {
                System.out.printf("      Błędy: %s" +
                        "\n", averageStats.error());
            }
            System.out.println("   ------------------------------------");
        }

        String fileName = ExcelExporter.exportPerformanceTest(allResults, "test_wydajnosci_statyczna_dynamiczna_js");
        if (fileName != null) {
            System.out.println("Wyniki zapisane do: " + fileName);
        }

        String fileNameAverage = ExcelExporter.exportPerformanceAveragesTest(averageResults, "test_wydajnosci_statyczna_dynamiczna_js_srednie");
        if (fileNameAverage != null) {
            System.out.println("Wyniki średnie zapisane do: " + fileNameAverage);
        }
    }

    @Test
    @DisplayName("Testy skalowalności")
    void testScalabilityCharacteristicsImproved() {
        String testUrl = "https://pl.wikipedia.org/wiki/Smartfon";
        String query = "Jaki system operacyjny steruje urządzeniem?";

        int[] concurrentUsers = {1, 3, 5};

        System.out.println("\n=== TESTY SKALOWALNOŚCI ===");

        Map<String, AIClient> testClients = getAllClients();
        Map<String, Map<Integer, ExcelExporter.ScalabilityResult>> allResults = new HashMap<>();

        for (Map.Entry<String, AIClient> entry : testClients.entrySet()) {
            String modelName = entry.getKey();
            AIClient client = entry.getValue();

            System.out.printf("MODEL: %s%n", modelName);
            Map<Integer, ExcelExporter.ScalabilityResult> scalabilityResults = new HashMap<>();

            for (int users : concurrentUsers) {
                System.out.printf("Liczba użytkowników: %d%n", users);

                List<Double> responseTimes = new ArrayList<>();
                List<Double> memoryUsages = new ArrayList<>();
                List<Integer> elementsProcessed = new ArrayList<>();

                List<String> errors = new ArrayList<>();

                for (int i = 0; i < users; i++) {
                    WebDriver modelDriver = createNewDriver();
                    try {
                        ScrapingService scrapingService = new ScrapingService(modelDriver, client);
                        scrapingService.setUserContext(query);

                        System.gc();
                        Thread.sleep(500);
                        long memoryBefore = getUsedMemory();

                        long startTime = System.nanoTime();

                        scrapingService.navigateTo(testUrl);
                        scrapingService.extractAndFilterContent();

                        long endTime = System.nanoTime();

                        System.gc();
                        Thread.sleep(500);
                        long memoryAfter = getUsedMemory();

                        double responseTime = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
                        double memoryUsage = Math.max(0, (memoryAfter - memoryBefore) / (1024.0));

                        responseTimes.add(responseTime);
                        memoryUsages.add(memoryUsage);
                        elementsProcessed = Collections.singletonList(scrapingService.getLastExtractedElements().size());

                        modelDriver.quit();

                        Thread.sleep(5000);

                    } catch (Exception e) {
                        System.out.printf("      Błąd dla użytkownika %d: %s%n", i, e.getMessage());
                    }
                }

                double avgResponseTime = calculateFilteredAverage(responseTimes);
                double avgMemoryUsage = calculateFilteredAverage(memoryUsages);
                double avgElements = elementsProcessed.stream().mapToInt(Integer::intValue).average().orElse(0.0);
                double throughput = avgResponseTime > 0 ? users * 1000.0 / avgResponseTime : 0.0;

                if (client.hasParsingError()) {
                    errors.add("LLM Parsing Error: " + client.getLastParsingError());
                }

                ExcelExporter.ScalabilityResult scalabilityResult =
                        new ExcelExporter.ScalabilityResult(
                                users,
                                avgResponseTime,
                                avgMemoryUsage,
                                avgElements,
                                throughput,
                                responseTimes.size(),
                                String.join("; ", errors)
                        );

                scalabilityResults.put(users, scalabilityResult);

                System.out.printf("      Średni czas odpowiedzi: %.1f ms%n", avgResponseTime);
                System.out.printf("      Średnie użycie pamięci: %.1f MB%n", avgMemoryUsage);
                System.out.printf("      Przepustowość: %.2f zapytań/s%n", throughput);
                System.out.printf("      Udane zapytania: %d/%d%n", responseTimes.size(), users);
            }

            allResults.put(modelName, scalabilityResults);
            System.out.println("   ------------------------------------");
        }

        String fileName = ExcelExporter.exportScalabilityTest(allResults, "test_skalowalnosci_iteracyjne");
        if (fileName != null) {
            System.out.println("Wyniki zapisane do: " + fileName);
        }
    }

    @Test
    @DisplayName("Testy zużycia zasobów")
    void testResourceConsumption() {
        String testUrl = "https://pl.wikipedia.org/wiki/Smartfon";
        String query = "Jaki system operacyjny steruje urządzeniem?";

        int iterations = 3;

        System.out.println("\n=== TESTY ZUŻYCIA ZASOBÓW ===");
        System.out.println("Liczba iteracji: " + iterations);
        System.out.println();

        Map<String, AIClient> testClients = getAllClients();
        Map<String, ExcelExporter.ResourceConsumptionResult> allResults = new HashMap<>();

        for (Map.Entry<String, AIClient> entry : testClients.entrySet()) {
            String modelName = entry.getKey();
            AIClient client = entry.getValue();

            System.out.printf("MODEL: %s%n", modelName);

            List<Double> cpuUsages = new ArrayList<>();
            List<Double> systemCpuUsages = new ArrayList<>();
            List<Double> memoryUsages = new ArrayList<>();
            List<Long> responseTimes = new ArrayList<>();

            for (int i = 0; i < iterations; i++) {
                try {
                    WebDriver modelDriver = createNewDriver();
                    ScrapingService scrapingService = new ScrapingService(modelDriver, client);
                    scrapingService.setUserContext(query);

                    long memoryBefore = getUsedMemory();

                    CPUMonitor.CPUMetrics cpuMetrics = cpuMonitor.measureCpuDuringTask(() -> {
                        try {
                            scrapingService.navigateTo(testUrl);
                            scrapingService.extractAndFilterContent();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });

                    long memoryAfter = getUsedMemory();


                    double memoryUsage = (memoryAfter - memoryBefore) / (1024.0);


                    cpuUsages.add(cpuMetrics.processCpuUsage());
                    systemCpuUsages.add(cpuMetrics.systemCpuUsage());
                    memoryUsages.add(memoryUsage);
                    responseTimes.add(cpuMetrics.durationMs());

                    System.out.printf("      Czas: %d ms%n", cpuMetrics.durationMs());
                    System.out.printf("      CPU procesu: %.1f%%%n", cpuMetrics.processCpuUsage());
                    System.out.printf("      CPU systemu: %.1f%%%n", cpuMetrics.systemCpuUsage());
                    System.out.printf("      Pamięć: %.1f MB%n", memoryUsage);

                    modelDriver.quit();

                    Thread.sleep(1000);
                } catch (Exception e) {
                    System.out.printf("   Iteracja %d: Błąd - %s%n", i + 1, e.getMessage());
                }
            }

            double avgCpuUsage = cpuUsages.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double avgSystemCpuUsage = systemCpuUsages.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double avgMemoryUsage = memoryUsages.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double avgResponseTime = responseTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);

            ExcelExporter.ResourceConsumptionResult resourceResult =
                    new ExcelExporter.ResourceConsumptionResult(
                            avgResponseTime,
                            avgMemoryUsage,
                            avgCpuUsage,
                            avgSystemCpuUsage,
                            iterations
                    );

            allResults.put(modelName, resourceResult);

            System.out.printf("   ŚREDNIE:%n");
            System.out.printf("      Czas: %.1f ms%n", avgResponseTime);
            System.out.printf("      Pamięć: %.1f MB%n", avgMemoryUsage);
            System.out.printf("      CPU procesu: %.1f%%%n", avgCpuUsage);
            System.out.printf("      CPU systemu: %.1f%%%n", avgSystemCpuUsage);
            System.out.println("   ------------------------------------");
        }

        String fileName = ExcelExporter.exportResourceConsumptionTest(allResults, "test_zuzycia_zasobow");
        if (fileName != null) {
            System.out.println("Wyniki zapisane do: " + fileName);
        }
    }

    private ExcelExporter.PerformanceStatsResult calculateAverages(List<ExcelExporter.PerformanceStatsResult> stats) {
        if (stats.isEmpty()) {
            return new ExcelExporter.PerformanceStatsResult(0, 0, 0.0, "Brak danych", "Średnia");
        }

        long totalResponseTime = 0;
        int totalElements = 0;
        double totalMemory = 0.0;
        List<String> errors = new ArrayList<>();
        int validResults = 0;

        for (ExcelExporter.PerformanceStatsResult stat : stats) {
            if (stat.responseTime() >= 0) {
                totalResponseTime += stat.responseTime();
                totalElements += stat.elementsProcessed();
                totalMemory += stat.memoryUsage();
                validResults++;
            }
            if (!stat.error().isEmpty()) {
                errors.add(stat.error());
            }
        }

        long avgResponseTime = validResults > 0 ? totalResponseTime / validResults : 0;
        int avgElements = validResults > 0 ? totalElements / validResults : 0;
        double avgMemory = validResults > 0 ? totalMemory / validResults : 0.0;
        String allErrors = String.join("; ", errors);

        return new ExcelExporter.PerformanceStatsResult(
                avgResponseTime,
                avgElements,
                avgMemory,
                allErrors,
                "Średnia wszystkich typów"
        );
    }

    private double calculateFilteredAverage(List<Double> values) {
        if (values.isEmpty()) return 0.0;

        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        double median = sorted.get(sorted.size() / 2);

        double threshold = median * 3;
        return sorted.stream()
                .filter(v -> v <= threshold)
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(median);
    }

    private ExcelExporter.PerformanceStatsResult testUrlPerformance(AIClient client, String url, String query, String pageType) {
        WebDriver modelDriver = createNewDriver();
        ScrapingService scrapingService = new ScrapingService(modelDriver, client);
        scrapingService.setUserContext(query);

        String error = "";
        long memoryBefore = getUsedMemory();
        long responseTime;
        int elementsProcessed = 0;
        double memoryUsage = 0.0;

        try {
            long startTime = System.nanoTime();
            scrapingService.navigateTo(url);
            scrapingService.extractAndFilterContent();
            long endTime = System.nanoTime();

            long memoryAfter = getUsedMemory();

            responseTime = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
            elementsProcessed = scrapingService.getLastExtractedElements().size();
            memoryUsage = Math.max(0, (memoryAfter - memoryBefore) / (1024.0));

            if (client.hasParsingError()) {
                error = "LLM Parsing Error: " + client.getLastParsingError();
            }

        } catch (Exception e) {
            error = "Execution Error: " + e.getMessage();
            responseTime = -1;
        } finally {
            modelDriver.quit();
        }

        return new ExcelExporter.PerformanceStatsResult(
                responseTime,
                elementsProcessed,
                memoryUsage,
                error,
                pageType
        );
    }

    private long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {
            // ignored
        }
        return runtime.totalMemory() - runtime.freeMemory();
    }

    @SuppressWarnings("unused")
    public record PerformanceStats(
            long responseTime,
            int elementsProcessed,
            double memoryUsage,
            String error,
            int count,
            String pageType
    ) {

        public PerformanceStats add(PerformanceStats other) {
            return new PerformanceStats(
                    this.responseTime + other.responseTime,
                    this.elementsProcessed + other.elementsProcessed,
                    this.memoryUsage + other.memoryUsage,
                    this.error + (other.error.isEmpty() ? "" : "; " + other.error),
                    this.count + other.count,
                    "Średnia wszystkich typów"
            );
        }
    }
}