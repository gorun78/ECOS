# PMO-59 Phase 2 指令: 认知引擎心智层 P1 实现（两单串行）

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../.trae/rules/架构铁律.md)
> 来源: 肖国荣 | 日期: 2026-09-14
> 铁律: ① LLM 零参与认知计算（纯 Java 规则/贝叶斯）；② 写操作必发 Kafka `ecos.audit`（EventBus）；③ 时间回放 Phase 3 实施，本 Phase 仅保 `snapshot_version` 预留在位

## §背景

Phase 1（P0）已交付三表 DDL（V127~V129，含 seed）+ common-api VO + cognitive-engine-api 两接口契约 + api-contract 端点/topic 预登记（验收记录见 `PMO-59-认知引擎心智层-Phase1验收记录.md`）。本指令落地 Phase 2 实现，拆两单串行：

- **P2a 持久化与端点**：三表存取层 + 强类型入参 + 三组 REST 端点 + 三滤波器核对 + ADR-9 正式落盘与铁律同步修订
- **P2b 事件与失效检测**：Kafka `ecos.cognitive` 事件生产 + runtime-task 定时补算 + 假设失效自动检测 + 不确定性判断贝叶斯更新/人工覆写

**关键实现决策（T1 风格裁定，指令内声明）**：cognitive-engine-impl 既有存储实现（PMO-51 `ModelRegistryService`）为 **JdbcTemplate 显式列名**风格，模块无 MyBatis mapper 包基建（gateway `@MapperScan` 亦未登记 cognitive 包），故本单 Store 层**跟齐 JdbcTemplate 既有风格**（包名命名沿用 `*Store` 语义，避免与 MyBatis Mapper 概念混淆），不引入 MyBatis Mapper 基建。铁律合规：0 个 `SELECT *`、0 个无条件 UPDATE/DELETE、全查询 `is_deleted=0` 逻辑删除过滤。

## §禁止清单（继承铁律 §5.1 + Phase 1 沿用）

1. 单指令 ≤5 Task 不许加塞；不跨 Phase 预创建（Phase 3 反事实/时间回放代码一律不碰）
2. 不改既有 API 路径签名；不新建 Maven 模块/Docker 容器；不引 ML 框架
3. `mvn install` 非 compile；`"-Dmaven.test.skip=true"` 带引号（隔离 kb-engine-impl 既有测试编译错误）；编译前查 8080 旧 gateway 进程，占用先停
4. LLM 零参与认知计算；写操作发 Kafka `ecos.audit`（走 runtime-event EventBus，禁自建 KafkaTemplate — 铁律 §2.5）
5. clean commit 批次内收口；批次外文件不提交
6. Controller 入参出参禁止 Map（例外：invalidate 单字段 reason 用 `Map<String,String>` 最小载体——与 api-contract 预登记 body 一致；若 Reviewer 判违可换强类型 DTO，路径不变）
7. 授权例外（审查需知悉）：按铁律 §2.5 runtime 公共底座"补强而非自建"，本单在 runtime-monitor 补注册 `WarnLogServiceImpl` 既有实现的 `IWarnLogService` Bean（该 impl 原本无 Spring 装配，属 runtime 缺口补强，不落引擎表不动 runtime 既有文件标识）

## §P2a Task（≤5）

| Task | 文件/路径 | 操作 | 验收 |
|:--|------|------|------|
| T1 | `cognitive-engine-impl` `service/EvidenceStore.java` + `HypothesisStore.java` + `BeliefStore.java` | 三表存取层（**JdbcTemplate**，风格裁定见 §背景；显式列名与 V127~V129 DDL 逐列一致；JSONB `?::jsonb` 绑定；行→VO 归一化） | 列名与 DDL 逐列一致；编译绿 |
| T2 | `cognitive-engine-api` `dto/{EvidenceSaveDTO, HypothesisSaveDTO, BeliefSaveDTO}.java`（@Data）+ api/impl POM 补 lombok(optional) | 强类型请求 DTO（discreteDistribution 字段，Javadoc 声明 prob 和=1 由 Service 校验）；VO 复用 common-api（Lombok 按现状统一——common-api VO 手写 getter/setter 不动） | `mvn install -pl engine/cognitive-engine/cognitive-engine-api` 绿 |
| T3 | `cognitive-engine-impl` `service/Cognitive{Evidence,Hypothesis,Belief}Service.java` + `controller/Cognitive{Evidence,Hypothesis,Belief}Controller.java` | 三组端点（evidence `/api/v1/cognitive/evidence`；hypotheses `/api/v1/cognitive/hypotheses` + `{id}/invalidate`；beliefs `/api/v1/cognitive/beliefs`）；信念接口落库部分（注册/列表/详情/失效时间写）；prob 和=1 强校验（容差 1e-6）；evidence_code/hypothesis_code 幂等；写操作发 ecos.audit | curl 经 gateway:8080 三组端点 CRUD 全通 + psql 落库核对 |
| T4 | 三滤波器核对 + `cognitive-engine-impl/AGENTS.md` + `cognitive-engine/AGENTS.md` 端点清单 | 核对结论（**零代码改动**）：VersionPrefixRewriteFilter `/api/v1/cognitive/` 无 V1_REWRITE_MAP 条目=KEEP 无误伤；SecurityConfig permitAll 既有 `/api/v1/cognitive/**`+`/api/cognitive/**` 双通配已覆盖；ClearanceInterceptor 既有 `/api/v1/cognitive/` 豁免已覆盖；`auth.whitelist.paths` 既有 `/api/v1/cognitive/**` 已覆盖 → **均无新增登记**；AGENTS 端点清单更新 | curl 经 8080 全通（0 个 403/404） |
| T5 | `docs/plans/current-plan.md` ADR 区新增 **ADR-9** + `.trae/rules/架构铁律.md` §0.3 表 cognitive 行 + §3.3 修订 | 三档口径（推理结果不落盘/模型资产落盘/心智状态落盘）；**仅改"cognitive 不新增 DB 表"两处表述，其余条款零改动** | 铁律文件与三处 AGENTS.md 口径 grep 一致 |

