/** 通用API响应结构 */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
  timestamp?: string
  traceId?: string
}

/** 分页请求参数 */
export interface PageReq {
  page: number
  size: number
  [key: string]: unknown
}

/** 分页响应 */
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

/** 登录请求 */
export interface LoginReq {
  username: string
  password: string
}

/** 登录响应 */
export interface LoginResult {
  token: string
  user: import('./user').UserVO
}
