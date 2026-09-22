# PMO-59 Phase 3 指令: 认知引擎 — 反事实推演核心 + 业务场景落地（两单串行）

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../.trae/rules/架构铁律.md)
> 来源: 肖国荣 | 日期: 2026-09-14
> 责任人: fullstack-implementer（开发实现）/ PM项目经理（验收）/ code-artifact-reviewer（独立审查）
> 铁律: ① LLM 零参与认知计算（纯 Java 蒙特卡洛）；② 写操作必发 Kafka `ecos.audit`（EventBus）；③ 回放/复盘只读重算，禁止改历史数据
> 前序: P2b 已验收 PASS（commit `a1ac61d`/`6b23a76`，见 `PMO-59-认知引擎心智层-Phase2验收记录.md`）

## §背景

Phase 1/2 已交付：三表 evidence/hypothesis/belief（V127~V129）+ 9 端点 + 贝叶斯更新/人工覆写；事件链 MentalEventPublisher→Kafka `ecos.cognitive`（含 faultContext/reviewTag=`P2b-mental-layer-review`）+ `ecos.audit` 审计；runtime-task `COGNITIVE_MENTAL_SCAN` 定时补算 + 双规则失效检测；ForecastController 统计基线预测 + 模型注册表。

用户确认决策（继承）：① 实时冲击链=原生 Kafka 事件+定时补算（不做流式）② 告警预留 faultContext，**复盘聚合口径本 Phase 定稿**：按 reviewTag 聚合心智层事件 → 三表版本时间线重建 ③ 时间回放已确认实施（belief 版本链，V129 `snapshot_version` 字段启用）。

**本指令拆两单串行**：
- **P3a 反事实推演核心**：推演契约 + CounterfactualSimulator（do(A) 干预 + 蒙特卡洛）+ `POST /api/v1/cognitive/counterfactual` 端点 + 性能压测
- **P3b 业务落地与时间回放**：belief 版本回放端点 + `dccheng-cognitive-group` 订阅方（失效→作废 run）+ sc001 SAFEGUARD 贯通 + 复盘聚合端点

**硬门禁**：P3a 未过验收（reviewer PASS + 编译绿 + 压测达标 + 复现性验证）不得启动 P3b。

## §实现决策（编码前裁定，验收按此核对）

1. **指标联动公式选"轻量线性响应模型"（不选既有 Simulator）**：`ScenarioSimulatorServiceImpl.simulate` 走 LLM Agent completion，违反"LLM 零参与认知计算"红线，仅作语义参照。推演链路纯 Java：
   - **输入装配**：interventions 各 `variableName+domain` → 读 belief 当前版本分布（`BeliefStore.findLatest`）；缺 distribution 的变量 400。假设有效性过滤：domain 下 `status=VALID` 假设列表随结果 `assumptionRefs` 透出（失效假设不进入推演前提，计入 `excludedAssumptions`）。
   - **数值映射**：baseline 参数携带 `outcomeValues`（`{variableName: {outcome: 数值}}`）与可选 `weights`（`${variableName}` → 1.0 默认）；outcomeValues 缺省的 outcome 取序数 ×1.0。
   - **do(A) 语义**：`SET` → 该变量采点后强制覆写为 `value`（单点化，概率质量=1）；`DELTA` → 该变量全部 outcome 数值 `+ value`（相对基线平移）。
   - **蒙特卡洛**：`java.util.Random(seed)`（seed 缺省 42，可传入保证可复现）N 次；每次对**全部** belief 输入变量按分布概率采样 outcome（非干预变量走分布，干预变量按 do(A) 施加），`metric_t = Σ_i w_i · x_{i,t}`（基线/干预同 seed 流成对采样，配对消偏）。
   - **风险四指标**（干预组相对基线组）：
     - `expectedBenefit = mean(interv) − mean(base)`
     - `lossProbability = count(metric_interv_t < metric_base_t) / N`（配对逐样本）
     - `maxDrawdown = max(0, median(interv) − min(interv))`
     - `volatilityRange = [p05(interv), p95(interv)]`
   - **敏感性 Top3**：对每个输入变量单独做 `±5%`（SET）/ `±max(1, |1%·range|)`（DELTA）扰动重算 `E[metric_interv]`，按 `|ΔE|/|扰动量|` 降序取 Top3 `{variable, sensitivity}`。
