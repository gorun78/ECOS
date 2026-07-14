/**
 * ECOS 项目工作台 — 纯函数工具。
 * 从 ScenarioManagementView.tsx 拆分（消除 3 处重复指标计算）。
 */

import type { BusinessScenario } from './types';

/** 根据绑定要素计算动态指标 */
export function calcMetrics(
  wDatasets: string[],
  wObjectTypes: string[],
  wKnowledgeBases: string[],
  wAiAgents: string[],
  wInterfaces: string[],
  wSecurityPolicies: string[]
) {
  const mappingCompleteness = Math.min(100, Math.max(45, (wDatasets.length * 15) + (wObjectTypes.length * 15)));
  const integrityScore = Math.min(100, Math.max(50, (wKnowledgeBases.length * 20) + (wAiAgents.length * 15)));
  const threatBlockRate = wSecurityPolicies.includes('gr-pii') || wSecurityPolicies.includes('gr-approval') ? 100 : Math.min(98, wSecurityPolicies.length * 25 + 20);
  const slaScore = Math.min(100, Math.max(70, 85 + (wInterfaces.length * 5)));
  return { integrityScore, mappingCompleteness, threatBlockRate, slaScore };
}

/** 检查与上次 commit 的 bindings 是否有变化 */
export function hasBindingChanged(lastCommit: any, bindings: BusinessScenario['bindings']): boolean {
  if (!lastCommit) return true;
  return (
    JSON.stringify(lastCommit.bindings?.datasets) !== JSON.stringify(bindings.datasets) ||
    JSON.stringify(lastCommit.bindings?.objectTypes) !== JSON.stringify(bindings.objectTypes) ||
    JSON.stringify(lastCommit.bindings?.knowledgeBases) !== JSON.stringify(bindings.knowledgeBases) ||
    JSON.stringify(lastCommit.bindings?.aiAgents) !== JSON.stringify(bindings.aiAgents) ||
    JSON.stringify(lastCommit.bindings?.interfaces) !== JSON.stringify(bindings.interfaces) ||
    JSON.stringify(lastCommit.bindings?.securityPolicies) !== JSON.stringify(bindings.securityPolicies)
  );
}
