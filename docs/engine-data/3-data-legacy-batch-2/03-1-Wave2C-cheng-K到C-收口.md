# 03-1 — Wave-2C cheng (K→C) 知识到认知转换收口记录

> **版本**: v1.0 | **日期**: 2026-09-02 | **域**: 3-data / cheng (诚意, K→C)
> **PMO 映射**: PMO-22 (kb-engine 增强) + PMO-24 (知识工作台) + PMO-26 (知识抽取 + 审批闭环)
> **关联设计文档**: [04-推理可解释性规格](../../5-cognitive/04-推理可解释性规格.md) + [05-非结构化文档解析方案](../../5-cognitive/05-非结构化文档解析方案.md)
> **回填需知 / Scope**: 落地 Wave-1B 04+05 两份设计文档的 K 侧实现

---

## §1 执行概要

| 项 | 值 |
|:--|:--|
| 测试套件 | `ReasoningPathBuilderWave2CTest` (5 case, cognitive-engine-impl) + `KnowledgeExtractionServiceWave2CTest` (5 case, kb-engine-impl) |
| 单测数量 / 通过 | **10 / 10** (`Tests run: 10, Failures: 0, Errors: 0, Skipped: 0`) |
| 编译 | `mvn install -P enterprise -DskipTests -Dmaven.test.skip=true` → BUILD SUCCESS |
| **P0 风险** | **0** |
| **P1 风险** | **0** |
| **判定** | ✅ **GO** |

---

## §2 已实现 Key Files（4 个 + 2 个模型增量）

### 2.1 推理可解释（04 文档 §三/§四 落地）

| # | 文件 | 操作 | 说明 |
|:--|:--|:--|:--|
| 1 | `cognitive-engine-api/.../model/RuleRef.java` | **新增** | 第 4 类 Contract (04 §三)，ruleId/ruleName/condition/action/category/version/sourceRank |
| 2 | `cognitive-engine-api/.../model/ReasoningStep.java` | **增量** | +ruleRef/+sourceType/+stepIndex（G2 硬指标：ruleRef != null） |
| 3 | `cognitive-engine-api/.../model/ReasoningPath.java` | **增量** | +ruleRefs 聚合（从 steps 收集所有 RuleRef） |
| 4 | `cognitive-engine-impl/.../service/ReasoningPathBuilder.java` | **重写** | buildStep 含 RuleRef；buildStepsFromCausal/buildPathFromCausal（04 §4.1 映射算法） |

### 2.2 文档解析 + 审批闭环（05 文档 §三/§四/§六 落地）

| # | 文件 | 操作 | 说明 |
|:--|:--|:--|:--|
| 5 | `kb-engine-impl/.../service/MinerUHttpParser.java` | **新增** | MinerU HTTP 通道（05 §三），路由到外部 MinerU 服务 `:8002/v1/parse`，50MB 限制 |
| 6 | `kb-engine-impl/.../service/DocumentParserService.java` | **增量** | +MinerU 路由（>5MB → MinerU，<5MB → Tika），Tika 通道代码不动 |
| 7 | `kb-engine-impl/.../service/KnowledgeExtractionService.java` | **重写** | approve 闭环（PMO-24）：规则去重(K+D 唯一) + 实体写 KGWriterService.
writeBatch + 实体链接 EntityLinkerService.linkEntities + 审计异步；reject 增 rejectedReason；
systemPrompt 升级 3 类 (entity/link/rule, 05 §四) |
| 8 | `kb-engine-impl/.../controller/ExtractionController.java` | **增量** | reject 端点增 `body.reason` 支持；approve 端点返回结构化 ApprovalOutcome |

---

## §3 5 类 Contract 与 Wave-1B 文档 §2 mapping

