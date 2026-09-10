/**
 * PMO-48-A T5 — Data Quality governance (规则中心/6 维度/自检) API 层
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 * 后端已就绪 (T2 已过三滤波器, T3 落地):
 *   GET /api/v1/dq/rules?{category}&{domain}&{status}&{pageNum}&{pageSize} → ApiResponse<List<DqRuleVO>>
 *   GET /api/v1/dq/rules/{id} → ApiResponse<DqRuleDetailVO>
 *   GET /api/v1/dq/rules/dimension-registry → ApiResponse<List<DqDimensionRegistryVO>>
 *   POST/PUT/DELETE → 后端 Phase 2 就绪, 当前 405, 前端按 toast "onlyReadPhase1" 处理
 *   POST /api/v1/dq/health/selfcheck → ApiResponse<SelfCheckResult> (T6 提供, 当前可 404)
 *
 * 复用 src/api.ts 的 `apiFetchData`(自动注入 Bearer token, 解 .data, 抛 403 NoAccessError)。
 * 不复用老 fetchDqRules (指向 /api/v1/dq/dashboard 等已 405 旧路径)。
 */

import { apiFetchData } from "../../api";

const DQ_GOV_BASE = "/api/v1/dq";

/** T3 规则 VO — 与后端 DqRuleVO 字段对齐(驼峰) */
export interface DqRuleVO {
  id: string;
  ruleName: string;
  ruleCode: string;
  category: string;
  domain: string;
  ruleType: string;
  severity: string;
  targetKind: string;
  targetTable?: string;
  targetField?: string;
  status: string;
  version?: number;
  parametersJson?: string;
  createdAt?: string;
  updatedAt?: string;
}

/** T3 规则详情 — 列表 VO 字段 + 扩展 */
export interface DqRuleDetailVO extends DqRuleVO {
  description?: string;
  owner?: string;
  parameters?: Record<string, unknown>;
  history?: Array<{ version: number; updatedAt: string; changedBy?: string; diff?: string }>;
}

/** T3 维度注册表 VO — 与后端 DqDimensionRegistryVO 字段对齐 */
export interface DqDimensionRegistryVO {
  dimension: string;
  name: string;
  ruleTypes: string[];
  defaultWeight: number;
  description: string;
}

/** T6 自检结果 VO — 占位接口, T6 落地后按实际后端结构对齐 */
export interface DqSelfCheckResult {
  success: boolean;
  total: number;
  passed: number;
  failed: number;
  checks: Array<{ name: string; passed: boolean; message?: string }>;
  execAt?: string;
}

/** 规则状态机枚举 — 与后端 DqRule status 字段对齐 */
export type DqRuleStatus =
  | 'DRAFT'
  | 'IN_REVIEW'
  | 'ACTIVE'
  | 'DEPRECATED'
  | 'SUPERSEDED'
  | 'REJECTED'
  | 'DISABLED';

/** 规则创建/更新 DTO（后端 T7c DqRuleDTO @Data） */
export interface DqRuleDTO {
  ruleName?: string;
  ruleCode?: string;
  category?: string;
  domain?: string;
  ruleType?: string;
  severity?: string;
  targetKind?: string;
  targetTable?: string;
  targetField?: string;
  parameters?: Record<string, unknown>;
}

/** 状态转换请求体 DqRuleActionDTO — 按动作拆分字段 */
export interface DqRuleActionDTO {
  submitter?: string;
  approver?: string;
  rejector?: string;
  operator?: string;
  reason?: string;
}

/** 版本快照 VO — GET /rules/{id}/versions 返回 */
export interface DqRuleVersionVO {
  ruleId: string;
  version: number;
  snapshotJson: string;
  changedBy: string;
  changedAt: string;
  changeNote?: string;
}

/** 状态动作类型 */
export type DqRuleActionType = 'submit' | 'approve' | 'reject' | 'deprecate' | 'supersede' | 'disable';

/**
 * 列表查询(支持 category / domain / status 三维筛选 + 分页)
 * 失败抛 Error(可能为 403 NoAccessError / 405 业务);由调用方 catch。
 */
