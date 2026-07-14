# 企业认知操作系统 (ECOS) 后端详细设计说明书
## 第四分册：智能体工作室 Mesh 编排、全栈 Prometheus/OpenTelemetry 监控与零信任安全审计 (Volume 4: Multi-Agent Studio Mesh, Full-stack Telemetry, and Zero-Trust ABAC Security Audit)

本说明书承接前三分册，全面补全 ECOS 平台的后端微服务设计。本分册重点阐述战略协同生态中的三大中枢系统设计：**多智能体协作链（Agent Mesh）与追踪执行内核**、**Prometheus/OpenTelemetry 集群高并发指标采集引擎（Monitoring Daemon）**，以及**零信任 ABAC 强制资源拦截与抗篡改链式日志归档系统**。

---

## 1. 智能体工作室：基于 ReAct 规划模型的多智能体协同协同内核 (Agent Studio Peer-Mesh)

`AgentStudio` 中定义的各类 Specialist Agents (如 Coordinator, Researcher, Analyst) 需要统一的后端运行驱动。ECOS 基于 **双层规划-执行架构 (ReAct/Plan-and-Solve)** 构建智能体编排网格。

```
                       [ 决策总目标 (Forensic Goal) ]
                                      │
                                      ▼
                        ┌────────────────────────┐
                        │   Coordinator Agent    │
                        │ (切分任务为 3 步里程碑)  │
                        └──────────┬─────────────┘
                                   │
              ┌────────────────────┴────────────────────┐
              ▼                                         ▼
   ┌──────────────────────┐                  ┌──────────────────────┐
   │   Researcher Agent   │                  │    Analyst Agent     │
   │ (Tool: SearchCustomer│                  │ (Tool: QueryOrders)  │
   └──────────┬───────────┘                  └──────────┬───────────┘
              │                                         │
              └────────────────────┬────────────────────┘
                                   ▼
                        ┌────────────────────────┐
                        │ Consensus State Merge  │
                        │ (共识状态收敛与评估)    │
                        └────────────────────────┘
```

### 1.1 智能体规划状态机与动态上下文装配器 (Orchestrator Context Assembler)
后端服务 `ecos-agent-kernel` 实现以下主循环流程：
1. **输入装配**：合并 Agent 的 System Prompt（静态元数据）、工作站安全 Clearance 以及当前内存变量。
2. **思考与轮询 (Thought-Action-Observation Loop)**：
   - 模型思考（Thought）：分析当前状况，规划最紧迫的任务。
   - 工具调用（Action）：匹配 `MOCK_TOOLS` 中声明的 JSON Schema，并将工具名与参数回传给宿主机。
   - 物理反馈（Observation）：执行环境下的驱动函数、落库查询、或者是数据清洗流水线，并将结果追加到 LLM 上下文中。
3. **共识收敛 (Merge Evaluator)**：
   - 收集各个专职 Agent 的中间执行报告，由主引导智能体（Coordinator）校验数据完整度，通过最终决策逻辑决策。

### 1.2 OpenTelemetry-style 算力与 Token 消耗审计协议 (Telemetry Cost Auditor)
每一次大模型交互在 ECOS 内部必须产生详细的可追溯流信息，记录成功率、延迟、令牌数以及计算折现。

```json
{
  "trace_id": "tr_forensic_9a01bb",
  "span_id": "sp_specialist_research_01",
  "parent_span_id": "sp_coordinator_init",
  "agent_id": "researcher",
  "tokens_stats": {
    "prompt_tokens": 1280,
    "completion_tokens": 420,
    "total_tokens": 1700,
    "pricing_rate_per_million": 0.15,
    "cost_usd": 0.000255
  },
  "invoked_tool": {
    "tool_name": "SearchCustomer",
    "status": "success",
    "duration_ms": 480
  },
  "timestamp": "2026-06-21T21:05:06.120Z"
}
```

---

## 2. 边缘与指标监控：Prometheus/OpenTelemetry 流式电传网关

`MonitoringCenter` 中展示的 CPU利用率、高并发时序 ingestion 吞吐波形需要由一个秒级采集（High-scrubbing Rate）的边缘监控守护服务 `ecos-monitoring-daemon` 提供源源不断的电传数据。

```
 [Docker/K8s cgroups] ──► [Prometheus Exporter] ──► [ECOS Metric Consumer] ──► [Redis Time-Series Cache]
                                                                                      │
                                                                                 (WebSocket)
                                                                                      ▼
                                                                           [前端 Recharts 渲染]
```

### 2.1 高频电传数据实时拉取架构 (Ingestion Data Shaker)
1. **硬件 Cgroups 电传抓取（Host Telemetry Fetching）**：
   - 守护进程直接读取宿主机系统文件：
     - CPU：% 占用从 `/sys/fs/cgroup/cpu/cpu.stat` 计算平均核周期算出。
     - Memory：`/sys/fs/cgroup/memory/memory.usage_in_bytes` 映射为内存消耗量。
2. **Doris Routine Load/MQTT 吞吐率电传统计 (Ingestion Throughput Rate)**：
   - Doris 集群通过 Prometheus 导出器或内置 Metric API，将当前 Routine Load / Stream Load 的并发写入吞吐行数和字节速率推送至 `ecos-metric-gateway`，并发读写状态由极速 Redis（Redis Time-Series）维护缓存。
   - MQTT 数据网关（连接工业物联网节点）则直接在大并发传输时将数据计数打点缓存在 Redis 的高频时序索引中。
   - Web 端的 WebSocket 频道 `ws://ecos-ingress-gateway/api/v1/telemetry/live-speed` 每 $500\text{ ms}$ 将时序指标包向前推送，驱动前端 Recharts 仪表盘平滑无缝地更新吞吐速率与物理电传波形。

