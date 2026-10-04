# 详细设计 06 — 智能域（ai-engine 火·W + llm-gateway 器·LLM 出口）

> 来源: AI Agent（资深系统设计师视角，用户指令"分册按子系统顺序生成各子系统的设计文档…不符合要求的一定要改"）
> 日期: 2026-09-29 | 版本: v1.2（v1.2 2026-09-29：随**需求检视报告 §十四.1** 批量批准定版——本册 **R-18 ②／R-19 ①+② 并行／R-20 ①／R-21 ①（含全量端口校正）／R-22 ①+③（严格顺序）／R-23 ①（一处例外）／R-37 ①／R-39 ①（A 单一自建 Runtime）／R-41 ②／R-47 ①／R-50 ①** 按推荐项批准；M0 子项不依赖批准。v1.1 新增 §八「与 W-Agent 接缝」，原 §八 顺延为 §九；接缝对象 = PRD-06 v1.2 §六 REQ-AI-06~11） | 责任人: AI Agent
> 上游依据: `docs/20-需求/需求检视报告-2026-09-28.md` **v2.8** §十二（Q1~Q14 + E2E 补记）+ §十三（R-1~R-17，**本册追加 R-18~R-23 于 §13.5**）**+ §十四.1（R-1~R-72 已于 2026-09-29 全量批准）+ §十四.3（Gate-1 已签字／Gate-2 已通过）**—— 依 Q12，本节即需求基线批准凭证；`docs/10-架构/架构设计说明书-2026-09-28.md` **v1.5**（§十一 C1~C121 已有条目 + §4.3 读写边界 + §5.2 runtime 四底座 + §八 非功能门禁）
> 主责 REQ: **REQ-AI-01（安全护栏接线，P0 红线）· REQ-AI-02（会话压缩/记忆治理）· REQ-AI-03（可观测性与 token 计量）· REQ-AI-04（场景受控 AI 工具集）· REQ-AI-05（错误处理规范）**（`PRD-06-ai-engine需求规格-2026-09-28.md`，1 P0 红线 + 3 P1 + 1 P2）
> 关联 REQ: REQ-PLT-21（llm-gateway 唯一 LLM 出口，PRD-01）、REQ-SEC-01~04（OPA 裁决/脱敏/审计，PRD-04/分册 01）、REQ-NF-04（traceId 全链透传）、REQ-FC-02/03/04（五个场景工具的后端供数，PRD-09）、REQ-COG-02/03（`forecast.explain` 与情景运行的上游，分册 05）、REQ-WS-03（场景工作台右栏 AI 面板，分册 07）
> 契约上游: PRD-06 §1.3 OPA 裁决载荷 + §1.5 四项验收 + §4.1 五工具注册表 + §5.1 七类错误矩阵；`docs/10-架构/架构设计说明书-2026-09-28.md` §4.3（ai-engine 写权限 = `ecos_ai`）、§七-3（ApiResponse 无 traceId 字段，分册 00 D.5.1 增量引入）
> 前置依赖: 分册 00（traceId/ApiResponse/两态寻址/ADR-15 承流）、分册 01（默认 DENY 与 `ecos.audit` Kafka 链，**本册 AI-01 是其唯一工程消费方**）、分册 05（R-13/R-14 Mind 落点、认知四端点、`ECOS-COG-*` 码表）、分册 09（FC-02 预测运行 results/evidence 端点）
> 关联 ADR: ADR-8（认知模型资产存储）、ADR-11（pgvector 基线）、ADR-14（确定性计算产物经 data-engine 落业务域）、ADR-15（制品口径 ≠ 承流口径，**本册是其最大承压点**）
> 规范锚点: `.trae/rules/架构铁律.md` v2.0（§2.4-4 操作授权 / §2.4-6 默认 DENY / §2.4-7 禁重复实现 / §2.4-8 安全集成强制卡 grep 计数 / §2.5-2 llm-gateway 唯一 LLM 出口 / §2.5-3 runtime-task 禁自建调度 / §2.5-7 runtime-event 禁自建 KafkaTemplate / §0.2.1 禁 LLM 生成数值）；`.trae/rules/数据库访问规范.md` v1.2（MC01/MC02/MC03/MC05/ST07/DR06~DR08/§四附则1 裸表名=FAIL/§五 三层纪律）；`.trae/rules/后端开发规范.md` v1.1（§九 runtime 底座 / §十 配置单源）；`.trae/rules/前端开发规范.md` v1.1
> **编号接续**: 分册 00 = W01~W15→C16~C21；01 = W29~W40→C22~C29；02 = W41~W66→C30~C50；03 = W67~W89→C51~C71；04 = W90~W114→C72~C96；05 = W115~W139→C97~C121；**本册 = W140~W164 → C122~C146**

---

## 〇、本册定位与判定基线

### 0.1 本册覆盖的部署制品与实测承载

| 制品/模块 | 端口 | 实测承载 | 证据 |
|:--|:--:|:--|:--|
| `engine/ai-engine`（api/impl/boot 三模块） | boot 18084 | **32 个 `@RestController` / 157 个 mapping 注解 / 132 个 Java 文件**；逻辑上属"火·W"场景编排引擎，物理上被 gateway fat-JAR 与 aiming 双宿主 | `grep -rl "@RestController" engine/ai-engine/ai-engine-impl/src/main`（32）、`grep -rc "@(Get\|Post\|Put\|Delete\|Request)Mapping"`（157） |
| `runtime/llm-gateway` | — | **26 个 Java 文件 / 3392 行 / 0 个 Controller / 0 个测试文件**（`src/test` 目录不存在）；内含**两套并行门面**（`runtime.llm.gateway.LLMGateway` 与 `runtime.llm.LLMGatewayService`） | `find runtime/llm-gateway/src/test -type f` → 空 |
| `services/aiming` | 18084 | 制品空心（分册 05 §0.1 实测：3 个文件、0 自有 Controller），**ai-engine 的 32 个 Controller 全靠它 `@ComponentScan` 才在 18084 上可达** | 分册 05 X-1；`AimingServiceApplication.java:110` 显式排除 `runtime.llm.repository` |
| `ai-engine-boot` | **18084** | 与生产 aiming **同端口**（文档宣称 boot 态端口自动 +1000=19084，实配未改）；`@ComponentScan` 含**不存在的历史包** `com.chinacreator.gzcm.cognitive` | `engine/ai-engine/ai-engine-boot/src/main/resources/application.yml:2` |
| AI 运行态数据 | — | **`ecos_ai` 50 张表全 0 行**（空壳）；真实数据全在 `public`（`sys_agent_session` 18 行 / `sys_agent_message` 6 行 / `ecos_agent_memory` 15 / `ecos_agent_registry` 16 / `sys_agent_profile` 5） | 本册 X-45~X-50（只读 count 探针） |

### 0.2 本册要解决的核心命题

一句话：**PRD-06 五项主责能力（AI-01 护栏 / AI-02 压缩 / AI-03 可观测 / AI-04 场景工具 / AI-05 错误处理）在本域的实测完成度是"一条都不达标"——护栏只对 SQL 型工具生效且非 SQL 工具完全无裁决、输入输出护栏 fail-open 且从不问 security-engine、压缩是"条数阈值 + 200 字截断 + 物理 DELETE"、traceId 是进程内 8 位随机串且从不透传、五个场景工具 0 实现、七类错误 0 实现、`GUARDRAIL_*`/`LLM_USAGE` 两个事件类型全仓零命中。**

但本册与分册 05 的关键差别在于：**咽喉已经存在，缺的是咽喉上的那道闸门。** `ToolExecutorService.execute(...)` 事实上已是唯一工具执行入口（`AgentLoopService.java:1093` 是唯一调用点），且其上已装有 `AgentToolSqlSecurityGate`（真调 OPA evaluate、真 fail-closed）与 `AiSecurityEngineClient`（可复用的 security REST 客户端）。因此 AI-01 的正确改造**不是新增一个 `ToolDispatcher` 类**（PRD-06 §1.2 字面写的名字，全仓 0 命中），而是**把既有唯一入口的裁决面从"仅 SQL"扩到"全工具类型"** —— 新建平行类反而会造出第二个可绕过的入口。

