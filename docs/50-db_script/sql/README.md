# SQL 资产归集区（canonical-ddl 落位）

> 依据：[数据层规范化治理方案-2026-10-05.md](../数据层规范化治理方案-2026-10-05.md) §三 / §2.1
> 落位规则：`sql/` 下按「来源域」原子归集，非按业务属主（业务族切文件 = 后续 P5+ 批次，见 §五 归集细则）。

## 目录

| 目录 | 来源 | 数量 | 说明 |
|:--|:--|:--:|:--|
| `sysman-boot-migration/` | `ecos_backend/services/sysman/impl/sysman-boot/.../db/migration/` | 16 | V 号 14 个与 gateway 同名不同内容 → 以 gateway 为默认真源（见 §三.2），此目录保留作落库证据 |
| `sysman-boot-database/` | `ecos_backend/services/sysman/impl/sysman-boot/database/` | 3 | V1.4/V1.5 一次性 bootstrap + seed |
| `engine-ai/` | `ecos_backend/engine/ai-engine/ai-engine-impl/.../db/migration/` | 1 | 第四处散 V4 收编 |
| `engine-ontology/` | `ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/resources/db/` | 1 | 单表散置收编 |
| `database/` | `ecos_backend/database/` | 3 | V150__schema_unify / V150__rollback（一次性）+ seed（unchanged 兼容视图） |
| `scripts/` | `ecos_backend/scripts/*.sql + scripts/migration/*.sql` | 7 | 运维脚本，含 `ecos_demo_repair_escape_172.sql` 13.3MB |
| `8split/{postgresql,mysql,oracle}/` | `ecos-sql/{postgresql,mysql,oracle}/` | 25 | 8 拆分 DDL 快照（knownLegacy 参考，mysql/oracle 标「示意态」） |
| `migration-onetime/` | `ecos-sql/migration/` | 1 | `12_to_8_schema.sql` 一次性 SET SCHEMA |

## 未归集（待续）

- `ecos_backend/gateway/src/main/resources/db/migration/` 197 文件：受 ~25 gateway/data/kb/workspace/ontology 测试硬编码路径强耦合（本批不动，独立批次处理）。属 canonical-ddl 主真源，本 README 落位后独立批次将按业务族拆到 `10-control-main/ 20-engine-{data,ontology,kb,ai,cognitive}/ 30-business-dw/`。
- `ecos-docker/postgres-extensions/vector--0.8.1.sql`：compose `docker-compose.yml:25-27` + `docker-compose.base.yml:31-33` 直接卷挂载，物理归位需同时改 compose；独立批次处理。
- `docs/40-实现/ops/legacy-11/rollback-ddl-pmo58.sql`：R9 只增不改，保留原位作 docs 归档证据。

## 权威声明

本目录（含子目录）是 ECOS 数据层「非 gateway 侧」SQL DDL/seed/一次性脚本的**唯一落位**（G1 归并后）。任何新增 SQL 文件必须落入此目录或 `ecos_backend/gateway/.../db/migration/`（当前真源，收编目标）之一，二者之外放置 = ST10 违规。
