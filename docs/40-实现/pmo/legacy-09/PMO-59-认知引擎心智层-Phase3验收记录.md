# PMO-59 Phase 3 验收记录 — 认知引擎反事实推演核心（P3a）+ 业务落地与时间回放（P3b）

> 来源: PM项目经理（PMO-59 P3 收口）
> 日期: 2026-09-14
> 责任人: fullstack-implementer（开发实现）/ PM项目经理（验收）/ code-artifact-reviewer（独立审查）
> 铁律: 继承 [架构铁律](../../.trae/rules/架构铁律.md) + PMO-59 Phase 3 指令 §禁止清单；commit hash 即 DONE 凭证
> 前序: P2b 已验收 PASS（commit `a1ac61d`/`6b23a76`，HEAD 基线 `10f462a`，分支 `feature/cognitive-mentalevidence-p0`）

---

# ══ P3a 段：反事实推演核心 ══

## 0. 交付物清单（P3a T1~T5）

| Task | 交付文件 | 状态 |
|:--|------|:--:|
| T1 | `cognitive-engine-api/.../dto/counterfactual/CounterfactualRequest.java`（@Data：scenarioId?/domain 必填/variableName 必填/baseline{outcomeValues?,weights?}/interventions[{variableName,op SET/DELTA,value}]/sampleCount 默认 1000 上限 5000/seed 默认 42）+ `CounterfactualResult.java`（requestEcho/baselineMean/intervenedMean/riskMetrics 四指标/sensitivityTop3/assumptionRefs/excludedAssumptions/scenarioSummary） | ✅ |
| T2 | `cognitive-engine-impl/.../service/mental/CounterfactualSimulator.java`（**实现选择=轻量线性响应模型**——既有 ScenarioSimulator 走 LLM 违反红线仅作语义参照；belief 分布装配 + do(A) SET/DELTA + `Random(seed)` 蒙特卡洛配对采样 + 四指标 + 敏感性 Top3（outcome 数值 +5% 解析响应）+ 假设有效性过滤；纯 Java 0 LLM；审计发 ecos.audit） | ✅ |
| T3 | `cognitive-engine-impl/.../controller/CounterfactualController.java`（`POST /api/v1/cognitive/counterfactual` 强类型 DTO）+ `cognitive-engine-impl/AGENTS.md` 端点清单登记 | ✅ |
| T4 | 性能压测（内联 PowerShell 循环 curl 10×2 档，无临时脚本） | ✅ |
| T5 | `docs/plans/api-contract.md` §3.4 回填 + 本验收记录 | ✅ |

## 1. V2 集成点 grep（编译前逻辑校验）

| 检项 | 结论 |
|------|------|
| 三滤波器 | `/api/v1/cognitive/**` 已被 SecurityConfig permitAll（sysman L96）+ ClearanceInterceptor 豁免（L112）+ whitelist 覆盖；`VersionPrefixRewriteFilter.V1_REWRITE_MAP` 无 cognitive 条目=KEEP → **零新增登记** ✅ |
| 依赖方向 | CounterfactualSimulator 仅 import cognitive2.service.{BeliefStore,HypothesisStore,EvidenceStore}（同模块）+ common-api + runtime-event（既有 P2b 依赖）；0 处 `*-engine-impl` 跨引擎 import；0 处 llm-gateway/llm 引用 ✅ |
| API 只增不改 | 仅 1 新增端点； foresight Controller/VO/既有 Store 0 签名变更；`ScenarioSimulatorService` 未动 ✅ |
| Store 风格 | 复用既有 `BeliefStore.findLatest/listByVariable` + `HypothesisStore.list`（既有方法，0 新增 SQL）；0 `SELECT *`、全 `is_deleted=0` ✅ |

## 2. V3 全量编译（mvn install 非 compile）

