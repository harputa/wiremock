package com.ahsharp.wiremock.mockserver;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "mock.wiremock-port=0",
        "mock.data-directory=./mock-data"
})
class MockServerIntegrationTest {
    @Autowired WireMockServer wireMock;
    @Value("${local.server.port}") int adminPort;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    private HttpResponse<String> request(String path, String header, String value) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + wireMock.port() + path))
                .GET().timeout(Duration.ofSeconds(6));
        if (header != null) request.header(header, value);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> reload() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + adminPort + "/admin/reload"))
                .POST(HttpRequest.BodyPublishers.noBody()).timeout(Duration.ofSeconds(6)).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test void productSelection() throws Exception {
        var response = request("/products", "X-Products-Ref", "2000");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Laptop Pro 14"));
        assertFalse(response.body().contains("4K Monitor"));
    }
    @Test void anotherProduct() throws Exception {
        var response = request("/products", "X-Products-Ref", "2001");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("4K Monitor"));
    }
    @Test void customerSelection() throws Exception {
        var response = request("/customers", "X-Customers-Ref", "2305");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Example Customer"));
    }
    @Test void everyFolderIsAnEndpoint() throws Exception {
        assertEquals(200, request("/orders", "X-Orders-Ref", "7001").statusCode());
        assertEquals(200, request("/categories", "X-Categories-Ref", "3001").statusCode());
    }
    @Test void headerNameIsCaseInsensitive() throws Exception {
        assertEquals(200, request("/products", "x-products-ref", "2000").statusCode());
    }
    @Test void headerMustContainFolderName() throws Exception {
        var response = request("/products", "X-Product-Ref", "2000");
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("X-Products-Ref"));
    }
    @Test void unknownFolderHasNoEndpoint() throws Exception {
        assertEquals(404, request("/unknown", "X-Unknown-Ref", "1").statusCode());
    }
    @Test void subFolderUsesTopLevelHeader() throws Exception {
        var response = request("/products/electronics", "X-Products-Ref", "2100");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Noise Cancelling Headphones"));
        assertEquals(400, request("/products/electronics", "X-Electronics-Ref", "2100").statusCode());
    }
    @Test void subFolderOnlyServesItsOwnFixtures() throws Exception {
        assertEquals(404, request("/products/electronics", "X-Products-Ref", "2000").statusCode());
        assertEquals(404, request("/products", "X-Products-Ref", "2100").statusCode());
    }
    @Test void newSubFolderActivatedByReload() throws Exception {
        String name = "reloadtest" + System.nanoTime();
        Path folder = Path.of("mock-data", "customers", name);
        try {
            Files.createDirectories(folder);
            Files.writeString(folder.resolve("1.json"), "{\"reloaded\":true}");
            assertEquals(404, request("/customers/" + name, "X-Customers-Ref", "1").statusCode());

            assertEquals(200, reload().statusCode());
            var response = request("/customers/" + name, "X-Customers-Ref", "1");
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("reloaded"));
        } finally {
            Files.deleteIfExists(folder.resolve("1.json"));
            Files.deleteIfExists(folder);
            reload();
        }
    }
    @Test void missingHeader() throws Exception {
        var response = request("/products", null, null);
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("MISSING_REFERENCE"));
    }
    @Test void unknownReference() throws Exception {
        var response = request("/products", "X-Products-Ref", "9999");
        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("FIXTURE_NOT_FOUND"));
    }
    @Test void simulatedFailure() throws Exception {
        var response = request("/products", "X-Products-Ref", "2999");
        assertEquals(503, response.statusCode());
    }
    @Test void pathTraversalRejected() throws Exception {
        var response = request("/products", "X-Products-Ref", "../customers/2305");
        assertEquals(404, response.statusCode());
    }
}
