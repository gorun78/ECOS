/**
 * monitorClient.ts — src/api.ts 按域拆分产物（design-05 B.9-1 / X-50 / F05-17）。
 * 经 src/api.ts re-export shim 保持既有 from "../api" 导入面零破坏。
 */
import type {
  Goal,
  CausalLink,
  Scenario,
} from "../types";
import { API_BASE, apiFetchData, doFetch } from "../services/httpClient";

// ── World Model Viewer ────────────────────────────────────────
const WM_BASE = `${API_BASE}/v1/ecos/world-model-graph`;

export async function fetchWorldGoals(): Promise<Goal[]> {
  const resp = await doFetch(`${WM_BASE}/goals`);
  const arr = resp?.data?.data;  // API returns {data:{data:[...],total}}
  if (Array.isArray(arr)) return arr as Goal[];
  return [];
}

export async function fetchWorldScenarios(): Promise<Scenario[]> {
  try {
    const resp = await doFetch(`${WM_BASE}/scenarios`);
    const arr = resp?.data?.data;
    if (Array.isArray(arr)) return arr as Scenario[];
  } catch { /* not yet available */ }
  return [];
}

export async function fetchWorldCausalLinks(): Promise<CausalLink[]> {
  const resp = await doFetch(`${WM_BASE}/links`);
  const arr = resp?.data?.data;
  if (Array.isArray(arr)) return arr as CausalLink[];
  return [];
}

export async function fetchWorldCausalGraph(): Promise<any> {
  try {
    return await doFetch(`${WM_BASE}/causal-graph`);
  } catch { /* not yet available */ }
  return { nodes: [], edges: [] };
}

export async function fetchWorldGoalTree(): Promise<any> {
  const resp = await doFetch(`${WM_BASE}/goals/tree`);
  return resp?.data ?? [];
}

export async function fetchWorldModelAll(): Promise<[any, any, any, any, any]> {
  return Promise.all([
    fetchWorldGoals(), fetchWorldScenarios(),
    fetchWorldCausalLinks(), fetchWorldCausalGraph(),
    fetchWorldGoalTree(),
  ]);
}

export async function createWorldModelItem(type: string, body: any): Promise<any> {
  return doFetch(`${WM_BASE}/${type}`, { method: "POST", body: JSON.stringify(body) });
}

export async function updateWorldModelItem(type: string, id: string, body: any): Promise<any> {
  return doFetch(`${WM_BASE}/${type}/${id}`, { method: "PUT", body: JSON.stringify(body) });
}

export async function deleteWorldModelItem(type: string, id: string): Promise<any> {
  return doFetch(`${WM_BASE}/${type}/${id}`, { method: "DELETE" });
}

export async function compareWorldScenarios(body: any): Promise<any> {
  return doFetch(`${WM_BASE}/compare`, { method: "POST", body: JSON.stringify(body) });
}

// ── Pareto Optimization ───────────────────────────────────────
const PARETO_BASE = `${API_BASE}/v1/pareto`;

export interface ParetoSolution {
  variables: Record<string, number>;
  objectives: Record<string, number>;
}

export interface ParetoOptimizeResult {
  problemId: string;
  frontSize: number;
  solutions: ParetoSolution[];
  elapsed_ms: number;
}

export interface ParetoProblem {
  problemId: string;
  problemName: string;
  frontSize: number;
  timestamp: string;
}

/** POST /api/pareto/optimize — run multi-objective optimization */
export async function paretoOptimize(body: {
  numObjectives: number;
  numVariables: number;
  populationSize: number;
  generations: number;
}): Promise<ParetoOptimizeResult> {
  const resp = await doFetch(`${PARETO_BASE}/optimize`, {
    method: "POST",
    body: JSON.stringify(body),
  });
  return (resp?.data ?? resp) as ParetoOptimizeResult;
}

/** GET /api/pareto/problems — list previous optimization runs */
export async function fetchParetoProblems(): Promise<ParetoProblem[]> {
  const resp = await doFetch(`${PARETO_BASE}/problems`);
  const arr = resp?.data?.data ?? resp?.data ?? resp;
  if (Array.isArray(arr)) return arr as ParetoProblem[];
  return [];
}