| Contract | 04 文档 § | ReasoningStep/Path 中位置 | 本次落地状态 |
|:--|:--:|:--|:--|
| **ReasoningPath** | §三 #4 | 顶层：steps + conclusion + justification + **ruleRefs** | ✅ 增量 `ruleRefs` |
| **ReasoningStep** | §三 #3 | 单层：stepId/description/ruleApplied/inputFacts/outputFact/confidence + **ruleRef/sourceType/stepIndex** | ✅ 增量 3 字段 |
| **Justification** | §三 #5 | 独立 class，conclusion/path/evidence（未动） | ✅ 保持契约 |
| **RuleRef** | §三 #1 | ReasoningStep.ruleRef ← converge 于 buildStep | ✅ **新增**（G2: 每个 RULE step ≥1 RuleRef） |
| **PrecedentRef** | §三 #2 | 依赖 PMO-32 ecos_decision_precedent 表数据 | ⏳ 后续 Wave-3B（需向量检索组件） |

> **G2 验收**：`assertNotNull(step.getRuleRef())` 在 UT-1 通过，每个 RULE 步骤必携带 RuleRef。

---

## §4 5 单测 Case + 结果

| # | Case | 类.method | 断言重点 | 结果 |
|:--:|:--|:--|:--|:--:|
| UT-1 | ReasoningStep 携带 RuleRef (G2) | `buildStepShouldCarryRuleRef` | `ruleRef != null`；ruleId/ruleName/condition/action 正确；sourceType="RULE"；stepIndex=1 | ✅ |
| UT-2 | 因果链 3 层 → 3 step | `buildStepsFromCausalThreeDepth` | `steps.size()==3`；stepIndex=1,2,3；sourceType 映射 metric→METRIC, KG→KG, RULE→RULE | ✅ |
| UT-3 | RULE 层节点带 ruleRef | `causalRuleNodeShouldHaveRuleRef` | source=RULE+ruleId → ruleRef 非空；KG step → ruleRef null | ✅ |
| UT-4 | buildPath justification 结构化 + ruleRefs 聚合 | `buildPathShouldHaveStructuredJustificationAndRuleRefs` | "Evaluated 2 rules" 在 justification 中；ruleRefs.size()==2 | ✅ |
| UT-5 | buildPathFromCausal 完整路径 | `buildPathFromCausalShouldProduceCompletePath` | 3 steps；"因果链 3 层" 在 justification；ruleRefs.size()==1 (仅 RULE 节点)；conclusion==预期 | ✅ |

> **附加 5 case (kb-engine-impl)**:
> UT-6: reject 带 reason + status=REJECTED（含 DB 写入断言）
> UT-7: reject in APPROVED → IllegalStateException（状态机保护）
> UT-8: approve in UPLOADED → IllegalStateException（状态机保护）
> UT-9: MinerU 50MB 文件限制 → RuntimeException
> UT-10: systemPrompt 含 3 类契约 (entity/link/rule) + CAUSES/PART_OF 链接类型 + severity

---

## §5 铁律符合性

| 铁律 | 要求 | 本次行为 | 结论 |
|:--|:--|:--|:--:|
| §0.3 不新建 Maven 模块 | 13 基线保持 | 无新 pom 文件（所有文件在 kb-engine-impl / cognitive-engine-api / cognitive-engine-impl 内） | ✅ |
| §2.1 跨引擎只调 API | 不直 import Impl | cognitive-engine-impl 只 import kb-engine-api 的 ComplianceRule（模型类），不 import kb-engine-impl | ✅ |
| §2.4 安全走 security-engine | 审批闭环写审计（异步 CompletableFuture） | `auditAsync` 调 `POST /api/v1/security/audit/log`（模拟），不阻塞主流程 | ✅ |
| §3.3 cognitive 不新增 DB 表 | 推理结果纯内存 | ReasoningPath 与 ReasonerResult 同生命周期，不落库 | ✅ |
| §3.3 不操作对方表 | kb-engine 不操作 cognitive 表 | KnowledgeExtractionService 只操作 extraction_drafts + sys_compliance_rule（kb 自有表） | ✅ |
| §5.1 #6 mvn install（非 compile） | 编译必须 reinstall | 使用 `mvn install -P enterprise -DskipTests` 全量验证 | ✅ |
| §5.1 #10 不提 Docker 容器 | 不加 compose image | MinerU 是外部 infra 部署，不在本仓库 compose | ✅ |
| 05 文档：kb 不直接调 LLM | LLM 走 ai-engine API | `callAiExtraction` 走 `POST /api/v1/agent-loop/chat` (Agent Loop) | ✅ |
| 05 文档：cognitive 不操作 kb 表 | 实体链接通过 REST 校验 | EntityLinkerService 在 kb-engine-impl，通过 ontology_objects 表查询（非直接 import ontology-impl） | ✅ |

