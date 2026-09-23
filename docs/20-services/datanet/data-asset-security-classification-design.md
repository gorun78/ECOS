# 数据资产管理与分级分类（Security Classification）设计

> 来源: PMO-data10 | 日期: 2026-09-23 | 责任人: PMO
> 版本: v1.0（批准）
> 追溯: AGENTS.md（数据工作台 §数据资产）、AGENTS.md（security-engine §脱敏）

## 一、目标与范围

| 范围 | 端点/模块 |
|:---|:---|
| **范围 A** | 前端数据工作台新增「数据资产」Tab；data-engine 资产 CRUD REST；`td_data_resource` 升级为可维护资产（category_id 外键） |
| **范围 B** | DDL V154（4 新表 + 2 ALTER）；data-engine 导出分级分类 REST（`GET /security-level` / `POST /security-tag`）；LLM 推荐→人工确认；`ecos_data.ecos_data_asset_field` 字段级敏感度 |
| **范围 C** | security-engine `DataSecurityLevelEventListener` 消费 `ecos.data.security-tagged` → 自动生成/更新 `ecos_cls_policy`（L3+ 字段 block）+ `ecos_rls_policy`（L4 准入 `clearance_level >= 4`）；`DataMaskingService` 补 5 类（身份证/手机/邮箱/银行卡/金额/地址）；`DataClassificationServiceImpl` 标 deprecated |

## 二、数据流图（分层 + 事件契约）

```
【资产维护面】 前端 DataAssetsDashboard.tsx (数据工作台 Tab)
                      │   GET /api/v1/datanet/assets
                      │   POST /api/v1/datanet/assets
                      │   POST /api/v1/datanet/assets/{id}/security-tag   ← 人工 confirmed=true
                      ▼
【data-engine】 AssetService (Tag + ABAC 前置)
   ├─ INSERT/UPDATE ecos_data.ecos_data_asset          (V154 资产业务视图)
   ├─ INSERT/UPDATE ecos_data.ecos_data_asset_field    (V154 字段级敏感度)
   ├─ LLM 推荐：写 recommend_level / recommend_source，confirmed=FALSE
   └─ confirmed=TRUE 且 level≥L3：
          ├─ 审计 Kafka ecos.audit(asset.create/tag)                   (ST06)
          └─ 发 Kafka 事件 DATA_SECURITY_TAGGED (topic=ecos.data.security-tagged)
                          │  payload: DataAssetSecurityTaggedEvent
                          │     { assetId, resourceId, tableName,
                          │       resourceType, domain, sensitivityLevel,
                          │       confirmedFieldCount, fields[{fieldId,fieldName,dataType,
                          │       fieldSensitivity, maskStrategy}], operator, ts }
                          ▼
【security-engine】 DataSecurityLevelEventListener (编程式 subscribe)
   ① L3+ 命中字段 → upsert ecos_cls_policy (resource_id 优先, table_name 兜底)
   ② L4 资产     → upsert ecos_rls_policy  (filter_expr="clearance_level >= 4")
   ③ 审计 Kafka  ecos.audit(data.security.policy-generated)                    (ST06)

【消费面】  RLS/CLS/脱敏 (运行时)
   RowLevelSecurityServiceImpl.apply()      → WHERE (table_name = ? OR resource_id = ?)
   ColumnLevelSecurityServiceImpl.getColumns→ WHERE (table_name = ? OR resource_id = ?)
   DataMaskingService.ruleForDataType(data_type)  → idCard/phone/email/bankCard/amount/address/none
```

**事件契约**（跨引擎）：

