/**
 * C2EOS BFF (Backend for Frontend)
 * Proxies /api/* calls to Databridge Java backend, with fallbacks for missing endpoints.
 * Serves React SPA via Vite middleware.
 */
import express from "express";
import path from "path";
import { createServer as createViteServer } from "vite";
import dotenv from "dotenv";

dotenv.config();

const app = express();
const PORT = parseInt(process.env.PORT || "3000");
const BACKEND = process.env.BACKEND_URL || "http://localhost:8081";
const GATEWAY = process.env.GATEWAY_URL || "http://localhost:8080";

app.use(express.json({ limit: "10mb" }));

// ── Gateway proxy (MonitorController, DeviceTwinController are on port 8080) ──
// These routes must be defined BEFORE the general /api proxy below.
const gatewayProxy = async (req: express.Request, res: express.Response) => {
  const targetUrl = `${GATEWAY}${req.originalUrl}`;
  const method = req.method;
  console.log(`[BFF] ${method} ${req.originalUrl} -> ${targetUrl} (gateway)`);
  try {
    // 仅在请求体存在时声明 JSON Content-Type —
    // GET/HEAD 请求对 Java 后端加 JSON Content-Type 会被部分 Filter 判定为非法
    // 触发 500 空响应（原本导致前端"点按钮无反馈"）。
    const withBody =
      method !== "GET" && method !== "HEAD" && req.body;
    const upstreamHeaders: Record<string, string> = {
      ...(withBody ? { "Content-Type": "application/json" } : {}),
      ...(req.headers.authorization ? { Authorization: req.headers.authorization } : {}),
    };
    const fetchOptions: RequestInit = {
      method,
      headers: upstreamHeaders,
    };
    if (withBody) {
      fetchOptions.body = JSON.stringify(req.body);
    }
    const upstream = await fetch(targetUrl, fetchOptions);
    const contentType = upstream.headers.get("content-type") || "";
    if (contentType.includes("application/json")) {
      const data = await upstream.json();
      res.status(upstream.status).json(data);
    } else {
      const text = await upstream.text();
      res.status(upstream.status).set("Content-Type", contentType).send(text);
    }
  } catch (err: any) {
    console.error(`[BFF] Gateway proxy error for ${req.originalUrl}:`, err.message);
    res.status(502).json({
      success: false,
      message: `Gateway unavailable: ${err.message}`,
    });
  }
};

app.use("/api/monitor", gatewayProxy);
app.use("/api/twins", gatewayProxy);
app.use("/datanet", gatewayProxy);

// ── Special endpoints (backend doesn't have these) ─────────

// GET /api/audit-logs — aggregates agent execution records as audit events
app.get("/api/audit-logs", async (_req, res) => {
  try {
    const resp = await fetch(`${BACKEND}/sys-man/api/v1/ecos/agent/executions`);
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    const body = await resp.json();
    const executions = body.data || [];
    const auditLogs = executions.map((exec: any) => ({
      id: exec.id || `aud_${Date.now()}`,
      timestamp: exec.createdAt || "Just now",
      actor: `AI-Agent-${exec.agentId || "unknown"}`,
      action: exec.missionType || "Agent Execution",
      objectType: "agent_studio",
      objectId: exec.agentId || "unknown",
      details: `Status: ${exec.status || "completed"}. Input: ${(exec.input || "").substring(0, 100)}`,
      status: exec.status === "FAILED" ? "rejected" as const : "success" as const,
    }));
    res.json({ success: true, count: auditLogs.length, data: auditLogs });
  } catch (err: any) {
    console.log(`[BFF] audit-logs fallback: ${err.message}`);
    res.json({ success: true, count: 0, data: [] });
  }
});

// ── API Proxy ──────────────────────────────────────────────
// All remaining /api/* calls are forwarded to the Gateway (port 8080).
app.use("/api", async (req, res) => {
  const targetUrl = `${GATEWAY}${req.originalUrl}`;
  const method = req.method;

  console.log(`[BFF] ${method} ${req.originalUrl} -> ${targetUrl}`);

  try {
    // 与 gatewayProxy 保持一致 — 仅在请求体存在时设置 JSON Content-Type，
    // 避免 GET 请求因带 JSON Content-Type 被 Java 后端 Filter 拒绝返回 500。
    const withBody =
      method !== "GET" && method !== "HEAD" && req.body;
    const upstreamHeaders: Record<string, string> = {
      ...(withBody ? { "Content-Type": "application/json" } : {}),
      ...(req.headers.authorization ? { Authorization: req.headers.authorization } : {}),
    };
    const fetchOptions: RequestInit = {
      method,
      headers: upstreamHeaders,
    };

    if (withBody) {
      fetchOptions.body = JSON.stringify(req.body);
    }

    const upstream = await fetch(targetUrl, fetchOptions);
    const contentType = upstream.headers.get("content-type") || "";

    if (contentType.includes("application/json")) {
      const data = await upstream.json();
      res.status(upstream.status).json(data);
    } else {
      const text = await upstream.text();
      res.status(upstream.status).set("Content-Type", contentType).send(text);
    }
  } catch (err: any) {
    console.error(`[BFF] Proxy error for ${req.originalUrl}:`, err.message);
    res.status(502).json({
      success: false,
      message: `Backend unavailable: ${err.message}`,
    });
  }
});

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
    console.log(`[BFF] C2EOS running on http://0.0.0.0:${PORT}`);
    console.log(`[BFF]   /api/monitor, /api/twins -> ${GATEWAY} (gateway)`);
    console.log(`[BFF]   /api/* (other) -> ${BACKEND}/sys-man`);
  });
};

startServer();
