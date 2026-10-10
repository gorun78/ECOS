/**
 * C2EOS BFF (Backend for Frontend)
 * 代理 /api/* 调用至后端 gateway（唯一外部入口，见 ADR-10 / 铁律 v1.6 §0.3.1）。
 *
 * 🔴 ADR-10 / PMO-B：生产与默认全部 /api/* → GATEWAY_URL (gateway :8080)。
 * 开发态定向直连 service（datanet/workspace/aiming）
 * 仅在 ECOS_LOCAL_DIRECT_SERVICE_PROXY=true 时解锁（默认 false）。
 *
 * 行为口径：
 * - dev 默认模式（开关=false）：所有 /api/* 经 GATEWAY 转发；DATANET_URL/WORKSPACE_URL/AIMING_URL
 *   不被消费（即便环境变量给出）
 * - dev 调试模式（开关=true）：5 类前缀走 service 端口直连；其余认证透传不变
 * - 任何特例直连启动时必须打 [BFF] direct-service 日志
 *
 * 启动：npm run dev → 浏览器访问 http://localhost:3000
 * 见 ../ADOptimized/README.md（环境变量表）与 ../ADOptimized-生态.md
 */
import express from "express";
import path from "path";
import { createServer as createViteServer } from "vite";
import dotenv from "dotenv";

dotenv.config();

/**
 * 提取 ECOS_LOCAL_DIRECT_SERVICE_PROXY 开关语义。
 * 字符串 "true"/"1"/"yes"/"on" 视为开启；任何其它值（包括 false/empty/misspelled）一律 false。
 * 生产环境建议 Node 启动时设置 ECOS_LOCAL_DIRECT_SERVICE_PROXY=false 强制只走 gateway
 * （与 ADR-10 行为对上：生产 BFF 不直连 service）。
 */
const LOCAL_DIRECT_PROXY_ENABLED: boolean = (() => {
  const v = (process.env.ECOS_LOCAL_DIRECT_SERVICE_PROXY || "").trim().toLowerCase();
  return v === "true" || v === "1" || v === "yes" || v === "on";
})();

// C190/W208 (M2·A) 生产档拒绝直连：NODE_ENV=production 时若 ECOS_LOCAL_DIRECT_SERVICE_PROXY=true
// 直接 process.exit(1) —— 与之对齐 ADR-10 "生产 BFF 不直连 service" 铁律，防误开造成
// :8080 单端口契约被冲掉（PMO-B）。红色日志按"启动期可观测"口径打，不再静默处理。
const _NODE_ENV_CURRENT = process.env.NODE_ENV || "";
if (_NODE_ENV_CURRENT === "production" && LOCAL_DIRECT_PROXY_ENABLED) {
  console.error(
    "[BFF] FATAL: ECOS_LOCAL_DIRECT_SERVICE_PROXY=true is refused in production (ADR-10 PMO-B). " +
      "Set ECOS_LOCAL_DIRECT_SERVICE_PROXY=false or run with NODE_ENV=development/undefined.",
  );
  process.exit(1);
}

const app = express();
const PORT = parseInt(process.env.PORT || "3000", 10);
// 🔴 gateway 是唯一权威出口（ADR-10 / v1.6）。生产必填变量。
// C189/W207 收口：/api/audit-logs 已经改走 GATEWAY，不再需要 v1 时代 BACKEND_URL 兜底（:8081）。
const GATEWAY = process.env.GATEWAY_URL || "http://localhost:8080";

// 以下三个仅 LOCAL_DIRECT_PROXY_ENABLED=true 时被消费。生产/默认下变量设了也不被引用。
// 即便没设置也有 localhost 缺省，避免调试开放时直接用。
const DATANET = process.env.DATANET_URL || "http://localhost:18082";
const WORKSPACE = process.env.WORKSPACE_URL || "http://localhost:18090";
const AIMING = process.env.AIMING_URL || "http://localhost:18084";

