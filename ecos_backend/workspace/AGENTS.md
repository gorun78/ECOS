# workspace 场景层 — AGENTS

> 属主：详细设计-07（场景层 workspace 场景工作台）。本文件由 F07-25 建立，描述 `ecos_backend/workspace/` 的真实边界，禁复制上层 `ecos_backend/AGENTS.md` 的过时虚构表述。

## 1. 模块定位

`workspace` 是**场景工作台的业务聚合层**（端口 18090），把数据层 / 本体层 / 知识层 / 智能层的资源装配成「可编排、可演练、可闭环」的业务场景。它**不是**任何其它域的封装壳：

- **不拥有** Twin / Alert / Task / EngineTask / EcosKnowledgeGraph 控制器（旧文档「P3-C 迁入」表述系虚构）。
- 对其它 service 的调用**只有一处聚合探测**：`ScenarioOptionsController`（探测可用绑定候选项）。跨 service 一律走 REST（见根 `ecos_backend/AGENTS.md` 依赖铁律 2），Controller 禁止直用 JdbcTemplate（铁律 5）。

## 2. 真实控制器面（本层）

场景域 REST 控制器（约 24 个类、场景域端点量级以本册 D-1「既有 32 条 + 新增 12 条」为准）：

| 分组 | 控制器 | 契约出处 |
|---|---|---|
| 场景 CRUD / 状态 | `ScenarioController`、`ScenarioStatusController`、`ScenarioPreValidateController` | D-1 A 系列、N12 |
| 完整度 / 目录 / 口径 | `ScenarioCompletenessController`、`BindingCatalogController`、`ForecastScopeController` | D-2 N1/N2/N3 |
| 认知 E1~E4 | `ScenarioCognitionV2Controller`、`ScenarioCognitiveController`、`ScenarioMindsController` | D-2 N4~N7（GET 只读；旧 POST 保留为 Deprecation 别名） |
| 动作 / 决策 | `ScenarioActionController`、`ObjectActionController` | D-2 N8~N11 |
| 运行 / 沙箱 / 绑定 | `ScenarioRunController`、`ScenarioSandboxController`、`ScenarioBindingLinkController` | D-1 / 05 册 |
| 对象 / ObjectQL / 图 | `ObjectController`、`ObjectQLController`、`ObjectRelationshipController`、`ObjectStateMachineController`、`ObjectTimelineController`、`QueryHistoryController` | 03 册接缝 |
| 探测 / 聚合 | `ScenarioOptionsController`（**唯一跨 service 聚合探测点**） | D-3 三滤波器 |

> 精确端点数以路由 mapping 表实测为准（`OpenApiPathParityTest` 与 `docs/40-实现/SCENARIO_OPENAPI.md` 对齐）；本文件不写死会漂移的总数。
>
> **2026-10-15 #140 子批 A 订正注**：`OpenApiPathParityTest` 已实装于 `gateway/src/test/java/com/chinacreator/gzcm/gateway/`（差距分析曾判其为悬空引用）。落地形态=**静态扫描 workspace main 源码映射注解**，非 Spring `RequestMappingHandlerMapping` 反射；三向对账（详设-07 D-2 12 行 ≡ 契约片段 12 条 ≡ 6 属主类 12 映射）+ 详设 line 529 具名用例 `#everyWorkspaceMappingHasAnOperationId`，2026-10-15 实跑 6/6 绿。反射档/可达性档仍属真容器 IT，本类不冒充（D-3 禁静态门禁宣称可达性）。

## 3. 认知四端点（E1~E4）契约纪律

- 路径：`GET /api/v1/business/scenarios/{id}/cognition/{detect|operation-eval|hypotheses|beliefs}`。
- **状态码真值化**：引擎禁用 / schema 未就绪 = **503**，原样穿透，禁包成 HTTP 200（历史 X-16/X-17 违规，登记 C 系列修复）。
- 后端不合成结论；`detect` 的 mind 不属于场景 = 400，场景不存在 = 404。
- 前端唯一消费通道 = `hooks/useCognition.ts` → `services/cognitiveClient.ts`；503 → 横幅 + traceId，**绝不本地伪造 verdict/answer**（F07-12/F07-22）。

## 4. 依赖方向（不可逆）

`workspace → services → engine → common`（单向）。`workspace` 仅依赖 `api` 模块中的 DTO/接口，禁直连其它域 DAO。

## 5. 前端对偶约定（勿在后端文档臆造）

前端 Git UI 一律走 `services/gitService.ts` 单一通道，只传 `repositoryId`；组件 ≤800 行；颜色用 `useTheme` 语义 token；文案用 `t('ns.key')`；图标仅 `lucide-react`；移动适配用 `useMediaQuery`。详见 `ecos_frontend/AGENTS.md`。

## 6. 待办接缝（D-5，属主在他册）

- `policy evaluate` 载荷（01 册）、DQ 门禁 BLOCK 判定（02 册）、对象/映射契约探测（03 册）、知识抽取引用（04 册）、`DecisionService`（05/06 册）、端到端闭环（09 册）。本层仅提供底座，不认领这些验收。
