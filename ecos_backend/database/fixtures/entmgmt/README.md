# 企业经营管控演示 · 原始台账夹具（域1 数据工作台近源层输入）

## 1. 定位与真实性声明

- 本目录是**合成演示数据**，不是任何真实企业的经营记录；行值、金额、机构、人员均为演示用途构造。
- 用途：作为数据工作台管道 `SOURCE_CSV` 节点的**原始台账输入**，对应需求「数据工作台：原始数据源需要定义…并将数据加载到近源层」。
- 场景句柄：`scenario_id = sc_entmgmt`、`domain = entmgmt`（与安全域 `org_ent2_*` 机构、`u_ent2_*` 人员同一套词表）。
- 逻辑期次：实际 `2026-01 ~ 2026-06`，计划 `2026-07 ~ 2026-12`；金额一律两位小数，币种 `CNY`。

## 2. 为什么是 CSV 而不是 XLSX（裁决项丁的实测答复）

| 事实 | 取证位置 |
|------|----------|
| 数据源类型枚举无 `EXCEL`/`CSV` | `DataSourceServiceImpl.SUPPORTED_TYPES` = POSTGRESQL/MYSQL/ORACLE/MSSQL/DM/KINGBASE/GAUSS/CLICKHOUSE/MINIO/FILESYSTEM |
| 管道 12 类节点里文件侧只有 `SOURCE_CSV` | `PipelineNodeTypesCatalog`（`GET /api/v1/pipeline/node-types`） |
| CSV 读取实现只吃本地绝对路径 | `runtime-access` `CsvConnector`（`Path.of(filePath)`），类注释提到 Excel 但无对应实现 |
| 全仓无 Excel 解析依赖 | `easyexcel` / `apache.poi` / `xlsx` 在依赖树零命中 |

结论：**XLSX 当前无法接入**（要接必须新增依赖与节点类型，撞「不引入新依赖 / 节点目录单源」红线）。本夹具按 CSV 多文件形态提供，Excel 侧的列名、表头、单位口径保持不变，日后若平台补 `SOURCE_EXCEL` 可直接复用同一批台账。

## 3. 文件清单与目标列契约

列名全部 snake_case，与 `ecos_dw.ecos_biz_*` 一期事实表形态对齐；管道 `TRANSFORM_SQL` 段负责改名/归一/派生列。

| 文件 | 数据行 | 逻辑键 | 目标层/表 | 说明 |
|------|--------|--------|-----------|------|
| `dim_project.csv` | 6 | `project_id` | CURATED `ecos_dw` 项目维 | `p-ent-001..003` 沿用一期句柄，`004..006` 二期新增 |
| `dim_contract.csv` | 7 | `contract_id` | CURATED 合同维 | 甲方名称为演示虚构 |
| `dim_attribution.csv` | 18 | `project_id + contract_id + dept_org_id + attribution_type` | CURATED 归因维 | 三级平衡 ACCEPTANCE/COLLECTION/SUBCONTRACT |
| `dim_staff.csv` | 8 | `staff_code` | CURATED 人员维 | `user_id` 跨域引用安全域人员 |
| `fact_cost_voucher.csv` | 126 | `voucher_no`（**含刻意重复**） | APPLICATION `ecos_dw.ecos_biz_cost_fact` | 成本凭证台账 |
| `fact_output_measure.csv` | 68 | `measure_no` | APPLICATION `ecos_dw.ecos_biz_stage_fact` | 计量产值台账 |
| `fact_collection_flow.csv` | 32 | `receipt_no` | APPLICATION 回款流水 | 收款单 |
| `fact_timesheet.csv` | 28 | `period + staff_code + project_id` | APPLICATION `ecos_dw.ecos_biz_resource_fact` | 工时/人力投入 |
| `cfg_allocation_rule.csv` | 3 | `rule_code` | CURATED 分摊规则配置 | `ALC-2026-STD/MKT/SAL` |

## 4. 接入链路（严格用五层名，不使用 ODS/DWD/DWS）

```
CSV 台账（本目录）
  └─ 数据源 type=FILESYSTEM（connection_config.rootPath 指向台账目录）
      └─ 管道A 采集：SOURCE_CSV ─→ SINK_MINIO      ⇒ 近源层 RAW，对象键 raw/structured/{source}/{table}/...（MinIO bucket ecos-datalake）
          └─ 管道B 质量加工：SOURCE_CSV ─→ TRANSFORM_SQL ─→ SINK   ⇒ DW 层 CURATED（dq_status 标 PASSED/SUSPECT/REJECTED）
              └─ 管道C 聚合：CURATED ─→ APPLICATION 事实（ecos_dw.ecos_biz_*）
                  └─ 资产登记 ecos_data_asset / ecos_data_asset_field（经数据工作台，非直插）
```

载体归属依据《数据湖存储分层规范》v2.0：RAW 落 MinIO，CURATED/APPLICATION 落 PG `ecos_dw`；本体与知识只从 DW 层取数。

