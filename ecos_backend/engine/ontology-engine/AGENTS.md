# ontology-engine — 本体引擎

> 端口: **18083** | PMO: **ecos-arch** | 依赖: PostgreSQL, kb-engine-api (KG同步)

## 我负责的
- 本体模型（实体类型、属性、关系类型定义）
- 对象实例（本体模型下的实例对象CRUD）
- 版本管理（本体模型版本、对象版本）
- 工作流（审批、发布流程）
- 领域管理（业务域定义和隔离）
- 本体→KG同步（对象变更自动推送Neo4j）

## 我暴露的端点
| 端点 | 方法 | 用途 |
|------|------|------|
| /api/v1/ecos/ontologies | * | 本体模型CRUD |
| /api/v1/ecos/entities | * | 实体类型定义 |
| /api/v1/ecos/domains | * | 业务域管理 |
| /api/v1/ecos/versions | GET | 版本查询 |
| /api/v1/ecos/workflows | * | 工作流管理 |
| /api/v1/ecos/objects | * | 对象实例CRUD |

## 我的数据库表
- 本体模型表、实体类型表、对象实例表、关系表、版本表、工作流表

## 我依赖的外部端点
| 引擎 | 端点 | 用途 |
|------|------|------|
| kb-engine | POST :18086/api/v1/kb/graph/sync | 对象→KG同步 |

## 禁止
1. 不直接写Neo4j（通过kb-engine的graph/sync端点）
2. 本体模型变更不自动生效（需要版本发布流程）
3. 对象实例不支持物理删除（只标记逻辑删除）

## Modules

> 原 3 份子模块 AGENTS.md 合并入此章节。原文件已删除，git 历史保留。

### ontology-engine-api（接口层）

> 端口: 共享父 18083 | 契约唯一原则

- **接口/服务层契约**：承载本体配置 / 版本 / 图谱 / 工作流 / Git / Copilot / ActionType 的 `interface` + `model` 契约。唯一定义契约的模块，impl/boot 仅参考（API 只增不改）。

#### 主要 code（契约清单）
- `OntologyConfigService` — 本体配置契约
- `OntologyGitService` — 本体 Git 同步契约（委托 runtime 的 Git 服务，禁止本模块操作 git）
- `OntologyGraphService` — 本体检索契约（对象→KG 同步入口）
- `OntologyCopilotService` — 本体 Copilot 契约
- `OntologyWorkflowService` — 工作流（审批/发布）契约
- `ActionTypeService` + `model/ActionType` — 动作类型契约
- `model/ExtractedSubGraph` — 抽取子图模型（供 ai-engine `KnowledgeExtractorService` 复用）

#### 调用链
- → 上层: 无（契约层只读）
- ← 被调方: impl 的 `OntologyConfigController` / `OntologyGitController` / `OntologyGraphController` / `OntologyCopilotController` / `OntologyWorkflowController` / `ActionTypeController` 等
- 跨引擎: 顶层依赖 `kb-engine-api`（KG 同步），本 api 模块只暴露方法签名（`KgSyncService` 注入），不直接调 impl

#### 禁止
- 不改既有方法签名（API 只增不改）
- 不在此模块写实现类（带 `interface` 与 `model` 的池外禁止）
- 不 import `*-engine-impl`（架构铁律 2.1）
- 不硬编码 token / BOD / metadata；不在此 api 内用 `@Value` 字符串注入
- 对象物理删除建议在 API 层改为 `logicallyDelete`（顶层红线 #3：对象实例只标记逻辑删除）
- 实体新提自有 driver 禁止（Driver 收敛 `runtime-access`）

### ontology-engine-impl（实现层）

> 端口: 共享父 18083 | 测试: `Wave31OntologyConvergenceTest` + `ArchitectureTest` 守门

- **实现层（业务）**：承载本体建模、对象 CRUD、版本管理、工作流、域管理、本体→KG 同步、术语表、自动发现、Git 同步。

#### 主要 code（控制器）
- `OntologyController` / `OntologyCompatController` — 本体模型 CRUD + 兼容接口（双路径）
- `OntologyDomainApiController` + `OntologyDomainController` — domain 入驻（注册 + listAll + get/cascade）
- `OntologyVersionController` / `OntologyVersionSimpleController` — 版本管理
- `OntologyWorkflowController` / `WorkflowController` — 审批/发布/工作流
- `OntologyConfigController` / `OntologyMappingController` — 配置 + 映射（含 `MappingStore.store` round-trip）
- `OntologyGraphController` / `OntologyGitController` — 本体检索与 Git 同步
- `OntologyCopilotController` / `OntologyExportController` — Copilot / 导出
- `OntologyDataController` / `OntologyProposalController` / `OntologyRuleController` / `OntologyRelationshipController` / `OntologyPropertyController` — 对象/提案/规则/关系/属性
- `GlossaryController` / `AutoDiscoverController` / `ActionTypeController` / `FunctionController` / `LineageController` — 术语/自动发现/动作/函数/血缘
- `OntologyEngineStatusController` — `/api/v1/engine/ontology/*` 健康检查与统计
- `Neo4jGraphService` / `OntologyKgSyncService` — 对象→KG 同步（**合规风险区，必须收敛 `runtime-access`**；现状 `import org.neo4j.driver.*` 需在 Wave 5 之前统一迁到 runtime；在此之前**新代码禁止再 new Neo4j Driver**）