export async function fetchDqGovernanceRules(params?: {
  category?: string;
  domain?: string;
  status?: string;
  pageNum?: number;
  pageSize?: number;
}): Promise<DqRuleVO[] | null> {
  const sp = new URLSearchParams();
  if (params?.category) sp.append("category", params.category);
  if (params?.domain) sp.append("domain", params.domain);
  if (params?.status) sp.append("status", params.status);
  if (params?.pageNum) sp.append("pageNum", String(params.pageNum));
  if (params?.pageSize) sp.append("pageSize", String(params.pageSize));
  const qs = sp.toString();
  const resp = await apiFetchData<any>(`${DQ_GOV_BASE}/rules${qs ? `?${qs}` : ""}`);
  const arr = Array.isArray(resp) ? resp : (resp?.data ?? null);
  return arr ?? null;
}

/** 详情查询 */
export async function fetchDqGovernanceRuleDetail(id: string): Promise<DqRuleDetailVO | null> {
  try {
    return await apiFetchData<DqRuleDetailVO>(`${DQ_GOV_BASE}/rules/${id}`);
  } catch (e) {
    // 404 / 网络错误不阻塞主流程, 由调用方决定是否展示错误
    throw e;
  }
}

/** 6 维度注册表(T3 端点) */
export async function fetchDqDimensionRegistry(): Promise<DqDimensionRegistryVO[] | null> {
  try {
    const resp = await apiFetchData<DqDimensionRegistryVO[] | { data?: DqDimensionRegistryVO[] }>(
      `${DQ_GOV_BASE}/rules/dimension-registry`
    );
    if (Array.isArray(resp)) return resp;
    return resp?.data ?? null;
  } catch (e) {
    // 端点未就绪/权限异常 → 占位, UI 显示 placeholder
    return null;
  }
}

/** 自检(T6 端点, 当前 404 由调用方提示) */
export async function runDqSelfCheck(): Promise<DqSelfCheckResult | null> {
  return apiFetchData<DqSelfCheckResult>(`${DQ_GOV_BASE}/health/selfcheck`, { method: "POST" });
}

/**
 * PMO-48-B T8 — 评分 API（后端 /api/v1/dq/scores/**）
 *
 * 5 端点:
 *   GET  /api/v1/dq/scores?assetType=TABLE&assetId=xxx          → DqAssetScoreVO
 *   GET  /api/v1/dq/scores/trend?assetType=TABLE&assetId=xxx&days=30 → DqScoreTrendVO[]
 *   GET  /api/v1/dq/scores/grade?grade=F                         → DqAssetScoreVO[]
 *   GET  /api/v1/dq/scores/system                                → DqScoreSystemVO
 *   POST /api/v1/dq/scores/recompute?assetType=TABLE&assetId=xxx → DqAssetScoreVO
 */

/** T8 资产评分 VO — 与后端 DqAssetScoreVO 字段对齐(驼峰) */
export interface DqAssetScoreVO {
  assetType: string;
  assetId: string;
  /** 加权汇总分 0.0-1.0 */
  rolledScore: number;
  /** 等级 A/B/C/D/F (后端按阈值表 DqDimension#gradeOf 算) */
  grade: string;
  /** 6 维分数 (dimension name → 0.0-1.0) */
  dimensionScores: Record<string, number>;
  lastEvaluatedAt?: string;
  sampleSize?: number;
  distinctRuleCount?: number;
  weightSum?: number;
}

/** T8 评分趋势 VO — 按天 × 维度行 */
export interface DqScoreTrendVO {
  /** ISO 日期 (YYYY-MM-DD) */
  date: string;
  /** 维度 name 大写 (COMPLETENESS/ACCURACY/CONSISTENCY/FRESHNESS/UNIQUENESS/VALIDITY) */
  dimension: string;
  /** 当日 0.0-1.0 */
  scoreValue: number;
  hits?: number;
}

/** T8 系统健康度 VO */
export interface DqScoreSystemVO {
  overallScore: number;
  count: number;
  grade: string;
  perDimension: Record<string, number>;
}

