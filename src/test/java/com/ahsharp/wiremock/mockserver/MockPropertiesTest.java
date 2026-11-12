package com.ahsharp.wiremock.mockserver;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MockPropertiesTest {
    @Test void hasDefaults() {
        MockProperties properties = new MockProperties();
        assertEquals(9090, properties.getWiremockPort());
        assertEquals("./mock-data", properties.getDataDirectory());
    }

    @Test void settersOverrideDefaults() {
        MockProperties properties = new MockProperties();
        properties.setWiremockPort(1234);
        properties.setDataDirectory("/tmp/fixtures");
        assertEquals(1234, properties.getWiremockPort());
        assertEquals("/tmp/fixtures", properties.getDataDirectory());
    }
}
