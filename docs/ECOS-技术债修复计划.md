# ECOS 技术债修复计划（基于 codebase-memory 诊断报告）

> 来源：肖国荣 | 日期：2026-08-21
> 依据：codebase-memory 图谱诊断（38,498 节点 / 121,477 边 / 1,418 Java + 373 TS）
> 交叉验证：已抽查 dccheng Controller 清单、agent/chat 三处冲突、knowledge/rag 三处冲突，报告可信，部分结论已修正
> 架构框架：**五对象·五行（引擎层）＋ 四转化·格致诚明（服务层）**，已写入 ARCHITECTURE-RULES.md 0.2/0.3 节

---

## 零、诊断报告修正（先对齐认知，再执行）

诊断报告整体可信，但交叉验证后发现 4 处需修正：

| # | 报告说法 | 实际核查 | 影响 |
|---|---------|---------|------|
| 1 | agent/chat 3 处冲突，建议"保留 agent-service 的 AgentRuntimeController 链路" | AgentRuntimeController 用 `/api/v1/agent-runtime` 前缀，**不参与** `/api/v1/agent/chat` 冲突。真正冲突的是 3 个：ai-engine/AgentChatController、ai-engine/DiagnosticAgentController、gateway/DiagnosticAgentController | 权威归属需重新判定 |
| 2 | "dccheng ≈ engine/ontology-engine 历史 fork" | dccheng 24 个 Controller：19 个 ontology（错放的金层代码）+ 5 类 knowledge/classification/glossary/guardrails，**全部在引擎层已有对应** | 不是废弃，是**拆解** |
| 3 | 未提 services 层 12 子服务 | services 层是薄壳（大部分 0-2 Controller），knowledge-service 的 KnowledgeRagController 映射 `/api/v1/knowledge`，与 kb-engine 冲突 | P0 要处理 services 层路径冲突 |
| 4 | "P1 缓存迁移 Caffeine + Redis" | ECOS 三版本里 standard 只有 PG，无 Redis | 引入 Redis 破坏版本架构，需分版本选型 |

**核心诊断（用五行+格致诚明框架解读）**：

ECOS 的病灶不是"三层并存"，是**分层没对齐**：

- **dccheng 为什么乱**：它把"引擎层的对象管理"（ontology 的 19 个 Controller + knowledge 的 4 个）和"服务层的转化职责"（`cheng` 这个名字本该是 K→C 转化）**混在一个模块里**。名字是转化的名字，干的是对象管理的活。
- **services 层为什么薄壳**：它还没按"格致诚明"四转化收敛，散成 12 个服务。应收敛为 4 个转化服务：ge-service（D→I）、zhi-service（I→K）、cheng-service（K→C）、ming-service（K→W）。
- **修复方向**：不是"删 dccheng"，是"拆解 dccheng"——对象代码归还引擎层（五行），`cheng` 名归还服务层（格致诚明）。

---

## 一、修复总原则

1. **引擎层（五对象·五行）是对象管理的唯一权威**。data/ontology/kb/cognitive/ai 五引擎各管其对象生命周期，security-engine 是横切的"护"。
2. **服务层（四转化·格致诚明）只做对象间转化**。ge（D→I）、zhi（I→K）、cheng（K→C）、ming（K→W）。
3. **拆解 dccheng，不修它**。24 个 Controller 全部在引擎层有对应，对象代码归还引擎，`cheng` 名归还服务层。
4. **services 层本次只修冲突，不废弃**。它是未来四转化服务的雏形，先消除路径冲突，收敛留后续。
5. **不破坏三版本架构**。缓存选型分版本：standard=Caffeine 本地，enterprise/ultimate 加 Redis。
6. **每阶段独立可验收**，git commit + curl/tsc 为唯一产出。

---

## 二、分阶段执行计划

### 阶段 A：止血（P0，1-2 周）

