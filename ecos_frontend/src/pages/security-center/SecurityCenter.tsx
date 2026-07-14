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



import { LucideIcon, CHART_COLORS, pieData, evaluationTrend } from './helpers';
import OverviewTab from './tabs/OverviewTab';
import OrgsTab from './tabs/OrgsTab';
import DacTab from './tabs/DacTab';
import MacTab from './tabs/MacTab';
import PbacTab from './tabs/PbacTab';
import Row_colTab from './tabs/Row_colTab';
import AuditTab from './tabs/AuditTab';
import type { SecurityOrg, ProjectDAC, SecurityMarking, PurposePBAC, RowColPolicy, SecurityAuditLog, PlaybookStep } from './types';
import {
  mockSecurityOrgs, mockProjectDACs, mockSecurityMarkings,
  mockPurposes, mockRowColPolicies, initialAuditLogs
} from './mockData';

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

  // Statistics & Charts Data — imported from ./helpers

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
        verdict: data.verdict || 'DENIED',
        traces: Array.isArray(data.traces) ? data.traces : ['⚠️ 后端返回数据格式异常，请检查 API 链路。']
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
            <OverviewTab
              orgs={orgs} setOrgs={setOrgs}
              projects={projects} setProjects={setProjects}
              markings={markings} setMarkings={setMarkings}
              purposes={purposes} setPurposes={setPurposes}
              rowColPolicies={rowColPolicies} setRowColPolicies={setRowColPolicies}
              auditLogs={auditLogs} setAuditLogs={setAuditLogs}
              activeTab={activeTab} setActiveTab={setActiveTab}
              selectedProjectId={selectedProjectId} setSelectedProjectId={setSelectedProjectId}
              selectedMarkingId={selectedMarkingId} setSelectedMarkingId={setSelectedMarkingId}
              selectedRowColDs={selectedRowColDs} setSelectedRowColDs={setSelectedRowColDs}
              simUser={simUser} setSimUser={setSimUser}
              simDataset={simDataset} setSimDataset={setSimDataset}
              simPurpose={simPurpose} setSimPurpose={setSimPurpose}
              simResult={simResult} simIp={simIp} setSimIp={setSimIp}
              handleRunSimulation={handleRunSimulation}
              showAddOrgModal={showAddOrgModal} setShowAddOrgModal={setShowAddOrgModal}
              showAddMemberModal={showAddMemberModal} setShowAddMemberModal={setShowAddMemberModal}
              showAddMarkingModal={showAddMarkingModal} setShowAddMarkingModal={setShowAddMarkingModal}
              showAddPurposeModal={showAddPurposeModal} setShowAddPurposeModal={setShowAddPurposeModal}
              newOrgName={newOrgName} setNewOrgName={setNewOrgName}
              newOrgId={newOrgId} setNewOrgId={setNewOrgId}
              newOrgIp={newOrgIp} setNewOrgIp={setNewOrgIp}
              newOrgIsolation={newOrgIsolation} setNewOrgIsolation={setNewOrgIsolation}
              newMemberName={newMemberName} setNewMemberName={setNewMemberName}
              newMemberRole={newMemberRole} setNewMemberRole={setNewMemberRole}
              newMarkId={newMarkId} setNewMarkId={setNewMarkId}
              newMarkName={newMarkName} setNewMarkName={setNewMarkName}
              newMarkLevel={newMarkLevel} setNewMarkLevel={setNewMarkLevel}
              newMarkDesc={newMarkDesc} setNewMarkDesc={setNewMarkDesc}
              newPurpId={newPurpId} setNewPurpId={setNewPurpId}
              newPurpName={newPurpName} setNewPurpName={setNewPurpName}
              newPurpDesc={newPurpDesc} setNewPurpDesc={setNewPurpDesc}
              newPurpDs={newPurpDs} setNewPurpDs={setNewPurpDs}
              newPurpRules={newPurpRules} setNewPurpRules={setNewPurpRules}
              handleAddOrg={handleAddOrg} handleAddMember={handleAddMember}
              handleAddMarking={handleAddMarking} handleAddPurpose={handleAddPurpose}
              handleToggleColumnMask={handleToggleColumnMask}
              handleToggleRowFilter={handleToggleRowFilter}
              handleExecutePlaybookStep={handleExecutePlaybookStep}
              openDiagnosticReport={openDiagnosticReport}
              showToast={showToast}
            />
          )}

          {/* ================================================== */}
          {/* TAB 2: ORGANIZATIONS (组织架构管理) */}
          {/* ================================================== */}
          {activeTab === 'orgs' && (
            <OrgsTab
              orgs={orgs} setOrgs={setOrgs}
              projects={projects} setProjects={setProjects}
              markings={markings} setMarkings={setMarkings}
              purposes={purposes} setPurposes={setPurposes}
              rowColPolicies={rowColPolicies} setRowColPolicies={setRowColPolicies}
              auditLogs={auditLogs} setAuditLogs={setAuditLogs}
              activeTab={activeTab} setActiveTab={setActiveTab}
              selectedProjectId={selectedProjectId} setSelectedProjectId={setSelectedProjectId}
              selectedMarkingId={selectedMarkingId} setSelectedMarkingId={setSelectedMarkingId}
              selectedRowColDs={selectedRowColDs} setSelectedRowColDs={setSelectedRowColDs}
              simUser={simUser} setSimUser={setSimUser}
              simDataset={simDataset} setSimDataset={setSimDataset}
              simPurpose={simPurpose} setSimPurpose={setSimPurpose}
              simResult={simResult} simIp={simIp} setSimIp={setSimIp}
              handleRunSimulation={handleRunSimulation}
              showAddOrgModal={showAddOrgModal} setShowAddOrgModal={setShowAddOrgModal}
              showAddMemberModal={showAddMemberModal} setShowAddMemberModal={setShowAddMemberModal}
              showAddMarkingModal={showAddMarkingModal} setShowAddMarkingModal={setShowAddMarkingModal}
              showAddPurposeModal={showAddPurposeModal} setShowAddPurposeModal={setShowAddPurposeModal}
              newOrgName={newOrgName} setNewOrgName={setNewOrgName}
              newOrgId={newOrgId} setNewOrgId={setNewOrgId}
              newOrgIp={newOrgIp} setNewOrgIp={setNewOrgIp}
              newOrgIsolation={newOrgIsolation} setNewOrgIsolation={setNewOrgIsolation}
              newMemberName={newMemberName} setNewMemberName={setNewMemberName}
              newMemberRole={newMemberRole} setNewMemberRole={setNewMemberRole}
              newMarkId={newMarkId} setNewMarkId={setNewMarkId}
              newMarkName={newMarkName} setNewMarkName={setNewMarkName}
              newMarkLevel={newMarkLevel} setNewMarkLevel={setNewMarkLevel}
              newMarkDesc={newMarkDesc} setNewMarkDesc={setNewMarkDesc}
              newPurpId={newPurpId} setNewPurpId={setNewPurpId}
              newPurpName={newPurpName} setNewPurpName={setNewPurpName}
              newPurpDesc={newPurpDesc} setNewPurpDesc={setNewPurpDesc}
              newPurpDs={newPurpDs} setNewPurpDs={setNewPurpDs}
              newPurpRules={newPurpRules} setNewPurpRules={setNewPurpRules}
              handleAddOrg={handleAddOrg} handleAddMember={handleAddMember}
              handleAddMarking={handleAddMarking} handleAddPurpose={handleAddPurpose}
              handleToggleColumnMask={handleToggleColumnMask}
              handleToggleRowFilter={handleToggleRowFilter}
              handleExecutePlaybookStep={handleExecutePlaybookStep}
              openDiagnosticReport={openDiagnosticReport}
              showToast={showToast}
            />
          )}

          {/* ================================================== */}
          {/* TAB 3: DAC - PROJECTS & ROLES (项目角色权限) */}
          {/* ================================================== */}
          {activeTab === 'dac' && (
            <DacTab
              orgs={orgs} setOrgs={setOrgs}
              projects={projects} setProjects={setProjects}
              markings={markings} setMarkings={setMarkings}
              purposes={purposes} setPurposes={setPurposes}
              rowColPolicies={rowColPolicies} setRowColPolicies={setRowColPolicies}
              auditLogs={auditLogs} setAuditLogs={setAuditLogs}
              activeTab={activeTab} setActiveTab={setActiveTab}
              selectedProjectId={selectedProjectId} setSelectedProjectId={setSelectedProjectId}
              selectedMarkingId={selectedMarkingId} setSelectedMarkingId={setSelectedMarkingId}
              selectedRowColDs={selectedRowColDs} setSelectedRowColDs={setSelectedRowColDs}
              simUser={simUser} setSimUser={setSimUser}
              simDataset={simDataset} setSimDataset={setSimDataset}
              simPurpose={simPurpose} setSimPurpose={setSimPurpose}
              simResult={simResult} simIp={simIp} setSimIp={setSimIp}
              handleRunSimulation={handleRunSimulation}
              showAddOrgModal={showAddOrgModal} setShowAddOrgModal={setShowAddOrgModal}
              showAddMemberModal={showAddMemberModal} setShowAddMemberModal={setShowAddMemberModal}
              showAddMarkingModal={showAddMarkingModal} setShowAddMarkingModal={setShowAddMarkingModal}
              showAddPurposeModal={showAddPurposeModal} setShowAddPurposeModal={setShowAddPurposeModal}
              newOrgName={newOrgName} setNewOrgName={setNewOrgName}
              newOrgId={newOrgId} setNewOrgId={setNewOrgId}
              newOrgIp={newOrgIp} setNewOrgIp={setNewOrgIp}
              newOrgIsolation={newOrgIsolation} setNewOrgIsolation={setNewOrgIsolation}
              newMemberName={newMemberName} setNewMemberName={setNewMemberName}
              newMemberRole={newMemberRole} setNewMemberRole={setNewMemberRole}
              newMarkId={newMarkId} setNewMarkId={setNewMarkId}
              newMarkName={newMarkName} setNewMarkName={setNewMarkName}
              newMarkLevel={newMarkLevel} setNewMarkLevel={setNewMarkLevel}
              newMarkDesc={newMarkDesc} setNewMarkDesc={setNewMarkDesc}
              newPurpId={newPurpId} setNewPurpId={setNewPurpId}
              newPurpName={newPurpName} setNewPurpName={setNewPurpName}
              newPurpDesc={newPurpDesc} setNewPurpDesc={setNewPurpDesc}
              newPurpDs={newPurpDs} setNewPurpDs={setNewPurpDs}
              newPurpRules={newPurpRules} setNewPurpRules={setNewPurpRules}
              handleAddOrg={handleAddOrg} handleAddMember={handleAddMember}
              handleAddMarking={handleAddMarking} handleAddPurpose={handleAddPurpose}
              handleToggleColumnMask={handleToggleColumnMask}
              handleToggleRowFilter={handleToggleRowFilter}
              handleExecutePlaybookStep={handleExecutePlaybookStep}
              openDiagnosticReport={openDiagnosticReport}
              showToast={showToast}
            />
          )}

          {/* ================================================== */}
          {/* TAB 4: MAC - SECURITY MARKINGS (强制密级标记) */}
          {/* ================================================== */}
          {activeTab === 'mac' && (
            <MacTab
              orgs={orgs} setOrgs={setOrgs}
              projects={projects} setProjects={setProjects}
              markings={markings} setMarkings={setMarkings}
              purposes={purposes} setPurposes={setPurposes}
              rowColPolicies={rowColPolicies} setRowColPolicies={setRowColPolicies}
              auditLogs={auditLogs} setAuditLogs={setAuditLogs}
              activeTab={activeTab} setActiveTab={setActiveTab}
              selectedProjectId={selectedProjectId} setSelectedProjectId={setSelectedProjectId}
              selectedMarkingId={selectedMarkingId} setSelectedMarkingId={setSelectedMarkingId}
              selectedRowColDs={selectedRowColDs} setSelectedRowColDs={setSelectedRowColDs}
              simUser={simUser} setSimUser={setSimUser}
              simDataset={simDataset} setSimDataset={setSimDataset}
              simPurpose={simPurpose} setSimPurpose={setSimPurpose}
              simResult={simResult} simIp={simIp} setSimIp={setSimIp}
              handleRunSimulation={handleRunSimulation}
              showAddOrgModal={showAddOrgModal} setShowAddOrgModal={setShowAddOrgModal}
              showAddMemberModal={showAddMemberModal} setShowAddMemberModal={setShowAddMemberModal}
              showAddMarkingModal={showAddMarkingModal} setShowAddMarkingModal={setShowAddMarkingModal}
              showAddPurposeModal={showAddPurposeModal} setShowAddPurposeModal={setShowAddPurposeModal}
              newOrgName={newOrgName} setNewOrgName={setNewOrgName}
              newOrgId={newOrgId} setNewOrgId={setNewOrgId}
              newOrgIp={newOrgIp} setNewOrgIp={setNewOrgIp}
              newOrgIsolation={newOrgIsolation} setNewOrgIsolation={setNewOrgIsolation}
              newMemberName={newMemberName} setNewMemberName={setNewMemberName}
              newMemberRole={newMemberRole} setNewMemberRole={setNewMemberRole}
              newMarkId={newMarkId} setNewMarkId={setNewMarkId}
              newMarkName={newMarkName} setNewMarkName={setNewMarkName}
              newMarkLevel={newMarkLevel} setNewMarkLevel={setNewMarkLevel}
              newMarkDesc={newMarkDesc} setNewMarkDesc={setNewMarkDesc}
              newPurpId={newPurpId} setNewPurpId={setNewPurpId}
              newPurpName={newPurpName} setNewPurpName={setNewPurpName}
              newPurpDesc={newPurpDesc} setNewPurpDesc={setNewPurpDesc}
              newPurpDs={newPurpDs} setNewPurpDs={setNewPurpDs}
              newPurpRules={newPurpRules} setNewPurpRules={setNewPurpRules}
              handleAddOrg={handleAddOrg} handleAddMember={handleAddMember}
              handleAddMarking={handleAddMarking} handleAddPurpose={handleAddPurpose}
              handleToggleColumnMask={handleToggleColumnMask}
              handleToggleRowFilter={handleToggleRowFilter}
              handleExecutePlaybookStep={handleExecutePlaybookStep}
              openDiagnosticReport={openDiagnosticReport}
              showToast={showToast}
            />
          )}

          {/* ================================================== */}
          {/* TAB 5: PBAC - PURPOSE BASED ACCESS CONTROL (基于用途) */}
          {/* ================================================== */}
          {activeTab === 'pbac' && (
            <PbacTab
              orgs={orgs} setOrgs={setOrgs}
              projects={projects} setProjects={setProjects}
              markings={markings} setMarkings={setMarkings}
              purposes={purposes} setPurposes={setPurposes}
              rowColPolicies={rowColPolicies} setRowColPolicies={setRowColPolicies}
              auditLogs={auditLogs} setAuditLogs={setAuditLogs}
              activeTab={activeTab} setActiveTab={setActiveTab}
              selectedProjectId={selectedProjectId} setSelectedProjectId={setSelectedProjectId}
              selectedMarkingId={selectedMarkingId} setSelectedMarkingId={setSelectedMarkingId}
              selectedRowColDs={selectedRowColDs} setSelectedRowColDs={setSelectedRowColDs}
              simUser={simUser} setSimUser={setSimUser}
              simDataset={simDataset} setSimDataset={setSimDataset}
              simPurpose={simPurpose} setSimPurpose={setSimPurpose}
              simResult={simResult} simIp={simIp} setSimIp={setSimIp}
              handleRunSimulation={handleRunSimulation}
              showAddOrgModal={showAddOrgModal} setShowAddOrgModal={setShowAddOrgModal}
              showAddMemberModal={showAddMemberModal} setShowAddMemberModal={setShowAddMemberModal}
              showAddMarkingModal={showAddMarkingModal} setShowAddMarkingModal={setShowAddMarkingModal}
              showAddPurposeModal={showAddPurposeModal} setShowAddPurposeModal={setShowAddPurposeModal}
              newOrgName={newOrgName} setNewOrgName={setNewOrgName}
              newOrgId={newOrgId} setNewOrgId={setNewOrgId}
              newOrgIp={newOrgIp} setNewOrgIp={setNewOrgIp}
              newOrgIsolation={newOrgIsolation} setNewOrgIsolation={setNewOrgIsolation}
              newMemberName={newMemberName} setNewMemberName={setNewMemberName}
              newMemberRole={newMemberRole} setNewMemberRole={setNewMemberRole}
              newMarkId={newMarkId} setNewMarkId={setNewMarkId}
              newMarkName={newMarkName} setNewMarkName={setNewMarkName}
              newMarkLevel={newMarkLevel} setNewMarkLevel={setNewMarkLevel}
              newMarkDesc={newMarkDesc} setNewMarkDesc={setNewMarkDesc}
              newPurpId={newPurpId} setNewPurpId={setNewPurpId}
              newPurpName={newPurpName} setNewPurpName={setNewPurpName}
              newPurpDesc={newPurpDesc} setNewPurpDesc={setNewPurpDesc}
              newPurpDs={newPurpDs} setNewPurpDs={setNewPurpDs}
              newPurpRules={newPurpRules} setNewPurpRules={setNewPurpRules}
              handleAddOrg={handleAddOrg} handleAddMember={handleAddMember}
              handleAddMarking={handleAddMarking} handleAddPurpose={handleAddPurpose}
              handleToggleColumnMask={handleToggleColumnMask}
              handleToggleRowFilter={handleToggleRowFilter}
              handleExecutePlaybookStep={handleExecutePlaybookStep}
              openDiagnosticReport={openDiagnosticReport}
              showToast={showToast}
            />
          )}

          {/* ================================================== */}
          {/* TAB 6: ROW & COLUMN LEVEL POLICIES (行列级安全策略) */}
          {/* ================================================== */}
          {activeTab === 'row_col' && (
            <Row_colTab
              orgs={orgs} setOrgs={setOrgs}
              projects={projects} setProjects={setProjects}
              markings={markings} setMarkings={setMarkings}
              purposes={purposes} setPurposes={setPurposes}
              rowColPolicies={rowColPolicies} setRowColPolicies={setRowColPolicies}
              auditLogs={auditLogs} setAuditLogs={setAuditLogs}
              activeTab={activeTab} setActiveTab={setActiveTab}
              selectedProjectId={selectedProjectId} setSelectedProjectId={setSelectedProjectId}
              selectedMarkingId={selectedMarkingId} setSelectedMarkingId={setSelectedMarkingId}
              selectedRowColDs={selectedRowColDs} setSelectedRowColDs={setSelectedRowColDs}
              simUser={simUser} setSimUser={setSimUser}
              simDataset={simDataset} setSimDataset={setSimDataset}
              simPurpose={simPurpose} setSimPurpose={setSimPurpose}
              simResult={simResult} simIp={simIp} setSimIp={setSimIp}
              handleRunSimulation={handleRunSimulation}
              showAddOrgModal={showAddOrgModal} setShowAddOrgModal={setShowAddOrgModal}
              showAddMemberModal={showAddMemberModal} setShowAddMemberModal={setShowAddMemberModal}
              showAddMarkingModal={showAddMarkingModal} setShowAddMarkingModal={setShowAddMarkingModal}
              showAddPurposeModal={showAddPurposeModal} setShowAddPurposeModal={setShowAddPurposeModal}
              newOrgName={newOrgName} setNewOrgName={setNewOrgName}
              newOrgId={newOrgId} setNewOrgId={setNewOrgId}
              newOrgIp={newOrgIp} setNewOrgIp={setNewOrgIp}
              newOrgIsolation={newOrgIsolation} setNewOrgIsolation={setNewOrgIsolation}
              newMemberName={newMemberName} setNewMemberName={setNewMemberName}
              newMemberRole={newMemberRole} setNewMemberRole={setNewMemberRole}
              newMarkId={newMarkId} setNewMarkId={setNewMarkId}
              newMarkName={newMarkName} setNewMarkName={setNewMarkName}
              newMarkLevel={newMarkLevel} setNewMarkLevel={setNewMarkLevel}
              newMarkDesc={newMarkDesc} setNewMarkDesc={setNewMarkDesc}
              newPurpId={newPurpId} setNewPurpId={setNewPurpId}
              newPurpName={newPurpName} setNewPurpName={setNewPurpName}
              newPurpDesc={newPurpDesc} setNewPurpDesc={setNewPurpDesc}
              newPurpDs={newPurpDs} setNewPurpDs={setNewPurpDs}
              newPurpRules={newPurpRules} setNewPurpRules={setNewPurpRules}
              handleAddOrg={handleAddOrg} handleAddMember={handleAddMember}
              handleAddMarking={handleAddMarking} handleAddPurpose={handleAddPurpose}
              handleToggleColumnMask={handleToggleColumnMask}
              handleToggleRowFilter={handleToggleRowFilter}
              handleExecutePlaybookStep={handleExecutePlaybookStep}
              openDiagnosticReport={openDiagnosticReport}
              showToast={showToast}
            />
          )}

          {/* ================================================== */}
          {/* TAB 7: SECURITY AUDIT LOGS (安全审计日志) */}
          {/* ================================================== */}
          {activeTab === 'audit' && (
            <AuditTab
              orgs={orgs} setOrgs={setOrgs}
              projects={projects} setProjects={setProjects}
              markings={markings} setMarkings={setMarkings}
              purposes={purposes} setPurposes={setPurposes}
              rowColPolicies={rowColPolicies} setRowColPolicies={setRowColPolicies}
              auditLogs={auditLogs} setAuditLogs={setAuditLogs}
              activeTab={activeTab} setActiveTab={setActiveTab}
              selectedProjectId={selectedProjectId} setSelectedProjectId={setSelectedProjectId}
              selectedMarkingId={selectedMarkingId} setSelectedMarkingId={setSelectedMarkingId}
              selectedRowColDs={selectedRowColDs} setSelectedRowColDs={setSelectedRowColDs}
              simUser={simUser} setSimUser={setSimUser}
              simDataset={simDataset} setSimDataset={setSimDataset}
              simPurpose={simPurpose} setSimPurpose={setSimPurpose}
              simResult={simResult} simIp={simIp} setSimIp={setSimIp}
              handleRunSimulation={handleRunSimulation}
              showAddOrgModal={showAddOrgModal} setShowAddOrgModal={setShowAddOrgModal}
              showAddMemberModal={showAddMemberModal} setShowAddMemberModal={setShowAddMemberModal}
              showAddMarkingModal={showAddMarkingModal} setShowAddMarkingModal={setShowAddMarkingModal}
              showAddPurposeModal={showAddPurposeModal} setShowAddPurposeModal={setShowAddPurposeModal}
              newOrgName={newOrgName} setNewOrgName={setNewOrgName}
              newOrgId={newOrgId} setNewOrgId={setNewOrgId}
              newOrgIp={newOrgIp} setNewOrgIp={setNewOrgIp}
              newOrgIsolation={newOrgIsolation} setNewOrgIsolation={setNewOrgIsolation}
              newMemberName={newMemberName} setNewMemberName={setNewMemberName}
              newMemberRole={newMemberRole} setNewMemberRole={setNewMemberRole}
              newMarkId={newMarkId} setNewMarkId={setNewMarkId}
              newMarkName={newMarkName} setNewMarkName={setNewMarkName}
              newMarkLevel={newMarkLevel} setNewMarkLevel={setNewMarkLevel}
              newMarkDesc={newMarkDesc} setNewMarkDesc={setNewMarkDesc}
              newPurpId={newPurpId} setNewPurpId={setNewPurpId}
              newPurpName={newPurpName} setNewPurpName={setNewPurpName}
              newPurpDesc={newPurpDesc} setNewPurpDesc={setNewPurpDesc}
              newPurpDs={newPurpDs} setNewPurpDs={setNewPurpDs}
              newPurpRules={newPurpRules} setNewPurpRules={setNewPurpRules}
              handleAddOrg={handleAddOrg} handleAddMember={handleAddMember}
              handleAddMarking={handleAddMarking} handleAddPurpose={handleAddPurpose}
              handleToggleColumnMask={handleToggleColumnMask}
              handleToggleRowFilter={handleToggleRowFilter}
              handleExecutePlaybookStep={handleExecutePlaybookStep}
              openDiagnosticReport={openDiagnosticReport}
              showToast={showToast}
            />
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
