# 00_init/ — ECOS 完整数据库初始化脚本（结构 + 系统初始配置）

> 状态单源。本册由**真实库 `sys_man` 反向导出**后清洗装配，非人工编造。
> 导出基线 = 2026-11-17 阶段3 清理（V349）**提交之后**的库结构。
> 依据：《数据库访问规范》v1.4 ST07 八 schema 封闭枚举、IR02（Flyway 禁用，人工 psql）、
> MC01/MC02（主键 VARCHAR(36) 应用侧 UUID）、MC03（禁 `ON CONFLICT`/`RETURNING`/`::` cast）。

## 一、执行顺序（唯一权威顺序，禁跳步）

| 步 | 文件 | 内容 | CREATE TABLE |
|:--|:--|:--|--:|
| 0 | `00_create_schemas.sql` | ST07 八 schema + `vector`/`pg_trgm` 扩展 + 收口自检 | 0 |
| 1 | `01_ecos_control.sql` | 主控制域：IAM/配置/字典/审计/安全策略 + workspace 场景层 + sysman/security/runtime/service 层 | 123 |
| 2 | `02_ecos_data.sql` | 土 D · data-engine | 36 |
| 3 | `03_ecos_ontology.sql` | 金 I · ontology-engine | 38 |
| 4 | `04_ecos_knowledge.sql` | 水 K · kb-engine | 33 |
| 5 | `05_ecos_ai.sql` | 火 W · ai-engine | 61 |
| 6 | `06_ecos_cognitive.sql` | 木 C · cognitive-engine | 25 |
| 7 | `07_ecos_infra.sql` | 横切底座：outbox/运行时任务/监控 | 4 |
| 8 | `08_ecos_dw.sql` | 业务域五层数据湖 | 20 |
| 9 | `11_public_pending.sql` | public 挂起待裁定 7 表（见 §四） | 7 |
| 10 | `09_foreign_keys.sql` | 全库 36 条外键，**延后统一施加** | 0 |
| 11 | `10_system_seed.sql` | 系统初始配置数据：795 行 INSERT / 27 张表 | 0 |

合计 **347 张表**（345 张父表/独立表 + 2 张 DEFAULT 分区）。

**为什么 FK 在第 10 步而不是内联**：36 条外键里存在 `ecos_control → ecos_ontology` 等跨 schema 引用，
建表阶段无法拓扑排序，内联会因文件顺序失败。故全部下沉到 `09_foreign_keys.sql`。
**为什么种子在最后**：种子的 INSERT 依赖 09 建立的外键。
**为什么 `11_public_pending.sql` 编号在 09 之后却要先跑**：编号表达「归属待定」的语义排序，
执行顺序以本表为准（它是建表册，必须在 FK 之前）。

## 二、运行方式

```bash
# 新库（空库）
docker exec -i ecos-postgres psql -U postgres -d postgres -c "CREATE DATABASE ecos_new"

# 结构：00 → 01~08 → 11 → 09（逐册 ON_ERROR_STOP，任一册失败即停）
for f in 00_create_schemas 01_ecos_control 02_ecos_data 03_ecos_ontology 04_ecos_knowledge \
         05_ecos_ai 06_ecos_cognitive 07_ecos_infra 08_ecos_dw 11_public_pending 09_foreign_keys; do
  docker exec -i ecos-postgres psql -U postgres -d ecos_new -v ON_ERROR_STOP=1 -q < "$f.sql" || break
done

# 系统初始配置：默认 dry-run（事务回滚），确认无误后追加 -v ecos_commit=1 实跑
docker exec -i ecos-postgres psql -U postgres -d ecos_new -v ON_ERROR_STOP=1 -q < 10_system_seed.sql
docker exec -i ecos-postgres psql -U postgres -d ecos_new -v ON_ERROR_STOP=1 -v ecos_commit=1 -q < 10_system_seed.sql
```

结构册**刻意不含 `IF NOT EXISTS`**：结构不符即报错（fail-loud，禁静默跳过）。
`00_create_schemas.sql` 与 `10_system_seed.sql` 幂等，可重复执行。

