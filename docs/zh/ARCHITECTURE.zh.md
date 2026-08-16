# 架構文檔

本文檔描述 MQCluster 的架構——一個本地優先的 MQ 集群學習平台，嵌入真實 Apache RocketMQ 運行時，通過瀏覽器儀表盤操作。

---

## 目錄

- [總體概覽](#總體概覽)
- [六邊形架構（端口與適配器）](#六邊形架構端口與適配器)
- [後端分層](#後端分層)
  - [核心領域層](#核心領域層)
  - [應用層](#應用層)
  - [基礎設施層](#基礎設施層)
  - [API 層](#api-層)
- [前端架構](#前端架構)
- [嵌入式 RocketMQ](#嵌入式-rocketmq)
- [實時通信](#實時通信)
- [請求與數據流](#請求與數據流)
- [設計決策](#設計決策)

---

## 總體概覽

MQCluster 是一個**前後端分離**的應用：

```
┌─────────────────────────────────────────────────────────┐
│                    瀏覽器（用戶）                         │
│                                                         │
│  ┌───────────────────────────────────────────────────┐  │
│  │            Next.js 16 前端（端口 3000）            │  │
│  │  React 19 · Tailwind v4 · ECharts 5 · STOMP       │  │
│  └───────────────────┬───────────────────────────────┘  │
│                      │ /api（REST）  /ws（STOMP）        │
│                      │ Next.js rewrites 代理              │
│  ┌───────────────────▼───────────────────────────────┐  │
│  │         Spring Boot 4.1.0 後端（端口 8088）        │  │
│  │  六邊形架構 · 嵌入式 RocketMQ 5.3.3                │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

- **前端**是 Next.js 16 應用（App Router、React 19、TypeScript 嚴格模式），渲染儀表盤、拓撲圖、消息工作台和監控圖表。
- **後端**是 Spring Boot 4.1.0 應用，嵌入真實 Apache RocketMQ 5.3.3 運行時，提供 REST + WebSocket 端點。
- 通信通過 **REST**（`/api`）發送命令，通過 **STOMP over WebSocket**（`/ws`）接收實時事件流。

---

## 六邊形架構（端口與適配器）

後端遵循**六邊形架構**（也稱端口與適配器）模式。核心思想：業務邏輯位於中心，對外部世界一無所知。所有外部關注點（HTTP、數據庫、消息代理）都是插入核心定義端口的適配器。

```
                         ┌─────────────────────────────┐
   REST / WebSocket  ───▶│         API 層              │
                         │  控制器 · DTO · WebSocket    │
                         └──────────────┬──────────────┘
                                        │ 調用
                         ┌──────────────▼──────────────┐
                         │        應用層               │
                         │  用例 · Provider 選擇 · 編排 │
                         └──────────────┬──────────────┘
                                        │ 使用端口
                         ┌──────────────▼──────────────┐
                         │        核心領域             │
                         │  集群 · 節點 · 消息          │
                         │  端口接口（零依賴）          │
                         └──────────────▲──────────────┘
                                        │ 實現端口
                         ┌──────────────┴──────────────┐
                         │       基礎設施層            │
                         │  嵌入式 RocketMQ 適配器     │
                         │  RocketMQ admin 適配器      │
                         └─────────────────────────────┘
```

**依賴規則**：依賴始終**向內**指向。`core` 不依賴 `application`、`infrastructure` 或 `api`。`infrastructure` 依賴 `core`（實現端口），但 `core` 從不依賴 `infrastructure`。

---

## 後端分層

### 核心領域層

**位置**：`java/src/main/java/com/example/clustermanager/core/`

核心包含純領域模型和端口接口，**零外部依賴**——沒有 Spring、沒有 RocketMQ、沒有 HTTP 庫。

關鍵概念：

| 概念 | 描述 |
| --- | --- |
| `Cluster` | 聚合根，表示一個含多節點的邏輯 MQ 集群。 |
| `Node` | 單個 broker 或 nameserver 實例，有生命週期（STOPPED、STARTING、RUNNING、STOPPING）。 |
| `Message` | 生產到 topic 或從 topic 消費的消息。 |
| 端口接口 | `application` 調用、`infrastructure` 實現的接口（如集群生命週期、消息操作）。 |

因為核心無框架，可輕鬆進行單元測試，並可在任何上下文中復用。

### 應用層

**位置**：`java/src/main/java/com/example/clustermanager/application/`

應用層編排用例。它本身不含業務規則——協調領域模型並選擇合適的基礎設施適配器。

關鍵組件：**`ClusterProviderRegistry`**——根據 `(ClusterMode, MiddlewareType)` 元組選擇適配器。例如 `(PSEUDO, ROCKETMQ)` 選嵌入式 RocketMQ 適配器，`(REAL, ROCKETMQ)` 選外部 admin 適配器。

### 基礎設施層

**位置**：`java/src/main/java/com/example/clustermanager/infrastructure/`

此層用具體技術實現核心端口。現有兩個適配器：

| 適配器 | 包 | 描述 |
| --- | --- | --- |
| **Pseudo（嵌入式）** | `infrastructure/pseudo` | 進程內運行真實 Apache RocketMQ 5.3.3 NameServer + Broker。默認學習模式。 |
| **RocketMQ admin** | `infrastructure/rocketmq` | 通過 RocketMQ admin 客戶端連接外部 RocketMQ 集群。進階用途。 |

Pseudo 適配器是 MQCluster 的核心——它在 JVM 內啟動真實 RocketMQ 組件，學習者與真實 broker 交互，而非模擬。

### API 層

**位置**：`java/src/main/java/com/example/clustermanager/api/`

API 層是前端入口點，包含：

- **REST 控制器**——集群 CRUD、節點生命週期、消息生產/消費、指標等端點。
- **DTO**——映射領域模型的請求/響應對象。
- **WebSocket / STOMP 配置**——設置 `/ws` 端點和實時事件 topic 訂閱。
- **異常處理**——全局異常處理器，將領域錯誤轉換為 HTTP 響應。

---

## 前端架構

**位置**：`next/src/`

前端遵循單向數據流的分層結構：

```
page.tsx  ──▶  components  ──▶  hooks  ──▶  lib/api.ts  ──▶  後端 REST / STOMP
```

| 層 | 位置 | 職責 |
| --- | --- | --- |
| **頁面** | `src/app/` | App Router 頁面。`page.tsx` 是主儀表盤；`guide/` 是學習指南。儀表盤持有集群選擇狀態並組合子組件。 |
| **組件** | `src/components/` | 可復用 UI：拓撲圖（ECharts）、消息工作台、操作面板、監控指標、活動日誌。 |
| **Hooks** | `src/hooks/` | 自定義 React Hooks。`use-cluster-streams.ts` 訂閱 STOMP/SockJS 實時流並向組件暴露實時數據。 |
| **Lib** | `src/lib/` | `api.ts` 是統一 axios 客戶端，含類型化端點函數。工具函數也在此。 |
| **Types** | `src/types/` | 共享 TypeScript 類型定義，鏡像後端 DTO。 |

### API 代理

`next.config.ts` 配置 rewrites，將 `/api/*` 和 `/ws/*` 代理到 `http://localhost:8088`（後端）。這避免了開發中的 CORS 問題，並讓前端代碼與環境無關。

---

## 嵌入式 RocketMQ

MQCluster 的標誌性特徵是在後端 JVM 內運行**真實** Apache RocketMQ 5.3.3 運行時——不是 mock 或模擬器。

### 工作原理

1. Pseudo 適配器啟動**進程內 NameServer**，處理服務發現。
2. 然後啟動一個或多個**進程內 Broker**（Master 和/或 Slave），向 NameServer 註冊。
3. Broker 使用本地工作目錄（默認 `run/pseudo-cluster/`）存儲消息（CommitLog、ConsumeQueue 等）——與生產 broker 完全一樣，只是規模更小。
4. 消息生產/消費操作走**真實 RocketMQ 客戶端 API**，學習者體驗真實 broker 行為：topic 路由、消息存儲、消費者 offset、主從複製。

### 為什麼嵌入式？

| 方案 | 優點 | 缺點 |
| --- | --- | --- |
| **Mock broker** | 易實現 | 不真實；學習者無法信任行為 |
| **Docker 集群** | 真實 | 笨重；需 Docker；不便攜 |
| **嵌入式（MQCluster）** ✅ | 真實 broker、零外部依賴、單命令 | 僅限單機規模（學習場景足夠） |

### 種子拓撲

默認情況下，MQCluster 種子化 3 節點拓撲：

| 節點 | 角色 | 初始狀態 |
| --- | --- | --- |
| `rmq-ns-01` | NameServer | STOPPED |
| `rmq-broker-m-01` | Master Broker | STOPPED |
| `rmq-broker-s-01` | Slave Broker | STOPPED |

節點初始為 STOPPED 狀態（`cluster.pseudo.auto-start=false`），學習者可手動啟動並觀察生命週期轉換。

---

## 實時通信

MQCluster 使用 **STOMP over WebSocket**（含 SockJS 回退）向前端推送實時事件。

| 頻道 | 方向 | 內容 |
| --- | --- | --- |
| `/topic/cluster/{id}/topology` | 服務端 → 客戶端 | 節點啟停時的拓撲更新 |
| `/topic/cluster/{id}/metrics` | 服務端 → 客戶端 | 週期性 CPU、內存、網絡 IO（每節點） |
| `/topic/cluster/{id}/activity` | 服務端 → 客戶端 | 每次操作的審計日誌條目 |

前端的 `use-cluster-streams` hook 訂閱這些 topic 並將更新注入 React state，UI 在毫秒級反映集群變化。

---

## 請求與數據流

以下是啟動節點的典型流程：

```
1. 用戶在 UI 點擊 broker-master-1 的「啟動」
2. 前端 POST /api/clusters/{id}/nodes/{nodeId}/start
3. API 控制器 → 應用用例
4. 應用通過 ClusterProviderRegistry 選適配器（PSEUDO, ROCKETMQ）
5. Pseudo 適配器啟動嵌入式 broker 進程
6. Broker 向進程內 NameServer 註冊
7. 適配器發布 TopologyChangedEvent 到 STOMP topic
8. 前端 hook 接收事件 → React 重新渲染拓撲圖
9. 活動日誌條目也推送到 /topic/.../activity
```

消息生產/消費遵循類似路徑，在適配器內額外調用真實 RocketMQ 客戶端。

---

## 設計決策

### 為什麼用六邊形架構？

- **可測試性**——核心領域可無 Spring 或 RocketMQ 進行單元測試。
- **可替換性**——`ClusterProviderRegistry` 讓我們插入新中間件類型或部署模式，無需觸碰核心或 API。
- **清晰性**——每層單一職責，代碼庫易於導航和教學（這正是本項目的意義）。

### 為什麼用 Next.js（而非 Vue 或純 React）？

- **App Router** 開箱即用地提供基於文件的路由和服務端組件。
- **API rewrites** 讓代理到後端變得簡單，零 CORS 煩惱。
- **React 19** + **TypeScript 嚴格模式** 提供強類型安全和現代 hooks 人體工學。
- **Tailwind CSS v4** 保持快速一致的樣式，無需上下文切換。

### 為什麼嵌入 RocketMQ 而非 mock？

- 學習者值得與**真實事物**交互。Mock 教的是 mock，不是 RocketMQ。
- 嵌入避免了 Docker 或遠程集群的開銷，降低入門門檻（一條 Java 命令、一條 npm 命令）。
- 嵌入式 broker 使用與生產相同的存儲格式和客戶端協議，知識直接遷移。

---

<div align="center">

**[⬆ 回到頂部](#架構文檔)**

</div>
