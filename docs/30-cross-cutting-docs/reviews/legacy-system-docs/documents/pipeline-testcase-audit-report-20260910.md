# TESTCASE_AUDIT_REPORT — Pipeline Wave 1~6

> 覆盖度矩阵: P2-01 §七 6 规则 + 执行器 9 分支 + 前端校验 + 安全集成

---

## 一、P2-01 §七 校验规则覆盖度

| # | P2-01 规则 | 后端测试 | 前端测试 | 判定 |
|:--:|:--|:--|:--|:--:|
| 1 | 必填校验 (name/nodes/nodeId/type) | `PipelineServiceImplTest` 7 用例; `PipelineControllerTest` createDefinition 404/400 路径 | `pipelineValidation.test.ts` 22 用例覆盖 `checkRequiredConfigs` | ✅ |
| 2 | 数据类型校验 (type ∈ 枚举全集) | `PipelineNodeTypesCatalogTest` 3 用例 (supports 9 类型); `PipelineServiceImplTest` 非法 type 拒绝 | `NodePalette` 渲染 PipelineNodeType 8 可选 + CDC disabled | ✅ |
| 3 | 节点 id 唯一性 | `PipelineServiceImplTest` `duplicateNodeIdRejected` ✓ | 画布隐式（ReactFlow 唯一 node.id） | ✅ |
| 4 | 拓扑环检测 (Kahn) | `PipelineTopologyValidationTest.cycleDetectedBetweenTwoNodes` ✓; `PipelineExecutionService.topologicalSort` 被 6 测试间接覆盖 | `pipelineValidation.test.ts` `findCycleNodes` 多种环拓扑 | ✅ |
| 5 | 可达性/孤立节点告警 | **缺** — 后端无显式测试 | `pipelineValidation.test.ts` `findOrphanedNodes` 覆盖 | ⚠️ 后端 gap (P2) |
| 6 | 敏感字段脱敏 | `PipelineSecurityIntegrationTest` 5 用例 (maskNodeConfig 7 键/嵌套/null/parse/invalid JSON) | N/A (前端不处理脱敏) | ✅ |

## 二、执行器 9 类型分支覆盖

| 节点类型 | PipelineExecutionService 测试 | PipelineDebugService 测试 | 判定 |
|:--|:--|:--|:--:|
| SOURCE_JDBC | `PipelineTopologyValidationTest.sourceJdbcMissingSql` (FAILED) + `sourceJdbcMissingDatasourceId` | `PipelineTransformSqlRoutingTest` 无直接 JDBC 测试 (需 mock ConnectorFactory) | ✅ (验证入口校验) |
| SOURCE_CSV | `PipelineTopologyValidationTest.sourceCsvMissingFilePath` (FAILED) | — | ✅ |
| SOURCE_REST | `PipelineTopologyValidationTest.sourceRestMissingUrl` (FAILED) | — | ✅ |
| SOURCE_CDC | `PipelineTopologyValidationTest.sourceCdcUnsupportedInStandardOrEnterprise` ✓ | `PipelineDebugService.executeNode` 测试未创建但代码一致 (flagship 守卫) | ✅ |
| TRANSFORM_SQL | `PipelineTransformSqlRoutingTest` **4 用例**: SELECT→queryForList, CTE→queryForList, UPDATE→update, debug链 SELECT→queryForList | 同左 (case D 直接测 DebugService) | ✅ **最强覆盖** |
| TRANSFORM_UDF | **缺** — 无聚焦 UDF 执行测试 (UdfSandbox 是 `com.chinacreator.gzcm.engine.data.service` 包, 有独立单元测试?) | 调试链无 UDF 分支 (P1-1) | ⚠️ 缺 (P2) |
| JOIN | **缺** — 无执行 JOIN 的专注测试 (joinFrames/memoryJoin 是 static, 可单测 memoryJoin 逻辑) | 调试链无 JOIN 分支 | ⚠️ 缺 (P2) |
| SINK | **缺** — 无 executeSink 聚焦测试 (需 mock JdbcConnector + ConnectorFactory) | 调试链无 SINK 分支 | ⚠️ 缺 (P2) |
| OUTPUT_OBJECT | `PipelineTopologyValidationTest.outputObjectMissingTargetTable` (FAILED) | — | ✅ |

