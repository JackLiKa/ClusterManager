# Cluster Manager Architecture

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
└─ pom.xml
```

## Design notes

- `core` 只保留领域模型和稳定端口，包含 `IClusterProvider`、`IVirtualNetwork`、`IServiceRegistry` 等扩展点。
- `application` 通过 `ClusterProviderRegistry` 统一选择 provider，并由 `ClusterFacadeService` 编排拓扑、监控、日志、消息模拟和服务登记。
- `infrastructure/pseudo` 负责伪集群运行时、虚拟 IP 分配、节点隔离和手工服务注册。
- `infrastructure/rocketmq` 负责真实集群适配、RocketMQ 节点映射，以及手工登记服务的模拟接入。
- `api` 暴露 REST 与 WebSocket，`ClusterController` 现在同时负责节点操作和服务增删。
- `frontend` 中 `OperationsPanel.vue` 承担服务登记入口，`ClusterTopologyCard.vue` 负责更疏朗的拓扑布局展示。

## Startup note

- `spring-boot:run` 阶段会先构建前端并复制 `frontend/dist` 到 `target/classes/static`。
- 因此开发模式下访问 `http://localhost:8080/` 也能稳定拿到完整 SPA，而不再只适用于打包后的 jar。
