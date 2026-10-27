package com.aidefenselayer.model;

import com.aidefenselayer.domain.Decision;

import java.util.List;

public record DefenseResponse(
        Decision decision,
        String processedContent,
        double riskScore,
        List<String> triggeredScanners,
        Telemetry telemetry
) {
    public DefenseResponse {
        triggeredScanners = triggeredScanners == null ? List.of() : List.copyOf(triggeredScanners);
    }
}
