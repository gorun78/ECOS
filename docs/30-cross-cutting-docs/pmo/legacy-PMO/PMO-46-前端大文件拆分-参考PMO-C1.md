# PMO-46 大文件拆分（参考 PMO-C1）

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../.trae/rules/架构铁律.md) §4.6 组件规范（文件 ≤ 800 行）
> 来源: 肖国荣 | 日期: 2026-09-05 | 责任人: fullstack-implementer (FE)
> 铁律:
> 1. 文件 ≤ 800 行（§4.6）— 报告 §3.4 共 4 文件超限：AgentStudioView 1915 行 / useAgentStudio 1488 行 / OntologyWorkbenchLayout ~1000 行 / InteractiveStepGuide ~2085 行
> 2. 拆分后**保留原导出路径**（re-export），不破坏 callers（§4.x 扩展性）
> 3. 每个 Tab 独立文件（§4.6）— `useAgentStudio.ts` 的 Tab 拆分 reference PMO-C1

报告来源：`docs/9-checks/2026-09-05-前端界面操作实现情况检查报告.md` §3.4 + §5.1 P0 项 15 + 报告指向 `docs/6-techdebt/PMO-C1-前端大文件拆分.md`。本批次拆 4 文件 → 5 Task（4 个文件 + 1 个验证重组）。

## §禁止清单

1. ❌ 拆分后**改组件/函数语义**（仅拆文件，行为不变；diff 验证）
2. ❌ 拆分前未读 PMO-C1 约定 → 不允许；先看 `docs/6-techdebt/PMO-C1-前端大文件拆分.md`
3. ❌ 不允许拆分后单文件 `> 800` 行（§4.6 硬约束）
4. ❌ 不允许拆分中**漏 re-export** 导致调用方 404 import（综合 build 验证）
5. ❌ 拆分过程中的临时 `any` / 注释掉的死代码必须清理（前端规范一：低冗余）

## §Task（原子）

| Task | 文件/路径 | 操作 | 验收 |
|:--|------|------|------|
| T1 | `ecos_frontend/src/pages/aiworkbench/AgentStudioView.tsx`（1915 行）| 拆分为 `AgentStudioView.tsx`（≤ 300 行，仅组合+状态）+ 各 Tab 子组件（参考 PMO-C1）：`agent-studio/AgentFormTab.tsx`、`agent-studio/AgentConfigTab.tsx`、`agent-studio/AgentTestTab.tsx`、`agent-studio/AgentLogsTab.tsx`、`agent-studio/AgentPipelineTab.tsx` 等（具体几步依据原 Tab 数量）。`src/pages/aiworkbench/AgentStudioView.tsx` 保留 `export default` + re-export 兼容 | `wc -l src/pages/aiworkbench/AgentStudioView.tsx` ≤ 300；拆出子组件均 ≤ 800；`npm run lint` 0 错误；浏览器 `#/agent-builder` 与 `#/agent-studio` 两 Tab 交互无异常，功能与拆前 diff = 0（截图对比） |
| T2 | `ecos_frontend/src/pages/aiworkbench/useAgentStudio.ts`（1488 行）| 拆为：`useAgentStudio.ts`（≤ 300 行，仅 hook 入口 + 公共 state）+ `agent-studio/useAgentForm.ts` + `agent-studio/useAgentTest.ts` + `agent-studio/useAgentPipeline.ts` + `agent-studio/toolTypes.ts` 等。所有 hook 入参出参签名严格保留（**不改 hook 对外 API**） | `wc -l useAgentStudio.ts` ≤ 300；`npm run lint` 0 错误；浏览器 Agent 创建/编辑/测试/执行全流程可用 |
| T3 | `ecos_frontend/src/pages/OntologyWorkbenchLayout.tsx`（~1000 行）| 拆为：`OntologyWorkbenchLayout.tsx`（≤ 300 行，组合 + 路由切换）+ `OntologyDesignTab.tsx` / `OntologyReviewTab.tsx` / `OntologyPublishTab.tsx` / `OntologyVersionTab.tsx`。每个 Tab 独立文件（§4.6 强制） | `wc -l OntologyWorkbenchLayout.tsx` ≤ 300；`wc -l Ontology*.Tab.tsx` 各 ≤ 800；`npm run lint` 0 错误；浏览器 `/ontology_workbench` 4 个 Tab 切换无异常 |
| T4 | `ecos_frontend/src/components/pipeline/InteractiveStepGuide.tsx`（~2085 行）| 拆为：`InteractiveStepGuide.tsx`（≤ 300 行，组合 + 公共 state）+ 子步骤 Tab：`StepDataSource.tsx` / `StepPipeline.tsx` / `StepValidation.tsx` / `StepPublish.tsx`。组件 API 不变 | `wc -l InteractiveStepGuide.tsx` ≤ 300；`npm run lint` 0 错误；浏览器 `/data-workbench` Step Guide 各步骤交互正常 |
| T5 | 拆分验证闸 + 大文件清单更新 | 运行 `for f in $(find src -name "*.tsx" -o -name "*.ts" | grep -v node_modules); do lines=$(wc -l < $f); if [ $lines -gt 800 ]; then echo $lines $f; fi; done` 输出到 `docs/9-checks/2026-09-05-大文件清单元初.md`，确认 = 0；保留 `docs/6-techdebt/PMO-C1-前端大文件拆分.md` 中已完成清单 + 本批新增 4 文件 | 文档存在；`wc -l` 全超 800 文件 = 0；`npm run build` 通过；浏览器 4 路由可走（agent-builder / ontology_workbench / data-workbench / knowledge_view） |

## §目标工时

8 小时（T1 2h（最大）+ T2 1.5h + T3 1.5h + T4 1.5h + T5 0.5h + 浏览器 diff 截图对比 1h）

## §依赖关系

- **前置**：PMO-43/44/45 done（大文件拆分要在全 norm 修正后做，避免拆中同时改 i18n 色）
- **输入**：报告 §3.4 + §5.1 项 15 + PMO-C1
- **后置**：PMO-46 QA 验收（含 §5.1 全 15 项关闭 + 4 主题 + 中英文切换）
- **Worker**: `fullstack-*`，**Verifier**: `reviewer-*`

## §分支

`feature/pmo-46-fe-big-file-split-2`

## §验收

- [ ] `wc -l` 全超 800 = 0（4 文件全拆完）
- [ ] `npm run lint` 0 错误；`npm run build` 通过
- [ ] 浏览器：`agent-builder` / `ontology_workbench` / `data-workbench` / `knowledge_view` 4 路由可用且每个 Tab 功能 diff 拆前 = 0
- [ ] `docs/9-checks/2026-09-05-大文件清单元初.md` 存在且 = 0
- [ ] Reviewer 代码审查 deliverable_allowed=true
