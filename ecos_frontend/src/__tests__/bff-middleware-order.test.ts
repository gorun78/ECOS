// @vitest-environment node
/**
 * 详细设计-08 §九 门禁「中间件顺序」冻结标识：
 *   unit: bff-middleware-order.test.ts › viteMiddlewareBeforeApiCatchAll （W193/C175，M1·A）
 *
 * 背景：旧 server.ts 的 `app.use(vite.middlewares)` 位于 catch-all
 * `app.use("/api", forwardProxy(GATEWAY))` 之后 ⇒ dev 下 /api/* 永远先被 BFF 接走，
 * vite.config.ts 的引擎直连代理表对 /api/* 实际不生效（代理双轨声明失效）。
 *
 * 本门禁以真实 Express 装配行为验证三点：
 *  1) vite middlewares 位于 catch-all 之前（/api/* 命中 vite 表 ⇒ 由 vite 承接）；
 *  2) BFF 显式路由（/api/monitor、/api/twins）先于 vite 注册，不被 vite 截胡；
 *  3) vite middlewareMode 语义：未命中 proxy 的请求 next() 回落 catch-all → gateway。
 */
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import type { RequestHandler } from "express";
import http from "node:http";
import type { AddressInfo } from "node:net";
import { buildBffApp } from "../../server";

describe("bff › middleware order (W193/C175)", () => {
  let upstream: http.Server;
  let upstreamBase = "";
  const upstreamHits: string[] = [];

  let bff: http.Server;
  let bffUrl = "";

  beforeAll(async () => {
    // 假 gateway：记录命中路径并回 JSON（用于区分请求由谁承接）
    upstream = http.createServer((req, res) => {
      upstreamHits.push(req.url ?? "");
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ hit: "gateway", path: req.url }));
    });
    await new Promise<void>((r) => upstream.listen(0, "127.0.0.1", r));
    upstreamBase = `http://127.0.0.1:${(upstream.address() as AddressInfo).port}`;

    // 假 vite middleware：命中 /api/* 即截获（模拟 vite 代理表接管 dev 引擎前缀），
    // 但对 probe-passthrough 前缀调用 next()（模拟 middlewareMode 未命中行为）。
    const fakeViteMiddleware: RequestHandler = (req, res, next) => {
      const url = req.url ?? "";
      if (url.startsWith("/api") && !url.startsWith("/api/passthrough")) {
        res.writeHead(418, { "Content-Type": "application/json" });
        res.end(JSON.stringify({ hit: "vite", path: url }));
        return;
      }
      next();
    };

    const { app } = buildBffApp({
      env: {
        NODE_ENV: "test",
        ECOS_LOCAL_DIRECT_SERVICE_PROXY: "false",
        GATEWAY_URL: upstreamBase,
      },
      devViteMiddleware: fakeViteMiddleware,
    });
    await new Promise<void>((r) => {
      bff = app.listen(0, "127.0.0.1", r as () => void);
    });
    bffUrl = `http://127.0.0.1:${(bff.address() as AddressInfo).port}`;
  });

  afterAll(async () => {
    await new Promise<void>((r) => bff.close(() => r()));
    await new Promise<void>((r) => upstream.close(() => r()));
  });

  it("viteMiddlewareBeforeApiCatchAll — vite middlewares 先于 /api catch-all 注册且实际生效", async () => {
    // 行为面：/api/* 应被 vite 承接（418），而非落入 catch-all → gateway
    const res = await fetch(`${bffUrl}/api/some-engine-prefix`);
    expect(res.status).toBe(418);
    const body = (await res.json()) as { hit: string };
    expect(body.hit).toBe("vite");
    expect(upstreamHits.filter((p) => p.startsWith("/api/some-engine-prefix"))).toHaveLength(0);

    // 装配顺序面：mountOrder 中 vite-middlewares 必须早于 catch-all:/api
    const ordered = buildBffApp({
      env: { NODE_ENV: "test", ECOS_LOCAL_DIRECT_SERVICE_PROXY: "false" },
      devViteMiddleware: (_req, _res, next) => next(),
    });
    const idxVite = ordered.mountOrder.indexOf("vite-middlewares");
    const idxCatchAll = ordered.mountOrder.findIndex((m) => m.startsWith("catch-all:"));
    expect(idxVite).toBeGreaterThan(-1);
    expect(idxCatchAll).toBeGreaterThan(-1);
    expect(idxVite).toBeLessThan(idxCatchAll);
  });

  it("bffExplicitRoutesRegisteredBeforeVite — /api/monitor、/api/twins 不被 vite 截胡", async () => {
    // 假 vite 会截获一切 /api/*；monitor/twins 仍须由 BFF 显式路由先接走 → gateway
    const res = await fetch(`${bffUrl}/api/monitor`);
    expect(res.status).toBe(200);
    const body = (await res.json()) as { hit: string; path: string };
    expect(body.hit).toBe("gateway");
    expect(body.path).toBe("/api/monitor");

    const resTwins = await fetch(`${bffUrl}/api/twins`);
    const bodyTwins = (await resTwins.json()) as { hit: string; path: string };
    expect(bodyTwins.hit).toBe("gateway");
    expect(bodyTwins.path).toBe("/api/twins");
  });

  it("viteMiddlewareModeNextPassthrough — 未命中 proxy 的请求 next() 后仍落 catch-all → gateway", async () => {
    const res = await fetch(`${bffUrl}/api/passthrough/x`);
    expect(res.status).toBe(200);
    const body = (await res.json()) as { hit: string; path: string };
    expect(body.hit).toBe("gateway");
    expect(body.path).toBe("/api/passthrough/x");
  });
});
