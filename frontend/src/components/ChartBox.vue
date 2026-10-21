<template>
  <div ref="el" :style="{ width: '100%', height: height }"></div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  option: { type: Object, required: true },
  height: { type: String, default: '280px' }
})
const emit = defineEmits(['chart-click'])

const el = ref(null)
let chart = null
let ro = null

const render = () => {
  if (!el.value) return
  if (!chart) {
    chart = echarts.init(el.value)
    chart.on('click', (p) => emit('chart-click', p))
  }
  chart.setOption(props.option, true)
}

onMounted(async () => {
  await nextTick()
  render()
  ro = new ResizeObserver(() => chart && chart.resize())
  ro.observe(el.value)
})

watch(() => props.option, render, { deep: true })

onBeforeUnmount(() => {
  if (ro) ro.disconnect()
  if (chart) chart.dispose()
})
</script>
