# AI引擎 vs Hermes：能力盘点与Java自研评估

> 肖总 / 2026-08-01

---

## 一、先看ECOS AI引擎已经有什么

翻完了ai-engine-impl的全部Controller。底盘远超预期——不是说骨架，是实打实有代码的：

| Hermes能力 | ECOS AI引擎现状 | 代码位置 |
|------------|----------------|---------|
| Agent配置管理 | ✅ CRUD + 工具绑定 + 知识库绑定 + Prompt版本管理 + 测试控制台 | `AgentConfigController` (208行) |
| 多Agent编排 | ✅ Agent注册/Mission创建/Pipeline模式/意图路由/执行引擎 | `AgentMeshController` (218行) + `MissionExecutionEngine` |
| LLM调用 | ✅ LLMGatewayService（DeepSeek）+ 子系统级Semaphore隔离 | `AgentCallController` (123行) |
| 工具注册与执行 | ✅ 工具定义表CRUD + 执行 + CEO诊断场景 | `DiagnosticAgentController` (423行) |
| 护栏/安全策略 | ✅ validate端点 + 策略CRUD | `GuardrailsApiController` (69行) |
| Action Bridge | ✅ LLM输出模式匹配→自动执行动作 | `ActionBridgeService` (111行) |
| 技能管理 | ✅ Skill CRUD | `SkillController` |
| 定时任务 | ✅ CronJob管理 | `CronJobController` |
| NLQ/分类/Copilot | ✅ 各独立Controller | 8+个额外Controller |
| 推理器 | ✅ CognitiveController（保留自审计、需重写数据喂养） | `CognitiveController` |

**一句话：配置层已经齐了。缺的是运行时执行层。**

---

## 二、Hermes真正做了什么（ECOS还没做的）

Hermes的核心不是"管理Agent"——那个ECOS早有了。Hermes的核心是**Agent的运行时**：

```
Hermes Agent Runtime:
┌──────────────────────────────────────────────┐
│  ① Agent Loop（推理循环）                     │
│  think → call_tool → observe → think → 输出  │
│  不是单次chat/completions，是多轮工具调用链    │
├──────────────────────────────────────────────┤
│  ② Session/Memory（会话与记忆）               │
│  会话持久化 + 消息历史 + Memory注入            │
│  跨轮对话不丢上下文                           │
├──────────────────────────────────────────────┤
│  ③ Streaming（流式输出）                      │
│  SSE逐token推送，不是等全部生成完再返回        │
├──────────────────────────────────────────────┤
│  ④ Delegation（子Agent委托）                  │
│  一个Agent在推理过程中可以spawn子Agent         │
│  完成子任务后结果回流到主Agent                 │
└──────────────────────────────────────────────┘
```

这四块是ECOS AI引擎真正缺失的。其他的——Agent配置、工具注册、护栏、技能——ECOS都已经有代码了，只需要补齐。

---

## 三、需要Java实现的四块

### ③A → Agent Loop（推理循环）—— **P0，最关键**

**现状**：`AgentCallController`调用`LLMGatewayService.execute()`，一次HTTP请求→一次LLM调用→返回。没有工具调用循环。

**Hermes怎么做**：LLM返回tool_call→执行工具→把工具结果塞回prompt→再次调LLM→...→直到LLM返回最终答案。

**Java实现路径**：在ai-engine-impl新增`AgentLoopService`
```java
// 核心逻辑（~200行Java）
AgentLoopResult runLoop(AgentConfig config, String userMessage, Session session) {
    List<Message> messages = session.getHistory();
    messages.add(Message.user(userMessage));
    
    for (int turn = 0; turn < config.getMaxTurns(); turn++) {
        LLMResponse llmResp = llmGateway.chat(messages, config.getTools());
        
        if (llmResp.isFinal()) {
            session.append(llmResp);
            return AgentLoopResult.complete(llmResp.content);
        }
        
        if (llmResp.hasToolCall()) {
            ToolResult toolResult = toolExecutor.execute(llmResp.toolCall);
            messages.add(llmResp.asToolCallMessage());
            messages.add(Message.toolResult(toolResult));
            session.append(llmResp, toolResult);
            // 继续循环
        }
    }
    return AgentLoopResult.maxTurnsExceeded();
}
```

**难点**：不是代码量大，是工具调用的超时控制、并发工具调用、错误重试、工具结果截断（长结果要压缩再塞回prompt）。

**工期**：3天

---

### ③B → Session/Memory（会话与记忆）—— **P0**

**现状**：`hermes-engine`模块有`SessionManager`存会话，但`AgentScheduler`不读历史消息——多轮对话不工作（此乃7月审计发现的致命伤之一）。

**Hermes怎么做**：Session持久化到SQLite + Memory注入到每轮system prompt。

**Java实现路径**：利用ECOS已有的PG，在ai-engine-impl新增`AgentSessionService`
```java
// 会话模型
AgentSession {
    sessionId, agentId, userId, tenantId,
    messages: List<Message>,       // 对话历史
    workingMemory: Map<String,Object>, // 工作记忆（工具调用中间结果）
    longTermMemory: List<MemoryFact>,  // 长期记忆（KG存储）
    createdAt, lastActiveAt
}
```

