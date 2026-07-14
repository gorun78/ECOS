# PMO指令: ECOS 一套代码三套发布 — 标准版/企业版/旗舰版

> **来源**: 肖国荣产品架构规划 | **日期**: 2026-06-29
> **铁律**: 一套代码全量编译，功能按Profile激活。三套docker-compose + 三套application.yml。
> **目标**: 同一份源码，三种规模客户覆盖。不改一行业务代码，只加配置层。

---

## 背景

ECOS现有客户三种管控类型都有，且已用其他产品实施过一期。ECOS需要完成升级覆盖。

不同规模企业数据量差异大（<50GB→>1TB），基础设施需求不同（PG→Doris/Neo4j/MinIO），但业务逻辑一致。

**核心原则**：
1. **一套代码**——所有功能全量编译，不条件编译
2. **接口抽象**——Neo4j/Doris/MinIO通过接口抽象，标准版用PG替代实现
3. **Profile激活**——`@Profile("enterprise")` / `@ConditionalOnProperty`控制
4. **三套脚本**——docker-compose + application.yml + deploy.sh 各三套

---

## 禁止清单

1. **禁止**创建三个Git分支——一个仓库、一个main分支
2. **禁止**条件编译（Maven profile exclude模块）——代码全量编译
3. **禁止**在Controller里写 `if (edition.equals("flagship"))`——用接口抽象
4. **禁止**对已有Endpoints做任何破坏性修改
5. **禁止**产出超过本指令的设计文档

---

## Track A: 接口抽象层（4天，P0核心）

### A1: 图服务抽象（1天）

**问题**: `CausalController`直接引用Neo4j Java Driver，标准版无Neo4j时无法启动。

**方案**: 抽象`IGraphService`接口，两个实现按Profile注入。

**改文件**:
- `databridge-common/databridge-common-api/src/main/java/com/chinacreator/gzcm/common/service/IGraphService.java` — 新建接口

```java
package com.chinacreator.gzcm.common.service;

public interface IGraphService {
    /** 执行Cypher查询（Neo4j用Cypher，PG实现翻译为SQL） */
    List<Map<String, Object>> query(String cypherPattern, Map<String, Object> params);
    /** 创建节点 */
    void createNode(String label, Map<String, Object> props);
    /** 创建关系 */
    void createRelationship(String fromId, String toId, String relType);
    /** 1-hop子图查询（给定实体→返回相邻节点+边） */
    Map<String, Object> getSubgraph(String entityId);
}
```

- `databridge-worldmodel/worldmodel-impl/src/main/java/com/chinacreator/gzcm/worldmodel/service/Neo4jGraphService.java` — 从CausalController迁出Neo4j逻辑，实现IGraphService

```java
@Service
@Profile("enterprise") // 企业版/旗舰版激活
@ConditionalOnProperty(name = "ecos.graph.provider", havingValue = "neo4j", matchIfMissing = false)
public class Neo4jGraphService implements IGraphService {
    private final Driver neo4jDriver;
    // ... 从CausalController抽取Cypher执行逻辑
}
```

- `databridge-worldmodel/worldmodel-impl/src/main/java/com/chinacreator/gzcm/worldmodel/service/PgGraphService.java` — 标准版，用PG递归CTE实现

```java
@Service
@Profile("standard") // 标准版激活
public class PgGraphService implements IGraphService {
    private final JdbcTemplate jdbc;
    
    @Override
    public List<Map<String, Object>> query(String cypherPattern, Map<String, Object> params) {
        // 翻译简单Cypher为PG SQL: MATCH (n:Project {name:"浙北路桥"})-[r]->(m)
        // → SELECT * FROM ecos_objects WHERE object_type='Project' ...
    }
    
    @Override
    public Map<String, Object> getSubgraph(String entityId) {
        // SELECT ... FROM ecos_objects o JOIN ecos_object_links l ON o.id=l.source_id
        // 返回1-hop子图
    }
}
```

- `databridge-worldmodel/worldmodel-impl/src/main/java/com/chinacreator/gzcm/worldmodel/controller/CausalController.java` — **重构**：不再直接引用Neo4j Driver，改注入`IGraphService`

