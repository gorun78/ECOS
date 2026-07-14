<template>
  <div class="board-page">
    <!-- Loading -->
    <div v-if="boardStore.boardLoading" class="board-loading-full">
      <el-skeleton :rows="5" animated />
    </div>

    <!-- Error -->
    <el-result v-else-if="boardStore.error" icon="error" title="加载失败" :sub-title="boardStore.error">
      <template #extra>
        <el-button type="primary" @click="loadBoard">重新加载</el-button>
      </template>
    </el-result>

    <!-- Board content -->
    <template v-else-if="board">
      <!-- Board Header -->
      <div class="board-header">
        <div class="board-header-left">
          <el-button text :icon="ArrowLeft" @click="router.push('/dashboard')">返回</el-button>
          <div class="board-title-area">
            <h2 class="board-title">{{ board.name }}</h2>
            <span v-if="board.ownerTeam" class="board-team-tag">
              <el-tag size="small" type="info">{{ board.ownerTeam }}</el-tag>
            </span>
          </div>
        </div>
        <div class="board-header-right">
          <el-input
            v-model="searchKeyword"
            :prefix-icon="Search"
            placeholder="搜索卡片..."
            clearable
            class="board-search"
            size="small"
          />
          <el-button text :icon="Setting" @click="router.push(`/board/${board.id}/settings`)">
            设置
          </el-button>
        </div>
      </div>

      <!-- Board Columns -->
      <div class="board-columns board-scroll-x">
        <div
          v-for="col in board.columns"
          :key="col.id"
          class="board-column"
          :class="{ 'column-wip-over': isWipOver(col) }"
        >
          <!-- Column Header -->
          <div class="column-header" :style="{ '--col-color': col.color }">
            <div class="column-title-row">
              <span class="column-title">{{ col.name }}</span>
              <el-tag size="small" round :type="col.isDoneColumn ? 'success' : 'info'">
                {{ col.cards?.length || 0 }}
                <template v-if="col.wipLimit"> / {{ col.wipLimit }}</template>
              </el-tag>
            </div>
          </div>

          <!-- Column Body with Drag -->
          <div
            class="column-body"
            @dragover.prevent="onDragOver($event, col.id)"
            @dragleave="onDragLeave($event, col.id)"
            @drop="onDrop($event, col.id)"
            :class="{ 'drag-over': dragOverColumn === col.id }"
          >
            <TransitionGroup name="card" tag="div" class="cards-container">
              <div
                v-for="card in filteredCards(col)"
                :key="card.id"
                class="board-card"
                :class="[`priority-${card.priority}`, { 'dragging': draggingCardId === card.id }]"
                draggable="true"
                @dragstart="onDragStart($event, card, col.id)"
                @dragend="onDragEnd"
                @click="openCardDetail(card)"
              >
                <!-- Tags -->
                <div v-if="card.tags?.length" class="card-tags">
                  <el-tag
                    v-for="tag in card.tags.slice(0, 3)"
                    :key="tag.id"
                    :color="tag.color"
                    class="card-tag"
                    size="small"
                  >
                    {{ tag.name }}
                  </el-tag>
                  <span v-if="card.tags.length > 3" class="card-tags-more">+{{ card.tags.length - 3 }}</span>
                </div>

                <!-- Title -->
                <div class="card-title">{{ card.title }}</div>

                <!-- Description preview -->
                <div v-if="card.description" class="card-desc">{{ card.description.slice(0, 50) }}{{ card.description.length > 50 ? '...' : '' }}</div>

                <!-- Checklists progress -->
                <div v-if="card.checklists?.length" class="card-checklist">
                  <el-progress
                    :percentage="Math.round(card.checklists.filter(c => c.completed).length / card.checklists.length * 100)"
                    :stroke-width="4"
                    :show-text="false"
                  />
                  <span class="checklist-text">{{ card.checklists.filter(c => c.completed).length }}/{{ card.checklists.length }}</span>
                </div>

                <!-- Card footer -->
                <div class="card-footer">
                  <div class="card-footer-left">
                    <el-icon v-if="card.dueDate" :size="14" :color="isOverdue(card.dueDate) ? '#f56c6c' : '#94a3b8'">
                      <Calendar />
                    </el-icon>
                    <span v-if="card.dueDate" class="card-due" :class="{ overdue: isOverdue(card.dueDate) }">
                      {{ formatDate(card.dueDate) }}
                    </span>
                  </div>
                  <div class="card-footer-right">
                    <el-avatar
                      v-if="card.assignee"
                      :size="22"
                      :style="{ background: getAvatarColor(card.assignee.realName) }"
                    >
                      {{ card.assignee.realName.charAt(0) }}
                    </el-avatar>
                    <el-icon v-if="card.comments?.length" :size="14" color="#94a3b8">
                      <ChatLineSquare />
                    </el-icon>
                    <span v-if="card.comments?.length" class="card-comment-count">{{ card.comments.length }}</span>
                  </div>
                </div>
              </div>
            </TransitionGroup>

            <!-- Empty state -->
            <el-empty v-if="!filteredCards(col).length" :description="searchKeyword ? '无匹配卡片' : '暂无卡片'">
              <el-button v-if="!searchKeyword && !boardStore.isViewer" text type="primary" size="small" @click="showCreateCard(col.id)">
                + 添加卡片
              </el-button>
            </el-empty>
          </div>

          <!-- Add card button -->
          <div v-if="!boardStore.isViewer" class="column-footer">
            <el-button text type="primary" size="small" :icon="Plus" @click="showCreateCard(col.id)">
              添加卡片
            </el-button>
          </div>
        </div>

        <!-- Add column (Admin only) -->
        <div v-if="currentRole === 'admin'" class="add-column-btn" @click="showAddColumn = true">
          <el-icon :size="20"><Plus /></el-icon>
          <span>添加列</span>
        </div>
      </div>
    </template>

    <!-- Create Card Dialog -->
    <el-dialog v-model="createDialogVisible" title="新建卡片" width="480px">
      <el-form ref="cardFormRef" :model="cardForm" :rules="cardRules" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="cardForm.title" placeholder="卡片标题" maxlength="200" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="cardForm.description" type="textarea" :rows="3" placeholder="卡片详细描述（可选）" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-select v-model="cardForm.assigneeId" placeholder="选择负责人" clearable filterable style="width: 100%">
            <el-option
              v-for="m in boardMembers"
              :key="m.userId"
              :label="m.user.realName"
              :value="m.userId"
            >
              <span>{{ m.user.realName }}</span>
              <span class="text-muted"> ({{ m.user.username }})</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="截止日期">
          <el-date-picker v-model="cardForm.dueDate" type="date" placeholder="选择日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-radio-group v-model="cardForm.priority">
            <el-radio-button value="low">低</el-radio-button>
            <el-radio-button value="medium">中</el-radio-button>
            <el-radio-button value="high">高</el-radio-button>
            <el-radio-button value="urgent">紧急</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="cardCreating" @click="handleCreateCard">创建</el-button>
      </template>
    </el-dialog>

    <!-- Add Column Dialog -->
    <el-dialog v-model="showAddColumn" title="添加列" width="400px">
      <el-form ref="colFormRef" :model="colForm" :rules="colRules" label-width="80px">
        <el-form-item label="列名" prop="name">
          <el-input v-model="colForm.name" placeholder="如：待办" maxlength="100" />
        </el-form-item>
        <el-form-item label="WIP上限">
          <el-input-number v-model="colForm.wipLimit" :min="0" :max="99" placeholder="不限制" />
        </el-form-item>
        <el-form-item label="颜色">
          <el-color-picker v-model="colForm.color" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddColumn = false">取消</el-button>
        <el-button type="primary" :loading="colCreating" @click="handleAddColumn">添加</el-button>
      </template>
    </el-dialog>

    <!-- Card Detail Drawer -->
    <CardDetailDrawer
      v-model:visible="detailVisible"
      :card="selectedCard"
      :board-id="board?.id || ''"
      @update="handleCardUpdate"
      @delete="handleCardDelete"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { ArrowLeft, Search, Setting, Plus, Calendar, ChatLineSquare } from '@element-plus/icons-vue'
