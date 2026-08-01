# ECOS知识库生命周期规划 v3（融合KAG架构）

> 肖总 / 2026-08-01 | 基于KAG (OpenSPG) 知识增强生成框架

---

## 〇、KAG带来的核心洞察

KAG（Knowledge Augmented Generation，蚂蚁OpenSPG开源）的核心架构是 **Builder + Solver 双层**：

```
KAG架构:
┌─────────────────────────────────────────────┐
│  Builder（知识构建）                          │
│  文档 → Splitter → Extractor → Writer → KG   │
│                   ↘ Vectorizer → 向量索引    │
├─────────────────────────────────────────────┤
│  Solver（知识推理）                           │
│  问题 → Planner → Reasoner → Executor → 答案 │
│         (LF规划)  (混合检索)   (LF执行)       │
└─────────────────────────────────────────────┘
```

**三个关键洞察直接优化ECOS知识库设计：**

| # | KAG洞察 | ECOS对应优化 |
|---|---------|-------------|
| ① | **向量化和KG抽取是同一管道的两个输出**，不是两条独立加工线 | 修正生命周期：Splitter→[Extractor→KG] + [Vectorizer→向量] 并行 |
| ② | **Extractor输出SubGraph（实体+关系+规则）**，规则只是SubGraph的一种边类型 | 规则提取是Extractor的一个子类型，不是独立流程 |
| ③ | **Solver的混合检索 = KG直接查询 + 向量语义检索**，由Planner动态选择 | W层统一为Solver：RAG和合规判定都是Solver的检索策略 |

---

## 一、修正后的知识库全景

```
                         知识库工作台（统一入口）
┌──────────────────────────────────────────────────────────────────┐
│                                                                  │
│  五类资产                                                        │
│  ┌──────────┬──────────┬──────────┬──────────┬──────────┐       │
│  │  元数据   │  血缘关系  │ 本体+对象 │ KG图谱    │  规则库   │       │
│  │  (PG)    │ (Neo4j)  │  (PG)    │ (Neo4j)  │  (PG)    │       │
│  └────┬─────┴────┬─────┴────┬─────┴────┬─────┴────┬─────┘       │
│       │          │          │          │          │              │
│  加工管道 ← KAG Builder模式                                      │
│       │          │          │          │          │              │
│  ┌────▼──────────▼──────────▼──────────▼──────────▼────┐        │
│  │                                                      │        │
│  │  结构化数据              非结构化数据                  │        │
│  │  Mapping → Writer        Reader → Splitter           │        │
│  │                ↕                    ↕                │        │
│  │           Vectorizer        ┌──────┴──────┐         │        │
│  │           (向量索引)        │   Extractor  │         │        │
│  │                             │  LLM实体抽取 │         │        │
│  │                             │  LLM关系抽取 │         │        │
│  │                             │  LLM规则抽取 │←规则提取 │        │
│  │                             └──────┬──────┘         │        │
│  │                                    ↓                 │        │
│  │                             KG Writer → Neo4j        │        │
│  │                                                      │        │
│  └──────────────────────┬───────────────────────────────┘        │
│                         │                                        │
│  推理引擎 ← KAG Solver模式                │                                        │
│  ┌──────────────────────▼───────────────────────────────┐        │
│  │  Planner ──→ Reasoner ──→ Executor ──→ Generator      │        │
│  │  (问题分解)   (混合检索)    (LF执行)     (答案生成)     │        │
│  │               ├─ KG直接查询 (Cypher)                  │        │
│  │               └─ 向量语义检索 (RAG)                   │        │
│  └──────────────────────────────────────────────────────┘        │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

## 二、修正一：加工管道 = KAG Builder模式

**之前的误区**：把"向量化"和"KG构建"当成两条独立加工线。

**KAG的真相**：它们是同一条Builder管道从Splitter分叉的**两个并行输出**。

```
Builder管道（KAG模式 → ECOS落地）:

