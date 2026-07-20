/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect } from 'react';
import LucideIcon from '../components/LucideIcon';
import { useLanguage } from '../components/LanguageContext';
import { useTheme } from '../components/ThemeContext';
import { CopilotPanel } from '../components/CopilotPanel';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip as RechartsTooltip,
  ResponsiveContainer,
  LineChart,
  Line,
  AreaChart,
  Area
} from 'recharts';
import type { BusinessScenario, ScenarioManagementViewProps } from './project-workbench/types';
import { calcMetrics, hasBindingChanged } from './project-workbench/helpers';
import { initialScenarios, AVAILABLE_DATASETS, AVAILABLE_OBJECTS, AVAILABLE_KNOWLEDGE, AVAILABLE_AGENTS, AVAILABLE_INTERFACES, AVAILABLE_SECURITY, threatRadarData, efficiencyData } from './project-workbench/data';
import FusionMatrixTab from './project-workbench/tabs/FusionMatrixTab';
import DecisionDeskTab from './project-workbench/tabs/DecisionDeskTab';
import MetricsTab from './project-workbench/tabs/MetricsTab';
import GitVersionTab from './project-workbench/tabs/GitVersionTab';

// Types imported from ./project-workbench/types

// ==========================================
// Default Scenario Templates
// ==========================================
// initialScenarios imported from ./project-workbench/data
// AVAILABLE_DATASETS imported from ./project-workbench/data


// AVAILABLE_OBJECTS imported from ./project-workbench/data


// AVAILABLE_KNOWLEDGE imported from ./project-workbench/data


// AVAILABLE_AGENTS imported from ./project-workbench/data


// AVAILABLE_INTERFACES imported from ./project-workbench/data


// AVAILABLE_SECURITY imported from ./project-workbench/data


