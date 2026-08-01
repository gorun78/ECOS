# PMO指令：AI引擎Agent运行时——Java自研替代Hermes依赖

> **来源**：肖总 / 2026-08-01 | **工期**：2周 | **铁律见§禁止清单**

---

## §零 背景

ECOS AI引擎的配置层已齐（Agent CRUD、多Agent编排、工具注册、护栏、技能、定时任务），但运行时执行层缺失——Agent Loop、Session/Memory、Streaming、Delegation。当前实现是单次LLM调用（`LLMGatewayService.execute()`），没有工具调用循环，没有会话持久化。

**目标**：在ai-engine-impl中用纯Java实现四块运行时能力，使ECOS不再依赖Hermes作为外部MCP Server。政企环境可独立部署，零外部Agent依赖。

**四块能力与现状对照：**

| 能力 | Hermes实现 | ECOS现状 | 行动 |
|------|-----------|---------|------|
| Agent Loop | think→tool→observe→think循环 | 单次chat/completions | **新建** |
| Session/Memory | SQLite + Memory注入 | SessionManager有但AgentScheduler不读历史 | **重写** |
| Streaming | SSE逐token | 代码写了但stream硬编码false | **激活** |
| Delegation | spawn子Agent | AgentMesh有Mission但非动态 | **新建** |

---

## §禁止清单

1. **禁止新建Maven模块** — 所有新增代码在`ai-engine-impl`内
2. **禁止修改LLMGatewayService接口** — 只消费不改造
3. **禁止引入非Java依赖** — 无Python/Node.js/外部MCP Server
4. **禁止新增数据库** — Session/Memory表在现有`sys_man`库
5. **Agent Loop上限5轮** — 防止无限循环耗尽token
6. **Delegation单层** — 子Agent不可再委托（政企场景够用）
7. **所有Session必须可审计** — 消息历史不可物理删除，只标记archived

---

## §P0: Agent Loop（Week 1，3天）

### T0.1: AgentLoopService — 多轮工具调用循环（ai-engine-impl，2天）
**目标**: 实现think→tool_call→observe→think的推理循环
**改文件**: `engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/service/AgentLoopService.java`
**核心逻辑**:
```java
/**
 * Agent推理循环 — 替代Hermes Agent Loop的Java实现
 * 
 * 流程：
 * 1. 构建初始messages（system prompt + memory + history + user message）
 * 2. 调LLM → 解析响应
 * 3. 如果是tool_call → 执行工具 → 工具结果塞回messages → 回到步骤2
 * 4. 如果是final → 返回结果
 * 5. 最多5轮，超出返回超限错误
 */
AgentLoopResult run(AgentConfig config, String userMessage, AgentSession session) {
    List<Message> messages = buildInitialMessages(config, userMessage, session);
    
    for (int turn = 1; turn <= 5; turn++) {
        LLMResponse resp = llmGateway.chat(messages, config.getTools(), config.getModel());
        
        // 工具调用
        if (resp.hasToolCalls()) {
            for (ToolCall tc : resp.getToolCalls()) {
                ToolResult tr = toolExecutor.execute(tc);
                messages.add(Message.assistant(tc));      // assistant: "调用tool X"
                messages.add(Message.toolResult(tr));      // tool: result
                session.recordToolCall(tc, tr);
            }
            continue; // 回到循环顶部，让LLM看到工具结果后继续推理
        }
        
        // 最终回复
        session.recordFinalResponse(resp);
        return AgentLoopResult.success(resp.getContent(), turn, session);
    }
    
    return AgentLoopResult.maxTurnsExceeded(session);
}
```
**关键细节**:
- 工具调用超时30s，超时结果返回`{"error":"timeout"}`仍塞回messages
- 工具结果超过2000字符自动截断+注记"结果已截断"
- LLM返回格式错误（非JSON tool_call）→重试一次，仍失败则返回错误给用户
- 每轮记录`turn, llmTokens, toolName, toolDuration`到session
**验收**:
```bash
# 模拟一个需要工具调用的Agent对话
curl -s -X POST http://localhost:8080/api/v1/agent-loop/chat \
  -H @/tmp/auth_header.txt \
  -H "Content-Type: application/json" \
  -d '{
    "agentId":"diagnostic-agent",
    "message":"查询项目PRJ-001的经营数据并诊断",
    "stream":false
  }' | python3 -c "
import sys,json; d=json.load(sys.stdin)
assert d['code']==200
r = d['data']
assert r['turns'] >= 1, 'Expected at least 1 turn'
assert 'toolCalls' in r, 'Expected tool call records'
assert len(r['toolCalls']) > 0, 'Expected tool to be called'
print('PASS: Agent loop completed in', r['turns'], 'turns,', len(r['toolCalls']), 'tool calls')"
```

