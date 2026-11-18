# 数据库脚本整理计划 — 完成度核账（2026-11-18）

> 依据: `C:\Users\guorongxiao\.qoder-cn\plans\crisp-fjord-skua.md`（用户已批准）
> 口径: **每条都指向可复现的实跑证据，不引用台账自述**｜责任人: 数据库整理批次

本册只回答一个问题：**批准的计划里 6 个阶段，哪些真的落地了、证据在哪、哪些尚未可做。**

---

## 一、六阶段状态总表

| 阶段 | 内容 | 状态 | 证据落点 |
|:--|:--|:--:|:--|
| 0 | 备份与结构/数据基线固化 | ✅ | `schema-snapshot-actual.sql` + `data-snapshot-actual.md` |
| 1 | `public` 硬编码归位 `ecos_control` | ✅ | 见 §二逐项 |
| 2 | 生成完整初始化脚本 `00_init/` | ✅ | `00_init/README.md` §五 验证记录 |
| 3 | 清理现存库与脚本一致 | ✅ | 见 §三（含一项被反证否决） |
| 4 | 演示数据四件套（清空/初始化/校验） | ✅ | `04-demo-data/README.md` §四 **13 腿** |
| 5 | 归档 298 历史脚本 + 门禁扫描面同批改 | ✅ | `50-db_script/README.md` §阶段5 |

---

## 二、阶段 1 逐项核账（计划点名的 3 处代码 + 1 处文档）

| 计划项 | 要求 | 实测 | 判定 |
|:--|:--|:--|:--:|
| 1.1 | `JdbcQueryHistoryRepository` 的 `public.` 硬编码 → 配置注入 | `JdbcQueryHistoryRepository.java:51` 为 `@Value("${ecos.db.control-schema:ecos_control}") String schema`；Javadoc `:17-19` 明标「落库面 = V212 冻结 DDL，V345 迁入 ecos_control」 | ✅ |
| 1.2 | `CognitiveObjectClass` 2 处失效 carrier 改指真实表 | carrier 实指 `ecos_cognitive.ecos_cognitive_evidence` / `.ecos_cognitive_hypothesis` / `.ecos_cognitive_belief` / `ecos_dw.ecos_application_forecast_run`；残留 4 处 `public.*` **全在 `legacyWriteSuspended` 清单内**（该文件 Javadoc `:19` 明写「按 IR03 只停写、不 DROP、不改名」），并被 `DdlCodeSchemaAlignmentGateTest.NON_READ_SURFACES` 登记为声明式目录豁免 | ✅ |
| 1.3 | 剩余 43 张 `public` 唯一空表归位 | 由 `11_public_pending.sql` 承载 **7 张**「归属待裁」表（原样保留 `public` 前缀、不臆造归属）；其余已由 V345/V346 实库搬入归属 schema（`11_public_pending.sql` 头注实测） | ✅ 按裁决口径 |
| 文档 | 订正 `50-db_script/README.md` 中 V200 错误陈述 | 已订正：`V200` 号被两份互不相干脚本占用（单源 `V200__ai_tool_registry.sql` 建 AI 工具注册表 vs 归档那枚做 schema 治理），原「V200 已收录进单源」为误记 | ✅ |

---

## 三、阶段 3 逐项核账（6 项破坏性动作）

| 项 | 计划动作 | 实测 | 判定 |
|:--|:--|:--|:--:|
| A | `DROP SCHEMA ecos_sysman CASCADE` | 活库 `pg_namespace` 已无该 schema | ✅ 已执行 |
| B | `DROP SCHEMA ecos_security CASCADE` | 同上 | ✅ 已执行 |
| C | DROP `public` 中被遮蔽的 15 张空表 | 活库 `public` 仅余 7 张待裁表（`11_public_pending` 清单一致） | ✅ 已执行 |
| D | DROP `public` 中无代码引用的唯一空表 | 同上，收敛为 7 张待裁面 | ✅ 已执行 |
| E | `public.ecos_scenario_query_history` 迁 `ecos_control` | V345 已落；代码读端走 `ecos.db.control-schema` 注入 | ✅ |
| F | 运行时噪声清空（可选） | 未执行 | ⏸ 计划标注「可选」 |
| 附注 | `DwSchemaQualifier` 白名单移除将 DROP 的 schema | **经反证否决，不执行**：`ecos_sysman`/`ecos_security` 虽已 DROP，但 deny-list 留着零成本、纯保护 —— 若无此项，ultimate 档配 Doris 时 `carrierRef="ecos_sysman.x"` 会被误判为合法 OLAP 载体；`ecos_dq` 属仍存在的 knownLegacy，必须判 CONTROL。已用 `ControlSchemaDenyListRatchetTest` 双向钉住 | ✅ 否决并钉住 |