#### 调用链
- → 同 engine api: 注入 `OntologyConfigService` / `OntologyGitService` / `OntologyGraphService` / `OntologyCopilotService` / `OntologyWorkflowService` / `ActionTypeService`
- → kb-engine: KG 同步走 **REST** `POST :18086/api/v1/kb/graph/sync`（不 import kb-engine-impl，架构铁律 2.1）
- → 引擎外: 不 import `*-engine-impl`；外部图查询通过 `Neo4jGraphService` 封装（隔离 driver 路径，TODO Wave5 迁 runtime-access）
- ← 被调方: gateway 聚合加载 + 前端 `/api/v1/ecos/{ontologies|entities|domains|versions|workflows|objects}`

#### 端点 / 补丁
- 兼容双路径: `/api/v1/ecos/{...}` + `/api/v1/engine/ontology/*`（健康检查 + 统计 + 域入驻）
- 映射一致性校验（PMO-B2 T2）: `POST /api/v1/ontology/mappings/validate`（入参 `OntologyMappingValidateDTO`，出参 `ApiResponse<OntologyMappingValidationVO>`）— DW 元数据经 `DataNetResourceClient` 走 data-engine REST；失败返回 200 + `valid=false` + `issues[]` + `rejectCode=INVALID_MAPPING`
- 映射契约查询（PMO-B3-1 T4）: `GET /api/v1/ontology/entity-mappings?ontologyId=`（出参 `ApiResponse<List<OntologyMappingVO>>`）— 供 kb-engine 图谱实例抽取（B3-2）消费
- Domain 入驻落地（`OntologyDomainApiController`，`/api/v1/engine/ontology/jurisdiction`）

#### 禁止
- 不直接写 Neo4j（通过 kb-engine 的 `graph/sync` 端点，顶层红线 #1）
- 本体模型变更不自动生效（必须走版本发布流程，顶层红线 #2）
- 对象实例不支持物理删除（只标记逻辑删除，顶层红线 #3）
- 不 import `kb-engine-impl` / `cognitive-engine-impl` / `ai-engine-impl`（架构铁律 2.1 = 验收失败）
- 现状 `Neo4jGraphService` / `OntologyKgSyncService` 仍直接 `import org.neo4j.driver.*` — **新代码不得再 new `Neo4jDriver` 或 `Neo4jRouter`**，必须走 `runtime-access` 提供的 `Neo4jClient` Bean
- 不硬编码 token / BOD / metadata（KG 凭据走 `runtime-access`/`application.yml`，不在 Service 字面量）
- 实体新提自有 driver 禁止（治理）
- 不引入 Flyway（`spring.flyway.enabled: false`，schema 变更走 ADR）

### ontology-engine-boot（启动器）

> 端口: 共享父 18083 | 仅开发调试用 | 生产统一走 gateway 的 `excludeFilters` 聚合加载

- `OntologyEngineApplication.java`（唯一 file）：

```java
@SpringBootApplication(exclude = {
    HibernateJpaAutoConfiguration.class,
    JpaRepositoriesAutoConfiguration.class
})
@EnableScheduling
@ComponentScan(basePackages = {
    "com.chinacreator.gzcm.engine.ontology",
    "com.chinacreator.gzcm.buszhi",
    "com.chinacreator.gzcm.runtime"
})
public class OntologyEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(OntologyEngineApplication.class, args);
    }
}
```

> 未标 `@MapperScan`，依赖 mybatis-spring 自动扫描（mapper XML: `classpath*:mapper/*.xml`）。
> `@ComponentScan` 含 `com.chinacreator.gzcm.buszhi`（历史包路径，保留兼容，**不要删除**）。

#### 调用链
- ComponentScan 扫 `ontology/` + `buszhi/`（历史包）+ `runtime/` 三个 package
- 排除 `HibernateJpaAutoConfiguration` + `JpaRepositoriesAutoConfiguration`
- 被调方: 开发环境独立 JVM；生产 gateway 通过 `excludeFilters` 排除本类同名副本（已有 60+ 项）

#### 端点 / 补丁
- Phase 1 已 startup，Phase 2+ boot 不增端点，端点池在 impl（27 个 Controller）
- 新增端点流程：impl 加 Controller → gateway 的 `VersionPrefixRewriteFilter` → `sysman/SecurityConfig` permitAll 双路径 → `ClearanceInterceptor` 双路径豁免 → `mvn install -DskipTests` 验证

#### 禁止
- 不在此模块新加业务代码
- 不删除 `@SpringBootApplication(exclude=...)` 的 JPA 排除（架构铁律 4）
- 不硬编码 token / BOD / metadata（KG 凭据走 `runtime-access` + `application-*.yml`）
- 不在此 boot 内 `new Driver` / `new JdbcTemplate` / `new Neo4jDriver`（Driver 收敛 `runtime-access`）
- 不修改 `ComponentScan` 删 `com.chinacreator.gzcm.buszhi`（历史兼容包，删掉会有 class 缺失）
- 不启用 Flyway
- 生产发布禁止以 boot 启动，生产只走 gateway
