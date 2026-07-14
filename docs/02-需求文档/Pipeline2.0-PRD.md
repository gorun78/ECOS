# ECOS Pipeline 2.0 — 编辑/执行/检测/验证 PRD

> 版本: v1.0 | 日期: 2026-07-11 | 作者: ECOS-PMO
> 基于 BE/FE 现状调研 + Palantir Foundry PB 函数全集

---

## 一、现状诊断

| 维度 | 已有 | 缺失 |
|------|------|------|
| **Pipeline 编辑** | ✅ PipelineFlowEditor (ReactFlow 834行) | ❌ 表达式编辑器（Monaco 未集成） |
| **节点类型** | ✅ 5 种 (Source/Transform/Join/Aggregate/Sink) | 基本够用 |
| **属性面板** | ✅ PropertyPanel (541行) | ❌ 只有12个函数，未同步30+定义 |
| **算子函数库** | ✅ 30+ PB 函数定义在 DataWorkbenchLayout | ❌ 未与 PropertyPanel 同步，缺 70+ 函数 |
| **Git 集成** | ✅ GitPanel (526行) + GitController (8端点) | ❌ GitService 是内存占位桩；❌ 未集成到 Pipeline 工作流 |
| **执行引擎** | ✅ PipelineController (7端点) | ❌ PipelineTaskController 未创建 |
| **DB 存储** | ✅ ecos_pipeline_definition/node/edge/execution | ❌ ecos_pipeline_task/step 表不存在 |
| **Transform** | ✅ 6 个 TransformStep 实现 | ✅ 可复用 |
| **UDF** | ❌ 不存在 | ❌ 需从零构建 |
| **Copilot** | ❌ 不存在 | ❌ 需从零构建 |

---

## 二、目标架构

```
用户 → PipelineFlowEditor (FE)
           ↓
  ┌────────┼────────┬──────────┐
  │  编辑   │  Git   │  Copilot │
  │  DAG   │  版本  │  AI辅助  │
  └────────┼────────┴──────────┘
           ↓
  PipelineTaskController (BE) ─→ PipelineIntegrationService
           ↓                          ↓
  TaskScheduler ─→ ConnectorFactory ─→ TransformChain
```

---

## 三、功能需求

### 3.1 Pipeline 完整工作流

```
新建 Pipeline → 编辑数据源 → 选择算子 → 定义 DAG → 保存 ─┬─→ 仅保存到 DB
                                                         └─→ 提交到 Git 仓库
从 Git 加载 → 编辑 → 增量提交 / 新建分支
执行 → 监控进度 → 查看日志 → 重试/取消
```

**状态机**：
```
DRAFT → SAVED → QUEUED → RUNNING → SUCCEEDED / FAILED / CANCELLED
                                                    ↓
                                               RETRYING
```

### 3.2 Pipeline Git 存储结构

```
{git-repo}/pipelines/
├── {pipeline-id}/
│   ├── pipeline.yaml           # Pipeline DSL 定义
│   ├── expressions/            # 自定义表达式(UDF)
│   │   └── custom_func.py
│   ├── config/
│   │   └── runtime.yaml        # 执行引擎/超时等配置
│   └── README.md               # 自动生成的 Pipeline 文档
├── shared/
│   ├── transforms/             # 团队共享算子
│   └── udfs/                   # 团队共享 UDF
└── .ecos-pipeline-meta.json    # ECOS 元数据(版本/创建人/最后编辑)
```