/** 查资产当前评分 (404 时返回 null 让调用方兜底) */
export async function fetchDqScoreAsset(
  assetType: string,
  assetId: string,
): Promise<DqAssetScoreVO | null> {
  const qs = `?assetType=${encodeURIComponent(assetType)}&assetId=${encodeURIComponent(assetId)}`;
  try {
    const resp = await apiFetchData<{ code?: number; message?: string; data: DqAssetScoreVO | null } | DqAssetScoreVO>(
      `${DQ_GOV_BASE}/scores${qs}`,
    );
    // 404 后端返回 {code: 404, message: "DQ_SCORE_NOT_FOUND"} + data 字段为 null
    if (!resp || typeof resp !== "object") return null;
    const r = resp as unknown as { data?: DqAssetScoreVO | null };
    if (r.data === undefined || r.data === null) {
      const direct = resp as unknown as DqAssetScoreVO;
      return direct && (direct.rolledScore !== undefined || direct.assetId !== undefined)
        ? (direct as DqAssetScoreVO)
        : null;
    }
    return r.data;
  } catch (e) {
    // 404 → null (前端展示 "未评分" + 手动重算); 其它错误抛
    if (e instanceof Error && /404/.test(e.message)) return null;
    throw e;
  }
}

/** 趋势查询 (按天分组，回看 days 天，上限 365) */
export async function fetchDqScoreTrend(
  assetType: string,
  assetId: string,
  days: number,
): Promise<DqScoreTrendVO[]> {
  const safeDays = Math.max(1, Math.min(days, 365));
  const qs = `?assetType=${encodeURIComponent(assetType)}&assetId=${encodeURIComponent(assetId)}&days=${safeDays}`;
  try {
    const resp = await apiFetchData<{ data?: DqScoreTrendVO[] } | DqScoreTrendVO[]>(
      `${DQ_GOV_BASE}/scores/trend${qs}`,
    );
    if (Array.isArray(resp)) return resp;
    return (resp as { data?: DqScoreTrendVO[] })?.data ?? [];
  } catch {
    return [];
  }
}

/** 按等级查资产 (grade 空 = 全部，按 rolledScore ASC, 最低分优先) */
export async function fetchDqScoreGrade(grade: string): Promise<DqAssetScoreVO[]> {
  const qs = grade ? `?grade=${encodeURIComponent(grade)}` : "";
  try {
    const resp = await apiFetchData<{ data?: DqAssetScoreVO[] } | DqAssetScoreVO[]>(
      `${DQ_GOV_BASE}/scores/grade${qs}`,
    );
    if (Array.isArray(resp)) return resp;
    return (resp as { data?: DqAssetScoreVO[] })?.data ?? [];
  } catch {
    return [];
  }
}

/** 系统级健康度 (AVG overall + 近 1 天 6 维均值) */
export async function fetchDqScoreSystem(): Promise<DqScoreSystemVO> {
  try {
    const resp = await apiFetchData<{ data?: DqScoreSystemVO } | DqScoreSystemVO>(
      `${DQ_GOV_BASE}/scores/system`,
    );
    if (resp && typeof resp === "object" && !Array.isArray(resp)) {
      const r = resp as { data?: DqScoreSystemVO };
      if (r.data) return r.data;
      const direct = resp as unknown as DqScoreSystemVO;
      if (direct && typeof direct.overallScore === "number") return direct as DqScoreSystemVO;
    }
    return { overallScore: 0, count: 0, grade: "F", perDimension: {} };
  } catch {
    return { overallScore: 0, count: 0, grade: "F", perDimension: {} };
  }
}

/** 手动触发重算 (200 + data=null 时返回 null 提示无规则) */
export async function recomputeDqScore(
  assetType: string,
  assetId: string,
): Promise<DqAssetScoreVO | null> {
  const qs = `?assetType=${encodeURIComponent(assetType)}&assetId=${encodeURIComponent(assetId)}`;
  try {
    const resp = await apiFetchData<{ data?: DqAssetScoreVO | null } | DqAssetScoreVO | null>(
      `${DQ_GOV_BASE}/scores/recompute${qs}`,
      { method: "POST" },
    );
    if (!resp) return null;
    const r = resp as { data?: DqAssetScoreVO | null };
    if (r.data !== undefined) return r.data ?? null;
    const direct = resp as unknown as DqAssetScoreVO | null;
    return direct && (direct.rolledScore !== undefined || direct.assetId !== undefined)
      ? (direct as DqAssetScoreVO)
      : null;
  } catch {
    return null;
  }
}

/** 创建规则（后端落库为 DRAFT），返回新规则 id */
export async function createDqGovernanceRule(dto: DqRuleDTO): Promise<{ success: boolean; id?: string; error?: string }> {
  try {
    const resp = await apiFetchData<unknown>(`${DQ_GOV_BASE}/rules`, {
      method: "POST",
      body: JSON.stringify(dto),
    });
    // 后端 ApiResponse 解 .data 后可能是 {id} 或裸 id
    const id =
      resp && typeof resp === "object"
        ? ((resp as Record<string, unknown>).id as string | undefined)
        : (resp as string | undefined);
    return { success: true, id };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : String(e) };
  }
}

