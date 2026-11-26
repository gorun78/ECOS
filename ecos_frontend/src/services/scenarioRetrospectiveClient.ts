/**
 * 场景复盘客户端（V367 批次 C）：运行记录 + 认知侧按 run 聚合。
 *
 * <p>单一通道：`GET /api/v1/workspace/scenarios/{id}/question-runs/{runId}/retrospective`。
 * 认知段 unavailable 时按原样透出，不本地合成结论。</p>
 */
import { apiFetchData } from '../api';
import { WORKSPACE_ANALYSES_BASE } from './scenarioAnalysisBase';

export interface RetrospectiveCognitive {
  runId?: string;
  runScoped?: boolean;
  unavailable?: boolean;
  message?: string;
  reconstructionMode?: string;
  invalidations?: unknown[];
  hypotheses?: unknown[];
  evidence?: unknown[];
  warnAlerts?: unknown[];
  summary?: Record<string, unknown>;
}

export interface ScenarioRetrospective {
  scenarioId: string;
  runId: string;
  run: Record<string, unknown>;
  algorithm: unknown;
  cognitive: RetrospectiveCognitive;
  previous?: Record<string, unknown>;
}

export function fetchScenarioRetrospective(
  scenarioId: string,
  runId: string,
): Promise<ScenarioRetrospective> {
  return apiFetchData<ScenarioRetrospective>(
    `${WORKSPACE_ANALYSES_BASE}/scenarios/${encodeURIComponent(scenarioId)}` +
      `/question-runs/${encodeURIComponent(runId)}/retrospective`,
  );
}
