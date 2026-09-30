-- V176 (卷04 §E.2 逐字块 + R-8 ② 拆分): 历史画像 定义态(ecos_knowledge) × 数值统计事实(ecos_dw)
-- 追溯: K-48（画像表形态违规）→ W106/C88；REQ-KB-02 / F04-06；错误码 ECOS-KB-040/041/060
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-8=②（数值事实落业务域 ecos_dw + 写通道唯一(ADR-14)，
--       版本/审批/置信语义留 K 层；profiles/resolve 跨两 schema 组装）；R-9=①（派生统计量列按 ST03-A 逐列登记，不加密）；
--       执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / IR02(手动 psql) / IR03(只加不删；R-12 DROP 例外前置未闭环 ⇒ 本批零 DROP)
--
-- 列分布（§E.2 V176 逐字块 + 其尾注"若 R-8 选数值事实落 ecos_dw，则本表保留定义态 + stats_ref，
-- 数值列迁 ecos_dw.ecos_kb_profile_stats（经 data-engine 写通道）"的落盘化）:
--   ecos_knowledge.ecos_kb_profile        定义态：profile_key / metric_code / group_dims_json / window /
--                                          ci_level / stats_method / confidence / degrade_* /
--                                          applicable_condition / source_query_ref / stats_ref /
--                                          profile_version / status / approved_* / task_id / trace_id / 基线列
--   ecos_dw.ecos_kb_profile_stats         数值统计事实：sample_count / missing_rate / p10 / p50 / p90 /
--                                          mean_value / ci_low / ci_high（写方 = data-engine 代 kb 写，
--                                          kb 不直写 APPLICATION/ecos_dw —— ARCH_SPEC §4.3 + ADR-14）
--   两表间用 stats_ref 弱关联（VARCHAR(36) 定位符），**不建跨 schema FOREIGN KEY**（MC/ST 纪律）。
--
-- 【2026-09-30 校订】独立复核后收口（仅脚本文件，不实跑）：DR07 version_no INTEGER → VARCHAR(20) NOT NULL DEFAULT '1'、
--   DR08 domain VARCHAR(64) → VARCHAR(50) NOT NULL DEFAULT 'default'，两表同改。§E.2 逐字块形态与规范模板冲突时
--   取规范侧（本两表零行、代码未引用，改名/改型无回归风险）。

