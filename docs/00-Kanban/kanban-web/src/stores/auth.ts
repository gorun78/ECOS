import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { UserVO } from '@/types/user'
import { login as loginApi, fetchCurrentUser } from '@/api/users'
import type { LoginReq } from '@/types/api'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem('kanban_token') || '')
  const currentUser = ref<UserVO | null>(null)
  const loading = ref(false)

  const isLoggedIn = computed(() => !!token.value && !!currentUser.value)
  const isSysAdmin = computed(() => currentUser.value?.role === 'sys_admin')

  async function doLogin(data: LoginReq) {
    loading.value = true
    try {
      const res = await loginApi(data)
      if (res.code === 200) {
        token.value = res.data.token
        currentUser.value = res.data.user
        localStorage.setItem('kanban_token', res.data.token)
        localStorage.setItem('kanban_user', JSON.stringify(res.data.user))
        return true
      }
      return false
    } finally {
      loading.value = false
    }
  }

  async function restoreSession() {
    const savedUser = localStorage.getItem('kanban_user')
    const savedToken = localStorage.getItem('kanban_token')
    if (savedUser && savedToken) {
      currentUser.value = JSON.parse(savedUser)
      token.value = savedToken
      return true
    }
    return false
  }

  function logout() {
    token.value = ''
    currentUser.value = null
    localStorage.removeItem('kanban_token')
    localStorage.removeItem('kanban_user')
  }

  return { token, currentUser, loading, isLoggedIn, isSysAdmin, doLogin, restoreSession, logout }
})
