package com.aidefenselayer.model;

import com.aidefenselayer.domain.ExecutionPhase;
import com.aidefenselayer.domain.PayloadType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DefenseRequest(
        @NotNull ExecutionPhase executionPhase,
        @NotNull PayloadType payloadType,
        @NotBlank String content,
        @NotNull UserContext userContext,
        SystemManifest systemManifest
) {
}
