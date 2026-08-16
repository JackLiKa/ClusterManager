# AGENTS.md — MQCluster 後端（Java）

> AI agent 後端導航與規約文件。項目總覽見上層 `../AGENTS.md`，中文文檔見 `../docs/zh/`。

## 1. 項目速覽

| 字段 | 值 |
| --- | --- |
| 名稱 | MQCluster（`io.github.jacklika:mqcluster`） |
| 版本 | `0.1.0-SNAPSHOT` |
| 倉庫 | https://github.com/JackLiKa/MQCluster |
| 許可證 | Apache 2.0 |
| 描述 | Spring Boot 4.1.0 後端，嵌入真實 Apache RocketMQ 5.3.3，提供 REST + WebSocket API。 |

### 技術棧

- Java 21、Spring Boot 4.1.0（Web / Validation / WebSocket / Actuator）
- Apache RocketMQ 5.3.3（嵌入式 NameServer + Broker）
- JUnit 5 + AssertJ + Spring Boot Test
- Maven（wrapper `mvnw`/`mvnw.cmd`）

## 2. 架構（六邊形 / 端口與適配器）

```
src/main/java/com/example/clustermanager
├─ api/            → REST 控制器、DTO、WebSocket 配置、異常處理
│  ├─ config/      → CorsConfig、WebSocketBrokerConfig、ClusterStreamProperties
│  ├─ controller/  → ClusterController（REST 端點）
│  └─ dto/         → 請求 DTO（帶校驗）
├─ application/    → 用例編排與 provider 選擇
│  ├─ model/       → 命令 record（ClusterSelection、*Command）
│  └─ service/     → ClusterFacadeService、ClusterProviderRegistry、MessageTemplateService、RocketMqConnectionConfigService
├─ core/           → 領域模型與端口（無外部依賴）
│  ├─ model/       → ClusterMode、ClusterNode、ClusterTopology、NodeStatus、MessageTemplate …
│  └─ port/        → IClusterProvider、ITopologyReader、INodeManager、IMonitoringGateway、IMessageWorkbench
└─ infrastructure/ → 適配器實現
   ├─ pseudo/      → PseudoClusterProvider + 子組件
   │  ├─ node/     → ManagedNode、NodeRegistry、NodeRole
   │  ├─ runtime/  → EmbeddedRocketMqRuntime、EmbeddedRocketMqNode、EmbeddedNodeSpec、PortPool
   │  ├─ messaging/→ EmbeddedMessageWorkbench
   │  ├─ topology/ → PseudoTopologySeeder
   │  └─ 網絡組件  → TapVirtualNetwork、NoOpTapNativeBridge
   └─ rocketmq/    → RocketMqClusterProvider、RocketMqAdminClient（+ Mock/Real）、RocketMqConnectionConfig
```

**依賴方向**：`api` → `application` → `core` ← `infrastructure`。`core` 零外部依賴。

## 3. 構建、運行、測試

```powershell
.\mvnw.cmd spring-boot:run          # 開發：http://localhost:8088
.\mvnw.cmd clean verify             # 完整構建 + 測試（148 tests, 2 skipped）
.\mvnw.cmd clean package            # fat JAR → target/mqcluster-0.1.0-SNAPSHOT.jar
```

### 啟動前必做步驟（Java 21 + 端口清理 + 殘留清理）

> ⚠️ **Java 版本要求**：後端需要 **Java 21**（class file version 65.0）。
> 若 `JAVA_HOME` 指向 Java 17（version 61.0），`spring-boot:run` 會報
> `UnsupportedClassVersionError: ... has been compiled by a more recent version of the Java Runtime`。
> 必須在啟動前設置 `JAVA_HOME` 為 Java 21。

```powershell
# Step 1: 設置 Java 21（當前 session，不影響系統全局）
$env:JAVA_HOME = "C:\Users\13026\.jdks\ms-21.0.9"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
java -version                        # 確認顯示 21.x.x

# Step 2: 殺掉佔用 8088 端口的舊進程
$proc = Get-NetTCPConnection -LocalPort 8088 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($proc) { Stop-Process -Id $proc.OwningProcess -Force; Write-Output "Killed PID $($proc.OwningProcess)" }
else { Write-Output "Port 8088 free" }
Start-Sleep -Seconds 2

# Step 3: 清理運行殘留（遇到 Broker 端口衝突或 store lock 錯誤時執行）
cd A:\project\Cluster\java
if (Test-Path run) { Remove-Item -Recurse -Force run; Write-Output "Cleaned run/" }
.\mvnw.cmd clean                     # 清理 target/（解決 class version 不匹配）

# Step 4: 啟動後端
.\mvnw.cmd spring-boot:run
# 等待 "Started ClusterManagerApplication" 日誌

# Step 5: 驗證
Invoke-WebRequest -Uri "http://localhost:8088/api/clusters/providers" -UseBasicParsing
# 應返回 200 + JSON
```

