-- ============================================================
-- V43__ecos_g3_operations_seed.sql
-- G3 统一运营平台种子数据
-- Workflow Designer + Policy Engine 示例数据
-- ============================================================

-- 1. Workflow Designer: 添加 Agent 节点流程示例
INSERT INTO ecos_workflow (id, name, description, status, mode, nodes, edges, created_at, updated_at)
SELECT 'wf004', '供应商风险审查', 'AI Agent驱动的供应商风险自动化审查流程',
       'draft', 'sequential',
       '[
          {"id":"n1","name":"开始审查","nodeType":"START"},
          {"id":"n2","name":"KYC信息核验","nodeType":"AGENT_NODE","config":{"agent_profile":"supplier_auditor","tools":["object_query","knowledge_search"]}},
          {"id":"n3","name":"风险评分>80?","nodeType":"GATEWAY","config":{"expression":"risk_level > 80","field":"risk_level","routes":{"high":"n4","low":"n6"}}},
          {"id":"n4","name":"人工复核","nodeType":"HUMAN_TASK","config":{"assigneeRole":"risk_manager"}},
          {"id":"n5","name":"生成审计报告","nodeType":"AGENT_NODE","config":{"agent_profile":"data_analyst","tools":["object_query","graph_query"],"input_mapping":{"entity_type":"Supplier"},"output_mapping":{"report_url":"${agent.extracted.report_url}"}}},
          {"id":"n6","name":"审查完成","nodeType":"END"}
       ]',
       '[
          {"id":"e1","source":"n1","target":"n2"},
          {"id":"e2","source":"n2","target":"n3"},
          {"id":"e3","source":"n3","target":"n4","label":"高风险"},
          {"id":"e4","source":"n3","target":"n6","label":"低风险"},
          {"id":"e5","source":"n4","target":"n5"},
          {"id":"e6","source":"n5","target":"n6"}
       ]',
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM ecos_workflow WHERE id = 'wf004');

INSERT INTO ecos_workflow (id, name, description, status, mode, nodes, edges, created_at, updated_at)
SELECT 'wf005', '设备故障应急响应', '高速信科设备故障自动诊断与派工流程',
       'draft', 'sequential',
       '[
          {"id":"n1","name":"故障告警触发","nodeType":"START"},
          {"id":"n2","name":"诊断分析","nodeType":"AGENT_NODE","config":{"agent_profile":"data_analyst","tools":["object_query","graph_query","knowledge_search"]}},
          {"id":"n3","name":"严重程度判断","nodeType":"GATEWAY","config":{"expression":"severity == \"Critical\"","field":"severity","routes":{"Critical":"n4","Warning":"n5"}}},
          {"id":"n4","name":"紧急派工","nodeType":"HUMAN_TASK","config":{"assigneeRole":"maintenance_lead"}},
          {"id":"n5","name":"常规维保","nodeType":"HUMAN_TASK","config":{"assigneeRole":"maintenance_engineer"}},
          {"id":"n6","name":"生成维修工单","nodeType":"AGENT_NODE","config":{"agent_profile":"coordinator","tools":["workflow_start"],"input_mapping":{"entity_type":"Facility"}}},
          {"id":"n7","name":"响应完成","nodeType":"END"}
       ]',
       '[
          {"id":"e1","source":"n1","target":"n2"},
          {"id":"e2","source":"n2","target":"n3"},
          {"id":"e3","source":"n3","target":"n4","label":"Critical"},
          {"id":"e4","source":"n3","target":"n5","label":"Warning"},
          {"id":"e5","source":"n4","target":"n6"},
          {"id":"e6","source":"n5","target":"n6"},
          {"id":"e7","source":"n6","target":"n7"}
       ]',
       NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM ecos_workflow WHERE id = 'wf005');
