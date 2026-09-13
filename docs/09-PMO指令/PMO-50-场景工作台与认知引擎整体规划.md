# PMO-50 ~ 53：场景工作台 × 认知引擎整体规划

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../../.trae/rules/架构铁律.md)
> 来源: 规划评审 + 用户决策 (2026-09-13)
> 日期: 2026-09-13
> 责任人: fullstack-implementer
> 铁律: workspace 经 REST 调 dccheng；cognitive 推理结果不落盘（模型资产除外，见 ADR-8）；API 只增不改

## §背景

场景工作台（workspace :18090）是横切全部 5 业务 service 的"整车"层；认知引擎（cognitive-engine，寄居 dccheng :18086）是"因果/预测/模拟/决策"的认知大脑。本批次完成两者闭环：

- **PMO-50** 场景实体化：Scenario 从内存 Map → PG 落库 + 强类型
- **PMO-51** 认知预测能力：新增 forecast（统计基线模型）+ 模型注册 + diagnose 弱数据降级
- **PMO-52** 场景运行编排：ScenarioRun 编排 4 类认知调用 + 指标回写 + 附：切 workspace→buszhi/ontology library 依赖
- **PMO-53** 前端场景工作台：后端真实 API + i18n + 认知诊断面板

## §用户决策（2026-09-13）

1. 模型注册表：**新增** `ecos_cognitive_model` 表（via ADR-8 修订"不新增 DB 表"红线口径：推理**结果**不落盘，模型**资产**可落盘）
2. workspace→buszhi/ontology library 依赖：顺带切断，转 common-api 接口 + 网关 REST
3. 预测 P0 只做统计基线模型（移动平均/线性外推 + KG 因子修正），不引第三方 ML
4. cognitive 的 `Scenario` 类文档层改称"仿真情景"（代码不动，注释/文档口径）

## §禁止清单

1. 不改既有 API 路径与签名（只增）
2. workspace 禁止 import `*-engine-impl` / `buszhi-impl`（PMO-52 附任务收口 G4）
3. cognitive 禁止引入规则引擎 / 第三方 ML 框架 / 直调 LLM
4. 不新增 Maven 模块 / Docker 容器
5. 前端禁硬编码颜色/中文，图标仅 lucide-react
6. 安全敏感数据（无本批次涉及）走 security-engine

## §Task 总览

### PMO-50 场景实体化

| Task | 文件/路径 | 操作 | 验收 |
|:--:|------|------|------|
| T1 | `gateway/.../db/migration/V123__ecos_business_scenarios.sql` | 新增 `ecos_business_scenario` + `ecos_scenario_binding`（含审计列/索引/seed 3 场景，id 列必须 VARCHAR(64)） | psql 两表可查且有 3 行 |
| T2 | `workspace-impl/.../scenario/{entity,repository,service,dto}` | MyBatis Mapper + ScenarioService（参数校验+异常捕获） | 单测 CRUD 全绿 |
| T3 | `workspace-impl/.../controller/ScenarioController.java` | 重写为强类型（ScenarioVO/ScenarioSaveDTO，Lombok @Data），保留原 6 端点路径 | curl CRUD 全绿，重启不丢数据 |

**路径**：`/api/v1/workspace/scenarios`（三滤波器已放行：SecurityConfig L157 + ClearanceInterceptor L106，无需新登记）

### PMO-51 认知预测能力

| Task | 文件/路径 | 操作 | 验收 |
|:--:|------|------|------|
| T1 | `cognitive-engine-api/.../model/{ForecastRequest,ForecastPoint,ForecastResult}.java` + `ForecastService` 接口 | 预测契约（只增） | 单测编译绿 |
| T2 | `cognitive-engine-impl/.../service/ForecastServiceImpl.java` | 移动平均 + 线性外推，KG 因子修正，输入历史序列，输出 ≥3 点 + 置信区间 | `POST /api/v1/cognitive/forecast` curl 通 |
| T3 | `gateway/.../db/migration/V124__ecos_cognitive_model.sql` + `ModelRegistryService` + `ForecastController` | 模型注册表（ADR-8） + forecast 端点 | curl 注册+预测通 |
| T4 | `CausalReasonerServiceImpl` 降级路径 | KG 无指标时 fallback 规则+RAG 低置信度结果（不再 metricFound=false 硬 404），保留 metricFound 字段兼容 | 单测：弱数据 case 出低置信度结果 |

**路径**：`/api/v1/cognitive/forecast`（`/api/v1/cognitive/**` 三滤波器已放行）

### PMO-52 场景运行编排

| Task | 文件/路径 | 操作 | 验收 |
|:--:|------|------|------|
| T1 | `gateway/.../db/migration/V125__ecos_scenario_run.sql` | `ecos_scenario_run` 表（run 记录：4 类认知结果 JSON 引用） | psql 可查 |
| T2 | `workspace-impl/.../scenario/{ScenarioRunService,ScenarioRunController}` + `DcchengClient`（RestTemplate 封装 dccheng :18086） | 编排 diagnose/forecast/simulate/strategy 4 类调用，经 gateway 内网直连；结论回写 `ecos_business_scenario.actual_safety_index` 等 | curl 发起 run → 4 类结果齐 |
| T3 | 附（决策 2）：新增 `common-api/.../common/engine/{FunctionEvaluator,ActionHookExecutor} 接口`；buszhi/ontology-impl 提供实现 Bean；workspace 两 Controller 改注接口并删 2 个 import；`workspace-impl/pom.xml` 删 `buszhi-impl` + `ontology-engine-impl` 依赖；`WorkspaceServiceApplication` 删 buszhi/ontology engine 扫描包 | 断 G4 依赖链，维持可启动 | 全仓 `grep workspace-impl` 0 命中 buszhi/ontology engine import；mvn install 绿 |

**路径**：`/api/v1/workspace/scenarios/{id}/runs`（`/api/v1/workspace/**` 已放行）

### PMO-53 前端场景工作台

| Task | 文件/路径 | 操作 | 验收 |
|:--:|------|------|------|
| T1 | `ecos_frontend/src/pages/ScenarioManagementView.tsx` | 切真实 API（`/api/v1/workspace/scenarios`），删 initialScenarios/localStorage mock | 无 mock 残留，数据来自后端 |
| T2 | `src/i18n/locales/scenario/{zh-CN.json,en.json}` + LanguageContext 注册 namespace | 清 `tl()` 内联双语，全量 `t("scenario.*")` | 中英文切换 E2E |
| T3 | `pages/scenario/CognitionPanel.tsx`（新 Tab） | 发起 ScenarioRun → 渲染 ReasoningPath（step 可高亮，RuleRef/PrecedentRef 可点击）+ 预测图表 | 浏览器 E2E：渲染无 ErrorBoundary、console 无 error、network 无意外 4xx |

## §验证四步法

- V1 文件生存 / V2 集成点 grep（三滤波器 + 依赖方向）/ V3 后端 `mvn install -DskipTests` + 前端 `npm run lint` / V4 gateway 启动 curl；涉及页面追加浏览器 E2E
- 双跑兼容：gateway 单体 (:8080) 与 workspace 独立 (:18090) 均验证

## §ADR

- **ADR-8**：cognitive 边界口径修订 —— "不新增 DB 表"修订为"推理**结果**不落盘；模型**资产**（注册/版本/回测）可落 `ecos_cognitive_model`"。同步修订 `ecos_backend/engine/cognitive-engine/AGENTS.md` 与 impl AGENTS.md 红线 #2 表述
