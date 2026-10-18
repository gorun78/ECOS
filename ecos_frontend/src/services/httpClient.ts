/**
 * HTTP 传输原语单源层 (PMO-74.6 H6-T2) — 自 src/api.ts 逐字迁入，行为不变。
 * src/api.ts 通过 re-export 保持既有 `from "./api"` 导入面。
 *
 * auth 单源 (design-05 X-51 / B.9-2)：三处 fetch 的 Bearer token 一律经
 * `services/auth.ts#getAuthToken()`（三 key union）读取，禁直读 `localStorage['token']`。
 */

import { clearAuthSession, getAuthToken } from "./auth";
import { tStatic } from "../components/LanguageContext";

export const API_BASE = "/api";

// ═══════════════════════════════════════════════
//  PMO-43: Network error handling primitives
// ═══════════════════════════════════════════════

/** Thrown when a network-level failure occurs (backend down, offline, unreachable). */
export class NetworkError extends Error {
  constructor(message = "Network error: service unavailable", original?: unknown) {
    super(message);
    this.name = "NetworkError";
    if (original instanceof Error) this.cause = original;
  }
}

/**
 * Wrap a promise chain so that network-level failures propagate visibly
 * (instead of being swallowed by a Promise chain) while still notifying the
 * global network-down UI. Business errors (thrown as Error with a message)
 * re-throw as-is; the caller's own catch remains responsible.
 */
export function wrapNet(p: Promise<unknown>): Promise<unknown> {
  return p.then(
    v => v,
    (e: unknown) => {
      const network = e instanceof NetworkError ||
        e instanceof TypeError ||
        (typeof navigator !== "undefined" && navigator.onLine === false);
      if (network) void notifyNetworkDown(e);
      if (network && !(e instanceof NetworkError)) throw new NetworkError(undefined, e);
      throw e;
    }
  );
}

/** Check response status for auth failure — only 401 triggers logout, 403 is a permission issue */
export function isErrorResponse(e: unknown): { message: string; kind: "network" | "http" | "unknown"; status?: number } {
  if (e instanceof NetworkError) return { message: e.message, kind: "network" };
  if (e instanceof TypeError) return { message: tStatic("common.status.offline_desc"), kind: "network" };
  if (e instanceof Error) {
    const status = typeof (e as { status?: unknown }).status === "number" ? (e as { status?: number }).status : undefined;
    if (typeof status === "number") return { message: e.message, kind: "http", status };
    return { message: e.message, kind: "unknown" };
  }
  return { message: String(e ?? "Unknown error"), kind: "unknown" };
}

/**
 * Publish a `ecos-network-down` CustomEvent so global UI layers
 * (NetworkErrorBanner) can react to backend/network failures.
 */
export async function notifyNetworkDown(e?: unknown): Promise<void> {
  if (typeof window === "undefined") return;
  try {
    const detail: Record<string, unknown> = { timestamp: Date.now() };
    if (e && typeof e === "object") {
      const record = e as { status?: number; message?: string };
      if (record.status !== undefined) detail.status = record.status;
      if (record.message) detail.message = record.message;
      if (e instanceof NetworkError) detail.kind = "network";
    }
    window.dispatchEvent(new CustomEvent("ecos-network-down", { detail }));
  } catch {
    // SSR/runtime without window — ignore
  }
}

/**
 * Publish a `ecos-network-up` CustomEvent when a fetch succeeds after a
 * previously reported network-down, so the banner can self-clear.
 * Idempotent: only dispatches while a down state is tracked (best-effort
 * module-level flag, no global state store).
 */
let lastNetworkDownAt = 0;

export function markNetworkDown(at: number = Date.now()): void {
  lastNetworkDownAt = at;
}

