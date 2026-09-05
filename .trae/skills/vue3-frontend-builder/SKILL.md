---
name: vue3-frontend-builder
description: Vue3+TS+Tailwind frontend builder with component abstraction engine. Invoke when user provides PRD and design mockups for frontend development.
---

<!-- ENCODING-GUARD: This file is UTF-8. In Windows PowerShell 5.1, use `Get-Content -Encoding UTF8`; if Chinese text appears garbled, reread with UTF-8 before summarizing or acting. -->

# Vue3-Frontend-Builder

## 概览

将 Markdown 格式的需求文档（PRD）和原型截图/设计稿转化为 Vue3 + TypeScript + Tailwind CSS 前端代码。使用 CyberPPT 级别的标准审视需求完整性、设计还原度和代码质量。

**核心差异化能力：组件抽象分析引擎**

拿到原型后，不是立即按页面逐个开发，而是先全局扫描所有设计稿：
1. 识别跨页面重复的 UI 模式（同一个按钮出现在 5 个页面）
2. 计算每个候选组件的**复用指数 (RI)** — 出现页面数 × 功能通用性 × 变体复杂度
3. 自动推荐分层策略：RI ≥ 9 → 全局原子组件，RI 5-8 → 领域分子/有机，RI < 5 → 页面级

这样可以最大化组件复用度，避免把本可以全局复用的按钮写成页面内联代码。

## 核心原则

与 CyberPPT 一脉相承，以下原则直接借鉴：

1. **证据驱动的需求转化** — PRD 内容必须逐条可追溯到需求文档
2. **多阶段确认门** — 每阶段有明确停止条件，用户不确认不跨阶段
3. **双硬门槛** — 设计还原度和代码可编辑性同等重要
4. **逐页/逐组件迭代** — 防止 AI 注意力分散导致的质量下降
5. **完整的内容锁定** — 组件 content、样式参数、状态定义必须先冻结再开发
6. **可视化验收** — 代码生成后必须实际渲染截图对照
7. **复用优先的组件抽象** — 先分析后开发，最大化跨页面复用价值
8. **交付门禁机制** — 每个组件必须通过 QA 门禁（component_qa_gate.json）和审批记录（component_approval_record.json）才能进入交付包，未通过门禁的组件永远不得交付
9. **智能确认机制** — 支持严格/快速/自动三种确认模式，根据项目复杂度和用户偏好动态调整确认频率，避免过度中断开发流
10. **设计稿分析前置** — 修正实现源码前，必须先完成设计稿全量分析并落盘证据文档到设计稿同级 `temp/` 目录，分析未完成不得动代码

## 确认模式

为平衡质量保障与开发效率，提供三种确认模式供用户在启动时选择：

### 模式选择

| 模式 | 适用场景 | 确认频率 | 阶段合并 | Phase 3 确认粒度 |
|------|----------|----------|----------|-----------------|
| **严格模式** (strict) | 中大型项目、高保真要求、团队协作 | 每阶段必须确认 | 不合并 | 逐组件确认 |
| **快速模式** (fast) | 中小型项目、快速迭代、独立开发者 | 合并阶段后确认 | Phase 0+1 合并 | 按组件层级批量确认 |
| **自动模式** (auto) | 原型验证、MVP 开发、时间紧迫 | 仅关键节点确认 | 最大合并 | 全层级一次确认 |

### 模式切换规则

1. **默认询问** — 首次启动时询问用户选择确认模式，默认为快速模式
2. **动态降级** — 连续 2 次无修改直接确认 → 提示切换到更快速模式
3. **动态升级** — 出现需求缺口或设计冲突 → 自动升级到更严格模式
4. **强制升级** — Phase 3 组件 QA 门禁失败 → 强制升级为严格模式（不可降回，直到该组件通过）

### 合并规则（快速模式）

