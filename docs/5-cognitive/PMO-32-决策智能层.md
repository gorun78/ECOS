# PMO指令: ECOS 决策智能层（借鉴 Semantica · P0-A）

> **来源**: 肖国荣 | **日期**: 2026-08-20
> **协同**: ECOS-PM（cognitive-engine 主责）+ ECOS-ARCH（架构评审）
> **架构铁律**: 必须遵循 [ECOS架构铁律](../ARCHITECTURE-RULES.md)
> **关联**: 配套方案 `../ECOS-借鉴Semantica-完整方案.md`、差距分析 `../ECOS-借鉴Semantica-差距分析.md`

## 零、现状摸底

cognitive-engine 现有推理能力（已核实代码）：

| 类 | 现状 |
|----|------|
| `CausalReasonerServiceImpl`(726行) | KG路径遍历 + LLM补充，构建≥3层因果链。**真实现** |
| `KnowledgeReasonerService`(484行) | KAG混合推理(KG/RULE/RAG/HYBRID)，**真实现** |
| `RuleCausalService`(157行) | 通过 sys_compliance_rule 的 description 文本匹配建因果链。**弱** |

**核心缺口**：决策散落在三处各自为政——
- `V29__ecos_ceo_causal_chain.sql`（CEO 因果链）
- `GovernanceDecision.java`（agent-service 治理决策）
- `V30__ecos_diagnostic_agent.sql`（诊断 Agent）

**没有**"决策即一等公民节点"的通用抽象：无先例检索、无策略合规门、无审批链、无决策溯源。

## 一、目标架构

在 cognitive-engine 落地 Semantica `context/` 的"决策智能"抽象（翻译成 Java，**非抄代码**）：

**五元组数据模型**：Decision / Policy / Exception / Precedent / ApprovalChain
**五步生命周期**：record → link → query → govern → audit
**统一溯源**：所有决策/事实写入共享 `ecos_provenance_entry`（P0-C 数据级，本指令落地表结构）

**KAG 定位**：决策是 KAG reasoner 推理的**落地产物**，不是独立模块——KAG 推理出结论（`KnowledgeReasonerService`），决策落地（record_decision）在 KAG 推理出口触发。决策的记录/先例/合规门/审批是 KAG 没覆盖的能力，融合进 KAG reasoner。

## 二、分阶段执行计划（5 个 Task）

| Task | 文件/路径 | 操作 | 工期 |
|:-----|----------|------|:---:|
| T1 | `engine/cognitive-engine/cognitive-engine-api/.../cognitive2/model/` 下新建 `Decision.java`、`DecisionPolicy.java`、`DecisionException.java`、`DecisionPrecedent.java`、`ApprovalChain.java`、`ProvenanceEntry.java` | 五元组 + 溯源模型类（纯 POJO，getter/setter） | 0.5天 |
| T2 | `gateway/src/main/resources/db/migration/V103__ecos_decision.sql` | 建 6 张表（见下方 DDL 契约） | 0.5天 |
| T3 | `.../cognitive2/DecisionService.java`(api) + `.../cognitive2/service/DecisionServiceImpl.java`(impl) | 五步生命周期实现 | 2天 |
| T4 | `.../cognitive2/controller/DecisionController.java` + `.../cognitive2/controller/ProvenanceController.java` | REST API + auth whitelist | 1天 |
| T5 | `.../cognitive2/service/DecisionLegacyBridge.java` | V29 因果链 + GovernanceDecision 挂入 category，作为兼容层 | 1天 |

### T2 表结构契约（6 张表）

```sql
-- V103__ecos_decision.sql
CREATE TABLE IF NOT EXISTS ecos_decision (
    id              VARCHAR(64) PRIMARY KEY,
    category        VARCHAR(64)  NOT NULL,   -- 决策分类（如 vendor_selection / compliance / diagnostic）
    scenario        TEXT,                    -- 场景描述
    reasoning       TEXT,                    -- 推理依据
    outcome         VARCHAR(128),            -- 决策结果
    confidence      DECIMAL(5,4),            -- 置信度 0~1
    decision_maker  VARCHAR(64),             -- 决策者（人/Agent）
    valid_from      TIMESTAMP,
    valid_until     TIMESTAMP,
    metadata        JSONB,
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS ecos_decision_causal_link (
    id                  VARCHAR(64) PRIMARY KEY,
    source_decision_id  VARCHAR(64) NOT NULL,
    target_decision_id  VARCHAR(64) NOT NULL,
    relationship        VARCHAR(32) NOT NULL,  -- triggers/enables/causes/precedes
    weight              DECIMAL(5,4) DEFAULT 0.5,
    created_at          TIMESTAMP DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS ecos_decision_policy (
    id          VARCHAR(64) PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    category    VARCHAR(64),
    rules       JSONB,                        -- 规则+约束
    version     INTEGER DEFAULT 1,
    status      VARCHAR(16) DEFAULT 'active', -- active/retired
    created_at  TIMESTAMP DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS ecos_decision_exception (
    id          VARCHAR(64) PRIMARY KEY,
    decision_id VARCHAR(64) NOT NULL,
    reason      TEXT,
    approver    VARCHAR(64),
    status      VARCHAR(16) DEFAULT 'pending', -- pending/approved/rejected
    created_at  TIMESTAMP DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS ecos_decision_approval (
    id          VARCHAR(64) PRIMARY KEY,
    decision_id VARCHAR(64) NOT NULL,
    approver    VARCHAR(64),
    level       INTEGER DEFAULT 1,
    status      VARCHAR(16) DEFAULT 'pending', -- pending/approved/rejected
    comment     TEXT,
    created_at  TIMESTAMP DEFAULT NOW()
);
CREATE TABLE IF NOT EXISTS ecos_provenance_entry (
    id          VARCHAR(64) PRIMARY KEY,
    entity_type VARCHAR(32) NOT NULL,  -- decision/fact/rule
    entity_id   VARCHAR(64) NOT NULL,
    source_type VARCHAR(32),           -- KG/RULE/RAG/LLM/MANUAL
    source_ref  TEXT,                  -- 来源引用（文档/规则/节点）
    agent       VARCHAR(64),           -- 执行 Agent
    activity    VARCHAR(64),           -- 活动（record/link/reason）
    timestamp   TIMESTAMP DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_decision_category ON ecos_decision(category);
CREATE INDEX IF NOT EXISTS idx_causal_source ON ecos_decision_causal_link(source_decision_id);
CREATE INDEX IF NOT EXISTS idx_causal_target ON ecos_decision_causal_link(target_decision_id);
CREATE INDEX IF NOT EXISTS idx_provenance_entity ON ecos_provenance_entry(entity_type, entity_id);
```

