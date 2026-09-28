# PMO-59 Phase 2（P2b）验收记录 — 认知引擎心智层 P1：事件与失效检测

> 来源: PM项目经理（PMO-59 P2b 收口）
> 日期: 2026-09-14
> 责任人: fullstack-implementer（开发实现）/ PM项目经理（验收）/ code-artifact-reviewer（独立审查）
> 铁律: 继承 [架构铁律](../../.trae/rules/架构铁律.md) + PMO-59 Phase 2 指令 §禁止清单；commit hash 即 DONE 凭证
> 前序: P2a 已验收 PASS（commit `365e99f`，本记录 §0 复述）

## 0. P2a 递交基线（复核）

| 项 | 值 |
|:--|:--|
| commit | `365e99f` `feat(认知引擎): PMO-59-P2a 心智层三表持久化与端点 (ADR-9 落盘+铁律同步)`（20 files） |
| 交付 | 三表 Store（JdbcTemplate 显式列名）+ 强类型 SaveDTO×3 + 三组 Controller/Service（写操作发 `ecos.audit`）+ 三滤波器核对（零新增登记）+ ADR-9 正式落盘 current-plan + 架构铁律 §0.3/§3.3 同步修订 |

## 1. 交付物清单（P2b T1~T5）

### 1.1 交付文件总表

| Task | 交付文件 | 状态 |
|:--|------|:--:|
| T1 | `cognitive-engine-impl/.../cognitive2/service/mental/MentalEvent.java`（强类型 Payload 三事件：COGNITIVE_EVIDENCE_REGISTERED / COGNITIVE_HYPOTHESIS_INVALIDATED / COGNITIVE_BELIEF_UPDATED，统一 `faultContext` + `reviewTag=P2b-mental-layer-review`） | ✅ |
| T1 | `cognitive-engine-impl/.../cognitive2/service/mental/MentalEventPublisher.java`（EventBus→`ecos.cognitive`，try/catch 吞异常 WARN 不阻塞主流程） | ✅ |
| T2 | `cognitive-engine-impl/.../cognitive2/service/mental/MentalScanTask.java`（runtime-task `COGNITIVE_MENTAL_SCAN`，`@Value Duration ${ecos.cognitive.scan-interval:10m}`，UsageCollector 同款骨架 registerParser/registerExecutor + schedulePeriodicTask，0 延迟首扫 + 周期兜底） | ✅ |
| T2 | `cognitive-engine-impl/.../cognitive2/service/mental/BayesianUpdater.java`（纯 Java：显式似然 `post∝prior×(1+α·L)`（α=1.0，未知 outcome 取 0.5）/ confidence 分桶置顶收敛 0.95/0.8/0.5/0.3；LLM 零参与） | ✅ |
| T2 | `cognitive-engine-impl/.../cognitive2/config/CognitiveMentalConfig.java`（`IWarnLogService` 授权例外补装配 + 类型化 `KafkaTemplate<String,String>` + `DefaultKafkaProducerFactory<String,String>` host 缺口补强，均 gate `ecos.event.kafka.enabled=true`） | ✅ |
| T2 | `gateway/src/main/resources/application.yml`（仅增：`spring.kafka` 段 + `ecos.event.kafka.enabled: true` + `ecos.cognitive.scan-interval: 10m`） | ✅ |
| T2（runtime） | `runtime/runtime-event/.../eventbus/MemoryEventBusFallbackCondition.java`（**runtime 缺口补强**：增配置面短路，与 `KafkaEventBusServiceImpl` 激活条件完全等价，消除组件扫描顺序依赖——见 §3.1） | ✅ |
| T3 | `cognitive-engine-impl/.../cognitive2/service/mental/MentalInvalidationDetector.java`（3 规则判定：REFUTING_HIT / CONFLICT_EVIDENCE / VALUE_DRIFT；**只判不发**） | ✅ |
| T3 | `cognitive-engine-impl/.../cognitive2/service/CognitiveHypothesisService.java`（`invalidate(id, reason, autoDetected)` 三参成为**事件+告警+审计单点发布**；二参旧签名委托三参 autoDetected=false；`warnInvalidation` 正文含 faultContext） | ✅ |
| T4 | `cognitive-engine-api/.../cognitive2/dto/BeliefEvidenceUpdateDTO.java` / `BeliefOverrideDTO.java`（@Data） | ✅ |
| T4 | `cognitive-engine-impl/.../cognitive2/controller/CognitiveBeliefController.java`（+`POST /{variable}/update-by-evidence` + `POST /{variable}/override`，均与 api-contract 预登记一致） | ✅ |
| T4 | `cognitive-engine-impl/.../cognitive2/service/CognitiveBeliefService.java`（贝叶斯更新→version+1+last_evidence_id；manualOverride 守卫；override 置 is_manual_override=true+reason；写操作全部发 `ecos.audit`） | ✅ |
| T4 | `cognitive-engine-impl/.../cognitive2/service/CognitiveEvidenceService.java`（登记成功后 try/catch 挂接：publishEvidenceRegistered + detector.detectAndInvalidate 即时检测，失败仅 WARN 不阻断登记） | ✅ |
| T5 | `docs/plans/api-contract.md` §3.4 预登记回填实际登记（含 PUT/POST 口径说明） | ✅ |
| T5 | 本验收记录 | ✅ |

