-- V189 (卷05 附录 E.2 / F05-05 要点·R-15 ①): 假设状态机终态收敛——只加 old_status 留痕列 + 存量映射，CHECK 延后
-- 追溯: W118/C100（三套枚举并存：文档 4 值 / 契约 VALID / 库内 VALID,INVALIDATED,ARCHIVED 7 行，状态机守护从未生效）；需求依据 REQ-COG-01 §1.1 E3、REQ-KB-04、REQ-COG-05（状态机守护）
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-15 ①（终态四值仅对新数据生效 + 存量映射保留 old_status 审计列，不伪造历史）、R-13 ①（存量 belief/hypothesis 原地合规化）；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / IR02(手动 psql) / IR03(只加不删)
--
-- ── 操作对象说明（文档草案与库内实体的取舍，R-13 ① 已批准口径）──
-- 文档 E.2 草案写作 ALTER TABLE ecos_cognitive.cognitive_hypothesis ——该"归位后表名"以
-- V190~V195 批次的物理收敛为前提，而 V190~V195 的分批表清单文档**未定版**（本批不凑数发明）。
-- 按 R-13 ① "存量 belief/hypothesis **原地合规化**"的批准语义，本脚本作用于 V128 实际建的
-- 物理表 public.ecos_cognitive_hypothesis（心智状态三表之一，ADR-9 落盘，现居 public）。
-- public 侧只停写不物理迁（R-1 a）；本脚本仅做加列 + 存量映射两类**只加动作**。
--
-- ── R-15 ① 映射口径（终态 PROPOSED/EVIDENCED/BELIEVED/REFUTED）──
-- 存量 7 行（VALID/INVALIDATED/ARCHIVED）：VALID→BELIEVED、INVALIDATED→REFUTED、ARCHIVED→REFUTED；
-- 原值写入 old_status 审计列留痕（不伪造历史：留痕列可完整回放收敛前状态）。
-- 新数据由应用层 HypothesisStatus 枚举 + Mapper 白名单保证只写四终态；
-- 收敛期读侧用 HypothesisStatusLegacyAlias 只读兼容映射（禁再写旧值）。
-- CHECK 约束**本脚本不建**（待应用全量切换且无旧值后另行补建，防"先加约束后改代码"线上拒写）。

-- ── 1. 加留痕列（幂等）───────────────────────────────────────
ALTER TABLE public.ecos_cognitive_hypothesis
    ADD COLUMN IF NOT EXISTS old_status VARCHAR(20);

COMMENT ON COLUMN public.ecos_cognitive_hypothesis.old_status IS '收敛前遗留状态留痕审计列（R-15 ①：VALID/INVALIDATED/ARCHIVED 原值；NULL=新数据原生四终态）';

-- ── 2. 存量映射（幂等：映射后 status 不再命中遗留值集合，重跑零行受影响）──
UPDATE public.ecos_cognitive_hypothesis
   SET old_status  = status,
       status      = CASE status
                       WHEN 'VALID'       THEN 'BELIEVED'
                       WHEN 'INVALIDATED' THEN 'REFUTED'
                       WHEN 'ARCHIVED'    THEN 'REFUTED'
                     END,
       update_time = CURRENT_TIMESTAMP,
       update_by   = 'migration:V189'
 WHERE status IN ('VALID', 'INVALIDATED', 'ARCHIVED')
   AND old_status IS NULL;
-- 注: status 既有列 VARCHAR(16)，BELIEVED(8)/REFUTED(7) 均兼容，不改列（R9 不动既有列形态）。

-- ── 3. 回滚说明 ──────────────────────────────────────────────
-- 数据回滚（如需）：
--   UPDATE public.ecos_cognitive_hypothesis
--      SET status = old_status, update_by = 'migration:V189-rollback'
--    WHERE old_status IS NOT NULL;
-- 列回滚：old_status 为审计留痕列，按 IR03 只加不删，不回滚（留空值即等效未执行）。
-- 幂等性：加列 IF NOT EXISTS；UPDATE 带 old_status IS NULL 守卫，重复执行不重复改写、不覆盖留痕。

-- ── DDL Lint Self-audit ──────────────────────────────────────
-- [DR01] 全语句 schema 限定 + 小写下划线 ✓  [DR06] 改写行同步 update_time/update_by ✓
-- [MC03] 无裸 cast / 无条件索引 / 无数组列 / 无时区戳列 ✓  [时间默认值] CURRENT_TIMESTAMP ✓
-- [ST03-A] 本脚本无新增金额/比率/概率列 ✓  [ST07] 操作对象 public.ecos_cognitive_hypothesis 属主控制 schema 存量原地合规（白名单内）✓
-- [IR03] 只加不删：不改既有列、不 DROP ✓  [R-15 ①] CHECK 延后 + old_status 留痕 + 只映射存量 ✓
-- [R-13 ①] 存量 hypothesis 原地合规化 ✓  [幂等] ✓  [回滚说明] 已附 ✓
