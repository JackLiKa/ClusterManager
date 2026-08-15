<div align="center">

# MQCluster

### Local-first MQ cluster learning platform — simulate a real RocketMQ cluster in your browser, on a single machine.

[![CI](https://github.com/JackLiKa/MQCluster/actions/workflows/ci.yml/badge.svg)](https://github.com/JackLiKa/MQCluster/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](./LICENSE)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-6DB33F.svg)](https://spring.io/projects/spring-boot)
[![Next.js](https://img.shields.io/badge/Next.js-16-black.svg)](https://nextjs.org/)
[![React](https://img.shields.io/badge/React-19-61DAFB.svg)](https://react.dev/)
[![RocketMQ](https://img.shields.io/badge/RocketMQ-4.9.8-D77916.svg)](https://rocketmq.apache.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-strict-3178C6.svg)](https://www.typescriptlang.org/)

</div>

---

> Spin up a **real, embedded Apache RocketMQ** cluster — NameServer, Master, Slave — right inside your laptop.
> Watch the topology come alive, produce and consume messages, observe replication, and learn how a distributed
> message broker really works. **No Docker, no cloud, no cluster of machines required.**

MQCluster is a local-first learning platform designed for university students and self-learners who want to
understand RocketMQ cluster topology, message models, and day-to-day operations without provisioning
infrastructure. The backend embeds a genuine Apache RocketMQ 4.9.8 runtime (in-process NameServer + Broker),
and the Next.js frontend gives you an interactive dashboard with topology visualization, a message workbench,
and live monitoring metrics.

---

## Screenshots

> Place dashboard screenshots here.

| Dashboard | Topology | Message Workbench |
| :---: | :---: | :---: |
| ![Dashboard](docs/screenshots/dashboard.png) | ![Topology](docs/screenshots/topology.png) | ![Messages](docs/screenshots/messages.png) |

---

## Features

- **Embedded real RocketMQ** — runs an actual Apache RocketMQ 4.9.8 NameServer + Broker in-process. No fake mocks, no Docker.
- **Topology visualization** — interactive ECharts graph showing NameServer, Master, and Slave nodes and their relationships.
- **Message simulation** — produce and consume real messages through the embedded broker; watch delivery succeed in real time.
- **Monitoring metrics** — live CPU, memory, and network I/O charts for each node, streamed over STOMP/WebSocket.
- **Node lifecycle management** — start, stop, and restart individual nodes; observe cluster state transitions.
- **Master–Slave replication** — run a Master and Slave simultaneously and see replication in action.
- **Activity log & audit trail** — every operation is recorded and pushed to the UI in real time via STOMP.
- **Learning guide** — built-in guided walkthroughs that explain cluster concepts as you interact.
- **Zero external dependencies** — one command for the backend, one for the frontend, open your browser. That's it.

---

## Quick Start

### Prerequisites

| Tool | Version | Notes |
| --- | --- | --- |
| Java (JDK) | 17+ | Required for the Spring Boot backend |
| Maven | 3.9+ | Or use the bundled `mvnw` wrapper |
| Node.js | 20+ | Required for the Next.js frontend |
| npm | 10+ | Ships with Node.js |

### 1. Start the backend

```bash
cd java
./mvnw spring-boot:run        # Windows: .\mvnw.cmd spring-boot:run
```

The backend starts on **http://localhost:8088**.

### 2. Start the frontend

```bash
cd next
npm install
npm run dev
```

The frontend starts on **http://localhost:3000** and proxies `/api` and `/ws` requests to the backend.

### 3. Open your browser

Navigate to **http://localhost:3000** — you'll see the MQCluster dashboard with a 3-node topology
(NameServer + Master + Slave), all initially stopped. Click **Start** on any node to bring it to life.

---

## Project Structure

```
MQCluster/
├── java/                      # Spring Boot 4.1.0 backend (port 8088)
│   ├── src/main/java/com/example/clustermanager/
│   │   ├── api/               # REST controllers, DTOs, WebSocket config, exception handling
│   │   ├── application/       # Use-case orchestration & provider selection
│   │   ├── core/              # Domain model & ports (zero external dependencies)
│   │   └── infrastructure/    # Adapter implementations (embedded RocketMQ + admin)
│   ├── src/main/resources/
│   │   └── application.properties
│   ├── src/test/              # JUnit 5 + AssertJ tests
│   └── pom.xml                # Maven build
├── next/                      # Next.js 16 frontend (port 3000)
│   ├── src/
│   │   ├── app/               # App Router pages (dashboard, learning guide)
│   │   ├── components/        # Shared UI (topology graph, message workbench, metrics)
│   │   ├── hooks/             # Custom React hooks (STOMP stream subscriptions)
│   │   ├── lib/               # API client & utilities
│   │   └── types/             # TypeScript type definitions
│   ├── next.config.ts         # API proxy config (/api → localhost:8088)
│   └── .env.local             # Environment variables
├── docs/                      # Documentation
│   └── ARCHITECTURE.md        # Architecture deep-dive
├── LICENSE                    # Apache 2.0
├── CONTRIBUTING.md            # Contribution guidelines
├── SECURITY.md                # Security policy
└── README.md                  # This file
```

---

## Configuration

### Backend (`java/src/main/resources/application.properties`)

| Property | Default | Description |
| --- | --- | --- |
| `server.port` | `8088` | Backend HTTP port |
| `cluster.cors.allowed-origins` | `http://localhost:3000` | Allowed frontend origin (CORS) |
| `cluster.pseudo.auto-start` | `false` | Seed nodes start in STOPPED state |
| `cluster.pseudo.work-dir` | `run/pseudo-cluster` | Embedded RocketMQ storage directory |

### Frontend (`next/.env.local`)

| Variable | Default | Description |
| --- | --- | --- |
| `BACKEND_URL` | `http://localhost:8088` | Backend address (rewrites proxy target) |

---

## Architecture

MQCluster follows a **Hexagonal Architecture (Ports & Adapters)** on the backend with a clean
front-end / back-end separation.

```
                    ┌──────────────────────────────────────────┐
                    │              Frontend (Next.js)           │
                    │   ECharts topology · Message workbench    │
                    │   Metrics · STOMP real-time streams       │
                    └───────────────┬──────────────────────────┘
                          REST /api │ /ws STOMP
                    ┌───────────────▼──────────────────────────┐
                    │               API Layer                   │
                    │   REST controllers · WebSocket · DTOs     │
                    ├──────────────────────────────────────────┤
                    │            Application Layer              │
                    │   Use-case orchestration · Provider       │
                    │   selection by (mode, middleware)         │
                    ├──────────────────────────────────────────┤
                    │              Core Domain                  │
                    │   Cluster · Node · Message models         │
                    │   Port interfaces (zero deps)             │
                    ├──────────────────────────────────────────┤
                    │          Infrastructure Layer             │
                    │   Embedded RocketMQ adapter (pseudo)      │
                    │   RocketMQ admin adapter (real)           │
                    └──────────────────────────────────────────┘
```

- **Core** — pure domain model and port interfaces with zero external dependencies.
- **Application** — orchestrates use cases and selects the right adapter via `ClusterProviderRegistry`.
- **Infrastructure / pseudo** — runs a real embedded RocketMQ runtime (in-process NameServer + Broker).
- **Infrastructure / rocketmq** — connects to an external RocketMQ cluster via the admin API.

See **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** for the full deep-dive.

---

## Testing

### Backend

```bash
cd java
./mvnw clean verify            # Full build + run all tests
```

The test suite uses JUnit 5 + AssertJ + Spring Boot Test and covers domain logic, application
orchestration, and adapter behavior.

### Frontend

```bash
cd next
npx tsc --noEmit               # Type-check
npm run lint                   # ESLint
npm run build                  # Production build
```

---

## Contributing

Contributions are welcome! Please read our **[Contributing Guidelines](CONTRIBUTING.md)** to get started.

If you find a security issue, please follow the **[Security Policy](SECURITY.md)** — do **not** open a public issue.

---

## License

This project is licensed under the **Apache License 2.0** — see [LICENSE](LICENSE) for the full text.

---

## Acknowledgements

- [Apache RocketMQ](https://rocketmq.apache.org/) — the distributed messaging and streaming platform that powers the embedded runtime.
- [Spring Boot](https://spring.io/projects/spring-boot) — the application framework behind the backend.
- [Next.js](https://nextjs.org/) & [React](https://react.dev/) — the frontend framework and UI library.
- [ECharts](https://echarts.apache.org/) — the charting and visualization library for topology graphs.
- Everyone in the open-source community who makes learning distributed systems accessible.

---

## 中文文档

本项目同时提供中文文档：

- **[架构文档（中文）](docs/ARCHITECTURE.md)** — 六边形架构、前后端分离、嵌入式 RocketMQ 详解
- **[贡献指南（中文）](CONTRIBUTING.md)** — 如何参与开发、提交 PR

---

<div align="center">

**[⬆ Back to top](#mqcluster)**

Made with care for learners of distributed message systems.

</div>