┌─────────────────────────────────────────────────────────────┐
│                                                             │
│  结构化数据                         非结构化数据              │
│  (数据库表/本体对象)                 (文档/PDF/文章)          │
│       │                                  │                   │
│       ▼                                  ▼                   │
│  Mapping                            Reader                   │
│  (字段→实体映射)                     (PDF/OCR/DOCX→文本)     │
│       │                                  │                   │
│       │                                  ▼                   │
│       │                              Splitter                │
│       │                              (分段: 语义/定长/大纲)   │
│       │                                  │                   │
│       │                    ┌─────────────┴─────────────┐    │
│       │                    ▼                           ▼    │
│       │              Extractor                    Vectorizer │
│       │              ┌─ SchemaFreeExtractor       (Embedding)│
│       │              │   (无Schema: 自由抽取)         │      │
│       │              ├─ SchemaConstraintExtractor    │      │
│       │              │   (有Schema: 约束抽取)        │      │
│       │              └─ RuleExtractor ← NEW          │      │
│       │                  (规则抽取: 条件+结论)        │      │
│       │                    │                           │      │
│       │                    ▼                           ▼      │
│       │              KGWriter                    向量索引     │
│       │              (Neo4j节点+边)              (PG向量表)   │
│       │                    │                           │      │
│       ▼                    ▼                           ▼      │
│  ┌──────────────────────────────────────────────────────┐    │
│  │                  知识库资产                             │    │
│  │  本体对象(PG) + KG图谱(Neo4j) + 向量索引(PG) + 规则(PG) │    │
│  └──────────────────────────────────────────────────────┘    │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**ECOS落地方式**：
- `Mapping` → 已有：`ontology-engine`的本体映射
- `Reader/Splitter` → 已有：`data-engine`的文档解析管道
- `Extractor` → **新增核心**：`ai-engine`的`KnowledgeExtractorService`（合并实体抽取、关系抽取、规则抽取）
- `Vectorizer` → 已有：`kb-engine`的Embedding向量化
- `KGWriter` → 已有：`kb-engine`的KG同步器（扩展到也能写Extractor产出的SubGraph）

---

## 三、修正二：Extractor是统一的知识抽取层

**之前的误区**：规则提取是一个独立的"规则提取服务"。

**KAG的真相**：Extractor输出的是SubGraph（节点+边），实体、关系、规则都是SubGraph的组成部分。

```
Extractor的统一模型（KAG SubGraph → ECOS落地）:

知识库内容（Chunk）
      │
      ▼
  Extractor (LLM驱动)
      │
      ├─ 实体抽取: "灭菌工艺" → KG节点(:Process {name:'灭菌'})
      │             "生物相容性检测报告" → KG节点(:Material {name:'检测报告'})
      │
      ├─ 关系抽取: (:Process)-[:REQUIRES]->(:Material)
      │
      └─ 规则抽取: (:Process)-[:IF_CHANGED]->(:Rule {condition:'...', action:'须提交检测报告'})
                       │
                       ▼
                  ComplianceRule (写PG) + 规则边 (写Neo4j)
```

**ECOS落地方式**：将之前的`RuleExtractionService`升级为`KnowledgeExtractorService`——

```java
/**
 * 统一知识抽取服务 — KAG Extractor的ECOS实现
 * 输入：知识库内容Chunk
 * 输出：SubGraph（实体节点 + 关系边 + 规则边）
 */
KnowledgeExtractionResult extract(String sourceType, String sourceId, ExtractionConfig config) {
    String content = sourceLoader.load(sourceType, sourceId);
    
    // 1. LLM抽取（一次调用，同时抽实体/关系/规则）
    SubGraph subGraph = config.hasSchema()
        ? llmSchemaConstrainedExtract(content, config.getSchema()) // 有本体约束
        : llmSchemaFreeExtract(content, config.getDomain());       // 自由抽取
    
    // 2. 规则子集 → ComplianceRule（需要人工审核的部分）
    List<ExtractedRule> rules = subGraph.getRules();
    
    // 3. 实体+关系子集 → 直接写Neo4j（低风险，可自动入库）
    List<ExtractedEntity> entities = subGraph.getEntities();
    List<ExtractedRelation> relations = subGraph.getRelations();
    kgWriter.write(entities, relations); // 自动入库
    
    // 4. 规则子集 → 返回前端人工审核
    return new KnowledgeExtractionResult(entities, relations, rules);
}
```

