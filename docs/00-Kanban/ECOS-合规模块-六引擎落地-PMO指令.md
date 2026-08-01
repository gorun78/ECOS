# PMO指令：ECOS知识抽取与规则引擎——Builder+Extractor+Reasoner落地

> **来源**：肖总 / 2026-08-01 | **基于KAG架构优化** | **工期**：2.5周 | **铁律见§禁止清单**

---

## §零 背景

**KAG（OpenSPG）架构洞察**：知识加工的核心是Extractor——从文本中抽取SubGraph（实体+关系+规则），向量化和KG写入是同一条Builder管道的两个并行输出。规则只是SubGraph中的一种边类型。

基于此，ECOS落地策略调整为：**先建统一Extractor（知识抽取），再在Extractor之上做规则特化（规则库+合规判定）。**

### 边界

| | 安全中心（已有） | 知识抽取+规则引擎（新增） |
|---|---|---|
| **核心问题** | "能不能做"——权限/脱敏/越权 | "该不该做"——从知识中萃取规则并执行判定 |
| **受众** | IT管理员 | 业务/法务/质量管理人员 |
| **前端** | 安全中心（已有菜单） | **融入知识库工作台**（新增3个子Tab） |
| **共用点** | — | 借用security-engine的认证框架 |

### 六引擎分工（融合KAG）

| 引擎 | 角色 | 关键动作 |
|------|------|----------|
| ai-engine | **Extractor（知识抽取）** | KnowledgeExtractorService——LLM从KB内容抽取SubGraph（实体+关系+规则） |
| kb-engine | **KG写入 + 规则库** | KGWriter（实体/关系→Neo4j）+ ComplianceRule CRUD + 版本 + KG关联 |
| cognitive-engine | **Reasoner（混合检索执行）** | 规则条件评估 + KG反向推理 + 因果链 + 影响分析 + 审计 |
| ontology-engine | 数据建模 | ComplianceRule实体 + RuleVersion + ExtractionSource + SubGraph模型 |
| data-engine | — | 不直接参与（知识库内容接入走现有管道） |
| security-engine | — | 不直接参与；API复用其认证框架 |

**数据流（KAG Builder模式）：**
```
知识库内容（文档/文章/KG实体/结构化数据）
  │
  ▼
Splitter（分段）→ Extractor（ai-engine: LLM抽取SubGraph）
  │
  ├─ 实体+关系 → KGWriter → Neo4j图谱（自动入库）
  │
  ├─ 规则 → 前端人工审核 → 规则库（PG + Neo4j规则边）
  │
  └─ 向量 → Vectorizer → 向量索引（已有，不变）
  │
  ▼
Reasoner（cognitive-engine: 混合检索 = KG查询 + 规则执行 + RAG）
  → 推理链 + 审计
```

---

## §禁止清单

1. **禁止新建Maven模块**
2. **禁止修改现有API路径** — 只增接口不改签名
3. **禁止新建数据库** — 新增表在现有`sys_man`库
4. **禁止引入新依赖**
5. **禁止新增Docker容器** — Neo4j/PostgreSQL已就绪
6. **所有规则判定必须输出推理链** — `ruleId → condition → facts → conclusion → source`
7. **实体/关系抽取可自动入库，规则抽取必须人工审核**

---

## §P0 数据层（Week 1，1.5天）

### T0.1: SubGraph + ComplianceRule实体模型（ontology-engine-api，0.5天）
**改文件**: `engine/ontology-engine/ontology-engine-api/src/main/java/com/chinacreator/gzcm/engine/ontology/model/`
**新增三个文件**:

```java
// 1. 抽取结果：SubGraph（KAG核心模型）
ExtractedSubGraph {
    List<ExtractedEntity> entities;    // 抽取的实体 → 自动写Neo4j
    List<ExtractedRelation> relations; // 抽取的关系 → 自动写Neo4j
    List<ExtractedRule> rules;         // 抽取的规则 → 人工审核
}

ExtractedEntity { name, type, properties(Map), confidence }
ExtractedRelation { sourceEntity, targetEntity, relationType, confidence }
ExtractedRule { name, domain, condition(SpEL预备), action, confidence, sourceExcerpt }

// 2. 合规规则（扩展ExpertRule，同原设计）
ComplianceRule extends ExpertRule { ... }

// 3. 版本+源记录（同原设计）
RuleVersion { ... }
ExtractionSource { ... }
```
**验收**: `mvn compile -pl engine/ontology-engine/ontology-engine-api -am -DskipTests`

### T0.2: 数据库建表（gateway，0.5天）
**改文件**: `gateway/src/main/resources/db/migration/V100__compliance_tables.sql`
**内容**: `sys_compliance_rule` / `sys_rule_version` / `sys_extraction_source`
**验收**: 三张表存在

