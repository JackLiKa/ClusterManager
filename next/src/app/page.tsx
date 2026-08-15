'use client'

/**
 * 主儀表盤頁面——集群概覽與操作入口。
 *
 * 職責：
 * - 持有集群選擇狀態（cluster selection）
 * - 加載 provider 列表，默認選擇第一個 PSEUDO provider
 * - 定時輪詢拓撲和指標數據
 * - 通過 STOMP 實時接收指標和日誌推送
 * - 組合拓撲圖、消息工作台、操作面板、監控指標等子組件
 *
 * 數據流：
 * - 首次加載：fetchProviders → 選擇默認集群 → fetchTopology + fetchMetrics + fetchLogs
 * - 定時輪詢：每 5s 拉取拓撲和指標
 * - 實時推送：STOMP 訂閱 /topic/clusters/{clusterId}/{metrics|logs}
 */

import { useCallback, useEffect, useState } from 'react'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { ClusterTopologyCard } from '@/components/cluster-topology-card'
import { MessageWorkbenchCard } from '@/components/message-workbench-card'
import { OperationsPanel } from '@/components/operations-panel'
import { MetricsCard } from '@/components/metrics-card'
import { useClusterStreams } from '@/hooks/use-cluster-streams'
import { fetchProviders, fetchTopology, fetchMetrics, fetchLogs } from '@/lib/api'
import type {
  ClusterSelection,
  ClusterTopology,
  LogEntry,
  MonitoringSnapshot,
  ProviderDescriptor,
} from '@/types/cluster'

export default function ClusterOverviewPage() {
  // Provider 列表
  const [providers, setProviders] = useState<ProviderDescriptor[]>([])
  // 當前選擇的集群
  const [selection, setSelection] = useState<ClusterSelection | null>(null)
  // 拓撲數據
  const [topology, setTopology] = useState<ClusterTopology | null>(null)
  // 監控指標
  const [metrics, setMetrics] = useState<MonitoringSnapshot | null>(null)
  // 活動日誌
  const [logs, setLogs] = useState<LogEntry[]>([])
  // 加載狀態
  const [loading, setLoading] = useState(true)

  /** 刷新拓撲數據（供子組件調用） */
  const refreshTopology = useCallback(async () => {
    if (!selection) return
    try {
      const topo = await fetchTopology(selection)
      setTopology(topo)
    } catch {
      // 忽略輪詢錯誤
    }
  }, [selection])

  // 首次加載：獲取 provider 列表並選擇默認集群
  useEffect(() => {
    async function init() {
      try {
        const list = await fetchProviders()
        setProviders(list)
        // 默認選擇第一個 PSEUDO provider，否則選第一個
        const pseudo = list.find((p) => p.mode === 'PSEUDO')
        const first = pseudo ?? list[0]
        if (first) {
          setSelection({
            clusterId: first.providerId.includes('pseudo')
              ? 'local-lab'
              : 'rocketmq-demo',
            mode: first.mode,
            middleware: first.middleware,
          })
        }
      } catch {
        // 加載失敗時靜默處理
      } finally {
        setLoading(false)
      }
    }
    init()
  }, [])

  // selection 變化時加載全部數據
  useEffect(() => {
    if (!selection) return
    const sel = selection
    let cancelled = false

    async function loadAll() {
      try {
        const [topo, m, l] = await Promise.all([
          fetchTopology(sel),
          fetchMetrics(sel),
          fetchLogs(sel),
        ])
        if (cancelled) return
        setTopology(topo)
        setMetrics(m)
        setLogs(l)
      } catch {
        // 忽略初始加載錯誤
      }
    }

    void loadAll()
    return () => {
      cancelled = true
    }
  }, [selection])

  // 定時輪詢拓撲和指標（每 5 秒）
  useEffect(() => {
    if (!selection) return
    const sel = selection
    const interval = setInterval(async () => {
      try {
        const [topo, m] = await Promise.all([
          fetchTopology(sel),
          fetchMetrics(sel),
        ])
        setTopology(topo)
        setMetrics(m)
      } catch {
        // 忽略輪詢錯誤
      }
    }, 5000)
    return () => clearInterval(interval)
  }, [selection])

  // STOMP 實時流訂閱
  useClusterStreams({
    selection,
    onMetrics: (snapshot) => setMetrics(snapshot),
    onLogs: (newLogs) => setLogs((prev) => [...newLogs, ...prev].slice(0, 100)),
  })

  /** 處理 provider 選擇變化 */
  function handleProviderChange(providerId: string | null) {
    if (!providerId) return
    const provider = providers.find((p) => p.providerId === providerId)
    if (!provider) return
    setSelection({
      clusterId: provider.providerId.includes('pseudo') ? 'local-lab' : 'rocketmq-demo',
      mode: provider.mode,
      middleware: provider.middleware,
    })
  }

  if (loading) {
    return (
      <div className="flex h-[60vh] items-center justify-center text-muted-foreground">
        加載中...
      </div>
    )
  }

  if (!selection) {
    return (
      <div className="flex h-[60vh] items-center justify-center text-muted-foreground">
        未找到可用的集群 Provider
      </div>
    )
  }

  return (
    <div className="container mx-auto p-4 space-y-4">
      {/* 集群選擇器 */}
      <div className="flex items-center gap-4">
        <span className="text-sm font-medium">選擇集群：</span>
        <Select onValueChange={handleProviderChange}>
          <SelectTrigger className="w-[300px]">
            <SelectValue
              placeholder={
                providers.find(
                  (p) =>
                    p.mode === selection.mode &&
                    p.middleware === selection.middleware,
                )?.displayName ?? '選擇集群'
              }
            />
          </SelectTrigger>
          <SelectContent>
            {providers.map((p) => (
              <SelectItem key={p.providerId} value={p.providerId}>
                {p.displayName} ({p.mode})
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <span className="text-xs text-muted-foreground">
          集群 ID: {selection.clusterId} | 模式: {selection.mode}
        </span>
      </div>

      {/* 主內容區——兩列佈局 */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* 左列：拓撲圖 + 操作面板 */}
        <div className="space-y-4">
          <ClusterTopologyCard topology={topology} />
          <OperationsPanel
            selection={selection}
            topology={topology}
            logs={logs}
            onTopologyRefresh={refreshTopology}
          />
        </div>
        {/* 右列：消息工作台 + 監控指標 */}
        <div className="space-y-4">
          <MessageWorkbenchCard selection={selection} topology={topology} />
          <MetricsCard metrics={metrics} topology={topology} />
        </div>
      </div>
    </div>
  )
}