| Task | 内容 | 验收 |
|------|------|------|
| A1 | **拆解 dccheng**：①先写 ArchUnit 断言"engine 不依赖 dccheng" ②确认 dccheng 的 24 Controller 全部在引擎层有对应（19 ontology→ontology-engine 金，4 knowledge→kb-engine 水，classification/glossary/guardrails→各自引擎）③从 pom.xml 4 个 profile 移除 `<module>dccheng</module>` ④删 dccheng 目录 | 全量 `mvn install` BUILD SUCCESS，`grep -r dccheng` 仅剩注释/历史文档 |
| A2 | 统一 agent/chat 归属：删 ai-engine/DiagnosticAgentController 和 gateway/DiagnosticAgentController 里的 `/chat`，保留 ai-engine/AgentChatController（火·智慧对象）为唯一权威 | Gateway 启动无 Ambiguous mapping，`curl /api/v1/agent/chat` 200 |
| A3 | 统一 knowledge/rag 归属：删 dccheng/KnowledgeApiController 和 services/knowledge-service 的 `/rag`，保留 kb-engine/RagController（水·知识对象） | `curl /api/v1/knowledge/rag` 200 |
| A4 | 统一 `/api/security/mask` + `/api/v1/policy-engine/evaluate` 到 security-engine（护） | 端点 200，无重复路由 |
| A5 | 前端去重：删 components/AsyncTaskCenterView.tsx（与 pages 完全相同），确认 ObjectExplorerView/Sidebar 等 18 处权威版本 | `npx tsc --noEmit` 零新增错误 |

**A 阶段决策点（已确认）**：
- **D0** ✅ 五行+格致诚明框架写进 ARCHITECTURE-RULES.md（已完成）
- **D1** ✅ dccheng **拆解**（对象归引擎、cheng 名归服务层），非废弃
- **D2** ✅ agent/chat 权威 = ai-engine/AgentChatController

### 阶段 A+：runtime 拆解（器定位，框架落地核心）

runtime 现状 515 文件（7 子模块），是历史大本营。按「器」定位拆解：

| Task | 内容 | 验收 |
|------|------|------|
| A+1 | **迁安全 → security-engine（护）**：runtime-security 37 文件（compliance/datapermission/abac/policy）+ runtime-crypto 19 文件（KMS/加密）迁入 security-engine，改写 import | 全量 `mvn install` BUILD SUCCESS，`grep -r runtime.core.security` engine 外 0 匹配 |
| A+2 | **迁数据工程 → data-engine（土）+ ge（格）**：runtime-core 的 dataaccess 33/dataobjectmgr 28/datadescription 19/datasourcemgr 11/format 15/metadata 10/quality 4/lineage 3/kettle 6/bigdataengine 8/modelaccess 5（~142 文件）中**有活引用的**迁 data-engine，纯转化逻辑迁 ge-service | 编译通过，data-engine 不再反向 import runtime-core |
| A+3 | **迁 agent.mesh → ai-engine（火）**：runtime-core 的 agent.mesh 14 文件（MissionExecutionEngine/AgentRegistry/Mission 等，被 ai-engine 反向 import 12 处）迁入 ai-engine，改写 ai-engine 8 个 import | 编译通过，`grep -r runtime.core.agent.mesh` engine 外 0 匹配 |
| A+4 | **建 runtime-access 收敛基础设施访问**：在 runtime 下新建 `runtime-access` 子模块（与 runtime-task 并列），把散落的 Driver/Client 封装收敛——Neo4j（5 处：ontology-engine/kb-engine/workspace/dccheng/runtime-core）、MinIO（2 处：gateway/workspace）、Doris（3 处）、Git（2 处）+ 基础工具（util/logging/i18n/mybatis/alert 从 runtime-core 提取） | runtime-access 模块独立编译，引擎层 Driver/Client 全部改走 runtime-access，`grep -r 'GraphDatabase.driver\|MinioClient' engine/ workspace/` 0 匹配 |
| A+5 | **删死代码 + 清空壳**：runtime-datanet 空壳删除；runtime-core 中 0 引用的死代码（~160 文件：agent.tool 8/agent.impl 5/agent.llm 4/legacy 6 + 数据工程中无活引用的部分）；runtime-monitor 清死代码（保留监控框架） | 编译通过，runtime-core 从 388 降到 ~100 文件 |

