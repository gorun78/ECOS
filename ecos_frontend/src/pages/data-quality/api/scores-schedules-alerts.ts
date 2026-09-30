/**
 * PMO-48 — Data Quality 评分 / 监控调度 / 告警中心 / 工单中心 / 报告中心 API 函数层
 * (从原单文件 api.ts 拆分, 见 W66 前端 <800 行约定)
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import { apiFetchData } from "../../../api";
import { DQ_GOV_BASE } from "./_base";
import type {
  DqAssetScoreVO,
  DqScoreTrendVO,
  DqScoreSystemVO,
  DqScheduleVO,
  DqAlertVO,
  DqWorkOrderVO,
  DqReportVO,
  DqReportQuery,
  DqReportPageResult,
  DqReportType,
} from "./types";

/* ── 评分 (PMO-48-B T8) ─────────────────────────────────────── */

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

/* ── 监控调度 (PMO-48-C T11) ─────────────────────────────────── */

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

/* ── 告警中心 (PMO-48-C T12) ─────────────────────────────────── */

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

/* ── 工单中心 (PMO-48-C T12/T13) ─────────────────────────────── */

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

/* ── DQ 报告中心 (PMO-48-D T15) ─────────────────────────────── */

/**
 * PMO-48-D T15 — DQ 报告中心 API 层
 *
 * 后端端点 (Phase 4 T15 就绪):
 *   POST   /api/v1/dq/reports/generate?scope=&type=daily|weekly|monthly   → DqReportVO
 *   GET    /api/v1/dq/reports?reportType=&scope=&pageNum=&pageSize=      → PageResult<DqReportVO>
 *   GET    /api/v1/dq/reports/{id}/html                                  → text/html (浏览器直接查看)
 *   GET    /api/v1/dq/reports/{id}/pdf                                   → 501 (Phase 5 补 PDF 插件)
 */

/** 生成报告（POST /generate?scope=&type=） */
export async function generateDqReport(
  type: DqReportType,
  scope: string,
): Promise<{ success: boolean; id?: string; error?: string }> {
  const qs = `?scope=${encodeURIComponent(scope || "ALL")}&type=${encodeURIComponent(type)}`;
  try {
    const resp = await apiFetchData<unknown>(`${DQ_GOV_BASE}/reports/generate${qs}`, { method: "POST" });
    const id = resp && typeof resp === "object"
      ? ((resp as Record<string, unknown>).id as string | undefined)
      : (resp as string | undefined);
    return { success: true, id };
  } catch (e) {
    return { success: false, error: e instanceof Error ? e.message : String(e) };
  }
}

/** 报告分页列表（reportType / scope 过滤 + pageNum / pageSize） */
export async function fetchDqReports(query?: DqReportQuery): Promise<DqReportPageResult | null> {
  const sp = new URLSearchParams();
  if (query?.reportType) sp.append("reportType", query.reportType);
  if (query?.scope) sp.append("scope", query.scope);
  if (query?.pageNum) sp.append("pageNum", String(query.pageNum));
  if (query?.pageSize) sp.append("pageSize", String(query.pageSize));
  const qs = sp.toString();
  try {
    const resp = await apiFetchData<unknown>(`${DQ_GOV_BASE}/reports${qs ? `?${qs}` : ""}`);
    if (!resp || typeof resp !== "object") return null;
    const r = resp as { data?: DqReportVO[]; total?: number; pageNum?: number; pageSize?: number };
    const data = (Array.isArray(r.data) ? r.data : []) as DqReportVO[];
    return {
      data,
      total: typeof r.total === "number" ? r.total : data.length,
      pageNum: typeof r.pageNum === "number" ? r.pageNum : 1,
      pageSize: typeof r.pageSize === "number" ? r.pageSize : data.length,
    };
  } catch {
    return null;
  }
}

/** 报告 HTML 查看 URL（新开 tab window.open） */
export function dqReportHtmlUrl(id: string): string {
  return `${DQ_GOV_BASE}/reports/${id}/html`;
}
