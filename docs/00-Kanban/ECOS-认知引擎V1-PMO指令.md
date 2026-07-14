# PMO指令：认知引擎 V1 — 从骨架到真实的语义编排运行时

> **来源**: 肖国荣 | **日期**: 2026-07-10
> **铁律**: 单文件粒度。Service层优先——Controller只做路由。DONE=commit hash+curl 200。禁止产出解释性文档。

---

## 零、架构决策

### 认知引擎的定位

```
知识工作台 ──(元数据+图谱)──→ 认知引擎 ──(Prompt+Agent+护栏)──→ AI工作台
                                  │
                            ┌─────┼─────┐
                            │  认知引擎   │
                            │ 四个子引擎  │
                            └─────┼─────┘
                                  │
                    知识工作台 ←──┘──→ AI工作台
```

认知引擎是知识工作台和AI工作台之间的"神经中枢"——不是大模型调用网关，是语义编排运行时。

### 语言选型

用户设计要求使用Rust。**工程现状是纯Java（Spring Boot + MyBatis）**。V1维持Java实现——
- 高性能路径（向量检索、图谱遍历）委托给Neo4j/PGVector引擎
- 编排逻辑（Agent Mesh、Guardrails、Prompt Compiler）在Java Service层
- Rust迁移是Phase 3的事，V1先让四引擎活起来

### 四个子引擎映射

| 用户设计 | V1落地名称 | 现有基础 |
|------|------|------|
| Federated RAG & Knowledge Retriever | **PromptCompilerService** | 全新 |
| Multi-Agent Mesh Coordinator | **AgentMeshService** | AgentMeshController已存在，需抽Service |
| Cognitive Guardrails | **GuardrailsService** | GuardrailsApiController占位实现，需重写 |
| Action Execution Bridge | **ActionBridgeService** | 全新 |

### 现有资产盘点

| 资产 | 状态 | V1动作 |
|------|------|------|
| `CognitiveController` (744行) | ✅ 真实：RuleEngine+CausalReasoner+NsgaII | 保留，不动 |
| `AgentMeshController` (207行) | ✅ 真实：Agent/Mission CRUD+执行 | 抽Service |
| `GuardrailsApiController` (165行) | ⚠️ 占位：CopyOnWriteArrayList | 重写为JdbcTemplate持久化 |
| `KnowledgeGraphController` (157行) | ✅ 真实：Neo4j+PG双源 | 保留 |
| `CognitiveEngineImpl` (46行) | ❌ 骨架：硬编码RUNNING | 升级为真实生命周期 |
| `cognitive-engine-api` | ❌ 空壳：0个接口 | 补4个Service接口 |
| 其他11个Controller | ✅ 大部分真实 | 不动 |

---

## 一、目标结构

### 后端 Service 层 (全部新建)

```
engine/cognitive-engine/
├── cognitive-engine-api/
│   └── src/.../engine/cognitive/
│       ├── PromptCompilerService.java      ← 新建：混合召回+上下文编译
│       ├── AgentMeshService.java           ← 新建：Agent/Mission编排
│       ├── GuardrailsService.java          ← 新建：安全护栏策略引擎
│       └── ActionBridgeService.java        ← 新建：LLM输出→本体Action映射
│
├── cognitive-engine-impl/
│   └── src/.../engine/cognitive/
│       ├── CognitiveEngineImpl.java        ← 修改：真实生命周期
│       ├── service/
│       │   ├── PromptCompilerServiceImpl.java   ← 新建
│       │   ├── AgentMeshServiceImpl.java        ← 新建（抽离AgentMeshController逻辑）
│       │   ├── GuardrailsServiceImpl.java       ← 新建（替代占位实现）
│       │   └── ActionBridgeServiceImpl.java     ← 新建
│       └── controller/
│           ├── CognitiveController.java         ← 不动
│           ├── AgentMeshController.java         ← 精简（逻辑移到Service）
│           ├── GuardrailsApiController.java     ← 精简
│           ├── PromptCompilerController.java    ← 新建
│           ├── ActionBridgeController.java      ← 新建
│           └── (其余11个Controller不动)
```

### 前端 (Phase 4)