### 2.2 时序阈值评估与自动告警规则配置机 (Alert Threshold Rule Broker)
监控引擎在计算的过程中运行指标评估逻辑。一季度检测到越限行为，直接向平台管理员派发系统告警（Alert-Broadcast）。

```yaml
# 监控中心自动告警规则配置文件 EcosAlertRules.yaml
groups:
  - name: EcosCoreHardwareAlerts
    rules:
      - alert: CriticalOverHeatSpike
        expr: ecos_plant_ops_sensor_temperature_celsius > 115
        for: 5s
        labels:
          severity: WARNING
          tier: hardware_edge
        annotations:
          summary: "Physical engine thermal sensor overheating detected"
          description: "Machine identifier {{ $labels.machine_id }} reported spiked overheat higher than threshold."
      - alert: LargeTransactionEscalation
        expr: ecos_billing_transaction_value_usd > 1000000
        labels:
          severity: INFO
          tier: abac_security
        annotations:
          summary: "Enormous Pending transaction auto-escalated to security sandbox verification"
```

---

## 3. 零信任与 ABAC 安全审计：防篡改链式日志与双写安全机制 (Zero-Trust Security & Audit Logs)

在 ECOS 的统一物理/语义模型中，任何操作（如“修改数据库表”、“将物理设备退化”、“拉高加热器功率功率”）不仅受到 RBAC（角色授权控制）拦截，还深度受到 **ABAC（属性访问属性控制，Attribute-Based Access Control）** 和物理沙盒引擎的安全限制验证：

```
       [ 用户发起物理控制指令 Request ]  
                       │
                       ▼ 
    ┌──────────────────────────────────────┐
    │     ABAC 安全过滤器拦截评估层         │
    │  - 匹配 clearance 等级 (如 L4 才能写)│
    │  - 评估当前机器操作员物理物理位置    │
    └──────────────────┬───────────────────┘
                       │ 
               [ 通过 (Cleared) ]
                       │
                       ▼
    ┌──────────────────────────────────────┐
    │     防篡改链式日志落库 (Cryptographic)│
    │  - 计算 Hash_{t} = SHA256(Record_t)  │
    │  - 锁链连接 Hash_{t-1} 并写安全硬账本│
    └──────────────────┬───────────────────┘
                       │
                       ▼
         [ 真实世界物理控制器执行指令 ]
```

### 3.1 基于属性的安全拦截机制验证规范 (ABAC Rules Validation Matrix)
在用户提交任意带有物理修改属性的事务时，ABAC 模块对请求进行阻断评估：

$$A = \{\text{SubjectAttrs}, \text{ObjectAttrs}, \text{EnvironmentAttrs}\}$$

* **条件一（安全等级差）**：
  $\text{SubjectAttrs.clearance} \ge \text{ObjectAttrs.required_clearance}$。若操作员的当前 Clearance 等级低于资产需要的 L4 级权限，强制抛出安全越级异常并熔断。
* **条件二（属性物理围栏评估）**：
  在生产物理指令发送时，若 $\text{EnvironmentAttrs.current_host_ip}$ 不处于授权厂区白名单内，即使 Clearance 足够也强制将对象状态转为隔离受限沙箱（`Sandboxed`）。

### 3.2 密码学链式审计历史账本设计 (Cryptographic Ledgers Database)
一旦通过 ABAC 检测，所有高危险核心操作（如 $150\text{ 万}$ 大额账单处理、阀门温控指令、或 AI 修改系统提示词）在写入业务主数据库的同时，**必须强制、不可分割地、原子性双写（Double-Write）**到只读密码学链式安全账本（Security Ledgers Table）中。

这采用了一种类似区块链哈希传递链（Hash Chain）的机制：每一条日志均由**前一条日志的哈希密匙、当前内容的哈希、以及操作员签发数字签名 (ECDSA Signature)** 特征合成：

$$H_t = \text{SHA256}\left( H_{t-1} + \text{Message}_t + \text{SignOperator}_t \right)$$

```sql
-- Drizzle/PostgreSQL 级加密防篡改审计日志物理表
CREATE TABLE ecos_audit_cryptographic_ledger (
    log_index SERIAL PRIMARY KEY,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    operator_uid VARCHAR(64) NOT NULL,
    workstation_id VARCHAR(64) NOT NULL,
    action_type VARCHAR(64) NOT NULL,              -- e.g., "COGNITIVE_OVERRIDE_PROMPT"
    action_payload JSONB NOT NULL,                 -- 完整操作数据负载
    previous_block_hash CHAR(64) NOT NULL,         -- 上一条 H_{t-1}
    current_block_hash CHAR(64) UNIQUE NOT NULL,    -- 当前 SHA256 复合哈希 H_t
    operator_signature TEXT NOT NULL               -- 操作员基于私钥签发的可验证电子签名
);
```

---
**《企业认知操作系统 (ECOS) 后端详细设计说明书 · 第四分册》编制完成，待评审签发。**
与会架构评审委员会成员：ECOS 后端设计专家组、企业级数字孪生研发小组。
