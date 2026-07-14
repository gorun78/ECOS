# PMO指令: 三版本发布体系 + 架构保护机制

> **来源**: 肖国荣产品架构决策 | **日期**: 2026-06-29
> **铁律**: Git提交=唯一绩效。DONE=commit hash+脚本执行结果。不产出解释性文档。
> **原则**: 一套代码，三套发布脚本。不改业务逻辑，只加分层开关。

---

## 背景

ECOS产品定位已明确：一套DIKW底座支撑运营管控/战略管控/财务管控三种管控类型。客户规模从50人到5000人不等。当前代码已包含全部模块（36个Controller + Pipeline + DQ执行 + KG同步 + Agent），现在要做**分层裁剪**——让不同规模客户按需部署，而不是另起代码分支。

同时，存量客户有三种类型（制造/贸易/项目），之前运行在其他产品上。ECOS需要完成对原有产品的升级覆盖。

---

## Part A: 三版本发布脚本（1周，P0）

### A1: Maven三Profile — 模块裁剪（2天，BE）

**目标**: `mvn -Pstandard` 不引入Neo4j和Doris相关模块

**改文件**: `pom.xml`（根）

**内容**: 三个Maven profile

```xml
<profiles>
    <!-- ===== 标准版: 纯PG，不含Neo4j/Doris ===== -->
    <profile>
        <id>standard</id>
        <activation><activeByDefault>true</activeByDefault></activation>
        <modules>
            <module>databridge-common</module>
            <module>databridge-runtime</module>
            <module>databridge-sysman</module>
            <module>databridge-datanet</module>
            <module>databridge-dccheng</module>
            <module>databridge-buszhi</module>
            <module>databridge-aimod</module>
            <module>databridge-workspace</module>
            <module>databridge-market</module>
            <module>databridge-portal</module>
            <module>databridge-gateway</module>
            <!-- 不含: cognitive, worldmodel (依赖Neo4j的模块) -->
        </modules>
        <properties>
            <ecos.edition>standard</ecos.edition>
        </properties>
    </profile>

    <!-- ===== 企业版: PG + Neo4j ===== -->
    <profile>
        <id>enterprise</id>
        <modules>
            <module>databridge-common</module>
            <module>databridge-runtime</module>
            <module>databridge-sysman</module>
            <module>databridge-datanet</module>
            <module>databridge-dccheng</module>
            <module>databridge-buszhi</module>
            <module>databridge-aimod</module>
            <module>databridge-workspace</module>
            <module>databridge-market</module>
            <module>databridge-portal</module>
            <module>databridge-gateway</module>
            <module>databridge-worldmodel</module>  <!-- Neo4j KG -->
            <module>databridge-cognitive</module>     <!-- 认知引擎 -->
        </modules>
        <properties>
            <ecos.edition>enterprise</ecos.edition>
        </properties>
    </profile>

    <!-- ===== 旗舰版: PG + Neo4j + Doris ===== -->
    <profile>
        <id>ultimate</id>
        <modules>
            <module>databridge-common</module>
            <module>databridge-runtime</module>
            <module>databridge-sysman</module>
            <module>databridge-datanet</module>
            <module>databridge-dccheng</module>
            <module>databridge-buszhi</module>
            <module>databridge-aimod</module>
            <module>databridge-workspace</module>
            <module>databridge-market</module>
            <module>databridge-portal</module>
            <module>databridge-gateway</module>
            <module>databridge-worldmodel</module>
            <module>databridge-cognitive</module>
            <module>databridge-datanet/datanet-doris</module>  <!-- Doris 数据源 -->
        </modules>
        <properties>
            <ecos.edition>enterprise</ecos.edition>
        </properties>
    </profile>
</profiles>
```

**验收**:
```bash
cd /home/guorongxiao/databridge-v2 && source ~/ecos-env.sh

# 标准版编译 — 不含Neo4j/Doris模块
mvn -Pstandard install -DskipTests -q && echo "PASS: standard" || echo "FAIL: standard"

# 企业版编译 — 含Neo4j模块
mvn -Penterprise install -DskipTests -q && echo "PASS: enterprise" || echo "FAIL: enterprise"

# 旗舰版编译 — 含全部模块
mvn -Pultimate install -DskipTests -q && echo "PASS: ultimate" || echo "FAIL: ultimate"
```

---

### A2: 功能开关 — Spring @ConditionalOnProperty（2天，BE）

**目标**: 标准版不启动Neo4j相关Bean，避免Bean缺失导致启动失败

