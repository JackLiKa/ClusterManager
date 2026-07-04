import axios from 'axios'
import type {
  ClusterSelection,
  ClusterTopology,
  LogEntry,
  MessageSimulationRequest,
  MessageSimulationResult,
  MonitoringSnapshot,
  OperationResult,
  ProviderDescriptor,
  ServiceRegistrationRequest
} from '../types/cluster'

const api = axios.create({
  baseURL: '/api'
})

function pathOf(selection: ClusterSelection): string {
  return `/clusters/${selection.mode.toLowerCase()}/${selection.middleware.toLowerCase()}/${selection.clusterId}`
}

export async function fetchProviders(): Promise<ProviderDescriptor[]> {
  const { data } = await api.get<ProviderDescriptor[]>('/clusters/providers')
  return data
}

export async function fetchTopology(selection: ClusterSelection): Promise<ClusterTopology> {
  const { data } = await api.get<ClusterTopology>(`${pathOf(selection)}/topology`)
  return data
}

export async function fetchMetrics(selection: ClusterSelection): Promise<MonitoringSnapshot> {
  const { data } = await api.get<MonitoringSnapshot>(`${pathOf(selection)}/metrics`)
  return data
}

export async function fetchLogs(selection: ClusterSelection, nodeId?: string): Promise<LogEntry[]> {
  const { data } = await api.get<LogEntry[]>(`${pathOf(selection)}/logs`, {
    params: {
      nodeId
    }
  })
  return data
}

export async function operateNode(
  selection: ClusterSelection,
  nodeId: string,
  operationType: 'START' | 'STOP' | 'RESTART'
): Promise<OperationResult> {
  const { data } = await api.post<OperationResult>(`${pathOf(selection)}/nodes/${nodeId}/operations`, {
    operationType
  })
  return data
}

export async function registerService(
  selection: ClusterSelection,
  payload: ServiceRegistrationRequest
): Promise<OperationResult> {
  const { data } = await api.post<OperationResult>(`${pathOf(selection)}/services`, payload)
  return data
}

export async function deleteService(
  selection: ClusterSelection,
  nodeId: string
): Promise<OperationResult> {
  const { data } = await api.delete<OperationResult>(`${pathOf(selection)}/services/${nodeId}`)
  return data
}

export async function simulateMessages(
  selection: ClusterSelection,
  payload: MessageSimulationRequest
): Promise<MessageSimulationResult> {
  const { data } = await api.post<MessageSimulationResult>(`${pathOf(selection)}/messages/simulate`, payload)
  return data
}
