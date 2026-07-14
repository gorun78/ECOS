<template>
  <el-drawer
    v-model="drawerVisible"
    direction="rtl"
    size="480px"
    :title="drawerTitle"
    @close="handleClose"
  >
    <template v-if="props.card" #header>
      <div class="drawer-header">
        <el-input
          v-model="editTitle"
          :autosize="{ minRows: 1, maxRows: 3 }"
          type="textarea"
          class="title-input"
          @blur="saveTitle"
        />
        <div class="drawer-column-badge">
          <el-tag type="info" size="small">{{ columnName }}</el-tag>
        </div>
      </div>
    </template>

    <template v-if="props.card">
      <div class="card-detail">
        <!-- Priority -->
        <div class="detail-section">
          <div class="section-label">优先级</div>
          <el-select v-model="editPriority" size="small" style="width: 120px" @change="saveCard">
            <el-option label="低" value="low" />
            <el-option label="中" value="medium" />
            <el-option label="高" value="high" />
            <el-option label="紧急" value="urgent" />
          </el-select>
        </div>

        <!-- Assignee -->
        <div class="detail-section">
          <div class="section-label">负责人</div>
          <el-select v-model="editAssigneeId" placeholder="选择负责人" clearable filterable size="small" style="width: 100%" @change="saveCard">
            <el-option v-for="m in members" :key="m.userId" :label="m.user.realName" :value="m.userId" />
          </el-select>
        </div>

        <!-- Due date -->
        <div class="detail-section">
          <div class="section-label">截止日期</div>
          <el-date-picker v-model="editDueDate" type="date" placeholder="选择日期" size="small" style="width: 100%" value-format="YYYY-MM-DD" @change="saveCard" />
        </div>

        <!-- Tags -->
        <div class="detail-section">
          <div class="section-label">标签</div>
          <div class="tags-container">
            <el-tag v-for="tag in props.card.tags" :key="tag.id" :color="tag.color" class="detail-tag" closable size="small" @close="removeTag(tag.id)">
              {{ tag.name }}
            </el-tag>
            <el-button text size="small" type="primary" @click="showAddTag = true">+ 添加标签</el-button>
          </div>
        </div>

        <!-- Description -->
        <div class="detail-section">
          <div class="section-label">描述</div>
          <el-input v-model="editDescription" type="textarea" :rows="4" placeholder="添加描述..." @blur="saveCard" />
        </div>

        <!-- Checklists -->
        <div class="detail-section">
          <div class="section-label">子任务 ({{ doneCount }}/{{ props.card.checklists.length }})</div>
          <div class="checklist-container">
            <div v-for="item in props.card.checklists" :key="item.id" class="checklist-item">
              <el-checkbox v-model="item.completed" @change="saveCard" />
              <el-input v-model="item.content" size="small" :class="{ 'checklist-done': item.completed }" @blur="saveCard" />
              <el-button text size="small" type="danger" @click="removeChecklist(item.id)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <el-button text size="small" type="primary" @click="addChecklist">+ 添加子任务</el-button>
          </div>
        </div>

        <!-- Activity log -->
        <div class="detail-section">
          <div class="section-label">活动日志</div>
          <div class="activity-list">
            <div v-for="log in props.card.activities" :key="log.id" class="activity-item">
              <el-avatar :size="24">{{ log.operator.realName?.charAt(0) }}</el-avatar>
              <div class="activity-content">
                <span class="activity-operator">{{ log.operator.realName }}</span>
                <span class="activity-action">{{ log.detail }}</span>
                <div class="activity-time">{{ formatDateTime(log.createdAt) }}</div>
              </div>
            </div>
            <el-empty v-if="!props.card.activities.length" description="暂无活动记录" :image-size="60" />
          </div>
        </div>

        <!-- Comments -->
        <div class="detail-section">
          <div class="section-label">评论</div>
          <div class="comments-container">
            <div v-for="comment in props.card.comments" :key="comment.id" class="comment-item">
              <el-avatar :size="24">{{ comment.createdBy.realName?.charAt(0) }}</el-avatar>
              <div class="comment-content">
                <div class="comment-header">
                  <span class="comment-author">{{ comment.createdBy.realName }}</span>
                  <span class="comment-time">{{ formatDateTime(comment.createdAt) }}</span>
                </div>
                <div class="comment-text">{{ comment.content }}</div>
              </div>
            </div>
            <div class="comment-input-area">
              <el-input v-model="newComment" :rows="2" type="textarea" placeholder="输入评论..." @keyup.ctrl.enter="submitComment" />
              <el-button type="primary" size="small" :disabled="!newComment.trim()" @click="submitComment" style="margin-top: 8px">发送</el-button>
            </div>
          </div>
        </div>
      </div>
    </template>

    <template #footer>
      <div class="drawer-footer">
        <el-popconfirm title="确定删除此卡片？" @confirm="handleDelete">
          <template #reference>
            <el-button type="danger" text size="small" :icon="Delete">删除卡片</el-button>
          </template>
        </el-popconfirm>
        <el-button text size="small" :icon="Share" @click="copyLink">复制链接</el-button>
      </div>
    </template>
  </el-drawer>

  <!-- Add Tag Dialog -->
  <el-dialog v-model="showAddTag" title="添加标签" width="360px">
    <el-form label-width="60px">
      <el-form-item label="名称">
        <el-input v-model="newTagName" placeholder="如：紧急" maxlength="20" />
      </el-form-item>
      <el-form-item label="颜色">
        <el-color-picker v-model="newTagColor" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showAddTag = false">取消</el-button>
      <el-button type="primary" @click="submitTag">添加</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Delete, Share } from '@element-plus/icons-vue'
