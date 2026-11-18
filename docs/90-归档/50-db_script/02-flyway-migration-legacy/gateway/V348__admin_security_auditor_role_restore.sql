-- ============================================================================
-- V348__admin_security_auditor_role_restore.sql
-- ============================================================================
-- 归属: ecos_control（控制域主 schema，ST07 八 schema 封闭枚举）
-- 类型: 数据补齐（INSERT，非破坏性；IR03 只加不删）
-- 日期: 2026-11-17
-- 关联: V347（登录权威统一到 td_user）／用户裁决「迁登录到 td_user，消灭 users」
--
-- 【为什么需要这一枚】
-- V347 把登录读面从平行表 users 迁到 td_user 后，角色单源改为真实隶属链
-- td_user_role → td_role。逐账号对账（live 实测）发现 admin 的角色集发生缩水：
--
--   users.roles（旧登录面） = ["ROLE_SUPER_ADMIN", "admin", "SECURITY_AUDITOR"]
--   td_user_role（新登录面） = ROLE_SUPER_ADMIN, ADMIN        ← 缺 SECURITY_AUDITOR
--
-- 用户裁决明确要求「先对账 admin 角色保住 ROLE_SUPER_ADMIN/SECURITY_AUDITOR」，
-- 故本枚把审计员码补回隶属链，使新角色集成为旧集的等价超集。
--
-- 【为什么不是把已有 SEC_AUDITOR 改码】
-- td_role 现有 role_ent2_sec_audit 的 role_code = 'SEC_AUDITOR'（企业经营管控演示
-- 种子 V283~V286 产出），与消费方认的 'SECURITY_AUDITOR' 不是同一个字符串。
-- 改码会动到演示种子的既有语义（IR03 + API 只增不改），故另立一行，
-- 两码并存的事实登记为「同类误会表/平行重复」清理批次的一项待裁，不在本枚擅自合并。
--
-- 【消费方取证（grep 全仓，非测试面）】
--   · SECURITY_AUDITOR 唯一消费方 = SecurityPolicyController:98
--       !"ADMIN".equalsIgnoreCase(role) && !"SECURITY_AUDITOR".equalsIgnoreCase(role)
--     其 role 取自 X-ECOS-ROLE 信任头，而 EcosTrustHeaderFilter:157-160 把全部
--     authority 用逗号拼成整串（"ROLE_SUPER_ADMIN,ADMIN,SECURITY_AUDITOR"），
--     整串 equalsIgnoreCase 单码恒 false ⇒ 该端点对 admin 迁移前后都 403。
--     这是既有缺陷（多角色头 vs 单码判据），本枚不修（改判据属授权行为变更，
--     超出迁移范围），只保证角色数据不丢，待该缺陷修复后审计员权限即可生效。
--   · 小写 "admin" 的全部消费方均已核对，不构成回归：
--       PermissionAspect:100 / GitRepoOrgGuard:96 —— 大小写敏感，但同时认
--         "ROLE_SUPER_ADMIN"（V347 已补隶属）⇒ 命中；
--       WAgentIdentityResolver:53 / SecuritySandboxService:100,106,110 /
--         SecurityController:113,119,124 —— equalsIgnoreCase ⇒ ADMIN 命中；
--       opa-policies/rbac.rego:56,63,98,108 —— 每条 "admin" 判据都有配对的
--         "ROLE_SUPER_ADMIN" 判据（55-67 的 is_scenario_admin 四条腿）⇒ 命中。
--     故不补小写 admin 码（避免在 td_role 里造大小写重复行）。
--
-- 【排序语义】AuthDao.findRoleCodes 的 ORDER BY 把 ROLE_SUPER_ADMIN 显式置首，
-- 其余按 role_code 字典序 ⇒ 本枚落地后 admin 角色集 =
--   ROLE_SUPER_ADMIN, ADMIN, SECURITY_AUDITOR
-- 首角色仍是 ROLE_SUPER_ADMIN，与旧 users.roles[0] 一致，OPA input.role 判据不变。
--
-- 幂等: INSERT ... SELECT ... WHERE NOT EXISTS（MC03，不用 ON CONFLICT）。
-- 运行: dry-run（默认回滚）
--   docker exec -i ecos-postgres psql -U postgres -d sys_man -v ON_ERROR_STOP=1 -f - < V348__admin_security_auditor_role_restore.sql
-- 实跑: 同上追加 -v ecos_commit=1
-- ============================================================================

