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
// MOCK DATA
// ==========================================


export interface PlaybookStep {
  step: number;
  title: string;
  tab: 'overview' | 'orgs' | 'dac' | 'mac' | 'pbac' | 'row_col' | 'audit';
  description: string;
  ecosContext: string;
  actionLabel: string;
}

export interface SecurityCenterViewProps {
  activeTab?: 'overview' | 'orgs' | 'dac' | 'mac' | 'pbac' | 'row_col' | 'audit' | 'guide';
  onActiveTabChange?: (tab: 'overview' | 'orgs' | 'dac' | 'mac' | 'pbac' | 'row_col' | 'audit' | 'guide') => void;
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