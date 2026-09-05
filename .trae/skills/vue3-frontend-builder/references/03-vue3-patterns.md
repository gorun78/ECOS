# Vue3 开发模式与最佳实践

## Composition API 模式

### `<script setup>` 语法

```vue
<script setup lang="ts">
// 导入
import { ref, computed, watch } from 'vue'
import type { PropType } from 'vue'

// Props 定义 - 使用 withDefaults
interface Props {
  title: string
  count?: number
  items?: string[]
  onChange?: (value: string) => void
  variant?: 'primary' | 'secondary'
}

const props = withDefaults(defineProps<Props>(), {
  count: 0,
  items: () => [],
  variant: 'primary'
})

// Emits 定义
const emit = defineEmits<{
  (e: 'update:title', value: string): void
  (e: 'click', id: number): void
}>()

// Reactive state
const isOpen = ref(false)
const inputValue = ref('')

// Computed
const hasItems = computed(() => props.items.length > 0)
const upperTitle = computed(() => props.title.toUpperCase())

// Watch
watch(() => props.count, (newVal, oldVal) => {
  console.log(`count changed from ${oldVal} to ${newVal}`)
})

// Methods
const toggle = () => {
  isOpen.value = !isOpen.value
}

const handleInput = (e: Event) => {
  const target = e.target as HTMLInputElement
  inputValue.value = target.value
  props.onChange?.(target.value)
}

// 生命周期
import { onMounted, onUnmounted } from 'vue'

onMounted(() => {
  console.log('Component mounted')
})

onUnmounted(() => {
  console.log('Component unmounted')
})

// Expose
defineExpose({
  open: toggle,
  close: () => { isOpen.value = false }
})
</script>

<template>
  <div class="container">
    <h1>{{ upperTitle }}</h1>
    <input
      :value="inputValue"
      @input="handleInput"
    />
  </div>
</template>

<style scoped>
.container {
  padding: 16px;
}
</style>
```

## Pinia Store 模式

### Store 定义

```typescript
// stores/useUserStore.ts
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { User, UserRole } from '@/types'

export const useUserStore = defineStore('user', () => {
  // State
  const user = ref<User | null>(null)
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  // Getters
  const isLoggedIn = computed(() => user.value !== null)
  const userName = computed(() => user.value?.name ?? 'Guest')
  const isAdmin = computed(() => user.value?.role === 'admin')

  // Actions
  async function fetchUser(userId: string) {
    isLoading.value = true
    error.value = null
    try {
      const response = await api.getUser(userId)
      user.value = response.data
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Unknown error'
    } finally {
      isLoading.value = false
    }
  }

  function logout() {
    user.value = null
  }

  return {
    // State
    user,
    isLoading,
    error,
    // Getters
    isLoggedIn,
    userName,
    isAdmin,
    // Actions
    fetchUser,
    logout
  }
})
```

### Store 使用

```vue
<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { useUserStore } from '@/stores/useUserStore'

const userStore = useUserStore()

// 使用 storeToRefs 保持响应性
const { user, isLoggedIn, isLoading } = storeToRefs(userStore)

// 直接调用 actions
const { fetchUser, logout } = userStore
</script>
```

## Vue Router 模式

### 路由配置

```typescript
// router/index.ts
import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'Home',
    component: () => import('@/pages/HomePage.vue')
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/pages/DashboardPage.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/users/:id',
    name: 'UserProfile',
    component: () => import('@/pages/UserProfilePage.vue'),
    props: true
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 导航守卫
router.beforeEach((to, from, next) => {
  const isAuthenticated = /* 从 store 获取 */ false

  if (to.meta.requiresAuth && !isAuthenticated) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
  } else {
    next()
  }
})

export default router
```

### 页面组件

```vue
<!-- pages/DashboardPage.vue -->
<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

// 获取路由参数
const userId = route.params.id

// 编程式导航
const goBack = () => router.back()
</script>

<template>
  <div>
    <h1>Dashboard</h1>
    <p>User ID: {{ userId }}</p>
    <button @click="goBack">Go Back</button>
  </div>
</template>
```

## Composables 模式

### 常用 Composables

```typescript
// composables/useLocalStorage.ts
import { ref, watch } from 'vue'

export function useLocalStorage<T>(key: string, defaultValue: T) {
  const stored = localStorage.getItem(key)
  const data = ref<T>(stored ? JSON.parse(stored) : defaultValue)

  watch(data, (newVal) => {
    if (newVal === null || newVal === undefined) {
      localStorage.removeItem(key)
    } else {
      localStorage.setItem(key, JSON.stringify(newVal))
    }
  }, { deep: true })

  return data
}

// composables/useDebounce.ts
import { ref, watch } from 'vue'
import type { Ref } from 'vue'

export function useDebounce<T>(value: Ref<T>, delay: number = 300) {
  const debouncedValue = ref<T>(value.value) as Ref<T>
  let timeout: ReturnType<typeof setTimeout>

  watch(value, (newVal) => {
    clearTimeout(timeout)
    timeout = setTimeout(() => {
      debouncedValue.value = newVal
    }, delay)
  })

  return debouncedValue
}

// composables/useFetch.ts
import { ref, watchEffect } from 'vue'

export function useFetch<T>(url: string) {
  const data = ref<T | null>(null)
  const error = ref<Error | null>(null)
  const isLoading = ref(false)

  const fetchData = async () => {
    isLoading.value = true
    error.value = null
    try {
      const response = await fetch(url)
      if (!response.ok) throw new Error('Fetch failed')
      data.value = await response.json()
    } catch (e) {
      error.value = e instanceof Error ? e : new Error('Unknown error')
    } finally {
      isLoading.value = false
    }
  }

  watchEffect(() => {
    if (url) fetchData()
  })

  return { data, error, isLoading, refetch: fetchData }
}
```

