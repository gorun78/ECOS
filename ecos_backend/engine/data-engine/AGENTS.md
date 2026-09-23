# data-engine — 数据引擎

> 端口: **18082** | PMO: **ecos-be** | 依赖: PostgreSQL

## 我负责的
- 数据源管理（DB连接、文件、API）
- 数据管道（采集、清洗、转换、入湖）
- 数据目录（表结构、字段、统计）
- 数据血缘（字段级、表级、跨系统）
- 数据质量（规则配置、检查执行、报告）
- 数据管道任务调度

## 我暴露的端点
| 端点 | 方法 | 用途 |
|------|------|------|
| /api/v1/engine/data/health | GET | 健康检查 |
| /api/v1/engine/data/pipeline | POST | 管道CRUD |
| /api/v1/engine/data/lineage | GET | 血缘查询 |
| /api/v1/engine/data/quality | POST | 质量检查 |
| /api/v1/engine/data/query | POST | 数据查询 |
| /api/v1/engine/data/settings | GET/PUT | 引擎配置 |
| /api/v1/engine/data/layers | GET | 数据分层 |
| /api/v1/engine/data/layers/{layer}/resources/{id}/rows | GET | 分层资源实例行增量读取（水位线，PMO-B3-1 T2） |
| /api/v1/engine/data/layers/{layer}/resources/{id}/sample | GET | 分层资源实例行抽样（PMO-B3-1 T2） |
| /api/v1/engine/data/functions | GET | 计算函数 |
| /api/v1/engine/data/copilot | POST | 数据Copilot |
| /api/v1/datanet/datalake/unstructured | POST | 登记非结构化原文对象（B5-1，D5 生产者侧；组装 key `raw/unstructured/{source}/{docId}/{fileName}`，登记 RAW/UNSTRUCTURED/LAKE_OBJECT） |
| /api/v1/datanet/metadata/resources | POST | 通用数据资源登记（B5-1；供知识工作台登记解析文本为 CURATED；强制校验 layer/zone 合法性矩阵） |

## 我的数据库表
- 数据源定义表、管道任务表、血缘关系表、质量规则表
- 复用的PG业务表（由管道写入）

## 我依赖的外部端点
无。data-engine是底层引擎。

## 禁止
1. 不直接操作其他引擎的表
2. 管道不执行超过30分钟的同步任务
3. 血缘不追踪Neo4j内的关系（那是kb-engine的事）

## Modules

> 原 3 份子模块 AGENTS.md 合并入此章节。原文件已删除，git 历史保留。

### data-engine-api（接口层）

> 端口: 共享父 18082 | 契约唯一原则

- **接口层/服务层契约**：承载 DataSource / Pipeline / Catalog / Metadata / DQ / Copilot / UDF 的 `interface` + `model` 契约。新增 DSL/DTO 必须走 api（impl 不直接定义新契约）。

#### 主要 code（契约清单）
- `DataSourceService` — 数据源 CRUD + 连接测试契约
- `PipelineService` + `PipelineDefinition` / `PipelineNode` / `PipelineExecution` — 管道契约与 ORM 模型
- `PipelineTaskService` / `PipelineFunctionService` — 管道任务 / 函数契约
- `CatalogService` / `MetadataService` / `CategoryService` — 数据目录、元数据、类目
- `QualityService` — 数据质量契约
- `UdfService` — 用户注册函数（计算函数）契约
- `CopilotService` — 数据 Copilot 契约（databench 集成调用方）
- `QueryExecutionService` — 数据查询契约
- `model/DataLayer` — 数据分层模型

#### 调用链
- → 上层: 无（底层契约，被 impl 与 gateway 引用）
- ← 被调方: impl 的 `DataSourceController` / `PipelineTaskController` / `PipelineFunctionController` / `QualityController` / `DqController` / `CatalogController` / `LineageCompatController` 等
- 跨引擎: data-engine 不调其他引擎（架构铁律 0.3：data-engine 是底层引擎，统一被调用，禁止反调）

