-- ============================================================
-- PMO-12 ActionType: ecos_action_type 建表 DDL
-- 本体引擎 (ontology-engine) 新增
-- ============================================================

CREATE TABLE IF NOT EXISTS ecos_action_type (
    id             VARCHAR(64)  PRIMARY KEY,
    name           VARCHAR(128) NOT NULL UNIQUE,
    description    TEXT,
    object_type_id VARCHAR(64)  NOT NULL,
    preconditions  TEXT,
    post_actions   TEXT,
    audit_required BOOLEAN      DEFAULT true,
    enabled        BOOLEAN      DEFAULT true,
    created_by     VARCHAR(64),
    created_at     TIMESTAMP    DEFAULT NOW(),
    updated_at     TIMESTAMP    DEFAULT NOW()
);

COMMENT ON TABLE ecos_action_type IS '动作类型定义 — PMO-12 本体引擎 ActionType';
COMMENT ON COLUMN ecos_action_type.id IS '主键';
COMMENT ON COLUMN ecos_action_type.name IS '动作名称（唯一）';
COMMENT ON COLUMN ecos_action_type.description IS '动作描述';
COMMENT ON COLUMN ecos_action_type.object_type_id IS '关联的对象类型 ID';
COMMENT ON COLUMN ecos_action_type.preconditions IS '前置条件 JSON';
COMMENT ON COLUMN ecos_action_type.post_actions IS '后置动作 JSON';
COMMENT ON COLUMN ecos_action_type.audit_required IS '是否需要审计';
COMMENT ON COLUMN ecos_action_type.enabled IS '是否启用';
COMMENT ON COLUMN ecos_action_type.created_by IS '创建人';
COMMENT ON COLUMN ecos_action_type.created_at IS '创建时间';
COMMENT ON COLUMN ecos_action_type.updated_at IS '更新时间';

-- 索引
CREATE INDEX IF NOT EXISTS idx_ecos_action_type_object_type_id ON ecos_action_type(object_type_id);
CREATE INDEX IF NOT EXISTS idx_ecos_action_type_enabled ON ecos_action_type(enabled);
