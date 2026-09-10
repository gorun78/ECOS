# ECOS 微服务改造 — API 契约草案

> 本文档定义微服务改造后各服务间的 API 契约。基于现有端点反向分析，改造后路径保持不变（API 只增不改铁律）。

---

## 服务端口清单

| 服务 | 端口 | 路径前缀 |
|------|:--:|---------|
| api-gateway | 8080 | `/api` (统一入口) |
| security-service | 18081 | `/api/v1/security`, `/api/v1/audit`, `/api/v1/abac`, `/api/v1/data-masking`, `/api/v1/data-permission`, `/api/v1/policy-engine` |
| data-service | 18082 | `/api/v1/engine/data` |
| ontology-service | 18083 | `/api/v1/ecos` |
| ai-service | 18084 | `/api/v1/agent`, `/api/v1/agent-loop`, `/api/v1/agent-mesh`, `/api/v1/agent-call`, `/api/v1/knowledge` |
| kb-service | 18086 | `/api/v1/rules`, `/api/v1/kb` |
| cognitive-service | 18089 | `/api/v1/cognitive`, `/api/v1/world-model` |
| core-service | 18090 | `/api/v1/core`, `/sys-man/api` |

---

## 跨服务 REST API 契约

### security-service（被调用最多）

| Method | Path | 调用方 | 用途 | 请求体 | 响应 |
|--------|------|--------|------|--------|------|
| POST | `/api/v1/security/auth/login` | api-gateway | 用户登录 | `{"username","password"}` | `{code, token, expiresIn}` |
| POST | `/api/v1/security/auth/verify` | api-gateway | JWT 校验 | `{"token"}` | `{code, valid, claims{userId, tenantId, roles}}` |
| POST | `/api/v1/security/rls/apply` | data-service, kb-service, core-service | RLS 行级权限注入 | `{userId, tenantId, resourceType}` | `{code, conditions[]}` |
| POST | `/api/v1/security/cls/columns` | data-service, core-service | 列级过滤 | `{userId, resourceType, columns[]}` | `{code, allowedColumns[]}` |
| POST | `/api/v1/security/mask` | data-service, core-service | 敏感字段脱敏 | `{data, fields[]}` | `{code, maskedData}` |
| POST | `/api/v1/security/policy-engine/evaluate` | data-service, ai-service, core-service | OPA ABAC 裁决 | `{userId, action, resource, context}` | `{code, decision: allow/deny, policy}` |
| POST | `/api/v1/security/audit/log` | 全部服务 | 审计日志（异步） | `{userId, action, resource, ip, timestamp, detail}` | `{code, success}` |
| GET | `/api/v1/security/rls/user/{id}/permissions` | core-service | 查用户 RLS 权限 | — | `{code, permissions[]}` |
| POST | `/api/v1/security/encrypt` | data-service | 加密数据（如密码） | `{plainText, keyId}` | `{code, cipherText}` |
| POST | `/api/v1/security/decrypt` | data-service | 解密数据 | `{cipherText, keyId}` | `{code, plainText}` |

### data-service

| Method | Path | 调用方 | 用途 | 说明 |
|--------|------|--------|------|------|
| POST | `/api/v1/engine/data/datasource` | core-service | 创建数据源 | — |
| GET | `/api/v1/engine/data/datasource/{id}` | core-service | 获取数据源 | — |
| GET | `/api/v1/engine/data/pipeline/definitions` | core-service, ai-service | 管道列表（摘要） | — |
| GET | `/api/v1/engine/data/pipeline/definitions/{id}` | core-service | 管道详情（含 nodes/edges） | 铁律 §4.8：详情接口 |
| POST | `/api/v1/engine/data/pipeline/run` | core-service | 触发管道执行 | 产生 Kafka 事件 |
| GET | `/api/v1/engine/data/lineage` | kb-service | 查询数据血缘 | — |
| GET | `/api/v1/engine/data/dq/records` | core-service | 数据质量报告 | — |

### ontology-service

| Method | Path | 调用方 | 用途 | 说明 |
|--------|------|--------|------|------|
| GET | `/api/v1/ecos/object/list` | api-gateway, core-service | 对象列表 | — |
| GET | `/api/v1/ecos/object/{id}` | core-service, kb-service | 对象详情 | — |
| POST | `/api/v1/ecos/object` | core-service | 创建对象 | 产生 Kafka 事件 |
| GET | `/api/v1/ecos/workflow` | core-service | 工作流定义 | — |
| POST | `/api/v1/ecos/kb/graph/sync` | ontology-service → kb-service | 本体→KG 同步 | 内部调用 |

### kb-service

