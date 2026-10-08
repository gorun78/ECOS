-- V262 (REQ-COG-04 之 P1 / PRD-05 §1.1 E2 规格 1 与 详设-05 §B.4/C.1 承接): 信念表补"点概率"列位，使 E2 score 有真值来源
-- 追溯: W120/C102（E2 计算要素全缺）；本批实测缺口（2026-10-08）：
--       V191 的 `ecos_cognitive.ecos_cognitive_belief` 仅有 `distribution_json TEXT`（分布序列化）+ `version` + `status`
--       ⇒ **无 `probability` 标量列位**；而 PRD-05 §1.1 E2 规格 1 冻结的算式是
--       `score(belief_i) = belief.probability × trend_weight(belief.domain, scenario)`，
--       应用侧因此长期以 `score = 0.0` 硬编码兜底（详设-05 X-44 伪实现族，`BeliefScorer.toScored`），
--       且 `BeliefReader.java:80` 以 `probability=null // R-DDL 补齐列后上填（禁 clamp，禁静默）` 显式挂账。
-- 需求依据: PRD-05 §1.1 E2 规格 1（算式冻结）+ 规格 3（score 由 Java 算术、LLM 零参与，入口单一 `CognitiveEvalCalculator`）
--           REQ-COG-09 规格 4（**无来源数值判 FAIL**，禁 0 替真值）⇒ 本列是本项数值的唯一库侧来源
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-13 ①（认知族归位 ecos_cognitive）；执行边界 §14.4 = **仅落迁移脚本文件，未对 ecos-postgres 实跑**（实跑属未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A（登记行见 docs/40-实现/列级加密豁免登记表）/ ST07（控制域认知引擎 schema）/ IR02(手动 psql) / IR03(旧表只停写、不加列)
--
-- ── 落点选择（为什么只 ALTER ecos_cognitive，不给 public 旧表加列）──────────
--   1. 权威载体 = `ecos_cognitive.ecos_cognitive_belief`（V191 已并建 + 幂等复制段搬运，ST07/R-13 ①）；
--   2. `public.ecos_cognitive_belief`（V129）属双写期只停写面（IR03），加列等于把新契约写回将废弃载体，违"单源"；
--   3. 应用侧收口（本批同批完成）：信念读路径表名改 `ecos_cognitive.` 限定 + 纳入 probability 列，
--      未收口前新列不可见（纯增量、零破坏）。
--
-- ── 列形态依据 ─────────────────────────────────────────────
--   `probability NUMERIC(6,5)`：取值域 0..1 的**点概率**（不是金额，也不是比率百分数）。
--     选 NUMERIC 而非 FLOAT/DOUBLE：MC02 要求数值列带精度、禁二进制浮点参与等值判定；
--     (6,5) 给 0.00000~9.99999，业务域实际只用 [0,1] 子段，宽度足够且与既有 `confidence` 类列同族。
--     **可空（无 DEFAULT）**：语义 = "尚未标定"。禁 DEFAULT 0（那会把"未标定"伪装成"概率为 0"，
--     直接违反 REQ-COG-09 规格 4 与 X-44 收口口径）。NULL 行的处理归应用侧：
--     进 E2 响应的 `missingProbability` 计数并从 Top-N 排序集中剔除（不是给 0 分）。
--   与 `distribution_json` 的关系：分布是形态（Phase 3 时间回放/置信区间用），点概率是从分布取出的
--     **已标定标量**；两者并存不互为替代，应用侧禁由分布自行"算出"点概率冒充标定值（无来源数值判 FAIL）。
--   不建 CHECK(probability BETWEEN 0 AND 1)：枚举/取值域单源在 Java 契约层（R-15 同纪律，V261 同口径），
--     且 CHECK 会让存量 NULL 与越界行的批量补形在 IR02 人工执行面变成阻塞事故。

-- ── 1. 补列（幂等，可空无默认，存量行一律 NULL=未标定）──────────────
ALTER TABLE ecos_cognitive.ecos_cognitive_belief ADD COLUMN IF NOT EXISTS probability NUMERIC(6,5);

-- ── 2. 索引（普通索引；NULL 行不入有效判定，禁条件索引 MC03，is_deleted 并入键代偿）──
CREATE INDEX IF NOT EXISTS idx_cv262_belief_prob ON ecos_cognitive.ecos_cognitive_belief (probability, is_deleted);

-- ── 3. 注释登记 ──────────────────────────────────────────────
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_belief.probability IS
    'E2 算式唯一数值来源：belief 点概率（0..1，NUMERIC(6,5)，MC02 带精度）；NULL=未标定 ⇒ 应用侧从 Top-N 剔除并计入 missingProbability，禁 DEFAULT 0、禁由 distribution_json 反算冒充标定值（REQ-COG-09 规格 4 / X-44）';

-- ── 4. 回滚说明 ──────────────────────────────────────────────
-- 数据回滚：纯增量列。回滚 = 应用侧不再读写该列（E2 退回 DG-7 语义：capability/score 缺源即不申报数值）；
--           如需清列，只允许人工裁决（IR03 禁脚本内 DROP COLUMN）。
-- 幂等性：ALTER ADD COLUMN IF NOT EXISTS + CREATE INDEX IF NOT EXISTS ⇒ 重复执行零报错。

-- ── DDL Lint Self-audit ──────────────────────────────────────
-- [DR01] schema 限定（ecos_cognitive.）+ 小写下划线 ✓  [DR02] 不改表名 ✓  [DR03] 单数 ✓
-- [DR04] 本批无新增 JSON 列（distribution_json 已在 V191）✓  [DR05] 无新增布尔列 ✓
-- [DR06~DR08] 七基线列 V191 已自带，本批不重复 ✓
-- [MC01] 无新主键、无自增序列 ✓
-- [MC02] 数值列带精度 NUMERIC(6,5)；无二进制 JSON ✓
-- [MC03] 无裸 cast / 无条件索引 / 无 RLS / 无分区 / 无数组列 / 无时区戳列 ✓
-- [ST03-A] `probability`=NUMERIC(6,5)：非金额、非个人敏感数据（模型标定的派生统计量），
--          逐列声明已登记 docs/40-实现/列级加密豁免登记表；control-domain 列永不可豁免，本批未触碰 ✓
-- [ST07] 落 ecos_cognitive（控制域认知引擎 schema，V191 权威载体）✓
-- [IR02] 手动 psql 执行面，本批只落文件 ✓  [IR03] public.ecos_cognitive_belief 只停写，不加列、不 DROP、不改名 ✓
-- [待补] 取值域 CHECK 本批不建（枚举/域单源在 Java 契约层，R-15 同纪律）；
--        03 册 E2 响应的 `missingProbability` 字段与 Top-N 剔除逻辑属应用侧同批（详设-05 D.3）
