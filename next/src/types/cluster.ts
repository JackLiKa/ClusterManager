/**
 * 與後端 DTO 對齊的 TypeScript 類型定義。
 *
 * 所有類型嚴格映射 Java 後端的 record 字段（含命名 casing 差異），
 * 確保前端 API 調用與後端響應結構一致。
 */

/** 集群模式——對應後端 ClusterMode 枚舉 */
export type ClusterMode = 'PSEUDO' | 'REAL'

/** 中間件類型——對應後端 MiddlewareType 枚舉 */
export type MiddlewareType = 'ROCKETMQ'

/** 節點狀態——對應後端 NodeStatus 枚舉 */
export type NodeStatus = 'STOPPED' | 'STARTING' | 'RUNNING' | 'FAILED'

/** 操作類型——對應後端 OperationType 枚舉 */
export type OperationType = 'START' | 'STOP' | 'RESTART'

/** 集群選擇標識——對應後端 ClusterSelection record */
export interface ClusterSelection {
  clusterId: string
  mode: ClusterMode
  middleware: MiddlewareType
}

/** 集群引用——對應後端 ClusterRef record */
export interface ClusterRef {
  clusterId: string
  mode: ClusterMode
  middleware: MiddlewareType
}

/** Provider 描述符——對應後端 ProviderDescriptor record */
export interface ProviderDescriptor {
  providerId: string
  displayName: string
  mode: ClusterMode
  middleware: MiddlewareType
}

/** 集群節點——對應後端 ClusterNode record */
export interface ClusterNode {
  nodeId: string
  displayName: string
  hostName: string
  virtualIp: string | null
  status: NodeStatus
  labels: Record<string, string>
}

/** 網絡鏈路——對應後端 NetworkLink record */
export interface NetworkLink {
  sourceNodeId: string
  targetNodeId: string
  healthy: boolean
  linkType: string
  latencyMs: number
}

/** 集群拓撲——對應後端 ClusterTopology record */
export interface ClusterTopology {
  cluster: ClusterRef
  nodes: ClusterNode[]
  links: NetworkLink[]
  activeVip: string | null
}

/** 節點指標——對應後端 NodeMetrics record */
export interface NodeMetrics {
  nodeId: string
  cpuUsage: number
  memoryUsage: number
  networkInBytesPerSecond: number
  networkOutBytesPerSecond: number
}

/** 監控快照——對應後端 MonitoringSnapshot record */
export interface MonitoringSnapshot {
  capturedAt: string
  nodes: NodeMetrics[]
}

/** 日誌條目——對應後端 LogEntry record */
export interface LogEntry {
  timestamp: string
  nodeId: string
  level: string
  message: string
}

/** 操作結果——對應後端 OperationResult record */
export interface OperationResult {
  targetId: string
  operationType: OperationType | null
  success: boolean
  message: string
}

/** 消息投遞結果——對應後端 MessageDeliveryResult record */
export interface MessageDeliveryResult {
  messageKey: string
  producerNodeId: string
  consumerNodeId: string
  success: boolean
  detail: string
}

/** 消息模擬結果——對應後端 MessageSimulationResult record */
export interface MessageSimulationResult {
  executedAt: string
  deliveries: MessageDeliveryResult[]
}

/** 消息模擬請求——對應後端 MessageSimulationRequest DTO */
export interface MessageSimulationRequest {
  topic: string
  consumerGroup: string
  messageCount: number
  payloadTemplate?: string
  producerNodeId: string
  consumerNodeIds?: string[]
  headers?: Record<string, string>
}

/** 服務登記請求——對應後端 ServiceRegistrationRequest DTO */
export interface ServiceRegistrationRequest {
  nodeId: string
  displayName: string
  role: string
  hostName: string
  address: string
  port: number
  labels?: Record<string, string>
}

/** 節點操作請求——對應後端 NodeOperationRequest DTO */
export interface NodeOperationRequest {
  operationType: OperationType
}