**验收**:
```bash
# 标准版启动（无Neo4j容器）
SPRING_PROFILES_ACTIVE=standard
curl -X POST http://localhost:8080/api/causal/graph?entity=Supplier \
  -H "Authorization: Bearer *** expect: 200 + PgGraphService返回相邻节点
# 企业版启动（有Neo4j容器）
SPRING_PROFILES_ACTIVE=enterprise
curl -X POST http://localhost:8080/api/causal/ontology/sync \
  -H "Authorization: Bearer *** expect: 200 + Neo4j中可见Label
```

---

### A2: 分析引擎抽象（1天）

**问题**: `DorisRunner.java` (216行)直接写Doris JDBC调用，标准版Doris不可用。

**方案**: 抽象`IAnalyticsService`，标准版走PG，旗舰版走Doris。

**改文件**:
- `databridge-common/databridge-common-api/src/main/java/com/chinacreator/gzcm/common/service/IAnalyticsService.java`

```java
public interface IAnalyticsService {
    /** 执行OLAP查询（Doris用自身SQL方言，PG用窗口函数/CTE翻译） */
    List<Map<String, Object>> executeQuery(String analyticsSql);
    /** Doris/health 或 PG替代 */
    Map<String, Object> health();
}
```

- `databridge-datanet/datanet-impl/src/main/java/com/chinacreator/gzcm/datanet/service/DorisAnalyticsService.java` — 现有DorisRunner迁入，实现接口

```java
@Service
@Profile("flagship")
@ConditionalOnProperty(name = "ecos.analytics.provider", havingValue = "doris")
public class DorisAnalyticsService implements IAnalyticsService { ... }
```

- `databridge-datanet/datanet-impl/src/main/java/com/chinacreator/gzcm/datanet/service/PgAnalyticsService.java` — 标准版/企业版

```java
@Service
@Profile({"standard", "enterprise"})
public class PgAnalyticsService implements IAnalyticsService {
    private final JdbcTemplate jdbc;
    @Override
    public List<Map<String, Object>> executeQuery(String sql) {
        // Doris SQL → PG SQL翻译（GROUPING SETS→简单GROUP BY，PERCENTILE→PERCENTILE_CONT）
        return jdbc.queryForList(translateToPg(sql));
    }
}
```

**验收**:
```bash
# 标准版: Doris/health端点走PG
curl http://localhost:8080/api/v1/task/doris/health -H "Authorization: Bearer *** expect: 200 + {"analyticsProvider":"PG","status":"UP"}
```

---

### A3: 文件存储抽象（1天）

**问题**: `WorkbookExecutionEngine` / MinIO操作在标准版不可用。

**方案**: 抽象`IObjectStorageService`。

**改文件**:
- `databridge-common/databridge-common-api/src/main/java/com/chinacreator/gzcm/common/service/IObjectStorageService.java`

```java
public interface IObjectStorageService {
    String putObject(String key, byte[] data, String contentType);
    byte[] getObject(String key);
    void deleteObject(String key);
}
```

- `databridge-workspace/workspace-impl/.../MinioObjectStorageService.java` — MinIO实现

```java
@Service
@Profile({"enterprise", "flagship"})
@ConditionalOnProperty(name = "ecos.storage.provider", havingValue = "minio")
public class MinioObjectStorageService implements IObjectStorageService { ... }
```

- `databridge-workspace/workspace-impl/.../PgObjectStorageService.java` — PG BYTEA实现（标准版，<10MB单文件）

```java
@Service
@Profile("standard")
public class PgObjectStorageService implements IObjectStorageService {
    // CREATE TABLE ecos_files (id VARCHAR(32), content BYTEA, content_type VARCHAR(64))
}
```

**验收**:
```bash
# 标准版: Workbook文件上传走PG
curl -X POST http://localhost:8080/api/v1/workbooks/upload \
  -F "file=@test.csv" -H "Authorization: Bearer *** expect: 200 + {"storageProvider":"PG","objectKey":"..."}
```

---

## Track B: 三套Spring配置（2天）

### B1: application-{edition}.yml（1天）

**目标**: 同一份Gateway JAR，通过 `spring.profiles.active` 切换功能集。

**改文件**: `databridge-gateway/src/main/resources/`