```
① 护栏覆盖面（P0 红线）：AgentToolSqlSecurityGate 只覆盖 toolType='SQL' 与 registry 中带 sql 参数的工具
   （ToolExecutorService.java:499-513 / :536-541）；invoke_rest / read_file / write_file / search_files /
   patch / delegate_to_agent / getCurrentTime / calculateRisk 共 8 类工具**不经任何 OPA 裁决**。
   sandboxReview 只在 sandbox_mandatory=true 且正则命中"高危"时触发，异常 → return null 放行
   （ToolExecutorService.java:234-239）→ 非 fail-closed。
② 护栏可用性：输入/输出护栏（AgentLoopService.java:133-146 / :276-286）是 `@Autowired(required=false)` +
   `catch (Exception){log.warn}` 双重 fail-open；GuardrailsServiceImpl 为纯本地正则，从不调 security-engine
   → 与 PRD-06 §1.1"护栏不可用时默认拒绝"和 §1.4 审计要求全部不符。
③ LLM 出口：`engine/ai-engine/.../service/LLMProvider.java` 在引擎侧自建 Provider 抽象（违 §2.5-2），
   AgentLoopService.java:581 与 oag/ReasoningEngineNode.java:144 都写"优先直调 Provider"；实测
   `implements LLMProvider` **0 命中**（接口无实现）→ 该分支当前不可达，但 `:597 request.setApiKey(deepseekApiKey)`
   仍把裸 key 从引擎侧塞进 gateway，绕开了 llm-gateway 自己的 `SecurityEngineBridge.resolveSecret`。
④ 计量：`sys_agent_call_log` 两 schema 均 0 行；llm-gateway 的 `AgentCallLogRepository`/`ProfileConfigRepository`
   是 @Mapper 接口，而 aiming:110 / datanet:68 / sysman:74 **三处显式把 `runtime.llm.repository` 排除出扫描**
   → 计量链路在承流进程里物理断裂；`LLM_USAGE` 全仓 0 命中。
⑤ traceId：`AgentTracer` 是 `ConcurrentHashMap` + `UUID.substring(0,8)` 的进程内轨迹（MAX_TRACES=1000 竞态淘汰），
   仅 `log.debug`；`X-Request-Id`/`MDC`/`EVT=`/`promptTokens` 全仓 0 命中 → PRD-06 §3.1 与 §3.3 回放从零起建。
⑥ 压缩：`AgentSessionService.java:40 COMPRESS_THRESHOLD=20`（**条数**而非 PRD 的 token×60%）、
   `:273-290` 200 字截断拼接（非 LLM 结构化摘要，引用类信息必丢）、`:294 DELETE FROM sys_agent_message`
   （**物理删除**，与"只加不删"与证据链保全双违）；`SessionManagerImpl.java:40` ConcurrentHashMap 且
   closeSession 不移除 → 无界增长。
⑦ 场景工具：`forecast.query`/`forecast.drilldown`/`forecast.explain`/`scenario.copyCompare`/`action.createDraft`
   五个 + 引用块模板（`forecastRunId`/`caliberId`/`asOfTime` 三个占位符名）全仓 **0 命中**；
   当前内置工具是 7 个通用能力（ToolRegistry.java:162/229/305/337/395/451/514），与场景需求无交集。
⑧ 错误处理：七类错误（LLM_TIMEOUT/…/TOOL_BACKEND_ERROR）**0 实现**；`ErrorCodeMapper.java` 是死代码
   （全仓仅自身声明 1 处引用）；ai-engine-impl main 里 `catch (Exception` **187 处**、controller 里
   `e.getMessage()` **67 处**；`AgentLoopService.java:1108-1119` 工具异常时返回
   `success=false` 却带 `content="{\"status\":\"success\"…}"` 的自相矛盾 mock → LLM 会把失败读成成功。
⑨ 门禁盲区：`ModuleDependencyArchTest.java:77-95` 的 16 条 candidatePaths **不含 `runtime/llm-gateway`**
   → 全部 `noClasses()` 规则对 llm-gateway 不可见；本域 11 处自建线程/线程池
   （含 `AgentEvaluator.java:281 new Thread(...).start()`、llm-gateway 自己的 `AgentSchedulerImpl.java:56`）
   全部逃逸 `businessMustNotSelfSchedule`。
⑩ 数据层：`ecos_ai` 50 张表 0 行 / 26 个 jsonb 列（违 MC02）/ 全 PK `VARCHAR(64)`（非 MC01 的 36）/
   DR06~DR08 基线列 0/50 / `ecos_decision_case.id` BIGINT nextval（违 MC01）/ `agent_cost` 25 个空分区；
   真身 5+ 张在 `public`，另有 8 组 **`ecos_ai`↔`public` 双镜像**；代码全靠 `V47` 的
   `ALTER DATABASE … SET search_path` 才能让裸表名继续跑（单点依赖，换库即崩）。
⑪ 承流：BFF `server.ts` 对 `text/event-stream` **整体缓冲**（`await upstream.text()`）→ 生产流式必挂；
   网关 `request-timeout: 60s` 与 SSE `300_000ms` 打架；前端 **9 条死/错配 AI 调用**（3 条来自 live 文件），
   其中 EventSource 指向后端 POST-only 的端点（物理不可能成功）。
⑫ 显示面真实性：约 **9 900 行死 AI 界面**（含整条护栏链与整目录 knowledge 组件）；live 对话页用
   `buildMockAgentReply` **客户端伪造 LLM 回复**、审计面板永久 mock；token 显示 `Math.random()*500`；
   护栏面板由 locale 驱动；`oagPlaygroundStream` 硬编码 `userId:'admin'` 且不带鉴权头。
⑬ 验收地基：全仓 `@SpringBootTest` **0 命中**、llm-gateway/aiming 各 0 测试、OpenAPI 制品 0、
   `playwright.config.ts` 与 `ecos-tests/package.json` 不存在 —— 本册所有集成形态验收都需先建底座。
```

本册主任务：
1. **建成全工具类型的裁决咽喉**（在既有唯一入口上扩展，禁新增平行 Dispatcher），带 obligations 脱敏回传与 `GUARDRAIL_EVAL` 审计；
2. **把输入/输出护栏改为经 security-engine 且 fail-closed**，删除本地正则冒充护栏的形态；
3. **把 LLM 出口收一**（删引擎侧 `LLMProvider` 抽象、双门面归一、api-key 不再由引擎侧携带）；
4. **按 PRD-06 §2.1 重写会话压缩**（token 阈值 + LLM 结构化摘要 + 引用保护 + 逻辑删除）；
5. **建成 traceId/MDC/结构化日志/`LLM_USAGE` 计量与配额**，并修复 ArchUnit 宇宙盲区使计量与调度可门禁化；
6. **实现 5 个场景受控工具 + 引用块后校验**（AI-04，与分册 09 FC-02 接缝）；
7. **实现 7 类错误分类与有界退避**（AI-05），消灭 mock-success；
8. **AI 数据层归位**（`ecos_ai` 空壳 vs `public` 真身 → R-19 裁决），DDL 从 **V200** 起；
9. **打通流式承流并清死调用/死界面**（BFF SSE 直通 + 9 条错配 + 约 9 900 行死码 + 客户端 mock 主路径清除）；
10. **建 AI 测试底座**（全仓 `@SpringBootTest` 0 命中 → 红线验收当前无载体），并据此产出本域首份契约文档；
11. 登记 **R-18 ~ R-23** 六项新裁决。

### 0.3 判定基线

| # | 基线 | 来源 |
|:--:|:--|:--|
| B-1 | 工具执行**唯一入口** = `ToolExecutorService.execute(...)`（既有事实咽喉）；入口内**全工具类型**强制经 security-engine OPA evaluate，不可用/超时/非明确 ALLOW → **默认 DENY**；裁决请求载荷字段以 PRD-06 §1.3 为单源，`subject/action/resource/environment` 四段齐全 | REQ-AI-01 + 铁律 §2.4-4/6 |
| B-2 | 脱敏/行过滤**只调 security mask 端点**，ai-engine 侧禁自实现；obligations 在工具结果回 LLM 之前执行 | REQ-AI-01 §1.3 + 铁律 §2.4-3/7 |
| B-3 | 每次裁决（ALLOW/DENY/FAIL_CLOSED）经 runtime-event 发 Kafka `ecos.audit`，eventType=`GUARDRAIL_EVAL`；引擎侧禁自建 `KafkaTemplate` | REQ-AI-01 §1.4 + 铁律 §2.5-5/7 |
| B-4 | **llm-gateway 是 LLM 唯一出口**：引擎侧不得持有 Provider 抽象、不得携带裸 api-key 进 gateway、不得 `new RestTemplate()` 打模型端点；计量点与 traceId 注入口都在 gateway 出口 | 铁律 §2.5-2 + REQ-PLT-21 |
| B-5 | 数值只来自确定性端点：AI 回答中的经营数值**必须**逐字来自工具返回，禁 LLM 复述改写；引用块缺失即拦截 | 铁律 §0.2.1 + REQ-AI-04 §4.2 |
| B-6 | 验收标识只允许可执行形态（`mvn -Dtest=类#方法` / `*.spec.ts` 用例名）；写"人工确认"或"Playwright 通过"= 虚假验收 | Q13 |
| B-7 | 存量数据只定性不擅自迁移；`sys_agent_message` 压缩改造涉及"历史消息是否物理删除"属 **R-20 ① 已批准**项 ⇒ 按"仅对新数据生效 + `message_count` 一次性回填对账"设计，存量 6 行不重建、不做兼容开关 | 用户裁定纪律 + R-12 同源 |

---

## 一、实测事实（X-1 ~ X-74）

> 全部条目带 `file:line` 或可复现探针；采集 2026-09-29。数据库侧一律只读（`information_schema` / `reltuples` / `count`），无 DDL/DML。本册对上游文档做了**三处事实更正**（X-6、X-21、X-24），凡与 ARCH_SPEC/PRD 记载冲突处，以本册实测为准并回写。

### 1.1 制品与装配（X-1 ~ X-8）

| # | 事实 | 证据 |
|:--:|:--|:--|
| X-1 | ai-engine-impl main 侧 **32 个 `@RestController`**、**157 个 mapping 注解**（含类级 `@RequestMapping`）、全模块 132 个 Java 文件 | `grep -rl "@RestController" engine/ai-engine/ai-engine-impl/src/main \| wc -l` → 32；`grep -rc "@\(Get\|Post\|Put\|Delete\|Request\)Mapping"` 合计 → 157；`find engine/ai-engine -name "*.java" -not -path "*/target/*" \| wc -l` → 132 |
| X-2 | llm-gateway **29 个 Java 文件 / 3 392 行 / 0 个 Controller / `src/test` 目录不存在（0 个测试）** | `find runtime/llm-gateway/src/test -type f` → 空 |
| X-3 | llm-gateway 内含**两套并行 LLM 门面**：`runtime.llm.gateway.LLMGateway(+Impl)`（新式，带 provider 目录与 `DEFAULT_BASE_URLS`）与 `runtime.llm.LLMGatewayService(+Impl)`（旧式，带 scheduler/session/profile/metrics 子域） | `runtime/llm-gateway/src/main/java/com/chinacreator/gzcm/runtime/llm/gateway/LLMGatewayImpl.java` vs `.../runtime/llm/LLMGatewayServiceImpl.java` |
| X-4 | `AgentLoopService` **同时注入两套门面**：`:67 llmGatewayService`、`:70 llmGateway`；`LLMProvider` 亦注入（`:104 List<LLMProvider> llmProviders`，required=false） | `engine/ai-engine/ai-engine-impl/.../service/AgentLoopService.java:67,70,101,104` |
| X-5 | aiming 制品空心（3 文件 / 0 自有 Controller，分册 05 X-1 已证），**ai-engine 的 32 个 Controller 在 18084 上的可达性完全依赖 aiming 的 `@ComponentScan`**；`AimingServiceApplication.java:110` Yet 显式把 `runtime.llm.repository` 排除出扫描 | `services/aiming/src/main/java/.../AimingServiceApplication.java:110` |
| X-6 | **`ai-engine-boot/src/main/resources/application.yml:2 server.port: 18084` 与生产 `services/aiming/application.yml:3: 18084` 同端口**；AGENTS.md 宣称"boot 调试引擎端口自动 +1000"仅由 `start-backend.ps1` 在启动时改参实现，配置文件本身仍是 18084 → 任何直接 `java -jar` 起 boot 的路线都与 aiming 抢端口 | 两处 yml `server.port` 实测；`_win_tasks/start-backend.ps1` boot 端口 +1000 逻辑 |
| X-7 | 端口三元组冲突面扩大：**18086 被 kb-engine-boot / dccheng / agent-service 三处同时声明**；18081 = security-boot + sysman；18082/18083 同样 boot↔service 重叠 | `grep -rn "^  port:" --include="application*.yml"` |
| X-8 | llm-gateway 有 MyBatis mapper XML（`src/main/resources` 下），但三个 service 进程（aiming:110 / datanet:68 / sysman:74）**都把 `runtime.llm.repository` 排除出 MapperScan** → 该 mapper 在承流进程中从不注册 | 三处 `*ServiceApplication` 注释行 |

### 1.2 护栏与工具裁决（X-9 ~ X-20，REQ-AI-01 红线）

| # | 事实 | 证据 |
|:--:|:--|:--|
| X-9 | **`ToolDispatcher` 全仓 0 命中**（PRD-06 §1.2 写的类名不存在）；事实上的唯一工具入口是 `ToolExecutorService.execute(toolName, arguments, callId)`（`:109/:113/:115`），`AgentLoopService.java:1093` 是其**全仓唯一业务调用点** | `grep -rn "ToolDispatcher" --include="*.java" ecos_backend` → 0；`grep -rn "toolExecutorService.execute"` → 仅 AgentLoopService:1093 |
| X-10 | 既有裁决闸 `AgentToolSqlSecurityGate` **真实调用** `POST {securityBaseUrl}/api/v1/security/policy-engine/evaluate`（`:341`），且未装配时**真 fail-closed**（`ToolExecutorService.java:481-484` 抛异常拒绝、`:536-541` 返回 DENY）—— 本域安全接线的"可复用样板"已存在 | `engine/ai-engine/ai-engine-impl/.../security/AgentToolSqlSecurityGate.java:341`、`.../service/ToolExecutorService.java:483,536-541` |
| X-11 | **但该闸的作用域只有 SQL**：`executeSql`（`:475-500`）与 registry 路径的 `guardSqlFromArguments`（`:121-127` 注释"如 query_db，sql 为 LLM 直供参数"）两处调用；`toolType` 为 REST/BUILTIN 的工具**完全不进闸** | `ToolExecutorService.java:121-127,466-470,475-500,535-553` |
| X-12 | 内置工具共 **7 个通用能力**：`query_db`(`ToolRegistry.java:162`)、`invoke_rest`(`:229`)、`delegate_to_agent`(`:305`)、`read_file`(`:337`)、`write_file`(`:395`)、`search_files`(`:451`)、`patch`(`:514`)；另有 BUILTIN 三个 `getCurrentTime`/`calculateRisk`/`delegate_to_agent`（`ToolExecutorService.java:618-630`）。其中 6 个（除 query_db 外的全部）**无任何 OPA 裁决** | `grep -n "new ToolSchema(\"" ToolRegistry.java` → 7 行 |
| X-13 | `query_db` 直接 `jdbcTemplate.queryForList(sql, params)` 执行 **LLM 生成的 SQL**（`ToolRegistry.java:201`），用的是引擎自己的默认数据源，无 RLS/租户注入；`invoke_rest` 用 `restTemplate.exchange(url, ...)` 请求 **LLM 给的任意 URL**（`:281`），无 allowlist、无 SSRF 防护 | 两处 file:line |
| X-14 | `sandboxReview`（HITL 沙盒）仅在 `td_user_security_profile.sandbox_mandatory=true` **且**高危正则命中时才裁决（`:191-207`）；其余一律放行；**审查器自身异常 → `return null` 放行**（`:234-239`，日志自陈"维持放行"）→ 该路径 fail-open | `ToolExecutorService.java:182-239` |
| X-15 | 输入/输出护栏是**双重 fail-open**：`@Autowired(required=false) GuardrailsService`（`AgentLoopService.java:88`）+ `guardrailsService != null` 判空跳过（`:133`/`:276`）+ `catch (Exception e){ log.warn }` 后继续（`:144-146`/`:284-286`） | `AgentLoopService.java:88,133-146,276-286` |
| X-16 | `GuardrailsServiceImpl` 为**纯本地正则实现，从不调 security-engine** → 与铁律 §2.4-3/7"脱敏/护栏一律经 security"和 PRD-06 §1.1 直接冲突 | `engine/ai-engine/.../service/GuardrailsServiceImpl.java:21-30` |
| X-17 | **`GUARDRAIL` 关键字全仓（Java）0 命中** → PRD-06 §1.4 要求的 `GUARDRAIL_EVAL` / `GUARDRAIL_FAIL_CLOSED` 两个事件类型完全不存在；`LLM_USAGE` 同样 **0 命中** | `grep -rn "GUARDRAIL" --include="*.java" ecos_backend \| wc -l` → 0；`LLM_USAGE` → 0 |
| X-18 | obligations（`mask` / `rowFilter`）在裁决返回结构（`AgentToolSqlSecurityGate.Decision`）中**不承载**，工具结果回 LLM 前无任何脱敏/裁剪阶段 → PRD-06 §1.3 obligations 与 §1.5-4 验收项无实现 | `ToolExecutorService.java:546`（只读 `decision.allowed()/reason()`） |
| X-19 | OPA evaluate 的现存调用点共 3 处，**均不在工具咽喉上**：`AgentToolSqlSecurityGate.java:341`（SQL 工具，超时实测 5000ms ≠ PRD 的 2s）、`oag/SecurityCheckerNode.java:78`（OAG 意图路径，此路径**确为 fail-closed**）、`security/AiSecurityEngineClient.java:69`（可复用客户端，仅被上两者使用） | 三处 file:line；`AiSecurityEngineClient` 引用者仅 SecurityCheckerNode + AgentToolSqlSecurityGate |
| X-20 | 审计留痕用 `ObjectProvider` 缺失即 `log.warn` 继续（fail-open 形态，分册 05 同源问题在 cognitive 侧为 C76）；本域未接 `KafkaTopics.AUDIT` 统一出口，裁决事件无处可发 | `AgentToolSqlSecurityGate.java:30,34` 注释自陈经 `AiSecurityEngineClient#audit`，但咽喉路径（X-11 之外）无审计调用 |

### 1.3 LLM 出口与底座（X-21 ~ X-28，铁律 §2.5-2）

| # | 事实 | 证据（含更正） |
|:--:|:--|:--|
| X-21 | **更正 ARCH_SPEC C1 的 file:line**：`DeepSeekProvider.java` **不存在**（`find ecos_backend -name DeepSeekProvider.java` → 空，`grep DeepSeekProvider` → 0 命中）。当前形态是引擎侧自建抽象 `engine/ai/.../service/LLMProvider.java`（接口，含 `priority()`/`supportsFunctionCalling()`/`isAvailable()`），**`implements LLMProvider` 在 main 侧 0 命中 → 无实现** | 见证据列命令 |
| X-22 | 但**绕行结构已成型且三处依赖它**：`AgentLoopService.java:581 selectProvider()`（注释"优先尝试 LLMProvider（直接调用 DeepSeek API）"）、`oag/ReasoningEngineNode.java:144 + :261`、`controller/AgentProviderController.java:32-37`。因列表恒空 → 当前不可达，一旦有人补实现即绕开 llm-gateway；且 `GET /api/v1/agent/providers` 现**恒返回空列表 200**（伪实现形态，同分册 05 §0.2-⑦） | `AgentProviderController.java:34-37,48-70` |
| X-23 | **裸密钥穿线**：`AgentLoopService.java:100-101 @Value("${llm.deepseek.api-key:}") deepseekApiKey` → `:597 request.setApiKey(deepseekApiKey)` 后 `:599 llmGateway.call(request)`。即由**引擎侧持有并向底座塞入明文 key**，而 llm-gateway 自身的 `SecurityEngineBridge.resolveSecret`（默认 DENY、不回退明文）被架空；同类穿线另有 `oag/ReasoningEngineNode.java:53`、`data-engine/.../CopilotServiceImpl.java:48` | 四处 file:line |
| X-24 | **更正"5 处 new RestTemplate"**：实测 ai-engine + llm-gateway 内 `new RestTemplate()` 共 **3 处** —— `ToolExecutorService.java:88`、`ToolRegistry.java:59`、`controller/DiagnosticAgentController.java:27`；三处都应改经 runtime-access（§2.5-1） | 精确 grep 输出 |
| X-25 | **自建线程/线程池在本域共 11 处**（全部逃逸 §2.5-3 与 §1.6）：`AgentLoopController.java:143`、`oag/OagController.java:136`、`AgentDelegationService.java:120`（**每次调用新建** singleThreadExecutor）、`AgentEvaluator.java:281`（**裸 `new Thread(...).start()`**）、`AgentMetricsCollector.java:34`（自建 Thread 工厂）、`AgentStudioService.java:64,67,308`、`ToolExecutorService.java:441`（每次调用新建）、llm-gateway `AgentSchedulerImpl.java:56,100`（底座自己建 `hermes-scheduler-*` 线程池 = **违 §2.5-3 于底座内部**） | 逐处 file:line（grep `Executors.\|CompletableFuture.runAsync\|new Thread(`） |
| X-26 | **ArchUnit 宇宙盲区已证实且不可自愈**：`ModuleDependencyArchTest.java:77-95` 的 16 条 candidatePaths 不含 `runtime/llm-gateway/target/classes`；主分支只要命中任一目录就用 `importPaths(classPaths)`（`:121-125`），**classpath 兜底（`:127-131`）仅在 `classPaths.isEmpty()` 时触发** → 正常构建下 llm-gateway 类**永不进入宇宙**，所有 `noClasses()` 规则对它不可见 | `gateway/src/test/java/.../ModuleDependencyArchTest.java:77-95,121-131` |
| X-27 | `businessMustNotSelfSchedule`（`:365-403`）谓词只查 `@Scheduled` 注解与 `ScheduledExecutorService` 类型（`:373-374,381`）→ 对 `Executors.newSingleThreadExecutor()`（返回类型 `ExecutorService`）、`CompletableFuture.runAsync`、裸 `new Thread()` **三类全部漏检**；`businessPackagesForScheduling`（`:437-451`）虽含 `engine.ai..`(:443) 与 `services.aiming..`(:448) 也无法弥补 | 同上 file:line |
| X-28 | **无"安全集成强制卡"规则**：`ModuleDependencyArchTest` 内安全相关规则 0 命中（仅 Kafka 规则 `:417-431`）；ai-engine 自有 `ArchitectureTest.java:93-100` 只禁跨引擎依赖，不禁 LLM/安全绕行 → 铁律 §2.4-8 的 grep 计数门禁在本域无从执行 | 两份 ArchTest |

### 1.4 会话、记忆与压缩（X-29 ~ X-35，REQ-AI-02）

| # | 事实 | 证据 |
|:--:|:--|:--|
| X-29 | 压缩触发用**消息条数**而非 token：`AgentSessionService.java:40 COMPRESS_THRESHOLD = 20`，`compressHistory(sessionId, threshold)`（`:258`）以 `SELECT COUNT(*)` 与阈值比较（`:264-269`）→ PRD-06 §2.1 的"模型上限 × 60%"与配置项 `ecos.ai.session.compress.threshold` **0 命中** | `grep -n COMPRESS_THRESHOLD`；`grep "compress.threshold"` → 0 |
| X-30 | 摘要**不经 LLM**：`:273-290` 取最早 N 条 → 每条截断 200 字符 → 拼 `"[会话历史摘要]\n用户: …\n助手: …"` 纯字符串拼接。`{已确认事实/用户偏好/未决问题/关键引用}` 四类结构化字段 **0 命中**；引用类信息（runId/证据链接）**必被丢弃**（与 §2.1"引用类禁摘要丢弃"冲突，直接威胁分册 05 B-3 与分册 09 证据链） | `AgentSessionService.java:273-290` |
| X-31 | **物理删除历史消息**：`:294 jdbc.update("DELETE FROM sys_agent_message WHERE session_id = ? AND id <= ?", ...)`；随后 `:298+` INSERT 摘要 system 消息。违"schema 只加不删"精神与可回放要求（AI-03 §3.3 要求全步骤可重建，此处原件被销毁） | `AgentSessionService.java:294` |
| X-32 | 三层纪律 + 裸表名双违：压缩逻辑直接持有 `JdbcTemplate jdbc` 写在 **Service 层**（§五"Service 禁 JdbcTemplate 直连"），且 `sys_agent_message` / `sys_agent_session` 均为**裸表名无 schema 限定**（§四附则1 = FAIL）；ai-engine-impl main 内 **17 个 Service 层文件**持 JdbcTemplate，`@Transactional` **0 命中** | `AgentSessionService.java:264,273,294,298`；全模块计数 |
| X-33 | `tool_call_id` 引用完整性无任何保护（压缩按 `id <= maxId` 整段删除，`tool_calls`/`tool_results` 列随消息一起消失）→ PRD-06 §2.1"工具调用保护"行无对应实现 | `AgentSessionService.java:273-294`（SELECT 列含 tool_calls/tool_results） |
| X-34 | 会话内存无界：`runtime/llm-gateway/.../session/SessionManagerImpl.java:40` `ConcurrentHashMap`，`closeSession` 不从 map 移除（实测 214 行文件内无 `remove`）→ 长压测（§2.2-1）必然线性增长；ARCH_SPEC §八 已定"RSS 峰值 < 基线×1.2 且无 Full GC 连续 3 次"内存门禁，本域当前**无达标路径** | `SessionManagerImpl.java:40` + 全文 grep |
| X-35 | 摘要失败降级（截断最早轮次 + warn + 上报 runtime-monitor）**0 实现**：`:312` 仅 `log.warn("Failed to compress history…")` 后由外层吞掉 | `AgentSessionService.java:312` |

### 1.5 可观测性与 token 计量（X-36 ~ X-42，REQ-AI-03）

| # | 事实 | 证据 |
|:--:|:--|:--|
| X-36 | traceId 现状 = **进程内 8 位随机串**：`AgentTracer.newTrace()` 用 `UUID.randomUUID().toString().substring(0,8)`（`:28-29`），存 `static ConcurrentHashMap`，超 `MAX_TRACES=1000` 时 `traces.keys().nextElement()` 淘汰（`:31-35`，**迭代序非时序**且与并发写竞态）；全类仅 `log.debug` | `engine/ai/.../service/AgentTracer.java:21-58`；`AgentTracer` 引用点全仓 9 处 |
| X-37 | **`X-Request-Id` / `MDC` / `EVT=` / `promptTokens` 四个关键字全仓 0 命中** → PRD-06 §3.1 三条（入口读头、MDC 落 traceId、每步结构化日志）与 §3.2 计量字段名全部无实现；出站透传（llm-gateway/security/cognitive）无从谈起 | 逐词 grep 计数 = 0 |
| X-38 | 观测数据结构错位：`AgentTracer.record(traceId, turn, action, elapsedMs, tokens, toolName, success)` 的 action 取值为 `think/act/observe`，而 PRD-06 §3.1-2 规定 `plan/tool_call/tool_result/llm_call/final` 五态；且调用处实际传 `0, 0`（如 `AgentLoopService.java` observe 记录 `record(traceId, turn, "observe", 0, 0, null, true)`）→ **耗时与 token 恒为 0** | `AgentTracer.java:49-58`；AgentLoopService observe 调用行 |
| X-39 | 计量落点物理断裂：`runtime/llm-gateway/.../repository/AgentCallLogRepository.java:13` 与 `ProfileConfigRepository.java:13` **只是接口**（无实现类，`AgentMetricsImpl.java:30` 注入的正是它）→ 加上 X-8 的三处 MapperScan 排除，计量链路两处断点；库里 `sys_agent_call_log` 在 `public` 与 `ecos_ai` **均 0 行**，`ecos_working_memory` 0 行 | 接口定义 + `AgentMetricsImpl.java:4,18,30` + 只读 count |
| X-40 | 配额（`ecos.ai.session.token_limit`）**0 命中**；无会话级 token 上限 → PRD-06 §3.2"超限拒绝 + 可读消息（防跑飞）"无实现，Loop 迭代上限亦未见 token 维度 | grep 计数 = 0 |
| X-41 | SSE 侧 traceId：后端**有**流式端点 `AgentLoopController.java:138 @PostMapping(value="/chat", produces=TEXT_EVENT_STREAM)`、`oag/OagController` 流式；前端**有**诊断码显示（`playground/MessageBubble.tsx:105-148,309-343`、`agent-studio/AgentMonitor.tsx:389`）—— 但显示的 traceId 就是 X-36 的 8 位进程内串，跨引擎不可 grep（§3.4-1 判 FAIL） | 四处 file:line |
| X-42 | `ApiResponse` 无 `traceId` 字段（分册 00 D.5.1 已立 W 项，ARCH_SPEC §七-3 记载实测 `runtime/common-api/.../ApiResponse.java:48-52`）→ 本册所有 AI-03 验收依赖该增量字段先落地，属跨册接缝，**本册只引用不重复立项** | ARCH_SPEC §七-3 |

### 1.6 场景受控工具与引用强制（X-43 ~ X-46，REQ-AI-04）

| # | 事实 | 证据 |
|:--:|:--|:--|
| X-43 | PRD-06 §4.1 五个场景工具 **0 实现**：`forecast.query`、`forecast.drilldown`、`forecast.explain`、`scenario.copyCompare`、`action.createDraft` 全仓（Java + TS）**0 命中**；现有 7 个内置工具（X-12）与年度经营预测场景无交集 | 逐词 grep |
| X-44 | 引用块模板 **0 实现**：`forecastRunId`、`caliberId`、`asOfTime` 三个占位符名全仓 0 命中；无"含数值必带引用"的后校验阶段（工具结果 → LLM → 最终回答路径上无任何引用检查） | 逐词 grep |
| X-45 | 三条拒绝规则（口径未批准→澄清、要求 AI 估算金额→拒绝、工具全失败→明说分类）**0 实现**；且 X-13 的 `query_db` 允许 LLM 直接生成 SQL 取数 = 铁律 §0.2.1"禁 LLM 承担数值生成职责"的现实违规面 | 逻辑缺位（咽喉上无该阶段） |
| X-46 | 工具白名单机制存在但属"能力清单"非"权限裁决"：`AgentLoopService.java:1071-1085` 按 `config.getToolWhitelist()` 字符串包含判断，白名单来自 `DataInitializer.java:83` 硬编码 5 项（`search_files/read_file/write_file/patch/knowledge_extract`）—— 注意其中 `knowledge_extract` **在 ToolRegistry 里并未注册**（X-12 的 7 项里没有），即配置与实现脱节 | `DataInitializer.java:83` vs `ToolRegistry.java` 注册表 |

### 1.7 错误处理（X-47 ~ X-51，REQ-AI-05）

| # | 事实 | 证据 |
|:--:|:--|:--|
| X-47 | 七类错误（LLM_TIMEOUT / LLM_RATE_LIMITED / PARSE_FAILURE / CONTEXT_OVERFLOW / GUARDRAIL_DENIED / GUARDRAIL_FAIL_CLOSED / TOOL_BACKEND_ERROR）**逐词 0 命中**；无重试上限与退避策略的任何集中实现 | grep 计数 = 0 |
| X-48 | `ErrorCodeMapper.java` 是死代码：全仓仅 `:8` 自身类声明 1 处命中，**零引用** → 已有的错误码映射意图从未接线 | `grep -rn ErrorCodeMapper --include="*.java" ecos_backend` → 1 行 |
| X-49 | 掩蔽与泄露：ai-engine-impl main 内 `catch (Exception` **187 处**；controller 目录内 `e.getMessage()` **67 处**，其中被写入响应体的实例包括 `AgentLoopController.java:114,219,272,334,417` → 违"禁裸 500 透传/禁泄露内部异常原文"（§5.2-1）与 AI-01 DENY 消息"不泄露资源细节"要求 | 计数 + 5 处 file:line |
| X-50 | **mock-success 反语义**（本册最危险的单点）：`AgentLoopService.java:1087-1089` 执行器未就绪、`:1102-1105` 工具抛异常，两者都走 `buildMockToolResult`（`:1108-1119`）返回 `success=false` **但 `content = "{\"status\":\"success\",\"message\":\"ToolExecutorService 未就绪，返回模拟结果\"}"`** → LLM 读到的是 JSON 里的 `"status":"success"`，会把失败当事实继续推理（直接触发 §0.2.1 数值幻觉路径） | `AgentLoopService.java:1087-1119` |
| X-51 | 熔断器存在但未覆盖 LLM/护栏语义：`engine/ai-engine/ai-engine-impl/src/test/.../AgentCircuitBreakerTest.java` 有测试，main 侧熔断仅护住 provider 调用；PRD-06 §5.1 的"降级备用 provider（如配置）"因 X-21 无 Provider 实现而无处可降 | ai-engine test 清单（7 类 43 个 `@Test`） |

### 1.8 数据层（X-52 ~ X-62，REQ-AI-02/03 落点 + AI-03 审计）

> 只读探针：`information_schema` + `pg_class.reltuples` + `count(*)`；无任何写入。

| # | 事实 | 证据 |
|:--:|:--|:--|
| X-52 | **`ecos_ai` = 50 张表、全 0 行**（空壳）：`SELECT count(*) FROM information_schema.tables WHERE table_schema='ecos_ai'` → 50；reltuples>0 过滤 → **0** | 复现命令左列 |
| X-53 | `ecos_ai` 内 **26 个 jsonb 列**（违 MC02"JSON 只用 TEXT"） | `... AND data_type='jsonb'` → 26 |
| X-54 | `ecos_ai` **全部 PK 为 `VARCHAR(64)`**，规范要求 `VARCHAR(36)` 应用侧 UUID（MC01）→ 50 张表逐张宽度不符 | information_schema 列宽实测 |
| X-55 | **DR06~DR08 七基线列覆盖 0/50**：`ecos_ai` 内不存在 `create_time/update_time/create_by/update_by/version_no/is_deleted/domain` 任一列（探针返回空串） | 7 列 IN 查询 → 空 |
| X-56 | `ecos_decision_case.id` 为 **BIGINT + nextval**（违 MC01）；`agent_cost` 表带 **25 个空分区**（无数据、无用途说明的预分区） | catalog 探针 |
| X-57 | **AI 运行态真身全在 `public`**：`sys_agent_session` 18 行、`sys_agent_message` 6 行（**两表在 `ecos_ai` 中不存在**）、`ecos_agent_memory` 15、`ecos_agent_registry` 16、`sys_agent_profile` 5 → 与 ST07"控制域 5+1 schema"和 ARCH_SPEC §4.3 表（ai-engine 写 `ecos_ai`）双重不符 | 行数与存在性探针 |
| X-58 | 计量与回放表**双双无数据且无字段**：`sys_agent_call_log` 在 `public` 与 `ecos_ai` 均 0 行；全库**零表/零列/零事件类型含 `guardrail` 或 `replay`**；`ecos_audit_log`（533 行，id BIGINT，changes **JSONB**）、`td_audit_log`、`outbox_event` 三处 `GUARDRAIL_EVAL`/`LLM_USAGE` 载荷命中 0 | catalog + 载荷 grep |
| X-59 | 会话计数与实体不符：`sys_agent_session.message_count` 列合计 ≥18，而 `sys_agent_message` 实存 6 行 → 冗余计数未随 X-31 的物理删除同步（删除后不回填的必然结果） | 两侧 count 对账 |
| X-60 | DDL 单源失守（铁律 v2.0 §3.1）：迁移目录实数 **125 个文件、最大 V163、重复版本 V50/V150/V155**；另有**第二 DDL 源** `ecos-sql/migration/12_to_8_schema.sql` 以 `SET SCHEMA` 搬表，与"单源目录 + 只加不删"冲突 | `ls \| wc -l`=125；`sed 's/__.*//' \| sort \| uniq -d`=V50/V150/V155 |
| X-61 | `V47` 迁移创建 12 个非 ST07 schema，并执行 **`ALTER DATABASE sys_man SET search_path`** —— 库级 search_path 副作用使"裸表名"从代码问题升级为数据库行为，X-32 的裸表名风险被该设置掩盖（换库即崩） | 迁移文件内容 |
| X-62 | 三方言分叉：`ecos-sql/{postgresql,mysql,oracle}/06_ecos_ai.sql` 三份并存，oracle 档**无任何文档记载**，MySQL 份用原生 `JSON` + `AUTO_INCREMENT`（违 MC01/MC02），三份**均无基线列**（与 X-55 一致）；另有 8+ 张在用的 AI 表**没有任何迁移脚本承载**（库内存在、目录中找不到建表语句） | 三方言文件对比 + 存在性对账 |

### 1.9 前端 AI 面（X-63 ~ X-70，REQ-AI-03 显示面 / REQ-WS-03 接缝）

| # | 事实 | 证据 |
|:--:|:--|:--|
| X-63 | **16 个前端文件持有 AI 端点字面量**，同一概念多套并存：AgentLoop/AIP 客户端 **×5**（`api.ts:224-376`、`pages/aiworkbench/api.ts:80`、`services/aiworkbenchApi.ts:87-174`、`services/agentMeshApi.ts:20-21`、`services/cognitiveEngineApi.ts:416-426` 旧 `/api/agent-mesh`）；聊天面板 **×3**（`components/aiworkbench/chatbot/ChatPanel.tsx` 645 行、`chatbot/ChatThreadPanel.tsx`、`agent-studio/ChatPlayground.tsx`）+ `MessageBubble.tsx` 双份（chatbot/ 与 playground/）；护栏客户端 **×4**（`pages/Guardrails/constants.ts:8`、`pages/aiworkbench/api.ts:80`、`AiGuardrailsView.tsx:86-167`、`cognitiveEngineApi.ts:437-460`） | 逐文件 grep + 行数 |
| X-64 | 端点清单（去重）：`/api/v1/agent-loop/sessions`(`api.ts:536`)、`/api/v1/agent-loop/chat`、`/api/v1/oag/chat/stream`(`api.ts:447`)、`/api/v1/aip/*`、`/api/v1/agent-mesh`、`/api/v1/agent-runtime`、`/api/v1/agent/*`(`agentConfig.ts:107`)、`/api/v1/agent/copilot/*`(`copilotApi.ts:6,11`)、`/api/v1/agent/call`(`api.ts:2193`)、`/api/v1/guardrails/*`、`/api/v1/chat/stream`(`ChatPanel.tsx:173`)、旧 `/api/agent*`(`api.ts:294,304`) | 各处 file:line |
| X-65 | **`/api/v1/chat/stream` 是死调用**：后端全仓 `api/v1/chat` **0 命中**（真实流式端点是 `AgentLoopController.java:138 /chat`（SSE 变体）与 `oag/OagController.java:132 /chat/stream`）→ `ChatPanel.tsx:173,185` 的 EventSource 永远连不上；后端另有 `gateway/oag/OagForwardController.java:145` 转发同名 stream | 前后端 grep 对照 |
| X-66 | **鉴权头绕行**：AI 面 **0 个文件 import `services/auth.ts`**（前端规范"auth 头唯一通道"整体失守）；手工拼头/裸 fetch 实证 `AgentStudioView.tsx:135`、`useAgentStudio.ts:159`、`pages/aiworkbench/api.ts:447`（**SSE 请求完全不带 Authorization**）；`pages/knowledge/services/knowledgeApi.ts:108,624,734,962,1124` 直接 `localStorage.getItem('token')` | 各处 file:line |
| X-67 | 规范违规密度：硬编码 Tailwind 颜色 **17 个 AI 文件**（`components/aiworkbench/logic/*.tsx`、`chatbot/OntologyContextPanel.tsx`、`pages/aiworkbench/cognition/*.tsx`、`pages/Guardrails/UiPrimitives.tsx`）；硬编码中文 15+ 文件（例 `AgentStudioView.tsx:58,153,185,191,240,267,323-324,330`）；内联 `<svg>` 违 lucide-only（`CognitiveOperatingSystem.tsx`、`DashboardView.tsx`）；**超 800 行 3 个文件**（`api.ts` 2625、`pages/data-workbench/api.ts` 1656、`pages/knowledge/services/knowledgeApi.ts` 1233） | 逐项 grep |
| X-68 | **AI 数值伪造（前端侧的 §0.2.1 违规）**：token 展示为 `Math.random()*500`（`AgentStudioView.tsx:268`、`useAgentStudio.ts:292`）；模型价目硬编码 `cost: '$0.004'`（`ModelCatalogView.tsx:194,202,210`）；护栏决策面板由 locale 文案驱动模拟（`locales/aiworkbench/en.json:281,293`）→ 用户看到的"护栏/用量"均为装饰，与 X-17/X-39 的后端缺位互为掩盖 | 各处 file:line |
| X-69 | 会话压缩**无任何 UI**：`压缩/摘要/compress` 在 AI 面 0 命中（唯一命中 `StepIngest.tsx:38` 属数据管道语境）→ REQ-AI-02 的"降级提示"与 AI-05 的"摘要降级可读消息"无落点 | 全前端 grep |
| X-70 | dev 直连仅配置层：`vite.config.ts:21-24` 把 4 个 agent 前缀直连 `http://localhost:18084`（绕过 gateway，dev-only）；`server.ts:112` 的 `/api/v1/cognitive`→AIMING 直连受 `ECOS_LOCAL_DIRECT_SERVICE_PROXY` 门（默认 false，PMO-B 已收口）。源码内**无硬编码 host:port**（`18084` 仅出现在 `pages/scenario-sandbox/api.ts:196` 注释） | 两处 config + grep |

### 1.10 配置、契约文档与测试基线（X-71 ~ X-74）

| # | 事实 | 证据 |
|:--:|:--|:--|
| X-71 | LLM 配置单源未成立：`gateway/application.yml:86-114` 为事实中心（`llm.default-provider`/`default-model:88-89`、`llm.deepseek.api-key: ${DEEPSEEK_API_KEY:}` `:94`（**env 占位符，库内无明文密钥** ✓）、`llm.deepseek.base-url: https://api.deepseek.com` `:95`）；但 `services/sysman/impl/sysman-boot/application.yml:61` **重复声明 base-url**，`services/aiming/application.yml:66-68` 另有 `llm.api-key` → 三处写同一语义，违铁律 §十/§2.5-2 单源；消费方 `AgentConfigResolver.java:49-61`、`ReasoningEngineNode.java:53`、`AgentLoopService.java:100`、`CopilotServiceImpl.java:48` | 逐处 file:line |
| X-72 | 死配置 4 组：`runtime/llm-gateway/src/main/resources/application-llm.yml` **仅在 profile `llm` 下加载，全仓未见激活**（其中 `llm.engine.enabled` 无对应字段，`LLMGatewayProperties.java:13-98` 实际字段是 `running`）；gateway yml `:112` 顶层 `profile:` 落在 `llm` 前缀之外，无消费者；`llm.default-provider`/`llm.default-model`(`:88-89`) 与 aiming `llm.api-key`(`:66-68`) Java 侧消费未证实；`opa.policy-dir`（security-engine-boot yml `:26-28`）与 REST 裁决路径无关 | 绑定类对照 |
| X-73 | **三份模块文档假记载（必须订正）**：① `runtime/llm-gateway/agents.md:14` 声称 `POST /api/v1/agent-loop/run` —— `AgentLoopController` 实测只有 `/chat`(`:73`、`:138` SSE)、`/sessions`(`:250`)、`/sessions/{id}`(`:285`)、`/sessions/{id}/chat`(`:358`)，**无 `/run`**（全仓 `/run` 只在 `AgentEvalController.java:60`）；② `engine/ai-engine/ai-engine-boot/agents.md:43` 声称 impl 有 27 个 Controller（实测 32，X-1）、`:32` 声称"本 impl 内有 Agent 周期任务"（`@Scheduled`/`ScheduledExecutorService` 在 ai-engine-impl **0 命中**）；③ `engine/ai/ai-engine-impl/AGENTS.md:8` 声称"6 个 test class / 40 case"（实测 7 类 / 43 个 `@Test`，漏 `ToolCallWave54Test`）、`:7,:20` 提及的 `KnowledgeExtractorService` 文件不存在、`:20` 的 `AgentMemory` 主类不存在、`:13,:17` 路径错、`:30` 声称"evaluate+DENY"与 X-11/X-14/X-15 实测 fail-open 相反 | 三处文档行 vs 实测 |
| X-74 | 测试基线：ai-engine-impl 7 个测试类（`ArchitectureTest`、`AgentMemoryTest`、`LLMProviderServiceTest`、`KnowledgeAgentContextTestCase`、`AgentCircuitBreakerTest`、`AgentLoopResultContractTest`、`ToolCallWave54Test`，43 个 `@Test`，多为契约/结构断言）；**llm-gateway 与 aiming 各 0 个测试**；前端 14 个测试文件中唯一 AI 相关 `pages/aiworkbench/CognitionViewE2E.test.tsx` 全量 mock `apiFetchData`(`:27-29`)，只断言 cognition tab UI 与 404 stub，**不测任何 AI 行为**；`ecos-tests/` 3 个 `.mjs` 冒烟脚本对 `agent\|llm\|copilot\|chat` **0 命中** → AI 域当前**无任何端到端覆盖**，PRD-06 全部验收项需本册新建可执行标识（Q13） | 逐目录清点 |

### 1.11 前端第二遍深挖（X-75 ~ X-88，与 1.9 互补，含更重的结构性事实）

| # | 事实 | 证据 |
|:--:|:--|:--|
| X-75 | **AI 死界面约 9 900+ 行**（0 importer；实测方法 = 全仓 735 个 import specifier 集 与 97 个 AI 目标文件做差集）：`pages/AgentStudio.tsx`(698) + 整个 `pages/AgentStudio/` 目录、**整条护栏死链** `AiGuardrailsView.tsx`(441)+`AiGuardrailsWorkflowTab`(498)+`AiGuardrailsPolicyCompilerTab`(486)+`AiGuardrailsGuardrailsTab`(186)、`useAgentStudio.ts`(500)、`components/aiworkbench/agent-studio/` 5 文件、`components/aiworkbench/guardrails/` 全 3 个、`components/aiworkbench/knowledge/` **整目录 7 文件 1 971 行**、`ontology/ActionTypeTab.tsx`(640)、`chatbot/ChatHistory.tsx`(411)、`stores/agent/useAgentStore.ts`、`stores/cognitive/useCognitiveStore.ts`、`Marketplace.tsx`(345，本轮复验：路由实际 lazy-import 的是 `MarketplaceBrowser.tsx`) | 集合差 + 逐文件 importer 清点 |
| X-76 | **两套 agent-studio 组件集并存**：live `AgentStudioView.tsx` 用 `pages/aiworkbench/agent-studio/`；`components/aiworkbench/agent-studio/`（含 `AgentMonitor.tsx` 420 行，X-41 提到的诊断码组件**正在其中**）全目录无 importer → **诊断码显示能力实际活在死代码里** | 目录对照 + importer 集 |
| X-77 | **9 条死/错配 API 调用，3 条来自 live 文件**：① `GET /api/v1/chat/stream`（`ChatPanel.tsx:173,185` EventSource；agent-loop SSE 是 **POST-only**（`AgentLoopController.java:138`）→ EventSource 物理不可能成功）② `GET /api/v1/agent-loop/sessions` 列表（`pages/aiworkbench/api.ts:534-538` ← **live** `SessionList.tsx:8`；controller 只有 POST `/sessions`(`:250`) 与 GET `/sessions/{id}`(`:285`)，GET 列表 **0 命中**）③ `GET /api/v1/guardrails/policies/preview`（**live** `AgentStudioView.tsx:81` vs `GuardrailsApiController.java:65 @GetMapping("/policies/{id}/preview")`，"preview" 被当 {id} 且无 `GET /policies/{id}` → 404/405）④ `agentConfig.ts:151 GET ${BASE}/tools` 落进 `@GetMapping("/{id}")`（id="tools"）⑤ `agentConfig.ts:168 GET /{id}/tools`（仅 `@PutMapping`，`AgentConfigController.java:106`）→ 405 ⑥ `/api/v1/aip/agents/templates` vs 后端 `/agent-templates`(`AIPAgentController.java:184`) ⑦ `/api/v1/aip/evals/run?agentId=` vs `"/api/v1/aip/eval"`+`POST /run`(`AgentEvalController.java:30,60`)，**路径与方法皆错** ⑧ `/agents/{id}/versions`、`POST /rollback`、`PUT /status`(`api.ts:318,325,336`) 后端 0 命中 ⑨ `KnowledgeRetrieverNode.java:67` 注释自陈 mock | 前后端 mapping 逐条对照（zero-hit 已验证） |
| X-78 | **跨引擎幻影依赖**：`engine/ai-engine/AGENTS.md` 宣称暴露 `POST /api/v1/knowledge/reason` —— **无 controller mapping**；cognitive 侧 `EngineCapabilityRegistryImpl.java:112` 却真把它配成 `http://localhost:8080/api/v1/knowledge/reason`（`:38`）→ 运行期 404-target；`/api/v1/knowledge/extract*` 实体在 **kb-engine** `ExtractionController.java:28`（分册 04 F04-01 属主议题在本域的投影） | 三处 file:line |
| X-79 | **BFF 不能透传 SSE（生产流式必挂）**：`server.ts` `forwardProxy`(`:55-93`) 对非 JSON 响应走 `await upstream.text()` 后 `res.send(text)`(`:81-87`) → `text/event-stream` **被整体缓冲**，流式语义丧失；server.ts 亦无超时配置 | `server.ts:55-93,81-87` |
| X-80 | 超时自相矛盾：SSE 侧 300s（`gateway/oag/OagForwardController.java:145-150` `SseEmitter(300_000L)` + HttpClient 300s；`AgentLoopController.java:42`、`OagController.java:46` `SSE_TIMEOUT_MS=300_000`）vs `gateway/application.yml:105 request-timeout: 60s` → **`LLM_TIMEOUT` 分类（AI-05）在此配置下不可判** | 四处 file:line |
| X-81 | **mock 接在 live 主路径**（比 X-68 更重）：`pages/aiworkbench/agent-studio/agentStudioHelpers.ts:26 buildMockAgentReply`（硬编码 UA102 航班回答）被 **live** `AgentStudioView.tsx:278` 在 `setTimeout`(`:274`) 中调用 → Agent 对话页客户端伪造 LLM 回复；`pages/aiworkbench/index.tsx:74 useState<AIPAuditLog[]>(mockAIPAuditLogs)`（`mockData.ts` 312 行）→ 审计面板永久 mock，`/api/v1/aip/audit-logs` 从不请求；`ChatbotStudioView.tsx:84,89` 700ms 假动画轮询无后端 | 五处 file:line |
| X-82 | 鉴权绕行的**身份伪造面**：`pages/aiworkbench/api.ts:447-458 oagPlaygroundStream` 裸 fetch，**无 Authorization 且硬编码 `userId:'admin', tenantId:'default'`**（`:455-456`），调用方 live（`AgentPlayground.tsx:19`）→ 以 admin 身份跨引擎取数；AI 目录内 **24 处裸 `fetch(`** 未走 apiFetch/authHeaders；`services/agentMeshApi.ts:3` 注释自陈"无鉴权头"(`:7,:13`)；`copilotApi.ts:6,11` 同 | 逐处 file:line |
| X-83 | AI 范围合规计数：硬编码中文 **1 296** 处（Top：`useAgentStudio.ts` 62〔死〕、`AiGuardrailsWorkflowTab` 60〔死〕、`mockData.ts` 51、`cognition/hypothesis.tsx` 38）；硬编码 Tailwind 颜色 **945** 处（`hypothesis.tsx` 45、`overview.tsx` 37、`causalAnalysis.tsx` 34、`DashboardView.tsx` 16）；`aria-*` **11** 处；内联 `<svg>` **2** 处；缺 useTheme+useLanguage 的文件仅 4 个；**AI 组件无超 800 行**（最大 `DashboardView.tsx` 741；超行者为 `src/api.ts` 2625 等非组件模块）→ 修正 X-67 的"3 个文件超行"须限定为非组件文件 | 逐项计数 |
| X-84 | i18n 缺口量化：AI 文件用 **1 164** 个不同 `t()` key，**575 个（49.4%）在所有 zh-CN locale 中不存在**（aiworkbench 442、hypo 32、diagnosis 23、causal 17、state 16、scenario 16；例 `aiworkbench.playground.guardrailBinding`、`causal.title`）；对照 `aiworkbench` zh/en 各 495 key（0 diff） | key 集差集实测 |
| X-85 | 模型标识虚构/硬编码三处：前端默认 `model:'gemini-1.5-pro'`（`api.ts:58,257,279`；AGENTS.md 已证 GEMINI 系旧文档虚构、代码零引用）；后端 `DataInitializer.java:188` 硬编码 `"deepseek-v4-pro"`；`agent.default-max-iterations: 5`（gateway yml `:109`）与 5 个 agent-template JSON 各自硬编码 `maxIterations=5` → 单源未成立（联动 X-71） | 六处 file:line |
| X-86 | **契约制品全缺**：全仓 OpenAPI/Swagger spec **0 命中** → X-77 的 9 条错配无法从文档发现（本册 D 章因此必须逐条自证 mapping 对照）；`docs/40-实现/features/**` 内 AI 相关只有 scenario-workbench 两份认知契约（146/205 行，勾选框全 `- [ ]`，无虚假完成声称） | 全仓 glob/grep |
| X-87 | **测试底座不存在**：全仓 `@SpringBootTest` **0 命中**；ai-engine 7 个测试类全为 Mockito 单测（`AgentMemoryTest` mock JdbcTemplate，被测主类不存在，X-73）；llm-gateway 0、aiming 0、cognitive 16；`playwright.config.ts` 与 `ecos-tests/package.json` **均不存在**；3 个 `.mjs`（352/140/77 行）对 `agent\|chat\|aip\|llm\|cognitive` **0 命中**；前端唯一 AI 测试 `CognitionViewE2E.test.tsx`(200 行) **断言了 "P3b stub 404" 兜底** → 现有测试把桩行为锁成期望 | 逐目录清点 |
| X-88 | PRD-06 配置键全不存在：`ecos.ai.` 前缀在全部 `application*.yml` 与 Java 中 **0 命中**（`session.compress.enabled` / `compress.threshold` / `session.token_limit`）；`TokenEstimator` 类存在但无强制消费点；`AgentLoopService.java:151` 硬编码 `compressHistory(sessionId, 20)`、`:190` 一次性 ad-hoc 重试、`AgentCircuitBreaker` 三失败降级 —— 三者均不在 PRD 语义上 | grep + 三处 file:line |

---

## 二、A 章 — 功能设计（F06-01 ~ F06-22）

> 每项含：功能陈述 / 规则锚点 / 现状差距（X 编号）/ 设计要点 / **可执行验收标识**（`mvn -Dtest=…` 或 `*.spec.ts` 用例名，依 Q13：写"人工确认"视为虚假验收）。

### F06-01 全工具类型裁决咽喉（P0 红线，REQ-AI-01 主件）

**陈述**：把 PRD-06 §1.2 的裁决流程落到**既有事实咽喉** `ToolExecutorService.execute(String, Map, String)` 上，使裁决作用域从"仅 SQL"（X-11）扩为"全部工具类型 × 全部来源"（内置 7 + BUILTIN 3 + DB 注册的 SQL/REST/BUILTIN 工具 + F06-11 的 5 个场景工具）。
**规则锚点**：铁律 §2.4-4（操作授权）/ §2.4-6（默认 DENY）/ §2.4-7（禁重复实现）；REQ-AI-01 §1.1/§1.2。
**差距**：X-9 ~ X-14、X-19、X-20。
**要点**：
1. **禁新建 `ToolDispatcher` 类**（PRD-06 §1.2 的名字在此判定为不宜照抄）：咽喉若出现第二个入口，绕过面反而扩大。改为在 `ToolExecutorService` 内引入显式阶段链 `execute() → policyEvaluate() → sandboxReview() → dispatch() → obligationsApply()`，`policyEvaluate` 对**所有** toolType 无条件执行（不再以 `"SQL".equalsIgnoreCase(def.toolType)` 为前置条件，X-11）。
2. 复用 `AgentToolSqlSecurityGate` 的三段实现（载荷组装 / `AiSecurityEngineClient#evaluate` / fail-closed 判定），把类语义从"SQL 安全闸"升格为"工具裁决闸"（改名 `AgentToolPolicyGate`，SQL 白名单校验降为其子步骤），**避免同仓出现两套裁决**（§2.4-7）。
3. 超时按 PRD 定 **2s**（现 5000ms，X-19），超时/异常/非明确 ALLOW 三态统一走 `GUARDRAIL_FAIL_CLOSED`（F06-03 审计 + F06-14 错误分类）。
4. `sandboxReview` 的 `catch → return null`（X-14）改为 `return deny(...)`；高危正则保留为**附加**防御，不再是裁决触发条件（`sandbox_mandatory=false` 的用户同样要裁决，否则 DENY 面按用户而变，违反 §1.5-1"无裁决记录的工具调用次数 = 0"）。
5. ArchUnit 新规则（与 F06-17 同批）：`noClasses().should().AccessFactory...` 形态落地为——除 `ToolExecutorService` 外，任何类不得调用 `ToolRegistry#execute` 与 `*ToolExecutor#executeBuiltin`（防绕过咽喉）。
**验收**：
- `mvn -Dtest=ToolGuardrailChokepointTest#everyToolTypeReceivesExactlyOneEvaluate`（枚举 15 个工具名，断言各自捕获到 1 次裁决调用、0 次未裁决执行）；
- `mvn -Dtest=ToolGuardrailChokepointTest#nonSqlToolsAreAlsoAdjudicated`（`invoke_rest`/`read_file`/`write_file`/`patch`/`delegate_to_agent` 全 DENY 桩下 0 执行）；
- `mvn -Dtest=ToolGuardrailChokepointTest#sandboxReviewerFailureYieldsDenyNotPass`；
- `mvn -Dtest=AgentToolPolicyGateTest#timeoutTwoSecondsFailClosed`；
- `mvn -Dtest=ToolChokepointArchTest#noBypassOfToolExecutorService`（ArchUnit 源码级）。

### F06-02 裁决载荷与 obligations 脱敏接线

**陈述**：按 PRD-06 §1.3 组装 `subject/action/resource/environment` 四段载荷；对返回的 obligations 在**工具结果回 LLM 之前**执行脱敏/行过滤。
**规则锚点**：铁律 §2.4-1/2/3（RLS/CLS/脱敏只在 security 侧）、§2.4-7；REQ-AI-01 §1.3。
**差距**：X-18（Decision 不承载 obligations）、X-20（无审计）、载荷字段缺 `department`/`dataScope`/`channel`（现仅 tool/sql/user/tenant）。
**要点**：
1. `ToolAdjudicationRequest` 单一构造器（放 `engine/ai-engine-api`，供 cognitive/workspace 复用）：`subject{userId,roles[],department}` 取自 security token 上下文（**不得**由调用方自填）；`action{type=TOOL_CALL, tool, operation=read|write}`；`resource{scenarioId?, forecastRunId?, dataScope{projectIds[],periods[]}}` 从工具入参白名单字段投影（禁把整段 LLM 参数塞进载荷，防参数注入式越权）；`environment{traceId,channel=AGENT,timestamp}`。
2. obligations 消费：`mask[]` 与 `rowFilter` **一律调 security 的 mask/行过滤端点**（分册 01 SEC-02/03 契约），ai-engine 侧只传结果集、不实现算法；无 security 可用 → 整条结果丢弃并 FAIL_CLOSED（禁"先返回再说明"）。
3. 结果结构 `Decision{allowed, reason, policyId, obligations, latencyMs}`（现缺后三项）。
**验收**：`mvn -Dtest=ToolAdjudicationPayloadTest#payloadCarriesFourSectionsWithServerSideSubject`、`#llmSuppliedUnauthorizedArgsNeverEnterPayload`、`mvn -Dtest=ObligationsApplyTest#maskObligationAppliedBeforeLlmSeesResult`、`#engineDoesNotSelfImplementMasking`（grep 计数门禁：ai-engine 内 `mask` 算法类 0 命中）。

### F06-03 `GUARDRAIL_EVAL` 审计事件接线

**陈述**：每次裁决（ALLOW/DENY/FAIL_CLOSED）经 runtime-event 发 Kafka `ecos.audit`，eventType=`GUARDRAIL_EVAL`，detail={tool, decision, policyId, latencyMs, obligations}。
**规则锚点**：铁律 §2.4-5（审计走 Kafka `ecos.audit`，`KafkaTopics.AUDIT`）/ §2.5-7（禁自建 `KafkaTemplate`/`@KafkaListener`）；REQ-AI-01 §1.4。
**差距**：X-17（事件类型全仓 0 命中）、X-20（`ObjectProvider` 缺失即 warn 继续 = fail-open）、X-58（三个审计载体表 0 载荷）。
**要点**：
1. 唯一出口 = `EventBusService`（runtime-event），**事件发出失败必须让裁决整体失败**（护栏审计属红线，不接受"尽力而为"）：`auditOrThrow(...)` 与既有 `auditQuietly(...)` 并存但咽喉只准用前者。
2. 载荷只带**摘要与 ID**，禁带工具入参原文/SQL 原文/结果原文（防审计库变成新的敏感面，联动 X-58 的 `changes JSONB` 治理）。
3. 事件类型常量登记在 `runtime/common-api`（禁 ai-engine 自定义字符串）。
**验收**：`mvn -Dtest=GuardrailAuditEventTest#allowDenyAndFailClosedEachEmitOneEvent`、`#auditPublishFailureFailsClosed`（桩 `EventBusService` 抛异常 → 工具不执行）、`#eventCarriesNoRawPayload`（断言 detail 无 sql/content 键）。

### F06-04 输入/输出护栏改经 security-engine 且 fail-closed

**陈述**：废掉"本地正则冒充护栏"的形态，护栏的两个拦截点（用户输入、模型输出）都调 security-engine，不可用即拒绝整次会话请求。
**规则锚点**：铁律 §2.4-3/6/7；REQ-AI-01 §1.1；PRD-06 §1.5-2。
**差距**：X-15（双重 fail-open）、X-16（从不问 security）。
**要点**：
1. `GuardrailsService` 接口保留（避免破坏 API），实现改为 security 客户端；**删除** `GuardrailsServiceImpl` 的正则判定逻辑，本地正则只允许作为"降级前的粗筛"且粗筛结果不得作为放行依据。
2. `@Autowired(required=false)` → 必填；`guardrailsService != null` 判空跳过（X-15）→ 删除；`catch(Exception){log.warn}` → 返回 GUARDRAIL_FAIL_CLOSED 错误（F06-14 分类）并终止 Loop。
3. 输出护栏命中后的现行为"整段替换为 `[内容已根据安全策略过滤]`"（`AgentLoopService.java:281`）保留，但必须同时发审计事件（否则 §1.5-1 对账不可得）。
**验收**：`mvn -Dtest=GuardrailFailClosedTest#absentSecurityEngineBlocksInputBeforeLlmCall`、`#guardrailBeanMissingIsNotSilentlySkipped`、`#outputFilterEmitsAuditEvent`；`mvn -Dtest=GuardrailComplianceArchTest#noLocalRegexUsedAsPassDecision`。

### F06-05 LLM 出口收一（REQ-PLT-21 / 铁律 §2.5-2）

**陈述**：让 llm-gateway 成为唯一 LLM 出口 —— 删除引擎侧 Provider 抽象、双门面归一、api-key 不再由引擎携带。
**规则锚点**：铁律 §2.5-2、§2.5-1（HTTP 经 runtime-access）；REQ-AI-03 §3.2 计量点成立的前置。
**差距**：X-3（双门面）、X-4（同时注入）、X-21 ~ X-24、X-66（前端 SSE 无鉴权是同源问题的显示面）。
**要点**：
1. **删 `engine/ai/.../service/LLMProvider.java` 及其 3 处依赖**（X-22；当前 0 实现 → 删除无功能损失，属"避免将来绕行"的预防性拆除，符合"避免向后兼容壳"的取舍）。`AgentProviderController` 的 `GET /api/v1/agent/providers` **不删端点**（API 只增不改），改为委托 llm-gateway 的 provider 目录端点取真值；无目录数据 → 返回 501 + `stub=true`（禁 X-22 的"恒空 200"伪实现，与分册 05 §0.2-⑦ 同裁定）。
2. 门面归一：`runtime.llm.LLMGatewayService`（旧）标 `@Deprecated` 且**只保留读接口**，所有 `call/stream` 写路径统一进 `runtime.llm.gateway.LLMGateway`；`AgentLoopService` 只注入后者（X-4）。旧接口在 ≥2 迭代内保留兼容（ARCH_SPEC §七-2）。
3. key 穿线切断：删 `AgentLoopService.java:101` 的 `@Value("${llm.deepseek.api-key:}")` 与 `:597` 的 `setApiKey`，改由 gateway 侧 `SecurityEngineBridge.resolveSecret(apiKeyRef)` 解析（该实现已 fail-closed，X-23 显示它只是被绕过）；`ReasoningEngineNode.java:53`、`CopilotServiceImpl.java:48` 同批清理。
4. **跨进程裁决形态**（R-18 裁决项）：`SecurityEngineBridge` 现用"类名反射 + 同 JVM bean"（`:125,:154,:178`）——在 gateway fat-JAR 态可用、在 aiming service 态因 security 类不在 classpath 而**恒 DENY**。设计上给出两选项：① 改走 security-engine REST（与 `AiSecurityEngineClient` 同款先例，两态一致）；② 保持反射但要求在 service 态强制声明 `llm.gateway.abac-eval-enabled=true` 并 REST 兜底。**本册默认 ①（REST）**，因 `abac-eval-enabled=false` 这个可关开关本身就是绕过面。
**验收**：`mvn -Dtest=LlmSingleExitArchTest#engineMustNotOwnProviderAbstraction`（源码级：`LLMProvider` 类型 0 命中）、`#noRawApiKeyFieldInEngineSideRequest`（反射断言 `ChatRequest` 无被引擎赋值的 apiKey）、`mvn -Dtest=LlmGatewayFacadeTest#onlyNewFacadeHasCallAndStream`、`mvn -Dtest=AgentProviderControllerTest#emptyCatalogYields501Not200Empty`。

### F06-06 traceId 贯通与 Loop 结构化日志（REQ-AI-03 §3.1）

**陈述**：入口读 `X-Request-Id`（无则生成 UUID）→ MDC `traceId`；Loop 每步结构化日志；跨引擎出站透传；SSE 事件带 traceId。
**规则锚点**：PRD-01 NF-04 / ARCH_SPEC §七-3（ApiResponse 需先加 `traceId`，属分册 00 C 系列接缝）；REQ-AI-03 §3.1。
**差距**：X-36（8 位进程内串 + 竞态淘汰）、X-37（四关键字全 0）、X-38（action 枚举错位、tokens/耗时恒 0）、X-41。
**要点**：
1. **`AgentTracer` 降级为"本地调试视图"，不当作 traceId 源**：traceId 唯一源 = MDC（由 runtime-access 的入口过滤器写，分册 00 承流口径），`AgentTracer.newTrace()` 改为**读 MDC，无则生成完整 UUID 并写回 MDC**；`MAX_TRACES` 的 `keys().nextElement()` 淘汰改为按 `insertSeq` 的 LRU（X-36 竞态），或直接删除内存 map、改由日志侧回放（F06-08）。
2. 出站透传三处：`AiSecurityEngineClient`、`ToolExecutorService` 的 REST 工具、llm-gateway 调 provider —— 统一加 `X-Request-Id`（HTTP 客户端经 runtime-access 收口后由拦截器自动注入，禁各调用点手写，F06-17）。
3. 结构化日志行固定：`EVT=AgentLoop op={plan|tool_call|tool_result|llm_call|final} step={n} agent={agentId} tool={} model={} promptTokens={} completionTokens={} latencyMs={} decision={ALLOW|DENY|FAIL_CLOSED|NA}`；**token 与 latency 必填**（禁 X-38 的恒 0），无值写 `-1` 并在回放里标缺失。
4. `op` 枚举取代 `think/act/observe`（X-38），并在 `runtime/common-api` 常量登记，供分册 07/09 的前端诊断码对齐。
**验收**：`mvn -Dtest=AgentLoopTraceTest#entryHeaderBecomesMdcAndIsPropagatedToSecurityAndGateway`、`#everyStepEmitsOneStructuredLine`、`#tokenFieldsNeverZero`；`mvn -Dtest=CrossEngineTraceIT#grepTraceIdSpansAimingAndSecurity`（本册只断言 aiming+security，cognitive 侧断言归分册 05 C114 链）；`*.spec.ts › ai › 流式响应显示诊断码`。

### F06-07 token 计量与配额（REQ-AI-03 §3.2）

**陈述**：llm-gateway 出口做唯一计量点，事件 `LLM_USAGE` 发 Kafka；会话级 token 上限超限拒绝。
**规则锚点**：铁律 §2.4-5/§2.5-2/§2.5-7；REQ-AI-03 §3.2（"不落新业务表"）。
**差距**：X-39（mapper 未注册 → 计量断裂）、X-40（配额 0 实现）、X-58（零载荷）、X-68（前端 `Math.random()` 伪数值）。
**要点**：
1. 计量记录点 = `LLMGatewayImpl` 响应返回前（provider 的 usage 字段优先；provider 不返回 usage 时按 tokenizer 估算并标 `estimated=true`，**禁把估算值当实测值上报**）。
2. 落点遵从 PRD"不落新业务表"：`LLM_USAGE` 事件进 `ecos.audit` 链（X-58 的三个审计载体只读、不改形），`sys_agent_call_log` **保留为 gateway 内部查询视图**，其 MapperScan 排除（X-8）在三处 service 启动类改为按档位显式启用，避免"接口存在、注册被删"的静默断链。
3. 配额：`ecos.ai.session.token_limit`（经 sysman 配置门面读，铁律 §十/§2.5-5），超限 → 终止 Loop + 可读消息（F06-14 的 `CONTEXT_OVERFLOW` 前置判定），**禁继续静默消耗**。
4. 前端一律展示后端计量真值（X-68 的三处伪数值同批改：无数据 → 显示"未上报"而非 0）。
**验收**：`mvn -Dtest=LlmUsageMeteringTest#usageEventEmitOncePerCallWithRealOrEstimatedFlag`、`#estimatedTokensAreMarked`、`mvn -Dtest=SessionTokenQuotaTest#overLimitStopsLoopWithReadableMessage`、`mvn -Dtest=AuditPayloadProbeTest#llmUsageFindableInAuditStore`、`*.spec.ts › ai › token 数值来自后端而非随机`。

### F06-08 运行回放（REQ-AI-03 §3.3）

**陈述**：一次 Agent 运行的全部步骤可按 traceId 从日志重建时序（plan→tool→llm→final），含耗时与 token、失败重试记录。
**差距**：X-31（压缩物理删原件 → 回放断链）、X-36（进程内 map 重启即失）。
**要点**：回放**只依赖日志与审计事件**（F06-06 行 + F06-03/F06-07 事件），不新建"回放表"（X-58 显示全库无 `replay` 字段，本册确认无需引入）；提供 `GET /api/v1/agent/runs/{traceId}/replay`（**新端点，只增**）做日志侧聚合，数据来源限本地结构化日志查询或审计事件流，禁在响应里回显工具结果原文（脱敏面）。
**验收**：`mvn -Dtest=RunReplayTest#stepsReconstructedInOrderWithLatencyAndTokens`、`#replaySurvivesProcessRestart`（断言不依赖 `AgentTracer` 内存）、`#replayResponseOmitsRawToolOutput`。

### F06-09 会话压缩按 PRD 语义重写（REQ-AI-02）

**陈述**：把"条数阈值 + 200 字截断拼接 + 物理 DELETE"改为"token 阈值 + LLM 结构化摘要 + 引用保护 + 逻辑归档"。
**规则锚点**：REQ-AI-02 §2.1 全表；铁律 §2.5-2（摘要必经 llm-gateway）；DB 规范"只加不删"精神 + B-7。
**差距**：X-29 ~ X-35。
**要点**：
1. **触发**：`estimatedContextTokens > modelLimit × ecos.ai.session.compress.threshold`（默认 0.6）；`modelLimit` 从 llm-gateway 模型目录读（禁引擎内硬编码，联动 X-71 配置单源）；保留 `compress.enabled` 开关，关闭时上报 `compressSkipped=true`（禁静默关闭，与分册 04 C3 同款裁定）。
2. **窗口**：最近 K=6 轮原文保留（`compress.keep_recent`）；更早轮次交 llm-gateway 生成结构化摘要，注入 system 段。
3. **摘要结构**（落 TEXT 列，遵 MC02 禁 jsonb）：`{confirmedFacts[], userPreferences[], openQuestions[], keyRefs[{type,id,link}]}`；`keyRefs` 采集规则 = 从被压缩消息中**正则预提取** `forecastRunId|caliberId|evidenceUrl|tool_call_id` 后**原文并入摘要**，摘要 prompt 明示"引用类信息禁省略"，且生成后做**引用完整性硬校验**：被压缩段出现的 runId 集合 ⊆ 摘要 keyRefs 集合，校验失败 → 走降级（下条）。
4. **工具引用完整性**：被压缩消息若其 `tool_call_id` 仍被后续消息引用 → **保留该工具结果原文**（不压缩、不截断）。
5. **失败降级**：摘要 LLM 调用失败/引用校验不过 → 退化为截断最早轮次 + `log.warn` + 事件上报 runtime-monitor（X-35 现只 warn 不上报）；降级必须写 `compression_mode=TRUNCATE_FALLBACK` 标记，供 §2.2-3 抽检。
6. **禁物理删除**：`DELETE FROM sys_agent_message`（X-31）改为 `UPDATE ... SET is_deleted=1, compression_batch_id=?` + 新增摘要行（引用式，非替换式）。历史原件保留 = 回放（F06-08）与 §2.2-2 语义抽检的前提。
   > **R-20 裁决项**：本条与既有实现的行为相反（现删、本设计不删），且影响 `sys_agent_message` 存量行数语义与 X-59 的计数对账。**R-20 ① 已批准** ⇒ 本册按"仅对新数据生效 + 逻辑归档"写作，迁移脚本文件本轮落地（不实跑库，§14.4）。
**验收**：`mvn -Dtest=SessionCompressionTest#triggerIsTokenBasedNotCountBased`、`#summaryKeepsAllForecastRunIdsFromCompressedRange`、`#referencedToolCallResultsAreNotCompressed`、`#compressDoesNotIssuePhysicalDelete`（断言 SQL 语句集合无 DELETE）、`#summaryFailureFallsBackToTruncateWithMarker`、`mvn -Dtest=SessionMemoryIT#longSessionHundredTurnsRssWithinBaseline`（对应 PRD §2.2-1 与 ARCH_SPEC §八 内存门禁）。

### F06-10 会话与轨迹内存治理

**陈述**：消除两处无界增长，使 F06-09 的内存门禁可判。
**差距**：X-34（`SessionManagerImpl.java:40` ConcurrentHashMap 且 closeSession 不移除）、X-36（AgentTracer 竞态淘汰）。
**要点**：会话 map 改带 TTL + 上限的淘汰结构（`expireAfterAccess` 语义由 runtime-task 的清扫任务驱动，禁本模块自建 `ScheduledExecutorService`，X-25）；`closeSession` 必须 `remove` 并释放消息引用；AgentTracer 若按 F06-08 保留内存视图则改 LRU-by-seq，若废弃则整类删除（不留空壳）。
**验收**：`mvn -Dtest=SessionManagerLifecycleTest#closedSessionIsEvictedFromMemory`、`#idleSessionsExpireWithinConfiguredTtl`、`mvn -Dtest=AgentTracerBoundTest#traceMapNeverExceedsMaxAndEvictsByInsertionOrder`。

### F06-11 五个场景受控工具（REQ-AI-04 §4.1）

**陈述**：注册 `forecast.query` / `forecast.drilldown` / `forecast.explain` / `scenario.copyCompare` / `action.createDraft` 五个工具，全部走 F06-01 咽喉，后端来源一律经 REST 取数（禁 SQL 直查他引擎表）。
**差距**：X-43（0 实现）、X-12/X-13（现有工具与场景无交集且可执行任意 SQL/URL）。
**要点**：
1. 注册表条目结构 = `{name, argsSchema, backendEndpoint, operation(read|write), permissionHint, timeout}`，**声明式**（入 `ecos_ai.agent_tool_definition`，F06-18 数据设计）；不允许"注册即内置 Java lambda"的形态（现 `ToolRegistry.register(schema, executor)` 的 lambda 注册使权限不可审计，X-12）。
2. 参数与后端映射（PRD-06 §4.1 单源，逐条抄自 PRD 表格，不得自创）：`forecast.query`→预测运行服务 results 端点（PRD-09 FC-02），只读、RLS 按部门域；`forecast.drilldown`→results 下钻 + evidence 端点，只读、行级 RLS；`forecast.explain`→cognitive `diagnose`（带 forecastContext，PRD-05 COG-02 / 分册 05 E 端点），只读；`scenario.copyCompare`→情景运行 + diff，**写**，OPA 需 `scenario:write`；`action.createDraft`→动作服务，**写**、仅创建 DRAFT 态、审批仍走人工流。
3. 三个只读取数工具**禁**复用 `query_db`（X-13 的 LLM 生成 SQL）路径；`invoke_rest` 类通用工具在场景 Agent 的白名单里**显式禁用**（白名单机制从 X-46 的"能力清单"升级为"经裁决的权限视图"）。
4. 每个工具返回体必须自带引用元数据 `{forecastRunId, caliberId, version, asOfTime, evidenceLinks[]}`，供 F06-12 逐字引用。
**验收**：`mvn -Dtest=ScenarioToolRegistryTest#fiveToolsRegisteredWithBackendEndpoints`、`#writeToolsDeclareScenarioWrite`、`#forecastToolsNeverRouteThroughQueryDb`、`mvn -Dtest=ScenarioToolInvocationIT#eachToolProducesExactlyOneOpaDecision`（对应 PRD §4.3-4）、`#actionCreateDraftYieldsDraftStateOnly`。

### F06-12 回答引用强制与后校验（REQ-AI-04 §4.2）

**陈述**：含经营数值的最终回答必须附引用块，且数值逐字取自工具返回；系统提示词 + 后校验双保险。
**规则锚点**：铁律 §0.2.1（禁 LLM 生成数值）；REQ-AI-04 §4.2；B-5。
**差距**：X-44（模板与三占位符 0 命中）、X-45（无后校验阶段）。
**要点**：
1. 引用块格式固定（逐字符对齐 PRD）：`[运行 {forecastRunId} · 口径 {caliberId}@{version} · 截至 {asOfTime} · 证据 {links}]`，字段只从工具返回体取，**禁 LLM 复述改写数值**：数值 token 化匹配 —— 后校验把回答中的数字串与本轮工具结果集中的数字串做集合比对，出现"工具结果中不存在的数字" → 判违规（比"有没有引用块"更强，直接封住幻觉数值）。
2. 后校验失败 → 重生成，**最多 2 次**；仍失败返回固定话术"无法提供有依据的数值"（F06-14 的 `PARSE_FAILURE` 分支复用，不新增错误类）。
3. 后校验器放 `engine/ai-engine`（属编排语义，非安全语义，故不违 §2.4-7），但**其数值抽取与比较逻辑必须是确定性代码**，禁再叫一次 LLM 判"有没有引用"。
**验收**：`mvn -Dtest=CitationPostCheckTest#numberWithoutCitationBlockIsIntercepted`、`#numberAbsentFromToolResultsIsInterceptedAsHallucination`、`#regenerateCappedAtTwoThenFallbackMessage`、`mvn -Dtest=CitationInducementIT#refusesDirectAskForNumberWithoutCitation`（PRD §4.3-2 诱导注入项）。

### F06-13 三条拒绝规则与澄清提问

**陈述**：口径缺失/未批准 → 澄清不给数；要求 AI"算一下/估算"金额 → 拒绝并引导创建确定性预测运行；工具全部失败 → 明说失败分类，禁编造。
**差距**：X-45、X-50（mock-success 正是"编造"的机器）。
**要点**：三条规则都实现为**咽喉上的前置判定**（非提示词祈使）：口径状态从本体/口径服务读（分册 03 口径编码），未批准 → 直接返回澄清结构 `{type:CLARIFY, missing:caliber, nextAction}`，不进 LLM 数值路径；"估算请求"识别为意图标记（确定性关键词 + 结构判定），命中 → `TOOL_BACKEND_REQUIRED` 引导；工具全失败 → 由 F06-14 分类结果渲染，禁 F06-01 之外的兜底文案。
**验收**：`mvn -Dtest=RefusalRulesTest#unapprovedCaliberYieldsClarifyNotNumber`、`#estimateAmountRequestIsRefusedWithRunCreationPointer`、`#allToolsFailedReportsClassifiedReason`。

### F06-14 七类错误分类与有界退避（REQ-AI-05）

**陈述**：按 PRD §5.1 矩阵实现分类、重试上限/退避、用户可读消息模板、日志级别四元组。
**差距**：X-47（0 实现）、X-48（ErrorCodeMapper 死代码）、X-50（mock-success）、X-51。
**要点**：
1. `AiErrorClass` 常量登记于 `runtime/common-api`（与分册 05 的 `ECOS-COG-*` 并列，前缀 `ECOS-AI-*`，D.5 码表），`ErrorCodeMapper` **接线复用**（不删，改为 `classify(Throwable, context) → AiError`），避免再造第二个映射器。
2. 重试规则逐条照 PRD：`LLM_TIMEOUT` 指数退避 ≤3；`LLM_RATE_LIMITED` 退避 ≤3 后按配置降备用 provider（**降 provider 只能经 llm-gateway 内部路由**，禁引擎侧绕行，X-22）；`PARSE_FAILURE` 重生成 ≤2（与 F06-12 共用配额，不叠加）；`CONTEXT_OVERFLOW` 截断降级（F06-09）；`GUARDRAIL_DENIED`/`GUARDRAIL_FAIL_CLOSED`/`TOOL_BACKEND_ERROR` 分别 0/0/≤1 次。
3. **废 mock-success**（X-50）：`buildMockToolResult` 删除；执行器未就绪或工具异常一律 `TOOL_BACKEND_ERROR` 结构化失败，`success=false` 且 `content` 内不得出现 `"status":"success"` 字样。
4. 消息模板与日志级别逐字照 PRD 表（含"诊断码 {traceId}"，与 F06-06 的 MDC traceId 同源，禁再显 8 位串）。
**验收**：`mvn -Dtest=AiErrorMatrixTest#sevenClassesEachCarryTemplateRetryBoundAndLevel`、`#retryIsBoundedAndBackoff`、`#mockSuccessPathIsGone`（反射/源码断言 `buildMockToolResult` 0 引用）、`mvn -Dtest=AiFaultInjectionIT#timeoutRateLimitParseFaultTakeDocumentedPaths`（PRD §5.2-3 三类故障注入）。

### F06-15 异常掩蔽与内部信息泄露治理

**陈述**：压掉 187 处 `catch (Exception)` 的掩蔽与 67 处 `e.getMessage()` 外泄，建立"边界才捕、捕后必分类"的纪律。
**差距**：X-49。
**要点**：控制器层禁 `catch (Exception)`（只允许 `@RestControllerAdvice` 统一处理，与分册 04 F04-03 同一形态、分册 01 的 Exception→404 掩蔽改造为跨册接缝本册引用）；`AgentLoopController.java:114,219,272,334,417` 五处响应体异常原文改为 `errorCode + traceId`；Service 层 catch 必须重抛领域异常或经 F06-14 分类，禁 `log.warn(msg)` 后返回语义成功值（X-50 同族）。
**验收**：`mvn -Dtest=AiExceptionHygieneTest#controllersDeclareNoCatchAll`（源码扫描计数=0）、`#responseBodyNeverContainsRawExceptionMessage`、`mvn -Dtest=AiErrorSemanticsTest#absentTableYields500Not404`（与分册 04 同断言形态，防 404 掩蔽复发）。

### F06-16 三层纪律与 schema 限定（ai-engine 持久化）

**陈述**：把 17 个 Service 层 JdbcTemplate 直连文件迁到 Mapper 层，所有表名 schema 限定，补事务边界。
**规则锚点**：DB 规范 §五（Controller/Service/Mapper 三层，Service 禁 JdbcTemplate 直连）、§四附则1（裸表名=FAIL）、MC01/MC02/MC03、DR06~DR08。
**差距**：X-31/X-32（压缩路径为最重样本）、X-53 ~ X-56、X-59。
**要点**：1) 新增 `engine/ai-engine-impl` Mapper 层（`ecos_ai` schema 限定，双路径 SQL 带 `databaseId` 方言分支，禁 `::`/`ON CONFLICT`/无 `databaseId` 的 `RETURNING`/`ILIKE`，MC03）；2) 写操作补 `@Transactional`（现 0 命中）；3) 会话计数列与实体行**改同事务维护**（解 X-59 对账不符）；4) `is_deleted` 逻辑删除成为唯一删除形态（F06-09）；5) 归属口径见 F06-18 与 R-19。
**验收**：`mvn -Dtest=AiPersistenceArchTest#serviceLayerHasNoJdbcTemplateDirectAccess`、`#everyTableNameIsSchemaQualified`（沿用分册 04 `SchemaInventoryGateTest` 形态）、`mvn -Dtest=AiSqlDialectComplianceTest#noBareCastNoOnConflictNoIlike`、`mvn -Dtest=SessionCountConsistencyIT#messageCountMatchesRowsAfterCompress`。

