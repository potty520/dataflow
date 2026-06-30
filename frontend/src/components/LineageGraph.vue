<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  graph: {
    type: Object,
    default: () => ({ nodes: [], edges: [] })
  },
  highlightId: { type: [Number, String], default: null }
})

const emit = defineEmits(['node-click'])

const chartRef = ref(null)
let chart = null

const LAYER_ORDER = ['ODS', 'STD', 'DWD', 'DWS', 'ADS', 'OTHER']
const LAYER_COLORS = {
  ODS: '#5470c6',
  STD: '#91cc75',
  DWD: '#fac858',
  DWS: '#ee6666',
  ADS: '#73c0de',
  OTHER: '#909399'
}

function buildOption(graph) {
  const categories = LAYER_ORDER.map(name => ({ name }))
  const nodeMap = new Map()

  const nodes = (graph.nodes || []).map(n => {
    const layer = n.layer || 'OTHER'
    const catIdx = Math.max(0, LAYER_ORDER.indexOf(layer))
    const isHighlight = props.highlightId != null && String(n.id) === String(props.highlightId)
    const item = {
      id: String(n.id),
      name: n.name,
      value: n.label || n.name,
      category: catIdx,
      symbolSize: isHighlight ? 58 : (n.isKeyAsset ? 52 : 42),
      itemStyle: {
        color: LAYER_COLORS[layer] || LAYER_COLORS.OTHER,
        borderColor: isHighlight ? '#f56c6c' : '#fff',
        borderWidth: isHighlight ? 3 : 1
      },
      label: {
        show: true,
        formatter: n.label ? `${n.name}\n${n.label}` : n.name,
        fontSize: 11
      }
    }
    nodeMap.set(String(n.id), item)
    nodeMap.set(n.name, item)
    return item
  })

  const links = (graph.edges || []).map(e => ({
    source: String(e.sourceId ?? e.source),
    target: String(e.targetId ?? e.target),
    value: e.relationType || 'DERIVE',
    lineStyle: { curveness: 0.25 },
    label: { show: true, formatter: e.relationType || 'ETL', fontSize: 10 }
  }))

  return {
    tooltip: {
      trigger: 'item',
      formatter: params => {
        if (params.dataType === 'edge') {
          return `${params.data.source} → ${params.data.target}<br/>关系: ${params.data.value}`
        }
        const d = params.data
        return `<b>${d.name}</b><br/>${d.value || ''}<br/>分层: ${LAYER_ORDER[d.category] || ''}`
      }
    },
    legend: [{ data: LAYER_ORDER, bottom: 0, textStyle: { fontSize: 11 } }],
    series: [{
      type: 'graph',
      layout: 'force',
      roam: true,
      draggable: true,
      focusNodeAdjacency: true,
      categories,
      data: nodes,
      links,
      force: {
        repulsion: 280,
        edgeLength: [80, 160],
        gravity: 0.08,
        layoutAnimation: true
      },
      emphasis: {
        focus: 'adjacency',
        lineStyle: { width: 3 }
      },
      lineStyle: { color: '#aaa', width: 1.5, opacity: 0.85 }
    }]
  }
}

function render() {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  chart.setOption(buildOption(props.graph), true)
}

function handleResize() {
  chart?.resize()
}

onMounted(() => {
  render()
  chart?.on('click', params => {
    if (params.dataType === 'node') {
      emit('node-click', params.data)
    }
  })
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})

watch(() => [props.graph, props.highlightId], render, { deep: true })

defineExpose({ resize: handleResize })
</script>

<template>
  <div ref="chartRef" class="lineage-graph" />
</template>

<style scoped>
.lineage-graph {
  width: 100%;
  height: 100%;
  min-height: 420px;
}
</style>
