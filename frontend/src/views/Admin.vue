<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>系统管理</h2>
        <div class="sub">平台健康、服务清单、运行参数、用户角色与接口清单</div>
      </div>
      <el-button size="small" @click="load">刷新</el-button>
    </div>

    <div class="grid grid-4" style="margin-bottom:12px">
      <div class="stat"><div class="label">平台状态</div><div class="value" :style="{ color: health.status === 'UP' ? '#16a34a' : '#dc2626' }">{{ health.status }}</div><div class="foot">版本 {{ health.version }}</div></div>
      <div class="stat"><div class="label">运行时长</div><div class="value">{{ Math.floor((health.uptimeSeconds || 0) / 60) }}<small>分钟</small></div><div class="foot">{{ health.components?.[0]?.detail || '' }}</div></div>
      <div class="stat orange"><div class="label">堆内存</div><div class="value">{{ health.jvm?.heapUsedMb }}<small>/ {{ health.jvm?.heapMaxMb }} MB</small></div><div class="foot">CPU {{ health.jvm?.processors }} 核</div></div>
      <div class="stat green"><div class="label">元数据对象</div><div class="value">{{ health.data?.tables }}</div><div class="foot">规则 {{ health.data?.qualityRules }} · API {{ health.data?.apiCount }}</div></div>
    </div>

    <el-tabs v-model="tab" type="border-card" style="border-radius:8px">
      <el-tab-pane label="平台健康" name="health">
        <el-table :data="health.components || []" size="small">
          <el-table-column prop="name" label="组件" width="170"><template #default="{ row }"><span class="mono">{{ row.name }}</span></template></el-table-column>
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'UP' ? 'success' : row.status === 'N/A' ? 'info' : 'danger'" effect="plain">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="detail" label="说明" min-width="380" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="服务清单" name="services">
        <div class="muted" style="font-size:12px;margin-bottom:8px">
          基线要求 14 个微服务；本交付将领域服务合并为单进程 dgp-boot，端口与领域边界保持一一对应，可平滑拆分。
        </div>
        <el-table :data="services" size="small">
          <el-table-column prop="service" label="服务名" width="180"><template #default="{ row }"><span class="mono">{{ row.service }}</span></template></el-table-column>
          <el-table-column prop="port" label="端口" width="90" align="right" />
          <el-table-column prop="duty" label="职责" min-width="240" />
          <el-table-column prop="module" label="实现模块" width="140"><template #default="{ row }"><span class="mono">com.dgp.{{ row.module }}</span></template></el-table-column>
          <el-table-column prop="status" label="状态" width="90"><template #default="{ row }"><el-tag size="small" type="success" effect="plain">{{ row.status }}</el-tag></template></el-table-column>
          <el-table-column prop="latencyMs" label="时延(ms)" width="100" align="right" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="运行参数" name="config">
        <el-table :data="configs" size="small">
          <el-table-column prop="configKey" label="参数键" width="260"><template #default="{ row }"><span class="mono">{{ row.configKey }}</span></template></el-table-column>
          <el-table-column prop="configValue" label="取值" min-width="320"><template #default="{ row }"><span class="mono">{{ row.configValue }}</span></template></el-table-column>
          <el-table-column prop="description" label="说明" min-width="240" />
          <el-table-column prop="updateTime" label="更新时间" width="150"><template #default="{ row }">{{ fmt(row.updateTime) }}</template></el-table-column>
          <el-table-column label="操作" width="80">
            <template #default="{ row }"><el-button link type="primary" size="small" @click="editConfig(row)">编辑</el-button></template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="用户与角色" name="user">
        <div class="grid grid-2">
          <div>
            <div class="section-title">用户（{{ users.length }}）</div>
            <el-table :data="users" size="small" max-height="330">
              <el-table-column prop="username" label="账号" width="110"><template #default="{ row }"><span class="mono">{{ row.username }}</span></template></el-table-column>
              <el-table-column prop="realName" label="姓名" width="90" />
              <el-table-column prop="roleName" label="角色" min-width="140" />
              <el-table-column prop="dept" label="部门" min-width="110" />
              <el-table-column prop="status" label="状态" width="80" />
            </el-table>
          </div>
          <div>
            <div class="section-title">角色（{{ roles.length }}）</div>
            <el-table :data="roles" size="small" max-height="330">
              <el-table-column prop="code" label="角色编码" width="190"><template #default="{ row }"><span class="mono">{{ row.code }}</span></template></el-table-column>
              <el-table-column prop="name" label="名称" width="140" />
              <el-table-column prop="userCount" label="用户数" width="80" align="right" />
              <el-table-column prop="description" label="职责" min-width="220" show-overflow-tooltip />
            </el-table>
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="接口清单" name="api">
        <div class="toolbar">
          <el-input v-model="apiKeyword" placeholder="按路径 / 权限 / 实现类过滤" clearable style="width:280px" />
          <span class="spacer"></span>
          <el-tag effect="plain" size="small">共 {{ filteredApis.length }} 条</el-tag>
        </div>
        <el-table :data="filteredApis" size="small" max-height="420">
          <el-table-column prop="method" label="方法" width="86">
            <template #default="{ row }"><el-tag size="small" :type="row.method === 'GET' ? 'success' : row.method === 'POST' ? 'warning' : 'info'">{{ row.method }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="path" label="路径" min-width="280"><template #default="{ row }"><span class="mono">{{ row.path }}</span></template></el-table-column>
          <el-table-column prop="perm" label="权限编码" width="240" />
          <el-table-column prop="handler" label="实现" min-width="260" />
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="configVisible" :title="`编辑参数 · ${configForm.key}`" width="560px">
      <el-input v-model="configForm.value" type="textarea" :rows="4" class="mono" />
      <template #footer>
        <el-button size="small" @click="configVisible = false">取消</el-button>
        <el-button size="small" type="primary" @click="saveConfig">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi, authApi } from '@/api'

