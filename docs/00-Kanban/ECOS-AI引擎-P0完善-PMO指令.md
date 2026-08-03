# PMO指令：AI引擎运行时增强——P0核心体验补全

> **引擎契约**: `engine/ai-engine/AGENTS.md` | **工期**: 2周 | **铁律见§禁止清单**

---

## §零 背景

ECOS ai-engine的配置管理层已完整（Agent CRUD、多Agent编排、工具注册、技能、定时任务、护栏），但Agent Loop运行时存在四个致命短板：LLM工具调用解析脆弱、硬编码DeepSeek、无Token预算、工具无Schema定义。本指令补全这四块。

**所有代码仅限 `engine/ai-engine/ai-engine-impl`，不改其他模块。**

---

## §禁止清单

1. ❌ 不新增Maven模块
2. ❌ 不引入非Java依赖（用JDK自带JSON+HTTP）
3. ❌ 不改LLMGatewayService接口
4. ❌ 不改AgentLoopService公开方法签名（`run(config, userMessage, session)`不变）
5. ❌ 不碰其他引擎的文件
6. ❌ Agent Loop上限保持5轮限制

---

## §P0 Task

### T1: ToolSchema + ToolRegistry（2天）

**目标**：每个工具有JSON Schema定义参数类型/必填/默认值，ToolRegistry统一管理和执行。

**改文件**：
- 新增 `service/ToolSchema.java` — 工具参数定义
- 新增 `service/ToolRegistry.java` — 工具注册中心
- 改造 `service/ToolExecutorService.java` — 改为从ToolRegistry查找执行器

**ToolSchema.java 关键结构**：
```java
public class ToolSchema {
    private String name;
    private String description;
    private Map<String, ParamDef> parameters;  // 参数名 → {type, required, default, description}

    public static class ParamDef {
        private String type;       // "string" | "number" | "boolean" | "object" | "array"
        private boolean required;  // 是否必填
        private Object defaultValue;
        private String description;
    }

    /** 生成OpenAI function-calling兼容的JSON Schema */
    public Map<String, Object> toFunctionCallSchema() { ... }
}
```

**ToolRegistry.java 关键方法**：
```java
@Component
public class ToolRegistry {
    private final Map<String, ToolSchema> schemas = new ConcurrentHashMap<>();
    private final Map<String, Function<Map<String,Object>, ToolResult>> executors = new ConcurrentHashMap<>();

    public void register(ToolSchema schema, Function<Map<String,Object>, ToolResult> executor) { ... }
    public ToolSchema get(String name) { ... }
    public List<ToolSchema> listAll() { ... }
    public ToolResult execute(String name, Map<String,Object> args) { ... }
}
```

**验收**：
```bash
# V1: 编译通过
cd engine/ai-engine && mvn install -pl ai-engine-boot -am -DskipTests -q

# V2: 启动boot后注册工具查询
curl -s http://localhost:18084/api/v1/agent/tools | python3 -c "
import json,sys; d=json.load(sys.stdin)['data']
assert len(d) >= 3, '至少3个内置工具'
for t in d: print(f'{t[\"name\"]}: params={len(t.get(\"parameters\",{}))}')
"

# V3: 工具参数schema校验
curl -s -X POST http://localhost:18084/api/v1/agent/tools/query_db/validate \
  -H "Content-Type: application/json" \
  -d '{"arguments":{"sql":"SELECT 1"}}'  # 应pass
curl -s -X POST http://localhost:18084/api/v1/agent/tools/query_db/validate \
  -H "Content-Type: application/json" \
  -d '{"arguments":{}}'  # 应fail（sql必填）
```

### T2: Provider抽象（2天）

**当前问题**：`AgentLoopService.callLLM()`中 `new ChatRequest(model, messages, ...)` 硬编码DeepSeek，无切换能力，无fallback。

**改文件**：
- 新增 `service/LLMProvider.java` — Provider接口
- 新增 `service/DeepSeekProvider.java` — DeepSeek实现
- 新增 `service/OpenAIProvider.java` — OpenAI实现（可选）
- 改造 `service/AgentLoopService.java` — 注入List<LLMProvider>，按配置选Provider