| 合并组 | 合并后确认点 | 输出物 |
|--------|-------------|--------|
| Phase 0 + Phase 1 | 组件清单确认 | 设计源映射表 + 组件清单 + Props/State/Emits 一次性输出 |
| Phase 2 + Phase 3 (批量) | 按组件层级批量确认 | 原子组件批量 → 分子组件批量 → 有机组件批量 → 页面组件批量 |

### 批量确认规则（快速模式 Phase 3）

Phase 3 支持按组件层级批量确认：
1. 收集同层级所有组件的 QA 结果（qagate.json）
2. 一次性展示所有组件的渲染截图和 QA 报告
3. 用户批量批准或逐个标记修改项
4. 批量批准后统一进入下一层级

### 自动模式确认节点

自动模式下仅在以下关键节点强制确认：
- 需求缺口（GAP）出现时
- 设计稿与原型冲突时
- QA 门禁失败时
- 最终交付前

## 强制流程

> **确认模式说明**：下表停止条件为严格模式要求。快速模式下 Phase 0+1 合并为一次确认，Phase 3 按层级批量确认；自动模式仅关键节点确认。详见上方"确认模式"章节。

| 阶段 | 必须产出 | 停止条件（严格模式） | 可合并 | 读取 |
|---|---|---|---|---|
| 0. 设计源解析 | 设计源映射表、设计规范引用摘要、Tailwind 基础配置 | 设计源目录确认、映射表确认、还原策略确认 | ✅ 快速模式与 Phase 1 合并 | `references/00-design-source-resolution.md` |
| 1. 需求分析 | 组件清单、属性定义、状态定义、交互定义、依赖关系、页面结构树 | 第一次确认：用户批准组件清单、每个组件的 props/状态/事件、页面结构 | ✅ 快速模式与 Phase 0 合并 | `references/01-component-analysis.md` |
| 2. 设计还原 | 逐组件设计还原计划、样式参数锁定、图标库选择、Tailwind 配置扩展 | 第二次确认：用户批准样式参数、图标库、组件实现顺序 | ❌ 不可合并 | `references/02-design-system.md` |
| 3. 组件开发 | 逐组件 Vue3 代码、逐组件渲染验证、逐组件类型定义、逐组件单元测试 | 第三次确认前必须：运行 `validate_component_full.js`，通过 QA 门禁（component_qa_gate.json），生成审批记录（component_approval_record.json） | ✅ 快速模式按层级批量确认 | `references/03-vue3-patterns.md`, `references/gate-functions.md` |
| 4. 集成与交付 | 完整页面集成、响应式验证、跨组件状态打通、最终渲染对照、QA 报告 | 最终确认：所有组件 deliverable_allowed=true，第四阶段 qagate 全部通过，第四阶段 manifest 完整 | ❌ 不可合并 | `references/04-quality-assurance.md` |

未经确认不要跨过确认门。用户要求修改时，回到对应阶段修订并重新确认。

## Reference Gate

| 阶段 | 读取 | 说明 |
|------|------|------|
| Phase 0 | `00-design-source-resolution.md` | 设计源解析（新增） |
| Phase 1 | `01-component-analysis.md` | 组件抽象 + Props/State 定义 |
| Phase 2 | `02-design-system.md` | 样式参数 + Tailwind 配置（双路径） |
| Phase 3 | `03-vue3-patterns.md` + `gate-functions.md` | Vue3 模式 + 门禁判定 |
| Phase 4 | `04-quality-assurance.md` | QA 体系 + 交付标准 |

> 阶段开始前必须读取对应 reference 文件，清单优先于摘要描述。

## 第零步：设计源解析 + 确认

在开始需求分析前，必须先识别项目中的设计来源文件，建立原型页面与 UI 设计稿的映射关系。

> **合并执行（快速模式）**：快速模式和自动模式下，Phase 0 与 Phase 1 合并执行，设计源映射表与组件清单一次性输出，用户一次确认覆盖两个阶段。合并时仍须完成下方所有硬性要求，只是确认环节合并。

### 第零步硬性要求

