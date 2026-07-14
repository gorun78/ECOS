# 企业认知操作系统 (ECOS) 后端详细设计说明书
## 第三分册：Doris & MinIO 数据管道与物化视图、多语言沙盒 Workbook 与极速湖仓元数据目录 (Volume 3: Doris & MinIO High-Throughput Pipelines, Multi-language Workbook Engine & Lakehouse Metadata Catalog)

本说明书承接《第一分册：核心安全、语义实体与智能体底座架构》与《第二分册：多目标优化推理、世界模型演练与企业级数字孪生引擎》，针对 ECOS 基础数据平台的后台三大核心服务——**自主 Doris DAG 计算管道编译服务**、**多语言隔离交互 Workbook 运行时环境** 以及 **基于 Doris 与 MinIO 存储对齐的统一元数据检索目录** 进行深度后端系统与核心协议设计。

---

## 1. 编译数据管道：基于 Apache Doris SQL 与 MinIO 外表的自动 DAG 生成引擎 (Pipeline DAG Engine)

在 ECOS 平台中，`PipelineBuilder` 模块允许用户通过拖拽图形组件或直接编写 SQL 来编排复杂的高通量数据加工流程。系统后端负责将这些静态的节点（Sources, Filters, Joins, Aggregations, Sinks）转换为生产级 Apache Doris 结构化实体、物物化视图（Materialized Views）或 MinIO 归档作业。

```
                    ┌───────────────────────────────────┐
                    │  图形或 SQL 变更：前端发送元数据 JSON  │
                    └─────────────────┬─────────────────┘
                                      ▼
                    ┌───────────────────────────────────┐
                    │  AST 词法/语法解析器与模式对齐校验 │
                    └─────────────────┬─────────────────┘
                                      ▼
                    ┌───────────────────────────────────┐
                    │    依赖项拓扑排序 (Kahn's 算法)    │
                    └─────────────────┬─────────────────┘
                                      ▼
                    ┌───────────────────────────────────┐
                    │  Doris DDL / DML 管道代码自动生成   │
                    └─────────────────┬─────────────────┘
                                      ▼
                    ┌───────────────────────────────────┐
                    │  通过 Stream Load 提报与执行作业   │
                    └───────────────────────────────────┘
```

### 1.1 依赖项拓扑排序算法 (Kahn's Topological Sort)
后端在接收到 Pipeline 的 JSON 定义后，必须通过 Kahn's 算法对节点连接关系进行拓扑排序，检测是否存在死循环，并得出合理的先后执行与物化顺序（Execution & Materialization Order）：

```
算法步骤：
1. 计算图中所有节点的入度 (Indegree)。
2. 将所有入度为 0 的源头节点（Source Nodes，如连接在 MinIO 上的 External Tables 或者是 Doris ODS 数据库源表）放入初始化队列 Q。
3. 循环弹出队列首节点 n，顺次访问其后继节点 m：
   - 削减节点 m 的入度数：Indegree(m) = Indegree(m) - 1;
   - 若 Indegree(m) 降为 0，则将节点 m 压入队列 Q；
4. 如果被输出的节点总数小于总节点数，说明 DAG 中存在环形循环引用（Cycle Detected），直接报错熔断并拒绝编译。
```

### 1.2 动态 Doris SQL 管道代码生成器 (Automated Doris SQL Codegen Services)
经过拓扑校验通过后，代码生成微服务 `ecos-pipeline-compiler` 依次提取各节点属性特征，在内存中动态组装 Doris SQL。

**一阶段（抽样与外表定义 S3 External Table）：**
若输入源为存储在 MinIO 对象存储中的冷 Parquet 或 CSV 数据，生成器会自动装配外部表（S3 Table-Valued Function 或 S3 Catalog）：
```sql
CREATE TABLE temp_minio_raw_transactions (
    transaction_id VARCHAR(64),
    customer_id VARCHAR(64),
    amount DOUBLE,
    event_time DATETIME
) ENGINE=ODS
PROPERTIES (
    "resource" = "minio_s3_resource",
    "path" = "s3a://ecos-raw-bucket/transactions/*.parquet",
    "format" = "parquet"
);
```

