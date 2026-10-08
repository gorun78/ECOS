-- V263 (详设-05 §E.5 配置单源 / F05-11 REQ-COG-04 规格 2 / F05-16 要点 5): 认知域四枚配置键落 sysman 配置门面单表分组
-- 追溯:
--   X-31/W129（认知代码跨域直写 sysman.sys_config ⇒ 已收口到 SysConfigService 门面，代码侧零他域语句）；
--   X-17（`ecos.cognition.*` 配置键全仓零命中 ⇒ 本期建列位与种子行）；
--   X-36（`@Value` 借键 / 全仓未定义键 ⇒ 默认值散落造成"代码默认 vs 库内值"两态漂移）。
-- 需求依据: 详设-05 §E.5「`ecos.cognitive.enabled`、`ecos.cognitive.eval.trend_weight`、`ecos.cognition.domain_alias`、
--           `ecos.cognitive.demo` 一律经 sysman 配置门面消费，禁 `@Value` 散落默认值造成两态漂移」；
--           F05-11 要点 2「别名表落 `sysman` 配置门面（铁律 §十 配置单源），新增别名不改代码」；
--           DG-1（`ecos.cognitive.enabled=false` → 503 `ECOS-COG-500`，用户裁定 A3 原样穿透）。
-- 批准: 需求检视报告 §十四 14.4 执行边界 = **仅落迁移脚本文件，未对 ecos-postgres 实跑**（实跑属未授权项）。
-- 归属纪律: `sys_config` 属 **sysman 域**（控制域主 schema）。本脚本是"配置种子"这一数据治理动作的落地件，
--           由 DDL 单源目录统一维护（铁律 §3.1）；**认知引擎代码侧不得出现 sys_config 的任何语句**
--           （读/写/建全部经 `SysConfigService` 门面，守卫见 CognitiveConfigSingleSourceTest + CognitiveCrossDomainTest）。
-- 与 yml 面的边界（不是两态漂移，而是两类配置）：
--   本表 4 键 = 业务运行期可调（运维在认知配置 UI 改，无需重启）；
--   `ecos.cognitive.scan-interval` / `ecos.cognitive.neo4j-enabled` / `ecos.cognitive.audit-fallback-file`
--   = 部署期形态（调度周期、档位装配、日志落点），留在 Spring yml/环境变量面，不入 sys_config。
--   `ecos.cognitive.demo` 双写声明：`Wave3DemoController` 侧的 `@ConditionalOnProperty(havingValue="true",
--   matchIfMissing=false)` 是 **装配期** 条件（未配置 ⇒ 整个 Controller 不注册，生产 404）；
--   本行是 **运行期** 数据面开关（内置模板 `/scenario/list` 可达性）。两者默认同为"关"⇒ 方向一致、无漂移，
--   登记于 CognitiveConfigSingleSourceTest 的豁免清单（只准出现在这两处）。

-- ── 种子行（幂等：config_key 上有 UNIQUE，ON CONFLICT DO NOTHING ⇒ 重跑零重复、不覆盖运维已改的值）──
INSERT INTO sys_config (id, config_group, config_key, config_value, config_type, config_label, config_label_en, description, sort_order, status) VALUES
  ('cfg-cog-engine-enabled', 'cognitive', 'ecos.cognitive.enabled', 'true', 'boolean',
   '认知引擎总开关', 'Cognitive Engine Switch',
   'false 时 E1~E4 全部 503 ECOS-COG-500 "cognitive engine not enabled"（DG-1，用户裁定 A3 原样穿透，禁 200 空 body）', 1, 'active'),
  ('cfg-cog-demo', 'cognitive', 'ecos.cognitive.demo', 'false', 'boolean',
   'demo 内容开关', 'Demo Content Switch',
   '默认 false = 生产不可达：内置情景模板 /scenario/list 返 404；仅 dev profile 置 true 时可达且逐行 demo=true（F05-16 要点 5 / X-44）', 2, 'active'),
  ('cfg-cog-eval-trend-weight', 'cognitive', 'ecos.cognitive.eval.trend_weight', '1', 'number',
   'E2 趋势权重', 'E2 Trend Weight',
   'E2 算式 score = probability × trend_weight 的第二因子；本期恒 1（PRD-05 §1.1 规格 1 冻结，不实现动态学习）。空白/非数/负值 ⇒ 应用侧回落 1 并在响应 trendWeightSource 标注 DEFAULT', 3, 'active'),
  ('cfg-cog-domain-alias', 'cognitive', 'ecos.cognition.domain_alias', '经营=FORECAST;运营=DETECT', 'string',
   '领域名→能力别名表', 'Domain Capability Alias',
   'belief.domain ↔ MindCapabilityMask 中文别名映射，形态 "domain=CAPABILITY" 以 ; 或换行分隔；新增别名只改本行不改代码（F05-11 要点 2）。未命中 ⇒ capability=null + issue 记录（DG-7，行不丢）', 4, 'active')
ON CONFLICT (config_key) DO NOTHING;

-- ── 注释登记 ──────────────────────────────────────────────
COMMENT ON TABLE sys_config IS '系统配置单表（config_group 分组，铁律 §十 配置单源）；认知域四枚运行期键见 config_group=''cognitive''（V263）';

-- ── 回滚说明 ──────────────────────────────────────────────
-- 数据回滚：应用侧读不到行即回落门面内常量（enabled=true / demo=false / trend_weight=1 / 别名表空），
--           行为与本批一致 ⇒ 回滚 = 运维把 4 行 status 置 'inactive'（本脚本不 DROP、不 DELETE，IR03 纪律）。
-- 幂等性：INSERT ... ON CONFLICT (config_key) DO NOTHING ⇒ 重复执行零报错、零覆盖。
-- 键名唯一性：`ecos.cognition.domain_alias` 的 `cognition`（非 `cognitive`）是详设-05 §E.5/F05-11 与 PRD-05
--           §1.1 的冻结字面量（X-17 已实证该键族全仓零命中 ⇒ 本批首次定义），禁"顺手改名统一"（API/键只增不改）。

-- ── DDL Lint Self-audit ──────────────────────────────────────
-- [DR01] 目标表 sys_config 沿用既有单源脚本非限定形态（V13/V102 同批同形态，限定化属 03 册 schema 归位批）✓
-- [DR04/DR05] 无新增 JSON 列、无新增布尔列（仅数据行）✓
-- [DR06~DR08] 无新表新列 ⇒ 七基线列不适用 ✓
-- [MC01] 无新主键；id 沿用 'cfg-*' 串形态（既有族）✓  [MC02] 无数值列 ✓  [MC03] 无索引/cast/分区 ✓
-- [ST03-A] 无敏感列：4 行值均为开关/权重/别名字符串，非个人数据、非金额 ✓
-- [ST07/ST08] 落主控制 schema 的配置表（sysman 域），不入业务域、不入 ecos_cognitive ✓
-- [跨域] 认知引擎侧零 sys_config 语句（经门面），本文件属 DDL 单源目录而非引擎代码 ✓
-- [IR02] 手动 psql 执行面，本批只落文件 ✓  [IR03] 不 DROP、不改名、不 DELETE ✓
