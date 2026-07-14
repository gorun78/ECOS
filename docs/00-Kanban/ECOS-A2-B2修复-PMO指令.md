# PMO指令: A2 Neo4j同步修复 + B2 ObjectQL修复

> **来源**: 肖总验证报告 | **日期**: 2026-07-01
> **铁律**: Git提交=唯一绩效。DONE=commit hash+curl PASS。不产出解释性文档。

## 背景

6/28 Sprint 10两轨并行，7/13项通过。两个bug堵住：

- **A2**: ObjectKgSyncService(151行) + 44个Object在PG → Neo4j 0节点。种子数据绕过API不触发同步。
- **B2**: ObjectQLParser生成的SQL——`FROM Project`而非`FROM ecos_object_data`，Link遍历引用不存在的`ecos_object_links`表。

---

## 禁止清单

1. 禁止改ObjectKgSyncService的异步写入逻辑——代码已验证正确，只缺触发
2. 禁止新增Maven模块
3. 禁止产出设计文档

---

## A2修复: 批量回填 + 验证异步链

### A2.1: 新增全量回填端点（1h）

**改文件**: `databridge-worldmodel/worldmodel-impl/src/main/java/com/chinacreator/gzcm/worldmodel/controller/CausalController.java`

**新增端点**: `POST /api/causal/ontology/resync`

**逻辑**:
```java
@PostMapping("/ontology/resync")
public ApiResponse<Map<String, Object>> resyncAllObjects() {
    // 1. 全量查 ecos_object_data
    List<Map<String, Object>> allObjects = jdbc.queryForList(
        "SELECT id, entity_code, object_data FROM ecos_object_data");
    
    // 2. 逐条调 ObjectKgSyncService.syncObjectToNeo4j(entityCode, id, properties, "CREATE")
    int count = 0;
    for (Map<String, Object> row : allObjects) {
        String entityCode = (String) row.get("entity_code");
        String id = (String) row.get("id");
        // object_data是PG jsonb，jdbc.queryForList反序列化后是PGobject或Map
        Map<String, Object> props = extractProperties(row.get("object_data"));
        kgSyncService.syncObjectToNeo4j(entityCode, id, props, "CREATE");
        count++;
    }
    
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("total", allObjects.size());
    result.put("synced", count);
    result.put("message", "全量回填已提交（异步），等待30秒后查Neo4j验证");
    return ApiResponse.success(result);
}
```

**注意**: `object_data` 是PG `jsonb`类型，`jdbc.queryForList`返回后可能是`PGobject`。需要判断类型后转Map。

**验收**:
```bash
# 登录
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])" > /tmp/ecos_token.txt
echo "Authorization: Bearer $(cat /tmp/ecos_token.txt)" > /tmp/auth_header.txt

# 触发回填
curl -s -X POST http://localhost:8080/api/causal/ontology/resync \
  -H @/tmp/auth_header.txt | python3 -c "import sys,json;d=json.load(sys.stdin);print(d)" 
# 期望: {"total":44,"synced":44}

# 等待30秒后验证Neo4j节点数
curl -s -u neo4j:neo4j123 http://localhost:7474/db/neo4j/tx/commit \
  -H "Content-Type: application/json" \
  -d '{"statements":[{"statement":"MATCH (n) RETURN count(n) as total"}]}' \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print('Neo4j nodes:', d['results'][0]['data'][0]['row'][0])"
# 期望: ≥44
```

---

### A2.2: ObjectController create验证（0.5h）

新对象创建后，验证异步真能写入Neo4j。

**改文件**: 无（代码已有syncObjectToNeo4j调用），仅curl验证

**验收**:
```bash
# 通过API创建一个新对象
curl -s -X POST http://localhost:8080/api/v1/ecos/objects/Project \
  -H @/tmp/auth_header.txt \
  -H "Content-Type: application/json" \
  -d '{"name":"验证测试项目","budget":1000000,"progress":50,"status":"planning"}' \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print('Created:', d.get('data',{}).get('id','FAIL'))"

# 等待5秒后查Neo4j
curl -s -u neo4j:neo4j123 http://localhost:7474/db/neo4j/tx/commit \
  -H "Content-Type: application/json" \
  -d '{"statements":[{"statement":"MATCH (n:Project {name:\"验证测试项目\"}) RETURN n.name, n.budget, n.progress"}]}' \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print(d['results'][0]['data'])"
# 期望: [{"row": ["验证测试项目", 1000000, 50]}]
```

**如果A2.2失败** → 说明`CompletableFuture.runAsync()`的默认ForkJoinPool未初始化或被SecurityManager阻止。修复方案：注入`@Async`线程池或改用`new Thread()`。

---

## B2修复: ObjectQL表名映射

### B2.1: ObjectQLParser修复（2h）

