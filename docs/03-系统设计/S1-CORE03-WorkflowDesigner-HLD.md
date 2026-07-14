# S1-CORE03: Workflow Designer HLD

> **版本**: v1.0  
> **日期**: 2025-06-17  
> **作者**: ECOS-ARCH  
> **来源**: 第9章 Workflow & Process Automation 功能设计文档 (1029第9章工作流与流程自动化.txt, 1200行)

---

## 1. 模块定位

Workflow Platform 是 ECOS 的**业务流程执行中心**，负责将 Ontology 定义的业务动作（Action）编排为可执行的工作流（Workflow），实现审批流、任务流、人机协同、Case 管理等业务流程自动化。

```
Ontology → Object Runtime → Action & Rule → Workflow → Agent → Mission Control
      Entity → Object → Action → Workflow → Task → 人员/Agent → Result
```

对应 Palantir Foundry Workflow Builder + Operational Workflow + Case Management。

---

## 2. 现有代码分析

### 2.1 WorkflowController (`/api/v1/ecos/workflows`)

| 方法 | 路径 | 功能 | 差距 |
|------|------|------|------|
| GET | `/` | 工作流列表 | ✅ 完整（缺分页参数） |
| GET | `/{id}` | 工作流详情 | ✅ 完整 |
| POST | `/` | 创建工作流 | ⚠️ 创建为空壳（nodes/edges 可传JSON但无校验） |
| PUT | `/{id}` | 更新工作流 | ⚠️ 直接覆盖 JSON，无格式校验 |
| PATCH | `/{id}/publish` | 发布工作流 | ⚠️ 仅改 status，无校验/无版本快照 |
| POST | `/{id}/test` | 测试运行 | ⚠️ 基于拓扑排序模拟执行，无真实调度 |

### 2.2 WorkflowEngine

已实现的内部类:
- `WorkflowNode` — {id, type, label, config}
- `WorkflowEdge` — {source, target}
- `ExecutionStep` — {nodeId, nodeType, label, status, time, result}

当前支持节点类型:
- `start` — 开始节点（占位）
- `end` — 结束节点（占位）
- `condition` — 条件节点（mock）
- `task` / `action` — 任务/动作节点（模拟执行）
- `llm` — LLM 调用（mock）
- `api` — API 调用（mock）

### 2.3 WorkflowEntity

```java
{id, name, description, status, mode, nodes(JSON), edges(JSON), publishedAt, createdAt, updatedAt}
```

- `status`: "draft" → "active" (publish)
- `mode`: "sequential" | "parallel"

### 2.4 缺失严重分析

| 编号 | 设计文档模块 | 现有状态 | 优先级 |
|------|-------------|---------|--------|
| GAP-01 | **Visual Designer 交互协议** | ❌ 仅接受 raw JSON，无标准 WorkflowDefinition Schema | 🔴 P0 |
| GAP-02 | **5种节点数据模型** | ❌ nodes JSON 无固定 Schema，Engine 仅 mock 执行 | 🔴 P0 |
| GAP-03 | **User Task / Human Task 节点** | ❌ 完全缺失 — 无任务分配/签收/完成 | 🔴 P0 |
| GAP-04 | **Agent Task 节点** | ⚠️ 有 llm 类型但仅 mock，无 HermesEngine 集成 | 🔴 P0 |
| GAP-05 | **Condition Gateway** | ⚠️ 有 condition 类型但仅 mock，无真实条件分支 | 🔴 P0 |
| GAP-06 | **Parallel Gateway** | ❌ 缺失独立节点类型（仅通过 mode 区分） | 🟡 P1 |
| GAP-07 | **Approval Task** | ❌ 完全缺失 — 无审批流 | 🔴 P0 |
| GAP-08 | **Process Instance 管理** | ❌ 无实例表，test 是一次性模拟 | 🔴 P0 |
| GAP-09 | **Task Center** | ❌ 完全缺失 | 🔴 P0 |
| GAP-10 | **Approval Center** | ❌ 完全缺失 | 🔴 P0 |
| GAP-11 | **Human-in-the-loop** | ❌ 完全缺失 | 🟡 P1 |
| GAP-12 | **Case Management** | ❌ 完全缺失 | 🟢 P2 |
| GAP-13 | **Workflow Monitor** | ❌ 完全缺失 | 🟡 P1 |
| GAP-14 | **Workflow Scheduler** | ❌ 完全缺失 | 🟢 P2 |
| GAP-15 | **Notification Center** | ❌ 完全缺失 | 🟡 P1 |
| GAP-16 | **Workflow Marketplace** | ❌ 完全缺失 | 🟢 P2 |

---

## 3. 5种核心节点数据模型

### 3.1 WorkflowDefinition JSON Schema