### F06-17 底座收口与门禁可判化（§2.5-1/3/7）

**陈述**：11 处自建线程/线程池、3 处 `new RestTemplate()`、Kafka 出口全部收 runtime；并补齐 ArchUnit 使这些收口**机器可判**。
**差距**：X-24、X-25、X-26、X-27、X-28。
**要点**：
1. 调度：`AgentStudioService.PIPELINE_EXECUTOR`、`ToolExecutorService.java:441` 与 `AgentDelegationService.java:120` 的**每调用新建线程池**（既是违规也是资源泄漏面）、`AgentEvaluator.java:281` 裸线程、`AgentMetricsCollector.java:34` 自建工厂、两处 `CompletableFuture.runAsync` → 全部改经 runtime-task（§1.6 即时/定时双入口，`td_runtime_task_plan` 单源，禁自建任务表）；**llm-gateway 自己的 `AgentSchedulerImpl.java:56` 线程池**改为委托 runtime-task（底座内部违底座规则，优先级与引擎侧同）。
2. HTTP：3 处 `new RestTemplate()` → runtime-access 客户端（并自动带 `X-Request-Id`，与 F06-06 咬合）。
3. 事件：`ObjectProvider` 尽力而为的审计发送（X-20）→ runtime-event `EventBusService`，红线路径 fail-closed（F06-03）。
4. **门禁两处修复**（否则以上收口永远判不出来）：① `ModuleDependencyArchTest.java:77-95` candidatePaths **加 `runtime/llm-gateway/target/classes`**（X-26 主分支命中即不走 fallback，故必须显式加路径）；② `businessMustNotSelfSchedule` 谓词（`:373-381`）**扩三类漏检形态**：`java.util.concurrent.Executors` 方法调用、`CompletableFuture.runAsync/supplyAsync`、`java.lang.Thread` 构造/`start`（X-27）；③ 新增"安全集成强制卡"规则（X-28，§2.4-8）：`engine.ai..` 中除咽喉包外任何类不得出现 `queryForList(String`（LLM 文本→SQL 执行的 grep 计数形态）且工具执行必经 `ToolExecutorService`。
**验收**：`mvn -Dtest=ModuleDependencyArchTest#llmGatewayIsInsideTheUniverse`（断言 import 类数含 gateway 包）、`mvn -Dtest=SelfScheduleArchTest#noExecutorFactoryNoRunAsyncNoRawThreadInBusiness`（含 llm-gateway 包）、`mvn -Dtest=SecurityIntegrationGateTest#noDirectSqlExecutionOutsideChokepoint`、`mvn -Dtest=RuntimeAccessTest#noNewRestTemplateInAiDomain`。

