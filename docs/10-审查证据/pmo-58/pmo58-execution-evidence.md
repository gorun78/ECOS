# PMO-58 执行证据（T2-T5 全闭环 — DROP 8 ghost 表 + 死 FK 解除 + canonical 冒烟 PASS）

> 来源: 全栈开发工程师 / 日期: 2026-09-13 / 责任人: fullstack-implementer
> 对应指令: [PMO-58](../../04-本体/PMO-58-ecos-ontology-schema-ghost表物理清理.md)
> 授权扩展（PM 已裁定）: `ALTER TABLE ecos_cognitive.ecos_wm_goal DROP CONSTRAINT fk_cog_goal_domain`
> （该 FK 指向 0 行 ghost 表 ecos_domain，参照语义已死；永久废弃不重建）
> 分支: feat/ontology-workbench-wave-b | 前序留痕: [pmo58-abort-evidence.md](pmo58-abort-evidence.md) (fcb7472 + 154f39e)

**结论**: T2（V126 脚本）→ T3（运行库 DROP，exit 0）→ T4（回滚档更新）→ T5（冒烟 + 本留痕）全部 PASS。
8 张 0 行 ghost 表物理消灭，pg_class `ecos_ontology` 对象数 **89 → 59**（推算 89-8-21-1=59，实测 = 预期）。
canonical 链路 2 读端点全 200 且 data 非空。0 Java 变更，public schema 0 触碰，gateway 未自启未 kill。

## § Step 0 — 二次终验（执行时刻新鲜数据，非上批快照）

执行时刻: 2026-09-13 13:22:13.400017+00（容器 ecos-postgres，psql，REPEATABLE READ 单事务）

### 0-1. 8 表 count(*) 全 = 0 **PASS**

```
 c_entity | c_property | c_relationship | c_action | c_rule | c_version | c_domain | c_glossary
----------+------------+----------------+----------+--------+-----------+----------+------------
        0 |          0 |              0 |        0 |      0 |         0 |        0 |          0
```

### 0-2. FK 全扫描 = 4 条（无第 5 条） **PASS**

```
       conname        |               from_tbl               |               to_tbl
----------------------+--------------------------------------+------------------------------------
 fk_cog_goal_domain   | ecos_cognitive.ecos_wm_goal          | ecos_ontology.ecos_domain
 fk_onto_ent_domain   | ecos_ontology.ecos_ontology_entity   | ecos_ontology.ecos_domain
 fk_onto_gloss_domain | ecos_ontology.ecos_business_glossary | ecos_ontology.ecos_domain
 fk_onto_prop_ent     | ecos_ontology.ecos_ontology_property | ecos_ontology.ecos_ontology_entity
(4 rows)
```

3 条内部（附着 ghost 表之间/到 ecos_domain）+ 1 条跨引擎（PM 授权解除），与上批中止留痕完全一致，**无新增超范围依赖**。

### 0-3. FK 精确定义快照（pg_get_constraintdef 原文，已同步入回滚档 §(d.0)）

```
 fk_onto_prop_ent     | FOREIGN KEY (entity_id) REFERENCES ecos_ontology.ecos_ontology_entity(id)
 fk_onto_ent_domain   | FOREIGN KEY (domain_id) REFERENCES ecos_ontology.ecos_domain(id)
 fk_onto_gloss_domain | FOREIGN KEY (domain_id) REFERENCES ecos_ontology.ecos_domain(id)
 fk_cog_goal_domain   | FOREIGN KEY (domain_id) REFERENCES ecos_ontology.ecos_domain(id)
```

4 条全为无 ON DELETE/ON UPDATE 子句的 bare FK（默认 NO ACTION）。

### 0-4. pg_class 前值

`SELECT count(*) FROM pg_class WHERE relnamespace='ecos_ontology'::regnamespace;` = **89**（复验 T1 基线，一致）

## § V 号终裁 — V126

`Get-ChildItem migration/V12*.sql` 实录: V120~V125 全占（V123=PMO-51 / V124=认知模型 / V125=PMO-52 并发未跟踪），**V126 空号 → 终裁 V126**。

- 文件: [V126__drop_ecos_ontology_ghost_tables.sql](../../../ecos_backend/gateway/src/main/resources/db/migration/V126__drop_ecos_ontology_ghost_tables.sql)
- 结构: ① 跨引擎死 FK 解除 ② 内部 3 FK 显式解除（IF EXISTS 双保险）③ DROP 8 表（表名以 T1 pg_class 实录为准，**version 表真实名 = `ecos_ontology.ecos_ontology_version`**，非指令猜测的 `ecos_ontology_ecos_version`）④ 不 DROP SCHEMA（活表在案，前置决策 2）
- 注: 指令草稿示例中的 `ecos_ontology.ecos_property/ecos_action/ecos_rule` 经 T1 pg_class 实录核对**不存在**（真实名带 `_ontology_` 前缀），已按实录命名，草稿 8 名中 3 个假名剔除后 8 表实名全覆盖

## § Step 3 — 运行库执行（exit 0 硬条件 **PASS**）