2. **三滤波器零新增登记**（代码勘察实证）：`/api/v1/cognitive/**` 已被 SecurityConfig permitAll（L96）+ ClearanceInterceptor 豁免（L112）+ whitelist 覆盖；`VersionPrefixRewriteFilter.V1_REWRITE_MAP` 无 cognitive 条目=KEEP → counterfactual / replay / mental-reviews 均零新增。
3. **counterfactor 审计**：推演仅读不写，但按指令要求经 EventBus 发 `ecos.audit` 一条 `cognitive.counterfactual` 留痕（detail 含 variable/sampleCount/seed），降级 WARN 不阻塞。
4. **P3b-T2 订阅方形态**：Kafka 路径下 `EventBusService.subscribe` 仅内存注册（`KafkaEventBusServiceImpl` L94 实证），真实消费必须 `@KafkaListener`——参照 kb-engine `EcosOntologyEventConsumer` 先例（`@KafkaListener(topics=KafkaTopics.COGNITIVE, groupId="dccheng-cognitive-group")`，String 入参 + ObjectMapper 解析 + try/catch 吞异常打 WARN 不堵分区）。消费逻辑单一实现 `handleInvalidation(payload)`，同进程内失效动作（`CognitiveHypothesisService.invalidate` 本 JVM 触发时）经 EventBus Kafka 发布后由 listener 消费（自发自收，Kafka 单点驱动保证幂等去重以 `eventId+runId` 落 impact 表）。
5. **P3b-T2 落库留痕**：新增 `V130__ecos_cognitive_run_invalidation.sql`（只加表，手工 docker cp 执行，与 V123~V129 先例一致；Flyway 禁用）。表 `ecos_cognitive_run_invalidation`（event_id/hypothesis_id/run_id/superseded_at/detail + 审计六列；uniq 幂等 `(event_id, run_id)`）。
6. **P3b-T3 落库列复用**：`ecos_scenario_run`（V125）无 counterfactual 专列——SAFEGUARD 四指标 + `assumptionRefs` 落 `simulation_result` JSONB（只增键不改结构）；`run_type='SAFEGUARD'`；superseded 联动改 `status='SUPERSEDED'`（status VARCHAR(32) 足够，`idx_scenario_run_status` 既有索引覆盖）。
7. **P3b-T4 复盘口径定稿**：确认 Kafka 事件**未落 PG**（全仓无 `ecos.audit` 落库 consumer；cognitive 三表只有状态无事件）→ 口径采用**基于三表版本链重建**（belief 版本时间线 + hypothesis 失效时点 + evidence 登记时点 + V130 impact 行），结构 JSON 输出；不在本 Phase 给事件加落库表（避免加塞）。
8. **replay 只读语义**：`GET /api/v1/cognitive/beliefs/{variable}/{version}/replay?domain=&interventions=&outcomeValues=&seed=`——按指定历史版本分布（`findIfExists(variable,domain,version)` 新增 Store 只读方法）+ **当时有效假设**（`invalid_at IS NULL OR invalid_at >= 该版本 update_time`）重算 counterfactual；全程 0 写；interventions/outcomeValues 为可选 query 参数（JSON 串，同参数即同结论）。
9. **workspace 依赖**：`ScenarioRunService`/`DcchengClient` 仅新增（ALLOWED_RUN_TYPES 增加 SAFEGUARD 为白名单集合扩充，既有 4 类型行为 0 变化；DcchengClient 加 `counterfactual(Map)` 方法，既有 4 方法不动）。workspace-impl 不引 spring-kafka（consumer 归 cognitive-engine-impl，workspace 侧零 Kafka 依赖）。

## §禁止清单（继承铁律 §5.1 + Phase 1/2 沿用 + 本单特有）

