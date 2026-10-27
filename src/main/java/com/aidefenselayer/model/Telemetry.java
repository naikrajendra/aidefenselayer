package com.aidefenselayer.model;

import java.util.Map;

public record Telemetry(
        long processingTimeMs,
        int inputTokens,
        int outputTokens,
        Map<String, Object> operationalMetadata
) {
    public Telemetry {
        operationalMetadata = operationalMetadata == null ? Map.of() : Map.copyOf(operationalMetadata);
    }
}