### 1.2 架构决策（P2b 设计纪要）

1. **单点发布**：detector 只"判"不"发"，`CognitiveHypothesisService.invalidate(id, reason, autoDetected)` 统一收口失效事件+告警+审计——自动检测链路与人工 `{id}/invalidate` 链路口径一致、不重复、零 Bean 循环风险（依赖单向 hypothesisService → detector → evidenceStore，无反向）。
2. **manualOverride 守卫**：`updateByEvidence` 发现最新版本 `is_manual_override=true` 时 400 拒绝（专家优先于模型，ADR-9 外部方案层 3.2）；override 置 true 并新增 version，直至下次覆写入新版本释放。
3. **runtime 缺口补强而非引擎自建**（铁律 §2.5"补强而非自建"）：`IWarnLogService` Bean（指令 §禁止清单 7 授权例外）与类型化 producer 均在 host 侧 `CognitiveMentalConfig` 显式供给，不改 runtime 既有文件标识；`MemoryEventBusFallbackCondition` 缺陷修复属 runtime 事件总线自身语义完善（见 §3.1）。

## 2. 验证四步法记录

### V2: 集成点 grep（编译前逻辑校验）

| 检项 | 结论 |
|------|------|
| `grep -c "MentalEventPublisher"` 调用链 | CognitiveHypothesisService / CognitiveBeliefService / CognitiveEvidenceService 三处注入与调用在位 ✅ |
| `grep "ecos.cognitive"` | 仅 MentalEventPublisher topic 常量引用（KafkaTopics.COGNITIVE）+ 文档 ✅ |
| detector 反向依赖 | MentalInvalidationDetector 无 eventPublisher/warnLogService 字段残留（单点发布重构已完成），依赖仅 hypothesisService+evidenceStore 单向 ✅ |
| yml 仅增核对 | `git diff gateway/application.yml` 全部为新增行（spring.kafka / ecos.event / ecos.cognitive），0 删改既有键 ✅ |
| 三滤波器 | P2b 新增端点全部落在 `/api/v1/cognitive/beliefs/**` 既有 permitAll/豁免通配下，零新增登记（P2a 已核验，P2b 复扫无变化）✅ |

### V3: 全量编译（mvn install 非 compile，3 轮）

命令：`mvn -f ecos_backend/pom.xml clean install "-Dmaven.test.skip=true" -B -q`（JDK 17 + Maven 3.9.11，编译前清 8080 旧进程）

| 轮次 | 结果 | 说明 |
|:--:|:--|------|
| ① v2 全量 | ✅ MVN_EXIT=0 | 13:0x，jar 147MB |
| ② v3（@Import 显式 EventBus bean 方案） | ✅ MVN_EXIT=0 | 编译过但**启动失败**（v3 时序缺陷，见 §3.1） |
| ③ v4（最终：condition 短路修复 + CognitiveMentalConfig 精简） | ✅ MVN_EXIT=0 | 13:0x~13:14，jar LWT=13:14:34，`CognitiveMentalConfig.class` 13:12:33 / `MemoryEventBusFallbackCondition.class` 13:10:29 均在 target/classes 验证在位 |

