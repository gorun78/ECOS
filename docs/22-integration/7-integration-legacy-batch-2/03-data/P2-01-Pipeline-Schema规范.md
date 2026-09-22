# P2-01 Pipeline Schema 规范

> ECOS data-engine | 首版 2026-08-24 | 本版 2026-09-09（从 git 历史 `5f334d3` 恢复 + 对照当前实现完善）
> 参考 Kettle step 模型 + Palantir Foundry Pipeline Builder DSL（`Pipeline2.0-PRD.md` §3.2/§3.4）

## 一、目标

制定 Pipeline Schema 规范，使 Pipeline 定义可调试、可监控、可版本管理。
本规范覆盖 **Pipeline 定义（PipelineDefinition）、节点（PipelineNode）、边（PipelineEdge）、执行记录（PipelineExecution）** 四张表的字段级契约，并给出算子扩展（UDF/JOIN/SINK）章节。

---

## 二、顶层 Schema（Pipeline 定义）

Pipeline 定义保存于 `ecos_pipeline_definition` 表。顶层 DSL 结构：

```yaml
pipeline:
  id: string          # 必填，全局唯一（UUID 32 位无连字符）
  name: string        # 必填，人类可读名称
  description: string # 选填
  status: string      # 必填, 枚举: DRAFT / ACTIVE / ARCHIVED
  version: string     # 选填，SemVer 语义 (e.g. "1.0.0")，缺省 1.0.0
  schedule:           # 选填，调度配置 (存 definition JSONB)
    cron: string      # cron 表达式，走 runtime-task
    scheduleId: string # runtime-task 调度注册 ID（系统回写）
  nodes:              # 必填，节点列表（至少 1 个）
    - NodeSpec[]
  edges:              # 选填，DAG 边（由 from → to 推导 dependsOn）
    - { from: nodeId, to: nodeId, label: string }
  retry:              # 选填
    maxAttempts: int  # 默认 3
    backoffSeconds: int # 默认 30
```

### 2.1 definition JSONB 列结构（DB 实际落地）

`ecos_pipeline_definition.definition` 为 JSONB 列，当前实现落库结构：

```json
{
  "schedule": {
    "cron": "0 2 * * ?",
    "scheduleId": "rt-schedule-xxx"
  },
  "...其他扩展键": "..."
}
```

- `schedule.cron`：调度表达式；空 → 未注册定时调度。
- `schedule.scheduleId`：runtime-task 调度注册 ID（`TaskSchedulerService.scheduleTask` 返回），用于 `cancelSchedule` 取消。
- 其余键透传到 `PipelineDefinition.extensions`（Map），前端可按需读写（如 `version`/`tags`）。

### 2.2 字段表

| 字段 | 必填 | 类型 | 默认值 | 说明 |
|------|:---:|------|--------|------|
| `id` | ✅ | string | — | 主键，32 位无连字符 UUID |
| `name` | ✅ | string | — | Pipeline 名称，非空校验 |
| `description` | ❌ | string | `""` | 描述 |
| `status` | ❌ | string | `DRAFT` | 枚举 `DRAFT`/`ACTIVE`/`ARCHIVED`；删除为逻辑删除（→`ARCHIVED`） |
| `scheduleCron` | ❌ | string | `null` | 调度 cron，存 `definition.schedule.cron` |
| `extensions` | ❌ | map | `{}` | 扩展属性（含 `scheduleId` 等） |
| `createdAt` | — | datetime | `NOW()` | 创建时间 |
| `updatedAt` | — | datetime | `NOW()` | 更新时间 |

---

## 三、节点 Schema（PipelineNode）

节点保存于 `ecos_pipeline_node` 表。每个节点是 DAG 中的执行单元。

### 3.1 NodeSpec 通用字段

