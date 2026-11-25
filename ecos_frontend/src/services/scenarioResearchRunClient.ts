/**
 * 场景运行 L3 · 研究循环 API（RD-Agent 模式接入首切片，2026-11-25）。
 *
 * <p>与 {@link scenarioQuestionRunClient}（L1 单轮问答）并列：这面是「多轮假设—实验—反馈—谱系」。
 * 路径基址复用 {@link WORKSPACE_ANALYSES_BASE}（workspace 单源），本文件不得再自拼
 * `/api/v1/workspace`——那会成为第二生产方被棘轮门判红。</p>
 */
import { apiFetchData } from '../api';
import { WORKSPACE_ANALYSES_BASE as WORKSPACE_BASE } from './scenarioAnalysisBase';

/** 研究准备产出（不执行）。 */
export interface ResearchPrepare {
  scenarioId: string;
  question: string;
  runLevel: string;
  context: {
    knowledgeBindings?: unknown[];
    bindingCount?: number;
    resolvable?: boolean | null;
    resolvableNote?: string;
  };
  hypotheses: ResearchHypothesis[];
  budget: ResearchBudget;
  executor: {
    kind: string;
    codeExecution: boolean;
    network: boolean;
    note: string;
  };
}

export interface ResearchHypothesis {
  text: string;
  kind: string;
  source?: string;
  confirmed?: boolean;
}

export interface ResearchBudget {
  maxLoops: number;
  maxSecondsPerLoop: number;
  maxTotalSeconds: number;
}

/** 单轮执行轨迹节点。 */
export interface ResearchStep {
  step: string;
  status: string;
  detail: string;
  elapsedMs: number;
}

/** 一轮研究结果。 */
export interface ResearchRound {
  loopIndex: number;
  runId?: string;
  parentRunId?: string | null;
  accepted?: boolean;
  degraded?: boolean;
  detail?: string;
  evaluation?: {
    accepted?: boolean;
    degraded?: boolean;
    noProgress?: boolean;
    evidence?: string;
  };
  trace?: ResearchStep[];
  advice?: {
    summary?: string;
    actions?: string[];
    risks?: string[];
    confidence?: string;
  };
}

/** 研究循环整体结果。 */
export interface ResearchRunResult {
  scenarioId: string;
  question: string;
  runLevel: string;
  branchKey: string;
  budget: ResearchBudget;
  rounds: ResearchRound[];
  sotaRunId?: string | null;
  sota?: ResearchRound | null;
  stopReason: string;
  evaluationDegraded: boolean;
  elapsedMs: number;
  candidateOnly: boolean;
}

/** 谱系记录（后端原样返回的快照）。 */
export interface ResearchRunRecord {
  id: string;
  scenario_id: string;
  question: string;
  run_level: string;
  run_type: string;
  loop_index: number | null;
  parent_run_id?: string | null;
  sota_run_id?: string | null;
  branch_key?: string | null;
  status: string;
  degraded: boolean;
  stop_reason?: string | null;
  hypotheses?: unknown;
  experiment_spec?: unknown;
  exec_trace?: unknown;
  advice?: unknown;
  budget?: unknown;
  create_time: string;
}

/** 阶段一：研究准备（不执行、不落库）。 */
export function prepareResearchRun(scenarioId: string, question: string): Promise<ResearchPrepare> {
  return apiFetchData<ResearchPrepare>(
    `${WORKSPACE_BASE}/scenarios/${encodeURIComponent(scenarioId)}/research-runs/prepare`,
    { method: 'POST', body: JSON.stringify({ question }) },
  );
}

/** 阶段二：发起研究循环（首切片同步返回整条谱系）。 */
export function executeResearchRun(
  scenarioId: string,
  payload: { question: string; hypotheses?: ResearchHypothesis[]; budget?: Partial<ResearchBudget> },
): Promise<ResearchRunResult> {
  return apiFetchData<ResearchRunResult>(
    `${WORKSPACE_BASE}/scenarios/${encodeURIComponent(scenarioId)}/research-runs`,
    { method: 'POST', body: JSON.stringify(payload) },
  );
}

/** 研究谱系列表。 */
export function listResearchRuns(scenarioId: string, limit = 50): Promise<ResearchRunRecord[]> {
  return apiFetchData<ResearchRunRecord[]>(
    `${WORKSPACE_BASE}/scenarios/${encodeURIComponent(scenarioId)}/research-runs?limit=${limit}`,
  );
}

/** 单轮研究详情（含产物引用）。 */
export function getResearchRun(runId: string): Promise<ResearchRunRecord & { artifacts?: unknown[] }> {
  return apiFetchData(
    `${WORKSPACE_BASE}/research-runs/${encodeURIComponent(runId)}`,
  );
}

/** 两轮对比（后端只给两侧原值与是否相同，不判优劣）。 */
export function compareResearchRuns(a: string, b: string): Promise<{
  left: ResearchRunRecord;
  right: ResearchRunRecord;
  diff: Record<string, { left: unknown; right: unknown; same: boolean }>;
}> {
  return apiFetchData(
    `${WORKSPACE_BASE}/research-runs/${encodeURIComponent(a)}/compare/${encodeURIComponent(b)}`,
  );
}

/** 审批留痕记录（append-only；空=尚未审批）。 */
export interface ResearchApprovalRecord {
  id: string;
  run_id: string;
  scenario_id: string;
  decision: 'APPROVED' | 'REJECTED';
  approval_ref?: string | null;
  reason?: string | null;
  approver: string;
  approver_roles?: string | null;
  run_stop_reason?: string | null;
  run_sota_run_id?: string | null;
  evidence_digest?: string | null;
  create_time: string;
}

/** 审批结果。 */
export interface ResearchApprovalResult {
  approvalId: string;
  runId: string;
  scenarioId: string;
  decision: 'APPROVED' | 'REJECTED';
  approver: string;
  approvalRef?: string | null;
  evidenceDigest?: string | null;
  candidateOnly: boolean;
  note: string;
}

/**
 * 审批一轮研究候选。
 *
 * <p><b>批准必带 approvalRef</b>（后端强校验，缺即 400）；驳回可只带 reason。
 * 审批只留痕，不自动回写蓝图/指标。</p>
 */
export function approveResearchRun(
  runId: string,
  payload: { decision: 'APPROVED' | 'REJECTED'; approvalRef?: string; reason?: string },
): Promise<ResearchApprovalResult> {
  return apiFetchData<ResearchApprovalResult>(
    `${WORKSPACE_BASE}/research-runs/${encodeURIComponent(runId)}/approve`,
    { method: 'POST', body: JSON.stringify(payload) },
  );
}

/** 某轮运行的审批历史（只追加不覆盖）。 */
export function listResearchApprovals(runId: string): Promise<ResearchApprovalRecord[]> {
  return apiFetchData<ResearchApprovalRecord[]>(
    `${WORKSPACE_BASE}/research-runs/${encodeURIComponent(runId)}/approvals`,
  );
}
