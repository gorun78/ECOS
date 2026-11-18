-- ============================================================================
-- V347 — 登录权威表归整：ecos_control.users → ecos_control.td_user
-- ============================================================================
-- 背景（2026-11-17 活体反证，推翻「users 是开发期误会表、可直接删」的原判）:
--   · gateway/pom.xml 依赖 sysman-boot，部署 fat JAR 内实存
--     BOOT-INF/lib/sysman-boot-1.0.0-SNAPSHOT.jar，GatewayApplication 扫
--     com.chinacreator.gzcm.sysman 覆盖其 boot.controller.AuthController；
--   · 全后端唯一的 /api/v1/auth/login 端点即在该控制器，读的是 users；
--   · 活体登录签出的 JWT roles = ["ROLE_SUPER_ADMIN","admin","SECURITY_AUDITOR"]，
--     与 users.roles 逐字相同，而 td_user_role 侧 admin 只有 role_admin(ADMIN)；
--   · td_user 版 AuthServiceImpl 虽是 @Service 但零注入方，无 HTTP 入口。
--   ⇒ users 是承重登录表，td_user 是用户管理表，中间靠 LoginCredentialService 单向镜像。
--     单向镜像已产生真实分叉：u-d01 在 users 侧 locked_until 有值、td_user 侧 locked=0。
--
-- 本脚本兑现用户裁决「迁登录到 td_user，消灭 users」的**数据库前置**：
--   ① 给 td_user 补齐原只存在于 users 的 5 个登录安全字段；
--   ② 角色对账：把 users.roles 里 td_user_role 缺失的平台超管码补成真实隶属行；
--   ③ 把 users 侧现存安全态回填到 td_user（不覆盖 td_user.password —— 它是权威口令）。
--   代码侧同批把 boot AuthDao/AuthService/AuthController 改读 td_user，之后镜像层才允许拆。
--   users 的 DROP 属阶段 3，需逐项授权，本脚本不做（IR03）。
--
-- 角色对账的判据（为什么必须补 ROLE_SUPER_ADMIN、且必须置首）:
--   · PermissionAspect / GitRepoOrgGuard 用**大小写敏感**的
--     "ROLE_SUPER_ADMIN".equals(a) || "admin".equals(a) ⇒ td_role 的 ADMIN 不命中；
--   · SecuritySandboxService 经 hasRoleCode(userId,'ROLE_SUPER_ADMIN') 查
--     td_user_role JOIN td_role ⇒ 缺该行时超管优先腿今天就是断的；
--   · opa-policies/rbac.rego 的 input.role 取**角色集首角色**，判据为
--     =="admin" / =="ROLE_SUPER_ADMIN" ⇒ 首角色若是 ADMIN 则 scenario:write 等规则恒 false。
--   故 DAO 取角色码时按「超管码优先置首，其余按 role_code 升序」排序（见 AuthDao.findRoleCodes）。
--   users.roles 里的 "admin"（小写）与 "SECURITY_AUDITOR" 是冗余别名：前者被
--   ROLE_SUPER_ADMIN 覆盖，后者在 SecurityPolicyController 里只是 ADMIN 的或分支，
--   admin 已持 ADMIN ⇒ 不再补行，避免造重复授权。
--
-- 纪律: IR03（只加不删：仅 ADD COLUMN / INSERT / UPDATE，不 DROP 不删列）
--       IR02（手动 psql，Flyway 禁用）
--       MC03（无 ON CONFLICT、无 RETURNING、无 :: cast；幂等靠 WHERE NOT EXISTS）
--       DR05（布尔 is_ 前缀）/ DR04（JSON 语义列 _json 后缀）
--
-- 运行:
--   dry-run（默认回滚）:
--     docker exec -i ecos-postgres psql -U postgres -d sys_man -v ON_ERROR_STOP=1 < 本文件
--   正式提交:
--     docker exec -i ecos-postgres psql -U postgres -d sys_man -v ON_ERROR_STOP=1 -v ecos_commit=1 < 本文件
-- ============================================================================

BEGIN;

-- ── ① td_user 补登录安全字段 ────────────────────────────────────────────────
-- locked_until 与既有 locked/lock_time 分工（两种锁语义并存，登录侧都认）:
--   · 管理锁：UserServiceImpl.lockUser 写 locked='1' + lock_time，无到期 ⇒ 永久，需管理员解锁；
--   · 爆破锁：登录失败达阈值写 locked_until=NOW()+间隔，locked 保持 '0' ⇒ 到期自动放行。
-- 刻意不让爆破锁写 locked='1'，否则管理锁判据会把临时锁定变成永久锁定。
ALTER TABLE ecos_control.td_user ADD COLUMN IF NOT EXISTS failed_attempts INTEGER DEFAULT 0;
ALTER TABLE ecos_control.td_user ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP;
ALTER TABLE ecos_control.td_user ADD COLUMN IF NOT EXISTS is_password_change_required BOOLEAN DEFAULT FALSE;
ALTER TABLE ecos_control.td_user ADD COLUMN IF NOT EXISTS last_password_change TIMESTAMP;
ALTER TABLE ecos_control.td_user ADD COLUMN IF NOT EXISTS password_history_json TEXT;

