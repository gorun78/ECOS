/**
 * 心智层人机干预面板共享类型（PMO-59 P4b）。
 *
 * <p>与后端 cognitive-engine DTO 同源：推演/回放统一响应 `CounterfactualResult`、
 * 不确定性判断 `BeliefDistributionVO`、复盘聚合 `mental-reviews` 输出。
 * 集中定义避免多个面板重复声明（前端铁律：拒绝重复组件/类型）。</p>
 */

/** 推演干预项（POST /api/v1/cognitive/counterfactual 请求体 elements） */
export interface Intervention {
  variableName: string;
  op: 'SET' | 'DELTA';
  value: number;
}

/** 风险四指标（纯 Java 数值计算，0 LLM） */
export interface RiskMetrics {
  expectedBenefit: number;
  maxDrawdown: number;
  lossProbability: number;
  volatilityRange: number[];
}

/** 敏感性 Top3 单条 */
export interface SensitivityItem {
  variable: string;
  sensitivity: number;
}

/** 被排除假设（失效/归档，不进入推演前提） */
export interface ExcludedAssumption {
  hypothesisId: string;
  status: string;
  invalidReason?: string;
}

/** 回放分布点（outcome + prob） */
export interface ReplayDistPoint {
  outcome: string;
  prob: number;
}

/** 时间回放元信息（P3b：指定历史版本只读重算凭证） */
export interface ReplayMeta {
  replayedVersion: number;
  believedDistribution: ReplayDistPoint[];
  versionUpdatedAt?: string;
  assumptionsValidAtReplayTime: number;
  currentVersion: number;
}

/** 推演 / 回放统一响应（后端 CounterfactualResult） */
export interface CounterfactualResult {
  requestEcho?: {
    domain?: string;
    variableName?: string;
    sampleCount?: number;
    seed?: number;
    interventions?: Record<string, string>;
  };
  baselineMean: number;
  intervenedMean: number;
  riskMetrics: RiskMetrics;
  sensitivityTop3: SensitivityItem[];
  assumptionRefs: string[];
  excludedAssumptions: ExcludedAssumption[];
  sampleCount: number;
  seed: number;
  variableName?: string;
  scenarioSummary?: string;
  replayMeta?: ReplayMeta | null;
}

/** 不确定性判断（后端 BeliefDistributionVO，GET /api/v1/cognitive/beliefs） */
export interface BeliefDistribution {
  id: string;
  variableName?: string;
  domain?: string;
  distribution?: ReplayDistPoint[];
  version: number;
  manualOverride?: boolean;
  overrideReason?: string;
  status?: string;
}

/** 复盘聚合-假设时间线单条 */
export interface ReviewHypothesis {
  id: string;
  hypothesisCode?: string;
  domain?: string;
  metricRef?: string;
  status?: string;
  isValid?: boolean;
  invalidAt?: string;
  invalidReason?: string;
  updatedAt?: string;
}

/** 复盘聚合-告警留痕单条（ecos_warn_log 只读 join） */
export interface ReviewWarnAlert {
  id?: string;
  logId?: string;
  warnType?: string;
  warnLevel?: string;
  warnObjId?: string;
  warnObjName?: string;
  warnMessage?: string;
  faultContext?: Record<string, unknown> | null;
  reviewTag?: string;
  warnTime?: string;
}

/** 复盘聚合-不确定性判断版本时间线（按变量分组） */
export interface ReviewBeliefTimeline {
  variableName?: string;
  domain?: string;
  versions?: Array<{
    version: number;
    distribution?: ReplayDistPoint[];
    manualOverride?: boolean;
    status?: string;
    updatedAt?: string;
  }>;
}

/** 复盘聚合-作废 run 留痕（V130） */
export interface ReviewRunImpact {
  eventId?: string;
  hypothesisId?: string;
  runId?: string;
  autoDetected?: boolean;
  supersededAt?: string;
}

/** 复盘聚合响应 */
export interface MentalReviewResult {
  tag?: string;
  since?: string | null;
  reconstructionMode?: string;
  beliefTimelines?: ReviewBeliefTimeline[];
  hypotheses?: ReviewHypothesis[];
  evidence?: Array<{ id?: string; evidenceCode?: string; sourceType?: string; confidence?: number; status?: string }>;
  runImpacts?: ReviewRunImpact[];
  warnAlerts?: ReviewWarnAlert[];
  summary?: Record<string, number>;
}

/** 数值展示（保留 4 位，压掉浮点长尾） */
export function formatNum(v: number | null | undefined): string {
  if (v === null || v === undefined || Number.isNaN(v)) {
    return '-';
  }
  return Number(v).toFixed(4);
}

/** 时间展示（ISO → 本地可读，空值给占位） */
export function formatTime(raw: string | null | undefined, emptyPlaceholder: string): string {
  if (!raw) {
    return emptyPlaceholder;
  }
  return String(raw).replace('T', ' ').slice(0, 19);
}
