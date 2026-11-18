-- V261 (REQ-COG-07 / PRD-05 §6.2 之 P0 模型注册中心契约，详设-05 F05 系列承接): 认知模型注册表补"CM 类型 × 版本不可变 × 四类发布门禁"列面
-- 追溯: W124/C106（认知真身在 public）、X-23（model 1 行在 public，V192 已并建 ecos_cognitive 镜像）；
--       本册实测缺口（2026-10-08）：V192 的 `ecos_cognitive.ecos_cognitive_model` 仅有
--       `model_type VARCHAR(32)`（FORECAST/CAUSAL/SIMULATION/DECISION）+ `version` + `features_json` + `backtest_score` + `status active/retired`
--       ⇒ **无 CM-01~07 枚举位、无 content_hash、无 supersedes、无四门禁列**，REQ-COG-07 规格 1/3/4 在库侧零承载（M0：结构必败）。
-- 需求依据: PRD-05 §6.2 REQ-COG-07 规格 1（枚举冻结 CM-01~07）/ 规格 3（已发布版本行不可变，变更只准新建版本行并 `supersedes` 指旧版）/ 规格 4（contract·regression·backtest·calibration 四门禁全通过才可 PUBLISHED）/ 规格 5（注册表落 ecos_cognitive，**不建** `ecos_ai.ecos_wagent_cognitive_model` 副本 — ADR-17；实测全仓 DDL/Java/XML 零命中 ⇒ 规格 5 天然满足，静态扫描守护）
-- 批准: 需求检视报告 §十四 14.1（2026-09-29）之 R-13 ①（认知族归位 ecos_cognitive）+ PRD-05 §6.2 P0 归属；执行边界 §14.4 = **仅落迁移脚本文件，未对 ecos-postgres 实跑**（实跑属未授权项，需逐项再确认）
-- 纪律: DR01~DR08 / MC01~MC03 / ST03-A（见 docs/40-实现/列级加密豁免登记表）/ ST07（控制域认知引擎 schema）/ IR02(手动 psql) / IR03(旧表只停写、不 DROP 不改名、不加列)
--
-- ── 落点选择（为什么只 ALTER 新表，不给 public 旧表加列）──────────
--   1. REQ-COG-07 规格 5 + ST07 定权威载体 = `ecos_cognitive.ecos_cognitive_model`（V192 已建 + 幂等复制段搬运）；
--   2. `public.ecos_cognitive_model` 属双写期只停写面（IR03），给它加列等于把新契约写回将废弃载体，违"单源"；
--   3. 应用侧待办（本批 #86）：`ModelRegistryDao.java:25/40/47/56` 现以**未限定表名** `ecos_cognitive_model`
--      且以旧列名 `features` 读写 ⇒ 实际打的是 public 旧形态。收口动作 = 表名改 `ecos_cognitive.` 限定 +
--      `features`→`features_json` + 新增列纳入 DO/Mapper；未收口前新列对本仓 Java 不可见（无破坏，纯增量）。
--
-- ── 列形态依据 ─────────────────────────────────────────────
--   `cognitive_model_type` VARCHAR(8)：CM-01~CM-07 冻结枚举字面量最长 5 字符（"CM-07"），8 留余量；
--     与既有 `model_type`（FORECAST/CAUSAL/SIMULATION/DECISION 旧四值）**并存不覆写**——API 只增不改，
--     旧列继续服务既有读路径，新列是注册中心的权威类型位（枚举单源在 cognitive-engine-api，禁 DDL 建 CHECK，R-15 同纪律）。
--   `registry_state` VARCHAR(16)：DRAFT/PUBLISHED（规格 4 的状态机承载）；与 `status`（active/retired 旧运营标记）解耦，
--     避免把"是否在用"与"是否过门禁"混为一列。**存量行一律回落 'DRAFT'**（四门禁在库侧从无留痕，不得推定已发布），
--     对外以"已发布"提供必须重走四门禁升格。
--   `content_hash` VARCHAR(64)：SHA-256 hex（64 字符定长）——发布时冻结的规范化内容摘要，
--     不可变性判据 = `registry_state='PUBLISHED' AND content_hash IS NOT NULL`（应用侧守卫，见 #86 测试）。
--   `supersedes_id` VARCHAR(36)：指向被替代版本行的 `id`（MC01 应用侧 UUID，跨表/自表均无 FK，应用层校验）。
--   `gate_contract`/`gate_regression`/`gate_backtest`/`gate_calibration` VARCHAR(16)：
--     取值 PASS/FAIL/PENDING 三值字符串（**不用 SMALLINT 布尔**——门禁需区分"未跑"与"跑失败"，
--     布尔形态会丢 PENDING 语义，导致"任一缺项 → 停留 DRAFT"不可判）。
--   `published_at` TIMESTAMP / `published_by` VARCHAR(64)：升格留痕（无时区戳列，MC03；发布人非个人敏感数据）。

