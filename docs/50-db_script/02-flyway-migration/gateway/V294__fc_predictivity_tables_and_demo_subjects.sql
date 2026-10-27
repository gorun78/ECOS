-- ============================================================
-- V294__fc_predictivity_tables_and_demo_subjects.sql
-- D-B4-19（差距台账 v1.0.180，#142/B4 安全域余项队列；2026-10-27 #146i 用户裁决
-- 「D-B4-19 live 三前置」）：预测场景 fc_* 三张事实表的 DDL 单源首建 +
-- V246 种子三表真正实跑的三重前置件（① fc_* 三表建表；② 演示主体 seed；
--   ③ V246 播种在本批后续 psql 步骤执行——本脚本只落前置两件套，
--   V246 文件的 INSERT 字面自 3e-90 起已迁 ecos_control 目标，本脚本不改其字面，
--   仅补其缺的前置真值面）。
--
-- ── 缺陷事实（live 实证，取证 = 本批 #146i 期 2026-10-27 dev PG 实查）──
--   ① fc_* 三表名（fc_project_period_fact / fc_cost_allocation_fact /
--      fc_resource_input_fact）在 DDL 单源 **零 DDL 定义**（全目录 V*.sql grep
--      建表语句 0 命中），仅 V246 种子的 table_name/asset_key 字面引用；
--      列级加密豁免登记表 :30/:31/:32 三行「预登记」依赖这三张表真实存在
--      才能「预登记」转「生效」——dev 库 ecos_dw 面实测三表零存在。
--     列面单源 = V246 夹具（V246SeedFixtures.java:63-66 三表常量）+
--     V246 种子谓词（department_id / project_id 两绑面列）+
--     列级加密豁免登记表 §二 首批行（plan_amount / actual_amount /
--     hourly_rate / staff_ref / allocated_amount 五列名）反推。
--   ② td_user 演示主体 u-fin/u-p1/u-d01/u-none 零在位；四角色
--      finance_lead / project_owner / dept_user / non_finance 在
--      td_role 零在位（EXIST_ROLES 实查 11 枚无一敌对）；
--      td_user_role / td_user_organization 对四演示主体零行。
--      四角色 × 四主体的布局（u-fin→{finance_lead} 主机构 org_ent_fin；
--      u-p1→{project_owner,non_finance} 主机构 org_ent_ops；
--      u-d01→{dept_user,non_finance} 主机构 org_ent_fin；u-none→无角色行）
--      单源 = 用户 2026-10-27 裁决 verbatim 范围声明；
--      真值面与 bpa live 行（p-ent-001→org_ent_ops 0.6+org_ent_fin 0.4；
--      p-ent-002→org_ent_ops 1.0；p-ent-003→org_ent_fin 1.0）联动匹配，
--      u-p1 走子查询谓词时命中 p-ent-001+p-ent-002 两项目。
--
-- ── 本脚本动作（两节，均幂等 ON CONFLICT / IF NOT EXISTS）──
--   A. fc_* 三张事实表 CREATE TABLE IF NOT EXISTS（V167 同构展开 + 审计五列
--      + is_active + domain 'forecast' + version_no '1' + ST03-A 预登记列
--      plan_amount/actual_amount/hourly_rate/staff_ref 照 V246 头注所述
--      「与列级加密豁免登记表 4 张预登记表一致」口径命名）。
--   B. td_role 四角色 seed（角色 id = 关键词键，与 V246 种子 role_id 字面同源，
--      不沿用 role_ 前缀——V246 已把项目内语义固化为「四角色 = 四关键词」，
--      本 seed 取 V246 侧为单源）。
--   C. td_user 四演示主体 seed（password 字段 = u-admin 的 60-char bcrypt 值
--      复制，非应用侧重新 hash——python bcrypt/pip/jrunscript/node bcryptjs
--      本机全不可用，已在差距台账 D-B4-19 行订正注⑫如实标注占位缘由；
--      登录演示主体不是本批 live E2E 通道，/api/v1/security/decision 只按
--      subject.id 覆盖鉴权面）。
--   D. td_user_role 五枚角色行 + td_user_organization 三枚主机构行（u-none
--      无任何 role 行 = 零策略 ⇒ 1=0 deny；u-fin/u-p1/u-d01 各恰好一行
--      is_primary='1' 主机构，唯一主机构解析链 SubjectDepartmentResolutionService
--      → PrimaryOrganizationRule.uniquePrimaryOrg 可直接解析 CONTEXT_ORG）。
--   F. fc_* 三张事实表最小演示行（每表 3 行 × 三枚 bpa 实存项目
--      p-ent-001/002/003，department_id 取 bpa 归属侧 org_ent_ops/org_ent_fin）——
--      不带这些行，live E2E 四道行范围取证全是空集（「无泄漏」恒真＝假绿形），
--      F 节让 RlsFinAll/RlsProjOwner/RlsDept/RlsDenyNoLeak 的行数断言可非平凡。
--
-- ── DDL/Seed Lint Self-audit ──────────────────────────────────
-- [IR03] 只加不删（CREATE IF NOT EXISTS + INSERT ON CONFLICT DO NOTHING）✓
-- [IR04] 无 SELECT * 拼接 ✓  [MC01] 主键 VARCHAR(36) 应用侧固定键（无 gen_random_uuid）✓
-- [MC02] 金额 NUMERIC(18,2) ✓  [MC03] 无 JSON 二进制列、无自增 ✓
-- [DR02] fc_* 前缀：见差距台账 D-B4-19 行，V167 同族业务事实五表走 ecos_biz_，
--   本族 fc_* 属 V246 已冻结语义（谓词/asset_key/豁免登记表三处字面均 fc_ 头），
--   再更名等价于破坏 V246 种子字面 + 声称在册的字面契约 ⇒ 本脚本字面口径以
--   V246 冻结面为权威（R9「现有字面不动，新面按规范」），fc_* 判定为 knownLegacy
--   语义冻结（D-B4-19 行订正注⑫ 同批登记 DR02 例外锚）✓
-- [DR06/07/08] 审计五列 + version_no VARCHAR(20) NN + domain VARCHAR(50) NN
--   DEFAULT 'forecast' ✓（V200+ 段成员级，与 V167 同形）
-- [ST07] 事实表落业务域 ecos_dw ✓  [ST09] 零 FOREIGN KEY ✓
-- [CD-1] td_user_user_id 主键 VARCHAR(64) 与 td_user_role.user_id/td_user_organization.user_id
--   共享同型；VARCHAR(36) 与 VARCHAR(64) 在 pg 上无隐式截断（本 seed 直接用
--   u-fin/u-p1/u-d01/u-none 短键）✓
-- [ST03-A] fc_* 三表金额/时薪/人员 ref 列均已预登记（豁免登记表 §二 :30/:31/:32；
--   本脚本落 DDL 只是把「预登记」转「生效」的动作前置件）✓
--
-- 回滚说明: IR03 禁 DROP/DELETE；如需在新库重跑本脚本，全部 IF NOT EXISTS +
--   ON CONFLICT DO NOTHING，重复执行无副作用。
-- ============================================================

