-- V202 (卷06 §六 E 章 6.2 新增 DDL): 会话消息/会话表压缩治理附加列（F06-09：逻辑删除 + 摘要批次 + token 预算）
-- 追溯: W151/C133；需求依据 REQ-AI-02
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-20=①、R-19=①+② 并行；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / ST09 / IR02(手动 psql) / IR03(只加不删)
-- R-20 ① 核心口径（已批准）:
--   1) 压缩语义切换（条数阈值 + 物理 DELETE → token 阈值 + 逻辑归档）**仅对新数据生效**；
--   2) 存量仅 6 行消息（X-57），历史已删行不可恢复，**不重建、不做兼容开关**（拒绝 ②全量重建/③双语义开关）；
--   3) `message_count` 一次性回填对账（解 X-59：session 合计 ≥18 与实存 6 行不符），本脚本 §3 幂等实现。
-- R-19 归属口径（目标表落点判定，文档以占位 <AI_SCHEMA>. 书写）:
--   实测（分册 06 §6.1）：`sys_agent_message`/`sys_agent_session` **真身在 public**（ecos_ai 侧不存在），
--   代码裸表名依赖库级 search_path（V47）。R-19 裁"public 在用真身只停写**不迁**（不搬表）、
--   ecos_ai 50 空壳待 R-12 同批处置"⇒ 本批压缩治理列只能加在**当前唯一读写载体** public 上；
--   ADD COLUMN 属"只加不删"（IR03 允许面），不是搬表/改归属；schema 归位 ecos_ai 待 R-19/R-12
--   后续批次（前置 = W160 schema 限定改造 + R-22 撤 search_path），届时本脚本列面随迁。
--   ⚠ 若用户裁决"真身表零 ALTER"，则 V202~V203 需整批改走 ecos_ai 新表 + 读写切换路线（超出本批范围）。
-- 上线: psql -U postgres -d sys_man -f V202__ai_session_compression_governance.sql

-- ── 1. 消息表附加列（幂等：ADD COLUMN IF NOT EXISTS）──────
ALTER TABLE public.sys_agent_message ADD COLUMN IF NOT EXISTS compression_batch_id VARCHAR(36);      -- 指向压缩批次头（逻辑引用，跨 schema 不建 FK）
ALTER TABLE public.sys_agent_message ADD COLUMN IF NOT EXISTS compressed          SMALLINT NOT NULL DEFAULT 0;   -- 已归档标记（新语义：不物理删，置 1）
ALTER TABLE public.sys_agent_message ADD COLUMN IF NOT EXISTS is_deleted          SMALLINT NOT NULL DEFAULT 0;   -- DR05 逻辑删除（替代物理 DELETE，X-31）
ALTER TABLE public.sys_agent_message ADD COLUMN IF NOT EXISTS tool_call_ref_ids   TEXT;               -- 引用完整性（F06-09-③，逗号分引用串）
ALTER TABLE public.sys_agent_message ADD COLUMN IF NOT EXISTS key_refs            TEXT;               -- runId/caliberId/证据引用（禁摘要丢弃，F06-09）
ALTER TABLE public.sys_agent_message ADD COLUMN IF NOT EXISTS token_count         INTEGER;            -- 单条 token 计量（压缩判定输入）

-- ── 2. 会话表附加列 ──────────────────────────────────────
ALTER TABLE public.sys_agent_session ADD COLUMN IF NOT EXISTS total_prompt_tokens     INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.sys_agent_session ADD COLUMN IF NOT EXISTS total_completion_tokens INTEGER NOT NULL DEFAULT 0;
ALTER TABLE public.sys_agent_session ADD COLUMN IF NOT EXISTS compression_mode        VARCHAR(24);    -- LLM_SUMMARY | TRUNCATE_FALLBACK（SSE done 帧同源）
ALTER TABLE public.sys_agent_session ADD COLUMN IF NOT EXISTS update_time             TIMESTAMP;      -- DR06 补齐（真身表历史缺列，只加不删）
ALTER TABLE public.sys_agent_session ADD COLUMN IF NOT EXISTS is_deleted              SMALLINT NOT NULL DEFAULT 0;  -- DR05

-- ── 3. message_count 一次性回填对账（R-20 ③，幂等）────────
-- 语义：message_count := 该会话当前活消息数（未逻辑删、未压缩归档）。
-- 幂等性：纯重算赋值，重复执行结果不变；新压缩链路生效后同一公式继续成立
--        （归档消息 compressed=1/is_deleted=1 不计入，与 token 语义并存不冲突）。
UPDATE public.sys_agent_session s
SET    message_count = (SELECT CAST(COUNT(*) AS INTEGER)
                        FROM public.sys_agent_message m
                        WHERE m.session_id = s.id
                          AND COALESCE(m.compressed, 0) = 0
                          AND COALESCE(m.is_deleted, 0) = 0)
WHERE  COALESCE(s.is_deleted, 0) = 0;

-- ── 4. 热路径索引（MC03：普通索引，无 WHERE 子句）─────────
CREATE INDEX IF NOT EXISTS idx_sam_session_active
    ON public.sys_agent_message (session_id, compressed, is_deleted);

-- ── 回滚说明 ──────────────────────────────────────────────
-- 1) 附加列全部带默认值，旧写读代码（SELECT 显式列名 / INSERT 显式列名）不受影响，回滚 = 应用侧回退即可；
-- 2) 列本身不 DROP（IR03 只加不删）；
-- 3) message_count 回填是"对账修正"（把 ≥18 的虚高值修正为实存活消息数），若需回退只能恢复旧值——
--    属**不可逆语义变更**，故执行前先跑只读对账快照：
--    SELECT s.id, s.message_count AS old_count,
--           (SELECT COUNT(*) FROM public.sys_agent_message m WHERE m.session_id = s.id) AS msg_rows
--    FROM public.sys_agent_session s;  -- 输出留存后方可实跑（实跑本身属 §14.4 未授权项）

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] public 显式限定（真身现址，R-19 注记待归位）✓  [DR05] compressed/is_deleted SMALLINT ✓
-- [DR04] tool_call_ref_ids/key_refs 为逗号分引用串（TEXT），非 JSON 检索列 ✓
-- [DR06] update_time/is_deleted 补齐审计列 ✓（存量表其余基线列缺项随 R-19 归位批处置）
-- [MC02] 零 JSONB、零裸 NUMERIC ✓  [MC03] 索引无 WHERE/无 POLICY/无 PARTITION/无裸 cast（COUNT 用 CAST(x AS INTEGER)）✓
-- [R-20①] 仅新数据生效、存量 6 行不重建、无兼容开关；回填幂等 + 回滚说明 ✓
-- [IR03] 零 DROP；ADD COLUMN 属"只加"允许面 ✓  [ST07] 目标表归属冲突已按 R-19 登记（public 真身待归位批）
