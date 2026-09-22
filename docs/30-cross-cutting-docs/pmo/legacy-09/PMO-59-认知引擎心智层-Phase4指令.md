# PMO-59 Phase 4 指令: 认知引擎心智层 — 人机干预与审计（两单串行：P4a 告警落库 → P4b 前端干预面板）

> **架构铁律**: 必须遵循 [ECOS架构铁律](../../.trae/rules/架构铁律.md)（§2.4 安全接入 / §2.5 runtime 公共底座补强而非自建 / §4 前端主题+i18n 铁律 / §5.1 禁止清单）
> 来源: 肖国荣 | 日期: 2026-09-16
> 责任人: fullstack-implementer（开发实现）/ PM项目经理（验收）/ code-artifact-reviewer（独立审查）
> 铁律: ① **LLM 零参与认知计算**（前端渲染与后端落库均零 LLM）；② 监控告警走 runtime-monitor、定时任务走 runtime-task、跨模块共享走 common-api（补强而非自建）；③ API 只增不改 + 000 硬编码 Tailwind 色值 / 0 硬编码中文
> 前序: P1/P2/P3 全部验收 PASS 并已合入 `release/v2.1-alpha`；P0 契约 + V127~V130 三表 + 心智层端点 + 反事实推演 + 时间回放 + 复盘聚合 + workspace SAFEGUARD 贯通

## §背景

本 Phase 对应外部方案层 5「人机干预与审计」，拆除 P2b 遗留的两处未闭环：

1. **P2b 残留风险 #1**：`IWarnLogService`（runtime-monitor）为纯内存态 `logStore`，**进程重启即失**，认知失效告警不落库 → 周一故障复盘无据可查。P4a 关闭该风险。
2. **人机干预入口缺失**：P3a/P3b 交付的反事实推演 / 时间回放 / 复盘聚合三个心智层只读端点，前端场景工作台 CognitionPanel 尚无入口 → P4b 补齐可视化。

**本指令拆两单串行**：
- **P4a 后端告警落库通道**：新表 + 落库分支 + 复盘口径对接
- **P4b 前端人机干预面板**：CognitionPanel 反事实/回放子区 + MentalReviewPanel 新面板 + i18n

**硬门禁**：P4a 未过验收（reviewer PASS + 全量编译绿 + curl/psql 通）不得启动 P4b。

## §环境基线（已核实，勿重复确认）

- 仓库根 `d:\workspace\javaprojects\ECOS`；当前分支 `release/v2.1-alpha`（HEAD=e480988），工作区干净
- `.working/`（19 文件）与 `_p3b_*.log` 为 **gitignore 的 P3b 临时残留，保持原样勿动勿提交**
- `_win_tasks/` 在本检出不存在；Gateway 启动等价命令见 §环境要点
- 三滤波器：`/api/v1/cognitive/**` 通配已覆盖（零新增登记）

## §实现决策（编码前裁定，验收按此核对）

1. **落库表 `ecos_warn_log`（V131）**：跟齐 V123~V130 风格（`id` PK + 审计六列 + `is_deleted`）；显式承载 `fault_context JSONB` 与 `review_tag`（复盘聚合键，与 `MentalEventPublisher.REVIEW_TAG` 同源）；`log_id` 唯一索引保幂等。
2. **接口只增不改**：`IWarnLogService` 新增 `warn(type, objid, objname, err, typeHander, faultContext, reviewTag)` **default 方法**（默认委托既有 5 参方法，向后兼容零破坏）；既有 5 个方法签名与语义不动。
3. **落库位置在 runtime-monitor 实现内**（铁律 §2.5 监控告警收敛 runtime-monitor，禁止引擎内自建）：`WarnLogServiceImpl` 保留内存 `logStore`（既有查询语义零变化），**只增持久化分支**；`JdbcTemplate` 构造器注入（`null` = 纯内存态向后兼容）；**落库异常 try/catch 打 WARN 不抛出**（不阻塞主流程）。
4. **装配点沿用 P2b 授权例外**：`CognitiveMentalConfig.cognitiveWarnLogService()` 由 cognitive 侧补装配 runtime 缺口（现有既成事实，本 Phase 仅补 `JdbcTemplate` 入参，通过 `ObjectProvider` 容错）。
5. **不新增告警查询端点**（T3 勘察裁定）：既有告警查询端点已存在（`GET /api/monitor/alerts` 统计 + `GET /api/v1/alerts` 列表），落库表可 psql 直查；新增 warn 专用端点属扩面，且 `AlertController` 注释已声明 `:8080` 不再提供该数据（由 workspace 降级路由），新增子路径存在路由遮蔽风险 → **只做落库，不新增端点**，勘察结论登记入 AGENTS.md。
6. **复盘口径对接（T4）**：`GET /api/v1/cognitive/mental-reviews` **纳入告警维度**为**只增键**（新增 `warnAlerts` 段 + `summary.warnAlerts` 计数），既有键与结构契约 0 变化；关联口径 = `ecos_warn_log.review_tag` 与复盘 `tag` 同源（`P2b-mental-layer-review`），只读 join，不改报告结构契约。
7. **P4b 前端零硬编码**：颜色全走 `useTheme()` styles，文案全走 `useLanguage()` t()（`scenario` namespace 已注册，zh-CN.json + en.json 双写），图标仅 lucide-react，Tab 独立文件且单文件 ≤800 行。
8. **P4b 数据流**：推演/回放/复盘均为只读 GET/POST 渲染，**0 LLM**；列表摘要与详情的契约按 P3a/P3b 已登记 DTO 消费（`assumptionRefs`/`excludedAssumptions`/四指标/replayedVersion/believedDistribution 等）。