-- ── A. fc_* 三张事实表（V167 同族展开）──────────────────────────
-- A.1 fc_project_period_fact：预测项目周期事实表（V246 谓词 department_id = :userDeptId）
CREATE TABLE IF NOT EXISTS ecos_dw.fc_project_period_fact (
    id                 VARCHAR(36) PRIMARY KEY,            -- MC01：应用侧固定键
    project_id         VARCHAR(36)  NOT NULL,
    department_id      VARCHAR(36)  NOT NULL,              -- rls-dept-scope 谓词绑面
    period             VARCHAR(7)   NOT NULL,              -- yyyy-MM
    plan_amount        NUMERIC(18,2),                      -- ST03-A 预登记（豁免表 §二 行 30）
    actual_amount      NUMERIC(18,2),                      -- ST03-A 预登记（豁免表 §二 行 31）
    currency           VARCHAR(3),
    source_type        VARCHAR(20),
    dq_status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    is_active          SMALLINT     NOT NULL DEFAULT 1,
    batch_id           VARCHAR(36),
    source_ref         VARCHAR(255),
    evidence_ref       VARCHAR(255),
    version_no         VARCHAR(20)  NOT NULL,
    domain             VARCHAR(50)  NOT NULL DEFAULT 'forecast',
    is_deleted         SMALLINT     NOT NULL DEFAULT 0,
    create_time        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by          VARCHAR(100),
    update_by          VARCHAR(100)
);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_pcf_active
    ON ecos_dw.fc_project_period_fact(project_id, department_id, period, is_active);
