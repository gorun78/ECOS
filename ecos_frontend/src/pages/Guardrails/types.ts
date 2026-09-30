// ─────────────────────────────────────────────────────────────
// Types
// ─────────────────────────────────────────────────────────────

export type PolicyType =
  | 'column_masking'
  | 'row_filtering'
  | 'pii_redaction'
  | 'human_approval'
  | 'hallucination_check'
  | 'custom';

export type PolicySeverity = 'block' | 'warn';
export type PolicyStatus = 'DRAFT' | 'COMPILED';
export type MaskType = 'REDACT' | 'PARTIAL' | 'HASH';

export interface GuardrailPolicy {
  id: string;
  name: string;
  description: string;
  type: PolicyType;
  severity: PolicySeverity;
  isEnabled: boolean;
  status: PolicyStatus;
  // type-specific configuration
  table?: string;
  column?: string;
  maskType?: MaskType;
  condition?: string; // SQL WHERE predicate for row_filtering
  config?: Record<string, any>;
  compiledAt?: string;
  compileLogs?: string[];
  createdAt?: string;
  updatedAt?: string;
}

export interface PreviewData {
  raw: any[] | Record<string, any[]>;
  compiled: any[] | Record<string, any[]>;
  columns?: string[];
  table?: string;
}

export interface AuditLogEntry {
  id: string;
  timestamp: string;
  policyId: string;
  policyName: string;
  action: string;
  actor: string;
  result: 'success' | 'blocked' | 'warning';
  detail: string;
}
