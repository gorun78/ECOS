/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useRef } from 'react';
import LucideIcon from '../LucideIcon';
import {
  SecurityOrg,
  ProjectDAC,
  SecurityMarking,
  PurposePBAC,
  RowColPolicy,
  SecurityAuditLog
} from '../../pages/business-workbench/SecurityCenterView';

interface AIPCopilotDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  // State setters to control App.tsx state dynamically (all optional — c2eos callers pass only isOpen/onClose)
  viewMode?: 'ontology' | 'explorer' | 'integration' | 'knowledge' | 'aip' | 'security' | 'workshop';
  onViewModeChange?: (mode: 'ontology' | 'explorer' | 'integration' | 'knowledge' | 'aip' | 'security' | 'workshop') => void;
  selectedCategory?: 'overview' | 'object' | 'link' | 'action' | 'interface' | 'shared_property' | 'dataset' | 'function';
  onSelectCategory?: (category: 'overview' | 'object' | 'link' | 'action' | 'interface' | 'shared_property' | 'dataset' | 'function', id: string | null) => void;
  integrationTab?: 'connections' | 'syncs' | 'pipelines' | 'health' | 'lineage' | 'pipeline-builder' | 'code-repositories' | 'code-workbooks' | 'contour' | 'guide';
  onIntegrationTabChange?: (tab: 'connections' | 'syncs' | 'pipelines' | 'health' | 'lineage' | 'pipeline-builder' | 'code-repositories' | 'code-workbooks' | 'contour' | 'guide') => void;
  showToast?: (type: 'success' | 'info' | 'error', message: string) => void;

  // App-level mock ontology tables we can mutate or highlight to simulate creation
  objectTypes?: any[];
  setObjectTypes?: React.Dispatch<React.SetStateAction<any[]>>;
  linkTypes?: any[];
  setLinkTypes?: React.Dispatch<React.SetStateAction<any[]>>;
  datasets?: any[];
  setDatasets?: React.Dispatch<React.SetStateAction<any[]>>;

  // Security Center Control States
  securityTab?: 'overview' | 'orgs' | 'dac' | 'mac' | 'pbac' | 'row_col' | 'audit';
  onSecurityTabChange?: (tab: 'overview' | 'orgs' | 'dac' | 'mac' | 'pbac' | 'row_col' | 'audit') => void;
  securityOrgs?: SecurityOrg[];
  setSecurityOrgs?: React.Dispatch<React.SetStateAction<SecurityOrg[]>> | ((val: SecurityOrg[]) => void);
  securityProjects?: ProjectDAC[];
  setSecurityProjects?: React.Dispatch<React.SetStateAction<ProjectDAC[]>> | ((val: ProjectDAC[]) => void);
  securityMarkings?: SecurityMarking[];
  setSecurityMarkings?: React.Dispatch<React.SetStateAction<SecurityMarking[]>> | ((val: SecurityMarking[]) => void);
  securityPurposes?: PurposePBAC[];
  setSecurityPurposes?: React.Dispatch<React.SetStateAction<PurposePBAC[]>> | ((val: PurposePBAC[]) => void);
  securityRowColPolicies?: RowColPolicy[];
  setSecurityRowColPolicies?: React.Dispatch<React.SetStateAction<RowColPolicy[]>> | ((val: RowColPolicy[]) => void);
  securityAuditLogs?: SecurityAuditLog[];
  setSecurityAuditLogs?: React.Dispatch<React.SetStateAction<SecurityAuditLog[]>> | ((val: SecurityAuditLog[]) => void);
  securitySimUser?: string;
  setSecuritySimUser?: (val: string) => void;
  securitySimDataset?: string;
  setSecuritySimDataset?: (val: string) => void;
  securitySimPurpose?: string;
  setSecuritySimPurpose?: (val: string) => void;
  securitySimResult?: { verdict: 'GRANTED' | 'DENIED'; traces: string[] } | null;
  setSecuritySimResult?: (val: { verdict: 'GRANTED' | 'DENIED'; traces: string[] } | null) => void;
  securitySelectedRowColDs?: string;
  setSecuritySelectedRowColDs?: (val: string) => void;
}

interface Message {
  id: string;
  sender: 'user' | 'agent';
  text: string;
  timestamp: string;
  isExecuting?: boolean;
  executionStep?: string;
  completed?: boolean;
  automationType?: 'pipeline' | 'ontology' | 'health' | 'lineage' | 'sec_gdpr' | 'sec_finance' | 'sec_row_col' | 'sec_audit' | 'ws_generate_dashboard' | 'ws_auto_bind' | 'ws_inject_copilot' | 'ws_transform_theme';
}

