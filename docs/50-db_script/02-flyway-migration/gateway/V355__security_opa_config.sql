-- ============================================================================
-- V355 — 安全中心 OPA 运行参数配置化（G-1 首批）
-- ============================================================================
-- 为什么需要本脚本（差距分析 G-1）：
--   `docs/30-设计/ECOS系统配置现状与差距分析-2026-11-19.md` G-1 登记：
--   「OPA：OpaPolicyService.java:74-75 @Value("${opa.url}") / ${opa.policy-dir}」——
--   安全引擎主力参数硬绑定 yml，不可在线调整、不入门面。
--
-- 本脚本把 OPA 两项**运行参数**播种进 sys_config `config_group=security`：
--   * security.opa.url：OPA 服务地址。播种值 = yml 缺省 http://localhost:8181。
--   * security.opa.policy-dir：策略目录。播种值为空串 = 「沿用解析链」
--     （ECOS_OPA_POLICY_DIR 环境变量 > yml 显式值 > classpath 解包，见 OpaPolicyDirResolver）；
--     置非空值时由 OpaPolicyService 直接采用。
--
-- 口径（保守基线，不借本脚本改行为）：
--   OpaPolicyService 取值链（本批改造后）= sys_config 值（非空）> yml 注入值（@Value 缺省）。
--   种子按 yml 缺省播种 ⇒ 播种前后行为完全一致；运维改值 + refreshCache 即生效。
--   **fail-close 不可配**：opa.fail-close 已按详设-01 W35 训为硬编码 true（铁律 §2.4-8），
--   本脚本刻意不播种任何 fail-open 开关。
--
-- 不播种项（部署形态，留 yml/环境变量）：
--   JWT 私钥（pem 文件注入，属密钥载体非运行参数）、审计哈希链策略（硬编码法定口径）、
--   KMS（密钥服务凭据）、ABAC 决策缓存 TTL（沿用 cache 组口径）。
--
-- 幂等形态：逐行 `INSERT ... SELECT ... WHERE NOT EXISTS`（同 V306/V354），不用 ON CONFLICT。
-- 纪律：IR02 / IR03（只加不删）/ MC01 / 禁 DROP、禁新建容器、禁改既有列。
-- ============================================================================

INSERT INTO sys_config (id, config_group, config_key, config_value, config_type, config_label, config_label_en, description, sort_order, status, default_value)
SELECT 'cfg-security-opa-url', 'security', 'security.opa.url', 'http://localhost:8181', 'string',
       '安全·OPA 服务地址', 'Security OPA URL',
       'OPA 策略引擎服务地址。在线改值即时生效（经 SysConfigService 配置缓存）；播种值 = yml 缺省，播种前后行为一致。fail-close 硬编码不可配（铁律 §2.4-8）', 11, 'active', 'http://localhost:8181'
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'security.opa.url');

INSERT INTO sys_config (id, config_group, config_key, config_value, config_type, config_label, config_label_en, description, sort_order, status, default_value)
SELECT 'cfg-security-opa-policy-dir', 'security', 'security.opa.policy-dir', '', 'string',
       '安全·OPA 策略目录', 'Security OPA Policy Dir',
       'OPA 策略文件目录。空串 = 沿用解析链（ECOS_OPA_POLICY_DIR 环境变量 > yml 显式值 > classpath 解包）；非空 = 直接采用。在线改值即时生效', 12, 'active', ''
WHERE NOT EXISTS (SELECT 1 FROM sys_config WHERE config_key = 'security.opa.policy-dir');
