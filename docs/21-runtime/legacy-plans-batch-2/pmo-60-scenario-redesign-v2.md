# PMO-60 v2.0 — 场景创建流程重规划（四维度：功能/界面 · 技术 · 数据 · 接口）

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../.trae/rules/架构铁律.md)（违反任一 = 验收不通过）
> 来源: 肖国荣 | 日期: 2026-09-21 | 优先级: P0 | 状态: **Planned（v2.0 冻结待审）**
> **本文件是 PMO-60 的 v2.0 重规划**，原方案 [pmo-60-scenario-creation-redesign-planned.md](./pmo-60-scenario-creation-redesign-planned.md) **保留不删**（v1.0~v1.4 为线性表单向导设计，本 v2.0 为沙盘可视化设计）
>
> **三条硬约束（本方案 tügel，违反须退回）**：
> 1. **遵循架构铁律** —— 引用 §0.1 微服务 / §0.3 六引擎 / §1.2 三滤波器 / §2.1 引擎只调 API / §2.4 安全接入 / §2.5 runtime 公共底座 / §3.1 PG / §4.1 主题 / §4.3 i18n / §4.6 组件 / §5.1 禁止清单
> 2. **认知引擎服务层并入 ai-engine 服务层（aiming :18084）** —— 见 §3.1【P0 前置债务】认知↔知识库 REST 化
> 3. **认知前端融入 AI 工作台（aiworkbench）+ 项目工作台场景定义改为沙盘可视化（React Flow v12）**

---

## §0 与 v1.x 的关系（避免读者混淆）

| 维度 | v1.x（保留） | **v2.0（本方案）** |
|---|---|---|
| 场景定义交互 | 7 步线性 checkbox 表单向导 | **一张 React Flow 沙盘画布**，拖放节点 = 拖放资源 |
| 资源绑定模型 | `target_ref` 字符串别名 → 升级 `target_id` 真 ID | 同（保留 v1.x §2.4 的 `target_id`/`target_type` 真 ID 化） |
| 心智层 | `ecos_scenario_mind`（1:N 多变体 + `active_mind` 哨兵） | **完全继承** v1.x §2.2（心智独立于场景定义，ADR-PMO60-1） |
| 认知 UI | `CognitionPanel` 挂在项目工作台 | **迁入 AI 工作台** 作 `cognition` Tab |
| 认知推理执行 | workspace 透传 cognitive:18089 | **统一走 aiming:18084**（cognitive REST 化后） |

> **保留继承**：v1.x 的六类真资源 ID 基线（§P0.1）、`ecos_scenario_mind` 1:N 心智模型、四件套端点（diagnose/forecast/simulate/policy）、心智三要素（evidence/hypothesis/belief）、`source_type` 解耦规则、策略非要素、企业投资决策全例（Q4）—— **v2.0 全部沿用**，只改「交互形态 + 进程归属 + 前端落位」三件事。

---

## §1 功能 · 界面维度

### 1.1 现状问题（v1.x 已诊断，此处继承）

