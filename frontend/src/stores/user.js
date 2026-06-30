import { defineStore } from 'pinia'
import { getUserInfo, login as loginApi } from '../api'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    user: null,
    roles: [],
    menus: []
  }),
  actions: {
    async login(form) {
      const res = await loginApi(form)
      this.token = res.data.token
      localStorage.setItem('token', this.token)
      return res.data
    },
    async fetchInfo() {
      const res = await getUserInfo()
      this.user = res.data.user
      this.roles = res.data.roles || []
      this.menus = res.data.menus || []
    },
    logout() {
      this.token = ''
      this.user = null
      this.roles = []
      this.menus = []
      localStorage.removeItem('token')
    }
  }
})
