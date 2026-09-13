# PMO-57 T5 运行时双态回归验证记录

> 批次: PMO-57（docs/04-本体/PMO-57-本体引擎跨引擎反向依赖断链.md）T4 尾部 + T5 双态回归
> 基线 commit: `9d4bc24`（T1-T3 反向依赖物理断链 + 强类型契约回归 buszhi 单点）
> 验证日期: 2026-09-13（本地时区 +08:00）| 分支: `feat/ontology-workbench-wave-b`
> 验证人: 全栈开发工程师（fullstack-implementer）

---

## 0. 结论速览

| 项 | 结果 | 判定 |
|:--|:--|:--|
| T4 尾（BuszhiServiceApplication 锁死排除） | 9d4bc24 已修正注释，**0 代码改动** | 跳过（本文件仅 docs） |
| T5.A gateway fat-JAR（:8080） | **启动失败**（Ambiguous mapping，**非 PMO-57 引入**，为既有 E-C 提交 `85e41a9` 遗留） | BLOCKER |
| T5.A curl（/workflows 列表 / instances） | HTTP 000（无服务） | 阻断 |
| T5.B buszhi standalone（:18083） | **启动成功**（59.9s）；两端点路由注册、返回 403 认证拦截（**非 404**） | PASS（端点存在性验证） |
| Ambiguous mapping / bean 双注册（buszhi 侧日志） | 0 命中 | PASS |
| 本批产物 | 仅本文档 1 个 docs commit | — |

**关键判定**：T5.A 失败的冲突双方为
`com.chinacreator.gzcm.engine.cognitive2.controller.CognitiveEngineOpenHealthController#health()`
与
`com.chinacreator.gzcm.engine.ai.controller.AiEngineStatusController#health()`，
同映射 `GET /api/v1/engine/cognitive/health`。
**两者与 PMO-57 workflow 断链零涉关**（见 §4 根因与归属）。T5.B 反而正向核证了
9d4bc24 的加载分离：fat-JAR 侧 buszhi Controller 加载路径已开、standalone 侧 workflow 端点独立存活。

---

## 1. 启动脚本（spot-check 实际路径）

| 用途 | 实际路径 | 说明 |
|:--|:--|:--|
| gateway fat-JAR | `D:\workspace\javaprojects\ECOS\_win_tasks\start-gateway.ps1` | 加载 JWT_PRIVATE_KEY（PEM 文件）+ DEEPSEEK_API_KEY，fat-JAR 启动 enterprise 档 :8080 |
| buszhi standalone | **无独立启动脚本**（`_win_tasks/` 下仅 buszhi*.log 历史日志） | 本次用 inline java -jar + 仓库内 gateway 开发公钥绕过 JWT 配置 gap（见 §5） |

JAR 时间窗核对（新鲜度）：
- `gateway-1.0.0-SNAPSHOT.jar` 2026-09-13 17:38:18（晚于 9d4bc24，140.5 MB）
- `buszhi-service-1.0.0-SNAPSHOT.jar` 2026-09-13 17:34:32（晚于 9d4bc24，97.9 MB）
- `buszhi-impl-1.0.0-SNAPSHOT.jar` 2026-09-13 17:33:41

启动前端口核查：:8080 / :18083 均 0 监听。

---

## 2. T5.A gateway fat-JAR 态 :8080

### 2.1 启动过程

- 17:54:58 起（start-gateway.ps1），JWT len=1703 加载成功，DEEPSEEK len=35 加载成功
- 4 项基础设施探活全 UP：PostgreSQL :5432 / Neo4j :7687 / MinIO :9000 / OPA :8181
- OPA policy 'rbac' 推送成功
- **17:55:09 Context refresh 开始 → 17:56:23 `Application run failed`，首启耗时 ≈ 74.7s（未成功）**
- 崩溃后进程自行退出，:8080 无监听残留，curl 全部 HTTP 000

### 2.2 首启日志摘行（开场关键行）

