# 50-db_script — DDL 迁移脚本唯一单源

> 来源: docs 整理批（2026-11-17），用户裁决「归档非单源子目录」 | 责任人: 文档整理 Agent
> 宪法依据: `.trae/rules/架构铁律.md` **v2.2 §3.1**（2026-10-11 卷09 H-7 用户裁决订正）

## 唯一权威

**`02-flyway-migration/gateway/` 是全仓 DDL 迁移脚本的唯一单源**（2026-11-17 实扫 **293** 枚 `V*.sql`，最新 V344）。

- 新迁移脚本**只写这里**，V 号顺延（下一号以 `40-实现/DDL迁移脚本落地登记-2026-09-30.md` + gateway `MigrationVersionUniquenessTest` 钉值为准，两处同批改）。
- `ecos_backend/gateway/src/main/resources/db/migration/` 是空目录，**不写入**（历史误记落点）。
- Flyway 运行时禁用（`spring.flyway.enabled: false`），脚本**落文件不实跑**；对 dev 库实跑属授权动作，须先事务空跑验证（见 `90-归档` 外的经验教训）。
- 命名与红线遵循 `.trae/rules/数据库访问规范.md`（IR/DR/EN/ST/MC 30 条）；种子幂等写法用 `WHERE NOT EXISTS` / DELETE+INSERT，**不得** `ON CONFLICT`（MC03 + 规范 §seed 订正注）。

## ⚠ 历史漂移警示（为什么其余子目录被归档）

以下子目录已整体迁至 `../90-归档/50-db_script/`，**它们不再是权威**，引用其中任何建表都会造成漂移误判：

| 已归档子目录 | 漂移风险（实测案例） |
|:--|:--|
| `01-ddl-多方言/` | postgresql/00~08 建的是 V200 治理**之前**的旧 schema 形态，与现行 8 schema 封闭枚举不符 |
| `05-service/sysman/`（V1~V41） | 旧 V 号序列与单源 V 号**撞号**；dev 库部分表（demo_customer 等）即由此手建、从未进单源 |
| `04-engine/`、`03-seed/`、`06-docker-infra/`、`07-ops-scripts/`、`08-database-util/` | 历史手建产物；与单源并存时无法判断"哪个才是真身" |
| 根部 `V200__schema_governance.sql` | V200 **已收录进单源序列**（`02-flyway-migration/gateway/` 内同名 V200 为权威），根部这枚是历史副本 |

**实证教训（D123-2，2026-11-17）**：单源外的 V111 建 `ecos_dq.dq_*`，而 DQ DAO 读写 `ecos_data.dq_*` —— 两套并存导致"全新库建不出 DAO 要的表"误判数周。任何表结构争议，**只认单源 + 活库实查**，不认归档件。
