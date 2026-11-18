-- V305 (#147V 补充设计册 R7~R11 数据面批 / DS-4 前置①：规则版本表历史读切 Git 的载体列):
--   ecos_control.sys_rule_version 增 1 列 git_ref VARCHAR(64)（可空）。
--
-- 取证缘由（2026-11-02 现状实测，本脚本不发"已实现"叙述）：
--   ① dev PG 实查（information_schema.tables/columns）：
--      `sys_rule_version` 与 `ecos_object_version` 均已落 **ecos_control**（V200 schema 治理），
--      两表现列**均无 git_ref/git_commit_id/git_tag** ⇒「历史版本只进 Git」的读侧切换
--      在没有引用列时**不可能实现**（引用无从存）。本脚本只补 rule 表一枚。
--   ② 为什么只补 rule、**不补 object**：
--      rule 域已有属主归档通道（V300 种子 `kb-asset-tree`，#147E 已接
--      `ComplianceRuleVersionService#archive`）——只是归档返回的 commitId/tag **被调用方丢弃、
--      无列可落**，本列即该缺口的载体，补了就有位可写、有 ref 可读。
--      object 域（`ecos_object_version`）**既无归档腿、也不在 §2.9.5③ 六类设计资产枚举内**
--      （六类=数据工作台/pipeline/血缘/分级分类、本体模型、知识资产树、认知模型、
--      Agent 协同模型、场景定义；运行态对象历史不属其中任何一类，六枚 repositoryId 亦不覆盖）。
--      按纪律「无载体的位不建」（DG-9/X-43）与「未接入前一律返回 not_available、禁伪成功」
--      （铁律 #8），本批**不为其建列**、**不擅自新增第 7 枚 repositoryId**（注册表基线属治理面，
--      改动需 PMO 对资产归类作出裁决；擅改会让 `GitRepoRegistryConsumptionGateTest` 的
--      逐枚属主钉定与 §2.9.5① 清单分叉）。此项如实挂账为**待裁**，不计入 DS-4 完成度。
--   ③ 可空 + 不建默认值：存量版本行从未归档，ref 必为空（读侧按「ref 空 ⇒ 内容取 DB 快照列」
--      分态处理）；禁建假默认冒充已归档。
--   ④ 不 DROP、不删列、不改既有列：4 张废弃版本表的 DROP 前置（停写 + 读写全切 Git + pg_dump
--      备份）本批仍未满足（见册 §2.9.6④/DQ-6），本脚本只推进「读写切 Git」中的 rule 读侧。
--
-- 纪律: IR02(手动 psql -f，Flyway 永久禁用) / IR03(只加不删) / DR01(小写下划线 + schema 限定名) /
--       ST07(控制域落 ecos_control) / MC02(白名单类型 VARCHAR(n)) /
--       不触 DROP（DQ-6 前置未满足）
--
-- ── 规则版本表补 git_ref（幂等）────────────────────────────────────────────
ALTER TABLE ecos_control.sys_rule_version
    ADD COLUMN IF NOT EXISTS git_ref VARCHAR(64);

-- 注：COMMENT ON ... IS 只接受单一字符串字面量，禁 || 拼接（PG 语法；本行系 #147V 实跑
-- 报错 psql "syntax error at or near \"||\"" 订正，与 V300/V301/V303/V304 同法单字面量）
COMMENT ON COLUMN ecos_control.sys_rule_version.git_ref IS
    'Git 归档引用（commit SHA；§2.9.5② 四版本概念的历史定位键）。非空⇒该版本行历史内容的权威来源是 kb-asset-tree 对象库，snapshot 列降为兜底；空⇒未归档（存量行/归档失败），内容仍取 snapshot 列，读侧须标 DB_IN_USE/DB_FALLBACK，禁凭本列存在就宣称历史已切 Git。载体=V305，消费方=ComplianceRuleVersionService#getVersions。V305 引入';
