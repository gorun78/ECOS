# REVIEW_REPORT_REAL — Pipeline Wave 1~6 按 severity 排序

> artifact: pipeline@0377176..a4fd24e (12 commits)
> 审查维度: 正确性 / 可维护性 / 性能 / 一致性 / 跨引擎纪律

---

## P0（红线拦截）

**无。** 未发现阻断交付的 P0 问题。

---

## P1（高优先级缺陷 — 建议下一 wave 修复）

### P1-1: `PipelineDebugService.executeNode` 与 `PipelineExecutionService.executeNode` 分支不一致（架构铁律 §4.8.2 违反）

- **文件:行**: `data-engine-impl/.../pipeline/PipelineDebugService.java:369-381`
- **描述**: 调试链 `executeNode` switch 仅有 6 分支（SOURCE_JDBC/CSV/REST/CDC/TRANSFORM_SQL/OUTPUT_OBJECT），而生产执行链 `PipelineExecutionService.executeNode` 有 9 分支（多了 TRANSFORM_UDF/JOIN/SINK）。调试链对 TRANSFORM_UDF/JOIN/SINK 节点会直接抛 `ValidationException("type", "不支持的节点类型: ...")`。
- **影响**: 用户在画布上添加了 UDF/JOIN/SINK 节点并进入调试会话后，step/continue 到这些节点必然失败。与 P2-01 §4.1 节点类型全集矛盾。
- **suggestions**: 在 `PipelineDebugService.executeNode` 中补齐 3 分支（可调用 `PipelineExecutionService` 的对应方法或同构实现），保持三方枚举一致。
- **现实感**: 当前 debug 场景以 SELECT/DML 为主，UDF/JOIN/SINK 调试是后续 wave 需求，但端点契约已对外暴露（NodePalette 已渲染 9 节点），用户可触发。

### P1-2: `PipelineDebugService.topologicalSort` 预构建 nodeMap/children 顺序 bug

- **文件:行**: `data-engine-impl/.../pipeline/PipelineDebugService.java:873-879`
- **描述**: `topologicalSort` 在 `for (PipelineNode node : nodes)` 循环内同时构建 `nodeMap` 和 `children`。对节点 A（`dependsOn=[B]`），若 B 尚未加入 `nodeMap`（即 B 在 A 之后出现），则 `if (!nodeMap.containsKey(dep)) continue;` 会跳过 B→A 边的入度累加，导致 A 的入度为 0 而非 1 — 拓扑序错误。
- **影响**: 当 DAG 中子节点在 dependsOn 父节点之前出现时（数据库 `ORDER BY created_at` 不保证拓扑序），调试链可能产生错误的执行顺序或误判循环。
- **对比**: `PipelineExecutionService.topologicalSort`（行 994-1046）**先**构建 nodeMap（节点循环）**再**遍历边，不存在此问题。
- **suggestions**: 拆成两遍循环 — 第一遍填充 nodeMap，第二遍构建 children/inDegree。与 Production 版对齐。
- **现实感**: 实际 DAG 中节点创建顺序通常 source→transform→sink，概率较低，但非确定性 bug。

### P1-3: `PipelineSecurityService.evaluateExecute` 硬编码 `role: "system"`

- **文件:行**: `data-engine-impl/.../pipeline/PipelineSecurityService.java:148`
- **描述**: ABAC 裁决请求体中 `input.role` 硬编码为 `"system"`，未从当前请求上下文（JWT / AuthFilter）提取真实用户角色。
- **影响**: OPA 策略无法基于调用者身份做差异化裁决（如区分 admin 与 analyst），所有 pipeline execute 以同一角色裁决。
- **suggestions**: 从 `SecurityContextHolder` 或请求 header 提取当前用户角色，传入 `input.role`。若当前 gateway 链路未透传用户身份，至少在注释中标注已知限制。
- **现实感**: 当前 OPA 策略为 rbac + `pipeline.execute` 全局规则，短期影响有限，但违反 ABAC 语义。

### P1-4: `PipelineExecutionService.executeSink` 逐行 INSERT 性能风险

- **文件:行**: `data-engine-impl/.../pipeline/PipelineExecutionService.java:600-610`
- **描述**: SINK 节点对 rows 逐行调用 `insertRowViaConnector`（每行一次 `JdbcConnector.executeSql`），无 batch prepareStatement。`batchSize` 参数被计算但未实际使用。
- **影响**: 100 万行 SINK → 100 万次 SQL 往返。标准档（PG）下 10 分钟+；external JDBC 可能更长。
- **suggestions**: 短期：注释标注 JdbcConnector 限制（已部分标注）；中期：推动 `runtime-access` 的 JdbcConnector 增加 `batchExecute` 方法。
- **现实感**: 代码注释已说明 "JdbcConnector 当前仅支持 executeSql，无批量 prepareStatement 路径"，属架构层约束而非本 wave 新引入。

