package com.example.mockserver.wiremock;

import com.example.mockserver.MockProperties;
import com.example.mockserver.data.MockDataRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

@Component
public class StubRegistry implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(StubRegistry.class);
    private final WireMockServer wireMock;
    private final MockProperties properties;
    private final MockDataRepository repository;
    private volatile List<MockDomain> domains = List.of();

    public StubRegistry(WireMockServer wireMock, MockProperties properties, MockDataRepository repository) {
        this.wireMock = wireMock;
        this.properties = properties;
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) { reload(); }

    /** Rescans the data directory, resets requests and mappings, then registers one endpoint per folder. */
    public synchronized int reload() {
        List<MockDomain> discovered = repository.responseFolders().stream().map(MockDomain::forFolder).toList();
        wireMock.resetAll();
        discovered.forEach(domain -> wireMock.stubFor(get(urlPathEqualTo(domain.path()))
                .willReturn(aResponse().withStatus(200)
                        .withTransformer(ReferenceResponseTransformer.NAME, "folder", domain.folder()))));
        domains = discovered;
        if (discovered.isEmpty()) {
            log.warn("No response folders found in {}; no mock endpoints registered", properties.getDataDirectory());
        } else {
            discovered.forEach(d -> log.info("Registered mock endpoint GET {} (header {})", d.path(), d.selectorHeader()));
        }
        return discovered.size();
    }

    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("running", wireMock.isRunning());
        result.put("mockPort", wireMock.port());
        result.put("fixtureDirectory", properties.getDataDirectory());
        result.put("domains", domains);
        return result;
    }
}
