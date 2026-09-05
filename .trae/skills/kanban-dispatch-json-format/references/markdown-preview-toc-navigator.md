# Markdown 预览 TOC 目录导航实现

## 功能概述

在 Markdown 预览面板右侧添加目录导航，自动提取 H1/H2/H3 标题生成可折叠树形结构，点击跳转，当前标题高亮。

## 目录生成（computed）

从编辑器文本解析标题行，限制到 H3：

```javascript
const mdToc = computed(() => {
  const raw = editorInstance.getValue()
  const items = [], idCounter = {}
  for (const line of raw.split('\n')) {
    const m = line.match(/^(#{1,6})\s+(.+)$/)
    if (!m) continue
    const level = m[1].length
    if (level > 3) continue
    const text = m[2].trim()
    const baseId = text.replace(/[^\w\u4e00-\u9fff]+/g, '-').toLowerCase() || 'section'
    idCounter[baseId] = (idCounter[baseId] || 0) + 1
    const id = idCounter[baseId] === 1 ? baseId : baseId + '-' + (idCounter[baseId] - 1)
    items.push({ level, text, id })
  }
  return items
})
```

> ⚠️ 在 .vue 单文件组件的 `<script>` 块中，不要在模板字符串中直接写正则 —— Vite/Vue 编译器对反斜杠有额外转义处理，容易出错。

## 滚动到标题（3 轮迭代）

### ❌ 尝试 1：scrollIntoView({ behavior: 'smooth' })

长文档 smooth 动画非常卡顿（浏览器每帧都在计算过渡），用户体验极差。

### ❌ 尝试 2：getBoundingClientRect() 差值计算

```javascript
const targetTop = h.getBoundingClientRect().top - containerTop + container.scrollTop
container.scrollTo({ top: targetTop - 16, behavior: 'instant' })
```

`getBoundingClientRect()` 获取的是视口坐标，容器 `overflow: auto` 时 `container.getBoundingClientRect().top` 和 `.scrollTop` 不在同一坐标系。`+ container.scrollTop` 重复计算导致远距离章节偏很多。

### ✅ 尝试 3：直接用 offsetTop

```javascript
container.scrollTop = h.offsetTop - 16
```

`offsetTop` 返回元素相对于最近的 `offsetParent`（即 `overflow: auto` 的父容器）的垂直偏移。这是滚动目标的标准值，不需要任何坐标转换。

### 最终实现

```javascript
function scrollToHeading(id) {
  const container = mdPreviewBodyRef.value
  if (!container) return
  tocActiveId.value = id  // 在循环外设置，避免 Vue 响应式在循环中重复触发

  const headings = container.querySelectorAll('h1, h2, h3')
  for (const h of headings) {
    const baseId = (h.textContent || '')
      .replace(/[^\w\u4e00-\u9fff]+/g, '-')
      .replace(/^-|-$/g, '')
      .toLowerCase() || 'section'
    if (baseId === id || h.id === id) {
      container.scrollTop = h.offsetTop - 16
      break
    }
  }
}
```

## Vue 模板结构

```html
<div ref="mdPreviewBodyRef" v-html="renderedMdContent" style="flex:1;overflow-y:auto"></div>

<div class="ws-toc-panel" :class="{ collapsed: tocCollapsed }">
  <div class="ws-toc-header" @click="tocCollapsed = !tocCollapsed">
    <span>目录</span>
    <svg>...</svg>
  </div>
  <div v-show="!tocCollapsed" class="ws-toc-body">
    <div v-for="item in mdToc" :key="item.id"
      class="ws-toc-item"
      :style="{ paddingLeft: (item.level - 1) * 14 + 8 + 'px' }"
      :class="{ active: item.id === tocActiveId }"
      @click="scrollToHeading(item.id)">
      {{ item.text }}
    </div>
  </div>
</div>
```

## 关键数值

- 面板宽度: 220px（折叠后 36px）
- 层级缩进: `(level - 1) * 14 + 8` px
- 标题级别限制: 只到 H3
- 滚动偏移: -16px（留一点头部空间）

## 涉及文件

`frontend/src/views/project/workspace/index.vue` — 模板 + script + CSS 三处改动。
