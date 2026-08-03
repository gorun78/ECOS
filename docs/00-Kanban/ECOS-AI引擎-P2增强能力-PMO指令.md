# PMO指令：AI引擎——P2内置Agent体系 + AI工作台协同

> **引擎契约**: `engine/ai-engine/AGENTS.md` | **工期**: 3周（15天） | **铁律见§禁止清单**
> **基线**: P0+P1全部完成

---

## §零 背景——为什么P2不是"补功能"，而是"建体系"

当前AI引擎有三个根本问题：

1. **没有内置Agent** — 6个引擎各自需要Agent来驱动自身智能化（data-agent查数据、ontology-agent管本体、cognitive-agent做诊断…），但Agent全是用内存Map手工创建的，重启丢失，没有模板
2. **AI工作台和后端脱节** — 前端ChatbotStudio/逻辑编排/多Agent协同三个面板，调用的是三套不同的后端API，没有统一的服务入口
3. **配置散落四处** — LLM参数在yml、Agent配置在内存Map的metadata JSON blob、工具白名单硬编码、前端UI偏好独立存——改一个Agent的system prompt要翻三个地方

本指令一次性解决这三个问题。

---

## §设计决策（三个核心概念）

### 概念一：内置Agent（Built-in Agent）

ECOS有6个引擎，每个引擎对应一个**内置Agent**。内置Agent是系统预设的、不可删除的，用户可基于内置Agent创建自己的实例。

| 内置Agent | 绑定引擎 | 默认工具白名单 | 默认System Prompt |
|-----------|---------|---------------|-------------------|
| **data-agent** | data-engine | query_db, list_tables, get_table_schema, get_lineage, run_dq_check | "你是数据管理专家。帮助用户查询数据库、分析数据质量、追溯数据血缘。" |
| **ontology-agent** | ontology-engine | list_domains, get_object_type, query_objects, get_links, get_functions, search | "你是本体建模专家。帮助用户管理业务对象、定义关系、设计Function计算属性。" |
| **cognitive-agent** | cognitive-engine | rag_search, kg_query, diagnose, scenario_analysis, get_goals, get_causal_chain | "你是经营诊断专家。分析经营数据、识别异常指标、追溯因果链、推演情景。" |
| **security-agent** | security-engine | check_permission, my_permissions, data_class | "你是安全合规专家。检查权限、审计访问、数据分类分级。" |
| **kb-agent** | kb-engine | search_files, read_file, write_file, patch, knowledge_extract | "你是知识管理专家。管理文档知识库、抽取实体关系、更新知识图谱。" |
| **ai-agent** | ai-engine | delegate_to_agent, 全部Agent元信息查询 | "你是AI编排器。理解用户意图，拆解复杂任务，分发给对应领域的专业Agent。" |

### 概念二：配置三层模型

```
L1 引擎全局配置 (application.yml)
  └─ LLM Provider / Model / Token上限 / 超时 / 温度
     ↓ 覆盖
L2 Agent模板 (内置JSON文件)
  └─ System Prompt / 工具白名单 / 推荐Model / maxIterations
     ↓ 覆盖
L3 Agent实例 (DB: ecos_agent_registry)
  └─ 用户可覆盖L2的任何配置 + 自定义参数
```

AgentLoopService查配置时：**先查L3 → 缺则查L2 → 缺则查L1**

### 概念三：AI工作台统一服务入口

前端AI工作台三个面板调用统一后端：

```
ChatbotStudio      ─→ AgentStudioService  ─→ AgentLoopService
逻辑编排(Pipeline)  ─→ PipelineService     ─→ AIPPipelineController
多Agent协同        ─→ AgentOrchestrator   ─→ AgentDelegationService
                         ↑
                    统一注入配置(L1+L2+L3)
```

---

## §禁止清单

1. ❌ 不新增Maven模块
2. ❌ Agent持久化用现有`ecos_agent_registry`表，不新建表
3. ❌ 内置Agent不可通过API删除（delete返回403）
4. ❌ 不引入工作流引擎框架（Pipeline用现有AIPPipelineController+异步执行）
5. ❌ 不改LLMGatewayService接口
6. ❌ 不改AgentLoopService.run()公开方法签名
7. ❌ 内置Agent的system prompt不超过500字

---

## §P2 Tasks

