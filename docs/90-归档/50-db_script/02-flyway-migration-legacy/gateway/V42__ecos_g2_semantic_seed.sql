-- ============================================================
-- V42__ecos_g2_semantic_seed.sql
-- G2 统一业务语义 种子数据 — 高速信科场景
-- ============================================================

-- ── 1. Ontology 实体补充 ──────────────────────────────────────
INSERT INTO ecos_ontology_entity (id, code, name, description, entity_type, status)
VALUES
  ('ent_hs001', 'HS_Device',   '高速信科设备',   '高速公路沿线物联网设备数字孪生实体',   'MASTER',      'published'),
  ('ent_hs002', 'HS_Sensor',   '传感器',         '温度/振动/压力传感器采集实体',        'TRANSACTION', 'published'),
  ('ent_hs003', 'HS_Alert',    '告警事件',       '设备异常告警与运维事件实体',           'TRANSACTION', 'published'),
  ('ent_hs004', 'HS_Maintain', '运维工单',       '设备巡检与维修工单实体',               'TRANSACTION', 'published')
ON CONFLICT (id) DO NOTHING;

-- ── 2. Ontology Properties 属性 ───────────────────────────────
INSERT INTO ecos_ontology_property (id, entity_id, code, name, property_type, required_flag, searchable_flag)
VALUES
  ('prop_hs001', 'ent_hs001', 'device_code', '设备编码',   'STRING',  1, 1),
  ('prop_hs002', 'ent_hs001', 'mileage',     '里程桩号',   'STRING',  1, 1),
  ('prop_hs003', 'ent_hs001', 'device_type', '设备类型',   'STRING',  1, 1),
  ('prop_hs004', 'ent_hs002', 'temp_c',      '温度(℃)',   'DECIMAL', 1, 1),
  ('prop_hs005', 'ent_hs002', 'vibration',   '振动(mm/s)', 'DECIMAL', 0, 1),
  ('prop_hs006', 'ent_hs003', 'alert_level', '告警等级',   'STRING',  1, 1),
  ('prop_hs007', 'ent_hs003', 'alert_msg',   '告警描述',   'TEXT',    1, 0),
  ('prop_hs008', 'ent_hs004', 'maintain_type','维修类型',  'STRING',  1, 1),
  ('prop_hs009', 'ent_hs004', 'assignee',    '责任人',     'STRING',  1, 1)
ON CONFLICT (id) DO NOTHING;

-- ── 3. Ontology Relationships ─────────────────────────────────
INSERT INTO ecos_ontology_relationship (id, source_entity_id, target_entity_id, code, name, relationship_type)
VALUES
  ('rel_hs001', 'ent_hs001', 'ent_hs002', 'has_sensor',    '部署传感器',     'ONE_TO_MANY'),
  ('rel_hs002', 'ent_hs002', 'ent_hs003', 'triggers_alert', '触发告警',     'MANY_TO_ONE'),
  ('rel_hs003', 'ent_hs003', 'ent_hs004', 'creates_order',  '生成工单',     'ONE_TO_ONE'),
  ('rel_hs004', 'ent_hs001', 'ent_hs004', 'maintained_by',  '关联运维',     'ONE_TO_MANY')
ON CONFLICT (id) DO NOTHING;

-- ── 4. Glossary 术语补充 ──────────────────────────────────────
INSERT INTO ecos_glossary_term (name, definition, domain, status)
VALUES
  ('数字孪生',     '物理实体在数字空间的实时映射模型，支持双向数据同步与仿真',            'AI技术',    'PUBLISHED'),
  ('边缘计算',     '在靠近数据源头的网络边缘侧进行数据处理与分析的计算范式',              '技术架构',  'PUBLISHED'),
  ('OBU',          '车载单元(On-Board Unit)，高速公路ETC系统的车载电子标签设备',        '业务术语',  'PUBLISHED'),
  ('RSU',          '路侧单元(Road Side Unit)，部署在高速公路沿线的通信与感知设备',       '业务术语',  'PUBLISHED'),
  ('数据血缘',     '描述数据从源头到消费端的全链路流转关系，包括ETL转换和依赖关系',       '数据管理',  'PUBLISHED'),
  ('本体对齐',     '将不同数据源的异构模式映射到统一语义本体的过程，确保语义一致性',       'AI技术',    'PUBLISHED'),
  ('态势感知',     '通过对多源异构数据的实时融合分析，实现对系统运行状态的全面认知与预测', '安全合规',  'PUBLISHED'),
  ('知识图谱',     '以图结构组织实体及其关系的语义网络，支持推理与智能问答',               'AI技术',    'PUBLISHED')
ON CONFLICT DO NOTHING;

-- ── 5. Knowledge Graph 文档补充 ───────────────────────────────
INSERT INTO ecos_knowledge_document (id, title, content, doc_type, tags, entity_types)
VALUES
  ('kg_hs001', '高速信科设备运维知识库', 
   '本知识库涵盖高速公路沿线物联网设备的运维知识，包括设备分类、常见故障、维修流程等。设备类型：RSU路侧单元、OBU车载单元、摄像头、雷达等。', 
   'guide', '高速信科,运维,设备', 'HS_Device,HS_Maintain'),
  ('kg_hs002', 'ETC收费系统数据规范',
   'ETC电子不停车收费系统的数据格式规范，包括交易报文结构、OBU-RSU通信协议、收费计算规则等。',
   'standard', 'ETC,收费,数据规范', 'HS_Device'),
  ('kg_hs003', '公路桥梁结构健康监测指南',
   '基于传感器网络的桥梁结构健康监测技术指南，涵盖振动、应变、温度等多维感知数据的采集与分析。',
   'guide', '桥梁,监测,传感器', 'HS_Sensor,HS_Device')
ON CONFLICT (id) DO NOTHING;
