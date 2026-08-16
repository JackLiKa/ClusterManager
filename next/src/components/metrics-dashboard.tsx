'use client'

/**
 * 數據面板組件——集群各節點動態數據可視化。
 *
 * 職責：
 * - 維護時間序列歷史數據（最近 30 個採樣點）
 * - 折線圖：CPU/內存/TPS 隨時間變化趨勢（多節點對比）
 * - 柱狀圖：各節點當前指標橫向對比
 * - 實時數值卡片：關鍵指標即時展示
 * - 數軸標註：Y 軸帶量化刻度，X 軸為時間
 *
 * 數據來源：MonitoringSnapshot（與 MetricsCard 共享，由父頁面輪詢/STOMP 推送）
 */

import { useEffect, useMemo, useRef, useState, useCallback } from 'react'
import * as echarts from 'echarts'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import type { ClusterTopology, MonitoringSnapshot, NodeMetrics, NodeStatus } from '@/types/cluster'

interface MetricsDashboardProps {
  metrics: MonitoringSnapshot | null
  topology?: ClusterTopology | null
}

/** 歷史採樣點最大數量 */
const MAX_HISTORY = 30

/** 歷史採樣點結構 */
interface HistoryPoint {
  time: Date
  nodes: NodeMetrics[]
}

/** 節點顯示信息 */
interface NodeDisplayInfo {
  role: string
  status: NodeStatus
  displayName: string
}

/** 節點顏色映射（與拓撲圖狀態色一致） */
const NODE_COLORS: Record<string, string> = {
  nameserver: '#3b82f6',
  'broker-master': '#10b981',
  'broker-slave': '#f59e0b',
}

/**
 * 數據面板——折線圖 + 柱狀圖 + 實時卡片。
 * 維護歷史時間序列，每次 metrics 更新時追加採樣點。
 */
export function MetricsDashboard({ metrics, topology }: MetricsDashboardProps) {
  // 歷史時間序列
  const [history, setHistory] = useState<HistoryPoint[]>([])
  // 記錄上次追加的 metrics 時間戳，避免重複追加
  const lastAppendedAt = useRef<string | null>(null)

  // 節點信息查找表
  const nodeInfoMap = useMemo(() => {
    const map = new Map<string, NodeDisplayInfo>()
    if (topology) {
      for (const node of topology.nodes) {
        map.set(node.nodeId, {
          role: node.labels?.role ?? 'unknown',
          status: node.status,
          displayName: node.displayName,
        })
      }
    }
    return map
  }, [topology])

  // 追加歷史點的回調——避免在 effect 中直接 setState
  const appendHistory = useCallback((snapshot: MonitoringSnapshot) => {
    if (snapshot.nodes.length === 0) return
    if (lastAppendedAt.current === snapshot.capturedAt) return
    lastAppendedAt.current = snapshot.capturedAt
    setHistory((prev) => {
      const next = [...prev, { time: new Date(snapshot.capturedAt), nodes: snapshot.nodes }]
      return next.slice(-MAX_HISTORY)
    })
  }, [])

  // metrics 更新時追加歷史點（通過回調避免 lint 警告）
  useEffect(() => {
    if (metrics) appendHistory(metrics)
  }, [metrics, appendHistory])

  if (!metrics || metrics.nodes.length === 0) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>數據面板</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="text-sm text-muted-foreground">暫無監控數據</div>
        </CardContent>
      </Card>
    )
  }

  // 當前快照節點列表
  const currentNodes = metrics.nodes
  const runningNodes = currentNodes.filter((n) => {
    const info = nodeInfoMap.get(n.nodeId)
    return info && info.status !== 'STOPPED' && info.status !== 'FAILED'
  })

  // 聚合指標
  const avgCpu = runningNodes.length > 0
    ? runningNodes.reduce((s, n) => s + n.cpuUsage, 0) / runningNodes.length
    : 0
  const avgMem = runningNodes.length > 0
    ? runningNodes.reduce((s, n) => s + n.memoryUsage, 0) / runningNodes.length
    : 0
  const totalPutTps = runningNodes.reduce((s, n) => s + n.networkInBytesPerSecond, 0)
  const totalGetTps = runningNodes.reduce((s, n) => s + n.networkOutBytesPerSecond, 0)

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center justify-between">
          數據面板
          <Badge variant="outline" className="text-xs">
            {new Date(metrics.capturedAt).toLocaleTimeString()}
          </Badge>
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {/* 實時數值卡片——4 個關鍵指標 */}
        <div className="grid grid-cols-4 gap-2">
          <MetricCard
            label="平均 CPU"
            value={`${avgCpu.toFixed(1)}%`}
            color={avgCpu > 80 ? 'text-red-600' : avgCpu > 60 ? 'text-yellow-600' : 'text-green-600'}
          />
          <MetricCard
            label="平均內存"
            value={`${avgMem.toFixed(1)}%`}
            color={avgMem > 80 ? 'text-red-600' : avgMem > 60 ? 'text-yellow-600' : 'text-green-600'}
          />
          <MetricCard
            label="總投遞 TPS"
            value={formatTps(totalPutTps)}
            color="text-blue-600"
          />
          <MetricCard
            label="總消費 TPS"
            value={formatTps(totalGetTps)}
            color="text-purple-600"
          />
        </div>

        {/* 折線圖——時間序列趨勢 */}
        <TimeSeriesChart history={history} nodeInfoMap={nodeInfoMap} />

        {/* 柱狀圖——節點對比 */}
        <NodeComparisonChart nodes={currentNodes} nodeInfoMap={nodeInfoMap} />
      </CardContent>
    </Card>
  )
}

