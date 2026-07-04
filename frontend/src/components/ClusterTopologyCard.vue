<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { GraphChart } from 'echarts/charts'
import { LegendComponent, TooltipComponent } from 'echarts/components'
import { init, use, type ECharts } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import type { ClusterNode, ClusterTopology } from '../types/cluster'

use([CanvasRenderer, GraphChart, TooltipComponent, LegendComponent])

const props = defineProps<{
  topology: ClusterTopology | null
}>()

const canvasRef = ref<HTMLDivElement | null>(null)
let chart: ECharts | null = null

const title = computed(() => props.topology?.cluster.mode === 'PSEUDO'
  ? 'Pseudo Cluster Topology'
  : 'Physical Cluster Topology')

function renderChart(): void {
  if (!canvasRef.value || !props.topology) {
    return
  }

  chart ??= init(canvasRef.value)
  const layout = layoutNodes(props.topology.nodes)

  chart.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      confine: true,
      formatter: (params: { data?: { displayName?: string, hostName?: string, role?: string, virtualIp?: string, nodeKind?: string, status?: string } }) => {
        const data = params.data
        if (!data) {
          return ''
        }
        return [
          `<strong>${data.displayName ?? ''}</strong>`,
          `Role: ${data.role ?? 'unknown'}`,
          `Address: ${data.virtualIp ?? 'N/A'}`,
          `Host: ${data.hostName ?? 'N/A'}`,
          `Kind: ${data.nodeKind ?? 'VIRTUAL'}`,
          `Status: ${data.status ?? 'UNKNOWN'}`
        ].join('<br/>')
      }
    },
    animationDurationUpdate: 400,
    series: [
      {
        type: 'graph',
        layout: 'none',
        roam: true,
        draggable: true,
        edgeSymbol: ['none', 'arrow'],
        cursor: 'grab',
        emphasis: {
          focus: 'adjacency'
        },
        label: {
          show: true,
          color: '#173126',
          fontWeight: 700,
          lineHeight: 18,
          formatter: (params: { data: { name: string } }) => params.data.name
        },
        edgeLabel: {
          show: true,
          color: '#4f655a',
          formatter: (params: { data: { linkType: string } }) => params.data.linkType
        },
        lineStyle: {
          color: '#8cb194',
          width: 3,
          curveness: 0.08
        },
        data: layout,
        links: props.topology.links.map(link => ({
          source: link.sourceNodeId,
          target: link.targetNodeId,
          linkType: link.linkType,
          latencyMs: link.latencyMs,
          lineStyle: {
            color: link.healthy ? '#7cb486' : '#c2503d',
            type: link.healthy ? 'solid' : 'dashed',
            width: link.healthy ? 3 : 2
          }
        }))
      }
    ]
  })

  chart.resize()
}

function layoutNodes(nodes: ClusterNode[]) {
  const groups = {
    nameserver: [] as ClusterNode[],
    brokerMaster: [] as ClusterNode[],
    brokerSlave: [] as ClusterNode[],
    proxy: [] as ClusterNode[],
    other: [] as ClusterNode[]
  }

  for (const node of nodes) {
    const role = node.labels.role ?? ''
    if (role === 'nameserver') {
      groups.nameserver.push(node)
    } else if (role === 'broker-master') {
      groups.brokerMaster.push(node)
    } else if (role === 'broker-slave') {
      groups.brokerSlave.push(node)
    } else if (role === 'proxy') {
      groups.proxy.push(node)
    } else {
      groups.other.push(node)
    }
  }

  const columns = [
    { x: 140, items: groups.nameserver },
    { x: 420, items: groups.brokerMaster },
    { x: 700, items: groups.brokerSlave },
    { x: 980, items: groups.proxy },
    { x: 1260, items: groups.other }
  ]

  return columns.flatMap(column => column.items.map((node, index) => ({
    id: node.nodeId,
    name: `${node.displayName}\n${node.virtualIp}`,
    displayName: node.displayName,
    hostName: node.hostName,
    role: node.labels.role ?? 'unknown',
    virtualIp: node.virtualIp,
    nodeKind: node.labels.nodeKind ?? 'VIRTUAL',
    status: node.status,
    x: column.x,
    y: 120 + index * 160,
    symbol: node.labels.nodeKind === 'HOST' ? 'roundRect' : 'circle',
    symbolSize: node.labels.nodeKind === 'HOST'
      ? [180, node.status === 'RUNNING' ? 72 : 64]
      : (node.status === 'RUNNING' ? 88 : 74),
    itemStyle: {
      color: colorOf(node),
      shadowBlur: 24,
      shadowColor: 'rgba(23, 49, 38, 0.14)'
    },
    label: {
      width: 170,
      overflow: 'break'
    }
  })))
}

function colorOf(node: ClusterNode): string {
  if (node.status !== 'RUNNING') {
    return '#c2503d'
  }
  if (node.labels.nodeKind === 'HOST') {
    return '#14532d'
  }
  switch (node.labels.role) {
    case 'nameserver':
      return '#2a6f97'
    case 'broker-master':
      return '#2f7d4f'
    case 'broker-slave':
      return '#ca8a04'
    case 'proxy':
      return '#7c3aed'
    default:
      return '#4a6a62'
  }
}

function handleResize(): void {
  chart?.resize()
}

onMounted(() => {
  renderChart()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})

watch(() => props.topology, renderChart, { deep: true })
</script>

<template>
  <section class="panel topology-panel">
    <div class="panel-header">
      <div>
        <h2 class="panel-title">Topology</h2>
        <p class="panel-copy">{{ title }} · Entry {{ topology?.activeVip ?? 'N/A' }}</p>
      </div>
      <div class="legend-row">
        <span class="legend-chip ns">NameServer</span>
        <span class="legend-chip master">Broker Master</span>
        <span class="legend-chip slave">Broker Slave</span>
        <span class="legend-chip proxy">Proxy</span>
        <span class="legend-chip host">Host Node</span>
      </div>
    </div>
    <div class="helper-note helper-note-inline">
      Use mouse wheel to zoom, drag the canvas, drag individual nodes, and hover for node details.
    </div>
    <div ref="canvasRef" class="topology-shell" />
  </section>
</template>
