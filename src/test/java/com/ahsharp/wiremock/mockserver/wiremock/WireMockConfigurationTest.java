package com.ahsharp.wiremock.mockserver.wiremock;

import com.ahsharp.wiremock.mockserver.MockProperties;
import com.ahsharp.wiremock.mockserver.data.MockDataRepository;
import com.ahsharp.wiremock.mockserver.data.MockResponse;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WireMockConfigurationTest {
    private final MockDataRepository repository = mock(MockDataRepository.class);

    @Test void usesConfiguredPortAndDoesNotStartServer() {
        MockProperties properties = new MockProperties();
        properties.setWiremockPort(12345);
        WireMockServer server = new WireMockConfiguration().wireMockServer(properties, repository);
        assertEquals(12345, server.getOptions().portNumber());
        assertFalse(server.isRunning());
    }

    @Test void registersReferenceTransformerExtension() throws Exception {
        MockProperties properties = new MockProperties();
        properties.setWiremockPort(0);
        WireMockServer server = new WireMockConfiguration().wireMockServer(properties, repository);
        server.start();
        try {
            when(repository.find("products", "1"))
                    .thenReturn(Optional.of(new MockResponse(202, "application/json", 0, "{\"ok\":true}")));
            server.stubFor(get(urlPathEqualTo("/products")).willReturn(aResponse()
                    .withTransformer(ReferenceResponseTransformer.NAME, "folder", "products")));

            HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + server.port() + "/products"))
                    .header("X-Products-Ref", "1").build(), HttpResponse.BodyHandlers.ofString());

            assertEquals(202, response.statusCode());
            assertEquals("{\"ok\":true}", response.body());
        } finally {
            server.stop();
        }
    }
}
