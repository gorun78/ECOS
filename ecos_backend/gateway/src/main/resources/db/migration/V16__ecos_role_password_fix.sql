-- V16__ecos_role_password_fix.sql
-- Fix: copy admin's password hash to all users (all use 'admin123' after this)
-- Map actual DB users to 7-role system
-- Date: 2026-06-24

-- Copy admin's password_hash to all users
UPDATE users SET 
    password_hash = (SELECT password_hash FROM users WHERE username = 'admin')
WHERE username != 'admin' AND password_hash IS NULL;

-- Set roles
UPDATE users SET roles = '["ROLE_EXECUTIVE"]'          WHERE username IN ('test_admin', 'test_final');
UPDATE users SET roles = '["ROLE_DEPT_MANAGER"]'       WHERE username IN ('zhangsan', 'lisi');
UPDATE users SET roles = '["ROLE_DATA_MANAGER"]'       WHERE username IN ('test_data_admin', 'test_expert');
UPDATE users SET roles = '["ROLE_BUSINESS_OPERATOR"]'   WHERE username IN ('test_hermes', 'testuser');
UPDATE users SET roles = '["ROLE_READONLY_OBSERVER"]'  WHERE username IN ('test_guest', 'test_user_001');
UPDATE users SET roles = '["ROLE_SECURITY_AUDITOR"]'   WHERE username = 'wangwu';
