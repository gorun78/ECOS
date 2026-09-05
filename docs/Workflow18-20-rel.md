# Workflow 18-20 回归报告 — T22 端点 5xx 修复 (V108)

> **架构铁律**: 遵循 ECOS 架构铁律 5.4 验证四步法 + 数据层 3.1 (schema 只加不删, Flyway 禁用)
> 来源: PMO Wave6 T25 | 日期: 2026-09-03
> 铁律: 迁移只加不删 / 全部幂等 / 不跨 Phase 预创建 / 表名以代码实际访问为准

## 背景

PAT 18 WSL T22 端点回归中暴露 **15 个 500**，根因全部为
`PG relation "xxx" does not exist` + `column "xxx" does not exist` 两类 DDL 缺口。
FAQ 408 提出的"DDL 先于代码，not exist 死循环 551"在本批首次落地：迁移 V108
在 `gateway/db/migration/` 新增，Flyway 已禁用故由外部工单执行（本文附步骤）。

## 4 组根因 → 修复

| # | 触发端点 | 根因 | 代码证据 | V108 修复 |
|:-:|----------|------|----------|-----------|
| 1 | `GET/POST /api/v1/ontology/proposals` (arch 1/30/36) | 生产库无 `ecos_ontology_proposals` 表 | `OntologyProposalService.java` 28 处 SQL 全用复数表名；`V4.1` 定义在 dev 库未同步到生产 | `CREATE TABLE IF NOT EXISTS ecos_ontology_proposals` + `ALTER TABLE ADD COLUMN IF NOT EXISTS optimistic_lock_version` (V4.3 乐观锁) + 2 索引 |
| 2 | `POST /api/v1/catalog/*` register (catalog 11/34) + `td_datasource` | `td_catalog_item` 缺 `tenant_id` 列 | `CatalogServiceImpl.register()` 第 41 行显式 INSERT `tenant_id`；`V19` 原始 DDL 未含该列 | `ALTER TABLE td_catalog_item ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64)` |
| 3 | workflow `terminate` / `resume` (19-21) | `ecos_workflow_instance` 缺 `error_message` / `current_node_id` / `context_json` 列 | `WorkflowInstanceRepository.ROW_MAPPER` 读 `current_node_id`/`context_json`；line 76 `UPDATE ... error_message = ?`；生产库表旧于 `V1.1` 命名差异 (V1.1 用 `current_node_ids`/`context`，Repository 用单数 `_json`) | `ALTER TABLE ecos_workflow_instance ADD COLUMN IF NOT EXISTS error_message/retry_count/current_node_id/context_json/created_at/started_at/completed_at` |
| 4 | knowledge node/edge DQ (25/26) | 生产库无 `ecos_knowledge_graph_node` 表 + `processed_at` 类型不匹配 (业务按 BIGINT epoch 赋值旧 TIMESTAMP 列报 5xx) | `KnowledgeNodeRepository.java` 15/21/26/33 行 SELECT `created_at`；`V50` 在 `ecos_agent` schema 定义的是另一套 agent 表，与 KB node 无关 | `CREATE TABLE IF NOT EXISTS ecos_knowledge_graph_node` (id/label/node_type/description/properties_json/created_at/processed_at) + 2 索引 |

## 交付物

1. **迁移文件** (交付物, 幂等, 重复执
行无副作用)：
   `\\wsl$\Ubuntu\home\guorongxiao\ECOS\ecos_backend\gateway\src\main\resources\db\migration\V108__wave6_t25_missing_tables.sql`

2. **本报告**：`\\wsl$\Ubuntu\home\guorongxiao\ECOS\docs\Workflow18-20-rel.md`

## 执行验证四步法 (V1-V4)

| 步 | 命令 | 结果 |
|:-:|------|------|
| V1 文件生存 | `ls -la .../migration/V108*.sql` | ✅ 存在, 6 组 DDL |
| V2 集成点 grep | 4 处代码全部匹配 V108 列/表名 | ✅ 无 identifier 错配 |
| V3 执行 | `docker cp V108_verify.sql /tmp/ && docker exec ecos-postgres psql -d sys_man -v ON_ERROR_STOP=1 -f ...` | ✅ `EXIT_CODE=0`, 仅 NOTICE (列已存在跳过), 无 ERROR |
| V4 幂等 + 列回读 | 第二遍执行 `IDEMPOTENT_PASS`；`information_schema.columns` 回读 4 组列/表 | ✅ 全部存在 |

## 执行指令 (生产/预发库)

```bash
# 容器内
docker exec -it -e PGPASSWORD=<pwd> ecos-postgres psql -U <user> -d sys_man \
  -v ON_ERROR_STOP=1 -f $(docker cp - <path>/V108__wave6_t25_missing_tables.sql)
# 或外部落地 psql:
psql "$DSN" -v ON_ERROR_STOP=1 -f V108__wave6_t25_missing_tables.sql
```

## 风险与后续

- **不删列/表铁律**：`created_at` TIMESTAMP 与新增 `processed_at` BIGINT 并存，业务层后续统一切换到 `processed_at` 后再评估收敛。
- **`td_catalog_item` UNIQUE 约束**：本次仅补列，未加 `UNIQUE(tenant_id, resource_id)`——因生产库 LIKE 查询走 `idx_catalog_res`，唯一索引可能扰动索引选择；留到 Wave6 T26 与租户隔离方案统一处理。
- **`current_node_ids` vs `current_node_id`**：V1.1 原始名与 Repository 实际读取名不一致，V108 按 Repository 补齐单数形式；双列并存一段时间后 (V4.1 修复时) 再统一收敛，期间两张列名都能工作。

## 结论

15 个 500 全部由 DDL 缺口导致，V108 一次性补齐 4 组根因。执行后 T22 回归应
从 15 个 5xx 收敛到 0（业务层 4xx 按各自验收标准另行跟踪）。