### F06-18 AI 数据层归位（`ecos_ai` 空壳 vs `public` 真身）

**陈述**：给出 AI 域表归属的唯一口径与新表 DDL 合规形态。
**差距**：X-52 ~ X-62。
**要点**：
1. 现状判定：`ecos_ai` 50 张全 0 行 + 26 jsonb + PK VARCHAR(64) + 基线列 0/50（X-52~X-56）；真身 5+ 张在 `public`（X-57）；计量/回放表零行零字段（X-58）。
2. **执行边界（R-19 ①+②／R-12 ① 已批准）**：本册**不**擅自 DROP/搬迁（实跑与物理删除属待授权执行项，§14.4）。归属推荐 = `ecos_ai` 为权威（ST07 + ARCH_SPEC §4.3 ai-engine 写 `ecos_ai`），`public` 的 5 张在用表**先定性为待归位**、只停写不迁；`ecos_ai` 的 50 张空壳处置**已按 R-19 ①+② 并行批准**（新表按 MC/DR 合规建于 `ecos_ai`（`V200~V204`）；空壳待 R-12 同批、**严格双条件**下才可 DROP——原候选：① 零行+零引用双条件者 DROP 后按合规 DDL 重建，承接 R-12 口径；② 只停写；③ 保留空壳并新建合规表）。
3. 新增表（本册 E 章，编号 **V200** 起）一律 MC01 `VARCHAR(36)` 应用侧 UUID、MC02 TEXT、MC03 方言分支、DR06~DR08 七基线列、schema 限定；`agent_cost` 25 个空分区与 `ecos_decision_case.id nextval` 登记为待整改不复用。
4. DDL 单源：第二源 `ecos-sql/migration/12_to_8_schema.sql`（X-60）与 `V47` 的 `ALTER DATABASE … SET search_path`（X-61）处置属 **R-22** 裁决项；重复版本 V50/V150/V155 属必须修的确定性缺陷（Flyway 当前禁用，但一旦启用即崩），列 W 项不等裁决。
**验收**：`mvn -Dtest=SchemaInventoryGateTest#aiTablesResideInEcosAiBeforeHandlers`（沿用分册 02/04 清单门禁形态）、`mvn -Dtest=AiDdlComplianceTest#newAiTablesHaveBaselineColumnsAndUuidPk`、`mvn -Dtest=MigrationSingleSourceTest#noSecondDdlSourceNoDuplicateVersion`。

