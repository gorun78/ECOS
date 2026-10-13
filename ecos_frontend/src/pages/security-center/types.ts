/**
 * Security Center — type definitions
 * @license SPDX-License-Identifier: Apache-2.0
 */

// ==========================================
// TYPES DEFINITIONS
// ==========================================

export interface SecurityOrg {
  id: string;
  name: string;
  isolationMode: boolean;
  memberCount: number;
  ipRanges: string[];
  crossOrgSharing: string[];
  createdAt: string;
}

export interface ProjectDAC {
  id: string;
  name: string;
  description: string;
  members: {
    username: string;
    role: 'Owner' | 'Editor' | 'Viewer' | 'Discoverer';
    grantedBy: string;
    grantedAt: string;
  }[];
  discoverableAllOrgs: boolean;
  autoPropagation: boolean;
}

export interface SecurityMarking {
  id: string;
  displayName: string;
  apiName: string;
  classificationLevel: 'CONFIDENTIAL' | 'SECRET' | 'TOP_SECRET' | 'RESTRICTED' | 'PUBLIC';
  grantedGroups: string[];
  appliedDatasets: string[];
  description: string;
}

export interface PurposePBAC {
  id: string;
  name: string;
  description: string;
  authorizedUsers: string[];
  inputDatasets: string[];
  redactionRules: string[];
  expiresAt: string;
  status: 'ACTIVE' | 'EXPIRED' | 'PENDING';
}

export interface RowColPolicy {
  datasetId: string;
  datasetName: string;
  columnMasks: {
    column: string;
    maskType: 'SHA256' | 'REDACT' | 'PARTIAL' | 'NONE';
    active: boolean;
  }[];
  rowFilters: {
    filterSql: string;
    description: string;
    active: boolean;
  }[];
}

export interface SecurityAuditLog {
  id: string;
  timestamp: string;
  username: string;
  orgId: string;
  resourceId: string;
  resourceType: string;
  action: string;
  status: 'SUCCESS' | 'DENIED' | 'WARN';
  details: string;
}

// ==========================================
// COMPONENT CONTRACTS（无 mock 数据 — 2026-10-13 报告 §4.7 行308 订正：
// 原「MOCK DATA」章节头下早已无任何数据导出，属残留注释，予以更正）
// ==========================================

/**
 * 安全中心 Tab id — 单源 = SecurityCenterLayout.tsx 的 SecurityTab
 * （prevent/detect/policies/audit/audit-query，?tab= 查询参数承接）。
 */
export type SecurityCenterTabId = 'prevent' | 'detect' | 'policies' | 'audit' | 'audit-query';

export interface PlaybookStep {
  step: number;
  title: string;
  /** 跳转目标 Tab（与 SecurityCenterLayout 现 tab 集对齐） */
  tab: SecurityCenterTabId;
  description: string;
  ecosContext: string;
  actionLabel: string;
}

export interface SecurityCenterViewProps {
  activeTab?: SecurityCenterTabId;
  onActiveTabChange?: (tab: SecurityCenterTabId) => void;
  orgs?: SecurityOrg[];
  setOrgs?: React.Dispatch<React.SetStateAction<SecurityOrg[]>> | ((val: SecurityOrg[]) => void);
  projects?: ProjectDAC[];
  setProjects?: React.Dispatch<React.SetStateAction<ProjectDAC[]>> | ((val: ProjectDAC[]) => void);
  markings?: SecurityMarking[];
  setMarkings?: React.Dispatch<React.SetStateAction<SecurityMarking[]>> | ((val: SecurityMarking[]) => void);
  purposes?: PurposePBAC[];
  setPurposes?: React.Dispatch<React.SetStateAction<PurposePBAC[]>> | ((val: PurposePBAC[]) => void);
  rowColPolicies?: RowColPolicy[];
  setRowColPolicies?: React.Dispatch<React.SetStateAction<RowColPolicy[]>> | ((val: RowColPolicy[]) => void);
  auditLogs?: SecurityAuditLog[];
  setAuditLogs?: React.Dispatch<React.SetStateAction<SecurityAuditLog[]>> | ((val: SecurityAuditLog[]) => void);
  simUser?: string;
  setSimUser?: (val: string) => void;
  simDataset?: string;
  setSimDataset?: (val: string) => void;
  simPurpose?: string;
  setSimPurpose?: (val: string) => void;
  simResult?: { verdict: 'GRANTED' | 'DENIED'; traces: string[] } | null;
  setSimResult?: (val: { verdict: 'GRANTED' | 'DENIED'; traces: string[] } | null) => void;
  selectedRowColDs?: string;
  setSelectedRowColDs?: (val: string) => void;
  showToast?: (type: 'success' | 'info' | 'error', message: string) => void;
}