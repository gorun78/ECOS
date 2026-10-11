# ECOS 认知域与智能域补充详细设计
## ——基于 PRD-05 / PRD-06、详细设计-05 / 详细设计-06 的差距分析与补齐方案

> 文档版本：v1.0  
> 编制日期：2026-10-09  
> 适用范围：`cognitive-engine`（认知域，C）与 `ai-engine + llm-gateway`（智能域，W），并定义与 data-engine、ontology-engine、kb-engine、workspace、W Agent 的必要接口  
> 目标：在不推翻现有架构、不新增 Maven 模块、不新增 Docker 容器、不破坏既有 API 的前提下，补齐需求与详细设计之间的缺口，使认知能力真正参与业务场景构建、执行、验证与受控反馈。

---

## 0. 文档使用方式与重要边界### 0.1 本文依据

本次审查依据以下文档：

- `PRD-05-cognitive-engine需求规格-2026-09-28.md`，包括 REQ-COG-01～09；
- `PRD-06-ai-engine需求规格-2026-09-28.md`，包括 REQ-AI-01～11；
- `详细设计-05-认知域-cognitive-engine与aiming-2026-09-29.md`；
- `详细设计-06-智能域-ai-engine与llm-gateway-2026-09-29.md`；
- 之前针对 ECOS 需求提出的八项修改建议：DIKCW 迭代闭环、任务上下文构建、场景蓝图、AI 辅助数据治理、本体候选建模、认知资产边界与生命周期、受控学习回流、分层评估体系。

### 0.2 审查口径

本文把结论严格分为三类：

- **设计已覆盖**：详细设计中存在明确的功能、接口、数据、状态机或验收设计。
- **设计有缺口/不闭环**：需求只在局部提及，缺少端到端流程、接口契约、持久化/生命周期、失败语义或验收标准。
- **实现状态待验证**：文档中写有设计、测试标识或批准记录，但这不能证明代码已经实现、测试已执行或业务验收通过。本文没有直接审查完整源码与运行环境，因此不把“已批准”“已有测试名”“设计中列出接口”当成“已实现”。

### 0.3 不改变的架构红线

1. 不新增 Maven 模块或 Docker 容器；在既有 `aiming`、`ai-engine`、`llm-gateway`、`runtime-*` 和既有引擎 API 内扩展。
2. `llm-gateway` 仍是 LLM 与 Embedding 的唯一出口；小模型可通过统一模型/能力注册与路由管理，但不得在各引擎中各自直连供应商。
3. `runtime-task` 是长任务/调度唯一入口；Agent 内禁止私建调度器、裸线程和第二套运行时。
4. security-engine 是权限、密级、自动化等级、审批策略的权威裁决方；AI 只能执行裁决，不得自行复制策略判断。
5. cognitive 的推理过程与请求上下文默认是运行时态，不新增 `cognitive_context` 或推理结果业务事实表。受治理的认知资产可按既有 PRD/ADR 规定落盘；运行轨迹通过既有审计/事件机制追溯。
6. 财务金额、业务指标、评分中作为业务结论的数值必须来自确定性计算或权威数据；LLM 只可生成解释、候选假设或结构化建议。
7. 既有 API 路径、请求/响应语义只增不改；既有数据只定性、迁移前评估，不擅自 DROP、迁表或改写权威数据源。
8. “设计补齐”不等于授权直接修改所有存量代码或执行数据库迁移。涉及存量载体归属、双写、schema 归位的变更，必须按现有治理流程确认。

---

# 第一部分：需求与设计匹配度审查

## 1. 总体结论

详细设计-05 和详细设计-06 对各自专项 PRD 的主干需求覆盖较充分，尤其在端点契约、错误语义、安全护栏、运行状态机、模型版本与发布门禁、会话压缩、Token 计量、运行追踪、工具注册等方面，设计颗粒度较高。

但它们更偏向于“引擎内部能力和控制面组件设计”，尚未形成完整的“以业务目标为入口、自动组织数据/本体/知识/认知资产、形成可验证场景、受控执行、用结果提出认知资产更新建议”的平台闭环。当前最重要的差距不是再增加一个独立的认知引擎，而是补齐现有引擎之间的协作契约和端到端验收。

### 1.1 匹配度概览

| 审查范围 | 需求覆盖情况 | 主要差距 | 结论 |
|---|---|---|---|
| PRD-05 REQ-COG-01～05 | E1–E4、领域因果诊断、预测驱动反事实、能力枚举、禁止推理结果任意落盘均有明确设计 | 缺少统一任务上下文构建契约；证据/假设/信念的跨场景解释与更新流程没有形成完整平台闭环 | 主干覆盖，端到端协作不足 |
| PRD-05 REQ-COG-06～09 | C 类对象目录、Cognitive Model Registry、Decision Basis、确定性计算产物归属均有设计条款 | 对象生命周期与治理操作仍偏目录/守护；上下文、情境、模式匹配、经验如何以“引用态/治理资产态”协同需要进一步明确；发布门禁与实际运行消费的闭环需补强 | 设计已覆盖部分契约，实施闭环待补 |
| PRD-06 REQ-AI-01～05 | 安全护栏、会话压缩、可观测性与 Token 计量、场景工具、错误矩阵均有设计 | 工具调用前后缺少统一的上下文包和证据要求；模型路由对 OCR/ASR/图像理解等小模型的能力抽象及运营流程需要落实到接口/验收 | 主干覆盖，跨能力编排不足 |
| PRD-06 REQ-AI-06～11 | Run 状态机、Planner/Executor、Tool Contract、Model Router、自动化等级、CLI/契约前置件均有设计 | 模型/能力中心与场景工作台、数据治理、本体候选、认知模型注册表之间的协作关系缺少统一的场景级验收；候选生成到批准发布的业务流程需要进一步闭合 | 控制面设计较完整，业务场景闭环不足 |
| 与 PRD-02/03/04/08/09/10 的跨域协作 | 文档已有接口引用、依赖说明及部分接缝表 | 没有一个统一的 `Scenario Blueprint` 作为场景定义、依赖、缺口、测试、发布和结果回流的版本化对象；数据/本体 AI 辅助建议缺少统一的候选/审批/回滚协议 | 关键平台级缺口 |
| 之前提出的八项建议 | 局部内容已在多个分册体现 | 主要以分散规则存在，没有成为一套可执行、可观测、可验收的完整工作流 | 需要本文补齐 |

> **关键判断：**不建议新增 `cognitive-scene-engine` 或第二套 Agent Runtime。应在现有 `workspace`、`ai-engine`、`cognitive-engine` 及 data/ontology/kb 引擎中补充契约、编排步骤与门禁，由各引擎继续拥有自己的业务语义与写入权。

## 2. 按需求编号审查

### 2.1 PRD-05：认知域

| 需求 | 详细设计现有覆盖 | 差距与风险 | 本文补齐项 |
|---|---|---|---|
| REQ-COG-01 E1–E4 场景认知端点 | 已定义 detect、operation-eval、hypotheses、beliefs 的读侧契约、过滤、排序、错误矩阵和验收用例 | E1–E4 解决“展示已有认知资产”，但没有统一回答“当前任务应装配哪些数据、知识、Mind、模型和证据”；`503`、缺表与空结果必须在 UI/Agent 端保持语义区分 | 增加 `Context Builder` 契约；明确空数据、缺失依赖、权限拒绝和低置信度的差异 |
| REQ-COG-02 领域化因果诊断 | 已定义 `forecastContext`、因果链、`evidenceRefs`、统计支持及待验证假设 | 证据来源、证据时效、冲突证据与上下文版本之间的关联还需统一；缺少面向不同业务域的可复用解释协议 | 增加证据封装与因果输出校验；规定每条路径边的支持状态 |
| REQ-COG-03 预测驱动反事实 | 已明确预测运行服务负责确定性重算，cognitive 负责差异归因；探索式模拟需标注 `exploratory=true` | 需要把“运行输入快照、情景差异、证据、假设、解释”形成可回放的关联链，避免 UI 或 Agent 混淆探索性答案与核算结果 | 统一 `RunReference`、`EvidenceRef` 与场景蓝图的引用契约 |
| REQ-COG-04 capabilityMask | 已定义枚举及本期映射规则 | 能力枚举与 Model/Capability Center 的能力描述需要对齐；同一能力不能在 Mind、模型目录、工具注册表中出现互相矛盾的名称 | 定义能力 ID、版本和兼容校验规则 |
| REQ-COG-05 铁律守护 | 已明确推理结果不落盘、不新增业务事实表、不引入规则引擎、LLM 不承担确定性推理 | “推理结果不落盘”与“受治理的认知资产需要持久化”需要以资产类型和用途进一步划界；否则容易误把所有 belief/hypothesis 视为禁止落盘，或反过来把运行时推理输出永久存储 | 增加认知资产分类、存储规则、生命周期和写入白名单 |
| REQ-COG-06 C 类 11 对象目录 | 已定义 11 类对象与物理载体/引用态的映射原则 | 对象目录本身不等于生命周期管理；候选、待验证、已批准、失效/被反证的治理动作缺少统一状态机与变更提案契约 | 增加 C 资产生命周期与变更提案 API |
| REQ-COG-07 Cognitive Model Registry | 已定义 CM-01～07、版本不可变、contract/regression/backtest/calibration 四类门禁，CM-07 数值禁令 | 缺少将评估结果、校准情况、适用范围、数据漂移和线上反馈连接起来的完整闭环；CM-06 案例相似度当前设计中已标注形态限制，不能在验收时将降级检索冒充向量相似度 | 增加模型评估记录、校准验收和运行时路由反馈契约 |
| REQ-COG-08 Decision Basis | 已要求单一写权威、Agent 只读引用 `decisionId + version + hash` | 镜像权威仍需先实测确认；决策依据、证据、审批记录与结果的统一解析协议还不够完整 | 增加只读解析 DTO 与权威检查门禁，不擅自决定存量迁移 |
| REQ-COG-09 确定性计算产物 | 已规定计算产物落业务域/`ecos_dw`，cognitive 只保留引用 | 缺少通用 `CalculationResultRef` 的 schema 与证据校验约束，难以让 AI 工具、场景工作台、认知解释一致引用同一个运行结果 | 增加计算产物引用契约与数值来源校验 |

### 2.2 PRD-06：智能域

| 需求 | 详细设计现有覆盖 | 差距与风险 | 本文补齐项 |
|---|---|---|---|
| REQ-AI-01 安全护栏 | 已设计统一 ToolExecutor 咽喉、security-engine 裁决、fail-closed、obligations 与审计 | 必须确保新增的数据治理建议、本体候选生成、认知资产更新也经过同一安全/审批边界，不能只保护既有五类工具 | 把新能力注册为有契约的工具类型，纳入同一护栏链 |
| REQ-AI-02 会话压缩/记忆治理 | 已设计 token 阈值、结构化摘要、引用保留、tool_call 完整性及逻辑归档 | 会话摘要与长期知识/认知资产边界需更明确；不得把摘要自动当成事实或经验入库 | 增加摘要中的来源引用、未决事项、置信状态与写入禁令 |
| REQ-AI-03 可观测性与计量 | 已设计 traceId、运行轨迹、Token 计量与回放 | 跨引擎链路缺少统一的场景/任务/蓝图/上下文版本关联键，难以分析“场景为什么构建失败” | 增加 `scenarioId`、`blueprintVersion`、`contextDigest`、`capabilityId` 等关联维度 |
| REQ-AI-04 场景工具 | 已设计场景工具、引用块、后校验，禁止 LLM 生成数值 | 工具仍偏向“调用既有功能”；缺少用于数据剖析、字段语义建议、本体候选和场景蓝图生成的工具合同与审批分级 | 补充候选生成类工具及只读/建议/写入三级操作语义 |
| REQ-AI-05 错误处理 | 已设计错误分类、有界退避、模板消息和错误码 | 跨引擎错误应区分“业务数据不足、权限拒绝、依赖未就绪、模型不支持、候选待审批、确定性校验失败” | 增加跨引擎错误分类与前端/Agent 处理规则 |
| REQ-AI-06 Run/Planner/Executor | 已定义状态机、计划校验、四轴预算、偏差重规划 | 没有把“目标理解—资产盘点—缺口分析—蓝图生成—验证—发布”作为一个标准可复用的计划模板 | 增加场景构建 Playbook 与步骤输出契约 |
| REQ-AI-07 Tool Contract | 已定义工具合同与生命周期 | 工具输入/输出 schema、证据要求、是否产生候选、是否触发写操作和审批等级需要成为可机器校验字段 | 增加能力合同字段与校验规则 |
| REQ-AI-08 Model Router | 已定义按能力、成本、延迟、风险等路由 | 小模型（OCR、ASR、语音合成、图像理解、文档解析等）应以能力而不是“LLM 名称”被选择；异步批量任务与多模态输入也需统一能力接口 | 增加模型能力目录、能力路由和结果标准封装 |
| REQ-AI-09 自动化等级/Kill Switch | 已定义策略由 security-engine 裁决、AI 作为 PEP 执行 | 候选建议、审批、发布、业务执行是不同风险级别，需与工具合同和场景蓝图门禁联动 | 增加动作分类到自动化等级的映射契约 |
| REQ-AI-10 单一 Runtime/分层约束 | 已要求单一 Agent Runtime、依赖方向约束与 Hermes 边界 | 场景构建能力不得绕过统一 Runtime 自建 loop；长任务必须走 runtime-task | 在架构门禁和场景验收中明确检查 |
| REQ-AI-11 Model & Capability Center/CLI 前置 | 已登记模型/能力中心与契约前置件 | 能力目录尚需与认知模型、Mind、Tool Contract 的能力语义一致；没有统一的能力兼容性测试 | 增加 Capability Contract 和跨目录兼容测试 |