**application-standard.yml**:
```yaml
# ── 标准版：PG only ──
ecos:
  edition: standard
  graph:
    provider: pg
  analytics:
    provider: pg
  storage:
    provider: pg
  features:
    knowledge-graph: false       # 无KG
    causal-analysis: false       # 无因果链追溯
    workbook-sandbox: false      # 无文件沙盒
    nsga-ii-optimizer: false     # 无多目标优化
    multi-tenant: false          # 暂不开启（单租户）
    opentelemetry: false
  module:
    aimod: false                 # 无Agent模块
    cognitive: false             # 无认知引擎
    worldmodel: false            # 无世界模型
    market: false                # 无应用市场
    portal: false

spring:
  autoconfigure:
    exclude:
      - com.chinacreator.gzcm.aimod.AimodAutoConfiguration
      - com.chinacreator.gzcm.cognitive.CognitiveAutoConfiguration
```

**application-enterprise.yml**:
```yaml
ecos:
  edition: enterprise
  graph:
    provider: neo4j
    neo4j-uri: bolt://localhost:7687
  analytics:
    provider: pg
  storage:
    provider: minio
    minio-endpoint: http://localhost:9000
  features:
    knowledge-graph: true
    causal-analysis: true
    workbook-sandbox: true
    nsga-ii-optimizer: true
    multi-tenant: true
    opentelemetry: true
  module:
    aimod: true
    cognitive: true
    worldmodel: true
    market: true
    portal: true
```

**application-flagship.yml**:
```yaml
ecos:
  edition: flagship
  graph:
    provider: neo4j
    neo4j-uri: bolt://neo4j-cluster:7687
  analytics:
    provider: doris
    doris-fe: jdbc:mysql://doris-fe:9030
    doris-be: http://doris-be:8040
  storage:
    provider: minio
    minio-endpoint: http://minio-cluster:9000
  features:
    knowledge-graph: true
    causal-analysis: true
    workbook-sandbox: true
    nsga-ii-optimizer: true
    multi-tenant: true
    opentelemetry: true
  module:
    aimod: true
    cognitive: true
    worldmodel: true
    market: true
    portal: true
    datanet: true
```

---

### B2: 条件加载组件（0.5天）

**目标**: Controller中的Bean按Profile条件加载，避免标准版启动时因缺失依赖而崩溃。

**关键注解应用**:
```java
// CausalController — 仅企业版/旗舰版激活
@RestController
@RequestMapping("/api/causal")
@Profile({"enterprise", "flagship"})
public class CausalController { ... }

// OntologyKgSyncService — 仅企业版/旗舰版
@Service
@Profile({"enterprise", "flagship"})
public class OntologyKgSyncService { ... }

// DorisRunner — 仅旗舰版
@Service
@Profile("flagship")
public class DorisRunner { ... }

// WorkbookExecutionEngine — 仅企业版/旗舰版
@Profile({"enterprise", "flagship"})
public class WorkbookExecutionEngine { ... }
```

**验收**:
```bash
# 标准版启动 → 无Bean缺失错误
cd /home/guorongxiao/databridge-v2
export JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10
SPRING_PROFILES_ACTIVE=standard mvn spring-boot:run -pl databridge-gateway -DskipTests

# 验证：CausalController端点不存在
curl http://localhost:8080/api/causal/graph → 404 ✅

# 企业版启动 → CausalController存在
SPRING_PROFILES_ACTIVE=enterprise mvn spring-boot:run -pl databridge-gateway -DskipTests
curl http://localhost:8080/api/causal/graph → 200 ✅
```

---

### B3: Flyway迁移分版（0.5天）

**问题**: 标准版的Flyway不应执行Neo4j相关（V26/V27/V34）、Doris相关（V30）等迁移脚本。

**方案**: Flyway的`locations`按Profile区分。

**改文件**: `application-{edition}.yml`:
```yaml
spring:
  flyway:
    locations:
      - classpath:db/migration/common     # 全版通用：V1~V21
      - classpath:db/migration/standard   # 标准版额外：V2x（PG替代方案的DDL）
      # 企业版：+ classpath:db/migration/enterprise（Neo4j相关V26,V27,V34）
      # 旗舰版：+ classpath:db/migration/flagship（Doris相关V30）
```

**改文件**: 将现有Flyway迁移文件按版本拆到子目录。

---

## Track C: 三套Docker Compose（1天）

### C1: docker-compose-standard.yml

```yaml
version: '3.8'
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: sys_man
      POSTGRES_USER: root
      POSTGRES_PASSWORD: root
    ports: ["5432:5432"]
    volumes: ["./data/pg:/var/lib/postgresql/data"]

  gateway:
    image: ecos-gateway:latest
    ports: ["8080:8080"]
    environment:
      SPRING_PROFILES_ACTIVE: standard
    depends_on: [postgres]
    # 无Neo4j、无Doris、无MinIO
```

