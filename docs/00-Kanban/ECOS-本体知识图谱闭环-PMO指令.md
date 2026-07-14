# PMO指令: 本体-知识图谱闭环 + Palantir差距补齐

> **来源**: ECOS核心架构——两模型一图谱路线落地 | **日期**: 2026-06-28
> **铁律**: Git提交=唯一绩效。DONE=commit hash+curl 200。不产出解释性文档。
> **前置**: Neo4j Docker已运行(:7474)，CausalController Neo4j Driver已接通

## 背景

ECOS有三个东西各自独立：Ontology定义了实体/属性/关系，Object存了实例数据，CausalController连着Neo4j但没数据。三件事都没串起来。

同时，Ontology属性只支持静态类型（STRING/NUMBER），缺少Palantir Ontology的核心能力——**FUNCTION计算属性**。ObjectQL只能查单个对象，不能沿Ontology Relationship做**跨对象Link遍历**。

## 禁止清单

1. **禁止新建Maven模块或基础设施**——Neo4j已有，Ontology/Object/Agent框架已有
2. **禁止产出分析文档**——只产出可curl验证的代码
3. **禁止拆成微服务**——同步逻辑放在现有Controller的Service层

---

## Track A: 知识图谱同步引擎（3天）

### A1: Ontology → Neo4j Schema映射（1天）

**目标**: 本体的实体/属性/关系定义自动同步为Neo4j的Label + Constraint

**改文件**:
- `databridge-worldmodel/worldmodel-impl/src/main/java/com/chinacreator/gzcm/worldmodel/service/OntologyKgSyncService.java` — 新建
- `databridge-worldmodel/worldmodel-impl/src/main/java/com/chinacreator/gzcm/worldmodel/controller/CausalController.java` — 追加端点

**新增端点**: `POST /api/causal/ontology/sync`
- 读取 `ecos_ontology_entity` + `ecos_ontology_property` + `ecos_ontology_relationship`
- 为每个实体在Neo4j创建Label约束（CREATE CONSTRAINT IF NOT EXISTS）
- 为每个关系创建Relationship Type索引

**验收**:
```bash
TOKEN=$(curl -s -X POST http://localhost:8081/sys-man/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123"}' | jq -r '.data.token')

# 触发同步
curl -s -X POST http://localhost:8081/sys-man/api/causal/ontology/sync \
  -H "Authorization: Bearer $TOKEN" | jq '.data'
# 期望: {"syncedEntities": N, "syncedRelationships": M} (N>0, M>0)

# 验证Neo4j中有Label
curl -s -u neo4j:password http://localhost:7474/db/neo4j/tx/commit \
  -H "Content-Type: application/json" \
  -d '{"statements":[{"statement":"CALL db.labels()"}]}' | jq '.results[0].data | length'
# 期望: >0
```

---

### A2: Object实例 → KG节点同步（1.5天）

**目标**: ObjectController创建/更新实例时，实时同步到Neo4j节点+关系边

**改文件**:
- `databridge-workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/service/ObjectKgSyncService.java` — 新建
- `databridge-workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/controller/ObjectController.java` — create/update方法追加同步调用

**逻辑**:
```
Object POST create → 先写PG → 异步写Neo4j: CREATE (n:EntityLabel {props})
Object PUT update  → 先写PG → 异步写Neo4j: MATCH ... SET n.prop = value
Object POST 关系   → 先写PG → 异步写Neo4j: CREATE (a)-[:REL_TYPE]->(b)
```

**验收**:
```bash
TOKEN=$(curl -s -X POST http://localhost:8081/sys-man/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123"}' | jq -r '.data.token')

# 1. 先确保Ontology有Project实体定义（通过种子数据或手动创建）
# 2. 创建Object实例
curl -s -X POST http://localhost:8081/sys-man/api/v1/ecos/objects/project \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"浙北路桥","budget":830000000,"progress":62,"status":"DELAYED"}' | jq '.data'
# 期望: 200 + 返回创建的Object

# 3. 验证Neo4j中有对应节点
curl -s -u neo4j:password http://localhost:7474/db/neo4j/tx/commit \
  -H "Content-Type: application/json" \
  -d '{"statements":[{"statement":"MATCH (n:Project {name:\"浙北路桥\"}) RETURN n.name, n.budget, n.progress"}]}' | jq '.results[0].data'
# 期望: [{"row": ["浙北路桥", 830000000, 62]}]
```

---

### A3: KG查询工具注册到Agent（0.5天）

**目标**: Agent平台新增 `query_knowledge_graph` 工具，Agent能调CausalController的graph/paths端点做根因分析

**改文件**:
- `databridge-aimod/aimod-impl/src/main/resources/db/migration/V34__ecos_agent_kg_tool.sql` — 工具注册种子数据
- `databridge-aimod/aimod-impl/src/main/java/com/chinacreator/gzcm/aimod/service/DiagnosticToolService.java` — 追加kg_query工具实现

**工具定义**:
- 名称: `query_knowledge_graph`
- 功能: 给定实体名称，返回1-hop子图（节点+边+属性）
- 底层调用: `GET /api/causal/graph?entity=xxx`

**验收**:
```bash
TOKEN=$(curl -s -X POST http://localhost:8081/sys-man/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123"}' | jq -r '.data.token')

# 工具已注册
curl -s http://localhost:8081/sys-man/api/v1/agent/tools?category=knowledge \
  -H "Authorization: Bearer $TOKEN" | jq '.data[] | select(.name=="query_knowledge_graph")'
# 期望: 返回工具定义

# 端到端: Agent调用KG工具
curl -s -X POST http://localhost:8081/sys-man/api/v1/agent/tools/execute \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"tool":"query_knowledge_graph","params":{"entity":"浙北路桥"}}' | jq '.data.nodes | length'
# 期望: >0 (有节点返回)
```

