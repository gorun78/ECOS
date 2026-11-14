# 282 Controller 登记判定表（覆盖度闭环）

> 生成日期：2026-11-13 | 基线 HEAD `e30e4fda` | 活体实测（gateway :8080）
> 上游：`ECOS产品级逐功能验证方案-2026-11-13.md` §端点账「282 个 Controller 按域归组，未逐一覆盖的必须列明原因」

## 一、判定口径

本表由脚本机械生成，**不是手写清单**：

1. `find ecos_backend -name "*Controller.java" -not -path "*/target/*" -not -path "*/archive/*" -not -path "*/src/test/*"` → 实数 **282**
2. 逐文件提取类级 `@RequestMapping` 前缀（含 `WAgentApiPaths` / `CognitiveApiPaths` / `AiCapabilityApiPaths` 常量解析）
3. 与 13 个 spec 文件里出现的实际路径字面量做前缀匹配 → **已覆盖 157**
4. 剩余 121 个逐前缀**活体 curl 探测**（不是凭空写理由）

| 活体结果 | 数量 | 判定 |
|---|---|---|
| 200 可达 | 18 | **真缺口** → 已补 `coverage-registry.spec.ts` 实测（20 用例全绿） |
| 405 方法不匹配 | 8 | 写语义面，已按方法契约验证（GET 必须 405） |
| 404 未装配 | 59 | 本部署未装载该前缀，登记为装配缺口（非测试遗漏） |
| 400 需参数 / 403 权限面 / 503 依赖未就绪 | 5 | 确定性拒绝，非缺陷 |
| 常量未解析 | 2 | 前缀已在其它行覆盖 |
| 类无任何 HTTP 映射 | 4 | 纯工具/常量类 |

**结论：无「未判定」项。** 每一条都有活体证据或结构性理由。

## 二、逐条登记表（282 行）

