'use client'

/**
 * 學習指南頁面——MQ 集群學習教程。
 *
 * 職責：
 * - 展示 RocketMQ 集群學習的核心知識點
 * - 提供操作指引，幫助學生理解消息隊列集群的概念
 * - 內容為靜態中文教程，不依賴後端 API
 */

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'

export default function GuidePage() {
  return (
    <div className="container mx-auto p-4 max-w-4xl space-y-6">
      <div>
        <h1 className="text-3xl font-bold">MQ 集群學習指南</h1>
        <p className="text-muted-foreground mt-2">
          本指南幫助你理解 RocketMQ 集群的核心概念，並通過本平台進行實戰練習。
        </p>
      </div>

      {/* 章節 1：RocketMQ 架構基礎 */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Badge>1</Badge>
            RocketMQ 架構基礎
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3 text-sm leading-relaxed">
          <p>
            RocketMQ 集群由三類核心組件組成：
          </p>
          <ul className="list-disc pl-6 space-y-2">
            <li>
              <strong>NameServer</strong>：輕量級服務發現組件，Broker 啟動時向其註冊，
              Producer/Consumer 從其獲取路由信息。多個 NameServer 之間互不通信，
              各自獨立維護路由表。
            </li>
            <li>
              <strong>Broker（Master）</strong>：消息存儲與轉發的核心節點，
              負責接收 Producer 發送的消息並持久化到 commitlog，
              同時為 Consumer 提供消費服務。
            </li>
            <li>
              <strong>Broker（Slave）</strong>：從 Master 同步數據的副本節點，
              提供 HA（高可用）能力。Master 故障時，Consumer 可切換到 Slave 繼續消費。
            </li>
          </ul>
          <p className="text-muted-foreground">
            在本平台的拓撲圖中，你可以看到這三類節點及其連接關係。
            藍色圓圈為 NameServer，綠色方塊為 Broker Master，橙色方塊為 Broker Slave。
          </p>
        </CardContent>
      </Card>

      {/* 章節 2：節點生命週期 */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Badge>2</Badge>
            節點生命週期管理
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3 text-sm leading-relaxed">
          <p>
            在「節點管理」面板中，你可以對每個節點執行啟動、停止、重啟操作。
          </p>
          <div className="rounded-md bg-muted p-3 text-xs space-y-1">
            <p><strong>啟動順序</strong>：必須先啟動 NameServer，再啟動 Broker。</p>
            <p>原因：Broker 啟動時需要向 NameServer 註冊，若 NameServer 未運行則註冊失敗。</p>
          </div>
          <p>
            <strong>實戰練習</strong>：
          </p>
          <ol className="list-decimal pl-6 space-y-1">
            <li>點擊 NameServer 節點的「啟動」按鈕，等待狀態變為 RUNNING</li>
            <li>點擊 Broker Master 的「啟動」按鈕</li>
            <li>觀察拓撲圖中鏈路狀態的變化（從灰色虛線變為綠色實線）</li>
            <li>嘗試停止 Broker，觀察拓撲變化</li>
            <li>重啟 Broker，觀察恢復過程</li>
          </ol>
        </CardContent>
      </Card>

      {/* 章節 3：消息模型 */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Badge>3</Badge>
            消息模型——Topic、Producer、Consumer
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3 text-sm leading-relaxed">
          <p>
            在「消息工作台」中，你可以模擬真實的消息生產與消費流程。
          </p>
          <ul className="list-disc pl-6 space-y-2">
            <li>
              <strong>Topic</strong>：消息的主題分類，類似於郵箱地址。
              Producer 向指定 Topic 發送消息，Consumer 訂閱 Topic 接收消息。
            </li>
            <li>
              <strong>Producer</strong>：消息生產者，負責創建並發送消息到 Broker。
            </li>
            <li>
              <strong>Consumer Group</strong>：消費者組，同一組內的 Consumer 共享消費進度
              （廣播模式除外）。
            </li>
            <li>
              <strong>Message Key</strong>：消息鍵，用於唯一標識一條消息，便於追蹤。
            </li>
          </ul>
          <p>
            <strong>實戰練習</strong>：
          </p>
          <ol className="list-decimal pl-6 space-y-1">
            <li>確保 NameServer 和至少一個 Broker 已啟動</li>
            <li>在消息工作台中輸入 Topic 名稱（如 TestTopic）</li>
            <li>選擇生產者節點（任一運行中的 Broker）</li>
            <li>設置消息數量為 3，點擊「發送消息」</li>
            <li>觀察投遞結果——成功表示消息已真實通過嵌入式 RocketMQ 生產並消費</li>
          </ol>
        </CardContent>
      </Card>

      {/* 章節 4：主從複製 */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Badge>4</Badge>
            主從複製與高可用
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3 text-sm leading-relaxed">
          <p>
            本平台默認種子化了 1 個 Master Broker 和 1 個 Slave Broker，
            模擬真實的主從複製架構。
          </p>
          <ul className="list-disc pl-6 space-y-2">
            <li>
              <strong>同步複製</strong>：Master 將消息寫入後，等待 Slave 同步完成才返回 ACK。
              數據安全但延遲略高。
            </li>
            <li>
              <strong>異步複製</strong>：Master 寫入後立即返回 ACK，Slave 異步同步。
              性能高但可能有少量數據丟失風險。
            </li>
          </ul>
          <p>
            <strong>實戰練習</strong>：啟動 Master 和 Slave Broker，在拓撲圖中觀察
            Master→Slave 的鏈路。停止 Master 後，Slave 仍可提供消費服務。
          </p>
        </CardContent>
      </Card>

      {/* 章節 5：監控與可觀測性 */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Badge>5</Badge>
            監控與可觀測性
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3 text-sm leading-relaxed">
          <p>
            本平台提供實時監控能力：
          </p>
          <ul className="list-disc pl-6 space-y-2">
            <li><strong>監控指標</strong>：CPU、內存使用率、網絡 IO 速率，每 5 秒刷新</li>
            <li><strong>活動日誌</strong>：節點操作、服務變更的審計日誌，實時推送</li>
            <li><strong>拓撲圖</strong>：節點狀態和鏈路健康度的可視化展示</li>
          </ul>
          <p className="text-muted-foreground">
            所有監控數據通過 WebSocket STOMP 協議實時推送，無需手動刷新頁面。
          </p>
        </CardContent>
      </Card>

      {/* 章節 6：動態限流與資源安全 */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Badge>6</Badge>
            動態限流與資源安全
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3 text-sm leading-relaxed">
          <p>
            本平台根據你的電腦硬件配置動態計算最大安全消息量，防止本地系統卡死。
          </p>
          <ul className="list-disc pl-6 space-y-2">
            <li>
              <strong>多因素綜合公式</strong>：上限 = min(內存因子, CPU 因子, 磁盤因子, 絕對上限) × 安全係數
            </li>
            <li>
              <strong>內存因子</strong>：可用 JVM 堆 MB × 50 條/MB
            </li>
            <li>
              <strong>CPU 因子</strong>：邏輯核心數 × 200 條/核
            </li>
            <li>
              <strong>磁盤因子</strong>：可用磁盤 GB × 100 條/GB
            </li>
            <li>
              <strong>安全係數</strong>：0.7（保留 30% 餘量）
            </li>
          </ul>
          <div className="rounded-md bg-muted p-3 text-xs space-y-1">
            <p><strong>示例</strong>：16 核 CPU、3883MB 可用堆、238GB 磁盤 → 上限 2240 條/批次</p>
            <p>不同電腦看到的上限不同——這是因為每台機器的硬件配置不同。</p>
          </div>
          <p className="text-muted-foreground">
            超過此限制的請求會被後端直接拒絕（HTTP 400），前端也會在輸入框旁顯示當前上限。
          </p>
        </CardContent>
      </Card>

      {/* 章節 7：數據面板 */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Badge>7</Badge>
            數據面板與趨勢分析
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3 text-sm leading-relaxed">
          <p>
            數據面板提供集群級別的趨勢可視化，幫助你理解集群隨時間的變化。
          </p>
          <ul className="list-disc pl-6 space-y-2">
            <li>
              <strong>折線圖</strong>：CPU、內存、投遞 TPS、消費 TPS 的時間序列趨勢（30 個採樣點）
            </li>
            <li>
              <strong>柱狀圖</strong>：各節點當前指標的橫向對比，直觀看出哪個節點壓力最大
            </li>
            <li>
              <strong>數值卡片</strong>：平均 CPU、平均內存、總投遞 TPS、總消費 TPS 的實時聚合值
            </li>
          </ul>
          <p className="text-muted-foreground">
            雙 Y 軸設計：左軸為百分比（CPU/內存），右軸為消息速率（TPS）。
          </p>
        </CardContent>
      </Card>

      {/* 進階學習模塊 */}
      <div className="pt-4">
        <h2 className="text-2xl font-bold border-b pb-2 mb-4">進階學習模塊</h2>
        <p className="text-muted-foreground text-sm mb-4">
          以下概念超出本平台當前的實作範圍，但對深入理解 RocketMQ 生產環境使用至關重要。
          建議在掌握基礎模塊後閱讀，並查閱 RocketMQ 官方文檔進行動手實驗。
        </p>
      </div>

      {/* 章節 8：消息發送模式 */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Badge variant="secondary">進階</Badge>
            8. 消息發送模式——同步、異步、單向
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3 text-sm leading-relaxed">
          <p>
            RocketMQ 支持三種消息發送模式，適用於不同的可靠性和延遲需求：
          </p>
          <ul className="list-disc pl-6 space-y-2">
            <li>
              <strong>同步發送（Sync Send）</strong>：Producer 發送後阻塞等待 Broker 返回 ACK。
              可靠性最高，延遲最大。本平台的「消息工作台」使用此模式。
              <div className="rounded-md bg-muted p-2 text-xs mt-1 font-mono">
                {'SendResult result = producer.send(msg);'}
              </div>
            </li>
            <li>
              <strong>異步發送（Async Send）</strong>：Producer 發送後立即返回，通過回調函數接收結果。
              適用於對響應時間敏感的場景。
              <div className="rounded-md bg-muted p-2 text-xs mt-1 font-mono">
                {'producer.send(msg, new SendCallback() {'}{'}'}
                <br />&nbsp;&nbsp;{'public void onSuccess(SendResult r) {'}{' } {'}{'}'}
                <br />&nbsp;&nbsp;{'public void onException(Throwable e) {'}{' } {'}{'}'}
                <br/>{'}'}{'}'});
              </div>
            </li>
            <li>
              <strong>單向發送（One-way Send）</strong>：Producer 只負責發送，不等待 ACK 也不觸發回調。
              延遲最低，可靠性最低，適用於日誌收集等容忍丟失的場景。
              <div className="rounded-md bg-muted p-2 text-xs mt-1 font-mono">
                {'producer.sendOneway(msg);'}
              </div>
            </li>
          </ul>
          <div className="rounded-md bg-blue-50 dark:bg-blue-950 p-3 text-xs">
            <strong>本平台現狀</strong>：消息工作台僅展示同步發送。
            如需實驗異步/單向模式，可在外部 RocketMQ 項目中使用 RocketMQ Client SDK 實作，
            並通過「連接配置」面板將本平台連接到你的本地 RocketMQ。
          </div>
        </CardContent>
      </Card>

      {/* 章節 9：消費模式 */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Badge variant="secondary">進階</Badge>
            9. 消費模式——Push vs Pull、順序消費
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3 text-sm leading-relaxed">
          <p>
            RocketMQ 提供兩種消費者實作模式和兩種消費順序語義：
          </p>
          <ul className="list-disc pl-6 space-y-2">
            <li>
              <strong>Push 消費者</strong>：類似事件驅動，Broker 有消息時主動推送給 Consumer。
              實作上仍是 Pull 長輪詢，但對開發者透明。適用於大多數業務場景。
            </li>
            <li>
              <strong>Pull 消費者</strong>：Consumer 主動拉取消息，可精確控制拉取速率和偏移量。
              適用於流式處理或批量消費場景。
            </li>
            <li>
              <strong>並發消費</strong>：多線程同時消費同一隊列的消息，無順序保證。吞吐量高。
            </li>
            <li>
              <strong>順序消費</strong>：保證同一 MessageQueue 內的消息按發送順序消費。
              吞吐量較低，適用於訂單狀態流轉等有序場景。
            </li>
          </ul>
          <div className="rounded-md bg-blue-50 dark:bg-blue-950 p-3 text-xs">
            <strong>本平台現狀</strong>：消息工作台使用 Push + 並發消費。
            順序消費需要在外部項目中配置 <code>MessageListenerOrderly</code> 並驗證消息順序。
          </div>
        </CardContent>
      </Card>

      {/* 章節 10：事務消息 */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Badge variant="secondary">進階</Badge>
            10. 事務消息——最終一致性方案
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3 text-sm leading-relaxed">
          <p>
            事務消息是 RocketMQ 的特色功能，用於實現分布式事務的最終一致性。
          </p>
          <ol className="list-decimal pl-6 space-y-2">
            <li>
              Producer 發送「半消息」（Half Message）到 Broker，此消息對 Consumer 不可見
            </li>
            <li>
              Broker 返回發送成功後，Producer 執行本地事務（如扣庫存、寫訂單）
            </li>
            <li>
              本地事務成功 → Producer 向 Broker 發送 <code>COMMIT</code>，消息變為可見
            </li>
            <li>
              本地事務失敗 → Producer 發送 <code>ROLLBACK</code>，消息被刪除
            </li>
            <li>
              若 Broker 未收到二次確認（如 Producer 宕機），Broker 會回查 Producer 的本地事務狀態
            </li>
          </ol>
          <div className="rounded-md bg-muted p-3 text-xs font-mono">
            {'// 實作 TransactionListener'}<br />
            {'TransactionMQProducer producer = new TransactionMQProducer("group");'}<br />
            {'producer.setTransactionListener(new TransactionListener() {'}{'}'}<br />
            &nbsp;&nbsp;{'public LocalTransactionState executeLocalTransaction(Message msg, Object arg) {'}{'}'}<br />
            &nbsp;&nbsp;&nbsp;&nbsp;{'// 執行本地事務'}<br />
            &nbsp;&nbsp;&nbsp;&nbsp;{'return LocalTransactionState.COMMIT_MESSAGE;'}<br />
            &nbsp;&nbsp;{'}'}{'}'}<br />
            &nbsp;&nbsp;{'public LocalTransactionState checkLocalTransaction(MessageExt msg) {'}{'}'}<br />
            &nbsp;&nbsp;&nbsp;&nbsp;{'// 回查本地事務狀態'}<br />
            &nbsp;&nbsp;&nbsp;&nbsp;{'return LocalTransactionState.COMMIT_MESSAGE;'}<br />
            &nbsp;&nbsp;{'}'}{'}'}<br />
            {'}'}{'}'};
          </div>
          <div className="rounded-md bg-blue-50 dark:bg-blue-950 p-3 text-xs">
            <strong>本平台現狀</strong>：未實作事務消息 UI。建議在本地啟動獨立 RocketMQ 項目實驗，
            本平台可通過連接配置面板協助觀察 Broker 狀態。
          </div>
        </CardContent>
      </Card>

      {/* 章節 11：死信隊列 */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Badge variant="secondary">進階</Badge>
            11. 死信隊列（DLQ）與消息重試
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3 text-sm leading-relaxed">
          <p>
            當消息消費失敗時，RocketMQ 會自動重試。超過最大重試次數（默認 16 次）後，
            消息進入死信隊列（Dead Letter Queue）。
          </p>
          <ul className="list-disc pl-6 space-y-2">
            <li>
              <strong>重試機制</strong>：消費失敗後，消息返回 Broker 並延遲重試。
              重試間隔遞增：10s、30s、1m、2m、3m、4m、5m、6m、7m、8m、9m、10m、20m、30m、1h、2h
            </li>
            <li>
              <strong>死信 Topic 命名</strong>：<code>%DLQ%消費者組名</code>
            </li>
            <li>
              <strong>處理方式</strong>：人工訂閱死信 Topic 排查問題，或寫定時任務自動補償
            </li>
          </ul>
          <div className="rounded-md bg-blue-50 dark:bg-blue-950 p-3 text-xs">
            <strong>本平台現狀</strong>：嵌入式集群未暴露 DLQ 管理界面。
            可通過 <code>mqadmin</code> 命令行工具查詢死信隊列：
            <code className="block mt-1">mqadmin queryDLQMsg -g 消費者組 -t Topic</code>
          </div>
        </CardContent>
      </Card>

      {/* 章節 12：消息過濾 */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Badge variant="secondary">進階</Badge>
            12. 消息過濾——Tag 與 SQL92
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3 text-sm leading-relaxed">
          <p>
            RocketMQ 支持在 Consumer 端按條件過濾消息，避免接收無關消息：
          </p>
          <ul className="list-disc pl-6 space-y-2">
            <li>
              <strong>Tag 過濾</strong>：Producer 在消息上設置 Tag，Consumer 訂閱時指定 Tag。
              簡單高效，適用於粗粒度分類。
              <div className="rounded-md bg-muted p-2 text-xs mt-1 font-mono">
                {'producer.send(new Message("Topic", "TagA", body));'}<br />
                {'consumer.subscribe("Topic", "TagA || TagB");'}
              </div>
            </li>
            <li>
              <strong>SQL92 過濾</strong>：使用 SQL 表達式過濾消息屬性，支持複雜條件。
              <div className="rounded-md bg-muted p-2 text-xs mt-1 font-mono">
                {'consumer.subscribe("Topic",'}<br />
                &nbsp;&nbsp;{'MessageSelector.bySql("age > 18 AND gender = \'F\'")'};
              </div>
            </li>
            <li>
              <strong>類過濾</strong>：通過實作 <code>MessageFilter</code> 介面在 Broker 端過濾，
              適用於需要自定義邏輯的場景。
            </li>
          </ul>
          <div className="rounded-md bg-blue-50 dark:bg-blue-950 p-3 text-xs">
            <strong>本平台現狀</strong>：消息工作台未暴露 Tag/SQL 過濾選項。
            可在外部項目中實驗，本平台用於觀察基礎消息流轉。
          </div>
        </CardContent>
      </Card>

      {/* 章節 13：進階運維 */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <Badge variant="secondary">進階</Badge>
            13. 生產環境運維要點
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3 text-sm leading-relaxed">
          <p>
            本平台幫助你理解集群拓撲和消息流轉，生產環境還需關注以下運維要點：
          </p>
          <ul className="list-disc pl-6 space-y-2">
            <li>
              <strong>消息堆積</strong>：Consumer 消費速度跟不上 Producer 發送速度時，
              消息在 Broker 上堆積。需監控隊列深度並擴容 Consumer。
            </li>
            <li>
              <strong>消息軌跡</strong>：開啟消息軌跡（Message Trace）可追蹤消息從產生到消費的全鏈路，
              用於問題排查。本平台的活動日誌是簡化版的軌跡記錄。
            </li>
            <li>
              <strong>Broker 擴容</strong>：新增 Broker 時需配置相同的 NameServer 地址，
              Topic 的 MessageQueue 會自動分配到新 Broker。
            </li>
            <li>
              <strong>Dledger 集群</strong>：RocketMQ 5.x 推薦使用 Dledger 替代 Master-Slave，
              基於 Raft 協議實現自動故障轉移和選主。
            </li>
            <li>
              <strong>Controller 模式</strong>：RocketMQ 5.x 的 Controller 組件提供統一的集群管理，
              支持自動 Master 選舉和 Broker 上下線。
            </li>
          </ul>
          <p className="text-muted-foreground">
            建議查閱 <a href="https://rocketmq.apache.org/docs/" className="underline" target="_blank" rel="noopener noreferrer">RocketMQ 官方文檔</a>
            {' '}深入了解生產環境最佳實踐。
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
