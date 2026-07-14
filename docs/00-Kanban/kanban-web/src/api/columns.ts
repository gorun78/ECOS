/**
 * 列 API 层（Mock先行）
 */
import { mockRequest, apiRequest } from './request'
import type { BoardColumnVO, CreateColumnReq, UpdateColumnReq } from '@/types/column'

const USE_MOCK = true

/** 看板下列表 */
export function fetchColumns(boardId: string) {
  if (USE_MOCK) {
    return mockRequest<BoardColumnVO[]>(() => [])
  }
  return apiRequest<BoardColumnVO[]>(`/boards/${boardId}/columns`)
}

/** 创建列 */
export function createColumn(data: CreateColumnReq) {
  if (USE_MOCK) {
    const newCol: BoardColumnVO = {
      id: `col-${Date.now()}`,
      boardId: data.boardId,
      name: data.name,
      position: 999,
      color: data.color || '#E2E8F0',
      wipLimit: data.wipLimit,
      isDoneColumn: data.isDoneColumn || false,
      createdAt: new Date().toISOString(),
      cards: [],
    }
    return mockRequest(() => newCol)
  }
  return apiRequest<BoardColumnVO>('/columns', { method: 'POST', body: JSON.stringify(data) })
}

/** 更新列 */
export function updateColumn(columnId: string, data: UpdateColumnReq) {
  if (USE_MOCK) {
    return mockRequest(() => ({ id: columnId, ...data }) as unknown as BoardColumnVO)
  }
  return apiRequest<BoardColumnVO>(`/columns/${columnId}`, { method: 'PUT', body: JSON.stringify(data) })
}

/** 删除列 */
export function deleteColumn(columnId: string) {
  if (USE_MOCK) {
    return mockRequest(() => true)
  }
  return apiRequest<boolean>(`/columns/${columnId}`, { method: 'DELETE' })
}

/** 重排列顺序 */
export function reorderColumns(boardId: string, columnIds: string[]) {
  if (USE_MOCK) {
    return mockRequest(() => true)
  }
  return apiRequest<boolean>(`/boards/${boardId}/columns/reorder`, {
    method: 'PUT',
    body: JSON.stringify({ columnIds }),
  })
}
