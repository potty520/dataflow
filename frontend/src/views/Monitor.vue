<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>运维监控</h2>
        <div class="sub">JVM / 数据库 / 质量 / 集成 / BPM 指标采集，服务探针与告警处置</div>
      </div>
      <div>
        <el-button size="small" @click="refresh">刷新</el-button>
        <el-button size="small" type="primary" @click="collect">立即采集</el-button>
      </div>
    </div>

    <div class="grid grid-4" style="margin-bottom:12px">
      <div class="stat"><div class="label">JVM 堆使用</div><div class="value">{{ fmt(metrics['jvm.heap.percent']) }}%</div><div class="foot">线程 {{ metrics['jvm.threads'] || 0 }}</div></div>
      <div class="stat green"><div class="label">数据库时延</div><div class="value">{{ fmt(metrics['db.latency.ms']) }} ms</div><div class="foot">{{ metrics['db.up'] ? '连接正常' : '连接异常' }}</div></div>
      <div class="stat orange"><div class="label">未闭环问题</div><div class="value">{{ metrics['quality.open.issues'] || 0 }}</div><div class="foot">质量域</div></div>
      <div class="stat" :class="{ red: openAlerts > 0 }"><div class="label">未处置告警</div><div class="value">{{ openAlerts }}</div><div class="foot">24h 集成失败 {{ metrics['integration.failed.jobs'] || 0 }}</div></div>
    </div>

    <el-tabs v-model="tab" type="border-card">
      <el-tab-pane label="服务探针" name="services">
        <el-table :data="services" size="small">
          <el-table-column prop="name" label="服务" width="140" />
          <el-table-column prop="code" label="模块" width="180"><template #default="{ row }"><span class="mono">{{ row.code }}</span></template></el-table-column>
          <el-table-column prop="status" label="状态" width="100"><template #default="{ row }"><el-tag size="small" :type="row.status === 'UP' ? 'success' : row.status === 'WARN' ? 'warning' : 'danger'">{{ row.status }}</el-tag></template></el-table-column>
          <el-table-column prop="latency" label="时延(ms)" width="110" align="right" />
          <el-table-column prop="detail" label="说明" min-width="240" />
        </el-table>
        <div class="hint" style="margin-top:8px">采集周期 60 秒；指标快照保留在 mon_metric_snapshot。</div>
      </el-tab-pane>

      <el-tab-pane label="告警中心" name="alerts">
        <div class="toolbar">
          <el-select v-model="alertStatus" size="small" style="width:140px" clearable @change="loadAlerts">
            <el-option label="OPEN" value="OPEN" /><el-option label="ACK" value="ACK" />
          </el-select>
          <el-button size="small" @click="loadAlerts">查询</el-button>
        </div>
        <el-table :data="alerts" size="small">
          <el-table-column prop="createTime" label="时间" width="160"><template #default="{ row }">{{ fmtTime(row.createTime) }}</template></el-table-column>
          <el-table-column prop="level" label="级别" width="90"><template #default="{ row }"><el-tag size="small" :type="row.level === 'CRITICAL' ? 'danger' : 'warning'">{{ row.level }}</el-tag></template></el-table-column>
          <el-table-column prop="ruleName" label="规则" width="180" />
          <el-table-column prop="metric" label="指标" width="200"><template #default="{ row }"><span class="mono">{{ row.metric }}</span></template></el-table-column>
          <el-table-column prop="metricValue" label="值" width="100" align="right" />
          <el-table-column prop="message" label="详情" min-width="260" show-overflow-tooltip />
          <el-table-column prop="status" label="状态" width="90" />
          <el-table-column label="操作" width="90">
            <template #default="{ row }"><el-button v-if="row.status === 'OPEN'" link type="primary" size="small" @click="ack(row)">处置</el-button></template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="告警规则" name="rules">
        <div class="toolbar">
          <span class="hint">阈值规则在采集后评估，同一规则仅保留一条 OPEN 告警</span>
          <span class="spacer"></span>
          <el-button size="small" type="primary" @click="openRule()">新建规则</el-button>
        </div>
        <el-table :data="rules" size="small">
          <el-table-column prop="code" label="编码" width="180"><template #default="{ row }"><span class="mono">{{ row.code }}</span></template></el-table-column>
          <el-table-column prop="name" label="名称" width="180" />
          <el-table-column prop="metric" label="指标" width="220"><template #default="{ row }"><span class="mono">{{ row.metric }}</span></template></el-table-column>
          <el-table-column prop="operator" label="条件" width="80" />
          <el-table-column prop="threshold" label="阈值" width="90" align="right" />
          <el-table-column prop="level" label="级别" width="90" />
          <el-table-column prop="enabled" label="启用" width="80"><template #default="{ row }">{{ row.enabled ? '是' : '否' }}</template></el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openRule(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="removeRule(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="ruleDialog" :title="ruleForm.id ? '编辑告警规则' : '新建告警规则'" width="520px">
      <el-form label-width="100px" size="small">
        <el-form-item label="编码"><el-input v-model="ruleForm.code" :disabled="!!ruleForm.id" placeholder="MON_DB_LATENCY" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="ruleForm.name" /></el-form-item>
        <el-form-item label="指标">
          <el-select v-model="ruleForm.metric" style="width:100%">
            <el-option v-for="(label, key) in metricLabels" :key="key" :label="label + '（' + key + '）'" :value="key" />
          </el-select>
        </el-form-item>
        <el-form-item label="条件">
          <el-select v-model="ruleForm.operator" style="width:90px"><el-option label=">" value=">" /><el-option label=">=" value=">=" /><el-option label="<" value="<" /><el-option label="<=" value="<=" /></el-select>
          <el-input-number v-model="ruleForm.threshold" :min="0" style="margin-left:10px" />
        </el-form-item>
        <el-form-item label="级别"><el-radio-group v-model="ruleForm.level"><el-radio label="WARN">WARN</el-radio><el-radio label="CRITICAL">CRITICAL</el-radio></el-radio-group></el-form-item>
        <el-form-item label="启用"><el-switch v-model="ruleForm.enabled" :active-value="1" :inactive-value="0" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="ruleForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="ruleDialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="saveRule">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { monitorApi } from '@/api'

