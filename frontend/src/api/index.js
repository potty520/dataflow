import http from './http'

export const authApi = {
  login: (data) => http.post('/api/v1/auth/login', data),
  refresh: (data) => http.post('/api/v1/auth/refresh', data),
  // 会话引导类探针失败时由 store 自行兜底，不打扰用户
  me: () => http.get('/api/v1/auth/me', { silent: true }),
  menus: () => http.get('/api/v1/auth/menus', { silent: true }),
  logout: () => http.post('/api/v1/auth/logout'),
  users: (params) => http.get('/api/v1/auth/users', { params }),
  // 责任人下拉：任意登录用户可用，只返回最小字段
  userOptions: () => http.get('/api/v1/auth/users/options'),
  createUser: (data) => http.post('/api/v1/auth/users', data),
  updateUser: (id, data) => http.put(`/api/v1/auth/users/${id}`, data),
  deleteUser: (id) => http.delete(`/api/v1/auth/users/${id}`),
  roles: () => http.get('/api/v1/auth/roles'),
  assignRole: (id, roleCode) => http.post(`/api/v1/auth/users/${id}/roles`, { roleCode }),
  resetPassword: (id, password) => http.put(`/api/v1/auth/users/${id}/password`, { password }),
  createRole: (data) => http.post('/api/v1/auth/roles', data),
  updateRole: (id, data) => http.put(`/api/v1/auth/roles/${id}`, data),
  deleteRole: (id) => http.delete(`/api/v1/auth/roles/${id}`),
  grantRolePerms: (id, perms) => http.post(`/api/v1/auth/roles/${id}/permissions`, { perms }),
  tenants: () => http.get('/api/v1/auth/tenants/tree'),
  dataScopes: () => http.get('/api/v1/auth/data-scopes'),
  changePassword: (data) => http.put('/api/v1/auth/users/0/password', data),
  // 本人改密：必须携带 oldPassword / newPassword
  changeOwnPassword: (data) => http.put('/api/v1/auth/password', data)
}

export const metadataApi = {
  datasources: (params) => http.get('/api/v1/metadata/datasources', { params }),
  createDatasource: (data) => http.post('/api/v1/metadata/datasources', data),
  updateDatasource: (id, data) => http.put(`/api/v1/metadata/datasources/${id}`, data),
  testDatasource: (id) => http.post(`/api/v1/metadata/datasources/${id}/test`),
  search: (params) => http.get('/api/v1/metadata/search', { params }),
  tables: (params) => http.get('/api/v1/metadata/tables', { params }),
  table: (id) => http.get(`/api/v1/metadata/tables/${id}`),
  updateTable: (id, data) => http.put(`/api/v1/metadata/tables/${id}`, data),
  updateColumn: (id, data) => http.put(`/api/v1/metadata/columns/${id}`, data),
  lineage: (params) => http.get('/api/v1/metadata/lineage/graph', { params }),
  impact: (params) => http.get('/api/v1/metadata/lineage/impact', { params }),
  parseSql: (data) => http.post('/api/v1/metadata/lineage/parse', data),
  collectTasks: () => http.get('/api/v1/metadata/collect/tasks'),
  runCollect: (id) => http.post(`/api/v1/metadata/collect/tasks/${id}/run`),
  collectInstances: (params) => http.get('/api/v1/metadata/collect/instances', { params }),
  versions: (params) => http.get('/api/v1/metadata/versions', { params }),
  importMeta: (data) => http.post('/api/v1/metadata/import', data),
  terminology: (params) => http.get('/api/v1/metadata/terminology', { params }),
  stats: () => http.get('/api/v1/metadata/stats/overview', { silent: true })
}

export const modelApi = {
  list: (params) => http.get('/api/v1/model/models', { params }),
  detail: (id) => http.get(`/api/v1/model/models/${id}`),
  create: (data) => http.post('/api/v1/model/models', data),
  update: (id, data) => http.put(`/api/v1/model/models/${id}`, data),
  publish: (id, data) => http.post(`/api/v1/model/models/${id}/publish`, data || {}),
  approve: (id, data) => http.post(`/api/v1/model/models/${id}/approve`, data || {}),
  versions: (id) => http.get(`/api/v1/model/models/${id}/versions`),
  impact: (id) => http.get(`/api/v1/model/models/${id}/impact`),
  ddl: (id) => http.post(`/api/v1/model/models/${id}/ddl`),
  executeDdl: (entityId) => http.post(`/api/v1/model/entities/${entityId}/ddl/execute`),
  templates: () => http.get('/api/v1/model/templates', { silent: true }),
  stats: () => http.get('/api/v1/model/stats/overview', { silent: true })
}

