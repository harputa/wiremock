package com.ahsharp.wiremock.mockserver;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;
import static org.mockito.Mockito.mockStatic;

class MockServerApplicationTest {
    @Test void mainDelegatesToSpringApplication() {
        String[] args = {"--server.port=0"};
        try (MockedStatic<SpringApplication> spring = mockStatic(SpringApplication.class)) {
            MockServerApplication.main(args);
            spring.verify(() -> SpringApplication.run(MockServerApplication.class, args));
        }
    }
}
