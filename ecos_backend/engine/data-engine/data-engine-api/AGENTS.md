# data-engine-api — 数据引擎·服务接口层

> 子模块: data-engine/api | 端口: 共享父模块 18082 | DB: PostgreSQL
> 上层: 见 ../AGENTS.md（data-engine 顶层）

## 本模块干什么
- **接口层/服务层契约**：承载 DataSource/Pipeline/Catalog/Metadata/DQ/Copilot/UDF 的 `interface` + `model` 契约。
- 新增 DSL/DTO 必须走 `api`（impl 不直接定义新契约）。

## 主要 code（接口/实体）
- `DataSourceService` — 数据源 CRUD + 连接测试契约。
- `PipelineService` + `PipelineDefinition` / `PipelineNode` / `PipelineExecution` — 管道契约与 ORM 模型。
- `PipelineTaskService` / `PipelineFunctionService` — 管道任务 / 函数契约。
- `CatalogService` / `MetadataService` / `CategoryService` — 数据目录、元数据、类目。
- `QualityService` — 数据质量契约。
- `UdfService` — 用户注册函数（计算函数）契约。
- `CopilotService` — 数据 Copilot 契约（databench 集成调用方）。
- `QueryExecutionService` — 数据查询契约。
- `model/DataLayer` — 数据分层模型。

## 调用链（只读 + 调谁）
- → 上层 engine: **无**（底层契约。被 data-engine-impl 与 gateway 引用）。
- ← 被调用方: 仅 data-engine-impl 的 `DataSourceController`、`PipelineTaskController`、`PipelineFunctionController`、`QualityController`、`DqController`、`CatalogController`、`LineageCompatController` 等。
- 跨引擎：data-engine 不调其他引擎（架构铁律 0.3：data-engine 是底层引擎，统一被调用，禁止反调）。

## 端点 / 补丁
- 本模块**不暴露 REST 端点**（无 `@RestController`）。
- 新增 PipelineService 方法示例（双契约一致性）：
```java
public interface PipelineService {
    /** 新增 DSL，必带任务名称 */
    List<PipelineDefinition> listByTaskId(String taskId);
}
```
- 新契约的 impl 在 `*-impl` 内独立文件，命名遵循 `Service`/`ServiceImpl` 习惯。

## 禁止
- 不改既有方法签名（API 只增不改）。
- 不在此模块加任何业务实现类（带 `interface` 与 `entity` 的池外禁止）；新增类必须显式走 impl。
- 不 import `*-engine-impl`（契约不依赖业务实现，违反架构铁律 2.1 = 验收失败）。
- 不硬编码 token / BOD / metadata（连接串、用户名走 `JdbcTemplate` 注入，不走 `@Value` 字面量）。
- 实体新提自有 驾庫 driver — 禁止（Driver 收敛 `runtime-access`，本模块不得 new）。
- SQL 入参必须强类型/DTO，禁止 `Map<String, Object>` 作为必填接口（架构铁律 后端 1.5）。