import { useBoardStore } from '@/stores/board'
import CardDetailDrawer from '@/components/board/CardDetailDrawer.vue'
import type { CardVO, CreateCardReq } from '@/types/card'
import type { BoardColumnVO } from '@/types/column'

const router = useRouter()
const route = useRoute()
const boardStore = useBoardStore()

// Board state
const board = computed(() => boardStore.currentBoard)
const boardMembers = computed(() => boardStore.boardMembers)
const currentRole = computed(() => boardStore.currentUserRole)

// Search
const searchKeyword = ref('')

// Drag state
const draggingCardId = ref<string | null>(null)
const dragSourceColumnId = ref<string | null>(null)
const dragOverColumn = ref<string | null>(null)

// Create card dialog
const createDialogVisible = ref(false)
const cardCreating = ref(false)
const targetColumnId = ref('')
const cardFormRef = ref<FormInstance>()
const cardForm = ref({
  title: '',
  description: '',
  assigneeId: '' as string | undefined,
  dueDate: '' as string | undefined,
  priority: 'medium' as 'low' | 'medium' | 'high' | 'urgent',
})
const cardRules = {
  title: [{ required: true, message: '请输入卡片标题', trigger: 'blur' }],
}

// Add column dialog
const showAddColumn = ref(false)
const colCreating = ref(false)
const colFormRef = ref<FormInstance>()
const colForm = ref({ name: '', wipLimit: 0, color: '#E2E8F0' })
const colRules = {
  name: [{ required: true, message: '请输入列名', trigger: 'blur' }],
}