1. 单指令 ≤5 Task 不许加塞；不跨 Phase 预创建（前端 CognitionPanel 归 Phase 4 禁碰）；前端零改动
2. 不改既有 API 路径签名；不新建 Maven 模块/Docker 容器；不引 ML/Python/规则引擎
3. 定时任务走 runtime-task；跨模块共享走 common-api；**LLM 零参与认知计算**（CounterfactualSimulator/BeliefReplayService/MentalReviewService 0 处 LLM 引用）
4. `mvn install` 非 compile；`"-Dmaven.test.skip=true"` 带引号；编译前查 8080 旧 gateway 进程先停（当前 pid 89988）
5. 写操作发 `ecos.audit`；clean commit 风格 `feat(认知引擎): PMO-59-P3a ...` / `feat(认知引擎): PMO-59-P3b ...`；批次外文件（`_p2b_gateway_v4.log` 等）保持原样不提交
6. `ScenarioRunService` 修改必须向后兼容：既有 DIAGNOSE/FORECAST/SIMULATE/STRATEGY 行为零变化，curl 回归原有 runType 一条确认
7. Store 层 0 `SELECT *`、0 无条件 UPDATE、全查询 `is_deleted=0`（跟齐 V127~V129 风格）；JdbcTemplate 构造器注入
8. Controller 入参出参强类型 DTO（Map 例外仅 P2a/P2b 已登记场景沿用）；`ApiResponse` 统一返回体；异常抛 `BusinessException`（COG-4xx 前缀）
9. 新 DDL 仅 V130 一张表（只加不删）；手工 docker cp 法执行（禁 PS 管道给 psql 注 BOM）

## §P3a Task（≤5）

| Task | 文件/路径 | 操作 | 验收 |
|:--|------|------|------|
| T1 | `cognitive-engine-api/.../dto/counterfactual/CounterfactualRequest.java` + `CounterfactualResult.java` | 推演契约（@Data）：Request{scenarioId?, domain 必填, variableName 必填, baseline{outcomeValues Map<String,Map<String,Double>>?, weights Map<String,Double>?}, interventions List<Intervention{variableName, op SET/DELTA, value}>, sampleCount 默认1000 上限5000, seed 默认42}；Result{requestEcho, baselineMean, fourMetrics{expectedBenefit,maxDrawdown,lossProbability,volatilityRange[2]}, sensitivityTop3 List<{variable,sensitivity}>, assumptionRefs, excludedAssumptions, sampleCount, seed} | api 模块 `mvn install -pl` 绿 |
| T2 | `cognitive-engine-impl/.../service/mental/CounterfactualSimulator.java` | 按 §实现决策 1 实现（纯 Java，`java.util.Random(seed)` 可复现；belief 分布概率加权 outcome 采样；线性响应 Σ w·x；四指标 + Top3；0 LLM 引用） | 同参数双跑（同 seed）结果逐位一致；异 seed 分布形态一致数值可差异 |
| T3 | `cognitive-engine-impl/.../controller/CounterfactualController.java` + `cognitive-engine-impl/AGENTS.md` 端点清单 | `POST /api/v1/cognitive/counterfactual`（强类型 DTO，只增不改；三滤波器按 §实现决策 2 核对零新增）+ 审计发 `ecos.audit`（cognitive.counterfactual）| curl 经 8080 走通，返回四指标 + Top3 + assumptionRefs；ecos.audit 实读 1 条 |
| T4 | 性能压测（内联 PowerShell 循环 curl，不留脚本文件） | sampleCount=1000/5000 两档各 10 次经 gateway :8080 计时 | 1000 档 P99<3s、5000 档 P99<10s（超 10s 声明异步降级方案并给理由）；P50/P99 表入验收记录 |
| T5 | `docs/plans/api-contract.md` §3.4 追加 + `docs/09-PMO指令/PMO-59-认知引擎心智层-Phase3验收记录.md` | api-contract 回填实际登记（含 DcchengClient 契约）；验收记录含压测表 | 全量编译绿 + Gateway 存活 |

## §P3b Task（P3a 验收 PASS 后启动，≤5）

