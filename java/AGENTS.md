# AGENTS.md — Cluster Manager

> AI agent 导航与规约文件（机器向入口）。人类可读文档位于 `docs/`（英文）与 `docs/zh/`（中文）。
> 先读本文件，再深入到对应层。

## 1. 项目速览

| 字段 | 值 |
| --- | --- |
| 名称 | Cluster Manager（`io.github.jacklika:cluster-manager`） |
| 版本 | `0.1.0-SNAPSHOT` |
| 仓库 | https://github.com/JackLiKa/ClusterManager（默认分支 `main`，公开） |
| 许可证 | Apache 2.0 |
| 描述 | Spring Boot + Vue 3 Web 控制台，统一管理本地**伪集群**与真实 **RocketMQ** 节点。 |
| 定位 | 早期实验/演示工具——单用户、本地优先。**未生产化**（无认证、无容器）。 |

### 技术栈

- **后端**：Java 17、Spring Boot 4.1.0（`spring-boot-starter-web` / `-validation` / `-websocket` / `-actuator`）、Jackson、Apache RocketMQ client 4.9.8。
- **前端**：Vue 3.5 + TypeScript、Vite 5、Element Plus 2、ECharts 5、vue-router 4、axios、`@stomp/stompjs` + `sockjs-client`（WebSocket）。
- **构建**：Maven（wrapper `mvnw`/`mvnw.cmd`）驱动后端**和**前端构建——`exec-maven-plugin` 跑 `npm run build`，`maven-resources-plugin` 把 `frontend/dist` 拷进 `target/classes/static`。
- **CI**：GitHub Actions（`.github/workflows/ci.yml`）——JDK 17 + Node 20，跑 `./mvnw -B verify` 再跑 `npm run lint` + `npm run typecheck`。发布工作流 `publish.yml` 在版本 tag 上创建 GitHub Release。

## 2. 架构（六边形 / 端口与适配器）

```text
src/main/java/com/example/clustermanager
├─ api/            → REST 控制器、DTO、WebSocket 配置、SPA 转发、异常处理
│  ├─ config/      → CorsConfig、WebSocketBrokerConfig、ClusterStreamProperties
│  ├─ controller/  → ClusterController、ClusterTelemetryPushService、SpaForwardController、ApiExceptionHandler
│  └─ dto/         → 请求 DTO（带校验）
├─ application/    → 用例编排与 provider 选择
│  ├─ model/       → 命令 record（ClusterSelection、*Command）
│  └─ service/     → ClusterFacadeService、ClusterProviderRegistry
├─ core/           → 领域模型与端口（无外部依赖）
│  ├─ model/       → ClusterMode、ClusterNode、ClusterTopology、NodeStatus、MessageScenario …
│  └─ port/        → IClusterProvider、ITopologyReader、INodeManager、IMonitoringGateway、
│                    IMessageWorkbench、IServiceRegistry、IVirtualNetwork
└─ infrastructure/ → 适配器实现
   ├─ pseudo/      → PseudoClusterProvider（瘦身編排器）+ 子組件
   │  ├─ node/     → ManagedNode（record）、NodeRegistry、NodeRole、NodeKind
   │  ├─ runtime/  → EmbeddedRocketMqRuntime、EmbeddedRocketMqNode、EmbeddedNodeSpec、
   │  │             PortPool、BrokerConfigBuilder、NamesrvConfigBuilder
   │  ├─ messaging/→ EmbeddedMessageWorkbench（統一嵌入式 + HOST 消息路徑）
   │  ├─ topology/ → PseudoTopologySeeder（種子化默認節點）
   │  ├─ AuditLog  → 共享審計日誌（Pseudo + REAL provider 共用）
   │  └─ 網絡組件  → TapVirtualNetwork、CidrAddressPool、TapNativeBridge、NoOpTapNativeBridge
   └─ rocketmq/    → RocketMqClusterProvider、RocketMqAdminAdapter、RocketMqAdminClient（+ Mock）
```