### T0: Agent持久化——内存Map → PG表（2天，基础设施，必须先做）

**当前**: `AIPAgentController`用`ConcurrentHashMap`存Agent，重启全丢。

**目标**: Agent CRUD走`AgentRegistryRepository`，读写`ecos_agent_registry`表。启动从DB加载。

**改文件**: `engine/ai-engine/ai-engine-impl/`
- 改造 `controller/AIPAgentController.java` — ConcurrentHashMap → AgentRegistryRepository
- `ecos_agent_registry`表已存在（runtime-core中有AgentRegistryEntity对应），直接用

**关键改动**:
```java
// 改前:
private final Map<String, Map<String, Object>> store = new ConcurrentHashMap<>();

// 改后:
private final AgentRegistryRepository agentRepo;

// create:
AgentRegistryEntity entity = new AgentRegistryEntity();
entity.setId(UUID.randomUUID().toString());
entity.setName(body.get("name"));
entity.setRole(body.get("role"));       // "builtin" | "user"
entity.setCapability(body.get("systemPrompt"));
entity.setStatus("active");
entity.setMetadata(toJson(body));       // 存完整配置JSON
agentRepo.insert(entity);
```

**ecos_agent_registry表结构确认** (已有):
```sql
-- 如果不存在则创建
CREATE TABLE IF NOT EXISTS ecos_agent_registry (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    role VARCHAR(32) DEFAULT 'user',
    capability TEXT,
    status VARCHAR(16) DEFAULT 'active',
    endpoint VARCHAR(256),
    metadata JSONB,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
```

**验收**:
```bash
# V1: 编译
cd ~/ECOS/ecos_backend && mvn compile -pl engine/ai-engine/ai-engine-impl -am -DskipTests -q && echo "BUILD PASS"

# V2: CRUD走DB
TOKEN=$(curl -s http://localhost:8080/api/v1/auth/login -X POST -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}' | python3 -c "import json,sys; print(json.load(sys.stdin)['data']['token'])")

# 创建
curl -s -X POST http://localhost:8080/api/v1/aip/agents \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"test-agent","role":"user","systemPrompt":"你是测试助手"}'
# 期望: 返回 id + name

# 重启后验证
# Gateway重启 → GET /api/v1/aip/agents → test-agent仍存在
```

---

### T1: 内置Agent体系（4天，依赖T0）

**目标**: 系统启动时自动创建6个内置Agent。内置Agent不可删除。用户可基于内置Agent创建实例（继承工具白名单和system prompt）。

**改文件**:
- 改造 `config/DataInitializer.java` — 增加内置Agent种子数据
- 新增 `service/AgentTemplateService.java` — 内置Agent模板定义 + 实例化
- 新增 `resources/agent-templates/` — 6个内置Agent的JSON模板文件
- 改造 `controller/AIPAgentController.java` — 删除时校验内置Agent

**内置Agent模板文件** (`resources/agent-templates/data-agent.json`):
```json
{
  "id": "builtin-data-agent",
  "name": "data-agent",
  "role": "builtin",
  "systemPrompt": "你是数据管理专家。帮助用户查询数据库、分析数据质量、追溯数据血缘。回答时始终标注数据来源（表名+行数）。不确定的SQL不要凭空生成，先查表结构。",
  "toolWhitelist": ["query_db", "list_tables", "get_table_schema", "get_lineage", "run_dq_check"],
  "model": "deepseek-chat",
  "maxIterations": 5,
  "temperature": 0.1,
  "icon": "database",
  "description": "数据查询与质量分析专家"
}
```

其他5个引擎同理（ontology-agent.json / cognitive-agent.json / security-agent.json / kb-agent.json / ai-agent.json）。

**AgentTemplateService**:
```java
@Component
public class AgentTemplateService {
    /**
     * 基于内置Agent模板创建用户实例。
     * 用户实例继承模板的 toolWhitelist + systemPrompt，
     * 用户可传 overrideParams 覆写任意字段。
     */
    public AgentRegistryEntity instantiate(String templateId, String userId, 
                                            Map<String, Object> overrideParams) {
        // 1. 加载模板JSON
        // 2. 生成新ID: "agent-{userId}-{templateId}-{uuid8}"
        // 3. role设为"user"，继承模板的toolWhitelist/systemPrompt
        // 4. overrideParams覆写
        // 5. 写入agentRepo
    }
}
```