BEGIN;

-- ① 审计员角色码（消费方认的字符串形态）
INSERT INTO ecos_control.td_role
    (role_id, role_name, role_code, description, tenant_id,
     created_time, created_by, updated_time, updated_by, status)
SELECT 'role_security_auditor', '安全审计员', 'SECURITY_AUDITOR',
       '平台审计员码：SecurityPolicyController 策略表达式原文读面的授权判据之一（与 ADMIN 并列）',
       'tenant-a', NOW(), 'system', NOW(), 'system', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM ecos_control.td_role WHERE role_id = 'role_security_auditor');

-- ② 补回 admin 的审计员隶属（org_id 沿用其既有隶属的组织，缺省 '-1'）
INSERT INTO ecos_control.td_user_role (user_id, role_id, org_id, created_time, created_by)
SELECT 'u-admin', 'role_security_auditor',
       COALESCE((SELECT src.org_id FROM ecos_control.td_user_role src
                  WHERE src.user_id = 'u-admin' AND src.role_id = 'role_admin' LIMIT 1), '-1'),
       NOW(), 'system'
WHERE EXISTS (SELECT 1 FROM ecos_control.td_user WHERE user_id = 'u-admin')
  AND NOT EXISTS (SELECT 1 FROM ecos_control.td_user_role
                   WHERE user_id = 'u-admin' AND role_id = 'role_security_auditor');

-- ③ 收口自检：角色集等价超集 + 首角色不变
DO $$
DECLARE
    v_codes TEXT;
    v_first TEXT;
    v_cnt   BIGINT;
BEGIN
    SELECT string_agg(r.role_code, ',' ORDER BY
               CASE WHEN r.role_code = 'ROLE_SUPER_ADMIN' THEN 0 ELSE 1 END, r.role_code)
      INTO v_codes
    FROM ecos_control.td_user_role ur
    JOIN ecos_control.td_role r ON ur.role_id = r.role_id
    WHERE ur.user_id = 'u-admin' AND r.role_code IS NOT NULL;

    IF v_codes IS NULL THEN
        RAISE EXCEPTION '收口失败：u-admin 无任何角色隶属';
    END IF;

    v_first := split_part(v_codes, ',', 1);
    IF v_first <> 'ROLE_SUPER_ADMIN' THEN
        RAISE EXCEPTION '收口失败：首角色 = %（期望 ROLE_SUPER_ADMIN，OPA input.role 判据会落空）', v_first;
    END IF;

    IF position('SECURITY_AUDITOR' in v_codes) = 0 THEN
        RAISE EXCEPTION '收口失败：admin 角色集缺 SECURITY_AUDITOR，实得 %', v_codes;
    END IF;
    IF position('ADMIN' in v_codes) = 0 THEN
        RAISE EXCEPTION '收口失败：admin 角色集缺 ADMIN，实得 %', v_codes;
    END IF;

    -- 隶属不得重复（幂等复跑不翻倍）
    SELECT count(*) INTO v_cnt
    FROM ecos_control.td_user_role ur
    JOIN ecos_control.td_role r ON ur.role_id = r.role_id
    WHERE ur.user_id = 'u-admin' AND r.role_code = 'SECURITY_AUDITOR';
    IF v_cnt <> 1 THEN
        RAISE EXCEPTION '收口失败：SECURITY_AUDITOR 隶属行数 = %（期望 1）', v_cnt;
    END IF;

    RAISE NOTICE 'V348 收口通过：admin 角色集 = %', v_codes;
END $$;

-- 事务开关：默认回滚（dry-run），传 -v ecos_commit=1 才提交
\if :{?ecos_commit}
    \if :ecos_commit
        COMMIT;
        \echo 'V348 已提交（ecos_commit=1）'
    \else
        ROLLBACK;
        \echo 'V348 已回滚（dry-run，未传 ecos_commit=1）'
    \endif
\else
    ROLLBACK;
    \echo 'V348 已回滚（dry-run，未传 ecos_commit=1）'
\endif