/**
 * 鉴权头单源定义 (PMO-74.6 H6-T1) — 全仓唯一 authHeaders 定义处。
 * token 读取 union 兼容历史三套 key: localStorage 'token' | 'accessToken' | 'jwt' 与 sessionStorage 'jwt'。
 */
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

/** RequireAuth 会话探测: GET /api/v1/auth/me — 返回状态码与原始 JSON，不做跳转副作用。 */
export async function authMeProbe(
  token: string,
  signal?: AbortSignal,
): Promise<{ ok: boolean; status: number; json: unknown } | null> {
  const res = await fetch("/api/v1/auth/me", {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    signal,
  });
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
  const res = await fetch("/api/v1/auth/me", {
    headers: { Authorization: `Bearer ${token}` },
  });
  return res.json();
}
