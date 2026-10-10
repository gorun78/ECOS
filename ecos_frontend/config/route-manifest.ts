/**
 * config/route-manifest.ts — BFF/vite 路由表单源（详细设计-08 §4.1，W194/C176，R-54 ① 已批准）
 *
 * 唯一事实源：`vite.config.ts` 的 dev 代理表与 `server.ts` 的 Express BFF 路由
 * 必须同时从本清单派生，消灭历史上「vite 21 条 + server.ts 开关分支各写一份」的双表漂移。
 *
 * 条目形态（冻结口径，勿改字段语义）：
 *   { prefix, engine, port, directInDev, directWhenFlag, alwaysGateway, streaming }
 *
 * 构建期校验（禁运行期静默，validateRouteManifest 返回错误数组，调用方必须抛错）：
 *   ① directInDev=true 的项必须出现在 vite dev 代理表中；
 *   ② directWhenFlag 仅在 ECOS_LOCAL_DIRECT_SERVICE_PROXY=true 且非 production 时生效
 *      （ADR-10 生产守卫在 resolveLocalDirectFlag 内实现）；
 *   ③ streaming=true 的项必须能落到不缓冲分支
 *      （dev 走 vite 代理直通，或经 server.ts `/api` catch-all 的 forwardProxy
 *        text/event-stream 分支 ⇒ 前缀必须位于 `/api` 下）。
 *
 * 禁止在本文件写入任何本机绝对路径 / 凭据；端口默认值仅为本机 dev 约定（见根 AGENTS.md 端口表）。
 */

import type { ProxyOptions } from "vite";

export type EngineKey =
  | "gateway"
  | "security"
  | "data"
  | "ontology"
  | "ai"
  | "kb"
  | "cognitive"
  | "workspace";

export interface RouteManifestEntry {
  /** 路由前缀（Express app.use / vite proxy key 共用） */
  prefix: string;
  /** 实际持有端点的引擎（gateway 条目 = facade 自身） */
  engine: EngineKey;
  /** 引擎内网端口（gateway=8080；见根 AGENTS.md 端口表，禁本机路径/凭据） */
  port: number;
  /** dev：进 vite 代理表直连引擎端口 */
  directInDev: boolean;
  /** dev 调试：ECOS_LOCAL_DIRECT_SERVICE_PROXY=true 时 BFF 定向直连引擎 */
  directWhenFlag: boolean;
  /** BFF 显式注册且恒走 gateway（不受开关影响；`/api` 条目即 catch-all 兜底） */
  alwaysGateway: boolean;
  /** SSE / 流式前缀：必须走不缓冲分支（校验③） */
  streaming: boolean;
  /** 在 vite 代理表中生成 gateway 兜底条目（/api、/datanet、/cases 这类非直连项） */
  viteFallback?: boolean;
}

/** 引擎 → 上游地址环境变量（与 server.ts 既有 GATEWAY_URL/DATANET_URL/WORKSPACE_URL/AIMING_URL 兼容） */
export const ENGINE_ENV_VARS: Record<EngineKey, string> = {
  gateway: "GATEWAY_URL",
  security: "SECURITY_URL",
  data: "DATANET_URL",
  ontology: "ONTOLOGY_URL",
  ai: "AIMING_URL",
  kb: "KB_URL",
  cognitive: "COGNITIVE_URL",
  workspace: "WORKSPACE_URL",
};

/** 解析上游基址：env 优先，缺省 localhost:约定端口（无本机绝对路径） */
export function resolveUpstream(
  entry: Pick<RouteManifestEntry, "engine" | "port">,
  env: Record<string, string | undefined> = process.env,
): string {
  const fromEnv = env[ENGINE_ENV_VARS[entry.engine]];
  const fallback = `http://localhost:${entry.port}`;
  return (fromEnv ?? fallback).replace(/\/+$/, "");
}

/**
 * dev 直连 :18084（aiming）时注入的受信 dev 信任头（F05-14/R-16 口径，
 * 等价模拟 gateway 下行注入；生产无 vite 路径，未登录访问仍被拒绝）。
 */
export const AIMING_DEV_TRUST_HEADERS: Readonly<Record<string, string>> = Object.freeze({
  "X-ECOS-USER": "admin",
  "X-ECOS-TENANT": "default",
});