CREATE INDEX IF NOT EXISTS idx_pcf_project ON ecos_dw.fc_project_period_fact(project_id, period);
CREATE INDEX IF NOT EXISTS idx_pcf_dept    ON ecos_dw.fc_project_period_fact(department_id);

-- A.2 fc_cost_allocation_fact：成本分摊事实表（V246 CLS 列 hourly_rate/amount 绑面）
CREATE TABLE IF NOT EXISTS ecos_dw.fc_cost_allocation_fact (
    id                 VARCHAR(36) PRIMARY KEY,
    project_id         VARCHAR(36)  NOT NULL,
    department_id      VARCHAR(36)  NOT NULL,
    period             VARCHAR(7)   NOT NULL,
    hourly_rate        NUMERIC(18,2),                      -- ST03-A 预登记（V246 asset sec-asset-cost-allocation-hourly）
    amount             NUMERIC(18,2),                      -- ST03-A 预登记（V246 asset sec-asset-cost-allocation-amount）
    allocated_amount   NUMERIC(18,2),                      -- ST03-A 预登记（豁免表 §二 行 32）
    currency           VARCHAR(3),
    source_type        VARCHAR(20),
    dq_status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    is_active          SMALLINT     NOT NULL DEFAULT 1,
    batch_id           VARCHAR(36),
    source_ref         VARCHAR(255),
    evidence_ref       VARCHAR(255),
    version_no         VARCHAR(20)  NOT NULL,
    domain             VARCHAR(50)  NOT NULL DEFAULT 'forecast',
    is_deleted         SMALLINT     NOT NULL DEFAULT 0,
    create_time        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by          VARCHAR(100),
    update_by          VARCHAR(100)
);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_caf_active
    ON ecos_dw.fc_cost_allocation_fact(project_id, department_id, period, is_active);
CREATE INDEX IF NOT EXISTS idx_caf_project ON ecos_dw.fc_cost_allocation_fact(project_id, period);
CREATE INDEX IF NOT EXISTS idx_caf_dept    ON ecos_dw.fc_cost_allocation_fact(department_id);

-- A.3 fc_resource_input_fact：资源投入事实表（V246 CLS 列 staff_ref/hourly_rate 绑面）
CREATE TABLE IF NOT EXISTS ecos_dw.fc_resource_input_fact (
    id                 VARCHAR(36) PRIMARY KEY,
    project_id         VARCHAR(36)  NOT NULL,
    department_id      VARCHAR(36)  NOT NULL,
    period             VARCHAR(7)   NOT NULL,
    staff_ref          VARCHAR(64)  NOT NULL,              -- ST03-A 预登记（V246 asset sec-asset-resource-input-staffref；非哈希，展示 mask 通道由脱敏裁决点单源承担）
    hours              NUMERIC(8,1),
    hourly_rate        NUMERIC(18,2),                      -- ST03-A 预登记（V246 asset sec-asset-resource-input-rate）
    cost_category      VARCHAR(20),
    dq_status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    is_active          SMALLINT     NOT NULL DEFAULT 1,
    batch_id           VARCHAR(36),
    source_ref         VARCHAR(255),
    evidence_ref       VARCHAR(255),
    version_no         VARCHAR(20)  NOT NULL,
    domain             VARCHAR(50)  NOT NULL DEFAULT 'forecast',
    is_deleted         SMALLINT     NOT NULL DEFAULT 0,
    create_time        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by          VARCHAR(100),
    update_by          VARCHAR(100)
);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_rii_active
    ON ecos_dw.fc_resource_input_fact(project_id, department_id, period, staff_ref, is_active);
CREATE INDEX IF NOT EXISTS idx_rii_project ON ecos_dw.fc_resource_input_fact(project_id, period);
CREATE INDEX IF NOT EXISTS idx_rii_dept    ON ecos_dw.fc_resource_input_fact(department_id);

