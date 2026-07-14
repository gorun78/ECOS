# ECOS 平台三块短板补齐 — PMO指令

> **来源**: 肖国荣 全量审计结论  
> **日期**: 2026-06-27  
> **前提**: 已完成逐Controller审计，底盘结论见附件  
> **铁律**: Git提交=唯一绩效。DONE=commit hash+curl 200。不允许产出解释性文档。  

---

## 背景

DIKW四层全量审计结论：36个Controller真实可用，但缺三块导致数据工程师无法完成"不改代码只换配置"的落地路径：

| 缺口 | 影响 | 优先级 |
|------|------|:--:|
| Pipeline后端缺失（只有1个Event类） | 数据工程师无法创建数据管道，企业数据流不进ECOS | P0 |
| DQ规则只能CRUD不能执行 | 质量监控形同虚设 | P1 |
| Agent Builder只有后端API，前端缺配置页面 | 智能配置能力被埋没 | P1 |

---

## P0: Pipeline后端（2周）

> **目标**: 数据工程师可在PipelineBuilder前端创建Pipeline→保存→执行→查看结果  
> **模块**: databridge-datanet（扩展现有datanet模块，复用ConnectorFactory/JdbcConnector）  
> **参考**: 
> - 前端PipelineBuilder.tsx (375行) — 已有DAG画布，但后端API为空
> - 现有DataSourceController/MetadataController — 参考代码风格（JdbcTemplate+ApiResponse）
> - 现有ConnectorFactory/JdbcConnector — 复用数据库连接能力

### 任务拆解

| ID | 任务 | 文件 | 工作量 |
|:--|------|------|:--:|
| P0-1 | Pipeline定义实体 | `PipelineDefinition.java` | 0.5d |
| P0-2 | Pipeline节点实体 | `PipelineNode.java` | 0.5d |
| P0-3 | Pipeline执行记录实体 | `PipelineExecution.java` | 0.5d |
| P0-4 | Pipeline建表SQL | `V__pipeline.sql` | 0.5d |
| P0-5 | PipelineRepository | `PipelineRepository.java` | 0.5d |
| P0-6 | PipelineService接口 | `PipelineService.java` (api模块) | 0.5d |
| P0-7 | PipelineServiceImpl | `PipelineServiceImpl.java` | 2d |
| P0-8 | PipelineExecutionService | `PipelineExecutionService.java` | 2d |
| P0-9 | PipelineController | `PipelineController.java` | 1d |
| P0-10 | 前端API对接 | 修改`PipelineBuilder.tsx`对接真实API | 1d |
| P0-11 | SecurityConfig白名单 | 加 `/api/pipeline/**` | 0.5d |

### 验收标准

```bash
# P0-9: 创建Pipeline
curl -X POST http://localhost:8081/sys-man/api/pipeline/definitions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "name": "供应商数据导入",
    "nodes": [
      {"id":"n1","type":"SOURCE_JDBC","config":{"datasourceId":"ds001","sql":"SELECT * FROM supplier"}},
      {"id":"n2","type":"TRANSFORM_SQL","config":{"sql":"SELECT *, NOW() as ingested_at FROM source"}},
      {"id":"n3","type":"OUTPUT_OBJECT","config":{"entityCode":"Supplier"}}
    ],
    "edges": [{"from":"n1","to":"n2"},{"from":"n2","to":"n3"}]
  }'
# 期望: 200 + 返回pipelineId

# P0-8: 执行Pipeline
curl -X POST http://localhost:8081/sys-man/api/pipeline/definitions/{pipelineId}/execute \
  -H "Authorization: Bearer $TOKEN"
# 期望: 200 + 返回executionId + status=RUNNING

# 查询执行状态
curl http://localhost:8081/sys-man/api/pipeline/executions/{executionId} \
  -H "Authorization: Bearer $TOKEN"
# 期望: 200 + status=COMPLETED + rowsProcessed > 0
```

### 技术约束

1. **模块位置**: 在`databridge-datanet/datanet-impl`下新建`pipeline/`包
2. **持久化**: JdbcTemplate直接操作PG，三张表`ecos_pipeline_definition`/`ecos_pipeline_node`/`ecos_pipeline_execution`
3. **执行模式**: 同步执行（MVP不引入调度器），按DAG拓扑序串行执行
4. **最小节点集**: SOURCE_JDBC / TRANSFORM_SQL / OUTPUT_OBJECT 三种
5. **不引入**: Kafka/Doris/Spark。只用现有PG+JdbcTemplate
6. **错误处理**: 任一节点失败则Pipeline标记FAILED，记录错误信息到execution记录

---

## P1-1: DQ规则执行引擎（1周）

> **目标**: 为DqDashboardController增加规则执行功能，从"能存不能跑"升级为"能存能跑"  
> **模块**: databridge-gateway（扩展DqDashboardController）  
> **参考**: DqDashboardController.java (217行，10次JdbcTemplate调用)

### 任务拆解

| ID | 任务 | 文件 | 工作量 |
|:--|------|------|:--:|
| P1-1.1 | DQ执行Service | `DqExecutionService.java` | 1.5d |
| P1-1.2 | DQ执行结果实体+表 | `DqExecutionResult.java` + DDL | 0.5d |
| P1-1.3 | Controller增加3个端点 | 修改`DqDashboardController.java` | 1d |
| P1-1.4 | 前端DQ Dashboard增加"执行"按钮 | 修改`DataQualityDashboard.tsx` | 1d |

### 验收标准

