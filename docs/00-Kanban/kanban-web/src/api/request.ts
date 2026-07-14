/**
 * 请求工具 - 在未对接真实后端时使用 Mock 数据
 */
import type { ApiResult } from '@/types/api'

const API_BASE = '/api/v1'

/** 模拟网络延迟（Mock模式使用） */
function delay(ms = 200): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms + Math.random() * 300))
}

/** 包装为 ApiResult */
function ok<T>(data: T): ApiResult<T> {
  return { code: 200, message: 'success', data, timestamp: new Date().toISOString(), traceId: `mock-${Date.now()}` }
}

function fail<T>(message: string, code = 500): ApiResult<T> {
  return { code, message, data: null as unknown as T, timestamp: new Date().toISOString(), traceId: `mock-${Date.now()}` }
}

export async function mockRequest<T>(fn: () => T | Promise<T>, errorMsg?: string): Promise<ApiResult<T>> {
  await delay()
  try {
    const data = await fn()
    return ok(data)
  } catch {
    return fail(errorMsg || '操作失败')
  }
}

/** 实际 API 请求（后端就绪后使用） */
export async function apiRequest<T>(
  url: string,
  options?: RequestInit
): Promise<ApiResult<T>> {
  try {
    const res = await fetch(`${API_BASE}${url}`, {
      headers: { 'Content-Type': 'application/json' },
      ...options,
    })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    return res.json()
  } catch (err) {
    return fail(err instanceof Error ? err.message : '网络异常')
  }
}
