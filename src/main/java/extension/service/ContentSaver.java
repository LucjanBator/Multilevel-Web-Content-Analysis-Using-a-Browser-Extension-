package extension.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ContentSaver {
    private static final Logger logger = LogManager.getLogger(ContentSaver.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS");

    public static String saveSnapshot(String htmlContent, String directory) throws IOException {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String filename = "snapshot_" + timestamp + ".html";
        Path filePath = Paths.get(directory, filename);

        Files.createDirectories(filePath.getParent());
        Files.write(filePath, htmlContent.getBytes());

        return filePath.toString();
    }

    public static void saveFilteredResults(FilteredElements filteredElements) {
        try {
            saveElementsToJsonFile(filteredElements.textElements(), "filtered_text_elements.json");
            saveElementsToJsonFile(filteredElements.linkElements(), "filtered_link_elements.json");
            logger.info("Filtered content saved successfully to separate files");
        } catch (Exception e) {
            logger.error("Error saving filtered results", e);
        }
    }

    private static void saveElementsToJsonFile(List<ScrapingService.WebElementData> elements, String fileName) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(elements);

            try (FileWriter file = new FileWriter(fileName)) {
                file.write(json);
            }

            logger.info("Elements saved to: {}", fileName);

        } catch (Exception e) {
            logger.error("Error saving elements to file: {}", fileName, e);
        }
    }
}