# ECOS 微服务改造 — API 契约

> 本文档定义微服务改造后**服务间**的 API 契约（REST 同步 + Kafka 异步）。
> **唯一基准**: [current-plan.md](current-plan.md) v1.4（5 业务服务 + 1 网关 + 1 顶层 workspace 场景层，按业务域聚合）。
> **核心铁律**: API **只增不改**——既有路径与参数签名不可变，新增端点以 `/api/v1/` 前缀对齐；本文所有路径以**现存 Controller 为准**（已逐一核验 `@RequestMapping`）。

---

## 0. 旧→新服务名映射（v1.x → v2.0）

v1.x 契约基于"7+1 按引擎拆分"旧方案，v1.2 改为"5 业务服务按域聚合"。映射如下：

| v1.x 旧名 | v2.0 归属 | 说明 |
|-----------|:--------:|------|
| security-service (18081) | **sysman** | 认证/IAM/审计/脱敏/ABAC/加密 |
| data-service (18082) | **datanet** | 数据源/管道/血缘/DQ |
| ontology-service (18083) | **buszhi** | 本体/对象/关系/版本/工作流（**不含**对象运行时，归 workspace） |
| kb-service (18086) | **dccheng** | KG/RAG/规则/抽取（注意：真实前缀 `/api/v1/knowledge/*`） |
| cognitive-service (18089) | **dccheng** | 因果推理/情景推演（合并入 dccheng，**18089 端口废弃**） |
| ai-service (18084) | **aiming** | Agent/Loop/Mesh/Delegation/LLM |
| **（无）workspace (18090)** | **workspace** | **顶层独立场景应用层**（v1.4 新增）：对象运行时/ObjectQL/Scenario/Workbook，跨调用全部 service；旧 core-service 的 18090 端口重分配给它 |

> v1.x 中与上述清单不符的端点（`/api/v1/core/*`、`ecos.core.events`、独立 18089 端口、旧 core-service 18090）在本版**全部作废**；18090 重分配给顶层 workspace 模块。**路径消歧**：workspace 的 `/api/v1/ecos/objects/*` 与 buszhi 的 `/api/v1/ecos/object/*` 为精确前缀兄弟，网关按最长前缀优先路由。

---

## 1. 服务端口与路由归属

### 1.1 网关路由表（api-gateway :8080 → service）

| 服务 | 端口 | 网关路由前缀（→ 该服务） |
|:----:|:---:|:----|
| sysman | 18081 | `/api/v1/security/*`、`/api/v1/audit/**`、`/api/v1/abac/*`、`/api/v1/data-masking/*`、`/api/v1/data-permission/*`、`/api/v1/policy-engine/*`、`/api/v1/auth/*`、`/api/v1/core/*`、`/sys-man/api/*` |
| datanet | 18082 | `/api/v1/engine/data/*`、`/api/v1/pipeline/**` |
| buszhi | 18083 | `/api/v1/ecos/object/*`、`/api/v1/ecos/workflow/*` |
| dccheng | 18086 | `/api/v1/knowledge/*`（含 `reason`/`rag`/`sync`/`extract`/`compliance-rules`）、`/api/v1/cognitive/*`、`/api/v1/rules/*`、`/api/v1/world-model/*` |
| aiming | 18084 | `/api/v1/agent/*`、`/api/v1/agent-loop/*`、`/api/v1/agent-mesh/*`、`/api/v1/agent-call/*`、`/api/v1/knowledge/*`（**除 `reason`**） |

