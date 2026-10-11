/**
 * cognitiveClient — 认知域唯一 REST 通道（design-05 B 章 / F05-17 / F05-08 / F05-01）
 *
 * <p>本文件是前端所有 `/api/v1/cognitive*` 与
 * `/api/v1/business/scenarios/{id}/cognition*` 静态前缀常量 + 4 契约端点
 * （E1 detect / E2 operation-eval / E3 hypotheses / E4 beliefs）的**唯一**
 * 出口。既有 {@link cognitiveEngineApi}（17-controller 面）通过具名 re-export
 * 与本文件 alias，不引入第二套 auth 头拼接、不重复实现 fetch。</p>
 *
 * <p>auth 单源：所有 fetch 均经 {@link getAuthToken}（`services/auth.ts`）
 * 拼 Bearer，禁直接读 localStorage['token'] / sessionStorage['jwt']。</p>
 *
 * <p>错误矩阵（与后端 `CognitiveErrorMatrixIntegrationTest` 对齐）：
 * ① 场景不存在 → 404 NOT_FOUND ② 已软删 → 404 NOT_FOUND
 * ③ 参数非法 / mind 不属场景 / mind 不存在 → 400 BAD_REQUEST
 * ④ 引擎未启用 → 503 ENGINE_DISABLED ⑤ schema 未就绪 → 503 SCHEMA_NOT_READY
 * ⑥ 未实现 → 501 + `stub=true` STUB。</p>
 *
 * @license Apache-2.0
 */

import { getAuthToken } from "./auth";

// ── 轮询 / 防抖常量（design-05 B.6 / X-52 契约要求，唯一命名源，禁散落魔法数）────

/** 认知列表轮询周期：契约要求 30 秒（现网曾 10s/3s 违例，统一收敛至此常量）。 */
export const COGNITION_POLL_INTERVAL_MS = 30_000;

/** 重复触发防抖窗口：契约要求 100–300ms，取中值 200ms。 */
export const COGNITION_DEBOUNCE_MS = 200;

/** E3/E4 列表分页默认条数（后端契约默认 10）。 */
export const COGNITION_DEFAULT_LIMIT = 10;

/** E3/E4 列表分页上限（后端契约上限 50，超限后端截断）。 */
export const COGNITION_MAX_LIMIT = 50;

/**
 * 尾部防抖：在 `wait` 毫秒内的重复调用合并为最后一次执行。
 * 返回的函数附带 `.cancel()` 以便组件卸载时清理定时器（性能规范 §五）。
 */
export function debounce<A extends unknown[]>(
  fn: (...args: A) => void,
  wait = COGNITION_DEBOUNCE_MS,
): ((...args: A) => void) & { cancel: () => void } {
  let timer: ReturnType<typeof setTimeout> | null = null;
  const debounced = (...args: A): void => {
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => {
      timer = null;
      fn(...args);
    }, wait);
  };
  debounced.cancel = (): void => {
    if (timer) {
      clearTimeout(timer);
      timer = null;
    }
  };
  return debounced;
}

/** 归一分页 limit：`opts.limit` 缺省 → 10；钳制到 [1, 50]（禁无 limit 全量拉取，X-50）。 */
export function clampCognitionLimit(limit?: number): number {
  const raw = typeof limit === "number" && Number.isFinite(limit) ? Math.floor(limit) : COGNITION_DEFAULT_LIMIT;
  return Math.min(COGNITION_MAX_LIMIT, Math.max(1, raw));
}

// ── 静态前缀常量（唯一出口）────────────────────────

/** 认知引擎裸域前缀（旧 17-controller 端点，`/api/v1/cognitive/*`）。 */
export const COGNITIVE_BASE = "/api/v1/cognitive";

/** 场景级认知契约前缀（PRD-05 E1~E4，`/api/v1/business/scenarios/{id}/cognition/[...]`）。 */
export const SCENARIO_COGNITION_BASE = "/api/v1/business/scenarios";

