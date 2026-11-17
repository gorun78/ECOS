# ECOS Cross-Domain Relationships

## Schema Dependency Graph

```
ecos_sysman ←── ecos_security (user/role FK)
ecos_sysman ←── ecos_data (org FK)
ecos_ontology ←── ecos_cognitive (domain FK)
ecos_ontology ←── ecos_knowledge (glossary domain_id FK)
ecos_cognitive ←── ecos_cognitive (self-ref: parent_id, causal links)
```

## Inter-Domain Foreign Keys

| Source Schema | Source Table | FK Column | Target Schema | Target Table | Target Column |
|---|---|---|---|---|---|
| ecos_security | td_user_security_profile | user_id | ecos_sysman | td_user | user_id |
| ecos_security | td_role_security_profile | role_id | ecos_sysman | td_role | role_id |
| ecos_security | ecos_spans | tenant_id | ecos_sysman | ecos_tenant | id |
| ecos_data | td_datasource | org_id | ecos_sysman | td_organization | ORG_ID |
| ecos_data | td_data_resource | datasource_id | ecos_data | td_datasource | datasource_id |
| ecos_data | td_data_field | resource_id | ecos_data | td_data_resource | resource_id |
| ecos_data | ecos_pipeline_execution | pipeline_id | ecos_data | ecos_pipeline_definition | id |
| ecos_data | ecos_pipeline_node | definition_id | ecos_data | ecos_pipeline_definition | id |
| ecos_data | ecos_pipeline_edge | definition_id | ecos_data | ecos_pipeline_definition | id |
| ecos_data | ecos_dq_issue | rule_id | ecos_data | ecos_dq_rule | id |
| ecos_ontology | ecos_ontology_entity | domain_id | ecos_ontology | ecos_domain | id |
| ecos_ontology | ecos_ontology_property | entity_id | ecos_ontology | ecos_ontology_entity | id |
| ecos_ontology | ecos_business_glossary | domain_id | ecos_ontology | ecos_domain | id |
| ecos_knowledge | graph_edge | source_id | ecos_knowledge | graph_node | id |
| ecos_knowledge | graph_edge | target_id | ecos_knowledge | graph_node | id |
| ecos_knowledge | ecos_marketplace_access_request | asset_id | ecos_knowledge | ecos_marketplace_asset | id |
| ecos_ai | ecos_mission_task | mission_id | ecos_ai | ecos_mission | id |
| ecos_ai | agent_execution | agent_id | ecos_ai | agent_definition | id |
| ecos_ai | agent_execution_step | execution_id | ecos_ai | agent_execution | id |
| ecos_ai | simulation | scenario_id | ecos_ai | scenario | id |
| ecos_ai | simulation_result | simulation_id | ecos_ai | simulation | id |
| ecos_cognitive | ecos_wm_goal | parent_id | ecos_cognitive | ecos_wm_goal | id |
| ecos_cognitive | ecos_wm_goal | domain_id | ecos_ontology | ecos_domain | id |
| ecos_cognitive | ecos_wm_causal_link | source_goal_id | ecos_cognitive | ecos_wm_goal | id |
| ecos_cognitive | ecos_wm_causal_link | target_goal_id | ecos_cognitive | ecos_wm_goal | id |
| ecos_cognitive | ecos_wm_goal_log | goal_id | ecos_cognitive | ecos_wm_goal | id |
| ecos_cognitive | ecos_goal_tracking | goal_id | ecos_cognitive | ecos_wm_goal | id |
| ecos_cognitive | ecos_biz_project | goal_id | ecos_cognitive | ecos_wm_goal | id |
| ecos_cognitive | ecos_biz_contract | project_id | ecos_cognitive | ecos_biz_project | id |
| ecos_cognitive | ecos_biz_metric | goal_id | ecos_cognitive | ecos_wm_goal | id |
| ecos_cognitive | ecos_biz_target | goal_id | ecos_cognitive | ecos_wm_goal | id |

## Intra-Domain Relationships (Same Schema)

### ecos_sysman
- td_user_role.user_id → td_user.user_id
- td_user_role.role_id → td_role.role_id
- td_role_permission.role_id → td_role.role_id
- td_role_permission.permission_id → td_permission.permission_id
- td_user_organization.USER_ID → td_user.user_id
- td_user_organization.ORG_ID → td_organization.ORG_ID
- td_org_permission.org_id → td_organization.ORG_ID
- ecos_tenant_quota.tenant_id → ecos_tenant.id
- ecos_tenant_usage.tenant_id → ecos_tenant.id

### ecos_knowledge
- graph_edge.source_id → graph_node.id
- graph_edge.target_id → graph_node.id

### ecos_ai
- ecos_mission_task.mission_id → ecos_mission.id
- agent_execution.agent_id → agent_definition.id
- agent_execution_step.execution_id → agent_execution.id

### ecos_cognitive
- ecos_wm_goal.parent_id → ecos_wm_goal.id (self-ref)
- ecos_wm_causal_link.source_goal_id → ecos_wm_goal.id
- ecos_wm_causal_link.target_goal_id → ecos_wm_goal.id

## DIKW Dependency Direction

Per architecture constraint, knowledge layer must NOT depend on wisdom (AI/Cognitive) layer:

```
D (ecos_data) ← I (ecos_ontology) ← K (ecos_knowledge) ← W (ecos_ai, ecos_cognitive)
                                        ↑
                                   kb-engine owns this layer
```

- ecos_data: No dependency on K or W layers
- ecos_ontology: May reference ecos_data (via entity_code, table_name)
- ecos_knowledge: May reference ecos_ontology (via domain_id, entity_types)
- ecos_ai / ecos_cognitive: May reference ecos_knowledge (via knowledge graph, embeddings)
- **ecos_knowledge must NOT reference ecos_ai or ecos_cognitive tables**

## Application-Layer Cross-Domain References (Not FK-Enforced)

These are logical references via IDs/strings without FK constraints:
- ecos_ai.agent_execution → ecos_cognitive.ecos_wm_goal (goal text reference)
- ecos_cognitive.ecos_wm_goal.linked_workflow_id → ecos_ontology.ecos_workflow.id
- ecos_cognitive.ecos_biz_department → ecos_sysman.td_organization (logical org mapping)
