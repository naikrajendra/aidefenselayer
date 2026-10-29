# AI Defense Layer

This Spring Boot microservice provides a defense-in-depth layer for AI applications.

## Features

- Pre-inference prompt injection and PII detection
- Post-inference output safety scanning
- Structured decision contract for gateways and orchestrators
- Async Kafka event publishing for telemetry and security analytics
- gRPC service for low-latency inline validation
- Role- and permission-aware policy checks

## Quick Start

```bash
mvn clean package
java -jar target/ai-defense-layer-0.0.1-SNAPSHOT.jar
```

## Mermaid C4 Diagrams

### System Context

```mermaid
C4Context
    title AI Defense Layer system context

    Person(user, "End User / App Client")
    System_Boundary(ai_gateway, "AI Platform") {
        System(gateway, "AI Gateway / Orchestrator")
        System(defense, "AI Defense Layer")
        System(rag, "RAG / Retrieval Layer")
    }
    System(llm, "External LLM / Model Endpoint")
    System(kafka, "Telemetry / Risk Event Bus")

    Rel(user, gateway, "Requests and tool calls")
    Rel(gateway, defense, "Evaluates prompts and outputs")
    Rel(gateway, rag, "Retrieves context and memory")
    Rel(gateway, llm, "Sends prompt / model output")
    Rel(defense, kafka, "Publishes risk telemetry")
    Rel(defense, gateway, "Returns ALLOW / BLOCK / REDACT / FLAG")
    Rel(rag, llm, "Grounded context retrieval and synthesis")
```

### Container Diagram

```mermaid
C4Container
    title AI Defense Layer container view

    System_Boundary(platform, "AI platform") {
        Container(api, "API Gateway", "Spring Boot / REST", "Ingress for app requests")
        Container(defense, "AI Defense Layer", "Java Spring Boot", "Fast inline scanners and policy enforcement")
        Container(grpc, "gRPC Gateway Adapter", "gRPC Service", "Low-latency validation for sidecars and services")
        Container(kafka, "Risk Event Broker", "Kafka", "Asynchronous risk telemetry and downstream analysis")
        Container(rag, "RAG Service", "Vector + retrieval", "Grounds LLM responses with retrieved context")
        Container(llm, "LLM Runtime", "OpenAI / Azure OpenAI / model endpoint", "Generates inference output")
    }

    Rel(api, defense, "POST /api/defense/evaluate")
    Rel(defense, grpc, "Per-request inline checks")
    Rel(defense, rag, "Checks retrieved context and policy boundaries")
    Rel(defense, llm, "Validates prompt/output safety before and after inference")
    Rel(defense, kafka, "Publishes risk events")
    Rel(rag, llm, "Query and answer generation")
```

### Component Diagram

```mermaid
C4Component
    title AI Defense Layer component view

    Container_Boundary(defense_container, "AI Defense Layer") {
        Component(controller, "DefenseController", "REST API", "Accepts evaluation requests")
        Component(service, "DefenseEvaluationService", "Java Service", "Runs PRE_INFERENCE and POST_INFERENCE rules")
        Component(policy, "PolicyDecisionService", "RBAC / Tool Policy", "Checks user roles, permissions, and tool access")
        Component(plugins, "Scanner Chain", "Heuristics + Regex + NLI", "Prompt injection, PII, toxicity, hallucination checks")
        Component(async, "AsyncRiskEventPublisher", "Kafka Producer", "Publishes non-blocking telemetry")
        Component(grpcSvc, "DefenseGrpcService", "gRPC Service", "Handles low-latency eval calls")
        Component(telemetry, "Telemetry / Observability", "Metrics + Logs", "Risk score, latency, and operational metadata")
    }

    Rel(controller, service, "Evaluates request payload")
    Rel(service, policy, "Enforces user intent and tool scope")
    Rel(service, plugins, "Invokes scanners")
    Rel(service, async, "Publishes telemetry asynchronously")
    Rel(async, telemetry, "Risk event stream")
    Rel(grpcSvc, service, "Calls same evaluation engine")
    Rel(service, telemetry, "Logs metrics and trigger metadata")
```

### Sequence Diagram: Pre- and Post-Inference Defense Flow

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Gateway as AI Gateway
    participant Defense as AI Defense Layer
    participant Policy as PolicyDecisionService
    participant RAG as RAG / Retrieval Layer
    participant LLM as LLM
    participant Kafka as Kafka Risk Topic

    User->>Gateway: Submit prompt / tool request
    Gateway->>Defense: PRE_INFERENCE evaluation
    Defense->>Policy: Check RBAC + tool scope
    Defense->>Defense: Prompt injection + PII + token checks
    alt Request blocked or redacted
        Defense-->>Gateway: BLOCK / REDACT
        Gateway-->>User: Security policy message
    else Request allowed
        Gateway->>RAG: Retrieve grounded context
        RAG-->>Gateway: Context snippets
        Gateway->>LLM: Prompt + context
        LLM-->>Gateway: Model output
        Gateway->>Defense: POST_INFERENCE evaluation
        Defense->>Defense: System prompt leak / toxicity / hallucination / exfiltration checks
        alt Output unsafe
            Defense-->>Gateway: BLOCK / REDACT
        else Output safe
            Defense-->>Gateway: ALLOW
        end
        Defense->>Kafka: Publish risk event asynchronously
    end
```

### Deployment Diagram: AWS / Azure / GCP

```mermaid
C4Deployment
    title AI Defense Layer deployment across cloud providers

    Deployment_Node(cloud, "Cloud Environment") {
        Deployment_Node(aws, "AWS") {
            Container(aws_app, "ECS / Fargate", "Java Spring Boot", "AI Defense Layer")
            Container(aws_kafka, "MSK / Kafka", "Kafka", "Risk telemetry")
        }

        Deployment_Node(azure, "Azure") {
            Container(az_app, "Azure Container Apps", "Java Spring Boot", "AI Defense Layer")
            Container(az_kafka, "Azure Event Hubs / Kafka-compatible broker", "Kafka-like broker", "Risk telemetry")
        }

        Deployment_Node(gcp, "GCP") {
            Container(gcp_app, "Cloud Run", "Java Spring Boot", "AI Defense Layer")
            Container(gcp_kafka, "Pub/Sub / Kafka-compatible pipeline", "Messaging", "Risk telemetry")
        }
    }

    Rel(aws_app, aws_kafka, "Publishes risk events")
    Rel(az_app, az_kafka, "Publishes risk events")
    Rel(gcp_app, gcp_kafka, "Publishes risk events")

    Person(user, "Gateway / App / API Consumer")
    Rel(user, aws_app, "Calls via AWS-hosted app")
    Rel(user, az_app, "Calls via Azure-hosted app")
    Rel(user, gcp_app, "Calls via GCP-hosted app")
```

## Docker

```bash
docker build -t ai-defense-layer .
docker run -p 8080:8080 -p 9090:9090 \
  -e SERVER_PORT=8080 \
  -e GRPC_SERVER_PORT=9090 \
  -e KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
  -e OPENAI_API_KEY=your-key \
  ai-defense-layer
```

## Deployment Notes

Example infrastructure templates are included under:

- `deploy/aws/task-definition.json`
- `deploy/azure/container-app.yaml`
- `deploy/gcp/cloudrun.yaml`

These are starter templates and must be tailored to your actual cloud accounts, infrastructure, Kafka brokers, and secret management configuration.