/** 认知引擎健康/启停前缀（AiEngineStatusController）。 */
export const COGNITIVE_ENGINE_BASE = "/api/v1/engine/cognitive";

/** 世界模型前缀（world-model 四端点）。 */
export const WORLD_MODEL_BASE = "/api/v1/world-model";

/** 构造场景认知子路径：`/api/v1/business/scenarios/{id}/cognition[/suffix]`。
 *  `suffix` 应以 `/` 开头（如 `/detect`）或为空（前缀末尾）。 */
export function scenarioCognitionUrl(scenarioId: string, suffix: string = ""): string {
  return `${SCENARIO_COGNITION_BASE}/${encodeURIComponent(scenarioId)}/cognition${suffix}`;
}

// ── 统一 API 错误类型 ────────────────────────────────

/** 错误码 — 与 F05-07 后端错误矩阵 6 行 + STUB / NETWORK / BUSINESS_ERROR 对齐。 */
export type CognitiveErrorCode =
  | "NOT_FOUND"        // 404 场景不存在 / 软删
  | "BAD_REQUEST"      // 400 mind 不属场景 / mind 不存在 / 参数非法
  | "ENGINE_DISABLED"  // 503 认知引擎未启用
  | "SCHEMA_NOT_READY" // 503 心智层表未建（`schema not ready`）
  | "STUB"             // 501 + stub = true
  | "HTTP_ERROR"       // 其他 HTTP 错误
  | "BUSINESS_ERROR"   // 200 但 success=false / code!=200
  | "NETWORK";         // 网络层不可用

export interface CognitiveApiErrorInfo {
  code: CognitiveErrorCode;
  status: number;
  message: string;
  /** 501 stub 端点标记 true，UI 应渲染占位而非错误 */
  stub?: boolean;
  /** 若响应头/体携带 traceId 则透传到 UI 复制按钮 */
  traceId?: string;
}

/** 认知域统一 API 错误；调用方按 `code` 分支渲染 7 态矩阵之一。 */
export class CognitiveApiError extends Error {
  readonly code: CognitiveErrorCode;
  readonly status: number;
  readonly stub: boolean;
  readonly traceId?: string;
  constructor(info: CognitiveApiErrorInfo) {
    super(info.message);
    this.name = "CognitiveApiError";
    this.code = info.code;
    this.status = info.status;
    this.stub = info.stub ?? false;
    this.traceId = info.traceId;
  }
}

/** 断言错误是否为 `CognitiveApiError`。 */
export function isCognitiveApiError(e: unknown): e is CognitiveApiError {
  return e instanceof CognitiveApiError;
}

// ── 内部 fetch（auth 单源）────────────────────────────

/**
 * 认知域唯一 fetch/auth 通道。导出仅为供 `services/cognitive/*` 拆分模块复用
 * （禁拆出模块自拼 Bearer 头 / 自起 fetch），组件面不应直接调用它。
 */
