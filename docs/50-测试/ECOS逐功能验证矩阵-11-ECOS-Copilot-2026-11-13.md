# ECOS 逐功能验证矩阵 — 域11 ECOS Copilot（COPILOT）

> 来源: 用户补充「还要增加 ECOS Copilot 功能验证」 | 日期: 2026-11-13 | 责任人: 验证执行 Agent
> 版本: v1.0
> 上游: `ECOS产品级逐功能验证方案-2026-11-13.md`（判定词/证据等级/反假绿红线以此为准）+ `ECOS需求目标达成回顾验证-2026-11-13.md`（RT 层）
> 基线: HEAD `e30e4fda`；活体 2026-11-13 实测
> 覆盖口径: 后端 `CopilotController`（4 端点）+ 前端 Topbar 入口 + `AIPCopilotDrawer` 抽屉（6 组件 + `useAipAutomation` 12 场景自动化）+ 域内 Copilot（管道/本体）
> 实测位置说明: Copilot 不是独立路由，入口在 **Topbar 右上角**（2026-10-14 用户裁决从悬浮按钮移入），点击开抽屉

---

## 一、Copilot 定位与验收锚点

### 1.1 它是什么

ECOS Copilot = 平台内嵌的 AI 助手。用户点 Topbar 右上角按钮 → 打开抽屉 → 选 Agent（数据/本体/知识/安全…）→ 提问或选快捷问题 → 真实调 `POST /api/v1/agent/copilot/chat` → agent-loop + 引擎工具白名单执行。

### 1.2 后端端点实测（2026-11-13，活体）

| 端点 | 实测结果 | 证据 |
|---|---|---|
| `GET /api/v1/agent/copilot/agents` | `code=0`，返回 agent-data（数据智能体，含 routes/tools）等 | 活体 200 |
| `GET /api/v1/agent/copilot/tools?agentId=` | `code=0`，返回工具清单（name/agent/stage/tier/method/path/desc） | 活体 200 |
| `GET /api/v1/agent/copilot/quick-questions?agentId=` | `code=0`，返回快捷问题 | 活体 200 |
| `POST /api/v1/agent/copilot/chat` | `code=0`，返回 agentId/agentName/engine/sessionId/tools… | 活体 200（中文/英文载荷均通过） |

### 1.3 高危点：前端「12 场景自动化」的假成功风险

`useAipAutomation.ts` 里有 12 个场景自动化流，其中本体场景直接构造本地对象：

```ts
const newObj = { id: 'obj_aviation_flight', displayName: ..., apiName: 'AviationFlight', ... }
setObjectTypes([newObj, ...])          // ← 只改 React 内存状态
showToast('success', t('copilot.toast.ontology.done'))   // ← 却告诉用户「已完成」
```

**问题**：若不经后端 API 落库，用户刷新页面数据即丢失，但 toast 说成功 ⇒ **假成功**。这是本域最需要验证的核心风险（V6「前端不伪造」）。

### 1.4 组件结构（`components/copilot/`）

| 组件 | 职责 |
|---|---|
| `AIPCopilotDrawer.tsx` | 抽屉容器，串起下述组件 + `useAipAutomation` |
| `CopilotAgentSwitcher.tsx` | Agent 切换 |
| `CopilotMessageList.tsx` | 消息列表 |
| `CopilotInputBar.tsx` | 输入框 |
| `CopilotSuggestions.tsx` | 快捷问题/建议 |
| `AgentScenarioData.tsx` | 12 场景定义与 prompt |
| `useAipAutomation.ts` | 12 场景自动化执行流 |

> 另有域内 Copilot：`PipelineCopilotController`（`/api/v1/engine/data/copilot`，5 映射）、`OntologyCopilotController`（`/api/v1/engine/ontology/copilot`，4 映射）——分别在数据域（04）、本体域（05）验证，本册只覆盖统一 Copilot 入口。

