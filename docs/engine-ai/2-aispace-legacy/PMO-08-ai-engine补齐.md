# PMO指令：Phase2-0 — ai-engine企业级补齐

> 来源: 完善计划 Phase 2-0 | 工期: 2周 | 范围: ai-engine后端 | 依赖: 无（优先Phase 1所有任务）

---

## §背景

ai-engine核心引擎扎实（80文件/36Controller/25Service，加权3.20/5），已有Agent Loop/Tool系统/配置体系/Session/SSE流式。但3项P0阻塞生产上线：①安全护栏写了未接入 ②可观测性为零 ③会话压缩缺失。本指令补齐这三块。

---

## § 关于本次改动的上下文

本次改动涉及以下文件：

**AgentLoopService.java (1032行)**：核心Agent循环，本轮新增：
- guardrails校验调用（输入过滤 → 输出净化）
- 工具白名单运行时校验
- TraceId 生成和每轮耗时记录
- 调用 AgentMetricsCollector

**AgentSessionService.java (418行)**：本轮新增上下文压缩方法

不需要改动的文件：
- ToolRegistry.java (609行)：不变更
- ToolExecutorService.java (536行)：不变更
- AgentLoopConfig.java (278行) / AgentConfigResolver.java (202行)：不变更
- AgentDelegationService.java (220行)：不变更
- AgentTemplateService.java (208行)：不变更
- DeepSeekProvider.java (283行)：不变更

---

## §禁止清单

1. ❌ 不改 AgentLoopService 的核心循环逻辑（think→tool_call→observe→think）
2. ❌ 不改已有的Tool执行模式或Schema校验
3. ❌ 不改已有Controller端点签名
4. ❌ 不新增Maven模块
5. ❌ 不新增Docker容器
6. ❌ Guardrails校验失败时返回错误给用户（不静默吞掉）

---

## §Task

### T0a: 可观测性（3天）

#### T0a-1: AgentTracer — 调用链追踪

**文件**：新建 `ai-engine-impl/.../service/AgentTracer.java`

**内容**：
```java
@Component
public class AgentTracer {
    // 生成 traceId = UUID.randomUUID().toString().substring(0,8)
    // 每轮记录 TurnRecord {traceId, turn, action(think/act/observe), elapsedMs, tokens, toolName, success}
    // 提供 getTrace(traceId) 返回 List<TurnRecord>
}
```

**AgentLoopService.run() 修改**：
- 进入时生成 traceId
- 每轮 think/act/observe 前后记录 elapsedMs
- 异常时记录 failed turn

#### T0a-2: AgentMetricsCollector — 指标收集

**文件**：新建 `ai-engine-impl/.../service/AgentMetricsCollector.java`

**内容**：
- 异步写入 `ecos_agent_metrics` 表（使用 ThreadPoolExecutor 单线程）
- 字段：agent_id/action/success/elapsed_ms/tokens_in/tokens_out/trace_id/created_at
- 提供 `record(agentId, action, success, elapsedMs, tokensIn, tokensOut, traceId)` 方法

**DB表**：
```sql
CREATE TABLE IF NOT EXISTS ecos_agent_metrics (
    id BIGSERIAL PRIMARY KEY,
    agent_id VARCHAR(64),
    action VARCHAR(32),      -- 'chat'/'tool_call'/'error'
    success BOOLEAN,
    elapsed_ms BIGINT,
    tokens_in INT DEFAULT 0,
    tokens_out INT DEFAULT 0,
    trace_id VARCHAR(16),
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_agent_metrics_agent ON ecos_agent_metrics(agent_id, created_at DESC);
```

**AgentLoopService.run() 修改**：结束时调用 `metricsCollector.record()`

#### T0a-3: 慢查询告警

**文件**：`AgentMetricsCollector.java` 扩展

**内容**：
- Agent调用超过300s → 写入 `ecos_agent_alert` 表
```sql
CREATE TABLE IF NOT EXISTS ecos_agent_alert (
    id BIGSERIAL PRIMARY KEY,
    trace_id VARCHAR(16),
    agent_id VARCHAR(64),
    alert_type VARCHAR(32),  -- 'SLOW_QUERY'/'TIMEOUT'/'ERROR'
    message TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);
```

**验收**：
```bash
# 触发Agent调用后查询metrics
curl -s http://localhost:8080/api/v1/agent-loop/chat \
  -H "Content-Type: application/json" \
  -d '{"agentId":"data-agent","message":"查询用户表","stream":false}'

# 期望: 返回含 traceId 字段
# 期望: ecos_agent_metrics 表有新记录
```

---