## 三、验证证据（2026-11-17 实跑，非推断）

在临时库 `ecos_init_verify4` 从零跑完整链路，**11 册结构 + 种子全部 EXIT=0**，
再与清理后的真实库 `sys_man` 同口径对账：

| 对账项 | 实库 sys_man | 新库 ecos_init_verify4 | 结论 |
|:--|--:|--:|:--|
| 父表/独立表清单 | 345 | 345 | `diff` 零差异 |
| 逐表列数（345 行明细） | 345 | 345 | `diff` 零差异 |
| 主键约束（扣运行时分区子表） | 344 | 344 | 相等 |
| 索引（扣运行时分区子表） | 1012 | 1012 | 相等 |
| UNIQUE 约束 | 29 | 29 | 相等 |
| CHECK 约束 | 52 | 52 | 相等 |
| 视图/物化视图 | 3 | 3 | 相等 |
| 外键 | 36 | 36 | 相等 |

种子自检 NOTICE（实跑输出）：
`td_user=17 loginable=5 admin_super=1 td_role_permission=135 sys_config=178`。

> 对账口径说明：实库侧扣除「父表名不以 `_default` 结尾」的分区子表（60 张按月日期子表，
> 运行时创建，初始化不建）；新库侧用同一条件扣除，两侧同口径。
> 未扣子表时实库 PK=406 / 索引=1185，差额 62/173 全部归因到子表及其继承索引，已逐项核实。

### 门禁复跑（2026-11-18，新增 SQL 进入被扫描树之后）

`SecretScanGateTest` 的扫描面是 **`docs/50-db_script` 全树**（不只 DDL 单源），
本目录与 `../04-demo-data/` 的新增 `.sql` 都落在里面，且种子册含真实口令哈希 ⇒ 必须实跑确认不打红。

| 门禁 | 实跑结果 |
|---|---|
| `SecretScanGateTest` | **Tests run: 2, Failures: 0, Errors: 0** |
| `SourceNotIgnoredGateTest` | **Tests run: 4, Failures: 0, Errors: 0** |
| 合计 | **6/6 绿，BUILD SUCCESS**（`mvn -pl gateway test -Dtest=SecretScanGateTest,SourceNotIgnoredGateTest`） |

配套自查：24 个新增文件按该门 5 条模式逐文件复扫 = **0 命中**（种子册存的是哈希不是明文口令；
`minioadmin`/`neo4j123` 不在该门模式表内，属 compose 公开开发默认值，已在 §五 登记）；
3 个 `.md` 属该门文档豁免；`git check-ignore` 对新增文件 **EXIT=1（未被忽略）**。
跑测前后 `AI_ENGINE_API_CONTRACT.md` md5 一致（未被门禁重写）。

### 门禁复跑续（2026-11-18）：public 回归门 + 迁移 lint

| 门禁 | 实跑结果 |
|---|---|
| `PublicSqlRegressionTest`（`gateway.arch` 与 `gateway.f0214` 两个同名类） | **Tests run: 6, Failures: 0, Errors: 0, Skipped: 2 / BUILD SUCCESS**。2 个 skip 是**既有 `@Disabled`**（`ecos-sql/postgresql/public.sql` 不存在，W57 blocker，见各类第 131 / 94 行），非本批引入 ⇒ 实跑 4 条全绿 |
| `CognitiveObjectCatalogTest` + `QueryHistoryPersistenceTest` + `DataAccessArchTest`（5 模块一次跑） | **合计 16 run / 0 Failures / 0 Errors / 0 Skipped / BUILD SUCCESS**：data 2、ai 2、cognitive 4（catalog 2 + arch 2）、kb 2、workspace 6（queryHistory 4 + arch 2） |
| 契约册未被重写 | `AI_ENGINE_API_CONTRACT.md` md5 跑前跑后 **OK** |
| `db-migration-lint.ps1` | **FAIL: 1 项（IR03）**，与登记基线「0 FAIL / 8 WARN」不符 —— 逐项归因见下，**不是未授权违规** |

