-- V299 (#146bd 台账 v1.0.228 §8.2.3「写侧仍产旧值」+ TOP 风险 #2「写侧枚举冻结被自家 DAO 打破」销账 /
--          详设 §8.2.3 + R-15 ① 四终态冻结):
--   ecos_cognitive.ecos_cognitive_hypothesis.status 列默认值由 'VALID' 改写 'PROPOSED'。
--
-- 背景（取证结论，2026-10-31）:
--   ① V191（收敛建表）曾按当时双轨期口径给 status 挂 DEFAULT 'VALID'，
--      V191 头注本身登记"本表不收旧值 / 终态 4 值"——**默认值与正文语义自相矛盾**；
--   ② HypothesisDao.java INSERT 字面量 'VALID' 同违（写侧产旧值，R-15 ① 明示禁止新写入）；
--   ③ dev PG（sys_man）当前 4 行全在四终态（BELIEVED 1 / EVIDENCED 2 / REFUTED 1），
--      无 'VALID'/'INVALIDATED'/'ARCHIVED' 存量行 ⇒ 本脚本需求面 = DDL 默认值对齐，
--      不涉存量值 UPDATE（幂等，无二次直改存量风险）；
--   ④ 四终态词表单源 = cognitive-engine-api `HypothesisStatus`（PROPOSED/EVIDENCED/BELIEVED/REFUTED，
--      R-15 ①）；本脚本默认值是"新假设注册入口的语义起点"，与 HypothesisStore 应用链语义一致
--      （store 现 INSERT 显式写入显式值不受 DDL 默认值影响，改默认值只影响未来
--      仍走 old 签名 / 存量扩展的行为面）。
--
-- 本脚本处置:
--   ALTER TABLE...ALTER COLUMN status SET DEFAULT 'PROPOSED'
--   ——只动默认值，不动类型（VARCHAR(16)）、不动 NOT NULL、不加/删任何列，
--   属"只加不删"红线（IR03）的"不改骨架、只改词表默认值"形态。
--
-- 批准口径: /goal「完整补齐差距、不漏项、无假绿」批内 DDL 收口——本项目台账 §8.2.3
--           与 TOP 风险 #2 指名"写侧产旧值"为现存的最优先真欠账，属主批授权本批
--           （#146bd）归一 DDL 默认值同词表单源。
--
-- 状态: 2026-10-31 dev PG 实跑完成（POWER shell stdin 注入：
--   `cat V299... | docker exec -i ecos-postgres psql -U postgres -d sys_man`）
--   —— 两条语句各回响一次 `ALTER TABLE`/`COMMENT`（非 FAILED ERROR）。
--   幂等复跑同法再跑一次：第二条 ALTER TABLE...SET DEFAULT 无副作用（PG 层面零冲突）。
--   回读取证（information_schema.columns.column_default）：
--     实跑前 = 'VALID'::character varying   （V191 历史签发，与本批头注 背景①一致）
--     实跑后 / 复跑后 = 'PROPOSED'::character varying
--   存量分布前后不变（BELIEVED 1 / EVIDENCED 2 / REFUTED 1，四行全在四终态）。
--
-- 纪律: DR01(小写下划线+schema限定) / DR02(无新表) / DR04(无JSON语义列) /
--       DR05(无布尔列) / DR06(不触审计五列) / MC01(无主键改动) /
--       MC02(白名单类型VARCHAR) / MC03(不建索引) / ST07(落ecos_cognitive) /
--       IR02(手动psql) / IR03(只加不删骨架；默认值改词表字面量不删列不删约束)
--
-- ── 1. status 默认值改 PROPOSED（幂等）───────────────────────────────────────────────────
ALTER TABLE ecos_cognitive.ecos_cognitive_hypothesis
    ALTER COLUMN status SET DEFAULT 'PROPOSED';

-- ── 2. 注释登记（变更单源）────────────────────────────────────────────────────────────────
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_hypothesis.status IS
    '四终态/proposed/evidenced/believed/refuted（R-15 ①；V191 建表时默认 VALID 系双轨期历史签发，
     V299 归一 PROPOSED=应用语义起点；存量 4 行全在四终态无 VALID 行，DEFAULT 改写零影响存量）';

-- ── 3. 为什么用 SET DEFAULT 而不是 UPDATE 存量值 ──────────────────────────────────────
-- ① dev PG 快照：SELECT status, COUNT(*) ... GROUP BY = BELIEVED 1 / EVIDENCED 2 / REFUTED 1，
--    无任何 'VALID' 行，UPDATE 会 0 行命中＝无效且留无用扫描的假绿；
-- ② 默认值改是"未来 INSERT 语义起点"归一，与本批次 #146bd 核心工作面（HypothesisDao 显式
--    写入 PROPOSED）互补形成"DDL ↔ 代码"两侧同词表；
-- ③ 存量行语义守在 read 侧 HypothesisStatus.fromDbValue（该读面已备 VALID/INVALIDATED/ARCHIVED
--    只读别名映射），无需也不应物理覆盖。
--
-- ── 4. 幂等与回滚 ───────────────────────────────────────────────────────────────────────
-- 幂等: SET DEFAULT 是幂等形态，重复执行到同一字面无副作用。
-- 回滚: 若发现四终态语义需修订，可后续再发 V29x SET DEFAULT 回旧值；本脚本不 DROP 任何结构。
--
-- ── DDL Lint Self-audit ─────────────────────────────────────────────────────────────────
-- [DR01] schema 限定 + 小写 ✓  [DR02] 无新表  [DR03] n/a
-- [DR04] 无 JSON 语义列  [DR05] 无布尔列  [DR06] 不触碰审计五列 ✓
-- [DR07] 存量表版本列不动  [DR08] n/a