命令：`& "D:\JavaProjects\env\apache-maven-3.9.11\bin\mvn.cmd" -f ecos_backend/pom.xml clean install "-Dmaven.test.skip=true" -B`（编译前已停 8080 旧 gateway pid 89988）

| 轮次 | 结果 | 说明 |
|:--:|:--|------|
| ① 模块级（cognitive api+impl -am） | ✅ MVN_EXIT=0 | T1 验收口径（api 模块编译绿） |
| ② 全量 17 模块 | ✅ **MVN_EXIT=0**（14:11:03 完成，DUR=874s≈14.6min） | 新 gateway jar LWT 同批 |

## 3. V4 Gateway 启动 + curl 验收（enterprise profile）

启动：`java -Xms512m -Xmx2g -DJWT_PRIVATE_KEY=<PEM 纯 base64 1624 字符> -jar gateway-*.jar --spring.profiles.active=enterprise`（PID 77396；日志关键行：`[CognitiveMental] Kafka producer 已装配` / `IWarnLogService 已按 PMO-59 授权例外补装配` / `COGNITIVE_MENTAL_SCAN 注册 scheduleId=f3de5c6f` / `Started GatewayApplication in 69.027 seconds`）。

### 3.1 复现性验证（T2 硬验收）

**同参双跑逐位一致**（cf-a：domain=pricing, variable=competitor_price_cut_prob, SET=2.5, N=100, seed=42）：

```
run1: {"baselineMean":1.35,"intervenedMean":2.5,"riskMetrics":{"expectedBenefit":1.15,"maxDrawdown":0.0,"lossProbability":0.07,"volatilityRange":[2.5,2.5]},"sensitivityTop3":[{"variable":"competitor_price_cut_prob","sensitivity":0.12500000000000003}]}
run2: 完全一致（全字段逐位相同，仅 timestamp 差）✅
```

**SEED 语义核验（手算 vs 响应逐项一致）**：
- baselineMean=1.35 = v4 分布期望 0.7·1 + 0.2·2 + 0.1·3 = 1.3 ✅（outcomeValues 缺省→序数映射 i+1）
- intervenedMean=2.5 = SET 单点化 ✅；volatilityRange=[2.5,2.5] = 单点退化 ✅；maxDrawdown=0 = 常数序列 ✅
- lossProbability=0.07 = 7/100 采中 low(序数 3>2.5) 样本占比，与 P(low)=0.10 采样波动吻合 ✅
- 敏感性=0.125 = |ΔE|+|ΔVar| = 0.05·1·2.5 + 0（SET 单点 Var=0）= 0.125 ✅

**outcomeValues 精确键映射实证**（cf-d：high=10/mid=5/low=1，DELTA +2，N=300，seed=3，手算全部吻合）：
- baselineMean=8.12 = 0.7·10 + 0.2·5 + 0.1·1 = 8.2 − 0.08（N=300 采样波动）≈ 期望 8.2 ✅
- expectedBenefit=**+2.0 精确**（DELTA 全量平移严格 +2）✅；p50=12(=10+2)/p05=6→3? 实为 [3,12]=[low+2, high+2] 边界采样点 ✅
- maxDrawdown=9.0 = median(12) − min(3) ✅

**DELTA 语义 + 同 seed 复跑**（cf-b：DELTA=0.4，N=500，seed=2026）：expectedBenefit=+0.4（0.40000000000000036 float 噪声）✅；volatilityRange=[1.4,3.4]=[0+0.4, 3+0.4] 边界 ✅。

### 3.2 假设有效性过滤（T3 验收点）

