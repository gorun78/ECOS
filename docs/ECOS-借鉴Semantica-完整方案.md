# ECOS 借鉴 Semantica 完整方案（v3 · 含 KAG 边界修正）

> v3 变化：①新增 KAG 与 Semantica GraphRAG 边界修正（核心）②P1-A 解析落点改为 kb-engine 就地升级 ③P2 补 KG 分析，明确不抄 GraphRAG 分块/社区检测。
> 核心结论：KAG 是主干（已落地），Semantica 是侧翼补充（决策/溯源/编排/冲突/双时态）。

---

## 零、KAG 与 Semantica GraphRAG 的边界（核心修正）

ECOS 图谱走的是 **KAG 路线，且已落地**（`ExtractedSubGraph`、`KnowledgeReasonerService` 均标注 KAG）。**KAG 是 GraphRAG 的进化不是并列**，Semantica 的传统 GraphRAG 管线（entity-aware chunking、NER 抽取、社区检测）已由 KAG 的"LLM 抽取 + 逻辑推理"替代，**不融合**。

Semantica 真正值得借鉴的是 **KAG 没覆盖的五块**（下游/侧翼）：

| KAG 没覆盖的能力 | 对应档 |
|----------------|-------|
| 决策管理（记录/先例/合规门/审批） | P0-A |
| 跨引擎编排 | P0-B |
| 全链路溯源 | P0-C |
| 冲突检测 + 语义去重 | P2 |
| 双时态 | P2 |

详见差距分析 `ECOS-借鉴Semantica-差距分析.md` §零。

---

## 结论先行

六引擎的真相是**"头尾强、腰细、链路断"**——ai-engine(80) 和 ontology-engine(42) 最全，cognitive-engine(12) 最细是"腰"，DIKW 主线的两个断点在：

1. **数据(K)入口断了**：非结构化数据（PDF/Word）在 ECOS 里没有入口——data-engine 只接结构化（JDBC/CSV/REST），kb-engine 的抽取链路声明了"文档上传→解析"但解析是空壳（只从 DB 取已入库文本，不解析文件本体）。
2. **认知(C)能力虚了**：cognitive-engine 的规则条件评估是字符串 `contains` 匹配，不是真规则引擎；因果链靠 KG 遍历 + LLM 补，缺确定性推理兜底。

主线（决策智能 + 编排 + 溯源）不变，但 P1 要新增两项：**非结构化接入 + 轻量规则评估引擎**。

---

## 一、六引擎能力复盘（点3）

### 规模盘点

| 引擎 | Java 文件 | Ctrl | Svc | Repo | 一句话定性 |
|------|:---:|:---:|:---:|:---:|------|
| ai-engine | 80 | 30 | 16 | 4 | Agent/Loop/Tool/Session/Delegation/AIP/Guardrails，**最全** |
| ontology-engine | 42 | 27 | 7 | 0 | 本体建模/版本/实体/Function 沙箱，**完整** |
| data-engine | 36 | 17 | 12 | 0 | 数据源/管道/血缘/质量/调度 |
| kb-engine | 32 | 12 | 11 | 6 | KG/RAG/规则/抽取(LLM)/SubGraph |
| security-engine | 20 | 13 | 6 | 0 | RBAC+ABAC+ClearanceInterceptor，薄但职责单一 |
| **cognitive-engine** | **12** | 4 | 7 | 0 | 因果链/混合推理/场景模拟，**最细** |

### 逐个引擎的真实现状（不是看接口，是看代码）

**cognitive-engine（最薄，重点）**
- `CausalReasonerServiceImpl`(726 行)：KG 路径遍历(CAUSES/AFFECTS/CORRELATES) + LLM 补充，构建 ≥3 层因果链。**真实现**，但深度依赖 KG + ai-engine Agent Loop，KG 空时全靠 LLM。
- `KnowledgeReasonerService`(484 行)：KAG Reasoner 落地，混合检索(KG_QUERY/RULE_CHECK/VECTOR_RAG/HYBRID 并行融合+超时降级)。**这个较完整**。
- `RuleCausalService`(157 行)：通过 `sys_compliance_rule` 的 description 字段**文本匹配**建因果链。弱。
- **核心短板**：`evaluateCondition()` 是字符串 `contains` 匹配（代码注释明写"后续可升级为 SpEL"）。没有真正的规则条件评估。

