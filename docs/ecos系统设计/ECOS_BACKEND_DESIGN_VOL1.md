# 企业认知操作系统 (ECOS) 后端详细设计说明书
## 第一分册：核心安全、语义实体与智能体底座架构 (Volume 1: Core Security, Semantic Entities & Agent Infrastructure)

本说明书承接《C2EOS 系统蓝图功能图》与前端的高密认知设计风格，针对企业级认知操作系统（Enterprise Cognitive Operating System, ECOS）的后端关键服务、通信协议、安全沙盒、密码学双写审计以及多智能体（Agent Mesh）底座，进行深度的系统级详细设计。

---

## 1. 总体物理与微服务架构

ECOS 采用基于 Kubernetes 容器编排的云原生微服务架构，核心后端使用 **Go / Rust** 作为高性能底座模型计算与沙盒控制服务，使用 **Node.js (TypeScript) / Python** 编写业务逻辑与大模型编排层。

```
                                 [ 外部终端 / Web Portal / 大屏指挥中心 ]
                                                 │ 
                                                 ▼ (mTLS + HTTPS / WSS)
                                      [ ECOS API 网关集群 ]
                                                 │
            ┌───────────────────┬────────────────┴──────────────────┬───────────────────┐
            ▼                   ▼                                   ▼                   ▼
    [安全准入与设备微服务] [语义实体运行时服务]                     [Agent-OS 运行时服务]  [水流计算与高性能湖仓]
    (IAM / ABAC & mTLS)   (Ontology Runtime)                 (Agent Mesh / A2A)   (Doris/MinIO Pipeline)
            │                   │                                   │                   │
            ▼                   ▼                                   ▼                   ▼
    [设备指纹/CA中心]   [PostgreSQL (Drizzle)]             [Vector DB / Redis]  [Doris + MinIO + Redis]
            │                   │                                   │                   │
            └───────────────────┴───────────────┬───────────────────┴───────────────────┘
                                                ▼
                                    [密码学防篡改审计链表服务] (双写链式签名)
```

---

## 2. 右上角高阶安全面板在后端的映射架构设计

前端右上角高阶安全配置面板所映射的核心要素包括：**绑定物理工作站**、**安全准入等级**、**双写审计力度** 以及 **强制高危指令智能体沙盒审查**。以下是其对应的后端详细架构与协议设计。

### 2.1 物理工作站安全认证 (Linked Safe Workstation)
任何接入 ECOS 后端核心业务的终端，除了持合法的 JWT 凭证外，必须满足**硬绑定物理工作站**的安全拦截。

1. **双向 TLS 认证（mTLS）**：
   - 每一个授权的物理工作站均在本地安全芯片（TPM 2.0）中生成唯一的物理密钥对，并由系统内建的私有 CA（Certificate Authority）签发设备证书。
   - API 网关在握手阶段强制验证客户端设备证书（Client Certificate），提取并解密证书中的工作站唯一硬件标识码（如 `UUID-WS-COSMOS-09`）。
2. **多重设备特征指纹校验（Device Fingerprinting）**：
   - 本地轻量级 Daemons（守护进程）搜集 CPU Serial、硬盘 UUID、网卡 MAC 地址，通过 SHA-256 计算设备动态指纹特征，并在请求中注入特定的 `X-ECOS-Fingerprint` 哈希。
   - 后端防伪检测中间件比对当前 IP 段、证书内置标识与指纹哈希：
     $$\text{Fingerprint}_{\text{calc}} = \text{HMAC-SHA256}(\text{CpuID} \mathbin{\Vert} \text{MacAddr} \mathbin{\Vert} \text{WorkstationID}, K_{\text{system}})$$

```json
{
  "header": {
    "X-ECOS-Workstation-Id": "WS-COSMOS-09",
    "X-ECOS-Fingerprint": "8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca120202160612abcd",
    "Authorization": "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }
}
```