export default function ScenarioManagementView({ showToast }: ScenarioManagementViewProps) {
  const toast = showToast || ((type: string, msg: string) => console.log(`[项目工作台] ${type}:`, msg));
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const tl = (zh: string, en: string) => locale === 'zh' ? zh : en;
  const [scenarios, setScenarios] = useState<BusinessScenario[]>(() => {
    try {
      const cached = localStorage.getItem('ecos_cached_scenarios');
      return cached ? JSON.parse(cached) : initialScenarios;
    } catch { return initialScenarios; }
  });
  const [selectedScenarioId, setSelectedScenarioId] = useState<string>('scen_summer_rush');
  const [activeTab, setActiveTab] = useState<'fusion' | 'decision' | 'metrics' | 'git'>('fusion');
  const [showCopilot, setShowCopilot] = useState(false);

  // Git & Configuration Version Control States
  const [gitCommits, setGitCommits] = useState<{[scenarioId: string]: any[]}>(() => {
    const cached = localStorage.getItem('ecos_cached_git_commits');
    if (cached) return JSON.parse(cached);
    return {
      scen_summer_rush: [
        {
          id: 'c1',
          hash: 'b712fa4',
          author: 'AOC_Admin',
          date: '2026-07-06 14:32:10',
          message: 'feat: 初始化 ECOS 2026 暑运配置架构并挂载基础宽表',
          bindings: {
            datasets: ['ds_flight_schedules'],
            objectTypes: ['AviationFlight'],
            knowledgeBases: ['CAAC 121部运行合格审定规则'],
            aiAgents: ['AOC签派大脑智能体 (王凯副本)'],
            securityPolicies: ['proj_aviation_core'],
            interfaces: ['航空运行指挥与航班调度系统']
          }
        },
        {
          id: 'c2',
          hash: 'e42e519',
          author: 'SecOps_Auditor',
          date: '2026-07-07 09:15:43',
          message: 'sec: 级联安全底线重构，绑定 gr-pii 拦截网保护飞行员隐私',
          bindings: {
            datasets: ['ds_flight_schedules', 'ds_fleet_costs'],
            objectTypes: ['AviationFlight', 'AviationPilot'],
            knowledgeBases: ['CAAC 121部运行合格审定规则', 'AOC 雷雨天气签派应急改派规范'],
            aiAgents: ['AOC签派大脑智能体 (王凯副本)', 'PII数据物理遮蔽卫士'],
            securityPolicies: ['purpose_fleet_opt_2026', 'proj_aviation_core', 'gr-pii'],
            interfaces: ['航空运行指挥与航班调度系统']
          }
        }
      ],
      scen_pilot_audit: [
        {
          id: 'p1',
          hash: 'd620ff1',
          author: 'Finance_Lead',
          date: '2026-06-15 11:00:22',
          message: 'feat: 初始化飞行员薪酬与执勤对账要素框架',
          bindings: {
            datasets: ['ds_pilots_biography'],
            objectTypes: ['AviationPilot'],
            knowledgeBases: ['民航飞行员薪资管理条例'],
            aiAgents: ['财务对账合规审计助理'],
            securityPolicies: ['purpose_pilot_health_audit'],
            interfaces: ['飞行员资质与适航合规分析大盘']
          }
        },
        {
          id: 'p2',
          hash: 'f93c12e',
          author: 'DPO_Officer',
          date: '2026-06-28 17:40:55',
          message: 'compliance: 挂接 GDPR 欧盟隐私及总监放行机制，提升实际运行安全性',
          bindings: {
            datasets: ['ds_pilots_biography', 'ds_ticket_sales'],
            objectTypes: ['AviationPilot'],
            knowledgeBases: ['民航飞行员薪资管理条例', 'GDPR 数据安全保护白皮书'],
            aiAgents: ['财务对账合规审计助理', 'DPO安全审查智能体'],
            securityPolicies: ['purpose_pilot_health_audit', 'proj_pilot_credentials', 'gr-approval'],
            interfaces: ['飞行员资质与适航合规分析大盘']
          }
        }
      ],
      scen_evtol_sandbox: [
        {
          id: 'e1',
          hash: 'a12bc4f',
          author: 'Strategy_Planner',
          date: '2026-07-02 10:20:00',
          message: 'feat: 搭建低空客运 eVTOL 测试沙盒与 121部试运行草案',
          bindings: {
            datasets: ['ds_flight_schedules'],
            objectTypes: ['AviationFlight'],
            knowledgeBases: ['eVTOL城市空中交通试行条例'],
            aiAgents: ['低空本体演进规划助手'],
            securityPolicies: ['proj_aviation_finance'],
            interfaces: ['低代码决策可视化系统']
          }
        }
      ]
    };
  });

  const [gitBranches, setGitBranches] = useState<{[scenarioId: string]: string}>(() => {
    const cached = localStorage.getItem('ecos_cached_git_branches');
    if (cached) return JSON.parse(cached);
    return {
      scen_summer_rush: 'main',
      scen_pilot_audit: 'main',
      scen_evtol_sandbox: 'main'
    };
  });

  const [selectedCommitId, setSelectedCommitId] = useState<string | null>(null);
  const [gitCommitMsg, setGitCommitMsg] = useState<string>('');
  const [gitTerminalLogs, setGitTerminalLogs] = useState<string[]>([]);
  const [isGitPushing, setIsGitPushing] = useState<boolean>(false);
  const [gitViewMode, setGitViewMode] = useState<'visual' | 'json'>('visual');

  useEffect(() => {
    localStorage.setItem('ecos_cached_git_commits', JSON.stringify(gitCommits));
  }, [gitCommits]);

  useEffect(() => {
    localStorage.setItem('ecos_cached_git_branches', JSON.stringify(gitBranches));
  }, [gitBranches]);
  
  // Standardized Wizard States (Replacing simple modal states)
  const [showWizardModal, setShowWizardModal] = useState(false);
  const [wizardScenarioId, setWizardScenarioId] = useState<string | null>(null);
  const [wizardStep, setWizardStep] = useState<number>(1);

  // Wizard fields (Basic info & goals)
  const [wName, setWName] = useState('');
  const [wGoal, setWGoal] = useState('');
  const [wDesc, setWDesc] = useState('');
  const [wDept, setWDept] = useState('民航 AOC 运行指挥部');
  const [wPriority, setWPriority] = useState<'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'>('HIGH');
  const [wBudget, setWBudget] = useState('¥800,000');
  const [wStatus, setWStatus] = useState<'ACTIVE' | 'DRAFT' | 'COMPLETED' | 'SUSPENDED'>('DRAFT');
  const [wSafetyIndex, setWSafetyIndex] = useState('99.90%');

  // Wizard bindings
  const [wDatasets, setWDatasets] = useState<string[]>([]);
  const [wObjectTypes, setWObjectTypes] = useState<string[]>([]);
  const [wKnowledgeBases, setWKnowledgeBases] = useState<string[]>([]);
  const [wAiAgents, setWAiAgents] = useState<string[]>([]);
  const [wInterfaces, setWInterfaces] = useState<string[]>([]);
  const [wSecurityPolicies, setWSecurityPolicies] = useState<string[]>([]);

  // Proposal State
  const [proposals, setProposals] = useState<any[]>([]);
  const [isLoadingProposals, setIsLoadingProposals] = useState(false);
  const [resolvingProposalId, setResolvingProposalId] = useState<string | null>(null);

  // Simulation Sandbox Sandbox
  const [simQuery, setSimQuery] = useState('查询 UA102 机长张建国资质与保底工资');
  const [simRole, setSimRole] = useState<'AOC_DIRECTOR' | 'EXTERNAL_CONTRACTOR'>('AOC_DIRECTOR');
  const [simResult, setSimResult] = useState<any | null>(null);
  const [isSimulating, setIsSimulating] = useState(false);

  const activeScenario = scenarios.find(s => s.id === selectedScenarioId) || scenarios[0];

  useEffect(() => {
    localStorage.setItem('ecos_cached_scenarios', JSON.stringify(scenarios));
  }, [scenarios]);

  // Fetch Proposals
  const fetchProposalsList = async () => {
    setIsLoadingProposals(true);
    try {
      const res = await fetch('/api/v1/ontology/proposals');
      if (res.ok) {
        const data = await res.json();
        // API returns ApiResponse<T> wrapper — unwrap the data field
        const list = Array.isArray(data) ? data : (data?.data || []);
        setProposals(list);
      }
    } catch (err) {
      console.error('Failed to fetch proposals', err);
    } finally {
      setIsLoadingProposals(false);
    }
  };

  useEffect(() => {
    fetchProposalsList();
    // Poll every 10 seconds for real-time collaboration updates
    const timer = setInterval(fetchProposalsList, 10000);
    return () => clearInterval(timer);
  }, []);

  // Approve proposal (Execute)
  const handleApproveProposal = async (id: string, actionId: string) => {
    setResolvingProposalId(id);
    try {
      const res = await fetch(`/api/v1/ontology/proposals/${id}/approve`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          userRole: '签派总监',
          userName: '王凯'
        })
      });
      const data = await res.json();
      if (res.ok && data.success) {
        toast('success', `✅ [AOC 总监授权成功] 已完成 ${actionId} 物理落库对账写入！`);
        fetchProposalsList();
        // Update safety index slightly to simulate dynamic scenario improvements
        setScenarios(prev => prev.map(s => {
          if (s.id === selectedScenarioId) {
            return {
              ...s,
              actualSafetyIndex: '100.00%',
              metrics: {
                ...s.metrics,
                integrityScore: Math.min(100, s.metrics.integrityScore + 1)
              }
            };
          }
          return s;
        }));
      } else {
        toast('error', data.message || data.error || '审批写入失败！');
      }
    } catch (err: any) {
      toast('error', `网络请求失败: ${err.message}`);
    } finally {
      setResolvingProposalId(null);
    }
  };

  // Reject proposal
  const handleRejectProposal = async (id: string, actionId: string) => {
    setResolvingProposalId(id);
    const reason = prompt('请输入拒绝该项写回提案的安全合规理由 (Reject Reason):', '物理主键冲突或非工作时段越权写回');
    if (reason === null) {
      setResolvingProposalId(null);
      return; // cancelled
    }
    try {
      const res = await fetch(`/api/v1/ontology/proposals/${id}/reject`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          userName: '王凯',
          reason
        })
      });
      if (res.ok) {
        toast('info', `❌ 已安全拒绝对账事务 ${actionId} 的物理写回，日志已被拦截归档。`);
        fetchProposalsList();
      } else {
        toast('error', '拒绝失败！');
      }
    } catch (err: any) {
      toast('error', `网络请求失败: ${err.message}`);
    } finally {
      setResolvingProposalId(null);
    }
  };

  // Run decision sandbox
  const handleRunSandbox = async () => {
    setIsSimulating(true);
    try {
      // Formulate request params depending on active scenario bindings
      let params = {
        query: simQuery,
        userId: simRole === 'AOC_DIRECTOR' ? 'analyst_li' : 'contractor_xiao',
        orgId: simRole === 'AOC_DIRECTOR' ? 'org_aviation_hq' : 'org_contractor',
        clientIp: simRole === 'AOC_DIRECTOR' ? '10.120.5.23' : '222.22.22.22', // triggers IP block
        projectId: activeScenario.bindings.securityPolicies[1] || 'proj_aviation_core',
        datasetId: activeScenario.bindings.datasets[0] || 'ds_flight_schedules',
        purposeId: activeScenario.bindings.securityPolicies[0] || 'purpose_fleet_opt_2026',
        distanceMetric: 'cosine'
      };

      const res = await fetch('/api/v1/knowledge/query', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(params)
      });
      
      if (res.ok) {
        const data = await res.json();
        setSimResult(data);
        if (data.verdict === 'DENIED') {
          toast('error', '🛡️ 沙箱安全拦截！零信任校验拦截阻断访问！');
        } else {
          toast('success', '🧬 沙箱校验通过！数据已自动脱敏并完成 RAG 推理。');
        }
      } else {
        // Fallback simulated logic for demo consistency if API is offline
        const simulatedAnswer = simRole === 'AOC_DIRECTOR' 
          ? `[Grounded Chunk Match 100%] 已获取机长张建国适航状态极佳。资质符合CAAC 121部规章。其保底薪资与SSN已通过 PII 合规遮蔽策略处理：\n- SSN: [REDACTED_BY_PII_GUARDRAIL]\n- 薪资: [REDACTED_BY_PII_GUARDRAIL]`
          : `⚠️ [AIP Zero-Trust block] IP 地址越权或角色隔离阻断。外部承包商无权读取此人资物理表。`;
        
        setSimResult({
          verdict: simRole === 'AOC_DIRECTOR' ? 'GRANTED' : 'DENIED',
          answer: simulatedAnswer,
          groundedDocs: [
            { title: 'AOC民航适航与薪资规范', score: 0.94 }
          ]
        });
      }
    } catch (e: any) {
      console.warn('Sandbox call failed, generating simulated results:', e);
      setSimResult({
        verdict: simRole === 'AOC_DIRECTOR' ? 'GRANTED' : 'DENIED',
        answer: simRole === 'AOC_DIRECTOR' 
          ? `[本地沙箱模拟] 已在「${activeScenario.name}」场景下执行。获取张建国(Doris ID: PL-028)信息。已执行安全遮蔽：\n- SSN: [REDACTED_SSN_MASK]\n- 薪资: [REDACTED_SALARY_MASK]`
          : `⚠️ [AIP 拦截] 外部承包商(EXTERNAL_CONTRACTOR)企图访问限制级 PII 物理数据库。系统已实时进行 IP 与角色隔离阻断。`,
        groundedDocs: [
          { title: 'CAAC 121部运行合格审定规则', score: 0.92 }
        ]
      });
    } finally {
      setIsSimulating(false);
    }
  };

  // ==========================================
  // Guided Wizard Functions
  // ==========================================
  const openCreateWizard = () => {
    setWizardScenarioId(null);
    setWizardStep(1);
    setWName('');
    setWGoal('');
    setWDesc('');
    setWDept('民航 AOC 运行指挥部');
    setWPriority('HIGH');
    setWBudget('¥800,000');
    setWStatus('DRAFT');
    setWSafetyIndex('99.90%');
    setWDatasets(['ds_flight_schedules']);
    setWObjectTypes(['AviationFlight']);
    setWKnowledgeBases(['CAAC 121部运行合格审定规则']);
    setWAiAgents(['默认决策智能体助理']);
    setWInterfaces(['低代码决策可视化系统']);
    setWSecurityPolicies(['proj_aviation_core']);
    setShowWizardModal(true);
  };

  const openEditWizard = (scen: BusinessScenario) => {
    setWizardScenarioId(scen.id);
    setWizardStep(1);
    setWName(scen.name);
    setWGoal(scen.businessGoal);
    setWDesc(scen.description);
    setWDept(scen.department);
    setWPriority(scen.priority);
    setWBudget(scen.budget);
    setWStatus(scen.status);
    setWSafetyIndex(scen.safetyIndexTarget);
    setWDatasets(scen.bindings.datasets || []);
    setWObjectTypes(scen.bindings.objectTypes || []);
    setWKnowledgeBases(scen.bindings.knowledgeBases || []);
    setWAiAgents(scen.bindings.aiAgents || []);
    setWInterfaces(scen.bindings.interfaces || []);
    setWSecurityPolicies(scen.bindings.securityPolicies || []);
    setShowWizardModal(true);
  };

  const handleSaveWizard = () => {
    if (!wName.trim()) {
      toast('error', '请填写场景名称！');
      setWizardStep(1);
      return;
    }
    if (!wGoal.trim()) {
      toast('error', '请填写业务目标！');
      setWizardStep(1);
      return;
    }

    const { integrityScore, mappingCompleteness, threatBlockRate, slaScore } = calcMetrics(
      wDatasets, wObjectTypes, wKnowledgeBases, wAiAgents, wInterfaces, wSecurityPolicies
    );

    if (wizardScenarioId) {
      // Edit Mode
      const updatedScenarios = scenarios.map(s => {
        if (s.id === wizardScenarioId) {
          return {
            ...s,
            name: wName,
            description: wDesc || '暂无描述。',
            businessGoal: wGoal,
            department: wDept,
            priority: wPriority,
            status: wStatus,
            budget: wBudget,
            safetyIndexTarget: wSafetyIndex,
            actualSafetyIndex: wStatus === 'ACTIVE' ? '99.50%' : '0.00%',
            bindings: {
              datasets: wDatasets,
              objectTypes: wObjectTypes,
              knowledgeBases: wKnowledgeBases,
              aiAgents: wAiAgents,
              interfaces: wInterfaces,
              securityPolicies: wSecurityPolicies
            },
            metrics: {
              integrityScore,
              mappingCompleteness,
              threatBlockRate,
              slaScore
            }
          };
        }
        return s;
      });
      setScenarios(updatedScenarios);
      localStorage.setItem('ecos_cached_scenarios', JSON.stringify(updatedScenarios));

      // Auto-commit to Git on wizard update if there are any changes
      const currentCommits = gitCommits[wizardScenarioId] || [];
      const lastCommit = currentCommits[currentCommits.length - 1];
      const wBindings = { datasets: wDatasets, objectTypes: wObjectTypes, knowledgeBases: wKnowledgeBases, aiAgents: wAiAgents, interfaces: wInterfaces, securityPolicies: wSecurityPolicies };
      const hasChanges = hasBindingChanged(lastCommit, wBindings);

      if (hasChanges) {
        const newCommit = {
          id: `c_${Date.now()}`,
          hash: Math.random().toString(16).substring(2, 9),
          author: 'Wizard_Auto',
          date: new Date().toISOString().replace('T', ' ').substring(0, 19),
          message: `refactor(config): 依托 7步要素配置向导更新「${wName}」依赖模型`,
          bindings: {
            datasets: wDatasets,
            objectTypes: wObjectTypes,
            knowledgeBases: wKnowledgeBases,
            aiAgents: wAiAgents,
            interfaces: wInterfaces,
            securityPolicies: wSecurityPolicies
          }
        };
        setGitCommits(prev => ({
          ...prev,
          [wizardScenarioId]: [...currentCommits, newCommit]
        }));
      }

      toast('success', `✅ 已成功通过标准化配置向导更新场景「${wName}」及其全部依赖要素！(Git 版本已自动同步)`);
    } else {
      // Create Mode
      const newId = `scen_${Date.now()}`;
      const newScen: BusinessScenario = {
        id: newId,
        name: wName,
        description: wDesc || '暂无描述。',
        businessGoal: wGoal,
        department: wDept,
        priority: wPriority,
        status: wStatus,
        budget: wBudget,
        safetyIndexTarget: wSafetyIndex,
        actualSafetyIndex: wStatus === 'ACTIVE' ? '99.50%' : '0.00%',
        createdAt: new Date().toISOString().split('T')[0],
        bindings: {
          datasets: wDatasets,
          objectTypes: wObjectTypes,
          knowledgeBases: wKnowledgeBases,
          aiAgents: wAiAgents,
          interfaces: wInterfaces,
          securityPolicies: wSecurityPolicies
        },
        metrics: {
          integrityScore,
          mappingCompleteness,
          threatBlockRate,
          slaScore
        }
      };
      
      // Initialize Git history for the new scenario
      const initialCommit = {
        id: `c_${Date.now()}`,
        hash: Math.random().toString(16).substring(2, 9),
        author: 'Wizard_Init',
        date: new Date().toISOString().replace('T', ' ').substring(0, 19),
        message: `feat: 初始化 ECOS 全要素场景「${wName}」并首次冻结基线`,
        bindings: { ...newScen.bindings }
      };

      setGitCommits(prev => ({
        ...prev,
        [newId]: [initialCommit]
      }));

      // Initialize default main branch for this scenario
      setGitBranches(prev => ({
        ...prev,
        [newId]: 'main'
      }));

      const updatedScenarios = [...scenarios, newScen];
      setScenarios(updatedScenarios);
      setSelectedScenarioId(newScen.id);
      localStorage.setItem('ecos_cached_scenarios', JSON.stringify(updatedScenarios));
      toast('success', `🎉 已成功通过标准化配置向导创建新场景「${wName}」，并初始化其 Git 配置版本仓库！`);
    }

    setShowWizardModal(false);
  };

  // Manual Commit Handler from Git status panel
  const handleGitCommitManual = (message: string) => {
    if (!message.trim()) {
      toast('error', '请输入提交说明 (Commit Message)！');
      return;
    }
    
    const currentCommits = gitCommits[selectedScenarioId] || [];
    const newCommit = {
      id: `c_${Date.now()}`,
      hash: Math.random().toString(16).substring(2, 9),
      author: 'AOC_Admin',
      date: new Date().toISOString().replace('T', ' ').substring(0, 19),
      message: message.trim(),
      bindings: { ...activeScenario.bindings }
    };

    setGitCommits(prev => ({
      ...prev,
      [selectedScenarioId]: [...currentCommits, newCommit]
    }));
    setGitCommitMsg('');
    toast('success', `📦 Git 提交成功！已在 [${gitBranches[selectedScenarioId] || 'main'}] 分支冻结配置版本：${newCommit.hash}`);
  };

  // Rollback / checkout handler
  const handleGitCheckoutCommit = (commit: any) => {
    if (!commit) return;
    
    // Update active scenario bindings to match this commit snapshot
    const updatedScenarios = scenarios.map(s => {
      if (s.id === selectedScenarioId) {
        // Re-calculate mock metrics reflecting the rollback bindings
        const wB = commit.bindings;
        const { integrityScore, mappingCompleteness, threatBlockRate, slaScore } = calcMetrics(
          wB.datasets || [], wB.objectTypes || [], wB.knowledgeBases || [], wB.aiAgents || [], wB.interfaces || [], wB.securityPolicies || []
        );

        return {
          ...s,
          bindings: {
            datasets: wB.datasets || [],
            objectTypes: wB.objectTypes || [],
            knowledgeBases: wB.knowledgeBases || [],
            aiAgents: wB.aiAgents || [],
            interfaces: wB.interfaces || [],
            securityPolicies: wB.securityPolicies || []
          },
          metrics: {
            integrityScore,
            mappingCompleteness,
            threatBlockRate,
            slaScore
          }
        };
      }
      return s;
    });

    setScenarios(updatedScenarios);
    localStorage.setItem('ecos_cached_scenarios', JSON.stringify(updatedScenarios));
    toast('success', `🔮 检出成功！已回滚 ECOS 场景配置至历史版本 ${commit.hash} - [${commit.message}]`);
  };

  // Branch Switch Handler
  const handleSwitchGitBranch = (branchName: string) => {
    setGitBranches(prev => ({
      ...prev,
      [selectedScenarioId]: branchName
    }));
    toast('info', `🔀 已成功切换至本地分支 [${branchName}]`);
  };

  // Remote Push Handler (Simulates terminal command trace)
  const handleGitPushRemote = () => {
    if (isGitPushing) return;
    setIsGitPushing(true);
    setGitTerminalLogs([
      `$ git remote -v`,
      `origin  gitlab.ecos.internal:aviation-dispatch/scenarios.git (fetch)`,
      `origin  gitlab.ecos.internal:aviation-dispatch/scenarios.git (push)`,
      `$ git status`,
      `On branch ${gitBranches[selectedScenarioId] || 'main'}`,
      `Your branch is ahead of 'origin/${gitBranches[selectedScenarioId] || 'main'}' by ${gitCommits[selectedScenarioId]?.length || 1} commits.`,
      `  (use "git push" to publish your local commits)`,
      `$ git push origin ${gitBranches[selectedScenarioId] || 'main'}`,
      `[ECOS Zero-Trust Gateway: Authenticating user guorongxiao@gmail.com...]`,
      `🔐 [MFA Signature Verified] AOC_DIRECTOR certificate approved.`,
    ]);

    setTimeout(() => {
      setGitTerminalLogs(prev => [
        ...prev,
        `Enumerating objects: 7, done.`,
        `Counting objects: 100% (7/7), done.`,
        `Delta compression using up to 16 threads`,
        `Compressing objects: 100% (4/4), done.`,
      ]);
    }, 1000);

    setTimeout(() => {
      setGitTerminalLogs(prev => [
        ...prev,
        `Writing objects: 100% (4/4), 1.24 KiB | 1.24 MiB/s, done.`,
        `Total 4 (delta 2), reused 0 (delta 0), pack-reused 0`,
        `To gitlab.ecos.internal:aviation-dispatch/scenarios.git`,
        `   2f10b5a..${gitCommits[selectedScenarioId]?.[gitCommits[selectedScenarioId].length - 1]?.hash || 'HEAD'}  ${gitBranches[selectedScenarioId] || 'main'} -> ${gitBranches[selectedScenarioId] || 'main'}`,
        `🟢 ECOS 云底座要素库配置冻结成功！安全合规策略已级联更新至所有边缘计算集群。`
      ]);
      setIsGitPushing(false);
      toast('success', '🚀 [ECOS 云端同步完成] 场景配置文件已成功冻结至远程企业仓库并应用！');
    }, 2500);
  };

  // Chart data imported from ./project-workbench/data




  return (
    <div className={`flex-1 flex flex-col ${styles.appBg} ${styles.appText} overflow-hidden font-sans relative`}>
      
      {/* 1. Header / KPI Dashboard */}
      <div className={`p-4 ${styles.cardBg} border-b ${styles.cardBorder} shrink-0 flex flex-col md:flex-row md:items-center justify-between gap-4`}>
        <div>
          <div className="flex items-center gap-2">
            <span className="p-1.5 rounded-md bg-indigo-600 text-white flex items-center justify-center">
              <LucideIcon name="Briefcase" size={16} />
            </span>
            <h1 className="text-lg font-bold tracking-tight text-white flex items-center gap-2">
              场景与项目综合调度中心
              <span className="text-[10px] bg-indigo-900/60 border border-indigo-700/50 px-2 py-0.5 rounded text-indigo-300 font-bold tracking-widest uppercase">Executive Cockpit</span>
            </h1>
          </div>
          <p className={`text-xs ${styles.cardTextMuted} mt-1`}>从企业高层管理者的视角，将孤立的「物理数据、语义本体、知识 RAG、AI 智能体及安全围栏」融合成具体的高价值业务场景。</p>
        </div>

        {/* Global scenario statistics */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-center max-w-2xl w-full md:w-auto">
          <div className={`${styles.cardBg} border ${styles.cardBorder} p-2 rounded-lg`}>
            <span className={`block text-[10px] ${styles.cardTextMuted} font-bold uppercase`}>运行场景总数</span>
            <span className="text-lg font-extrabold text-blue-400">{scenarios.length} <span className={`text-xs font-normal ${styles.cardTextMuted}`}>个</span></span>
          </div>
          <div className={`${styles.cardBg} border ${styles.cardBorder} p-2 rounded-lg`}>
            <span className={`block text-[10px] ${styles.cardTextMuted} font-bold uppercase`}>融合预算估算</span>
            <span className="text-lg font-extrabold text-indigo-400">¥2.85M</span>
          </div>
          <div className={`${styles.cardBg} border ${styles.cardBorder} p-2 rounded-lg`}>
            <span className={`block text-[10px] ${styles.cardTextMuted} font-bold uppercase`}>威胁防御阻断率</span>
            <span className="text-lg font-extrabold text-emerald-400">100%</span>
          </div>
          <div className={`${styles.cardBg} border ${styles.cardBorder} p-2 rounded-lg`}>
            <span className={`block text-[10px] ${styles.cardTextMuted} font-bold uppercase`}>平均对账时效</span>
            <span className="text-lg font-extrabold text-amber-400">2.8s <span className="text-xs font-normal text-emerald-400">(-93.7%)</span></span>
          </div>
          <div className="col-span-2 sm:col-span-4 flex justify-end mt-1">
            <button
              onClick={() => setShowCopilot(!showCopilot)}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded border transition-colors cursor-pointer text-xs font-bold ${
                showCopilot
                  ? 'bg-blue-600 text-white border-blue-500'
                  : `${styles.cardBg} ${styles.cardTextMuted} border-[var(--border)] hover:bg-slate-800/50`
              }`}
            >
              <LucideIcon name="MessageSquare" size={12} />
              {showCopilot ? (locale === 'zh' ? '关闭助手' : 'Close Copilot') : (locale === 'zh' ? '智能助手' : 'Copilot')}
            </button>
          </div>
        </div>
      </div>

      <div className="flex-1 flex overflow-hidden">
        
        {/* Left Side: Scenario list & Add action */}
        <div className={`w-80 border-r ${styles.cardBorder} flex flex-col ${styles.cardBg} select-none shrink-0`}>
          <div className={`p-3 border-b ${styles.cardBorder} flex items-center justify-between shrink-0 ${styles.cardBg}`}>
            <span className={`text-xs font-bold ${styles.cardTextMuted} flex items-center gap-1.5`}>
              <LucideIcon name="ListCollapse" size={12} className="text-indigo-500" />
              业务场景库 (Scenarios list)
            </span>
            <button
              onClick={openCreateWizard}
              className="text-[10px] bg-indigo-600 hover:bg-indigo-700 text-white font-bold px-2 py-1 rounded flex items-center gap-1 transition-all cursor-pointer"
            >
              <LucideIcon name="Plus" size={10} />
              创建新场景
            </button>
          </div>

          <div className="flex-1 overflow-y-auto p-3 space-y-3">
            {scenarios.map(scen => {
              const isSelected = scen.id === selectedScenarioId;
              const isCritical = scen.priority === 'CRITICAL';
              const isHigh = scen.priority === 'HIGH';
              const isDraft = scen.status === 'DRAFT';

              return (
                <div
                  key={scen.id}
                  onClick={() => setSelectedScenarioId(scen.id)}
                  className={`p-3 rounded-lg border transition-all cursor-pointer ${
                    isSelected
                      ? 'bg-slate-800 border-indigo-500 shadow-md shadow-indigo-950/30'
                      : `${styles.cardBg} ${styles.cardBorder} hover:border-slate-700 hover:bg-slate-800/50`
                  }`}
                >
                  <div className="flex items-start justify-between gap-1.5">
                    <span className="text-xs font-extrabold tracking-tight text-white line-clamp-1 flex-1">{scen.name}</span>
                    <span className={`text-[8px] font-bold px-1.5 py-0.5 rounded uppercase shrink-0 ${
                      isCritical ? 'bg-red-950 text-red-400 border border-red-900/50' :
                      isHigh ? 'bg-amber-950 text-amber-400 border border-amber-900/50' : `bg-slate-800 ${styles.cardTextMuted} border border-slate-700`
                    }`}>
                      {scen.priority}
                    </span>
                  </div>

                  <p className={`text-[11px] ${styles.cardTextMuted} line-clamp-2 mt-1.5 leading-relaxed`}>{scen.description}</p>

                  <div className={`flex items-center justify-between mt-3 pt-2.5 border-t ${styles.cardBorder} text-[10px] ${styles.cardTextMuted} font-mono`}>
                    <span className="flex items-center gap-1">
                      <LucideIcon name="Users" size={10} className={`${styles.cardTextMuted}`} />
                      {scen.department.slice(0, 8)}
                    </span>
                    <span className={`flex items-center gap-1 font-bold ${isDraft ? `${styles.cardTextMuted}` : 'text-emerald-400'}`}>
                      <span className={`w-1.5 h-1.5 rounded-full ${isDraft ? 'bg-slate-600' : 'bg-emerald-500 animate-pulse'}`} />
                      {scen.status}
                    </span>
                  </div>
                </div>
              );
            })}
          </div>

          <div className={`p-3 ${styles.cardBg} border-t ${styles.cardBorder} text-[10px] ${styles.cardTextMuted} space-y-1 font-sans`}>
            <p className={`flex items-center gap-1 font-semibold ${styles.cardTextMuted}`}>
              <LucideIcon name="Info" size={11} className="text-indigo-400" />
              如何起作用？
            </p>
            <p className="leading-relaxed">管理者在此定义高维业务场景，绑定不同层级的系统底座（数据、实体、知识与安全规则），最终形成高合规的企业级智能流闭环。</p>
          </div>
        </div>

        {/* Right Side: Tab switcher & Scenario view */}
        <div className={`flex-1 flex flex-col overflow-hidden ${styles.appBg}`}>
          
          {/* Active Scenario Overview Card */}
          <div className={`p-4 ${styles.cardBg} border-b ${styles.cardBorder} shrink-0`}>
            <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-3">
              <div>
                <div className="flex items-center gap-2">
                  <span className="text-[10px] bg-emerald-950 border border-emerald-900 px-2 py-0.5 rounded text-emerald-400 font-mono font-bold">
                    ACTIVE PROJECT
                  </span>
                  <span className={`text-xs font-semibold ${styles.cardTextMuted} font-mono`}>ID: {activeScenario.id}</span>
                </div>
                <h2 className="text-base font-extrabold text-white mt-1">{activeScenario.name}</h2>
                <p className={`text-xs ${styles.cardTextMuted} mt-1 max-w-4xl leading-relaxed`}>{activeScenario.description}</p>
              </div>

              <div className="flex flex-col gap-2 items-end self-end sm:self-start shrink-0">
                <div className="flex items-center gap-1.5">
                  <span className={`text-xs ${styles.cardTextMuted}`}>部门责任人：</span>
                  <span className="text-xs bg-slate-800 border border-slate-700 px-2.5 py-1 rounded font-bold text-slate-200">
                    {activeScenario.department}
                  </span>
                </div>
                <button
                  onClick={() => openEditWizard(activeScenario)}
                  className="px-3 py-1 bg-indigo-600/20 hover:bg-indigo-600 border border-indigo-500/30 text-indigo-300 hover:text-white text-[11px] font-bold rounded flex items-center gap-1 transition-all cursor-pointer shadow-xs"
                >
                  <LucideIcon name="Settings" size={11} className="text-indigo-400" />
                  修改场景要素对接 (向导)
                </button>
              </div>
            </div>

            {/* Target & Budget Row */}
            <div className={`grid grid-cols-2 sm:grid-cols-4 gap-4 mt-4 pt-3 border-t ${styles.cardBorder} text-xs font-mono`}>
              <div>
                <span className={`${styles.cardTextMuted} text-[10px] block`}>项目预设总预算 (Scenario Budget)</span>
                <span className="text-sm font-bold text-indigo-400">{activeScenario.budget}</span>
              </div>
              <div>
                <span className={`${styles.cardTextMuted} text-[10px] block`}>安全适航合规率 (Safety SLA Target)</span>
                <span className="text-sm font-bold text-teal-400">{activeScenario.safetyIndexTarget}</span>
              </div>
              <div>
                <span className={`${styles.cardTextMuted} text-[10px] block`}>当前实际运行安全率 (Actual SLA)</span>
                <span className="text-sm font-bold text-emerald-400">{activeScenario.actualSafetyIndex}</span>
              </div>
              <div>
                <span className={`${styles.cardTextMuted} text-[10px] block`}>项目创建日期 (Created At)</span>
                <span className={`text-sm font-bold ${styles.cardText}`}>{activeScenario.createdAt}</span>
              </div>
            </div>
          </div>

          {/* Sub Tab Switcher */}
          <div className={`h-10 ${styles.cardBg} px-4 border-b ${styles.cardBorder} shrink-0 flex items-center justify-between`}>
            <div className="flex gap-2">
              <button
                onClick={() => setActiveTab('fusion')}
                className={`px-4 h-10 border-b-2 text-xs font-bold transition-all flex items-center gap-1.5 cursor-pointer ${
                  activeTab === 'fusion'
                    ? 'border-indigo-500 text-indigo-400'
                    : `border-transparent ${styles.cardTextMuted} hover:text-slate-200`
                }`}
              >
                <LucideIcon name="Network" size={13} />
                <span>{tl('认知能力融合矩阵', 'Fusion Matrix')}</span>
              </button>
              <button
                onClick={() => setActiveTab('decision')}
                className={`px-4 h-10 border-b-2 text-xs font-bold transition-all flex items-center gap-1.5 cursor-pointer ${
                  activeTab === 'decision'
                    ? 'border-indigo-500 text-indigo-400'
                    : `border-transparent ${styles.cardTextMuted} hover:text-slate-200`
                }`}
              >
                <LucideIcon name="ShieldCheck" size={13} />
                <span>{tl('决策与对账沙箱', 'Decision & Sandbox')}</span>
                {proposals.filter(p => p.status === 'pending').length > 0 && (
                  <span className="bg-rose-500 text-white text-[9px] px-1.5 py-0.2 rounded-full animate-bounce">
                    {proposals.filter(p => p.status === 'pending').length} {tl('待对账', 'pending')}
                  </span>
                )}
              </button>
              <button
                onClick={() => setActiveTab('metrics')}
                className={`px-4 h-10 border-b-2 text-xs font-bold transition-all flex items-center gap-1.5 cursor-pointer ${
                  activeTab === 'metrics'
                    ? 'border-indigo-500 text-indigo-400'
                    : `border-transparent ${styles.cardTextMuted} hover:text-slate-200`
                }`}
              >
                <LucideIcon name="TrendingUp" size={13} />
                <span>{tl('业务指标分析', 'Metrics & Analytics')}</span>
              </button>
              <button
                onClick={() => setActiveTab('git')}
                className={`px-4 h-10 border-b-2 text-xs font-bold transition-all flex items-center gap-1.5 cursor-pointer ${
                  activeTab === 'git'
                    ? 'border-indigo-500 text-indigo-400'
                    : `border-transparent ${styles.cardTextMuted} hover:text-slate-200`
                }`}
              >
                <LucideIcon name="GitBranch" size={13} />
                <span>{tl('Git 版本控制', 'Git Version Control')}</span>
              </button>
            </div>

            <div className="text-[10px] text-indigo-400 font-mono font-bold bg-indigo-950/60 border border-indigo-900/60 px-2 py-0.5 rounded">
              {activeTab === 'fusion' ? tl('资源绑定与集成', 'Bindings & Integrations') : activeTab === 'decision' ? tl('审批与威胁日志', 'Approval & Threats') : activeTab === 'metrics' ? tl('数据分析看板', 'Analytics Dashboard') : tl('Git版本与架构', 'Git Version & Schema')}
            </div>
          </div>

          {/* Tab Content Panels */}
          <div className="flex-1 overflow-y-auto p-4 space-y-4">
            
            {/* TAB 1: COGNITIVE FUSION MATRIX */}
            {activeTab === 'fusion' && (
              <FusionMatrixTab activeScenario={activeScenario} />
            )}

            {/* TAB 2: DECISION DESK & PROPOSAL APPROVALS */}
            {activeTab === 'decision' && (
              <DecisionDeskTab 
                activeScenario={activeScenario}
                proposals={proposals}
                isLoadingProposals={isLoadingProposals}
                fetchProposalsList={fetchProposalsList}
                handleApproveProposal={handleApproveProposal}
                handleRejectProposal={handleRejectProposal}
                resolvingProposalId={resolvingProposalId}
                simQuery={simQuery}
                setSimQuery={setSimQuery}
                simRole={simRole}
                setSimRole={setSimRole}
                simResult={simResult}
                isSimulating={isSimulating}
                handleRunSandbox={handleRunSandbox}
              />
            )}

            {/* TAB 3: ANALYTICAL METRICS & RECHARTS */}
            {activeTab === 'metrics' && (
              <MetricsTab threatRadarData={threatRadarData} efficiencyData={efficiencyData} />
            )}

            {/* TAB 4: GIT VERSION CENTER */}
            {activeTab === 'git' && (
              <GitVersionTab
                gitCommits={gitCommits}
                gitBranches={gitBranches}
                selectedScenarioId={selectedScenarioId}
                gitCommitMsg={gitCommitMsg}
                setGitCommitMsg={setGitCommitMsg}
                gitTerminalLogs={gitTerminalLogs}
                isGitPushing={isGitPushing}
                gitViewMode={gitViewMode}
                setGitViewMode={setGitViewMode}
                selectedCommitId={selectedCommitId}
                setSelectedCommitId={setSelectedCommitId}
                handleGitCommitManual={handleGitCommitManual}
                handleGitCheckoutCommit={handleGitCheckoutCommit}
                handleSwitchGitBranch={handleSwitchGitBranch}
                handleGitPushRemote={handleGitPushRemote}
              />
            )}

          </div>

        </div>

      </div>

      {/* GUIDED CONFIGURATION WIZARD MODAL */}
      {showWizardModal && (
        <div className="fixed inset-0 bg-black/80 backdrop-blur-xs flex items-center justify-center p-4 z-50 animate-fadeIn overflow-y-auto">
          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl max-w-2xl w-full overflow-hidden shadow-2xl my-8`}>
            {/* Header */}
            <div className={`p-4 ${styles.cardBg} border-b ${styles.cardBorder} flex items-center justify-between`}>
              <span className="text-sm font-bold text-white flex items-center gap-1.5">
                <LucideIcon name="Briefcase" size={14} className="text-indigo-400" />
                {wizardScenarioId ? '修改场景要素对接 (ECOS 全生命周期向导)' : '创建全新 ECOS 业务融合场景 (全要素向导)'}
              </span>
              <button
                onClick={() => setShowWizardModal(false)}
                className={`${styles.cardTextMuted} hover:text-white cursor-pointer`}
              >
                <LucideIcon name="X" size={16} />
              </button>
            </div>

            {/* Step Indicators */}
            <div className={`${styles.cardBg} px-4 py-3 border-b ${styles.cardBorder} overflow-x-auto shrink-0`}>
              <div className="flex items-center justify-between min-w-[760px] px-2 py-1">
                {[
                  { step: 1, label: '1. 场景要素', icon: 'Briefcase' },
                  { step: 2, label: '2. 物理数据', icon: 'Database' },
                  { step: 3, label: '3. 联邦本体', icon: 'Network' },
                  { step: 4, label: '4. 合规知识', icon: 'BookOpen' },
                  { step: 5, label: '5. AI 智能体', icon: 'Cpu' },
                  { step: 6, label: '6. 应用工作台', icon: 'Layout' },
                  { step: 7, label: '7. 安全阻断', icon: 'ShieldCheck' }
                ].map((item, idx, arr) => (
                  <React.Fragment key={item.step}>
                    <div className="flex items-center gap-1.5">
                      <div className={`w-6 h-6 rounded-full flex items-center justify-center text-[10px] font-bold transition-all ${
                        wizardStep === item.step 
                          ? 'bg-indigo-600 text-white ring-4 ring-indigo-950 shadow-md' 
                          : wizardStep > item.step 
                            ? 'bg-emerald-600 text-white font-bold' 
                            : `bg-slate-800 ${styles.cardTextMuted} border border-slate-700`
                      }`}>
                        {wizardStep > item.step ? '✓' : item.step}
                      </div>
                      <span className={`text-[11px] font-bold whitespace-nowrap transition-colors ${
                        wizardStep === item.step 
                          ? 'text-indigo-400 font-extrabold' 
                          : wizardStep > item.step 
                            ? 'text-emerald-500' 
                            : `${styles.cardTextMuted}`
                      }`}>
                        {item.label}
                      </span>
                    </div>
                    {idx < arr.length - 1 && (
                      <div className={`flex-1 h-[2px] mx-2 min-w-[12px] transition-all duration-300 ${
                        wizardStep > item.step ? 'bg-emerald-600/60' : 'bg-slate-800'
                      }`} />
                    )}
                  </React.Fragment>
                ))}
              </div>
            </div>

            {/* Scrollable Content Area */}
            <div className="p-5 max-h-[55vh] overflow-y-auto space-y-4 text-xs">
              
              {/* STEP 1: Scenario Elements & Project Goals */}
              {wizardStep === 1 && (
                <div className="space-y-4">
                  <div className={`p-3 ${styles.cardBg} border ${styles.cardBorder} rounded-lg space-y-1`}>
                    <span className="text-[11px] font-bold text-indigo-400 flex items-center gap-1">
                      <LucideIcon name="Info" size={12} />
                      ECOS 顶层设计 - 场景要素与项目战术目标定位
                    </span>
                    <p className={`text-[10px] ${styles.cardTextMuted} leading-relaxed`}>
                      从场景的基本物理特征、主责主控部门、安全目标与保障预算出发，定义该业务场景在数字太空中的核心价值边界与战术攻坚指向。
                    </p>
                  </div>

                  <div className="space-y-1.5">
                    <label className={`${styles.cardTextMuted} font-bold block`}>1. 场景/项目名称 (Scenario Name) <span className="text-red-400">*</span></label>
                    <input
                      type="text"
                      placeholder="例如：2026 跨境执勤时数综合对账合规中心"
                      value={wName}
                      onChange={(e) => setWName(e.target.value)}
                      className={`w-full p-2.5 ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-indigo-500 transition-colors`}
                    />
                  </div>

                  <div className="space-y-1.5">
                    <label className={`${styles.cardTextMuted} font-bold block`}>2. 核心战术目标 (Business Goal) <span className="text-red-400">*</span></label>
                    <input
                      type="text"
                      placeholder="例如：通过大模型对账推理，将飞行执勤对账时效降低90%，阻断SSN高敏泄露"
                      value={wGoal}
                      onChange={(e) => setWGoal(e.target.value)}
                      className={`w-full p-2.5 ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-indigo-500 transition-colors`}
                    />
                  </div>

                  <div className="space-y-1.5">
                    <label className={`${styles.cardTextMuted} font-bold block`}>3. 场景描述与背景 (Description)</label>
                    <textarea
                      placeholder="详细描述该业务场景涉及的关联部门、合规背景以及具体想要预防的零信任数据安全隐患。"
                      value={wDesc}
                      onChange={(e) => setWDesc(e.target.value)}
                      rows={3}
                      className={`w-full p-2.5 ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none font-sans focus:border-indigo-500 transition-colors`}
                    />
                  </div>

                  <div className="grid grid-cols-2 gap-4">
                    <div className="space-y-1.5">
                      <label className={`${styles.cardTextMuted} font-bold block`}>4. 主责主导部门</label>
                      <select
                        value={wDept}
                        onChange={(e) => setWDept(e.target.value)}
                        className={`w-full p-2 ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-indigo-500 transition-colors`}
                      >
                        <option value="民航 AOC 运行指挥部">民航 AOC 运行指挥部</option>
                        <option value="人资财务审计处">人资财务审计处</option>
                        <option value="新航线前沿探索战略部">新航线前沿探索战略部</option>
                        <option value="企业零信任安全审计处">企业零信任安全审计处</option>
                      </select>
                    </div>

                    <div className="space-y-1.5">
                      <label className={`${styles.cardTextMuted} font-bold block`}>5. 安全优先级 (Priority)</label>
                      <select
                        value={wPriority}
                        onChange={(e) => setWPriority(e.target.value as any)}
                        className={`w-full p-2 ${styles.inputBg} border ${styles.inputBorder} rounded text-indigo-400 font-bold outline-none focus:border-indigo-500 transition-colors`}
                      >
                        <option value="CRITICAL">🔥 CRITICAL (紧急核心)</option>
                        <option value="HIGH">⚡ HIGH (高度重要)</option>
                        <option value="MEDIUM">📋 MEDIUM (常规推进)</option>
                        <option value="LOW">🛡️ LOW (前瞻探索)</option>
                      </select>
                    </div>
                  </div>

                  <div className="grid grid-cols-3 gap-3">
                    <div className="space-y-1.5">
                      <label className={`${styles.cardTextMuted} font-bold block`}>6. 运行状态</label>
                      <select
                        value={wStatus}
                        onChange={(e) => setWStatus(e.target.value as any)}
                        className={`w-full p-2 ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none`}
                      >
                        <option value="DRAFT">📋 DRAFT (配置草稿)</option>
                        <option value="ACTIVE">⚡ ACTIVE (运行中)</option>
                        <option value="COMPLETED">✅ COMPLETED (已闭环)</option>
                        <option value="SUSPENDED">⚠️ SUSPENDED (暂阻断)</option>
                      </select>
                    </div>

                    <div className="space-y-1.5">
                      <label className={`${styles.cardTextMuted} font-bold block`}>7. 场景保障预算</label>
                      <input
                        type="text"
                        value={wBudget}
                        onChange={(e) => setWBudget(e.target.value)}
                        className={`w-full p-2 ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none`}
                      />
                    </div>

                    <div className="space-y-1.5">
                      <label className={`${styles.cardTextMuted} font-bold block`}>8. 目标安全性指数</label>
                      <input
                        type="text"
                        value={wSafetyIndex}
                        onChange={(e) => setWSafetyIndex(e.target.value)}
                        className={`w-full p-2 ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none`}
                      />
                    </div>
                  </div>
                </div>
              )}

              {/* STEP 2: Physical Datasets (数据对接) */}
              {wizardStep === 2 && (
                <div className="space-y-4">
                  <div className={`p-3 ${styles.cardBg} border ${styles.cardBorder} rounded-lg space-y-1`}>
                    <span className="text-[11px] font-bold text-indigo-400 flex items-center gap-1">
                      <LucideIcon name="Database" size={12} />
                      ECOS 物理数据层 (Physical Datasets Layer)
                    </span>
                    <p className={`text-[10px] ${styles.cardTextMuted} leading-relaxed`}>
                      联接底层原始物理数据表与主库变动流。这些物理数据是支撑整个融合场景本体演化与实时分析的绝对基石。
                    </p>
                  </div>

                  <div className="space-y-2">
                    <div className="flex items-center justify-between">
                      <label className={`${styles.cardText} font-bold block`}>
                        选择集成的物理表与数据源
                      </label>
                      <span className="text-[10px] text-indigo-400 font-mono">已选择 {wDatasets.length} 个</span>
                    </div>
                    <div className={`grid grid-cols-1 gap-2 max-h-[220px] overflow-y-auto p-2 ${styles.inputBg} border ${styles.inputBorder} rounded`}>
                      {AVAILABLE_DATASETS.map((ds: any) => {
                        const isChecked = wDatasets.includes(ds.id);
                        return (
                          <label key={ds.id} className={`flex items-start gap-2.5 p-2.5 rounded cursor-pointer transition-colors border ${
                            isChecked 
                              ? 'bg-indigo-950/30 border-indigo-500/40 text-indigo-200' 
                              : `${styles.cardBg} ${styles.cardBorder} ${styles.cardTextMuted} hover:bg-slate-850/40`
                          }`}>
                            <input
                              type="checkbox"
                              checked={isChecked}
                              onChange={() => {
                                if (isChecked) {
                                  setWDatasets(wDatasets.filter(id => id !== ds.id));
                                } else {
                                  setWDatasets([...wDatasets, ds.id]);
                                }
                              }}
                              className="mt-0.5 cursor-pointer accent-indigo-500"
                            />
                            <div className="space-y-0.5">
                              <span className="font-bold text-[11px] block text-slate-200">{ds.label}</span>
                              <span className={`text-[10px] ${styles.cardTextMuted} block leading-tight`}>{ds.desc}</span>
                            </div>
                          </label>
                        );
                      })}
                    </div>
                  </div>
                </div>
              )}

              {/* STEP 3: Logical Object Types (本体对接) */}
              {wizardStep === 3 && (
                <div className="space-y-4">
                  <div className={`p-3 ${styles.cardBg} border ${styles.cardBorder} rounded-lg space-y-1`}>
                    <span className="text-[11px] font-bold text-indigo-400 flex items-center gap-1">
                      <LucideIcon name="Network" size={12} />
                      ECOS 联邦逻辑本体层 (Logical Ontology Layer)
                    </span>
                    <p className={`text-[10px] ${styles.cardTextMuted} leading-relaxed`}>
                      将原始物理表在虚拟对齐空间内抽象为业务对象。本体屏蔽了底层关系型表的物理存储细节，使得智能体能直接进行常识推理。
                    </p>
                  </div>

                  <div className="space-y-2">
                    <div className="flex items-center justify-between">
                      <label className={`${styles.cardText} font-bold block`}>
                        勾选需要对接的联邦逻辑本体实体
                      </label>
                      <span className="text-[10px] text-indigo-400 font-mono">已选择 {wObjectTypes.length} 个</span>
                    </div>
                    <div className={`grid grid-cols-1 gap-2 max-h-[220px] overflow-y-auto p-2 ${styles.inputBg} border ${styles.inputBorder} rounded`}>
                      {AVAILABLE_OBJECTS.map((obj: any) => {
                        const isChecked = wObjectTypes.includes(obj.id);
                        return (
                          <label key={obj.id} className={`flex items-start gap-2.5 p-2.5 rounded cursor-pointer transition-colors border ${
                            isChecked 
                              ? 'bg-indigo-950/30 border-indigo-500/40 text-indigo-200' 
                              : `${styles.cardBg} ${styles.cardBorder} ${styles.cardTextMuted} hover:bg-slate-850/40`
                          }`}>
                            <input
                              type="checkbox"
                              checked={isChecked}
                              onChange={() => {
                                if (isChecked) {
                                  setWObjectTypes(wObjectTypes.filter(id => id !== obj.id));
                                } else {
                                  setWObjectTypes([...wObjectTypes, obj.id]);
                                }
                              }}
                              className="mt-0.5 cursor-pointer accent-indigo-500"
                            />
                            <div className="space-y-0.5">
                              <span className="font-bold text-[11px] block text-slate-200">{obj.label}</span>
                              <span className={`text-[10px] ${styles.cardTextMuted} block leading-tight`}>{obj.desc}</span>
                            </div>
                          </label>
                        );
                      })}
                    </div>
                  </div>
                </div>
              )}

              {/* STEP 4: Knowledge Bases (知识库对接) */}
              {wizardStep === 4 && (
                <div className="space-y-4">
                  <div className={`p-3 ${styles.cardBg} border ${styles.cardBorder} rounded-lg space-y-1`}>
                    <span className="text-[11px] font-bold text-indigo-400 flex items-center gap-1">
                      <LucideIcon name="BookOpen" size={12} />
                      ECOS 规则与先验知识库层 (Knowledge Base Layer)
                    </span>
                    <p className={`text-[10px] ${styles.cardTextMuted} leading-relaxed`}>
                      导入符合行业标准的合规审定标准、管理条例或应急预案。这构成了智能体进行逻辑推理、审查与核准的核心规则约束红线。
                    </p>
                  </div>

                  <div className="space-y-2">
                    <div className="flex items-center justify-between">
                      <label className={`${styles.cardText} font-bold block`}>
                        选择注入此场景的合规知识库
                      </label>
                      <span className="text-[10px] text-indigo-400 font-mono">已选择 {wKnowledgeBases.length} 个</span>
                    </div>
                    <div className={`grid grid-cols-1 gap-2 max-h-[220px] overflow-y-auto p-2 ${styles.inputBg} border ${styles.inputBorder} rounded`}>
                      {AVAILABLE_KNOWLEDGE.map((kb: any) => {
                        const isChecked = wKnowledgeBases.includes(kb.id);
                        return (
                          <label key={kb.id} className={`flex items-start gap-2.5 p-2.5 rounded cursor-pointer transition-colors border ${
                            isChecked 
                              ? 'bg-indigo-950/30 border-indigo-500/40 text-indigo-200' 
                              : `${styles.cardBg} ${styles.cardBorder} ${styles.cardTextMuted} hover:bg-slate-850/40`
                          }`}>
                            <input
                              type="checkbox"
                              checked={isChecked}
                              onChange={() => {
                                if (isChecked) {
                                  setWKnowledgeBases(wKnowledgeBases.filter(id => id !== kb.id));
                                } else {
                                  setWKnowledgeBases([...wKnowledgeBases, kb.id]);
                                }
                              }}
                              className="mt-0.5 cursor-pointer accent-indigo-500"
                            />
                            <div className="space-y-0.5">
                              <span className="font-bold text-[11px] block text-slate-200">{kb.label}</span>
                              <span className={`text-[10px] ${styles.cardTextMuted} block leading-tight`}>{kb.desc}</span>
                            </div>
                          </label>
                        );
                      })}
                    </div>
                  </div>
                </div>
              )}

              {/* STEP 5: AI Agents (AI 智能体对接) */}
              {wizardStep === 5 && (
                <div className="space-y-4">
                  <div className={`p-3 ${styles.cardBg} border ${styles.cardBorder} rounded-lg space-y-1`}>
                    <span className="text-[11px] font-bold text-indigo-400 flex items-center gap-1">
                      <LucideIcon name="Cpu" size={12} />
                      ECOS 协同认知智能体 (AI Agents & Copilots)
                    </span>
                    <p className={`text-[10px] ${styles.cardTextMuted} leading-relaxed`}>
                      将不同的后台推理大模型或自动化分析智能体指派给本场景。它们将协同处理自动排班、财务差异账单审查或敏感越权告警。
                    </p>
                  </div>

                  <div className="space-y-2">
                    <div className="flex items-center justify-between">
                      <label className={`${styles.cardText} font-bold block`}>
                        指派协同大语言模型与决策智能体
                      </label>
                      <span className="text-[10px] text-indigo-400 font-mono">已选择 {wAiAgents.length} 个</span>
                    </div>
                    <div className={`grid grid-cols-1 gap-2 max-h-[220px] overflow-y-auto p-2 ${styles.inputBg} border ${styles.inputBorder} rounded`}>
                      {AVAILABLE_AGENTS.map((agent: any) => {
                        const isChecked = wAiAgents.includes(agent.id);
                        return (
                          <label key={agent.id} className={`flex items-start gap-2.5 p-2.5 rounded cursor-pointer transition-colors border ${
                            isChecked 
                              ? 'bg-indigo-950/30 border-indigo-500/40 text-indigo-200' 
                              : `${styles.cardBg} ${styles.cardBorder} ${styles.cardTextMuted} hover:bg-slate-850/40`
                          }`}>
                            <input
                              type="checkbox"
                              checked={isChecked}
                              onChange={() => {
                                if (isChecked) {
                                  setWAiAgents(wAiAgents.filter(id => id !== agent.id));
                                } else {
                                  setWAiAgents([...wAiAgents, agent.id]);
                                }
                              }}
                              className="mt-0.5 cursor-pointer accent-indigo-500"
                            />
                            <div className="space-y-0.5">
                              <span className="font-bold text-[11px] block text-slate-200">{agent.label || agent.id}</span>
                              <span className={`text-[10px] ${styles.cardTextMuted} block leading-tight`}>{agent.desc}</span>
                            </div>
                          </label>
                        );
                      })}
                    </div>
                  </div>
                </div>
              )}

              {/* STEP 6: Application Workbenches (应用工作台对接) */}
              {wizardStep === 6 && (
                <div className="space-y-4">
                  <div className={`p-3 ${styles.cardBg} border ${styles.cardBorder} rounded-lg space-y-1`}>
                    <span className="text-[11px] font-bold text-indigo-400 flex items-center gap-1">
                      <LucideIcon name="Layout" size={12} />
                      ECOS 应用工作台与可视化看板 (Application Interfaces)
                    </span>
                    <p className={`text-[10px] ${styles.cardTextMuted} leading-relaxed`}>
                      联接面向用户的终端作业界面、低代码大盘以及业务决策流。这是最终保障运营总监、DPO 或调度员人机共协的窗口。
                    </p>
                  </div>

                  <div className="space-y-2">
                    <div className="flex items-center justify-between">
                      <label className={`${styles.cardText} font-bold block`}>
                        绑定前端业务展示系统与可视化大盘
                      </label>
                      <span className="text-[10px] text-indigo-400 font-mono">已选择 {wInterfaces.length} 个</span>
                    </div>
                    <div className={`grid grid-cols-1 gap-2 max-h-[220px] overflow-y-auto p-2 ${styles.inputBg} border ${styles.inputBorder} rounded`}>
                      {AVAILABLE_INTERFACES.map((ui: any) => {
                        const isChecked = wInterfaces.includes(ui.id);
                        return (
                          <label key={ui.id} className={`flex items-start gap-2.5 p-2.5 rounded cursor-pointer transition-colors border ${
                            isChecked 
                              ? 'bg-indigo-950/30 border-indigo-500/40 text-indigo-200' 
                              : `${styles.cardBg} ${styles.cardBorder} ${styles.cardTextMuted} hover:bg-slate-850/40`
                          }`}>
                            <input
                              type="checkbox"
                              checked={isChecked}
                              onChange={() => {
                                if (isChecked) {
                                  setWInterfaces(wInterfaces.filter(id => id !== ui.id));
                                } else {
                                  setWInterfaces([...wInterfaces, ui.id]);
                                }
                              }}
                              className="mt-0.5 cursor-pointer accent-indigo-500"
                            />
                            <div className="space-y-0.5">
                              <span className="font-bold text-[11px] block text-slate-200">{ui.label}</span>
                              <span className={`text-[10px] ${styles.cardTextMuted} block leading-tight`}>{ui.desc}</span>
                            </div>
                          </label>
                        );
                      })}
                    </div>
                  </div>
                </div>
              )}

              {/* STEP 7: Security Policies (安全层对接) */}
              {wizardStep === 7 && (
                <div className="space-y-4">
                  <div className={`p-3 ${styles.cardBg} border ${styles.cardBorder} rounded-lg space-y-1`}>
                    <span className="text-[11px] font-bold text-indigo-400 flex items-center gap-1">
                      <LucideIcon name="ShieldCheck" size={12} />
                      ECOS 零信任级联安全隔离域 (Zero-Trust Guardrail Policies)
                    </span>
                    <p className={`text-[10px] ${styles.cardTextMuted} leading-relaxed`}>
                      挂载强约束型的细粒度安全规则。对敏感 PII（如机长社保、薪酬）强制在拦截挂钩中遮蔽，非白名单 IP 自动沙阻断，强制总监多级签批。
                    </p>
                  </div>

                  <div className="space-y-2">
                    <div className="flex items-center justify-between">
                      <label className={`${styles.cardText} font-bold block`}>
                        勾选级联安全隔离域规则与拦截网
                      </label>
                      <span className="text-[10px] text-indigo-400 font-mono">已选择 {wSecurityPolicies.length} 个</span>
                    </div>
                    <div className={`grid grid-cols-1 gap-2 max-h-[220px] overflow-y-auto p-2 ${styles.inputBg} border ${styles.inputBorder} rounded`}>
                      {AVAILABLE_SECURITY.map((policy: any) => {
                        const isChecked = wSecurityPolicies.includes(policy.id);
                        return (
                          <label key={policy.id} className={`flex items-start gap-2.5 p-2.5 rounded cursor-pointer transition-colors border ${
                            isChecked 
                              ? 'bg-indigo-950/30 border-indigo-500/40 text-indigo-200' 
                              : `${styles.cardBg} ${styles.cardBorder} ${styles.cardTextMuted} hover:bg-slate-850/40`
                          }`}>
                            <input
                              type="checkbox"
                              checked={isChecked}
                              onChange={() => {
                                if (isChecked) {
                                  setWSecurityPolicies(wSecurityPolicies.filter(id => id !== policy.id));
                                } else {
                                  setWSecurityPolicies([...wSecurityPolicies, policy.id]);
                                }
                              }}
                              className="mt-0.5 cursor-pointer accent-indigo-500"
                            />
                            <div className="space-y-0.5">
                              <span className="font-bold text-[11px] block text-slate-200">{policy.label}</span>
                              <span className={`text-[10px] ${styles.cardTextMuted} block leading-tight`}>{policy.desc}</span>
                            </div>
                          </label>
                        );
                      })}
                    </div>
                  </div>
                </div>
              )}

            </div>

            {/* Footer / Navigation */}
            <div className={`p-4 ${styles.cardBg} border-t ${styles.cardBorder} flex justify-between items-center shrink-0`}>
              <div>
                <span className={`text-[10px] ${styles.cardTextMuted} font-mono`}>
                  步进 {wizardStep} / 7 | ECOS Engine Active
                </span>
              </div>
              <div className="flex gap-2">
                {wizardStep > 1 && (
                  <button
                    onClick={() => setWizardStep(wizardStep - 1)}
                    className={`px-3.5 py-1.5 ${styles.inputBg} ${styles.cardTextMuted} text-xs font-bold rounded cursor-pointer transition-all flex items-center gap-1`}
                  >
                    <LucideIcon name="ChevronLeft" size={12} />
                    上一步
                  </button>
                )}

                {wizardStep < 7 ? (
                  <button
                    onClick={() => {
                      if (wizardStep === 1) {
                        if (!wName.trim() || !wGoal.trim()) {
                          toast('error', '请先填写必填的场景名称与核心目标！');
                          return;
                        }
                      }
                      setWizardStep(wizardStep + 1);
                    }}
                    className="px-4 py-1.5 bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-bold rounded cursor-pointer transition-all flex items-center gap-1"
                  >
                    下一步
                    <LucideIcon name="ChevronRight" size={12} />
                  </button>
                ) : (
                  <button
                    onClick={handleSaveWizard}
                    className="px-5 py-1.5 bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-bold rounded cursor-pointer transition-all flex items-center gap-1 shadow-lg shadow-emerald-900/20"
                  >
                    <LucideIcon name="Check" size={12} />
                    {wizardScenarioId ? '保存全部要素绑定' : '完成并初始化融合场景'}
                  </button>
                )}
              </div>
            </div>
          </div>
        </div>
      )}

      {showCopilot && (
        <div className="absolute top-0 right-0 bottom-0 w-80 border-l border-[var(--border)] bg-[var(--card)] shadow-2xl z-40 flex flex-col overflow-hidden">
          <CopilotPanel agentType="scenario" />
        </div>
      )}

    </div>
  );
}
