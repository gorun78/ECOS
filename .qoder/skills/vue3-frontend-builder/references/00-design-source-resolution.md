# 设计源解析

## 目的

在开始前端开发前，自动识别项目中的设计来源文件，建立原型页面与 UI 设计稿的映射关系，确保：
1. 有设计稿的页面使用精确视觉还原
2. 无设计稿的页面应用统一设计规范
3. 所有页面样式保持一致

## Phase 0 工作流程

### Step 1: 目录识别

#### 自动扫描

搜索项目目录，识别以下类型文件：

| 类型 | 文件特征 | 扫描模式 |
|------|----------|----------|
| UI 设计稿页面 | HTML 文件，包含设计系统 CSS 变量引用 | `**/pages/*.html`, `**/design/*.html`, `**/mockups/*.html` |
| 原型页面 | HTML 文件，包含交互逻辑或布局结构 | `**/prototype/*.html`, `**/原型/*.html`, `**/pages/*.html` |
| 设计规范文档 | `.md` 文件，描述颜色、字体、间距等 | `**/design*.md`, `**/规范*.md` |
| CSS 设计系统 | `.css` 文件，包含大量 CSS 自定义属性 | `**/*.css`（包含 `--color-`, `--font-`, `--space-`） |

#### 用户确认

如果自动扫描结果不明确，或存在多个候选目录，输出候选列表供用户确认：

```markdown
## 设计源识别结果

### 候选设计稿目录
1. `docs/01需求分析/02-UI设计/ai-native-platform/pages/` — 包含 12 个 HTML 文件
2. `docs/02设计阶段/02-03原型设计/` — 包含 8 个 HTML 文件

### 候选原型目录
1. `docs/01需求分析/01-软件开发工厂/原型页面文件/` — 包含 10 个 HTML 文件

### 候选设计规范
1. `docs/01需求分析/02-UI设计/ai-native-platform/design.md` — 设计规范文档
2. `docs/01需求分析/02-UI设计/ai-native-platform/colors_and_type.css` — CSS 设计系统

请选择或提供正确的目录路径：
- 设计稿目录：[用户输入]
- 原型目录：[用户输入]
- 设计规范文件：[用户输入]
- CSS 设计系统文件：[用户输入]
```

### Step 2: 文件匹配

#### 匹配规则

| 规则 | 优先级 | 说明 |
|------|--------|------|
| 精确匹配 | 1 | 文件名完全一致（如 `dashboard.html` ↔ `dashboard.html`） |
| 部分匹配 | 2 | 文件名包含关系（如 `project-overview.html` ↔ `project/overview.html`） |
| 名称相似 | 3 | 使用相似度算法匹配（如 `project_member.html` ↔ `project-members.html`） |
| 无匹配 | 4 | 仅原型页面存在，无对应设计稿 |

#### 名称相似度算法

```typescript
function calculateSimilarity(name1: string, name2: string): number {
  const normalized1 = name1.toLowerCase().replace(/[-_]/g, '')
  const normalized2 = name2.toLowerCase().replace(/[-_]/g, '')
  const maxLen = Math.max(normalized1.length, normalized2.length)
  let matches = 0
  for (let i = 0; i < Math.min(normalized1.length, normalized2.length); i++) {
    if (normalized1[i] === normalized2[i]) matches++
  }
  return matches / maxLen
}
```

相似度 ≥ 0.7 视为匹配成功。

### Step 3: 生成映射表

#### 映射表格式

```markdown
## 设计源映射表

| 原型页面 | 设计稿页面 | 匹配类型 | 优先级 | 还原策略 |
|----------|------------|----------|--------|----------|
| dashboard.html | ✅ dashboard.html | exact | design | 路径A：精确还原 |
| project-overview.html | ✅ project-overview.html | exact | design | 路径A：精确还原 |
| workspace.html | ✅ workspace.html | exact | design | 路径A：精确还原 |
| new-feature.html | ❌ 无 | none | prototype | 路径B：规范驱动 |
```

#### 映射类型说明

| 匹配类型 | 说明 | 还原策略 |
|----------|------|----------|
| `exact` | 精确匹配 | 路径A：以设计稿为基准 |
| `partial` | 部分匹配 | 路径A：以设计稿为基准 |
| `similar` | 名称相似 | 路径A：以设计稿为基准，需用户确认 |
| `none` | 无匹配 | 路径B：应用设计规范 |

### Step 4: 确认门

Phase 0 必须获得用户确认才能进入下一阶段，确认内容包括：

- [ ] 设计源目录已确认（设计稿目录、原型目录）
- [ ] 设计规范文件已确认（design.md、colors_and_type.css）
- [ ] 设计源映射表已确认
- [ ] 每个页面的还原策略已确认
- [ ] 无匹配页面的处理方式已确认

## 还原策略

### 路径A：有设计稿（精确还原）

当原型页面匹配到设计稿页面时，采用精确还原策略：

