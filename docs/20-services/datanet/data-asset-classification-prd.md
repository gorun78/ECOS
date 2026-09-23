> 来源: PMO（用户指令，2026-09-22） | 日期: 2026-09-22 | 责任人: PM（Fullstack 待领取）
> 版本: v1.0（批准，可进入拆解）
> 追溯: 网络安全管控模块（数据分级分类/等保 L1-L4） + 架构铁律 §2.4 安全接入 + 数据湖存储分层规范 §一 + 数据库访问规范 DR01~DR08 / ST03~ST06
> PRD 编号: PRD-数据资产与分级分类（number_to_be_assigned，落地时分配）

# PRD：数据资产管理与分级分类（数据工作台 + 安全中心协作）

## 〇、背景与现状（实证结论）

> 依据 2026-09-22 代码库核查，以下结论均有代码/DDL 佐证。

1. **数据工作台当前无"数据资产"产品功能**：仅 5 Tab（数据源连接/管道构建/数据质量/血缘/引擎配置），无资产 Tab、无资产 CRUD 端点。
   - 但**物理事实源已存在**：`td_data_resource`（资产行）+ `td_data_field`（511 列元数据）+ `td_datasource`，被 `MetadataCollectTaskExecutor`/`MetadataServiceImpl` 写入，被 `GET /api/v1/datanet/metadata/resources|fields|preview` 暴露（`src/services/dataCatalogClient.ts` 消费）。
   - `td_catalog_item`（目录索引，V19）与 `td_data_resource` 是同份资源的两个视图，`category_path` 为字符串路径，**无外键**；`td_data_category`（V58）仅 5 条种子、无真实业务树。
   - 全仓不存在 `DataAsset` 命名的实体/表/Controller。
2. **security-engine 现状**（护，:18081）：
   - **真正运行**的是：人员准入等级 `clearance_level`（`td_user_security_profile`/`td_role_security_profile` + V41 五维作用域）**对人是准入级，不是数据级**；RLS（行，`ecos_rls_policy.filter_expr`）+ CLS（列，`ecos_cls_policy.visible_cols/blocked_cols`）+ 脱敏（`DataMaskingService` 仅 3 条硬编码正则）按 `(table_name, role, user)` 文本匹配取策略，**无 resource_id 外键、不消费 level_code**。
   - **半成品**：`DataClassificationServiceImpl` 纯内存 Map + 2 条 keyword 规则 + 4 级 + 0 Controller + 0 持久化表 + 0 前端入口 + 1 行 TODO，**非生产功能**。
   - **"RMF 六切片风险决策 / 等保定级 L1-L4"**：文档所述能力在本仓库**无对应落地代码**（等保定级实际指人员准入级）。
3. **ai-engine `/catalog/assets/{assetId}/auto-classify` 是 mock 展示**（`ClassificationController` L134-155 硬编码 mock 字段、不查真实资源、不落库、产出的 `level` 不被 CLS/RLS 或任何下游消费）。前文知识工作台「自动分类」按钮因此"跑了个寂寞"。

## 一、功能定位

> **数据资产 + 数据分级分类是数据工作台的治理底座，安全中心消费并执行其安全产出。**

- **主体能力（数据工作台，土 D）**：
  - ① 数据资产管理：把 `td_data_resource` + `td_data_field` 升级为**可手工维护的资产**（提供资产注册/编辑/详情/检索），并建立**业务分类树**（DW 层资产归属）。
  - ② 数据分级分类：为**资产级 + 字段级**标注 `sensitivity_level`（L1~L4）+ `data_category_id`（业务类目）+ 敏感标识。
- **协作能力（安全中心，护）**：
  - **消费**数据工作台导出的"资产/字段级敏感度"，**驱动**字段级 RLS/CLS 策略自动生成 + 脱敏规则匹配 + 准入级比对放行。
  - 安全中心**只读消费**分级分类，**永不回写**数据资产的分级字段（铁律 §2.4 单向依赖）。