```
c2eos/src/pages/cognitive-engine/
├── CognitiveEngineView.tsx        ← 新建：认知引擎控制台
├── tabs/
│   ├── PromptCompilerTab.tsx      ← 新建：上下文编译沙箱
│   ├── AgentMeshTab.tsx           ← 改造自现有AgentStudio
│   ├── GuardrailsTab.tsx          ← 改造自现有Guardrails页面
│   └── ActionBridgeTab.tsx        ← 新建：动作执行桥
├── services/
│   └── cognitiveEngineApi.ts      ← 新建：API调用封装
└── typesAndConstants.ts           ← 新建
```

---

## 二、禁止清单

1. 禁止新增Maven模块——在现有cognitive-engine下扩展
2. 禁止改动已有API路径签名——只增不改
3. 禁止在Controller中直接操作JdbcTemplate——必须走Service
4. 禁止产出解释性文档
5. 禁止前端新增npm依赖
6. 禁止新增Rust模块（V1纯Java）
7. 禁止改CognitiveController（744行那坨——RuleEngine/CausalReasoner/NsgaII）

---

## 三、Phase 1：EngineImpl真实化 + api接口补全（1天，BE）

### P1-1: cognitive-engine-api 补全4个Service接口（BE，0.5天）

**目标**: 空壳api模块补上四个子引擎的接口契约。

**新建文件**（共4个，均在 `engine/cognitive-engine/cognitive-engine-api/` 下）:

**1. `PromptCompilerService.java`**
```java
package com.chinacreator.gzcm.engine.cognitive;

public interface PromptCompilerService {
    /** 混合召回：向量检索(metadata) + 图谱检索(KG) → 合并上下文 */
    CompileResult compileContext(CompileRequest req);
    /** 获取当前索引状态 */
    IndexStatus getIndexStatus();
}
// CompileRequest: { query, userId, topK, enableKG?, enableVector? }
// CompileResult: { prompt, sources[{type:"vector"|"graph",title,snippet,score}], tokensUsed }
// IndexStatus: { vectorCount, graphNodeCount, lastSyncTime, status }
```

**2. `AgentMeshService.java`**
```java
package com.chinacreator.gzcm.engine.cognitive;

public interface AgentMeshService {
    /** 解析用户意图 → 路由到目标Agent */
    RouteResult routeIntent(IntentRequest req);
    /** 查询Mission执行状态 */
    MissionStatus getMissionStatus(String missionId);
}
// IntentRequest: { query, context? }
// RouteResult: { targetAgentId, targetAgentName, confidence, reasoning }
// MissionStatus: { missionId, status, completedTasks, totalTasks, currentStep }
```

**3. `GuardrailsService.java`**
```java
package com.chinacreator.gzcm.engine.cognitive;

public interface GuardrailsService {
    /** 校验LLM输出：幻觉检测+PII脱敏+合规检查 */
    ValidationResult validate(ValidationRequest req);
    /** 策略CRUD */
    List<GuardrailPolicy> listPolicies();
    GuardrailPolicy createPolicy(GuardrailPolicy policy);
    void deletePolicy(String id);
}
// ValidationRequest: { llmOutput, contextFacts[], userId, sensitivityLevel }
// ValidationResult: { passed, issues[{type:"HALLUCINATION"|"PII_LEAK"|"COMPLIANCE", detail, severity}], sanitizedOutput }
// GuardrailPolicy: { id, name, type, rules, enabled }
```

**4. `ActionBridgeService.java`**
```java
package com.chinacreator.gzcm.engine.cognitive;

public interface ActionBridgeService {
    /** 将LLM文本输出 → 匹配本体Action算子 → 生成确定性执行payload */
    ActionMatchResult matchAction(ActionMatchRequest req);
    /** 获取可用的Action算子列表 */
    List<ActionDef> getAvailableActions();
}
// ActionMatchRequest: { llmOutput, domain? }
// ActionMatchResult: { matched, actionId, actionName, parameters, confidence, needsApproval }
// ActionDef: { id, name, ontologyType, inputSchema, outputSchema, approved }
```

**验收**:
```bash
cd /home/guorongxiao/databridge-v2
mvn compile -pl engine/cognitive-engine/cognitive-engine-api -am -DskipTests -q
# 期望: BUILD SUCCESS，4个接口编译通过
```

---

### P1-2: CognitiveEngineImpl 真实化（BE，0.5天）

**目标**: 把46行骨架升级为真实生命周期（对标SecurityEngineImpl/DataEngineImpl）。

**修改文件**: `/home/guorongxiao/databridge-v2/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive/CognitiveEngineImpl.java`

