/**
 * Platform 控制台 API 层 — 网关诊断 + 监控告警 + DLQ
 * @license SPDX-License-Identifier: Apache-2.0
 *
 * 后端契约（已实现，固定）：
 *   GET  /api/v1/system/gateway-diagnostics   → ApiResponse{ code, data:{ artifacts, filterChain, routeManifest, anonymousEndpoints, editionMatrix, traceId } }
 *   GET  /api/v1/monitor/alerts?severity=&status=&page=&size=  → { rows, total, page, size }
 *   POST /api/v1/monitor/alerts/{id}/ack
 *   GET  /api/v1/monitor/dlq?status=&page=&size=  → { rows, total, page, size }
 *   POST /api/v1/monitor/dlq/{id}/replay
 *
 * 复用 src/api 的 `apiFetchData`（自动注入 Bearer token、解 `.data`、
 * 403 抛 NoAccessError）。all admin-only；调用方用 `isNoAccessError` 感知 403。
 */

import { API_BASE, apiFetchData } from "./httpClient";

// ── Gateway Diagnostics ─────────────────────────────────────────────

export interface GatewayArtifact {
  name: string;
  port: number | string;
  mode: string;
  active: boolean;
}

export interface GatewayFilterChainItem {
  index: number;
  name: string;
  /** 实际注册 ORDER */
  ORDER: number;
  /** 规格（架构铁律）要求的 ORDER */
  specIndex: number;
}

export interface GatewayRouteManifest {
  present: boolean;
  count: number;
  serviceRoutes: number;
  monolithRoutes: number;
}

export interface GatewayEditionMatrix {
  activeProfile: string;
  /** capability 名 → 是否启用 */
  capabilities: Record<string, boolean> | Array<{ name: string; enabled: boolean }>;
}

export interface GatewayAnonymousEndpoint {
  /** 可能的字段：method, path 或 endpoint / name */
  method?: string;
  path?: string;
  endpoint?: string;
  name?: string;
  [key: string]: unknown;
}

export interface GatewayDiagnostics {
  artifacts: GatewayArtifact[];
  filterChain: GatewayFilterChainItem[];
  routeManifest: GatewayRouteManifest;
  anonymousEndpoints: GatewayAnonymousEndpoint[] | Record<string, unknown>;
  editionMatrix: GatewayEditionMatrix;
  traceId?: string;
}

/** GET /api/v1/system/gateway-diagnostics */
export async function fetchGatewayDiagnostics(): Promise<GatewayDiagnostics> {
  const data = await apiFetchData<GatewayDiagnostics>(
    `${API_BASE}/v1/system/gateway-diagnostics`,
  );
  return {
    artifacts: data.artifacts ?? [],
    filterChain: data.filterChain ?? [],
    routeManifest: data.routeManifest ?? {
      present: false,
      count: 0,
      serviceRoutes: 0,
      monolithRoutes: 0,
    },
    anonymousEndpoints: data.anonymousEndpoints ?? [],
    editionMatrix: data.editionMatrix ?? { activeProfile: "", capabilities: {} },
    traceId: data.traceId,
  };
}

// ── Alerts ─────────────────────────────────────────────────────────

export interface MonitorAlert {
  id: string;
  rule_code: string;
  severity: string;
  status: string;
  title: string;
  source_module: string;
  trace_id: string;
  ack_by?: string;
  ack_time?: string;
  close_time?: string;
  occurred_at: string;
}

export interface MonitorAlertPage {
  rows: MonitorAlert[];
  total: number;
  page: number;
  size: number;
}

/** GET /api/v1/monitor/alerts */
export async function fetchMonitorAlerts(params: {
  severity?: string;
  status?: string;
  page?: number;
  size?: number;
}): Promise<MonitorAlertPage> {
  const qs = new URLSearchParams();
  if (params.severity) qs.set("severity", params.severity);
  if (params.status) qs.set("status", params.status);
  qs.set("page", String(params.page ?? 1));
  qs.set("size", String(params.size ?? 10));
  const data = await apiFetchData<MonitorAlertPage>(
    `${API_BASE}/v1/monitor/alerts?${qs.toString()}`,
  );
  return {
    rows: data.rows ?? [],
    total: data.total ?? 0,
    page: data.page ?? params.page ?? 1,
    size: data.size ?? params.size ?? 10,
  };
}

/** POST /api/v1/monitor/alerts/{id}/ack */
export async function ackMonitorAlert(id: string): Promise<void> {
  await apiFetchData(`${API_BASE}/v1/monitor/alerts/${encodeURIComponent(id)}/ack`, {
    method: "POST",
  });
}

// ── DLQ ─────────────────────────────────────────────────────────────

export interface DlqRecord {
  id: string;
  topic: string;
  dlt_topic: string;
  error_message: string;
  attempts: number;
  first_seen_at: string;
  replayed_at?: string;
  status: string;
}

export interface DlqPage {
  rows: DlqRecord[];
  total: number;
  page: number;
  size: number;
}

/** GET /api/v1/monitor/dlq */
export async function fetchDlq(params: {
  status?: string;
  page?: number;
  size?: number;
}): Promise<DlqPage> {
  const qs = new URLSearchParams();
  if (params.status) qs.set("status", params.status);
  qs.set("page", String(params.page ?? 1));
  qs.set("size", String(params.size ?? 10));
  const data = await apiFetchData<DlqPage>(`${API_BASE}/v1/monitor/dlq?${qs.toString()}`);
  return {
    rows: data.rows ?? [],
    total: data.total ?? 0,
    page: data.page ?? params.page ?? 1,
    size: data.size ?? params.size ?? 10,
  };
}

/** POST /api/v1/monitor/dlq/{id}/replay */
export async function replayDlq(id: string): Promise<void> {
  await apiFetchData(`${API_BASE}/v1/monitor/dlq/${encodeURIComponent(id)}/replay`, {
    method: "POST",
  });
}