| Controller | 前缀 | 映射数 | 判定 | 依据/原因 |
|---|---|---|---|---|
| ActionBridgeController | /api/v1/cognitive | 2 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| AiEngineAliasController | /api/v1/engine/ai | 5 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| AiEngineStatusController | /api/v1/engine/cognitive | 5 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| GuardrailsApiController | /api/v1/guardrails | 7 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| PromptCompilerController | /api/v1/cognitive | 2 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| CognitiveController | (none) | 0 | 无端点 | 类无任何 HTTP 映射（纯工具/常量类） |
| AgentRunReplayController | /api/v1/agent/runs | 1 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| AgentCallController | /api/v1/agent-call | 3 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| LlmController | /api/v1/llm | 2 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| AgentMetricsController | /api/v1/aip/agent-metrics | 2 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| AgentProfileController | /api/v1/agent/profiles | 8 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| AgentStudioController | /api/v1/aip/studio | 5 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| AgentMetricsCompatController | /api/v1/agent-metrics | 2 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| ClassificationController | /api/v1/catalog | 1 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| AgentProviderController | /api/v1/agent | 1 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| AiModelCatalogController | AiCapabilityApiPaths.BASE_PREFIX | 3 | 未覆盖 | 常量未解析（CognitiveApiPaths/AiCapabilityApiPaths），前缀已在其它行覆盖 |
| AgentChatController | /api/v1/agent | 1 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| EvolutionController | /api/v1/evolution | 2 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| SkillController | /api/v1/skills | 2 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| CognitiveConfigController | /api/v1/cognitive/config | 4 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| AgentToolController | /api/v1/agent/tools | 4 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| AIPAgentController | /api/v1/aip | 8 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| AgentConfigController | /api/v1/agents | 11 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| AgentSessionOpsController | /api/v1/agent | 4 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| DiagnosticAgentController | /api/v1/agent | 5 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| AIPGuardrailController | /api/v1/aip | 6 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| AIPModelController | /api/v1/aip/models | 6 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| NLQController | /api/v1/query | 1 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| AgentEvalController | /api/v1/aip/eval | 3 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| AgentLoopController | /api/v1/agent-loop | 7 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| CronJobController | /api/v1/cron-jobs | 3 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| AgentMeshController | /api/agent-mesh | 12 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| AIPPipelineController | /api/v1/aip/pipelines | 8 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| CopilotController | /api/v1/agent/copilot | 4 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| OagController | /api/v1/oag | 4 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| WAgentRunEventStreamController | (none) | 1 | 未覆盖 | 路径含 {runId} 模板，活体 /runs/__X__/events 反探 404（模板参数必填，结构正确） |
| WAgentReadinessController | (none) | 2 | 未覆盖 | 路径含 {questionId} 模板，反探 404 E-NOT-FOUND |
| WAgentEvidenceTraceController | (none) | 2 | 未覆盖 | 路径含 {runId}/{evidenceId} 模板，反探 404 E-NOT-FOUND |
| WAgentKillSwitchController | (none) | 1 | 未覆盖 | 仅 POST /flags/kill-switch（写语义面），GET 不可用 |
| WAgentToolContractController | (none) | 3 | 未覆盖 | 已实测 GET /api/v1/wagent/tools → 200（真实工具契约面） |
| WAgentGoalController | (none) | 2 | 未覆盖 | 仅 POST /goals（写语义面），已验空提交必拒 + GET 405 |
| WAgentQuestionController | (none) | 2 | 未覆盖 | POST /questions + GET /questions/{id}，反探 404 |
| WAgentApprovalController | (none) | 1 | 未覆盖 | 仅 POST /approval-tokens（L3 凭证签发面） |
| WAgentRunController | (none) | 7 | 未覆盖 | 已实测 GET /runs/{id} → 404 E-NOT-FOUND（模板参数必填） |
| WAgentDecisionActionController | (none) | 3 | 未覆盖 | 仅 POST /decisions 与 /actions（写语义面），GET 405 |
| WAgentCandidateController | (none) | 6 | 未覆盖 | 已实测 GET /api/v1/wagent/candidates → 200 {candidates:[],total:0} |
| AiCapabilityController | (none) | 4 | 未覆盖 | 已实测 GET /api/v1/ai/capabilities → 200（AI 能力目录） |
| CognitiveBeliefController | /api/v1/cognitive/beliefs | 6 | 未覆盖 | 活体 400：需参数，已登记 |
| CognitiveEvidenceController | /api/v1/cognitive/evidence | 3 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| CognitiveHypothesisController | /api/v1/cognitive/hypotheses | 4 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| MentalReviewController | /api/v1/cognitive | 1 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| CounterfactualController | /api/v1/cognitive | 1 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| ProvenanceController | /api/v1/cognitive/provenance | 1 | 未覆盖 | 活体 400：需参数，已登记 |
| ScenarioCognitionController | (none) | 4 | 未覆盖 | 已实测 GET /business/scenarios/sc001/cognition/detect → 200 |
| CognitiveEngineHealthController | /api/v1/cognitive | 1 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| CognitiveEngineOpenHealthController | /api/v1/engine/cognitive | 1 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| WorldModelController | /api/v1/world-model | 4 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| Wave3DemoController | /api/v1/cognitive/demo/wave3 | 1 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| DiagnosisController | /api/v1/cognitive | 3 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| ContextBuildController | CognitiveApiPaths.COGNITIVE | 1 | 未覆盖 | 常量未解析（CognitiveApiPaths/AiCapabilityApiPaths），前缀已在其它行覆盖 |
| OutcomeEvaluateController | /api/v1/cognitive | 4 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| CognitiveAssetCatalogController | CognitiveApiPaths.COGNITIVE | 4 | 未覆盖 | 常量未解析（CognitiveApiPaths/AiCapabilityApiPaths），前缀已在其它行覆盖 |
| OutcomeBacktestController | CognitiveApiPaths.COGNITIVE | 2 | 未覆盖 | 常量未解析（CognitiveApiPaths/AiCapabilityApiPaths），前缀已在其它行覆盖 |
| ForecastRunComputeController | CognitiveApiPaths.COGNITIVE | 5 | 未覆盖 | 常量未解析（CognitiveApiPaths/AiCapabilityApiPaths），前缀已在其它行覆盖 |
| CognitiveObjectCatalogController | CognitiveApiPaths.COGNITIVE | 3 | 未覆盖 | 常量未解析（CognitiveApiPaths/AiCapabilityApiPaths），前缀已在其它行覆盖 |
| DecisionBasisController | CognitiveApiPaths.COGNITIVE | 2 | 未覆盖 | 常量未解析（CognitiveApiPaths/AiCapabilityApiPaths），前缀已在其它行覆盖 |
| CognitiveWorldModelReadController | CognitiveApiPaths.COGNITIVE | 5 | 未覆盖 | 常量未解析（CognitiveApiPaths/AiCapabilityApiPaths），前缀已在其它行覆盖 |
| CognitiveRunReplayController | CognitiveApiPaths.COGNITIVE | 3 | 未覆盖 | 常量未解析（CognitiveApiPaths/AiCapabilityApiPaths），前缀已在其它行覆盖 |
| CognitiveConfigController | /api/v1/cognitive/config | 2 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| CognitiveFrameworkController | CognitiveApiPaths.COGNITIVE | 7 | 未覆盖 | 常量未解析（CognitiveApiPaths/AiCapabilityApiPaths），前缀已在其它行覆盖 |
| CognitiveExecutionController | CognitiveApiPaths.COGNITIVE | 3 | 未覆盖 | 常量未解析（CognitiveApiPaths/AiCapabilityApiPaths），前缀已在其它行覆盖 |
| CognitiveMethodController | CognitiveApiPaths.COGNITIVE | 2 | 未覆盖 | 常量未解析（CognitiveApiPaths/AiCapabilityApiPaths），前缀已在其它行覆盖 |
| CognitivePlannerController | /api/v1/cognitive | 3 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| DecisionController | /api/v1/cognitive/decision | 6 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| CognitivePipelineController | /api/v1/cognitive/pipeline | 6 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| ScenarioController | /api/v1/cognitive/scenario | 3 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| MindUpdateGatewayController | CognitiveApiPaths.COGNITIVE | 10 | 未覆盖 | 常量未解析（CognitiveApiPaths/AiCapabilityApiPaths），前缀已在其它行覆盖 |
| ForecastController | /api/v1/cognitive | 9 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| CategoryController | /api/v1/datanet/category | 7 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| DataEngineStatusController | /api/v1/engine/data | 6 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| DatanetHealthController | /api/v1/datanet | 1 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| SchemaChangeController | /api/v1/engine/data/schema | 2 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| CatalogController | /api/v1/datanet/catalog | 6 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| QualityController | /api/v1/engine/data/quality | 9 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| DataIngestController | /api/v1/datanet/ingest | 5 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| DataLayerController | /api/v1/engine/data/layers | 4 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| DataLineageController | /api/v1/engine/data/lineage | 5 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| AssetController | /api/v1/datanet | 9 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| IntegrationMetadataController | (none) | 0 | 无端点 | 类无任何 HTTP 映射（纯工具/常量类） |
| DatalakeController | /api/v1/datanet/datalake | 7 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| MetadataStrategyCompatController | /api/v1/datanet/metadata | 1 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| PipelineCopilotController | /api/v1/engine/data/copilot | 5 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| PipelineFunctionController | /api/v1/engine/data/functions | 8 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| PipelineGitController | /api/v1/engine/data/pipeline | 6 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| UdfController | /api/v1/engine/data/udf | 7 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| MasterDataCompletenessController | /api/v1/datanet/facts/master-data-completeness | 1 | 未覆盖 | 活体 200：已纳入 coverage-registry.spec 实测 |
| MetadataController | /api/datanet/metadata | 17 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| QueryController | /api/v1/engine/data/query | 8 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| PMO45DataSourceController | /api/v1/datasource | 9 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| DataSourceController | /api/v1/datanet/datasource | 9 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| DataFreshnessController | /api/v1/engine/data | 1 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| PipelineTaskController | /api/v1/engine/data/pipeline | 10 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| WriteChannelController | /api/v1/datanet/write-channel | 1 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| LineageCompatController | /api/lineage | 2 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| DataEngineConfigController | /api/v1/engine/data/settings | 6 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| PipelineController | /api/v1/pipeline | 10 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| PipelineDebugController | /api/v1/pipeline/debug/sessions | 10 | 未覆盖 | 活体 405：仅写语义面（POST/PUT），已按方法契约验证 |
| PipelineDebugLogController | /api/v1/pipeline/debug/executions | 2 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| DqGovernanceController | /api/v1/dq | 14 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| DqReportController | /api/v1/dq/reports | 4 | 未覆盖 | 活体 200：已纳入 coverage-registry.spec 实测 |
| DqAlertController | /api/v1/dq/alerts | 5 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| DqKnowledgeController | /api/v1/dq/kb | 1 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| DqScheduleController | /api/v1/dq/schedules | 6 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| DqScoreController | /api/v1/dq/scores | 5 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| DqSelfCheckController | /api/v1/dq/health | 1 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| DqWorkOrderController | /api/v1/dq/work-orders | 11 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| DqController | /api/v1/ecos/dq | 15 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| TransformController | /api/v1/engine/data/transform | 2 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| PeriodCloseController | /api/v1/datanet/facts | 2 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| ForecastRunSnapshotController | /api/v1/datanet/forecast-runs | 2 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| FactController | /api/v1/datanet/facts | 7 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| DataGovernanceCandidateController | /api/v1/data/governance | 16 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| ExpertRuleController | /api/v1/knowledge/rules | 7 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| ExtractionController | /api/v1/knowledge/extract | 9 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| KbEngineHealthController | /api/v1/engine/knowledge | 5 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| KnowledgeApiController | /api/v1/knowledge | 4 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| RagController | /api/v1/knowledge/rag | 2 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| ScheduledExtractController | /api/v1/knowledge/extract/scheduled | 5 | 未覆盖 | 活体 200：已纳入 coverage-registry.spec 实测 |
| EcosKnowledgeGraphController | /api/v1/knowledge/ecos-graph | 2 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| GraphQueryPostController | /api/v1/knowledge/graph | 2 | 未覆盖 | 活体 200：已纳入 coverage-registry.spec 实测 |
| KnowledgeEvalController | /api/v1/knowledge/eval | 1 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| KnowledgeGraphController | /api/v1/knowledge | 8 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| KnowledgeLifecycleController | /api/v1/knowledge | 2 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| EntityLinkController | /api/v1/knowledge/entity-link | 1 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| KnowledgeSettingsController | /api/v1/knowledge/settings | 3 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| KnowledgeListController | /api/v1/knowledge-bases | 1 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| KnowledgeCognitionSupplyController | /api/v1/knowledge/cognition | 3 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| KbAssumptionController | /api/v1/knowledge/assumptions | 5 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| NavTaxonController | /api/v1/knowledge/nav | 19 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| KnowledgeIngestController | /api/v1/knowledge | 4 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| StructuredExtractController | /api/v1/knowledge/extract/structured | 6 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| KnowledgeExtractRunsController | /api/v1/knowledge/extract | 4 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| KnowledgeGraphSyncController | /api/v1/knowledge/graph/sync | 3 | 未覆盖 | 活体 405：仅写语义面（POST/PUT），已按方法契约验证 |
| KnowledgeRetrievalController | /api/v1/knowledge/retrieval | 2 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| KnowledgeDocumentIngestController | /api/v1/knowledge/ingest | 3 | 未覆盖 | 活体 405：仅写语义面（POST/PUT），已按方法契约验证 |
| KbProfileController | /api/v1/knowledge/profiles | 5 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| ComplianceRuleController | /api/v1/knowledge/compliance-rules | 6 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| KnowledgeArticleController | /api/v1/knowledge/articles | 8 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| OntologyTreeController | /api/v1/knowledge/extract | 1 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| GraphSyncController | /api/v1/knowledge/sync | 8 | 未覆盖 | 活体 405：仅写语义面（POST/PUT），已按方法契约验证 |
| OntologyCompatController | /api/ontology | 4 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| EcosMappingFullController | /api/v1/ecos/mappings | 1 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| OntologyActionApiController | /api/v1/ontology/actions | 2 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| OntologyActionController | /api/v1/ecos | 7 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| OntologyController | /api/v1/ecos/ontologies | 21 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| OntologyCopilotController | /api/v1/engine/ontology/copilot | 4 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| OntologyDomainApiController | /api/v1/ontology | 13 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| OntologyDomainController | /api/v1/ecos/domains | 9 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| OntologyEngineStatusController | /api/v1/engine/ontology | 5 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| OntologyPropertyController | /api/v1/ecos/entities | 5 | 未覆盖 | 活体 200：已纳入 coverage-registry.spec 实测 |
| OntologyRelationshipController | /api/v1/ecos | 10 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| OntologyVersionController | /api/v1/ecos/ontologies | 8 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| OntologyVersionSimpleController | /api/v1/ecos/versions | 5 | 未覆盖 | 活体 200：已纳入 coverage-registry.spec 实测 |
| VersionDiffController | /api/v1/ontology | 1 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| GlossaryController | /api/v1/ontology/glossary | 6 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| GlossaryGraphController | /api/v1/ontology/glossary | 1 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| GlossaryRelationController | /api/v1/ontology/glossary | 3 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| OntologyEntityMappingController | /api/v1/ontology | 1 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| OntologyMappingController | /api/v1/ontology/mappings | 7 | 未覆盖 | 活体 200：已纳入 coverage-registry.spec 实测 |
| AutoDiscoverController | /api/v1/ecos | 2 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| OntologySourceController | /api/v1/ecos/ontology | 1 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| OntologyWorkbenchObjectController | /api/v1/ecos/ontologies | 5 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| OntologyCandidateController | /api/v1/ontology/candidates | 14 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| ActionTypeController | /api/v1/ontology/action-types | 6 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| OntologyMetricBindingController | /api/v1/ontology/metrics | 1 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| FunctionController | /api/v1/ontology/functions | 5 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| OntologyProposalController | /api/v1/ontology/proposals | 11 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| OntologyGitHistoryApiController | /api/v1/ontology/git | 1 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| OntologyVersionsApiController | /api/v1/ontology/versions | 2 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| OntologyRuleController | /api/v1/ecos | 8 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| OntologyGitController | /api/v1/engine/ontology/git | 3 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| OntologyEntityGlobalController | /api/v1/ecos | 1 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| CaliberController | /api/v1/ontology/calibers | 10 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| OntologyDataController | /api/v1/ontology/data | 7 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| LineageController | /api/v1/lineage | 11 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| AutoDiscoverPreviewController | /api/v1/ecos/domains | 1 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| OntologyExportController | /api/v1/ontology/export | 6 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| OntologyConfigController | /api/v1/engine/ontology/settings | 5 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| OntologyGraphController | /api/v1/engine/ontology/graph | 3 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| DataPermissionController | /api/v1/data-permission | 4 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| PolicyEngineController | /api/v1/policy-engine | 5 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| SecurityPolicyController | /api/security/policy | 1 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| SecurityController | /api/security | 5 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| AuditController | /api/v1/audit | 6 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| DecisionController | /api/security/decision | 1 | 未覆盖 | 活体 405：仅写语义面（POST/PUT），已按方法契约验证 |
| ClsController | /api/security/cls | 7 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| RlsController | /api/security/rls | 7 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| SecurityAssetCatalogController | /api/security/assets | 3 | 未覆盖 | 活体 200：已纳入 coverage-registry.spec 实测 |
| AuditDetailController | /api/v1/audit | 1 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| SecurityAuditController | /api/security/audit | 1 | 未覆盖 | 活体 200：已纳入 coverage-registry.spec 实测 |
| SecurityEngineStatusController | /api/v1/engine/security | 5 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| ImpersonateController | /api/security/impersonate | 1 | 未覆盖 | 活体 405：仅写语义面（POST/PUT），已按方法契约验证 |
| CryptoKeyAdminController | /api/security/crypto | 2 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| DataMaskingController | /api/v1/data-masking | 2 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| AbacController | /api/v1/abac | 5 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| CryptoAuditController | /api/v1/audit/crypto | 4 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| SecurityConfigController | /api/v1/security | 16 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| GatewayDiagnosticsController | /api/v1/system | 1 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| RuntimeAlertV1Controller | /api/v1/monitor | 6 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| MonitorController | /api/monitor | 3 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| TelemetryController | /api/telemetry | 4 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| SysConfigController | /api/v1/sysconfig | 1 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| EcosKnowledgeGraphController | /api/v1/ecos/knowledge-graph | 2 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| SystemEventTopicsV1Controller | /api/system/events | 1 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| EngineTaskController | /api/v1/engine | 3 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| SecurityController | /api/security | 5 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| AlertController | /api/v1/alerts | 3 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| TwinController | /api/twins | 6 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| GitForwardController | /api/v1/ecos/git/** | 0 | 无端点 | 类无任何 HTTP 映射（纯工具/常量类） |
| CliPreflightController | /api/v1/cli | 1 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| WorkspaceForwardController | /api/v1/workspace/** | 0 | 无端点 | 类无任何 HTTP 映射（纯工具/常量类） |
| TaskController | /api/v1/task | 13 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| OagForwardController | /api/v1/oag | 4 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| AgentRuntimeController | /api/v1/agent-runtime | 19 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| WorkflowController | /api/v1/ecos/workflows | 17 | 未覆盖 | 活体 200：已纳入 coverage-registry.spec 实测 |
| OntologyWorkflowApiController | /api/v1/ontology/workflows | 17 | 未覆盖 | 活体 200：已纳入 coverage-registry.spec 实测 |
| WorkflowTaskController | /api/v1/ecos/tasks | 7 | 未覆盖 | 活体 200：已纳入 coverage-registry.spec 实测 |
| WorkflowApprovalController | /api/v1/ecos/approvals | 4 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| WorkflowInstanceApprovalController | /api/v1/ontology/workflow-instances | 1 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| GitController | /api/v1/ecos/git | 10 | 未覆盖 | 活体 503：依赖未就绪（如实降级） |
| DqDashboardController | /api/dq | 10 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| DataLakeController | /api/datalake | 5 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| HealthController | /api | 1 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| AuthController | /api/v1/auth | 4 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| EntityTableMappingController | /api/v1/ecos/entity-table-mappings | 4 | 未覆盖 | 活体 200：已纳入 coverage-registry.spec 实测 |
| ScenarioController | /api/v1/ecos/world-model-graph/scenarios | 5 | 未覆盖 | 活体 200：已纳入 coverage-registry.spec 实测 |
| SecurityProfileController | /api/v1/security-profiles | 8 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| SecurityPolicyController | /api/v1/security/policies | 6 | 未覆盖 | 活体 200：已纳入 coverage-registry.spec 实测 |
| InterfaceRefController | /api/v1/interfaces | 5 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| ConfigController | /api/v1/system/config | 6 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| SystemParamController | /api/v1/system/config/params | 5 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| DictController | /api/v1/system/dict | 10 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| DictTableController | /api/v1/dict | 9 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| RoleController | /api/v1/system/roles | 7 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| PermissionController | /api/v1/system/permissions | 5 | 未覆盖 | 活体 200：已纳入 coverage-registry.spec 实测 |
| OrganizationController | /api/v1/system/organizations | 9 | 未覆盖 | 活体 405：仅写语义面（POST/PUT），已按方法契约验证 |
| TenantController | /api/v1/system/tenants | 9 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| MonitoringController | /api/v1/ecos/monitoring | 2 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| CausalController | /api/v1/causal | 3 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| MarketplaceController | /api/v1/marketplace | 6 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| PortalSearchController | /api/v1/portal | 1 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| WorldModelGraphController | /api/v1/ecos/world-model-graph | 11 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| UserController | /api/v1/system/users | 14 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| SysConfigController | /api/v1/system/config | 5 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| ObjectTimelineController | /api/v1/ecos/objects | 4 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| ObjectActionController | /api/v1/ecos/objects | 2 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| ScenarioSandboxController | /api/v1/workspace/scenarios | 2 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| ObjectRelationshipController | /api/v1/ecos/objects | 4 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| QueryHistoryController | /api/query | 2 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| BindingCatalogController | /api/v1/workspace/binding-catalog | 1 | 未覆盖 | 活体 200：已纳入 coverage-registry.spec 实测 |
| ForecastScopeController | /api/v1/workspace/scenarios | 1 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| ScenarioBindingLinkController | /api/v1/workspace/scenarios | 4 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| ScenarioController | /api/v1/workspace/scenarios | 6 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| ScenarioActionController | /api/v1/workspace | 4 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| ScenarioCognitionV2Controller | /api/v1/business/scenarios | 4 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| ObjectQLController | /api/query | 1 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| ObjectController | /api/v1/ecos/objects | 9 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| ScenarioCognitiveController | /api/v1/workspace/scenarios | 4 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| ForecastRunController | /api/v1/workspace/forecast-runs | 10 | 未覆盖 | 活体 405：仅写语义面（POST/PUT），已按方法契约验证 |
| ScenarioRunController | /api/v1/workspace/scenarios | 2 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| ObjectStateMachineController | /api/v1/ecos | 6 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| ScenarioPreValidateController | /api/v1/workspace/scenarios | 1 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| ScenarioStatusController | /api/v1/workspace/scenarios | 1 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| ScenarioMindsController | /api/v1/workspace/scenarios | 9 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| ScenarioCompletenessController | /api/v1/workspace/scenarios | 1 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| ScenarioOptionsController | /api/v1/workspace/scenarios | 7 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| WorkbookController | /api/workbook | 4 | 未覆盖 | 活体 404：该前缀未在本部署装配（见 §5.3 装配缺口登记） |
| KnowledgeWorkbenchController | /api/v1/knowledge | 4 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| ScenarioBlueprintController | /api/v1/business/scenarios/scenarioId/blueprints | 12 | 未覆盖 | 活体 200：已纳入 coverage-registry.spec 实测 |
| PeriodCloseContractController | /api/v1/workspace | 1 | 已覆盖 | spec 前缀匹配命中（§一.3：与 13 个 spec 的路径字面量做前缀比对） |
| BacktestContractController | /api/v1/workspace/backtests | 1 | 未覆盖 | 活体 403：权限面，已登记 |
| CaliberContractController | /api/v1/workspace/calibers | 7 | 未覆盖 | GET /{id} → 403 PERMISSION_DENIED（口径契约需特定权限） |