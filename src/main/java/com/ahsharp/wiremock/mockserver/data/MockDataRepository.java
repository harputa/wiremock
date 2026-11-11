package com.ahsharp.wiremock.mockserver.data;

import java.util.List;
import java.util.Optional;

public interface MockDataRepository {
    Optional<MockResponse> find(String responseFolder, String reference);

    /** Names of the available response folders, each of which is exposed as one mock endpoint. */
    List<String> responseFolders();
}
