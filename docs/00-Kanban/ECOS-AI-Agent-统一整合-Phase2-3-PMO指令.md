# PMO指令: ECOS AI Agent 统一整合 — Phase 2+3

> **来源**: 肖国荣 | **日期**: 2026-07-29
> **前置**: Phase 1 (hermes-engine→llm-gateway) 已完成

---

## 铁律

1. 严格按 Phase 顺序，Phase 2 全绿再进 Phase 3
2. 禁止跨 Phase 预创建文件
3. 每个 Task curl 验收不过不回下一 Task
4. 只改指令列的文件
5. Bean 名必须加 `ecos` 前缀避免冲突

---

## Phase 1 遗留修复

| Task | 文件 | 操作 |
|:-----|------|------|
| FIX-1 | `gateway/src/main/resources/application.yml` | `auth.whitelist.paths` 加 `- "/api/v1/agent-call/**"` |

```bash
# 验收: 非404
curl -s -w "\nHTTP:%{http_code}" -X POST http://localhost:8080/api/v1/agent-call/chat \
  -H "Authorization: Bearer $(curl -s -X POST http://localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")" \
  -H "Content-Type: application/json" \
  -d '{"subsystem":"sysman","profileName":"default","message":"hi"}'
# 期望: HTTP 200 或 500，不是 404
```

---

## Phase 2: Hermes MCP 部署 + Client 适配 (3天)

### P2-1 Hermes MCP Server

- P2-1.1: 确认 `hermes -p gorunkol chat` 正常
- P2-1.2: 配置 MCP 白名单 `memory_*, session_*, delegate_task, cronjob, skill_*`
- P2-1.3: 创建 `ecos-ai-agent` profile，裁剪 browser/terminal/file_raw

### P2-2 agent-service MCP Client（全部新文件）

| Task | 新文件路径 |
|:-----|----------|
| P2-2.1 | `services/agent-service/src/main/java/com/chinacreator/gzcm/services/agent/runtime/mcp/HermesMCPClient.java` |
| P2-2.2 | `services/agent-service/src/main/java/com/chinacreator/gzcm/services/agent/runtime/mcp/HermesMemoryAdapter.java` |
| P2-2.3 | `services/agent-service/src/main/java/com/chinacreator/gzcm/services/agent/runtime/mcp/HermesSessionAdapter.java` |
| P2-2.4 | `services/agent-service/src/main/java/com/chinacreator/gzcm/services/agent/runtime/mcp/HermesDelegationAdapter.java` |

Spring Bean 名必须为: `ecosHermesMCPClient`, `ecosHermesMemoryAdapter`, `ecosHermesSessionAdapter`, `ecosHermesDelegationAdapter`。

### P2-3 验证

```bash
# V1: 编译
cd /home/guorongxiao/ECOS/ecos_backend && env -i HOME=/home/guorongxiao PATH=/usr/bin:/usr/local/bin:/home/guorongxiao/.local/bin:/home/guorongxiao/.local/apache-maven-3.9.11/bin JAVA_HOME=/home/guorongxiao/.local/jdk/jdk-17.0.19+10 bash -c 'mvn install -DskipTests -Dmaven.test.skip=true -q && echo BUILD SUCCESS'

# V2: 无 Bean 冲突
# Gateway 启动无 ConflictingBeanDefinitionException

# V3: MCP 连接
# HermesMCPClient 初始化成功 + tools 列表可查
```

---

## Phase 3: 能力归并 (2周)

### P3-1 Memory

| Task | 文件 | 操作 |
|:-----|------|------|
| P3-1.1 | `services/agent-service/.../memory/MemoryServiceImpl.java` | 注入 `@Autowired(required=false) ecosHermesMemoryAdapter`，store/search 走 MCP，null 时降级原逻辑 |

### P3-2 Session

| Task | 文件 | 操作 |
|:-----|------|------|
| P3-2.1 | `runtime/llm-gateway/.../session/SessionManagerImpl.java` | 注入 `@Autowired(required=false) ecosHermesSessionAdapter`，createSession 同步持久化，null 时降级 |

### P3-3 AgentMesh

| Task | 文件 | 操作 |
|:-----|------|------|
| P3-3.1 | `services/agent-service/.../orchestration/OrchestrationServiceImpl.java` | 注入 `@Autowired(required=false) ecosHermesDelegationAdapter`，SUPERVISOR→delegate_task，PIPELINE→kanban |

### P3-4 Cron

| Task | 新文件 | 操作 |
|:-----|-------|------|
| P3-4.1 | `engine/ai-engine/ai-engine-impl/.../controller/CronJobController.java` | CRUD controller，调 HermesMCPClient cronjob_* |

### P3-5 Skills

| Task | 新文件 | 操作 |
|:-----|-------|------|
| P3-5.1 | `engine/ai-engine/ai-engine-impl/.../controller/SkillController.java` | 列表/启用/禁用，调 HermesMCPClient skill_* |

### P3 验证

```bash
# Memory: 连续两轮对话 → 回答包含上下文
curl -X POST http://localhost:8080/api/v1/agent/chat \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"agentId":"t","message":"我叫张三","sessionId":"s1"}'
curl -X POST http://localhost:8080/api/v1/agent/chat \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"agentId":"t","message":"我叫啥","sessionId":"s1"}'

# Session: 重启 Gateway → 同 sessionId 不报 "not found"

# AgentMesh: POST /api/agent-mesh/missions → execute → status=COMPLETED

# Cron: POST /api/v1/cron-jobs → 200

# Skills: GET /api/v1/skills → 200 + 列表
```

---

## 禁止

| ❌ | 原因 |
|----|------|
| 改现有 API 路径 | 前端依赖 |
| 改 `runtime/llm-gateway` 除 SessionManagerImpl 外任何文件 | Phase 1 已稳定 |
| 删旧 MemoryService/MissionExecutionEngine | 保留 fallback |
| Bean 名不用 `ecos` 前缀 | 避免冲突 |
| 跨 Phase 建文件 | 执行顺序铁律 |

---

## 提交

每个 Task 独立 commit: `[Phase2] P2-2.1 HermesMCPClient - MCP stdio 封装`
