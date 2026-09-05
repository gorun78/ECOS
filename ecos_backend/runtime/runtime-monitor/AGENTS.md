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

## 别接 (调谁, 已核)
- 各引擎禁止自建监控端点 (现在 HealthCheck 链走 IEngine, 不需要自己 polling)
- 告警通知走 runtime-core `INotificationService`/`IAlertService`, 不直连 IM/短信 provider
- 采集数据经 runtime-access (PG 查库同), 不自建 JDBC 连接

## 验收 flows
`GET /api/monitor` 返回 `system` + `active_alerts` 字段数值型, `GET /api/monitor/health` 200;
`POST /api/v1/alerts/{id}/ack` 后 `GET /api/v1/alerts/{id}` 的 `acknowledged` 翻 true。发布事件: 策略命中 → `PipelineEvent.of(DATASOURCE_STATUS_CHANGED, ...).fromModule("runtime-monitor")`。