## §P2b Task（P2a 验收 PASS 后启动，≤5）

| Task | 文件/路径 | 操作 | 验收 |
|:--|------|------|------|
| T1 | `cognitive-engine-impl` `service/mental/MentalEventTypes.java` + `MentalEventPublisher.java` | 强类型事件包（HypothesisInvalidatedEvent/BeliefUpdatedEvent/EvidenceConflictEvent，含 eventId/timestamp/faultContext 周一故障复盘预留字段）+ Publisher（EventBus 发 `ecos.cognitive`，try/catch 吞异常 WARN 不阻塞） | kafka-console-consumer 查到消息 |
| T2 | `service/mental/BayesianUpdater.java` + `service/mental/MentalScanTask.java` + `config/CognitiveMentalConfig.java` + gateway `application.yml`（`spring.kafka` + `ecos.event.kafka.enabled` + `ecos.cognitive.scan-interval` 三行**仅增**） | ① 纯 Java 确定性似然规则（confidence 分桶 0.95+/0.8/0.5/0.3 + fact/value 关系匹配，无 LLM）加权贝叶斯更新；② runtime-task 定时补算（UsageCollector 同款骨架：registerParser/Executor + schedulePeriodicTask，周期可读配置，默认 10min）；③ `IWarnLogService` Bean 注册（runtime 补强） | runtime-task 注册日志 + 手动触发一代日志 + Kafka 消息可查 |
| T3 | `service/mental/MentalInvalidationDetector.java` + `CognitiveHypothesisService.invalidate` 事件/告警接入 | 证据与假设 rule 匹配（refuting 含/CONFLICTED/metric 冲突）→ INVALIDATED + invalid_at + Kafka 事件 + 告警（warn() 正文含 faultContext 结构化字段）；人工失效端点同链路 | 构造冲突证据 → 假设 status→INVALIDATED + 事件 + 告警可见 |
| T4 | `controller/CognitiveBeliefController` 增 `POST /{variable}/update-by-evidence` + `PUT /{variable}/override`（api-contract 预登记路径，只增不改） | 新证据 → 贝叶斯 → version+1 + last_evidence_id；override 置 manualOverride=true + reason（覆写后模型更新让位专家——检测器与后续更新以 manualOverride 为守卫）；domain 参数强制 | curl 新证据触发 version+1 且分布和=1；覆写端点生效 |
| T5 | api-contract §3.4 预登记回填实际登记 + `docs/09-PMO指令/PMO-59-认知引擎心智层-Phase2验收记录.md` | 7 端点实际登记 + 验收记录（commit hash + curl 原文 + psql 摘要 + Reviewer 结论） | 全量编译绿 + Gateway 存活 + 文档齐全 |

## §环境要点

- 编译前 `Get-NetTCPConnection -LocalPort 8080` 查旧 gateway 进程，占用先停（Phase 1 已实证 target 锁坑）
- Windows PowerShell 5.1；curl 用 `curl.exe -s`；复杂 body `[IO.File]::WriteAllText($tmp, $json, [Text.UTF8Encoding]::new($false))` 无 BOM + `--data-binary "@$tmp"`，临时文件放 `$env:TEMP\ecos-curl\` 用完删
- Gateway 启动：fat-JAR `java -jar gateway/target/gateway-1.0.0-SNAPSHOT.jar --spring.profiles.active=enterprise`（本检出 `_win_tasks/` 不存在，按 Phase 1 实证命令等价启动）
- Kafka 容器 ecos-kafka:9092 已 healthy；`spring.kafka` 未配时 EventBus 降级内存路径，P2b 开启 `ecos.event.kafka.enabled=true` + `spring.kafka.bootstrap-servers` 后走真 Kafka（MemoryEventBusFallbackCondition 保证二者互斥不双 Bean）

## §最终交付（每单各自返回）

① commit hash 列表 ② 全量编译结果 ③ curl/Kafka 验收原文摘要 ④ 独立 code-artifact-reviewer 审查结论（铁律 §2.4 安全集成计数、命名、依赖方向、只增不改）⑤ 残留风险 ⑥ Phase 3 入口建议