export const standardApi = {
  elements: (params) => http.get('/api/v1/standard/elements', { params }),
  createElement: (data) => http.post('/api/v1/standard/elements', data),
  publishElement: (id) => http.post(`/api/v1/standard/elements/${id}/publish`),
  standards: () => http.get('/api/v1/standard/standards'),
  mappings: (params) => http.get('/api/v1/standard/mappings', { params }),
  recommend: (params) => http.get('/api/v1/standard/mappings/recommend', { params }),
  batchMapping: (data) => http.post('/api/v1/standard/mappings/batch', data),
  compliance: (data) => http.post('/api/v1/standard/compliance/check', data || {}),
  complianceSummary: () => http.get('/api/v1/standard/compliance/summary'),
  stats: () => http.get('/api/v1/standard/stats/overview', { silent: true })
}

export const integrationApi = {
  jobs: (params) => http.get('/api/v1/integration/jobs', { params }),
  job: (id) => http.get(`/api/v1/integration/jobs/${id}`),
  createJob: (data) => http.post('/api/v1/integration/jobs', data),
  updateJob: (id, data) => http.put(`/api/v1/integration/jobs/${id}`, data),
  publishJob: (id) => http.post(`/api/v1/integration/jobs/${id}/publish`),
  runJob: (id) => http.post(`/api/v1/integration/jobs/${id}/run`),
  logs: (id) => http.get(`/api/v1/integration/jobs/${id}/logs`),
  instances: (id, params) => http.get(`/api/v1/integration/jobs/${id}/instances`, { params }),
  compile: (id) => http.post(`/api/v1/integration/jobs/${id}/compile`),
  dryRun: (id, data) => http.post(`/api/v1/integration/jobs/${id}/dry-run`, data || {}),
  toScript: (id) => http.post(`/api/v1/integration/jobs/${id}/dsl:toScript`),
  fromScript: (id) => http.post(`/api/v1/integration/jobs/${id}/dsl:fromScript`),
  formatSql: (sql) => http.post('/api/v1/integration/sql/format', { sql }),
  validateSql: (sql) => http.post('/api/v1/integration/sql/validate', { sql }),
  adhoc: (sql) => http.post('/api/v1/integration/adhoc/query', { sql }),
  sandbox: () => http.get('/api/v1/integration/sandbox/policies'),
  datasources: () => http.get('/api/v1/integration/datasources'),
  testDatasource: (id) => http.post(`/api/v1/integration/datasources/${id}/test`),
  stats: () => http.get('/api/v1/integration/stats/overview', { silent: true })
}

export const workflowApi = {
  definitions: () => http.get('/api/v1/workflow/definitions'),
  definition: (id) => http.get(`/api/v1/workflow/definitions/${id}`),
  online: (id) => http.post(`/api/v1/workflow/definitions/${id}/online`),
  offline: (id) => http.post(`/api/v1/workflow/definitions/${id}/offline`),
  trigger: (id) => http.post(`/api/v1/workflow/definitions/${id}/trigger`),
  instances: (params) => http.get('/api/v1/workflow/instances', { params }),
  instance: (id) => http.get(`/api/v1/workflow/instances/${id}`),
  retry: (id) => http.post(`/api/v1/workflow/instances/${id}/retry`),
  kill: (id) => http.post(`/api/v1/workflow/instances/${id}/kill`),
  stats: () => http.get('/api/v1/workflow/stats/overview', { silent: true })
}

export const qualityApi = {
  rules: (params) => http.get('/api/v1/quality/rules', { params }),
  createRule: (data) => http.post('/api/v1/quality/rules', data),
  updateRule: (id, data) => http.put(`/api/v1/quality/rules/${id}`, data),
  deleteRule: (id) => http.delete(`/api/v1/quality/rules/${id}`),
  tryRun: (id) => http.post(`/api/v1/quality/rules/${id}/try-run`),
  templates: (params) => http.get('/api/v1/quality/rules/templates', { params }),
  batchApply: (data) => http.post('/api/v1/quality/rules/batch-apply', data),
  checks: () => http.get('/api/v1/quality/checks', { silent: true }),
  runCheck: (id) => http.post(`/api/v1/quality/checks/${id}/run`),
  checkInstances: (id) => http.get(`/api/v1/quality/checks/${id}/instances`),
  scores: (params) => http.get('/api/v1/quality/scores', { params, silent: true }),
  saveWeights: (data) => http.put('/api/v1/quality/scores/weights', data),
  coverage: () => http.get('/api/v1/quality/metrics/rule-coverage', { silent: true }),
  issues: (params) => http.get('/api/v1/quality/issues', { params }),
  issue: (id) => http.get(`/api/v1/quality/issues/${id}`),
  dispatch: (id, data) => http.post(`/api/v1/quality/issues/${id}/dispatch`, data),
  handle: (id, data) => http.post(`/api/v1/quality/issues/${id}/handle`, data),
  review: (id, data) => http.post(`/api/v1/quality/issues/${id}/review`, data),
  issueMetrics: () => http.get('/api/v1/quality/issues/metrics', { silent: true }),
  byAssignee: () => http.get('/api/v1/quality/issues/metrics/by-assignee', { silent: true }),
  reports: (params) => http.get('/api/v1/quality/reports', { params }),
  report: (id) => http.get(`/api/v1/quality/reports/${id}`),
  generateReport: (data) => http.post('/api/v1/quality/reports', data || {}),
  exportReport: (id, format) => http.post(`/api/v1/quality/reports/${id}/export`, { format }),
  stats: () => http.get('/api/v1/quality/stats/overview', { silent: true })
}

