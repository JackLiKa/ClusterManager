'use client'

/**
 * STOMP/SockJS 實時流訂閱 Hook。
 *
 * 職責：
 * - 通過 SockJS + STOMP 協議連接後端 WebSocket 端點 /ws
 * - 訂閱 /topic/clusters/{clusterId}/metrics 和 /topic/clusters/{clusterId}/logs 主題
 * - 在 selection 變化時自動重連並重新訂閱
 * - 在組件卸載時斷開連接
 *
 * 後端推送服務：ClusterTelemetryPushService（@Scheduled 每 5s 推送）
 * 後端 WebSocket 配置：WebSocketBrokerConfig（端點 /ws，代理前綴 /topic）
 *
 * @param selection 集群選擇標識，變化時觸發重連
 * @param onMetrics 收到指標快照時的回調
 * @param onLogs 收到日誌時的回調
 */

import { useEffect, useRef } from 'react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import type { ClusterSelection, LogEntry, MonitoringSnapshot } from '@/types/cluster'

interface UseClusterStreamsOptions {
  selection: ClusterSelection | null
  onMetrics?: (snapshot: MonitoringSnapshot) => void
  onLogs?: (logs: LogEntry[]) => void
}

export function useClusterStreams({
  selection,
  onMetrics,
  onLogs,
}: UseClusterStreamsOptions): void {
  const clientRef = useRef<Client | null>(null)
  // 用 ref 保存最新的回調，避免 selection 不變時回調變化導致重連
  const callbacksRef = useRef({ onMetrics, onLogs })

  // 在 effect 中更新 ref，避免 render 期間修改 ref
  useEffect(() => {
    callbacksRef.current = { onMetrics, onLogs }
  })

  useEffect(() => {
    if (!selection) return

    const client = new Client({
      // SockJS 工廠——後端 /ws 端點同時支持原生 WebSocket 和 SockJS 回退
      webSocketFactory: () => new SockJS('/ws'),
      // 心跳檢測：每 10s 發送一次，15s 無接收則判定斷連
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      // 連接成功回調
      onConnect: () => {
        // 訂閱指標主題
        client.subscribe(`/topic/clusters/${selection.clusterId}/metrics`, (message) => {
          try {
            const snapshot = JSON.parse(message.body) as MonitoringSnapshot
            callbacksRef.current.onMetrics?.(snapshot)
          } catch {
            // 忽略解析錯誤
          }
        })
        // 訂閱日誌主題
        client.subscribe(`/topic/clusters/${selection.clusterId}/logs`, (message) => {
          try {
            const logs = JSON.parse(message.body) as LogEntry[]
            callbacksRef.current.onLogs?.(logs)
          } catch {
            // 忽略解析錯誤
          }
        })
      },
      // 靜默處理錯誤，避免控制台刷屏
      onStompError: () => {},
      onWebSocketError: () => {},
    })

    clientRef.current = client
    client.activate()

    // 組件卸載或 selection 變化時斷開連接
    return () => {
      client.deactivate()
      clientRef.current = null
    }
  }, [selection])
}