**runtime 拆解后最终形态**：

| 模块 | 文件 | 定位 |
|------|------|------|
| runtime-access（新建） | 基础工具 + PG/Neo4j/MinIO/Doris/Git 访问 | 器·基础设施访问 |
| runtime-task | 27 | 器·全局调度 |
| runtime-monitor | 57（清死代码后） | 器·全局监控 |
| llm-gateway | 26 | 器·LLM 网关（归属不变） |
| security-engine | +56 | 护·安全 |
| data-engine + ge | +~142 | 土·数据 + 格·D→I 转化 |
| ai-engine | +14 | 火·agent.mesh |

**A+ 阶段决策点（已确认）**：
- **D5** ✅ 基础设施访问含 git（PG/Neo4j/MinIO/Doris/Git 五类）
- **D6** ✅ llm-gateway 归属不变（留 runtime）
- **D7** ✅ 命名 runtime-access，放 runtime 下与 runtime-task 并列（非独立 core 模块）

### 阶段 B：内存与稳定性（P1，2-4 周）

> 差距分析（2026-08-23）：5 个 Task 全部成立。位置已因 A/A+ 迁移更新——TokenServiceImpl / InMemoryPermissionCacheService / DictService / SysConfigService 仍在 sysman；InMemoryDecisionCacheService 已迁 security-engine（R1）；BaseJdbcAdapter 已迁 data-engine（A+7b）；AgentSessionService 已迁 ai-engine（A+9c）。
> 依赖现状：Caffeine 已在 data-engine-impl / gateway / workspace-impl / catalog-service 引入，Redis 在主 pom dependencyManagement；**但 sysman-impl 无 Caffeine**（B1/B2/B3 的 4 个类全在 sysman），需先给 sysman-impl 加 caffeine 依赖。
> 原计划 B4 的 IFieldMappingDao.addMappingRefs、B5 的 synthesizeFromArray 内层已核实：前者已不存在（作废），后者现位于 ai-engine/ClassificationController。

| Task | 内容 | 验收 |
|------|------|------|
| B1 | TokenServiceImpl.blacklist（sysman，现 ConcurrentHashMap 无 TTL）→ Caffeine（standard）+ Redis TTL（enterprise/ultimate） | 重启后黑名单仍生效 |
| B2 | InMemoryPermissionCacheService（sysman）/ InMemoryDecisionCacheService（security-engine）→ Caffeine | 多实例一致 |
| B3 | DictService.cache / SysConfigService.cache（sysman，现 ConcurrentHashMap）→ Caffeine + 启动预热 | 首次查询不冷启动 |
| B4 | 修复无保护递归：~~IFieldMappingDao.addMappingRefs~~（已删）；AbacPepService.evaluate / BaseJdbcAdapter.query / AgentSessionService.compressHistory 已逐个读方法体核实为普通循环/重载（非自递归）→ **降级为「核实残余递归点」** | 补 base case，单测覆盖 |
| B5 | 消除隐藏 O(n²)：~~synthesizeFromArray 内层 List→Map~~（已核实为单层循环）；CausalReasonerServiceImpl BFS 遍历为 O(V+E) → **降级为「核实残余 O(n²) 点」** | 复杂度降 O(n log n) |

**B 阶段决策点（已确认）**：
- **D3** ✅ Redis 引入范围：standard 不引（PG-only），enterprise/ultimate 才加

### 阶段 C：拆分与可维护性（P1-P2，4-8 周）

> 差距分析（2026-08-23）：C3 ScheduleBean 已删（死代码，A+9a）→ **作废**。
> 新发现前端 2 对重复文件，拆分前须先判定权威版本（同 A5 去重逻辑）：
> GuardrailsView（pages/aiworkbench/ 1,464 行 vs pages/ 1,161 行）、ObjectExplorerView（pages/ 1,304 行 vs business-workbench/ 1,553 行）。

