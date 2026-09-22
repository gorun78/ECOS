# PMO指令: E3 Gateway 启动阻塞排查（Bean 装配系统性修复）

> **来源**: 肖国荣 | **日期**: 2026-08-24
> **协同**: ECOS-BE + ECOS-ARCH
> **架构铁律**: 必须遵循 [ECOS架构铁律](../../ARCHITECTURE-RULES.md)
> **铁律**: ①迭代启动——用"启动→报错→修→再启动"循环，直到 standard 和 enterprise 都 `Started GatewayApplication`，不得只修已列问题就停 ②删副本前必须 grep 确认无消费者 + diff 判断权威 ③禁止改端点路径/响应 ④禁止动 E1/E2 已下沉的 Controller（JdbcTemplate 下沉成果不能破坏）⑤禁止引入新依赖

## 零、现状摸底（已核实）

E1/E2 下沉完成后，联调启动 Gateway 失败，暴露一批 **A 阶段基础设施收敛 + 三版本架构遗留** 的 Bean 装配问题。Gateway 很可能从 A 阶段起就没真正全量启动过。

### 已确认启动阻塞（3 个）

| # | 问题 | 根因 | 状态 |
|---|------|------|------|
| 1 | MinioStorageService 找不到（DataLakeExportService 构造参数） | A+5 加的排除规则 `runtime.access.storage.**` 在 R3 删 gateway 实现后没同步删 | 肖总已删 2 行排除规则（未 commit），PMO 验证 + commit |
| 2 | pgAnalyticsService 找不到（TaskController 字段） | StubAnalyticsService `@Profile("!enterprise & !flagship")` 只在 standard 生效；TaskController `@Qualifier("pgAnalyticsService")` 指向从未实现的 PG 分析服务（init 就缺失） | 待修 |
| 3 | pgObjectStorageService 冲突（ConflictingBeanDefinition） | gateway 和 workspace 各有 `PgObjectStorageService`，Bean 名相同 | 待修 |

### 同名 Service 隐患（9 个，逐个判断真冲突）

| 同名 Service | 位置 A | 位置 B | 架构归属判断 |
|-------------|--------|--------|-------------|
| ColumnLevelSecurityService | sysman-api/datapermission | security-engine-api | 安全→security-engine 权威，sysman 副本 |
| DataMaskingService | sysman-api/datapermission | security-engine-impl/service | 同上 |
| RowLevelSecurityService | sysman-api/datapermission | security-engine-api | 同上 |
| IDataSecurityPolicyService | sysman-api/security/policy | security-engine-api | 同上 |
| GitRepositoryService | sysman-api/config/git | runtime-access/git | Git 访问→runtime-access 权威，sysman 副本 |
| GitService | sysman-api/config/git | runtime-access/git | 同上 |
| KnowledgeGraphService | ai-engine-impl/agent/mesh | kb-engine-api | 知识图谱→kb-engine 权威，ai-engine 副本 |
| TelemetryService | gateway/service | agent-service/runtime/telemetry | 需 diff 判断 |
| PipelineService | data-engine-api | data-engine-impl/pipeline | 疑似接口+实现，非冲突，先 diff |

## 一、目标状态

Gateway 在 **standard 和 enterprise 两个 profile** 下都能 `Started GatewayApplication`，`curl /api/v1/auth/login` 返回 200。

## 二、分阶段执行计划

### T0: MinioStorageService 修复验证 + commit（0.5h）

肖总已删 GatewayApplication 两处过时排除规则（`runtime.access.storage.**` 整包排除 + `MinioObjectStorageService.class` 单类排除）。PMO：①确认改动正确 ②连同后续修复一起 commit。**注意**：删整包排除后 `MinioObjectStorageService`（runtime-access）也被扫描，它与 `PgObjectStorageService` 同为 `IObjectStorageService` 实现，需配合 T2 一起处理注入多候选问题。

### T1: pgAnalyticsService 缺失修复（1h，最小兜底）

`gateway/service/StubAnalyticsService.java`：
1. 加 `@Service("pgAnalyticsService")` 显式 Bean 名（匹配 TaskController 的 `@Qualifier("pgAnalyticsService")`）
2. 去掉 `@Profile("!enterprise & !flagship")`（改为全 profile 生效，stub 兜底所有版本）

**权衡说明**：这是"启动兜底"方案，enterprise/flagship 的分析查询（executeQuery）仍是 stub 返回空。真正的 PG/Doris 分析引擎是**功能缺口**，本次不实现，交付报告里单独记录。

### T2: pgObjectStorageService 冲突去重（1h）