| Method | Path | 调用方 | 用途 | 说明 |
|--------|------|--------|------|------|
| GET | `/api/v1/kb/rules` | cognitive-service | 获取合规规则 | 只读，cognitive 不写 |
| POST | `/api/v1/kb/graph/sync` | ontology-service | 本体→KG 同步 | 异步 |
| POST | `/api/v1/kb/rag/search` | ai-service, cognitive-service | RAG 检索 | `{"query", "topK"}` |
| GET | `/api/v1/kb/graph/nodes` | cognitive-service | 图谱节点 | — |
| POST | `/api/v1/kb/extract` | ontology-service, data-service | 知识抽取（zhi 转化） | 异步 |

### cognitive-service

| Method | Path | 调用方 | 用途 | 说明 |
|--------|------|--------|------|------|
| POST | `/api/v1/cognitive/diagnose` | ai-service | 因果诊断 | >=3 层因果链 |
| POST | `/api/v1/cognitive/reason` | ai-service | 场景推演 | 混合推理 |
| POST | `/api/v1/knowledge/reason` | ai-service | 知识推理 | 别名端点 |
| GET | `/api/v1/cognitive/scenario/{id}` | core-service | 场景详情 | — |

### ai-service

| Method | Path | 调用方 | 用途 | 说明 |
|--------|------|--------|------|------|
| POST | `/api/v1/agent/chat` | api-gateway (SSE) | Agent 对话 | 流式返回 |
| POST | `/api/v1/agent-loop/chat` | api-gateway (SSE) | Agent Loop | 流式返回 |
| POST | `/api/v1/agent/delegate` | internal | 任务委派 | 单层 Delegate |
| GET | `/api/v1/agent/session/{id}` | core-service | 会话详情 | — |
| POST | `/api/v1/agent/mesh/create` | core-service | 创建 Mesh | — |

### core-service

| Method | Path | 调用方 | 用途 |
|--------|------|--------|------|
| GET | `/api/v1/core/dict/type` | api-gateway | 字典查询 |
| GET | `/api/v1/core/config` | api-gateway, 全部服务 | 系统配置 |
| POST | `/api/v1/core/tenant` | api-gateway | 租户管理 |
| GET | `/api/v1/core/casual/metrics` | api-gateway | 全局指标聚合 |
| GET | `/sys-man/api/v1/ecos/agent/executions` | api-gateway (BFF 兼容) | Agent 执行记录 |

---

## Kafka 事件契约

| Topic | 生产者 | 消费者 | 事件类型 | 烘焙 |
|-------|--------|--------|---------|------|
| `ecos.pipeline.events` | data-service | kb-service, core-service | `PipelineEvent(COLLECTION_COMPLETED)` | 管道采集完成 |
| `ecos.ontology.events` | ontology-service | kb-service | `PipelineEvent(TRANSFORM_COMPLETED)` | 本体变更完成 |
| `ecos.kb.events` | kb-service | cognitive-service, ai-service | `PipelineEvent(TRANSFORM_COMPLETED)` | KG 同步完成 |
| `ecos.cognitive.events` | cognitive-service | ai-service, core-service | `PipelineEvent(STATUS_CHANGED)` | 推理完成 |
| `ecos.agent.events` | ai-service | core-service, kb-service | `PipelineEvent(STATUS_CHANGED)` | Agent 执行完成 |
| `ecos.core.events` | core-service | data-service, ai-service | `PipelineEvent(STATUS_CHANGED)` | 工作流审批完成 |
| `ecos.audit.events` | security-service | core-service | `AuditEvent` | 审计事件（异步） |

### 事件消息格式（PipelineEvent）

```json
{
  "eventType": "TRANSFORM_COMPLETED",
  "sourceService": "data-service",
  "targetService": "kb-service",
  "correlationId": "uuid",
  "payload": {
    "pipelineId": "123",
    "objectType": "PIPELINE",
    "objectId": "456"
  },
  "timestamp": "2026-09-10T12:00:00Z",
  "traceId": "celtraceid"
}
```

---

## 请求头契约（API 网关 → 服务）

| Header | 来源 | 说明 |
|--------|------|------|
| `Authorization` | 前端 | 原始 JWT（不变） |
| `X-ECOS-USER` | gateway 解析 JWT 后注入 | 用户 ID |
| `X-ECOS-TENANT` | gateway 解析 JWT 后注入 | 租户 ID |
| `X-ECOS-ROLES` | gateway 解析 JWT 后注入 | 角色列表，逗号分隔 |
| `X-Request-Id` | gateway 生成 | 分布式追踪 ID |
| `X-Forwarded-For` | nginx 注入 | 原始客户端 IP |

---

## 健康检查端点（每服务）

| 端点 | 返回 |
|------|------|
| `GET :{port}/actuator/health` | `{"status":"UP"}` — 仅存活 |
| `GET :{port}/actuator/health/liveness` | 存活探针 |
| `GET :{port}/actuator/health/readiness` | 就绪探针 |

gateway 聚合：`GET :8080/api/v1/monitor/health` → 遍历所有 service 的 `/actuator/health`，返回聚合状态。
