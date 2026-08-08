# PMO-28: 本体提案持久化 + Mapping持久化 + 版本联动

> **架构铁律**: 必须遵循 `/home/guorongxiao/ECOS/docs/ARCHITECTURE-RULES.md`
> **差距分析**: `/home/guorongxiao/ECOS/docs/4-onto/01-差距分析.md` §7.3, §7.7
> 来源: 肖国荣 | 日期: 2026-08-08 | 优先级: 🔴 P0
> **引擎**: ontology-engine:18083 | **工期**: 2天 | **协同**: ECOS-BE
> **工具**: 执行前用 `codebase-memory-mcp search_graph/trace_path` 理解现有提案/版本/Mapping相关类的调用链

---

## §背景

OntologyProposalController (349行) + OntologyMappingController (191行) 均用 `ConcurrentHashMap` 存数据——进程重启全部丢失。提案是本体治理核心机制，Mapping是自动发现基础设施，内存存储不可接受。

同时提案审批通过后需要自动打通版本发布——当前提案系统与版本系统是两套独立流程。

---

## §前置：codebase-memory-mcp 探索

执行任何代码修改前，先用 `codebase-memory-mcp` 理解代码结构：

```bash
# 1. 确认项目已索引
codebase-memory-mcp list_projects

# 2. 查看本体引擎相关的调用图
codebase-memory-mcp search_graph --label Class --pattern ".*Proposal.*"
codebase-memory-mcp trace_path --function "OntologyProposalController.approveProposal" --direction both --depth 3

# 3. 查看版本Controller的依赖
codebase-memory-mcp trace_path --function "OntologyVersionController.publishVersion" --direction outbound
```

---

## §禁止清单

1. ❌ 不新增Maven模块（铁律0.4）
2. ❌ 不新开Docker容器
3. ❌ 不删除旧的WorkflowController——保留，供Phase 5联调用
4. ❌ 提案和版本不打通的方案拒收——必须是审批通过→自动触发版本发布
5. ❌ 不硬编码中文——提案payload/错误消息用i18n key
6. ❌ 新表不设外键级联——ECOS只增不删（AGENTS.md）

---

## §Task

### T1: ecos_ontology_proposals 建表 + Mapper（0.5天）

**新建文件**:
- `gateway/src/main/resources/db/migration/V4.1__ontology_proposals.sql`

**建表SQL**:
```sql
CREATE TABLE IF NOT EXISTS ecos_ontology_proposals (
    id              BIGSERIAL PRIMARY KEY,
    domain_code     VARCHAR(128) NOT NULL,
    proposal_type   VARCHAR(32) NOT NULL,        -- CREATE_ENTITY/ADD_PROPERTY/MODIFY_PROPERTY/DELETE_ENTITY/ADD_RELATIONSHIP/CREATE_FUNCTION
    target_entity   VARCHAR(256),
    payload         JSONB NOT NULL,               -- 变更内容
    snapshot        JSONB,                        -- 变更前快照（用于回滚）
    status          VARCHAR(16) NOT NULL DEFAULT 'DRAFT',  -- DRAFT/PENDING/APPROVED/REJECTED/EXECUTED
    author          VARCHAR(64),
    reviewer        VARCHAR(64),
    version_id      BIGINT,                       -- 关联的版本ID（执行后回填）
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_proposals_domain ON ecos_ontology_proposals(domain_code, status);
CREATE INDEX idx_proposals_author ON ecos_ontology_proposals(author);
```

**新建文件**:
- `engine/ontology-engine/ontology-engine-impl/src/main/resources/mapper/OntologyProposalMapper.xml`

---

### T2: OntologyProposalController 重写（1天）

**重写文件**: `engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/controller/OntologyProposalController.java`

**现状**: 349行，ConcurrentHashMap存储

**目标**: HashMap→JdbcTemplate，保留现有端点签名不变，新增提案-版本联动

**保留端点**（不改签名）:
| 端点 | 方法 | 现状 | 改后 |
|------|------|------|------|
| `/api/v1/ontology/proposals` | GET | HashMap.values() | JdbcTemplate.query |
| `/api/v1/ontology/proposals` | POST | HashMap.put() | INSERT+返回ID |
| `/api/v1/ontology/proposals/{id}` | GET | HashMap.get() | SELECT BY ID |
| `/api/v1/ontology/proposals/{id}` | PUT | HashMap.replace() | UPDATE |
| `/api/v1/ontology/proposals/{id}` | DELETE | HashMap.remove() | UPDATE status=REJECTED |
| `/api/v1/ontology/proposals/{id}/submit` | POST | status→PENDING | UPDATE status=PENDING |
| `/api/v1/ontology/proposals/{id}/approve` | POST | status→APPROVED | UPDATE+触发版本发布(见T3) |
| `/api/v1/ontology/proposals/{id}/reject` | POST | status→REJECTED | UPDATE status=REJECTED |
| `/api/v1/ontology/proposals/{id}/execute` | POST | 执行payload | 执行payload+status→EXECUTED |