**IR03 FAIL 归因：门禁豁免名录滞后，不是本批越线**

按脚本同一正则（`^\s*(DROP TABLE|DROP COLUMN|ALTER TABLE…DROP)`，剥 `--` 行）复扫单源，命中分解：

| 文件 | 命中 | 授权依据 |
|---|--:|---|
| `V349__phase3_retire_superseded_shells.sql` | 39 | 本批阶段3，用户裁决「先做无争议面」后实跑 |
| `V315__ddl_code_schema_alignment.sql` | 4 | **既有批次**，非本批引入 |
| `V323`/`V324`/`V325__drop_*_version_history_table.sql` | 各 1 | **铁律 v2.0 §3.1 明列例外**：设计资产历史版本表可 DROP |
| `V126__drop_ecos_ontology_ghost_tables.sql` | 8 | 脚本已豁免（R9 授权） |

脚本的 IR03 豁免名录**只认 `V126`**（`_win_tasks/db-migration-lint.ps1:270`），既不认铁律 §3.1 的版本表例外，
也不认后续获批的清理批 ⇒ 基线滞后。按 `_win_tasks/` **只增不改**红线，扩豁免名录**需用户批准**，本批不擅自改脚本。
（该脚本另有已登记的 D-15/D-16 假绿缺陷，读其 PASS 同样须打折。）

**计划「阶段2 验证⑤」口径订正：按字面不可达**

计划写「`db-migration-lint.ps1` 对 `00_init/` 零 FAIL」。实查该脚本扫描根（`:62-72`）= `database` /
`gateway/src/main/resources/db/migration` / `engine` / `services` / `runtime` / `workspace` /
`docs/50-db_script/02-flyway-migration`，**不含 `00_init/` 与 `04-demo-data/`**。
⇒ 本目录从未进入该 lint 扫描面，「对 00_init 零 FAIL」无从成立，**不得据此宣称已验**。
本目录的合规证据改以：逐条比对《数据库访问规范》+ `SecretScanGateTest`（扫 `docs/50-db_script` **全树**，含本目录）实跑 6/6 绿为准。
已知偏差（360 处 `::` cast 等）如实登记在 §四，不假装合规。

### 阶段1 验证③ 活体补验（2026-11-18）：计划写的端点路径不存在，按真路径验

此前该项只有单测证据（`CognitiveObjectCatalogTest`），**缺计划明写的「启后端 + GET 端点」活体腿**，现补。

**计划端点路径有误**：原文 `GET /api/v1/cognitive/objects/catalog` 实跑 **400 `ECOS-COG-400P`
「未登记的 C 层对象名 catalog」** —— 该 URL 被 `/objects/{objectName}` 吃掉，`catalog` 被当成对象名。
真路径 = `CognitiveApiPaths.COGNITIVE + OBJECT_CATALOG_REL` = **`GET /api/v1/cognitive/object-catalog`**
（连字符单段，不是 `objects` 的子路径）。实跑 **200 / code=0 / items=11**。

11 项 = **6 PHYSICAL**（出 schema 限定载体）+ **5 REFERENCE**（出引用表达式、不落 cognitive 表，
故不做表存在性核验）。6 个 PHYSICAL carrier 逐个 `to_regclass` + `count(*)` 实查：

| cClass | carrier | 表在位 | 行数 |
|---|---|:--|--:|
| evidence | `ecos_cognitive.ecos_cognitive_evidence` | ✅ | 0 |
| hypothesis | `ecos_cognitive.ecos_cognitive_hypothesis` | ✅ | 0 |
| belief | `ecos_cognitive.ecos_cognitive_belief` | ✅ | 0 |
| prediction | `ecos_dw.ecos_application_forecast_run` | ✅ | 0 |
| scenario | `ecos_cognitive.ecos_world_scenarios` | ✅ | 0 |
| decision_basis | `ecos_control.ecos_decision` | ✅ | 0 |

