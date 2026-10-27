package com.aidefenselayer.service;

import com.aidefenselayer.domain.Decision;
import com.aidefenselayer.domain.ExecutionPhase;
import com.aidefenselayer.domain.PayloadType;
import com.aidefenselayer.model.DefenseRequest;
import com.aidefenselayer.model.DefenseResponse;
import com.aidefenselayer.model.SystemManifest;
import com.aidefenselayer.model.Telemetry;
import com.aidefenselayer.model.UserContext;
import com.aidefenselayer.proto.AiDefenseServiceGrpc;
import com.aidefenselayer.proto.DefenseRequestProto;
import com.aidefenselayer.proto.DefenseResponseProto;
import com.aidefenselayer.proto.SystemManifestProto;
import com.aidefenselayer.proto.TelemetryProto;
import com.aidefenselayer.proto.UserContextProto;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@GrpcService
public class DefenseGrpcService extends AiDefenseServiceGrpc.AiDefenseServiceImplBase {

    private final DefenseEvaluationService defenseEvaluationService;

    public DefenseGrpcService(DefenseEvaluationService defenseEvaluationService) {
        this.defenseEvaluationService = defenseEvaluationService;
    }

    @Override
    public void evaluate(DefenseRequestProto request, StreamObserver<DefenseResponseProto> responseObserver) {
        DefenseResponse response = defenseEvaluationService.evaluate(toDomainRequest(request));
        responseObserver.onNext(toProto(response));
        responseObserver.onCompleted();
    }

    private DefenseRequest toDomainRequest(DefenseRequestProto request) {
        UserContextProto userContextProto = request.getUserContext();
        UserContext userContext = new UserContext(
                userContextProto.getUserId(),
                userContextProto.getRolesList().stream().collect(Collectors.toSet()),
                userContextProto.getPermissionsList().stream().collect(Collectors.toSet()),
                userContextProto.getSessionId()
        );

        SystemManifest manifest = null;
        if (request.hasSystemManifest()) {
            SystemManifestProto systemManifestProto = request.getSystemManifest();
            manifest = new SystemManifest(
                    systemManifestProto.getSystemInstructionsList(),
                    systemManifestProto.getAllowedToolsList(),
                    systemManifestProto.getAvailableMcpServersList(),
                    systemManifestProto.getBoundaryRulesList()
            );
        }

        return new DefenseRequest(
                ExecutionPhase.valueOf(request.getExecutionPhase().toUpperCase(Locale.ROOT)),
                PayloadType.valueOf(request.getPayloadType().toUpperCase(Locale.ROOT)),
                request.getContent(),
                userContext,
                manifest
        );
    }

    private DefenseResponseProto toProto(DefenseResponse response) {
        Telemetry telemetry = response.telemetry();
        TelemetryProto telemetryProto = TelemetryProto.newBuilder()
                .setProcessingTimeMs(telemetry.processingTimeMs())
                .setInputTokens(telemetry.inputTokens())
                .setOutputTokens(telemetry.outputTokens())
                .putAllOperationalMetadata(telemetry.operationalMetadata().entrySet().stream()
                        .collect(Collectors.toMap(
                                entry -> String.valueOf(entry.getKey()),
                                entry -> String.valueOf(entry.getValue()),
                                (left, right) -> right,
                                java.util.LinkedHashMap::new
                        )))
                .build();

        return DefenseResponseProto.newBuilder()
                .setDecision(response.decision().name())
                .setProcessedContent(response.processedContent())
                .setRiskScore(response.riskScore())
                .addAllTriggeredScanners(response.triggeredScanners())
                .setTelemetry(telemetryProto)
                .build();
    }
}