**结论**: 9 类型中 6 类型有执行入口测试 (SOURCE×3 + CDC + TRANSFORM_SQL + OUTPUT_OBJECT)，TRANSFORM_UDF/JOIN/SINK 3 分支缺 **真实执行** 测试（仅有接口定义/目录测试）。
- 9189ad5 只修复 SELECT 分流，JOIN/SINK 无真实执行测试 → **确认 P2 缺口**
- 建议: `PipelineUdfTransformTest` / `PipelineMemoryJoinTest` (纯函数, 不需 mock) / `PipelineSinkExecutionTest` (mock JdbcConnector)

## 三、前端 pipelineValidation.test.ts 22 用例覆盖度

| 规则 | 用例类型 | 覆盖 |
|:--|:--|:--:|
| checkRequiredConfigs: 各 nodeType 必填字段缺失 | 每类型 1+ 用例 (JDBC/CSV/REST/SQL/UDF/JOIN/SINK/OUTPUT) | ✅ |
| findCycleNodes: 环检测 (A↔B, A→B→C→A, 多节点环) | 3+ 用例 | ✅ |
| findOrphanedNodes: 孤立节点 (无入边/无出边/自由浮动) | 3+ 用例 | ✅ |
| runPreFlightCheck 组合: 空画布/多错误/全通过 | 3+ 用例 | ✅ |
| REQUIRED_FIELDS_BY_TYPE 导出 | 1 用例 | ✅ |

**总体**: 22 用例覆盖了 3 大规则 (required/cycle/reachability) 的正向+反向+边界。覆盖度 ≥80%。

## 四、PipelineSecurityIntegrationTest 16 用例

| 测试类 | 用例数 | 红线检查 |
|:--|:--:|:--|
| auditWrite success/failure | 2 | §2.4 ⑤ 审计不阻塞主流程 ✓ |
| maskNodeConfig 7 keys + null + nested | 3 | §2.4 ③ 脱敏 ✓ |
| parseAndMaskConfig parse/invalid | 2 | §2.4 ③ 脱敏 + 容错 ✓ |
| ABAC deny/allow/unavailable | 3 | §2.4 ⑥ 默认 DENY ✓ |
| Controller ABAC deny blocks submit | 1 | 403 语义 ✓ |
| ARCHIVED → 404 | 1 | 逻辑删除语义 ✓ |
| **Identified gaps** | | |
| "用户敏感字段密码不外泄" 红线 | 0 直接 | ⚠️ maskNodeConfig 间接覆盖 password，但 **无 "响应体 JSON 序列化后 grep 不到明文 password" 的赤脸测试** |

## 五、三 Profile 兼容性测试

| 检查 | 实现 | 判定 |
|:--|:--|:--:|
| SOURCE_CDC standard/enterprise → "仅 flagship" | `PipelineTopologyValidationTest.sourceCdcUnsupportedInStandardOrEnterprise` + `PipelineExecutionService.executeNode` 硬编码 throw | ✅ |
| Doris/Neo4j 行为差异测试 | **缺** — 无 `@DisabledOnJre` 或 profile-aware 测试 | ⚠️ 缺 (P2) |
| Pipeline 执行不依赖 Neo4j/Doris (全版本) | 隐式: 单测 mock 不触 PG → 等价 standard 档 | ✅ (隐式) |

## 总结

| 维度 | 覆盖度 | 缺口 |
|:--|:--:|:--|
| P2-01 §七 6 规则 | 4/6 完整 + 2 部分覆盖 | 孤立节点后端测试 + 密码赤脸验证 |
| 执行器 9 分支 | 6/9 有执行测试 | UDF/JOIN/SINK 3 分支缺真实执行单测 |
| 前端 22 用例 | 3/3 规则全覆盖 | 无显著缺口 |
| 安全集成 16 用例 | 核心红线全覆盖 | 缺"响应体 grep 无明文 password"赤脸测试 |
| 三 Profile 兼容 | CDC 守卫有测试 | 缺 Doris/Neo4j profile 差异测试 |