### 常見啟動失敗排查

| 症狀 | 原因 | 解決方案 |
| --- | --- | --- |
| `UnsupportedClassVersionError (class file version 65.0)` | JAVA_HOME 指向 Java 17 | `$env:JAVA_HOME = "C:\Users\13026\.jdks\ms-21.0.9"` |
| `Failed to bind to 0.0.0.0:8088` | 8088 端口被佔用 | 殺進程：`Get-NetTCPConnection -LocalPort 8088` → `Stop-Process` |
| `Address already in use: bind`（Broker 端口） | 上次 Broker 進程未完全退出 | 殺進程 + `Remove-Item -Recurse -Force run` + 等待 2 秒 |
| `Store lock file occupied` | RocketMQ store 鎖未釋放 | `Remove-Item -Recurse -Force run` |

### JVM 參數（重要）

`spring-boot-maven-plugin` 配置了 `--add-opens` JVM 參數：
- `--add-opens java.base/java.nio=ALL-UNNAMED`
- `--add-opens java.base/java.lang.reflect=ALL-UNNAMED`
- `--add-opens java.base/sun.nio.ch=ALL-UNNAMED`
- `--add-opens java.base/java.lang=ALL-UNNAMED`
- `--add-opens java.base/java.util=ALL-UNNAMED`
- `--add-exports java.base/jdk.internal.ref=ALL-UNNAMED`

**這些參數是必需的**——RocketMQ 5.3.3 在 Java 21 上 `Broker.shutdown()` 時需要反射訪問 `DirectByteBuffer.attachment()` 釋放內存映射文件。Java 21 模塊系統更嚴格，需要比 Java 17 多打開 `java.lang` 和 `java.util` 模塊。沒有這些參數，shutdown 拋 `InaccessibleObjectException`，store lock 文件無法刪除，重啟時 Broker 無法啟動。

## 4. 配置

`src/main/resources/application.properties`：

| 屬性 | 值 | 備註 |
| --- | --- | --- |
| `server.port` | `8088` | 後端端口 |
| `cluster.cors.allowed-origins` | `http://localhost:3000` | 前端源 |
| `cluster.pseudo.auto-start` | `false` | 種子節點初始 STOPPED |
| `cluster.pseudo.clean-store-on-start` | `true` | 啟動時清理舊 store 目錄 |
| `cluster.pseudo.work-dir` | `run/pseudo-cluster` | 嵌入式 RocketMQ 存儲 |
| `cluster.pseudo.health-timeout` | `5s` | 健康輪詢超時 |
| `cluster.stream.publish-interval-ms` | `5000` | 遙測推送週期 |

## 5. 關鍵實現細節

- **Broker 重啟延遲**：`EmbeddedRocketMqRuntime.restart()` 在 stop 後等待 1.5 秒再 start；`EmbeddedRocketMqNode.stop()` 在 shutdown 後等待 1 秒。確保線程池終止和文件句柄釋放。
- **Broker stats key**：RocketMQ `BROKER_PUT_NUMS`/`BROKER_GET_NUMS` 的 stats key 是 `brokerClusterName`（`embedded-cluster`）。
- **消息模板占位符**：`MessageTemplateService.render()` 替換 `{index}`/`{timestamp}`/`{uuid}`/`{random}`/`{topic}`。
- **外部 MQ 通信三層後備**：PSEUDO HOST 路徑按優先級查找 NameServer——登記的 HOST 節點 > 運行時 UI 配置 > 環境變量。
- **運行時配置持久化**：UI 配置保存到 `run/rocketmq-connection.properties`，啟動時自動加載。
- **RealRocketMqAdminClient 條件啟用**：`@ConditionalOnProperty(prefix="cluster.rocketmq", name="name-servers")`。

## 6. 規約

- **DTO/命令/模型用 record**（不可變值對象）
- **構造器注入**貫穿；無字段注入
- **按層分包**（六邊形）：`core` → `application` → `infrastructure` + `api`
- **錯誤處理**：`ApiExceptionHandler` 把 `IllegalArgumentException`→400、`IllegalStateException`→409
- **歷史修復標記**：代碼裡的 `// P0 修復` / `// P1 修復` / `// P3 修復` 註釋不要移除
- **推送前跑** `.\mvnw.cmd clean verify`——CI 跑同一道門
