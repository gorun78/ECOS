# 50-db_script — 数据库脚本资产归档

> 生命周期分类：**50-数据库**（2026-10-01 整理，V200 治理后更新）
> 来源：原 `ecos-sql/`、`gateway/src/main/resources/db/`、`ecos_backend/database/`、`ecos_backend/scripts/` 等多处散落位置

## 目录结构

| 目录 | 内容 | 文件数 |
|------|------|--------|
| `01-ddl-多方言/` | 三方言 DDL（PostgreSQL/MySQL/Oracle）+ schema 合并脚本 | 28 |
| `02-flyway-migration/gateway/` | Flyway 迁移脚本（V1→V260，已禁用，仅历史参考）+ **V200__schema_governance.sql**（V200 彻底治理） | ~203 |
| `03-seed/` | 种子数据 | 6 |
| `04-engine/` | 引擎专属 SQL（ai-engine / ontology-engine） | 2 |
| `05-service/sysman/` | Sysman 服务迁移子集 + seed | 20 |
| `06-docker-infra/` | Docker 初始化 SQL + pgvector 扩展 | 5 |
| `07-ops-scripts/` | 运维/修复/回滚脚本 | 6 |
| `08-database-util/` | V150 schema unify + rollback | 3 |

## 核心报告

- [ECOS数据库现状盘点与交叉验证报告-2026-10-01.md](./ECOS数据库现状盘点与交叉验证报告-2026-10-01.md)
- [ECOS-Schema彻底治理方案-2026-10-01.md](./ECOS-Schema彻底治理方案-2026-10-01.md)（V200 执行方案 + 结果报告）

## V200 治理成果（2026-10-01）

| 指标 | 治理前 | 治理后 |
|------|--------|--------|
| Schema 数 | 15 | **8**（ecos_control/ecos_data/ecos_ontology/ecos_knowledge/ecos_ai/ecos_cognitive/ecos_infra/ecos_dw） |
| 表总数 | 238 | **201** |
| 消灭 schema | — | public(→ecos_control) / ecos_identity / ecos_agent / ecos_dq + 7 空壳 |
| outbox_event | 11 个分散 | **1 个统一**（ecos_infra） |

## 注意事项

1. Flyway 已全局禁用（`spring.flyway.enabled: false`），迁移脚本仅作为 DDL 历史参考
2. ST07 权威 schema 定义见 `01-ddl-多方言/postgresql/00_init_database.sql`（8 schema 封闭枚举）
3. **新 DDL 迁移脚本单源目录** = `docs/50-db_script/02-flyway-migration/gateway/`（见《数据库访问规范》v1.4 §八）
4. 实际 PG 实例已与 ST07 对齐（V200 治理完成，2026-10-01）
