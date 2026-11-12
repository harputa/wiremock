package com.ahsharp.wiremock.mockserver.wiremock;

import com.ahsharp.wiremock.mockserver.data.MockDataRepository;
import com.ahsharp.wiremock.mockserver.data.MockFixtureException;
import com.ahsharp.wiremock.mockserver.data.MockResponse;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReferenceResponseTransformerTest {
    private final MockDataRepository repository = mock(MockDataRepository.class);
    private final ReferenceResponseTransformer transformer = new ReferenceResponseTransformer(repository);

    private static ServeEvent event(Parameters parameters, String header, String value) {
        LoggedRequest request = mock(LoggedRequest.class);
        if (header != null) when(request.getHeader(header)).thenReturn(value);
        ServeEvent event = mock(ServeEvent.class);
        when(event.getTransformerParameters()).thenReturn(parameters);
        when(event.getRequest()).thenReturn(request);
        return event;
    }

    private static ServeEvent event(String folder, String header, String value) {
        return event(Parameters.one("folder", folder), header, value);
    }

    private static void assertError(ResponseDefinition response, int status, String code, String message) {
        assertEquals(status, response.getStatus());
        assertEquals("application/json", response.getHeaders().getHeader("Content-Type").firstValue());
        assertEquals("{\"error\":\"" + code + "\",\"message\":\"" + message + "\"}", response.getBody());
    }

    @Test void metadata() {
        assertEquals("reference-response-transformer", transformer.getName());
        assertFalse(transformer.applyGlobally());
    }

    @Test void returnsFixture() {
        when(repository.find("products", "2000"))
                .thenReturn(Optional.of(new MockResponse(201, "text/plain", 15, "hello")));
        ResponseDefinition response = transformer.transform(event("products", "X-Products-Ref", "2000"));
        assertEquals(201, response.getStatus());
        assertEquals("text/plain", response.getHeaders().getHeader("Content-Type").firstValue());
        assertEquals(15, response.getFixedDelayMilliseconds());
        assertEquals("hello", response.getBody());
    }

    @Test void nestedFolderUsesTopLevelHeader() {
        when(repository.find("products/electronics", "2100"))
                .thenReturn(Optional.of(new MockResponse(200, "application/json", 0, "{}")));
        assertEquals(200, transformer.transform(event("products/electronics", "X-Products-Ref", "2100")).getStatus());
    }

    @Test void missingFolderParameter() {
        assertError(transformer.transform(event(Parameters.empty(), null, null)),
                500, "MOCK_CONFIGURATION_ERROR", "Unknown mock domain");
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void blankFolderParameter(String folder) {
        assertError(transformer.transform(event(folder, null, null)),
                500, "MOCK_CONFIGURATION_ERROR", "Unknown mock domain");
        verifyNoInteractions(repository);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void missingReferenceHeader(String value) {
        assertError(transformer.transform(event("products", "X-Products-Ref", value)),
                400, "MISSING_REFERENCE", "Required header: X-Products-Ref");
        verifyNoInteractions(repository);
    }

    @Test void unknownReference() {
        when(repository.find("products", "9999")).thenReturn(Optional.empty());
        assertError(transformer.transform(event("products", "X-Products-Ref", "9999")),
                404, "FIXTURE_NOT_FOUND", "No fixture for requested reference");
    }

    @Test void unreadableFixture() {
        when(repository.find("products", "1")).thenThrow(new MockFixtureException("bad", new RuntimeException()));
        assertError(transformer.transform(event("products", "X-Products-Ref", "1")),
                500, "FIXTURE_READ_ERROR", "Fixture could not be read");
    }
}
