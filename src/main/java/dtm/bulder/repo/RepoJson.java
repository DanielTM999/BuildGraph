package dtm.bulder.repo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RepoJson {

    public static final ObjectMapper MAPPER = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .serializationInclusion(JsonInclude.Include.NON_NULL)
            .build();

    private RepoJson() {
    }

    public static <T> T read(Path path, Class<T> type) throws IOException {
        return MAPPER.readValue(Files.readString(path, StandardCharsets.UTF_8), type);
    }

    public static void write(Path path, Object value) throws IOException {
        Files.writeString(path, MAPPER.writeValueAsString(value), StandardCharsets.UTF_8);
    }
}
