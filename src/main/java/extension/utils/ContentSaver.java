package extension.utils;

import extension.services.ScrapingService.WebElementData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.openqa.selenium.Point;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class ContentSaver {
    private static final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS");

    public static void saveToJson(List<WebElementData> content, String filePath) throws IOException {
        List<SimplifiedElement> simplifiedList = filterDuplicates(content).stream()
                .map(SimplifiedElement::new)
                .collect(Collectors.toList());

        mapper.writeValue(new File(filePath), simplifiedList);
    }

    private static List<WebElementData> filterDuplicates(List<WebElementData> elements) {
        List<WebElementData> result = new ArrayList<>();
        final int POSITION_TOLERANCE = 5;

        for (WebElementData currentElement : elements) {
            boolean isDuplicate = false;

            // Sprawdzamy przeciwko już zapisanym elementom
            for (WebElementData savedElement : result) {
                if (isDuplicateElement(currentElement, savedElement, POSITION_TOLERANCE)) {
                    // Zachowaj element z krótszym selektorem
                    if (currentElement.getSelector().length() < savedElement.getSelector().length()) {
                        result.remove(savedElement);
                        result.add(currentElement);
                    }
                    isDuplicate = true;
                    break;
                }
            }

            if (!isDuplicate) {
                result.add(currentElement);
            }
        }

        return result;
    }

    private static boolean isDuplicateElement(WebElementData elem1, WebElementData elem2, int tolerance) {
        // Sprawdź czy jeden tekst zawiera się w drugim
        boolean textContains = elem1.getText().contains(elem2.getText()) ||
                elem2.getText().contains(elem1.getText());

        if (!textContains) {
            return false;
        }

        Point loc1 = elem1.getLocation();
        Point loc2 = elem2.getLocation();

        // Sprawdź czy elementy są w podobnej lokalizacji (w obrębie tolerancji)
        boolean similarPosition = Math.abs(loc1.x - loc2.x) <= tolerance ||
                Math.abs(loc1.y - loc2.y) <= tolerance;

        // Sprawdź czy jeden selektor jest rozszerzeniem drugiego (relacja rodzic-dziecko)
        boolean selectorHierarchy = elem1.getSelector().contains(elem2.getSelector()) ||
                elem2.getSelector().contains(elem1.getSelector());

        return similarPosition && selectorHierarchy;
    }

    public static String saveSnapshot(String htmlContent, String directory) throws IOException {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String filename = "snapshot_" + timestamp + ".html";
        Path filePath = Paths.get(directory, filename);

        Files.createDirectories(filePath.getParent());
        Files.write(filePath, htmlContent.getBytes());

        return filePath.toString();
    }

    private static class SimplifiedElement {
        public final String selector;
        public final String text;
        public final String type;
        public final int x;
        public final int y;

        public SimplifiedElement(WebElementData data) {
            this.selector = data.getSelector();
            this.text = data.getText();
            this.type = data.getType().name();
            this.x = data.getLocation().getX();
            this.y = data.getLocation().getY();
        }
    }
}