-- ── ② 角色对账：补平台超管码及其隶属行 ──────────────────────────────────────
INSERT INTO ecos_control.td_role
    (role_id, role_name, role_code, description, tenant_id,
     created_time, created_by, updated_time, updated_by, status)
SELECT 'role_super_admin', '超级管理员', 'ROLE_SUPER_ADMIN',
       '平台超管码：PermissionAspect/GitRepoOrgGuard 大小写敏感判据与 OPA rbac.rego 首角色判据的唯一命中形态',
       'tenant-a', NOW(), 'system', NOW(), 'system', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM ecos_control.td_role WHERE role_id = 'role_super_admin');

INSERT INTO ecos_control.td_user_role (user_id, role_id, org_id, created_time, created_by)
SELECT 'u-admin', 'role_super_admin',
       COALESCE((SELECT src.org_id FROM ecos_control.td_user_role src
                  WHERE src.user_id = 'u-admin' AND src.role_id = 'role_admin' LIMIT 1), '-1'),
       NOW(), 'system'
WHERE EXISTS (SELECT 1 FROM ecos_control.td_user WHERE user_id = 'u-admin')
  AND NOT EXISTS (SELECT 1 FROM ecos_control.td_user_role
                   WHERE user_id = 'u-admin' AND role_id = 'role_super_admin');

-- ── ③ 回填 users 侧现存安全态（口令不覆盖：td_user.password 是权威） ─────────
-- 幂等判据 = password_history_json IS NULL（尚未回填过）。
UPDATE ecos_control.td_user t
   SET failed_attempts = COALESCE((SELECT u.failed_attempts FROM ecos_control.users u
                                    WHERE u.username = t.username), 0),
       locked_until = (SELECT u.locked_until FROM ecos_control.users u
                        WHERE u.username = t.username),
       is_password_change_required = COALESCE((SELECT u.password_change_required
                                                 FROM ecos_control.users u
                                                WHERE u.username = t.username), FALSE),
       last_password_change = (SELECT u.last_password_change FROM ecos_control.users u
                                WHERE u.username = t.username),
       password_history_json = (SELECT u.password_history FROM ecos_control.users u
                                 WHERE u.username = t.username)
 WHERE t.password_history_json IS NULL
   AND EXISTS (SELECT 1 FROM ecos_control.users u WHERE u.username = t.username);

-- ── 收口断言 ────────────────────────────────────────────────────────────────
DO $$
DECLARE
    v_missing TEXT;
    v_cnt INTEGER;
BEGIN
    SELECT string_agg(c, ', ') INTO v_missing
    FROM (SELECT unnest(ARRAY['failed_attempts', 'locked_until', 'is_password_change_required',
                              'last_password_change', 'password_history_json']) AS c) want
    WHERE NOT EXISTS (SELECT 1 FROM information_schema.columns col
                       WHERE col.table_schema = 'ecos_control' AND col.table_name = 'td_user'
                         AND col.column_name = want.c);
    IF v_missing IS NOT NULL THEN
        RAISE EXCEPTION '收口失败：td_user 缺列 %', v_missing;
    END IF;

    -- 超管码必须能被 hasRoleCode 的 JOIN 形态命中（否则超管优先腿仍是断的）
    SELECT count(*) INTO v_cnt
    FROM ecos_control.td_user_role ur
    JOIN ecos_control.td_role r ON ur.role_id = r.role_id
    WHERE ur.user_id = 'u-admin' AND r.role_code = 'ROLE_SUPER_ADMIN';
    IF v_cnt <> 1 THEN
        RAISE EXCEPTION '收口失败：u-admin 的 ROLE_SUPER_ADMIN 隶属行数 = %（期望 1）', v_cnt;
    END IF;

    RAISE NOTICE 'V347 收口通过：td_user 登录安全字段齐备，u-admin 超管码隶属在位';
END $$;

-- 事务开关：默认回滚（dry-run），传 -v ecos_commit=1 才提交
\if :{?ecos_commit}
    \if :ecos_commit
        COMMIT;
        \echo 'V347 已提交（ecos_commit=1）'
    \else
        ROLLBACK;
        \echo 'V347 已回滚（dry-run，未传 ecos_commit=1）'
    \endif
\else
    ROLLBACK;
    \echo 'V347 已回滚（dry-run，未传 ecos_commit=1）'
\endif