#### 禁止
- 不改既有方法签名（API 只增不改）
- 不在此模块加业务/实现类（带 `interface` 与 `entity` 的池外禁止）
- 不 import `*-engine-impl`（契约不依赖业务实现，违反架构铁律 2.1 = 验收失败）
- 不硬编码 token / BOD / metadata（连接串、用户名走 `JdbcTemplate` 注入，不走 `@Value` 字面量）
- 实体新提自有 driver 禁止（Driver 收敛 `runtime-access`，本模块不得 new）
- SQL 入参必须强类型/DTO，禁止 `Map<String, Object>` 作为必填接口（架构铁律 后端 1.5）

### data-engine-impl（实现层）

> 端口: 共享父 18082 | DB: PostgreSQL | 测试: 7 test class / 28 case P0-2 反向

**实现层（业务）**：承载数据源 CRUD、管道调度、血缘、DQ、查询、UDF、Copilot 全部 Controller 与 Service/DAO 实现。

#### 主要 code（控制器/服务/DAO）
- `DataSourceController` — 数据源 CRUD + 连接测试
- `PipelineTaskController` / `PipelineFunctionController` / `LineageCompatController` — 管道任务/函数/血缘兼容
- `QualityController` / `DqController` — 数据质量（DQ 检查 + 报告）
- `CatalogController` / `CategoryController` / `DataLayerController` — 目录/类目/分层
- `QueryController` / `MetadataController` / `SchemaChangeController` — 查询/元数据/变更
- `PipelineCopilotController` / `UdfController` — 数据 Copilot + 自定义函数
- `DataEngineStatusController` — `/api/v1/engine/data/*` 健康检查与统计
- `transform/impl` 子包：`TransformChain` + 统计

#### 调用链
- → 同 engine api: 注入 `DataSourceService` / `PipelineService` / `CatalogService` / `QualityService` 等
- → runtime: 共用 runtime-access 的 JdbcTemplate/MinIO/Git（`PipelineGitController` 通过 runtime 委托，**不自建 GitClient**）
- → 跨 engine: **无**（data-engine 是底层引擎，不 import 其他 engine-impl；架构铁律 2.1）
- ← 被调方: databench 前端（`/datanet` 代理）+ gateway 聚合加载

#### 端点 / 补丁
- 数据源 CRUD: `/api/v1/data/datasources`（与 LinearLayout 前端 `DataSourceApi` 对应）
- 管道 CRUD: `/api/v1/engine/data/pipeline`
- 血缘查询: `/api/v1/engine/data/lineage` + `/api/data/lineage`（双路径）
- UDF / 分层 / 设置 / Copilot: `/api/v1/engine/data/*/...`

#### 禁止
- 管道不执行超过 30 分钟的同步任务（顶层红线 #2）
- 不直接 import `kb-engine-impl` / `ontology-engine-impl` / `cognitive-engine-impl`（架构铁律 2.1）
- 不跨引擎操作其他 engine 的表（血缘不追 Neo4j，那是 kb-engine 的事，顶层红线 #3）
- 不 import `*-engine-impl`；不要在 `PipelineGitController` 内 new 出 `JGit` / `GitClient`，必须走 runtime-access 提供的 Git 服务
- 不硬编码 token / BOD / metadata（连接串/凭据走 `DataSourceEntity` + PG `DataSourceService` 加密存储，不在 Controller 字面量）
- 实体新提自有 driver（治理）— 禁止 new 出 `org.postgresql.PGConnection` 或自建 `DriverManager`，统一经 runtime-access
- 不引入 Flyway（`spring.flyway.enabled: false`，schema 变更走 ADR）

### data-engine-boot（启动器）

> 端口: 共享父 18082 | 仅开发调试用 | 生产统一走 gateway 的 `excludeFilters` 聚合加载

- `DataEngineApplication.java`（唯一 file）：

```java
@SpringBootApplication(exclude = {
    HibernateJpaAutoConfiguration.class,
    JpaRepositoriesAutoConfiguration.class
})
@EnableScheduling
@EnableAsync
@ComponentScan(basePackages = {
    "com.chinacreator.gzcm.engine.data",
    "com.chinacreator.gzcm.runtime"
})
public class DataEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(DataEngineApplication.class, args);
    }
}
```

