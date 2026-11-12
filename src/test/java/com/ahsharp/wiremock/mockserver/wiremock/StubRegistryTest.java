package com.ahsharp.wiremock.mockserver.wiremock;

import com.ahsharp.wiremock.mockserver.MockProperties;
import com.ahsharp.wiremock.mockserver.data.MockDataRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.boot.DefaultApplicationArguments;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StubRegistryTest {
    private final WireMockServer wireMock = mock(WireMockServer.class);
    private final MockDataRepository repository = mock(MockDataRepository.class);
    private final MockProperties properties = new MockProperties();
    private final StubRegistry registry = new StubRegistry(wireMock, properties, repository);

    @Test void reloadResetsThenRegistersOneStubPerFolder() {
        when(repository.responseFolders()).thenReturn(List.of("products", "products/electronics"));

        assertEquals(2, registry.reload());

        ArgumentCaptor<MappingBuilder> stubs = ArgumentCaptor.forClass(MappingBuilder.class);
        InOrder order = inOrder(wireMock);
        order.verify(wireMock).resetAll();
        order.verify(wireMock, times(2)).stubFor(stubs.capture());

        List<StubMapping> mappings = stubs.getAllValues().stream().map(MappingBuilder::build).toList();
        assertStub(mappings.get(0), "/products", "products");
        assertStub(mappings.get(1), "/products/electronics", "products/electronics");
    }

    private static void assertStub(StubMapping mapping, String path, String folder) {
        assertEquals(RequestMethod.GET, mapping.getRequest().getMethod());
        assertEquals(path, mapping.getRequest().getUrlPath());
        assertEquals(200, mapping.getResponse().getStatus());
        assertEquals(List.of(ReferenceResponseTransformer.NAME), mapping.getResponse().getTransformers());
        assertEquals(folder, mapping.getResponse().getTransformerParameters().getString("folder"));
    }

    @Test void reloadWithNoFoldersOnlyResets() {
        when(repository.responseFolders()).thenReturn(List.of());
        assertEquals(0, registry.reload());
        verify(wireMock).resetAll();
        verify(wireMock, never()).stubFor(any(MappingBuilder.class));
    }

    @Test void runPerformsReload() {
        when(repository.responseFolders()).thenReturn(List.of("orders"));
        registry.run(new DefaultApplicationArguments());
        verify(wireMock).resetAll();
        verify(wireMock).stubFor(any(MappingBuilder.class));
    }

    @Test void statusBeforeReloadHasNoDomains() {
        when(wireMock.isRunning()).thenReturn(false);
        when(wireMock.port()).thenReturn(9090);
        assertEquals(Map.of("running", false, "mockPort", 9090, "fixtureDirectory", "./mock-data",
                "domains", List.of()), registry.status());
    }

    @Test void statusReportsDiscoveredDomainsInOrder() {
        when(repository.responseFolders()).thenReturn(List.of("customers", "orders"));
        when(wireMock.isRunning()).thenReturn(true);
        when(wireMock.port()).thenReturn(1234);
        properties.setDataDirectory("/data");
        registry.reload();

        Map<String, Object> status = registry.status();
        assertEquals(List.of("running", "mockPort", "fixtureDirectory", "domains"), List.copyOf(status.keySet()));
        assertEquals(true, status.get("running"));
        assertEquals(1234, status.get("mockPort"));
        assertEquals("/data", status.get("fixtureDirectory"));
        assertEquals(List.of(MockDomain.forFolder("customers"), MockDomain.forFolder("orders")), status.get("domains"));
    }
}
