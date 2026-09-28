# BUSINESS_SCENARIO_COGNITION_DOC — 场景×认知耦合契约

> **溯源** · [BUSINESS_SCENARIO_SERVICE_DOC.md](./BUSINESS_SCENARIO_SERVICE_DOC.md)（主契约）
> **上游** · [CognitionPlannerController](file:///d:/workspace/javaprojects/ECOS/ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/controller/CognitivePlannerController.java) · `ecos_cognitive_hypothesis` / `ecos_cognitive_belief` / `ecos_cognitive_evidence`（V127–V129，PMO-59 P0）
> **修订** · 2026-09-27 (v1.1 — 真实表名对齐 PMO-59 P0 数据契约)

---

## 一、耦合定位

场景侧的四端点（E1–E4）从 cognitive 域**只读**取数。本文档把这条**单向读**的契约里 cognitive 承担的三件事说死：

1. **检测检测器要拿到的 Mind 身份**（E1 在 `kb_mind_registry` 投影）
2. **假设记录如何匹配场景**（E3 在 `kb_cognitive_hypothesis.scenario_id` 上）
3. **信念记录如何匹配场景 + Mind**（E4 在 `kb_cognitive_belief.scenario_id` + `mind_id`）

> **禁忌**：cognitive 域**不反向**写回 business 表，也不订阅场景 CRUD 事件。这是 ADR-P0 定案的推理结果不落盘 + 心智层不持久化场景元信息的延伸。

---

## 二、E1 detect 数据源

### 2.1 直接数据源

```sql
-- 场景下挂的 Mind 集合（绑定表名待 P5-0 查 DDL 后再敲，见 §八-1）
SELECT m.*, b.scenario_id
FROM {mind_registry_table} m
JOIN {scenario_mind_binding} b ON b.mind_id = m.mind_id
WHERE b.scenario_id = ${id} AND m.is_active
```

> **绑定表名候选**（provisional，需 T5 前 Pg 实测确认）：
> - `kb_scenario_mind_binding`（最贴命名）
> - `ecos_mind_binding`
> - 或 Mind 上直接 `scenario_id` 字段（无 binding 表）
> 三个候选**不进入本批 DDL**，要候着 Pg 直查做

### 2.2 capabilityMask 约定枚举

| 值 | 语义 | 来源 |
|---|---|---|
| `DETECT` | 可用 diagnose 类算子 | [DiagnosisController](file:///d:/workspace/javaprojects/ECOS/ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/controller/DiagnosisController.java) |
| `FORECAST` | 可用 forecast 算子 | [ForecastController](file:///d:/workspace/javaprojects/ECOS/ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/controller/ForecastController.java) |
| `SIMULATE` | 可用 counterfactual | [CausalReasonerService#simulate](file:///d:/workspace/javaprojects/ECOS/ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/service/CausalReasonerService.java) |
| `PLAN` | 可用 policy/planning | [CognitivePlannerController](file:///d:/workspace/javaprojects/ECOS/ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/controller/CognitivePlannerController.java) |
| `REVIEW` | 可用于事后评语 | 预留常量 |

### 2.3 Mind 未启用（503 穿透）

`kb_mind_registry` 挂了 Mind，但 cognitive 服务只有 stub 没装 LLM -> 后端按 A3 规则返 `503`，不走 200 空 body。

---

## 三、E3 hypotheses 数据源

### 3.1 匹配语义

- `WHERE scenario_id = {id} AND is_deleted = 0`
- 若带 `?mind=M001`：额外 `AND mind_id = M001`（ strict filter）
- 若不带 mind 参数：**默认不过滤**（返回全部场景关联假设）

### 3.2 状态机对齐

`kb_cognitive_hypothesis.status` 枚举（源自 PMO-59 Phase 1）：

| status | 中文 | 说明 |
|---|---|---|
| `PROPOSED` | 提议 | 刚提出未验证 |
| `EVIDENCED` | 有证据 | 关联 EvidenceRef 不为空 |
| `BELIEVED` | 被相信 | 被某 Mind 采信 |
| `REFUTED` | 已证伪 | 反向证据累计推翻 |

前端 E3 响应里的 `status` 字段必须与上表字符串一一对应；新枚举值必须先入此文档。

### 3.3 confidence 取值约定

`confidence` 在 `[0,1]` 区间，`[0,0.5)` 为低 / `[0.5,0.8)` 中 / `[0.8,1]` 高，前端三色色阶对齐（不早改后端）。

---

## 四、E4 beliefs + operation-eval 数据源

### 4.1 belief ↔ capability 投影

`kb_cognitive_belief` 表字段（从 PMO-59 Phase 2 记住）：`belief_id / scenario_id / domain / probability / strength / mind_id`。

**"运营执行功能"这个名字的来源**：E4 返回的 `capability` 字段就是 **belief.domain 映射到 E1 的 capabilityMask 之一**。映射规则来自 Mind 的 `capabilityMask`：

```
profile = mind.mind_registry.capability_mask
belief = belief 行
result.capability = profile ∩ belief.domain   // 取交集
```

**疑义**：`belief.domain` 是自由文本（例如"采购"/"客户"），而 `capabilityMask` 是能力枚举。二者语义并不同层 — 本批先用字符串相等做初版匹配 + QA 验证真实情况。**正式版本应引入 KB 维度 recode**（预留，不在本批）。

### 4.2 E2 operation-eval 语义

E2 不是"单点执行"，而是**给前端一个"这个 Mind 在这场景下能做什么 + 效能评分"的合成层**：

```
score(belief_i) = belief.probability * trend_weight(belief.domain, scenario)
trend_weight 当前固定取 1（本期不引入外部时序预测），后端预留接口
```

E2 与 E4 的区别：
- **E4** = 信念列表原始（按 `updatedAt desc`）
- **E2** = 经过**加权 Evaluate** 的指标列表（按 `score desc` Top-N，默认 N=10），附 `trend` 字段

---

## 五、Mind 失效的降级路径

| 触发 | 后端行为 |
|---|---|
| Mind 行存在但 `is_active=0` | E1 不返回该 Mind（前端显示"未启用"） |
| Mind 行不存在 | E1 对 `mind={id}` 返 `400 mind not found` |
| 场景被软删除 | E1–E4 全 `404 scenario deleted` |
| cognitive 引擎未启 | E1–E4 全 `503 cognitive not enabled` |

---

## 六、traceId 与日志

- 所有四端点均从 `X-Request-Id` 读，写 `traceId`，对应请求入库时的 `EVT=CognitiveAxB op=... agent={} scenario={} mind={}` 风格
- 前端 Header 通过 `apiFetch.ts` 的 `apiFetchData` 继承，**不改调用方**

---

## 七、验收产物

| 项 | 落点 | 对齐主文档 |
|---|---|---|
| `kb_*` 三表读权限确认 | 单测 P1 先断言 | 主 §5.3-C |
| `MindCapabilityMask` 枚举报表 | 枚举落 cognitive-engine-api 包内 | 主 §3.2 |
| E1–E4 四组集成用例 | `ScenarioCognitionIntegrationTest` | 主 §5.1 |
| 前端 fable 三卡片 mock 数据 | E2E 减真：`data:[]` 分支 | 主 §5.2 |

---

## 八、待确认项

1. **`belief.domain` 与 `capabilityMask` 语义同层性**（第三点已描述：本批字符串直等，正式版需知识库 recode）
2. **未来 Mind 漂移场景**（Mind 注册后 scope 扩展到场景变化）：本批不支持事件反写，按"每次请求都重新读 kb_mind_registry"的实现
3. **E2 trend_weight = 1** 的默认值是否能被配置（如走 `ecos.cognition.eval.trend_weight`）— 预留 Properties 入口，本期不实现