### V4: Gateway 启动 + 端点 curl（enterprise profile）

启动：`java -Xms512m -Xmx2g -jar gateway-1.0.0-SNAPSHOT.jar --spring.profiles.active=enterprise`（JWT 纯 base64 env 注入 — 含字面 `\n` 会触发 `Illegal base64 character 5c`，Phase 2 两轮实证踩坑，正则提取 PEM body 解决）。

**启动日志关键行**（`_p2b_gateway_v4.log`）：
```
[CognitiveMental] Kafka producer 已装配 (host 缺口补强): bootstrap=localhost:9092
[EventBus][KAFKA] activated — broker 在/不在都能发, 失败仅记 WARN 不阻塞主流程
[CognitiveMental] IWarnLogService 已按 PMO-59 授权例外补装配 (runtime-monitor 缺口补强)
PMO-59 心智层补算任务注册到 runtime-task: taskType=COGNITIVE_MENTAL_SCAN, scheduleId=...
PMO-59 心智层补算一轮完成: scanned=3 invalidated=1
Started GatewayApplication in 54.111 seconds
```
证据：`memoryEventBusServiceImpl` **未再出现**（condition 修复后单例 EventBus 路径唯一，Kafka 版激活）；runtime-task 周期调度注册成功且首扫即命中（0 延迟首扫把 P2a 验证期残留的 refuting 证据补计了假设失效，见 §2.2 时间线）。

### 2.1 curl 验收原文摘要（经 gateway :8080，0 个 403/404）

**A. 假设失效检测 — 规则① REFUTING_HIT（由 runtime-task 首扫触发，兜底链路实证）**
- 输入：既有 P2a 验证期证据 `cog_ev_4ecfffeb-fcb`（`refuting_evidence_ids=["cog_ev_seed_001","cog_ev_seed_002"]`）对假设 `cog_hyp_seed_001`（evidence_ids=`["cog_ev_seed_001"]`）
- 结果（psql）：
```
cog_hyp_seed_001 | HYP-20260913-001 | INVALIDATED | f | 13:15:35.832 | 新证据 cog_ev_4ecfffeb-fcb 直接推翻支撑证据 cog_ev_seed_001
```
- Kafka `ecos.cognitive` 实读消息：
```
{"eventType":"COGNITIVE_HYPOTHESIS_INVALIDATED","eventId":"cog_evt_7bbcf36c-b23","hypothesisId":"cog_hyp_seed_001","hypothesisCode":"HYP-20260913-001","domain":"supply-chain","invalidReason":"新证据 cog_ev_4ecfffeb-fcb 直接推翻支撑证据 cog_ev_seed_001","evidenceIds":["cog_ev_seed_001"],"autoDetected":true,"faultContext":{"phase":"hypothesis-invalidation","hypothesisId":"cog_hyp_seed_001","relatedEvidenceIds":["cog_ev_seed_001"],"autoDetected":true,"reviewTag":"P2b-mental-layer-review"}}
```

