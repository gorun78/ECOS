/**
 * Platform governance API — gateway diagnostics + alert center (PMO §B).
 * Transports: apiFetch (path-prefix /api, unwraps nothing but status) and
 * authHeaders from ./auth. BACKEND DAY 端点契约见
 * docs/30-设计/详细设计-00-平台入口与横切底座-2026-09-28.md §D.5.3。
 *
 * X-Request-Id 不需前端生成（BFF 会做）；错误时展示响应体 traceId。
 */
import { apiFetch } from "../api";
import { authHeaders } from "./auth";

// ── Gateway diagnostics (GET /api/v1/system/gateway-diagnostics) ─────

export type GatewayTab = "filters" | "routes" | "anonymous" | "editions";

export interface DiagnosticsArtifact {
  name: string;
  port: number;
  mode?: string;
  carried: boolean;
}

export interface DiagnosticsFilter {
  name: string;
  order: number;
  expectedOrder: number;
  consistent: boolean;
}

export interface DiagnosticsRoute {
  prefix: string;
  owner: string;
  mode: string;
  carried: boolean;
}

export interface DiagnosticsAnonymous {
  pattern: string;
  dualPath: boolean;
  justification: string;
  approver: string;
}

export interface GatewayDiagnostics {
  artifacts: DiagnosticsArtifact[];
  filterChain: DiagnosticsFilter[];
  routeManifest: DiagnosticsRoute[];
  anonymousEndpoints: DiagnosticsAnonymous[];
  /** Might be object or array depending on backend; render defensively. */
  editionMatrix: Record<string, unknown> | unknown[];
  traceId?: string;
}

/**
 * Fetch the gateway diagnostics snapshot for a given tab.
 * The backend returns the full body so the frontend can render the
 * selected block while caching the rest for instant tab switching.
 */
export async function fetchGatewayDiagnostics(tab?: GatewayTab): Promise<GatewayDiagnostics> {
  const qs = tab ? `?tab=${tab}` : "";
  const json = await apiFetch<{ data?: GatewayDiagnostics; traceId?: string } & GatewayDiagnostics>(
    `/v1/system/gateway-diagnostics${qs}`
  );
  // ApiResponse envelope: { code, errorCode, message, data, timestamp, traceId }
  if (json && typeof json === "object" && "data" in json && json.data) {
    return json.data as GatewayDiagnostics;
  }
  return json as unknown as GatewayDiagnostics;
}

/** Best-effort: extract traceId from a thrown error (ApiResponse body). */
export function errorTraceId(e: unknown): string | undefined {
  const rec = (e ?? {}) as { traceId?: string; body?: { traceId?: string }; message?: string };
  if (rec.traceId) return rec.traceId;
  if (rec.body && typeof rec.body === "object" && rec.body.traceId) return rec.body.traceId;
  if (rec.message) {
    const m = String(rec.message).match(/traceId[:"\s]+([0-9a-fA-F-]{8,})/i);
    if (m) return m[1];
  }
  return undefined;
}

// ── Alerts (GET /api/v1/monitor/alerts/*) ────────────────────────────

export type AlertSeverity = "info" | "warn" | "error" | "critical";
export type AlertStatus = "open" | "acked" | "closed";

export interface AlertRecord {
  id: string;
  ruleCode?: string;
  severity: AlertSeverity;
  status: AlertStatus;
  title: string;
  sourceModule?: string;
  detail?: unknown;
  occurredAt?: string;
  traceId?: string;
  ackBy?: string;
  ackTime?: string;
  closeTime?: string;
  upgradeSlaMin?: number;
}

export interface AlertPage {
  items: AlertRecord[];
  page: number;
  size: number;
  total: number;
}

interface ListAlertsParams {
  status?: AlertStatus;
  severity?: AlertSeverity;
  page?: number;
  size?: number;
}

export async function listAlerts(params: ListAlertsParams): Promise<AlertPage> {
  const sp = new URLSearchParams();
  if (params.status) sp.set("status", params.status);
  if (params.severity) sp.set("severity", params.severity);
  sp.set("page", String(params.page ?? 1));
  sp.set("size", String(params.size ?? 20));
  const json = await apiFetch<{ data?: AlertPage } & AlertPage>(
    `/v1/monitor/alerts?${sp.toString()}`
  );
  if (json && typeof json === "object" && "data" in json && json.data) {
    const d = json.data as AlertPage;
    // 后端某些版本把 list 放在 `rows` / `records` 字段 — 做一次性归一
    if (!Array.isArray(d.items)) {
      const anyD = d as unknown as Record<string, unknown>;
      const r = anyD.items ?? anyD.rows ?? anyD.records ?? anyD.list;
      if (Array.isArray(r)) d.items = r as AlertRecord[];
    }
    if (d.items === undefined) d.items = [];
    return {
      items: d.items,
      page: d.page ?? params.page ?? 1,
      size: d.size ?? params.size ?? 20,
      total: d.total ?? d.items.length,
    };
  }
  return { items: [], page: 1, size: 20, total: 0 };
}

export async function getAlertDetail(id: string): Promise<AlertRecord> {
  const json = await apiFetch<{ data?: AlertRecord } & AlertRecord>(
    `/v1/monitor/alerts/${encodeURIComponent(id)}`
  );
  if (json && typeof json === "object" && "data" in json && json.data) {
    return json.data as AlertRecord;
  }
  return json as unknown as AlertRecord;
}

export interface AckAlertPayload {
  operator?: string;
  note?: string;
}

export async function ackAlert(id: string, payload: AckAlertPayload = {}): Promise<void> {
  await apiFetch(`/v1/monitor/alerts/${encodeURIComponent(id)}/ack`, {
    method: "POST",
    headers: authHeaders(),
    body: JSON.stringify(payload),
  });
}

export async function closeAlert(id: string): Promise<void> {
  await apiFetch(`/v1/monitor/alerts/${encodeURIComponent(id)}/close`, {
    method: "POST",
    headers: authHeaders(),
  });
}
