/**
 * ECOS 场景工作台 — 纯函数工具。
 * 从 ScenarioManagementView.tsx 拆分（消除 3 处重复指标计算）。
 *
 * 指标口径修订（§0.6.2.3，PMO-66 B2）：
 *   旧版 calcMetrics 仅按各绑定要素的「数量」累加计分，绑得越多分越高，反映不了
 *   真实拓扑；新版按「连边覆盖率」计分：
 *     - mappingCompleteness = (有 MAPPING 边的 OBJECT_TYPE / 全部 OBJECT_TYPE) * 100
 *     - integrityScore      = (有 COGNITION 边的 AI_AGENT / 全部 AI_AGENT) * 100
 *   无相应资源时占 100（平凡成立）。
 */

import type { BusinessScenario } from './types';

export interface CoverageSummary {
  /** 数据集→本体实体 覆盖率（MAPPING 边命中 OBJECT_TYPE 的占比），无 OBJECT_TYPE = 1 */
  d2iCoverage: number;
  /** 知识→Agent 覆盖率（COGNITION 边命中 AI_AGENT 的占比），无 AI_AGENT = 1 */
  k2wCoverage: number;
  /** 节点总数 */
  totalNodes: number;
  /** 边总数 */
  totalLinks: number;
}

/** 前端在缺少真实覆盖率时（如数据仍走旧 seed）给出的默认占位——可按资源直观估算。 */
const DEFAULT_COVERAGE: CoverageSummary = {
  d2iCoverage: 0,
  k2wCoverage: 0,
  totalNodes: 0,
  totalLinks: 0,
};

/** 资源列表 → 兜底覆盖率（旧 seed 兼容；优先用真实 coverage）。 */
function fallbackCoverage(
  wDatasets: string[],
  wObjectTypes: string[],
  wKnowledgeBases: string[],
  wAiAgents: string[]
): CoverageSummary {
  const totalObjects = wObjectTypes.length;
  const totalAgents = wAiAgents.length;
  if (totalObjects < 1 && totalAgents < 1) {
    return { d2iCoverage: 1, k2wCoverage: 1, totalNodes: wDatasets.length + wObjectTypes.length + wKnowledgeBases.length + wAiAgents.length, totalLinks: 0 };
  }
  // 保守旧模型：无覆盖率数据 → 一律记为 0（缺失连边未联网）
  return {
    d2iCoverage: totalObjects < 1 ? 1 : 0,
    k2wCoverage: totalAgents < 1 ? 1 : 0,
    totalNodes: wDatasets.length + wObjectTypes.length + wKnowledgeBases.length + wAiAgents.length,
    totalLinks: 0,
  };
}

/**
 * 根据真实连边覆盖率 + 静态策略线索（不变量）计算指标。
 *
 * @param wDatasets   数据集列表（仅用于细粒度占位）
 * @param wObjectTypes 本体实体列表
 * @param wKnowledgeBases 知识库列表
 * @param wAiAgents   AI Agent 列表
 * @param wInterfaces 接口列表（用于 SLA 四色）
 * @param wSecurityPolicies 安全策略（用于威胁分级）
 * @param coverage    后端 /graph 端点返回的真实连边覆盖率（优先）；未传时走 fallback
 */
export function calcMetrics(
  wDatasets: string[],
  wObjectTypes: string[],
  wKnowledgeBases: string[],
  wAiAgents: string[],
  wInterfaces: string[],
  wSecurityPolicies: string[],
  coverage?: Partial<CoverageSummary>
) {
  const cov: CoverageSummary = coverage && (coverage.d2iCoverage !== undefined || coverage.k2wCoverage !== undefined)
    ? { ...DEFAULT_COVERAGE, ...coverage }
    : fallbackCoverage(wDatasets, wObjectTypes, wKnowledgeBases, wAiAgents);

  const mappingCompleteness = clamp100(Math.round(cov.d2iCoverage * 100));
  const integrityScore = clamp100(Math.round(cov.k2wCoverage * 100));
  const threatBlockRate = wSecurityPolicies.includes('gr-pii') || wSecurityPolicies.includes('gr-approval')
    ? 100
    : Math.min(98, wSecurityPolicies.length * 25 + 20);
  const slaScore = Math.min(100, Math.max(70, 85 + (wInterfaces.length * 5)));

  return { integrityScore, mappingCompleteness, threatBlockRate, slaScore };
}

function clamp100(n: number): number {
  if (Number.isNaN(n)) return 0;
  return Math.max(0, Math.min(100, n));
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
