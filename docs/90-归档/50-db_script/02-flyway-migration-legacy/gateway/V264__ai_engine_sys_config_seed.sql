-- V264 (详设-06 F06-07 要点 3 / F06-09 要点 1-2 智能域配置键落 sysman 配置门面单表分组): 会话 token 配额 + 压缩阈值
-- 追溯:
--   X-40（会话配额 0 实现 ⇒ `ecos.ai.session.token_limit` 全仓零命中，代码有读点、库内无键位，
--        读侧一律回落"不启用" ⇒ 配额形同虚设；本批建键位并给出默认 200000）；
--   X-29/X-30（压缩按"条数阈值 20 + 200 字截断"触发，PRD-06 §2.1 要求按 token 阈值 + 结构化摘要
--        ⇒ 阈值/保留窗口/开关三键本批建立，供 F06-09 重写读取，禁引擎内硬编码）。
-- 需求依据: 详设-06 F06-07 要点 3「配额：`ecos.ai.session.token_limit`（经 sysman 配置门面读，
--           铁律 §十/§2.5-5），超限 → 终止 Loop + 可读消息」；
--           F06-09 要点 1「保留 `compress.enabled` 开关，关闭时上报 `compressSkipped=true`（禁静默关闭）」、
--           要点 2「最近 K=6 轮原文保留（`compress.keep_recent`）」。
-- 批准: 需求检视报告 §十四 14.4 执行边界 = **仅落迁移脚本文件，未对 ecos-postgres 实跑**（实跑属未授权项）。
-- 归属纪律: `sys_config` 属 **sysman 域**（控制域主 schema）。智能引擎代码侧不得出现 sys_config 的任何语句
--           （读/写全部经 `SysConfigService` 门面，铁律 §十/§2.5-5）。
-- 与 yml 面的边界: 本表 4 键 = 业务运行期可调（运维在配置 UI 改，refreshCache 后生效，无需重启）；
--           `llm.gateway.*`（provider 基址、默认 key 引用、超时）属部署期形态，留在 Spring yml 面，不入 sys_config。

-- ── 种子行（幂等：config_key 上有 UNIQUE，ON CONFLICT DO NOTHING ⇒ 重跑零重复、不覆盖运维已改的值）──
INSERT INTO sys_config (id, config_group, config_key, config_value, config_type, config_label, config_label_en, description, sort_order, status) VALUES
  ('cfg-ai-session-token-limit', 'ai-engine', 'ecos.ai.session.token_limit', '200000', 'number',
   '会话 token 上限', 'Session Token Limit',
   '会话累计 token 用量上限（输入+输出，真值来自 llm-gateway 唯一计量点）。达到即终止 Agent Loop 并回可读消息 + 诊断码 ECOS-AI-413，禁继续静默消耗（F06-07 要点 3）。0 或未配置 = 不启用配额；读数失败时同样不判定但必记 ERROR（成本控制项，禁静默降级）', 1, 'active'),
  ('cfg-ai-compress-enabled', 'ai-engine', 'ecos.ai.session.compress.enabled', 'true', 'boolean',
   '会话压缩开关', 'Session Compression Switch',
   'false = 跳过压缩但必须上报 compressSkipped=true（F06-09 要点 1，禁静默关闭）；长期关闭会使上下文逼近模型上限并由 CONTEXT_OVERFLOW(413) 兜底', 2, 'active'),
  ('cfg-ai-compress-threshold', 'ai-engine', 'ecos.ai.session.compress.threshold', '0.6', 'number',
   '压缩触发阈值', 'Compression Threshold',
   '触发比例：estimatedContextTokens > modelLimit × 本值 时压缩（F06-09 要点 1，替代旧"20 条"计数口径）。modelLimit 取自 llm-gateway 模型目录，禁引擎内硬编码（联动 X-71 配置单源）', 3, 'active'),
  ('cfg-ai-compress-keep-recent', 'ai-engine', 'ecos.ai.session.compress.keep_recent', '6', 'number',
   '压缩保留窗口', 'Compression Keep Recent',
   '最近 K 轮原文保留（F06-09 要点 2，默认 6）；更早轮交 llm-gateway 生成结构化摘要（confirmedFacts/userPreferences/openQuestions/keyRefs），摘要失败退化为截断最早轮并记 compression_mode=TRUNCATE_FALLBACK 供抽检', 4, 'active')
ON CONFLICT (config_key) DO NOTHING;

-- ── 注释登记 ──────────────────────────────────────────────
COMMENT ON TABLE sys_config IS '系统配置单表（config_group 分组，铁律 §十 配置单源）；智能域四枚运行期键见 config_group=''ai-engine''（V264）';

-- ── 回滚说明 ──────────────────────────────────────────────
-- 数据回滚：应用侧读不到行即回落"不启用"（token_limit=0 ⇒ 配额关闭；compress.enabled 缺省按代码常量走
--           保守分支），不产生隐性放行。回滚 = 运维把 4 行 status 置 'inactive'（本脚本不 DROP、不 DELETE，IR03）。
-- 幂等性：INSERT ... ON CONFLICT (config_key) DO NOTHING ⇒ 重复执行零报错、零覆盖。
-- 键名唯一性：四键字面量取自详设-06 F06-07/F06-09 的冻结写法，禁"顺手改名统一"（键只增不改）。

-- ── DDL Lint Self-audit ──────────────────────────────────────
-- [DR01] 目标表 sys_config 沿用既有单源脚本非限定形态（V263 同批同形态，限定化属 schema 归位批）✓
-- [DR04/DR05] 无新增 JSON 列、无新增布尔列（仅数据行）✓
-- [DR06~DR08] 无新表新列 ⇒ 七基线列不适用 ✓
-- [MC01] 无新主键；id 沿用 'cfg-*' 串形态（既有族）✓  [MC02] 无数值列（四行值均为字符串形态的开关/阈值）✓  [MC03] 无索引/cast/分区 ✓
-- [ST03-A] 无敏感列：4 行值均为开关/阈值/上限，非个人数据、非金额、不含任何密钥引用 ✓
-- [ST07/ST08] 落主控制 schema 的配置表（sysman 域），不入业务域、不入 ecos_ai ✓
-- [跨域] 智能引擎侧零 sys_config 语句（经门面），本文件属 DDL 单源目录而非引擎代码 ✓
-- [IR02] 手动 psql 执行面，本批只落文件 ✓  [IR03] 不 DROP、不改名、不 DELETE ✓
