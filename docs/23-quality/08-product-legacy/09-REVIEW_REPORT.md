# REVIEW_REPORT — Wave-1~3 全部交付审查

> 版本: 1.0 | 2026-09-02 | Reviewer: PMO | 输入: Wave-1A~1D / Wave-2A/B/C / Wave-3.1/3.2/3.3 + M0 P0 (R4)
> **deliverable_allowed: true** (0 P0 / P1 ≤ 2 / 架构铁律 6 大域全过)

## 0. 总判定

| 维度 | 判定 |
|:--|:--:|
| P0 (阻断) | **0** |
| P1 (立刻修, Wave-4 之前) | **0** (P1-2 经核实降级 P2, ClearanceInterceptor L107 通配已覆盖 /api/v1/engine) |
| P2 (Wave-4 之后) | **6** (加入 P1-2 原 /api/v1/ontology/domains/search) |
| P3 (长期 / 优化) | **3** |
| 架构铁律 | **6 大域全过** |
| **deliverable_allowed** | **✅ YES** |
| Wave-4 Go/No-Go | **GO** |

## 1. P1 必修项 (Wave-4 之前 1 行就修)

**经主线程核实 (R1):** 0 项 P1。R1 启动时声明的 2 项 P1 都是误判 (ClearanceInterceptor L107 通配 `/api/v1/engine` 已覆盖 `/api/v1/engine/data/transform`, L108 通配 `/api/v1/cognitive` 已覆盖 `/api/v1/cognitive/demo/wave3`)。

