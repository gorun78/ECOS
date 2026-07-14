/** 用户信息 */
export interface UserVO {
  id: string
  username: string
  realName: string
  email: string
  avatar?: string
  role: 'sys_admin' | 'board_admin' | 'member' | 'viewer'
  status: 0 | 1
  createdAt: string
}

/** 看板成员 */
export interface BoardMember {
  userId: string
  user: UserVO
  role: 'admin' | 'member' | 'viewer'
  joinedAt: string
}