/** 更新规则 —— 仅 DRAFT 可编辑 */
export async function updateDqGovernanceRule(id: string, dto: DqRuleDTO): Promise<{ success: boolean; error?: string }> {
  try {
    await apiFetchData<unknown>(`${DQ_GOV_BASE}/rules/${id}`, {
      method: "PUT",
      body: JSON.stringify(dto),
    });
    return { success: true };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : String(e) };
  }
}

/** 逻辑删除（DELETE → is_deleted=1 / status=ARCHIVED） */
export async function deleteDqGovernanceRule(id: string): Promise<{ success: boolean; error?: string }> {
  try {
    await apiFetchData<unknown>(`${DQ_GOV_BASE}/rules/${id}`, { method: "DELETE" });
    return { success: true };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : String(e) };
  }
}

/**
 * 状态机动作 —— 动作名=subPath: submit / approve / reject / deprecate / supersede / disable
 * payload 按动作自动拆分到 submitter / approver / rejector / operator 字段
 */
export async function dqRuleAction(
  id: string,
  action: DqRuleActionType,
  payload?: { submitter?: string; approver?: string; rejector?: string; operator?: string; reason?: string }
): Promise<{ success: boolean; error?: string }> {
  const body: DqRuleActionDTO = {};
  const raw = payload ?? {};
  switch (action) {
    case "submit":
      body.submitter = raw.submitter;
      break;
    case "approve":
      body.approver = raw.approver;
      break;
    case "reject":
      body.rejector = raw.rejector;
      body.reason = raw.reason;
      break;
    default:
      body.operator = raw.operator;
      body.reason = raw.reason;
  }
  try {
    await apiFetchData<unknown>(`${DQ_GOV_BASE}/rules/${id}/${action}`, {
      method: "POST",
      body: JSON.stringify(body),
    });
    return { success: true };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : String(e) };
  }
}

/** 版本历史 —— GET /rules/{id}/versions，失败返回空数组（占位提示由 UI 提供） */
export async function fetchDqGovernanceVersions(id: string): Promise<DqRuleVersionVO[]> {
  try {
    const resp = await apiFetchData<unknown>(`${DQ_GOV_BASE}/rules/${id}/versions`);
    if (Array.isArray(resp)) return resp as DqRuleVersionVO[];
    if (resp && typeof resp === "object") {
      const inner = (resp as { data?: unknown }).data;
      if (Array.isArray(inner)) return inner as DqRuleVersionVO[];
    }
    return [];
  } catch {
    return [];
  }
}

/**
 * PMO-48-C T14 — 监控调度 / 告警中心 / 工单中心 API 层
 *
 * 后端已就绪 (Phase 3 T11/T12/T13):
 *   Schedules:  GET/POST/PUT/DELETE /api/v1/dq/schedules + POST .../{id}/trigger
 *   Alerts:     GET /api/v1/dq/alerts + POST .../{id}/ack + POST .../{id}/resolve
 *   Work Orders: GET /api/v1/dq/work-orders + 状态机动作 (assign/start/resolve/verify/close/reject/run-rca)
 */

/* ── 监控调度 ─────────────────────────────────────────────────── */

/** T11 调度计划 VO — 与后端 DqScheduleVO 字段对齐（驼峰） */
export interface DqScheduleVO {
  id: string;
  name: string;
  triggerType: string;
  cronExpression?: string;
  eventType?: string;
  ruleIds: string[];
  scopeType: string;
  scopeId: string;
  enabled: boolean;
  maxRuntimeSeconds: number;
  createdBy?: string;
  createdAt?: string;
  updatedAt?: string;
}

/**
 * 调度列表
 * @returns 列表；网络错误/后端未就绪时返回 null
 */
export async function fetchDqSchedules(): Promise<DqScheduleVO[] | null> {
  try {
    const resp = await apiFetchData<unknown>(`${DQ_GOV_BASE}/schedules`);
    const arr = Array.isArray(resp) ? resp : (resp as { data?: unknown })?.data;
    return (Array.isArray(arr) ? arr : null) as DqScheduleVO[] | null;
  } catch {
    return null;
  }
}