## 组件通信模式

### Props / Emits

```vue
<!-- Parent.vue -->
<script setup lang="ts">
import { ref } from 'vue'
import Child from './Child.vue'

const message = ref('Hello')
const handleUpdate = (newValue: string) => {
  console.log('Updated:', newValue)
}
</script>

<template>
  <Child
    v-model:message="message"
    @update="handleUpdate"
  />
</template>

<!-- Child.vue -->
<script setup lang="ts">
const props = defineProps<{
  message: string
}>()

const emit = defineEmits<{
  (e: 'update:message', value: string): void
  (e: 'update', value: string): void
}>()

const updateMessage = (newVal: string) => {
  emit('update:message', newVal)
  emit('update', newVal)
}
</script>
```

### Provide / Inject

```typescript
// Parent.vue
import { provide, ref } from 'vue'

const theme = ref('light')
provide('theme', theme)
provide('updateTheme', (newTheme: string) => {
  theme.value = newTheme
})

// Child.vue
import { inject } from 'vue'

const theme = inject('theme')
const updateTheme = inject('updateTheme')
```

### Event Bus (替代方案)

```typescript
// utils/bus.ts
import mitt from 'mitt'

export const bus = mitt()

// 使用
import { bus } from '@/utils/bus'

// 监听
bus.on('open-modal', (data) => {
  console.log('Modal opened:', data)
})

// 触发
bus.emit('open-modal', { id: 1 })
```

## TypeScript 类型模式

### 组件类型定义

```typescript
// types/components.ts

// Props 类型
export interface ButtonProps {
  variant?: 'primary' | 'secondary' | 'ghost'
  size?: 'sm' | 'md' | 'lg'
  disabled?: boolean
  loading?: boolean
  icon?: string
}

// Emits 类型
export interface ButtonEmits {
  (e: 'click', event: MouseEvent): void
  (e: 'update:modelValue', value: boolean): void
}

// Expose 类型
export interface ButtonExpose {
  focus: () => void
  blur: () => void
}

// 组合类型
export interface ComponentPublicInstance {
  $el: HTMLElement
  $props: Record<string, unknown>
}
```

### API 响应类型

```typescript
// types/api.ts

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface PaginatedResponse<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
}

export interface User {
  id: string
  name: string
  email: string
  avatar?: string
  createdAt: string
}
```

## 错误处理模式

### 组件内错误处理

```vue
<script setup lang="ts">
import { ref, onErrorCaptured } from 'vue'

const error = ref<Error | null>(null)

onErrorCaptured((err, instance, info) => {
  error.value = err
  console.error('Error captured:', err, info)
  return false // 阻止错误继续传播
})
</script>
```

### 全局错误处理

```typescript
// main.ts
import { createApp } from 'vue'
import App from './App.vue'

const app = createApp(App)

app.config.errorHandler = (err, instance, info) => {
  console.error('Global error:', err)
  console.error('Component:', instance)
  console.error('Info:', info)
}

app.config.warnHandler = (msg, instance, trace) => {
  console.warn('Warning:', msg)
  console.warn('Trace:', trace)
}
```

## 性能优化模式

### 异步组件

```vue
<script setup lang="ts">
import { defineAsyncComponent } from 'vue'

// 懒加载组件
const HeavyChart = defineAsyncComponent(() =>
  import('./components/HeavyChart.vue')
)

// 带加载状态
const AsyncModal = defineAsyncComponent({
  loader: () => import('./Modal.vue'),
  loadingComponent: LoadingSpinner,
  errorComponent: ErrorBoundary,
  delay: 200,
  timeout: 3000
})
</script>
```

### v-memo 用法

```vue
<template>
  <!-- 只在 listCells 变化时重新渲染 -->
  <div v-for="item in listItems" :key="item.id" v-memo="[item.id, item.name]">
    <ComplexComponent :item="item" />
  </div>
</template>
```

### KeepAlive 缓存

```vue
<template>
  <KeepAlive include="HomePage,AboutPage">
    <router-view />
  </KeepAlive>

  <!-- 配合 max 控制缓存数量 -->
  <KeepAlive :max="10">
    <component :is="currentComponent" />
  </KeepAlive>
</template>
```