**DataInitializer改造**:
```java
// seedBuiltinAgents(): 启动时检查6个内置Agent是否存在，不存在则创建
// 内置Agent的id固定: "builtin-data-agent", "builtin-ontology-agent", ...
```

**AIPAgentController.delete改造**:
```java
@DeleteMapping("/{id}")
public ApiResponse<Void> deleteAgent(@PathVariable String id) {
    AgentRegistryEntity agent = agentRepo.findById(id);
    if (agent == null) return ApiResponse.notFound("Agent不存在");
    if ("builtin".equals(agent.getRole())) {
        return ApiResponse.forbidden("内置Agent不可删除");
    }
    agentRepo.deleteById(id);
    return ApiResponse.success(null);
}
```

**验收**:
```bash
# V1: 编译通过
cd ~/ECOS/ecos_backend && mvn compile -pl engine/ai-engine/ai-engine-impl -am -DskipTests -q && echo "BUILD PASS"

# V2: 启动后6个内置Agent自动创建
curl -s http://localhost:8080/api/v1/aip/agents \
  -H "Authorization: Bearer $TOKEN" | python3 -c "
import json,sys; agents=json.load(sys.stdin)['data']
builtins = [a for a in agents if a.get('role')=='builtin']
assert len(builtins) == 6, f'内置Agent数量: {len(builtins)}'
for a in builtins: print(f'  {a[\"name\"]}: {a.get(\"description\",\"\")[:40]}')
print('6 BUILTIN AGENTS OK')
"

# V3: 内置Agent不可删除
curl -s -X DELETE "http://localhost:8080/api/v1/aip/agents/builtin-data-agent" \
  -H "Authorization: Bearer $TOKEN"
# 期望: 403 forbidden

# V4: 基于模板创建用户Agent
curl -s -X POST http://localhost:8080/api/v1/aip/agents/instantiate \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"templateId":"builtin-data-agent","override":{"name":"我的数据分析师","temperature":0.3}}'
# 期望: 返回新Agent，toolWhitelist继承自data-agent，temperature=0.3
```

---

### T2: 配置分层统一（3天，依赖T1）

**目标**: AI工作台的全部参数明确归属到L1/L2/L3三层，AgentLoopService运行时按L3→L2→L1顺序解析。

**改文件**:
- 改造 `service/AgentLoopConfig.java` — 支持三层覆盖
- 新增 `service/AgentConfigResolver.java` — 配置解析器
- 改造 `service/AgentLoopService.java` — 调用AgentConfigResolver替代直接读config
- 改造 `gateway/src/main/resources/application.yml` — L1全局默认值补全

**AgentConfigResolver核心逻辑**:
```java
@Component
public class AgentConfigResolver {
    /**
     * 解析Agent运行时配置：L3(DB实例) → L2(模板JSON) → L1(application.yml)
     */
    public AgentLoopConfig resolve(String agentId, Map<String, Object> requestOverrides) {
        // 1. 加载L1全局默认 (从application.yml)
        AgentLoopConfig config = AgentLoopConfig.fromGlobalDefaults(env);

        // 2. 加载L2模板 (从AgentRegistryEntity查role=builtin的模板)
        AgentRegistryEntity agent = agentRepo.findById(agentId);
        if (agent != null && agent.getMetadata() != null) {
            config.applyTemplate(agent);  // 覆写 systemPrompt, toolWhitelist, model...
        }

        // 3. 如果是用户Agent(role=user)，叠加L3实例配置
        if (agent != null && "user".equals(agent.getRole())) {
            config.applyInstance(agent);  // 用户自定义覆写
        }

        // 4. 运行时临时覆写（ChatbotStudio测试面板传入）
        if (requestOverrides != null) {
            config.applyOverrides(requestOverrides);
        }
        return config;
    }
}
```

**配置归属清单**:

