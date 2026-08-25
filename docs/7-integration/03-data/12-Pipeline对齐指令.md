# PMO-3J: Pipeline 编辑器对齐 P2-01 规范 + 执行引擎补齐

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> **P2-01 规范**: [Pipeline YAML Schema 规范](./P2-01-Pipeline-Schema规范.md)
> 来源: 肖国荣 | 日期: 2026-08-25
> 铁律: ①架构规则 2.5 基础设施访问收敛 runtime-access（Pipeline 节点走 ConnectorFactory，禁系统 JdbcTemplate）②架构规则 2.3 定时任务走 runtime-task ③架构规则 4.3 i18n ④P2-01 节点类型枚举是唯一标准

## §背景

Pipeline 三方脱节（摸底确认）：

| 层 | 节点类型 | 数量 | 问题 |
|----|---------|:---:|------|
| P2-01 规范 | SOURCE_JDBC / SOURCE_CSV / SOURCE_REST / SOURCE_CDC / TRANSFORM_SQL / OUTPUT_OBJECT | 6 | 唯一标准 |
| 前端编辑器 | source / transform / join / aggregate / sink | 5 | **命名完全不同**，config 字段对不上 |
| 后端执行引擎 | SOURCE_JDBC / TRANSFORM_SQL / OUTPUT_OBJECT | 3 | **缺 CSV/REST**，SOURCE_JDBC 用系统 JdbcTemplate 而非 ConnectorFactory |

前端 PropertyPanel 配 `sourceTable`/`transformRules`/`joinConditions`，P2-01 要求 `datasourceId`/`sql`/`fetchSize`/`incrementalColumn`。保存时前后端 node.data 与 PipelineNode.config 是两套结构。

后端 PipelineExecutionService.executeSourceJdbc() 第 134 行 `jdbc.execute(sql)` 用的是系统库 JdbcTemplate，注释写着"简化处理"，违反架构规则 2.5。Pipeline 调度无 runtime-task 接入（P2-01 schedule.cron 字段无消费方），executePipeline() 同步阻塞。

ConnectorFactory 三个 Connector（JdbcConnector/CsvConnector/RestApiConnector）已存在且可用，但执行引擎没调。

## §Task

### T1 — 前端节点类型对齐 P2-01（NodePalette + constants + types）

**文件**：
- `pipeline-editor/constants.tsx`（buildPaletteItems / PALETTE_LABELS）
- `pipeline-editor/types.ts`（NodeConfig）

**操作**：
1. buildPaletteItems 改为 6 种节点（对齐 P2-01）：

| type | label | icon | 说明 |
|------|-------|------|------|
| SOURCE_JDBC | JDBC 数据源 | Database | 关系型数据库抽取 |
| SOURCE_CSV | CSV 文件 | FileText | CSV/TSV 文件抽取 |
| SOURCE_REST | REST API | Globe | REST API 抽取 |
| SOURCE_CDC | CDC 实时订阅 | Radio | Flink CDC（flagship 版灰显） |
| TRANSFORM_SQL | SQL 转换 | Settings | SQL 转换 |
| OUTPUT_OBJECT | 输出目标 | HardDrive | 写入目标表 |

2. PALETTE_LABELS 同步更新
3. NodeConfig 改为 P2-01 config 字段结构：

```typescript
export interface NodeConfig {
  label: string;
  nodeType: string;  // P2-01 枚举值
  config: {
    // SOURCE_JDBC
    datasourceId?: string;
    sql?: string;
    fetchSize?: number;
    incrementalColumn?: string;
    lastSyncValue?: string;
    // SOURCE_CSV
    filePath?: string;
    delimiter?: string;
    header?: boolean;
    encoding?: string;
    // SOURCE_REST
    url?: string;
    method?: string;
    headers?: Record<string, string>;
    body?: string;
    pagination?: string;
    // TRANSFORM_SQL
    transformSql?: string;
    timeout?: number;
    // OUTPUT_OBJECT
    targetTable?: string;
    mode?: 'append' | 'overwrite';
    batchSize?: number;
  };
  dependsOn?: string[];
  nodeStatus?: NodeStatus;
}
```