这是拖拽式设计器前后端交互的核心契约 — 一份标准化的 JSON 定义文件。

```json
{
  "$schema": "https://ecos.nousresearch.com/schemas/workflow-definition-v1.json",
  "id": "wf-001",
  "name": "客户准入审批流程",
  "description": "新客户准入的标准审批流程，包含 Agent 风险评估",
  "version": "1.0.0",
  "mode": "sequential",
  "triggerType": "MANUAL",
  "triggerConfig": {
    "objectEntity": "Customer",
    "objectAction": "Approve"
  },
  "nodes": [],
  "edges": [],
  "variables": {},
  "errorHandling": {
    "onError": "SUSPEND",
    "retryCount": 3,
    "retryDelay": "5m",
    "timeout": "7d"
  },
  "notifications": {
    "onStart": ["{owner}"],
    "onComplete": ["{initiator}", "{owner}"],
    "onError": ["admin"]
  }
}
```

### 3.2 节点类型定义

#### (1) Start Node — 开始节点

```json
{
  "id": "node-start-001",
  "type": "start",
  "label": "开始",
  "position": {"x": 400, "y": 100},
  "config": {
    "triggerType": "OBJECT_ACTION",
    "objectEntity": "Customer",
    "objectAction": "SubmitForApproval",
    "inputMapping": {
      "customerId": "${object.id}",
      "customerName": "${object.name}",
      "initiator": "${context.userId}"
    }
  }
}
```

#### (2) End Node — 结束节点

```json
{
  "id": "node-end-001",
  "type": "end",
  "label": "审批通过",
  "position": {"x": 400, "y": 900},
  "config": {
    "endType": "APPROVED",
    "outputMapping": {
      "objectStatus": "Approved",
      "notificationTo": ["{initiator}"]
    }
  }
}
```

#### (3) User Task — 人工任务节点

```json
{
  "id": "node-user-001",
  "type": "userTask",
  "label": "部门经理审批",
  "position": {"x": 400, "y": 300},
  "config": {
    "taskType": "APPROVAL",
    "assignee": "${object.ownerId}",
    "candidateUsers": [],
    "candidateRoles": ["DepartmentManager"],
    "dueDate": "48h",
    "formSchema": {
      "fields": [
        {"code": "opinion", "label": "审批意见", "type": "TEXTAREA", "required": true},
        {"code": "decision", "label": "审批结果", "type": "RADIO", "options": ["同意", "驳回", "转签"]}
      ]
    },
    "actions": [
      {"code": "approve", "label": "同意", "targetStatus": "approved"},
      {"code": "reject", "label": "驳回", "targetStatus": "rejected"},
      {"code": "transfer", "label": "转签", "targetStatus": "transferred"}
    ],
    "approvalType": "SINGLE",
    "escalation": {
      "timeout": "48h",
      "escalateTo": "{parentManager}"
    }
  }
}
```

#### (4) Agent Task — Agent 任务节点

```json
{
  "id": "node-agent-001",
  "type": "agentTask",
  "label": "Agent 风险评估",
  "position": {"x": 400, "y": 500},
  "config": {
    "agentType": "RISK_ASSESSMENT",
    "agentConfig": {
      "agentId": "agent-risk-001",
      "model": "deepseek-v4-pro",
      "systemPrompt": "你是ECOS风险评估专家，负责评估客户风险等级...",
      "temperature": 0.3
    },
    "inputContext": {
      "customerProfile": "${object}",
      "customerOrders": "${variables.recentOrders}",
      "industryRisk": "${variables.industryRiskScore}"
    },
    "outputSchema": {
      "riskLevel": "STRING",
      "riskScore": "NUMBER",
      "recommendation": "STRING",
      "confidence": "NUMBER"
    },
    "outputMapping": {
      "riskLevel": "${variables.agentRiskLevel}",
      "riskScore": "${variables.agentRiskScore}",
      "recommendation": "${variables.agentRecommendation}"
    },
    "humanReview": true,
    "timeout": "5m",
    "retryOnError": 2
  }
}
```

#### (5) Exclusive Gateway — 条件网关

```json
{
  "id": "node-gateway-001",
  "type": "exclusiveGateway",
  "label": "风险评估判断",
  "position": {"x": 400, "y": 700},
  "config": {
    "conditions": [
      {
        "expression": "${variables.agentRiskLevel} == 'HIGH'",
        "label": "高风险",
        "targetNode": "node-user-002"
      },
      {
        "expression": "${variables.agentRiskLevel} == 'MEDIUM'",
        "label": "中风险",
        "targetNode": "node-user-001"
      },
      {
        "expression": "${variables.agentRiskLevel} == 'LOW'",
        "label": "低风险",
        "targetNode": "node-end-001",
        "isDefault": true
      }
    ]
  }
}
```

