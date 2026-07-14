-- V46: 统一多态对象关系中间表
-- 支持 ObjectQL links 格式 B (relationCode + targetType) 的关系遍历。
-- ecos_object_data 是所有业务对象的统一 JSONB 存储表，
-- 对象间关系通过此中间表表达，避免为每种实体建表。

CREATE TABLE IF NOT EXISTS ecos_object_links (
    id            VARCHAR(64) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    source_id     VARCHAR(64)  NOT NULL,
    target_id     VARCHAR(64)  NOT NULL,
    relation_code VARCHAR(128) NOT NULL,
    created_at    TIMESTAMP    DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_obj_links_source   ON ecos_object_links(source_id);
CREATE INDEX IF NOT EXISTS idx_obj_links_target   ON ecos_object_links(target_id);
CREATE INDEX IF NOT EXISTS idx_obj_links_relation ON ecos_object_links(relation_code);

-- 种子关系数据：业务对象间的已知关系
INSERT INTO ecos_object_links (id, source_id, target_id, relation_code) VALUES
  ('link_c1_o1', 'obj_c001', 'obj_o001', 'PLACED'),
  ('link_c2_o2', 'obj_c002', 'obj_o002', 'PLACED'),
  ('link_s1_o1', 'obj_s001', 'obj_o001', 'SUPPLIES'),
  ('link_s2_o3', 'obj_s002', 'obj_o003', 'SUPPLIES'),
  ('link_o1_i1', 'obj_o001', 'obj_i001', 'INVOICED_AS'),
  ('link_p1_e1', 'obj_hw_p1', 'obj_hw_e1', 'USES'),
  ('link_o2_i2', 'obj_o002', 'obj_i002', 'INVOICED_AS')
ON CONFLICT (id) DO NOTHING;