**pipeline.yaml DSL 示例**：
```yaml
apiVersion: ecos/v2
kind: Pipeline
metadata:
  id: "pl-abc123"
  name: "订单ETL流程"
  version: 1
  author: "guorongxiao"
  created: "2026-07-11T10:00:00Z"
  updated: "2026-07-11T12:00:00Z"
  tags: ["etl", "orders", "production"]
spec:
  execution:
    engine: memory                # memory | doris
    timeout: 600
    retryMax: 3
    batchSize: 5000
  nodes:
    - id: source_orders
      type: source
      config:
        datasourceId: ds-mysql-prod
        table: orders
        columns: ["order_id", "amount", "status", "created_at", "customer_id"]
    - id: filter_active
      type: transform
      config:
        expression: "filter(status == 'active')"
      dependsOn: [source_orders]
    - id: calc_rfm
      type: transform
      config:
        derive:
          - field: r_score
            expression: "case(datediff(now(), created_at) > 90, 1, datediff(now(), created_at) > 30, 2, 3)"
      dependsOn: [filter_active]
    - id: agg_customer
      type: aggregate
      config:
        groupBy: ["customer_id"]
        aggregations:
          - field: total_amount
            function: sum
            sourceField: amount
      dependsOn: [calc_rfm]
    - id: sink_dw
      type: sink
      config:
        datasourceId: ds-postgres-prod
        table: dw_customer_rfm
        mode: overwrite
      dependsOn: [agg_customer]
  edges:
    - from: source_orders
      to: filter_active
    - from: filter_active
      to: calc_rfm
    - from: calc_rfm
      to: agg_customer
    - from: agg_customer
      to: sink_dw
```

### 3.3 算子注册体系

将所有 Palantir Foundry PB 函数注册为可用的 Pipeline 算子，分为 **转换算子** 和 **表达式函数** 两层：

#### 3.3.1 算子层级

| 层级 | 作用 | 示例 |
|------|------|------|
| **Row 级算子** | 输入 DataFrame → 输出 DataFrame | filter, derive, union, sort, limit, sample, drop_columns, rename |
| **Column 级算子** | 对列施加变换 | cast, with_column, coalesce |
| **Multi-Frame 算** | 多表操作 | inner_join, left_join, right_join, full_join, anti_join, cross_join, union, except |
| **Aggregate 算子** | 分组聚合 | group_by(agg), pivot, unpivot, cube, rollup |
| **Window 算子** | 窗口函数 | window(over_spec, expression) |
| **I/O 算子** | 读写 | source(read), sink(write), commit |

#### 3.3.2 表达式函数全集（PB 函数目录）

**字符串函数 (25 个)**：
| 函数 | 签名 | 说明 |
|------|------|------|
| lower | `lower(str)` | 转小写 |
| upper | `upper(str)` | 转大写 |
| trim | `trim(str)` | 去首尾空格 |
| ltrim | `ltrim(str)` | 去左空格 |
| rtrim | `rtrim(str)` | 去右空格 |
| concat | `concat(a,b,...)` | 字符串拼接 |
| substring | `substring(str, start, len)` | 子串 |
| left | `left(str, n)` | 取左 n 个字符 |
| right | `right(str, n)` | 取右 n 个字符 |
| length | `length(str)` | 字符串长度 |
| replace | `replace(str, from, to)` | 替换 |
| split | `split(str, delimiter)` | 分割为数组 |
| regex_extract | `regex_extract(str, regex, group)` | 正则提取 |
| regex_replace | `regex_replace(str, regex, replacement)` | 正则替换 |
| starts_with | `starts_with(str, prefix)` | 是否以某前缀开始 |
| ends_with | `ends_with(str, suffix)` | 是否以某后缀结束 |
| contains | `contains(str, substring)` | 是否包含 |
| initcap | `initcap(str)` | 首字母大写 |
| reverse | `reverse(str)` | 反转 |
| lpad | `lpad(str, len, pad)` | 左填充 |
| rpad | `rpad(str, len, pad)` | 右填充 |
| repeat | `repeat(str, n)` | 重复 n 次 |
| translate | `translate(str, from, to)` | 字符映射 |
| instr | `instr(str, substr)` | 子串位置 |
| locate | `locate(substr, str, pos)` | 从 pos 开始查找 |