### C2: docker-compose-enterprise.yml

```yaml
version: '3.8'
services:
  postgres: ...
  neo4j:
    image: neo4j:5
    environment:
      NEO4J_AUTH: neo4j/password
    ports: ["7474:7474", "7687:7687"]
  minio:
    image: minio/minio
    ports: ["9000:9000", "9001:9001"]
    environment:
      MINIO_ROOT_USER: admin
      MINIO_ROOT_PASSWORD: admin123
  gateway:
    image: ecos-gateway:latest
    environment:
      SPRING_PROFILES_ACTIVE: enterprise
    depends_on: [postgres, neo4j, minio]
```

### C3: docker-compose-flagship.yml

```yaml
version: '3.8'
services:
  postgres: ...
  neo4j: ...
  minio: ...
  doris-fe:
    image: apache/doris:2.1-fe
    ports: ["8030:8030", "9030:9030"]
  doris-be:
    image: apache/doris:2.1-be
    ports: ["8040:8040"]
  gateway:
    image: ecos-gateway:latest
    environment:
      SPRING_PROFILES_ACTIVE: flagship
    depends_on: [postgres, neo4j, minio, doris-fe, doris-be]
```

---

## Track D: 升级覆盖——现有客户数据迁移（2天）

### D1: 原产品数据迁移脚本（1天）

**问题**: 现有客户已用其他产品实施一期，需将原产品数据导入ECOS。

**方案**: 为每种管控类型写一个数据迁移存储过程 + Ontology模板初始化脚本。

**改文件**: `databridge-sysman/sysman-impl/src/main/resources/db/migration/common/V37__ecos_upgrade_from_legacy.sql`

```sql
-- 运营管控型客户导入模板（信科/制造型）
-- 1. 创建Ontology实体（Project/Contract/Supplier/Warehouse）
-- 2. 从原产品表导入数据：
INSERT INTO ecos_objects (object_type, properties, tenant_id)
SELECT 'Project', jsonb_build_object('name', p.project_name, 'budget', p.budget, ...), p.tenant_id
FROM legacy_project p;
-- 3. 创建预设指标（OEE/良品率/产值率）
-- 4. 创建DQ规则（物料编码/供应商资质/生产合规）
```

### D2: E2E验证（1天）

对三种管控类型各验证一套客户升级路径：

| 客户 | 管控类型 | 原产品 | 迁移脚本 | 验证标准 |
|------|----------|--------|----------|---------|
| 信科/制造企业 | 运营管控 | 老ERP/自研MES | V37-ops | CEO看OEE+良品率 |
| 连锁/平台企业 | 战略管控 | 用友/金蝶 | V37-strategy | CEO看事业部协同 |
| 投资集团 | 财务管控 | 报表系统 | V37-financial | CEO看ROI |

---

## 资源与排期

| 轨道 | 工时 | 交付物 |
|------|:--:|------|
| A: 接口抽象（IGraphService/IAnalyticsService/IObjectStorageService） | 3d | 3个接口+6个实现类 |
| B: Spring配置（3套yml + Flyway分版 + @Profile注解） | 2d | 3套配置+条件注解 |
| C: Docker Compose（3套） | 1d | 3个compose文件 |
| D: 数据迁移（升级覆盖脚本） | 2d | 3套客户迁移SQL |
| **合计并行** | **5d** | |

```
Day 1-3: Track A (BE) ∥ Track B (BE)    — 抽象接口+配置
Day 3-4: Track C (PMO) ────────────────  — Docker Compose
Day 4-5: Track D (BE+PM) ───────────────  — 迁移脚本+E2E
```

---

## 验收方式

1. **标准版**：`docker compose -f docker-compose-standard.yml up` → Gateway:8080 → health 200 → CausalController 404（正确）
2. **企业版**：`docker compose -f docker-compose-enterprise.yml up` → CausalController 200 → Neo4j有Label → MinIO可上传
3. **旗舰版**：`docker compose -f docker-compose-flagship.yml up` → Doris/health 200 → CausalController 200
4. **升级覆盖**：执行V37迁移 → 原产品客户数据出现在ObjectController查询结果中