**curl验收**(T2):
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")

# 创建提案
curl -s -H "Authorization: Bearer $TOKEN" \
  -X POST http://localhost:8080/api/v1/ontology/proposals \
  -H 'Content-Type: application/json' \
  -d '{"domainCode":"finance","proposalType":"CREATE_ENTITY","targetEntity":"fin_invoice","payload":{"name":"发票","properties":[{"name":"invoice_no","type":"STRING"}]}}' \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print('PASS: id='+str(d['data']['id']) if d.get('success') else 'FAIL')"
# 期望: PASS: id=1

# 重启Gateway后查询（验证持久化）
curl -s -H "Authorization: Bearer $TOKEN" \
  -X GET "http://localhost:8080/api/v1/ontology/proposals/1" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print('PASS: 持久化OK' if d.get('data') else 'FAIL: 重启丢失')"
# 期望: PASS: 持久化OK
```

---

### T3: 提案-版本联动（0.5天）

**修改文件**: `engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/controller/OntologyProposalController.java`

**新增端点**: `POST /api/v1/ontology/proposals/{id}/approve-and-publish`

**实现逻辑**:
1. 提案状态改为 APPROVED
2. 调用 `OntologyVersionController` 创建新 Draft 版本
3. 执行 payload 变更（创建实体/属性/关系等）
4. 新版本 publish
5. 提案 status→EXECUTED，回填 version_id

**curl验收**:
```bash
# 提交→审批+发布（一键）
curl -s -H "Authorization: Bearer $TOKEN" \
  -X POST "http://localhost:8080/api/v1/ontology/proposals/1/approve-and-publish" \
  -H 'Content-Type: application/json' \
  -d '{"reviewerComment":"OK"}' \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print('PASS' if d.get('success') and d['data'].get('versionId') else 'FAIL')"

# 确认实体已创建
curl -s -H "Authorization: Bearer $TOKEN" \
  -X GET "http://localhost:8080/api/v1/ecos/domains/finance/entities/fin_invoice" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print('PASS: 实体已创建' if d.get('data') else 'FAIL')"
# 期望: PASS: 实体已创建
```

---

### T4: OntologyMappingController 持久化（0.5天）

**重写文件**: `engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/controller/OntologyMappingController.java`

**现状**: 191行，ConcurrentHashMap

**目标**: HashMap→JdbcTemplate，写入已有表 `ecos_entity_table_mapping`

**保留端点**: 全部保留签名不变，只替换存储层。

**curl验收**:
```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  -X POST "http://localhost:8080/api/v1/ecos/entity-mappings" \
  -H 'Content-Type: application/json' \
  -d '{"domainCode":"finance","entityName":"fin_revenue","tableName":"fin_revenue","columnMappings":[{"propertyName":"amount","columnName":"amount","columnType":"NUMERIC"}]}' \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print('PASS' if d.get('success') else 'FAIL')"

# 重启后验证持久化
curl -s -H "Authorization: Bearer $TOKEN" \
  -X GET "http://localhost:8080/api/v1/ecos/entity-mappings?domainCode=finance" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); items=d.get('data',{}).get('items',[]); print(f'PASS: {len(items)}条' if len(items)>0 else 'FAIL: 重启丢失')"
```

---

## §三滤波器注册

T2-T3新增端点 `/api/v1/ontology/proposals/**` 已在 `OntologySecurityInterceptor` 覆盖，但需验证三层：

| 层 | 文件 | 操作 |
|:--|------|------|
| 1 | `gateway/.../VersionPrefixRewriteFilter.java` | 确认 `/api/v1/ontology/` 映射 |
| 2 | `sysman/.../security/SecurityConfig.java` | 加 `/api/v1/ontology/**` 到 permitAll |
| 3 | `sysman/.../security/ClearanceInterceptor.java` | 加 `/api/v1/ontology` 到豁免列表 |

---

## §验证门禁

```bash
# V1: 编译
cd /home/guorongxiao/ECOS/ecos_backend && mvn install -DskipTests -q 2>&1 | tail -3

# V2: 建表确认
docker exec ecos-postgres psql -U postgres sys_man -c "\d ecos_ontology_proposals"

# V3: 全量curl（T2+T3+T4各跑一条）
# V4: 重启Gateway后所有数据仍在
```
