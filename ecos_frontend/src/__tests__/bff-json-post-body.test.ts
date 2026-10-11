// @vitest-environment node
/**
 * BFF JSON POST 体透传冻结门禁（2026-10-11 live 事故回归守卫）：
 *
 * 背景（详设-08 §4.3 中间件顺序的衍生缺陷，live 实证）：旧装配 `app.use(express.json())`
 * 全局挂载于 vite dev middlewares 之前 ⇒ dev 下 vite 代理表（/api 兜底，W193/C175）接管
 * POST /api/v1/auth/login 时原始请求流已被 body-parser 耗尽，http-proxy 永久等待请求体，
 * 登录挂死 60s 超时（GET 无体不受影响，故只有 JSON POST 复现）。
 *
 * 本门禁以真实 Express 装配行为验证两条红线：
 *  1) vite 中间件（模拟代理表接管 /api/*）必须能原样读到 JSON POST 请求体
 *     ⇒ JSON 解析器禁止全局挂载，必须作用域收敛到 BFF forwardProxy 挂载点；
 *  2) 未命中 vite 代理、落 catch-all forwardProxy 的 POST，请求体必须被解析并重新序列化送达上游。
 */
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import type { RequestHandler } from "express";
import http from "node:http";
import type { AddressInfo } from "node:net";
import { buildBffApp } from "../../server";

describe("bff › json-post-body passthrough (2026-10-11 回归守卫)", () => {
  let upstream: http.Server;
  let bff: http.Server;
  let bffUrl = "";
  const upstreamBodies: string[] = [];

  beforeAll(async () => {
    // 假 gateway：记录完整请求体
    upstream = http.createServer((req, res) => {
      const chunks: Buffer[] = [];
      req.on("data", (c) => chunks.push(c as Buffer));
      req.on("end", () => {
        upstreamBodies.push(Buffer.concat(chunks).toString("utf8"));
        res.writeHead(200, { "Content-Type": "application/json" });
        res.end(JSON.stringify({ ok: true }));
      });
    });
    await new Promise<void>((r) => upstream.listen(0, "127.0.0.1", r));
    const upstreamBase = `http://127.0.0.1:${(upstream.address() as AddressInfo).port}`;

    // 假 vite middleware：命中 /api/vite-claimed/* 时像 http-proxy 一样流式读取原始体
    // （若全局 body-parser 已耗尽流，这里将永远收不到 body ⇒ 门禁失败点即旧事故点）。
    const fakeVite: RequestHandler = (req, res, next) => {
      const url = req.url ?? "";
      if (url.startsWith("/api/vite-claimed")) {
        const chunks: Buffer[] = [];
        req.on("data", (c) => chunks.push(c as Buffer));
        req.on("end", () => {
          res.writeHead(200, { "Content-Type": "application/json" });
          res.end(JSON.stringify({ hit: "vite", body: Buffer.concat(chunks).toString("utf8") }));
        });
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
      devViteMiddleware: fakeVite,
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

  it("viteProxySeesRawJsonBody — vite 接管前请求体未被全局解析器消耗", async () => {
    const payload = JSON.stringify({ username: "admin", password: "x".repeat(8) });
    const res = await fetch(`${bffUrl}/api/vite-claimed/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: payload,
    });
    expect(res.status).toBe(200);
    const echoed = (await res.json()) as { hit: string; body: string };
    expect(echoed.hit).toBe("vite");
    // 旧缺陷下 body 恒为空串（流已被 express.json 读完）
    expect(echoed.body).toBe(payload);
  });

  it("catchAllForwardProxyReserializesParsedBody — next() 回落 catch-all 的 POST 体照常送达", async () => {
    const payload = JSON.stringify({ goal: "蓝图草稿创建回环", n: 42 });
    const res = await fetch(`${bffUrl}/api/passthrough-blueprint/sc001/blueprints`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: payload,
    });
    expect(res.status).toBe(200);
    const sent = upstreamBodies[upstreamBodies.length - 1];
    expect(JSON.parse(sent)).toEqual(JSON.parse(payload));
  });
});
