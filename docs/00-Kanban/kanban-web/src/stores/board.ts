import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { BoardVO } from '@/types/board'
import type { BoardColumnVO } from '@/types/column'
import type { CardVO, CreateCardReq, UpdateCardReq, ChecklistItemVO, CommentVO } from '@/types/card'
import type { BoardMemberVO } from '@/types/column'
import { fetchBoardList, fetchBoardDetail, createBoard as createBoardApi, deleteBoard } from '@/api/boards'
import { createCard as createCardApi, updateCard as updateCardApi, moveCard as moveCardApi, deleteCard as deleteCardApi } from '@/api/cards'
import { createColumn as createColumnApi, updateColumn as updateColumnApi, deleteColumn as deleteColumnApi, reorderColumns } from '@/api/columns'
import { useAuthStore } from '@/stores/auth'
import { ElMessage } from 'element-plus'

export const useBoardStore = defineStore('board', () => {
  // 状态
  const boardList = ref<BoardVO[]>([])
  const currentBoard = ref<BoardVO | null>(null)
  const loading = ref(false)
  const boardLoading = ref(false)
  const error = ref<string | null>(null)

  // 计算属性
  const columns = computed(() => currentBoard.value?.columns || [])
  const boardMembers = computed(() => currentBoard.value?.members || [])
  const currentUserRole = computed(() => {
    const authStore = useAuthStore()
    const currentUserId = authStore.currentUser?.id
    if (currentUserId && currentBoard.value) {
      const member = currentBoard.value.members.find(m => m.userId === currentUserId)
      if (member) return member.role as 'admin' | 'member' | 'viewer'
    }
    // Fallback: 看板只有一个成员时取其角色
    if (currentBoard.value?.members?.length === 1) {
      return currentBoard.value.members[0].role as 'admin' | 'member' | 'viewer'
    }
    return 'viewer' as const
  })
  const isViewer = computed(() => currentUserRole.value === 'viewer')

  // 获取看板列表
  async function fetchBoards() {
    loading.value = true
    error.value = null
    try {
      const res = await fetchBoardList()
      if (res.code === 200) {
        boardList.value = res.data
      } else {
        error.value = res.message
      }
    } catch (err) {
      error.value = '获取看板列表失败'
    } finally {
      loading.value = false
    }
  }

  // 获取看板详情
  async function fetchBoard(boardId: string) {
    boardLoading.value = true
    error.value = null
    try {
      const res = await fetchBoardDetail(boardId)
      if (res.code === 200) {
        currentBoard.value = res.data
      } else {
        error.value = res.message
      }
    } catch (err) {
      error.value = '获取看板详情失败'
    } finally {
      boardLoading.value = false
    }
  }

  // 创建看板
  async function createBoard(data: { name: string; description?: string; template: '3col' | '4col' | '5col' | 'custom'; ownerTeam?: string }) {
    const res = await createBoardApi(data)
    if (res.code === 200) {
      boardList.value.unshift(res.data)
      ElMessage.success('看板创建成功')
      return res.data
    }
    ElMessage.error(res.message || '创建看板失败')
    return null
  }

  // 删除看板
  async function removeBoard(boardId: string) {
    const res = await deleteBoard(boardId)
    if (res.code === 200) {
      boardList.value = boardList.value.filter(b => b.id !== boardId)
      if (currentBoard.value?.id === boardId) currentBoard.value = null
      ElMessage.success('看板已删除')
      return true
    }
    ElMessage.error(res.message || '删除看板失败')
    return false
  }

  // 创建卡片
  async function addCard(data: CreateCardReq) {
    const res = await createCardApi(data)
    if (res.code === 200 && currentBoard.value) {
      const col = currentBoard.value.columns.find(c => c.id === data.columnId)
      if (col) {
        if (!col.cards) col.cards = []
        col.cards.push(res.data)
      }
      ElMessage.success('卡片创建成功')
      return res.data
    }
    ElMessage.error(res.message || '创建卡片失败')
    return null
  }

  // 移动卡片
  async function moveCardToColumn(cardId: string, fromColumnId: string, toColumnId: string, position: number) {
    if (!currentBoard.value) return false

    // 乐观更新
    const fromCol = currentBoard.value.columns.find(c => c.id === fromColumnId)
    const toCol = currentBoard.value.columns.find(c => c.id === toColumnId)
    if (!fromCol || !toCol) return false
    if (!fromCol.cards) return false

    const cardIndex = fromCol.cards.findIndex((c: CardVO) => c.id === cardId)
    if (cardIndex === -1) return false

    const card = fromCol.cards[cardIndex]
    fromCol.cards.splice(cardIndex, 1)
    if (!toCol.cards) toCol.cards = []
    card.columnId = toColumnId
    card.position = position
    toCol.cards.push(card)

    // 异步同步（失败时不回滚，只给出错误提示）
    const res = await moveCardApi(cardId, toColumnId, position)
    if (res.code !== 200) {
      ElMessage.error('移动卡片失败，请刷新重试')
    }
    return true
  }

  // 更新卡片
  async function editCard(cardId: string, data: UpdateCardReq) {
    const res = await updateCardApi(cardId, data)
    if (res.code === 200 && currentBoard.value) {
      for (const col of currentBoard.value.columns) {
        const card = col.cards?.find(c => c.id === cardId)
        if (card) {
          Object.assign(card, data)
          break
        }
      }
      ElMessage.success('卡片已更新')
      return true
    }
    ElMessage.error(res.message || '更新卡片失败')
    return false
  }

  // 删除卡片
  async function removeCard(cardId: string, columnId: string) {
    if (!currentBoard.value) return false
    const res = await deleteCardApi(cardId)
    if (res.code === 200) {
      const col = currentBoard.value.columns.find(c => c.id === columnId)
      if (col && col.cards) {
        col.cards = col.cards.filter(c => c.id !== cardId)
      }
      ElMessage.success('卡片已删除')
      return true
    }
    ElMessage.error(res.message || '删除卡片失败')
    return false
  }

  // 获取看板中的用户角色
  function getUserRole(userId: string): 'admin' | 'member' | 'viewer' {
    if (!currentBoard.value) return 'viewer'
    const member = currentBoard.value.members.find(m => m.userId === userId)
    return member?.role || 'viewer'
  }

  return {
    boardList, currentBoard, loading, boardLoading, error,
    columns, boardMembers, currentUserRole, isViewer,
    fetchBoards, fetchBoard, createBoard, removeBoard,
    addCard, moveCardToColumn, editCard, removeCard,
    getUserRole,
  }
})