/** GET /api/pareto/result/{problemId} — fetch a specific result */
export async function fetchParetoResult(problemId: string): Promise<ParetoOptimizeResult> {
  const resp = await doFetch(`${PARETO_BASE}/result/${encodeURIComponent(problemId)}`);
  return (resp?.data ?? resp) as ParetoOptimizeResult;
}

/** POST /api/pareto/from-scenario — generate demo from scenario */
export async function paretoFromScenario(body: { scenarioId?: string }): Promise<ParetoOptimizeResult> {
  const resp = await doFetch(`${PARETO_BASE}/from-scenario`, {
    method: "POST",
    body: JSON.stringify(body),
  });
  return (resp?.data ?? resp) as ParetoOptimizeResult;
}

// ── P2-4: Causal Graph (Neo4j-backed) ─────────────────────────
const CAUSAL_BASE = WM_BASE; // 单值收敛：因果图与世界模型图同前缀（原同值双别名，X-50）

export async function fetchCausalGraph(): Promise<any> {
  return doFetch(`${CAUSAL_BASE}/causal-graph`);
}

export async function fetchCausalPaths(from: string, to: string): Promise<any> {
  return doFetch(`${CAUSAL_BASE}/paths?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`);
}

export async function compareCausalScenarios(body: any): Promise<any> {
  return doFetch(`${CAUSAL_BASE}/compare`, { method: "POST", body: JSON.stringify(body) });
}


// ── Monitoring Dashboard ───────────────────────────────
// 对接 MonitorController (/api/monitor)
export const MONITOR_BASE = `${API_BASE}/v1/monitor`;

export interface MonitoringKpi {
  label: string;
  value: string;
  desc: string;
  icon: string;
  color: string;
}

export interface MonitoringChartPoint {
  time: string;
  [key: string]: number | string;
}

export interface MonitoringProcess {
  name: string;
  type: string;
  status: string;
  uptime: string;
  items: string;
}

export interface MonitoringAlert {
  time: string;
  module: string;
  level: string;
  message: string;
}

export interface MonitoringDashboard {
  systemMetrics: MonitoringKpi[];
  chartData: MonitoringChartPoint[];
  processes: MonitoringProcess[];
  alerts: MonitoringAlert[];
}

export async function fetchMonitoringDashboard(): Promise<MonitoringDashboard> {
  try {
    const raw = await apiFetchData<any>(MONITOR_BASE);
    const sys = raw?.system || {};
    const recentAlerts: any[] = raw?.recent_alerts || [];

    // Map backend response → MonitoringDashboard
    const systemMetrics: MonitoringKpi[] = [
      { label: "CPU Cores", value: String(sys.cpu_cores || "N/A"), desc: "Available", icon: "Cpu", color: "text-blue-400" },
      { label: "CPU Load", value: String(sys.cpu_load || "N/A"), desc: "System load", icon: "Cpu", color: "text-cyan-400" },
      { label: "Heap Used", value: (sys.heap_used_mb || 0) + " MB", desc: "JVM heap", icon: "Server", color: "text-emerald-400" },
      { label: "Heap Max", value: (sys.heap_max_mb || 0) + " MB", desc: "Max heap", icon: "Server", color: "text-violet-400" },
      { label: "Active Alerts", value: String(raw.active_alerts ?? 0), desc: "Open alerts", icon: "AlertTriangle", color: "text-red-400" },
      { label: "DQ Issues", value: String(raw.open_dq_issues ?? 0), desc: "Open DQ issues", icon: "Database", color: "text-amber-400" },
    ];

    const alerts: MonitoringAlert[] = recentAlerts.map((a: any) => ({
      time: a.created_at || "",
      module: a.rule_name || "System",
      level: a.level || "INFO",
      message: a.message || "",
    }));

    // Map backend processes (or fallback mock)
    const backendProcesses: any[] = raw?.processes || [];
    const processes: MonitoringProcess[] = backendProcesses.length > 0
      ? backendProcesses.map((p: any) => ({
          name: p.name || "Unknown",
          type: p.type || "process",
          status: p.status || "UNKNOWN",
          uptime: p.uptime || "N/A",
          items: String(p.items ?? ""),
        }))
      : [
          { name: "ECOS Gateway", type: "Java/Spring", status: "RUNNING", uptime: "N/A", items: "Port 8080" },
          { name: "PostgreSQL", type: "Database", status: "RUNNING", uptime: "N/A", items: "Port 5432" },
        ];

    // Map backend chartData (or fallback empty)
    const chartData: MonitoringChartPoint[] = (raw?.chartData || []).map((c: any) => ({
      time: c.time || "",
      cpu: c.cpu,
      memory: c.memory,
    }));

    return {
      systemMetrics,
      chartData,
      processes,
      alerts,
    };
  } catch {
    return { systemMetrics: [], chartData: [], processes: [], alerts: [] };
  }
}