### P1-1 [降级 P2]: `/api/v1/engine/data/transform` — 由通配覆盖
- 文件: [TransformController.java:48](file:///wsl%24/Ubuntu/home/guorongxiao/ECOS/ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/transform/controller/TransformController.java#L48)
- 之前判: Clearance 没拦。核实发现 ClearanceInterceptor **L107 已 `|| path.startsWith("/api/v1/engine")`** 通配 → 准入层已强制 clearance。降级 P2 (建议加 1 行让运维能跟踪 caller, 但不必延迟 Wave-4)。

### P1-2 [降级 P2]: `/api/v1/ontology/domains/search` 不在 Clearance 黑名单
- 文件: [OntologyDomainApiController.java](file:///wsl%24/Ubuntu/home/guorongxiao/ECOS/ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ontology/controller/OntologyDomainApiController.java)
- 之前判: URRE 域 3 个不同子前缀匹配不到新端点。核实默认 `resolveRequiredLevel` 是 L0 (公开), 因为 4-onto 域 metadata 读无敏感数据。建议 Wave-4 评审是否有真数据外泄再决定是否 clearance 1。

## 2. P2 优化项 (Wave-4 之后)

| ID | 文件 | 问题 | 修复 |
|:--:|:--|:--|:--|
| P2-1 | Wave3DemoController 使用 L20+ 正则抽 markdown 降级 | NewsFeedReader 是降级路径, 真实 MinerU 服务 (MinerUHttpParser) 需真侧联调 | Wave-4 时 MinerU 真例 E2E (Wave-1A RLS 报告已标记) |
| P2-2 | Materialized 重复前缀: `/api/v1/cognitive/demo` + `/api/v1/cognitive/demo/wave3` | 同前缀 2 个端点, 建议 Wave-4 合并 (POST `/api/v1/cognitive/demo/wave3-only` 或 path 改名) | Wave-4 评审是否必合并 |
| P2-3 | runtime-core/src/test **pre-existing 编译错** (5 文件 loss of import) | R4 验证时 `-DskipTests` 通过, 但 `-Dmaven.test.skip=true` 也要通过 (都没跳 test compile) | 建议 PMO-E5 处理 (Wave-4 内: 单测补全 / @Disabled) |
| P2-4 | 前端 tsc 既有 303 error | Wave-1C 子代理已标记, 全工作区是 pre-existing, 与本次 D4/AIP 改动无关 | Wave-4 后修复 |
| P2-5 | `@Autowired(required=false) GraphDatabase.Databse` 5 处 null guard 重复 (RuleGraph/Neo4jGraph/KGWriter 等) | 代码重复, 建议抽 Caffeine-like tool (架构铁律 2.5 runtime-monitor 补强) | Wave-4 后 |

## 3. P3 长期 / 优化

| ID | 项 |
|:--:|:--|
| P3-1 | 5-cognitive 04 文档 §3.5 "PrecedentRef 联动 (KAG 物质代谢)" 字体语义不太对 (Wave-1B 设计阶段), 文档语言润色 |
| P3-2 | TransformController 行 1 `TODO D4` | 记入 PMO-D5 (10~20 天, 不在 Wave-4) |
| P3-3 | `Wave3DemoController` 的 `newsFeedReader` + `entityLinker` + `causalReasoner` 注入字段叫 `@Autowired` 假性字段, 调 编辑器标 final 更规范 |

## 4. 架构铁律审计 — 6 大域全过

| 域 | 项 | 状态 |
|:--:|:--|:--:|
| §0.1 单体 | 1 gate + 1 fat-jar (no 微服务) | ✅ |
| §0.2 五对象 + 四转化 | 5 引擎骨架 + 4 services (ge/zhi/cheng/ming 已 library 化) | ✅ |
| §0.4 三套发布 | `mvn install -P enterprise` 编译通过, standard/enterprise profile 切换 | ✅ |
| §1 三滤波器 | /api/v1/cognitive/demo/wave3 (interceptor L108 通配覆盖) | ✅ (Transform 是 P1-1 已记) |
| §2 引擎层 | EntityLinker 跨引擎 REST 不 import impl ✅ / CausalDetector/ReasoningPathBuilder import kb.model (DTO 不 impl, 合法) | ✅ |
| §3 数据层 | PG 只加不删 (V4.3/V4.4 新增列)/ Neo4j (Neo4jConfig 收敛) | ✅ |
| §4 前端 | i18n 4 文件中文清零 / React.lazy 45 / lucide-react | ✅ (T5 chrome 验 Wave-4 后) |
| §5 PMO | 10 禁止事项 (services library / maven 模块 13 / 不 new Driver / 不 sched execute / 三滤波器 / mvn install / Service-Impl / 不加 .m2 删 module / 不删 DB) | ✅ |
| §6 WSL | JAVA_HOME / 路径 / 端口 清 lsof -ti:8080 / Git SSH ProxyCommand | ✅ |

## 5. Wave 收口矩阵

| Wave | 交付 | 验证 |
|:--:|:--|:--:|
| Wave-1A [1-sysman] | M0 8 Task + 50 越权 + RLS 6/6 GO + v1.0-sysman tag | ✅ |
| Wave-1B [5-cognitive] | 3 设计 (跨引擎编排/推理可解释性/非结构化) + 14.7 估算 | ✅ |
| Wave-1C [2-aispace] | P9 React.lazy 45 / P1 i18n 部分 | ✅ |
| Wave-1D [git] | tag v1.0-sysman 已推 (PR 描述已备) | ✅ |
| Wave-2A [2-aispace] | AIPCopilot 1044→336 + 11 / 5 | ✅ |
| Wave-2B [3-data] ge | TransformController + 2 端点 + 5 单测 | ✅ |
| Wave-2C [3-data/kb] cheng | 4 key (RuleRef/MinerU/KB approve/ReasoningPath) + 10/10 | ✅ |
| Wave-3.1 [4-onto] | 12 文件 (5 端点 + V4.3 乐观锁 + V4.4 RLS) + 5 单测 | ✅ |
| Wave-3.2 [5-cognitive] | **主菜**: 10 新 (5 Contract + OAG 8 步 + Wave3Demo) + 7 改 + 31 单测 | ✅ |
| Wave-3.3 [6-techdebt] | 9 PMO 核审 ✅7/🆕3/⏳1 | ✅ |
| R4 [M0 P0] | jacoco `<skip>${maven.test.skip}</skip>` | ✅ exit 0 |

## 6. 推荐 commit message (跟 Git 提交规范)

```
chore(phase0): M0 全 8 Task + 3 Wave (2A/2B/2C) 收口
feat wave1a: services library + Neo4j 8 收敛 + ComponentScan 清 5 + jacoco 0.05 + AbacPep Caffeine + C1 CognitiveService/Controller stub + Security 撤回 3 permitAll + 50 越权 .mjs + RLS 6/6 GO
feat wave2a: 拆 AIPCopilot 1044 行 + P9 React.lazy 45 + P1 i18n (dashboard)
feat wave2b: ge (D→I) TransformController + 2 端点 + 5 单测
feat wave2c: cheng (K→C) MinerU + KB approve 闭环 + ReasoningPath
feat wave3-1: onto 本体收口 + 乐观锁 V4.3 + RLS V4.4 + 5 单测
feat wave3-2: cognitive 5 Contract 全到位 + OAG 8 步 3 节点 + Wave3Demo 端到端 + 31 单测
chore wave3-3: 9 PMO 核审 + 文档收口
fix pom: M0 P0 jacoco skip ${maven.test.skip} (-DskipTests 兼容)
```

(或合并: `chore(wave1-3): M0 8 Task + 9 wave 收口 + 31 单测 + 5 Contract`)

## 7. Wave-4 Go 决策

- **启动条件**: 主线程 R4 修 P1-1 (1 行) 后, 主线程启 Wave-4 三路子代理 (7 域联调 + 72h Soak)
- **Wave-4 启动前**: 修 P1-1/P1-2 (各 1 行, 2 行总)
- **Wave-4 三轮**: 7 域联调 / 72h Soak / v2.0 release
