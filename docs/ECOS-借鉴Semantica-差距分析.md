# ECOS 借鉴 Semantica 差距分析（v2）

> 配套文档：`ECOS-借鉴Semantica-完整方案.md`（v2）。本文做逐项差距分析 + 补充梳理可借鉴点。
> **v2 变化**：①新增"KAG 与 Semantica GraphRAG 边界"修正 ②删除 GraphRAG 分块（KAG 已替代）③图分析重定位为 KG 分析增强 ④P1-A 解析落点修正为 kb-engine 就地升级。
> 每项格式：现状（ECOS 实际代码）→ 目标（Semantica 对应能力）→ 差距 → 借鉴方式 → 落点。

---

## 零、KAG 与 Semantica GraphRAG 的边界（核心修正）

**先纠一个定位错误**：ECOS 图谱走的是 **KAG 路线，且已落地**（非计划中），代码铁证：

- `ExtractedSubGraph`（ontology-engine）注释：**"KAG核心模型：一次抽取产出的子图（实体+关系+规则）"**
- `KnowledgeReasonerService`（cognitive-engine）注释：**"KAG Reasoner 的 ECOS 实现 — 混合检索推理引擎"**（KG_QUERY/RULE_CHECK/VECTOR_RAG/HYBRID 四路）
- kb-engine `KnowledgeExtractionService` 的 LLM 抽取（实体+关系+规则一次出）= KAG Extractor

**KAG 是 GraphRAG 的进化，不是并列**（你 KOL 第五篇五代进化：VectorRAG→GraphRAG→HippoRAG→HybridRAG→KAG→Agentic RAG）。Semantica 走的是传统 GraphRAG 路线：

| 能力 | 传统 GraphRAG（Semantica） | KAG（ECOS 已落地） |
|------|---------------------------|-------------------|
| 分块 | entity-aware chunking（先识别实体再按实体边界分块） | 基础分块 + LLM 直接抽取 |
| 抽取 | NER + 关系抽取管线（`semantic_extract/`） | LLM 一次出实体+关系+规则（子图） |
| 全局检索 | 社区检测 + 社区摘要（`community_detector.py`） | SPG 语义图 + 逻辑推理（规则推理） |

**融合结论**：Semantica 的传统 GraphRAG 管线（entity-aware chunking、NER 抽取、社区检测）**不融合**——KAG 已用更先进的方案替代。Semantica 真正值得借鉴的是 **KAG 没覆盖的五块**（KAG 的下游/侧翼）：

1. **决策管理**（KAG 推理出结论 → 决策的记录/先例/合规门/审批）→ P0-A
2. **跨引擎编排**（KAG 是知识构建+推理，不是编排引擎）→ P0-B
3. **全链路溯源**（KAG 有 SPG 来源追溯，但 PROV-O 全链路是通用能力）→ P0-C
4. **冲突检测 + 语义去重**（KAG 有实体对齐，矛盾事实标记是通用数据质量）→ P2
5. **双时态**（数据仓库概念，KAG 没有）→ P2

**一句话**：KAG 是主干（知识构建 + 混合推理），Semantica 借鉴的决策/溯源/编排**不是独立模块，而是融合进 KAG reasoner 的能力扩展**——决策是 KAG 推理的落地产物，溯源是 KAG 来源标注的升级，编排是 KAG 推理链（Builder→Solver→决策）的显式化。冲突去重 + 双时态是 KAG 侧翼。

---

## 一、已确定借鉴点 · 逐项差距分析

### P0-A 决策智能层（融合进 KAG reasoner 的决策落地）

| 维度 | 内容 |
|------|------|
| **现状** | ECOS 有"因果链"（`CausalReasonerServiceImpl`、`V29__ecos_ceo_causal_chain.sql`）、"Agent 治理决策"（`GovernanceDecision.java`）、"诊断 Agent"（`V30`）。三处各自为政，无通用决策模型。 |
| **目标** | Semantica `context/decision_models.py` 五元组：Decision/Policy/Exception/Precedent/ApprovalChain；`decision_methods.py` 五步生命周期：record→link→query→govern→audit。 |
| **差距** | ECOS 决策散落在三处，没有"决策即一等公民节点"的抽象；没有先例检索、策略合规门、审批链。KAG 只推理出结论，不管决策的记录/审计。 |
| **借鉴方式** | 翻译成 Java 通用 Decision 模型，新建 `ecos_decision` 表族。决策落地融合进 KAG reasoner（`KnowledgeReasonerService`）推理出口——推理结果若是决策，触发 record_decision。已有因果链/治理决策作为 category 挂入。 |
| **落点** | cognitive-engine（KnowledgeReasonerService 推理出口） |

### P0-B 跨引擎编排层（融合进 KAG 推理链）

