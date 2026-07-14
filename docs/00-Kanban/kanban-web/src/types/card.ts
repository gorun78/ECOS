import type { UserVO } from './user'

/** 卡片标签 */
export interface TagVO {
  id: string
  name: string
  color: string
  boardId: string
}

/** 检查项 */
export interface ChecklistItemVO {
  id: string
  cardId: string
  content: string
  completed: boolean
  position: number
}

/** 评论 */
export interface CommentVO {
  id: string
  cardId: string
  content: string
  createdBy: UserVO
  createdAt: string
  mentions?: string[]
}

/** 活动日志 */
export interface ActivityLogVO {
  id: string
  cardId: string
  action: 'created' | 'moved' | 'updated' | 'commented' | 'assigned'
  operator: UserVO
  detail: string
  createdAt: string
}

/** 看板卡片 */
export interface CardVO {
  id: string
  boardId: string
  columnId: string
  title: string
  description?: string
  assigneeId?: string
  assignee?: UserVO
  dueDate?: string
  priority: 'low' | 'medium' | 'high' | 'urgent'
  position: number
  linkedObjectType?: string
  linkedObjectId?: string
  linkedWorkflowId?: string
  tags: TagVO[]
  checklists: ChecklistItemVO[]
  comments: CommentVO[]
  activities: ActivityLogVO[]
  createdBy: UserVO
  createdAt: string
  updatedAt: string
  completedAt?: string
}

/** 创建卡片请求 */
export interface CreateCardReq {
  boardId: string
  columnId: string
  title: string
  description?: string
  assigneeId?: string
  dueDate?: string
  priority?: 'low' | 'medium' | 'high' | 'urgent'
  tags?: string[]
}

/** 更新卡片请求 */
export interface UpdateCardReq {
  title?: string
  description?: string
  assigneeId?: string
  dueDate?: string
  priority?: 'low' | 'medium' | 'high' | 'urgent'
}