---

## P2（中低优先级 — 技术债务 / 可改进）

### P2-1: `PipelineGitService.versions()` 硬编码 Windows 路径 `/tmp/ecos-git/{id}`

- **文件:行**: `data-engine-impl/.../service/PipelineGitService.java:155`
- **描述**: `File gitDir = new File("/tmp/ecos-git/" + id)` — Windows 下 `/tmp/` 不存在（除非 WSL 路径），git 仓库永远 "不存在" → 始终返回空列表。
- **影响**: Windows 原生开发环境下 `GET /api/v1/engine/data/pipeline/git/versions/{id}` 恒返回 `[]`，GitVersionPanel 前端永远显示 empty state。
- **suggestions**: 将 base path 提取为 `@Value("${dw.pipeline.git.base-path:/tmp/ecos-git}")`，Windows 环境配置为 `D:/ecos-git` 或 `C:/Users/guoro/.ecos/git`。

### P2-2: `PipelineGitService.commit()` / `pull()` 使用 `SELECT *` 违反 SQL 规范

- **文件:行**: `data-engine-impl/.../service/PipelineGitService.java:27, 63`
- **描述**: `jdbc.queryForMap("SELECT * FROM ecos_pipeline_task WHERE id = ?", id)` — 后端规范明确禁止 `select *`。
- **suggestions**: 改为 `SELECT id, name, git_url, git_branch, yaml_content, updated_at FROM ecos_pipeline_task WHERE id = ?`。

### P2-3: `PipelineGitController` 6 端点使用 `Map<String, Object>` 作为请求/响应体

- **文件:行**: `data-engine-impl/.../controller/PipelineGitController.java:20-107`
- **描述**: 所有端点入参 `@RequestBody Map<String, Object>`、出参 `ApiResponse<Map<String, Object>>` — 违反后端规范 "接口不能使用 Map 作为入参和出参"。
- **影响**: 类型安全缺失，前后端契约靠文档约定。
- **suggestions**: 新增 `PipelineGitCommitDTO` / `PipelineGitPullDTO` / `PipelineGitBranchDTO` 强类型。
- **现实感**: Git 版本列表端点（`listVersions`）返回 `List<String>` 已合规；commit/pull/branch 3 组端点为低流量操作，短期可接受。

### P2-4: `PipelineTaskExecutor.getStatus()` 硬编码返回 RUNNING

- **文件:行**: `data-engine-impl/.../pipeline/PipelineTaskExecutor.java:106-112`
- **描述**: `getStatus(taskId)` 始终返回 `TaskStatus(RUNNING)`，不反映实际 Pipeline 执行状态（可能已 COMPLETED/FAILED）。
- **影响**: runtime-task 状态查询对 Pipeline 任务永远显示 "执行中"，前端轮询 `getTaskStatus` 可能误判。
- **suggestions**: 从 `ecos_pipeline_execution` 读取最新 executionId 的状态，或从 `PipelineRepository.findExecutionById` 查询。
- **现实感**: 当前 `PipelineController.executeDefinition` 用 `fillTaskStatus` 二次查询覆盖，实际影响有限。

### P2-5: `PipelineDebugService` 1095 行 vs 后端规范 ≤800 行

- **文件:行**: `data-engine-impl/.../pipeline/PipelineDebugService.java` (全文件)
- **描述**: 文件 1095 行，超出后端规范 800 行上限。包含会话管理、节点执行、日志、拓扑排序、VO 序列化 5 个职责。
- **suggestions**: 将 `executeNode` 相关方法（行 369-531）抽到 `PipelineDebugNodeExecutor` 类；`topologicalSort` 可与 `PipelineExecutionService` 共享（提取到 `PipelineTopoSorter` 工具类）。

### P2-6: `PipelineSecurityService.restTemplate` 未配置超时

- **文件:行**: `data-engine-impl/.../pipeline/PipelineSecurityService.java:51`
- **描述**: `private final RestTemplate restTemplate = new RestTemplate()` 无 `SimpleClientHttpRequestFactory` 超时配置（默认无限等待）。
- **影响**: security-engine 不可达时，`@Async auditWrite` 线程可能长时间阻塞（虽不阻塞主流程，但线程池耗尽风险）。
- **suggestions**: 配置 `setConnectTimeout(3000)` / `setReadTimeout(5000)`。