⇒ **计划阶段1 验证③「carrier 全部指向真实存在的表」成立**（6/6 在位）。

**本批两处 carrier 改动已活体生效**（运行中的 gateway 已加载改后制品，响应出的就是新值）：
`SCENARIO` `public.ecos_world_scenarios` → `ecos_cognitive.ecos_world_scenarios`；
`DECISION_BASIS` `public.ecos_decision` → `ecos_control.ecos_decision`。

**反证「改的必要」**：两个旧 `public` 目标实查均 **MISSING**（V349 已清退）⇒ 不改则 catalog
会对外宣告两张已不存在的表。`SCENARIO` 保留的别名 `ecos_cognitive.ecos_wm_scenario` 亦在位。

> 口径边界：6 张载体表当前**全 0 行**，真实数据在 control 侧平行副本 —— 属裁定册的 SPLIT 面
> （`../03-baseline/dup-family-adjudication.md` #4/#5/#6/#36），**不在本项验收口径内**。
> 本项只验「指向真实存在的表」，不验「表里有数据」，两者不得混为一谈。
> 另：首次核验误用 `count(*) FROM pg_class` 当行数（恒得 1），已改为逐表真 `count(*)` 订正。

### 阶段1 验证④ 查询历史落库（2026-11-18）：落库面已证，活体 HTTP 腿被「过期制品」挡住

计划原文「开 `query-history.persistent=true`，写入落 `ecos_control`，`public` 该表为空」。
分两腿报，**不合并成一句「已验」**。

**开关无需改动**：`gateway/application.yml:75-77` 已是
`ecos.workspace.query-history.persistent: ${ECOS_WS_QUERY_HISTORY_PERSISTENT:true}`（D95 用户裁决），
全仓 `grep control-schema` 对 yml/properties **零命中** ⇒ `ecos.db.control-schema` 取代码默认 `ecos_control`。

**落库腿（已证，真库真写）**：用**当前** fat jar 内的同一批 class + 真库 `sys_man`，
在隔离 Spring 上下文里装配 `JdbcQueryHistoryRepository` + `QueryHistoryService` 实跑：

| 项 | 实测 |
|---|---|
| `svc.getHistory(3)` | READ OK |
| `svc.save(...)` | SAVE OK id=`178088a5-2f12-45f4-8ea2-ec47669f1371` |
| `ecos_control.ecos_scenario_query_history` 行数 | **18 → 19**（+1 即本次写入） |
| 落库行 | `subject=anonymous / sql_digest=4bc46668d68788c7 / row_count=0` |
| `to_regclass('public.ecos_scenario_query_history')` | **NULL**（V349 已清退） |

> 该行是本次核验留下的探针残留（`is_deleted=0`），如实登记，不冒充业务数据。

**活体 HTTP 腿（未成立，归因已证）**：三条腿全红，且**不是代码缺陷，是运行中的制品过期**。

| 请求 | 实测 |
|---|---|
| `POST /api/query/objectql`（`entity=ENT_ORG` / 不存在的 `ZZZ_NOT_EXIST` 各一次） | HTTP **200** + `{"code":-1,"message":"查询执行失败"}` |
| `GET /api/query/history?limit=1` | HTTP **500** `INTERNAL` |
| `DELETE /api/query/history/{不存在的id}` | HTTP **500** `INTERNAL`（本该 404） |
| 直连 `:18090` 同路径（带 `X-ECOS-USER`/`X-ECOS-TENANT`） | HTTP **500**，与经网关同形 |

**根因链（逐环实证，非推断）**：
1. `route-manifest.json:24` 的 `/api/query` 是 **`"route":"service"`** ⇒ 网关**转发到 workspace-service :18090**，
   网关内嵌的那份（正确的）`ObjectQLController`/`QueryHistoryController` **根本不承流**。
   实测佐证：`/actuator/mappings` 里 `/api/query/**` 落在转发处理器的 pattern 组内。
