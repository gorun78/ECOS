# ECOS PMO效能方案 v2：引擎独立 + 高效联调

> 肖总 / 2026-08-02

---

## 一、核心思路

**每个PMO在自己的引擎boot上开发，改完立刻自测。联调时一键并行启动全部引擎。**

```
生产部署：Gateway(:8080)  ← 聚合全部引擎，JWT认证，统一入口
开发模式：6个engine boot并行，各自独立端口，auth白名单全放行
```

---

## 二、引擎-PMO分配 + 工作目录

| PMO | 引擎 | 端口 | 工作目录 |
|-----|------|:--:|------|
| ecos-be | data-engine + security-engine | 18082, 18081 | `engine/data-engine/` |
| ecos-arch | ontology-engine + kb-engine | 18083, 18086 | `engine/ontology-engine/` |
| ecos-pm | cognitive-engine | 18089 | `engine/cognitive-engine/` |
| ecos-pmo | ai-engine | 18084 | `engine/ai-engine/` |
| ecos-fe | 前端（连Gateway） | 3000 | `ecos_frontend/` |
| ecos-qa | 集成测试 | — | 根目录 |

**每个引擎目录放入 `AGENTS.md`**（见§四），PMO在其工作目录下自动加载专属上下文。

---

## 三、联调方案

### 3.1 单引擎开发（日常）

PMO只启动自己引擎。改代码→编译→启动→curl：

```bash
# ecos-pmo 开发 ai-engine
cd engine/ai-engine
mvn install -pl ai-engine-boot -am -DskipTests -q   # 30-60s
mvn spring-boot:run -pl ai-engine-boot               # 10s, port 18084
# curl localhost:18084/api/v1/agent-loop/chat ...
```

### 3.2 全栈联调（跨引擎）

```bash
bash ~/ECOS/dev-up.sh    # 并行启动6个boot
bash ~/ECOS/dev-down.sh  # 一键停掉全部
```

`dev-up.sh` 逻辑：
```bash
#!/bin/bash
PORTS=(18081 18082 18083 18084 18086 18089)
ENGINES=(security data ontology ai kb cognitive)

for i in "${!ENGINES[@]}"; do
  engine=${ENGINES[$i]}
  port=${PORTS[$i]}
  (cd engine/${engine}-engine && \
   mvn spring-boot:run -pl ${engine}-engine-boot -q 2>&1 | sed "s/^/[${engine}:${port}] /") &
done
wait
```

### 3.3 跨引擎REST调用

引擎间不走Maven依赖（除-api模块），走REST。每个引擎契约文档明确标注：

```yaml
# 依赖其他引擎的端点
dependencies:
  cognitive-engine:
    reason: POST http://localhost:18089/api/v1/knowledge/reason
    causalChain: GET http://localhost:18089/api/v1/rules/causal-chain/{ruleId}
  kb-engine:
    graphQuery: POST http://localhost:18086/api/v1/kb/graph/query
```

**关键优势**：改cognitive-engine不需要重新编译ai-engine。只要API契约不变，各自独立演进。

### 3.4 认证简化

开发模式：每个boot的 `application.yml` 加：
```yaml
auth:
  dev-mode: true  # 跳过JWT验证，所有请求放行
```

生产 `application-enterprise.yml` 覆盖为 `dev-mode: false`。

---

## 四、引擎AGENTS.md（示例：ai-engine）

```markdown
# ai-engine — Agent运行时 + LLM网关

> 端口: 18084 | PMO: ecos-pmo | 依赖: runtime/llm-gateway, kb-engine-api

## 我负责的
- AgentLoopService: 多轮工具调用循环
- ToolExecutorService: SQL/REST/BUILTIN三种执行模式
- AgentSessionService: PG持久化会话
- AgentDelegationService: 子Agent委托
- KnowledgeExtractorService: KAG风格知识抽取

## 我暴露的端点
| 端点 | 方法 | 用途 |
|------|------|------|
| /api/v1/agent-loop/chat | POST | Agent对话(stream=true→SSE) |
| /api/v1/agent-loop/sessions | POST/GET | 会话管理 |
| /api/v1/agent-loop/sessions/{id}/chat | POST | 会话内对话 |
| /api/v1/knowledge/extract | POST | 知识抽取 |
| /api/v1/knowledge/reason | POST | 混合推理 |

## 我的数据库表
- sys_agent_session, sys_agent_message

## 我依赖的外部端点
| 引擎 | 端点 | 用途 |
|------|------|------|
| cognitive-engine | POST :18089/api/v1/knowledge/reason | 推理委托 |
| kb-engine | POST :18086/api/v1/kb/graph/query | KG查询 |

## 禁止
1. 不直接import其他引擎的impl模块
2. 不改LLMGatewayService接口
3. Agent Loop上限5轮
```