`diff` gateway/service/PgObjectStorageService.java 和 workspace/service/PgObjectStorageService.java，判断权威（倾向 workspace——对象管理是 workspace 职责），删除非权威副本前 `grep -rn "PgObjectStorageService"` 确认消费者改指向权威版。**同时**确认 `IObjectStorageService` 的三个实现（MinioObjectStorageService + PgObjectStorageService×2）在去重后不产生多候选注入问题——`DataLakeController` 若 `@Autowired IObjectStorageService` 无 qualifier，需加 `@Qualifier` 或 `@Primary`。

### T3: 9 个同名 Service 逐个排查（1-1.5 天）

每个同名 Service 对，按三步走：

1. **先查是否已在 excludeFilters 排除**——`grep` GatewayApplication 的 excludeFilters，已排除的跳过（确认排除规则仍有效）
2. **未排除的 diff 判断**——真副本（>80% 相同）→ 按上表架构归属删非权威副本；功能分化 → 保留但加 `@Profile` 区分或 `@Service("name")` 改名
3. **PipelineService 特例**——data-engine-api vs data-engine-impl，先 diff 确认是接口+实现（不冲突）还是真冲突

**删副本铁律**：删前 `grep -rn` 确认无消费者，有消费者先改 import 指向权威版。删副本优先用 GatewayApplication excludeFilters 排除（软删除），而非直接 `git rm`（保留 git 历史可追溯）。

### T4: standard 启动验证（0.5h）

```bash
# standard profile 启动，观察 Started GatewayApplication
```

### T5: enterprise 启动验证（0.5h）

```bash
# enterprise profile 启动，观察 Started GatewayApplication
```

### T6: 登录 + 关键端点 curl（1h）

登录拿 token，抽查 5 个下沉过的关键端点（TenantController /api/v1/system/tenants、ObjectQL、Causal、EntityTableMapping、DataLake），确认 200。

## 三、排查方法论（核心铁律）

**无法预知全部问题**。已列出的 3+9 是第一批，启动过程中会层层递进暴露更多（当前已暴露到第 3 个）。PMO 必须坚持：

```
启动 → 报错 → 定位 Bean 问题 → 修（去重/补实现/加排除）→ 再启动 → 直到 Started
```

**每修一个 commit 一个**，commit message 写清楚"E3-Tn: 修 XXX Bean 问题"。standard 和 enterprise 两个 profile 都要跑通。

## 四、禁止清单

- ❌ 只修已列问题就收工（必须迭代启动到两个 profile 都 Started）
- ❌ 删副本前不 grep 消费者、不 diff 权威（删错 = 功能缺失）
- ❌ 改端点路径 / 改响应结构 / 改 SQL 语义
- ❌ 动 E1/E2 下沉成果（Controller 已无 JdbcTemplate 的状态不能破坏）
- ❌ 引入新依赖 / 新 ORM / 新模块
- ❌ 把"真 PG/Doris 分析引擎""对象存储三版本完整实现"等**功能缺口**当本次任务做——只记录，不实现

## 五、风险与回滚

- **删副本是最高风险**：sysman 的 ColumnLevelSecurity/DataMasking/RowLevelSecurity 等若被 sysman Controller 仍在使用，删了会功能崩溃。删前必须逐个 grep 消费者。
- **excludeFilters 软删除优先于 git rm**：软删除可回滚，且保留历史。
- **层层递进暴露**：可能修完 12 个还有第 13 个。这是正常的，按迭代循环继续，不要慌。
- **回滚**：每修一个 commit，`git revert` 精确回退。

## 六、验证门禁

```bash
# V1: standard 启动
# 观察日志出现 "Started GatewayApplication"，无 APPLIICATION FAILED TO START

# V2: enterprise 启动
# 同上

# V3: 登录 200
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
# 期望: 返回 token，非 401/404

# V4: 关键端点抽查 200（带 token）

# V5: 全量编译仍 BUILD SUCCESS
# V6: 铁律5 仍全绿（E2 成果不回退）
env -i HOME=/home/guorongxiao PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 \
  bash -c 'cd /home/guorongxiao/ECOS/ecos_backend && mvn test -pl common/common-api 2>&1 | grep -E "Tests run|BUILD"'
```

## 七、工时估算

T0(0.5) + T1(1) + T2(1) + T3(1-1.5天) + T4(0.5) + T5(0.5) + T6(1) ≈ **2.5-3 天**（含迭代启动暴露的未知问题）

## 八、一句话给 PMO

Gateway 从 A 阶段起就攒了一堆 Bean 装配烂账（同名 Service 冲突 + 缺失实现），E1/E2 之后该还了——用迭代启动逐个修，删副本先 grep 消费者，两个 profile 都 Started 才算完。