-- ── B. 四角色 seed（角色 id = V246 关键词键，字面与 V246 role_id 同源）─────
INSERT INTO ecos_control.td_role (role_id, role_name, role_code, status, created_by)
VALUES
    ('finance_lead',   '财务负责人',   'FINANCE_LEAD',   'ACTIVE', 'seed-v294'),
    ('project_owner',  '项目负责人',   'PROJECT_OWNER',  'ACTIVE', 'seed-v294'),
    ('dept_user',      '部门用户',     'DEPT_USER',      'ACTIVE', 'seed-v294'),
    ('non_finance',    '非财务角色',   'NON_FINANCE',    'ACTIVE', 'seed-v294')
ON CONFLICT (role_id) DO NOTHING;

-- 注: td_role/td_user/td_user_role/td_user_organization 的建表列族走 created_by/created_time
-- 形态（dev 库 pg_attribute 实查），非 V164 安全三表的 create_by/create_time 形态——
-- 两套列名双轨属 V164~V200 治理过渡遗留（IR01 只写不迁），本 seed 只按库存列名落。

-- ── C. 四演示主体 seed（password 占位 = u-admin 的 60-char bcrypt 值复制；
--      占位缘由已在本脚本头注 D 段与差距台账 D-B4-19 行订正注⑫ 双处登记）──
INSERT INTO ecos_control.td_user (user_id, username, password, status, tenant_id, real_name, created_by)
VALUES
    ('u-fin',  'u-fin',  '$2a$10$aTRk.naQNo2OpLYv8n0YVeSozjz92nbYu.nXKKS8ChD7VCd8Psp4a', 'ACTIVE', NULL, '财务负责人(演示)', 'seed-v294'),
    ('u-p1',   'u-p1',   '$2a$10$aTRk.naQNo2OpLYv8n0YVeSozjz92nbYu.nXKKS8ChD7VCd8Psp4a', 'ACTIVE', NULL, '项目负责人 P1(演示)', 'seed-v294'),
    ('u-d01',  'u-d01',  '$2a$10$aTRk.naQNo2OpLYv8n0YVeSozjz92nbYu.nXKKS8ChD7VCd8Psp4a', 'ACTIVE', NULL, '部门用户 D01(演示)', 'seed-v294'),
    ('u-none', 'u-none', '$2a$10$aTRk.naQNo2OpLYv8n0YVeSozjz92nbYu.nXKKS8ChD7VCd8Psp4a', 'ACTIVE', NULL, '无角色(演示)', 'seed-v294')
ON CONFLICT (user_id) DO NOTHING;

-- ── D. 角色行（五枚；u-none 零行 = 零策略 ⇒ live E2E 断言 1=0 deny）──────
INSERT INTO ecos_control.td_user_role (user_id, role_id)
VALUES
    ('u-fin',  'finance_lead'),
    ('u-p1',   'project_owner'),
    ('u-p1',   'non_finance'),
    ('u-d01',  'dept_user'),
    ('u-d01',  'non_finance')
ON CONFLICT (user_id, role_id) DO NOTHING;

-- ── E. 主机构行（三枚，每演示主体恰好一行 is_primary='1'）────────────────
--  唯一主机构解析链（PrimaryOrganizationRule.uniquePrimaryOrg）要求该用户
--  is_primary='1' 的行数恰为 1，否则 CONTEXT_ORG 谓词 fail-close。
INSERT INTO ecos_control.td_user_organization (user_id, org_id, is_primary)
VALUES
    ('u-fin',  'org_ent_fin', '1'),
    ('u-p1',   'org_ent_ops', '1'),
    ('u-d01',  'org_ent_fin', '1')
ON CONFLICT (user_id, org_id) DO NOTHING;

-- ── F. fc_* 最小演示行（live E2E 行范围取证非空夹具）──────────────────────
-- 项目/机构键全部取 bpa 实存真值（p-ent-001/002/003 × org_ent_ops/org_ent_fin，
-- is_active=1 实查 4 行），保证 V246 三条谓词子查询/等值绑面在 dev 库有行可命：
--   pcf:     4 行（002-ops / 001-ops / 001-fin / 003-fin）
--   caf:     4 行（2-ops 时薪+金额 / 1-ops 时薪+金额 / 1-fin 仅金额 0.4 分摊 / 3-fin 时薪+金额）
--   rii:     3 行（002-ops 张三 / 001-ops 李四 / 003-fin 王五）
INSERT INTO ecos_dw.fc_project_period_fact
    (id, project_id, department_id, period, plan_amount, actual_amount, currency,
     source_type, version_no, create_by, update_by)