| Task | 文件/路径 | 操作 | 验收 |
|:--|------|------|------|
| T1 | `BeliefStore` 增 `findIfExists(variable,domain,version)` + `service/mental/BeliefReplayService.java` + `CognitiveBeliefController` 增 `GET /{variable}/{version}/replay` | 时间回放（§实现决策 8）：指定历史版本 + 当时有效假设过滤重算，输出含 `replayedVersion`/`believedDistribution`/`assumptionsAtTime`/四指标，与当前版本对比段；0 写 | curl 重放 v2 与当前最新版本（同参）输出可复现且数值随版本分布变化；`psql` 核对 0 行更新（只读实证） |
| T2 | `service/mental/CognitiveInvalidationConsumer.java`（@KafkaListener group=`dccheng-cognitive-group`）+ `V130__ecos_cognitive_run_invalidation.sql`（手工执行） | 假设失效事件（Kafka `COGNITIVE_HYPOTHESIS_INVALIDATED`）→ 关联作废：`ecos_scenario_run` 中 `simulation_result->'assumptionRefs'` 含该假设且 status=SUCCEEDED* 的 run → `status='SUPERSEDED'` + impact 表留痕（eventId+runId 幂等） | 触发失效（复用 hypotheses/{id}/invalidate）→ psql 可见 run status=SUPERSEDED + impact 表 1 行；重复投递幂等（0 重复行） |
| T3 | `workspace/.../scenario/ScenarioRunService.java`（+SAFEGUARD 分支/白名单）+ `DcchengClient.java`（+counterfactual 方法） | SAFEGUARD 经 DcchengClient 调 counterfactual（§实现决策 6：结果+assumptionRefs 落 simulation_result）；**向后兼容回归**：既有 runType 行为零变化 | curl POST runs `runTypes=["SAFEGUARD"]` 含燃油价格+15%干预 → ecos_scenario_run SUCCEEDED 含四指标+assumptionRefs；回归一条 DIAGNOSE run 行为与 P2 口径一致 |
| T4 | `controller/MentalReviewController.java` + `service/mental/MentalReviewService.java` | `GET /api/v1/cognitive/mental-reviews?since=&tag=`（§实现决策 7：三表版本链+impact 表重建；tag 校验对 `P2b-mental-layer-review`） | curl `tag=P2b-mental-layer-review&since=<P2b起日>` 输出结构完整（beliefs/hypotheses/evidence/impact/summary）；非法 tag 400 |
| T5 | `docs/plans/api-contract.md` §3.4 追加 P3b 登记 + Phase3 验收记录补 P3b 段 | commit hash + curl 原文 + psql 摘要 + 向后兼容回归证据 | 全量编译绿 + 文档齐全 |

## §环境要点

- Windows PowerShell 5.1；编译：`& "D:\JavaProjects\env\apache-maven-3.9.11\bin\mvn.cmd" -f "D:\workspace\javaprojects\ECOS\ecos_backend\pom.xml" clean install "-Dmaven.test.skip=true" -B`（编译前 `Get-NetTCPConnection -LocalPort 8080 -State Listen` 查旧 gateway pid 先停）
- Gateway 启动（本检出 `_win_tasks/` 不存在，Phase 2 实证等价命令）：`java -Xms512m -Xmx2g -jar gateway/target/gateway-1.0.0-SNAPSHOT.jar --spring.profiles.active=enterprise`（JWT 纯 base64 env 注入，正则提 PEM body——Phase 2 实证坑 3.2）
- curl body：`[IO.File]::WriteAllText($tmp, $json, (New-Object Text.UTF8Encoding $false))` 无 BOM + `curl.exe -s --data-binary "@$tmp"`，临时文件 `$env:TEMP\ecos-curl\` 用完删；`ecos_*` 表查询 `docker exec ecos-postgres psql -U postgres -d sys_man -c "..."`（简单查询走 -c 无 BOM 风险）
- sc001 贯通 seed（curl 注册，非 DDL）：belief `fuel_price_level`（domain=aviation，分布 low/base/high）+ hypothesis `HYP-SAFEGUARD-001`（metric_ref=fuel_price_level，statement 含 sc001 运行合规语义）；SAFEGUARD 干预 `fuel_price_level DELTA +value`（"燃油价格+15%" 按 outcomeValues 映射量纲）

## §最终交付（两单各自返回）

① commit hash 列表 ② 全量编译结果 ③ curl/压测/psql 验收原文摘要 ④ 独立 code-artifact-reviewer 审查结论（铁律 §2.4 安全集成计数、命名、依赖方向、只增不改、向后兼容回归证据）⑤ 残留风险 ⑥ Phase 4 入口建议（人机干预前端 CognitionPanel 推演/回放入口预估 + IWarnLogService 落库通道）
