/**
 * 引擎监控探针 REST 收口 (H6-T2) — 自 EngineMonitor / MonitoringCenter /
 * DataAssetsDashboard 迁入，401/403/解包语义逐字保持。
 */
import { authHeaders } from "./auth";
import { NoAccessError } from "./httpClient";

/** 原 EngineMonitor.apiFetch */
export async function engineMonitorFetch<T = any>(url: string): Promise<T> {
  const res = await fetch(url, { headers: authHeaders() });
  if (res.status === 401) {
    // Token expired/invalid → clear auth & re-login (AM-30 event,
    // PMO-43 T4, same behaviour as api.ts handleAuthExpired).
    localStorage.removeItem('token');
    window.location.hash = '#/login';
    throw new Error('HTTP_401');
  }
  if (res.status === 403) {
    // Permission denied on a valid session → canonical NoAccessError
    // (api.ts contract). UI layers detect via isNoAccessError() and
    // render t("common.noPermission"). Never log the user out.
    throw new NoAccessError();
  }
  if (!res.ok) {
    const text = await res.text().catch(() => '');
    throw new Error(text || `HTTP ${res.status}`);
  }
  const ct = res.headers.get('content-type');
  if (!ct || !ct.includes('application/json')) return null as unknown as T;
  const json = await res.json();
  // unwrap common envelopes
  if (json && typeof json === 'object' && json.data !== undefined && 'data' in json) return json.data as T;
  return json as T;
}

/** 原 MonitoringCenter.apiFetch */
export async function monitoringCenterFetch<T>(url: string, id?: string): Promise<T | null> {
  try {
    const res = await fetch(url, { headers: authHeaders() });
    if (res.status === 401) {
      // Login expired — force re-login (PMO-43 T4).
      localStorage.removeItem("token");
      window.location.hash = "#/login";
      return null;
    }
    if (res.status === 403) {
      // Permission denied on a valid session — never log out.
      // Surface via the global toast (ToastProvider mounted at main.tsx);
      // the EngineMonitor child renders its own inline no-permission state.
      // H-001: `id` is the owning card's identity — lets each EngineCard
      // filter out 403s raised by sibling cards (cross-card mis-report).
      window.dispatchEvent(new CustomEvent("ecos-403", { detail: { id, url, timestamp: Date.now() } }));
      return null;
    }
    if (!res.ok) return null;
    const ct = res.headers.get("content-type");
    if (!ct || !ct.includes("application/json")) return null;
    const json = await res.json();
    if (json && typeof json === "object" && json.data !== undefined && "data" in json) return json.data as T;
    return json as T;
  } catch {
    return null;
  }
}
