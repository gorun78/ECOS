# ECOS 技术债修复计划（基于 codebase-memory 诊断报告）

> 来源：肖国荣 | 日期：2026-08-21
> 依据：codebase-memory 图谱诊断（38,498 节点 / 121,477 边 / 1,418 Java + 373 TS）
> 交叉验证：已抽查 dccheng Controller 清单、agent/chat 三处冲突、knowledge/rag 三处冲突，报告可信，部分结论已修正

---

## 零、诊断报告修正（先对齐认知，再执行）

诊断报告整体可信，但交叉验证后发现 4 处需修正，直接改变 P0 的执行顺序：

| # | 报告说法 | 实际核查 | 影响 |
|---|---------|---------|------|
| 1 | agent/chat 3 处冲突，建议"保留 agent-service 的 AgentRuntimeController 链路" | AgentRuntimeController 用 `/api/v1/agent-runtime` 前缀，**不参与** `/api/v1/agent/chat` 冲突。真正冲突的是 3 个：ai-engine/AgentChatController、ai-engine/DiagnosticAgentController、gateway/DiagnosticAgentController | 权威归属需重新判定，不是 agent-service |
| 2 | "dccheng ≈ engine/ontology-engine 历史 fork" | dccheng 24 个 Controller 里，19 个是 ontology（错放的 I 层代码），但还有 knowledge/classification/glossary/guardrails 5 类，**全部在 engine 层已有对应**（kb-engine 4 个 + ai-engine 2 个 + ontology-engine 1 个） | dccheng 可整体废弃，不止是 ontology fork |
| 3 | 未提 services 层 12 子服务 | services 层是薄壳（大部分 0-2 Controller），其中 knowledge-service/KnowledgeRagController 映射 `/api/v1/knowledge`，与 kb-engine 冲突 | P0 要处理 services 层的路径冲突 |
| 4 | "P1 缓存迁移 Caffeine + Redis" | ECOS 三版本里 standard 只有 PG，无 Redis | 引入 Redis 破坏版本架构，需分版本选型 |

**核心诊断（我的判断）**：ECOS 处于"三层并存"状态——engine 层（6引擎，权威）+ dccheng（历史 fork，24 Controller 全部有 engine 对应）+ services 层（薄壳微服务）。299 个 API 路径冲突、代码重复、超大文件的根因都是这个三层并存。

---

## 一、修复总原则

1. **engine 层是唯一权威**。dccheng 的 K/C 职责已全部被 kb-engine/cognitive-engine 接管，ontology 代码被 ontology-engine 接管。
2. **废弃 dccheng，不修它**。24 个 Controller 全部在 engine 有对应，修 dccheng 是浪费，直接移除 module。
3. **services 层本次只修冲突，不废弃**。它是面向未来的微服务化方向，但当前薄壳，先消除路径冲突，定位问题留待后续。
4. **不破坏三版本架构**。缓存选型分版本：standard=Caffeine 本地，enterprise/ultimate 加 Redis。
5. **每阶段独立可验收**，git commit + curl/tsc 为唯一产出。

---

## 二、分阶段执行计划

### 阶段 A：止血（P0，1-2 周）

| Task | 内容 | 验收 |
|------|------|------|
| A1 | 废弃 dccheng：从 pom.xml 4 个 profile 移除 `<module>dccheng</module>`，删 dccheng 目录 | 全量 `mvn install` BUILD SUCCESS，`grep -r dccheng` 仅剩注释/历史文档 |
| A2 | 统一 agent/chat 归属：删 ai-engine/DiagnosticAgentController 和 gateway/DiagnosticAgentController 里的 `/chat`，保留 ai-engine/AgentChatController 为唯一权威 | Gateway 启动无 Ambiguous mapping，`curl /api/v1/agent/chat` 200 |
| A3 | 统一 knowledge/rag 归属：删 dccheng/KnowledgeApiController 和 services/knowledge-service 的 `/rag`，保留 kb-engine/RagController | `curl /api/v1/knowledge/rag` 200 |
| A4 | 统一 `/api/security/mask` + `/api/v1/policy-engine/evaluate` 到 security-engine | 端点 200，无重复路由 |
| A5 | 前端去重：删 components/AsyncTaskCenterView.tsx（与 pages 完全相同），确认 ObjectExplorerView/Sidebar 等 18 处权威版本 | `npx tsc --noEmit` 零新增错误 |

