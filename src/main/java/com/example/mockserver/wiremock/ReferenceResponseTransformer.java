package com.example.mockserver.wiremock;

import com.example.mockserver.MockProperties;
import com.example.mockserver.data.MockDataRepository;
import com.example.mockserver.data.MockFixtureException;
import com.example.mockserver.data.MockResponse;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import java.util.Optional;

public class ReferenceResponseTransformer implements ResponseDefinitionTransformerV2 {
    public static final String NAME = "reference-response-transformer";
    private final MockProperties configuration;
    private final MockDataRepository repository;

    public ReferenceResponseTransformer(MockProperties configuration, MockDataRepository repository) {
        this.configuration = configuration;
        this.repository = repository;
    }

    @Override
    public String getName() { return NAME; }

    @Override
    public boolean applyGlobally() { return false; }

    @Override
    public ResponseDefinition transform(ServeEvent event) {
        String domainName = event.getTransformerParameters().getString("domain");
        MockProperties.Domain domain = configuration.getDomains().get(domainName);
        if (domain == null) return error(500, "MOCK_CONFIGURATION_ERROR", "Unknown mock domain");

        String reference = event.getRequest().getHeader(domain.getSelectorHeader());
        if (reference == null || reference.isBlank()) {
            return error(400, "MISSING_REFERENCE", "Required header: " + domain.getSelectorHeader());
        }
        try {
            Optional<MockResponse> result = repository.find(domain.getResponseFolder(), reference);
            if (result.isEmpty()) return error(404, "FIXTURE_NOT_FOUND", "No fixture for requested reference");
            MockResponse response = result.get();
            return new ResponseDefinitionBuilder()
                    .withStatus(response.status())
                    .withHeader("Content-Type", response.contentType())
                    .withFixedDelay(response.delayMillis())
                    .withBody(response.body())
                    .build();
        } catch (MockFixtureException ex) {
            return error(500, "FIXTURE_READ_ERROR", "Fixture could not be read");
        }
    }

    private ResponseDefinition error(int status, String code, String message) {
        // Strings above are controlled by server configuration, not user-supplied request values.
        String json = "{\"error\":\"" + code + "\",\"message\":\"" + message + "\"}";
        return new ResponseDefinitionBuilder()
                .withStatus(status).withHeader("Content-Type", "application/json")
                .withBody(json).build();
    }
}