- **边界（防重复造）**：
  - 数据分级 ≠ 人员准入级（`clearance_level`）——前者描述数据敏感，后者描述人的权限深度，运行时在 RLS 判定链内比大小裁决。
  - 不重复 security `DataClassificationServiceImpl` 的内存分级——**标记 deprecated**，4 级语义迁入 `td_data_level_def`（见范围 C-5）。
  - 不采用 ai-engine mock 的 `auto-classify` 作为基础设施（其 LLM 能力**可保留**为"推荐分级"的调用侧，但落库/生效归数据工作台）。

## 二、目标（P0/P1/P2）

| 期 | 目标 |
|:--|:--|
| **P0（闭环可演示）** | 资产 CRUD + 详情；分级 L1~L4 + 业务分类树 CRUD；字段级敏感标注；**资产分级变更 → 驱动 CLS/脱敏 生效**（一条回归链路打通）；资产列表前端 Tab |
| **P1（治理完整）** | 批量分级/批量分类；LLM 推荐分级（人工确认）；字段级 RLS 自动生成；准入级比对裁决；分级分类报表（资产敏感度分布/空类目） |
| **P2（增强）** | 分级变更历史/审计下钻；分类模板导入；与知识工作台知识导航的敏感维度打通（可协调） |

## 三、依赖与支撑

### 3.1 前置硬依赖

| 依赖 | 说明 | 现状 |
|:--|:--|:--|
| `td_data_resource`/`td_data_field`/`td_datasource` | 资产事实源 | ✅ 已有（V19/V54/V133） |
| security-engine RLS/CLS 服务 | 执行侧（`RlsController`/`ClsController`/`DataMaskingController`） | ✅ 已有，需加字段级 + resource 维度 |
| security-engine ABAC（OPA） | 写操作前策略评估 | ✅ 已有（`/api/v1/security/policy-engine/evaluate`） |
| runtime-event + Kafka（`ecos.audit`） | 审计 | ✅ 已有 |
| llm-gateway | 推荐分级调用 | ✅ 已有 |
| `td_data_level_def`/`td_data_category_tree`/`td_data_asset(_field)` | 新增核心表 | ❌ 需新建（V156~V158） |

### 3.2 支撑（下游消费者）

- **安全中心**：CLS/RLS/脱敏/准入裁决（本功能核心服务对象）
- **知识工作台**（指令 2）：知识资产若需"敏感维度"导航可引用资产分级（P2）
- **本体/场景工作台**：经 `GET /api/v1/engine/data/layers/CURATED` 读资产时透传分级元数据

## 四、数据模型（3 份 DDL，V156~V158，只加不删）

### V156 `td_data_level_def`（分级字典，data-engine 持有）
```
id PK UUID | level_code VARCHAR(10)  UNIQUE | level_name | l lescription TEXT
 | default_masking_rule VARCHAR(50)  -- 关联: 该级别默认脱敏规则(email/idcard)
 | sort_order INT | 审计5字段 | domain | version_no
-- seed: L1 一般(脱敏无) / L2 内部(脱敏手机号) / L3 敏感(脱敏身份证/银行卡) / L4 核心(全列脱敏+密文)
```
> **ADR-1 裁决**：4 级字典由 data-engine 持有（非 security），security 只引用 `level_code` 语义。

### V157 `td_data_category_tree`（业务分类树 ≤3 级）
```
id PK UUID | domain | parent_id FK(self NULL 根) | path VARCHAR(512)  -- /root/child/leaf 冗余
 | level SMALLINT(1~3) | category_name | sort_order
 | 审计5字段 | version_no
 UNIQUE(domain, path)；idx(category tree: domain, parent_id)
-- seed: 继承 V58 的 5 条作为 L1（Data Assets/Raw/Curated/Semantic/Application），后续业务项人工维护
```