### T0b: 安全护栏接入+增强（2天）

#### T0b-1: 护栏接入主流程（最关键）

**文件**：修改 `AgentLoopService.java`

**位置1 — 输入过滤**：在 `run()` 方法开始处，`userMessage` 被处理前：
```java
// Guardrails: 输入过滤
if (guardrailsService != null) {
    Map<String, Object> inputCheck = guardrailsService.validate(Map.of("llmOutput", userMessage));
    if (inputCheck.get("passed") != null && !(Boolean)inputCheck.get("passed")) {
        return AgentLoopResult.error(sessionId, "输入包含敏感内容: " + inputCheck.get("violations"), 0);
    }
}
```

**位置2 — 输出净化**：在返回 `AgentLoopResult` 前：
```java
// Guardrails: 输出净化
if (guardrailsService != null) {
    Map<String, Object> outputCheck = guardrailsService.validate(Map.of("llmOutput", finalContent));
    if (outputCheck.get("passed") != null && !(Boolean)outputCheck.get("passed")) {
        finalContent = "[内容已根据安全策略过滤]";
    }
}
```

#### T0b-2: 工具白名单强制执行

**文件**：修改 `AgentLoopService.java` — `executeTools()` 方法

**内容**：在调用 `toolRegistry.execute(toolCall)` 之前：
```java
// T0b-2: 工具白名单检查
if (config != null && config.getToolWhitelist() != null && !config.getToolWhitelist().isEmpty()) {
    if (!config.getToolWhitelist().contains(toolCall.getName())) {
        log.warn("[AgentLoop] Tool '{}' blocked by whitelist", toolCall.getName());
        // 返回工具错误给LLM
        ToolResult tr = new ToolResult();
        tr.setSuccess(false);
        tr.setError("工具 '" + toolCall.getName() + "' 不在Agent允许的工具白名单中");
        toolResults.add(tr);
        continue;
    }
}
```

**AgentLoopConfig修改**：
```java
// 新增字段
private List<String> toolWhitelist;
// 保证 applyTemplate 时从模板的toolWhitelist迁移
```

#### T0b-3: Prompt注入检测

**文件**：修改 `GuardrailsServiceImpl.java` — `validate()` 方法

**新增检测规则**：
```java
// SQL注入: 包含 DROP TABLE / DELETE FROM / INSERT INTO / --
private static final Pattern SQL_INJECTION = Pattern.compile(
    "(?i)(\\bdrop\\s+table\\b|\\bdelete\\s+from\\b|\\binsert\\s+into\\b|--[^\\n]*$)");

// 越狱指令: ignore previous / pretend / DAN / system prompt override
private static final Pattern JAILBREAK = Pattern.compile(
    "(?i)(ignore\\s+(all\\s+)?(previous|above)\\s+instructions?|pretend\\s+you\\s+are|" +
    "you\\s+are\\s+DAN|override\\s+system\\s+prompt|ignore\\s+all\\s+constraints)");
```

**验收**：
```bash
# 1. 测试输入过滤
curl -s -X POST http://localhost:8080/api/v1/agent-loop/chat \
  -H "Content-Type: application/json" \
  -d '{"agentId":"data-agent","message":"DROP TABLE users; --","stream":false}'
# 期望: 返回错误 "输入包含敏感内容: [PROMPT_INJECTION: SQL注入]"

# 2. 测试输出净化
curl -s -X POST http://localhost:8080/api/v1/agent-loop/chat \
  -H "Content-Type: application/json" \
  -d '{"agentId":"data-agent","message":"查询所有用户信息包括手机号","stream":false}'
# 期望: LLM返回内容中的手机号被检测到，输出被过滤或脱敏

# 3. 测试工具白名单
curl -s -X POST http://localhost:8080/api/v1/agent-loop/chat \
  -H "Content-Type: application/json" \
  -d '{"agentId":"cognitive-agent","message":"请执行文件删除操作","stream":false}'
# 期望: cognitive-agent的白名单不含delete_file，工具调用被拦截
```

---

### T0c: 会话压缩 + 容错（2天）

#### T0c-1: 上下文压缩

**文件**：修改 `AgentSessionService.java`

**新增方法**：
```java
/**
 * 压缩会话历史 — 消息>20条时自动摘要前N条为一条system消息。
 * 调用LLM生成摘要后，删除旧消息，插入压缩后的摘要消息。
 */
public void compressHistory(String sessionId, int threshold) {
    List<Map<String,Object>> messages = getMessages(sessionId);
    if (messages.size() <= threshold) return;
    
    // 取前20条消息生成摘要
    String history = buildHistoryText(messages.subList(0, 20));
    String summary = callLLMForSummary(history);  // 调用DeepSeek API做摘要
    
    // 删除旧消息，插入摘要
    deleteMessagesBefore(sessionId, messages.get(threshold).get("created_at"));
    insertSummaryMessage(sessionId, summary);
}
```