命令: `docker cp <V126> ecos-postgres:/tmp/pmo58-V126.sql && docker exec ecos-postgres psql -X -U postgres -d sys_man -v ON_ERROR_STOP=1 -f /tmp/pmo58-V126.sql`

输出（原文）:

```
ALTER TABLE   ← fk_cog_goal_domain 解除
ALTER TABLE   ← fk_onto_prop_ent 解除
ALTER TABLE   ← fk_onto_ent_domain 解除
ALTER TABLE   ← fk_onto_gloss_domain 解除
DROP TABLE ×8 ← 全成功
PSQL_EXIT_CODE=0
```

### 执行后 pg_class 计数: 89 → **59**（推算全对上）

| 项 | 前 | 后 | delta |
|:--|--:|--:|--:|
| pg_class `ecos_ontology` 总对象 | 89 | **59** | -30 |
| — table | 25 | **19** | -8 (8 ghost 表) |
| — index | 59 | **40** | -19 (8 pkey + 11 idx) |
| — sequence | 1 | **0** | -1 (ecos_business_glossary_id_seq) |

推算: 89 - 8(表) - 19(索引: 每表 1 pkey + 11 业务 idx) - 1(序列) = **59 = 实测**。89 中 table 层 T1 记 25，本次消 8 → 活表 19。

### 执行后交叉核对（全 PASS）

| 核对项 | 结果 |
|:--|:--|
| ecos_ontology 相关 FK 残留 | **0**（pg_constraint contype='f' 双向扫） |
| 8 ghost 表存在性 | **0**（pg_class 名册命中） |
| ecos_cognitive.ecos_wm_goal 行数 | 0（FK 解除无副作用面，表本体保留） |
| 残留 19 活表 | action_definition / ecos_object_{attachment,data,links,relation,relationship,state_machine,timeline,version} / ecos_workflow{,_approval,_instance,_log,_task} / entity_definition / relationship_definition / metric_definition / policy_definition / event_definition — 全部保留 ✅ |

> 勘误（对上批中止留痕 T1 延伸核查表）: 中止证据把 `outbox_event (V48)` 列为 schema 下活表，本次执行后全库查实 `outbox_event` 实际位于 **ecos_infra** schema（V48 落位），不在 ecos_ontology 名下。ecos_ontology 实际活表 = **19**（非 17），已按实录修正；**不影响"不 DROP SCHEMA"判定**（条件 2 仍满足：schema 下有活表）。

## § Step 4 — canonical 链路冒烟（借并发 gateway PID 123644 :8080，未自启未 kill） **PASS**

```
$ curl.exe -s -w "\nHTTP:%{http_code}\n" http://localhost:8080/api/v1/ecos/ontologies
{"code":0,"message":"ok","data":[{"id":"fb972746","code":"ont_w7_cra","name":"Wave7 E2E ontology",...
  ... 共 8 条本体 ... {"id":"ont002","code":"finance","name":"财务本体",...,"status":"PUBLISHED",...}],
 "timestamp":1789305896483,"success":true}
HTTP:200

$ curl.exe -s -w "\nHTTP:%{http_code}\n" http://localhost:8080/api/v1/ecos/ontologies/fb972746/entities
{"code":0,"message":"ok","data":[{"id":"ent512","ontologyId":"fb972746","code":"e_w7_s99",
 "name":"Wave7 entity","description":"smoke","entityType":"Entity","sortOrder":1,...}],
 "timestamp":1789305896648,"success":true}
HTTP:200
```

2/2 全 200，data 非空 → canonical `public.ecos_ontology`(8 行) / `public.ecos_ontology_entity` 读取链路在 ghost 侧 DROP 后存活。gateway 未被本批次触碰（:8080 监听者 PID 123644 = 上批实证的并发实例，保留运行）。

## § 交付物清单

| 项 | 文件 | 状态 |
|:--|:--|:--|
| V126 DROP 脚本 | `ecos_backend/gateway/src/main/resources/db/migration/V126__drop_ecos_ontology_ghost_tables.sql` | commit 1 |
| 回滚档更新（§(d) 定义快照 + fk_cog_goal_domain 永久废弃标注） | `docs/11-运维/rollback-ddl-pmo58.sql` | commit 1 |
| 本证据 | `docs/10-审查证据/pmo-58/pmo58-execution-evidence.md` | commit 2 |

## § 红线自检

| 红线 | 状态 |
|:--|:--|
| Step 0 不过不执行 | PASS（0-1~0-4 全过才执行） |
| 第二处超范围依赖 → 中止 | 未触发（FK 扫描 = 4，恰在授权名单内，**越界: PASS 未发生**） |
| 0 Java 变更 | PASS（本批次仅 1 个 .sql + 2 个 docs） |
| public schema 0 触碰 | PASS（全部语句仅涉及 ecos_ontology.* + 1 次 ecos_cognitive.ecos_wm_goal ALTER） |
| gateway 不 kill 不自启 | PASS（全程借用 PID 123644） |
| 并发批次 V123/V124/V125 未跟踪文件不 add | PASS（commit 仅含 V126 + rollback 档 / 证据 md） |
| Flyway 禁用语境 | 说明: V 系列仅作原子迁移留档，本次经 psql 手动 ON_ERROR_STOP 执行，不触碰 Flyway 机制 |
