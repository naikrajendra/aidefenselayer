package com.aidefenselayer.policy;

import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

@Service
public class PolicyDecisionService {

    public boolean canAccessTool(String userIntent, Set<String> userRoles, Set<String> userPermissions) {
        String normalizedIntent = normalize(userIntent);
        Set<String> roles = safeSet(userRoles);
        Set<String> permissions = safeSet(userPermissions);

        if (normalizedIntent.contains("admin") && !roles.contains("ADMIN")) {
            return false;
        }

        if (normalizedIntent.contains("system") && !permissions.contains("SYSTEM_ACCESS")) {
            return false;
        }

        if (normalizedIntent.contains("delete") && !permissions.contains("WRITE") && !permissions.contains("ADMIN")) {
            return false;
        }

        if (normalizedIntent.contains("run tool") || normalizedIntent.contains("execute tool") || normalizedIntent.contains("mcp")) {
            return permissions.contains("TOOL_ACCESS") || permissions.contains("SYSTEM_ACCESS");
        }

        return true;
    }

    public boolean isAllowedAction(String action, Set<String> userRoles, Set<String> userPermissions) {
        String normalizedAction = normalize(action);
        Set<String> roles = safeSet(userRoles);
        Set<String> permissions = safeSet(userPermissions);

        if (normalizedAction.contains("admin") && !roles.contains("ADMIN")) {
            return false;
        }

        if (normalizedAction.contains("secret") && !permissions.contains("SYSTEM_ACCESS") && !permissions.contains("READ_SECRET")) {
            return false;
        }

        if ((normalizedAction.contains("write") || normalizedAction.contains("delete")) && !permissions.contains("WRITE") && !permissions.contains("ADMIN")) {
            return false;
        }

        return true;
    }

    private Set<String> safeSet(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        return new HashSet<>(values.stream().map(v -> v == null ? "" : v.trim().toUpperCase(Locale.ROOT)).toList());
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
