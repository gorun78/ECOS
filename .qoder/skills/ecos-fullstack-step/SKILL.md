---
name: ecos-fullstack-step
description: "ECOS Windows 本机一键拉起/停止前后端（7 后端 jar + 前端 vite）并跑 4 主题 × 6 Tab Playwright E2E 截图验证。当用户说'启动前后端'、'启一下'、'把 ECOS 拉起来'、'链路验一下'、'跑下 E2E'时触发。不改业务代码、不 force stop docker、不引入新依赖。"
---

# ECOS Full-Stack Up + E2E Quick-Check

> 用途：Windows 本机夏勤开发场景下一击启动前后端 + 4 主题 × 6 Tab 智能 E2E。
> 触发词："启动前后端" / "启一下" / "把 ECOS 拉起来" / "链路验一下" / "跑下 E2E"。
> 约束：不改业务代码 · 不 force stop docker server · 不引入新依赖。

## 当且/条件触发
- 用户要求拉起前后端任一集或验证链路
- 分支漂后动态导入失效应可通道重启
- 需要可复现的 UI 验证证据（截图 × 场景矩阵）

## 工具/制品清单
| 项 | 路径 | 用途 |
|---|---|---|
| 一键启脚本 | `ecos-docker/scripts/ecos-up.ps1` | 7 后端 jar + 前端 vite；参数见下 |
| Playwright E2E | `ecos-docker/scripts/aiworkbench-theme-e2e-v2.py` | 4 主题 × 6 Tab 截图，纯文字判 fail |
| 中间件 | Docker Desktop (PG/Neo4j/MinIO/Kafka/ZK/OPA) | `docker compose up -d` |
| JWT / DEEPSEEK | 自动加载（路径固定） | 见下 |

