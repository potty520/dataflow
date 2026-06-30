import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user'

const MainLayout = () => import('../layout/MainLayout.vue')
const Login = () => import('../views/login/index.vue')
const Dashboard = () => import('../views/dashboard/index.vue')

const routes = [
  { path: '/login', component: Login, meta: { public: true } },
  {
    path: '/',
    component: MainLayout,
    redirect: '/dashboard',
    children: [
      { path: 'dashboard', component: Dashboard, meta: { title: '首页' } },
      { path: 'portal/tenant', component: () => import('../views/portal/tenant/index.vue'), meta: { title: '租户管理' } },
      { path: 'portal/user', component: () => import('../views/portal/user/index.vue'), meta: { title: '用户管理' } },
      { path: 'portal/role', component: () => import('../views/portal/role/index.vue'), meta: { title: '角色管理' } },
      { path: 'portal/dept', component: () => import('../views/portal/dept/index.vue'), meta: { title: '部门管理' } },
      { path: 'portal/menu', component: () => import('../views/portal/menu/index.vue'), meta: { title: '菜单管理' } },
      { path: 'portal/log', component: () => import('../views/portal/log/index.vue'), meta: { title: '日志管理' } },
      { path: 'aggregation/datasource', component: () => import('../views/aggregation/datasource/index.vue'), meta: { title: '数据源管理' } },
      { path: 'aggregation/increment', component: () => import('../views/aggregation/increment/index.vue'), meta: { title: '增量字段' } },
      { path: 'aggregation/dataflow', component: () => import('../views/aggregation/dataflow/index.vue'), meta: { title: '数据流管理' } },
      { path: 'aggregation/monitor', component: () => import('../views/aggregation/monitor/index.vue'), meta: { title: '数据流监控' } },
      { path: 'aggregation/fullsync', component: () => import('../views/aggregation/fullsync/index.vue'), meta: { title: '整库同步' } },
      { path: 'aggregation/cdc', component: () => import('../views/aggregation/cdc/index.vue'), meta: { title: 'CDC实时同步' } },
      { path: 'governance/standard', component: () => import('../views/governance/standard/index.vue'), meta: { title: '数据标准' } },
      { path: 'governance/warehouse', component: () => import('../views/governance/warehouse/index.vue'), meta: { title: '仓库分层' } },
      { path: 'governance/modeling', component: () => import('../views/governance/modeling/index.vue'), meta: { title: '数据建模' } },
      { path: 'governance/indicator', component: () => import('../views/governance/indicator/index.vue'), meta: { title: '指标管理' } },
      { path: 'development/workflow', component: () => import('../views/development/workflow/index.vue'), meta: { title: '工作流开发' } },
      { path: 'development/script', component: () => import('../views/development/script/index.vue'), meta: { title: '脚本开发' } },
      { path: 'development/quality', component: () => import('../views/development/quality/index.vue'), meta: { title: '数据质量' } },
      { path: 'development/schedule', component: () => import('../views/development/schedule/index.vue'), meta: { title: '数据调度' } },
      { path: 'service/application', component: () => import('../views/service/application/index.vue'), meta: { title: '应用管理' } },
      { path: 'service/api', component: () => import('../views/service/api/index.vue'), meta: { title: 'API管理' } },
      { path: 'service/catalog', component: () => import('../views/service/catalog/index.vue'), meta: { title: '服务目录' } },
      { path: 'service/workorder', component: () => import('../views/service/workorder/index.vue'), meta: { title: '工单管理' } },
      { path: 'asset/overview', component: () => import('../views/asset/overview/index.vue'), meta: { title: '数据总览' } },
      { path: 'asset/map', component: () => import('../views/asset/map/index.vue'), meta: { title: '数据地图' } },
      { path: 'asset/monitor', component: () => import('../views/asset/monitor/index.vue'), meta: { title: '资产监控' } },
      { path: 'infrastructure/component', component: () => import('../views/infrastructure/component/index.vue'), meta: { title: '组件管理' } },
      { path: 'infrastructure/cluster', component: () => import('../views/infrastructure/cluster/index.vue'), meta: { title: '集群管理' } }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to, from, next) => {
  const store = useUserStore()
  if (to.meta.public) {
    next()
    return
  }
  if (!store.token) {
    next('/login')
    return
  }
  if (!store.user) {
    try {
      await store.fetchInfo()
    } catch (e) {
      store.logout()
      next('/login')
      return
    }
  }
  next()
})

export default router