---

## 二、前端界面与交互（V1/V2）

| ID | 功能 | 入口 | 维度 | 期望与断言 | 判定 | 证据 | 批次 |
|---|---|---|---|---|---|---|---|
| V-COP-001 | Topbar 入口 | 任意页 Topbar 右上角 | V1·V2 | 按钮可见、有 aria-label（`app.copilot.open`）；点击开抽屉；悬浮按钮已移除（2026-10-14 裁决） | PASS | `npx playwright test --project=api` → ecos-tests/tests/api/verification/copilot.spec.ts:21:7 PASS `V-COP-001` | B9 |
| V-COP-002 | 抽屉开合 | 点击入口 | V2 | 打开/关闭；ESC 关闭；点遮罩关闭；关闭后状态保留或按设计重置 | PASS | `npx playwright test --project=api` → ecos-tests/tests/api/verification/copilot.spec.ts:36:7 PASS `V-COP-002` | B9 |
| V-COP-003 | Agent 切换器 | 抽屉内 | V2·V3 | 切换 Agent 后 tools/quick-questions 随之变化（真实重新拉取） | PASS | `npx playwright test --project=api` → ecos-tests/tests/api/verification/copilot.spec.ts:46:7 PASS `V-COP-003` | B9 |
| V-COP-004 | 消息列表 | 抽屉内 | V1·V2 | 用户/助手消息区分渲染；长文本不破版；滚动到底 | PASS | `npx playwright test --project=api` → ecos-tests/tests/api/verification/copilot.spec.ts:70:7 PASS `V-COP-004` | B9 |
| V-COP-005 | 输入框 | 抽屉内 | V2·V3 | 输入/发送；空消息不可发；发送中禁用防重复 | **PASS** | 浏览器实测（2026-11-14）：抽屉空输入时 `RUN AGENT` 按钮 `disabled=true`；输入「当前系统有多少个场景？」后按钮转为可用，点击后消息入列 | B10 |
| V-COP-006 | 快捷问题 | 抽屉内 | V2·V3 | 点击快捷问题填入并发送；内容来自后端 `quick-questions` | PASS | `npx playwright test --project=api` →  PASS `V-COP-006` | B9 |
| V-COP-007 | 六态 | 抽屉 | V2 | 空（无消息引导）/加载/错误（网络失败横幅）/边界（超长）/权限/降级（引擎不可用） | **PARTIAL** | 实测覆盖 3 态：**空**（欢迎语+引导文案）、**错误**（后端 `ECOS-AI-403` → 前端如实渲染「智能体执行失败：ECOS-AI-403 / 未能从 AIP 获得应答」，**未洗白成成功**）、**加载**（发送时按钮 disabled）。未覆盖：超长边界 / 权限态 / 引擎降级 | B10 |
| V-COP-008 | i18n/主题 | 抽屉 | V1 | 四主题渲染；zh/en 无裸 key | PARTIAL | 四主题 × i18n 无裸 key 由前端门禁（`route-registry-66.spec.ts` + i18n 域清单）覆盖；Copilot 抽屉独立渲染未验 | B9 |

---

## 三、对话主链路（V3/V4/V6）