## 3. 对之前八项建议的落地差距

| # | 建议 | 当前设计状态 | 需要补齐的设计成果 |
|---|---|---|---|
| 1 | DIKCW 改为目标驱动的迭代闭环 | 分散体现于 W Agent Run/重规划、认知诊断、场景端点；未形成统一平台级状态/回退语义 | 标准业务认知循环、循环状态、停止条件、回退/人工澄清条件 |
| 2 | 增加任务上下文构建器 | 有 `forecastContext`、上下文读取接口和 Run 上下文概念，但没有通用 Context Builder | 统一 Context Pack schema、组装算法、冲突/缺失处理、权限过滤和摘要哈希 |
| 3 | 场景工作台成为业务能力构建器 | 场景工作台与 W Agent 分别有设计，但缺少统一的场景蓝图对象 | `Scenario Blueprint`、版本、依赖图、缺口、测试、审批、发布与运行结果 |
| 4 | AI 辅助数据治理 | PRD-02 及 data-engine 有自己的数据治理能力，PRD-06 有工具和护栏，但 AI 建议流程未与数据治理写通道统一 | Profile、字段语义建议、映射/转换/DQ 规则候选、预览、回归和批准契约 |
| 5 | 证据驱动的本体候选建模 | ontology 提案机制存在；认知/Agent 候选治理存在，但缺少端到端证据绑定和差异审查协议 | Ontology Candidate、证据来源、置信/冲突、提案与批准接口 |
| 6 | 认知资产边界与生命周期 | C/K 边界、对象目录和版本门禁已被重视，但生命周期没有统一到全部资产类型 | 资产分类、状态机、状态迁移权限、失效/反证与保留策略 |
| 7 | W→D 验证学习回流 | 预测误差、运行轨迹与候选/经验等在不同分册中涉及，缺少验证后提出更新的统一机制 | Outcome Evaluation、更新建议、影响分析、回归验证、审批与发布 |
| 8 | 分层评估体系 | 已有各 REQ 的局部验收和模型门禁，但没有跨“数据—认知—场景—业务”指标体系 | 指标目录、基准集、线上反馈、门禁阈值、效率指标与发布报告 |

---

# 第二部分：补充详细设计

## 4. 总体架构与职责边界

### 4.1 组件职责

| 组件 | 归属 | 新增/补齐职责 | 明确禁止 |
|---|---|---|---|
| Scenario Workbench / Workspace | 既有 workspace | 持有 `Scenario Blueprint` 生命周期、场景资产依赖、缺口任务、测试结果、审批和发布状态 | 不直接读写其他引擎的业务表；不自建 Agent loop |
| W Agent / Agent Runtime | 既有 ai-engine 控制面 | 将业务目标转成任务计划；按步骤调用能力；管理预算、暂停、澄清、重规划 | 不定义 C/K 资产语义；不直接写认知资产或本体正式对象 |
| Context Builder | cognitive-engine 内的应用服务/接口，不新增服务 | 根据任务和蓝图组装请求态 Context Pack，解析数据、本体、知识、认知资产及权限范围 | 不新增 `cognitive_context` 持久化表；不绕过 security-engine |
| Cognitive Asset Catalog / Registry | cognitive-engine | 维护 C 类对象目录、认知模型版本与发布门禁，暴露只读解析与受控提案接口 | 不把每次临时推理结果都持久化；不复制 K 类对象 |
| Model & Capability Center / Router | ai-engine + llm-gateway 既有模块 | 统一管理 LLM、Embedding、小模型和业务能力；执行能力匹配、风险/成本/延迟路由 | 不让引擎直连模型供应商；不让模型自行决定权限 |
| Data Governance Assistant | data-engine 内部能力，通过 Tool Contract 暴露 | 生成剖析报告、字段语义候选、映射/转换/DQ 规则建议，执行只读预览和回归验证 | 不绕过 data-engine 的正式写入与审批流程 |
| Ontology Candidate Assistant | ontology-engine 内部能力，通过 Tool Contract 暴露 | 基于数据剖析、知识证据和现有本体提出对象/属性/关系候选 | 不直接发布正式本体，不以 LLM 结果覆盖权威对象 |
| Outcome Evaluator | cognitive-engine 负责认知评估协议；data-engine/业务场景提供事实结果 | 对比预测/执行结果与实际结果，形成偏差诊断与更新建议 | 不因一次偏差自动改写规则、信念或生产模型 |

### 4.2 逻辑架构

```text
用户业务目标
    |
    v
Workspace / Scenario Workbench
    |  创建或更新 Scenario Blueprint
    v
W Agent Planner ---- security-engine 策略/自动化等级/审批裁决
    |
    v
Context Builder（cognitive-engine）
    |---- data-engine：资产目录、Profile、DQ、字段语义、快照引用
    |---- ontology-engine：本体模型、对象/关系、候选提案状态
    |---- kb-engine：知识片段/制度/案例的只读引用
    |---- cognitive-engine：Mind、Hypothesis、Belief、Decision Basis、Cognitive Model
    |---- ai-engine：Capability / Tool / Model Registry
    |
    v
Plan Validator -> Executor -> ToolExecutor 唯一安全咽喉
    |                    |
    |                    +--> 数据治理建议 / 本体候选 / 知识检索 / 确定性业务工具
    |
    v
Blueprint Validate / Replay / Approval / Publish
    |
    v
受控运行 -> 结果引用 + traceId + evidence refs
    |
    v
Outcome Evaluator -> 偏差诊断 -> 更新候选 -> 影响分析/回归/审批
    |
    +---------------------> 下一轮业务认知循环
```

### 4.3 DIKCW：目标驱动的迭代循环

DIKCW 作为业务认知流程，不应被解释为必须单向、一次性经过的线性流水线。定义以下逻辑阶段：

1. **D — Data**：识别数据源、数据资产、质量、覆盖范围和可用快照。
2. **I — Information**：完成字段语义、指标口径、数据映射、数据间关联和时效判定。
3. **K — Knowledge**：检索并引用制度、规范、案例、业务定义和既有知识。
4. **C — Cognition**：结合上下文形成假设、证据权重、因果解释、适用条件和不确定性。
5. **W — Work**：生成计划、执行受控工具、验证结果、交付场景能力。
6. **Feedback**：根据运行结果和实际结果提出候选更新，必要时回到 D/I/K/C 任一阶段。

每轮循环必须有明确的 `goal`、`successCriteria`、`budget`、`stopConditions`。允许阶段跳转，但每次跳转必须有原因记录。例如：
- 缺少事实数据 → 回到 D；
- 指标口径冲突 → 回到 I 并创建澄清任务；
- 规则或制度版本冲突 → 回到 K；
- 证据不足或假设相互矛盾 → 回到 C 或请求人工澄清；
- 工具失败/预算耗尽 → 暂停或重规划，不得伪造成功；
- 结果达标且验收通过 → 进入发布/交付；
- 运行后发现误差 → 进入反馈评估，不直接修改生产知识。

---

## 5. Context Builder 详细设计

### 5.1 设计目标

Context Builder 是认知域中的请求态上下文组装服务，用于解决“每个引擎都能提供一些数据，但 Agent 不知道本次任务应该信任什么、缺什么、哪些信息冲突”的问题。

它不取代知识库，不是长期记忆库，也不是新的持久化上下文引擎。它只负责在一次任务/一次阶段执行前，按当前主体权限和目标范围，将权威资产组织成结构化上下文。

### 5.2 接口设计

建议新增内部应用接口；具体 HTTP 路径应纳入现有 OpenAPI 前置件 P-5，并与已有路径规范对齐：

`POST /api/v1/cognitive/context/build`

请求示例：

```json
{
  "requestId": "req-123",
  "scenarioId": "scenario-annual-forecast",
  "blueprintId": "bp-001",
  "blueprintVersion": 4,
  "goal": {
    "type": "BUILD_OR_RUN_SCENARIO",
    "statement": "构建年度经营预测场景并解释主要风险",
    "successCriteria": ["金额可追溯", "关键风险有证据", "所有必需测试通过"]
  },
  "scope": {
    "tenantId": "tenant-1",
    "projectIds": ["p1", "p2"],
    "departmentIds": ["d1"],
    "periods": ["2027-01", "2027-12"],
    "caliberId": "caliber-01",
    "asOfTime": "2026-10-09T00:00:00Z"
  },
  "requiredCapabilities": ["DATA_PROFILE", "ONTOLOGY_CANDIDATE", "COGNITIVE_DIAGNOSE"],
  "include": {
    "dataAssets": true,
    "ontology": true,
    "knowledge": true,
    "cognitiveAssets": true,
    "modelCapabilities": true
  },
  "budget": {
    "maxTokens": 12000,
    "maxSources": 100,
    "maxLatencyMs": 5000
  }
}
```

响应示例：

```json
{
  "code": 0,
  "message": "ok",
  "traceId": "tr-123",
  "data": {
    "contextDigest": "sha256:...",
    "createdAt": "2026-10-09T00:00:00Z",
    "expiresAt": "2026-10-09T00:05:00Z",
    "scope": {
      "tenantId": "tenant-1",
      "projectIds": ["p1", "p2"],
      "periods": ["2027-01", "2027-12"],
      "caliberId": "caliber-01",
      "asOfTime": "2026-10-09T00:00:00Z"
    },
    "sources": [
      {
        "sourceType": "DATA_ASSET",
        "sourceId": "asset-001",
        "version": "12",
        "authority": "AUTHORITATIVE",
        "freshness": "CURRENT",
        "evidenceRefs": ["ev-001"]
      }
    ],
    "facts": [],
    "knowledgeRefs": [],
    "ontologyRefs": [],
    "cognitiveRefs": [],
    "capabilities": [],
    "conflicts": [],
    "gaps": [],
    "warnings": []
  }
}
```

**重要约束：**
- `Context Pack` 作为请求态 DTO 使用，不新建 `cognitive_context` 表。
- `contextDigest` 仅用于关联、回放与一致性校验；不意味着要把完整上下文写入认知数据库。
- 如审计需要保存执行证据，应在既有 `ecos.audit`/运行事件中记录 `contextDigest`、来源引用、蓝图版本和脱敏后的摘要，而非复制整份敏感上下文。
- 权限过滤必须在源引擎或其授权读接口执行；Context Builder 不得先读取全量数据再由模型自行过滤。

### 5.3 Context Pack 核心结构

