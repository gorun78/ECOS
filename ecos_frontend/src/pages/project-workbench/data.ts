/**
 * ECOS 项目工作台 — 种子数据与选项池。
 * 从 ScenarioManagementView.tsx 拆分（Lines 63-145, 150-198, 822-837）。
 */

import type { BusinessScenario } from './types';

// ==========================================
// Default Scenario Templates
// ==========================================
export const initialScenarios: BusinessScenario[] = [
  {
    id: 'scen_summer_rush',
    name: '2026 暑运航班极速调配与安全合规中心',
    description: '旨在应对暑期高温、雷雨季高密度的航班运行，提供实时、高精度的航班自动延误、备降改派，同时严防机组个人隐私（SSN/薪资）及非白名单 IP 越权读取物理表。',
    businessGoal: '将雷雨季大面积延误的决策平均时间从 45 分钟缩短至 5 分钟，且保障零安全数据合规泄露。',
    department: '民航 AOC 运行指挥部',
    priority: 'CRITICAL',
    status: 'ACTIVE',
    budget: '¥1,500,000',
    safetyIndexTarget: '99.99%',
    actualSafetyIndex: '100.00%',
    createdAt: '2026-05-15',
    bindings: {
      datasets: ['ds_flight_schedules', 'ds_fleet_costs'],
      objectTypes: ['AviationFlight', 'AviationPilot'],
      knowledgeBases: ['CAAC 121部运行合格审定规则', 'AOC 雷雨天气签派应急改派规范'],
      aiAgents: ['AOC签派大脑智能体 (王凯副本)', 'PII数据物理遮蔽卫士'],
      securityPolicies: ['purpose_fleet_opt_2026', 'proj_aviation_core', 'gr-pii'],
      interfaces: ['航空运行指挥与航班调度系统']
    },
    metrics: {
      integrityScore: 98,
      mappingCompleteness: 95,
      threatBlockRate: 100,
      slaScore: 99.8
    }
  },
  {
    id: 'scen_pilot_audit',
    name: '跨境航线及飞行员人资财务综合对账审计',
    description: '联合民航人资管理部门与财务审计团队，拉取飞行员执勤记录、飞行小时数、机票实际收益率，对账其保底薪资与飞行津贴，遵守 GDPR 隐私审计规则。',
    businessGoal: '确保年度飞行员人资成本审计合规，通过 PBAC 与 MAC 属性隔离，阻断非特定部门对机长社保保障号的越权审计。',
    department: '人资财务审计处 & DPO 办公室',
    priority: 'HIGH',
    status: 'ACTIVE',
    budget: '¥850,000',
    safetyIndexTarget: '99.50%',
    actualSafetyIndex: '99.85%',
    createdAt: '2026-06-10',
    bindings: {
      datasets: ['ds_pilots_biography', 'ds_ticket_sales'],
      objectTypes: ['AviationPilot'],
      knowledgeBases: ['民航飞行员薪资管理条例', 'GDPR 数据安全保护白皮书'],
      aiAgents: ['财务对账合规审计助理', 'DPO安全审查智能体'],
      securityPolicies: ['purpose_pilot_health_audit', 'proj_pilot_credentials', 'gr-approval'],
      interfaces: ['飞行员资质与适航合规分析大盘']
    },
    metrics: {
      integrityScore: 94,
      mappingCompleteness: 92,
      threatBlockRate: 100,
      slaScore: 99.2
    }
  },
  {
    id: 'scen_evtol_sandbox',
    name: '低空经济（eVTOL）新型载人航线物理本体演化试验',
    description: '前瞻性探索 eVTOL 载人低空客运航线规划。基于虚拟数据通道建模「航路」、「垂直起降场」、「空域气象实体」，评估逻辑实体多对多级联关系。',
    businessGoal: '完成低空经济本体模型建模，评估未来城市低空物流与客运的融合逻辑。',
    department: '新航线前沿探索战略部',
    priority: 'MEDIUM',
    status: 'DRAFT',
    budget: '¥500,000',
    safetyIndexTarget: '99.00%',
    actualSafetyIndex: '99.10%',
    createdAt: '2026-07-01',
    bindings: {
      datasets: ['ds_flight_schedules'],
      objectTypes: ['AviationFlight'],
      knowledgeBases: ['eVTOL城市空中交通试行条例'],
      aiAgents: ['低空本体演进规划助手'],
      securityPolicies: ['proj_aviation_finance'],
      interfaces: ['低代码决策可视化系统']
    },
    metrics: {
      integrityScore: 82,
      mappingCompleteness: 75,
      threatBlockRate: 98,
      slaScore: 95.0
    }
  }
];