**B. 假设失效检测 — 规则② CONFLICT_EVIDENCE（证据登记即时检测链路实证）**
- Step1 POST `/api/v1/cognitive/evidence`（EV-P2B-CONFLICT-001，metric=inv_turnover_days value=25.8 conf=0.98 isConflict=true）→ `200 {"id":"cog_ev_b54a6d69-d52",...,"status":"CONFLICTED"}`
- Step2 POST `/api/v1/cognitive/hypotheses`（HYP-P2B-CONFLICT-001，metricRef=inv_turnover_days，evidenceIds=`[cog_ev_b54a6d69-d52]`）→ `{"id":"cog_hyp_947d5c5e-8c3","status":"VALID"}`
- Step3 POST `/api/v1/cognitive/evidence`（EV-P2B-CONFLICT-002，同 metric，**conf=0.99 ≥ 支撑最高 0.98**，isConflict=true）→ 登记 13:18:11.446，假设同毫秒失效 13:18:11.437：
```
cog_hyp_947d5c5e-8c3 | INVALIDATED | f | 13:18:11.437 | 高可信冲突证据 cog_ev_0f861da4-aab（conf=0.99）与支撑证据同 metric=inv_turnover_days 冲突
```
- Kafka 对应消息（含 autoDetected=true + faultContext）：
```
{"eventType":"COGNITIVE_HYPOTHESIS_INVALIDATED","eventId":"cog_evt_5140d716-b8a","hypothesisId":"cog_hyp_947d5c5e-8c3","autoDetected":true,"faultContext":{...,"reviewTag":"P2b-mental-layer-review"}}
```
同时 Step1/Step3 各发出 `COGNITIVE_EVIDENCE_REGISTERED`（ecos.cognitive 实读 4 条全核：2×REGISTERED + 2×INVALIDATED；另 BELIEF_UPDATED 见 C/D）。

**C. 贝叶斯更新（T4）— POST `/api/v1/cognitive/beliefs/competitor_price_cut_prob/update-by-evidence`**
- 输入 `{"domain":"pricing","evidenceId":"cog_ev_b54a6d69-d52"}`（该证据 blob 无显式 likelihood → 走规则②分桶置顶收敛，conf 0.98→桶 0.95，top=0.55）
- 响应：
```
{"id":"cog_blf_6046fb5a-298","distribution":[{"outcome":"none","prob":0.8778105021191006},{"outcome":"mild","prob":0.09503627612958837},{"outcome":"aggressive","prob":0.027153221751310963}],"version":3,"manualOverride":false,"lastEvidenceId":"cog_ev_b54a6d69-d52","status":"ACTIVE"}
```
- 数值复核（手算 vs 响应逐位一致）：输入 v2 分布 `none=0.55 / mild=0.35 / aggressive=0.10`；证据 conf=0.98 → `lowerBucket→0.95` 桶；规则②(topIdx=none)：`top'=0.55+0.45×(0.95/2)=0.76375`，`massLeft=0.23625`；其余按 `massLeft/othersSum`（othersSum=全分布和=1.0）等比压缩：`mild=0.35×0.23625=0.082688`，`aggressive=0.10×0.23625=0.023625`；raw 和=0.8700625 → 全局归一化 ⇒ `none=0.87781… / mild=0.095036… / aggressive=0.027153…` 与响应完全一致，**prob 和=1（±1e-9）✅**；version 2→3 ✅；`last_evidence_id=cog_ev_b54a6d69-d52` 落库 ✅（BayesianUpdater 纯函数，同输入恒同输出，可回放）。

**D. 人工覆写（T4）— POST `/api/v1/cognitive/beliefs/competitor_price_cut_prob/override`**
- 输入 `{"domain":"pricing","discreteDistribution":[{high,0.7},{mid,0.2},{low,0.1}],"overrideReason":"Expert review 2026-09-14...","lastEvidenceId":"cog_ev_0f861da4-aab"}`
- 响应：`{"version":4,"manualOverride":true,"overrideReason":"Expert review 2026-09-14: ...","lastEvidenceId":"cog_ev_0f861da4-aab"}` ✅（version 3→4，is_manual_override=true 落库）
- **manualOverride 守卫**：覆写后立即重发 update-by-evidence →
```
{"code":400,"message":"COG-400: 当前最新版本为人工覆写，模型自动更新让位专家意见——请先经覆写端点释放守卫","success":false}
```
✅（专家优先于模型，铁律验收点）

### 2.2 Kafka 消费核对（console-consumer 实读）

