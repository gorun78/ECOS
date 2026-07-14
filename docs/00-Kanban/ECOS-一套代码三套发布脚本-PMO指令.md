# PMO指令: 一套代码三套发布脚本

> **来源**: 肖国荣产品分层决策 | **日期**: 2026-06-29
> **铁律**: Git提交=唯一绩效。DONE=commit hash+编译通过+启动成功。不产出解释性文档。
> **原则**: 同一套代码，三套Maven/Spring/Docker配置，零业务代码的`if edition==xxx`

---

## 背景

ECOS产品分三层：标准版（中小企业/财务管控）、企业版（中型集团/战略管控）、旗舰版（大型集团/运营管控）。

差异维度：

| | 标准版 | 企业版 | 旗舰版 |
|------|:--:|:--:|:--:|
| Maven profile | `standard` | `enterprise` | `flagship` |
| Doris模块 | ❌ exclude | ✅ include | ✅ include |
| Neo4j模块 | ❌ exclude | ✅ include | ✅ include |
| MinIO模块 | ❌ exclude | ❌ exclude | ✅ include |
| 隔离模式 | ROW_FILTER | SCHEMA | DATABASE |
| 种子数据 | 财务管控模板 | 战略管控模板 | 运营管控模板 |
| Docker Compose | 单PG | PG+Doris+Neo4j | PG+Doris+Neo4j+MinIO+K8s |

---

## 禁止清单

1. **禁止在业务代码里写 `if (edition == "enterprise")`**
2. **禁止三个分支/三个仓库**——差异只在配置
3. **禁止硬编码基础设施依赖**——Doris/Neo4j的注入走`@ConditionalOnProperty`
4. **禁止复制Controller**——同一份Controller，通过特征开关控制端点可见性

---

## Phase 1: Maven Profile 模块裁剪（3天）

### T1.1: 根POM添加三套profiles

**改文件**: `pom.xml`

```xml
<profiles>
    <!-- 标准版: 轻量，仅PG -->
    <profile>
        <id>standard</id>
        <modules>
            <module>databridge-common</module>
            <module>databridge-runtime</module>
            <module>databridge-sysman</module>
            <module>databridge-datanet</module>
            <module>databridge-buszhi</module>
            <module>databridge-dccheng</module>
            <module>databridge-workspace</module>
            <module>databridge-worldmodel</module>
            <module>databridge-aimod</module>
            <module>databridge-cognitive</module>
            <module>databridge-portal</module>
            <module>databridge-market</module>
            <module>databridge-gateway</module>
        </modules>
    </profile>

    <!-- 企业版: +Doris +Neo4j -->
    <profile>
        <id>enterprise</id>
        <modules>
            <!-- 标准版全部模块 -->
            <module>databridge-common</module>
            <module>databridge-runtime</module>
            <module>databridge-sysman</module>
            <module>databridge-datanet</module>
            <module>databridge-buszhi</module>
            <module>databridge-dccheng</module>
            <module>databridge-workspace</module>
            <module>databridge-worldmodel</module>
            <module>databridge-aimod</module>
            <module>databridge-cognitive</module>
            <module>databridge-portal</module>
            <module>databridge-market</module>
            <module>databridge-gateway</module>
            <!-- 企业版扩展 -->
            <module>databridge-olap</module>    <!-- Doris适配器 -->
            <module>databridge-graph</module>    <!-- Neo4j适配器 -->
        </modules>
    </profile>

    <!-- 旗舰版: +Doris +Neo4j +MinIO -->
    <profile>
        <id>flagship</id>
        <modules>
            <!-- 企业版全部模块 -->
            ... (同上)
            <!-- 旗舰版扩展 -->
            <module>databridge-olap</module>
            <module>databridge-graph</module>
            <module>databridge-lake</module>     <!-- MinIO数据湖 -->
        </modules>
    </profile>
</profiles>
```

### T1.2: Doris适配器模块骨架

**新建模块**: `databridge-olap/`（2个POM + 适配器类）

```
databridge-olap/
├── pom.xml
├── olap-api/
│   └── pom.xml
│   └── src/main/java/com/chinacreator/gzcm/olap/
│       ├── OlapQueryService.java       — 接口: query(OlapRequest)→OlapResponse
│       └── OlapConfig.java             — @ConfigurationProperties
└── olap-impl/
    └── pom.xml
    └── src/main/java/com/chinacreator/gzcm/olap/
        ├── DorisQueryService.java      — JDBC直连Doris
        ├── PostgresQueryService.java   — PG兜底（标准版用）
        └── OlapAutoConfiguration.java  — @ConditionalOnProperty(prefix="ecos.olap", name="engine", havingValue="doris")
```

**关键**: `PostgresQueryService`和`DorisQueryService`都实现`OlapQueryService`接口。标准版只编译`PostgresQueryService`，企业版/旗舰版编译`DorisQueryService`。——**同一套Controller代码通过`@Autowired OlapQueryService`注入，不知道底层是谁**。