2. :18090 的 JVM（PID 23148）**启动于 2026-11-17 13:30:20**，其 jar 内嵌
   `workspace-impl` 的 `JdbcQueryHistoryRepository.class` 常量池里是**字面量
   `public.ecos_scenario_query_history`**，连 `ecos.db.control-schema` 占位符都没有。
3. 该 public 表已被 V345 迁走、V349 清退（上表 `to_regclass` = NULL）⇒ 读写全部
   `relation does not exist`。源码侧已于 **19:16** 改为 schema 注入、**19:20** 安装到 `.m2`，
   但 :18090 未重启 ⇒ 跑的是改前制品。
4. 网关侧制品**是新的**：fat jar 内 `QueryHistoryService` / `JdbcQueryHistoryRepository` /
   `ObjectQLController` 三个 class 与 `workspace-impl/target/classes` **逐字节 `cmp` 相同**；
   `/actuator/beans` 显示 `jdbcQueryHistoryRepository` 在位、依赖 `tenantAwareJdbcTemplate`。

**四条先行假设已被反证推翻（留档防重复走弯路）**：

| 假设 | 反证实验 | 结果 |
|---|---|---|
| 网关 fat jar 内嵌 workspace-impl 过期 | 三 class 与 target/classes `cmp` | **IDENTICAL** ⇒ 推翻 |
| `@Repository` 被 `PersistenceExceptionTranslationPostProcessor` 代理但容器无 `PersistenceExceptionTranslator` ⇒ 每次调用抛异常 | 隔离上下文注册该后置处理器、刻意不放 translator | READ OK ⇒ 推翻 |
| `TenantAwareJdbcTemplate` 改写 SQL 致失败 | 隔离上下文换用该类 | READ OK ⇒ 推翻 |
| CGLIB 代理（`proxyTargetClass=true`）致失败 | 隔离上下文开 `setProxyTargetClass(true)`，代理类名与网关完全一致 | READ OK ⇒ 推翻 |

旁证：psql 与 jshell 各用**应用同一条 JDBC URL**（含 `currentSchema=...&stringtype=unspecified`）
跑 repository 的原样 SELECT，均 OK；库内只有 `postgres` 一个可登录角色、该表 ACL 为 owner-only
⇒ 权限面亦排除。同期 `GET /api/v1/workspace/scenarios` **200 带数据** ⇒ 不是全局 DB 故障。

⇒ **归因判定：落库面成立；活体 HTTP 面当时不成立，阻塞原因 = :18090 运行过期制品**（非源码缺陷）。

**收口（2026-11-18 07:34，用户授权「重建+重启 :18090」后实跑）**：

重建踩到一个坑并已解：`mvn -pl :workspace-impl,:workspace-service install` 报
`BUILD SUCCESS` + `Replacing main artifact … with repackaged archive`，但**磁盘上的 fat jar
逐字节没变**（size/mtime 仍是 13:30，内嵌 class 仍是 `public.` 字面量）——
`Nothing to compile - all classes are up to date` ⇒ jar 插件复用旧包 ⇒ repackage 对「已是 Boot 包」的
源包空转。**删掉 `*.jar` + `*.jar.original` 后加 `-Djar.forceCreation=true` 重建**才真正出包
（`Building jar:` 行出现，size 143966819→143960935）。
⇒ 教训：**「BUILD SUCCESS + Replacing main artifact」不等于制品真的换了**，必须验内嵌 class 常量。

| 验收项 | 重启前 | 重启后（实测） |
|---|---|---|
| 新 jar 内嵌 repository 常量 | `public.ecos_scenario_query_history` | **`ecos.db.control-schema:ecos_control`**，`public.` 字面量消失 |
| `GET /api/query/history?limit=3` | 500 INTERNAL | **200 / code=0 / 返回真实历史行** |
| `POST /api/query/objectql`（`ENT_ORG`） | 200 + `code:-1「查询执行失败」` | **200 / code=0 / success=true** |
| 表行数（活体写入腿） | 19（仅隔离探针写入） | **19 → 20**，新行 `subject=u-admin / digest=5b83d05c135d92ce / 07:34:24.086154`，与 POST 应答 timestamp 同刻 |
| `DELETE /api/query/history/{不存在id}` | 500 INTERNAL | **404 `NOT_FOUND`「记录不存在」**（状态行保真） |
| `to_regclass('public.ecos_scenario_query_history')` | NULL | **NULL**（未回流） |

