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
│   ├── src/test/             → JUnit 5 + AssertJ 測試（148 tests）
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
- Java 21、Spring Boot 4.1.0
- Spring Web / Validation / WebSocket / Actuator
- Apache RocketMQ 5.3.3（嵌入式 NameServer + Broker）
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
.\mvnw.cmd clean verify             # 完整構建 + 測試（148 tests）
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

### 開發流程（完整啟動步驟）

> ⚠️ **重要**：後端需要 **Java 21**（class file version 65.0）。若 `JAVA_HOME` 指向 Java 17（version 61.0），
> `spring-boot:run` 會報 `UnsupportedClassVersionError`。啟動前必須確認 JDK 版本。
> 若 8088/3000 端口被佔用或上次運行殘留了進程/文件，項目會啟動失敗，需先清理。

#### Step 0：確認 Java 21 可用
```powershell
# 查看當前 JAVA_HOME（可能指向 Java 17，需切換到 Java 21）
echo $env:JAVA_HOME
# 確認 Java 21 路徑存在
Test-Path "C:\Users\13026\.jdks\ms-21.0.9\bin\java.exe"
```

#### Step 1：清理舊進程和端口佔用
```powershell
# 殺掉佔用 8088 端口的進程（後端）
$proc = Get-NetTCPConnection -LocalPort 8088 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($proc) { Stop-Process -Id $proc.OwningProcess -Force; Write-Output "Killed backend PID $($proc.OwningProcess)" }
else { Write-Output "Port 8088 free" }

# 殺掉佔用 3000 端口的進程（前端）
$proc = Get-NetTCPConnection -LocalPort 3000 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($proc) { Stop-Process -Id $proc.OwningProcess -Force; Write-Output "Killed frontend PID $($proc.OwningProcess)" }
else { Write-Output "Port 3000 free" }

# 等待端口釋放
Start-Sleep -Seconds 2
```

#### Step 2：清理運行殘留（可選，遇到啟動失敗時執行）
```powershell
# 清理嵌入式 RocketMQ store（節點鎖文件殘留會導致 Broker 無法啟動）
cd A:\project\Cluster\java
if (Test-Path run) { Remove-Item -Recurse -Force run; Write-Output "Cleaned run/" }

# 清理編譯產物（解決 class file version 不匹配）
.\mvnw.cmd clean
# 或手動刪除：if (Test-Path target) { Remove-Item -Recurse -Force target }

# 清理前端構建緩存（遇到前端異常時執行）
cd A:\project\Cluster\next
if (Test-Path .next) { Remove-Item -Recurse -Force .next; Write-Output "Cleaned .next/" }
```

#### Step 3：設置 Java 21 環境變量並啟動後端
```powershell
# 設置當前 session 的 JAVA_HOME 為 Java 21（不影響系統全局配置）
$env:JAVA_HOME = "C:\Users\13026\.jdks\ms-21.0.9"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# 驗證 Java 版本（應顯示 21.x.x）
java -version

# 啟動後端
cd A:\project\Cluster\java
.\mvnw.cmd spring-boot:run
# 等待看到 "Started ClusterManagerApplication" 日誌，後端在 http://localhost:8088
```

#### Step 4：啟動前端
```powershell
# 新開一個終端窗口
cd A:\project\Cluster\next
npm run dev
# 等待看到 "Ready" 日誌，前端在 http://localhost:3000
```

#### Step 5：驗證啟動成功
```powershell
# 驗證後端
Invoke-WebRequest -Uri "http://localhost:8088/api/clusters/providers" -UseBasicParsing
# 應返回 200 + JSON provider 列表

# 驗證前端
Invoke-WebRequest -Uri "http://localhost:3000" -UseBasicParsing
# 應返回 200 + HTML

# 打開瀏覽器訪問 http://localhost:3000
```

#### 快速啟動一鍵腳本（端口空閒且 Java 21 已配置時）
```powershell
# 後端（終端 1）
$env:JAVA_HOME = "C:\Users\13026\.jdks\ms-21.0.9"; $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
cd A:\project\Cluster\java; .\mvnw.cmd spring-boot:run

# 前端（終端 2）
cd A:\project\Cluster\next; npm run dev
```

### 嵌入式 RocketMQ JVM 參數
RocketMQ 5.3.3 嵌入式運行需要 Java 模块訪問權限（Java 21 需要更多 --add-opens）：
```
--add-opens java.base/java.nio=ALL-UNNAMED
--add-opens java.base/java.lang.reflect=ALL-UNNAMED
--add-opens java.base/sun.nio.ch=ALL-UNNAMED
--add-opens java.base/java.lang=ALL-UNNAMED
--add-opens java.base/java.util=ALL-UNNAMED
--add-exports java.base/jdk.internal.ref=ALL-UNNAMED
```

### 常見啟動失敗排查

