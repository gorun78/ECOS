# PMO指令：认知引擎V1 — 骨架→真实语义编排运行时

> **来源**: 肖国荣 | **日期**: 2026-07-10
> **铁律**: 单文件粒度。Service层优先。DONE=commit hash+curl 200。禁止产出解释性文档。

---

## 零、审计结论：CognitiveController 合格性评估

`CognitiveController.java` (744行) 包含三个推理器：

| 组件 | 状态 | 判决 |
|------|------|:--:|
| `RuleEngine` | ✅ 真实工厂模式，`evaluate(facts)→List<MatchedRule>` | **保留** |
| `CausalReasoner` | ✅ 真实图遍历算法，`traceAllPaths/findRootCauses` | **保留** |
| `NsgaIIOptimizer` | ✅ 真实NSGA-II多目标优化，`optimize()` | **保留** |
| Blueprint六层数据 | ❌ 硬编码数组，L1-L6分数写死 | **重写：对接真实数据源** |
| 因果图 | ❌ `buildDefaultCausalGraph`硬编码边 | **重写：对接Neo4j知识图谱** |
| 目标函数评估器 | ❌ `Random(42)`模拟，非真实指标 | **重写：对接实际业务指标** |
| ExecutionPlan | ❌ ConcurrentHashMap内存，重启丢失 | **重写：JdbcTemplate持久化** |

**结论**: 三个推理器保留——它们是真实算法。**数据喂养层全部重写**：蓝图→读引擎状态，因果图→读Neo4j，优化器→读实际指标。

---

## 一、架构决策

### 四个子引擎（对齐你的设计）

```
认知引擎
├── 子引擎1: PromptCompiler   — 联邦RAG混合召回 + 上下文编译
├── 子引擎2: AgentMesh         — 多Agent协同编排 + 意图路由
├── 子引擎3: Guardrails        — 零信任护栏(幻觉检测/PII脱敏/合规)
├── 子引擎4: ActionBridge      — LLM输出→本体Action→自动执行
└── 继承: CognitiveController  — RuleEngine/CausalReasoner/NsgaII
```

### 独立性保持

认知引擎保持独立引擎模块。**不在知识工作台内嵌认知引擎代码**。参照本体工作台模式：
- 认知引擎 = 独立 `engine/cognitive-engine/` 模块（现状保持）
- 知识工作台 = 新增"认知引擎配置"Tab（配置子引擎参数）
- AI工作台 = 消费认知引擎的Agent Mesh + ActionBridge端点
- 知识工作台 ↔ 认知引擎：通过REST API交互（知识工作台提供元数据，认知引擎消费）

### 语言决策

**纯Java实现。** 高性能路径（向量检索/图谱遍历）委托给PGVector/Neo4j引擎。

---

## 二、现有资产盘点

```
engine/cognitive-engine/
├── cognitive-engine-api/     ← ❌ 空壳，0个接口
├── cognitive-engine-impl/
│   ├── CognitiveEngineImpl   ← ❌ 骨架(46行，硬编码RUNNING)
│   ├── CognitiveController   ← ✅ 保留推理器，重写数据喂养层
│   ├── AgentMeshController   ← ✅ 真实(207行，10端点)，需抽Service
│   ├── GuardrailsApiController ← ⚠️ 占位(165行，CopyOnWriteArrayList)
│   ├── KnowledgeGraphController ← ✅ 真实(157行，Neo4j+PG双源)
│   ├── 其他12个Controller    ← 不动
│   └── service/              ← ❌ 空目录，0个Service
└── cognitive-engine-boot/    ← ✅ 已建
```

---

## 三、禁止清单

1. 禁止新增Maven模块
2. 禁止改已有API路径
3. 禁止在Controller中直接操作JdbcTemplate——必须走Service
4. 禁止产出解释性文档
5. 禁止引入非Java技术栈
6. 禁止把认知引擎代码嵌入知识工作台前端
7. 禁止改动三个推理器（RuleEngine/CausalReasoner/NsgaIIOptimizer）的算法逻辑

---

## 四、Phase 1：地基——api接口 + EngineImpl（1天，BE）

### P1-1: cognitive-engine-api 补4个接口（BE，0.5天）

**新建**（均在 `engine/cognitive-engine/cognitive-engine-api/src/main/java/com/chinacreator/gzcm/engine/cognitive/`）:

**1. `PromptCompilerService.java`**
```java
public interface PromptCompilerService {
    CompileResult compileContext(CompileRequest req);
    IndexStatus getIndexStatus();
}
// CompileResult: { prompt, sources[{type,title,snippet,score}], tokensUsed }
```

