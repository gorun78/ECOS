# PMO指令：ceos_new v3 新组件移植 + 后端对接

> **来源**: 肖总 | **日期**: 2026-07-05 | **工期**: 预计4天
> **前置**: 本体工作台替换已完成（commit dc48f70 / f80c71d）
> **铁律**: PMO唯一产出= commit hash + curl PASS/FAIL。不产出分析文档。

---

## 背景

ceos_new 两天内连更两版（6b103d8→a991582），新增4个组件+大量后端mock API。c2eos当前完全没有这些能力：

| ceos_new 新组件 | 行数 | c2eos现状 |
|------|:--:|------|
| AIPCopilotDrawer | 914 | ❌ 无 |
| InteractiveStepGuide | 1016 | ❌ 无 |
| KnowledgeView | 1916 | ❌ 无（有Neo4j但无知识视图） |
| AgentStudioView（扩展版） | +675 | ⚠️ 有514行旧版 |
| SecurityCenterView（扩展版） | +866 | ❌ 无 |
| GuardrailsView（扩展版） | +1466 | ❌ 无 |

**ceos_new v3 源路径**: `/home/guorongxiao/ceos_new/src/`

---

## 禁止清单

1. **禁止** 引入新npm依赖（ceos_new用的lucide-react/motion/recharts c2eos已有）
2. **禁止** 删除c2eos现有AgentStudio（514行）——改为增量升级
3. **禁止** 改动后端Controller签名——只新增端点
4. **禁止** 产出分析文档
5. **禁止** 组件直接依赖ceos_new的mockData——全部对接真实API或先stub

---

## Phase 1: AIPCopilotDrawer 移植（FE，1天）

### T1.1: 移植AIPCopilotDrawer（4h，FE）

**目标**: ceos_new的AI副驾驶侧抽屉移植到c2eos。这是一个全局导航+操作助手，可以语音/文字输入，理解意图后自动导航到对应页面或执行操作。

**改文件**: `/home/guorongxiao/c2eos/src/components/copilot/AIPCopilotDrawer.tsx`（新建）

**源文件**: `/home/guorongxiao/ceos_new/src/components/AIPCopilotDrawer.tsx`（914行）

**适配要求**:
- LucideIcon → c2eos直接用lucide-react的对应图标
- App状态控制props → 对接c2eos的react-router导航（用useNavigate替代props回调）
- 去掉SecurityCenter的props依赖 → 安全相关功能先stub（返回"功能开发中"）
- mockData → 用c2eos现有API数据（如fetchOntologyEntities）

**验收**:
```bash
cd /home/guorongxiao/c2eos && npx tsc --noEmit 2>&1 | grep -i "copilot"  # 期望: 0 errors
```

### T1.2: App.tsx集成Copilot入口（0.5h，FE）

**目标**: 在c2eos全局Header或右下角加Copilot触发按钮。

**改文件**: `/home/guorongxiao/c2eos/src/components/Layout.tsx`（或等效的全局布局组件）

**验收**:
```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:3000  # 200，页面有Copilot入口图标
```

---

## Phase 2: AgentStudio 升级 + KnowledgeView（FE，1.5天）

### T2.1: AgentStudio增量升级（3h，FE）

**目标**: 把ceos_new扩展版AgentStudioView的Human-in-the-loop Action Proposal模式合并到c2eos现有AgentStudio。

**改文件**: `/home/guorongxiao/c2eos/src/pages/AgentStudio.tsx`

**合并要点**（从ceos_new `AgentStudioView.tsx` L159-287提取）:
- 新增 `actionProposal` 消息类型：Agent输出不只是文本，可以是 `{text, thoughtTrace, actionProposal?: {actionId, actionName, payload, status}}`
- 新增审批按钮（批准/拒绝）→ 审批后调后端执行Action
- ThoughtTrace可视化保留c2eos现有实现，增强为多步折叠展开

**后端依赖**: 需要T4.1的Action执行端点

**验收**:
```bash
cd /home/guorongxiao/c2eos && npx tsc --noEmit 2>&1 | grep -i "agentstudio\|AgentStudio"  # 期望: 0 errors
```

### T2.2: KnowledgeView移植（3h，FE）

**目标**: ceos_new的知识库视图移植，展示Neo4j中的本体知识图谱数据。

**改文件**: `/home/guorongxiao/c2eos/src/pages/KnowledgeView.tsx`（新建）

**源文件**: `/home/guorongxiao/ceos_new/src/components/AIPWorkbench/KnowledgeView.tsx`（1916行）

**适配要求**:
- 知识同步状态 → 对接到T4.2的新端点
- 知识查询 → 对接到T4.2的新端点
- 索引状态 → 对接到T4.2的新端点
- 去掉Gemini mock逻辑 → 先用stub，等后端AI就绪再对接

**验收**:
```bash
cd /home/guorongxiao/c2eos && npx tsc --noEmit 2>&1 | grep -i "knowledge"  # 期望: 0 errors
```

