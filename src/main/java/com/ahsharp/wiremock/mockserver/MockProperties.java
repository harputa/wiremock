package com.ahsharp.wiremock.mockserver;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mock")
public class MockProperties {
    private int wiremockPort = 9090;
    private String dataDirectory = "./mock-data";

    public int getWiremockPort() {
        return wiremockPort;
    }

    public void setWiremockPort(int port) {
        this.wiremockPort = port;
    }

    public String getDataDirectory() {
        return dataDirectory;
    }

    public void setDataDirectory(String dir) {
        this.dataDirectory = dir;
    }
}
