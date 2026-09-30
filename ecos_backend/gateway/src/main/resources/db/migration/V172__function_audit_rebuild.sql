-- V172 (卷03 §E.3 / F03-06 行4): Function 审计表合规重建 —— 并建新表 ecos_ontology.ecos_function_audit（MC01 UUID 主键 + DR 列 + trace_id），旧表停写不删 + 只读别名视图
-- 追溯: W82/C65（O-14④：旧表 id BIGSERIAL 违 MC01、无 DR05~DR08 列；关联 W81/C64 无 trace_id 承载）；REQ-ONTO-04
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-5=①+③（审计行保留并回填，V171.1 已先做错位纠正）；本组按任务定版落 ecos_ontology（见下"归属说明"）
-- 号段: 整数号接续段（卷02 仅占 V164.1/V165.1/V166~V171 整号，V172 起无冲突）
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST07 / IR02(手动 psql) / IR03(只加不删)
-- 归属说明：文档 R-5① 推荐"审计归主控制侧（与 ecos_audit_log 同侧）"，本组任务书定版为
--   `ecos_ontology.` 前缀 ecos_ 名并建表（并建不搬，跨侧迁移属物理迁移未授权项），两口径下**旧表均停写不删**；
--   终态归属如需改 public/ecos_control，只需改本组脚本 schema 名（R-1 a 写作纪律 = schema 限定，SQL 文本其余不动）。
-- 字段依据：F03-06 Kafka FUNCTION_EXEC detail 清单 {functionId, expression(≤512), objectType, params,
--   resultSummary, durationMs, cacheHit, traceId} + 旧表仍存活列（function_name/caller_id/status/error_message）；
--   cacheHit 按 DR05 定名 is_cache_hit（与事件字段映射由代码侧承担，差异已登记汇报）。
-- 回滚说明：新表为纯增量对象；回滚 = 停写新表 + DROP 本批新增对象需走 PMO 专项（IR03），别名视图可
--   直接 DROP VIEW（非表，不触铁律只加不删的表级红线）。旧表数据零改动。

-- ── 1. 新表（合规形态；旧表 public.ecos_function_audit_log 只停写不迁不删）──
CREATE TABLE IF NOT EXISTS ecos_ontology.ecos_function_audit (
    id             VARCHAR(36) PRIMARY KEY,           -- MC01: 应用侧 UUID，DDL 无默认值（禁 BIGSERIAL/gen_random_uuid）
    function_id    VARCHAR(64),                       -- F03-06 detail.functionId（函数注册键）
    function_name  VARCHAR(255),                      -- V171.1 纠正后的可归因函数名
    expression     TEXT NOT NULL,                     -- 执行表达式（事件侧截断 ≤512，列存全长）
    object_type    VARCHAR(128),                      -- F03-06 detail.objectType（旧列 entity_name 的规范语义名）
    params_json    TEXT,                              -- DR04/MC02: 入参 JSON 文本，只做存取，禁 WHERE/JOIN/索引
    result_summary TEXT,                              -- F03-06 detail.resultSummary（旧列 result_value 的摘要化形态）
    duration_ms    INTEGER,                           -- 类型推断表：毫秒→INTEGER（旧列 execution_time_ms）
    is_cache_hit   SMALLINT NOT NULL DEFAULT 0,       -- DR05 布尔 is_* 形态（事件字段 cacheHit）
    caller_id      VARCHAR(64),                       -- 只取 UserContext（无上下文 = 400 ECOS-ONTO-060 拒收，不落表）
    trace_id       VARCHAR(64),                       -- REQ-NF-04 链路标识（旧表缺失列）
    status         VARCHAR(20) NOT NULL,              -- SUCCESS/ERROR/TIMEOUT/FORBIDDEN（沿用旧值域）
    error_message  TEXT,
    create_time    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, -- DR06 审计五列
    update_time    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by      VARCHAR(100),
    update_by      VARCHAR(100),
    is_deleted     SMALLINT NOT NULL DEFAULT 0,       -- DR05
    version_no     VARCHAR(20) NOT NULL DEFAULT '1',  -- DR07
    domain         VARCHAR(50) NOT NULL DEFAULT 'default' -- DR08
);
COMMENT ON TABLE ecos_ontology.ecos_function_audit IS 'Function 沙箱执行审计表·合规重建（W82/C65；Kafka ecos.audit FUNCTION_EXEC 的本地兜底落点，双失败=拒收 ECOS-ONTO-061 属代码侧）；旧 public.ecos_function_audit_log 停写保留';
CREATE INDEX IF NOT EXISTS idx_ecos_func_audit_status  ON ecos_ontology.ecos_function_audit(status);
CREATE INDEX IF NOT EXISTS idx_ecos_func_audit_caller  ON ecos_ontology.ecos_function_audit(caller_id);
CREATE INDEX IF NOT EXISTS idx_ecos_func_audit_trace   ON ecos_ontology.ecos_function_audit(trace_id);
CREATE INDEX IF NOT EXISTS idx_ecos_func_audit_created ON ecos_ontology.ecos_function_audit(create_time);
CREATE INDEX IF NOT EXISTS idx_ecos_func_audit_domain  ON ecos_ontology.ecos_function_audit(domain);

