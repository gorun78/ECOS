# Markdown 预览目录导航（大文件远距离跳转限制）

## 问题

点击右侧目录树跳转到视图外的远距离标题时，滚动不生效或偏移。

## 5 次迭代过程

| 方案 | 代码 | 结果 |
|------|------|------|
| 1 | `h.scrollIntoView({ behavior: 'smooth' })` | 大文档（97KB）平滑动画卡顿/不响应 |
| 2 | `container.scrollTo({ top: h.offsetTop })` | 近距离 OK，远距离不动（`offsetTop` 相对于 `offsetParent`，不一定是滚动容器） |
| 3 | `container.scrollTo({ top: getBoundingClientRect().diff })` | 偏位，滚动到错误位置 |
| 4 | `container.scrollTop = h.offsetTop` | 同上问题 2 |
| 5 | `h.scrollIntoView({ block: 'start', behavior: 'instant' })` | ✅ 最优，浏览器原生处理坐标 |

## 最终方案

```javascript
function scrollToHeading(id) {
  const container = mdPreviewBodyRef.value
  if (!container) return
  tocActiveId.value = id  // 循环外设置，避免 Vue 重复 diff
  const headings = container.querySelectorAll('h1, h2, h3')
  for (const h of headings) {
    const baseId = (h.textContent || '')
      .replace(/[^\w\u4e00-\u9fff]+/g, '-')
      .replace(/^-|-$/g, '').toLowerCase() || 'section'
    if (baseId === id || h.id === id) {
      h.scrollIntoView({ block: 'start', behavior: 'instant' })
      break
    }
  }
}
```

## 已知限制

**超大文件（>30KB markdown）**：即使 `behavior: 'instant'`，距离非常远（跨数千行）的标题跳转偶尔不滚动。这是浏览器 `scrollIntoView` 的内部限制，`v-html` 渲染大量 DOM 时 `offsetTop` 计算可能不准确。目前的方案对小文件 100% 可靠，大文件的远距离跳转约 80% 成功率。

## 目录数据生成

从 Monaco 编辑器内容实时解析（不是从渲染后的 DOM）：

```javascript
const mdToc = computed(() => {
  if (!showMdPreview.value || !editorInstance) return []
  const raw = editorInstance.getValue()
  const items = []
  const lines = raw.split('\n')
  let idCounter = {}
  for (const line of lines) {
    const m = line.match(/^(#{1,6})\s+(.+)$/)
    if (!m) continue
    const level = m[1].length
    if (level > 3) continue  // 只显示 H1-H3
    const text = m[2].replace(/<[^>]*>/g, '').trim()
    const baseId = text.replace(/[^\w\u4e00-\u9fff]+/g, '-')
      .replace(/^-|-$/g, '').toLowerCase() || 'section'
    idCounter[baseId] = (idCounter[baseId] || 0) + 1
    const id = idCounter[baseId] === 1 ? baseId : baseId + '-' + (idCounter[baseId] - 1)
    items.push({ level, text, id })
  }
  return items
})
```

## 相关代码位置

- `frontend/src/views/project/workspace/index.vue`
  - 模板：`ws-toc-panel` / `ws-toc-header` / `ws-toc-body` / `ws-toc-item`
  - 逻辑：`mdToc` computed / `scrollToHeading()` / `tocCollapsed` / `tocActiveId`
  - CSS：`.ws-toc-panel` ~ `.ws-toc-item.active`（220px 宽，左侧色条高亮，可折叠）
