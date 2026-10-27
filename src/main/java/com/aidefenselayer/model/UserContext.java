package com.aidefenselayer.model;

import java.util.Set;

public record UserContext(
        String userId,
        Set<String> roles,
        Set<String> permissions,
        String sessionId
) {
    public UserContext {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }
}
