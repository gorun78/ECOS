# ECOS 认知引擎接口契约文档

> **文档标识**: cognitive-api-contract v1.0
> **生效日期**: 2026-06-26
> **关联模块**: `databridge-cognitive` (🆕 新建)
> **源码包**: `com.chinacreator.gzcm.cognitive`
> **Controller**: `CognitiveController` — `@RequestMapping("/api/v1/cognitive")`
> **统一响应格式**: `com.chinacreator.gzcm.common.base.ApiResponse<T>`

---

## 目录

1. [接口概览](#1-接口概览)
2. [统一响应格式](#2-统一响应格式)
3. [通用错误码](#3-通用错误码)
4. [接口定义](#4-接口定义)
   - [4.1 GET /api/v1/cognitive/blueprint — 六层蓝图健康度](#41-get-apiv1cognitiveblueprint--六层蓝图健康度)
   - [4.2 POST /api/v1/cognitive/reason — 规则推理 / 因果分析](#42-post-apiv1cognitivereason--规则推理--因果分析)
   - [4.3 POST /api/v1/cognitive/optimize — 帕累托优化](#43-post-apiv1cognitiveoptimize--帕累托优化)
   - [4.4 POST /api/v1/cognitive/plan — 创建执行计划](#44-post-apiv1cognitiveplan--创建执行计划)
   - [4.5 GET /api/v1/cognitive/plan/{id} — 查询执行计划](#45-get-apiv1cognitiveplanid--查询执行计划)
   - [4.6 GET /api/v1/cognitive/health — 认知引擎健康检查](#46-get-apiv1cognitivehealth--认知引擎健康检查)
   - [4.7 GET /api/v1/cognitive/rules — 查询规则引擎规则库](#47-get-apiv1cognitiverules--查询规则引擎规则库)
5. [响应对象模型](#5-响应对象模型)
6. [版本历史](#6-版本历史)

---

## 1. 接口概览

| # | 方法 | 路径 | 摘要 | 推理器 |
|:--:|------|------|------|:------:|
| 1 | `GET` | `/api/v1/cognitive/blueprint` | 六层蓝图健康度 | — |
| 2 | `POST` | `/api/v1/cognitive/reason` | 规则推理 / 因果分析 | RuleEngine / CausalReasoner |
| 3 | `POST` | `/api/v1/cognitive/optimize` | 帕累托多目标优化 | NsgaIIOptimizer |
| 4 | `POST` | `/api/v1/cognitive/plan` | 创建执行计划 | PlanGenerator |
| 5 | `GET` | `/api/v1/cognitive/plan/{id}` | 查询执行计划 | — |
| 6 | `GET` | `/api/v1/cognitive/health` | 认知引擎健康检查 | — |
| 7 | `GET` | `/api/v1/cognitive/rules` | 查询规则引擎规则库 | — |

> **设计原则**: 三个推理器（RuleEngine、CausalReasoner、NsgaIIOptimizer）均为 `databridge-cognitive` 模块内部组件，不独立暴露端点。`CognitiveController` 是唯一对外入口。

---

## 2. 统一响应格式

所有接口返回 `com.chinacreator.gzcm.common.base.ApiResponse<T>`，JSON 结构如下：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": { ... },
  "success": true,
  "timestamp": 1719398400000
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | `int` | 响应码。`200` = 成功；`4xx/5xx` = 失败 |
| `message` | `string` | 响应消息 |
| `data` | `object` / `array` / `null` | 业务数据。失败时为 `null` |
| `success` | `boolean` | 是否成功 |
| `timestamp` | `long` | Unix 毫秒时间戳 |

---

## 3. 通用错误码

| code | message 模板 | 说明 |
|:----:|------|------|
| `200` | `"操作成功"` | 成功 |
| `400` | `"请求参数校验失败: {具体原因}"` | 参数校验不通过 |
| `401` | `"未认证或 Token 已过期"` | 鉴权失败 |
| `403` | `"无权限访问该资源"` | 鉴权通过但权限不足 |
| `404` | `"资源不存在: {resourceType}/{id}"` | 资源未找到 |
| `409` | `"资源冲突: {原因}"` | 并发冲突 / 重复创建 |
| `500` | `"服务器内部错误: {原因}"` | 未预期异常 |
| `503` | `"认知引擎推理器不可用: {reasoner}"` | 推理器降级/不可用 |

---

## 4. 接口定义

### 4.1 GET /api/v1/cognitive/blueprint — 六层蓝图健康度

> 对应前端 `CognitiveOperatingSystem.tsx` 页面主数据源，汇总 ECOS 六层架构的实时健康状态。

**请求**:

```
GET /api/v1/cognitive/blueprint?layer=L3
```

| 参数 | 位置 | 类型 | 必填 | 说明 |
|------|------|------|:--:|------|
| `layer` | Query | `string` | 否 | 过滤指定层：`L1`~`L6`。不传返回全部六层 |

**请求示例**:

```bash
curl -X GET "http://localhost:8080/api/v1/cognitive/blueprint" \
  -H "Authorization: Bearer <token>"
```

**成功响应** `200`:

```json
{
  "code": 200,
  "message": "success",
  "success": true,
  "timestamp": 1719398400000,
  "data": {
    "generatedAt": "2026-06-26T10:30:00Z",
    "overallScore": 87.5,
    "layers": [
      {
        "layerId": "L1",
        "name": "数据接入层",
        "score": 92.0,
        "status": "HEALTHY",
        "metrics": {
          "activeStreams": 12,
          "throughput": "3500 rec/s",
          "latencyP99": "120ms",
          "errorRate": 0.02
        },
        "alerts": []
      },
      {
        "layerId": "L2",
        "name": "数据存储层",
        "score": 88.0,
        "status": "HEALTHY",
        "metrics": {
          "storageUsage": "68.5%",
          "connectionPool": "45/200",
          "queryLatencyP99": "85ms"
        },
        "alerts": []
      },
      {
        "layerId": "L3",
        "name": "数据治理层",
        "score": 74.0,
        "status": "WARNING",
        "metrics": {
          "dqRulesActive": 156,
          "dqRulesFailing": 8,
          "lineageCoverage": "92%",
          "freshness": "15min"
        },
        "alerts": [
          {
            "severity": "WARNING",
            "message": "8 条 DQ 规则未通过，涉及表: t_order, t_payment",
            "raisedAt": "2026-06-26T10:25:00Z"
          }
        ]
      },
      {
        "layerId": "L4",
        "name": "数据分析层",
        "score": 91.0,
        "status": "HEALTHY",
        "metrics": {
          "activeQueries": 23,
          "avgResponseTime": "320ms",
          "cacheHitRate": 0.78
        },
        "alerts": []
      },
      {
        "layerId": "L5",
        "name": "数据服务层",
        "score": 85.0,
        "status": "HEALTHY",
        "metrics": {
          "apiQPS": 420,
          "apiErrorRate": 0.01,
          "activeConsumers": 18
        },
        "alerts": []
      },
      {
        "layerId": "L6",
        "name": "数据应用层",
        "score": 95.0,
        "status": "HEALTHY",
        "metrics": {
          "activeDashboards": 8,
          "activeReports": 35,
          "userSessions": 128
        },
        "alerts": []
      }
    ],
    "recommendations": [
      {
        "layerId": "L3",
        "type": "DQ_FIX",
        "priority": "HIGH",
        "message": "建议排查并修复 t_order / t_payment 的 8 条失败 DQ 规则"
      }
    ]
  }
}
```

**失败响应** `500`:

```json
{
  "code": 500,
  "message": "服务器内部错误: 健康度聚合查询 KG/WM 超时",
  "success": false,
  "timestamp": 1719398400000,
  "data": null
}
```

---

### 4.2 POST /api/v1/cognitive/reason — 规则推理 / 因果分析

> 统一推理入口。通过 `mode` 参数选择推理器：
> - `rule` — RuleEngine 规则匹配
> - `causal` — CausalReasoner 因果链推理

**请求**:

```
POST /api/v1/cognitive/reason
Content-Type: application/json
```

**请求体参数**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `mode` | `string` | 是 | 推理模式：`"rule"` / `"causal"` |
| `facts` | `object` | 是 | 事实数据（键值对） |
| `context` | `object` | 否 | 上下文约束（数据源、时间窗口等） |
| `options` | `object` | 否 | 推理选项（最大深度、阈值等） |

**请求示例 (规则推理)**:

```bash
curl -X POST "http://localhost:8080/api/v1/cognitive/reason" \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "mode": "rule",
    "facts": {
      "tableName": "t_order",
      "dqCheckFailed": true,
      "dqFailedRules": ["NULL_CHECK_id", "RANGE_VIOLATION_amount"],
      "pipelineStatus": "RUNNING",
      "recentLatency": "5.2s"
    },
    "context": {
      "datasource": "doris-prod",
      "timeWindow": "5min"
    }
  }'
```

**成功响应 (规则推理)** `200`:

```json
{
  "code": 200,
  "message": "success",
  "success": true,
  "timestamp": 1719398400000,
  "data": {
    "mode": "rule",
    "matchedRules": [
      {
        "ruleId": "RULE-001",
        "ruleName": "DQ失败紧急响应",
        "description": "当 DQ 规则失败数超过阈值时，暂停管道并告警",
        "confidence": 0.95,
        "actions": [
          {
            "type": "PAUSE_PIPELINE",
            "target": "pipeline-order-etl",
            "params": { "reason": "DQ_FAILURE" }
          },
          {
            "type": "NOTIFY",
            "target": "ops-channel",
            "params": { "level": "P1", "message": "t_order DQ 失败" }
          }
        ]
      },
      {
        "ruleId": "RULE-014",
        "ruleName": "高延迟管道降级",
        "description": "管道延迟超过 5s 时触发降级策略",
        "confidence": 0.82,
        "actions": [
          {
            "type": "THROTTLE",
            "target": "pipeline-order-etl",
            "params": { "rate": "50%" }
          }
        ]
      }
    ],
    "reasoningPath": [
      "事实 → RULE-001 命中 (dqCheckFailed=true ∧ dqFailedRules>0)",
      "事实 → RULE-014 命中 (recentLatency≥5s)"
    ],
    "elapsedMs": 12
  }
}
```

**请求示例 (因果推理)**:

```json
{
  "mode": "causal",
  "facts": {
    "event": "DATA_LATENCY_SPIKE",
    "layer": "L1",
    "observedAt": "2026-06-26T10:15:00Z",
    "affectedStreams": ["order-stream", "payment-stream"]
  },
  "options": {
    "maxDepth": 5,
    "confidenceThreshold": 0.6,
    "maxPaths": 10
  }
}
```

**成功响应 (因果推理)** `200`:

```json
{
  "code": 200,
  "message": "success",
  "success": true,
  "timestamp": 1719398400000,
  "data": {
    "mode": "causal",
    "rootCauses": [
      {
        "nodeId": "KG_NODE_DB_CONN_01",
        "nodeLabel": "Doris FE 连接池耗尽",
        "layer": "L2",
        "confidence": 0.91,
        "impactScore": 0.85
      }
    ],
    "causalPaths": [
      {
        "pathId": "PATH-001",
        "nodes": [
          { "nodeId": "KG_NODE_DB_CONN_01", "label": "Doris FE 连接池耗尽", "layer": "L2" },
          { "nodeId": "KG_NODE_DQ_TIMEOUT", "label": "DQ 规则执行超时", "layer": "L3" },
          { "nodeId": "KG_NODE_LATENCY_SPIKE", "label": "数据接入延迟飙升", "layer": "L1" }
        ],
        "totalConfidence": 0.88,
        "pathLength": 3
      },
      {
        "pathId": "PATH-002",
        "nodes": [
          { "nodeId": "KG_NODE_KAFKA_LAG", "label": "Kafka 消费 Lag 增大", "layer": "L1" },
          { "nodeId": "KG_NODE_LATENCY_SPIKE", "label": "数据接入延迟飙升", "layer": "L1" }
        ],
        "totalConfidence": 0.72,
        "pathLength": 2
      }
    ],
    "kgNodesVisited": 156,
    "elapsedMs": 45
  }
}
```

**失败响应** `400`:

```json
{
  "code": 400,
  "message": "请求参数校验失败: mode 不在允许范围 [rule, causal]",
  "success": false,
  "timestamp": 1719398400000,
  "data": null
}
```

---

### 4.3 POST /api/v1/cognitive/optimize — 帕累托优化

> 自研 NSGA-II 轻量实现，求解多目标帕累托前沿。

**请求**:

```
POST /api/v1/cognitive/optimize
Content-Type: application/json
```

**请求体参数**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `problem` | `object` | 是 | 多目标优化问题定义 |
| `problem.variables` | `array` | 是 | 决策变量列表 |
| `problem.constraints` | `array` | 否 | 约束列表 |
| `problem.objectives` | `array` | 是 | 目标函数列表（每个目标指定 direction: MIN/MAX） |
| `options` | `object` | 否 | 算法参数（种群大小、迭代代数等） |

**请求示例**:

```bash
curl -X POST "http://localhost:8080/api/v1/cognitive/optimize" \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "problem": {
      "variables": [
        { "name": "pipelineParallelism", "type": "INTEGER", "min": 1, "max": 16 },
        { "name": "batchSize", "type": "INTEGER", "min": 100, "max": 10000 },
        { "name": "retryCount", "type": "INTEGER", "min": 0, "max": 5 }
      ],
      "constraints": [
        { "expression": "pipelineParallelism * batchSize <= 50000", "description": "内存约束" },
        { "expression": "retryCount >= 1 if pipelineParallelism > 8", "description": "高并发重试策略" }
      ],
      "objectives": [
        { "name": "throughput", "direction": "MAX", "description": "数据吞吐量 (rec/s)" },
        { "name": "cost", "direction": "MIN", "description": "计算资源成本 (CU/h)" },
        { "name": "latency", "direction": "MIN", "description": "端到端延迟 (ms)" }
      ]
    },
    "options": {
      "populationSize": 100,
      "maxGenerations": 200,
      "crossoverRate": 0.9,
      "mutationRate": 0.1
    }
  }'
```

**成功响应** `200`:

```json
{
  "code": 200,
  "message": "success",
  "success": true,
  "timestamp": 1719398400000,
  "data": {
    "frontId": "PF-20260626-001",
    "generations": 142,
    "elapsedMs": 380,
    "convergedAt": 142,
    "paretoFront": [
      {
        "solutionId": "SOL-001",
        "rank": 1,
        "variables": { "pipelineParallelism": 8, "batchSize": 2500, "retryCount": 2 },
        "objectives": { "throughput": 6200.0, "cost": 12.5, "latency": 85.0 },
        "crowdingDistance": 0.42,
        "feasibility": "FEASIBLE"
      },
      {
        "solutionId": "SOL-002",
        "rank": 1,
        "variables": { "pipelineParallelism": 4, "batchSize": 5000, "retryCount": 1 },
        "objectives": { "throughput": 4800.0, "cost": 7.2, "latency": 140.0 },
        "crowdingDistance": 0.61,
        "feasibility": "FEASIBLE"
      },
      {
        "solutionId": "SOL-003",
        "rank": 1,
        "variables": { "pipelineParallelism": 12, "batchSize": 1500, "retryCount": 3 },
        "objectives": { "throughput": 7500.0, "cost": 18.0, "latency": 65.0 },
        "crowdingDistance": 0.33,
        "feasibility": "FEASIBLE"
      }
    ],
    "problemSummary": {
      "variableCount": 3,
      "constraintCount": 2,
      "objectiveCount": 3,
      "feasibleSolutions": 87,
      "infeasibleSolutions": 13
    }
  }
}
```

**失败响应** `400`:

```json
{
  "code": 400,
  "message": "请求参数校验失败: problem.variables 不能为空",
  "success": false,
  "timestamp": 1719398400000,
  "data": null
}
```

**失败响应** `500`:

```json
{
  "code": 500,
  "message": "服务器内部错误: NSGA-II 种群初始化失败 — 无可行解",
  "success": false,
  "timestamp": 1719398400000,
  "data": null
}
```

---

### 4.4 POST /api/v1/cognitive/plan — 创建执行计划

> 将推理产出（规则匹配动作 / 因果根因 / 帕累托最优解）编排为可下发给 Hermes 执行的 `ExecutionPlan`。

**请求**:

```
POST /api/v1/cognitive/plan
Content-Type: application/json
```

**请求体参数**:

| 字段 | 类型 | 必填 | 说明 |
|------|------|:--:|------|
| `source` | `object` | 是 | 计划来源（关联的推理/优化结果） |
| `source.type` | `string` | 是 | `"reason"` (来自 reason 接口) / `"optimize"` (来自 optimize 接口) / `"manual"` |
| `source.reasonId` | `string` | 条件 | reason 返回的推理结果 ID（source=reason 时必填） |
| `source.solutionId` | `string` | 条件 | optimize 返回的解 ID（source=optimize 时必填） |
| `name` | `string` | 否 | 计划名称 |
| `priority` | `string` | 否 | 优先级：`P0`/`P1`/`P2`/`P3`，默认 `P2` |
| `target` | `object` | 是 | 执行目标 |
| `target.agentId` | `string` | 否 | 指定 Hermes Agent ID |
| `target.workflow` | `string` | 否 | 指定工作流类型 |

**请求示例**:

```bash
curl -X POST "http://localhost:8080/api/v1/cognitive/plan" \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "source": {
      "type": "reason",
      "reasonId": "REASON-20260626-001"
    },
    "name": "t_order DQ失败修复计划",
    "priority": "P1",
    "target": {
      "agentId": "hermes-dq-agent",
      "workflow": "dq-remediation"
    }
  }'
```

**成功响应** `200`:

```json
{
  "code": 200,
  "message": "执行计划创建成功",
  "success": true,
  "timestamp": 1719398400000,
  "data": {
    "planId": "PLAN-20260626-001",
    "name": "t_order DQ失败修复计划",
    "status": "PENDING",
    "priority": "P1",
    "source": {
      "type": "reason",
      "reasonId": "REASON-20260626-001"
    },
    "steps": [
      {
        "stepId": "STEP-1",
        "order": 1,
        "action": "PAUSE_PIPELINE",
        "target": "pipeline-order-etl",
        "params": { "reason": "DQ_FAILURE" },
        "dependsOn": []
      },
      {
        "stepId": "STEP-2",
        "order": 2,
        "action": "RUN_DQ_CHECK",
        "target": "t_order",
        "params": { "rules": ["NULL_CHECK_id", "RANGE_VIOLATION_amount"] },
        "dependsOn": ["STEP-1"]
      },
      {
        "stepId": "STEP-3",
        "order": 3,
        "action": "NOTIFY",
        "target": "ops-channel",
        "params": { "level": "P1", "channel": "wecom" },
        "dependsOn": ["STEP-2"]
      }
    ],
    "estimatedDurationMs": 45000,
    "createdAt": "2026-06-26T10:31:00Z",
    "createdBy": "system"
  }
}
```

**失败响应** `409`:

```json
{
  "code": 409,
  "message": "资源冲突: 相同来源计划已存在 PLAN-20260626-000",
  "success": false,
  "timestamp": 1719398400000,
  "data": null
}
```

---

### 4.5 GET /api/v1/cognitive/plan/{id} — 查询执行计划

> 查询指定计划的详情及当前执行状态。

**请求**:

```
GET /api/v1/cognitive/plan/{planId}
```

| 参数 | 位置 | 类型 | 必填 | 说明 |
|------|------|------|:--:|------|
| `planId` | Path | `string` | 是 | 执行计划 ID，如 `PLAN-20260626-001` |

**请求示例**:

```bash
curl -X GET "http://localhost:8080/api/v1/cognitive/plan/PLAN-20260626-001" \
  -H "Authorization: Bearer <token>"
```

**成功响应** `200`:

```json
{
  "code": 200,
  "message": "success",
  "success": true,
  "timestamp": 1719398400000,
  "data": {
    "planId": "PLAN-20260626-001",
    "name": "t_order DQ失败修复计划",
    "status": "EXECUTING",
    "priority": "P1",
    "progress": {
      "totalSteps": 3,
      "completedSteps": 1,
      "currentStep": "STEP-2",
      "percentage": 33
    },
    "steps": [
      {
        "stepId": "STEP-1",
        "order": 1,
        "action": "PAUSE_PIPELINE",
        "target": "pipeline-order-etl",
        "status": "SUCCESS",
        "startedAt": "2026-06-26T10:31:05Z",
        "completedAt": "2026-06-26T10:31:08Z",
        "durationMs": 3000
      },
      {
        "stepId": "STEP-2",
        "order": 2,
        "action": "RUN_DQ_CHECK",
        "target": "t_order",
        "status": "EXECUTING",
        "startedAt": "2026-06-26T10:31:08Z"
      },
      {
        "stepId": "STEP-3",
        "order": 3,
        "action": "NOTIFY",
        "target": "ops-channel",
        "status": "PENDING"
      }
    ],
    "hermesSessionId": "SESS-abc123",
    "createdAt": "2026-06-26T10:31:00Z",
    "updatedAt": "2026-06-26T10:31:10Z"
  }
}
```

**失败响应** `404`:

```json
{
  "code": 404,
  "message": "资源不存在: plan/PLAN-99999999-001",
  "success": false,
  "timestamp": 1719398400000,
  "data": null
}
```

---

### 4.6 GET /api/v1/cognitive/health — 认知引擎健康检查

> 探活端点，返回认知引擎三推理器就绪状态。供 Gateway 健康检查、Prometheus 采集。

**请求**:

```
GET /api/v1/cognitive/health
```

**请求示例**:

```bash
curl -X GET "http://localhost:8080/api/v1/cognitive/health"
```

**成功响应** `200`:

```json
{
  "code": 200,
  "message": "success",
  "success": true,
  "timestamp": 1719398400000,
  "data": {
    "service": "databridge-cognitive",
    "status": "UP",
    "version": "1.0.0-SNAPSHOT",
    "reasoners": {
      "ruleEngine": { "status": "UP", "rulesLoaded": 34, "lastReloadAt": "2026-06-26T10:00:00Z" },
      "causalReasoner": { "status": "UP", "kgNodesCached": 12580, "kgConnection": "CONNECTED" },
      "nsgaIIOptimizer": { "status": "UP", "activeSessions": 2 }
    },
    "dependencies": {
      "knowledgeGraph": "UP",
      "worldModel": "UP"
    },
    "uptime": "3d 14h 22m"
  }
}
```

**降级响应 (部分推理器不可用)** `200`:

```json
{
  "code": 200,
  "message": "部分推理器降级",
  "success": true,
  "timestamp": 1719398400000,
  "data": {
    "service": "databridge-cognitive",
    "status": "DEGRADED",
    "version": "1.0.0-SNAPSHOT",
    "reasoners": {
      "ruleEngine": { "status": "UP", "rulesLoaded": 34, "lastReloadAt": "2026-06-26T10:00:00Z" },
      "causalReasoner": { "status": "DOWN", "error": "Knowledge Graph 连接超时" },
      "nsgaIIOptimizer": { "status": "UP", "activeSessions": 0 }
    },
    "dependencies": {
      "knowledgeGraph": "DOWN",
      "worldModel": "UP"
    },
    "uptime": "3d 14h 22m"
  }
}
```

---

### 4.7 GET /api/v1/cognitive/rules — 查询规则引擎规则库

> 查询 RuleEngine 当前加载的规则。供管理界面使用。

**请求**:

```
GET /api/v1/cognitive/rules?category=dq&status=ACTIVE&page=1&pageSize=20
```

| 参数 | 位置 | 类型 | 必填 | 说明 |
|------|------|------|:--:|------|
| `category` | Query | `string` | 否 | 规则分类：`dq` / `pipeline` / `schema` / `security` |
| `status` | Query | `string` | 否 | 规则状态：`ACTIVE` / `DISABLED` |
| `keyword` | Query | `string` | 否 | 规则名称/描述模糊搜索 |
| `page` | Query | `int` | 否 | 页码，默认 `1` |
| `pageSize` | Query | `int` | 否 | 每页条数，默认 `20`，最大 `100` |

**请求示例**:

```bash
curl -X GET "http://localhost:8080/api/v1/cognitive/rules?category=dq&status=ACTIVE" \
  -H "Authorization: Bearer <token>"
```

**成功响应** `200`:

```json
{
  "code": 200,
  "message": "success",
  "success": true,
  "timestamp": 1719398400000,
  "data": {
    "total": 34,
    "page": 1,
    "pageSize": 20,
    "items": [
      {
        "ruleId": "RULE-001",
        "name": "DQ失败紧急响应",
        "category": "dq",
        "status": "ACTIVE",
        "priority": "P1",
        "description": "当 DQ 规则失败数超过阈值时，暂停管道并告警",
        "conditions": {
          "dqCheckFailed": true,
          "dqFailedCount": { "operator": "GT", "value": 3 }
        },
        "actions": [
          { "type": "PAUSE_PIPELINE", "target": "${pipelineName}" },
          { "type": "NOTIFY", "target": "ops-channel", "params": { "level": "P1" } }
        ],
        "createdAt": "2026-06-20T08:00:00Z",
        "updatedAt": "2026-06-25T16:30:00Z"
      }
    ]
  }
}
```

---

## 5. 响应对象模型

### BlueprintLayer (六层健康度单层)

| 字段 | 类型 | 说明 |
|------|------|------|
| `layerId` | `string` | 层次标识：`L1`~`L6` |
| `name` | `string` | 层次名称 |
| `score` | `float` | 健康评分 0~100 |
| `status` | `string` | `HEALTHY` / `WARNING` / `CRITICAL` / `DOWN` |
| `metrics` | `object` | 该层关键指标（各层不同，见 #4.1） |
| `alerts` | `Alert[]` | 活跃告警列表 |

### MatchedRule (规则匹配结果)

| 字段 | 类型 | 说明 |
|------|------|------|
| `ruleId` | `string` | 规则 ID |
| `ruleName` | `string` | 规则名称 |
| `description` | `string` | 规则描述 |
| `confidence` | `float` | 匹配置信度 0~1 |
| `actions` | `Action[]` | 触发的动作列表 |

### CausalPath (因果链路径)

| 字段 | 类型 | 说明 |
|------|------|------|
| `pathId` | `string` | 路径 ID |
| `nodes` | `CausalNode[]` | 因果节点序列（从因到果） |
| `totalConfidence` | `float` | 路径总体置信度 |
| `pathLength` | `int` | 路径长度 |

### ParetoSolution (帕累托解)

| 字段 | 类型 | 说明 |
|------|------|------|
| `solutionId` | `string` | 解 ID |
| `rank` | `int` | 非支配排序等级 (1 = Pareto Front) |
| `variables` | `object` | 决策变量取值 |
| `objectives` | `object` | 目标函数值 |
| `crowdingDistance` | `float` | 拥挤度距离 (NSGA-II) |
| `feasibility` | `string` | `FEASIBLE` / `INFEASIBLE` |

### ExecutionPlan (执行计划)

| 字段 | 类型 | 说明 |
|------|------|------|
| `planId` | `string` | 计划唯一 ID |
| `name` | `string` | 计划名称 |
| `status` | `string` | `PENDING` / `EXECUTING` / `SUCCESS` / `FAILED` / `CANCELLED` |
| `priority` | `string` | `P0`~`P3` |
| `source` | `object` | 来源追溯 |
| `steps` | `PlanStep[]` | 执行步骤序列 |
| `hermesSessionId` | `string` | Hermes 会话 ID（执行中时非空） |
| `createdAt` | `string` | ISO 8601 创建时间 |
| `updatedAt` | `string` | ISO 8601 更新时间 |

### PlanStep (执行步骤)

| 字段 | 类型 | 说明 |
|------|------|------|
| `stepId` | `string` | 步骤 ID |
| `order` | `int` | 执行顺序 (从 1 开始) |
| `action` | `string` | 动作类型 |
| `target` | `string` | 动作目标 |
| `params` | `object` | 动作参数 |
| `status` | `string` | `PENDING` / `EXECUTING` / `SUCCESS` / `FAILED` / `SKIPPED` |
| `dependsOn` | `string[]` | 前置依赖步骤 ID 列表 |
| `startedAt` | `string` | 开始时间 (ISO 8601) |
| `completedAt` | `string` | 完成时间 (ISO 8601) |
| `durationMs` | `long` | 执行耗时 (毫秒) |

---

## 6. 版本历史

| 版本 | 日期 | 作者 | 变更 |
|:----:|------|------|------|
| `v1.0` | 2026-06-26 | ECOS PMO / Hermes Agent | 初始版本 — 7 个接口定义，含请求/响应示例、错误码、对象模型 |

> **评审记录**: 肖总审批后会签于此。
