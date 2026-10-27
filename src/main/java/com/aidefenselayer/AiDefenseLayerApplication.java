package com.aidefenselayer;

import com.aidefenselayer.config.AiDefenseProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties(AiDefenseProperties.class)
public class AiDefenseLayerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiDefenseLayerApplication.class, args);
    }
}
