<template>
  <div class="settings-page">
    <!-- Loading -->
    <div v-if="boardStore.boardLoading" class="loading-state">
      <el-skeleton :rows="8" animated />
    </div>

    <!-- Error -->
    <el-result v-else-if="boardStore.error" icon="error" title="加载失败" :sub-title="boardStore.error">
      <template #extra>
        <el-button type="primary" @click="loadBoard">重新加载</el-button>
      </template>
    </el-result>

    <template v-else-if="board">
      <div class="settings-header">
        <el-button text :icon="ArrowLeft" @click="router.push(`/board/${board.id}`)">返回看板</el-button>
        <h1>看板设置</h1>
      </div>

      <div class="settings-content">
        <!-- Basic Info -->
        <el-card class="settings-card" shadow="never">
          <template #header><span>基本信息</span></template>
          <el-form label-width="100px">
            <el-form-item label="看板名称">
              <el-input v-model="editName" maxlength="100" />
            </el-form-item>
            <el-form-item label="看板描述">
              <el-input v-model="editDescription" type="textarea" :rows="2" />
            </el-form-item>
            <el-form-item label="所属团队">
              <el-input v-model="editTeam" placeholder="如：采购部" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="savingBasic" @click="saveBasicInfo">保存</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- Column Management -->
        <el-card class="settings-card" shadow="never">
          <template #header>
            <div class="card-header-row">
              <span>列管理</span>
              <el-button size="small" type="primary" :icon="Plus" @click="showAddColumn = true">添加列</el-button>
            </div>
          </template>

          <div class="column-list">
            <div v-for="(col, index) in board.columns" :key="col.id" class="column-item">
              <div class="column-drag-handle">
                <el-icon><Rank /></el-icon>
              </div>
              <div class="column-info">
                <div class="column-name-row">
                  <el-input v-model="col.name" size="small" style="width: 160px" />
                  <el-color-picker v-model="col.color" size="small" />
                  <el-tag v-if="col.isDoneColumn" type="success" size="small">完成列</el-tag>
                </div>
                <div class="column-options">
                  <span class="option-label">WIP上限：</span>
                  <el-input-number v-model="col.wipLimit" :min="0" :max="99" size="small" controls-position="right" style="width: 120px" />
                </div>
              </div>
              <div class="column-actions">
                <el-button text size="small" type="primary" @click="saveColumn(col)">保存</el-button>
                <el-popconfirm
                  title="确定删除此列？列中卡片将被移至第一列"
                  @confirm="handleDeleteColumn(col.id, index)"
                >
                  <template #reference>
                    <el-button text size="small" type="danger" :icon="Delete" />
                  </template>
                </el-popconfirm>
              </div>
            </div>
          </div>
        </el-card>

        <!-- Member Management -->
        <el-card class="settings-card" shadow="never">
          <template #header>
            <div class="card-header-row">
              <span>权限管理</span>
              <el-button size="small" type="primary" :icon="Plus" @click="showAddMember = true">添加成员</el-button>
            </div>
          </template>

          <el-table :data="board.members" style="width: 100%">
            <el-table-column label="成员">
              <template #default="{ row }">
                <div class="member-cell">
                  <el-avatar :size="28">{{ row.user.realName?.charAt(0) }}</el-avatar>
                  <div>
                    <div class="member-name">{{ row.user.realName }}</div>
                    <div class="member-email">{{ row.user.email }}</div>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="角色" width="180">
              <template #default="{ row }">
                <el-select v-model="row.role" size="small" @change="saveMemberRole(row)">
                  <el-option label="管理员" value="admin" />
                  <el-option label="成员" value="member" />
                  <el-option label="观察者" value="viewer" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="加入时间" width="160">
              <template #default="{ row }">
                {{ formatDateTime(row.joinedAt) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-popconfirm title="移除此成员？" @confirm="removeMember(row.userId)">
                  <template #reference>
                    <el-button text size="small" type="danger" :icon="Remove" />
                  </template>
                </el-popconfirm>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <!-- Automation Rules -->
        <el-card class="settings-card" shadow="never">
          <template #header><span>自动化规则（开发中）</span></template>
          <el-empty description="自动化规则功能即将上线" :image-size="60">
            <p class="text-muted">可配置当卡片进入指定列时自动通知、更新关联 Object 状态等</p>
          </el-empty>
        </el-card>
      </div>
    </template>

    <!-- Add Column Dialog -->
    <el-dialog v-model="showAddColumn" title="添加列" width="400px">
      <el-form ref="colFormRef" :model="colForm" :rules="colRules" label-width="80px">
        <el-form-item label="列名" prop="name">
          <el-input v-model="colForm.name" maxlength="100" />
        </el-form-item>
        <el-form-item label="WIP上限">
          <el-input-number v-model="colForm.wipLimit" :min="0" :max="99" />
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

    <!-- Add Member Dialog -->
    <el-dialog v-model="showAddMember" title="添加成员" width="400px">
      <el-form label-width="60px">
        <el-form-item label="用户">
          <el-select v-model="newMemberUserId" filterable placeholder="搜索用户" style="width: 100%">
            <el-option
              v-for="user in availableUsers"
              :key="user.id"
              :label="user.realName"
              :value="user.id"
            >
              <span>{{ user.realName }}</span>
              <span class="text-muted" style="margin-left:8px">{{ user.username }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="newMemberRole" style="width: 100%">
            <el-option label="管理员" value="admin" />
            <el-option label="成员" value="member" />
            <el-option label="观察者" value="viewer" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddMember = false">取消</el-button>
        <el-button type="primary" :loading="memberAdding" @click="handleAddMember">添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { ArrowLeft, Plus, Delete, Remove, Rank } from '@element-plus/icons-vue'
import { useBoardStore } from '@/stores/board'
import { updateBoard } from '@/api/boards'
import { createColumn, updateColumn, deleteColumn } from '@/api/columns'
import { fetchUserList } from '@/api/users'
import type { UserVO } from '@/types/user'

const router = useRouter()
const route = useRoute()
const boardStore = useBoardStore()

const board = computed(() => boardStore.currentBoard)

// Basic info
const editName = ref('')
const editDescription = ref('')
const editTeam = ref('')
const savingBasic = ref(false)

// Add column
const showAddColumn = ref(false)
const colCreating = ref(false)
const colFormRef = ref<FormInstance>()
const colForm = ref({ name: '', wipLimit: 0, color: '#E2E8F0' })
const colRules = { name: [{ required: true, message: '请输入列名', trigger: 'blur' }] }

// Add member
const showAddMember = ref(false)
const memberAdding = ref(false)
const newMemberUserId = ref('')
const newMemberRole = ref<'admin' | 'member' | 'viewer'>('member')
const availableUsers = ref<UserVO[]>([])

function loadBoard() {
  const id = route.params.id as string
  if (id) {
    boardStore.fetchBoard(id).then(() => {
      if (board.value) {
        editName.value = board.value.name
        editDescription.value = board.value.description || ''
        editTeam.value = board.value.ownerTeam || ''
      }
    })
  }
}

async function saveBasicInfo() {
  if (!board.value) return
  savingBasic.value = true
  const res = await updateBoard(board.value.id, {
    name: editName.value,
    description: editDescription.value || undefined,
    ownerTeam: editTeam.value || undefined,
  })
  savingBasic.value = false
  if (res.code === 200) {
    board.value.name = editName.value
    board.value.description = editDescription.value
    board.value.ownerTeam = editTeam.value
    ElMessage.success('基本信息已保存')
  }
}

async function saveColumn(col: { id: string; name: string; color?: string; wipLimit?: number }) {
  if (!col.name.trim()) {
    ElMessage.warning('列名不能为空')
    return
  }
  const res = await updateColumn(col.id, {
    name: col.name,
    color: col.color,
    wipLimit: col.wipLimit || undefined,
  })
  if (res.code === 200) {
    ElMessage.success(`列「${col.name}」已保存`)
  }
}

async function handleDeleteColumn(columnId: string, index: number) {
  const res = await deleteColumn(columnId)
  if (res.code === 200 && board.value) {
    board.value.columns.splice(index, 1)
    ElMessage.success('列已删除')
  }
}

async function handleAddColumn() {
  if (!board.value) return
  const valid = await colFormRef.value?.validate().catch(() => false)
  if (!valid) return

  colCreating.value = true
  const res = await createColumn({
    boardId: board.value.id,
    name: colForm.value.name,
    color: colForm.value.color,
    wipLimit: colForm.value.wipLimit > 0 ? colForm.value.wipLimit : undefined,
  })
  colCreating.value = false
  if (res.code === 200) {
    board.value.columns.push(res.data)
    ElMessage.success('列已添加')
    showAddColumn.value = false
    colForm.value = { name: '', wipLimit: 0, color: '#E2E8F0' }
  }
}

async function handleAddMember() {
  if (!newMemberUserId.value || !board.value) return
  memberAdding.value = true
  const user = availableUsers.value.find(u => u.id === newMemberUserId.value)
  if (user) {
    board.value.members.push({
      userId: user.id,
      user,
      role: newMemberRole.value,
      joinedAt: new Date().toISOString(),
    })
    ElMessage.success('成员已添加')
    showAddMember.value = false
    newMemberUserId.value = ''
    newMemberRole.value = 'member'
  }
  memberAdding.value = false
}

function saveMemberRole(member: { userId: string; role: string }) {
  ElMessage.success(`成员角色已更新为「${member.role === 'admin' ? '管理员' : member.role === 'member' ? '成员' : '观察者'}」`)
}

function removeMember(userId: string) {
  if (!board.value) return
  board.value.members = board.value.members.filter(m => m.userId !== userId)
  ElMessage.success('成员已移除')
}

function formatDateTime(dateStr: string): string {
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${(d.getMonth() + 1).toString().padStart(2, '0')}-${d.getDate().toString().padStart(2, '0')}`
}

onMounted(async () => {
  loadBoard()
  const res = await fetchUserList()
  if (res.code === 200) availableUsers.value = res.data
})
</script>

<style scoped>
.settings-page {
  height: 100%;
  overflow-y: auto;
  padding: 24px 32px;
  background: #f8fafc;
}
.loading-state {
  background: #fff;
  padding: 24px;
  border-radius: 8px;
}
.settings-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
}
.settings-header h1 {
  font-size: 22px;
  font-weight: 600;
  color: #1e293b;
}
.settings-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 800px;
}
.settings-card {
  border-radius: 10px;
}
.card-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.column-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.column-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: #f8fafc;
  border-radius: 8px;
}
.column-drag-handle {
  cursor: grab;
  color: #94a3b8;
}
.column-info {
  flex: 1;
}
.column-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.column-options {
  display: flex;
  align-items: center;
  gap: 8px;
}
.option-label {
  font-size: 13px;
  color: #64748b;
}
.column-actions {
  display: flex;
  gap: 4px;
}
.member-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}
.member-name {
  font-size: 13px;
  font-weight: 500;
}
.member-email {
  font-size: 11px;
  color: #94a3b8;
}
.text-muted {
  color: #94a3b8;
  font-size: 12px;
}
</style>