### V158 `td_data_asset` + `td_data_asset_field`
```
-- 资产业务视图（升级自 physical resource，独立表，不改 t d_data_resource 语义）
td_data_asset:
  id PK UUID | resource_id VARCHAR(36) UNIQUE  -- 指向 td_data_resource.id（软关联，不建硬外键，R9 兼容）
  | domain | business_name | owner_user_id | owner_org_id
  | sensitivity_level_code VARCHAR(10)  -- 默认取资源列最大级别
  | data_category_id FK(td_data_category_tree)
  | is_pii SMALLINT | data_format | partition_scheme | retention_policy
  | 审计5字段 | version_no
  idx: resource_id / domain+x_sensitivity_level / data_category_id

td_data_asset_field:
  id PK UUID | asset_id FK(td_data_asset) | field_id FK(td_data_field)
  | field_name | field_sensitivity_level_code VARCHAR(10)
  | is_masked SMALLINT | mask_rule VARCHAR(50) | 审计5字段 | version_no
  UNIQUE(asset_id, field_id)；idx: asset_id / field_sensitivity_level_code
```
> **ADR-2 裁决**：新建 `td_data_asset(_field)` 业务视图，**不在** `td_data_resource` 上加列（尊重 R9 只加不删 + 保持物理资源表语义纯净）。
> **字段级敏感度**落 `td_data_asset_field`，因脱敏/CLS 必须落到列。

## 五、接口契约（data-engine 强类型 VO，禁 Map）

### 5.1 资产（新增 `DataAssetController`，`/api/v1/datanet/assets`）
- `GET /assets?domain=&layer=&pageNum=&pageSize=` → 分页资产（联 `td_data_resource.layer/zone`）
- `GET /assets/{assetId}` → 资产详情（含字段清单 + 字段级敏感度）
- `POST /assets`（注册，body `DataAssetSaveDTO`）| `PUT /assets/{id}`（编辑）| `DELETE /assets/{id}`（逻辑删）
- `POST /assets/{assetId}/tag`（分级分类打标，body `SecurityTagDTO{assetLevel, categoryIds[], fieldTags[]}`）
- `GET /assets/{assetId}/security-level` → 返回资产/字段级敏感度（**供 security 消费**）

### 5.2 分级分类字典（`DataClassifyController`，`/api/v1/datanet/classify`）
- `GET/POST/PUT/DELETE /levels`（分级字典）
- `GET /categories/tree?domain=` | `POST/PUT/DELETE /categories`（类目树 ≤3 级校验）
- `POST /assets/batch-tag`（批量打标，单批 ≤100，`REQUIRES_NEW`）
- `GET /assets/{id}/recommend-level`（LLM 推荐分级，**仅返回候选，人工确认后走 `/tag` 落库**）

## 六、与 security-engine 协作（护 的 3 项改造，暴露面收敛）

| # | 改造 | 说明 |
|:--|:--|:--|
| C-1 | 字段级敏感度 | `ClsController`/`DataMaskingController` 新增按**字段名 + asset_id** 匹配（现仅 `table_name` 文本），`ecos_cls_policy`/`ecos_rls_policy` **ADD COLUMN `resource_id`**（R9 只加；`table_name` 兜底不删） |
| C-2 | RLS 准入裁决 | `RlsServiceImpl.apply` 增加一级：`clearance_level(caller) >= asset.sensitivity_level` 才放行（跨护/data 只经 security 内部 REST 查资产级，不暴露新跨引擎写 API） |
| C-3 | 脱敏规则可配 | `DataMaskingService` 从 3 条硬编码正则升级为**消费 `field_sensitivity_level_code` + 可配规则** |