### 3.3 完整节点类型枚举

| 类型 | 标签 | 说明 | Sprint 1 |
|------|------|------|----------|
| `start` | 开始 | 流程起点，定义触发方式和输入映射 | ✅ |
| `end` | 结束 | 流程终点，定义输出映射 | ✅ |
| `userTask` | 人工任务 | 需人工参与的任务/审批 | ✅ |
| `agentTask` | Agent 任务 | 调用 Hermes Agent 执行 AI 任务 | ✅ |
| `exclusiveGateway` | 条件网关 | 根据条件分支路由 | ✅ |
| `parallelGateway` | 并行网关 | 并行分支/汇聚 | 🟡 P1 |
| `serviceTask` | 服务调用 | 调用外部 REST API | 🟡 P1 |
| `scriptTask` | 脚本任务 | 执行 Groovy/JavaScript 脚本 | 🟢 P2 |
| `timerEvent` | 定时器 | 定时触发/等待 | 🟢 P2 |
| `eventGateway` | 事件网关 | 基于事件的分支 | 🟢 P2 |
| `decisionTask` | 决策任务 | 调用 Rule Engine 做决策 | 🟡 P1 |

---

## 4. 拖拽式设计器前后端交互协议

### 4.1 核心交互流程

```
┌─────────────────────────────────────────────────────────────────┐
│                     Workflow Designer (前端)                      │
│                                                                   │
│  ┌──────────────┐   ┌───────────────────┐   ┌─────────────────┐ │
│  │ Node Palette  │   │   Flow Canvas     │   │ Property Panel  │ │
│  │              │   │   (React Flow)    │   │  (动态表单)      │ │
│  │  ┌─────┐     │   │                   │   │                 │ │
│  │  │Start│     │   │   [Start]──→[UT]  │   │ 选中节点时      │ │
│  │  ├─────┤     │   │           ↓       │   │ 显示配置表单    │ │
│  │  │User │     │   │        [Agent]    │   │                 │ │
│  │  │Task │     │   │        ↓    ↓     │   │ - 基本信息      │ │
│  │  ├─────┤     │   │   [Gate]→[End]    │   │ - 任务配置      │ │
│  │  │Agent│     │   │                   │   │ - 输入/输出映射  │ │
│  │  │Task │     │   │                   │   │ - 审批配置      │ │
│  │  ├─────┤     │   │                   │   │                 │ │
│  │  │Gate │     │   │                   │   │                 │ │
│  │  ├─────┤     │   │                   │   │                 │ │
│  │  │ End │     │   │                   │   │                 │ │
│  │  └─────┘     │   │                   │   │                 │ │
│  └──────────────┘   └───────────────────┘   └─────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                           │                           ▲
                    保存/加载                        验证
                           ▼                           │
┌─────────────────────────────────────────────────────────────────┐
│                     WorkflowController (后端)                     │
│                                                                   │
│  POST /api/v1/ecos/workflows          — 创建（接收完整 JSON）      │
│  PUT  /api/v1/ecos/workflows/{id}     — 更新                      │
│  GET  /api/v1/ecos/workflows/{id}     — 加载                      │
│  POST /api/v1/ecos/workflows/validate — 验证 Schema               │
│  POST /api/v1/ecos/workflows/{id}/preview — 预览（节点展开）       │
└─────────────────────────────────────────────────────────────────┘
```

### 4.2 API 契约详情

#### 4.2.1 保存/更新工作流

```
PUT /api/v1/ecos/workflows/{id}
Content-Type: application/json

Request:  完整的 WorkflowDefinition JSON（nodes + edges + config）
Response: 保存后的完整定义 + 验证结果

Response 200:
{
  "id": "wf-001",
  "name": "客户准入审批流程",
  "version": 2,
  "status": "draft",
  "nodes": [...],
  "edges": [...],
  "validation": {
    "valid": true,
    "warnings": ["节点 '风险评估' 无输出连线"],
    "errors": []
  },
  "updatedAt": "2025-06-17T10:30:00Z"
}
```

#### 4.2.2 加载工作流

```
GET /api/v1/ecos/workflows/{id}

Response 200:
{
  "id": "wf-001",
  "name": "客户准入审批流程",
  "definition": {
    "nodes": [...],
    "edges": [...],
    ...
  },
  "status": "draft",
  "createdAt": "2025-06-01T09:00:00Z",
  "updatedAt": "2025-06-17T10:30:00Z"
}
```

#### 4.2.3 验证工作流

