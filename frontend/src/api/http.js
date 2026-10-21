import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * 统一 HTTP 层。
 * - 请求：注入 Authorization（来自 Pinia 会话状态）+ X-Tenant-Id；
 * - 401：单飞静默续期（/auth/refresh），成功后把原请求重放一次；
 *   续期失败则整体清理会话并跳转登录（每个 401 风暴只跳一次）；
 * - config.silent === true 时不弹错误提示（后台探针 / KPI 旁路调用）；
 * - 响应统一解包 body.data，所有视图依赖该契约。
 */
const http = axios.create({ baseURL: '', timeout: 60000 })

const ACCESS_KEY = 'dgp_token'
const REFRESH_KEY = 'dgp_refresh_token'
const TENANT_KEY = 'dgp_tenant'

const readAccess = () => {
  try {
    return sessionStorage.getItem(ACCESS_KEY) || localStorage.getItem(ACCESS_KEY) || ''
  } catch (e) {
    return ''
  }
}

const readRefresh = () => {
  try {
    return sessionStorage.getItem(REFRESH_KEY) || localStorage.getItem(REFRESH_KEY) || ''
  } catch (e) {
    return ''
  }
}

/** /auth/* 属于会话凭据接口，http 层绝不重试它们（否则会自我递归）。 */
const isAuthUrl = (url = '') => String(url).includes('/api/v1/auth/')

let refreshPromise = null
let expiring = false

function toastError(message, config) {
  if (config && config.silent) return
  ElMessage.error(message)
}

function bizError(message, code, config) {
  const err = new Error(message || '请求失败')
  err.code = code
  err.__toasted = !(config && config.silent)
  return err
}

http.interceptors.request.use(async (cfg) => {
  if (!cfg.headers) cfg.headers = {}
  let token = ''
  try {
    // 懒加载 store，避免 http -> store -> api -> http 的静态循环依赖
    const { useAuthStore } = await import('@/stores/auth')
    token = useAuthStore().token || ''
  } catch (e) {
    token = ''
  }
  if (!token) token = readAccess()
  if (token) cfg.headers.Authorization = `Bearer ${token}`
  let tenant = ''
  try {
    tenant = localStorage.getItem(TENANT_KEY) || sessionStorage.getItem(TENANT_KEY) || ''
  } catch (e) {
    tenant = ''
  }
  cfg.headers['X-Tenant-Id'] = tenant || '1'
  return cfg
})

/** 单飞续期：并发 401 共用同一个 refresh Promise，最多续期一次。 */
function ensureRefresh() {
  if (!refreshPromise) {
    refreshPromise = performRefresh().finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}

async function performRefresh() {
  const refreshToken = readRefresh()
  if (!refreshToken) throw new Error('无刷新令牌')
  // 直接用裸 axios，绕开拦截器，避免续期请求再次进入 401 处理
  const res = await axios.post('/api/v1/auth/refresh', { refreshToken }, { timeout: 20000 })
  const body = res.data
  const data = body && typeof body.data !== 'undefined' ? body.data : body
  if (!data || !data.accessToken) {
    throw new Error((body && body.message) || '刷新登录状态失败')
  }
  try {
    const { useAuthStore } = await import('@/stores/auth')
    useAuthStore().applyTokens(data)
  } catch (e) {
    // store 尚未就绪时退化为直接落盘
    try {
      sessionStorage.setItem(ACCESS_KEY, data.accessToken)
      if (data.refreshToken) sessionStorage.setItem(REFRESH_KEY, data.refreshToken)
    } catch (e2) {
      /* 存储不可用时忽略 */
    }
  }
  return data
}

function clearStoredSession() {
  try {
    sessionStorage.removeItem(ACCESS_KEY)
    sessionStorage.removeItem(REFRESH_KEY)
    localStorage.removeItem(ACCESS_KEY)
    localStorage.removeItem(REFRESH_KEY)
    localStorage.removeItem('dgp_user')
    localStorage.removeItem(TENANT_KEY)
  } catch (e) {
    /* 存储不可用时忽略 */
  }
}

/** 会话彻底失效：清空本地状态并跳登录页，一次 401 风暴内只执行一次。 */
async function expireSession() {
  if (expiring) return
  expiring = true
  try {
    const [{ default: router }, { useAuthStore }] = await Promise.all([
      import('@/router'),
      import('@/stores/auth')
    ])
    const current = router.currentRoute.value
    const from = current.fullPath || current.path || '/'
    useAuthStore().resetSession()
    if (current.path !== '/login') {
      await router.replace({ path: '/login', query: from && from !== '/' ? { redirect: from } : {} })
      ElMessage.warning('登录状态已失效，请重新登录')
    }
  } catch (e) {
    clearStoredSession()
    if (!location.hash.startsWith('#/login')) location.hash = '#/login'
  } finally {
    // 防抖窗口：同一次批量 401 内不重复跳转/提示
    setTimeout(() => {
      expiring = false
    }, 1500)
  }
}

http.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && typeof body.code !== 'undefined' && body.code !== 0) {
      const msg = body.message || '请求失败'
      toastError(`${msg}（${body.code}）`, res.config)
      return Promise.reject(bizError(msg, body.code, res.config))
    }
    return body && typeof body.data !== 'undefined' ? body.data : body
  },
  async (err) => {
    const cfg = err.config || {}
    const status = err.response && err.response.status
    const body = err.response && err.response.data
    const message = (body && body.message) || err.message || '网络异常'

    // 0) 网络层失败（无 HTTP 响应，如公网中间设备偶发截断分块响应）且为幂等 GET：自动重试一次
    const isNetwork = !err.response
    const method = String(cfg.method || 'get').toLowerCase()
    if (isNetwork && !cfg.__netRetried && method === 'get' && !isAuthUrl(cfg.url)) {
      cfg.__netRetried = true
      await new Promise((resolve) => setTimeout(resolve, 300))
      return http(cfg)
    }

    if (status !== 401) {
      toastError(message, cfg)
      err.__toasted = !cfg.silent
      return Promise.reject(err)
    }

    // 1) 凭据类接口自身的 401（登录失败 / 续期失败）：不续期、不重试
    if (isAuthUrl(cfg.url)) {
      toastError(message, cfg)
      err.__toasted = !cfg.silent
      return Promise.reject(err)
    }

    // 2) 已经重放过一次仍然 401：判定会话失效
    if (cfg.__retried) {
      await expireSession()
      err.__toasted = true
      return Promise.reject(err)
    }

    // 3) 静默续期，成功后把原请求重放一次（不重放 /auth/*）
    let data
    try {
      data = await ensureRefresh()
    } catch (e) {
      await expireSession()
      err.__toasted = true
      return Promise.reject(err)
    }

    cfg.__retried = true
    const token = (data && data.accessToken) || readAccess()
    if (cfg.headers) {
      if (typeof cfg.headers.set === 'function') cfg.headers.set('Authorization', `Bearer ${token}`)
      else cfg.headers.Authorization = `Bearer ${token}`
    }
    return http(cfg)
  }
)

export default http
