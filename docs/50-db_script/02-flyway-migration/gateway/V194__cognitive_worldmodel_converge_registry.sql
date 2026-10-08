-- V194 (卷05 附录 E.3 / R-13 ① + R-12 ① 分批收敛 第4批): 世界模型族（wm_* / world_*）收敛登记与 {schema}. 限定读声明 —— **本批不并建镜像表**（同名冲突裁决待定，见下）
-- 追溯: W124/C106（认知真身 14 张全在 public，X-23：wm_scenario 8 行 / world_scenario 7 行 / _impact 10 行）、X-24（public↔ecos_cognitive 镜像 11 对：ecos_wm_causal_link、ecos_wm_goal **两侧均为 bigint nextval**，双方同时违 MC01）、X-25（public.ecos_wm_scenario + public.kb_cognitive_pipeline 系 MC01 目标集）、X-29/X-30（漂移面）
-- 批准: 需求检视报告 §十四 14.1 之 R-13 ①（执行顺序：先 {schema}. 限定 + 新表，再双写迁移，禁一次性 DROP）与 R-12 ①（ecos_cognitive 内 11 张错放业务/WM 表**定性错放、只停写不迁**；DROP 重建=严格双条件待授权）；执行边界 §14.4 = 脚本文件落地（**不实跑库**）
-- 纪律: DR01~DR08 / MC01~MC03 / ST07 / IR02(手动 psql) / IR03(只加不删)
--
-- ── 为什么本批不并建镜像（与 V191~V193 的差异化处置）────────
--   1) **同名占位冲突**：R-13 ① 归位目标 schema `ecos_cognitive` 内已存在同名 0 行错放表
--      （`ecos_cognitive.ecos_wm_scenario` / `ecos_cognitive.ecos_world_scenarios` 等 11 张，X-23/X-24）。
--      若本批在 ecos_cognitive 下 CREATE TABLE IF NOT EXISTS 同名镜像，IF NOT EXISTS 将**静默跳过**、
--      随后复制段会把 public 存量**写入 R-12 ① 定性为"错放、只停写"的表**——直接违反裁决口径，禁。
--   2) **终态名待裁**：世界模型族（wm_*）属业务域/世界模型资产还是认知维持产物的表名终态（归位原名 vs 认知前缀改名）
--      属 R-13 执行细案，待 §14.4 授权批次定版后另行接续号脚本落地；本批只固化**限定读声明 + 漂移登记 + MC01 过渡前置（V196 guid 双写）**。
--   3) **无单源可证列面的两张漂移表**（仅实库盘点可证、迁移 corpus 零 CREATE，禁凭记忆手写镜像 DDL）：
--      public.ecos_world_scenario（单数）、public.ecos_world_scenario_impact（X-23 计数清单之 "_impact 10"）
--      —— 出处：docs/40-实现/数据库现状盘点与schema归属映射-2026-09-28.md §B（22 张白名单）；
--      收敛前置 = 先向单源目录补登其 CREATE DDL（列面以实库 information_schema 核对后落档），再进 V190~V195 镜像轨道。
--
-- ── 本批动作：public 侧世界模型族收敛状态注释登记（COMMENT ON 可重跑，IR03 只加语义）──
COMMENT ON TABLE public.ecos_wm_scenario     IS '认知真身 14 张之一（V5 裸名建表落 public，BIGSERIAL PK 违 MC01→guid 双写列由 V196 添加）。R-13 ① 归位 ecos_cognitive，终态名待 §14.4 授权批定版（V194 登记：ecos_cognitive 内同名 0 行错放表按 R-12 ① 只停写不迁，禁写入本表名下）；过渡期读一律 public. 限定';
COMMENT ON TABLE public.ecos_wm_goal         IS '世界模型目标表（V5 裸名建表落 public，BIGSERIAL PK 违 MC01→V196 加 guid）。非 X-23 计数清单项，随 wm 族一并登记；与 ecos_cognitive.ecos_wm_goal（0 行错放，R-12 ① 停写）构成 X-24 双违 MC01 镜像对';
COMMENT ON TABLE public.ecos_wm_causal_link  IS '世界模型因果边表（V5 裸名建表落 public，BIGSERIAL PK 违 MC01→V196 加 guid）。与 ecos_cognitive.ecos_wm_causal_link（0 行错放，R-12 ① 停写）构成 X-24 双违 MC01 镜像对；本域外因果口径以 ontology REST 只读为准（E.4）';
COMMENT ON TABLE public.ecos_wm_goal_log     IS '世界模型目标日志表（单源 corpus 无 CREATE，仅盘点文档 §B 可证实库存在——漂移登记对象；guid 收敛待列面单源补登后进 V196 轨道）';
COMMENT ON TABLE public.ecos_world_scenarios IS '世界场景表（V10 裸名建表落 public，VARCHAR(64) PK，X-24 镜像对宽度 64↔64）。认知真身 14 张计数清单之 world_scenario 7 行的实证候选；终态名待裁（V194 登记），过渡期读一律 public. 限定';
COMMENT ON TABLE public.ecos_world_scenario  IS '世界场景表（单数形态，仅盘点文档 §B 可证、单源 corpus 无 CREATE——漂移登记对象，镜像于 V195 暂缓并登记；禁凭记忆建表）';
COMMENT ON TABLE public.ecos_world_scenario_impact IS '世界场景影响表（X-23 计数清单 "_impact 10" 行的唯一 corpus 可证名，出处=盘点文档 §B；单源无 CREATE ⇒ 列面不可证，镜像暂缓，前置=单源补登 DDL）';
COMMENT ON TABLE public.ecos_world_goal      IS '世界目标表（仅盘点文档 §B 可证，单源无 CREATE——漂移登记对象；E.4 口径：本体口径/因果边认知侧只读经 ontology REST，写权在产生方引擎）';
COMMENT ON TABLE public.ecos_world_causal_link IS '世界因果边表（仅盘点文档 §B 可证，单源无 CREATE——漂移登记对象；同上 E.4 只读口径）';