### T0.3: 规则Mapper+Repository（kb-engine-impl，0.5天）
**改文件**: `ComplianceRuleMapper.java` + `ComplianceRuleMapper.xml`
**验收**: `mvn test`

---

## §P0 提取层：Extractor（Week 1，3天）

### T0.4: 知识抽取服务——核心（ai-engine-impl，2天）
**目标**: KAG Extractor的ECOS实现——从KB内容抽取SubGraph（实体+关系+规则）
**改文件**: `engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/service/KnowledgeExtractorService.java`

**核心设计——一次LLM调用，同时产三类输出**:

```java
/**
 * KAG Extractor的ECOS实现
 * 从知识库Chunk中统一抽取实体、关系、规则
 * 
 * 入库策略：
 * - 实体+关系 → 自动写Neo4j（低风险，可批量）
 * - 规则 → 返回前端人工审核（高风险，需确认）
 */
ExtractedSubGraph extract(String sourceType, String sourceId, ExtractionConfig config) {
    String content = sourceLoader.load(sourceType, sourceId);
    
    // 一次LLM调用，统一抽取
    SubGraph subGraph = config.hasSchema()
        ? llmSchemaConstrainedExtract(content, config.getSchema())  // 有本体约束
        : llmSchemaFreeExtract(content);                             // 自由抽取
    
    // 实体/关系 → 直接写Neo4j
    if (!subGraph.getEntities().isEmpty() || !subGraph.getRelations().isEmpty()) {
        kgWriter.writeBatch(subGraph.getEntities(), subGraph.getRelations());
    }
    
    // 规则 → 返回前端待审核
    return subGraph;
}
```

**LLM Prompt设计**:
```
从以下文本中抽取三类信息：
1. 实体：业务对象（产品/项目/合同/部门/人员/材料/流程...）
2. 关系：实体间的业务关系（属于/关联/依赖/需要/禁止...）
3. 规则：条件性规则（如果...则应当/必须/不得...）
   - condition: SpEL表达式
   - action: 规则结论
   - applicableObjectTypes: 适用对象类型列表

输出JSON格式：
{
  "entities": [{"name":"...","type":"...","properties":{...}}],
  "relations": [{"source":"...","relationType":"...","target":"..."}],
  "rules": [{"name":"...","condition":"...","action":"...","applicableObjectTypes":[...]}]
}
```

**验收**:
```bash
curl -s -X POST http://localhost:8080/api/v1/knowledge/extract \
  -H @/tmp/auth_header.txt \
  -H "Content-Type: application/json" \
  -d '{
    "sourceType":"MANUAL",
    "config":{"domain":"医疗器械","syncMode":true},
    "content":"产品灭菌工艺发生实质性变化的，应当重新提交生物相容性检测报告。检测报告由具备CNAS资质的第三方实验室出具。"
  }' | python3 -c "
import sys,json; d=json.load(sys.stdin)
sg = d['data']['subGraph']
entities = sg.get('entities',[])
relations = sg.get('relations',[])
rules = sg.get('rules',[])
print('EXTRACT:', len(entities), 'entities,', len(relations), 'relations,', len(rules), 'rules')
assert len(entities) > 0 and len(rules) > 0, 'Expected entities AND rules'
# 实体应自动入库
assert len(entities) > 0, 'Should auto-commit entities to Neo4j'
print('PASS: entities auto-committed, rules pending review')"
```

### T0.5: 提取源加载器 + KGWriter扩展（kb-engine-impl，1天）
**改文件**:
- `ExtractionSourceLoader.java`（同原设计，加载KB各种源类型）
- `KGWriterService.java`（扩展：支持批量写Extractor产出的实体+关系节点）
**验收**: 由T0.4间接验证

---

## §P1 规则库层（Week 2，1.5天）→ kb-engine

### T1.1: 规则CRUD + 版本管理（kb-engine-impl，1天）
**同原设计**：ComplianceRuleController CRUD + 版本链 + 状态机
**验收**: 创建→更新(触发版本快照)→查版本历史 PASS

### T1.2: 规则知识图谱 + 抽取实体图（kb-engine-impl，0.5天）
**改文件**: `RuleGraphService.java`
**图谱模型（扩展：加入Extractor产出的实体和关系）**:
```
抽取实体层:
  (ExtractedEntity)-[:EXTRACTED_FROM]->(KnowledgeSource)

规则层:
  (KnowledgeSource)-[:DERIVED]->(ComplianceRule)
  (ComplianceRule)-[:APPLIES_TO]->(ObjectType)
  (ComplianceRule)-[:REFERENCES]->(ComplianceRule)
  (ComplianceRule)-[:SUPERSEDES]->(ComplianceRule)
  (ComplianceRule)-[:CONFLICTS_WITH]->(ComplianceRule)

跨层关联:
  (ExtractedEntity)-[:GOVERNED_BY]->(ComplianceRule)  // 实体受哪个规则约束
```
**验收**: curl图谱含DERIVED和EXTRACTED_FROM边