## 一键启动
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\ecos-docker\scripts\ecos-up.ps1
# 常用参数：
#   -FromUp          # 只启前端 :3000
#   -BackendOnly     # 只启后端 7 进程
#   -JarTimeout 180  # 单 jar 启动等待秒数（gateway 典型 60s, buszhi 最慢 1-3 min）
#   -Down            # 全停（java + vite）, docker 保留
```

**7 后端 jar → 端口**（[AGENTS.md §Architecture](file:///d:/workspace/javaprojects/ECOS/ecos_backend/AGENTS.md) v2 微服务）：

| 进程 | jar | 端口 | 主体 |
|---|---|---|---|
| gateway | `gateway/target/gateway-...jar` | 8080 | monolith fat-jar（含 :8080 分派） |
| sysman | `services/sysman/target/sysman-service-...jar` | 18081 | IAM/审计/脱敏 |
| datanet | `services/datanet/target/...jar` | 18082 | 数据源/管道/DQ |
| buszhi | `services/buszhi/target/...jar` | 18083 | 本体/工作流（⚠ 无真主线？TIMEDOUT 主因待查） |
| aiming | `services/aiming/target/...jar` | 18084 | Agent/Loop/LLM（**认知 4 卡实际端点**） |
| dccheng | `services/dccheng/target/...jar` | 18086 | KB/图谱/RAG/规则 |
| workspace | `workspace/workspace-service/target/...jar` | 18090 | 场景层（认知 4 卡走 workspace REST） |

**Profile 规则**：`gateway` 用 `enterprise`（frontend 需要 `cognitive:true` 方案，standard 无）；其余 6 用 `standard`。

**环境变量（脚本内自动加载，不传则不报）**：
- `JWT_PRIVATE_KEY` ← `C:\Users\guoro\.config\ecos\jwt-private-key.pem`
- `DEEPSEEK_API_KEY` ← `C:\Users\guoro\.hermes\profiles\gorunkol\.env`

## 验证基线
cleanup kill `:3000` 前）跑：
```powershell
py -3 ecos-docker/scripts/aiworkbench-theme-e2e-v2.py
```
预期输出：
```
[C1] token acquired: user=admin
[C2] base render body_len=~1700-2000   (>100)
[C4] theme=slate-light/deep-space/cyber-terminal/royal-purple: OK
[C3/C5] <theme>/<tab> × 24 shot ok=ok (errbox=0)
```
SCREENSHOTS：`ecos-docker/scripts/shots/<theme>-<tab>.png`（24 帧）

## 前端路由/认证速查
- 路径：`http://localhost:3000/#/ai-workbench`  ·  登录 `admin/admin123`
- token 存 `localStorage.token`（字符串，非 JSON）
- theme 存 `localStorage.ecos_theme`（四选一：`slate-light, deep-space, cyber-terminal, royal-purple`）
- 应用：[App.tsx](file:///d:/workspace/javaprojects/ECOS/ecos_frontend/src/App.tsx) 240+ keys 伙伴映射 + [LanguageContext](file:///d:/workspace/javaprojects/ECOS/ecos_frontend/src/components/LanguageContext.tsx) 双抖擞；Tab 在 [index.tsx #L33-46](file:///d:/workspace/javaprojects/ECOS/ecos_frontend/src/pages/aiworkbench/index.tsx) `NAV_TABS`
- 后端 API 落点头：`GET /api/v1/auth/login`（前端 `Login.tsx` 全仓意用，POST 进 gateway :8080）

## 失败快诊（按 4 序排查）
1. **JAVA SERVER REAL DOWN** — 查 `ecos_backend/_logs/<name>.log` 末 20 行：
   - 含 `Address already in use` → 另有独立进程占该端口；`Get-Process java | Where-Object ...`  kill 或重生
   - 含 `DataSource ... Connection refused` → docker 中间件下跳跳；查 `docker compose ps`
   - 含 `No qualifying bean ...` / `BeanCreationException` → 该 jar 与 current HEAD 代码不匹配；**mvn install** 重生
   - 含 `no main manifest attribute` → 该 jar 只 library 非 executable（buszhi/aiming 可能）；用 Maven 重建：`mvn -pl services/buszhi -am package -DskipTests`
2. **GATEWAY FALIVED 但 仓进程 IN**——`STILL` 查 `lad gar`侧：`_win_tasks`（warning: AGENTS.md §5.1.14 旧脚本 **可能已 deleteHEAD 拭漂**，脚本 根究AGENTS.md 分享 ago portion？）
3. **PORT PORT 2(full port) NET** — `Test-NetConnection localhost -Port <port>`
4. **DYNAMIC IMPORT 前段 / 503** — `35216e9` Vite proxy 已从 `35216e9` 分支漂 `533f9be`(P2b 认知 Tab)：前端走 `/api/v1/workspace/scenarios/{id}/cognitive/{ep}`；**只** 依赖 workspace :18090 启；cognitive 不开 = 4 卡 groupbox "未启用" 红警（非故障、预期语义）

## 环境变量铁律
- **停 jar 保 docker**：`-Down` 合同应不考 `docker compose down`（重建 ~2 min；脚本留 mute 提示）
- **当前分支 fork down**：12+ 外部 commit 更软变 tokens 漂移风险；**opponents** [commits](file:///d:/workspace/javaprojects/ECOS) 核对 → 不空闲的另一台 amd 同时发 skill curl
- **劲 > 1 jar 后 真 MOTOKEN**：不能用 -Xmx512m 硬拉 gateway（AI, net-prot）；用 -Xmx2048m（8080 profile active）

## 交付验证 (Reviewer 必检)
- [ ] `ecos-up.ps1` 末尾 8 端口状态霓虹（绿/--）
- [ ] E2E v2 6 项 `script exit 0`
- [ ] 24 帧 screenshots 可读（人工替）
- [ ] JWT/DEEPSEEK 未铺到 shell env 而仅是脚本局部（覆叠即泄漏风险 → 不ского落 GIT TODO 变量到 environ/global）