⇒ **计划阶段1 验证④ 成立**：开关已开、活体写入落 `ecos_control`、`public` 该表为空。
本节保留全部反证过程与「过期制品」归因，供同类故障复用；§四 第 9 项的折叠缺陷**依然存在**
（本次只是让历史表可写从而不再触发它，代码语义未改）。

## 四、已知偏差与挂起面（刻意保留，不假装合规）

1. **`::` cast 共 360 处（01~08 册）** —— 反向导出保留形态，与 MC03「禁 `::` cast」字面冲突。
   这些 cast 出现在 DEFAULT 表达式与视图定义里（如 `'public'::text`），是 PG 目录的规范输出，
   删除会改变语义。**登记为偏差，不在本批整改**；`10_system_seed.sql` 自身零 cast，符合 MC03。
2. **public 剩 7 张独有表（`11_public_pending.sql`）** —— `ecos_decision_source_ref` /
   `ecos_fc_action_ext` / `ecos_knowledge_document` / `ecos_ontology_domain` /
   `ecos_runtime_llm_usage` / `ecos_scenario_active_mind` / `ecos_scenario_mind_ref`。
   单源脚本即在 public 建、main 代码零真实 SQL 引用、**无归属信号** ⇒ 归属待用户裁定。
   既不臆造 schema 搬走，也不丢弃唯一副本：原样保留 public 前缀独立成册。
   裁定后按 V345/V346 同口径归位并删除本册。
3. **60 张运行时分区子表不建** —— 只保父表 + DEFAULT 分区（`ecos_ai.agent_cost_default`、
   `ecos_infra.outbox_event_default`）。按月日期子表由运行时创建。
4. **`ecos_dq` schema 不复建** —— 11 个 `dq_*` 重名表族属登记在案的漂移
   （V111 建 `ecos_dq`，V339/V342 建 `ecos_data`），待逐族裁定，不在 ST07 枚举内。
5. **`ecos_sysman` / `ecos_security` 不复建** —— 全 0 行、不在任何 `currentSchema`，
   已由 V349 在实库 DROP（含 schema 本身）。
6. **重名表族仍在 —— 计数必须带口径（2026-11-18 实查订正）**
   阶段3 只清「无争议面」。引擎侧 0 行副本**刻意不清退**：它们可能是逐族裁定的迁移目标，不是垃圾。
   同一库三种口径数出三个数，此前只写「26」而未声明口径，**会漏掉 10 个前缀变体族**：

   | 口径 | 定义 | 实查族数 |
   |---|---|--:|
   | A | `relname` 完全相同、跨 schema（不归一前缀） | **26** |
   | B | 去 `ecos_` 前缀后同名、跨 schema | **29** |
   | C | 去 `ecos_` 前缀后同名、**含同 schema 内前缀变体** | **36** |

   逐族裁定应按 **C=36** 取全集（A 会漏 `agent_registry`/`dq_rule`/`kb_extract_audit`/`kg_sync_log`/
   `kb_ontology_snapshot` 等**同 schema 内**的裸名 vs `ecos_` 前缀分叉，这些恰是写方最容易读错的一类）。
   复现命令：`pg_class ⋈ pg_namespace`，`relkind='r' AND relispartition=false`，9 schema 白名单，
   `GROUP BY regexp_replace(relname,'^ecos_','') HAVING count(*)>1`。
   逐族 DDL 意图 + 写方实查见 `../03-baseline/dup-family-adjudication.md`。