### 2.2 基于 ABAC 的多级安全准入控制 (clearance level)
ECOS 不采用单纯的 RBAC（基于角色的访问控制），而是采用粒度更细、上下文相关的 **ABAC（基于属性的访问控制）** 引擎。

1. **属性维度定义**：
   - **主体属性（Subject Attributes）**：用户的安全准入等级（Clearance Level: 1级至5级。默认 4级高阶授权专家）、当前绑定的物理工作站受信状态。
   - **客体属性（Object Attributes）**：目标操作实体（如：Factory Alpha 泵阀控制参数）的安全密级、操作类型（Read / Write / Invoke）。
   - **环境属性（Environment Attributes）**：内网 IP、时间段、当前系统威胁等级。
2. **规则决策器 (PDP - Policy Decision Point)**：
   - 后端拦截器将主体、客体及操作统一重组为决策请求，交付高性能 ABAC 推理引擎（如基于 Open Policy Agent - OPA 的 Rego 脚本）。

```rego
# OPA 决策服务规则实例
package ecos.authz

default allow = false

allow {
    # 规则1: 操作员准入等级必须大于或等于资源的敏感密级
    input.subject.clearance_level >= input.object.classification_level
    
    # 规则2: 必须在绑定的物理工作站上进行修改操作
    input.subject.workstation == input.object.allowed_workstations[_]
    
    # 规则3: 如果是高危写入或执行操作，系统必须不在红色威胁警报状态下
    input.action.type == "Execute"
    input.environment.threat_level != "CRITICAL"
}
```

### 2.3 双写不可篡改审计系统 (Dual-Write Audit Mode)
对于核心指令，系统提供两种可选审计级别，满足完全不同的实时合规校验需求：

#### 方案 A：普通控制台日志模式 (`standard` Mode)
- 操作日志通过异步队列（如 Kafka）投递至常规时序数据库（如 InfluxDB）/ Elasticsearch 中，仅保持结构化历史记录以供日常检索，审计性能损耗接近于零。

#### 方案 B：高密不可篡改密码学签名账本模式 (`full` Mode)
- **双写流程**：当执行具有状态变更的核心指令时，数据在存入传统 PostgreSQL/Drizzle 业务数据库的同时，必须同步阻塞写入**防篡改密码学签名账本（Audit Ledger）**。
- **Merkle 链式签名设计**：
  每一个审计区块（Audit Block）头部均包含上一个区块的区块哈希（PreHash）、当前操作的时间戳、操作详情、操作员数字签名，以及通过当前实体状态和属性联合哈希计算得出的密匙状态：
  $$\text{Hash}_n = \text{SHA256}(\text{Hash}_{n-1} \mathbin{\Vert} \text{Payload}_n \mathbin{\Vert} \text{Timestamp} \mathbin{\Vert} \text{OperatorSignature})$$
- 这种结构使得攻击者在不掌握系统主私钥和所有历史验证树的前提下，由于哈希链条的雪崩效应，**绝对无法在事后篡改任何一次高风险操作的历史日志**。

```json
{
  "audit_block_id": 104857,
  "pre_hash": "a4f8902d3bb9c2401f8d956a29dfc29e0de549eecc3fbb51a8facdd40a831e5f",
  "timestamp": "2026-06-21T20:25:00Z",
  "operator": {
    "email": "guorongxiao@gmail.com",
    "clearance_level": 4,
    "workstation": "WS-COSMOS-09"
  },
  "action": {
    "service": "OntologyRuntime",
    "method": "ExecuteActionOverride",
    "params": {
      "target_object": "conveyor_belt_03",
      "action_trigger": "RerouteToDockB"
    }
  },
  "block_hash": "bc698eb39d1b0283c7cc618bb9d1b02826cfd5aa6acf3ca12020216061212ab0"
}
```

### 2.4 高危指令智能体沙盒审查 (Command Sandboxed Runtime)
在 `sandbox` 模式启动下，智能体（Agent）所产生或建议的物理控制、调度指令，**绝不允许直接对物理真实系统生效**。

