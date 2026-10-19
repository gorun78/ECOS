/**
 * agentClient.ts — src/api.ts 按域拆分产物（design-05 B.9-1 / X-50 / F05-17）。
 * 经 src/api.ts re-export shim 保持既有 from "../api" 导入面零破坏。
 */
import type {
  AgentDefinition,
  ToolDefinition,
  PromptTemplate,
} from "../types";
import { API_BASE, apiFetch, apiFetchData } from "../services/httpClient";

// ── Agent Chat ──────────────────────────────────────────
/** @deprecated legacy path `/agent/chat`; please use `/api/v1/agent/chat` via the primary entry `pages/aiworkbench/api`. 保留本函数仅为兼容 AgentTestConsole 等旧调用方。 */
export async function agentChat(
  agentId: string, message: string, promptTemplate?: string, datasetContext?: any
): Promise<{
  success: boolean;
  source: string;
  responseText: string;
  thoughtTrace: { type: string; summary: string }[];
  logId: string;
  // ── Human-in-the-loop: optional Action Proposal returned by the agent ──
  actionProposal?: {
    actionId: string;
    actionName?: string;
    payload?: Record<string, string>;
    status?: "pending" | "approved" | "rejected";
  };
}> {
  console.warn("legacy api, please use /api/v1/agent/*");
  return apiFetch("/agent/chat", {
    method: "POST",
    body: JSON.stringify({ agentId, message, promptTemplate, datasetContext }),
  });
}

/** @deprecated legacy path `/agent/agents`; please use `/api/v1/agent/agents` via the primary entry. 保留本函数仅为兼容 AgentBuilder 等旧调用方。 */
export async function fetchAgents(): Promise<AgentDefinition[]> {
  console.warn("legacy api, please use /api/v1/agent/*");
  try {
    const resp = await apiFetch<{ success: boolean; data: any[] }>("/agent/agents");
    const raw = resp.data || [];
    return raw.map((a: any) => {
      let tools: string[] = [];
      if (a.toolset) {
        try { tools = typeof a.toolset === "string" ? JSON.parse(a.toolset) : a.toolset; } catch { tools = []; }
      } else if (a.tools) {
        tools = a.tools;
      }
      return {
        id: a.id,
        name: a.name,
        role: a.role,
        goal: a.goal || a.description || "",
        tools,
        systemPrompt: a.systemPrompt || "",
        capabilities: a.capabilities || [],
      };
    });
  } catch {
    return [];
  }
}

/** @deprecated legacy path `/agent/tools`; please use `/api/v1/agent/tools` via the primary entry. 保留本函数仅为兼容 AgentBuilder 等旧调用方。 */
export async function fetchTools(): Promise<ToolDefinition[]> {
  console.warn("legacy api, please use /api/v1/agent/*");
  try {
    const resp = await apiFetch<{ success: boolean; data: ToolDefinition[] }>("/agent/tools");
    return resp.data || [];
  } catch {
    return [];
  }
}

/** @deprecated legacy path `/agent/prompts`; please use `/api/v1/agent/prompts` via the primary entry. 保留本函数仅为兼容 AgentBuilder 等旧调用方。 */
export async function fetchPrompts(): Promise<PromptTemplate[]> {
  console.warn("legacy api, please use /api/v1/agent/*");
  try {
    const resp = await apiFetch<{ success: boolean; data: any[] }>("/agent/prompts");
    const raw = resp.data || [];
    return raw.map((p: any) => ({
      id: p.id,
      title: p.name || p.title || "",
      filename: p.filename || `${p.id}.md`,
      content: p.template || p.content || "",
      version: p.version || "1.0",
      category: p.category || "planning",
    }));
  } catch {
    return [];
  }
}


// ── Agent Mesh ───────────────────────────────────────────────
const AGENT_MESH_BASE = `${API_BASE}/v1/agent-mesh`;

export interface AgentMeshAgent {
  id: string;
  name: string;
  role: string;
  description: string;
  systemPrompt: string;
  toolset: string;
  model: string;
  maxIterations: number;
  status: string;
}

export interface AgentMeshMission {
  id: string;
  title: string;
  description: string;
  mode: string;
  status: string;
  inputParams?: string;
  outputResult?: string;
  errorMessage?: string;
  durationMs?: number;
  startedAt?: string;
  completedAt?: string;
  createdAt: string;
}

export interface AgentMeshTask {
  id: string;
  missionId: string;
  seq: number;
  agentId: string;
  agentName: string;
  instruction: string;
  status: string;
  resultSummary?: string;
  resultDetail?: string;
  errorMessage?: string;
  durationMs?: number;
}

export async function fetchAgentMeshAgents(): Promise<AgentMeshAgent[]> {
  return apiFetchData(`${AGENT_MESH_BASE}/agents`);
}

export async function fetchAgentMeshMissions(limit = 30): Promise<AgentMeshMission[]> {
  return apiFetchData(`${AGENT_MESH_BASE}/missions?limit=${limit}`);
}

export async function fetchAgentMeshMission(id: string): Promise<{ mission: AgentMeshMission; tasks: AgentMeshTask[] }> {
  return apiFetchData(`${AGENT_MESH_BASE}/missions/${id}`);
}

export async function fetchAgentMeshMissionTasks(id: string): Promise<AgentMeshTask[]> {
  return apiFetchData(`${AGENT_MESH_BASE}/missions/${id}/tasks`);
}

export async function createAgentMeshMission(body: any): Promise<AgentMeshMission> {
  return apiFetchData(`${AGENT_MESH_BASE}/missions`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function executeAgentMeshMission(id: string): Promise<AgentMeshMission> {
  return apiFetchData(`${AGENT_MESH_BASE}/missions/${id}/execute`, { method: "POST" });
}

// ── Workflow Designer ────────────────────────────────────────
const WF_BASE = `${API_BASE}/v1/ecos/workflows`;

export async function fetchWorkflows(pageSize = 50): Promise<{ data: any[]; total: number }> {
  return apiFetchData(`${WF_BASE}?pageSize=${pageSize}`);
}

export async function fetchWorkflow(id: string): Promise<any> {
  return apiFetchData(`${WF_BASE}/${id}`);
}

export async function createWorkflow(body?: any): Promise<any> {
  return apiFetchData(WF_BASE, {
    method: "POST",
    body: JSON.stringify(body || { name: "新建流程", code: "wf_" + Date.now(), workflowType: "APPROVAL" }),
  });
}

export async function updateWorkflow(id: string, body: any): Promise<any> {
  return apiFetchData(`${WF_BASE}/${id}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

export async function publishWorkflow(id: string): Promise<any> {
  return apiFetchData(`${WF_BASE}/${id}/publish`, { method: "PATCH" });
}

export async function testWorkflow(id: string, body?: any): Promise<any> {
  return apiFetchData(`${WF_BASE}/${id}/test`, {
    method: "POST",
    body: body ? JSON.stringify(body) : undefined,
  });
}


// ── Diagnostic Agent ──────────────────────────────────────
export async function callDiagnosticAgent(query: string): Promise<any> {
  return apiFetchData(`${API_BASE}/v1/agent/call`, {
    method: "POST",
    body: JSON.stringify({ agent: "diagnostic", query }),
  });
}

