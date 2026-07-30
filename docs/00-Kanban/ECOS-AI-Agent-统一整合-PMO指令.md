# PMO指令: ECOS AI Agent 统一整合 — Hermes + Java 双引擎归并

> **来源**: 肖国荣 | **日期**: 2026-07-29
> **协同**: ECOS-ARCH (架构评审) + ECOS-BE (Java重构) + ECOS-FE (前端适配)
> **铁律**: 不动API路径签名、不改现有Controller行为、每阶段独立验证、编译不过不进下一阶段

---

## 零、现状审计结论

ECOS 当前存在 **两套 Agent 运行时 + 一套 LLM 网关**，三者彼此不知对方存在：

| 模块 | 位置 | 实质 | 问题 |
|------|------|------|------|
| `hermes-engine` | `runtime/hermes-engine/` | LLM 调用网关 (OpenAI chat/completions) | 命名误导，无 Agent 能力 |
| `agent-service` | `services/agent-service/` | 真正的 Agent 运行时 (ToolRouter+22 Tools+Planner+Executor+Memory) | 无统一 LLM 入口 |
| `ai-engine` | `engine/ai-engine/` | AI 能力 REST API 层 (28 Controller) | 上游依赖上述两者，自身无运行时 |

**核心矛盾**：
- `hermes-engine` 封装的是 OpenAI API，不是开源 Hermes Agent (https://github.com/NousResearch/hermes-agent)
- `agent-service` 自建了 Planner/Executor/ToolRouter/Memory，但与 Hermes 的 memory/delegation/cron/skills 零交集
- AgentMesh 的 Mission 执行引擎与 Hermes 的 delegation+kanban 功能重叠但互不感知

---

## 一、目标架构

```
┌──────────────────────────────────────────────────────────┐
│  engine/ai-engine (REST API 层，不动)                     │
│  AgentChatController  AgentMeshController                │
│  AgentCallController  AgentProfileController             │
│  CognitiveController  DiagnosticAgentController  ...      │
├──────────────────────────────────────────────────────────┤
│  agent-service (Java，保留核心)                           │
│  ├── ToolRouter + 22 Tools ──→ 暴露为 MCP Server        │
│  ├── Planner (域规划模板) ──→ 内部走 llm-gateway        │
│  └── Copilot / Evolution / Guardrails                    │
├──────────────────────────────────────────────────────────┤
│  runtime/llm-gateway (Java，改名后保留)                   │
│  ├── Provider 路由 (deepseek/openrouter/openai)          │
│  ├── 流式 SSE 解析                                       │
│  └── 重试 + 熔断                                         │
├──────────────────────────────────────────────────────────┤
│  ECOS ai-agent (Python Hermes + ECOS Plugin)             │
│  ├── Agent 循环 ──── 替代 AgentMesh 执行引擎             │
│  ├── Memory ──────── 替代 agent-service MemoryService    │
│  ├── Session ─────── 替代 ConcurrentHashMap              │
│  ├── Delegation ──── 替代 MissionExecutionEngine         │
│  ├── Cron ────────── 新增：定时诊断/数据质量             │
│  ├── Skills ──────── 新增：域技能包管理                   │
│  ├── MCP Client ──── 调用 ECOS MCP Server               │
│  └── ECOS Plugin ─── 多租户/RBAC/审计                    │
├──────────────────────────────────────────────────────────┤
│  ECOS MCP Server (Java)                                  │
│  ├── Data Tools (query/list_tables/get_schema/lineage)   │
│  ├── Ontology Tools (list_objects/get_properties/...)    │
│  ├── Cognitive Tools (rag_search/kg_query/diagnose)      │
│  └── Security Tools (check_permission/audit_access)      │
└──────────────────────────────────────────────────────────┘
```

**职责分界**：
- Java 管业务（ToolRouter / Planner / 域知识 / MCP Server）
- Hermes 管 Agent（循环 / 记忆 / 调度 / 编排）
- MCP 做桥
- llm-gateway 做 LLM 底座

---

## 二、三阶段执行计划

### Phase 1: 正名归位 — hermes-engine → llm-gateway (P0, 1天)

**目标**: 消除命名误导，统一 LLM 调用入口，不改逻辑。

#### P1-1 模块重命名

| Task | 文件/路径 | 操作 |
|:-----|----------|------|
| P1-1.1 | `runtime/hermes-engine/pom.xml` | `<artifactId>` 改为 `llm-gateway` |
| P1-1.2 | `runtime/hermes-engine/` 目录 | 重命名为 `runtime/llm-gateway/` |
| P1-1.3 | 包路径 `com.chinacreator.gzcm.runtime.hermes` | 改为 `com.chinacreator.gzcm.runtime.llm` |
| P1-1.4 | `HermesEngine.java` | 重命名为 `LLMGatewayService.java` |
| P1-1.5 | `HermesEngineImpl.java` | 重命名为 `LLMGatewayServiceImpl.java` |
| P1-1.6 | `HermesAutoConfiguration.java` | 重命名为 `LLMGatewayAutoConfiguration.java` |
| P1-1.7 | `HermesProperties.java` | 重命名为 `LLMGatewayProperties.java`，前缀 `hermes` → `llm` |
| P1-1.8 | `AgentScheduler.java` / `AgentSchedulerImpl.java` | 保留不动（功能正确，Semaphore并发控制属于网关层） |
| P1-1.9 | `LLMGateway.java` / `LLMGatewayImpl.java` | 保留不动（已是正确命名） |
| P1-1.10 | `SessionManager.java` / `SessionManagerImpl.java` | 保留不动 |
| P1-1.11 | `ProfileManager.java` / `ProfileManagerImpl.java` | 保留不动 |
| P1-1.12 | `application-hermes.yml` | 重命名为 `application-llm.yml` |

#### P1-2 依赖链更新

| Task | 文件 | 操作 |
|:-----|------|------|
| P1-2.1 | `engine/ai-engine/ai-engine-impl/pom.xml` | 若有 `hermes-engine` 依赖，改为 `llm-gateway` |
| P1-2.2 | `services/agent-service/pom.xml` | 确认是否依赖 hermes-engine，如有则更新 |
| P1-2.3 | `runtime/pom.xml` | `<module>` 更新 |
| P1-2.4 | 根 `pom.xml` | `<module>` 更新 (如有) |
| P1-2.5 | `gateway/pom.xml` | 确认 `<dependency>` 更新 |
| P1-2.6 | 所有 import 语句 | 全量搜索 `runtime.hermes` 替换为 `runtime.llm` |

#### P1-3 AgentService 统一走 llm-gateway

| Task | 文件 | 操作 |
|:-----|------|------|
| P1-3.1 | `services/agent-service/` 中 Planner/Executor 实现 | 审计是否裸调 HTTP → LLM，若是则注入 `LLMGatewayService` 替换 |
| P1-3.2 | `agent-service/pom.xml` | 添加 `llm-gateway` 依赖 |

#### P1-4 验证

```bash
# V1: 全量编译
cd /home/guorongxiao/ECOS/ecos_backend
mvn clean install -DskipTests -q
# 期望: BUILD SUCCESS

# V2: 搜索残留旧名
grep -r "hermes-engine\|HermesEngine\|hermes\.engine" /home/guorongxiao/ECOS/ecos_backend/ --include="*.java" --include="*.xml" --include="*.yml" --include="*.properties" | grep -v target | grep -v ".class"
# 期望: 0 匹配（除本条指令文档外）

# V3: Gateway 启动
bash ~/start-gateway.sh
# 期望: 8080 端口正常响应
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
# 期望: 返回 token

# V4: AgentCallController 仍可调
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")
curl -s -X POST http://localhost:8080/api/v1/agent-call/chat \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"subsystem":"sysman","profileName":"default","message":"hello"}'
# 期望: 200 + content 字段有值
```

---

### Phase 2: Hermes Agent 作为 MCP Server 部署 (P1, 3天)

**目标**: 开源 Hermes Agent 作为 ECOS 基础设施运行，通过 MCP 暴露 memory/session/delegation/cron/skills 能力。

> **前置条件**: Phase 1 编译通过 + 启动正常

#### P2-1 Hermes Agent 部署

| Task | 内容 |
|:-----|------|
| P2-1.1 | 确认 Hermes Agent 安装路径与版本 |
| P2-1.2 | 创建 ECOS 专用 Hermes profile: `ecos-ai-agent` |
| P2-1.3 | 配置 Hermes MCP Server (stdio 模式)，工具白名单: `memory_*, session_*, delegate_task, cronjob, skill_*` |
| P2-1.4 | 裁剪无关能力: browser, terminal, file_raw, gaming, smart-home |
| P2-1.5 | 部署 ECOS Plugin: tenant 隔离层、RBAC 拦截器、审计日志适配器 |
| P2-1.6 | 验证 Hermes 独立启动: `hermes -p ecos-ai-agent chat` |

#### P2-2 agent-service 新增 MCP Client

| Task | 文件 | 操作 |
|:-----|------|------|
| P2-2.1 | `services/agent-service/src/main/java/.../runtime/mcp/HermesMCPClient.java` | 新增，封装对 Hermes MCP Server 的 stdio 调用 |
| P2-2.2 | `services/agent-service/src/main/java/.../runtime/mcp/HermesMemoryAdapter.java` | 新增，实现 MemoryService 接口，底层调 Hermes memory_* tools |
| P2-2.3 | `services/agent-service/src/main/java/.../runtime/mcp/HermesSessionAdapter.java` | 新增，实现会话持久化，底层调 Hermes session_* tools |
| P2-2.4 | `services/agent-service/src/main/java/.../runtime/mcp/HermesDelegationAdapter.java` | 新增，底层调 Hermes delegate_task |

#### P2-3 验证

```bash
# V1: Hermes MCP 连接验证
cd /home/guorongxiao/c2eos/engine
C2EOS_SERVER_LOG_LEVEL=ERROR .venv/bin/python -c "
import subprocess, json, select
proc = subprocess.Popen(['.venv/bin/python', 'src/server.py'],
    stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
init = json.dumps({'jsonrpc':'2.0','id':1,'method':'initialize',
    'params':{'protocolVersion':'2024-11-05','capabilities':{},'clientInfo':{'name':'t','version':'1'}}}) + '\n'
proc.stdin.write(init.encode()); proc.stdin.flush()
r,_,_ = select.select([proc.stdout], [], [], 5)
if r: print('MCP OK:', json.loads(proc.stdout.readline())['result']['serverInfo']['name'])
"
# 期望: MCP OK: Hermes Agent

# V2: Hermes 工具列表
# 期望: 包含 memory_store, memory_search, session_create, delegate_task, cronjob_create 等

# V3: Java 端 MCP Client 集成测试
mvn test -pl services/agent-service -Dtest=HermesMCPClientTest
# 期望: PASS
```

---

### Phase 3: 能力归并 (P2, 2周)

**目标**: AgentMesh 执行引擎、MemoryService、Session 管理逐步迁入 Hermes，Java 侧保留 REST API + 域 Tool。

> **前置条件**: Phase 2 MCP 连接稳定 + 集成测试全绿

#### P3-1 Memory 归并

| Task | 操作 |
|:-----|------|
| P3-1.1 | `agent-service` 的 `MemoryService` 改为 `HermesMemoryAdapter` 实现 |
| P3-1.2 | `MemoryContext`、`MemoryRecord` 模型保留，适配到 Hermes memory schema |
| P3-1.3 | 旧内存 MemoryService 标记 `@Deprecated`，保留一个版本后删除 |

```bash
# V: 新旧 Memory 并行运行对比测试
mvn test -pl services/agent-service -Dtest=MemoryMigrationTest
# 期望: 新旧实现读写一致
```

#### P3-2 Session 归并

| Task | 操作 |
|:-----|------|
| P3-2.1 | `llm-gateway` 的 `SessionManagerImpl` (ConcurrentHashMap) → `HermesSessionAdapter` |
| P3-2.2 | 增加 session 持久化 (Hermes session_search 可查历史) |

```bash
# V: 重启后 session 不丢失
# 1. AgentCallController 创建 session → 获取 sessionId
# 2. 重启 Gateway
# 3. 用 sessionId 继续对话
# 期望: 返回历史上下文，非 "session not found"
```

#### P3-3 AgentMesh → Hermes Delegation 归并

| Task | 操作 |
|:-----|------|
| P3-3.1 | `AgentMeshController` REST API 保留 (前端依赖) |
| P3-3.2 | `MissionExecutionEngine.execute()` 内部改为调用 `HermesDelegationAdapter.delegate()` |
| P3-3.3 | PIPELINE 模式 → Hermes kanban 串行调度 |
| P3-3.4 | SUPERVISOR 模式 → Hermes delegate_task 分发 |
| P3-3.5 | 意图路由 `route-intent` → Hermes skill 匹配 |

```bash
# V: Mission 创建+执行
curl -s -X POST http://localhost:8080/api/agent-mesh/missions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"title":"测试Mission","mode":"PIPELINE","tasks":[{"agentId":"a1","instruction":"step1"},{"agentId":"a2","instruction":"step2"}]}'
# 期望: 200 + mission.id

# 执行
curl -s -X POST http://localhost:8080/api/agent-mesh/missions/{id}/execute \
  -H "Authorization: Bearer $TOKEN"
# 期望: 200 + status=COMPLETED
```

#### P3-4 Cron 能力新增

| Task | 操作 |
|:-----|------|
| P3-4.1 | `ai-engine` 新增 `CronJobController`: CRUD + 启用/禁用 + 执行历史 |
| P3-4.2 | 预置两个定时 Agent: 每日数据质量巡检、每周认知诊断报告 |

#### P3-5 Skills 能力新增

| Task | 操作 |
|:-----|------|
| P3-5.1 | `ai-engine` 新增 `SkillController`: 技能包上传/启用/禁用/版本管理 |
| P3-5.2 | 预置三个技能包: 数据治理、企业经营诊断、政务一件事 |

---

## 三、禁止清单

| 禁止项 | 原因 |
|--------|------|
| ❌ 修改现有 Controller 的 API 路径或参数签名 | 前端已依赖，只增不改 |
| ❌ 在 Phase 1 改任何业务逻辑 | 纯重命名，改逻辑放到 Phase 2/3 |
| ❌ 删除旧 `MemoryService` / `SessionManagerImpl` / `MissionExecutionEngine` | 先并行运行，验证通过后再标记 @Deprecated |
| ❌ 在 Java 里重写 Hermes 的 memory/delegation | 通过 MCP 调 Hermes，不在 Java 重复造 |
| ❌ Fork Hermes 源码 | 用 plugin + skill 扩展，保持可跟随上游升级 |
| ❌ 新增 Maven 模块 | 在现有模块内重构 |
| ❌ 新增 Docker 容器 | Hermes 与 ECOS 共用 Python venv |

---

## 四、风险与回滚

| 风险 | 影响 | 缓解 |
|------|------|------|
| Phase 1 重命名漏改依赖 | 编译失败 | P1-4 的 V1+V2 验证门禁 |
| Hermes MCP stdio 连接不稳定 | Phase 2/3 阻塞 | 已验证 821ms 连接稳定，加健康检查 + 自动重启 |
| agent-service 内部裸 HTTP 调用 LLM 未发现 | 改漏 | P1-3.1 全量审计 |
| Phase 3 AgentMesh 迁 Hermes 后前端异常 | 用户可见 | 前端调 AgentMeshController REST API 不变，底层替换透明 |

**回滚路径**: 每个 Phase 独立 git branch，验证不过直接切回 main。

---

## 五、工时估算

| Phase | 内容 | 估算 |
|:------|------|:----:|
| Phase 1 | 正名归位 (重命名+依赖更新+验证) | 1天 |
| Phase 2 | Hermes MCP 部署 + agent-service MCP Client | 3天 |
| Phase 3 | 能力归并 (Memory/Session/AgentMesh/Cron/Skills) | 2周 |

---

*审批后发 PMO 执行。Phase 1 先跑，Phase 2/3 等 Phase 1 验证通过后再拆解为细粒度原子任务。*