export async function callJson(url: string, init: RequestInit = {}): Promise<unknown> {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  const token = getAuthToken();
  if (token) headers["Authorization"] = `Bearer ${token}`;
  const mergedHeaders: Record<string, string> = {
    ...headers,
    ...((init.headers as Record<string, string> | undefined) ?? {}),
  };
  let res: Response;
  try {
    res = await fetch(url, { ...init, headers: mergedHeaders });
  } catch (e) {
    throw new CognitiveApiError({
      code: "NETWORK",
      status: 0,
      message: e instanceof Error ? e.message : String(e),
    });
  }
  const text = await res.text().catch(() => "");
  let body: unknown = null;
  if (text) {
    try { body = JSON.parse(text); } catch { /* 非 JSON 响应，保持 null */ }
  }
  const extract = (b: unknown): { msg: string; traceId?: string } => {
    if (b && typeof b === "object") {
      const o = b as Record<string, unknown>;
      const traceId =
        (res.headers.get("X-Trace-Id") ??
          (typeof o.traceId === "string" ? o.traceId : undefined)) as string | undefined;
      return {
        msg: typeof o.message === "string" ? o.message : text || `HTTP ${res.status}`,
        traceId,
      };
    }
    return { msg: text || `HTTP ${res.status}`, traceId: res.headers.get("X-Trace-Id") ?? undefined };
  };
  if (res.status === 404) {
    const { msg, traceId } = extract(body);
    throw new CognitiveApiError({ code: "NOT_FOUND", status: 404, message: msg, traceId });
  }
  if (res.status === 400) {
    const { msg, traceId } = extract(body);
    throw new CognitiveApiError({ code: "BAD_REQUEST", status: 400, message: msg, traceId });
  }
  if (res.status === 503) {
    const { msg, traceId } = extract(body);
    const schemaNotReady = /schema not ready|SCHEMA_NOT_READY/i.test(msg);
    throw new CognitiveApiError({
      code: schemaNotReady ? "SCHEMA_NOT_READY" : "ENGINE_DISABLED",
      status: 503,
      message: msg,
      traceId,
    });
  }
  if (res.status === 501) {
    const { msg, traceId } = extract(body);
    throw new CognitiveApiError({ code: "STUB", status: 501, message: msg, stub: true, traceId });
  }
  if (!res.ok) {
    const { msg, traceId } = extract(body);
    throw new CognitiveApiError({ code: "HTTP_ERROR", status: res.status, message: msg, traceId });
  }
  // 200：{success,data} / {code,data} / 裸 data 三形态兼容
  if (body && typeof body === "object") {
    const o = body as Record<string, unknown>;
    if (o.success === false) {
      throw new CognitiveApiError({
        code: "BUSINESS_ERROR",
        status: res.status,
        message: typeof o.message === "string" ? o.message : "Request failed",
      });
    }
    if (typeof o.code === "number" && o.code !== 200 && o.code !== 0) {
      throw new CognitiveApiError({
        code: "BUSINESS_ERROR",
        status: res.status,
        message: typeof o.message === "string" ? o.message : `Error ${o.code}`,
      });
    }
    return o.data !== undefined ? o.data : body;
  }
  return body;
}

/** 形状归一：数组直取，`{rows}` / `{data}` 包装取其数组，其余一律空集（DG-6 禁造元素）。 */
export function asArray<T>(v: unknown): T[] {
  if (Array.isArray(v)) return v as T[];
  if (v && typeof v === "object") {
    const o = v as Record<string, unknown>;
    if (Array.isArray(o.rows)) return o.rows as T[];
    if (Array.isArray(o.data)) return o.data as T[];
  }
  return [];
}

// ── 契约响应形状（design-05 §5 E1~E4）──────────────────

/** E1 `detect` 行：一个可用 Mind。 */
export interface CognMindRow {
  mindId: string;
  name: string;
  capabilityMask?: string[];
  closedLoopBounds?: Array<"PRE" | "POST">;
  isActive?: boolean;
  [key: string]: unknown;
}

/** E2 `operation-eval` 行。 */
export interface OperationEvalRow {
  name?: string;
  capability?: string;
  /** 0~1 评分（本期 `trend_weight` 恒 1，score = belief.probability） */
  score?: number;
  /** 本期恒 FLAT（`trendSource=NOT_IMPLEMENTED`），禁伪造 */
  trend?: "UP" | "DOWN" | "FLAT";
  trendSource?: string;
  [key: string]: unknown;
}

/** E2 响应体：rows + 截断标记（`limit>50` 后端硬截 → `truncated=true`）。 */
export interface OperationEvalResponse {
  rows: OperationEvalRow[];
  truncated?: boolean;
}

/** E3 `hypotheses` 行。 */
export interface HypothesisRow {
  hypothesisId?: string;
  mindId?: string;
  reason?: string;
  confidence?: number;
  status?:
    | "PROPOSED"
    | "EVIDENCED"
    | "BELIEVED"
    | "REFUTED"
    | "VALID"
    | "INVALIDATED"
    | "ARCHIVED"
    | (string & {});
  updatedAt?: string;
  [key: string]: unknown;
}

