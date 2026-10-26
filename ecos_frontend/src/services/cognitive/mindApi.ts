/**
 * cognitive/mindApi — R4 Mind 聚合根读面 + 候选更新闸门（差距分析 P0-6 /
 * 详设 §8.6.1 line 5685 / #/decision/mind 列表+详情+候选区的数据面）。
 *
 * <p>自 {@link ../cognitiveClient.ts} 单源出口（fetch/auth 走唯一 {@link callJson}
 * 通道，禁本模块自拼 Bearer 头；路径基址从 {@link COGNITIVE_BASE} 派生，
 * 全仓无第二处字面量）。本文件守 ≤800 行门禁（前端开发规范 §十）。
 *
 * <p><b>路径契约（后端 MindUpdateGatewayController 单源，实测核查）</b>：
 * <pre>
 * GET  /api/v1/cognitive/minds                             — 列表（?limit=1~200，默认 50）
 * GET  /api/v1/cognitive/minds/{mindId}                    — 详情（六段投影 + 三轴 + 版本字段）
 * POST /api/v1/cognitive/minds/{mindId}/proposals          — 提交候选（恒落 PROPOSED）
 * GET  /api/v1/cognitive/minds/proposals?mindId=&amp;limit=    — 按 Mind 列候选（倒序，默认 20 上限 100）
 * GET  /api/v1/cognitive/minds/proposals/{updateId}        — 回读单行（400 mind 缺失由提交腿承载）
 * PUT  /api/v1/cognitive/minds/proposals/{updateId}/status — 受控置终态（唯一终态写入口; 已终态 409）
 * </pre>
 * 前 3 条自带 mindId 路径键；后 3 条 updateId 载体不嵌 mindId（回路鉴别由闸门行 mind_id 列承载）。
 *
 * <p>真值纪律：三轴（validity/review/adoption）由后端 {@code MindStatusAxes} 词表单源
 * 下发；前端只做 i18n 显示映射，不复制枚举字面。V292 未迁移库上
 * {@code r4ColumnsProvisioned=false} + 三轴全 null，UI 走「未迁移」横幅，禁折叠成假值；
 * 六段·认知上下文五列（subject_ref/scope/context_ref/goals_json/key_questions_json）
 * 与本批读面端点解耦（端点不读该五列 ⇒ 前端也不读，待后续批扩充端点后接入，禁本地合成）。
 *
 * <p>D-B3-1 承流挂账：ACCEPTED 后同步写正式资产新版本行（superseded_by 链）本批未实现；
 * 前端只按 status 字段渲染闸门行三态（PROPOSED/ACCEPTED/REJECTED），
 * 禁以本地合成「正式资产已更新」假推力。</p>
 *
 * @license Apache-2.0
 */

import { asArray, callJson, COGNITIVE_BASE } from "../cognitiveClient";

// ── 路径基址单源（禁散落实测 API 字面）─────────────────────────

/** 列表基址（`/api/v1/cognitive/minds`，对齐 CognitiveApiPaths.MINDS_REL）。 */
export const MIND_LIST_PATH = `${COGNITIVE_BASE}/minds`;

/** Mind 详情路径构造（mindId URL 编码，防路径注入）。 */
export function mindDetailPath(mindId: string): string {
  return `${MIND_LIST_PATH}/${encodeURIComponent(mindId)}`;
}

/** 候选列表（query 携 mindId + limit [1,100]，后端夹取兜底）。 */
export function mindProposalsPath(mindId: string, limit?: number): string {
  const q = new URLSearchParams({ mindId });
  const lim = Math.max(1, Math.min(100, Math.floor(limit ?? 20)));
  q.set("limit", String(lim));
  return `${MIND_LIST_PATH}/proposals?${q.toString()}`;
}

/** 候选择单回读（updateId URL 编码；未找到/已软删 → 404，单源通道归一）。 */
export function mindProposalPath(updateId: string): string {
  return `${MIND_LIST_PATH}/proposals/${encodeURIComponent(updateId)}`;
}

/** 提交候选（POST，只由 Agent 或受控人工走只写 PROPOSED，禁前端本地伪造终态）。 */
export function mindProposalSubmitPath(mindId: string): string {
  return `${mindDetailPath(mindId)}/proposals`;
}

/** 终态置入（PUT，唯一写 ACCEPTED/REJECTED 入口；reviewReason 四要件必填，后端校验）。 */
export function mindProposalFinalizePath(updateId: string): string {
  return `${mindProposalPath(updateId)}/status`;
}

// ── 契约形状（服务端单源字面，前端不复制枚举）──────────────────

/** 三轴状态值（R4 MindStatusAxes name() 投影；具体值由服务端下发，前端只透传不判字面）。 */
export interface MindAxes {
  validityStatus: string | null;
  reviewStatus: string | null;
  adoptionStatus: string | null;
}

/** Mind 行（列表态瘦投影 + 三轴可空；禁止前端补零/隐私位假值）。 */
export interface MindRow extends MindAxes {
  mindId: string;
  mindKey?: string;
  name?: string;
  description?: string | null;
  isActive?: boolean;
  [key: string]: unknown;
}

/** Mind 列表响应壳：r4ColumnsProvisioned=false 时三轴全 null（禁折叠）。 */
export interface MindListResponse {
  r4ColumnsProvisioned: boolean;
  items: MindRow[];
}