**数值函数 (25 个)**：
| 函数 | 签名 | 说明 |
|------|------|------|
| abs | `abs(x)` | 绝对值 |
| ceil | `ceil(x)` | 向上取整 |
| floor | `floor(x)` | 向下取整 |
| round | `round(x, d)` | 四舍五入到 d 位 |
| power | `power(base, exp)` | 幂运算 |
| sqrt | `sqrt(x)` | 平方根 |
| mod | `mod(a, b)` | 取模 |
| exp | `exp(x)` | e^x |
| ln | `ln(x)` | 自然对数 |
| log | `log(base, x)` | 对数 |
| log10 | `log10(x)` | 以10为底对数 |
| sign | `sign(x)` | 符号 (-1/0/1) |
| greatest | `greatest(a,b,...)` | 最大值 |
| least | `least(a,b,...)` | 最小值 |
| rand | `rand()` | 随机数 (0-1) |
| radians | `radians(deg)` | 度转弧度 |
| degrees | `degrees(rad)` | 弧度转度 |
| sin | `sin(x)` | 正弦 |
| cos | `cos(x)` | 余弦 |
| tan | `tan(x)` | 正切 |
| asin | `asin(x)` | 反正弦 |
| acos | `acos(x)` | 反余弦 |
| atan | `atan(x)` | 反正切 |
| atan2 | `atan2(y, x)` | 双参数反正切 |
| crc32 | `crc32(str)` | CRC32 哈希 |

**日期时间函数 (25 个)**：
| 函数 | 签名 | 说明 |
|------|------|------|
| year | `year(date)` | 提取年份 |
| month | `month(date)` | 提取月份 |
| day | `day(date)` | 提取日 |
| hour | `hour(ts)` | 提取小时 |
| minute | `minute(ts)` | 提取分钟 |
| second | `second(ts)` | 提取秒 |
| dayofweek | `dayofweek(date)` | 星期几 (1=周日) |
| dayofyear | `dayofyear(date)` | 一年第几天 |
| weekofyear | `weekofyear(date)` | 一年第几周 |
| quarter | `quarter(date)` | 季度 (1-4) |
| date_add | `date_add(date, days)` | 加天数 |
| date_sub | `date_sub(date, days)` | 减天数 |
| datediff | `datediff(end, start)` | 日期差 (天数) |
| date_trunc | `date_trunc(date, unit)` | 截断到 unit 粒度 |
| current_date | `current_date()` | 当前日期 |
| current_timestamp | `current_timestamp()` | 当前时间戳 |
| to_date | `to_date(str, fmt)` | 字符串转日期 |
| to_timestamp | `to_timestamp(str, fmt)` | 字符串转时间戳 |
| date_format | `date_format(date, fmt)` | 日期格式化 |
| unix_timestamp | `unix_timestamp(ts)` | 转 Unix 时间戳 |
| from_unixtime | `from_unixtime(unix, fmt)` | Unix 时间戳转日期 |
| add_months | `add_months(date, n)` | 加 n 月 |
| months_between | `months_between(end, start)` | 月份差 |
| last_day | `last_day(date)` | 当月最后一天 |
| next_day | `next_day(date, weekday)` | 下一个指定星期几 |

**条件/逻辑函数 (10 个)**：
| 函数 | 签名 | 说明 |
|------|------|------|
| if | `if(condition, true_val, false_val)` | 条件分支 |
| case | `case(cond1, val1, cond2, val2, ..., default)` | 多条件 |
| coalesce | `coalesce(a, b, ...)` | 第一个非空值 |
| nullif | `nullif(a, b)` | a==b 则返回 NULL |
| ifnull | `ifnull(a, b)` | 如果 a 为 NULL 返回 b |
| nvl | `nvl(a, b)` | NULL 替换 |
| nvl2 | `nvl2(a, b, c)` | a 非NULL返回b，否则c |
| isnull | `isnull(a)` | 判断是否为 NULL |
| isnotnull | `isnotnull(a)` | 判断是否非 NULL |
| decode | `decode(expr, key1, val1, ..., default)` | 键值映射 |

