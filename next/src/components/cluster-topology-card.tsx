'use client'

/**
 * 集群拓撲可視化組件——使用 ECharts 渲染節點關係圖。
 *
 * 職責：
 * - 將後端 ClusterTopology 的節點和鏈路數據轉換為 ECharts Graph 圖表
 * - 節點按狀態著色：灰色=停止、綠色=運行中、紅色=失敗、黃色=降級/高壓
 * - 節點按角色定形狀：圓形=NameServer、圓角矩形=Broker Master、菱形=Broker Slave
 * - 鏈路按健康狀態著色（健康=綠色實線，不健康=灰色虛線）
 * - 支持拖拽節點和縮放平移
 * - 組件掛載時初始化 ECharts 實例，卸載時銷毀，避免內存洩漏
 * - 拓撲數據變化時自動刷新圖表
 *
 * ECharts 生命週期管理：
 * - useEffect 中創建 echarts.init(dom) 實例
 * - 通過 ResizeObserver 監聽容器尺寸變化，調用 chart.resize()
 * - useEffect cleanup 中調用 chart.dispose() 銷毀實例
 */

import { useEffect, useRef } from 'react'
import * as echarts from 'echarts'
import type { ClusterTopology, NodeStatus } from '@/types/cluster'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

interface ClusterTopologyCardProps {
  topology: ClusterTopology | null
}

/**
 * 節點狀態到顏色的映射——狀態著色方案。
 * 灰色=未啟動、綠色=運行中、紅色=失敗/崩潰、黃色=降級/高壓警告、藍色=啟動中。
 */
const STATUS_COLORS: Record<NodeStatus, string> = {
  STOPPED: '#9ca3af',   // 灰色——未啟動
  STARTING: '#3b82f6',  // 藍色——啟動中
  RUNNING: '#10b981',   // 綠色——運行中
  DEGRADED: '#f59e0b',  // 黃色——降級/高壓警告
  FAILED: '#ef4444',    // 紅色——失敗/崩潰
}

/** 節點狀態中文標籤 */
const STATUS_LABELS: Record<NodeStatus, string> = {
  STOPPED: '已停止',
  STARTING: '啟動中',
  RUNNING: '運行中',
  DEGRADED: '降級',
  FAILED: '故障',
}

/**
 * 節點角色到圖標的映射——角色定形狀方案。
 * 圓形=NameServer、圓角矩形=Broker Master、菱形=Broker Slave。
 */
const ROLE_SYMBOLS: Record<string, string> = {
  nameserver: 'circle',
  'broker-master': 'roundRect',
  'broker-slave': 'diamond',
}

/** 節點角色中文標籤 */
const ROLE_LABELS: Record<string, string> = {
  nameserver: 'NameServer',
  'broker-master': 'Broker Master',
  'broker-slave': 'Broker Slave',
}