---

## 四、阶段 4 的关键补证：完整 11 文件链（2026-11-18 新增）

`04-demo-data/README.md` §四 原有 12 腿，但**建库时未纳入 `11_public_pending.sql`** ⇒ `public` 7 表缺失、从零建库不完整。

本轮以库 `ecos_demo_rt2` 补跑并留证：

| 动作 | 实测 |
|:--|:--|
| 按 `00_init/README.md` 规定顺序 `00→08→**11**→09` 逐册执行（`ON_ERROR_STOP=1`） | **11 文件逐个 EXIT=0**，`TOTAL_FAIL=0` |
| `public` 落点 | **7 张待裁表**，与 `11_public_pending.sql` 头注声明一致 |
| `10_system_seed.sql` 实跑 | 自检 `td_user=17 / loginable=5 / admin_super=1 / td_role_permission=135 / sys_config=178` |
| `demo_seed.sql` 实跑 | 自检 `scenario=245 / ontology_entity=18 / kb_doc=36 / pipeline_def=41` |
| `demo_verify.sql` | **86 项 OK / 0 EMPTY / 0 DRIFT**（58 演示 + 27 种子 + 1 超管） |

补充旁证（同一日，库 `ecos_demo_rt`）：seed dry-run 回滚不动库、连跑两次 seed 行数不翻倍（245/13/36/41）、cleanup dry-run 回滚不动库、cleanup 实跑后演示面归 0 而种子面完好（`td_user=17` / `sys_config=178`）、再 seed 后 verify 回到 86 OK。

> 两个临时库在取证后已 `DROP DATABASE`，`sys_man` 全程未被触碰。

---

## 五、阶段 5 要点复述（详版见 `50-db_script/README.md` §阶段5）

- `git mv` 298 枚 V1~V349 → `90-归档/50-db_script/02-flyway-migration-legacy/gateway/`，其中 **293 枚 R100**（内容逐字节相同）；活单源只留 V350+。
- 51 个 Java 门禁 + `_win_tasks/db-migration-lint.ps1` 同批改为「活单源 ∪ 归档」并集扫描。
- 实跑复验：11 模块 test-compile SUCCESS；44 枚门禁 **202 tests / 200 绿 / 2 存量红 / 2 存量 skip**。
- 2 枚红已反证为存量且**未掩盖**：`DdlCodeSchemaAlignmentGateTest` 的 11 个 `dq_*` 重名（V111 vs V342 语料并存）、`OntologyNoFactValueArchTest` 的 V344 钉值缺口（**经 §8.7 反证为门禁误报**：V344 无任何金额列定义，命中的是 `COMMENT ON COLUMN` 字符串字面量）。

### 5.1 阶段 5 遗漏的归档引用链已补修（2026-11-18）

docs 归档批（`1c5b2840`）把两份设计册移入 `90-归档/30-设计/详细设计-分册/`，但**三处引用未同批改**，导致两枚门禁红与一处 pom 注释失效：

| 位置 | 原引用（已失效） | 已改为 |
|:--|:--|:--|
| `RepoTestSurfaceExecutabilityTest.java:84` | `docs/30-设计/详细设计-04-知识域-dccheng与kb-engine-2026-09-29.md` | `docs/90-归档/30-设计/详细设计-分册/…`（同名） |
| `OpenApiPathParityTest.java:81` | `docs/30-设计/详细设计-07-场景层-workspace场景工作台-2026-09-29.md` | `docs/90-归档/30-设计/详细设计-分册/…`（同名） |
| `kb-engine-impl/pom.xml:178` | 同上详设-04 旧路径 | 同上新路径 |

**改路径不够，还核了锚点内容确实在新位置**（否则只是把「路径不存在」换成「内容不匹配」）：
详设-04 含 `F04-03` ×4 与验收命令原文 ×1；详设-07 含 `D-2` ×2、`N1~N12` 齐全。

**复跑证据**：`mvn -o test -pl gateway -Dtest='RepoTestSurfaceExecutabilityTest,OpenApiPathParityTest,MigrationSingleSourceTest'`
⇒ **Tests run: 18, Failures: 0, Errors: 0** / `BUILD SUCCESS`。
（对照：修复前同一批为 `Tests run: 14, Failures: 1`，`RepoTestSurfaceExecutabilityTest` 11 跑 1 红。）

### 5.2 顺带修掉 D119 的测试桩缺口（`TaskControllerWireShapeTest.listShape`）

