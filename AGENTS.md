# ECOS — Root Monorepo Guide

> **ECOS** = Enterprise Cognitive Operating System. Core pipeline: data governance → knowledge graph → LLM agent deployment.
> Windows path: `D:\workspace\javaprojects\ECOS\`（2026-09-09 起已从 WSL 迁移到 Windows 原生开发）

## Repo Structure

| Directory | Stack | Entry | Notes |
|-----------|-------|-------|-------|
| `ecos_backend/` | Java 17 / Spring Boot 3.2.2 / MyBatis / PG | `gateway/GatewayApplication.java` | Microservice v2 (7 JARs). See `ecos_backend/AGENTS.md` |
| `ecos_frontend/` | React 19 / Vite 6 / Tailwind 4 / TypeScript | `src/main.tsx` → `App.tsx` | Express server (`server.ts`) for SSR. Proxies `/api` → `localhost:8080` |
| `ecos-docker/` | Docker Compose | `docker-compose.yml` | PG 16, Neo4j 5, MinIO, OPA |
| `ecos-kb/` | Python scripts + JSON | `scripts/scan_all.py` | Auto-generated API index & schema. See `ecos-kb/AGENTS.md` |
| `ecos-tests/` | Node.js / Playwright | `data-workbench-smoke.mjs` | Headless smoke tests against running backend+frontend |
| `docs/` | Markdown | — | Kanban, architecture, specs, handover docs |
| `ecos-git-repos/` | Git data dirs | — | `.gitkeep` placeholders for pipeline/ontology data |

启动命令（PowerShell 一行式，**不创建临时脚本**）：

```powershell
# 后端（enterprise profile, gateway :8080 微服务 v2）
powershell -NoProfile -ExecutionPolicy Bypass -File D:\workspace\javaprojects\ECOS\_win_tasks\start-gateway.ps1

# 前端
cd D:\workspace\javaprojects\ECOS\ecos_frontend && npm run dev

