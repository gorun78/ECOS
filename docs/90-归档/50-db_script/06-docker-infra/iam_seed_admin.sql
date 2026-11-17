-- 本地开发种子用户（供确认用）：admin / Admin@123
-- 幂等：重复执行安全（ON CONFLICT DO UPDATE）
INSERT INTO ecos_identity.users (id, username, password_hash, display_name, roles, enabled, password_change_required, last_password_change, password_history, tenant_id)
VALUES ('u-admin', 'admin', '$2a$10$hDB6FaD8Jvvx27AeG7LCFOFumpFcY5MkgElm0JQkjtYirNwKd.ZUO',
        'Super Administrator', '["ROLE_SUPER_ADMIN"]', TRUE, FALSE, NOW(), '[]', 'tenant-a')
ON CONFLICT (username) DO UPDATE SET enabled = TRUE, password_change_required = FALSE;

INSERT INTO ecos_identity.td_user (user_id, username, password, status, tenant_id)
VALUES ('u-admin', 'admin', '$2a$10$hDB6FaD8Jvvx27AeG7LCFOFumpFcY5MkgElm0JQkjtYirNwKd.ZUO', 'ACTIVE', 'tenant-a')
ON CONFLICT (user_id) DO UPDATE SET status = 'ACTIVE', tenant_id = 'tenant-a';
