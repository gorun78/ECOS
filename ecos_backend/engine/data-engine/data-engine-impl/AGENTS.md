# data-engine-impl — 数据引擎·实现层

> 子模块: data-engine/impl | 端口: 共享父模块 18082 | DB: PostgreSQL
> 上层: 见 ../AGENTS.md（data-engine 顶层）

## 本模块干什么
- **实现层（业务）**：承载数据源 CRUD、管道调度、血缘、DQ、查询、UDF、Copilot 全部 Controller 与 Service/DAO 实现。
- 测试充分（7 个 test class / 28 case P0-2 反向）：`DataSourceControllerTest` / `PipelineControllerTest` / `DataSourceDaoItLikeTest` / `TransformChainStatisticsTest` / `TransformControllerTest` / `TransformStatisticsTest` / `ArchitectureTest`。

## 主要 code（控制器/服务/DAO）
- `DataSourceController` — 数据源 CRUD + 连接测试。
- `PipelineTaskController` / `PipelineFunctionController` / `LineageCompatController` — 管道任务/函数/血缘兼容。
- `QualityController` / `DqController` — 数据质量（DQ 检查 + 报告）。
- `CatalogController` / `CategoryController` / `DataLayerController` — 目录/类目/分层。
- `QueryController` / `MetadataController` / `SchemaChangeController` — 查询/元数据/变更。
- `PipelineCopilotController` / `UdfController` — 数据 Copilot + 自定义函数。
- `DataEngineStatusController` — `/api/v1/engine/data/*` 健康检查与统计。
- `transform/impl` 子包：`TransformChain` + `TransformChainStatisticsTest` 覆盖 transform 链与统计。

## 调用链（只读 + 调谁）
- → 同 engine api: 注入 `DataSourceService`/`PipelineService`/`CatalogService`/`QualityService` 等接口（来自 `data-engine-api`）。
- → runtime: 共用 runtime-access 的 JdbcTemplate/MinIO/Git（证据：`PipelineGitController` 通过 runtime 委托，不自建 GitClient）。
- → 跨 engine: **无**（data-engine 是底层引擎，不 import 其他-engine-impl；架构铁律 2.1）。
- ← 被调用方: databench 前端（`/datanet` 代理）+ gateway 聚合加载。

## 端点 / 补丁
- 数据源 CRUD 路径：`/api/v1/data/datasources`（与 LinearLayout 前端 `DataSourceApi` 对应）。
- 管道 CRUD：`/api/v1/engine/data/pipeline`（Controller `PipelineTaskController`）。
- 血缘查询：`/api/v1/engine/data/lineage` + `/api/data/lineage`（`LineageCompatController` 双路径）。
- 计算函数 / 数据分层 / 设置 / Copilot：`/api/v1/engine/data/*/...`（`DataEngineStatusController`）。
- 示例（DataSourceController 片段）：
```java
@RestController
@RequestMapping("/api/v1/data/datasources")
public class DataSourceController {
    private final DataSourceService dao;
    @PostMapping
    public ApiResponse<DataSourceVO> add(@Valid @RequestBody DataSourceSaveDTO dto) {
        // 调用 DataSourceService.addDataSource(...)；DTO/VO 走 data-engine-api
    }
}
```

## 禁止
- 管道不执行超过 30 分钟的同步任务（data-engine AGENTS.md 顶层红线 #2）。
- 不直接 import `kb-engine-impl` / `ontology-engine-impl` / `cognitive-engine-impl`（架构铁律 2.1，违反 = 验收失败）。
- 不跨引擎操作其他 engine 的表（data-engine 红线 #1：血缘不追踪 Neo4j，那是 kb-engine 的事）。
- 不 import `*-engine-impl`；不要在 `PipelineGitController` 内 new 出 `JGit` / `GitClient`，必须走 runtime-access 提供的 Git 服务。
- 不硬编码 token / BOD / metadata：连接串/凭据走 `DataSourceEntity` + PG `DataSourceService` 加密存储，不在 Controller 字面量。
- 实体新提自有 driver（治理）— 禁止 new 出 `org.postgresql.PGConnection` 或自建 `DriverManager`，统一经 runtime-access。
- 不引入 Flyway（`spring.flyway.enabled: false`，schema 变更走 ADR）。