| 字段 | 类型 | 说明 |
|---|---|---|
| `contextDigest` | string | 规范化 Context Pack 的摘要哈希 |
| `goal` | object | 目标、成功条件、停止条件 |
| `scope` | object | 租户、业务范围、时间范围、口径、数据时点 |
| `sources[]` | array | 来源类型、来源 ID、版本、权威等级、新鲜度、访问结果 |
| `facts[]` | array | 权威事实的引用/必要投影，必须带来源与时点 |
| `knowledgeRefs[]` | array | KB 文档/片段/案例引用，不复制完整知识资产 |
| `ontologyRefs[]` | array | 本体模型版本、对象/关系和提案状态 |
| `cognitiveRefs[]` | array | Mind、hypothesis、belief、decision basis、CM 版本的引用 |
| `capabilities[]` | array | 可用能力、输入输出契约、风险等级、模型版本 |
| `conflicts[]` | array | 冲突项、冲突来源、权威优先级、待处理动作 |
| `gaps[]` | array | 缺失数据、缺失关系、缺失权限、缺失证据等 |
| `warnings[]` | array | 过期、部分覆盖、低置信、降级模式等警告 |

### 5.4 组装算法

1. **校验请求**：校验场景、蓝图版本、scope、主体权限、预算和必需能力。
2. **建立权威源清单**：从 Blueprint 依赖与任务类型中推导必需资产；不得无界检索全平台。
3. **并行读取元数据**：通过引擎 API 读取数据资产、DQ/Profile、Ontology、KB、Cognitive Registry、Model & Capability Center。
4. **权限与密级裁剪**：security-engine 裁决主体、租户、行列权限与密级；失败默认 DENY。
5. **统一引用和版本**：每个引用包含稳定 ID、版本/快照、来源引擎、时点与权限结果。
6. **冲突检测**：同一语义字段存在多个值时，优先依据明确的权威来源/版本策略；无法确定时生成 conflict，不允许 LLM 自行裁决为事实。
7. **缺口识别**：按 `successCriteria` 和能力依赖，生成可操作的 Gap 项。
8. **预算裁剪**：按权威性、相关性、时效、范围匹配度裁剪；裁剪必须输出 `truncated=true` 与原因，不得静默丢失证据。
9. **生成摘要哈希**：对规范化结构计算 `contextDigest`，返回给 Run/Step/Blueprint。
10. **过期与刷新**：Context Pack 仅在请求声明的 TTL 内可用；数据快照/本体/知识版本发生变化时应重新构建，不允许把过期上下文当作最新事实。

### 5.5 失败语义

| 场景 | 处理 |
|---|---|
| 某可选来源暂时不可用 | 返回部分上下文并列入 `warnings`；若该来源是成功条件必需项，则 `ready=false` |
| 必需数据源缺失/权限不足 | 不得继续执行依赖该数据的步骤；返回明确 gap 或 403 |
| 引擎未启用/服务不可达 | 返回真实 503/依赖错误，不返回伪造空集合 |
| 来源之间语义冲突 | 返回 conflict，并要求人工澄清或按已批准权威策略解决 |
| 超过 Token/延迟预算 | 返回截断原因及未纳入引用清单，Planner 决定分阶段读取或暂停 |
| 版本不匹配 | 拒绝使用旧版本执行；要求刷新 Context Pack |

### 5.6 验收要求

- 权限测试：未授权数据不能出现在 Context Pack、模型输入、日志摘要中。
- 权威测试：同一指标出现冲突值时，不得由模型“猜一个”；必须输出冲突项。
- 可追溯测试：每个事实/结论性引用可解析到来源 ID、版本和时点。
- 稳定性测试：相同来源版本、相同请求和相同策略下，`contextDigest` 应稳定。
- 预算测试：超预算必须显式标记截断；不能以“上下文已完整”返回。
- 依赖故障测试：必需引擎 503 时，依赖步骤不得继续。
- 存储守护：Context Builder 调用前后不得因上下文组装新增认知事实表行。

---

## 6. Scenario Blueprint：业务场景蓝图

### 6.1 定位与归属

`Scenario Blueprint` 是 Workspace/Scenario Workbench 的业务场景定义与交付单元。它描述“要完成什么业务目标、依赖哪些资产、需要哪些能力、还有哪些缺口、如何验证、如何发布和运行”。

- **对象归属**：workspace/场景工作台。
- **编排执行**：ai-engine/W Agent。
- **上下文构建**：cognitive-engine/Context Builder。
- **数据资产与质量**：data-engine。
- **业务语义与正式对象**：ontology-engine。
- **知识检索**：kb-engine。
- **安全裁决**：security-engine。
- **确定性业务计算**：对应业务引擎/数据写通道。
- 不在 cognitive 或 ai 控制 schema 中复制 Blueprint 的正式权威副本。

### 6.2 Blueprint 数据契约

以下为逻辑模型，最终物理字段需遵守 workspace 既有 DDL 规范、schema 限定与只增不删规则。

```json
{
  "blueprintId": "bp-001",
  "scenarioId": "scenario-001",
  "version": 4,
  "status": "DRAFT",
  "goal": {
    "statement": "年度经营预测",
    "ownerRole": "BUSINESS_OWNER",
    "successCriteria": [
      "金额可追溯到预测运行快照",
      "关键风险有可解析证据",
      "所有 P0 验收测试通过"
    ]
  },
  "scope": {
    "tenantId": "tenant-1",
    "periods": ["2027-01", "2027-12"],
    "projectIds": ["p1", "p2"],
    "caliberId": "caliber-01"
  },
  "dependencies": [
    {
      "type": "DATA_ASSET",
      "id": "asset-001",
      "required": true,
      "versionConstraint": "PINNED"
    }
  ],
  "capabilities": [
    {
      "capabilityId": "DATA_PROFILE",
      "contractVersion": "1",
      "required": true
    }
  ],
  "gaps": [],
  "risks": [],
  "tests": [],
  "approval": {
    "required": true,
    "policyRef": "security-policy-ref"
  },
  "release": {
    "artifactDigest": null,
    "releasedAt": null
  }
}
```

### 6.3 状态机

```text
DRAFT
  -> DISCOVERING
  -> GAP_ANALYSIS
  -> BLUEPRINT_READY
  -> VALIDATING
  -> READY_FOR_APPROVAL
  -> APPROVED
  -> RELEASED
  -> ACTIVE
  -> RUNNING
  -> COMPLETED
```

异常与回退状态：

- `NEEDS_CLARIFICATION`：业务目标、口径或权威来源不明确。
- `BLOCKED`：权限、必需数据、模型能力、依赖服务或安全策略不满足。
- `VALIDATION_FAILED`：测试或回放未通过。
- `PAUSED`：预算耗尽、人工干预或运行风险触发。
- `DEPRECATED`：被新版本替代，但历史版本仍可追溯。

状态迁移必须校验前置条件，禁止从 `DRAFT` 直接进入 `ACTIVE`。每次状态变化记录 actor、时间、原因、traceId、Blueprint 版本和关联审计事件。

### 6.4 构建流程

1. **目标理解**：W Agent 将自然语言目标解析成目标、范围、成功条件、限制和待澄清问题。
2. **资产盘点**：调用 Context Builder 和各引擎只读接口，识别已有数据、本体、知识、认知资产、模型/工具能力。
3. **缺口分析**：把缺失资产、质量问题、语义冲突、无证据结论、权限缺失、能力缺失转换成 Gap。
4. **生成蓝图候选**：生成 Blueprint 草案，不直接发布或修改权威资产。
5. **补齐依赖**：按审批等级调用数据治理建议、本体候选、知识补充等工具；产生的候选必须带证据、来源、影响范围和回滚方案。
6. **静态校验**：检查依赖存在性、版本约束、权限、工具契约、数据质量门槛、模型能力匹配、自动化等级。
7. **回放/测试**：使用固定数据集或只读预览验证流程；确定性计算结果必须从业务计算服务获得。
8. **审批**：按 security-engine 的策略与自动化等级执行审批；高风险动作不得自动批准。
9. **发布**：发布不可变 Blueprint 版本及摘要；依赖版本要么固定，要么明确采用兼容范围。
10. **运行与反馈**：每次运行关联 Blueprint 版本、Context Digest、模型/工具版本、结果引用和测试结果。

### 6.5 Gap 项结构

| 字段 | 说明 |
|---|---|
| `gapId` | 稳定 ID |
| `category` | `DATA_MISSING`、`DQ_FAILURE`、`ONTOLOGY_GAP`、`KNOWLEDGE_GAP`、`EVIDENCE_GAP`、`CAPABILITY_GAP`、`PERMISSION_GAP`、`CONFLICT` 等 |
| `severity` | `BLOCKER`、`HIGH`、`MEDIUM`、`LOW` |
| `targetRef` | 关联资产或蓝图步骤 |
| `description` | 可读描述 |
| `evidenceRefs` | 形成缺口判断的证据引用 |
| `suggestedActions` | 建议动作，不等于已执行动作 |
| `requiresApproval` | 是否需要审批 |
| `status` | `OPEN`、`IN_PROGRESS`、`RESOLVED`、`ACCEPTED_RISK` |
| `resolutionRef` | 解决该缺口的提案、测试或发布版本 |

### 6.6 Blueprint 验收门槛

- 所有 `BLOCKER` 缺口必须解决，或通过明确的风险接受流程关闭。
- 每个 required dependency 必须存在且满足版本约束。
- 所有写操作必须有 Tool Contract、security-engine 决策和审计事件。
- P0 测试未通过时不能进入 `READY_FOR_APPROVAL`。
- 发布版本不可变；更新生成新版本，不覆盖历史。
- 运行结果必须能回链到 Blueprint 版本、Context Digest、工具/模型版本及输入快照。
- Blueprint 中的“AI 建议”不得自动视为事实或正式业务配置。

---

## 7. 数据治理辅助与本体候选建模

### 7.1 统一候选协议

数据治理建议与本体候选都采用“生成候选—证据校验—预览/测试—审批—正式写入”的模式，但正式写入仍由资产所属引擎负责。

候选不是正式事实，也不是自动生效配置。AI 工具只能创建候选对象或提案，不得直接绕过 data-engine/ontology-engine 的正式写入入口。

### 7.2 数据治理候选

建议由 data-engine 提供下列能力，通过 ai-engine 的 Tool Contract 暴露：

| Capability ID | 输入 | 输出 | 默认权限 |
|---|---|---|---|
| `DATA_PROFILE` | `assetRef`、范围、采样预算 | 列类型、空值率、基数、分布、异常与覆盖率 | 只读 |
| `FIELD_SEMANTIC_SUGGEST` | 数据资产/字段、已有字典与本体引用 | 字段语义候选、候选理由、证据引用、置信等级 | 只读建议 |
| `FIELD_MAPPING_SUGGEST` | 源字段、目标 schema/本体、转换约束 | 映射候选、转换候选、未匹配字段清单 | 只读建议 |
| `DQ_RULE_SUGGEST` | Profile、业务规则、历史异常 | DQ 规则候选、阈值建议、误报风险 | 只读建议 |
| `DQ_PREVIEW` | 候选规则/映射版本、只读样本 | 通过率、异常样本引用、预计影响 | 只读预览 |
| `DATA_CHANGE_PROPOSAL` | 已验证候选、影响范围、回滚计划 | data-engine 的正式变更提案引用 | 需审批 |
| `DATA_CHANGE_APPLY` | 已批准提案 token | 正式执行结果、版本与审计引用 | 高风险写操作，默认人工审批 |

输出候选必须包含：

```json
{
  "candidateId": "cand-data-001",
  "candidateType": "FIELD_MAPPING",
  "targetRef": "asset-001.field-17",
  "proposal": {},
  "evidenceRefs": ["profile-001", "ontology-ref-4"],
  "confidence": {
    "value": 0.78,
    "kind": "MODEL_ESTIMATE",
    "calibrated": false
  },
  "assumptions": [],
  "conflicts": [],
  "impact": {
    "affectedAssets": ["asset-001"],
    "breakingChange": false
  },
  "status": "PROPOSED"
}
```

**置信度规则：**`confidence.value` 不能被当作已校准概率，除非关联模型通过 calibration 门禁且明确声明校准方法、数据集和有效范围。LLM 自报“90% 确信”必须标为 `UNCALIBRATED_MODEL_ESTIMATE`，不得与统计概率混用。

### 7.3 本体候选

ontology-engine 提供以下能力：

- `ONTOLOGY_GAP_ANALYZE`：根据 Blueprint、字段语义与现有本体识别缺少的对象/属性/关系。
- `ONTOLOGY_CANDIDATE_GENERATE`：生成对象类型、属性、关系、约束候选。
- `ONTOLOGY_CANDIDATE_VALIDATE`：检查命名、类型、基数、关系端点、现有本体冲突和向后兼容性。
- `ONTOLOGY_PROPOSAL_CREATE`：将通过校验的候选提交到既有本体提案/审批流程。
- `ONTOLOGY_DIFF_PREVIEW`：显示正式发布前后的结构差异与影响对象。

