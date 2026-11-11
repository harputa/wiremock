package com.example.mockserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(MockProperties.class)
public class MockServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(MockServerApplication.class, args);
    }
}