### F06-19 前端 AI 面归一与真值化

**陈述**：×5 AgentLoop/AIP 客户端、×3 聊天面板、×4 护栏客户端各收敛为单一属主通道；死端点修通；伪数值清零；AI 面全面接入 `services/auth.ts`。
**规则锚点**：前端规范 §十 收口 + auth 唯一通道 + 组件 ≤800 行 + lucide-only + i18n/主题 token；REQ-AI-03 §3.1-3（诊断码显示）。
**差距**：X-63 ~ X-70。
**要点**：
1. 唯一 AI API 模块 = `services/aiApi.ts`（新建，收敛 5 套客户端；旧文件保留 re-export ≥2 迭代并标 `@deprecated`，与"API 只增不改"同精神，不做破坏性删改）；`api.ts`(2625 行) 内的 AI 段必须外移，使文件回到 ≤800 行约束（分册 08 统一处理超大文件，本册只切 AI 段）。
2. `ChatPanel.tsx:173` 的 `/api/v1/chat/stream` 死端点改指 `AgentLoopController.java:138` 的 `/api/v1/agent-loop/chat`（SSE 变体）或 OAG `/chat/stream`，并**必须带 Authorization**（X-66）。
3. 护栏/压缩/用量三块面板从 locale 模拟改为消费真事件（F06-03/F06-07/F06-09）；无数据显示"未上报"（禁 X-68 的 `Math.random()` 与硬编码 cost）。
4. 会话压缩需新增状态条（X-69）：显示"已压缩 N 轮 · 模式 {LLM_SUMMARY|TRUNCATE_FALLBACK}"，降级态用告警色由 `useTheme()` 取 token。
5. 移动端仅复用 `useMediaQuery`/`useMobileSidebar`/`MobileDataTable`（不新建移动形态）。
6. **死代码整批删除 + 诊断码迁移**（X-75/X-76）：importer 为空的 AI 组件与目录一律删除；`components/aiworkbench/agent-studio/AgentMonitor.tsx` 内含的 traceId 显示能力先迁入 live 面板，避免"删掉唯一实现"。
7. **身份伪造面归零**（X-82）：`oagPlaygroundStream` 的硬编码 `userId:'admin', tenantId:'default'`（`pages/aiworkbench/api.ts:455-456`）必须删除 —— 主体身份只能来自 token，禁前端自填；24 处裸 `fetch(` 全部改走 `services/auth.ts` + 统一 fetch 封装。
8. **mock 主路径清除**（X-81/X-85）：`buildMockAgentReply`（live 调用）与 `mockAIPAuditLogs` 永久种子一律删除，改真端点或显式 501-stub 渲染；默认模型 `'gemini-1.5-pro'` 改从后端模型目录取（禁前端硬编码模型 id）。
9. i18n 补齐按**缺口清单**推进（X-84 的 575 个缺失 key，其中 aiworkbench 442）：以测试驱动（缺 key 即 FAIL），禁逐页手工补（补不完且会漂移）。
**验收**：`*.spec.ts › ai › 单一 aiApi 通道被所有面板引用`（源码扫描 import 计数）、`*.spec.ts › ai › 流式对话端到端可用且带鉴权`、`*.spec.ts › ai › token 数值来自后端而非随机`、`*.spec.ts › ai › 压缩状态条显示模式标记`、`mvn -Dtest=FrontendAiComplianceScanTest#noHardcodedColorsNoChineseLiteralsNoInlineSvgInAiFiles`（配置断言型，读源码计数）、`*.spec.ts › ai › 前端不再自填主体身份`（断言请求体/头中无 `userId`/`tenantId` 字面量来源）、`mvn -Dtest=FrontendI18nGapTest#noMissingZhCnKeyAmongAiUsedKeys`（X-84 缺口清单驱动）。

### F06-20 模块文档与配置单源订正

**陈述**：三份 agents.md 的假记载按实测改写；LLM 配置三处重复合一。
**差距**：X-71 ~ X-73。
**要点**：① `runtime/llm-gateway/agents.md:14` 删 `/agent-loop/run` 虚假端点，改列真实四条（`/chat`、`/chat` SSE、`/sessions`、`/sessions/{id}/chat`）；② `engine/ai-engine/ai-engine-boot/agents.md:43` 的 27 → 32，`:32` 的"周期任务"改为"当前无 `@Scheduled`，调度待经 runtime-task"；③ `engine/ai/ai-engine-impl/AGENTS.md:8` 测试计数 6/40 → 7/43，`:7,:20` 不存在的 `KnowledgeExtractorService`/`AgentMemory` 条目删除，`:30` 的"evaluate+DENY"改为"仅 SQL 型工具裁决，其余待 W140 接线"（禁文档继续记载未成立的安全承诺）；④ `application-llm.yml` 死配置要么激活要么删；⑤ `llm.*` 收敛到 gateway 单源，`sysman-boot` 与 `aiming` 的重复声明改为引用；⑥ 启动类三处 `runtime.llm.repository` 排除改为按档位显式启用并注释说明理由（X-8）。
**验收**：`mvn -Dtest=ModuleDocTruthfulnessTest#aiDomainDocsMatchMeasuredCounts`（扫描文档中的数字/路径/类名与源码目录对账，沿用分册 05 的文档真实性门禁）、`mvn -Dtest=LlmConfigSingleSourceTest#noDuplicateLlmKeysOutsideGatewayYml`。

### F06-21 流式承流打通与死调用/死界面清理（生产可用性前提）

**陈述**：让"AI 流式对话"在生产链路（BFF→gateway→service）真正可用，并清掉 9 条死/错配调用与约 9 900 行死界面。
**规则锚点**：ADR-10 / PMO-B（BFF gateway-first）；ARCH_SPEC §七-2（API 只增不改）；分册 05 B-4 + 用户裁定 A3（503 原样穿透，禁 200 空 body）。
**差距**：X-75 ~ X-80、X-65、X-76。
**要点**：
1. **BFF 侧 SSE 直通**（X-79，判为 P1 阻塞项）：`server.ts forwardProxy` 增加 `text/event-stream` 分支 —— 用流式管道而非 `await upstream.text()`，显式关缓冲（`res.flushHeaders()`、`Cache-Control: no-cache`、`X-Accel-Buffering: no`），并补 BFF 超时（现无）。**属主说明**：`server.ts` 通用代理改造归分册 08（前端与 BFF）C 系列，本册登记为跨册接缝需求并给出 AI 侧验收断言，不重复立项。
2. 超时拉平（X-80）：`gateway/application.yml:105 request-timeout: 60s` vs SSE 300s 必须写成显式契约（推荐 AI/Agent 前缀单独 300s、其余 60s），否则 F06-14 的 `LLM_TIMEOUT` 判定与网关超时互相打架。
3. 死调用（X-77 全 9 条）只允许两种处置：① 前端改指真端点（如会话列表需后端**新增** `GET /api/v1/agent-loop/sessions`，遵只增不改 + 三滤波器）；② 前端删除调用与入口（如 `/agents/{id}/versions` 系版本能力应经 Git 单通道，AGENTS.md 约束 6）。**禁**为迁就死调用造第二个属主端点 —— X-78 的 `/knowledge/reason`（文档宣称 + 下游真调 + 实现不存在）即为反面案例。
4. 死界面（X-75/X-76）整批**删除**（不是标 deprecated），判据 = importer 集合为空且无路由挂载；`Marketplace.tsx` 本轮已复验为空；`components/aiworkbench/agent-studio/` 若含唯一诊断码实现则**先迁移再删**，禁把能力一起删掉。
5. 联动订正：`EngineCapabilityRegistryImpl.java:38/:112` 的 404-target 端点改指 kb-engine 真端点或标 501 + `stub=true`（与分册 05 §0.2-⑦ 同裁定）。
**验收**：`*.spec.ts › ai › 生产链路下流式首帧早于完整响应到达`（硬判据：收到 ≥2 个 `delta` 帧时上游尚未完成）；`mvn -Dtest=AiRouteIntegrityTest#everyFrontendAiPathHasOwningMapping`（解析前端 AI 端点字符串集与后端 mapping 结果集做差，断言差集为空）；`mvn -Dtest=AiRouteIntegrityTest#sessionsListEndpointExistsInBothPrefixes`；`mvn -Dtest=FrontendAiDeadCodeTest#noUnimportedAiComponentRemains`。

### F06-22 AI 测试底座与集成门禁（虚假验收的反制件）

**陈述**：本册所有 `*IT` / 集成形态验收的前置条件 —— 全仓 `@SpringBootTest` 0 命中（X-87）意味着"集成断言"当前无处安放。
**规则锚点**：Q13（验收只允许可执行标识）；AGENTS.md/§十二 补记（Playwright 全量工程化，工程建成前任何"E2E 通过"表述视为虚假验收）。
**差距**：X-74、X-86、X-87。
**要点**：
1. **后端集成底座**：在 `engine/ai-engine/ai-engine-impl/src/test` 与 `runtime/llm-gateway/src/test`（现为空）建 `@SpringBootTest` 薄切片（`webEnvironment=MOCK` + `TestRestTemplate`），security 与 provider 用可切换桩边界（ALLOW/DENY/FAIL_CLOSED 三态注入、429/超时/不可解析响应注入）；该底座是 F06-01/03/04/07/09/11/12/13/14 验收的载体，**必须与首个 P0 改造项同批落**，否则红线无验收。
2. **两态各跑一次**（4.5 矩阵要求）：同一断言分别在 gateway fat-JAR 态与 aiming service 态执行（profile 切换），防"gateway 态测过 = 能力存在"的假阳性（X-26 与 R-18 的现实形态）。
3. **契约制品生成**：以 `AiRouteIntegrityTest` 的源码扫描替代缺失的 OpenAPI 制品（X-86），并据此**生成** `docs/40-实现/features/20260929-ai-engine/AI_ENGINE_API_CONTRACT.md` 作为本域首份契约文档 —— 由测试产出而非手写，避免再次失真（X-73/X-78 教训）。
4. 清掉"把桩锁成期望"的既有测试：`CognitionViewE2E.test.tsx` 的 404-stub 断言（X-87）改为断言真端点或显式 501-stub 语义。
**验收**：`mvn -Dtest=AiTestHarnessSmokeTest#springBootTestSliceBootsInBothModes`（**前置门禁**：本条不过则本册所有 IT 验收一律标"未执行"，不得标"通过"）；`mvn -Dtest=GatewayAbacModeTest#abacDenyIsNotSilentlySkippedInServiceMode`；`mvn -Dtest=AiIntegrationCoverageTest#aiEngineAndGatewayHaveAtLeastOneRealIT`（计数断言，防"目录建成但无用例"）。

---

## 三、B 章 — 界面设计

### 3.1 AI 面信息架构（本册属主 = 智能域；工作台容器属主 = 分册 07）

```
场景工作台（分册 07 容器）
└─ 右栏 AI 面板  ←── 本册唯一"场景内 AI 入口"，其余 AI 页均在导航独立域
   ├─ 对话区（单一 ChatPanel，F06-19 收敛 ×3 → ×1）
   │  ├─ 消息流：user / assistant / tool-call（折叠）/ refusal（澄清卡）/ citation-block（引用条）
   │  ├─ 状态条：压缩状态（F06-19-4）· 运行状态（plan→act→final）· 诊断码 traceId
   │  └─ 错误气泡：七类错误模板（F06-14）
   ├─ 证据区：工具返回的 forecastRunId / caliberId@version / asOfTime / evidenceLinks（可点击）
   └─ 用量区：prompt/completion tokens（真值，F06-07）· 配额剩余 · 护栏裁决计数

AI 工作台（导航独立域，非场景内）
├─ Agent Studio（配置/白名单/工具注册表只读视图，F06-11 声明式条目）
├─ Guardrails 审计页（GUARDRAIL_EVAL 事件列表 + decision 分布，F06-03）
├─ Model Catalog（provider 目录真值，禁硬编码 cost，X-68）
└─ Run Replay（按 traceId 步骤时序视图，F06-08）
```

### 3.2 状态矩阵（每个 AI 面板必须实现的六态）

| 态 | 触发条件（后端语义） | 渲染要求 | 禁止行为 |
|:--|:--|:--|:--|
| 空 | 200 + `data:[]` | 空态插画 + 引导文案（i18n key） | 禁渲染成"错误"；禁 console 静默 |
| 加载 | 请求 in-flight / SSE 已连接未出首字 | 骨架 + 流式光标；步骤进度取 `EVT=AgentLoop op=` | 禁显示假进度百分比 |
| 故障 | 5xx / `ECOS-AI-*` | **显式错误态** + 错误码 + 诊断码 traceId + 重试（受 F06-14 重试上限约束显示是否可重试） | **禁 `catch → 空态`**（X-67/分册 04 F04-03 同罪）；禁 `e.getMessage()` 原文（X-49） |
| 权限拒绝 | `GUARDRAIL_DENIED` | "当前权限不支持该操作"，**不显示资源存在性细节**（PRD §1.5-3） | 禁回显 DENY reason 原文（含 policyId 也不给终端用户） |
| 护栏不可用 | `GUARDRAIL_FAIL_CLOSED` | 面板级暂停横幅"安全服务暂不可用，AI 操作已暂停" + 诊断码 | 禁继续发起工具调用；禁降级为"本地正则放行" |
| 显式 stub | 501 + `stub=true` | 灰色"能力未上线"标记（分册 05 同裁定） | **禁 200 空 body 伪装成功**（用户裁定 A3 / B-4） |

### 3.3 前端契约要点（与后端一一对应）

1. **诊断码**：所有 AI 错误态与流式响应必须显示 traceId（完整 UUID，禁 X-36 的 8 位串），文案 `t('ai.diagnosticCode')`；用户报障凭此码 grep 日志（F06-06 验收）。
2. **引用块渲染**：`citation-block` 是**结构化组件**而非文本 —— 字段 `{forecastRunId, caliberId, version, asOfTime, links[]}` 逐个渲染，链接可点击跳证据详情（分册 09 的 evidence 端点）。数值必须与引用块**同气泡**出现，渲染层再校验一次（缺引用块 → 气泡降级为"无依据数值，已隐藏"，与 F06-12 后校验互为双保险）。
3. **压缩状态条**：`compression_mode=TRUNCATE_FALLBACK` 时告警色（`useTheme()` token）+"摘要降级，历史可能不完整"（PRD §5.1 CONTEXT_OVERFLOW 消息）。
4. **用量区**：`estimated=true` 的 token 必须显示"估算"标记（F06-07-1 的诚实性要求在 UI 的落点）。
5. 合规硬约束（X-67 清零）：主题 token / `useLanguage()` / lucide-react / 组件 ≤800 行 / 每 Tab 独立文件 / auth 头唯一走 `services/auth.ts`。

### 3.4 移动端

只复用既有三件套；AI 面板在移动端折叠为"对话 + 证据"两段，用量与回放入口移入溢出菜单；`MobileDataTable` 承载护栏审计列表。**不新建移动专用组件**。

---

## 四、C 章 — 技术设计（控制流 · 数据流）

### 4.1 主控制流：带裁决咽喉的 Agent 单轮

```
入口（BFF→gateway:8080→[fat-JAR 宿主] 或 →aiming:18084 [service 态]）
 │  VersionPrefixRewriteFilter → SecurityConfig → ClearanceInterceptor（三滤波器，AGENTS.md 约束 2）
 ├─ 0. MDC 写 traceId（读 X-Request-Id，无则 UUID 并回写响应头）              [F06-06]
 ├─ 1. 输入护栏：security-engine 校验（不可用→GUARDRAIL_FAIL_CLOSED，终止）    [F06-04]
 ├─ 2. 上下文预算：estimateTokens(ctx) > limit×0.6 ? 压缩 : 继续              [F06-09]
 ├─ 3. EVT=AgentLoop op=plan
 ├─ 4. llm_call：唯一经 LLMGateway（引擎无 key、无 Provider 抽象）             [F06-05]
 │      └─ gateway 出口：resolveSecret(apiKeyRef) → ABAC(REST) → provider → 计量 [F06-07]
 ├─ 5. 解析工具调用（PARSE_FAILURE → 重生成 ≤2）                                [F06-14]
 ├─ 6. 咽喉 execute(toolName, args) ─────────────────────────────────────┐
 │      ├─ 6a policyEvaluate：组装四段载荷 → POST evaluate（2s）          │  [F06-01/02]
 │      │      ALLOW / DENY / FAIL_CLOSED(超时·异常·不可解析)            │
 │      ├─ 6b GUARDRAIL_EVAL 审计（发布失败=失败，不吞）                  │  [F06-03]
 │      ├─ 6c sandboxReview（附加防御，异常→DENY）                        │  [F06-01-4]
 │      ├─ 6d dispatch：REST/BUILTIN/SQL → 经 runtime-access 出向         │  [F06-17]
 │      └─ 6e obligations：security mask/rowFilter 处理结果               │  [F06-02]
 │             └─ 结果含引用元数据 {runId,caliberId,version,asOfTime,links}│ [F06-11-4]
 ├─ 7. EVT=AgentLoop op=tool_result（decision/latency/tokens 必填）
 ├─ 8. 循环至 final（迭代上限 + 会话 token 配额双重护栏）                   [F06-07-3]
 ├─ 9. 后校验：数值↔引用块一致性，违规重生成 ≤2 → 兜底话术                  [F06-12]
 └─ 10. 输出护栏（security）→ SSE/ApiResponse(+traceId) → 审计 GUARDRAIL_EVAL
```

**关键不变式**（写进测试而非注释）：
- I-1 任何一次工具执行 ↔ 恰好一条 `GUARDRAIL_EVAL`（PRD §1.5-1 对账依据）；
- I-2 `decision != ALLOW` ⇒ 无出向调用（无 SQL/HTTP/文件 IO 副作用）；
- I-3 最终回答含数字 ⇒ 数字 ∈ 本轮工具结果集 ∧ 引用块非空；
- I-4 引擎侧不存在明文 api-key 字段赋值路径；
- I-5 `success=false` 的结果 ⇒ content 内不含 `"status":"success"`。

### 4.2 裁决决策树（咽喉 6a）

```
evaluate 请求（超时 2s）
 ├─ 200 且 decision=ALLOW 且 obligations 可解析 → 放行（obligations 交 6e）
 ├─ 200 且 decision=DENY                        → GUARDRAIL_DENIED（不重试，可读消息，审计）
 ├─ 200 但 decision 字段缺失/未知取值             → FAIL_CLOSED（禁"默认放行"兜底）
 ├─ 连接失败 / 4xx 非 400 / 5xx / 超时 / 响应不可解析 → GUARDRAIL_FAIL_CLOSED + monitor 告警 + ERROR 日志
 └─ security-engine Bean/类不可达（service 态，R-18）→ 同上 FAIL_CLOSED
```
> 现网 `AgentToolSqlSecurityGate` 已把"未装配→拒绝"做对（X-10），本节只补三件事：**作用域**（全工具类型）、**超时值**（2s）、**obligations 与审计**（X-18/X-20）。

### 4.3 压缩控制流（F06-09）

```
每轮入模前：tokensEstimate(ctx) vs modelLimit×threshold
 ├─ 未触发 → 原样入模
 └─ 触发：
     ① 划窗口：最近 K=6 轮 = 保留集；更早 = 压缩集
     ② 引用预提取（确定性正则）：runId/caliberId/evidenceUrl/tool_call_id → keyRefs
     ③ tool_call_id 交叉检查：压缩集结果若被保留集引用 → 移出压缩集（保留原文）
     ④ llm-gateway 摘要调用（结构化模板，keyRefs 原文附于 prompt）
     ⑤ 引用完整性硬校验：runIds(压缩集) ⊆ keyRefs(摘要)
         ├─ 通过 → 写摘要行(LLM_SUMMARY) + 压缩集 UPDATE is_deleted=1（禁 DELETE）
         └─ 不通过/摘要失败 → 截断最早轮次（TRUNCATE_FALLBACK）+ warn + monitor 事件
     ⑥ 返回 compression_mode + compressedTurns 供状态条（B.3-3）
```

### 4.4 数据流与落点

| 流 | 产生点 | 传输 | 落点 | 读取方 |
|:--|:--|:--|:--|:--|
| 会话消息 | Loop 每轮 | Mapper（schema 限定）→ `ecos_ai.sys_agent_*`（归属待 R-19） | 同表，逻辑删除 | 上下文组装、压缩、回放 |
| 裁决事件 | 咽喉 6b | runtime-event → Kafka `ecos.audit` | `GUARDRAIL_EVAL` | 护栏审计页、§1.5-1 对账 |
| 用量事件 | llm-gateway 出口 | 同上 | `LLM_USAGE` | 用量区、配额判定、sysman 审计库 |
| 结构化日志 | 每步（op=*） | logger（MDC traceId） | 本地日志/采集 | 回放（F06-08）、报障 grep |
| 引用元数据 | 工具返回 | 结果体内 | 消息表 + 前端气泡 | 后校验、证据区 |
| 摘要副本 | 压缩 ④/⑤ | Mapper | 新 system 行（TEXT，MC02） | 上下文组装 |

