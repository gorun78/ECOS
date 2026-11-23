/**
 * scenarioDefinitionClient — 场景定义聚合面唯一 REST 通道（场景工作台简化 v0.3）
 *
 * <p>绑定 workspace 新 Controller：{@code /api/v1/workspace/scenarios/{id}/definition}。
 * 路径单源走 {@link API_BASE}；组件不直接拼 URL。</p>
 */

import { apiFetchData } from '../api';
import { WORKSPACE_SCENARIOS_BASE } from './scenarioActionClient';

export function scenarioDefinitionUrl(scenarioId: string): string {
  return `${WORKSPACE_SCENARIOS_BASE}/${encodeURIComponent(scenarioId)}/definition`;
}

export interface DefinitionVersionVO {
  id: string;
  scenarioId: string;
  version: number;
  status: string;
  goalStatement?: string;
  successCriteria?: string[];
  releasedTime?: string;
  artifactDigest?: string;
  allowedTransitions?: string[];
  [key: string]: unknown;
}

export interface UpstreamEdgeVO {
  type: string;
  sourceType?: string;
  targetType?: string;
  reason?: string;
  severity?: string;
  contractCount?: number;
  lastVerifiedAt?: string;
}

export interface UpstreamReadinessVO {
  verdict?: string;
  coverage?: number;
  requiredEdges?: UpstreamEdgeVO[];
  presentEdges?: UpstreamEdgeVO[];
  missingEdges?: UpstreamEdgeVO[];
  islands?: Array<Record<string, unknown>>;
  probeErrors?: string[];
}

export interface DefinitionVO {
  scenario: Record<string, unknown>;
  bindings: Record<string, string[]>;
  versions: DefinitionVersionVO[];
  currentVersion?: string | null;
  upstream?: UpstreamReadinessVO;
  editable?: boolean;
  published?: boolean;
  pendingApproval?: boolean;
  releasedVersion?: string;
  artifactDigest?: string;
  [key: string]: unknown;
}

export function fetchScenarioDefinition(scenarioId: string): Promise<DefinitionVO> {
  return apiFetchData<DefinitionVO>(scenarioDefinitionUrl(scenarioId));
}

export function saveScenarioDefinition(scenarioId: string, payload: Record<string, unknown>): Promise<DefinitionVO> {
  return apiFetchData<DefinitionVO>(scenarioDefinitionUrl(scenarioId), {
    method: 'PUT',
    body: JSON.stringify(payload),
  });
}

export function publishScenarioDefinition(scenarioId: string, payload: Record<string, unknown>): Promise<DefinitionVO> {
  return apiFetchData<DefinitionVO>(`${scenarioDefinitionUrl(scenarioId)}/publish`, {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}