/**
 * 唯一事实源。条目集合 = server.ts 显式路由(2)+开关分支(5)+catch-all(1)
 * 与 vite.config.ts 代理表(18 直连 + 3 兜底) 的并集，共 26 条（21 条进 vite 表）。
 * 顺序即 vite proxy 对象的 key 插入顺序（vite 按前缀匹配，顺序保持迁移前后等价）。
 */
export const ROUTE_MANIFEST: readonly RouteManifestEntry[] = Object.freeze([
  // ── BFF 显式 gateway 路由（不进 vite 表：dev 下由 server.ts 先于 vite 注册）──
  { prefix: "/api/monitor", engine: "gateway", port: 8080, directInDev: false, directWhenFlag: false, alwaysGateway: true, streaming: false },
  { prefix: "/api/twins", engine: "gateway", port: 8080, directInDev: false, directWhenFlag: false, alwaysGateway: true, streaming: false },

  // ── vite dev 引擎直连表（18 条，等价迁移自旧 vite.config.ts:24-43）──
  { prefix: "/api/v1/agent-loop", engine: "ai", port: 18084, directInDev: true, directWhenFlag: false, alwaysGateway: false, streaming: true },
  { prefix: "/api/v1/agent-mesh", engine: "ai", port: 18084, directInDev: true, directWhenFlag: false, alwaysGateway: false, streaming: true },
  { prefix: "/api/v1/agent", engine: "ai", port: 18084, directInDev: true, directWhenFlag: false, alwaysGateway: false, streaming: true },
  { prefix: "/api/v1/agent-call", engine: "ai", port: 18084, directInDev: true, directWhenFlag: false, alwaysGateway: false, streaming: true },
  { prefix: "/api/v1/knowledge", engine: "kb", port: 18086, directInDev: true, directWhenFlag: false, alwaysGateway: false, streaming: false },
  { prefix: "/api/v1/security", engine: "security", port: 18081, directInDev: true, directWhenFlag: false, alwaysGateway: false, streaming: false },
  { prefix: "/api/v1/audit", engine: "security", port: 18081, directInDev: true, directWhenFlag: false, alwaysGateway: false, streaming: false },
  { prefix: "/api/v1/abac", engine: "security", port: 18081, directInDev: true, directWhenFlag: false, alwaysGateway: false, streaming: false },
  { prefix: "/api/v1/data-masking", engine: "security", port: 18081, directInDev: true, directWhenFlag: false, alwaysGateway: false, streaming: false },
  { prefix: "/api/v1/data-permission", engine: "security", port: 18081, directInDev: true, directWhenFlag: false, alwaysGateway: false, streaming: false },
  { prefix: "/api/v1/policy-engine", engine: "security", port: 18081, directInDev: true, directWhenFlag: false, alwaysGateway: false, streaming: false },
  { prefix: "/api/v1/engine/data", engine: "data", port: 18082, directInDev: true, directWhenFlag: false, alwaysGateway: false, streaming: false },
  { prefix: "/api/v1/ecos", engine: "ontology", port: 18083, directInDev: true, directWhenFlag: false, alwaysGateway: false, streaming: false },
  // PMO-60：workspace 独立 :18090；cognitive 并入 aiming :18084。两者同时是 BFF 开关分支条目。
  { prefix: "/api/v1/workspace", engine: "workspace", port: 18090, directInDev: true, directWhenFlag: true, alwaysGateway: false, streaming: false },
  { prefix: "/api/v1/cognitive", engine: "ai", port: 18084, directInDev: true, directWhenFlag: true, alwaysGateway: false, streaming: true },
  { prefix: "/api/v1/world-model", engine: "cognitive", port: 18089, directInDev: true, directWhenFlag: false, alwaysGateway: false, streaming: false },
  { prefix: "/api/v1/rules", engine: "kb", port: 18086, directInDev: true, directWhenFlag: false, alwaysGateway: false, streaming: false },
  { prefix: "/api/v1/kb", engine: "kb", port: 18086, directInDev: true, directWhenFlag: false, alwaysGateway: false, streaming: false },

  // ── BFF 开关分支（directWhenFlag，等价迁移自旧 server.ts:144-149；/api/v1/workspace、/api/v1/cognitive 见上）──
  // P3-A：Dq/Git/DataLake 路由物理迁至 datanet :18082（与网关 V1_REWRITE_MAP 同前缀）。
  { prefix: "/api/dq", engine: "data", port: 18082, directInDev: false, directWhenFlag: true, alwaysGateway: false, streaming: false },
  { prefix: "/api/v1/ecos/git", engine: "data", port: 18082, directInDev: false, directWhenFlag: true, alwaysGateway: false, streaming: false },
  { prefix: "/api/datalake", engine: "data", port: 18082, directInDev: false, directWhenFlag: true, alwaysGateway: false, streaming: false },

  // ── gateway 兜底（vite 表 3 条 + BFF catch-all /api，等价迁移自旧两表尾段）──
  { prefix: "/api", engine: "gateway", port: 8080, directInDev: false, directWhenFlag: false, alwaysGateway: true, streaming: false, viteFallback: true },
  { prefix: "/datanet", engine: "gateway", port: 8080, directInDev: false, directWhenFlag: false, alwaysGateway: false, streaming: false, viteFallback: true },
  { prefix: "/cases", engine: "gateway", port: 8080, directInDev: false, directWhenFlag: false, alwaysGateway: false, streaming: false, viteFallback: true },
]) as readonly RouteManifestEntry[];

