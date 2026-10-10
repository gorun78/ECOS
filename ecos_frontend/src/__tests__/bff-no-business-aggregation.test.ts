// @vitest-environment node
/**
 * 详细设计-08 §九 门禁「BFF 不聚合业务」冻结标识（M0）：
 *   unit: bff-no-business-aggregation.test.ts › auditLogsProxiedNotAggregated （W207/C189）
 *
 * 旧 server.ts:157-180 在 BFF 内聚合 /api/audit-logs：
 *   - 打 :8081（v1 legacy 端口）的 `/sys-man/api/v1/ecos/agent/executions`，绕开 gateway:8080 与 security-engine；
 *   - 伪造 `id: aud_${Date.now()}`、假 actor（AI-Agent-*）；
 *   - 上游失败时返回假 `{ success:true, data:[] }` 冒充空审计（虚假验收族，铁律 :14）。
 * 整改：整段删除，/api/audit-logs 落 catch-all → gateway 真值转发。
 * 后端供数证据（只读 grep，2026-10-10）：gateway `SecurityController`（/api/security/audit-logs）、
 *   security-engine `SecurityController` + `AuditController`（/api/v1/audit、/api/v1/security/audit 查询族）、
 *   cognitive `/api/v1/rules/audit-logs`、ai-engine `/api/v1/aip/audit-logs`。
 */
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import http from "node:http";
import type { AddressInfo } from "node:net";
import { buildBffApp } from "../../server";

describe("bff › no business aggregation (W207/C189)", () => {
  const serverSrc = readFileSync(resolve(__dirname, "../../server.ts"), "utf8");

  it("auditLogsProxiedNotAggregated — server.ts 源码面无聚合残余、无 :8081 兜底、无伪造 id/actor", () => {
    // 旧聚合上游：v1 legacy /sys-man 端点
    expect(serverSrc).not.toContain("/sys-man/api/v1/ecos/agent/executions");
    // 伪造 id 拼接（aud_${Date.now()}）
    expect(serverSrc).not.toMatch(/aud_\$\{/);
    // v1 端口 :8081 与 BACKEND_URL 兜底变量彻底移除
    expect(serverSrc).not.toMatch(/:8081/);
    expect(serverSrc).not.toContain("BACKEND_URL");
    // 不再注册 /api/audit-logs 独立聚合路由（只允许经 catch-all 转发）
    expect(serverSrc).not.toMatch(/app\s*\.\s*(get|use|post)\s*\(\s*["']\/api\/audit-logs/);
    // 假成功兜底 { success:true, count:0, data:[] } 不再出现
    expect(serverSrc).not.toMatch(/success:\s*true,\s*count:\s*0/);
  });

  it("auditLogsFallThroughToGatewayCatchAll — /api/audit-logs 由 gateway 承接（BFF 不合成数据）", async () => {
    const upstreamHits: string[] = [];
    const gatewaySrv = http.createServer((req, res) => {
      upstreamHits.push(req.url ?? "");
      // 模拟 gateway 真实裁决（无该端点时 404 —— 原样透传，BFF 不得改写为 200 假数据）
      res.writeHead(404, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ code: 404, message: "no such endpoint on gateway", data: null }));
    });
    await new Promise<void>((r) => gatewaySrv.listen(0, "127.0.0.1", r));
    const gatewayBase = `http://127.0.0.1:${(gatewaySrv.address() as AddressInfo).port}`;

    const { app } = buildBffApp({
      env: {
        NODE_ENV: "test",
        ECOS_LOCAL_DIRECT_SERVICE_PROXY: "false",
        GATEWAY_URL: gatewayBase,
      },
      devViteMiddleware: null,
    });
    const server = await new Promise<http.Server>((resolveListen) => {
      const s = app.listen(0, "127.0.0.1", () => resolveListen(s));
    });
    try {
      const port = (server.address() as AddressInfo).port;
      const res = await fetch(`http://127.0.0.1:${port}/api/audit-logs`);
      expect(upstreamHits).toContain("/api/audit-logs");
      expect(res.status).toBe(404); // 上游语义原样透传（不再被 BFF 掩成 200 + 假列表）
      const body = (await res.json()) as { code: number };
      expect(body.code).toBe(404);
    } finally {
      await new Promise<void>((r) => server.close(() => r()));
      await new Promise<void>((r) => gatewaySrv.close(() => r()));
    }
  });

  it("serverHasNoRemainingSpecialCaseHandlers — BFF 只剩派生自 route-manifest 的转发路由", () => {
    // 不允许出现任何直接命中 /api 前缀的 app.get 特例 handler
    // （生产 SPA 兜底 `app.get("*")` 非 /api 特例，不在此禁止面）
    const apiGetMatches = serverSrc.match(/\.\s*get\s*\(\s*["'`][^"'`]*\/api/g) ?? [];
    expect(apiGetMatches).toHaveLength(0);
    // forwardProxy 的调用只发生在 buildBffApp 装配段（清单派生），聚合逻辑无法藏身他处
    expect(serverSrc).toContain("selectBffExplicitEntries");
    expect(serverSrc).toContain("selectBffCatchAllEntry");
  });
});
