package com.aidefenselayer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "ai-defense")
public class AiDefenseProperties {

    private long latencyBudgetMs = 2000L;
    private long tokenBudget = 12000L;
    private List<String> injectionPatterns = List.of(
            "ignore previous instructions",
            "override system",
            "developer mode",
            "jailbreak",
            "system prompt",
            "reveal hidden instructions"
    );
    private List<String> toxicTerms = List.of(
            "kill",
            "bomb",
            "weapon",
            "hate",
            "racial slur"
    );
    private String policyMessage = "I cannot fulfill this request based on security policies.";

    public long getLatencyBudgetMs() {
        return latencyBudgetMs;
    }

    public void setLatencyBudgetMs(long latencyBudgetMs) {
        this.latencyBudgetMs = latencyBudgetMs;
    }

    public long getTokenBudget() {
        return tokenBudget;
    }

    public void setTokenBudget(long tokenBudget) {
        this.tokenBudget = tokenBudget;
    }

    public List<String> getInjectionPatterns() {
        return injectionPatterns;
    }

    public void setInjectionPatterns(List<String> injectionPatterns) {
        this.injectionPatterns = injectionPatterns == null ? new ArrayList<>() : injectionPatterns;
    }

    public List<String> getToxicTerms() {
        return toxicTerms;
    }

    public void setToxicTerms(List<String> toxicTerms) {
        this.toxicTerms = toxicTerms == null ? new ArrayList<>() : toxicTerms;
    }

    public String getPolicyMessage() {
        return policyMessage;
    }

    public void setPolicyMessage(String policyMessage) {
        this.policyMessage = policyMessage;
    }
}