-- ── 1. 补列（只加不删，全部幂等；NOT NULL 列一律带 DEFAULT，存量行不受影响）──
ALTER TABLE ecos_cognitive.ecos_cognitive_model ADD COLUMN IF NOT EXISTS cognitive_model_type VARCHAR(8);
ALTER TABLE ecos_cognitive.ecos_cognitive_model ADD COLUMN IF NOT EXISTS registry_state VARCHAR(16) NOT NULL DEFAULT 'DRAFT';
ALTER TABLE ecos_cognitive.ecos_cognitive_model ADD COLUMN IF NOT EXISTS content_hash VARCHAR(64);
ALTER TABLE ecos_cognitive.ecos_cognitive_model ADD COLUMN IF NOT EXISTS supersedes_id VARCHAR(36);
ALTER TABLE ecos_cognitive.ecos_cognitive_model ADD COLUMN IF NOT EXISTS gate_contract VARCHAR(16) NOT NULL DEFAULT 'PENDING';
ALTER TABLE ecos_cognitive.ecos_cognitive_model ADD COLUMN IF NOT EXISTS gate_regression VARCHAR(16) NOT NULL DEFAULT 'PENDING';
ALTER TABLE ecos_cognitive.ecos_cognitive_model ADD COLUMN IF NOT EXISTS gate_backtest VARCHAR(16) NOT NULL DEFAULT 'PENDING';
ALTER TABLE ecos_cognitive.ecos_cognitive_model ADD COLUMN IF NOT EXISTS gate_calibration VARCHAR(16) NOT NULL DEFAULT 'PENDING';
ALTER TABLE ecos_cognitive.ecos_cognitive_model ADD COLUMN IF NOT EXISTS published_at TIMESTAMP;
ALTER TABLE ecos_cognitive.ecos_cognitive_model ADD COLUMN IF NOT EXISTS published_by VARCHAR(64);

-- ── 2. 存量补形（幂等：仅在 cognitive_model_type IS NULL 时按旧四值映射；未映射值留 NULL，由应用侧 fail-loud 拒绝）──
UPDATE ecos_cognitive.ecos_cognitive_model SET cognitive_model_type = CASE upper(model_type)
        WHEN 'FORECAST'   THEN 'CM-03'   -- 预测
        WHEN 'CAUSAL'     THEN 'CM-04'   -- 因果
        WHEN 'SIMULATION' THEN 'CM-05'   -- 情景·反事实
        WHEN 'DECISION'   THEN 'CM-01'   -- 策略/决策类归"规则推理"（附件第二册 §1056-1062 七类中无 DECISION 类型，本批按最近语义定性）
        ELSE NULL END
    WHERE cognitive_model_type IS NULL AND upper(model_type) IN ('FORECAST','CAUSAL','SIMULATION','DECISION');

-- ── 3. 索引（普通索引，禁条件索引 MC03；is_deleted 并入键代偿）──
CREATE INDEX IF NOT EXISTS idx_cv261_model_state   ON ecos_cognitive.ecos_cognitive_model (registry_state, is_deleted);
CREATE INDEX IF NOT EXISTS idx_cv261_model_cm      ON ecos_cognitive.ecos_cognitive_model (cognitive_model_type, registry_state);
CREATE INDEX IF NOT EXISTS idx_cv261_model_supersed ON ecos_cognitive.ecos_cognitive_model (supersedes_id);
-- 注：uniq(model_id, version, is_deleted) 已由 V192 建立，承载"版本不可变"的业务键唯一性；
--     本批不加 content_hash 唯一索引（DRAFT 期哈希可重算、NULL 不参与唯一判定，加索引无收益且有歧义面）。

