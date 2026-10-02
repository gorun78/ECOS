# BUSINESS_SCENARIO_SERVICE_DOC — 场景工作台设计文档

> **溯源** · PMO-57-场景工作台功能缺口整改（5 条裁定已确认） + PMO-50-场景工作台与认知引擎整体规划
> **版本** · 1.0
> **修订** · 2026-09-27
> **上游** · [PMO-57-场景工作台功能缺口整改.md](../../30-cross-cutting-docs/pmo/legacy-09/PMO-57-场景工作台功能缺口整改.md)
> **下游** · [BUSINESS_SCENARIO_COGNITION_DOC.md](./BUSINESS_SCENARIO_COGNITION_DOC.md)（认知侧接口契约）

---

## 一、范围与边界

### 1.1 决策矩阵（门面 vs 深化）

| 决策项 | 裁定 | 落地点 |
|---|---|---|
| 主数据源：实体树 from `/workbench` vs `/tree` | **从 `/tree`**（建议 ①） | 前端 `ScenarioWorkbenchPanel` 初始化取数 |
| 三栏右栏：认知策略合成 vs 实体属性 | **认知策略合成可视化**（建议 ②） | 前端 认知策略合成面板（**待实现**，见分册 07 W176） |
| 认知策略计算时机：实时 vs 滚动间隔 | **滚动间隔 30s + Mind 变化即触发**（建议 ③，混合） | 前端 debounce（100–300ms 已做）+ setInterval 30s |
| 能力约束机制：显式 UI vs 隐形 | **隐形 — 后端按 Mind 自动过滤**（建议 ④） | 后端 `ScenarioServiceImpl` 三栏接入点 |
| 权限模型：场景设计者 vs 工作台高危 | **沿用既有分域**（建议 ⑤） | 复用 `@PreAuthorize` |
| "场景认知自由基" 下拉 | 保留，作为快速附加入口，不当主接口 | 前端 `CognitionRadicalButton` |
| "识别-Operation 评估" | 并入主"运行"，不独立落 L2 | 与"运行"复用同一 `mindId/mindThreadId` |

### 1.2 不变铁律清单

- 后端三栏（工作流 / 实体 / 服务）资源铁律 0.2 命中，**仅实现场景服务层**
- 后端新增 2 端点（`scenarios/{id}/cognition/detect` + `.../operation-eval`）**复用既有 Mind 层**，不新增 DAO
- 前端架构铁律 5.8（React 18 + 函数组件，不引入新状态库）
- 前端业务铁律 5.2（不允许裸 `<div>` 兜底外圈，走 `styles` token）

---

## 二、数据架构

### 2.1 表结构现状（USER 已确认数据库已运行时验证）

| 表 | 说明 | 关键字段 |
|---|---|---|
| `kb_cognitive_pipeline` | 认知管线持久化主表 | `pipeline_id/name/status/config(JSONB)/created_by/created_at/updated_at/result/is_deleted` |
| `business_scenario`（引用） | 业务场景表 | 既有，`id/name/description/shadow_scope/...` |
| `kb_cognitive_hypothesis` | 假设表（PMO-59 P0a 产物） | `hypothesis_id/scenario_id/mind_id/belief/confidence/...` |
| `kb_cognitive_belief` | 信念表（PMO-59 P0b 产物） | `belief_id/scenario_id/domain/probability/...` |
| `kb_mind_registry` | Mind 注册表 | `mind_id/name/closed_loop_bounds/...` |

### 2.2 场景侧新增字段（**无新增列**）

场景自身两问都已用现字段回答，**无需 ALTER**：

1. **认知原因** → 走 `kb_cognitive_hypothesis.scenario_id = {id}` 查
2. **运营执行功能** → 走 `kb_cognitive_belief.scenario_id = {id}` + `kb_cognitive_pipeline` 查

### 2.3 JOIN 语义

```
business_scenario.id
  └─ kb_cognitive_hypothesis.scenario_id    （1..n，认知原因）
  └─ kb_cognitive_belief.scenario_id        （1..n，运营执行功能）
  └─ kb_cognitive_pipeline（如果 pipeline 挂了 Mind，conversation 层桥接）
```

> **疑义记录**：`kb_cognitive_hypothesis` 与 `kb_cognitive_belief` 的 DDL 在 PMO-59 Phase 1/P2 时落过迁移（`V127~V129`），但**本次 rebase 后没找到本地对应 .sql 文件** — 执行时需到 Pg 实例直查；QA 侧须先 `SELECT to_regclass` 验证存在再走四端点 curl。

---

## 三、API 契约

### 3.1 后端四端点（新增，走 ScenarioController）