> 未标 `@MapperScan`，依赖 mybatis-spring 自动扫描（mapper XML: `classpath*:mapper/*.xml`）。
> 新增 `@Mapper` 接口需被自动扫描到；若扫不到，在本目录新增 `DataBootConfig` 配置类（**不要**改 Application），标 `@MapperScan("com.chinacreator.gzcm.engine.data.**.dao")`）。
> **不要**自己 `new DataSource`，PG 凭据走 `application.yml`。

#### 调用链
- ComponentScan 扫 `data/` + `runtime/` 两个 package
- 排除 `HibernateJpaAutoConfiguration` + `JpaRepositoriesAutoConfiguration`（MyBatis 项目禁 JPA）
- `@EnableScheduling` + `@EnableAsync`: 管道任务调度 + 异步（注意：Phase 1 起已计划委托 `runtime-task`；本 boot 仍保留切面以兼容当前代码）
- 被调方: 开发环境独立 JVM；生产 gateway 通过 `excludeFilters` 排除本类同名副本（已有 60+ 项）

#### 端点 / 补丁
- Phase 1 已 startup，Phase 2+ boot 不增端点，端点池在 impl 子模块（各 Controller）
- 新增端点流程：impl 加 Controller → gateway/VersionPrefixRewriteFilter → `sysman/SecurityConfig` permitAll 双路径 → `ClearanceInterceptor` 双路径豁免 → `mvn install -DskipTests` 启动 gateway 验证

#### 禁止
- 不在此模块新加业务代码（业务在 impl，接口在 api）
- 不删除 `@SpringBootApplication(exclude=...)` 的 JPA 排除（架构铁律 4 + 数据层 3.1）
- 不硬编码 token / BOD / metadata（凭据走 `application-*.yml`、应用开关走 environment profile）
- 不在此 boot 内 `new DataSource` / `new JdbcTemplate`（PG 连接池随 `runtime-access` 注入）
- 不启用 Flyway
- 调度任务：Phase 2+ 迁移到 `runtime-task` 后，本 boot 的 `@EnableScheduling` 必须降级移除；迁移前不删
- 生产发布禁止以 boot 启动，生产只走 gateway

## DQ 治理 API 路径表（PMO-48-A T3, 2026-09-10）

| 端点 | 方法 | 状态 | 用途 |
|------|------|------|------|
| /api/v1/dq/rules | GET | Phase 1 可用 | 规则列表（category/status/domain/ruleType/targetKind/keyword 过滤 + pageNum/pageSize 分页） |
| /api/v1/dq/rules/{id} | GET | Phase 1 可用 | 详情 + 版本历史（ecos_dq.dq_rule_version） |
| /api/v1/dq/rules/dimension-registry | GET | Phase 1 可用 | 6 维 rule_type 映射注册表（COMPLETENESS/ACCURACY/CONSISTENCY/FRESHNESS/UNIQUENESS/VALIDITY） |
| /api/v1/dq/rules | POST | 405 (Phase 2 再开) | 创建规则，写尝试计审计 |
| /api/v1/dq/rules/{id} | PUT/DELETE | 405 (Phase 2 再开) | 更新/删除规则，写尝试计审计 |