VALUES
    ('demo-pcf-002-ops', 'p-ent-002', 'org_ent_ops', '2026-10', 100000.00, 80000.00,    'CNY', 'seed', '1', 'seed-v294', 'seed-v294'),
    ('demo-pcf-001-ops', 'p-ent-001', 'org_ent_ops', '2026-10',  60000.00, 45000.00,    'CNY', 'seed', '1', 'seed-v294', 'seed-v294'),
    ('demo-pcf-001-fin', 'p-ent-001', 'org_ent_fin', '2026-10',  24000.00, 18000.00,    'CNY', 'seed', '1', 'seed-v294', 'seed-v294'),
    ('demo-pcf-003-fin', 'p-ent-003', 'org_ent_fin', '2026-10',  30000.00, 21000.00,    'CNY', 'seed', '1', 'seed-v294', 'seed-v294')
ON CONFLICT (id) DO NOTHING;

INSERT INTO ecos_dw.fc_cost_allocation_fact
    (id, project_id, department_id, period, hourly_rate, amount, allocated_amount, currency,
     source_type, version_no, create_by, update_by)
VALUES
    ('demo-caf-002-ops', 'p-ent-002', 'org_ent_ops', '2026-10', 150.00, 30000.00, 30000.00, 'CNY', 'seed', '1', 'seed-v294', 'seed-v294'),
    ('demo-caf-001-ops', 'p-ent-001', 'org_ent_ops', '2026-10', 150.00, 12000.00, 12000.00, 'CNY', 'seed', '1', 'seed-v294', 'seed-v294'),
    ('demo-caf-001-fin', 'p-ent-001', 'org_ent_fin', '2026-10',    NULL,  6000.00,  6000.00, 'CNY', 'seed', '1', 'seed-v294', 'seed-v294'),
    ('demo-caf-003-fin', 'p-ent-003', 'org_ent_fin', '2026-10', 120.00,  9000.00,  9000.00, 'CNY', 'seed', '1', 'seed-v294', 'seed-v294')
ON CONFLICT (id) DO NOTHING;

INSERT INTO ecos_dw.fc_resource_input_fact
    (id, project_id, department_id, period, staff_ref, hours, hourly_rate, cost_category,
     version_no, create_by, update_by)
VALUES
    ('demo-rii-002-ops', 'p-ent-002', 'org_ent_ops', '2026-10', 'STAFF-1001', 200.0, 150.00, 'DELIVERY', '1', 'seed-v294', 'seed-v294'),
    ('demo-rii-001-ops', 'p-ent-001', 'org_ent_ops', '2026-10', 'STAFF-1002',  80.0, 150.00, 'DELIVERY', '1', 'seed-v294', 'seed-v294'),
    ('demo-rii-003-fin', 'p-ent-003', 'org_ent_fin', '2026-10', 'STAFF-1003',  75.0, 120.00, 'CONSULT',  '1', 'seed-v294', 'seed-v294')
ON CONFLICT (id) DO NOTHING;

-- ── DDL/Seed Lint Self-audit (restate) ─────────────────────────────────
-- [IR03] 零 DROP/DELETE/TRUNCATE，只 CREATE IF NOT EXISTS + INSERT ON CONFLICT ✓
-- [MC01] 主键全 VARCHAR(36/64) 应用侧固定键，零 gen_random_uuid / SERIAL ✓
-- [MC02] 五枚金额/时薪列全 NUMERIC(18,2)；hours NUMERIC(8,1)（V167 同口径）✓
-- [DR06~DR08] 审计五列 + version_no VARCHAR(20) NN + domain VARCHAR(50) NN DEF 'forecast' ✓
-- [ST07] fc_* 落业务域 ecos_dw（ST07 5+1 白名单内）✓  [ST09] 零 FK ✓
-- [ST03-A] 五枚 ST03-A 列 plan_amount/actual_amount/hourly_rate/amount/staff_ref
--   全部在列级加密豁免登记表 §二 已预登记（:30/:31/:32 + V246 asset 字面同源），
--   本脚本落 DDL = 「预登记」转「生效」的前置动作 ✓
-- [幂等] 重复执行全部 IF NOT EXISTS / ON CONFLICT DO NOTHING（含 F 节演示行）✓
