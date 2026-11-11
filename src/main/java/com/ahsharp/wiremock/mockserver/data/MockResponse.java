package com.ahsharp.wiremock.mockserver.data;

public record MockResponse(int status, String contentType, int delayMillis, String body) { }