1. 必须读取 `00-design-source-resolution.md`
2. 必须扫描项目目录，识别设计稿目录、原型目录、设计规范文件、CSS 设计系统文件
3. 必须生成设计源映射表，明确每个页面的设计来源
4. 必须获得用户确认后才能进入下一阶段
5. 设计源目录不固定时，必须通过用户确认环节确定正确路径

### 第零步工作顺序

1. **目录识别** — 搜索项目目录，识别设计稿页面、原型页面、设计规范文档、CSS 设计系统文件
2. **候选列表输出** — 如果存在多个候选目录，输出列表供用户确认
3. **用户确认** — 用户确认设计稿目录、原型目录、设计规范文件、CSS 设计系统文件
4. **文件匹配** — 使用精确匹配、部分匹配、名称相似度算法建立映射关系
5. **生成映射表** — 输出设计源映射表，包含原型页面、设计稿页面、匹配类型、优先级、还原策略
6. **确认输出** — 获得用户对映射表和还原策略的确认

### 第零步确认输出

第零步确认必须包含：

- **设计源目录确认**
  - 设计稿目录路径
  - 原型目录路径
  - 设计规范文件路径
  - CSS 设计系统文件路径
- **设计源映射表**（见 `00-design-source-resolution.md` 格式）
- **还原策略确认**
  - 路径A：有设计稿的页面列表（精确还原）
  - 路径B：无设计稿的页面列表（规范驱动）
- **样式统一保障方案**
  - CSS 变量 → Tailwind 映射规则
  - design-tokens.css 生成计划

### 第零步禁止事项

- 不得在未确认设计源目录的情况下进入 Phase 1
- 不得跳过设计源映射表确认环节
- 不得在无匹配页面时随意推断样式值，必须引用设计规范
- 不得在设计稿和原型页面冲突时自行决定优先级，必须提交用户确认

## 第一步：需求分析 + 组件清单

第一步不是"读完 PRD 后给一个组件列表"。它要把 PRD 变成可审计的需求底表，再从中提取组件结构、属性、状态、事件和交互定义。

### 第一步硬性要求

1. 所有 UI 元素、交互行为、状态流转、数据来源必须能追溯到 PRD 位置或明确标记为需求缺口
2. 必须建立组件清单，记录组件名称、类型（原子/分子/有机）、职责、props、emits、slots、状态、依赖
3. 必须完成组件关系梳理：父子关系、兄弟通信、跨层级通信（Pinia store）
4. 必须定义每个组件的 Props 接口，包括类型、是否必填、默认值
5. 必须定义每个组件的 State 状态（ref/reactive），包括状态名称、类型、初始值
6. 必须定义组件之间的交互：事件总线或 Pinia actions
7. 必须输出页面结构树，展示组件层级和组件映射关系
8. 不得让用户只确认组件名称列表；第一次确认必须覆盖组件清单、props/state/事件定义、页面结构树

### 第一步工作顺序

1. **扫描所有原型/设计稿** — 使用 `vision_analyze` 逐个分析设计稿，提取所有可见 UI 元素（按钮、输入框、卡片、图标等），记录每个元素的 bbox、视觉特征、出现页面
2. **模式识别与相似度分析** — 将视觉相似或功能相似的元素归组，识别跨页面重复出现的模式
3. **计算复用指数 (Reusability Index)** — `RI = 出现页面数 × 功能通用性系数 × 变体复杂度系数`
4. **抽象决策** — 根据 RI 值和组件类型决策树，决定每个候选组件是原子/分子/有机/页面级
5. **生成组件抽象报告** — 输出全局原子组件清单、领域组件清单、页面专属组件清单和开发优先级
6. 解析 PRD 文档，提取页面/组件、功能点、交互描述
7. 建立需求追踪表：需求来源、优先级、UI 元素、状态、交互
8. 定义组件 Props 接口
9. 定义组件 State 和 computed
10. 定义组件 emits 和事件处理
11. 建立组件关系图和页面结构树
12. 生成组件开发优先级顺序（按原子→分子→有机→页面的依赖顺序）