### T3 五步生命周期 API 契约（DecisionService 接口方法）

```java
// 对齐 Semantica decision_methods.py，翻译成 Java
String recordDecision(String category, String scenario, String reasoning,
                      String outcome, double confidence, String decisionMaker);
void   addCausalRelationship(String sourceId, String targetId, String relationship);
List<Decision> findSimilarDecisions(String query, int maxResults);
List<Decision> traceDecisionChain(String decisionId);          // 因果祖先链
Map<String,Object> analyzeDecisionImpact(String decisionId);    // 下游影响图
Map<String,Object> checkDecisionRules(String decisionId);       // 策略合规门
```

### T4 REST 端点（前缀 `/api/v1/cognitive`）

| 方法 | 路径 | 对应 |
|------|------|------|
| POST | `/api/v1/cognitive/decision/record` | record_decision |
| POST | `/api/v1/cognitive/decision/{id}/link` | add_causal_relationship |
| GET | `/api/v1/cognitive/decision/similar?query=...` | find_similar_decisions |
| GET | `/api/v1/cognitive/decision/{id}/chain` | trace_decision_chain |
| GET | `/api/v1/cognitive/decision/{id}/impact` | analyze_decision_impact |
| POST | `/api/v1/cognitive/decision/{id}/check-rules` | check_decision_rules |
| GET | `/api/v1/cognitive/provenance?entityType=&entityId=` | 溯源查询 |

## 三、禁止清单

1. **禁止新建 Maven 模块** — 全部落在 cognitive-engine 现有 api/impl/boot 三子模块
2. **禁止修改现有 API 路径或签名** — CausalReasonerService/KnowledgeReasonerService 等现有端点不动
3. **禁止用 JdbcTemplate 直连数据库** — Controller 只调 Service，Service 走 Mapper/JdbcTemplate
4. **禁止推倒 V29 因果链 / GovernanceDecision** — 只挂入，不删除不重写
5. **禁止跨 Phase 预创建文件** — 本指令只做决策层，编排层/SpEL/非结构化接入等留后续指令
6. **禁止硬编码 LLM 调用** — find_similar/trace 的语义检索如需 LLM，走 ai-engine Agent Loop（与 `CausalReasonerServiceImpl.callLlm` 同模式）

## 四、风险与回滚

- **风险1**：`find_similar_decisions` 语义检索依赖向量库，若 pgvector 未就绪 → 降级为 category 精确匹配 + 关键词，不影响主流程。
- **风险2**：`GovernanceDecision` 在 agent-service 模块，cognitive-engine 直接 import 会违反依赖方向 → T5 用 REST 或仅在 DB 层做 category 映射，不 import agent-service 类。
- **回滚**：新增表可 `DROP TABLE`（未改现有表），新增类删除即可，不动既有代码。

## 五、工时估算

| Task | 工期 |
|------|:---:|
| T1 模型 | 0.5天 |
| T2 表结构 | 0.5天 |
| T3 服务层 | 2天 |
| T4 接口层 | 1天 |
| T5 兼容层 | 1天 |
| **合计** | **5天** |

## 交付检查清单

| 验收项 | 命令 | 期望 |
|--------|------|------|
| V1 编译 | `env -i HOME=/home/guorongxiao PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -pl engine/cognitive-engine/cognitive-engine-impl -am -DskipTests -q'` | BUILD SUCCESS |
| V2 建表 | `docker exec ecos-postgres psql -U postgres -d sys_man -c "\dt ecos_decision*"` | 6 张表 |
| V3 record | `curl -s -X POST http://localhost:8080/api/v1/cognitive/decision/record -H 'Content-Type: application/json' -d '{"category":"test","scenario":"s","reasoning":"r","outcome":"o","confidence":0.9,"decisionMaker":"admin"}'` | 返回 decisionId |
| V4 chain | `curl -s http://localhost:8080/api/v1/cognitive/decision/{id}/chain` | 返回 chain 数组 |
| V5 provenance | `curl -s "http://localhost:8080/api/v1/cognitive/provenance?entityType=decision&entityId={id}"` | 返回溯源记录 |

## 一句话给 PMO

在 cognitive-engine 建"决策即一等公民节点"：五元组模型 + 六张表 + 五步生命周期 REST API，把散落三处的决策收敛成一套，别动现有推理代码。