| 字段 | 必填 | 类型 | 默认值 | 说明 |
|------|:---:|------|--------|------|
| `id` | — | string | — | 节点表主键（服务端生成 UUID） |
| `definitionId` | — | string | — | 所属 Pipeline 定义 ID（关联 `ecos_pipeline_definition.id`） |
| `nodeId` | ✅ | string | — | 前端节点标识，**同一定义内唯一**（DAG 拓扑依赖键） |
| `type` | ✅ | string | `TRANSFORM_SQL` | 节点类型枚举，见§四 |
| `config` | ❌ | map(string) | `{}` | 节点配置 JSONB，类型相关，见§四.2 |
| `dependsOn` | ❌ | string[](jsonb) | `[]` | 依赖的 `nodeId` 列表，用于拓扑排序（由 edges 推导回写） |
| `positionX` | ❌ | int | `0` | 画布 X 坐标 |
| `positionY` | ❌ | int | `0` | 画布 Y 坐标 |
| `createdAt` | — | datetime | `NOW()` | 创建时间 |
| `updatedAt` | — | datetime | `NOW()` | 更新时间 |

> 请求体侧（`PipelineSaveDTO.NodeSpec`）使用 `id`/`nodeId` 双键：`nodeId` 优先，`id` 兜底，二者取其一即可。
> `edges` 的 `from`/`to` 指向 `nodeId`；服务端据此反向计算每个节点的 `dependsOn` 列。

### 3.2 边（PipelineEdge）

边表 `ecos_pipeline_edge` 为冗余存储（方便查询），创建/更新时按 edges 写入，但**详情/节点拓扑的执行以 `depends_on` 列为准**（写不读，保持 edges 与 dependsOn 单向一致）。

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | string | 主键 |
| `definitionId` | string | 所属定义 |
| `fromNodeId` | string | 源节点 `nodeId` |
| `toNodeId` | string | 目标节点 `nodeId`（即该节点依赖 `fromNodeId`） |

---

## 四、节点类型枚举

### 4.1 节点类型全集

当前 `PipelineExecutionService.executeNode` 支持全集（`switch` 分支）：

| 类型 | 说明 | 版本 | 执行器 |
|------|------|:---:|--------|
| `SOURCE_JDBC` | JDBC 数据源抽取 | 全版本 | `JdbcConnector.executeSql`（经 ConnectorFactory，禁系统 JdbcTemplate） |
| `SOURCE_CSV` | CSV 文件抽取 | 全版本 | `CsvConnector.readRows` |
| `SOURCE_REST` | REST API 抽取 | 全版本 | `RestApiConnector.fetchData` |
| `SOURCE_CDC` | Flink CDC 实时订阅 | 仅 flagship | 未实现（抛 `UnsupportedOperationException`） |
| `TRANSFORM_SQL` | SQL 转换 | 全版本 | 系统 JdbcTemplate.update（转换在系统库内，允许） |
| `OUTPUT_OBJECT` | 写入目标表 | 全版本 | 系统 JdbcTemplate INSERT |
| `TRANSFORM_UDF` | UDF 脚本转换 | 预留 | 见§八 UDF 扩展章节 |
| `JOIN` | 多表关联 | 预留 | 见§九 |
| `SINK` | 统一写入（JDBC 目标） | 预留 | 见§九 |

> 前端节点面板、后端执行器 switch、测试数据三方必须同源一致（架构铁律 §4.8.2）。前端契约使用 `SourceType/TransformType/OutputType` 三组常量映射，禁止裸 `SOURCE`/`SINK`（被执行器拒绝）。

### 4.2 各节点 config schema

通用约束：`config` 为 JSONB，按节点类型解析；所有值在返回 VO 前经敏感字段脱敏（见§四.3）。

#### SOURCE_JDBC

| 字段 | 必填 | 类型 | 默认值 | 说明 |
|------|:---:|------|--------|------|
| `datasourceId` | ✅ | string | — | 已注册数据源 ID（走 `DataSourceService.getById` 取 connectionConfig） |
| `sql` | ✅ | string | — | 查询/执行 SQL |
| `fetchSize` | ❌ | int | `1000` | 每次拉取行数 |
| `incrementalColumn` | ❌ | string | — | 增量列名（增量同步用） |
| `lastSyncValue` | ❌ | string | — | 上次同步值 |
| `password` | ❌ | string | — | 直连口令（可选直连，**敏感字段，响应脱敏**） |

