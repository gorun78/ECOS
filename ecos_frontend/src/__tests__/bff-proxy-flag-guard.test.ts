// @vitest-environment node
/**
 * 详细设计-08 §九 门禁（W208/C190，ADR-10 / PMO-B）冻结标识：
 *   unit: bff-proxy-flag-guard.test.ts › prodRefusesDirectServiceProxy
 *
 * 口径：ECOS_LOCAL_DIRECT_SERVICE_PROXY 仅供本地调试（默认 false）。
 * NODE_ENV=production 且开关误开为 true 时，BFF 必须拒绝直连、强制回落 gateway-only，
 * 并以 console.error 明示（构建/启动期断言级，不静默）。
 */
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import http from "node:http";
import type { AddressInfo } from "node:net";
import { buildBffApp } from "../../server";
import { resolveLocalDirectFlag } from "../../config/route-manifest";

describe("bff › ADR-10 proxy flag guard (W208/C190)", () => {
  let gatewaySrv: http.Server;
  let datanetSrv: http.Server;
  let gatewayBase = "";
  let datanetBase = "";
  const gatewayHits: string[] = [];
  const datanetHits: string[] = [];

  beforeAll(async () => {
    gatewaySrv = http.createServer((req, res) => {
      gatewayHits.push(req.url ?? "");
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ hit: "gateway", path: req.url }));
    });
    datanetSrv = http.createServer((req, res) => {
      datanetHits.push(req.url ?? "");
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ hit: "datanet", path: req.url }));
    });
    await new Promise<void>((r) => gatewaySrv.listen(0, "127.0.0.1", r));
    await new Promise<void>((r) => datanetSrv.listen(0, "127.0.0.1", r));
    gatewayBase = `http://127.0.0.1:${(gatewaySrv.address() as AddressInfo).port}`;
    datanetBase = `http://127.0.0.1:${(datanetSrv.address() as AddressInfo).port}`;
  });

  afterAll(async () => {
    await new Promise<void>((r) => gatewaySrv.close(() => r()));
    await new Promise<void>((r) => datanetSrv.close(() => r()));
  });

  it("prodRefusesDirectServiceProxy — 生产档开关误开必须拒绝直连并回落 gateway", async () => {
    const errorSpy = vi.spyOn(console, "error").mockImplementation(() => undefined);
    const logSpy = vi.spyOn(console, "log").mockImplementation(() => undefined);
    const warnSpy = vi.spyOn(console, "warn").mockImplementation(() => undefined);
    try {
      // 1) 解析层：resolveLocalDirectFlag 在生产档返回 false 且 console.error 明示
      expect(
        resolveLocalDirectFlag({
          NODE_ENV: "production",
          ECOS_LOCAL_DIRECT_SERVICE_PROXY: "true",
        }),
      ).toBe(false);
      expect(errorSpy.mock.calls.some((c) => String(c[0]).includes("ADR-10"))).toBe(true);

      // 2) 装配层：buildBffApp 生产档 + 开关 true ⇒ localDirectEnabled=false，直连路由不注册
      const built = buildBffApp({
        env: {
          NODE_ENV: "production",
          ECOS_LOCAL_DIRECT_SERVICE_PROXY: "true",
          GATEWAY_URL: gatewayBase,
          DATANET_URL: datanetBase,
        },
        devViteMiddleware: null,
      });
      expect(built.localDirectEnabled).toBe(false);
      expect(built.mountOrder.some((m) => m.startsWith("direct:"))).toBe(false);

      // 3) 行为层：开关直连前缀 /api/dq 必须打到 gateway，datanet 收到 0 个请求
      const server = await new Promise<http.Server>((resolve) => {
        const s = built.app.listen(0, "127.0.0.1", () => resolve(s));
      });
      try {
        const port = (server.address() as AddressInfo).port;
        const res = await fetch(`http://127.0.0.1:${port}/api/dq/rules`);
        const body = (await res.json()) as { hit: string; path: string };
        expect(body.hit).toBe("gateway");
        expect(gatewayHits).toContain("/api/dq/rules");
        expect(datanetHits).toHaveLength(0);
      } finally {
        await new Promise<void>((r) => server.close(() => r()));
      }
    } finally {
      errorSpy.mockRestore();
      logSpy.mockRestore();
      warnSpy.mockRestore();
    }
  });

  it("devFlagEnabledRoutesDirect — 开发档开关开启时 directWhenFlag 前缀直连生效", async () => {
    const logSpy = vi.spyOn(console, "log").mockImplementation(() => undefined);
    const warnSpy = vi.spyOn(console, "warn").mockImplementation(() => undefined);
    try {
      const built = buildBffApp({
        env: {
          NODE_ENV: "development",
          ECOS_LOCAL_DIRECT_SERVICE_PROXY: "true",
          GATEWAY_URL: gatewayBase,
          DATANET_URL: datanetBase,
        },
        devViteMiddleware: null,
      });
      expect(built.localDirectEnabled).toBe(true);
      expect(built.mountOrder.filter((m) => m.startsWith("direct:"))).toHaveLength(5);

      gatewayHits.length = 0;
      datanetHits.length = 0;
      const server = await new Promise<http.Server>((resolve) => {
        const s = built.app.listen(0, "127.0.0.1", () => resolve(s));
      });
      try {
        const port = (server.address() as AddressInfo).port;
        const res = await fetch(`http://127.0.0.1:${port}/api/dq/rules`);
        const body = (await res.json()) as { hit: string; path: string };
        expect(body.hit).toBe("datanet");
        expect(datanetHits).toContain("/api/dq/rules");
        expect(gatewayHits).toHaveLength(0);
      } finally {
        await new Promise<void>((r) => server.close(() => r()));
      }
    } finally {
      logSpy.mockRestore();
      warnSpy.mockRestore();
    }
  });

  it("flagDefaultFalseGatewayOnly — 开关默认关闭（含生产误配其它值）一律 gateway", () => {
    expect(resolveLocalDirectFlag({})).toBe(false);
    expect(resolveLocalDirectFlag({ NODE_ENV: "production" })).toBe(false);
    expect(resolveLocalDirectFlag({ NODE_ENV: "production", ECOS_LOCAL_DIRECT_SERVICE_PROXY: "false" })).toBe(false);
    expect(resolveLocalDirectFlag({ ECOS_LOCAL_DIRECT_SERVICE_PROXY: "yes" })).toBe(true);
    expect(resolveLocalDirectFlag({ ECOS_LOCAL_DIRECT_SERVICE_PROXY: "maybe" })).toBe(false);
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });
});
