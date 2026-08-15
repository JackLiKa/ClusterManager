# MQCluster Architecture

## Directory layout

```text
.
├─ src/main/java/com/example/clustermanager
│  ├─ api
│  │  ├─ config
│  │  ├─ controller
│  │  └─ dto
│  ├─ application
│  │  ├─ model
│  │  └─ service
│  ├─ core
│  │  ├─ model
│  │  └─ port
│  └─ infrastructure
│     ├─ pseudo
│     └─ rocketmq
├─ frontend
│  └─ src
│     ├─ api
│     ├─ components
│     ├─ composables
│     ├─ types
│     └─ views
├─ docs
│  ├─ ARCHITECTURE.md
│  ├─ CONTRIBUTING.md
│  ├─ CODE_OF_CONDUCT.md
│  ├─ SECURITY.md
│  └─ zh
│     ├─ README.md
│     └─ manual.md
├─ pom.xml
└─ README.md
```

## Why hexagonal?

The backend is organized around **hexagonal (ports and adapters) architecture**:

- **`core`** owns the domain models and stable contracts (ports). It has no external dependencies.
- **`application`** orchestrates use cases and selects the right adapter through `ClusterProviderRegistry`.
- **`infrastructure`** contains concrete adapters: the pseudo cluster runtime and the RocketMQ admin client.
- **`api`** exposes REST endpoints and WebSocket streams, and serves the Vue SPA.

This split makes it straightforward to add new middleware (for example, Kafka) by implementing the same `IClusterProvider` port.

## Core ports

| Port | Responsibility |
| --- | --- |
| `IClusterProvider` | Aggregates all other ports; identified by `ClusterMode` + `MiddlewareType`. |
| `ITopologyReader` | Load cluster topology. |
| `INodeManager` | Start/stop/restart nodes. |
| `IMonitoringGateway` | Load metrics and logs. |
| `IMessageWorkbench` | Simulate message produce/consume. |
| `IServiceRegistry` | Register/delete manual services. |
| `IVirtualNetwork` | Virtual segment and TAP attach/detach/isolate. |

## Application services

- `ClusterProviderRegistry` — selects the right `IClusterProvider` based on mode and middleware.
- `ClusterFacadeService` — central orchestrator for topology, metrics, logs, node operations, message simulation, and service registration.

## Infrastructure adapters

### Pseudo cluster provider

`infrastructure/pseudo/PseudoClusterProvider.java`

- Mode: `PSEUDO`, Middleware: `ROCKETMQ`.
- Seeds three nodes on first use: `rmq-ns-01`, `rmq-broker-m-01`, `rmq-broker-s-01`.
- Nodes can be:
  - **VIRTUAL** — allocated a virtual IP from the CIDR pool, isolated via `IVirtualNetwork`, backed by a local JVM process (`LocalPseudoNodeRuntime` running `PseudoNodeAgent`).
  - **HOST** — bound to a real address (for example, `127.0.0.1:10911`), no local process started.
- Supports both purely local pseudo message flow and host-backed RocketMQ flow when a HOST NameServer is registered.
- Uses `PseudoRocketMqBridge` for real RocketMQ produce/consume via `DefaultMQProducer` / `DefaultMQPushConsumer`.

### RocketMQ cluster provider

`infrastructure/rocketmq/RocketMqClusterProvider.java`

- Mode: `REAL`, Middleware: `ROCKETMQ`.
- Wraps `RocketMqAdminAdapter`.
- Allows manual service registration/deletion; manual nodes merge into topology, metrics, logs, and message simulation.

## Frontend

- `frontend/src/views/ClusterOverviewPage.vue` — main dashboard; holds cluster selection state and wires WebSocket streams.
- `frontend/src/components/ClusterTopologyCard.vue` — ECharts graph rendering nodes and links.
- `frontend/src/components/MessageWorkbenchCard.vue` — topic-based message send/validate form.
- `frontend/src/components/OperationsPanel.vue` — KPI strip, node action table, service registration form, and activity log.
- `frontend/src/composables/useClusterStreams.ts` — STOMP/SockJS consumer with reconnection logic.

## Startup note

`spring-boot:run` builds the frontend during the `generate-resources` phase and copies `frontend/dist` into `target/classes/static`. Therefore both development mode and the packaged JAR serve the full SPA from `http://localhost:8080/`.