export function ClusterTopologyCard({ topology }: ClusterTopologyCardProps) {
  const chartRef = useRef<HTMLDivElement>(null)
  const chartInstanceRef = useRef<echarts.ECharts | null>(null)

  useEffect(() => {
    if (!chartRef.current) return

    // 初始化 ECharts 實例
    const chart = echarts.init(chartRef.current)
    chartInstanceRef.current = chart

    // ResizeObserver 監聽容器尺寸變化
    const resizeObserver = new ResizeObserver(() => chart.resize())
    resizeObserver.observe(chartRef.current)

    // 清理函數：銷毀 ECharts 實例和 ResizeObserver
    return () => {
      resizeObserver.disconnect()
      chart.dispose()
      chartInstanceRef.current = null
    }
  }, [])

  useEffect(() => {
    if (!chartInstanceRef.current || !topology) return

    // 構建 ECharts 節點數據——狀態著色 + 角色定形狀
    const nodes = topology.nodes.map((node) => {
      const role = node.labels?.role ?? 'unknown'
      const status = node.status
      const color = STATUS_COLORS[status] ?? '#9ca3af'

      return {
        id: node.nodeId,
        name: node.displayName,
        symbol: ROLE_SYMBOLS[role] ?? 'circle',
        symbolSize: 56,
        itemStyle: {
          color,
          borderColor: color,
          borderWidth: 2,
          shadowBlur: status === 'RUNNING' ? 10 : 0,
          shadowColor: color,
        },
        label: {
          show: true,
          position: 'bottom',
          fontSize: 11,
          formatter: `${node.displayName}\n[${STATUS_LABELS[status]}]`,
        },
        // 自定義數據，供 tooltip 使用
        nodeStatus: status,
        nodeStatusLabel: STATUS_LABELS[status],
        nodeRole: role,
        nodeRoleLabel: ROLE_LABELS[role] ?? role,
        virtualIp: node.virtualIp,
      }
    })

    // 構建 ECharts 鏈路數據
    const links = topology.links.map((link) => ({
      source: link.sourceNodeId,
      target: link.targetNodeId,
      lineStyle: {
        color: link.healthy ? '#10b981' : '#9ca3af',
        width: link.healthy ? 2 : 1,
        type: link.healthy ? 'solid' : 'dashed',
        curveness: 0.1,
      },
      label: {
        show: true,
        formatter: `${link.latencyMs.toFixed(0)}ms`,
        fontSize: 9,
        color: '#6b7280',
      },
    }))

    // 設置 ECharts 配置項
    chartInstanceRef.current.setOption({
      tooltip: {
        trigger: 'item',
        formatter: (params: { data: Record<string, unknown> }) => {
          const data = params.data
          if (data.nodeStatus) {
            return `<b>${data.name}</b><br/>狀態: ${data.nodeStatusLabel}<br/>角色: ${data.nodeRoleLabel}<br/>VIP: ${data.virtualIp ?? 'N/A'}`
          }
          return `${data.source} → ${data.target}`
        },
      },
      series: [
        {
          type: 'graph',
          layout: 'force',
          roam: true,
          draggable: true,
          force: {
            repulsion: 250,
            edgeLength: 140,
            gravity: 0.08,
          },
          data: nodes,
          links,
          emphasis: {
            focus: 'adjacency',
            lineStyle: { width: 3 },
          },
        },
      ],
    })
  }, [topology])

  return (
    <Card>
      <CardHeader>
        <CardTitle>集群拓撲</CardTitle>
      </CardHeader>
      <CardContent>
        {topology && topology.nodes.length > 0 ? (
          <>
            <div ref={chartRef} style={{ width: '100%', height: '360px' }} />
            {/* 圖例：狀態顏色 + 角色形狀 + 鏈路狀態 */}
            <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground border-t pt-2">
              <span className="font-medium text-foreground">狀態：</span>
              <span className="flex items-center gap-1">
                <span className="inline-block h-2.5 w-2.5 rounded-full" style={{ backgroundColor: STATUS_COLORS.STOPPED }} />
                已停止
              </span>
              <span className="flex items-center gap-1">
                <span className="inline-block h-2.5 w-2.5 rounded-full" style={{ backgroundColor: STATUS_COLORS.RUNNING }} />
                運行中
              </span>
              <span className="flex items-center gap-1">
                <span className="inline-block h-2.5 w-2.5 rounded-full" style={{ backgroundColor: STATUS_COLORS.DEGRADED }} />
                降級/高壓
              </span>
              <span className="flex items-center gap-1">
                <span className="inline-block h-2.5 w-2.5 rounded-full" style={{ backgroundColor: STATUS_COLORS.FAILED }} />
                故障
              </span>
              <span className="ml-2 font-medium text-foreground">形狀：</span>
              <span className="flex items-center gap-1">
                <span className="inline-block h-2.5 w-2.5 rounded-full border" style={{ borderColor: '#6b7280' }} />
                NameServer
              </span>
              <span className="flex items-center gap-1">
                <span className="inline-block h-2.5 w-2.5 rounded border" style={{ borderColor: '#6b7280' }} />
                Broker Master
              </span>
              <span className="flex items-center gap-1">
                <span className="inline-block h-2.5 w-2.5 rotate-45 border" style={{ borderColor: '#6b7280' }} />
                Broker Slave
              </span>
              <span className="ml-2 font-medium text-foreground">鏈路：</span>
              <span className="flex items-center gap-1">
                <span className="inline-block h-0.5 w-4" style={{ backgroundColor: '#10b981' }} />
                健康
              </span>
              <span className="flex items-center gap-1">
                <span className="inline-block h-0.5 w-4 border-t border-dashed" style={{ borderColor: '#9ca3af' }} />
                斷開
              </span>
            </div>
          </>
        ) : (
          <div className="flex h-[400px] items-center justify-center text-muted-foreground">
            暫無拓撲數據
          </div>
        )}
      </CardContent>
    </Card>
  )
}
