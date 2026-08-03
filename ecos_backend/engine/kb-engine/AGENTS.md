# kb-engine — 知识库引擎

> 端口: **18086** | PMO: **ecos-arch** | 依赖: Neo4j(enterprise)

## 我负责的
- **KnowledgeGraphService**: Neo4j图查询、节点/关系CRUD
- **KnowledgeRetrievalService**: RAG向量检索 + 知识问答
- **RuleGraphService**: 规则KG关联（规则→实体→关系图）
- **KGWriterService**: SubGraph批量写入Neo4j
- **ExtractionSourceLoader**: 知识抽取源加载器
- **ExpertRuleService**: 规则CRUD + 版本管理
- **ComplianceRuleMapper**: 合规规则MyBatis数据访问

## 我暴露的端点
| 端点 | 方法 | 用途 |
|------|------|------|
| /api/v1/kb/graph/query | POST | KG Cypher查询 |
| /api/v1/kb/graph/nodes | POST | 节点CRUD |
| /api/v1/kb/graph/edges | POST | 关系CRUD |
| /api/v1/kb/rules | GET/POST/PUT/DELETE | 规则CRUD |
| /api/v1/kb/rules/versions | GET | 规则版本历史 |
| /api/v1/kb/articles | GET | 知识文章 |
| /api/v1/kb/rag | POST | RAG检索 |
| /api/v1/ecos/knowledge-graph | * | 知识图谱通用端点 |

## 我的数据库表
- compliance_rules (id, name, domain, condition, action, applicable_object_types, confidence, source_excerpt, status, version, ...)
- rule_versions (id, rule_id, version, condition, action, changed_by, changed_at)
- Neo4j: 知识图谱节点和关系

## 我依赖的外部端点
无。kb-engine是底层引擎，不依赖其他引擎。

## 禁止
1. 不执行规则判定（那是cognitive-engine的事）
2. 不直接调LLM（那是ai-engine的事）
3. Neo4j只在enterprise/flagship版本启用
