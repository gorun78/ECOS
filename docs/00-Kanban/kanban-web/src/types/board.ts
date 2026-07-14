/** 看板定义 */
import type { BoardColumnVO } from './column'
import type { BoardMemberVO } from './column'

export interface BoardVO {
  id: string
  name: string
  description?: string
  template: '3col' | '4col' | '5col' | 'custom'
  createdBy: string
  createdAt: string
  updatedAt: string
  isArchived: boolean
  ownerTeam?: string
  columns: BoardColumnVO[]
  members: BoardMemberVO[]
}

/** 创建看板请求 */
export interface CreateBoardReq {
  name: string
  description?: string
  template: '3col' | '4col' | '5col' | 'custom'
  columns?: { name: string; color?: string }[]
  ownerTeam?: string
}

/** 更新看板请求 */
export interface UpdateBoardReq {
  name?: string
  description?: string
  ownerTeam?: string
}
