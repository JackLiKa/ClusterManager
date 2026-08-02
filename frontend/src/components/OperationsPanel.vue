<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteService, operateNode, registerService } from '../api/clusterApi'
import type {
  ClusterNode,
  ClusterSelection,
  LogEntry,
  MonitoringSnapshot,
  PseudoNodeKind,
  ServiceRegistrationRequest
} from '../types/cluster'

const props = defineProps<{
  cluster: ClusterSelection
  nodes: ClusterNode[]
  metrics: MonitoringSnapshot | null
  logs: LogEntry[]
}>()

const emit = defineEmits<{
  refresh: []
}>()

const busyNodeId = ref<string | null>(null)
const serviceBusy = ref(false)
const serviceForm = reactive({
  nodeId: '',
  displayName: '',
  role: 'nameserver',
  hostName: '',
  pseudoNodeKind: 'VIRTUAL' as PseudoNodeKind,
  addressInput: '',
  port: 9876
})

const summary = computed(() => {
  // P3 修复: HOST 节点 metrics 后端返回 0，会拉低平均值；先按 nodeId 找出非 HOST 节点再统计
  const hostNodeIds = new Set(
    props.nodes.filter(node => node.labels.nodeKind === 'HOST').map(node => node.nodeId)
  )
  const items = (props.metrics?.nodes ?? []).filter(item => !hostNodeIds.has(item.nodeId))
  if (items.length === 0) {
    return { cpu: '0.0', memory: '0.0', netIn: '0', netOut: '0' }
  }

  const cpu = items.reduce((sum, item) => sum + item.cpuUsage, 0) / items.length
  const memory = items.reduce((sum, item) => sum + item.memoryUsage, 0) / items.length
  const netIn = items.reduce((sum, item) => sum + item.networkInBytesPerSecond, 0)
  const netOut = items.reduce((sum, item) => sum + item.networkOutBytesPerSecond, 0)

  return {
    cpu: cpu.toFixed(1),
    memory: memory.toFixed(1),
    netIn: Math.round(netIn).toString(),
    netOut: Math.round(netOut).toString()
  }
})

const addressLabel = computed(() => {
  if (props.cluster.mode !== 'PSEUDO') {
    return 'Host / NameServer Address'
  }
  return serviceForm.pseudoNodeKind === 'HOST' ? 'Host Address' : 'Virtual IP'
})

const addressHint = computed(() => {
  if (props.cluster.mode !== 'PSEUDO') {
    return 'For example 192.168.50.78:9876 or 192.168.50.78'
  }
  return serviceForm.pseudoNodeKind === 'HOST'
    ? 'For example 127.0.0.1 or 192.168.1.20'
    : 'For example 10.77.0.40'
})

const serviceModeHint = computed(() => {
  if (props.cluster.mode !== 'PSEUDO') {
    return 'Use real RocketMQ NameServer or Broker endpoints in this mode.'
  }
  return serviceForm.pseudoNodeKind === 'HOST'
    ? 'Host node mode binds an already-running broker or nameserver on your machine and lets it participate in real RocketMQ message flow.'
    : 'Virtual node mode creates a TAP-backed pseudo service node; when host NameServer exists, it can join the same RocketMQ validation path.'
})

watch(
  () => props.cluster.mode,
  mode => {
    resetServiceForm(mode)
  },
  { immediate: true }
)

async function execute(nodeId: string, operationType: 'START' | 'STOP' | 'RESTART'): Promise<void> {
  busyNodeId.value = `${nodeId}-${operationType}`
  try {
    const result = await operateNode(props.cluster, nodeId, operationType)
    ElMessage.success(result.message)
    emit('refresh')
  } finally {
    busyNodeId.value = null
  }
}

async function submitService(): Promise<void> {
  serviceBusy.value = true
  try {
    const payload = normalizeServiceForm()
    const result = await registerService(props.cluster, payload)
    ElMessage.success(result.message)
    resetServiceForm(props.cluster.mode)
    emit('refresh')
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Service registration failed'
    ElMessage.error(message)
    throw error
  } finally {
    serviceBusy.value = false
  }
}

async function removeService(nodeId: string): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `Delete service ${nodeId}? The node will be removed from the current cluster view immediately.`,
      'Delete Service',
      {
        confirmButtonText: 'Delete',
        cancelButtonText: 'Cancel',
        type: 'warning'
      }
    )
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    throw error
  }

  serviceBusy.value = true
  try {
    const result = await deleteService(props.cluster, nodeId)
    ElMessage.success(result.message)
    emit('refresh')
  } finally {
    serviceBusy.value = false
  }
}

