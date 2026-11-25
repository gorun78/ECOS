-- ============================================================================
-- V366 — L3 研究候选审批留痕（可行性报告 v2.0 §5.5 候选治理，2026-11-25）
-- ============================================================================
-- 目标（只加不删）：
--   1) 新增 ecos_control.ecos_scenario_run_approval：记录「某轮研究运行被谁、以什么
--      依据、在什么时刻批准/驳回」，作为候选→生效的唯一人工闸门证据；
--   2) 不新增「候选」实体表：候选就是 ecos_scenario_run 里的 RESEARCH_RUN 行，
--      审批只改变其审批态，不复制运行数据（禁双份真相）。
--
-- 纪律：IR03 只加不删；MC01 主键 VARCHAR(36) 应用侧 UUID；MC02 类型白名单；
--       MC03 标准 SQL；ST07 落 ecos_control；DR06 审计列齐备；DR07 version_no；
--       DR08 domain；不建 FK（ST09）；本批无金额列。
--
-- 口径：
--   审批是 append-only 留痕（一次运行可被多次驳回后再次批准），
--   故不在 ecos_scenario_run 上加审批态列，也不做 UPDATE 覆盖历史。
--   decision 词表固定 APPROVED/REJECTED；批准必须带 approvalRef（防「生成即审批」）。
-- ============================================================================

CREATE TABLE IF NOT EXISTS ecos_control.ecos_scenario_run_approval (
    id                  VARCHAR(36)  NOT NULL,
    run_id              VARCHAR(36)  NOT NULL,
    scenario_id         VARCHAR(36)  NOT NULL,
    decision            VARCHAR(16)  NOT NULL,
    approval_ref        VARCHAR(128),
    reason              TEXT,
    approver            VARCHAR(64)  NOT NULL,
    approver_roles      VARCHAR(256),
    run_stop_reason     VARCHAR(32),
    run_sota_run_id     VARCHAR(36),
    evidence_digest     VARCHAR(80),
    domain              VARCHAR(50)  NOT NULL DEFAULT 'default',
    version_no          VARCHAR(20)  NOT NULL DEFAULT '1',
    is_deleted          SMALLINT     NOT NULL DEFAULT 0,
    create_by           VARCHAR(64)  NOT NULL,
    create_time         TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_by           VARCHAR(64),
    update_time         TIMESTAMP,
    CONSTRAINT pk_ecos_scenario_run_approval PRIMARY KEY (id),
    CONSTRAINT ck_scenario_run_approval_decision CHECK (decision IN ('APPROVED', 'REJECTED'))
);

COMMENT ON TABLE ecos_control.ecos_scenario_run_approval IS
    'L3 研究候选审批留痕（V366）：只追加不覆盖，一次运行可多次驳回后批准；候选=RESEARCH_RUN 行本身，不复制运行数据';
COMMENT ON COLUMN ecos_control.ecos_scenario_run_approval.decision IS
    '审批结论（V366）：APPROVED / REJECTED（不设 PENDING——未审批即无记录）';
COMMENT ON COLUMN ecos_control.ecos_scenario_run_approval.approval_ref IS
    '审批凭据（V366；批准时必填，防「生成即审批」；驳回可空）';
COMMENT ON COLUMN ecos_control.ecos_scenario_run_approval.approver IS
    '审批人（V366；取信任头 X-ECOS-USER，随请求中继）';
COMMENT ON COLUMN ecos_control.ecos_scenario_run_approval.approver_roles IS
    '审批时角色快照（V366；审计用，不参与授权判定）';
COMMENT ON COLUMN ecos_control.ecos_scenario_run_approval.run_stop_reason IS
    '审批时该轮运行的停止原因快照（V366；便于回看「批准的是什么状态」）';
COMMENT ON COLUMN ecos_control.ecos_scenario_run_approval.evidence_digest IS
    '证据摘要（V366；sha256:... 对人审批所依据的产物/轨迹）';

CREATE INDEX IF NOT EXISTS idx_run_approval_run
    ON ecos_control.ecos_scenario_run_approval (run_id, create_time DESC);
CREATE INDEX IF NOT EXISTS idx_run_approval_scenario
    ON ecos_control.ecos_scenario_run_approval (scenario_id, create_time DESC);
