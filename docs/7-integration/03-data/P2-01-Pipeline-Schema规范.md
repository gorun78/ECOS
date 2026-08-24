# P2-01 Pipeline YAML Schema 规范

> ECOS data-engine | 2026-08-24 | 本轮出规范，下轮实现校验器

## 一、目标

制定 Pipeline YAML Schema 规范，使 Pipeline 定义可调试、可监控、可版本管理。参考 Kettle step 模型 + Foundry Pipeline Builder。

## 二、顶层 Schema

```yaml
pipeline:
  id: string          # 必填，全局唯一
  name: string        # 必填，人类可读名称
  version: string     # 必填，SemVer (e.g. "1.0.0")
  description: string # 选填
  schedule:           # 选填，调度配置
    cron: string      # cron 表达式，走 runtime-task
    enabled: boolean  # 默认 true
  nodes:              # 必填，节点列表（至少1个）
    - NodeSpec[]      # 见下
  retry:              # 选填
    maxAttempts: int  # 默认 3
    backoffSeconds: int # 默认 30
```

## 三、节点类型枚举

| 类型 | 说明 | 版本 |
|------|------|:---:|
| SOURCE_JDBC | JDBC 数据源抽取 | 全版本 |
| SOURCE_CSV | CSV 文件抽取 | 全版本 |
| SOURCE_REST | REST API 抽取 | 全版本 |
| SOURCE_CDC | Flink CDC 实时订阅 | 仅 flagship |
| TRANSFORM_SQL | SQL 转换 | 全版本 |
| OUTPUT_OBJECT | 写入目标表/对象存储 | 全版本 |

## 四、节点 config schema

### NodeSpec 通用字段

```yaml
- id: string          # 必填，节点内唯一
  type: NodeType      # 必填，枚举值
  name: string        # 选填
  config: map         # 必填，类型相关
  dependsOn: string[] # 选填，依赖节点 id 列表
```

### SOURCE_JDBC

| 字段 | 必填 | 类型 | 说明 |
|------|:---:|------|------|
| datasourceId | ✅ | string | 数据源 ID |
| sql | ✅ | string | 查询 SQL |
| fetchSize | ❌ | int | 默认 1000 |
| incrementalColumn | ❌ | string | 增量列名 |
| lastSyncValue | ❌ | string | 上次同步值 |

### SOURCE_CSV

| 字段 | 必填 | 类型 | 说明 |
|------|:---:|------|------|
| filePath | ✅ | string | 文件路径 |
| delimiter | ❌ | string | 默认逗号 |
| header | ❌ | bool | 默认 true |
| encoding | ❌ | string | 默认 UTF-8 |

### SOURCE_REST

| 字段 | 必填 | 类型 | 说明 |
|------|:---:|------|------|
| url | ✅ | string | API URL |
| method | ❌ | string | 默认 GET |
| headers | ❌ | map | 请求头 |
| body | ❌ | string | 请求体 |
| pagination | ❌ | string | 分页策略 |

### TRANSFORM_SQL

| 字段 | 必填 | 类型 | 说明 |
|------|:---:|------|------|
| sql | ✅ | string | 转换 SQL |
| timeout | ❌ | int | 超时秒数，默认 30 |

### OUTPUT_OBJECT

| 字段 | 必填 | 类型 | 说明 |
|------|:---:|------|------|
| targetTable | ✅ | string | 目标表名 |
| mode | ❌ | string | append/overwrite，默认 append |
| batchSize | ❌ | int | 批量大小，默认 1000 |

## 五、version 语义

采用 SemVer：`MAJOR.MINOR.PATCH`

- MAJOR：不兼容的 schema 变更（删除节点类型、改字段类型）
- MINOR：向后兼容新增（新节点类型、新可选字段）
- PATCH：修复/优化

## 六、示例

### 示例 1：简单抽取

```yaml
pipeline:
  id: pipeline-001
  name: 订单全量抽取
  version: "1.0.0"
  nodes:
    - id: source_order
      type: SOURCE_JDBC
      config:
        datasourceId: ds_erp
        sql: "SELECT * FROM orders WHERE updated_at > '${lastSync}'"
        fetchSize: 5000
    - id: sink_warehouse
      type: OUTPUT_OBJECT
      config:
        targetTable: ods_orders
        mode: append
      dependsOn: [source_order]
```

### 示例 2：带转换的 ETL

```yaml
pipeline:
  id: pipeline-002
  name: 客户清洗入湖
  version: "1.0.0"
  schedule:
    cron: "0 2 * * ?"
  nodes:
    - id: source_crm
      type: SOURCE_JDBC
      config:
        datasourceId: ds_crm
        sql: "SELECT * FROM customers"
    - id: transform_clean
      type: TRANSFORM_SQL
      config:
        sql: "SELECT id, TRIM(name) as name, LOWER(email) as email FROM ${source_crm}"
      dependsOn: [source_crm]
    - id: sink_dwh
      type: OUTPUT_OBJECT
      config:
        targetTable: dwd_customers
        mode: overwrite
      dependsOn: [transform_clean]
```

### 示例 3：CDC 实时同步

```yaml
pipeline:
  id: pipeline-003
  name: 订单实时同步
  version: "1.0.0"
  nodes:
    - id: source_cdc
      type: SOURCE_CDC
      config:
        datasourceId: ds_mysql_binlog
        tables: ["orders", "order_items"]
        startPosition: "earliest"
    - id: sink_realtime
      type: OUTPUT_OBJECT
      config:
        targetTable: realtime_orders
        mode: append
      dependsOn: [source_cdc]
```

## 七、校验规则

1. **必填校验**：id/name/version/nodes 不可为空
2. **类型校验**：type 必须在枚举内
3. **依赖环检测**：dependsOn 不可形成环
4. **唯一性校验**：节点 id 不可重复
5. **可达性校验**：每个节点必须可达（从源头或被依赖可达）
