package com.example.mockserver.data;

import java.util.Optional;

public interface MockDataRepository {
    Optional<MockResponse> find(String responseFolder, String reference);
}