-- ── 执行环境探测（只读对账，重跑安全；失败=表缺失即迁移漂移面，按 IR02 人工核对，禁脚本内兜底建表）──
SELECT to_regclass('public.ecos_wm_scenario')          AS pub_wm_scenario,
       to_regclass('public.ecos_world_scenarios')      AS pub_world_scenarios,
       to_regclass('public.ecos_world_scenario')       AS pub_world_scenario_singular,
       to_regclass('public.ecos_world_scenario_impact') AS pub_world_scenario_impact,
       to_regclass('ecos_cognitive.ecos_wm_scenario')  AS cog_mirror_wm_scenario,
       to_regclass('ecos_cognitive.ecos_world_scenarios') AS cog_mirror_world_scenarios;
-- 判读：cog_mirror_* 非 NULL ⇒ 同名错放表在场（R-12 ① 只停写，任何本轨道脚本**禁**向其写入/改形）；
--       pub_world_scenario(_impact) 为 NULL ⇒ 盘点漂移面与实库不符，登记后上报，禁自动兜底。

-- ── 回滚说明 ──────────────────────────────────────────────
-- 本批仅 COMMENT 登记 + 只读探测，无表/列/索引变化 ⇒ 回滚为 no-op（注释可覆写回原文案）。

-- ── DDL Lint Self-audit ──────────────────────────────────
-- [DR01] 全部表引用 schema 限定（public./ecos_cognitive.）✓  本批无 CREATE TABLE（差异于 V191~V193 的裁决理由见头部 1)~3)）✓
-- [MC01] 无新列新表；nextval 违例列的过渡（guid 双写）在 V196 ✓  [MC02] 无 JSON 列变化 ✓  [MC03] 无 ON CONFLICT/RETURNING/ILIKE/裸二进制 JSON cast ✓（to_regclass 只读探测非 PG 专有写语法，V198 同法）
-- [ST07] 登记口径与 5+1 单源一致（控制域 ecos_cognitive / 错放表只停写）✓
-- [IR02] 手动 psql、失败暴露 ✓  [IR03] 只加不删、零 DROP ✓
-- [R-13 ①] 第 4 批（世界模型族）= 限定读声明 + 漂移登记，镜像待 §14.4 授权批 ✓  [R-12 ①] 错放表零触碰 ✓
