package com.example.mockserver.admin;

import com.example.mockserver.wiremock.StubRegistry;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminController {
    private final StubRegistry registry;

    public AdminController(StubRegistry registry) { this.registry = registry; }

    @GetMapping("/status")
    public Map<String, Object> status() { return registry.status(); }

    @PostMapping("/reload")
    public Map<String, Object> reload() {
        return Map.of("registeredDomains", registry.reload(), "result", "reloaded");
    }
}