候选示例：

```json
{
  "candidateId": "cand-onto-001",
  "candidateType": "RELATION",
  "subjectType": "Project",
  "predicate": "HAS_ACCEPTANCE_MILESTONE",
  "objectType": "AcceptanceMilestone",
  "properties": [],
  "evidenceRefs": ["data-profile-001", "kb-chunk-221", "decision-basis-18"],
  "confidence": {
    "value": 0.71,
    "kind": "MODEL_ESTIMATE",
    "calibrated": false
  },
  "conflicts": [
    {
      "targetRef": "ontology-version-7",
      "description": "现有关系语义可能重叠"
    }
  ],
  "compatibility": {
    "breakingChange": false,
    "requiresMigration": true
  },
  "status": "PROPOSED"
}
```

### 7.4 候选通用状态机

```text
GENERATED
  -> EVIDENCE_CHECKED
  -> VALIDATED
  -> PENDING_APPROVAL
  -> APPROVED
  -> APPLIED
```

分支状态：
- `REJECTED`：审批拒绝；
- `NEEDS_EVIDENCE`：证据不足；
- `CONFLICTED`：与权威资产冲突；
- `EXPIRED`：所依赖的数据/本体/模型版本已过期；
- `ROLLED_BACK`：应用后经回归发现问题并完成回滚。

不得将 `GENERATED` 或 `VALIDATED` 当成 `APPROVED`。生成候选与应用正式变更必须是两个不同动作、两个不同权限判断、两个不同审计事件。

### 7.5 安全与幂等

- 每个候选都必须有 `candidateId`、`idempotencyKey`、`tenantId`、`createdBy`、`traceId`、`sourceRunId`。
- 同一 `idempotencyKey` 重试不得重复创建或重复应用。
- Tool Contract 声明 `operationClass=READ|SUGGEST|WRITE`、`automationLevel`、`requiresApproval`、`dataClassificationMax`、`evidenceRequired`。
- 写操作必须经过统一 `ToolExecutor` 和 security-engine；策略依赖失败时 DENY。
- 预览只读，不应改变正式资产、版本号或生产水位线。
- 候选引用的源版本变化后，候选自动标记为 `EXPIRED` 或要求重新验证。

---

## 8. 认知资产模型与生命周期

### 8.1 资产类型分层

为避免“所有推理都不落盘”和“所有推理都长期保存”两种错误，按用途区分以下类型：

| 层次 | 对象/内容 | 持久化规则 | 权威性 |
|---|---|---|---|
| A. 权威业务事实 | 业务对象、指标事实、合同/项目/实际结果等 | 由所属业务引擎/data-engine 写入 | 最高，必须有来源与版本 |
| B. 治理后的认知资产 | Hypothesis、Belief、Decision Basis、Cognitive Model 及经治理的 Pattern/Case 等 | 仅按 PRD-05 对象目录、载体归属和审批规则持久化 | 受治理；不自动等于事实 |
| C. 运行时推理产物 | 单次诊断、临时推理链、上下文拼装结果、临时计划和中间思考 | 默认不作为业务事实落盘；通过 traceId/runId 与审计事件关联 | 临时、可过期 |
| D. 证据引用 | 文档、数据快照、图谱边、计算结果、运行结果的引用 | 保存引用 ID、版本、哈希、时点；正文由权威来源保存 | 支持核验，不替代来源 |
| E. 模型与能力定义 | CM Registry、模型版本、Capability Contract、评估与门禁结果 | 按对应控制域/注册表持久化；发布版本不可变 | 受治理的配置资产 |

### 8.2 关键语义

- **Fact**：由权威业务源确认的事实；必须有来源、时间和范围。
- **Evidence**：支持或反驳某一判断的材料引用，不自动等于事实本身。
- **Hypothesis**：可检验的候选解释；可被支持、反驳或过期。
- **Belief**：在给定证据和模型条件下的非确定性判断，需记录其适用范围与更新依据。
- **Rule**：经治理、可重复执行的业务约束；不能因 LLM 一次输出自动成为规则。
- **Pattern/Case/Experience**：经验证并通过所属治理流程沉淀的可复用经验；不能把对话摘要直接入库冒充经验。
- **Decision Basis**：决策时使用的依据集合与版本引用，应可回放，但 Agent 只读。
- **Context/Situation**：一次任务中的临时组合，默认请求态；必要的回放信息通过 digest 和引用记录，而不是复制完整上下文表。

### 8.3 Belief 置信与概率

必须区分三种值：

1. `STATISTICAL_PROBABILITY`：由可复算的统计模型产生，需记录模型版本、数据集/范围和校准状态。
2. `CALIBRATED_MODEL_PROBABILITY`：通过校准门禁的模型概率，需声明校准方法与适用域。
3. `UNCALIBRATED_CONFIDENCE`：模型/LLM 的置信估计，不可直接用于金额计算、自动审批或作为已验证概率。

建议 API/DTO 增加语义字段（只增不改）：

```json
{
  "probability": 0.72,
  "probabilityKind": "UNCALIBRATED_CONFIDENCE",
  "calibrationRef": null,
  "modelRef": "cm-07:v3",
  "evidenceRefs": ["ev-1", "ev-2"],
  "scopeRef": "scenario-001",
  "asOfTime": "2026-10-09T00:00:00Z"
}
```

对旧数据无法确定概率来源的，不要伪造补齐；返回 `probabilityKind=UNKNOWN_LEGACY` 并纳入治理清单。

### 8.4 认知资产生命周期

建议统一逻辑状态（具体物理表按现有对象载体扩展，遵守只加不删）：

```text
DRAFT
  -> CANDIDATE
  -> EVIDENCE_CHECKED
  -> VALIDATED
  -> APPROVED
  -> ACTIVE
  -> SUPERSEDED
```

异常状态：
- `CONTRADICTED`：出现足以反驳当前判断的新证据；
- `EXPIRED`：证据、数据快照或适用范围过期；
- `REJECTED`：审批拒绝；
- `DEPRECATED`：不再推荐使用，但保留历史；
- `SUSPENDED`：安全、质量或漂移门禁触发暂时停用。

状态迁移要求：
- 每次迁移有操作者/系统身份、原因、证据引用、旧/新版本和审计 traceId。
- `ACTIVE` 的规则/模型版本不可原地覆盖；变更创建新版本并建立 `supersedes`。
- `CONTRADICTED` 不等于删除；必须保留反证链，以便审计和模型评估。
- 单次错误或单条反馈只能触发“复审候选”，不能自动改变生产 Belief/Rule/Model。
- C 与 K 类型清单继续遵循 PRD-04/05 的封闭枚举与互斥守护；不得把 C 类型经 KB 写入路径静默落成 K 资产。

---

## 9. W→D 结果验证与认知反馈

### 9.1 目标

把“任务执行完了”与“业务结果验证过了”区分开。Run 成功只说明工具/流程按预期完成，不代表预测正确、业务假设成立或模型可以更新。

### 9.2 流程

1. **收集结果**：取得业务运行结果、实际值、时间窗口、口径版本和输入快照引用。
2. **对齐口径**：确认预测与实际在同一指标定义、时间范围、组织范围和币种/单位下比较。
3. **计算误差**：使用确定性计算器计算偏差、MAPE/MAE 等指标；样本不适用时返回明确原因。
4. **分解偏差**：按项目、部门、期间、因素贡献定位差异；数值来源必须可复算。
5. **认知诊断**：cognitive-engine 关联当时使用的 Hypothesis、Belief、Decision Basis、Cognitive Model 版本，形成解释和待验证假设。
6. **生成更新建议**：可建议调整假设、更新证据、补充案例、重新评估模型或修改数据规则；全部是 Candidate/Proposal。
7. **影响分析**：识别受影响的 Blueprint、运行、下游场景、模型与决策依据。
8. **回归/回测/校准**：按资产类型执行必要测试；模型发布至少通过对应门禁。
9. **审批与发布**：按 security-engine 策略审批；通过后生成新版本，不覆盖旧版本。
10. **闭环记录**：保留输入/实际/误差/建议/审批/新版本的引用链。

### 9.3 输出契约

建议内部接口：

`POST /api/v1/cognitive/outcomes/evaluate`

请求：

```json
{
  "scenarioId": "scenario-001",
  "blueprintId": "bp-001",
  "blueprintVersion": 4,
  "runId": "run-001",
  "forecastRunId": "fr-001",
  "actualRef": {
    "assetId": "actuals-2027-08",
    "version": "7",
    "asOfTime": "2027-09-10T00:00:00Z"
  },
  "metricRefs": ["M_FC_PROFIT", "M_REALIZATION_RATE"],
  "evaluationWindow": {
    "from": "2027-08-01",
    "to": "2027-08-31"
  }
}
```

响应逻辑结构：

```json
{
  "code": 0,
  "data": {
    "evaluationId": "eval-001",
    "status": "EVALUATED",
    "metrics": [
      {
        "metricRef": "M_FC_PROFIT",
        "predicted": 980000,
        "actual": 930000,
        "absoluteError": 50000,
        "relativeError": 0.0538,
        "calculationRef": "calc-001"
      }
    ],
    "diagnoses": [],
    "updateCandidates": [],
    "warnings": []
  }
}
```

该接口的数值字段必须由确定性业务计算服务产生，认知引擎只负责组织引用、诊断和候选建议。实际部署时如已有预测/评估接口，应优先以适配器扩展既有接口，不重复创建同义端点。

### 9.4 防止错误学习

以下情形不得触发自动更新：
- 实际数据未关账或尚未达到成熟观察窗口；
- 预测与实际口径不一致；
- 关键数据质量门禁失败；
- 证据不足或来源冲突；
- 误差可能由一次性外部事件造成；
- 新旧模型在同一基准集上没有完成回归；
- 变更影响高风险业务规则但未获审批。

---

## 10. Model & Capability Center 与小模型

### 10.1 模型与能力分离

模型是可调用的实现；能力是业务可请求的稳定契约。一个能力可由多个模型实现，一个模型也可支持多种能力。业务任务应请求 `capabilityId`，由 Model Router 结合约束选择实现，避免在工具/业务代码中硬编码供应商或模型名。

### 10.2 建议能力目录

| 能力族 | Capability ID 示例 | 输入 | 输出 | 典型模型 |
|---|---|---|---|---|
| 文本生成/推理 | `LLM_TEXT_REASONING` | 文本、Context Pack 引用 | 结构化文本/假设候选 | 通用 LLM |
| 向量化 | `TEXT_EMBEDDING` | 文本片段 | 向量引用/向量值 | Embedding 模型 |
| OCR | `DOCUMENT_OCR` | 文件/图片引用 | 文本块、页码、区域、置信/质量标识 | OCR 小模型 |
| 文档结构解析 | `DOCUMENT_LAYOUT_PARSE` | PDF/图片/扫描件引用 | 标题、段落、表格、阅读顺序与页码 | Layout 模型 |
| 语音识别 | `SPEECH_TO_TEXT` | 音频引用 | 转写文本、时间戳、语言与质量标识 | ASR 模型 |
| 语音合成 | `TEXT_TO_SPEECH` | 文本、声音/语言参数 | 音频引用、格式、时长 | TTS 模型 |
| 图像理解 | `IMAGE_UNDERSTANDING` | 图片引用、问题、Context Pack 摘要 | 结构化描述/视觉问答 | 多模态模型 |
| 分类/抽取 | `ENTITY_EXTRACTION`、`DOCUMENT_CLASSIFY` | 文本/文档引用 | 实体、类别、来源区间 | 小模型或 LLM |
| 重排序 | `RERANK` | 查询、候选引用列表 | 排序分数与顺序 | Reranker |
| 语义相似度 | `SEMANTIC_SIMILARITY` | 两段文本/案例引用 | 相似度及模型/校准说明 | Embedding/Reranker |

能力目录中的输出统一带 `modelRef`、`modelVersion`、`inputRef`、`traceId`、`latencyMs`、`status` 和可选质量/置信字段。模型输出的“置信度”仍需按第 8.3 节标注类型。

### 10.3 路由策略

