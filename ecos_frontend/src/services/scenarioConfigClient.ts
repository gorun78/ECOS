/**
 * scenarioConfigClient — 场景配置（模板/算法库）唯一 REST 通道（场景工作台简化 v0.3）
 *
 * <p>绑定 workspace {@code /api/v1/workspace/scenario-templates} 与
 * {@code /api/v1/workspace/algorithms}。只增不改。</p>
 */

import { apiFetchData } from '../api';
import { WORKSPACE_CONFIG_BASE } from './scenarioAnalysisBase';

export function scenarioTemplatesUrl(): string {
  return `${WORKSPACE_CONFIG_BASE}/scenario-templates`;
}

export function scenarioTemplateUrl(id: string): string {
  return `${scenarioTemplatesUrl()}/${encodeURIComponent(id)}`;
}

export interface ScenarioTemplateVO {
  id?: string;
  name?: string;
  category?: string;
  description?: string;
  definitionDefaults?: Record<string, unknown>;
  defaultAnalysis?: string[];
  status?: string;
  [key: string]: unknown;
}

export function fetchScenarioTemplates(): Promise<ScenarioTemplateVO[]> {
  return apiFetchData<ScenarioTemplateVO[]>(scenarioTemplatesUrl());
}

export function saveScenarioTemplate(payload: ScenarioTemplateVO): Promise<ScenarioTemplateVO> {
  const method = payload.id ? 'PUT' : 'POST';
  const url = payload.id ? scenarioTemplateUrl(String(payload.id)) : scenarioTemplatesUrl();
  return apiFetchData<ScenarioTemplateVO>(url, { method, body: JSON.stringify(payload) });
}

export function deleteScenarioTemplate(id: string): Promise<void> {
  return apiFetchData<void>(scenarioTemplateUrl(id), { method: 'DELETE' });
}

export function scenarioAlgorithmsUrl(): string {
  return `${WORKSPACE_CONFIG_BASE}/algorithms`;
}

export function scenarioAlgorithmUrl(id: string): string {
  return `${scenarioAlgorithmsUrl()}/${encodeURIComponent(id)}`;
}

export interface ScenarioAlgorithmVO {
  id?: string;
  name?: string;
  analysisType?: string;
  version?: string;
  providerType?: string;
  implementationRef?: string;
  paramsSchema?: Record<string, unknown>;
  outputSchema?: Record<string, unknown>;
  status?: string;
  [key: string]: unknown;
}

export function fetchScenarioAlgorithms(): Promise<ScenarioAlgorithmVO[]> {
  return apiFetchData<ScenarioAlgorithmVO[]>(scenarioAlgorithmsUrl());
}

export function saveScenarioAlgorithm(payload: ScenarioAlgorithmVO): Promise<ScenarioAlgorithmVO> {
  const method = payload.id ? 'PUT' : 'POST';
  const url = payload.id ? scenarioAlgorithmUrl(String(payload.id)) : scenarioAlgorithmsUrl();
  return apiFetchData<ScenarioAlgorithmVO>(url, { method, body: JSON.stringify(payload) });
}

export function deleteScenarioAlgorithm(id: string): Promise<void> {
  return apiFetchData<void>(scenarioAlgorithmUrl(id), { method: 'DELETE' });
}
