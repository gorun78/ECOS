---
name: pm-auto-deploy
description: "PM 自动部署验证 Skill：后端 mvn 编译→强杀旧进程→启动 jar→API 验证，前端 npm build→启动 vite→连通验证。部署前校验端口归属，只操作本项目端口；失败自动回退。Invoke when PM 需在零干预模式下执行部署验证。"
version: 1.0.0
author: Hermes Agent (AI-Native Software Factory)
license: MIT
platforms: [linux, macos]
triggers:
  - 自动部署
  - 部署验证
  - auto-deploy
  - 后端部署
  - 前端部署
metadata:
  hermes:
    tags: [pm, deploy, auto, backend, frontend, api, rollback, port-owner]
    profiles: [pm]
    auto_trigger: false
    priority: high
---

# PM 自动部署验证 Skill

## 功能说明

在 PM 全自动流程（auto_pipeline）的「部署验证」阶段，自动完成后端、前端、API 三层部署验证。代码合并后由 auto_pipeline 调用，无需用户说「需要」。

## 🔴 部署红线

1. **部署前校验端口归属**：强杀进程前必须先 `check_port_owner` + `is_own_process` 确认端口被本项目进程占用，端口被其他项目占用时**拒绝操作并升级人工**。
2. **只操作本项目端口**：绝不强杀不属于本项目的进程。
3. **mvn 必须在 backend/ 目录执行**：项目根目录没有 pom.xml，`mvn clean package` 必须在 `backend/` 下执行。
4. **强杀用 `pkill -9 -f`**：`pkill -f '<jar>'` 可能杀不掉所有旧实例，必须 `pkill -9 -f '<jar>'` 后 `sleep 2` 确保端口释放。
5. **前端 build 必须验证**：worker 产出可能在 scratch workspace 通过构建，项目目录 `npm run build` 可能失败。部署前必须实际 build，失败则回退。

## 部署链路

```
后端：cd backend && mvn clean package -DskipTests
      → 校验端口归属 → pkill -9 -f jar → sleep 2
      → java -jar target/<jar> --server.port=<port>（日志 /tmp/backend-auto.log）
      → sleep 15 → lsof 校验端口 + curl 校验 HTTP

前端：cd frontend && npm run build
      → 校验端口归属 → kill 旧 vite → sleep 1
      → npx vite --host 0.0.0.0 --port=<port>（日志 /tmp/frontend-auto.log）
      → sleep 10 → 端口校验 + HTTP 校验

API ：登录获取 token → 调用本次开发 API → 响应时间 < 3s
```

## 脚本清单

| 脚本 | 用途 |
|------|------|
| `scripts/deploy_backend.py` | 后端编译→强杀→启动→验证 |
| `scripts/deploy_frontend.py` | 前端 build→启动→验证 |
| `scripts/verify_api.py` | API 功能验证（登录 + 调用 + 响应时间） |

## 使用方式

```bash
# 后端部署
python3 -B <skill-dir>/scripts/deploy_backend.py \
  --project-dir <TERMINAL_CWD> \
  --jar ainative-factory-1.0.0.jar \
  --port 18085 \
  --server-ip 127.0.0.1 \
  --health-path /health \
  --expected-codes 200

# 前端部署
python3 -B <skill-dir>/scripts/deploy_frontend.py \
  --project-dir <TERMINAL_CWD> \
  --port 18084 \
  --server-ip 127.0.0.1

# API 验证（--verify-path 指向本次开发的实际 API，此处以 /api/auth/info 为例）
python3 -B <skill-dir>/scripts/verify_api.py \
  --base-url http://127.0.0.1:18085 \
  --account admin \
  --password admin123 \
  --login-path /api/auth/login \
  --verify-path /api/auth/info
```

> 参数默认值来自 PM `config.yaml` 的 `auto_pipeline.deploy` 与 `auto_pipeline.api` 配置块，脚本支持命令行覆盖。

## 回退策略

| 失败场景 | 回退动作 |
|---------|---------|
| 后端编译失败 | 不启动，返回 `{success: false, error: 编译失败}`，由 auto_pipeline 升级人工 |
| 后端启动失败 | `pkill -9 -f <jar>` 清理残留进程，升级人工 |
| 前端构建失败 | `git checkout HEAD -- frontend/` 回退前端文件，升级人工 |
| API 验证失败 | 后端已启动但功能异常，保留进程供人工排查，升级人工 |

## 参考

- 部署强杀模式：`kanban-dispatch-json-format/references/deploy-java-process-force-kill.md`
- 前端 build 失败恢复：`kanban-dispatch-json-format/references/frontend-build-failure-recovery.md`