7. **计划「移除 `DwSchemaQualifier` 已 DROP schema」项 —— 反证后不予执行（2026-11-18）**
   计划「关键文件清单·将修改」第 3 项与阶段3 注记要求把 `DwSchemaQualifier.java:38` 的
   `ecos_dq`/`ecos_sysman`/`ecos_security` 从「白名单」移除。**该前提倒置，执行会削弱 MC04 守卫**：
   - `CONTROL_SCHEMAS` 是 fail-closed 的**控制域 deny-list**，不是白名单：
     `classifyDomain()`（`:96-101`）对**不在**集合内的 schema **默认返回 `BUSINESS`**；
   - `OlapEngineResolver.guard()`（`:96`）只拒 `CONTROL → OLAP`。故「移除」= 改判 BUSINESS = **放开**守卫。
     ultimate 档配了 Doris 时 `carrierRef="ecos_sysman.x"` + `DORIS` + `APPLICATION` 会被放行，
     并登记成 `OLAP_TABLE` 载体（`DwWriteChannelService:140/148`）；OLAP 是独立存储，
     「schema 在 PG 不存在」拦不住它。
   - **变异取证（实跑）**：按平面移除两名 → 新门 `ControlSchemaDenyListRatchetTest` 红 2/3，
     行为腿报 `Expected IllegalStateException to be thrown, but nothing was thrown`（写入真被放行），
     常量腿报 `expected: <CONTROL> but was: <BUSINESS>` → 逐字节复原（`git diff` 净空）→ 复跑绿。
   - 旁证：`GatewayNoDomainTableArchTest:57` 禁域正则至今仍列 `ecos_sysman`；`ecos_dq` 实查**仍在库中**；
     knownLegacy 口径是「只停写不迁移」，四个服务残留 schema 必须留名。
   - ⇒ **deny-list 里留着已消失的名字零成本、纯保护；删掉才是净损失。** 本批改为加门钉住该语义，
     生产代码 `DwSchemaQualifier.java` **未改**（`git diff` 净空）。
8. **两处门禁随本批订正（均已实跑复验）**
   - `DdlComplianceLintTest.ir03NoDropStatements`（data-engine-impl）此前因 **V349 自身**红：
     该门豁免名单按文件名冻结，V349 未登记。现按 V315 同口径补登（脚本头部已记明三项前置：
     逐项授权 / 备份 `Temp/pre_phase3_backup.sql` 9798 行 / 默认 ROLLBACK 空跑后才实跑）。
     **判据未失明的证据**：登记前本用例即红并精确点名 V349，名单仍按文件名精确匹配，
     任何其它新增 DROP 文件依旧 FAIL。
   - `RouteManifestParityTest:43` 的规格册锚点断链：`详细设计-02` 已随归档批（commit `1c5b2840`）
     移入 `docs/90-归档/30-设计/详细设计-分册/`，basename 未变。门禁随件改指归档位，不复制回 `30-设计`。
     同批排查另两处 `docs/30-设计` 引用（`DatanetEndpointParityTest:38`、
     `CognitiveContractSingleSourceTest:31`）均为**声明后从未读取的死常量**，不构成断链，登记不改。
   - **全模块实跑**：`mvn -o -pl :data-engine-impl test` → `Tests run: 595, Failures: 0, Errors: 0,
     Skipped: 1` / `BUILD SUCCESS`。唯一跳过项 = `IncompleteContextUnknownScoreTest` 的
     `@Disabled("W58 gap")` 占位，属既有刻意跳过，与本批无关。

9. **新登记缺陷：ObjectQL 把「历史写入失败」折叠成「查询执行失败」（HTTP 200 + code=-1）**
   `ObjectQLController:121` 的 `historyService.save(...)` 在 `try` 内、且在结果集已算好之后。
   历史表不可写时异常落进 `:132` 的泛 `catch (Exception)` ⇒ 应答
   `HTTP 200 {"code":-1,"message":"查询执行失败"}`，**已经查成功的结果集被整份丢弃**。
   本次活体实测即此形态（根因见 §三 阶段1 验证④）。属已登记的「HTTP 状态行保真 / 端点折叠」族：
   把「旁路持久化故障」冒充成「主功能失败」，调用方会去查 DSL 而不是查历史表。
   **本批只登记不改**：修法涉及响应语义（主结果照返 + 旁路故障单独告知），属 API 行为变更，须用户裁决。

