# vue3-frontend-builder — 拆分出的模板/示例

> 由 SKILL.md 拆出，SKILL.md 对应位置留有指针。生成相关制品前先读本文件。

## 块 1（原 SKILL.md L289-324）

```markdown
# 设计稿分析报告 - [页面名称]

## 分析信息
- 设计稿目录：[路径]
- 分析时间：[YYYY-MM-DD]
- 分析文件：[设计稿文件列表]

## 1. 布局结构分析

| 元素 | 布局方式 | 设计稿来源 | 代码段 |
|------|----------|-----------|--------|
| 顶部导航 | flex, justify-between | `dashboard.html#L10` | `<header class="flex justify-between">` |
| 内容区域 | grid, 2 cols | `dashboard.html#L25` | `<main class="grid grid-cols-2">` |

## 2. 配色方案分析

| 元素 | 色值 | 用途 | 设计稿来源 | 代码段 |
|------|------|------|-----------|--------|
| 主色 | #3370FF | 按钮/链接 | `dashboard.html#L42` | `color: #3370FF` |
| 背景 | #F8FAFC | 页面背景 | `dashboard.html#L5` | `background: #F8FAFC` |

## 3. 设计 Token 冲突检测

| 组件 | 组件使用色值 | 主题变量定义 | 冲突 | 建议 |
|------|-------------|-------------|------|------|
| Button | #3370FF | --primary: #030213 | ❌ | 使用组件色值 #3370FF |

## 4. 排版分析
## 5. 间距分析
## 6. 组件尺寸分析
## 7. 交互状态分析
## 8. 响应式分析
## 9. 图标与资源分析
## 10. 动画与过渡分析
```

## 块 2（原 SKILL.md L428-478）

```vue
<!-- 标准组件结构 -->
<template>
  <div class="component-wrapper">
    <!-- 组件内容 -->
  </div>
</template>

<script setup lang="ts">
// Props 定义
interface Props {
  title?: string
  size?: 'sm' | 'md' | 'lg'
  disabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  title: '',
  size: 'md',
  disabled: false
})

// Emits 定义
const emit = defineEmits<{
  (e: 'click', value: MouseEvent): void
  (e: 'update:modelValue', value: boolean): void
}>()

// State
const isOpen = ref(false)

// Computed
const classes = computed(() => [
  'base-class',
  `size-${props.size}`,
  { 'is-disabled': props.disabled }
])

// Expose
defineExpose({
  open: () => { isOpen.value = true },
  close: () => { isOpen.value = false }
})
</script>

<style scoped>
.component-wrapper {
  /* 使用 Tailwind 或自定义样式 */
}
</style>
```
