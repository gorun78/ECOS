import { FileLock2, Filter, Users, EyeOff, ShieldCheck } from 'lucide-react';
import type { PolicyType } from './types';

// ─────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────

/**
 * 护栏策略端点基址（完整路径，供 helpers#apiCall 经单源 `rawAuthedUrlFetch` 取用）。
 * #145/B6-11 起族根上收至 `services/cognitiveEngineApi.ts` 的 `GUARDRAILS_POLICIES_BASE` 单源，
 * 本行只是**别名重导出**（不含 URL 体 ⇒ 不构成第二个族根构造方，#145/B6-5 ② 判例）。
 * 改前两处形状对本仓基址单源判据不可见：`API_BASE as API_ROOT` 的导入别名让族根派生态写成
 * `${API_ROOT}/v1/…`，而所有派生态判据按 token 名 `API_BASE` 匹配（D-B5-40 的名字变体腿）；
 * 加上本文件把常量**也叫** `API_BASE`，取值却是 `/api/v1/guardrails/policies`。
 */
export { GUARDRAILS_POLICIES_BASE } from '../../services/cognitiveEngineApi';

export const POLICY_TYPE_META: Record<PolicyType, { labelKey: string; icon: any; color: string }> = {
  column_masking: { labelKey: 'gw.type.column_masking', icon: FileLock2, color: 'text-blue-500' },
  row_filtering: { labelKey: 'gw.type.row_filtering', icon: Filter, color: 'text-amber-500' },
  pii_redaction: { labelKey: 'gw.type.pii_redaction', icon: FileLock2, color: 'text-purple-500' },
  human_approval: { labelKey: 'gw.type.human_approval', icon: Users, color: 'text-cyan-500' },
  hallucination_check: { labelKey: 'gw.type.hallucination_check', icon: EyeOff, color: 'text-rose-500' },
  custom: { labelKey: 'gw.type.custom', icon: ShieldCheck, color: 'text-slate-500' },
};

export const TYPE_OPTIONS = Object.entries(POLICY_TYPE_META).map(([value, meta]) => ({
  value: value as PolicyType,
  labelKey: meta.labelKey,
}));