| 维度 | 内容 |
|------|------|
| **现状** | DIKW 五层引擎（data→ontology→kb→cognitive→ai）只有 `PipelineEvent` 事件解耦，无统一编排。data-engine 的 `PipelineExecutionEngine` 只管自己内部 ETL。 |
| **目标** | Semantica `pipeline/`（9 文件 3577 行）通用编排引擎，节点是任意步骤，非仅 ETL。 |
| **差距** | ECOS 没有把五层引擎能力作为"可编排节点"暴露的层；"抽取→建图→推理→决策"无法定义成一条可重试、可追溯、可并行的流程。 |
| **借鉴方式** | **不是独立编排引擎**，而是把 KAG 的 Builder（抽取）→Solver（推理）→决策落地 推理链显式化为可编排、可追溯的 pipeline。复用 data-engine 的 Kahn 拓扑思路。 |
| **落点** | cognitive-engine（KAG 推理链显式化） |

### P0-C 统一溯源层（融合进 KAG reasoner 的来源标注）

| 维度 | 内容 |
|------|------|
| **现状** | ECOS 只有操作审计（security-engine），无数据级/执行级溯源。`PipelineExecutionEngine` 有 `step_run` 记录，但无来源链路。 |
| **目标** | Semantica `provenance/`（6 文件 3770 行）W3C PROV-O 全链路 source-linked 溯源。 |
| **差距** | 事实/决策的来源链路、管线运行的血缘，两样都缺。 |
| **借鉴方式** | **不是独立溯源层**，而是把 KAG reasoner 已有的 `sources` 标注（type/content/confidence）升级为全链路 source-linked，写入 `ecos_provenance_entry`。溯源是 KAG 推理过程的副产品，不重复建。 |
| **落点** | cognitive-engine（KnowledgeReasonerService 的 sources 升级） |

### P1-A 非结构化数据接入 + 解析（KAG 链路第一环，ECOS 空壳）

| 维度 | 内容 |
|------|------|
| **现状** | kb-engine `KnowledgeExtractionService.parseFile()`(L154) 是 `new String(Files.readAllBytes, UTF_8)` 直读字节，**PDF/Word 解析不了**。LLM 抽取已就绪（`callAiExtraction` 走 ai-engine Agent Loop）。data-engine 只有 source_jdbc/csv/rest。 |
| **目标** | Semantica `ingest/file_ingestor.py`（PDFIngestor）+ `parse/`（DocumentParser）。 |
| **差距** | 非结构化数据在 ECOS 无入口：kb-engine 不解析文件本体，data-engine 不接文件。 |
| **借鉴方式** | **修正：kb-engine 就地升级**（Tika 替换 UTF-8 直读），不搬移到 data-engine——kb-engine 已有"上传→解析→抽取"完整链路，搬移是破坏+重复建设。这是"先摸代码再定方案"对方案 v2 的修正。 |
| **落点** | kb-engine（就地升级解析）、抽取已有 |

### P1-B 轻量规则条件评估 + 推理可解释（KAG RULE_CHECK 的条件评估增强）

| 维度 | 内容 |
|------|------|
| **现状** | cognitive-engine `KnowledgeReasonerService.evaluateCondition()` 是字符串 `contains` 匹配（代码注释明写"后续可升级为 SpEL"）。**ECOS 后端没有 Drools**（全仓 `org.kie`/`drools` 引用 = 0）。 |
| **目标** | Semantica `reasoning/explanation_generator.py`（ReasoningStep/ReasoningPath/Justification 三件套）。 |
| **差距** | KAG 的 RULE_CHECK 规则条件无法真正评估（只能字符串匹配）；裁决无法输出"为什么这么判"。 |
| **借鉴方式** | 用 SpEL（Spring 原生）替换 `evaluateCondition()`；借鉴 explanation_generator 做推理路径可解释。**这是 KAG 内增强，不替代 KAG 的混合推理**。 |
| **落点** | cognitive-engine |

### P1-C Pipeline 四件（与 KAG 无关，data-engine ETL 增强）

| 维度 | 内容 |
|------|------|
| **现状** | `PipelineExecutionEngine` 串行执行，失败即 throw，只检测循环依赖，JOIN 空实现、SINK 降级日志。 |
| **目标** | Semantica `pipeline/` 的 FailureHandler+RetryPolicy+Fallback、ParallelismManager、PipelineValidator、PipelineWithProvenance。 |
| **差距** | 缺重试/并行/验证/溯源四件 + JOIN/SINK 空实现。 |
| **借鉴方式** | 直接增强现有执行引擎，不重构。溯源接入 P0-C。 |
| **落点** | data-engine |

### P2 冲突检测 + 语义去重 + 双时态（KAG 的侧翼）