---

## §P1 推理层：Reasoner（Week 2，2天）→ cognitive-engine

### T1.3: 混合检索推理引擎（cognitive-engine-impl，1.5天）
**目标**: KAG Reasoner的ECOS实现——**混合检索策略：KG查询 + 规则执行 + 向量RAG**
**改文件**: `engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/service/KnowledgeReasonerService.java`

```java
/**
 * KAG Reasoner的ECOS实现
 * 混合检索策略，由Planner根据子问题类型动态选择
 */
ReasonerResult reason(SubQuery sq) {
    return switch (sq.getType()) {
        case KG_QUERY    -> kgExecutor.cypher(sq.getCypher());        // Neo4j直接查
        case RULE_CHECK  -> ruleExecutor.check(sq.getObjectType(),    // 规则判定
                                              sq.getFacts());
        case VECTOR_RAG  -> ragExecutor.retrieve(sq.getSemanticQuery()); // 向量+LLM
        case HYBRID      -> hybridExecute(sq);                        // 混合
    };
}
```
**验收**:
```bash
curl -s -X POST http://localhost:8080/api/v1/knowledge/reason \
  -H @/tmp/auth_header.txt \
  -H "Content-Type: application/json" \
  -d '{"query":"产品MD-001的灭菌工艺变更后，需要补做什么检测？","context":{"objectType":"医疗器械","objectId":"MD-001"}}' \
  | python3 -c "
import sys,json; d=json.load(sys.stdin)
r = d['data']
assert 'answer' in r
assert 'subQueries' in r
assert 'retrievalStrategies' in r
strategies = r['retrievalStrategies']
print('PASS:', len(r['subQueries']), 'sub-queries, strategies:', strategies)"
```

### T1.4: 规则因果链 + 变更影响分析（cognitive-engine-impl，0.5天）
**同原设计**: `RuleCausalService` + `RuleImpactService`
**验收**: 因果链长度>0 + 影响分析含affectedRules

---

## §P2 前端：融入知识库工作台（Week 3，1.5天）

**知识库工作台现有Tab：** 闭环设计 / 元数据同步 / 血缘解析 / 本体对齐 / 向量索引 / RAG模拟
**新增Tab：** 知识抽取 / 规则库 / 合规检查

### T2.1: 知识抽取Tab（FE，0.5天）
**改文件**: `ecos_frontend/src/pages/knowledge/KnowledgeExtractionTab.tsx`（新增）
**功能**:
- 选择源 + 配置提取
- 执行抽取 → 展示三类结果:
  - **实体/关系**：已自动写入Neo4j（绿色标记+数量统计，不可审核）
  - **规则**：待审核列表（确认→入库 / 修改→入库 / 拒绝）
- 抽取历史记录
**路由**: `/knowledge?tab=knowledge-extraction`
**验收**: `curl "http://localhost:3000/knowledge?tab=knowledge-extraction" → 200`

### T2.2: 规则库Tab（FE，0.5天）
**同原设计**: CRUD + 版本管理 + KG关联图 + 状态管理
**路由**: `/knowledge?tab=rules`

### T2.3: 合规检查Tab（FE，0.5天）
**同原设计**: 业务对象→匹配规则→判定→合规报告
**路由**: `/knowledge?tab=compliance-check`

### T2.4: Tab注册（FE，0.25天）
**改文件**: `KnowledgeView.tsx`

### T2.5: 后端聚合Controller（gateway，0.25天）
**改文件**: `gateway/src/main/java/com/chinacreator/gzcm/gateway/controller/KnowledgeExtractionController.java`
**聚合端点**:
```
知识抽取（→ ai-engine）:
  POST   /api/v1/knowledge/extract
  GET    /api/v1/knowledge/extract/sources
  GET    /api/v1/knowledge/extract/history

规则管理（→ kb-engine）:
  GET    /api/v1/rules
  POST   /api/v1/rules
  GET    /api/v1/rules/{id}
  PUT    /api/v1/rules/{id}
  DELETE /api/v1/rules/{id}
  GET    /api/v1/rules/{id}/versions
  GET    /api/v1/rules/{id}/graph

推理（→ cognitive-engine）:
  POST   /api/v1/knowledge/reason      ← 混合检索（KG+规则+RAG）
  POST   /api/v1/rules/check            ← 规则判定特化入口
  GET    /api/v1/rules/causal-chain/{id}
  POST   /api/v1/rules/impact-analysis
  GET    /api/v1/rules/audit-logs
```
**验收**: `/api/v1/knowledge/extract/sources → 200`

---

## §执行顺序