Model Router 至少考虑：
1. 能力匹配；
2. 输入模态与格式；
3. 数据密级/区域/部署限制；
4. 质量门槛和校准状态；
5. 延迟预算；
6. Token/调用成本预算；
7. 可用性、错误率和并发；
8. 模型版本与发布状态；
9. 是否支持流式/批量/异步；
10. 是否允许将输入发送到外部 provider。

风险/密级由 security-engine 提供，不能在 Router 内复制成另一套策略。路由决策应记录 `requestedCapability`、候选模型、选中模型、未选原因、预算和策略引用。

### 10.4 小模型调用接口

内部统一接口建议：

```java
public interface ModelCapabilityExecutor {
    CapabilityResult execute(CapabilityRequest request);
    AsyncCapabilityHandle submit(CapabilityRequest request);
    CapabilityStatus getStatus(String taskId);
}
```

这是逻辑契约，不要求新建 Maven 模块。同步小任务可直接执行；大文件 OCR、批量语音转写等通过 `runtime-task` 异步提交，返回 `taskId`，由统一任务底座管理超时、重试、取消和状态。

### 10.5 统一结果结构

```json
{
  "capabilityId": "DOCUMENT_OCR",
  "status": "SUCCEEDED",
  "inputRef": "file-001",
  "modelRef": "ocr-model-a",
  "modelVersion": "3",
  "outputRef": "ocr-result-001",
  "quality": {
    "score": 0.93,
    "kind": "MODEL_ESTIMATE",
    "calibrated": false
  },
  "warnings": [],
  "traceId": "tr-001",
  "latencyMs": 420
}
```

OCR/ASR 输出应包含可追溯的页码、时间戳或文本区间，便于后续 KB 抽取、本体候选与认知判断引用证据。对于不适合持久化的内容，只保存结果引用与元数据，不复制大文件或完整敏感输入到控制域表。

### 10.6 小模型治理

- 模型注册、版本、能力标签、输入输出 schema、许可、部署位置和密级上限必须可查。
- 发布前执行 contract、regression、backtest（适用时）、calibration（适用时）门禁。
- 模型不可用时只允许切换到满足同一能力契约、密级和质量要求的备用模型。
- 若降级导致质量门槛不满足，返回 `DEGRADED` 或失败，不能仍标 `SUCCEEDED`。
- 每次调用纳入 Token/调用量/成本/延迟/错误率计量；非 Token 模型也需按调用次数、输入规模、GPU/CPU 时间或配置的成本单位计量。
- OCR、ASR 等结果如果用于权威业务决策，必须经过字段/内容验证，不得仅凭模型输出自动写入权威事实。

---

## 11. Tool Contract 与自动化等级扩展

### 11.1 工具契约字段

在既有 Tool Contract 上补充以下机器可校验字段（如已有同义字段则映射，不重复造字段）：

| 字段 | 作用 |
|---|---|
| `capabilityId` | 关联能力目录 |
| `operationClass` | `READ`、`SUGGEST`、`WRITE` |
| `inputSchemaRef` / `outputSchemaRef` | 机器可读输入/输出契约 |
| `evidenceRequired` | 是否要求来源引用 |
| `deterministicOutput` | 输出是否应由确定性计算产生 |
| `automationLevel` | 工具所需自动化等级 |
| `dataClassificationMax` | 工具允许处理的最高密级 |
| `requiresApproval` | 是否需要人工/双人审批 |
| `idempotent` / `idempotencyKeyRequired` | 幂等约束 |
| `timeoutMs` / `retryPolicyRef` | 超时和重试策略 |
| `sideEffectScope` | 影响的资产/范围 |
| `rollbackCapability` | 是否可回滚及回滚方式 |
| `auditEventType` | 统一审计事件类型 |
| `ownerEngine` | 正式语义与写入权归属引擎 |

### 11.2 操作类别约束

- `READ`：只读，不改变正式资产；仍需权限校验。
- `SUGGEST`：生成候选或提案，不修改权威资产；必须带证据、适用范围、置信类型和冲突。
- `WRITE`：改变正式资产或触发有副作用的任务；必须通过 security-engine 裁决，满足审批要求、幂等、审计和回滚契约。

任何没有有效 Tool Contract、未处于 `ACTIVE`、输入输出 schema 不匹配、能力不满足或预算不足的工具，不得进入执行计划。

---

## 12. 分层评估与可观测性

### 12.1 评估层次

| 层次 | 指标例子 | 评估频率 | 用途 |
|---|---|---|---|
| 数据层 | 数据源覆盖率、DQ 通过率、字段映射准确率、Profile 新鲜度、冲突数 | 每次资产发现/管道运行 | 判断数据是否可用 |
| 知识/本体层 | 证据可解析率、对象/关系候选接受率、本体冲突率、知识引用命中率 | 每次候选/版本发布 | 判断语义层质量 |
| 认知层 | 假设被支持/反驳比例、Belief 校准误差、因果路径证据覆盖率、CM 门禁通过率 | 每次评估与定期回测 | 判断推理与判断质量 |
| Agent/工具层 | 计划成功率、工具调用成功率、重规划率、人工介入率、预算超限率、策略拒绝率 | 每个 Run/Step | 判断执行可靠性 |
| 场景层 | Blueprint 从草案到可发布耗时、必需缺口关闭率、回归通过率、发布后回滚率 | 每个场景版本 | 判断场景构建效率 |
| 业务层 | 预测误差、风险识别提前量、行动完成率、实际业务改善、决策周期变化 | 按业务周期 | 判断业务价值 |
| 运营层 | 首个可用数据源耗时、字段映射人工修改比例、Ontology Candidate 接受率、每个场景人工投入、单次运行成本 | 周/月 | 判断平台效率与成本 |

### 12.2 统一 Trace 维度

在既有 traceId/runId 基础上，建议对所有跨引擎调用传递：

- `traceId`
- `scenarioId`
- `blueprintId`
- `blueprintVersion`
- `runId`
- `stepId`
- `contextDigest`
- `capabilityId`
- `toolContractVersion`
- `modelRef` / `modelVersion`
- `sourceRefs`（仅引用 ID，不写敏感正文）
- `tenantId`（按安全规范传递）
- `resultRef`
- `approvalRef`（存在审批时）

日志和事件不得记录密钥、原始凭据或不必要的敏感数据。跨引擎调用必须保留真实 HTTP 状态和错误码，不能将依赖失败包装成“成功但为空”。

### 12.3 评估基准集

至少建立以下基准集：
- 数据剖析与 DQ 异常固定样本；
- 字段语义/本体候选标注集；
- 证据支持/反驳与冲突识别集；
- CM-01～07 的契约测试与模型评估集；
- Agent 工具权限、预算、超时、重试与注入攻击集；
- 年度经营预测固定演练包及其历史输入快照；
- OCR/ASR/图像理解的领域样本与质量阈值集。

基准集版本必须固定并可追溯。新模型或提示模板发布前比较旧版本，达到门槛才可发布。测试失败时不得以人工解释覆盖机器判定结果。

---

## 13. API 与事件契约总表

> 以下是逻辑 API 清单。若现有接口已承担相同职责，应扩展现有接口并保留路径/语义兼容；新增接口必须进入 P-5 OpenAPI 单源，补齐 operationId、鉴权、错误码、schema 和测试。

| 逻辑接口/事件 | 所属 | 用途 | 写入/副作用 |
|---|---|---|---|
| `POST /api/v1/cognitive/context/build` | cognitive-engine | 生成请求态 Context Pack | 不写认知事实表 |
| `GET /api/v1/cognitive/assets/catalog` | cognitive-engine | 查询 C 类对象目录及载体/引用态 | 只读 |
| `GET /api/v1/cognitive/assets/{id}/resolve` | cognitive-engine | 解析受治理认知资产引用 | 只读 |
| `POST /api/v1/cognitive/assets/{id}/proposals` | cognitive-engine | 提交认知资产变更建议 | 只创建候选/提案，按既有治理载体 |
| `POST /api/v1/cognitive/outcomes/evaluate` | cognitive-engine/业务适配器 | 评估运行与实际结果，提出更新候选 | 数值由确定性业务服务计算 |
| `POST /api/v1/data/governance/profile` | data-engine | 数据剖析（若已有同义接口优先复用） | 只读 |
| `POST /api/v1/data/governance/candidates` | data-engine | 创建映射/DQ/语义候选 | 候选态，不直接改正式资产 |
| `POST /api/v1/ontology/candidates` | ontology-engine | 创建本体候选并进入提案流程 | 候选态，不直接发布 |
| `GET /api/v1/ai/capabilities` | ai-engine | 查询可用能力与契约版本 | 只读 |
| `POST /api/v1/ai/capabilities/{id}/execute` | ai-engine | 执行指定能力（通过统一路由） | 按 Tool Contract 决定 |
| `POST /api/v1/ai/capabilities/{id}/tasks` | ai-engine/runtime-task | 提交长耗时模型任务 | 异步任务 |
| `ecos.audit` 运行/决策事件 | runtime-event | 记录状态迁移、策略决策、候选批准与结果引用 | 不建立第二套 Agent 运行日志表 |
| `ScenarioBlueprintPublished`（逻辑事件） | workspace/runtime-event | 蓝图发布通知 | 事件名须并入既有事件类型单源清单后方可使用 |
| `CognitiveCandidateProposed`（逻辑事件） | cognitive-engine/runtime-event | 认知资产更新候选通知 | 事件名须先登记到事件目录 |

### 13.1 统一错误语义

| 错误类型 | HTTP 建议 | 处理策略 |
|---|---:|---|
| 输入/schema 错误 | 400 | 指明字段与错误，不自动重试 |
| 场景/资产不存在 | 404 | 不降级为伪造空数据 |
| 不属于当前场景的 Mind/资产引用 | 400 或按既有契约 | 严格拒绝，禁止静默扩大范围 |
| 权限/密级拒绝 | 403 | 不重试，不暴露敏感对象是否存在（按安全策略） |
| 必需依赖未就绪 | 503 | 传播真实错误，按预算有限重试 |
| 业务前置条件未满足 | 409 | 返回缺口、冲突或状态迁移原因 |
| 质量/回归门禁失败 | 422 | 保留失败明细，不允许发布 |
| 预算耗尽 | 429 或既有业务错误码 | Run 转 `PAUSED`/`NEEDS_RECONCILE`，不得返回成功 |
| 模型暂不可用 | 503/既有码表 | 仅按能力契约尝试合格备用模型 |
| 幂等键冲突 | 409 | 返回原操作引用或明确冲突 |

实际 HTTP 状态码与错误码以各分册既有契约为准；本表作为跨引擎补充原则，不覆盖既有冻结契约。

---

## 14. 数据设计与持久化约束

### 14.1 本文不直接规定所有新表

现有详细设计已包含 DDL、迁移版本与数据库访问红线。本补充设计优先通过既有对象、DTO、事件与 API 实现能力，避免重复表与双写。

只有在实现前确认以下条件全部成立时，才可新增持久化字段或表：
1. PRD/对象目录已定义其权威归属；
2. 不存在语义相同的既有表/载体；
3. 不属于运行时上下文或临时推理结果；
4. 通过 schema、审计基线列、UUID、JSON/TEXT、索引与多租户规范检查；
5. DDL 进入唯一迁移源，并有回滚/恢复方案；
6. 存量迁移与新数据写入分开审批。

### 14.2 建议优先复用的既有载体

- C 类资产与模型注册：按 PRD-05 REQ-COG-06～09、详细设计-05 对对象目录与载体的定性使用；先完成双镜像权威复核。
- Agent Run/Step/Tool Contract/Candidate：按 PRD-06 REQ-AI-06～11 与既有 W Agent 设计复用，不新建第二套 Run 记录。
- Blueprint：归 workspace/场景工作台；不在 ai/cognitive 域复制。
- Data Candidate：由 data-engine 维护正式候选与应用状态。
- Ontology Candidate：由 ontology-engine 的既有提案系统维护。
- 审计与事件：复用 `ecos.audit`、runtime-event 与既有 traceId 机制。
- 大文件、OCR/ASR 结果：按既有 runtime-access/文件存储路径保存，业务库仅保存引用与元数据。

---

## 15. 实施顺序与交付批次

### 15.1 批次 A：契约和安全门禁（优先级最高）

目标：先保证新闭环有统一契约、权限边界和真实错误语义。

