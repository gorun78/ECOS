# AI引擎 vs Hermes 差距分析与完善方案

> 肖总 / 2026-08-02 | 基线: ECOS ai-engine (55文件/8537行) vs Hermes Agent

---

## 一、总体评估

ECOS ai-engine的**配置管理层已相当完整**——Agent CRUD、多Agent编排、工具注册、技能、定时任务、护栏、Prompt管理全部就绪。差距集中在**运行时执行深度**和**生产级健壮性**。

```
ECOS已有（配置层）: ████████████████████░░░░  80%
ECOS缺失（执行层）: ████░░░░░░░░░░░░░░░░░░░░  20%
但缺失的20%决定了Agent体验的80%
```

---

## 二、差距清单（16项）

### P0 — 核心Agent体验断裂（4项，2周）

| # | 能力 | Hermes | ECOS现状 | 影响 |
|---|------|--------|---------|------|
| 1 | **结构化工具输出解析** | 标准function-calling JSON → 类型化ToolCall | 简单字符串匹配+格式错误重试，无schema校验 | LLM输出不稳定时Agent直接挂 |
| 2 | **Tool Schema** | 每个工具有JSON Schema定义参数类型/必填/默认值 | 工具只有name+Map<String,Object>参数 | LLM不知道参数约束，乱填参数名 |
| 3 | **Provider抽象** | 多Provider+fallback链 | `AgentLoopService`硬编码DeepSeek | 换模型要改代码 |
| 4 | **Token预算管理** | 动态裁剪历史消息+估算token数 | 无限制，长对话爆上下文窗口 | 超过16K token后Agent返回乱码或报错 |

### P1 — 生产就绪（5项，3周）

| # | 能力 | Hermes | ECOS现状 | 影响 |
|---|------|--------|---------|------|
| 5 | **Skill运行时加载** | 文件系统加载SKILL.md → 解析YAML frontmatter → 注入System Prompt | Skill CRUD存DB，但不加载不注入 | 技能定义好了但Agent不知道 |
| 6 | **Cron执行引擎** | 定时触发→创建Agent Session→执行→推送结果 | CronJob CRUD完整，但没有执行器 | 定时任务形同虚设 |
| 7 | **记忆注入** | 长期记忆每回合注入System Prompt | 只存当前会话消息，不注入持久记忆 | Agent没有"长期认识" |
| 8 | **文件系统工具** | read_file/write_file/search_files/patch | 无 | Agent无法操作知识库文件 |
| 9 | **Agent评估** | Eval harness跑标准基准测试 | 无 | 无法量化Agent质量 |

### P2 — 增强能力（7项，后续迭代）

| # | 能力 | Hermes | 说明 |
|---|------|--------|------|
| 10 | Web Search | web_search工具 | 政企环境可能不需要外网 |
| 11 | Code Execution | execute_code沙箱 | 安全敏感，需隔离执行 |
| 12 | Browser Automation | browser_navigate/click等 | 复杂交互场景 |
| 13 | 多级委托 | orchestrator→leaf委托 | AgentMesh已有多Agent编排基础 |
| 14 | 插件系统 | Plugin扩展机制 | 生态建设 |
| 15 | 会话线程 | Topic threading | 多话题并行对话 |
| 16 | 图片/语音 | Vision + TTS | 多模态 |

---

## 三、P0详细方案（4项，2周）

### P0-1: 结构化工具输出解析 + Tool Schema（3天）

**当前问题**: `AgentLoopService` 把LLM返回的工具调用当纯文本解析，格式稍有偏差就丢"格式错误"重试，浪费一轮。

**方案**: 

1. **Tool Schema定义** — 每个Tool注册时带JSON Schema：
```java
public class ToolSchema {
    String name;
    String description;
    Map<String, ParamDef> parameters;  // name → {type, required, default, description}
}
```

2. **LLM调用时注入Schema** — 把可用工具序列化为OpenAI function-calling格式，要求LLM按标准格式返回：
```json
{"tool_calls": [{"id": "call_1", "function": {"name": "query_db", "arguments": "{\"sql\": \"...\"}"}}]}
```

3. **结构化解析** — 用`ObjectMapper`反序列化到`ToolCall`对象，带schema校验：
```java
public ToolCall parseToolCall(String llmOutput, List<ToolSchema> schemas) {
    // 1. 解析JSON → ToolCall
    // 2. 按schema校验必填参数
    // 3. 参数默认值填充
    // 4. 类型转换（string→int等）
}
```