export function notifyNetworkUp(): void {
  if (typeof window === "undefined") return;
  if (lastNetworkDownAt === 0) return;
  const elapsed = Date.now() - lastNetworkDownAt;
  // Only announce recovery if we recently went down (avoids noisy events
  // after a long quiet period where the banner has already self-dismissed).
  if (elapsed > 5 * 60_000) return;
  lastNetworkDownAt = 0;
  try {
    window.dispatchEvent(new CustomEvent("ecos-network-up", { detail: { timestamp: Date.now() } }));
  } catch {
    // SSR/runtime without window — ignore
  }
}

/** Decode network failures from raw fetch → throw instead of swallow silently. */
export function toNetworkError(e: unknown, operation = "fetch"): NetworkError | Error {
  if (e instanceof NetworkError) return e;
  if (e instanceof TypeError) {
    return new NetworkError(`Network error: ${operation} unreachable`);
  }
  if (e instanceof Error) return e;
  return new Error(e instanceof Error ? e.message : String(e ?? "Unknown error"));
}

/** Safely convert an AbortError into a user-visible "Request cancelled" error. */
export function toAbortError(e: unknown): Error {
  if (e instanceof DOMException && e.name === "AbortError") {
    return new Error("Request cancelled");
  }
  return toNetworkError(e, "fetch");
}

// ── Internal fetch helpers ────────────────────────────────────

/**
 * Login grace period — lets authenticated session avoid hard logout from
 * transient 401 during bootstrapped requests (e.g., Topbar fetches
 * /api/v1/security-profiles/user/{id} right after Login.setToken and
 * before topbar's useEffect flush clears); in that 10s window we only
 * throw without clearing the user's token, so a flaky 401 doesn't
 * silently kick the user back to /login.
 */
const AUTH_GRACE_KEY = "auth_refresh_grace_until";
const AUTH_GRACE_MS = 30_000;

/** 写入登录宽限期（login 成功后调用，让 UI 有 30s 缓冲消化 P3 端点的初次探测） */
export function setAuthGracePeriod() {
  try {
    localStorage.setItem(AUTH_GRACE_KEY, String(Date.now() + AUTH_GRACE_MS));
  } catch { /* non-critical */ }
}

/** 检查并消费宽限期 — 在宽限期内返回 true（**不清标记**，保证 10s 窗口内多次 401 都被吸收），过期返回 false */
export function consumeAuthGraceIfActive(): boolean {
  if (typeof window === "undefined" || !window.localStorage) return false;
  try {
    const raw = localStorage.getItem(AUTH_GRACE_KEY);
    if (!raw) return false;
    const until = Number(raw);
    if (Number.isFinite(until) && Date.now() < until) return true;
    // 宽限期已过期 — 清理
    if (Number.isFinite(until) && Date.now() >= until) localStorage.removeItem(AUTH_GRACE_KEY);
  } catch { /* non-critical */ }
  return false;
}

/** Global token expiration handler — clears auth and redirects to login */
export function handleAuthExpired(): never {
  if (consumeAuthGraceIfActive()) {
    // 宽限期内：只 throw，不清 token、不跳 #/login
    throw new Error(tStatic("common.session.refreshing"));
  }
  // token + 展示侧身份一律经 services/auth 单源清理（X-51 + 报告 §三 S5/S6）
  clearAuthSession();
  // Hash-based redirect avoids React Router race conditions
  window.location.hash = '#/login';
  throw new Error(tStatic("common.session.expired"));
}

/**
 * Check response status for auth failure.
 * 401 → token invalid: clear auth and redirect to login.
 * 403 → token VALID but insufficient permission: never clear the token.
 *      Notify the global UI (ecos-no-access) so the caller can surface the
 *      NoAccess page/message — see PMO-44 T5.
 */
export function checkAuthExpired(status: number): void {
  if (status === 401) handleAuthExpired();
  if (status === 403) notifyNoAccess();
}

/** 403 marker attached to thrown errors for 403 responses */
export const NO_ACCESS_ERROR_NAME = "NoAccessError";

