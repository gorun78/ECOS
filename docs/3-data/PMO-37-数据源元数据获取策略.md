# PMO-37 数据源元数据获取策略

## 问题

1. **数据表目录分页异常**：数据工作台「选择数据源」→ 右侧目录，数据源 deleted 后变成 `暂无数据`，没有分页控件
2. **行数不显示**：`CatalogTree` 有 `formatRowCount` 渲染函数但 `recordCount` 字段始终为 0/empty
3. **采集不自动**：元数据采集靠手动 OR `POST /collect`，新建数据源后需要用户手动点一下才能看到表

## 现状分析

### 数据库

| 表 | 关键字段 | 状态 |
|----|---------|------|
| `td_data_resource` | `record_count BIGINT DEFAULT 0` | ✅ 字段已存在，`0` = 未采集 |
| `td_catalog_item` | `record_count BIGINT DEFAULT 0` | ✅ 字段已存在 |
| `td_datasource` | `metadata_config JSONB` | ❌ 缺失，需新增 |
| `td_datasource` | `last_collect_time TIMESTAMP` | ❌ 缺失，需新增 |

### 后端

- `MetadataCollectionService.getResources(datasourceId)` 返回 `List<DataResource>` — 含 `fieldCount`、`recordCount`，**但映射行：`recordCount` 默认为 0**
- `MetadataServiceImpl.collectAll(datasourceId)` — 遍历 Connector 发现的表，`fieldCount` 已统计，**但没有 `SELECT COUNT(*)` 执行行计数**
- `ITaskManagementService` — 已有 `submitTask(TaskDescription)`, `TaskDescription.async=true` 支持异步，**可作为元数据采集任务的载体**

### 前端

- `CatalogTree` — 数据源选中后调 `fetchResources(datasourceId)` → `GET /datanet/metadata/resources/{id}` → `DataResource[]`
- 数据源删除后 `selectedResource?.datasource?.name` 为 undefined → 渲染变成 `暂无数据`
- `DataResourcesPanel` — 独立面板分页是正常的（`useState page/setPage`），但数据源目录树 `dataResources` 数组本身受 `mapResource` 影响，`metadata.resource_count` 未正确解析

### 任务引擎

- `ITaskManagementService.submitTask(TaskDescription)` — 已支持 `async=true`，`taskType` 如 `METADATA_COLLECT`
- `ITaskExecutor` / `ITaskStatusCallback` — 可回调状态
- `TaskDescription.parameters` — 可存 `datasourceId`、`includeCount: boolean`

## 解决方案

### 1. 数据源增加「元数据获取策略」字段

**Schema 变更（Flyway V??）`td_datasource`**

```sql
ALTER TABLE td_datasource ADD COLUMN IF NOT EXISTS metadata_config JSONB DEFAULT '{}';
ALTER TABLE td_datasource ADD COLUMN IF NOT EXISTS last_collect_time TIMESTAMP;
COMMENT ON COLUMN td_datasource.metadata_config IS '元数据获取策略配置';
```

`metadata_config` JSON 结构：

```json
{
  "strategy": "ON_SAVE | ON_SCHEDULE | MANUAL | ON_DEMAND",
  "includeRowCount": true,
  "countMethod": "EXACT | ESTIMATE | OFF",    // EXACT=SELECT COUNT(*), ESTIMATE=估计, OFF=不采集行数
  "scheduleCron": "*",                          // ON_SCHEDULE 时使用
  "cacheTtlMinutes": 5,
  "onSourceEdit": true                          // 编辑数据源连接配置后自动触发采集
}
```

**DTO 扩展 `DataSourceDTO`**

```java
private String metadataStrategy;      // ON_SAVE / ON_SCHEDULE / MANUAL / ON_DEMAND
private Boolean includeRowCount;      // 是否采集行数
private String countMethod;           // EXACT / ESTIMATE / OFF
private String scheduleCron;          // ON_SCHEDULE 时的 cron 表达式
private Integer cacheTtlMinutes;      // 缓存 TTL（分钟）
private Boolean onSourceEdit;         // 编辑连接配置后自动触发采集
```

### 2. 元数据策略枚举与校验

```java
public enum MetadataStrategy {
    ON_SAVE,      // 创建/编辑数据源后立即触发采集
    ON_SCHEDULE,  // 按 cron 定时采集
    MANUAL,       // 手动触发（当前默认行为）
    ON_DEMAND     // 前端每次请求目录时按需采集（性能差，不推荐）
}
```

### 3. 行计数真实化

**`MetadataServiceImpl.collectAll(datasourceId, Map<String, Object> config)`**

当前只统计 `fieldCount`，新增可选 `recordCount`：

