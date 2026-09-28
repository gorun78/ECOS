# ECOS 后端模块清单

> 来源: PMO-C | 日期: 2026-09-27 | 责任人: AI Agent
> 版本: v1.0
> 依据: 《ECOS 架构铁律》v1.6 §0.1 部署拓扑 + §0.3 引擎层 + §0.3.1 业务服务层

---

## 一、Reactor 模块（参与 `mvn -f ecos_backend/pom.xml` 构建）

| 模块 | Maven module 路径 | 类型 | 生产部署 | owner | 退出条件 |
|:--|:--|:--|:--|:--|:--|
| **gateway** | `gateway` | 可运行 JAR | ✅ 独立部署 :8080 | infra | 永久 |
| **runtime** (横切底座) | `runtime` | Library + Boot | ✅ 随 gateway fat jar 内嵌 | infra | 永久 |
| engine (聚合器) | `engine` | POM 聚合 | — | arch | 永久 |
| **data-engine** (土·D) | `engine/data-engine` | POM 聚合 (api/impl/boot) | ✅ 随 gateway fat jar | data-team | 永久 |
| **ontology-engine** (金·I) | `engine/ontology-engine` | POM 聚合 | ✅ 随 gateway fat jar | onto-team | 永久 |
| **kb-engine** (水·K) | `engine/kb-engine` | POM 聚合 | ✅ 随 gateway fat jar | kb-team | 永久 |
| **cognitive-engine** (木·C) | `engine/cognitive-engine` | POM 聚合 | ✅ 随 aiming :18084 部署 | cog-team | 永久 |
| **ai-engine** (火·W) | `engine/ai-engine` | POM 聚合 | ✅ 随 aiming :18084 部署 | ai-team | 永久 |
| **security-engine** (护) | `engine/security-engine` | POM 聚合 | ✅ 随 gateway fat jar | sec-team | 永久 |
| **sysman** | `services/sysman` | POM 聚合 + Lib | ✅ :18081 | sys-team | 永久 |
| **datanet** | `services/datanet` | 可运行 JAR | ✅ :18082 | data-team | 永久 |
| **buszhi** | `services/buszhi` | POM 聚合 | ✅ :18083 | onto-team | 永久 |
| **dccheng** | `services/dccheng` | 可运行 JAR | ✅ :18086 | kb-team | 永久 |
| **aiming** | `services/aiming` | 可运行 JAR | ✅ :18084 | ai-team+cog-team | 永久 |
| **workspace** | `workspace` | POM 聚合 (impl+service) | ✅ :18090 | app-team | 永久 |

### 运行模块（独立 JAR 部署）

| JAR | 端口 | 容器/进程 |
|:--|:--:|:--|
| gateway-1.0.0-SNAPSHOT.jar | :8080 | docker / 本地 java |
| datanet.jar | :18082 | docker ecos-data |
| buszhi.jar | :18083 | docker ecos-buszhi |
| aiming.jar | :18084 | docker ecos-ai |
| dccheng.jar | :18086 | docker ecos-kb |
| workspace-service.jar | :18090 | docker ecos-workspace |
| sysman-impl.jar | :18081 | 内嵌 gateway fat（非独立） |

### Library 模块（被引用、不独立部署）

| 模块 | 作用 |
|:--|:--|
| runtime (6 子: runtime-access / runtime-task / runtime-monitor / llm-gateway / common-api / common-security) | 横切底座 |
| engine/*-engine-api | 对外 REST 契约 |
| engine/*-engine-impl | 引擎业务实现 |
| engine/*-engine-boot | 引擎启动器（@SpringBootApplication） |
| services/sysman/impl/sysman-impl | sysman 业务逻辑 |
| workspace/workspace-impl | workspace 业务逻辑 |

---

## 二、Legacy / 已迁移目录（不参与 reactor、不部署）

| 目录 | 原归属 | 现状 | 退出条件 |
|:--|:--|:--|:--|
| `services/sysman/impl/sysman-boot` (旧) | 4-sub-module 遗留 | 已并入 `services/sysman/impl` | N/A（已不存在） |
| `engine/*/boot` 独立可部署 JAR | PMO-49/V1.4 前单体 8 JAR 态 | 已废，改由 gateway fat jar 内嵌 | 已完成 |
| `agent-service` | 旧独立 agent 微服务 | **不存在于 reactor**；功能已并入 ai-engine/agent-service submodule | 永久 Archived |
| `ontology-service` | 旧独立本体服务 | **不存在于 reactor**；功能已并入 ontology-engine | 永久 Archived |
| `identity-service` | 旧独立身份服务 | **不存在于 reactor**；功能已并入 sysman/security-engine | 永久 Archived |
| `api-gateway` | 旧独立网关 | **不存在于 reactor**；已由 `gateway` 替换（CMD rewrite `/api/v1/*` → `/api/*`） | 永久 Archived |

> ⚠️ 以上 4 个 legacy 名称在 `ecos_backend/` 下**无对应目录**（已物理清理），仅出现在 docker-compose 历史 references 和文档中。
> 如未来再现同名目录，CI（Task C3）将阻断构建。

---

## 三、依赖方向（单向）

```
workspace
    ↓ REST/Kafka（禁 Maven 依赖）
services (5)
    ↓ Maven dep (仅 *-engine-api + runtime)
engine (6)
    ↓ Maven dep
common (runtime + common-api + common-security)

runtime ← （被 engine/services 依赖，不反向依赖）
```

**禁止**：
- workspace → services/engine 的 Maven 依赖
- service A → service B 的 Maven 依赖（横向）
- runtime → engine/services/workspace 的反向依赖
- 新增 `<module>` 入 reactor（需 ADR）

---

## 四、CI 门禁（Task C3 + C4）

| 门禁项 | 验收命令 | 阻断级别 |
|:--|:--|:--|
| reactor module 白名单 | `mvn -f ecos_backend/pom.xml validate` | FAIL（阻断） |
| 无 legacy 目录入 reactor | `_win_tasks/check-legacy-modules.ps1` | FAIL（阻断） |
| 依赖方向 ArchUnit | `mvn -pl gateway -am test -Dtest='*Arch*'` | FAIL（阻断） |
| 前端 TS 类型 | `cd ecos_frontend && npm run lint` | FAIL（阻断） |
| 文档死链 | `git diff --check` | WARN |
