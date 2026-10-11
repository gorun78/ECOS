/**
 * C2EOS BFF (Backend for Frontend)
 * 代理 /api/* 调用至后端 gateway（唯一外部入口，见 ADR-10 / 铁律 v1.6 §0.3.1）。
 *
 * 🔴 ADR-10 / PMO-B：生产与默认全部 /api/* → GATEWAY_URL (gateway :8080)。
 * 开发态定向直连 service（datanet/workspace/aiming）
 * 仅在 ECOS_LOCAL_DIRECT_SERVICE_PROXY=true 时解锁（默认 false；
 * NODE_ENV=production 时即便开关误开也会被 resolveLocalDirectFlag 拒绝，W208/C190）。
 *
 * 路由表单源（详细设计-08 §4.1，W194/C176 / R-54 ①）：
 * 本文件与 vite.config.ts 的代理表均派生自 `config/route-manifest.ts`，禁止在此另写路由表。
 *
 * 中间件顺序（W193/C175 修复，见 §4.3；JSON 体解析器 2026-10-11 下钻为按挂载点作用域）：
 *   BFF 显式 gateway 路由(/api/monitor,/api/twins)【各自前置 jsonParser】
 *   → 开关分支直连（仅 flag=true 且非 production）【同上】
 *   → vite dev middlewares（dev 下使 vite 代理表对 /api/* 真正生效；未命中 proxy 的请求 next() 继续）
 *   → catch-all app.use("/api", jsonParser, forwardProxy(GATEWAY))
 *   🔴 禁止全局 app.use(express.json())：dev 下 vite 代理表(/api 兜底)会接管 /api/* POST，
 *      而全局 body-parser 已先耗尽原始请求流 ⇒ http-proxy 永久等待请求体（登录等 JSON POST 挂死）。
 *      原始流必须留给 vite 代理透传；仅 BFF 自有 forwardProxy 挂载点（forward 时重新序列化）解析。
 *
 * 行为口径：
 * - dev 默认模式（开关=false）：BFF 显式路由经 GATEWAY；引擎直连前缀由 vite 代理表承接；
 *   其余 /api/* 落 catch-all → GATEWAY。
 * - dev 调试模式（开关=true）：清单 directWhenFlag 前缀走 service 端口直连；其余认证透传不变
 * - 生产：无 vite 路径，全部 /api/* → GATEWAY（gateway-first，ADR-10）
 * - BFF 不做业务聚合（W207/C189）：/api/audit-logs 的 BFF 内聚合已删除，
 *   该路径落 catch-all → gateway（gateway:8080 / security-engine 提供 /api/security/audit-logs、
 *   /api/v1/audit 等真实端点；BFF 不再伪造 id/actor，不再兜底 v1 legacy 端口）。
 *
 * 启动：npm run dev → 浏览器访问 http://localhost:3000
 */
import express from "express";
import type { Application, RequestHandler, Response } from "express";
import path from "path";
import { createServer as createViteServer } from "vite";
import dotenv from "dotenv";
import {
  CATCH_ALL_PREFIX,
  resolveLocalDirectFlag,
  resolveUpstream,
  selectBffCatchAllEntry,
  selectBffExplicitEntries,
  validateRouteManifest,
} from "./config/route-manifest";

dotenv.config();

// 构建期校验（禁运行期静默，详细设计-08 §4.1 ①②③）：清单不一致直接抛错。
{
  const manifestErrors = validateRouteManifest();
  if (manifestErrors.length > 0) {
    throw new Error(
      `[BFF][route-manifest] 路由表单源校验失败:\n - ${manifestErrors.join("\n - ")}`,
    );
  }
}