**这样规则提取就不是一个孤立的流程，而是知识抽取的一个子产品。**

---

## 四、修正三：Solver统一RAG和合规判定

**之前的误区**：RAG和合规判定是两种独立的应用模式。

**KAG的真相**：Solver的Reasoner使用**混合检索**——KG直接查询（Cypher）+ 向量语义检索（RAG），由Planner根据问题类型动态选择。

```
Solver推理流程（KAG模式 → ECOS落地）:

用户问题: "产品PRJ-001的灭菌工艺变更后，需要补做什么检测？"

      │
      ▼
  Planner (LLM: 问题分解)
      │
      ├─ 子问题1: "PRJ-001的灭菌工艺是否有变更记录？"
      │   → Reasoner选择: KG直接查询 (Cypher)
      │   → MATCH (p:Product {id:'PRJ-001'})-[:HAS_PROCESS]->(proc:Process)
      │     WHERE proc.changeRecord IS NOT NULL
      │
      ├─ 子问题2: "灭菌工艺变更需要什么检测报告？"
      │   → Reasoner选择: 规则执行 (cognitive-engine)
      │   → 匹配规则 → SpEL评估 → 推理链
      │
      └─ 子问题3: "同类产品通常还需要什么检测？"
          → Reasoner选择: 向量语义检索 (RAG)
          → 向量检索 + LLM生成
          
      │
      ▼
  Executor (执行上述子查询，合并结果)
      │
      ▼
  Generator (LLM: 生成最终答案)
  → "PRJ-001的灭菌工艺于2025年3月变更了温度参数。根据《医疗器械生产质量管理规范》
     第X条，属于实质性变更，须提交生物相容性检测报告。同类产品通常还需补充……"
```

**ECOS落地方式**：将`RuleReasonerService`扩展为`KnowledgeSolverService`——

```java
/**
 * 统一知识求解服务 — KAG Solver的ECOS实现
 * 混合检索策略：KG直接查询 + 规则执行 + 向量语义检索
 */
SolverResult solve(String question, SolverContext ctx) {
    // 1. Planner: LLM分解问题
    List<SubQuery> subQueries = planner.decompose(question);
    
    // 2. Reasoner: 为每个子问题选择检索策略
    List<SubResult> subResults = new ArrayList<>();
    for (SubQuery sq : subQueries) {
        RetrievalStrategy strategy = reasoner.selectStrategy(sq);
        switch (strategy) {
            case KG_QUERY:    // 结构化查询 → Neo4j Cypher
                subResults.add(kgExecutor.query(sq));
                break;
            case RULE_CHECK:  // 规则判定 → SpEL + KG反向推理
                subResults.add(ruleExecutor.check(sq));
                break;
            case VECTOR_RAG:  // 语义检索 → 向量 + LLM
                subResults.add(ragExecutor.retrieve(sq));
                break;
        }
    }
    
    // 3. Generator: LLM融合子结果生成最终答案
    return generator.generate(question, subResults);
}
```

---

## 五、修正后的知识库工作台Tab结构