## §禁止清单（继承铁律 §5.1 + PMO-59 Phase 1-3 沿用 + 本单特有）

1. 单指令 ≤5 Task 不许加塞；不跨 Phase 预创建（Phase 5 内容一律不碰）
2. 不改既有 API 路径与参数签名（只增不改）；不新建 Maven 模块/Docker 容器；不引 ML/Python/规则引擎
3. **LLM 零参与认知计算**（前端渲染与后端落库均零 LLM）
4. 定时/周期任务走 runtime-task；监控告警走 runtime-monitor（补强而非自建）；跨模块共享走 common-api
5. `mvn install` 非 compile；`"-Dmaven.test.skip=true"` 带引号；**编译前先查 8080 端口旧 gateway 进程并停掉**（多次实证 target 锁导致 clean 失败）
6. 前端铁律：0 硬编码 Tailwind 颜色（用 `useTheme()` styles）、0 硬编码中文（`useLanguage()` t()，i18n key zh-CN.json + en.json 双写）、图标仅 lucide-react、单文件 ≤800 行、Tab 独立文件
7. 写操作发 Kafka `ecos.audit`；`ApiResponse` 统一返回体；Controller 强类型 DTO（禁 Map 入参出参）；Store 层 0 `SELECT *`、全 `is_deleted=0`
8. 新 DDL 只加不删，编号 = 现有最大 +1（V130 已存在 → **V131**）；手工执行用 `docker cp xxx.sql ecos-postgres:/tmp/ && docker exec ecos-postgres psql -U postgres -d sys_man -f /tmp/xxx.sql`（**禁 PS 管道给 psql，会注 BOM**）
9. clean commit 风格 `feat(认知引擎): PMO-59-P4a ...` / `feat(场景工作台): PMO-59-P4b ...`；**只 add 本批次文件**（严禁 `git add -A` / `git add .`）
10. runtime-monitor **不得反向依赖业务模块**（`engine/*`、`services/*`、`workspace/*` 均不可出现在其 POM 或 import 中）

## §P4a Task（≤5，后端告警落库）

| Task | 文件/路径 | 操作 | 验收 |
|:--|------|------|------|
| T1 | 先勘察 `runtime/runtime-monitor` 现有 `IWarnLogService` 实现（内存 logStore）与 `runtime-*` 既有表 DDL 风格 | 新增告警落库表 DDL（`ecos_warn_log`：id/level/source/message/fault_context JSONB/review_tag/审计六列 + idx；编号 = 现有最大 +1 = V131），docker cp 法手工执行 | docker psql 表存在 + 结构核对 |
| T2 | `IWarnLogService` + `WarnLogServiceImpl`（runtime-monitor） | 接口新增 7 参 `warn(...)` default 方法（委托既有 5 参，签名只增不改）；实现增落库（内存 logStore 保留，**只增持久化分支**）；落库失败 try/catch WARN 不抛出；`JdbcTemplate` 构造器注入 | 触发一次 warn → psql 表见 1 行含 `fault_context`/`review_tag` |
| T3 | 补「告警查询」只读端点或复用既有 warn 查询端点（**先勘察是否已有**；已有则只做落库，不新增端点） | 勘察裁定：既有 2 个告警查询端点（`/api/monitor/alerts`、`/api/v1/alerts`）→ **不新增端点**；结论登记 `runtime-monitor/AGENTS.md`（新表 + 落库通道 + 查询口径） | 勘察结论登记 + 0 个 403/404（既有端点回归） |
| T4 | 复盘通道对接：`MentalReviewService`（cognitive-engine-impl） | 纳入告警维度为**只增键**：新增 `warnAlerts` 段（按 `review_tag=tag` + `since` 只读查询）+ `summary.warnAlerts` 计数；口径写清（避免双口径），**不改报告结构契约** | 文档登记 + curl 复盘输出含 `warnAlerts` 且既有键 0 变化 |
| T5 | 全量编译 + Gateway 启动 + `docs/09-PMO指令/PMO-59-认知引擎心智层-Phase4验收记录.md`（新建） | 含 commit hash/curl 原文/psql 摘要 | 文档齐全 + 工作区无本批次残留 |

