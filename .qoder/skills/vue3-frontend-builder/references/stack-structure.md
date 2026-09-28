# vue3-frontend-builder — 拆分出的模板/示例

> 由 SKILL.md 拆出，SKILL.md 对应位置留有指针。生成相关制品前先读本文件。

## 块 1（原 SKILL.md L509-540）

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