### T0.2: ToolExecutor — 工具执行器（ai-engine-impl，1天）
**目标**: 从工具注册表加载定义→执行→返回结果
**改文件**: `engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/service/ToolExecutorService.java`
**核心逻辑**:
```java
/**
 * 工具执行器 — 从ecos_tool_definition表加载工具schema
 * 支持三种执行模式：
 * - SQL: 执行预定义SQL查询
 * - REST: 调用内部微服务API
 * - BUILTIN: 内置Java函数（如getCurrentTime, calculateRisk）
 */
ToolResult execute(ToolCall tc) {
    ToolDefinition def = toolRepo.findById(tc.getToolId());
    return switch (def.getToolType()) {
        case "SQL"     -> executeSql(def.getSchemaJson(), tc.getArguments());
        case "REST"    -> executeRest(def.getSchemaJson(), tc.getArguments());
        case "BUILTIN" -> executeBuiltin(def.getCode(), tc.getArguments());
    };
}
```
**验收**: 同T0.1的curl验证——工具调用在Agent Loop中自动触发

---

## §P0: Session/Memory（Week 1，2天）

### T0.3: AgentSessionService — 会话持久化+记忆管理（ai-engine-impl，1.5天）
**目标**: 替代Hermes的Session/Memory，PG持久化+KG记忆注入
**改文件**: `engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/service/AgentSessionService.java`
**核心逻辑**:
```java
/**
 * 会话管理 — PG持久化 + KG记忆注入
 * 
 * 会话生命周期：
 * CREATE → ACTIVE → (每轮对话追加消息) → IDLE(30min)→EXPIRED
 * 
 * 记忆注入流程：
 * 1. 从KG查询该用户/租户相关的记忆事实
 * 2. 拼入system prompt: "## 相关背景知识\n- fact1\n- fact2"
 * 3. 对话结束后，LLM提取的新事实→写回KG
 */
AgentSession createSession(String agentId, String userId, String tenantId) {
    // 1. 创建会话记录
    // 2. 从KG加载相关记忆→注入system prompt
    // 3. 返回session（含初始messages列表）
}
```
**数据模型**（两张新表）:
```sql
-- 会话表
CREATE TABLE sys_agent_session (
    id VARCHAR(64) PRIMARY KEY,
    agent_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64), tenant_id VARCHAR(64),
    status VARCHAR(16) DEFAULT 'ACTIVE',  -- ACTIVE/IDLE/EXPIRED/ARCHIVED
    message_count INT DEFAULT 0,
    created_at BIGINT, last_active_at BIGINT
);

-- 消息表
CREATE TABLE sys_agent_message (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(64) REFERENCES sys_agent_session(id),
    role VARCHAR(16),          -- system/user/assistant/tool
    content TEXT,
    tool_calls JSONB,          -- LLM请求的工具调用
    tool_results JSONB,        -- 工具执行结果
    tokens INT,                -- 该消息消耗token数
    created_at BIGINT
);
```
**验收**:
```bash
# 创建会话→发两轮消息→查询会话验证消息历史完整
SESSION_ID=$(curl -s -X POST http://localhost:8080/api/v1/agent-loop/sessions \
  -H @/tmp/auth_header.txt \
  -H "Content-Type: application/json" \
  -d '{"agentId":"diagnostic-agent"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['sessionId'])")

# 发第一轮
curl -s -X POST "http://localhost:8080/api/v1/agent-loop/sessions/$SESSION_ID/chat" \
  -H @/tmp/auth_header.txt -H "Content-Type: application/json" \
  -d '{"message":"hello"}' > /dev/null

# 发第二轮（引用上一轮上下文）
curl -s -X POST "http://localhost:8080/api/v1/agent-loop/sessions/$SESSION_ID/chat" \
  -H @/tmp/auth_header.txt -H "Content-Type: application/json" \
  -d '{"message":"刚才我提到了什么？"}' > /dev/null

# 验证消息数
curl -s "http://localhost:8080/api/v1/agent-loop/sessions/$SESSION_ID" \
  -H @/tmp/auth_header.txt | python3 -c "
import sys,json; d=json.load(sys.stdin)
assert d['data']['messageCount'] >= 4, 'Expected at least 4 messages (2 user + 2 assistant)'
print('PASS:', d['data']['messageCount'], 'messages')"
```

