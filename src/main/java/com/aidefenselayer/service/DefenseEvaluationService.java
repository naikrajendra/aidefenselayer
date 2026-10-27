package com.aidefenselayer.service;

import com.aidefenselayer.config.AiDefenseProperties;
import com.aidefenselayer.domain.Decision;
import com.aidefenselayer.domain.ExecutionPhase;
import com.aidefenselayer.domain.PayloadType;
import com.aidefenselayer.model.DefenseRequest;
import com.aidefenselayer.model.DefenseResponse;
import com.aidefenselayer.model.Telemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class DefenseEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(DefenseEvaluationService.class);
    private static final Set<String> DEFAULT_BOUNDARY_KEYWORDS = Set.of(
            "admin",
            "secret",
            "Internal",
            "database schema",
            "prod",
            "private key",
            "system prompt"
    );

    private final AiDefenseProperties aiDefenseProperties;
    private final AsyncRiskEventPublisher asyncRiskEventPublisher;

    public DefenseEvaluationService(AiDefenseProperties aiDefenseProperties, AsyncRiskEventPublisher asyncRiskEventPublisher) {
        this.aiDefenseProperties = aiDefenseProperties;
        this.asyncRiskEventPublisher = asyncRiskEventPublisher;
    }

    public DefenseResponse evaluate(DefenseRequest request) {
        long started = System.nanoTime();
        String originalContent = Objects.requireNonNullElse(request.content(), "");
        String normalized = normalizeContent(originalContent);

        List<String> triggered = new ArrayList<>();
        double riskScore = 0.0;
        String processed = normalized;

        if (request.executionPhase() == ExecutionPhase.PRE_INFERENCE) {
            riskScore = evaluatePreInference(request, normalized, processed, triggered);
            processed = applyPreInferenceSanitization(processed, triggered);
        } else {
            riskScore = evaluatePostInference(request, normalized, processed, triggered);
            processed = applyPostInferenceSanitization(processed, triggered);
        }

        Decision decision = resolveDecision(riskScore, triggered);
        if (decision == Decision.BLOCK) {
            processed = aiDefenseProperties.getPolicyMessage();
        } else if (decision == Decision.REDACT && processed.isBlank()) {
            processed = aiDefenseProperties.getPolicyMessage();
        }

        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        int inputTokens = estimateTokens(normalized);
        int outputTokens = estimateTokens(processed);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("execution_phase", request.executionPhase());
        metadata.put("payload_type", request.payloadType());
        metadata.put("latency_budget_ms", aiDefenseProperties.getLatencyBudgetMs());
        metadata.put("latency_actual_ms", elapsedMs);
        metadata.put("user_id", request.userContext().userId());
        metadata.put("session_id", request.userContext().sessionId());
        metadata.put("async_risk_event_published", true);

        DefenseResponse response = new DefenseResponse(
                decision,
                processed,
                round(riskScore),
                triggered,
                new Telemetry(elapsedMs, inputTokens, outputTokens, metadata)
        );

        asyncRiskEventPublisher.publishSecurityEvent(request, response);
        return response;
    }

    private double evaluatePreInference(DefenseRequest request, String text, String processed, List<String> triggered) {
        double score = 0.0;

        if (containsTrigger(text, aiDefenseProperties.getInjectionPatterns())) {
            triggered.add("PROMPT_INJECTION");
            score += 0.65;
        }

        if (containsSensitiveData(text)) {
            triggered.add("SENSITIVE_DATA_DETECTED");
            score += 0.25;
        }

        long tokenCount = estimateTokens(text);
        if (tokenCount > aiDefenseProperties.getTokenBudget()) {
            triggered.add("TOKEN_LIMIT_EXCEEDED");
            score += 0.70;
        }

        if (violatesIntentBoundary(request)) {
            triggered.add("INTENT_BOUNDARY_VIOLATION");
            score += 0.55;
        }

        if (request.systemManifest() != null && request.systemManifest().allowedTools() != null
                && !request.systemManifest().allowedTools().isEmpty() && request.payloadType() == PayloadType.PROMPT
                && containsToolScopeViolation(request)) {
            triggered.add("TOOL_SCOPE_VIOLATION");
            score += 0.50;
        }

        return Math.min(score, 0.99);
    }

    private double evaluatePostInference(DefenseRequest request, String text, String processed, List<String> triggered) {
        double score = 0.0;

        if (containsSensitiveLeak(text) || containsSystemPromptLeak(text)) {
            triggered.add("SYSTEM_PROMPT_LEAK");
            score += 0.85;
        }

        if (containsToxicTerm(text, aiDefenseProperties.getToxicTerms())) {
            triggered.add("TOXIC_OUTPUT");
            score += 0.60;
        }

        if (request.payloadType() == PayloadType.MODEL_OUTPUT && request.systemManifest() != null && !request.systemManifest().boundaryRules().isEmpty()) {
            for (String rule : request.systemManifest().boundaryRules()) {
                if (text.toLowerCase(Locale.ROOT).contains(rule.toLowerCase(Locale.ROOT))) {
                    triggered.add("BOUNDARY_RULE_VIOLATION");
                    score += 0.35;
                }
            }
        }

        if (request.payloadType() == PayloadType.MODEL_OUTPUT && request.systemManifest() != null
                && request.systemManifest().availableMcpServers() != null && !request.systemManifest().availableMcpServers().isEmpty()) {
            if (text.toLowerCase(Locale.ROOT).contains("database schema") || text.toLowerCase(Locale.ROOT).contains("internal host")) {
                triggered.add("EXFILTRATION_DETECTED");
                score += 0.55;
            }
        }

        if (request.payloadType() == PayloadType.MODEL_OUTPUT && request.systemManifest() != null) {
            boolean isFaithful = hasFaithfulnessOverlap(text, request.systemManifest());
            if (!isFaithful) {
                triggered.add("POTENTIAL_HALLUCINATION");
                score += 0.30;
            }
        }

        return Math.min(score, 0.99);
    }

    private String applyPreInferenceSanitization(String content, List<String> triggered) {
        String sanitized = content;
        if (triggered.contains("SENSITIVE_DATA_DETECTED")) {
            sanitized = maskSensitiveData(sanitized);
        }
        if (triggered.contains("PROMPT_INJECTION")) {
            sanitized = sanitized.replaceAll("(?i)(ignore previous instructions|override system|developer mode|jailbreak)", "[blocked instruction]");
        }
        if (triggered.contains("TOKEN_LIMIT_EXCEEDED")) {
            sanitized = sanitized.substring(0, Math.min(sanitized.length(), 1200));
        }
        return sanitized;
    }

    private String applyPostInferenceSanitization(String content, List<String> triggered) {
        String sanitized = content;

        if (triggered.contains("SYSTEM_PROMPT_LEAK") || triggered.contains("EXFILTRATION_DETECTED") || triggered.contains("TOXIC_OUTPUT")) {
            sanitized = aiDefenseProperties.getPolicyMessage();
        } else if (triggered.contains("BOUNDARY_RULE_VIOLATION") || triggered.contains("POTENTIAL_HALLUCINATION")) {
            sanitized = "The response was flagged for policy review and is being redacted.";
        }

        if (triggered.contains("SENSITIVE_DATA_DETECTED")) {
            sanitized = maskSensitiveData(sanitized);
        }

        return sanitized;
    }

    private boolean violatesIntentBoundary(DefenseRequest request) {
        if (request.userContext() == null || request.userContext().roles() == null || request.userContext().permissions() == null) {
            return false;
        }

        String low = normalizeContent(request.content()).toLowerCase(Locale.ROOT);
        Set<String> roles = request.userContext().roles();
        Set<String> permissions = request.userContext().permissions();

        boolean roleMismatch = !roles.isEmpty() && low.contains("admin") && !roles.contains("ADMIN");
        boolean permissionMismatch = !permissions.isEmpty() && low.contains("delete") && !permissions.contains("WRITE");
        boolean boundaryMatch = DEFAULT_BOUNDARY_KEYWORDS.stream().anyMatch(low::contains);

        return roleMismatch || permissionMismatch || (boundaryMatch && !permissions.contains("SYSTEM_ACCESS"));
    }

    private boolean containsToolScopeViolation(DefenseRequest request) {
        if (request.systemManifest() == null || request.systemManifest().allowedTools() == null) {
            return false;
        }

        String lower = normalizeContent(request.content()).toLowerCase(Locale.ROOT);
        for (String tool : request.systemManifest().allowedTools()) {
            if (lower.contains(tool.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }

        return lower.contains("run") || lower.contains("execute") || lower.contains("tool") || lower.contains("mcp");
    }

    private boolean hasFaithfulnessOverlap(String text, com.aidefenselayer.model.SystemManifest manifest) {
        if (manifest == null || manifest.systemInstructions() == null || manifest.systemInstructions().isEmpty()) {
            return true;
        }

        String lower = text.toLowerCase(Locale.ROOT);
        for (String instruction : manifest.systemInstructions()) {
            String normalizedInstruction = instruction.toLowerCase(Locale.ROOT);
            if (lower.contains(normalizedInstruction) || lower.contains(normalizedInstruction.replace(" ", ""))) {
                return true;
            }
        }

        return false;
    }

    private boolean containsTrigger(String text, List<String> patterns) {
        String normalized = text.toLowerCase(Locale.ROOT);
        for (String pattern : patterns) {
            if (normalized.contains(pattern.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsSensitiveData(String text) {
        return text.matches("(?s).*(\\b\\d{3}-\\d{2}-\\d{4}\\b|\\b(?:sk|sk_live|AKIA)[A-Za-z0-9_\\-]{10,}\\b|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}).*")
                || text.toLowerCase(Locale.ROOT).contains("medical record")
                || text.toLowerCase(Locale.ROOT).contains("passport number");
    }

    private boolean containsSensitiveLeak(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("system prompt")
                || lower.contains("secret key")
                || lower.contains("database schema")
                || lower.contains("internal tooling")
                || lower.contains("private key");
    }

    private boolean containsSystemPromptLeak(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("you are") && (lower.contains("developer") || lower.contains("system") || lower.contains("instruction"));
    }

    private boolean containsToxicTerm(String text, List<String> toxicTerms) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String term : toxicTerms) {
            if (lower.contains(term.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String maskSensitiveData(String text) {
        String masked = text.replaceAll("(?i)\\b\\d{3}-\\d{2}-\\d{4}\\b", "[REDACTED_SSN]");
        masked = masked.replaceAll("(?i)\\b(?:sk|sk_live|AKIA)[A-Za-z0-9_\\-]{10,}\\b", "[REDACTED_API_KEY]");
        masked = masked.replaceAll("(?i)\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b", "[REDACTED_EMAIL]");
        masked = masked.replaceAll("(?i)\\b(?:\\+?1[-.\\s]?)?(?:\\(?\\d{3}\\)?[-.\\s]?)\\d{3}[-.\\s]?\\d{4}\\b", "[REDACTED_PHONE]");
        return masked;
    }

    private Decision resolveDecision(double riskScore, List<String> triggered) {
        if (riskScore >= 0.85 || triggered.contains("SYSTEM_PROMPT_LEAK") || triggered.contains("TOKEN_LIMIT_EXCEEDED") || triggered.contains("PROMPT_INJECTION")) {
            return Decision.BLOCK;
        }
        if (riskScore >= 0.60 || triggered.contains("SENSITIVE_DATA_DETECTED") || triggered.contains("TOXIC_OUTPUT") || triggered.contains("EXFILTRATION_DETECTED")) {
            return Decision.REDACT;
        }
        if (riskScore >= 0.35 || !triggered.isEmpty()) {
            return Decision.FLAG;
        }
        return Decision.ALLOW;
    }

    private String normalizeContent(String content) {
        return content == null ? "" : content.replaceAll("\\s+", " ").trim();
    }

    private int estimateTokens(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Math.max(1, value.split("\\s+").length);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