/**
 * Thrown-like marker for 403 responses: token is valid but the resource is
 * forbidden. The error carries `status: 403` and `name: NoAccessError` so
 * UI layers can render a dedicated "no access" state (instead of logging
 * the user out or showing a generic HTTP error).
 */
export class NoAccessError extends Error {
  status = 403;
  /** 传输层追踪号（响应头优先，见 pickTraceId 单源口径）；无号时保持 undefined，不补假值 */
  traceId?: string;
  constructor(message = "You do not have permission to access this resource.", init?: { traceId?: string }) {
    super(message);
    this.name = NO_ACCESS_ERROR_NAME;
    this.traceId = init?.traceId;
  }
}

/** 403 notification event name */
export const NO_ACCESS_EVENT = "ecos-no-access";

/**
 * Publish a `ecos-no-access` CustomEvent so global UI layers
 * (e.g. the NoAccess page / a banner) can react to permission denials.
 * The token must NOT be cleared — the user is authenticated, just
 * unauthorized for this specific resource.
 */
export function notifyNoAccess(detail?: { path?: string; status?: number }): void {
  if (typeof window === "undefined") return;
  try {
    window.dispatchEvent(new CustomEvent(NO_ACCESS_EVENT, {
      detail: { ...detail, timestamp: Date.now() },
    }));
  } catch {
    // SSR/runtime without window — ignore
  }
}

/**
 * Detect whether an error thrown by apiFetch/apiFetchData/doFetch was a
 * 403 permission denial (token still valid).
 */
export function isNoAccessError(e: unknown): e is NoAccessError {
  return e instanceof NoAccessError;
}

// ── traceId 单源（#143/B5-6；差距台账 篇13-15 「httpClient 无 traceId」腿）──

/** 后端信封里可能带追踪号的形态（成功/失败均可能为 null，禁把 null 当 0 号） */
interface TraceBearingBody {
  traceId?: string | null;
}

/**
 * 追踪号提取唯一口径：响应头优先（X-Request-Id / X-Trace-Id / X-ECOS-Trace-Id），其次信封 traceId。
 * `headers` 用可选链读取：取号只是错误归因的辅助面，绝不能因为拿不到头就把传输层错误
 * 本身吞掉（改前此处裸取，遇到无 headers 的 Response 桩/异常响应对象会抛 TypeError，
 * 页面拿到的是「解析崩溃」而不是 500/404 + traceId）。
 */
export function pickTraceId(res: Response, body?: TraceBearingBody | null): string | undefined {
  const header =
    res.headers?.get("X-Request-Id") ??
    res.headers?.get("X-Trace-Id") ??
    res.headers?.get("X-ECOS-Trace-Id");
  if (header) return header;
  const envelope = body?.traceId;
  return typeof envelope === "string" && envelope !== "" ? envelope : undefined;
}

/**
 * 失败响应体尽力解析为信封：错误响应可能不是 JSON（网关 HTML/纯文本），
 * 解析不出时返回 null——「拿不到追踪号」与「后端没给追踪号」都不能被当成一个号。
 */
function parseEnvelope(text: string): TraceBearingBody | null {
  if (!text) return null;
  try {
    const parsed = JSON.parse(text) as unknown;
    return parsed && typeof parsed === "object" ? (parsed as TraceBearingBody) : null;
  } catch {
    return null;
  }
}

/**
 * 传输层业务/HTTP 错误统一形态：在 `message` 之外透出 `traceId` 与 `httpStatus`，
 * 使页面不必为拿追踪号各自拆信封（旧形态是 glossary/cognitiveClient 各写一份
 * `pickTraceId`，一处改口径另一处就悄悄漂移）。
 *
 * <p>兼容性红线：既有消费者读 `e.message` 与 `e.status`（`isErrorResponse` 按
 * `status` 数字分 http/unknown），故这里 `status` 与 `httpStatus` 同值双写，
 * 不改既有判别面。</p>
 */