const tab = ref('services')
const metrics = ref({})
const services = ref([])
const openAlerts = ref(0)
const alerts = ref([])
const rules = ref([])
const alertStatus = ref('OPEN')
const ruleDialog = ref(false)
const ruleForm = reactive({ id: null, code: '', name: '', metric: 'jvm.heap.percent', operator: '>', threshold: 80, level: 'WARN', enabled: 1, remark: '' })
const metricLabels = {
  'jvm.heap.percent': 'JVM 堆使用率',
  'jvm.threads': 'JVM 线程数',
  'db.latency.ms': '数据库时延',
  'quality.open.issues': '未闭环质量问题',
  'integration.failed.jobs': '24h 集成失败',
  'bpm.pending.tasks': 'BPM 待办',
  'alerts.open': '未处置告警'
}
const fmt = (v) => (v == null ? '-' : Number(v).toFixed(2))
const fmtTime = (v) => (v ? String(v).replace('T', ' ').slice(0, 19) : '-')

const loadOverview = async () => {
  try {
    const d = await monitorApi.overview()
    metrics.value = d.metrics || {}; services.value = d.services || []; openAlerts.value = d.openAlerts || 0
  } catch (e) { /* ignore */ }
}
const loadAlerts = async () => { try { alerts.value = (await monitorApi.alerts({ status: alertStatus.value || undefined })).records || [] } catch (e) { alerts.value = [] } }
const loadRules = async () => { try { rules.value = (await monitorApi.rules()).records || [] } catch (e) { rules.value = [] } }
const refresh = async () => { await Promise.allSettled([loadOverview(), loadAlerts(), loadRules()]) }
const collect = async () => { try { await monitorApi.collect(); ElMessage.success('采集完成'); refresh() } catch (e) { /* http 层已提示 */ } }
const ack = async (row) => { try { await monitorApi.ack(row.id); ElMessage.success('已处置'); refresh() } catch (e) { /* http 层已提示 */ } }
const openRule = (row) => {
  Object.assign(ruleForm, { id: null, code: '', name: '', metric: 'jvm.heap.percent', operator: '>', threshold: 80, level: 'WARN', enabled: 1, remark: '' })
  if (row) Object.assign(ruleForm, { id: row.id, code: row.code, name: row.name, metric: row.metric, operator: row.operator, threshold: row.threshold, level: row.level, enabled: row.enabled, remark: row.remark })
  ruleDialog.value = true
}
const saveRule = async () => { try { await monitorApi.saveRule({ ...ruleForm }); ElMessage.success('规则已保存'); ruleDialog.value = false; refresh() } catch (e) { /* http 层已提示 */ } }
const removeRule = async (row) => { try { await ElMessageBox.confirm('删除规则 ' + row.code + '？', '提示', { type: 'warning' }); await monitorApi.deleteRule(row.id); refresh() } catch (e) { /* 取消 */ } }

onMounted(refresh)
</script>

<style scoped>
.page { padding: 4px; }
.hint { color: #909399; font-size: 12px; }
.stat.red .value { color: #f56c6c; }
</style>