```
POST /api/v1/ecos/workflows/validate
Request: 完整的 WorkflowDefinition JSON

Response 200:
{
  "valid": false,
  "errors": [
    {"code": "WF-003", "nodeId": "node-user-001", "message": "任务节点未指定处理人"},
    {"code": "WF-010", "message": "存在孤立节点: node-gateway-002"}
  ],
  "warnings": [
    {"message": "Agent 节点 '风险评估' 未启用人工复核"},
    {"message": "条件网关缺少默认分支"}
  ],
  "suggestions": [
    {"message": "建议为审批节点添加超时升级策略"}
  ]
}
```

#### 4.2.4 预览展开

```
POST /api/v1/ecos/workflows/{id}/preview
Request:
{
  "context": {"objectId": "CUST001", "userId": "user001"}
}

Response 200:
{
  "expandedNodes": [...],   // 解析所有变量，展开后的节点配置
  "estimatedPath": "...",   // 基于当前上下文的预测路径
  "warnings": [...]
}
```

### 4.3 前后端数据同步策略

```
前端 (React Flow)                    后端 (Spring Boot)
─────────────────                    ──────────────────

拖拽节点到画布                      →
连接节点（拖拽连线）                  →
                                      定时自动保存（debounce 3s）
                                      PUT /workflows/{id}
                                      返回 validation + warnings
                                     ←
接收验证结果
高亮错误节点/连线
                                    手动保存（Ctrl+S）
                                    PUT /workflows/{id}
                                    返回确认
                                     ←
发布 →                              PATCH /workflows/{id}/publish
                                    校验 + 创建版本快照 + 部署到 WorkflowEngine
                                     ← 返回 published 状态
```

### 4.4 节点面板拖拽协议

前端 Node Palette 提供可拖拽的节点模板：

```typescript
// 前端节点模板定义
const nodeTemplates = [
  {
    type: "start",
    label: "开始",
    icon: "PlayCircleOutlined",
    defaultConfig: { triggerType: "MANUAL" },
    category: "event"
  },
  {
    type: "userTask",
    label: "人工任务",
    icon: "UserOutlined",
    defaultConfig: {
      taskType: "APPROVAL",
      assignee: "",
      formSchema: { fields: [] }
    },
    category: "task"
  },
  {
    type: "agentTask",
    label: "Agent 任务",
    icon: "RobotOutlined",
    defaultConfig: {
      agentType: "GENERAL",
      agentConfig: { model: "deepseek-v4-pro" },
      humanReview: false
    },
    category: "task"
  },
  {
    type: "exclusiveGateway",
    label: "条件网关",
    icon: "BranchesOutlined",
    defaultConfig: { conditions: [] },
    category: "gateway"
  },
  {
    type: "end",
    label: "结束",
    icon: "StopOutlined",
    defaultConfig: { endType: "COMPLETED" },
    category: "event"
  }
];
```

---

## 5. Agent 节点与 HermesEngine 集成方案

### 5.1 集成架构

```
┌─────────────────────────────────────────────────────────────┐
│                    WorkflowEngine                            │
│                                                              │
│  执行到 AgentTask 节点                                        │
│       │                                                      │
│       ▼                                                      │
│  ┌──────────────────────────────────────────────────────┐   │
│  │           AgentTaskExecutor                          │   │
│  │                                                      │   │
│  │  1. 解析 agentConfig（agentId, model, prompt）        │   │
│  │  2. 构建 inputContext（替换 ${variables.xxx}）        │   │
│  │  3. 调用 HermesEngine API                            │   │
│  │  4. 解析 outputSchema 映射到 variables                │   │
│  │  5. 若 humanReview=true → 挂起等待人工确认            │   │
│  └──────────────────────┬───────────────────────────────┘   │
│                         │                                    │
│                         ▼                                    │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              HermesEngine (Agent Runtime)             │   │
│  │                                                      │   │
│  │  POST /api/v1/hermes/execute                         │   │
│  │  {                                                  │   │
│  │    "agentId": "agent-risk-001",                     │   │
│  │    "model": "deepseek-v4-pro",                      │   │
│  │    "systemPrompt": "...",                           │   │
│  │    "userPrompt": "请评估客户风险等级...",             │   │
│  │    "context": {...}                                 │   │
│  │  }                                                  │   │
│  │                                                      │   │
│  │  Response:                                           │   │
│  │  {                                                  │   │
│  │    "executionId": "exec-001",                       │   │
│  │    "status": "completed",                           │   │
│  │    "output": {                                      │   │
│  │      "riskLevel": "MEDIUM",                         │   │
│  │      "riskScore": 65,                               │   │
│  │      "recommendation": "建议加强信用审核",            │   │
│  │      "confidence": 0.85                             │   │
│  │    },                                               │   │
│  │    "tokens": {"input": 1500, "output": 200},       │   │
│  │    "duration": "3.2s"                               │   │
│  │  }                                                  │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 AgentTaskExecutor 伪代码

```java
@Component
public class AgentTaskExecutor implements WorkflowNodeExecutor {