// Card detail drawer
const detailVisible = ref(false)
const selectedCard = ref<CardVO | null>(null)

function loadBoard() {
  const id = route.params.id as string
  if (id) boardStore.fetchBoard(id)
}

function filteredCards(col: BoardColumnVO): CardVO[] {
  if (!searchKeyword.value) return col.cards || []
  const kw = searchKeyword.value.toLowerCase()
  return (col.cards || []).filter(c =>
    c.title.toLowerCase().includes(kw) ||
    c.description?.toLowerCase().includes(kw) ||
    c.tags?.some(t => t.name.toLowerCase().includes(kw))
  )
}

function isWipOver(col: BoardColumnVO): boolean {
  if (!col.wipLimit) return false
  return (col.cards?.length || 0) > col.wipLimit
}

function isOverdue(dateStr: string): boolean {
  return new Date(dateStr) < new Date()
}

function formatDate(dateStr: string): string {
  const d = new Date(dateStr)
  return `${d.getMonth() + 1}/${d.getDate()}`
}

const AVATAR_COLORS = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#909399', '#b37feb', '#36cfc9']
function getAvatarColor(name: string): string {
  let hash = 0
  for (let i = 0; i < name.length; i++) hash = name.charCodeAt(i) + ((hash << 5) - hash)
  return AVATAR_COLORS[Math.abs(hash) % AVATAR_COLORS.length]
}

// Drag handlers
function onDragStart(event: DragEvent, card: CardVO, columnId: string) {
  draggingCardId.value = card.id
  dragSourceColumnId.value = columnId
  event.dataTransfer?.setData('text/plain', JSON.stringify({ cardId: card.id, fromColumnId: columnId }))
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move'
}

function onDragOver(event: DragEvent, _columnId: string) {
  event.preventDefault()
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
  dragOverColumn.value = _columnId
}

function onDragLeave(event: DragEvent, columnId: string) {
  const target = event.currentTarget as HTMLElement
  const related = event.relatedTarget as HTMLElement
  if (!target.contains(related)) {
    if (dragOverColumn.value === columnId) dragOverColumn.value = null
  }
}

function onDragEnd() {
  draggingCardId.value = null
  dragSourceColumnId.value = null
  dragOverColumn.value = null
}

async function onDrop(event: DragEvent, toColumnId: string) {
  dragOverColumn.value = null
  const raw = event.dataTransfer?.getData('text/plain')
  if (!raw) return
  const { cardId, fromColumnId } = JSON.parse(raw)
  if (fromColumnId === toColumnId) return

  // Check WIP limit
  const toCol = board.value?.columns.find(c => c.id === toColumnId)
  if (toCol?.wipLimit && (toCol.cards?.length || 0) >= toCol.wipLimit) {
    ElMessage.warning(`列「${toCol.name}」已达 WIP 上限 (${toCol.wipLimit})`)
    return
  }

  const success = await boardStore.moveCardToColumn(cardId, fromColumnId, toColumnId, Date.now())
  if (!success) {
    ElMessage.error('移动卡片失败')
  }
}

// Cards
function showCreateCard(columnId: string) {
  targetColumnId.value = columnId
  cardForm.value = { title: '', description: '', assigneeId: undefined, dueDate: undefined, priority: 'medium' }
  createDialogVisible.value = true
}

async function handleCreateCard() {
  const valid = await cardFormRef.value?.validate().catch(() => false)
  if (!valid) return

  cardCreating.value = true
  const data: CreateCardReq = {
    boardId: board.value!.id,
    columnId: targetColumnId.value,
    title: cardForm.value.title,
    description: cardForm.value.description || undefined,
    assigneeId: cardForm.value.assigneeId || undefined,
    dueDate: cardForm.value.dueDate || undefined,
    priority: cardForm.value.priority,
  }
  await boardStore.addCard(data)
  cardCreating.value = false
  createDialogVisible.value = false
}

