package com.example.mockserver.wiremock;

import com.example.mockserver.MockProperties;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

@Component
public class StubRegistry implements ApplicationRunner {
    private final WireMockServer wireMock;
    private final MockProperties properties;

    public StubRegistry(WireMockServer wireMock, MockProperties properties) {
        this.wireMock = wireMock;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) { reload(); }

    /** Resets requests and mappings, then recreates the configured endpoints. */
    public synchronized int reload() {
        wireMock.resetAll();
        properties.getDomains().forEach((name, config) -> {
            if (config.getPath() == null || !config.getPath().startsWith("/")
                    || config.getSelectorHeader() == null || config.getSelectorHeader().isBlank()
                    || config.getResponseFolder() == null || config.getResponseFolder().isBlank()) {
                throw new IllegalStateException("Invalid mock domain configuration: " + name);
            }
            wireMock.stubFor(get(urlPathEqualTo(config.getPath()))
                    .willReturn(aResponse().withStatus(200)
                            .withTransformer(ReferenceResponseTransformer.NAME, "domain", name)));
        });
        return properties.getDomains().size();
    }

    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("running", wireMock.isRunning());
        result.put("mockPort", wireMock.port());
        result.put("fixtureDirectory", properties.getDataDirectory());
        result.put("domains", properties.getDomains());
        return result;
    }
}