`TaskController.java:195` 在 D119（2026-11-15）把 `items.size()` 改为 `countTasks(condition)` 取真实总数，
但 `TaskControllerWireShapeTest` 的动态代理桩（`switch (method.getName())`）**没有对应分支** ⇒ 落 `default -> null` ⇒ 拆箱 NPE。

修法两条（都必要，缺一即假绿）：

1. 桩补 `case "countTasks" -> 7;`
2. **补断言** `assertEquals(7, data.path("total").asInt())` —— 否则 `total` 键的语义（真实总数 vs 当页条数）无人盯，D119 的修复本身也没被锁住

**变异探针（两枚，路径不同，缺一不可）**：

| 探针 | 改法 | 结果 |
|:--|:--|:--|
| ① 移除分支 | 删掉 `case "countTasks" -> 7;` | `listShape` **ERROR**（NPE 拆箱 null）—— 证「漏分支会被发现」 |
| ② 改返回假值 | 桩改 `-> 99` | `listShape:114` **`expected: <7> but was: <99>`** —— 证「`total` 断言本身有鉴别力，不是恒真」 |

复原后 ⇒ **5/5 全绿**。两枚探针分别覆盖「分支缺失」与「值错误」两条失效路径。

### 5.3 gateway 全量复跑记录（含一次环境卡顿，如实登记）

| 时点 | 内容 | 结果 |
|:--|:--|:--|
| 修复前 | `mvn -o test -pl gateway`（`-Dtest='SecretScanGateTest,RepoTestSurfaceExecutabilityTest,MigrationSingleSourceTest'`） | `Tests run: 14, Failures: 1` |
| **改路径之后** | `mvn -o test -pl gateway`（`-Dmaven.test.failure.ignore=true` 全量） | **`Tests run: 228, Failures: 1, Errors: 1, Skipped: 11`**。对照 §7.5 旧记录（`Tests run: 384, Failures: 8, Errors: 1, Skipped: 17`）：**红从 9 降到 2**。两次测试总数不同（384 vs 228）系定向/全量选择集差异，**不据此宣称「消掉 7 条」**；只陈述实测的两个快照。余 2 条 = `DdlCodeSchemaAlignmentGateTest`（11 个 `dq_*`，待裁）+ `TaskControllerWireShapeTest.listShape`（D119 桩缺口） |
| 修 D119 桩之后 | 定向 `TaskControllerWireShapeTest` + 变异探针 | 5/5 绿；探针红；复原绿 |
| 全量再复跑 | 同命令重跑 | ⚠ **卡在 `E5ConfigCarrierSingleSourceTest`（`Files.walk(ecos_backend)` 全仓遍历），16 分钟无输出，人为中止**。判定为环境卡顿（与运行中的后端 JVM 并发读同一 `target/` 树），**非测试失败**；未取得完整结论，不据此宣称「全量零红」 |

**当前可断言的状态**：`DdlCodeSchemaAlignmentGateTest` 的 11 个 `dq_*` 重名是**唯一已知残余红**（裁决项见 `dup-family-adjudication.md` §8.4）；
`RepoTestSurfaceExecutabilityTest` / `OpenApiPathParityTest` / `TaskControllerWireShapeTest` 三处已修并各自定向验证。

---

## 六、计划内**不可执行**项（已反证，非遗漏）

| 计划项 | 为何不可执行 |
|:--|:--|
| 阶段 3 附注：移除 `DwSchemaQualifier` 中的 `ecos_dq`/`ecos_sysman`/`ecos_security` | 删掉是净损失（见 §三附注）；已反向钉住 |
| 阶段 4「在真实库 `sys_man` 上跑清空」 | 破坏性，**需用户逐项授权**；流程与演练见 `04-demo-data/README.md` §七。演练侧已用独立库完整验证 |

---

## 七、计划外的实际状态：后续工作不在本计划范围

本计划 6 阶段已全部收口。与之相邻但**不属于本计划**、当前仍挂账的是：

- 36 重名表族的逐族处置（`dup-family-adjudication.md` §七/§八）：**36 族无一可在无授权下执行**，7 项待裁（A1-残 / A2~A7）。
- `IAgentProfileService` 零实现导致 `/api/v1/agent/profiles` 502 等运行面缺陷。
- lint 自身假绿缺陷 D-15/D-16（DR04/DR05 用 `Singleline` 恒 0 命中、DR02 捕获组缺 `.`）。

这些已各自登记在 `docs/50-测试/ECOS待裁决事项清单-2026-11-17.md`，不在本册重复。
