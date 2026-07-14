# ECOS 数据工作台 — QA 全功能验证清单

> 版本: v2.0 | 日期: 2026-07-11 | 作者: ECOS-PMO
> 验证范围: Pipeline 2.0 + 配置体系 + SQL 查询 + Git 统一

---

## 一、环境确认

| 项目 | 预期值 | 验证命令 |
|------|--------|---------|
| Gateway | port 8080, 启动无异常 | `curl http://localhost:8080/actuator/health` |
| Vite Dev | port 3000 | `curl http://localhost:3000/` |
| PostgreSQL | localhost:5432 | Gateway 启动日志无 JDBC 错误 |

---

## 二、数据工作台配置体系 (Sprint 2.1)

### 2.1 配置 API

| # | 端点 | 预期结果 |
|---|------|---------|
| 1 | `GET /api/v1/engine/data/settings` | 返回 7 分组: execution/lake/storage/pipeline/quality/lineage/general |
| 2 | `GET /api/v1/engine/data/settings/defaults` | 返回 69 项默认值 |
| 3 | `GET /api/v1/engine/data/settings/data-engine` | 返回 data-engine 分组配置 |
| 4 | `PUT /api/v1/engine/data/settings` + body `[{"config_key":"dw.execution.timeout","config_value":"999"}]` | updated=1, 再次 GET 验证值为 999 |
| 5 | `POST /api/v1/engine/data/settings/refresh` | refreshed=true, cache_size>0 |

### 2.2 配置页面 (前端)

| # | 操作 | 预期结果 |
|---|------|---------|
| 6 | 进入数据工作台 → 点击左侧底部 "⚙ 引擎配置" | 打开配置面板，显示 7 个分组标签页 |
| 7 | 切换分组 (execution/lake/storage/...) | 各分组表单正确渲染，默认值显示 |
| 8 | 修改 execution.timeout 为 900 → 点击保存 | Toast "保存成功"，API 返回 updated:1 |
| 9 | 切换到 Copilot 分组 | 显示 copilot.enabled/provider/model 等 5 项 |
| 10 | 切换到 Pipeline 分组 (新增) | 显示 log_storage/resume_enabled/keep_history/monaco_theme 等 13 项 |

---

## 三、SQL 查询控制台 (Sprint 2.0-2.1)

| # | 操作 | 预期结果 |
|---|------|---------|
| 11 | 进入数据工作台 → 连接 → 点击数据源 | 数据源详情底部显示 SQL 编辑框 + 执行按钮 |
| 12 | 输入 `SELECT 1 AS test_val` → 执行 | 结果显示表格: test_val=1 |
| 13 | 输入语法错误 SQL → 执行 | 显示错误信息，不崩溃 |
| 14 | 左侧 Schema 树 → 展开数据源 → 展开表 → 字段 | 树形结构正确，表名/字段名与 DB 一致 |
| 15 | GET /api/v1/engine/data/query/execute (POST) | 返回 ApiResponse，含 columns + rows |

---

## 四、Pipeline 2.0 新增端点

### 4.1 PB 函数注册表

| # | 端点 | 预期 |
|---|------|------|
| 16 | `GET /api/v1/engine/data/functions` | 返回 120+ 条函数 |
| 17 | `GET /api/v1/engine/data/functions/string` | 只返回字符串类函数 (25 个) |
| 18 | `GET /api/v1/engine/data/functions/search?q=regex` | 返回含 regex 的函数 |

### 4.2 Pipeline Task API