**data-engine**
- `PipelineExecutionEngine`(420 行)：source(JDBC/CSV/REST) → transform(cleansing/typeconversion/mapping/calculator/validation) → aggregate → join → sink。
- **三处空实现**：`executeJoinStep` 是 pass-through（"多源 JOIN 需 DAG 调度"）、`executeSinkStep` 降级为日志（"降级为日志记录"）、**无任何非结构化文件接入**（无 PDF/Word/网页 connector）。

**kb-engine**
- `KnowledgeExtractionService`：文档上传→解析→LLM抽取→审核→入库全链路，LLM 抽取走 ai-engine Agent Loop（实体+关系+规则一次出）。
- **缺口在"解析"**：`ExtractionSourceLoader` 只支持 KB_ARTICLE/DOCUMENT/MANUAL 三种源，从 DB 取已入库文本，**不解析 PDF/Word 文件本体**。文件里的内容进不来。

**ontology-engine / ai-engine / security-engine**：相对完整，非本轮重点。

### 差距结论（一句话）

**不是六引擎平均都弱，是"头尾强、腰细、两个断点"。** 断点一在数据入口（非结构化进不来），断点二在认知层（规则评估是假的）。

---

## 二、两个纠错（对我上一轮的修正）

### 纠错1：非结构化管线不是"广度不用抄"，是"入口缺失"

上一轮说"摄取/解析/导出是广度不是深度，别抄"——**错了一半**。Semantica 的 28 个 ingestor（file/db/web/api/email/feed/gdrive/...）确实不必全抄，但它暴露了一个 ECOS 真缺的东西：**文件接入 + 解析**。

- Semantica `file_ingestor` 有 PDFIngestor，支持 PDF/DOCX/PPTX/HTML/TXT/CSV/Excel/XML。
- ECOS data-engine 只有 JDBC/CSV/REST，kb-engine 的"解析"是空壳。
- **结论：抄"文件接入+解析"这个能力，不抄 28 个 ingestor 的广度。**

### 纠错2：ECOS 没有 Drools，问题不是"重"是"没有"

上一轮说"ECOS 已有 Drools，推理引擎别抄"——**事实错了**。全仓 `org.kie`/`drools` 引用 = 0，pom 无依赖。你记的 Drools 是 `/mnt/d/javaprojects/incubator-kie-drools` + `masterrule` 两个独立项目。

ECOS 现在的"规则引擎"是字符串匹配，缺的是一个**轻量规则条件评估**。所以正确的落点是：

- **不引入 Drools**（KIE 全家桶，数百 MB 依赖，DSL 复杂，政企私有化重负担）。
- **也不照抄 Semantica 的 Rete**（`rete_engine.py` 12867 行 alpha/beta/terminal 节点，对 ECOS 仍偏重，而且 ECOS 用 Neo4j Cypher 做图查询，不需要自研推理网络）。
- **用 SpEL**（Spring Expression Language，ECOS 是 Spring Boot 3.2.2 原生自带，零新增依赖）做规则条件评估——这正是 ECOS 代码注释里已经预留的方向。
- **借鉴 Semantica 的 `explanation_generator`**（ReasoningStep/ReasoningPath/Justification 三件套）：让规则裁决输出"为什么这么判"的逐步推理路径 + 证据追踪。这是 Semantica 比 Drools 真正值得抄的地方。

---

## 三、调整后的完整方案

### P0（主线不变，绑定交付，约两周）

#### P0-A 决策智能层（融合进 KAG reasoner 的决策落地）

