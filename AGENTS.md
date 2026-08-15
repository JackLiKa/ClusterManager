# AGENTS.md — MQCluster（MQ 集群學習平台）

> AI agent 導航與規約文件。前後端分離架構，Java 後端 + Next.js 前端。

## 1. 項目速覽

| 字段 | 值 |
| --- | --- |
| 名稱 | MQCluster（`io.github.jacklika:mqcluster`） |
| 版本 | `0.1.0-SNAPSHOT` |
| 倉庫 | https://github.com/JackLiKa/MQCluster |
| 許可證 | Apache 2.0 |
| 描述 | 本地優先的 MQ 集群學習平台——單機瀏覽器操作嵌入式真實 RocketMQ，學習集群拓撲、消息模型與運維。 |

## 2. 目錄結構

```
MQCluster/
├── .github/       → GitHub Actions CI/CD、Issue 模板
├── docs/          → 架構文檔（ARCHITECTURE.md）
├── java/          → Spring Boot 4.1.0 後端（端口 8088）
│   ├── src/main/java/com/example/clustermanager/
│   │   ├── api/              → REST 控制器、DTO、WebSocket 配置、異常處理
│   │   ├── application/      → 用例編排與 provider 選擇
│   │   ├── core/             → 領域模型與端口（無外部依賴）
│   │   └── infrastructure/   → 適配器實現（pseudo 嵌入式 RocketMQ + rocketmq admin）
│   ├── src/main/resources/   → application.properties
│   ├── src/test/             → JUnit 5 + AssertJ 測試（128 tests）
│   ├── pom.xml               → Maven 構建（artifact: mqcluster）
│   └── AGENTS.md             → 後端詳細導航
├── next/          → Next.js 16 前端（端口 3000）
│   ├── src/
│   │   ├── app/              → App Router 頁面（page.tsx 儀表盤、guide/ 學習指南）
│   │   ├── components/       → 共享 UI 組件（拓撲圖、消息工作台、操作面板、監控指標）
│   │   ├── hooks/            → 自定義 React Hooks（STOMP 流訂閱）
│   │   ├── lib/              → API 客戶端、工具函數
│   │   └── types/            → TypeScript 類型定義
│   ├── next.config.ts        → API 代理配置（/api → localhost:8088）
│   └── .env.local            → 環境變量
├── README.md      → 開源項目主 README
├── CONTRIBUTING.md → 貢獻指南
├── SECURITY.md    → 安全政策
├── LICENSE        → Apache 2.0
└── AGENTS.md      → 本文件
```

## 3. 技術棧

### 後端（java/）
- Java 17、Spring Boot 4.1.0
- Spring Web / Validation / WebSocket / Actuator
- Apache RocketMQ 4.9.8（嵌入式 NameServer + Broker）
- JUnit 5 + AssertJ + Spring Boot Test
- Maven（wrapper `mvnw`/`mvnw.cmd`，artifact: `mqcluster`）

### 前端（next/）
- Next.js 16（App Router）+ React 19
- TypeScript（嚴格模式）
- Tailwind CSS v4 + shadcn/ui
- ECharts 5（拓撲可視化）
- axios（HTTP 客戶端）
- @stomp/stompjs + sockjs-client（WebSocket 實時推送）

## 4. 構建、運行、測試

### 後端
```powershell
cd java
.\mvnw.cmd spring-boot:run          # 開發：http://localhost:8088
.\mvnw.cmd clean verify             # 完整構建 + 測試（128 tests）
.\mvnw.cmd clean package            # fat JAR → target/mqcluster-0.1.0-SNAPSHOT.jar
```

### 前端
```powershell
cd next
npm install
npm run dev                         # 開發：http://localhost:3000
npm run build                       # 生產構建
npm run lint                        # ESLint
npx tsc --noEmit                    # Typecheck
```

### 開發流程
1. 啟動後端：`cd java; .\mvnw.cmd spring-boot:run`
2. 啟動前端：`cd next; npm run dev`
3. 打開瀏覽器：`http://localhost:3000`
4. 前端通過 Next.js rewrites 代理 `/api` 和 `/ws` 到後端 `localhost:8088`

