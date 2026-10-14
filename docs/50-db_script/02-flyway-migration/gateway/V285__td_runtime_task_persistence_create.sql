-- V285 (差距回补 / runtime-task 持久化五表整族缺建): public.td_runtime_task / _plan / _status / _log / _execution
-- ============================================================
-- 缺陷事实（live 实证，非推断）:
--   V152__runtime_task_persistence.sql 头注即自认"保持/补列"脚本——对 td_runtime_task / _plan 只有
--   `ALTER TABLE ... ADD COLUMN IF NOT EXISTS`，**从未有 CREATE**；td_runtime_task_status / _log / _execution
--   全仓 grep `CREATE TABLE` = 0 命中（含 legacy：docs/50-db_script/01-ddl-多方言、ecos-sql）。
--   dev PG 实查 information_schema.tables WHERE table_name ILIKE '%runtime_task%' = **0 行**（五表全缺）。
--   ⇒ 消费方 SQL（TaskPersistenceDao 全部 13 条语句、V277 头注"前端 AsyncTaskCenter 消费
--   td_runtime_task_status"）今天任何一条执行都会 relation does not exist。
--   按用户裁决（2026-10-14 R9 ③"缺表查 legacy，没有则自行补上"）**整族补齐五表**，
--   只补 status 一表会让 saveTask/savePlan/logTask/recordExecution 继续 500，不属"补上"。
--
-- 列清单单源 = 代码（禁凭设计文档臆造，V281 先例）:
--   * runtime-task TaskPersistenceDao（R2 B4c 收口后的唯一 SQL 咽喉）逐条 INSERT/SELECT 列名，
--     双引号大写列 = pg_attribute quote_ident 验证过（DAO 类注释 21-24 行）；
--   * 小写审计/治理列（create_time/update_time/create_by/update_by/is_deleted/domain/version_no 及
--     plan 表 11 列）= V152 原文逐字；
--   * **大小写双轨不合并**：DAO 读 "TASK_TYPE"/"TASK_NAME" 大写列，V152 建的是小写 task_type/task_name，
--     两形态各自有引用面（V152 索引用小写混列、DAO 全用大写）——本脚本两套都建（小写列保留 V152 原文语义，
--     大写列满足 DAO 契约），登记 D1；禁在本批统一改名（返工面 = DAO+回调+ AsyncTaskCenter）。
--
-- Schema 归属: public（V152 头注"池: sys_man (schema public)"原文 + DAO 裸表名靠 currentSchema 解析，MC06 house style）。
-- td_ 前缀违例（登记 D2）: 详设 §10.4.4 禁 `agt_/aim_/cog_` 前缀、要求 ecos_{module}_{business} 模板——
--   本五表是**为存量代码补载体**（表名列名被 DAO 冻结），非新语义表，按存量名对齐；
--   归一改名挂台账 §D 多轨裁决（runtime 域），禁夹带。
--
-- 状态: **仅落文件，未对 ecos-postgres 实跑**（实跑需逐项授权；五表当前零存量行，建表无数据回滚风险）。
-- 纪律: DR01~DR08(存量形态豁免见 D1/D2) / MC01 / MC02 / ST07 / ST09 / IR02 / IR03
-- ============================================================

