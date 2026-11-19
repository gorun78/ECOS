-- ============================================================================
-- V352 — ecos_mask_strategy 脱敏策略字典表（F04-03 缺口补齐）
-- ============================================================================
-- 为什么需要本脚本（差距分析 D-3 / F04-03）：
--   `docs/20-需求/ECOS详细设计从零实施版-实现差距分析-2026-10-14.md` F04-03 / §4.2.5 登记：
--   「ecos_mask_strategy 字典表全仓 0 命中，8 类脱敏策略硬编码映射」——
--   security-engine `DataMaskingService.java:64-77` 把 ID_CARD/PHONE/EMAIL/BANK_CARD/
--   AMOUNT/ADDRESS 六类 + GENERAL→none 的 dataType→maskStrategy 映射写死在代码里，
--   策略不可在线维护、NAME/FREE_TEXT 未独立、ADDRESS 系文档外增项。
--
--   字典按规范应由 sysman 统一持有并支持分组消费（铁律 §3.6：字典走 sys_dict/subsystem）。
--   但脱敏策略属安全引擎强类型契约（每条策略含 rule 实现键 + 正则锚点 + 示例），
--   非纯展示型字典项，故落独立策略表与 ecos_security_policy（V148）同形态，
--   由 security-engine 消费、sysman 字典门面只做只读投影（后续批次接）。
--
-- 本脚本只做「建表 + seed 8 策略」；DataMaskingService 改读表消费在后续批次
-- （需先保证表存在，否则消费侧无可读源）。
--
-- schema 落点：主控制 schema（现 public，目标 ecos_control）——
--   与 sys_config/sys_dict/ecos_security_policy 同域（ST07 控制域归口），
--   显式不带 schema 前缀沿用 V148 形态。
--
-- 只加不改（IR03 兼容）：CREATE TABLE IF NOT EXISTS + INSERT ON CONFLICT DO NOTHING。
-- ============================================================================

CREATE TABLE IF NOT EXISTS ecos_mask_strategy (
    id              VARCHAR(64)  PRIMARY KEY,
    strategy_code   VARCHAR(64)  NOT NULL UNIQUE,   -- 策略代码（DataMaskingService 映射目标值：idCard/phone/email/bankCard/amount/address/none/freeText）
    strategy_name   VARCHAR(128) NOT NULL,          -- 中文名
    strategy_name_en VARCHAR(128),                  -- 英文名
    data_types      VARCHAR(255) NOT NULL,          -- 适用的 dataType 集合（逗号分隔，如 ID_CARD；GENERAL 表示兜底）
    rule_key        VARCHAR(64)  NOT NULL,          -- DataMaskingService 内的规则实现键（与硬编码方法一一对应）
    pattern_anchor  VARCHAR(64),                    -- 正则锚点标识（供审计/测试引用，非正则本身）
    example_masked  VARCHAR(255),                   -- 脱敏示例（展示用）
    status          VARCHAR(16)  NOT NULL DEFAULT 'active',
    sort_order      INTEGER      NOT NULL DEFAULT 0,
    description     VARCHAR(500),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE ecos_mask_strategy IS '脱敏策略字典（F04-03）：dataType→脱敏策略映射的可维护事实源，替代 DataMaskingService 硬编码 switch';

INSERT INTO ecos_mask_strategy
    (id, strategy_code, strategy_name, strategy_name_en, data_types, rule_key, pattern_anchor, example_masked, status, sort_order, description)
VALUES
    ('mask-idcard',   'idCard',   '身份证号脱敏',  'ID Card Mask',   'ID_CARD',            'idCard',   'ID_CARD_15_18', '110***********1234',      'active', 10, '身份证 15/18 位，保留前 3 后 4'),
    ('mask-phone',    'phone',    '手机号脱敏',    'Phone Mask',     'PHONE',              'phone',    'PHONE_11',      '138****5678',             'active', 20, '手机号 11 位，保留前 3 后 4'),
    ('mask-email',    'email',    '邮箱脱敏',      'Email Mask',     'EMAIL',              'email',    'EMAIL_LOCAL',   'a***@example.com',        'active', 30, '邮箱保留首字符与域名'),
    ('mask-bankcard', 'bankCard', '银行卡号脱敏',  'Bank Card Mask', 'BANK_CARD',          'bankCard', 'BANK_16_19',   '**** **** **** 1234',     'active', 40, '银行卡 16-19 位，仅保留后 4'),
    ('mask-amount',   'amount',   '金额脱敏',      'Amount Mask',    'AMOUNT',             'amount',   'AMOUNT_NUM',    '¥***,***.00',             'active', 50, '金额数值掩码，保留货币符号与小数格式'),
    ('mask-address',  'address',  '地址脱敏',      'Address Mask',   'ADDRESS',            'address',  'ADDRESS_CN',    '北京市朝阳区***',          'active', 60, '中文地址保留行政区，详细路段掩码'),
    ('mask-freetext', 'freeText', '自由文本脱敏',  'Free Text Mask', 'FREE_TEXT',          'freeText', NULL,            '***',                     'active', 70, '自由文本全掩码（F04-03 独立项，原硬编码缺）'),
    ('mask-none',     'none',     '不脱敏',        'No Masking',     'GENERAL',            'none',     NULL,            '(原样)',                  'active', 99, '兜底策略：未匹配类型不脱敏')
ON CONFLICT (strategy_code) DO NOTHING;
