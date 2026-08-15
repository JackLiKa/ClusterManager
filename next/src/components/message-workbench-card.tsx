'use client'

/**
 * 消息工作台組件——topic 級 produce/consume 模擬表單。
 *
 * 職責：
 * - 提供消息模擬表單（topic、consumerGroup、消息數量、payload 模板、producer/consumer 節點選擇）
 * - 調用 simulateMessages API 執行真實的 RocketMQ produce/consume
 * - 展示每條消息的投遞結果（成功/失敗 + 詳情）
 * - 表單字段校驗（topic 和 consumerGroup 必填，消息數量 >= 1）
 *
 * 與後端的交互：
 * - POST /api/clusters/{...}/messages/simulate
 * - 請求體：MessageSimulationRequest
 * - 響應體：MessageSimulationResult（含每條消息的投遞結果）
 */

import { useState } from 'react'
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
import { simulateMessages } from '@/lib/api'
import type {
  ClusterSelection,
  ClusterTopology,
  MessageDeliveryResult,
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

  // 結果狀態
  const [results, setResults] = useState<MessageDeliveryResult[] | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // 從拓撲中提取可用節點
  const brokerNodes = topology?.nodes.filter((n) => n.labels?.role?.includes('broker')) ?? []
  const allNodes = topology?.nodes ?? []

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
            <Label htmlFor="messageCount">消息數量</Label>
            <Input
              id="messageCount"
              type="number"
              min={1}
              value={messageCount}
              onChange={(e) => setMessageCount(Number(e.target.value) || 1)}
            />
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
            <Label htmlFor="payloadTemplate">Payload 模板</Label>
            <Input
              id="payloadTemplate"
              value={payloadTemplate}
              onChange={(e) => setPayloadTemplate(e.target.value)}
              placeholder='例如: {"msg":"hello"}'
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

        {/* 結果展示 */}
        {results && (
          <div className="space-y-2">
            <h4 className="text-sm font-semibold">投遞結果（{results.length} 條）</h4>
            <div className="space-y-1">
              {results.map((r, i) => (
                <div
                  key={i}
                  className="flex items-start gap-2 rounded-md border p-2 text-xs"
                >
                  <Badge variant={r.success ? 'default' : 'destructive'}>
                    {r.success ? '成功' : '失敗'}
                  </Badge>
                  <div className="flex-1">
                    <div className="font-mono">{r.messageKey}</div>
                    <div className="text-muted-foreground">{r.detail}</div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  )
}