# 构建
& "D:\JavaProjects\env\apache-maven-3.9.11\bin\mvn.cmd" -f D:\workspace\javaprojects\ECOS\ecos_backend\pom.xml clean install -DskipTests
```

**_win_tasks/ 脚本清单**（只增不改，禁止为新任务临时创建）：
| 脚本 | 用途 |
|------|------|
| `start-gateway.ps1` | 后端启动（推荐，加载 JWT + DEEPSEEK + infra 检查） |
| `fe_win.bat` | 前端 dev 启动 |
| `gateway_run.bat` | mvn spring-boot:run 方式启动（开发调试用，慢） |
| `backend_resume.cmd` | 后端停止/恢复管理 |

**Java**: `C:\Program Files\Microsoft\jdk-17.0.17.10-hotspot` | **Maven**: `D:\JavaProjects\env\apache-maven-3.9.11`

## Frontend Quick Commands (Windows)

```powershell
cd D:\workspace\javaprojects\ECOS\ecos_frontend
npm install                             # 首次或依赖变更时
npm run dev                             # Vite dev server (port 3000), proxies /api→:8080
npm run build                           # vite build + esbuild server → dist/
npm run lint                            # tsc --noEmit
npm test                                # vitest run
npm run test:watch                      # vitest watch
```

**Env**: `GEMINI_API_KEY` in `.env.local` for AI features; `DISABLE_HMR=true` to disable hot reload.
**Node**: `C:\Program Files\nodejs\node.exe`

## Three Editions (Maven Profiles)

| Edition | DB | Profile Flag |
|---------|----|-------------|
| standard (default) | PostgreSQL | `-Pstandard` |
| enterprise | PostgreSQL + Neo4j | `-Penterprise` |
| ultimate/flagship | PostgreSQL + Neo4j + Doris | `-Pultimate` |

## Database

- PostgreSQL 16, database `sys_man`, local creds `postgres/postgres`
- MyBatis (not JPA — Hibernate auto-config excluded)
- Flyway disabled (`spring.flyway.enabled: false`)
- Schema rule: only add columns/tables, never drop

## Infrastructure (Windows Docker Desktop)

基础设施容器运行在 **Windows Docker Desktop**（非 WSL 内部），数据卷持久化为 `ecos_pgdata` / `ecos_neo4jdata` / `ecos_miniodata` 等命名卷。

```powershell
# 从 Windows PowerShell 直接操作（docker CLI 指向 Docker Desktop server）
docker ps --filter "name=ecos-"          # 查看容器状态
docker start ecos-postgres ecos-neo4j ecos-minio ecos-opa ecos-kafka ecos-zookeeper  # 启动全部
docker stop  ecos-postgres               # 停止单个
docker exec ecos-postgres psql -U postgres -d sys_man -c "SELECT 1"  # 验证 PG
```

compose 文件：`ecos-docker/docker-compose.yml`（PG 16:5432, Neo4j 5:7474+7687, MinIO:9000, OPA:8181, Kafka:9092, ZK:2181）

## Windows 迁移注意事项（2026-09-09 起）

- **路径**: 所有操作的根目录为 `D:\workspace\javaprojects\ECOS\`，不再使用 WSL 路径
- **启动脚本**: 统一使用 `_win_tasks/` 下的 4 个核心脚本（见表格），**禁止为新任务创建临时脚本**（架构铁律 #14）
- **操作原则**: 探测用 RunCommand 内联命令，调试用 logger debug，不用独立脚本
- **JWT**: `C:\Users\guoro\.config\ecos\jwt-private-key.pem`（start-gateway.ps1 自动加载）
- **DEEPSEEK**: `C:\Users\guoro\.hermes\profiles\gorunkol\.env`（start-gateway.ps1 自动加载）
- **Docker**: Windows Docker Desktop 管理（`docker` CLI 直接用），不再走 WSL
- **清端口**: `Get-NetTCPConnection -LocalPort 8080 -State Listen | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }`
- **Git SSH**: 如仍走代理，`$env:GIT_SSH_COMMAND = "ssh -o ProxyCommand=nc -X 5 -x 127.0.0.1:7897 %h %p"`

## Frontend Conventions

- Theme tokens via `useTheme()` — never hardcode Tailwind colors (`bg-white`, `bg-slate-900`) for structural components
- Icons from `lucide-react` only — no custom SVG
- i18n via `useLanguage()` → `t("namespace.key")` — no hardcoded strings
- Path alias: `@/` maps to project root (`vite.config.ts` + `tsconfig.json`)
- 4 themes: `slate-light`, `deep-space`, `cyber-terminal`, `royal-purple`
- Full design spec: `ecos_frontend/GEMINI.md`

## Key Cross-Cutting Facts

- Backend API base: `http://localhost:8080/api/v1/...`
- Frontend dev: `http://localhost:3000` (proxies `/api` and `/datanet` to backend)
- Smoke test: `node ~/ecos-tests/data-workbench-smoke.mjs`（如需，需要从 Windows 侧适配；目前 ecos-tests 目录在 Windows 检出中不存在）
- Knowledge base refresh: `cd ecos-kb && python scripts/scan_all.py`
- Sub-project AGENTS.md files: `ecos_backend/AGENTS.md` (backend arch & rules), `ecos-kb/AGENTS.md` (KB queries)

## What Not To Do

- Don't create new Maven modules (baseline is 13)
- Don't add new Docker containers (compose image count is baselined)
- Don't change existing API paths or signatures — only additive changes
- Don't bypass `@Autowired` with `new` — always use constructor injection
- Don't delete columns/tables from the database schema

# 项目概述

ECOS（Enterprise Cognitive Operating System）企业认知操作系统。核心链路：数据治理 → 知识图谱 → LLM Agent 部署。微服务 v2 架构（7 独立 JAR：gateway:8080 + 5 service:sysman/datanet/buszhi/dccheng/aiming + workspace:18090）：引擎层五对象（土D/金I/水K/木C/火W）+ 服务层四转化（格致诚明）+ 横切层（security 护 / runtime 器）。PostgreSQL 16 为主存储，Neo4j 5（enterprise 档）+ Doris（ultimate 档）按档位叠加。

# 工程环境

.hermes/env.md

# 技术栈

- 前端组件：react19,typescript,vite6,tailwind4,express-bff,hashrouter,lucide-react
- 后端组件：springboot3.2,mybatis,fat-jar-gateway,maven-3-editions,archunit

# 项目规范