import type { CardVO } from '@/types/card'
import type { UserVO } from '@/types/user'
import { useBoardStore } from '@/stores/board'
import type { BoardMemberVO } from '@/types/column'

const props = defineProps<{
  visible: boolean
  card: CardVO | null
  boardId: string
}>()

const emit = defineEmits<{
  'update:visible': [v: boolean]
  'update': [card: CardVO]
  'delete': [cardId: string]
}>()

const boardStore = useBoardStore()

const drawerVisible = computed({
  get: () => props.visible,
  set: (v: boolean) => emit('update:visible', v),
})

const drawerTitle = computed(() => props.card?.title || '卡片详情')

const editTitle = ref('')
const editDescription = ref('')
const editAssigneeId = ref<string | undefined>()
const editDueDate = ref<string>('')
const editPriority = ref<'low' | 'medium' | 'high' | 'urgent'>('medium')
const showAddTag = ref(false)
const newTagName = ref('')
const newTagColor = ref('#409EFF')
const newComment = ref('')
let checklistIdCounter = 100

const members = computed(() => boardStore.boardMembers)

const columnName = computed(() => {
  if (!props.card || !boardStore.currentBoard) return ''
  const col = boardStore.currentBoard.columns.find(c => c.id === props.card!.columnId)
  return col?.name || ''
})

const doneCount = computed(() => {
  if (!props.card) return 0
  return props.card.checklists.filter((c: { completed: boolean }) => c.completed).length
})

// Sync card data to local edit state
watch(() => props.card, (card) => {
  if (card) {
    editTitle.value = card.title
    editDescription.value = card.description || ''
    editAssigneeId.value = card.assigneeId
    editDueDate.value = card.dueDate || ''
    editPriority.value = card.priority
  }
}, { immediate: true })

function handleClose() {
  emit('update:visible', false)
}

async function saveTitle() {
  if (!props.card || editTitle.value === props.card.title) return
  await boardStore.editCard(props.card.id, { title: editTitle.value })
}

async function saveCard() {
  if (!props.card) return
  await boardStore.editCard(props.card.id, {
    title: editTitle.value,
    description: editDescription.value || undefined,
    assigneeId: editAssigneeId.value,
    dueDate: editDueDate.value || undefined,
    priority: editPriority.value,
  })
}

function removeTag(tagId: string) {
  if (!props.card) return
  props.card.tags = props.card.tags.filter((t: { id: string }) => t.id !== tagId)
  ElMessage.success('标签已移除')
}

function submitTag() {
  if (!newTagName.value.trim() || !props.card) return
  props.card.tags.push({
    id: `tag-${Date.now()}`,
    name: newTagName.value.trim(),
    color: newTagColor.value,
    boardId: props.boardId,
  })
  showAddTag.value = false
  newTagName.value = ''
  newTagColor.value = '#409EFF'
}

function addChecklist() {
  if (!props.card) return
  checklistIdCounter++
  props.card.checklists.push({
    id: `cl-${checklistIdCounter}`,
    cardId: props.card.id,
    content: '新子任务',
    completed: false,
    position: props.card.checklists.length * 1000,
  })
}

function removeChecklist(itemId: string) {
  if (!props.card) return
  props.card.checklists = props.card.checklists.filter((c: { id: string }) => c.id !== itemId)
}

function submitComment() {
  if (!newComment.value.trim() || !props.card) return
  props.card.comments.push({
    id: `cmt-${Date.now()}`,
    cardId: props.card.id,
    content: newComment.value.trim(),
    createdBy: { id: 'u-001', username: 'current', realName: '当前用户', email: '', role: 'member', status: 1, createdAt: '' },
    createdAt: new Date().toISOString(),
  })
  newComment.value = ''
  ElMessage.success('评论已发送')
}

function formatDateTime(dateStr: string): string {
  const d = new Date(dateStr)
  const pad = (n: number) => n.toString().padStart(2, '0')
  return `${pad(d.getMonth() + 1)}/${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function handleDelete() {
  if (props.card) emit('delete', props.card.id)
}

function copyLink() {
  navigator.clipboard.writeText(window.location.href).then(() => {
    ElMessage.success('链接已复制')
  })
}
</script>

<style scoped>
.drawer-header {
  padding-right: 40px;
}
.title-input :deep(.el-textarea__inner) {
  border: none;
  font-size: 18px;
  font-weight: 600;
  padding: 0;
  resize: none;
  background: transparent;
}
.title-input :deep(.el-textarea__inner:focus) {
  background: #f8fafc;
}
.drawer-column-badge {
  margin-top: 4px;
}
.card-detail {
  padding: 0 4px;
}
.detail-section {
  margin-bottom: 20px;
}
.section-label {
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 8px;
}
.tags-container {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
}
.detail-tag {
  border: none;
  color: #fff !important;
}
.checklist-container {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.checklist-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.checklist-done :deep(.el-input__inner) {
  text-decoration: line-through;
  color: #94a3b8;
}
.activity-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.activity-item {
  display: flex;
  gap: 10px;
}
.activity-content {
  flex: 1;
}
.activity-operator {
  font-weight: 500;
  font-size: 13px;
}
.activity-action {
  color: #64748b;
  font-size: 13px;
  margin-left: 4px;
}
.activity-time {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 2px;
}
.comments-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.comment-item {
  display: flex;
  gap: 10px;
}
.comment-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.comment-author {
  font-weight: 500;
  font-size: 13px;
}
.comment-time {
  font-size: 11px;
  color: #94a3b8;
}
.comment-text {
  font-size: 13px;
  color: #334155;
  line-height: 1.5;
  white-space: pre-wrap;
}
.drawer-footer {
  display: flex;
  justify-content: space-between;
}
</style>