/** E4 `beliefs` 行；`capability=null` 表示未映射（前端必须保留行并显示 t('scenario.cognition.unset')）。 */
export interface BeliefRow {
  variableName?: string;
  domain?: string;
  /** NUMERIC(18,4) 0~1 */
  probability?: number;
  /** NUMERIC(18,4) 强度 */
  strength?: number;
  capability?: string | null;
  updatedAt?: string;
  [key: string]: unknown;
}

// ── 4 契约端点 E1~E4 ─────────────────────────────────

/** E1: 探测场景可用 Mind。 */
export async function detectScenarioMinds(scenarioId: string): Promise<CognMindRow[]> {
  const data = await callJson(scenarioCognitionUrl(scenarioId, "/detect"));
  return asArray<CognMindRow>(data);
}

/** E2: 默认 limit=10；`limit∈[1,50]`，`limit>50` 后端截断 50 并 `truncated=true`。
 *  mind 不属于该场景 → 后端 400 `mind not found on this scenario`（前端传 0/null 时用场景全部）。 */
export async function fetchOperationEval(
  scenarioId: string,
  opts: { mind?: string; limit?: number } = {},
): Promise<OperationEvalResponse> {
  // E2 客户端仅钳下限（保留后端 truncated 信号），缺省复用契约默认值常量，避免散落魔数。
  const limit = Math.max(1, Math.floor(opts.limit ?? COGNITION_DEFAULT_LIMIT));
  const q = new URLSearchParams();
  if (opts.mind) q.set("mind", opts.mind);
  q.set("limit", String(limit));
  const url = `${scenarioCognitionUrl(scenarioId, "/operation-eval")}?${q.toString()}`;
  const data = await callJson(url);
  const rows = asArray<OperationEvalRow>(
    data && typeof data === "object" && "rows" in (data as Record<string, unknown>)
      ? (data as Record<string, unknown>).rows
      : data,
  );
  const truncated =
    !!data && typeof data === "object" && (data as { truncated?: boolean }).truncated === true;
  return { rows, truncated };
}

/** E3: 假设列表（带 `mind` 严格过滤 → 该 mind；不传 mind → 场景全部）。
 *  恒 `is_deleted=0`；按 `updatedAt desc`。
 *  `?limit=` **必传**（design-05 B.6 / X-50 禁无 limit 全量拉取）：默认 10、钳制 [1,50]。 */
export async function fetchScenarioHypotheses(
  scenarioId: string,
  opts: { mind?: string; limit?: number } = {},
): Promise<HypothesisRow[]> {
  const q = new URLSearchParams();
  if (opts.mind) q.set("mind", opts.mind);
  q.set("limit", String(clampCognitionLimit(opts.limit)));
  const url = `${scenarioCognitionUrl(scenarioId, "/hypotheses")}?${q.toString()}`;
  const data = await callJson(url);
  return asArray<HypothesisRow>(data);
}

/** E4: 信念列表（带 `mind` 严格过滤；`capability=null` 行保留不丢弃，前端显示未映射）。
 *  未映射行后端写入 `data.issues[]`（可观测），前端透传即可。
 *  `?limit=` **必传**（design-05 B.6 / X-50）：默认 10、钳制 [1,50]。 */
export async function fetchScenarioBeliefs(
  scenarioId: string,
  opts: { mind?: string; limit?: number } = {},
): Promise<{ rows: BeliefRow[]; issues?: string[] }> {
  const q = new URLSearchParams();
  if (opts.mind) q.set("mind", opts.mind);
  q.set("limit", String(clampCognitionLimit(opts.limit)));
  const url = `${scenarioCognitionUrl(scenarioId, "/beliefs")}?${q.toString()}`;
  const data = await callJson(url);
  let rows: BeliefRow[] = [];
  let issues: string[] | undefined;
  if (Array.isArray(data)) rows = data as BeliefRow[];
  else if (data && typeof data === "object") {
    const o = data as Record<string, unknown>;
    rows = asArray<BeliefRow>(o.rows ?? o.data);
    if (Array.isArray(o.issues)) issues = o.issues as string[];
  }
  return { rows, issues };
}

