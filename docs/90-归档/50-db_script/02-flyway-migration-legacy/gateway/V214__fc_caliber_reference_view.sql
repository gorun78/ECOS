-- V214 (卷09 §6.1/§3.3 R-60②): 财务口径 ecos_fc_caliber 降为"引用视图"——口径主权归本体域（卷 03）
-- 追溯: W215↔C197（口径承载缺失）；需求依据 REQ-FC-01（口径与公式 DSL）
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-60=②（口径=指标注册表一种，`ecos_fc_caliber` 降为引用视图不落表）、R-64=①、R-6=①（口径主权归 ontology）；执行边界 §14.4 = 脚本文件落地
-- 状态: 仅落迁移脚本文件，**未对 ecos-postgres 实跑**（实跑属 §14.4 未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A / ST06 / ST07 / ST09 / IR02(手动 psql) / IR03(只加不删)
-- 号段: 接续卷 07 V213（V200~V213 已被卷 06/07 占用），凭证 = 已批准 R-64 ①
-- 被引用对象: 卷 03 `V167.1__ecos_caliber_tables.sql`（本文件并行落地，作者另派）——
--   **被引用表名/列名以卷 03 V167.1 实落为准，若不符需同步修订本视图（属文本口径，不实跑）**。
--   已核对 V167.1 实落：表 ecos_ontology.ecos_caliber / ecos_ontology.ecos_caliber_version，
--   本视图投影列（c.code/name/unit/currency/owner_role/domain；cv.version_no/formula/additive/
--   period_granularity/status/approved_by/approved_at/git_ref 及 DR06 审计列）全部命中，无需修订。
--   视图按卷 03 §E.2 DDL 文本投影 `ecos_ontology.ecos_caliber` + `ecos_caliber_version`；
--   落位 = 控制域 `public`（现主控制 schema；ADR-12 迁 `ecos_control` 后由 MC06 配置注入改限定名）。
-- 消费约束（卷 03 F03-01 / 卷 09 §3.3）: 预测侧只读本视图（GET /api/v1/ontology/calibers/{code}/current 语义等价物），
--   禁复制公式为第二事实源；run 创建时冻结 caliber_snapshot 于 ecos_fc_run.caliber_snapshot_json（TEXT，MC02）。

-- ── 引用视图（一次升版一行；status 供消费方过滤 APPROVED）──────
CREATE OR REPLACE VIEW public.ecos_fc_caliber AS
SELECT
    c.id                    AS caliber_id,           -- VARCHAR(36) 应用侧 UUID（MC01，随主表）
    c.code                  AS caliber_code,         -- 口径业务编码
    c.name                  AS caliber_name,
    c.unit,
    c.currency,
    c.owner_role,
    c.domain,
    cv.id                   AS caliber_version_id,
    cv.version_no           AS caliber_version,      -- 六要素之 caliber_version（C209）
    cv.formula              AS formula_text,         -- 公式 DSL 原文（TEXT，MC02；卷 03 列名 formula）
    cv.formula              AS formula_json,         -- 别名投影：卷 09 §6.1 消费名 formula_json（同列，不改主权）
    cv.additive,                                     -- 1 ADDITIVE / 2 SEMI / 3 NON（卷 03 E.2）
    cv.period_granularity,
    cv.status               AS caliber_status,       -- DRAFT/PENDING_APPROVAL/APPROVED/REJECTED/SUPERSEDED（卷 03 §3.3）
    cv.approved_by,
    cv.approved_at,
    cv.git_ref              AS content_git_ref,      -- 历史版本走 Git 归档（铁律 v2.0 §3.1）
    cv.create_time,
    cv.update_time,
    cv.create_by,
    cv.update_by,
    cv.is_deleted
FROM ecos_ontology.ecos_caliber c
JOIN ecos_ontology.ecos_caliber_version cv
  ON cv.caliber_id = c.id
WHERE c.is_deleted = 0;

COMMENT ON VIEW public.ecos_fc_caliber IS '财务口径引用视图（R-60② 已批准：不落 `ecos_fc_caliber` 表，口径主权在 ecos_ontology 本体域）；预测侧只读消费，禁 UPDATE/INSERT（视图天然单表 JOIN 可更新性由应用层禁写约束）；历史版本走 Git（铁律 §3.1）';

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] 视图与底表均 schema 限定 ✓  [DR02] 视图名 ecos_ 前缀 ✓  [ST07] 引用视图落控制域 public（目标 ecos_control，ADR-12）✓
-- [ST09] 无跨 schema FOREIGN KEY（视图 JOIN 不构成约束）✓  [MC02] formula TEXT 原样投影（JSON 语义列一律 TEXT）✓
-- [IR03] 只建视图，不动卷 03 底表 ✓  [MC01] 主键随底表应用侧 UUID ✓
-- [R-64①] 仅脚本文件落地，未实跑 ✓
