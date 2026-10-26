-- V292 (差距分析 P0-6 / R4 Mind 聚合根演进 / 详设 §8.4.2 / #141-B3 认知治理批):
--   ecos_cognitive.ecos_cognitive_mind 由 V187 单 status 演进为 R4 聚合根（六段结构 + 三维状态三轴 + 版本历史），
--   并新建候选更新闸门表 ecos_cognitive.ecos_cognitive_mind_update（W Agent 候选走闸门，禁直写正式资产）。
--
-- 批准口径: 差距分析 B3 行「既成事实表列演进需架构裁决」→ 用户授权 /goal 批（完整补齐差距 + 不漏项无假绿）；
--           演进路径 = 只加不删（IR03）：V187 单 status 列保留停写，原值 stop-not-copy（不回填，读面恒走三轴）。
-- 状态: 仅落迁移脚本文件，未对 ecos-postgres 实跑（实跑属 dev PG 授权门控项，与 V288+ MC02 实收口并批）。
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / IR02(手动 psql) / IR03(只加不删) / DP01 列演进须过 DBA
--
-- ── 设计依据（唯一真源 docs/30-设计/ECOS详细设计说明书-从零实施版 §8.4.2/§8.4.3 R4 轮）──────────
-- 三维状态三轴独立、禁止合并回单 status（R4 取代 V187 单列承载，单列只停写不删）：
--   validity_status  VALID|PENDING_REVIEW|INVALIDATED   （认知有效性）
--   review_status    DRAFT|PENDING|APPROVED             （审查治理）
--   adoption_status  NOT_ADOPTED|ADOPTED|WITHDRAWN      （业务采纳）
-- R4 备注：三轴 CHECK 约束同 V187 头注 4 纪律延后（禁先加约束后改代码造成线上拒写），
--          词表守护由应用层 MindStatusAxes 单源 + MindAggregateSchemaGateTest 静态门各自把守。
-- 六段中：认知上下文/解释结构/演化治理落本表新列；信念/认知假设/证据关联沿既有
--          V127~V129 + V190 mind_id 归属（本批不动），候选变更经新增 mind_update 闸门表流经正。

-- ── 1. 存量表加列（全部 ADD COLUMN IF NOT EXISTS，可重复执行）──────────────────────────────
ALTER TABLE ecos_cognitive.ecos_cognitive_mind
    ADD COLUMN IF NOT EXISTS subject_ref     VARCHAR(255);
ALTER TABLE ecos_cognitive.ecos_cognitive_mind
    ADD COLUMN IF NOT EXISTS scope           VARCHAR(255);
ALTER TABLE ecos_cognitive.ecos_cognitive_mind
    ADD COLUMN IF NOT EXISTS context_ref     VARCHAR(255);
ALTER TABLE ecos_cognitive.ecos_cognitive_mind
    ADD COLUMN IF NOT EXISTS goals_json      TEXT;
ALTER TABLE ecos_cognitive.ecos_cognitive_mind
    ADD COLUMN IF NOT EXISTS key_questions_json TEXT;
ALTER TABLE ecos_cognitive.ecos_cognitive_mind
    ADD COLUMN IF NOT EXISTS explanation     TEXT;
ALTER TABLE ecos_cognitive.ecos_cognitive_mind
    ADD COLUMN IF NOT EXISTS validity_status   VARCHAR(20) NOT NULL DEFAULT 'PENDING_REVIEW';
ALTER TABLE ecos_cognitive.ecos_cognitive_mind
    ADD COLUMN IF NOT EXISTS review_status     VARCHAR(20) NOT NULL DEFAULT 'DRAFT';
ALTER TABLE ecos_cognitive.ecos_cognitive_mind
    ADD COLUMN IF NOT EXISTS adoption_status   VARCHAR(20) NOT NULL DEFAULT 'NOT_ADOPTED';
ALTER TABLE ecos_cognitive.ecos_cognitive_mind
    ADD COLUMN IF NOT EXISTS change_reason     VARCHAR(500);
ALTER TABLE ecos_cognitive.ecos_cognitive_mind
    ADD COLUMN IF NOT EXISTS superseded_by     VARCHAR(36);

-- ── 2. 候选更新闸门表（R4 新增；W Agent 只写 PROPOSED，受控端点受理/驳回；只增不改的受控写面）────
CREATE TABLE IF NOT EXISTS ecos_cognitive.ecos_cognitive_mind_update (
    id                    VARCHAR(36)   NOT NULL,               -- MC01: 应用侧 UUID，DDL 无默认值
    mind_id               VARCHAR(36)   NOT NULL,               -- 指向聚合根 id
    agent_run_id          VARCHAR(36)   NOT NULL,               -- 提出候选的 W Agent 运行号（ecos_ai.ecos_wagent_run.id）
    target_type           VARCHAR(20)   NOT NULL,               -- BELIEF|HYPOTHESIS|CONTEXT|EXPLANATION（词表守护应用层）
    target_id             VARCHAR(36),                          -- 可空（新增型候选）
    change_json           TEXT          NOT NULL,               -- 候选变更全文（禁 MC02 二进制 JSON 列；禁进 WHERE）
    evidence_refs_json    TEXT          NOT NULL DEFAULT '[]',  -- 证据引用数组（来源+版本+极性+理由+时间）
    status                VARCHAR(20)   NOT NULL DEFAULT 'PROPOSED', -- PROPOSED|ACCEPTED|REJECTED（写路径唯一）
    reviewed_by           VARCHAR(64),
    reviewed_at           TIMESTAMP,
    review_reason         VARCHAR(500),
    trace_id              VARCHAR(64),
    -- DR06~DR08 七基线列
    domain                VARCHAR(50)   NOT NULL DEFAULT 'default',
    version_no            VARCHAR(20)   NOT NULL DEFAULT '1',
    is_deleted            SMALLINT      NOT NULL DEFAULT 0,
    create_by             VARCHAR(64)   NOT NULL,
    update_by             VARCHAR(64),
    create_time           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time           TIMESTAMP,
    CONSTRAINT pk_ecos_cognitive_mind_update PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS idx_cmu_mind
    ON ecos_cognitive.ecos_cognitive_mind_update (mind_id, status, is_deleted);
CREATE INDEX IF NOT EXISTS idx_cmu_run
    ON ecos_cognitive.ecos_cognitive_mind_update (agent_run_id, is_deleted);

-- ── 3. 注释登记 ──────────────────────────────────────────────
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.subject_ref IS 'R4 认知主体引用（六段·认知上下文）；V187 前为 NULL=未挂认知上下文';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.scope IS 'R4 认知范围（六段·认知上下文）；无明确认知范围的 Mind 禁注册（应用层把关）';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.context_ref IS 'R4 关联业务情境/Scenario 认知上下文引用';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.goals_json IS 'R4 认知目标数组 TEXT JSON（DR04 _json 后缀）';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.key_questions_json IS 'R4 关键问题数组 TEXT JSON';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.explanation IS 'R4 解释结构（概念关系/因果/机制与推理依据）';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.validity_status IS 'R4 三维状态·认知有效性 VALID|PENDING_REVIEW|INVALIDATED；三轴独立禁合并，CHECK 延后（头注纪律）';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.review_status IS 'R4 三维状态·审查治理 DRAFT|PENDING|APPROVED';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.adoption_status IS 'R4 三维状态·业务采纳 NOT_ADOPTED|ADOPTED|WITHDRAWN';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.change_reason IS 'R4 本版本变化原因（版本历史段）';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.superseded_by IS 'R4 替代版本行 id（新版本行落下时由受控端点回填本列，形成链）';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_mind.status IS 'V187 单状态列【停写】；R4 起治理真值恒走三轴（validity_status/review_status/adoption_status），本列只读存量';
COMMENT ON TABLE ecos_cognitive.ecos_cognitive_mind_update IS 'R4 候选更新闸门：W Agent 提交候选只写 PROPOSED；受控端点受理(ACCEPTED)/驳回(REJECTED)留痕；Agent 运行禁直写 belief/hypothesis';

-- ── 4. 幂等与回滚说明 ────────────────────────────────────────
-- 幂等: ALTER ADD COLUMN IF NOT EXISTS + CREATE TABLE/INDEX IF NOT EXISTS，重复执行无副作用。
-- 回滚: IR03 禁 DROP 列/表；本批新列/新表若未实跑过则库内无对象，无需回滚动作。

-- ── DDL Lint Self-audit ──────────────────────────────────────
-- [DR01] 全列 schema 限定 + 小写下划线 ✓  [DR02] 新表定名 ecos_cognitive_mind_update（ecos_ 前缀）✓
-- [DR03] 单数 ✓  [DR04] 新增 JSON 语义列 goals_json/key_questions_json/change_json/evidence_refs_json 均 TEXT ✓
-- [DR05] is_deleted SMALLINT ✓（本批无新增 is_* 列）  [DR06~DR08] 新表含七基线列，形态与 V187 同族 ✓
-- [MC01] 主键 VARCHAR(36) 应用侧 UUID，DDL 无默认值 ✓  [MC02] 无 JSONB/二进制 JSON/裸 NUMERIC ✓
-- [MC03] 全普通索引、时间列一律普通 TIMESTAMP、无时区变体、无 partial/RLS/分区 ✓  [ST03-A] 无敏感列/无金额列 ✓
-- [ST07] 落 ecos_cognitive（控制域五引擎白名单）✓  [IR02] 手动 psql 执行（Flyway 禁用）✓
-- [IR03] 只加不删：V187 单 status 停写保留，belief/hypo 归属面不动 ✓
