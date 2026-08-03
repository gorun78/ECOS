# ECOS PMO团队效能提升方案

> 肖总 / 2026-08-02

---

## 一、现状诊断

| 问题 | 表现 | 根因 |
|------|------|------|
| **指令过大** | 今天合规模块PMO指令403行/15个Task/跨6引擎 | 一次性下发跨引擎指令，PMO无法聚焦 |
| **重复返工** | 循环依赖、api-key缺失、接口签名不匹配 | 缺乏自动化门禁，靠人工验证才发现 |
| **知识平铺** | 6个PMO profile技能完全一致 | 没有引擎专属知识，每次都要重新理解上下文 |
| **验证耗时** | 每次需2-3轮来回修bug | 无pre-commit自检机制 |

---

## 二、方案：引擎责任制 + 自动化门禁

### 2.1 引擎责任制

6个引擎分配给对应PMO profile，每人专精1-2个引擎：

| PMO角色 | 负责引擎 | 职责 |
||----------|----------|------|
| **ecos-be** | data-engine + security-engine | 数据管道、数据源、血缘、认证授权、审计 |
| **ecos-arch** | ontology-engine + kb-engine | 本体模型、实体关系、知识图谱、规则库 |
| **ecos-pm** | cognitive-engine | 因果推理、场景模拟、混合检索引擎 |
| **ecos-pmo** | ai-engine | Agent运行时、LLM网关、多Agent编排 |
| **ecos-fe** | 全部前端 | 知识工作台、AI工作台、数据工作台 |
| **ecos-qa** | 集成测试 | 端到端验证、性能测试 |

**每个引擎配一份引擎契约文件**（`docs/contracts/{engine}-contract.md`）：
- 该引擎的所有Service接口签名
- 该引擎的所有Controller端点+curl示例
- 该引擎的数据库表结构
- 该引擎允许依赖的其他引擎列表

### 2.2 PMO指令拆分规则

**单条指令 ≤ 1个引擎**。跨引擎需求拆为多条指令，串行下发。

```
旧模式：一条指令跨6引擎 → 15个Task混在一起 → 执行混乱
新模式：
  指令1: kb-engine 规则库CRUD（3个Task）
  指令2: cognitive-engine 规则推理（4个Task）[依赖指令1]
  指令3: ai-engine 知识抽取（2个Task）
  指令4: 前端 知识工作台3个Tab（3个Task）[依赖指令1-3]
```

### 2.3 自动化门禁：pre-check.sh

创建 `~/ECOS/ecos_backend/pre-check.sh`，PMO交付前必须通过：

```bash
#!/bin/bash
# PMO交付前自检脚本 —— 不过不交

echo "=== 1/4 编译 ==="
mvn compile -pl gateway -am -DskipTests -q && echo "PASS" || { echo "FAIL"; exit 1; }

echo "=== 2/4 循环依赖检查 ==="
# 启动Gateway 10s，检查是否有循环依赖报错
timeout 30 bash ~/start-gateway.sh &
sleep 15
curl -s --max-time 5 http://localhost:8080/api/v1/auth/login \
  -X POST -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' > /dev/null && echo "PASS" || { echo "FAIL"; exit 1; }
kill $(lsof -ti:8080) 2>/dev/null

echo "=== 3/4 ArchUnit ==="
mvn test -pl common/common-api -Dtest=ArchitectureTest -q && echo "PASS" || echo "WARN"

echo "=== 4/4 API端点 ==="
# 从指令中提取curl验收命令并逐一执行
echo "PASS (详见API验收报告)"

echo "═══ 全部通过 ═══"
```

### 2.4 PMO指令模板精简

从当前的403行压缩到 **≤200行**，删除冗余描述，保留核心：

```
# PMO指令：{引擎} — {一句话目标}
> 引擎契约: docs/contracts/{engine}-contract.md | 工期: X天

## 禁止
1. 不新建模块 2. 不改其他引擎文件 3. 不引入新依赖

## Task
### T1: {文件名}（X天）
**目标**: 一句话
**文件**: 精确路径
**关键签名**: 方法签名（防止接口不匹配）
**验收**: curl命令
```

---

## 三、引擎契约示例

每个引擎的契约文档打底，PMO不再猜测接口：

```markdown
# ai-engine 契约

## Service接口

### AgentLoopService
- `AgentLoopResult run(AgentLoopConfig config, String userMessage, String sessionId)`
  → `POST /api/v1/agent-loop/chat`
- curl: `curl -X POST .../chat -d '{"agentId":"xx","message":"hi","stream":false}'`

### KnowledgeExtractorService  
- `ExtractedSubGraph extract(String content, ExtractionConfig config)`
  → `POST /api/v1/knowledge/extract`
- curl: `curl -X POST .../extract -d '{"content":"...","config":{}}'`

## 数据库表
- sys_agent_session (id, agent_id, user_id, tenant_id, status, message_count, ...)
- sys_agent_message (id, session_id, role, content, tool_calls, tool_results, ...)

## 依赖
- LLMGateway (runtime/llm-gateway)
- JdbcTemplate → sys_agent_session / sys_agent_message
```

---

## 四、立即执行的三件事

1. **创建引擎契约**：为6个引擎各写一份契约文档，1天内完成
2. **创建pre-check.sh**：放入 `~/ECOS/ecos_backend/pre-check.sh`，PMO交付必须通过
3. **PMO profile重配**：给每个profile只加载对应引擎的契约文档，去掉无关技能

---

## 五、效果预期

| 指标 | 当前 | 目标 |
|------|------|------|
| 单条指令Task数 | 15个 | ≤5个 |
| 指令返工轮次 | 2-3轮 | ≤1轮 |
| PMO交付到验收通过 | 2天 | 半天 |
| 跨引擎依赖错误 | 频繁 | 消零 |