- **依赖方向**：`api` → `application` → `core` ← `infrastructure`。`core` 零外部依赖。适配器实现 `core` 端口；`application` 通过 `ClusterProviderRegistry` 按 `(ClusterMode, MiddlewareType)` 选适配器。
- **两个 provider**：
  - `PseudoClusterProvider`（`PSEUDO` + `ROCKETMQ`）：瘦身編排器，委託子組件。種子化 3 節點（`rmq-ns-01`、`rmq-broker-m-01`、`rmq-broker-s-01`）；VIRTUAL 節點由 `EmbeddedRocketMqRuntime` 啟動進程內真實 RocketMQ；HOST 節點綁定真實地址。消息模擬由 `EmbeddedMessageWorkbench` 統一處理（嵌入式或外部 NameServer）。
  - `RocketMqClusterProvider`（`REAL` + `ROCKETMQ`）：包装 `RocketMqAdminAdapter` 拉拓扑/链路；手工登记的节点合并进 topology/metrics/logs/simulate。使用共享 `AuditLog`。
- **流式推送**：`ClusterTelemetryPushService` 是 `@Scheduled`（每 `cluster.stream.publish-interval-ms`，默认 5000ms），通过 `SimpMessagingTemplate` 把 metrics + logs 推到 STOMP 主题 `/topic/clusters/{clusterId}/{metrics|logs}`。前端用 `useClusterStreams.ts`（SockJS + STOMP）消费。
- **SPA 服务**：`SpaForwardController` 把非 API、非静态资源请求转发到 `index.html`。`mvn spring-boot:run` 与 fat JAR 都在 `http://localhost:8080/` 提供 SPA。

完整设计说明见 `docs/ARCHITECTURE.md`。

## 3. 前端布局

```text
frontend/src
├─ api/clusterApi.ts            → axios 客户端 + 类型化端点函数（响应拦截器归一化后端错误）
├─ components/
│  ├─ ClusterTopologyCard.vue   → ECharts 节点 + 链路图
│  ├─ MessageWorkbenchCard.vue  → topic 级 produce/consume 模拟表单
│  └─ OperationsPanel.vue       → KPI 条、节点操作表、服务登记表单、活动日志
├─ composables/useClusterStreams.ts → STOMP/SockJS 订阅，selection 变化时重连
├─ types/cluster.ts             → 与后端 DTO 对齐的 TS 类型
├─ views/
│  ├─ ClusterOverviewPage.vue   → 主仪表盘，持有 cluster selection 状态
│  └─ GuidePage.vue             → 渲染打包进来的 docs/zh/manual.md
├─ App.vue、main.ts、router.ts、styles.css、env.d.ts
```

Vite dev server（`npm run dev`，端口 5173）把 `/api`、`/ws`、`/guide` 代理到 `http://localhost:8080`。

## 4. 构建、运行、测试

```powershell
# 后端 + 前端一个产物（构建前端，把 dist 拷进 target/classes/static）
.\mvnw.cmd spring-boot:run          # 开发：http://localhost:8080
.\mvnw.cmd clean verify             # 完整构建 + 测试
.\mvnw.cmd clean package            # fat JAR → target/cluster-manager-0.1.0-SNAPSHOT.jar
java -jar target/cluster-manager-0.1.0-SNAPSHOT.jar

# 前端热重载（与 spring-boot:run 并行跑）
cd frontend; npm install; npm run dev

# 前端质量门
cd frontend; npm run typecheck      # vue-tsc --noEmit
cd frontend; npm run lint           # eslint --fix
cd frontend; npm run build          # typecheck + vite build
```

> ⚠️ `mvn spring-boot:run` 与 `mvn package` 各触发 `npm run build` **两次**（一次 `generate-resources`，一次 `prepare-package`）。见优化清单。

### 测试