**改动**: 参照`SecurityEngineImpl`（68行）的模式：
```java
@Component
public class CognitiveEngineImpl implements IEngine {
    private final AtomicReference<EngineStatus> status = new AtomicReference<>(EngineStatus.STOPPED);
    private final JdbcTemplate jdbc;  // 构造器注入

    @Override public String getName() { return "cognitive-engine"; }
    @Override public EngineStatus getStatus() { return status.get(); }
    @Override public Map<String, Object> getConfig() {
        return Map.of("module", "cognitive", "controllers", 17,
            "status", status.get().name(), "subEngines", List.of("prompt-compiler","agent-mesh","guardrails","action-bridge"));
    }
    @Override public HealthCheck healthCheck() {
        boolean dbUp = /* ping */; return new HealthCheck(dbUp ? "UP" : "DOWN", ...);
    }
    @Override public void start() { status.compareAndSet(STOPPED, RUNNING); log.info(...); }
    @Override public void stop() { status.compareAndSet(RUNNING, STOPPED); log.info(...); }
}
```

**验收**:
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.data.accessToken')
H="Authorization: Bearer $TOKEN"

# 认知引擎健康检查
curl -s -H "$H" http://localhost:8080/api/v1/engine/cognitive/health | jq '.data'
# 期望: {"overall":"UP","details":{"db":"UP","engine":"RUNNING"}}

# 配置快照
curl -s -H "$H" http://localhost:8080/api/v1/engine/cognitive/config | jq '.data.subEngines'
# 期望: ["prompt-compiler","agent-mesh","guardrails","action-bridge"]
```

---

## 四、Phase 2：四个子引擎 Service 实现（3天，BE）

### P2-1: PromptCompilerServiceImpl（BE，1天）

**目标**: 联邦RAG混合召回——向量检索 + 图谱检索 → 合并上下文→编译Prompt。

**新建文件**: `/home/guorongxiao/databridge-v2/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive/service/PromptCompilerServiceImpl.java`

**实现策略**:
1. **向量检索**: 用JdbcTemplate查PG的向量表（如有pgvector）或降级为ILIKE关键字匹配
2. **图谱检索**: 注入`KnowledgeGraphService`（已有，runtime-core），调用`getGraph(domain)` 拿子图
3. **上下文合并**: 向量结果片段 + 图谱子拓扑描述 → 拼成结构化Prompt
4. **前置权限过滤**: 根据userId查sys_role/sys_user的权限范围，过滤掉未授权的数据源

**开放端点** (在PromptCompilerController中):
```
POST /api/v1/cognitive/compile-context
  Body: { query, userId?, enableKG:true, enableVector:true, topK:5 }
  → { prompt, sources[{type,title,snippet,score}], tokensUsed }

GET /api/v1/cognitive/index-status
  → { vectorCount, graphNodeCount, lastSyncTime, status }
```

**验收**:
```bash
curl -s -X POST -H "$H" http://localhost:8080/api/v1/cognitive/compile-context \
  -H "Content-Type: application/json" \
  -d '{"query":"客户订单数据来源","topK":3}' | jq '.data.prompt'
# 期望: 非空字符串，包含向量检索和图谱检索的混合上下文
```

---

### P2-2: AgentMeshServiceImpl（BE，1天）

**目标**: 从AgentMeshController抽离业务逻辑到Service层。

**新建文件**: `AgentMeshServiceImpl.java`

**实现策略**:
1. 把AgentMeshController中现有的Agent/Mission CRUD逻辑移到Service
2. 新增意图路由：`routeIntent()` —— 用规则匹配（关键字→Agent映射表）+ 置信度打分
3. 复用的Repository：`AgentRegistryRepository`、`MissionRepository`、`MissionTaskRepository`（已有）

**精简**: AgentMeshController从207行缩减到≤100行（纯@RestController→调Service）

**开放端点**:
```
POST /api/agent-mesh/route-intent
  Body: { query, context? }
  → { targetAgentId, targetAgentName, confidence, reasoning }

# 已有端点保持路径不变，内部改为调Service
```

**验收**:
```bash
curl -s -X POST -H "$H" http://localhost:8080/api/agent-mesh/route-intent \
  -H "Content-Type: application/json" \
  -d '{"query":"帮我分析这个月的销售趋势"}' | jq '.data.targetAgentName'
