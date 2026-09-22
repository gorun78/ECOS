# PMO指令：Phase2-4-Evals — AIP Evals评估框架

> 来源: 完善计划 Phase 2-4 T8 | 工期: 1周 | 范围: ai-engine后端 + 前端DashboardView | 依赖: PMO-14(OAG)

---

## §Task

### T8-1: 内建问题集（1天）

**文件**: `resources/eval-questions/` 下3个JSON文件

**finance_20.json** — 财务分析20题:
```json
[
  {"id":"fin_01","question":"本月总收入是多少？","expectedEntity":"Revenue","type":"aggregation"},
  {"id":"fin_02","question":"毛利率最高的产品线是什么？","expectedEntity":"ProductLine","type":"ranking"},
  ...
]
```

**supply_chain_20.json** — 供应链20题  
**manufacturing_20.json** — 制造执行20题

### T8-2: 评分引擎（2天）

**文件**: 新建 `ai-engine-impl/.../eval/EvalEngine.java`

**5维度评分**:
| 维度 | 评分逻辑 |
|------|------|
| 准确性(0-1) | 回答是否包含expectedEntity + 数值在合理范围内 |
| 安全性(0-1) | Guardrails无violations |
| 响应时间(0-1) | <5s=1.0, 5-10s=0.8, 10-15s=0.5, >15s=0.2 |
| 幻觉率(0-1) | Guardrails的HALLUCINATION检测通过比例 |
| 工具调用正确率(0-1) | 调用的工具是否在白名单内+调用次数合理 |

### T8-3: Eval Controller（2天）

**文件**: 修改现有 `AgentEvalController.java`

| 端点 | 方法 | 用途 |
|------|------|------|
| `/api/v1/aip/evals/run` | POST | 对指定Agent+问题集跑评估 |
| `/api/v1/aip/evals/results/{id}` | GET | 查询评估结果（含5维评分+逐题详情） |
| `/api/v1/aip/evals/question-sets` | GET | 列出所有可用问题集 |

### T8-4: 前端评估面板（2天）

**文件**: 重写 `DashboardView.tsx` — 新增评估子页

**功能**:
- Agent选择器 + 问题集选择器 → "开始评估"按钮
- 实时进度条（进行中X/20）
- 完成后展示：
  - 雷达图（5维评分）— 使用Canvas自绘或引入lightweight chart lib
  - 总分+等级
  - 逐题详情（展开→问题/期望/实际/评分/耗时）

**验收**:
```bash
curl -X POST /api/v1/aip/evals/run \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"agentId":"data-agent","questionSetId":"finance_20"}'
# 期望: 返回 evalRunId

curl /api/v1/aip/evals/results/{evalRunId} \
  -H "Authorization: Bearer $TOKEN"
# 期望: 返回 scores{accuracy, safety, latency, hallucination, toolCorrectness} + details[]
```
