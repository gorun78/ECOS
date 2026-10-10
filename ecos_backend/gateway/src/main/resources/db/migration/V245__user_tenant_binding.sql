-- V245 (详细设计-00 W06/R1.10): 登录身份表补租户绑定列，修 ECOS-AUTH-006 断链
-- 追溯: docs/30-设计/详细设计-00-平台入口与横切底座-2026-09-28.md §F F00-02（缺租户 → 403 显式拒绝）
-- 现网事实（2026-10-10 实测）: JwtAuthenticationFilter/AuthServiceImpl/sysman-boot AuthService 的租户回查
--       原指 TD_USER."TENANT_ID"，而控制域 td_user（组织侧遗留表）无该列、亦无 "ID"；权威登录身份表
--       ecos_control.users 同样没有租户列 ⇒ 回查必 bad SQL grammar，凡 claim 缺 tenant_id 的请求一律 403。
-- 处置: 只加列（IR03 schema 只加不删），并把存量登录用户初始化到 ACTIVE 默认租户 tenant-a
--       （V37__ecos_tenant_unified.sql）。这是 R1.10 javadoc 要求的"租户初始化"数据动作，不是代码兜底。
-- 红线: IR03 只加不删；schema 限定表标识符（ST07 主控制 schema）；tenant_id 引用 ecos_control.ecos_tenant.id
-- 回滚: 不 DROP COLUMN（R9 禁项）；如需撤权，UPDATE ecos_control.users SET tenant_id = NULL 即恢复 403 语义

ALTER TABLE ecos_control.users
    ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(64);

COMMENT ON COLUMN ecos_control.users.tenant_id IS '所属租户ID，引用 ecos_control.ecos_tenant.id；NULL = 未绑定，认证层按 ECOS-AUTH-006 显式拒绝';

UPDATE ecos_control.users SET tenant_id = 'tenant-a' WHERE tenant_id IS NULL;