**LLMProvider接口**：
```java
public interface LLMProvider {
    ChatResponse chat(ChatRequest request);
    String getName();
    boolean supportsFunctionCalling();
    default int priority() { return 100; }  // 数字越小优先级越高
}
```

**配置**（gateway/application.yml，不改引擎boot yml）：
```yaml
llm:
  deepseek:
    api-key: ${DEEPSEEK_API_KEY:}
    base-url: https://api.deepseek.com
    model: deepseek-chat
  providers:
    - deepseek
  fallback-chain: []  # P0阶段不配fallback，留P1
```

**AgentLoopService改造**：`callLLM()`从直接调`llmGateway`改为遍历Provider列表，找到第一个支持function-calling的Provider执行。

**验收**：
```bash
# V1: 编译通过
cd engine/ai-engine && mvn install -pl ai-engine-boot -am -DskipTests -q

# V2: 启动后检查Provider列表
curl -s http://localhost:18084/api/v1/agent/providers | python3 -c "
import json,sys; d=json.load(sys.stdin)['data']
assert len(d) >= 1, '至少1个Provider'
print(f'Provider: {d[0][\"name\"]} functionCalling={d[0][\"supportsFunctionCalling\"]}')
"

# V3: Agent对话仍正常工作
TOKEN=$(curl -s http://localhost:18084/api/v1/auth/login -X POST -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}' | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['accessToken'])")
SID=$(curl -s -X POST http://localhost:18084/api/v1/agent-loop/sessions -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"agentId":"diagnostic-agent"}' | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['id'])")
curl -s --max-time 60 -X POST "http://localhost:18084/api/v1/agent-loop/sessions/$SID/chat" -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"message":"你好","stream":false}' | python3 -c "import json,sys; d=json.load(sys.stdin)['data']; assert d['success'], d.get('errorMsg','unknown error'); print('CHAT OK')"
```

### T3: Token预算管理（2天）

**目标**：对话历史超过Token上限时自动裁剪，避免上下文溢出导致Agent乱码或报错。

**改文件**：
- 改造 `service/AgentLoopService.java` — 新增`trimHistory()`方法
- 改造 `service/AgentLoopConfig.java` — 新增`maxContextTokens`字段（默认8000）
- 新增 `service/TokenEstimator.java` — Token估算器

**TokenEstimator.java**：
```java
public class TokenEstimator {
    /** 估算一段文本的token数（简单算法：中文1字≈1.5token，英文1词≈1.3token） */
    public static int estimate(String text) { ... }
    /** 估算一条Message的token数（role + content） */
    public static int estimate(Message msg) { ... }
}
```

**trimHistory逻辑**：
```java
private List<Message> trimHistory(List<Message> history, int maxTokens) {
    // 从近→远保留消息（System Message始终保留在最前）
    // 总额不超过 maxTokens - 500（留回复空间）
    List<Message> systemMsgs = history.stream().filter(m -> m.getRole().equals("system")).toList();
    List<Message> rest = history.stream().filter(m -> !m.getRole().equals("system")).toList();
    
    int budget = maxTokens - 500 - systemMsgs.stream().mapToInt(TokenEstimator::estimate).sum();
    List<Message> trimmed = new ArrayList<>();
    int used = 0;
    for (int i = rest.size() - 1; i >= 0; i--) {
        int tokens = TokenEstimator.estimate(rest.get(i));
        if (used + tokens > budget) break;
        trimmed.add(0, rest.get(i));
        used += tokens;
    }
    
    List<Message> result = new ArrayList<>(systemMsgs);
    result.addAll(trimmed);
    return result;
}
```

**验收**：
```bash
# V1: 编译通过
cd engine/ai-engine && mvn install -pl ai-engine-boot -am -DskipTests -q

# V2: Token估算器单元测试
# 写一个简单main验证：100字中文 ≈ 150 tokens, 50 words英文 ≈ 65 tokens
# 验证trimHistory不超maxContextTokens

# V3: 长对话不报错
# 连续发10轮"请重复：你好"消息，验证消息数被裁剪到合理范围
```

