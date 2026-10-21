import { defineStore } from 'pinia'
import { authApi } from '@/api'

const ACCESS_KEY = 'dgp_token'
const REFRESH_KEY = 'dgp_refresh_token'
const USER_KEY = 'dgp_user'
const TENANT_KEY = 'dgp_tenant'

const readSession = (key) => {
  try {
    return sessionStorage.getItem(key) || ''
  } catch (e) {
    return ''
  }
}

const writeSession = (key, value) => {
  try {
    sessionStorage.setItem(key, value)
  } catch (e) {
    /* 隐私模式下不可写时忽略 */
  }
}

const removeSession = (key) => {
  try {
    sessionStorage.removeItem(key)
  } catch (e) {
    /* ignore */
  }
}

/** 本地缓存的用户资料可能在磁盘损坏/手改后变成非法 JSON，必须容错解析。 */
function readCachedUser() {
  let raw = null
  try {
    raw = localStorage.getItem(USER_KEY)
  } catch (e) {
    return null
  }
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw)
    return parsed && typeof parsed === 'object' ? parsed : null
  } catch (e) {
    try {
      localStorage.removeItem(USER_KEY)
    } catch (e2) {
      /* ignore */
    }
    return null
  }
}

export const useAuthStore = defineStore('auth', {
  state: () => {
    // 历史版本把令牌放在 localStorage，升级后立即清理，避免长期驻留
    try {
      localStorage.removeItem(ACCESS_KEY)
      localStorage.removeItem(REFRESH_KEY)
    } catch (e) {
      /* ignore */
    }
    return {
      // 令牌只放 sessionStorage：关闭标签页即失效，降低 XSS 持久化风险
      token: readSession(ACCESS_KEY),
      refreshToken: readSession(REFRESH_KEY),
      user: readCachedUser(),
      menus: [],
      loaded: false
    }
  },
  getters: {
    perms: (s) => s.user?.permissions || [],
    roleName: (s) => s.user?.roleName || '-',
    tenantName: (s) => s.user?.tenantName || '-',
    isAdmin: (s) => (s.user?.permissions || []).includes('*') || s.user?.roleCode === 'ROLE_GOV_ADMIN'
  },
  actions: {
    has(perm) {
      if (!perm) return true
      return this.isAdmin || this.perms.includes(perm)
    },
    /** 写入令牌（login / refresh 共用）。 */
    applyTokens(data) {
      if (!data) return
      if (data.accessToken) {
        this.token = data.accessToken
        writeSession(ACCESS_KEY, data.accessToken)
      }
      if (data.refreshToken) {
        this.refreshToken = data.refreshToken
        writeSession(REFRESH_KEY, data.refreshToken)
      }
      if (data.user) this.applyUser(data.user)
      else if (data.userId || data.username) this.applyUser(data)
    },
    applyUser(user) {
      if (!user || typeof user !== 'object') return
      this.user = user
      try {
        localStorage.setItem(USER_KEY, JSON.stringify(user))
      } catch (e) {
        /* ignore */
      }
      try {
        if (user.tenantId) localStorage.setItem(TENANT_KEY, String(user.tenantId))
        else localStorage.removeItem(TENANT_KEY)
      } catch (e) {
        /* ignore */
      }
    },
    async login(form) {
      const data = await authApi.login(form)
      if (!data || !data.accessToken) throw new Error('登录响应缺少访问令牌')
      this.menus = []
      this.loaded = false
      this.applyTokens(data)
      await this.fetchMenus()
      return data
    },
    /** 用 refreshToken 换新令牌；失败时抛出，由调用方决定是否清空会话。 */
    async refresh() {
      if (!this.refreshToken) throw new Error('无刷新令牌')
      const data = await authApi.refresh({ refreshToken: this.refreshToken })
      if (!data || !data.accessToken) throw new Error('刷新登录状态失败')
      this.applyTokens(data)
      return data
    },
    async fetchMenus() {
      try {
        const data = await authApi.menus()
        this.menus = data.menus || []
      } catch (e) {
        // /auth/* 在 http 层不做自动重试；accessToken 过期时在此主动续期后重取一次
        this.menus = []
        if (this.refreshToken) {
          try {
            await this.refresh()
            const data = await authApi.menus()
            this.menus = data.menus || []
          } catch (e2) {
            this.menus = []
          }
        }
      }
      this.loaded = true
      return this.menus
    },
    async refreshMe() {
      const me = await authApi.me()
      this.applyUser(me)
      return me
    },
    /** 清空全部会话痕迹（含租户与历史 localStorage 令牌）。 */
    resetSession() {
      this.token = ''
      this.refreshToken = ''
      this.user = null
      this.menus = []
      this.loaded = false
      removeSession(ACCESS_KEY)
      removeSession(REFRESH_KEY)
      try {
        localStorage.removeItem(ACCESS_KEY)
        localStorage.removeItem(REFRESH_KEY)
        localStorage.removeItem(USER_KEY)
        localStorage.removeItem(TENANT_KEY)
      } catch (e) {
        /* ignore */
      }
    },
    async logout() {
      try {
        await authApi.logout()
      } catch (e) {
        // 令牌可能已失效，本地仍然要清理干净
      }
      this.resetSession()
    }
  }
})