| 项 | 现状 | 目标 | 借鉴方式 | 落点 |
|----|------|------|---------|------|
| 冲突检测 | DQ 只能 CRUD 不能执行 | `conflicts/`（detector/resolver/source_tracker） | 做成 DQ 升级后的第一个可执行规则类型，先"冲突标记+人工仲裁" | data-engine/DQ |
| 语义去重 | 无 | `deduplication/`（duplicate_detector/entity_merger） | 第二期，与 KAG 实体对齐协调 | kb-engine |
| 双时态 | PG 无 | `kg/temporal_model.py` BiTemporalFact | ontology Functions 内建字段预留，存量表不动 | ontology-engine |

---

## 二、补充梳理 · 之前未纳入的可借鉴点（修正后）

### 补充1：KG 分析增强（centrality / link_prediction / path_finder）

| 维度 | 内容 |
|------|------|
| **现状** | ECOS kb-engine 有 KG（Neo4j），但无图分析。`CausalReasonerServiceImpl` 只做 BFS 路径遍历。 |
| **目标** | Semantica `kg/` 的 `centrality_calculator.py`、`link_predictor.py`、`path_finder.py`。**不抄 `community_detector.py`**——社区检测是传统 GraphRAG 的全局检索，KAG 已用逻辑推理替代。 |
| **价值** | 中心性=找关键指标节点（谁动影响全局）；链路预测=发现隐含因果边。是 KAG 逻辑推理的**补充分析**，不替代。 |
| **借鉴方式** | Neo4j GDS 或自研轻量实现，在 kb-engine 加中心性/链路预测，喂给 cognitive-engine 因果链。 |
| **优先级** | **P2**（KAG 逻辑推理已能跑基本检索，图分析是 KG 纵深增强） |

### 补充2（已删除）：GraphRAG 分块策略

~~entity-aware chunking~~ **删除**。理由：entity-aware 分块是传统 GraphRAG 的（先识别实体再按实体边界分块），因为它的抽取是 NER 管线。**KAG 用 LLM 直接抽取**（一次出实体+关系+规则），分块只需基础分块，LLM 抽取自然理解语义。所以 entity-aware chunking 对 KAG 是多余，已移入"明确不抄"。

### 补充3：Agent 上下文图化（ContextGraph / agent_memory）

| 维度 | 内容 |
|------|------|
| **现状** | ECOS ai-engine 有 `AgentSessionService`、`MemoryExtractor`、OAG（`KnowledgeRetrieverNode`），但 Agent 上下文/记忆是散落的 session/memory 记录，不是图。 |
| **目标** | Semantica `context/agent_context.py`、`agent_memory.py`、`context_graph.py`（"把 Agent 知道的一切做成结构化可查询图"）。 |
| **价值** | 与 P0-A 决策智能层是同一套抽象（Agent 状态/决策都是一等公民图节点）。 |
| **借鉴方式** | 不单列，合并进 P0-A——决策层的 Decision 节点天然覆盖 Agent 决策，Agent 上下文作为决策的 context 字段挂入。 |
| **优先级** | 合并，不新增档 |

---

## 三、明确不抄（补充项）

| 项 | 理由 |
|----|------|
| **entity-aware chunking、社区检测、传统 NER 抽取** | **KAG 已用 LLM 抽取 + 逻辑推理替代**（见§零 KAG 边界） |
| 多后端存储抽象（vector_store/graph_store/triplet_store 20+ 后端） | ECOS 已定 PG+Neo4j+Doris 三版，多后端是过度设计 |
| export 19 种格式（RDF/OWL/Parquet/Cypher/JSON-LD...） | 政企场景用不到，低优先级，可选 |
| visualization（KG/Ontology/Embedding/Temporal 可视化） | ECOS 前端已有 cytoscape/echarts/ag-grid |
| change_management（ontology_version_manager） | ECOS ontology-engine 已有提案+版本机制，不弱于 Semantica |
| normalize 细粒度归一（EntityNormalizer/DateNormalizer） | ECOS 有 `DataCleansingStep`，实体别名归一可后续补 |
| 推理引擎 Rete/Datalog/SPARQL、Python NLP 全家桶、GPU 调度 | 已在方案 v2 不抄清单 |

---

## 四、最终借鉴优先级总表

| 档 | 借鉴点 | 落点 | 工程量 |
|----|--------|------|:---:|
| **P0** | 决策智能层 + 跨引擎编排层 + 统一溯源层（绑定） | cognitive-engine | 两周 |
| **P1** | 非结构化解析（KAG链路第一环）、SpEL规则评估（KAG内增强）、Pipeline四件 | kb-engine + cognitive-engine + data-engine | 两周 |
| **P2** | 冲突检测+去重、双时态、KG分析（中心性/链路预测） | data-engine + kb-engine + ontology-engine | 预留 |

**一句话**：KAG 是主干（知识构建+混合推理，已落地），Semantica 是侧翼补充（决策/溯源/编排/冲突/双时态）。P0 定架构，P1 补 KAG 链路短板（非结构化解析 + 规则评估），P2 升能力（冲突去重 + 双时态 + KG 分析）。**GraphRAG 分块和社区检测不抄——KAG 已经替代了。**
