package extension.utils;

import extension.services.ScrapingService.WebElementData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class ContentSaver {
    private static final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public static void saveToJson(Map<String, WebElementData> content, String filePath) throws IOException {
        mapper.writeValue(new File(filePath), content);
    }

    public static Map<String, WebElementData> loadFromJson(String filePath) throws IOException {
        return mapper.readValue(new File(filePath),
                mapper.getTypeFactory().constructMapType(
                        Map.class, String.class, WebElementData.class));
    }
}