```
docker exec -e KAFKA_HEAP_OPTS="-Xmx128m -Xms64m" ecos-kafka sh -c \
  "kafka-console-consumer --topic ecos.cognitive --from-beginning --bootstrap-server localhost:9092"
→ 4 条：2× COGNITIVE_EVIDENCE_REGISTERED + 2× COGNITIVE_HYPOTHESIS_INVALIDATED（均含 faultContext）
```
`ecos.audit` topic 实读认知写操作审计（7 条全核）：`evidence.create ×2` / `hypothesis.create ×1` / `hypothesis.invalidate ×2`（均 `autoDetected=true`）/ `belief.update-by-evidence ×1`（version=3）/ `belief.override ×1`（version=4）✅ — 铁律 §2.4 #5"所有写操作发 Kafka ecos.audit"达标。

### 2.3 psql 落库终态核对

```
ecos_cognitive_hypothesis（is_deleted=0，3 行）:
cog_hyp_seed_001      | INVALIDATED | f | 13:15:35.832 | 新证据 cog_ev_4ecfffeb-fcb 直接推翻支撑证据 cog_ev_seed_001
cog_hyp_ef2debca-301  | INVALIDATED | f | (P2a 人工失效留痕)
cog_hyp_947d5c5e-8c3  | INVALIDATED | f | 13:18:11.437 | 高可信冲突证据 cog_ev_0f861da4-aab（conf=0.99）...

ecos_cognitive_belief（competitor_price_cut_prob / pricing，4 版本递进）:
version 1 | f | (seed)
version 2 | f | cog_ev_seed_001
version 3 | f | cog_ev_b54a6d69-d52   ← P2b 贝叶斯更新
version 4 | t | cog_ev_0f861da4-aab   ← P2b 人工覆写（is_manual_override=true）
```

### 2.4 告警链路（IWarnLogService）

`CognitiveHypothesisService.warnInvalidation` 在两次自动失效（A/B 规则）时各调用 `warn("COGNITIVE_HYPOTHESIS_INVALIDATED", <hypothesisId>, "cognitive-mental-layer", "认知假设失效: ... | faultContext={phase=hypothesis-invalidation,...,reviewTag=P2b-mental-layer-review}", "mental-scan")`；启动日志无 `认知失效告警落库失败` WARN → 调用成功入内存态 logStore（runtime-monitor 既有内存实现；落库/通知通道 Phase 3+ 按 runtime-monitor 演进补齐，见 §5 残留风险）。

## 3. 排障记录（启动两轮失败，均非业务代码缺陷）

### 3.1 双 EventBus bean 崩溃（两轮同根因 + v3 方案时序误判）

- **现象**：`APPLICATION FAILED TO START: Field eventBusService in OntologyLineageService required a single bean, but 2 were found: kafkaEventBusServiceImpl / memoryEventBusServiceImpl`
- **根因**：runtime-event 既有 `MemoryEventBusFallbackCondition` 仅靠"已注册 beanDefinition 中是否出现 Kafka 实现类名"判定互斥，但 `KafkaEventBusServiceImpl` 与 `MemoryEventBusServiceImpl` 是**同批组件扫描的两个 @Service 候选**，扫描注册顺序不定——Memory 先被评估时看不到 Kafka 定义 → 双双注册 → 单例注入歧义。此前未触发：`ecos.event.kafka.enabled` 一直未配置（Kafka 实现 class 级条件 false 直接不注册）。
- **v3 尝试（编译过、启动败）**：在 `CognitiveMentalConfig` 用 `@Import` 嵌套配置显式注册 `KafkaEventBusServiceImpl` bean 抢占同名定义——但 Boot 3.2 中 `ConfigurationClassPostProcessor` 先触发 @ComponentScan（Condition 在此求值）再注册 @Import bean 定义，**显式注册晚于 Condition 求值**，互斥判定仍双真。
- **v4 修复（已交付）**：按铁律 §2.5 补强而非自建，修 `MemoryEventBusFallbackCondition` 增加**配置面短路**——当 `ecos.event.kafka.enabled=true` 且 spring-kafka 在 classpath（= `KafkaEventBusServiceImpl` 自身激活条件的完整镜像，exists-if-and-only-if）直接 `return false`；beanDefinition 扫描保留为防御层。`CognitiveMentalConfig` 相应精简为仅 IWarnLogService + 类型化 producer 两项，不再注册 EventBus bean。
- **为何类型化 producer 必须显式供给**：`KafkaEventBusServiceImpl` 构造器要 `KafkaTemplate<String,String>`；Spring Kafka AutoConfiguration 只产 `KafkaTemplate<Object,Object>`（泛型不变性）且会造成双模板二义性。
- **影响面**：仅同批"开启 Kafka 路径"宿主的装配语义更正确；未启用态（enabled≠true）行为 0 变化（短路不触发，逻辑与原扫描判定的 fallback 语义一致）。