// ==========================================
// Standard Wizard Option Pools
// ==========================================
export const AVAILABLE_DATASETS = [
  { id: 'ds_flight_schedules', name: 'ds_flight_schedules', label: '航班计划原始排班数据 (ds_flight_schedules)', desc: '民航核心排班与计划变动实时流数据' },
  { id: 'ds_fleet_costs', name: 'ds_fleet_costs', label: '航线机队燃油与维修预算成本 (ds_fleet_costs)', desc: '机队每日运行费用与维修保障成本物理表' },
  { id: 'ds_pilots_biography', name: 'ds_pilots_biography', label: '飞行员资质与基本信息数据库 (ds_pilots_biography)', desc: '包含敏感 PII（SSN、执照、家庭住址、薪酬）的主数据库' },
  { id: 'ds_ticket_sales', name: 'ds_ticket_sales', label: '每日跨境航线票务收益与对账报表 (ds_ticket_sales)', desc: '用于航司财务合规与销售对账核算' },
  { id: 'ds_aircraft_raw_records', name: 'ds_aircraft_raw_records', label: '飞机基础测绘与故障维修档案 (ds_aircraft_raw_records)', desc: '关联物理表 aircraft_records' },
  { id: 'ds_airport_geolocations', name: 'ds_airport_geolocations', label: '全球民用机场地理坐标与天气 (ds_airport_geolocations)', desc: '提供 IATA 机场代码和经纬度映射' }
];

export const AVAILABLE_OBJECTS = [
  { id: 'AviationFlight', label: '民航航班逻辑实体 (AviationFlight)', desc: '映射航班号、起降、延误等核心业务实体属性' },
  { id: 'AviationPilot', label: '民航飞行员逻辑实体 (AviationPilot)', desc: '包含执勤时间、保底工资、SSN 遮蔽规则的物理投影' },
  { id: 'AviationAircraft', label: '航空飞行器逻辑实体 (AviationAircraft)', desc: '绑定飞机尾号、适航状态、维修周期的本体实体' },
  { id: 'AviationAirport', label: '机场跑道与物理实体 (AviationAirport)', desc: '用于地理测绘、备降降准定位的逻辑地图基座' }
];

export const AVAILABLE_KNOWLEDGE = [
  { id: 'CAAC 121部运行合格审定规则', label: 'CAAC 121部运行合格审定规则', desc: '中国民航运行最严格的标准与飞行时间上限红线' },
  { id: 'AOC 雷雨天气签派应急改派规范', label: 'AOC 雷雨天气签派应急改派规范', desc: '大面积恶劣气候下的航班改派、放行优先级指南' },
  { id: '民航飞行员薪资管理条例', label: '民航飞行员薪资管理条例', desc: '指导机长、副驾驶执勤小时费、津贴合规核算对账' },
  { id: 'GDPR 数据安全保护白皮书', label: 'GDPR 数据安全保护白皮书', desc: '用于跨境航线和外籍飞行员薪资SSN合规审计的安全红线' },
  { id: 'eVTOL城市空中交通试行条例', label: 'eVTOL城市空中交通试行条例', desc: '新型低空客运和停机泊位多对多调度的前瞻指南' }
];

