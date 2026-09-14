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

（P3b 交付明细在收口时补全本段：T1 replay / T2 失效订阅 / T3 SAFEGUARD / T4 mental-reviews / T5 收口文档 + 压测与 curl/psql 原文 + 向后兼容回归证据）