- 后端：JUnit 5 + AssertJ + Spring Boot Test。文件在 `src/test/java/com/example/clustermanager/`：
  - `ClusterManagerApplicationTests`、`ClusterFacadeSmokeTest`、`ClusterServiceRegistrationTest`、`PseudoClusterRuntimeTest`、`FixesVerificationTest`（过往 P0/P1/P3 修复的回归测试）。
- 前端：**无单元/组件测试**——CI 只跑 `typecheck` + `lint`。

## 5. 配置

`src/main/resources/application.properties`（无 profile、无环境覆盖）：

| 属性 | 默认值 | 备注 |
| --- | --- | --- |
| `server.port` | `8080` | 后端 + SPA。 |
| `cluster.cors.allowed-origins` | `http://localhost:5173,127.0.0.1:5173` | 仅开发源。 |
| `cluster.pseudo.cluster-id` | `local-lab` | 伪集群 id。 |
| `cluster.pseudo.cidr` | `10.77.0.0/24` | TAP 节点虚拟 IP 池。 |
| `cluster.pseudo.auto-start` | `false` | 种子节点初始为 `STOPPED`。 |
| `cluster.pseudo.work-dir` | `run/pseudo-cluster` | 每节点工作目录（嵌入式 RocketMQ 存储根路径）。 |
| `cluster.pseudo.health-timeout` | `5s` | 健康轮询超时。 |
| `cluster.pseudo.tap.enabled` | `false` | TAP native bridge 开关。 |
| `cluster.rocketmq.cluster-id` | `rocketmq-demo` | 真实集群 id。 |
| `cluster.rocketmq.dashboard-name` | `JackLi` | 个人标识——应外部化。 |
| `cluster.rocketmq.name-servers` | `192.168.50.78:9876` | **环境特定私网 IP 已入仓**——应外部化。 |
| `cluster.stream.publish-interval-ms` | `5000` | 遥测推送周期。 |

## 6. 规约

### 后端

- **DTO/命令/模型用 record**（`ClusterSelection`、`MessageSimulationRequest`、`ClusterNode`…）。不可变值对象。
- **构造器注入**贯穿；无字段注入、无 `@Autowired` 字段。
- **按层分包**（六边形）：`core` → `application` → `infrastructure` + `api`。新增中间件 = 新增 `infrastructure/<name>` 包实现 `IClusterProvider`。
- **错误处理**：`ApiExceptionHandler`（`@RestControllerAdvice`）把 `IllegalArgumentException`→400、`IllegalStateException`→409、`MethodArgumentNotValidException`→400、`Exception`→500。响应体形如 `Map.of("error", "<CODE>", "message", "<...>")`。
- **校验**：请求体 `@Valid`；控制器 `@Validated` 处理 path/query。枚举 path 变量用 `ClusterMode.valueOf(raw.toUpperCase(Locale.ROOT))` 解析。
- **历史修复标记**：代码里有 `// P0 修复` / `// P1 修复` / `// P3 修复` 注释记录回归修复——**不要移除**，除非已核对 `FixesVerificationTest` 中对应测试。
- **审计日志**：内存 `ConcurrentLinkedDeque`，每 provider 上限 200 条（`appendAuditLog`）。不持久化。

### 前端

- **Composition API + `<script setup lang="ts">`**（`tsconfig.json` 严格模式）。
- **单一 axios 实例**在 `api/clusterApi.ts`，响应拦截器把 `error.response.data.message` 解包成 `Error`。始终走该客户端，不要用裸 `fetch` 绕过。
- **STOMP 订阅**按 `selection.clusterId` 作用域；composable 在 selection 变化时重连，`onBeforeUnmount` 时断开。
- **无状态管理库**——本地组件状态 + composable。除非应用长大，保持现状。

### Git / CI

- Conventional Commits 风格（`feat:`、`fix:`、`chore:`、`docs:`——见 `git log`）。中文 commit body 可接受。
- PR 模板在 `.github/pull_request_template.md`；issue 模板在 `.github/ISSUE_TEMPLATE/`。
- CI 门：`./mvnw -B verify` + `npm run lint` + `npm run typecheck`，全过才合并。