    private final HermesClient hermesClient;

    @Override
    public boolean supports(String nodeType) {
        return "agentTask".equals(nodeType);
    }

    @Override
    public ExecutionResult execute(WorkflowNode node, WorkflowContext context) {
        Map<String, Object> config = node.getConfig();
        Map<String, Object> agentConfig = (Map) config.get("agentConfig");

        // 1. 解析输入上下文，替换变量
        Map<String, Object> inputContext = resolveVariables(
            (Map) config.get("inputContext"), context);

        // 2. 构建 Agent 调用
        HermesRequest request = HermesRequest.builder()
            .agentId((String) agentConfig.get("agentId"))
            .model((String) agentConfig.get("model"))
            .systemPrompt(resolveTemplate(
                (String) agentConfig.get("systemPrompt"), context))
            .userPrompt(buildUserPrompt(config, inputContext))
            .context(inputContext)
            .temperature(agentConfig.get("temperature"))
            .build();

        // 3. 调用 HermesEngine
        HermesResponse response = hermesClient.execute(request);

        // 4. 映射输出到工作流变量
        Map<String, Object> outputSchema = (Map) config.get("outputSchema");
        Map<String, Object> outputMapping = (Map) config.get("outputMapping");
        context.setVariables(mapOutput(response.getOutput(), outputSchema, outputMapping));

        // 5. 若需人工复核 → 挂起
        if (Boolean.TRUE.equals(config.get("humanReview"))) {
            return ExecutionResult.suspended("Agent 结果待人工复核: " + response.getOutput());
        }

        return ExecutionResult.success("Agent 执行完成: " + response.getOutput());
    }
}
```

### 5.3 HermesClient 接口契约

```java
public interface HermesClient {
    /**
     * 同步执行 Agent 任务
     */
    HermesResponse execute(HermesRequest request);

    /**
     * 异步执行 Agent 任务（带回调）
     */
    String executeAsync(HermesRequest request, String callbackUrl);

    /**
     * 查询异步执行状态
     */
    HermesResponse getStatus(String executionId);
}
```

---

## 6. 数据库设计

### 6.1 已有表增强

```sql
-- 扩展 ecos_workflow 表
ALTER TABLE ecos_workflow ADD COLUMN IF NOT EXISTS version_no VARCHAR(20) DEFAULT '1.0.0';
ALTER TABLE ecos_workflow ADD COLUMN IF NOT EXISTS trigger_type VARCHAR(50) DEFAULT 'MANUAL';
ALTER TABLE ecos_workflow ADD COLUMN IF NOT EXISTS trigger_config JSONB;
ALTER TABLE ecos_workflow ADD COLUMN IF NOT EXISTS variables JSONB DEFAULT '{}';
ALTER TABLE ecos_workflow ADD COLUMN IF NOT EXISTS error_handling JSONB;
ALTER TABLE ecos_workflow ADD COLUMN IF NOT EXISTS notifications JSONB;
ALTER TABLE ecos_workflow ADD COLUMN IF NOT EXISTS category VARCHAR(100);
ALTER TABLE ecos_workflow ADD COLUMN IF NOT EXISTS tags TEXT[];