/** 格式化 TPS 數值 */
function formatTps(value: number): string {
  if (value < 1) return value.toFixed(2)
  if (value < 100) return value.toFixed(1)
  if (value < 10000) return value.toFixed(0)
  return `${(value / 1000).toFixed(1)}K`
}

/** 實時數值小卡片 */
function MetricCard({ label, value, color }: { label: string; value: string; color: string }) {
  return (
    <div className="rounded-md border bg-muted/20 p-2 text-center">
      <div className={`text-lg font-bold ${color}`}>{value}</div>
      <div className="text-xs text-muted-foreground">{label}</div>
    </div>
  )
}

/**
 * 折線圖——CPU/內存/TPS 時間序列趨勢。
 * 三個 Y 軸：CPU/內存（%，左軸）、投遞 TPS（msg/s，右軸）。
 */
function TimeSeriesChart({
  history,
  nodeInfoMap,
}: {
  history: HistoryPoint[]
  nodeInfoMap: Map<string, NodeDisplayInfo>
}) {
  const chartRef = useRef<HTMLDivElement>(null)
  const chartInstance = useRef<echarts.ECharts | null>(null)

  // 初始化 / 銷毀 ECharts
  useEffect(() => {
    if (!chartRef.current) return
    chartInstance.current = echarts.init(chartRef.current)
    const handleResize = () => chartInstance.current?.resize()
    const observer = new ResizeObserver(handleResize)
    observer.observe(chartRef.current)
    return () => {
      observer.disconnect()
      chartInstance.current?.dispose()
      chartInstance.current = null
    }
  }, [])

  // 數據更新時刷新圖表
  useEffect(() => {
    if (!chartInstance.current || history.length === 0) return

    // 收集所有節點 ID（按首次出現順序）
    const nodeIds = new Set<string>()
    for (const point of history) {
      for (const n of point.nodes) nodeIds.add(n.nodeId)
    }

    // X 軸：時間標籤
    const xLabels = history.map((p) =>
      p.time.toLocaleTimeString('zh-CN', { hour12: false }),
    )

    // 為每個節點生成 CPU/內存/TPS 三條線
    const series: echarts.SeriesOption[] = []
    for (const nodeId of nodeIds) {
      const info = nodeInfoMap.get(nodeId)
      const color = info ? NODE_COLORS[info.role] ?? '#6b7280' : '#6b7280'
      const label = info?.displayName ?? nodeId

      // CPU 線
      series.push({
        name: `${label} CPU`,
        type: 'line',
        yAxisIndex: 0,
        smooth: true,
        symbol: 'none',
        lineStyle: { color, width: 1.5 },
        data: history.map((p) => {
          const n = p.nodes.find((x) => x.nodeId === nodeId)
          return n ? Number(n.cpuUsage.toFixed(2)) : null
        }),
      })

      // 內存線（虛線區分）
      series.push({
        name: `${label} 內存`,
        type: 'line',
        yAxisIndex: 0,
        smooth: true,
        symbol: 'none',
        lineStyle: { color, width: 1, type: 'dashed' },
        data: history.map((p) => {
          const n = p.nodes.find((x) => x.nodeId === nodeId)
          return n ? Number(n.memoryUsage.toFixed(2)) : null
        }),
      })

      // 投遞 TPS 線
      series.push({
        name: `${label} 投遞TPS`,
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        symbol: 'none',
        lineStyle: { color, width: 1, opacity: 0.6 },
        data: history.map((p) => {
          const n = p.nodes.find((x) => x.nodeId === nodeId)
          return n ? Number(n.networkInBytesPerSecond.toFixed(2)) : null
        }),
      })
    }

    chartInstance.current.setOption({
      title: { text: '時間序列趨勢', left: 'center', textStyle: { fontSize: 12 } },
      tooltip: { trigger: 'axis' },
      legend: { type: 'scroll', bottom: 0, textStyle: { fontSize: 10 } },
      grid: { left: 50, right: 50, top: 35, bottom: 40 },
      xAxis: {
        type: 'category',
        data: xLabels,
        axisLabel: { fontSize: 10, rotate: 30 },
      },
      yAxis: [
        {
          type: 'value',
          name: 'CPU/內存 (%)',
          min: 0,
          max: 100,
          position: 'left',
          axisLabel: { fontSize: 10, formatter: '{value}%' },
        },
        {
          type: 'value',
          name: 'TPS (msg/s)',
          min: 0,
          position: 'right',
          axisLabel: { fontSize: 10 },
        },
      ],
      series,
    } as echarts.EChartsOption)
  }, [history, nodeInfoMap])

  return <div ref={chartRef} className="h-[280px] w-full" />
}