**A 阶段决策点（需拍板）**：
- **D1**：dccheng 废弃确认——我判断 24 Controller 全部有 engine 对应，可安全废弃。确认？
- **D2**：agent/chat 权威 = ai-engine/AgentChatController（AI 工作台 Agent 对话入口），非 agent-service。确认？

### 阶段 B：内存与稳定性（P1，2-4 周）

| Task | 内容 | 验收 |
|------|------|------|
| B1 | TokenServiceImpl.blacklist → Caffeine（standard）+ Redis TTL（enterprise/ultimate） | 重启后黑名单仍生效 |
| B2 | InMemoryPermissionCacheService / InMemoryDecisionCacheService → Caffeine | 多实例一致 |
| B3 | DictService.cache / SysConfigService.cache → Caffeine + 启动预热 | 首次查询不冷启动 |
| B4 | 修复 21 个无保护递归（重点 IFieldMappingDao.addMappingRefs、AgentSessionService.compressHistory、AbacPepService.evaluate、BaseJdbcAdapter.query） | 补 base case，单测覆盖 |
| B5 | 消除隐藏 O(n²)：synthesizeFromArray 内层 List→Map，CausalReasonerServiceImpl 预聚合 | 复杂度降 O(n log n) |

**B 阶段决策点**：
- **D3**：Redis 引入确认——standard 版不引 Redis（保持 PG-only），enterprise/ultimate 才加。确认？

### 阶段 C：拆分与可维护性（P1-P2，4-8 周）

| Task | 内容 | 验收 |
|------|------|------|
| C1 | WorkshopView.tsx 1,951 行 → WorkshopView + WidgetRenderer + VariableManager + PageTabs | 单文件 < 400 行，UI 零退化 |
| C2 | GuardrailsView 1,404 / ObjectExplorerView 1,478 / DictManager 1,390 同理拆分 | 同上 |
| C3 | ScheduleBean 814 行/155 方法 → ScheduleTrigger/ScheduleAction/ScheduleStatus | 编译通过 + curl |
| C4 | CausalReasonerServiceImpl 691 行 → CausalDetector + RootCauseAnalyzer + SuggestionBuilder | 同上 |
| C5 | buildWhereClause complexity 30 → 策略模式拆 ≤10 | ArchUnit + 单测 |

### 阶段 D：架构守护（P3，持续）

| Task | 内容 | 验收 |
|------|------|------|
| D1 | ArchUnit 扩展到 engine/sysman/gateway 模块 | 断言：engine 不依赖 dccheng、Controller 跨模块不重复、API 路径不重复 |
| D2 | ComponentFactory 迪米特法则修复（max_access_depth=7）→ Spring @Autowired | 编译 + 启动 |
| D3 | previewRestfulData 12 参数 → PreviewRequest record | 编译 + curl |

---

## 三、风险与回滚

- **dccheng 废弃是最大风险**：移除前必须先跑 ArchUnit 断言"engine 不依赖 dccheng"，确保零依赖后再删。
- **每次改动单独 commit**：阶段内每个 Task 一个 commit，便于 git revert。
- **Gateway 依赖重编陷阱**：`mvn install -pl gateway` 不重编下游模块，必须 `-am` 或全量 install。

---

## 四、待确认决策点汇总

| # | 决策 | 我的推荐 | 影响 |
|---|------|---------|------|
| D1 | dccheng 废弃 | 废弃（24 Controller 全有 engine 对应） | P0 核心 |
| D2 | agent/chat 权威归属 | ai-engine/AgentChatController | P0-A2 |
| D3 | Redis 引入范围 | standard 不引，enterprise/ultimate 才加 | P1-B1/B2 |

确认这 3 个决策点后，我按阶段出 PMO 指令。
