package com.example.mockserver.wiremock;

import com.example.mockserver.MockProperties;
import com.example.mockserver.data.MockDataRepository;
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
                .extensions(new ReferenceResponseTransformer(properties, repository)));
    }
}
