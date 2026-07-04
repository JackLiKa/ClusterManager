<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { fetchLogs, fetchMetrics, fetchProviders, fetchTopology } from '../api/clusterApi'
import ClusterTopologyCard from '../components/ClusterTopologyCard.vue'
import MessageWorkbenchCard from '../components/MessageWorkbenchCard.vue'
import OperationsPanel from '../components/OperationsPanel.vue'
import { useClusterStreams } from '../composables/useClusterStreams'
import type { ClusterMode, ClusterSelection, ClusterTopology, LogEntry, MonitoringSnapshot, ProviderDescriptor } from '../types/cluster'

const router = useRouter()
const providers = ref<ProviderDescriptor[]>([])
const topology = ref<ClusterTopology | null>(null)
const metrics = ref<MonitoringSnapshot | null>(null)
const logs = ref<LogEntry[]>([])
const loading = ref(false)

const selected = reactive<ClusterSelection>({
  clusterId: 'local-lab',
  mode: 'PSEUDO',
  middleware: 'ROCKETMQ'
})

const currentProvider = computed(() => providers.value.find(provider =>
  provider.mode === selected.mode && provider.middleware === selected.middleware
))

const topologyStats = computed(() => {
  const nodes = topology.value?.nodes ?? []
  return {
    totalNodes: nodes.length,
    runningNodes: nodes.filter(node => node.status === 'RUNNING').length,
    hostNodes: nodes.filter(node => node.labels.nodeKind === 'HOST').length,
    virtualNodes: nodes.filter(node => node.labels.nodeKind !== 'HOST').length
  }
})

async function reload(): Promise<void> {
  loading.value = true
  try {
    topology.value = await fetchTopology(selected)
    metrics.value = await fetchMetrics(selected)
    logs.value = await fetchLogs(selected)
  } finally {
    loading.value = false
  }
}

async function bootstrap(): Promise<void> {
  providers.value = await fetchProviders()
  await reload()
}

function switchMode(mode: ClusterMode): void {
  if (selected.mode === mode) {
    return
  }
  selected.mode = mode
  selected.clusterId = mode === 'PSEUDO' ? 'local-lab' : 'rocketmq-demo'
}

function openGuide(): void {
  void router.push('/guide')
}

watch(
  () => [selected.clusterId, selected.mode, selected.middleware] as const,
  () => {
    void reload()
  }
)

onMounted(bootstrap)

useClusterStreams(selected, {
  onMetrics(payload) {
    metrics.value = payload
  },
  onLogs(payload) {
    logs.value = payload
  }
})
</script>

<template>
  <main class="dashboard-shell">
    <header class="topbar">
      <div class="topbar-brand">
        <div class="brand-kicker">Cluster Console</div>
        <h1>Cluster Manager</h1>
        <p>Manage host RocketMQ nodes and TAP-backed virtual service nodes in one dashboard for topology, operations and message validation.</p>
      </div>

      <div class="topbar-controls">
        <div class="segmented-control">
          <button class="segmented-button" :class="{ active: selected.mode === 'PSEUDO' }" type="button" @click="switchMode('PSEUDO')">
            Local Pseudo
          </button>
          <button class="segmented-button" :class="{ active: selected.mode === 'REAL' }" type="button" @click="switchMode('REAL')">
            Real Cluster
          </button>
        </div>
        <el-button :loading="loading" @click="reload">Refresh</el-button>
        <el-button type="primary" plain @click="openGuide">Guide</el-button>
      </div>
    </header>

    <section class="summary-ribbon">
      <article class="summary-card">
        <span class="summary-label">Mode</span>
        <strong class="summary-value">{{ selected.mode }}</strong>
      </article>
      <article class="summary-card">
        <span class="summary-label">Provider</span>
        <strong class="summary-value summary-value-small">{{ currentProvider?.displayName ?? 'N/A' }}</strong>
      </article>
      <article class="summary-card">
        <span class="summary-label">Active VIP</span>
        <strong class="summary-value summary-value-small">{{ topology?.activeVip ?? 'N/A' }}</strong>
      </article>
      <article class="summary-card">
        <span class="summary-label">Node Overview</span>
        <strong class="summary-value summary-value-small">
          {{ topologyStats.runningNodes }}/{{ topologyStats.totalNodes }} running
        </strong>
        <span class="summary-meta">Host {{ topologyStats.hostNodes }} · Virtual {{ topologyStats.virtualNodes }}</span>
      </article>
    </section>

    <section class="dashboard-grid">
      <div class="dashboard-main">
        <ClusterTopologyCard :topology="topology" />
        <MessageWorkbenchCard :cluster="selected" :nodes="topology?.nodes ?? []" />
      </div>
      <OperationsPanel
        :cluster="selected"
        :nodes="topology?.nodes ?? []"
        :metrics="metrics"
        :logs="logs"
        @refresh="reload"
      />
    </section>
  </main>
</template>
