/**
 * Agent Config API Service Layer
 * Encapsulates all Agent Builder CRUD operations against AgentConfigController.
 * Uses apiFetch from ../api for consistent auth/proxy behaviour.
 *
 * AgentConfigController endpoints (9):
 *   GET    /api/v1/agents          — List all agents
 *   POST   /api/v1/agents          — Create agent
 *   GET    /api/v1/agents/{id}     — Get single agent
 *   PUT    /api/v1/agents/{id}     — Update agent
 *   DELETE /api/v1/agents/{id}     — Delete agent
 *   GET    /api/v1/agents/tools    — List available tools
 *   POST   /api/v1/agents/{id}/tools — Bind tools to agent
 *   GET    /api/v1/agents/{id}/tools — Get agent tools
 *   POST   /api/v1/agents/{id}/test  — Test agent with a message
 */

import { apiFetch } from "../api";

// ── Types ───────────────────────────────────────────────────────

export interface AgentConfig {
  id: string;
  name: string;
  description?: string;
  model: string;
  systemPrompt: string;
  temperature: number;
  maxTokens?: number;
  knowledgeBaseId?: string;
  toolIds: string[];
  status?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ToolInfo {
  id: string;
  name: string;
  description: string;
  category?: string;
  parameters?: Record<string, any>;
}

export interface KnowledgeBase {
  id: string;
  name: string;
  description?: string;
  documentCount?: number;
}

export interface AgentPromptVersion {
  id: string;
  agentId: string;
  version: number;
  content: string;
  createdAt: string;
}

export interface AgentTestRequest {
  message: string;
  context?: Record<string, any>;
}

export interface AgentTestResponse {
  success: boolean;
  responseText: string;
  toolCalls?: AgentToolCallTrace[];
  tokensUsed?: number;
  latencyMs?: number;
}

export interface AgentToolCallTrace {
  toolName: string;
  input: Record<string, any>;
  output?: string;
  error?: string;
  durationMs: number;
}

export interface AgentListResponse {
  code: number;
  message?: string;
  data: AgentConfig[];
}

export interface AgentSingleResponse {
  code: number;
  message?: string;
  data: AgentConfig;
}

export interface ToolListResponse {
  code: number;
  message?: string;
  data: ToolInfo[];
}

export interface TestResponse {
  code: number;
  message?: string;
  data: AgentTestResponse;
}

// ── API Functions ───────────────────────────────────────────────

const BASE = "/v1/agents";

/** Fetch all agents */
export async function fetchAgents(): Promise<AgentConfig[]> {
  const resp = await apiFetch<AgentListResponse>(BASE);
  return resp.data || [];
}

/** Fetch a single agent by ID */
export async function fetchAgent(id: string): Promise<AgentConfig> {
  const resp = await apiFetch<AgentSingleResponse>(`${BASE}/${id}`);
  return resp.data;
}

/** Create a new agent */
export async function createAgent(
  data: Omit<AgentConfig, "id" | "createdAt" | "updatedAt" | "status">
): Promise<AgentConfig> {
  const resp = await apiFetch<AgentSingleResponse>(BASE, {
    method: "POST",
    body: JSON.stringify(data),
  });
  return resp.data;
}

/** Update an existing agent */
export async function updateAgent(
  id: string,
  data: Partial<Omit<AgentConfig, "id" | "createdAt" | "updatedAt">>
): Promise<AgentConfig> {
  const resp = await apiFetch<AgentSingleResponse>(`${BASE}/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
  return resp.data;
}

/** Delete an agent */
export async function deleteAgent(id: string): Promise<void> {
  await apiFetch(`${BASE}/${id}`, { method: "DELETE" });
}

/** Fetch all available tools */
export async function fetchTools(): Promise<ToolInfo[]> {
  const resp = await apiFetch<ToolListResponse>(`${BASE}/tools`);
  return resp.data || [];
}

/** Bind tools to an agent */
export async function bindTool(
  agentId: string,
  toolIds: string[]
): Promise<void> {
  await apiFetch(`${BASE}/${agentId}/tools`, {
    method: "POST",
    body: JSON.stringify({ toolIds }),
  });
}

/** Get tools bound to an agent */
export async function fetchAgentTools(agentId: string): Promise<ToolInfo[]> {
  const resp = await apiFetch<ToolListResponse>(`${BASE}/${agentId}/tools`);
  return resp.data || [];
}

/** Test an agent with a message */
export async function testAgent(
  agentId: string,
  message: string,
  context?: Record<string, any>
): Promise<AgentTestResponse> {
  const resp = await apiFetch<TestResponse>(`${BASE}/${agentId}/test`, {
    method: "POST",
    body: JSON.stringify({ message, context }),
  });
  return resp.data;
}

/** Fetch available LLM models */
export async function fetchModels(): Promise<string[]> {
  try {
    const resp = await apiFetch<{ code: number; data: string[] }>("/agent/models");
    return resp.data || [];
  } catch {
    // Fallback models if backend unavailable
    return ["deepseek-v3", "deepseek-r1", "gpt-4o", "gpt-4-turbo", "claude-3.5-sonnet", "qwen-max"];
  }
}

/** Fetch available knowledge bases */
export async function fetchKnowledgeBases(): Promise<KnowledgeBase[]> {
  try {
    const resp = await apiFetch<{ code: number; data: KnowledgeBase[] }>("/v1/knowledge-bases");
    return resp.data || [];
  } catch {
    return [];
  }
}