// ── 认知资产目录 / 只读解析（补充设计 §8、§13 SUP-COG-01/02；C/K 互斥守护：只读引用态）─
//
// catalog = 模式/词表权威（分层图例 A/B/C + 治理资产类 + 12 态生命周期，全部服务端单源下发）；
// 资产**实例**来自 governed-class 既有列表端点（fetchBeliefs / fetchHypotheses / fetchModels）。
// 本模块**不含任何 KB 写入路径**（§8.4「不得把 C 类型经 KB 写通道静默落成 K 资产」）——
// 前端只消费 catalog/resolve/proposals，proposals 走认知域自己的候选状态机，不写知识表。

/** GET catalog —— 唯一路径常量，禁散落字面量（纳入 cognitivePaths 单源测试）。 */
export const COGNITIVE_ASSET_CATALOG_PATH = `${COGNITIVE_BASE}/assets/catalog`;

/** resolve 路径构造；前缀 bel/hyp/mod 由后端 parseRef 校验（非法 400 / 查不到 404）。 */
export function cognitiveAssetResolvePath(assetId: string): string {
  return `${COGNITIVE_BASE}/assets/${encodeURIComponent(assetId)}/resolve`;
}

/** 生命周期词表 + 治理类均由服务端 catalog 单源下发；前端不复制枚举值，仅做 i18n 显示映射。 */
export interface CognitiveAssetCatalog {
  /** { A, B, C } → 分层图例文案（服务端下发，只读） */
  layers: Record<string, string>;
  /** C 类对象目录（PRD-05 REQ-COG-06 11 对象） */
  objects: unknown[];
  /** 治理资产类：[BELIEF, HYPOTHESIS, MODEL] */
  governedAssetClasses: string[];
  /** §8.4 生命周期 12 态（服务端 CognitiveAssetStatus 单源） */
  lifecycleStates: string[];
}

/** resolve 单条只读 DTO（字段与服务端 setConfidenceTriplet / resolve 逐字对齐）。 */
export interface CognitiveAssetResolution {
  assetId: string;
  assetClass: string;
  assetType: string;
  /** lifecycle_state（V268 权威列）；null → legacy-fallback，禁伪造接管态 */
  lifecycleState: string | null;
  lifecycleSource: string;
  legacyStatus: string | null;
  /** 以下三项 belief 专属（§8.3 置信三分） */
  confidenceProbability?: number | null;
  confidenceKind?: string | null;
  calibrationRef?: string | null;
  objectKey?: string | null;
}

/** 拉认知资产目录（模式/词表；只读、零 SQL）。 */
export async function fetchCognitiveAssetCatalog(): Promise<CognitiveAssetCatalog> {
  const data = await callJson(COGNITIVE_ASSET_CATALOG_PATH);
  const o = (data && typeof data === "object" ? data : {}) as Record<string, unknown>;
  return {
    layers: (o.layers as Record<string, string>) ?? {},
    objects: asArray<unknown>(o.objects),
    governedAssetClasses: asArray<string>(o.governedAssetClasses),
    lifecycleStates: asArray<string>(o.lifecycleStates),
  };
}

/** 只读解析单条认知资产（按前缀路由；查不到抛 404，非法前缀抛 400，均由 callJson 归一）。 */
export async function resolveCognitiveAsset(assetId: string): Promise<CognitiveAssetResolution> {
  const data = await callJson(cognitiveAssetResolvePath(assetId));
  return (data ?? {}) as CognitiveAssetResolution;
}

