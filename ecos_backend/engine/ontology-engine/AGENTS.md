# ontology-engine — 本体引擎

> 端口: **18083** | PMO: **ecos-arch** | 依赖: PostgreSQL, kb-engine-api (KG同步)

## 我负责的
- 本体模型（实体类型、属性、关系类型定义）
- 对象实例（本体模型下的实例对象CRUD）
- 版本管理（本体模型版本、对象版本）
- 工作流（审批、发布流程）
- 领域管理（业务域定义和隔离）
- 本体→KG同步（对象变更自动推送Neo4j）

## 我暴露的端点
| 端点 | 方法 | 用途 |
|------|------|------|
| /api/v1/ecos/ontologies | * | 本体模型CRUD |
| /api/v1/ecos/entities | * | 实体类型定义 |
| /api/v1/ecos/domains | * | 业务域管理 |
| /api/v1/ecos/versions | GET | 版本查询 |
| /api/v1/ecos/workflows | * | 工作流管理 |
| /api/v1/ecos/objects | * | 对象实例CRUD |

## 我的数据库表
- 本体模型表、实体类型表、对象实例表、关系表、版本表、工作流表

## 我依赖的外部端点
| 引擎 | 端点 | 用途 |
|------|------|------|
| kb-engine | POST :18086/api/v1/kb/graph/sync | 对象→KG同步 |

## 禁止
1. 不直接写Neo4j（通过kb-engine的graph/sync端点）
2. 本体模型变更不自动生效（需要版本发布流程）
3. 对象实例不支持物理删除（只标记逻辑删除）
