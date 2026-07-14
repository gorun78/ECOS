/** 看板列定义 */
import type { CardVO } from './card'

export interface BoardColumnVO {
  id: string
  boardId: string
  name: string
  position: number
  color: string
  wipLimit?: number
  isDoneColumn: boolean
  createdAt: string
  cards?: CardVO[]
}

/** 创建列请求 */
export interface CreateColumnReq {
  boardId: string
  name: string
  color?: string
  wipLimit?: number
  isDoneColumn?: boolean
}

/** 更新列请求 */
export interface UpdateColumnReq {
  name?: string
  color?: string
  wipLimit?: number
  isDoneColumn?: boolean
}

/** 看板成员 */
export interface BoardMemberVO {
  userId: string
  user: import('./user').UserVO
  role: 'admin' | 'member' | 'viewer'
  joinedAt: string
}
