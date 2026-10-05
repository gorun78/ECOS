/**
 * 分册09 消费侧 API client — 场景·年度经营预测（确定性预测闭环）。
 *
 * <p>本文件是 P07/P08/P09 界面（载体定义在卷 08 §2.2/§2.3）与口径管理 Banner
 * 消费 19 端点的<b>唯一前端通道</b>（禁各页面 raw fetch /api/v1/workspace/*，
 * 同 gitService 单通道纪律 C181）。端点族 = workspace :18090（vite 代理
 * `/api/v1/workspace` → 18090，见 vite.config.ts）：</p>
 * <ul>
 *   <li>calibers 7（listCalibers/getCaliber/[create/submit/approve/supersede 写侧 501]
 *       /validateCaliber 本层实装 4 关校验）—— R-60② 口径主权在 ontology</li>
 *   <li>forecast-runs 9（create/get/results/evidence/export/scenario-copy/compare/
 *       audit-pack/retry）—— 六要素 C209 + SUCCEEDED 只读 C210</li>
 *   <li>backtests 1（listForecastBacktests）+ periods 1（closePeriod）
 *       + previewBacktestMetrics（离线 dry-run）</li>
 *   <li>actions 1（createFcAction 五必填缺一 400，C206）</li>
 * </ul>
 *
 * <p>金额/区间/回测数字一律由认知引擎计算后经 data-engine 写通道回贴；
 * 本 client 只做<b>契约透传</b>（请求/响应类型），不做任何金额运算（铁律 §0.6）。</p>
 */
import { apiFetchData } from "../api";

const WS = "/api/v1/workspace";
const CALIBERS = `${WS}/calibers`;
const RUNS = `${WS}/forecast-runs`;
const BACKTESTS = `${WS}/backtests`;

// ── 语义类型（契约透传，非权威——权威在认知引擎 / data-engine 结果表）──────

/** 六要素（C209）：run 级 + 每行同时成立；缺任一即不可 SUCCEEDED。 */
export interface SixElement {
  forecastRunId: string;
  caliberId: string;
  caliberVersion: string;
  asOfTime: string;
  snapshotId: string;
  formulaVersion: string;
}

export type ForecastRunStatus =
  | "CREATED"
  | "DATA_CHECK"
  | "SNAPSHOT_FROZEN"
  | "RUNNING"
  | "SUCCEEDED"
  | "FAILED";

export interface ForecastRunView {
  runId: string;
  status: ForecastRunStatus;
  reused: boolean;
  reusedFromRunId: string | null;
  taskId: string | null;
  scopeHash: string | null;
  overridesHash: string | null;
  runKey: string;
  errorCode: string | null;
  errorText: string | null;
  six: SixElement;
  cellCount: number;
  /** scenario-copy 基准 run（P07 情景对比用）。 */
  baselineRunId?: string;
}

export interface CreateRunRequest {
  caliberId: string;
  caliberVersion: string;
  formulaVersion: string;
  asOfTime: string;
  snapshotId: string;
  scope: Record<string, string | string[]>;
  overrides?: Record<string, unknown>;
  force?: boolean;
}

/** 结果明细（P06 数字区 / P07 区间图消费；三值 P10/P50/P90 全存全展 R-62①）。 */
export interface ForecastResultCell {
  metricId: string;
  period: string;
  granularity: "STAGE" | "DEPARTMENT" | "TOTAL" | string;
  projectId?: string;
  departmentId?: string;
  stage?: string;
  sourceType: "ACTUAL" | "PLAN" | "PROFILE_IMPUTED" | "MANUAL_OVERRIDE" | "COMPUTED" | string;
  amountP10: string;
  amountP50: string;
  amountP90: string;
  sourceRef?: Record<string, unknown>;
}

export interface ForecastResultsResponse {
  runId: string;
  six: SixElement;
  cells: ForecastResultCell[];
  stageSums?: Record<string, string>;
  deptSums?: Record<string, string>;
  /** 后端回贴提示：本层不缓存金额，明细经 data-engine 读通道回取。 */
  routingNote?: string;
}

export interface CaliberValidateInput {
  formula: string;
  knownSymbols?: string[];
  symbolUnits?: Record<string, string>;
  expectedUnit?: string;
}

export interface CaliberValidateResult {
  ok: boolean;
  violations: string[];
  engine?: string;
}

/** FC-04 五必填（C206）：actionDesc/ownerId/dueDate/expectedImpact/kpiText 缺一即 400。 */
export interface FcActionCreate {
  actionDesc: string;
  ownerId: string;
  dueDate: string; // ISO date (yyyy-MM-dd)
  expectedImpact: string; // NUMERIC 字符串，禁 double
  kpiText: string;
  decisionId?: string;
  factorRef?: string;
  projectId?: string;
}

/** 回测五指标（C207）：preview/落库行通用；数值由认知引擎 BigDecimal 产出，前端只透传字符串。 */
export interface BacktestMetrics {
  mae: string;
  mape: string;
  biasDirection: "OVER" | "UNDER" | "NEUTRAL" | string;
  intervalCoverage: string;
  dataCoverage: string;
  zeroActualCount: number;
  sampleCount: number;
  sampleFlag?: string; // LOW_SAMPLE / null
  reviewRequired?: boolean;
  reviewRole?: string; // PENDING_ROUTING / NONE
  reviewReason?: string;
}

// ── calibers 7（R-60②：读侧 hint / 写侧 501 ⇒ 生产走 ontology）─────────────