### 第一步确认输出

第一次确认必须包含：

- **组件抽象分析报告**（新增）
  - 全局原子组件清单（高复用指数，建议最先开发）
  - 领域分子/有机组件清单
  - 页面专属组件清单
  - 每个组件的复用指数和抽象决策理由
- 需求追踪表摘要
- 组件清单（含类型、职责、props、state、emits）
- 组件关系图或树状结构
- 组件开发优先级顺序
- 需要用户决策或补充的需求缺口

### 第一步禁止事项

- 不得跳过组件抽象分析直接进入页面级开发
- 不得忽略跨页面重复模式的识别
- 不得将高复用组件降级为页面级组件
- 不得跳过需求分析直接出代码
- 不得用常识补 PRD 没有的交互细节
- 不得把 ImageGen/generative UI 当作组件内容来源
- 不得输出只有"组件名 + 简短描述"的低信息量清单

## 第二步：设计还原 + 样式参数锁定

第二步不是"看一下设计稿就开写代码"。它要根据 Phase 0 的设计源映射表，采用双路径策略为每个组件制定详细的设计还原计划，锁定所有样式参数，然后按优先级实现。

### 第二步硬性要求

1. 必须读取 `02-design-system.md`
2. **必须先完成设计稿全量分析并落盘（见下方"设计稿分析前置"章节），分析未完成不得进入后续步骤**
3. 必须根据设计源映射表选择还原路径：
   - 路径A（有设计稿）：以设计稿页面为视觉基准，精确还原
   - 路径B（无设计稿）：引用设计规范（design.md + colors_and_type.css），规范驱动
4. 必须锁定 Tailwind 配置扩展：自定义颜色、字体、动画、插件
5. 必须从设计稿或设计规范中提取精确的样式值，转换为 Tailwind class 或自定义 CSS
6. 必须选择统一的图标库并锁定（Heroicons / Lucide / Tabler 等）
7. 必须处理响应式设计：从设计稿或设计规范提取 desktop/tablet/mobile 断点
8. 样式参数必须记录在 `component-style-lock.json` 中，第三阶段直接引用，不得重新推断
9. 无论走哪条路径，必须确保样式统一（引用同一套 design tokens）

### 设计稿分析前置（强制）

> **核心规则**：修正实现源码前，必须先完成设计稿全量分析并落盘证据文档。分析未完成不得动代码。

#### 分析范围

必须覆盖以下所有影响还原程度的信息：

| 分析维度 | 必须提取的内容 | 证据要求 |
|----------|---------------|----------|
| 布局结构 | DOM 层级、flex/grid 布局、定位方式 | 设计稿文件路径 + 行号 + 代码段 |
| 配色方案 | 主色/辅助色/状态色/文本色/背景色/边框色 | 设计稿文件路径 + 行号 + 色值代码段 |
| 排版系统 | 字体族、字号层级、字重、行高 | 设计稿文件路径 + 行号 + 排版代码段 |
| 间距系统 | 内边距、外边距、间隙值 | 设计稿文件路径 + 行号 + 间距代码段 |
| 组件尺寸 | 宽度、高度、圆角、阴影 | 设计稿文件路径 + 行号 + 尺寸代码段 |
| 交互状态 | hover/active/focus/disabled/loading 样式 | 设计稿文件路径 + 行号 + 状态代码段 |
| 响应式 | 断点定义、各断点布局差异 | 设计稿文件路径 + 行号 + 响应式代码段 |
| 图标与资源 | 图标名称/SVG 路径、图片资源 | 设计稿文件路径 + 行号 + 资源引用 |
| 动画与过渡 | 过渡时长、缓动函数、关键帧 | 设计稿文件路径 + 行号 + 动画代码段 |
| 设计 Token 冲突 | 主题变量与组件实际使用的颜色冲突 | 主题文件 + 组件文件 + 行号 + 对比代码段 |

#### 证据格式

每条分析记录必须包含可追溯的证据：