**数组/结构体函数 (15 个)**：
| 函数 | 签名 | 说明 |
|------|------|------|
| array | `array(e1, e2, ...)` | 构造数组 |
| array_contains | `array_contains(arr, elem)` | 是否包含元素 |
| array_join | `array_join(arr, delimiter)` | 数组拼字符串 |
| array_append | `array_append(arr, elem)` | 追加元素 |
| array_prepend | `array_prepend(arr, elem)` | 前置元素 |
| explode | `explode(arr)` | 展开数组为多行 |
| size | `size(arr)` | 数组大小 |
| cardinality | `cardinality(arr)` | 数组元素数 |
| element_at | `element_at(arr, idx)` | 取下标元素 |
| sort_array | `sort_array(arr)` | 数组排序 |
| slice | `slice(arr, start, len)` | 数组切片 |
| map | `map(k1, v1, k2, v2, ...)` | 构造 Map |
| map_keys | `map_keys(m)` | Map 所有 key |
| map_values | `map_values(m)` | Map 所有 value |
| struct | `struct(f1, f2, ...)` | 构造结构体 |

**窗口函数 (12 个)**：
| 函数 | 签名 | 说明 |
|------|------|------|
| row_number | `row_number()` | 行号 |
| rank | `rank()` | 排名 (有间隔) |
| dense_rank | `dense_rank()` | 排名 (无间隔) |
| lead | `lead(col, offset, default)` | 取后行值 |
| lag | `lag(col, offset, default)` | 取前行值 |
| first_value | `first_value(col)` | 窗口首个值 |
| last_value | `last_value(col)` | 窗口最后值 |
| nth_value | `nth_value(col, n)` | 窗口第 n 个值 |
| percent_rank | `percent_rank()` | 百分位排名 |
| cume_dist | `cume_dist()` | 累积分布 |
| ntile | `ntile(n)` | 分桶 |
| sum_over | `sum(col) over(...)` | 窗口求和 |

**类型转换函数 (8 个)**：
| 函数 | 签名 | 说明 |
|------|------|------|
| cast | `cast(expr as type)` | 类型转换 |
| to_string | `to_string(x)` | 转字符串 |
| to_int | `to_int(x)` | 转整数 |
| to_long | `to_long(x)` | 转长整数 |
| to_double | `to_double(x)` | 转双精度 |
| to_float | `to_float(x)` | 转浮点 |
| to_decimal | `to_decimal(x, p, s)` | 转十进制 |
| to_boolean | `to_boolean(x)` | 转布尔 |

**合计: 120+ PB 函数**

### 3.4 UDF 脚本转换组件

**设计理念**：用户可以在 Pipeline 编辑器中（1）直接写 Python/Java UDF 脚本，（2）或从已有的 SQL 查询结果中一键转换为可复用的 UDF 算子。

**UDF 类型**：
```
UDF
├── Python UDF    — def transform(df: DataFrame, params: dict) -> DataFrame:
├── SQL UDF       — CREATE FUNCTION fn_name(input_type) RETURNS output_type AS $$ ... $$
├── Java UDF      — @UDF public class MyTransform implements TransformStep { ... }
└── Expression UDF — 由多个 PB 表达式组合成的虚拟函数
```

**脚本转换器功能**：
1. SQL → UDF：选中 SQL 查询 → 自动生成 Python UDF 骨架
2. Python ↔ Java：双向翻译（用 AST 模板）
3. UDF 测试：在沙箱中运行测试用例
4. UDF 注册：保存到 pipeline/udfs/ 目录 + 注册到算子面板

### 3.5 Copilot 配置

在数据工作台配置页新增 **Copilot 分组**：

