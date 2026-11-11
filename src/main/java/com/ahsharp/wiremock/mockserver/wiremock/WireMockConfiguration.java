package com.ahsharp.wiremock.mockserver.wiremock;

import com.ahsharp.wiremock.mockserver.MockProperties;
import com.ahsharp.wiremock.mockserver.data.MockDataRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

@Configuration
public class WireMockConfiguration {
    @Bean(initMethod = "start", destroyMethod = "stop")
    WireMockServer wireMockServer(MockProperties properties, MockDataRepository repository) {
        return new WireMockServer(wireMockConfig()
                .port(properties.getWiremockPort())
                .extensions(new ReferenceResponseTransformer(repository)));
    }
}