**关键设计**：
- 消息历史存在PG（`sys_agent_session` + `sys_agent_message`两张表）
- Memory注入：每轮对话前，从KG查询相关记忆事实，拼入system prompt
- 会话过期：政企场景默认30分钟超时（比Hermes的24小时保守）

**工期**：2天

---

### ③C → Streaming（流式输出）—— **P1**

**现状**：`AgentCallController`硬编码`stream=false`。SSE解析代码写了但从未被调用（7月审计发现）。

**Java实现路径**：Spring Boot原生支持SSE（`SseEmitter`），改造`AgentCallController.chat()`返回值
```java
@PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter chatStream(@RequestBody Map<String, Object> body) {
    SseEmitter emitter = new SseEmitter(300_000L); // 5分钟超时
    agentLoopService.runLoopAsync(config, message, session, 
        token -> {
            try { emitter.send(SseEmitter.event().data(token)); } 
            catch (IOException e) { emitter.completeWithError(e); }
        },
        () -> emitter.complete(),
        error -> emitter.completeWithError(error)
    );
    return emitter;
}
```

**工期**：1天

---

### ③D → Delegation（子Agent委托）—— **P1**

**现状**：AgentMesh有Mission/Pipeline模式，但这是在Agent对话开始前预设的。缺少**对话进行中动态spawn子Agent**的能力。

**Hermes怎么做**：Agent Loop中遇到复杂子任务→spawn子Agent→子Agent独立运行→结果回流主Agent→主Agent继续推理。

**Java实现路径**：扩展`AgentLoopService`
```java
// 在Agent Loop中
if (llmResp.isDelegate()) {
    // 主Agent暂停，子Agent带上下文独立运行
    AgentLoopResult subResult = runLoop(
        subAgentConfig, 
        llmResp.delegationInstruction, 
        new Session(subAgentConfig) // 子Agent独立session
    );
    // 子结果注入回主Agent的消息历史
    messages.add(Message.subAgentResult(subResult));
    // 主Agent继续循环
}
```

**比起Hermes的简化**：ECOS第一版不支持嵌套委托（子Agent不能再委托），这个限制对于政企场景够用了。

**工期**：2天

---

## 四、不需要动的部分

ECOS已有的这些Hermes能力**不需要重新实现**——它们已经够用了：

| 能力 | 现状 | 理由 |
|------|------|------|
| 工具注册 | `DiagnosticAgentController` | 有DB持久化 + 执行引擎，补齐即可 |
| 护栏/安全 | `GuardrailsApiController` | 已有策略CRUD + validate端点 |
| 技能管理 | `SkillController` | 已有CRUD，技能文件可以是Markdown存PG |
| 定时任务 | `CronJobController` | 已有管理端点，执行器用Spring @Scheduled |
| Prompt管理 | `AgentConfigController.getPrompts` | 已有版本列表 + 模板管理 |
| Knowledge/RAG | `KnowledgeApiController` + kb-engine | KG检索在kb-engine已就绪 |

---

## 五、总结：一条清晰的边界

```
┌─────────────────────────────────────────────────┐
│              ECOS AI引擎（纯Java）                │
│                                                  │
│  配置层（已有，不需要动）                          │
│  ├─ Agent Builder (CRUD+tools+knowledge+prompt)  │
│  ├─ Agent Mesh (多Agent编排+Mission)             │
│  ├─ Guardrails (护栏策略)                        │
│  ├─ Skills / CronJobs / ActionBridge              │
│  └─ Knowledge / RAG / NLQ                        │
│                                                  │
│  运行时层（需要Java实现）                          │
│  ├─ Agent Loop ← 核心，3天                       │
│  ├─ Session/Memory ← 脱敏-hermes-engine，2天     │
│  ├─ Streaming SSE ← 1天                          │
│  └─ Delegation ← 2天                             │
│                                                  │
│  LLM层（已有，不需要动）                           │
│  └─ LLMGatewayService (DeepSeek/OpenAI)          │
└─────────────────────────────────────────────────┘

外部依赖：零。Hermes完全不需要。
LLM：DeepSeek API（政企可私有化部署DeepSeek）
存储：PG（ECOS原生）
```

**总工期**：8天（Agent Loop 3 + Session 2 + Streaming 1 + Delegation 2）。串行依赖：Session→Loop→Streaming→Delegation。

**跟之前"参考Claude Code"的本质区别**：Claude Code是一个完整的终端Agent产品（前端+后端+交互），你要搬的是整个用户体验。Hermes的Agent管理是**纯后端能力**——没有前端包袱，没有交互模式包袱，就是四个Java Service要写。这就是为什么我否了CC方案但认可这个方案。

**风险点**：Agent Loop的健壮性——工具调用超时、LLM返回格式错误、无限循环。Hermes在这上面踩了一年坑。建议先实现最小可行Loop（3轮上限+单工具调用），跑通后再加并发工具+动态工具选择。
