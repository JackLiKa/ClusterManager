/**
 * API 客戶端與類型化端點函數。
 *
 * 職責：
 * - 創建統一的 axios 實例（baseURL: `/api`），所有 HTTP 請求均通過此實例發出。
 * - 通過響應攔截器歸一化後端錯誤響應，將 ApiExceptionHandler 返回的 ErrorResponse.message
 *   解包為標準 Error 對象拋出，使調用方可用 error.message 直接獲取用戶可讀的錯誤信息。
 * - 提供與後端 ClusterController REST 端點一一對應的類型化函數。
 *
 * 在前端架構中的角色：
 * - 前端唯一的數據訪問層。所有組件和頁面均通過此模塊的函數調用後端 API，
 *   不直接使用 fetch 或裸 axios，確保錯誤處理一致性和類型安全。
 *
 * 後端端點映射（所有路徑前綴 /api）：
 * - GET  /clusters/providers                         → fetchProviders()
 * - GET  /clusters/{mode}/{middleware}/{id}/topology → fetchTopology()
 * - GET  /clusters/{mode}/{middleware}/{id}/metrics  → fetchMetrics()
 * - GET  /clusters/{mode}/{middleware}/{id}/logs     → fetchLogs()
 * - POST /clusters/{mode}/{middleware}/{id}/nodes/{nodeId}/operations → operateNode()
 * - POST /clusters/{mode}/{middleware}/{id}/services → registerService()
 * - DELETE /clusters/{mode}/{middleware}/{id}/services/{nodeId} → deleteService()
 * - POST /clusters/{mode}/{middleware}/{id}/messages/simulate → simulateMessages()
 */

import axios from 'axios'
import type {
  ClusterSelection,
  ClusterTopology,
  LogEntry,
  MessageSimulationRequest,
  MessageSimulationResult,
  MessageTemplate,
  MonitoringSnapshot,
  NodeOperationRequest,
  OperationResult,
  ProviderDescriptor,
  RocketMqConnectionConfig,
  ServiceRegistrationRequest,
} from '@/types/cluster'

/** 統一 axios 實例，baseURL 為 /api，由 Next.js rewrites 代理到後端 http://localhost:8088 */
const api = axios.create({
  baseURL: '/api',
})

/**
 * 響應攔截器：成功時直接透傳 response；失敗時解包後端 ErrorResponse.message 為 Error 拋出。
 * 後端 ApiExceptionHandler 統一返回 { code, message, correlationId, timestamp } 結構。
 */
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const backendMessage = error?.response?.data?.message
    if (backendMessage) {
      return Promise.reject(new Error(backendMessage))
    }
    return Promise.reject(error)
  },
)

/**
 * 構造 REST 路徑前綴。
 * 路徑格式：/clusters/{mode}/{middleware}/{clusterId}（均轉小寫）
 *
 * @param selection 集群選擇標識
 * @returns 路徑前綴字符串
 */
function pathOf(selection: ClusterSelection): string {
  return `/clusters/${selection.mode.toLowerCase()}/${selection.middleware.toLowerCase()}/${selection.clusterId}`
}

/**
 * 獲取所有可用的集群 Provider 描述列表。
 * 後端端點：GET /api/clusters/providers
 */
export async function fetchProviders(): Promise<ProviderDescriptor[]> {
  const { data } = await api.get<ProviderDescriptor[]>('/clusters/providers')
  return data
}

/**
 * 獲取所有預定義消息模板列表。
 * 後端端點：GET /api/clusters/message-templates
 */
export async function fetchMessageTemplates(): Promise<MessageTemplate[]> {
  const { data } = await api.get<MessageTemplate[]>('/clusters/message-templates')
  return data
}

/**
 * 獲取當前 RocketMQ 連接配置。
 * 後端端點：GET /api/clusters/settings/rocketmq
 */
export async function fetchRocketMqConfig(): Promise<RocketMqConnectionConfig> {
  const { data } = await api.get<RocketMqConnectionConfig>('/clusters/settings/rocketmq')
  return data
}