**改文件**: `AgentLoopService.java`（改造callLLM方法）、新增`ToolSchema.java`

---

### P0-2: Provider抽象（2天）

**当前问题**: `AgentLoopService` 里 `new ChatRequest(model, messages, ...)` 硬编码DeepSeek。

**方案**: 抽象 `LLMProvider` 接口：
```java
public interface LLMProvider {
    ChatResponse chat(ChatRequest request);
    String getName();
    boolean supportsFunctionCalling();
}
```

实现 `DeepSeekProvider`、`OpenAIProvider`，Gateway yml配置：
```yaml
llm:
  providers:
    deepseek:
      api-key: ${DEEPSEEK_API_KEY:}
      model: deepseek-chat
      base-url: https://api.deepseek.com
    openai:
      api-key: ${OPENAI_API_KEY:}
      model: gpt-4o
  default: deepseek
  fallback-chain: [deepseek, openai]
```

**改文件**: 新增`LLMProvider.java`、`DeepSeekProvider.java`，改造`AgentLoopService.java`

---

### P0-3: Token预算管理（2天）

**当前问题**: AgentLoopService传全部历史消息给LLM，无截断。

**方案**: 

1. **Token估算器** — 简单估算（中文1字≈1.5token，英文1词≈1.3token）
2. **对话裁剪策略** — 近→远保留消息，总额不超过maxTokens-1000（留回复空间）
3. **AgentLoopConfig新增maxContextTokens参数**，默认8000

```java
public List<Message> trimHistory(List<Message> history, int maxTokens) {
    int total = 0;
    List<Message> trimmed = new ArrayList<>();
    for (int i = history.size() - 1; i >= 0; i--) {
        int msgTokens = estimateTokens(history.get(i));
        if (total + msgTokens > maxTokens) break;
        trimmed.add(0, history.get(i));  // 从近到远，首部插入
        total += msgTokens;
    }
    return trimmed;
}
```

**改文件**: 改造`AgentLoopService.java`（trimHistory方法），`AgentLoopConfig.java`新增字段

---

### P0-4: 工具注册中心（含P0-1的Tool Schema，1天）

**当前问题**: 工具分散在ToolExecutorService的switch-case和AgentDelegationService中。

**方案**: `ToolRegistry`统一管理：
```java
@Component
public class ToolRegistry {
    private final Map<String, ToolSchema> tools = new ConcurrentHashMap<>();
    
    public void register(ToolSchema tool, Function<Map<String,Object>, ToolResult> executor) { ... }
    public ToolSchema get(String name) { ... }
    public List<ToolSchema> listAll() { ... }
    public ToolResult execute(String name, Map<String,Object> args) { ... }
}
```

**改文件**: 新增`ToolRegistry.java`，改造`ToolExecutorService.java`

---

## 四、P1详细方案（5项，3周）

### P1-1: Skill运行时加载（3天）

加载DB中的Skill → 编译Markdown → 注入System Prompt

### P1-2: Cron执行引擎（3天）

基于AgentSessionService + AgentLoopService，定时触发生成Agent会话

### P1-3: 记忆注入（2天）

从sys_agent_session中提取"有价值的持久事实" → 注入System Prompt

### P1-4: 文件系统工具（2天）

read_file / write_file / search_files / patch 四个工具

### P1-5: Agent评估框架（3天）

标准问题集 + 批量评估 + 得分报告

---

## 五、执行优先级与工期

```
Week 1-2: P0（核心体验）— 4项，10天
  Day 1-3: P0-1 结构化解析 + Tool Schema
  Day 4-5: P0-2 Provider抽象
  Day 6-7: P0-3 Token管理
  Day 8:   P0-4 工具注册中心

Week 3-5: P1（生产就绪）— 5项，15天
  Day 1-3: P1-1 Skill运行时
  Day 4-6: P1-2 Cron执行引擎
  Day 7-8: P1-3 记忆注入
  Day 9-10: P1-4 文件工具
  Day 11-13: P1-5 Agent评估

P2: 后续迭代
```

---

## 六、一句话总结

**ECOS的AI引擎不缺骨架，缺肌肉。** 55个Java文件把配置管理做到了80分，但Agent Loop里的工具解析、Token控制、模型切换这些"执行肌肉"只有30分。P0四块补上后，Agent体验会有质变——不再出现"格式错误重试"和"上下文溢出乱码"。