| Task | 内容 | 验收 |
|------|------|------|
| C1 | WorkshopView.tsx 2,243 行（src/pages/）→ WorkshopView + WidgetRenderer + VariableManager + PageTabs | 单文件 < 400 行，UI 零退化 |
| C2 | GuardrailsView / ObjectExplorerView / DictManager（1,606 行）同理拆分；**先判定 2 对重复文件权威版本再拆** | 同上 |
| C3 | ~~ScheduleBean 814 行~~ **作废**（死代码已删） | — |
| C4 | CausalReasonerServiceImpl 726 行（cognitive2/service/）→ CausalDetector + RootCauseAnalyzer + SuggestionBuilder | 同上 |
| C5 | buildWhereClause complexity 30 → 策略模式拆 ≤10（现位于 data-engine/BaseJdbcAdapter） | ArchUnit + 单测 |

### 阶段 D：架构守护（P3，持续）

> 差距分析（2026-08-23）：D2 ComponentFactory（A+8）、D3 previewRestfulData（A+9a）均已在死代码清理中删除 → **两者作废**。
> D1 已有 5 个 ArchUnit 测试基础：sysman/ArchitectureTest、runtime-core/ArchitectureTest、gateway/DcchengRemovalGuardTest、common-api/ArchitectureGuardTest + ArchitectureTest。
> D4 services 层现状为 10 个子服务（agent/ai/api-gateway/catalog/cognitive/identity/knowledge/object/ontology/workflow），非原计划的 12 个。

| Task | 内容 | 验收 |
|------|------|------|
| D1 | ArchUnit 扩展到 engine/sysman/gateway 模块（已有 5 测试基础，增量补断言） | 断言：engine 不依赖 dccheng、Controller 跨模块不重复、API 路径不重复 |
| D2 | ~~ComponentFactory 迪米特法则~~ **作废**（死代码已删） | — |
| D3 | ~~previewRestfulData 12 参数~~ **作废**（已删） | — |
| D4 | **services 层收敛为四转化服务**：ge-service（D→I）、zhi-service（I→K）、cheng-service（K→C）、ming-service（K→W），替换散乱的 10 个子服务 | 每转化服务有独立 Controller + curl 验收 |

---

## 三、风险与回滚

- **dccheng 拆解是最大风险**：移除前必须先跑 ArchUnit 断言"engine 不依赖 dccheng"，确认零依赖后再删。拆解顺序：先迁对象代码 → 再移 pom module → 最后删目录。
- **每次改动单独 commit**：阶段内每个 Task 一个 commit，便于 git revert。
- **Gateway 依赖重编陷阱**：`mvn install -pl gateway` 不重编下游模块，必须 `-am` 或全量 install。
- **命名红线**：dccheng 拆解后，`cheng` 名不得被任何对象管理模块占用，留给服务层 K→C 转化服务（阶段 D4）。

---

## 四、决策点状态

| # | 决策 | 状态 |
|---|------|------|
| D0 | 五行+格致诚明框架入架构宪法 | ✅ 已确认，已写入 ARCHITECTURE-RULES.md |
| D1 | dccheng 拆解（非废弃） | ✅ 已确认 |
| D2 | agent/chat 权威 = ai-engine | ✅ 已确认 |
| D3 | Redis 分版本引入 | ✅ 已确认 |
| D4 | ActionHookExecutor/FunctionEvaluator 归 ontology-engine | ✅ 已确认 |
| D5 | 基础设施访问含 git（PG/Neo4j/MinIO/Doris/Git） | ✅ 已确认 |
| D6 | llm-gateway 归属不变（留 runtime） | ✅ 已确认 |
| D7 | 命名用 core（非 common） | ✅ 已确认 |

全部决策点已确认。下一步：按阶段 A 出 PMO 指令（A1 拆解 dccheng 为第一条，已出）。