```
知识库工作台（9个Tab，DIKW四层）

D层（2个Tab）— 数据入湖
├── 元数据同步      data-engine自动采集         已经存在
└── 血缘解析        血缘图(Neo4j)                已经存在

I层（1个Tab）— 本体建模
└── 本体对齐        本体vs实际数据对标            已经存在

K层（4个Tab）— 知识加工 ← Builder模式
├── 闭环设计        知识管理PDCA循环              已经存在
├── 向量索引        Vectorizer输出               已经存在
├── 知识抽取 ← NEW  Extractor统一抽取             新建（合并规则提取）
│                   实体/关系→Neo4j自动入库
│                   规则→前端审核→规则库
└── 规则库   ← NEW  CRUD+版本+KG关联             新建

W层（2个Tab）— 知识推理 ← Solver模式
├── 知识问答 ← 重构 混合检索(KG+RAG+规则)        重构RAG模拟Tab
│                   Planner→Reasoner→Executor
└── 合规检查 ← NEW  规则判定特化入口             新建
                    实际上是Solver的规则子模式
```

**关键变化**：
- "规则提取"Tab → 升级为 "知识抽取"Tab（抽取实体/关系/规则，不只是规则）
- "RAG模拟"Tab → 重构为 "知识问答"Tab（混合检索，不只是RAG）
- "合规检查"Tab → 保留，是Solver面向合规场景的特化入口

---

## 六、完整数据流图（融合KAG后）

```
外部数据源                       ECOS平台                              知识库工作台
─────────                      ────────                              ──────────

┌──────────┐                                              ┌──────────────────┐
│ 数据库     │──┐                                          │  元数据同步        │
│ API/文件  │  │   ┌──────────┐                           │  血缘解析          │
│ 文档/标准  │──┼──→│data-engine│──→ PG(元数据+对象)        │  本体对齐          │
│           │  │   │ 管道采集   │                           │  (D+I层：看数据)    │
└──────────┘  │   └─────┬────┘                           └──────────────────┘
              │         │
              │    ┌────▼────┐                           ┌──────────────────┐
              │    │ontology │──→ PG(本体+对象)           │  闭环设计          │
              │    │-engine  │                           │  向量索引          │
              │    └────┬───┘                            │  (K层：管知识)      │
              │         │                                └──────────────────┘
              │    KG同步器                               
              │         │                                ┌──────────────────┐
              │    ┌────▼────┐                           │  知识抽取 ← NEW    │
              │    │  Neo4j  │←──┐                       │  (Extractor统一入口)│
              │    │  图谱    │   │                       │  · 实体自动入库     │
              │    └─────────┘   │                       │  · 关系自动入库     │
              │                  │                       │  · 规则→人工审核    │
              │    ┌──────────┐  │                       └──────┬───────────┘
              │    │kb-engine │  │                              │
              │    │ Vectorizer│──┤                       ┌──────▼───────────┐
              │    │ Extractor│──┤                       │  规则库   ← NEW    │
              │    │ KGWriter │──┘                       │  CRUD+版本+KG关联   │
              │    └────┬─────┘                          └──────────────────┘
              │         │
              │    ┌────▼─────┐                          ┌──────────────────┐
              │    │ai-engine │                          │  知识问答 ← 重构    │
              │    │ Planner  │                          │  Solver: Planner   │
              │    │ Reasoner │                          │  →Reasoner(混合)   │
              │    │ Generator│                          │  →Executor→Gen     │
              │    └────┬─────┘                          └──────────────────┘
              │         │
              │    ┌────▼─────────┐                      ┌──────────────────┐
              │    │cognitive     │                      │  合规检查 ← NEW    │
              │    │-engine       │                      │  Solver特化入口     │
              │    │ 规则执行      │                      │  规则判定+推理链    │
              │    │ 因果推理      │                      └──────────────────┘
              │    └──────────────┘
```

---

## 七、一句话总结

**"KAG教会我们三件事：①向量化和KG抽取是同一条Builder管道的两个并行输出，不是两条独立加工线；②Extractor统一抽取实体/关系/规则，规则只是SubGraph的一种边；③Solver的混合检索（KG查询+RAG+规则执行）由Planner动态选择，RAG和合规判定是同一个推理引擎的不同检索策略。"**