export const streamApi = {
  overview: () => http.get('/api/v1/quality/stream/overview', { silent: true }),
  health: () => http.get('/api/v1/quality/stream/health', { silent: true }),
  tables: () => http.get('/api/v1/quality/stream/tables'),
  columns: (table) => http.get('/api/v1/quality/stream/columns', { params: { table } }),
  sources: (params) => http.get('/api/v1/quality/stream/sources', { params }),
  createSource: (data) => http.post('/api/v1/quality/stream/sources', data),
  updateSource: (id, data) => http.put(`/api/v1/quality/stream/sources/${id}`, data),
  deleteSource: (id) => http.delete(`/api/v1/quality/stream/sources/${id}`),
  runSource: (id) => http.post(`/api/v1/quality/stream/sources/${id}/run`),
  runAll: () => http.post('/api/v1/quality/stream/sources/run-all'),
  resetWatermark: (id) => http.post(`/api/v1/quality/stream/sources/${id}/reset-watermark`),
  rules: (params) => http.get('/api/v1/quality/stream/rules', { params }),
  createRule: (data) => http.post('/api/v1/quality/stream/rules', data),
  updateRule: (id, data) => http.put(`/api/v1/quality/stream/rules/${id}`, data),
  deleteRule: (id) => http.delete(`/api/v1/quality/stream/rules/${id}`),
  results: (params) => http.get('/api/v1/quality/stream/results', { params }),
  alerts: (params) => http.get('/api/v1/quality/stream/alerts', { params }),
  handleAlert: (id) => http.post(`/api/v1/quality/stream/alerts/${id}/handle`),
  pushConfig: (id) => http.get(`/api/v1/quality/stream/sources/${id}/push-config`),
  savePushConfig: (id, data) => http.put(`/api/v1/quality/stream/sources/${id}/push-config`, data),
  pushEvent: (code, data) => http.post(`/api/v1/quality/stream/push/${code}`, data)
}

