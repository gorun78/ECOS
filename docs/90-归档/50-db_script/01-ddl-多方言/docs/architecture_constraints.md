# ECOS Architecture Constraints — Add-Only SQL Policy

## Core Principle

All SQL schema changes in ECOS follow an **add-only policy**:
- **NEVER** drop an existing table
- **NEVER** drop or rename an existing column
- **NEVER** modify an existing column type (add a new column instead)
- **ONLY** add new tables, columns, indexes, constraints, or comments

## Rationale

1. **Zero-Downtime Deployments**: Add-only changes are backward-compatible. Old code continues reading old columns while new code reads new columns.
2. **Rollback Safety**: If a deployment fails, no data is lost. Simply redirect traffic to the old version.
3. **Audit Trail**: Every schema change is additive, preserving the full history of data model evolution.
4. **Multi-Dialect Compatibility**: Add-only changes translate cleanly across PostgreSQL, Oracle, and MySQL.

## Implementation Rules

### Allowed Operations
```sql
-- Add new table
CREATE TABLE IF NOT EXISTS ecos_domain.new_table (...);

-- Add new column
ALTER TABLE ecos_domain.existing_table ADD COLUMN IF NOT EXISTS new_col VARCHAR(128);

-- Add new index
CREATE INDEX IF NOT EXISTS idx_domain_table_col ON ecos_domain.table(col);

-- Add new FK constraint
ALTER TABLE ecos_domain.table
    ADD CONSTRAINT fk_name FOREIGN KEY (col) REFERENCES ecos_domain.other(col);

-- Add comment
COMMENT ON TABLE ecos_domain.table IS 'description';
COMMENT ON COLUMN ecos_domain.table.col IS 'description';
```

### Forbidden Operations
```sql
-- FORBIDDEN: Drop table
DROP TABLE ecos_domain.old_table;

-- FORBIDDEN: Drop column
ALTER TABLE ecos_domain.table DROP COLUMN old_col;

-- FORBIDDEN: Rename column
ALTER TABLE ecos_domain.table RENAME COLUMN old_name TO new_name;

-- FORBIDDEN: Change column type
ALTER TABLE ecos_domain.table ALTER COLUMN col TYPE NEW_TYPE;

-- FORBIDDEN: Rename table
ALTER TABLE ecos_domain.table RENAME TO new_name;
```

### Deprecation Pattern

When a column is no longer needed:
1. Add `COMMENT ON COLUMN ... IS 'DEPRECATED: Use new_col instead'`
2. Keep the column in DDL files for all 3 dialects
3. Application code migrates to new column gradually
4. Column is never physically removed

## Schema Version Tracking

Use `ecos_infra.schema_version` to track applied changes:

```sql
INSERT INTO ecos_infra.schema_version (id, schema_name, version, description, applied_at, applied_by)
VALUES (gen_random_uuid()::text, 'ecos_data', 'V54', 'Add data_lineage table', NOW(), 'migration');
```

## 7-Domain Schema Layout

| # | Schema | Domain | Engine |
|---|--------|--------|--------|
| 1 | ecos_sysman | System Management | — |
| 2 | ecos_security | Security Center | security-engine |
| 3 | ecos_data | Data Domain | data-engine |
| 4 | ecos_ontology | Ontology Domain | ontology-engine |
| 5 | ecos_knowledge | Knowledge Domain | kb-engine |
| 6 | ecos_ai | AI Domain | ai-engine |
| 7 | ecos_cognitive | Cognitive Domain | cognitive-engine |
| 8 | ecos_infra | Infrastructure | — |

## Migration from 12-Schema to 8-Schema

See `migration/12_to_8_schema.sql` for the ALTER TABLE SET SCHEMA migration script.
This script moves existing tables from the legacy 12-schema layout to the new 8-schema layout.
No tables are dropped or renamed — only moved between schemas.

## 3-Dialect Compatibility

All DDL must be provided in 3 dialects:
- **PostgreSQL** (primary/dev): `postgresql/` directory
- **Oracle**: `oracle/` directory (VARCHAR2, NUMBER, SYS_GUID, CLOB)
- **MySQL**: `mysql/` directory (VARCHAR, BIGINT AUTO_INCREMENT, LONGTEXT, JSON)

Type mapping and dialect-specific patterns are documented in each dialect directory.

## Table Preservation

Existing table names are preserved for backward compatibility:
- `td_user`, `td_role`, `td_organization` (quoted columns preserved)
- `ecos_agent`, `ecos_mission`, `ecos_wm_goal`
- `graph_node`, `graph_edge` (kb-engine)
- `ecos_knowledge_graph_node`, `ecos_knowledge_graph_edge` (runtime-core compat)

Both knowledge graph table sets coexist in `ecos_knowledge` schema until code is unified.
