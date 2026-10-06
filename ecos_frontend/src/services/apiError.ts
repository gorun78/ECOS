/**
 * Typed ApiError — E4.1 前端 API 层统一错误契约 (P0 顶层共因)。
 *
 * 报告 §2.5 E4.1 明令 "typed ApiError 统一" (V1 184 god-file 拆域, 下同)。
 *
 * 语义:
 *  - `network`  : fetch 抛网络异常 / 超时 / AbortSignal 主动中止
 *  - `http`     : 后端返非 2xx, status + body 已读出
 *  - `auth`     : 401/403 (补充 path: httpClient 已承担 handleAuthExpired, 此处仅结构化)
 *  - `parse`    : body JSON/shape 解析失败
 *  - `aborted`  : caller 主动 signal.abort
 *
 * 使用:
 *  ```ts
 *  try {
 *    await apiFetchData(...);
 *  } catch (e) {
 *    if (ApiError.isNetwork(e)) ...
 *    if (ApiError.isHttp(e) && e.status === 404) ...
 *  }
 *  ```
 *
 * 兼容: 本类型**新家**、不改既有 httpClient 抛出行为 —— 现有 catch(e) 仍能按 e 处理,
 * 新代码用 ApiError.isXxx / wrapAny() 判定。
 */
export type ApiErrorKind =
  | "network"
  | "http"
  | "auth"
  | "parse"
  | "aborted"
  | "unknown";

export interface ApiErrorBody {
  code?: number | string;
  message?: string;
  error?: string;
  data?: unknown;
}

export class ApiError extends Error {
  readonly kind: ApiErrorKind;
  readonly status?: number;
  readonly url?: string;
  readonly method?: string;
  readonly body?: ApiErrorBody;
  readonly cause?: unknown;

  constructor(init: {
    kind: ApiErrorKind;
    message: string;
    status?: number;
    url?: string;
    method?: string;
    body?: ApiErrorBody;
    cause?: unknown;
  }) {
    super(init.message);
    this.name = "ApiError";
    this.kind = init.kind;
    this.status = init.status;
    this.url = init.url;
    this.method = init.method;
    this.body = init.body;
    this.cause = init.cause;
  }

  static isNetwork(e: unknown): boolean {
    return e instanceof ApiError && e.kind === "network";
  }
  static isHttp(e: unknown): e is ApiError & { status: number } {
    return e instanceof ApiError && (e.kind === "http" || e.kind === "auth");
  }
  static isAuth(e: unknown): e is ApiError & { status: 401 | 403 } {
    return e instanceof ApiError && e.kind === "auth";
  }
  static isParse(e: unknown): boolean {
    return e instanceof ApiError && e.kind === "parse";
  }
  static isAborted(e: unknown): boolean {
    return e instanceof ApiError && e.kind === "aborted";
  }

  // 401 / 403 → auth, 5xx → unknown (工单化), 其余非 2xx → http
  static wrapAny(raw: unknown, ctx?: { url?: string; method?: string }): ApiError {
    if (raw instanceof ApiError) return raw;
    if (raw instanceof DOMException && raw.name === "AbortError") {
      return new ApiError({ kind: "aborted", message: "request aborted", url: ctx?.url, method: ctx?.method, cause: raw });
    }
    if (raw instanceof TypeError && /fetch|network/i.test(raw.message)) {
      return new ApiError({ kind: "network", message: raw.message, url: ctx?.url, method: ctx?.method, cause: raw });
    }
    if (raw instanceof ApiError) return raw;
    if (typeof raw === "object" && raw !== null) {
      const r = raw as { status?: number; statusText?: string; body?: ApiErrorBody; message?: string };
      if (typeof r.status === "number") {
        const status = r.status;
        const kind: ApiErrorKind = status === 401 || status === 403 ? "auth" : "http";
        return new ApiError({
          kind,
          message: r.statusText || r.body?.message || `HTTP ${status}`,
          status,
          url: ctx?.url,
          method: ctx?.method,
          body: r.body,
          cause: raw,
        });
      }
    }
    return new ApiError({
      kind: "unknown",
      message: raw instanceof Error ? raw.message : String(raw),
      url: ctx?.url,
      method: ctx?.method,
      cause: raw,
    });
  }
}
