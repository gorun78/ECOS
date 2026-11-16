-- V335: Pareto 多目标优化历史表（世界模型 Pareto 优化 Tab 后端落地）
-- 追溯: 2026-11-15 产品级逐功能验证批 D106 活体实证——前端 ParetoTab 调
--       /api/v1/pareto/{optimize,problems,result/{id},from-scenario} 四端点，全仓零实现，
--       四端点经网关恒 404（cognition 侧 ParetoOptimizerServiceImpl 仅 24 行桩、签名不符）。
--       用户裁决 = 实现真正后端（NSGA-II 类多目标优化 + 历史持久化）。
-- 纪律: IR03 只加不删；幂等（IF NOT EXISTS）；主键 VARCHAR(36) 应用侧生成 UUID（MC01/MC02，
--       禁 gen_random_uuid()）；金额类不在本表范围。
-- 归属: 控制域 ecos_control（Pareto 属世界模型/认知的产物索引，落在控制平面，
--       非业务域五层建模产物）；代码侧显式限定 schema，不依赖会话 search_path（V47/ST07）。
-- 上线: psql -U postgres -d sys_man -f V335__ecos_pareto_problem.sql

-- 1. 优化问题登记（每次提交一次请求 = 一行；结果快照同表，便于 /result/{id} 直取）
--   审计列（create_by/create_time/update_by/update_time/domain/is_deleted/version_no）
--   按 MC02 口径随建表一次给齐 —— 缺了会被 gateway 的 Mc02TypeWhitelistTest 判红
--   （该门禁要求「表含审计列就要有配套回滚」，本次首次失败实测即此红线）。
CREATE TABLE IF NOT EXISTS ecos_control.ecos_pareto_problem (
    id              VARCHAR(36)  NOT NULL,
    problem_name    VARCHAR(255) NOT NULL DEFAULT '',
    num_objectives  INTEGER      NOT NULL,
    num_variables   INTEGER      NOT NULL,
    population_size INTEGER      NOT NULL,
    generations     INTEGER      NOT NULL,
    front_size      INTEGER      NOT NULL DEFAULT 0,
    solutions_json  TEXT         NOT NULL DEFAULT '[]',
    elapsed_ms      BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT now(),
    create_by       VARCHAR(100),
    create_time     TIMESTAMP    NOT NULL DEFAULT now(),
    update_by       VARCHAR(100),
    update_time     TIMESTAMP    NOT NULL DEFAULT now(),
    domain          VARCHAR(50)  NOT NULL DEFAULT 'default',
    is_deleted      SMALLINT     NOT NULL DEFAULT 0,
    version_no      VARCHAR(20)  NOT NULL DEFAULT '1',
    CONSTRAINT ecos_pareto_problem_pkey PRIMARY KEY (id)
);

-- 1b. 已建库补审计列（幂等；首个版本若已落库则本段补齐，重跑无副作用）
ALTER TABLE ecos_control.ecos_pareto_problem ADD COLUMN IF NOT EXISTS create_by  VARCHAR(100);
ALTER TABLE ecos_control.ecos_pareto_problem ADD COLUMN IF NOT EXISTS create_time TIMESTAMP NOT NULL DEFAULT now();
ALTER TABLE ecos_control.ecos_pareto_problem ADD COLUMN IF NOT EXISTS update_by  VARCHAR(100);
ALTER TABLE ecos_control.ecos_pareto_problem ADD COLUMN IF NOT EXISTS update_time TIMESTAMP NOT NULL DEFAULT now();
ALTER TABLE ecos_control.ecos_pareto_problem ADD COLUMN IF NOT EXISTS domain      VARCHAR(50) NOT NULL DEFAULT 'default';
ALTER TABLE ecos_control.ecos_pareto_problem ADD COLUMN IF NOT EXISTS is_deleted  SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE ecos_control.ecos_pareto_problem ADD COLUMN IF NOT EXISTS version_no  VARCHAR(20) NOT NULL DEFAULT '1';

COMMENT ON TABLE ecos_control.ecos_pareto_problem
    IS 'Pareto 多目标优化问题登记与结果快照（D106 落地；solutions_json 存完整前沿，便于 /result/{id} 直取）';
COMMENT ON COLUMN ecos_control.ecos_pareto_problem.solutions_json
    IS 'Pareto 前沿解集 JSON 数组：[{"variables":{"x1":..},"objectives":{"f1":..}}]';

-- 2. 历史列表按时间倒序（/problems 默认排序）
CREATE INDEX IF NOT EXISTS idx_pareto_problem_created
    ON ecos_control.ecos_pareto_problem(created_at DESC);
