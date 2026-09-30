/**
 * HTTP 传输原语单源层 (PMO-74.6 H6-T2) — 自 src/api.ts 逐字迁入，行为不变。
 * src/api.ts 通过 re-export 保持既有 `from "./api"` 导入面。
 */

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
  if (e instanceof TypeError) return { message: "网络错误：服务不可用", kind: "network" };
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
    throw new Error("Session refresh in progress. Please try again.");
  }
  localStorage.removeItem('token');
  localStorage.removeItem('username');
  localStorage.removeItem('roles');
  // Hash-based redirect avoids React Router race conditions
  window.location.hash = '#/login';
  throw new Error('Session expired. Please sign in again.');
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
  constructor(message = "You do not have permission to access this resource.") {
    super(message);
    this.name = NO_ACCESS_ERROR_NAME;
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
  const token = localStorage.getItem('token') || '';
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
    if (res.status === 403) throw new NoAccessError(`API ${path} forbidden`);
    throw new Error(`API ${path} returned ${res.status}`);
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
  const token = localStorage.getItem('token') || '';
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
    if (res.status === 403) throw new NoAccessError(text || `HTTP 403`);
    throw new Error(text || `HTTP ${res.status}`);
  }
  const json = await res.json();
  if (json.success === false) throw new Error(json.message || "Request failed");
  if (json.code && json.code !== 200 && json.code !== 0) throw new Error(json.message || `Error ${json.code}`);
  notifyNetworkUp();
  return json.data !== undefined ? json.data : json;
}

/**
 * Simple doFetch – returns full JSON body from arbitrary URL.
 * Includes Authorization Bearer token from localStorage when available.
 * Network failures are normalized to NetworkError + ecos-network-down event.
 */
export async function doFetch(url: string, opts: RequestInit = {}): Promise<any> {
  const token = localStorage.getItem('token') || '';
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
      if (r.status === 403) throw new NoAccessError(`HTTP 403 on ${url}`);
      throw new Error(`${r.status}`);
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
  const res = await fetch("/api/v1/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    if (res.status === 401 || res.status === 403) throw new Error("用户名或密码错误");
    throw new Error(`登录失败 (${res.status})`);
  }
  const json = await res.json();
  const data = json.data || json;
  if (!data.accessToken) throw new Error("登录响应无效，缺少 accessToken");
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