export async function runSystemDiagnostics(): Promise<{ database: { status: string }; uptime_ms: number; version: string; status: string }> {
  const health = await apiFetchData<any>(`${MONITOR_BASE}/health`);
  return {
    database: { status: health?.database?.status || "UNKNOWN" },
    uptime_ms: health?.uptime_ms || 0,
    version: health?.version || "N/A",
    status: health?.status || "DOWN",
  };
}


// ── Biz Dashboard ──────────────────────────────────────────
export async function fetchBizDashboard(): Promise<any> {
  return apiFetchData(`${API_BASE}/v1/ecos/biz/dashboard`);
}

// ── Goal Tracking ────────────────────────────────────────
// NOTE: 裸路径 /api/dq/goal-tracking → DqDashboardController (datanet:18082, P3-A)
//       不用 /api/v1/dq（DqGovernanceController 没有 goal-tracking 子路径）
export async function fetchGoalTracking(goalId?: number): Promise<any> {
  const qs = goalId ? `?goalId=${goalId}` : '';
  return apiFetchData(`${API_BASE}/dq/goal-tracking${qs}`);
}


// ── Digital Twin ─────────────────────────────────────────────
// 对接 DeviceTwinController (/api/twins)

export interface TwinHealth {
  mqtt: { status: string };
  device_count: number;
}

export interface TwinDevice {
  deviceId: string;
  name: string;
  type: string;
  unit: string;
  status: string;
}

export interface TwinTelemetry {
  deviceId: string;
  value: number;
  ts: string;
}

export interface TwinShadowState {
  desired: Record<string, any>;
  reported: Record<string, any>;
}

export interface TwinDeviceStatus {
  deviceId: string;
  status: string;
  shadow: TwinShadowState;
  telemetryCount: number;
}

export interface TwinCommandResult {
  status: string;
  deviceId: string;
  command: string;
  params: Record<string, any>;
}

export async function fetchTwinHealth(): Promise<TwinHealth> {
  try {
    return await apiFetchData<TwinHealth>(`${API_BASE}/v1/twins/health`);
  } catch {
    console.warn('fetchTwinHealth: backend unavailable');
    return { mqtt: { status: 'DOWN' }, device_count: 0 };
  }
}

export async function fetchTwinDevices(): Promise<TwinDevice[]> {
  try {
    return await apiFetchData<TwinDevice[]>(`${API_BASE}/v1/twins/devices`);
  } catch {
    console.warn('fetchTwinDevices: backend unavailable');
    return [];
  }
}

export async function fetchTwinTelemetry(deviceId: string, limit = 20): Promise<TwinTelemetry[]> {
  try {
    return await apiFetchData<TwinTelemetry[]>(`${API_BASE}/v1/twins/${encodeURIComponent(deviceId)}/telemetry?limit=${limit}`);
  } catch {
    console.warn(`fetchTwinTelemetry(${deviceId}): backend unavailable`);
    return [];
  }
}

export async function sendTwinCommand(deviceId: string, command: string, params: Record<string, any> = {}): Promise<TwinCommandResult> {
  return apiFetchData<TwinCommandResult>(`${API_BASE}/v1/twins/${encodeURIComponent(deviceId)}/command`, {
    method: 'POST',
    body: JSON.stringify({ command, params }),
  });
}

export async function fetchTwinDeviceStatus(deviceId: string): Promise<TwinDeviceStatus> {
  try {
    return await apiFetchData<TwinDeviceStatus>(`${API_BASE}/v1/twins/${encodeURIComponent(deviceId)}/status`);
  } catch {
    console.warn(`fetchTwinDeviceStatus(${deviceId}): backend unavailable`);
    return { deviceId, status: 'offline', shadow: { desired: {}, reported: {} }, telemetryCount: 0 };
  }
}