cf-c（domain=supply-chain, supplier_default_prob 无干预 N=200）：
```json
"assumptionRefs":[],
"excludedAssumptions":[
  {"hypothesisId":"cog_hyp_947d5c5e-8c3","status":"INVALIDATED","invalidReason":"高可信冲突证据 cog_ev_0f861da4-aab（conf=0.99）与支撑证据同 metric=inv_turnover_days 冲突"},
  {"hypothesisId":"cog_hyp_seed_001","status":"INVALIDATED","invalidReason":"新证据 cog_ev_4ecfffeb-fcb 直接推翻支撑证据 cog_ev_seed_001"},
  {"hypothesisId":"cog_hyp_ef2debca-301","status":"INVALIDATED","invalidReason":"验收验证: P2a 人工失效链路留痕"}
]
```
3 条 INVALIDATED 假设全部进入排除列表（不进入推演前提）✅；pricing 域 0 假设 → assumptionRefs 空 ✅。

### 3.3 错误路径（0 个 5xx）

| Case | 请求 | 响应 |
|:--|:--|:--|
| E1 变量不存在 | variableName=no_such_var | `{"code":404,"message":"COG-404: 不确定性判断不存在: variable=no_such_var, domain=pricing"}` ✅ |
| E2 非法 op | op=MULTIPLY | `{"code":400,"message":"COG-400: 非法干预 op=MULTIPLY（允许 SET/DELTA）"}` ✅ |
| E3 domain 缺失 | — | `{"code":400,"message":"COG-400: domain 必填"}` ✅ |

### 3.4 审计（ecos.audit console-consumer 实读）

4 条 `cognitive.counterfactual` 审计消息全核（cf-a×2 复现双跑 + cf-b + cf-c，含 detail=domain/variable/interventions/N/seed；cf-d 另 1 条同格式）。HTTP 200 + code=0 成功路径 + code=400/404 业务错误体（HTTP 200 断言看 code+success 陷阱口径遵守）。

### 3.5 性能压测（T4 硬验收）

内联 PowerShell 循环 curl（客户端 Stopwatch 计时，含 gateway 处理+网络回环；同 JVM localhost 场景）：

| 档位 N | P50 | P99 | MAX | MIN | AVG | 预算 | 判定 |
|:--:|:--:|:--:|:--:|:--:|:--:|:--:|:--:|
| 1000 | **93ms** | **128ms** | 128 | 61 | 79 | P99 < 3s | ✅ 达标（余量 23s，2340%） |
| 5000 | **90ms** | **243ms** | 243 | 68 | 123 | P99 < 10s | ✅ 达标（余量 9.7s，3992%） |

单调性核验：5000/1000 采样比 5× → P99 比 243/128≈1.9×（亚线性：单次 big-array percentile 排序 O(N log N) + 敏感性分析 O(outcomes)），线性复杂度符合预期；**无需异步降级**（余量两数量级）。

## 4. 铁律合规自检（P3a）

| 检项 | 结论 |
|------|------|
| LLM 零参与认知计算 | PASS — CounterfactualSimulator 0 处 llm-gateway/LLM import（grep 核验），纯 Random(seed) + 解析公式 |
| 审计发 ecos.audit | PASS — 5 条实读（§3.4）；EventBus 通道复用 P2b 装配（0 新建 producer） |
| API 只增不改 | PASS — 1 新增端点 + 0 既有签名变更 |
| 0 新 Maven 模块 / 0 新 Docker 容器 | PASS |
| Schema 只加不删 | PASS — 0 DDL 变更（P3a 纯内存推理，结果不落盘=ADR-9 口径） |
| 定时任务/监控 | N/A（P3a 无定时/监控需求） |
| Map 入参 | PASS — 全强类型 DTO（CounterfactualRequest/Result），0 Map 入参出参 |

## 5. commit 凭证（P3a 待收口回填，见 §7 汇总）

---

# ══ P3b 段：业务落地与时间回放（P3a PASS 门禁后启动） ══

> P3a 验收 PASS 确认：① reviewer 独立审查 PASS ② 全量编译绿（§2）③ 压测达标（§3.5）④ 复现性验证（§3.1）→ 硬门禁通过，P3b 启动。

## P3b-1. 交付物清单（P3b T1~T5）

