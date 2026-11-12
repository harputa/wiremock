package com.ahsharp.wiremock.mockserver.wiremock;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MockDomainTest {
    @Test
    void topLevelFolder() {
        assertEquals(new MockDomain("products", "/products", "X-Products-Ref"), MockDomain.forFolder("products"));
    }

    @Test
    void nestedFolderUsesTopLevelHeader() {
        assertEquals(new MockDomain("products/electronics", "/products/electronics", "X-Products-Ref"),
                MockDomain.forFolder("products/electronics"));
    }

    @Test
    void alreadyCapitalizedAndSingleCharacterFolders() {
        assertEquals("X-Orders-Ref", MockDomain.forFolder("Orders").selectorHeader());
        assertEquals("X-A-Ref", MockDomain.forFolder("a/b/c").selectorHeader());
    }
}