# 期望: 返回匹配的Agent名称(如 "SalesAnalyst")
```

---

### P2-3: GuardrailsServiceImpl（BE，0.5天）

**目标**: 从占位内存存储→JdbcTemplate持久化 + 幻觉检测逻辑。

**新建文件**: `GuardrailsServiceImpl.java`

**实现策略**:
1. 策略持久化：JdbcTemplate读写`td_guardrail_policy`表（如不存在则建表）
2. 验证逻辑：
   - **幻觉检测**: LLM输出中的实体（航班号/客户名/产品编码）是否在contextFacts中存在
   - **PII检测**: 正则匹配身份证/手机号/邮箱/SSN等模式
   - **合规检查**: 关键词黑名单（偏见/情绪化表述）
3. 脱敏输出：`sanitizedOutput`——替换PII为`[REDACTED]`

**精简**: GuardrailsApiController从165行缩减到≤80行

**验收**:
```bash
# 验证含幻觉的输出
curl -s -X POST -H "$H" http://localhost:8080/api/v1/guardrails/validate \
  -H "Content-Type: application/json" \
  -d '{"llmOutput":"航班UA9999已起飞，请联系张三13812345678。","contextFacts":["UA1001","UA2002"],"userId":"u001"}' \
  | jq '.data'
# 期望: passed=false, issues包含HALLUCINATION和PII_LEAK, sanitizedOutput中138****替换为[REDACTED]
```

---

### P2-4: ActionBridgeServiceImpl（BE，0.5天）

**目标**: LLM文本输出→本体Action算子匹配→确定性执行payload。

**新建文件**: `ActionBridgeServiceImpl.java`

**实现策略**:
1. 从`sys_ontology_action`表（或本体引擎的Action定义）加载可用算子列表
2. 匹配逻辑：LLM输出中识别意图动词（"重新调度""创建工单""更新库存"）→匹配Action名称/描述
3. 生成payload：根据Action的inputSchema，从LLM输出中提取参数值
4. 返回`needsApproval: true`——所有写回操作需人工确认

**验收**:
```bash
curl -s -X POST -H "$H" http://localhost:8080/api/v1/cognitive/match-action \
  -H "Content-Type: application/json" \
  -d '{"llmOutput":"建议将航班UA1001重新调度到明天上午10点","domain":"aviation"}' \
  | jq '.data'
# 期望: matched=true, actionName包含"reschedule", needsApproval=true, parameters非空
```

---

## 五、Phase 3：前端认知引擎控制台（1.5天，FE）

### P3-1: typesAndConstants.ts（FE，0.25天）

**新建文件**: `/home/guorongxiao/c2eos/src/pages/cognitive-engine/typesAndConstants.ts`

包含：四个子引擎的TypeScript类型定义、图标映射、静态配置。

### P3-2: cognitiveEngineApi.ts（FE，0.25天）

**新建文件**: `/home/guorongxiao/c2eos/src/pages/cognitive-engine/services/cognitiveEngineApi.ts`

封装所有认知引擎端点调用。

### P3-3: CognitiveEngineView.tsx 主控制台（FE，1天）

**新建文件**: `/home/guorongxiao/c2eos/src/pages/cognitive-engine/CognitiveEngineView.tsx`

**布局**: 四卡片式控制台，每卡片对应一个子引擎：

| 卡片 | 内容 |
|------|------|
| 🧠 上下文编译器 | 输入query→实时查看混合召回结果+编译后的Prompt→复制Prompt到剪贴板 |
| 🤖 Agent Mesh | Agent列表+Mission状态+意图路由测试 |
| 🛡️ 安全护栏 | 策略列表+验证沙箱（输入LLM输出→查看检测结果） |
| 🔧 动作执行桥 | 算子列表+匹配测试（输入LLM输出→查看匹配结果+payload） |

**路由注册**: 在侧边栏或AI工作台下新增"认知引擎"入口。

---

## 六、Phase 4：闭环验证（0.5天，FE+BE联调）

### V1-V8 端到端验证

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
  -H "Content-Type: application/json" \
  -d '{"query":"分析订单数据","topK":3}' | jq '.data.prompt'
# 期望: 非空

# V3: Agent路由
curl -s -X POST -H "$H" http://localhost:8080/api/agent-mesh/route-intent \
  -H "Content-Type: application/json" \
  -d '{"query":"销售趋势分析"}' | jq '.data.targetAgentName'
# 期望: 非空

# V4: 护栏验证
curl -s -X POST -H "$H" http://localhost:8080/api/v1/guardrails/validate \
  -H "Content-Type: application/json" \
  -d '{"llmOutput":"客户张三的身份证号是110101199001011234。","contextFacts":[],"userId":"u001"}' \
  | jq '.data.passed'
# 期望: false (PII检测)

# V5: 动作匹配
curl -s -X POST -H "$H" http://localhost:8080/api/v1/cognitive/match-action \
  -H "Content-Type: application/json" \
  -d '{"llmOutput":"建议重新调度航班UA1001到2026-07-11 10:00"}' \
  | jq '.data.matched'
# 期望: true

# V6: 蓝图
curl -s -H "$H" http://localhost:8080/api/v1/cognitive/blueprint | jq '.data.overallScore'
# 期望: >0

# V7: 前端TS编译
cd /home/guorongxiao/c2eos && npx tsc --noEmit 2>&1 | grep -c "error"
# 期望: 0

# V8: 后端编译
cd /home/guorongxiao/databridge-v2 && mvn compile -q -DskipTests
# 期望: BUILD SUCCESS
```