1. **虚拟推演环境（Micro-Sandbox Sandbox）**：
   - 后端自动为该 Agent 构建隔离的进程级或容器级沙盒推演空间（如：基于 WebAssembly 虚拟机组件或 Firecracker 极简微型 VM ）。
   - 沙盒中复制了一套基于**世界模型 (World Model)** 与**历史时序数据**运行的物理模拟仿真节点，在微秒级时间内演绎执行指令。
2. **两阶段提审机制 (Two-Phase Commit Verify - 2PC)**：
   - **第一阶段（推演审计与危害分析）**：
     Agent 在沙盒内执行拟定的行动指令，收集模拟执行产生的状态指标（如压力、温度、资金吞吐），输入给“安全检查官 Agent”进行二次红队威胁分析，判定是否存在过冲损失。
   - **第二阶段（双因子人工二次重连验证 - mFA & Hot-reload）**：
     如果评估出的风险因子超过预先设定的 ABAC 阈值，API 端将向管理员绑定的工作站发送热重连审核弹窗，须通过二次硬证书密钥签名（如 Yubikey 握手）来下发最终物理控制器。

---

## 3. 智能体操作系统层与多智能体协同协议 (Agent OS & A2A Protocol)

### 3.1 核心智能体协作与通信协议 (A2A Protocol)
Agent Mesh 中，多个自协商智能体（如目标规划 Agent、数据管道 Agent、风险感知 Agent）通过事件驱动总线异步交互，其 A2A 通信协议定义如下：

```json
{
  "protocol_version": "A2A-1.3",
  "message_id": "msg_agent_908f921d",
  "correlation_id": "session_chain_089104",
  "timestamp": "2026-06-21T20:25:12Z",
  "route": {
    "sender": "agent_goal_decomposer",
    "recipient": "agent_pipeline_scheduler",
    "trace_path": ["agent_goal_decomposer"]
  },
  "intent": "DecomposeTarget",
  "payload": {
    "goal_id": "g_rev_target",
    "target_parameters": {
      "revenue_target": 890000000,
      "time_horizon": "Q2-2026"
    },
    "constraints": {
      "allowable_risk_level": "LOW",
      "max_downtime_hours": 36
    }
  },
  "verification_tag": "sig_52ad9eeffc102b3c7"
}
```

### 3.2 智能体长期记忆与语义记忆追踪系统 (Memory Graph)
智能体不仅通过向量数据库（如 Pinecone / Milvus / PostgreSQL pgvector）存取历史经验知识的相似度，还依赖**关系型图结构**（Memory Graph）实现链式的因果网络推理。
- 后端维护了图关联表结构，将“先前决策结果”和“真实业务产出”定义成概念边的权重（Concept Edge Weight）。
- 随着时间推演，学习与改进引擎通过梯度惩罚自适应地对不良决策连线进行**降权/记忆遗忘**，实现系统的持续自适应改进。

---

## 4. 物理数据平台层设计 (Data Platform Layer Design)

### 4.1 核心数据湖仓与多维高高能计算分析层 (Doris + MinIO + Redis Stack)
物理数据接入涉及大量的传感器电传时序事件与大规模业务流数据。底层抛弃了庞重的 Spark 方案，采用更敏捷、物理并发能力更高的 **Apache Doris + MinIO + Redis** 联合数据架构：

- **极速热数据摄入层与实时 OLAP 引擎 (Apache Doris)**：
  - Doris 通过内建的 **Routine Load** / **Stream Load** 直接秒级订阅 Kafka 消息队列或摄取应用服务器推送的 JSON 电传流。
  - 对于业务汇总与计算，利用 Doris 极强的实时聚合与高并发物化视图（Materialized Views）对时序指标（如产量变动、设备状态指标）进行亚秒级统计。 DQL 引擎能自底向上传递秒级计算指标：
  ```sql
  -- Doris 动态物化视图与窗口时序检测示例
  CREATE MATERIALIZED VIEW mv_sensor_hourly_stat AS
  SELECT 
    workstation_id,
    date_trunc('minute', event_time) AS stat_minute,
    AVG(machine_failure_probability) AS avg_fail_prob,
    SUM(production_throughput) AS total_throughput
  FROM iot_sensor_stream
  GROUP BY workstation_id, date_trunc('minute', event_time);
  ```