/** Mind 详情（对齐 MindAggregateService.mindView 投影；六段·认知上下文五列未接线端点，不在此形状）。 */
export interface MindDetail extends MindAxes {
  mindId: string;
  mindKey?: string;
  name?: string;
  description?: string | null;
  capabilityMask?: unknown;
  closedLoopBounds?: unknown;
  /** 版本历史段（V292 加列）：change_reason / superseded_by 分开承载。 */
  changeReason?: string | null;
  supersededBy?: string | null;
  /** 三轴列未落库 = false，UI 走「R4 未迁移」横幅，禁以 DRAFT/PENDING_REVIEW 假值坐落。 */
  r4ColumnsProvisioned: boolean;
}

/** 候选更新闸门行（Gate 表投影，字段与 MindUpdateVO 逐字对齐，服务端权威）。 */
export interface MindUpdateRow {
  id: string;
  mindId?: string;
  agentRunId?: string;
  /** BELIEF | HYPOTHESIS | CONTEXT | EXPLANATION（词表由后端 MindUpdateVO 单源，前端只透传）。 */
  targetType?: string;
  targetId?: string | null;
  changeJson?: string;
  evidenceRefsJson?: string;
  /** PROPOSED | ACCEPTED | REJECTED（写路径唯一 = 本闸门，Agent 只能 PROPOSED）。 */
  status?: string;
  reviewedBy?: string | null;
  reviewedAt?: string | null;
  reviewReason?: string | null;
  traceId?: string;
  createTime?: string;
  [key: string]: unknown;
}

/** 提交候选请求体（四要件：agentRunId / targetType / changeJson / mind 存在 → 后端校验）。 */
export interface MindProposalSubmitInput {
  agentRunId: string;
  targetType: string;
  targetId?: string | null;
  changeJson: string;
  evidenceRefsJson?: string;
  operator?: string;
}

/** 终态请求体（ACCEPTED / REJECTED 二选一；复核原因必填四要件，后端 MD-CHK 校验）。 */
export interface MindFinalizeInput {
  status: "ACCEPTED" | "REJECTED";
  reviewReason: string;
  operator?: string;
}

// ── 端点函数 ─────────────────────────────────────────────────────

/** Mind 列表（?limit=[1,200]，后端夹取兜底；返回体外壳 = { r4ColumnsProvisioned, items }）。 */
export async function fetchMindList(limit?: number): Promise<MindListResponse> {
  const q = new URLSearchParams();
  if (typeof limit === "number" && Number.isFinite(limit)) {
    q.set("limit", String(Math.max(1, Math.min(200, Math.floor(limit)))));
  }
  const qs = q.toString();
  const data = await callJson(qs ? `${MIND_LIST_PATH}?${qs}` : MIND_LIST_PATH);
  const o = (data && typeof data === "object" ? data : {}) as Record<string, unknown>;
  return {
    r4ColumnsProvisioned: o.r4ColumnsProvisioned === true,
    items: asArray<MindRow>(o.items),
  };
}

/** Mind 六段详情（V292 三轴未落库时与版本字段全 null，UI 走未迁移横幅，禁假值；
 *  mind 缺失/已软删 → 400 由单源通道归一为 CognitiveApiError(BAD_REQUEST)）。 */
export async function fetchMindDetail(mindId: string): Promise<MindDetail> {
  const data = await callJson(mindDetailPath(mindId));
  const o = (data && typeof data === "object" ? data : {}) as Record<string, unknown>;
  return {
    mindId: String(o.mindId ?? mindId),
    mindKey: typeof o.mindKey === "string" ? o.mindKey : undefined,
    name: typeof o.name === "string" ? o.name : undefined,
    description: typeof o.description === "string" ? o.description : null,
    capabilityMask: o.capabilityMask as MindDetail["capabilityMask"],
    closedLoopBounds: o.closedLoopBounds as MindDetail["closedLoopBounds"],
    validityStatus: (o.validityStatus as string | null) ?? null,
    reviewStatus: (o.reviewStatus as string | null) ?? null,
    adoptionStatus: (o.adoptionStatus as string | null) ?? null,
    changeReason: typeof o.changeReason === "string" ? o.changeReason : null,
    supersededBy: typeof o.supersededBy === "string" ? o.supersededBy : null,
    r4ColumnsProvisioned: o.r4ColumnsProvisioned === true,
  };
}

/** Mind 候选列表（按 create_time 倒序，limit [1,100]，后端默认 20 / 夹取；mind 缺失 → 400 单源通道归一）。 */
export async function fetchMindProposals(mindId: string, limit?: number): Promise<MindUpdateRow[]> {
  const data = await callJson(mindProposalsPath(mindId, limit));
  return asArray<MindUpdateRow>(data);
}

/** 候选择单回读（未找到 / 已软删 → 抛 CognitiveApiError(NOT_FOUND)，由单源通道归一）。 */
export async function fetchMindProposal(updateId: string): Promise<MindUpdateRow> {
  const data = await callJson(mindProposalPath(updateId));
  return (data ?? {}) as MindUpdateRow;
}

/** 提交候选（Agent 提交通道等价只写 PROPOSED；mind 不存在 / 参数不齐 → 400，单源通道归一）。 */
export async function submitMindProposal(
  mindId: string,
  input: MindProposalSubmitInput,
): Promise<MindUpdateRow> {
  const data = await callJson(mindProposalSubmitPath(mindId), {
    method: "POST",
    body: JSON.stringify(input),
  });
  return (data ?? {}) as MindUpdateRow;
}

/** 受控置终态（ACCEPTED / REJECTED 唯一入口；已终态 → 409 归一为 CognitiveApiError，status=409 供 UI 分支）。 */
export async function finalizeMindProposal(
  updateId: string,
  input: MindFinalizeInput,
): Promise<MindUpdateRow> {
  const data = await callJson(mindProposalFinalizePath(updateId), {
    method: "PUT",
    body: JSON.stringify(input),
  });
  return (data ?? {}) as MindUpdateRow;
}