export class ApiRequestError extends Error {
  readonly traceId?: string;
  readonly httpStatus?: number;
  /** 与 httpStatus 同值，供既有 `isErrorResponse` / `(e as {status}).status` 消费者读取 */
  readonly status?: number;

  constructor(message: string, init?: { traceId?: string; httpStatus?: number }) {
    super(message);
    this.name = "ApiRequestError";
    this.traceId = init?.traceId;
    this.httpStatus = init?.httpStatus;
    this.status = init?.httpStatus;
  }
}

/**
 * 页面侧取错误摘要（文案 + 追踪号）的单源口径。
 * 非 ApiRequestError/NoAccessError（如网络层 NetworkError、TypeError）时 traceId 为
 * undefined——界面不补假值，也不把「拿不到号」渲染成「没有号」。
 */
export function readApiError(e: unknown): { message: string; traceId?: string; status?: number } {
  const message = (() => {
    if (e instanceof Error) return e.message;
    // 非 Error 抛出物（拦截器/代理层转发的裸对象）常带 message 字段，直接 String() 只会
    // 得到 "[object Object]"，页面错误态等于没文案
    const bare = (e as { message?: unknown } | null)?.message;
    return typeof bare === "string" && bare !== "" ? bare : String(e);
  })();
  if (e instanceof ApiRequestError) {
    return { message, traceId: e.traceId, status: e.httpStatus };
  }
  if (e instanceof NoAccessError) {
    return { message, traceId: e.traceId, status: e.status };
  }
  const legacyStatus = (e as { status?: unknown } | null)?.status;
  return typeof legacyStatus === "number"
    ? { message, status: legacyStatus }
    : { message };
}

/** Encode query parameters for Tomcat UTF-8 compatibility */
export function encodeQueryString(path: string): string {
  const qIdx = path.indexOf('?');
  if (qIdx < 0) return path;
  const basePath = path.substring(0, qIdx);
  const qs = path.substring(qIdx + 1);
  const params = new URLSearchParams(qs);
  const encoded = new URLSearchParams();
  params.forEach((v, k) => {
    encoded.append(encodeURIComponent(k), encodeURIComponent(v));
  });
  return basePath + '?' + encoded.toString();
}

/**
 * Standard apiFetch – returns full JSON.
 * Prefixes path with /api and encodes query parameters for Tomcat UTF-8.
 */
export async function apiFetch<T>(path: string, options?: RequestInit): Promise<T> {
  const finalPath = encodeQueryString(path);
  const token = getAuthToken();
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  let res: Response;
  try {
    res = await fetch(`${API_BASE}${finalPath}`, { headers, ...options });
  } catch (e) {
    markNetworkDown();
    await notifyNetworkDown(e);
    throw toNetworkError(e, `fetch ${path}`);
  }
  if (res.status === 401) handleAuthExpired();
  if (res.status === 403) notifyNoAccess({ path });
  if (!res.ok) {
    if (res.status === 403) throw new NoAccessError(`API ${path} forbidden`, { traceId: pickTraceId(res) });
    throw new ApiRequestError(`API ${path} returned ${res.status}`, {
      httpStatus: res.status,
      traceId: pickTraceId(res),
    });
  }
  notifyNetworkUp();
  return res.json();
}

/**
 * Data-aware fetch – calls an arbitrary URL (no automatic /api prefix),
 * extracts `.data` field from response, handles both {success,data}
 * and {code,data} response formats.
 */
