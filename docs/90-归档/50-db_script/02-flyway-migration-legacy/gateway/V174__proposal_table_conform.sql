-- V174 (卷03 §E.3 / F03-08): 本体变更提案表合规化 —— 并建 schema 限定合规新表 ecos_ontology.ecos_ontology_proposal（UUID PK + DR 列 + JSON→TEXT），旧表停写不删
-- 追溯: W72/C56（O-8：提案 SQL `?::bigint` + `SELECT *` + 裸表名，DDL 侧对应 = 旧表裸名 public 复数表 + JSON 二进制类型列 + BIGSERIAL 主键）；W74/C58（O-7：提案链 0 行，状态机无承载）；REQ-ONTO-01 §1.4
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之批量批准；本册按 R-6/R-7 同款"控制域 ecos_ontology + 只加不删"口径执行（提案=控制域语义资产，§E.1）
-- 号段: 整数号接续段（卷02 止于 V171，V174 无冲突）
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / IR02(手动 psql) / IR03(只加不删)
-- 表名说明：旧表 ecos_ontology_proposals 为复数（违 DR03）且裸名落 public（违 DR01/§四附则1），MC01 主键形态
--   不可原地修复 → 按 V168.1 同法并建合规新表 ecos_ontology.ecos_ontology_proposal（单数 + ecos_ 前缀 +
--   VARCHAR(36) PK）；文档 §E.3 V174 行"规范化（UUID PK、DR 列、状态枚举字典）"即此口径。
--   旧表实测 0 行（O-7），§3 复制段为空操作，仅保幂等与全新环境兜底。
-- 范围说明：W72 的代码侧整改（`?::bigint`→`CAST(? AS BIGINT)`+databaseId 分支、`SELECT *`→显式列、
--   OntologyProposalController Mapper 化 IR01/§五）属后端批次，本脚本只落 DDL 侧；
--   proposal_status 字典 seed（E.3 尾注 ≥3 行示例）属 sysman 字典表批次，未落（见交付汇报）。
-- 回滚说明：新表纯增量对象；回滚 = 代码回退读旧表；旧表数据零改动。

-- ── 1. 合规新表（控制域 ecos_ontology；状态机 PENDING→REVIEWING→APPROVED→VERIFIED→EXECUTED / reject→REJECTED，F03-08）──
CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_ontology_proposal (
    id                     VARCHAR(36) PRIMARY KEY,   -- MC01: 应用侧 UUID（替代旧 BIGSERIAL 自增键）
    domain_code            VARCHAR(128) NOT NULL,     -- E.4 检索列（proposal.domain_code 独立成列）
    proposal_type          VARCHAR(32) NOT NULL,      -- CREATE_ENTITY/ADD_PROPERTY/...（字典 proposal_type 管理）
    target_entity          VARCHAR(256),
    payload_json           TEXT NOT NULL,             -- DR04/MC02: 变更内容 JSON 文本形态（旧列的 JSON 二进制类型形态停写，只做存取禁 WHERE，E.4）
    snapshot_json          TEXT,                      -- DR04: 变更前快照（回滚用）
    status                 VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- E.4 检索列；值域入字典 proposal_status；execute 仅接受 VERIFIED/APPROVED（409 ECOS-ONTO-012）
    author                 VARCHAR(100),
    reviewer               VARCHAR(100),
    reviewer_comment       VARCHAR(512),
    version_id             VARCHAR(64),               -- 关联 ecos_ontology_version.id（目标表现存 VARCHAR(50) 形态，取容纳上限 64；不建 FK）
    optimistic_lock_version INTEGER NOT NULL DEFAULT 1, -- 乐观锁沿用（V108/V4.3 既有机制，O-22 正向保留）
    create_time            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- DR06
    update_time            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by              VARCHAR(100),
    update_by              VARCHAR(100),
    is_deleted             SMALLINT NOT NULL DEFAULT 0, -- DR05
    version_no             VARCHAR(20) NOT NULL DEFAULT '1', -- DR07
    domain                 VARCHAR(50) NOT NULL DEFAULT 'default' -- DR08
);
COMMENT ON TABLE ecos_ontology.ecos_ontology_proposal IS '本体变更提案合规表（W72/W74；V174 起唯一写入口；旧 public.ecos_ontology_proposals 停写）';
CREATE INDEX IF NOT EXISTS idx_ecos_proposal_domain_status ON ecos_ontology.ecos_ontology_proposal(domain_code, status);
CREATE INDEX IF NOT EXISTS idx_ecos_proposal_author        ON ecos_ontology.ecos_ontology_proposal(author);
CREATE INDEX IF NOT EXISTS idx_ecos_proposal_domain        ON ecos_ontology.ecos_ontology_proposal(domain);

