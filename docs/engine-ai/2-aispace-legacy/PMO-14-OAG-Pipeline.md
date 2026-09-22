# PMO指令：Phase2-3-OAG — OAG Pipeline引擎（8步闭环）

> 来源: 完善计划 Phase 2-3 T6 | 工期: 3周 | 范围: ai-engine核心新增 | 依赖: Phase 2-2 (ActionType+安全集成)

---

## §背景

ECOS有Agent Loop/Tool系统/Ontology/Security，但各引擎独立——缺一个串联8步的"OAG Pipeline"。这是Palantir AIP的核心差异化能力。

---

## §禁止清单

1. ❌ OAG Pipeline不替代AgentLoop——OAG是AgentLoop的上层编排，AgentLoop仍是底层执行引擎
2. ❌ 不新增Maven模块——OAG Pipeline在ai-engine-impl中实现
3. ❌ 每个节点失败时不静默吞掉——返回该节点的错误详情给用户
4. ❌ OAG SSE事件必须按 step1→step8 顺序发送（不跳步）

---

## §架构设计

```
   用户请求
      │
      ▼
┌──────────────────────────────────────────────┐
│             OAG Pipeline Engine              │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐       │
│  │Step 1│→│Step 2│→│Step 3│→│Step 4│→...→ 8 │
│  │意图  │ │实体  │ │本体  │ │权限  │         │
│  │识别  │ │抽取  │ │检索  │ │校验  │         │
│  └──────┘ └──────┘ └──────┘ └──────┘       │
│       ↓         ↓         ↓         ↓        │
│  cognitive   cognitive  ontology  security   │
│  -engine     -engine    -engine   -engine    │
└──────────────────────────────────────────────┘
```

---

## §Task

### T6-1: OAG Pipeline DAG执行器（3天）

**文件**: 新建 `ai-engine-impl/.../oag/OagPipelineEngine.java`

**核心逻辑**:
```java
public OagResult execute(OagRequest request) {
    OagContext ctx = new OagContext(request);
    
    for (OagStep step : PIPELINE) {
        ctx.emitEvent(step.name, "start");
        try {
            step.execute(ctx);
            ctx.emitEvent(step.name, "done", ctx.getStepResult(step));
        } catch (Exception e) {
            ctx.emitEvent(step.name, "error", Map.of("message", e.getMessage()));
            return OagResult.fail(ctx, step, e);
        }
    }
    return OagResult.success(ctx);
}
```

### T6-2: 8个节点处理器实现（7天）

| 节点 | 文件 | 对接引擎 | 核心逻辑 |
|------|------|------|------|
| Step1-Intent | `IntentRecognizer.java` | cognitive-engine KG | NLU→意图类型(查询/操作/咨询) |
| Step2-Entity | `EntityExtractor.java` | cognitive-engine KG | NER→实体→ObjectType匹配 |
| Step3-Ontology | `OntologyRetriever.java` | ontology-engine | 图遍历+属性校验 |
| Step4-Security | `SecurityChecker.java` | security-engine | RLS/CLS/ABAC全链路 |
| Step5-Prompt | `OagPromptBuilder.java` | kb-engine | 结构化Ontology语义注入LLM |
| Step6-LLM | `LlmInferencer.java` | ai-engine AgentLoop | 调AgentLoop推理 |
| Step7-Validate | `ResultValidator.java` | cognitive-engine | 业务规则+数据一致性校验 |
| Step8-Respond | `StructuredResponder.java` | ontology-engine | 格式化回复+可选ActionType执行 |

### T6-3: OAG Controller + SSE（3天）

**文件**: 新建 `ai-engine-impl/.../controller/OagController.java`

**端点**: `POST /api/v1/oag/chat`

**SSE事件流**:
```
event:step1_start
data:{"step":"intent_recognition","status":"start"}

event:step1_done
data:{"step":"intent_recognition","status":"done","result":{"intent":"data_query","confidence":0.95,"objectTypes":["PurchaseOrder"]}}

... step2→step3→...→step8

event:done
data:{"final":{"content":"本月华东区采购订单总额：1,234,567元...","sources":["erp_db.purchase_orders","hr_db.org_structure"]}}
```

### T6-4: 前端OAG对话面板（3天）

**文件**: 重写 `components/aiworkbench/chatbot/ChatPanel.tsx`

**新增**:
- OAG进度条（8步横条，当前步高亮，已完步绿色，失败步红色）
- 消息附带"元数据卡"：涉及对象/权限过滤/数据来源
- SSE流式更新进度条

**验收**:
```bash
curl -N -X POST /api/v1/oag/chat \
  -H "Content-Type: application/json" \
  -d '{"query":"本月华东区采购订单总额及供应商排名","userId":"admin"}'
# 期望: SSE流式输出8步事件
# event:step1_start → step1_done → ... → step8_done → done
```