// ── 通用上游转发函数 ────────────────────────────────────────
// 用法：proxy(upstreamBaseUrl, routeLabel)
//  - upstreamBaseUrl: 要转发到的目标 (GATEWAY 或 service 直连地址)
//  - routeLabel: 日志记录用标签（如 "gateway" / "datanet-direct"）
export const forwardProxy =
  (upstreamBaseUrl: string, routeLabel: string) =>
  async (req: express.Request, res: Response): Promise<void> => {
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
        // Last-Event-ID 续传（详细设计-08 §4.4）：BFF 透传，gateway 不 strip。
        ...(req.headers["last-event-id"]
          ? { "Last-Event-ID": req.headers["last-event-id"] as string }
          : {}),
      };
      const fetchOptions: RequestInit = {
        method,
        headers: upstreamHeaders,
      };
      if (withBody) fetchOptions.body = JSON.stringify(req.body);
      const upstream = await fetch(targetUrl, fetchOptions);
      // 透传响应体 + 状态码，不做任何字段改写（错误原样返回，401/403 必须正确传递）。
      // 🔴 F06-21-1 / W163 / W192（C174）：SSE（text/event-stream）必须流式直通，禁止缓冲整个 body ——
      //    否则 Agent 流式对话（/api/v1/agent*/*?stream=true）在 BFF 侧被攒到回连结束才下发，
      //    SSE 语义（逐 token 推送）彻底失效。命中 SSE 走管道直转发 + 关压缩 + flushHeaders。
      const contentType = upstream.headers.get("content-type") || "";
      if (contentType.includes("text/event-stream")) {
        res.status(upstream.status);
        res.setHeader("Content-Type", "text/event-stream");
        res.setHeader("Cache-Control", "no-cache, no-transform");
        res.setHeader("X-Accel-Buffering", "no");
        res.flushHeaders?.();
        const webBody = upstream.body;
        if (webBody) {
          const reader = webBody.getReader();
          const pump = async (): Promise<void> => {
            try {
              for (;;) {
                const { done, value } = await reader.read();
                if (done) break;
                res.write(Buffer.from(value));
              }
              res.end();
            } catch (e) {
              console.error(`[BFF] SSE ${routeLabel} stream error:`, e instanceof Error ? e.message : String(e));
              res.end();
            }
          };
          const cancelReader = (): void => {
            void reader.cancel().catch(function (_e: unknown): void {
              /* swallow — reader already consumed or already cancelled */
            });
          };
          req.on("close", cancelReader);
          void pump();
        } else {
          res.end();
        }
        return;
      }
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

// ── 路由装配（顺序即门禁 bff-middleware-order 断言对象，W193/C175）──────
export interface BffAppBuildResult {
  app: Application;
  /** 装配顺序轨迹（"json" → "gateway:/api/monitor" → … → "vite-middlewares" → "catch-all:/api"） */
  mountOrder: string[];
  /** 生效的开关（ADR-10 生产守卫之后） */
  localDirectEnabled: boolean;
}

export interface BffAppOptions {
  env?: Record<string, string | undefined>;
  /** dev 下由 startServer 传入 vite.middlewares；生产/测试为 null */
  devViteMiddleware?: RequestHandler | null;
}