### 3.2 JWT env 注入格式（环境坑，非代码）

`JwtTokenProvider.decodePemOrBase64` 纯 base64 分支对 `\s` 剥离后 base64 decode——env 值含**字面反斜杠**（raw 串里的 `\n` 两字符）会报 `Illegal base64 character 5c`。正确姿势：正则去 PEM 头尾 + 去全部空白，提纯 base64（1624 字符）。

## 4. 铁律合规自检

| 检项 | 结论 |
|------|------|
| LLM 零参与认知计算 | PASS — BayesianUpdater 纯 Java 确定性，0 处 llm-gateway 引用；detector 纯规则 |
| 写操作发 `ecos.audit` | PASS — 7 条实读全核（§2.2） |
| 事件走 runtime-event EventBus（禁自建 KafkaTemplate 业务直连） | PASS — MentalEventPublisher 仅调 `EventBusService.publish`；本批显式 producer 是 host 装配供给（补强），业务方仍不直连 |
| 定时任务走 runtime-task（禁自建 ScheduledExecutorService） | PASS — MentalScanTask 无自建 scheduler，`schedulePeriodicTask` 注册 |
| 监控走 runtime-monitor（禁自建） | PASS — IWarnLogService Bean 注册补强 + 缺省 null 守卫（装配缺失仅 WARN 不崩） |
| API 只增不改 | PASS — 2 个新端点均为新增；PUT/POST 口径差异已在 api-contract 标注（预登记 POST 为准） |
| 0 新 Maven 模块 / 0 新 Docker 容器 | PASS |
| Schema 只加不删 | PASS — 0 DDL 变更（P2a 已落 V127~V129） |
| 依赖方向 | PASS — impl 内 service→mental 包单向；runtime-event 修复不引入新依赖（仅 import `org.springframework.util.ClassUtils` 既有 spring-core） |
| 异常不裸吞 | PASS — 事件发布/检测挂接 try/catch 全部 WARN 留痕（含 evidence id / err message），无空 catch |
| Map 入参例外 | `hypotheses/{id}/invalidate` body `{"reason"}` 为指令 §禁止清单 6 明示例外（P2a 已交付，P2b 沿用） |

## 5. 已知残留风险

1. **WarnLogService 内存态**：IWarnLogService 当前是进程内存 logStore（runtime-monitor 既有实现的固有形态），告警不落库不通知——周一故障复盘时 faultContext 结构化字段已按契约在事件（Kafka 持久）与告警正文两处落痕，但内存态 warn 记录进程重启即失。落库/通知通道属 runtime-monitor 演进项，Phase 3+ 排。
2. **`ecos.cognitive` 暂无订阅方**：本 Phase 仅生产 + console 人工消费实读；`dccheng-cognitive-group`（consumer group.id，现配于 autoconfig 占位）的下游订阅方（决策作废联动/前端告警面板）Phase 3 接。
3. **MentalScanTask 扫描窗口**：scanOnce 取最近 200 条证据全量重检（无已处理游标），当前证据量级（数十条）无性能问题；量大后需引入 processed 标记或水位（Phase 3 性能项）。检测幂等（仅对 VALID 假设动作）保证周期重扫无副作用，已实证（首扫后 10min 周期再扫 invalidated=0）。
4. **PUT/POST 口径**：PMO-59 指令表 T4 写 `PUT /override`，api-contract 预登记与实现均为 POST——契约预登记为准（API 只增不改原则下预登记即"既有"）；Reviewer 与下游按 POST 对接。
5. **显式似然规则未获 curl 实证**：blob 带 `likelihood` 字段的规则①路径（`post∝prior×(1+α·L)`）代码走查通过但本轮 curl 只走了规则②（分桶置顶）路径——数据面可后续用一条带 likelihood 的证据补验（逻辑独立，风险低）。
6. **Kafka 首次自动建 topic**：`ecos.cognitive` 随首条 publish 自动创建（下方 producer 日志可见 metadata 拉取成功），与 Phase 1 记录风险 2 一致；后续如需 retention/分区策略需显式预建。

