package com.aidefenselayer.controller;

import com.aidefenselayer.model.DefenseRequest;
import com.aidefenselayer.model.DefenseResponse;
import com.aidefenselayer.service.DefenseEvaluationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/defense")
public class DefenseController {

    private final DefenseEvaluationService defenseEvaluationService;

    public DefenseController(DefenseEvaluationService defenseEvaluationService) {
        this.defenseEvaluationService = defenseEvaluationService;
    }

    @PostMapping("/evaluate")
    public DefenseResponse evaluate(@Valid @RequestBody DefenseRequest request) {
        return defenseEvaluationService.evaluate(request);
    }

    @GetMapping("/health")
    public String health() {
        return "UP";
    }
}