// Columns
async function handleAddColumn() {
  const valid = await colFormRef.value?.validate().catch(() => false)
  if (!valid || !board.value) return

  colCreating.value = true
  const res = await import('@/api/columns').then(m => m.createColumn({
    boardId: board.value!.id,
    name: colForm.value.name,
    color: colForm.value.color || undefined,
    wipLimit: colForm.value.wipLimit > 0 ? colForm.value.wipLimit : undefined,
  }))
  if (res.code === 200) {
    board.value.columns.push(res.data)
    ElMessage.success('列已添加')
    showAddColumn.value = false
    colForm.value = { name: '', wipLimit: 0, color: '#E2E8F0' }
  }
  colCreating.value = false
}

// Card detail
function openCardDetail(card: CardVO) {
  selectedCard.value = card
  detailVisible.value = true
}

function handleCardUpdate(updatedCard: CardVO) {
  if (!board.value) return
  for (const col of board.value.columns) {
    const idx = col.cards?.findIndex(c => c.id === updatedCard.id) ?? -1
    if (idx !== -1 && col.cards) {
      col.cards[idx] = updatedCard
      break
    }
  }
}

function handleCardDelete(cardId: string) {
  if (!board.value) return
  for (const col of board.value.columns) {
    if (col.cards) {
      col.cards = col.cards.filter(c => c.id !== cardId)
    }
  }
  detailVisible.value = false
}

onMounted(loadBoard)
watch(() => route.params.id, loadBoard)
</script>

<style scoped>
.board-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--board-bg, #f1f5f9);
}
.board-loading-full {
  padding: 32px;
  background: #fff;
  margin: 16px;
  border-radius: 8px;
}
.board-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  background: #fff;
  border-bottom: 1px solid #e2e8f0;
  flex-shrink: 0;
}
.board-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.board-title-area {
  display: flex;
  align-items: center;
  gap: 8px;
}
.board-title {
  font-size: 18px;
  font-weight: 600;
  color: #1e293b;
}
.board-header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.board-search {
  width: 200px;
}
.board-columns {
  flex: 1;
  display: flex;
  gap: 16px;
  padding: 20px 24px;
  overflow-x: auto;
  overflow-y: hidden;
  align-items: flex-start;
}
.board-column {
  min-width: 280px;
  max-width: 320px;
  width: 280px;
  flex-shrink: 0;
  background: var(--column-bg, #f8fafc);
  border-radius: 10px;
  display: flex;
  flex-direction: column;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
}
.column-header {
  padding: 12px 14px 8px;
  border-top-left-radius: 10px;
  border-top-right-radius: 10px;
  border-top: 3px solid var(--col-color, #E2E8F0);
}
.column-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.column-title {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}
.column-body {
  flex: 1;
  padding: 4px 8px 8px;
  min-height: 60px;
  overflow-y: auto;
}
.cards-container {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.board-card {
  background: #fff;
  border-radius: 8px;
  padding: 10px 12px;
  cursor: grab;
  box-shadow: var(--card-shadow);
  transition: box-shadow 0.2s, transform 0.15s;
}
.board-card:hover {
  box-shadow: var(--card-shadow-hover);
}
.board-card:active {
  cursor: grabbing;
}
.card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 6px;
}
.card-tag {
  padding: 0 6px;
  height: 20px;
  line-height: 20px;
  font-size: 11px;
  border: none;
  color: #fff !important;
  border-radius: 3px;
}
.card-tags-more {
  font-size: 11px;
  color: #94a3b8;
}
.card-title {
  font-size: 14px;
  font-weight: 500;
  color: #1e293b;
  line-height: 1.4;
  margin-bottom: 4px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.card-desc {
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.4;
  margin-bottom: 8px;
}
.card-checklist {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}
.card-checklist .el-progress {
  flex: 1;
}
.checklist-text {
  font-size: 11px;
  color: #94a3b8;
  white-space: nowrap;
}
.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 4px;
}
.card-footer-left,
.card-footer-right {
  display: flex;
  align-items: center;
  gap: 4px;
}
.card-due {
  font-size: 11px;
  color: #94a3b8;
}
.card-due.overdue {
  color: #f56c6c;
  font-weight: 500;
}
.card-comment-count {
  font-size: 11px;
  color: #94a3b8;
}
.column-footer {
  padding: 8px 12px;
  border-top: 1px solid #e2e8f0;
}
.add-column-btn {
  min-width: 60px;
  width: 60px;
  flex-shrink: 0;
  background: #fff;
  border: 2px dashed #cbd5e1;
  border-radius: 10px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  cursor: pointer;
  color: #94a3b8;
  font-size: 13px;
  height: 100px;
  transition: all 0.2s;
}
.add-column-btn:hover {
  border-color: #3b82f6;
  color: #3b82f6;
  background: #eff6ff;
}
.column-wip-over {
  box-shadow: 0 0 0 2px #f56c6c;
}
.text-muted { color: #94a3b8; font-size: 12px; }
</style>