-- ============================================================
-- T1 td_runtime_task — 任务描述主表（DAO saveTask/queryTask/queryTasks 面）
-- ============================================================
CREATE TABLE IF NOT EXISTS public.td_runtime_task (
    "TASK_ID"         VARCHAR(64) PRIMARY KEY,                 -- MC01 变体登记 D3：应用侧生成字符串 id（DAO 全参数化写入），非 VARCHAR(36)——列宽被存量 ext("scheduleId") 等自由文本挤开，取 64
    "TASK_NAME"       VARCHAR(255),
    "TASK_TYPE"       VARCHAR(64),
    "DESCRIPTION"     TEXT,
    "TASK_CONFIG"     TEXT,                                    -- MC02：JSON 语义一律 TEXT（DAO jstr() 序列化后写入）
    "PARAMETERS"      TEXT,                                    -- 同上（jstr(parameters)）
    "PRIORITY"        INTEGER,
    "TIMEOUT"         BIGINT,                                  -- longOrZero(timeout)
    "RETRY_COUNT"     INTEGER,
    "ASYNC_FLAG"      VARCHAR(2),                              -- DAO 写 "1"/"0" 字符串
    "DEPENDENCIES"    TEXT,
    "SCHEDULE_ID"     VARCHAR(64),
    "NODE_ID"         VARCHAR(64),
    "EXECUTION_MODE"  VARCHAR(32),
    "CREATED_BY"      VARCHAR(100),
    "TENANT_ID"       VARCHAR(64),
    "TAGS"            TEXT,
    "EXTENSIONS"      TEXT,
    "CREATED_TIME"    TIMESTAMP,                               -- V150 双轨大写时间（saveTask 尾 UPDATE 回写原值）
    "UPDATED_TIME"    TIMESTAMP,
    create_time       TIMESTAMP NOT NULL DEFAULT NOW(),        -- V152 T1.1 逐字（下略 6 列同批原文）
    update_time       TIMESTAMP NOT NULL DEFAULT NOW(),
    create_by         VARCHAR(100),
    update_by         VARCHAR(100),
    is_deleted        SMALLINT NOT NULL DEFAULT 0,
    domain            VARCHAR(50) NOT NULL DEFAULT 'default',
    version_no        VARCHAR(20) NOT NULL DEFAULT '1'
);
COMMENT ON TABLE public.td_runtime_task IS
    'runtime-task 即时/定时任务描述主表（列清单单源 = TaskPersistenceDao.saveTask/queryTasks + V152 原文；本 CREATE 补 V152 从未建表的缺口）';
CREATE INDEX IF NOT EXISTS idx_td_runtime_task_tenant  ON public.td_runtime_task ("TENANT_ID", is_deleted);
CREATE INDEX IF NOT EXISTS idx_td_runtime_task_created ON public.td_runtime_task ("CREATED_TIME" DESC NULLS LAST);  -- queryTasks 排序面
CREATE INDEX IF NOT EXISTS idx_td_runtime_task_type    ON public.td_runtime_task ("TASK_TYPE");

-- ============================================================
-- T2 td_runtime_task_plan — 执行计划表（DAO savePlan/queryPlan/updatePlanState/markLastRun/queryPlansByType/softDeletePlan 面）
-- ============================================================
CREATE TABLE IF NOT EXISTS public.td_runtime_task_plan (
    "TASK_ID"          VARCHAR(64) PRIMARY KEY,                -- savePlan ON CONFLICT ("TASK_ID") ⇒ PK/唯一必需
    "PLAN_CONTENT"     TEXT,                                   -- jstr(plan) JSON 语义 → TEXT
    "EXECUTION_MODE"   VARCHAR(32),
    "TARGET_NODE_ID"   VARCHAR(64),
    "CREATED_TIME"     TIMESTAMP,
    "UPDATED_TIME"     TIMESTAMP,
    "TASK_NAME"        VARCHAR(255),                           -- updatePlanState 大写面（D1 双轨并存）
    "TASK_TYPE"        VARCHAR(64),                            -- queryPlansByType WHERE 大写面
    "CRON_EXPRESSION"  VARCHAR(128),                           -- DAO 大写读写；V152 另立小写列（D1）
    "NEXT_RUN_AT"      TIMESTAMP,
    "LAST_RUN_AT"      TIMESTAMP,
    "LAST_STATUS"      VARCHAR(32),
    cron_expression    VARCHAR(128),                           -- V152 T1.2 逐字保留（小写七列 + 四治理列）
    next_run_at        TIMESTAMP,
    last_run_at        TIMESTAMP,
    last_status        VARCHAR(32),
    task_name          VARCHAR(255),
    task_type          VARCHAR(64),
    create_by          VARCHAR(64),
    update_by          VARCHAR(64),
    is_deleted         SMALLINT NOT NULL DEFAULT 0,
    domain             VARCHAR(50) NOT NULL DEFAULT 'default',
    version_no         VARCHAR(20) NOT NULL DEFAULT '1'
);
COMMENT ON TABLE public.td_runtime_task_plan IS
    'runtime-task 执行计划表（plan_content + 定时四要素；大小写双轨 = 登记 D1，DAO 读大写、V152 建小写，两套并存至归一批次裁决）';
