# PMO-22: kb-engine增强 — 抽取管道+实体链接+向量索引

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> 来源: 肖国荣 | 日期: 2026-08-07 | 优先级: 🟡 P1
> **引擎**: kb-engine:18086 | **依赖**: Neo4j(enterprise), MinerU, pgvector
> **工期**: 3天 | **协同**: ECOS-ARCH + ECOS-BE

---

## §背景

kb-engine有9个Controller+9个Service，CRUD完备。Phase 3需要端到端验证知识抽取管道、增强实体链接、确认向量索引可用性。

**引擎职责**：知识库生命周期——存储/检索/抽取/同步。**不执行规则判定（cognitive的事），不直接调LLM（ai的事）**。

---

## §禁止清单

1. ❌ 不执行规则判定 — cognitive-engine的事（铁律0.3）
2. ❌ 不直接调LLM — ai-engine的事（铁律0.3）
3. ❌ Neo4j仅在enterprise/flagship启用
4. ❌ 不跨Phase预创建文件（铁律5.1 #1）

---

## §Task

### T1: 知识抽取管道端到端验证（1.5天）

**涉及文件**:
- `engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/service/KnowledgeExtractionService`（验证+补全）
- 相关Controller端点确认

**现状**: 抽取Service存在（推测通过`KnowledgeExtractionTab.tsx`前端调用），但端到端流程未经完整验证

**验证+补全项**：

1. **上传→解析→抽取→审核→入库**全链路走通：
   - PDF/Word上传 → MinerU解析为Markdown文本
   - 调ai-engine的LLM接口抽取实体+关系+规则（**kb不直接调LLM，走ai-engine API**）
   - 抽取结果临时存`extraction_drafts`表（status=PENDING_REVIEW）
   - 人工审核API：`POST /api/v1/kb/extraction/{id}/approve` → 实体写Neo4j + 规则写compliance_rules
   - 驳回API：`POST /api/v1/kb/extraction/{id}/reject` → status=REJECTED

2. **超时+重试**: 解析超时120s，LLM抽取超时60s，失败自动重试1次

3. **状态追踪**: 每个抽取任务状态机 `UPLOADED→PARSING→EXTRACTING→PENDING_REVIEW→APPROVED/REJECTED`

**curl验收**:
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' \
  | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)

# 模拟上传（用curl multipart）
echo "测试文档内容" > /tmp/test_doc.txt
curl -s -H "Authorization: Bearer $TOKEN" \
  -X POST http://localhost:8080/api/v1/kb/extraction/upload \
  -F "file=@/tmp/test_doc.txt" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print('PASS: 上传成功' if d.get('success') else 'FAIL')"

# 查询抽取任务列表
curl -s -H "Authorization: Bearer $TOKEN" \
  -X GET "http://localhost:8080/api/v1/kb/extraction/tasks?page=1&pageSize=10" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print('PASS' if d.get('success')!=None else 'FAIL')"
```

---

### T2: 实体链接增强（1.5天）

**新建文件**: `engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/service/EntityLinkerService.java`

**目标**: 抽取的实体自动关联本体对象类型

**实现要求**：
1. 输入：实体名称（如"应收账款"）+ 实体类型(从LLM抽取结果)
2. 通过ontology-engine API查询本体对象类型列表 → 名称相似度匹配(编辑距离+语义向量余弦)
3. 映射结果存Neo4j关系 `(:Entity)-[:MAPS_TO {confidence:0.85}]->(:OntologyType)`
4. **API端点**: `POST /api/v1/kb/entity/link` — 手动触发实体链接
5. **自动触发**: T1知识抽取审核通过后自动执行实体链接

**映射示例**:
```
"应收账款" → 本体"财务/资产/应收" (confidence:0.92)
"差旅费报销" → 本体"财务/费用/差旅" (confidence:0.87)
```

**curl验收**:
```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  -X POST http://localhost:8080/api/v1/kb/entity/link \
  -H 'Content-Type: application/json' \
  -d '{"entityName":"应收账款","entityType":"财务科目"}' \
  | python3 -c "import sys,json; d=json.load(sys.stdin); \
     mapping=d.get('data',{}).get('mapping',{}); \
     print(f'PASS: {mapping.get(\"ontologyPath\",\"?\")}' if mapping else 'FAIL')"
# 期望: PASS: 财务/资产/应收 (或"未匹配"也算PASS——链路通即可)
```

---

### T3: 向量索引+Neo4j连接池验证（1天）

**涉及文件**:
- `engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/service/KnowledgeRetrievalServiceImpl.java`
- `engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/engine/kb/service/KGWriterService.java`

**验证+补全项**：

1. **pgvector**确认可用：
   - 检查`pg_vector`扩展已安装 → `SELECT * FROM pg_extension WHERE extname='vector'`
   - 确认chunk向量化写入pgvector → query验证向量检索返回Top-K

2. **Neo4j连接池**（KGWriterService）：
   - 加HikariCP风格的连接池配置（最大连接10，最小空闲2）
   - 健康检查：`MATCH (n) RETURN count(n) LIMIT 1` ping每30s
   - 自动重连：连接断开后3次重试

3. **RAG端点验证**：
   - `POST /api/v1/kb/rag` 返回Top-K文档+来源+置信度
   - 验证向量检索延迟 <2s

**curl验收**:
```bash
# RAG检索
curl -s -H "Authorization: Bearer $TOKEN" \
  -X POST http://localhost:8080/api/v1/kb/rag \
  -H 'Content-Type: application/json' \
  -d '{"query":"差旅费报销标准","topK":5}' \
  | python3 -c "import sys,json; d=json.load(sys.stdin); \
     sources=d.get('data',{}).get('sources',[]); \
     print(f'PASS: {len(sources)}条结果' if len(sources)>0 else 'FAIL: 无结果')"
# 期望: PASS (如果没有向量数据，返回0条也算链路通，但需确认请求到达了pgvector)

# Neo4j健康检查
curl -s -H "Authorization: Bearer $TOKEN" \
  -X GET http://localhost:8080/api/v1/kb/graph/health \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print('PASS: Neo4j OK' if d.get('success') else 'FAIL')"
```

---

## §验证门禁

```bash
# V1: 文件生存
find engine/kb-engine -name "*.java" -newer /home/guorongxiao/ECOS/docs/3-data/PMO-22-kb-engine增强.md -type f | sort

# V2: 关键类
grep -rn "EntityLinkerService\|KnowledgeExtractionService\|pgvector" engine/kb-engine/ --include="*.java"

# V3: 编译
env -i HOME=/home/guorongxiao PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn install -DskipTests -Dmaven.test.skip=true -q 2>&1 | tail -5'

# V4: Gateway curl (T1-T3)
```

## §工时

| Task | 工期 | 依赖 |
|:--|:--:|------|
| T1 抽取管道验证 | 1.5天 | — |
| T2 实体链接 | 1.5天 | T1(抽取实体才有链接) |
| T3 向量索引+Neo4j池 | 1天 | — |