## 5. 刻意质量缺陷登记（管道与 DQ 规则的验证靶）

缺陷是**故意埋进原始台账**的，用于证明「质量加工」真的发生。行号以本仓库当前版本为准，`grep` 可复现。

| 编号 | 文件:行 | 缺陷 | 期望 DQ 判据 | 期望处置 |
|------|---------|------|--------------|----------|
| D-1 | `fact_cost_voucher.csv:122` | 部门机构号 `org_ent2_infra_p9` 不在安全域组织主数据 | 引用完整性 | REJECTED |
| D-2 | `fact_cost_voucher.csv:123` | 期间写成 `2026/04`（非 `YYYY-MM`） | 形态校验 | 归一为 `2026-04` 后 PASSED |
| D-3 | `fact_cost_voucher.csv:124` | 项目号 `project_id` 为空（凭证 `CB-DEFECT-003`，金额本身正常） | 必填列 + 归属可判定 | REJECTED |
| D-4 | `fact_cost_voucher.csv:125` | 金额 `-45000.00`（凭证 `CB-DEFECT-004`，负数且无冲销凭证引用） | 金额符号域（非负） | REJECTED |
| D-5 | `fact_cost_voucher.csv:126` vs `:2` | 同凭证号 `CB-5-001-1000` 重复报销 | 逻辑键唯一 | 后到行 REJECTED |
| D-6 | `fact_cost_voucher.csv:127` | 成本类别 `salary` 小写 | 词表归一 | 归一 `SALARY` 后 PASSED |
| D-7 | `fact_collection_flow.csv:33` | 合同号 `c-ent-999` 不在合同台账 | 引用完整性 | REJECTED |
| D-8 | `fact_output_measure.csv:69` | 累计计量产值低于上期（本期增量 `-14600000.00`） | 累计单调性 | REJECTED |
| D-9 | `fact_timesheet.csv:29` | 工时期间 `2026-01` 早于项目开工 `2026-03-15` | 业务时序 | REJECTED |
| D-10 | `dim_attribution.csv:12,13` | p-ent-004 ACCEPTANCE 比例合计 `1.0500` 超 1 | 分摊比例合计 = 1（容差 0） | 该组全量 REJECTED |
| D-11 | `dim_attribution.csv:14,15` | COLLECTION 同逻辑键整行重复录入 | 逻辑键唯一 | 后到行 REJECTED |

配套：一期 seed 里「恰好压线不触发」的正例仍保留（`p-ent-001` ACCEPTANCE `0.6+0.4=1.0000`），用于证明 DQ 规则不是恒红。

### 5.1 缺陷 → 治理层落点 → 实际判据 对照（逐条核过源码，非声明）

治理层落点只有 4 张事实表，与 fact import 的封闭五值 `factType` 一一对应；二期 DQ 定义面见
`docs/50-db_script/03-seed/entmgmt_demo_seed_v2_w4_data.sql` §8（13 条，`target_id` 指向 CURATED 资源，`status='ACTIVE'`）。

