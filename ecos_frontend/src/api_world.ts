/**
 * E4.1 域拆分 · World Model / Pareto / Causal Graph
 * 原 api.ts L1023-1157 + 1446-1449
 */
import { doFetch } from "./services/httpClient";
import type { Goal, CausalLink, Scenario } from "./types";

// ── World Model Viewer ────────────────────────────────────────
const WM_BASE = "/api/v1/ecos/world-model-graph";

export async function fetchWorldGoals(): Promise<Goal[]> {
  const resp = await doFetch(`${WM_BASE}/goals`);
  const arr = resp?.data?.data;
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
const PARETO_BASE = "/api/v1/pareto";

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
const CAUSAL_BASE = "/api/v1/ecos/world-model-graph";

export async function fetchCausalGraph(): Promise<any> {
  return doFetch(`${CAUSAL_BASE}/causal-graph`);
}

export async function fetchCausalPaths(from: string, to: string): Promise<any> {
  return doFetch(`${CAUSAL_BASE}/paths?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`);
}

export async function compareCausalScenarios(body: any): Promise<any> {
  return doFetch(`${CAUSAL_BASE}/compare`, { method: "POST", body: JSON.stringify(body) });
}

// ── Goals / Causal / Scenarios (async with mock fallback) ─
export async function getGoals(): Promise<Goal[]> { return fetchWorldGoals(); }
export async function getCausalLinks(): Promise<CausalLink[]> { return fetchWorldCausalLinks(); }
export async function getScenarios(): Promise<Scenario[]> { return fetchWorldScenarios(); }