- **冷数据归档与高弹对象湖存储 (MinIO Object Storage)**：
  - ECOS 利用 MinIO 作为统一的 S3 兼容对象湖。Doris 定期自动将历史冷数据归档至 MinIO Parquet 存储桶，支持外表联合查询（MinIO External S3 Catalog）。
- **边缘动态高速缓冲与状态协调层 (Redis Engine)**：
  - 维护瞬时高速时序电传数据的内存热排（Redis Time-Series & Sorted Sets），用以刷新实时监测中心面板，并存储 Pipeline DAG 中间演练状态。

---

## 5. 统一数据实体关系（Drizzle / Prisma / SQL 语义对应）

我们在后端应用中定义以下持久化数据库对象模型设计，以支持在 C2EOS 中完成上述功能的数据读取：

```typescript
// 数据库表结构定义参考 Schema (drizzle-orm style)
import { pgTable, text, timestamp, integer, boolean, real, jsonb } from "drizzle-orm/pg-core";

// 1. 系统核心用户配置表（绑定物理工作站与准入安全审计配置）
export const userSecurityConfigs = pgTable("user_security_configs", {
  userId: text("user_id").primaryKey(),
  email: text("email").notNull(),
  clearanceLevel: integer("clearance_level").default(4), // 1-5 等级
  linkedWorkstation: text("linked_workstation").default("WS-COSMOS-09"),
  auditMode: text("audit_mode").default("full"), // full / standard
  sandboxMandatory: boolean("sandbox_mandatory").default(true),
  updatedAt: timestamp("updated_at").defaultNow(),
});

// 2. merkel 链式密码学不可篡改审计数据表
export const cryptographicAuditLedger = pgTable("cryptographic_audit_ledger", {
  blockId: integer("block_id").primaryKey(),
  preHash: text("pre_hash").notNull(),
  currentHash: text("current_hash").notNull(),
  timestamp: timestamp("timestamp").defaultNow(),
  operatorId: text("operator_id").notNull(),
  operatorEmail: text("operator_email").notNull(),
  actionName: text("action_name").notNull(),
  actionPayload: jsonb("action_payload").notNull(),
  signatureVerification: text("signature_verification").notNull(),
});

// 3. 智能体物理沙盒运行任务指令追踪表
export const agentSandboxTasks = pgTable("agent_sandbox_tasks", {
  taskId: text("task_id").primaryKey(),
  agentId: text("agent_id").notNull(),
  proposedAction: text("proposed_action").notNull(),
  simulationDurationMs: integer("simulation_duration_ms"),
  estimatedRiskFactor: real("estimated_risk_factor"), // 0.0 - 1.0 风险因子评估
  sandboxOutcomes: jsonb("sandbox_outcomes"),
  approvalStatus: text("approval_status").default("PENDING"), // PENDING, APPROVED, REJECTED
  verifiedByOperator: text("verified_by_operator"),
  createdAt: timestamp("created_at").defaultNow(),
});
```

---

## 6. 后端高弹性、灾备与通信通道重建方案

1. **热备接管（Hot-reload Heartbeat）**：
   - 物理工作站与后端维系周期为 3 秒的安全心跳链路。一旦心跳异常或客户端网络包内指纹发生偏离，API 网关立即将该信道切换为“临时受控沙箱”级别，关闭所有真实指令通道，自动强制执行 ABAC 阻写。
2. **连接热重连模式**：
   - 用户在前端右上角修改并“保存并重连核准通道”后，前端通过全双工 WebSocket 发送重新注册握手请求。
   - 后端重新下发动态对称会话秘钥，在不中断渲染主控大屏的前提下，瞬间重连底层物理通信网道。

---
**《企业认知操作系统 (ECOS) 后端详细设计说明书 · 第一分册》编制完成，待评审签发。**