export const CATCH_ALL_PREFIX = "/api";

/** ADR-10 / PMO-B：ECOS_LOCAL_DIRECT_SERVICE_PROXY 开关语义解析。
 * 字符串 "true"/"1"/"yes"/"on" 视为开启；其它值一律 false。
 * 🔴 生产守卫（W208/C190）：NODE_ENV=production 时即便开关为 true 也强制回落 gateway，
 * 并 console.error 明示「拒绝直连」。返回 false 即 gateway-only。
 */
export function resolveLocalDirectFlag(
  env: Record<string, string | undefined> = process.env,
): boolean {
  const raw = (env.ECOS_LOCAL_DIRECT_SERVICE_PROXY || "").trim().toLowerCase();
  const requested = raw === "true" || raw === "1" || raw === "yes" || raw === "on";
  if (requested && env.NODE_ENV === "production") {
    console.error(
      "[BFF][ADR-10] NODE_ENV=production 时拒绝 ECOS_LOCAL_DIRECT_SERVICE_PROXY=true —— 强制回落 gateway-only（直连仅限本地调试）。",
    );
    return false;
  }
  return requested;
}

/** vite dev 代理表条目 = vite 官方 ProxyOptions（单源类型，杜绝自造子集与 vite 签名漂移；
 * target 必填——清单派生项一律给出上游地址） */
export type ViteProxyEntryLike = ProxyOptions & { target: string };

/** 由清单派生 vite dev 代理表（21 条：18 引擎直连 + /api|/datanet|/cases gateway 兜底）。
 * `/api` 兜底保留 Authorization 透传 configure（等价旧 vite.config.ts:48-54）。 */
export function buildViteDevProxyTable(
  env: Record<string, string | undefined> = process.env,
  manifest: readonly RouteManifestEntry[] = ROUTE_MANIFEST,
): Record<string, ViteProxyEntryLike> {
  const gatewayBase = resolveUpstream({ engine: "gateway", port: 8080 }, env);
  const table: Record<string, ViteProxyEntryLike> = {};
  for (const entry of manifest) {
    if (entry.directInDev) {
      const target = resolveUpstream(entry, env);
      const item: ViteProxyEntryLike = { target, changeOrigin: true };
      if (entry.engine === "ai") {
        item.headers = { ...AIMING_DEV_TRUST_HEADERS };
      }
      table[entry.prefix] = item;
    } else if (entry.viteFallback) {
      const item: ViteProxyEntryLike = { target: gatewayBase, changeOrigin: true };
      if (entry.prefix === CATCH_ALL_PREFIX) {
        item.configure = (proxy) => {
          proxy.on("proxyReq", (proxyReq, req) => {
            const auth = req.headers["authorization"];
            if (auth) proxyReq.setHeader("Authorization", auth);
          });
        };
      }
      table[entry.prefix] = item;
    }
  }
  return table;
}

/** BFF 显式路由条目（先于 vite 注册，不受 vite 截胡）：
 * - alwaysGateway 非 catch-all 条目（/api/monitor、/api/twins）→ 恒走 gateway
 * - directWhenFlag 条目（开关开启且非生产时）→ 定向直连
 * - 开关关闭时这些前缀自然落 catch-all → gateway（默认路径不变） */
