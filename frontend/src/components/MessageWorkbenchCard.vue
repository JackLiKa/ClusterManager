<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { ClusterNode, ClusterSelection, MessageDeliveryResult } from '../types/cluster'
import { simulateMessages } from '../api/clusterApi'

const props = defineProps<{
  cluster: ClusterSelection
  nodes: ClusterNode[]
}>()

const running = ref(false)
const deliveries = ref<MessageDeliveryResult[]>([])

const brokerNodes = computed(() => props.nodes.filter(node => node.labels.role?.includes('broker')))

const form = reactive({
  topic: 'TopicTest',
  consumerGroup: 'cluster-manager-lab',
  messageCount: 8,
  payloadTemplate: '{"hello":"cluster"}',
  producerNodeId: '',
  consumerNodeIds: [] as string[]
})

watch(
  () => props.nodes,
  nodes => {
    const producer = nodes.find(node => node.labels.role === 'broker-master')
      ?? nodes.find(node => node.labels.role?.includes('broker'))
      ?? nodes[0]
    form.producerNodeId = producer?.nodeId ?? ''
    form.consumerNodeIds = nodes
      .filter(node => node.labels.role?.includes('broker'))
      .map(node => node.nodeId)
  },
  { immediate: true }
)

const workbenchHint = computed(() => props.cluster.mode === 'PSEUDO'
  ? 'Pseudo mode can route validation through a real RocketMQ NameServer when host nodes are registered, while virtual nodes remain visible in the same topology.'
  : 'Real mode uses the RocketMQ admin-backed view and manually registered endpoints for message validation.')

async function submit(): Promise<void> {
  if (!form.producerNodeId) {
    ElMessage.error('No producer broker is available yet.')
    return
  }
  if (form.consumerNodeIds.length === 0) {
    ElMessage.error('No consumer broker is available yet.')
    return
  }

  running.value = true
  try {
    const result = await simulateMessages(props.cluster, {
      ...form,
      headers: {
        source: 'web-console'
      }
    })
    deliveries.value = result.deliveries
    const successCount = result.deliveries.filter(delivery => delivery.success).length
    ElMessage.success(`Validated ${result.deliveries.length} messages, ${successCount} succeeded.`)
  } finally {
    running.value = false
  }
}
</script>

<template>
  <section class="panel panel-compact">
    <div class="panel-header">
      <div>
        <h2 class="panel-title">Message Workbench</h2>
        <p class="panel-copy">{{ workbenchHint }}</p>
      </div>
    </div>

    <div class="helper-note" v-if="brokerNodes.length === 0">
      No broker is available yet. Add a broker in the operations panel first, then return here to validate message paths.
    </div>

    <el-form label-position="top" @submit.prevent>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="Topic">
            <el-input v-model="form.topic" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Consumer Group">
            <el-input v-model="form.consumerGroup" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="Producer Node">
            <el-select v-model="form.producerNodeId" style="width: 100%">
              <el-option v-for="node in brokerNodes" :key="node.nodeId" :label="node.displayName" :value="node.nodeId" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Consumers">
            <el-select v-model="form.consumerNodeIds" multiple style="width: 100%">
              <el-option v-for="node in brokerNodes" :key="node.nodeId" :label="node.displayName" :value="node.nodeId" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="Count">
            <el-input-number v-model="form.messageCount" :min="1" :max="500" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="16">
          <el-form-item label="Payload Template">
            <el-input v-model="form.payloadTemplate" type="textarea" :rows="3" />
          </el-form-item>
        </el-col>
      </el-row>

      <div class="badge-row">
        <el-button type="primary" :loading="running" @click="submit">Send & Validate</el-button>
      </div>
    </el-form>

    <el-table :data="deliveries" style="margin-top: 20px" height="240">
      <el-table-column prop="messageKey" label="Message Key" min-width="160" />
      <el-table-column prop="producerNodeId" label="Producer" min-width="140" />
      <el-table-column prop="consumerNodeId" label="Consumer" min-width="140" />
      <el-table-column prop="detail" label="Result" min-width="260" />
    </el-table>
  </section>
</template>