/**
 * 手动触发调度
 * @returns { success, error? }
 */
export async function runDqSchedule(scheduleId: string): Promise<{ success: boolean; error?: string }> {
  try {
    await apiFetchData<unknown>(`${DQ_GOV_BASE}/schedules/${scheduleId}/trigger`, { method: "POST" });
    return { success: true };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : String(e) };
  }
}

/* ── 告警中心 ─────────────────────────────────────────────────── */

/** T12 告警记录 VO — 与后端 DqAlertVO 字段对齐（驼峰） */
export interface DqAlertVO {
  id: string;
  ruleId: string;
  alertLevel: string;
  alertType: string;
  assetId?: string;
  assetName?: string;
  ruleName: string;
  message: string;
  payload?: Record<string, unknown>;
  status: string;
  escalatedTo?: string;
  notifyCount: number;
  lastNotifyAt?: string;
  ackBy?: string;
  ackAt?: string;
  resolvedBy?: string;
  resolvedAt?: string;
  resolvedNote?: string;
  createdAt: string;
}

/**
 * 告警列表（可按 level / status 过滤）
 * @returns 列表；网络错误/后端未就绪时返回 null
 */
export async function fetchDqAlerts(
  query?: { level?: string; status?: string; sort?: string },
): Promise<DqAlertVO[] | null> {
  const sp = new URLSearchParams();
  if (query?.level) sp.append("level", query.level);
  if (query?.status) sp.append("status", query.status);
  if (query?.sort) sp.append("sort", query.sort);
  const qs = sp.toString();
  try {
    const resp = await apiFetchData<unknown>(`${DQ_GOV_BASE}/alerts${qs ? `?${qs}` : ""}`);
    const arr = Array.isArray(resp) ? resp : (resp as { data?: unknown })?.data;
    return (Array.isArray(arr) ? arr : null) as DqAlertVO[] | null;
  } catch {
    return null;
  }
}

/**
 * 确认告警（POST /alerts/{id}/ack）
 */
export async function ackDqAlert(id: string): Promise<{ success: boolean; error?: string }> {
  try {
    await apiFetchData<unknown>(`${DQ_GOV_BASE}/alerts/${id}/ack`, { method: "POST" });
    return { success: true };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : String(e) };
  }
}

/**
 * 解决告警（POST /alerts/{id}/resolve，body 含 note）
 */
export async function resolveDqAlert(id: string, note: string): Promise<{ success: boolean; error?: string }> {
  try {
    await apiFetchData<unknown>(`${DQ_GOV_BASE}/alerts/${id}/resolve`, {
      method: "POST",
      body: JSON.stringify({ note }),
    });
    return { success: true };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : String(e) };
  }
}

/* ── 工单中心 ─────────────────────────────────────────────────── */

/** T12/T13 工单 VO — 与后端 DqWorkOrderVO 字段对齐（驼峰） */
export interface DqWorkOrderVO {
  id: string;
  orderNo: string;
  alertId?: string;
  ruleId: string;
  assetId?: string;
  title: string;
  description: string;
  status: string;
  handlingMode: string;
  severity: string;
  assignedTo?: string;
  assignedAt?: string;
  repairAction?: string;
  repairStatus?: string;
  repairLog?: string;
  verifiedBy?: string;
  verifiedAt?: string;
  verifyPass?: boolean;
  verifyNote?: string;
  resolvedBy?: string;
  resolvedAt?: string;
  resolutionNote?: string;
  rcaResult?: string;
  rcaConfidence?: number;
  rcaAnalyzedAt?: string;
  retryCount: number;
  createdAt: string;
  updatedAt?: string;
  closedAt?: string;
}

/**
 * 工单列表（可按 status / severity 过滤）
 * @returns 列表；网络错误/后端未就绪时返回 null
 */
export async function fetchDqWorkOrders(
  query?: { status?: string; severity?: string; sort?: string },
): Promise<DqWorkOrderVO[] | null> {
  const sp = new URLSearchParams();
  if (query?.status) sp.append("status", query.status);
  if (query?.severity) sp.append("severity", query.severity);
  if (query?.sort) sp.append("sort", query.sort);
  const qs = sp.toString();
  try {
    const resp = await apiFetchData<unknown>(`${DQ_GOV_BASE}/work-orders${qs ? `?${qs}` : ""}`);
    const arr = Array.isArray(resp) ? resp : (resp as { data?: unknown })?.data;
    return (Array.isArray(arr) ? arr : null) as DqWorkOrderVO[] | null;
  } catch {
    return null;
  }
}