```markdown
| 分析项 | 设计稿规格 | 设计稿来源 | 代码段 |
|--------|-----------|-----------|--------|
| 主色 | #3370FF | `design/pages/dashboard.html#L42` | `color: #3370FF` |
| 按钮高度 | 38px | `design/pages/dashboard.html#L87` | `height: 38px` |
| 卡片圆角 | 10px | `design/pages/dashboard.html#L105` | `border-radius: 10px` |
```

#### 保存规则

| 规则 | 说明 |
|------|------|
| 保存目录 | 设计稿目录的同级 `temp/` 目录 |
| 目录创建 | 若 `temp/` 不存在，必须先创建 |
| 文件命名 | 分析内容简写（如 `color-analysis.md`、`layout-analysis.md`、`typography-analysis.md`） |
| 文件格式 | Markdown（.md） |
| 多维度拆分 | 可按维度拆分为多个文件，或合并为一个 `design-analysis.md` |

#### 分析工作流

```
1. 定位设计稿目录
   ↓
2. 创建 temp/ 目录（如不存在）
   ↓
3. 逐维度提取设计信息 + 收集证据（文件路径 + 行号 + 代码段）
   ↓
4. 检测设计 Token 冲突（主题变量 vs 组件实际使用）
   ↓
5. 生成分析文档 → 保存到 temp/
   ↓
6. 确认分析完整性 → 所有维度已覆盖、所有证据已落盘
   ↓