export default function AIPCopilotDrawer({
  isOpen,
  onClose,
  viewMode = 'aip',
  onViewModeChange = () => {},
  selectedCategory = 'overview',
  onSelectCategory = () => {},
  integrationTab = 'connections',
  onIntegrationTabChange = () => {},
  showToast = () => {},
  objectTypes = [],
  setObjectTypes = (() => {}) as any,
  linkTypes = [],
  setLinkTypes = (() => {}) as any,
  datasets = [],
  setDatasets = (() => {}) as any,

  securityTab = 'overview',
  onSecurityTabChange = () => {},
  securityOrgs = [],
  setSecurityOrgs = (() => {}) as any,
  securityProjects = [],
  setSecurityProjects = (() => {}) as any,
  securityMarkings = [],
  setSecurityMarkings = (() => {}) as any,
  securityPurposes = [],
  setSecurityPurposes = (() => {}) as any,
  securityRowColPolicies = [],
  setSecurityRowColPolicies = (() => {}) as any,
  securityAuditLogs = [],
  setSecurityAuditLogs = (() => {}) as any,
  securitySimUser = '',
  setSecuritySimUser = () => {},
  securitySimDataset = '',
  setSecuritySimDataset = () => {},
  securitySimPurpose = '',
  setSecuritySimPurpose = () => {},
  securitySimResult = null,
  setSecuritySimResult = () => {},
  securitySelectedRowColDs = '',
  setSecuritySelectedRowColDs = () => {},
}: AIPCopilotDrawerProps) {
  const [messages, setMessages] = useState<Message[]>([
    {
      id: 'welcome',
      sender: 'agent',
      text: '您好！我是 **ECOS AIP 智能协处理器** 🤖。\n\n数据集成与本体（Ontology）配置的多级操作链常常由于跨越“物理数仓 - 调度 - ETL - 语义层 - 关系链”而显得极其繁琐。我已激活，旨在通过**自然语言智能代理（Agent）**一键帮您跨模块编排与自动转译！\n\n您可以**直接输入您的开发诉求**，或点击下方的 **AIP 一键高频开发代理**：',
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    }
  ]);
  const [inputText, setInputText] = useState('');
  const [isTyping, setIsTyping] = useState(false);
  const [currentStep, setCurrentStep] = useState<string>('');
  const [executionProgress, setExecutionProgress] = useState<number>(0);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // Auto-scroll chat to bottom
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isTyping, currentStep]);

  if (!isOpen) return null;

  // Pre-configured Intelligent Agents (Presettings)
  const dataScenarios = [
    {
      title: '一键自动构建清洗 Pipeline',
      desc: '智能将 Bronze 物理源合并清洗并输出 Silver 级宽表',
      prompt: '为北京首都机场(PEK)航班构建自动清洗和关联管道，剔除延迟 < 5分钟的冗余行，并合并飞行员姓名数据。',
      type: 'pipeline' as const
    },
    {
      title: '一键自动构建实体 Ontology',
      desc: '读取物理结构，全自动注册实体并建立多端关联关系',
      prompt: '全自动分析清洗后的航班宽表，将其向 Ontology 同步发布为全新实体 [AviationFlight]，设置主键并关联 [AviationPilot] 实体。',
      type: 'ontology' as const
    },
    {
      title: '一键注入全面数据质量监控',
      desc: '自动识别字段，智能推荐并挂载 SLA 时效与空值防污染断言',
      prompt: '针对生产数据集 [ds_flights_clean] 注入 3 组严苛的数据质量健康检查(Data Health Check)。',
      type: 'health' as const
    },
    {
      title: '一键全链路数据血缘探查',
      desc: '智能探查指定表的所有上下游依赖拓扑图，定位延迟源头',
      prompt: '帮我探查 [AviationFlight] 的端到端血缘依赖地图，看看其下游是否影响了 4 个消费数据集？',
      type: 'lineage' as const
    }
  ];

  const securityScenarios = [
    {
      title: '一键部署 GDPR 零信任隔离域',
      desc: '智能创建欧盟专属网段、控制专案 ACL、应用标记密级锁并挂载合规脱敏用途及策略',
      prompt: '为欧盟客运数据构建端到端的 GDPR 零信任隔离体系，自动完成组织、项目ACL、安全标记和分析用途绑定。',
      type: 'sec_gdpr' as const
    },
    {
      title: '一键挂载飞行员档案行列脱敏',
      desc: '对 SSN 及薪资执行物理级抹除，过滤非本年飞满 300 小时或非 HR 账户数据',
      prompt: '为 ds_pilots_biography 飞行员档案数据集配置最高机密行列安全策略，阻断非HR人员查看核心隐私字段。',
      type: 'sec_row_col' as const
    },
    {
      title: '一键筑牢财务最高机密堡垒',
      desc: '限制专用 IP Whitelist、加挂营收密级锁锁、演示过期生命周期解密密钥的自动吊销与拦截',
      prompt: '为客票营收明细数据 ds_ticket_sales 构建一键财务最高机密内审屏障，并演示生命周期到期及动态行列过滤。',
      type: 'sec_finance' as const
    },
    {
      title: '一键执行系统合规风险审计',
      desc: '智能挖掘近 1 小时 1,450 条访问日志中的越权扫描及非法 IP 探测，自动上报防御诊断书',
      prompt: '请帮我审计过去 1 小时的系统访问日志，找出所有被拦截(DENIED)的越权尝试并生成风险告警分析。',
      type: 'sec_audit' as const
    }
  ];

  const workshopScenarios = [
    {
      title: 'AIP 一键生成航班运行监控看板',
      desc: '智能创建全新仪表盘并挂载指标卡、航班状态条形图、离港航班表格等全套低代码组件',
      prompt: '请使用 AIP 智能体在应用构建中心一键组装一个【航班运行综合大盘】，自动生成指标卡、状态分布柱状图、到离港航班清单以及航班本体视图！',
      type: 'ws_generate_dashboard' as const
    },
    {
      title: 'AIP 自动配置实体交互与变量绑定',
      desc: '自动创建选中状态变量，并将其在表格选中事件与实体详情视图之间进行双向绑定',
      prompt: '帮我将航班明细表格的【选中行输出】与详情卡片的【绑定目标变量】进行关联绑定，将它们同时挂载到 v_selected_flight 变量上，实现无代码联动！',
      type: 'ws_auto_bind' as const
    },
    {
      title: 'AIP 一键注入智能 AI 协处理器面板',
      desc: '在画布右侧一键挂载 AI 交互框组件，允许最终用户通过自然语言生成/操作实体',
      prompt: '请在页面右侧一键挂载全新的【AIP Copilot 智能辅助面板】组件，实现用户在运行时直接使用自然语言操作航班状态！',
      type: 'ws_inject_copilot' as const
    },
    {
      title: 'AIP 智能转换深色主题与品牌设计',
      desc: '根据集团VI一键切换深色主题(Dark Mode)并应用全新的靛蓝(Indigo)科技感品牌配色',
      prompt: '帮我将当前应用的主题一键转换为深色模式(Dark Mode)，品牌标题修改为【AIP 航空智能联合指挥控制中心】，主色调设为极客靛蓝(Indigo)！',
      type: 'ws_transform_theme' as const
    }
  ];

  const agentScenarios = 
    viewMode === 'security' ? securityScenarios : 
    viewMode === 'workshop' ? workshopScenarios : 
    dataScenarios;

  const sleep = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));

  const runAgentAutomation = async (
    type: 'pipeline' | 'ontology' | 'health' | 'lineage' | 'sec_gdpr' | 'sec_row_col' | 'sec_finance' | 'sec_audit' | 'ws_generate_dashboard' | 'ws_auto_bind' | 'ws_inject_copilot' | 'ws_transform_theme',
    customPromptText?: string
  ) => {
    const userPrompt = customPromptText || (agentScenarios.find(s => s.type === type)?.prompt || '');
    
    // Add User Message
    const userMsgId = `user-${Date.now()}`;
    setMessages(prev => [...prev, {
      id: userMsgId,
      sender: 'user',
      text: userPrompt,
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    }]);

    setIsTyping(true);
    await sleep(800);

    // Initial Agent response about Starting
    const agentMsgId = `agent-exec-${Date.now()}`;
    const scenarioName = 
      type === 'pipeline' ? '自动化数据清洗算子编排' :
      type === 'ontology' ? '自动化语义实体映射' :
      type === 'health' ? '自动化数据健壮性规则挂载' :
      type === 'lineage' ? '数据依赖血缘回溯' :
      type === 'sec_gdpr' ? 'GDPR 零信任物理/逻辑层端到端围栏部署' :
      type === 'sec_row_col' ? '行列级安全策略动态注入与掩蔽部署' :
      type === 'sec_finance' ? '高敏财务安全壁垒与生命周期密钥拦截部署' :
      '系统日志 AI 智能风险审计与合规评估';

    setMessages(prev => [...prev, {
      id: agentMsgId,
      sender: 'agent',
      text: `🤖 **AIP 执行代理启动。分析输入意图...**\n\n- 🎯 **意图检测**: ${scenarioName}\n- 🌲 **安全物理引擎**: ECOS AIP Dynamic Policy & Security Compiler\n\n**正在为您启动全链条自动漫游修改，请密切注意左侧主界面的动态变化...**`,
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
      isExecuting: true,
      executionStep: '意图编译中...',
      automationType: type
    }]);

    setIsTyping(false);

    if (type === 'pipeline') {
      // Step 1: Switch View to Data Integration Workspace
      setCurrentStep('正在切换到【数据工作台】...');
      await sleep(1000);
      onViewModeChange('integration');
      
      // Step 2: Switch Tab to Pipeline Builder
      setCurrentStep('正在进入【Pipeline Builder 算子图】页面...');
      await sleep(1000);
      onIntegrationTabChange('pipeline-builder');

      // Step 3: Populate or add steps (simulate visual builder highlighting)
      setCurrentStep('正在智能挂载 Data Cleaning 算子链 (coalesce, trim)...');
      await sleep(1500);
      showToast('info', 'AIP 智能挂载：trim(flight_num) 已注入 active_flights 节点');

      // Step 4: Add Join condition
      setCurrentStep('正在智能寻找主外键，创建 Join(flights_raw, pilots_raw) 节点...');
      await sleep(1500);
      showToast('success', 'AIP 智能关联完成！北京航班成功与 pilots 宽表建立 Inner Hash Join 逻辑');

      // Finish Pipeline Automation
      setMessages(prev => prev.map(m => m.id === agentMsgId ? {
        ...m,
        text: `🤖 **AIP 管道自动构建成功！**\n\n我已经为您全自动执行了原本需要数十步人工操作的 ETL 流程：\n1. 自动定位了 \`postgres_prod_db\` 的物理表并切入 **Pipeline Builder**；\n2. 为原始表挂载了 \`trim(flight_num)\` 和 \`upper(dep_airport)\` 过滤算子；\n3. 通过分析物理键，建立了 **flights 与 pilots** 的 inner join 分区融合物理算子；\n4. 结果已输出并在下方 **Doris 预览流** 挂载。您现在可在此直接执行一键编译发布！`,
        isExecuting: false,
        completed: true
      } : m));
      setCurrentStep('');

    } else if (type === 'ontology') {
      // Step 1: Switch View to Ontology Config
      setCurrentStep('正在切换到【本体配置控制台 (Ontology Manager)】...');
      await sleep(1000);
      onViewModeChange('ontology');
      onSelectCategory('overview', null);

      // Step 2: Simulate adding a new ObjectType
      setCurrentStep('正在从 flights_clean 读取字段元数据类型...');
      await sleep(1200);

      setCurrentStep('正在自动在全局语义中注册 [AviationFlight] 新物理实体...');
      await sleep(1500);
      
      // Mutate App.tsx state to actually add the object type in real-time!
      const exists = objectTypes.some(o => o.id === 'obj_aviation_flight');
      if (!exists) {
        const newObj = {
          id: 'obj_aviation_flight',
          displayName: '航班实体 (AviationFlight)',
          apiName: 'AviationFlight',
          description: '由 AIP 智能代理通过 flights_clean 数据集一键转译、挂载的官方生产航班业务对象，拥有标准 SLA 属性绑定。',
          iconName: 'Plane',
          primaryKey: 'flight_id',
          titleProperty: 'flight_num',
          status: 'draft',
          properties: [
            { name: 'flight_id', displayName: '航班唯一编号 (ID)', type: 'string', isPrimaryKey: true },
            { name: 'flight_num', displayName: '航班号 (Num)', type: 'string' },
            { name: 'dep_airport', displayName: '出发机场 (Dep)', type: 'string' },
            { name: 'arr_airport', displayName: '到达机场 (Arr)', type: 'string' },
            { name: 'is_delayed', displayName: '是否延误 (IsDelayed)', type: 'boolean' }
          ]
        };
        setObjectTypes([newObj, ...objectTypes]);
        
        // Add a relationship
        const newLink = {
          id: 'link_flight_pilot',
          displayName: '航班 关联 飞行员 (Flight_to_Pilot)',
          apiName: 'Flight_to_Pilot',
          description: 'AIP 智能探查并建议的航班与飞行员一端对多端全局逻辑链关系。',
          sourceObjectType: 'obj_aviation_flight',
          targetObjectType: 'obj_pilot',
          cardinality: 'MANY_TO_ONE' as const,
          foreignKey: 'pilot_id',
          status: 'draft'
        };
        setLinkTypes([newLink, ...linkTypes]);
      }

      // Select the newly created ObjectType so the UI displays it!
      onSelectCategory('object', 'obj_aviation_flight');
      showToast('success', 'AIP Agent 已在语义层一键为您生成并关联 [AviationFlight] 业务实体！');

      setMessages(prev => prev.map(m => m.id === agentMsgId ? {
        ...m,
        text: `🤖 **AIP 本体实体一键生成及映射挂载成功！**\n\n我已为您自动完成了以下高频但繁杂的“物理 -> 逻辑”转译：\n1. 自动加载 \`flights_clean\` 的物理 Schema；\n2. 一键创建全新本体对象类型 **[航班实体 (AviationFlight)]** 并在左侧侧边栏高亮呈现；\n3. 智能推断主键属性为 \`flight_id\`，展示标题为 \`flight_num\`；\n4. 智能识别 \`pilot_id\` 物理外键，全自动与现有 **飞行员实体 (obj_pilot)** 建立了 \`MANY_TO_ONE\` 的全局语义链接！\n\n您现在可以在本体详情中直接查看或进行微调，随后点击顶部【保存本体】即可同步生产。`,
        isExecuting: false,
        completed: true
      } : m));
      setCurrentStep('');

    } else if (type === 'health') {
      // Step 1: Go to Data Integration
      setCurrentStep('正在切换到【数据工作台】...');
      await sleep(1000);
      onViewModeChange('integration');

      // Step 2: Go to Health Tab
      setCurrentStep('正在进入【数据质量健康检测】模块...');
      await sleep(1000);
      onIntegrationTabChange('health');

      // Step 3: Inject Health Guardrules
      setCurrentStep('正在为目标宽表智能推荐并配置 Row Count 极小值安全规则 (报警阈值: >1000)...');
      await sleep(1500);
      showToast('info', 'SLA 挂载完成: row_count_check 已追加');

      setCurrentStep('正在自动创建 [pilot_id] 的空值比例污染拦截熔断器 (阈值: <1.0%)...');
      await sleep(1500);
      showToast('success', 'AIP 拦截器配置通过：发现异常空值超过 1% 将阻断下游 Ontology 自动更新发布');

      setMessages(prev => prev.map(m => m.id === agentMsgId ? {
        ...m,
        text: `🤖 **AIP 质量监控及熔断健康拦截注入成功！**\n\n为了保障下游业务使用的安全性，我已全自动完成了如下配置，并自动切换至质量中心：\n1. 推荐并注入 **行数规模断言** (Row Count Assertion, 保证每天生产增量数据不低于 1,000 行，防上游断流)；\n2. 推荐并注入 **空值率防污染熔断器** (Null Percentage Guard, 严格监控 \`pilot_id\`，超过 1% 即发出报警并阻断本体状态机更新，保障时效 SLA)；\n3. 与 Doris 离线质量扫描底座进行热绑定，实现多维极秒级诊断。`,
        isExecuting: false,
        completed: true
      } : m));
      setCurrentStep('');

    } else if (type === 'lineage') {
      // Step 1: Go to Lineage
      setCurrentStep('正在切换到【数据工作台】...');
      await sleep(800);
      onViewModeChange('integration');

      // Step 2: Go to Lineage Tab
      setCurrentStep('正在调取 Apache Doris 元数据血缘追踪，绘制拓扑 DAG...');
      await sleep(1200);
      onIntegrationTabChange('lineage');

      setMessages(prev => prev.map(m => m.id === agentMsgId ? {
        ...m,
        text: `🤖 **AIP 物理链路拓扑探查完成！**\n\n已为您智能定位了该实体的全局血缘树，并已经将主窗口切换到了 **全链路数据血缘地图 (Data Lineage Map)**：\n- 🔍 **上游溯源**: \`postgres_prod_db.flights_record\` [原始提取] -> \`flights_raw\` [Bronze] -> \`active_flights\` [Spark清洗] -> \`ds_flights_clean\` [Doris物理宽表] -> **航班实体 (AviationFlight)** [Ontology 逻辑层]\n- 📊 **下游时效影响评估**: 发现该节点下方被 4 个敏捷分析卡片及下游 Workbook 所订阅。如果上游产生 5 分钟以上的调度延迟，由于血缘拓扑无环，将会导致 **4 个敏捷报表面临数据时效性延迟风险**。建议已提交给 Data Scheduler。`,
        isExecuting: false,
        completed: true
      } : m));
      setCurrentStep('');

    } else if (type === 'sec_gdpr') {
      // Step 1: Switch View to Security Workspace
      setCurrentStep('正在切换到【安全中心控制台】...');
      await sleep(1000);
      onViewModeChange('security');

      // Step 2: Create Org
      setCurrentStep('正在创建欧盟专属物理隔离网域 [Org_EU_Ops] 并限制白名单 IP 段...');
      await sleep(1200);
      onSecurityTabChange('orgs');
      
      const existsOrg = securityOrgs.some(o => o.id === 'Org_EU_Ops');
      if (!existsOrg) {
        const newOrg: SecurityOrg = {
          id: 'Org_EU_Ops',
          name: '欧盟运营分部 (Org_EU_Ops)',
          isolationMode: true,
          memberCount: 120,
          ipRanges: ['10.150.0.0/16', '10.152.0.0/24'],
          crossOrgSharing: [],
          createdAt: new Date().toISOString().split('T')[0]
        };
        setSecurityOrgs([newOrg, ...securityOrgs]);
        showToast('success', 'AIP 部署成功：物理隔离组织域 Org_EU_Ops 已配置');
      }

      // Step 3: Project DAC
      setCurrentStep('正在创建 GDPR 旅客合规高敏感项目域 [proj_passenger_eu] 并配置 ACL 主体...');
      await sleep(1200);
      onSecurityTabChange('dac');

      const existsProj = securityProjects.some(p => p.id === 'proj_passenger_eu');
      if (!existsProj) {
        const newProj: ProjectDAC = {
          id: 'proj_passenger_eu',
          name: '欧盟旅客隐私项目 (proj_passenger_eu)',
          description: '包含欧盟始发/终到旅客舱单、过境清关数据、会员敏感PII等，受GDPR法律严格控制。',
          members: [
            { username: 'admin_guorong', role: 'Owner', grantedBy: 'System', grantedAt: '2026-07-04' },
            { username: 'analyst_li', role: 'Editor', grantedBy: 'admin_guorong', grantedAt: '2026-07-04' }
          ],
          discoverableAllOrgs: false,
          autoPropagation: true
        };
        setSecurityProjects([newProj, ...securityProjects]);
        showToast('success', 'AIP 部署成功：专案 DAC 网卡 proj_passenger_eu 已激活');
      }

      // Step 4: Security Marking Lock
      setCurrentStep('正在配置高敏感密级锁 [GDPR_PII] 并强制绑定至 ds_passenger_manifest 数据集...');
      await sleep(1200);
      onSecurityTabChange('mac');

      const existsMarking = securityMarkings.some(m => m.id === 'M_GDPR_PII');
      if (existsMarking) {
        const updated = securityMarkings.map(m => m.id === 'M_GDPR_PII' ? {
          ...m,
          appliedDatasets: Array.from(new Set([...m.appliedDatasets, 'ds_passenger_manifest']))
        } : m);
        setSecurityMarkings(updated);
        showToast('success', 'AIP 密级挂锁成功：ds_passenger_manifest 强物理隔离生效');
      }

      // Step 5: Purpose PBAC
      setCurrentStep('正在发布欧盟合规特定用途 [purpose_eu_passenger_audit] 限制不当解密...');
      await sleep(1200);
      onSecurityTabChange('pbac');

      const existsPurp = securityPurposes.some(p => p.id === 'purpose_eu_passenger_audit');
      if (!existsPurp) {
        const newPurp: PurposePBAC = {
          id: 'purpose_eu_passenger_audit',
          name: '欧盟合规客舱审查 (EU Passenger Audit)',
          description: '用于欧盟GDPR合规性审计，必须对敏感PII完成自动掩码与哈希脱敏。',
          authorizedUsers: ['analyst_li', 'eu_dpo_officer'],
          inputDatasets: ['ds_passenger_manifest'],
          redactionRules: ['MASK(customer_name)', 'HASH(passport_no)'],
          expiresAt: '2027-01-01',
          status: 'ACTIVE'
        };
        setSecurityPurposes([newPurp, ...securityPurposes]);
        showToast('success', 'AIP 用途注册成功：仅授权此用途可以申请动态密匙');
      }

      // Step 6: Trigger Simulator
      setCurrentStep('正在进行端到端零信任解密链路自检，启动策略编译器进行模拟判定...');
      await sleep(1500);
      onSecurityTabChange('overview');

      setSecuritySimUser('analyst_li');
      setSecuritySimDataset('ds_passenger_manifest');
      setSecuritySimPurpose('purpose_eu_passenger_audit');
      setSecuritySimResult({
        verdict: 'GRANTED',
        traces: [
          '1. 验证用户 analyst_li 属于欧盟运营分部组织：验证通过。',
          '2. 检测目标数据集 ds_passenger_manifest 挂载了 GDPR_PII 标记锁：用户所在组已授权。',
          '3. 匹配当前执行用途「欧盟合规客舱审查」：判定合法且未过期。',
          '4. 动态规则继承：检测到2个脱敏规则 MASK(customer_name) 与 HASH(passport_no)，在渲染层强制激活生效。',
          '✅ 判定结果：[GRANTED] 允许解密访问（列级脱敏生效中）。'
        ]
      });

      // Step 7: Inject audit log
      const newLog: SecurityAuditLog = {
        id: `log_playbook_${Date.now()}`,
        timestamp: new Date().toTimeString().split(' ')[0],
        username: 'analyst_li',
        orgId: 'Org_EU_Ops',
        resourceId: 'ds_passenger_manifest',
        resourceType: 'Dataset',
        action: 'READ_DATASET',
        status: 'SUCCESS',
        details: '「AIP 零信任围栏」判定通过：analyst_li 成功解密读取 ds_passenger_manifest，已强制实施 GDPR_PII 遮蔽策略。'
      };
      setSecurityAuditLogs([newLog, ...securityAuditLogs]);

      setMessages(prev => prev.map(m => m.id === agentMsgId ? {
        ...m,
        text: `🤖 **AIP 欧盟 GDPR 零信任隔离围栏一键配置部署成功！**\n\n我已经为您全自动串联了如下物理及逻辑合规规则，秒级解决传统跨团队多端协作的繁琐：\n1. **物理网络域**: 创建欧盟专属物理隔离域 **Org_EU_Ops**，封禁域外不合规网段；\n2. **专案 ACL**: 自动配置 **proj_passenger_eu** 项目阻断外域探测与横向移动；\n3. **密级标记锁**: 对数据集挂载密级锁 **GDPR_PII**，强力阻断非授权组成员读取；\n4. **PBAC 审计目的**: 建立 **欧盟合规客舱审查** 用途，限制只能基于特定用途进行非明文读取；\n5. **一键仿真校验**: 自动帮您在安全大盘中运行仿真编译器，输出 **[GRANTED]** 自检判定并注入实时安全日志。`,
        isExecuting: false,
        completed: true
      } : m));
      setCurrentStep('');

    } else if (type === 'sec_row_col') {
      // Step 1: Switch View to Security Workspace
      setCurrentStep('正在切换到【安全中心控制台】...');
      await sleep(800);
      onViewModeChange('security');

      // Step 2: Switch Tab to Row/Col Policies
      setCurrentStep('正在定位飞行员档案数据集 ds_pilots_biography 安全策略...');
      await sleep(1000);
      onSecurityTabChange('row_col');
      setSecuritySelectedRowColDs('ds_pilots_biography');

      // Step 3: Update Row/Col security policies with SSN & Salary REDACT
      setCurrentStep('正在自动为机组敏感字段(ssn_number, base_salary)注入最高密级 REDACT 抹除策略，并加载 row-filter 逻辑 SQL...');
      await sleep(1500);

      const updatedPolicies = securityRowColPolicies.map(p => p.datasetId === 'ds_pilots_biography' ? {
        ...p,
        columnMasks: [
          { column: 'ssn_number', maskType: 'REDACT' as const, active: true },
          { column: 'email_address', maskType: 'PARTIAL' as const, active: true },
          { column: 'base_salary', maskType: 'REDACT' as const, active: true }
        ],
        rowFilters: [
          { filterSql: "flight_hours_ytd > 300 OR role = 'HR_DIRECTOR'", description: "非HR仅显示本年累计飞行满300小时的资深教官", active: true }
        ]
      } : p);
      setSecurityRowColPolicies(updatedPolicies);

      // Step 4: Add Audit Log
      const newLog: SecurityAuditLog = {
        id: `log_rowcol_${Date.now()}`,
        timestamp: new Date().toTimeString().split(' ')[0],
        username: 'admin_guorong',
        orgId: 'org_aviation_hq',
        resourceId: 'ds_pilots_biography',
        resourceType: 'Dataset',
        action: 'UPDATE_POLICY',
        status: 'SUCCESS',
        details: 'AIP 智能安全代理：成功为飞行员隐私档案 ds_pilots_biography 热挂载行列过滤器并激活。'
      };
      setSecurityAuditLogs([newLog, ...securityAuditLogs]);
      showToast('success', 'AIP 行列级策略：ds_pilots_biography 过滤屏蔽规则挂载成功！');

      setMessages(prev => prev.map(m => m.id === agentMsgId ? {
        ...m,
        text: `🤖 **AIP 机组机密档案行列级脱敏策略部署成功！**\n\n我已为您自动在最终渲染输出层挂载了如下高级热插拔行列过滤器：\n1. **列级加密脱敏 (Column Masking)**:\n   - 对核心物理标识 \`ssn_number\`、\`base_salary\` 强行应用 **REDACT 绝对遮蔽**，非 HR 身份用户访问时，解密视图渲染直接抹除为星号，保障极高薪资防泄密合规；\n   - \`email_address\` 自动转换为 **PARTIAL 部分脱敏**。\n2. **行级物理隔离 (Row Filtering)**:\n   - 动态拼接 SQL 主体 \`flight_hours_ytd > 300 OR role = 'HR_DIRECTOR'\`。非 HR 管理员只能看到满足 300 小时特等功飞行教官数据，实现了极其严密的数据横向逻辑安全隔离；\n3. **防穿透审计**: 安全事件已插桩，全物理监控。`,
        isExecuting: false,
        completed: true
      } : m));
      setCurrentStep('');

    } else if (type === 'sec_finance') {
      // Step 1: Switch View to Security Workspace
      setCurrentStep('正在切换到【安全中心控制台】...');
      await sleep(800);
      onViewModeChange('security');

      // Step 2: Go to Orgs
      setCurrentStep('正在创建高密财务总部专用网段 [Org_Finance_Dept] 并锁定 IP Whitelist...');
      await sleep(1000);
      onSecurityTabChange('orgs');

      const existsOrg = securityOrgs.some(o => o.id === 'Org_Finance_Dept');
      if (!existsOrg) {
        const newOrg: SecurityOrg = {
          id: 'Org_Finance_Dept',
          name: '高密财务部分部 (Org_Finance_Dept)',
          isolationMode: true,
          memberCount: 45,
          ipRanges: ['10.130.0.0/16', '192.168.10.0/24'],
          crossOrgSharing: ['org_aviation_hq'],
          createdAt: new Date().toISOString().split('T')[0]
        };
        setSecurityOrgs([newOrg, ...securityOrgs]);
        showToast('success', 'AIP 部署成功：物理阻断财务 Org_Finance_Dept 已激活');
      }

      // Step 3: MAC Markings tab
      setCurrentStep('正在激活客票高敏营收标记锁 [M_SENSITIVE_REVENUE] 并绑定目标表 ds_ticket_sales...');
      await sleep(1200);
      onSecurityTabChange('mac');

      const existsMarking = securityMarkings.some(m => m.id === 'M_SENSITIVE_REVENUE');
      if (existsMarking) {
        const updated = securityMarkings.map(m => m.id === 'M_SENSITIVE_REVENUE' ? {
          ...m,
          appliedDatasets: Array.from(new Set([...m.appliedDatasets, 'ds_ticket_sales']))
        } : m);
        setSecurityMarkings(updated);
        showToast('success', 'AIP 营收锁开启：ds_ticket_sales 开启高密级防穿透防护');
      }

      // Step 4: Purposes tab - expired purpose and key rotation demonstration
      setCurrentStep('正在加载已过期财务审计用途 [purpose_expired_finance] 密钥生命周期，模拟吊销拦截行为...');
      await sleep(1200);
      onSecurityTabChange('pbac');

      // Step 5: Simulator tab
      setCurrentStep('正在在安全概览中运行仿真模拟，校验 analyst_li 跨期强行解密 ds_ticket_sales 表的行为...');
      await sleep(1500);
      onSecurityTabChange('overview');

      setSecuritySimUser('analyst_li');
      setSecuritySimDataset('ds_ticket_sales');
      setSecuritySimPurpose('purpose_expired_finance');
      setSecuritySimResult({
        verdict: 'DENIED',
        traces: [
          '1. 验证用户 analyst_li 属于航空集团总部：通过。',
          '2. 检测目标数据集 ds_ticket_sales 挂载了 M_SENSITIVE_REVENUE (最高机密锁)：用户所在组未直接授权。',
          '3. 匹配当前执行用途「2024年度财务审计归档」：警告！该用途状态为 EXPIRED (已于 2025-05-01 过期)。',
          '❌ 判定结果：[DENIED] 访问被阻断。理由：授权用途已过期，解密密钥被自动吊销。'
        ]
      });

      // Inject denied audit log
      const logDenied: SecurityAuditLog = {
        id: `log_denied_${Date.now()}`,
        timestamp: new Date().toTimeString().split(' ')[0],
        username: 'analyst_li',
        orgId: 'org_aviation_hq',
        resourceId: 'ds_ticket_sales',
        resourceType: 'Dataset',
        action: 'READ_DATASET',
        status: 'DENIED',
        details: '安全告警拦截：analyst_li 尝试使用已过期的财务审计用途访问高密资产 ds_ticket_sales 被智能阻断。'
      };
      setSecurityAuditLogs([logDenied, ...securityAuditLogs]);
      showToast('error', '安全拦截：analyst_li 跨期未授权读取被安全大盘物理截断！');

      setMessages(prev => prev.map(m => m.id === agentMsgId ? {
        ...m,
        text: `🤖 **AIP 财务营收资产最高防护及生命周期吊销演示挂载完成！**\n\n已成功通过物理和逻辑的双向绑定，筑牢财务审计边界：\n1. **白名单阻断**: 创建 **Org_Finance_Dept** 分部并封锁非法 IP 网段；\n2. **标记锁定**: 对客票明细表 **ds_ticket_sales** 应用营收顶密锁 **M_SENSITIVE_REVENUE**，实现强阻断；\n3. **解密密钥自动吊销 (Key Rotation)**: 当系统检测到用途 \`purpose_expired_finance\` 超过 2025-05-01 归档生命周期且状态处于 **EXPIRED** 时，解密卡物理密钥自动置零吊销；\n4. **策略模拟判定**: 已在主窗口概览中激活模拟，判定输出 **[DENIED]** 红牌，同时向审计控制中心上报高危越权告警。`,
        isExecuting: false,
        completed: true
      } : m));
      setCurrentStep('');

    } else if (type === 'sec_audit') {
      // Step 1: Switch View to Security Workspace
      setCurrentStep('正在切换到【安全中心控制台】...');
      await sleep(800);
      onViewModeChange('security');

      // Step 2: Switch Tab to Audit Logs
      setCurrentStep('正在调用 Apache Doris 安全网关日志中心，过滤过去 1 小时全部 1,450 条访问审计...');
      await sleep(1500);
      onSecurityTabChange('audit');

      // Inject custom alert logs
      const alertLogs: SecurityAuditLog[] = [
        {
          id: `alert_01_${Date.now()}`,
          timestamp: new Date().toTimeString().split(' ')[0],
          username: 'unknown_scanner',
          orgId: 'EXTERNAL_IP',
          resourceId: 'ds_special_routes',
          resourceType: 'Dataset',
          action: 'READ_DATASET',
          status: 'DENIED',
          details: '【AIP 智能警报】高危越权：未知外部 IP 198.51.100.45 尝试通过扫描高敏感军用航路明细被拦截阻断。'
        },
        {
          id: `alert_02_${Date.now()}`,
          timestamp: new Date().toTimeString().split(' ')[0],
          username: 'external_auditor',
          orgId: 'org_contractor',
          resourceId: 'proj_pilot_credentials',
          resourceType: 'Project',
          action: 'DISCOVER_PROJECT',
          status: 'DENIED',
          details: '【AIP 智能警报】黑产穿透扫描：外协审计员在无合法分析用途上下文状态下尝试绕过 ACL 被拒绝。'
        }
      ];
      setSecurityAuditLogs([...alertLogs, ...securityAuditLogs]);
      showToast('error', 'AIP 日志扫描：发现 2 起严重越权高敏拦截告警，风险已被扼杀！');

      setMessages(prev => prev.map(m => m.id === agentMsgId ? {
        ...m,
        text: `🔍 **AIP 联邦安全网关合规风险审计报告**\n\n已为您自动审计了过去 1 小时的 **1,450 条** 数据解密及读取请求，进行智能语义分析：\n- 🛡️ **安全大盘健康度**: **98.2%**\n- 🛑 **严重安全风险事件**: **2 起** (已全部成功掐灭，并自动投掷并置顶于安全审计控制中心主页，请查收)\n\n### 🚨 被物理拦截的高危越权威胁定位分析：\n1. **[高危拦截] 外部黑客网段刺探**:\n   - **请求详情**: 未授权外部 IP \`198.51.100.45\` 曾尝试高频越权扫描军航空路机密数据集 \`ds_special_routes\`。\n   - **拦截原因**: 数据集被密级锁 \`M_MILITARY_FLIGHT\` 顶格保护，该 IP 也不在集团物理准入 IP 网段。\n   - **防范建议**: 建议通过一键部署，将其 IP 直接加入硬件防火墙拉黑黑名单。\n2. **[越权探测] 第三方外协特权穿透**:\n   - **请求详情**: 承包商成员 \`external_auditor\` 尝试探针刺探保密项目 \`proj_pilot_credentials\`。\n   - **修复建议**: 建议立即在 DAC 控制台隐藏该专案的 discoverable 标记。\n\n物理拦截判定 100% 成功，数据完全未受污染！报告已抄送首席安全官(CISO)备查。`,
        isExecuting: false,
        completed: true
      } : m));
      setCurrentStep('');
    } else if (type === 'ws_generate_dashboard') {
      // Step 1: Switch View to Workshop
      setCurrentStep('正在切换到【应用构建中心 (Workshop)】控制台...');
      await sleep(1000);
      onViewModeChange('workshop');

      // Step 2: Dispatch Command to WorkshopView
      setCurrentStep('正在利用 AIP 智能低代码引擎解析航班本体 schema...');
      await sleep(1200);
      
      setCurrentStep('正在一键向设计画布注入指标卡、分布图、航班运行数据表及本体属性卡片...');
      window.dispatchEvent(new CustomEvent('aip-workshop-command', { detail: { action: 'ws_generate_dashboard' } }));
      await sleep(1800);
      showToast('success', 'AIP 仪表盘装载成功！指标、表格、图表联动引擎全部初始化就绪。');

      setMessages(prev => prev.map(m => m.id === agentMsgId ? {
        ...m,
        text: `🤖 **AIP 航班运行监控大盘一键生成成功！**\n\n我已在低代码构建中心（Workshop）中为您完成了以下端到端组装：\n1. **多布局挂载**: 在主顶部、中部及底部插槽中自动创建并挂载了 **指标卡 (关注航班数 / 延误数)**、**图表组件 (状态分布条形图)** 与 **数据表格组件**；\n2. **数据集绑定**: 所有展示组件均深度关联到过滤变量 \`v_flights_filtered\` 所指向的民航本体数据集；\n3. **联动效果**: 已自动将右侧的 **航班属性卡片视图**、**状态更改操作按钮** 与选中变量进行了安全挂载。\n\n大盘已自动为您切换为 **预览(Interact)模式**，您可以点击表格中的不同航班，体验即时的底层无代码交互仿真联动！`,
        isExecuting: false,
        completed: true
      } : m));
      setCurrentStep('');

    } else if (type === 'ws_auto_bind') {
      // Step 1: Switch View to Workshop
      setCurrentStep('正在切换到【应用构建中心 (Workshop)】控制台...');
      await sleep(800);
      onViewModeChange('workshop');

      // Step 2: Trigger Event to perform variable binding
      setCurrentStep('正在自动调取页面组件，分析 Table 组件与 Details 视图的输出输入语义...');
      await sleep(1200);

      setCurrentStep('正在智能挂载变量绑定关系，将表格选中行 output 联动至 v_selected_flight 槽...');
      window.dispatchEvent(new CustomEvent('aip-workshop-command', { detail: { action: 'ws_auto_bind' } }));
      await sleep(1500);
      showToast('success', 'AIP 实体联动配置就绪：表格行选中事件已成功绑定变量 v_selected_flight');

      setMessages(prev => prev.map(m => m.id === agentMsgId ? {
        ...m,
        text: `🤖 **AIP 实体交互与变量双向绑定配置成功！**\n\n已经完成了组件属性的高级配置映射：\n1. **选中项输出绑定**: 将「到离港航班清单」Table 组件的 **Selection Output** 参数关联至当前应用变量 **v_selected_flight** (航班本体类型)；\n2. **详情视图目标源绑定**: 将「航班本体卡片视图」Object View 组件的 **Target Object** 输入参数同样重定向关联至 **v_selected_flight**；\n3. **交互成型**: 用户在前端页面点击航班表的任意一行时，详情卡片与关联的业务 Action 按钮将实时随之刷新，呈现精美的端到端联动效应！\n\n系统已在画布左侧的「变量管理器 (Variables)」为您选中并高亮该变量，以便您进行二次微调和逻辑核验。`,
        isExecuting: false,
        completed: true
      } : m));
      setCurrentStep('');

    } else if (type === 'ws_inject_copilot') {
      // Step 1: Switch View to Workshop
      setCurrentStep('正在切换到【应用构建中心 (Workshop)】控制台...');
      await sleep(800);
      onViewModeChange('workshop');

      // Step 2: Trigger event to inject Copilot Widget
      setCurrentStep('正在读取可用 AIP 代理列表，分析 [Aviation Control Copilot] 工具槽绑定规范...');
      await sleep(1200);

      setCurrentStep('正在智能在右侧 aside 布局插槽挂载【AIP 智能操作重排班协同】动作组件...');
      window.dispatchEvent(new CustomEvent('aip-workshop-command', { detail: { action: 'ws_inject_copilot' } }));
      await sleep(1500);
      showToast('success', 'AIP 智能组件挂载成功！低代码运行时已安全融入 AIP Agent 控制面板');

      setMessages(prev => prev.map(m => m.id === agentMsgId ? {
        ...m,
        text: `🤖 **AIP 智能操作组件注入挂载成功！**\n\n我已为您在仪表盘中深度嵌入了 AIP AI 协处理器：\n1. **动作卡片注入**: 在右侧侧边栏中额外增加了一个 **「🚀 AIP 智能自动重排班协同 (AIP Agent)」** 执行按钮；\n2. **代理工具关联**: 此动作直接映射了 Ontology 中 \`update_flight_status\` 操作类型，并在背后链接到 **Aviation Control Copilot** 语言模型引擎；\n3. **零门槛运行**: 业务签派人员现在在预览中选中某一航班后，可以直接点击该 AI 操作卡片，根据智能提示直接执行跨实体的联动重排班与状态变迁！`,
        isExecuting: false,
        completed: true
      } : m));
      setCurrentStep('');

    } else if (type === 'ws_transform_theme') {
      // Step 1: Switch View to Workshop
      setCurrentStep('正在切换到【应用构建中心 (Workshop)】控制台...');
      await sleep(800);
      onViewModeChange('workshop');

      // Step 2: Dispatch command to change theme and branding
      setCurrentStep('正在分析应用品牌指南(VI)，智能调配 Violet 品牌色调与 Dark Mode CSS 标记...');
      await sleep(1200);

      setCurrentStep('正在重构仪表盘 DOM 样式，启用 0.015ms 延迟极速暗黑极客主题编译...');
      window.dispatchEvent(new CustomEvent('aip-workshop-command', { detail: { action: 'ws_transform_theme' } }));
      await sleep(1500);
      showToast('info', '主题转译成功：已全面切换为暗黑科技感品牌「AIP 航空智能联合指挥控制中心」');

      setMessages(prev => prev.map(m => m.id === agentMsgId ? {
        ...m,
        text: `🤖 **AIP 暗黑科技感品牌主题转译应用成功！**\n\n我已按照您的品牌指导方案，通过 AIP 一键对当前的低代码应用进行了视觉重构：\n1. **一键深色化 (Dark Mode)**: 重新应用了全暗黑科技底色，极大降低了 AOC 签派大厅调度人员长时间凝视屏幕的眼部疲劳；\n2. **极客配色调谐**: 将应用主色调更换为 **极客深靛蓝 (Violet/Indigo)**，并自动对图表、状态指示 badge 进行了对比度匹配；\n3. **品牌名称更新**: 应用名称重写更新为「**AIP 航空智能联合指挥控制中心**」，英文品牌标识同步修正为 \`AIP Joint Command Center\`。\n\n大盘左侧面板已为您跳转到「主题与品牌 (Theme)」配置页，您可随时在其中继续进行精细的排版视觉定义。`,
        isExecuting: false,
        completed: true
      } : m));
      setCurrentStep('');
    }
  };

  const handleSendMessage = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!inputText.trim()) return;

    const query = inputText.trim();
    setInputText('');

    // Add User Message
    const userMsgId = `user-${Date.now()}`;
    setMessages(prev => [...prev, {
      id: userMsgId,
      sender: 'user',
      text: query,
      timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
    }]);

    setIsTyping(true);

    // Check if the query matches high-level automated agent scenarios exactly (by checking if it is one of our preset prompts)
    const presetPrompts = [
      '为北京首都机场(PEK)航班构建自动清洗和关联管道，剔除延迟 < 5分钟的冗余行，并合并飞行员姓名数据。',
      '全自动分析清洗后的航班宽表，将其向 Ontology 同步发布为全新实体 [AviationFlight]，设置主键并关联 [AviationPilot] 实体。',
      '针对生产数据集 [ds_flights_clean] 注入 3 组严苛的数据质量健康检查(Data Health Check)。',
      '帮我探查 [AviationFlight] 的端到端血缘依赖地图，看看其下游是否影响了 4 个消费数据集？',
      '为欧盟客运数据构建端到端的 GDPR 零信任隔离体系，自动完成组织、项目ACL、安全标记和分析用途绑定。',
      '为 ds_pilots_biography 飞行员档案数据集配置最高机密行列安全策略，阻断非HR人员查看核心隐私字段。',
      '为客票营收明细数据 ds_ticket_sales 构建一键财务最高机密内审屏障，并演示生命周期到期及动态行列过滤。',
      '请帮我审计过去 1 小时的系统访问日志，找出所有被拦截(DENIED)的越权尝试并生成风险告警分析。',
      '请使用 AIP 智能体在应用构建中心一键组装一个【航班运行综合大盘】，自动生成指标卡、状态分布柱状图、到离港航班清单以及航班本体视图！',
      '帮我将航班明细表格的【选中行输出】与详情卡片的【绑定目标变量】进行关联绑定，将它们同时挂载到 v_selected_flight 变量上，实现无代码联动！',
      '请在页面右侧一键挂载全新的【AIP Copilot 智能辅助面板】组件，实现用户在运行时直接使用自然语言操作航班状态！',
      '帮我将当前应用的主题一键转换为深色模式(Dark Mode)，品牌标题修改为【AIP 航空智能联合指挥控制中心】，主色调设为极客靛蓝(Indigo)！'
    ];

    const isScenarioQuery = query.startsWith('/') || presetPrompts.some(p => query.includes(p.slice(0, 15)));

    if (isScenarioQuery) {
      setIsTyping(false);
      // Map to preconfigured automation scenario
      let detectedType: 'pipeline' | 'ontology' | 'health' | 'lineage' | 'sec_gdpr' | 'sec_finance' | 'sec_row_col' | 'sec_audit' | 'ws_generate_dashboard' | 'ws_auto_bind' | 'ws_inject_copilot' | 'ws_transform_theme' = 'pipeline';
      
      if (viewMode === 'workshop' || query.includes('无代码') || query.includes('大盘') || query.includes('画布') || query.includes('变量') || query.includes('绑定') || query.includes('看板') || query.includes('低代码') || query.includes('组件') || query.includes('挂载')) {
        if (query.includes('大盘') || query.includes('一键组装') || query.includes('监控') || query.includes('生成')) {
          detectedType = 'ws_generate_dashboard';
        } else if (query.includes('变量') || query.includes('绑定') || query.includes('联动')) {
          detectedType = 'ws_auto_bind';
        } else if (query.includes('Copilot') || query.includes('辅助面板') || query.includes('协处理器') || query.includes('操作面板')) {
          detectedType = 'ws_inject_copilot';
        } else {
          detectedType = 'ws_transform_theme';
        }
      } else if (viewMode === 'security' || query.includes('安全') || query.includes('隔离') || query.includes('信任') || query.includes('审计') || query.includes('GDPR') || query.includes('脱敏') || query.includes('掩膜') || query.includes('行列') || query.includes('财务') || query.includes('越权') || query.includes('日志')) {
        if (query.includes('GDPR') || query.includes('零信任') || query.includes('欧盟')) {
          detectedType = 'sec_gdpr';
        } else if (query.includes('脱敏') || query.includes('行列') || query.includes('ssn') || query.includes('过滤') || query.includes('pilots')) {
          detectedType = 'sec_row_col';
        } else if (query.includes('审计') || query.includes('日志') || query.includes('拦截') || query.includes('扫描') || query.includes('越权')) {
          detectedType = 'sec_audit';
        } else {
          detectedType = 'sec_finance';
        }
      } else {
        if (query.includes('本体') || query.includes('实体') || query.includes('映射') || query.includes('AviationFlight') || query.includes('Object')) {
          detectedType = 'ontology';
        } else if (query.includes('健康') || query.includes('监控') || query.includes('质量') || query.includes('规则') || query.includes('check') || query.includes('SLA')) {
          detectedType = 'health';
        } else if (query.includes('血缘') || query.includes('链路') || query.includes('上下游') || query.includes('依赖') || query.includes('lineage')) {
          detectedType = 'lineage';
        } else {
          detectedType = 'pipeline';
        }
      }
      runAgentAutomation(detectedType, query);
    } else {
      // General Q&A calling real backend /api/knowledge/query with current simulator context!
      try {
        // Determine org & project contextual defaults for testing
        let orgId = 'org_aviation_hq';
        if (securitySimUser === 'operator_zhang') orgId = 'org_logistics_p';
        else if (securitySimUser === 'contractor_eng' || securitySimUser === 'external_auditor') orgId = 'org_contractor';
        else if (securitySimUser === 'EU_DPO') orgId = 'Org_EU_Ops';
        else if (securitySimUser === 'auditor_wang') orgId = 'Org_Finance_Dept';

        let projectId = 'proj_aviation_core';
        if (securitySimDataset === 'ds_ticket_sales') {
          projectId = 'proj_flight_analytics';
        } else if (securitySimDataset === 'ds_pilots_biography') {
          projectId = 'proj_pilot_credentials';
        } else if (securitySimDataset === 'ds_passenger_manifest') {
          projectId = 'proj_passenger_eu';
        }

        // Match the same simulator IP mappings
        let clientIp = '10.120.5.23';
        if (securitySimUser === 'operator_zhang') clientIp = '172.16.45.12';
        else if (securitySimUser === 'contractor_eng' || securitySimUser === 'external_auditor') clientIp = '202.96.128.44';
        else if (securitySimUser === 'EU_DPO') clientIp = '10.120.9.15';
        else if (securitySimUser === 'unauthorized_ip_user') clientIp = '198.51.100.45';

        const res = await fetch('/api/knowledge/query', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            query,
            userId: securitySimUser,
            orgId,
            projectId,
            datasetId: securitySimDataset,
            purposeId: securitySimPurpose,
            clientIp
          })
        });

        const data = await res.json();
        
        setIsTyping(false);
        const answerMsgId = `agent-qa-${Date.now()}`;
        setMessages(prev => [...prev, {
          id: answerMsgId,
          sender: 'agent',
          text: data.answer || '未能从 AIP 获得应答。',
          timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        }]);

        // Sync audit logs list on screen if the query was intercepted
        if (data.verdict === 'DENIED') {
          showToast('error', `🛡️ AIP 警报：拦截敏感解密请求并强制零化明文数据流！`);
          const logsRes = await fetch('/api/security/audit-logs');
          if (logsRes.ok) {
            const logs = await logsRes.json();
            setSecurityAuditLogs(logs);
          }
        }
      } catch (err) {
        console.error('Copilot Q&A backend query failed:', err);
        setIsTyping(false);
        setMessages(prev => [...prev, {
          id: `agent-err-${Date.now()}`,
          sender: 'agent',
          text: `❌ **AIP 网关离线异常**\n\n无法连接至后端协处理器推理端点。将自动采取本级零信任最高隔离判定。`,
          timestamp: new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        }]);
      }
    }
  };

  return (
    <div className="fixed inset-y-0 right-0 w-96 bg-white shadow-2xl border-l border-slate-200 flex flex-col z-50 animate-slide-in select-none">
      
      {/* 1. Drawer Header */}
      <div className="h-14 bg-slate-900 text-white flex items-center justify-between px-4 shrink-0">
        <div className="flex items-center gap-2">
          <span className="p-1.5 rounded-lg bg-indigo-600 text-white flex items-center justify-center animate-pulse">
            <LucideIcon name="Bot" size={15} />
          </span>
          <div>
            <h3 className="text-xs font-black tracking-wide font-sans text-white">ECOS AIP Copilot</h3>
            <span className="text-[9px] text-indigo-400 font-mono">Agent-driven Co-Processor Active</span>
          </div>
        </div>
        <button 
          onClick={onClose}
          className="p-1.5 text-slate-400 hover:text-white hover:bg-slate-800 rounded-lg transition-colors cursor-pointer"
        >
          <LucideIcon name="X" size={16} />
        </button>
      </div>

      {/* 2. Live Agent Steps Indicator Banner */}
      {currentStep && (
        <div className="bg-indigo-900/90 text-indigo-100 px-3 py-2 flex items-center gap-2 text-[10px] font-mono shrink-0 border-b border-indigo-950">
          <LucideIcon name="Loader2" size={12} className="animate-spin text-indigo-400" />
          <span className="flex-1 font-bold">{currentStep}</span>
          <span className="bg-indigo-950 px-1.5 py-0.5 rounded text-[8px] text-indigo-400">Agent Action</span>
        </div>
      )}

      {/* 3. Messages Chat History */}
      <div className="flex-1 overflow-y-auto p-4 space-y-4 bg-slate-50">
        {messages.map(msg => {
          const isUser = msg.sender === 'user';
          return (
            <div 
              key={msg.id} 
              className={`flex flex-col ${isUser ? 'items-end' : 'items-start'}`}
            >
              <div className="flex items-center gap-1.5 text-[9px] text-slate-400 mb-1 font-semibold">
                <LucideIcon name={isUser ? 'User' : 'Bot'} size={10} className={isUser ? 'text-slate-500' : 'text-indigo-500'} />
                <span>{isUser ? '开发人员 (You)' : 'AIP 智能体'}</span>
                <span>•</span>
                <span>{msg.timestamp}</span>
              </div>
              
              <div className={`max-w-[85%] rounded-2xl px-3 py-2.5 text-xs leading-relaxed shadow-xs ${
                isUser 
                  ? 'bg-blue-600 text-white rounded-tr-none font-medium' 
                  : 'bg-white text-slate-800 border border-slate-200/80 rounded-tl-none font-normal'
              }`}>
                {/* Process markdown-like double stars and line breaks */}
                <div className="whitespace-pre-line space-y-1">
                  {msg.text.split('\n').map((line, i) => {
                    // Check for lists or key highlights
                    let processedLine = line;
                    const parts = [];
                    let lastIndex = 0;
                    const boldRegex = /\*\*(.*?)\*\*/g;
                    let match;
                    
                    while ((match = boldRegex.exec(line)) !== null) {
                      if (match.index > lastIndex) {
                        parts.push(line.substring(lastIndex, match.index));
                      }
                      parts.push(
                        <strong key={match.index} className={isUser ? 'text-white font-extrabold' : 'text-slate-900 font-extrabold'}>
                          {match[1]}
                        </strong>
                      );
                      lastIndex = boldRegex.lastIndex;
                    }
                    if (lastIndex < line.length) {
                      parts.push(line.substring(lastIndex));
                    }

                    return (
                      <p key={i} className="m-0">
                        {parts.length > 0 ? parts : line}
                      </p>
                    );
                  })}
                </div>

                {/* Show execution banner inside the message bubble if executing */}
                {msg.isExecuting && (
                  <div className="mt-3 pt-2.5 border-t border-slate-100 flex flex-col gap-1 text-[9px] font-mono text-slate-500">
                    <div className="flex items-center gap-1.5 text-indigo-600 font-bold">
                      <span className="w-1.5 h-1.5 rounded-full bg-indigo-600 animate-ping" />
                      <span>正在控制主界面漫游操作...</span>
                    </div>
                  </div>
                )}
              </div>
            </div>
          );
        })}

        {isTyping && (
          <div className="flex flex-col items-start">
            <div className="flex items-center gap-1 text-[9px] text-slate-400 mb-1 font-semibold">
              <LucideIcon name="Bot" size={10} className="text-indigo-500" />
              <span>AIP Copilot is typing...</span>
            </div>
            <div className="bg-white rounded-2xl px-4 py-3 border border-slate-200/80 rounded-tl-none shadow-xs">
              <div className="flex gap-1.5">
                <span className="w-2 h-2 rounded-full bg-slate-300 animate-bounce" style={{ animationDelay: '0ms' }} />
                <span className="w-2 h-2 rounded-full bg-slate-300 animate-bounce" style={{ animationDelay: '150ms' }} />
                <span className="w-2 h-2 rounded-full bg-slate-300 animate-bounce" style={{ animationDelay: '300ms' }} />
              </div>
            </div>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* 4. One-Click Quick Automation Agents (Solves manual friction) */}
      <div className="border-t border-slate-200 p-3 bg-slate-50 shrink-0 space-y-2 select-none">
        <span className="text-[10px] font-black uppercase text-slate-400 block tracking-wider font-mono">
          ⚡ AIP 一键智能代理协同
        </span>
        <div className="grid grid-cols-1 gap-2">
          {agentScenarios.map(scenario => (
            <button
              key={scenario.type}
              onClick={() => {
                if (!currentStep && !isTyping) {
                  runAgentAutomation(scenario.type);
                }
              }}
              disabled={!!currentStep || isTyping}
              className="w-full text-left p-2.5 bg-white border border-slate-200 rounded-xl hover:border-indigo-400 hover:bg-indigo-50/20 active:bg-indigo-50 transition-all text-xs flex flex-col gap-1 cursor-pointer group disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <div className="flex items-center justify-between w-full">
                <span className="font-extrabold text-slate-800 flex items-center gap-1.5">
                  <LucideIcon 
                    name={
                      scenario.type === 'pipeline' ? 'Workflow' :
                      scenario.type === 'ontology' ? 'Boxes' :
                      scenario.type === 'health' ? 'ShieldAlert' :
                      scenario.type === 'lineage' ? 'Share2' :
                      scenario.type === 'sec_gdpr' ? 'ShieldCheck' :
                      scenario.type === 'sec_row_col' ? 'EyeOff' :
                      scenario.type === 'sec_finance' ? 'KeyRound' : 'Activity'
                    } 
                    size={13} 
                    className="text-indigo-500 group-hover:scale-110 transition-transform" 
                  />
                  {scenario.title}
                </span>
                <span className="text-[9px] text-indigo-600 bg-indigo-50 px-1.5 py-0.2 rounded font-mono font-bold opacity-0 group-hover:opacity-100 transition-opacity">
                  RUN AGENT
                </span>
              </div>
              <p className="text-[10px] text-slate-400 font-sans group-hover:text-slate-500">
                {scenario.desc}
              </p>
            </button>
          ))}
        </div>
      </div>

      {/* 5. Natural Language Input Bar */}
      <form 
        onSubmit={handleSendMessage}
        className="border-t border-slate-200 p-3 bg-white shrink-0 flex items-center gap-2"
      >
        <input
          type="text"
          placeholder="给 AIP 协处理器下达指令（如：为航班关联排班...）"
          value={inputText}
          onChange={e => setInputText(e.target.value)}
          disabled={!!currentStep || isTyping}
          className="flex-1 h-9 px-3 bg-slate-50 hover:bg-slate-100/50 focus:bg-white border border-slate-200 rounded-lg text-xs placeholder-slate-400 text-slate-800 focus:outline-hidden focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500/30 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
        />
        <button
          type="submit"
          disabled={!inputText.trim() || !!currentStep || isTyping}
          className="h-9 w-9 bg-indigo-600 text-white rounded-lg flex items-center justify-center hover:bg-indigo-700 active:bg-indigo-800 transition-colors cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
        >
          <LucideIcon name="Send" size={13} />
        </button>
      </form>

    </div>
  );
}