export function selectBffExplicitEntries(
  directEnabled: boolean,
  manifest: readonly RouteManifestEntry[] = ROUTE_MANIFEST,
): RouteManifestEntry[] {
  return manifest.filter(
    (e) =>
      (e.alwaysGateway && e.prefix !== CATCH_ALL_PREFIX) ||
      (directEnabled && e.directWhenFlag),
  );
}

/** BFF catch-all 条目 */
export function selectBffCatchAllEntry(
  manifest: readonly RouteManifestEntry[] = ROUTE_MANIFEST,
): RouteManifestEntry {
  const e = manifest.find(
    (m) => m.prefix === CATCH_ALL_PREFIX && m.alwaysGateway,
  );
  if (!e) throw new Error("[route-manifest] 缺少 /api catch-all 条目");
  return e;
}

/** 构建期校验（禁运行期静默）：返回错误列表，空数组 = 通过。调用方（vite.config / server / 门禁测试）非空即抛。 */
export function validateRouteManifest(
  manifest: readonly RouteManifestEntry[] = ROUTE_MANIFEST,
  viteTable?: Record<string, ViteProxyEntryLike>,
): string[] {
  const errors: string[] = [];
  const table = viteTable ?? buildViteDevProxyTable(process.env, manifest);

  // 唯一前缀
  const seen = new Set<string>();
  for (const e of manifest) {
    if (seen.has(e.prefix)) errors.push(`重复前缀: ${e.prefix}`);
    seen.add(e.prefix);
    // 形态不变式
    if (e.directWhenFlag && e.alwaysGateway) {
      errors.push(`${e.prefix}: directWhenFlag 与 alwaysGateway 互斥`);
    }
    if (!e.directInDev && !e.directWhenFlag && !e.alwaysGateway && !e.viteFallback) {
      errors.push(`${e.prefix}: 无任何路由归属（dev/开关/gateway/vite 兜底均为 false）`);
    }
    // ② 开关直连条目必须能被 catch-all 兜住（开关关闭/生产守卫时回落 gateway）
    if (e.directWhenFlag && !manifest.some((m) => m.prefix === CATCH_ALL_PREFIX && m.alwaysGateway)) {
      errors.push(`${e.prefix}: directWhenFlag 但清单缺少 /api gateway catch-all 兜底`);
    }
    // ③ streaming 必须落到不缓冲分支：dev 直通（在 vite 表）或经 server.ts `/api` catch-all 的
    //    forwardProxy text/event-stream 分支 ⇒ 前缀必须位于 `/api` 之下；否则 prod 冷启动下
    //    该前缀无 BFF 归属（只会被 SPA 兜底吞掉），属于双表漂移族，构建期即拒绝。
    if (e.streaming && !e.prefix.startsWith(CATCH_ALL_PREFIX)) {
      errors.push(`${e.prefix}: streaming 前缀不在 /api 不缓冲 catch-all 之下（dev/prod 承流路径会漂移）`);
    }
    // ③b vite 兜底条目（如 /datanet、/cases）历史上即无 BFF 显式承流；
    //     新增 /api 之外的 alwaysGateway vite 兜底条目会造成 dev 有路由、prod 无路由的漂移，禁止。
    if (
      e.viteFallback &&
      e.alwaysGateway &&
      e.prefix !== CATCH_ALL_PREFIX &&
      !e.directWhenFlag
    ) {
      errors.push(`${e.prefix}: /api 之外的 vite 兜底 + alwaysGateway，但 BFF 无对应显式承流路由（单源等价破坏）`);
    }
  }

  // ① directInDev 项必须生成进 vite 表；无 env 覆写时必须指向引擎默认端口
  for (const e of manifest) {
    if (!e.directInDev) continue;
    const item = table[e.prefix];
    if (!item) {
      errors.push(`① ${e.prefix}: directInDev=true 但未生成 vite 表条目`);
      continue;
    }
    const envOverride = process.env[ENGINE_ENV_VARS[e.engine]];
    if (!envOverride && !item.target.includes(`:${e.port}`)) {
      errors.push(`① ${e.prefix}: vite 表 target=${item.target} 未指向引擎端口 ${e.port}`);
    }
  }

  // vite 表中不得混入未登记前缀
  for (const key of Object.keys(table)) {
    if (!seen.has(key)) errors.push(`vite 表存在清单外前缀: ${key}`);
  }

  return errors;
}