export const migrationApi = {
  overview: () => http.get('/api/v1/migration/overview', { silent: true }),
  tables: (params) => http.get('/api/v1/migration/tables', { params }),
  columns: (table) => http.get(`/api/v1/migration/tables/${table}/columns`),
  projects: (params) => http.get('/api/v1/migration/projects', { params }),
  project: (id) => http.get(`/api/v1/migration/projects/${id}`),
  createProject: (data) => http.post('/api/v1/migration/projects', data),
  updateProject: (id, data) => http.put(`/api/v1/migration/projects/${id}`, data),
  deleteProject: (id) => http.delete(`/api/v1/migration/projects/${id}`),
  mappings: (id) => http.get(`/api/v1/migration/projects/${id}/mappings`),
  recommend: (id) => http.get(`/api/v1/migration/projects/${id}/recommend`),
  saveMappings: (id, mappings) => http.post(`/api/v1/migration/projects/${id}/mappings`, { mappings }),
  saveChecks: (id, checks) => http.post(`/api/v1/migration/projects/${id}/checks`, { checks }),
  precheck: (id) => http.post(`/api/v1/migration/projects/${id}/precheck`),
  createTarget: (id) => http.post(`/api/v1/migration/projects/${id}/create-target`),
  statement: (id) => http.get(`/api/v1/migration/projects/${id}/statement`),
  run: (id, replace) => http.post(`/api/v1/migration/projects/${id}/run`, { replace: replace ? 1 : 0 }),
  runs: (id, params) => http.get(`/api/v1/migration/projects/${id}/runs`, { params }),
  runDetail: (runId) => http.get(`/api/v1/migration/runs/${runId}`),
  reconcile: (runId) => http.post(`/api/v1/migration/runs/${runId}/reconcile`),
  diffs: (runId) => http.get(`/api/v1/migration/runs/${runId}/diffs`),
  rollbackRun: (runId) => http.post(`/api/v1/migration/runs/${runId}/rollback`),
  // ---- 远程数据源（跨网络）+ FTP 文件迁移（v1.4）----
  remoteDsList: (params) => http.get('/api/v1/migration/datasources', { params }),
  createRemoteDs: (data) => http.post('/api/v1/migration/datasources', data),
  updateRemoteDs: (id, data) => http.put(`/api/v1/migration/datasources/${id}`, data),
  deleteRemoteDs: (id) => http.delete(`/api/v1/migration/datasources/${id}`),
  testRemoteDs: (id) => http.post(`/api/v1/migration/datasources/${id}/test`),
  testRemoteDsAdhoc: (data) => http.post('/api/v1/migration/datasources/test', data),
  remoteDsColumns: (id, table) => http.get(`/api/v1/migration/datasources/${id}/columns`, { params: { table } }),
  fileTasks: (params) => http.get('/api/v1/migration/files/tasks', { params }),
  createFileTask: (data) => http.post('/api/v1/migration/files/tasks', data),
  updateFileTask: (id, data) => http.put(`/api/v1/migration/files/tasks/${id}`, data),
  deleteFileTask: (id) => http.delete(`/api/v1/migration/files/tasks/${id}`),
  runFileTask: (id) => http.post(`/api/v1/migration/files/tasks/${id}/run`),
  fileTaskRuns: (id) => http.get(`/api/v1/migration/files/tasks/${id}/runs`),
  testFtp: (data) => http.post('/api/v1/migration/files/test', data),
  // ---- 聚合汇总（v1.5）----
  aggList: (params) => http.get('/api/v1/migration/agg', { params }),
  aggDetail: (id) => http.get(`/api/v1/migration/agg/${id}`),
  aggCreate: (data) => http.post('/api/v1/migration/agg', data),
  aggUpdate: (id, data) => http.put(`/api/v1/migration/agg/${id}`, data),
  aggDelete: (id) => http.delete(`/api/v1/migration/agg/${id}`),
  aggPreview: (id) => http.post(`/api/v1/migration/agg/${id}/preview`),
  aggBuild: (id) => http.post(`/api/v1/migration/agg/${id}/build`),
  aggRefresh: (id) => http.post(`/api/v1/migration/agg/${id}/refresh`),
  aggRuns: (id) => http.get(`/api/v1/migration/agg/${id}/runs`),
  aggEnable: (id, enabled) => http.post(`/api/v1/migration/agg/${id}/enable`, { enabled }),
}
export const securityApi = {
  sensitiveRules: () => http.get('/api/v1/security/sensitive/rules'),
  scan: (data) => http.post('/api/v1/security/sensitive/scan', data || {}),
  sensitiveResults: (params) => http.get('/api/v1/security/sensitive/results', { params }),
  maskPolicies: (params) => http.get('/api/v1/security/mask/policies', { params }),
  createPolicy: (data) => http.post('/api/v1/security/mask/policies', data),
  approvePolicy: (id, data) => http.post(`/api/v1/security/mask/policies/${id}/approve`, data || {}),
  // 真实启停开关：status 在 ENABLED / DISABLED 之间切换
  enablePolicy: (id, enabled) => http.post(`/api/v1/security/mask/policies/${id}/enable`, { enabled: !!enabled }),
  previewMask: (data) => http.post('/api/v1/security/mask/preview', data),
  applies: (params) => http.get('/api/v1/security/access-applies', { params }),
  approveApply: (id, data) => http.post(`/api/v1/security/access-applies/${id}/approve`, data || {}),
  grants: () => http.get('/api/v1/security/access-grants'),
  auditLogs: (params) => http.get('/api/v1/security/audit/logs', { params }),
  verifyChain: () => http.post('/api/v1/security/audit/verify-chain', {}),
  alerts: (params) => http.get('/api/v1/security/alerts', { params, silent: true }),
  alertRules: () => http.get('/api/v1/security/alert-rules', { silent: true }),
  handleAlert: (id, data) => http.post(`/api/v1/security/alerts/${id}/handle`, data || {}),
  stats: () => http.get('/api/v1/security/stats/overview', { silent: true })
}

