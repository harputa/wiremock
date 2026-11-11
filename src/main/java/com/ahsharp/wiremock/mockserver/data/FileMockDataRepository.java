package com.ahsharp.wiremock.mockserver.data;

import com.ahsharp.wiremock.mockserver.MockProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.StringJoiner;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** Reads files afresh on each request, so editing fixtures requires no restart. */
@Component
public class FileMockDataRepository implements MockDataRepository {
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_-]{1,100}");
    private static final int MAX_DEPTH = 10;
    private final Path basePath;

    public FileMockDataRepository(MockProperties config) {
        basePath = Path.of(config.getDataDirectory()).toAbsolutePath().normalize();
    }

    @Override
    public Optional<MockResponse> find(String responseFolder, String reference) {
        if (reference == null || !SAFE_NAME.matcher(reference).matches() || !isSafeFolder(responseFolder)) {
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

    /**
     * Lists every directory below the data directory (up to {@value #MAX_DEPTH} levels) as a
     * {@code /}-separated relative path, e.g. {@code products} and {@code products/electronics}.
     * Directories with an unsafe name, and everything below them, are skipped.
     */
    @Override
    public List<String> responseFolders() {
        if (!Files.isDirectory(basePath)) return List.of();
        try (Stream<Path> entries = Files.walk(basePath, MAX_DEPTH)) {
            return entries.filter(path -> !path.equals(basePath))
                    .filter(Files::isDirectory)
                    .map(path -> toFolderName(basePath.relativize(path)))
                    .filter(FileMockDataRepository::isSafeFolder)
                    .sorted()
                    .toList();
        } catch (IOException | UncheckedIOException e) {
            throw new MockFixtureException("Could not list fixture directory " + basePath, e);
        }
    }

    private static String toFolderName(Path relative) {
        StringJoiner name = new StringJoiner("/");
        relative.forEach(segment -> name.add(segment.toString()));
        return name.toString();
    }

    /** A folder is a {@code /}-separated path whose every segment is a safe name. */
    private static boolean isSafeFolder(String folder) {
        if (folder == null || folder.isEmpty()) return false;
        for (String segment : folder.split("/", -1)) {
            if (!SAFE_NAME.matcher(segment).matches()) return false;
        }
        return true;
    }
}
