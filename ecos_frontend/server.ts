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

const app = express();
const PORT = parseInt(process.env.PORT || "3000", 10);
// 兼容 /api/audit-logs 走 /sys-man 的兜底端点。
const BACKEND = process.env.BACKEND_URL || "http://localhost:8081";
// 🔴 gateway 是唯一权威出口（ADR-10 / v1.6）。生产必填变量。
const GATEWAY = process.env.GATEWAY_URL || "http://localhost:8080";

// 以下三个仅 LOCAL_DIRECT_PROXY_ENABLED=true 时被消费。生产/默认下变量设了也不被引用。
// 即便没设置也有 localhost 缺省，避免调试开放时直接用。
const DATANET = process.env.DATANET_URL || "http://localhost:18082";
const WORKSPACE = process.env.WORKSPACE_URL || "http://localhost:18090";
const AIMING = process.env.AIMING_URL || "http://localhost:18084";

app.use(express.json({ limit: "10mb" }));

// ── 通用上游转发函数 ────────────────────────────────────────
// 用法：proxy(upstreamBaseUrl, routeLabel)
//  - upstreamBaseUrl: 要转发到的目标 (GATEWAY 或 service 直连地址)
//  - routeLabel: 日志记录用标签（如 "gateway" / "datanet-direct"）
const forwardProxy =
  (upstreamBaseUrl: string, routeLabel: string) =>
  async (req: express.Request, res: express.Response): Promise<void> => {
    const targetUrl = `${upstreamBaseUrl}${req.originalUrl}`;
    const method = req.method;
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
      };
      const fetchOptions: RequestInit = {
        method,
        headers: upstreamHeaders,
      };
      if (withBody) fetchOptions.body = JSON.stringify(req.body);
      const upstream = await fetch(targetUrl, fetchOptions);
      // 透传响应体 JSON/text + 状态码，不做任何字段改写（错误原样返回，401/403 必须正确传递）
      const contentType = upstream.headers.get("content-type") || "";
      if (contentType.includes("application/json")) {
        const data = await upstream.json();
        res.status(upstream.status).json(data);
      } else {
        const text = await upstream.text();
        res.status(upstream.status).set("Content-Type", contentType).send(text);
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      console.error(`[BFF] Proxy ${routeLabel} error for ${req.originalUrl}:`, msg);
      res.status(502).json({ success: false, message: `Upstream (${routeLabel}) unavailable: ${msg}` });
    }
  };

// ── 路由装配 ────────────────────────────────────────────────
// 1) gateway 必选直连（🔴 架构铁律 v1.6：仅 :8080 对外可达；BFF 默认永不 bypass）
//    /api/monitor 与 /api/twins 被 gateway Controller 持有，永远经 GATEWAY。
app.use("/api/monitor", forwardProxy(GATEWAY, "gateway/monitor"));
app.use("/api/twins", forwardProxy(GATEWAY, "gateway/twins"));

// 2) 🔴 ADR-10 直连接能开关（默认关闭）：5 类前缀仅在 LOCAL_DIRECT_PROXY_ENABLED=true 时
//    直接打到 service 端口；否则一律落到下方通用 /api 规则 → GATEWAY。
if (LOCAL_DIRECT_PROXY_ENABLED) {
  console.warn("[BFF] ECOS_LOCAL_DIRECT_SERVICE_PROXY=true — 开发态允许 BFF 定向直连 service (datanet/workspace/aiming)");
  // P3-A：Dq/Git/DataLake 路由物理迁至 datanet :18082（与网关 V1_REWRITE_MAP 同前缀）。
  // 路由前缀互不重叠，最长精确前缀天然胜出（Spring @RequestMapping 规则）。
  app.use("/api/dq", forwardProxy(DATANET, "datanet-direct/dq"));
  app.use("/api/v1/ecos/git", forwardProxy(DATANET, "datanet-direct/git"));
  app.use("/api/datalake", forwardProxy(DATANET, "datanet-direct/datalake"));
  // PMO-60：workspace/controller 独立 :18090，cognitive 迁至 aiming :18084（认知前端归属 v1.5）。
  app.use("/api/v1/workspace", forwardProxy(WORKSPACE, "workspace-direct"));
  app.use("/api/v1/cognitive", forwardProxy(AIMING, "aiming-cognitive-direct"));
} else {
  // 默认路径：所有 /api/* (包括 5 类前缀) 一律转发 gateway :8080。
  // gateway 按最长前缀精确优先分派 service（铁律 v1.6 §0.3.1，详见 GatewayApplication excludeFilters / @ComponentScan）。
  console.log("[BFF] ECOS_LOCAL_DIRECT_SERVICE_PROXY=false — 所有 /api/* 经 GATEWAY (ADR-10)");
}

// 3) 残留特例：/api/audit-logs — 后端系统无对应端点，由 BFF 底层聚合，无 bypass gateway 行为。
app.get("/api/audit-logs", async (_req, res): Promise<void> => {
  try {
    // 兼容旧源：兼容老 /sys-man 代理（BACKEND_URL :8081）的过渡端点。
    const resp = await fetch(`${BACKEND}/sys-man/api/v1/ecos/agent/executions`);
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    const body: any = await resp.json();
    const executions: any[] = body.data || [];
    const auditLogs = executions.map((exec: any) => ({
      id: exec.id || `aud_${Date.now()}`,
      timestamp: exec.createdAt || "Just now",
      actor: `AI-Agent-${exec.agentId || "unknown"}`,
      action: exec.missionType || "Agent Execution",
      objectType: "agent_studio",
      objectId: exec.agentId || "unknown",
      details: `Status: ${exec.status || "completed"}. Input: ${(exec.input || "").substring(0, 100)}`,
      status: exec.status === "FAILED" ? ("rejected" as const) : ("success" as const),
    }));
    res.json({ success: true, count: auditLogs.length, data: auditLogs });
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : String(err);
    console.log(`[BFF] audit-logs fallback: ${msg}`);
    res.json({ success: true, count: 0, data: [] });
  }
});

// 4) 兜底：另外所有 /api/* 一律 GATEWAY（ADR-10）。这是 default 唯一出口。
app.use("/api", forwardProxy(GATEWAY, "gateway/default"));

// ── Vite SPA ──────────────────────────────────────────────
const startServer = async () => {
  if (process.env.NODE_ENV !== "production") {
    const vite = await createViteServer({
      server: { middlewareMode: true, hmr: false },
      appType: "spa",
    });
    app.use(vite.middlewares);
    console.log("[BFF] Vite dev middleware mounted.");
  } else {
    const distPath = path.join(process.cwd(), "dist");
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