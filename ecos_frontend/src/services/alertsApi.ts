/**
 * 告警 REST 单源通道（#143/B5-11a C2′ 收口）
 *
 * <p>传输委托 {@link apiFetchEnvelope}（httpClient 单源）：Bearer 拼接、解包、traceId
 * 取号、401 会话副作用与网络/权限广播一律不在本文件重复实现；本文件只留域内分类。</p>
 *
 * <p>无假真理 P0：`ApiResponse.notFound/badRequest` 在 HTTP 层是 **200 包壳**（`code` 装在
 * 信封里），只看 `res.ok` 会把「后端自声明未承流」读成成功 ⇒ 本通道把信封 `code`/`success`
 * 折叠成 {@link AlertApiError}，404 面显式归 `ENDPOINT_NOT_WIRED` 而非空列表。</p>
 *
 * <p>承流现状（台账 D-B5-32，2026-10-19 源级取证）：`/api/alerts`（gateway `AlertController`）
 * 列表恒空壳、详情与 ack 自回 notFound；`rules` / `history` / `test` 三族后端零映射；
 * 真实落库告警面在 `/api/v1/monitor/alerts`（`RuntimeAlertV1Controller`，字段面与本面板
 * 现契约不同构）。整页归真属 R-52「不擅自下线」待产品定档项，本批只收传输与 fail-open。</p>
 *
 * @license Apache-2.0
 */

import { API_BASE, apiFetchEnvelope } from './httpClient';

const ALERTS = `${API_BASE}/alerts`;

export type AlertApiErrorCode =
  | 'ENDPOINT_NOT_WIRED'
  | 'BAD_REQUEST'
  | 'FORBIDDEN'
  | 'HTTP_ERROR'
  | 'BUSINESS_ERROR'
  | 'NETWORK';

export class AlertApiError extends Error {
  readonly code: AlertApiErrorCode;
  readonly status: number;
  readonly businessCode?: number;
  readonly traceId?: string;

  constructor(init: {
    code: AlertApiErrorCode;
    status: number;
    message: string;
    businessCode?: number;
    traceId?: string;
  }) {
    super(init.message);
    this.name = 'AlertApiError';
    this.code = init.code;
    this.status = init.status;
    this.businessCode = init.businessCode;
    this.traceId = init.traceId;
  }
}

/** 一次取齐解包与分类所需面；除网络层外不抛错，分类规则留在域内。 */
async function callAlert(url: string, init: RequestInit = {}): Promise<unknown> {
  let out: Awaited<ReturnType<typeof apiFetchEnvelope>>;
  try {
    out = await apiFetchEnvelope(url, init);
  } catch (e) {
    throw new AlertApiError({
      code: 'NETWORK',
      status: 0,
      message: e instanceof Error ? e.message : String(e),
    });
  }
  const message = out.envelope.message ?? (out.text || `HTTP ${out.status}`);
  if (out.status === 404) {
    throw new AlertApiError({ code: 'ENDPOINT_NOT_WIRED', status: 404, message, traceId: out.traceId });
  }
  if (out.status === 400) {
    throw new AlertApiError({ code: 'BAD_REQUEST', status: 400, message, traceId: out.traceId });
  }
  if (out.status === 403) {
    throw new AlertApiError({ code: 'FORBIDDEN', status: 403, message, traceId: out.traceId });
  }
  if (!out.ok) {
    throw new AlertApiError({ code: 'HTTP_ERROR', status: out.status, message, traceId: out.traceId });
  }
  const code = out.envelope.code;
  const folded = out.envelope.success === false || (typeof code === 'number' && code !== 0 && code !== 200);
  if (folded) {
    // `ApiResponse` 契约（common-api:35-43）= `0` 成功 / `400` 参数错 / `404` 不存在 / `-1` 系统错，
    // 且这些一律以 **HTTP 200** 下发。后端自声明的 404 语义是「该腿未承流」，必须与
    // 真 404 同态折叠，否则面板会把「后端明确说没有这个端点」渲染成一次普通业务失败。
    const mapped: AlertApiErrorCode =
      code === 404 ? 'ENDPOINT_NOT_WIRED'
        : code === 400 ? 'BAD_REQUEST'
          : code === 403 ? 'FORBIDDEN'
            : 'BUSINESS_ERROR';
    throw new AlertApiError({
      code: mapped,
      status: out.status,
      message,
      businessCode: typeof code === 'number' ? code : undefined,
      traceId: out.traceId,
    });
  }
  return out.data;
}

const JSON_HEADERS = { 'Content-Type': 'application/json' };

export function fetchAlertRules(): Promise<unknown> {
  return callAlert(`${ALERTS}/rules`);
}

export function fetchAlertHistory(limit = 20): Promise<unknown> {
  return callAlert(`${ALERTS}/history?limit=${encodeURIComponent(String(limit))}`);
}

export function alertTest(body: Record<string, unknown>): Promise<unknown> {
  return callAlert(`${ALERTS}/test`, {
    method: 'POST',
    headers: JSON_HEADERS,
    body: JSON.stringify(body),
  });
}

/**
 * 后端映射是 `POST /api/v1/alerts/{id}/ack`（原前端用 PUT ⇒ 405 且被忽略），
 * 且该端点自回 `notFound`（告警确认由 runtime-task 维持）⇒ 本腿必然抛
 * {@link AlertApiError}，不再静默成功。操作人不拼造：后端 ack 体只取 id 路径段。
 */
export function alertAck(id: string | number): Promise<unknown> {
  return callAlert(`${ALERTS}/${encodeURIComponent(String(id))}/ack`, { method: 'POST' });
}

export function alertCreateRule(rule: Record<string, unknown>): Promise<unknown> {
  return callAlert(`${ALERTS}/rules`, {
    method: 'POST',
    headers: JSON_HEADERS,
    body: JSON.stringify(rule),
  });
}

export function alertDeleteRule(id: string | number): Promise<unknown> {
  return callAlert(`${ALERTS}/rules/${encodeURIComponent(String(id))}`, { method: 'DELETE' });
}