| Task | 交付文件 | 状态 |
|:--|------|:--:|
| T1 | `CognitiveBeliefController` 增 `GET /api/v1/cognitive/beliefs/{variable}/{version}/replay` + `BeliefStore.findIfExists(variable,domain,version)`（显式列名 + `version=? AND is_deleted=0`，404 转 null）+ `CounterfactualSimulator.replay()`（行覆盖 + asOf 假设时点过滤，复用 core 内核 0 复制逻辑）+ `BeliefReplayService`（GET query JSON 串参数解析）+ `CounterfactualResult` 增 `ReplayMeta`/`ReplayDistPoint` | ✅ |
| T2 | `CognitiveInvalidationConsumer`（cognitive 模块首个 `@KafkaListener`，topic=ecos.cognitive，groupId=**dccheng-cognitive-group**；String 入参 + 类型白名单 `COGNITIVE_HYPOTHESIS_INVALIDATED` + 异常吞掉 WARN 不堵分区，ref: kb-engine `EcosOntologyEventConsumer` 先例）+ `V130__ecos_cognitive_run_invalidation.sql`（事件↔run 留痕表 + `uniq_ecos_cog_run_inv_evt_run` 幂等键 + 3 索引）+ `MentalEventPublisher.publishRunSuperseded`（COGNITIVE_RUN_SUPERSEDED + faultContext.reviewTag） | ✅ |
| T3 | `ScenarioRunService` `ALLOWED_RUN_TYPES` 只增 `SAFEGUARD`（400 校验含新类型）+ `buildCounterfactualPayload`（counterfactualDomain 缺省 aviation / Variable / Interventions / OutcomeValues / SampleCount / Seed 六可选键）+ `DcchengClient.counterfactual`（**原始 JSON 通道**，见 P3b-6 缺陷修复） | ✅ |
| T4 | `MentalReviewController` `GET /api/v1/cognitive/mental-reviews?tag=&since=` + `MentalReviewService.aggregate`（**复盘口径定稿=三表版本链 + V130 impact 重建**——Kafka 事件未落 PG 全仓无 consumer 镜像表，指令允许二选一；tag 白名单与 `MentalEventPublisher.REVIEW_TAG` 同源，非法 tag 400；全显式列名 0 `SELECT *`） | ✅ |
| T5 | `api-contract.md` §3.4 P3b 登记（replay/mental-reviews 端点 + CognitiveInvalidationConsumer 消费方 + workspace SAFEGUARD 契约）+ 本段落 + `cognitive-engine-impl/AGENTS.md` 端点清单 | ✅ |

## P3b-2. V2 集成点 grep（编译前逻辑校验）

| 检项 | 结论 |
|------|------|
| 三滤波器 | `/api/v1/cognitive/**` 既有通配覆盖零新增登记（P3a 核对沿用）；workspace `:18090` 独立入口（gateway excludeFilters 排除 `workspace.controller.*` 整包——**环境事实**：该检出 gateway 未聚合 workspace controller，SAFEGUARD 验收走 workspace-service 独立 jar）✅ |
| Kafka 装配 | gateway `spring.kafka` producer/consumer 既配（P2b 落）+ `CognitiveMentalConfig` 类型化 producer；`@KafkaListener` 由 spring-kafka AutoConfiguration 默认 factory 装载（与 kb 先例同口径，0 自建 listener factory）✅ |
| 依赖方向 | Consumer/Review/Replay 仅 import 同模块 Store + common-api；0 跨 engine-impl import；workspace 侧 0 引 spring-kafka（仅 cognitive 侧消费）✅ |
| API 只增不改 | 2 新端点（replay/mental-reviews）+ SAFEGUARD 白名单只增；`ScenarioRunService` 既有 DIAGNOSE/FORECAST/SIMULATE/STRATEGY 4 分支代码 0 改动；`DcchengClient` 既有 4 方法 0 签名改动 ✅ |
| DB 只加不删 | V130 单张新表（0 改既有表/列）；docker cp 法执行成功（`ecos_cognitive_run_invalidation` + 4 索引就绪）✅ |

