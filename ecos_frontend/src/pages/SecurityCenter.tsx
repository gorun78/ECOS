/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect } from 'react';
import {
  ResponsiveContainer,
  PieChart,
  Pie,
  Cell,
  LineChart,
  Line,
  XAxis,
  YAxis,
  Tooltip as ChartTooltip,
  Legend,
  BarChart,
  Bar
} from 'recharts';
import { Activity, ArrowRight, BookOpen, CheckSquare, ChevronRight, ClipboardList, Cpu, Database, Download, EyeOff, Filter, Flame, FolderGit, Globe, Info, Lock, PieChart as PieChartIcon, Play, Plus, RefreshCw, Settings, Shield, ShieldAlert, ShieldCheck, Tag, TrendingUp, UserPlus, Users, Workflow, X, Zap, HelpCircle } from 'lucide-react';

/** Dynamic icon renderer — replaces ceos_new LucideIcon wrapper */
function LucideIcon({ name, className = '', size = 16 }: { name: string; className?: string; size?: number }) {
  const icons: Record<string, any> = { Activity, ArrowRight, BookOpen, CheckSquare, ChevronRight, ClipboardList, Cpu, Database, Download, EyeOff, Filter, Flame, FolderGit, Globe, Info, Lock, PieChart: PieChartIcon, Play, Plus, RefreshCw, Settings, Shield, ShieldAlert, ShieldCheck, Tag, TrendingUp, UserPlus, Users, Workflow, X, Zap };
  const IconComponent = icons[name] || HelpCircle;
  return <IconComponent className={className} size={size} />;
}

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

export const mockSecurityOrgs: SecurityOrg[] = [
  {
    id: 'org_aviation_hq',
    name: '航空集团总部 (Aviation Corp HQ)',
    isolationMode: true,
    memberCount: 340,
    ipRanges: ['10.120.0.0/16', '192.168.1.0/24'],
    crossOrgSharing: ['org_logistics_p'],
    createdAt: '2024-01-10'
  },
  {
    id: 'org_logistics_p',
    name: '区域物流合作伙伴 (Regional Logistics)',
    isolationMode: false,
    memberCount: 85,
    ipRanges: ['172.16.45.0/24'],
    crossOrgSharing: ['org_aviation_hq'],
    createdAt: '2024-02-15'
  },
  {
    id: 'org_contractor',
    name: '第三方维修承包商 (Maintenance Contractor)',
    isolationMode: true,
    memberCount: 42,
    ipRanges: ['202.96.128.0/24'],
    crossOrgSharing: [],
    createdAt: '2024-05-20'
  }
];

export const mockProjectDACs: ProjectDAC[] = [
  {
    id: 'proj_aviation_core',
    name: 'Aviation Core Ontology (航空核心本体项目)',
    description: '航空产业的核心概念、机队、航班及人资数据的本体层架构。',
    members: [
      { username: 'admin_guorong', role: 'Owner', grantedBy: 'System', grantedAt: '2024-01-12' },
      { username: 'analyst_li', role: 'Editor', grantedBy: 'admin_guorong', grantedAt: '2024-01-15' },
      { username: 'operator_zhang', role: 'Viewer', grantedBy: 'admin_guorong', grantedAt: '2024-01-18' },
      { username: 'external_auditor', role: 'Discoverer', grantedBy: 'admin_guorong', grantedAt: '2024-03-01' }
    ],
    discoverableAllOrgs: false,
    autoPropagation: true
  },
  {
    id: 'proj_flight_analytics',
    name: 'Flight Operations Analytics (航班运行分析项目)',
    description: '全航线机队油耗、时刻延误及动态路线计算的高级决策分析空间。',
    members: [
      { username: 'analyst_li', role: 'Owner', grantedBy: 'admin_guorong', grantedAt: '2024-02-05' },
      { username: 'operator_zhang', role: 'Editor', grantedBy: 'analyst_li', grantedAt: '2024-02-10' },
      { username: 'logistic_viewer', role: 'Viewer', grantedBy: 'analyst_li', grantedAt: '2024-02-12' }
    ],
    discoverableAllOrgs: true,
    autoPropagation: true
  },
  {
    id: 'proj_pilot_credentials',
    name: 'Pilot Credentials & Training (飞行员资质与培训)',
    description: '包含飞行员执照有效期、飞行时长学分、特情模拟训练等高度敏感的隐私数据项目。',
    members: [
      { username: 'admin_guorong', role: 'Owner', grantedBy: 'System', grantedAt: '2024-01-12' },
      { username: 'hr_manager', role: 'Editor', grantedBy: 'admin_guorong', grantedAt: '2024-01-20' }
    ],
    discoverableAllOrgs: false,
    autoPropagation: false
  }
];

export const mockSecurityMarkings: SecurityMarking[] = [
  {
    id: 'M_SENSITIVE_REVENUE',
    displayName: 'P_SENSITIVE_REVENUE (敏感收入与财务)',
    apiName: 'p_sensitive_revenue',
    classificationLevel: 'SECRET',
    grantedGroups: ['finance_exec_grp', 'global_auditors_grp'],
    appliedDatasets: ['ds_ticket_sales', 'ds_fleet_costs'],
    description: '任何派生、联结此标识的下游计算资产将强制继承，必须授予财务决策组才能读取解密。'
  },
  {
    id: 'M_MILITARY_FLIGHT',
    displayName: 'MILITARY_FLIGHT_DATA (特许军用航路机密)',
    apiName: 'military_flight_data',
    classificationLevel: 'TOP_SECRET',
    grantedGroups: ['military_ops_grp'],
    appliedDatasets: ['ds_special_routes'],
    description: '包含特殊军用或空管管制空域的保密飞行轨迹。必须由特遣办公室特别批准授予。'
  },
  {
    id: 'M_PILOT_CONFIDENTIAL',
    displayName: 'PILOT_CO_INFO (机组人资核心隐私)',
    apiName: 'pilot_co_info',
    classificationLevel: 'CONFIDENTIAL',
    grantedGroups: ['hr_directors_grp', 'pilot_union_trustee'],
    appliedDatasets: ['ds_pilots_biography'],
    description: '飞行员身份证件、心理状态评估、体能指标与家庭详细背景。'
  },
  {
    id: 'M_GDPR_PII',
    displayName: 'GDPR_PII (欧盟客户个人敏感身份)',
    apiName: 'gdpr_pii',
    classificationLevel: 'RESTRICTED',
    grantedGroups: ['data_protection_officer', 'customer_care_vip'],
    appliedDatasets: ['ds_passenger_manifest'],
    description: '遵守欧盟通用数据保护条例。包含客户护照号码、电子邮件、座位偏好及信用卡账单地址。'
  }
];

export const mockPurposes: PurposePBAC[] = [
  {
    id: 'purpose_fleet_opt_2026',
    name: '2026机队效率优化分析 (Fleet Optimization)',
    description: '仅准许由于降低燃油消耗及航线优化目标之用途，不得使用任何乘客隐私个人信息。',
    authorizedUsers: ['analyst_li', 'operator_zhang'],
    inputDatasets: ['ds_flight_schedules', 'ds_fleet_costs'],
    redactionRules: ['MASK(customer_name)', 'DROP(passport_no)'],
    expiresAt: '2026-12-31',
    status: 'ACTIVE'
  },
  {
    id: 'purpose_pilot_health_audit',
    name: '机组健康适航年度评估 (Pilot Health Evaluation)',
    description: '旨在评估现役飞行员的生理健康、模拟机复训是否达标，完成年度适航合规。',
    authorizedUsers: ['hr_manager', 'medical_reviewer_grp'],
    inputDatasets: ['ds_pilots_biography', 'ds_sim_logs'],
    redactionRules: ['NONE'],
    expiresAt: '2026-08-15',
    status: 'ACTIVE'
  },
  {
    id: 'purpose_expired_finance',
    name: '2024年度财务审计归档 (2024 Audit Archival)',
    description: '用于2024财年财务核销，已于过期后丧失自动解密密钥。',
    authorizedUsers: ['external_auditor'],
    inputDatasets: ['ds_ticket_sales'],
    redactionRules: ['REDISTRIBUTE_MASK_90'],
    expiresAt: '2025-05-01',
    status: 'EXPIRED'
  }
];

export const mockRowColPolicies: RowColPolicy[] = [
  {
    datasetId: 'ds_flight_schedules',
    datasetName: 'flights_schedule_source (航班时刻源表)',
    columnMasks: [
      { column: 'flight_num', maskType: 'NONE', active: true },
      { column: 'captain_id', maskType: 'SHA256', active: true },
      { column: 'delay_minutes', maskType: 'NONE', active: true }
    ],
    rowFilters: [
      { filterSql: "org_id = current_user.org_id", description: "仅查看当前用户归属组织的航班记录", active: true }
    ]
  },
  {
    datasetId: 'ds_pilots_biography',
    datasetName: 'pilots_biography_encrypted (飞行员隐私档案)',
    columnMasks: [
      { column: 'ssn_number', maskType: 'REDACT', active: true },
      { column: 'email_address', maskType: 'PARTIAL', active: true },
      { column: 'base_salary', maskType: 'REDACT', active: true }
    ],
    rowFilters: [
      { filterSql: "flight_hours_ytd > 300 OR role = 'HR_DIRECTOR'", description: "非HR仅显示本年累计飞行满300小时的资深教官", active: true }
    ]
  },
  {
    datasetId: 'ds_ticket_sales',
    datasetName: 'ticket_sales_raw (客票交易明细)',
    columnMasks: [
      { column: 'card_number', maskType: 'REDACT', active: true },
      { column: 'customer_name', maskType: 'PARTIAL', active: true },
      { column: 'total_amount', maskType: 'NONE', active: true }
    ],
    rowFilters: []
  }
];

export const initialAuditLogs: SecurityAuditLog[] = [
  { id: 'log_001', timestamp: '14:32:01', username: 'analyst_li', orgId: 'org_aviation_hq', resourceId: 'ds_flight_schedules', resourceType: 'Dataset', action: 'READ_DATASET', status: 'SUCCESS', details: '通过基于用途「2026机队效率优化分析」安全读取3490行数据' },
  { id: 'log_002', timestamp: '14:35:12', username: 'external_auditor', orgId: 'org_contractor', resourceId: 'ds_ticket_sales', resourceType: 'Dataset', action: 'READ_DATASET', status: 'DENIED', details: '访问遭拒：缺少标记 M_SENSITIVE_REVENUE 且基于用途授权已过期。' },
  { id: 'log_003', timestamp: '14:38:44', username: 'hr_manager', orgId: 'org_aviation_hq', resourceId: 'ds_pilots_biography', resourceType: 'Dataset', action: 'READ_DATASET', status: 'SUCCESS', details: '列脱敏(ssn_number->已遮蔽; email_address->部分脱敏)及行过滤成功应用。' },
  { id: 'log_004', timestamp: '14:41:20', username: 'system_daemon', orgId: 'org_aviation_hq', resourceId: 'M_PILOT_CONFIDENTIAL', resourceType: 'SecurityMarking', action: 'GRANT_MARKING', status: 'SUCCESS', details: '将飞行员机密标记 PILOT_CO_INFO 自动向下游「机队综合指数视图」进行沿袭标记传播。' },
  { id: 'log_005', timestamp: '14:45:00', username: 'contractor_eng', orgId: 'org_contractor', resourceId: 'proj_pilot_credentials', resourceType: 'Project', action: 'DISCOVER_PROJECT', status: 'DENIED', details: '拦截尝试：第三方承包商成员尝试搜索保密项目「飞行员资质与培训」。' }
];

// ==========================================
// PLAYBOOK INTERACTIVE TUTORIAL CONFIG
// ==========================================

export interface PlaybookStep {
  step: number;
  title: string;
  tab: 'overview' | 'orgs' | 'dac' | 'mac' | 'pbac' | 'row_col' | 'audit';
  description: string;
  ecosContext: string;
  actionLabel: string;
}

const gdprSteps: PlaybookStep[] = [
  {
    step: 1,
    title: '组织物理级隔离 (Organizations)',
    tab: 'orgs',
    description: '作为 ECOS 最外层的安全边界，首先建立欧盟运营分部组织。此边界是严密物理隔离，非特许共享的跨组织访问将被硬件阻断。',
    ecosContext: '【官方设计】ECOS Organization 用于在多租户/多分支机构中实现严格数据壁垒，是零信任的最底层基石。',
    actionLabel: '一键建立欧盟组织边界 (Org_EU_Ops)'
  },
  {
    step: 2,
    title: '自主访问控制授权 (Project DAC)',
    tab: 'dac',
    description: '在受控项目中授予欧盟旅客数据。我们将项目的可见性设置为非跨组织共享，且只授予 EU_DPO (欧盟数据合规官) 拥有 Owner 角色权限。',
    ecosContext: '【官方设计】DAC (Discretionary Access Control) 为项目提供细粒度的 ACL（Owner, Editor, Viewer, Discoverer）控制。',
    actionLabel: '一键授权欧盟项目 (proj_passenger_eu)'
  },
  {
    step: 3,
    title: '强制标记与下游级联 (MAC Markings)',
    tab: 'mac',
    description: '向核心旅客表 ds_passenger_manifest 应用「GDPR_PII」强制安全标记。在 ECOS 中，下游的所有派生表都会自动、无条件地继承此标记锁。',
    ecosContext: '【官方设计】Markings (MAC) 具有极其强大的级联沿袭传播（Propagation）特征，数据加工无法逃逸标记锁定。',
    actionLabel: '一键应用并传播 GDPR_PII 标记锁'
  },
  {
    step: 4,
    title: '分析用途与脱敏绑定 (PBAC Purposes)',
    tab: 'pbac',
    description: '创建一个合法的分析用途（Purpose）「2026欧盟客运动态安全内审 (EU Passenger Audit)」。在这个用途里绑定特许 DPO 人员，并附加列级敏感数据遮蔽规则（如 passport_no 绝对遮蔽、email_address 部分遮蔽）。',
    ecosContext: '【官方设计】Purpose (PBAC) 控制数据的正当商业合规使用背景，它是解密 Markings 标记锁的唯一合规通道。',
    actionLabel: '一键申请并绑定客货运分析用途'
  },
  {
    step: 5,
    title: '多重复合判定校验 (Zero-Trust Simulation)',
    tab: 'overview',
    description: '在主页面对刚刚配置的安全体系进行多重复合网关验证！我们将模拟 EU_DPO 主体在合法用途下读取高敏 ds_passenger_manifest 数据，结果应为 GRANTED 允许，且自动在底层激活列级脱敏（REDACT）遮蔽。',
    ecosContext: '【官方设计】在零信任网关中，所有访问必须满足：Org白名单 ➔ Project DAC ➔ Marking (MAC) ➔ Purpose (PBAC) 的链式全部通过。',
    actionLabel: '一键仿真零信任判定并触发脱敏'
  }
];

const financeSteps: PlaybookStep[] = [
  {
    step: 1,
    title: '特许财务组织建立 (Organizations)',
    tab: 'orgs',
    description: '建立独立的「特许财务内审中心 (Org_Finance_Dept)」专属物理域。财务域只信任内部核心业务 IP CIDR，防止外部运维、承包商等非该域主机探测。',
    ecosContext: '【官方设计】Organization 支持设置 IP 白名单 CIDR，未在物理域段内的请求会被直接阻断。',
    actionLabel: '一键划定特许财务域 (Org_Finance_Dept)'
  },
  {
    step: 2,
    title: '财务审计空间立项 (Project DAC)',
    tab: 'dac',
    description: '在特许财务域内建立高敏内审项目「proj_finance_audit_2026」。此时，将项目的跨组织检索可见性设为 False（其他分支机构搜索不到），仅添加 auditor_wang 与管理员。',
    ecosContext: '【官方设计】Discoverable 可选属性。如果未打开，非项目成员甚至无法通过索引检索到该项目文件夹的存在。',
    actionLabel: '一键立项并添加财务审计成员'
  },
  {
    step: 3,
    title: '特许敏感营收标记锁 (MAC Markings)',
    tab: 'mac',
    description: '对客票交易明细 ds_ticket_sales 施加高敏财务密级标记「M_HIGH_VAL_REVENUE」。所有联结、计算了该销售表的数据下游，都会强制携带此密级标签。',
    ecosContext: '【官方设计】Markings 是超越文件权限的全局强制标记。拥有该项目 Editor 权限，若无此安全标记亦不可读原始数据。',
    actionLabel: '一键施加财务敏感营收标记'
  },
  {
    step: 4,
    title: '生命周期到期机制演练 (PBAC Purpose)',
    tab: 'pbac',
    description: '添加审计分析用途「purpose_finance_audit_2026」。我们将体验当该合规分析用途过期（EXPIRED）时，分析员 auditor_wang 即使对该项目是 Owner，也会因为目的过期而无法解密营收标记！',
    ecosContext: '【官方设计】ECOS PBAC 用途有着极其严格的生命周期。一经到期或管理员手动吊销，底层解密钥匙自动失活。',
    actionLabel: '一键添加财务审计目的用途'
  },
  {
    step: 5,
    title: '动态行列过滤判定 (Row/Col Security & Logs)',
    tab: 'row_col',
    description: '转到行列级策略面板，激活对 ds_ticket_sales 表的列脱敏（card_number 绝对脱敏，customer_name 部分掩盖）以及行过滤（仅查看当前用户归属组织的航班记录），查看实时渲染的高敏财务解密结果与实时安全日志！',
    ecosContext: '【官方设计】行列级安全策略（Row/Column Level Security）是附加在解密后的数据渲染层的，根据当前用户身份动态决定列脱敏或行过滤。',
    actionLabel: '一键激活行列安全脱敏策略并看审计'
  }
];

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

