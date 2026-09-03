# cognitive-engine-api — 认知引擎·服务接口层

> 子模块: cognitive-engine/api | 端口: 共享父模块 18089 | 依赖: kb-engine-api (REST)
> 上层: 见 ../AGENTS.md（cognitive-engine 顶层）

## 本模块干什么
- **接口层/服务层契约**：承载因果/决策/情景/世界模型/管线/能力注册的 `interface` + 全部 `model`（POJO，纯数据）。
- **唯一定义契约的模块**：本模块定义 `CausalReasonerService` / `DecisionService` / `ScenarioSimulatorService` / `WorldModelService` / `ParetoOptimizerService` / `EngineCapabilityRegistry` 接口，跨引擎调用方（ai-engine）只能 import 本模块，**禁止 import cognitive-engine-impl**（架构铁律 2.1）。

## 主要 code（接口/模型）
- `CausalReasonerService` — 合规因果链推理契约（kb 规则 → 因果链）。
- `DecisionService` — 决策契约（含 `DecisionException` / `DecisionPolicy` / `DecisionPrecedent`）。
- `ScenarioSimulatorService` — 情景模拟契约（`SimulationResult` / `SimulationStatus`）。
- `WorldModelService` — 世界模型契约（`WorldState`）。
- `ParetoOptimizerService` — 帕累托优化契约（`StrategyRecommendation`）。
- `EngineCapabilityRegistry` — 引擎能力注册接口（供 ai-engine 能力发现）。
- model 包：`ReasoningPath` / `ReasoningStep` / `ReasonerResult` / `CausalChainResult` / `CausalChainNode` / `CausalEdge` / `Justification` / `JustificationClause` / `RuleRef` / `PrecedentRef` / `NodeType` / `CognitivePipeline` / `CognitivePipelineNode` / `ProvenanceEntry` / `ApprovalChain` / `ImpactAnalysisResult` / `DiagnosisRequest` / `SubQuery` 等。

## 调用链（只读 + 调谁）
- → 上层 engine: **无**（契约层只读。被 cognitive-engine-impl 与 gateway / ai-engine 引用）。
- ← 被调用方:
  - `cognitive-engine-impl` 的 `DiagnosisController` / `CognitivePipelineController` / `DecisionController` / `ScenarioController` / `WorldModelController` / `ProvenanceController` / `CognitiveEngineHealthController` / `Wave3DemoController` 等。
  - `ai-engine` 顶层依赖本模块（混合推理委托，跨引擎）。
- 跨引擎：cognitive-engine 顶层依赖 `kb-engine-api`，本 api 中只定义 `KgQueryService` / `RuleSource` 的接口签名，**不在本模块直接调 impl**（架构铁律 2.1）。

## 端点 / 补丁
- 本模块**不暴露 REST 端点**（无 `@RestController`）。
- 双契约一致性：impl 必须保持签名一致、参数 model 来自 api。
- 示例（决策契约）：
```java
public interface DecisionService {
    /** 业务因果链推理（复用 CausalReasoner 模板） */
    Decision assessDecision(DiagnosisRequest req);
    List<DecisionException> listExceptions(String decisionId);
}
```

## 禁止
- 不改既有方法签名（API 只增不改）。
- 不在此模块加任何业务实现类（带 `interface` 与 `model` 的池外禁止）。
- 不 import `*-engine-impl`（违反架构铁律 2.1 = 验收失败）。
- 不硬编码 token / BOD / metadata（模型是纯 POJO，禁止 `@Value` 字面量）。
- 不管理 DB 表（`compliance_rules` 表归 kb-engine 所有，本模块 model 仅持读视图 `RuleRef`）。
- 新增 DB 表严禁（顶层红线 #2：推理结果实时计算，不持久化）。
- 不在此 api 内 `@Value` 或 `@ConfigurationProperties`（配置归 impl 或 boot）。
