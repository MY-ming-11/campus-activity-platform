import { defineStore } from 'pinia'
import { authApi } from '@/api'

function readUser() {
  try {
    return JSON.parse(localStorage.getItem('user') || 'null')
  } catch {
    return null
  }
}

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    user: readUser()
  }),

  getters: {
    logged: (state) => !!state.token,
    isAdmin: (state) => state.user?.role === 'ADMIN',
    displayName: (state) => state.user?.nickname || state.user?.username || ''
  },

  actions: {
    async login(form) {
      this.saveSession(await authApi.login(form))
    },

    async register(form) {
      this.saveSession(await authApi.register(form))
    },

    saveSession(data) {
      this.token = data.token
      this.user = data.user
      localStorage.setItem('token', data.token)
      localStorage.setItem('user', JSON.stringify(data.user))
    },

    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('token')
      localStorage.removeItem('user')
    }
  }
})