export const AVAILABLE_AGENTS = [
  { id: 'AOC签派大脑智能体 (王凯副本)', label: 'AOC签派大脑智能体 (王凯副本)', desc: '模拟总监决策风格，自动化大面积雷雨航班延误与改派' },
  { id: 'PII数据物理遮蔽卫士', label: 'PII数据物理遮蔽卫士', desc: '在内存计算和RUST沙箱运行中，实时遮蔽 SSN/薪资 等高度敏感隐私' },
  { id: '财务对账合规审计助理', label: '财务对账合规审计助理', desc: '利用大模型推理对账，一键找出飞行津贴与物理执勤的差异提案' },
  { id: 'DPO安全审查智能体', label: 'DPO安全审查智能体', desc: '监控非白名单 IP 越权行为并自动在拦截沙箱生成拦截日志' },
  { id: '低空本体演进规划助手', label: '低空本体演进规划助手', desc: '进行 eVTOL 模拟测试，推演空域及跑道等多对多复杂路径' },
  { id: '默认决策智能体助理', label: '默认决策智能体助理', desc: '提供基础的语义和数据对齐引导' }
];

export const AVAILABLE_INTERFACES = [
  { id: '航空运行指挥与航班调度系统', label: '航空运行指挥与航班调度系统', desc: '主要用于航班调派、计划执行和应急大盘' },
  { id: '飞行员资质与适航合规分析大盘', label: '飞行员资质与适航合规分析大盘', desc: '面向人资财务，整合薪资对账与物理时数的审计 UI' },
  { id: '低代码决策可视化系统', label: '低代码决策可视化系统', desc: '拖拽式流程画布，用于 eVTOL 及新场景的数据和实体关系推演' },
  { id: '企业零信任安全拦截审计看板', label: '企业零信任安全拦截审计看板', desc: '提供全系统物理表访问及阻断威胁拦截的看板面板' }
];

export const AVAILABLE_SECURITY = [
  { id: 'purpose_fleet_opt_2026', label: 'purpose_fleet_opt_2026 (目的型授权)', desc: '限制该场景数据仅能用于2026机队航班优化调度' },
  { id: 'proj_aviation_core', label: 'proj_aviation_core (项目隔离域)', desc: '阻断非授权子项目对民航基础表的数据穿透' },
  { id: 'gr-pii', label: 'gr-pii (敏感隐私防护网)', desc: '对机长及高管等核心人员的财务、社保进行数字物理遮蔽拦截' },
  { id: 'purpose_pilot_health_audit', label: 'purpose_pilot_health_audit (执勤健康审计授权)', desc: '仅允许在满足适航合规目的下对飞行小时数和排班进行审计' },
  { id: 'proj_pilot_credentials', label: 'proj_pilot_credentials (凭证白名单)', desc: '限制特定组织和岗位访问执照详情和安全记录' },
  { id: 'gr-approval', label: 'gr-approval (总监放行机制)', desc: '对于物理数据库的所有写回动作，强制在 ECOS 拦截挂钩中通过总监签名批准' },
  { id: 'proj_aviation_finance', label: 'proj_aviation_finance (财务安全隔离域)', desc: '确保航班实际收益及成本票务不被 AOC 签派人员明文获取' }
];

// ==========================================
// Charts Mock Data
// ==========================================
  // Mock charts data
  export const threatRadarData = [
    { name: '外部IP越权', count: 42, color: '#f43f5e' },
    { name: 'PII脱敏规避', count: 28, color: '#f59e0b' },
    { name: '过时密钥重放', count: 15, color: '#3b82f6' },
    { name: '物理端口扫描', count: 7, color: '#10b981' }
  ];

  export const efficiencyData = [
    { name: '7/2', durationSimulated: 45, durationReal: 4.8 },
    { name: '7/3', durationSimulated: 45, durationReal: 4.5 },
    { name: '7/4', durationSimulated: 45, durationReal: 4.2 },
    { name: '7/5', durationSimulated: 45, durationReal: 3.9 },
    { name: '7/6', durationSimulated: 45, durationReal: 3.5 },
    { name: '7/7', durationSimulated: 45, durationReal: 2.8 }
  ];
