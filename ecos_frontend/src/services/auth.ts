/**
 * 鉴权与登录会话生命周期单源 (PMO-74.6 H6-T1 / 检视报告 S5/S6 批次1 收口)。
 *
 * <p>单源纪律：token 的「读 / 清 / 探测」全部集中在本文件 + services/httpClient，
 * 页面/壳层组件不得再各自手删 localStorage key（历史上 Login 写 `token`、
 * Topbar 清 `ecos_token`、NoAccess 又清第三套 → 登出点了没退出，见报告 §三 S5/S6）。</p>
 *
 * <p>token 读取 union 兼容历史三套 key: localStorage 'token' | 'accessToken' | 'jwt'
 * 与 sessionStorage 'jwt'。</p>
 */
import { rawAuthedFetch } from "./httpClient";

export function getAuthToken(): string {
  try {
    const ls = typeof localStorage !== "undefined" ? localStorage : null;
    const ss = typeof sessionStorage !== "undefined" ? sessionStorage : null;
    return (
      ls?.getItem("token") ||
      ls?.getItem("accessToken") ||
      ss?.getItem("jwt") ||
      ls?.getItem("jwt") ||
      ""
    );
  } catch {
    return "";
  }
}

/** Content-Type: application/json + Bearer（缺失 token 时仅 Content-Type）。 */
export function authHeaders(): Record<string, string> {
  const token = getAuthToken();
  return {
    "Content-Type": "application/json",
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

/** 仅 Authorization Bearer（调用方自行控制 Content-Type / 其余 header）。 */
export function authOnlyHeaders(): Record<string, string> {
  const token = getAuthToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

/**
 * 登出/会话过期单源清理（X-51 收口新增，只增不改既有签名）：
 * 清除 getAuthToken 联合读取的全部三 key（localStorage token/accessToken/jwt + sessionStorage jwt）。
 */
export function clearAuthToken(): void {
  try {
    localStorage.removeItem("token");
    localStorage.removeItem("accessToken");
    localStorage.removeItem("jwt");
    sessionStorage.removeItem("jwt");
  } catch {
    /* storage 不可用（SSR/隐私模式）忽略 */
  }
}

/**
 * 会话全量清理 = token 三 key + 展示侧身份（username/roles）。
 *
 * <p>登出只有这一个入口（报告 §三 S5/S6：历史上 Login 写 `token`、Topbar 清
 * `ecos_token`、NoAccess 又清第三套 key → 点了退出但会话没退）。
 * 注意不清 `ecos_locale` / `ecos_theme`（用户偏好，与登录态无关）。</p>
 */
export function clearAuthSession(): void {
  clearAuthToken();
  try {
    localStorage.removeItem("username");
    localStorage.removeItem("roles");
  } catch {
    /* storage 不可用忽略 */
  }
}

/**
 * 主动登出单源（Topbar / NoAccess / 任何「退出登录」动作都走这里）：
 * 清会话 → 用 hash 跳转避免 React Router 与 401 处理器竞态（同 httpClient#handleAuthExpired）。
 */
export function performLogout(): void {
  clearAuthSession();
  if (typeof window !== "undefined") {
    window.location.hash = "#/login";
  }
}

/** 当前登录用户身份（展示侧）。无有效身份时返回 null —— 调用方必须显式降级，不得造访客名（铁律：无假真理）。 */
export interface AuthedUserIdentity {
  username: string;
  roles: string[];
}

export function getUserIdentity(): AuthedUserIdentity | null {
  try {
    const username = (localStorage.getItem("username") || "").trim();
    if (!username) return null;
    const rawRoles = localStorage.getItem("roles");
    let roles: string[] = [];
    if (rawRoles) {
      try {
        const parsed: unknown = JSON.parse(rawRoles);
        if (Array.isArray(parsed)) roles = parsed.map((r) => String(r));
      } catch {
        /* 非 JSON 的旧值按单角色处理，不丢弃 */
        roles = [rawRoles];
      }
    }
    return { username, roles };
  } catch {
    return null;
  }
}

/** 头像首字母：取用户名首字符（大写）；无身份时返回 "" 由调用方渲染中性占位。 */
export function getUserInitials(): string {
  const identity = getUserIdentity();
  if (!identity) return "";
  const first = identity.username.split(/[\s_.@-]+/).filter(Boolean)[0] ?? "";
  return first.slice(0, 2).toUpperCase();
}

/** RequireAuth 会话探测: GET /api/v1/auth/me — 返回状态码与原始 JSON，不做跳转副作用。 */
export async function authMeProbe(
  token: string,
  signal?: AbortSignal,
): Promise<{ ok: boolean; status: number; json: unknown } | null> {
  const res = await rawAuthedFetch("/v1/auth/me", { signal, ...(token ? { headers: { Authorization: `Bearer ${token}` } } : {}) });
  let json: unknown = null;
  try {
    json = await res.json();
  } catch {
    /* 非 JSON 响应按 null 处理 */
  }
  return { ok: res.ok, status: res.status, json };
}

/** Login 会话引导: 用登录响应里的新 accessToken 探测当前用户并返回原始 JSON。 */
export async function fetchAuthMeWithToken(token: string): Promise<unknown> {
  const res = await rawAuthedFetch("/v1/auth/me", { headers: { Authorization: `Bearer ${token}` } });
  return res.json();
}
