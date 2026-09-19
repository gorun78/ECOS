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
| /api/v1/engine/data/layers/{layer}/resources/{id}/rows | GET | 分层资源实例行增量读取（水位线，PMO-B3-1 T2） |
| /api/v1/engine/data/layers/{layer}/resources/{id}/sample | GET | 分层资源实例行抽样（PMO-B3-1 T2） |
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

## DQ 治理 API 路径表（PMO-48-A T3, 2026-09-10）

| 端点 | 方法 | 状态 | 用途 |
|------|------|------|------|
| /api/v1/dq/rules | GET | Phase 1 可用 | 规则列表（category/status/domain/ruleType/targetKind/keyword 过滤 + pageNum/pageSize 分页） |
| /api/v1/dq/rules/{id} | GET | Phase 1 可用 | 详情 + 版本历史（ecos_dq.dq_rule_version） |
| /api/v1/dq/rules/dimension-registry | GET | Phase 1 可用 | 6 维 rule_type 映射注册表（COMPLETENESS/ACCURACY/CONSISTENCY/FRESHNESS/UNIQUENESS/VALIDITY） |
| /api/v1/dq/rules | POST | 405 (Phase 2 再开) | 创建规则，写尝试计审计 |
| /api/v1/dq/rules/{id} | PUT/DELETE | 405 (Phase 2 再开) | 更新/删除规则，写尝试计审计 |

- Controller: `data-engine-impl/.../quality/controller/DqGovernanceController.java`（直接映射 /api/v1/dq，VersionPrefixRewriteFilter 中 dq 为 KEEP 不 rewrite）
- 数据层: MyBatis `DqRuleMapper` 读 `ecos_dq.dq_rule`（显式 schema 前缀；gateway `application.yml` 有 `map-underscore-to-camel-case`）
- 三滤波器: VersionPrefixRewriteFilter KEEP + SecurityConfig/ClearanceInterceptor 双路径（/api/v1/dq/** 与 /api/dq/**）+ application.yml auth.whitelist — T2 已交付
- 安全集成: `DqSecurityService`（Bean: `ecosDqSecurityService`，对齐 PipelineSecurityService 先例）— 响应前 mask 脱敏敏感字段（phone/mobile/idcard/bank_card/amount 等）；读/写操作异步 `POST /api/security/audit/log`；security-engine 不可用时默认脱敏/默认 DENY
- Bean 命名: `ecosDqGovernanceService`（Service 接口 `DqGovernanceService` 在 data-engine-api，不 implements 既有接口，防多 Bean 冲突）
- 完整方案: `docs/3-data/数据质量管理方案.md` §2（6 维度）；指令: `docs/3-data/PMO-48-A-数据质量基础设施.md`

## DQ 评分 SPI + Score API（PMO-48-B T8, 2026-09-10）

| 端点 | 方法 | 用途 |
|------|------|------|
| /api/v1/dq/scores?assetType=TABLE&assetId=xxx | GET | 当前资产评分（无评分 404） |
| /api/v1/dq/scores/trend?assetType=TABLE&assetId=xxx&days=30 | GET | 趋势（按天 × 维度，上限 365） |
| /api/v1/dq/scores/grade?grade=F | GET | 按等级扫描（A/B/C/D/F，空=全部） |
| /api/v1/dq/scores/system | GET | 系统级健康度 |
| /api/v1/dq/scores/recompute?assetType=TABLE&assetId=xxx | POST | 手动触发重算 |

### 6 个评估器 Bean（`data-engine-impl/.../quality/scoring/impl/`）

| Bean | 维度 | 默认权重 | rule_types |
|------|------|---------|-----------|
| `DqCompletenessEvaluator` | COMPLETENESS | 30 | NOT_NULL / PRESENCE |
| `DqAccuracyEvaluator` | ACCURACY | 25 | FORMAT / RANGE / REGEX |
| `DqConsistencyEvaluator` | CONSISTENCY | 15 | CONSISTENCY / STATISTICAL |
| `DqUniquenessEvaluator` | UNIQUENESS | 15 | UNIQUE |
| `DqValidityEvaluator` | VALIDITY | 10 | VALIDITY / ENUM |
| `DqFreshnessEvaluator` | FRESHNESS | 5 | FRESHNESS / TIMELINESS |

### 等级阈值（A ≥ 0.95 / B ≥ 0.85 / C ≥ 0.70 / D ≥ 0.50 / F < 0.50）

- 加权公式：`Σ(维度分 × defaultWeight) / Σ(defaultWeight)` → 写 `dq_score_asset.overall_score`
- `dq_score_snapshot` 每维度一行（scope_type=TABLE，weight=defaultWeight）
- `dq_score_asset` 按 (asset_type, asset_id) UPSERT，`last_evaluated_at = NOW()`
- 评分窗口：仅取最近 1h 内 `dq_rule_check`（`executed_at >= NOW() - interval '1 hour'`），避免跨窗污染
- 6 边界：sample 0 行 → 1.0；超时（Phase 3）→ 0.0 + detail.status=TIMEOUT；敏感字段 detail.values=***；executed_at null → 1.0（Freshness only）

### 关键文件

- SPI 抽象: `data-engine-api/.../quality/scoring/{DqDimension, DimensionEvaluator, DimensionScore, ScoringContext}.java`
- 引擎: `data-engine-impl/.../quality/scoring/service/DqScoreEngine.java`（@Component，构造器注入 `List<DimensionEvaluator>`）
- 服务: `data-engine-impl/.../quality/service/DqScoreServiceImpl.java`（@Service("ecosDqScoreService")+@Transactional，含 audit 安全卡）
- 控制器: `data-engine-impl/.../quality/controller/DqScoreController.java`（@RequestMapping("/api/v1/dq/scores")）
- VO: `data-engine-api/.../quality/model/{DqAssetScoreVO, DqScoreTrendVO, DqScoreSystemVO}.java`
- 方案: `docs/3-data/PMO-48-数据质量管理方案.md` §4

### 铁律

1. 评估器不写库（只算+返回，落库在 DqScoreService）
2. Phase 2 简化：评估器读 `dq_rule_check.{passed, total_rows, failed_rows, pass_rate}`，不拉 IStorageAdapter 样本（Phase 3 监控调度器再放开）
3. 不 implements 既有接口；不 throws 受检异常；Bean 名 `ecosDqScoreService`（铁律 1.3）
4. 安全卡（铁律 2.4 #3/#5）：评估器 detail.sensitiveMasked=true 当 target_field 命中；Service 每次 recompute 异步 `auditWrite(DQ_SCORE_RECOMPUTE, *:, SUCCESS)`