| 参数 | 归属层 | 存储位置 | 可被下层覆盖 |
|------|:--:|------|:--:|
| LLM Provider (deepseek/openai) | L1 | application.yml | ❌ 全局唯一 |
| 默认Model | L1 | application.yml | ✅ L2/L3可覆盖 |
| 默认温度(temperature) | L1 | application.yml | ✅ |
| Token上限(maxContextTokens) | L1 | application.yml | ✅ |
| Agent超时(ms) | L1 | application.yml | ✅ |
| System Prompt | L2/L3 | Agent模板/实例 | ✅ L3覆盖L2 |
| 工具白名单 | L2/L3 | Agent模板/实例 | ✅ |
| 最大迭代轮数 | L2/L3 | Agent模板/实例 | ✅ |
| Agent图标/描述 | L3 | Agent实例 | — |
| UI主题/展开面板 | 前端 | localStorage | — |

**application.yml L1配置补全**:
```yaml
llm:
  default-provider: deepseek
  default-model: deepseek-chat
  default-temperature: 0.3
  max-context-tokens: 8000
  agent-timeout-ms: 300000

agent:
  default-max-iterations: 5
  default-system-prompt: "你是ECOS平台的AI助手。"
```

**验收**:
```bash
# V1: 编译通过
cd ~/ECOS/ecos_backend && mvn compile -pl engine/ai-engine/ai-engine-impl,gateway -am -DskipTests -q && echo "BUILD PASS"

# V2: 三层覆盖验证
# 用内置data-agent对话 → 验证system prompt来自L2模板
# 基于data-agent创建用户Agent、覆盖system prompt → 验证L3覆盖L2
# 发请求时传override temperature → 验证运行时覆盖

# V3: AgentConfigResolver日志
# 启动后对话，日志输出:
# [AgentConfig] agent=builtin-data-agent → L1 model=deepseek-chat L2 template=data-agent L3 instance=N/A
```

---

### T3: AI工作台完整链路（3天，依赖T1+T2）

**目标**: 前端AI工作台三个面板——ChatbotStudio、逻辑编排(Pipeline)、多Agent协同——统一通过后端AgentStudioService调度。

**改文件**:
- 新增 `service/AgentStudioService.java` — AI工作台统一服务入口
- 改造 `controller/AIPAgentController.java` — 增加测试对话端点
- 改造 `controller/AIPPipelineController.java` — Pipeline执行
- 新增 `controller/AgentStudioController.java` — 统一前端API

**AgentStudioService**:
```java
@Service
public class AgentStudioService {
    // ── ChatbotStudio ──
    /** 创建Agent + 立即测试对话 */
    public AgentTestResult createAndTest(Map<String,Object> agentDef, String testMessage);
    /** 对比两个Agent版本（A/B test） */
    public AgentCompareResult compare(String agentIdA, String agentIdB, List<String> testMessages);

    // ── 逻辑编排 ──
    /** 启动Pipeline执行 */
    public PipelineExecution startPipeline(String pipelineId, Map<String,Object> params);
    /** 查询Pipeline执行状态 */
    public PipelineExecution getPipelineStatus(String executionId);

    // ── 多Agent协同 ──
    /** Orchestrator模式：ai-agent拆解→分发→汇总 */
    public OrchestrationResult orchestrate(String userMessage, String sessionId);
}
```

**API端点**:
```java
// ChatbotStudio:
POST /api/v1/aip/agents/{id}/test    // 测试对话
POST /api/v1/aip/agents/compare      // A/B对比

// 逻辑编排:
POST /api/v1/aip/pipelines/{id}/execute
GET  /api/v1/aip/pipelines/{id}/executions

// 多Agent协同:
POST /api/v1/aip/orchestrate
```

**AI工作台前端协同时序**:
```
用户操作 ChatbotStudio:
  1. 选择模板"data-agent" → 前端 GET /api/v1/aip/agent-templates
  2. 自定义名称+system prompt → POST /api/v1/aip/agents/instantiate
  3. 测试对话 → POST /api/v1/aip/agents/{id}/test
  4. 查看对比 → POST /api/v1/aip/agents/compare

用户操作 逻辑编排:
  1. 创建Pipeline → POST /api/v1/aip/pipelines
  2. 启动执行 → POST /api/v1/aip/pipelines/{id}/execute
  3. 轮询状态 → GET /api/v1/aip/pipelines/{id}/executions

用户操作 多Agent协同:
  1. 输入复杂需求 → POST /api/v1/aip/orchestrate
  2. 查看orchestrator拆解的子任务 → 返回 subTasks[]
  3. 查看汇总结果 → 返回 summary
```