**2. `AgentMeshService.java`**
```java
public interface AgentMeshService {
    RouteResult routeIntent(IntentRequest req);
    MissionStatus getMissionStatus(String missionId);
}
```

**3. `GuardrailsService.java`**
```java
public interface GuardrailsService {
    ValidationResult validate(ValidationRequest req);
    List<GuardrailPolicy> listPolicies();
    GuardrailPolicy createPolicy(GuardrailPolicy p);
    void deletePolicy(String id);
}
```

**4. `ActionBridgeService.java`**
```java
public interface ActionBridgeService {
    ActionMatchResult matchAndExecute(ActionMatchRequest req);
    List<ActionDef> getAvailableActions();
}
// matchAndExecute: 匹配→生成payload→自动执行→返回结果
// 不需要人工确认——直接执行
```

**验收**: `mvn compile -pl engine/cognitive-engine/cognitive-engine-api -am -q` → BUILD SUCCESS

---

### P1-2: CognitiveEngineImpl 真实化（BE，0.5天）

**修改**: `/home/guorongxiao/databridge-v2/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive/CognitiveEngineImpl.java`

参照`SecurityEngineImpl`模式：
```java
@Component
public class CognitiveEngineImpl implements IEngine {
    private final AtomicReference<EngineStatus> status = new AtomicReference<>(STOPPED);
    private final JdbcTemplate jdbc;

    @Override public String getName() { return "cognitive-engine"; }
    @Override public EngineStatus getStatus() { return status.get(); }
    @Override public HealthCheck healthCheck() {
        boolean dbUp = /* jdbc ping */;
        return new HealthCheck(dbUp ? "UP" : "DOWN",
            Map.of("db", dbUp ? "UP":"DOWN", "engine", status.get().name()));
    }
    @Override public void start() { status.set(RUNNING); }
    @Override public void stop() { status.set(STOPPED); }
    @Override public Map<String, Object> getConfig() {
        return Map.of("subEngines", List.of("prompt-compiler","agent-mesh","guardrails","action-bridge"));
    }
}
```

**验收**:
```bash
curl -s -H "$H" http://localhost:8080/api/v1/engine/cognitive/health | jq '.data.overall'
# 期望: "UP"
```

---

## 五、Phase 2：CognitiveController重构——保留推理器，重写数据喂养（2天，BE）

### P2-1: CognitiveService 提取（BE，1天）

**目标**: 把CognitiveController中500行内部构建方法剥离到Service。

**新建**: `engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive/service/CognitiveService.java`

**移入方法**:
- `buildBlueprint()` → 改名为 `buildBlueprintFromEngineStates()`，数据来源从硬编码→调用四引擎的healthCheck + DataEngine的指标API
- `doRuleReason()` → 保留，RuleEngine调用不变
- `doCausalReason()` → 保留CausalReasoner调用，**但因果图来源改为从Neo4j KnowledgeGraphService动态加载**
- `buildSimulatedEvaluator()` → 改名为 `buildMetricEvaluator()`，对接实际业务指标（从PostgreSQL的metrics表读取）
- ExecutionPlan的CRUD → 改为JdbcTemplate持久化

**精简**: CognitiveController从744行→≤200行（纯路由+参数校验→调Service）

**验收**:
```bash
# 蓝图从真实引擎状态生成
curl -s -H "$H" http://localhost:8080/api/v1/cognitive/blueprint | jq '.data.overallScore'
# 期望: >0，且不是固定87.5（不再是硬编码平均值）

# 因果推理从Neo4j加载因果图
curl -s -X POST -H "$H" http://localhost:8080/api/v1/cognitive/reason \
  -H "Content-Type: application/json" \
  -d '{"mode":"causal","facts":{"event":"SALES_DROP"}}' | jq '.data.rootCauses | length'
# 期望: >0（如果Neo4j有数据）
```

---

### P2-2: 认知引擎配置Tab——知识工作台侧（FE+BE，1天）

**目标**: 知识工作台新增"认知引擎配置"Tab，配置四个子引擎参数。

**BE**: 新增 `CognitiveConfigController.java`
```java
@RestController
@RequestMapping("/api/v1/cognitive/config")
public class CognitiveConfigController {
    @GetMapping → 返回四个子引擎的配置
    @PutMapping → 更新配置（写入sys_config, config_group="cognitive"）
}
// 配置项:
//   prompt-compiler.topK (default:5)
//   prompt-compiler.vectorModel (default:text-embedding-3-small)
//   agent-mesh.routingStrategy (default:keyword_match)
//   guardrails.piiDetectionEnabled (default:true)
//   guardrails.hallucinationCheckEnabled (default:true)
//   action-bridge.autoExecuteEnabled (default:true)
//   action-bridge.matchConfidenceThreshold (default:0.7)
```

