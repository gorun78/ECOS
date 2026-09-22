# ARCH_CONSISTENCY_REPORT — Pipeline Wave 1~6

> 审查维度: DTO 契约一致性 / 节点类型枚举一致性 / 校验规则一致性
> 依据: P2-01-Pipeline-Schema规范.md v2.0.0 + 架构铁律 §4.8

---

## 表 1: DTO 契约一致性（前端 ↔ 后端 PipelineSaveDTO ↔ PipelineVO）

| 契约项 | 前端 | 后端 DTO/VO | 判定 |
|:--|:--|:--|:--|
| Pipeline 定义 ID 字段 | `DataPipeline.id` (string) | `PipelineVO.id` (String) | ✅ |
| Pipeline 名称 | `DataPipeline.name` | `PipelineVO.name` (String) | ✅ |
| 状态枚举 | `'active'|'draft'|'running'|'success'|'error'` | `DRAFT/ACTIVE/ARCHIVED` (P2-01 §2.2) | ✅ (mapPipelineStatus 映射) |
| 节点 nodeId | `PipelineSaveNode.nodeId` | `PipelineSaveDTO.NodeSpec.nodeId` → `PipelineVO.NodeVO.nodeId` | ✅ |
| 节点 type | `PipelineSaveNode.type` (string) | `PipelineSaveDTO.NodeSpec.type` | ✅ |
| 节点 config | `PipelineSaveNode.config` (Record) | `PipelineSaveDTO.NodeSpec.config` (Map<String,Object>) → JSONB | ✅ |
| 节点 dependsOn | `PipelineSaveNode.edges` (from/to) | `PipelineSaveDTO.EdgeSpec.from/to` → `depends_on` JSONB | ✅ (edge→dependsOn 推导) |
| 节点 position | `PipelineSaveNode.positionX/Y` | `PipelineSaveDTO.NodeSpec.positionX/Y` | ✅ |
| 创建 API | `POST /api/v1/pipeline/definitions` | `PipelineController.createDefinition` | ✅ |
| 更新 API | `PUT /api/v1/pipeline/definitions/{id}` | `PipelineController.updateDefinition` | ✅ |
| 详情 API | `GET /api/v1/pipeline/definitions/{id}` (mapPipelineDef) | `PipelineController.getDefinition` → `toVO(def, true)` 含 nodes | ✅ |
| 列表 API | `GET /api/v1/pipeline/definitions` (listDefinitions) | `PipelineController.listDefinitions` → `toVO(def, false)` 摘要 | ✅ |
| 执行 API | `POST /api/v1/pipeline/definitions/{id}/execute` | `PipelineController.executeDefinition` | ✅ |
| 逻辑删除语义 | DELETE → status=ARCHIVED，列表过滤 `!= 'ARCHIVED'` | `PipelineRepository.deleteDefinition` → `UPDATE status='ARCHIVED'` | ✅ (P2-01 §7.10 + 架构铁律 4.8.4) |

**差异 1: 前端 `DataPipeline.status` 枚举与后端不完全对应**
- 前端 `DataPipeline` 状态包含 `'running'`/`'success'`/`'error'`，但后端 `PipelineDefinition.status` 仅 `DRAFT/ACTIVE/ARCHIVED`。
- 语义: 后端 status 是定义生命周期态；前端 status 混入了执行态（success/error 来自 execution 表）。
- 判定: ⚠️ 已知设计 — 前端 `mapPipelineStatus` 做了宽松的 default 映射（`default → 'draft'`）。不影响数据正确性，但语义清晰度待改善。

---

## 表 2: 节点类型枚举一致性（前端 PipelineNodeType ↔ 后端 PipelineNodeTypesCatalog ↔ 执行器 switch）

| 节点类型 | 前端 types.ts | PipelineNodeTypesCatalog.SUPPORTED | PipelineExecutionService.executeNode | PipelineDebugService.executeNode | P2-01 §4.1 | 判定 |
|:--|:--:|:--:|:--:|:--:|:--:|:--|
| SOURCE_JDBC | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| SOURCE_CSV | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| SOURCE_REST | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| SOURCE_CDC | ✅ | ✅ | ✅ (flagship 守卫) | ✅ (flagship 守卫) | ✅ | ✅ |
| TRANSFORM_SQL | ✅ | ✅ | ✅ (SELECT/SHOW/DESCRIBE/WITH → queryForList; DML → update) | ✅ (Wave 5b 修复后对齐) | ✅ | ✅ |
| TRANSFORM_UDF | ✅ | ✅ | ✅ | ❌ **缺** | ✅ | ⚠️ |
| JOIN | ✅ | ✅ | ✅ | ❌ **缺** | ✅ | ⚠️ |
| SINK | ✅ | ✅ | ✅ | ❌ **缺** | ✅ | ⚠️ |
| OUTPUT_OBJECT | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |

**差异 2: PipelineDebugService 缺 3 分支（TRANSFORM_UDF/JOIN/SINK）**
- 生产执行链（PipelineExecutionService）完整覆盖 9 类型。
- 调试链（PipelineDebugService）仅覆盖 6 类型，TRANSFORM_UDF/JOIN/SINK 走 default → 抛 ValidationException。
- 判定: ⚠️ 与架构铁律 §4.8.2 "前端节点面板、后端执行器、测试数据三方对齐" 相关 — 前端 NodePalette 已渲染 9 节点，但调试执行器缺 3 分支。P2-01 §4 将 TRANSFORM_UDF/JOIN/SINK 标注为 "预留"，当前执行器已实现但调试链未跟进属已知分阶段差距（归入 P1-1）。