const tab = ref('health')
const health = ref({})
const services = ref([])
const configs = ref([])
const users = ref([])
const roles = ref([])
const apis = ref([])
const apiKeyword = ref('')
const configVisible = ref(false)
const configForm = reactive({ key: '', value: '' })

const fmt = (v) => (v ? String(v).replace('T', ' ').slice(0, 16) : '-')

const filteredApis = computed(() => {
  const k = apiKeyword.value.trim()
  if (!k) return apis.value
  return apis.value.filter((a) => a.path.includes(k) || a.perm.includes(k) || a.handler.includes(k))
})

const load = async () => {
  // 各区块独立失败：任一接口异常只影响自己那块卡片/表格
  const [h, s, c, u, r, a] = await Promise.allSettled([
    adminApi.health(),
    adminApi.services(),
    adminApi.configs(),
    authApi.users({ pageSize: 50 }),
    authApi.roles(),
    adminApi.apis()
  ])
  health.value = h.status === 'fulfilled' ? h.value || {} : {}
  services.value = s.status === 'fulfilled' ? s.value.records || [] : []
  configs.value = c.status === 'fulfilled' ? c.value.records || [] : []
  users.value = u.status === 'fulfilled' ? u.value.records || [] : []
  roles.value = r.status === 'fulfilled' ? r.value.records || [] : []
  apis.value = a.status === 'fulfilled' ? a.value.records || [] : []
}

const editConfig = (row) => {
  configForm.key = row.configKey
  configForm.value = row.configValue
  configVisible.value = true
}

const saveConfig = async () => {
  try {
    await adminApi.saveConfig(configForm.key, configForm.value)
    ElMessage.success('参数已保存')
    configVisible.value = false
    load()
  } catch (e) {
    /* http 层已提示 */
  }
}

onMounted(load)
</script>