```
输入：
  - 设计稿页面 HTML（视觉基准）
  - 原型页面 HTML（功能/布局参考）
  - design.md（设计规范）
  - colors_and_type.css（CSS 变量）

任务：
  1. 分析设计稿页面的 DOM 结构和样式类名
  2. 提取布局结构、组件层次、交互状态
  3. 将设计稿中的样式映射到 Tailwind
  4. 生成 component-style-lock.json（精确像素值）

输出：
  - Tailwind 配置扩展（基于设计稿）
  - component-style-lock.json
```

#### 优先级规则

| 维度 | 优先级 | 说明 |
|------|--------|------|
| 视觉样式 | 设计稿 | 颜色、字体、间距、阴影、圆角 |
| 功能布局 | 原型页面 | 组件结构、交互逻辑、数据流向 |
| 命名规范 | 设计规范 | 类名、变量名、组件命名 |
| 冲突解决 | 用户确认 | 记录为需求缺口，提交用户确认 |

### 路径B：无设计稿（规范驱动）

当原型页面未匹配到设计稿页面时，采用规范驱动策略：

```
输入：
  - 原型页面 HTML（功能/布局参考）
  - design.md（设计规范）
  - colors_and_type.css（CSS 变量）

任务：
  1. 分析原型页面的 DOM 结构
  2. 直接引用 colors_and_type.css 中的 CSS 变量
  3. 使用 design.md 中的字体、间距、圆角等规范
  4. 将 CSS 变量映射到 Tailwind extend

输出：
  - Tailwind 配置扩展（基于 CSS 变量）
  - component-style-lock.json（使用规范值）
```

#### 规范引用规则

| 属性 | 引用来源 |
|------|----------|
| 颜色 | colors_and_type.css → Tailwind colors |
| 字体 | design.md → Tailwind fontFamily |
| 间距 | colors_and_type.css → Tailwind spacing |
| 圆角 | colors_and_type.css → Tailwind borderRadius |
| 阴影 | colors_and_type.css → Tailwind boxShadow |
| 布局 | design.md → 组件布局规范 |
| 交互状态 | design.md → hover/active/disabled 规范 |

## CSS 变量 → Tailwind 映射规则

### 颜色映射

```javascript
// tailwind.config.js
theme: {
  extend: {
    colors: {
      primary: {
        DEFAULT: '#6366F1',
        hover: '#818CF8',
        active: '#4F46E5',
        light: '#EEF2FF',
        border: '#C7D2FE'
      },
      success: '#10B981',
      warning: '#F59E0B',
      error: '#EF4444',
      info: '#3B82F6',
      purple: '#8B5CF6',
      cyan: '#06B6D4',
      orange: '#F97316',
      'text-primary': '#1E293B',
      'text-secondary': '#64748B',
      'text-tertiary': '#94A3B8',
      'bg-layout': '#F8FAFC',
      'bg-container': '#FFFFFF',
      border: '#E2E8F0',
      'border-hover': '#C7D2FE'
    }
  }
}
```

### 圆角映射

```javascript
borderRadius: {
  sm: '4px',
  md: '8px',
  lg: '12px',
  xl: '16px',
  full: '9999px'
}
```

### 阴影映射

```javascript
boxShadow: {
  sm: '0 1px 2px rgba(0,0,0,0.04)',
  md: '0 2px 8px rgba(0,0,0,0.04)',
  card: '0 1px 3px rgba(0,0,0,0.03), 0 1px 6px rgba(0,0,0,0.02)',
  'card-hover': '0 4px 12px rgba(0,0,0,0.05)',
  dropdown: '0 6px 16px rgba(0,0,0,0.05), 0 3px 6px rgba(0,0,0,0.03)',
  modal: '0 12px 40px rgba(0,0,0,0.12), 0 4px 12px rgba(0,0,0,0.05)'
}
```

### 间距映射

```javascript
spacing: {
  xs: '4px',
  sm: '8px',
  md: '12px',
  lg: '16px',
  xl: '24px',
  '2xl': '32px'
}
```

## 样式统一保障

无论走哪条路径，最终都必须满足：

1. **Tailwind 配置统一**：所有项目共享同一个 `tailwind.config.js`（基于 `colors_and_type.css`）
2. **设计 Token 统一**：生成 `src/styles/design-tokens.css`，所有组件引用同一套变量
3. **规范引用统一**：`design.md` 作为唯一设计规范来源
4. **门禁验证统一**：所有组件通过同一套 QA 门禁

```
colors_and_type.css
        ↓
design-tokens.css (CSS 变量注入)
        ↓
tailwind.config.js (extend 主题)
        ↓
所有 Vue 组件（引用 Tailwind class）
```

## 输出物清单

Phase 0 完成后必须输出：

1. `design-source-map.md` — 设计源映射表
2. `design-spec-reference.md` — 设计规范引用摘要
3. `tailwind-config-base.js` — 基于 CSS 变量的 Tailwind 基础配置

## 禁止事项

- 不得在未确认设计源目录的情况下进入 Phase 1
- 不得跳过设计源映射表确认环节
- 不得在无匹配页面时随意推断样式值，必须引用设计规范
- 不得在设计稿和原型页面冲突时自行决定优先级，必须提交用户确认