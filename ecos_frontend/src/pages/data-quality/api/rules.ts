/**
 * PMO-48-A T5 — Data Quality rules (规则中心/6 维度/自检) API 函数层
 * (从原单文件 api.ts 拆分, 见 W66 前端 <800 行约定)
 * @license
 * SPDX-License-Identifier: Apache-2.0
 *
 *  规则列表当前数据源 = legacy 只读端点 GET /api/v1/ecos/dq/rules (fetchEcosDqRules())
 *  —— 与数据工作台「数据质量」页同一来源, 读表 ecos_dq_rule_v2 (26 条真实数据)。
 *    治理端点 GET /api/v1/dq/rules (DqGovernanceController, 表 ecos_dq.dq_rule) 当前实测 404,
 *    故规则中心的写操作 UI 已隐藏 (仅只读展示), 列表走 fetchEcosDqRules()。
 *    以下治理类 API (fetchDqGovernanceRules / create / update / delete / dqRuleAction) 全部保留,
 *    待 Phase 2 治理端点就绪后复用。
 *
 * 复用 src/api.ts 的 `apiFetchData`(自动注入 Bearer token, 解 .data, 抛 403 NoAccessError)。
 * 不复用老 fetchDqRules (指向 /api/v1/dq/dashboard 等已 405 旧路径)。
 */

import { apiFetchData } from "../../../api";
import { DQ_GOV_BASE, DQ_LEGACY_BASE, recordDqFallback } from "./_base";
import type {
  DqRuleVO,
  DqRuleDetailVO,
  DqDimensionRegistryVO,
  DqSelfCheckResult,
  DqRuleDTO,
  DqRuleActionDTO,
  DqRuleVersionVO,
  DqRuleActionType,
} from "./types";

/** legacy 规则行原始结构 — GET /api/v1/ecos/dq/rules 返回的元素 (camelCase) */
interface EcosDqRuleRawVO {
  id: string | number;
  name?: string;
  description?: string;
  ruleType?: string;
  config?: Record<string, unknown>;
  severity?: string;
  enabled?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

/**
 * R-3 ② (详细设计-02 §F02-08 规格 6): 治理端点 404 → legacy 只读端点的**非静默回落**。
 *
 * <p>V166 legacy 规则迁移完成前保留回落，但不再静默：治理端点失败/不可达时</p>
 * <ol>
 *   <li>{@link recordDqFallback} — console.warn + {@code window.__ecosFallbacks} push
 *       (遥测/测试断言钩子, 带 path/status/traceId)；</li>
 *   <li>派发 {@code dq-fallback} CustomEvent — 页面级可据此展示"兼容模式"提示
 *       (用户可见文案走 t(...), 本层不硬编码 UI 字符串)。</li>
 * </ol>
 *
 * <p>治理端点 200 但空数组 = 真实空列表, 不回落。legacy 端点自身失败依然抛错,
 * 由调用方 error 态展示。</p>
 */
export async function fetchDqRulesWithFallback(params?: {
  category?: string;
  domain?: string;
  status?: string;
  pageNum?: number;
  pageSize?: number;
}): Promise<DqRuleVO[]> {
  try {
    const govs = await fetchDqGovernanceRules(params);
    // null = apiFetchData 解包失败/未就绪 → 回落；空数组 = 真实空 → 不回落
    if (govs !== null) return govs;
    throw new Error("governance endpoint returned no data");
  } catch (e) {
    const status = e instanceof Error && /404/.test(e.message) ? 404 : undefined;
    const note =
      e instanceof Error
        ? `governance endpoint unavailable (${e.message}); legacy fallback engaged`
        : "governance endpoint unavailable; legacy fallback engaged";
    recordDqFallback(`${DQ_GOV_BASE}/rules`, note, { status });
    if (typeof window !== "undefined") {
      window.dispatchEvent(new CustomEvent("dq-fallback", { detail: { note, status } }));
    }
  }
  return fetchEcosDqRules();
}

/**
 * 规则列表(规则中心当前唯一数据源) — legacy 只读端点 GET /api/v1/ecos/dq/rules
 *
 * 与数据工作台「数据质量」页同源(读表 ecos_dq_rule_v2)。经 apiFetchData 解包后响应体为
 * { data: EcosDqRuleRawVO[], total: number }, 规则数组在 resp.data。
 * legacy 无 ruleCode / category / domain / targetKind / targetTable / targetField, 一律填空串(不编造);
 * enabled 映射为 status: true → ACTIVE / false → DISABLED。
 * 失败抛 Error(与 fetchDqGovernanceRules 一致), 由调用方 error 态 + 重试按钮处理。
 */
export async function fetchEcosDqRules(): Promise<DqRuleVO[]> {
  const resp = await apiFetchData<{ data?: EcosDqRuleRawVO[]; total?: number } | EcosDqRuleRawVO[]>(
    `${DQ_LEGACY_BASE}/rules`,
  );
  const arr = Array.isArray(resp) ? resp : resp?.data;
  if (!Array.isArray(arr)) return [];
  return arr.map((r) => ({
    id: String(r.id),
    ruleName: r.name ?? "",
    ruleCode: "",
    category: "",
    domain: "",
    ruleType: r.ruleType ?? "",
    severity: r.severity ?? "",
    targetKind: "",
    status: r.enabled ? "ACTIVE" : "DISABLED",
    version: 1,
    parametersJson: JSON.stringify(r.config ?? {}),
    createdAt: r.createdAt,
    updatedAt: r.updatedAt,
  }));
}

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
