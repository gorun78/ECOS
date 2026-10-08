-- V196 (卷05 附录 E.3 / W125·C107 / MC01 过渡第1步): bigint nextval PK 认知表添加 guid VARCHAR(36) 双写列（只加列，不改主键，禁序列/禁 gen_random_uuid）
-- 追溯: X-25（MC01：目标集 9 张表 9 列 `id bigint default nextval` = ecos_cognitive 6 + public.ecos_wm_scenario + public.kb_cognitive_pipeline + ecos_data.ecos_cognitive_rule；根因 V22:7 BIGSERIAL、V117:22 BIGSERIAL、V146:9 BIGINT GENERATED ALWAYS AS IDENTITY）、W124/W125（认知 14 表中 6 张 PK 为 bigint nextval 的设计-05 口径）
-- 需求依据: 数据库访问规范 MC01（主键 VARCHAR(36) 应用侧生成 UUID，DDL 禁 gen_random_uuid()/自增序列）；E.3 收敛序"平行 GUID 列双写 → 读切换 → 新表以 GUID 为主"
-- 批准: 需求检视报告 §十四 14.1 之 R-13 ① 执行顺序（先限定 + 新表，再双写迁移，禁一次性 DROP）；前置=**应用侧先行**（E.3 V196 行）；执行边界 §14.4 = 脚本文件落地（**不实跑库**）
-- 纪律: IR03(只加不删) / MC01 / MC03 / ST07 / DR01（全 schema 限定）
--
-- ── 覆盖面核对（设计-05 宣称 6 张 ↔ 单源 corpus 可证 nextval 表）──
--   实动 5 张（corpus CREATE 可证 BIGSERIAL/nextval 且实库在场）：
--     A1 public.kb_cognitive_pipeline      （V117:22 BIGSERIAL；X-25/X-23 在列；管线真身，收编轨道=V163）
--     A2 public.ecos_wm_scenario           （V5:30 BIGSERIAL 裸名落 public；X-25 明列）
--     A3 public.ecos_wm_goal               （V5:8  BIGSERIAL 裸名落 public；X-24 与 ecos_cognitive 侧同为 nextval 双违面，本批只动 public 真数据侧，**禁触碰 R-12 ① 错放表**）
--     A4 public.ecos_wm_causal_link        （V5:19 BIGSERIAL 同上）
--     A5 ecos_data.ecos_cognitive_rule      （V22:7 BIGSERIAL；X-25 实测落点 ecos_data（"落点与脚本不符"X-29），按实库落点限定；V22 裸名与实库差异属漂移面，人工核对后可选择性同形补执行）
--   登记不实动 1 张（凑"6 张"口径的第 6 项候选）：
--     B1 public.ecos_scenario_mind          （V146:9 BIGINT GENERATED ALWAYS AS IDENTITY；**X-29 实证库内无表**（迁移在、表不存在）⇒ ALTER 必败，不实动；
--                                            终态由 V188 ecos_cognitive.ecos_cognitive_scenario_mind（VARCHAR(36) PK，本已合规）替代，旧 INSERT 先停写——guid 无对象可加）
--   ecos_cognitive 侧 6 张 nextval 错放表：**零触碰**（R-12 ① 只停写不迁，加列=继续养错放，违裁决）。
--
-- ── guid 生成纪律（MC01/MC02 红线）────────────────────────
--   ① 本脚本**只加列**（nullable，无 DDL 默认值，无 gen_random_uuid()，无序列），不引入任何 DB 侧 UUID 生成；
--   ② 新行 guid 值一律由**应用侧**生成（UUID v4，36 字符）并在双写路径同事务写入（E.3 "应用侧先行"）；
--   ③ 存量行为可重跑、可验证的**确定性别名回填**：guid = 'lgcy_' || <数值id>（≤24 字符，稳定映射，读切换对账期用作旧 PK ↔ 新 GUID 的连接键；
--      终态切换完成后该别名行不要求形态合规，属 knownLegacy 存量面）。
--   ④ 本批**不建 guid 唯一索引**：双写期 guid 允许暂空（读侧 WHERE guid IS NOT NULL 过滤），唯一性门禁止读切换批（应用保证全量写入后）随接续号脚本落地。

-- ── A1. public.kb_cognitive_pipeline ─────────────────────
ALTER TABLE public.kb_cognitive_pipeline ADD COLUMN IF NOT EXISTS guid VARCHAR(36);
COMMENT ON COLUMN public.kb_cognitive_pipeline.guid IS 'MC01 过渡双写列（V196）：应用侧 UUID v4；存量=lgcy_ 前缀确定性别名；权威新表=V163 ecos_cognitive.ecos_cognitive_pipeline，本列仅供旧表停写前对账';
UPDATE public.kb_cognitive_pipeline SET guid = 'lgcy_' || id WHERE guid IS NULL;

