// @vitest-environment node
/**
 * 详细设计-08 §九 门禁「路由表单源」冻结标识（W194/C176 / R-54 ①，M1·B）：
 *   unit: routeManifest.test.ts › devAndProdDeriveFromSameManifest
 *
 * 证明 vite.config.ts（dev 直连表）与 server.ts（BFF prod/默认承流）从同一
 * config/route-manifest.ts 派生，双表已消灭：
 *  - vite dev 表恰为迁移前 21 条（18 引擎直连 + /api、/datanet、/cases gateway 兜底），target 等价；
 *  - BFF 显式路由 = alwaysGateway 2 条（monitor/twins）+ 开关分支 5 条 + catch-all /api；
 *  - 构建期校验①②③通过，且变异注入（删表项/加表外项）会被校验捕获。
 */
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import {
  ROUTE_MANIFEST,
  buildViteDevProxyTable,
  resolveLocalDirectFlag,
  selectBffCatchAllEntry,
  selectBffExplicitEntries,
  validateRouteManifest,
  type RouteManifestEntry,
} from "../../config/route-manifest";

/** 迁移前 vite.config.ts 的 21 条代理 key（冻结基线，逐条等价迁移） */
const PRE_MIGRATION_VITE_KEYS = [
  "/api/v1/agent-loop",
  "/api/v1/agent-mesh",
  "/api/v1/agent",
  "/api/v1/agent-call",
  "/api/v1/knowledge",
  "/api/v1/security",
  "/api/v1/audit",
  "/api/v1/abac",
  "/api/v1/data-masking",
  "/api/v1/data-permission",
  "/api/v1/policy-engine",
  "/api/v1/engine/data",
  "/api/v1/ecos",
  "/api/v1/workspace",
  "/api/v1/cognitive",
  "/api/v1/world-model",
  "/api/v1/rules",
  "/api/v1/kb",
  "/api",
  "/datanet",
  "/cases",
];

/** 迁移前 vite 表 target → 端口映射（冻结基线） */
const PRE_MIGRATION_VITE_TARGETS: Record<string, number> = {
  "/api/v1/agent-loop": 18084,
  "/api/v1/agent-mesh": 18084,
  "/api/v1/agent": 18084,
  "/api/v1/agent-call": 18084,
  "/api/v1/knowledge": 18086,
  "/api/v1/security": 18081,
  "/api/v1/audit": 18081,
  "/api/v1/abac": 18081,
  "/api/v1/data-masking": 18081,
  "/api/v1/data-permission": 18081,
  "/api/v1/policy-engine": 18081,
  "/api/v1/engine/data": 18082,
  "/api/v1/ecos": 18083,
  "/api/v1/workspace": 18090,
  "/api/v1/cognitive": 18084,
  "/api/v1/world-model": 18089,
  "/api/v1/rules": 18086,
  "/api/v1/kb": 18086,
  "/api": 8080,
  "/datanet": 8080,
  "/cases": 8080,
};

const viteConfigSrc = readFileSync(resolve(__dirname, "../../vite.config.ts"), "utf8");
const serverSrc = readFileSync(resolve(__dirname, "../../server.ts"), "utf8");