```bash
# 执行单条规则
curl -X POST http://localhost:8081/sys-man/api/dq/rules/{ruleId}/execute \
  -H "Authorization: Bearer $TOKEN"
# 期望: 200 + {"passed":true,"totalRows":150,"failedRows":3,"errors":[...]}

# 批量执行全部规则
curl -X POST http://localhost:8081/sys-man/api/dq/execute-all \
  -H "Authorization: Bearer $TOKEN"
# 期望: 200 + {"totalRules":8,"passed":6,"failed":2,"executionId":"..."}

# 查看执行历史
curl http://localhost:8081/sys-man/api/dq/executions?ruleId={ruleId}&limit=10 \
  -H "Authorization: Bearer $TOKEN"
# 期望: 200 + [{"executionId":"...","ruleId":"...","passed":true,"executedAt":"..."}]
```

### 技术约束

1. **规则类型最少支持两种**: NOT_NULL（字段非空检查）、RANGE（数值范围检查）
2. **执行逻辑**: 读`ecos_dq_rule`中的`rule_expression`→拼SQL→在`target_entity`对应表上执行→统计通过/失败行数
3. **不引入**: 调度器（手动触发即可）、Drools规则引擎（MVP用SQL表达式）
4. **结果存储**: 新表`ecos_dq_execution_result`（rule_id/passed/total_rows/failed_rows/error_details/executed_at）

---

## P1-2: Agent Builder前端（1周）

> **目标**: 新增Agent配置表单页+测试控制台，对接已有AgentConfigController的9个端点  
> **后端API已全部就绪**，前端只需调已有接口  
> **参考**: 
> - AgentConfigController.java (148行) — 已有POST/PUT/DELETE/GET + 工具绑定 + 知识库绑定 + Prompt版本 + 测试
> - AgentStudio.tsx (614行) — 参考现有Agent展示页面的UI风格

### 任务拆解

| ID | 任务 | 文件 | 工作量 |
|:--|------|------|:--:|
| P1-2.1 | API服务层 | `src/services/agentConfig.ts` | 0.5d |
| P1-2.2 | Agent配置表单页 | `src/pages/AgentBuilder.tsx` | 2d |
| P1-2.3 | Agent测试控制台 | `src/pages/AgentTestConsole.tsx` 或嵌入Builder | 1d |
| P1-2.4 | App.tsx注册路由 | 修改`src/App.tsx` | 0.5d |
| P1-2.5 | AgentStudio增加"新建"按钮跳转 | 修改`src/pages/AgentStudio.tsx` | 0.5d |

### 验收标准

```bash
# 前端页面验证（浏览器操作，非curl）
# 1. 侧边栏出现"Agent Builder"入口 → 点击进入配置表单页
# 2. 表单包含: Agent名称输入框 + Model选择下拉(deepseek-v4-flash等) + 
#    SystemPrompt编辑区(CodeMirror) + Tool多选列表(从/api/agent/tools加载) +
#    Knowledge绑定下拉 + Temperature滑块
# 3. 填写→点击"保存"→调用POST /api/v1/agents → 返回200+新Agent出现在AgentStudio列表
# 4. 点击"测试"→进入测试控制台→输入消息→调用POST /api/v1/agents/{id}/test → 显示回复+工具调用trace
```

### 技术约束

1. **UI风格**: 复用现有组件（Modal/DataTable/LoadingSkeleton），保持与AgentStudio一致
2. **API全部现成**: AgentConfigController的9个端点无需后端改动
3. **Tool列表**: 从`GET /api/agent/tools`动态获取
4. **不引入**: 拖拽编排（那是P2的事）、A/B测试功能

---

## 资源与排期

| 轨道 | 内容 | 工时 | 建议并行 |
|------|------|:--:|:--:|
| P0 | Pipeline后端 | 10d | BE 1人 |
| P1-1 | DQ执行引擎 | 4d | BE 1人（可与P0同人串行，或另人并行） |
| P1-2 | Agent Builder前端 | 4.5d | FE 1人（可与BE完全并行） |

```
Week 1:  P0 (BE) ∥ P1-2 (FE)           — BE做Pipeline实体+Service，FE做Agent Builder页面
Week 2:  P0 (BE) ∥ P1-1 (BE)           — BE完成Pipeline Controller+执行，同时做DQ执行引擎
         P1-2收尾                          — FE完成Agent Builder路由注册+集成测试
```

**总工期**: 2周（并行）  
**交付物**: 
- 3个新Controller/Service（Pipeline + DQ执行）
- 2个新前端页面（Agent Builder + Agent测试控制台）
- 1个前端页面改造（PipelineBuilder对接真实API）
- curl全端点验证通过

---

## 禁止清单

1. **禁止**产出超过本指令以外的任何设计文档、评审报告、进度报告
2. **禁止**引入新基础设施（Neo4j/Doris/MinIO/Kafka/Docker新容器）
3. **禁止**创建新的Maven模块——全部在现有datanet/gateway/aimod模块内扩展
4. **禁止**修改已有API的路径或参数签名——只增不改
5. **禁止**写MockLLMClient——Agent测试用真实DeepSeek API

## 验收方式

两周后肖总亲自检查：
1. `git log --since="2026-06-27" --format="%h %ai %s"` — 看提交节奏
2. `curl` 本指令中的每条验收命令 — 看端点响应
3. 浏览器打开ECOS — 走通"创建Pipeline → 执行 → 查看DQ结果 → 新建Agent → 测试对话"全流程
