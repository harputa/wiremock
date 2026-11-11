package com.example.mockserver;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    private HttpResponse<String> request(String path, String header, String value) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + wireMock.port() + path))
                .GET().timeout(Duration.ofSeconds(6));
        if (header != null) request.header(header, value);
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test void productSelection() throws Exception {
        var response = request("/products", "X-Product-Ref", "2000");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Laptop Pro 14"));
        assertFalse(response.body().contains("4K Monitor"));
    }
    @Test void anotherProduct() throws Exception {
        var response = request("/products", "X-Product-Ref", "2001");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("4K Monitor"));
    }
    @Test void customerSelection() throws Exception {
        var response = request("/customers", "X-Customer-Ref", "2305");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Example Customer"));
    }
    @Test void missingHeader() throws Exception {
        var response = request("/products", null, null);
        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("MISSING_REFERENCE"));
    }
    @Test void unknownReference() throws Exception {
        var response = request("/products", "X-Product-Ref", "9999");
        assertEquals(404, response.statusCode());
        assertTrue(response.body().contains("FIXTURE_NOT_FOUND"));
    }
    @Test void simulatedFailure() throws Exception {
        var response = request("/products", "X-Product-Ref", "2999");
        assertEquals(503, response.statusCode());
    }
    @Test void pathTraversalRejected() throws Exception {
        var response = request("/products", "X-Product-Ref", "../customers/2305");
        assertEquals(404, response.statusCode());
    }
}