// 🔴 禁止在 app 级全局注册 body 解析器。本 app 内 vite.middlewares（见 startServer）自带 /api 代理，
//    全局 express.json 会先于它抽干 req 流，而 http-proxy 仍按原 Content-Length 透传 ⇒ 上游等不到
//    任何 body 字节，阻塞到 socket 超时。实证 2026-10-10：POST /api/v1/auth/login 经 :3000 无响应，
//    同一 body 直连 :8080 = 200/0.16s；GET 与零长 POST 均正常 ⇒ 仅"带 body 的方法"受害。
//    解析器只挂在 BFF 自有 forwardProxy 挂载点上（forwardProxy 需要 req.body 再序列化）。
const jsonBody = express.json({ limit: "10mb" });

// ── 通用上游转发函数 ────────────────────────────────────────
// 用法：proxy(upstreamBaseUrl, routeLabel)
//  - upstreamBaseUrl: 要转发到的目标 (GATEWAY 或 service 直连地址)
//  - routeLabel: 日志记录用标签（如 "gateway" / "datanet-direct"）
const forwardProxy =
  (upstreamBaseUrl: string, routeLabel: string) =>
  async (req: express.Request, res: express.Response): Promise<void> => {
    const targetUrl = `${upstreamBaseUrl}${req.originalUrl}`;
    const method = req.method;
    const t0 = Date.now();
    console.log(`[BFF] ${method} ${req.originalUrl} -> ${targetUrl} (${routeLabel})`);
    try {
      // 🔴 仅在请求体存在时声明 JSON Content-Type — GET/HEAD 对 Java 后端加 JSON Content-Type
      //    会被部分 Filter 拒绝（实证：管线 M0 旧事故）。
      const withBody =
        method !== "GET" && method !== "HEAD" && req.body !== undefined;
      const upstreamHeaders: Record<string, string> = {
        ...(withBody ? { "Content-Type": "application/json" } : {}),
        ...(req.headers.authorization ? { Authorization: req.headers.authorization } : {}),
        ...(req.headers["x-request-id"]
          ? { "X-Request-ID": req.headers["x-request-id"] as string }
          : {}),
        // D-1 根因实锤（gateway-stdout.log：`CachedBodyHttpServletRequest` 在
        // `CoyoteInputStream.read` 上 SocketTimeoutException → ClientAbortException）——
        // BFF 的 undici fetch 复用了一条对端(Tomcat)已半关的 keep-alive 连接，body 字节
        // 错位后 gateway 阻塞读 body 至 20s socket 超时 ⇒ BFF 拿不到任何响应头。
        // 反制：上游用短连接（Connection: close + undici keepalive:false），不复用
        // 陈旧 socket，直接消除此"复用竞态"（一配一用，非泛化兜底）。
        Connection: "close",
      };
      const fetchOptions: RequestInit & { keepalive?: boolean } = {
        method,
        headers: upstreamHeaders,
        // 与 Connection: close 呼应，显式关闭 undici agent 级连接复用。
        keepalive: false,
      };
      // C174/W192 (P-4 落点): 透传客户端 Last-Event-ID 以便 BFF 之下的 gateway/service 走 SSE 续传；
      // 与 ADR-7 同属"透传不 strip"三段责任，BFF 不得静默吞掉该头（卷 00 属主：jwt/trust 三段）。
      if (req.headers["last-event-id"]) {
        (fetchOptions as any).headers = {
          ...(fetchOptions.headers as Record<string, string>),
          "Last-Event-ID": req.headers["last-event-id"] as string,
        };
      }
      if (withBody) fetchOptions.body = JSON.stringify(req.body);
      // D-1 (2026-10-05 BFF-login-hang 修复)：只约束"上游返回响应头"这段握手期，
      // 一旦头部到手立即 clearTimeout —— SSE 长流在头部之后不受 30s 限制，
      // 不给 C174 SSE 逐块 pipe 引入"30s 被砍"回归（实证 hang 发生在"拿不到任何响应"的握手相）。
      const connectCtrl = new AbortController();
      const connectTimer = setTimeout(
        () => connectCtrl.abort(new Error("BFF upstream connect timeout 30000ms")),
        30_000
      );
      const upstream = await fetch(targetUrl, { ...fetchOptions, signal: connectCtrl.signal });
      clearTimeout(connectTimer);
      console.log(`[BFF] ← ${routeLabel} ${upstream.status} wall_ms=${Date.now() - t0} ${req.originalUrl}`);
      const contentType = upstream.headers.get("content-type") || "";
      // C174 (旧 :85 `await upstream.text()` 全缓冲 ⇒ SSE 死亡): SSE 走"逐块 pipe"分支，禁 join/text().
      // 铁律 ADR-15（制品 ≠ 承流）：本层只做透传，不解析、不改写、不降级为轮询。
      if (contentType.includes("text/event-stream")) {
        res.writeHead(upstream.status, {
          "Content-Type": "text/event-stream",
          "Cache-Control": "no-cache, no-transform",
          "X-Accel-Buffering": "no",
          "Connection": "keep-alive",
        });
        const reader = upstream.body.getReader();
        for (;;) {
          const { done, value } = await reader.read();
          if (done) break;
          res.write(value);
          // 客户端断开即打断，防连接泄漏。
          if (res.writableEnded) break;
        }
        res.end();
        return;
      } else if (contentType.includes("application/json")) {
        const data = await upstream.json();
        res.status(upstream.status).json(data);
      } else {
        const text = await upstream.text();
        res.status(upstream.status).set("Content-Type", contentType).send(text);
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      // D-1: 附带 undici/底层 cause（如 undici UND_ERR_* / ECONNREFUSED / 30s 超时），
      // 便于区分"上游无回包" vs "超时中止" vs "连接被拒"，不改变 502 语义。
      const cause =
        err instanceof Error && (err as NodeJS.ErrnoException).cause
          ? ` cause=${String((err as NodeJS.ErrnoException).cause)}`
          : "";
      console.error(`[BFF] Proxy ${routeLabel} error for ${req.originalUrl} (wall_ms=${Date.now() - t0}): ${msg}${cause}`);
      res.status(502).json({ success: false, message: `Upstream (${routeLabel}) unavailable: ${msg}${cause}` });
    }
  };

// ── 路由装配（顺序敏感！C175/W193） ────────────────────────
// express 路由按注册顺序胜出；catch-all "/api" 必须在 vite.middlewares 之后注册，
// 否则 vite dev server 的 /@vite、/@fs、/__open-in-editor、/src/* 等开发资源
// 会被 /api 兜底吞掉（本窗口原本 catch-all 在 startServer 之前 ⇒ C175）。
// 因此这里只注册：gateway 必选细节前缀 + ADR-10 直连分支；catch-all 与 vite 挂载
// 挪到 startServer() 内部（vite 就绪后再挂），保持"vite 先，catch-all 后"的顺序。

// 1) gateway 必选直连（🔴 架构铁律 v1.6：仅 :8080 对外可达；BFF 默认永不 bypass）
//    /api/monitor 与 /api/twins 被 gateway Controller 持有，永远经 GATEWAY。
app.use("/api/monitor", jsonBody, forwardProxy(GATEWAY, "gateway/monitor"));
app.use("/api/twins", jsonBody, forwardProxy(GATEWAY, "gateway/twins"));

// 2) 🔴 ADR-10 直连接能开关（默认关闭）：5 类前缀仅在 LOCAL_DIRECT_PROXY_ENABLED=true 时
//    直接打到 service 端口；否则一律落到下方通用 /api 规则 → GATEWAY。
if (LOCAL_DIRECT_PROXY_ENABLED) {
  console.warn("[BFF] ECOS_LOCAL_DIRECT_SERVICE_PROXY=true — 开发态允许 BFF 定向直连 service (datanet/workspace/aiming)");
  // P3-A：Dq/Git/DataLake 路由物理迁至 datanet :18082（与网关 V1_REWRITE_MAP 同前缀）。
  // 路由前缀互不重叠，最长精确前缀天然胜出（Spring @RequestMapping 规则）。
  app.use("/api/dq", jsonBody, forwardProxy(DATANET, "datanet-direct/dq"));
  app.use("/api/v1/ecos/git", jsonBody, forwardProxy(DATANET, "datanet-direct/git"));
  app.use("/api/datalake", jsonBody, forwardProxy(DATANET, "datanet-direct/datalake"));
  // PMO-60：workspace/controller 独立 :18090，cognitive 迁至 aiming :18084（认知前端归属 v1.5）。
  app.use("/api/v1/workspace", jsonBody, forwardProxy(WORKSPACE, "workspace-direct"));
  app.use("/api/v1/cognitive", jsonBody, forwardProxy(AIMING, "aiming-cognitive-direct"));
} else {
  // 默认路径：所有 /api/* (包括 5 类前缀) 一律转发 gateway :8080。
  // gateway 按最长前缀精确优先分派 service（铁律 v1.6 §0.3.1，详见 GatewayApplication excludeFilters / @ComponentScan）。
  console.log("[BFF] ECOS_LOCAL_DIRECT_SERVICE_PROXY=false — 所有 /api/* 经 GATEWAY (ADR-10)");
}

// 3) C189/W207 收口：/api/audit-logs 不再在 BFF 内聚合（旧版走 BACKEND_URL :8081 兜底
//    + 手写过 data[]，属"业务聚合"违反 ADR-15 承流口径 + ST06 审计链路属主=security-engine）。
//    现在纯转发到 GATEWAY，与 audit 域其余路径同口径（SecController/auditLogs 属主在
//    security-engine，BFF 无任何合成/兜底/降级路径）。
app.use("/api/audit-logs", jsonBody, forwardProxy(GATEWAY, "gateway/audit-logs"));

// ── Vite SPA + catch-all（顺序约束见上） ──────────────────
const startServer = async () => {
  if (process.env.NODE_ENV !== "production") {
    const vite = await createViteServer({
      server: { middlewareMode: true, hmr: false },
      appType: "spa",
    });
    // C175: vite.middlewares 必须在 /api 兜底之前挂载（vite 的 /@vite、/src/* 等才被 vite 接住）
    app.use(vite.middlewares);
    console.log("[BFF] Vite dev middleware mounted (C175: before /api catch-all).");
    // C175: catch-all 兜底放到 vite.middlewares 之后注册 — 顺序即"vite 先，兜底后"。
    app.use("/api", jsonBody, forwardProxy(GATEWAY, "gateway/default"));
  } else {
    const distPath = path.join(process.cwd(), "dist");
    // C175: /api 兜底必须在 SPA 静态与 get("*") 之前注册 — 顺序即"细节前缀 → /api 兜底 → 静态 → get('*')"。
    app.use("/api", jsonBody, forwardProxy(GATEWAY, "gateway/default"));
    app.use(express.static(distPath));
    app.get("*", (_req, res) => {
      res.sendFile(path.join(distPath, "index.html"));
    });
    console.log("[BFF] Serving static build from /dist.");
  }

  app.listen(PORT, "0.0.0.0", () => {
    const proxyMode = LOCAL_DIRECT_PROXY_ENABLED ? "DIRECT (dev-only, ADR-10 例外开启)" : "GATEWAY-ONLY (default)";
    console.log(`[BFF] C2EOS running on http://0.0.0.0:${PORT}  [proxy=${proxyMode}]`);
    console.log(`[BFF]   gateway -> ${GATEWAY}`);
    if (LOCAL_DIRECT_PROXY_ENABLED) {
      console.log(`[BFF]   datanet  (direct, dev only) -> ${DATANET}`);
      console.log(`[BFF]   workspace (direct, dev only) -> ${WORKSPACE}`);
      console.log(`[BFF]   aiming    (direct, dev only) -> ${AIMING}`);
    }
  });
};

startServer();