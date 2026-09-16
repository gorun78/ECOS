# runtime-monitor (器·全局监控) 接口与验收 flows

> 横切底座·器 | cloudless (随 gateway fat-JAR) | 综合监控: 对象采集/策略判定/插件告警
> 源码: IMonitorObjectService / IStrategyService / ICollectionMonitorService / IPluginService / IMonitorDataService / IWarnLogService + gateway MonitorController/AlertController

## 接入 flows
client → Gateway `MonitorController` (:8080, `/api/monitor/*`) / `AlertController` (`/api/v1/alerts/*`) → runtime-monitor service (对象/策略/采集/告警) → 回 `ApiResponse`。
各引擎系统性监测统一走本模块 (铁律 2.5 #4), 不自建 Caretaker/Heartbeat 轮询; 策略超时/偏离触发 IWarnLogService 落告警日志。

## 主 API (curl)
```bash
curl -s "http://localhost:8080/api/monitor" -H "Authorization: Bearer $TOKEN"            # 仪表板: 系统资源+活跃告警+趋势
curl -s "http://localhost:8080/api/monitor/alerts" -H "Authorization: Bearer $TOKEN"      # 告警统计
curl -s "http://localhost:8080/api/v1/alerts" -H "Authorization: Bearer $TOKEN"           # 告警列表; ACK: POST /api/v1/alerts/{id}/ack
```

## 接 DB 表
`ecos_alert_history` (MonitorService 计数/趋势查询主表); 策略/插件对象等经 Dao (IMonitorObjectDao/IStrategyDao/IPluginDao) 持久化。

**`ecos_warn_log`（告警日志落库表 — PMO-59 P4a T1/T2，DDL V131）**: `IWarnLogService.warn(...)` 由纯内存态改为
「内存 `logStore`（查询语义零变化）+ 持久化分支」双写；`WarnLogServiceImpl` 经构造器注入 `JdbcTemplate`
（未注入即退化为纯内存态，向后兼容），落库异常一律 try/catch WARN 不抛出（不阻塞主流程）。
列: `id/log_id(uniq)/warn_type/warn_level/warn_objid/warn_objname/warn_message/fault_context(JSONB)/review_tag/warn_hand/warn_result/ishanded/warn_time` + 审计六列 + `is_deleted`。
接口只增不改: 新增七参 `warn(type,objid,objname,err,typeHander,faultContext,reviewTag)` default 方法（默认委托五参）。
**告警查询口径（T3 勘察裁定，本期不新增端点）**: 既有只读端点 2 个 —— `GET /api/monitor/alerts`（统计，源 `ecos_alert_history`）
与 `GET /api/v1/alerts`（列表，`:8080` 由 workspace 降级路由承接，见 `AlertController` 类注）；
`ecos_warn_log` 本期以 psql 直查 + 复盘聚合 `GET /api/v1/cognitive/mental-reviews`（`warnAlerts` 段，按 `review_tag` 只读 join）为查询通道，
新增 warn 专用 REST 端点属扩面且存在路由遮蔽风险，留待后续按需评审。

## 别接 (调谁, 已核)
- 各引擎禁止自建监控端点 (现在 HealthCheck 链走 IEngine, 不需要自己 polling)
- 告警通知走 runtime-core `INotificationService`/`IAlertService`, 不直连 IM/短信 provider
- 采集数据经 runtime-access (PG 查库同), 不自建 JDBC 连接
- runtime-monitor 禁止反向依赖业务模块 (`engine/*` / `services/*` / `workspace/*` 不得出现在 POM 或 import 中);
  数据源经由宿主装配注入 (`JdbcTemplate`), 本模块不 new 连接/不自建 DataSource

## 验收 flows
`GET /api/monitor` 返回 `system` + `active_alerts` 字段数值型, `GET /api/monitor/health` 200;
`POST /api/v1/alerts/{id}/ack` 后 `GET /api/v1/alerts/{id}` 的 `acknowledged` 翻 true。发布事件: 策略命中 → `PipelineEvent.of(DATASOURCE_STATUS_CHANGED, ...).fromModule("runtime-monitor")`。