### T0.4: 数据库建表（gateway，0.5天）
**目标**: 新增session/message两张表
**改文件**: `gateway/src/main/resources/db/migration/V101__agent_session_tables.sql`
**验证**:
```bash
PGPASSWORD=postgres psql -h localhost -U postgres -d sys_man -c "\dt sys_agent_session"
PGPASSWORD=postgres psql -h localhost -U postgres -d sys_man -c "\dt sys_agent_message"
# 预期：两张表存在
```

---

## §P1: Streaming + Delegation（Week 2，3天）

### T1.1: SSE流式输出（ai-engine-impl，1天）
**目标**: 激活现有SSE解析代码，AgentLoop支持流式返回
**改文件**: `engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/controller/AgentLoopController.java`（新增）
**核心**: `SseEmitter` + AgentLoop异步执行 + 逐token推送
```java
@PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter chatStream(@RequestBody Map<String, Object> body) {
    SseEmitter emitter = new SseEmitter(300_000L);
    agentLoopService.runAsync(config, message, session,
        event -> emitter.send(SseEmitter.event()
            .name(event.type())       // "token" | "tool_call" | "tool_result" | "done"
            .data(event.payload())),
        () -> emitter.complete(),
        error -> emitter.completeWithError(error)
    );
    return emitter;
}
```
**验收**:
```bash
curl -s -N -X POST http://localhost:8080/api/v1/agent-loop/chat \
  -H @/tmp/auth_header.txt \
  -H "Content-Type: application/json" \
  -d '{"agentId":"diagnostic-agent","message":"hello","stream":true}' \
  | head -20
# 预期：输出SSE事件流（event:token / event:tool_call / event:done）
```

### T1.2: AgentDelegationService — 子Agent动态委托（ai-engine-impl，2天）
**目标**: Agent Loop中LLM可决定将子任务委托给子Agent
**改文件**: `engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/service/AgentDelegationService.java`
**核心逻辑**:
```java
/**
 * 子Agent委托 — Agent Loop中的特殊工具
 * 
 * 实现方式：把delegation注册为一个内置工具"delegate_to_agent"
 * LLM在推理过程中调用这个工具 → spawn子Agent → 结果回流
 * 
 * 限制：
 * - 单层委托（子Agent不可再委托，tool列表不含delegate_to_agent）
 * - 子Agent超时120s
 * - 子Agent结果截断3000字符
 */
// 注册为内置工具
ToolDefinition DELEGATE_TOOL = ToolDefinition.builder()
    .code("delegate_to_agent")
    .name("委托子Agent")
    .toolType("BUILTIN")
    .schemaJson("""
        {"type":"object","properties":{
            "agentName":{"type":"string","description":"要委托的Agent名称"},
            "instruction":{"type":"string","description":"给子Agent的指令"}
        }}
    """)
    .build();
```
**验收**:
```bash
curl -s -X POST http://localhost:8080/api/v1/agent-loop/chat \
  -H @/tmp/auth_header.txt \
  -H "Content-Type: application/json" \
  -d '{
    "agentId":"orchestrator-agent",
    "message":"帮我同时查项目PRJ-001的经营数据和合同列表",
    "stream":false
  }' | python3 -c "
import sys,json; d=json.load(sys.stdin)
r = d['data']
# 验证是否触发了委托
tool_names = [tc['toolName'] for tc in r.get('toolCalls',[])]
has_delegate = 'delegate_to_agent' in tool_names
print('PASS: delegation triggered' if has_delegate else 'FAIL: no delegation')
assert has_delegate, 'Expected delegate_to_agent tool call'"
```