1. 梳理 PRD-05/06 既有 API 与 OpenAPI，确定复用接口和增量接口；不得重复造同义 API。
2. 定义 `ContextPack`、`EvidenceRef`、`CalculationResultRef`、`CapabilityContract` 和 `ScenarioBlueprint` 的机器可读 schema。
3. 补齐 Tool Contract 的 `READ/SUGGEST/WRITE`、证据要求、能力 ID、自动化等级、密级上限、审批和幂等字段。
4. 验证所有新工具进入既有 ToolExecutor 唯一咽喉；security-engine 不可用时写操作默认 DENY。
5. 建立跨引擎错误矩阵、trace 关联字段和 P-5 契约一致性门禁。
6. 为现有 E1–E4、CM Registry、Run 状态机、会话压缩、安全护栏补齐缺失测试；未执行测试一律标“未执行”。

### 15.2 批次 B：最小端到端场景闭环

以“年度经营预测”为首个验收场景，避免同时铺开所有场景：

1. 用户提出目标；
2. W Agent 生成目标/范围/成功条件；
3. Context Builder 组装数据、口径、本体、知识、认知与模型能力引用；
4. Workspace 创建 Blueprint 草案；
5. 自动形成 Gap 清单；
6. 只读验证基准数据、质量与预测输入；
7. 执行确定性预测，获得 `forecastRunId` 和输入快照引用；
8. cognitive 生成领域化因果诊断与证据/假设分离结果；
9. Workspace 完成 Blueprint 验证与审批；
10. 对实际结果执行 Outcome Evaluation，生成更新候选而非自动改写生产资产。

**批次 B 验收红线：**
- 金额不能定位到输入快照和确定性计算；
- 场景变更必须严格限制到指定项目/部门/期间；
- 证据不足的陈述必须标为待验证假设；
- AI 不得绕过安全门禁写数据、本体或认知资产；
- 任何必需依赖失败不能伪装成空结果/成功；
- 运行结果能回链 Blueprint、Context Digest、模型/工具版本和 traceId。

### 15.3 批次 C：AI 辅助数据治理与本体建模

1. 接入 `DATA_PROFILE`、`FIELD_SEMANTIC_SUGGEST`、`FIELD_MAPPING_SUGGEST`、`DQ_RULE_SUGGEST`。
2. 接入 `ONTOLOGY_GAP_ANALYZE`、`ONTOLOGY_CANDIDATE_GENERATE`、`ONTOLOGY_CANDIDATE_VALIDATE`。
3. 所有候选必须带来源、证据、版本、冲突、影响范围、置信类型和回滚信息。
4. 候选只能通过所属引擎的提案/审批/写入路径变成正式资产。
5. 建立固定样本、候选接受率、回归通过率和人工修订率统计。

### 15.4 批次 D：模型能力扩展与运营治理

1. 将 OCR、文档结构解析、ASR、TTS、图像理解、分类抽取、重排序纳入 Capability Center。
2. 建立同步/异步调用、预算、取消、超时、重试和降级策略。
3. 建立能力级 contract/regression/backtest/calibration 门禁。
4. 将线上错误率、延迟、成本、质量与漂移反馈到 Model Router。
5. 未通过质量阈值的备用模型不得被自动选中。

### 15.5 批次 E：认知资产反馈闭环

1. 建立 Outcome Evaluation 固定评估流程。
2. 将偏差定位到具体输入、口径、假设、证据、模型版本和场景蓝图。
3. 生成更新候选并执行影响分析。
4. 通过回归/回测/校准后走审批和新版本发布。
5. 建立过期、反证、废弃、暂停状态与复审任务。

---

## 16. 验收矩阵

| ID | 需求来源 | 验收点 | 测试类型 | 通过标准 |
|---|---|---|---|---|
| SUP-CTX-01 | 八项建议 #2 | Context Pack 来源均带 ID/版本/时点 | 单元/契约 | 缺少来源的事实类项被拒绝 |
| SUP-CTX-02 | REQ-SEC/COG-05 | 未授权数据不会进入 Context Pack 或模型输入 | 安全集成 | 0 泄露；权限依赖失败默认 DENY |
| SUP-CTX-03 | REQ-COG-01 | 空集合、404、403、503 语义不混淆 | API/前端 E2E | 每种状态有独立呈现，不以空数据伪装依赖失败 |
| SUP-BP-01 | 八项建议 #3 | Blueprint 状态迁移非法时拒绝 | 状态机测试 | 非法迁移返回 409，状态不变 |
| SUP-BP-02 | REQ-AI-06 | 发布前必需依赖、P0 测试和审批条件全部满足 | 集成 | 缺任一条件不得发布 |
| SUP-BP-03 | REQ-COG-03/FC | 运行可回链蓝图版本、Context Digest、输入快照 | 端到端 | 运行记录引用完整且可解析 |
| SUP-DATA-01 | 八项建议 #4 | DQ/映射候选预览不改变正式资产 | 集成 | 预览前后正式版本/水位线/行数无变化 |
| SUP-DATA-02 | REQ-AI-01 | 数据治理写操作通过唯一 ToolExecutor 和策略裁决 | 安全集成 | 绕过咽喉的调用被架构测试拦截 |
| SUP-ONTO-01 | 八项建议 #5 | 本体候选带证据和兼容性结果 | 契约/集成 | 缺证据/冲突未处理的候选不能发布 |
| SUP-ONTO-02 | REQ-ONTO | AI 无权直接发布正式本体 | 安全集成 | 直接写正式对象路径被拒绝 |
| SUP-COG-01 | REQ-COG-05/06 | C 类 11 对象各自唯一载体或明确引用态 | 架构/集成 | 目录 11 项无双载体歧义，禁止名 0 命中 |
| SUP-COG-02 | REQ-COG-07 | CM-07 不能写 probability/金额/业务比率 | 单元/架构 | 所有写路径被拒绝 |
| SUP-COG-03 | 八项建议 #6 | 运行态推理结果不被自动沉淀为长期资产 | 集成 | 无批准提案时认知资产状态/行数不变 |
| SUP-COG-04 | REQ-COG-08 | Decision Basis 单写权威、Agent 只读 | 架构/集成 | 镜像未定性时不开放双写 |
| SUP-OUT-01 | 八项建议 #7 | 单次偏差只产生更新候选，不直接更新生产资产 | 集成 | 评估前后生产版本不变 |
| SUP-OUT-02 | REQ-COG-09/FC | 预测金额与差异贡献可确定性复算 | 端到端 | 贡献合计与总差额在既定精度容差内一致 |
| SUP-MODEL-01 | REQ-AI-08 | OCR/ASR/图像理解能力通过 Capability ID 路由 | 契约/集成 | 调用方不硬编码 provider/model |
| SUP-MODEL-02 | REQ-AI-03/08 | 模型版本、延迟、成本/调用量、错误率均可追踪 | 可观测性 | 指定 trace 可查到完整调用记录 |
| SUP-MODEL-03 | REQ-COG-07/AI-08 | 未校准置信度不冒充概率 | 单元/契约 | 输出带 `probabilityKind`/校准状态 |
| SUP-FEEDBACK-01 | 八项建议 #8 | 形成数据、认知、场景、业务四层指标 | 评估集成 | 仪表/报告可查询定义、时间范围与来源 |
| SUP-GOV-01 | REQ-AI-01/09 | 安全服务超时、无策略、无审批凭证时写操作 DENY | 故障注入 | 所有写操作 fail-closed |
| SUP-GOV-02 | REQ-AI-06/10 | 长任务由 runtime-task 管理 | 架构/集成 | 无新增私有调度器/裸线程 |
| SUP-GOV-03 | REQ-PLT-26/P-5 | 新增 API/工具均有 OpenAPI operationId 与契约校验 | CI | 漏登/不一致即失败 |

> 测试类名应在核对现有仓库后登记为实际可执行名称。本文中的 `SUP-*` 是验收需求 ID，不是声称已经存在的测试类。测试未创建或未运行时必须标记“未执行”。

---

## 17. 与现有文档的回写清单

本文不取代原有 PRD 或详细设计。建议按以下方式回写，避免同一规则在多处复制后发生漂移：

| 文档 | 回写内容 |
|---|---|
| PRD-05 | 将 Context Pack、认知资产生命周期、Outcome Evaluation 与置信类型列为需求增量；明确与 REQ-COG-05/06～09 的关系 |
| 详细设计-05 | 新增 Context Builder、认知资产生命周期、EvidenceRef/CalculationResultRef、Outcome Evaluation 章节；保留“不新增 cognitive_context 表”的约束 |
| PRD-06 | 补充 Model & Capability Center 的小模型能力目录、候选工具的 READ/SUGGEST/WRITE 语义与跨引擎评估要求 |
| 详细设计-06 | 补充 Capability Contract、小模型同步/异步执行、候选类工具安全契约、Blueprint 编排 Playbook、统一关联维度 |
| PRD-02 / 详细设计-02 | 定义数据剖析、字段语义、映射、DQ 规则候选的正式归属和写入/回滚接口 |
| PRD-03 / 详细设计-03 | 定义本体候选生成、证据引用、冲突检测、提案、批准与发布契约 |
| PRD-08 / 详细设计-07/08 | 将 Scenario Blueprint 作为场景工作台权威对象，补状态机、Gap 清单、版本、测试与发布页面/接口 |
| PRD-09 / 详细设计-09 | 将年度经营预测作为首个端到端闭环验收场景，补 Blueprint、Context Digest、Outcome Evaluation 和反馈候选 |
| PRD-10 / 详细设计-10 | W Agent 只负责目标理解、规划、工具调用、预算和重规划；不定义 C/K 资产，不绕过各引擎写入通道 |
| PRD-01 / 详细设计-00/01 | 将跨引擎 Trace 维度、错误语义、安全策略、P-5 OpenAPI 契约单源和测试门禁统一化 |

---

## 18. 最终实施判定标准

补齐后，ECOS 应能够完成以下端到端场景，而不依赖开发人员手工拼接各引擎：

1. 用户描述业务目标，系统能识别目标、范围、成功标准和待澄清问题。
2. 系统能盘点已有数据、本体、知识、认知资产和可用模型/工具能力。
3. 系统能生成 Context Pack，明确权威来源、版本、时点、权限、冲突、缺口和预算裁剪。
4. 系统能生成可版本化的 Scenario Blueprint，列出依赖、Gap、风险、测试与审批要求。
5. 系统能提出数据治理和本体候选，但所有正式变更都通过所属引擎的提案/审批/写入流程。
6. 系统能根据能力契约选择 LLM 或小模型，并保留模型版本、质量、延迟、成本与 trace。
7. 系统能在统一安全护栏、预算和任务底座约束下执行场景；遇到依赖失败、证据不足或预算耗尽时暂停/澄清，而不是伪造成功。
8. 系统能将确定性业务结果、输入快照、证据和认知解释分离展示，保证每个业务数值可复算。
9. 系统能比较实际结果与预期结果，形成偏差诊断与更新候选；不会因单次偏差自动修改生产认知资产。
10. 系统能通过影响分析、回归、回测、校准和审批发布新版本，并保留历史链路。
11. 系统能按数据、知识/本体、认知、Agent、场景、业务和运营七个层次评估质量与效率。
12. 所有新增接口、模型能力、候选和状态迁移都有机器可读契约、可执行测试、审计和可追溯性。

**完成定义（Definition of Done）：**
- 以上 12 项均有对应需求编号、接口/状态机/数据契约和自动化验收；
- P0 安全与数值真实性红线全部通过；
- 固定的年度经营预测演练端到端通过；
- 所有测试真实运行并记录结果，不以“设计已写”“类名已列”“审批已通过”替代执行证据；
- 原有 PRD、详细设计、OpenAPI、DDL 和测试矩阵的追溯关系一致；
- 存量双写、旧表迁移、模型发布与生产写入均经过规定的审批和迁移门禁。

---

## 附录 A：关键名词

| 名词 | 本文定义 |
|---|---|
| Context Pack | 单次任务请求态的结构化上下文，不是长期知识库 |
| Context Digest | Context Pack 规范化后的哈希，用于关联与一致性校验 |
| Scenario Blueprint | 业务场景的版本化定义，包含目标、依赖、缺口、测试、审批、发布与结果引用 |
| Candidate | 尚未成为权威资产的建议/变更提案 |
| EvidenceRef | 指向证据源、版本、时间和可解析位置的引用 |
| CalculationResultRef | 指向确定性计算结果及其输入快照/口径版本的引用 |
| Capability | 面向调用方的稳定能力契约，不等同于某个具体模型 |
| Calibrated Probability | 经明确校准方法与门禁验证的概率值 |
| Uncalibrated Confidence | 未校准的模型置信估计，不可作为权威概率 |
| Outcome Evaluation | 运行结果与实际结果的口径对齐、误差计算、诊断和更新建议过程 |