-- schema 前置（ST07 禁止迁移脚本内 CREATE SCHEMA）：目标 schema = ecos_knowledge / ecos_dw 须已存在；
--   2026-09-30 只读实测本机库 sys_man 内上述 schema 均已实存（登记见 docs/40-实现/DDL迁移脚本落地登记-2026-09-30.md §9.2），故原 CREATE SCHEMA 语句删除，不预置。
CREATE TABLE IF NOT EXISTS ecos_knowledge.ecos_kb_profile (
    id                   VARCHAR(36)   NOT NULL,
    profile_key          VARCHAR(255)  NOT NULL,      -- 规范化：metric|k1=v1|k2=v2（键排序后拼接）
    metric_code          VARCHAR(64)   NOT NULL,      -- 对齐 PRD-03 指标编码（口径只引用不定义，S-4）
    group_dims_json           TEXT          NOT NULL,      -- JSON 文本（MC02：禁 JSONB）
    window_from          VARCHAR(7)    NOT NULL,      -- YYYY-MM
    window_to            VARCHAR(7)    NOT NULL,
    stats_ref            VARCHAR(36),                 -- R-8 ②: 指向 ecos_dw.ecos_kb_profile_stats.id（弱关联）
    ci_level             VARCHAR(10)   NOT NULL DEFAULT '95',
    stats_method         VARCHAR(32)   NOT NULL DEFAULT 'T_APPROX',  -- T_APPROX/BOOTSTRAP
    confidence           VARCHAR(10)   NOT NULL,      -- HIGH/MEDIUM/LOW
    degrade_from         VARCHAR(36),                 -- 自引用：退化来源画像
    degrade_path_json         TEXT,                        -- 退化链（JSON 文本）
    applicable_condition VARCHAR(500),
    source_query_ref     VARCHAR(255),                -- 取数可复现（datanet 查询标识，S-1）
    profile_version      VARCHAR(20)   NOT NULL,
    status               VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',   -- DRAFT/PUBLISHED/SUPERSEDED
    approved_by          VARCHAR(100),
    approved_time        TIMESTAMP,
    task_id              VARCHAR(64),                 -- 生成任务反查（runtime-task）
    trace_id             VARCHAR(64),
    domain               VARCHAR(50)   NOT NULL DEFAULT 'default',
    version_no           VARCHAR(20)   NOT NULL DEFAULT '1',
    is_deleted           SMALLINT      NOT NULL DEFAULT 0,
    create_by            VARCHAR(64)   NOT NULL,
    update_by            VARCHAR(64)   NOT NULL,
    create_time          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ecos_kb_profile PRIMARY KEY (id),
    CONSTRAINT ck_kbp_status CHECK (status IN ('DRAFT','PUBLISHED','SUPERSEDED')),
    CONSTRAINT ck_kbp_conf   CHECK (confidence IN ('HIGH','MEDIUM','LOW'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uniq_kbp_key_ver ON ecos_knowledge.ecos_kb_profile(profile_key, profile_version, is_deleted);
CREATE INDEX IF NOT EXISTS idx_kbp_metric_status ON ecos_knowledge.ecos_kb_profile(metric_code, status, is_deleted);
CREATE INDEX IF NOT EXISTS idx_kbp_domain_window ON ecos_knowledge.ecos_kb_profile(domain, window_from, window_to);

-- ── 2. 数值统计事实（R-8 ②，业务域 ecos_dw；类型/精度承 §E.2 逐字块原列，未改）──
CREATE TABLE IF NOT EXISTS ecos_dw.ecos_kb_profile_stats (
    id                   VARCHAR(36)   NOT NULL,
    profile_id           VARCHAR(36)   NOT NULL,      -- 反向定位 ecos_knowledge.ecos_kb_profile.id（弱关联）
    profile_key          VARCHAR(255)  NOT NULL,      -- 冗余规范化键（ecos_dw 侧自证可分析）
    sample_count         INTEGER       NOT NULL,
    missing_rate         NUMERIC(5,4)  NOT NULL DEFAULT 0,   -- 比率→概率档 NUMERIC(5,4)（承逐字块精度；ST03-A 说明行登记）
    p10                  NUMERIC(18,4),               -- 派生统计量（R-9 ①：ST03-A 逐列登记，不加密）
    p50                  NUMERIC(18,4),               -- 派生统计量（同上）
    p90                  NUMERIC(18,4),               -- 派生统计量（同上）
    mean_value           NUMERIC(18,4),               -- 派生统计量（同上）
    ci_low               NUMERIC(18,4),               -- 派生统计量（同上）
    ci_high              NUMERIC(18,4),               -- 派生统计量（同上）
    window_from          VARCHAR(7)    NOT NULL,      -- 分区窗口冗余（业务域按窗扫描）
    window_to            VARCHAR(7)    NOT NULL,
    stats_batch_id       VARCHAR(64),                 -- 生成批次（runtime-task 反查）
    trace_id             VARCHAR(64),
    domain               VARCHAR(50)   NOT NULL DEFAULT 'default',
    version_no           VARCHAR(20)   NOT NULL DEFAULT '1',
    is_deleted           SMALLINT      NOT NULL DEFAULT 0,
    create_by            VARCHAR(64)   NOT NULL,
    update_by            VARCHAR(64)   NOT NULL,
    create_time          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ecos_kb_profile_stats PRIMARY KEY (id),
    CONSTRAINT uniq_kbps_profile UNIQUE (profile_id, is_deleted)
);
CREATE INDEX IF NOT EXISTS idx_kbps_key_window ON ecos_dw.ecos_kb_profile_stats(profile_key, window_from, window_to);
CREATE INDEX IF NOT EXISTS idx_kbps_metric_domain ON ecos_dw.ecos_kb_profile_stats(domain, trace_id);

COMMENT ON TABLE  ecos_knowledge.ecos_kb_profile IS '历史画像·定义态（REQ-KB-02/F04-06；R-8 ② 拆分：数值统计事实见 ecos_dw.ecos_kb_profile_stats，经 data-engine 写通道）';
COMMENT ON TABLE  ecos_dw.ecos_kb_profile_stats  IS '历史画像·数值统计事实（业务域 ecos_dw；写方=data-engine 代 kb 写(ADR-14)，kb 只读组装 profiles/resolve）';
COMMENT ON COLUMN ecos_dw.ecos_kb_profile_stats.p10        IS '派生统计量 P10（ST03-A 说明行逐列登记，R-9 ①，不加密不豁免业务金额列规则）';
COMMENT ON COLUMN ecos_dw.ecos_kb_profile_stats.p50        IS '派生统计量 P50（ST03-A 逐列登记）';
COMMENT ON COLUMN ecos_dw.ecos_kb_profile_stats.p90        IS '派生统计量 P90（ST03-A 逐列登记）';
COMMENT ON COLUMN ecos_dw.ecos_kb_profile_stats.mean_value IS '派生统计量 均值（ST03-A 逐列登记）';
COMMENT ON COLUMN ecos_dw.ecos_kb_profile_stats.ci_low     IS '置信区间下界（ST03-A 逐列登记）';
COMMENT ON COLUMN ecos_dw.ecos_kb_profile_stats.ci_high    IS '置信区间上界（ST03-A 逐列登记）';
COMMENT ON COLUMN ecos_dw.ecos_kb_profile_stats.missing_rate IS '缺失率（概率形态 NUMERIC(5,4)；ST03-A 逐列登记）';

-- ── 回滚说明 ────────────────────────────────────────────────
-- 两表均为零行新表（本批不实跑），回滚 = 撤销本文件（不执行）；实跑后回滚须走 R-12 ① 双条件前置件，禁止盲目 DROP。

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] schema 限定 ✓  [DR02] 表名从文档定名 ecos_kb_profile（ecos_ 前缀 ✓）；ecos_dw 侧同前缀 ✓  [DR03] 单数 ✓
-- [DR04] group_dims_json/degrade_path_json：`_json` 后缀 + TEXT ✓【2026-09-30 收口】§E.2 逐字块原字面 `group_dims`/`degrade_path` 违 DR04 后缀约定，以红线为准改名（新表零行、零代码引用，实测命中 0），卷04 §E.2 同步勘误；新表零 JSONB ✓
-- [DR05] is_deleted SMALLINT ✓  [DR06] 审计四列+时间 ✓  [DR07] version_no VARCHAR(20) NOT NULL DEFAULT '1' ✓  [DR08] domain VARCHAR(50) NOT NULL DEFAULT 'default' ✓
--        （2026-09-30 收口：§E.2 逐字块 INTEGER / VARCHAR(64) 无 DEFAULT 形态与规范模板冲突，两列均取规范侧）
-- [MC01] PK VARCHAR(36) 应用侧 UUID、无默认值 ✓  零 SERIAL/BIGSERIAL ✓
-- [MC02] 零 JSONB / 零裸 NUMERIC ✓（全部 NUMERIC(p,s)）
-- [MC03] 无 partial index（唯一性用 (profile_key, profile_version, is_deleted) / (profile_id, is_deleted) 复合键）✓
-- [ST03] 金额/统计列 NUMERIC(p,s) ✓  [ST03-A] p10/p50/p90/mean_value/ci_low/ci_high/missing_rate 逐列 COMMENT 登记（R-9 ①）✓
-- [ST07] 定义态落 ecos_knowledge、数值事实落业务域 ecos_dw ✓  跨 schema 无 FOREIGN KEY ✓  [IR03] 零 DROP ✓
