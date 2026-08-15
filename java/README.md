# MQCluster

[![CI](https://github.com/JackLiKa/MQCluster/actions/workflows/ci.yml/badge.svg)](https://github.com/JackLiKa/MQCluster/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-17-blue.svg)](https://openjdk.org/projects/jdk/17/)
[![Next.js](https://img.shields.io/badge/Next.js-16-black.svg)](https://nextjs.org/)
[![RocketMQ](https://img.shields.io/badge/RocketMQ-4.9.8-D77310.svg)](https://rocketmq.apache.org/)

A local-first MQ cluster learning platform — simulate real RocketMQ clusters in your browser on a single machine.

MQCluster unifies `PSEUDO` and `REAL` cluster modes in one web UI: you can visualize topology, start/stop nodes, stream logs and metrics, simulate message flows, and register manual services side by side with virtual TAP nodes.

> [中文文档](docs/zh/README.md) | [Architecture](docs/ARCHITECTURE.md) | [Contributing](docs/CONTRIBUTING.md)

## Features

- **Unified cluster abstraction** — switch between `PSEUDO` and `REAL` modes from the top navigation.
- **RocketMQ topology** — visualize NameServer, Broker Master, Broker Slave, and Proxy nodes with ECharts.
- **Node lifecycle** — start, stop, restart, and delete services directly from the operations panel.
- **Manual service registry** — register real IP / NameServer entries or virtual IPs for mixed clusters.
- **Host + virtual nodes** — in pseudo mode, mix local `HOST` RocketMQ nodes with `VIRTUAL` TAP nodes.
- **Message workbench** — simulate produce/consume flows; host-backed nodes validate against a real RocketMQ instance.
- **Live telemetry** — WebSocket streams push metrics and logs to the dashboard.
- **Single runnable artifact** — `mvn spring-boot:run` builds the frontend and serves the SPA on `http://localhost:8080`.

## Quick Start

### Prerequisites

- Java 17
- Node.js 20+ and npm
- (Optional) A local RocketMQ NameServer + Broker if you want to validate host-backed message flow

### Run in development mode

```powershell
# On Windows
.\mvnw.cmd spring-boot:run

# On macOS / Linux
./mvnw spring-boot:run
```

Then open `http://localhost:8080/`.

The Maven build will automatically compile the Vue frontend and copy it into `target/classes/static`, so the full UI is available at the backend port.

### Run with frontend hot reload

```powershell
cd frontend
npm install
npm run dev
```

The Vite dev server runs on `http://localhost:5173` and proxies `/api`, `/ws`, and `/guide` to `http://localhost:8080`.

### Build and run the fat JAR

```powershell
# Windows
.\mvnw.cmd clean package
java -jar target\mqcluster-0.1.0-SNAPSHOT.jar

# macOS / Linux
./mvnw clean package
java -jar target/mqcluster-0.1.0-SNAPSHOT.jar
```

## Configuration

Key properties in `src/main/resources/application.properties`:

| Property | Default | Description |
| --- | --- | --- |
| `server.port` | `8080` | HTTP port for the backend and SPA. |
| `cluster.pseudo.cluster-id` | `local-lab` | Identifier for the pseudo cluster. |
| `cluster.pseudo.cidr` | `10.77.0.0/24` | Virtual network CIDR for TAP nodes. |
| `cluster.pseudo.auto-start` | `false` | Whether to start seeded pseudo nodes automatically. |
| `cluster.pseudo.work-dir` | `run/pseudo-cluster` | Working directory for pseudo node runtime. |
| `cluster.rocketmq.cluster-id` | `rocketmq-demo` | Identifier for the real cluster view. |
| `cluster.rocketmq.name-servers` | `192.168.50.78:9876` | NameServer list for host-backed message validation. |
| `cluster.stream.publish-interval-ms` | `5000` | Metrics/log WebSocket push interval. |

## Architecture

The backend follows a hexagonal architecture:

- `core` — domain models and stable ports (`IClusterProvider`, `IVirtualNetwork`, `IServiceRegistry`, ...).
- `application` — provider selection (`ClusterProviderRegistry`) and orchestration (`ClusterFacadeService`).
- `infrastructure` — provider implementations for pseudo clusters and RocketMQ.
- `api` — REST controllers, DTOs, WebSocket config, and SPA forwarding.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the full layout and design notes.

## Testing

```powershell
# Run backend tests
.\mvnw.cmd clean test

# Run frontend type-check and build
cd frontend
npm run build
```

## Contributing

We welcome contributions! Please read [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md) for branch naming, commit style, and the pull request process.

## Security

If you discover a security vulnerability, please follow the instructions in [docs/SECURITY.md](docs/SECURITY.md) to report it privately.

## License

MQCluster is licensed under the [Apache License 2.0](LICENSE).

## Acknowledgments

- [Spring Boot](https://spring.io/projects/spring-boot)
- [Vue.js](https://vuejs.org/)
- [Element Plus](https://element-plus.org/)
- [ECharts](https://echarts.apache.org/)
- [Apache RocketMQ](https://rocketmq.apache.org/)
