# Architecture

This document describes the architecture of MQCluster — a local-first MQ cluster learning platform
that embeds a real Apache RocketMQ runtime and exposes it through a browser-based dashboard.

---

## Table of Contents

- [High-Level Overview](#high-level-overview)
- [Hexagonal Architecture (Ports & Adapters)](#hexagonal-architecture-ports--adapters)
- [Backend Layers](#backend-layers)
  - [Core Domain](#core-domain)
  - [Application Layer](#application-layer)
  - [Infrastructure Layer](#infrastructure-layer)
  - [API Layer](#api-layer)
- [Frontend Architecture](#frontend-architecture)
- [Embedded RocketMQ](#embedded-rocketmq)
- [Real-Time Communication](#real-time-communication)
- [Request & Data Flow](#request--data-flow)
- [Design Decisions](#design-decisions)

---

## High-Level Overview

MQCluster is a **front-end / back-end separated** application:

```
┌─────────────────────────────────────────────────────────┐
│                    Browser (user)                        │
│                                                         │
│  ┌───────────────────────────────────────────────────┐  │
│  │            Next.js 16 Frontend (port 3000)        │  │
│  │  React 19 · Tailwind v4 · ECharts 6 · STOMP       │  │
│  └───────────────────┬───────────────────────────────┘  │
│                      │ /api (REST)  /ws (STOMP)         │
│                      │ Next.js rewrites proxy            │
│  ┌───────────────────▼───────────────────────────────┐  │
│  │         Spring Boot 4.1.0 Backend (port 8088)     │  │
│  │  Hexagonal architecture · Embedded RocketMQ 5.3.3 │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

- The **frontend** is a Next.js 16 application (App Router, React 19, TypeScript strict mode).
  It renders the dashboard, topology graph, message workbench, and monitoring charts.
- The **backend** is a Spring Boot 4.1.0 application that embeds a real Apache RocketMQ 5.3.3
  runtime and exposes REST + WebSocket endpoints.
- Communication happens over **REST** (`/api`) for commands and **STOMP over WebSocket** (`/ws`)
  for real-time event streaming.

---

## Hexagonal Architecture (Ports & Adapters)

The backend follows the **Hexagonal Architecture** (also known as Ports & Adapters) pattern.
The core idea: business logic lives in the center and knows nothing about the outside world.
All external concerns (HTTP, databases, messaging brokers) are adapters that plug into ports
defined by the core.

```
                         ┌─────────────────────────────┐
   REST / WebSocket  ───▶│         API Layer           │
                         │  Controllers · DTOs · WS    │
                         └──────────────┬──────────────┘
                                        │ calls
                         ┌──────────────▼──────────────┐
                         │      Application Layer      │
                         │  Use cases · Provider       │
                         │  selection · Orchestration  │
                         └──────────────┬──────────────┘
                                        │ uses ports
                         ┌──────────────▼──────────────┐
                         │        Core Domain          │
                         │  Cluster · Node · Message   │
                         │  Port interfaces (0 deps)   │
                         └──────────────▲──────────────┘
                                        │ implements ports
                         ┌──────────────┴──────────────┐
                         │    Infrastructure Layer     │
                         │  Embedded RocketMQ adapter  │
                         │  RocketMQ admin adapter     │
                         └─────────────────────────────┘
```

**Dependency rule**: dependencies always point **inward**. `core` has no dependencies on
`application`, `infrastructure`, or `api`. `infrastructure` depends on `core` (to implement ports)
but `core` never depends on `infrastructure`.

---

## Backend Layers

### Core Domain

**Location**: `java/src/main/java/com/example/clustermanager/core/`

The core contains the pure domain model and port interfaces. It has **zero external dependencies**
— no Spring, no RocketMQ, no HTTP libraries.

Key concepts:

| Concept | Description |
| --- | --- |
| `Cluster` | An aggregate root representing a logical MQ cluster with multiple nodes. |
| `Node` | A single broker or nameserver instance with a lifecycle (STOPPED, STARTING, RUNNING, STOPPING). |
| `Message` | A message produced to or consumed from a topic. |
| Port interfaces | Interfaces that `application` calls and `infrastructure` implements (e.g. cluster lifecycle, message operations). |

Because the core is framework-free, it is trivially unit-testable and can be reused in any context.

### Application Layer

**Location**: `java/src/main/java/com/example/clustermanager/application/`

The application layer orchestrates use cases. It does not contain business rules itself — it
coordinates the domain model and selects the appropriate infrastructure adapter.

Key component: **`ClusterProviderRegistry`** — selects an adapter based on a
`(ClusterMode, MiddlewareType)` tuple. For example, `(PSEUDO, ROCKETMQ)` selects the embedded
RocketMQ adapter, while `(REAL, ROCKETMQ)` selects the external admin adapter.

### Infrastructure Layer

**Location**: `java/src/main/java/com/example/clustermanager/infrastructure/`

This layer implements the core ports with concrete technology. Two adapters exist today:

| Adapter | Package | Description |
| --- | --- | --- |
| **Pseudo (embedded)** | `infrastructure/pseudo` | Runs a real Apache RocketMQ 5.3.3 NameServer + Broker in-process. This is the default learning mode. |
| **RocketMQ admin** | `infrastructure/rocketmq` | Connects to an external RocketMQ cluster via the RocketMQ admin client. For advanced use. |

The pseudo adapter is the heart of MQCluster — it launches genuine RocketMQ components inside the
JVM, so learners interact with a real broker, not a simulation.

### API Layer

**Location**: `java/src/main/java/com/example/clustermanager/api/`

The API layer is the entry point for the frontend. It contains:

- **REST controllers** — endpoints for cluster CRUD, node lifecycle, message produce/consume, and metrics.
- **DTOs** — request/response objects that map to/from domain models.
- **WebSocket / STOMP config** — sets up the `/ws` endpoint and topic subscriptions for real-time events.
- **Exception handling** — a global exception handler that translates domain errors into HTTP responses.

---

## Frontend Architecture

**Location**: `next/src/`

The frontend follows a layered structure that flows data in one direction:

```
page.tsx  ──▶  components  ──▶  hooks  ──▶  lib/api.ts  ──▶  Backend REST / STOMP
```

| Layer | Location | Responsibility |
| --- | --- | --- |
| **Pages** | `src/app/` | App Router pages. `page.tsx` is the main dashboard; `guide/` holds the learning guide. The dashboard holds cluster selection state and composes child components. |
| **Components** | `src/components/` | Reusable UI: topology graph (ECharts), message workbench, operation panel, monitoring metrics, activity log. |
| **Hooks** | `src/hooks/` | Custom React hooks. `use-cluster-streams.ts` subscribes to STOMP/SockJS real-time streams and exposes live data to components. |
| **Lib** | `src/lib/` | `api.ts` is a unified axios client with typed endpoint functions. Utilities live here too. |
| **Types** | `src/types/` | Shared TypeScript type definitions mirroring backend DTOs. |

### API proxying

`next.config.ts` configures rewrites so that `/api/*` and `/ws/*` are proxied to
`http://localhost:8088` (the backend). This avoids CORS issues in development and keeps the
frontend code environment-agnostic.

---

## Embedded RocketMQ

The defining feature of MQCluster is that it runs a **real** Apache RocketMQ 5.3.3 runtime
inside the backend JVM — not a mock or a simulator.

### How it works

1. The pseudo adapter starts an **in-process NameServer** that handles service discovery.
2. It then starts one or more **in-process Brokers** (Master and/or Slave) that register with
   the NameServer.
3. The broker uses a local working directory (`run/pseudo-cluster/` by default) for message storage
   (CommitLog, ConsumeQueue, etc.) — exactly like a production broker, just on a smaller scale.
4. Message produce/consume operations go through the **real RocketMQ client API**, so learners
   experience authentic broker behavior: topic routing, message storage, consumer offsets,
   and master–slave replication.

### Why embedded?

| Approach | Pros | Cons |
| --- | --- | --- |
| **Mock broker** | Easy to build | Not realistic; learners can't trust behavior |
| **Docker cluster** | Realistic | Heavy; requires Docker; not portable |
| **Embedded (MQCluster)** ✅ | Real broker, zero external deps, single command | Limited to single-machine scale (which is fine for learning) |

### Seed topology

By default, MQCluster seeds a 3-node topology:

| Node | Role | Initial state |
| --- | --- | --- |
| `nameserver-1` | NameServer | STOPPED |
| `broker-master-1` | Master Broker | STOPPED |
| `broker-slave-1` | Slave Broker | STOPPED |

Nodes start in the STOPPED state (`cluster.pseudo.auto-start=false`) so learners can start them
manually and observe the lifecycle transitions.

---

## Real-Time Communication

MQCluster pushes live events to the frontend using **STOMP over WebSocket** (with SockJS fallback).

| Channel | Direction | Content |
| --- | --- | --- |
| `/topic/cluster/{id}/topology` | Server → Client | Topology updates when nodes start/stop |
| `/topic/cluster/{id}/metrics` | Server → Client | Periodic CPU, memory, network I/O per node |
| `/topic/cluster/{id}/activity` | Server → Client | Audit log entries for every operation |

The frontend's `use-cluster-streams` hook subscribes to these topics and feeds updates into
React state, so the UI reflects cluster changes within milliseconds.

---

## Request & Data Flow

Here is a typical flow for starting a node:

```
1. User clicks "Start" on broker-master-1 in the UI
2. Frontend POST /api/clusters/{id}/nodes/{nodeId}/start
3. API controller → Application use case
4. Application selects adapter via ClusterProviderRegistry (PSEUDO, ROCKETMQ)
5. Pseudo adapter starts the embedded broker process
6. Broker registers with the in-process NameServer
7. Adapter publishes a TopologyChangedEvent to the STOMP topic
8. Frontend hook receives the event → React re-renders topology graph
9. Activity log entry is also pushed to /topic/.../activity
```

Message produce/consume follows a similar path, with the addition of real RocketMQ client calls
inside the adapter.

---

## Design Decisions

### Why hexagonal architecture?

- **Testability** — the core domain can be unit-tested without Spring or RocketMQ.
- **Swappability** — the `ClusterProviderRegistry` lets us plug in new middleware types or
  deployment modes without touching the core or API.
- **Clarity** — each layer has a single responsibility, making the codebase easier to navigate
  and teach (which is the whole point of this project).

### Why Next.js (not Vue or plain React)?

- **App Router** gives us file-based routing and server components out of the box.
- **API rewrites** make proxying to the backend trivial with zero CORS headaches.
- **React 19** + **TypeScript strict** provide strong type safety and modern hooks ergonomics.
- **Tailwind CSS v4** keeps styling fast and consistent without context switching.

### Why embed RocketMQ instead of mocking?

- Learners deserve to interact with the **real thing**. A mock teaches the mock, not RocketMQ.
- Embedding avoids the overhead of Docker or remote clusters, keeping the barrier to entry low
  (one Java command, one npm command).
- The embedded broker uses the same storage format and client protocol as production, so
  knowledge transfers directly.

---

<div align="center">

**[⬆ Back to top](#architecture)**

</div>