```java
// 现有: tables = connector.listTables(config);
// 新增: 如果 config.get("includeRowCount") == true 且 countMethod != OFF
for (Table t : tables) {
    long count = -1;
    String method = config.getOrDefault("countMethod", "EXACT");
    switch (method) {
        case "EXACT" -> count = executeSelectCount(connector, schema, t.name);
        case "ESTIMATE" -> count = executeEstimate(connector, schema, t.name);
        case "OFF" -> count = -1;
    }
    updateRecordCount(resourceId, count);
}
```

`executeSelectCount` 和 `executeEstimate` 按 Connector 类型分派：
- **PG**：`SELECT COUNT(*) FROM schema.table` / `SELECT n_live_tup FROM pg_stat_user_tables WHERE relname = ?`
- **MySQL**：`SELECT COUNT(*) FROM schema.table` / `SELECT table_rows FROM information_schema.tables WHERE table_name = ?`

结果写入 `td_data_resource.record_count`（`-1` = 未采集，`0` = 空表，`>=0` = 真实值）。

### 4. 数据源创建/编辑后触发异步采集

**DataSourceServiceImpl.register() 末尾**

```java
// 解析 metadataConfig
Map<String, Object> metaConfig = parseMetadataConfig(dto.getMetadataConfig());
if (shouldCollectImmediately(metaConfig)) {
    submitAsyncCollectTask(entity.getDatasourceId(), metaConfig);
}

private void submitAsyncCollectTask(String datasourceId, Map<String, Object> config) {
    TaskDescription task = new TaskDescription();
    task.setTaskType("METADATA_COLLECT");
    task.setTaskName("元数据采集: " + entity.getDatasourceName());
    task.setDescription("数据源 " + datasourceId + " 的元数据采集任务");
    task.setParameters(Map.of("datasourceId", datasourceId, "metadataConfig", config));
    task.setAsync(true);
    task.setCreatedBy(dto.getCreateBy() != null ? dto.getCreateBy() : "system");
    taskManagementService.submitTask(task);
}
```

`TaskDescription.parameters` 传递 `datasourceId` + `metadataConfig`，异步由 AutoCollectTask 执行。

### 5. 定时采集调度器（如 ON_SCHEDULE）

**新增 `AutoCollectScheduler`**（data-engine 内）

```java
@Service
public class AutoCollectScheduler {

    @Scheduled(cron = "0 */5 * * * ?")
    public void checkScheduledCollections() {
        // 查询 metadata_config -> strategy == "ON_SCHEDULE" 的数据源
        // 按各自的 scheduleCron 判断是否到期
        // 到期 → ITaskManagementService.submitTask(METADATA_COLLECT)
    }
}
```

### 6. 任务执行器：`AutoCollectTaskExecutor`

**新增 `AutoCollectTaskExecutor implements ITaskExecutor`**（在 data-engine 里）

```java
@Component
public class AutoCollectTaskExecutor implements ITaskExecutor {

    final ITaskStatusListener listener;
    final MetadataService metadataService;
    final MetadataCollectionService collectionService;

    @Override
    public TaskResult execute(TaskContext ctx) {
        String datasourceId = ctx.getParam("datasourceId");
        Map<String, Object> config = ctx.getJson("metadataConfig");

        int tables = metadataService.collectAll(datasourceId, config);
        collectionService.invalidateCache(datasourceId);
        return TaskResult.ok(Map.of(
            "datasourceId", datasourceId,
            "resourcesCollected", tables,
            "elapsedMs", System.currentTimeMillis() - start
        ));
    }
}
```

AutoCollectTask（引擎 plugin 形式）注册 taskType `METADATA_COLLECT` 到 sprider，任务调度时直接转 `ITaskExecutor.execute`。

### 6.1 失败重试机制

**重试策略**：

| 层级 | 触发条件 | 行为 |
|------|---------|------|
| Connector 单层 | 连接超时 / 语句失败 | 重试 1 次（timeout 默认 10s） |
| 单表计数 | SELECT COUNT 失败 | 该表 `record_count` 写 `-1`，其他表继续（不阻塞增量） |
| 整个任务 | 全部表采集失败 | `TaskStatus=FAILED`，`ITaskStatusCallback.onStatusUpdate` 回写 `last_collect_time` 不变更 |
| AutoCollectScheduler | 上次 FAILED | 下次 cron 触发时若 `last_collect_time` 仍 <TTL| 自动重提交 |

**失败不阻塞**：单表计数失败不影响整体任务状态（`status=PARTIAL`），任务允许部分成功；过期 > 3 次才标记 `FAILED_FINAL` 且告警。

**可观测**：`TaskStatus.statusMessage` 写入失败原因，前端「任务中心」`/api/v1/task/list?taskType=METADATA_COLLECT` 可查状态流。

### 7. 前端：数据源创建/编辑表单增加策略区块

**`ConnectionsTab.tsx`** — 在「提交连接」按钮前加 **元数据获取策略** 区块：

