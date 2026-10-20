/**
 * 本体提案 API（ontologyApi 拆分产物 · W197/R-56 C 批）
 *
 * 域：Ontology Proposal（T2.4）—— 候选/提案的评审链路由（submit/approve/reject/verify/execute）。
 * 端点前缀：/api/v1/ontology/proposals。
 * 对外唯一导入入口仍是 `src/services/ontologyApi.ts`（纯 re-export 兼容入口）。
 */

import { apiFetchData } from "../../api";
import { API_BASE } from "../httpClient";
import type {
  Proposal,
  ProposalStatus,
  CreateProposalDTO,
  UpdateProposalDTO,
  VerifyProposalResult,
  ReviewProposalDTO,
} from "../../types/ontology";

// ================================================================
// 本体提案 (Ontology Proposal) — T2.4
// ================================================================

/** `/v1/ontology/proposals` 族基址单源（#145/B6-4）：全仓构造该族根的唯一书写点，消费者一律 import。 */
export const ONTOLOGY_PROPOSALS_BASE = `${API_BASE}/v1/ontology/proposals`;

/**
 * 提案状态归一化（API 边界适配器）
 *
 * 后端本体提案域状态存在大小写混写：execute 写 "executed"、verify 写 "verified"，
 * 而 approve 等走大写 "APPROVED"/"EXECUTED"。此处统一收敛为契约规范值（大写），
 * 使 TS 声明 ProposalStatus 与运行期真实数据一致。
 *
 * @param raw 后端返回的原始状态字符串
 * @returns 归一化后的大写状态
 */
export function normalizeProposalStatus(raw: string): ProposalStatus {
  return String(raw).trim().toUpperCase() as ProposalStatus;
}

/**
 * 后端提案 VO 的宽松形态。
 * 后端 `OntologyProposalVO` 直出列为 author / targetEntity / proposalType / reviewerComment，
 * 而 title / description / changeType 内嵌在 payload JSONB 中，与前端 Proposal 声明不同名。
 */
type RawProposalVO = Partial<Proposal> & {
  author?: string;
  targetEntity?: string;
  proposalType?: string;
  reviewerComment?: string;
  payload?: Record<string, any>;
};

/** proposal_type → changeType 反向推断（payload 未携带 changeType 时的兜底） */
const PROPOSAL_TYPE_TO_CHANGE: Record<string, 'CREATE' | 'UPDATE' | 'DELETE'> = {
  CREATE_ENTITY: 'CREATE',
  ADD_PROPERTY: 'CREATE',
  ADD_RELATIONSHIP: 'CREATE',
  UPDATE_ENTITY: 'UPDATE',
  MODIFY_PROPERTY: 'UPDATE',
  DELETE_PROPERTY: 'DELETE',
};

/**
 * 归一化提案对象（API 边界适配器）
 *
 * 后端 VO 与前端 Proposal 的字段错位收敛点：
 * - status           ← 大小写混写 → 统一大写
 * - title/description ← payload JSONB 内嵌
 * - changeType       ← payload 内嵌，缺失时按 proposalType 推断
 * - proposedBy       ← author
 * - targetType/targetId ← targetEntity
 * - reviewComment    ← reviewerComment
 */
function normalizeProposal(proposal: RawProposalVO): Proposal {
  const payload = proposal.payload && typeof proposal.payload === 'object' ? proposal.payload : {};
  const proposalType = String(proposal.proposalType || '').trim();
  return {
    ...(proposal as Proposal),
    status: normalizeProposalStatus(String(proposal.status || '')),
    title: proposal.title || payload.title || proposalType,
    description: proposal.description || payload.description || '',
    changeType: proposal.changeType
      || payload.changeType
      || PROPOSAL_TYPE_TO_CHANGE[proposalType.toUpperCase()]
      || 'UPDATE',
    proposedBy: proposal.proposedBy || proposal.author || '',
    targetType: proposal.targetType || proposal.targetEntity || '',
    targetId: proposal.targetId || proposal.targetEntity || '',
    reviewComment: proposal.reviewComment || proposal.reviewerComment || '',
    payload,
  };
}

export async function fetchProposals(params?: { status?: string; targetType?: string }) {
  const qs = new URLSearchParams();
  if (params?.status) qs.set("status", params.status);
  if (params?.targetType) qs.set("targetType", params.targetType);
  const query = qs.toString() ? `?${qs.toString()}` : "";
  const proposals = await apiFetchData<Proposal[]>(`${ONTOLOGY_PROPOSALS_BASE}${query}`);
  return proposals.map(normalizeProposal);
}

export async function fetchProposal(id: string) {
  return normalizeProposal(await apiFetchData<Proposal>(`${ONTOLOGY_PROPOSALS_BASE}/${id}`));
}

export async function createProposal(data: CreateProposalDTO) {
  return normalizeProposal(
    await apiFetchData<Proposal>(ONTOLOGY_PROPOSALS_BASE, {
      method: "POST",
      body: JSON.stringify(data),
    })
  );
}

export async function updateProposal(id: string, data: UpdateProposalDTO) {
  return normalizeProposal(
    await apiFetchData<Proposal>(`${ONTOLOGY_PROPOSALS_BASE}/${id}`, {
      method: "PUT",
      body: JSON.stringify(data),
    })
  );
}

export async function deleteProposal(id: string) {
  await apiFetchData(`${ONTOLOGY_PROPOSALS_BASE}/${id}`, { method: "DELETE" });
}

export async function submitProposal(id: string) {
  return normalizeProposal(await apiFetchData<Proposal>(`${ONTOLOGY_PROPOSALS_BASE}/${id}/submit`, { method: "POST" }));
}

export async function approveProposal(id: string, body: ReviewProposalDTO = {}) {
  return normalizeProposal(
    await apiFetchData<Proposal>(`${ONTOLOGY_PROPOSALS_BASE}/${id}/approve`, {
      method: "POST",
      body: JSON.stringify(body),
    })
  );
}

export async function rejectProposal(id: string, body: ReviewProposalDTO = {}) {
  return normalizeProposal(
    await apiFetchData<Proposal>(`${ONTOLOGY_PROPOSALS_BASE}/${id}/reject`, {
      method: "POST",
      body: JSON.stringify(body),
    })
  );
}

export async function verifyProposal(id: string): Promise<VerifyProposalResult> {
  const result = await apiFetchData<VerifyProposalResult>(`${ONTOLOGY_PROPOSALS_BASE}/${id}/verify`, { method: "POST" });
  return { ...result, proposal: normalizeProposal(result.proposal) };
}

export async function executeProposal(id: string) {
  return normalizeProposal(await apiFetchData<Proposal>(`${ONTOLOGY_PROPOSALS_BASE}/${id}/execute`, { method: "POST" }));
}