#### SOURCE_CSV

| 字段 | 必填 | 类型 | 默认值 | 说明 |
|------|:---:|------|--------|------|
| `filePath` | ✅ | string | — | 文件路径 |
| `delimiter` | ❌ | string | `,` | 分隔符 |
| `header` / `hasHeader` | ❌ | bool | `true` | 是否有表头 |
| `encoding` | ❌ | string | `UTF-8` | 编码 |
| `fetchSize` | ❌ | int | `0` | 0 = 不限制 |

#### SOURCE_REST

| 字段 | 必填 | 类型 | 默认值 | 说明 |
|------|:---:|------|--------|------|
| `url` | ✅ | string | — | API URL |
| `method` | ❌ | string | `GET` | HTTP 方法 |
| `headers` | ❌ | map | — | 请求头 |
| `body` | ❌ | string | — | 请求体 |
| `pagination` | ❌ | string | — | 分页策略 |
| `token` | ❌ | string | — | 认证令牌（**敏感字段，响应脱敏**） |

#### TRANSFORM_SQL

| 字段 | 必填 | 类型 | 默认值 | 说明 |
|------|:---:|------|--------|------|
| `sql` | ✅ | string | — | 转换 SQL |
| `timeout` | ❌ | int | `30` | 超时秒数 |

#### OUTPUT_OBJECT

| 字段 | 必填 | 类型 | 默认值 | 说明 |
|------|:---:|------|--------|------|
| `targetTable` | ✅ | string | — | 目标表名 |
| `mode` | ❌ | string | `append` | `append`/`overwrite` |
| `batchSize` | ❌ | int | `1000` | 批量大小 |
| `rows` | ❌ | array | — | 内联数据行（OUTPUT_OBJECT 直接 INSERT 时用） |

#### TRANSFORM_UDF（预留，见§八）

| 字段 | 必填 | 类型 | 默认值 | 说明 |
|------|:---:|------|--------|------|
| `udfId` | ✅ | string | — | 关联 `ecos_pipeline_udf.id` |
| `udfName` | ❌ | string | — | UDF 名称（冗余，便于调试） |
| `params` | ❌ | map | — | 传给 UDF 的参数 |

#### JOIN（预留，见§九）

| 字段 | 必填 | 类型 | 说明 |
|------|:---:|------|------|
| `joinType` | ✅ | string | `inner`/`left`/`right`/`full`/`anti`/`cross` |
| `leftNode` | ✅ | string | 左输入 `nodeId` |
| `rightNode` | ✅ | string | 右输入 `nodeId` |
| `on` | ✅ | array | 关联键 `[{left:col, right:col}]` |

#### SINK（预留，见§九）

| 字段 | 必填 | 类型 | 默认值 | 说明 |
|------|:---:|------|--------|------|
| `datasourceId` | ✅ | string | — | 目标数据源 ID |
| `table` | ✅ | string | — | 目标表名 |
| `mode` | ❌ | string | `append` | `append`/`overwrite` |
| `columns` | ❌ | array | — | 写入列映射 |

### 4.3 敏感字段脱敏规则（架构铁律 §2.4 强制卡）

- **适用节点**：`SOURCE_JDBC`（`password`/`token`/`secret`）、`SOURCE_REST`（`token`/`headers.Authorization`）。
- **规则**：明文仅入库（`ecos_pipeline_node.config` JSONB）；任何返回 VO（列表/详情/创建/更新/执行响应）在序列化前，将命中键的值替换为 `******`。
- **脱敏实现**：管道包内 `PipelineSecurityService.mask(secret...)`，统一由 `data-engine-impl` 收敛，禁止各调用点重复实现（对齐 security-engine 横切原则）。

---

## 五、version 语义（SemVer）

`PipelineDefinition.version` / 顶层 DSL `version` 采用 SemVer：`MAJOR.MINOR.PATCH`

- **MAJOR**：不兼容的 schema 变更（删除节点类型、改字段类型、改主键）。
- **MINOR**：向后兼容新增（新节点类型、新可选字段）。
- **PATCH**：修复/优化。

