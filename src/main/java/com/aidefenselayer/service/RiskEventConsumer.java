package com.aidefenselayer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class RiskEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(RiskEventConsumer.class);

    @KafkaListener(topics = "ai-defense-risk-events", groupId = "ai-defense-consumer-group")
    public void consume(String message) {
        log.info("Consumed AI defense risk event: {}", message);
    }
}