**禁**：新建"AI 回放表"、"AI 用量业务表"（PRD §3.2 明令不落新业务表）；`ecos_ai` 内引入 jsonb（X-53 存量待整改，新增一律 TEXT）。

### 4.5 两态可达性矩阵（ADR-15 承压点，联动 R-18/R-16）

| 组件 | gateway fat-JAR 态（:8080，承流目标） | aiming service 态（:18084，dev/直连） | 设计要求 |
|:--|:--|:--|:--|
| ai-engine 32 Controller | 在 JVM 内 | 在 JVM 内（靠 ComponentScan） | 两态同一鉴权水平（分册 05 C113 同罪，本域不重复立项，引用 W→C 映射） |
| security OPA 裁决 | 同 JVM Bean 可达（反射可成） | **类不在 classpath → 恒 DENY** | 裁决必经 **REST**（F06-05-4 默认①），使两态同形 |
| llm-gateway | 在 JVM 内 | 在 JVM 内 | 计量/ABAC 两态一致 |
| `runtime.llm.repository` mapper | 注册 | **三处显式排除 → 不注册** | 按档位显式启用（F06-20-⑥），禁静默断链 |
| `AgentToolSqlSecurityGate` 超时 5s | 同 | 同 | 两态统一 2s |

> 该矩阵是本册最容易被忽略的验收面：**"在 gateway 态测过"不等于"能力存在"**。所有 F06 验收测试必须在 service 态（aiming）至少跑一次等价断言。

---

## 五、D 章 — 接口设计

### 5.1 既有 AI 接口面（实测，只增不改）

| 前缀 | 属主 Controller | 实测 mapping | 处置 |
|:--|:--|:--|:--|
| `/api/v1/agent-loop` | `AgentLoopController`（`:36`） | `/chat`(`:73`)、`/chat` SSE(`:138`)、`/sessions`(`:250`)、`/sessions/{id}`(`:285`)、`/sessions/{id}/chat`(`:358`) | 保留；响应体补 `traceId`（分册 00 接缝）；SSE 事件加诊断码（F06-06） |
| `/api/v1/oag` | `OagController` / `gateway/oag/OagForwardController`（`:145`） | `/chat/stream` 等 | 保留；`SecurityCheckerNode` 路径已是 fail-closed（X-19），统一到 F06-01 咽喉后不得再自成裁决 |
| `/api/v1/agent/providers` | `AgentProviderController`（`:48`） | GET（现恒空 200） | **端点保留、语义改为真值或 501 + `stub=true`**（F06-05-1） |
| `/api/v1/agent/copilot/*` | `CopilotServiceImpl` 系 | — | 保留；切断 key 穿线（X-23） |
| `/api/v1/guardrails/*` | 护栏配置面 | 前端 4 套客户端指向它 | 保留；数据源改真事件（F06-19-3） |
| `/api/v1/aip/*`、`/api/v1/agent-mesh`、`/api/v1/agent-runtime`、`/api/v1/agent/*`、旧 `/api/agent*` | 多 controller 承载 | 见 X-64 | 属主澄清归分册 08（前端/BFF 收口）；本册只提供 AI 侧路由真值 |
| `/api/v1/chat/stream` | **不存在** | 后端 0 命中（X-65） | 前端死调用改指真端点；**禁**为迁就前端新增 `/api/v1/chat` 别名（会造成第二个会话属主） |

### 5.2 本册新增端点（全部"只加"，双路径 `/api/v1/*` 与 `/api/*` 各写一遍）

| 方法 路径 | 用途 | 请求/响应要点 | 门禁 |
|:--|:--|:--|:--|
| `GET /api/v1/agent/tools` | 工具注册表只读视图（F06-11 声明式条目 + 权限要点） | 响应 `items[{name,operation,backendEndpoint,permission,registered:true}]` | 只读；RLS 按域 |
| `GET /api/v1/agent/runs/{traceId}/replay` | 运行回放（F06-08） | 步骤时序 `{op,step,tool,model,latencyMs,promptTokens,completionTokens,decision,retries[]}`；**不含工具结果原文** | 只读；traceId 属主校验 |
| `GET /api/v1/agent/sessions/{id}/context` | 上下文预算与压缩状态（B.3-3/4） | `{estimatedTokens,limit,threshold,compressedTurns,compressionMode,quotaRemaining}` | 只读；本人会话 |
| `POST /api/v1/agent/tools/{name}/dry-run` | 场景工具干跑（开发期验收，**不进产品路径**） | 走完整咽喉但不执行副作用；响应含裁决载荷与 obligations | ADMIN 角色 + OPA 裁决；生产 profile 下 403 |

> 三滤波器接入（AGENTS.md 约束 2）：以上四条每条都要 ① `VersionPrefixRewriteFilter` 映射 ② SecurityConfig permitAll/鉴权策略显式声明 ③ ClearanceInterceptor 豁免或纳入；漏任一条在 gateway 态表现为 404（分册 04 K-11/K-12 同罪，验收用 `KnowledgeRouteIntegrityTest` 同族的 `AiRouteIntegrityTest`）。

### 5.3 对 security-engine 的消费契约（本域唯一安全入口）

| 用途 | 端点 | 超时 | fail 行为 | 现状态 |
|:--|:--|:--|:--|:--|
| 操作授权（全工具） | `POST /api/v1/security/policy-engine/evaluate` | **2s**（PRD §1.2） | FAIL_CLOSED + 审计 + monitor | 仅 SQL 型调用，5s（X-11/X-19） |
| 列过滤/脱敏 | security mask 端点（分册 01 SEC-02） | 2s | 丢弃结果 + FAIL_CLOSED | **0 接线**（X-18） |
| 行级 RLS | security RLS 端点（分册 01 SEC-01） | 由下游查询承担 | 拒绝查询 | 场景工具未接（F06-11） |
| 审计 | Kafka `ecos.audit`（`KafkaTopics.AUDIT`，经 runtime-event） | — | **发布失败=裁决失败** | fail-open（X-20） |
| 密钥取得 | security 密钥服务（`SecurityEngineBridge.resolveSecret`） | — | null → DENY，不回退明文 | 已实现但被绕过（X-23） |

裁决请求载荷 = PRD §1.3 逐字四段；响应载荷需 security 侧扩展 `obligations` 与 `policyId`（跨册接缝，登记为分册 01 的追加项，本册不代为设计其内部结构）。

### 5.4 SSE 契约

```
event: meta      data: {"traceId":"<full-uuid>","sessionId":"...","model":"..."}   ← 首帧必发
event: step      data: {"op":"plan|tool_call|tool_result|llm_call|final","step":n,"tool":"...","decision":"ALLOW"}
event: token     data: {"promptTokens":n,"completionTokens":n,"estimated":bool}    ← 每个 llm_call 后
event: delta     data: {"text":"..."}
event: citation  data: {"forecastRunId":"...","caliberId":"...","version":"...","asOfTime":"...","links":[...]}
event: error     data: {"errorCode":"ECOS-AI-503","class":"GUARDRAIL_FAIL_CLOSED","message":"安全服务暂不可用，AI 操作已暂停（诊断码 {traceId}）","retryable":false}
event: done      data: {"traceId":"...","totalTokens":n,"compressionMode":"LLM_SUMMARY|TRUNCATE_FALLBACK|null"}
```
约束：鉴权必须可经 SSE（现 `pages/aiworkbench/api.ts:447` 的 fetch-stream 不带 Authorization，X-66）；`error` 帧永不携带 `e.getMessage()` 原文（X-49）；含数值的 `delta` 若在 `done` 前未发 `citation` → 服务端按 F06-12 拦截并把该段替换为兜底话术。

### 5.5 错误码表（`ECOS-AI-*`，与 PRD §5.1 七类一一对应）

| HTTP | errorCode | 错误类 | 重试 | 用户消息模板 | 日志级别 |
|:--:|:--|:--|:--:|:--|:--|
| 504 | `ECOS-AI-504` | LLM_TIMEOUT | ≤3 退避 | AI 服务响应超时，请稍后重试（诊断码 {traceId}） | WARN |
| 429 | `ECOS-AI-429` | LLM_RATE_LIMITED / 配额超限 | ≤3 后退备用 provider | AI 服务繁忙… | WARN |
| 500 | `ECOS-AI-500P` | PARSE_FAILURE | 重生成 ≤2 | AI 输出格式异常，已重试 | ERROR（原文只入日志） |
| 413 | `ECOS-AI-413` | CONTEXT_OVERFLOW | 0（截断降级） | 会话摘要降级提示 | WARN |
| 403 | `ECOS-AI-403` | GUARDRAIL_DENIED | 0 | 当前权限不支持该操作 | INFO + 审计 |
| 503 | `ECOS-AI-503` | GUARDRAIL_FAIL_CLOSED | 0 | 安全服务暂不可用，AI 操作已暂停 | ERROR + 审计 + monitor |
| 502 | `ECOS-AI-502` | TOOL_BACKEND_ERROR | ≤1 | 数据服务异常（{service}），请稍后重试 | ERROR |
| 400 | `ECOS-AI-400` | 参数非法/工具未注册 | 0 | 字段级提示 | WARN |
| 400 | `ECOS-AI-400C` | 口径缺失/未批准（澄清） | 0 | 澄清提问（不给数，F06-13） | INFO |
| 422 | `ECOS-AI-422` | 引用缺失（后校验兜底） | 已内含 ≤2 | 无法提供有依据的数值 | WARN |
| 404 | `ECOS-AI-404` | 会话/运行不存在 | 0 | — | INFO |
| 501 | `ECOS-AI-501` | 显式 stub（`stub=true`） | 0 | 能力未上线 | INFO |

**禁**：`Exception → 404`（分册 01 C 系列改造项，本册引用）；`e.getMessage()` 进响应（X-49）；503 被 BFF 吞成 200 空 body（用户裁定 A3）。

---

## 六、E 章 — 数据设计

### 6.1 AI 域载体归属实测（双镜像 + 空壳 + 真身三态并存）

| 表（逻辑名） | `ecos_ai` 侧 | `public` 侧 | 代码引用（裸表名） | 判定 |
|:--|:--|:--|:--|:--|
| `sys_agent_message` | **不存在** | 6 行 | `FROM sys_agent_message` ×9、`INTO` ×3 | 真身在 public |
| `sys_agent_session` | **不存在** | 18 行 | `UPDATE` ×7、`FROM` ×3 | 真身在 public；`message_count` 合计 ≥18 与 6 行不符（X-59） |
| `ecos_agent_registry` | 0 行 | 16 行 | `FROM` ×5、`UPDATE`/`INTO` ×1 | **双镜像**，真身 public |
| `ecos_tool_definition` | 0 行（`schema_json jsonb`） | 存在 | `FROM ecos_tool_definition` ×1、`FROM ecos_ai.ecos_tool_definition` ×1 | **双镜像 + 一条显式跨库引用**（两种写法并存即 bug 面） |
| `agent_execution` / `agent_execution_step` | 0 行 | 存在 | — | 双镜像 |
| `agent_memory` / `ecos_agent_memory` | 0 行 | 15 行 | — | 双镜像（且两个命名变体） |
| `sys_agent_call_log` | 0 行 | 0 行 | mapper 未注册（X-8/X-39） | **两处皆空 → 计量从未落地** |
| `sys_agent_profile` | 0 行 | 5 行 | — | 双镜像 |
| `agent_cost`（+25 月分区 +default） | 0 行 | — | — | 空分区集群（X-56） |
| `ecos_decision_case` | id BIGINT nextval | — | — | 违 MC01 |

> `V47` 的 `ALTER DATABASE sys_man SET search_path`（X-61）正是让上述裸表名**今天还能跑**的唯一原因；一旦换库/换 search_path，9+ 处 SQL 立即崩。这是本域最隐蔽的单点依赖，必须在 E.4 门禁里显式判。

### 6.2 新增 DDL（编号 **V200** 起；遵 MC01/MC02/MC03 + DR06~DR08 + schema 限定）

> 依报告 §14.4：**R-18~R-23 已批准 ⇒ 相关 DDL 按推荐项写作并**落迁移脚本文件**（不实跑库）**；`V204` 与分册 04 `V175` 同属"纯补建缺失表、无归属争议"例外，同批落地。

> **【2026-09-30 收口对照（脚本为准）】** 本节 SQL 块四张表已按 DR02（新表必带 `ecos_` 前缀）改名并已在单源脚本落地：
> `ai_tool_registry`→**`ecos_ai.ecos_ai_tool_registry`**、`ai_tool_permission_binding`→**`ecos_ai.ecos_ai_tool_permission_binding`**、
> `ai_compression_batch`→**`ecos_ai.ecos_ai_compression_batch`**、`ai_guardrail_decision_ledger`→**`ecos_ai.ecos_ai_guardrail_decision_ledger`**
> （实测四表在库内不存在、全仓 Java/XML/TS 零引用 ⇒ 改名无回归面，故红线优先于文档字面名）。
> 唯一索引名同时按《数据库访问规范》索引命名表归一：`uk_atr_key`→**`uniq_atr_key`**（其余 `uk_*`/`uq_*` 同法前缀改 `uniq_`）。
> 迁移脚本**文件名未改**（`V200__ai_tool_registry.sql` 等），以保持 `psql -f` 引用稳定；文件名↔表名映射见落地登记清单。

```sql
-- V200__ai_tool_registry.sql（F06-11；归属 schema = ecos_ai，ST07 + ARCH_SPEC §4.3）
CREATE TABLE IF NOT EXISTS ecos_ai.ecos_ai_tool_registry (
  id VARCHAR(36) NOT NULL,
  tool_key       VARCHAR(120)  NOT NULL,
  name           VARCHAR(200)  NOT NULL,
  description    TEXT,
  operation      VARCHAR(10)   NOT NULL DEFAULT 'read',
  args_schema    TEXT          NOT NULL,           -- MC02: JSON 只用 TEXT，禁 jsonb
  backend_kind   VARCHAR(20)   NOT NULL,           -- REST | SQL | BUILTIN
  backend_endpoint TEXT,
  permission_hint  TEXT,                            -- 例 "scenario:write"
  opa_action_type  VARCHAR(40) NOT NULL DEFAULT 'TOOL_CALL',
  resource_scope_projection TEXT,                   -- 允许进载荷的入参字段白名单（逗号分）
  timeout_ms     INTEGER       NOT NULL DEFAULT 2000,
  status         VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
  trace_id       VARCHAR(64),
  domain         VARCHAR(64)   NOT NULL DEFAULT 'DEFAULT',
  version_no     INTEGER       NOT NULL DEFAULT 1,
  is_deleted     SMALLINT      NOT NULL DEFAULT 0,
  create_by      VARCHAR(64)   NOT NULL,
  update_by      VARCHAR(64),
  create_time    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time    TIMESTAMP,
  CONSTRAINT pk_ecos_ai_tool_registry PRIMARY KEY (id),
  CONSTRAINT ck_atr_operation CHECK (operation IN ('read','write')),
  CONSTRAINT ck_atr_status    CHECK (status IN ('ACTIVE','DISABLED','DRAFT'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_atr_key ON ecos_ai.ecos_ai_tool_registry (tool_key, is_deleted);
```
> 与既有 `ecos_tool_definition`（jsonb + `created_at/updated_at` + 无基线列 + 双镜像）**并建新表而非改造**，理由同分册 04 `V178/V179/V184` 路线，且改造需 R-19 裁决；旧表只停写。

```sql
-- V201__ai_tool_permission_binding.sql（F06-02 载荷投影与权限映射，禁把 LLM 任意入参塞进裁决载荷）
CREATE TABLE IF NOT EXISTS ecos_ai.ecos_ai_tool_permission_binding (
  id VARCHAR(36) NOT NULL, tool_key VARCHAR(120) NOT NULL,
  role_key VARCHAR(64) NOT NULL,           -- 允许调用该工具的角色
  required_attributes TEXT,                -- 载荷必需主体属性（roles/department/dataScope）
  obligation_expectation TEXT,             -- 期望 obligations 形态（mask/rowFilter），用于验收对账
  domain VARCHAR(64) NOT NULL DEFAULT 'DEFAULT', version_no INTEGER NOT NULL DEFAULT 1,
  is_deleted SMALLINT NOT NULL DEFAULT 0, create_by VARCHAR(64) NOT NULL, update_by VARCHAR(64),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time TIMESTAMP,
  CONSTRAINT pk_ai_tool_perm PRIMARY KEY (id)
);

-- V202__ai_session_compression_governance.sql（F06-09：逻辑删除 + 摘要批次 + token 预算）
-- 目标表归属随 R-19（现真身 = public.sys_agent_*，权威 = ecos_ai）；DDL 以占位 <AI_SCHEMA>. 书写
ALTER TABLE <AI_SCHEMA>.sys_agent_message ADD COLUMN IF NOT EXISTS compression_batch_id VARCHAR(36);
ALTER TABLE <AI_SCHEMA>.sys_agent_message ADD COLUMN IF NOT EXISTS compressed       SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE <AI_SCHEMA>.sys_agent_message ADD COLUMN IF NOT EXISTS is_deleted        SMALLINT NOT NULL DEFAULT 0;
ALTER TABLE <AI_SCHEMA>.sys_agent_message ADD COLUMN IF NOT EXISTS tool_call_ref_ids TEXT;    -- 引用完整性（F06-09-③）
ALTER TABLE <AI_SCHEMA>.sys_agent_message ADD COLUMN IF NOT EXISTS key_refs          TEXT;    -- runId/caliberId/证据（禁摘要丢弃）
ALTER TABLE <AI_SCHEMA>.sys_agent_message ADD COLUMN IF NOT EXISTS token_count       INTEGER;
ALTER TABLE <AI_SCHEMA>.sys_agent_session ADD COLUMN IF NOT EXISTS total_prompt_tokens     INTEGER NOT NULL DEFAULT 0;
ALTER TABLE <AI_SCHEMA>.sys_agent_session ADD COLUMN IF NOT EXISTS total_completion_tokens INTEGER NOT NULL DEFAULT 0;
ALTER TABLE <AI_SCHEMA>.sys_agent_session ADD COLUMN IF NOT EXISTS compression_mode        VARCHAR(24);
ALTER TABLE <AI_SCHEMA>.sys_agent_session ADD COLUMN IF NOT EXISTS update_time             TIMESTAMP;
ALTER TABLE <AI_SCHEMA>.sys_agent_session ADD COLUMN IF NOT EXISTS is_deleted              SMALLINT NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_sam_session_active ON <AI_SCHEMA>.sys_agent_message (session_id, compressed, is_deleted);

-- V203__ai_compression_batch.sql（摘要批次头，供状态条与降级可查）
CREATE TABLE IF NOT EXISTS <AI_SCHEMA>.ecos_ai_compression_batch (
  id VARCHAR(36) NOT NULL, session_id VARCHAR(64) NOT NULL,
  mode VARCHAR(24) NOT NULL, compressed_from_seq INTEGER, compressed_to_seq INTEGER,
  estimated_tokens_before INTEGER, estimated_tokens_after INTEGER,
  kept_key_refs TEXT, dropped_key_refs TEXT,      -- dropped 非空即降级证据（§2.2-2 抽检）
  failure_reason VARCHAR(120), trace_id VARCHAR(64),
  domain VARCHAR(64) NOT NULL DEFAULT 'DEFAULT', version_no INTEGER NOT NULL DEFAULT 1,
  is_deleted SMALLINT NOT NULL DEFAULT 0, create_by VARCHAR(64) NOT NULL, update_by VARCHAR(64),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time TIMESTAMP,
  CONSTRAINT pk_ecos_ai_compression_batch PRIMARY KEY (id),
  CONSTRAINT ck_acb_mode CHECK (mode IN ('LLM_SUMMARY','TRUNCATE_FALLBACK'))
);

-- V204__ai_guardrail_decision_ledger.sql（例外可先落：纯补建、无归属争议 —— §1.5-1 对账的可查询面）
-- 注意：这不是"新业务表"，而是裁决事件的**索引化落点**，与 Kafka ecos.audit 同批双写（PRD §3.2 禁的是把计量落业务表）
CREATE TABLE IF NOT EXISTS ecos_ai.ecos_ai_guardrail_decision_ledger (
  id VARCHAR(36) NOT NULL, trace_id VARCHAR(64) NOT NULL, session_id VARCHAR(64),
  tool_key VARCHAR(120) NOT NULL, decision VARCHAR(20) NOT NULL,
  policy_id VARCHAR(120), obligations TEXT, latency_ms INTEGER,
  subject_snapshot TEXT,                      -- 仅 userId/roles/department，禁入参原文
  domain VARCHAR(64) NOT NULL DEFAULT 'DEFAULT', version_no INTEGER NOT NULL DEFAULT 1,
  is_deleted SMALLINT NOT NULL DEFAULT 0, create_by VARCHAR(64) NOT NULL, update_by VARCHAR(64),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, update_time TIMESTAMP,
  CONSTRAINT pk_agdl PRIMARY KEY (id),
  CONSTRAINT ck_agdl_decision CHECK (decision IN ('ALLOW','DENY','FAIL_CLOSED'))
);
CREATE INDEX IF NOT EXISTS idx_agdl_trace ON ecos_ai.ecos_ai_guardrail_decision_ledger (trace_id);
CREATE INDEX IF NOT EXISTS idx_agdl_tool  ON ecos_ai.ecos_ai_guardrail_decision_ledger (tool_key, decision, create_time);
```

**不新建**：LLM 用量表（走 `LLM_USAGE` 事件 + `sys_agent_call_log` 视图）、回放表（走日志）、引用表（走消息体）。

### 6.3 存量待整改登记（不擅自动，逐条挂裁决）

| 项 | 现状 | 处置路径 | 挂裁决 |
|:--|:--|:--|:--|
| `ecos_ai` 50 张 0 行空壳 | 26 jsonb / PK VARCHAR(64) / 基线列 0/50 | ① DROP 重建（零行+零引用双条件）② 只停写 ③ 并建新表 | **R-19**（承接 R-12/R-13） |
| `public` 在用 5 张 AI 表 | 真身，代码裸表名依赖 | 归位 `ecos_ai` 的时机与停写范围 | **R-19** |
| `sys_agent_message` 历史行 | 现被物理删除（X-31）；本册改逻辑删除 | 存量消息处置：只回填 `is_deleted=0` 不动内容 | **R-20** |
| `V47` 的 `ALTER DATABASE SET search_path` | 让裸表名可用（掩盖风险） | 作废/保留（作废需先完成 schema 限定改造） | **R-22** |
| `ecos-sql/migration/12_to_8_schema.sql` | 第二 DDL 源，`SET SCHEMA` 搬表 | 作废并回归单源目录 | **R-22** |
| 重复迁移版本 V50/V150/V155 | 同名多文件 | 重编号（确定性缺陷，**不等裁决**，W 项直改） | — |
| 三方言 `06_ecos_ai.sql`（oracle 无文档 / MySQL JSON+AUTO_INCREMENT） | 三份均无基线列 | 单源 DDL + 方言生成路线（与分册 00/02 同题） | **R-22** |
| `agent_cost` 25 空分区 | 无用途说明 | 随空壳一并处置 | **R-19** |
| `ecos_decision_case.id` nextval | 违 MC01 | 新表不复用；存量按 R-12 判定 | **R-19/R-12** |

---

## 七、F 章 — 符合性与改造项（W140 ~ W164 → 回填 ARCH_SPEC C122 ~ C146）

> 纪律：每条 W 必须回填 `架构设计说明书` §十一 为 C 系列行（"不得只在分册内部登记而不回填本表"）。本册 25 条已在收卷时一次性回写。