function normalizeServiceForm(): ServiceRegistrationRequest {
  const { host, port } = parseAddressAndPort(serviceForm.addressInput, serviceForm.port)
  const role = serviceForm.role.trim()
  const hostName = serviceForm.hostName.trim() || host
  const displayName = serviceForm.displayName.trim() || `${formatRole(role)} ${host}`
  const nodeId = serviceForm.nodeId.trim() || generateNodeId(role, host, port)

  return {
    nodeId,
    displayName,
    role,
    hostName,
    address: host,
    port,
    labels: {
      role,
      source: 'console',
      ...(props.cluster.mode === 'PSEUDO' ? { nodeKind: serviceForm.pseudoNodeKind } : {})
    }
  }
}

function parseAddressAndPort(rawAddress: string, fallbackPort: number): { host: string, port: number } {
  const trimmed = rawAddress.trim()
  if (!trimmed) {
    throw new Error('Address is required')
  }

  const expectsInlinePort = props.cluster.mode === 'REAL' || serviceForm.pseudoNodeKind === 'HOST'
  if (expectsInlinePort) {
    const segments = trimmed.split(':')
    if (segments.length === 2 && /^\d+$/.test(segments[1])) {
      return {
        host: segments[0],
        port: Number(segments[1])
      }
    }
  }

  return {
    host: trimmed,
    port: fallbackPort
  }
}

function resetServiceForm(mode: ClusterSelection['mode']): void {
  serviceForm.nodeId = ''
  serviceForm.displayName = ''
  serviceForm.role = mode === 'PSEUDO' ? 'broker-master' : 'nameserver'
  serviceForm.hostName = ''
  serviceForm.pseudoNodeKind = 'VIRTUAL'
  serviceForm.addressInput = ''
  serviceForm.port = mode === 'PSEUDO' ? 19931 : 9876
}

function canDelete(node: ClusterNode): boolean {
  return node.labels.managed === 'true' || node.labels.source === 'manual'
}

function formatRole(role: string): string {
  return role
    .split('-')
    .map(segment => segment.charAt(0).toUpperCase() + segment.slice(1))
    .join(' ')
}

function generateNodeId(role: string, host: string, port: number): string {
  const normalizedHost = host.replace(/[^a-zA-Z0-9]+/g, '-').replace(/^-+|-+$/g, '').toLowerCase()
  return `${role}-${normalizedHost}-${port}`
}
</script>