## 附录 B：实现时不得采用的捷径

- 不要新增一个“认知场景引擎”来绕开现有 workspace、cognitive-engine 或 ai-engine。
- 不要在 cognitive/ai 控制 schema 中复制 Blueprint、正式数据规则或本体正式对象。
- 不要把完整 Prompt、完整上下文或模型隐藏推理内容写进长期业务表；只保留必要的引用、摘要哈希和审计元数据。
- 不要让 LLM 直接产出用于财务核算的金额、误差、贡献值或权威概率。
- 不要把 LLM 的置信表达自动转换成 `belief.probability`。
- 不要因模型/引擎返回空内容就视为”没有数据”；要区分空集合、未启用、无权限、缺表、超时和依赖故障。
- 不要让 Candidate 的生成、验证、审批、应用成为一个不可分辨的单步动作。
- 不要因一条错误或一次业务偏差自动更新生产规则、Belief、模型或经验。
- 不要为 OCR/ASR 等小模型另建与 Model & Capability Center 并行的第二套模型管理。
- 不要将”需求已批准”或”设计已完成”表述为”实现已完成”或”验收已通过”。

---

## 19. 实施落地记录（2026-10-10 批次，§15 批次 A~E 首轮）

> 本节为实施回写（R10：不新起平行文档；§17 回写清单的增量落点）。
> 口径纪律沿用 §0.2：<b>代码交付 ≠ 库侧实跑 ≠ 验收通过</b>；DDL 仅落迁移脚本文件（`docs/50-db_script/02-flyway-migration/gateway/` V267~V274），**未对 ecos-postgres 实跑**（IR02 实跑属未授权项，需逐项再确认）；测试运行证据已于 **2026-10-11 逐模块 `mvn` 实跑取回**（首轮 2026-10-10 提交以 `-DskipTests` 通过、把「测试类已建」当成「测试已跑」记录，属假真值，本轮订正，见 §19.4/§19.6）。

### 19.1 契约与事件单源（§15 批次 A）

| 落地物 | 位置 | 溯源 |
|---|---|---|
| 事件登记：Cognition 族（CognitiveCandidateProposed / CognitiveAssetStatusChanged / OutcomeEvaluated / CapabilityInvoked 等）+ Scenario 族（ScenarioBlueprintPublished / …StatusChanged） | `runtime/common-api` `EventTypes.java`（additive，禁改既有 6 名） | §13 事件行；§16 SUP-GOV |
| topic 路由：聚合名 `cognition` → `ecos.cognitive`；`scenario` 走默认 `ecos.audit` | `KafkaTopics.topicForAggregate`（additive case 分支） | §13 |
| 认知域路径单源追加：`CONTEXT_BUILD` / `ASSETS_CATALOG` / `ASSETS_RESOLVE` / `ASSETS_PROPOSALS` / `OUTCOMES_EVALUATE` | `CognitiveApiPaths.java`（append-only） | §5/§8/§9 端点 |
| 错误码增量：来源引用缺失 / 权限拒绝 / 状态非法 / 版本失配 / BLOCKER 缺口未关 / 语义冲突 / 依赖不可用 / 预算耗尽 | `CognitiveErrorCode.java`（append-only，冻结字面量） | §13.1 |
| OpenAPI 单源（12 端点 + 2 逻辑事件 + trace 维度） | `docs/30-设计/openapi/supplementary-cognitive-ai-v1.yaml` | §13 / §12.2 / P-5 |

### 19.2 DDL（V267~V274，仅文件，未实跑）

| 版本 | 对象 | 要点 |
|---|---|---|
| V267 | `ecos_cognitive.ecos_cognitive_outcome_evaluation` | §9 评估记录；数值只存计算引用（可复算），update_candidates 只存候选引用 |
| V268 | hypothesis/belief/model 三表 `lifecycle_state` + belief 置信五列（prob_kind 等） | §8.4 统一生命周期；§8.3 置信三分（存量 NULL → UNKNOWN_LEGACY 回落，禁伪造补齐） |
| V269 | `ecos_ai.ecos_ai_capability_task` | §10.4/10.5 能力调用台账（只存引用与元数据，禁复制正文/大文件） |
| V270 | `ecos_ai.ecos_wagent_tool_contract` 追加 13 列 | §11 机校字段（READ/SUGGEST/WRITE、encryption 上限、审批/幂等/回滚；不重复造同义字段） |
| V271 | `workspace.ecos_scenario_blueprint` | §6 蓝图权威载体（workspace 属主；发布不可变；ai/cognitive 禁复制副本） |
| V272 | `ecos_data.ecos_data_governance_candidate` | §7 数据治理候选（候选态 ≠ 正式资产；幂等键唯一） |
| V273 | `ecos_ontology.ecos_ontology_candidate` | §7.3 本体候选（发布只经既有 proposals 流；AI 禁直发） |
| V274 | `ecos_cognitive.ecos_cognitive_outcome_evaluation` 追加 3 列 + 唯一索引 | §9.5 幂等（`idempotency_key` 同租户唯一，空键不参与约束）；`blockers_text` 承载 §9.4 七情形回显；`caliber_alignment` 承载口径对齐结论（ALIGNED/MISMATCH:/UNCONFIRMED:）。同样仅落文件，未实跑 |

### 19.3 模块实现归属（批次 B~E）

| 批次 | 模块 | 交付 | 状态口径 |
|---|---|---|---|
| B | cognitive-engine | Context Builder（§5 十步组装 + 失败语义五态 + contextDigest 可复现） | 代码交付，见 §19.4 |
| B | cognitive-engine | 认知资产目录/解析/提案（§8；proposal 不改生产行）+ Belief 置信写入守卫 | 代码交付 |
| B | workspace | 场景蓝图状态机（**16 态** = 11 主链 + 5 异常/回退，`BlueprintStatus` 实测；DRAFT 禁直进 ACTIVE）+ §6.6 发布门槛（四道静态验收：BLOCKER 缺口/必需依赖/必需能力/P0 测试 → 审批链齐 → 发布不可变） | 代码交付，见 §19.4（首轮「15 态 / 六道门槛」为凭印象计数，2026-10-11 按枚举与校验器实测订正） |
| C | data-engine | 数据治理候选面（profile/candidates/preview/approve；GENERATED/VALIDATED≠APPROVED） | 代码交付 |
| C | ontology-engine | 本体候选面 + 接既有 proposals 流（无直接 publish 路径） | 代码交付（**2026-10-11 补齐轮**：首轮 §19.3 记"代码交付"为假 —— 该面当时实为零文件，本轮建成 `governance/dto/OntologyCandidateCreateDTO` + `governance/OntologyCandidateService` + `governance/dao/OntologyCandidateDao` + `controller/OntologyCandidateController`（14 端点，§7.3/§7.4/§7.5），并过 ontology ArchUnit 铁幕：禁 import 他引擎包、置信归一在 ontology 侧私有复刻） |
| D | ai-engine | 能力目录/路由/执行/异步任务（§10；未接供应商能力显式不可用，禁伪数据）+ Tool Contract §11 字段 | 代码交付 |
| E | cognitive-engine | W→D 结果评估（§9 十步骨架；7 种禁自更新情形 fail-loud）+ TraceDimensions 常量类（§12.2） | 代码交付（**2026-10-11 补齐轮**：首轮同为假 —— 本轮建成 `api/outcome/` 契约十件 + `cognitive2/service/outcome/`（`OutcomeEvaluateService`/`OutcomeGuardEvaluator`/`DeterministicErrorCalculator`/`OutcomeMetricValueReader` 接缝）+ `dao/OutcomeEvaluationDao` + `controller/OutcomeEvaluateController` + V274；`runtime/common-api` `com.chinacreator.gzcm.common.trace.TraceDimensions` 单源（**15 个常量** = §12.2 的 14 项，其中 `modelRef`/`modelVersion` 分列两枚；2026-10-11 按文件实测订正，原记「14 维」系按文档条目数计） | |

### 19.4 验收矩阵（§16）运行面

**2026-10-11 补实跑轮：本批次冻结测试类首次真实执行。** 命令口径 = `mvn -o -pl <module> test`（离线、串行、单模块，模块已 install 故无 `-am`），判绿以 surefire 汇总行 + `target/surefire-reports/*.txt` 为权威，日志留 `_win_tasks/logs/`：

| 模块 | 实跑 Tests run / Failures / Errors / Skipped | 本批次冻结测试类用例数 | 日志文件 |
|---|---|---|---|
| `engine/cognitive-engine/cognitive-engine-impl` | **311 / 0 / 0 / 0** | `ContextBuilderServiceTest` 14、`ContextPackContractTest` 8、`CognitiveAssetLifecycleTest` 21、`BeliefProbabilityKindGuardTest` 8、`OutcomeEvaluateServiceTest` 9、`DeterministicErrorCalculatorTest` 11 = **71** | `cog-impl-test-r5.log` → 收尾复跑 `ai-cog-test-r2.log` |
| `workspace/workspace-impl` | **116 / 0 / 0 / 0** | `BlueprintStatusMachineTest` 7、`ScenarioBlueprintServiceTest` 10 = **17** | `ws-test-r2.log` |
| `engine/data-engine/data-engine-impl` | **387 / 0 / 0 / 1** | `DataGovernanceCandidateServiceTest` 11 = **11** | `data-test.log` |
| `engine/ontology-engine/ontology-engine-impl` | **104 / 0 / 0 / 0** | `OntologyCandidateServiceTest` 11 = **11** | `onto-test.log` |
| `engine/ai-engine/ai-engine-impl` | **196 / 0 / 0 / 0** | `ModelCapabilityExecutorTest` 6、`CapabilityTaskLedgerTest` 6、`CapabilityConfidenceCalibrationTest` 5 = **17** | `ai-test.log` → 收尾复跑 `ai-cog-test-r2.log`（夹具假 host 改动后复验） |
| `runtime/common-api` + `runtime/runtime-core` | **35 / 0 / 0 / 0** + **10 / 0 / 0 / 0** | `TraceDimensions` 契约面随 common-api 门禁 | `runtime-test-r2.log` |
| `gateway`（全仓门禁汇总） | **126 / 0 / 0 / 4** | 路由单源/文档真实性/LLM 唯一出口/ArchUnit 铁幕 | `gw-test-r2.log`（首跑 `gw-test-r1.log` 5 FAIL，见 §19.6） |

- 本批次冻结测试类用例合计 **127**，实跑全绿（0 failures / 0 errors）。`SUP-GOV-01/02` 架构面复用既有 ArchUnit 守卫（`NoDirectScheduler`、`EndpointResolverUsageArchTest`、`CrossEngineEndpointArchTest`），本批次只增业务测试，未放宽任何既有守卫。
- 两条 skipped 属**环境/上游前置未满足**、非本批次缺陷，如实记录：data-engine 1 skip = `data.quality.service.IncompleteContextUnknownScoreTest` 的 `@Disabled` 行为占位（等 F02-09/W58 的 `DqCheckExecuteResult` + `INCONCLUSIVE` sentinel 落地，与本设计无关）；gateway 4 skip = `arch.ComposePublicSqlGateLintTest`、`arch.PublicSqlRegressionTest`、`f0209.ScoringContextHydrationTest`、`f0214.PublicSqlRegressionTest` 四条 `assumeTrue` 实库/实容器门。
- live 集成/E2E 项（SUP-CTX-02 安全集成、SUP-BP-03 端到端回链、SUP-OUT-02 端到端复算、SUP-GOV-01 故障注入）仍记「**未执行（待实跑授权）**」——单测绿不等于这些项通过。
- 口径订正：2026-10-10 首轮提交以 `-DskipTests` 通过并写「测试类已交付，运行证据以门禁报告为准」，实际当时**无任何门禁报告**，属假真值；自本节起，「测试绿」的声明必须有对应日志文件与 surefire 汇总行支撑。

### 19.5 验收矩阵（§16）→ 代码/测试落点登记