| 缺陷 | 源台账 CSV | 治理层落点 | 实际判定者 | 实况结论 |
|---|---|---|---|---|
| D-2 `2026/04` 期间形态 | fact_cost_voucher:123 | `ecos_dw.ecos_biz_cost_fact` | 行门禁 **DQ-F04**（`PERIOD_PAT`）| 真拒绝；规则 ENT-STG-001 是同一判据在产值资产上的声明式登记 |
| D-3 `project_id` 空 | fact_cost_voucher:124 | 同上 | — | **当前代码不拒**：COST 必填集 `departmentId/period/costCategory/directOrAllocated/amount/factType` 不含 `projectId`（`DqRowGateImpl.buildRequiredFields` :350-361），`is_pool` 也无一致性校验 ⇒ 会产出「无主 + is_pool=0」成本行。缺口已登记，未冒充成 DQ 成果 |
| D-4 金额 `-45000.00` | fact_cost_voucher:125 | 同上 | 行门禁 **DQ-F03**（`AMOUNT_FIELDS` 含 `amount`，`signum()<0`）| 真拒绝。「无冲销引用」这一半平台没有对应判据，只有非负这半生效 |
| D-5 凭证号重复 | fact_cost_voucher:126 vs :2 | 同上 | 行门禁 **DQ-F01** | 真拒绝，但判定键是 `projectId\|departmentId\|period\|costCategory\|directOrAllocated\|factType` **六元组**（`businessKey` :308-321），**不含 `voucher_no`**；两行恰好六元组相同（金额 1200000 vs 120000 不参与键）|
| D-6 类别小写 `salary` | fact_cost_voucher:127 | 同上 | 规则 **ENT-CST-001**（ENUM 取模板词表 SALARY/MANAGEMENT/MARKETING）| 评分面命中；行门禁不做枚举归一 |
| D-1 部门机构号悬空 | fact_cost_voucher:122 | 同上 | — | **平台无引用完整性维度**：`DqDimension` 词表（COMPLETENESS/ACCURACY/CONSISTENCY/UNIQUENESS/VALIDITY/FRESHNESS）没有 REFERENCE 类，行门禁也只比 `contractId×contractBase`（DQ-F10，仅 WARN）。缺项已登记 |
| D-7 合同号悬空 | fact_collection_flow:33 | **无治理层落点** | — | 回款流水没有事实表承载：`factType` 封闭枚举无收款/回款类型，`ecos_dw` 亦无对应表 ⇒ 只在源层 RAW 对象与管道日志中可观察，不进资产评分面 |
| D-8 累计单调性 | fact_output_measure:69 | `ecos_dw.ecos_biz_stage_fact` | 无单调性判据 | 门禁只有单行值域（F02/F03）与同月一致（F04），**没有跨期累计单调性校验**。该行只有在本期增量被映射进 `amount` 时才因"为负"被 **DQ-F03** 拒 —— 生效语义是"金额为负"，不是"累计倒挂"，如实记录 |
| D-9 工时早于开工 | fact_timesheet:29 | `ecos_dw.ecos_biz_resource_fact` | 行门禁 **DQ-F11** 邻近但不等价 | F11 只校验 `ACTUAL` 期间不晚于当前月，**不比对项目开工日** ⇒ 该缺陷在现行代码下不被拒绝（2026-01 早于 2026-10 完全合法）。缺项已登记 |
| D-10 归属比例合计 1.05 | dim_attribution:12,13 | `ecos_dw.ecos_biz_project_attribution` | 行门禁 **DQ-F06**（同 `(projectId,contractId)` Σ>1 ⇒ 组内全拒）| 真拒绝 |
| D-11 归属整行重复 | dim_attribution:14,15 | 同上 | 行门禁 **DQ-F01** | 真拒绝 |

**词表与列形态落差（必须在导入 payload 构造时归一，否则相应门禁恒不触发）**：

- `direct_or_allocated`：本目录取值为 `DIRECT / ALLOCATION`，平台 COST 模板与 DQ-F08 判定字面量是 `DIRECT / ALLOCATED` ⇒ 映射时须归一。
- `evidence_ref` / `fact_type` / `is_pool`：COST 模板要求这三列，而原始台账没有（`fact_type` 还是 COST 必填列，且它参与 DQ-F01 业务键）。DQ-F09 对 `type=COST` 的行**恒要求** `evidenceRef ∨ sourceRef` ⇒ 原样导入会导致成本行**全量被拒**。构造 payload 时正常行给引用、刻意缺陷行留空，才能同时证明"加工发生"与"门禁有效"。
- `cost_category`：RESOURCE 的必填集含 `costCategory`（`buildRequiredFields` :354-355），而工时台账没有成本类别列 ⇒ 映射时须按人员时薪归属补出该维度，否则 RESOURCE 整批被 F07 拒。
- 归一与补列发生在**调用侧**：平台管道节点宇宙（12 种）里没有质量/转换节点，`SOURCE_CSV → SINK_MINIO` 只做搬运；CURATED 层的唯一写通道是 fact import，它只拒绝、不转换。这是本期实测到的架构实况，不是设计意图。


## 6. 跨域引用一致性（本目录自检结论）

- 机构号：本目录出现的全部 `org_ent2_*` 均在 `docs/50-db_script/03-seed/entmgmt_demo_seed_v2_w3_iam.sql` A 段有定义；唯一悬空项即刻意缺陷 D-1 的 `org_ent2_infra_p9`。
- 人员：`dim_staff.csv.user_id` 7 枚（`u_ent2_proj_a/b/c`、`u_ent2_infra_fin`、`u_ent2_energy_fin`、`u_ent2_data_gov`、`u_ent2_group_cfo`）全部命中域0 seed E 段人员。
- 合同：`fact_collection_flow.csv` 引用的合同号除刻意缺陷 D-7 外全部命中 `dim_contract.csv`。
- 项目：`p-ent-001/002/003` 与一期 seed 同句柄（可在二期对照中复用）；`p-ent-004/005/006` 为二期新增，一期 seed 无对应行，属预期。

## 7. 入库纪律

- 本目录**不含任何凭证、本机绝对路径或环境变量值**。`FILESYSTEM` 型数据源无口令；`SOURCE_CSV` 节点所需的 `filePath` 绝对路径由部署侧注入，禁止写进 seed 或本文档。
- 原始台账属**定义面/输入面**制品，可入库；DW 层与 MinIO 的**结果面**（近源对象、事实行、资源登记）必须由管道真实执行产出，禁止 seed 直插。
- 文件为确定性生成（无随机、无时间戳），重跑生成器产出逐字节一致；生成器为会话临时件，不入库。