4. SOURCE_CDC 在 NodePalette 中加 `disabled` 标记（非 flagship 版灰显，title 提示"仅旗舰版支持"）

**验收**：NodePalette 显示 6 种节点；SOURCE_CDC 灰显；NodeConfig 字段与 P2-01 schema 一一对应；0 硬编码中文。

### T2 — 前端属性面板对齐 P2-01（PropertyPanel）

**文件**：`pipeline-editor/PropertyPanel.tsx`

**操作**：
1. 删除旧的 source/transform/join/aggregate/sink 分支逻辑
2. 按 P2-01 节点类型渲染对应 config 表单：

| 节点类型 | 表单字段 |
|---------|---------|
| SOURCE_JDBC | datasourceId（下拉选 connections）、sql（textarea）、fetchSize（number）、incrementalColumn（text，选填） |
| SOURCE_CSV | filePath（text）、delimiter（text，默认逗号）、header（bool，默认 true）、encoding（text，默认 UTF-8） |
| SOURCE_REST | url（text）、method（enum GET/POST）、headers（key-value 编辑器）、body（textarea）、pagination（text） |
| SOURCE_CDC | 灰显提示"仅旗舰版支持" |
| TRANSFORM_SQL | transformSql（textarea）、timeout（number，默认 30） |
| OUTPUT_OBJECT | targetTable（text）、mode（enum append/overwrite）、batchSize（number，默认 1000） |

3. datasourceId 下拉从 connections（DataConnection[]）取值
4. 删除 TransformRulesEditor / JoinConditionsEditor / AggregateConfigEditor 的引用（不再需要，改走 config.sql）
5. i18n：所有 label 走 `t("dw.xxx")`

**验收**：选中 SOURCE_JDBC 节点→属性面板显示 datasourceId/sql/fetchSize/incrementalColumn；选中 OUTPUT_OBJECT→显示 targetTable/mode/batchSize；0 硬编码中文。

### T3 — 前端保存/加载对齐后端 PipelineNode 结构

**文件**：`PipelineFlowEditor.tsx`、`api.ts`

**操作**：
1. handleSave 时，将 ReactFlow nodes/edges 转换为后端 PipelineController createDefinition 期望的格式：
   - node.data.config → node.config（Map → JSON string 由后端处理）
   - edges → dependsOn（edge.target 依赖 edge.source）
   - node.type → type（P2-01 枚举值）
2. 加载 Pipeline 定义时，后端返回的 nodes（PipelineNode[]）+ dependsOn 转换回 ReactFlow nodes/edges
3. api.ts 的 createPipeline/updatePipeline 确保传 `{ name, nodes, edges }` 结构

**验收**：保存 Pipeline → 后端 ecos_pipeline_definition 落库 + ecos_pipeline_node 落库；重新打开 → 画布恢复节点+连线；dependsOn 正确。

### T4 — 后端执行引擎补齐 CSV/REST + SOURCE_JDBC 走 ConnectorFactory

**文件**：`engine/data-engine/.../pipeline/PipelineExecutionService.java`

**操作**：
1. executeNode() 的 switch 补 `SOURCE_CSV` / `SOURCE_REST` 分支
2. executeSourceJdbc() **修复架构违规**：
   - 从 config 取 datasourceId → 查 td_datasource 获取 connectionConfig → `connectorFactory.getConnector("JDBC")` → 用 Connector 建立连接执行 SQL
   - 删除 `jdbc.execute(sql)`（系统库 JdbcTemplate），改走 Connector 的连接
   - 返回真实影响行数
3. 新增 executeSourceCsv()：
   - `connectorFactory.getConnector("SOURCE_CSV")` → CsvConnector 读取文件 → 返回行数据
   - config: filePath/delimiter/header/encoding
4. 新增 executeSourceRest()：
   - `connectorFactory.getConnector("SOURCE_REST")` → RestApiConnector 调用 API → 返回行数据
   - config: url/method/headers/body
