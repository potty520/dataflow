<script setup>
import { computed, ref, watch } from 'vue'
import { VueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'

const props = defineProps({
  modelValue: { type: String, default: '{"nodes":[],"edges":[]}' }
})

const emit = defineEmits(['update:modelValue'])

const NODE_TYPES = [
  { type: 'extract', label: '数据抽取', color: '#409eff' },
  { type: 'transform', label: '数据转换', color: '#67c23a' },
  { type: 'quality', label: '质量检查', color: '#e6a23c' },
  { type: 'governance', label: '数据治理', color: '#909399' },
  { type: 'load', label: '数据加载', color: '#f56c6c' },
  { type: 'output', label: '数据输出', color: '#626aef' },
  { type: 'condition', label: '条件分支', color: '#b88230' }
]

const nodes = ref([])
const edges = ref([])
const selectedNodeId = ref(null)
const nodeIdSeq = ref(1)

const configPanel = computed(() => {
  if (!selectedNodeId.value) return null
  const n = nodes.value.find(x => x.id === selectedNodeId.value)
  return n?.data || null
})

function parseDag(jsonStr) {
  try {
    const dag = JSON.parse(jsonStr || '{}')
    const rawNodes = dag.nodes || []
    const rawEdges = dag.edges || []
    let maxId = 0
    const parsedNodes = rawNodes.map((n, i) => {
      const id = String(n.id ?? i + 1)
      maxId = Math.max(maxId, Number(id) || 0)
      const meta = NODE_TYPES.find(t => t.type === n.type) || { label: n.label || n.type || '节点', type: n.type || 'custom', color: '#409eff' }
      return {
        id,
        type: 'default',
        label: n.label || meta.label,
        position: n.position || { x: 60 + (i % 4) * 200, y: 60 + Math.floor(i / 4) * 120 },
        data: { nodeType: n.type || meta.type, label: n.label || meta.label, color: meta.color, config: n.config || {} }
      }
    })
    const parsedEdges = rawEdges.map((e, i) => ({
      id: e.id || `e-${e.source || e.from}-${e.target || e.to}-${i}`,
      source: String(e.source ?? e.from),
      target: String(e.target ?? e.to),
      animated: true,
      label: e.label || ''
    }))
    nodeIdSeq.value = maxId + 1
    return { nodes: parsedNodes, edges: parsedEdges }
  } catch {
    return { nodes: [], edges: [] }
  }
}

function serializeDag() {
  return JSON.stringify({
    nodes: nodes.value.map(n => ({
      id: n.id,
      type: n.data?.nodeType,
      label: n.data?.label || n.label,
      position: n.position,
      config: n.data?.config || {}
    })),
    edges: edges.value.map(e => ({
      id: e.id,
      source: e.source,
      target: e.target,
      label: e.label
    }))
  }, null, 2)
}

function loadFromModel() {
  const parsed = parseDag(props.modelValue)
  nodes.value = parsed.nodes
  edges.value = parsed.edges
}

function emitChange() {
  emit('update:modelValue', serializeDag())
}

watch(() => props.modelValue, loadFromModel, { immediate: true })
watch([nodes, edges], emitChange, { deep: true })

watch(() => configPanel.value?.label, (val) => {
  if (!selectedNodeId.value || !val) return
  const n = nodes.value.find(x => x.id === selectedNodeId.value)
  if (n) {
    n.label = val
    if (n.data) n.data.label = val
  }
})

function onConnect(params) {
  edges.value = [
    ...edges.value,
    { ...params, id: `e-${params.source}-${params.target}-${Date.now()}`, animated: true }
  ]
}

function addNode(meta) {
  const id = String(nodeIdSeq.value++)
  nodes.value = [
    ...nodes.value,
    {
      id,
      type: 'default',
      label: meta.label,
      position: { x: 120 + Math.random() * 160, y: 100 + Math.random() * 120 },
      data: { nodeType: meta.type, label: meta.label, color: meta.color, config: { remark: '' } }
    }
  ]
}

function onNodeClick({ node }) {
  selectedNodeId.value = node.id
}

function onPaneClick() {
  selectedNodeId.value = null
}

function deleteSelected() {
  if (!selectedNodeId.value) return
  const id = selectedNodeId.value
  nodes.value = nodes.value.filter(n => n.id !== id)
  edges.value = edges.value.filter(e => e.source !== id && e.target !== id)
  selectedNodeId.value = null
}

function deleteSelectedEdge() {
  const sel = edges.value.find(e => e.selected)
  if (sel) edges.value = edges.value.filter(e => e.id !== sel.id)
}

defineExpose({ serializeDag })
</script>

<template>
  <div class="workflow-editor">
    <div class="palette">
      <div class="palette-title">算子面板</div>
      <div
        v-for="item in NODE_TYPES"
        :key="item.type"
        class="palette-item"
        :style="{ borderLeftColor: item.color }"
        @click="addNode(item)"
      >
        {{ item.label }}
      </div>
      <el-divider />
      <el-button size="small" type="danger" :disabled="!selectedNodeId" @click="deleteSelected">删除节点</el-button>
      <el-button size="small" @click="deleteSelectedEdge">删除连线</el-button>
      <p class="palette-tip">从节点连接点拖拽到另一节点以建立依赖关系</p>
    </div>
    <div class="canvas-wrap">
      <VueFlow
        v-model:nodes="nodes"
        v-model:edges="edges"
        :default-viewport="{ zoom: 0.85 }"
        :snap-to-grid="true"
        :snap-grid="[16, 16]"
        fit-view-on-init
        @connect="onConnect"
        @node-click="onNodeClick"
        @pane-click="onPaneClick"
      >
        <Background pattern-color="#e8e8e8" :gap="16" />
        <Controls />
      </VueFlow>
    </div>
    <div v-if="configPanel" class="config-panel">
      <div class="palette-title">节点配置</div>
      <el-form label-width="72px" size="small">
        <el-form-item label="名称"><el-input v-model="configPanel.label" /></el-form-item>
        <el-form-item label="类型"><el-input v-model="configPanel.nodeType" disabled /></el-form-item>
        <el-form-item label="参数"><el-input v-model="configPanel.config.remark" type="textarea" :rows="3" placeholder="算子参数或备注" /></el-form-item>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.workflow-editor {
  display: flex;
  height: 520px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  overflow: hidden;
  background: #fafafa;
}
.palette {
  width: 160px;
  padding: 12px;
  background: #fff;
  border-right: 1px solid #eee;
  flex-shrink: 0;
}
.palette-title { font-weight: 600; margin-bottom: 10px; font-size: 13px; }
.palette-item {
  padding: 8px 10px;
  margin-bottom: 8px;
  background: #f5f7fa;
  border-left: 3px solid #409eff;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
}
.palette-item:hover { background: #ecf5ff; }
.palette-tip { font-size: 11px; color: #999; line-height: 1.4; margin-top: 8px; }
.canvas-wrap { flex: 1; min-width: 0; height: 100%; }
.config-panel {
  width: 220px;
  padding: 12px;
  background: #fff;
  border-left: 1px solid #eee;
  flex-shrink: 0;
}
:deep(.vue-flow) { height: 100%; }
:deep(.vue-flow__node) {
  font-size: 12px;
  padding: 8px 14px;
  border-radius: 6px;
  border: 1px solid #409eff;
  background: #fff;
  box-shadow: 0 2px 6px rgba(0,0,0,.08);
}
:deep(.vue-flow__node.selected) {
  border-color: #f56c6c;
  box-shadow: 0 0 0 2px rgba(245,108,108,.3);
}
</style>
