'use client'

/**
 * 消息工作台組件——topic 級 produce/consume 模擬表單。
 *
 * 職責：
 * - 提供消息模擬表單（topic、consumerGroup、消息數量、payload 模板、producer/consumer 節點選擇）
 * - 支持預定義消息模板選擇 + 自定義模板輸入
 * - 模板支持占位符替換（{index}、{timestamp}、{uuid}、{random}、{topic}）
 * - 調用 simulateMessages API 執行真實的 RocketMQ produce/consume
 * - 展示每條消息的投遞結果（成功/失敗 + 詳情）
 * - 表單字段校驗（topic 和 consumerGroup 必填，消息數量 >= 1）
 *
 * 與後端的交互：
 * - GET /api/clusters/message-templates（獲取模板列表）
 * - POST /api/clusters/{...}/messages/simulate
 * - 請求體：MessageSimulationRequest
 * - 響應體：MessageSimulationResult（含每條消息的投遞結果）
 */

import { useEffect, useState } from 'react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Badge } from '@/components/ui/badge'
import { simulateMessages, fetchMessageTemplates, fetchRateLimit } from '@/lib/api'
import type {
  ClusterSelection,
  ClusterTopology,
  MessageDeliveryResult,
  MessageTemplate,
  RateLimitResult,
} from '@/types/cluster'

interface MessageWorkbenchCardProps {
  selection: ClusterSelection
  topology: ClusterTopology | null
}

