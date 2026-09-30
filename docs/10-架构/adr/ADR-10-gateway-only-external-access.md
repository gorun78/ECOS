# ADR-10 — Gateway-Only External Access（唯一外部入口）

> 来源: PMO-A 架构权威源收敛 | 日期: 2026-09-26 | 责任人: PMO 架构治理
> 版本: v1.0（批准）
> 裁决: **Accepted**
> 上游: 架构铁律 v1.6 §0.1（微服务 v2）+ ADR-7（端口隔离）+ ADR-3（数据隔离）
> 状态: 生效，全工程强制

---

## 1. 背景（Context）

ECOS 微服务 v2 下存在 **7 个可部署 JAR**（gateway + 5 service + workspace），网关 `api-gateway :8080` 是系统中唯一面向外部（浏览器 / 第三方客户端）的入口。但开发态下前端 BFF（`ecos_frontend/server.ts`）出于**调试便利**，曾在部分路由上**直连 service 独立端口**（如 `/api/dq` → datanet :18082、`/api/v1/workspace` → workspace :18090、`/api/v1/cognitive` → aiming :18084），绕过了 gateway 的组织/认证/限流 facade。

这带来三类风险：

1. **认证旁路**：绕过 gateway 的路由不经过 ADR-7 的「strip 外部 `X-ECOS-*` 头 + 重新注入」防伪造逻辑，跨服务信任头可被客户端伪造。
2. **策略不一致**：限流、审计、路径重写（`VersionPrefixRewriteFilter`）只挂 gateway 路径，直连绕过这些横切策略。
3. **部署耦合**：BFF 需同时知道每个 service 的独立端口，微服务扩展/重建时前端必须跟随改配置。

本 ADR 固化「**gateway-only external access**」原则，将其提升为架构宪法级决策。

## 2. 决策（Decision）

### 2.1 生产环境（🔴 强制）

- **生产 BFF 不直连任何 service / workspace 独立端口。** 所有 `/api/*` 流量**必须**经 `gateway :8080`。
- 生产部署中 BFF 的 `GATEWAY_URL` 指向 gateway；`DATANET_URL` / `WORKSPACE_URL` / `AIMING_URL` 等 service 直连变量**在生产 Profile 下失效**（即便残留配置也不被消费）。
- gateway 负责：认证鉴权、限流、路径重写、审计埋点、`X-ECOS-*` 信任头重注入，然后再按路由表的分派到后端 service。

### 2.2 开发直连（显式开关，默认关闭）

- 开发态允许 BFF 对个别慢/调试路由**直连 service 端口**，但必须由**显式环境变量开关**控制：`ECOS_LOCAL_DIRECT_SERVICE_PROXY=true`（PMO-B 实际落地名；本文 v1.0 原写作 `BFF_DIRECT_SERVICE`，与 `server.ts` 实装不符，v1.1 更正）。
- **默认 `ECOS_LOCAL_DIRECT_SERVICE_PROXY=false`** —— 即开发态默认也走 gateway，与生产一致；只有确认要调试某独立 service 时才置 true。
- 开关打开时，BFF 仍记录 `[BFF] direct-service` 日志标记，便于排查。

### 2.3 例外审批

- 任何**生产**绕 gateway 直连的诉求 = **架构违规**，必须提 ADR 级例外审批（PMO 裁决 + 留 commit hash），禁止在 BFF 代码里悄悄加直连路由。
- 例外必须写明：影响面（哪些路由）、为何 gateway 无法承载（如超大流式/文件直传）、回退方案、审计补偿。

### 2.4 回退策略

- 若强制 gateway-only 导致 gateway 成为瓶颈或某 service 重路由故障：
  1. **首选**：在 gateway 侧补分派路由 / 扩容，**不改回 BFF 直连**。
  2. 临时回退：置 `BFF_DIRECT_SERVICE=true` + 指定单一 service 端口，**仅开发/灰度环境**，限时 48h 内必须给出 gateway 侧修复。
  3. 回退动作必须记入 ADR-10 变更日志（§5），不允许静默回退。

## 3. 影响（Consequences）

| 维度 | 影响 |
|:--|:--|
| 安全 | ✅ 认证/信任头防伪造/限流/审计统一到 gateway，杜绝旁路 |
| 部署 | ✅ BFF 仅需 `GATEWAY_URL` 一个外部地址，微服务变更不前端感知 |
| 性能 | ⚠️ 所有流量多一跳 gateway；对大文件/流式路由需 gateway 侧缓冲调优（不做 BFF 直连绕过） |
| 复杂度 | ✅ 决策收敛：外部只有一个入口，排查链路单一 |

## 4. 关联

- ADR-7（端口隔离）：本 ADR 依赖端口隔离做大路由分派前提。
- ADR-3（数据隔离）、架构铁律 §2.4（安全接入）、§2.5（runtime-access）。
- 前端 BFF 实现：`ecos_frontend/server.ts`（dev 特例代理需收敛到 `BFF_DIRECT_SERVICE` 开关守卫）。

## 5. 变更日志

| 日期 | 版本 | 变更 | 责任人 |
|:--|:--|:--|:--|
| 2026-09-26 | v1.0 | 初版 Accepted，固化 gateway-only external access | PMO-A |
| 2026-09-28 | v1.1 | §2.2/§2.4 开关名更正为实装名 `ECOS_LOCAL_DIRECT_SERVICE_PROXY`（PMO-B 落地）；§4 关联补 ADR-14（确定性计算落点） | AI Agent（需求检视 Q10 自执行修正） |