## 五、口令与凭据

- `td_user.password` 保留**真实哈希**（PBKDF2 / BCrypt 两种存量形态），保证新库可直接登录。
  V347 起登录只读 `td_user`，平行误会表 `users` 已由 V349 清退。
- 开发环境账号/密码/端点的唯一权威 = `.trae/rules/开发环境登录凭据.md`（🔴 红线文件，
  **任何 AI Agent 不得修改**）。本册不复制明文口令。
- `sys_config` 中 `dw.storage.minio.access_key`/`secret_key` = `minioadmin`、
  `ontology.graph.neo4j.password` = `neo4j123` —— 与 `ecos-docker/docker-compose.yml`
  的公开开发默认值完全一致，非新增泄露。**生产部署必须改这三项 + 全部口令哈希。**
- 部分演示用户口令为 `NOLOGIN:seed-entmgmt-v2` 哨兵值，刻意不可登录。

### 口令面实证（2026-11-18，离线复算，非仅行数断言）

「新库可直接登录」不能只靠 `td_user` 非空来断言。已按**部署端同一算法**复算本册哈希：
`AuthController.matchesPbkdf2` = `PBKDF2WithHmacSHA256`，迭代数取哈希第 2 段、密钥长度取哈希第 4 段解码后字节数
（实测 iter=65536 / salt 16 B / hash 32 B）。

| 项 | 实测 |
|---|---|
| 本册 `td_user` 行数 | 17 |
| 形态分布 | `pbkdf2` 3 + `bcrypt` 2 + `NOLOGIN:` 哨兵 12 |
| 可登录面（非哨兵非 NULL） | **5** —— 与本册自检 NOTICE `loginable=5` 一致 |
| 2 个 BCrypt 哈希形态 | 均 `$2a$10$` 前缀、长度 60，合法（`passwordEncoder.matches` 可消费） |
| **admin 口令复算** | 以红线凭据册所载 admin 口令复算 = **MATCH** ✓ |
| 反证候选（防恒真） | 大小写变体 / 短口令 / 任意错误口令 **3 个全 MISMATCH** ⇒ 该比对确有鉴别力 |

> 明文口令不写入本目录任何文件，唯一权威仍是 `.trae/rules/开发环境登录凭据.md`。

## 六、范围边界

**含**：系统运转必需的配置/注册表 —— IAM、租户、安全策略、配置字典、各引擎平台级注册表（27 表 / 795 行）。
**不含**：
- 演示业务数据 → `../04-demo-data/`（58 表 / 1014 行，三件套：`demo_seed.sql` 灌入 / `demo_cleanup.sql` 清空 / `demo_verify.sql` 只读对账）。
  与本册**表级互斥、零重叠**，故清空演示面不会动到 IAM/配置/字典/策略。演示册有 2 条 FK 指向本册的 `ecos_ontology.ecos_domain`，**必须在本册之后跑**。
- 运行时噪声（审计日志/执行流水/任务实例/Agent 消息等 21 表 / 50723 行），刻意不导出。

> `td_user` 本册灌 **17** 行，实库 `sys_man` 有 18 行：多的一行是 `tdv01`（登录迁移验证账号，`status=DELETED`、0 角色隶属，
> 建账 2026-11-17 19:37 晚于本册导出 19:18），属测试残留，刻意不进系统种子。
> 下游对账基线应取**本册 INSERT 语句数**而非实库 `count(*)`，否则新库会假报漂移。

## 七、与历史迁移脚本的关系

`../02-flyway-migration/gateway/` 下 298 枚 `V*.sql` 是**演进史**（最大 V349，下一号 V350）；
本册是**目标态快照**。两者不是双轨：新库只跑本册，存量库继续按 V 号顺延增量。
历史脚本归档属独立批次（先建新、后归档，用户裁决）。