-- ── 2. 历史行复制（O-13 实测仅 5 行；幂等按确定性 legacy id 不存在才插，R-5③ 保留回填）──
INSERT INTO ecos_ontology.ecos_function_audit
    (id, function_id, function_name, expression, object_type, params_json, result_summary,
     duration_ms, is_cache_hit, caller_id, trace_id, status, error_message,
     create_time, update_time, create_by, update_by, is_deleted, version_no, domain)
SELECT left('fcaud_' || CAST(o.id AS VARCHAR), 36),
       NULL,                                     -- 旧表无函数注册键，不伪造
       o.function_name, o.expression, o.entity_name, NULL, o.result_value,
       o.execution_time_ms, 0, o.caller_id, NULL, o.status, o.error_message,
       COALESCE(o.created_at, CURRENT_TIMESTAMP), COALESCE(o.created_at, CURRENT_TIMESTAMP),
       'system-legacy', 'system-legacy', 0, '1', 'default'
FROM public.ecos_function_audit_log o
WHERE NOT EXISTS (
    SELECT 1 FROM ecos_ontology.ecos_function_audit n
    WHERE n.id = left('fcaud_' || CAST(o.id AS VARCHAR), 36)
);

-- ── 3. 旧表只读别名视图（未切新表前的过渡读面，旧列形态投影新表活数据；仿 V163 先例）──
CREATE OR REPLACE VIEW public.v_ecos_function_audit_log AS
SELECT n.id,
       n.function_name,
       n.expression,
       n.object_type       AS entity_name,
       n.result_summary    AS result_value,
       n.duration_ms       AS execution_time_ms,
       n.caller_id,
       n.status,
       n.error_message,
       n.create_time       AS created_at,
       n.trace_id,
       n.domain,
       n.version_no,
       n.is_deleted
FROM ecos_ontology.ecos_function_audit n;
COMMENT ON VIEW public.v_ecos_function_audit_log IS 'V172 只读别名视图：旧 ecos_function_audit_log 列形态 → 新 ecos_ontology.ecos_function_audit 活数据；消费方终态直读新表（列清单显式，禁 SELECT *，IR04）';

-- ── 4. 旧表停写登记（不 DROP、不改名；knownLegacy 只停写）──
DO $$
BEGIN
    IF to_regclass('public.ecos_function_audit_log') IS NOT NULL THEN
        EXECUTE $q$ COMMENT ON TABLE public.ecos_function_audit_log IS
            'Function 审计旧表（V4.2 建）：自增 BIGINT 主键违 MC01、缺 DR05~DR08 列（W82/C65）；V172 起停写，新写一律 ecos_ontology.ecos_function_audit；历史 5 行已按 R-5③ 复制保留于新表；本表不删（IR03），清理走 PMO 专项' $q$;
    END IF;
END $$;

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] 新表 schema 限定 + 小写下划线 ✓  [DR02] ecos_ 前缀 ✓  [DR03] 单数 ✓
-- [DR04] params_json TEXT（_json 后缀，无 JSON 类型）✓  [DR05] is_cache_hit/is_deleted SMALLINT ✓
-- [DR06] 五列齐 ✓  [DR07] version_no VARCHAR(20) NOT NULL ✓  [DR08] domain ✓
-- [MC01] PK VARCHAR(36) 无默认值，新表无自增序列 ✓
-- [MC02] 无 JSON 类型列、无裸 NUMERIC（ST03-A：无金额/敏感列新增，无登记项）✓
-- [MC03] 复制段用 CAST(o.id AS VARCHAR) 非 ::；索引均非 partial；无 CREATE POLICY / PARTITION BY / timestamptz ✓
-- [ST07] 新表落 ecos_ontology（任务定版口径，归属说明见头注）；视图/注释仅触 public ✓
-- [IR02] 手动 psql，未实跑 ✓  [IR03] 旧表不删不改；依赖 V171.1 先执行（号序 171.1 < 172 保证）✓
