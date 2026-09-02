# 组件分析规范

## 组件抽象分析

### 核心流程

```
输入：原型截图 + UI 设计规范
         ↓
Step 1: 视觉元素扫描 → 提取所有 UI 元素
         ↓
Step 2: 模式识别 → 找出相似元素组
         ↓
Step 3: 复用指数计算 → RI = 页面数 × 通用性 × 变体系数
         ↓
Step 4: 抽象决策 → 原子/分子/有机/页面级
         ↓
Step 5: 组件依赖图 → 输出开发优先级
```

### 复用指数判定

| 复用指数 | 决策 | 目录 |
|----------|------|------|
| RI ≥ 9 | 全局抽象 | `components/atoms/` 或 `components/molecules/` |
| RI 5-8 | 可选抽象 | 权衡成本后决定 |
| RI < 5 | 页面级 | `components/pages/` |

## 组件分类

| 类型 | 定义 | 示例 |
|------|------|------|
| 原子 (Atom) | 不可再分的基础 UI 元素 | Button, Input, Icon, Badge |
| 分子 (Molecule) | 原子组件的简单组合 | SearchBar, DataCard, Avatar |
| 有机 (Organism) | 多个分子/原子组成的完整功能单元 | Header, DataTable, FilterPanel |
| 页面 (Page) | 完整页面 | LoginPage, DashboardPage |

## 组件结构模板

```vue
<template>
  <div class="component-name" :class="classes">
    <!-- 组件内容 -->
  </div>
</template>

<script setup lang="ts" name="ComponentName">
/**
 * ComponentName - 组件描述
 */
import { ref, computed } from 'vue'

// Props
interface Props {
  title?: string
  size?: 'sm' | 'md' | 'lg'
  variant?: 'primary' | 'secondary'
}
const props = withDefaults(defineProps<Props>(), {
  title: '',
  size: 'md',
  variant: 'primary'
})

// Emits
const emit = defineEmits<{
  (e: 'click', event: MouseEvent): void
}>()

// State
const isOpen = ref(false)

// Computed
const classes = computed(() => [
  'base',
  `size-${props.size}`,
  `variant-${props.variant}`
])

// Methods
const handleClick = () => emit('click', event)

// Expose
defineExpose({ open: () => { isOpen.value = true } })
</script>

<style scoped>
.base { /* 基础样式 */ }
</style>
```

## TypeScript 类型规范

### Props 定义

```typescript
interface Props {
  title: string
  count?: number
  items?: string[]
}
const props = withDefaults(defineProps<Props>(), {
  count: 0,
  items: () => []
})
```

### Emit 定义

```typescript
const emit = defineEmits<{
  (e: 'update:value', value: string): void
  (e: 'click'): void
}>()
```

### 组合式函数

```typescript
// usePagination.ts
interface UsePaginationOptions<T> {
  initialPage?: number
  total: Ref<number>
}
export function usePagination<T>(options: UsePaginationOptions<T>) {
  const currentPage = ref(options.initialPage ?? 1)
  const totalPages = computed(() => Math.ceil(options.total.value / 10))
  return { currentPage, totalPages }
}
```

## 组件卡片模板

```markdown
## 组件名称

### 基本信息
- **类型**: 原子/分子/有机/页面
- **职责**: 该组件负责什么
- **复用指数**: X (出现Y个页面)

### Props 接口
| 属性 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| title | string | 否 | '' | 标题 |

### State
| 状态 | 类型 | 初始值 | 说明 |
|------|------|--------|------|
| isLoading | Ref<boolean> | false | 加载状态 |

### Emits
| 事件 | 参数 | 说明 |
|------|------|------|
| click | MouseEvent | 点击事件 |

### 样式参数（第二阶段填充）
- 颜色：待提取
- 字体：待提取
- 间距：待提取
```

## 优先级排序

```
Phase 1 - 全局原子组件
  1. BaseButton      (高复用，强依赖)
  2. BaseBadge
  3. BaseInput
  4. BaseIcon

Phase 2 - 领域分子组件
  5. SearchBar       (依赖 Phase 1)
  6. StatusTag

Phase 3 - 领域有机组件
  7. DataTable       (依赖 Phase 1+2)
  8. FilterPanel

Phase 4 - 页面组件
  9. DashboardPage   (依赖前期所有)
```

## 需求缺口处理

缺口标记格式：
```markdown
⚠️ [GAP-001]: PRD 未定义搜索历史最大条数，建议限制为 10 条，待用户确认
```

---
*本文档整合自 requirement-analysis.md 和 component-standards.md*