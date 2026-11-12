package com.ahsharp.wiremock.mockserver.data;

import com.ahsharp.wiremock.mockserver.MockProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

class FileMockDataRepositoryTest {
    @TempDir
    Path base;
    private FileMockDataRepository repository;

    @BeforeEach
    void setUp() {
        repository = repositoryFor(base);
    }

    private static FileMockDataRepository repositoryFor(Path dir) {
        MockProperties properties = new MockProperties();
        properties.setDataDirectory(dir.toString());
        return new FileMockDataRepository(properties);
    }

    private Path fixture(String folder, String ref, String json) throws IOException {
        Path dir = Files.createDirectories(base.resolve(folder));
        return Files.writeString(dir.resolve(ref + ".json"), json);
    }

    private void metadata(String folder, String ref, String content) throws IOException {
        Files.writeString(base.resolve(folder).resolve(ref + ".properties"), content);
    }

    // ---- find ----

    @Test
    void findsFixtureWithDefaultMetadata() throws IOException {
        fixture("products", "2000", "{\"name\":\"Laptop\"}");
        assertEquals(Optional.of(new MockResponse(200, "application/json", 0, "{\"name\":\"Laptop\"}")),
                repository.find("products", "2000"));
    }

    @Test
    void appliesMetadataFromPropertiesFile() throws IOException {
        fixture("products", "2999", "{}");
        metadata("products", "2999", "status=503\ndelay-ms=250\ncontent-type=application/problem+json\n");
        assertEquals(Optional.of(new MockResponse(503, "application/problem+json", 250, "{}")),
                repository.find("products", "2999"));
    }

    @Test
    void findsFixtureInNestedFolder() throws IOException {
        fixture("products/electronics", "2100", "{\"n\":1}");
        assertEquals("{\"n\":1}", repository.find("products/electronics", "2100").orElseThrow().body());
    }

    @Test
    void acceptsMetadataBoundaryValues() throws IOException {
        fixture("p", "low", "{}");
        metadata("p", "low", "status=100\ndelay-ms=0");
        fixture("p", "high", "{}");
        metadata("p", "high", "status=599\ndelay-ms=60000");
        assertEquals(100, repository.find("p", "low").orElseThrow().status());
        assertEquals(60000, repository.find("p", "high").orElseThrow().delayMillis());
    }

    @Test
    void missingFixtureIsEmpty() throws IOException {
        Files.createDirectories(base.resolve("products"));
        assertTrue(repository.find("products", "9999").isEmpty());
    }

    @Test
    void directoryNamedLikeFixtureIsEmpty() throws IOException {
        Files.createDirectories(base.resolve("products").resolve("1.json"));
        assertTrue(repository.find("products", "1").isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"../customers/2305", "a.b", "a/b", " "})
    void unsafeReferenceIsEmpty(String reference) throws IOException {
        fixture("products", "2000", "{}");
        assertTrue(repository.find("products", reference).isEmpty());
    }

    @Test
    void referenceLongerThan100CharactersIsEmpty() throws IOException {
        fixture("products", "x".repeat(100), "{}");
        assertTrue(repository.find("products", "x".repeat(100)).isPresent());
        assertTrue(repository.find("products", "x".repeat(101)).isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"..", "../products", "products/", "/products", "products//x", "a.b", "pro ducts"})
    void unsafeFolderIsEmpty(String folder) throws IOException {
        fixture("products", "2000", "{}");
        assertTrue(repository.find(folder, "2000").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"status=99", "status=600", "delay-ms=-1", "delay-ms=60001", "status=abc", "status=\\uZZZZ"})
    void invalidMetadataThrows(String content) throws IOException {
        fixture("products", "1", "{}");
        metadata("products", "1", content);
        MockFixtureException ex = assertThrows(MockFixtureException.class, () -> repository.find("products", "1"));
        assertEquals("Could not read fixture for products/1", ex.getMessage());
        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
    }

    @Test
    void unreadableBodyThrows() throws IOException {
        Path body = fixture("products", "1", "");
        Files.write(body, new byte[]{(byte) 0xC3, (byte) 0x28}); // malformed UTF-8
        MockFixtureException ex = assertThrows(MockFixtureException.class, () -> repository.find("products", "1"));
        assertInstanceOf(IOException.class, ex.getCause());
    }

    // ---- responseFolders ----

    @Test
    void missingBaseDirectoryHasNoFolders() {
        assertEquals(List.of(), repositoryFor(base.resolve("does-not-exist")).responseFolders());
    }

    @Test
    void baseThatIsAFileHasNoFolders() {
        // Use an existing file instead of creating one in @TempDir: on Windows, a freshly written file can still
        // be held open by antivirus/indexing when JUnit deletes the temp dir, which fails the test with
        // "Failed to close extension context". Maven runs tests from the project root, so pom.xml exists.
        Path file = Path.of("pom.xml").toAbsolutePath();
        assertTrue(Files.isRegularFile(file), "expected to run from the project root: " + file);
        assertEquals(List.of(), repositoryFor(file).responseFolders());
    }

    @Test
    void listsNestedSafeFoldersSortedAndSkipsFilesAndUnsafeNames() throws IOException {
        Files.createDirectories(base.resolve("products/electronics"));
        Files.createDirectories(base.resolve("customers"));
        Files.createDirectories(base.resolve("bad.name/child"));
        Files.createDirectories(base.resolve("orders/bad name"));
        Files.writeString(base.resolve("readme.json"), "{}");
        assertEquals(List.of("customers", "orders", "products", "products/electronics"), repository.responseFolders());
    }

    @Test
    void limitsDiscoveryToTenLevels() throws IOException {
        Files.createDirectories(base.resolve("l1/l2/l3/l4/l5/l6/l7/l8/l9/l10/l11"));
        List<String> folders = repository.responseFolders();
        assertEquals(10, folders.size());
        assertEquals("l1/l2/l3/l4/l5/l6/l7/l8/l9/l10", folders.get(9));
    }

    @Test
    void walkFailureIsWrapped() {
        try (MockedStatic<Files> files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.walk(any(Path.class), anyInt())).thenThrow(new IOException("boom"));
            MockFixtureException ex = assertThrows(MockFixtureException.class, repository::responseFolders);
            assertTrue(ex.getMessage().startsWith("Could not list fixture directory "));
            assertInstanceOf(IOException.class, ex.getCause());
        }
    }
}