### T1.3: Neo4j适配器模块骨架

**新建模块**: `databridge-graph/`（2个POM + 适配器类）

```
databridge-graph/
├── pom.xml
├── graph-api/
│   └── pom.xml
│   └── src/main/java/com/chinacreator/gzcm/graph/
│       ├── GraphQueryService.java      — 接口: traverse(GraphRequest)→GraphResponse
│       └── GraphConfig.java
└── graph-impl/
    └── pom.xml
    └── src/main/java/com/chinacreator/gzcm/graph/
        ├── Neo4jGraphService.java      — Neo4j Java Driver
        ├── PostgresGraphService.java   — PG递归CTE兜底（标准版用）
        └── GraphAutoConfiguration.java — @ConditionalOnProperty
```

**T1.2和T1.3验收**:

```bash
# 标准版编译 — 不含doris/graph模块
mvn clean install -Pstandard -DskipTests
# 期望: BUILD SUCCESS，grep databridge-olap 无结果

# 企业版编译 — 含doris+graph模块
mvn clean install -Penterprise -DskipTests
# 期望: BUILD SUCCESS，grep databridge-olap 有结果

# 旗舰版编译 — 全模块
mvn clean install -Pflagship -DskipTests
# 期望: BUILD SUCCESS
```

---

## Phase 2: Spring Profile 运行时配置（2天）

### T2.1: 三套application-{edition}.yml

**改文件**: `databridge-gateway/src/main/resources/`

```
application-standard.yml     — 单PG，RowFilter隔离，OlapQueryService→PostgresQueryService
application-enterprise.yml   — PG+Doris+Neo4j，Schema隔离，OlapQueryService→DorisQueryService
application-flagship.yml     — PG+Doris+Neo4j+MinIO，Database隔离，全部启用
```

**`application-standard.yml`核心内容**:

```yaml
ecos:
  edition: standard
  olap:
    engine: postgresql             # 不用Doris
  graph:
    engine: postgresql             # 不用Neo4j，PG递归CTE
  storage:
    engine: postgresql             # 不用MinIO
  tenant:
    isolation: ROW_FILTER          # 行级过滤
```

**`application-enterprise.yml`核心内容**:

```yaml
ecos:
  edition: enterprise
  olap:
    engine: doris
    host: ${DORIS_HOST:localhost}
    port: ${DORIS_PORT:9030}
  graph:
    engine: neo4j
    uri: bolt://${NEO4J_HOST:localhost}:7687
  tenant:
    isolation: SCHEMA
    schema-prefix: t_
```

**`application-flagship.yml`核心内容**:

```yaml
ecos:
  edition: flagship
  olap:
    engine: doris
  graph:
    engine: neo4j
  storage:
    engine: minio
    endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
  tenant:
    isolation: DATABASE
    datasource-pool-size: 20
```

### T2.2: 特征开关Conditional

**改文件**: 所有涉及Doris/Neo4j/MinIO的Bean加`@ConditionalOnProperty`

```java
// OlapAutoConfiguration.java
@Configuration
@ConditionalOnProperty(prefix = "ecos.olap", name = "engine", havingValue = "doris")
public class DorisOlapConfiguration {
    @Bean
    public OlapQueryService olapQueryService() {
        return new DorisQueryService();
    }
}

@Configuration
@ConditionalOnProperty(prefix = "ecos.olap", name = "engine", havingValue = "postgresql", matchIfMissing = true)
public class PostgresOlapConfiguration {
    @Bean
    public OlapQueryService olapQueryService() {
        return new PostgresQueryService();
    }
}
```

### T2.3: 隔离拦截器特征开关

**新建**: `TenantIsolationInterceptor.java`

```java
@Component
public class TenantIsolationInterceptor {
    
    @Value("${ecos.tenant.isolation:ROW_FILTER}")
    private String isolationMode;
    
    // ROW_FILTER: 在SQL WHERE子句注入 tenant_id = ?
    // SCHEMA: 在SQL表名前加 schema prefix (SET search_path TO t_{tenantId})
    // DATABASE: 切换DataSource (AbstractRoutingDataSource)
}
```

### T2.4: GatewayApplication 的 ComponentScan 按Profile裁剪

**改文件**: `GatewayApplication.java`

```java
@ComponentScan(basePackages = {
    "com.chinacreator.gzcm.gateway",
    "com.chinacreator.gzcm.common",
    "com.chinacreator.gzcm.sysman",
    "com.chinacreator.gzcm.runtime",
    "com.chinacreator.gzcm.dccheng",
    "com.chinacreator.gzcm.buszhi",
    "com.chinacreator.gzcm.aimod",
    "com.chinacreator.gzcm.market",
    "com.chinacreator.gzcm.worldmodel",
    "com.chinacreator.gzcm.workspace",
    "com.chinacreator.gzcm.portal",
    "com.chinacreator.gzcm.datanet",
    "com.chinacreator.gzcm.cognitive"
    // olap和graph模块的包由各自的@ConditionalOnProperty控制
}, excludeFilters = {
    @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "com.chinacreator.gzcm.(olap|graph|lake)\\.(Doris|Neo4j|Minio).*"
    )
})
```

