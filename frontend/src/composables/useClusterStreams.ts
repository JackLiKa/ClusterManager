import { onBeforeUnmount, watch } from 'vue'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import type { ClusterSelection, LogEntry, MonitoringSnapshot } from '../types/cluster'

type StreamHandlers = {
  onMetrics?: (payload: MonitoringSnapshot) => void
  onLogs?: (payload: LogEntry[]) => void
}

export function useClusterStreams(selection: ClusterSelection, handlers: StreamHandlers): void {
  let client: Client | null = null

  const connect = (): void => {
    client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      debug: () => {}
    })
    client.onConnect = () => {
      client?.subscribe(`/topic/clusters/${selection.clusterId}/metrics`, frame => {
        handlers.onMetrics?.(JSON.parse(frame.body) as MonitoringSnapshot)
      })
      client?.subscribe(`/topic/clusters/${selection.clusterId}/logs`, frame => {
        handlers.onLogs?.(JSON.parse(frame.body) as LogEntry[])
      })
    }
    client.activate()
  }

  const disconnect = (): void => {
    void client?.deactivate()
    client = null
  }

  watch(
    () => [selection.clusterId, selection.mode, selection.middleware] as const,
    () => {
      disconnect()
      connect()
    },
    { immediate: true }
  )

  onBeforeUnmount(() => {
    disconnect()
  })
}
