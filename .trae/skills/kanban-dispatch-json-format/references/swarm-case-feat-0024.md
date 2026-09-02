# FEAT-0024：详细设计文档 + PlantUML 架构图（2026-07-28）

## 任务概述

全量分析项目前后端代码，生成详细设计文档 + 7 张 PlantUML 架构图。

## 派发

```
worker: arch-1785224485752 (AI-Native-架构师)
verifier: pm-1784271841029
synthesizer: pm-1784271841029
complexity: L3（多模块系统分析）
duration: ~4min（代码分析+文档生成+PlantUML图表）
```

## 产出

### 详细设计文档
`docs/design/detailed-design.md`（374 行）

覆盖内容：
- 系统概述（8 大功能模块：系统管理/项目管理/数字员工/工作空间/Git协作/对话/看板/需求管理）
- 技术栈（前端 Vue3+Vite+Pinia / 后端 SpringBoot+Kingbase+JPA / AI Hermes Kanban）
- 前端架构：路由表（14 条路由）、页面结构、组件层次、状态管理（Pinia）、API 层
- 后端架构：13 个 Controller、Service 层、16 个 Entity
- API 接口清单（9 大模块）

### PlantUML 架构图（7 张，.puml + .svg）

| 图表 | 文件 | 类型 |
|------|------|------|
| 数据库 ER 图 | `database-er.puml` | ER 图 |
| 前端路由图 | `frontend-router.puml` | 状态图 |
| 前端页面流转时序图 | `frontend-page-flow.puml` | 时序图 |
| 后端模块组件图 | `backend-component.puml` | 组件图 |
| 系统部署图 | `deployment.puml` | 部署图 |
| 前端 API 调用流程图 | `frontend-api-flow.puml` | 活动图 |
| 系统模块依赖图 | `system-module-dependency.puml` | 组件图 |

全部产出在 `docs/design/diagrams/` 目录。

## 关键点

1. **架构师首次出马**：`arch-1785224485752` 是新增的第 4 位数字员工，拥有 PlantUML 完整技能
2. **PlantUML skill 自动激活**：prompt 中提到 "plantuml" 时，架构师自动加载 skill 并生成图表
3. **产出落回项目目录**：这次 swarm 的产出成功写回了 GP 目录（注意：之前的 FEAT-0017 没成功）
4. **.svg 格式**：导出为 SVG（不是 PNG），方便 Markdown 阅览器中直接渲染

## 后续集成

FEAT-0023（Markdown 预览器）让这些设计文档可以在工作空间编辑器中预览——打开 `.md` 文件，点眼睛图标即可看到渲染后的设计文档，SVG 图自动内嵌显示。
