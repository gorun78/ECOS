-- =============================================================================
-- V304 — R7 §2.9.5③ 注册表纳入/排除清单载体列（#147Q）
-- 单源目录：docs/50-db_script/02-flyway-migration/gateway/
--
-- 背景：§2.9.7 订正登记「注册表无 include/exclude 载体列 ⇒ §2.9.5③ 数据类型区别处理
--       只靠文档约定」。本批落两枚可空 TEXT 列（逗号分隔的相对路径前缀清单）：
--         include_paths  非空时仅允许这些前缀（受控纳入）
--         exclude_paths  命中的前缀一律拒绝（排除优先级高于纳入）
--       密码/令牌/密钥/会话/个人敏感信息不得入普通 Git 的约定自此有代码载体。
-- 约束：additive、可空（存量 6 行种子不填＝现状全量纳入）；不改既有 13 列。
-- =============================================================================

ALTER TABLE ecos_control.ecos_git_repo
    ADD COLUMN IF NOT EXISTS include_paths TEXT;

ALTER TABLE ecos_control.ecos_git_repo
    ADD COLUMN IF NOT EXISTS exclude_paths TEXT;

COMMENT ON COLUMN ecos_control.ecos_git_repo.include_paths IS
    '§2.9.5③ 纳入清单（逗号分隔相对路径前缀；非空时仅允许命中前缀，空=全量纳入）';
COMMENT ON COLUMN ecos_control.ecos_git_repo.exclude_paths IS
    '§2.9.5③ 排除清单（逗号分隔相对路径前缀；命中即拒，优先级高于纳入——敏感目录专用）';