-- ── A2. public.ecos_wm_scenario ──────────────────────────
ALTER TABLE public.ecos_wm_scenario ADD COLUMN IF NOT EXISTS guid VARCHAR(36);
COMMENT ON COLUMN public.ecos_wm_scenario.guid IS 'MC01 过渡双写列（V196）：应用侧 UUID v4；存量=lgcy_ 确定性别名；归位终态待 R-13 执行细案（V194 登记）';
UPDATE public.ecos_wm_scenario SET guid = 'lgcy_' || id WHERE guid IS NULL;

-- ── A3. public.ecos_wm_goal ──────────────────────────────
ALTER TABLE public.ecos_wm_goal ADD COLUMN IF NOT EXISTS guid VARCHAR(36);
COMMENT ON COLUMN public.ecos_wm_goal.guid IS 'MC01 过渡双写列（V196）：同 A2；禁据此读写 ecos_cognitive 侧错放同名表（R-12 ①）';
UPDATE public.ecos_wm_goal SET guid = 'lgcy_' || id WHERE guid IS NULL;

-- ── A4. public.ecos_wm_causal_link ───────────────────────
ALTER TABLE public.ecos_wm_causal_link ADD COLUMN IF NOT EXISTS guid VARCHAR(36);
COMMENT ON COLUMN public.ecos_wm_causal_link.guid IS 'MC01 过渡双写列（V196）：同 A2；因果边认知侧只读经 ontology REST（E.4），本列仅收敛旧写面用';
UPDATE public.ecos_wm_causal_link SET guid = 'lgcy_' || id WHERE guid IS NULL;

-- ── A5. ecos_data.ecos_cognitive_rule（实库落点，X-25/X-29）──
ALTER TABLE ecos_data.ecos_cognitive_rule ADD COLUMN IF NOT EXISTS guid VARCHAR(36);
COMMENT ON COLUMN ecos_data.ecos_cognitive_rule.guid IS 'MC01 过渡双写列（V196）：实库落点 ecos_data（V22 裸名建表与实库不符属 X-29 漂移面，人工核对后处理，不在本脚本向 public 补执行）；action_config 二进制 JSON 列的 TEXT 平行列属 V197 轨道';
UPDATE ecos_data.ecos_cognitive_rule SET guid = 'lgcy_' || id WHERE guid IS NULL;

-- ── 执行环境探测（只读，重跑安全；NULL=表缺失 ⇒ 漂移面按 IR02 人工核对，禁脚本兜底建表/跳过）──
SELECT to_regclass('public.kb_cognitive_pipeline')   AS kb_pipeline_present,
       to_regclass('public.ecos_wm_scenario')        AS wm_scenario_present,
       to_regclass('public.ecos_wm_goal')            AS wm_goal_present,
       to_regclass('public.ecos_wm_causal_link')     AS wm_causal_present,
       to_regclass('ecos_data.ecos_cognitive_rule')  AS cog_rule_present,
       to_regclass('public.ecos_scenario_mind')      AS scen_mind_present;  -- 预期 NULL（X-29 漂移实证；B1 不实动依据）

-- ── 回滚说明 ──────────────────────────────────────────────
-- 只加 nullable 列，旧读写路径零影响 ⇒ 等效回滚=应用不再写该列；列本身依 IR03 不 DROP。
-- 幂等性: ADD COLUMN IF NOT EXISTS + UPDATE ... WHERE guid IS NULL 重跑零变化（别名确定性映射不漂移）。

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] 全 schema 限定（public./ecos_data.）✓  [MC01] **零 gen_random_uuid()、零新增序列/BIGSERIAL、零主键变更**；guid VARCHAR(36) 与 UUID 应用侧形态一致 ✓
-- [MC02] 无 JSON 列变化 ✓  [MC03] UPDATE 守卫可重跑，无 RETURNING/ON CONFLICT/ILIKE ✓
-- [ST07] 仅触 public 真身与实测落点表；R-12 ① 错放表零触碰 ✓
-- [IR02] 手动 psql、to_regclass 探测暴露漂移 ✓  [IR03] 只加不删，无 DROP/ALTER 既有列 ✓
-- [W125/E.3] "6 张 bigint nextval PK 表加 guid 双写"= 5 实动 + 1 登记不实动（ ecos_scenario_mind 库内无表、由 V188 替代），口径差已核对 ✓