## 7. 改动落点

| 你想… | 去这里… |
| --- | --- |
| 新增 REST 端点 / 改请求结构 | `api/controller/ClusterController.java` + `api/dto/` + `application/model/*Command` + `ClusterFacadeService`。 |
| 新增中间件（如 Kafka） | 新增 `infrastructure/kafka/` 包实现 `IClusterProvider`；标 `@Component`；确保 `ClusterProviderRegistry` 能按 `(mode, middleware)` 解析。 |
| 改遥测推送周期 / 通道 | `ClusterTelemetryPushService` + `ClusterStreamProperties`。 |
| 改伪节点生命周期 / 拉起 | `infrastructure/pseudo/LocalPseudoNodeRuntime` + `PseudoNodeAgent`。 |
| 改真实 RocketMQ admin 集成 | `infrastructure/rocketmq/RocketMqAdminAdapter` + `RocketMqAdminClient`（测试用 `MockRocketMqAdminClient`）。 |
| 改 WebSocket/CORS 配置 | `api/config/WebSocketBrokerConfig`、`CorsConfig`、`CorsProperties`。 |
| 改前端 API 调用 | `frontend/src/api/clusterApi.ts` + `types/cluster.ts`。 |
| 改仪表盘布局 | `frontend/src/views/ClusterOverviewPage.vue` + `components/`。 |
| 改构建流水线 | `pom.xml`（Maven 插件）+ `.github/workflows/`。 |

## 8. 应做 / 不应做

**应做**
- 保持 `core` 不引入 Spring/Jackson/RocketMQ。
- 任何用户可见 bug 修复都加回归测试（`FixesVerificationTest` 或同级文件）。
- 优先用 record 与不可变集合（`List.of`、`Map.copyOf`）。
- 推送前跑 `.\mvnw.cmd clean verify`——CI 跑同一道门。

**不应做**
- 不要在 `application.properties` 放 secret / connection string / PII（当前 `name-servers` IP 是已知违规——外部化它）。
- 不要从控制器绕过 `ClusterFacadeService`；facade 是唯一编排点。
- 不要在未核对关联测试时移除 `// P0/P1/P3 修复` 注释。
- 不要在 `core` 加字段注入或可变单例。
- 不要进一步抑制编译警告（`pom.xml` 已设 `showWarnings=false` + `-nowarn`——见优化清单；建议反过来重新打开警告）。

## 9. 已知缺口 / 后续

详见随本文件交付的优化分析。头条项：
- 管理操作与 actuator 端点无认证。
- ~~环境特定配置（`name-servers`、`dashboard-name`）入仓。~~ **已外部化**（Phase 0-1）。
- ~~Maven 每个生命周期把前端构建两遍；遥测无订阅者也推送；REAL provider 返回随机指标。~~ **前端双构建已修；遥测按订阅推送已修**（Phase 0-2/0-3）。
- 前端无测试；无 Dockerfile；无 Spring profile；~~兜底 500 处理器吞掉真实错误。~~ **统一 ErrorResponse + correlationId 已修**（Phase 0-4）。

## 11. 嵌入式 RocketMQ Spike 结论（Phase 1）

**结论：RocketMQ 4.9.8 可在 Spring Boot 4.1.0 + Java 17 进程内嵌入运行，无需独立 JVM 进程。**

`src/test/java/com/example/clustermanager/EmbeddedRocketMqSpikeTest.java`（默认禁用，需 `-DrunEmbeddedRocketMqSpike=true` 启用）验证：
- 进程内启动 `NamesrvController` + `BrokerController`（直接 `new`，绕过 `BrokerStartup` 的 `ROCKETMQ_HOME` 依赖）。
- 真实 `DefaultMQProducer` 发送、`DefaultMQPushConsumer` 接收，topic/tag/key/body 全部对齐。
- `BrokerController.shutdown()` 干净退出。

### 关键 API（4.9.8 实测）