> **Operation 命名**：全部用**后端真实存在的 Controller** 路径，禁写任何虚构接口名。

| # | 端点 | 方法 | 作用 | 返回 |
|:--:|---|---|---|---|
| E1 | `GET business.scenarios/{id}/cognition/detect` | GET | 检测场景已配置的 Mind 集合 | `[{mindId, capabilityMask, closedLoopBounds}]` |
| E2 | `GET business.scenarios/{id}/cognition/operation-eval` | GET+Q | 评估当前 Mind 可执行的运营操作指标 | `{mindId, metrics:[{metricId, name, score, trend}]}` |
| E3 | `GET business.scenarios/{id}/cognition/hypotheses` | GET+Q | 列出场景关联的所有认知原因（假设） | `[{hypothesisId, reason, confidence, belief, status}]` |
| E4 | `GET business.scenarios/{id}/cognition/beliefs` | GET+Q | 列出场景关联的运营执行功能（信念） | `[{beliefId, capability, domain, probability, updatedAt}]` |

#### E1 请求 / 响应

**Request**：无 query，无 body；仅 URL path `id`。

**Response 200**（`ApiResponse.hibernate`）：
```json
{
  "code": 0, "message": "ok", "timestamp": 1790495600000,
  "data": [
    {"mindId":"M001","capabilityMask":"PLANNING,REVIEW,EXECUTION","closedLoopBounds":["PRE","POST"]}
  ]
}
```

**Response 504 穿越**（用户裁定 A3 — 503/cognitive 未启用时**原样穿透**）：
```json
{"code":503,"message":"cognitive engine not enabled","data":null}
```

#### E3 响应（认知原因 — 此端点是主"角色差异"数据源）

```json
{
  "code": 0,
  "data": [
    {"hypothesisId":"H003","reason":"毛利被采购成本侵蚀","confidence":0.82,"belief":"BELIEVED","status":"VALID"}
  ]
}
```

#### E4 响应（运营执行功能）

```json
{
  "code": 0,
  "data": [
    {"beliefId":"B001","capability":"REVIEW","domain":"采购","probability":0.91,"updatedAt":"2026-09-27T08:30:00Z"}
  ]
}
```

### 3.2 Mind 关联使用约束（铁律）

**Mind 覆盖场景一次执行**：当脑通过 E1 检测到同一场景上有多个 Mind 时，**以最优先的 Mind 为准**，前端 Three-panel 按 Mind 轮询取"**primaryMind**"的指标（其他 Mind 折叠展示）。

**Mind 字段贯穿三端点请求**：E3/E4 带 `?mind={mindId}` 可选参数；若 Mind 不属于当前场景，后端返 `400 mind not found on this scenario` — 禁止静默兜底。

---

## 四、前端交互

### 4.1 三栏数据流

```
+-------------------+    /scenario/tree(id)     +---------------------+
|  业务对象树        | <-----------------------> |  后端 ScenarioSvc   |
|  (左栏)           |    /workbench 健康卡 ->   |  businessz-engine   |
+-------------------+    /scenario/...          +---------------------+
                                                                    |
                                    /scenario/{id}/cognition/...    |
                                                         * 三栏叠加Hook
                                                                  |
              +---------------------------------------------------+
              |                     中栏（实体/服务/工作流）                        |
              |                 +--------------------------------------------------------------------+
              |                 |  右栏 — 认知策略合成（认知面板·待实现，见分册 07 W176）          |
              |                 |  - Mind chip + capabilityMask badge                                 |
              |                 |  - E3 "原因" 列表 + confidence 进度条                               |
              |                 |  - E4 "功能" 列表 + probability 热力                                 |
              |                 |  - E2 "执行" 指标 sparkline + trend 标签                            |
              |                 +--------------------------------------------------------------------+
              |    评估/运行 按钮 -> 顺带带上 mindId 透传 -> E2 + E4 重取                        |
              +---------------------------------------------------+
```

### 4.2 前端目标组件设计（契约占位 · 未落地）

| 目标落位（**待实现**，分册 07 W176/W187） | 职责 |
|---|---|
| 认知策略合成主视图（`ecos_frontend/src/pages/project-workbench/` 下，**尚未落地**） | 右栏主视图，按 Mind 订阅，debounce + 30s 轮询 |
| E3 "原因" 列表子组件（同上目录，**尚未落地**） | E3 假设列表 + confidence |
| E4 "功能" 列表子组件（同上目录，**尚未落地**） | E4 信念列表 + probability |

