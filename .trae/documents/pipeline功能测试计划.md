# 计划：启动前后端并对数据工作台 Pipeline 模块做功能测试

## Context

用户要求启动前后端进行功能测试，首个目标是数据工作台（Data Workbench）的 pipeline 模块。当前仓库为 Windows 检出（`d:\workspace\javaprojects\ECOS`），对应 WSL `/home/guorongxiao/ECOS`。后端为单体 Gateway（:8080），前端 Express BFF + Vite（:3000，代理 `/api`→:8080）。Windows 检出中无 `ecos-tests/` 冒烟脚本，需以 curl + 浏览器 E2E 方式现场验证。

**被测对象关键事实（已探明）**：
- 后端：[PipelineController.java](file:///d:/workspace/javaprojects/ECOS/ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/pipeline/PipelineController.java)，`@RequestMapping("/api/v1/pipeline")`，8 个端点：
  - `POST/GET /definitions`（创建/列表）、`GET/PUT/DELETE /definitions/{id}`
  - `POST /definitions/{id}/execute`（走 runtime-task，返回 taskId）
  - `GET /tasks/{taskId}/status`（轮询）、`GET /executions/{id}`（旧记录）
  - 免认证：`auth.whitelist.paths` 含 `/api/v1/pipeline/**`；VersionPrefixRewriteFilter 已移除 pipeline 重写（直接映射）
- 前端：路由 `#/data-workbench` → [DataWorkbenchLayout.tsx](file:///d:/workspace/javaprojects/ECOS/ecos_frontend/src/pages/DataWorkbenchLayout.tsx) 内 Tab `pipeline-builder` → [PipelineBuilderTab.tsx](file:///d:/workspace/javaprojects/ECOS/ecos_frontend/src/pages/data-workbench/tabs/PipelineBuilderTab.tsx) + [PipelineFlowEditor.tsx](file:///d:/workspace/javaprojects/ECOS/ecos_frontend/src/pages/data-workbench/PipelineFlowEditor.tsx)（拖拽 DAG 编辑器）；API 层 `src/pages/data-workbench/api.ts`（`PIPELINE_DEFS='/api/v1/pipeline/definitions'`）
- 数据：PG `sys_man`（localhost:5432，postgres/postgres），表 `ecos_pipeline_definition/node/execution`
- 登录：`POST /api/v1/auth/login`，测试账号 `admin/admin123`（有历史记录提示本机可能登录失败，需先验证）

## 实施步骤

### Step 1 — 前置检查（只读）
- 确认 WSL 可用：`wsl -e bash -c "echo ok"`
- 确认 PG 可达：`wsl -e bash -c "pg_isready -h localhost -p 5432"` 或 psql 查 `sys_man`
- 确认端口空闲：8080（后端）、3000（前端）；若被占用先确认归属（仅操作本项目端口）
- 确认 Docker 基础设施（ecos-docker：PG/Neo4j/MinIO/OPA）是否已运行；未运行则 `docker-compose up -d`

### Step 2 — 后端构建（条件执行）
- 检查 `~/.m2` 中 gateway 相关 JAR 时间戳是否新于源码；若陈旧或从未构建：
  ```bash
  env -i HOME=/home/guorongxiao PATH=... JAVA_HOME=... bash -c 'cd ~/ECOS/ecos_backend && mvn install -DskipTests -Dmaven.test.skip=true -q'
  ```
  （遵守铁律：编译=mvn install，非 compile）

### Step 3 — 启动后端（WSL）
- `wsl -e bash -c "bash ~/start-gateway.sh"`（后台运行，内置 `unset HOME` 绕 UNC bug，enterprise profile）
- 轮询健康检查至就绪：`curl -s http://localhost:8080/api/health`（或 `/health`）
- 失败兜底：查日志定位（端口占用用 `lsof -ti:8080 | xargs kill -9`，仅本项目端口）

### Step 4 — 启动前端（Windows）
- `ecos_frontend` 下若无 `node_modules` 先 `npm install`
- `npm run dev`（= `tsx server.ts`，Express BFF，PORT 3000，GATEWAY_URL 默认 :8080）
- 验证 `http://localhost:3000` 可访问、`/api` 代理连通（`curl localhost:3000/api/health`）

### Step 5 — 后端 API 功能测试（curl 逐项断言）
| # | 用例 | 预期 |
|---|------|------|
| A1 | `GET /api/v1/pipeline/definitions` | 200 + `ApiResponse` 标准体（code/success/data） |
| A2 | `POST /definitions`（最小合法 body：name/description/nodes） | 200 + 返回 id |
| A3 | `GET /definitions/{id}` | 200 + nodes 回显 |
| A4 | `PUT /definitions/{id}`（改名） | 200 + updatedAt 变化 |
| A5 | `POST /definitions/{id}/execute` | 200 + taskId（runtime-task 闭环） |
| A6 | `GET /tasks/{taskId}/status` | 200 + status 枚举（RUNNING/SUCCEEDED/FAILED 之一） |
| A7 | `GET /definitions/not-exist` | 404 语义（ApiResponse.notFound，非 500） |
| A8 | `DELETE /definitions/{id}` 后 `GET` | 删除成功 + 404 |
- 同步验证登录链路：`POST /api/v1/auth/login`（admin/admin123），记录 token 可用性（供前端 E2E 用）

### Step 6 — 前端 E2E 功能测试（浏览器自动化）
用 Chrome DevTools MCP（`mcp_Chrome_DevTools_MCP`）或 TRAE-browseruse：
1. 打开 `http://localhost:3000`，如需登录用 admin/admin123
2. 进入 `#/data-workbench`，切到 Pipeline Builder Tab
3. 验证：列表加载（与 A1 数据一致）、无 console 报错、无白屏
4. 通过 UI 新建 pipeline（填名称/描述）→ 保存 → 列表出现新条目
5. 打开流程编辑器（拖拽 DAG），验证画布/节点面板/属性面板渲染
6. 点击执行 → 观察执行监控组件状态轮询（对应 A5/A6）
7. 每步截图存档；记录所有 console error / network 4xx-5xx

### Step 7 — 汇总测试报告
- 输出 PASS/FAIL 清单（API 8 项 + UI 场景），失败项附证据（curl 响应/截图/console 日志）
- 发现缺陷按严重级标注（不修代码，仅报告，除非用户要求修复）

## 验证方式
- 后端：curl 断言 HTTP 状态码 + `ApiResponse.success/code` 字段
- 前端：浏览器截图 + console/network 抓包（DevTools MCP `list_console_messages`/`list_network_requests`）
- 全程不改动业务代码；如启动脚本需微调（如端口/路径），先向用户确认

## 风险与兜底
- admin/admin123 登录失败 → 查 `td_user` 表确认账号，或改用 whitelist 免认证路径直测 pipeline（pipeline 本身免认证）
- Gateway 启动 OOM → 按 start-gateway-jar.sh 注释提升 -Xmx 至 4GB（需用户确认）
- Windows 侧 npm 依赖缺失 → 先 `npm install`（可能耗时）