> 下表测试类名已于 **2026-10-11 按仓内实测逐条核对为实存文件**（原记为「目标名」，其中 `CapabilityTaskServiceTest` 系不存在的载体名，已订正，见 SUP-GOV-02 行）。实跑用例数（同日期 `mvn` 门禁 surefire 汇总，证据见 §19.4）：`ContextBuilderServiceTest` 14 · `ContextPackContractTest` 8 · `CognitiveAssetLifecycleTest` 21 · `BeliefProbabilityKindGuardTest` 8 · `OutcomeEvaluateServiceTest` 9 · `DeterministicErrorCalculatorTest` 11 · `BlueprintStatusMachineTest` 7 · `ScenarioBlueprintServiceTest` 10 · `DataGovernanceCandidateServiceTest` 11 · `OntologyCandidateServiceTest` 11 · `ModelCapabilityExecutorTest` 6 · `CapabilityTaskLedgerTest` 6 · `CapabilityConfidenceCalibrationTest` 5 = **127 用例全绿**。

| SUP ID | 落点（模块 · 测试类/载体） | 覆盖的通过标准 |
|---|---|---|
| SUP-CTX-01 | cognitive-engine · `ContextBuilderServiceTest` + `ContextPackContractTest`（`EvidenceRef` 三要素缺失拒） | 缺来源的事实项被拒 |
| SUP-CTX-02 | cognitive-engine · `ContextBuilderServiceTest`（权限 DENY 分支）+ 既有 RLS/CLS 门禁 | 未授权不入 Pack/模型输入，fail-closed |
| SUP-CTX-03 | cognitive-engine · `ContextBuilderServiceTest`（503/403/409/422 分码 + 无伪空断言） | 空集/404/403/503 语义不混淆 |
| SUP-BP-01 | workspace · `BlueprintStatusMachineTest`（DRAFT→ACTIVE 拒 / 非法 409） | 非法迁移返 409 态不变 |
| SUP-BP-02 | workspace · `ScenarioBlueprintServiceTest`（10 用例；validate **四道**静态门槛缺一即 `passed=false`：BLOCKER 缺口 / 必需依赖 / 必需能力 / P0 测试，原记「六门」系凭印象计数，2026-10-11 按 `ScenarioBlueprintValidator` 实测订正）+ release 审批链门槛 | 依赖+P0 测试+审批全满足才发布 |
| SUP-BP-03 | workspace · V271 `context_digest`/`artifact_digest` 列 + `ScenarioBlueprintServiceTest`（回链断言，端到端 live 记未执行） | 运行回链蓝图版本/Digest/输入快照 |
| SUP-DATA-01 | data-engine · `DataGovernanceCandidateServiceTest`（preview 不触正式表 mock） | 预览前后正式版本/水位线/行数无变化 |
| SUP-DATA-02 | data-engine · 既有 ToolExecutor 咽喉 + `DataGovernanceCandidateServiceTest`（APPLY 过裁决） | 绕过咽喉被 ArchTest 拦截 |
| SUP-ONTO-01 | ontology-engine · `OntologyCandidateServiceTest`（缺证据/未解冲突禁提提案） | 候选必带证据+兼容性 |
| SUP-ONTO-02 | ontology-engine · `OntologyCandidateServiceTest`（无直接 publish 路径断言；发布只走 proposals 链） | AI 无权直发正式本体 |
| SUP-COG-01 | cognitive-engine · `CognitiveAssetLifecycleTest`（11 类目录载体/引用态无双轨） | 11 对象唯一载体或明确引用态 |
| SUP-COG-02 | cognitive-engine · `BeliefProbabilityKindGuardTest`（UNCALIBRATED 禁写 probability）+ `CognitiveAssetLifecycleTest`（同族写路径；本批次按生产真值订正：UNCALIBRATED 族经 `BeliefLifecycleGateway.guard` 抛 `CognitiveGuardException`→400，kind 缺失路径仍抛 `BusinessException`，两路异常型不同，原断言统一按 BusinessException 记为假绿已修） | 金额/概率写路径被拒 |
| SUP-COG-03 | cognitive-engine · `CognitiveAssetLifecycleTest`（proposal 不改原行 / 无批准提案状态不变） | 运行态推理不自动沉淀 |
| SUP-COG-04 | cognitive-engine · `CognitiveAssetLifecycleTest`（resolve 只读 / Decision Basis 单写权威镜像未定性不开放双写） | Agent 只读引用 |
| SUP-OUT-01 | cognitive-engine · `OutcomeEvaluateServiceTest`（单次偏差只产候选，mock 断言 0 生产写） | 评估前后生产版本不变 |
| SUP-OUT-02 | cognitive-engine · `DeterministicErrorCalculatorTest`（BigDecimal 精确复算，端到端 live 记未执行） | 贡献合计与总差额容差内一致 |
| SUP-MODEL-01 | ai-engine · `ModelCapabilityExecutorTest`（按 CapabilityId 路由 + 未接能力显式 FAILED 无伪造输出；原名 `ModelCapabilityRouterTest` 作废 -- 生产类为 `ModelCapabilityExecutor`，目录面断言并入本类） | OCR/ASR/图像理解经能力 ID |
| SUP-MODEL-02 | ai-engine · `CapabilityTaskLedgerTest`（原名 `CapabilityTaskServiceTest` 作废 -- 生产无同名类，台账写入在 `ModelCapabilityExecutor` 私有落账路径）+ V269 台账列（modelRef/version/latency/cost/trace） | 版本/延迟/成本/错误率可追踪 |
| SUP-MODEL-03 | ai-engine · `CapabilityConfidenceCalibrationTest`（原名 `CapabilityTaskServiceTest` 作废；quality.kind 未校准默认 calibrated=false，台账列同步落 0） | 未校准置信度不冒充概率 |
| SUP-FEEDBACK-01 | 各模块 · 四层指标复用既有可观测性（C.7 事件日志面 + §12.1 指标目录已定义）；端到端仪表/报告查询 live 记「未执行（需实跑+前端）」 | 数据/认知/场景/业务指标可查定义与来源 |
| SUP-GOV-01 | data-engine/ai-engine · fail-closed 分支（安全服务超时/无策略/无凭证 → DENY）复用既有 ArchUnit 安全卡 | 所有写操作 fail-closed |
| SUP-GOV-02 | ai-engine · `CapabilityTaskLedgerTest#asyncSubmitOnlyGoesThroughInjectedRuntimeTask` + `#submitWithoutRuntimeTaskFailsExplicitly`（**载体名订正**：原登 `CapabilityTaskServiceTest` 仓内零实存，SUP-MODEL-02/03 两行已订正但本行漏改，2026-10-11 实测补齐）+ 既有 NoDirectScheduler ArchTest | 无新增私有调度器/裸线程 |
| SUP-GOV-03 | `docs/30-设计/openapi/supplementary-cognitive-ai-v1.yaml` + 各 ApiPaths 单源（12 端点 operationId 齐） | 新增 API 均有 operationId 与契约校验 |

### 19.6 未决项（下沉后续批次，不冒充完成）

1. 小模型（OCR/ASR/TTS/图像理解）供应商接入：契约与台账在位，执行面未接真实模型 → 能力目录 `supported=false` 显式声明。
2. `ecos_cognitive` 既有 `ecos_decision*`（V103/V151）双镜像权威复核：沿用详设-05 R-13 ① 口径，本批次未重新裁定。
3. 年度经营预测首场景（§15.2 批次 B 全端到端 / 详设-09 接缝）：随 V267~V273 实跑授权 + 详设-09/10 在途批次收敛后执行。
4. 前端面（蓝图向导/候选评审/能力盘）：本批次不涉前端改动，随场景工作台 UI 批次落（F07 族后续）。
5. **非本设计文档遗留（真跑门禁暴露，2026-10-11）**：ai-engine `ToolExecutorService:89` 硬编码 `http://localhost:18090` 场景 base-url，属详设-06 F06-11（commit 4940048c，2026-10-09）存量，命中 F02-15 两态寻址单源门禁 FAIL。此前各批次均 `-DskipTests` 提交，该门禁从未实跑，故长期未被发现。本轮按门禁自身规定的路径登记豁免基线（data-engine `EndpointResolverUsageArchTest` + gateway `CrossEngineEndpointArchTest` 两份，条目均注明来源 commit 与归属批次），**未做实质收口**；收口 = 改走 runtime-access `ServiceEndpointResolver`，完成后须从两份基线同步删除。本设计文档批次不代他域还债。
6. **2026-10-11 首轮真跑抓出的历史缺陷清单**（此前均因 `-DskipTests` 提交而从未暴露；本轮已全部消化，除注明者）：

| # | 缺陷 | 归属批次 | 本轮处置 |
|---|---|---|---|
| 1 | ai-engine `ToolExecutorService:89` 硬编码场景 base-url，违反两态寻址单源 | 详设-06 F06-11（4940048c） | 登记两份豁免基线（见上条），实质收口另批（本文件 #161） |
| 2 | 认知域 `CognitiveBeliefService.toVo:288` 对可空 `Boolean` 直接拆箱 → 存量 NULL 行 NPE | 补充设计 v1.0 §8.3 首轮 | 改 `Boolean.TRUE.equals(...)`，随 cognitive 311 绿 |
| 3 | ai-engine 包分层违例 + `ArchitectureTest` 宇宙基线缺 `OutcomeMetricValueReader` | 补充设计 v1.0 §9 首轮 | allow-list 追加并注理由 |
| 4 | 认知域 DDL 白名单门禁 FAIL：V267 未登记 | 补充设计 v1.0 批次 A | 按 PRD-05 §6.1 规格 3 补登 |
| 5 | `ContextBuilderServiceTest` 5 项失败/错误（装配与生产 SPI 按 `sourceType` 路由 + `findFirst()` 模型不符；读失败夹具条件永不成立；400/503 用例预置后置步骤桩 → `UnnecessaryStubbing`） | 补充设计 v1.0 §5 首轮（从未实跑） | 改测试装配（face-based 单 reader + `failingFace` + 删必要前的桩位），14/14 绿；**未动生产语义与 API DTO** |
| 6 | `ScenarioBlueprintServiceTest` `InvalidUseOfMatchersException`：`verify(never())` 内裸值与 `anyString()` 混用 | 补充设计 v1.0 §6 首轮（从未实跑） | `never()` 面统一改 `any()`（`anyString()` 不匹配 null 实参会让「0 次写」断言**假绿**），116 绿 |
| 7 | `AgentProviderControllerTest` 夹具硬编码 LLM 供应商端点字面量，命中 F00-07 唯一出口门禁 | 补充设计 v1.0 §10 首轮 | 换假 host `https://provider-base-url.from-config/v1`（该值原从未被断言，用例强度不变） |
| 8 | 路由重写表条数基线漂移：测试/AGENTS.md 记 23（正向 10 + 反向 13），源码实为 **25**（反向 15） | 详设-10 R1-A（卷 10 §5.1/W252/R-43 的 `/api/wagent` + `/api/wagent/` 两条反向条目，`-DskipTests` 提交） | 按门禁「只严不松 + 登记来源批次」口径订正常量/文案并注归属；`route-rewrite-lint.ps1` 同步实跑为 `denominator=25 fail=6`（新增 2 条 FAIL-R4 = wagent 反向前缀尚无 Controller），AGENTS.md 基线文案同步 |
| 9 | `ModuleDocTruthfulnessTest` Controller 计数基线 33，实测 37 | 本批 +1（§10 `AiCapabilityController`）、详设-10 R2-A +3（`WAgentRun/Question/Goal`） | 计数经 `git ls-tree` 两时点独立实测（9e4833dd=36 / 工作树=37）后订正常量并注归属；ai impl AGENTS.md 无数字声明，无需改文档 |


---

**版本记录**：v1.0（编制 2026-10-09）· §19 实施回写首轮 2026-10-10 · **订正轮 2026-10-11** —— §19.2 DDL 区间订正为 V267~V274；§19.4 回写各模块 `mvn` 实跑 surefire 汇总与日志文件（本批次冻结测试类 127 用例全绿，gateway 门禁 126/0/0/4skip）；§19.5 载体名逐条核对为实存文件并订正 SUP-GOV-02；§19.6 新增第 6 条真跑抓出的历史缺陷清单（9 项，含 2 项另批收口）。首轮的「测试类已交付 = 记录为已运行」口径作废。