5. SOURCE_CDC 暂不实现（flagship 版，throw "CDC 仅旗舰版支持"）
6. executeOutputObject() 保持现有逻辑（已有真实 INSERT）

**验收**：
- SOURCE_JDBC 节点执行 → 走 ConnectorFactory 连外部数据源（日志可见 `Connector type: JDBC`），不再用系统 JdbcTemplate
- SOURCE_CSV 节点执行 → CsvConnector 读取文件，返回行数
- SOURCE_REST 节点执行 → RestApiConnector 调用 API，返回行数
- `grep "jdbc.execute(sql)" PipelineExecutionService.java` = 0（架构违规已清除）

### T5 — Pipeline 执行走 runtime-task 全闭环（提交→调度→执行→回调→监控）

**充分利用 runtime 能力，不是简单"注册到 runtime-task"**。Pipeline 执行从直接调 `executePipeline()` 改为走 runtime-task 的完整生命周期。

**文件**：
- 新建 `engine/data-engine/.../pipeline/PipelineTaskExecutor.java`（实现 ITaskExecutor）
- 修改 `PipelineExecutionService.java`（对接 ITaskStatusCallback）
- 修改 `PipelineController.java` 或 `PipelineTaskController.java`（提交走 ITaskManagementService）

**5a — PipelineTaskExecutor 实现 ITaskExecutor（注册到 runtime-task）**

```java
@Component
public class PipelineTaskExecutor implements ITaskExecutor {

    @Autowired
    private PipelineExecutionService executionService;

    @Override
    public String execute(TaskExecutionPlan plan, ITaskStatusCallback callback) {
        // 从 plan.getParameters() 取 definitionId
        String definitionId = (String) plan.getParameters().get("definitionId");
        // 执行前回调
        callback.onStatusUpdate(TaskStatus.running(taskId));
        callback.onProgressUpdate(taskId, 0, "Pipeline 执行开始");
        // 调 PipelineExecutionService，传入 callback
        PipelineExecution exec = executionService.executePipeline(definitionId, callback);
        // 完成回调
        callback.onTaskComplete(taskId, "COMPLETED".equals(exec.getStatus()),
                exec.getId(), exec.getErrorMessage());
        return exec.getId();
    }
    // cancel/pause/resume/getStatus 透传 executionService
}
```

在 data-engine 启动时注册：`taskManagementService.registerExecutor("PIPELINE", pipelineTaskExecutor);`

**5b — PipelineExecutionService 对接 ITaskStatusCallback**

executePipeline() 签名增加 callback 参数（可选，向后兼容）：

```java
public PipelineExecution executePipeline(String definitionId, ITaskStatusCallback callback)
```

每个 DAG 节点执行时：
- `callback.onStepStart(taskId, node.getNodeId(), node.getType())` — 节点开始
- `callback.onStepComplete(taskId, node.getNodeId(), node.getType(), success, msg)` — 节点完成
- `callback.onProgressUpdate(taskId, progress%, "已执行 N/M 节点")` — 整体进度

**5c — Pipeline 调度走 TaskSchedulerService（P2-01 schedule.cron）**

Pipeline 定义保存时，若 schedule.cron 非空：
```java
TaskDescription desc = new TaskDescription();
desc.setTaskType("PIPELINE");
desc.setTaskName(pipelineName);
desc.getParameters().put("definitionId", definitionId);
desc.setAsync(true);
String scheduleId = taskSchedulerService.scheduleTask(desc, cronExpression);
// 存储 scheduleId 到 PipelineDefinition.extensions.scheduleId
```

Pipeline 定义更新/删除时，若 scheduleId 存在 → `taskSchedulerService.cancelSchedule(scheduleId)`。

**5d — Pipeline 执行入口改为 ITaskManagementService**

PipelineController/PipelineTaskController 的执行端点改为：
```java
TaskDescription desc = new TaskDescription();
desc.setTaskType("PIPELINE");
desc.getParameters().put("definitionId", definitionId);
desc.setAsync(true);
String taskId = taskManagementService.submitAndExecute(desc);
// 返回 taskId 给前端，前端轮询 getTaskStatus(taskId) 看进度
```

