/**
 * 认知引擎 world-model-graph REST 收口 (H6-T2) — 自 stores/cognitive 与 queries 层迁入。
 * 原语义保持：无鉴权头；非 2xx 抛错（errorMessage 缺省为 `HTTP ${status}`，与迁移前各调用方一致）。
 */
async function getJson(url: string, errorMessage?: string): Promise<any> {
  const resp = await fetch(url);
  if (!resp.ok) throw new Error(errorMessage ?? `HTTP ${resp.status}`);
  return resp.json();
}

async function postJson(url: string, errorMessage?: string, body?: unknown): Promise<any> {
  const resp = await fetch(url, {
    method: 'POST',
    ...(body !== undefined ? { headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) } : {}),
  });
  if (!resp.ok) throw new Error(errorMessage ?? `HTTP ${resp.status}`);
  return resp.json();
}

const WMG = '/api/v1/ecos/world-model-graph';

export function fetchWorldStateJson(errorMessage?: string): Promise<any> {
  return getJson(`${WMG}/state`, errorMessage);
}

export function fetchScenariosJson(errorMessage?: string): Promise<any> {
  return getJson(`${WMG}/scenarios`, errorMessage);
}

export function fetchCausalGraphJson(errorMessage?: string): Promise<any> {
  return getJson(`${WMG}/causal-graph`, errorMessage);
}

export function postSimulationJson(scenario: Record<string, unknown>, errorMessage?: string): Promise<any> {
  return postJson(`${WMG}/scenarios`, errorMessage, scenario);
}

export function postStrategyRecommendJson(goal: string, errorMessage?: string): Promise<any> {
  return postJson(`${WMG}/strategy/recommend?goal=${encodeURIComponent(goal)}`, errorMessage);
}
