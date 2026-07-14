/**
 * 卡片 API 层（Mock先行）
 */
import { mockRequest, apiRequest } from './request'
import { createMockBoards } from './mock/data'
import type { CardVO, CreateCardReq, UpdateCardReq } from '@/types/card'
import type { UserVO } from '@/types/user'

const USE_MOCK = true

let cardIdCounter = 200

/** 获取列下卡片列表 */
export function fetchCardsByColumn(boardId: string, columnId: string) {
  if (USE_MOCK) {
    return mockRequest(() => {
      const board = createMockBoards().find(b => b.id === boardId)
      if (!board) return []
      const col = board.columns.find(c => c.id === columnId)
      return col?.cards || []
    })
  }
  return apiRequest<CardVO[]>(`/boards/${boardId}/columns/${columnId}/cards`)
}

/** 创建卡片 */
export function createCard(data: CreateCardReq) {
  if (USE_MOCK) {
    return mockRequest(() => {
      cardIdCounter++
      return {
        id: `c-${cardIdCounter}`,
        boardId: data.boardId,
        columnId: data.columnId,
        title: data.title,
        description: data.description,
        assigneeId: data.assigneeId,
        dueDate: data.dueDate,
        priority: data.priority || 'medium',
        position: Date.now(),
        tags: [],
        checklists: [],
        comments: [],
        activities: [],
        createdBy: { id: 'u-001', username: 'current', realName: '当前用户', email: '', role: 'member', status: 1, createdAt: '' },
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      } as CardVO
    })
  }
  return apiRequest<CardVO>('/cards', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

/** 更新卡片 */
export function updateCard(cardId: string, data: UpdateCardReq) {
  if (USE_MOCK) {
    return mockRequest(() => ({ id: cardId, ...data } as unknown as CardVO))
  }
  return apiRequest<CardVO>(`/cards/${cardId}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  })
}

/** 移动卡片到新列 */
export function moveCard(cardId: string, targetColumnId: string, position: number) {
  if (USE_MOCK) {
    return mockRequest(() => ({ id: cardId, columnId: targetColumnId, position }) as unknown as CardVO)
  }
  return apiRequest<CardVO>(`/cards/${cardId}/move`, {
    method: 'PUT',
    body: JSON.stringify({ columnId: targetColumnId, position }),
  })
}

/** 删除卡片 */
export function deleteCard(cardId: string) {
  if (USE_MOCK) {
    return mockRequest(() => true)
  }
  return apiRequest<boolean>(`/cards/${cardId}`, { method: 'DELETE' })
}