不再直接调 `executionService.executePipeline()`。

**5e — 执行日志走 ILoggingService + 失败告警走 IAlertService**

- PipelineExecutionService 内部 `log.info()` 改为注入 `ILoggingService.log(LogLevel.INFO, "Pipeline nodeId=... executed")`
- Pipeline 执行失败时调 `IAlertService.createAlert(...)` + `INotificationService.notify(...)`（配置项 `dw.pipeline.alert_on_failure=true` 时触发）
- 删除 Pipeline 内部任何自建 `ScheduledExecutorService` / `new Thread` / `@Scheduled`（架构规则 2.3）

**验收**：
- Pipeline 立即执行 → 返回 taskId（非 executionId），`getTaskStatus(taskId)` 可查状态
- Pipeline 带 cron → `taskSchedulerService.scheduleTask(desc, cron)` 注册成功（日志可见 scheduleId）
- 执行过程中 ITaskStatusCallback 回调链完整：onStepStart → onStepComplete → onProgressUpdate → onTaskComplete
- Pipeline 失败 → IAlertService 有告警记录（`dw.pipeline.alert_on_failure=true` 时）
- `grep -rn "ScheduledExecutorService\|new Thread\|@Scheduled" pipeline/` = 0
- `grep "executePipeline(.*definitionId)" PipelineController.java` 改为走 submitAndExecute

### T6 — 配置项三版本感知 + i18n + 颜色合规

**文件**：`DataEngineConfigPanelTypes.tsx`、`DataEngineConfigPanel.tsx`

**操作**：
1. Doris 配置组（dw.execution.doris.* / dw.execution.mode=doris）在 standard 版灰显或隐藏，enterprise/flagship 版正常显示
   - 通过 `/api/v1/engine/data/settings` 返回的版本信息或前端环境变量判断当前版本
2. DataEngineConfigPanelTypes.tsx 的 label/description 硬编码中文 → `t("dw.xxx")`（ConfigItem 加 i18nKey 字段）
3. DataEngineConfigPanel.tsx 的 toast 消息（"已保存 N 项配置"等）→ i18n
4. 颜色走 useTheme styles（若 PMO-3H 已完成则继承）

**验收**：standard 版 Doris 配置灰显；0 硬编码中文；0 硬编码颜色；配置修改 → 保存 → 刷新后值持久化（sys_config 表验证）。

## §执行顺序

```
T1（前端节点类型）→ T2（前端属性面板）→ T3（前端保存加载）→ T4（后端执行引擎）→ T5（后端调度）→ T6（配置项+合规）
```

T1/T2/T3 是前端链路（节点类型→属性面板→保存加载），必须顺序。T4/T5 是后端链路，T4 先（执行器补齐），T5 后（调度接入）。T6 收尾。

## §禁止清单

1. 用系统 JdbcTemplate 执行外部数据源 SQL（架构规则 2.5，必须走 ConnectorFactory）
2. 自建 ScheduledExecutorService / new Thread / @Scheduled（架构规则 2.3，走 runtime-task）
3. 前端节点类型不按 P2-01 枚举命名
4. 硬编码中文（架构规则 4.3）
5. 硬编码 Tailwind 颜色（架构规则 4.1）
6. 新增 Maven 模块或 Docker 容器
7. 跨 Phase 预创建文件

## §交付

- [ ] 前端 6 种 P2-01 节点类型 + 属性面板 config 字段对齐，commit
- [ ] 前端保存/加载与后端 PipelineNode 结构对齐，commit
- [ ] 后端 SOURCE_JDBC 走 ConnectorFactory + CSV/REST 执行器补齐，commit
- [ ] 后端 Pipeline 调度接入 runtime-task，commit
- [ ] 配置项三版本感知 + i18n，commit
- [ ] curl 验收：建 Pipeline 定义 → 保存 → 执行 → 查 ecos_pipeline_execution 状态 COMPLETED
- [ ] grep 架构违规 = 0（jdbc.execute(sql) / ScheduledExecutorService / @Scheduled）
- [ ] 更新测试报告 + 修复记录
