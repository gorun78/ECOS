-- ============================================================================
-- V358 — ecos_mask_strategy 补内置策略种子（7 类 + none 兜底）
-- ============================================================================
-- 背景（D-3 残留，2026-11-19 第三批）：
--   该表由 `docs/50-db_script/00_init/01_ecos_control.sql` 建于 `ecos_control` schema，
--   结构完整（含 `ck_mask_strategy_code` 限定 8 值），但**长期 0 行**——
--   `DataMaskingService` 仍用硬编码 switch 决定 dataType→规则名，表纯属摆设。
--
-- 本脚本的边界（重要）：
--   表只承载**策略参数**（keep_prefix / keep_suffix / amount_bucket / apply_channels /
--   enabled），**不承载掩码算法**——正则与掩码实现在 `DataMaskingService` 代码内，
--   属不可配置的实现细节。消费端（V358 同批代码）按 `strategy_code` 取参，
--   读不到则回落既有硬编码行为。
--
-- 种子值口径（对齐代码真相，禁臆造）：
--   * 7 类由 `DataMaskingService#ruleForDataType` 的 switch 分支确定：
--     ID_CARD / PHONE / EMAIL / BANK_CARD / AMOUNT / ADDRESS（6 个显式）+ GENERAL（default 兜底）
--   * 第 8 值 `none`（显式不脱敏）不入种子——它是"关闭"语义的保留字，
--     由 `applyMaskingByStrategy("none", ...)` 直接在代码里短路，不需要表行。
--   * `keep_prefix` / `keep_suffix` 目前**不驱动算法**（算法内的保留位是硬编码：
--     银行卡保留前 6 后 4、手机保留前 3 后 4、身份证保留前 4 后 4），
--     故种子只填结构基线值并如实注释"当前不驱动"，避免让人误以为改了立刻生效。
--   * `enabled = 1`（启用）；`domain = 'default'`（全局域）；
--     `tenant_id = 'default'`（本服务不感知租户，用保留字表全局，见头注说明）。
--
-- 幂等：逐行 `INSERT ... SELECT ... WHERE NOT EXISTS`（同 V306/V354），不用 ON CONFLICT。
-- 纪律：IR03 只加不删；MC01 主键应用侧给定；ST07 schema 显式限定 `ecos_control`。
-- ============================================================================

INSERT INTO ecos_control.ecos_mask_strategy (id, tenant_id, strategy_code, keep_prefix, keep_suffix, amount_bucket, apply_channels, enabled, comment, domain, version_no, create_by)
SELECT 'ms-builtin-id-card', 'default', 'ID_CARD', 4, 4, NULL, 'export+ai', 1,
       '身份证号脱敏（保留前4后4，中间定长星号）。参数当前仅登记、不驱动算法——DataMaskingService 内保留位为硬编码常量', 'default', '1', 'system'
WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_mask_strategy WHERE tenant_id='default' AND strategy_code='ID_CARD');

INSERT INTO ecos_control.ecos_mask_strategy (id, tenant_id, strategy_code, keep_prefix, keep_suffix, amount_bucket, apply_channels, enabled, comment, domain, version_no, create_by)
SELECT 'ms-builtin-phone', 'default', 'PHONE', 3, 4, NULL, 'export+ai', 1,
       '手机号脱敏（保留前3后4）。参数当前仅登记、不驱动算法', 'default', '1', 'system'
WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_mask_strategy WHERE tenant_id='default' AND strategy_code='PHONE');

INSERT INTO ecos_control.ecos_mask_strategy (id, tenant_id, strategy_code, keep_prefix, keep_suffix, amount_bucket, apply_channels, enabled, comment, domain, version_no, create_by)
SELECT 'ms-builtin-email', 'default', 'EMAIL', 1, 0, NULL, 'export+ai', 1,
       '邮箱脱敏（保留首字符 + 域名）。参数当前仅登记、不驱动算法', 'default', '1', 'system'
WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_mask_strategy WHERE tenant_id='default' AND strategy_code='EMAIL');

INSERT INTO ecos_control.ecos_mask_strategy (id, tenant_id, strategy_code, keep_prefix, keep_suffix, amount_bucket, apply_channels, enabled, comment, domain, version_no, create_by)
SELECT 'ms-builtin-bank-card', 'default', 'BANK_CARD', 6, 4, NULL, 'export+ai', 1,
       '银行卡号脱敏（保留 BIN 前6 + 末4，银联惯例）。参数当前仅登记、不驱动算法', 'default', '1', 'system'
WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_mask_strategy WHERE tenant_id='default' AND strategy_code='BANK_CARD');

INSERT INTO ecos_control.ecos_mask_strategy (id, tenant_id, strategy_code, keep_prefix, keep_suffix, amount_bucket, apply_channels, enabled, comment, domain, version_no, create_by)
SELECT 'ms-builtin-amount', 'default', 'AMOUNT', NULL, NULL, 'thousand', 'export+ai', 1,
       '金额脱敏（千分位分桶显示）。amount_bucket 当前仅登记、不驱动算法', 'default', '1', 'system'
WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_mask_strategy WHERE tenant_id='default' AND strategy_code='AMOUNT');

INSERT INTO ecos_control.ecos_mask_strategy (id, tenant_id, strategy_code, keep_prefix, keep_suffix, amount_bucket, apply_channels, enabled, comment, domain, version_no, create_by)
SELECT 'ms-builtin-address', 'default', 'ADDRESS', NULL, NULL, NULL, 'export+ai', 1,
       '地址脱敏（保留省市、详址打码）。参数当前仅登记、不驱动算法', 'default', '1', 'system'
WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_mask_strategy WHERE tenant_id='default' AND strategy_code='ADDRESS');

INSERT INTO ecos_control.ecos_mask_strategy (id, tenant_id, strategy_code, keep_prefix, keep_suffix, amount_bucket, apply_channels, enabled, comment, domain, version_no, create_by)
SELECT 'ms-builtin-general', 'default', 'GENERAL', NULL, NULL, NULL, 'export+ai', 1,
       '通用兜底（未知 dataType 走 none 不脱敏）。对应 DataMaskingService#ruleForDataType 的 default 分支', 'default', '1', 'system'
WHERE NOT EXISTS (SELECT 1 FROM ecos_control.ecos_mask_strategy WHERE tenant_id='default' AND strategy_code='GENERAL');
