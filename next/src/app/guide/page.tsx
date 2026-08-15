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
    </div>
  )
}