---

## Track B: 本体模型Palantir对齐（4天）

### B1: OntologyProperty增加FUNCTION类型（1.5天）

**目标**: 属性支持计算表达式，不存储值，查询时动态计算

**改文件**:
- `databridge-sysman/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/controller/OntologyController.java` — `POST /properties` 支持 `propertyType=FUNCTION`
- `databridge-sysman/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/service/OntologyFunctionEvaluator.java` — 新建，评估FUNCTION表达式
- `databridge-sysman/sysman-impl/src/main/resources/db/migration/V35__ecos_ontology_function_property.sql` — `ecos_ontology_property` 追加 `function_expression` 列

**FUNCTION属性格式**:
```json
{
  "entityId": "project",
  "code": "profit_margin",
  "name": "利润率",
  "propertyType": "FUNCTION",
  "functionExpression": "({revenue} - {cost}) / {revenue} * 100",
  "functionParams": ["revenue", "cost"],
  "outputType": "NUMBER"
}
```

**验收**:
```bash
TOKEN=$(curl -s -X POST http://localhost:8081/sys-man/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123"}' | jq -r '.data.token')

# 创建FUNCTION属性
curl -s -X POST http://localhost:8081/sys-man/api/v1/ecos/ontologies/entities/project/properties \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"code":"profit_margin","name":"利润率","propertyType":"FUNCTION","functionExpression":"({revenue}-{cost})/{revenue}*100","outputType":"NUMBER"}' | jq '.data'
# 期望: 200 + propertyType=FUNCTION

# 查询Object时自动计算
curl -s http://localhost:8081/sys-man/api/v1/ecos/objects/project/PROJ-001 \
  -H "Authorization: Bearer $TOKEN" | jq '.data.properties.profit_margin'
# 期望: 数值 (如 23.5)
```

---

### B2: ObjectQL跨对象Link遍历（1.5天）

**目标**: `POST /api/query/objectql` 支持沿Ontology Relationship做JOIN查询

**改文件**: `databridge-workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/controller/ObjectQLController.java` — 追加 QueryQL解析

**语法支持**:
```json
{
  "objectType": "project",
  "filters": {"status": "DELAYED"},
  "links": [
    {
      "relationCode": "supplied_by",
      "targetType": "supplier",
      "alias": "s"
    }
  ],
  "fields": ["id", "name", "progress", "s.name", "s.delivery_rate"]
}
```

**产生的SQL**:
```sql
SELECT p.id, p.name, p.progress, s.name, s.delivery_rate
FROM ecos_objects p
LEFT JOIN ecos_object_links l ON p.id = l.source_id AND l.relation_code = 'supplied_by'
LEFT JOIN ecos_objects s ON l.target_id = s.id
WHERE p.object_type = 'project' AND p.properties->>'status' = 'DELAYED'
```

**验收**:
```bash
TOKEN=$(curl -s -X POST http://localhost:8081/sys-man/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123"}' | jq -r '.data.token')

# 跨对象Link查询: 找所有滞后项目及其供应商
curl -s -X POST http://localhost:8081/sys-man/api/query/objectql \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"objectType":"project","filters":{"status":"DELAYED"},"links":[{"relationCode":"supplied_by","targetType":"supplier","alias":"s"}],"fields":["id","name","progress","s.name","s.delivery_rate"]}' | jq '.data.rows | length'
# 期望: >0 (有滞后项目+供应商数据)
# 期望: 每行包含 supplier.name 和 supplier.delivery_rate
```

---

### B3: 前端OntologyDesigner体现FUNCTION属性+Link遍历入口（1天，FE）

**目标**: 前端OntologyDesigner能配置FUNCTION类型属性，ObjectExplorer有"沿关系跳转"按钮

**改文件**:
- `c2eos/src/pages/OntologyDesigner.tsx` — 属性类型下拉加"FUNCTION"选项 + 表达式编辑框
- `c2eos/src/components/ObjectExplorer.tsx` — 对象详情页加"关联对象"Tab，展示关系跳转

**验收**:
```bash
cd /home/guorongxiao/c2eos
npx tsc --noEmit 2>&1 | grep -c "error TS"
# 期望: 0

# 页面可访问
curl -s http://localhost:5173/ecos/ontology/designer | head -5
# 期望: HTML，非404

curl -s http://localhost:5173/ecos/objects | head -5
# 期望: HTML，非404
```

---

## 两条轨道的关联

```
Track A (KG同步)                 Track B (Ontology补齐)
─────────────────────            ─────────────────────
Ontology定义实体                 实体属性支持FUNCTION
        ↓                               ↓
自动建Neo4j Label               FUNCTION属性被ObjectQL计算
        ↓                               ↓
Object CRUD→KG节点              跨Link查询→找到关联供应商
        ↓                               ↓
CausalController查子图          Agent拿到子图+计算指标→诊断
```

两条轨道在**Agent工具**处汇合：`query_knowledge_graph` 拿到图结构，`query_worldmodel_deviation` 拿到目标偏差，Agent Prompt里两条信息一拼——"浙北路桥滞后的根因是华强钢构交货准时率从92%降到67%"。

## 验收方式

肖总亲自检查：
1. `git log --since="2026-06-28"` 每天≥1 commit
2. 逐条执行上述curl命令
3. 浏览器验证: OntologyDesigner里能创建FUNCTION属性 → ObjectExplorer里能看到关联供应商 → Agent诊断结果包含KG数据