**FE**: 新建文件
- `/home/guorongxiao/c2eos/src/pages/knowledge/tabs/CognitiveConfigTab.tsx` — 配置表单
- 知识工作台Tab注册：在KnowledgeView.tsx的Tab列表中排在最后，**样式靠底对齐**（`margin-top: auto`），与上方内容区形成视觉分隔

**验收**:
```bash
curl -s -H "$H" http://localhost:8080/api/v1/cognitive/config | jq '.data."prompt-compiler.topK"'
# 期望: "5"
```

---

## 六、Phase 3：四个子引擎Service实现（2.5天，BE）

### P3-1: PromptCompilerServiceImpl（BE，1天）

**新建**: `engine/cognitive-engine/cognitive-engine-impl/.../service/PromptCompilerServiceImpl.java`

**实现**:
1. 向量检索：通过KnowledgeApiController的端点查PGVector索引
2. 图谱检索：注入`KnowledgeGraphService`，调用`getGraph(domain)`拿子图拓扑
3. 上下文合并：向量片段 + 图谱子图描述 → 拼成结构化System Prompt
4. 前置权限过滤：根据userId过滤未授权数据源的检索结果

**新建**: `engine/cognitive-engine/cognitive-engine-impl/.../controller/PromptCompilerController.java`
```java
@RestController
@RequestMapping("/api/v1/cognitive")
public class PromptCompilerController {
    @PostMapping("/compile-context")
    ApiResponse<CompileResult> compile(@RequestBody CompileRequest req);

    @GetMapping("/index-status")
    ApiResponse<IndexStatus> status();
}
```

**验收**:
```bash
curl -s -X POST -H "$H" http://localhost:8080/api/v1/cognitive/compile-context \
  -d '{"query":"订单数据来源","topK":3}' | jq '.data.prompt'
# 期望: 非空
```

---

### P3-2: AgentMeshServiceImpl（BE，0.5天）

**新建**: AgentMeshServiceImpl——从AgentMeshController抽离业务逻辑。

**新增端点** (在AgentMeshController中):
```java
@PostMapping("/route-intent")
ApiResponse<RouteResult> routeIntent(@RequestBody IntentRequest req);
```

**验收**:
```bash
curl -s -X POST -H "$H" http://localhost:8080/api/agent-mesh/route-intent \
  -d '{"query":"销售趋势分析"}' | jq '.data.targetAgentName'
```

---

### P3-3: GuardrailsServiceImpl（BE，0.5天）

**新建**: GuardrailsServiceImpl——JdbcTemplate持久化 + 规则校验引擎。

**验证逻辑**:
- 幻觉检测：LLM输出中的实体是否在contextFacts中存在
- PII检测：正则匹配身份证/手机号/邮箱
- 合规检查：关键词黑名单

**后端端点已在GuardrailsApiController中，改为调Service即可**。

**验收**:
```bash
curl -s -X POST -H "$H" http://localhost:8080/api/v1/guardrails/validate \
  -d '{"llmOutput":"航班UA9999已起飞，联系张三13812345678。","contextFacts":["UA1001"],"userId":"u001"}' \
  | jq '.data.passed'
# 期望: false
```

---

### P3-4: ActionBridgeServiceImpl（BE，0.5天）

**新建**: ActionBridgeServiceImpl——LLM输出→本体Action匹配→**自动执行**。

**与之前版本的差异**: 不需要人工确认。`matchAndExecute`直接执行。
```java
public ActionMatchResult matchAndExecute(ActionMatchRequest req) {
    // 1. 从LLM输出提取意图动词
    // 2. 匹配本体Action算子
    // 3. 生成确定性payload
    // 4. 执行action（调用对应API）
    // 5. 记录审计日志
    // 6. 返回执行结果
}
```

**验收**:
```bash
curl -s -X POST -H "$H" http://localhost:8080/api/v1/cognitive/execute-action \
  -d '{"llmOutput":"建议将航班UA1001重新调度到2026-07-11 10:00","domain":"aviation"}' \
  | jq '.data'
# 期望: matched=true, executed=true
```

---

## 七、Phase 4：前端认知引擎控制台（1.5天，FE）

### P4-1: typesAndConstants + apiService（FE，0.5天）

**新建**:
- `/home/guorongxiao/c2eos/src/pages/cognitive-engine/typesAndConstants.ts`
- `/home/guorongxiao/c2eos/src/pages/cognitive-engine/services/cognitiveEngineApi.ts`

### P4-2: CognitiveEngineView.tsx 四卡片控制台（FE，1天）