### T4: 结构化工具输出解析（2天，依赖T1+T2）

**目标**：AgentLoopService中LLM的工具调用从"字符串匹配"改为"结构化JSON解析+Schema校验"。

**改文件**：
- 改造 `service/AgentLoopService.java` — `parseToolCalls()`方法重写

**改造前**（当前）：
```java
// 简单字符串匹配，格式稍有偏差就丢"格式错误"重试
String response = llmResponse.getContent();
// 手工找 "tool_call" 关键字 → 正则提取 → 容易失败
```

**改造后**：
```java
/**
 * 解析LLM返回的工具调用（OpenAI function-calling格式）
 * LLM返回 → JSON解析 → ToolCall列表
 */
private List<ToolCall> parseToolCalls(ChatResponse llmResponse) {
    // 1. 尝试直接JSON解析 tool_calls 字段
    // 2. 如果失败，尝试从 content 中提取JSON代码块 ```json ... ```
    // 3. 如果仍失败，正则匹配 function_name(arg1=val1, arg2=val2) 模式
    // 4. 全部失败 → 不是工具调用，是普通文本回复
    
    // 对每个解析出的ToolCall：
    // - 查ToolRegistry获取ToolSchema
    // - Schema校验：必填参数检查 + 类型检查 + 默认值填充
    // - 校验失败的参数 → 标记为invalidParams，让LLM在下一轮修正
}
```

**验收**：
```bash
# V1: 编译通过
cd engine/ai-engine && mvn install -pl ai-engine-boot -am -DskipTests -q

# V2: 工具调用正常解析
# 发一条需要工具的消息（如"查询所有数据源"），验证：
#   - Agent Loop返回的toolCalls不为空
#   - 工具被执行并返回结果
#   - 最终回复包含工具执行结果

# V3: 错误格式容忍
# 模拟LLM返回格式偏差（如arguments多了个逗号、参数名拼写错误），
# 验证Agent不会直接崩溃，而是返回"参数校验失败"让LLM修正
```

---

## §执行顺序

```
Week 1:
  Day 1-2: T1（ToolSchema+ToolRegistry） —— 无依赖，先做
  Day 3-4: T2（Provider抽象）            —— 依赖T1（ToolRegistry就绪）
  Day 5:   T1+T2集成测试
  
Week 2:
  Day 6-7: T3（Token管理）               —— 独立任务，可与T4并行
  Day 6-7: T4（结构化解析）[依赖T1+T2]    —— 与T3并行
  Day 8-9: T3+T4联调 + pre-check
  Day 10:  全量编译 + Gateway集成测试
```

---

## §交付检查清单

```bash
# ─── 1. 全量编译 ───
cd ~/ECOS/ecos_backend
mvn install -pl gateway -am -DskipTests -q && echo "BUILD PASS"

# ─── 2. 新增文件检查 ───
ls engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/service/ToolSchema.java
ls engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/service/ToolRegistry.java
ls engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/service/LLMProvider.java
ls engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/service/DeepSeekProvider.java
ls engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/service/TokenEstimator.java

# ─── 3. 禁止清单检查 ───
# 无新增Maven模块
# 无新增外部依赖到pom.xml
# AgentLoopService.run() 方法签名未变
# Agent Loop上限5轮未变

# ─── 4. 功能检查 ───
# Tool Registry至少3个内置工具（query_db/invoke_rest/delegate_to_agent）
# Provider配置加载正常，DeepSeekProvider可用
# Token修剪后消息不超过8000 token
# Agent Loop工具调用解析成功率≥90%（标准function-calling格式）
```

---

## §一句话总结

**"Agent现在能对话了，但LLM说'我要调query_db'时，解析靠猜、Token没管、模型绑死。四块补上后：工具调用结构化、模型可切换、长对话不爆、参数有校验。"**
