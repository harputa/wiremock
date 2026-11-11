package com.example.mockserver.wiremock;

/**
 * A mock endpoint derived from a response folder. The URL mirrors the full folder path, while the
 * selector header is named after the top-level folder only:
 * <ul>
 *   <li>{@code products} → {@code /products}, header {@code X-Products-Ref}</li>
 *   <li>{@code products/electronics} → {@code /products/electronics}, header {@code X-Products-Ref}</li>
 * </ul>
 */
public record MockDomain(String folder, String path, String selectorHeader) {
    public static MockDomain forFolder(String folder) {
        String topLevel = folder.split("/", 2)[0];
        String capitalized = Character.toUpperCase(topLevel.charAt(0)) + topLevel.substring(1);
        return new MockDomain(folder, "/" + folder, "X-" + capitalized + "-Ref");
    }
}
