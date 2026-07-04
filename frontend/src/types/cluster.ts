export type ClusterMode = 'PSEUDO' | 'REAL'
export type MiddlewareType = 'ROCKETMQ' | 'KAFKA'

export interface ClusterSelection {
  clusterId: string
  mode: ClusterMode
  middleware: MiddlewareType
}

export interface ProviderDescriptor {
  providerId: string
  displayName: string
  mode: ClusterMode
  middleware: MiddlewareType
}

export interface ClusterNode {
  nodeId: string
  displayName: string
  hostName: string
  virtualIp: string
  status: 'STARTING' | 'RUNNING' | 'STOPPED' | 'DEGRADED' | 'FAILED'
  labels: Record<string, string>
}

export interface NetworkLink {
  sourceNodeId: string
  targetNodeId: string
  healthy: boolean
  linkType: string
  latencyMs: number
}

export interface ClusterTopology {
  cluster: ClusterSelection
  nodes: ClusterNode[]
  links: NetworkLink[]
  activeVip: string
}

export interface NodeMetrics {
  nodeId: string
  cpuUsage: number
  memoryUsage: number
  networkInBytesPerSecond: number
  networkOutBytesPerSecond: number
}

export interface MonitoringSnapshot {
  capturedAt: string
  nodes: NodeMetrics[]
}

export interface OperationResult {
  targetId: string
  operationType: 'START' | 'STOP' | 'RESTART'
  success: boolean
  message: string
}

export interface ServiceRegistrationRequest {
  nodeId: string
  displayName: string
  role: string
  hostName: string
  address: string
  port: number
  labels: Record<string, string>
}

export type PseudoNodeKind = 'HOST' | 'VIRTUAL'

export interface LogEntry {
  timestamp: string
  nodeId: string
  level: string
  message: string
}

export interface MessageSimulationRequest {
  topic: string
  consumerGroup: string
  messageCount: number
  payloadTemplate: string
  producerNodeId: string
  consumerNodeIds: string[]
  headers: Record<string, string>
}

export interface MessageDeliveryResult {
  messageKey: string
  producerNodeId: string
  consumerNodeId: string
  success: boolean
  detail: string
}

export interface MessageSimulationResult {
  executedAt: string
  deliveries: MessageDeliveryResult[]
}