export default function SecurityCenter({
  activeTab: propActiveTab,
  onActiveTabChange,
  orgs: propOrgs,
  setOrgs: propSetOrgs,
  projects: propProjects,
  setProjects: propSetProjects,
  markings: propMarkings,
  setMarkings: propSetMarkings,
  purposes: propPurposes,
  setPurposes: propSetPurposes,
  rowColPolicies: propRowColPolicies,
  setRowColPolicies: propSetRowColPolicies,
  auditLogs: propAuditLogs,
  setAuditLogs: propSetAuditLogs,
  simUser: propSimUser,
  setSimUser: propSetSimUser,
  simDataset: propSimDataset,
  setSimDataset: propSetSimDataset,
  simPurpose: propSimPurpose,
  setSimPurpose: propSetSimPurpose,
  simResult: propSimResult,
  setSimResult: propSetSimResult,
  selectedRowColDs: propSelectedRowColDs,
  setSelectedRowColDs: propSelectedRowColDsChange,
  showToast: propShowToast
}: SecurityCenterViewProps = {}) {
  // Navigation Tabs
  const [localActiveTab, setLocalActiveTab] = useState<'overview' | 'orgs' | 'dac' | 'mac' | 'pbac' | 'row_col' | 'audit' | 'guide'>('overview');
  const activeTab = propActiveTab !== undefined ? propActiveTab : localActiveTab;
  const setActiveTab = (tab: any) => {
    if (onActiveTabChange) onActiveTabChange(tab);
    else setLocalActiveTab(tab);
  };

  const showToast = propShowToast || ((type: 'success' | 'info' | 'error', msg: string) => {
    console.log(`[Security Center Notification - ${type}]: ${msg}`);
  });

  // --- Core States ---
  const [localOrgs, setLocalOrgs] = useState<SecurityOrg[]>(mockSecurityOrgs);
  const orgs = propOrgs !== undefined ? propOrgs : localOrgs;
  const setOrgs = propSetOrgs !== undefined ? propSetOrgs : setLocalOrgs;

  const [localProjects, setLocalProjects] = useState<ProjectDAC[]>(mockProjectDACs);
  const projects = propProjects !== undefined ? propProjects : localProjects;
  const setProjects = propSetProjects !== undefined ? propSetProjects : setLocalProjects;

  const [localMarkings, setLocalMarkings] = useState<SecurityMarking[]>(mockSecurityMarkings);
  const markings = propMarkings !== undefined ? propMarkings : localMarkings;
  const setMarkings = propSetMarkings !== undefined ? propSetMarkings : setLocalMarkings;

  const [localPurposes, setLocalPurposes] = useState<PurposePBAC[]>(mockPurposes);
  const purposes = propPurposes !== undefined ? propPurposes : localPurposes;
  const setPurposes = propSetPurposes !== undefined ? propSetPurposes : setLocalPurposes;

  const [localRowColPolicies, setLocalRowColPolicies] = useState<RowColPolicy[]>(mockRowColPolicies);
  const rowColPolicies = propRowColPolicies !== undefined ? propRowColPolicies : localRowColPolicies;
  const setRowColPolicies = propSetRowColPolicies !== undefined ? propSetRowColPolicies : setLocalRowColPolicies;

  const [localAuditLogs, setLocalAuditLogs] = useState<SecurityAuditLog[]>(initialAuditLogs);
  const auditLogs = propAuditLogs !== undefined ? propAuditLogs : localAuditLogs;
  const setAuditLogs = propSetAuditLogs !== undefined ? propSetAuditLogs : setLocalAuditLogs;

  // --- Selections for Details ---
  const [selectedProjectId, setSelectedProjectId] = useState<string>('proj_aviation_core');
  const [selectedMarkingId, setSelectedMarkingId] = useState<string>('M_SENSITIVE_REVENUE');
  
  const [localSelectedRowColDs, setLocalSelectedRowColDs] = useState<string>('ds_flight_schedules');
  const selectedRowColDs = propSelectedRowColDs !== undefined ? propSelectedRowColDs : localSelectedRowColDs;
  const setSelectedRowColDs = propSelectedRowColDsChange !== undefined ? propSelectedRowColDsChange : setLocalSelectedRowColDs;

  // --- Form & Modal States ---
  const [showAddOrgModal, setShowAddOrgModal] = useState(false);
  const [showAddMemberModal, setShowAddMemberModal] = useState(false);
  const [showAddMarkingModal, setShowAddMarkingModal] = useState(false);
  const [showAddPurposeModal, setShowAddPurposeModal] = useState(false);

  const [newOrgName, setNewOrgName] = useState('');
  const [newOrgId, setNewOrgId] = useState('');
  const [newOrgIp, setNewOrgIp] = useState('');
  const [newOrgIsolation, setNewOrgIsolation] = useState<boolean>(true);

  const [newMemberName, setNewMemberName] = useState('');
  const [newMemberRole, setNewMemberRole] = useState<'Owner' | 'Editor' | 'Viewer'>('Viewer');

  const [newMarkId, setNewMarkId] = useState('');
  const [newMarkName, setNewMarkName] = useState('');
  const [newMarkLevel, setNewMarkLevel] = useState<'PUBLIC' | 'CONFIDENTIAL' | 'SECRET'>('CONFIDENTIAL');
  const [newMarkDesc, setNewMarkDesc] = useState('');

  const [newPurpId, setNewPurpId] = useState('');
  const [newPurpName, setNewPurpName] = useState('');
  const [newPurpDesc, setNewPurpDesc] = useState('');
  const [newPurpDs, setNewPurpDs] = useState('');
  const [newPurpRules, setNewPurpRules] = useState('');

  // --- Policy Simulator States ---
  const [localSimUser, setLocalSimUser] = useState('analyst_li');
  const simUser = propSimUser !== undefined ? propSimUser : localSimUser;
  const setSimUser = propSetSimUser !== undefined ? propSetSimUser : setLocalSimUser;

  const [localSimDataset, setLocalSimDataset] = useState('ds_flight_schedules');
  const simDataset = propSimDataset !== undefined ? propSimDataset : localSimDataset;
  const setSimDataset = propSetSimDataset !== undefined ? propSetSimDataset : setLocalSimDataset;

  const [localSimPurpose, setLocalSimPurpose] = useState('purpose_eu_passenger_audit');
  const simPurpose = propSimPurpose !== undefined ? propSimPurpose : localSimPurpose;
  const setSimPurpose = propSetSimPurpose !== undefined ? propSetSimPurpose : setLocalSimPurpose;

  const [localSimResult, setLocalSimResult] = useState<{ verdict: 'GRANTED' | 'DENIED'; traces: string[] } | null>(null);
  const simResult = propSimResult !== undefined ? propSimResult : localSimResult;
  const setSimResult = propSetSimResult !== undefined ? propSetSimResult : setLocalSimResult;

  const [simIp, setSimIp] = useState('10.120.5.23');

  useEffect(() => {
    if (simUser === 'analyst_li' || simUser === 'admin_guorong') {
      setSimIp('10.120.5.23');
    } else if (simUser === 'operator_zhang') {
      setSimIp('172.16.45.12');
    } else if (simUser === 'contractor_eng' || simUser === 'external_auditor') {
      setSimIp('202.96.128.44');
    } else if (simUser === 'EU_DPO') {
      setSimIp('10.120.9.15');
    } else {
      setSimIp('198.51.100.45');
    }
  }, [simUser]);

  // --- Statistics & Charts Data ---
  const COLORS = ['#10b981', '#ef4444', '#f59e0b', '#3b82f6'];
  const pieData = [
    { name: '通过 (GRANTED)', value: 145 },
    { name: '拦截 (DENIED)', value: 34 },
    { name: '审计预警 (WARN)', value: 12 }
  ];
  const evaluationTrend = [
    { name: '06-25', count: 120, granted: 105, denied: 15 },
    { name: '06-26', count: 145, granted: 125, denied: 20 },
    { name: '06-27', count: 190, granted: 165, denied: 25 },
    { name: '06-28', count: 135, granted: 110, denied: 25 },
    { name: '06-29', count: 155, granted: 135, denied: 20 },
    { name: '06-30', count: 210, granted: 180, denied: 30 },
    { name: '07-01', count: 185, granted: 155, denied: 30 }
  ];

  // --- Playbook Guide State ---
  const [showPlaybookGuide, setShowPlaybookGuide] = useState(false);
  const [activeScenario, setActiveScenario] = useState<'gdpr' | 'finance'>('gdpr');
  const [playbookStep, setPlaybookStep] = useState<number>(1); // 1 to 5

  // --- Diagnostic Report State ---
  const [showDiagnosticModal, setShowDiagnosticModal] = useState(false);
  const [diagnosticReport, setDiagnosticReport] = useState('');

  // --- Internal Active Section for Administrator Operation Manual ---
  const [guideSection, setGuideSection] = useState<'arch' | 'org' | 'dac' | 'mac' | 'pbac' | 'audit'>('arch');

  const handleExecutePlaybookStep = (step: PlaybookStep) => {
    // 1. Transition tab
    setActiveTab(step.tab);

    if (activeScenario === 'gdpr') {
      if (step.step === 1) {
        // Ingest Org_EU_Ops
        const exists = orgs.some(o => o.id === 'Org_EU_Ops');
        if (!exists) {
          const newOrg: SecurityOrg = {
            id: 'Org_EU_Ops',
            name: '欧盟运营分部 (Org_EU_Ops)',
            isolationMode: true,
            memberCount: 120,
            ipRanges: ['10.150.0.0/16', '10.152.0.0/24'],
            crossOrgSharing: [],
            createdAt: new Date().toISOString().split('T')[0]
          };
          setOrgs(prev => [newOrg, ...prev]);
        }
        // Add audit log
        const log: SecurityAuditLog = {
          id: `log_playbook_${Date.now()}`,
          timestamp: new Date().toTimeString().split(' ')[0],
          username: 'admin_guorong',
          orgId: 'org_aviation_hq',
          resourceId: 'Org_EU_Ops',
          resourceType: 'Organization',
          action: 'CREATE_ORG',
          status: 'SUCCESS',
          details: '「📖 GDPR实战向导模式」自动触发：成功创建欧盟隔离域 Org_EU_Ops，设置物理高层阻断。'
        };
        setAuditLogs(prev => [log, ...prev]);
      } 
      else if (step.step === 2) {
        // Ingest proj_passenger_eu
        const exists = projects.some(p => p.id === 'proj_passenger_eu');
        if (!exists) {
          const newProj: ProjectDAC = {
            id: 'proj_passenger_eu',
            name: '欧盟旅客隐私项目 (proj_passenger_eu)',
            description: '包含欧盟始发/终到旅客舱单、过境清关数据、会员敏感PII等，受GDPR法律严格控制。',
            members: [
              { username: 'EU_DPO', role: 'Owner', grantedBy: 'admin_guorong', grantedAt: new Date().toISOString().split('T')[0] },
              { username: 'external_auditor', role: 'Discoverer', grantedBy: 'EU_DPO', grantedAt: new Date().toISOString().split('T')[0] }
            ],
            discoverableAllOrgs: false,
            autoPropagation: true
          };
          setProjects(prev => [newProj, ...prev]);
        }
        setSelectedProjectId('proj_passenger_eu');
        // Add audit log
        const log: SecurityAuditLog = {
          id: `log_playbook_${Date.now()}`,
          timestamp: new Date().toTimeString().split(' ')[0],
          username: 'admin_guorong',
          orgId: 'Org_EU_Ops',
          resourceId: 'proj_passenger_eu',
          resourceType: 'Project',
          action: 'UPDATE_PROJECT_ACL',
          status: 'SUCCESS',
          details: '「📖 GDPR实战向导模式」：自动在欧盟项目组内，授予 EU_DPO (合规官) 角色 Owner 管理权。'
        };
        setAuditLogs(prev => [log, ...prev]);
      }
      else if (step.step === 3) {
        // Ingest GDPR_PII marking
        const exists = markings.some(m => m.id === 'M_GDPR_PII');
        if (!exists) {
          const newMarking: SecurityMarking = {
            id: 'M_GDPR_PII',
            displayName: 'GDPR_PII (欧盟客户敏感身份)',
            apiName: 'gdpr_pii',
            classificationLevel: 'RESTRICTED',
            grantedGroups: ['data_protection_officer', 'customer_care_vip'],
            appliedDatasets: ['ds_passenger_manifest'],
            description: '欧盟通用数据保护条例强制标记。受该标记限制的数据会自动级联向下游派生关系传播。'
          };
          setMarkings(prev => [newMarking, ...prev]);
        }
        setSelectedMarkingId('M_GDPR_PII');
        // Add audit log
        const log: SecurityAuditLog = {
          id: `log_playbook_${Date.now()}`,
          timestamp: new Date().toTimeString().split(' ')[0],
          username: 'EU_DPO',
          orgId: 'Org_EU_Ops',
          resourceId: 'M_GDPR_PII',
          resourceType: 'SecurityMarking',
          action: 'APPLY_MARKING',
          status: 'SUCCESS',
          details: '「📖 GDPR实战向导模式」：对 ds_passenger_manifest 数据集施加 GDPR_PII 标记锁，开启级联传播沿袭。'
        };
        setAuditLogs(prev => [log, ...prev]);
      }
      else if (step.step === 4) {
        // Ingest purpose_eu_passenger_audit_2026
        const exists = purposes.some(p => p.id === 'purpose_eu_passenger_audit_2026');
        if (!exists) {
          const newPurpose: PurposePBAC = {
            id: 'purpose_eu_passenger_audit_2026',
            name: '2026欧盟客运动态安全内审 (EU Passenger Audit)',
            description: '旨在对欧盟航线执行一年一度的动态客运流量及信息合规分析。正当用途合法有效。',
            authorizedUsers: ['EU_DPO', 'admin_guorong'],
            inputDatasets: ['ds_passenger_manifest'],
            redactionRules: ['MASK(passport_no)', 'PARTIAL(email_address)'],
            expiresAt: '2026-12-31',
            status: 'ACTIVE'
          };
          setPurposes(prev => [newPurpose, ...prev]);
        }
        // Ensure Row/Col policy exists for ds_passenger_manifest
        const colExists = rowColPolicies.some(p => p.datasetId === 'ds_passenger_manifest');
        if (!colExists) {
          const newColPolicy: RowColPolicy = {
            datasetId: 'ds_passenger_manifest',
            datasetName: 'passenger_manifest_eu (欧盟客运舱密单)',
            columnMasks: [
              { column: 'passport_no', maskType: 'REDACT', active: true },
              { column: 'email_address', maskType: 'PARTIAL', active: true },
              { column: 'seat_class', maskType: 'NONE', active: true }
            ],
            rowFilters: [
              { filterSql: "org_id = 'Org_EU_Ops'", description: "仅展示归属Org_EU_Ops的本国航段旅客", active: true }
            ]
          };
          setRowColPolicies(prev => [newColPolicy, ...prev]);
        }
        setSelectedRowColDs('ds_passenger_manifest');
        // Add audit log
        const log: SecurityAuditLog = {
          id: `log_playbook_${Date.now()}`,
          timestamp: new Date().toTimeString().split(' ')[0],
          username: 'EU_DPO',
          orgId: 'Org_EU_Ops',
          resourceId: 'purpose_eu_passenger_audit_2026',
          resourceType: 'Purpose',
          action: 'CREATE_PURPOSE',
          status: 'SUCCESS',
          details: '「📖 GDPR实战向导模式」：成功申请并激活欧盟旅客审计目的用途，绑定列脱敏 MASK(passport_no) 策略。'
        };
        setAuditLogs(prev => [log, ...prev]);
      }
      else if (step.step === 5) {
        // Run simulator
        setSimUser('EU_DPO');
        setSimDataset('ds_passenger_manifest');
        setSimPurpose('purpose_eu_passenger_audit_2026');
        
        // Execute simulator after a tiny timeout to let state apply
        setTimeout(() => {
          const traces = [
            `[1] 初始化安全校验规则链路: 用户 EU_DPO 请求读取数据集 ds_passenger_manifest`,
            `[2] 检测到用户组织归属: Org_EU_Ops`,
            `[3] 项目 DAC (自主访问控制) 验证: 用户在「欧盟旅客隐私项目 (proj_passenger_eu)」中拥有角色 [Owner]. DAC 校验通过。`,
            `[4] 检测到强制访问标记(MAC)锁定: 该数据集已被标记 [GDPR_PII (欧盟客户敏感身份)] 保护`,
            `    -> 匹配结果: 用户具有特许组权限 [data_protection_officer]。MAC校验通过。`,
            `[5] 目的合规性验证(PBAC)生效中: 关联分析目的「2026欧盟客运动态安全内审 (EU Passenger Audit)」`,
            `    -> 校验通过: 用户处于受托人员列表中，目的状态为 [ACTIVE]，强制脱敏列规则 [MASK(passport_no), PARTIAL(email_address)] 自动装载！`,
            `[6] 最终决策裁定: [允许访问 (ACCESS GRANTED) 且脱敏输出]`
          ];
          setSimResult({ verdict: 'GRANTED', traces });
          
          // Add audit log
          const log: SecurityAuditLog = {
            id: `log_playbook_${Date.now()}`,
            timestamp: new Date().toTimeString().split(' ')[0],
            username: 'EU_DPO',
            orgId: 'Org_EU_Ops',
            resourceId: 'ds_passenger_manifest',
            resourceType: 'Dataset',
            action: 'READ_DATASET',
            status: 'SUCCESS',
            details: '「📖 GDPR实战向导模式」：DPO 成功触发零信任解密，脱敏引擎应用 MASK(passport_no) 进行流式遮蔽输出。'
          };
          setAuditLogs(prev => [log, ...prev]);
        }, 100);
      }
    } 
    else if (activeScenario === 'finance') {
      if (step.step === 1) {
        // Ingest Org_Finance_Dept
        const exists = orgs.some(o => o.id === 'Org_Finance_Dept');
        if (!exists) {
          const newOrg: SecurityOrg = {
            id: 'Org_Finance_Dept',
            name: '特许财务内审中心 (Org_Finance_Dept)',
            isolationMode: true,
            memberCount: 50,
            ipRanges: ['192.168.8.0/24'],
            crossOrgSharing: [],
            createdAt: new Date().toISOString().split('T')[0]
          };
          setOrgs(prev => [newOrg, ...prev]);
        }
        // Add audit log
        const log: SecurityAuditLog = {
          id: `log_playbook_${Date.now()}`,
          timestamp: new Date().toTimeString().split(' ')[0],
          username: 'admin_guorong',
          orgId: 'org_aviation_hq',
          resourceId: 'Org_Finance_Dept',
          resourceType: 'Organization',
          action: 'CREATE_ORG',
          status: 'SUCCESS',
          details: '「📖 财务审计向导模式」：成功创建特许财务组织 Org_Finance_Dept，设定 192.168.8.0 网段信任。'
        };
        setAuditLogs(prev => [log, ...prev]);
      }
      else if (step.step === 2) {
        // Ingest proj_finance_audit_2026
        const exists = projects.some(p => p.id === 'proj_finance_audit_2026');
        if (!exists) {
          const newProj: ProjectDAC = {
            id: 'proj_finance_audit_2026',
            name: '2026高敏财务内审项目 (proj_finance_audit_2026)',
            description: '由集团内审部设立，专门用于机票销售明细、税收减免及退改票异常账目核查空间。',
            members: [
              { username: 'auditor_wang', role: 'Owner', grantedBy: 'admin_guorong', grantedAt: new Date().toISOString().split('T')[0] },
              { username: 'admin_guorong', role: 'Owner', grantedBy: 'System', grantedAt: new Date().toISOString().split('T')[0] }
            ],
            discoverableAllOrgs: false,
            autoPropagation: false
          };
          setProjects(prev => [newProj, ...prev]);
        }
        setSelectedProjectId('proj_finance_audit_2026');
        // Add audit log
        const log: SecurityAuditLog = {
          id: `log_playbook_${Date.now()}`,
          timestamp: new Date().toTimeString().split(' ')[0],
          username: 'admin_guorong',
          orgId: 'Org_Finance_Dept',
          resourceId: 'proj_finance_audit_2026',
          resourceType: 'Project',
          action: 'CREATE_PROJECT',
          status: 'SUCCESS',
          details: '「📖 财务审计向导模式」：成立2026内审高密项目，关闭全局可见检索。仅添加 auditor_wang 审计角色。'
        };
        setAuditLogs(prev => [log, ...prev]);
      }
      else if (step.step === 3) {
        // Apply M_HIGH_VAL_REVENUE marking
        const exists = markings.some(m => m.id === 'M_HIGH_VAL_REVENUE');
        if (!exists) {
          const newMarking: SecurityMarking = {
            id: 'M_HIGH_VAL_REVENUE',
            displayName: 'HIGH_VAL_REVENUE (特许高额营收特许标记)',
            apiName: 'high_val_revenue',
            classificationLevel: 'SECRET',
            grantedGroups: ['finance_auditors_grp'],
            appliedDatasets: ['ds_ticket_sales'],
            description: '特许财务最高营收标记锁。所有涉及机票营收明细的表，都将强制继承并向下沿袭锁定。'
          };
          setMarkings(prev => [newMarking, ...prev]);
        }
        setSelectedMarkingId('M_HIGH_VAL_REVENUE');
        // Add audit log
        const log: SecurityAuditLog = {
          id: `log_playbook_${Date.now()}`,
          timestamp: new Date().toTimeString().split(' ')[0],
          username: 'admin_guorong',
          orgId: 'Org_Finance_Dept',
          resourceId: 'M_HIGH_VAL_REVENUE',
          resourceType: 'SecurityMarking',
          action: 'APPLY_MARKING',
          status: 'SUCCESS',
          details: '「📖 财务审计向导模式」：对 ds_ticket_sales（客票交易明细）施加 HIGH_VAL_REVENUE 密级锁，锁定销售上游。'
        };
        setAuditLogs(prev => [log, ...prev]);
      }
      else if (step.step === 4) {
        // Ingest purpose_finance_audit_2026
        const exists = purposes.some(p => p.id === 'purpose_finance_audit_2026');
        if (!exists) {
          const newPurpose: PurposePBAC = {
            id: 'purpose_finance_audit_2026',
            name: '2026年度异常账目退票合规核查 (Finance Audit 2026)',
            description: '主要进行2026年度客票销售交易审计与退票异常资金溯源核销用途。',
            authorizedUsers: ['auditor_wang'],
            inputDatasets: ['ds_ticket_sales'],
            redactionRules: ['MASK(card_number)', 'PARTIAL(customer_name)'],
            expiresAt: '2026-11-30',
            status: 'ACTIVE'
          };
          setPurposes(prev => [newPurpose, ...prev]);
        }
        // Ensure Row/Col policy exists for ds_ticket_sales
        const colExists = rowColPolicies.some(p => p.datasetId === 'ds_ticket_sales');
        if (!colExists) {
          const newColPolicy: RowColPolicy = {
            datasetId: 'ds_ticket_sales',
            datasetName: 'ticket_sales_raw (客票交易明细)',
            columnMasks: [
              { column: 'card_number', maskType: 'REDACT', active: true },
              { column: 'customer_name', maskType: 'PARTIAL', active: true },
              { column: 'total_amount', maskType: 'NONE', active: true }
            ],
            rowFilters: [
              { filterSql: "org_id = 'Org_Finance_Dept'", description: "仅过滤展示财务部门核准的主航线机票账单", active: true }
            ]
          };
          setRowColPolicies(prev => [newColPolicy, ...prev]);
        }
        setSelectedRowColDs('ds_ticket_sales');
        // Add audit log
        const log: SecurityAuditLog = {
          id: `log_playbook_${Date.now()}`,
          timestamp: new Date().toTimeString().split(' ')[0],
          username: 'auditor_wang',
          orgId: 'Org_Finance_Dept',
          resourceId: 'purpose_finance_audit_2026',
          resourceType: 'Purpose',
          action: 'CREATE_PURPOSE',
          status: 'SUCCESS',
          details: '「📖 财务审计向导模式」：成功申请财务审计目的用途，并设定目的到期生命周期规则。'
        };
        setAuditLogs(prev => [log, ...prev]);
      }
      else if (step.step === 5) {
        // Switch Row Col tab and view preview
        setSelectedRowColDs('ds_ticket_sales');
        
        // Ensure simulation variables set
        setSimUser('auditor_wang');
        setSimDataset('ds_ticket_sales');
        setSimPurpose('purpose_finance_audit_2026');
        
        // Execute simulator
        setTimeout(() => {
          const traces = [
            `[1] 初始化安全校验规则链路: 用户 auditor_wang 请求读取数据集 ds_ticket_sales`,
            `[2] 检测到用户组织归属: Org_Finance_Dept`,
            `[3] 项目 DAC (自主访问控制) 验证: 用户在「2026高敏财务内审项目 (proj_finance_audit_2026)」中拥有角色 [Owner]. DAC 校验通过。`,
            `[4] 检测到强制访问标记(MAC)锁定: 该数据集已被标记 [M_HIGH_VAL_REVENUE] 保护`,
            `    -> 匹配结果: 用户拥有特许组权限 [finance_auditors_grp]。MAC校验通过。`,
            `[5] 目的合规性验证(PBAC)生效中: 关联分析目的「2026年度异常账目退票合规核查 (Finance Audit 2026)」`,
            `    -> 校验通过: 用户处于该分析目的执行人列表中，状态为 [ACTIVE]，行过滤及列遮蔽规则自动激活：card_number 遮蔽 & 仅查看所属组织账单！`,
            `[6] 最终决策裁定: [允许访问 (ACCESS GRANTED) 并启用物理隔离过滤与脱敏预览]`
          ];
          setSimResult({ verdict: 'GRANTED', traces });
          
          // Add audit log
          const log: SecurityAuditLog = {
            id: `log_playbook_${Date.now()}`,
            timestamp: new Date().toTimeString().split(' ')[0],
            username: 'auditor_wang',
            orgId: 'Org_Finance_Dept',
            resourceId: 'ds_ticket_sales',
            resourceType: 'Dataset',
            action: 'READ_DATASET',
            status: 'SUCCESS',
            details: '「📖 财务审计向导模式」：审计人员触发访问，行列安全过滤器激活：card_number掩盖，行物理过滤生效。'
          };
          setAuditLogs(prev => [log, ...prev]);
        }, 100);
      }
    }
  };
  useEffect(() => {
    const timer = setInterval(() => {
      const users = ['analyst_li', 'hr_manager', 'external_auditor', 'operator_zhang', 'contractor_eng'];
      const actions = ['READ_DATASET', 'UPDATE_PROJECT_ACL', 'EVAL_ROW_FILTER', 'DOWNLOAD_REPORT', 'EXPORT_MARKING'];
      const resources = ['ds_flight_schedules', 'ds_ticket_sales', 'ds_pilots_biography', 'M_SENSITIVE_REVENUE', 'proj_pilot_credentials'];
      const orgsList = ['org_aviation_hq', 'org_logistics_p', 'org_contractor'];
      
      const randomUser = users[Math.floor(Math.random() * users.length)];
      const randomAction = actions[Math.floor(Math.random() * actions.length)];
      const randomResource = resources[Math.floor(Math.random() * resources.length)];
      const randomOrg = orgsList[Math.floor(Math.random() * orgsList.length)];
      const isSuccess = Math.random() > 0.25;

      const now = new Date();
      const timeStr = now.toTimeString().split(' ')[0];

      const detailsMap: Record<string, string> = {
        'READ_DATASET': `请求读取 ${randomResource} 数据集。`,
        'UPDATE_PROJECT_ACL': `尝试更新项目访问白名单 (ACL)。`,
        'EVAL_ROW_FILTER': `计算并评估行过滤器安全策略。`,
        'DOWNLOAD_REPORT': `执行数据统计与脱敏报表下载。`,
        'EXPORT_MARKING': `导出含有敏感属性的安全标记实体资产。`
      };

      const newLog: SecurityAuditLog = {
        id: `log_${Date.now()}`,
        timestamp: timeStr,
        username: randomUser,
        orgId: randomOrg,
        resourceId: randomResource,
        resourceType: randomResource.startsWith('ds_') ? 'Dataset' : randomResource.startsWith('M_') ? 'SecurityMarking' : 'Project',
        action: randomAction,
        status: isSuccess ? 'SUCCESS' : (Math.random() > 0.5 ? 'DENIED' : 'WARN'),
        details: isSuccess 
          ? `${detailsMap[randomAction]} 评估结果: 准予访问。` 
          : `评估结果: 拒绝访问。触发安全屏障拦截器(缺少相应组织白名单或安全标记)。`
      };

      setAuditLogs(prev => [newLog, ...prev.slice(0, 19)]);
    }, 6000);

    return () => clearInterval(timer);
  }, []);

  // --- Run Policy Simulation ---
  const handleRunSimulation = async () => {
    // Determine the user's organization
    let orgId = 'org_aviation_hq';
    if (simUser === 'operator_zhang') orgId = 'org_logistics_p';
    else if (simUser === 'contractor_eng' || simUser === 'external_auditor') orgId = 'org_contractor';
    else if (simUser === 'EU_DPO') orgId = 'Org_EU_Ops';
    else if (simUser === 'auditor_wang') orgId = 'Org_Finance_Dept';

    // Determine the project DAC container
    let projectId = 'proj_aviation_core';
    if (simDataset === 'ds_ticket_sales') {
      projectId = projects.some(p => p.id === 'proj_finance_audit_2026') ? 'proj_finance_audit_2026' : 'proj_flight_analytics';
    } else if (simDataset === 'ds_pilots_biography') {
      projectId = 'proj_pilot_credentials';
    } else if (simDataset === 'ds_passenger_manifest') {
      projectId = 'proj_passenger_eu';
    }

    try {
      showToast('info', '正在联系零信任加密解密网关，编译解密谓词...');
      
      const response = await fetch('/api/security/decrypt', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          userId: simUser,
          orgId,
          projectId,
          datasetId: simDataset,
          purposeId: simPurpose,
          clientIp: simIp,
          cipherText: `CIPHER_${simDataset.toUpperCase()}`
        })
      });

      const data = await response.json();
      
      setSimResult({
        verdict: data.verdict,
        traces: data.traces
      });

      if (data.success) {
        showToast('success', `🔓 判定通过: 明文段还原成功!`);
      } else {
        showToast('error', `🛡️ 安全拦截: 网关拒绝解密物理主键！`);
      }

      // Sync audit logs list on screen
      const logsRes = await fetch('/api/security/audit-logs');
      if (logsRes.ok) {
        const logs = await logsRes.json();
        setAuditLogs(logs);
      }
    } catch (err) {
      console.error('Master decryption API simulation failed:', err);
      showToast('error', '端到端解密网关通信故障，切换至本地离线引擎判定。');
      
      // Fallback to offline rule emulation if server is disconnected
      const traces: string[] = [];
      traces.push(`[1] 初始化安全校验规则链路: 用户 ${simUser} 请求读取数据集 ${simDataset}`);
      traces.push(`⚠️ [WARNING] 发生网关通信超时，启动浏览器端离线降级编译器自检...`);
      
      let userOrgName = '航空总部研发隔离域 (org_aviation_hq)';
      if (orgId === 'org_logistics_p') userOrgName = '物流供应链合作伙伴 (org_logistics_p)';
      else if (orgId === 'org_contractor') userOrgName = '外部承包商协查网络 (org_contractor)';
      else if (orgId === 'Org_EU_Ops') userOrgName = '欧盟运营分部 (Org_EU_Ops)';
      else if (orgId === 'Org_Finance_Dept') userOrgName = '特许财务内审中心 (Org_Finance_Dept)';
      traces.push(`[2] 离线检测用户组织归属: ${userOrgName}`);

      let targetDatasetOrg = 'org_aviation_hq';
      if (simDataset === 'ds_passenger_manifest') targetDatasetOrg = 'Org_EU_Ops';
      else if (simDataset === 'ds_ticket_sales' && projects.some(p => p.id === 'proj_finance_audit_2026')) targetDatasetOrg = 'Org_Finance_Dept';

      let isVerified = true;
      if (orgId !== targetDatasetOrg && simUser !== 'admin_guorong') {
        traces.push(`    -> [组织级安全门禁拦截]！物理隔离拦截`);
        isVerified = false;
      } else {
        traces.push(`    -> [组织级安全门禁校验通过] 同一逻辑网络内。`);
      }

      let dacVerdict = false;
      const proj = projects.find(p => p.id === projectId);
      if (proj && isVerified) {
        const member = proj.members.find(m => m.username === simUser);
        if (member) {
          traces.push(`[3] 项目 DAC 验证: 用户在「${proj.name}」中拥有角色 [${member.role}]`);
          dacVerdict = true;
        } else if (simUser === 'admin_guorong') {
          traces.push(`[3] 项目 DAC 验证: 超级管理员，过载通过。`);
          dacVerdict = true;
        } else {
          traces.push(`[3] 项目 DAC 校验失败: 缺少授权角色`);
          isVerified = false;
        }
      }

      let macVerdict = true;
      const associatedMarking = markings.find(m => m.appliedDatasets.includes(simDataset));
      if (associatedMarking && isVerified) {
        traces.push(`[4] 强制访问标记(MAC)保护: 数据集被标记 [${associatedMarking.displayName || associatedMarking.id}] 保护`);
        let hasMarking = simUser === 'admin_guorong' ||
          (simUser === 'hr_manager' && associatedMarking.id === 'M_PILOT_CONFIDENTIAL') ||
          (simUser === 'analyst_li' && associatedMarking.id === 'M_SENSITIVE_REVENUE') ||
          (simUser === 'EU_DPO' && associatedMarking.id === 'M_GDPR_PII');
        if (hasMarking) {
          traces.push(`    -> 匹配结果: 用户安全凭证吻合。`);
        } else {
          traces.push(`    -> 匹配结果: 拦截！安全凭证缺失。`);
          macVerdict = false;
          isVerified = false;
        }
      }

      let pbacVerdict = true;
      const selectedPurp = purposes.find(p => p.id === simPurpose);
      if (selectedPurp && isVerified) {
        traces.push(`[5] 目的合规性验证(PBAC): 关联分析目的「${selectedPurp.name}」`);
        if (selectedPurp.status === 'EXPIRED') {
          traces.push(`    -> 校验失败: 该目的已失效过期`);
          pbacVerdict = false;
          isVerified = false;
        } else {
          const isUserAuth = selectedPurp.authorizedUsers.includes(simUser) || simUser === 'admin_guorong';
          if (isUserAuth) {
            traces.push(`    -> 校验通过: 特许名单分析小组。`);
          } else {
            traces.push(`    -> 校验失败: 用户不属于授权列表`);
            pbacVerdict = false;
            isVerified = false;
          }
        }
      }

      const finalVerdict = isVerified ? 'GRANTED' : 'DENIED';
      traces.push(`[6] 离线评定最终决策结果: [${finalVerdict === 'GRANTED' ? '允许访问 (ACCESS GRANTED)' : '拦截拒绝 (ACCESS DENIED)'}]`);
      setSimResult({ verdict: finalVerdict, traces });
    }
  };

  const openDiagnosticReport = async () => {
    try {
      const res = await fetch('/api/security/audit');
      if (res.ok) {
        const data = await res.json();
        setDiagnosticReport(data.diagnosticReport || '');
        setShowDiagnosticModal(true);
        showToast('success', '🧬 自适应安全扫描诊断书加载成功！');
      } else {
        showToast('error', '获取诊断书失败，请检查安全服务。');
      }
    } catch (e) {
      console.error(e);
      showToast('error', '服务通信故障，无法调取主权级合规审计诊断书。');
    }
  };

  // --- Operations / Submits ---
  const handleAddOrg = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newOrgName || !newOrgId) return;
    const newOrg: SecurityOrg = {
      id: newOrgId,
      name: newOrgName,
      isolationMode: newOrgIsolation,
      memberCount: 0,
      ipRanges: newOrgIp ? [newOrgIp] : ['10.0.0.0/24'],
      crossOrgSharing: [],
      createdAt: new Date().toISOString().split('T')[0]
    };
    setOrgs([...orgs, newOrg]);
    setNewOrgName('');
    setNewOrgId('');
    setNewOrgIp('');
    setShowAddOrgModal(false);
  };

  const handleAddMember = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newMemberName) return;
    setProjects(projects.map(p => {
      if (p.id === selectedProjectId) {
        return {
          ...p,
          members: [
            ...p.members,
            {
              username: newMemberName,
              role: newMemberRole,
              grantedBy: 'admin_guorong',
              grantedAt: new Date().toISOString().split('T')[0]
            }
          ]
        };
      }
      return p;
    }));
    setNewMemberName('');
    setShowAddMemberModal(false);
  };

  const handleAddMarking = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newMarkName || !newMarkId) return;
    const newMark: SecurityMarking = {
      id: newMarkId,
      displayName: `${newMarkId} (${newMarkName})`,
      apiName: newMarkId.toLowerCase(),
      classificationLevel: newMarkLevel,
      grantedGroups: ['admin_super_grp'],
      appliedDatasets: [],
      description: newMarkDesc || '无描述'
    };
    setMarkings([...markings, newMark]);
    setNewMarkName('');
    setNewMarkId('');
    setNewMarkDesc('');
    setShowAddMarkingModal(false);
  };

  const handleAddPurpose = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newPurpName || !newPurpId) return;
    const newPurp: PurposePBAC = {
      id: newPurpId,
      name: newPurpName,
      description: newPurpDesc,
      authorizedUsers: ['analyst_li'],
      inputDatasets: newPurpDs ? [newPurpDs] : [],
      redactionRules: newPurpRules ? [newPurpRules] : ['NONE'],
      expiresAt: '2026-12-31',
      status: 'ACTIVE'
    };
    setPurposes([...purposes, newPurp]);
    setNewPurpName('');
    setNewPurpId('');
    setNewPurpDesc('');
    setNewPurpDs('');
    setNewPurpRules('');
    setShowAddPurposeModal(false);
  };

  const handleToggleColumnMask = (datasetId: string, colName: string) => {
    setRowColPolicies(rowColPolicies.map(p => {
      if (p.datasetId === datasetId) {
        return {
          ...p,
          columnMasks: p.columnMasks.map(c => c.column === colName ? { ...c, active: !c.active } : c)
        };
      }
      return p;
    }));
  };

  const handleToggleRowFilter = (datasetId: string, idx: number) => {
    setRowColPolicies(rowColPolicies.map(p => {
      if (p.datasetId === datasetId) {
        return {
          ...p,
          rowFilters: p.rowFilters.map((f, i) => i === idx ? { ...f, active: !f.active } : f)
        };
      }
      return p;
    }));
  };

  return (
    <div className="h-full w-full flex bg-slate-900 text-slate-100 overflow-hidden font-sans select-none">
      
      {/* LEFT NAVIGATION COLUMN */}
      <div className="w-64 bg-slate-950 border-r border-slate-800 flex flex-col justify-between shrink-0">
        <div className="p-4 space-y-5">
          <div className="flex items-center gap-2.5 px-2">
            <span className="p-1.5 rounded-lg bg-indigo-600 text-white flex items-center justify-center shadow-md">
              <ShieldAlert size={15} />
            </span>
            <div>
              <div className="text-xs font-black tracking-wider text-slate-200 uppercase">ECOS 安全中心</div>
              <div className="text-[9px] text-indigo-400 font-mono">SECURE POSTURE CONTROL</div>
            </div>
          </div>

          <div className="h-px bg-slate-800" />

          <nav className="space-y-1">
            <div className="text-[10px] font-bold text-slate-500 uppercase tracking-wider px-2 mb-2">安全域导航</div>
            
            <button
              onClick={() => setActiveTab('overview')}
              className={`w-full flex items-center gap-2.5 px-3 py-2 text-xs rounded-lg transition-all font-semibold ${
                activeTab === 'overview'
                  ? 'bg-slate-800 text-white border-l-2 border-indigo-500'
                  : 'text-slate-400 hover:bg-slate-900 hover:text-slate-200'
              }`}
            >
              <Shield size={13} className={activeTab === 'overview' ? 'text-indigo-400' : 'text-slate-500'} />
              <span>安全合规概览</span>
            </button>

            <button
              onClick={() => setActiveTab('orgs')}
              className={`w-full flex items-center gap-2.5 px-3 py-2 text-xs rounded-lg transition-all font-semibold ${
                activeTab === 'orgs'
                  ? 'bg-slate-800 text-white border-l-2 border-indigo-500'
                  : 'text-slate-400 hover:bg-slate-900 hover:text-slate-200'
              }`}
            >
              <Globe size={13} className={activeTab === 'orgs' ? 'text-indigo-400' : 'text-slate-500'} />
              <span>组织隔离架构 (Orgs)</span>
            </button>

            <button
              onClick={() => setActiveTab('dac')}
              className={`w-full flex items-center gap-2.5 px-3 py-2 text-xs rounded-lg transition-all font-semibold ${
                activeTab === 'dac'
                  ? 'bg-slate-800 text-white border-l-2 border-indigo-500'
                  : 'text-slate-400 hover:bg-slate-900 hover:text-slate-200'
              }`}
            >
              <FolderGit size={13} className={activeTab === 'dac' ? 'text-indigo-400' : 'text-slate-500'} />
              <span>项目授权治理 (DAC)</span>
            </button>

            <button
              onClick={() => setActiveTab('mac')}
              className={`w-full flex items-center gap-2.5 px-3 py-2 text-xs rounded-lg transition-all font-semibold ${
                activeTab === 'mac'
                  ? 'bg-slate-800 text-white border-l-2 border-indigo-500'
                  : 'text-slate-400 hover:bg-slate-900 hover:text-slate-200'
              }`}
            >
              <Tag size={13} className={activeTab === 'mac' ? 'text-indigo-400' : 'text-slate-500'} />
              <span>安全标记管理 (MAC)</span>
            </button>

            <button
              onClick={() => setActiveTab('pbac')}
              className={`w-full flex items-center gap-2.5 px-3 py-2 text-xs rounded-lg transition-all font-semibold ${
                activeTab === 'pbac'
                  ? 'bg-slate-800 text-white border-l-2 border-indigo-500'
                  : 'text-slate-400 hover:bg-slate-900 hover:text-slate-200'
              }`}
            >
              <ClipboardList size={13} className={activeTab === 'pbac' ? 'text-indigo-400' : 'text-slate-500'} />
              <span>合规分析用途 (PBAC)</span>
            </button>

            <button
              onClick={() => setActiveTab('row_col')}
              className={`w-full flex items-center gap-2.5 px-3 py-2 text-xs rounded-lg transition-all font-semibold ${
                activeTab === 'row_col'
                  ? 'bg-slate-800 text-white border-l-2 border-indigo-500'
                  : 'text-slate-400 hover:bg-slate-900 hover:text-slate-200'
              }`}
            >
              <EyeOff size={13} className={activeTab === 'row_col' ? 'text-indigo-400' : 'text-slate-500'} />
              <span>行列级安全策略</span>
            </button>

            <button
              onClick={() => setActiveTab('audit')}
              className={`w-full flex items-center gap-2.5 px-3 py-2 text-xs rounded-lg transition-all font-semibold ${
                activeTab === 'audit'
                  ? 'bg-slate-800 text-white border-l-2 border-indigo-500'
                  : 'text-slate-400 hover:bg-slate-900 hover:text-slate-200'
              }`}
            >
              <Activity size={13} className={activeTab === 'audit' ? 'text-indigo-400' : 'text-slate-500'} />
              <span>动态安全审计 (Logs)</span>
            </button>

            <button
              onClick={() => setActiveTab('guide')}
              className={`w-full flex items-center gap-2.5 px-3 py-2 text-xs rounded-lg transition-all font-semibold ${
                activeTab === 'guide'
                  ? 'bg-slate-800 text-white border-l-2 border-indigo-500'
                  : 'text-slate-400 hover:bg-slate-900 hover:text-slate-200'
              }`}
            >
              <BookOpen size={13} className={activeTab === 'guide' ? 'text-indigo-400' : 'text-slate-500'} />
              <span>管理员操作手册</span>
            </button>
          </nav>
        </div>

        <div className="p-4 space-y-2 bg-slate-950 border-t border-slate-800/80">
          <div className="flex items-center gap-2 text-[10px] text-emerald-400 font-bold bg-emerald-950/40 p-2 rounded border border-emerald-900/30">
            <span className="h-2 w-2 rounded-full bg-emerald-400 animate-pulse shrink-0" />
            <span>ECOS 零信任网关已激活</span>
          </div>
          <div className="text-[9px] text-slate-500 leading-normal text-center font-mono">
            V.2026.07.SEC_SHIELD
          </div>
        </div>
      </div>

      {/* CENTRAL STAGE CONTAINER */}
      <div className="flex-1 flex flex-col overflow-hidden">
        
        {/* VIEW TITLE BAR */}
        <div className="h-14 bg-slate-950 border-b border-slate-800 px-6 flex items-center justify-between shrink-0">
          <div className="flex items-center gap-3">
            <div className="text-sm font-black text-slate-100 flex items-center gap-2">
              <span>Palantir Security Guardian</span>
              <span className="text-[10px] bg-slate-800 text-slate-400 px-2 py-0.5 rounded font-mono uppercase">
                Enterprise Shield
              </span>
            </div>
          </div>
          <div className="flex items-center gap-2 text-xs text-slate-400">
            <span>安全上下文: </span>
            <span className="text-indigo-400 font-mono font-bold">Aviation_Domain_Root</span>
          </div>
        </div>

        {/* ACTIVE STAGE CONTENT */}
        <div className="flex-1 overflow-y-auto p-6 bg-slate-900">
          
          {/* ================================================== */}
          {/* TAB 1: OVERVIEW (安全合规概览) */}
          {/* ================================================== */}
          {activeTab === 'overview' && (
            <div className="space-y-6">
              
              {/* Summary Metrics */}
              <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                <div className="bg-slate-950 border border-slate-800 rounded-xl p-4 flex items-center justify-between">
                  <div className="space-y-1">
                    <div className="text-[11px] text-slate-400 font-semibold uppercase">安全隔离域 (Orgs)</div>
                    <div className="text-xl font-mono font-black text-white">{orgs.length}</div>
                  </div>
                  <span className="p-2 rounded-lg bg-indigo-500/10 text-indigo-400">
                    <Globe size={18} />
                  </span>
                </div>
                <div className="bg-slate-950 border border-slate-800 rounded-xl p-4 flex items-center justify-between">
                  <div className="space-y-1">
                    <div className="text-[11px] text-slate-400 font-semibold uppercase">受控工程项目 (DAC)</div>
                    <div className="text-xl font-mono font-black text-white">{projects.length}</div>
                  </div>
                  <span className="p-2 rounded-lg bg-blue-500/10 text-blue-400">
                    <FolderGit size={18} />
                  </span>
                </div>
                <div className="bg-slate-950 border border-slate-800 rounded-xl p-4 flex items-center justify-between">
                  <div className="space-y-1">
                    <div className="text-[11px] text-slate-400 font-semibold uppercase">强制密级标记 (MAC)</div>
                    <div className="text-xl font-mono font-black text-white">{markings.length}</div>
                  </div>
                  <span className="p-2 rounded-lg bg-pink-500/10 text-pink-400">
                    <Tag size={18} />
                  </span>
                </div>
                <div className="bg-slate-950 border border-slate-800 rounded-xl p-4 flex items-center justify-between">
                  <div className="space-y-1">
                    <div className="text-[11px] text-slate-400 font-semibold uppercase">合规目的管控 (PBAC)</div>
                    <div className="text-xl font-mono font-black text-white">
                      {purposes.filter(p => p.status === 'ACTIVE').length} <span className="text-xs text-slate-500">/ {purposes.length}</span>
                    </div>
                  </div>
                  <span className="p-2 rounded-lg bg-emerald-500/10 text-emerald-400">
                    <ClipboardList size={18} />
                  </span>
                </div>
              </div>

              {/* Charts Row */}
              <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                
                {/* Recharts Evaluation Trend */}
                <div className="bg-slate-950 border border-slate-800 rounded-xl p-4 lg:col-span-2 space-y-4">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold text-slate-200 flex items-center gap-1.5">
                      <TrendingUp size={13} className="text-indigo-400" />
                      安全策略鉴权评估趋势 (Policy Evaluation Trend)
                    </span>
                    <span className="text-[10px] text-slate-500">过去7天动态安全阻断详情</span>
                  </div>
                  <div className="h-64">
                    <ResponsiveContainer width="100%" height="100%">
                      <LineChart data={evaluationTrend}>
                        <XAxis dataKey="day" stroke="#64748b" fontSize={11} tickLine={false} />
                        <YAxis stroke="#64748b" fontSize={11} tickLine={false} />
                        <ChartTooltip contentStyle={{ backgroundColor: '#020617', borderColor: '#334155', color: '#f8fafc' }} />
                        <Legend wrapperStyle={{ fontSize: 11 }} />
                        <Line type="monotone" dataKey="success" name="成功放行 (Allow)" stroke="#10b981" strokeWidth={2.5} dot={{ r: 3 }} />
                        <Line type="monotone" dataKey="denied" name="安全拦截 (Deny)" stroke="#ef4444" strokeWidth={2.5} dot={{ r: 3 }} />
                        <Line type="monotone" dataKey="warn" name="潜在风险警告 (Warn)" stroke="#f59e0b" strokeWidth={1.5} strokeDasharray="3 3" />
                      </LineChart>
                    </ResponsiveContainer>
                  </div>
                </div>

                {/* Pie Chart sensitive assets */}
                <div className="bg-slate-950 border border-slate-800 rounded-xl p-4 space-y-4 flex flex-col justify-between">
                  <div className="space-y-1">
                    <div className="text-xs font-bold text-slate-200 flex items-center gap-1.5">
                      <PieChartIcon size={13} className="text-indigo-400" />
                      安全标记保护下的高敏数据分布 (MAC Tag Distribution)
                    </div>
                    <p className="text-[10px] text-slate-500">按保密分类所涵盖的核心数源占比</p>
                  </div>
                  <div className="h-44 flex items-center justify-center relative">
                    <ResponsiveContainer width="100%" height="100%">
                      <PieChart>
                        <Pie
                          data={pieData}
                          innerRadius={50}
                          outerRadius={70}
                          paddingAngle={3}
                          dataKey="value"
                        >
                          {pieData.map((entry, index) => (
                            <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                          ))}
                        </Pie>
                        <ChartTooltip contentStyle={{ backgroundColor: '#020617', borderColor: '#334155', color: '#f8fafc' }} />
                      </PieChart>
                    </ResponsiveContainer>
                    <div className="absolute flex flex-col items-center">
                      <span className="text-xs font-bold font-mono text-slate-400">受护项</span>
                      <span className="text-lg font-black font-mono text-white">100%</span>
                    </div>
                  </div>
                  <div className="grid grid-cols-2 gap-2 text-[10px] text-slate-400">
                    {markings.map((m, idx) => (
                      <div key={m.id} className="flex items-center gap-1">
                        <span className="h-2 w-2 rounded-full shrink-0" style={{ backgroundColor: COLORS[idx % COLORS.length] }} />
                        <span className="truncate">{m.id.split('_')[1] || m.id}</span>
                      </div>
                    ))}
                  </div>
                </div>
              </div>

              {/* INTERACTIVE POLICY SIMULATOR BANNER */}
              <div className="bg-slate-950 border-2 border-indigo-900/40 rounded-xl p-5 space-y-4">
                <div className="flex items-center justify-between border-b border-slate-800 pb-3">
                  <div className="space-y-0.5">
                    <span className="text-xs font-bold text-slate-200 flex items-center gap-1.5">
                      <Zap size={14} className="text-indigo-400 animate-pulse" />
                      零信任策略仿真演算器 (Zero-Trust Security Policy Simulator)
                    </span>
                    <p className="text-[10px] text-slate-500">模拟不同用户、敏感资产和具体分析目的在 ECOS 级联策略链条中的真实决策判定结果</p>
                  </div>
                  <button
                    onClick={handleRunSimulation}
                    className="px-4 py-1.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg text-xs font-bold transition-all flex items-center gap-1 cursor-pointer"
                  >
                    <Play size={12} />
                    <span>一键仿真演算</span>
                  </button>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                  
                  {/* Inputs */}
                  <div className="space-y-3 bg-slate-900/80 p-3.5 rounded-lg border border-slate-800">
                    <div>
                      <label className="text-[10px] text-slate-400 font-bold block mb-1">模拟主体 (Subject User)</label>
                      <select
                        value={simUser}
                        onChange={(e) => setSimUser(e.target.value)}
                        className="w-full bg-slate-950 border border-slate-800 rounded px-2.5 py-1.5 text-xs text-slate-200 font-mono outline-none"
                      >
                        <option value="analyst_li">analyst_li (分析师 - HQ)</option>
                        <option value="hr_manager">hr_manager (HR主管)</option>
                        <option value="external_auditor">external_auditor (外部审计员 - Expired)</option>
                        <option value="contractor_eng">contractor_eng (外部承包商 - Isolated)</option>
                        <option value="admin_guorong">admin_guorong (超级管理员 - Super)</option>
                      </select>
                    </div>

                    <div>
                      <label className="text-[10px] text-slate-400 font-bold block mb-1">敏感资源 (Sensitive Dataset)</label>
                      <select
                        value={simDataset}
                        onChange={(e) => setSimDataset(e.target.value)}
                        className="w-full bg-slate-950 border border-slate-800 rounded px-2.5 py-1.5 text-xs text-slate-200 font-mono outline-none"
                      >
                        <option value="ds_flight_schedules">ds_flight_schedules (航班计划 - 无MAC锁)</option>
                        <option value="ds_ticket_sales">ds_ticket_sales (机票财务 - MAC: Revenue)</option>
                        <option value="ds_pilots_biography">ds_pilots_biography (机组隐私 - MAC: Pilot)</option>
                      </select>
                    </div>

                    <div>
                      <label className="text-[10px] text-slate-400 font-bold block mb-1">使用目的环境 (Purpose Context)</label>
                      <select
                        value={simPurpose}
                        onChange={(e) => setSimPurpose(e.target.value)}
                        className="w-full bg-slate-950 border border-slate-800 rounded px-2.5 py-1.5 text-xs text-slate-200 font-mono outline-none"
                      >
                        {purposes.map(p => (
                          <option key={p.id} value={p.id}>{p.name} [{p.status}]</option>
                        ))}
                      </select>
                    </div>

                    <div>
                      <label className="text-[10px] text-slate-400 font-bold block mb-1">物理客户端 IP (Client IP - Whitelist CIDR)</label>
                      <input
                        type="text"
                        value={simIp}
                        onChange={(e) => setSimIp(e.target.value)}
                        placeholder="例: 10.120.5.23"
                        className="w-full bg-slate-950 border border-slate-800 rounded px-2.5 py-1.5 text-xs text-slate-200 font-mono outline-none focus:border-indigo-500"
                      />
                      <span className="text-[8px] text-slate-500 mt-1 block">
                        HQ段: 10.120.0.0/16 | 合作段: 172.16.45.0/24 | 承包段: 202.96.128.0/24
                      </span>
                    </div>
                  </div>

                  {/* Verdict & Details */}
                  <div className="md:col-span-2 bg-slate-900/80 p-3.5 rounded-lg border border-slate-800 flex flex-col justify-between">
                    {simResult ? (
                      <div className="space-y-3">
                        <div className="flex items-center justify-between">
                          <span className="text-[10px] text-slate-400 font-bold">校验结果反馈 (Decision Result)</span>
                          <span className={`px-2 py-0.5 rounded text-[10px] font-black font-mono tracking-wider ${
                            simResult.verdict === 'GRANTED' ? 'bg-emerald-950 text-emerald-400 border border-emerald-800' : 'bg-red-950 text-red-400 border border-red-800'
                          }`}>
                            {simResult.verdict}
                          </span>
                        </div>
                        <div className="space-y-1.5 font-mono text-[10px] text-slate-300 overflow-y-auto max-h-32 pr-2">
                          {simResult.traces.map((trace, idx) => (
                            <div key={idx} className={`p-1 rounded ${
                              trace.includes('SUCCESS') || trace.includes('GRANTED') || trace.includes('通过')
                                ? 'bg-emerald-950/25 text-emerald-300' 
                                : trace.includes('DENIED') || trace.includes('拦截') || trace.includes('失败')
                                ? 'bg-red-950/25 text-red-300' 
                                : 'text-slate-300'
                            }`}>
                              {trace}
                            </div>
                          ))}
                        </div>
                      </div>
                    ) : (
                      <div className="flex-1 flex flex-col items-center justify-center text-slate-500 space-y-2 py-6">
                        <ShieldAlert size={24} className="text-slate-600" />
                        <span className="text-xs">等待仿真触发，请点击右上角「一键仿真演算」按钮</span>
                      </div>
                    )}

                    <div className="text-[9px] text-slate-500 border-t border-slate-800/60 pt-2 flex items-center gap-1.5">
                      <Info size={10} />
                      <span>本仿真完全模拟 Palantir ECOS 零信任架构下的 <strong>组织隔离 ➔ DAC ➔ MAC ➔ 目的链(PBAC)</strong> 四重复合门禁。</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* ================================================== */}
          {/* TAB 2: ORGANIZATIONS (组织架构管理) */}
          {/* ================================================== */}
          {activeTab === 'orgs' && (
            <div className="space-y-6">
              
              <div className="flex items-center justify-between">
                <div>
                  <h3 className="text-sm font-black text-slate-100">ECOS 组织边界配置 (Organizations Setup)</h3>
                  <p className="text-[10px] text-slate-400">ECOS 最高级别安全隔离机制。即使授予了文件权限，非共享协议白名单下的跨组织访问也将被绝对硬隔离阻断。</p>
                </div>
                <button
                  onClick={() => setShowAddOrgModal(true)}
                  className="px-3 py-1.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg text-xs font-bold transition-all flex items-center gap-1 cursor-pointer"
                >
                  <Plus size={13} />
                  <span>添加安全组织</span>
                </button>
              </div>

              {/* Organization List Grid */}
              <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
                {orgs.map(org => (
                  <div key={org.id} className="bg-slate-950 border border-slate-800 rounded-xl p-5 space-y-4">
                    <div className="flex items-start justify-between">
                      <div className="space-y-0.5">
                        <div className="text-xs font-extrabold text-slate-100 flex items-center gap-1.5">
                          <Globe size={13} className="text-indigo-400" />
                          <span>{org.name}</span>
                        </div>
                        <div className="text-[10px] text-slate-500 font-mono">ID: {org.id}</div>
                      </div>
                      <span className={`px-2 py-0.5 rounded text-[8px] font-black ${
                        org.isolationMode ? 'bg-red-950 text-red-400 border border-red-900/40' : 'bg-blue-950 text-blue-400 border border-blue-900/40'
                      }`}>
                        {org.isolationMode ? '严密单边隔离' : '多域弹性共享'}
                      </span>
                    </div>

                    <div className="h-px bg-slate-900" />

                    <div className="space-y-2 text-xs text-slate-300">
                      <div className="flex justify-between items-center">
                        <span className="text-slate-500">组织活跃成员:</span>
                        <span className="font-mono font-bold text-slate-200">{org.memberCount} 人</span>
                      </div>
                      <div className="flex justify-between items-center">
                        <span className="text-slate-500">已授权跨组织共享:</span>
                        <span className="font-mono text-[10px] text-indigo-400">
                          {org.crossOrgSharing.length > 0 ? org.crossOrgSharing.join(', ') : '无共享'}
                        </span>
                      </div>
                      <div className="space-y-1">
                        <div className="text-slate-500">活跃白名单IP段 (CIDR):</div>
                        <div className="flex flex-wrap gap-1">
                          {org.ipRanges.map(ip => (
                            <span key={ip} className="bg-slate-900 border border-slate-800 px-1.5 py-0.5 rounded text-[10px] font-mono text-indigo-300">
                              {ip}
                            </span>
                          ))}
                        </div>
                      </div>
                    </div>

                    <div className="flex items-center justify-end gap-1.5 pt-2">
                      <button 
                        onClick={() => {
                          const ip = prompt('输入要追加的 IP 白名单 CIDR (例 10.150.0.0/16):');
                          if (ip) {
                            setOrgs(orgs.map(o => o.id === org.id ? { ...o, ipRanges: [...o.ipRanges, ip] } : o));
                          }
                        }}
                        className="p-1 bg-slate-900 hover:bg-slate-800 rounded border border-slate-800 text-[10px] font-bold text-slate-300 cursor-pointer"
                      >
                        追加网段
                      </button>
                      <button 
                        onClick={() => {
                          setOrgs(orgs.filter(o => o.id !== org.id));
                        }}
                        className="p-1 bg-red-950/20 hover:bg-red-950 text-[10px] font-bold text-red-400 rounded cursor-pointer"
                      >
                        删除组织
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* ================================================== */}
          {/* TAB 3: DAC - PROJECTS & ROLES (项目角色权限) */}
          {/* ================================================== */}
          {activeTab === 'dac' && (
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
              
              {/* Left Projects list */}
              <div className="bg-slate-950 border border-slate-800 rounded-xl p-4 space-y-4">
                <div className="flex items-center justify-between border-b border-slate-800 pb-2">
                  <span className="text-xs font-bold text-slate-200">受控安全工程项目</span>
                  <span className="text-[9px] font-mono text-slate-500">DAC CONTROLS</span>
                </div>
                <div className="space-y-2">
                  {projects.map(proj => (
                    <button
                      key={proj.id}
                      onClick={() => setSelectedProjectId(proj.id)}
                      className={`w-full text-left p-3 rounded-lg border transition-all flex flex-col gap-1 cursor-pointer ${
                        selectedProjectId === proj.id
                          ? 'bg-slate-900 border-indigo-500/50 shadow-md shadow-indigo-500/5'
                          : 'bg-slate-950 border-slate-800 hover:bg-slate-900'
                      }`}
                    >
                      <div className="text-xs font-extrabold text-slate-100 truncate">{proj.name}</div>
                      <p className="text-[10px] text-slate-400 line-clamp-1">{proj.description}</p>
                      <div className="flex items-center gap-1.5 mt-1 text-[9px] text-slate-500">
                        <Users size={10} />
                        <span>授权成员: {proj.members.length}</span>
                        <span>•</span>
                        <span className={proj.autoPropagation ? 'text-emerald-400' : 'text-slate-500'}>
                          {proj.autoPropagation ? '沿袭传播已开' : '不传播'}
                        </span>
                      </div>
                    </button>
                  ))}
                </div>
              </div>

              {/* Right Members Detail */}
              <div className="lg:col-span-2 bg-slate-950 border border-slate-800 rounded-xl p-5 space-y-6">
                {(() => {
                  const currentProj = projects.find(p => p.id === selectedProjectId);
                  if (!currentProj) return <div className="text-slate-500 text-xs">请在左侧选择项目</div>;

                  return (
                    <div className="space-y-6">
                      <div className="flex items-start justify-between border-b border-slate-800 pb-4">
                        <div className="space-y-1">
                          <h3 className="text-sm font-black text-slate-100">{currentProj.name}</h3>
                          <p className="text-xs text-slate-400 leading-relaxed">{currentProj.description}</p>
                        </div>
                        <button
                          onClick={() => setShowAddMemberModal(true)}
                          className="px-3 py-1.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg text-xs font-bold transition-all flex items-center gap-1 shrink-0 cursor-pointer"
                        >
                          <UserPlus size={13} />
                          <span>授予成员角色</span>
                        </button>
                      </div>

                      {/* Project ACL Rules */}
                      <div className="grid grid-cols-2 gap-4">
                        <div className="bg-slate-900/60 border border-slate-800 p-3.5 rounded-lg flex items-center justify-between">
                          <div className="space-y-1">
                            <div className="text-[10px] text-slate-400 font-bold">跨组织可见性 (Discoverability)</div>
                            <div className="text-xs text-slate-200">
                              {currentProj.discoverableAllOrgs ? '允许其他组织在全局索引中检索' : '仅限本组织可见与索引'}
                            </div>
                          </div>
                          <button
                            onClick={() => {
                              setProjects(projects.map(p => p.id === currentProj.id ? { ...p, discoverableAllOrgs: !p.discoverableAllOrgs } : p));
                            }}
                            className={`p-1 px-2.5 rounded text-[10px] font-black cursor-pointer ${
                              currentProj.discoverableAllOrgs ? 'bg-indigo-950 text-indigo-400' : 'bg-slate-800 text-slate-400'
                            }`}
                          >
                            切换可见性
                          </button>
                        </div>

                        <div className="bg-slate-900/60 border border-slate-800 p-3.5 rounded-lg flex items-center justify-between">
                          <div className="space-y-1">
                            <div className="text-[10px] text-slate-400 font-bold">继承传播锁 (Auto-Inheritance)</div>
                            <div className="text-xs text-slate-200">
                              {currentProj.autoPropagation ? '下游派生项目强制自动继承此权限' : '当下游产生新工程时重置鉴权'}
                            </div>
                          </div>
                          <button
                            onClick={() => {
                              setProjects(projects.map(p => p.id === currentProj.id ? { ...p, autoPropagation: !p.autoPropagation } : p));
                            }}
                            className={`p-1 px-2.5 rounded text-[10px] font-black cursor-pointer ${
                              currentProj.autoPropagation ? 'bg-indigo-950 text-indigo-400' : 'bg-slate-800 text-slate-400'
                            }`}
                          >
                            切换沿袭
                          </button>
                        </div>
                      </div>

                      {/* Members Table */}
                      <div className="space-y-3">
                        <div className="text-xs font-bold text-slate-300 flex items-center gap-1">
                          <ShieldAlert size={13} className="text-indigo-400" />
                          <span>成员自主授权列表 (Access Control List - ACL)</span>
                        </div>
                        <div className="border border-slate-800 rounded-lg overflow-hidden bg-slate-900/30">
                          <table className="w-full text-left text-xs text-slate-300">
                            <thead className="bg-slate-950 text-slate-400 text-[10px] font-extrabold uppercase tracking-wider border-b border-slate-800">
                              <tr>
                                <th className="p-3">用户名称 (Subject)</th>
                                <th className="p-3">分配角色 (Role)</th>
                                <th className="p-3">授权执行人 (Granted By)</th>
                                <th className="p-3">授权日期 (Granted At)</th>
                                <th className="p-3 text-right">操作</th>
                              </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-800/60 font-mono">
                              {currentProj.members.map(member => (
                                <tr key={member.username} className="hover:bg-slate-900/50">
                                  <td className="p-3 font-semibold text-slate-200">{member.username}</td>
                                  <td className="p-3">
                                    <span className={`px-2 py-0.5 rounded text-[9px] font-extrabold ${
                                      member.role === 'Owner' ? 'bg-red-950 text-red-400 border border-red-900/30' :
                                      member.role === 'Editor' ? 'bg-blue-950 text-blue-400 border border-blue-900/30' :
                                      member.role === 'Viewer' ? 'bg-emerald-950 text-emerald-400 border border-emerald-900/30' :
                                      'bg-slate-800 text-slate-400'
                                    }`}>
                                      {member.role}
                                    </span>
                                  </td>
                                  <td className="p-3 text-slate-400">{member.grantedBy}</td>
                                  <td className="p-3 text-slate-500">{member.grantedAt}</td>
                                  <td className="p-3 text-right">
                                    <button
                                      onClick={() => {
                                        setProjects(projects.map(p => {
                                          if (p.id === currentProj.id) {
                                            return {
                                              ...p,
                                              members: p.members.filter(m => m.username !== member.username)
                                            };
                                          }
                                          return p;
                                        }));
                                      }}
                                      className="text-red-400 hover:text-red-300 font-bold transition-colors cursor-pointer"
                                    >
                                      吊销
                                    </button>
                                  </td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                      </div>
                    </div>
                  );
                })()}
              </div>
            </div>
          )}

          {/* ================================================== */}
          {/* TAB 4: MAC - SECURITY MARKINGS (强制密级标记) */}
          {/* ================================================== */}
          {activeTab === 'mac' && (
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
              
              {/* Left Markings List */}
              <div className="bg-slate-950 border border-slate-800 rounded-xl p-4 space-y-4">
                <div className="flex items-center justify-between border-b border-slate-800 pb-2">
                  <span className="text-xs font-bold text-slate-200 flex items-center gap-1.5">
                    <Tag size={13} className="text-pink-400" />
                    安全标记 (MAC Markings)
                  </span>
                  <button
                    onClick={() => setShowAddMarkingModal(true)}
                    className="p-1 bg-indigo-600 hover:bg-indigo-500 text-white rounded font-bold text-[10px] cursor-pointer"
                  >
                    + 新建标记
                  </button>
                </div>

                <div className="space-y-2">
                  {markings.map(m => (
                    <button
                      key={m.id}
                      onClick={() => setSelectedMarkingId(m.id)}
                      className={`w-full text-left p-3 rounded-lg border transition-all flex flex-col gap-1 cursor-pointer ${
                        selectedMarkingId === m.id
                          ? 'bg-slate-900 border-pink-500/40 shadow-md shadow-pink-500/5'
                          : 'bg-slate-950 border-slate-800 hover:bg-slate-900'
                      }`}
                    >
                      <div className="text-xs font-extrabold text-slate-100 flex items-center gap-1.5">
                        <span className="h-1.5 w-1.5 rounded-full bg-pink-500" />
                        <span>{m.id}</span>
                      </div>
                      <p className="text-[10px] text-slate-400 line-clamp-1">{m.description}</p>
                      <div className="flex items-center gap-2 mt-1.5 text-[9px] text-slate-500">
                        <span className="bg-slate-800 px-1 py-0.2 rounded font-mono text-slate-300">{m.classificationLevel}</span>
                        <span>锁定数据集: {m.appliedDatasets.length}</span>
                      </div>
                    </button>
                  ))}
                </div>
              </div>

              {/* Right Detail & Lineage Simulator */}
              <div className="lg:col-span-2 bg-slate-950 border border-slate-800 rounded-xl p-5 space-y-6">
                {(() => {
                  const currentMark = markings.find(m => m.id === selectedMarkingId);
                  if (!currentMark) return <div className="text-slate-500 text-xs">请选择一个安全标记</div>;

                  return (
                    <div className="space-y-6">
                      
                      <div className="border-b border-slate-800 pb-4 space-y-1.5">
                        <div className="flex items-center justify-between">
                          <span className="text-[10px] bg-pink-950/60 text-pink-400 border border-pink-900/40 px-2 py-0.5 rounded font-mono font-bold uppercase">
                            {currentMark.classificationLevel} 级密文安全标记
                          </span>
                          <span className="text-[10px] text-slate-500">API Key: {currentMark.apiName}</span>
                        </div>
                        <h3 className="text-sm font-black text-slate-100">{currentMark.displayName}</h3>
                        <p className="text-xs text-slate-400 leading-relaxed">{currentMark.description}</p>
                      </div>

                      {/* Detail attributes */}
                      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div className="bg-slate-900/60 border border-slate-800 p-3.5 rounded-lg space-y-2">
                          <span className="text-[10px] text-slate-400 font-bold block">获授此标记的用户组 (Authorized Recipient Groups)</span>
                          <div className="flex flex-wrap gap-1.5 pt-1">
                            {currentMark.grantedGroups.map(grp => (
                              <span key={grp} className="bg-slate-950 border border-slate-800 px-2 py-0.5 rounded text-[10px] font-mono text-pink-300">
                                {grp}
                              </span>
                            ))}
                            <button
                              onClick={() => {
                                const newGrp = prompt('输入要追加授权的 LDAP 用户组名称:');
                                if (newGrp) {
                                  setMarkings(markings.map(m => m.id === currentMark.id ? { ...m, grantedGroups: [...m.grantedGroups, newGrp] } : m));
                                }
                              }}
                              className="text-pink-400 hover:text-pink-300 font-bold text-[10px] px-1 bg-slate-950/50 rounded hover:bg-slate-900 cursor-pointer"
                            >
                              + 增授
                            </button>
                          </div>
                        </div>

                        <div className="bg-slate-900/60 border border-slate-800 p-3.5 rounded-lg space-y-2">
                          <span className="text-[10px] text-slate-400 font-bold block">当前锚定的根源数据集 (Anchored Root Datasets)</span>
                          <div className="flex flex-wrap gap-1.5 pt-1">
                            {currentMark.appliedDatasets.map(ds => (
                              <span key={ds} className="bg-slate-950 border border-slate-800 px-2 py-0.5 rounded text-[10px] font-mono text-slate-300">
                                {ds}
                              </span>
                            ))}
                          </div>
                        </div>
                      </div>

                      {/* Lineage Propagation simulator graph */}
                      <div className="bg-slate-900/40 border border-slate-800 rounded-lg p-4 space-y-4">
                        <div className="space-y-0.5">
                          <span className="text-xs font-bold text-slate-200 flex items-center gap-1.5">
                            <Workflow size={13} className="text-pink-400" />
                            Palantir 沿袭安全级联传递图 (Marking Propagation & Lineage Tracker)
                          </span>
                          <p className="text-[10px] text-slate-500">展现标记由于向下衍生转换，从而在底层管道中对下游节点产生的自动化锁定传染效果</p>
                        </div>

                        <div className="flex flex-col md:flex-row items-center justify-between gap-4 p-4 bg-slate-950/80 rounded border border-slate-800 relative">
                          
                          {/* Node 1 */}
                          <div className="flex-1 text-center p-3 rounded bg-slate-900 border-2 border-slate-800 relative z-10 flex flex-col items-center gap-1">
                            <Database size={16} className="text-slate-400" />
                            <span className="text-[10px] font-bold text-slate-300">根源数据集</span>
                            <span className="text-[8px] font-mono text-slate-500">ds_aviation_raw</span>
                            <div className="mt-1.5 flex items-center gap-1 text-[8px] bg-pink-950/60 text-pink-400 border border-pink-900/30 px-1 rounded">
                              <Tag size={8} />
                              <span>已打标锁</span>
                            </div>
                          </div>

                          {/* Arrow */}
                          <div className="text-slate-700 font-bold flex items-center justify-center">
                            <ArrowRight size={14} className="rotate-90 md:rotate-0" />
                          </div>

                          {/* Node 2 */}
                          <div className="flex-1 text-center p-3 rounded bg-slate-900 border-2 border-pink-800/40 relative z-10 flex flex-col items-center gap-1 shadow-md shadow-pink-950/10">
                            <Cpu size={16} className="text-pink-400" />
                            <span className="text-[10px] font-bold text-slate-300">清洗同步管道</span>
                            <span className="text-[8px] font-mono text-slate-500">sync_aviation_cleansed</span>
                            <div className="mt-1.5 text-[8px] text-pink-400 font-extrabold flex items-center gap-0.5">
                              <span className="h-1 w-1 rounded-full bg-pink-500 animate-ping" />
                              <span>安全自动穿透 (Inherited)</span>
                            </div>
                          </div>

                          {/* Arrow */}
                          <div className="text-slate-700 font-bold flex items-center justify-center">
                            <ArrowRight size={14} className="rotate-90 md:rotate-0" />
                          </div>

                          {/* Node 3 */}
                          <div className="flex-1 text-center p-3 rounded bg-slate-900 border-2 border-pink-700/60 relative z-10 flex flex-col items-center gap-1 shadow-lg shadow-pink-950/20">
                            <Workflow size={16} className="text-pink-400 animate-pulse" />
                            <span className="text-[10px] font-bold text-slate-200">派生本体对象</span>
                            <span className="text-[8px] font-mono text-slate-500">Aviation_Object_Derived</span>
                            <div className="mt-1.5 flex items-center gap-1 text-[8px] bg-red-950 text-red-400 border border-red-900/30 px-1 rounded font-black">
                              <ShieldAlert size={8} />
                              <span>终点级联锁定</span>
                            </div>
                          </div>
                        </div>

                        <p className="text-[9px] text-slate-500 text-center italic">
                          * ECOS 安全标准：任何衍生、转化、联结该根源数源的数据项都将自动继承 {currentMark.id}，无法通过普通视图清洗逻辑逃逸。
                        </p>
                      </div>
                    </div>
                  );
                })()}
              </div>
            </div>
          )}

          {/* ================================================== */}
          {/* TAB 5: PBAC - PURPOSE BASED ACCESS CONTROL (基于用途) */}
          {/* ================================================== */}
          {activeTab === 'pbac' && (
            <div className="space-y-6">
              
              <div className="flex items-center justify-between">
                <div>
                  <h3 className="text-sm font-black text-slate-100">基于分析用途授权控制 (Purpose-Based Access Control - PBAC)</h3>
                  <p className="text-[10px] text-slate-400">只有定义合法、合规的数据分析「目的」(Purpose)，才能获得临时的密文解密密钥。任何违背目的的导出或越权行为将被严厉记录。</p>
                </div>
                <button
                  onClick={() => setShowAddPurposeModal(true)}
                  className="px-3 py-1.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg text-xs font-bold transition-all flex items-center gap-1 cursor-pointer"
                >
                  <Plus size={13} />
                  <span>添加合规用途项目</span>
                </button>
              </div>

              {/* Purpose Cards Grid */}
              <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
                {purposes.map(p => (
                  <div key={p.id} className="bg-slate-950 border border-slate-800 rounded-xl p-5 space-y-4">
                    <div className="flex items-start justify-between">
                      <span className={`px-2 py-0.5 rounded text-[8px] font-black ${
                        p.status === 'ACTIVE' ? 'bg-emerald-950 text-emerald-400 border border-emerald-900/30' :
                        p.status === 'EXPIRED' ? 'bg-red-950 text-red-400 border border-red-900/30' :
                        'bg-slate-800 text-slate-400'
                      }`}>
                        {p.status}
                      </span>
                      <span className="text-[9px] text-slate-500 font-mono">ID: {p.id}</span>
                    </div>

                    <div className="space-y-1">
                      <div className="text-xs font-extrabold text-slate-100">{p.name}</div>
                      <p className="text-[10px] text-slate-400 leading-relaxed line-clamp-2">{p.description}</p>
                    </div>

                    <div className="h-px bg-slate-900" />

                    <div className="space-y-2 text-[10px] text-slate-300">
                      <div>
                        <span className="text-slate-500">特许执行人员:</span>
                        <div className="flex flex-wrap gap-1 mt-1">
                          {p.authorizedUsers.map(u => (
                            <span key={u} className="bg-slate-900 px-1.5 py-0.2 rounded font-mono text-indigo-300">
                              {u}
                            </span>
                          ))}
                        </div>
                      </div>

                      <div>
                        <span className="text-slate-500">锚定输入数据集:</span>
                        <div className="flex flex-wrap gap-1 mt-1">
                          {p.inputDatasets.map(ds => (
                            <span key={ds} className="bg-slate-900 px-1.5 py-0.2 rounded font-mono text-slate-300">
                              {ds}
                            </span>
                          ))}
                        </div>
                      </div>

                      <div>
                        <span className="text-slate-500">强制动态脱敏要求:</span>
                        <div className="flex flex-wrap gap-1 mt-1">
                          {p.redactionRules.map(rule => (
                            <span key={rule} className="bg-pink-950/30 text-pink-400 border border-pink-900/20 px-1.5 py-0.2 rounded font-mono">
                              {rule}
                            </span>
                          ))}
                        </div>
                      </div>

                      <div className="flex items-center justify-between text-[9px] border-t border-slate-900 pt-2 text-slate-500">
                        <span>届期截至日期:</span>
                        <span className="font-mono text-slate-400 font-semibold">{p.expiresAt}</span>
                      </div>
                    </div>

                    <div className="flex items-center justify-end gap-1.5 pt-1">
                      {p.status === 'EXPIRED' ? (
                        <button
                          onClick={() => {
                            setPurposes(purposes.map(purp => purp.id === p.id ? { ...purp, status: 'ACTIVE', expiresAt: '2027-01-01' } : purp));
                          }}
                          className="p-1 px-2.5 bg-indigo-900/40 hover:bg-indigo-900 text-[10px] font-bold text-indigo-300 rounded cursor-pointer"
                        >
                          重新激活
                        </button>
                      ) : (
                        <button
                          onClick={() => {
                            setPurposes(purposes.map(purp => purp.id === p.id ? { ...purp, status: 'EXPIRED' } : purp));
                          }}
                          className="p-1 px-2.5 bg-red-950/20 hover:bg-red-950 text-[10px] font-bold text-red-400 rounded cursor-pointer"
                        >
                          立即吊销目的
                        </button>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* ================================================== */}
          {/* TAB 6: ROW & COLUMN LEVEL POLICIES (行列级安全策略) */}
          {/* ================================================== */}
          {activeTab === 'row_col' && (
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
              
              {/* Left Datasets with custom security settings */}
              <div className="bg-slate-950 border border-slate-800 rounded-xl p-4 space-y-4">
                <div className="flex items-center justify-between border-b border-slate-800 pb-2">
                  <span className="text-xs font-bold text-slate-200">受动态策略约束的数据表</span>
                  <span className="text-[9px] font-mono text-slate-500">ROW/COL CONFIGS</span>
                </div>
                <div className="space-y-2">
                  {rowColPolicies.map(item => (
                    <button
                      key={item.datasetId}
                      onClick={() => setSelectedRowColDs(item.datasetId)}
                      className={`w-full text-left p-3 rounded-lg border transition-all flex flex-col gap-1 cursor-pointer ${
                        selectedRowColDs === item.datasetId
                          ? 'bg-slate-900 border-indigo-500/50 shadow-md shadow-indigo-500/5'
                          : 'bg-slate-950 border-slate-800 hover:bg-slate-900'
                      }`}
                    >
                      <div className="text-xs font-extrabold text-slate-100 truncate">{item.datasetName}</div>
                      <div className="flex items-center gap-2 mt-1.5 text-[9px] text-slate-500">
                        <span>列脱敏: {item.columnMasks.filter(c => c.active && c.maskType !== 'NONE').length}项</span>
                        <span>•</span>
                        <span>行过滤: {item.rowFilters.filter(r => r.active).length}条</span>
                      </div>
                    </button>
                  ))}
                </div>
              </div>

              {/* Right Detail configuration & Interactive Preview */}
              <div className="lg:col-span-2 bg-slate-950 border border-slate-800 rounded-xl p-5 space-y-6">
                {(() => {
                  const currentPolicy = rowColPolicies.find(p => p.datasetId === selectedRowColDs);
                  if (!currentPolicy) return <div className="text-slate-500 text-xs">请选择数据集</div>;

                  return (
                    <div className="space-y-6">
                      
                      <div className="border-b border-slate-800 pb-4">
                        <h3 className="text-sm font-black text-slate-100">{currentPolicy.datasetName}</h3>
                        <p className="text-[10px] text-slate-400">在此处针对特定的列项设置动态遮蔽(SHA256哈希、全部抹除或部分脱敏)，或添加SQL风格的行级隔离条件。</p>
                      </div>

                      {/* Column Masking section */}
                      <div className="space-y-3">
                        <div className="text-xs font-bold text-slate-200 flex items-center gap-1.5">
                          <EyeOff size={13} className="text-indigo-400" />
                          <span>列级敏感字段遮蔽规则 (Dynamic Column Masking)</span>
                        </div>
                        <div className="border border-slate-800 rounded-lg overflow-hidden bg-slate-900/40">
                          <table className="w-full text-left text-xs text-slate-300">
                            <thead className="bg-slate-950 text-slate-400 text-[10px] font-extrabold uppercase tracking-wider border-b border-slate-800">
                              <tr>
                                <th className="p-3">列字段名</th>
                                <th className="p-3">脱敏手段</th>
                                <th className="p-3">运行状态</th>
                                <th className="p-3 text-right">切换开关</th>
                              </tr>
                            </thead>
                            <tbody className="divide-y divide-slate-800/60 font-mono text-[11px]">
                              {currentPolicy.columnMasks.map(col => (
                                <tr key={col.column} className="hover:bg-slate-900/30">
                                  <td className="p-3 font-semibold text-slate-200">{col.column}</td>
                                  <td className="p-3">
                                    <span className={`px-2 py-0.5 rounded text-[9px] font-bold ${
                                      col.maskType === 'REDACT' ? 'bg-red-950 text-red-400 border border-red-900/30' :
                                      col.maskType === 'SHA256' ? 'bg-orange-950 text-orange-400 border border-orange-900/30' :
                                      col.maskType === 'PARTIAL' ? 'bg-blue-950 text-blue-400 border border-blue-900/30' :
                                      'bg-slate-800 text-slate-400'
                                    }`}>
                                      {col.maskType === 'NONE' ? '不遮蔽 (NONE)' : col.maskType}
                                    </span>
                                  </td>
                                  <td className="p-3">
                                    <span className={`h-1.5 w-1.5 inline-block rounded-full mr-1.5 ${col.active && col.maskType !== 'NONE' ? 'bg-emerald-400 animate-pulse' : 'bg-slate-500'}`} />
                                    <span>{col.active && col.maskType !== 'NONE' ? '已拦截脱敏中' : '明文放行'}</span>
                                  </td>
                                  <td className="p-3 text-right">
                                    <button
                                      disabled={col.maskType === 'NONE'}
                                      onClick={() => handleToggleColumnMask(currentPolicy.datasetId, col.column)}
                                      className={`p-1 px-2 text-[10px] font-bold rounded cursor-pointer ${
                                        col.maskType === 'NONE' ? 'opacity-30 cursor-not-allowed' :
                                        col.active ? 'bg-emerald-950 text-emerald-400 hover:bg-emerald-900' : 'bg-slate-800 text-slate-400 hover:bg-slate-700'
                                      }`}
                                    >
                                      {col.active ? '激活生效' : '已关闭'}
                                    </button>
                                  </td>
                                </tr>
                              ))}
                            </tbody>
                          </table>
                        </div>
                      </div>

                      {/* Row Filters */}
                      <div className="space-y-3">
                        <div className="text-xs font-bold text-slate-200 flex items-center gap-1.5">
                          <Filter size={13} className="text-indigo-400" />
                          <span>行级动态物理隔离策略 (Row-Level Security Policies)</span>
                        </div>
                        {currentPolicy.rowFilters.length === 0 ? (
                          <div className="text-center p-6 bg-slate-900/30 border border-slate-800 rounded-lg text-slate-500 text-xs">
                            当前数据集没有添加任何行过滤策略。
                          </div>
                        ) : (
                          <div className="space-y-2">
                            {currentPolicy.rowFilters.map((filter, idx) => (
                              <div key={idx} className="bg-slate-900/60 border border-slate-800 p-3.5 rounded-lg flex items-center justify-between">
                                <div className="space-y-1">
                                  <div className="text-[10px] text-indigo-400 font-mono font-bold">SQL FILTER: {filter.filterSql}</div>
                                  <p className="text-[10px] text-slate-400">{filter.description}</p>
                                </div>
                                <button
                                  onClick={() => handleToggleRowFilter(currentPolicy.datasetId, idx)}
                                  className={`p-1.5 rounded text-[10px] font-black cursor-pointer ${
                                    filter.active ? 'bg-emerald-950 text-emerald-400 border border-emerald-900/40' : 'bg-slate-800 text-slate-400'
                                  }`}
                                >
                                  {filter.active ? '过滤激活中' : '未开启'}
                                </button>
                              </div>
                            ))}
                          </div>
                        )}
                      </div>

                      {/* Dynamic Preview Simulation */}
                      <div className="bg-slate-900/30 border border-slate-800 rounded-lg p-4 space-y-3.5">
                        <div className="flex items-center justify-between border-b border-slate-800/60 pb-2">
                          <span className="text-[10px] font-bold text-slate-400 uppercase tracking-wider">
                            当前脱敏输出预览 (Dynamic Masking Simulation Preview)
                          </span>
                          <span className="text-[8px] bg-slate-800 px-1.5 py-0.5 rounded text-slate-500 font-mono">
                            Based on Active Policies
                          </span>
                        </div>

                        {/* Interactive columns view */}
                        {currentPolicy.datasetId === 'ds_flight_schedules' ? (
                          <div className="font-mono text-[10px] text-slate-300 space-y-2">
                            <div className="grid grid-cols-3 gap-2 bg-slate-950 p-2 rounded text-slate-400 font-bold border-b border-slate-800">
                              <span>航班号 (flight_num)</span>
                              <span>机长ID (captain_id)</span>
                              <span>延误时长 (delay_minutes)</span>
                            </div>
                            <div className="grid grid-cols-3 gap-2 p-2 rounded bg-slate-900/80 hover:bg-slate-900">
                              <span>CA-1209</span>
                              <span className="text-orange-400">
                                {currentPolicy.columnMasks.find(c => c.column === 'captain_id')?.active ? '0x8f7d9a1c... (SHA256)' : 'captain_john_09'}
                              </span>
                              <span>12 分钟</span>
                            </div>
                            <div className="grid grid-cols-3 gap-2 p-2 rounded bg-slate-900/80 hover:bg-slate-900">
                              <span>MU-3342</span>
                              <span className="text-orange-400">
                                {currentPolicy.columnMasks.find(c => c.column === 'captain_id')?.active ? '0xa2f10b89... (SHA256)' : 'captain_lee_32'}
                              </span>
                              <span>3 分钟</span>
                            </div>
                          </div>
                        ) : currentPolicy.datasetId === 'ds_pilots_biography' ? (
                          <div className="font-mono text-[10px] text-slate-300 space-y-2">
                            <div className="grid grid-cols-3 gap-2 bg-slate-950 p-2 rounded text-slate-400 font-bold border-b border-slate-800">
                              <span>身份证号 (ssn_number)</span>
                              <span>邮箱地址 (email_address)</span>
                              <span>基本月薪 (base_salary)</span>
                            </div>
                            <div className="grid grid-cols-3 gap-2 p-2 rounded bg-slate-900/80 hover:bg-slate-900">
                              <span className="text-red-400">
                                {currentPolicy.columnMasks.find(c => c.column === 'ssn_number')?.active ? '[REDACTED] (绝对遮蔽)' : '11010119881023001X'}
                              </span>
                              <span className="text-blue-400">
                                {currentPolicy.columnMasks.find(c => c.column === 'email_address')?.active ? 'jo***@aviation.com' : 'john.smith@aviation.com'}
                              </span>
                              <span className="text-red-400">
                                {currentPolicy.columnMasks.find(c => c.column === 'base_salary')?.active ? '[REDACTED] (绝对遮蔽)' : '¥45,000'}
                              </span>
                            </div>
                          </div>
                        ) : (
                          <div className="text-center py-4 text-slate-500 text-xs">暂无可用预览行数据</div>
                        )}
                      </div>
                    </div>
                  );
                })()}
              </div>
            </div>
          )}

          {/* ================================================== */}
          {/* TAB 7: SECURITY AUDIT LOGS (安全审计日志) */}
          {/* ================================================== */}
          {activeTab === 'audit' && (
            <div className="space-y-6">
              
              <div className="flex items-center justify-between border-b border-slate-800 pb-4">
                <div className="space-y-1">
                  <h3 className="text-sm font-black text-slate-100 flex items-center gap-2">
                    <Activity size={15} className="text-indigo-400" />
                    安全防护实时审计日志 (Real-Time Dynamic Security Audit Trails)
                  </h3>
                  <p className="text-[10px] text-slate-400">
                    捕获并永久存证所有针对敏感数据集、操作行为和安全标记的判定行为，每隔 6 秒自动同步评估引擎记录。
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <button
                    onClick={openDiagnosticReport}
                    className="px-3 py-1.5 bg-emerald-600 hover:bg-emerald-500 text-white rounded-lg text-xs font-bold transition-all flex items-center gap-1.5 cursor-pointer shadow-md"
                  >
                    <ShieldAlert size={13} />
                    <span>系统安全扫描诊断书</span>
                  </button>
                  <button
                    onClick={() => {
                      alert('安全合规PDF报告已自动生成并安全加密打包至本地，哈希值已写入审计底座！');
                    }}
                    className="px-3 py-1.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg text-xs font-bold transition-all flex items-center gap-1.5 cursor-pointer shadow-md"
                  >
                    <Download size={13} />
                    <span>导出安全合规报告</span>
                  </button>
                </div>
              </div>

              {/* Logs Table */}
              <div className="border border-slate-800 rounded-xl overflow-hidden bg-slate-950">
                <table className="w-full text-left text-xs text-slate-300">
                  <thead className="bg-slate-950 text-slate-400 text-[10px] font-extrabold uppercase tracking-wider border-b border-slate-800">
                    <tr>
                      <th className="p-3">事件时间</th>
                      <th className="p-3">访问主体</th>
                      <th className="p-3">归属组织</th>
                      <th className="p-3">操作行为</th>
                      <th className="p-3">受控资源</th>
                      <th className="p-3">判定结果</th>
                      <th className="p-3">合规评估明细</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/60 font-mono text-[11px]">
                    {auditLogs.map(log => (
                      <tr key={log.id} className="hover:bg-slate-900/30 transition-colors">
                        <td className="p-3 text-indigo-400/80 font-bold">{log.timestamp}</td>
                        <td className="p-3 text-slate-200">{log.username}</td>
                        <td className="p-3 text-slate-400">{log.orgId.split('_')[1] || log.orgId}</td>
                        <td className="p-3 text-slate-300 font-semibold">{log.action}</td>
                        <td className="p-3 text-slate-400">
                          <span className="text-[10px] text-indigo-300 font-bold bg-indigo-950/20 px-1 py-0.2 rounded border border-indigo-900/10 mr-1.5">
                            {log.resourceType}
                          </span>
                          {log.resourceId}
                        </td>
                        <td className="p-3">
                          <span className={`px-2 py-0.5 rounded text-[8px] font-black ${
                            log.status === 'SUCCESS' ? 'bg-emerald-950 text-emerald-400 border border-emerald-900/30' :
                            log.status === 'DENIED' ? 'bg-red-950 text-red-400 border border-red-900/30' :
                            'bg-amber-950 text-amber-400 border border-amber-900/30'
                          }`}>
                            {log.status}
                          </span>
                        </td>
                        <td className="p-3 text-slate-400 max-w-sm truncate" title={log.details}>
                          {log.details}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {/* ================================================== */}
          {/* TAB 8: ADMINISTRATOR OPERATION MANUAL (管理员操作手册) */}
          {/* ================================================== */}
          {activeTab === 'guide' && (
            <div className="space-y-6 animate-fadeIn">
              <div className="flex items-center justify-between border-b border-slate-800 pb-4">
                <div className="space-y-1">
                  <h3 className="text-sm font-black text-slate-100 flex items-center gap-2">
                    <BookOpen size={15} className="text-indigo-400" />
                    ECOS 零信任防御架构 - 安全中心系统管理员操作手册 (Security Operations Manual)
                  </h3>
                  <p className="text-[10px] text-slate-400">
                    本手册专为初级系统管理员设计，帮助其在极短时间内掌握组织初始化、项目授权、数据打标、行列脱敏与自适应审计的闭环防御能力。
                  </p>
                </div>
                <div className="flex items-center gap-1.5 text-[10px] font-mono text-indigo-400 bg-indigo-950/40 border border-indigo-900/30 px-3 py-1 rounded-lg">
                  <span className="h-1.5 w-1.5 rounded-full bg-indigo-400 animate-ping inline-block mr-1"></span>
                  <span>管理员: admin_guorong (超级特权模式)</span>
                </div>
              </div>

              {/* Sub-navigation inside guide tab */}
              <div className="flex items-center gap-1.5 border-b border-slate-800/80 pb-3 overflow-x-auto scrollbar-none">
                <button
                  onClick={() => setGuideSection('arch')}
                  className={`px-3 py-1.5 text-[11px] rounded-lg font-bold transition-all flex items-center gap-1.5 cursor-pointer whitespace-nowrap ${
                    guideSection === 'arch' ? 'bg-indigo-600 text-white shadow' : 'bg-slate-950 text-slate-400 hover:text-slate-200 hover:bg-slate-900'
                  }`}
                >
                  <Shield size={12} />
                  <span>核心安全管道机制</span>
                </button>
                <button
                  onClick={() => setGuideSection('org')}
                  className={`px-3 py-1.5 text-[11px] rounded-lg font-bold transition-all flex items-center gap-1.5 cursor-pointer whitespace-nowrap ${
                    guideSection === 'org' ? 'bg-indigo-600 text-white shadow' : 'bg-slate-950 text-slate-400 hover:text-slate-200 hover:bg-slate-900'
                  }`}
                >
                  <Globe size={12} />
                  <span>1. 组织初始化隔离</span>
                </button>
                <button
                  onClick={() => setGuideSection('dac')}
                  className={`px-3 py-1.5 text-[11px] rounded-lg font-bold transition-all flex items-center gap-1.5 cursor-pointer whitespace-nowrap ${
                    guideSection === 'dac' ? 'bg-indigo-600 text-white shadow' : 'bg-slate-950 text-slate-400 hover:text-slate-200 hover:bg-slate-900'
                  }`}
                >
                  <FolderGit size={12} />
                  <span>2. 项目自主授权 (DAC)</span>
                </button>
                <button
                  onClick={() => setGuideSection('mac')}
                  className={`px-3 py-1.5 text-[11px] rounded-lg font-bold transition-all flex items-center gap-1.5 cursor-pointer whitespace-nowrap ${
                    guideSection === 'mac' ? 'bg-indigo-600 text-white shadow' : 'bg-slate-950 text-slate-400 hover:text-slate-200 hover:bg-slate-900'
                  }`}
                >
                  <Tag size={12} />
                  <span>3. 强制标记传播 (MAC)</span>
                </button>
                <button
                  onClick={() => setGuideSection('pbac')}
                  className={`px-3 py-1.5 text-[11px] rounded-lg font-bold transition-all flex items-center gap-1.5 cursor-pointer whitespace-nowrap ${
                    guideSection === 'pbac' ? 'bg-indigo-600 text-white shadow' : 'bg-slate-950 text-slate-400 hover:text-slate-200 hover:bg-slate-900'
                  }`}
                >
                  <EyeOff size={12} />
                  <span>4. 目的授权与脱敏 (PBAC)</span>
                </button>
                <button
                  onClick={() => setGuideSection('audit')}
                  className={`px-3 py-1.5 text-[11px] rounded-lg font-bold transition-all flex items-center gap-1.5 cursor-pointer whitespace-nowrap ${
                    guideSection === 'audit' ? 'bg-indigo-600 text-white shadow' : 'bg-slate-950 text-slate-400 hover:text-slate-200 hover:bg-slate-900'
                  }`}
                >
                  <Activity size={12} />
                  <span>5. 闭环审计与自检测</span>
                </button>
              </div>

              {/* Guide Segment Details */}
              <div className="bg-slate-950 border border-slate-800 rounded-xl p-5 space-y-4">
                
                {/* 1. Core Architecture */}
                {guideSection === 'arch' && (
                  <div className="space-y-4">
                    <div className="flex items-center gap-2 border-b border-slate-800 pb-2">
                      <span className="p-1.5 rounded-md bg-indigo-500/10 text-indigo-400">
                        <Shield size={14} />
                      </span>
                      <h4 className="text-xs font-black text-slate-200 uppercase tracking-wider">ECOS 四层深度防御物理/逻辑校验链路</h4>
                    </div>
                    
                    <p className="text-xs text-slate-400 leading-relaxed">
                      Palantir ECOS 的安全底座不是靠单一防火墙或密保，而是依靠一套**零信任动态研判解密管道 (Zero-Trust Decryption Pipeline)**。
                      当任何用户尝试读取或查询核心数据集时，零信任网关会顺次执行以下 **四个安全关卡** 校验，全部通过才能实现实时无损流式解密：
                    </p>

                    <div className="grid grid-cols-1 md:grid-cols-4 gap-4 pt-2">
                      <div className="p-3 bg-slate-900 border border-slate-800 rounded-lg space-y-2">
                        <div className="flex items-center gap-1.5 text-xs font-bold text-slate-200">
                          <span className="h-4 w-4 rounded-full bg-slate-800 border border-slate-700 text-[10px] font-mono flex items-center justify-center text-indigo-400">1</span>
                          <span>组织网关 (Org Ingress)</span>
                        </div>
                        <p className="text-[10px] text-slate-400 font-normal">
                          检查用户所处物理终端 IP 是否符合其所属组织绑定的 CIDR 信任网段。阻断跨组织物理泄露。
                        </p>
                      </div>

                      <div className="p-3 bg-slate-900 border border-slate-800 rounded-lg space-y-2">
                        <div className="flex items-center gap-1.5 text-xs font-bold text-slate-200">
                          <span className="h-4 w-4 rounded-full bg-slate-800 border border-slate-700 text-[10px] font-mono flex items-center justify-center text-indigo-400">2</span>
                          <span>项目容器 (DAC ACL)</span>
                        </div>
                        <p className="text-[10px] text-slate-400 font-normal">
                          确认用户在项目目录上拥有相应角色（Owner/Editor/Viewer/Discoverer）。控制粗粒度读取资产。
                        </p>
                      </div>

                      <div className="p-3 bg-slate-900 border border-slate-800 rounded-lg space-y-2">
                        <div className="flex items-center gap-1.5 text-xs font-bold text-slate-200">
                          <span className="h-4 w-4 rounded-full bg-slate-800 border border-slate-700 text-[10px] font-mono flex items-center justify-center text-indigo-400">3</span>
                          <span>强制打标 (MAC Locks)</span>
                        </div>
                        <p className="text-[10px] text-slate-400 font-normal">
                          识别目标资产是否被打上安全标记。即使在项目内，如未拥有特定特许安全组标签（MAC），亦将被无情拦截。
                        </p>
                      </div>

                      <div className="p-3 bg-slate-900 border border-slate-800 rounded-lg space-y-2">
                        <div className="flex items-center gap-1.5 text-xs font-bold text-slate-200">
                          <span className="h-4 w-4 rounded-full bg-slate-800 border border-slate-700 text-[10px] font-mono flex items-center justify-center text-indigo-400">4</span>
                          <span>合规用途 (PBAC Rule)</span>
                        </div>
                        <p className="text-[10px] text-slate-400 font-normal">
                          校验当前操作是否关联了处于有效期内的“合规分析目的用途”。并在最终流式输出层应用行级过滤与列遮蔽。
                        </p>
                      </div>
                    </div>

                    <div className="bg-slate-900 border-l-4 border-indigo-500 p-3.5 rounded-r-lg space-y-1 font-mono text-[10px] text-slate-300">
                      <div className="text-indigo-400 font-bold mb-1">// 零信任解密网关判定逻辑伪代码示例：</div>
                      <div>const verdict = evaluateIngressIp(clientIp, user.org)</div>
                      <div>&nbsp;&nbsp;&nbsp;&nbsp;&amp;&amp; evaluateDac(user, project)</div>
                      <div>&nbsp;&nbsp;&nbsp;&nbsp;&amp;&amp; evaluateMac(user.securityGroups, dataset.markings)</div>
                      <div>&nbsp;&nbsp;&nbsp;&nbsp;&amp;&amp; evaluatePbac(user, purpose, dataset);</div>
                      <div className="text-slate-500 pt-1">if (verdict === GRANTED) {"{"} return decryptStream(cipherText, rowFilters, columnMasks); {"}"}</div>
                    </div>
                  </div>
                )}

                {/* 2. Organization Setup */}
                {guideSection === 'org' && (
                  <div className="space-y-4">
                    <div className="flex items-center gap-2 border-b border-slate-800 pb-2">
                      <span className="p-1.5 rounded-md bg-emerald-500/10 text-emerald-400">
                        <Globe size={14} />
                      </span>
                      <h4 className="text-xs font-black text-slate-200 uppercase tracking-wider">第 1 关：组织隔离架构初始化 & 信任域注册</h4>
                    </div>

                    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 text-xs text-slate-400">
                      <div className="lg:col-span-2 space-y-3 leading-relaxed">
                        <p className="font-normal">
                          组织（Security Organization）是 ECOS 里的最高安全边界。一个用户只能属于一个主权组织，而数据可以打上组织物理隔离标签。
                        </p>
                        <h5 className="font-bold text-slate-200">🌐 隔离模式详解：</h5>
                        <ul className="list-disc list-inside space-y-1 text-[11px] pl-2 font-normal">
                          <li>
                            <strong className="text-emerald-400">逻辑共享隔离</strong>: 允许跨组织数据通过严格授权相互合并，主要在数据集或项目层面施加 ACL 授权，便于集团协同。
                          </li>
                          <li>
                            <strong className="text-indigo-400">物理高耸阻断 (IsolationMode)</strong>: 彻底封闭一切跨部门关联！该组织下的项目在全局目录中不可被其他组织成员通过任何手段搜索到（甚至通过 SQL JOIN 或派生继承都无法带走），常用于国家安全隔离域、境外特别管制运营部门等。
                          </li>
                        </ul>
                        <h5 className="font-bold text-slate-200">🔒 CIDR IP信任限制：</h5>
                        <p className="text-[11px] font-normal">
                          当添加组织时，系统管理员必须指定一个或多个默认的 **IP网段限制 (CIDR)**。任何不处于此 IP 网段的访问请求，即使密码正确、令牌有效，也会被组织网关阻断。
                        </p>
                      </div>

                      <div className="bg-slate-900 border border-slate-800 p-4 rounded-xl space-y-3 text-[11px]">
                        <div className="text-slate-200 font-bold border-b border-slate-800 pb-1 flex items-center gap-1.5">
                          <CheckSquare size={12} className="text-emerald-400" />
                          <span>初级管理员配置清单</span>
                        </div>
                        <ol className="space-y-2 list-decimal list-inside pl-1 text-slate-400 font-normal">
                          <li>进入左侧「<span className="text-slate-200">组织隔离架构</span>」面板。</li>
                          <li>点击“新增安全隔离域”，注册新组织，分配一个不冲突的 ID（如 <code className="text-emerald-400 font-mono text-[10px]">Org_Aviation_EU</code>）。</li>
                          <li>设定可信 IP 地址块，例：<code className="text-indigo-400 font-mono text-[10px]">10.120.0.0/16</code>。</li>
                          <li>针对高保密合规要求（如 GDPR 强制本地阻断），勾选 <span className="text-amber-400 font-bold">“物理高耸隔离”</span>，阻断一切跨域血缘追溯。</li>
                        </ol>
                      </div>
                    </div>
                  </div>
                )}

                {/* 3. Project DAC */}
                {guideSection === 'dac' && (
                  <div className="space-y-4">
                    <div className="flex items-center gap-2 border-b border-slate-800 pb-2">
                      <span className="p-1.5 rounded-md bg-blue-500/10 text-blue-400">
                        <FolderGit size={14} />
                      </span>
                      <h4 className="text-xs font-black text-slate-200 uppercase tracking-wider">第 2 关：项目级自主授权机制 (Discretionary Access Control)</h4>
                    </div>

                    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 text-xs text-slate-400">
                      <div className="lg:col-span-2 space-y-3 leading-relaxed">
                        <p className="font-normal">
                          ECOS 采用类似于操作系统的“项目文件夹目录”进行粗粒度控制，我们称之为 **项目 ACL 授权 (DAC)**。
                          一个项目由多名拥有不同角色的用户或安全 LDAP 工作组共同管理。
                        </p>
                        <h5 className="font-bold text-slate-200">🔑 角色层级及防御重点：</h5>
                        <div className="space-y-2 pl-2 pt-1 text-[11px] font-normal">
                          <div className="flex gap-2">
                            <span className="px-1.5 py-0.2 bg-red-950 text-red-400 border border-red-900/30 rounded font-mono text-[9px] h-4">Owner</span>
                            <p><strong>完全自治角色</strong>。可以更改项目元数据，并在其名下随意添加或开除其他用户，拥有最宽广的权限。必须极力克制授予，防止权限泛滥（权限过载）。</p>
                          </div>
                          <div className="flex gap-2">
                            <span className="px-1.5 py-0.2 bg-orange-950 text-orange-400 border border-orange-900/30 rounded font-mono text-[9px] h-4">Editor</span>
                            <p><strong>编辑与数据产出角色</strong>。拥有写入、编辑、运行分析工作流、创建下游数据流的权限。可以读取和更新数据集，但无法配置项目级别的 ACL 授权。</p>
                          </div>
                          <div className="flex gap-2">
                            <span className="px-1.5 py-0.2 bg-blue-950 text-blue-400 border border-blue-900/30 rounded font-mono text-[9px] h-4">Viewer</span>
                            <p><strong>普通只读角色</strong>。仅允许读取项目内的成果和仪表盘表数据，无法在其中保存任何中间工作流，无法更改内容。</p>
                          </div>
                          <div className="flex gap-2">
                            <span className="px-1.5 py-0.2 bg-emerald-950 text-emerald-400 border border-emerald-900/30 rounded font-mono text-[9px] h-4 font-black">Discoverer</span>
                            <p><strong>数据防泄漏探索角色（Discoverer）</strong>。极其关键！该角色下的用户**仅能**在资产目录中检索到本项目的存在、看到该项目的数据结构和元数据说明，但**绝对无法**通过任何 SQL 或浏览工具看到其中任何一行实体明细数据。实现“可知不可见”。</p>
                          </div>
                        </div>
                      </div>

                      <div className="bg-slate-900 border border-slate-800 p-4 rounded-xl space-y-3 text-[11px]">
                        <div className="text-slate-200 font-bold border-b border-slate-800 pb-1 flex items-center gap-1.5">
                          <Settings size={12} className="text-blue-400" />
                          <span>如何为受控项目授权成员？</span>
                        </div>
                        <ol className="space-y-2 list-decimal list-inside pl-1 text-slate-400 font-normal leading-normal">
                          <li>进入左侧「<span className="text-slate-200">项目授权治理 (DAC)</span>」页面。</li>
                          <li>在项目列表里选择相应的分析项目（如 <code className="text-blue-400 font-mono">proj_passenger_eu</code>）。</li>
                          <li>找到右侧的 “项目 ACL 控制授权白名单” 栏目。</li>
                          <li>点击 “授权受控成员”，输入指定的分析员账号（例：<code className="text-indigo-400 font-mono text-[10px]">analyst_li</code>）。</li>
                          <li>选择精准的角色类型，初级管理员应严格贯彻**最小特权原则**（优先授予 Discoverer 或 Viewer，尽量不授权 Owner）。</li>
                        </ol>
                      </div>
                    </div>
                  </div>
                )}

                {/* 4. Security Markings MAC */}
                {guideSection === 'mac' && (
                  <div className="space-y-4">
                    <div className="flex items-center gap-2 border-b border-slate-800 pb-2">
                      <span className="p-1.5 rounded-md bg-pink-500/10 text-pink-400">
                        <Tag size={14} />
                      </span>
                      <h4 className="text-xs font-black text-slate-200 uppercase tracking-wider">第 3 关：保密等级强制标定与安全标记级联派生 (Mandatory Access Control)</h4>
                    </div>

                    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 text-xs text-slate-400">
                      <div className="lg:col-span-2 space-y-3 leading-relaxed">
                        <p className="font-normal">
                          普通的自主访问控制（DAC）很容易因分析人员意外将文件拷贝到另一个未受控文件夹而造成泄露。为了彻底封死此类漏洞，ECOS 推出了 **强制访问标记管理 (MAC - Security Markings)**。
                        </p>
                        <h5 className="font-bold text-slate-200">🏷️ 标记的最高行为特征 —— 级联向下游血缘传播 (Cascading Propagation)：</h5>
                        <p className="text-[11px] font-normal">
                          这是 ECOS 零信任安全机制中最著名的核心特点：
                          一旦源头数据集（如 <code className="text-pink-400 font-mono">ds_passenger_manifest</code>）被施加了某一安全标记（如 <code className="text-indigo-400 font-mono font-bold">gdpr_pii</code>），
                          **任何基于该源表创建的衍生表、视图、过滤导出的数据流资产，都将强制继承并终生沿袭这一安全标记标记锁**。
                          无论后续分析员如何复制、另存为、JOIN，安全限制在流式引擎层都永久跟随！
                        </p>
                        <h5 className="font-bold text-slate-200">💼 安全特许组 (Granted Groups)：</h5>
                        <p className="text-[11px] font-normal">
                          除非用户的 LDAP 账户显式加入该安全标记对应的 “特许授权安全组（Granted Group）”，否则即便拥有所在项目的最高管理员权限（Owner），解密引擎也将在最后一刻断然将其阻断，显示 `ACCESS DENIED`。
                        </p>
                      </div>

                      <div className="bg-slate-900 border border-slate-800 p-4 rounded-xl space-y-3 text-[11px]">
                        <div className="text-slate-200 font-bold border-b border-slate-800 pb-1 flex items-center gap-1.5">
                          <Lock size={12} className="text-pink-400" />
                          <span>如何绑定与创建标记锁？</span>
                        </div>
                        <ol className="space-y-2 list-decimal list-inside pl-1 text-slate-400 font-normal">
                          <li>打开左侧「<span className="text-pink-400 font-bold">安全标记管理</span>」面板。</li>
                          <li>可以点击 “创建密级安全标记” 来建立新的保密标识，如 <code className="text-pink-400 font-mono text-[10px]">M_VIP_PASSPORT</code>，选择相应的保密评级：`SECRET` / `RESTRICTED`。</li>
                          <li>在右下侧数据源列表上，选择敏感数据集。</li>
                          <li>点击 “数据源打标/解锁标记”，即可强行将数据集和此安全标记物理锁定。</li>
                        </ol>
                      </div>
                    </div>
                  </div>
                )}

                {/* 5. Purpose PBAC & Row/Col Policy */}
                {guideSection === 'pbac' && (
                  <div className="space-y-4">
                    <div className="flex items-center gap-2 border-b border-slate-800 pb-2">
                      <span className="p-1.5 rounded-md bg-emerald-500/10 text-emerald-400">
                        <EyeOff size={14} />
                      </span>
                      <h4 className="text-xs font-black text-slate-200 uppercase tracking-wider">第 4 关：合规分析用途 (PBAC) 与行列级流式脱敏网关策略</h4>
                    </div>

                    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 text-xs text-slate-400">
                      <div className="lg:col-span-2 space-y-3 leading-relaxed">
                        <p className="font-normal">
                          这是保护用户 PII（个人敏感信息）和财务核心安全的最后、也是最细粒度的过滤底盘：**基于商业正当分析用途的安全检查 (PBAC & Row/Column Level Policies)**。
                        </p>
                        <h5 className="font-bold text-slate-200">📋 合规目的用途机制 (Purpose-Based)：</h5>
                        <p className="text-[11px] font-normal">
                          仅仅拥有数据权限还不能看核心数据。用户每一次读取，解密网关都会询问其是否关联了经合规部门、法律顾问（DPO）核准的**“分析目的（Purpose）”**。目的是有生命周期的，一旦到期（ExpiresAt）或被冻结，数据访问自动阻断，有效防范数据被挪作他用。
                        </p>
                        <h5 className="font-bold text-slate-200">🔍 行列级双向掩护脱敏 (Column Masks & Row Filters)：</h5>
                        <p className="text-[11px] font-normal">
                          针对涉及个人尊严或敏感商业数据（如机票销售的信用卡、邮箱等），必须使用行列级安全脱敏机制：
                        </p>
                        <ul className="list-disc list-inside space-y-1.5 text-[11px] pl-2 font-mono font-normal">
                          <li>
                            <strong className="text-indigo-400">列脱敏 (Column Masking - REDACT)</strong>: 完全隐藏整列。例将护照号全隐，流式解码引擎在明文层将其置换为 <code className="text-slate-300">"** REDACTED **"</code>。
                          </li>
                          <li>
                            <strong className="text-amber-400">列脱敏 (Column Masking - PARTIAL)</strong>: 部分隐藏。例只保留电子邮箱邮箱后半段，前缀全掩，避免暴露客户的具体隐私（例如: <code className="text-slate-300">"t***@gmail.com"</code>）。
                          </li>
                          <li>
                            <strong className="text-emerald-400">行过滤 (Row Filter Predicate)</strong>: 注入谓词过滤 SQL，限制物理可见行。例如在行中注入 <code className="text-slate-300">"org_id = 'Org_EU_Ops'"</code>，当前用户如果访问此表，所有非欧盟组织范围内的记录将直接从底层物理引擎上蒸发，无法被探测！
                          </li>
                        </ul>
                      </div>

                      <div className="bg-slate-900 border border-slate-800 p-4 rounded-xl space-y-3 text-[11px]">
                        <div className="text-slate-200 font-bold border-b border-slate-800 pb-1 flex items-center gap-1.5">
                          <Settings size={12} className="text-emerald-400" />
                          <span>如何快速配置行列策略？</span>
                        </div>
                        <ol className="space-y-2 list-decimal list-inside pl-1 text-slate-400 font-normal leading-normal">
                          <li>点击左侧导航「<span className="text-slate-200">行列级安全策略</span>」面板。</li>
                          <li>从顶部下拉选项卡中选择你需要重点关切的数据集（如 <code className="text-emerald-400 font-mono">ds_passenger_manifest</code>）。</li>
                          <li>下方的 “列遮蔽脱敏设置 (Column Masking)” 区域中，可直接开关对应的某一列（例如 <code className="text-slate-200 font-mono text-[10px]">passport_no</code> 的 `REDACT`，或 <code className="text-slate-200 font-mono text-[10px]">email_address</code> 的 `PARTIAL`）。</li>
                          <li>在 “行过滤谓词策略 (RowFilter SQL)” 区域，可快速切换已绑定的区域隔离行过滤脚本，保障数据不过界。</li>
                        </ol>
                      </div>
                    </div>
                  </div>
                )}

                {/* 6. Auditing & AI scan */}
                {guideSection === 'audit' && (
                  <div className="space-y-4">
                    <div className="flex items-center gap-2 border-b border-slate-800 pb-2">
                      <span className="p-1.5 rounded-md bg-amber-500/10 text-amber-400">
                        <Activity size={14} />
                      </span>
                      <h4 className="text-xs font-black text-slate-200 uppercase tracking-wider">第 5 关：动态安全合规审计与自适应重新扫描诊断</h4>
                    </div>

                    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 text-xs text-slate-400">
                      <div className="lg:col-span-2 space-y-3 leading-relaxed font-normal">
                        <p>
                          任何防御都可能在策略更替中引入死角。因此，管理员必须周期性地查看安全审计，并结合 AIP 自适应安全扫描，做到**“动态威胁捕获”**。
                        </p>
                        <h5 className="font-bold text-slate-200">🛡️ 动态审计三态判例 (Audit Log States)：</h5>
                        <ul className="list-disc list-inside space-y-1 text-[11px] pl-2 font-mono">
                          <li>
                            <strong className="text-emerald-400">SUCCESS (正常通过)</strong>: 安全网关经链式研判，判定访问动作完全合法，流式渲染并返回经脱敏处理的数据。
                          </li>
                          <li>
                            <strong className="text-amber-500">WARN (审计风险预警)</strong>: 行为可能合法，但处于异常时段、或访问量突破基线，或用户身份处于临界组。写入审计底座，提示审查。
                          </li>
                          <li>
                            <strong className="text-red-500">DENIED (主动拦截阻断)</strong>: 强烈威胁！用户因缺组织隔离信任、无项目 ACL、缺 MAC 打标授权或目的过期，被零信任网关强行掐断并丢弃。
                          </li>
                        </ul>
                        <h5 className="font-bold text-slate-200">🧬 AIP自适应重新扫描诊断书机制：</h5>
                        <p className="text-[11px]">
                          点击审计面板或大盘上的“系统安全扫描诊断书”，审计引擎将对整个系统的组织注册、ACL 漏洞、不规范权限传播路径进行全面拓扑扫描。
                          每次修改配置后，点击 **“实时重新扫描”** 可即刻同步最新的安全研判评估状态，消除特权孤岛。
                        </p>
                      </div>

                      <div className="bg-slate-900 border border-slate-800 p-4 rounded-xl space-y-3 text-[11px]">
                        <div className="text-slate-200 font-bold border-b border-slate-800 pb-1 flex items-center gap-1.5">
                          <ShieldAlert size={12} className="text-amber-400" />
                          <span>态势感知与阻断核验</span>
                        </div>
                        <ul className="space-y-2 list-none pl-0 text-slate-400 leading-normal font-normal">
                          <li className="flex items-start gap-1">
                            <span className="text-amber-500">⚠️</span>
                            <span><strong>定期导出合规证明</strong>: 点击“导出安全合规报告”可自动生成并加密打包PDF合规文件存证。</span>
                          </li>
                          <li className="flex items-start gap-1">
                            <span className="text-red-400">🚨</span>
                            <span><strong>异常阻断应对</strong>: 若审计日志出现大范围连续的 `DENIED` 记录，预示有入侵或越权企图。应一键拉黑可疑 LDAP 成员并调阅诊断书排查漏网。</span>
                          </li>
                        </ul>
                      </div>
                    </div>
                  </div>
                )}

              </div>

              {/* Bottom Quick Simulator link */}
              <div className="p-4 bg-slate-950 border border-slate-800 rounded-xl flex items-center justify-between text-xs">
                <div className="flex items-center gap-3">
                  <span className="p-2 bg-indigo-950/50 text-indigo-400 rounded-lg border border-indigo-900/30">
                    <Flame size={16} />
                  </span>
                  <div>
                    <h5 className="font-black text-slate-200">准备好了？现在就开启一场合规攻防实战演练！</h5>
                    <p className="text-[10px] text-slate-500 font-normal">可以通过左侧的“安全合规概览”选择 GDPR 欧盟隐私合规或高密财务退票核销，跟随向导执行每一步动作并观看解密网关计算出的实时判定链路。</p>
                  </div>
                </div>
                <button
                  onClick={() => {
                    setActiveTab('overview');
                    setShowPlaybookGuide(true);
                  }}
                  className="px-4 py-2 bg-indigo-600 hover:bg-indigo-500 text-white font-black rounded-lg transition-all flex items-center gap-2 cursor-pointer shadow-md"
                >
                  <span>开启向导实战演练</span>
                  <ChevronRight size={13} />
                </button>
              </div>

            </div>
          )}

        </div>
      </div>

      {/* ========================================== */}
      {/* MODALS / OVERLAYS FOR CREATION */}
      {/* ========================================== */}

      {/* Modal 1: Add Org */}
      {showAddOrgModal && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs flex items-center justify-center z-50 p-4">
          <div className="bg-slate-950 border border-slate-800 rounded-xl p-6 max-w-md w-full space-y-4">
            <div className="flex justify-between items-center border-b border-slate-800 pb-3">
              <h4 className="text-xs font-bold text-slate-200 uppercase tracking-wider">添加全新安全隔离域</h4>
              <button onClick={() => setShowAddOrgModal(false)} className="text-slate-500 hover:text-slate-300 cursor-pointer">
                <X size={16} />
              </button>
            </div>
            <form onSubmit={handleAddOrg} className="space-y-3 text-xs">
              <div>
                <label className="text-slate-400 font-bold block mb-1">组织名称</label>
                <input
                  type="text"
                  required
                  placeholder="例: 航空安保局"
                  value={newOrgName}
                  onChange={(e) => setNewOrgName(e.target.value)}
                  className="w-full bg-slate-900 border border-slate-800 rounded p-2 text-slate-200 outline-none"
                />
              </div>
              <div>
                <label className="text-slate-400 font-bold block mb-1">唯一标识 (ID)</label>
                <input
                  type="text"
                  required
                  placeholder="例: org_sec_police"
                  value={newOrgId}
                  onChange={(e) => setNewOrgId(e.target.value)}
                  className="w-full bg-slate-900 border border-slate-800 rounded p-2 text-slate-200 font-mono outline-none"
                />
              </div>
              <div>
                <label className="text-slate-400 font-bold block mb-1">默认 IP 段限制 CIDR</label>
                <input
                  type="text"
                  placeholder="例: 10.220.0.0/16"
                  value={newOrgIp}
                  onChange={(e) => setNewOrgIp(e.target.value)}
                  className="w-full bg-slate-900 border border-slate-800 rounded p-2 text-slate-200 font-mono outline-none"
                />
              </div>
              <div className="flex items-center gap-2 pt-2">
                <input
                  type="checkbox"
                  id="isolation_chk"
                  checked={newOrgIsolation}
                  onChange={(e) => setNewOrgIsolation(e.target.checked)}
                  className="rounded bg-slate-900 border-slate-800"
                />
                <label htmlFor="isolation_chk" className="text-slate-300 font-semibold cursor-pointer select-none">
                  强制启用物理域高耸严密隔离
                </label>
              </div>
              <div className="flex justify-end gap-2 pt-4 border-t border-slate-900">
                <button
                  type="button"
                  onClick={() => setShowAddOrgModal(false)}
                  className="px-3 py-1.5 bg-slate-900 text-slate-400 rounded-lg hover:bg-slate-800 cursor-pointer"
                >
                  取消
                </button>
                <button
                  type="submit"
                  className="px-4 py-1.5 bg-indigo-600 hover:bg-indigo-500 text-white font-bold rounded-lg cursor-pointer"
                >
                  确认新增组织
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal 2: Add Member ACL */}
      {showAddMemberModal && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs flex items-center justify-center z-50 p-4">
          <div className="bg-slate-950 border border-slate-800 rounded-xl p-6 max-w-md w-full space-y-4">
            <div className="flex justify-between items-center border-b border-slate-800 pb-3">
              <h4 className="text-xs font-bold text-slate-200 uppercase tracking-wider">授予受控工程成员角色</h4>
              <button onClick={() => setShowAddMemberModal(false)} className="text-slate-500 hover:text-slate-300 cursor-pointer">
                <X size={16} />
              </button>
            </div>
            <form onSubmit={handleAddMember} className="space-y-3 text-xs">
              <div>
                <label className="text-slate-400 font-bold block mb-1">用户或工作组 LDAP 账号</label>
                <input
                  type="text"
                  required
                  placeholder="例: logistic_auditor"
                  value={newMemberName}
                  onChange={(e) => setNewMemberName(e.target.value)}
                  className="w-full bg-slate-900 border border-slate-800 rounded p-2 text-slate-200 font-mono outline-none"
                />
              </div>
              <div>
                <label className="text-slate-400 font-bold block mb-1">受控角色类别 (Role)</label>
                <select
                  value={newMemberRole}
                  onChange={(e) => setNewMemberRole(e.target.value as any)}
                  className="w-full bg-slate-900 border border-slate-800 rounded p-2 text-slate-200 outline-none"
                >
                  <option value="Owner">Owner (所有者 - 完全自治)</option>
                  <option value="Editor">Editor (编辑者 - 写入修改)</option>
                  <option value="Viewer">Viewer (查看者 - 仅读只读)</option>
                  <option value="Discoverer">Discoverer (发现者 - 仅可见索引，无法看明细)</option>
                </select>
              </div>
              <div className="flex justify-end gap-2 pt-4 border-t border-slate-900">
                <button
                  type="button"
                  onClick={() => setShowAddMemberModal(false)}
                  className="px-3 py-1.5 bg-slate-900 text-slate-400 rounded-lg hover:bg-slate-800 cursor-pointer"
                >
                  取消
                </button>
                <button
                  type="submit"
                  className="px-4 py-1.5 bg-indigo-600 hover:bg-indigo-500 text-white font-bold rounded-lg cursor-pointer"
                >
                  追加角色
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal 3: Add Marking */}
      {showAddMarkingModal && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs flex items-center justify-center z-50 p-4">
          <div className="bg-slate-950 border border-slate-800 rounded-xl p-6 max-w-md w-full space-y-4">
            <div className="flex justify-between items-center border-b border-slate-800 pb-3">
              <h4 className="text-xs font-bold text-slate-200 uppercase tracking-wider">创建密级安全标记</h4>
              <button onClick={() => setShowAddMarkingModal(false)} className="text-slate-500 hover:text-slate-300 cursor-pointer">
                <X size={16} />
              </button>
            </div>
            <form onSubmit={handleAddMarking} className="space-y-3 text-xs">
              <div>
                <label className="text-slate-400 font-bold block mb-1">安全标记代码 (ID)</label>
                <input
                  type="text"
                  required
                  placeholder="例: M_VIP_PASSPORT"
                  value={newMarkId}
                  onChange={(e) => setNewMarkId(e.target.value)}
                  className="w-full bg-slate-900 border border-slate-800 rounded p-2 text-slate-200 font-mono outline-none"
                />
              </div>
              <div>
                <label className="text-slate-400 font-bold block mb-1">标记中文显示名称</label>
                <input
                  type="text"
                  required
                  placeholder="例: 尊贵客户特许护照信息"
                  value={newMarkName}
                  onChange={(e) => setNewMarkName(e.target.value)}
                  className="w-full bg-slate-900 border border-slate-800 rounded p-2 text-slate-200 outline-none"
                />
              </div>
              <div>
                <label className="text-slate-400 font-bold block mb-1">保密评级 (Classification Level)</label>
                <select
                  value={newMarkLevel}
                  onChange={(e) => setNewMarkLevel(e.target.value as any)}
                  className="w-full bg-slate-900 border border-slate-800 rounded p-2 text-slate-200 outline-none"
                >
                  <option value="CONFIDENTIAL">CONFIDENTIAL (内部机密)</option>
                  <option value="SECRET">SECRET (高度机密)</option>
                  <option value="TOP_SECRET">TOP_SECRET (绝对核心绝密)</option>
                  <option value="RESTRICTED">RESTRICTED (受限合规级)</option>
                </select>
              </div>
              <div>
                <label className="text-slate-400 font-bold block mb-1">安全标记背景描述</label>
                <textarea
                  placeholder="详细解释打标依据和继承限制规则..."
                  value={newMarkDesc}
                  onChange={(e) => setNewMarkDesc(e.target.value)}
                  className="w-full bg-slate-900 border border-slate-800 rounded p-2 text-slate-200 outline-none h-16 resize-none"
                />
              </div>
              <div className="flex justify-end gap-2 pt-4 border-t border-slate-900">
                <button
                  type="button"
                  onClick={() => setShowAddMarkingModal(false)}
                  className="px-3 py-1.5 bg-slate-900 text-slate-400 rounded-lg hover:bg-slate-800 cursor-pointer"
                >
                  取消
                </button>
                <button
                  type="submit"
                  className="px-4 py-1.5 bg-indigo-600 hover:bg-indigo-500 text-white font-bold rounded-lg cursor-pointer"
                >
                  确认创建
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal 4: Add Purpose PBAC */}
      {showAddPurposeModal && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs flex items-center justify-center z-50 p-4">
          <div className="bg-slate-950 border border-slate-800 rounded-xl p-6 max-w-md w-full space-y-4">
            <div className="flex justify-between items-center border-b border-slate-800 pb-3">
              <h4 className="text-xs font-bold text-slate-200 uppercase tracking-wider">新增合规分析目的项目</h4>
              <button onClick={() => setShowAddPurposeModal(false)} className="text-slate-500 hover:text-slate-300 cursor-pointer">
                <X size={16} />
              </button>
            </div>
            <form onSubmit={handleAddPurpose} className="space-y-3 text-xs">
              <div>
                <label className="text-slate-400 font-bold block mb-1">合规目的编号 (ID)</label>
                <input
                  type="text"
                  required
                  placeholder="例: purpose_route_saving_2026"
                  value={newPurpId}
                  onChange={(e) => setNewPurpId(e.target.value)}
                  className="w-full bg-slate-900 border border-slate-800 rounded p-2 text-slate-200 font-mono outline-none"
                />
              </div>
              <div>
                <label className="text-slate-400 font-bold block mb-1">合规目的中文标题</label>
                <input
                  type="text"
                  required
                  placeholder="例: 航线节油性能与减排审计"
                  value={newPurpName}
                  onChange={(e) => setNewPurpName(e.target.value)}
                  className="w-full bg-slate-900 border border-slate-800 rounded p-2 text-slate-200 outline-none"
                />
              </div>
              <div>
                <label className="text-slate-400 font-bold block mb-1">拟引入的数据集</label>
                <input
                  type="text"
                  placeholder="例: ds_flight_schedules"
                  value={newPurpDs}
                  onChange={(e) => setNewPurpDs(e.target.value)}
                  className="w-full bg-slate-900 border border-slate-800 rounded p-2 text-slate-200 font-mono outline-none"
                />
              </div>
              <div>
                <label className="text-slate-400 font-bold block mb-1">脱敏遮蔽条款</label>
                <input
                  type="text"
                  placeholder="例: MASK(ssn_number)"
                  value={newPurpRules}
                  onChange={(e) => setNewPurpRules(e.target.value)}
                  className="w-full bg-slate-900 border border-slate-800 rounded p-2 text-slate-200 font-mono outline-none"
                />
              </div>
              <div>
                <label className="text-slate-400 font-bold block mb-1">目的用途背景详情说明</label>
                <textarea
                  placeholder="在此写入符合GDPR或审计要求的正当商业分析用途陈述..."
                  value={newPurpDesc}
                  onChange={(e) => setNewPurpDesc(e.target.value)}
                  className="w-full bg-slate-900 border border-slate-800 rounded p-2 text-slate-200 outline-none h-16 resize-none"
                />
              </div>
              <div className="flex justify-end gap-2 pt-4 border-t border-slate-900">
                <button
                  type="button"
                  onClick={() => setShowAddPurposeModal(false)}
                  className="px-3 py-1.5 bg-slate-900 text-slate-400 rounded-lg hover:bg-slate-800 cursor-pointer"
                >
                  取消
                </button>
                <button
                  type="submit"
                  className="px-4 py-1.5 bg-indigo-600 hover:bg-indigo-500 text-white font-bold rounded-lg cursor-pointer"
                >
                  新增目的
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Modal 5: System Security Scan Diagnostic Report */}
      {showDiagnosticModal && (
        <div className="fixed inset-0 bg-black/80 backdrop-blur-md flex items-center justify-center z-50 p-4">
          <div className="bg-slate-950 border border-slate-850 rounded-2xl p-6 max-w-4xl w-full max-h-[85vh] overflow-hidden flex flex-col space-y-4 shadow-2xl relative">
            <div className="absolute top-4 right-4 flex items-center gap-2">
              <button
                onClick={openDiagnosticReport}
                className="p-1 text-emerald-400 hover:text-emerald-300 bg-emerald-950/20 hover:bg-emerald-950/40 rounded border border-emerald-900/30 text-xs font-bold transition-all flex items-center gap-1 cursor-pointer"
                title="重新扫描态势"
              >
                <RefreshCw size={13} />
                <span>实时重新扫描</span>
              </button>
              <button onClick={() => setShowDiagnosticModal(false)} className="text-slate-400 hover:text-slate-200 cursor-pointer p-1 bg-slate-900 hover:bg-slate-800 rounded">
                <X size={16} />
              </button>
            </div>

            <div className="border-b border-slate-850 pb-3 flex items-center gap-2.5">
              <span className="p-2 bg-emerald-950/50 rounded-lg text-emerald-400 border border-emerald-800/30">
                <ShieldCheck size={18} className="animate-pulse" />
              </span>
              <div>
                <h4 className="text-sm font-black text-slate-100">
                  🧬 AIP 自适应零信任安全扫描诊断书
                </h4>
                <p className="text-[10px] text-slate-400">
                  System Security Scan Diagnostic & Threats Hunter (Auto-learned in Vector Knowledge Base)
                </p>
              </div>
            </div>

            <div className="flex-1 overflow-y-auto space-y-3 pr-2 scrollbar-thin scrollbar-thumb-slate-800 text-slate-300 text-xs font-sans leading-relaxed">
              <div className="p-4 bg-slate-900/50 border border-slate-850 rounded-xl space-y-3">
                {diagnosticReport.split('\n').map((line, idx) => {
                  if (line.startsWith('## ')) {
                    return (
                      <h5 key={idx} className="text-slate-100 font-extrabold text-sm border-b border-slate-800 pb-1 pt-3 flex items-center gap-2">
                        <span className="w-1.5 h-3 bg-emerald-500 rounded-sm inline-block"></span>
                        {line.replace('## ', '')}
                      </h5>
                    );
                  }
                  if (line.startsWith('### ')) {
                    return (
                      <h6 key={idx} className="text-indigo-400 font-black text-xs pt-2 flex items-center gap-1.5">
                        <ShieldAlert size={12} className="text-indigo-400" />
                        {line.replace('### ', '')}
                      </h6>
                    );
                  }
                  if (line.startsWith('- ')) {
                    const content = line.replace('- ', '');
                    let textColor = 'text-slate-300';
                    let icon = 'Circle';
                    let iconColor = 'text-indigo-400';
                    
                    if (content.includes('🚨') || content.includes('高危')) {
                      textColor = 'text-red-400 font-bold bg-red-950/10 px-2 py-1 rounded border border-red-900/10';
                      icon = 'AlertOctagon';
                      iconColor = 'text-red-500';
                    } else if (content.includes('⚠️') || content.includes('中危')) {
                      textColor = 'text-amber-400 font-bold bg-amber-950/10 px-2 py-1 rounded border border-amber-900/10';
                      icon = 'AlertTriangle';
                      iconColor = 'text-amber-500';
                    } else if (content.includes('✅') || content.includes('安全')) {
                      textColor = 'text-emerald-400 font-semibold';
                      icon = 'CheckCircle2';
                      iconColor = 'text-emerald-500';
                    }
                    
                    return (
                      <div key={idx} className={`ml-4 flex items-start gap-2 py-0.5 ${textColor}`}>
                        <span className="mt-0.5 shrink-0"><LucideIcon name={icon as any} size={11} className={iconColor} /></span>
                        <span>{content.replace(/🚨|⚠️|✅/g, '').trim()}</span>
                      </div>
                    );
                  }
                  if (/^\d+\s*\./.test(line.trim())) {
                    return (
                      <div key={idx} className="ml-4 pl-3 border-l border-indigo-900/30 py-1 bg-slate-900/30 rounded-r text-[11px] font-mono flex flex-col gap-1">
                        <div className="text-slate-200">{line}</div>
                      </div>
                    );
                  }
                  if (line.trim().startsWith('*判定依据*') || line.trim().startsWith('- *判定依据*')) {
                    return (
                      <div key={idx} className="ml-10 text-[10px] text-slate-400 italic bg-slate-950/40 p-1.5 rounded border border-slate-900">
                        {line}
                      </div>
                    );
                  }
                  if (line.trim() === '') return <div key={idx} className="h-1" />;
                  return <p key={idx} className="text-slate-400 pl-4">{line}</p>;
                })}
              </div>
            </div>

            <div className="border-t border-slate-850 pt-3 flex justify-between items-center text-[10px] text-slate-500 font-mono">
              <span>状态: 零信任自适应闭环机制已激活</span>
              <button
                type="button"
                onClick={() => setShowDiagnosticModal(false)}
                className="px-4 py-1.5 bg-slate-900 text-slate-300 font-black rounded-lg hover:bg-slate-800 cursor-pointer border border-slate-800 transition-all text-xs"
              >
                关闭诊断书
              </button>
            </div>
          </div>
        </div>
      )}

    </div>
  );
}
