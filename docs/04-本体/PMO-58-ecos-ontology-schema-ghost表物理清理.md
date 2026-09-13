# PMO-58: ecos_ontology schema 8 张 ghost 表物理清理

> **架构铁律**: 必须遵循 [ECOS架构铁律](../00-架构/ARCHITECTURE-RULES.md) —— 铁律 §3.1 "Schema 只加不删" 的**例外专项**（删除需 PMO 指令授权 + commit hash 留痕；文档目录规范与 DB schema 同款规则）
> 来源: 肖国荣（本体工作台 20 任务遗留项 #6，Wave D T20 调查结论）
> 日期: 2026-09-13
> 责任人: fullstack-implementer
> 铁律: ① DROP 是纯破坏性操作——执行前必须逐表再验 0 行 ② 删表留 commit hash + 回滚 DDL ③ 不动 public schema 任何表（canonical 全在 public）

## §背景

本体工作台 Wave D T20（commit `c58fa03`）经 sys_man 库直查坐实"双套并存"真相：

| 事实 | 值 |
|:--|:--|
| `ecos_ontology` schema 侧（V47 `SET SCHEMA` 搬迁产物） | **8 张 0 行 ghost 表**：`entity / property / relationship / action / rule / version`（V47 建）+ `ecos_domain / ecos_business_glossary`（V47 搬） |
| `public` 侧（canonical，有真实数据） | 同名表 `entity=15 行 / ontology 主表=8 / rule=1` 等 |
| 代码引用 | `git grep "ecos_ontology\." 全后端 = 0 命中`（无 schema 前缀 SQL，session search_path `"$user", public` 全命中 public） |
| 已做防护 | V122（commit `ea7ec9b` 修订版）已逐表 `COMMENT ON TABLE` 登记 ghost 身份 + public 侧幂等兜底 |
| 业务影响 | **零**（ghost 表 0 行、0 引用，纯沉默） |

**本 PMO 目标**：经 PMO 授权后物理 DROP 8 张 ghost 表，消除"双套 schema"命名歧义（T20 登记后状态：已标注、未 DROP）。

## §禁止清单

1. **DROP 仅限 `ecos_ontology` schema 那 8 张**——public schema 任何表/视图/列一律不碰（铁律 3.1）
2. 执行前逐表 `SELECT count(*)` 复验 = 0（防 T4 之后有新写入）
3. 不同步 DROP schema 本身？——DROP 8 表后 `ecos_ontology` schema 可一并 DROP（若其下无其它对象），先 `pg_class` 全列 schema 内容再定
4. Flyway 已禁用（铁律 3.1）：DROP 以独立 SQL 脚本执行 + `V123__drop_ecos_ontology_ghost_tables.sql` 形式**留档入 migration 目录**（保证全新库/迁移库 V47 不再产生 ghost；运行库直接执行）——⚠️ 注意 Flyway 虽禁用，但 V 文件作为 git 追溯锚点保留
5. V47 源脚本（`SET SCHEMA IF EXISTS` 搬表层）**不改**（历史不可变，只在其后追加 V123）
6. 不动 PMO-50/51/52 残留

## §Task（≤5）

| Task | 文件/路径 | 操作 | 验收 |
|:--|------|------|------|
| T1 | 8 表行数终验（执行日前） | `psql sys_man` 逐表 `count(*)` | 8/8 = 0，记录快照 |
| T2 | 新库防复发 | `V123__drop_ecos_ontology_ghost_tables.sql`：`DROP TABLE IF EXISTS ecos_ontology.xxx` ×8（+ 视 T1 延伸核查结果决定 `DROP SCHEMA IF EXISTS ecos_ontology CASCADE` 是否纳入，默认不 CASCADE） | 全新库 flyway-less 手工全量 DDL 顺序跑（V1→V123）零错 |
| T3 | 运行库执行 | 对当前 sys_man 库执行 T2 脚本 + `ON_ERROR_STOP=1` | exit 0；`pg_class` 复查 schema 内 0 表 |
| T4 | 回滚留档 | 8 表原 DDL（V47 对应建表语句 + public 侧数据 `INSERT INTO ... SELECT` 快照——本 PMO 前提下数据为空，快照为空集但语句留档）落 `docs/11-运维/rollback-ddl-pmo58.sql` | 文件存在 + 审批记录 |
| T5 | 验证留痕 | 执行前后 `pg_class` 计数对比 + 本体工作台 CRUD 冒烟（建/查/删 1 个 entity，确认 public 侧正常） | 冒烟 PASS + 证据落 `docs/10-审查证据/pmo-58/` |

## §验收四步法

V1 DROP 脚本文件存在 → V2 T1 行数快照 = 0 → V3 V123 全新库顺序执行零错 → V4 运行库执行 + 本体 CRUD 冒烟（curl `POST/GET/DELETE /api/v1/ecos/ontologies/.../entities`）。

## §风险

- **纯破坏性**：若 T1 与 T3 之间有新写入（低概率，代码 0 引用），行数 ≠ 0 → **立即中止回滚**（T4 DDL 重建 + 从快照恢复）
- 全新库场景：V47 仍会建 ghost 表 → V123 随后 DROP，净效果等价；脚本顺序必须 V47 → … → V123
- schema 级 DROP 若纳入，需先确认 runner/monitor 无对 `ecos_ontology` schema 的 search_path 硬依赖（T1 延伸核查手段）

## §前置决策（需压实）

1. DROP 8 表 ✔（默认批准，待你确认）
2. `DROP SCHEMA ecos_ontology`（空壳 schema）——**默认纳入**（8 表 DROP 后 schema 必然空，空 schema 保留无意义；若 T1 延伸发现 schema 下还有 view/index/sequence 则只 DROP 表留 schema）
3. 执行窗口：建议合入 dev 前或紧随其后的维护窗口，避免与 buszhi 侧并行改动撞车