五元组（Decision/Policy/Exception/Precedent/ApprovalChain）+ 五步生命周期（record → link → query → govern → audit）。表族 `ecos_decision` 系列。**决策落地在 KAG reasoner（`KnowledgeReasonerService`）推理出口**——推理结果若是决策，触发 record_decision。已有 V29 CEO 因果链、GovernanceDecision 作为 category 挂入。

#### P0-B 跨引擎编排层（融合进 KAG 推理链）

**不是独立编排引擎**，而是把 KAG 的 Builder（抽取）→Solver（推理）→决策落地 推理链显式化为可编排、可追溯的 pipeline。复用 Kahn 拓扑思路。

#### P0-C 统一溯源层（融合进 KAG reasoner 的来源标注）

**不是独立溯源层**，而是把 KAG reasoner 的 `sources` 标注升级为全链路 source-linked，写入 `ecos_provenance_entry`。数据级+执行级一套溯源。

### P1（补齐链路断点，本轮重点，约两周，可与 P0 并行）

#### P1-A 非结构化文档解析（→ kb-engine 就地升级，KAG 链路第一环）

- **修正**：kb-engine `KnowledgeExtractionService.parseFile()` 现在是 UTF-8 直读字节，PDF/Word 解析不了。用 Tika 就地升级，不搬移到 data-engine——kb-engine 已有"上传→解析→抽取"完整链路（即 KAG 链路），搬移是破坏+重复建设。
- `executeJoinStep`/`executeSinkStep` 两个空实现补齐归 P1-C。

#### P1-B 轻量规则条件评估引擎 + 推理可解释（→ cognitive-engine，点2）

- 用 SpEL 替换 `evaluateCondition()` 的字符串匹配，让规则条件真正可评估。
- 借鉴 Semantica `explanation_generator`：把 `KnowledgeReasonerService` 现有的 reasoningChain（雏形）升级成结构化 ReasoningStep/ReasoningPath，含规则引用、输入事实、输出结论、置信度、证据追踪。
- 收益：政务合规、企业经营问责的"为什么这么判"终于能回答。

#### P1-C Pipeline 四件（→ data-engine）

FailureHandler+RetryPolicy+Fallback、ParallelismManager、PipelineValidator（结构/依赖/性能三验）、PipelineProvenance（接入 P0-C 统一溯源）。

### P2（预留）

- **冲突检测 + 语义去重**（→ DQ 规则引擎升级，第一个可执行规则类型，先"冲突标记+人工仲裁"）。
- **双时态**（→ ontology Functions 内建字段，valid_from/valid_until + recorded_at/superseded_at，存量表不动）。
- **KG 分析增强**（中心性/链路预测，→ kb-engine，配合 KAG 的 Neo4j 存储，喂给 cognitive-engine 因果链）。**不抄社区检测**——那是传统 GraphRAG 的全局检索，KAG 已用逻辑推理替代。

### 不抄清单（修正后）

| 项 | 理由 |
|----|------|
| **entity-aware chunking、社区检测、传统 NER 抽取** | **KAG 已用 LLM 抽取 + 逻辑推理替代** |
| Drools / KIE | 重，ECOS 没用，别引入，SpEL 够 |
| Semantica Rete 推理网络 | 12867 行自研，ECOS 用 Cypher，无需自研 |
| Datalog / SPARQL | ECOS 已有 Neo4j Cypher |
| 28 个 ingestor 全量 | 抄文件解析即可，不必抄广度 |
| Python NLP 全家桶 | 政企私有化重负担，LLM 抽取已替代 |
| GPU/CPU/内存调度 | 政企纯负担 |

---

## 四、落地顺序与里程碑

```
M1（两周）：P0 三件绑定（决策层 + 编排层 + 溯源层）
M2（两周）：P1 三件（非结构化接入 + SpEL规则评估/可解释 + Pipeline四件）
M3（预留）：P2（冲突去重 + 双时态 + KG 分析）
```

每个里程碑走标准流程：差距分析 → 审阅确认 → 完善计划 → PMO 指令 → 执行 → 验收。
