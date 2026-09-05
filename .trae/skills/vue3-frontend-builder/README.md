# Vue3-Frontend-Builder

[English](README.en.md) | [简体中文](README.md) | [日本語](README.ja.md) | [한국어](README.ko.md)

Vue3-Frontend-Builder 是一个 Hermes Skill，用于将 Markdown 格式的需求文档（PRD）和原型截图/设计稿转化为 Vue3 + TypeScript + Tailwind CSS 前端代码。

借鉴 CyberPPT 的设计理念，Vue3-Frontend-Builder 强调**证据驱动的需求转化**、**多阶段确认门机制**、**双硬门槛原则**（设计还原度 + 代码可编辑性同等重要）和**逐组件迭代验收**。

## 适用场景

- 从设计稿到完整组件/页面的快速还原开发
- PRD 驱动的 Vue3 前端开发
- 需要高保真还原设计稿的组件开发
- 团队标准化组件开发流程

## 核心能力

- 从 Markdown PRD 中提取组件清单和需求追踪表
- 分析原型截图/设计稿，提取样式参数
- 基于 Tailwind CSS 锁定设计还原计划
- 使用 Vue3 Composition API + TypeScript 开发组件
- 逐组件验收，确保设计还原度

## 强制流程

| 阶段 | 产出 | 确认门 |
|---|---|---|
| 1. 需求分析 | 组件清单、Props/State/Emits 定义、页面结构树 | 用户批准组件清单和定义 |
| 2. 设计还原 | 样式参数锁定、Tailwind 配置、图标库选择 | 用户批准样式方案 |
| 3. 组件开发 | 逐组件 Vue3 代码、类型定义、单元测试 | 用户批准每个组件渲染效果 |
| 4. 集成交付 | 完整页面、响应式验证、QA 报告 | 用户批准最终交付物 |

## 使用方法

### 1. 需求分析

上传 PRD 文档，告诉 Codex：

```
使用 /workspace/vue3-frontend-builder 这个 skill，根据上传的 PRD 文档做前端开发
```

Codex 会分析 PRD，生成组件清单和需求追踪表。

### 2. 设计还原

提供设计稿/原型截图，Codex 会：
- 提取颜色、字体、间距等样式参数
- 生成 Tailwind 配置扩展
- 锁定图标库和实现顺序

### 3. 组件开发

Codex 按优先级（原子→分子→有机→页面）逐组件开发：
- 每个组件有完整的 TypeScript 类型定义
- 每个组件有单元测试
- 每个组件渲染截图与设计稿对照

### 4. 集成与交付

所有组件验收通过后，进行页面集成和响应式测试。

## 技术栈

| 类别 | 技术 |
|---|---|
| 框架 | Vue 3.4+ (Composition API + `<script setup>`) |
| 语言 | TypeScript 5.0+ |
| 样式 | Tailwind CSS 3.4+ |
| 状态管理 | Pinia |
| 路由 | Vue Router 4 |
| 构建 | Vite 5 |
| 测试 | Vitest |
| 图标 | Heroicons / Lucide / Tabler |

## 项目结构

```
src/
├── components/
│   ├── atoms/          # 原子组件（Button, Input, Icon 等）
│   ├── molecules/      # 分子组件（SearchBar, DataCard 等）
│   ├── organisms/      # 有机组件（AppHeader, DataTable 等）
│   └── pages/          # 页面组件
├── composables/        # 组合式函数
├── stores/             # Pinia stores
├── router/             # Vue Router 配置
├── types/              # TypeScript 类型定义
└── styles/             # 全局样式
```

## 借鉴自 CyberPPT 的设计理念

1. **多阶段确认门** — 每个阶段有明确停止条件，用户不确认不跨阶段
2. **证据驱动的需求转化** — 所有需求可追溯到 PRD 位置
3. **双硬门槛原则** — 设计还原度和代码可编辑性同等重要
4. **逐组件迭代** — 防止 AI 注意力分散导致的质量下降
5. **完整的内容锁定** — 组件 content、样式参数、状态定义必须先冻结再开发
6. **可视化验收** — 代码生成后必须实际渲染截图对照

## 安装

复制项目到 Hermes skills 目录：

```bash
git clone <repo-url> ~/.hermes/skills/vue3-frontend-builder
```

## 校验组件

```bash
node scripts/validate_component.js path/to/Component.vue
```

## 分析 PRD

```bash
node scripts/analyze_requirements.js path/to/prd.md
```

## 生成组件签名

```bash
node scripts/build_component_signature.js path/to/Component.vue
```

## 许可

MIT