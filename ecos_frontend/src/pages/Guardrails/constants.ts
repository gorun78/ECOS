import { FileLock2, Filter, Users, EyeOff, ShieldCheck } from 'lucide-react';
import type { PolicyType } from './types';
import { API_BASE as API_ROOT } from '../../services/httpClient';

// ─────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────

/**
 * 护栏策略端点基址（完整路径，供 helpers#apiCall 经单源 `rawAuthedUrlFetch` 取用）。
 * 前缀由 httpClient#API_BASE 单源派生 —— W206/C188 禁本文件再写字面前缀。
 */
export const API_BASE = `${API_ROOT}/v1/guardrails/policies`;

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