**差异 3: 前端 `NodeConfig.config` 字段命名 vs P2-01 §4.2 config schema**
- 前端 `TRANSFORM_SQL` 使用 `transformSql` 属性名（`REQUIRED_CONFIG_FIELDS.TRANSFORM_SQL: ['transformSql']`），P2-01 §4.2 定义为 `sql`。
- 前端 `SINK` 使用 `targetTable`/`targetDatasourceId`，P2-01 §4.2 SINK 定义为 `table`/`datasourceId`。
- 判定: ⚠️ 命名偏移 — 后端 `PipelineExecutionService` 同时兼容双别名（`configFirst(config, "table", "targetTable")`、`configFirst(config, "datasourceId", "targetDatasourceId")`），实际运行不受影响。但 P2-01 规范应与实际选择统一（建议 P2-01 追加兼容字段说明）。

---

## 表 3: 校验规则一致性（P2-01 §七 6 条 vs 前后端实现）

| P2-01 §七 规则 | 后端实现 | 前端实现 | 判定 |
|:--|:--|:--|:--|
| ① 必填校验: name 非空; 至少 1 节点; nodeId/type 非空 | `PipelineServiceImpl.validateSaveDto`: name 非空 ✓; `isCreate && nodes.isEmpty` ✓; `nodeId` 非空 ✓; `type` 非空（`VALID_NODE_TYPES.contains`）✓ | `pipelineValidation.ts runPreFlightCheck`: canvasEmpty ✓; `checkRequiredConfigs` 按 nodeType 必填字段 ✓ | ✅ |
| ② 数据类型校验: type ∈ 枚举全集 | `PipelineServiceImpl.validateSaveDto`: `VALID_NODE_TYPES.contains(node.getType())` → P2-01 §4.1 9 类 ✓ | `NodePalette` 渲染 PipelineNodeType 8 类（不含 SOURCE_CDC 可选项）; P2-01 标注 CDC 仅 flagship ✓ | ✅ |
| ③ 节点 id 唯一性: 同一定义内 nodeId 不可重复 | `PipelineServiceImpl.validateSaveDto`: `seenNodeIds.add(nodeId)` 检测重复 ✓ | 前端无显式重复检查（画布 ReactFlow 天然唯一 node id）| ✅ (后端兜底) |
| ④ 拓扑可达性 / 环检测: Kahn 排序 | `PipelineExecutionService.topologicalSort`: Kahn → 未排出全部 → BusinessException("循环依赖") ✓; `PipelineDebugService.topologicalSort`: 同构（但有 P1-2 bug）| `pipelineValidation.ts findCycleNodes`: Kahn 同构 ✓ | ✅ (调试链 bug 见 P1-2) |
| ⑤ 可达性校验: 孤立节点告警 | **后端无显式检查**（执行时孤立节点无法被拓扑序触及，会静默跳过） | `pipelineValidation.ts findOrphanedNodes`: 正向 BFS from SOURCE + 反向 BFS to SINK ✓ | ⚠️ 后端缺 |
| ⑥ 敏感字段脱敏: VO 序列化前脱敏 | `PipelineSecurityService.maskNodeConfig` 7 键 + `parseAndMaskConfig` + `PipelineServiceImpl.toVO` 调用 ✓ | N/A（前端不需要）| ✅ |

**差异 4: P2-01 §7 第 5 条 "可达性告警" 后端未实现**
- P2-01 原文: "每个节点必须可达（有入边/出边或为源/汇节点）；孤立节点既无 dependsOn 也无下游 → 告警"。
- 后端: `PipelineServiceImpl.createDefinition` 未做孤立节点检测。执行期 `topologicalSort` 不会报错（孤立节点入度=0，被正常排出），但它没有下游 → 实际效果是白执行。
- 前端: `findOrphanedNodes` 已实现并作为 pre-flight 阻断。
- 判定: ⚠️ 前端已覆盖，后端 gap 为 P2（前端做了第一道防线，后端未做第二道）。建议后续 wave 补后端 check。

---

## 差异对照总结

| # | 差异 | 严重度 | 说明 |
|:--:|:--|:--:|:--|
| D1 | 前端 DataPipeline.status 混入执行态 (running/success/error) | P2 | mapPipelineStatus 宽松映射，不影响功能 |
| D2 | PipelineDebugService 缺 3 分支 (UDF/JOIN/SINK) | **P1** | 调试链执行 UDF/JOIN/SINK 必然失败（P1-1） |
| D3 | 前端 TRANSFORM_SQL 用 `transformSql` vs P2-01 `sql`; SINK 用 `targetTable` vs P2-01 `table` | P2 | 后端兼容双别名，运行无影响；P2-01 需补兼容说明 |
| D4 | P2-01 §7 第 5 条 "孤立节点告警" 后端未实现 | P2 | 前端 pre-flight 已覆盖，后端 gap 为已知 |
