/**
 * SecurityCenter Tab 组件共享 Props
 * 包含主容器中所有 Tab 可能需要的状态和回调。
 * @license Apache-2.0
 */
import type { SecurityOrg, ProjectDAC, SecurityMarking, PurposePBAC, RowColPolicy, SecurityAuditLog, PlaybookStep } from '../types';

export interface TabProps {
  // Data
  orgs: SecurityOrg[];
  setOrgs: React.Dispatch<React.SetStateAction<SecurityOrg[]>> | ((val: SecurityOrg[]) => void);
  projects: ProjectDAC[];
  setProjects: React.Dispatch<React.SetStateAction<ProjectDAC[]>> | ((val: ProjectDAC[]) => void);
  markings: SecurityMarking[];
  setMarkings: React.Dispatch<React.SetStateAction<SecurityMarking[]>> | ((val: SecurityMarking[]) => void);
  purposes: PurposePBAC[];
  setPurposes: React.Dispatch<React.SetStateAction<PurposePBAC[]>> | ((val: PurposePBAC[]) => void);
  rowColPolicies: RowColPolicy[];
  setRowColPolicies: React.Dispatch<React.SetStateAction<RowColPolicy[]>> | ((val: RowColPolicy[]) => void);
  auditLogs: SecurityAuditLog[];
  setAuditLogs: React.Dispatch<React.SetStateAction<SecurityAuditLog[]>> | ((val: SecurityAuditLog[]) => void);
  // Navigation
  activeTab: string;
  setActiveTab: (tab: any) => void;
  // Selections
  selectedProjectId: string;
  setSelectedProjectId: (v: string) => void;
  selectedMarkingId: string;
  setSelectedMarkingId: (v: string) => void;
  selectedRowColDs: string;
  setSelectedRowColDs: (v: string) => void;
  // Simulator
  simUser: string; setSimUser: (v: string) => void;
  simDataset: string; setSimDataset: (v: string) => void;
  simPurpose: string; setSimPurpose: (v: string) => void;
  simResult: { verdict: 'GRANTED' | 'DENIED'; traces: string[] } | null;
  simIp: string; setSimIp: (v: string) => void;
  handleRunSimulation: () => void;
  // Modals
  showAddOrgModal: boolean; setShowAddOrgModal: (v: boolean) => void;
  showAddMemberModal: boolean; setShowAddMemberModal: (v: boolean) => void;
  showAddMarkingModal: boolean; setShowAddMarkingModal: (v: boolean) => void;
  showAddPurposeModal: boolean; setShowAddPurposeModal: (v: boolean) => void;
  // Forms
  newOrgName: string; setNewOrgName: (v: string) => void;
  newOrgId: string; setNewOrgId: (v: string) => void;
  newOrgIp: string; setNewOrgIp: (v: string) => void;
  newOrgIsolation: boolean; setNewOrgIsolation: (v: boolean) => void;
  newMemberName: string; setNewMemberName: (v: string) => void;
  newMemberRole: 'Owner' | 'Editor' | 'Viewer'; setNewMemberRole: (v: 'Owner' | 'Editor' | 'Viewer') => void;
  newMarkId: string; setNewMarkId: (v: string) => void;
  newMarkName: string; setNewMarkName: (v: string) => void;
  newMarkLevel: string; setNewMarkLevel: (v: any) => void;
  newMarkDesc: string; setNewMarkDesc: (v: string) => void;
  newPurpId: string; setNewPurpId: (v: string) => void;
  newPurpName: string; setNewPurpName: (v: string) => void;
  newPurpDesc: string; setNewPurpDesc: (v: string) => void;
  newPurpDs: string; setNewPurpDs: (v: string) => void;
  newPurpRules: string; setNewPurpRules: (v: string) => void;
  // Handlers (宽泛签名以兼容 React.FormEvent)
  handleAddOrg: (e?: any) => void;
  handleAddMember: (e?: any) => void;
  handleAddMarking: (e?: any) => void;
  handleAddPurpose: (e?: any) => void;
  handleToggleColumnMask: (datasetId: string, colName: string) => void;
  handleToggleRowFilter: (datasetId: string, idx: number) => void;
  handleExecutePlaybookStep: (step: PlaybookStep) => void;
  openDiagnosticReport: () => void;
  // Toast
  showToast?: (type: 'success' | 'info' | 'error', msg: string) => void;
}