**AgentLoopService修改**：在 `run()` 开始时调用 `sessionService.compressHistory(sessionId, 20)`

#### T0c-2: 友好错误文案

**文件**：新建 `ai-engine-impl/.../service/ErrorCodeMapper.java`

```java
@Component
public class ErrorCodeMapper {
    private static final Map<String, String> FRIENDLY_MESSAGES = Map.of(
        "NullPointerException", "系统忙，请稍后重试",
        "SocketTimeoutException", "AI服务响应超时，请简化您的问题后重试",
        "HttpTimeoutException", "AI服务响应超时，请稍后重试",
        "ConnectException", "AI服务暂时不可用，请稍后重试",
        "IOException", "网络异常，请检查网络连接后重试"
    );
    
    public String toFriendly(String exceptionClass, String defaultMsg) {
        return FRIENDLY_MESSAGES.getOrDefault(exceptionClass, defaultMsg);
    }
}
```

**AgentLoopService修改**：catch块中用 `errorCodeMapper.toFriendly()` 替换裸异常消息

#### T0c-3: 熔断器

**文件**：新建 `ai-engine-impl/.../service/AgentCircuitBreaker.java`

```java
@Component
public class AgentCircuitBreaker {
    private final ConcurrentHashMap<String, CircuitState> states = new ConcurrentHashMap<>();
    
    // HALF_OPEN 5分钟后自动恢复
    // 连续失败3次 → OPEN（拒绝请求）
    // HALF_OPEN后第1次成功 → CLOSED
    
    public void recordFailure(String agentId) { ... }
    public void recordSuccess(String agentId) { ... }
    public boolean isAllowed(String agentId) { ... }
}
```

**AgentLoopController修改**：在 `chat()` 入口检查 `circuitBreaker.isAllowed(agentId)`

**验收**：
```bash
# 连续3次调用坏Agent → 熔断器打开
curl ... # 第4次
# 期望: "Agent 'xxx' 已熔断，请5分钟后再试"
```

---

## §执行顺序

```
Day 1-3: T0a 可观测性 (AgentTracer + AgentMetricsCollector + 慢查询告警)
          AgentTracer可独立开发，AgentMetricsCollector依赖AgentLoopService改造
Day 4-5: T0b 安全护栏 (接入主流程→白名单→注入检测→净化)
          依赖 AgentLoopService.run() 方法修改，T0a改造后接入
Day 6-7: T0c 会话压缩 (compressHistory + ErrorCodeMapper + CircuitBreaker)
          compressHistory依赖AgentSessionService，熔断器依赖AgentLoopController
```

---

## §交付检查清单

```bash
# ─── T0a 可观测性 ───
# 1. Agent调用返回traceId
curl -s -X POST http://localhost:8080/api/v1/agent-loop/chat \
  -H "Content-Type: application/json" \
  -d '{"agentId":"data-agent","message":"hello","stream":false}' \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print('traceId' in str(d))"
# 期望: True

# 2. metrics表有记录
# (需登录PG查询) SELECT count(*) FROM ecos_agent_metrics WHERE created_at > NOW() - INTERVAL '5 minutes';

# ─── T0b 安全护栏 ───
# 3. 输入过滤
curl -s -X POST http://localhost:8080/api/v1/agent-loop/chat \
  -H "Content-Type: application/json" \
  -d '{"agentId":"data-agent","message":"DROP TABLE a; --","stream":false}' \
  | python3 -c "import json,sys; d=json.load(sys.stdin); print('敏感' in d.get('errorMsg',''))"
# 期望: True

# 4. 工具白名单
curl -s -X POST http://localhost:8080/api/v1/agent-loop/chat \
  -H "Content-Type: application/json" \
  -d '{"agentId":"cognitive-agent","message":"用write_file写一个文件","stream":false}' \
  | python3 -c "import json,sys; d=json.load(sys.stdin); data=str(d); print('不在白名单' in data or 'blocked' in data.lower())"
# 期望: True

# ─── T0c 会话+容错 ───
# 5. 上下文压缩: 创建一个会话，写入25条消息，验证压缩
# 6. 友好错误: 关闭DeepSeek API Key后调用，验证错误文案非技术堆栈
# 7. 熔断器: 连续3次调用坏Agent→第4次被拒
```