**Phase 2验收**:

```bash
# 标准版启动
SPRING_PROFILES_ACTIVE=standard mvn spring-boot:run -pl databridge-gateway -DskipTests
# 期望: 启动成功，无Doris/Neo4j连接错误，ecos.olap.engine=postgresql

# 企业版启动
SPRING_PROFILES_ACTIVE=enterprise mvn spring-boot:run -pl databridge-gateway -DskipTests
# 期望: 启动成功，Doris和Neo4j连接建立
```

---

## Phase 3: 三套部署脚本（2天）

### T3.1: Docker Compose 三套

```
deploy/
├── standard/
│   └── docker-compose.yml     → PG + Gateway
├── enterprise/
│   └── docker-compose.yml     → PG + Doris(FE+BE) + Neo4j + Gateway
├── flagship/
│   └── docker-compose.yml     → PG + Doris + Neo4j + MinIO + Gateway
│   └── k8s/
│       ├── namespace.yaml
│       ├── configmap.yaml
│       └── deployment.yaml
```

### T3.2: 部署脚本

**`deploy-standard.sh`**:

```bash
#!/bin/bash
mvn clean install -Pstandard -DskipTests -q
docker-compose -f deploy/standard/docker-compose.yml up -d
echo "ECOS 标准版已启动: http://localhost:8080"
```

**`deploy-enterprise.sh`**:

```bash
#!/bin/bash
mvn clean install -Penterprise -DskipTests -q
docker-compose -f deploy/enterprise/docker-compose.yml up -d
echo "ECOS 企业版已启动: http://localhost:8080"
echo "Doris: http://localhost:8030 | Neo4j: http://localhost:7474"
```

**`deploy-flagship.sh`**:

```bash
#!/bin/bash
mvn clean install -Pflagship -DskipTests -q
docker-compose -f deploy/flagship/docker-compose.yml up -d
echo "ECOS 旗舰版已启动: http://localhost:8080"
echo "Doris: http://localhost:8030 | Neo4j: http://localhost:7474 | MinIO: http://localhost:9000"
```

### T3.3: Flyway种子数据分层

**标准版种子（财务管控型模板）**:
```
V100__seed_standard_ontology.sql    — 财务域实体(Account/Investment/ROI/Subsidiary)
V101__seed_standard_metrics.sql     — 财务指标(ROI/利润/资产负债)
V102__seed_standard_dq_rules.sql    — 财务数据质量规则
```

**企业版种子（战略管控型模板）**:
```
V100__seed_standard_ontology.sql    — (继承标准版)
V101__seed_enterprise_ontology.sql  — +事业部/战略KPI/协同指标
V102__seed_enterprise_causals.sql   — +跨事业部因果链
```

**旗舰版种子（运营管控型模板）**:
```
V100__seed_standard_ontology.sql    — (继承标准版)
V101__seed_enterprise_ontology.sql  — (继承企业版)
V102__seed_flagship_ontology.sql    — +全链实体(生产/质量/供应链)
V103__seed_flagship_dq_rules.sql    — +全业务数据质量规则
```

**验收**:

```bash
# 标准版: 只有财务域
curl http://localhost:8080/api/v1/ecos/ontologies | jq '.data | length'
# 期望: ~4 (Account/Investment/ROI/Subsidiary)

# 旗舰版: 全链实体
curl http://localhost:8080/api/v1/ecos/ontologies | jq '.data | length'
# 期望: ~12 (含Production/Quality/Supplier/Warehouse...)
```

---

## 资源与排期

| Phase | 内容 | 工时 |
|:--:|------|:--:|
| P1 | Maven Profile + olap/graph模块骨架 | 3d |
| P2 | Spring Profile + 特征开关 + 隔离拦截器 | 2d |
| P3 | Docker Compose + 部署脚本 + Flyway分层 | 2d |
| **合计** | | **7d (1周)** |

前后端并行:
- BE: P1+P2 (5d) — 模块骨架+配置+拦截器
- FE+BE: P3 (2d) — 部署脚本+种子数据

---

## 验收方式

肖总亲自检查：

1. `mvn clean install -Pstandard -DskipTests` → BUILD SUCCESS
2. `mvn clean install -Penterprise -DskipTests` → BUILD SUCCESS，确认含olap/graph模块
3. `mvn clean install -Pflagship -DskipTests` → BUILD SUCCESS
4. 标准版启动 → `/api/health` 返回 `"edition":"standard"`，无Doris/Neo4j连接
5. 企业版启动 → `/api/health` 返回 `"edition":"enterprise"`，Doris/Neo4j Healthy
6. 三份种子数据导入后，Ontology实体数: 标准版~4 / 企业版~8 / 旗舰版~12