---

## 五、与当前工作流的差异

| | 当前 | 改造后 |
|------|------|------|
| PMO工作目录 | 根目录（1000行AGENTS.md） | 引擎目录（~100行AGENTS.md） |
| 编译范围 | gateway全量(180s) | 单引擎(30s) |
| 启动 | Gateway(70s) | 引擎boot(10s) |
| 联调 | 只能用Gateway | 6boot并行(30s) 或 只启依赖的2-3个 |
| 跨引擎调用 | Maven依赖(编译时耦合) | REST(运行时耦合) |
| 返工成本 | 4分钟/轮 | 45秒/轮 |

---

## 六、立即要做

1. ✅ **创建 `dev-up.sh` / `dev-down.sh`** — `~/ECOS/dev-up.sh` / `~/ECOS/dev-down.sh`
2. ✅ **给6个引擎写AGENTS.md** — `engine/{name}-engine/AGENTS.md`
3. ✅ **改造vite.config.ts** — 17条引擎专属路由 + Gateway fallback
4. ⬜ **配置PMO profile的workdir** — ecos-pmo → ai-engine, ecos-be → data-engine, ...
5. ~~auth dev-mode~~ — 不需要，引擎boot无Spring Security，天然对localhost放行

**已完成交付物**：
| 文件 | 用途 |
|------|------|
| `~/ECOS/dev-up.sh` | 一键启动全部/指定引擎（`dev-up.sh ai kb`） |
| `~/ECOS/dev-down.sh` | 一键停止全部引擎 |
| `ecos_frontend/vite.config.ts` | 多路径代理：引擎路径→boot端口 |
| `engine/*/AGENTS.md` ×6 | 各引擎契约文档（端口/端点/依赖/禁止） |

---

## 七、前端方案：Vite多路径代理——前端不连Gateway

**原理**：Vite dev server（`:3000`）按API路径前缀将请求分发到对应引擎boot。

```
/api/v1/agent-loop/chat  → :18084 (ai-engine)
/api/v1/rules/check      → :18086 (kb-engine)  
/api/v1/ecos/ontologies   → :18083 (ontology-engine)
/api/v1/auth/login        → :8080  (Gateway fallback)
```

**具体改动**：在 `vite.config.ts` 的 proxy 中，在 `/api` 规则前插入引擎专属路由：

```ts
proxy: {
  // ── 引擎专属路由（精确匹配，优先级高于Gateway fallback）──
  '/api/v1/agent-loop':    'http://localhost:18084',
  '/api/v1/agent-mesh':    'http://localhost:18084',
  '/api/v1/agent':         'http://localhost:18084',
  '/api/v1/agent-call':    'http://localhost:18084',
  '/api/v1/knowledge':     'http://localhost:18084',
  '/api/v1/security':      'http://localhost:18081',
  '/api/v1/audit':         'http://localhost:18081',
  '/api/v1/abac':          'http://localhost:18081',
  '/api/v1/data-masking':  'http://localhost:18081',
  '/api/v1/data-permission':'http://localhost:18081',
  '/api/v1/engine/data':   'http://localhost:18082',
  '/api/v1/ecos':          'http://localhost:18083',
  '/api/v1/cognitive':     'http://localhost:18089',
  '/api/v1/world-model':   'http://localhost:18089',
  '/api/v1/rules':         'http://localhost:18086',
  '/api/v1/kb':            'http://localhost:18086',
  // ── Fallback ──
  '/api': {
    target: 'http://localhost:8080',
    changeOrigin: true,
    configure: (proxy) => {
      proxy.on('proxyReq', (proxyReq, req) => {
        if (req.headers.authorization) {
          proxyReq.setHeader('Authorization', req.headers.authorization);
        }
      });
    },
  },
},
```

**前端联调场景**：

| 场景 | 启动的后端 | 前端 |
|------|-----------|------|
| 改数据工作台 | data-engine(18082) | npm run dev |
| 改知识工作台合规Tab | kb-engine(18086)+cognitive-engine(18089) | npm run dev |
| 改AI工作台 | ai-engine(18084) | npm run dev |
| 全栈联调 | `dev-up.sh` 全部6个 | npm run dev |
| 纯前端UI | 无需后端 | npm run dev（Gateway处理认证+系统管理） |