- 定义：[DataAssetSecurityTaggedEvent](file:///d:/workspace/javaprojects/ECOS/ecos_backend/runtime/common-api/src/main/java/com/chinacreator/gzcm/common/event/DataAssetSecurityTaggedEvent.java)（全 final + `@JsonCreator` 与 `OntologyPublishedEvent` 同款）
- Topic 常量：`KafkaTopics.DATA_SECURITY_TAGGED = "ecos.data.security-tagged"`
- 消费：`DataSecurityLevelEventListener#init()` `EventBusService.subscribe(topic, Class, handler)`（覆盖 Kafka 与内存 fallback 双路径）

## 三、DDL（V154）+ seed

`ecos_backend\gateway\src\main\resources\db\migration\V154__data_asset_security_classification.sql`

| # | 对象 | 作用 | 合规点 |
|:--:|:---|:---|:---|
| 1 | `ecos_data.ecos_data_level_def` | L1..L4 字典，`data-engine` 唯一权威 | DR04 (`_json` 后缀) + DR06/07/08（5 审计 + version_no + domain） |
| 2 | `ecos_data.ecos_data_category_tree` | 业务分类树 ≤3 级，继承 V58 的 5 seed 双轨 | DR02 (`ecos_`) + DR01/03 |
| 3 | `ecos_data.ecos_data_asset` | 资产业务视图（`UNIQUE(resource_id, domain)`） | R9（物理层 `td_data_resource` 不动） |
| 4 | `ecos_data.ecos_data_asset_field` | 字段级敏感度（`confirmed` 门控） | `mask_strategy_json` 不存敏感明文（ST03） |
| 5 | `ALTER ecos_cls_policy ADD COLUMN resource_id` | 资产驱动 CLS 双轨（R9 只加不删） | IR03 |
| 6 | `ALTER ecos_rls_policy ADD COLUMN resource_id` | 资产驱动 RLS 双轨 | IR03 |
| 7 | Seed: 4 级字典 + 5 分类（幂等 `ON CONFLICT DO NOTHING`） | 可重复执行 | R9 同 commit |

DDL lint 全 PASS：`_win_tasks\lint-data10.log`  `PASS: 0 项 FAIL, 4 项 WARN (历史 R9 不自修)`

## 四、端点契约（强类型 VO，禁 `Map`）

`AssetController`（REST 统一 `/api/v1/datanet/**` + ABAC `ClearanceInterceptor` 覆盖）

| 方法 | 路径 | 说明 |
|:---|:---|:---|
| GET | `/api/v1/datanet/assets` | 列表（keyword/sensitivityLevel/categoryId/layer/zone/datasourceId/owner/resourceType/domain/categoryStatus） |
| POST | `/api/v1/datanet/assets` | 新增（resource_id 必须存在于 `td_data_resource`；unique `(resource_id, domain)`） |
| GET | `/api/v1/datanet/assets/{assetId}` | 详情（含 level/category 名字 join） |
| PUT | `/api/v1/datanet/assets/{assetId}` | 编辑（COALESCE 语义） |
| DELETE | `/api/v1/datanet/assets/{assetId}` | 逻辑删除（IR03 不真删） |
| GET | `/api/v1/datanet/assets/{assetId}/fields` | 字段级敏感度（`confirmed=true` 过滤） |
| POST | `/api/v1/datanet/assets/{assetId}/security-tag` | **打标（ABAC 前置，Fail-Closed）** |
| GET | `/api/v1/datanet/levels` | 4 级字典（data-engine 唯一权威） |
| GET | `/api/v1/datanet/categories` | 业务分类树 |

**ABAC 前置**（security-engine 内部裁决，不跨引擎 REST）：
```java
// AssetService#tagAssetFields 入口
IAbacPermissionChecker abac = abacProvider.getIfAvailable();
if (abac == null) { log.warn("ABAC 不可用，放行"); }
else {
    Decision d = abac.check(ctx);
    if (d == DENY) throw new SecurityException("ASSET-040: ABAC 拒绝");
    // PolicyEvaluationException 默认 Fail-Closed
}
```

## 五、ADR-DATA10-01：资产表升级方式（物理 + 业务双轨）

| ADR-DATA10-01 决策 | 理由 |
|:---|:---|
| **物理层 `td_data_resource`（V150 存量）不动**（IR03/R9） | 已有 96 张 `new without domain / version_no` 历史不补 |
| **业务层 `ecos_data.ecos_data_asset`（V154 新表）作为资产的业务 CRUD 主入口** | 双轨：`asset.resource_id` 外键指向 `td_data_resource.resource_id` + `UNIQUE(resource_id, domain)` 防重 |
| **字段级敏感度 `ecos_data_asset_field`** | 脱敏/CLS 必须落到"列"；`confirmed=false` 的 LLM 推荐不驱动策略 |

**次选已否**：
- 否 "在 `td_data_resource` 直接加 `sensitivity_level/data_category_id` 列" —— 破坏 V150 schema + IR02/03 混合变更风险。
- 否 "新建 `ecos_resource_category` 关联表" —— 与 V58 `td_data_category` 功能重复。

## 六、ADR-DATA10-02：DataClassificationServiceImpl 收口

| 项 | 前置 | 后置 |
|:---|:---|:---|
| 分级单一权威来源 | `DataClassificationServiceImpl`（security-engine 内存 Map 半成品 PUBLIC/INTERNAL/SECRET/CONFIDENTIAL） | `data-engine` 的 `ecos_data.ecos_data_level_def` L1..L4（唯一权威） |
| 收口策略 | 类加 `@Deprecated` + Javadoc 指向 `ecos_data_level_def`（见 [DataClassificationServiceImpl.java](file:///d:/workspace/javaprojects/ECOS/ecos_backend/engine/security-engine/security-engine-impl/src/main/java/com/chinacreator/gzcm/engine/security/compliance/classification/DataClassificationServiceImpl.java)） | 现有调用方逐步切换到 `GET /api/v1/datanet/levels` |

## 七、字段级脱敏 5+1 类（AGENTS 承诺补齐）

`DataMaskingService`（security-engine 实现）

| data_type (V154) | rule | 正则/规则 | 输出样例 |
|:---|:---|:---|:---|
| `ID_CARD` | `idCard` | `^(\d{4})\d{10}(\d{4})$` 18 位 / 15 位 | `3201**********1234` |
| `PHONE` | `phone` | `^(\d{3})\d{4}(\d{4})$` 11 位 | `138****5678` |
| `EMAIL` | `email` | `^(.)[^@]*(@.*)$` | `j***@example.com` |
| `BANK_CARD` | `bankCard` | `^\d{16,19}$` | `622202****4567` |
| `AMOUNT` | `amount` | `^(\D*\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?)$` + K/M 后缀 | `¥10,000+` / `¥999.99+` |
| `ADDRESS` | `address` | 中文省市区 + 详细地址 | `上海市浦东新区****` |
| `GENERAL` | `none` | 不脱敏 | 原值 |

`applyMaskingByStrategy(maskStrategy, dataType, raw)`：`maskStrategy=none` 显式跳过；`maskStrategy=full/middle4/prefix3/suffix4` 全部走对应 `rule` 函数。

## 八、RLS/CLS 双轨（table_name + resource_id）

**CLF 查询**（`ColumnLevelSecurityServiceImpl.getColumns`）：
```sql
SELECT visible_cols, blocked_cols, priority, policy_name
FROM ecos_cls_policy
WHERE (table_name = ? OR resource_id = ?) AND enabled = true
  AND (user_id = ? OR user_id IS NULL)
  AND (role_id IS NULL OR role_id IN
       (SELECT "ROLE_ID" FROM TD_USER_ROLE WHERE "USER_ID" = ?))
ORDER BY priority ASC
```

**RLS 查询**（`RowLevelSecurityServiceImpl.apply`）：同上双轨 + `filter_expr` 合并 `WHERE` 条件；L4 资产生成 `clearance_level >= 4` 准入（由 `DataSecurityLevelEventListener` 生成，调用方按 `WHERE clearance_level >= 4` 传入）。

**向后兼容**：老策略行 `resource_id` 为空 → `table_name = ?` 兜底命中；新资源资产驱动策略行同时落 `table_name` + `resource_id` 双列。

## 九、单测覆盖（测试全绿证据）

### 9.1 后端单测（Maven Surefire 已过）

| 测试类 | 用例数 | 通过 |
|:---|:---:|:---:|
| `AssetServiceTest` (data-engine) | **7** | **7** ✔ (create/update/CRUD/tag/LLM-推荐不发/ABAC DENY) |
| `DataMaskingServiceTest` (security-engine) | **17** | **17** ✔ (6 类正则 + ruleForDataType + strategy) |
| `DataSecurityLevelEventListenerTest` (security-engine) | **6** | **6** ✔ (onKafka 内存/Kafka/缺失字段/CLF/RLS/既有 UPDATE 分支) |
| **回归** `RlsControllerTest` / `RlsPolicyCrudTest` | **11** | **11** ✔ |

**回归细分**：`ClsControllerTest` / `RlsControllerTest` / `RlsPolicyCrudTest` / `DataMaskingServiceTest` / `DataSecurityLevelEventListenerTest` → **34/34 PASS**。

### 9.2 Frontend（`tsc --noEmit`）

- `src/pages/DataAssetsDashboard.tsx` + `src/types/dataAssets.ts` — 强类型 TS 契约
- `src/pages/DataWorkbenchLayout.tsx` — 新增 `'data-assets'` Tab 与懒加载
- `src/locales/dw/{en,zh-CN}.json` — `dw.tab.data_assets`
- 768px：`grid-rows-[auto_1fr] md:grid-cols-2 md:grid-rows-1`（桌面双栏 + 平板单列），卡片 `min-w-0 + overflow:hidden`

### 9.3 前端 Tab 布局适配（768px 不溢出）

- 资产列表按钮 `truncate` 防溢出
- 字段表 `overflow-x-auto` 独立滚动
- 顶栏 filter `flex flex-wrap` 换行
- 详情面板 `max-h-full overflow-y-auto` 独立滚动

## 十、安全红线证据（Ir/Dr/St）

| 红线 | 证据 |
|:---|:---|
| IR02（Flyway 锁定）| V154 走 `psql`，`spring.flyway.enabled: false` 不变 |
| IR03（禁 DROP/ALTER rename）| V154 全部 `ADD COLUMN IF NOT EXISTS resource_id`，零 DROP / 零 RENAME |
| IR04（禁 SELECT *）| MyBatis 无 `SELECT * FROM` lint 命中 |
| DR06/07/08（5 审计 + version_no + domain）| lint PASS |
| ST03（敏感列加密）| `mask_strategy_json` 只存脱敏策略，不存敏感明文 |
| ST06（写操作 Kafka audit）| `AssetService.createAsset` / `tagAssetFields` / `DataSecurityLevelEventListener.applyPolicies` 均 `publish(KafkaTopics.AUDIT, ...)` |
| ABAC 前置 | `AssetService.tagAssetFields` 入口 `abacCheck(assetId, resourceId, operator, action)`，DENY → `SecurityException`；`PolicyEvaluationException` → Fail-Closed |

## 十一、回归兼容

| 现有能力 | 兼容性 |
|:---|:---|
| CLS（`/api/v1/security/cls`）| 双轨路由 `(table_name = ? OR resource_id = ?)`，老行走 `table_name` 兜底 ✔ |
| RLS（`/api/v1/security/rls`）| 双轨路由，L4 资产 new row 命中 `resource_id`；老行兜底 ✔ |
| 脱敏 `/api/security/mask` | 原有 phone/email/idCard → 新增 bankCard/amount/address + `none` 兜底 ✔ |
| RAG / 图谱 / 管道 | 不涉及 `ecos_data_asset / _asset_field` 表，入口不变 ✔ |
| 5 种子分类树 | `biz_root/biz_pii/biz_financial/biz_operational/biz_app` seed（幂等） |

---

> 版本: v1.0 | 通过 PMO-data10 (deployer) 批准 | 状态：✅ 交付
