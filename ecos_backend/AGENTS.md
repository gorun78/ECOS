# ECOS (Enterprise Cognitive Operating System) — Backend

> Java 17 + Spring Boot 3.2.2 + MyBatis + PostgreSQL | Maven multi-module | single fat-JAR deployment
> 代码路径: `/home/guorongxiao/ECOS/ecos_backend/` (WSL) | 文档路径: `/home/guorongxiao/ECOS/docs/`

## 产品定位

**ECOS** = 企业级认知操作系统。核心能力链路：**数据治理 → 知识图谱 → 大模型Agent落地**。

一套代码三套发布（Maven Profile 控制）：

| 版本 | 数据库 | 适用 |
|------|--------|------|
| standard (默认) | PostgreSQL | 中小企业 |
| enterprise | PostgreSQL + Neo4j | 中型企业，因果链>3层启用图谱 |
| ultimate/flagship | PostgreSQL + Neo4j + Doris | 大型企业，单表>100万行启用列存 |

### DIKW 分层与 DIKW×PDCA 方法论

产品方法论：业务目标 → 情景建模 → 本体设计 → 对象落地 → 质量管控 → 持续监控 (PDCA迭代)

DIKW 四层对应四个工作台+四个引擎：

| 层 | 模块 | 工作台 | 引擎 |
|----|------|--------|------|
| **D** 数据 | datanet | 数据工作台 | data-engine |
| **I** 信息 | buszhi | 本体工作台 | ontology-engine |
| **K** 知识 | dccheng | 知识工作台 | cognitive-engine |
| **W** 智能 | aimod | AI工作台 | cognitive-engine (共享) |

子系统缩写：sysman(系统管理) / g(数据·datanet) / zhi(本体·buszhi) / cheng(知识·dccheng) / ming(AI·aimod)

## Architecture

**单体架构**：`gateway` 是唯一的 Spring Boot 启动入口 (`GatewayApplication`)，通过 Maven 依赖导入所有业务模块。不是微服务。

### 模块依赖方向 (ArchUnit 守卫)

```
D/I (datanet, dccheng) ← K (buszhi, aimod) ← W (worldmodel, cognitive)
```
下层不能依赖上层。跨模块调用必须通过 `PipelineEvent`（在 `common-api`），禁止直接 import 其他模块的 Service。

### 四引擎架构 (建设中)

```
┌────────────────────────────────────────────────────────┐
│                  Gateway (:8080)                        │
│  路由转发 | 认证拦截 | 安全过滤链 | 统一入口            │
├──────────┬──────────┬──────────┬──────────────────────┤
│安全引擎   │数据引擎   │本体引擎   │认知引擎               │
│认证/授权  │数据源管理  │实体建模   │Agent/Mesh            │
│审计/合规  │管道/目录   │关系/版本   │知识图谱/RAG          │
│加密/脱敏  │质量/血缘   │提案/审批   │模型/NLQ              │
│ABAC/PBAC │调度/采集   │工作流/DQ  │分类/术语              │
└──────────┴──────────┴──────────┴───────────────────────┘
```

每个引擎有 api/impl/boot 三个子模块，boot 可独立启动（开发测试用），生产环境仅用 gateway。
每个引擎实现 `IEngine` 接口，暴露统一端点：`/api/v1/engine/{type}/health|config|status|tasks`

### 模块吸收 (Phase 5 已执行源码复制)

| 被吸收 | → 目标 | 状态 |
|--------|--------|------|
| portal | workspace | ✅ 源码已复制+旧目录已删除，JAR在~/.m2 |
| market | workspace | ✅ 源码已复制+旧目录已删除，JAR在~/.m2 |
| worldmodel | buszhi | ✅ 源码已复制+旧目录已删除，JAR在~/.m2 |
| cognitive | dccheng | ✅ 源码已复制+旧目录已删除，JAR在~/.m2 |
| ecos-kanban | runtime | ✅ 源码已复制+旧目录已删除，JAR在~/.m2 |