7. 分析完成后 → 才可进入源码修正/实现
```

#### 分析文档模板

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

#### 禁止事项

- 不得在分析文档未落盘前修正或实现源码
- 不得省略证据列（文件路径 + 行号 + 代码段）
- 不得将分析文档保存到非 `temp/` 目录
- 不得跳过任何分析维度

### 第二步工作顺序

#### 路径A：有设计稿（精确还原）

当原型页面匹配到设计稿页面时，采用精确还原策略：

1. 分析设计稿页面的 DOM 结构和样式类名
2. 提取布局结构、组件层次、交互状态
3. 将设计稿中的样式映射到 Tailwind（基于设计稿的精确像素值）
4. 生成 component-style-lock.json（精确像素值）
5. 确认视觉样式优先级：设计稿 > 原型页面 > 设计规范

#### 路径B：无设计稿（规范驱动）

当原型页面未匹配到设计稿页面时，采用规范驱动策略：

1. 分析原型页面的 DOM 结构
2. 直接引用 colors_and_type.css 中的 CSS 变量
3. 使用 design.md 中的字体、间距、圆角等规范
4. 将 CSS 变量映射到 Tailwind extend（见 `00-design-source-resolution.md` 映射规则）
5. 生成 component-style-lock.json（使用规范值）

### 第二步确认输出

第二次确认必须包含：

- Tailwind 主题配置（颜色、字体、间距、阴影）
- 图标库选择和语义化命名映射
- 每个组件的设计还原计划摘要（标明还原路径）
- 组件实现顺序和依赖关系
- 响应式断点和适配策略
- 样式统一保障方案确认（design tokens 引用方式）

## 第三步：逐组件开发 + 逐组件验收

第三步强制逐组件开发、逐组件渲染验证。禁止一次性生成多个组件作为终版。

> **批量确认（快速模式）**：快速模式下按组件层级批量确认——同层级所有组件开发完成并通过 QA 门禁后，一次性展示渲染截图和 QA 报告供用户批量批准。自动模式下全部组件完成后一次确认。

### 第三步硬性要求

1. **必须确认 Phase 2 的设计稿分析文档已落盘到 `temp/` 目录，若未完成则返回 Phase 2 补充分析**
2. 必须读取 `vue3-patterns.md` 和 `component-standards.md`
3. **必须读取 `temp/` 目录下的设计稿分析报告作为实现依据，不得凭记忆推断样式值**
4. 必须为每个组件生成完整的 .vue 文件，包含 `<template>`、`<script setup>`、`<style>`
5. 组件必须有完整的 TypeScript 类型定义：Props、Emits、Expose
6. 组件必须有 Storybook 或 inline preview 供可视化验收
7. 每生成一个组件，必须渲染截图与设计稿对照
8. 组件代码必须通过 ESLint + TypeScript 类型检查
9. 必须为每个组件生成单元测试（Vitest）
10. 组件必须在目标框架版本下实际运行，不得是伪代码

### 第三步工作顺序

#### 严格模式：逐组件确认

1. **读取 `temp/` 目录下的设计稿分析报告，确认分析已覆盖当前组件的所有维度**
2. 按优先级顺序选取下一个组件
3. 读取 `component-style-lock.json` 和 `temp/` 分析报告获取该组件样式参数
4. 生成组件 .vue 文件（Composition API + `<script setup>`）
5. 生成 TypeScript 类型定义
6. 运行 dev server 渲染组件
7. 截图与设计稿对照
8. 如有偏差，修复直到通过
9. 生成单元测试
10. 用户确认该组件后，才进入下一个

#### 快速模式：按层级批量确认

1. **读取 `temp/` 目录下的设计稿分析报告，确认分析已覆盖当前层级所有组件**
2. 按组件层级（原子 → 分子 → 有机 → 页面）依次开发同层级所有组件
3. 每个组件仍须完成步骤 3-9（读取参数、开发、类型定义、渲染、截图、修复、测试）
4. 收集同层级所有组件的 qagate.json 结果
5. 一次性展示同层级所有组件的渲染截图和 QA 报告
6. 用户批量批准或逐个标记修改项
7. 批量批准后进入下一层级

#### 自动模式：全层级一次确认

1. **读取 `temp/` 目录下的设计稿分析报告，确认分析已覆盖所有组件**
2. 按优先级顺序依次开发所有组件
3. 每个组件仍须完成读取参数、开发、类型定义、渲染、截图、修复、测试
4. 所有组件通过 QA 门禁后，一次性展示全部渲染截图和 QA 报告
5. 仅在 QA 门禁失败时暂停并请求用户确认

### 第三步禁止事项

- 不得一次性生成多个组件作为终版
- 不得用低代码平台或 AI 生成图片代替真实 Vue 组件
- 不得跳过 TypeScript 类型定义
- 不得跳过单元测试
- 不得跳过渲染验证

### 组件开发规范

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

## 第四步：集成 + 响应式验证 + 最终交付

第四步将验收通过的组件按页面结构树集成，验证跨组件交互，进行响应式测试，交付完整页面。

### 第四步硬性要求

1. 必须读取 `quality-assurance.md`
2. 必须基于页面结构树导入并组装所有已验收组件（approval.status = approved）
3. 必须配置 Vue Router（如多页面应用）
4. 必须配置 Pinia Store 处理跨组件状态
5. 必须进行响应式验证：在目标断点（desktop/tablet/mobile）截图对照
6. 必须验证组件间交互：props 传递、事件通信、store 共享状态
7. **必须检查所有组件的 deliverable_allowed=true（来自 component_approval_record.json）**
8. **必须运行第四阶段交付门禁判定（第四阶段 qagate + manifest 完整性）**
9. **任何 deliverable_allowed=false 的组件不得进入交付包**
10. 最终交付包含：源代码、可运行项目、渲染截图、QA 报告、manifest

### 第四步工作顺序

1. 创建或更新 main.ts / App.vue
2. 配置 Vue Router（如需要）
3. 配置 Pinia Store（如需要）
4. 按页面结构树组装组件
5. 运行 dev server
6. 逐断点截图验证
7. 验证组件间交互
8. 运行完整 QA
9. 生成最终交付报告

## 技术栈

| 类别 | 技术 | 说明 |
|---|---|---|
| 框架 | Vue 3.4+ | Composition API + `<script setup>` |
| 语言 | TypeScript 5.0+ | 严格类型检查 |
| 样式 | Tailwind CSS 3.4+ | JIT 模式，自定义主题 |
| 状态管理 | Pinia | 跨组件状态 |
| 路由 | Vue Router 4 | 页面导航 |
| 构建 | Vite 5 | 快速 HMR |
| 测试 | Vitest + Vue Test Utils | 单元测试 |
| 代码检查 | ESLint + Prettier | 格式化与lint |
| 图标库 | Heroicons / Lucide / Tabler | 按需选用 |

## 项目结构

```
src/
├── components/
│   ├── atoms/          # 原子组件（Button, Input, Icon等）
│   ├── molecules/      # 分子组件（SearchBar, Card等）
│   ├── organisms/      # 有机组件（Header, Sidebar等）
│   └── pages/          # 页面组件
├── composables/        # 组合式函数
├── stores/             # Pinia stores
├── router/             # Vue Router 配置
├── types/              # TypeScript 类型定义
├── utils/              # 工具函数
├── assets/             # 静态资源
└── styles/             # 全局样式
```

## 交付标准

每个组件必须同时满足：

1. **设计还原度** — 渲染结果与设计稿一致（像素级容差 P0: 2px, P1: 4px, P2: 6px）
2. **类型完整性** — 所有 props/state/emits 有 TypeScript 类型
3. **可交互性** — 所有交互状态（hover/active/disabled/loading）可用
4. **单元测试** — 核心逻辑有测试覆盖
5. **无 lint 错误** — ESLint + TypeScript 检查全部通过

最终页面必须同时满足：

1. **完整渲染** — 所有组件正确显示
2. **响应式适配** — 三个断点均正常显示
3. **状态连通** — 跨组件状态正确传递
4. **QA 通过** — quality-assurance.md 中的检查项全部通过
5. **所有 deliverable_allowed=true** — 来自 component_approval_record.json
6. **第四阶段 manifest 完整** — 所有交付文件已生成

## 交付门禁机制（强制）

交付前必须完成以下门禁检查，任一项不满足不得确认交付：

### 门禁 1：组件级 QA 门禁（第三阶段）

每个组件在第三阶段结束前必须：
1. 运行 `node scripts/validate_component_full.js <component-file>`
2. 生成本组件的 `outputs/qa/components/<layer>/<ComponentName>/<ComponentName>.qagate.json`
3. 确认 `gate_result.can_proceed_to_user_approval = true`
4. 生成 `outputs/approvals/<layer>/<ComponentName>.approval.json`
5. 确认 `approval.status = approved`

**硬门槛**：`can_proceed_to_user_approval = false` 的组件不得进入用户确认环节

### 门禁 2：组件级交付许可

每个组件在第四阶段集成前必须：
1. 读取所有 `component_approval_record.json`
2. 确认所有组件的 `approval.status = approved`
3. 确认所有组件的 `deliverable_allowed = true`

**硬门槛**：`approval.status ≠ approved` 或 `deliverable_allowed = false` 的组件不得进入交付包

### 门禁 3：第四阶段交付门禁

第四阶段交付前必须生成并确认：
1. `outputs/qa/delivery/delivery-qagate.json` — 第四阶段整体 QA
2. `outputs/qa/delivery/delivery-manifest.json` — 交付包清单
3. `outputs/qa/delivery/final-delivery-report.json` — 最终交付报告（含 `deliverable_allowed` 字段）

`deliverable_allowed = true` 的判定条件（全部满足）：
- 所有组件已批准（approval.status = approved）
- 关键 issues = 0（critical/high issues）
- 响应式三个断点已验证
- 所有交付文件存在
- README 文档完整
- Medium issues 已在交付前修复或用户接受

### 门禁 4：禁止绕过

- **禁止在 QA 门禁未通过的情况下跳过阶段确认**
- **禁止在 component_approval_record.json 缺失的情况下进入第四阶段**
- **禁止交付 deliverable_allowed = false 的组件**
- **禁止修改已批准的组件**（如需修改需重新走第三阶段门禁）