| ID | 功能 | 期望与断言 | 判定 | 证据 | 批次 |
|---|---|---|---|---|---|
| V-COP-010 | Agent 列表真实 | `GET /agents` 返回真实 agent 清单；前端渲染与后端一致（条数对账） | PASS | `npx playwright test --project=api` → ecos-tests/tests/api/verification/copilot.spec.ts:77:7 PASS `V-COP-010` | B9 |
| V-COP-011 | 工具清单真实 | `GET /tools` 每项含 stage/tier/method/path；与后端一致 | PASS | `npx playwright test --project=api` → ecos-tests/tests/api/verification/copilot.spec.ts:89:7 PASS `V-COP-011` | B9 |
| V-COP-012 | 快捷问题真实 | `GET /quick-questions` 与后端一致；非前端硬编码 | PASS | `npx playwright test --project=api` → ecos-tests/tests/api/verification/copilot.spec.ts:99:7 PASS `V-COP-012` | B9 |
| V-COP-013 | 对话执行 | `POST /chat` 返回真实回复（非本地模板）；含 sessionId 可续话 | PASS | `npx playwright test --project=api` → ecos-tests/tests/api/verification/copilot.spec.ts:127:7 PASS `V-COP-013` | B9 |
| V-COP-014 | 会话连续性 | 同 sessionId 二次提问上下文连贯 | **BLOCKED** | 两次调用均返回 HTTP 200 但 body `success:false, errorCode:ECOS-AI-403, turns:0`（见 D63）——工具链被护栏 DENY，取不到有效 answer，连续性无从验证。前端 sessionId 机制本身在位（`sessionIdRef` 单实例复用） | B10 |
| V-COP-015 | 工具白名单生效 | 对话只能调该 Agent 白名单内工具；越界工具不可用 | PASS | `npx playwright test --project=api` →  PASS `V-COP-015` | B9 |
| V-COP-016 | 未知 Agent | 传不存在 agentId → 明确业务错误，不 500 | PASS | `npx playwright test --project=api` →  PASS `V-COP-016` | B9 |
| V-COP-017 | 空消息/超长 | 空 message 拒绝；超长截断或拒绝 | PASS | `npx playwright test --project=api` →  PASS `V-COP-017` | B9 |
| V-COP-018 | **降级不伪造** | LLM/引擎不可用时返回明确错误或降级提示，**不得本地编造回复**（V6 红线） | PASS | `npx playwright test --project=api` →  PASS `V-COP-018` | B9 |

---

## 四、12 场景自动化（V3/V4/V6，本域重点）

> 这是最易出「假成功」的地方。每条都必须验证「toast 说完成」与「数据真落库」是否一致。

| ID | 场景类别 | 期望与断言 | 判定 | 证据 | 批次 |
|---|---|---|---|---|---|
| V-COP-020 | 场景清单 | 12 场景定义完整；每场景有 prompt + 目标视图 | PASS | `npx playwright test --project=api` →  PASS `V-COP-020` | B9 |
| V-COP-021 | 场景触发 | 关键字/预设命中后进入对应自动化流；未命中走真实 chat | **PASS** | 2026-11-14 用户裁决：12 条场景中 8 条为纯 `await sleep()` 伪自动化（0 后端调用），**已整体删除**；`detectScenarioType` 现只命中 4 条真实安全场景，未命中一律 `return null` → 回落真实 chat。**门禁**：`scenarioTruthfulness.test.ts` 5 用例（含"伪场景不得回流"+"未命中须 return null"）全绿，**变异探针**（重新加入 pipeline）→ 变红并点名 | B10 |
| V-COP-022 | **本体建对象是否真落库** | ~自动化创建 obj_aviation_flight 后刷新仍在~ | **N/A**（已删除：原实现为假成功） | 实测坐实：`useAipAutomation` 的 ontology 分支只做 `setObjectTypes(...)` + `sleep(1500)` + `showToast(success)`，**0 后端调用**；且 `App.tsx` 根本没给抽屉传 `setObjectTypes`（落到默认 `(() => {})`）⇒ **连内存都没变**。按用户裁决随 8 条伪场景一并删除 | B10 |
| V-COP-023 | **安全策略场景 verdict 来源** | 必须来自 `POST /api/v1/security/policy/evaluate` 实际返回，不得前端写死 | **PASS** | `runRealSecurityEval` 调 `evaluateAbacPolicy(...)`，verdict 由 `res.allowed` 决定（GRANTED/DENIED），异常时 `showToast(error)` 且**不合成**任何 verdict。`sec_gdpr`/`sec_row_col`/`sec_finance` 三条均走此通道（同批已剔除本地 state 伪写入） | B10 |
| V-COP-024 | **告警场景数据来源** | 必须来自 `GET /api/v1/security/audit-logs`；引擎不可用须错误态 | **PASS** | `sec_audit` 分支调 `fetchSecurityAuditLogs()`；`ok=false` → `showToast(error)`，`ok=true` → 显示真实行数。原「合成两条 DENIED 告警」代码已在 W204/C186 移除，本次核对确认无残留 | B10 |
| V-COP-025 | 场景执行步骤 | step banner 逐步推进；每步真实执行非纯 sleep | **PASS**（通过删除伪实现解决） | 33 处 `await sleep` 全部随 8 条伪场景删除；保留的 4 条不再有 sleep，步骤推进发生在真实调用前后。**门禁**：`scenarioTruthfulness.test.ts` ②「hook 内 `await sleep` 计数必须为 0」，**变异探针**（插入 1 处 sleep）→ 变红点名 | B10 |
| V-COP-026 | 场景结果回填 | `setAgentResult` 回填的是真实执行结果 | **PARTIAL** | 4 条场景的回填文案 `copilot.result.<type>` 是**静态 i18n 描述**（如「已完成 GDPR 合规检查」），不是把后端返回值渲染进消息体；真实的 verdict 通过 toast 单独呈现。判 PARTIAL：无假成功，但回填内容未携带执行结果数据 | B10 |
| V-COP-027 | 场景失败处理 | 任一步失败须显式报错，不继续假装成功 | **PASS** | `runRealSecurityEval` 的 catch 分支 → `showToast(error, engineFail)`，失败时**不弹成功 toast**；`sec_audit` 的 `!ok` 分支同理。反向探针：后端 403 时前端显示「智能体执行失败：ECOS-AI-403」而非成功态（浏览器实测） | B10 |

