package com.aidefenselayer.service;

import com.aidefenselayer.model.DefenseRequest;
import com.aidefenselayer.model.DefenseResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class AsyncRiskEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AsyncRiskEventPublisher.class);
    private static final String RISK_EVENTS_TOPIC = "ai-defense-risk-events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public AsyncRiskEventPublisher() {
        this(null, new ObjectMapper());
    }

    public AsyncRiskEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    @Async
    public CompletableFuture<Void> publishSecurityEvent(DefenseRequest request, DefenseResponse response) {
        Map<String, Object> eventPayload = new LinkedHashMap<>();
        eventPayload.put("executionPhase", request.executionPhase());
        eventPayload.put("payloadType", request.payloadType());
        eventPayload.put("userId", request.userContext().userId());
        eventPayload.put("sessionId", request.userContext().sessionId());
        eventPayload.put("decision", response.decision());
        eventPayload.put("riskScore", response.riskScore());
        eventPayload.put("triggeredScanners", response.triggeredScanners());
        eventPayload.put("processingTimeMs", response.telemetry().processingTimeMs());

        try {
            String payload = objectMapper.writeValueAsString(eventPayload);
            if (kafkaTemplate != null) {
                kafkaTemplate.send(RISK_EVENTS_TOPIC, request.userContext().sessionId(), payload);
            }
            log.info("Publishing async risk event -> {}", payload);
        } catch (JsonProcessingException e) {
            log.warn("Unable to serialize risk event payload", e);
        }

        return CompletableFuture.completedFuture(null);
    }
}