### 嵌入式 RocketMQ JVM 參數
RocketMQ 4.9.8 嵌入式運行需要 Java 模块訪問權限：
```
--add-opens java.base/java.nio=ALL-UNNAMED
--add-opens java.base/sun.nio.ch=ALL-UNNAMED
--add-exports java.base/jdk.internal.ref=ALL-UNNAMED
```

## 5. 架構（六邊形 / 端口與適配器）

### 後端
```
api → application → core ← infrastructure
```
- `core`：領域模型與端口（零外部依賴）
- `application`：用例編排，通過 `ClusterProviderRegistry` 按 `(ClusterMode, MiddlewareType)` 選適配器
- `infrastructure/pseudo`：嵌入式 RocketMQ 運行時（進程內 NameServer + Broker）
- `infrastructure/rocketmq`：真實 RocketMQ admin 適配器

### 前端
```
page.tsx → components → hooks → lib/api.ts → 後端 REST/STOMP
```
- `page.tsx`：主儀表盤，持有集群選擇狀態，組合子組件
- `components/`：拓撲圖（ECharts + 圖例）、消息工作台（含模板選擇器）、連接配置面板、操作面板、監控指標卡
- `hooks/use-cluster-streams.ts`：STOMP/SockJS 實時流訂閱
- `lib/api.ts`：統一 axios 客戶端 + 類型化端點函數

完整架構說明見 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

## 6. 配置

### 後端（java/src/main/resources/application.properties）
| 屬性 | 值 | 備註 |
| --- | --- | --- |
| `server.port` | `8088` | 後端端口 |
| `cluster.cors.allowed-origins` | `http://localhost:3000` | 前端源 |
| `cluster.pseudo.auto-start` | `false` | 種子節點初始 STOPPED |
| `cluster.pseudo.clean-store-on-start` | `true` | 啟動時清理舊 store（可配置，設 false 保留歷史消息） |
| `cluster.pseudo.work-dir` | `run/pseudo-cluster` | 嵌入式 RocketMQ 存儲 |
| `cluster.pseudo.health-timeout` | `5s` | 健康輪詢超時 |
| `cluster.rocketmq.name-servers` | `${CLUSTER_ROCKETMQ_NAME_SERVERS:}` | 真實集群 NameServer（環境變量外部化） |
| `cluster.stream.publish-interval-ms` | `5000` | 遙測推送週期 |

### 前端（next/.env.local）
| 變量 | 值 | 備註 |
| --- | --- | --- |
| `BACKEND_URL` | `http://localhost:8088` | 後端地址（rewrites 代理目標）|

### 前端（next/next.config.ts）
| 配置 | 值 | 備註 |
| --- | --- | --- |
| `allowedDevOrigins` | `['127.0.0.1']` | 允許的開發預覽源 |

### 運行時連接配置（UI 可修改，持久化到 `run/rocketmq-connection.properties`）
| 項目 | 默認值 | 範圍 | 備註 |
| --- | --- | --- | --- |
| `name-servers` | 繼承啟動配置 | 任意地址列表 | NameServer 地址（逗號分隔），空時 PSEUDO 用嵌入式集群 |
| `send-msg-timeout-ms` | `10000` | 1000-600000 | 發送超時（毫秒） |
| `consume-timeout-seconds` | `15` | 1-300 | 消費超時（秒） |
| `consumer-group-prefix` | `mqcluster` | 非空字符串 | 消費者組前綴 |

> UI 配置保存後立即生效，持久化到文件，重啟後保留。API：`GET/PUT /api/clusters/settings/rocketmq`

## 7. 已驗證功能

- ✅ Provider 列表加載
- ✅ 集群拓撲展示（3 節點：狀態着色 灰/綠/紅/黃 + 角色定形狀 圓/矩形/菱形 + 拖拽 + 圖例）
- ✅ 節點啟動/停止/重啟（嵌入式真實 RocketMQ）
- ✅ 消息 produce/consume 模擬（真實 RocketMQ 投遞，30/30 成功）
- ✅ 消息模板系統（6 預定義模板 + 占位符替換 `{index}`/`{timestamp}`/`{uuid}`/`{random}`/`{topic}`）
- ✅ 外部 MQ 通信（PSEUDO HOST 路徑 + REAL 模式真實 produce/consume）
- ✅ 網頁連接配置面板（NameServer + 超時 + 消費者組前綴，持久化到文件，立即生效）
- ✅ 監控指標（真實 JVM CPU/內存 + RocketMQ broker put/get TPS）
- ✅ 活動日誌（審計日誌 + 實時 STOMP 推送）
- ✅ 主從複製（Master + Slave 同時運行）
- ✅ 後端測試套件（128 tests pass, 2 skipped）
- ✅ 前端 typecheck + ESLint 通過
- ✅ 手動節點登記（HOST + VIRTUAL）