```
[OK] JWT_PRIVATE_KEY loaded from C:\Users\guoro\.config\ecos\jwt-private-key.pem (len=1703)
[OK] DEEPSEEK_API_KEY loaded (len=35)
  PostgreSQL   :5432  UP
  Neo4j        :7687  UP
  MinIO        :9000  UP
  OPA          :8181  UP
[OK] OPA policy 'rbac' pushed (pipeline.execute allow)
[START] gateway fat-JAR, profile=enterprise, port 8080

2026-09-13T17:55:09.107+08:00  INFO 133812 --- [ecos-gateway] [           main] c.c.gzcm.gateway.GatewayApplication      : Starting GatewayApplication using Java 17.0.17 with PID 133812
2026-09-13T17:55:09.125+08:00  INFO 133812 --- [ecos-gateway] [           main] c.c.gzcm.gateway.GatewayApplication      : The following 1 profile is active: "enterprise"
2026-09-13T17:55:41.509+08:00  INFO 133812 --- [ecos-gateway] [           main] w.s.c.ServletWebServerApplicationContext : Root WebApplicationContext: initialization completed in 31797 ms
2026-09-13T17:55:49.753+08:00  INFO 133812 --- [ecos-gateway] [           main] c.c.g.sysman.security.JwtTokenProvider   : JwtTokenProvider initialized with RS256 keys
2026-09-13T17:56:23.803+08:00 ERROR 133812 --- [ecos-gateway] [           main] o.s.boot.SpringApplication               : Application run failed
```

> 注：gateway 侧 JwtTokenProvider 初始化成功（RS256），**其 JWT 配置自洽**——
> 唯一启动失败点是下文 §4 的 mapping 冲突。

### 2.3 curl 回归（端点未通 — 无服务可验）

| # | 命令 | 结果 |
|:--|:--|:--|
| 1 | `curl.exe -s -m 6 http://localhost:8080/api/v1/ecos/workflows` | `HTTP_STATUS=000`（无响应，端口无监听） |
| 2 | `curl.exe -s -m 6 http://localhost:8080/api/v1/ecos/workflows/instances` | `HTTP_STATUS=000`（无响应，端口无监听） |

**curl 结论不采信为 PMO-57 回归**：服务从未到 Running 态，属启动中断连，非接口缺陷。

### 2.4 Ambiguous mapping / bean 冲突 grep（gateway 崩溃日志）

- `Ambiguous mapping`：**1 处**（即崩溃根因，内容见 §4）
- `expected single candidate bean but found 2`：**0 处**

---

## 3. T5.B buszhi standalone 态 :18083

### 3.1 启动

- 启动方式：`_win_tasks/` 无 buszhi 启动脚本 → 幂等 inline 起：
  `java -jar services/buszhi/target/buszhi-service-1.0.0-SNAPSHOT.jar --spring.profiles.active=enterprise`
  + JWT_PRIVATE_KEY（env）+ **`--jwt.public-key`（仓库内 gateway yml 的开发公钥 base，非密）**
- 首次未带公钥启动失败（`Missing key encoding`），带公钥后 **18:40:22 启动成功，`Started BuszhiServiceApplication in 59.934 seconds`**
- ApplicationAvailability：LivenessState=CORRECT / ReadinessState=ACCEPTING_TRAFFIC

**降级操作说明**：buszhi `application.yml` 完全没有 `jwt.*` 段（git 历史：该 yml 最后改动为
`2172519` P2 补 buszhi UP / `748950e` Phase 2 六部署单元物化），而 `JwtTokenProvider`（sysman-impl 封装）
对 standalone 场景强制要求 `jwt.public-key`（gateway yml 内置了该公钥，故 gateway 路径成形）。
**这属 PMO-49 微服务拆分遗留的 buszhi yml 配置 gap，与 PMO-57 断链无关**——已走 inline 命令参数解决，
不改任何源码 / yml。
（后续 runtime 窗口建议：为 buszhi standalone 补 `jwt.public-key` 段，或确立 service 公钥统一注入规范。）

### 3.2 curl 回归

| # | 命令 | HTTP | 响应体（原 sessions） |
|:--|:--|:--|:--|
| 1 | `curl.exe -s http://localhost:18083/api/v1/ecos/workflows` | 403 | `{"code":403,"message":"未认证用户无权访问该资源（需要 L1 及以上）","timestamp":1789296108133,"success":false}` |
| 2 | `curl.exe -s http://localhost:18083/api/v1/ecos/workflows/instances` | 403 | `{"code":403,"message":"未认证用户无权访问该资源（需要 L1 及以上）","timestamp":1789296108511,"success":false}` |

