/**
 * 用户 & 认证 API 层（Mock先行）
 */
import { mockRequest, apiRequest } from './request'
import { MOCK_USERS } from './mock/data'
import type { UserVO } from '@/types/user'
import type { LoginReq, LoginResult } from '@/types/api'

const USE_MOCK = true

/** 登录 */
export function login(data: LoginReq) {
  if (USE_MOCK) {
    return mockRequest(() => {
      const user = MOCK_USERS.find(u => u.username === data.username && u.status === 1)
      if (!user) throw new Error('用户名或密码错误')
      return {
        token: `mock-token-${user.id}-${Date.now()}`,
        user,
      } as LoginResult
    })
  }
  return apiRequest<LoginResult>('/auth/login', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

/** 获取当前用户信息 */
export function fetchCurrentUser() {
  if (USE_MOCK) {
    return mockRequest(() => MOCK_USERS[0])
  }
  return apiRequest<UserVO>('/auth/me')
}

/** 获取用户列表 */
export function fetchUserList() {
  if (USE_MOCK) {
    return mockRequest(() => MOCK_USERS.filter(u => u.status === 1))
  }
  return apiRequest<UserVO[]>('/users')
}

/** 搜索用户 */
export function searchUsers(keyword: string) {
  if (USE_MOCK) {
    return mockRequest(() =>
      MOCK_USERS.filter(u =>
        u.status === 1 &&
        (u.realName.includes(keyword) || u.username.includes(keyword) || u.email.includes(keyword))
      )
    )
  }
  return apiRequest<UserVO[]>(`/users/search?keyword=${encodeURIComponent(keyword)}`)
}