版本变更需与 `ecos_pipeline_udf` 的 `version` 字段联动（UDF 升级触发 Pipeline MINOR 增）。

---

## 六、示例

### 示例 1：简单抽取（JDBC → 目标表）

```yaml
pipeline:
  id: pipeline-001
  name: 订单全量抽取
  version: "1.0.0"
  nodes:
    - nodeId: source_order
      type: SOURCE_JDBC
      config:
        datasourceId: ds_erp
        sql: "SELECT id, amount, status, created_at FROM orders WHERE updated_at > '${lastSync}'"
        fetchSize: 5000
    - nodeId: sink_warehouse
      type: OUTPUT_OBJECT
      config:
        targetTable: ods_orders
        mode: append
      dependsOn: [source_order]
```

### 示例 2：带转换 + 定时调度的 ETL

```yaml
pipeline:
  id: pipeline-002
  name: 客户清洗入湖
  version: "1.0.0"
  schedule:
    cron: "0 2 * * ?"
  nodes:
    - nodeId: source_crm
      type: SOURCE_JDBC
      config:
        datasourceId: ds_crm
        sql: "SELECT id, name, email FROM customers"
    - nodeId: transform_clean
      type: TRANSFORM_SQL
      config:
        sql: "INSERT INTO staging_customers SELECT id, TRIM(name), LOWER(email) FROM src_customers"
      dependsOn: [source_crm]
    - nodeId: sink_dwh
      type: OUTPUT_OBJECT
      config:
        targetTable: dwd_customers
        mode: overwrite
      dependsOn: [transform_clean]
```

### 示例 3：UDF 算子（预留）

```yaml
pipeline:
  id: pipeline-003
  name: 订单实时同步 (UDF 清洗)
  version: "1.0.0"
  nodes:
    - nodeId: source_mysql
      type: SOURCE_JDBC
      config:
        datasourceId: ds_mysql
        sql: "SELECT * FROM orders"
    - nodeId: udf_clean
      type: TRANSFORM_UDF
      config:
        udfId: udf-2c1f...   # 关联 ecos_pipeline_udf.id
        udfName: order_clean
        params: { dialect: "mysql" }
      dependsOn: [source_mysql]
    - nodeId: sink_realtime
      type: OUTPUT_OBJECT
      config:
        targetTable: realtime_orders
        mode: append
      dependsOn: [udf_clean]
```

---

## 七、校验规则（服务端强约束）

创建/更新时服务端执行以下校验，违例抛 `ValidationException`/`BusinessException`（不裸 `IllegalArgumentException`）：

1. **必填校验**：`name` 不可为空；至少 1 个节点；节点 `nodeId` 与 `type` 不可为空。
2. **数据类型校验**：`type` 必须在枚举全集（§4.1）内，否则执行期抛 `UnsupportedOperationException`，建议创建期前置拒绝。
3. **节点 id 唯一性**：同一定义内 `nodeId` 不可重复。
4. **拓扑可达性 / 环检测**：`dependsOn` 不得形成环（Kahn 拓扑排序，无法排出全部节点即判环，见 `PipelineExecutionService.topologicalSort`）。创建期建议对 edges 做一次拓扑预检。
5. **可达性校验**：每个节点必须可达（有入边/出边或为源/汇节点）；孤立节点（既无 dependsOn 也无下游）告警。
6. **敏感字段脱敏**：响应 VO 序列化前对 §4.3 命中键脱敏（明文仅入库）。

---

## 八、UDF 扩展章节（预留 `TRANSFORM_UDF` 节点类型）

### 8.1 概述

UDF（用户自定义函数）允许在 Pipeline 节点内运行用户编写的脚本完成复杂转换，算子注册表落 `ecos_pipeline_udf` 表。后端已具备：

- `UdfService`/`UdfServiceImpl`：UDF 注册/更新/列表/详情/删除/测试/SQL→UDF 转换。
- `UdfSandbox`：UDF 沙箱执行。
- `UdfController`：`/api/v1/engine/data/udf/**` 7 端点（PRD §5.3）。

