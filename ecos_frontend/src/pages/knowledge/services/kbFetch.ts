/**
 * KB-domain HTTP primitives (B.9 / F04-03).
 *
 * 单源约束：
 *   - auth header 全走 `src/services/auth.ts`（authOnlyHeaders / getAuthToken），
 *     不再直接 `localStorage.getItem('token')`。
 *   - 5xx 或响应体携带 `ECOS-KB-*` 业务码时抛 `KbApiError`，
 *     Tab 组件 catch 分支据 `err instanceof KbApiError` 切换 error 态 vs 空态。
 *   - 其余行为与 `src/services/httpClient.ts::apiFetchData` 保持对齐
 *     （success=false / code != 200 时抛 Error）。
 *
 * 说明：不切换现有 `apiFetchData` 调用面，仅在需要错误态分治的 3 个 Tab
 *       （graph / wiki / govern）走本 wrapper；旧调用保留 `apiFetchData` 兼容面。
 */
import { getAuthToken } from '../../../services/auth';

/** KB 域业务错误（status=4xx/5xx，code=ECOS-KB-xxx 时携带业务码） */
export class KbApiError extends Error {
  constructor(
    public status: number,
    public code?: string,
    message?: string,
  ) {
    super(message ?? (code ? `KB error ${code} (HTTP ${status})` : `HTTP ${status}`));
    this.name = 'KbApiError';
  }
}

/**
 * 从响应 text / JSON 中抽取 ECOS-KB-* 业务码（存在即视为可映射为 KbApiError）。
 * 兼容两种 transport：
 *   - HTTP 层 5xx + 响应体 `{code:"ECOS-KB-xxx", message:"..."}`
 *   - HTTP 层 4xx + 响应体同上（如 403 ECOS-KB-062 / 409 ECOS-KB-071 / 410 ECOS-KB-072）
 */
function extractKbCode(text: string, fallback?: string): string | undefined {
  if (fallback && /^ECOS-KB-\d+/.test(fallback)) return fallback;
  if (!text) return undefined;
  // Fast path: structured JSON
  try {
    const parsed = JSON.parse(text) as { code?: unknown; error?: unknown };
    if (typeof parsed?.code === 'string' && /^ECOS-KB-\d+/.test(parsed.code)) return parsed.code;
    if (typeof parsed?.error === 'string' && /^ECOS-KB-\d+/.test(parsed.error)) return parsed.error;
  } catch {
    // fall through to regex scan
  }
  // Slow path: raw text may carry code in an inner contract field
  const m = text.match(/ECOS-KB-\d+/);
  return m ? m[0] : undefined;
}

function buildHeaders(extra?: Record<string, string>): Record<string, string> {
  const token = getAuthToken();
  const h: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) h.Authorization = `Bearer ${token}`;
  if (extra) Object.assign(h, extra);
  return h;
}

/**
 * 与 apiFetchData 兼容的 data 提取语义（`json.data !== undefined ? json.data : json`），
 * 但把 5xx 与 `ECOS-KB-*` 业务码升级成 `KbApiError`。
 *
 * 401 走原生语义（由调用方拦截 / 静默）；403 走原生 `NoAccessError` 兼容层
 * —— 简化合规：这里统一返回 `KbApiError` 便于 Tab catch 分支识别。
 */
export async function kbFetchData<T>(url: string, options?: RequestInit): Promise<T> {
  const headers = buildHeaders(options?.headers as Record<string, string> | undefined);
  let res: Response;
  try {
    res = await fetch(url, { ...options, headers });
  } catch (e) {
    throw e; // 网络/TypeError 交由上层（如 wrapNet / isErrorResponse）接管
  }
  if (res.status >= 400) {
    const text = await res.text().catch(() => '');
    const code = extractKbCode(text);
    // 5xx or business-code carry → KbApiError（Tab 判定 error 态）
    if (res.status >= 500 || code) {
      throw new KbApiError(res.status, code, text || `HTTP ${res.status}`);
    }
    // 4xx 非业务码：兼容历史语义（抛出通用 Error 携带 status）
    const err = new Error(text || `HTTP ${res.status}`) as Error & { status?: number };
    err.status = res.status;
    throw err;
  }
  const json = (await res.json()) as Record<string, unknown> & { data?: T };
  if (json.success === false) {
    const msg = String(json.message ?? 'Request failed');
    const code = extractKbCode(msg);
    if (code) throw new KbApiError(500, code, msg);
    throw new Error(msg);
  }
  if (json.code && json.code !== 200 && json.code !== 0) {
    const codeStr = String(json.code);
    const msg = String(json.message ?? `Error ${codeStr}`);
    if (/^ECOS-KB-\d+$/.test(codeStr)) throw new KbApiError(500, codeStr, msg);
    throw new Error(msg);
  }
  return (json.data !== undefined ? json.data : json) as T;
}

/**
 * SSE-capable raw fetch，auth header 也走 auth.ts 单源。
 * knowledge wiki retrieval / RAG 调试专用；页面禁止手写 `Authorization` 拼装（K-55 纠正）。
 */
export function kbSseRequest(url: string, options: RequestInit = {}): Promise<Response> {
  const headers = buildHeaders({ ...(options.headers as Record<string, string> | undefined) });
  // SSE 用 fetch + reader；不解析 JSON，保留 stream body。
  return fetch(url, { ...options, headers });
}