**二阶段（加工转换与同步物化视图）：**
利用 Doris 极速列式存储与高效的同步/异步物化视图进行关联聚合，输出汇总结算数据（Doris Unique Key 或 Aggregate Key 物理表）：
```sql
-- 自动生成的 Doris DML 数据处理管道
INSERT INTO cos_lakehouse.gold_customer_360
SELECT 
    customer_id,
    SUM(amount) AS total_spend,
    MAX(event_time) AS last_active_time
FROM temp_minio_raw_transactions
GROUP BY customer_id;
```

### 1.3 统一计算作业提交协议 (Payload to Apache Doris Stream Load API)
编译出的流式导入作业不经由 Spark 提报，而是利用高性能的 **Stream Load (HTTP Chunked Ingestion)** 将数据直推至 Doris 节点：

```http
PUT /api/cos_lakehouse/gold_customer_360/_stream_load HTTP/1.1
Host: doris-fe-node:8030
Authorization: Basic am9iX3VzZXI6cGFzc3dvcmQ=
Expect: 100-continue
column_separator: ,
columns: customer_id, total_spend, last_active_time
format: csv
properties: {
    "strip_outer_array": "true",
    "max_filter_ratio": "0.1"
}

[Body: Stream Cargo Array]
```

---

## 2. Interactive Workspace Workbooks: 多语言交互式沙盒 Server 架构 (Workbook Sandbox Runtime)

`CodeWorkbook` 提供高密度的敏捷交互式计算体验。每一位数据专家可以在隔离的工作区中实时编辑运行 SQL、Python 或 R 脚本，系统后端通过对会话（Session）的软硬手段（如内存边界限制、内核权限）来实现安全的多租户隔离设计。

### 2.1 容器级沙盒微内核生命周期管理 (Microservices Sandbox Lifecycles)
与普通的控制流不同，Workbook 代码的运算属于高消耗、不可控的行为。后端采用**单租户独占沙盒 Pod 容器组**结构进行构建：

1. **热备会话池（Session Warm Pools）**：
   - 后端在 Kubernetes 集群中自建了闲置会话池。用户打开 Workbook 的瞬间，系统调用网关将未分配容器进行绑定，将就绪等待时间（Spawning Latency）缩减至 1 秒以内。
2. **多进程语言控制器守护系统 (Language Kernel Daemons)**：
   - 在接收运行命令时，Pod 内部的 Python 控制守护程序调用对应的底层解释器，并通过命名管道抓取该进程在 `stdout` 与 `stderr` 中的全部溢出，将其重构为前端所需的微秒级实时数据帧包。

```
                             [ Web 终端 WebSocket 连接 ]
                                         │  (指令: RunCell-1)
                                         ▼
                             [ 网关: ECOS Session Router ]
                                         │   (转发对应容器)
                                         ▼
                            [ 独占容器: Pod-wb-cdrc-072 ]
                                         │
                 ┌───────────────────────┼───────────────────────┐
                 ▼                       ▼                       ▼
         [ Doris JDBC Client ]    [ IPython Process ]       [ R-Daemon Engine ]
         (Doris 亚秒级多维查询)   (Jupyter-like 核心)        (数据模拟与高频演练)
```

### 2.2 资源限额与多因子安全硬沙箱限制 (Hardware Hardening Presets)
为了防护越权调用与平台崩坏，每一个 Workbook 底层进程都会绑定强硬件边界：

- **CPU & 内存硬配额锁定（Memory Hard Constraint）**：
  在 Cgroups 配置中锁定最大运行时常：
  `resources.limits.cpu = 2`, `resources.limits.memory = "4Gi"`。
- **系统调用拦截（Syscall Interceptor / seccomp）**：
  配置 seccomp 权限文件，完全禁止该容器内部的程序触发任何敏感内核调用，隔离文件目录访问。

---

## 3. Doris + MinIO 统一元数据资产目录 (Doris Lakehouse Catalog & Search Services)