-- ── 4. 注释登记 ──────────────────────────────────────────────
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.cognitive_model_type IS 'CM-01~CM-07 认知模型类型（枚举单源在 cognitive-engine-api；NULL=未映射旧值，注册端点 fail-loud 拒绝）；旧 model_type 列只读保留（API 只增不改）';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.registry_state IS 'DRAFT/PUBLISHED（四类发布门禁 contract·regression·backtest·calibration 全 PASS 才可升 PUBLISHED；任一缺项停留 DRAFT 且发布端点返 4xx，禁静默成功）；存量行一律 DRAFT';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.content_hash IS '发布时冻结的规范化内容摘要（SHA-256 hex）；PUBLISHED 且非空 = 不可变态，UPDATE 必败（应用侧守卫）';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.supersedes_id IS '被替代版本行 id（版本变更只准新建版本行并指旧版，禁原地 UPDATE 已发布行）';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.gate_contract IS '发布门禁-契约校验：PASS/FAIL/PENDING（三值字符串，禁布尔——需区分未跑与跑失败）';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.gate_regression IS '发布门禁-回归集：PASS/FAIL/PENDING';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.gate_backtest IS '发布门禁-回测：PASS/FAIL/PENDING（与既有 backtest_score 列配套：分数供排序，门禁供裁决）';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.gate_calibration IS '发布门禁-校准：PASS/FAIL/PENDING';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.published_at IS '升格 PUBLISHED 时点（四门禁全 PASS 时由发布端点写入）';
COMMENT ON COLUMN ecos_cognitive.ecos_cognitive_model.published_by IS '升格操作人（审计留痕，非个人敏感数据；与 create_by/update_by 分列）';

-- ── 5. 回滚说明 ──────────────────────────────────────────────
-- 数据回滚：本批纯增量列。回滚 = 新列不再被读写（应用侧回退到 model_type/status 旧形态即等效）；
--           如需清列，只允许置 is_deleted=1 语义之外的**人工裁决**（IR03 禁脚本内 DROP COLUMN）。
-- 幂等性：ALTER ADD COLUMN IF NOT EXISTS + UPDATE WHERE ... IS NULL 守卫 + CREATE INDEX IF NOT EXISTS ⇒ 重复执行零报错、零重复补形。
--   二次执行时 cognitive_model_type 已非 NULL ⇒ 补形段自然空转。

-- ── DDL Lint Self-audit ──────────────────────────────────────
-- [DR01] 全部语句 schema 限定（ecos_cognitive.）+ 小写下划线 ✓  [DR02] 不改表名（存量 ecos_ 前缀已合规）✓
-- [DR03] 单数 ✓  [DR04] 无新增 JSON 语义列（features_json 已在 V192）✓  [DR05] 本批无布尔列；
--        四门禁**刻意不用 SMALLINT 布尔**（需 PENDING 第三值，见列形态依据）✓  [DR06~DR08] 七基线列 V192 已自带，本批不重复 ✓
-- [MC01] 无新主键、无自增序列；supersedes_id 沿用 VARCHAR(36) 应用侧 UUID 形态 ✓
-- [MC02] 无二进制 JSON 列；本批无新增 NUMERIC 列 ⇒ 无金额面、无 ST03-A 新豁免行
--        （派生统计量列逐列声明见 docs/40-实现/列级加密豁免登记表 §二·续·补二；control-domain 列永不可豁免，本批未触碰）✓
-- [MC03] 无裸 cast（补形段用 upper() 标量函数，无 :: 形态）/无条件索引/无 RLS/无分区/无数组列/无时区戳列（published_at TIMESTAMP）✓
-- [ST07] 落 ecos_cognitive（控制域认知引擎 schema，规格 5 权威载体）；未建 ecos_ai.ecos_wagent_cognitive_model 副本（ADR-17）✓
-- [IR02] 手动 psql 执行面，本批只落文件 ✓  [IR03] public.ecos_cognitive_model 只停写，不加列、不 DROP、不改名 ✓
-- [待补] registry_state/cognitive_model_type/gate_* 的枚举 CHECK 本批不建（R-15 同纪律，枚举单源在 Java 契约层）；
--        04 册 REQ-KB-08 画像版本与 CM-07 数值禁令的写路径守卫属应用侧（#86：Cm07NumericGuardTest）