```
┌─────────────────────────────────────┐
│ 元数据获取策略（可选）                 │
│                                     │
│ 获取时机:
│  ○ 保存后立即获取（ON_SAVE） — 默认  │
│  ○ 定时获取（ON_SCHEDULE）          │
│  ○ 手动获取（MANUAL）— 当前行为      │
│                                     │
│ 行数统计:
│  ■ 包含行数（EXACT — SELECT COUNT） │
│  □ 包含行数（ESTIMATE — 估计值）    │
│  □ 不统计行数（OFF）               │
│                                     │
│ 缓存 TTL（分钟）: [5]               │
└─────────────────────────────────────┘
```

提交时把策略字段序列化为 JSON → `metadata_config` 字段传给后端。

### 8. 前端：行数显示

**`CatalogTree`** — 修复 `formatRowCount` 渲染（已有函数，只需确保 `recordCount` 有值时显示）：

```tsx
{r.recordCount !== 0 && r.recordCount !== -1 && (
  <span className="text-stone-500 mL-2 text-xs">
    {formatRowCount(r.recordCount)} 行
  </span>
)}
```

`recordCount === -1` 表示「未采集」，`0` 表示空表，`>0` 是真实值。前端对 `-1` 显示「行数未统计」灰色文字。

### 9. 前端：分页策略定调（DB 分页 vs 元数据缓存分页）

**选型：元数据缓存分页（DB 不做分页）**

理由：
- `td_data_resource` 单数据源下属表数量通常 < 100（PG 单 schema 默认上限 ~500）
- `MetadataCollectionService.getResources(datasourceId)` 已有 Caffeine 缓存（5min TTL），整体加载成本可控
- DB 分页会对已落库的元数据做流式 LIMIT/OFFSET，但表清单本身是「先采集后消耗」的语义，不需要真分页
- 前端 `CatalogTree` 当前是「全量加载 + 前端本地搜索/筛选」模式，与缓存策略一致

**实现**：
1. 后端 `GET /datanet/metadata/resources/{id}` 返回完整列表（不分页），由 `MetadataCollectionService.getResources` 缓存
2. 若列表行数 > `pageSize`（默认 50），返回 `X-Has-More: true`，前端按「加载下一页」追加
3. 前端 `CatalogTree` 分页控件：「上一页 / 第 X / Y 页 / 下一页」，翻页触发前端本地切片（不重发请求）
4. 数据源 deleted 后空态处理（`dataResources.length === 0`）→ 显示「暂无数据」+「重新采集」按钮，不再白屏

**缓存失效**：采集任务完成时 `collectionService.invalidateCache(datasourceId)`，前端轮询到 `last_collect_time` 变化后重新 fetch

### 10. 策略生效的完整流程

```
创建数据源（ON_SAVE，includeRowCount=true, countMethod=EXACT，ON_SCHEDULE=cron=Math）
  │
  ├─ DataSourceController.register() → DataSourceServiceImpl.register()
  │   └─ 解析 metadata_config → 写入 td_datasource 表
  │   └─ 策略是 ON_SAVE？ → 提交 TaskDescription(taskType=METADATA_COLLECT, async=true)
  │                          (taskManagementService.submitTask)
  │
  └─ 同时 → AutoCollectScheduler（如果 ON_SCHEDULE）加入 cron 队列

AutoCollectTaskExecutor.execute()
  └─ metadataService.collectAll(datasourceId, metadataConfig)
      └─ ConnectorFactory.getConnector(type).listTables / listColumns
      └─ 如果 includeRowCount:
          └─ executeSelectCount / executeEstimate per table
      └─ INSERT INTO td_data_resource (..., record_count)
      └─ collectionService.invalidateCache(datasourceId)

CatalogTree fetchResources(datasourceId)
  └─ GET /datanet/metadata/resources/{id}
  └─ 读到 td_data_resource 数据（含 record_count）→ 渲染目录 + 行数
```

## 任务分解

| ID | 内容 | 模块 | 估计 |
|----|------|------|------|
| T0 | DDL 迁移：`td_datasource.metadata_config`, `last_collect_time` | data-engine | 15min |
| T1 | `DataSourceDTO` 增加 `metadataStrategy` 等字段（6 个字段） | data-engine-api | 15min |
| T2 | `DataSourceEntity` + `mapRow` 增加 2 字段 | data-engine-impl | 15min |
| T3 | `MetadataCollectionService.getResources` → 增加 `recordCount` 映射 + 404 时返回 202 提示 | data-engine-impl | 30min |
| T4 | `MetadataServiceImpl.collectAll` 增加可选 `record_count` 计数 | data-engine-impl | 30min |
| T5 | `AutoCollectTaskExecutor` 注册 `METADATA_COLLECT` 任务类型 | data-engine-impl | 45min |
| T6 | `AutoCollectScheduler` + `@EnableScheduling`（ON_SCHEDULE 模式） | data-engine-impl | 30min |
| T7 | `DataSourceServiceImpl` register/update 后根据策略 submitTask | data-engine-impl | 30min |
| T8 | 单元测试：计数逻辑（EXACT/ESTIMATE/OFF 三种模式） | data-engine-impl | 30min |
| T9 | 前端 axios 封装 + 表单 UI：策略区块 | ecos_frontend | 45min |
| T10 | CatalogTree 行数渲染修复 + 分页空态处理 | ecos_frontend | 30min |
| T11 | 集测：创建数据源（ON_SAVE）→ 等任务完成 → 目录显示行数 | data-engine | 30min |