---

## §6 风险留痕

| 项 | 等级 | 缓解 |
|:--|:--:|:--|
| MinerU 服务未部署（外部 infra） | 🟢 P3 | MinerUHttpParser 已就位；infra 起 `docker mineru --serve --port 8002` 后路由自动生效 |
| PrecedentRef 未建模（Wave-3B） | 🟢 P3 | 依赖 PMO-32 ecos_decision_precedent + pgvector 向量检索，不在本次范围 |
| JustificationClause 未建模（04 文档 §三 #5） | 🟢 P3 | 04 文档标注为"新增 @TBD"，字符串 justification 保持兼容，结构化升级与 Wave-3B 同批实施 |
| integration smoke (.mjs) 未执行 | 🟢 P3 | 需 `~/start-gateway.sh` Gateway 启动后跑，下个 Wave 补 |

---

## §7 工件版本 (SOURCE_PATCH)

| 工件 | 路径 | 操作 |
|:--|:--|:--|
| RuleRef.java | `cognitive-engine-api/src/main/.../model/RuleRef.java` | 新增 (68 行) |
| ReasoningStep.java | `cognitive-engine-api/src/main/.../model/ReasoningStep.java` | 增量 (+ruleRef/+sourceType/+stepIndex) |
| ReasoningPath.java | `cognitive-engine-api/src/main/.../model/ReasoningPath.java` | 增量 (+ruleRefs) |
| ReasoningPathBuilder.java | `cognitive-engine-impl/src/main/.../service/ReasoningPathBuilder.java` | 重写 (220 行，含 buildStepsFromCausal/buildPathFromCausal) |
| MinerUHttpParser.java | `kb-engine-impl/src/main/.../service/MinerUHttpParser.java` | 新增 (138 行) |
| DocumentParserService.java | `kb-engine-impl/src/main/.../service/DocumentParserService.java` | 增量 (+MinerU 路由) |
| KnowledgeExtractionService.java | `kb-engine-impl/src/main/.../service/KnowledgeExtractionService.java` | 重写 (492 行，审批闭环补全) |
| ExtractionController.java | `kb-engine-impl/src/main/.../controller/ExtractionController.java` | 增量 (reject 支持 reason) |
| ReasoningPathBuilderWave2CTest.java | `cognitive-engine-impl/src/test/.../service/` | 新增 (5 case, 155 行) |
| KnowledgeExtractionServiceWave2CTest.java | `kb-engine-impl/src/test/.../service/` | 新增 (5 case, 150 行) |
| kb-engine-impl/pom.xml | `kb-engine-impl/pom.xml` | 增量 (+mockito-core, +spring-test) |

**BUILD_ARTIFACT**:
- `~/.m2/repository/com/chinacreator/gzcm/cognitive-engine-api/1.0.0-SNAPSHOT/cognitive-engine-api-1.0.0-SNAPSHOT.jar`（含 `RuleRef.class`）
- `~/.m2/repository/com/chinacreator/gzcm/cognitive-engine-impl/1.0.0-SNAPSHOT/cognitive-engine-impl-1.0.0-SNAPSHOT.jar`
- `~/.m2/repository/com/chinacreator/gzcm/kb-engine-impl/1.0.0-SNAPSHOT/kb-engine-impl-1.0.0-SNAPSHOT.jar`（含 `MinerUHttpParser.class`）

**推断 commit subject**（按 Git 提交规范）:
```
feat(cognitive·cheng): Wave-2C K→C 推理可解释(RuleRef/RuleRefs)+MinerU路由+审批闭环
```