- 架构铁律：.trae/rules/架构铁律.md (v1.3, 宪法)
- 后端开发规范：.trae/rules/后端开发规范.md
- 前端开发规范：.trae/rules/前端开发规范.md
- Git提交规范：.trae/rules/Git提交规范.md
- 数据库访问规范：.trae/rules/数据库访问规范.md (v1.0, IR/DR/EN/ST 共 23 条红线)
- 数据湖存储分层规范：.trae/rules/数据湖存储分层规范.md (v1.2, 五层 + zone)
- 文档编写规范：.trae/rules/文档编写规范.md (v1.0, R1-R13)
- 文档目录规范：.trae/rules/文档目录规范.md (v2.0, 14 一级目录)
- 开发环境登录凭据：.trae/rules/开发环境登录凭据.md (v1.0, admin/admin123)

# 工程结构

ECOS/（Windows D:\workspace\javaprojects\ECOS）
├─ ecos_backend/  Java 17 / SB 3.2.2 微服务 v2 (7 JAR)，入口 gateway/GatewayApplication.java
│  ├─ gateway/       唯一 Spring Boot 启动器（端口 8080，聚合全部模块）
│  ├─ common/        common-api（PipelineEvent/ICopilotService 等共享契约）+ common-impl
│  ├─ engine/        五对象引擎（api/impl/boot 三模块制）：
│  │  ├─ security-engine  护·横切 18081 认证/授权/审计/脱敏/ABAC(OPA)
│  │  ├─ data-engine      土·D 18082 数据源/管道/血缘/DQ
│  │  ├─ ontology-engine  金·I 18083 本体建模/实体/关系
│  │  ├─ kb-engine        水·K 18086 KG存储/检索/RAG/规则CRUD
│  │  ├─ cognitive-engine 木·C 18089 因果推理/情景推演
│  │  └─ ai-engine        火·W 18084 Agent/Loop/Memory/LLM
│  ├─ service/     四转化服务层：ge(D→I) zhi(I→K) cheng(K→C) ming(K→W)
│  ├─ runtime/     器·横切底座：runtime-access(统一Driver) / runtime-task(调度) / runtime-monitor / llm-gateway
│  └─ sysman/      系统管理（IAM/菜单/审计 + GlobalExceptionHandler）
├─ ecos_frontend/  React 19 + Vite 6 + TS + Tailwind 4，Express BFF，端口 3000，HashRouter
├─ ecos-docker/    docker-compose：PG 16(5432) / Neo4j 5(7474,7687) / MinIO(9000) / OPA(8181)
├─ ecos-kb/        Python 脚本生成 API 索引与 schema（python3 scripts/scan_all.py）
├─ ecos-tests/     Playwright 冒烟（node data-workbench-smoke.mjs，需前后端同跑）
├─ docs/           分层文档：1-sysman ~ 7-integration + ARCHITECTURE-RULES.md（架构宪法）
└─ ecos-git-repos/ pipeline/ontology 数据 git 占位

后端模块基线 13 个（不加新 Maven 模块）；Docker 容器基线已定（不加新容器）。

# 其他约束

1. 编译用 mvn install（非 compile），Gateway 从 .m2 加载旧 JAR；重命名模块须删 .m2 旧 artifact
2. 新增 Controller 必过三滤波器：VersionPrefixRewriteFilter 映射 + SecurityConfig permitAll + ClearanceInterceptor 豁免（双路径 /api/v1/ 与 /api/ 各写一遍）
3. 安全一律走 security-engine REST（RLS/列过滤/脱敏/OPA 裁决/审计），禁止引擎内重复实现；security 不可用时默认 DENY
4. 基础设施 Driver/LLM 调用/调度/监控一律收敛 runtime（runtime-access / llm-gateway / runtime-task / runtime-monitor），禁止各自封装
5. DB 铁律：MyBatis + schema 只加不删，Flyway 禁用；跨引擎不操作对方表，cognitive 不新增 DB 表
6. 前端铁律：禁止硬编码 Tailwind 颜色与中文字符串，图标仅 lucide-react，组件 ≤800 行，每 Tab 独立文件
7. Windows 环境：启动用 `_win_tasks/start-gateway.ps1`（推荐）或 `start-gateway.bat`（⚠️已知 JWT 解析问题），清端口用 `Get-NetTCPConnection`，Docker 用 Windows Docker Desktop 的 `docker` CLI
8. PMO 指令开头必须引用架构铁律；原子任务 = 单文件 + curl 验收；单指令 ≤5 Task
9. 一条代码三套发布：standard(PG) / enterprise(+Neo4j) / ultimate(+Doris)，按 Maven profile 切换
10. API 只增不改：既有路径与参数签名不可变更

