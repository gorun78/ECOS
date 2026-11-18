-- V286 (V283 金额列落域订正 / 用户裁决「建，但把金额四列改形态」): ecos_dw.ecos_application_wagent_option_amount
-- ============================================================
-- 存在理由（不是新语义表，是 V283 的一半搬过来）:
--   V283 的 `ecos_ai.ecos_wagent_decision_option` 原按详设 §10.4.2 把四枚金额列
--   （profit_delta / revenue_delta / cost_delta / cash_impact）建成 NUMERIC(18,2) 落在 **控制域** ecos_ai，
--   直接触既有纪律：《列级加密豁免登记表》§二·续·补三（2026-10-10 W272 批）「控制域 ecos_ai 禁落金额语义，
--   金额实列唯一轨道为业务域 ecos_dw」「不得就地升级声明」+ ST03-A ④「控制域列永不可豁免」。
--   用户 2026-10-14 裁决 = 表照建、金额四列改形态 ⇒ 本脚本承接金额面，V283 只留 `amount_ref` 引用位。
--
-- 落域判据: 数据湖存储分层规范 v2.0 §「应用 APPLICATION（聚合/指标）→ PG ecos_dw」——
--   方案金额影响是面向场景的聚合值，非定义态资产，落业务域 APPLICATION 层。
--   写通道义务（铁律 v2.0 §0.6 + ADR-14）: 本表由 data-engine 唯一写通道写入；
--   wagent 决策面代码批次（差距文档 B3）落地时**不得**绕过该通道直写本表（当前四表零消费方 ⇒ 义务先行登记）。
--
-- 列清单单源 = 设计 §10.4.2 原四列语义逐字搬入 + 通用列（DR05~DR08）；
--   本表**不新造**设计未列的金额列（如币种/单位/含税位）——缺即缺，登记为 D2，禁臆造。
-- 状态: **已按 IR02 手动 psql 对 ecos-postgres/sys_man 实跑**（凭证见 docs/40-实现/DDL迁移脚本落地登记-2026-09-30.md §十五）。
-- 纪律: IR02 / IR03(只加不删) / DR01~DR08 / MC01(VARCHAR(36) PK 应用侧 UUID) / MC02(无 JSONB) /
--       MC03(无 PG 专有语法) / ST03-A(金额列逐列 COMMENT + 豁免登记) / ST07(业务域 ecos_dw) / ST09(无跨 schema FK)
-- ============================================================

CREATE TABLE IF NOT EXISTS ecos_dw.ecos_application_wagent_option_amount (
    option_id           VARCHAR(36) NOT NULL,                  -- 逻辑引用 ecos_ai.ecos_wagent_decision_option.id（ST09 不建 FK）
    tenant_id           VARCHAR(64) NOT NULL,                  -- §10.4.1 通用列
    org_id              VARCHAR(64),
    profit_delta        NUMERIC(18,2),                         -- 利润影响额（金额唯一形态 NUMERIC(p,s)；受 ST03/ST03-A 管辖）
    revenue_delta       NUMERIC(18,2),                         -- 收入影响额
    cost_delta          NUMERIC(18,2),                         -- 成本影响额
    cash_impact         NUMERIC(18,2),                         -- 现金影响额
    id                  VARCHAR(36) PRIMARY KEY,               -- MC01 应用侧生成 UUID，DDL 零默认函数
    domain              VARCHAR(50) NOT NULL DEFAULT 'entmgmt',
    version_no          VARCHAR(20) NOT NULL,                  -- DR07 应用侧写入方生成（同 V283 D3 家规）
    create_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by           VARCHAR(100),
    update_by           VARCHAR(100),
    is_deleted          SMALLINT NOT NULL DEFAULT 0,           -- DR05/DR06
    trace_id            VARCHAR(64),
    CONSTRAINT uk_app_wagent_option_amount UNIQUE (option_id)  -- 与 V283 方案行 1:1
);

COMMENT ON TABLE ecos_dw.ecos_application_wagent_option_amount IS
    'W 域备选方案的金额影响实列载体（V286；自 V283 控制域搬出，用户 2026-10-14 裁决）。四列均明文存储，豁免依据见《列级加密豁免登记表》§二·续·补五；写通道 = data-engine 唯一写通道';
COMMENT ON COLUMN ecos_dw.ecos_application_wagent_option_amount.profit_delta IS
    '利润影响额（方案 vs 基准差额，可负）：明文 NUMERIC(18,2)，受 RLS+CLS+审计三重保护 + 导出/AI 通道经 security-engine 脱敏裁决；豁免登记 = 登记表 §二·续·补五，批准人 = 用户 2026-10-14 裁决';
