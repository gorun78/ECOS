# data-engine — 数据引擎

> 端口: **18082** | PMO: **ecos-be** | 依赖: PostgreSQL

## 我负责的
- 数据源管理（DB连接、文件、API）
- 数据管道（采集、清洗、转换、入湖）
- 数据目录（表结构、字段、统计）
- 数据血缘（字段级、表级、跨系统）
- 数据质量（规则配置、检查执行、报告）
- 数据管道任务调度

## 我暴露的端点
| 端点 | 方法 | 用途 |
|------|------|------|
| /api/v1/engine/data/health | GET | 健康检查 |
| /api/v1/engine/data/pipeline | POST | 管道CRUD |
| /api/v1/engine/data/lineage | GET | 血缘查询 |
| /api/v1/engine/data/quality | POST | 质量检查 |
| /api/v1/engine/data/query | POST | 数据查询 |
| /api/v1/engine/data/settings | GET/PUT | 引擎配置 |
| /api/v1/engine/data/layers | GET | 数据分层 |
| /api/v1/engine/data/functions | GET | 计算函数 |
| /api/v1/engine/data/copilot | POST | 数据Copilot |

## 我的数据库表
- 数据源定义表、管道任务表、血缘关系表、质量规则表
- 复用的PG业务表（由管道写入）

## 我依赖的外部端点
无。data-engine是底层引擎。

## 禁止
1. 不直接操作其他引擎的表
2. 管道不执行超过30分钟的同步任务
3. 血缘不追踪Neo4j内的关系（那是kb-engine的事）