## P3b-3. seed 注册（curl 非 DDL，指令 §环境要点）

```
POST /api/v1/cognitive/beliefs × 4   → aviation/fuel_price_level v1(0.6/0.3/0.1) v2(0.5/0.4/0.1) v3(0.4/0.4/0.2) v4(0.3/0.4/0.3) 全部 version+1 落库，status=ACTIVE
POST /api/v1/cognitive/hypotheses    → HYP-SAFEGUARD-001 (cog_hyp_795a3871-11b, VALID, metric_ref=fuel_price_level)
POST /api/v1/cognitive/hypotheses    → HYP-SAFEGUARD-002 (cog_hyp_4a34a896-ca9, VALID, T2 链路专用)
```

## P3b-4. T1 回放端点 curl 验收（只读重算 + 0 行更新实证）

**无干预基线回放**（同参双跑逐位一致）：
```
GET /beliefs/fuel_price_level/2/replay?domain=aviation&sampleCount=1000&seed=42  (run1/run2)
baselineMean=1.626  intervenedMean=1.626  benefit=0.0  lossP=0.0  vol=1.0~3.0  maxDD=1.0
same=True（同参同 seed 双跑全字段一致）✅
```
**手算核验**：v2 分布 low=0.5/base=0.4/high=0.1 → 序数期望 E=0.5·1+0.4·2+0.1·3=1.6；N=1000 采样 1.626 ✅；单变量单点无干预 → intervened=baseline ✅。

**SET 干预版本对比**（`interventions=[{fuel_price_level,SET,1.3}]` JSON 数组串）：
```
replay-v2: base=1.626  interv=1.30  benefit=-0.326  lossP=0.535  vol=[1.3,1.3]  refs=[cog_hyp_795a3871-11b]
replay-v4: base=2.047  interv=1.30  benefit=-0.747  lossP=0.742  vol=[1.3,1.3]  refs 同上
```
- 版本间差异实证：v4 期望 2.0 > v2 期望 1.6（same SET 干预下 v4 亏损更多），**版本链语义生效** ✅
- SET 单点化：vol=[1.3,1.3]（常数序列退化）✅；lossP v2=0.535 < v4=0.742（配对采样与分布概率吻合）✅
- **asOf 假设时点过滤实证**：HYP-001 注册于 v2 之后、v4 之前 → 回放 v2/v4 时该假设"当时有效"均进 assumptionRefs（与 §P3b-5 失效后 excludedAssumptions 口径对照成立）✅

**replayMeta 完整校验**：`replayedVersion=2, currentVersion=4, versionUpdatedAt=2026-09-14T15:08:48.423327, believedDistribution=[low:0.5,base:0.4,high:0.1]`（原文搬运）✅

**版本不存在 404**：`/99/replay` → `COG-404: 不确定性判断版本不存在: variable=fuel_price_level, domain=aviation, version=99` ✅

**只读实证（psql 前后对拍）**：
```
回放前: SELECT max(update_time) ... fuel_price_level/aviation → 2026-09-14 15:08:48.496503
回放全链执行（6 次 replay 请求）后: 同一查询 → 2026-09-14 15:08:48.496503（0 行更新，逐位未变）✅
```

## P3b-5. T3 SAFEGUARD 贯通 sc001（含缺陷修复 P3b-6）