| # | 端点 | 预期 |
|---|------|------|
| 19 | `POST /api/v1/engine/data/pipeline/tasks` | 创建成功，返回 taskId |
| 20 | `GET /api/v1/engine/data/pipeline/tasks` | 返回任务列表 |
| 21 | `GET /api/v1/engine/data/pipeline/tasks/{id}` | 返回任务详情含 yaml_content |
| 22 | `PUT /api/v1/engine/data/pipeline/tasks/{id}` | 更新成功 |
| 23 | `POST /api/v1/engine/data/pipeline/tasks/{id}/run` | 返回 runId |
| 24 | `GET /api/v1/engine/data/pipeline/tasks/{id}/runs` | 返回执行历史 |
| 25 | `GET /api/v1/engine/data/pipeline/runs/{runId}` | 返回执行详情含状态 |
| 26 | `POST /api/v1/engine/data/pipeline/tasks/{id}/cancel` | 取消成功 |
| 27 | `DELETE /api/v1/engine/data/pipeline/tasks/{id}` | 删除成功 |

### 4.3 UDF API

| # | 端点 | 预期 |
|---|------|------|
| 28 | `POST /api/v1/engine/data/udf/register` | 创建 UDF 成功 |
| 29 | `GET /api/v1/engine/data/udf/list` | 返回 UDF 列表 |
| 30 | `GET /api/v1/engine/data/udf/{id}` | 返回 UDF 详情 + source_code |
| 31 | `POST /api/v1/engine/data/udf/convert` | SQL→UDF 转换成功 |

### 4.4 Copilot API

| # | 端点 | 预期 |
|---|------|------|
| 32 | `POST /api/v1/engine/data/copilot/sql` | NL→SQL 响应 |
| 33 | `POST /api/v1/engine/data/copilot/expression` | 表达式建议响应 |
| 34 | `POST /api/v1/engine/data/copilot/diagnose` | 错误诊断响应 |

---

## 五、Pipeline 前端

### 5.1 PipelineFlowEditor 主界面

| # | 操作 | 预期 |
|---|------|------|
| 35 | 进入数据工作台 → Pipeline Builder | ReactFlow 画布 + 左侧节点工具栏 + 右侧属性面板均渲染 |
| 36 | 从左侧拖拽 Source → 画布 | 创建蓝色 Source 节点 |
| 37 | 从左侧拖拽 Transform → 画布 | 创建绿色 Transform 节点 |
| 38 | 点击 Source Handle → 拖线到 Transform | 有向边连接 |
| 39 | 点击 Transform 节点 | 右侧属性面板显示配置 |
| 40 | 在 ExpressionEditor 输入 `filter(status=="active")` | Monaco 编辑器高亮 + 自动补全 PB 函数 |
| 41 | 点击 OperatorSearchPanel 搜索 "datediff" | 显示 datediff 函数 + 签名 + 说明 |
| 42 | Ctrl+S 保存 | 保存成功 Toast |

### 5.2 Git 集成

| # | 操作 | 预期 |
|---|------|------|
| 43 | 点击顶部 "保存到 Git" | 弹出 GitCommitDialog |
| 44 | 输入 commit message → 提交 | commit 成功 |
| 45 | 点击 "从 Git 加载" | 弹出 URL 输入框 |

### 5.3 UDF 面板

| # | 操作 | 预期 |
|---|------|------|
| 46 | 打开右侧 UdfBuilderPanel | 左侧代码输入 + 右侧预览 |
| 47 | 输入 SQL → 点击转换 | 生成 Python UDF 骨架代码 |
| 48 | 点击注册 | Toast "UDF 注册成功" |

### 5.4 Copilot 面板

| # | 操作 | 预期 |
|---|------|------|
| 49 | 打开 CopilotPanel | 聊天式对话框渲染 |
| 50 | 输入 "filter active users" | 返回 Pipeline DSL 建议 |

### 5.5 执行监控

| # | 操作 | 预期 |
|---|------|------|
| 51 | 执行 Pipeline | 节点颜色变化 (queued→running→succeeded) |
| 52 | 点击运行中节点 | 弹出步骤详情 (输入/输出行数/耗时) |

---

## 六、输出要求

1. 每个验证项标注 ✅ (通过) / ❌ (失败) / ⚠️ (部分)
2. 失败项标注错误信息 + 日志片段
3. 最终输出通过率 `PASS/52 (%)`