**路由例外（🔴 显式声明）**:
- `POST /api/v1/knowledge/reason` → **dccheng**（cognitive 实现，见 [CognitivePipelineController](file:///d:/workspace/javaprojects/ECOS/ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2)）。aiming 不实现此路径，网关不做跨服务转发（避免二跳）。
- `GET /sys-man/api/v1/ecos/agent/executions` → **sysman**，sysman 经 REST 转发 aiming 取数（跨服务禁 join，见 current-plan O7）。

### 1.2 端口互斥（开发约束）

service 沿用其主引擎端口。本机**同时运行引擎 boot 与对应 service 会端口冲突**，调试以 `--server.port` 覆盖。18089（cognitive-boot）与 18086 不同，但 dccheng(18086) 的生产入口仅 18086。

---

## 2. 统一响应信封（ApiResponse）

所有 REST 端点统一返回 [ApiResponse](file:///d:/workspace/javaprojects/ECOS/ecos_backend/common/common-api/src/main/java/com/chinacreator/gzcm/common/base/ApiResponse.java) `<T>`：

```json
{ "code": 0, "errorCode": "VALIDATION_ERROR", "message": "ok", "data": { ... }, "timestamp": 1789036556000 }
```

| 字段 | 类型 | 语义 |
|------|------|------|
| `code` | int | `0`=成功；`400`/`401`/`403`/`404`=业务错误；`-1`=系统错误 |
| `errorCode` | String? | 可选，精细客户端处理标识 |
| `message` | String | 提示文案 |
| `data` | T? | 业务载荷；`null`/省略时（`@JsonInclude NON_NULL`）表示无载荷 |
| `timestamp` | long | 服务端 epoch-millis |

> ⚠️ **勿混淆概念**：`code` 是**业务**状态码，与 HTTP 状态码、与 `PipelineEvent.EventType` 无关。HTTP 层即使 `200`，业务失败仍看 `code != 0`。
> 调用方断言**必须同时看 `code` + 期望的 `data`**，不可只看 HTTP 200。

---

## 3. 跨服务 REST API 契约

> "调用方"指 service 进程（非 frontend）。在线链透传用户 JWT、后台链用服务凭证——见 §4。
> "🔒 安全卡"标注的端点 = 调用方须先过 §2.4 安全裁决（RLS/CLS/脱敏/OPA）才可执行业务。

### 3.1 sysman（被调用最多 — 安全裁决扇入热点）

> 所有下行跨服务调用使用**可信命名空间** `/api/v1/security/**`（铁律 §1.2 既有路径口径）；`/api/security/**` 为兼容别名，内部调用禁止使用（免认证兜底仅对前端 BFF 保留）。

| Method | Path（可信命名空间） | 调用方 | 用途 | 请求体 | `data` 载荷 |
|:-----:|:--|:--|:--|:--|:--|
| POST | `/api/v1/auth/login` | 前端(gateway) | 登录签发 RS256 JWT | `{username,password}` | `{token, expiresIn, user}` |
| POST | `/api/v1/auth/refresh` | 前端(gateway) | 刷新令牌 | `{refreshToken}` | `{token, expiresIn}` |
| GET | `/api/v1/auth/me` | 前端 | 当前用户 | — | `{...用户}` |
| POST | `/api/v1/security/rls/apply` | datanet/buszhi/dccheng/aiming | RLS 行级注入 | `{userId,tenantId,resourceType,tableName}` | `{whereClause, conditions[]}` |
| POST | `/api/v1/security/cls/columns` | datanet/buszhi/aiming | 列级过滤 | `{userId,resourceType,columns[]}` | `{allowedColumns[]}` |
| POST | `/api/v1/data-masking/mask` | datanet/buszhi/aiming | 敏感字段脱敏 | `{data, fields[]}` | `{maskedData}` |
| POST | `/api/v1/security/policy-engine/evaluate` | datanet/buszhi/dccheng/aiming | OPA ABAC 裁决 | `{userId,action,resource,context}` | `{decision: allow/deny, policy}` |
| POST | `/api/v1/security/crypto/decrypt` | datanet | 解密 | `{cipherText, keyId}` | `{plainText}` |
| POST | `/api/v1/security/crypto/encrypt` 🔒 | datanet | 加密敏感字段（密码/令牌/证书） | `{plainText, keyId}` | `{cipherText}` |
| GET | `/api/v1/security/audit/list` | 前端(审计查询) | 审计列表 | — | `[{...}]` |

> 🔒 **数据安全（敏感数据）**：`crypto/*` 端点处理明文，请求体禁止落日志；响应 `cipherText` 仅在 `X-ECOS-CALLER=service-account` 调用下返回（防跨租户泄露）。
> 🔥 **热点缓解（强制，current-plan ADR-2）**: RLS/CLS/mask/OPA 决策须**调用方本地缓存**（Caffeine，TTL 30s~5min），并消费 `ecos.security` 事件主动失效。本表高频端点尤其 `policy-engine/evaluate`。

### 3.2 datanet

| Method | Path | 调用方 | 用途 | 说明 |
|:-----:|:--|:--|:--|:--|
| POST | `/api/v1/engine/data/datasource` | buszhi(ge 转化) / 前端 | 建数据源 | 🔒 落库前连接串/凭据须 §3.1 encrypt |
| GET | `/api/v1/engine/data/datasource/{id}` | 前端 | 数据源详情 | 🔒 含敏感字段须 masking |
| GET | `/api/v1/pipeline/definitions` | 前端 | 管道列表（摘要） | 铁律 §4.8：列表只摘要 |
| GET | `/api/v1/pipeline/definitions/{id}` | 前端/编辑器 | 管道全量（含 nodes/edges） | 铁律 §4.8：编辑态拉详情 |
| POST | `/api/v1/pipeline/run` | 前端 | 触发执行 | 产生 `PipelineEvent(COLLECTION_COMPLETED)` → `ecos.catalog` |
| GET | `/api/v1/engine/data/lineage` | dccheng | 血缘 | — |
| GET | `/api/v1/engine/data/dq/records` | 前端 | 数据质量报告 | 🔒 |

### 3.3 buszhi

| Method | Path | 调用方 | 用途 | 说明 |
|:-----:|:--|:--|:--|:--|
| GET | `/api/v1/ecos/object/list` | 前端 | 对象列表 | — |
| GET | `/api/v1/ecos/object/{id}` | 前端/dccheng | 对象详情 | — |
| POST | `/api/v1/ecos/object` | 前端 | 创建对象 | 产生事件 → `ecos.object` |
| GET | `/api/v1/ecos/workflow` | 前端 | 工作流定义 | — |
| POST | `/api/v1/knowledge/sync` | **→ dccheng**（进程内，见 §3.4 注） | 本体→KG 同步 | 异步 |

### 3.4 dccheng（KB + Cognitive 合并，端口 18086）

| Method | Path | 调用方 | 用途 | 说明 |
|:-----:|:--|:--|:--|:--|
| GET | `/api/v1/knowledge/compliance-rules` | aiming（进程内=同 dccheng） | 合规规则 | cognitive 复用（铁律 §3.3 只读），kb 不写 |
| POST | `/api/v1/knowledge/sync` | buszhi | 本体→KG 同步 | 异步，产生事件 → `ecos.knowledge` |
| POST | `/api/v1/knowledge/rag` | aiming | RAG 检索 | `{query, topK}` → `{chunks[]}` |
| POST | `/api/v1/knowledge/reason` | **aiming（跨服务）** | 混合推理 | 路由例外（§1.1）；`{KG_QUERY/RULE_CHECK/VECTOR_RAG/HYBRID}` |
| POST | `/api/v1/knowledge/extract` | buszhi/datanet | 知识抽取（zhi） | 异步 |
| POST | `/api/v1/cognitive/diagnose` | aiming | 因果诊断 | ≥3 层因果链 |
| POST | `/api/v1/cognitive/reason` | aiming | 场景推演 | 混合推理 |
| GET | `/api/v1/cognitive/scenario/{id}` | 前端 | 场景详情 | 🔒 |

> 注：dccheng 内 kb 与 cognitive 为**同 JVM 进程内调用**（不再跨服务 REST）；aiming 调 dccheng 为跨服务 REST。

### 3.5 aiming

| Method | Path | 调用方 | 用途 | 说明 |
|:-----:|:--|:--|:--|:--|
| POST | `/api/v1/agent/chat` | 前端(SSE) | Agent 对话 | 流式；gateway `proxy_buffering off` |
| POST | `/api/v1/agent-loop/chat` | 前端(SSE) | Agent Loop | 流式 |
| POST | `/api/v1/agent/delegate` | aiming 内部 | 任务委派 | **进程内**（非跨服务，列表仅为说明） |
| GET | `/api/v1/agent/session/{id}` | 前端 | 会话详情 | 🔒 |
| POST | `/api/v1/agent/mesh/create` | 前端 | 创建 Mesh | — |

### 3.6 workspace（顶层独立场景应用层，:18090）

> workspace 是**综合场景应用层**，自身暴露对象运行时/ObjectQL/Scenario 端点，并向 sysman/datanet/buszhi/dccheng/aiming 发起跨服务 REST 编排。

| Method | Path | 调用方 | 用途 | 说明 |
|:-----:|:--|:--|:--|:--|
| POST/GET | `/api/v1/ecos/objects` | 前端 | 对象运行时 CRUD（ObjectAction/Object） | 🔒；**精确前缀**区分 buszhi 的 `/api/v1/ecos/object` |
| GET/POST | `/api/v1/ecos/objects/relationships` | 前端 | 对象关系 | 🔒 |
| GET/POST | `/api/v1/ecos/objects/state-machine` | 前端 | 对象状态机 | 🔒 |
| GET | `/api/v1/ecos/objects/timelines` | 前端 | 对象时间线 | 🔒 |
| GET/POST | `/api/query/*` | 前端 | ObjectQL 查询 | 🔒 |
| GET/POST | `/api/workbook` | 前端 | Workbook（Python/R/SQL Runtime） | 🔒 |
| GET/POST | `/api/v1/workspace/scenarios` | 前端 | 场景编排（跨 service 编排） | 🔒；workspace→各业务服务 REST |

> **路由消歧**：`/api/v1/ecos/objects/*` → workspace(:18090)；`/api/v1/ecos/object/*` → buszhi(:18083)。两者是**精确前缀兄弟**（`objects` vs `object`），网关按**最长前缀优先**匹配（AntPathMatcher 天然支持，但触铁律 §1.2"含连字符/相似前缀需显式写出完整前缀"提醒，P2-6 专项 E2E 验证）。

### 3.7 sysman BFF 兼容

| Method | Path | 说明 |
|:-----:|:--|:--|
| GET | `/sys-man/api/v1/ecos/agent/executions` | 前端经 sysman 查 Agent 执行；sysman REST 转发 aiming（跨服务禁 join，O7） |

---

## 4. 服务间认证与信任（current-plan ADR-7）

| 通道 | 机制 |
|------|:--|
| 外部 → gateway | JWT（`/api/v1/auth/login` 签发 RS256）；gateway **本地公钥验签**，不依赖 REST verify |
| gateway → service | 校验后 **strip 外部 `X-ECOS-*` 头并重新注入**（防伪造）；透传原始 `Authorization` |
| service → service（在线） | 透传用户 JWT |
| service → service（后台） | 服务凭证：sysman 签发 `service-{name}` JWT（角色 `SERVICE`，支持轮换）；standard 首期可 env 共享令牌 |
| 网络 | service 端口（18081-18086）**仅内网可达**；`X-ECOS-*` 仅信任网络内有效 |

**各 service 侧**: 轻量 `HeaderAuthInterceptor` 从 `X-ECOS-*` 还原用户上下文；sysman 内部安全端点校验调用凭证，未认证 **默认 DENY**（铁律 §2.4-6）。

### 4.1 请求头契约

| Header | 来源 | 说明 |
|--------|------|------|
| `Authorization` | 前端/JWT | 原始 JWT（在线链透传；后台链为服务凭证） |
| `X-ECOS-USER` | gateway strip 后重注入 | 用户 ID |
| `X-ECOS-TENANT` | 同上 | 租户 ID |
| `X-ECOS-ROLES` | 同上 | 角色列表，逗号分隔 |
| `X-ECOS-CALLER` | gateway/service | 调用方标识（如 `service-account`、`frontend`） |
| `X-Request-Id` | gateway 生成 | 分布式追踪 ID（贯穿调用链→事件 `traceId`） |
| `X-Forwarded-For` | nginx 注入 | 原始客户端 IP |

> 📌 gateway 聚合健康端点 `GET /api/v1/monitor/health` 须过三滤波器（VersionPrefixRewriteFilter 映射 + SecurityConfig permitAll + ClearanceInterceptor 豁免，双路径各写一遍）——**新增端点高频踩坑点**。

---

## 5. Kafka 事件契约

### 5.1 Topic（以 [KafkaTopics](file:///d:/workspace/javaprojects/ECOS/ecos_backend/common/common-api/src/main/java/com/chinacreator/gzcm/common/event/KafkaTopics.java) 常量为准）

| Topic（常量） | 生产者 | 消费者 | 用途 |
|:--|:--|:--|:--|
| `ecos.audit`（AUDIT） | 全部 service | sysman | 审计事件（ADR-8，**替代 REST 审计**） |
| `ecos.identity`（IDENTITY） | sysman | 各 service | user/role/permission/org/tenant 变更 |
| `ecos.catalog`（CATALOG） | datanet | buszhi/dccheng | pipeline/datasource 变更（COLLECTION/TRANSFORM） |
| `ecos.ontology`（ONTOLOGY） | buszhi | dccheng | 本体/规则/版本变更 |
| `ecos.object`（OBJECT） | buszhi | dccheng | 对象/实例/关系变更 |
| `ecos.workflow`（WORKFLOW） | buszhi | datanet/aiming | 工作流/审批/任务变更 |
| `ecos.agent`（AGENT） | aiming | 各 service | agent/execution/tool 变更 |
| `ecos.knowledge`（KNOWLEDGE） | dccheng | aiming | KG/RAG/抽取变更 |
| `ecos.security`（**新增**） | sysman | datanet/buszhi/dccheng/aiming | 权限/密钥失效广播（§3.1 缓存失效） |

> ⚠️ **现状**：`PipelineEvent` production 引用仅 1 处、Kafka listener 尚缺（common-api AGENTS.md 记 P2-4 缺口）——本表为**目标态**，属新建实现。旧契约的 `ecos.*.events` topic 名**全部作废**（实际常量无 `events` 后缀），`ecos.core.events` 随 core-service 删除作废。

### 5.2 消息格式（[PipelineEvent](file:///d:/workspace/javaprojects/ECOS/ecos_backend/common/common-api/src/main/java/com/chinacreator/gzcm/common/event/PipelineEvent.java)）

现存 `PipelineEvent` **无 correlationId/traceId**，须扩展（新增字段，不改既有签名）：

```json
{
  "eventId": "uuid",
  "eventType": "COLLECTION_COMPLETED",
  "correlationId": "uuid",          // 新增：链路关联（= X-Request-Id）
  "traceId": "celtraceid",          // 新增：分布式追踪
  "dataSourceId": "datasource-1",
  "dataSourceName": "pg-extract",
  "pipelineId": "pipe-42",
  "occurredAt": "2026-09-10T12:00:00Z",
  "metadata": { "rowCount": 1000, "targetTable": "t_extract" },
  "sourceModule": "datanet"
}
```

`EventType`（现存枚举）：`COLLECTION_COMPLETED` / `CLEANSING_COMPLETED` / `TRANSFORM_COMPLETED` / `PIPELINE_FAILED` / `DATASOURCE_STATUS_CHANGED`。新增场景（审计/权限失效）复用本载体，`sourceModule` 区分。

### 5.3 可靠性契约（🔴 微服务化生命线）

| 维度 | 约定 |
|------|:--|
| **幂等键** | 消费者以 `eventId` 去重（`ecos.audit` 以 `eventId+sourceModule` 复合键）；重复投递不产生副作用 |
| **重试** | 至少一次投递；消费失败重试 3 次（指数退避 1s/4s/15s） |
| **死信** | 3 次失败 → DLQ topic `{topic}.dlt`；runtime-monitor 告警 |
| **乱序** | 消费者按 `occurredAt` 容忍乱序；状态类以"最后写入 + 版本号"收敛 |
| **顺序性** | 同 `correlationId` 强制同分区（分区键 = `correlationId`）保序 |

---

## 6. 健康检查端点（每服务）

| 端点 | 返回 |
|------|------|
| `GET :{port}/actuator/health` | `{"status":"UP"}` — 存活 |
| `GET :{port}/actuator/health/liveness` | 存活探针 |
| `GET :{port}/actuator/health/readiness` | 就绪探针 |

> 适用 deployment：sysman(18081) / datanet(18082) / buszhi(18083) / aiming(18084) / dccheng(18086) / **workspace(18090)** / gateway(8080)。

gateway 聚合：`GET :8080/api/v1/monitor/health` → 遍历各 service + workspace `/actuator/health` 返回聚合状态。该端点须过三滤波器（§4.1 注）。