export const assetApi = {
  search: (params) => http.get('/api/v1/asset/catalog/search', { params }),
  detail: (id) => http.get(`/api/v1/asset/catalog/${id}`),
  categories: () => http.get('/api/v1/asset/categories/tree', { silent: true }),
  map: () => http.get('/api/v1/asset/map/overview'),
  distribution: () => http.get('/api/v1/asset/map/distribution', { silent: true }),
  metrics: () => http.get('/api/v1/asset/metrics', { silent: true }),
  trend: (params) => http.get('/api/v1/asset/metrics/trend', { params, silent: true }),
  valueBasis: () => http.get('/api/v1/asset/value-basis', { silent: true }),
  statsOverview: () => http.get('/api/v1/asset/stats/overview', { silent: true }),
  favorite: (id) => http.post(`/api/v1/asset/favorites/${id}`)
}

export const serviceApi = {
  list: (params) => http.get('/api/v1/service/apis', { params }),
  detail: (id) => http.get(`/api/v1/service/apis/${id}`),
  create: (data) => http.post('/api/v1/service/apis', data),
  update: (id, data) => http.put(`/api/v1/service/apis/${id}`, data),
  test: (id, params) => http.post(`/api/v1/service/apis/${id}/test`, { params }),
  submit: (id) => http.post(`/api/v1/service/apis/${id}/submit`),
  audit: (id, data) => http.post(`/api/v1/service/apis/${id}/audit`, data || {}),
  publish: (id) => http.post(`/api/v1/service/apis/${id}/publish`),
  offline: (id) => http.post(`/api/v1/service/apis/${id}/offline`, {}),
  apps: () => http.get('/api/v1/service/apps', { silent: true }),
  createApp: (data) => http.post('/api/v1/service/apps', data),
  updateApp: (id, data) => http.put(`/api/v1/service/apps/${id}`, data),
  deleteApp: (id) => http.delete(`/api/v1/service/apps/${id}`),
  resetAppSecret: (id) => http.post(`/api/v1/service/apps/${id}/reset-secret`),
  quota: () => http.get('/api/v1/service/quota'),
  blacklist: () => http.get('/api/v1/service/blacklist'),
  orchestrations: () => http.get('/api/v1/service/orchestrations', { silent: true }),
  runOrchestration: (id) => http.post(`/api/v1/service/orchestrations/${id}/run`),
  metrics: (params) => http.get('/api/v1/service/metrics', { params, silent: true }),
  topn: (params) => http.get('/api/v1/service/metrics/topn', { params, silent: true }),
  stats: () => http.get('/api/v1/service/stats/overview', { silent: true })
}

export const industryApi = {
  packages: (params) => http.get('/api/v1/industry/packages', { params }),
  inspect: (data) => http.post('/api/v1/industry/packages/inspect', data),
  importPackage: (data) => http.post('/api/v1/industry/packages/import', data),
  exportPackage: (code) => http.get(`/api/v1/industry/packages/${code}/export`),
  instantiate: (code) => http.post(`/api/v1/industry/packages/${code}/instantiate`),
  enable: (code) => http.post(`/api/v1/industry/packages/${code}/enable`, {}),
  disable: (code) => http.post(`/api/v1/industry/packages/${code}/disable`),
  terminology: (params) => http.get('/api/v1/industry/terminology', { params }),
  lineageRules: () => http.get('/api/v1/industry/lineage-rules'),
  diff: () => http.get('/api/v1/industry/instances/diff'),
  stats: () => http.get('/api/v1/industry/stats/overview', { silent: true })
}

export const adminApi = {
  // 免权限探针：仅判断后端可达性，供顶栏健康指示灯使用
  ping: () => http.get('/api/v1/admin/ping', { silent: true }),
  health: () => http.get('/api/v1/admin/health'),
  services: () => http.get('/api/v1/admin/monitor/services'),
  configs: () => http.get('/api/v1/admin/configs'),
  saveConfig: (key, value) => http.put(`/api/v1/admin/configs/${key}`, { configValue: value }),
  dicts: () => http.get('/api/v1/admin/dicts'),
  apis: () => http.get('/api/v1/admin/apis'),
  loginLogs: (params) => http.get('/api/v1/admin/logs/login', { params }),
  operationLogs: (params) => http.get('/api/v1/admin/logs/operation', { params })
}

export const overviewApi = {
  dashboard: () => http.get('/api/v1/overview/dashboard')
}

export default { authApi, metadataApi, modelApi, standardApi, integrationApi, workflowApi, qualityApi, securityApi, assetApi, serviceApi, industryApi, adminApi, overviewApi }

export const reportApi = {
  templates: () => http.get('/api/v1/report/templates'),
  generate: (data) => http.post('/api/v1/report/generate', data),
  task: (taskId) => http.get(`/api/v1/report/tasks/${taskId}`),
  files: (taskId) => http.get(`/api/v1/report/tasks/${taskId}/files`)
}