**改文件**: 在Neo4j依赖的类上加条件注解

涉及类清单（需要标注`@ConditionalOnProperty(name="ecos.edition", havingValue="enterprise")`或`havingValue="ultimate"`）:
- `CausalController.java` — Neo4j驱动注入
- `OntologyKgSyncService.java` — Neo4j同步
- `ObjectKgSyncService.java` — KG节点同步
- `CognitiveController.java` — 认知引擎
- `DorisRunner.java` — Doris连接（旗舰版only）
- `DuckDbController.java` — DuckDB（旗舰版only）

**示例**:
```java
@Service
@ConditionalOnProperty(name = "ecos.edition", havingValue = "enterprise", matchIfMissing = false)
public class OntologyKgSyncService {
    // ... Neo4j依赖的代码
}
```

或在GatewayApplication的ComponentScan excludeFilters中按Profile排除。

**验收**:
```bash
# 企业版启动 — Neo4j相关端点可用
curl -s http://localhost:8080/api/v1/causal/ontology/sync -X POST -H @/tmp/auth_header.txt -o /dev/null -w "%{http_code}"
# 期望: 200

# 标准版编译通过（Profile切换后Neo4j相关类不加载）
```

---

### A3: Docker Compose分层（1.5天，BE）

**目标**: 三个compose文件，按版本启用不同服务

**改文件**: 在项目根`/home/guorongxiao/databridge-v2/docker/`新建

**文件结构**:
```
docker/
  docker-compose.base.yml       — PG + ECOS Gateway (所有版本共用)
  docker-compose.standard.yml   — 只 extend base
  docker-compose.enterprise.yml — extend base + Neo4j
  docker-compose.ultimate.yml   — extend base + Neo4j + Doris
```

**docker-compose.base.yml**:
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
    volumes: ["./init-pg:/docker-entrypoint-initdb.d"]

  ecos-gateway:
    build:
      context: ..
      dockerfile: Dockerfile
      args:
        EDITION: ${ECOS_EDITION:-standard}
    ports: ["8080:8080"]
    environment:
      SPRING_PROFILES_ACTIVE: ${ECOS_EDITION:-standard}
    depends_on:
      postgres:
        condition: service_healthy
```

**docker-compose.enterprise.yml**:
```yaml
version: '3.8'
services:
  postgres:
    extends:
      file: docker-compose.base.yml
      service: postgres

  neo4j:
    image: neo4j:5
    environment:
      NEO4J_AUTH: neo4j/password
    ports: ["7474:7474", "7687:7687"]
    volumes: ["./neo4j-data:/data"]

  ecos-gateway:
    extends:
      file: docker-compose.base.yml
      service: ecos-gateway
    environment:
      ECOS_EDITION: enterprise
      SPRING_PROFILES_ACTIVE: enterprise
```

**验收**:
```bash
cd /home/guorongxiao/databridge-v2/docker

# 标准版启动 — 仅PG+ECOS
docker compose -f docker-compose.base.yml -f docker-compose.standard.yml up -d
sleep 30 && curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/health
# 期望: 200

# 企业版启动 — PG+Neo4j+ECOS
docker compose -f docker-compose.base.yml -f docker-compose.enterprise.yml up -d
sleep 30 && curl -s -o /dev/null -w "%{http_code}" http://localhost:7474
# 期望: 200 (Neo4j可达)
```

---

### A4: 发布脚本（0.5天，BE）

**目标**: 一个`build.sh` + 一个`deploy.sh`，参数控制版本

**改文件**: 项目根`/home/guorongxiao/databridge-v2/`新建

**build.sh**:
```bash
#!/bin/bash
EDITION=${1:-standard}
echo "Building ECOS ${EDITION} edition..."
cd "$(dirname "$0")"
source ~/ecos-env.sh
mvn -P${EDITION} install -DskipTests -q
echo "BUILD DONE: target/databridge-gateway.jar"
```

**deploy.sh**:
```bash
#!/bin/bash
EDITION=${1:-standard}
cd "$(dirname "$0")/docker"
case $EDITION in
  standard)
    docker compose -f docker-compose.base.yml -f docker-compose.standard.yml up -d
    ;;
  enterprise)
    docker compose -f docker-compose.base.yml -f docker-compose.enterprise.yml up -d
    ;;
  ultimate)
    docker compose -f docker-compose.base.yml -f docker-compose.ultimate.yml up -d
    ;;
