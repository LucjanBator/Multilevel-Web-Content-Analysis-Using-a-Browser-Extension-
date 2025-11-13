package utils;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class ExcelExporter {

    private static final String EXPORT_DIR = "src/test/resources/test-results/";

    static {
        try {
            Files.createDirectories(Paths.get(EXPORT_DIR));
        } catch (IOException e) {
            System.err.println("Błąd podczas tworzenia katalogu: " + e.getMessage());
        }
    }

    public record AdaptabilityResult(
            int textElements,
            int linkElements,
            double overallRelevance,
            double selectorValidationScore,
            double semanticRelevanceScore,
            int validSelectors,
            int totalSelectors,
            String errors,
            int errorCount
    ) {
    }

    public record QualityMetricsResult(
            double precision,
            double recall,
            double f1Score,
            int truePositiveTexts,
            int truePositiveLinks,
            int expectedTexts,
            int expectedLinks,
            String errors,
            int errorCount
    ) {
    }

    public record NERMetricsResult(
            double entityPrecision,
            double entityRecall,
            double entityF1Score,
            int truePositiveEntities,
            int expectedEntities,
            String entityTypes,
            String errors,
            int errorCount
    ) {
    }

    public record ScalabilityResult(
            int concurrentUsers,
            double avgResponseTime,
            double avgMemoryUsage,
            double avgElementsProcessed,
            double throughput,
            int successfulRequests,

            String errors

    ) {
    }

    public record ResourceConsumptionResult(
            double avgResponseTime,
            double avgMemoryUsage,
            double avgCpuUsage,
            double avgSystemCpuUsage,
            int iterations
    ) {
    }

    public static String exportAdaptabilityTest(Map<String, Map<String, AdaptabilityResult>> results, String testName) {
        String fileName = generateFileName(testName);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Wyniki adaptacyjności");

            Row headerRow = sheet.createRow(0);
            String[] headers = {
                    "Model", "URL", "Teksty", "Linki",
                    "Trafność ogólna (%)", "Walidacja selektorów (%)",
                    "Trafność semantyczna (%)", "Poprawne selektory",
                    "Wszystkie selektory", "Błędy", "Liczba błędów", "Timestamp"
            };

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(createHeaderStyle(workbook));
            }

            CellStyle errorStyle = createErrorStyle(workbook);

            int rowNum = 1;
            for (Map.Entry<String, Map<String, AdaptabilityResult>> modelEntry : results.entrySet()) {
                String modelName = modelEntry.getKey();
                Map<String, AdaptabilityResult> urlResults = modelEntry.getValue();

                for (Map.Entry<String, AdaptabilityResult> urlEntry : urlResults.entrySet()) {
                    String url = urlEntry.getKey();
                    AdaptabilityResult result = urlEntry.getValue();

                    Row row = sheet.createRow(rowNum++);

                    row.createCell(0).setCellValue(modelName);
                    row.createCell(1).setCellValue(url);
                    row.createCell(2).setCellValue(result.textElements());
                    row.createCell(3).setCellValue(result.linkElements());
                    row.createCell(4).setCellValue(result.overallRelevance());
                    row.createCell(5).setCellValue(result.selectorValidationScore());
                    row.createCell(6).setCellValue(result.semanticRelevanceScore());
                    row.createCell(7).setCellValue(result.validSelectors());
                    row.createCell(8).setCellValue(result.totalSelectors());
                    row.createCell(9).setCellValue(result.errors());
                    row.createCell(10).setCellValue(result.errorCount());
                    row.createCell(11).setCellValue(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

                    if (result.errorCount() > 0) {
                        for (int i = 0; i < headers.length; i++) {
                            row.getCell(i).setCellStyle(errorStyle);
                        }
                    }
                }
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            sheet.setColumnWidth(9, 15000);

            try (FileOutputStream fileOut = new FileOutputStream(EXPORT_DIR + fileName)) {
                workbook.write(fileOut);
            }

            return fileName;

        } catch (IOException e) {
            System.err.println("Błąd eksportu do Excel: " + e.getMessage());
            return null;
        }
    }

    public static String exportQualityMetricsTest(Map<String, Map<String, QualityMetricsResult>> results, String testName) {
        String fileName = generateFileName(testName);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Metryki jakości");

            Row headerRow = sheet.createRow(0);
            String[] headers = {
                    "Model", "URL", "Precyzja", "Czułość", "F1-Score",
                    "Trafne teksty", "Oczekiwane teksty", "Trafne linki", "Oczekiwane linki",
                    "Błędy", "Liczba błędów", "Timestamp"
            };

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(createHeaderStyle(workbook));
            }

            CellStyle goodStyle = createGoodStyle(workbook);
            CellStyle mediumStyle = createMediumStyle(workbook);
            CellStyle poorStyle = createPoorStyle(workbook);

            int rowNum = 1;
            for (Map.Entry<String, Map<String, QualityMetricsResult>> modelEntry : results.entrySet()) {
                String modelName = modelEntry.getKey();
                Map<String, QualityMetricsResult> urlResults = modelEntry.getValue();

                for (Map.Entry<String, QualityMetricsResult> urlEntry : urlResults.entrySet()) {
                    String url = urlEntry.getKey();
                    QualityMetricsResult result = urlEntry.getValue();

                    Row row = sheet.createRow(rowNum++);

                    row.createCell(0).setCellValue(modelName);
                    row.createCell(1).setCellValue(url);
                    row.createCell(2).setCellValue(result.precision());
                    row.createCell(3).setCellValue(result.recall());

                    Cell f1Cell = row.createCell(4);
                    f1Cell.setCellValue(result.f1Score());

                    row.createCell(5).setCellValue(result.truePositiveTexts());
                    row.createCell(6).setCellValue(result.expectedTexts());
                    row.createCell(7).setCellValue(result.truePositiveLinks());
                    row.createCell(8).setCellValue(result.expectedLinks());
                    row.createCell(9).setCellValue(result.errors());
                    row.createCell(10).setCellValue(result.errorCount());
                    row.createCell(11).setCellValue(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

                    if (result.f1Score() >= 0.7) {
                        f1Cell.setCellStyle(goodStyle);
                    } else if (result.f1Score() >= 0.4) {
                        f1Cell.setCellStyle(mediumStyle);
                    } else {
                        f1Cell.setCellStyle(poorStyle);
                    }
                }
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream fileOut = new FileOutputStream(EXPORT_DIR + fileName)) {
                workbook.write(fileOut);
            }

            return fileName;

        } catch (IOException e) {
            System.err.println("Błąd eksportu do Excel: " + e.getMessage());
            return null;
        }
    }

    public static String exportNERMetricsTest(Map<String, Map<String, NERMetricsResult>> results, String testName) {
        String fileName = generateFileName(testName);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Metryki NER");

            Row headerRow = sheet.createRow(0);
            String[] headers = {
                    "Model", "URL", "Precyzja encji", "Czułość encji", "F1-Score encji",
                    "Trafne encje", "Oczekiwane encje", "Typy encji",
                    "Błędy", "Liczba błędów", "Timestamp"
            };

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(createHeaderStyle(workbook));
            }

            int rowNum = 1;
            for (Map.Entry<String, Map<String, NERMetricsResult>> modelEntry : results.entrySet()) {
                String modelName = modelEntry.getKey();
                Map<String, NERMetricsResult> urlResults = modelEntry.getValue();

                for (Map.Entry<String, NERMetricsResult> urlEntry : urlResults.entrySet()) {
                    String url = urlEntry.getKey();
                    NERMetricsResult result = urlEntry.getValue();

                    Row row = sheet.createRow(rowNum++);

                    row.createCell(0).setCellValue(modelName);
                    row.createCell(1).setCellValue(url);
                    row.createCell(2).setCellValue(result.entityPrecision());
                    row.createCell(3).setCellValue(result.entityRecall());
                    row.createCell(4).setCellValue(result.entityF1Score());
                    row.createCell(5).setCellValue(result.truePositiveEntities());
                    row.createCell(6).setCellValue(result.expectedEntities());
                    row.createCell(7).setCellValue(result.entityTypes());
                    row.createCell(8).setCellValue(result.errors());
                    row.createCell(9).setCellValue(result.errorCount());
                    row.createCell(10).setCellValue(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                }
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            sheet.setColumnWidth(7, 10000);
            sheet.setColumnWidth(8, 12000);

            try (FileOutputStream fileOut = new FileOutputStream(EXPORT_DIR + fileName)) {
                workbook.write(fileOut);
            }

            return fileName;

        } catch (IOException e) {
            System.err.println("Błąd eksportu do Excel: " + e.getMessage());
            return null;
        }
    }

    public static String exportPerformanceAveragesTest(Map<String, PerformanceStatsResult> results, String testName) {
        String fileName = generateFileName(testName);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Średnia wydajność");

            Row headerRow = sheet.createRow(0);
            String[] headers = {
                    "Model", "Typ strony", "Średni czas odpowiedzi (ms)", "Średnia liczba elementów",
                    "Średnie użycie pamięci (MB)", "Elementy/sekundę", "Błędy", "Timestamp"
            };

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(createHeaderStyle(workbook));
            }

            CellStyle fastStyle = createFastStyle(workbook);
            CellStyle mediumStyle = createMediumPerformanceStyle(workbook);
            CellStyle slowStyle = createSlowStyle(workbook);

            int rowNum = 1;
            for (Map.Entry<String, PerformanceStatsResult> entry : results.entrySet()) {
                String modelName = entry.getKey();
                PerformanceStatsResult result = entry.getValue();

                Row row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(modelName);
                row.createCell(1).setCellValue(result.pageType());

                Cell timeCell = row.createCell(2);
                timeCell.setCellValue(result.responseTime());

                row.createCell(3).setCellValue(result.elementsProcessed());
                row.createCell(4).setCellValue(result.memoryUsage());

                double elementsPerSecond = result.responseTime() > 0 ?
                        (result.elementsProcessed() * 1000.0) / result.responseTime() : 0.0;
                row.createCell(5).setCellValue(elementsPerSecond);

                row.createCell(6).setCellValue(result.error());
                row.createCell(7).setCellValue(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

                if (result.responseTime() < 2000) {
                    timeCell.setCellStyle(fastStyle);
                } else if (result.responseTime() < 8000) {
                    timeCell.setCellStyle(mediumStyle);
                } else {
                    timeCell.setCellStyle(slowStyle);
                }
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream fileOut = new FileOutputStream(EXPORT_DIR + fileName)) {
                workbook.write(fileOut);
            }

            return fileName;

        } catch (IOException e) {
            System.err.println("Błąd eksportu do Excel: " + e.getMessage());
            return null;
        }
    }

    public record PerformanceStatsResult(
            long responseTime,
            int elementsProcessed,
            double memoryUsage,
            String error,
            String pageType
    ) {
    }

    public static String exportPerformanceTest(Map<String, List<PerformanceStatsResult>> results, String testName) {
        String fileName = generateFileName(testName);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Wydajność");

            Row headerRow = sheet.createRow(0);
            String[] headers = {
                    "Model", "Typ strony", "Czas odpowiedzi (ms)", "Przetworzone elementy",
                    "Zużycie pamięci (MB)", "Elementy/sekundę", "Błędy", "Timestamp"
            };

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(createHeaderStyle(workbook));
            }

            CellStyle fastStyle = createFastStyle(workbook);
            CellStyle mediumStyle = createMediumPerformanceStyle(workbook);
            CellStyle slowStyle = createSlowStyle(workbook);
            CellStyle errorStyle = createErrorStyle(workbook);

            int rowNum = 1;
            for (Map.Entry<String, List<PerformanceStatsResult>> modelEntry : results.entrySet()) {
                String modelName = modelEntry.getKey();
                List<PerformanceStatsResult> modelResults = modelEntry.getValue();

                for (PerformanceStatsResult result : modelResults) {
                    Row row = sheet.createRow(rowNum++);

                    row.createCell(0).setCellValue(modelName);
                    row.createCell(1).setCellValue(result.pageType());

                    Cell timeCell = row.createCell(2);
                    if (result.responseTime() >= 0) {
                        timeCell.setCellValue(result.responseTime());

                        if (result.responseTime() < 2000) {
                            timeCell.setCellStyle(fastStyle);
                        } else if (result.responseTime() < 8000) {
                            timeCell.setCellStyle(mediumStyle);
                        } else {
                            timeCell.setCellStyle(slowStyle);
                        }
                    } else {
                        timeCell.setCellValue("BŁĄD");
                        timeCell.setCellStyle(errorStyle);
                    }

                    row.createCell(3).setCellValue(result.elementsProcessed());
                    row.createCell(4).setCellValue(result.memoryUsage());

                    double elementsPerSecond = result.responseTime() > 0 ?
                            (result.elementsProcessed() * 1000.0) / result.responseTime() : 0.0;
                    row.createCell(5).setCellValue(elementsPerSecond);

                    Cell errorCell = row.createCell(6);
                    errorCell.setCellValue(result.error());

                    row.createCell(7).setCellValue(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

                    if (!result.error().isEmpty()) {
                        for (int i = 0; i < headers.length; i++) {
                            row.getCell(i).setCellStyle(errorStyle);
                        }
                    }
                }

                rowNum++;
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            sheet.setColumnWidth(6, 15000);

            try (FileOutputStream fileOut = new FileOutputStream(EXPORT_DIR + fileName)) {
                workbook.write(fileOut);
            }

            return fileName;

        } catch (IOException e) {
            System.err.println("Błąd eksportu do Excel: " + e.getMessage());
            return null;
        }
    }

    public static String exportScalabilityTest(Map<String, Map<Integer, ScalabilityResult>> results, String testName) {
        String fileName = generateFileName(testName);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Skalowalność");

            Row headerRow = sheet.createRow(0);
            String[] headers = {
                    "Model", "Liczba użytkowników", "Średni czas odpowiedzi (ms)",
                    "Średnie użycie pamięci (MB)", "Średnia liczba elementów",
                    "Przepustowość (zapytania/s)", "Udane zapytania", "Błędy", "Timestamp"
            };

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(createHeaderStyle(workbook));
            }

            int rowNum = 1;
            for (Map.Entry<String, Map<Integer, ScalabilityResult>> modelEntry : results.entrySet()) {
                String modelName = modelEntry.getKey();
                Map<Integer, ScalabilityResult> userResults = modelEntry.getValue();

                for (Map.Entry<Integer, ScalabilityResult> userEntry : userResults.entrySet()) {
                    int users = userEntry.getKey();
                    ScalabilityResult result = userEntry.getValue();

                    Row row = sheet.createRow(rowNum++);

                    row.createCell(0).setCellValue(modelName);
                    row.createCell(1).setCellValue(users);
                    row.createCell(2).setCellValue(result.avgResponseTime());
                    row.createCell(3).setCellValue(result.avgMemoryUsage());
                    row.createCell(4).setCellValue(result.avgElementsProcessed());
                    row.createCell(5).setCellValue(result.throughput());
                    row.createCell(6).setCellValue(result.successfulRequests());
                    row.createCell(7).setCellValue(result.errors());
                    row.createCell(8).setCellValue(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                }
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream fileOut = new FileOutputStream(EXPORT_DIR + fileName)) {
                workbook.write(fileOut);
            }

            return fileName;

        } catch (IOException e) {
            System.err.println("Błąd eksportu do Excel: " + e.getMessage());
            return null;
        }
    }

    public static String exportResourceConsumptionTest(Map<String, ResourceConsumptionResult> results, String testName) {
        String fileName = generateFileName(testName);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Zużycie zasobów");

            Row headerRow = sheet.createRow(0);
            String[] headers = {
                    "Model", "Średni czas odpowiedzi (ms)", "Średnie użycie pamięci (MB)",
                    "Średnie CPU procesu (%)", "Średnie CPU systemu (%)", "Liczba iteracji", "Timestamp"
            };

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(createHeaderStyle(workbook));
            }

            CellStyle lowUsageStyle = createLowUsageStyle(workbook);
            CellStyle mediumUsageStyle = createMediumUsageStyle(workbook);
            CellStyle highUsageStyle = createHighUsageStyle(workbook);

            int rowNum = 1;
            for (Map.Entry<String, ResourceConsumptionResult> entry : results.entrySet()) {
                String modelName = entry.getKey();
                ResourceConsumptionResult result = entry.getValue();

                Row row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(modelName);
                row.createCell(1).setCellValue(result.avgResponseTime());

                Cell memoryCell = row.createCell(2);
                memoryCell.setCellValue(result.avgMemoryUsage());

                Cell processCpuCell = row.createCell(3);
                processCpuCell.setCellValue(result.avgCpuUsage());

                Cell systemCpuCell = row.createCell(4);
                systemCpuCell.setCellValue(result.avgSystemCpuUsage());

                row.createCell(5).setCellValue(result.iterations());
                row.createCell(6).setCellValue(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

                if (result.avgMemoryUsage() < 50) {
                    memoryCell.setCellStyle(lowUsageStyle);
                } else if (result.avgMemoryUsage() < 200) {
                    memoryCell.setCellStyle(mediumUsageStyle);
                } else {
                    memoryCell.setCellStyle(highUsageStyle);
                }

                if (result.avgCpuUsage() < 30) {
                    processCpuCell.setCellStyle(lowUsageStyle);
                } else if (result.avgCpuUsage() < 70) {
                    processCpuCell.setCellStyle(mediumUsageStyle);
                } else {
                    processCpuCell.setCellStyle(highUsageStyle);
                }

                if (result.avgSystemCpuUsage() < 30) {
                    systemCpuCell.setCellStyle(lowUsageStyle);
                } else if (result.avgSystemCpuUsage() < 70) {
                    systemCpuCell.setCellStyle(mediumUsageStyle);
                } else {
                    systemCpuCell.setCellStyle(highUsageStyle);
                }
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            try (FileOutputStream fileOut = new FileOutputStream(EXPORT_DIR + fileName)) {
                workbook.write(fileOut);
            }

            return fileName;

        } catch (IOException e) {
            System.err.println("Błąd eksportu do Excel: " + e.getMessage());
            return null;
        }
    }

    private static CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private static CellStyle createErrorStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setColor(IndexedColors.RED.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static CellStyle createGoodStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static CellStyle createMediumStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static CellStyle createPoorStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static CellStyle createFastStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static CellStyle createMediumPerformanceStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static CellStyle createSlowStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static CellStyle createLowUsageStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static CellStyle createMediumUsageStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static CellStyle createHighUsageStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private static String generateFileName(String testName) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return testName + "_" + timestamp + ".xlsx";
    }
}