/**
 * Security Center — mock data & helpers
 * @license SPDX-License-Identifier: Apache-2.0
 */

import type { SecurityOrg, ProjectDAC, SecurityMarking, PurposePBAC, RowColPolicy, SecurityAuditLog } from './types';

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
