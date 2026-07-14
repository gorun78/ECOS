/**
 * 看板 API 层（Mock先行）
 */
import { mockRequest, apiRequest } from './request'
import { createMockBoards } from './mock/data'
import type { BoardVO, CreateBoardReq, UpdateBoardReq } from '@/types/board'

const USE_MOCK = true

/** 获取看板列表 */
export function fetchBoardList() {
  if (USE_MOCK) {
    return mockRequest(() => {
      const all = createMockBoards()
      return all.filter(b => !b.isArchived)
    })
  }
  return apiRequest<BoardVO[]>('/boards')
}

/** 获取看板详情 */
export function fetchBoardDetail(boardId: string) {
  if (USE_MOCK) {
    return mockRequest(() => {
      const board = createMockBoards().find(b => b.id === boardId)
      if (!board) throw new Error('看板不存在')
      return board
    })
  }
  return apiRequest<BoardVO>(`/boards/${boardId}`)
}

/** 创建看板 */
export function createBoard(data: CreateBoardReq) {
  if (USE_MOCK) {
    return mockRequest(() => {
      const newBoard: BoardVO = {
        id: `b-${Date.now()}`,
        name: data.name,
        description: data.description,
        template: data.template,
        createdBy: 'u-001',
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        isArchived: false,
        ownerTeam: data.ownerTeam,
        columns: (data.columns || getDefaultColumns(data.template)).map((col, i) => ({
          id: `col-${Date.now()}-${i}`,
          boardId: `b-${Date.now()}`,
          name: col.name,
          position: i,
          color: col.color || '#E2E8F0',
          isDoneColumn: i === (data.columns || getDefaultColumns(data.template)).length - 1,
          createdAt: new Date().toISOString(),
          cards: [],
        })),
        members: [],
      }
      return newBoard
    })
  }
  return apiRequest<BoardVO>('/boards', {
    method: 'POST',
    body: JSON.stringify(data),
  })
}

/** 更新看板 */
export function updateBoard(boardId: string, data: UpdateBoardReq) {
  if (USE_MOCK) {
    return mockRequest(() => ({ id: boardId, ...data } as unknown as BoardVO))
  }
  return apiRequest<BoardVO>(`/boards/${boardId}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  })
}

/** 删除看板 */
export function deleteBoard(boardId: string) {
  if (USE_MOCK) {
    return mockRequest(() => true)
  }
  return apiRequest<boolean>(`/boards/${boardId}`, { method: 'DELETE' })
}

/** 获取默认列配置 */
function getDefaultColumns(template: string): { name: string; color?: string }[] {
  const templates: Record<string, { name: string; color?: string }[]> = {
    '3col': [
      { name: '待办', color: '#E2E8F0' },
      { name: '进行中', color: '#FEF3C7' },
      { name: '已完成', color: '#D1FAE5' },
    ],
    '4col': [
      { name: '待处理', color: '#E2E8F0' },
      { name: '审核中', color: '#FEF3C7' },
      { name: '待签约', color: '#DBEAFE' },
      { name: '已完成', color: '#D1FAE5' },
    ],
    '5col': [
      { name: '待提交', color: '#E2E8F0' },
      { name: '初审', color: '#FEF3C7' },
      { name: '会签', color: '#DBEAFE' },
      { name: '审批', color: '#FCE4EC' },
      { name: '已办结', color: '#D1FAE5' },
    ],
  }
  return templates[template] || templates['4col']
}