| W | 偏差（实测锚点） | 目标状态 | 优先级 | 批次 | 设计项 | REQ | 回填 C |
|:--:|:--|:--|:--:|:--:|:--:|:--:|:--:|
| W140 | 裁决咽喉作用域仅 SQL 型工具，8 类工具（含未来 5 个场景工具）不经 OPA（X-9~X-12） | 咽喉全工具类型强制裁决，禁新增平行 Dispatcher | **P0 红线** | G4 | F06-01 | AI-01 | C122 |
| W141 | `sandboxReview` 审查器异常 → `return null` 放行（X-14） | 异常→DENY；高危正则降为附加防御 | **P0** | G4 | F06-01 | AI-01 | C123 |
| W142 | 输入/输出护栏 `required=false` + `catch→warn` 双 fail-open，且 `GuardrailsServiceImpl` 纯本地正则从不问 security（X-15/X-16） | 必经 security-engine，不可用即整次拒绝 | **P0** | G4 | F06-04 | AI-01 | C124 |
| W143 | `GUARDRAIL_*`/`LLM_USAGE` 事件全仓 0 命中；审计 `ObjectProvider` 缺失即继续（X-17/X-20/X-58） | 每裁决一事件、发布失败=裁决失败、经 runtime-event | **P0** | G4 | F06-03 | AI-01 | C125 |
| W144 | obligations 不承载不执行，LLM 可见未脱敏结果（X-18） | mask/rowFilter 经 security 端点在回模前执行 | **P0** | G4 | F06-02 | AI-01 | C126 |
| W145 | 裁决超时 5000ms ≠ PRD 2s（X-19） | 2s 且与网关超时分层不冲突 | P1 | G4 | F06-01 | AI-01 | C127 |
| W146 | 引擎侧 `LLMProvider` 抽象（0 实现）+ 三处依赖 + `providers` 端点恒空 200（X-21/X-22） | 抽象删除、双门面归一、空目录→501 `stub=true` | P1 | G4 | F06-05 | PLT-21 | C128 |
| W147 | 裸 api-key 由引擎侧 `setApiKey` 穿线，架空 gateway 的 fail-closed 取密（X-23） | 引擎不持 key，gateway 经 security 解析 `apiKeyRef` | **P0 安全** | G4 | F06-05 | PLT-21/AI-03 | C129 |
| W148 | 3 处 `new RestTemplate()` 绕 runtime-access（X-24） | 一律经 runtime-access 且自动带 traceId | P1 | G4 尾 | F06-17 | §2.5-1 | C130 |
| W149 | 11 处自建线程/池，含**底座内部** `hermes-scheduler` 与每调用新建池、裸 `new Thread`（X-25） | 经 runtime-task（§1.6 双入口，任务表单源） | P1 | G4 尾 | F06-17 | §2.5-3 | C131 |
| W150 | ArchUnit 宇宙缺 `runtime/llm-gateway`；调度谓词三类漏检；无安全集成强制卡（X-26~X-28） | 宇宙补齐 + 谓词扩 `Executors`/`runAsync`/`Thread` + §2.4-8 规则落地 | P1 | G4 | F06-17 | §2.4-8/§2.5 | C132 |
| W151 | 压缩为条数阈值(20) + 200 字截断拼接 + **物理 DELETE** + 引用丢弃 + tool_call 完整性无保护（X-29~X-33） | token 阈值 + LLM 结构化摘要 + keyRefs 硬校验 + 逻辑删除 | P1 | G4 | F06-09 | AI-02 | C133 |
| W152 | `SessionManagerImpl` map 无淘汰、`AgentTracer` 静态 map 竞态淘汰（X-34/X-36） | TTL+LRU（清扫经 runtime-task），closeSession 必移除，内存门禁可判 | P1 | G4 | F06-10 | AI-02/NF | C134 |
| W153 | traceId = 进程内 8 位串；`X-Request-Id`/`MDC`/`EVT=`/`promptTokens` 0 命中；op 枚举错位；tokens/latency 恒 0（X-36~X-38/X-41） | MDC 全链贯通 + 五态结构化日志 + SSE 诊断码 | P1 | G4 | F06-06 | AI-03/NF-04 | C135 |
| W154 | 计量两处断点（mapper 未注册 + 事件缺失）；配额键 0 命中（X-8/X-39/X-40） | gateway 唯一计量点 + `LLM_USAGE` + 会话配额拒绝 | P1 | G4 | F06-07 | AI-03 | C136 |
| W155 | 五个场景工具 0 实现；白名单来自硬编码 `DataInitializer:83` 且含未注册的 `knowledge_extract`（X-43/X-46） | 声明式注册表 + 权限绑定 + 配置实现一致 | P1 | 场景批次 B | F06-11 | AI-04 | C137 |
| W156 | 引用块与后校验 0 实现，且 `query_db` 允许 LLM 生成 SQL 直查（X-13/X-44/X-45） | 数值↔引用一致性后校验（拦截重生成 ≤2）+ 场景取数禁走 query_db | **P0**（§0.2.1） | 场景批次 B | F06-12 | AI-04 | C138 |
| W157 | 七类错误 0 实现；`ErrorCodeMapper` 死代码；重试为一次性 ad-hoc（X-47/X-48/X-88） | 分类 + 有界退避 + 模板消息 + `ECOS-AI-*` 码表 | P2 | G4 尾批 | F06-14 | AI-05 | C139 |
| W158 | `buildMockToolResult` 返回 `success=false` 却含 `"status":"success"`，异常亦走此路（X-50） | 删除 mock-success；失败即结构化失败 | **P0**（幻觉源） | G4 | F06-14 | AI-05/AI-04 | C140 |
| W159 | 187 处 `catch (Exception`、67 处 `e.getMessage()`，5 处写入响应体（X-49） | 边界才捕 + 必分类；响应只给 errorCode+traceId | P1 | G4 尾 | F06-15 | AI-05 | C141 |
| W160 | 17 个 Service 持 JdbcTemplate、`@Transactional` 0、裸表名依赖库级 `search_path`（X-32/X-61） | Mapper 层 + schema 限定 + 事务边界；解除 search_path 单点依赖 | P1 | G4 | F06-16 | DB §四/§五 | C142 |
| W161 | `ecos_ai` 50 张空壳（26 jsonb / PK64 / 基线列 0/50 / nextval PK / 25 空分区）+ **8 组双镜像** + 真身在 `public`（X-52~X-57） | 归属唯一化（待 R-19）；新增表一律 MC/DR 合规 | P1 | 场景批次 B | F06-18 | AI-02/03/ST07 | C143 |
| W162 | 迁移重复版本 V50/V150/V155 + 第二 DDL 源 `ecos-sql/migration/12_to_8_schema.sql` + 三方言分叉（oracle 无文档）（X-60~X-62） | 单源目录 + 版本唯一 + 方言生成（重复版本属直改不等裁决） | P1 | G4 | F06-18 | §3.1 | C144 |
| W163 | BFF 缓冲 SSE / 9 条死调用（3 条 live）/ 约 9 900 行死界面 / live 页客户端伪造 LLM 回复 / 自填 `admin` 身份 / i18n 缺 49.4%（X-75~X-85） | 流式承流打通 + 死调用清零 + 死码删除 + 真值化 + auth 单通道 | P1 | G4~G5 | F06-19/F06-21 | FE-01/AI-03 | C145 |
| W164 | 全仓 `@SpringBootTest` 0 / llm-gateway+aiming 0 测试 / OpenAPI 0 / 三份 agents.md 假记载 / LLM 配置三处重复（X-71~X-73/X-86~X-88） | 测试底座建成（红线验收载体）+ 契约文档由测试产出 + 文档真实性门禁 | P1 | G4 | F06-20/F06-22 | Q13/§九/§十 | C146 |

### 7.1 上游制品回写清单（本册实测对其它文档提出的修改要求）

| 目标文档 | 回写内容 | 缘由 |
|:--|:--|:--|
| `PRD-06` §1.2 | "统一入口封装 = `ToolDispatcher`" 改为 **"既有唯一入口 `ToolExecutorService.execute(...)` 扩裁决面"**；新建平行类会造第二个可绕过入口 | X-9（类名 0 命中、咽喉已存在） |
| `PRD-06` §1.3 | `evaluate` 响应需含 `policyId` 与 `obligations`；现 `Decision` 只有 allowed/reason | X-18；需分册 01 侧 security 契约扩展（跨册接缝） |
| `PRD-06` §3.1-2 | op 五态枚举应在 `runtime/common-api` 常量登记，供 06/07/08/09 四册共用 | X-38 |
| `架构设计说明书` C1 | **file:line 已失真**：`DeepSeekProvider.java:56,122` 不存在；真实现状为 `LLMProvider.java`（接口，0 实现）+ `AgentLoopService.java:581,597` | X-21 |
| `架构设计说明书` §4.3 | ai-engine 写权限行 `ecos_ai` 需加"现真身在 `public`，双镜像 8 组"的实测注记，并挂 R-19 | X-52~X-57 |
| `架构设计说明书` §八 内存门禁 | "RSS 峰值 < 基线×1.2"的达标前提 = W152（两处无界）先修；门禁需注明依赖该改造 | X-34/X-36 |
| `AGENTS.md` | "boot 端口自动 +1000"补一句：**`ai-engine-boot/application.yml:2` 实配 18084，与生产 aiming 同端口，直起 jar 会冲突**；`engine/ai-engine/AGENTS.md` 的 `/api/v1/knowledge/reason` 声称作废 | X-6、X-78 |
| `runtime/llm-gateway/agents.md` | 删 `POST /api/v1/agent-loop/run` 虚假端点，改列 4 条真 mapping；补"mapper 在 3 个 service 态被排除"的事实 | X-73 ①、X-8 |
| `engine/ai-engine/ai-engine-boot/agents.md` | 27→32 Controller；"本 impl 有周期任务"改为"0 `@Scheduled`，调度待经 runtime-task" | X-73 ②、X-25 |
| `engine/ai/ai-engine-impl/AGENTS.md` | 测试计数 6/40→7/43；删不存在的 `KnowledgeExtractorService`/`AgentMemory`；**删"evaluate+DENY"安全承诺**（与实测 fail-open 相反） | X-73 ③、X-11/X-14/X-15 |
| `需求检视报告` §13.5 | 新增 R-18 ~ R-23 六项裁决 | 本册 §7.2 |
| `ecos_frontend/server.ts`（分册 08 属主） | 承接 F06-21-1 的 SSE 直通需求（AI 侧先验收） | X-79 |

### 7.2 本册新裁决项（登记至需求检视报告 §13.5）

| R | 议题 | 选项 | 本册推荐 |
|:--:|:--|:--|:--|
| **R-18** | llm-gateway 对 security 的取密与 ABAC 裁决形态 | ① 同 JVM 反射（现状，`SecurityEngineBridge:125,154,178`）；② 改经 security-engine REST（与 `AiSecurityEngineClient` 同款先例）；③ 保留反射 + service 态 REST 兜底并强制 `abac-eval-enabled=true` | **②**。现状在 aiming service 态 security 类不在 classpath → **恒 DENY**（或经 `abac-eval-enabled=false` 一键关断，即绕过面）；两态一致只能靠 REST。选 ① 则 service 态 AI 不可用；选 ③ 保留可关开关，违 §2.4-8 强制卡精神 |
| **R-19** | AI 数据层归属：`ecos_ai` 50 张 0 行空壳（26 jsonb/PK64/基线列 0/50/25 空分区）+ 8 组 `ecos_ai`↔`public` 双镜像 + 真身 5+ 张在 `public` | ① 权威 `ecos_ai`：零行+零引用者 DROP 重建（承接 **R-12** 双条件口径），有引用的 public 真身先定性停写；② 保留空壳只停写，新表另起（本册 V200+ 走此）；③ 承认识 `public` 为 AI 真身，改 ARCH_SPEC §4.3 | **①+②并行**（新表走 V200 并建、空壳待 R-12 同批 DROP）；③ 与 ST07 冲突不可取。**与 R-1/R-4/R-10 属同一批"存量载体归位"判定，建议同批裁决** |
| **R-20** | 会话压缩语义切换（条数+物理 DELETE → token+逻辑归档）时的存量消息处置 | ① 已删行不可恢复，仅对新数据生效 + `message_count` 一次性回填对账（解 X-59）；② 全量重建会话表（清空演示与真实混合行）；③ 保留旧行为做兼容开关 | **①**。存量仅 6 行消息（X-57），重建成本与收益不匹配；②会销毁既有取证；③留下"两种删除语义"正是当前病灶。**已批准 ①**（F06-09-6 按①写作；迁移脚本文件本轮落地、不实跑库） |
| **R-21** | `ai-engine-boot` 与 `services/aiming` 同为 18084（另有 18086 三连冲突、18081/18082/18083 boot↔service 重叠） | ① 改 yml 为 19084（与 `start-backend.ps1` 的 +1000 口径一致）；② 保持脚本注入、配置不动（文档补注）；③ boot 模块废弃 | **①**（并把 18086 三连一并校正）。②使"直接 `java -jar`"路线永远踩坑；③损失调试能力（boot 是引擎单调试唯一入口） |
| **R-22** | DDL 单源与库级副作用恢复 | ① 作废第二源 `ecos-sql/migration/12_to_8_schema.sql` 与 `V47` 的 `ALTER DATABASE … SET search_path`（前置=先完成 schema 限定改造 W160）；② 保留 search_path 作为兼容层但禁止新增裸表名；③ 三方言脚本改由单源 DDL 生成 | **①+③**，但**顺序依赖 W160**（先限定再撤 search_path，否则现网立即崩）；重复版本 V50/V150/V155 属确定性缺陷**不等裁决直改**。承接 R-12 的"只加不删例外口径"议题 |
| **R-23** | 约 9 900 行前端 AI 死代码处置档 | ① 整批删除（判据=importer 集为空且无路由挂载，`Marketplace.tsx` 已复验）；② 全部标 `@deprecated` 保留；③ 分批随改造页收敛时删 | **①**，但 `components/aiworkbench/agent-studio/AgentMonitor.tsx` 内含唯一诊断码实现，**先迁移再删**（X-76）。②使死码继续参与构建与误读（分册 04 F04-01 删 gateway 死类同裁定）；**①+一处例外 已批准**；实际删除属"存量动作"⇒ 执行待授权（报告 §14.4） |

## 八、与 W-Agent 接缝（智能域侧义务清单，v1.1 新增）

> **编号纪律**：本节**不新立 W/C 编号**（避免同族双口径），只登记"本册与 W Agent 编排域（分册 10 / PRD-10 / PRD-0X 承接需求族）之间的接缝义务"。
> 接缝中若发现新偏差，必须**同窗**回填 ARCH_SPEC §十一 C 系列并在分册侧登记对应 W 号后方可引用，否则只写"待登记"。
> 本节承接需求（REQ-AI-06~11 族）**已随 2026-09-29 §十四.1 批准并计入已批准基线 138**；**批准 ≠ 已实现 ≠ 已验收**——`pw *` 用例在前置件 P-3（Playwright 工程）建成前一律记"未执行"，未创建的测试类同样记"未执行"。

### 8.1 分工口径（先划清，避免双主责）

| 层 | 归本册（卷 06 / ai-engine + llm-gateway） | 归分册 10（W Agent 编排域） |
|:--|:--|:--|
| 语义 | 引擎能力、模型调用、护栏接线、计量与可观测 | Goal/Mission/Run/Step 语义、Playbook、Readiness、Candidate 治理流程 |
| 载体 | `ai-engine-*`、`llm-gateway` 既有模块；`ecos_ai` 控制域表 | 同一批 `ecos_ai.ecos_wagent_*` 表（**不另立 schema、不新增 Maven 模块**） |
| 底座 | llm-gateway = 唯一模型出口；runtime-task = 唯一调度出口 | 只调用底座，不自建 loop / 调度 / 计量 |

### 8.2 接缝义务

| # | 接缝 | 本册义务 | 不得越界 | 依据 |
|:--|:--|:--|:--|:--|
| 1 | **依赖方向**（M0 直改） | 删除 `ai-engine-impl` 对 `agent-service` 的 compile 依赖；`AgentRuntimeProvider` SPI 落 **llm-gateway**，实现由 `agent-service` 提供、`aiming` 组装期注入 | 规则扩展**不改** `check-legacy-modules.ps1 baselineCount=12` / `ArchitectureTest.baselineModules=12` 双门禁 | ARCH_SPEC **C257**；PRD-01 **REQ-PLT-28**；ADR-19 §2.1 |
| 2 | 单一 Runtime | Agent Loop 唯一实现在本册；Hermes 降级为经 SPI 接入的可选外部 provider | 许可 + 供应链审查登记为启用前置件（现 0 登记 = C238）；禁双套 loop | ADR-19 §2.2（推荐 A，随 **R-39**） |
| 3 | Skill / Cron | 定义落 `ecos_ai.ecos_wagent_*`，调度投 **runtime-task** | ai-engine 内禁自建 `@Scheduled`/线程池跑 Agent 任务（**C237**） | 铁律 §2.5；REQ-AI-06 |
| 4 | Tool Contract | `ecos_ai.ecos_wagent_tool_contract` 17 要素登记 + 生命周期 `draft→…→retired`；替换现 lambda 注册（权限不可审计 = **X-12**） | 正文 `contract_text` 为 `TEXT` 且不参与 WHERE/JOIN/索引（MC02）；未登记工具不可被 Plan 选用 | REQ-AI-07；附件二册 §4.1/§4.2 |
| 5 | Model Router | 与 **F06-18** 合并为同一批立项；路由四判据 = 能力/成本/延迟/风险 | 风险等级只来自 security-engine ABAC（:530），引擎内自写判定 = FAIL（**C255**）；`model-runtime` 新模块提案作废 | ADR-20 §2.1/§2.2；REQ-AI-08 |
| 6 | 自动化等级与 Kill Switch | ai 侧只做 PEP 执行；等级/密级/审批判定归 security-engine（卷 07 接缝另列） | 本地缓存判定必须带 TTL 且超时默认 DENY | REQ-AI-09；铁律 :530 |
| 7 | CLI | ai 侧端点必须先有 OpenAPI `operationId` 才可被 CLI 映射 | CLI 只经 gateway :8080；禁 service 端口与直连库表；凭据永不回显 | PRD-01 **REQ-PLT-26/27**；前置件 **P-5**（当前全仓 0 个 yaml = **C259**）；ADR-21 |

### 8.3 接缝验收（可执行标识）

1. `mvn -Dtest=ModuleDependencyArchTest#engineMustNotDependOnServices`（**M0，不等裁决**）；
2. `mvn -Dtest=AgentRuntimeUniquenessTest#onlyOneLoopImplementationInReactor`；
3. `mvn -Dtest=ToolContractRegistryTest#lambdaRegisteredToolRejected`；
4. `mvn -Dtest=ModelRouterDecisionTest#riskLevelComesOnlyFromAbac`；
5. `mvn -Dtest=TokenMeterConfigTest#noHardcodedModelName`（**M0**：`TokenMeterAspect` 去 `"hermes-agent"` 硬编码）；
6. `mvn -Dtest=CliContractParityTest#everyCommandMapsToOpenApiOperation` —— P-5 未建成记"未执行"。

---

## 九、追溯矩阵、依赖批次与门禁状态

### 8.1 REQ ↔ F ↔ W/C ↔ 承载 ↔ 验收标识（主责 5 项全覆盖）

> 纪律（Q13）：验收标识**只允许** `mvn -Dtest=类#方法` 或 `*.spec.ts › 用例名`。本册所有 `*IT` 项受 **8.4 前置 P-1** 约束：底座未建成前，其状态一律记为"未执行"，禁止记为"通过"。

| REQ | 优先级 | 设计项 | 偏差→回填 | 承载（端点/表/组件） | 可执行验收标识 |
|:--|:--|:--|:--|:--|:--|
| **REQ-AI-01** 护栏接线（全工具裁决 + fail-closed + obligations + 审计） | **P0 红线（G4 整批判定）** | F06-01/02/03/04 | C122,C123,C124,C125,C126,C127 | `ToolExecutorService#execute`（唯一咽喉，扩四段链）／`POST /api/v1/security/policy-engine/evaluate`／`V201 ecos_ai_tool_permission_binding`／`V204 ecos_ai_guardrail_decision_ledger` | `mvn -Dtest=ToolGuardrailChokepointTest#everyToolTypeReceivesExactlyOneEvaluate,#nonSqlToolsAreAlsoAdjudicated,#sandboxReviewerFailureYieldsDenyNotPass` + `AgentToolPolicyGateTest#timeoutTwoSecondsFailClosed` + `GuardrailFailClosedTest#absentSecurityEngineBlocksInputBeforeLlmCall` + `ObligationsApplyTest#maskObligationAppliedBeforeLlmSeesResult` + `ToolAdjudicationPayloadTest#payloadCarriesFourSectionsWithServerSideSubject` + `GuardrailAuditEventTest#allowDenyAndFailClosedEachEmitOneEvent` + `ToolChokepointArchTest#noBypassOfToolExecutorService` + `SecurityIntegrationGateTest#noDirectSqlExecutionOutsideChokepoint` + `GuardrailComplianceArchTest#noLocalRegexUsedAsPassDecision` |
| **REQ-AI-02** 会话压缩（token 阈值 / 结构化摘要 / 引用禁丢 / tool_call 完整性 / 逻辑删除） | P1 | F06-09/10 | C133,C134,C133(物理删除),C143 | `V202` 压缩治理列／`V203 ecos_ai_compression_batch`／`GET /api/v1/agent/sessions/{id}/context` | `mvn -Dtest=SessionCompressionTest#triggerIsTokenBasedNotCountBased` + `SessionTokenQuotaTest#overLimitStopsLoopWithReadableMessage` + `SessionCountConsistencyIT#messageCountMatchesRowsAfterCompress` + `SessionManagerLifecycleTest#closedSessionIsEvictedFromMemory` + `AgentTracerBoundTest#traceMapNeverExceedsMaxAndEvictsByInsertionOrder` + `SessionMemoryIT#longSessionHundredTurnsRssWithinBaseline` + `ecos-tests/ai-session.spec.ts › ai › 压缩状态条显示模式标记` |
| **REQ-AI-03** 可观测（traceId/MDC/五态日志 + token 计量 + 配额 + 回放） | P1 | F06-06/07/08 + F06-05(取密与计量同点) | C129,C135,C136 | `GET /api/v1/agent/runs/{traceId}/replay`／`LLM_USAGE` 事件／`sys_agent_call_log`（归属待 R-19）／llm-gateway 唯一计量点 | `mvn -Dtest=AgentLoopTraceTest#entryHeaderBecomesMdcAndIsPropagatedToSecurityAndGateway` + `RunReplayTest#stepsReconstructedInOrderWithLatencyAndTokens` + `LlmUsageMeteringTest#usageEventEmitOncePerCallWithRealOrEstimatedFlag` + `AuditPayloadProbeTest#llmUsageFindableInAuditStore` + `CrossEngineTraceIT#grepTraceIdSpansAimingAndSecurity` + `LlmGatewayFacadeTest#onlyNewFacadeHasCallAndStream` + `LlmSingleExitArchTest#engineMustNotOwnProviderAbstraction` + `AgentProviderControllerTest#emptyCatalogYields501Not200Empty` |
| **REQ-AI-04** 五场景工具 + 引用块 + 后校验（禁 LLM 生成数值） | P1（**引用块为 §0.2.1 的 P0 承接件**） | F06-11/12/13 | C137,C138,C140 | `V200 ecos_ai_tool_registry`／`GET /api/v1/agent/tools`／`POST /api/v1/agent/tools/{name}/dry-run`（非产品路径）／引用块模板 | `mvn -Dtest=ScenarioToolRegistryTest#fiveToolsRegisteredWithBackendEndpoints` + `ScenarioToolInvocationIT#eachToolProducesExactlyOneOpaDecision` + `CitationPostCheckTest#numberWithoutCitationBlockIsIntercepted` + `CitationInducementIT#refusesDirectAskForNumberWithoutCitation` + `RefusalRulesTest#unapprovedCaliberYieldsClarifyNotNumber` + `ecos-tests/ai.spec.ts › ai › token 数值来自后端而非随机` |
| **REQ-AI-05** 七类错误矩阵（分类 / 有界退避 / 模板消息 / 码表） | P2（**C140 的 mock-success 属 P0 幻觉源，提前批**） | F06-14/15 | C139,C140,C141 | `ECOS-AI-*` 码表（D.5）／删除 `buildMockToolResult`／`ErrorCodeMapper` 复活或删除 | `mvn -Dtest=AiErrorMatrixTest#sevenClassesEachCarryTemplateRetryBoundAndLevel` + `AiFaultInjectionIT#timeoutRateLimitParseFaultTakeDocumentedPaths` + `AiErrorSemanticsTest#absentTableYields500Not404` + `AiExceptionHygieneTest#controllersDeclareNoCatchAll` |
| REQ-PLT-21 LLM 唯一出口（铁律 §2.5-2，非本册主责但被强依赖） | P0 安全 | F06-05/17 | C128,C129,C130,C131,C132 | `runtime/llm-gateway` 门面归一／`SecurityEngineBridge.resolveSecret` | `mvn -Dtest=LlmSingleExitArchTest#engineMustNotOwnProviderAbstraction` + `RuntimeAccessTest#noNewRestTemplateInAiDomain` + `SelfScheduleArchTest#noExecutorFactoryNoRunAsyncNoRawThreadInBusiness` + `ModuleDependencyArchTest#llmGatewayIsInsideTheUniverse` + `LlmConfigSingleSourceTest#noDuplicateLlmKeysOutsideGatewayYml` |
| 数据层与 DDL 纪律（ST07 / MC01~MC03 / DR06~DR08 / §3.1 / §五） | P1 | F06-16/18 | C142,C143,C144 | `V200~V204`（单源目录）／`ecos_ai` 归位（R-19）／search_path 解除（R-22） | `mvn -Dtest=AiDdlComplianceTest#newAiTablesHaveBaselineColumnsAndUuidPk` + `AiSqlDialectComplianceTest#noBareCastNoOnConflictNoIlike` + `AiPersistenceArchTest#serviceLayerHasNoJdbcTemplateDirectAccess` + `MigrationSingleSourceTest#noSecondDdlSourceNoDuplicateVersion` + `SchemaInventoryGateTest#aiTablesResideInEcosAiBeforeHandlers` |
| 前端 AI 面（FE-01 / 承流 / 真实性） | P1 | F06-19/21 | C145 | `server.ts` SSE 直通（属主分册 08）／`aiApi` 单通道／live 页去 mock | `mvn -Dtest=FrontendAiComplianceScanTest#noHardcodedColorsNoChineseLiteralsNoInlineSvgInAiFiles` + `FrontendAiDeadCodeTest#noUnimportedAiComponentRemains` + `FrontendI18nGapTest#noMissingZhCnKeyAmongAiUsedKeys` + `AiRouteIntegrityTest#everyFrontendAiPathHasOwningMapping,#sessionsListEndpointExistsInBothPrefixes` + `ecos-tests/ai.spec.ts › ai › 流式响应显示诊断码,› ai › 前端不再自填主体身份,› ai › 生产链路下流式首帧早于完整响应到达,› ai › 流式对话端到端可用且带鉴权,› ai › 单一 aiApi 通道被所有面板引用` |
| 文档与配置真实性、测试底座（Q13 / §九 / §十） | P1 | F06-20/22 | C146 | 三份 `agents.md` 订正／OpenAPI 由测试产出／`AiTestHarnessSmokeTest` | `mvn -Dtest=ModuleDocTruthfulnessTest#aiDomainDocsMatchMeasuredCounts` + `AiIntegrationCoverageTest#aiEngineAndGatewayHaveAtLeastOneRealIT` + `AiTestHarnessSmokeTest#springBootTestSliceBootsInBothModes`（**本册所有 `*IT` 的先决条件**） |