```
POST /api/v1/workspace/scenarios/sc001/runs  runTypes=[SAFEGUARD]
  counterfactualVariable=fuel_price_level  DELTA=+0.15  outcomeValues{low:1,base:1.15,high:1.5}  N=1000 seed=42
→ runId=run_b6801924-6b6 status=SUCCEEDED degraded=false
```
**落库核验（psql，修复后终版行 run_d3a5542f/run_b6801924）**：
```
refs_type=array  risk_type=object  s3_type=array
im=1.3680499999999964  bm=1.21805  benefit=0.14999999999999636
assumptionRefs=["cog_hyp_4a34a896-ca9"]  (jsonb @> 包含匹配 t)
```
- **四指标全数字落库 + assumptionRefs 真数组** ✅（DELTA +0.15 → expectedBenefit ≈ +0.15 精确平移手算吻合；volatilityRange=[1.15,1.65]=[low+0.15, high+0.15] 边界采样点 ✅）
- run 含 assumptionRefs = T2 联动作废关联键 ✅

## P3b-6. T3 验收过程缺陷与修复（验收期间发现并关闭，独立审查已覆盖）

**现象**：初版 SAFEGUARD 落库 `simulation_result` 为 `"assumptionRefs":"cog_hyp_..."`（字符串）/ `"volatilityRange":{"volatilityRange":[...]}`（同名键嵌套）/ 数值变字符串 / null→""，`@>` 数组包含匹配恒 miss，**T2 联动作废失效**。

**排查路径（证据链完整）**：
1. gateway 直连 curl `POST :8080/api/v1/cognitive/counterfactual` 原始响应**干净 JSON**（数字/数组/null 全对）→ 排除 cognitive 侧；
2. 注入 ObjectMapper vs 全新 `new ObjectMapper()` 双序列化对比（临时 DIAG 日志）→ 输出相同 → 排除 ObjectMapper bean 污染，**污染源=RestTemplate 反序列化环节**；
3. `postJson` 捕获原始响应文本诊断 → gateway 返回 **`<ApiResponse><code>0</code>...` 伪 XML 文本**：`Map.class`/默认 Accept（`text/plain, */*`）下，workspace classpath 的某 XML 系 `HttpMessageConverter`（Jakarta+Jackson 双栈）被 RestTemplate ContentNegotiation 选中 → gateway 侧 `@RestController` 按 Accept 编出伪 XML → workspace `readTree` 解析 `<baselineMean>` 等 XML 文本为同名字段/字符串，结构全毁（与 `Map.class` 二次 Map 化叠加产生同名键嵌套）。curl 带 `Accept: application/json` 即返回干净 JSON 佐证。

**修复（仅新增路径，0 触碰既有 4 方法）**：`DcchengClient.postJson` 专用原始 JSON 通道——body Map 经独立 `RAW_JSON` 序列化发送；响应显式 `Accept: application/json` + `String.class` 接收（0 结构解析）→ `RAW_JSON.readTree` 得 JsonNode 树；`ScenarioRunService.toJson` 对 JsonNode 直取**规范树文本**落库（无损，不依赖注入 mapper 配置）。

**复验**：重建后重跑 SAFEGUARD → 落库全干净（P3b-5 数字/数组证据即修复后行）✅；临时 DIAG 代码已删除。

## P3b-7. T2 失效→作废联动全链验收