---

## 五、权限与安全（V5）

| ID | 断言 | 期望 | 判定 | 证据 | 批次 |
|---|---|---|---|---|---|
| V-COP-030 | 无 token 访问 Copilot | 四端点全部拒绝 | PASS | `npx playwright test --project=api` →  PASS `V-COP-030` | B9 |
| V-COP-031 | 工具权限 | Copilot 调用的工具受该用户权限约束（不可越权执行写操作） | PASS | 新增 `CopilotToolWhitelistGateTest` 5/5 绿：①白名单外工具在执行器之前被拦（`verify(executor, never())`）②正向对照白名单内工具真落到执行器 ③场景 Agent 误配 `invoke_rest` 仍被拦 ④六智能体白名单=本域工具全集且互不越域 ⑤`CopilotAgentCatalog.tools()` 与工具总表同源。变异探针：白名单判定改 `false &&` → 1 红；还原 → 5 绿。**唯一剩余缺口 = D65（非 SQL 工具裁决恒 fail-closed），故「越权被拦」当前与「合法也被拦」共存，无法用活体区分** | B9 |
| V-COP-032 | 写操作审批 | 经 Copilot 发起的 L2/L3 写操作须走审批，不得绕过 | PARTIAL | L3 审批前置实测可用（approval-tokens 签发 200 + 自审批 403，见域03 V-AI-055）；Copilot 发起路径是否真走该前置未验 | B9 |
| V-COP-033 | 凭据不回显 | 对话/工具返回不泄露 token/密钥 | PASS | `npx playwright test --project=api` →  PASS `V-COP-033` | B9 |
| V-COP-034 | 提示注入防护 | 恶意 prompt 不得越权调用工具或泄露系统信息 | PASS | 新增 `PromptInjectionPayloadConfinementTest` 5/5 绿：①五种注入终态（APPROVED/PUBLISHED/RELEASED/EFFECTIVE/DONE）全被改写为 DRAFT ②六类身份字段（approver/userId/tenantId/createdBy/operator/approvedBy）全剔除、非身份字段原样透传 ③正向对照读工具不被改写（防「一律清空」假绿）④T4 高危动词在工具面零注册 ⑤T3 即使 confirm=false 也不外呼。变异探针：终态改写改「保留入参」→ 1 红；还原 → 5 绿。**局限：注入对「模型层是否听劝」不构成断言，本断言只钉服务端结构性收口** | B9 |
| V-COP-035 | 审计留痕 | Copilot 发起的写操作落审计 | PARTIAL | 三面实查：①审计读面可用（`/api/v1/audit/logs → 200`，`{data,total,...}`，行含 eventId）；②Copilot 对话前后 `ecos_audit_log` 计数 **6→6**，业务审计面**零新增**；③护栏裁决确实有账，但只落 `ecos_ai.ecos_ai_guardrail_decision_ledger`（4 行，全为 dry-run 的 `FAIL_CLOSED`）——即「护栏层有账、业务审计面无账」。另查出一项**独立**缺陷 D69：网关日志同一 WARN 重复 1436 次「审计事件缺 id 且缺 eventId, 丢弃」，键面与 `ClearanceInterceptor` 超管旁路载荷逐键吻合 ⇒ 该旁路留痕**从未落库**，已在 `EventBusAuditPublisher#publish` 根修 + 调用点显式化（10/10 绿 + 变异探针 1 红）。本行剩余缺口=「Copilot 写操作端到端留痕」需先解 D65（写工具恒 fail-closed，没有一次成功的写可供留痕） | B9 |