esac
echo "DEPLOY DONE: http://localhost:8080"
```

**验收**:
```bash
bash /home/guorongxiao/databridge-v2/build.sh enterprise && echo "PASS" || echo "FAIL"
```

---

## Part B: 存量客户升级覆盖（1.5天）

### B1: 迁移适配器（1天，BE）

**目标**: 三种存量客户的数据源接入ECOS Pipeline

当前问题: 存量客户数据类型不同——制造型(MES/ERP)、贸易型(进销存)、项目型(PM)，但ECOS现在的Pipeline只支持JDBC Source。

**方案**: 不新增Maven模块。在现有`databridge-datanet/datanet-impl`下新增三个`Connector`实现。

**改文件**:
- `CsvConnector.java` — 新建，支持CSV/Excel导入（很多存量客户数据是导出的Excel）
- `RestApiConnector.java` — 新建，支持REST API源（老系统常有API）
- 修改`ConnectorFactory.java` — 注册两种新Connector

**验收**:
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"Admin@123"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])")

# CSV数据源接入
curl -s -X POST http://localhost:8080/api/v1/pipeline/definitions \
  -H "Authorization: Bearer ***  -H "Content-Type: application/json" \
  -d '{
    "name": "存量客户CSV导入",
    "nodes": [
      {"id":"n1","type":"SOURCE_CSV","config":{"filePath":"/data/migrate/projects.csv"}},
      {"id":"n2","type":"OUTPUT_OBJECT","config":{"entityCode":"Project"}}
    ],
    "edges":[{"from":"n1","to":"n2"}]
  }'
# 期望: 200 + pipelineId
```

### B2: 迁移脚本模板（0.5天，BE）

**目标**: 三个行业的数据迁移示例脚本+Ontology模板，让数据工程师直接改

**改文件**: 新建`/home/guorongxiao/databridge-v2/scripts/migrate/`

```
scripts/migrate/
  migrate_manufacturing.sql     — 制造型(MES→ECOS: 产线/工单/物料/质检)
  migrate_trading.sql           — 贸易型(进销存→ECOS: 供应商/合同/库存/回款)
  migrate_project.sql           — 项目型(PM→ECOS: 项目/合同/进度/产值)
```

**内容**: 每条脚本=建Ontology实体+DDL建表+Pipeline定义+种子数据示例。不写Java代码，全SQL+Pipeline JSON。

---

## Part C: 架构保护机制（精修阶段防崩塌）

> **时机**: 这些保护措施在精修开始**之前**建立。精修过程中任何代码变更必须通过这些门禁。

### C1: ArchUnit架构测试（1天，BE）

**目标**: 自动化测试强制DIKW分层依赖规则

**改文件**: 新建`databridge-common/databridge-common-api/src/test/java/com/chinacreator/gzcm/common/ArchitectureTest.java`

**强制规则（5条铁律）**:

```java
@Test
public void D层不能依赖K层_W层() {
    // D层（datanet/dccheng）不能import buszhi/aimod/worldmodel/cognitive
    noClasses()
        .that().resideInAnyPackage("..datanet..", "..dccheng..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("..buszhi..", "..aimod..", "..worldmodel..", "..cognitive..")
        .check(classes);
}

@Test
public void Controller只能调自己的Service() {
    // Controller不能跨模块调其他模块的Service
    noClasses()
        .that().resideInAPackage("..controller..")
        .should().accessClassesThat()
        .resideInAnyPackage("..service..")
        .andShould().accessClassesThat()
        .resideInAnyPackage("..repository..")
        .check(classes);  // 白名单: 同Module内
}

@Test
public void 禁止新增Maven模块() {
    // 禁止精修期间增加新<module>到根pom.xml
    // 通过检查pom.xml中module数量实现
}

@Test
public void 禁止新增Docker容器() {
    // 禁止修改docker-compose文件增加新服务
}

@Test
public void Controller必须通过Service访问数据库_不能直接JdbcTemplate() {
    // Controller注入JdbcTemplate必须走Service层
}
```

**验收**:
```bash
cd /home/guorongxiao/databridge-v2 && source ~/ecos-env.sh
mvn test -pl databridge-common/databridge-common-api -Dtest=ArchitectureTest
# 期望: Tests run: 5, Failures: 0
```

### C2: 精修期间的变更门禁（集成到pre-check.sh）

**目标**: 扩展已有的`~/pre-check.sh`，加架构规则检查

**改文件**: `~/pre-check.sh`

