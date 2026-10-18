/**
 * KB-domain HTTP primitives (B.9 / F04-03) — 传输腿已合流 `services/httpClient.ts` 单源（#143/B5-11c）。
 *
 * 单源约束：
 *   - auth header 全走 httpClient 的 `apiFetchEnvelope` / `rawAuthedUrlFetch`
 *     （其内部只经 `src/services/auth.ts::getAuthToken` 取 token），
 *     本文件不再出现裸 `fetch(`（C2′ 判据）也不再手拼 `Authorization`。
 *   - 5xx 或响应体携带 `ECOS-KB-*` 业务码时抛 `KbApiError`，
 *     Tab 组件 catch 分支据 `err instanceof KbApiError` 切换 error 态 vs 空态。
 *     **分态矩阵本身留在域内**（这正是 `apiFetchEnvelope` 通道存在的原因：
 *     `apiFetchData` 在解包前就把非 2xx 折叠成 ApiRequestError，status/原文面丢失）。
 *   - 其余行为与 `apiFetchData` 保持对齐（`success=false` / `code` 非 0/200 时抛错，
 *     解包口径 `json.data !== undefined ? json.data : json`）。
 *
 * 说明：不切换现有 `apiFetchData` 调用面，仅在需要错误态分治的 3 个 Tab
 *       （graph / wiki / govern）走本 wrapper；旧调用保留 `apiFetchData` 兼容面。
 *
 * 本次合流带来的三条**行为差**（登记于差距分析台账 #143/B5-11c 批，非回归）：
 *   1) 网络层失败改前原样抛出 `TypeError`，改后按单源 PMO-43 口径归一为
 *      `NetworkError` 并广播 `ecos-network-down`——`isErrorResponse` 对两者都判 `kind:"network"`，
 *      各 Tab 的「非 KbApiError 一律置错误态」分支不受影响；
 *   2) 401 改前只做静默抛错，改后先走单源 `handleAuthExpired()`（宽限期判定 + 清会话 + 跳 `#/login`），
 *      然后仍按 `status=401` 落本域错误态（envelope 通道吞掉其 throw 以透出 status）；
 *   3) 403 改前无全局信号，改后单源广播 `ecos-no-access`（NoAccess 页监听，不改路由），
 *      本域照旧抛 `KbApiError(403, ECOS-KB-062)` 供 Tab 显示业务码。
 *   SSE 腿（`kbSseRequest`）改后不再带 `Content-Type: application/json`（GET 流式无意义），
 *   其余语义不变：仍返回**未消费**的原始 `Response`，不做 401/403 副作用、不解析 JSON。
 */
import { apiFetchEnvelope, rawAuthedUrlFetch } from '../../../services/httpClient';

/** KB 域业务错误（status=4xx/5xx，code=ECOS-KB-xxx 时携带业务码） */
export class KbApiError extends Error {
  constructor(
    public status: number,
    public code?: string,
    message?: string,
    /** 单源 `pickTraceId` 顺位结果（响应头三号面优先，其次信封 `traceId`）；无号保持 undefined */
    public traceId?: string,
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
 *   - kb-engine 的 `errorCode` 面（实测 `405 ECOS-KB-070` / `503 ECOS-KB-073` 落该字段）
 *     靠此处慢路径的正则扫描兜住——**不要**改成只读 `code` 字段，那会让上面两枚码退化成裸 4xx。
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

/**
 * 与 apiFetchData 兼容的 data 提取语义（`json.data !== undefined ? json.data : json`），
 * 但把 5xx 与 `ECOS-KB-*` 业务码升级成 `KbApiError`。
 *
 * 401/403 的会话与权限副作用由单源执行（见文件头行为差 2/3），本函数只负责
 * KB 域自己的分态：`status >= 500 || 业务码存在` → `KbApiError`，其余 4xx → 带 `status` 的 `Error`。
 */
export async function kbFetchData<T>(url: string, options?: RequestInit): Promise<T> {
  const out = await apiFetchEnvelope(url, options);
  if (!out.ok) {
    const code = extractKbCode(out.text);
    // 5xx or business-code carry → KbApiError（Tab 判定 error 态）
    if (out.status >= 500 || code) {
      throw new KbApiError(out.status, code, out.text || `HTTP ${out.status}`, out.traceId);
    }
    // 4xx 非业务码：兼容历史语义（抛出通用 Error 携带 status）
    const err = new Error(out.text || `HTTP ${out.status}`) as Error & { status?: number };
    err.status = out.status;
    throw err;
  }
  if (out.body === null) {
    // 改前此处由 `res.json()` 抛 SyntaxError；单源把「非 JSON / 空体」塌成 null，
    // 若直接返回 null 就是把「网关 HTML 应答」读成「后端说没数据」的假真理 ⇒ 保持 fail-loud。
    throw new Error(`HTTP ${out.status} 响应体非 JSON`);
  }
  const json = out.body as Record<string, unknown> & { success?: boolean; code?: unknown; message?: string };
  if (json.success === false) {
    const msg = String(json.message ?? 'Request failed');
    const code = extractKbCode(msg);
    if (code) throw new KbApiError(500, code, msg, out.traceId);
    throw new Error(msg);
  }
  if (json.code && json.code !== 200 && json.code !== 0) {
    const codeStr = String(json.code);
    const msg = String(json.message ?? `Error ${codeStr}`);
    if (/^ECOS-KB-\d+$/.test(codeStr)) throw new KbApiError(500, codeStr, msg, out.traceId);
    throw new Error(msg);
  }
  return out.data as T;
}

/**
 * SSE-capable raw fetch — 走单源 `rawAuthedUrlFetch`（Bearer 在 httpClient 内拼）。
 * knowledge wiki retrieval / RAG 调试专用；页面禁止手写 `Authorization` 拼装（K-55 纠正）。
 *
 * 返回**未消费的原始 Response**：帧序解析归 `services/sseParser` 单源（D-B5-11），
 * 所以这里绝不能走 `apiFetchEnvelope`（它会把 body 读成 text，流一旦读掉就无法再取 reader）。
 */
export function kbSseRequest(url: string, options: RequestInit = {}): Promise<Response> {
  return rawAuthedUrlFetch(url, options);
}