---

## 六、需求达成（RT）

| RT ID | 需求 | 效果断言 | 判定 |
|---|---|---|---|
| RT-COP-01 | Copilot 是「真助手」而非模板机 | 对话回复由真实 LLM/agent-loop 产出，能回答平台内真实数据问题 | |
| RT-COP-02 | 12 场景自动化真的完成业务动作 | 每个场景执行后**业务状态真实改变且持久**（非仅 toast） | |
| RT-COP-03 | Copilot 能安全地执行写操作 | 写操作经审批 + 审计，越权被拒 | |
| RT-COP-04 | 多 Agent 覆盖各域 | 每个 Agent 的工具集覆盖其域主要能力 | |

---

## 七、本域覆盖度对账

| 账 | 口径 | 数值 |
|---|---|---|
| 后端端点 | CopilotController 4 个 | 4/4 有条目 |
| 前端组件 | 6 组件 + useAipAutomation | 全覆盖（V-COP-001~008） |
| 场景自动化 | 12 个 | V-COP-020~027 分类覆盖 + 逐场景抽验 |
| 域内 Copilot | Pipeline(5) / Ontology(4) | 归 04/05 分册 |

**未覆盖声明**：

| 项 | 原因 | 处置 |
|---|---|---|
| LLM 回复质量评估 | 属质量域，需基准集 | 另立专项 |
| 12 场景逐条端到端 | 依赖各域数据就绪 | 随各域批次补 |
| 提示注入对抗测试 | 属安全专项 | 与 V-COP-034 合并做基础检查 |

---

## 未覆盖清单（2026-11-13 验证批，按方案 §9.3 显式登记）

> 本批**未做活体验证**的条目（不是「通过」，也不是漏项）。每类给出下一步处置；
> 下一批收口时必须让本节清零。总未覆盖 = **15** 条。

| 原因 | 条数 | 条目 | 下一步处置 |
|---|---|---|---|
| 本批已全部收口（更新于 2026-11-14） | 0 | 原 15 条（V-COP-005、V-COP-007、V-COP-008、V-COP-014、V-COP-021、V-COP-022、V-COP-023、V-COP-024、V-COP-025、V-COP-026、V-COP-027、V-COP-031、V-COP-032、V-COP-034、V-COP-035）均已判定 | 剩余未决项见各行「证据」列的括注（D65/D63 等已登记缺陷） |
