# Markdown TOC 目录导航滚动修复实录

## 问题演变

用户反馈：系统详细设计.md（360KB，2829行）的目录点击后，部分章节可以跳转，但后面的章节（如"项目管理"之后）完全不跳——滚动条不动。

## 尝试过的方案

### 方案1：`scrollIntoView({ behavior: 'smooth' })`
- 结果：**卡顿**。大文档的 smooth 动画极慢，长距跳转到一半就卡住了。

### 方案2：`container.scrollTo({ top: ..., behavior: 'instant' })`  
- 用 `getBoundingClientRect` 计算相对位置
- 结果：**近距离可以，远距离不跳**。`offsetTop` 对于 `v-html` 渲染的元素不可靠。

### 方案3：`container.scrollTop = h.offsetTop - 16`
- 结果：**近距离准确，远距离偏很多**。`offsetTop` 依赖于 `offsetParent`，而 `v-html` 渲染的 DOM 结构中 `offsetParent` 可能不是滚动容器本身。

### 方案4：`h.scrollIntoView({ block: 'start', behavior: 'instant' })`
- 结果：**小文件可以，大文件仍失败**。原因是大文件 `v-html` 渲染未完成时 `querySelectorAll` 找不到元素。

## 最终方案（方案5）

```javascript
function scrollToHeading(id) {
  const container = mdPreviewBodyRef.value
  if (!container) return
  tocActiveId.value = id
  
  // 🔴 关键：大文件需要等 Vue 渲染/nextTick 完成后再查找 DOM
  nextTick(() => {
    const headings = container.querySelectorAll('h1, h2, h3')
    for (const h of headings) {
      // textContent 已自动解码 HTML，去掉残留的 <em>/<code> 子标签
      const plain = (h.textContent || '').trim().replace(/<[^>]*>/g, '')
      const baseId = plain
        .replace(/[^\w\u4e00-\u9fff]+/g, '-')
        .replace(/^-|-$/g, '')
        .toLowerCase() || 'section'
      
      if (baseId === id || h.id === id) {
        // 相对位置差值 + 容器当前滚动偏移 = 绝对滚动位置
        const top = h.getBoundingClientRect().top 
                  - container.getBoundingClientRect().top 
                  + container.scrollTop
        container.scrollTop = top - 20
        return
      }
    }
  })
}
```

## 为什么这个方案有效

| 要素 | 作用 |
|------|------|
| `nextTick` | 确保 `v-html` 大文档完全渲染到 DOM 后再 `querySelectorAll` |
| `textContent` | 自动提取纯文本（marked 生成的标题可能内含 `<em>`, `<code>` 标签） |
| `.replace(/<[^>]*>/g, '')` | 去掉 `textContent` 可能残留的 HTML 标签 |
| `getBoundingClientRect` 差值 | 计算元素相对于滚动容器的位置，不受 `offsetParent` 干扰 |
| `+ container.scrollTop` | 补偿当前已滚动的距离 |
| `container.scrollTop = ...` | 直接赋值，浏览器不执行任何动画 |

## 关键教训

1. `v-html` 是大文件性能瓶颈——marked.parse 返回的 3000 行 HTML 不是即时出现在 DOM 中的，Vue 需要一个 tick 来把 `innerHTML` 注入并让浏览器完成布局。
2. `scrollIntoView` 对大文档不可靠——浏览器行为因文件大小而异。
3. 直接用 `scrollTop` 赋值是最快最可靠的方式，但需要准确的坐标计算。