-- 修改 nodes/edges 列类型为 JSONB
ALTER TABLE ecos_workflow ALTER COLUMN nodes TYPE JSONB USING nodes::jsonb;
ALTER TABLE ecos_workflow ALTER COLUMN edges TYPE JSONB USING edges::jsonb;
```

### 6.2 新增表

#### 6.2.1 `ecos_workflow_instance` — 流程实例
```sql
CREATE TABLE ecos_workflow_instance (
    id                  VARCHAR(50) PRIMARY KEY,
    workflow_id         VARCHAR(50) NOT NULL,
    workflow_name       VARCHAR(200),
    version_no          VARCHAR(20),
    status              VARCHAR(50) NOT NULL,       -- Created/Running/Waiting/Completed/Failed/Suspended
    trigger_type        VARCHAR(50),
    triggered_by        VARCHAR(100),
    triggered_object_id VARCHAR(100),
    trigger_event       VARCHAR(200),
    variables           JSONB DEFAULT '{}',
    context             JSONB,                      -- 运行时上下文
    current_node_ids    TEXT[],                     -- 当前活跃节点列表
    started_at          TIMESTAMP,
    completed_at        TIMESTAMP,
    error_message       TEXT,
    retry_count         INT DEFAULT 0,
    created_at          TIMESTAMP DEFAULT NOW(),
    updated_at          TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_wf_instance_status ON ecos_workflow_instance(status);
CREATE INDEX idx_wf_instance_object ON ecos_workflow_instance(triggered_object_id);
```

#### 6.2.2 `ecos_workflow_task` — 任务
```sql
CREATE TABLE ecos_workflow_task (
    id              VARCHAR(50) PRIMARY KEY,
    instance_id     VARCHAR(50) NOT NULL,
    node_id         VARCHAR(100) NOT NULL,
    task_type       VARCHAR(50) NOT NULL,           -- APPROVAL/EXECUTION/AGENT/INVESTIGATION
    title           VARCHAR(500),
    assignee        VARCHAR(100),                   -- 当前处理人
    candidate_users TEXT[],                          -- 候选处理人
    candidate_roles TEXT[],                          -- 候选角色
    status          VARCHAR(50) DEFAULT 'New',      -- New/Assigned/InProgress/Completed/Rejected/Transferred
    priority        VARCHAR(20) DEFAULT 'NORMAL',
    form_schema     JSONB,                          -- 任务表单定义
    form_data       JSONB,                          -- 表单已填写数据
    result          JSONB,                          -- 处理结果
    agent_result    JSONB,                          -- Agent 执行结果
    due_date        TIMESTAMP,
    completed_at    TIMESTAMP,
    completed_by    VARCHAR(100),
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_wf_task_status ON ecos_workflow_task(status);
CREATE INDEX idx_wf_task_assignee ON ecos_workflow_task(assignee);
CREATE INDEX idx_wf_task_instance ON ecos_workflow_task(instance_id);
```

#### 6.2.3 `ecos_workflow_approval` — 审批记录
```sql
CREATE TABLE ecos_workflow_approval (
    id              VARCHAR(50) PRIMARY KEY,
    task_id         VARCHAR(50) NOT NULL,
    instance_id     VARCHAR(50) NOT NULL,
    approver        VARCHAR(100) NOT NULL,
    decision        VARCHAR(50) NOT NULL,           -- Approved/Rejected/Transferred
    opinion         TEXT,
    form_data       JSONB,
    created_at      TIMESTAMP DEFAULT NOW()
);
```

#### 6.2.4 `ecos_workflow_log` — 执行日志
```sql
CREATE TABLE ecos_workflow_log (
    id              VARCHAR(50) PRIMARY KEY,
    instance_id     VARCHAR(50) NOT NULL,
    node_id         VARCHAR(100),
    node_type       VARCHAR(50),
    event_type      VARCHAR(100) NOT NULL,         -- NodeStarted/NodeCompleted/TaskCreated/TaskAssigned/AgentInvoked/AgentCompleted
    message         TEXT,
    details         JSONB,
    duration_ms     BIGINT,
    trace_id        VARCHAR(100),                   -- 链路追踪ID
    created_at      TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_wf_log_instance ON ecos_workflow_log(instance_id, created_at);
CREATE INDEX idx_wf_log_trace ON ecos_workflow_log(trace_id);
```

---

## 7. 前端设计器组件清单

### 7.1 页面布局

```
┌──────────────────────────────────────────────────────────────┐
│ Workflow Designer                                             │
│                                                               │
│ ┌──────┐ ┌──────────────────────────────────┐ ┌────────────┐ │
│ │ Node │ │                                  │ │ Property   │ │
│ │Palette│ │        Flow Canvas              │ │ Panel      │ │
│ │      │ │        (React Flow)              │ │            │ │
│ │ ┌──┐ │ │                                  │ │ 节点类型   │ │
│ │ │St│ │ │   [Start]                        │ │ 节点名称   │ │
│ │ └──┘ │ │      ↓                           │ │ 配置项...  │ │
│ │ ┌──┐ │ │   [UserTask]                     │ │            │ │
│ │ │UT│ │ │      ↓                           │ │            │ │
│ │ └──┘ │ │   [AgentTask]                    │ │            │ │
│ │ ┌──┐ │ │     ↓    ↓                       │ │            │ │
│ │ │AT│ │ │  [Gate]→[End]                    │ │            │ │
│ │ └──┘ │ │                                  │ │            │ │
│ │ ┌──┐ │ │                                  │ │            │ │
│ │ │GW│ │ │                                  │ │            │ │
│ │ └──┘ │ │                                  │ │            │ │
│ │ ┌──┐ │ │                                  │ │            │ │
│ │ │En│ │ │                                  │ │            │ │
│ │ └──┘ │ │                                  │ │            │ │
│ └──────┘ └──────────────────────────────────┘ └────────────┘ │
│                                                               │
│ ┌─────────────────────────────────────────────────────────────┐│
│ │ Toolbar: [保存] [验证] [预览] [发布] [撤销] [重做] [缩放]    ││
│ └─────────────────────────────────────────────────────────────┘│
└──────────────────────────────────────────────────────────────┘
```

### 7.2 核心组件清单

| 组件名称 | 技术方案 | 说明 |
|---------|---------|------|
| **FlowCanvas** | React Flow (reactflow) | 核心画布，支持节点拖拽/连线/缩放 |
| **NodePalette** | React DnD + Ant Design | 左侧可拖拽节点模板面板，按类别分组 |
| **PropertyPanel** | Ant Design Form（动态） | 右侧属性面板，根据选中节点类型动态渲染表单 |
| **StartNodeConfig** | Ant Design Form | 开始节点配置：触发方式、输入映射 |
| **EndNodeConfig** | Ant Design Form | 结束节点配置：结束类型、输出映射 |
| **UserTaskConfig** | Ant Design Form（带字段编辑器） | 人工任务配置：处理人、表单设计、审批动作、超时升级 |
| **AgentTaskConfig** | Ant Design Form + JSON Editor | Agent 任务配置：Agent选择、Prompt模板、输入上下文、输出映射 |
| **GatewayConfig** | Ant Design Form（可编辑条件表格） | 网关配置：条件表达式编辑、默认分支设置 |
| **Toolbar** | Ant Design Space + Button | 顶部工具栏：保存/验证/预览/发布/撤销/重做 |
| **ValidationPanel** | Ant Design Alert + List | 实时/手动验证结果展示面板 |
| **VariableEditor** | Ant Design Drawer | 工作流全局变量编辑器 |
| **FormDesigner** | 自定义表单设计器 | 嵌入在 UserTask 配置中，拖拽设计审批表单 |
| **AgentSelector** | Ant Design Select（搜索+预览） | 从 Agent Mesh 中搜索/选择 Agent |
| **ExpressionEditor** | CodeMirror + 变量自动补全 | 条件表达式编辑器，支持 `${variables.xxx}` 语法高亮 |
| **PreviewPanel** | Ant Design Drawer | 流程预览：基于上下文展开变量，展示预测路径 |
| **MiniMap** | React Flow MiniMap | 画布缩略图导航 |

### 7.3 技术方案推荐

| 模块 | 推荐技术 |
|------|----------|
| 流程图渲染 | React Flow v12 (reactflow) |
| 拖拽 | React DnD 或直接使用 React Flow 内置拖拽 |
| UI 组件库 | Ant Design 5.x |
| 表单设计器（审批表单） | Formily 或 自定义 JSON Schema 表单引擎 |
| 条件表达式编辑 | CodeMirror 6 + 自定义 autoComplete |
| 数据状态管理 | Zustand（轻量）或 Redux Toolkit |
| API 请求 | React Query (TanStack Query) |
| 自动保存 | lodash debounce + React Query mutation |

---

## 8. API 契约汇总

```
工作流定义:
GET    /api/v1/ecos/workflows                         — 列表（?keyword=&status=&page=&size=）
POST   /api/v1/ecos/workflows                         — 创建
GET    /api/v1/ecos/workflows/{id}                    — 详情（含 definition）
PUT    /api/v1/ecos/workflows/{id}                    — 更新
DELETE /api/v1/ecos/workflows/{id}                    — 删除
PATCH  /api/v1/ecos/workflows/{id}/publish            — 发布（校验+版本快照+部署）
POST   /api/v1/ecos/workflows/validate                — 验证定义
POST   /api/v1/ecos/workflows/{id}/preview            — 预览展开
POST   /api/v1/ecos/workflows/{id}/clone              — 克隆

流程实例:
POST   /api/v1/ecos/workflows/{id}/start              — 启动流程实例
GET    /api/v1/ecos/workflows/instances               — 实例列表
GET    /api/v1/ecos/workflows/instances/{instanceId}  — 实例详情
POST   /api/v1/ecos/workflows/instances/{instanceId}/suspend  — 挂起
POST   /api/v1/ecos/workflows/instances/{instanceId}/resume   — 恢复
POST   /api/v1/ecos/workflows/instances/{instanceId}/terminate — 终止

任务中心:
GET    /api/v1/ecos/tasks                             — 我的任务列表
GET    /api/v1/ecos/tasks/{taskId}                    — 任务详情
POST   /api/v1/ecos/tasks/{taskId}/claim              — 签收任务
POST   /api/v1/ecos/tasks/{taskId}/complete           — 完成任务
POST   /api/v1/ecos/tasks/{taskId}/transfer           — 转签
POST   /api/v1/ecos/tasks/{taskId}/reject             — 驳回
GET    /api/v1/ecos/tasks/statistics                  — 任务统计

审批:
POST   /api/v1/ecos/approvals/{taskId}/approve        — 审批通过
POST   /api/v1/ecos/approvals/{taskId}/reject         — 审批驳回
POST   /api/v1/ecos/approvals/{taskId}/transfer       — 审批转签
POST   /api/v1/ecos/approvals/{taskId}/addSign        — 加签

监控:
GET    /api/v1/ecos/workflows/monitor/overview        — 监控概览
GET    /api/v1/ecos/workflows/monitor/instances       — 实例监控列表
GET    /api/v1/ecos/workflows/monitor/logs/{instanceId} — 执行日志（TraceID 关联）

Marketplace:
GET    /api/v1/ecos/workflows/marketplace             — 模板市场
POST   /api/v1/ecos/workflows/{id}/share              — 分享到市场
POST   /api/v1/ecos/workflows/marketplace/{templateId}/import — 导入模板
```

---

## 9. 错误码

| 错误码 | 说明 | HTTP Status |
|--------|------|-------------|
| WF-001 | 流程不存在 | 404 |
| WF-002 | 流程发布失败 | 400 |
| WF-003 | 节点配置错误 | 400 |
| WF-004 | 流程执行失败 | 500 |
| WF-005 | 任务超时 | 408 |
| WF-006 | 审批失败 | 400 |
| WF-007 | Agent 执行失败 | 500 |
| WF-008 | Case 关闭失败 | 400 |
| WF-009 | 实例不存在 | 404 |
| WF-010 | 流程定义不完整（缺失开始/结束节点） | 400 |
| WF-011 | 存在无连接节点 | 400 |
| WF-012 | 循环依赖 | 400 |

---

## 10. Sprint 1 交付范围

| 优先级 | 功能 | 说明 |
|--------|------|------|
| 🔴 P0 | WorkflowDefinition JSON Schema | 标准化5种节点 + 前后端契约 |
| 🔴 P0 | 拖拽式设计器协议 | 保存/加载/验证 API + React Flow 前端集成 |
| 🔴 P0 | User Task 节点 | `ecos_workflow_task` 表 + assignee/formSchema/actions |
| 🔴 P0 | Agent Task 节点 | AgentTaskExecutor + HermesClient 集成 |
| 🔴 P0 | Exclusive Gateway | 条件表达式引擎 + 分支路由 |
| 🔴 P0 | 流程实例管理 | `ecos_workflow_instance` 表 + start/suspend/resume/terminate |
| 🔴 P0 | 任务中心 | `GET /tasks` + claim/complete/transfer |
| 🔴 P0 | 审批中心 | `ecos_workflow_approval` 表 + approve/reject/transfer |
| 🟡 P1 | Parallel Gateway | 并行分支/汇聚 |
| 🟡 P1 | Human-in-the-loop | Agent 结果人工复核挂起/恢复 |
| 🟡 P1 | 工作流监控 | `ecos_workflow_log` 表 + TraceID + 监控 API |
| 🟡 P1 | 通知中心 | Email/SMS/WeCom 通知集成 |
| 🟢 P2 | Workflow Scheduler | Cron 定时触发 |
| 🟢 P2 | Case Management | `ecos_workflow_case` 表 |
| 🟢 P2 | Workflow Marketplace | 模板分享/导入 |

---

## 11. 架构决策记录 (ADR)

### ADR-01: nodes/edges 存储格式
- **决策**: 使用 JSONB 存储完整 WorkflowDefinition，而非拆表存储每个节点
- **理由**: Workflow 定义是整体文档，JSONB 便于原子性保存/加载，前端 React Flow 直接序列化；拆表导致联表查询复杂
- **替代方案**: 独立 node 表 + edge 表 — 查询灵活但复杂度高

### ADR-02: 流程引擎方案
- **决策**: Sprint 1 自研轻量级拓扑排序引擎（已有 WorkflowEngine），不引入 Camunda/Flowable
- **理由**: Sprint 1 范围可控（5种节点），Camunda 太重；自研引擎已支持 sequential/parallel 两种模式
- **未来**: 复杂 BPMN 2.0 需求可接入 Camunda，自研引擎作为轻量 fallback

### ADR-03: Agent 节点集成方式
- **决策**: 通过 HermesClient 接口抽象调用 HermesEngine，支持同步/异步两种模式
- **理由**: 解耦 WorkflowEngine 和 Agent Runtime；支持 humanReview 模式实现 Human-in-the-loop
- **回调方式**: 异步模式通过 WebHook URL 回调通知 Agent 执行完成

### ADR-04: 前端设计器技术选型
- **决策**: React Flow (reactflow) + Ant Design
- **理由**: React Flow 是生产级 React 流程图库，支持自定义节点/边缘/拖拽/缩放；Ant Design 与项目技术栈一致