**总估计 ~7 小时**

## 验收标准

1. ✅ 新建数据源时可选「元数据获取策略」（策略、计数方式、cron、缓存TTL）
2. ✅ 保存时即时策略：创建后自动触发元数据采集，无需手动操作
3. ✅ 目录中数据表显示行数（EXACT = 真实值，ESTIMATE = 估计值，OFF = 不显示）
4. ✅ 定时策略：按 cron 到时间自动采集
5. ✅ 分页数据源目录在无数据时显示提示，不是白屏
6. ✅ 任务可查：`GET /api/v1/task/list?taskType=METADATA_COLLECT` 可看到采集任务状态
7. ✅ 任务引擎状态流转：SUBMITTED → RUNNING → SUCCEEDED/FAILED，失败带 errorMessage

## 不改动（避免误伤）

- 不删除 `/datanet/metadata/resources/{id}` 端点
- 不改 `DataResource` 模型已有字段
- 不删除现有 Caffeine 缓存
- 不引入新的依赖

## 分支

`feature/pmo-37-metadata-strategy`

## 功能清单

总状态：8 项，P0 已完成，P1/P2 待开发。

### P0 — 三路径修复（✅ 已完成，commit 1c4695b）

| # | 功能 | 优先级 | 状态 | 验收方式 |
|---|------|--------|------|----------|
| 1 | 手动刷新采集 — 数据源详情卡 → 刷新按钮 | P0 | ✅ 已修 | 点击触发异步采集 → 轮询 collect-status → 完成后自动回显目录；超时显示 loading |
| 2 | 立即采集 — 数据源详情卡 → 收集按钮 | P0 | ✅ 已修 | 提交任务引擎返回 taskId（在前端 toast 中展示），任务引擎侧 RUNNING→SUCCEEDED |
| 3 | 目录回显 — `GET /api/v1/datanet/metadata/resources/{id}` | P0 | ✅ 已修 | 修前裸路径 404；修后返回 200 + 数据（curl 已验证） |
| 4 | 策略保存 — `PUT /strategy/{datasourceId}` | P0 | ✅ 已修 | 策略下拉框 onChange 调后端，metadata_config JSON 持久化到 td_datasource |
| 5 | 任务状态查询 — `GET /collect-status/{taskId}` | P0 | ✅ 已修 | 返回 status/progress/errorMessage/result，轮询用 |

### P1 — 定时配置闭环（⏳ 未做）

| # | 功能 | 状态 | 预估 |
|---|------|------|------|
| 6.1 | 后端：`td_datasource.metadata_config` 新增字段 `intervalMinutes` / `intervalUnit` / `scheduleEnabled` / `nextFireTime` / `lastFireTime` | ⏳ | 30min |
| 6.2 | 后端：`AutoCollectScheduler` 从一次性 cron 改为重复 interval 调度（每 5min 扫描一次） | ⏳ | 40min |
| 6.3 | 前端：数据源编辑界面新增「定时采集」配置区块（启用开关 + 间隔数值 + 单位下拉 + 下次执行预估） | ⏳ | 60min |
| 6.4 | i18n：`dw.scheduled.*` 翻译键 | ⏳ | 15min |

### P2 — 回显一致性治理（⏳ 未做）

| # | 功能 | 状态 | 预估 |
|---|------|------|------|
| 7.1 | `MetadataCollectTaskExecutor` 采集前按 datasourceId 清空 `td_data_resource`，再全量写入（根治行膨胀 531→180） | ⏳ 需确认 | 30min |
| 7.2 | 前端 fetchDataSourceResources 去重 + 行数与 DB 一致验证 | ⏳ | 20min |
| 7.3 | 失败态：断开数据源后触发采集，前端显示明确错误（不静默假成功） | ⏳ | 15min |

### 铁律约束

- 不新增 Maven 模块、不新增 Docker 容器
- 不改动已有 API 路径签名（新增 `/strategy/{id}` 除外）
- 双白名单同步修改
- 编译验证：`mvn clean compile -Dmaven.test.skip=true` 0 ERROR 进 QA
- 不动数据库已有表结构（P1 新增字段需先出方案）

