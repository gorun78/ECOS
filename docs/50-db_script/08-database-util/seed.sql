-- DataBridge SysMan Seed Data
-- Uses actual V1 table names: td_user, td_role, td_permission, td_organization, td_role_permission, td_user_role

-- 1. Roles
INSERT IGNORE INTO td_role (ROLE_ID, ROLE_NAME, ROLE_CODE, DESCRIPTION, STATUS, CREATED_TIME, CREATED_BY)
VALUES 
('role_admin', '系统管理员', 'ADMIN', '系统最高权限角色', 'ACTIVE', NOW(), 'system'),
('role_operator', '运维操作员', 'OPERATOR', '日常运维操作', 'ACTIVE', NOW(), 'system'),
('role_viewer', '只读用户', 'VIEWER', '仅查看权限', 'ACTIVE', NOW(), 'system');

-- 2. Admin User (password: admin123, bcrypt hash)
INSERT IGNORE INTO td_user (USER_ID, USERNAME, PASSWORD, REAL_NAME, EMAIL, STATUS, LOCKED, CREATED_TIME, CREATED_BY)
VALUES 
('user_admin', 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '系统管理员', 'admin@example.com', 'ACTIVE', '0', NOW(), 'system');

-- 3. User-Role Assignment
INSERT IGNORE INTO td_user_role (USER_ID, ROLE_ID, CREATED_TIME)
VALUES ('user_admin', 'role_admin', NOW());

-- 4. Permissions
INSERT IGNORE INTO td_permission (PERMISSION_ID, PERMISSION_NAME, PERMISSION_CODE, RESOURCE_ID, ACTION, DESCRIPTION, CREATED_TIME, CREATED_BY)
VALUES 
('perm_user_read', '查看用户', 'user:read', 'user', 'read', '查看用户列表与详情', NOW(), 'system'),
('perm_user_write', '管理用户', 'user:write', 'user', 'write', '创建和编辑用户', NOW(), 'system'),
('perm_user_delete', '删除用户', 'user:delete', 'user', 'delete', '删除用户', NOW(), 'system'),
('perm_role_read', '查看角色', 'role:read', 'role', 'read', '查看角色列表', NOW(), 'system'),
('perm_role_write', '管理角色', 'role:write', 'role', 'write', '创建和编辑角色', NOW(), 'system'),
('perm_role_delete', '删除角色', 'role:delete', 'role', 'delete', '删除角色', NOW(), 'system'),
('perm_org_read', '查看机构', 'org:read', 'organization', 'read', '查看机构', NOW(), 'system'),
('perm_org_write', '管理机构', 'org:write', 'organization', 'write', '管理组织机构', NOW(), 'system'),
('perm_menu_read', '查看菜单', 'menu:read', 'menu', 'read', '查看功能菜单', NOW(), 'system'),
('perm_audit_read', '查看审计', 'audit:read', 'audit', 'read', '查看审计日志', NOW(), 'system'),
('perm_agent_chat', 'AI问答', 'agent:chat', 'agent', 'chat', '使用AI智能问答', NOW(), 'system');

-- 5. Role-Permission (admin gets all)
INSERT IGNORE INTO td_role_permission (ROLE_ID, PERMISSION_ID, CREATED_TIME)
SELECT 'role_admin', PERMISSION_ID, NOW() FROM td_permission;

-- Operator gets read + agent
INSERT IGNORE INTO td_role_permission (ROLE_ID, PERMISSION_ID, CREATED_TIME)
SELECT 'role_operator', PERMISSION_ID, NOW() FROM td_permission WHERE ACTION='read';

INSERT IGNORE INTO td_role_permission (ROLE_ID, PERMISSION_ID, CREATED_TIME)
VALUES ('role_operator', 'perm_agent_chat', NOW());

-- Viewer gets read-only
INSERT IGNORE INTO td_role_permission (ROLE_ID, PERMISSION_ID, CREATED_TIME)
SELECT 'role_viewer', PERMISSION_ID, NOW() FROM td_permission WHERE ACTION='read';

-- 6. Root Organization
INSERT IGNORE INTO td_organization (ORG_ID, ORG_NAME, ORG_CODE, PARENT_ORG_ID, ORG_TYPE, DESCRIPTION, STATUS, CREATED_TIME, CREATED_BY)
VALUES ('org_root', '根机构', 'ROOT', NULL, 'CORPORATION', '根组织节点', 'ACTIVE', NOW(), 'system');
