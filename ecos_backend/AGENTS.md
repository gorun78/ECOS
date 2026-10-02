# ECOS (Enterprise Cognitive Operating System) — Backend

> Java 17 + Spring Boot 3.2.2 + MyBatis + PostgreSQL | Maven multi-module | **微服务 v2 (7 独立 JAR)**
> 代码路径 (Windows): `D:\workspace\javaprojects\ECOS\ecos_backend\` (2026-09-09 起)
> 架构宪法: `.trae/rules/架构铁律.md` v2.0 (2026-09-28) + `docs/40-实现/legacy-plans/current-plan.md` v1.4（历史目标结构，Phase 8 批次再对齐）+ 铁律 附录 A
> 数据库规范: `.trae/rules/数据库访问规范.md` v1.2 (2026-09-28, IR/DR/EN/ST/MC 共 30 条红线 + ST03-A 列级加密豁免登记)
> 数据湖规范: `.trae/rules/数据湖存储分层规范.md` v2.0 (数据域二分 + 五层 + 知识层双形态 + 版本×分层矩阵)
> 需求基线: `docs/20-需求/PRD-00-ECOS平台需求规格说明书-2026-09-28.md`（84 项 REQ）+ `docs/20-需求/需求检视报告-2026-09-28.md` §十二（Q1~Q14 用户裁决，约束性输入）

## 产品定位

**ECOS** = 企业级认知操作系统。核心链路：**数据治理 → 知识图谱 → 大模型 Agent 落地**。

一套代码三套发布（**生效机制 = yml `spring.profiles.active`；Maven profile 仅用于依赖裁剪** — 铁律 v2.0 §3.1 / Q5 裁决）：

| 版本 | 数据库 | 适用 |
|------|--------|------|
| standard (默认) | PostgreSQL | 中小企业；图谱形态由 PG 表承载，**不得实启 Neo4j**（ST02） |
| enterprise | PostgreSQL + Neo4j | 中型企业，因果链 >3 层启用图谱 |
| ultimate | PostgreSQL + Neo4j + Doris ∨ ClickHouse（`dw.olap.engine` 二选一） | 大型企业，单表 >100 万行启用列存 |

## Architecture (v2 微服务态)

### 7 个独立可部署 JAR

```
┌────────────────────────────────────────────────────────────────┐
│  nginx / 浏览器 (对外)                                        │
└──────────────┬─────────────────────────────────────────────────┘
               ▼
      gateway :8080   (monolith fat-JAR, 组织/认证/限流 facade)
      ├── 扫 services.* + engine.* + workspace.* (双跑兼容)
      ├── 扫 sysman.* + buszhi.* + runtime.*
      └── 60+ excludeFilters (防同路径 Controller 冲突)
               │
    ┌──────────┼──────────┬───────┐
    ▼          ▼          ▼       ▼
services/sysman:18081  services/datanet:18082
(IAM/菜单/审计/脱敏)    (数据源/管道/DQ/血缘)
    │
    └─→ services/buszhi:18083 (本体/工作流)
        services/dccheng:18086 (KB/知识图谱/RAG/规则)
        services/aiming:18084 (Agent/Loop/LLM + 认知木C)
        workspace:18090 (场景层, 调全部 5 service)