**判定**：两端点返回**标准 403 认证拦截**（非 404 Not Found）→ 已证明：
- buszhi 侧 `com.chinacreator.gzcm.buszhi.workflow.controller.WorkflowController` 的
  `@RequestMapping` **已正确注册到 :18083 standalone**（若有 404/端点缺失，返回体将不同）；
- 安全域（Clearance/RLS）链路正常；
- PMO-57 断链目标态"workflow 单点归 buszhi"在 standalone 侧得到运行时核证。
本窗口无可用 token（`_win_tasks/` 下无现成有效 token 文件，不硬造登录），403 视为端点存在性验证通过，
**功能性 200 + JSON 体断言留待 gateway 可用后有 token 的窗口补跑**。

### 3.3 冲突 grep（buszhi 日志）

- `Ambiguous mapping`：**0 命中**
- `expected single candidate bean but found 2`：**0 命中**
- 含 `CognitiveEngineOpenHealthController` / `AiEngineStatusController` 的日志行：0 命中（该类群在 buszhi standalone 下根本不存在）
- buszhi 的 REGEX 排除针对的 ontology 侧 `WorkflowController`：恒不命中，符合预期（类已物理删除）

---

## 4. T5.A BLOCKER：gateway 启动失败根因与归属

### 4.1 直接根因（原 sessions 摘录）

```
2026-09-13T17:56:23.803+08:00 ERROR 133812 --- [ecos-gateway] [main] o.s.boot.SpringApplication : Application run failed
org.springframework.beans.factory.BeanCreationException: Error creating bean with name
 'org.springframework.security.config.annotation.web.configuration.WebSecurityConfiguration':
 Injection of autowired dependencies failed
...
Caused by: org.springframework.beans.factory.BeanCreationException: Error creating bean with name
 'requestMappingHandlerMapping' ...: Ambiguous mapping. Cannot map
 'ecosCognitiveEngineOpenHealthController' method
com.chinacreator.gzcm.engine.cognitive2.controller.CognitiveEngineOpenHealthController#health()
to {GET [/api/v1/engine/cognitive/health]}: There is already 'aiEngineStatusController' bean method
com.chinacreator.gzcm.engine.ai.controller.AiEngineStatusController#health() mapped.
```

### 4.2 归属判定（⚠️ 非 PMO-57 责任）

| 冲突方 | 文件 | 权威提交 | 时间 |
|:--|:--|:--|:--|
| `CognitiveEngineOpenHealthController`（/api/v1/engine/cognitive /health） | cognitive-engine-impl/.../cognitive2/controller/CognitiveEngineOpenHealthController.java | `85e41a9` fix(cognitive): 批次 E-C 开放 cognitive 公开 health 端点 | 2026-09-13 **10:51 +01:00**（本地 11:51），为 9d4bc24 祖先 |
| `AiEngineStatusController`（/api/v1/engine/cognitive /health） | ai-engine-impl/.../ai/controller/AiEngineStatusController.java | `e4036df` 20260720 | 7 月 20 日（未变） |

归属断言：
1. 两个 Controller 均**不在** 9d4bc24 改动清单（git show 9d4bc24 --name-status 复核：本轮只动 ontology-engine-impl、buszhi-impl、gateway/GatewayApplication、BuszhiServiceApplication）。
2. 9d4bc24 对 `GatewayApplication` 的改动**仅为删除 "排除 buszhi 侧 WorkflowController" 的 `ASSIGNABLE_TYPE` 条目**（git show diff 复核），不涉及 cognitive2 / ai-engine 任何排除项。
3. 冲突双方 bean（`ecosCognitiveEngineOpenHealthController`、`aiEngineStatusController`）在 85e41a9 落地那刻起即在 classpath 上并存，若按同样 classpath 启动，`85e41a9 ~ 9d4bc24` 之间 fat-JAR gateway 应同样触发 `Application run failed`（无网关路径可路由该端点）。工作区未提交变更（cognitive/forecast、workspace/scenario 等）与本次 gateway 启动崩溃**无因果关系**（未进 JAR、未进 classpath）。

