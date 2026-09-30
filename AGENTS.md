# ECOS — Root Monorepo Guide

> **ECOS** = Enterprise Cognitive Operating System. Core pipeline: data governance → knowledge graph → LLM agent deployment.
> Windows path: `D:\workspace\javaprojects\ECOS\`（2026-09-09 起已从 WSL 迁移到 Windows 原生开发）

## Repo Structure

| Directory | Stack | Entry | Notes |
|-----------|-------|-------|-------|
| `ecos_backend/` | Java 17 / Spring Boot 3.2.2 / MyBatis / PG | `gateway/GatewayApplication.java` | Microservice v2 (7 JARs). See `ecos_backend/AGENTS.md` |
| `ecos_frontend/` | React 19 / Vite 6 / Tailwind 4 / TypeScript | `src/main.tsx` → `App.tsx` | Express BFF (`server.ts`, gateway-first). See「代理双轨」below |
| `ecos-docker/` | Docker Compose | `docker-compose.yml` | 仅基础设施 8 容器：PG 16, Neo4j 5, MinIO, OPA, Kafka/ZK + 一次性 kafka-init（建 topic）+ Grafana。v1 legacy 应用容器拆离至 `docker-compose.app-v1-legacy.yml`（默认不跑） |
| `ecos-tests/` | **Playwright 工程**（`@playwright/test` 1.63 + TS） | `playwright.config.ts`（project `api` / `app`）→ `tests/{api,e2e}/*.spec.ts` | 2026-09-30 按裁决建成：原 3 个 `.mjs` 冒烟已迁移为 11 条用例并**删除、不留双轨**（裁决见 `docs/20-需求/需求检视报告-2026-09-28.md` §十二 补记）。跑法 `cd ecos-tests && npx playwright test`（凭据仅从 `ecos-tests/.env` 读 `ECOS_USER`/`ECOS_PASS`，禁写入用例；模板 `.env.example`）。首跑实测 **10 passed / 1 failed**，失败项 = 管道 execute 因 OPA 无策略 fail-closed，见报告 §14.6 |
| `_win_tasks/` | PowerShell 运维脚本 | `preflight.ps1` / `start-backend.ps1` / `stop-backend.ps1` / `start-frontend.ps1` | 开发环境启停入口（2026-09-28 重建；工作树实存 9 个脚本文件 = 4 入口 + 5 治理辅助，见下表。旧 WSL 脚本在 `_legacy_wsl/`；旧 `ecos_backend/_win_tasks/` 已移出仓库） |
| `docs/` | Markdown | README.md | Lifecycle taxonomy: 10-架构 / 20-需求 / 30-设计 / 40-实现 / 50-测试 + migration map (legacy dirs purged 2026-09-28) |
| `ecos-git-repos/` | Git data dirs | — | `.gitkeep` placeholders for pipeline/ontology data |

启动命令（PowerShell 一行式，**不创建临时脚本**）：

```powershell
# 0) 环境体检（JDK/Maven/Node/JWT/DEEPSEEK/Docker 容器/端口/JAR，失败退出）
powershell -NoProfile -ExecutionPolicy Bypass -File D:\workspace\javaprojects\ECOS\_win_tasks\preflight.ps1

# 1) 后端（微服务 v2，7 个 JAR，可只启 gateway）
powershell -NoProfile -ExecutionPolicy Bypass -File D:\workspace\javaprojects\ECOS\_win_tasks\start-backend.ps1 -Modules gateway,sysman,datanet,buszhi,aiming,dccheng,workspace -Profile enterprise

# 2) 前端（Express BFF :3000 + vite）
powershell -NoProfile -ExecutionPolicy Bypass -File D:\workspace\javaprojects\ECOS\_win_tasks\start-frontend.ps1

# 3) 停止（只杀 ECOS 端口白名单上的监听进程）
powershell -NoProfile -ExecutionPolicy Bypass -File D:\workspace\javaprojects\ECOS\_win_tasks\stop-backend.ps1 -WithFrontend

# 构建
& "D:\JavaProjects\env\apache-maven-3.9.11\bin\mvn.cmd" -f D:\workspace\javaprojects\ECOS\ecos_backend\pom.xml clean install -DskipTests
```

**_win_tasks/ 脚本清单**（只增不改，禁止为新任务临时创建）：
| 脚本 | 用途 |
|------|------|
| `preflight.ps1` | 启停前环境体检：工具链路径、JWT pem、DEEPSEEK key、docker 容器、端口占用、JAR 是否已构建（`-SkipDocker -SkipJars` 可跳过） |
| `start-backend.ps1` | 启动 v2 JAR（`-Modules` 13 选 N：7 个 v2 服务 + 6 个 `*-boot` 调试引擎，boot 端口自动 +1000）；自动注入 JWT_PRIVATE_KEY + DEEPSEEK_API_KEY，清端口冲突，日志在 `_win_tasks/logs/` |
| `stop-backend.ps1` | 按 ECOS 端口白名单（8080/18081-18086/18090/19xxx）杀监听进程；`-WithFrontend` 附带 3000 |
| `start-frontend.ps1` | 前端 dev 启动（缺 node_modules 先 npm install；`-Build` 走生产构建） |
| `check-legacy-modules.ps1` | 校验 v1 遗留模块引用是否残留 |
| `db-migration-lint.ps1` | SQL 迁移脚本 lint（schema 只加不删；实跑 15 项检查，基线 0 FAIL / 8 WARN，IR01/ST03/ST06/R9 属人工审查，脚本头部已声明） |
| `route-rewrite-lint.ps1` | 网关路由重写表 lint（校验 `VersionPrefixRewriteFilter` 正向 10 + 反向 13 = 23 条的 target/source 是否真有 Controller 服务，并打印 permitAll 匿名面；基线 `fail=4`（存量待裁 H9-T6b）/ `EXIT=2`） |
| `docs-migration.ps1` / `.sh` | docs 五类目录迁移辅助 |

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

**Env**: AI 功能用 `DEEPSEEK_API_KEY`（start-backend.ps1 按「DEEPSEEK」条注入链加载，本机推荐 `~\.config\ecos\.env`；`GEMINI_API_KEY` 系旧文档虚构，代码零引用）；`DISABLE_HMR=true` 关热更新。
**Node**: `C:\Program Files\nodejs\node.exe`
**代理双轨（重要）**：
- vite dev proxy（`vite.config.ts`）：18 条引擎前缀直连各 service（18081 security/audit、18082 data、18083 ontology、18084 aiming+knowledge+cognitive、18086 rules/kb、18089 world-model、18090 workspace），兜底 `/api`、`/datanet`、`/cases` → gateway :8080。
- Express BFF（`server.ts`，ADR-10/PMO-B）：默认全部 `/api/*` → `GATEWAY_URL`（:8080）；仅 `ECOS_LOCAL_DIRECT_SERVICE_PROXY=true` 时解锁定向直连（默认 false，仅本地调试）。生产走 BFF，vite 直连只对 dev 生效。

## Three Editions (yml profile 生效 + Maven profile 裁剪)

> **生效机制 = yml `spring.profiles.active`；Maven profile 仅用于依赖裁剪**（铁律 v2.0 §3.1，2026-09-28 用户裁决 Q5）。历史别名 `flagship` 全仓作废。

| Edition | DB | Maven 裁剪 / 运行时生效 |
|---------|----|-------------|
| standard (default) | PostgreSQL | `-Pstandard` / `--spring.profiles.active=standard`（图谱形态走 PG 表，**禁实启 Neo4j** — ST02） |
| enterprise | PostgreSQL + Neo4j | `-Penterprise` / `--spring.profiles.active=enterprise` |
| ultimate | PostgreSQL + Neo4j + Doris ∨ ClickHouse | `-Pultimate` / `--spring.profiles.active=ultimate`（列存由 `dw.olap.engine=doris\|clickhouse` 二选一） |

## Database

- PostgreSQL 16, database `sys_man`, local creds `postgres/postgres`
- MyBatis (not JPA — Hibernate auto-config excluded)
- Flyway disabled (`spring.flyway.enabled: false`)
- Schema rule: only add columns/tables, never drop — **例外（铁律 v2.0 §3.1）**：设计资产历史版本表可 DROP（首批 `ecos_ontology_version`/`ecos_object_version`/`ecos_agent_version`/`sys_rule_version`，前置=备份 + 代码读写先切 Git 链路）
- **Schema 归属（ST07，单库 5+1 为权威）**：控制域 = `ecos_data`/`ecos_ontology`/`ecos_knowledge`/`ecos_ai`/`ecos_cognitive` 五引擎 schema + 主控制 schema（现 `public`，目标 `ecos_control`）；业务域五层数据落 `ecos_dw`（+ MinIO / Doris∨ClickHouse）。**不存在"每 service 一 schema"路线**（`ecos_sysman`/`ecos_datanet`/`ecos_buszhi`/`ecos_dccheng`/`ecos_aiming` 属 knownLegacy，只停写不迁移）
- DDL 迁移脚本单源目录：`ecos_backend/gateway/src/main/resources/db/migration/`（铁律 v2.0 §3.1，禁分域另立目录）
- 金额列唯一形态 `NUMERIC(p,s)`；主键 `VARCHAR(36)` 应用侧生成 UUID，DDL 禁 `gen_random_uuid()`（MC01/MC02 v1.2）

## Infrastructure (Windows Docker Desktop)

基础设施容器运行在 **Windows Docker Desktop**（非 WSL 内部），数据卷持久化为 `ecos_pgdata` / `ecos_neo4jdata` / `ecos_miniodata` 等命名卷。

```powershell
# 从 Windows PowerShell 直接操作（docker CLI 指向 Docker Desktop server）
docker ps --filter "name=ecos-"          # 查看容器状态
docker start ecos-postgres ecos-neo4j ecos-minio ecos-opa ecos-kafka ecos-zookeeper  # 启动全部
docker stop  ecos-postgres               # 停止单个
docker exec ecos-postgres psql -U postgres -d sys_man -c "SELECT 1"  # 验证 PG
```

compose 文件：
- `ecos-docker/docker-compose.yml` —— 仅基础设施，8 个 container_name（`grep -c container_name` 实测）：`ecos-postgres` (5432) / `ecos-zookeeper` (2181) / `ecos-kafka` (9092) / `ecos-kafka-init`（一次性容器，建 `ecos.*` 8 个 topic：identity/catalog/ontology/object/workflow/agent/knowledge/audit）/ `ecos-neo4j` (7474,7687) / `ecos-minio` (9000) / `ecos-opa` (8181) / `ecos-grafana` (3001)。**微服务 v2 不在容器里跑**（Windows 本机 7 JAR）。
- `ecos-docker/docker-compose.app-v1-legacy.yml` —— v1 legacy 应用容器（api-gateway + 9 服务），2026-09-28 拆离，默认不启用；恢复方式见文件头注释。

## Windows 迁移注意事项（2026-09-09 起）

- **路径**: 所有操作的根目录为 `D:\workspace\javaprojects\ECOS\`，不再使用 WSL 路径
- **启动脚本**: 统一使用 `_win_tasks/` 下的 4 个入口脚本（`preflight` / `start-backend` / `stop-backend` / `start-frontend`，见表格），**禁止为新任务创建临时脚本**（架构铁律 #14）。旧 WSL/无 JWT 脚本已归档 `_legacy_wsl/`
- **操作原则**: 探测用 RunCommand 内联命令，调试用 logger debug，不用独立脚本
- **JWT**: `C:\Users\guoro\.config\ecos\jwt-private-key.pem`（start-backend.ps1 自动读 pem 并注入 `JWT_PRIVATE_KEY` 环境变量，PEM 换行以字面量 `\n` 传递）
- **DEEPSEEK**: `DEEPSEEK_API_KEY` 注入链（start-backend.ps1，后者覆盖前者）：进程环境变量 → `~\.config\ecos\.env`（本机推荐落点，与 JWT pem 同目录）→ `~\.hermes\profiles\gorunkol\.env`（历史路径，2026-09-28 实证本机已不存在）→ 仓库根 `.env`。均缺失时仅 AI 功能降级，平台可启动（preflight 会给 WARN）
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
- Frontend dev: `http://localhost:3000` (vite 直连 service + BFF gateway-first，见「代理双轨」)
- E2E（Playwright 工程，2026-09-30 建成）: `cd ecos-tests && npx playwright test`（仅 7 后端 jar + 前端同跑；`--project=api` 只需 gateway，`--project=app` 需前端 :3000；原 3 个 `.mjs` 已迁移为 spec 并删除）
- Sub-project AGENTS.md files: `ecos_backend/AGENTS.md` (backend arch & rules)。（`ecos-kb/` 目录不存在，相关条目为历史遗留）

## What Not To Do

- Don't create new Maven modules (baseline = 7 个部署 JAR + 49 个根 reactor 构建模块，见「工程结构」Q5 裁决口径枚举)
- Don't add new Docker containers (compose image count is baselined)
- Don't change existing API paths or signatures — only additive changes
- Don't bypass `@Autowired` with `new` — always use constructor injection
- Don't delete columns/tables from the database schema — exception (铁律 v2.0 §3.1): design-asset history version tables may be DROPped after backup and code has switched to the Git archive path

# 项目概述

ECOS（Enterprise Cognitive Operating System）企业认知操作系统。核心链路：数据治理 → 知识图谱 → LLM Agent 部署。微服务 v2 架构（7 独立 JAR：gateway:8080 + 5 service:sysman/datanet/buszhi/dccheng/aiming + workspace:18090）：引擎层五对象（土D/金I/水K/木C/火W）+ 服务层四转化（格致诚明）+ 横切层（security 护 / runtime 器）。PostgreSQL 16 为主存储，Neo4j 5（enterprise 档）+ Doris（ultimate 档）按档位叠加。

# 工程环境

.hermes/env.md

# 技术栈

- 前端组件：react19,typescript,vite6,tailwind4,express-bff,hashrouter,lucide-react
- 后端组件：springboot3.2,mybatis,fat-jar-gateway,maven-3-editions,archunit

# 项目规范

- 架构铁律：.trae/rules/架构铁律.md (v2.0, 宪法)
- 后端开发规范：.trae/rules/后端开发规范.md (v1.1, 新增 §九 runtime 公共底座 + §十 配置/字典单表分组 + §十一 版本×Git 归档；附 CD-1~CD-3/VG-1~VG-6 待整改)
- 前端开发规范：.trae/rules/前端开发规范.md (v1.1, 新增 §六 i18n + §七 主题模板 + §八 移动端 + §九 配置字典消费 + §十 收口 + §十一 版本归档 UI)
- Git提交规范：.trae/rules/Git提交规范.md
- 数据库访问规范：.trae/rules/数据库访问规范.md (v1.2, IR/DR/EN/ST/MC 共 30 条红线 + ST03-A 豁免登记子条款；数据域二分 + Schema 归属 5+1 单库权威 + 多库兼容 + 金额列 NUMERIC(p,s) 白名单)
- 数据湖存储分层规范：.trae/rules/数据湖存储分层规范.md (v2.0, 数据域二分 + 五层 + 知识层双形态 + 版本×分层矩阵)
- 文档编写规范：.trae/rules/文档编写规范.md (v1.2, R1-R13)
- 文档目录规范：.trae/rules/文档目录规范.md (v3.1, 生命周期五类目录)
- 开发环境登录凭据：.trae/rules/开发环境登录凭据.md (v1.0, admin/admin123)

# 工程结构

ECOS/（Windows D:\workspace\javaprojects\ECOS）
├─ ecos_backend/  Java 17 / SB 3.2.2 微服务 v2 (7 JAR)，入口 gateway/GatewayApplication.java
│  ├─ gateway/            :8080 唯一 monolith fat-JAR（组织/认证 facade，聚合全部模块）
│  ├─ engine/             六引擎（api/impl/boot 三模块制）：security-护18081 / data-土D18082 / ontology-金I18083 / ai-火W18084 / cognitive-木C18089 / kb-水K18086
│  ├─ runtime/            器·横切底座 7 模块：common-api / runtime-core / runtime-access / runtime-task / runtime-monitor / runtime-event / llm-gateway
│  ├─ services/           v2 五微服务：sysman:18081 / datanet:18082 / buszhi:18083 / aiming:18084 / dccheng:18086（sysman/impl、buszhi/impl 为 P8 内阁目录；`agent-service` 经 PMO-74 H11-T2 挂回根 reactor，不再是遗留；api-gateway/identity-service/ontology-service 三个幽灵 pom 经 Q3 批次 A 归档至 `ecos_backend/archive/legacy/services-ghost/`）
│  ├─ workspace/          场景层 :18090（workspace-impl + workspace-service）
│  ├─ service/            四转化 API 文档占位 {ge,zhi,cheng,ming}（非 Maven，仅 AGENTS.md）
│  ├─ database/           seed.sql + V150 schema 统一/回滚脚本
│  ├─ scripts/            迁移/校验辅助脚本（sh/py/sql）
│  ├─ archive/legacy/     归档遗留（Dockerfile/deploy.sh 等）
│  ├─ _logs/              本机 dev 日志
│  └─ pom.xml             根聚合 POM（11 个 <module> 条目，profile 内重复声明同一集合）
├─ ecos_frontend/  React 19 + Vite 6 + TS + Tailwind 4，Express BFF，端口 3000，HashRouter
├─ ecos-docker/    docker-compose：PG 16(5432) / Neo4j 5(7474,7687) / MinIO(9000) / OPA(8181) / Kafka(9092)+ZK(2181) / 一次性 kafka-init（建 8 topic）/ Grafana(3001)；v1 应用容器另拆 docker-compose.app-v1-legacy.yml
├─ ecos-sql/       多库 DDL：mysql/（01~08 分 schema 脚本 8 个）+ postgresql/（00_init_database.sql）
├─ _win_tasks/     Windows 启停入口 4 脚本（preflight/start-backend/stop-backend/start-frontend）+ 治理辅助脚本；日志 _win_tasks/logs/
├─ _legacy_wsl/    WSL 时代遗留脚本（已归档，不再使用）
├─ ecos-tests/     Playwright 测试工程（`package.json` + `playwright.config.ts` + `tests/{api,e2e}/*.spec.ts`，11 用例；原 3 个 `.mjs` 已迁移并删除，裁决见需求检视报告 §十二 补记，落地与首跑证据见 §14.6）
├─ tools/          仓根独立工具（Q4 裁决 2026-09-28 落位）：`ecos-mcp-server.py` 从 `ecos_backend/engine/` 移出，不再混入引擎源码树
├─ docs/           生命周期五类：10-架构 / 20-需求 / 30-设计 / 40-实现 / 50-测试 + 迁移映射表（导航 docs/README.md）
└─ ecos-git-repos/ pipeline/ontology 数据 git 占位

后端模块基线（Q5 裁决可枚举口径，2026-09-28 实测，同日 PMO-74 H11-T2 后更新，2026-09-29 Q3 批次 A 后再更新）= **7 个部署 JAR**（gateway + services/{sysman,datanet,buszhi,aiming,dccheng} + workspace/workspace-service）+ **49 个根 reactor 构建模块**；全仓 `find ecos_backend -name pom.xml -not -path "*/target/*"` 实数 53 = 根聚合 1 + reactor 49 + 已归档遗留 3（`archive/legacy/services-ghost/{api-gateway,identity-service,ontology-service}`）。**该命令含 `archive/`，故排除归档后才是 50**：加 `-not -path "*/archive/*"` → **50**。复现命令 `mvn -o validate | grep -c '^\[INFO\] Building '`（= 50 行，含根聚合；Q3 归档前后各实测一次均为 50 / BUILD SUCCESS）。不加新 Maven 模块（门禁双处：`_win_tasks/check-legacy-modules.ps1` `baselineCount = 12` 与 `ArchitectureTest.baselineModules = 12`，二者必须同批改）；Docker 容器基线已定（compose 实数 8 容器，不加新容器）。

# 其他约束

1. 编译用 mvn install（非 compile），Gateway 从 .m2 加载旧 JAR；重命名模块须删 .m2 旧 artifact
2. 新增 Controller 必过三滤波器：VersionPrefixRewriteFilter 映射 + SecurityConfig permitAll + ClearanceInterceptor 豁免（双路径 /api/v1/ 与 /api/ 各写一遍）
3. 安全一律走 security-engine REST（RLS/列过滤/脱敏/OPA 裁决/审计），禁止引擎内重复实现；security 不可用时默认 DENY
4. 基础设施 Driver/LLM 调用/调度/监控/事件总线/Git 一律收敛 runtime（runtime-access / llm-gateway / runtime-task / runtime-monitor / runtime-event），禁止各自封装；配置与字典只经 sysman api 门面（单表分组 `config_group`/`subsystem`），DB 只存在用版本、历史版本走 Git
5. DB 铁律：MyBatis + schema 只加不删（例外：设计资产历史版本表可 DROP，见铁律 v2.0 §3.1），Flyway 禁用；跨引擎不操作对方表，cognitive 不新增 DB 表（**确定性计算产物落业务域 `APPLICATION`/`ecos_dw`，经 data-engine 写通道 — 铁律 v2.0 §0.6 + ADR-14**）
6. 前端铁律：禁止硬编码 Tailwind 颜色与中文字符串，图标仅 lucide-react，组件 ≤800 行，每 Tab 独立文件；移动端只复用 `useMediaQuery`/`useMobileSidebar`/`MobileDataTable`，版本/Git UI 走 `services/gitService.ts` 单一通道且只传 `repositoryId`
7. Windows 环境：先跑 `_win_tasks/preflight.ps1` 体检，再用 `_win_tasks/start-backend.ps1` 启 JAR（gateway 可单启；boot 调试引擎端口自动 +1000），清端口用 `Get-NetTCPConnection` 或 `stop-backend.ps1`，Docker 用 Windows Docker Desktop 的 `docker` CLI
8. PMO 指令开头必须引用架构铁律；原子任务 = 单文件 + curl 验收；单指令 ≤5 Task
9. 一条代码三套发布：standard(PG) / enterprise(+Neo4j) / ultimate(+Doris∨ClickHouse)，**yml profile 生效**、Maven profile 仅裁剪依赖
10. API 只增不改：既有路径与参数签名不可变更