---

## Phase 3: InteractiveStepGuide + Guardrails（FE，1天）

### T3.1: InteractiveStepGuide移植（3h，FE）

**目标**: 数据管道交互向导——5种算子拖拽（过滤/正则/空值/Join/类型转换）+ Git提交。

**改文件**: `/home/guorongxiao/c2eos/src/components/pipeline/InteractiveStepGuide.tsx`（新建）

**源文件**: `/home/guorongxiao/ceos_new/src/components/InteractiveStepGuide.tsx`（1016行）

**适配要求**:
- 管道输出 → 对接到c2eos现有PipelineBuilder的状态
- Git提交 → 对接到现有GitService API
- 算子列表保留mock（Pipeline后端未就绪时作为产品演示）

**验收**:
```bash
cd /home/guorongxiao/c2eos && npx tsc --noEmit 2>&1 | grep -i "interactivestep\|stepguide"  # 期望: 0 errors
```

### T3.2: GuardrailsView移植（2h，FE）

**目标**: 安全护栏管理面板——策略CRUD + 编译 + 预览。

**改文件**: `/home/guorongxiao/c2eos/src/pages/GuardrailsView.tsx`（新建）

**源文件**: `/home/guorongxiao/ceos_new/src/components/AIPWorkbench/GuardrailsView.tsx`（1466行）

**适配要求**:
- 护栏CRUD → 对接到T4.3的新端点
- 编译/预览 → 对接到T4.3的新端点
- 审计日志 → stub（等审计后端就绪）

**验收**:
```bash
cd /home/guorongxiao/c2eos && npx tsc --noEmit 2>&1 | grep -i "guardrail"  # 期望: 0 errors
```

---

## Phase 4: 后端新增端点（BE，1天）

### T4.1: Agent Action执行端点（2h，BE）

**目标**: Agent提议Action后，用户审批通过时后端执行写入。

**改文件**: 
- `/home/guorongxiao/databridge-v2/dccheng/dccheng-impl/src/main/java/com/chinacreator/gzcm/dccheng/ontology/controller/OntologyActionApiController.java`（新建或扩展现有）

**端点**:
```java
POST /api/v1/ontology/actions/{id}/execute  → 执行审批通过的Action
GET  /api/v1/ontology/actions/{id}/proposals → 查看Action的待审批提案列表
```

**验收**:
```bash
# 获取token（参见T4.1原有验收）
# 执行Action
curl -s -X POST -H @/tmp/auth_header.txt \
  http://localhost:8080/api/v1/ontology/actions/act_reschedule_flight/execute \
  -H "Content-Type: application/json" \
  -d '{"flight_number":"UA102","new_status":"DELAYED","delay_minutes":120}' \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print('PASS' if d.get('code')==0 else 'FAIL')"
```

### T4.2: Knowledge API端点（2h，BE）

**目标**: Neo4j知识图谱的同步状态查询 + 知识查询接口。

**改文件**: `/home/guorongxiao/databridge-v2/dccheng/dccheng-impl/src/main/java/com/chinacreator/gzcm/dccheng/knowledge/KnowledgeApiController.java`（新建）

**端点**:
```java
GET  /api/v1/knowledge/index-status     → Neo4j索引状态（节点数/关系数/最近同步时间）
POST /api/v1/knowledge/sync             → 触发全量KG同步（复用现有ObjectKgSyncService）
POST /api/v1/knowledge/query            → 知识查询（Cypher或自然语言→Cypher转换）
```

**验收**:
```bash
# 索引状态
curl -s -H @/tmp/auth_header.txt http://localhost:8080/api/v1/knowledge/index-status \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print('PASS' if d.get('code')==0 and d.get('data',{}).get('nodeCount',0)>0 else 'FAIL')"

# 触发同步
curl -s -X POST -H @/tmp/auth_header.txt http://localhost:8080/api/v1/knowledge/sync \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print('PASS' if d.get('code')==0 else 'FAIL')"
```

### T4.3: Guardrails Policy API（1.5h，BE）

**目标**: 安全护栏策略的CRUD + 编译 + 预览。

**改文件**: `/home/guorongxiao/databridge-v2/dccheng/dccheng-impl/src/main/java/com/chinacreator/gzcm/dccheng/guardrails/GuardrailsApiController.java`（新建）

**端点**:
```java
GET    /api/v1/guardrails/policies       → 策略列表
POST   /api/v1/guardrails/policies       → 创建策略
PUT    /api/v1/guardrails/policies/{id}   → 更新策略
DELETE /api/v1/guardrails/policies/{id}   → 删除策略
POST   /api/v1/guardrails/policies/{id}/compile → 编译策略
GET    /api/v1/guardrails/policies/{id}/preview  → 预览策略效果
```

**数据库**: 新建表 `ecos_guardrail_policy`(id, name, type, description, is_enabled, severity, parameters_json, created_at)

