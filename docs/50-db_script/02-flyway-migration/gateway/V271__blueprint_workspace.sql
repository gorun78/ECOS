-- V271 (补充设计-认知域与智能域 v1.0 §6): 场景蓝图 workspace 权威载体
-- 追溯: §6.1 定位与归属（Workspace/Scenario Workbench 属主；ai/cognitive 域禁止复制副本）；
--       八项建议 #3「场景蓝图」；§6.3 状态机；§6.5 Gap 项结构；§6.6 验收门槛
-- 落点裁定: 场景层控制数据 ✓（**2026-10-11 修订**：原稿落 `workspace.` schema，经与 PG 实库和
--           《数据库访问规范》v1.4 §二附 ST07 交叉核对判定为违规——schema 封闭枚举仅 8 个，
--           `workspace` 不在枚举且实库不存在；ST07 明文「sysman/workspace/service 层新表一律落
--           ecos_control」，场景层既有 12 表（ecos_scenario_definition 等）V200 治理后全在 ecos_control。
--           本稿未实跑无历史包袱，表落点改 ecos_control；属主不变（workspace 服务唯一写入面，§6.1）；
--           跨引擎仅逻辑引用 scenario 既有主表，ST09 禁跨 schema FK）
-- 只写红线:
--   * 状态词表单源 = blueprint.BlueprintStatus 枚举（DDL 不建 CHECK，R-15 同纪律）；
--   * 禁止 DRAFT 直进 ACTIVE（§6.3；状态机非法迁移 409，SUP-BP-01）；
--   * 发布版本不可变：RELEASED 后 content_json/artifact_digest 只读，更新生成 version+1 新行；
--   * gap/dependency/capability/test/approval 子段全 JSON-TEXT（§6.2 逻辑模型整体落 content_json；
--     结构化查询热列才拆列，禁复制其他引擎权威对象到本表正文，§17 捷径禁令 2）；
--   * 审批引用 approval_ref 指向 security-engine 裁决/批准记录（security 权威，本表只存引用）。
-- 状态: 2026-10-11 修订落点后，经用户「授权实跑」增量迁移之续授权（同一 live 契约核验目的，
--       见 goal 决议「拉起全栈核验契约后继续实现剩余面」）对本机开发 ecos-postgres/sys_man 实跑
-- 纪律: DR01~DR08 / MC01~MC03 / ST07 / IR02 / IR03(只加不删)

