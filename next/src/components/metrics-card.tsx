'use client'

/**
 * 監控指標卡片組件——展示各節點的 CPU、內存、網絡 IO 指標。
 *
 * 職責：
 * - 接收 MonitoringSnapshot 數據，按節點展示指標
 * - 結合拓撲數據顯示節點角色和狀態（NameServer/Broker、RUNNING/STOPPED）
 * - 使用進度條可視化 CPU 和內存使用率（最小寬度保證可見性）
 * - 網絡 IO 以 msg/s（消息數/秒）格式化展示——反映 broker put/get TPS
 * - 停止節點顯示「節點未運行」占位，與運行中空閒節點區分
 * - 數據來源：REST API 輪詢 + STOMP 實時推送
 */

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import type { ClusterTopology, MonitoringSnapshot, NodeMetrics, NodeStatus } from '@/types/cluster'

interface MetricsCardProps {
  metrics: MonitoringSnapshot | null
  /** 拓撲數據——用於查詢節點角色和狀態，為 null 時僅顯示指標 */
  topology?: ClusterTopology | null
}

/** 節點角色到中文標籤的映射 */
const ROLE_LABELS: Record<string, string> = {
  nameserver: 'NameServer',
  'broker-master': 'Broker Master',
  'broker-slave': 'Broker Slave',
}

/** 節點狀態到 Badge variant 的映射 */
function statusBadge(status: NodeStatus) {
  switch (status) {
    case 'RUNNING':
      return <Badge variant="default">RUNNING</Badge>
    case 'STARTING':
      return <Badge variant="secondary">STARTING</Badge>
    case 'STOPPED':
      return <Badge variant="outline">STOPPED</Badge>
    case 'FAILED':
      return <Badge variant="destructive">FAILED</Badge>
  }
}

/**
 * 格式化消息速率（TPS，消息數/秒）為人類可讀格式。
 * 值為 broker put/get 的每秒消息數，非字節數。
 */
function formatMsgRate(msgPerSecond: number): string {
  if (msgPerSecond < 1) return `${msgPerSecond.toFixed(2)} msg/s`
  if (msgPerSecond < 100) return `${msgPerSecond.toFixed(1)} msg/s`
  if (msgPerSecond < 10000) return `${msgPerSecond.toFixed(0)} msg/s`
  return `${(msgPerSecond / 1000).toFixed(1)}K msg/s`
}

/** CPU/內存使用率進度條——最小寬度 3% 保證低值也可見 */
function UsageBar({ label, value }: { label: string; value: number }) {
  const clamped = Math.min(100, Math.max(0, value))
  const color = clamped > 80 ? 'bg-red-500' : clamped > 60 ? 'bg-yellow-500' : 'bg-green-500'
  // 最小寬度 3%——確保低值（如 0.5% CPU）也有可見的進度條
  const width = Math.max(3, clamped)
  return (
    <div className="space-y-1">
      <div className="flex justify-between text-xs">
        <span className="text-muted-foreground">{label}</span>
        <span className="font-mono">{clamped.toFixed(1)}%</span>
      </div>
      <div className="h-2 rounded-full bg-muted overflow-hidden">
        <div
          className={`h-full rounded-full transition-all ${color}`}
          style={{ width: `${width}%` }}
        />
      </div>
    </div>
  )
}

export function MetricsCard({ metrics, topology }: MetricsCardProps) {
  // 從拓撲中建立 nodeId → {role, status} 的查找表
  const nodeInfoMap = new Map<
    string,
    { role: string; status: NodeStatus; displayName: string }
  >()
  if (topology) {
    for (const node of topology.nodes) {
      nodeInfoMap.set(node.nodeId, {
        role: node.labels?.role ?? 'unknown',
        status: node.status,
        displayName: node.displayName,
      })
    }
  }

  if (!metrics || metrics.nodes.length === 0) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>監控指標</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="text-sm text-muted-foreground">暫無指標數據</div>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center justify-between">
          監控指標
          <Badge variant="outline" className="text-xs">
            {new Date(metrics.capturedAt).toLocaleTimeString()}
          </Badge>
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        {metrics.nodes.map((node: NodeMetrics) => {
          const info = nodeInfoMap.get(node.nodeId)
          const role = info?.role ?? 'unknown'
          const status = info?.status
          const displayName = info?.displayName ?? node.nodeId
          const isStopped = status === 'STOPPED' || status === 'FAILED'
          const isAllZero =
            node.cpuUsage === 0 &&
            node.memoryUsage === 0 &&
            node.networkInBytesPerSecond === 0 &&
            node.networkOutBytesPerSecond === 0

          return (
            <div
              key={node.nodeId}
              className={`rounded-md border p-3 space-y-2 ${isStopped ? 'opacity-60' : ''}`}
            >
              {/* 節點標題行：顯示名 + 角色標籤 + 狀態 Badge */}
              <div className="flex items-center justify-between gap-2">
                <div className="flex items-center gap-2 min-w-0">
                  <span className="text-sm font-medium truncate">{displayName}</span>
                  <Badge variant="secondary" className="text-xs shrink-0">
                    {ROLE_LABELS[role] ?? role}
                  </Badge>
                </div>
                {status && statusBadge(status)}
              </div>

              {/* 停止/失敗節點顯示占位文字，不顯示零值進度條 */}
              {isStopped && isAllZero ? (
                <div className="text-xs text-muted-foreground py-1">節點未運行，無指標數據</div>
              ) : (
                <>
                  <UsageBar label="CPU" value={node.cpuUsage} />
                  <UsageBar label="內存" value={node.memoryUsage} />
                  <div className="flex justify-between text-xs text-muted-foreground pt-1 border-t">
                    <span>投遞: {formatMsgRate(node.networkInBytesPerSecond)}</span>
                    <span>消費: {formatMsgRate(node.networkOutBytesPerSecond)}</span>
                  </div>
                </>
              )}
            </div>
          )
        })}
      </CardContent>
    </Card>
  )
}