物理数据层通过 Doris 表、MinIO 桶中的冷 Parquet、以及 Redis 热缓存多渠道散落。为了让多智能体和工程师能快速发现、调用和解析数据实体，ECOS 后端构建了**统一高性能湖仓元数据资产目录服务** (Metadata Catalog Service)。

### 3.1 物理数据目录与语义对象的底层映射 (Semantic Object Mapper)
- 每一张物理 Doris 表、MinIO External Table 映射在后端都会被分配一个独一无二的 UUID 标签。
- 后端利用 PostgreSQL JSONB 数据段映射复杂的物理属性、中英文国际化文本标注以及历史访问统计。

```sql
-- 字段定义示例：实体元数据与湖仓物理存储路径绑定
CREATE TABLE lakehouse_schema_catalog (
    asset_id VARCHAR(64) PRIMARY KEY,
    physical_name VARCHAR(128) NOT NULL,          -- e.g., "cos_lakehouse.customer_churn_indices"
    minio_s3_path VARCHAR(256) NOT NULL,          -- e.g., "s3a://ecos-gold-bucket/analytics/customer_churn/"
    row_count_estimate BIGINT DEFAULT 0,
    storage_format VARCHAR(16) DEFAULT 'parquet', -- parquet / csv / doris_native
    asset_attributes JSONB NOT NULL,              -- 包含复杂多维标注与物化投影规则
    last_computed_time TIMESTAMP WITH TIME ZONE,
    is_hot_storage BOOLEAN DEFAULT TRUE           -- 热/冷存储分级
);
```

### 3.2 高性能倒排搜索体系与多维分类过滤器 (Atlas Search Elastic System)
后端定时触发 `ecos-metadata-sync-daemon`，从 Doris 的系统表（Information Schema）与统一元数据集中提取标签，同步写至集群的 **Elasticsearch** 全文索引中。

---

## 4. 全链路多维数据血缘体系设计 (Multi-dimensional Data Lineage System)

数据血缘用于厘清“源头物理字段在哪些转换算子中最终汇总为帕累托前置目标指标”的纵深依赖脉络。

```
 [原始 MinIO 源文件: Raw Parquet] ───(Doris DB Ingestion)───► [ODS层 Doris表: ods_tx]
                                                                  │
                                                                  │ (Doris 物化视图/聚合)
                                                                  ▼
 [决策指标: Quarterly Revenue] ◄───(Predictive Model)───── [黄金指标: Customer360]
```

### 4.1 血缘追踪采集器数据模型设计 (Lineage Trace Collector)
当用户通过 Pipeline Builder 执行 DDL 或是调用 Workbook 触发 Doris SQL 运行任务时，后端的 **Lineage-Listener（血缘监听中间件）** 会在后台实时分析物理计划与 SQL 抽象语法树（AST）：

1. **Doris SQL 计划血缘监听 (SQL AST & Explain Parser)**：
   - 监听器拦截 Doris CLI / JDBC 会话中提交的数据操纵指令（DML），通过对 SQL 进行 `EXPLAIN` 解析其底层涉及的输入表表名 (Input Tables) 与当前输出表 (Insert Destination)。
   - 提取字段级映射，追踪字段的依赖变换链，向图数据库中存储连线，生成动态可视化的血缘结构。

```typescript
// Drizzle-style 数据血缘关联表
export const dataLineageEdges = pgTable("data_lineage_edges", {
  edgeId: text("edge_id").primaryKey(),
  sourceAssetId: text("source_asset_id").notNull(), // 源资产 uuid
  targetAssetId: text("target_asset_id").notNull(), // 目标资产 uuid
  transformationType: text("transformation_type"),  // e.g., "Doris-SQL-Join", "S3-Schema-Align"
  transformExpression: text("transform_expression"), // 承载公式/算子详情
  createdByPipelineId: text("created_by_pipeline_id"), // 追踪所由 Pipeline 触发
  recordedAt: timestamp("recorded_at").defaultNow(),
});
```

---
**《企业认知操作系统 (ECOS) 后端详细设计说明书 · 第三分册》编制完成，待评审签发。**