| 配置键 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `dw.copilot.enabled` | bool | `false` | 启用 Copilot |
| `dw.copilot.provider` | enum | `openai` | AI 提供商 (openai/deepseek/anthropic) |
| `dw.copilot.api_key` | password | `""` | API 密钥 |
| `dw.copilot.model` | string | `gpt-4o` | 模型名称 |
| `dw.copilot.temperature` | float | `0.2` | 创造性 (0-1) |
| `dw.copilot.max_tokens` | int | `4096` | 最大 token 数 |
| `dw.copilot.default_prompt` | string | `你是 ECOS 数据工程专家...` | 默认系统提示词 |

**Copilot 功能点**：
1. **智能 SQL 生成** — 自然语言 → SQL
2. **Pipeline 自动编排** — 需求描述 → Pipeline DAG
3. **表达式建议** — 输入字段名 → 推荐 PB 函数
4. **UDF 生成** — 业务逻辑描述 → Python UDF 代码
5. **错误诊断** — 执行失败后自动分析日志并建议修复
6. **数据探索** — "这个表里有哪些异常值？"

---

## 四、数据库设计

### 4.1 新增表

```sql
-- Pipeline 任务表 (V2 架构)
CREATE TABLE ecos_pipeline_task (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    yaml_content TEXT NOT NULL,        -- Pipeline DSL
    git_url VARCHAR(500),              -- Git 仓库 URL
    git_branch VARCHAR(100) DEFAULT 'main',
    git_commit_id VARCHAR(40),         -- 关联 git commit
    status VARCHAR(20) DEFAULT 'DRAFT', -- DRAFT/SAVED/QUEUED/RUNNING/SUCCEEDED/FAILED
    cron_expression VARCHAR(100),      -- 定时调度
    config_json JSONB DEFAULT '{}',
    created_by VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Pipeline 步骤表
CREATE TABLE ecos_pipeline_step (
    id VARCHAR(36) PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL REFERENCES ecos_pipeline_task(id),
    step_order INTEGER NOT NULL,
    node_id VARCHAR(100) NOT NULL,     -- 对应 DSL node.id
    node_type VARCHAR(50) NOT NULL,    -- source/transform/join/aggregate/sink
    config_json JSONB DEFAULT '{}',
    depends_on JSONB DEFAULT '[]',     -- 依赖的 node_id 列表
    position_x FLOAT DEFAULT 0,
    position_y FLOAT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Pipeline 执行记录表
CREATE TABLE ecos_pipeline_run (
    id VARCHAR(36) PRIMARY KEY,
    task_id VARCHAR(36) NOT NULL REFERENCES ecos_pipeline_task(id),
    status VARCHAR(20) DEFAULT 'QUEUED',
    triggered_by VARCHAR(50) DEFAULT 'manual', -- manual/schedule/hook
    total_steps INTEGER DEFAULT 0,
    completed_steps INTEGER DEFAULT 0,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    elapsed_ms INTEGER DEFAULT 0,
    error_msg TEXT,
    log_json JSONB DEFAULT '[]',       -- 执行日志
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Pipeline 步骤执行记录
CREATE TABLE ecos_pipeline_step_run (
    id VARCHAR(36) PRIMARY KEY,
    run_id VARCHAR(36) NOT NULL REFERENCES ecos_pipeline_run(id),
    step_id VARCHAR(36) NOT NULL,
    node_id VARCHAR(100) NOT NULL,
    status VARCHAR(20) DEFAULT 'QUEUED',
    rows_input INTEGER DEFAULT 0,
    rows_output INTEGER DEFAULT 0,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    elapsed_ms INTEGER DEFAULT 0,
    error_msg TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- UDF 注册表
CREATE TABLE ecos_pipeline_udf (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(200) NOT NULL UNIQUE,
    category VARCHAR(50),              -- transform/expression/aggregate
    language VARCHAR(20) DEFAULT 'python', -- python/java/sql
    signature TEXT,                    -- 函数签名 JSON
    source_code TEXT NOT NULL,         -- UDF 源码
    compiled_path VARCHAR(500),        -- 编译后路径
    version INTEGER DEFAULT 1,
    author VARCHAR(100),
    is_shared BOOLEAN DEFAULT false,   -- 团队共享
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- PB 函数定义表 (函数注册中心)
CREATE TABLE ecos_pipeline_function (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    category VARCHAR(50) NOT NULL,     -- string/numeric/date_time/conditional/array/window/casting
    signature TEXT NOT NULL,           -- JSON: [{param, type, required, default}]
    return_type VARCHAR(50),
    description TEXT,
    example TEXT,
    is_builtin BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## 五、API 扩展

### 5.1 Pipeline 工作流 API

```
POST   /api/v1/engine/data/pipeline/tasks              — 创建 Pipeline 任务
GET    /api/v1/engine/data/pipeline/tasks              — 列表
GET    /api/v1/engine/data/pipeline/tasks/{id}         — 详情
PUT    /api/v1/engine/data/pipeline/tasks/{id}         — 更新
DELETE /api/v1/engine/data/pipeline/tasks/{id}         — 删除
POST   /api/v1/engine/data/pipeline/tasks/{id}/run     — 执行
POST   /api/v1/engine/data/pipeline/tasks/{id}/cancel  — 取消
GET    /api/v1/engine/data/pipeline/tasks/{id}/runs    — 执行历史
GET    /api/v1/engine/data/pipeline/runs/{runId}       — 执行详情
GET    /api/v1/engine/data/pipeline/runs/{runId}/steps — 步骤详情
```

### 5.2 Git 集成 API

```
POST   /api/v1/engine/data/pipeline/tasks/{id}/git/commit  — 提交到 Git
POST   /api/v1/engine/data/pipeline/tasks/{id}/git/pull    — 从 Git 拉取
POST   /api/v1/engine/data/pipeline/git/load               — 从 Git URL 加载 Pipeline
GET    /api/v1/engine/data/pipeline/git/branches           — Git 分支列表
POST   /api/v1/engine/data/pipeline/git/branch             — 切换分支
```

### 5.3 UDF API

```
POST   /api/v1/engine/data/udf/register               — 注册 UDF
GET    /api/v1/engine/data/udf/list                    — UDF 列表
GET    /api/v1/engine/data/udf/{id}                    — UDF 详情
PUT    /api/v1/engine/data/udf/{id}                    — 更新 UDF
DELETE /api/v1/engine/data/udf/{id}                    — 删除 UDF
POST   /api/v1/engine/data/udf/{id}/test               — 测试 UDF
POST   /api/v1/engine/data/udf/convert                 — SQL → UDF 转换
```

### 5.4 函数注册 API

```
GET    /api/v1/engine/data/functions                  — 列出所有 PB 函数
GET    /api/v1/engine/data/functions/{category}        — 按类别
GET    /api/v1/engine/data/functions/search?q=xxx      — 搜索函数
```

### 5.5 Copilot API

```
POST   /api/v1/engine/data/copilot/sql                 — NL → SQL
POST   /api/v1/engine/data/copilot/pipeline            — NL → Pipeline DSL
POST   /api/v1/engine/data/copilot/expression          — 表达式建议
POST   /api/v1/engine/data/copilot/udf                 — NL → UDF 代码
POST   /api/v1/engine/data/copilot/diagnose            — 错误诊断
```

---

## 六、前端改造清单

### 6.1 PipelineFlowEditor 增强

| 改动 | 说明 |
|------|------|
| 集成 Monaco Editor | 替换节点属性面板中的 expression 输入框，支持语法高亮+自动补全 |
| 算子面板改造 | PropertyPanel 的 12 个函数 → 120+ 函数，支持搜索/分类/收藏 |
| Git 集成 | 顶部工具栏增加 "Git 提交" 按钮，Save 按钮增加 "提交到 Git" checkbox |
| 执行监控 | 画布节点实时状态轮询 (idle/queued/running/succeeded/failed) |
| UDF 面板 | 右侧新增 "自定义函数" 面板，支持拖拽创建和配置 |

### 6.2 新增组件

| 组件 | 说明 |
|------|------|
| `ExpressionEditor.tsx` | Monaco 为核心的单行/多行表达式编辑器 |
| `OperatorSearchPanel.tsx` | 搜索+分类浏览 120+ PB 函数 |
| `UdfBuilderPanel.tsx` | SQL→UDF 转换 + UDF 注册/测试 |
| `CopilotPanel.tsx` | AI 辅助面板 (对话式) |
| `PipelineExecutionMonitor.tsx` | 执行进度+节点状态可视化 |
| `GitCommitDialog.tsx` | Git 提交对话框（commit message + 文件列表） |

### 6.3 清理

| 文件 | 操作 |
|------|------|
| `PipelineBuilder.tsx` (414行 SVG版) | 删除 |
| `PipelineBuilderPrototype.tsx` (78行 stub) | 删除 |
| `PipelineBuilderTab.tsx` | 重命名为 `PipelineFlowEditorTab.tsx` |

---

## 七、遗漏项提醒 (请你确认)

1. ⚠ **Pipeline 权限控制** — 谁可以编辑/执行/删除 Pipeline？需要 RBAC 集成吗？
2. ⚠ **多租户隔离** — Pipeline 是否按 tenant_id 隔离？（现有系统有 tenant_id 字段）
3. ⚠ **Cron 调度** — 是否复用 runtime TaskScheduler 的 Cron？需不需要 Pipeline 级别的调度 CRUD UI？
4. ⚠ **执行日志** — 执行日志存 DB 还是 ElasticSearch？前端如何展示实时日志流？
5. ⚠ **断点续跑** — 失败后从哪个 step 重试？是否支持？
6. ⚠ **Pipeline 版本历史** — Git 自然有版本，但 DB 中是否需要保留历史版本快照？
7. ⚠ **大文件/大数据** — Pipeline 产生的中间结果是否能预览？采样还是全量？
8. ⚠ **通知/告警** — Pipeline 失败/成功是否通过 runtime IAlertService 发通知？
9. ⚠ **Doris 执行引擎** — 当前只有 memory 模式，Doris 模式何时实现？
10. ⚠ **Pipeline 模板市场** — 是否需要预置模板（如电商ETL模板）？
11. ⚠ **JSON/YAML 编辑器** — 是否需要一个 DSL Editor (Monaco) 直接编辑 pipeline.yaml？

---

## 八、Sprint 分期

| Sprint | 内容 | 工期预估 |
|--------|------|---------|
| **Sprint P1** | BE: PB函数注册表 (120+ 函数入库) + 函数 API | 1天 |
| **Sprint P2** | BE: Pipeline Task CRUD (ecos_pipeline_task/step 表 + Controller) | 1天 |
| **Sprint P3** | BE: Git 集成实现 (sysman GitService 改造为真正 JGit 实现) + 文件结构 | 2天 |
| **Sprint P4** | BE: UDF 服务 (注册/测试/转换) | 1天 |
| **Sprint P5** | BE: Copilot 服务 (NL→SQL/DSL/UDF) | 1天 |
| **Sprint F1** | FE: ExpressionEditor (Monaco 集成) + OperatorSearchPanel | 1天 |
| **Sprint F2** | FE: PipelineFlowEditor Git 集成 (保存→Git 提交工作流) | 1天 |
| **Sprint F3** | FE: UdfBuilderPanel + CopilotPanel | 1天 |
| **Sprint F4** | FE: PipelineExecutionMonitor 实时监控 | 1天 |
| **Sprint INT** | 端到端集成验证 | 1天 |

---

## 九、下一步

- [ ] 用户确认遗漏项
- [ ] ARCH 出架构设计（Git 存储 / UDF 编译器 / Copilot 集成）
- [ ] BE 开工 Sprint P1
- [ ] FE 开工 Sprint F1 (同步进行)