sysman-impl/sysman-boot仍Maven依赖旧模块JAR(portal-impl/market-impl/worldmodel-impl)。未来需更新为依赖目标模块。

## Key Files

- `GatewayApplication.java` — `@ComponentScan` 扫描所有模块包 + `excludeFilters` 排除引擎接管后的旧Controller副本（60+项）。**新增Controller到引擎时，必须同步在excludeFilters中排除旧位置副本**
- `common/common-api/` — 共享异常/DTO/`PipelineEvent`/`IEngine`接口。maven-enforcer-plugin 禁止依赖业务模块
- `gateway/src/main/resources/application.yml` — 主配置；`application-{standard,enterprise,flagship}.yml` 版本覆盖
- `ArchitectureTest.java` — 5条ArchUnit铁律，位于 `common/common-api/src/test/`

## Build

```bash
# 全量构建 (standard, 默认profile)
mvn clean install -DskipTests

# 指定版本构建
bash build.sh standard|enterprise|ultimate

# 单模块+依赖编译
mvn compile -pl gateway -am

# 单模块测试
mvn test -pl common/common-api

# 本地启动 (需PostgreSQL在localhost:5432)
mvn spring-boot:run -pl gateway -DskipTests

# WSL启动 (绕过Hermes UNC路径bug)
bash ~/start-gateway.sh
```

`build.sh` 会加载 `~/ecos-env.sh`（JAVA_HOME等）。

## Database

- **PostgreSQL 16**，库 `sys_man`，Docker凭据 `root/root`，本地 `postgres/postgres`
- **MyBatis**（非JPA — Hibernate/JPA auto-config 已排除）
- **Flyway 已禁用** (`spring.flyway.enabled: false`)
- Mapper XMLs: `classpath*:mapper/*.xml`
- Schema变更原则：只加不删列/表

## Exception Hierarchy

```
DataBridgeException (RuntimeException)
├── BusinessException
├── ForbiddenException
├── UnauthorizedException
├── ValidationException
├── NotFoundException
└── DataAccessException
```
所有异常携带 `httpStatus` + `errorCode`。GlobalExceptionHandler 在 `sysman-boot`。禁止 `throws Exception`。

## Hard Rules (ArchUnit 守卫)

1. **D/I → K → W 依赖方向** — 下层禁止import上层
2. **Controller只调自己模块Service** — 跨模块走 `PipelineEvent` 或 REST
3. **不新增Maven模块** — 基线13个
4. **不新增Docker容器** — compose文件image数已基线化
5. **Controller禁止直接用JdbcTemplate** — 必须走Service层

## Conventions

- **不改已有API路径或参数签名** — 只增不改
- **不绕过@Autowired走new** — JdbcTemplate始终构造器注入
- **API前缀**: 所有端点在 `/api/*`，认证白名单在 `application.yml` → `auth.whitelist.paths`
- **新增@ComponentScan包**: `com.chinacreator.gzcm.*` 下新包必须加入 `GatewayApplication`
- **JWT RS256**: 密钥在 application.yml (仅dev)；LLM功能需 `DEEPSEEK_API_KEY` 环境变量
- **hermes.engine.running: false** — Hermes引擎默认关闭，需显式启用
- **Controller模式**: `@RestController` + `@RequestMapping("/api/v1/xxx")` + 注入Service + 抛DataBridgeException + 返回 `ApiResponse.success()/badRequest()/notFound()`

## WSL Environment

