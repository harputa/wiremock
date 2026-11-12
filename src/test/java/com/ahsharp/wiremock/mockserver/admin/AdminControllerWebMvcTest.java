package com.ahsharp.wiremock.mockserver.admin;

import com.ahsharp.wiremock.mockserver.wiremock.MockDomain;
import com.ahsharp.wiremock.mockserver.wiremock.StubRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web slice: only the MVC layer and {@link AdminController} are loaded; the registry is mocked.
 */
@WebMvcTest(AdminController.class)
class AdminControllerWebMvcTest {
    @Autowired
    MockMvc mvc;
    @MockitoBean
    StubRegistry registry;

    @Test
    void statusReturnsRegistryStateAsJson() throws Exception {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("running", true);
        state.put("mockPort", 9090);
        state.put("fixtureDirectory", "./mock-data");
        state.put("domains", List.of(MockDomain.forFolder("products"), MockDomain.forFolder("products/electronics")));
        when(registry.status()).thenReturn(state);

        mvc.perform(get("/admin/status"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.running").value(true))
                .andExpect(jsonPath("$.mockPort").value(9090))
                .andExpect(jsonPath("$.fixtureDirectory").value("./mock-data"))
                .andExpect(jsonPath("$.domains", hasSize(2)))
                .andExpect(jsonPath("$.domains[0].folder").value("products"))
                .andExpect(jsonPath("$.domains[0].path").value("/products"))
                .andExpect(jsonPath("$.domains[0].selectorHeader").value("X-Products-Ref"))
                .andExpect(jsonPath("$.domains[1].path").value("/products/electronics"))
                .andExpect(jsonPath("$.domains[1].selectorHeader").value("X-Products-Ref"));
    }

    @Test
    void reloadReturnsRegisteredDomainCount() throws Exception {
        when(registry.reload()).thenReturn(4);

        mvc.perform(post("/admin/reload"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.registeredDomains").value(4))
                .andExpect(jsonPath("$.result").value("reloaded"));
        verify(registry).reload();
    }

    @Test
    void reloadRequiresPost() throws Exception {
        mvc.perform(get("/admin/reload")).andExpect(status().isMethodNotAllowed());
        verify(registry, never()).reload();
    }

    @Test
    void statusRejectsPost() throws Exception {
        mvc.perform(post("/admin/status")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void unknownAdminPathIsNotFound() throws Exception {
        mvc.perform(get("/admin/unknown")).andExpect(status().isNotFound());
    }
}
