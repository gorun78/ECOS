# runtime-task (器·全局任务调度) 接口与验收 flows

> 横切底座·器 | cloudless (随 gateway fat-JAR) | 统一调度: 取代各引擎 ScheduledExecutorService
> 源码: ITaskManagementService / TaskSchedulerService / ITaskExecutor / ITaskStatusCallback / TaskLogService / TaskMonitoringService / TaskStatisticsService

## 接入 flows
client → Gateway `TaskController` (:8080, `/api/v1/task/*`) → `ITaskManagementService` (runtime-task) → executor → callback 回报 → 回 `ApiResponse`。
所有定时/周期任务统一委托本模块 (铁律 2.5 #3), 引擎内禁止自建 ScheduledExecutorService; 计划类 agent 任务实现 `ITaskStatusCallback` 注入 (现存样例 AgentCronTaskExecutor), 不另起调度器。

## 主 API (curl)
```bash
curl -s "http://localhost:8080/api/v1/task/list?offset=0&limit=20&status=RUNNING" -H "Authorization: Bearer $TOKEN"
curl -s -X POST "http://localhost:8080/api/v1/task/submit" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"taskName":"dq-nightly","taskType":"DATA_QUALITY","parameters":{"table":"sales"}}'
curl -s -X POST "http://localhost:8080/api/v1/task/{taskId}/execute" -H "Authorization: Bearer $TOKEN"
```

## 接 DB 表
`ecos_task` (V23__ecos_task.sql); 状态/持久化经 `ITaskPersistenceService` (database 实现, 构造器注入 JdbcTemplate/MyBatis)。

## 别接 (调谁, 已核)
- 引擎禁止自建 `ScheduledExecutorService` — 任务定义交 TaskSchedulerService, 结果回报走 ITaskStatusCallback
- 不自建监控 (走 runtime-monitor), 任务日志走 TaskLogService (落 runtime-core logging 体系)
- executor 不跨引擎 import 业务 Service (回调/接口隔离), 凭据/Driver 一律走 runtime-access

## 验收 flows
`POST /api/v1/task/submit` 返回 taskId, `GET /api/v1/task/{taskId}/status` 从 SUBMITTED → RUNNING → COMPLETED 三态流转可查;
`grep -rn "ScheduledExecutorService" engine/ --include=*.java` (排除 runtime) 接受 NO_HIT 即引擎自建调度清零通过。发布事件: 任务终态 → `PipelineEvent.of(STATUS_CHANGED, ...).fromModule("runtime-task")`。
