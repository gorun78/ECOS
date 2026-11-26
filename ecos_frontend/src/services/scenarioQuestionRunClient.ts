/**
 * 问题驱动的场景运行 API（2026-11-24 场景运行页重构）。
 *
 * <p>两段式交互：{@code prepare} 只做意图识别 + 假设检索 + 算法/模型清单（不执行），
 * 用户确认假设并选定心智模型后调 {@code run} 真正执行。这样「假设由用户确认」
 * 是真实的两步，不是前端假装的确认框。</p>
 *
 * <p>端点与后端 {@code ScenarioQuestionRunController} 一一对应；路径基址复用
 * {@link WORKSPACE_ANALYSES_BASE}（`scenarioAnalysisBase` 已持有的 workspace 单源，
 * 本文件不得再自拼 `/api/v1/workspace`——那会成为第二生产方被棘轮门判红）。</p>
 */
import { apiFetchData } from '../api';
import { WORKSPACE_ANALYSES_BASE as WORKSPACE_BASE } from './scenarioAnalysisBase';

/** 一次运行的执行过程节点（真实耗时）。 */
export interface ExecStep {
  step: string;
  status: string;
  detail: string;
  elapsedMs: number;
}

/** 假设/信念条目。 */
export interface Hypothesis {
  text: string;
  kind: string;
  source?: string;
  confirmed?: boolean;
}

/** 算法候选。 */
export interface AlgorithmOption {
  id: string;
  name: string;
  analysis_type?: string;
  analysisType?: string;
  category?: string;
  description?: string;
  executor_kind?: string;
  executorKind?: string;
  executable?: boolean;
  unavailableReason?: string;
  version?: string;
  implementation_ref?: string;
  implementationRef?: string;
}

/** 场景绑定的执行载体（PIPELINE / AI_AGENT）。 */
export interface ExecutorBinding {
  binding_type?: string;
  bindingType?: string;
  target_id?: string;
  targetId?: string;
}

export interface ScenarioExecutors {
  mentalModels: MentalModelOption[];
  pipelines: ExecutorBinding[];
  agents: ExecutorBinding[];
}

/** 心智模型选项。 */
export interface MentalModelOption {
  id: string;
  code: string;
  name: string;
  description?: string;
  pipeline_id?: string;
  pipelineId?: string;
}

/** prepare 阶段的产出。 */
export interface QuestionRunPrepare {
  scenarioId: string;
  question: string;
  intent: string;
  intentDetail: string;
  hypotheses: Hypothesis[];
  algorithms: AlgorithmOption[];
  mentalModels: MentalModelOption[];
  executors?: ScenarioExecutors;
  trace: ExecStep[];
}

/** 决策建议。 */
export interface DecisionAdvice {
  summary: string;
  actions: string[];
  risks: string[];
  confidence: string;
}

/** run 阶段的结果。 */
export interface QuestionRunResult {
  runId: string;
  scenarioId: string;
  question: string;
  intent: string;
  intentDetail: string;
  mentalModel: string;
  mentalModelName: string;
  pipelineId: string;
  hypotheses: Hypothesis[];
  algorithmId?: string;
  algorithmStatus?: string;
  algorithmResult?: {
    algorithmId?: string;
    name?: string;
    category?: string;
    code?: string;
    status?: string;
    degraded?: boolean;
    metrics?: Record<string, unknown>;
    assumptions?: string[];
    message?: string;
    elapsedMs?: number;
  } | null;
  executorType?: string;
  executorId?: string;
  executorSessionId?: string | null;
  trace: ExecStep[];
  degraded: boolean;
  result: string | null;
  advice: DecisionAdvice;
}

/** 历史运行记录（后端原样返回的快照）。 */
export interface QuestionRunRecord {
  id: string;
  scenario_id: string;
  question: string;
  intent: string;
  intent_detail?: string;
  mental_model: string;
  hypotheses?: unknown;
  algorithm_id?: string;
  algorithm_status?: string;
  algorithm_result?: unknown;
  executor_type?: string;
  executor_id?: string;
  executor_session_id?: string | null;
  exec_trace?: unknown;
  advice?: unknown;
  status: string;
  degraded: boolean;
  create_time: string;
}

/** 阶段一：意图识别 + 假设 + 算法 + 心智模型清单。 */
export function prepareQuestionRun(scenarioId: string, question: string): Promise<QuestionRunPrepare> {
  return apiFetchData<QuestionRunPrepare>(
    `${WORKSPACE_BASE}/scenarios/${encodeURIComponent(scenarioId)}/question-runs/prepare`,
    { method: 'POST', body: JSON.stringify({ question }) },
  );
}

/** 阶段二：按确认后的假设与选定心智模型执行。 */
export function executeQuestionRun(
  scenarioId: string,
  payload: {
    question: string;
    mentalModel?: string;
    executorType?: 'MENTAL_MODEL' | 'SCENARIO_PIPELINE' | 'AI_AGENT';
    executorId?: string;
    hypotheses: Hypothesis[];
    algorithmId?: string;
    algorithmInputs?: Record<string, unknown>;
  },
): Promise<QuestionRunResult> {
  return apiFetchData<QuestionRunResult>(
    `${WORKSPACE_BASE}/scenarios/${encodeURIComponent(scenarioId)}/question-runs`,
    { method: 'POST', body: JSON.stringify(payload) },
  );
}

/** 历史运行列表。 */
export function listQuestionRuns(scenarioId: string, limit = 20): Promise<QuestionRunRecord[]> {
  return apiFetchData<QuestionRunRecord[]>(
    `${WORKSPACE_BASE}/scenarios/${encodeURIComponent(scenarioId)}/question-runs?limit=${limit}`,
  );
}

/** 两次运行对比（后端只给两侧原值与是否相同，不判优劣）。 */
export function compareQuestionRuns(a: string, b: string): Promise<{
  left: QuestionRunRecord;
  right: QuestionRunRecord;
  diff: Record<string, { left: unknown; right: unknown; same: boolean }>;
}> {
  return apiFetchData(
    `${WORKSPACE_BASE}/question-runs/${encodeURIComponent(a)}/compare/${encodeURIComponent(b)}`,
  );
}
