# cognitive-engine-impl — 认知引擎·实现层

> 子模块: cognitive-engine/impl | 端口: 共享父模块 18089 | 依赖: kb-engine-api (REST), PostgreSQL (sys_man)
> 上层: 见 ../AGENTS.md（cognitive-engine 顶层）

## 本模块干什么
- **实现层（业务）**：因果推理/情景模拟/决策/世界模型/诊断/Provenance/新闻摘要等 8 个 Controller 与 4 层服务实现波。（5 主契约 + 1 能力注册）
- 测试充分（14 个 test class / 72 case，含 P0-3/P0-4 反向 + 5 Wave 3.x 契约 + ArchUnit）：`CausalReasonerServiceTest` / `CausalDetectorTest` / `ReasoningPathFromCausalBuilderTest` / `ReasoningPathFromCausalBuilderContractTest` / `ReasoningPathBuilderWave2CTest` / `NewsFeedReaderTruncateTest` / `NewsFeedReaderDemoTest` / `PrecedentRecallerTest` / `OagNodesTest` / `EntityLinkerTest` / `RuleRefCollectorTest` / `ContractModelsWave32Test` / `CrossEngineLLMProviderMockTest` / `ArchitectureTest`。

## 主要 code（控制器/服务/模型）
- `DiagnosisController` — `/api/v1/cognitive/diagnose` 业务诊断（含 ≥3 层因果链）。
- `CognitivePipelineController` — `/api/v1/knowledge/reason` 混合推理编排（KG_QUERY / RULE_CHECK / VECTOR_RAG / HYBRID）。
- `DecisionController` — 决策服务 API（含 `DecisionException` / `DecisionPolicy` / `DecisionPrecedent`）。
- `ScenarioController` / `WorldModelController` / `ProvenanceController` / `Wave3DemoController` — 情景模拟（文档口径：**仿真情景**，代码类名仍为 `Scenario`*，见 PMO-50 决策 4）/世界模型/Provenance/Wave 3 演示。
- `ForecastController` — `/api/v1/cognitive/forecast` 指标预测 + `/api/v1/cognitive/models` 模型注册表（PMO-51）。
- `CognitiveEngineHealthController` — `/api/v1/engine/cognitive/*` 健康检查与统计。
- service：`CausalReasonerService` / `ReasoningPathBuilder` / `RuleRefCollector` / `TraverseKgChain` / `PrecedentRecaller` / `EntityLinker` / `NewsFeedReader` / `NewsLetter` / `ScenarioSimulator`（合同体系）/ `ForecastServiceImpl`（统计基线：移动均值 + 最小二乘线性外推 + KG 因子修正）/ `ModelRegistryService`（`ecos_cognitive_model` 注册表）。

## 调用链（只读 + 调谁）
- → 同 engine api: 注入 `CausalReasonerService` / `DecisionService` / `ScenarioSimulatorService` / `WorldModelService` / `ParetoOptimizerService` / `EngineCapabilityRegistry` 等接口（来自 `cognitive-engine-api`）。
- → kb-engine: **REST** 调用 `GET :18086/api/v1/kb/rules`（规则查询）+ `POST :18086/api/v1/kb/graph/query`（KG 推理），**不改接口签名**（架构铁律 2.1：跨引擎调 api，不调 impl）。
- → 引擎外: 不直接 import `kb-engine-impl`（架构铁律 2.1）。
- ← 被调用方:
  - `ai-engine` 顶层（混合推理委托 `POST :18089/api/v1/knowledge/reason`）。
  - gateway 聚合加载、前端 `/api/v1/cognitive/*`。

## 端点 / 补丁
- 路径池：
  - `/api/v1/knowledge/reason` — 混合推理（`CognitivePipelineController`）。
  - `/api/v1/rules/causal-chain/{ruleId}` — 规则因果链（`DiagnosisController`）。
  - `/api/v1/rules/impact-analysis` — 影响分析。
  - `/api/v1/rules/audit-logs` — 合规审计日志。
  - `/api/v1/cognitive/*` — 认知推理通用端点（`DiagnosisController` / `ScenarioController` / `WorldModelController` / `ProvenanceController`）。
  - `/api/v1/cognitive/forecast` — 指标预测（`ForecastController`，PMO-51）。
  - `/api/v1/cognitive/models` — 认知模型注册表 GET/POST（`ForecastController`，PMO-51）。
  - `/api/v1/world-model/*` — 世界模型。
  - `/api/v1/engine/cognitive/*` — 引擎健康检查。
- 因果链产出契约（`CausalReasonerService`）：
```java
public CausalChainResult diagnose(DiagnosisRequest req) {
    // 复用 kb 规则（GET :18086/api/v1/kb/rules）
    // 复搜因果链（POST :18086/api/v1/kb/graph/query）
    // 实时产出，不落盘
}
```

## 禁止
- **不直接 import `kb-engine-impl` / `*-engine-impl**`**（顶层红线 #1，违反 = 验收失败）。
- **DB 落盘按 ADR-9 三档口径**（顶层红线 #2 修订，PMO-59 P0）：推理**结果**实时计算不落盘；模型**资产**注册落 `ecos_cognitive_model`（ADR-8，PMO-51）；认知**心智状态**（`ecos_cognitive_evidence`/`ecos_cognitive_hypothesis`/`ecos_cognitive_belief`，DDL V127~V129，PMO-59 P0）落盘。impl 落盘实现（Mapper/Service/Controller）留待 Phase 2；Phase 1 仅契约（api 层 `IUncertaintyJudgementService`/`IHypothesisLifecycleService`）。
- **不引入规则引擎**（顶层红线 #3：SpEL 表达式评估即可，不要引入 jBoss Drools / Easy Rules）。
- 不直接 LLM 调用（LLM 走 `llm-gateway`）。
- 不硬编码 token / BOD / metadata（cross-engine 凭据走 `RestTemplate` 注入的 `restTemplate` Bean，不在 Service 字面量）。
- 实体新提自有 driver 禁止（Neo4j / PG 都收敛 `runtime-access`）。
- 推理不能 >30s（雷击调用时间，超时上游默认拒绝）。
- 不引入 Flyway（schema 变更走 ADR）。
