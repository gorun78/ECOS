-- V14__ecos_role_expansion.sql
-- Expand role system from 2 roles to 7 roles
-- Date: 2026-06-24

-- 1. Update existing users with expanded role assignments
UPDATE users SET roles = '["ROLE_SUPER_ADMIN", "admin", "SECURITY_AUDITOR"]' WHERE username = 'admin';
UPDATE users SET roles = '["ROLE_DATA_MANAGER"]' WHERE username = 'data_manager';
UPDATE users SET roles = '["ROLE_DATA_MANAGER"]' WHERE username = 'data_engineer1';
UPDATE users SET roles = '["ROLE_BUSINESS_OPERATOR"]' WHERE username = 'operator1';
UPDATE users SET roles = '["ROLE_DEPT_MANAGER"]' WHERE username = 'dept_admin';
UPDATE users SET roles = '["ROLE_DEPT_MANAGER"]' WHERE username = 'market_manager';
UPDATE users SET roles = '["ROLE_READONLY_OBSERVER"]' WHERE username = 'viewer1';
UPDATE users SET roles = '["ROLE_BUSINESS_OPERATOR"]' WHERE username = 'workflow_tester';