[ScenarioEditor.tsx](../../ecos_frontend/src/pages/scenario/ScenarioEditor.tsx#L21-L27) 7 步线性向导，第 2~7 步全是同一个 `CbList` checkbox 勾选（数据来自 [data.ts](../../ecos_frontend/src/pages/project-workbench/data.ts) 的 26 个硬编码 mock 字符串别名）。`target_ref` 不指向任何 PG 真主键。

### 1.2 核心交互变革：沙盘可视化（沙盘 = Sandbox / War-Room Canvas）

**一张全屏 React Flow 画布，三大区 + 中央枢纽：**

```
┌───────────────────────────────────────────────────────────────────────┐
│  [场景元信息卡片]  name / goal / dept / priority / status / budget     │
├───────────────────────────────────────────────────────────────────────┤
│                                                                         │
│   ┌──────────────┐        ┌──────────────────────┐   ┌──────────────┐  │
│   │  资源供给区    │──────▶│  ★ 场景中央枢纽        │◀──│  交界输出区    │  │
│   │  Resource Hub │  连线 │  ScenarioHub (中心)    │   │   Insight   │  │
│   │              │       │  心智灯/四件套开关       │   │              │  │
│   │ ①数据源节点   │       │  belief 概率条          │   │ ⑤诊断卡       │  │
│   │ ②本体实体节点 │       │  激活心智 chip          │   │ ⑥预测卡       │  │
│   │ ③知识资产节点 │       └──────────────────────┘   │ ⑦推演卡(A/B)   │  │
│   │ ④Agent 节点   │         │   │   │              │ ⑧策略卡         │  │
│   │ ⑥安全策略节点 │         │   │   │              └────────────────┘  │
│   │ ⑦接口节点     │    ┌──────┘   │   └──────┐                          │
│   └──────────────┘    ▼          ▼          ▼                           │
│                   心智槽位区 MindGrid（1:N 变体卡片：base/optimistic/…）   │
└─────────────────────────────────────────────────────────────────────────┘
  操作：拖节点入画布=绑定资源 · 连线=声明引用 · 点节点=弹详情 Drawer · 画布=实时可选
```

| 区 | 类型 | 节点内容 | 对应 v1.x 的 step |
|---|:--:|---|---|
| **资源供给区** ResourceHub | 6 类 | 每类一个基础节点 + 可展开子节点（真 ID 列表） | 合并原 step 2~7 的 checkbox |
| **中央枢纽** ScenarioHub | 1 | 场景元信息 + 心智灯（belief 概率条 + 四件套开关）+ 激活心智 chip | 原 step 1 + v1.x step 4 心智档 |
| **心智槽位区** MindGrid | 1:N | 每个 mind 变体一张卡（label/active 标记/三要素摘要） | v1.x P3b T27 变体管理 |
| **交界输出区** Insight | 4 | 诊断/预测/推演/策略 四件套结果卡 | v1.x P3b T25/T26 运行态渲染 |

**沙盘 vs 表单向导的交互收益**：
- **空间感**：绑定关系可视化，一眼看出"哪类资源没绑"（v1.x 的 checkbox 看不出齐全度）
- **心智灯常驻**：belief 概率条、四件套开关在画布中央，不再是向导某一步的孤立输入
- **变体对比内嵌**：MindGrid 直接在画布上并列 optimistic/conservative 卡，对比推演（A/B）就地触发
- **运行态就地**：四件套结果卡直接挂在枢纽右侧，创建 ↔ 推理在同一画布闭环（符合"场景=可携带认知的决策单元"目标）

### 1.3 认知前端融入 AI 工作台

**现状**（侦察确认）：认知 UI 主战场在项目工作台 `scenario/CognitionPanel.tsx`；`aiworkbench/index.tsx` 有 6 个 Tab（[index.tsx:30](../../ecos_frontend/src/pages/aiworkbench/index.tsx#L30)），认知未接入。

**改造**：
1. [aiworkbench/index.tsx](../../ecos_frontend/src/pages/aiworkbench/index.tsx) 新增第 7 个 Tab `cognition`（`NAV_TABS` + `VIEW_TITLES` + view 渲染分支）
2. 新建 `aiworkbench/CognitionView.tsx`（**适配层**）：嵌入原 `CognitionPanel` 但剥离场景绑定，改为"场景选择器 + 认知执行"结构
3. i18n：`aiworkbench.tab.cognition` / `aiworkbench.viewTitle.cognition`（`i18n/locales/aiworkbench/{zh-CN,en}.json`）

> **职责边界**：项目工作台保留"场景**沙盘定义**"（建/改/绑/心智），AI 工作台 `cognition` Tab 承接"认知**执行**"（四件套运行态 + 介入）。定义在沙盘，执行在 AI 工作台，数据通过 `ecos_scenario_mind` + `GET /scenarios` 共享。

### 1.4 步骤/操作流（从 7 步向导 → 1 画 + 4 抽屉）

| 操作 | 入口 | 形态 |
|---|---|---|
| 定义场景元信息 | 中央枢纽卡片 | 点击展开 Drawer（原 step 1 表单字段） |
| 绑定 6 类资源 | 资源供给区节点 | 拖入画布 → 弹 Drawer 选真 ID（替换 checkbox） |
| 配置心智 | 场景激素区 + MindGrid | 点 belief 概率条/四件套开关；加/删变体 mind |
| 预校验 | 画布底部 "Run Pre-Validate" 按钮 | 6 类资源 + 心智 + 四件套可达性逐项 PASS/FAIL 徽标 |
| 保存 | 画布顶部 "Save" | DRAFT（预校验不全 PASS）/ ACTIVE（全 PASS） |
| 运行态推演 | AI 工作台 cognition Tab 或沙盘 Insight 区 | 四件套端点 |

---

## §2 数据维度

> **继承**：v1.x §2.4（binding `target_id`/`target_type`）、§2.5（`ecos_security_policy`/`ecos_interface_ref`）、cognitive 三表（ADR-9）、`ecos_cognitive_model`（PMO-51）全部不变。v2.0 只**新增 1 张沙盘布局表** + **迁移序号再编号**。

### 2.1 沙盘画布布局持久化（★ v2.0 唯一新增表）

| 列 | 类型 | 说明 |
|:--|---|---|
| `id` | BIGINT PK | identity |
| `scenario_id` | VARCHAR(64) NOT NULL UNIQUE | 1:1 场景（布局不随心智变体变） |
| `layout_jsonb` | JSONB | **React Flow `RFNode[]` + `Edge[]` 全量序列化**（节点 position/数据；边=资源→枢纽/枢纽→洞察引用） |
| `layout_version` | INT DEFAULT 1 | 每次保存 +1（乐观锁 + 审计） |
| 审计六列 | — | create/update time/by + is_deleted |

> **为什么独立表不进 `ecos_business_scenario`**：布局是 UI 表现态，高频变更（拖拽）单独落，避免污染业务主表；`UNIQUE scenario_id` 1:1（布局是场景属性，不是心智属性，**与心智 1:N 不同维度**）。

### 2.2 沙盘节点/边数据 Schema（前端 ↔ 后端契约，纯结构性）

```ts
// 沙盘节点类型（React Flow node.data）
type SdBNodeType =
  | 'resource'    // 6 类资源节点：data={category, targetId, targetName, children[]}
  | 'hub'         // 中央枢纽：data={scenarioId, status, activeMindId, beliefDelta, epOn}
  | 'mind'        // 心智变体卡：data={mindId, label, active, threeFactorBri, endpointBri}
  | 'insight';    // 四件套结果：data={ep: 'diagnose'|'forecast'|'simulate'|'policy', payloadHash, summary}

// 边类型
type SdEdge = {
  id: string;
  source: string;        // 上游 node id
  target: string;        // 下游 node id
  label?: string;        // 引用语义（如 "inherits from base"）
  kind: 'bind' | 'inherit' | 'feed' | 'insight';
};

// 全量序列化（layout_jsonb）
type SdLayout = { nodes: SdBNode[]; edges: SdEdge[]; viewport: { x: number; y: number; zoom: number } };
```

**节点 store + 真实意象 —— 摸着看，编码数据 + 真实意象**：

| 沙盘节点 | 数据语义 | 真实意象 |
|---|---|---|
| 数据源节点 | `target_id` → `td_datasource.datasource_id` | 数据库图标 + 连接状态点 |
| 本体实体节点 | `target_id` → `ecos_ontology_entity.code` | 实体图拓扑小缩略 |
| 知识节点 | `target_id` → `knowledge_article.id` | 条文 + reliability 环 |
| Agent 节点 | `target_id` → `sys_agent_profile.id` | 工具数 + 状态灯 |
| 安全策略节点 | `target_id` → `ecos_security_policy.id` | 锁图标 + domain 标签 |
| 接口节点 | `target_id` → `ecos_interface_ref.id` | 端点 + 时延 |
| 枢纽 | scenario + belief delta + 四件套开关 | 大脑 + 4 个小灯 |
| Mind 卡 | 三要素摘要 + active 标记 | 概率条 + 变体 label |
| Insight 卡 | 输出 hash + summary | 根因/预测/推演/策略 |

### 2.3 继承的表单状态（不变，仅重新布局）

v1.x §2.2 `ecos_scenario_mind`（1:N + `active_mind` + 3 个 partial/unique 索引）**原样保留**；v1.x §2.4 `ecos_scenario_binding`（`target_id`/`target_type`）**原样保留**。

### 2.4 迁移序号（★ v2.0 重新编号，避开已占用 124/126/127~129/130/131/132/133/134/135/136/138~145）

**⚠️ 纠正 v1.x 的 V130/V131 撞号**：实测 `gateway/db/migration/` 中 `V130__ecos_cognitive_run_invalidation.sql`（PMO-59 P3b）与 `V131__ecos_warn_log.sql`（PMO-59 P4a）**已占用**，`V134__kb_graph_ontology_traceability.sql`、`V135__ecos_entity_table_mapping_materialized.sql`、`V136__kb_extract_watermark_and_sync_report.sql` 也占用。v1.x 若按 V130/V131 落 `ecos_scenario_mind`/`binding 升级` 会与认知失效表名同 V 号冲突 → **v1.x 规划阶段尚未实施 DDL**，故 v2.0 在此统一改用 146 起未占区间。

| 迁移 | 归属 | 表 | 序号 |
|:--|---|---|:--|
| （v1.x T3·心智） | gateway | `ecos_scenario_mind` | **V146** |
| （v1.x T4·binding） | gateway | `ecos_scenario_binding` 升级（target_id/type） | **V147** |
| （v1.x T5a·安全） | sysman-boot | `ecos_security_policy` | **V148** |
| （v1.x T5b·接口） | sysman-boot | `ecos_interface_ref` | **V149** |
| **★ v2.0 沙盘布局** | gateway | `ecos_scenario_sandbox_layout` | **V150** |
| （v1.x P3b 决策回执） | gateway | `ecos_decision_record`（可选） | **V151** |

> **序号冲突防护**：v2.0 的认知休息方案（认知 REST 化）**无 DDL**（纯代码改造），不占序号。所有 migration 在 `gateway/db/migration/` 与 `sysman-boot/migration/` 下，实施前用 `ls` 列两目录 max 序号二次核对（防与并行批次撞 V146~V151），后端 `mvn install -DskipTests` 不破坏。

---

## §3 技术架构维度

### 3.1 【P0 前置债务】认知引擎服务层并入 aiming（:18084）

**侦察实测**（commit 基线）：
- `cognitive-engine-impl` 当前跑在 **`services/dccheng:18086`**，`DcchengServiceApplication` 的 `@ComponentScan` 扫描 `com.chinacreator.gzcm.engine.cognitive2` 包（2026 codebase memory 索引）
- **阻碍**：`cognitive-engine-impl` 与 `kb-engine-impl` 存在 **POM 级物理 Maven 耦合**（违反架构铁律 §2.1「引擎间只调 API 不调 Impl」）。若直接搬进 aiming，会把 kb 引擎一起拖进，破坏 dccheng 单一事实源
- `ai-engine-impl` 下有同名 controller 占 `/api/v1/cognitive/*`（如 CognitiveConfigController / ActionBridgeController），需 exclude 去重

**并入三步**（必须先做这一步，后续才能起 workspace→aiming 的 cognitive 调用）：

| 步骤 | 文件 | 操作 |
|:--:|---|---|
| A | `cross-service: cognitive-engine ↔ kb-engine` | 认知侧对 kb 依赖走 `GET /api/v1/knowledge/assets`（REST），**拆除 POM 物理依赖**（架构铁律 §2.1） |
| B | `services/aiming/pom.xml` | 追加 `cognitive-engine-impl` 依赖 |
| C | `aiming/AimingServiceApplication` | `@ComponentScan` 追加 `com.chinacreator.gzcm.engine.cognitive2`；`@MapperScan` 对齐 mapper；`excludeFilters` 挑一留一（ai 端 cognitive stub vs cognitive2 权威） |
| D | `services/dccheng/pom.xml` | **删除** `cognitive-engine-impl` 依赖（认知已迁出） |
| E | Gateway 路由 | `api-gateway` profile 新增 `cognitive-service → :18084`（`/api/v1/cognitive/**`, `/api/v1/engine/cognitive/**`, `/api/v1/world-model/**`）；`dccheng:18086` 保留 kb 路由 |
| F | 端口/health | cognitive 健康端点从 18089/18086 → **18084**；`GET /api/v1/engine/cognitive/health` 走 aiming |

> **验收门**：curl `:18084/api/v1/cognitive/beliefs` 返真数据（原 18086 端点 404/403 表示已迁出）；kb-engine 在 18086 仍只处理知识/图谱。

### 3.2 沙盘画布技术实现（前端）

| 项 | 选型 | 说明 |
|---|---|---|
| 画布引擎 | `@xyflow/react`（v12.11，已在 [package.json](../../ecos_frontend/package.json)） | React Flow v12，**零新依赖**（架构铁律 §5.1-10 不破 Docker 基线，仅前端依赖） |
| 参考范本 | [aiworkbench/LogicView.tsx](../../ecos_frontend/src/pages/aiworkbench/LogicView.tsx) | 仓库内已有 React Flow 工作流画布，节点/边/miniMap/拖拽可直接复用模式 |
| **纯自定义节点** | `SdResourceNode` / `SdHubNode` / `SdMindCard` / `SdInsightCard`（4 个自定义 `<Node>` 组件） | 不用默认 node，重写 with lucide-react icon + useTheme（前端铁律 §4.2 只允许 lucide） |
| 拖放 | `HTML5 drag & drop` + `dnd` 库（若未依赖则用 React Flow 内置 `useNodesDraggable`） | 资源节点从左侧 palette 拖入画布 = 绑定 |
| 状态 | React context + `zustand`（若已存在）或轻量 `useReducer` | 保存前 debounce 序列化 nodes/edges → `POST /scenarios/{id}/sandbox/layout` |
| 主题 | `useTheme().styles.*` token，**禁止硬编码 Tailwind 颜色**（前端铁律 §4.1） | 4 主题：slate-light/deep-space/cyber-terminal/royal-purple |
| i18n | `useLanguage().t('sandbox.*')`（前端铁律 §4.3），namespace `sandbox.*` | 节点标签/抽屉按钮/Tooltip 全 i18n |
| 文件结构 | `src/pages/scenario-sandbox/{SandboxCanvas.tsx, SdResourceNode.tsx, SdHubNode.tsx, SdMindCard.tsx, SdInsightCard.tsx, useSandbox.ts, api.ts, types.ts}`（主 < 300 行，子组件独立，前端铁律 §4.6） | 单文件 ≤ 800 行，每 Tab/节点独立文件 |

### 3.3 服务层归属改造后拓扑（合并后）

```
                       ┌────────────────────────────────────────┐
                       │   workspace-scene :18090                │
                       │   （场景 CRUD + binding + 心智 CRUD +   │
                       │     沙盘布局 + 预校验 + 四件套中转）      │
                       └─────┬─────────────────────────────┬────┘
            ① 认知执行        │                             │ ② 资源联查
            (读 mind 三要素→透传)│                            │ (真 ID 校验)
                       ▼                                          ▼
        ┌──────────────────────────┐            ┌─────────────────────────────────┐
        │  aiming :18084 (火 W)     │            │  四 service :18082/3/4/6         │
        │  ai-engine + 认知引擎(并入) │            │  datanet(数据源) buszhi(本体)     │
        │  /api/v1/cognitive/*      │            │  aiming(Agent) dccheng(知识)      │
        │  /api/v1/agent/*          │            │  + sysman:18081(安全/接口真表)   │
        └──────────────────────────┘            └─────────────────────────────────┘
                       │ (认知→KB 现在 REST)
                       ▼
        ┌──────────────────────────┐
        │  dccheng :18086 (水 K)    │
        │  kb-engine（图谱/检索/RAG）│
        └──────────────────────────┘
```

> **依赖方向合规**：workspace（服务层）→ 引擎 sub-agent，心智/布局表随 workspace 所在 PG（`sys_man`）or aiming PG（`sys_man`）双写 —— v2.0 规定 **`ecos_scenario_mind` / `ecos_scenario_sandbox_layout` / `ecos_scenario_binding` 三表归属 workspace 模块**（§0.3.1 workspace 作场景应用层），认知四件套端点在同进程 cognitive 包内，**跨进程 REST**（aiming:18084 ↔ workspace:18090 不是 in-JVM）。

### 3.4 新增 Controller / Service 归属（workspace-scene :18090）

| 类 | 职责 | 继承/新增 |
|---|---|---|
| `SandboxLayoutController` (`/api/v1/workspace/scenarios/{id}/sandbox/layout`) | 沙盘布局存/读（写 `ecos_scenario_sandbox_layout`） | 新增 |
| `ScenarioMindController` (`/scenarios/{id}/minds` CRUD) | 心智变体 1:N CRUD（继承 v1.x T24a） | 继承 |
| `ScenarioCognitiveController` (`/scenarios/{id}/cognitive/{ep}?mind=`) | 四件套跨服务透传（**目标 :18084**） | 继承 v1.x T25，**目标端口改 18084** |
| `ScenarioOptionsController` (`/scenarios/available/*` 6 联查) | 真 ID 选项池 | 继承 v1.x T8 |
| `ScenarioPreValidateService` | 预校验 + active_mind 唯一性 + 四件套可达 | 继承 v1.x T10 |

### 3.5 三滤波器（架构铁律 §1.2，新增沙盘/认知/心智端点逐层加）

**新增路径需同时过 4 层**：
1. `gateway/.../VersionPrefixRewriteFilter.java` → `V1_REWRITE_MAP` 确认 `/api/v1/workspace/scenarios/**` KEEP（不重写）
2. `sysman/.../SecurityConfig.java` → `permitAll /api/v1/workspace/scenarios/**` + `/api/v1/cognitive/**`（aiming 并入后，cognitive 端点路径不变）
3. `sysman/.../ClearanceInterceptor.java` → 豁免同类
4. 各 service `HeaderAuthInterceptor.java` → 从 gateway 头还原 X-ECOS- 用户上下文，缺头 → **默认 DENY**（铁律 §2.4-6）

### 3.6 运行态调用链（认知执行）

```
沙盘 Insight 区 / AI 工作台 cognition Tab
  → POST /api/v1/workspace/scenarios/{id}/cognitive/{diagnose|forecast|simulate|policy}?mind={mindId?}
  → workspace-scenarioService 读 ecos_scenario_mind（三要素 + cognitive_endpoints）
  → REST 中继 aiming:18084/api/v1/cognitive/*（认知引擎控制器已在 §3.1 并入）
  → 瞬态返回（ADR-9 不落盘）；策略输出可选落 ecos_decision_record
```

---

## §4 接口维度

> **继承 v1.x §2.7 端点（心智 CRUD + 四件套 + 6 联查 + pre-validate）全部不变**，仅追加 4 个沙盘布局端点（★ 新）+ 修正 4 个认知端点**目标端口 18089→18084**。

### 4.1 沙盘布局端点（★ v2.0 唯一新增契约）

| 方法 | 路径 | 请求/响应 | 验收 |
|---|---|---|---|
| GET | `/api/v1/workspace/scenarios/{id}/sandbox/layout` | → `{nodes[], edges[], viewport}, layout_version` | 未保存场景返默认布局（枢纽居中 + 6 资源节点分散）；已保存返真实 `layout_jsonb` |
| POST | `/api/v1/workspace/scenarios/{id}/sandbox/layout` | `{nodes[], edges[], viewport}, expected_version` → `layout_version+1` | 乐观锁：`expected_version` 不匹配返 409；成功返新版本；`nodes` 结构走 §2.2 Schema 校验 |
| GET | `/api/v1/workspace/scenarios/{id}/sandbox/preview` | → 仅 read-only 快照（不递增 version） | 供多端同步只读拉取 |

**校验规则**：
- 每个 `resource` 节点必带 `data.targetId` + `data.targetType`（与 `ecos_scenario_binding` 列对齐）
- `hub` 节点 1 个且 `data.scenarioId == {id}`（外键保护）
- 边 `kind=bind` 的 target 必须是 hub 或 mind 节点（资源→枢纽/心智）；`kind=feed` 的源是 mind，target 是 insight（表征"此 mind 触发了哪四个认知输出"）

### 4.2 继承端点（v1.x §2.7，目标端口修正）

| 方法 | 路径 | 目标 | 变化 |
|:--|---|---|:--|
| GET | `/scenarios/available/{datasets,objects,knowledge,agents,security,interfaces}` | 4 service + sysman:18081/18086 | 不变 |
| POST/GET/DELETE | `/scenarios/{id}/mind-model`（base） | workspace | 不变（兼容保留） |
| GET/POST/PATCH/DELETE | `/scenarios/{id}/minds` `/minds/{mindId}` | workspace | 继承 v1.x T24a |
| POST | `/scenarios/{id}/pre-validate` | workspace | 继承 v1.x T10；追加 **沙盘资源节点 coverage 检查**（节点数 ≥ 6 类非空） |
| POST | `/scenarios/{id}/cognitive/diagnose?mind=` | **aiming:18084** | 继承 v1.x，**端口从 18089→18084** |
| POST | `/scenarios/{id}/cognitive/forecast?mind=` | **aiming:18084** | 同上 |
| POST | `/scenarios/{id}/cognitive/simulate?mind={` | **aiming:18084** | 同上；`?mindA&mindB` 对比推演保留 |
| POST | `/scenarios/{id}/cognitive/policy?mind=` | **aiming:18084** | 同上；`action_plan[]` 可选项落 `ecos_decision_record` |

### 4.3 统一返回体

所有端点返 `ApiResponse`（业务错误码在 `code` 字段，HTTP 仍 200 —— 铁律 §5.4 curl 陷阱）。沙盘布局并发冲突返 `ApiResponse{code:409, msg:"layout_version_mismatch", currentVersion}`（非 HTTP 409）。

---

## §5 Task（v2.0 拆分：P0 前置 → P1 数据/契约 → P2 沙盘前端 → P2c 认知融入 AI 工作台 → P3 验收 → P4 审计）

| 阶段 | Task | 文件/路径 | 操作 | 验收 |
|:--:|:--:|---|---|---|
| **P0** | T0a | `engine/cognitive-engine/cognitive-engine-impl` 对 kb 依赖 | **REST 化**：`kb-engine` 调用改 `GET /api/v1/knowledge/assets`，拆除 POM `kb-engine-impl` 依赖（铁律 §2.1） | `mvn dependency:tree` 确认 cognitive 无 `kb-impl`；:18086 认知功能临时不可用，:18084 暂时不报 |
| **P0** | T0b | `services/aiming/pom.xml` + `AimingServiceApplication` | 追加 `cognitive-engine-impl` + `@ComponentScan engine.cognitive2` + `@MapperScan` + `excludeFilters` 去重 | aiming 独立起 → `GET :18084/api/v1/cognitive/beliefs` 返真数据 |
| **P0** | T0c | `services/dccheng/pom.xml` 删 cognitive-impl 依赖 | 认知已从 dccheng 抽离 | dccheng :18086 不再报认知 controller；kb 功能正常 |
| **P0** | T0d | `services/api-gateway/.../application.yml` | 新增 `cognitive-service → :18084`（`/api/v1/cognitive/**, /api/v1/engine/cognitive/**, /api/v1/world-model/**`） | gateway 转发生效 |
| **P1** | T1 | `docs/plans/api-contract.md` 相关段 + 本报告 | 契约冻结（§4） | 冻结 |
| **P1** | T2 | `gateway/src/main/resources/db/migration/V146__ecos_scenario_mind.sql` | 继承 v1.x（1:N + active_mind + 3 索引） | `uq_mind_active` 唯一性验证 |
| | T3 | `gateway/.../V147__ecos_scenario_binding_upgrade.sql` | 继承 v1.x（target_id/type） | 读取 backward compat |
| | T4a | `sysman-boot/.../V148__ecos_security_policy.sql` | 继承 v1.x | curl GET 列表 |
| | T4b | `sysman-boot/.../V149__ecos_interface_ref.sql` | 继承 v1.x | curl GET 列表 |
| | **★ T4c** | `gateway/.../V150__ecos_scenario_sandbox_layout.sql` | 新建 `ecos_scenario_sandbox_layout`（§2.1） | 1:1 UNIQUE 验证；默认布局可查可写 |
| | T5 | workspace `ScenarioOptionsController` | 6 联查（继承 v1.x T8） | 每类返真 ID 数组 |
| | T6 | workspace `ScenarioMindService` | 1:N CRUD（继承 v1.x T9） | 激活哨兵行为 |
| | **★ T6b** | workspace `SandboxLayoutService`（新建） | 沙盘布局存/读 + 乐观锁（§4.1） | 并发冲突 409 命中 |
| | T7 | workspace `ScenarioPreValidateService` | 继承 v1.x T10 + **追加沙盘资源节点 ≥ 6 类非空 coverage 检查** | 缺一类资源节点返 FAIL |
| | T8 | workspace `saveBindings` 改造 | 继承 v1.x T11（`target_id` 必填 + audit Kafka） | 审计事件落 Kafka |
| | T9 | 三滤波器新增路径（§3.5） | §3.5 逐层加 `/api/v1/workspace/scenarios/**` | gateway:8080 不需 JWT 打 200 |
| **P2** | T10 | `ecos_frontend/src/pages/scenario-sandbox/{SandboxCanvas,useSandbox,api,types}` + 4 自定义节点 | **★ v2.0 主体**：React Flow 沙盘画布（§3.2），替换 7 步向导；6 资源区域拖拽 | 节点拖入 → 拍可连的边 → 保存 → 刷新后布局一致；中英文切换 `t('sandbox.*')` 无硬编码 |
| | T11 | `SdResourceNode.tsx` | 6 类资源节点组件（lucide + useTheme，**零硬编码色/中文**，铁律 §4.1/§4.2/§4.3） | 主题/语言切换验证 |
| | T12 | `SdHubNode.tsx` | 中央枢纽节点（belief 概率条 + 四件套开关 + 激活 chip） | 中枢与各 mind/insight 连线 |
| | T13 | `SdMindCard.tsx`（MindGrid） | 1:N 变体卡片 + active 切换 + 加变体 Modal | 与 T6 后端 1:1 对齐 |
| | T14 | `SdInsightCard.tsx`（Insight） | 四件套结果卡（读决 T25 返回值） | 与 §4.2 四件套端点对齐 |
| | T15 | `ScenarioManagementView` 路由改造 | 原 7 步 `ScenarioEditor` 重定向到 `/project_workbench/sandbox/{id}` 沙盘；保留旧 URL 作为跳板 | redirect 逻辑测试 200 |
| | T16 | `ecos_frontend/src/api.ts` | 新增 `fetchSandboxLayout` / `saveSandboxLayout` / `listScenarioMinds` / `addMind` / `toggleMindActive` / `runSandboxCognitive(ep, mindId)` | tsc EXIT 0 |
| **P2c** | T17 | `aiworkbench/index.tsx` + `CognitionView.tsx`（新建） | AI 工作台新增第 7 个 Tab `cognition` + 场景选择器 + 四件套执行控件（§1.3） | 认知 Tab 可见；选中场景 → 可触发 `/workspace/scenarios/{id}/cognitive/{ep}`；i18n `aiworkbench.tab.cognition` 存在 |
| | T18 | `ecos_frontend/src/locales/scenario-sandbox/{zh-CN,en}.json` + `aiworkbench/*.json` | i18n namespace `sandbox.*` / `scenario.variant.*` / `scenario.diff.*` / `aiworkbench.cognition` | 中英对称；`vitest` 组件级切换断言 |
| | T19 | `scenario-sandbox/i18n.test.tsx`（vitest） | 沙盘 Tab `t()` 断言中英文切换 + 主题 `styles` 无硬编码回归 | 2 用例全 PASS；变硬编码↔测试必 FAIL |
| **P3** | T20 | `scenario-sandbox/SandboxE2E.test.tsx`（vitest + jsdom） | 选 6 类资源 + 设心智 + 保存沙盘 → `ecos_scenario_sandbox_layout` 1 行 + `mind` active=1 存在 | 200 |
| **P3** | T21 | 浏览器 E2E | 渲染 + console + network 三项无红 | ✓ |
| **P3b** | T22 | `workspace/.../ScenarioCognitiveController.java`（4 个认知端点，§4.2） | 目标 **aiming:18084**；`?mind` + `?mindA&mindB` | 四件套输出结构体齐；simulate 未启用 409 |
| | T23 | 前端 `CognitionView.tsx` 四件套渲染 + 决策回执 Preview | 卡片化 + 决策回执（`ecos_decision_record` V151） | 决策 `source_refs[]` 可回查 |
| **P4** | T24 | `docs/ARCHITECTURE-RULES.md` §3.3 | 补 "场景定义 = 沙盘画布；认知服务层 merged into aiming" 条款 | 文档同步 |
| | T25 | `ecos_frontend/src/pages/scenario/README.md` | 补充沙盘交互说明（含对比 v1.x 表单向导 changelog） | 可读 |
| | T26 | Reviewer × 6 专项 + `mvn install` + `vitest` 全绿 | 验收门 | `deliverable_allowed=true` |

---

## §6 验收四步法（铁律 §5.4）

| 步 | 内容 |
|:--:|---|
| V1 | 文件生存：`git diff --stat` 确认新增/改动文件（沙盘 8 新文件 + aiming component scan + workspace controller + 3 SQL） |
| V2 | 集成点 grep：`ComponentScan basePackages cognitive2` 命中；`ScopeLayoutService` 注入点命中；前端 `useSandbox` 引用命中；三滤波器 4 路径均命中 |
| V3 | 编译：后端 `mvn clean install -DskipTests`；前端 `npm run lint`（装 v2.0 作用域）+ `tsc` transform 检 |
| V4 | 浏览器 E2E：渲染 + console + network 截屏三项无红；沙盘 node 拖拽→save→刷新布局一致；中英文/4 主题全切换无硬编码泄漏 |

---

## §7 风险登记（v2.0 特）

| # | 风险 | 缓解 |
|:--:|---|---|
| 1 | **cognitive 并入 aiming 失败（kb 耦合拿不下来）** | T0a 拆 REST 先行；若认知 REST 化 wrapper 超时超 2 天，可回退任务：v2.0 认知端点仍指向 18086，DCCheng 保留 cognitive 依赖（不杀铁律，但标记"债务延后"） |
| 2 | React Flow 跨浏览器 default styles 与 4 主题冲突 | `useTheme().styles` token 显式增，避免 React Flow 内嵌 background/border 颜色可读 hardcode；若发现 React Flow 硬编码（如 mini map background）虑覆写 CSS |
| 3 | 沙盘 nodes/edges JSON 对象容体积（6 资源 × N 子 + 4 insight）可能对 DB 压力 | `layout_jsonb` = JSON (normal) 小于 100KB 前不予报监控；报警当 > 512KB |
| 4 | 内存：沙盘 React Flow `nodes` 大于 50 时同步摸底 | 节点限活 30 个以内；沙盘节点点击才展开 children（异步加载 `GET /available*/{id}`） |
| 5 | 跨 service cognitive REST timeout | RestTemplate 3s + caffeine cache（15s TTL）缓存认知健康；失败 **默认 DENY**（铁律 §2.4-6） |
| 6 | 代码仓库双方（aiming/dccheng cognitive）并排过高 require 代码平分 | T0a~T0c 单单序化，T0b listing cognitive-impl 依赖前 T0a 必须要项落盘；CI 目标是 cognitive-impl 对 kb 依赖 count = 0（架构铁律 §2.5） |

---
	
## §8 回滚

1. **DDL 回滚**：`DROP TABLE ecos_scenario_sandbox_layout, ecos_scenario_mind, ecos_security_policy, ecos_interface_ref, ecos_decision_record;` `ALTER TABLE ecos_scenario_binding DROP COLUMN target_id, DROP COLUMN target_type;`（V146~V151 每张独立回滚脚本，与 v1.x §回滚 4 项一致，仅序号更新）
2. **代码回滚**：沙盘模块 `scenario-sandbox/` 独立目录整 folder 回退，不脏 v1.x `scenarios/` 旧路由（两步双实存一方期，不杀可用性）
3. **aiming 认知回滚**：`AimingServiceApplication` 去掉 `engine.cognitive2` component scan + `pom.xml` 去依赖；dccheng 重新加 cognitive-impl 依赖（**认知双形态回滚**，不部分两边落风险）

---

## §9 豁免与建议加载规则

| 项 | 说明 |
|---|---|
| **豁免新增 Docker/Java 模块**：零新增 Maven 模块；cognitive-impl 仅限 super-update — pom 依赖变化不为新 module；Docker 零新增（与铁律 §5.1-10 v2.1 免一致） | compliant |
| **frontend 巨量硬编码 i18n key**：沙盘 `sandbox.*` 约 30 key，中英对称；改造的 AI 工作台 `aiworkbench.cognition.*` 约 8 key | 与前端铁律 §4.3 一致 |
| **React Flow 库能力边界**：road-map 需求可能触及 District（外接控件），当前需求不触及；若后续需 `dagre/network` 运算，择优写 `sandbox/nerfLayout.ts` 纯 linter（**不用第三方**） | 一期内容不算 |

---

## §变更日志

| 版本 | 日期 | 变更 | 原因 |
|:--:|:--:|---|---|
| **v2.0** | 2026-09-21 | **纯新写四维度方案文档**：§1 沙盘可视化 React Flow 画布交互（1 画 + 4 抽屉 + 认知融入 AI 工作台 cognition Tab）；§2 数据层新增 1 个 `ecos_scenario_sandbox_layout`（V135）+ 节点/边 schema（§2.2）；§3 技术架构「cognitive 并入 aiming:18084」三步前置债务（T0a~T0d）；§4 接口海拔 -4 沙盘布局 JSONB + 四件套目标 :18084；§5 Task 结构重梳（P0 前置 4 个 Task：T0a~T0d） | **用户三硬约束**：1）遵循架构铁律 · 2）cognitive 服务层并入 ai-engine · 3）认知前端 AI 工作台 + 项目工作台沙盘可视化 |

---
	
## §针对 PMO-72 并行批次的超调遵悉（与当前同分支并行工作协调）

待其他工作流读写 release/v2.1-alpha 时，**确认本文件 `pmo-60-scenario-redesign-v2.yaml` 属于自己的 change set**，不要与他人 commit 同提交；提交定名格式 `docs/pmo-60@v2 / feat(pmo-60) ...` 。实施进入 P1 在实际 changes plan 面中不同于 `pmo-60-scenario-creation-redesign-planned.md`。