| 组件 | 版本/路径 |
|------|----------|
| JDK (Linux native) | MS OpenJDK 17.0.14 (`~/.local/jdk17-linux/bin/java`) — **必须用此版本**，不能用~/jdk17(指向Windows java.exe) |
| JDK (Windows, via WSL interop) | MS JDK 17.0.17 (`/mnt/c/Program Files/Microsoft/jdk-17.0.17.10-hotspot`) — WSL bash自动转Windows路径导致classworlds错误 |
| Maven | 3.9.11 (`~/.local/apache-maven-3.9.11/bin/mvn`) |
| 构建快捷方式 | `source ecos_backend/env.sh && cd ecos_backend && mvn clean install -DskipTests -Dmaven.test.skip=true` |
| Maven仓库 | `~/.m2/repository` (WSL原生路径) |
| PostgreSQL | localhost:5432 (Windows侧，WSL可达) |
| Neo4j | localhost:7687 (Docker, enterprise/flagship) |
| Doris | FE:8030 + MySQL:9030 (Docker, flagship) |
| MinIO | :9000 (Docker) |
| OPA | :8181 (Docker) |

**WSL坑**：
- UNC路径Bug: Hermes重定向$HOME导致jansi.dll错误 → 用 `~/start-gateway.sh`（含`unset HOME`）启动
- Maven必须用WSL原生路径，不能用 `/mnt/d/` 下
- Git SSH过代理: `GIT_SSH_COMMAND="ssh -o ProxyCommand='nc -X 5 -x 127.0.0.1:7897 %h %p'"`

## Engine Layer Reality Check

四引擎全部已升级为REAL V1，Controller已迁入，均实现IEngine接口(生命周期+healthCheck+config)。

| 引擎 | api | impl | boot | V1-V10静态验证 |
|------|:---:|:----:|:----:|------|
| security-engine | 5接口 | 13Java(7Controller+4Service+EngineImpl) | port:18081 | V1-V5✅ V6❌ V7✅ V8✅ V9✅ V10✅ |
| data-engine | 11接口(重导出datanet-api) | 11Java(5Controller+4Service+EngineImpl) | port:18082 | V1-V5✅ V6❌ V7✅ V8✅(已修) V9✅ V10✅ |
| ontology-engine | 5接口 | 23Java(18Controller+3Service+EngineImpl+CopilotController) | port:18083 | V1-V5✅ V6❌ V7✅ V8✅ V9✅ V10✅(已修) |
| cognitive-engine | 4接口 | 17Java(16Controller+EngineImpl) | port:18084 | V1-V5✅ V6❌ V7✅ V8✅ V9✅ V10✅(已修) |

**V6(任务提交)四引擎均FAIL**：无引擎实现ITaskAwareEngine接口。Gateway EngineTaskController提供统一端点但绕过引擎契约。W2任务。
**V8 Data Engine /stop 已修复**：DataEngineStatusController增加POST /stop端点(2026-07-14)。
**V10 编译隔离已修复**：
- CopilotService从data-engine-api提升至common-api为ICopilotService，data-engine-api的CopilotService改为extends ICopilotService
- OntologyCopilotController改注入ICopilotService(来自common-api)，删除ontology-engine-impl对data-engine-api的依赖
- 删除cognitive-engine-impl对data-engine-api和ontology-engine-api的依赖(IGraphService已在common-api)
**四引擎Boot数据库配置全部正确**：均指向sys_man/postgres(2026-07-14验证)。
**Ontology/Cognitive引擎均为REAL V1**：有DB healthCheck(pingDb+countOntologies/countAgents)、AtomicReference<EngineStatus>生命周期、动态config Map。非骨架。

## Runtime-Core 瘦身现状

runtime-core 当前388个Java文件，PMO目标~100。需移出的包：

| 包 | 文件数 | 目标引擎 |
|----|:------:|----------|
| common/dataobjectmgr | 28 | data-engine |
| dataaccess | 33 | data-engine |
| common/datasourcemgr | 11 | data-engine |
| datadescription | 19 | data-engine |
| format | 15 | data-engine |
| transform | 15 | data-engine |
| metadata | 10 | data-engine |
| quality | 4 | data-engine |
| datasource | 5 | data-engine |
| kettle | 6 | data-engine |
| modelaccess | 5 | cognitive-engine |
| security | 4 | security-engine |
| **显式标注小计** | **~156** | |