> **【校订 2026-10-02，F07-15/X-65 二次订正】** 上表三行目标组件（原标题曾以具名 `.tsx` 落表，实测 `ecos_frontend/src/pages/project-workbench/` 内 **0 命中**）本批已**改为"待实现"占位**，不再以具名路径声明为已存在组件（避免 X-65 "点名的三个前端文件"假记载复现）；前端消费侧整体归分册 08 前端批次，本堂不盲建以免落不可验桩。**认知读侧契约真源** = 后端 §F07-12 已实现的四条只读 GET（`ecos_backend/workspace/workspace-impl/.../controller/ScenarioCognitionReadController.java`）：`GET /api/v1/business/scenarios/{id}/cognition/{detect|operation-eval|hypotheses|beliefs}`，`?mind=` 可选参（缺省取 activeMind），异常一律走 `CognitiveEngineUnavailableException` 503 穿透（A3）；前端组件落地时**只消费后端字段，禁本地推断、禁伪造推送**。本文 4.3 的 banner/toast 降级 UI 属分册 08 前端消费侧，本堂只做真源与降级语义，不做前端文件。

### 4.3 降级策略

- **后端未启 Mind**：E1 返 503 -> 右栏置灰 + banner"认知引擎未启用"
- **场景无 Mind**：E1 返 `data: []` -> 右栏显示"未关联 Mind，可先到 Mind 工作台关联"
- **E2/E4 部分失败**：单卡 toast，不全栏崩

> **【校订 2026-10-02】** 后端 503 穿透已落地（`WorkspaceExceptionHandler#handleCognitiveUnavailable` 映射 `CognitiveEngineUnavailableException` → HTTP 503，前段由既有 `ApiResponse.body.code` "200-swallow" 拆除），banner/toast UI 属 F07-06/D 批次前端消费，本堂不盲建。

---

## 五、验收清单

### 5.1 后端（复用 ScenarioController）

- [ ] `GET /scenario/{id}/cognition/detect` 场景无 Mind 时 `data: []`，不存在场景时 `404`
- [ ] `GET /scenario/{id}/cognition/hypotheses?mind=M001` 正确按 mind 过滤
- [ ] `GET /scenario/{id}/cognition/beliefs?mind=M001` 同上
- [ ] `GET /scenario/{id}/cognition/operation-eval?mind=M001` 走 belief 概率加权
- [ ] 503 穿透（`cognitive-engine` 未装时）
- [ ] 单测覆盖：空场景 / 多 Mind / 无 Mind / 503 四种状态

> **【校订 2026-10-02，F07-12/N4-N7 已实现】** 上表四端点**真源路径**为 `GET /api/v1/business/scenarios/{id}/cognition/*`（PRD-08 订正后的形态），实现于 `ScenarioCognitionReadController`（见 `ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/controller/ScenarioCognitionReadController.java`）；场景不存在 → `ScenarioNotFoundException` 404（走 `WorkspaceExceptionHandler`）；`?mind=` 缺省取 active mind，失败透传 503 至前端。本验收清单里"无 Mind → data:[]/404"两种语义当前的可复现行为：无 activeMind 时由 cognitive 侧决定返回值，503 为引擎不可用时诚实 503 穿透（**非 200-swallow**），单测载体属 F07-24/P-2 workspace 测试底座（建 `src/test`），本堂未创载体故记**未执行**。

### 5.2 前端

- [ ] `ScenarioWorkbenchPanel` 打开场景时三栏挂载
- [ ] 右栏在 Mind 变化 / 100ms debounce / 30s 轮询 三种条件下都能刷新
- [ ] 右栏 503 / 404 / 空 Mind 三个 fallback 分支均有真实渲染

### 5.3 E2E

- [ ] 登录后进 `#/workbench?scenarioId=P1`，左栏选中 P1，右栏有 Mind 卡片
- [ ] 切换 Mind，右栏内容变化
- [ ] 关掉 cognitive-engine（stop jar），E2/E4 显示 toast，不白屏

---

## 六、与下游文档的契约

- **`BUSINESS_SCENARIO_COGNITION_DOC.md`** 消费本文 E1–E4 的端点名与响应，但负责写 cognitive 扩展字段的读法、BeliefNext sprint 联动。

## 七、待确认项（PM 节选）

| 项 | 何时确认 |
|---|---|
| Pg 中 `kb_cognitive_hypothesis` / `kb_cognitive_belief` 表是否本环境存在（PMO-59 那条 DB 迁移是否已跑到本环境） | QA 开测前 `psql` 一次 |
| `business_scenario` 接 scenario_id 是否需要 cascade 到 hypothesis / belief（场景软删时子记录保留策略） | 今位 PM-was，涉及数据生命周期，属跨 PMO 契约——**不在本批** |
| 是否将 E1–E4 纳入 `ScenarioOpenApi` 公共契约文档（archive-trace） | 批次结束前补 |