export async function apiFetchData<T>(url: string, options?: RequestInit): Promise<T> {
  const token = getAuthToken();
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  let res: Response;
  try {
    res = await fetch(url, { headers, ...options });
  } catch (e) {
    markNetworkDown();
    await notifyNetworkDown(e);
    throw toNetworkError(e, `fetch ${url}`);
  }
  if (res.status === 401) handleAuthExpired();
  if (res.status === 403) notifyNoAccess({ path: url });
  if (!res.ok) {
    const text = await res.text().catch(() => '');
    const traceId = pickTraceId(res, parseEnvelope(text));
    if (res.status === 403) throw new NoAccessError(text || `HTTP 403`, { traceId });
    throw new ApiRequestError(text || `HTTP ${res.status}`, { httpStatus: res.status, traceId });
  }
  const json = (await res.json()) as TraceBearingBody & { code?: number; message?: string; success?: boolean; data?: unknown };
  if (json.success === false) {
    throw new ApiRequestError(json.message || "Request failed", { traceId: pickTraceId(res, json) });
  }
  if (json.code && json.code !== 200 && json.code !== 0) {
    throw new ApiRequestError(json.message || `Error ${json.code}`, { traceId: pickTraceId(res, json) });
  }
  notifyNetworkUp();
  return (json.data !== undefined ? json.data : json) as T;
}

/**
 * Simple doFetch – returns full JSON body from arbitrary URL.
 * Includes Authorization Bearer token via `services/auth#getAuthToken()` when available.
 * Network failures are normalized to NetworkError + ecos-network-down event.
 */
export async function doFetch(url: string, opts: RequestInit = {}): Promise<any> {
  const token = getAuthToken();
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = `Bearer ${token}`;
  let r: Response;
  try {
    r = await fetch(url, { headers, ...opts });
  } catch (e) {
    markNetworkDown();
    await notifyNetworkDown(e);
    throw toNetworkError(e, `fetch ${url}`);
  }
  try {
    if (r.status === 401) handleAuthExpired();
    if (r.status === 403) notifyNoAccess({ path: url });
    if (!r.ok) {
      if (r.status === 403) throw new NoAccessError(`HTTP 403 on ${url}`, { traceId: pickTraceId(r) });
      throw new ApiRequestError(`${r.status}`, { httpStatus: r.status, traceId: pickTraceId(r) });
    }
    const ct = r.headers.get('content-type');
    const body = ct && ct.includes('application/json') ? await r.json() : null;
    notifyNetworkUp();
    return body;
  } catch (e) {
    // 401 redirects outside; business errors keep their message/pass-through.
    throw e;
  }
}

// ── Raw authed fetch (probe channel) ──────────────────────────

/**
 * rawAuthedFetch — 带 Bearer 的「裸请求」通道：返回原始 Response，
 * **不做任何 401/403 副作用**（不清 token、不跳登录、不发 ecos-no-access 事件）。
 *
 * <p>为什么需要第三条通道：`/api/v1/auth/me` 这类会话探测端点本身返回 401/403 就是
 * 业务答案（未登录 / 无权限），必须交由调用方（RequireAuth）做 fail-closed 判定；
 * 若走 apiFetch/apiFetchData，401 会先被 handleAuthExpired 消化成「清 token + 跳
 * #/login」，探测结果就被吞掉了（报告 §4.8 RequireAuth「else 分支视为 ok」同族问题）。
 * 网络层失败仍按 PMO-43 口径归一为 NetworkError 并广播 ecos-network-down。</p>
 *
 * @param path 以 `/` 开头的路径（自动拼 API_BASE，与 apiFetch 同基址单源）
 */
export async function rawAuthedFetch(path: string, options?: RequestInit): Promise<Response> {
  return rawAuthedUrlFetch(`${API_BASE}${path}`, options);
}

/**
 * `rawAuthedFetch` 的全 URL 内核 — 同一份实现，只是不再拼 API_BASE。
 *
 * <p>为什么需要 URL 形态：SSE/流式与二进制下载腿必须拿到**未被消费**的 `Response.body`
 * 交给 `getReader()`，而 `apiFetchData`/`apiFetchEnvelope` 都会先把响应体读成
 * text/JSON（流一旦被读掉就无法再解析帧序），KB 域这类腿的入参一直是调用方拼好的
 * 完整路径 ⇒ 不能再拼一次前缀。Bearer 拼接仍只在本单源内发生（C2′ 判据的收口目标：
 * 域文件里不再出现裸 `fetch(`）。</p>
 *
 * <p>与 `rawAuthedFetch` 一致的取舍：不设 `Content-Type` 默认值（GET 流式带 JSON
 * 类型无意义，且调用方传 `headers` 时是逐键覆盖而非整组替换）。</p>
 */
