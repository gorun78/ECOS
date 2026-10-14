-- V282 (企业经营管控演示数据集 T2b / 画像数值列 inline 补齐): ecos_knowledge.ecos_kb_profile 追加 8 个派生统计量列
-- 追溯: 设计书《ECOS详细设计说明书-从零实施版-2026-10-12》第 12 篇（历史画像 PROFILE_IMPUTED 取数链）；
--       代码权威 = ProfileDao.java:39-46（ROW_MAPPER 逐列 rs.getInt("sample_count") / rs.getBigDecimal(...)）
--       与 ProfileDao.java:90-95（INSERT 显式列清单含 sample_count, missing_rate, p10, p50, p90, mean_value, ci_low, ci_high）
-- 批准: 用户裁决 G2=全授权（2026-10-14），本脚本为授权三枚之一
-- 状态: 本脚本已按 IR02 手动 psql 对 ecos-postgres/sys_man 实跑（实跑证据见 docs/40-实现/ 登记）
-- 纪律: IR02(禁 Flyway，手动 psql) / IR03(只加不删，零 DROP) / ST03-A(数值列逐列 COMMENT 登记) /
--       ST07(定义态表留控制域 ecos_knowledge) / MC02(无 JSON 列新增) / MC01(不动主键形态)
--
-- ── 设计↔实现漂移登记（本脚本的存在理由，必读）────────────────
-- ① V176（2026-09-30，R-8=② 裁决）把数值统计事实拆到 **独立表** ecos_dw.ecos_kb_profile_stats（经 data-engine 写通道，
--    两表以 stats_ref VARCHAR(36) 弱关联）。但该拆分形态在代码侧从未落地：
--      · ecos_dw.ecos_kb_profile_stats —— V176 建表语句在位，**实库无此表**（2026-10-14 只读实测 to_regclass 为 NULL）；
--      · 全仓 Java 对 ecos_kb_profile_stats 的引用数 —— **0**（grep 实证）；
--      · 唯一定义态读写链 ProfileDao（SELECT * / INSERT 显式列）把 8 个数值列 **inline 要求在本表上**。
-- ② 后果（实跑前）：profiles/resolve 与任何画像写入在 ROW_MAPPER/INSERT 处报 column "sample_count" does not exist
--    ⇒ 画像面不可用。第 12 篇的 PROFILE_IMPUTED 取数档依赖此列，故演示数据集必须补齐。
-- ③ 本脚本处置：**以实现（inline 列）为准补列，不改 DAO**。V176 的独立 stats 表形态保留为未落地设计，
--    不在本脚本建表（避免同一数值列出现两个权威落点）；两形态的最终收口（要么代码切 stats 表、要么废止 V176 拆分注记）
--    属独立治理批次，不在本次数据初始化范围。
-- ④ 类型/精度逐字镜像 V176:67-74（同一语义列不得有两个精度口径）；可空性有一处**有意偏离**：
--    V176 声明 missing_rate NOT NULL DEFAULT 0，本表落为可空——依据 KbProfileDO.java:19 该字段为 BigDecimal（可为 null），
--    NOT NULL 会在缺统计口径时把写入打回 500。sample_count 侧 KbProfileDO.java:18 为 int 原语，故保留 NOT NULL DEFAULT 0。

ALTER TABLE ecos_knowledge.ecos_kb_profile
    ADD COLUMN IF NOT EXISTS sample_count INTEGER       NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS missing_rate NUMERIC(5,4),
    ADD COLUMN IF NOT EXISTS p10          NUMERIC(18,4),
    ADD COLUMN IF NOT EXISTS p50          NUMERIC(18,4),
    ADD COLUMN IF NOT EXISTS p90          NUMERIC(18,4),
    ADD COLUMN IF NOT EXISTS mean_value   NUMERIC(18,4),
    ADD COLUMN IF NOT EXISTS ci_low       NUMERIC(18,4),
    ADD COLUMN IF NOT EXISTS ci_high      NUMERIC(18,4);

-- ── ST03-A 逐列登记（R-9 ①：派生统计量不加密、不豁免业务金额列规则）──
COMMENT ON COLUMN ecos_knowledge.ecos_kb_profile.sample_count  IS '样本量（画像窗口内参与统计的事实行数；KbProfileDO.sampleCount int 原语 ⇒ NOT NULL DEFAULT 0；ST03-A 逐列登记，派生统计量不加密）';
COMMENT ON COLUMN ecos_knowledge.ecos_kb_profile.missing_rate  IS '缺失率（比率→概率档 NUMERIC(5,4)，承 V176:68 精度；可空=缺统计口径时留 NULL 而非 0，避免把 0 误读为"零缺失"；ST03-A 逐列登记）';
COMMENT ON COLUMN ecos_knowledge.ecos_kb_profile.p10           IS '派生统计量 P10（分位数，NUMERIC(18,4) 承 V176:69；ST03-A 逐列登记，不加密不豁免业务金额列规则）';
COMMENT ON COLUMN ecos_knowledge.ecos_kb_profile.p50           IS '派生统计量 P50（中位数，NUMERIC(18,4) 承 V176:70；ST03-A 逐列登记）';
COMMENT ON COLUMN ecos_knowledge.ecos_kb_profile.p90           IS '派生统计量 P90（分位数，NUMERIC(18,4) 承 V176:71；ST03-A 逐列登记）';
COMMENT ON COLUMN ecos_knowledge.ecos_kb_profile.mean_value    IS '派生统计量 均值（NUMERIC(18,4) 承 V176:72；ST03-A 逐列登记）';
COMMENT ON COLUMN ecos_knowledge.ecos_kb_profile.ci_low        IS '置信区间下界（CI 由 ci_level/stats_method 决定口径，NUMERIC(18,4) 承 V176:73；ST03-A 逐列登记）';
COMMENT ON COLUMN ecos_knowledge.ecos_kb_profile.ci_high       IS '置信区间上界（NUMERIC(18,4) 承 V176:74；ST03-A 逐列登记）';

-- ── 回滚说明 ────────────────────────────────────────────────
-- IR03 只加不删：本脚本不做 DROP。如需回退，先确认无写入路径依赖（ProfileDao INSERT 显式列会因缺列 500），
-- 再由人工单独授权 DROP COLUMN；补列本身对既有 0 行定义态数据无副作用。