## §P4b Task（P4a 验收 PASS 后启动，≤5，前端人机干预面板）

| Task | 文件/路径 | 操作 | 验收 |
|:--|------|------|------|
| T1 | 先读 `ecos_frontend/src/pages/scenario/CognitionPanel.tsx` 现有结构（PMO-53 已交付：运行配置/诊断/预测/模拟/策略/历史/模型注册表） | 新增「反事实推演」子区：干预变量（SET/DELTA + 值）+ 样本数 + seed 表单 → `POST /api/v1/cognitive/counterfactual` → 渲染四指标（期望收益/最大回撤/亏损概率/波动区间）+ 敏感性 Top3 + `assumptionRefs`/`excludedAssumptions` | 页面渲染 + 请求 200 + console 无 error |
| T2 | 同文件或拆子组件 | 新增「时间回放」子区：变量 + 版本选择 + 可选干预 → `GET /api/v1/cognitive/beliefs/{variable}/{version}/replay` → 渲染 `replayedVersion`/`currentVersion`/`believedDistribution`/四指标对比 | 重放 v 与当前版本数值差异可见 |
| T3 | 新增 `ecos_frontend/src/pages/scenario/MentalReviewPanel.tsx`（Tab 独立文件，≤800 行） | 「复盘聚合」面板：tag + since → `GET /api/v1/cognitive/mental-reviews` → 渲染 `beliefTimelines`/`hypotheses`/`evidence`/`runImpacts`/`summary`；非法 tag 400 有友好提示 | 面板渲染 + 结构完整 |
| T4 | i18n：`ecos_frontend/src/i18n/locales/scenario/zh-CN.json` + `en.json` 补 key（namespace 已在 LanguageContext 注册，无需改注册）；**0 硬编码中文**；主题 0 硬编码色值 | 新增文案全部双写；`npm run lint`（tsc --noEmit）绿 | tsc 绿 + 中英切换正常 |
| T5 | 浏览器 E2E（browser_use 子智能体）：`http://localhost:3000/#/project_workbench` 场景页 | 需登录（admin/admin123 → `POST /api/v1/auth/login`）；若登录态缺失，记录并说明，改为「Vite transform 两文件成功 + tsc 绿」作为降级证据 | 页面渲染无 ErrorBoundary + console 无 error + network 无意外 4xx/5xx（favicon 404 忽略） |

## §环境要点

- 编译：`& "D:\JavaProjects\env\apache-maven-3.9.11\bin\mvn.cmd" -f "D:\workspace\javaprojects\ECOS\ecos_backend\pom.xml" clean install "-Dmaven.test.skip=true" -B`（编译前 `Get-NetTCPConnection -LocalPort 8080 -State Listen` 查旧 gateway pid 先停）
- Gateway 启动（本检出 `_win_tasks/` 不存在）：`java -Xms512m -Xmx2g -jar gateway/target/gateway-1.0.0-SNAPSHOT.jar --spring.profiles.active=enterprise`（JWT 纯 base64 env 注入）
- curl body：`[IO.File]::WriteAllText($tmp, $json, (New-Object Text.UTF8Encoding $false))` 无 BOM + `curl.exe -s --data-binary "@$tmp"`，临时文件 `$env:TEMP\ecos-curl\` 用完删
- 统一返回体 `ApiResponse`：业务错误码在 `code` 字段（HTTP 可能仍 200），断言看 `code` + `success`
- `ecos_*` 表查询：`docker exec ecos-postgres psql -U postgres -d sys_man -c "..."`（简单查询走 `-c` 无 BOM 风险）

## §最终交付（两单各自返回）

① commit hash 列表 ② 全量编译/tsc 结果 ③ curl/psql/浏览器 E2E 验收原文摘要 ④ 独立 code-artifact-reviewer 审查结论（铁律 §2.4 安全集成、命名规范、依赖方向 `runtime-monitor` 零反向依赖、API 只增不改、前端主题/i18n grep 计数）⑤ 残留风险 ⑥ PMO-59 整体收口建议（P1~P4 全链闭环与遗留项）
