package com.example.mockserver.data;

import com.example.mockserver.MockProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Reads files afresh on each request, so editing fixtures requires no restart. */
@Component
public class FileMockDataRepository implements MockDataRepository {
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_-]{1,100}");
    private final Path basePath;

    public FileMockDataRepository(MockProperties config) {
        basePath = Path.of(config.getDataDirectory()).toAbsolutePath().normalize();
    }

    @Override
    public Optional<MockResponse> find(String responseFolder, String reference) {
        if (reference == null || !SAFE_NAME.matcher(reference).matches()
                || responseFolder == null || !SAFE_NAME.matcher(responseFolder).matches()) {
            return Optional.empty();
        }
        Path folder = basePath.resolve(responseFolder).normalize();
        Path body = folder.resolve(reference + ".json").normalize();
        Path metadata = folder.resolve(reference + ".properties").normalize();
        if (!folder.startsWith(basePath) || !body.startsWith(folder) || !Files.isRegularFile(body)) {
            return Optional.empty();
        }
        try {
            Properties props = new Properties();
            if (Files.isRegularFile(metadata)) {
                try (InputStream in = Files.newInputStream(metadata)) { props.load(in); }
            }
            int status = Integer.parseInt(props.getProperty("status", "200"));
            int delay = Integer.parseInt(props.getProperty("delay-ms", "0"));
            if (status < 100 || status > 599 || delay < 0 || delay > 60000) {
                throw new IllegalArgumentException("Invalid response metadata: " + metadata);
            }
            return Optional.of(new MockResponse(status,
                    props.getProperty("content-type", "application/json"),
                    delay, Files.readString(body, StandardCharsets.UTF_8)));
        } catch (IOException | IllegalArgumentException e) {
            throw new MockFixtureException("Could not read fixture for " + responseFolder + "/" + reference, e);
        }
    }
}