CREATE INDEX IF NOT EXISTS idx_td_task_plan_cron       ON public.td_runtime_task_plan (cron_expression) WHERE cron_expression IS NOT NULL;  -- V152 原文（存量 partial 形态保留，见 D4）
CREATE INDEX IF NOT EXISTS idx_td_task_plan_next_run   ON public.td_runtime_task_plan (next_run_at) WHERE next_run_at IS NOT NULL;          -- 同上
CREATE INDEX IF NOT EXISTS idx_td_task_plan_last_run   ON public.td_runtime_task_plan (last_run_at);
CREATE INDEX IF NOT EXISTS idx_td_task_plan_task_type  ON public.td_runtime_task_plan ("TASK_TYPE");                                        -- DAO queryPlansByType 大写过滤面

-- ============================================================
-- T3 td_runtime_task_status — 运行状态表（DAO saveStatus/queryStatus/queryTaskStatuses/softDeleteTask 面）
-- ============================================================
CREATE TABLE IF NOT EXISTS public.td_runtime_task_status (
    "TASK_ID"                   VARCHAR(64) PRIMARY KEY,       -- saveStatus ON CONFLICT ("TASK_ID") ⇒ 唯一必需
    "STATUS"                    VARCHAR(32) NOT NULL DEFAULT 'PENDING',  -- saveStatus 空值兜底 'PENDING'（DAO:213 原文）
    "STATUS_MESSAGE"            VARCHAR(512),
    "PROGRESS"                  INTEGER,
    "CURRENT_STEP_ID"           VARCHAR(64),
    "START_TIME"                TIMESTAMP,
    "END_TIME"                  TIMESTAMP,
    "ESTIMATED_REMAINING_TIME"  BIGINT,                        -- TaskStatus.getEstimatedRemainingTime()（毫秒时长）
    "PROCESSED_COUNT"           BIGINT,
    "TOTAL_COUNT"               BIGINT,
    "ERROR_MESSAGE"             TEXT,
    "ERROR_STACK"               TEXT,
    "RESULT"                    TEXT,
    "METRICS"                   TEXT,                          -- jstr(metrics) JSON 语义 → TEXT
    "EXECUTION_NODE_ID"         VARCHAR(64),                   -- metricNode(metrics.executionNodeId)
    "UPDATED_TIME"              TIMESTAMP,
    is_deleted                  SMALLINT NOT NULL DEFAULT 0    -- softDeleteTask SET "is_deleted"=1（双引号小写=未加引号原形，列存在即可）
);
COMMENT ON TABLE public.td_runtime_task_status IS
    'runtime-task 运行状态表（列清单单源 = TaskPersistenceDao.saveStatus/queryStatus 16 列 + softDeleteTask 的 is_deleted；V152 头注"保持"但从未 CREATE，本脚本补建）';
CREATE INDEX IF NOT EXISTS idx_td_runtime_task_status_updated ON public.td_runtime_task_status ("UPDATED_TIME" DESC NULLS LAST);  -- queryTaskStatuses 排序面
CREATE INDEX IF NOT EXISTS idx_td_runtime_task_status_status  ON public.td_runtime_task_status ("STATUS");

-- ============================================================
-- T4 td_runtime_task_log — 任务日志表（DAO logTask 面）
-- ============================================================
CREATE TABLE IF NOT EXISTS public.td_runtime_task_log (
    "LOG_SEQ"       BIGSERIAL PRIMARY KEY,                     -- MC01 违例登记 D5：DAO 无 id 列、无去重键 ⇒ 只能库侧自增；append-only 日志表
    "EXECUTION_ID"  VARCHAR(64),
    "TASK_ID"       VARCHAR(64),
    "LOG_LEVEL"     VARCHAR(16),                               -- DAO 大写化后写入
    "MESSAGE"       TEXT,
    "CREATED_TIME"  TIMESTAMP
);
COMMENT ON TABLE public.td_runtime_task_log IS
    'runtime-task 任务日志（logTask 唯一写面；无软删/无唯一键 ⇒ PK 走 BIGSERIAL，登记 D5）';
CREATE INDEX IF NOT EXISTS idx_td_runtime_task_log_task ON public.td_runtime_task_log ("TASK_ID", "CREATED_TIME");

