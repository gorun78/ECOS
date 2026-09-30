/**
 * Agent Mesh / Agent Runtime REST 收口 (H6-T2) — 自 stores/agent 与 queries 层迁入。
 * 原语义保持：无鉴权头；非 2xx 抛错（errorMessage 缺省 `HTTP ${status}`）。
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

const MESH = '/api/v1/agent-mesh';
const RUNTIME = '/api/v1/agent-runtime';

export function fetchMeshAgentsJson(errorMessage?: string): Promise<any> {
  return getJson(`${MESH}/agents`, errorMessage);
}

export function fetchMeshMissionsJson(errorMessage?: string): Promise<any> {
  return getJson(`${MESH}/missions`, errorMessage);
}

export function createMeshMissionJson(mission: Record<string, unknown>, errorMessage?: string): Promise<any> {
  return postJson(`${MESH}/missions`, errorMessage, mission);
}

export function executeMeshMissionJson(missionId: string, errorMessage?: string): Promise<any> {
  return postJson(`${MESH}/missions/${missionId}/execute`, errorMessage);
}

export function fetchAgentTelemetryJson(errorMessage?: string): Promise<any> {
  return getJson(`${RUNTIME}/telemetry/default`, errorMessage);
}

export function createRuntimePlanJson(goal: Record<string, unknown>, errorMessage?: string): Promise<any> {
  return postJson(`${RUNTIME}/plans`, errorMessage, goal);
}