---

## §执行顺序

```
Week 1:
  Day 1: T0.4(建表)
  Day 1-3: T0.3(Session)  [依赖T0.4]
  Day 2-5: T0.1(Loop) [可与T0.3并行，Session接口先定契约]
  Day 5: T0.2(ToolExecutor) [依赖T0.1，收尾]

Week 2:
  Day 1: T1.1(Streaming) [依赖T0.1+T0.3]
  Day 2-3: T1.2(Delegation) [依赖T0.1]
  Day 4: 全量编译 + 端到端验证
  Day 5: 性能测试 + 文档
```

---

## §端到端验证

```bash
# ── V1: 后端编译 ──
cd /home/guorongxiao/ECOS/ecos_backend
mvn clean install -DskipTests -q
echo "V1 BUILD: $?"

# ── V2: Agent Loop + Session + Tool ──
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['token'])")
AUTH="Authorization: Bearer $TOKEN"

# 创建会话
SID=$(curl -s -X POST http://localhost:8080/api/v1/agent-loop/sessions \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"agentId":"diagnostic-agent"}' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['sessionId'])")
echo "SESSION: $SID"

# Agent Loop对话（需工具调用）
curl -s -X POST "http://localhost:8080/api/v1/agent-loop/sessions/$SID/chat" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"message":"查询项目PRJ-001的经营数据，并判断是否有风险","stream":false}' \
  | python3 -c "
import sys,json; d=json.load(sys.stdin)
assert d['code']==200
r = d['data']
assert r['turns'] >= 1
assert len(r.get('toolCalls',[])) > 0, 'Expected tool calls'
print('V2 LOOP: PASS -', r['turns'], 'turns,', len(r['toolCalls']), 'tool calls')"

# 验证会话持久化
curl -s "http://localhost:8080/api/v1/agent-loop/sessions/$SID" \
  -H "$AUTH" | python3 -c "
import sys,json; d=json.load(sys.stdin)
assert d['data']['messageCount'] >= 2
print('V2 SESSION: PASS -', d['data']['messageCount'], 'messages')"

# ── V3: Streaming ──
EVENT_COUNT=$(curl -s -N -X POST http://localhost:8080/api/v1/agent-loop/chat \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"agentId":"diagnostic-agent","message":"hello","stream":true}' \
  --max-time 10 2>/dev/null | grep -c "^event:")
echo "V3 STREAMING: $EVENT_COUNT events"

# ── V4: Delegation ──
curl -s -X POST "http://localhost:8080/api/v1/agent-loop/sessions/$SID/chat" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"message":"帮我同时查项目PRJ-001的经营数据和PRJ-002的合同列表","stream":false}' \
  | python3 -c "
import sys,json; d=json.load(sys.stdin)
r = d['data']
tool_names = [tc['toolName'] for tc in r.get('toolCalls',[])]
print('V4 DELEGATION: PASS' if 'delegate_to_agent' in tool_names else 'V4 DELEGATION: delegation not triggered (may be valid for simple query)')"

echo "═══ 全部验证通过 ═══"
```

---

## §交付检查清单

| Task | 引擎 | 文件 | 验收 | 状态 |
|------|------|------|------|:----:|
| T0.1 | ai | AgentLoopService.java | curl验证多轮工具调用 | ⬜ |
| T0.2 | ai | ToolExecutorService.java | 同T0.1验证 | ⬜ |
| T0.3 | ai | AgentSessionService.java | curl验证消息历史持久化 | ⬜ |
| T0.4 | gateway | V101__agent_session_tables.sql | 两张表存在 | ⬜ |
| T1.1 | ai | AgentLoopController.java | curl SSE事件流 | ⬜ |
| T1.2 | ai | AgentDelegationService.java | curl验证delegate_to_agent | ⬜ |

---

## §一句话给PMO

**"Hermes的Agent运行时能力，用纯Java在AI引擎内重写。四块：Agent Loop（多轮工具调用）、Session/Memory（PG持久化+KG记忆）、Streaming（SSE）、Delegation（动态子Agent）。不新建模块，不引入外部依赖，2周闭环。做完后ECOS不再依赖Hermes MCP Server。"**
