package com.example.mockserver;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mock")
public class MockProperties {
    private int wiremockPort = 9090;
    private String dataDirectory = "./mock-data";
    private Map<String, Domain> domains = new LinkedHashMap<>();

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

    public Map<String, Domain> getDomains() {
        return domains;
    }

    public void setDomains(Map<String, Domain> domains) {
        this.domains = domains;
    }

    public static class Domain {
        private String path;
        private String selectorHeader;
        private String responseFolder;

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getSelectorHeader() {
            return selectorHeader;
        }

        public void setSelectorHeader(String header) {
            this.selectorHeader = header;
        }

        public String getResponseFolder() {
            return responseFolder;
        }

        public void setResponseFolder(String folder) {
            this.responseFolder = folder;
        }
    }
}