```

**端口隔离铁律 (ADR-7)**：service 端口**仅内网可达**，前端/BFF 走 gateway :8080。

### 模块目录树（过渡态 + 附录 A 目标态）

```
ecos_backend/
├── pom.xml
├── runtime/
│   ├── common-api/          ★ P8-A 迁入 (原顶层 common/common-api), 核心契约 (ApiResponse/PipelineEvent/IEngine)
│   ├── runtime-core/        核心工具 (瘦身目标: 100 文件)
│   ├── runtime-access/      基础设施 Driver 统一封装
│   ├── runtime-monitor/     监控
│   ├── runtime-task/        任务调度
│   ├── runtime-event/       Kafka 事件总线 (PMO-50 新增, 可降级内存 fallback)
│   └── llm-gateway/         LLM 统一网关
├── engine/                    (六引擎不变, api/impl/boot 三模块)
├── services/                  (v2 5 microservice)
│   ├── sysman/                 封装 security + IAM (:18081, 业务 app)
│   │   └── impl/               ★ P8-C 内阁目录 (原顶层 sysman/ 整目录): sysman-api + sysman-impl(263 文件) + sysman-boot
│   ├── datanet/                封装 data-engine
│   ├── buszhi/                 封装 ontology-engine (金·I)
│   │   └── impl/              ★ P8-B 内阁目录, 原顶层 buszhi/buszhi-impl (23 工作流类 library)
│   ├── dccheng/                封装 kb-engine (水·K)
│   ├── aiming/                 封装 ai-engine + cognitive-engine + llm-gateway (火·W + 木·C)
│   └── agent-service/          ★ 保留 (Aiming 双容器演化保留, 见 AGENTS.md)
├── workspace/                 (附录 A 目标: 保留顶层独立)
│   ├── workspace-impl/
│   └── workspace-service/     :18090 (场景层)
├── gateway/                   (顶层, :8080, 组织/认证 facade)
└── service/{ge,zhi,cheng,ming}/   (四转化 API 文档占位, 非 Maven)
```

### Phase 8 — 模块结构调整 (2026-09-11 启动, 按 `docs/plans/cleanup-checklist.md` Sprint)

> **原则**：保 artifact 稳定 (groupId/artifactId 不变) · 仅移动物理目录。零 import/dependency 方变动。

#### 已完成
- **P8-A**: 顶层 `common/common-api` → `runtime/common-api`. 45 个 .java 包名 `com.chinacreator.gzcm.common.*` 不变, 20 处 POM 依赖零改
- **P8-B**: 顶层 `buszhi/` (buszhi-impl library) → `services/buszhi/impl/`. 5 个依赖方 artifact 稳定, 全量 reactor 绿
- **P8-C**: 顶层 `sysman/` (sysman-api/impl/boot 3 子模块) → `services/sysman/impl/`. 263 个 java 包名 `com.chinacreator.gzcm.sysman.*` 不变, 15 处外部依赖零改 (gateway / services/sysman / services/datanet / services/aiming / ontology-engine-{impl,api} / security-engine-{impl,boot,api} / data-engine-impl / ai-engine-impl / kb-engine-impl / workspace-service)

### 模块依赖方向 (ArchUnit 守卫, 铁律 §0.3.1)

```
workspace → (REST) → services/* → (Maven dep) → engine-impl → engine-api → common-api
```

**禁**：workspace 直引 engine-impl；engine → service；横向 services/* ↔ services/*；@Autowired 走 new。

### 五引擎 + 一护 + 四转化

| 层 | 代号 | 组件 |
|---|---|---|
| 引擎·土 D | data-engine | `services/datanet` 封装 |
| 引擎·金 I | ontology-engine | `services/buszhi` 封装 |
| 引擎·水 K | kb-engine | `services/dccheng` 封装 |
| 引擎·木 C | cognitive-engine | `services/aiming` 封装 |
| 引擎·火 W | ai-engine | `services/aiming` 封装 |
| 横切·护 | security-engine | `services/sysman` 封装 |
| 服务·四转化 | ge/zhi/cheng/ming | 寄居 engine-impl 对应子包 |

## Key Files

- `gateway/GatewayApplication.java` — monolith 启动器, `@ComponentScan` + 60+ excludeFilters
- `services/sysman/src/main/java/.../SysmanServiceApplication.java` — :18081 启动器
- `services/datanet/src/main/java/.../DatanetServiceApplication.java` — :18082
- `workspace/workspace-service/src/main/java/.../WorkspaceServiceApplication.java` — :18090
- `runtime/common-api/` — 核心契约 (P8-A 迁入)
- `gateway/src/main/resources/db/migration/` — Flyway 禁用, 只读历史

## Build

```powershell
# 全量构建 (Windows 原生, 2026-09-09 起)
& "D:\JavaProjects\env\apache-maven-3.9.11\bin\mvn.cmd" -f D:\workspace\javaprojects\ECOS\ecos_backend\pom.xml clean install -DskipTests

# 单 service 构建 (仅依赖)
& "D:\JavaProjects\env\apache-maven-3.9.11\bin\mvn.cmd" -f D:\workspace\javaprojects\ECOS\ecos_backend\pom.xml -pl services/sysman -am install -DskipTests

# 启动 gateway（唯一 Spring Boot 启动器；_win_tasks 无 start-gateway.ps1，此名系历史遗留）
powershell -NoProfile -ExecutionPolicy Bypass -File D:\workspace\javaprojects\ECOS\_win_tasks\start-backend.ps1 -Modules gateway -Profile enterprise
```

**根目录 `_win_tasks/` 脚本清单**（位于仓库根 `ECOS/_win_tasks/`，非 `ecos_backend/_win_tasks/`——后者已移出仓库，不得再引用；只增不改，禁止为新任务临时创建 — 铁律 §5.1#14。当前实存 8 个脚本文件）:
| 脚本 | 用途 |
|------|------|
| `preflight.ps1` | 启停前环境体检：工具链路径、JWT pem、DEEPSEEK key、docker 容器、端口占用、JAR 是否已构建（`-SkipDocker -SkipJars` 可跳过） |
| `start-backend.ps1` | 启动 v2 JAR（`-Modules` 13 选 N：7 个 v2 服务 + 6 个 `*-boot` 调试引擎，boot 端口自动 +1000）；自动注入 JWT_PRIVATE_KEY + DEEPSEEK_API_KEY，清端口冲突，日志在 `_win_tasks/logs/` |
| `stop-backend.ps1` | 按 ECOS 端口白名单（8080/18081-18086/18090/19xxx）杀监听进程；`-WithFrontend` 附带 3000 |
| `start-frontend.ps1` | 前端 dev 启动（缺 node_modules 先 npm install；`-Build` 走生产构建） |
| `check-legacy-modules.ps1` | 校验 v1 遗留模块引用是否残留 |
| `db-migration-lint.ps1` | SQL 迁移脚本 lint（schema 只加不删；实跑 15 项检查，基线 0 FAIL / 8 WARN，IR01/ST03/ST06/R9 属人工审查，脚本头部已声明） |
| `docs-migration.ps1` / `.sh` | docs 五类目录迁移辅助 |

> 旧名 `fe_win.bat` / `gateway_run.bat` / `backend_resume.cmd` / `start-gateway.ps1` 均已不存在（WSL 时代脚本归档 `_legacy_wsl/`）。

## Database

- **PostgreSQL 16**，库 `sys_man`，本地 `postgres/postgres`
- **MyBatis**（Hibernate/JPA auto-config 已排除）
- **Flyway 已禁用** (`spring.flyway.enabled: false`)；迁移脚本单源目录 = `gateway/src/main/resources/db/migration/`（铁律 v2.0 §3.1，Q7 裁决；禁分域另立目录）
- Mapper XMLs: `classpath*:mapper/*.xml`
- **Schema 归属（ST07 权威口径，取代"Phase 4-2 按 service 切流"旧路线）**：控制域只有 **5 引擎 schema**（`ecos_data`/`ecos_ontology`/`ecos_knowledge`/`ecos_ai`/`ecos_cognitive`）+ **主控制 schema**（现 `public`，目标 `ecos_control`）；业务域五层数据落 `ecos_dw`（+ MinIO / Doris∨ClickHouse）。
  - 存量 `ecos_sysman`/`ecos_datanet`/`ecos_buszhi`/`ecos_dccheng`/`ecos_aiming`/`ecos_security`/`ecos_infra`/`ecos_dq` = **knownLegacy**：**只停写、不迁不删**（R9）；新表一律按 ST07 落 5 引擎或主控制 schema。
  - 空壳 schema（零表）`ecos_aiming`/`ecos_buszhi`/`ecos_datanet`/`ecos_dccheng` 为按服务命名分库的历史残留，**处置=不再写入**（DROP 属 R9 禁项）。
  - 依据：`docs/40-实现/数据库现状盘点与schema归属映射-2026-09-28.md`（665 表 / 16 schema 实测）
- ~~数据迁移脚本: `ecos-docker/scripts/phase4_schema_init.{sql,sh}`~~（**文件不存在**，该路线已按 ST07 作废；`ecos-docker/scripts/` 实存仅 `oag-e2e-smoke.ps1` / `start-aiming.ps1`）

## 环境 (Windows)

| 项 | 版本/路径 |
|---|---|
| JDK | `C:\Program Files\Microsoft\jdk-17.0.17.10-hotspot` |
| Maven | `D:\JavaProjects\env\apache-maven-3.9.11` |
| JWT | `C:\Users\guoro\.config\ecos\jwt-private-key.pem` |
| DEEPSEEK | `C:\Users\guoro\.hermes\profiles\gorunkol\.env` |
| Docker | Windows Docker Desktop (PG/Neo4j/MinIO/Kafka/ZK/OPA) |

## Hard Rules (ArchUnit 守卫)

1. **workspace → services → engine → common 单向依赖**
2. **Controller 只调本 service 的 Service**，跨 service 走 REST (Kafka 走 `KafkaTopics` 常量)
3. **不新增 Maven module**（基线 = **7 个部署 JAR + 49 个根 reactor 构建模块**，2026-09-28 Q5 裁决口径、同日 H11-T2 后由 48 增至 49；枚举措见根 `AGENTS.md`「工程结构」与 `pom.xml`；仅例外：新引擎/新横切底座需 PMO 审批）
4. **不新增 Docker container**（compose image 基线已 fixed）
5. **Controller 禁止直用 JdbcTemplate** — 必过 Service
6. **服务端口仅内网可达** (ADR-7) — gateway/nginx 是唯一对外入口
7. **所有异常日志带堆栈**，全局异常处理在 gateway `GlobalExceptionHandler`

## Current Status (2026-09-11)

- **后端**: ✅ 编译绿 (7 JAR) + gateway :8080 UP
- **5 service 双跑**: ✅ sysman:18081 UP, datanet:18082 UP, buszhi:18083 UP, aiming:18084 UP, dccheng:18086 UP — JAR 已 build, 按需启动
- **workspace 场景层**: ✅ :18090 UP，实测包视角 = `scenario`/`controller`/`workbook`/`knowledge`/`security`/`audit`/`service`/`exception`/`trace` 九子包 + 顶层 `QueryHistoryService` / `QueryRecord`（`scenario` 域为本册场景工作台主承载，余域属 R-29 "存量端点暴露面待迁回属主"登记件，见 roots 层 `docs/30-设计/详细设计-07-…md` §7.3 C164/C165、W182）
- **PG Schema 预备**: ✅ 5 schema 建库验证
- **Phase 推荐下线单档**: standard (PG only) — 默认 profile
- **目标结构附录 A 对齐**: ⏸ Phase 8 Sprint 独立 Sprint 做 (3 个 Sprint 分级) — 不动代码保持可运行, 文档先对齐 (架构铁律 v2.0 + 本 AGENTS)

## Key Cross-Cutting

- **文件额外说明**: `services/sysman/impl/sysman-boot/`（P8-C 后路径，原顶层 `sysman/sysman-boot/`）保留作 library，被 `engine/security-engine-impl` + `engine/ontology-engine-impl` POM 依赖，**不删除**
- **`services/agent-service`** 保留作 aiming 双容器隔离被 AimingServiceApplication `excludeFilters` 引用，**不删除**
- **`service/{ge,zhi,cheng,ming}`** 是四转化 API 文档占位 (每个只有 `AGENTS.md` 不含 `pom.xml`)，**不删除**，全局 API 目标见 `ecos_backend/docs/four-transformations/`
