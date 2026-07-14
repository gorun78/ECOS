<template>
  <div class="dashboard-page">
    <!-- Header -->
    <div class="dashboard-header">
      <div>
        <h1>我的看板</h1>
        <p class="text-muted">管理和跟踪你的工作项</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="showCreateDialog = true">
        新建看板
      </el-button>
    </div>

    <!-- Loading -->
    <div v-if="boardStore.loading" class="dashboard-loading">
      <el-skeleton :rows="3" animated />
    </div>

    <!-- Error -->
    <el-result
      v-else-if="boardStore.error"
      icon="error"
      title="加载失败"
      :sub-title="boardStore.error"
    >
      <template #extra>
        <el-button type="primary" @click="boardStore.fetchBoards()">重新加载</el-button>
      </template>
    </el-result>

    <!-- Empty -->
    <el-empty v-else-if="boardStore.boardList.length === 0" description="暂无看板">
      <template #description>
        <p>还没有看板，点击下方按钮创建第一个看板</p>
      </template>
      <el-button type="primary" :icon="Plus" @click="showCreateDialog = true">
        新建看板
      </el-button>
    </el-empty>

    <!-- Board Grid -->
    <div v-else class="board-grid">
      <div
        v-for="board in boardStore.boardList"
        :key="board.id"
        class="board-card"
        @click="router.push(`/board/${board.id}`)"
      >
        <div class="board-card-header" :style="{ background: board.columns[0]?.color || '#E2E8F0' }">
          <span class="board-card-title">{{ board.name }}</span>
        </div>
        <div class="board-card-body">
          <p class="board-desc">{{ board.description || '暂无描述' }}</p>
          <div class="board-meta">
            <span>
              <el-icon :size="14"><Collection /></el-icon>
              {{ board.columns.length }} 列
            </span>
            <span>
              <el-icon :size="14"><Document /></el-icon>
              {{ totalCards(board) }} 卡片
            </span>
          </div>
          <div class="board-team" v-if="board.ownerTeam">
            <el-tag size="small" type="info">{{ board.ownerTeam }}</el-tag>
          </div>
        </div>
      </div>
    </div>

    <!-- Create Board Dialog -->
    <el-dialog v-model="showCreateDialog" title="新建看板" width="480px">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="80px">
        <el-form-item label="看板名称" prop="name">
          <el-input v-model="createForm.name" placeholder="如：供应商准入看板" maxlength="100" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="createForm.description" type="textarea" :rows="2" placeholder="看板用途描述" />
        </el-form-item>
        <el-form-item label="列模板">
          <el-radio-group v-model="createForm.template">
            <el-radio-button value="3col">3列</el-radio-button>
            <el-radio-button value="4col">4列</el-radio-button>
            <el-radio-button value="5col">5列</el-radio-button>
            <el-radio-button value="custom">自定义</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="所属团队">
          <el-input v-model="createForm.ownerTeam" placeholder="如：采购部" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Collection, Document } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { useBoardStore } from '@/stores/board'
import type { BoardVO } from '@/types/board'

const router = useRouter()
const boardStore = useBoardStore()
const showCreateDialog = ref(false)
const creating = ref(false)
const createFormRef = ref<FormInstance>()

const createForm = reactive({
  name: '',
  description: '',
  template: '4col' as '3col' | '4col' | '5col' | 'custom',
  ownerTeam: '',
})

const createRules = {
  name: [{ required: true, message: '请输入看板名称', trigger: 'blur' }],
}

function totalCards(board: BoardVO): number {
  return board.columns.reduce((sum, col) => sum + (col.cards?.length || 0), 0)
}

async function handleCreate() {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return

  creating.value = true
  const newBoard = await boardStore.createBoard({
    name: createForm.name,
    description: createForm.description || undefined,
    template: createForm.template,
    ownerTeam: createForm.ownerTeam || undefined,
  })
  creating.value = false

  if (newBoard) {
    showCreateDialog.value = false
    createForm.name = ''
    createForm.description = ''
    router.push(`/board/${newBoard.id}`)
  }
}

onMounted(() => {
  boardStore.fetchBoards()
})
</script>

<style scoped>
.dashboard-page {
  height: 100%;
  overflow-y: auto;
  padding: 24px 32px;
}
.dashboard-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}
.dashboard-header h1 {
  font-size: 22px;
  font-weight: 600;
  color: #1e293b;
}
.text-muted {
  color: #94a3b8;
  font-size: 14px;
  margin-top: 4px;
}
.dashboard-loading {
  padding: 24px;
  background: #fff;
  border-radius: 8px;
}
.board-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}
.board-card {
  background: #fff;
  border-radius: 10px;
  overflow: hidden;
  cursor: pointer;
  box-shadow: var(--card-shadow);
  transition: box-shadow 0.2s, transform 0.2s;
}
.board-card:hover {
  box-shadow: var(--card-shadow-hover);
  transform: translateY(-2px);
}
.board-card-header {
  padding: 20px 16px;
  min-height: 80px;
  display: flex;
  align-items: flex-end;
}
.board-card-title {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
}
.board-card-body {
  padding: 16px;
}
.board-desc {
  color: #64748b;
  font-size: 13px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: 12px;
}
.board-meta {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 8px;
}
.board-meta span {
  display: flex;
  align-items: center;
  gap: 4px;
}
</style>