export async function listCalibers(params?: { code?: string; status?: string }) {
  const qs = new URLSearchParams();
  if (params?.code) qs.set("code", params.code);
  if (params?.status) qs.set("status", params.status);
  const q = qs.toString();
  return apiFetchData(`${CALIBERS}${q ? "?" + q : ""}`);
}

export async function getCaliber(caliberId: string) {
  return apiFetchData(`${CALIBERS}/${encodeURIComponent(caliberId)}`);
}

export async function createCaliber(body: Record<string, unknown>) {
  return apiFetchData(CALIBERS, { method: "POST", body: JSON.stringify(body) });
}

export async function submitCaliber(caliberId: string) {
  return apiFetchData(`${CALIBERS}/${encodeURIComponent(caliberId)}/submit`, { method: "POST" });
}

export async function approveCaliber(caliberId: string) {
  return apiFetchData(`${CALIBERS}/${encodeURIComponent(caliberId)}/approve`, { method: "POST" });
}

export async function supersedeCaliber(caliberId: string, body: { changeReason: string }) {
  return apiFetchData(`${CALIBERS}/${encodeURIComponent(caliberId)}/supersede`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

/** 本层实装：口径公式 4 关静态校验（V1 语法/V2 引用闭合/V3 量纲/V4 无环），不落库。 */
export async function validateCaliber(input: CaliberValidateInput) {
  return apiFetchData<CaliberValidateResult>(`${CALIBERS}/validate`, {
    method: "POST",
    body: JSON.stringify(input),
  });
}

// ── forecast-runs 9 ─────────────────────────────────────────────────

export async function createForecastRun(req: CreateRunRequest) {
  return apiFetchData<ForecastRunView>(RUNS, {
    method: "POST",
    body: JSON.stringify(req),
  });
}

export async function getForecastRun(runId: string) {
  return apiFetchData<ForecastRunView>(`${RUNS}/${encodeURIComponent(runId)}`);
}

export async function queryForecastResults(
  runId: string,
  params?: { metric?: string; groupBy?: string; projectId?: string; departmentId?: string; period?: string; stage?: string },
) {
  const qs = new URLSearchParams();
  if (params?.metric) qs.set("metric", params.metric);
  if (params?.groupBy) qs.set("groupBy", params.groupBy);
  if (params?.projectId) qs.set("projectId", params.projectId);
  if (params?.departmentId) qs.set("departmentId", params.departmentId);
  if (params?.period) qs.set("period", params.period);
  if (params?.stage) qs.set("stage", params.stage);
  const q = qs.toString();
  return apiFetchData<ForecastResultsResponse>(
    `${RUNS}/${encodeURIComponent(runId)}/results${q ? "?" + q : ""}`,
  );
}

export async function traceForecastEvidence(runId: string, detailId: string) {
  return apiFetchData(
    `${RUNS}/${encodeURIComponent(runId)}/evidence?detailId=${encodeURIComponent(detailId)}`,
  );
}

export async function exportForecastRun(runId: string, format = "csv") {
  return apiFetchData(`${RUNS}/${encodeURIComponent(runId)}/export?format=${encodeURIComponent(format)}`);
}

/** P07 情景复制：基准 run + overrides ⇒ 新 run；非目标格 diff=0（C203/C225）。 */
export async function copyForecastScenario(runId: string, overrides?: Record<string, unknown>) {
  return apiFetchData<ForecastRunView>(`${RUNS}/${encodeURIComponent(runId)}/scenario-copy`, {
    method: "POST",
    body: JSON.stringify(overrides ?? {}),
  });
}

export async function compareForecastRuns(baselineRunId: string, scenarioRunId: string, groupBy?: string) {
  const qs = new URLSearchParams();
  qs.set("baselineRunId", baselineRunId);
  qs.set("scenarioRunId", scenarioRunId);
  if (groupBy) qs.set("groupBy", groupBy);
  return apiFetchData(`${RUNS}/compare?${qs.toString()}`);
}

export async function getForecastAuditPack(runId: string) {
  return apiFetchData(`${RUNS}/${encodeURIComponent(runId)}/audit-pack`);
}

/** C210：终态 run 重试 → 409；非终态从 DATA_CHECK 重入。 */
export async function retryForecastRun(runId: string) {
  return apiFetchData(`${RUNS}/${encodeURIComponent(runId)}/retry`, { method: "POST" });
}

// ── backtests 1 + periods 1 + preview ───────────────────────────────

export async function listForecastBacktests(params?: { period?: string; granularity?: string }) {
  const qs = new URLSearchParams();
  if (params?.period) qs.set("period", params.period);
  if (params?.granularity) qs.set("granularity", params.granularity);
  const q = qs.toString();
  return apiFetchData(`${BACKTESTS}${q ? "?" + q : ""}`);
}

export async function previewBacktestMetrics(body: {
  period?: string;
  granularity?: string;
  projectId?: string;
  departmentId?: string;
  stage?: string;
  mapeThreshold: string;
  coverageThreshold: string;
  minSample: number;
  samples: { forecast: string; actual: string; p10?: string; p90?: string }[];
}) {
  return apiFetchData<BacktestMetrics>(`${BACKTESTS}/preview`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function closePeriod(yyyMM: string) {
  return apiFetchData(`${WS}/periods/${encodeURIComponent(yyyMM)}/close`, {
    method: "POST",
  });
}

// ── actions 1 ───────────────────────────────────────────────────────

export async function createFcAction(runId: string, dto: FcActionCreate) {
  return apiFetchData(`${RUNS}/${encodeURIComponent(runId)}/actions`, {
    method: "POST",
    body: JSON.stringify(dto),
  });
}
