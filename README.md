<div align="center">

# MQCluster

### Local-first MQ cluster learning platform — simulate a real RocketMQ cluster in your browser, on a single machine.

[![CI](https://github.com/JackLiKa/MQCluster/actions/workflows/ci.yml/badge.svg)](https://github.com/JackLiKa/MQCluster/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](./LICENSE)
[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-6DB33F.svg)](https://spring.io/projects/spring-boot)
[![Next.js](https://img.shields.io/badge/Next.js-16-black.svg)](https://nextjs.org/)
[![React](https://img.shields.io/badge/React-19-61DAFB.svg)](https://react.dev/)
[![RocketMQ](https://img.shields.io/badge/RocketMQ-5.3.3-D77916.svg)](https://rocketmq.apache.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-strict-3178C6.svg)](https://www.typescriptlang.org/)

</div>

---

> Spin up a **real, embedded Apache RocketMQ** cluster — NameServer, Master, Slave — right inside your laptop.
> Watch the topology come alive, produce and consume messages, observe replication, and learn how a distributed
> message broker really works. **No Docker, no cloud, no cluster of machines required.**

MQCluster is a local-first learning platform designed for university students and self-learners who want to
understand RocketMQ cluster topology, message models, and day-to-day operations without provisioning
infrastructure. The backend embeds a genuine Apache RocketMQ 5.3.3 runtime (in-process NameServer + Broker),
and the Next.js frontend gives you an interactive dashboard with topology visualization, a message workbench,
and live monitoring metrics.

---

## Visual Dashboard

MQCluster provides an interactive web dashboard for visualizing and operating RocketMQ clusters:

- **Topology Graph** — Force-directed graph with drag-and-drop nodes. Node color reflects status (gray = stopped, green = running, yellow = degraded, red = failed). Node shape reflects role (circle = NameServer, rounded rect = Broker Master, diamond = Broker Slave). Link style reflects health (solid green = healthy, dashed gray = disconnected).
- **Message Workbench** — Send and consume real messages with built-in templates (JSON order, plain text, RocketMQ event, key-value, user behavior) or custom payloads. Placeholder substitution (`{index}`, `{timestamp}`, `{uuid}`, `{random}`, `{topic}`) makes it easy to generate realistic test data.
- **Connection Config Panel** — Configure external RocketMQ NameServer addresses, send/consume timeouts, and consumer group prefix directly in the browser. Changes take effect immediately and persist across restarts.
- **Monitoring Metrics** — Live CPU, memory, and network I/O charts per node, streamed over STOMP/WebSocket.
- **Operations Panel** — Start, stop, and restart individual nodes. Every operation is recorded in the activity log with real-time STOMP push.
- **Learning Guide** — Built-in guided walkthroughs covering architecture basics, node lifecycle, message models, master-slave replication, and observability.

---

## Features

- **Embedded real RocketMQ** — runs an actual Apache RocketMQ 5.3.3 NameServer + Broker in-process. No fake mocks, no Docker.
- **Topology visualization** — interactive ECharts graph showing NameServer, Master, and Slave nodes and their relationships.
- **Message simulation** — produce and consume real messages through the embedded broker; watch delivery succeed in real time.
- **Message templates** — 6 built-in templates (JSON order, plain text, RocketMQ event, key-value, user behavior) with placeholder substitution (`{index}`, `{timestamp}`, `{uuid}`, `{random}`, `{topic}`). Custom templates supported.
- **External MQ communication** — connect to your local RocketMQ cluster via the web UI configuration panel. Supports PSEUDO mode (HOST node bridge) and REAL mode (real admin client with produce/consume).
- **Web-based connection config** — configure NameServer addresses, timeouts, and consumer group prefix directly in the browser. Changes take effect immediately and persist across restarts.
- **Monitoring metrics** — live CPU, memory, and network I/O charts for each node, streamed over STOMP/WebSocket.
- **Data dashboard** — ECharts time-series line charts (CPU/memory/TPS trends), node comparison bar charts, and real-time metric cards with quantified axes.
- **Dynamic rate limiting** — automatically calculates a safe maximum message batch size based on your machine's CPU cores, available JVM heap, and disk space. Each user sees a different limit tailored to their hardware.
- **Compact delivery results** — statistics summary (total/success/fail/success rate) with paginated list, preventing page overflow when sending large batches.
- **Node lifecycle management** — start, stop, and restart individual nodes; observe cluster state transitions.
- **Master–Slave replication** — run a Master and Slave simultaneously and see replication in action.
- **Activity log & audit trail** — every operation is recorded and pushed to the UI in real time via STOMP.
- **Learning guide** — built-in guided walkthroughs that explain cluster concepts as you interact.
- **Zero external dependencies** — one command for the backend, one for the frontend, open your browser. That's it.

---

## Quick Start

> MQCluster runs on **Windows**, **Linux**, and **macOS**. The codebase is fully cross-platform —
> only the terminal commands differ slightly.

### Prerequisites

| Tool | Version | Notes |
| --- | --- | --- |
| Java (JDK) | 21+ | Required for the Spring Boot backend |
| Maven | 3.9+ | Or use the bundled `mvnw` wrapper |
| Node.js | 20+ | Required for the Next.js frontend |
| npm | 10+ | Ships with Node.js |

<details>
<summary><b>Java 21 installation guide (click to expand)</b></summary>

| OS | Command |
| --- | --- |
| Windows | `winget install Microsoft.OpenJDK.21` or download from [Microsoft OpenJDK](https://learn.microsoft.com/java/openjdk/) |
| Linux (Ubuntu/Debian) | `sudo apt install openjdk-21-jdk` |
| Linux (Fedora/RHEL) | `sudo dnf install java-21-openjdk-devel` |
| macOS (Homebrew) | `brew install openjdk@21` |
| macOS (SDKMAN) | `sdk install java 21-tem` |

Verify: `java -version` should show `21.x.x`.
</details>

### 1. Start the backend

**Linux / macOS:**
```bash
cd java
./mvnw spring-boot:run
```

**Windows (PowerShell):**
```powershell
cd java
.\mvnw.cmd spring-boot:run
```

The backend starts on **http://localhost:8088**.

> **Troubleshooting**: If you see `UnsupportedClassVersionError: class file version 65.0`,
> your `JAVA_HOME` points to Java 17. Set it to Java 21:
> - **Windows**: `$env:JAVA_HOME = "C:\Path\To\jdk-21"`
> - **Linux**: `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk`
> - **macOS**: `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`

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

<details>
<summary><b>Having startup issues? (click to expand)</b></summary>

| Symptom | Solution |
| --- | --- |
| Port 8088 already in use | **Windows**: `Get-NetTCPConnection -LocalPort 8088 \| ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }`<br>**Linux/macOS**: `lsof -ti:8088 \| xargs kill -9` |
| Port 3000 already in use | **Windows**: `Get-NetTCPConnection -LocalPort 3000 \| ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }`<br>**Linux/macOS**: `lsof -ti:3000 \| xargs kill -9` |
| Broker won't start (store lock) | Delete `java/run/` directory and restart |
| `./mvnw: Permission denied` (Linux/macOS) | `chmod +x ./mvnw` |
| Frontend blank page | Ensure backend is running on 8088 first |

For full startup guide with cleanup steps, see [AGENTS.md](AGENTS.md#4-構建運行測試).
</details>

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

- **[架构文档（中文）](docs/zh/ARCHITECTURE.zh.md)** — 六边形架构、前后端分离、嵌入式 RocketMQ 详解
- **[贡献指南（中文）](docs/zh/CONTRIBUTING.zh.md)** — 如何参与开发、提交 PR

---

<div align="center">

**[⬆ Back to top](#mqcluster)**

Made with care for learners of distributed message systems.

</div>