### 8.2 `ecos_pipeline_udf` 表字段（现有实现）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | varchar(36) PK | UDF 唯一标识 |
| `name` | varchar(200) UNIQUE | UDF 名称 |
| `category` | varchar(50) | `transform`/`expression`/`aggregate` |
| `language` | varchar(20) | `python`/`java`/`sql`（默认 `python`） |
| `signature` | text | 函数签名 JSON |
| `source_code` | text | UDF 源码（NOT NULL） |
| `compiled_path` | varchar(500) | 编译后路径 |
| `version` | int | 版本（更新 +1） |
| `author` | varchar(100) | 作者 |
| `is_shared` | bool | 是否团队共享 |
| `description` | text | 描述 |
| `created_at` / `updated_at` | timestamp | 时间戳 |

### 8.3 `TRANSFORM_UDF` 节点接入方式

- 节点 `type = TRANSFORM_UDF`，`config.udfId` 指向 `ecos_pipeline_udf.id`。
- 执行器（`PipelineExecutionService.executeNode` 新增 `case "TRANSFORM_UDF"`）通过 `UdfService` 取 `source_code`，在 `UdfSandbox` 内执行，输入为上游节点 DataFrame，输出替换下游输入。
- UDF 升级（`version +1`）应触发所属 Pipeline 的 `version MINOR +1`（SemVer §五联动）。
- **禁止**在管道执行器内自建脚本引擎，统一走 `UdfSandbox`（与 security-engine 沙箱同构）。

---

## 九、JOIN / SINK 节点定义章节（预留）

### 9.1 JOIN 节点（`type = JOIN`）

多表关联算子，依赖两个上游输入节点。

- 配置：`joinType` / `leftNode` / `rightNode` / `on`（见§4.2）。
- 执行语义：按 `on` 关联键做关系代数运算，`leftNode`/`rightNode` 的 `dependsOn` 在 DAG 中体现。
- 适用版本：全版本（内存执行）；`cross_join`/大表场景建议 ultimate 档（Doris 列存）。
- 约束：`leftNode`/`rightNode` 必须为已存在的 `nodeId`，否则创建期抛 `ValidationException`。

### 9.2 SINK 节点（`type = SINK`）

统一写入算子（区别于 `OUTPUT_OBJECT` 面向系统库内表，`SINK` 面向外部数据源 JDBC 目标）。

- 配置：`datasourceId` / `table` / `mode` / `columns`（见§4.2）。
- 执行语义：经 `ConnectorFactory` 获取 `JdbcConnector`，批量 INSERT 到外部数据源目标表（禁系统 JdbcTemplate，架构铁律 §2.5）。
- 敏感信息：目标数据源 `datasourceId` 指向已注册数据源，连接凭据走 `DataSourceService` 加密存储，**不落** `config`，不打印日志。
- 约束：`datasourceId` 必须存在，否则执行期抛 `NotFoundException`。

---

## 十、存储表速查

| 表 | 关键字段 | 用途 |
|----|----------|------|
| `ecos_pipeline_definition` | id, name, description, definition(JSONB), status, tenant_id, created_at, updated_at | 管道定义 |
| `ecos_pipeline_node` | id, definition_id, node_id, type, config(JSONB), depends_on(JSONB), position_x/y | DAG 节点 |
| `ecos_pipeline_edge` | id, definition_id, from_node_id, to_node_id | DAG 边（冗余） |
| `ecos_pipeline_execution` | id, pipeline_id, status, started_at, finished_at, error_message, rows_processed | 执行记录 |
| `ecos_pipeline_udf` | id, name, category, language, signature, source_code, version | UDF 注册表 |

---

## 附：变更记录

| 日期 | 版本 | 变更 |
|------|------|------|
| 2026-08-24 | 1.0.0 | 首版（git `5f334d3`） |
| 2026-09-09 | 2.0.0 | 恢复 + 对照当前实现完善：补字段完整表（§2.2/§3.1）、`definition` JSONB 实际结构（§2.1）、节点类型全集与 `config` 字段表（§4）、敏感字段脱敏规则（§4.3）、UDF 扩展章节（§8）、JOIN/SINK 节点定义（§9）、存储表速查（§10） |
