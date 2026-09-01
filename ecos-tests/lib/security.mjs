// lib/security.mjs — 越权/安全回归测试共享工具库
// 用法:
//   import { login, expect, BASE, ADMIN, report } from '../lib/security.mjs';
//
// 约定:
//   - BASE 默认 http://localhost:8080 （可被 ECOS_BASE 覆盖）
//   - 默认管理员 admin (memory 中 admin / ChangshaCSW@2025)
//   - 所有用例 exit 0 = PASS, 1 = FAIL
//   - login 失败时抛 GATEWAY_DOWN 异常, 调用方应捕获 ENV_BLOCKED

export const BASE =
  process.env.ECOS_BASE || "http://localhost:8080";

export const ADMIN = {
  username: process.env.ECOS_ADMIN_USER || "admin",
  password: process.env.ECOS_ADMIN_PASSWORD || "ChangshaCSW@2025",
};

// ── 60/66 接口（PRD 超前，实际不存在时使用）────────────────
export function callAuth(path, opts = {}) {
  return fetchWithBase(path, { ...opts, headers: { ...headers(opts), ...(opts.body?.token ? { Authorization: `Bearer ${opts.body.token}` } : {}) } });
}

function fetchWithBase(path, opts = {}) {
  const url = path.startsWith("http") ? path : `${BASE}${path}`;
  return fetch(url, { ...opts, signal: AbortSignal.timeout(opts.timeoutMs || 15000) });
}

function headers(opts) {
  return opts.headers || {};
}

// ── 登录 ──────────────────────────────────────────────────────
// 返回 { token, userId, roles } 或抛 GATEWAY_DOWN
export async function login(username = ADMIN.username, password = ADMIN.password) {
  const r = await fetchWithBase("/api/v1/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
  });
  if (r.status === 401) throw new Error(`LOGIN_FAILED: 401 (username=${username})`);
  if (!r.ok) {
    const txt = await r.text().catch(() => "");
    throw new Error(`LOGIN_HTTP_${r.status}: ${txt.slice(0, 200)}`);
  }
  const j = await r.json();
  const data = j.data || j;
  // Admin 登录若返回 PASSWORD_CHANGE_REQUIRED/PASSWORD_EXPIRED → changeToken
  if (data.code === "PASSWORD_CHANGE_REQUIRED" || data.code === "PASSWORD_EXPIRED") {
    return { token: data.data?.changeToken || data.changeToken, changeRequired: true };
  }
  const token = data?.accessToken || data?.token || data?.access_token;
  if (!token) throw new Error(`LOGIN_NO_TOKEN: ${JSON.stringify(data).slice(0, 200)}`);
  return {
    token,
    userId: data?.userId,
    username: data?.username,
    roles: data?.roles || [],
  };
}

// ── 通用 fetch 包装 ──────────────────────────────────────────
export async function req(method, path, { token, body, headers: h, timeout } = {}) {
  const headers = { ...(h || {}) };
  if (body !== undefined) headers["Content-Type"] = headers["Content-Type"] || "application/json";
  if (token) headers["Authorization"] = `Bearer ${token}`;
  const res = await fetchWithBase(path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    timeoutMs: timeout,
  });
  let data;
  const ct = res.headers.get("content-type") || "";
  if (ct.includes("application/json")) {
    data = await res.json().catch(() => ({}));
  } else {
    const txt = await res.text().catch(() => "");
    data = { _text: txt.slice(0, 500) };
  }
  return { status: res.status, body: data, headers: res.headers };
}

/**
 * 断言: assert(cond, msg) 失败时抛 AssertionError
 */
export function assert(cond, msg) {
  if (!cond) throw new AssertionError(msg);
}

export class AssertionError extends Error {
  constructor(msg) {
    super(msg);
    this.name = "AssertionError";
  }
}

/**
 * 断言 endpoint 返回 401/403（未授权/越权）
 * 铁律: 默认 DENY, 没有合法 token 必须拒绝 (401)
 */
export function expectForbid(st, msg) {
  if (st !== 401 && st !== 403) {
    throw new AssertionError(`expect 401/403 got ${st}: ${msg}`);
  }
}

/**
 * 断言 endpoint 返回 2xx（合法访问）
 */
export function expectAllow(st, msg) {
  if (st < 200 || st >= 300) {
    throw new AssertionError(`expect 2xx got ${st}: ${msg}`);
  }
}

/**
 * 断言 endpoint 不存在（PRD 超前 → 应该 404 不允许 2xx/3xx/5xx）
 */
export function expect404(st, msg) {
  if (st !== 404) {
    throw new AssertionError(`expect 404 got ${st}: ${msg}`);
  }
}

/**
 * 断言明确状态码
 */
export function expectStatus(expected, st, msg) {
  if (st !== expected) {
    throw new AssertionError(`expect ${expected} got ${st}: ${msg}`);
  }
}

/**
 * 断言 2xx 返回 JSON 含特定字段
 */
export function expectJsonField(obj, field, msg) {
  const v = obj?.[field];
  if (v === undefined || v === null) {
    throw new AssertionError(`missing field '${field}': ${msg}; got ${JSON.stringify(obj).slice(0, 200)}`);
  }
}

/**
 * 断言响应体含某字段或字段值不存在
 */
export function expectFieldAbsent(obj, field, msg) {
  if (obj?.[field] !== undefined) {
    throw new AssertionError(`field '${field}' should be absent: ${msg}; got ${JSON.stringify(obj?.[field]).slice(0, 100)}`);
  }
}

/**
 * 顶层报告: 测试通过/失败 退出
 */
export function report(label) {
  console.log(`[PASS] ${label}`);
  process.exit(0);
}

export function reportFail(label, err) {
  console.error(`[FAIL] ${label}: ${err.message}`);
  process.exit(1);
}

/**
 * 检查 Gateway 探活
 */
export async function gatewayAlive() {
  try {
    const r = await fetch(`${BASE}/api/health`, { signal: AbortSignal.timeout(3000) });
    return r.status === 200;
  } catch {
    return false;
  }
}

// 便捷预导入: test-entry 用法
export function guard(label, fn) {
  gatewayAlive().then((up) => {
    if (!up) {
      reportFail(label, new Error(`ENV_BLOCKED: Gateway ${BASE} 不可达`));
      return;
    }
    Promise.resolve()
      .then(fn)
      .then(() => report(label))
      .catch((e) => reportFail(label, e));
  });
}
