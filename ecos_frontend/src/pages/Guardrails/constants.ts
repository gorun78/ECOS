import { FileLock2, Filter, Users, EyeOff, ShieldCheck } from 'lucide-react';
import type { PolicyType } from './types';

// ─────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────

export const API_BASE = '/api/v1/guardrails/policies';

export const POLICY_TYPE_META: Record<PolicyType, { label: string; icon: any; color: string }> = {
  column_masking: { label: '列级脱敏', icon: FileLock2, color: 'text-blue-500' },
  row_filtering: { label: '行级过滤', icon: Filter, color: 'text-amber-500' },
  pii_redaction: { label: 'PII 脱敏', icon: FileLock2, color: 'text-purple-500' },
  human_approval: { label: '人工审批', icon: Users, color: 'text-cyan-500' },
  hallucination_check: { label: '幻觉检查', icon: EyeOff, color: 'text-rose-500' },
  custom: { label: '自定义', icon: ShieldCheck, color: 'text-slate-500' },
};

export const TYPE_OPTIONS = Object.entries(POLICY_TYPE_META).map(([value, meta]) => ({
  value: value as PolicyType,
  label: meta.label,
}));
