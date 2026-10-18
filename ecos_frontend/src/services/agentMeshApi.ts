/**
 * Agent Mesh / Agent Runtime REST 收口 (H6-T2；传输腿合流单源 #143/B5-11c) — 自 stores/agent 与 queries 层迁入。
 *
 * <p>**本域缺陷本体（D-B5-35，改前实测）**：本文件头注释原写「无鉴权头」，六腿一律裸 `fetch(url)`
 * 不带 `Authorization`。而 gateway 侧 `SecurityConfig`（sysman-impl）=
 * `.requestMatchers(AnonymousEndpointRegistry.permitAllPatterns()).permitAll()` ＋
 * `anyRequest().authenticated()`，实测匿名面登记零条 `agent-mesh` / `agent-runtime`
 * ⇒ **六腿改前恒 401**，Agent 网格/运行时面板拿到的是「传输失败」被 store 的
 * `catch → set({error})` 折叠，而 `useAgentStore` 的 `agents: []` 初值使空列表看起来像真相。</p>
 *
 * <p>**与 D-B5-33（世界模型四死腿）的差别**：这六腿的后端映射**实存**——
 * `AgentMeshController` @ `/api/agent-mesh`（ai-engine-impl）有 GET `/agents`、GET `/missions`、
 * POST `/missions`、POST `/missions/{id}/execute`，前端用的 `/api/v1/agent-mesh/…` 前缀由
 * gateway `VersionPrefixRewriteFilter` 正向条目 `/api/v1/agent-mesh/ → /api/agent-mesh/` 改写；
 * `AgentRuntimeController` @ `/api/v1/agent-runtime`（agent-service）有 GET `/telemetry/{agentId}`、
 * POST `/plans`。⇒ 补上 Bearer 即转为真实可达腿（本批只到「源码 + 配置」两面取证，
 * **未起服 curl、未 live 复验**，不宣布功能已可用）。</p>
 *
 * <p>语义保持：`errorMessage` 存在时用它包裹单源抛出的错误（`cause` 保留原始错误），
 * 解包口径由 `apiFetchData` 单源承担（`json.data !== undefined ? json.data : json`）；
 * 消费面 `useAgentStore` / `useEcosApi` 五处均写 `json.data || json`，解包后无 `data` 键 ⇒ 回落整包，零改动。</p>
 */
import { API_BASE, apiFetchData } from "./httpClient";

async function getJson(url: string, errorMessage?: string): Promise<any> {
  try {
    return await apiFetchData(url);
  } catch (e) {
    if (errorMessage) throw new Error(errorMessage, { cause: e });
    throw e;
  }
}

async function postJson(url: string, errorMessage?: string, body?: unknown): Promise<any> {
  try {
    // 不传 headers：单源 `{ headers, ...options }` 形态下自定义头会整组替换掉
    // Authorization（D-B5-34），Content-Type 单源已默认设置。
    return await apiFetchData(
      url,
      body === undefined
        ? { method: 'POST' }
        : { method: 'POST', body: JSON.stringify(body) },
    );
  } catch (e) {
    if (errorMessage) throw new Error(errorMessage, { cause: e });
    throw e;
  }
}

const MESH = `${API_BASE}/v1/agent-mesh`;
const RUNTIME = `${API_BASE}/v1/agent-runtime`;

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