- Controller: `data-engine-impl/.../quality/controller/DqGovernanceController.java`（直接映射 /api/v1/dq，VersionPrefixRewriteFilter 中 dq 为 KEEP 不 rewrite）
- 数据层: MyBatis `DqRuleMapper` 读 `ecos_dq.dq_rule`（显式 schema 前缀；gateway `application.yml` 有 `map-underscore-to-camel-case`）
- 三滤波器: VersionPrefixRewriteFilter KEEP + SecurityConfig/ClearanceInterceptor 双路径（/api/v1/dq/** 与 /api/dq/**）+ application.yml auth.whitelist — T2 已交付
- 安全集成: `DqSecurityService`（Bean: `ecosDqSecurityService`，对齐 PipelineSecurityService 先例）— 响应前 mask 脱敏敏感字段（phone/mobile/idcard/bank_card/amount 等）；读/写操作异步 `POST /api/security/audit/log`；security-engine 不可用时默认脱敏/默认 DENY
- Bean 命名: `ecosDqGovernanceService`（Service 接口 `DqGovernanceService` 在 data-engine-api，不 implements 既有接口，防多 Bean 冲突）
- 完整方案: `docs/3-data/数据质量管理方案.md` §2（6 维度）；指令: `docs/3-data/PMO-48-A-数据质量基础设施.md`

## DQ 评分 SPI + Score API（PMO-48-B T8, 2026-09-10）

| 端点 | 方法 | 用途 |
|------|------|------|
| /api/v1/dq/scores?assetType=TABLE&assetId=xxx | GET | 当前资产评分（无评分 404） |
| /api/v1/dq/scores/trend?assetType=TABLE&assetId=xxx&days=30 | GET | 趋势（按天 × 维度，上限 365） |
| /api/v1/dq/scores/grade?grade=F | GET | 按等级扫描（A/B/C/D/F，空=全部） |
| /api/v1/dq/scores/system | GET | 系统级健康度 |
| /api/v1/dq/scores/recompute?assetType=TABLE&assetId=xxx | POST | 手动触发重算 |

### 6 个评估器 Bean（`data-engine-impl/.../quality/scoring/impl/`）

| Bean | 维度 | 默认权重 | rule_types |
|------|------|---------|-----------|
| `DqCompletenessEvaluator` | COMPLETENESS | 30 | NOT_NULL / PRESENCE |
| `DqAccuracyEvaluator` | ACCURACY | 25 | FORMAT / RANGE / REGEX |
| `DqConsistencyEvaluator` | CONSISTENCY | 15 | CONSISTENCY / STATISTICAL |
| `DqUniquenessEvaluator` | UNIQUENESS | 15 | UNIQUE |
| `DqValidityEvaluator` | VALIDITY | 10 | VALIDITY / ENUM |
| `DqFreshnessEvaluator` | FRESHNESS | 5 | FRESHNESS / TIMELINESS |

### 等级阈值（A ≥ 0.95 / B ≥ 0.85 / C ≥ 0.70 / D ≥ 0.50 / F < 0.50）

- 加权公式：`Σ(维度分 × defaultWeight) / Σ(defaultWeight)` → 写 `dq_score_asset.overall_score`
- `dq_score_snapshot` 每维度一行（scope_type=TABLE，weight=defaultWeight）
- `dq_score_asset` 按 (asset_type, asset_id) UPSERT，`last_evaluated_at = NOW()`
- 评分窗口：仅取最近 1h 内 `dq_rule_check`（`executed_at >= NOW() - interval '1 hour'`），避免跨窗污染
- 6 边界：sample 0 行 → 1.0；超时（Phase 3）→ 0.0 + detail.status=TIMEOUT；敏感字段 detail.values=***；executed_at null → 1.0（Freshness only）

### 关键文件

- SPI 抽象: `data-engine-api/.../quality/scoring/{DqDimension, DimensionEvaluator, DimensionScore, ScoringContext}.java`
- 引擎: `data-engine-impl/.../quality/scoring/service/DqScoreEngine.java`（@Component，构造器注入 `List<DimensionEvaluator>`）
- 服务: `data-engine-impl/.../quality/service/DqScoreServiceImpl.java`（@Service("ecosDqScoreService")+@Transactional，含 audit 安全卡）
- 控制器: `data-engine-impl/.../quality/controller/DqScoreController.java`（@RequestMapping("/api/v1/dq/scores")）
- VO: `data-engine-api/.../quality/model/{DqAssetScoreVO, DqScoreTrendVO, DqScoreSystemVO}.java`
- 方案: `docs/3-data/PMO-48-数据质量管理方案.md` §4

### 铁律

1. 评估器不写库（只算+返回，落库在 DqScoreService）
2. Phase 2 简化：评估器读 `dq_rule_check.{passed, total_rows, failed_rows, pass_rate}`，不拉 IStorageAdapter 样本（Phase 3 监控调度器再放开）
3. 不 implements 既有接口；不 throws 受检异常；Bean 名 `ecosDqScoreService`（铁律 1.3）
4. 安全卡（铁律 2.4 #3/#5）：评估器 detail.sensitiveMasked=true 当 target_field 命中；Service 每次 recompute 异步 `auditWrite(DQ_SCORE_RECOMPUTE, *:, SUCCESS)`