### 4.3 订正（对本批早期判断的校正）

PMO-57 前批曾断言"三模块 mvn 全绿（`-DskipTests`）⇒ gateway 应可启动"——**该推论不成立**：
`-DskipTests` 跳过测试即跳过了 `ArchitectureTest` 等映射冲突检测。`85e41a9`（E-C 批次）配套未含
gateway 三滤波器（VersionPrefixRewriteFilter / SecurityConfig permitAll / ClearanceInterceptor 豁免）
复核与**路径冲突扫描**。**此为本批 T5 检出，评价记录 V4 强制力的真实价值**（铁律 5.1 #5）。

### 4.4 建议归属与处理（供 PM 裁定，**不越权处置**）

1. **优先**：`CognitiveEngineOpenHealthController` 迁移至独立专属前缀（如 `/api/v1/cognitive-engine/health`）或与 `appservice` 同步评审变更 `AiEngineStatusController` 的 `/api/v1/engine/cognitive` 挂载点。
2. **兼容**：`GatewayApplication` 对冲突控制器加 `excludeFilters`（需保留 `appservice` 侧 `/status` 等端点，且 `gateway-application-exclude-tokens` 特性）——但**需同步评估前端是否已消费该路径**，避免硬断。
3. **预防**：`mvn install` 的门检增补"Ambiguous mapping 预检"（如 ArchUnit 规则或在 gateway 侧加一个 wiring 监听捕获 handler 注册失败告警）——
   留 PM 与 QA 回复后续 PMO，不属本批。

---

## 5. T4 尾部：BuszhiServiceApplication 锁死加载范围 — 0 改动

9d4bc24 已修正该类 `@ComponentScan` REGEX 排除注释（2026-09-13 注明 "ontology 侧副本已物理删除，
REGEX 恒不命中、保留"），与 T4 目标态一致。本批 T4 = **0 代码改动**，零 diff，直接进入 T5。

（file: `ecos_backend/services/buszhi/src/main/java/com/chinacreator/gzcm/services/buszhi/BuszhiServiceApplication.java`，lines 51-58 已锁定）

---

## 6. 清理与现场状态

- gateway 进程 133812：自行退出（崩溃后续飞轮驱动），无残留监听 :8080=0
- buszhi 进程 133924：StopCommand 强杀，:18083=0
- 未触及其他 java 进程（19156/40348/41132/42676/42984/115900/135572 保持不变）
- 未改任何源码、未改任何配置

---

## 7. 本批投交与遗留

- 本 commit：仅本文件 1（docs 沉淀）
- 前批 9d4bc24 3 项 mvn 全绿
- **遗留 #1**：gateway :8080 当前不可用（cognitive health 冲突，`85e41a9` 遗留）。**与 PMO-57 无关**，
  建议立 PMO-57a 或并入认知 engine 下一批次处置；在此之前 T5.A 的 workflow 端点 200 curl 断言**未能完成**，
  待 gateway 可启动后补跑。
- **遗留 #2**：buszhi standalone 的 `application.yml` 缺 `jwt.public-key` 段（P2 微服务拆分遗留）。
  非本批范围，建议补入 buszhi/或 sysman 的 yml 基线。

---

## 附：关键时间轴（本地 +08:00）

| 时间 | 事件 |
|:--|:--|
| 17:33 | buszhi-impl / buszhi-service / gateway fat-JAR 全量重建完成 |
| 17:54:58 | gateway 启动（start-gateway.ps1，PID 133812） |
| 17:56:23 | gateway `Application run failed`（Ambiguous mapping，cognitive2 vs ai-engine health） |
| 18:29:22 | buszhi 首次启动失败（Missing key encoding）—— postProcessing 阶段 |
| 18:39:22 | buszhi 二次启动（inline --jwt.public-key 补 git 仓内开发公钥） |
| 18:40:22 | buszhi standalone 启动成功，耗时 59.9s |
| 18:42 | curl /api/v1/ecos/workflows[:instances] → 403（认证拦截，端点存在） |
| 18:52 | buszhi 进程强杀，18083 清理 |

---

*TRACE: PMO-57 T5 · 生成于 2026-09-13 · node: ecosystem fullstack-implementer*