COMMENT ON COLUMN ecos_dw.ecos_application_wagent_option_amount.revenue_delta IS
    '收入影响额（同上口径与豁免条件；登记表 §二·续·补五逐列行）';
COMMENT ON COLUMN ecos_dw.ecos_application_wagent_option_amount.cost_delta IS
    '成本影响额（同上口径与豁免条件；登记表 §二·续·补五逐列行）';
COMMENT ON COLUMN ecos_dw.ecos_application_wagent_option_amount.cash_impact IS
    '现金影响额（同上口径与豁免条件；登记表 §二·续·补五逐列行）';
COMMENT ON COLUMN ecos_dw.ecos_application_wagent_option_amount.option_id IS
    '逻辑引用 ecos_ai.ecos_wagent_decision_option.id（V283）；反向引用位 = 该表 amount_ref 列。两列同值，禁第三处存金额真相（防同列双权威）';

CREATE INDEX IF NOT EXISTS idx_app_wagent_option_amount_tenant
    ON ecos_dw.ecos_application_wagent_option_amount (tenant_id, is_deleted);

-- ── 偏差登记 ──────────────────────────────────────────────
-- D1 [表非设计原文] `ecos_application_wagent_option_amount` 这个表名在详设中**不存在**：
--   设计把四列写在 ecos_ai 表内。本表是"规范纪律（控制域禁金额）+ 用户裁决（金额列改形态）"的产物，
--   属设计↔实现漂移的新增一行，须在台账 §C 与设计册漂移册回写，不得声称与设计 §10.4.2 逐字一致。
-- D2 [缺列如实登记] 设计未给币种/单位/含税口径/正负号约定 ⇒ 本表不臆造。四列语义"差值"由列名 `_delta`
--   与 cash_impact 承载，正负号约定待 wagent 决策面代码批次（B3）在枚举单源时一并冻结。
-- D3 [domain 默认 entmgmt] 与 V283 的 `DEFAULT 'default'` 不同：本表属企业经营管控演示数据集同源链路
--   的金额面，DR08 分组取 `entmgmt`；若后续有多域消费方，须由写入方显式传值，不靠默认值。
-- D4 [RLS 阻塞未解] 豁免登记表 §二·续·补四 已披露 `TBD-卷09-RLS种子批`——本表建好但**未加任何 RLS 策略**
--   ⇒ ST03-A 条件①（RLS+CLS+审计三重保护）目前只满足"表在业务域 + 审计面存在"，RLS 种子批未落。
--   如实登记为预阻塞项：本表当前零行、零消费方 ⇒ 无实际暴露面；任何写入前必须先补 RLS 种子。
-- ──────────────────────────────────────────────────────────

-- ── 回滚说明 ──────────────────────────────────────────────
-- 幂等：CREATE TABLE/INDEX IF NOT EXISTS + COMMENT，重复执行零变更零报错。
-- 实跑影响面：新增 1 表 1 索引 1 唯一约束，0 行；不触碰存量；无 RLS 语句、无 DROP（IR03）。
-- 回滚 = 应用侧不启用本表（V283 的 amount_ref 留 NULL 即金额面未核算，语义自洽）。
--
-- ── DDL Lint Self-audit ──────────────────────────────────────
-- [DR01] ecos_dw. 限定 ✓  [DR02] ecos_ 前缀 ✓（人工核对：lint DR02 对 schema 限定 CREATE 空判，见登记表 §十四 D-16）
-- [DR03] 表单数 ✓  [DR04] 本表无 JSON 语义列 ⇒ 无 _json 后缀需求 ✓  [DR05] is_deleted SMALLINT ✓
-- [DR06] create_time/update_time ✓  [DR07] version_no VARCHAR(20) NOT NULL 无默认（应用侧生成）✓  [DR08] domain ✓
-- [MC01] VARCHAR(36) PK，无 gen_random_uuid()、无序列 ✓  [MC02] 无 JSONB；四枚金额全 NUMERIC(18,2) 在白名单 ✓
-- [MC03] 无 PG 专有语法（无 partial index / 无 JSONB / 无函数默认）✓
-- [ST03-A] 四枚金额列逐列 COMMENT ON COLUMN + 豁免登记 = 登记表 §二·续·补五 ✓（D4 披露 RLS 种子批未解）
-- [ST07] ecos_dw 业务域 ✓（金额实列唯一轨道）  [ST09] 无跨 schema FK，option_id 全逻辑引用 ✓
-- [ST04/05] 本脚本不写 RLS/CLS 策略（挂 D4 待批）  [ST06] 非审计表 ✓
-- [IR02] 手动 psql 实跑，Flyway 保持禁用 ✓  [IR03] 只加不删，脚本零 DROP ✓