export async function rawAuthedUrlFetch(url: string, options?: RequestInit): Promise<Response> {
  const token = getAuthToken();
  const headers: Record<string, string> = {};
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const initHeaders = options?.headers;
  let res: Response;
  try {
    res = await fetch(url, {
      ...options,
      headers: initHeaders
        ? { ...headers, ...(initHeaders as Record<string, string>) }
        : headers,
    });
  } catch (e) {
    markNetworkDown();
    await notifyNetworkDown(e);
    throw toNetworkError(e, `fetch ${url}`);
  }
  if (res.ok) notifyNetworkUp();
  return res;
}

// ── Envelope outcome channel (C2 单解包点) ────────────────────

/** {@link apiFetchEnvelope} 的返回形状：解包与取号在单源完成，分类判定留给调用方。 */
export interface ApiEnvelopeOutcome {
  status: number;
  ok: boolean;
  /** 单源解包结果：整包是对象且带 `data` ⇒ 取 `data`，否则整包原样（标量 JSON 不塌成 null） */
  data: unknown;
  /** 解析后的整包响应体；空体或非 JSON ⇒ null */
  body: unknown;
  /** 响应原文：调用方兜底文案（`text || HTTP <status>`）要用的就是这一面，单源不代造文案 */
  text: string;
  /** `pickTraceId` 单源顺位结果（响应头三号面优先，其次信封 `traceId`） */
  traceId?: string;
  /**
   * 信封字段透出（缺字段即 undefined，不伪造默认值）。
   * 容差口径留给调用方：`apiFetchData` 把「`code` 非 200/0」一律折叠成抛错，而
   * 蓝图通道对 `code:201`（创建成功）必须放行、认知通道不容 ⇒ 折叠会摧毁分类。
   */
  envelope: {
    code?: number;
    success?: boolean;
    message?: string;
    errorCode?: string;
  };
}

/**
 * 第四条通道：一次取齐「解包结果 + 整包 + 原文 + traceId + 信封字段」，
 * **除网络层失败外不抛错**（401/403 的会话副作用照做，但 status 仍透出让调用方分类）。
 *
 * <p>存在理由（C2「ApiResponse 单解包点」与错误分类矩阵的交点）：cognitiveClient 的
 * 404/400/503→SCHEMA_NOT_READY/501→STUB 矩阵、scenarioBlueprintClient 的 409 迁移冲突族
 * （优先后端 `errorCode`，缺失才按 HTTP 归一）都需要读**原始 status ＋ 原始 errorCode ＋
 * 响应体原文**才能分类，而 `apiFetchData` 在解包前就抛错、且不透出这些面 ⇒ 两者此前各自
 * 保留一份私设 `await fetch` ＋ 手写 `data ??` 解包（＝C2 的最后两处违例）。本通道把解包
 * 与取号收回单源，分类规则原样留在域内。</p>
 */
