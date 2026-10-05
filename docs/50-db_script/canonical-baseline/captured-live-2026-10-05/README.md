# 现网基线冻结（2026-10-05）

- `full-schema-only.sql` — **可回滚 baseline**（pg_dump --schema-only，679 表 / 3.9 万行 / 1.2MB）  
  复现命令: `docker exec -T ecos-postgres psql -U postgres -d sys_man < full-schema-only.sql`
- `live-objects-classified.tsv` — 686 行 '|'-sep 分类清单：`schema|objectname|kind|disposition`
- `pg_objects_live_2026-10-05.txt` / `pg_schema_summary.txt` — 原始快照（进审计 evidence/）
- `script_create_tables.txt` / `script_create_views.txt` — 仓内 SQL 声明 CREATE 表/视图清单
- `dump-stderr.log` — 空（pg_dump 无 warning）

## 处置分类口径（对齐 ST07 v1.3）

| disposition | 含义 |
|:--|:--|
| `retain-control-5engine` | 五引擎 schema 123 表 + 1 分区母表（已合规，本次不动） |
| `retain-then-rehome-to-ecos_control` | `public` 主控制 220（rehome 到 `ecos_control`，代码切流另立） |
| `retain-business-dw` | `ecos_dw` 2 表（保留） |
| `knownLegacy-stopwrite-no-drop` | `ecos_security/sysman/infra/dq` 133（R9 只停写不物理迁移） |
| `knownLegacy-drop-demo` | `ecos_demo` 208（演示，本次 drop） |
| `keep-empty-schema` | 4 空占位 schema（不 drop） |