// ── 候选提案 / 受控状态迁移（§3.5 候选评审面；后端 SUP-COG-03/§8.4，2026-10-11 契约实核）──
//
// 路径单源：`${COGNITIVE_BASE}/assets/{id}/proposals` 与 `.../transitions`
// （对齐 CognitiveApiPaths.ASSETS_PROPOSALS_REL / ASSETS_TRANSITION_REL）。
// proposals 只创建候选 + 审计留痕，原资产行 0 改；transitions 合法迁移才落
// lifecycle_state，非法迁移 ⇒ 409 ECOS-COG-409S（callJson 归一为 HTTP_ERROR，
// `status===409` 可供 UI 分支；消费面禁出现 `/api` 字面量）。

/** proposals 路径构造（POST；创建候选提案，不改原资产）。 */
export function cognitiveAssetProposalsPath(assetId: string): string {
  return `${COGNITIVE_BASE}/assets/${encodeURIComponent(assetId)}/proposals`;
}

/** transitions 路径构造（POST；受控状态迁移，非法迁移 409）。 */
export function cognitiveAssetTransitionPath(assetId: string): string {
  return `${COGNITIVE_BASE}/assets/${encodeURIComponent(assetId)}/transitions`;
}

/** 提案请求体（AssetProposalDTO 逐字对齐：targetState/reason/evidenceRef/operator/note）。 */
export interface CognitiveAssetProposalInput {
  targetState: string;
  reason: string;
  evidenceRef?: string;
  operator?: string;
  note?: string;
}

/** 提案响应（服务端单源字段；status 恒为 PENDING_APPROVAL，UI 不得本地合成其他值）。 */
export interface CognitiveAssetProposalReceipt {
  proposalId?: string;
  assetId?: string;
  status?: string;
  carrier?: string;
  eventRef?: string;
}

/** 迁移请求体（§8.4 四要件；SUPERSEDED 时 reason 须含 `supersededBy=<引用>`，由后端校验）。 */
export interface CognitiveAssetTransitionInput {
  targetState: string;
  reason: string;
  evidenceRef?: string;
  operator?: string;
}

/** 迁移响应（fromState/toState 由服务端回填；409 走异常不返回本形状）。 */
export interface CognitiveAssetTransitionResult {
  assetId?: string;
  assetClass?: string;
  fromState?: string;
  toState?: string;
  traceId?: string;
  eventRef?: string;
}

/** 提交候选提案（SUP-COG-03：只生成候选与审计事件，绝不改动原资产行）。 */
export async function submitAssetProposal(
  assetId: string,
  input: CognitiveAssetProposalInput,
): Promise<CognitiveAssetProposalReceipt> {
  const data = await callJson(cognitiveAssetProposalsPath(assetId), {
    method: "POST",
    body: JSON.stringify(input),
  });
  return (data ?? {}) as CognitiveAssetProposalReceipt;
}

/** 受控状态迁移（后端状态机是唯一权威；非法迁移抛 CognitiveApiError(status=409)）。 */
export async function transitionCognitiveAsset(
  assetId: string,
  input: CognitiveAssetTransitionInput,
): Promise<CognitiveAssetTransitionResult> {
  const data = await callJson(cognitiveAssetTransitionPath(assetId), {
    method: "POST",
    body: JSON.stringify(input),
  });
  return (data ?? {}) as CognitiveAssetTransitionResult;
}

// ── Context Builder（设计方案 §3.4 步骤 2 资产盘点；补充设计 §5.2，POST /context/build）──
//
// 2026-10-11 网关实核：请求三要素 = scenarioId + scope.tenantId + requiredAssets[]
// （缺任一 ⇒ 400 ECOS-COG-400S，envelope data 携带 {traceId, guard, errorCode, missing}）；
// 成功 = ContextBuildResult 装配壳（contextDigest sha256 / ready / truncated / ttl /
// createdAt / expiresAt + pack 本体）。错误族（ContextBuildErrorAdvice，@Order(1) 先于
// 网关全局 handler）：403 ECOS-COG-403S / 409 ECOS-COG-409V / 422 ECOS-COG-422C /
// 429 ECOS-COG-429B / 503 ECOS-COG-503D / 400 ECOS-COG-400S。
// ready=false 时 Run 侧不得继续依赖步骤——UI 如实呈现，禁伪就绪。