CREATE TABLE IF NOT EXISTS ecos_control.ecos_scenario_blueprint (
    id                    VARCHAR(36) PRIMARY KEY,           -- MC01 应用侧 UUID（= blueprintId）
    tenant_id             VARCHAR(64) NOT NULL,
    org_id                VARCHAR(64),
    scenario_id           VARCHAR(36) NOT NULL,              -- 逻辑引用 workspace 场景主表（同域，ST09 不建 FK）
    version               INTEGER NOT NULL,                  -- 版本号（从 1 起；发布不可变，更新 +1 新行）
    status                VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
        -- 词表（单源 BlueprintStatus）：DRAFT/DISCOVERING/GAP_ANALYSIS/BLUEPRINT_READY/VALIDATING/
        -- READY_FOR_APPROVAL/APPROVED/RELEASED/ACTIVE/RUNNING/COMPLETED +
        -- NEEDS_CLARIFICATION/BLOCKED/VALIDATION_FAILED/PAUSED/DEPRECATED（§6.3）
    goal_statement        TEXT,                              -- 业务目标陈述（§6.2 goal.statement）
    goal_success_text     TEXT,                              -- JSON 数组：成功条件（§6.2 successCriteria）
    scope_json_text       TEXT,                              -- JSON：租户/期间/项目/口径范围（§6.2 scope）
    dependencies_text     TEXT,                              -- JSON 数组：依赖（type/id/required/versionConstraint，§6.2）
    capabilities_text     TEXT,                              -- JSON 数组：能力需求（capabilityId/contractVersion/required，§6.2）
    gaps_text             TEXT,                              -- JSON 数组：Gap 项（§6.5 全字段：category/severity/status/evidenceRefs/…）
    risks_text            TEXT,                              -- JSON 数组：风险（§6.2 risks）
    tests_text            TEXT,                              -- JSON 数组：用例引用（P0/Pn 分级，§6.2 tests）
    content_json          TEXT,                              -- 完整 §6.2 逻辑模型快照（发布冻结体；运行回放以本列解析）
    artifact_digest       VARCHAR(120),                      -- 发布摘要（§6.2 release.artifactDigest；仅 RELEASED 起填充）
    approval_required     SMALLINT NOT NULL DEFAULT 0,       -- DR05 0/1（§6.2 approval.required）
    approval_ref          VARCHAR(100),                      -- security 裁决/批准记录引用（§6.6：写操作必带审计事件）
    context_digest        VARCHAR(120),                      -- 关联 Context Pack 摘要（§5.2 contextDigest；运行回链 §6.6）
    release_artifact_ref  VARCHAR(200),
    released_time         TIMESTAMP,
    trace_id              VARCHAR(64),
    actor                 VARCHAR(100),                      -- 最近状态迁移操作者（§6.3：每次迁移记录 actor/原因/traceId/版本）
    transition_reason     TEXT,                              -- 最近状态迁移原因（同上）
    domain                VARCHAR(50) NOT NULL DEFAULT 'default',
    version_no            VARCHAR(20) NOT NULL,
    create_time           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by             VARCHAR(100),
    update_by             VARCHAR(100),
    is_deleted            SMALLINT NOT NULL DEFAULT 0
);
COMMENT ON TABLE ecos_control.ecos_scenario_blueprint IS
    '场景蓝图权威载体（补充设计 v1.0 §6）：workspace 属主，唯一写入面；ai/cognitive 域禁复制副本（§6.1/§17 捷径禁令 2）；发布版本不可变，RUN 运行回链 version+context_digest（SUP-BP-02/03）';
CREATE UNIQUE INDEX IF NOT EXISTS uk_v271_bp_scen_ver ON ecos_control.ecos_scenario_blueprint (scenario_id, version, is_deleted);
CREATE INDEX IF NOT EXISTS idx_v271_bp_tenant_status ON ecos_control.ecos_scenario_blueprint (tenant_id, status, is_deleted);
COMMENT ON COLUMN ecos_control.ecos_scenario_blueprint.version IS
    '蓝图版本号（应用侧递增，非乐观锁 version_no）；RELEASED 后该行 content_json/artifact_digest 只读，更新 = version+1 新行 + 旧行 DEPRECATED（§6.6 发布不可变）';

-- ── 回滚说明 ──────────────────────────────────────────────
-- 数据回滚：仅新增表。回滚 = 应用侧停用 /blueprints 端点族；删表只允许人工裁决（IR03 禁脚本内 DROP）。
-- 幂等性：CREATE TABLE/UNIQUE INDEX/INDEX IF NOT EXISTS ⇒ 重复执行零报错。

-- ── DDL Lint Self-audit ──────────────────────────────────────
-- [DR01] schema 限定（ecos_control.，2026-10-11 修订）✓ [DR02/03] 新表单数 ✓ [DR04] JSON 全 TEXT ✓
-- [DR05] 布尔一律 SMALLINT 0/1 ✓ [DR06~08] 七基线列齐 ✓
-- [MC01] UUID 主键 ✓ [MC02] 无二进制浮点；version INTEGER 计数非度量 ✓
-- [MC03] 无条件索引（is_deleted 并入键）/ TIMESTAMP 无时区 ✓
-- [ST07] 八 schema 封闭枚举内：workspace 层新表落 ecos_control（v1.4 明文）✓ [ST09] 无跨 schema FK ✓ [IR02] 仅文件 ✓ [IR03] 只加 ✓