**新建**: `/home/guorongxiao/c2eos/src/pages/cognitive-engine/CognitiveEngineView.tsx`

四卡片布局：

| 卡片 | 内容 | 后端端点 |
|------|------|------|
| 🧠 上下文编译 | 输入query→实时查看混合召回+编译后Prompt | `POST /compile-context` |
| 🤖 Agent Mesh | Agent列表+Mission+意图路由测试 | `AgentMeshController` |
| 🛡️ 安全护栏 | 策略列表+验证沙箱 | `GuardrailsApiController` |
| 🔧 动作执行 | 算子列表+匹配执行测试 | `POST /execute-action` |

---

## 八、Phase 5：闭环验证（0.5天）

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.data.accessToken')
H="Authorization: Bearer $TOKEN"

# V1: 引擎健康
curl -s -H "$H" http://localhost:8080/api/v1/engine/cognitive/health | jq '.data.overall'
# 期望: "UP"

# V2: 上下文编译
curl -s -X POST -H "$H" http://localhost:8080/api/v1/cognitive/compile-context \
  -d '{"query":"订单数据分析","topK":3}' | jq '.data.prompt'
# 期望: 非空

# V3: Agent路由
curl -s -X POST -H "$H" http://localhost:8080/api/agent-mesh/route-intent \
  -d '{"query":"销售趋势分析"}' | jq '.data.targetAgentName'
# 期望: 非空

# V4: 护栏验证
curl -s -X POST -H "$H" http://localhost:8080/api/v1/guardrails/validate \
  -d '{"llmOutput":"张三13812345678的订单已发货。","contextFacts":[],"userId":"u001"}' \
  | jq '.data.passed'
# 期望: false

# V5: 动作执行
curl -s -X POST -H "$H" http://localhost:8080/api/v1/cognitive/execute-action \
  -d '{"llmOutput":"重新调度UA1001到7月11日10点"}' | jq '.data.executed'
# 期望: true

# V6: 蓝图（真实数据）
curl -s -H "$H" http://localhost:8080/api/v1/cognitive/blueprint | jq '.data.overallScore'
# 期望: >0，且非固定87.5

# V7: 认知引擎配置
curl -s -H "$H" http://localhost:8080/api/v1/cognitive/config | jq '.data'
# 期望: 返回配置json

# V8: TS编译
cd /home/guorongxiao/c2eos && npx tsc --noEmit 2>&1 | grep -c "error"
# 期望: 0

# V9: 后端编译
cd /home/guorongxiao/databridge-v2 && mvn compile -q -DskipTests
# 期望: BUILD SUCCESS
```

---

## 九、执行顺序

```
Phase 1: 地基（并行）
  P1-1 api接口 (0.5天) + P1-2 EngineImpl (0.5天)

Phase 2: CognitiveController重构
  P2-1 CognitiveService提取 (1天)
  └→ P2-2 知识工作台配置Tab (1天)

Phase 3: 子引擎Service（可并行）
  P3-1 PromptCompiler (1天)     ← 复杂度最高
  P3-2 AgentMesh (0.5天)        ← 可并行
  P3-3 Guardrails (0.5天)       ← 可并行
  P3-4 ActionBridge (0.5天)     ← 可并行

Phase 4: 前端
  P4-1 (0.5天) → P4-2 (1天)

Phase 5: 闭环验证 (0.5天)
```

**总工时**: ~8天

---

## 十、交付检查清单

| # | 检查项 | 验证 |
|---|--------|------|
| 1 | EngineImpl返回UP | curl health |
| 2 | compile-context混合召回 | curl POST |
| 3 | route-intent返回Agent | curl POST |
| 4 | guardrails检测PII+幻觉 | curl POST（假数据） |
| 5 | execute-action自动执行 | curl POST |
| 6 | blueprint非硬编码 | curl→overallScore≠87.5 |
| 7 | 因果图从Neo4j加载 | curl POST reason mode=causal |
| 8 | 知识工作台有认知引擎配置Tab | 浏览器截图 |
| 9 | CognitiveController ≤200行 | wc -l |
| 10 | 前端零TS错误 | npx tsc |
| 11 | 后端编译通过 | mvn compile |

---

## 十一、一句话给PMO

**CognitiveController保留三个推理器（RuleEngine/CausalReasoner/NsgaII——它们是真实算法），重写数据喂养层（蓝图从引擎状态读，因果图从Neo4j读，优化器从实际指标读）。新建四个子引擎Service（PromptCompiler/AgentMesh/Guardrails/ActionBridge），Guardrails从占位升级为JdbcTemplate持久化，ActionBridge直接自动执行不卡人工确认。知识工作台加一个配置Tab，认知引擎保持独立模块不嵌入。**