describe("bff › route manifest single source (W194/C176)", () => {
  it("devAndProdDeriveFromSameManifest — vite 表与 BFF 路由均出自 route-manifest 且与迁移前基线等价", () => {
    // 0) 单源证据：两侧文件都 import config/route-manifest，且不再各自手写路由表
    expect(viteConfigSrc).toContain("./config/route-manifest");
    expect(viteConfigSrc).toContain("buildViteDevProxyTable");
    expect(serverSrc).toContain("./config/route-manifest");
    // vite 表已无手写字面量 target 指向引擎端口
    expect(viteConfigSrc).not.toMatch(/target:\s*["']http:\/\/localhost:180\d+["']/);
    // server.ts 已无手写开关分支 / 手写 catch-all 字面量注册
    expect(serverSrc).not.toMatch(/app\.use\(\s*["']\/api\/(dq|datalake|v1\/ecos\/git)["']/);

    // 1) dev 侧：vite 表 = 迁移前 21 条、target 端口逐条等价
    const table = buildViteDevProxyTable({});
    expect(Object.keys(table).sort()).toEqual([...PRE_MIGRATION_VITE_KEYS].sort());
    for (const [prefix, port] of Object.entries(PRE_MIGRATION_VITE_TARGETS)) {
      expect(table[prefix]?.target, `vite target of ${prefix}`).toBe(`http://localhost:${port}`);
    }
    // aiming dev 直连仍注入信任头（F05-14/R-16 等价迁移）
    expect(table["/api/v1/agent-loop"]?.headers).toEqual({
      "X-ECOS-USER": "admin",
      "X-ECOS-TENANT": "default",
    });
    expect(table["/api/v1/cognitive"]?.headers).toBeDefined();
    expect(table["/api/v1/security"]?.headers).toBeUndefined();
    // /api 兜底保留 Authorization 透传 configure
    expect(typeof table["/api"]?.configure).toBe("function");

    // 2) prod/BFF 侧：显式路由与开关分支从同一清单派生
    const explicitFlagOff = selectBffExplicitEntries(false).map((e) => e.prefix);
    expect(explicitFlagOff).toEqual(["/api/monitor", "/api/twins"]);
    const explicitFlagOn = selectBffExplicitEntries(true).map((e) => e.prefix);
    expect(explicitFlagOn).toEqual([
      "/api/monitor",
      "/api/twins",
      "/api/v1/workspace",
      "/api/v1/cognitive",
      "/api/dq",
      "/api/v1/ecos/git",
      "/api/datalake",
    ]);
    expect(selectBffCatchAllEntry().prefix).toBe("/api");
    // 开关分支的引擎/端口与迁移前 server.ts 硬编码分支等价（datanet×3+18082 / workspace 18090 / aiming 18084）
    const byPrefix = new Map<string, RouteManifestEntry>(ROUTE_MANIFEST.map((e) => [e.prefix, e]));
    expect(byPrefix.get("/api/dq")?.port).toBe(18082);
    expect(byPrefix.get("/api/v1/ecos/git")?.port).toBe(18082);
    expect(byPrefix.get("/api/datalake")?.port).toBe(18082);
    expect(byPrefix.get("/api/v1/workspace")?.port).toBe(18090);
    expect(byPrefix.get("/api/v1/cognitive")?.port).toBe(18084);

    // 3) 构建期校验①②③：真实清单 + 真实表必须零错误
    expect(validateRouteManifest(ROUTE_MANIFEST, table)).toEqual([]);
  });

  it("validatorCatchesMutation — 破坏单源等价（删表项/表外项/越权直连）即报错", () => {
    const table = buildViteDevProxyTable({});
    // ① directInDev 项被从 vite 表摘除 ⇒ 校验失败
    const missing = { ...table };
    delete missing["/api/v1/agent-loop"];
    expect(validateRouteManifest(ROUTE_MANIFEST, missing).join("\n")).toContain("directInDev=true 但未生成 vite 表条目");
    // vite 表混入清单外前缀 ⇒ 校验失败
    expect(
      validateRouteManifest(ROUTE_MANIFEST, { ...table, "/api/rogue": { target: "http://evil:1", changeOrigin: true } }).join("\n"),
    ).toContain("清单外前缀");
    // directWhenFlag + alwaysGateway 互斥 ⇒ 校验失败
    const bad: RouteManifestEntry = {
      prefix: "/api/bad",
      engine: "data",
      port: 18082,
      directInDev: false,
      directWhenFlag: true,
      alwaysGateway: true,
      streaming: false,
    };
    expect(validateRouteManifest([...ROUTE_MANIFEST, bad]).join("\n")).toContain("互斥");
    // ③ streaming 前缀不在 /api 之下且不在表内 ⇒ 校验失败
    const orphanStream: RouteManifestEntry = {
      prefix: "/stream-orphan",
      engine: "ai",
      port: 18084,
      directInDev: false,
      directWhenFlag: false,
      alwaysGateway: false,
      streaming: true,
      viteFallback: true,
    };
    const errs = validateRouteManifest([...ROUTE_MANIFEST, orphanStream]);
    expect(errs.length).toBeGreaterThan(0);
  });

  it("prodFlagSemantics — 开关默认 false，仅非生产显式 true 才允许 directWhenFlag 生效（校验②入口）", () => {
    expect(resolveLocalDirectFlag({})).toBe(false);
    expect(resolveLocalDirectFlag({ NODE_ENV: "development", ECOS_LOCAL_DIRECT_SERVICE_PROXY: "true" })).toBe(true);
    // 生产守卫（详细项 4 / W208）：production 一律拒绝
    const errSpy: unknown[] = [];
    const orig = console.error;
    console.error = (...args: unknown[]) => { errSpy.push(args); };
    try {
      expect(resolveLocalDirectFlag({ NODE_ENV: "production", ECOS_LOCAL_DIRECT_SERVICE_PROXY: "true" })).toBe(false);
    } finally {
      console.error = orig;
    }
    expect(errSpy.length).toBe(1);
  });
});