export async function apiFetchEnvelope(url: string, opts: RequestInit = {}): Promise<ApiEnvelopeOutcome> {
  const token = getAuthToken();
  const defaults: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) defaults['Authorization'] = `Bearer ${token}`;
  // 逐调用方覆盖（而非整组替换）：两域客户端改前就是这个形状，
  // 若照 apiFetchData 的 `{headers, ...options}` 展开，带自定义头的调用会连 Bearer 一起丢掉。
  const headers: Record<string, string> = {
    ...defaults,
    ...((opts.headers as Record<string, string> | undefined) ?? {}),
  };
  let res: Response;
  try {
    res = await fetch(url, { ...opts, headers });
  } catch (e) {
    markNetworkDown();
    await notifyNetworkDown(e);
    throw toNetworkError(e, `fetch ${url}`);
  }
  if (res.status === 401) {
    // 会话副作用（清 token + 跳 #/login + 宽限期判定）照单源执行；其收尾 throw 在这里吞掉，
    // 因为两条域通道各自有 7 态/6 态错误矩阵，必须按 status=401 分类而非被折叠成网络态。
    try {
      handleAuthExpired();
    } catch {
      /* 已完成的清会话/跳转不回滚，改由返回值 status 透出 */
    }
  }
  if (res.status === 403) notifyNoAccess({ path: url });
  const text = await res.text().catch(() => '');
  const body = parseJsonBody(text);
  const obj = (body && typeof body === 'object' ? body : null) as Record<string, unknown> | null;
  const traceId = pickTraceId(res, obj as TraceBearingBody | null);
  if (res.ok) notifyNetworkUp();
  return {
    status: res.status,
    ok: res.ok,
    data: obj && obj.data !== undefined ? obj.data : body,
    body,
    text,
    traceId,
    envelope: {
      code: typeof obj?.code === 'number' ? obj.code : undefined,
      success: typeof obj?.success === 'boolean' ? obj.success : undefined,
      message: typeof obj?.message === 'string' ? obj.message : undefined,
      errorCode: typeof obj?.errorCode === 'string' ? obj.errorCode : undefined,
    },
  };
}

/**
 * 宽容解析：保留 JSON 标量（`5` / `"ok"`）原样返回，非 JSON 或空体才为 null。
 * 不复用 `parseEnvelope`——那只用于取号（对象面），塌成 null 会让裸标量载荷的调用方丢数据。
 */
function parseJsonBody(text: string): unknown {
  if (!text) return null;
  try {
    return JSON.parse(text) as unknown;
  } catch {
    return null;
  }
}

// ── Auth ────────────────────────────────────────────────
export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType?: string;
  username?: string;
  roles?: string[];
}

/** Login — POST /api/v1/auth/login (no auth header, user isn't logged in yet) */
export async function authLogin(body: LoginRequest): Promise<LoginResponse> {
  let res: Response;
  try {
    res = await fetch(`${API_BASE}/v1/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
  } catch (e) {
    // 传输层失败必须与「密码错误」区分（报告 §五 L3：登录 500/网络断只写「登录失败」
    // → 用户无从判断该重试还是该找管理员）。
    markNetworkDown();
    await notifyNetworkDown(e);
    throw toNetworkError(e, "login");
  }
  if (!res.ok) {
    if (res.status === 401 || res.status === 403) throw new Error(tStatic("login.fail.credentials"));
    // 5xx / 其它状态：给出可重试指引 + 状态码（fail-loud，不假成功）
    throw new Error(tStatic("login.fail.server", { status: res.status }));
  }
  const json = await res.json();
  const data = json.data || json;
  if (!data.accessToken) throw new Error(tStatic("login.invalidResponse"));
  return data;
}

/** GET /api/health — BFF/网关健康探测，失败回报网络不可用并返回 "DOWN" (自 App.tsx 迁入) */
export async function apiHealth(): Promise<string> {
  try {
    const r = await fetch("/api/health");
    const d = await r.json();
    return d.data?.status || d.status || "DOWN";
  } catch (err: unknown) {
    // G3: surface transport failures to the top NetworkErrorBanner.
    // HTTP status responses (e.g. 401 carrying a numeric `status`) are
    // auth/HTTP tokens, not network outages — only non-HTTP failures go up.
    const st = err && typeof err === "object" ? (err as { status?: unknown }).status : undefined;
    if (st === undefined) notifyNetworkDown(err);
    return "DOWN";
  }
}