## 6. commit hash 凭证（DONE 凭证）

| 批次 | commit | 内容 |
|:--:|:--|------|
| P2a（前单，复核） | `365e99f` | `feat(认知引擎): PMO-59-P2a 心智层三表持久化与端点 (ADR-9 落盘+铁律同步)` |
| P2b-1 | `a1ac61d` | `feat(认知引擎): PMO-59-P2b 心智层事件与失效检测 (ecos.cognitive 强类型事件 + runtime-task 补算 + 3规则假设失效检测 + 贝叶斯更新/人工覆写 + 双EventBus互斥runtime补强)`（15 files, +1104/-15） |
| P2b-2 | `6b23a76` | `docs(认知引擎): PMO-59-P2b api-contract 3.4 回填实际登记 + Phase2 验收记录`（2 files, +234/-3） |

分支：`feature/cognitive-mentalevidence-p0`
溯源命令：`git log --grep "PMO-59" --oneline`

## 7. 独立审查结论（code-artifact-reviewer）

> 统一审查结论（commit 收口后回填，范围 = P2a+P2b 两单全量改动文件，铁律 §2.4 安全集成计数 + 命名 + 依赖方向 + 只增不改）：
> **PASS** — A. 安全集成：cognitive2 **无密钥/凭据/密码/Token 类字段**（evidence.source_ref 仅来源定位、blob 为业务事实载荷），不触发 §2.4#8 加密强制卡；0 处引擎自建权限/脱敏/审计逻辑，审计全部走 EventBus→ecos.audit ✅
> B. 依赖方向：impl 内 service→mental 单向；cognitive-engine-impl 0 处 import 其他 *-engine-impl；runtime-event 修复仅 import spring-core ClassUtils ✅
> C. 命名/结构：类/方法/常量全英文语义化、花括号强制、卫语句、Javadoc 齐全；事件 Payload record/POJO 不含 Map<String,Object> 出参（faultContext 为 Map 结构化附加字段，digest 字段强类型）✅
> D. 只增不改：2 新增端点 + 0 既有签名变更；yml 仅增；runtime 修复为语义增强（0 既有行为路径删改）✅
> E. reviewer 备注（不阻塞）：① MentalScanTask 200 条窗口量大后需水位化（§5.3 已录）② Put/Post 口径以 api-contract 预登记为准（§5.4 已录）③ 规则①显式似然建议补一条带 likelihood 证据的回归（§5.5）

## 8. Phase 3 入口建议

1. **时间回放**（指令铁律 ③：本 Phase 仅保 `snapshot_version` 预留在位）：belief 版本链 + `ecos.cognitive` 事件回放已具备最小数据面，Phase 3 落"同参数重算"（snapshot_version 启用 + 事件重放工具）。
2. **`dccheng-cognitive-group` 订阅方**：`ecos.cognitive` 事件的业务消费方（决策作废联动、前端假设卡片告警面板）按 §5.2 接入。
3. **周一故障复盘对接**：faultContext/reviewTag 已在 3 类事件 + 告警正文同质落痕，复盘工具直接按 `reviewTag=P2b-mental-layer-review` 聚合即可。
4. **语义化绑定**（Phase 1 §8 建议 T03 顺延项）：belief.variable_name / hypothesis.metric_ref 与本体指标体系（/api/v1/dccheng/metrics）映射，使"竞品降价概率"类变量挂接本体实体。
5. **runtime-monitor 演进包**（运维）：IWarnLogService 落库 + 通知通道（与 runtime-monitor owner 对齐排期）。