<template>
  <div class="operations-stack">
    <section class="panel panel-compact">
      <div class="panel-header">
        <div>
          <h2 class="panel-title">Operations</h2>
          <p class="panel-copy">Check service state, resource metrics and node control from one place.</p>
        </div>
      </div>

      <div class="kpi-strip kpi-strip-compact">
        <div class="kpi-item">
          <span class="metric-label">Avg CPU</span>
          <strong class="metric-value metric-value-small">{{ summary.cpu }}%</strong>
        </div>
        <div class="kpi-item">
          <span class="metric-label">Avg Memory</span>
          <strong class="metric-value metric-value-small">{{ summary.memory }}%</strong>
        </div>
        <div class="kpi-item">
          <span class="metric-label">Ingress</span>
          <strong class="metric-value metric-value-small">{{ summary.netIn }}</strong>
        </div>
        <div class="kpi-item">
          <span class="metric-label">Egress</span>
          <strong class="metric-value metric-value-small">{{ summary.netOut }}</strong>
        </div>
      </div>
      <!-- P3 修复: 真实集群 metrics 来自 MockRocketMqAdminClient 的随机数，标注 mock 避免用户误判 -->
      <div v-if="props.cluster.mode === 'REAL'" class="helper-note helper-note-inline" style="margin-top: 8px">
        Real cluster metrics are mock placeholders, not live runtime values from RocketMQ.
      </div>

      <el-table :data="nodes" style="margin-top: 16px" height="320">
        <el-table-column prop="displayName" label="Service" min-width="168" />
        <el-table-column prop="virtualIp" label="Address" min-width="168" />
        <el-table-column label="Role" min-width="128">
          <template #default="{ row }">
            {{ row.labels.role ?? 'unknown' }}
          </template>
        </el-table-column>
        <el-table-column label="Kind" min-width="110">
          <template #default="{ row }">
            {{ row.labels.nodeKind ?? (props.cluster.mode === 'REAL' ? 'PHYSICAL' : 'VIRTUAL') }}
          </template>
        </el-table-column>
        <el-table-column label="Status" min-width="110">
          <template #default="{ row }">
            <span class="status-pill" :class="`status-${row.status.toLowerCase()}`">{{ row.status }}</span>
          </template>
        </el-table-column>
        <el-table-column label="Actions" min-width="330" fixed="right">
          <template #default="{ row }">
            <div class="badge-row">
              <el-button size="small" :loading="busyNodeId === `${row.nodeId}-START`" @click="execute(row.nodeId, 'START')">Start</el-button>
              <el-button size="small" type="warning" :loading="busyNodeId === `${row.nodeId}-RESTART`" @click="execute(row.nodeId, 'RESTART')">Restart</el-button>
              <el-button size="small" type="danger" :loading="busyNodeId === `${row.nodeId}-STOP`" @click="execute(row.nodeId, 'STOP')">Stop</el-button>
              <el-button size="small" text type="danger" :disabled="!canDelete(row)" @click="removeService(row.nodeId)">Delete</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="panel panel-compact">
      <div class="panel-header">
        <div>
          <h2 class="panel-title">Register Service</h2>
          <p class="panel-copy">{{ serviceModeHint }}</p>
        </div>
      </div>

      <div v-if="props.cluster.mode === 'PSEUDO'" class="mode-chip-row">
        <button
          class="mode-chip"
          :class="{ active: serviceForm.pseudoNodeKind === 'HOST' }"
          type="button"
          @click="serviceForm.pseudoNodeKind = 'HOST'"
        >
          Host Node
        </button>
        <button
          class="mode-chip"
          :class="{ active: serviceForm.pseudoNodeKind === 'VIRTUAL' }"
          type="button"
          @click="serviceForm.pseudoNodeKind = 'VIRTUAL'"
        >
          Virtual Node
        </button>
      </div>

      <div class="service-form service-form-wide">
        <div class="field-block">
          <label class="field-label">Display Name</label>
          <el-input v-model="serviceForm.displayName" placeholder="For example Host Broker 01" />
        </div>
        <div class="field-block">
          <label class="field-label">Node ID</label>
          <el-input v-model="serviceForm.nodeId" placeholder="Leave blank to auto-generate" />
        </div>
        <div class="field-block">
          <label class="field-label">Role</label>
          <el-select v-model="serviceForm.role" placeholder="Select role">
            <el-option label="NameServer" value="nameserver" />
            <el-option label="Broker Master" value="broker-master" />
            <el-option label="Broker Slave" value="broker-slave" />
            <el-option label="Proxy" value="proxy" />
          </el-select>
        </div>
        <div class="field-block">
          <label class="field-label">Host Name</label>
          <el-input v-model="serviceForm.hostName" placeholder="For example mq-ns-01.local" />
        </div>
        <div class="field-block">
          <label class="field-label">{{ addressLabel }}</label>
          <el-input v-model="serviceForm.addressInput" :placeholder="addressHint" />
        </div>
        <div class="field-block">
          <label class="field-label">Port</label>
          <el-input-number v-model="serviceForm.port" :min="1" :max="65535" style="width: 100%" />
        </div>
      </div>

      <div class="helper-note">
        Pseudo host node example: <code>127.0.0.1:10911</code>.
        Pseudo virtual node example: <code>10.77.0.40</code>.
        Add at least one host NameServer such as <code>127.0.0.1:9876</code> if you want pseudo mode to use real RocketMQ message flow.
      </div>

      <div class="badge-row" style="margin-top: 14px">
        <el-button type="primary" :loading="serviceBusy" @click="submitService">Add Service</el-button>
        <el-button :disabled="serviceBusy" @click="resetServiceForm(props.cluster.mode)">Reset</el-button>
      </div>
    </section>

    <section class="panel panel-compact">
      <div class="panel-header">
        <div>
          <h2 class="panel-title">Activity Log</h2>
          <p class="panel-copy">Registration, deletion and simulation results are collected here for quick diagnosis.</p>
        </div>
      </div>
      <div class="log-list">
        <div v-for="entry in logs" :key="`${entry.timestamp}-${entry.nodeId}`" class="log-entry">
          [{{ entry.timestamp }}] [{{ entry.level }}] [{{ entry.nodeId }}] {{ entry.message }}
        </div>
      </div>
    </section>
  </div>
</template>