export function MessageWorkbenchCard({ selection, topology }: MessageWorkbenchCardProps) {
  // 表單狀態
  const [topic, setTopic] = useState('TestTopic')
  const [consumerGroup, setConsumerGroup] = useState('test-consumer-group')
  const [messageCount, setMessageCount] = useState(3)
  const [payloadTemplate, setPayloadTemplate] = useState('{"msg":"hello cluster"}')
  const [producerNodeId, setProducerNodeId] = useState('')
  const [consumerNodeIds, setConsumerNodeIds] = useState<string>('')

  // 模板狀態
  const [templates, setTemplates] = useState<MessageTemplate[]>([])
  const [selectedTemplateId, setSelectedTemplateId] = useState<string>('custom')

  // 限流狀態
  const [rateLimit, setRateLimit] = useState<RateLimitResult | null>(null)

  // 結果狀態
  const [results, setResults] = useState<MessageDeliveryResult[] | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // 從拓撲中提取可用節點
  const brokerNodes = topology?.nodes.filter((n) => n.labels?.role?.includes('broker')) ?? []
  const allNodes = topology?.nodes ?? []

  // 加載模板列表和限流配置
  useEffect(() => {
    let cancelled = false
    fetchMessageTemplates()
      .then((data) => {
        if (!cancelled) setTemplates(data)
      })
      .catch(() => {
        // 模板加載失敗時靜默處理，用戶仍可手動輸入
      })
    fetchRateLimit()
      .then((data) => {
        if (!cancelled) setRateLimit(data)
      })
      .catch(() => {
        // 限流加載失敗時靜默處理
      })
    return () => {
      cancelled = true
    }
  }, [])

  /** 模板選擇變化時更新 payload */
  function handleTemplateChange(templateId: string | null) {
    const id = templateId ?? 'custom'
    setSelectedTemplateId(id)
    if (id === 'custom') {
      // 自定義模式，保留當前輸入
      return
    }
    const template = templates.find((t) => t.id === id)
    if (template) {
      setPayloadTemplate(template.template)
    }
  }

  /** 執行消息模擬 */
  async function handleSimulate() {
    if (!topic.trim() || !consumerGroup.trim()) {
      setError('Topic 和消費者組不能為空')
      return
    }
    if (messageCount < 1) {
      setError('消息數量至少為 1')
      return
    }
    if (!producerNodeId) {
      setError('請選擇生產者節點')
      return
    }
    // 動態限流校驗——前端提前攔截，避免無效請求
    if (rateLimit && messageCount > rateLimit.maxMessages) {
      setError(
        `消息數量 ${messageCount} 超過本機安全上限 ${rateLimit.maxMessages}（基於 CPU ${rateLimit.systemProfile.logicalCores} 核 / 堆 ${rateLimit.systemProfile.availableHeapMb}MB 計算）`,
      )
      return
    }

    setLoading(true)
    setError(null)
    setResults(null)

    try {
      const consumerList = consumerNodeIds
        ? consumerNodeIds.split(',').map((s) => s.trim()).filter(Boolean)
        : brokerNodes.map((n) => n.nodeId)

      const result = await simulateMessages(selection, {
        topic: topic.trim(),
        consumerGroup: consumerGroup.trim(),
        messageCount,
        payloadTemplate: payloadTemplate || undefined,
        producerNodeId,
        consumerNodeIds: consumerList,
      })
      setResults(result.deliveries)
    } catch (err) {
      setError(err instanceof Error ? err.message : '消息模擬失敗')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>消息工作台</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {/* 表單區 */}
        <div className="grid grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label htmlFor="topic">Topic</Label>
            <Input
              id="topic"
              value={topic}
              onChange={(e) => setTopic(e.target.value)}
              placeholder="輸入 topic 名稱"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="consumerGroup">消費者組</Label>
            <Input
              id="consumerGroup"
              value={consumerGroup}
              onChange={(e) => setConsumerGroup(e.target.value)}
              placeholder="輸入消費者組名稱"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="messageCount">
              消息數量
              {rateLimit && (
                <span className="ml-2 text-xs text-blue-600">
                  上限: {rateLimit.maxMessages}
                </span>
              )}
            </Label>
            <Input
              id="messageCount"
              type="number"
              min={1}
              max={rateLimit?.maxMessages ?? undefined}
              value={messageCount}
              onChange={(e) => setMessageCount(Number(e.target.value) || 1)}
            />
            {rateLimit && messageCount > rateLimit.maxMessages && (
              <p className="text-xs text-red-600">
                超過本機安全上限 {rateLimit.maxMessages}
              </p>
            )}
          </div>
          <div className="space-y-2">
            <Label htmlFor="producerNodeId">生產者節點</Label>
            <Select value={producerNodeId} onValueChange={(v) => setProducerNodeId(v ?? '')}>
              <SelectTrigger id="producerNodeId">
                <SelectValue placeholder="選擇生產者節點" />
              </SelectTrigger>
              <SelectContent>
                {allNodes.map((node) => (
                  <SelectItem key={node.nodeId} value={node.nodeId}>
                    {node.displayName} ({node.status})
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="col-span-2 space-y-2">
            <Label htmlFor="templateSelect">消息模板</Label>
            <Select value={selectedTemplateId} onValueChange={handleTemplateChange}>
              <SelectTrigger id="templateSelect">
                <SelectValue placeholder="選擇預定義模板或自定義" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="custom">自定義（手動輸入）</SelectItem>
                {templates.map((t) => (
                  <SelectItem key={t.id} value={t.id}>
                    {t.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {selectedTemplateId !== 'custom' && (
              <p className="text-xs text-muted-foreground">
                {templates.find((t) => t.id === selectedTemplateId)?.description}
              </p>
            )}
          </div>
          <div className="col-span-2 space-y-2">
            <Label htmlFor="payloadTemplate">
              Payload 模板
              <span className="ml-2 text-xs text-muted-foreground">
                支持占位符：{'{index}'}、{'{timestamp}'}、{'{uuid}'}、{'{random}'}、{'{topic}'}
              </span>
            </Label>
            <Input
              id="payloadTemplate"
              value={payloadTemplate}
              onChange={(e) => {
                setPayloadTemplate(e.target.value)
                setSelectedTemplateId('custom')
              }}
              placeholder='例如: {"orderId":"{uuid}","index":{index}}'
              className="font-mono text-xs"
            />
          </div>
          <div className="col-span-2 space-y-2">
            <Label htmlFor="consumerNodeIds">消費者節點（逗號分隔，留空則自動使用所有 Broker）</Label>
            <Input
              id="consumerNodeIds"
              value={consumerNodeIds}
              onChange={(e) => setConsumerNodeIds(e.target.value)}
              placeholder="例如: rmq-broker-m-01,rmq-broker-s-01"
            />
          </div>
        </div>

        {/* 操作按鈕 */}
        <Button onClick={handleSimulate} disabled={loading} className="w-full">
          {loading ? '模擬中...' : '發送消息'}
        </Button>

        {/* 錯誤提示 */}
        {error && (
          <div className="rounded-md bg-destructive/10 p-3 text-sm text-destructive">{error}</div>
        )}

        {/* 結果展示——緊湊列表 + 統計摘要（避免大量消息時頁面過長） */}
        {results && (
          <DeliveryResults key={results.length} results={results} />
        )}
      </CardContent>
    </Card>
  )
}

/**
 * 投遞結果展示組件——緊湊列表 + 統計摘要 + 分頁。
 *
 * 設計目標：
 * - 大量消息（100+）時不讓頁面過長——每頁最多顯示 10 條
 * - 頂部顯示統計摘要：總數、成功數、失敗數、成功率、耗時
 * - 每條結果緊湊顯示：序號 + 成功/失敗 Badge + messageKey（截斷）+ 詳情（截斷）
 * - 失敗結果優先顯示（用戶更關注失敗）
 */
function DeliveryResults({ results }: { results: MessageDeliveryResult[] }) {
  const [page, setPage] = useState(0)
  const pageSize = 10

  // 統計摘要
  const total = results.length
  const successCount = results.filter((r) => r.success).length
  const failCount = total - successCount
  const successRate = total > 0 ? ((successCount / total) * 100).toFixed(1) : '0.0'

  // 分頁
  const totalPages = Math.max(1, Math.ceil(total / pageSize))
  const currentPage = Math.min(page, totalPages - 1)
  const startIdx = currentPage * pageSize
  const pageItems = results.slice(startIdx, startIdx + pageSize)

  return (
    <div className="space-y-3">
      {/* 統計摘要卡片 */}
      <div className="grid grid-cols-4 gap-2 rounded-md border bg-muted/30 p-3">
        <div className="text-center">
          <div className="text-lg font-bold">{total}</div>
          <div className="text-xs text-muted-foreground">總數</div>
        </div>
        <div className="text-center">
          <div className="text-lg font-bold text-green-600">{successCount}</div>
          <div className="text-xs text-muted-foreground">成功</div>
        </div>
        <div className="text-center">
          <div className={`text-lg font-bold ${failCount > 0 ? 'text-red-600' : ''}`}>{failCount}</div>
          <div className="text-xs text-muted-foreground">失敗</div>
        </div>
        <div className="text-center">
          <div className={`text-lg font-bold ${successRate === '100.0' ? 'text-green-600' : 'text-yellow-600'}`}>
            {successRate}%
          </div>
          <div className="text-xs text-muted-foreground">成功率</div>
        </div>
      </div>

      {/* 緊湊列表——每頁最多 10 條 */}
      <div className="space-y-1 max-h-[320px] overflow-y-auto">
        {pageItems.map((r, i) => {
          const idx = startIdx + i
          return (
            <div
              key={idx}
              className="flex items-center gap-2 rounded border px-2 py-1 text-xs hover:bg-muted/30"
            >
              <span className="text-muted-foreground w-8 shrink-0 text-right">#{idx + 1}</span>
              <Badge
                variant={r.success ? 'default' : 'destructive'}
                className="shrink-0 px-1 py-0 text-[10px]"
              >
                {r.success ? 'OK' : 'FAIL'}
              </Badge>
              <span className="font-mono shrink-0 max-w-[120px] truncate" title={r.messageKey}>
                {r.messageKey}
              </span>
              <span className="text-muted-foreground truncate flex-1" title={r.detail}>
                {r.detail}
              </span>
            </div>
          )
        })}
      </div>

      {/* 分頁控制 */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between text-xs">
          <span className="text-muted-foreground">
            第 {currentPage + 1} / {totalPages} 頁（{startIdx + 1}-{Math.min(startIdx + pageSize, total)} / {total}）
          </span>
          <div className="flex gap-1">
            <Button
              size="sm"
              variant="outline"
              className="h-7 px-2"
              disabled={currentPage === 0}
              onClick={() => setPage((p) => Math.max(0, p - 1))}
            >
              上一頁
            </Button>
            <Button
              size="sm"
              variant="outline"
              className="h-7 px-2"
              disabled={currentPage >= totalPages - 1}
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
            >
              下一頁
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}