| 症狀 | 原因 | 解決方案 |
| --- | --- | --- |
| `UnsupportedClassVersionError: class file version 65.0` | JAVA_HOME 指向 Java 17 | 設置 `$env:JAVA_HOME` 為 Java 21 路徑 |
| `Failed to bind to 0.0.0.0:8088` | 8088 端口被佔用 | 執行 Step 1 殺進程 |
| `Address already in use: bind`（Broker 端口） | 上次 Broker 進程未完全退出 | 殺進程 + 清理 `run/` 目錄 + 等待 2 秒 |
| `Store lock file occupied` | RocketMQ store 鎖未釋放 | 清理 `java/run/` 目錄 |
| 前端頁面空白 / API 404 | 後端未啟動或端口不匹配 | 確認後端 8088 已啟動，檢查 `next/.env.local` 的 `BACKEND_URL` |
| `EADDRINUSE: address already in use 0.0.0.0:3000` | 3000 端口被佔用 | 執行 Step 1 殺進程 |

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
- ✅ 節點啟動/停止/重啟（嵌入式真實 RocketMQ，含 JVM `--add-opens` 修復 shutdown 異常）
- ✅ 消息 produce/consume 模擬（真實 RocketMQ 投遞，3/3 成功）
- ✅ 消息模板系統（6 預定義模板 + 占位符替換 `{index}`/`{timestamp}`/`{uuid}`/`{random}`/`{topic}`）
- ✅ 外部 MQ 通信（PSEUDO HOST 路徑 + REAL 模式真實 produce/consume）
- ✅ 網頁連接配置面板（NameServer + 超時 + 消費者組前綴，持久化到文件，立即生效）
- ✅ 監控指標（真實 JVM CPU/內存 + RocketMQ broker put/get TPS）
- ✅ 活動日誌（審計日誌 + 實時 STOMP 推送）
- ✅ 主從複製（Master + Slave 同時運行）
- ✅ 後端測試套件（148 tests pass, 2 skipped）
- ✅ 前端 typecheck + ESLint + build 通過
- ✅ 手動節點登記（HOST + VIRTUAL）
- ✅ 前端重試機制（後端未就緒時自動重試 + 錯誤重試按鈕）
- ✅ 動態限流（基於本機 CPU/內存/磁盤 多因素綜合公式，每台機器上限不同）
- ✅ 數據面板（ECharts 折線圖時間序列 + 柱狀圖節點對比 + 實時數值卡片）
- ✅ 緊湊投遞結果（統計摘要 + 分頁列表，避免大量消息時頁面過長）

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
| `RateLimitServiceTest` | 20 | 6 種黑盒測試方法覆蓋動態限流計算 |
| **合計** | **148** | **2 條件跳過** |

### 嵌入式 RocketMQ 測試
需要 JVM 模块參數（已在 `pom.xml` Surefire `argLine` 配置）：
```
--add-opens java.base/java.nio=ALL-UNNAMED
--add-opens java.base/java.lang.reflect=ALL-UNNAMED
--add-opens java.base/sun.nio.ch=ALL-UNNAMED
--add-opens java.base/java.lang=ALL-UNNAMED
--add-opens java.base/java.util=ALL-UNNAMED
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
- **JVM `--add-opens` 參數**：`spring-boot-maven-plugin` 配置了 `--add-opens java.base/java.nio=ALL-UNNAMED` 等 JVM 參數，確保 RocketMQ 5.3.3 在 Java 21 上 `Broker.shutdown()` 時能反射訪問 `DirectByteBuffer.attachment()` 釋放內存映射文件。Java 21 模塊系統更嚴格，需要比 Java 17 多打開 `java.lang` 和 `java.util` 模塊。
- **Broker 重啟延遲**：`EmbeddedRocketMqRuntime.restart()` 在 stop 後等待 1.5 秒再 start，`EmbeddedRocketMqNode.stop()` 在 shutdown 後等待 1 秒確保線程池終止和文件句柄釋放。不等待會導致端口衝突或 store lock 文件佔用。
- **動態限流公式**：`RateLimitService` 基於多因素綜合公式計算最大消息量：`maxMessages = floor(min(memoryFactor, cpuFactor, diskFactor, ceiling) × 0.7)`。memoryFactor=可用堆MB×50, cpuFactor=邏輯核數×200, diskFactor=可用磁盤GB×100, ceiling=5000。每台機器看到的上限不同。API：`GET /api/clusters/rate-limit`。
- **數據面板**：`MetricsDashboard` 組件使用 ECharts 展示時間序列折線圖（CPU/內存/TPS 趨勢，30 個採樣點）+ 節點對比柱狀圖 + 實時數值卡片。雙 Y 軸：左軸百分比，右軸 TPS。
- **緊湊投遞結果**：`DeliveryResults` 組件用統計摘要（總數/成功/失敗/成功率）+ 分頁列表（每頁 10 條）替代逐條展示，避免大量消息時頁面過長。