export function buildBffApp(options: BffAppOptions = {}): BffAppBuildResult {
  const env: Record<string, string | undefined> = { ...process.env, ...(options.env ?? {}) };
  const gateway = resolveUpstream({ engine: "gateway", port: 8080 }, env);
  // ADR-10 生产守卫：NODE_ENV=production 时 resolveLocalDirectFlag 内部拒绝直连并 console.error
  const localDirectEnabled = resolveLocalDirectFlag(env);

  if (localDirectEnabled) {
    console.warn("[BFF] ECOS_LOCAL_DIRECT_SERVICE_PROXY=true — 开发态允许 BFF 定向直连 service (datanet/workspace/aiming)");
  } else {
    console.log("[BFF] ECOS_LOCAL_DIRECT_SERVICE_PROXY=false — 所有 /api/* 经 GATEWAY (ADR-10)");
  }

  const mountOrder: string[] = [];
  const logMount = (label: string): void => {
    mountOrder.push(label);
  };

  const app = express();
  // JSON 解析器不全局挂载（见文件头「中间件顺序」红线）：只作用于 BFF 自有 forwardProxy
  // 挂载点（forwardProxy 用解析后 body 重新序列化转发）；原始请求流留给 vite 代理透传。
  const jsonParser = express.json({ limit: "10mb" });
  logMount("json:per-mount");

  // 1) BFF 显式路由（清单 alwaysGateway 非 catch-all 条目 + 开关分支），先于 vite 注册，
  //    保证 /api/monitor、/api/twins 与开关直连前缀不被 vite 代理表截胡。
  for (const entry of selectBffExplicitEntries(localDirectEnabled)) {
    const upstream = entry.alwaysGateway
      ? gateway
      : resolveUpstream(entry, env);
    const label = entry.alwaysGateway
      ? `gateway${entry.prefix}`
      : `${entry.engine}-direct${entry.prefix}`;
    app.use(entry.prefix, jsonParser, forwardProxy(upstream, label));
    logMount(`${entry.alwaysGateway ? "gateway" : "direct"}:${entry.prefix}`);
  }

  // 2) 🔴 W193/C175：vite dev middlewares 必须位于 catch-all 之前，
  //    否则 dev 下 /api/* 永远先被 BFF 接走、vite 代理表对 /api/* 不生效。
  //    vite middlewareMode 对未命中 proxy 的请求 next()，不影响上方 BFF 显式路由。
  if (options.devViteMiddleware) {
    app.use(options.devViteMiddleware);
    logMount("vite-middlewares");
  }

  // 3) 兜底：其余所有 /api/* 一律 GATEWAY（ADR-10 默认唯一出口）。
  //    /api/audit-logs 亦走此通道（W207/C189：BFF 聚合已删，不再伪造数据）。
  const catchAll = selectBffCatchAllEntry();
  app.use(catchAll.prefix, jsonParser, forwardProxy(gateway, "gateway/default"));
  logMount(`catch-all:${catchAll.prefix}`);

  return { app, mountOrder, localDirectEnabled };
}

// ── 启动装配 ──────────────────────────────────────────────
export async function startServer(): Promise<void> {
  const isProduction = process.env.NODE_ENV === "production";
  const port = parseInt(process.env.PORT || "3000", 10);

  let devViteMiddleware: RequestHandler | null = null;
  if (!isProduction) {
    const vite = await createViteServer({
      server: { middlewareMode: true, hmr: false },
      appType: "spa",
    });
    devViteMiddleware = vite.middlewares;
    console.log("[BFF] Vite dev middleware mounted (before /api catch-all, W193/C175).");
  }

  const { app: bffApp, localDirectEnabled } = buildBffApp({ devViteMiddleware });

  if (isProduction) {
    const distPath = path.join(process.cwd(), "dist");
    bffApp.use(express.static(distPath));
    bffApp.get("*", (_req, res) => {
      res.sendFile(path.join(distPath, "index.html"));
    });
    console.log("[BFF] Serving static build from /dist.");
  }

  bffApp.listen(port, "0.0.0.0", () => {
    const proxyMode = localDirectEnabled ? "DIRECT (dev-only, ADR-10 例外开启)" : "GATEWAY-ONLY (default)";
    console.log(`[BFF] C2EOS running on http://0.0.0.0:${port}  [proxy=${proxyMode}]`);
    console.log(`[BFF]   gateway -> ${resolveUpstream({ engine: "gateway", port: 8080 })}`);
    if (localDirectEnabled) {
      for (const entry of selectBffExplicitEntries(true).filter((e) => e.directWhenFlag)) {
        console.log(`[BFF]   ${entry.engine} (direct, dev only) ${entry.prefix} -> ${resolveUpstream(entry)}`);
      }
    }
  });
}

// 作为入口运行时自启；被 vitest / 门禁测试 import 时（VITEST 环境变量存在）不自启。
if (!process.env.VITEST) {
  void startServer();
}