**改文件**: `databridge-workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/controller/ObjectQLParser.java`

**修改点1** — `parse()`方法L154，改基础表名为 `ecos_object_data`，自动注入entity_code过滤:

```java
// 原: sql.append(" FROM ").append(entity);
// 改:
sql.append(" FROM ecos_object_data AS ").append(entity);
// entity_code 过滤推到 where 里:
List<Object> prependParams = new ArrayList<>();
prependParams.add(entity);
String entityFilter = entity + ".entity_code = ?";
```

然后修改 `buildWhere()` 返回时，如果已有条件则前面加 `AND entityFilter`，否则 `WHERE entityFilter`。

**修改点2** — `parseLinks()`中 format B（viaLinksTable）需要 `ecos_object_links` 表。该表在B2.2创建。确保JOIN语法正确:

```sql
-- 生成的SQL示例（Link遍历: Project→Supplier via ecos_object_links）
SELECT p.id, p.name, s.name AS supplier_name
FROM ecos_object_data AS p
LEFT JOIN ecos_object_links AS __l0 ON p.id = __l0.source_id AND __l0.relation_code = ?
LEFT JOIN ecos_object_data AS s ON __l0.target_id = s.id
WHERE p.entity_code = 'Project' AND p.object_data->>'status' = 'DELAYED'
```

**修改点3** — L148-153, fields引用需加表别名前缀。当有links时,单表字段自动加entity别名:

```java
// 如果 fields 不空且有 links
if (fields != null && !fields.isEmpty()) {
    List<String> qualified = new ArrayList<>();
    for (String f : fields) {
        if (!f.contains(".")) {
            qualified.add(entity + "." + f);  // 自动加表别名
        } else {
            qualified.add(f);
        }
    }
    sql.append(String.join(", ", qualified));
}
```

**验收**:
```bash
# 基础查询
curl -s -X POST http://localhost:8080/api/query/objectql \
  -H @/tmp/auth_header.txt \
  -H "Content-Type: application/json" \
  -d '{"query":"{\"entity\":\"Project\",\"filters\":[{\"field\":\"status\",\"op\":\"=\",\"value\":\"in_progress\"}],\"fields\":[\"id\",\"name\",\"status\"]}"}' \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print('rows:', len(d.get('data',[])))"
# 期望: rows: >0 (有项目数据)
```

```bash
# Link遍历 — 通过ecos_object_links查Project→Supplier
curl -s -X POST http://localhost:8080/api/query/objectql \
  -H @/tmp/auth_header.txt \
  -H "Content-Type: application/json" \
  -d '{"query":"{\"entity\":\"Project\",\"filters\":[{\"field\":\"status\",\"op\":\"=\",\"value\":\"in_progress\"}],\"links\":[{\"relationCode\":\"supplied_by\",\"targetType\":\"Supplier\",\"alias\":\"s\"}],\"fields\":[\"id\",\"name\",\"s.name\"]}"}' \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print('rows:', len(d.get('data',[])))"
# 期望: rows: >0
```

---

### B2.2: 创建ecos_object_links表 + 种子关系数据（0.5h）

**改文件**: `databridge-sysman/sysman-impl/src/main/resources/db/migration/V36__ecos_object_links.sql`

```sql
CREATE TABLE IF NOT EXISTS ecos_object_links (
    id VARCHAR(64) PRIMARY KEY,
    source_id VARCHAR(64) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    relation_code VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_obj_links_source ON ecos_object_links(source_id);
CREATE INDEX IF NOT EXISTS idx_obj_links_target ON ecos_object_links(target_id);
CREATE INDEX IF NOT EXISTS idx_obj_links_rel ON ecos_object_links(relation_code);

-- 种子数据：浙北路桥 → 华强钢构 (supplied_by关系)
-- Project: prj-001 = 浙北路桥, Supplier: 需查找华强钢构ID
INSERT INTO ecos_object_links (id, source_id, target_id, relation_code)
SELECT 'link_001', 'prj-001', id, 'supplied_by'
FROM ecos_object_data 
WHERE entity_code = 'Supplier' AND object_data->>'name' = '华强钢构'
AND NOT EXISTS (SELECT 1 FROM ecos_object_links WHERE id = 'link_001');
```

**验收**:
```bash
docker exec ecos-postgres psql -U postgres -d sys_man \
  -c "SELECT * FROM ecos_object_links"
# 期望: 至少1条记录
```

---

## 交付标准

两轨全部curl PASS才算DONE：
1. A2.1: resync → Neo4j节点数≥44 ✅
2. A2.2: 新Object创建后Neo4j有节点 ✅
3. B2.1: 基础查询返回行数>0 ✅
4. B2.1: Link遍历返回行数>0 ✅
5. B2.2: ecos_object_links表存在且有数据 ✅