-- ── 2. 幂等数据复制（旧表实测 0 行，O-7；全新库若旧表缺失则跳过）────────────
DO $$
BEGIN
    IF to_regclass('public.ecos_ontology_proposals') IS NOT NULL THEN
        EXECUTE $q$
            INSERT INTO ecos_ontology.ecos_ontology_proposal
                (id, domain_code, proposal_type, target_entity, payload_json, snapshot_json, status,
                 author, reviewer, reviewer_comment, version_id, optimistic_lock_version,
                 create_time, update_time, create_by, update_by, is_deleted, version_no, domain)
            SELECT left('eoprop_' || CAST(o.id AS VARCHAR), 36),
                   o.domain_code, o.proposal_type, o.target_entity,
                   CAST(o.payload AS TEXT), CAST(o.snapshot AS TEXT),  -- CAST 标准写法（MC03 禁 ::；PG 目标方言）
                   COALESCE(o.status, 'PENDING'),
                   o.author, o.reviewer, o.reviewer_comment,
                   left(CAST(o.version_id AS VARCHAR), 64),
                   COALESCE(o.optimistic_lock_version, 1),
                   COALESCE(o.created_at, CURRENT_TIMESTAMP), COALESCE(o.updated_at, o.created_at, CURRENT_TIMESTAMP),
                   'system-legacy', 'system-legacy', 0, '1', 'default'
              FROM public.ecos_ontology_proposals o
             WHERE NOT EXISTS (SELECT 1 FROM ecos_ontology.ecos_ontology_proposal n
                               WHERE n.id = left('eoprop_' || CAST(o.id AS VARCHAR), 36))
        $q$;
        RAISE NOTICE 'V174: 旧提案表 → 合规新表复制完成（0 行库为空操作，幂等）';
    ELSE
        RAISE NOTICE 'V174: public.ecos_ontology_proposals 不存在，跳过复制段';
    END IF;
END $$;

-- ── 3. 旧表停写登记（不 DROP、不改名；IR03 只加不删；铁律 §3.1 DROP 例外未启用 = 代码读写未切新表）──
DO $$
BEGIN
    IF to_regclass('public.ecos_ontology_proposals') IS NOT NULL THEN
        EXECUTE $q$ COMMENT ON TABLE public.ecos_ontology_proposals IS
            '提案旧表（V4.1/V108 建）：复数表名违 DR03、裸名落 public 违 DR01、自增主键违 MC01、JSON 二进制类型列违 MC02（W72/C56）；V174 起停写，新写一律 ecos_ontology.ecos_ontology_proposal；不删（IR03），清理走 PMO 专项' $q$;
    END IF;
END $$;

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] schema 限定 + 小写下划线 ✓  [DR02] ecos_ 前缀（文档旧复数名不带合规前缀形态，新表已按 DR02/DR03 定名单数，冲突已汇报）✓
-- [DR03] 单数 proposal ✓  [DR04] payload_json/snapshot_json TEXT ✓  [DR05] is_deleted SMALLINT ✓
-- [DR06] 五列齐 ✓  [DR07] version_no VARCHAR(20) NOT NULL ✓  [DR08] domain ✓
-- [MC01] PK VARCHAR(36) 无默认值，新表无自增序列 ✓
-- [MC02] 无 JSON 类型列；无裸 NUMERIC；ST03-A：无金额/敏感列新增，无登记项 ✓
-- [MC03] 复制段用 CAST 非 ::；索引无 partial/WHERE；无 CREATE POLICY / PARTITION BY / text[] / timestamptz ✓
-- [ST07] 新表落控制域 ecos_ontology ✓  [IR02] 手动 psql，未实跑 ✓  [IR03] 旧表只停写不删 ✓