覆盖率自检：PRD-06 的 REQ-AI-01~05 五项主责、每项均有 ≥3 个 F 项与 ≥5 个可执行验收标识；关联的 REQ-PLT-21、FE-01、DB 红线、Q13 亦落入表内 —— **无 REQ 缺 F、无 F 缺验收标识、无 W 缺 C**。

### 8.2 依赖与批次（本册内部时序）

| 设计项 | 前置依赖 | 被依赖（下游） | 批次 |
|:--|:--|:--|:--|
| F06-01/02/03/04（护栏咽喉全链） | **P-1 测试底座**、01 册 `evaluate` 响应扩 `obligations+policyId`、W145 超时对齐 | 09 册 FC-02 场景工具、G4 整批判定 | **A（P0 红线，阻塞整批）** |
| F06-05（LLM 出口收一 + 取密） | R-18 裁决（反射 vs REST）、00 册 llm-gateway 门面基线 | F06-06/07 计量点、09 册 | A |
| F06-06/07/08（traceId/计量/回放） | 00 册 `ApiResponse.traceId`、W150 ArchUnit 宇宙补齐、W154 mapper 注册 | 05 册 C114 链、08 册诊断码显示 | A |
| F06-09/10（压缩 + 内存） | R-20 裁决（存量语义）、W152 | 08 册压缩状态条、ARCH_SPEC §八 内存门禁 | B（M2） |
| F06-11/12/13（场景工具 + 引用后校验） | **09 册 FC-02 预测运行只读端点**、04 册 knowledge 属主（F04-01）、R-19 | 09 册演练、PRD-08 | B（场景批次） |
| F06-14/15（错误矩阵 + 异常卫生） | F06-05（错误源收敛后才可分类）；C140 mock-success **不等任何裁决，A 批先行** | 08 册错误呈现 | A(mock 清除) + 尾批(矩阵) |
| F06-16/18（持久化纪律 + 数据归位） | R-19/R-22 裁决、W160 schema 限定先于 search_path 撤销 | 全部 DDL 与查询 | A |
| F06-17（底座收口 + 门禁可判化） | 00 册 runtime-access/runtime-task/runtime-event、W150 | 所有 P0/P1 门禁 | A |
| F06-19/21（前端归一 + 承流） | 08 册 `server.ts` SSE 直通、F06-06 诊断码、R-23 存量删除裁决 | 09 册界面 | A(auth/mock 清除) + B |
| F06-20/22（文档真实性 + 测试底座） | 无（**F06-22 自身即 P-1，本册最先落**） | 本册所有 `*IT` | **A-0（先于全册）** |

### 8.3 门禁状态（本册自检）

| 门禁 | 判定 | 依据 |
|:--|:--|:--|
| Gate-1（模块划分 + 技术选型） | 本册不重开；**6 项 R-18~R-23 已回填需求检视报告 §13.5 并于 §十四.1 全量批准** | ARCH_SPEC Gate-1 第 9 行 |
| PRD 校验 | PRD-06 状态 APPROVED / `deliverable_allowed=true`；本册对 PRD-06 §1.2 的 `ToolDispatcher` 字面提出**需求文本更正请求**（见 7.1） | 本册 0.2 咽喉实测 |
| 需求覆盖 | 主责 5 项 REQ 全覆盖（8.1），无遗漏 | 8.1 |
| PRD 追溯 | 22 个 F 项各锚 REQ 或铁律条款；25 个 W 与 25 个 C 一一映射（C = W − 18） | F 章 |
| DDL 合规自检 | `V200~V204` 逐列核 MC01（`VARCHAR(36)` 应用侧 UUID）/ MC02（JSON 只用 TEXT，`args_schema`/`resource_scope_projection` 均为 TEXT）/ MC03（无 `::`/`ON CONFLICT`/`ILIKE`/无 databaseId 的 RETURNING）/ DR06~DR08（7 基线列齐）/ §四附则1（全表 schema 限定）；无 jsonb、无 nextval-PK、无 partial index | E.2 |
| 安全集成强制卡（§2.4-8） | 咽喉规则化（`ToolChokepointArchTest` + `SecurityIntegrationGateTest`），且 ArchUnit 宇宙补齐后 llm-gateway 不再逃逸（W150→C132） | C 章 4.1 不变式 I-1~I-5 |
| 两态可达性（ADR-15） | 4.5 矩阵给出 gateway fat-JAR 与 aiming service 两态逐条判定；**同 JVM 反射桥在 service 态恒 DENY** → 已提交 R-18 | C 章 4.5 |
| 虚假验收防线 | 全部验收为 `mvn -Dtest=类#方法` / `*.spec.ts › 用例名`；本册**无**"人工确认""E2E 通过"表述；`playwright.config.ts` 与 `ecos-tests/package.json` 实测不存在【2026-09-30 五补校订：两文件已建成，本句为批次起始取证记录】，故所有 `.spec.ts` 项在 30 号任务建成前记"未执行"【五补：任务 #30 已结案（载体面闭环，报告 §14.6），但本册声明的 AI 界面/契约用例仍未编写 ⇒ 继续记"未执行（用例未落地）"，口径见报告 §14.6.5】 | Q13 |
| 编号回填 | C122~C146 **本册收卷即刻回填 ARCH_SPEC §十一**，不延后 | F 章表头纪律 |
| 存量动作边界 | `ecos_ai` 空壳 DROP、9 900 行死前端删除、`V202` 存量语义切换三项均属**存量动作**，本册只定性并提裁决（R-19/R-23/R-20），**未擅自迁移**；仅 `V204`（纯补建、无归属争议）与重复迁移版本 V50/V150/V155（确定性缺陷）可先行动作 | 7.2 + 需求检视报告例外口径 |
| **Gate-2（技术负责人批准）** | **已通过** —— 凭证 = 需求检视报告 §十四.3（用户批准 2026-09-29，ARCH_SPEC + 11 册全集）；`deliverable_allowed=true` ⇒ 本册 F06-01~04（P0 红线）具备进入实现分发的门禁条件（**尚未实现，验收仍记"未执行"**） | — |

### 8.4 前置件 P-1：AI 测试底座（红线验收的载体）

| 项 | 内容 |
|:--|:--|
| 缺陷 | 全仓 `@SpringBootTest` **0 命中**；`runtime/llm-gateway`、`services/aiming` 各 0 测试文件（`src/test` 目录不存在，X-71/X-73）；OpenAPI 制品 0（X-86）；`playwright.config.ts` 与 `ecos-tests/package.json` 不存在 |
| 后果 | REQ-AI-01 的 G4 红线判定**当前无载体** —— 没有能同时装配 gateway 态与 aiming 态的测试切片，`evaluate` 调用次数、fail-closed、obligations 执行顺序都无法断言 |
| 要求 | `AiTestHarnessSmokeTest#springBootTestSliceBootsInBothModes` 建立两态切片（gateway fat-JAR / aiming service，含 security stub 契约与审计捕获器），置于 `engine/ai-engine/ai-engine-impl/src/test` 与 `runtime/llm-gateway/src/test` |
| **门禁** | **该用例失败或未存在时，本册 8.1 中所有 `*IT` 与 `*.spec.ts` 项状态必须写"未执行"，禁止写"通过"** —— 此为本册反虚假验收的硬约束，与需求检视报告 §十二 补记的"工程建成前任何 Playwright E2E 通过均属虚假验收"同源 |

### 8.5 跨册接缝（本册提出、由其它册属主承接）

| 接缝 | 本册诉求 | 属主册 | 本册落点 |
|:--|:--|:--|:--|
| `ApiResponse.traceId` 字段与填充时机 | AI 侧诊断码、回放、审计三处依赖同一 traceId 而非进程内 8 位串 | 分册 00 | C135 / F06-06 |
| `POST /api/v1/security/policy-engine/evaluate` 响应体 | 需返回 `obligations[]`（mask/rowFilter）+ `policyId`，否则 AI 侧无从执行脱敏 | 分册 01 | C126 / F06-02、5.3 |
| knowledge 属主与 `knowledge_extract` 工具下线 | 白名单里的 `knowledge_extract` 指向已删/待删的 dccheng 端点 | 分册 04（F04-01） | C137 / F06-11 |
| 鉴权两态等价（C113）与 traceId 链（C114） | 认知域与智能域在 aiming service 态同样无鉴权，R-18 与 R-16 必须同批口径 | 分册 05 | 4.5 / R-18 |
| COG-02/03 供数 | 反事实与诊断需要 AI 侧 `run replay` 的步骤时序作为证据输入 | 分册 05 | F06-08 / 5.2 |
| `server.ts` 对 `text/event-stream` 直通 | 现状整体缓冲（`await upstream.text()`）→ AI 流式在生产链路必挂 | 分册 08 | C145 / F06-21、7.1 |
| FC-02 预测运行服务（`forecastRunId`/`caliberId`/`asOfTime` 数据源） | 五个场景工具的后端端点与引用块三元组全部来自该运行快照 | 分册 09 | C137,C138 / F06-11/12 |

---

**本册一句话结论**：智能域的实测形态是"咽喉已在、闸门未装，门面在、计量断，界面在、真值无"——PRD-06 五项主责能力一条都不达标，但改造路径清晰且**不需要新增平行入口**：把 `ToolExecutorService#execute` 的裁决面从"仅 SQL"扩到全工具类型并 fail-closed、把 LLM 出口与 api-key 收一于 llm-gateway、把压缩从"条数+截断+物理删除"改为"token+结构化摘要+逻辑归档"、把 traceId 从进程内 8 位串换成 MDC 全链贯通、把五个场景工具从硬编码白名单改为声明式注册表并强挂引用后校验；同时必须先建 AI 测试底座（P-1），否则这套 P0 红线的验收无载体。六项存量归属议题（R-18~R-23）交由裁决，本册不擅自迁移。

<!-- 详细设计-06-智能域 / 2026-09-29 / v1.2（2026-09-29 定版） / W140~W164 → C122~C146 / R-18~R-23 与 R-37/R-39/R-41/R-47/R-50 已批准（报告 §十四.1） / Gate-1 已签字、Gate-2 已通过 / 本轮未实跑库、未改业务代码 -->

---

## 【校订·一】A-batch P0 咽喉+护栏 五连绿（2026-10-04，本地未 push）

> **口径**：只增记，不动既文。本段为 A-batch P0 咽喉（F06-01~05）落地佐证，尚未触发 8.3「编号回填 ARCH_SPEC C122~C146」条款（条款纪律：本册**收卷即刻**回填，非每 F 项落即回填；本段证明 A-batch 侧 4/5 C 项已具备回填物质条件）。剩余 F06-06~21、B 章前端、跨分册截面全部**授权闸/跨分册**，维持红不 fake 本地测试假绿。

### 一、commit 落点与全模块绿

| commit | 主题 | C 项 | 全模块 surefire |
|:--|:--|:--|:--|
| `bf1efe9` | F06-01 P0 咽喉全工具类型（W140/W141, C122/C123 部分） | C122, (C127 半·超时 2000ms 落码) | ai-engine-impl 55/0/0 BUILD SUCCESS |
| `caa45bb` | F06-02 裁决载荷四段 + obligations 脱敏接线（W144, C126） | C126 | ai-engine-impl 62/0/0 |
| `7df9f80` | F06-03 GUARDRAIL_EVAL 审计事件红线接线 3 测试（W143, C125） | C125 | ai-engine-impl 68/0/0 |
| `c5c71fb` | F06-04 输入/输出护栏改经 security-engine 且 fail-closed（W142, C124） | C124 | ai-engine-impl 72/0/0 |
| `c64e90d` | F06-05 LLM 出口唯一化 + api-key 死线程清理 + provider 空目录护栏（W146, C128 咽喉侧） | C128（咽喉侧半） | ai-engine-impl 71/0/0, data-engine-impl 318/0/0/1 |

### 二、C 项回填物质条件（A-batch 侧已备齐半）

- **C122**（咽喉全工具类型）：**落码+护栏齐**——`ToolGuardrailChokepointTest`(3) 含 `everyToolTypeReceivesExactlyOneEvaluate`/`nonSqlToolsAreAlsoAdjudicated`；`ToolChokepointArchTest#noBypassOfToolExecutorService`（源码级冻结外咽喉直调）。
- **C123**（sandbox fail-closed）：**落码+护栏齐**——`ToolGuardrailChokepointTest#sandboxReviewerFailureYieldsDenyNotPass`；`AgentToolPolicyGateTest#timeoutTwoSecondsFailClosed` 顺带覆盖了 C127（W145 超时 2000ms，X-19 已对齐 PRD）。
- **C124**（输入/输出 fail-closed 经 security）：**落码+护栏齐**——`GuardrailFailClosedTest`(3) + `GuardrailComplianceArchTest`(1，源码级禁 `Pattern.compile`/`.matcher().find()` 充当放行依据)。
- **C125**（GUARDRAIL_EVAL 一裁决一事件 + 发布上抛）：**落码+护栏齐**——`GuardrailAuditEventTest#allowDenyAndFailClosedEachEmitOneEvent` + `#auditPublishFailureFailsClosed` + `#eventCarriesNoRawPayload`（3 例）。
- **C126**（payload 四段 + obligations 执行）：**落码+护栏齐**——`ToolAdjudicationPayloadTest`(6) + `ObligationsApplyTest`(4) + grep 门禁 8 正则命中零。
- **C127**（2s 超时）：**落码**——`AgentToolPolicyGate` 缺省 2000ms + `AgentToolPolicyGateTest#timeoutTwoSecondsFailClosed`（后续 F06-01⑤ 已 commit `7ccae3a` 补外咽喉 0 直调 arch 护栏）。
- **C128**（LLM 唯一出口）：**咽喉侧半已备齐**——`LlmSingleExitArchTest#engineMustNotOwnProviderAbstraction` + `#noRawApiKeyFieldInEngineSideRequest`（2 例源码级护栏，engine 侧 `LLMProvider` 引用 0 命中 + engine 侧 `.setApiKey(` 0 命中）；`AgentProviderControllerTest`(3) 空目录 501。**次级未落（诚实接缝）**：`LLMGatewayService` 全面 `@Deprecated` 需先迁 4 处 ai-engine consumer（AgentConfigService/AgentCallController/ClassificationController/NLQController）→ 跨窗口，归 F06 后续 P1 派生面（详见 §8.6 J-x 登记）。

### 三、诚实接缝登记（J-1~J-7，带跳回代价）

| J-n | 项 | 状态/跳回代价 |
|:--|:--|:--|
| J-1 | F06-02 分册 01 seam：security `/evaluate` 响应扩展 `policyId/obligations[]` | **阻塞**：分册 01 尚未扩字段 → 本落点现网恒拿 `null/[]`，本地测试用 stub。跳回：分册 01 需扩 `SecurityDecisionService.evaluate()` 返回体并同步本文中 8.5 接缝第一行契约。**不 fake 字段**——`AgentToolPolicyGate.Decision` 结构已预留位，只填真值。 |
| J-2 | `ToolAdjudicationRequest` 落位 api 模块 vs impl 模块 | **落**：暂放 `ai-engine-impl/security` 包（api 无测试基建、铁律禁 api 内逻辑）；本册 doc 点名放 api 属跨册暂留共享面，本窗只服务 ai-engine 岔口。**跳回代价**：如认知域/workspace 后续复用需搬 api → 单点 rename + 双 consumer 切。 |
| J-3 | F06-02 `rowFilter`/`row_filter` obligations 保守 fail-closed | **落**：现只支持 `mask`，`rowFilter` 行级谓词属 RLS 算法引擎侧不评估（[[project-data-domain-rules]] §三 铁律 3）。**跳回代价**：若下窗要求支持 → 需经 security 新增 `/api/v1/security/row-filter/apply` 端点（分册 01 新端点），非本册越权。 |
| J-4 | F06-02 applier 只 log field 名不打值（防值索进日志） | **落**：`AgentToolObligationsApplier.summarize` 只 log `kind+field`。**跳回代价**：无——已是保守口径。 |
| J-5 | F06-03 主体缺失态不得静默旁路（I-1「每次执行↔恰好一条事件」） | **落**：主体缺失走 `emitGuardrailEvalOrFold` 补发 FAIL_CLOSED 事件（catch 后不改变已定的 DENY 裁决，但事件仍发）。**跳回代价**：无——已锁 `I-1`。 |
| J-6 | F06-04 security `/api/v1/security/guardrail/screen` 端点尚未入网（分册 01 文本审核端点） | **诚实阻塞**：现网恒抛 → 恒 fail-closed，本地不 fake 本地正则假绿。**跳回代价**：分册 01 需补该端点，见需求检视 §14.6；分册 06 侧已备齐 `SecurityEngineClient.screenOrThrow` 客户端+三态 `GUARDRAIL_EVAL` 事件，端点入场即点亮。 |
| J-7 | F06-05 要点4 `SecurityEngineBridge` reflection→REST 转换（R-18 裁决默认 ① REST） | **未落（跨窗口）**：llm-gateway 侧改动，非本 ui-engine-impl 文件集范围；语义由要点 1/2 REST 单出口先行满足。**跳回代价**：下窗回 llm-gateway 收口，需切 `SecurityEngineBridge.resolveSecret` 从反射改 REST，同时删同 JVM 反射桥（分册 05 C113 同批口径，联动 R-18/R-16）。 |

### 四、本窗新踩坑（跨窗复用，非本册特有）

- **Javadoc 内嵌 `*/` 提前终止注释**：`{@code /*...*/}` 中 `*/` 会终止 Javadoc 块 → 「需要<标识符>」编译错（`GuardrailComplianceArchTest` 首版 / `LlmSingleExitArchTest` 首版各踩一次）。规则：写注释禁在 `{@code ...}` 中放 `*/`。
- **`ApiResponse` 无 `error(int, Map)` 重载**：只有 `error(int, String)` / `error(int, String errorCode, String message)`。空目录 501 意图的负债：`ApiResponse.error(501, "llm-gateway provider 目录不可用或为空（stub=true 模式）")`——把 stub=true 编进 message 字面。
- **`mvn|grep` 掩盖 maven 退出码**：末尾 `| grep` 返回 grep exit，maven 失败也显 0。**修**：`${PIPESTATUS[0]}` 或查 output 中 `BUILD SUCCESS/FAILURE`/`.java:[` 三选一。
- **`StringBuilder.append(CharSequence, CharSequence)` 不存在**：链式 `append(src).append("\n")`。
- **改 common-api 常量后必 `-am install` 非 `-am test`**：common-api 未被 install 时，ai-engine-impl 编译读的是 ~/.m2 旧 jar → `cannot find symbol`（F06-03 首次踩，F06-05 二次证伪）。
- **`doThrow` 非 `when().thenThrow`**：Mockito void 方法必须用 `doThrow(e).when(mock).voidMethod(...)`；`when(voidReturning).thenThrow` 编译红（`thenThrow` 无 O 泛型 return）。

### 五、非本册窗口同批落档提醒（跨窗协同）

- **前端**（非本 goal owner）：`ecos_frontend/src/components/{LanguageContext,Sidebar}.tsx` + `main.tsx` 并发窗 WIP 未 commit，勿混入本册 doc 校订 commit。
- **数据域**（非本 goal owner）：`docs/30-设计/详细设计-05-认知域-cognitive-engine与aiming-2026-09-29.md` 已 UPDATED，属他窗，本册不代改。
- **sysman/jwt 相关工作**（非本 goal owner）：`sysman-boot/application.yml` + 未跟踪 `AnonymousEndpointInventoryTest`/`JwtDenyTest` + `gateway/routing/` 均属他窗。

### 六、下一跳（非本 A-batch 收尾范围）

- **B 批（M1/M2）**：F06-06/07/08（traceId/计量/回放）、F06-09/10（压缩+内存）、F06-11/12/13（场景工具+引用后校验）、F06-14/15（错误矩阵）、F06-16/18（持久化纪律+DDL）、F06-19/21（前端 B 章）、F06-20（文档真实性）。全部授权闸/跨分册/跨模块依赖项，非本窗 A-batch 可推进。
- **编号回填**：C122~C146 待本册**完整收卷**后再一次性回填 ARCH_SPEC §十一（8.3 表 + §8.1 纪律）；本 【校订·一】只作 A-batch 侧落档佐证。
- **未 push**：本 goal 全部 commit 在 local `release/v2.1-alpha`，`origin...HEAD` ≈**106 ahead**（含 c64e90d 与本 【校订·一】 commit）。**push 需用户显式授权**。
