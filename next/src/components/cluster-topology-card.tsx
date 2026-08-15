'use client'

/**
 * 集群拓撲可視化組件——使用 ECharts 渲染節點關係圖。
 *
 * 職責：
 * - 將後端 ClusterTopology 的節點和鏈路數據轉換為 ECharts Graph 圖表
 * - 節點按角色（nameserver / broker-master / broker-slave）著色
 * - 鏈路按健康狀態著色（健康=綠色，不健康=灰色）
 * - 節點狀態通過樣式區分（RUNNING=實線，STOPPED=半透明，FAILED=紅色邊框）
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

/** 節點角色到顏色的映射 */
const ROLE_COLORS: Record<string, string> = {
  nameserver: '#3b82f6', // 藍色
  'broker-master': '#10b981', // 綠色
  'broker-slave': '#f59e0b', // 橙色
}

/** 節點狀態到透明度的映射 */
const STATUS_OPACITY: Record<NodeStatus, number> = {
  RUNNING: 1.0,
  STARTING: 0.7,
  STOPPED: 0.4,
  FAILED: 1.0,
}

/** 節點角色到圖標的映射（ECharts symbol） */
const ROLE_SYMBOLS: Record<string, string> = {
  nameserver: 'circle',
  'broker-master': 'roundRect',
  'broker-slave': 'roundRect',
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

    // 構建 ECharts 節點數據
    const nodes = topology.nodes.map((node) => {
      const role = node.labels?.role ?? 'unknown'
      const status = node.status
      const color = ROLE_COLORS[role] ?? '#6b7280'
      const opacity = STATUS_OPACITY[status] ?? 0.5

      return {
        id: node.nodeId,
        name: node.displayName,
        symbol: ROLE_SYMBOLS[role] ?? 'circle',
        symbolSize: 50,
        itemStyle: {
          color,
          opacity,
          borderColor: status === 'FAILED' ? '#ef4444' : color,
          borderWidth: status === 'FAILED' ? 3 : 1,
        },
        label: {
          show: true,
          position: 'bottom',
          fontSize: 11,
          formatter: `${node.displayName}\n[${status}]`,
        },
        // 自定義數據，供 tooltip 使用
        nodeStatus: status,
        nodeRole: role,
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
            return `<b>${data.name}</b><br/>狀態: ${data.nodeStatus}<br/>角色: ${data.nodeRole}<br/>VIP: ${data.virtualIp ?? 'N/A'}`
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
            repulsion: 200,
            edgeLength: 120,
            gravity: 0.1,
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
            {/* 圖例：角色顏色 + 鏈路狀態 + 節點狀態說明 */}
            <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground border-t pt-2">
              <span className="font-medium text-foreground">角色：</span>
              <span className="flex items-center gap-1">
                <span className="inline-block h-2.5 w-2.5 rounded-full" style={{ backgroundColor: ROLE_COLORS['nameserver'] }} />
                NameServer
              </span>
              <span className="flex items-center gap-1">
                <span className="inline-block h-2.5 w-2.5 rounded" style={{ backgroundColor: ROLE_COLORS['broker-master'] }} />
                Broker Master
              </span>
              <span className="flex items-center gap-1">
                <span className="inline-block h-2.5 w-2.5 rounded" style={{ backgroundColor: ROLE_COLORS['broker-slave'] }} />
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
              <span className="ml-2 font-medium text-foreground">狀態：</span>
              <span>實線=運行中</span>
              <span>半透明=已停止</span>
              <span>紅框=失敗</span>
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