**验收**:
```bash
# 创建策略
curl -s -X POST -H @/tmp/auth_header.txt \
  http://localhost:8080/api/v1/guardrails/policies \
  -H "Content-Type: application/json" \
  -d '{"name":"PII脱敏","type":"pii_redaction","severity":"block","isEnabled":true}' \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print('PASS' if d.get('code')==0 else 'FAIL')"
```

---

## Phase 5: 路由注册 + 编译 + QA（FE+QA，0.5天）

### T5.1: 路由注册新页面（0.5h，FE）

**目标**: c2eos路由表注册KnowledgeView、GuardrailsView、Copilot。

**改文件**: `/home/guorongxiao/c2eos/src/main.tsx`

**新增路由**:
```tsx
<Route path="knowledge" element={<KnowledgeView />} />
<Route path="guardrails" element={<GuardrailsView />} />
```

**Copilot**: 注入到全局Layout，不占用路由。

**验收**:
```bash
curl -s -o /dev/null -w "%{http_code}" http://localhost:3000/knowledge  # 200
curl -s -o /dev/null -w "%{http_code}" http://localhost:3000/guardrails  # 200
```

### T5.2: 编译零错误（0.5h，FE）

```bash
cd /home/guorongxiao/c2eos && npx tsc --noEmit 2>&1 | grep "error TS" | grep -v "api.ts\|DictManager\|KnowledgeGraph\|CommandPalette\|DatasetExplorer\|ScenarioSandbox" | wc -l
# 期望: 0（只允许既存错误，不允许新增错误）
```

### T5.3: QA 10项验证（QA，3h）

| # | 测试场景 | 验证方式 | 期望 |
|---|---------|----------|------|
| 1 | Copilot打开/关闭 | 浏览器点击Copilot图标 | 侧抽屉滑出/收起 |
| 2 | Copilot导航 | 输入"查看对象" → 点击建议 | 页面跳转到本体工作台 |
| 3 | Agent Action Proposal | Agent对话框中输入"UA102延误" | 出现审批卡片 |
| 4 | Agent审批通过 | 点击"批准" | 系统通知+审计日志 |
| 5 | KnowledgeView加载 | 访问/knowledge | 显示KG索引状态 |
| 6 | Knowledge同步 | 点击"同步"按钮 | KG同步完成 |
| 7 | StepGuide拖拽 | 拖拽算子到管道 | 算子高亮放置区 |
| 8 | Guardrails策略CRUD | 创建→编辑→删除一条策略 | 全部200 |
| 9 | Guardrails编译 | 点击"编译"按钮 | 返回编译结果 |
| 10 | 编译零新增错误 | `npx tsc --noEmit` | PMO新文件0 error |

**QA判定**: 全部10项 PASS → 汇报肖总。任何 FAIL → 打回。

---

## 6. 执行顺序

```
Phase 1: T1.1 (Copilot移植) → T1.2 (入口集成)
Phase 4: T4.1 (Action执行) + T4.2 (Knowledge API) + T4.3 (Guardrails API) — 可并行
  ↓
Phase 2: T2.1 (AgentStudio升级) → 依赖T4.1
         T2.2 (KnowledgeView)    → 依赖T4.2
Phase 3: T3.1 (StepGuide) + T3.2 (GuardrailsView) — 可并行，T3.2依赖T4.3
  ↓
Phase 5: T5.1 (路由) → T5.2 (编译) → T5.3 (QA)
```

**可并行**: Phase 1与Phase 4 | T2.1与T2.2 | T3.1与T3.2 | T4.1/T4.2/T4.3三者

---

## 7. 交付检查清单

| Task | 负责人 | 交付物 | 验收 |
|------|--------|--------|------|
| T1.1 | FE | copilot/AIPCopilotDrawer.tsx | tsc 0 |
| T1.2 | FE | Layout.tsx Copilot入口 | curl 200 |
| T2.1 | FE | AgentStudio.tsx升级 | tsc 0 |
| T2.2 | FE | KnowledgeView.tsx | tsc 0 |
| T3.1 | FE | InteractiveStepGuide.tsx | tsc 0 |
| T3.2 | FE | GuardrailsView.tsx | tsc 0 |
| T4.1 | BE | Action执行端点 | curl PASS |
| T4.2 | BE | Knowledge API | curl PASS |
| T4.3 | BE | Guardrails API | curl PASS |
| T5.1 | FE | 路由注册 | curl 200 |
| T5.2 | FE | 编译零新增错误 | tsc |
| T5.3 | QA | 10项验证 | 全部PASS |

---

## 一句话给PMO

**ceos_new更新了两版，4个新组件+3组新API。Copilot塞全局右下角，AgentStudio加审批卡片，KnowledgeView接Neo4j，Guardrails接策略表，StepGuide做管道演示。10项QA，一个FAIL打回。**
