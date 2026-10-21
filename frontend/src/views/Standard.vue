<template>
  <div class="page">
    <div class="page-title">
      <div>
        <h2>数据标准</h2>
        <div class="sub">数据元定义、分类发布与标准体系维护，作为建表与开发流程的强制引用基线</div>
      </div>
      <el-button type="primary" size="small" :icon="Plus" @click="dialog = true">新建数据元</el-button>
    </div>

    <div class="grid grid-4" style="margin-bottom:12px">
      <div class="stat"><div class="label">数据元</div><div class="value">{{ stats.elementCount }}</div><div class="foot">已发布 {{ stats.publishedCount }} 个</div></div>
      <div class="stat green"><div class="label">数据标准</div><div class="value">{{ stats.standardCount }}</div><div class="foot">客户/交易/产品/设备/政务</div></div>
      <div class="stat orange"><div class="label">字段映射</div><div class="value">{{ stats.mappingCount }}</div><div class="foot">已确认落地的字段级映射</div></div>
      <div class="stat"><div class="label">行业来源</div><div class="value">{{ (stats.bySource || []).length }}</div><div class="foot">国标 / 行标 / 企业标准</div></div>
    </div>

    <el-tabs v-model="tab" type="border-card" style="border-radius:8px">
      <el-tab-pane label="数据元" name="element">
        <div class="toolbar">
          <el-input v-model="query.keyword" placeholder="编码 / 名称" clearable style="width:200px" @keyup.enter="load" @clear="load" />
          <el-select v-model="query.category" placeholder="分类" clearable style="width:130px" @change="load">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
          <el-button size="small" type="primary" @click="load">查询</el-button>
          <span class="spacer"></span>
          <router-link to="/standard/mapping"><el-button size="small" type="primary" plain>进入映射与落地检查</el-button></router-link>
        </div>
        <el-table :data="elements" size="small" v-loading="loading">
          <el-table-column prop="code" label="数据元编码" width="150"><template #default="{ row }"><span class="mono">{{ row.code }}</span></template></el-table-column>
          <el-table-column prop="cnName" label="中文名" width="130" />
          <el-table-column prop="name" label="英文名" width="140"><template #default="{ row }"><span class="mono">{{ row.name }}</span></template></el-table-column>
          <el-table-column prop="category" label="分类" width="90" />
          <el-table-column prop="dataType" label="类型" width="100" />
          <el-table-column prop="dataLen" label="长度" width="80" />
          <el-table-column prop="fmt" label="格式/取值" min-width="150" show-overflow-tooltip />
          <el-table-column prop="ruleDesc" label="质量规则" min-width="180" show-overflow-tooltip />
          <el-table-column prop="source" label="来源" width="130" />
          <el-table-column prop="mappedColumns" label="已映射字段" width="100" align="right" />
          <el-table-column prop="status" label="状态" width="90">
            <template #default="{ row }"><el-tag size="small" :type="row.status === 'PUBLISHED' ? 'success' : 'info'" effect="plain">{{ row.status }}</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status !== 'PUBLISHED'" link type="primary" size="small" @click="publish(row)">发布</el-button>
              <span v-else class="muted" style="font-size:12px">已生效</span>
            </template>
          </el-table-column>
        </el-table>
        <div style="display:flex;justify-content:flex-end;margin-top:10px">
          <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total"
                         layout="total, prev, pager, next" @change="load" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="数据标准" name="standard">
        <el-table :data="standards" size="small">
          <el-table-column prop="code" label="标准编码" width="170"><template #default="{ row }"><span class="mono">{{ row.code }}</span></template></el-table-column>
          <el-table-column prop="cnName" label="标准名称" min-width="180" />
          <el-table-column prop="category" label="主题" width="100" />
          <el-table-column prop="industry" label="行业" width="100" />
          <el-table-column prop="source" label="依据" width="130" />
          <el-table-column prop="elementCount" label="数据元数" width="90" align="right" />
          <el-table-column prop="version" label="版本" width="80" />
          <el-table-column prop="status" label="状态" width="90"><template #default="{ row }"><el-tag size="small" type="success" effect="plain">{{ row.status }}</el-tag></template></el-table-column>
          <el-table-column prop="description" label="说明" min-width="240" show-overflow-tooltip />
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="dialog" title="新建数据元" width="560px">
      <el-form :model="form" label-width="90px" size="small">
        <el-form-item label="编码"><el-input v-model="form.code" placeholder="DE_XXX" /></el-form-item>
        <el-form-item label="中文名"><el-input v-model="form.cnName" /></el-form-item>
        <el-form-item label="英文名"><el-input v-model="form.name" placeholder="customer_id" /></el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" style="width:100%">
            <el-option v-for="c in ['客户','交易','产品','设备','公共']" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据类型">
          <el-select v-model="form.dataType" style="width:100%">
            <el-option v-for="t in ['STRING','INT','DECIMAL','DATE','TIMESTAMP']" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="长度/精度"><el-input v-model="form.dataLen" placeholder="32 或 12,2" /></el-form-item>
        <el-form-item label="格式"><el-input v-model="form.fmt" placeholder="如 ^1[3-9]\\d{9}$" /></el-form-item>
        <el-form-item label="业务含义"><el-input v-model="form.businessDesc" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="质量规则"><el-input v-model="form.ruleDesc" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="dialog = false">取消</el-button>
        <el-button size="small" type="primary" @click="create">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { standardApi } from '@/api'
import { useAuthStore } from '@/stores/auth'

const store = useAuthStore()

const tab = ref('element')
const elements = ref([])
const standards = ref([])
const stats = ref({})
const total = ref(0)
const loading = ref(false)
const dialog = ref(false)
const categories = ['客户', '交易', '产品', '设备', '公共']
const query = reactive({ keyword: '', category: '', pageNum: 1, pageSize: 15 })
const form = reactive({ code: '', cnName: '', name: '', category: '公共', dataType: 'STRING', dataLen: '32', fmt: '', businessDesc: '', ruleDesc: '' })

const load = async () => {
  loading.value = true
  try {
    const d = await standardApi.elements({ ...query })
    elements.value = d.records || []
    total.value = d.total || 0
  } catch (e) {
    elements.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const create = async () => {
  if (!form.code || !form.cnName) return ElMessage.warning('编码与中文名必填')
  try {
    await standardApi.createElement({ ...form, industry: 'GOV', source: '企业标准' })
    ElMessage.success('数据元已创建（DRAFT，需发布后生效）')
    dialog.value = false
    load(); loadStats()
  } catch (e) {
    /* http 层已提示 */
  }
}

const publish = async (row) => {
  try {
    await standardApi.publishElement(row.id)
    ElMessage.success('已发布，可用于字段映射与建表引用')
    load(); loadStats()
  } catch (e) {
    /* http 层已提示 */
  }
}

const loadStats = async () => {
  if (!store.has('standard:overview:view')) {
    stats.value = {}
    return
  }
  try {
    stats.value = (await standardApi.stats()) || {}
  } catch (e) {
    stats.value = {}
  }
}

onMounted(() => {
  // 列表为主，统计与标准清单旁路加载，互不阻塞
  load()
  loadStats()
  if (!store.has('standard:standard:view')) {
    standards.value = []
    return
  }
  standardApi
    .standards()
    .then((d) => {
      standards.value = d.records || []
    })
    .catch(() => {
      standards.value = []
    })
})
</script>