**协作铁律**（写入守护）：
- 单向依赖：security → data-engine（读分级分类）。data-engine **永不**反向依赖 security 安全执行。
- 字段级授权：CLS/RLS 从"按 table_name"升级为"按 resource_id + 字段敏感度"；`table_name` 保留兼容。
- 脱敏规则可配：敏感列（身份证/手机/邮箱/银行卡/金额）标敏感级 → 自动套对应脱敏规则，不再运维手配。
- 级别字典：`td_data_level_def` data-engine 持有（L1~L4）；security `clearance_level`（人对等）**独立**，运行时在 RLS 判定链内比大小，**不共享字段/表**。

## 七、范围与验收

### P0 范围（本期一次完成，可验证闭环）
- [ ] V156/V157/V158 DDL + seed（只加不删，`ADD COLUMN` 不删旧列）+ rollback 脚本，lint 全 PASS
- [ ] data-engine `DataAssetController` / `DataClassifyController`（强类型 DTO/VO，禁 Map）/ `DataAssetService` / `DataAssetMapper`
- [ ] security C-1/C-2/C-3（字段级 CLS / RLS resource_id / 脱敏可配）
- [ ] `DataClassificationServiceImpl`（内存半成品）标记 `@Deprecated`（ADR-3），其级别语义迁 `td_data_level_def`
- [ ] 前端数据工作台 `资产总览 Tab` + `资产分级分类 Tab` + `分级分类配置`
- [ ] **闭环回归证据**：某资产某列标 L3-身份证 → `ecos_cls_policy` 自动出现脱敏列 → 前端查询该列返回 `***`
- [ ] `deliverable_allowed=true` + reviewer PASS（ocr + 安全审计）+ PM 验收报告含 curl 证据

### 红线自检（违反即打回）
- ST03 敏感列加密（KMS/AES）+ ST04/05 行级列级过滤 + ST06 写操作 Kafka `ecos.audit`
- IR01~06：gateway 零 JdbcTemplate；新 Driver 走 runtime-access；零 `SELECT *`；零裸 SQL 拼接；零 DROP/ALTER
- DR02/06/07/08：`ecos_` 前缀（注：`td_data_*` 为 knownLegacy 白名单，**本批新增表沿用 `td_data_` 既有序列以与 V19 资产表同域**，例外留痕 ADR-4）| 审计 5 字段 + `domain` + `version_no`

## 八、技术架构增量
- datasource-bridge：字段级敏感度采集写 `td_data_asset_field`
- RlsServiceImpl：准入裁决一段
- DataMaskingService：规则可配
- data-engine 新增 Asset/Classify 双 Controller，`ecos.audit` 事件发 Kafka

## 九、风险与回滚
| 风险 | 等级 | 缓解 |
|:--|:--|:--|
| RLS 加准入裁决影响现有 5 维作用域 | M | 仅新增比对段，`clearance_level >= level` 才拦，旧行为不变（兜底 table_name） |
| 资产视图与 physical resource 双写一致性 | M | `resource_id` 唯一约束 + 资产注册时事务校验；采集刷新只更新 physical 不覆盖人工 tag |
| 脱敏规则可配回归 | L | 保留 3 条内置规则为默认，可配规则叠加不替换 |
| 字典双头（data vs security） | M | ADR-3 明确字典归 data，security 只引用 level_code |

## 十、分期与里程碑
- M1（P0）：资产 CRUD + 分级分类 CRUD + 字段级标注 → **闭环回归通过**
- M2（P0 收尾）：前端 Tab + 验收
- M3（P1）：批量 + LLM 推荐 + 报表
- M4（P2）：审计下钻 + 跨工作台敏感维

## 关联文档
- 数据湖存储分层规范（layer/zone 语义）：[../../7跨切/数据湖存储分层规范.md](placeholder-relative-path)
- 架构铁律 §2.4 安全接入：[../../00-架构/ARCHITECTURE-RULES.md](../../00-架构/ARCHITECTURE-RULES.md)
- 数据库访问规范：`.trae/rules/数据库访问规范.md`
- 知识导航 PRD（指令 2）：[../kb-engine/knowledge-navigation-prd.md](../kb-engine/knowledge-navigation-prd.md)
