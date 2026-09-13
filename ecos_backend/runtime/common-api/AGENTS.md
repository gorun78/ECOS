# common-api (共享契约包) 接口与验收 flows

> common-api | 全仓引用点 0 业务 import (maven-enforcer 守门) | 顶层: 见 common/pom.xml
> 契约内容: `PipelineEvent` / `DomainEvent` / `EventTypes` / `KafkaTopics` / `IEngine` / `EngineStatus` / `HealthCheck` / `ITaskAwareEngine` / `StateMachineEngine` / `ApiResponse` / 异常层级 (DataBridgeException → Business/Forbidden/Unauthorized/Validation/NotFound/DataAccess) / `ICopilotService` / `IGraphService` / `IAnalyticsService` / `ObjectRuntimeService` / `TenantContextHolder` / `BaseEntity`

## 接入 flows
任模块 (gateway / 各 engine-impl / system / workspace / services) → 仅 import `common-api` 契约 → 注入 Bean / 发布事件 → 回契约 DTO。
enforcer 规则: `common-api` **只指 pom.xml 与 Spring 基础设施** (Spring Core/Validation 等), 禁止依赖业务模块 — 反向构建 Cycle 防范。

## "主 API" (本模块不发 HTTP; 契约 = bean 接口)
当前唯一进程内 plumbing = `PipelineEvent` 发事件, 全仓 production ref 仅 1 处 (`SecuritySandboxService`), 主路尚待:
```
# 程序内发布 (示例 POST /api/... 消费):
curl -s "http://localhost:8080/api/v1/engine/data/status" -H "Authorization: Bearer $TOKEN"   # 触发引擎状态变更 → 发 PIPELINE_STATUS
curl -s -X POST "http://localhost:8080/api/v1/engine/data/transform/execute" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -d '{"input":{...},"chain":[{"type":"cleansing"}]}'    # ge 完成 → 发 TRANSFORM_COMPLETED
curl -s "http://localhost:8080/api/v1/engine/security/health"   # security 引擎 CB 产 回事件
```

## 接 DB 表
0 — 契约层无 DAO, 不落表 (违反则属不当入库)。

## 别接 (调谁, 已核)
- common-api 不依赖任何业务模块 (enforcer `bannedDependencies`), 不引用 impl 类
- 事件发布 (PipelineEvent) 订阅者自行 impl (目前 listener 缺口 = P2-4 事件总线, Wave-6 排)
- IEngine / ITaskAwareEngine 实现归属引擎, common 不内置 impl (避免 com.chinacreator.gzcm.common.impl 包混入)

## 验收 flows
ArchUnit 测试 (common-api/src/test) 执行 `ArchitectureTest` 5 条全 PASS + `ArchitectureGuardTest` 反证命题当前 NO_HIT 即可;
enforcer `mvn install -pl common/common-api` 不报 forbidden-dep; 异常补证: `GlobalExceptionHandler` 捕获 `DataBridgeException` 族, ApiResponse 返回体含 `errorCode` 字段。
