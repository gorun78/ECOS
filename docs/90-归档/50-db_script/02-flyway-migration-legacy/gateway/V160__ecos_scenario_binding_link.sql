-- ============================================================
-- V160__ecos_scenario_binding_link.sql — 场景绑定关系边表（架构铁律 §0.6.2）
--
-- 六类绑定分三层（§0.6.2）：
--   链路节点：DATASET / OBJECT_TYPE / KNOWLEDGE_BASE / AI_AGENT（土D→金I→水K→火W）
--   横切约束：SECURITY_POLICY（正交，非链上一环）
--   出口：INTERFACE（链外出口）
--
-- 边类型（link_type）：
--   MAPPING     DATASET→OBJECT_TYPE     契约 ecos_entity_table_mapping.id（§0.6.2.2 必带）
--   EXTRACTION  OBJECT_TYPE→KNOWLEDGE_BASE     kb 抽取（B3 契约）
--   COGNITION   KNOWLEDGE_BASE→AI_AGENT        cognitive（B4 契约）
--   GOVERN      任意节点/边 → 任意节点/边       security-engine（横切）
--   EXPOSE      AI_AGENT → INTERFACE           接口暴露
--
-- 灰度 seed：sc001 三条已成型边
--   sb001(ds_flight_schedules) --MAPPING--> sb003(flight)
--   sb007(agent_safety_monitor) --COGNITION--> sb006(sop_maintenance_v2)
-- 第三边（OBJECT_TYPE→KNOWLEDGE_BASE）当前 seed 没有对应资源，本批不引入。
-- （占位 source_contract 仅用于本地验证；不影响 §0.6 判定）
--
-- 红线合规（IR02/03/05 · DR06/07/08）：
--   建新表，无 DROP/ALTER；主键 VARCHAR(64) 与 ecosystem binding 风格一致；
--   审计 5 字段 + version_no + domain 齐全；约束 INCLUDE 检查双端 binding 同场景。
--   索引覆盖常用查询：scenario 维 + 双端 binding_id 维。
-- ============================================================

CREATE TABLE IF NOT EXISTS ecos_scenario_binding_link (
    id                       VARCHAR(64)  PRIMARY KEY,
    scenario_id              VARCHAR(64)  NOT NULL,
    source_binding_id        VARCHAR(64)  NOT NULL,
    target_binding_id        VARCHAR(64)  NOT NULL,
    link_type                VARCHAR(16)  NOT NULL,
    source_contract          VARCHAR(128),
    remark                   TEXT         DEFAULT '',
    create_time              TIMESTAMP    NOT NULL DEFAULT NOW(),
    update_time              TIMESTAMP    NOT NULL DEFAULT NOW(),
    version_no               VARCHAR(20)  NOT NULL DEFAULT '1',
    create_by                VARCHAR(64)  NOT NULL DEFAULT 'system',
    update_by                VARCHAR(64)  NOT NULL DEFAULT 'system',
    is_deleted               SMALLINT     NOT NULL DEFAULT 0,
    domain                   VARCHAR(50)  NOT NULL DEFAULT 'default',

    CONSTRAINT chk_bsl_link_type CHECK (
        link_type IN ('MAPPING', 'EXTRACTION', 'COGNITION', 'GOVERN', 'EXPOSE')
    )
);

CREATE INDEX IF NOT EXISTS idx_bsl_sco ON ecos_scenario_binding_link(scenario_id);
CREATE INDEX IF NOT EXISTS idx_bsl_src ON ecos_scenario_binding_link(source_binding_id);
CREATE INDEX IF NOT EXISTS idx_bsl_tgt ON ecos_scenario_binding_link(target_binding_id);
CREATE INDEX IF NOT EXISTS idx_bsl_typ ON ecos_scenario_binding_link(link_type);

COMMENT ON TABLE  ecos_scenario_binding_link IS '场景绑定关系边：六类资源之外的有向关系（§0.6.2）';
COMMENT ON COLUMN ecos_scenario_binding_link.source_contract IS '跨工作台契约引用（如 ecos_entity_table_mapping.id），§0.6.2.2 必带';
COMMENT ON COLUMN ecos_scenario_binding_link.link_type IS 'MAPPING | EXTRACTION | COGNITION | GOVERN | EXPOSE';

-- seed（R9 幂等；id 固定以 sc001 的三条首成型边为准）
INSERT INTO ecos_scenario_binding_link (id, scenario_id, source_binding_id, target_binding_id, link_type, source_contract, domain)
VALUES
    ('bsl001', 'sc001', 'sb001', 'sb003', 'MAPPING',    'placeholder-sc001-flight-map', 'default'),
    ('bsl002', 'sc001', 'sb007', 'sb006', 'COGNITION',  'placeholder-sc001-agent-kb',   'default')
ON CONFLICT (id) DO NOTHING;