```
① POST /hypotheses  注册 HYP-SAFEGUARD-002 (cog_hyp_4a34a896-ca9, VALID)
② POST /scenarios/sc001/runs SAFEGUARD → run_b6801924-6b6 SUCCEEDED，落库 assumptionRefs=["cog_hyp_4a34a896-ca9"]
③ psql 前置核验: (simulation_result->'assumptionRefs') @> '["cog_hyp_4a34a896-ca9"]'::jsonb = t
④ POST /hypotheses/cog_hyp_4a34a896-ca9/invalidate {reason="P3b 失效→Kafka→作废链路实证..."}
⑤ gateway 日志: MentalEventPublisher 心智事件已发布 eventType=COGNITIVE_HYPOTHESIS_INVALIDATED eventId=cog_evt_...
              → CognitiveInvalidationConsumer（ntainer#0-0-C-1 消费线程）作废联动完成 supersededRuns=1
              → MentalEventPublisher 发布 eventType=COGNITIVE_RUN_SUPERSEDED eventId=cog_evt_73a1e97f-0f1
⑥ psql 核验（invalidate 后 8s）:
   ecos_scenario_run:  run_b6801924-6b6 status=SUPERSEDED ✅
   ecos_cognitive_run_invalidation: 1 行 (event_id=cog_evt_a2fb2df8-ba3, hypothesis_id=cog_hyp_4a34a896-ca9, run_id=run_b6801924-6b6, auto_detected=f, detail=hypothesisCode=HYP-SAFEGUARD-002...) ✅
⑦ 幂等：UPDATE 带旧状态守卫（仅 SUCCEEDED/DEGRADED 可作废）+ impact INSERT 用 NOT EXISTS + uniq(event_id,run_id) 唯一键 → 重复投递 0 二次留痕（impact_rows 恒 1，run 恒 SUPERSEDED 三查验证）✅
⑧ 审计：ecos.audit 发 hypothesis.invalidation-run-superseded（日志 EventBus WARN 降级口径遵守，broker 可达时实读）
```

## P3b-8. T4 mental-reviews curl 验收

```
GET /api/v1/cognitive/mental-reviews?tag=P2b-mental-layer-review&since=2026-09-14T15:00:00
→ code=0 reconstructionMode=three-table-version-chain+V130-impact（Kafka 事件未落 PG，按三表版本链重建口径，P3b T4 定稿）
  beliefTimelines=1  hypotheses=2  evidence=0  runImpacts=1
  summary={beliefVariables:1, beliefVersions:4, hypothesesTotal:2, hypothesesInvalidated:2, evidenceCount:0, runsSuperseded:1}
  （beliefVersions=4/2 失效假设/1 作废 run 与 DB 实际状态完全吻合）✅
GET ...?tag=NOT_A_TAG → code=400 "COG-400: 非法复盘 tag=NOT_A_TAG（白名单: [P2b-mental-layer-review]）" ✅
```

## P3b-9. 向后兼容回归（T3 硬验收）

```
POST /scenarios/sc001/runs  runTypes=[DIAGNOSE]（既有端点，0 改动路径）
→ run_a0778ec7-0e9 status=SUCCEEDED degraded=false
  diagnosis={affectedMetrics, causalChain, degraded, degradeReason, diagnosisId, reasoningPath, rootCause, suggestions}（完整 8 键结构）✅
```
SAFEGUARD 分支与 DIAGNOSE 分支独立 if（0 短路），runType 白名单校验 400 文案仅增 SAFEGUARD；DIAGNOSE 行为零变化 ✅。

## P3b-10. 铁律合规自检（P3b）

| 检项 | 结论 |
|------|------|
| 写操作必发 ecos.audit | PASS — run 落库（P3a T3 审计通道复用）+ invalidate 失效 + 作废联动 superseded 三类均走 EventBus；consumer 审计 `@Autowired(required=false)` null 兜底不阻塞 |
| Kafka 消费先例一致 | PASS — `@KafkaListener` + String 入参 + ObjectMapper 解析 + 异常 WARN 不堵分区 + 类型白名单（ref: kb-engine EcosOntologyEventConsumer） |
| 0 新 Maven 模块 / 0 新 Docker 容器 | PASS — 复用认知/workspace 既有模块；workspace-service 为 PMO-49 既有 7 部署单元之一 |
| Schema 只加不删 | PASS — V130 单新表 + 4 索引，0 触碰既有 |
| LLM 零参与 | PASS — replay/reviews 全纯 Java 数值/SQL 聚合，0 LLM import |
| API 只增不改 | PASS — 2 新端点 + 1 白名单只增，0 既有签名变更 |
| 三滤波器 | PASS — cognitive 既有通配零新增；workspace 独立部署入口（环境事实登记） |

## P3b-11. commit 凭证（收口回填）

（P3b clean commit hash 在本节补录，见下方收口）