---

## 七、执行顺序

```
Phase 1: 地基（并行）
  P1-1: 4个Service接口 (BE, 0.5天)
  P1-2: EngineImpl真实化 (BE, 0.5天)
  └→ 验证: EngineImpl健康检查返回UP

Phase 2: 子引擎实现（可部分并行）
  P2-1: PromptCompilerService (BE, 1天)    ← 复杂度最高
  P2-2: AgentMeshService (BE, 1天)
  P2-3: GuardrailsService (BE, 0.5天)      ← 可与P2-1并行
  P2-4: ActionBridgeService (BE, 0.5天)    ← 可与P2-1并行
  └→ 验证: 4个端点全通

Phase 3: 前端控制台
  P3-1+P3-2 → P3-3 (FE, 1.5天)
  └→ 验证: 浏览器打开控制台

Phase 4: 闭环验证 (FE+BE, 0.5天)
```

**总工时**: ~6.5天

---

## 八、交付检查清单

| # | 检查项 | 验证方式 |
|---|--------|---------|
| 1 | EngineImpl健康检查返回UP | curl /engine/cognitive/health |
| 2 | compile-context返回混合召回Prompt | curl POST |
| 3 | route-intent返回Agent匹配结果 | curl POST |
| 4 | guardrails/validate检测幻觉+PII | curl POST（含假数据） |
| 5 | match-action返回本体Action匹配 | curl POST |
| 6 | AgentMeshController ≤100行 | wc -l |
| 7 | GuardrailsApiController ≤80行 | wc -l |
| 8 | 前端零TS错误 | npx tsc --noEmit |
| 9 | 后端编译通过 | mvn compile -q |
| 10 | 认知引擎控制台四卡片均可交互 | 浏览器截图 |

---

## 九、关键设计决策（给PMO的背景）

### 为什么不用Rust？
工程现在是纯Java栈。V1的目标是"让认知引擎活起来"——真实EngineImpl + 四个Service + 端点可用。Rust重写在V1不碰。向量检索和图谱遍历的性能路径委托给Neo4j/PGVector引擎，Java层只做编排。

### 为什么Prompt Compiler是第一个子引擎？
因为它是认知引擎所有下游能力的前置——Agent路由、护栏校验、动作匹配都需要"已经编译好的带上下文的Prompt"。没有它，其他三个子引擎只有空转。

### 为什么Guardrails从占位升级为JdbcTemplate？
占位的CopyOnWriteArrayList重启丢数据。生产环境的护栏策略必须持久化。不能为了省事留个假实现。

### 为什么ActionBridge所有写回都需要人工确认？
安全铁律——LLM生成的写回指令不可自动执行。`needsApproval`必须为true，等待AI工作台的人工确认。

---

## 十、一句话给PMO

**认知引擎的骨架已经在了（16个Controller 3957行代码），但三个致命缺陷：EngineImpl是假的、Guardrails是占位的、没有Service层。V1做四件事：①把EngineImpl从46行骨架升级为真实生命周期 ②建四个Service（PromptCompiler/AgentMesh/Guardrails/ActionBridge）③把Controller里的逻辑剥离到Service ④前端做个四卡片控制台。不动CognitiveController那坨744行的推理器——那是工作遗产。Rust重写是Phase 3的事。**
