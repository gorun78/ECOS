<template>
  <el-container class="app-layout">
    <!-- Sidebar -->
    <el-aside :width="sidebarWidth" class="app-sidebar">
      <div class="sidebar-header" @click="goDashboard">
        <el-icon :size="24"><Monitor /></el-icon>
        <span class="sidebar-title">ECOS Kanban</span>
      </div>

      <el-menu
        :default-active="activeMenu"
        class="sidebar-menu"
        background-color="#fff"
        text-color="#475569"
        active-text-color="#3b82f6"
        router
      >
        <el-menu-item index="/dashboard">
          <el-icon><Grid /></el-icon>
          <span>我的看板</span>
        </el-menu-item>
      </el-menu>

      <div class="sidebar-footer">
        <div class="user-info">
          <el-avatar :size="32">{{ userInitial }}</el-avatar>
          <div class="user-detail">
            <div class="user-name">{{ authStore.currentUser?.realName || '用户' }}</div>
            <div class="user-role">{{ roleLabel }}</div>
          </div>
        </div>
        <el-button text circle @click="handleLogout">
          <el-icon><SwitchButton /></el-icon>
        </el-button>
      </div>
    </el-aside>

    <!-- Main content -->
    <el-container class="main-container">
      <router-view />
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

const sidebarWidth = '220px'
const activeMenu = computed(() => route.path)
const userInitial = computed(() => authStore.currentUser?.realName?.charAt(0) || '?')

const roleLabel = computed(() => {
  const roleMap: Record<string, string> = {
    sys_admin: '系统管理员',
    board_admin: '看板管理员',
    member: '成员',
    viewer: '观察者',
  }
  return roleMap[authStore.currentUser?.role || 'member'] || '成员'
})

function goDashboard() {
  router.push('/dashboard')
}

function handleLogout() {
  authStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.app-layout {
  height: 100vh;
  background: #f8fafc;
}
.app-sidebar {
  background: #fff;
  border-right: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.sidebar-header {
  height: 56px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 20px;
  border-bottom: 1px solid #e2e8f0;
  cursor: pointer;
  color: #1e293b;
  font-weight: 600;
  font-size: 16px;
}
.sidebar-menu {
  flex: 1;
  border-right: none;
  padding-top: 8px;
}
.sidebar-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-top: 1px solid #e2e8f0;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  overflow: hidden;
}
.user-detail {
  line-height: 1.3;
}
.user-name {
  font-size: 13px;
  font-weight: 500;
  color: #1e293b;
}
.user-role {
  font-size: 11px;
  color: #94a3b8;
}
.main-container {
  height: 100vh;
  overflow: hidden;
}
</style>