/** context/build 唯一路径（对齐 CognitiveApiPaths.CONTEXT_BUILD；纳入单源守卫）。 */
export const CONTEXT_BUILD_PATH = `${COGNITIVE_BASE}/context/build`;

/** 权威源类型（后端 ContextSourceRef.sourceType 词表；来自 §5.2 请求示例单源）。 */
export const CONTEXT_SOURCE_TYPES = ["DATA_ASSET", "ONTOLOGY", "KNOWLEDGE", "COGNITIVE", "CAPABILITY"] as const;
export type ContextSourceType = (typeof CONTEXT_SOURCE_TYPES)[number];

/** 必需资产条目输入（SUP-CTX-01：缺 id/版本/时点三要素后端 400，前端只做提示不代行校验）。 */
export interface ContextSourceRefInput {
  sourceType: string;
  sourceId: string;
  version?: string;
  required?: boolean;
  successCritical?: boolean;
  evidenceRefs?: string[];
}

/** 构建请求（ContextBuildRequest 字段镜像；仅必填外可缺省，由服务端补默认）。 */
export interface ContextBuildInput {
  requestId?: string;
  scenarioId: string;
  blueprintRef?: { blueprintId?: string; version?: string };
  goal?: { type?: string; statement?: string; successCriteria?: string[]; stopConditions?: string[] };
  scope: {
    tenantId: string;
    projectIds?: string[];
    departmentIds?: string[];
    periods?: string[];
    caliberId?: string;
    asOfTime?: string;
  };
  requiredCapabilities?: string[];
  requiredAssets: ContextSourceRefInput[];
  include?: Partial<Record<"dataAssets" | "ontology" | "knowledge" | "cognitiveAssets" | "modelCapabilities", boolean>>;
  budget?: { maxTokens?: number; maxSources?: number; maxLatencyMs?: number };
  expectedAsOfTime?: string;
  ttlMillis?: number;
}

/** Gap（§6.5 词表：category 9 族 / severity 4 档 / status 4 态；开放字符串防词表漂移）。 */
export interface ContextGapView {
  gapId?: string;
  category?: string;
  severity?: string;
  targetRef?: string;
  description?: string;
  status?: string;
  requiresApproval?: boolean;
  resolutionRef?: string;
  suggestedActions?: string[];
}

/** 构建响应（ContextBuildResult 装配壳；pack 原样保留供逐面读取，禁前端合成字段）。 */
export interface ContextBuildResultDto {
  contextDigest?: string;
  ttlMillis?: number;
  scenarioId?: string;
  blueprintRef?: { blueprintId?: string; version?: string };
  scopeRef?: Record<string, unknown>;
  requestId?: string;
  createdAt?: string;
  expiresAt?: string;
  ready: boolean;
  truncated: boolean;
  pack?: Record<string, unknown>;
}

/** pack 数组安全读取（缺失/非数组 → 空集，仅用于列表渲染，不当作服务端断言）。 */
export function packList(result: ContextBuildResultDto | null, key: string): unknown[] {
  const v = result?.pack?.[key];
  return Array.isArray(v) ? v : [];
}

/**
 * 发起上下文构建（步骤 2 资产盘点）。错误经 callJson 归一为 CognitiveApiError，
 * UI 按 `status`（403/409/422/429/503/400）分支渲染错误家族，message/traceId 原样呈现。
 */
