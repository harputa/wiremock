package com.ahsharp.wiremock.mockserver.admin;

import com.ahsharp.wiremock.mockserver.wiremock.StubRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminControllerTest {
    private final StubRegistry registry = mock(StubRegistry.class);
    private final AdminController controller = new AdminController(registry);

    @Test void statusDelegatesToRegistry() {
        Map<String, Object> status = Map.of("running", true);
        when(registry.status()).thenReturn(status);
        assertSame(status, controller.status());
    }

    @Test void reloadReportsRegisteredDomainCount() {
        when(registry.reload()).thenReturn(3);
        assertEquals(Map.of("registeredDomains", 3, "result", "reloaded"), controller.reload());
        verify(registry).reload();
    }
}