保留在runtime的：agent/(42) + core/(19) + logging/(25) + config/(10) + bigdataengine/(8) + alert/(6) + git/(5) + mybatis/(1) + i18n/(~5) + database/(~4) ≈ 125
另有~70文件在common/子包中(kettle/legacy/lineage等)需逐案判定。

## Old Directories Cleanup

| 目录 | 原文件数 | 状态 |
|------|:----------:|------|
| portal/ | 5 | ✅ 已删除 (2026-07-14) |
| worldmodel/ | 16 | ✅ 已删除 (2026-07-14) |
| market/ | 6 | ✅ 已删除 (2026-07-14) |
| cognitive/ | 30 | ✅ 已删除 (2026-07-14) |
| ecos-kanban/ | 多文件 | ✅ 已删除 (2026-07-14) |

旧JAR仍在~/.m2/repository/供sysman-impl/sysman-boot依赖引用。未来需更新sysman依赖至目标模块后可清除旧JAR。

## Current Status (2026-07-14)

- **后端编译+启动**: ✅ 正常 (Linux JDK 17.0.14 @ ~/.local/jdk17-linux)
- **四引擎V1重构**: ✅ 全部完成 (所有EngineImpl有真实生命周期+DB healthCheck+动态config)
- **V1-V5 静态验证**: ✅ 四引擎全部PASS
- **V6 (ITaskAwareEngine)**: ❌ 四引擎均未实现 (W2任务)
- **V8 Data Engine /stop**: ✅ 已修复
- **V10 编译隔离**: ✅ 已修复 (CopilotService→ICopilotService→common-api, 跨引擎依赖已删除)
- **旧目录物理清理**: ✅ portal/worldmodel/market/cognitive/ecos-kanban 已删除
- **Boot DB配置**: ✅ 四引擎均指向sys_man/postgres
- **Maven构建环境**: ✅ Linux JDK17 native (解决了WSL UNC路径问题)
- **verify-engines.sh**: ✅ 已创建于 ~/verify-engines.sh (需Gateway运行后执行)

## Key Scripts (WSL ~/)

| 脚本 | 用途 |
|------|------|
| `~/start-gateway.sh` | Gateway启动，含`unset HOME`绕Hermes UNC bug，默认enterprise profile |
| `~/verify-engines.sh` | 四引擎V1-V10闭环验证(需Gateway运行)，用法: `bash ~/verify-engines.sh [--jwt TOKEN]` |
| `~/ecos-env.sh` | 环境变量(JAVA_HOME/M2_HOME)，别名ecos-be/fe/build/test/up/check |
| `~/pre-check.sh` | 提交前5防线：编译→tsc→ArchUnit→Enforcer→API契约测试 |
| `ecos_backend/env.sh` | Linux JDK17+Maven环境变量(source后即可用mvn) |

## Roadmap Context

已完成: Sprint 1-19 (全模块API审计+对接修复 ✅)、四引擎REAL V1+Controller迁移 ✅、V8/V10修复 ✅、旧目录清理 ✅
进行中: 后端架构重整 (七域四引擎PMO指令) — W1已完成, W2(ITaskAwareEngine+Gateway瘦身)待启动
待做: ITaskAwareEngine(4引擎)、Gateway瘦身、Runtime瘦身(runtime-core 425→~100)、前端引擎管控页、公司场景联调(信科/江粮)

详细看板: `/home/guorongxiao/ECOS/docs/00-Kanban/ECOS-总看板.md`
移交文档: `/home/guorongxiao/ECOS/docs/ECOS-项目移交文档-20260709.md`
引擎指令: `/home/guorongxiao/ECOS/docs/00-Kanban/ECOS-后端架构重整-七域四引擎-PMO指令.md`