## 8. 監控指標語義

`NodeMetrics` 字段映射（教育語義映射，非實際字節吞吐）：

| 字段 | 數據來源 | 說明 |
| --- | --- | --- |
| `cpuUsage` | JVM 進程 CPU | `OperatingSystemMXBean.getProcessCpuLoad()` × 100 |
| `memoryUsage` | JVM 堆內存 | `used / max × 100` |
| `networkInBytesPerSecond` | RocketMQ broker put TPS | 消息投遞速率（msg/s） |
| `networkOutBytesPerSecond` | RocketMQ broker get TPS | 消息消費速率（msg/s） |

- Broker 節點：CPU/內存來自 JVM，網絡值來自 RocketMQ broker stats（key: `brokerClusterName`）
- NameServer 節點：CPU/內存來自 JVM，網絡值為零
- 停止/未知節點：全部為零

## 9. 測試覆蓋

### 測試文件
| 文件 | 測試數 | 方法 |
| --- | --- | --- |
| `ComprehensiveSystemTest` | 57 | 6 種黑盒測試方法（等價類、邊界值、正交試驗、判定表、錯誤猜測、場景法） |
| `MessageTemplateAndConfigTest` | 56 | 6 種黑盒測試方法覆蓋消息模板和連接配置功能 |
| `ClusterServiceRegistrationTest` | 3 | 服務登記集成測試 |
| `ClusterFacadeSmokeTest` | 2 | 門面冒煙測試 |
| `FixesVerificationTest` | 3 | P0/P1/P3 修復回歸 |
| `PseudoClusterRuntimeTest` | 2 | 偽集群拓撲測試 |
| `EmbeddedRocketMqRuntimeMetricsTest` | 3 | 嵌入式指標測試（1 條件啟用） |
| `EmbeddedRocketMqSpikeTest` | 1 | 嵌入式 Spike（條件啟用） |
| `ClusterManagerApplicationTests` | 1 | 應用上下文加載 |
| **合計** | **128** | **2 條件跳過** |

### 嵌入式 RocketMQ 測試
需要 JVM 模块參數（已在 `pom.xml` Surefire `argLine` 配置）：
```
--add-opens java.base/java.nio=ALL-UNNAMED
--add-opens java.base/sun.nio.ch=ALL-UNNAMED
--add-exports java.base/jdk.internal.ref=ALL-UNNAMED
```

## 10. 關鍵設計決策

- **Store 清理可配置**：`cluster.pseudo.clean-store-on-start=true`（默認），學習平台場景下每次啟動為乾淨狀態；設 `false` 保留歷史消息用於調試。
- **Broker stats key**：RocketMQ `BROKER_PUT_NUMS`/`BROKER_GET_NUMS` 的 stats key 是 `brokerClusterName`（`embedded-cluster`），不是 `brokerName`。
- **TPS 滾動窗口**：RocketMQ stats 為 60 秒滾動窗口，消息發送後需在窗口期內查詢才能看到非零值。
- **手動節點登記**：HOST 節點綁定真實地址（localhost/127.0.0.1），VIRTUAL 節點要求 CIDR 範圍內的 IPv4 地址。
- **消息模板占位符**：`MessageTemplateService.render()` 在發送時替換 `{index}`/`{timestamp}`/`{uuid}`/`{random}`/`{topic}`，支持預定義模板和自定義輸入。
- **外部 MQ 通信三層後備**：PSEUDO HOST 路徑按優先級查找 NameServer 地址——登記的 HOST 節點 > 運行時 UI 配置 > 啟動環境變量。
- **運行時配置持久化**：UI 配置保存到 `run/rocketmq-connection.properties`，啟動時自動加載，立即生效無需重啟。
- **RealRocketMqAdminClient 條件啟用**：`@ConditionalOnProperty(prefix="cluster.rocketmq", name="name-servers")`，配置了 NameServer 地址時自動替代 MockRocketMqAdminClient。
