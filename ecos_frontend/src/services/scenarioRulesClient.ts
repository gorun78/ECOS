/**
 * 场景规则候选客户端（V367 批次 E）。
 *
 * <p>单一通道：`GET /api/v1/workspace/scenarios/available/rules`（后端投影 buszhi 规则中心
 * id/name，不复制规则体）。空列表 = 规则库真实为空，不造演示项。</p>
 */
import { apiFetchData } from '../api';
import { WORKSPACE_ANALYSES_BASE } from './scenarioAnalysisBase';

export interface ScenarioRuleOption {
  id: string;
  name?: string;
  code?: string;
  ruleType?: string;
}

export function fetchScenarioRuleOptions(): Promise<ScenarioRuleOption[]> {
  return apiFetchData<ScenarioRuleOption[]>(`${WORKSPACE_ANALYSES_BASE}/scenarios/available/rules`);
}