export async function buildScenarioContext(input: ContextBuildInput): Promise<ContextBuildResultDto> {
  const data = await callJson(CONTEXT_BUILD_PATH, {
    method: "POST",
    body: JSON.stringify(input),
  });
  const o = (data && typeof data === "object" ? data : {}) as Record<string, unknown>;
  return {
    contextDigest: typeof o.contextDigest === "string" ? o.contextDigest : undefined,
    ttlMillis: typeof o.ttlMillis === "number" ? o.ttlMillis : undefined,
    scenarioId: typeof o.scenarioId === "string" ? o.scenarioId : undefined,
    blueprintRef: o.blueprintRef as ContextBuildResultDto["blueprintRef"],
    scopeRef: o.scopeRef as Record<string, unknown> | undefined,
    requestId: typeof o.requestId === "string" ? o.requestId : undefined,
    createdAt: typeof o.createdAt === "string" ? o.createdAt : undefined,
    expiresAt: typeof o.expiresAt === "string" ? o.expiresAt : undefined,
    ready: o.ready === true,
    truncated: o.truncated === true,
    pack: o.pack as Record<string, unknown> | undefined,
  };
}

// ── W→D 结果评估 / 世界模型推演已拆出 ────────────────────────────
//
// 该面（OUTCOMES_BASE_PATH / outcomesByRunPath / outcomesListPath / worldModelUrl /
// OutcomeEvaluateResultDto 及 fetchOutcomesByRun、fetchOutcomesByScenario、
// runWorldModelScenarioSimulation）现居 `services/cognitive/outcomesApi.ts`：
// 单文件 ≤800 行门禁（前端开发规范 §十 / file-size.test.ts）要求拆分，且拆出模块
// 仍复用本文件的 callJson 唯一 fetch/auth 通道与 COGNITIVE_BASE / WORLD_MODEL_BASE
// 路径单源常量（禁第二处字面量、禁第二套 Bearer 拼接）。

// ── alias：既有 17-controller 端点 ───────────────────
//
// 为保持既有 importer（pages/cos/*、CognitiveEngineView、MentalReviewTab、
// OverrideTab 等）无需修改，本文件 re-export `cognitiveEngineApi` 单源聚合。
// 该 module 内所有 fetch 调用经 `apiFetchData`（内部只走 `getAuthToken()` 单源
// token 读取）；本文件 4 契约端点走独立 `callJson`（同样经 `getAuthToken()`）。
// 2 个通道的 auth 读取路径完全等价（都是 `services/auth.ts#getAuthToken()`），
// 均不直接读 localStorage。

export {
  // 既有 17-controller：API 对象聚合 + 逐具名导出（importer 续用）
  cognitiveEngineApi,
  runDiagnosis,
  fetchDiagnosisHistory,
  runScenario,
  fetchScenarios,
  compareScenarios,
  runCounterfactual,
  fetchHypotheses,
  fetchHypothesis,
  createHypothesis,
  invalidateHypothesis,
  fetchEvidence,
  fetchEvidenceById,
  createEvidence,
  fetchBeliefs,
  fetchBelief,
  createBelief,
  updateBeliefByEvidence,
  overrideBelief,
  replayBelief,
  runForecast,
  fetchModels,
  fetchModelById,
  createModel,
  fetchMentalReviews,
  createPipeline,
  fetchPipelines,
  fetchPipelineDetail,
  executePipeline,
  deletePipeline,
  fetchPipelineExecution,
  createPlan,
  fetchPlan,
  runOptimize,
  recordDecision,
  linkDecision,
  fetchSimilarDecisions,
  fetchDecisionChain,
  fetchDecisionImpact,
  checkDecisionRules,
  fetchProvenance,
  fetchWorldState,
  runWorldScenarios,
  recommendStrategy,
  fetchCausalGraph,
  fetchEngineHealth,
  fetchCognitiveHealth,
  fetchEngineStatus,
  startEngine,
  stopEngine,
  fetchEngineSettings,
  fetchEngineSettingsDefaults,
  refreshEngineSettings,
  updateEngineSettings,
  compileContext,
  fetchIndexStatus,
  fetchMeshAgents,
  fetchMeshMissions,
  routeIntent,
  validateGuardrails,
  fetchGuardrailPolicies,
  createGuardrailPolicy,
  deleteGuardrailPolicy,
  executeAction,
  fetchAvailableActions,
} from "./cognitiveEngineApi";