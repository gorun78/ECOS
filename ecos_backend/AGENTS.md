# ECOS (Enterprise Cognitive Operating System) — Backend

> Java 17 + Spring Boot 3.2.2 + MyBatis + PostgreSQL | Maven multi-module | **微服务 v2 (7 独立 JAR)**
> 代码路径 (Windows): `D:\workspace\javaprojects\ECOS\ecos_backend\` (2026-09-09 起)
> 架构宪法: `.trae/rules/架构铁律.md` v1.3 (2026-09-11) + `docs/21-runtime/legacy-plans/current-plan.md` v1.4 + 附录 A 目标结构
> 数据库规范: `.trae/rules/数据库访问规范.md` v1.0 (2026-09-22, IR/DR/EN/ST 共 23 条红线)

## 产品定位

**ECOS** = 企业级认知操作系统。核心链路：**数据治理 → 知识图谱 → 大模型 Agent 落地**。

一套代码三套发布（Maven Profile 控制）：

| 版本 | 数据库 | 适用 |
|------|--------|------|
| standard (默认) | PostgreSQL | 中小企业 |
| enterprise | PostgreSQL + Neo4j | 中型企业，因果链 >3 层启用图谱 |
| ultimate/flagship | PostgreSQL + Neo4j + Doris | 大型企业，单表 >100 万行启用列存 |

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
        services/aiming:18084 (Agent/Loop/LLM)
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
│   └── llm-gateway/         LLM 统一网关
├── engine/                    (六引擎不变, api/impl/boot 三模块)
├── services/                  (v2 5 microservice)
│   ├── sysman/                 封装 security + IAM (:18081, 业务 app)
│   │   └── impl/               ★ P8-C 内阁目录 (原顶层 sysman/ 整目录): sysman-api + sysman-impl(263 文件) + sysman-boot
│   ├── datanet/                封装 data-engine
│   ├── buszhi/                 封装 ontology-engine (金·I)
│   │   └── impl/              ★ P8-B 内阁目录, 原顶层 buszhi/buszhi-impl (23 工作流类 library)
│   ├── dccheng/                封装 kb-engine + cognitive-engine
│   ├── aiming/                 封装 ai-engine + llm-gateway
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
| 引擎·木 C | cognitive-engine | `services/dccheng` 封装 |
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
mvn -f pom.xml -pl services/sysman -am install -DskipTests

# 启动 gateway (monolith)
powershell -NoProfile -ExecutionPolicy Bypass -File D:\workspace\javaprojects\ECOS\_win_tasks\start-gateway.ps1

# 启动 service (需设 $env:JWT_PRIVATE_KEY)
& java -jar services/sysman/target/sysman-service-1.0.0-SNAPSHOT.jar --spring.profiles.active=standard
```

`_win_tasks/` 脚本 (禁止随意增, 架构铁律 §5.1#14):
| 脚本 | 用途 |
|------|------|
| `start-gateway.ps1` | 后端 gateway 启动 (JWT + DEEPSEEK + infra check) |
| `fe_win.bat` | 前端 dev 启动 |
| `gateway_run.bat` | mvn spring-boot:run 方式启动 (开发调试) |
| `backend_resume.cmd` | 后端 stop/resume |

## Database

- **PostgreSQL 16**，库 `sys_man`，本地 `postgres/postgres`
- **MyBatis**（Hibernate/JPA auto-config 已排除）
- **Flyway 已禁用** (`spring.flyway.enabled: false`)，迁移脚本历史在 gateway
- Mapper XMLs: `classpath*:mapper/*.xml`
- **Schema 切分 (Phase 4 预备)**：已建 5 业务 schema (`ecos_sysman` / `ecos_datanet` / `ecos_buszhi` / `ecos_dccheng` / `ecos_aiming`) — 各 service yml 的 `currentSchema` 待 Phase 4-2 切流
- 数据迁移脚本: `ecos-docker/scripts/phase4_schema_init.{sql,sh}`

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
3. **不新增 Maven module**（v2 基线 = 已 fixed 7 service，仅例外：新引擎/新横切底座需 PMO 审批）
4. **不新增 Docker container**（compose image 基线已 fixed）
5. **Controller 禁止直用 JdbcTemplate** — 必过 Service
6. **服务端口仅内网可达** (ADR-7) — gateway/nginx 是唯一对外入口
7. **所有异常日志带堆栈**，全局异常处理在 gateway `GlobalExceptionHandler`

## Current Status (2026-09-11)

- **后端**: ✅ 编译绿 (7 JAR) + gateway :8080 UP
- **5 service 双跑**: ✅ sysman:18081 UP, datanet:18082 UP, buszhi:18083 UP, aiming:18084 UP, dccheng:18086 UP — JAR 已 build, 按需启动
- **workspace 场景层**: ✅ :18090 UP, 封装 Twin/Alert/Task/EngineTask/EcosKnowledgeGraph (P3-C 迁入)
- **PG Schema 预备**: ✅ 5 schema 建库验证
- **Phase 推荐下线单档**: standard (PG only) — 默认 profile
- **目标结构附录 A 对齐**: ⏸ Phase 8 Sprint 独立 Sprint 做 (3 个 Sprint 分级) — 不动代码保持可运行, 文档先对齐 (v1.1 铁律 + 本 AGENTS)

## Key Cross-Cutting

- **文件额外说明**: `sysman/sysman-boot/` 保留作 library，被 `engine/security-engine-impl` + `engine/ontology-engine-impl` POM 依赖，**不删除**
- **`services/agent-service`** 保留作 aiming 双容器隔离被 AimingServiceApplication `excludeFilters` 引用，**不删除**
- **`service/{ge,zhi,cheng,ming}`** 是四转化 API 文档占位 (每个只有 `AGENTS.md` 不含 `pom.xml`)，**不删除**，全局 API 目标见 `ecos_backend/docs/four-transformations/`