追加以下4项检查:

```bash
# === ARCHITECTURE GATES (精修阶段新增) ===

# 1. 禁止新增Maven模块
NEW_MODULES=$(grep -c "<module>" pom.xml 2>/dev/null)
if [ "$NEW_MODULES" -gt 16 ]; then  # 16=当前模块数
    echo "❌ ARCH: 禁止新增Maven模块 (当前:$NEW_MODULES, 基线:16)"
    exit 1
fi

# 2. 禁止新增Docker容器
if diff <(git show HEAD:docker/docker-compose.base.yml 2>/dev/null) docker/docker-compose.base.yml 2>/dev/null | grep -q "^+.*image:"; then
    echo "❌ ARCH: 禁止新增Docker容器"
    exit 1
fi

# 3. DIKW层次违规检测
if grep -r "import com.chinacreator.gzcm.buszhi" databridge-datanet/ --include="*.java" -q 2>/dev/null; then
    echo "❌ ARCH: D层依赖了K层 (buszhi)"
    exit 1
fi

# 4. Controller直接JdbcTemplate检测（新增）
DIRECT_JDBC=$(grep -r "JdbcTemplate" --include="*Controller.java" -l | grep -v "Health\|Gateway" | wc -l)
if [ "$DIRECT_JDBC" -gt 20 ]; then  # 基线容忍度
    echo "⚠️  ARCH: Controller直接使用JdbcTemplate数量: $DIRECT_JDBC (审核需确认)"
fi
```

### C3: 精修护城河——允许和禁止清单

**精修期间允许做的事**:
- ✅ 修复现有Controller的bug
- ✅ 优化SQL性能（不影响API契约）
- ✅ 增加工具类方法（不改变架构层次）
- ✅ 补充单元测试
- ✅ 改进日志输出
- ✅ 新增Ontology种子数据（Flyway迁移）

**精修期间禁止做的事**:
- 🚫 新增Maven模块
- 🚫 新增Docker容器/基础设施
- 🚫 新增Controller层（除非有PMO指令）
- 🚫 引入新依赖（在pom.xml加新dependency）
- 🚫 改变现有API路径或参数签名
- 🚫 Controller注入JdbcTemplate（必须走Service）
- 🚫 跨层import（Datanet import Buszhi等）
- 🚫 产出文档类交付物（只产出代码+测试）

---

## 优先级和排期

| Part | 内容 | 工时 | 优先 | 可并行 |
|------|------|:--:|:--:|:--:|
| A1 | Maven三Profile | 2d | P0 | BE |
| A2 | 功能开关@Conditional | 2d | P0 | BE(与A1串行) |
| A3 | Docker Compose分层 | 1.5d | P0 | BE(与A1并行) |
| A4 | 发布脚本 | 0.5d | P0 | BE(与A1并行) |
| B1 | 迁移适配器 | 1d | P1 | BE |
| B2 | 迁移脚本模板 | 0.5d | P1 | BE(与B1并行) |
| C1 | ArchUnit测试 | 1d | P0 | BE |
| C2 | pre-check扩展 | 0.5d | P0 | BE |
| **合计** | | **9d** | | 1人BE，1.5周 |

```
Day 1-2: A1(Maven profiles) + A3(Docker) + A4(脚本) 并行
Day 3-4: A2(功能开关)
Day 5:   B1+B2(迁移适配器)
Day 6:   C1+C2(架构保护)
```

---

## 禁止清单

1. **禁止创建新的Maven模块** — 三版本只裁剪现有模块，不新增
2. **禁止创建新的Git仓库或分支** — 一套代码在同一仓库
3. **禁止产出设计文档** — 三版本的差异体现在代码和配置中
4. **禁止改业务逻辑** — 分层裁剪不影响已有API行为
5. **禁止修改已有flyway migration编号** — 只增不改

---

## 验收方式

肖总亲自检查：
1. `bash build.sh standard && bash build.sh enterprise && bash build.sh ultimate` — 三版全编译通过
2. `bash deploy.sh standard` → `curl health` 返回200
3. `mvn test -Pstandard -Dtest=ArchitectureTest` — 5/5通过
4. `git log --since="2026-06-29"` — 每天≥1 commit
5. 存量客户CSV→Pipeline→Object全链路走通

> **精修阶段的总原则**: 架构保护措施（C1+C2）必须先建立。C1建立后，精修过程中任何违反架构规则的PR都会被ArchUnit测试拦截。这就是"防崩塌"的答案。
