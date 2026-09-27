# ECOS 前端（ecos_frontend）

ECOS（企业认知操作系统）Web 前端。React 19 + Vite 6 + TypeScript + Tailwind CSS 4，通过内嵌 **BFF（C2EOS）** 把 `/api/*` 转发到后端 **gateway**（微服务 v2 唯一外部入口，见 `docs/00-架构/adr/ADR-10-gateway-only-external-access.md`）。

## 运行前提

- Node.js ≥ 20
- 后端 `ecos_backend` 的 **gateway** 已启动（默认 `http://localhost:8080`）

## 本地启动

```bash
# 1. 安装依赖
npm install

# 2. （可选）配置环境变量，写入本地 .env 或 .env.local（不要提交真实值）
#    所有变量都有 localhost 默认值，纯净开发环境可省略
PORT=3000                      # BFF 监听端口
GATEWAY_URL=http://localhost:8080   # gateway（🔴 唯一外部入口，必连；ADR-10 生产 BFF 只连此）
BACKEND_URL=http://localhost:8081   # 兼容旧 /sys-man 兜底路由（audit-logs 聚合）
DATANET_URL=http://localhost:18082  # dev 特例代理直连 datanet（P3-A Dq/Git/DataLake 路由）
WORKSPACE_URL=http://localhost:18090  # dev 特例代理直连 workspace
AIMING_URL=http://localhost:18084     # dev 特例代理直连 aiming/认知

# 3. 启动（BFF + Vite 中间件，浏览器访问 http://localhost:3000）
npm run dev
```

> 注：`GATEWAY_URL` 是权威出口。`DATANET_URL`/`WORKSPACE_URL`/`AIMING_URL` 目前是**开发态特例代理**直连；生产按 ADR-10 应收敛为仅连 gateway（后续批次落 BFF 直连开关）。

> 默认开发端口 **3000**。`ADMIN`/密码等登录凭据见 `.trae/rules/开发环境登录凭据.md`，**不写入本文档、不得硬编码进代码或镜像**。

## 常用命令

| 命令 | 说明 |
|:--|:--|
| `npm run dev` | BFF + Vite dev server（:3000） |
| `npm run build` | 构建前端 + esbuild 打 `dist/server.cjs` |
| `npm start` | `node dist/server.cjs` 跑生产 BFF（静态托管 `/dist`） |
| `npm run lint` | TypeScript 类型检查（`tsc --noEmit`）|
| `npm test` | vitest 单测（`--pool=threads`，Windows 大前端下稳定）|
| `npm run test:watch` | vitest watch 模式 |
| `npm run preview` | Vite 产物预览 |
| `npm run clean` | 清理 `dist` |

## BFF 访问模型

`server.ts`（C2EOS）是唯一前端入口，负责静态托管 + API 代理：

- **生产 / 默认**：所有 `/api/*` → `GATEWAY_URL`（gateway :8080）。dev 大流/调试直连 service（:18082 / :18090 / :18084）是**开发态特例代理**；生产按 ADR-10 须收敛为仅连 gateway（后续批次落 BFF 直连开关）。
- 浏览器拿 `Bearer` JWT，BFF 透传 `Authorization` 头给上游；GET/HEAD 不带 JSON Content-Type（Java Filter 会拒）。
- 设计约定：前端不直接拼后端子服务地址；`GATEWAY_URL` 是唯一权威出口。

## 目录速览

```
src/
  App.tsx            壳层：路由 + Sidebar(Topbar) + ErrorBoundary
  components/        通用/业务组件（含 common/MobileDataTable、copilot/ 等）
  pages/             工作台页面（*Workbench、iam、knowledge_view…）
  hooks/             useMediaQuery / useMobileSidebar / useTheme / useTableData…
  services/ + api/   接口调用封装（走 /api/* 由 BFF 代到 gateway）
server.ts            BFF（C2EOS）入口
vite.config.ts       Vite + vitest 配置
```