```
Week 1:
  Day 1-2: T0.1(模型) → T0.2(建表) → T0.3(Mapper)  [串行]
  Day 2-5: T0.4(知识抽取) + T0.5(源加载+KGWriter)  [并行]

Week 2:
  Day 1-2: T1.1(规则CRUD+版本) ∥ T1.2(KG建图含抽取实体)  [并行]
  Day 2-4: T1.3(混合推理) + T1.4(因果链+影响)  [依赖T1.1+T1.2]

Week 3:
  Day 1: T2.5(聚合Controller) [依赖P1全部]
  Day 1-2: T2.1 ∥ T2.2 ∥ T2.3 ∥ T2.4 [全部并行]
  Day 2: 端到端验证
```

---

## §端到端验证

```bash
# ── V1: 编译 ──
cd /home/guorongxiao/ECOS/ecos_backend && mvn clean install -DskipTests -q
echo "V1: $?"

TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")
AUTH="Authorization: Bearer $TOKEN"

# V2: 知识抽取（实体+规则）
curl -s -X POST http://localhost:8080/api/v1/knowledge/extract \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"sourceType":"MANUAL","config":{"domain":"医疗器械","syncMode":true},"content":"灭菌工艺变更须提交检测报告，由CNAS实验室出具"}' \
  | python3 -c "
import sys,json; d=json.load(sys.stdin)
sg=d['data']['subGraph']
assert len(sg['entities'])>0 and len(sg['rules'])>0
print('V2 EXTRACT: PASS -',len(sg['entities']),'entities,',len(sg['rules']),'rules')"

# V3: 规则入库→检查
RID=$(curl -s -X POST http://localhost:8080/api/v1/rules \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"name":"灭菌变更检测","domain":"医疗器械","condition":"灭菌工艺变更==true && 安全性数据==false","action":"须提交检测报告","priority":1}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
curl -s -X POST http://localhost:8080/api/v1/rules/check \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"objectType\":\"医疗器械\",\"objectId\":\"MD-001\",\"facts\":{\"灭菌工艺变更\":true,\"安全性数据\":false}}" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); v=d['data']['verdicts'][0]; assert v['passed']==False; print('V3 CHECK: PASS')"

# V4: 混合推理
curl -s -X POST http://localhost:8080/api/v1/knowledge/reason \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"query":"MD-001灭菌工艺变更后需补什么检测？","context":{"objectType":"医疗器械","objectId":"MD-001"}}' \
  | python3 -c "import sys,json; d=json.load(sys.stdin); r=d['data']; assert 'answer' in r; print('V4 REASON: PASS')"

# V5: 前端Tab
for tab in knowledge-extraction rules compliance-check; do
  CODE=$(curl -s "http://localhost:3000/knowledge?tab=$tab" -o /dev/null -w "%{http_code}")
  echo "FE /knowledge?tab=$tab: $CODE"
done
echo "═══ 全部验证通过 ═══"
```

---

## §交付检查清单

| Task | 引擎 | 文件 | 验收 | 状态 |
|------|------|------|------|:----:|
| T0.1 | ontology | SubGraph+ComplianceRule等 | mvn compile | ⬜ |
| T0.2 | gateway | V100__compliance_tables.sql | 3张表 | ⬜ |
| T0.3 | kb | ComplianceRuleMapper | mvn test | ⬜ |
| T0.4 | ai | KnowledgeExtractorService | 抽取实体+关系+规则 | ⬜ |
| T0.5 | kb | ExtractionSourceLoader+KGWriter | 间接验证 | ⬜ |
| T1.1 | kb | ComplianceRuleController | CRUD+版本 | ⬜ |
| T1.2 | kb | RuleGraphService | DERIVED+EXTRACTED_FROM边 | ⬜ |
| T1.3 | cognitive | KnowledgeReasonerService | 混合检索（KG+规则+RAG） | ⬜ |
| T1.4 | cognitive | RuleCausal+RuleImpact | 因果链+影响分析 | ⬜ |
| T2.1 | FE | KnowledgeExtractionTab | FE /knowledge?tab=knowledge-extraction | ⬜ |
| T2.2 | FE | RuleRepositoryTab | FE /knowledge?tab=rules | ⬜ |
| T2.3 | FE | ComplianceCheckTab | FE /knowledge?tab=compliance-check | ⬜ |
| T2.4 | FE | KnowledgeView.tsx | 加载3个新Tab | ⬜ |
| T2.5 | gateway | KnowledgeExtractionController | /api/v1/knowledge/extract/sources | ✅ |

---

## §一句话给PMO

**"KAG架构落地ECOS：Extractor统一抽取实体+关系+规则（ai-engine），实体自动写Neo4j、规则人工审核进规则库（kb-engine），Reasoner做混合检索——KG查询+规则执行+RAG（cognitive-engine）。知识库工作台新增知识抽取/规则库/合规检查3个Tab。不新建模块，14个Task，2.5周闭环。"**