-- ============================================================
-- T5 td_runtime_task_execution — 执行记录表（DAO recordExecution 面）
-- ============================================================
CREATE TABLE IF NOT EXISTS public.td_runtime_task_execution (
    "EXECUTION_ID"        VARCHAR(64) PRIMARY KEY,             -- recordExecution ON CONFLICT ("EXECUTION_ID") DO NOTHING ⇒ 唯一必需
    "TASK_ID"             VARCHAR(64),
    "STATUS"              VARCHAR(32),
    "START_TIME"          TIMESTAMP,
    "END_TIME"            TIMESTAMP,
    "DURATION"            BIGINT,                              -- endTime-startTime 毫秒
    "RESULT"              TEXT,
    "ERROR_MESSAGE"       TEXT,
    "ERROR_STACK"         TEXT,
    "EXECUTION_NODE_ID"   VARCHAR(64),
    "CREATED_TIME"        TIMESTAMP
);
COMMENT ON TABLE public.td_runtime_task_execution IS
    'runtime-task 执行记录（recordExecution 唯一写面；列清单单源 = TaskPersistenceDao:322-329）';
CREATE INDEX IF NOT EXISTS idx_td_runtime_task_execution_task ON public.td_runtime_task_execution ("TASK_ID", "START_TIME");

-- ── 偏差登记 ──────────────────────────────────────────────
-- D1 [双轨列名] plan/task 大小写两套列并存：DAO 读写全大写（"TASK_TYPE" 等），V152 补列全小写
--   （task_type 等），两形态各自被引用（V152 索引混用小写）。合并 = DAO+AsyncTaskCenter 返工，
--   独立批次挂台账 §D 裁决；本批**两套都建**保证任一引用面可执行。已知代价 = 小写列成为
--   V152 语义的空闲镜像（DAO 不写它），禁再往里写第二份真相。
-- D2 [td_ 前缀] 违详设 §10.4.4 命名模板：存量代码冻结表名，为 DAO 补载体非新表（V281 D1 同族口径）。
-- D3 [PK 列宽] "TASK_ID" VARCHAR(64) 非白名单口径的 VARCHAR(36)：taskId 由应用侧生成且
--   scheduleId/nodeId 等 ext 引用可为自由文本，64 为最窄安全值；仍为字符串 PK、无序列（MC01 精神不破）。
-- D4 [partial index] plan 两枚 `WHERE ... IS NOT NULL` 沿用 V152 原文：新表禁 partial（详设 §10.4.2
--   用唯一索引忽略 NULL 替代的口径）——此处**逐字复刻存量脚本**以保证回滚/对账语义一致，不新建同类。
-- D5 [BIGSERIAL] td_runtime_task_log PK 用 BIGSERIAL：违 MC01"应用侧 UUID"，理由是 DAO logTask
--   列清单里没有 id 位，补应用侧 id 需改 DAO（越出"补建载体"边界）。日志表无业务外键引用，风险限于单库。
-- D6 [MC06] public. 硬编码前缀：house style 同 V152/V164/V272~V281，配置化挂工程清单 #6。
-- ──────────────────────────────────────────────────────────

-- ── 回滚说明 ──────────────────────────────────────────────
-- 幂等：全部 CREATE ... IF NOT EXISTS + COMMENT；重复执行零变更零报错。
-- 数据回滚：五表当前 information_schema 实查不存在 ⇒ 新增 0 行表，无存量数据触点；
--   回滚 = JdbcTaskPersistenceService 条件不启用（Bean 闲置无副作用，DAO 装配注释 29-30 行原文）。
-- 实跑影响面：task submit/status/list 面从"表不存在 500"转为可写可读；AsyncTaskCenter 消费面（V277 登记）获得载体。
--
-- ── DDL Lint Self-audit ──────────────────────────────────────
-- [DR02/D2] td_ 前缀违例=登记  [DR04] JSON 语义全 TEXT 且无 _json 后缀列=DAO 冻结列名（D1 同族，登记）
-- [DR06] task/plan/status 有 create/update_time；log/execution 仅 "CREATED_TIME"=DAO 冻结面（无 V152 补列历史），登记 D1 口径
-- [MC01] 4 表字符串 PK ✓；log BIGSERIAL=登记 D5  [MC02] 无 JSONB ✓  [MC03/D4] partial index 沿用存量=登记
-- [ST04/05] 不写 RLS ✓  [ST06] 无审计表（log 非安全审计，审计唯一走 ecos.audit）✓  [ST07] public 主控制现行面 ✓
-- [ST09] 无 FK（TASK_ID/EXECUTION_ID 全逻辑引用）✓  [IR02] 仅落文件，实跑待授权 ✓  [IR03] 只加不删 ✓