| 用途 | API |
| --- | --- |
| NameServer 监听端口 | `NettyServerConfig.setListenPort(int)`（**不是** `setBindPort`） |
| NameServer 配置 | `org.apache.rocketmq.common.namesrv.NamesrvConfig` |
| NameServer 控制器 | `org.apache.rocketmq.namesrv.NamesrvController` |
| Broker 控制器 | `org.apache.rocketmq.broker.BrokerController`（4 参构造：`BrokerConfig`、`NettyServerConfig`、`NettyClientConfig`、`MessageStoreConfig`） |
| Broker 生命周期 | `initialize()` → `start()` → `shutdown()` |
| 缩短注册间隔 | `BrokerConfig.setRegisterNameServerPeriod(int)`（**不是** `setBrokerHeartbeatInterval`） |
| 自动建 topic 常量 | `org.apache.rocketmq.common.topic.TopicValidator.AUTO_CREATE_TOPIC_KEY_TOPIC`（**不是** `MixAll.AUTO_CREATE_TOPIC_KEY_TOPIC`，4.9.8 已迁移） |

### 已知坑（务必规避）

1. **Broker 首次心跳不注册 topic**：`autoCreateTopicEnable=true` 时，`TBW102` 等系统 topic 在第二次心跳（默认 10s 后）才注册到 NameServer。生产者首次 `send` 会报 `No route info of this topic`。
   - **解决**：直接调用 `brokerController.getTopicConfigManager().createTopicInSendMessageMethod(...)` 建 topic，再 `brokerController.registerBrokerAll(true, false, true)` 强制立即注册；然后 `producer.getDefaultMQProducerImpl().getmQClientFactory().updateTopicRouteInfoFromNameServer(topic)` 拉路由。
2. **Java 17 模块反射**：RocketMQ 的 `MappedFile.clean()` 反射访问 `jdk.internal.ref.Cleaner.clean()` 与 `java.nio.DirectByteBuffer.attachment()`，需 Surefire `argLine` 加：
   ```
   --add-opens java.base/java.nio=ALL-UNNAMED
   --add-opens java.base/sun.nio.ch=ALL-UNNAMED
   --add-exports java.base/jdk.internal.ref=ALL-UNNAMED
   ```
   生产运行（`spring-boot:run` / fat JAR）同样需要这些 JVM 参数。
3. **`BrokerStartup` 不可用**：它依赖 `ROCKETMQ_HOME` 加载 logback 配置，进程内嵌入会崩。直接 `new BrokerController(...)` 绕过。
4. **shutdown 后事务消息检查线程报 `RejectedExecutionException`**：broker 线程池已终止，`TransactionalMessageCheckService` 仍尝试注册——属无害噪声，不影响测试结果。

### 后续架构（基于 spike 推进）

下一步将构建：
- `EmbeddedRocketMqNode`（封装 NameServer/Broker 生命周期、状态、指标）
- `EmbeddedRocketMqRuntime`（管理多节点）
- `PortPool`（端口分配/释放）
- `BrokerConfigBuilder` / `NamesrvConfigBuilder`
- `EmbeddedNodeSpec`（节点角色、端口、存储路径、broker name/id）
- 用真实 RocketMQ 控制器替换 `PseudoNodeAgent` 的虚拟节点启动
- 种子化 NameServer + Master/Slave Broker 拓扑
- 多 broker / 主从复制验证

## 10. 最近重要变更

- `8df683c` chore: trigger publish workflow on version tags
- `719d983` chore: simplify publish workflow to create release only
- `69f4a99` chore: add publish workflow
- `ac15284` chore: add ESLint and Prettier to frontend
- `8844635` chore: add GitHub Actions CI and issue templates
- `ad73da0` docs: rewrite README and add open-source documentation
- `99eabb0` fix: stabilize tests and remove redundant RocketMQ admin call
- `560433c` 修复了以下（P0/P1/P3 回归修复——见 `FixesVerificationTest`）
- `5ce1b75` 完成了伪集群的连接本地环境节点