/**
 * 更新 RocketMQ 連接配置，立即生效並持久化。
 * 後端端點：PUT /api/clusters/settings/rocketmq
 */
export async function updateRocketMqConfig(config: RocketMqConnectionConfig): Promise<RocketMqConnectionConfig> {
  const { data } = await api.put<RocketMqConnectionConfig>('/clusters/settings/rocketmq', config)
  return data
}

/**
 * 獲取指定集群的拓撲信息（節點列表 + 鏈路列表 + 活躍 VIP）。
 * 後端端點：GET /api/clusters/{mode}/{middleware}/{id}/topology
 */
export async function fetchTopology(selection: ClusterSelection): Promise<ClusterTopology> {
  const { data } = await api.get<ClusterTopology>(`${pathOf(selection)}/topology`)
  return data
}

/**
 * 獲取指定集群的監控指標快照。
 * 後端端點：GET /api/clusters/{mode}/{middleware}/{id}/metrics
 */
export async function fetchMetrics(selection: ClusterSelection): Promise<MonitoringSnapshot> {
  const { data } = await api.get<MonitoringSnapshot>(`${pathOf(selection)}/metrics`)
  return data
}

/**
 * 獲取指定集群的活動日誌。
 * 後端端點：GET /api/clusters/{mode}/{middleware}/{id}/logs?nodeId={nodeId}
 *
 * @param selection 集群選擇標識
 * @param nodeId 可選，按節點 ID 過濾日誌；不傳則返回全部節點日誌
 */
export async function fetchLogs(selection: ClusterSelection, nodeId?: string): Promise<LogEntry[]> {
  const { data } = await api.get<LogEntry[]>(`${pathOf(selection)}/logs`, {
    params: {
      nodeId: nodeId || undefined,
      limit: 50,
    },
  })
  return data
}

/**
 * 對指定節點執行啟停/重啟操作。
 * 後端端點：POST /api/clusters/{mode}/{middleware}/{id}/nodes/{nodeId}/operations
 *
 * @param selection 集群選擇標識
 * @param nodeId 目標節點 ID
 * @param operationType 操作類型：START / STOP / RESTART
 */
export async function operateNode(
  selection: ClusterSelection,
  nodeId: string,
  operationType: 'START' | 'STOP' | 'RESTART',
): Promise<OperationResult> {
  const body: NodeOperationRequest = { operationType }
  const { data } = await api.post<OperationResult>(
    `${pathOf(selection)}/nodes/${nodeId}/operations`,
    body,
  )
  return data
}

/**
 * 登記一個新服務節點到指定集群。
 * 後端端點：POST /api/clusters/{mode}/{middleware}/{id}/services
 *
 * @param selection 集群選擇標識
 * @param payload 服務登記請求體
 */
export async function registerService(
  selection: ClusterSelection,
  payload: ServiceRegistrationRequest,
): Promise<OperationResult> {
  const { data } = await api.post<OperationResult>(`${pathOf(selection)}/services`, payload)
  return data
}

/**
 * 從指定集群中刪除一個已登記的服務節點。
 * 後端端點：DELETE /api/clusters/{mode}/{middleware}/{id}/services/{nodeId}
 */
export async function deleteService(
  selection: ClusterSelection,
  nodeId: string,
): Promise<OperationResult> {
  const { data } = await api.delete<OperationResult>(`${pathOf(selection)}/services/${nodeId}`)
  return data
}

/**
 * 模擬消息發送與消費驗證。
 * 後端端點：POST /api/clusters/{mode}/{middleware}/{id}/messages/simulate
 *
 * @param selection 集群選擇標識
 * @param payload 消息模擬請求體（topic、consumerGroup、producer/consumer 節點等）
 */
export async function simulateMessages(
  selection: ClusterSelection,
  payload: MessageSimulationRequest,
): Promise<MessageSimulationResult> {
  const { data } = await api.post<MessageSimulationResult>(
    `${pathOf(selection)}/messages/simulate`,
    payload,
  )
  return data
}