/**
 * 柱狀圖——各節點當前 CPU/內存/TPS 橫向對比。
 */
function NodeComparisonChart({
  nodes,
  nodeInfoMap,
}: {
  nodes: NodeMetrics[]
  nodeInfoMap: Map<string, NodeDisplayInfo>
}) {
  const chartRef = useRef<HTMLDivElement>(null)
  const chartInstance = useRef<echarts.ECharts | null>(null)

  // 初始化 / 銷毀
  useEffect(() => {
    if (!chartRef.current) return
    chartInstance.current = echarts.init(chartRef.current)
    const handleResize = () => chartInstance.current?.resize()
    const observer = new ResizeObserver(handleResize)
    observer.observe(chartRef.current)
    return () => {
      observer.disconnect()
      chartInstance.current?.dispose()
      chartInstance.current = null
    }
  }, [])

  // 數據更新
  useEffect(() => {
    if (!chartInstance.current || nodes.length === 0) return

    const labels = nodes.map((n) => {
      const info = nodeInfoMap.get(n.nodeId)
      return info?.displayName ?? n.nodeId
    })

    const cpuData = nodes.map((n) => Number(n.cpuUsage.toFixed(2)))
    const memData = nodes.map((n) => Number(n.memoryUsage.toFixed(2)))
    const putTpsData = nodes.map((n) => Number(n.networkInBytesPerSecond.toFixed(2)))
    const getTpsData = nodes.map((n) => Number(n.networkOutBytesPerSecond.toFixed(2)))

    chartInstance.current.setOption({
      title: { text: '節點指標對比', left: 'center', textStyle: { fontSize: 12 } },
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      legend: { bottom: 0, textStyle: { fontSize: 10 } },
      grid: { left: 50, right: 50, top: 35, bottom: 40 },
      xAxis: {
        type: 'category',
        data: labels,
        axisLabel: { fontSize: 10 },
      },
      yAxis: [
        {
          type: 'value',
          name: '使用率 (%)',
          min: 0,
          max: 100,
          position: 'left',
          axisLabel: { fontSize: 10, formatter: '{value}%' },
        },
        {
          type: 'value',
          name: 'TPS (msg/s)',
          min: 0,
          position: 'right',
          axisLabel: { fontSize: 10 },
        },
      ],
      series: [
        {
          name: 'CPU',
          type: 'bar',
          yAxisIndex: 0,
          itemStyle: { color: '#3b82f6' },
          data: cpuData,
        },
        {
          name: '內存',
          type: 'bar',
          yAxisIndex: 0,
          itemStyle: { color: '#10b981' },
          data: memData,
        },
        {
          name: '投遞 TPS',
          type: 'bar',
          yAxisIndex: 1,
          itemStyle: { color: '#f59e0b' },
          data: putTpsData,
        },
        {
          name: '消費 TPS',
          type: 'bar',
          yAxisIndex: 1,
          itemStyle: { color: '#a855f7' },
          data: getTpsData,
        },
      ],
    } as echarts.EChartsOption)
  }, [nodes, nodeInfoMap])

  return <div ref={chartRef} className="h-[240px] w-full" />
}
