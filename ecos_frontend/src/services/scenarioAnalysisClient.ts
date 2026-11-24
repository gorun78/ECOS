/**
 * scenarioAnalysisClient — 场景分析运行面唯一 REST 通道（场景工作台简化 v0.3）
 *
 * <p>绑定 workspace {@code /api/v1/workspace/scenarios/{id}/analyses} 与
 * {@code /api/v1/workspace/analyses}。只增不改；What-If 试算仍走认知通道，不混入本面。</p>
 */

import { apiFetchData } from '../api';
import { WORKSPACE_ANALYSES_BASE } from './scenarioAnalysisBase';

export function scenarioAnalysesUrl(scenarioId: string): string {
  return `${WORKSPACE_ANALYSES_BASE}/scenarios/${encodeURIComponent(scenarioId)}/analyses`;
}

export interface AnalysisRunVO {
  id?: string;
  scenarioId?: string;
  analysisType?: string;
  runMode?: string;
  sourceType?: string;
  sourceId?: string;
  status?: string;
  degraded?: boolean;
  definitionVersion?: string | null;
  definitionDigest?: string | null;
  metric?: string;
  deviation?: number;
  createTime?: string;
  diagnosis?: Record<string, unknown>;
  forecast?: Record<string, unknown>;
  simulation?: Record<string, unknown>;
  /** PIPELINE 执行详情（节点轨迹/outputs/executionId；其它类型无此键，2026-11-23 additive）。 */
  result?: Record<string, unknown>;
  strategy?: Record<string, unknown>;
  [key: string]: unknown;
}

export function fetchScenarioAnalyses(scenarioId: string, limit = 20): Promise<AnalysisRunVO[]> {
  return apiFetchData<AnalysisRunVO[]>(`${scenarioAnalysesUrl(scenarioId)}?limit=${limit}`);
}

export function runScenarioAnalysis(scenarioId: string, payload: Record<string, unknown>): Promise<AnalysisRunVO> {
  return apiFetchData<AnalysisRunVO>(scenarioAnalysesUrl(scenarioId), {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function fetchScenarioAnalysis(analysisId: string): Promise<AnalysisRunVO> {
  return apiFetchData<AnalysisRunVO>(`${WORKSPACE_ANALYSES_BASE}/analyses/${encodeURIComponent(analysisId)}`);
}

export function compareScenarioAnalyses(baselineAnalysisId: string, candidateAnalysisId: string) {
  return apiFetchData<Record<string, unknown>>(
    `${WORKSPACE_ANALYSES_BASE}/analyses/compare?baselineAnalysisId=${encodeURIComponent(baselineAnalysisId)}&candidateAnalysisId=${encodeURIComponent(candidateAnalysisId)}`
  );
}
