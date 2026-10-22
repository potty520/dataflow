import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'

const routes = [
  { path: '/login', component: () => import('@/views/Login.vue'), meta: { public: true, title: '登录' } },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/overview',
    children: [
      { path: 'overview', component: () => import('@/views/Overview.vue'), meta: { title: '治理概览', perm: 'metadata:overview:view' } },
      { path: 'metadata', component: () => import('@/views/Metadata.vue'), meta: { title: '元数据管理', perm: 'metadata:table:view', tab: 'catalog' } },
      { path: 'metadata/datasources', component: () => import('@/views/Metadata.vue'), meta: { title: '数据源管理', perm: 'metadata:datasource:view', tab: 'datasource' } },
      { path: 'metadata/collect', component: () => import('@/views/Metadata.vue'), meta: { title: '采集任务', perm: 'metadata:collect:view', tab: 'collect' } },
      { path: 'lineage', component: () => import('@/views/Lineage.vue'), meta: { title: '血缘分析', perm: 'metadata:lineage:view' } },
      { path: 'model', component: () => import('@/views/Model.vue'), meta: { title: '数据建模', perm: 'model:model:view' } },
      { path: 'standard', component: () => import('@/views/Standard.vue'), meta: { title: '数据标准', perm: 'standard:element:view', tab: 'element' } },
      { path: 'standard/mapping', component: () => import('@/views/StandardMapping.vue'), meta: { title: '标准映射与落地', perm: 'standard:mapping:view' } },
      { path: 'integration', component: () => import('@/views/Integration.vue'), meta: { title: '数据集成', perm: 'integration:job:view' } },
      { path: 'workflow', component: () => import('@/views/Workflow.vue'), meta: { title: '调度与工作流', perm: 'workflow:definition:view' } },
      { path: 'quality', component: () => import('@/views/Quality.vue'), meta: { title: '数据质量', perm: 'quality:rule:view' } },
      { path: 'quality/stream', component: () => import('@/views/StreamQuality.vue'), meta: { title: '流式质检', perm: 'quality:stream:view' } },
      { path: 'quality/issues', component: () => import('@/views/Issues.vue'), meta: { title: '问题闭环', perm: 'quality:issue:view' } },
      { path: 'quality/reports', component: () => import('@/views/Reports.vue'), meta: { title: '质量报告', perm: 'quality:report:view' } },
      { path: 'security', component: () => import('@/views/Security.vue'), meta: { title: '数据安全', perm: 'security:mask:view' } },
      { path: 'security/audit', component: () => import('@/views/Audit.vue'), meta: { title: '审计与告警', perm: 'security:audit:view' } },
      { path: 'asset', component: () => import('@/views/Asset.vue'), meta: { title: '数据资产', perm: 'asset:catalog:view' } },
      { path: 'asset/map', component: () => import('@/views/AssetMap.vue'), meta: { title: '资产地图', perm: 'asset:map:view' } },
      { path: 'service', component: () => import('@/views/Service.vue'), meta: { title: '数据服务', perm: 'service:api:view' } },
      { path: 'migration', component: () => import('@/views/Migration.vue'), meta: { title: '数据迁移', perm: 'migration:project:view' } },
      { path: 'migration/tools', component: () => import('@/views/MigrationTools.vue'), meta: { title: '远程与文件迁移', perm: 'migration:file:view' } },
      { path: 'industry', component: () => import('@/views/Industry.vue'), meta: { title: '行业适配', perm: 'industry:package:view' } },
      { path: 'admin', component: () => import('@/views/Admin.vue'), meta: { title: '系统管理', perm: 'sys:config:view' } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/overview' }
]

const router = createRouter({ history: createWebHashHistory(), routes })

/** 取某路径对应的路由元信息（用于二次校验权限）。 */
const metaOf = (path) => {
  try {
    return router.resolve(path).meta || {}
  } catch (e) {
    return {}
  }
}

/**
 * 计算"当前用户真正有权限进入"的第一个页面。
 * 后端 /auth/menus 已按权限过滤，因此优先采用菜单顺序；路由表作为菜单加载失败时的兜底。
 * 结果绝不返回被拒绝的路径，也绝不返回 toPath 自身（避免二次重定向死循环）。
 */
function firstAllowedPath(store, toPath) {
  const menuPaths = []
  for (const m of store.menus || []) {
    if (m && typeof m.path === 'string' && m.path.startsWith('/')) menuPaths.push(m.path)
  }
  const routePaths = router
    .getRoutes()
    .filter((r) => r && typeof r.path === 'string' && r.path.startsWith('/') && r.path !== '/')
    .filter((r) => r.meta && r.meta.title && !r.meta.public && r.components && Object.keys(r.components || {}).length)
    .map((r) => r.path)
  const candidates = [...new Set([...menuPaths, ...routePaths])]
  for (const p of candidates) {
    if (p === toPath) continue
    const meta = metaOf(p)
    if (!meta || meta.public) continue
    if (meta.perm && !store.has(meta.perm)) continue
    return p
  }
  return '/login'
}

// 同一次导航（含重定向链）只提示一次，避免 toast 风暴
let deniedWarnedFor = ''

router.beforeEach(async (to, from) => {
  const store = useAuthStore()
  if (to.meta.public) {
    deniedWarnedFor = ''
    return true
  }
  if (!store.token) {
    return to.path && to.path !== '/'
      ? { path: '/login', query: { redirect: to.fullPath } }
      : { path: '/login' }
  }
  if (to.meta.perm && !store.has(to.meta.perm)) {
    // 需要菜单列表才能算出可访问目标，首次进入时先加载一次
    if (!store.loaded) {
      try {
        await store.fetchMenus()
      } catch (e) {
        /* 菜单加载失败不阻塞导航 */
      }
    }
    // 登录后自动跳到首个可访问页时不再提示（避免"登录成功"后紧跟一条无权限警告）
    if (deniedWarnedFor !== to.path && from.path !== '/login') {
      ElMessage.warning('当前角色无该功能权限')
      deniedWarnedFor = to.path
    }
    const target = firstAllowedPath(store, to.path)
    // 找不到可用目标，或目标就是当前路径：放行，绝不再重定向
    if (!target || target === to.path) return true
    return { path: target, replace: true }
  }
  deniedWarnedFor = ''
  return true
})

export default router
