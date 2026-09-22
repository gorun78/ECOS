# PMO指令：Phase2-4-Logic画布 — 可视化工作流编排

> 来源: 完善计划 Phase 2-4 T10 | 工期: 1周 | 范围: 前端LogicView重写 | 依赖: 无硬依赖

---

## §背景

当前LogicView是637行列表形式。需要升级为拖拽可视化画布。

---

## §Task

### T10-1: React Flow画布基础（1天）

**文件**: 重写 `components/aiworkbench/logic/` 下组件

**引入**: `reactflow` (npm install reactflow)

**画布功能**:
- 可拖拽空白画布
- 缩放（鼠标滚轮）
- 小地图（React Flow MiniMap）
- 撤销/重做（Ctrl+Z/Ctrl+Y）

### T10-2: 6种节点类型（2天）

| 节点类型 | 图标 | 配置参数 |
|------|------|------|
| LLM调用 | Brain | model/temperature/maxTokens/systemPrompt |
| 工具调用 | Wrench | 工具名称/参数 |
| Ontology查询 | Database | objectType/queryType/filter |
| 人工审批 | UserCheck | 审批人/超时时间/驳回后流程 |
| 条件分支 | GitBranch | if条件(JSONPath表达式)/then/else |
| 定时触发 | Clock | cron表达式/时区 |

**实现**: 每种节点一个自定义React Flow节点组件（`LLMNode.tsx`等）

### T10-3: 连线+配置面板（1天）

**功能**:
- 节点间拖拽连线（React Flow Edge）
- 双击节点 → 右侧展开配置面板
- 配置面板表单：根据节点类型动态渲染表单字段
- 连线条件标注（条件分支节点上标注if/else）

### T10-4: 执行引擎（1天）

**功能**:
- 画布顶部"执行"按钮 → 调 `POST /api/v1/aip/studio/pipelines/{id}/execute`
- 实时节点状态（灰色=未执行/蓝色=执行中/绿色=完成/红色=失败）
- 执行日志面板（底部可折叠）：实时显示节点日志
- 执行完成后展示总耗时+各节点耗时

**验收**:
- 拖拽6种节点到画布 → 连线 → 双击配置 → 点执行 → 节点逐个变色
- 条件分支：条件成立走then路径，不成立走else路径
- 撤销：Ctrl+Z回退最近操作