/**
 * 工单详情
 */
export async function fetchDqWorkOrderDetail(id: string): Promise<DqWorkOrderVO | null> {
  try {
    const resp = await apiFetchData<unknown>(`${DQ_GOV_BASE}/work-orders/${id}`);
    if (!resp || typeof resp !== "object") return null;
    const r = resp as { data?: DqWorkOrderVO };
    if (r.data) return r.data;
    const direct = resp as unknown as DqWorkOrderVO;
    if (direct && (direct.id !== undefined || direct.orderNo !== undefined)) return direct as DqWorkOrderVO;
    return null;
  } catch {
    return null;
  }
}

/**
 * 指派工单（POST /work-orders/{id}/assign?to=xxx）
 */
export async function assignDqWorkOrder(id: string, to: string): Promise<{ success: boolean; error?: string }> {
  try {
    await apiFetchData<unknown>(
      `${DQ_GOV_BASE}/work-orders/${id}/assign?to=${encodeURIComponent(to)}`,
      { method: "POST" },
    );
    return { success: true };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : String(e) };
  }
}

/**
 * 通用工单状态动作（start/resolve/verify/close/reject/run-rca）
 *
 * @param action  子路径：start | resolve | verify | close | reject | run-rca
 * @param body    随动作变化的字段（verify 用 pass + note；resolve 用 note；reject 用 reason）
 * @returns { success, error? }
 */
export async function dqWorkOrderAction(
  id: string,
  action: string,
  body?: Record<string, unknown>,
): Promise<{ success: boolean; error?: string }> {
  try {
    await apiFetchData<unknown>(`${DQ_GOV_BASE}/work-orders/${id}/${action}`, {
      method: "POST",
      body: body ? JSON.stringify(body) : undefined,
    });
    return { success: true };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : String(e) };
  }
}

/**
 * 工单分级排行（GET /work-orders/rank?grade=P1）
 */
export async function fetchDqWorkOrderRank(grade: string): Promise<DqWorkOrderVO[]> {
  const qs = `?grade=${encodeURIComponent(grade)}`;
  try {
    const resp = await apiFetchData<unknown>(`${DQ_GOV_BASE}/work-orders/rank${qs}`);
    const arr = Array.isArray(resp) ? resp : (resp as { data?: unknown })?.data;
    return (Array.isArray(arr) ? arr : []) as DqWorkOrderVO[];
  } catch {
    return [];
  }
}

/** 创建调度（POST /schedules）— 返回新调度 ID */
export async function createDqSchedule(dto: {
  name: string;
  triggerType: string;
  cronExpression?: string;
  ruleIds?: string[];
  scopeType?: string;
  scopeId?: string;
  enabled?: boolean;
  maxRuntimeSeconds?: number;
}): Promise<{ success: boolean; id?: string; error?: string }> {
  try {
    const resp = await apiFetchData<unknown>(`${DQ_GOV_BASE}/schedules`, {
      method: "POST",
      body: JSON.stringify(dto),
    });
    const id =
      resp && typeof resp === "object"
        ? ((resp as Record<string, unknown>).id as string | undefined)
        : (resp as string | undefined);
    return { success: true, id };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : String(e) };
  }
}

/** 更新调度（PUT /schedules/{id}） */
export async function updateDqSchedule(
  id: string,
  dto: Partial<Pick<DqScheduleVO, "name" | "triggerType" | "cronExpression" | "ruleIds" | "enabled" | "maxRuntimeSeconds">>,
): Promise<{ success: boolean; error?: string }> {
  try {
    await apiFetchData<unknown>(`${DQ_GOV_BASE}/schedules/${id}`, {
      method: "PUT",
      body: JSON.stringify(dto),
    });
    return { success: true };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : String(e) };
  }
}

/** 删除调度（DELETE /schedules/{id}，逻辑删除） */
export async function deleteDqSchedule(id: string): Promise<{ success: boolean; error?: string }> {
  try {
    await apiFetchData<unknown>(`${DQ_GOV_BASE}/schedules/${id}`, { method: "DELETE" });
    return { success: true };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : String(e) };
  }
}