**验收**:
```bash
# V1: 编译通过
cd ~/ECOS/ecos_backend && mvn compile -pl engine/ai-engine/ai-engine-impl -am -DskipTests -q && echo "BUILD PASS"

# V2: ChatbotStudio链路
# 1) 基于data-agent模板创建Agent → 成功
# 2) 对该Agent发测试消息 → 正常返回+标注数据来源
# 3) 对比原模板和用户自定义版本 → 返回两份结果+差异

# V3: 逻辑编排链路
# 1) 创建Pipeline: 步骤1=查数据质量 → 步骤2=生成报告
# 2) 启动执行 → 返回executionId
# 3) 查询状态 → DONE，有输出

# V4: 多Agent协同链路
POST /api/v1/aip/orchestrate
{"message":"分析上月经营异常，找出根因，给出改进建议"}
# 期望:
# - ai-agent拆解为3个子任务: data-analysis → causal-diagnose → suggest
# - 每个子任务分发给对应内置Agent
# - 最终返回汇总报告
```

---

### T4: 会话线程 + Agent评估框架（3天，独立任务）

**T4a: 会话线程（1.5天）**
- `sys_agent_message`加`thread_id`字段（默认'main'）
- AgentLoopService的getMessages按thread_id过滤
- API: `POST .../chat?thread=xxx`

**T4b: Agent评估框架（1.5天）**
- 5题标准问题集（`resources/eval-questions/default.json`）
- `AgentEvaluator`: 逐题执行→裁判打分(1-5)→汇总报告
- API: `POST /api/v1/aip/eval/run` + `GET /api/v1/aip/eval/{id}/report`

**验收**:
```bash
# 线程隔离
# 创建两个线程各自对话 → 验证上下文独立
# 评估打分
# 对内置data-agent跑5题 → avgScore在1-5之间
```

---

## §执行顺序

```
Week 1:
  Day 1-2: T0（Agent持久化）            —— 基础设施，必须先做
  Day 3-5: T1（内置Agent体系）          —— 依赖T0
  Day 5:   T0+T1联调验收

Week 2:
  Day 6-8: T2（配置分层统一）            —— 依赖T1
  Day 9:   T3（AI工作台完整链路）        —— 依赖T1+T2

Week 3:
  Day 10-12: T3继续 + T4（线程+评估）
  Day 13-14: 联调 + Gateway集成测试
  Day 15:   全量编译 + pre-check
```

T3和T4可以并行。

---

## §交付检查清单

```bash
# ─── 1. 全量编译 ───
cd ~/ECOS/ecos_backend && mvn compile -pl gateway -am -DskipTests -q && echo "BUILD PASS"

# ─── 2. 新增文件 ───
ls engine/ai-engine/ai-engine-impl/src/main/resources/agent-templates/data-agent.json
ls engine/ai-engine/ai-engine-impl/src/main/resources/agent-templates/ai-agent.json
ls engine/ai-engine/ai-engine-impl/src/main/java/.../service/AgentTemplateService.java
ls engine/ai-engine/ai-engine-impl/src/main/java/.../service/AgentConfigResolver.java
ls engine/ai-engine/ai-engine-impl/src/main/java/.../service/AgentStudioService.java
ls engine/ai-engine/ai-engine-impl/src/main/java/.../service/AgentEvaluator.java
ls engine/ai-engine/ai-engine-impl/src/main/resources/eval-questions/default.json

# ─── 3. 启动后验证 ───
# 6个内置Agent自动创建: GET /api/v1/aip/agents → role=builtin ×6
# Agent持久化: 重启Gateway → Agent不丢失
# 内置Agent不可删除: DELETE builtin-data-agent → 403
# 用户Agent基于模板: POST instantiate → 继承toolWhitelist

# ─── 4. 功能验证 ───
# ChatbotStudio: 创建→测试→对比A/B
# Pipeline: 创建→执行→查状态
# Orchestrator: 复杂需求→拆解→分发→汇总
# 配置三层: L1(yml)←L2(模板)←L3(实例)覆盖正确
# 会话线程: 多线程独立上下文
# Agent评估: 5题标准集→得分报告
```

---

## §一句话总结

**"P0让Agent能跑，P1让Agent能干活，P2让Agent成体系——6个内置Agent各司其职、配置分层可控、AI工作台一站式操作。ECOS从'有个Agent功能'升级为'Agent平台'。"**
