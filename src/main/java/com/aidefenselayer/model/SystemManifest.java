package com.aidefenselayer.model;

import java.util.List;

public record SystemManifest(
        List<String> systemInstructions,
        List<String> allowedTools,
        List<String> availableMcpServers,
        List<String> boundaryRules
) {
    public SystemManifest {
        systemInstructions = systemInstructions == null ? List.of() : List.copyOf(systemInstructions);
        allowedTools = allowedTools == null ? List.of() : List.copyOf(allowedTools);
        availableMcpServers = availableMcpServers == null ? List.of() : List.copyOf(availableMcpServers);
        boundaryRules = boundaryRules == null ? List.of() : List.copyOf(boundaryRules);
    }
}
