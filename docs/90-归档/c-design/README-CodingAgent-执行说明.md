# ECOS 场景工作台与认知引擎：Coding Agent 执行包

> 文档版本：v1.0  
> 编制日期：2026-10-29  
> 目标：将场景工作台（workspace）与认知引擎（cognitive-engine）的目标设计转为可分阶段实施、可自动验证的工程任务。  
> 状态：实施基线草案；Coding Agent 必须先完成仓库现状核验，不能把本文件视为现有代码事实。

## 1. 交付物

1. `详细设计-场景工作台.md`：场景工作台的职责、蓝图模型、生命周期、完整度、API、UI、沙盘隔离、测试验收。
2. `详细设计-认知引擎.md`：心智/心智模型、认知资产、Context Builder、框架注册、策略选择、执行契约、E1-E4 既有认知端点接缝、测试验收。
3. `实施任务与验收清单.md`：Coding Agent 的执行顺序、分阶段任务、禁止事项、DoD 与端到端验收。

## 2. 必须继承的 ECOS 架构基线

- DIKCW 主链：`D → I → K → C → W`；运行反馈为 `W → D`，反馈也可触发对 I/K/C 的复核。
- `workspace` 是 Scenario Blueprint 的权威生命周期所有者：目标、资产依赖、缺口、完整度、测试、审批、发布状态、运行引用与反馈关联。
- `cognitive-engine` 是 C 层认知应用能力的归属；Context Builder 必须是其内部应用服务/接口，不新建服务。
- `ai-engine` 承担 W Agent/Agent Runtime 的目标理解、计划编排、工具调用、预算、暂停和重规划。Hermes 仅作为受约束的 Agent Runtime 参考/宿主，不得创建第二套 Runtime。
- `security-engine` 是权限、密级、自动化等级、审批和策略的唯一权威裁决方。安全依赖不可用时写操作默认拒绝（fail-closed）。
- `llm-gateway` 是 LLM 与 Embedding 的唯一出口。OCR、ASR、图像理解、分类、抽取、重排等小模型应通过既有能力注册/路由机制，不允许引擎直连供应商。
- 长任务统一走 `runtime-task`；前端统一使用 `AsyncTaskCenterView`/既有 task-center 服务，不自建调度器、裸线程、私有轮询体系。
- 既有 API 只增不改；新增端点必须先查 OpenAPI/API 前置件（P-5）和当前控制器映射。若现有端点已经承担同一语义，应扩展兼容契约，不得重复造接口。
- 不新增 Maven 模块、Docker 容器、第二套 Agent Runtime、独立规则引擎或平行资产目录。
- 不新增 `cognitive_context` 持久化表，不把每次临时推理结果作为业务事实写库。请求态 Context Pack 可在内存/受控缓存中存在；审计与运行追踪使用既有事件/审计机制。
- 不新增认知域业务事实表；受治理的 C 类资产如 Hypothesis/Belief/Evidence 的持久化，只能复用当前已批准的物理载体、ADR 和生命周期，不得另建同义表。已知物理名线索 `public.ecos_cognitive_hypothesis`、`public.ecos_cognitive_belief`、`public.ecos_cognitive_evidence` 必须通过代码/迁移实测确认后再使用，不能凭本文直接执行 DDL。
- 知识库保存可复用的 K 类知识资产；C 层保存/引用认知状态与受治理认知资产。不得把 K 类资产复制到 C 层，也不得把单次运行上下文自动收编成知识。
- 财务金额、正式业务指标、预测运行结果只能来自权威数据或确定性业务计算。LLM 只能给解释、候选假设、结构化建议；不能生成/替代权威金额。
- 前端不自算后端权威完整度、财务结果、权限判定、预测区间或证据可信度。

## 3. Coding Agent 开始前必须执行的仓库核验

### 3.1 先读工程治理文件

按仓库实际位置读取：
- `AGENTS.md`、`.trae/rules/` 或项目同类 Agent/架构规则；
- `docs/10-架构/` 中架构说明、架构铁律、ADR、模块清单；
- `docs/20-需求/` 中 PRD-00、PRD-05、PRD-06、PRD-08、PRD-09、PRD-10；
- `docs/30-设计/` 或实际位置中的详细设计-00、-01、-05、-06、-07、-08、-09、-10；
- 当前在途功能目录 `features/20260917-scenario-workbench/`（若仓库存在）；
- 数据库迁移、OpenAPI、测试工程与 CI 配置。

### 3.2 记录基线

开始改代码前输出 `implementation-audit.md`，至少记录：
- Git 分支、工作区是否干净；不得覆盖用户未提交更改；
- workspace、cognitive-engine、ai-engine、security-engine、runtime-task 的实际模块路径；
- 已存在的 Controller/API/DTO/Service/Repository；
- 已存在的场景实体、绑定实体、连边实体、状态字段、迁移版本；
- 认知 E1-E4 端点真实路径和当前响应包络；
- Hypothesis/Belief/Evidence/Cognitive Model 的实际物理载体与所有权；
- `ApiResponse`、错误码、traceId、认证头、分页、幂等、异步任务的真实规范；
- 前端工作台路由、页面、组件、API client、i18n、主题、图标和 task-center 现状；
- 当前可运行测试命令及基线结果。
对每项标记 `EXISTS / PARTIAL / MISSING / CONFLICT / NOT_VERIFIED`，并给出源码路径和证据。

### 3.3 冲突处理

当本文与源码/已批准 ADR/架构铁律冲突时：
1. 不静默选择；
2. 记录冲突、影响范围和最小兼容方案；
3. 保持 API/DB 向后兼容；
4. 仅在无破坏性选择时继续；涉及表结构、写权威、状态迁移或安全策略的冲突，先停止该子任务并报告。
禁止仅凭文档或测试名称宣称功能已实现；必须实际运行测试并记录结果。

## 4. 执行方式

- 采用小步提交；每个阶段先改契约/测试，再实现，再运行验证。
- 不一次性重写工作台或认知引擎。
- 先实现最小纵向闭环：场景蓝图读取/编辑 → 完整度计算 → Context Pack 构建 → 认知端点/受控框架执行 → 验证结果 → 场景工作台展示 → 运行 trace 可追溯。
- 若依赖服务不可用，UI 必须明确显示不可用/降级，不能用静态 mock 伪装成功。
- 每次执行都要报告：变更文件、实现点、测试命令、真实结果、未完成项、风险和下一步。

## 5. 禁止事项

- 不执行 `DROP TABLE`、删除迁移、重写历史迁移或破坏性数据清理。
- 不修改既有 API 的路径、字段语义或响应结构；只能向后兼容地扩展。
- 不在前端复制完整度算法、财务计算、策略判定或权限逻辑。
- 不绕过 `security-engine`/ToolExecutor，不因 LLM 建议而直接发布本体、修改数据映射、写业务事实或自动更新生产知识/模型。
- 不把演练产物用于正式运行，不允许 sandbox 数据污染正式业务数据。
- 不在认知引擎内直连模型供应商、业务数据库、图数据库或向量数据库驱动；通过既有引擎接口/受控工具访问。
- 不新建 `cognitive_context`、第二套任务中心、私有 Agent loop 或私有调度器。
