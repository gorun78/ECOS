/**
 * ECOS 场景与项目综合调度中心（PMO-53 T1/T2：真实 API + scenario i18n namespace）。
 *
 * <p>场景数据来自 {@code /api/v1/workspace/scenarios}（ecos_business_scenario 表），
 * 不再使用 localStorage mock；新增「认知诊断与预测」Tab（CognitionPanel）发起
 * {@code POST /api/v1/workspace/scenarios/{id}/runs} 运行编排。</p>
 */
import React, { useEffect, useState } from 'react';
import LucideIcon from '../components/LucideIcon';
import { useLanguage } from '../components/LanguageContext';
import { showToastGlobal } from '../components/common/Toast';
import { useTheme } from '../components/ThemeContext';
import { CopilotPanel } from '../components/CopilotPanel';
import { apiFetchData } from '../api';
import type { BusinessScenario } from './project-workbench/types';
import { calcMetrics } from './project-workbench/helpers';
import { threatRadarData, efficiencyData } from './project-workbench/data';
import FusionMatrixTab from './project-workbench/tabs/FusionMatrixTab';
import DecisionDeskTab from './project-workbench/tabs/DecisionDeskTab';
import MetricsTab from './project-workbench/tabs/MetricsTab';
import GitVersionTab from './project-workbench/tabs/GitVersionTab';
import ScenarioList from './scenario/ScenarioList';
import ScenarioEditor, { type WizardState } from './scenario/ScenarioEditor';
import SimulationResultPanel from './scenario/SimulationResultPanel';
import CognitionPanel from './scenario/CognitionPanel';

/** 后端场景 VO（含业务目标，前端 BusinessScenario 展示同形） */
interface ScenarioApiVO {
  id: string;
  name: string;
  description?: string;
  businessGoal?: string;
  department?: string;
  priority?: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
  status?: 'ACTIVE' | 'DRAFT' | 'COMPLETED' | 'SUSPENDED';
  budget?: string;
  safetyIndexTarget?: number | string | null;
  actualSafetyIndex?: number | string | null;
  metrics?: Record<string, unknown>;
  createdAt?: string;
  bindings?: Record<string, string[]>;
}

/** 六类绑定分组 camelCase key */
const BINDING_GROUPS = ['datasets', 'objectTypes', 'knowledgeBases', 'aiAgents', 'securityPolicies', 'interfaces'] as const;
type BindingGroup = (typeof BINDING_GROUPS)[number];

/** 0~1（或百分比字符串）→ 展示百分比 */
function fmtIndex(v: number | string | null | undefined): string {
  if (v === null || v === undefined || v === '') return '—';
  const n = typeof v === 'string' ? parseFloat(v) : v;
  if (Number.isNaN(n)) return String(v);
  return `${n.toFixed(2)}%`;
}

/** 百分比字符串（向导输入 99.90%）→ 0~1 数值，非法回 null */
function parseIndex(v: string): number | null {
  const raw = v.replace('%', '').trim();
  const n = parseFloat(raw);
  if (Number.isNaN(n)) return null;
  return raw.includes('%') ? n / 100 : n;
}

/** 后端 VO → 前端展示模型（metrics 缺项由绑定要素补算） */
function toVM(vo: ScenarioApiVO): BusinessScenario {
  const bindings = {
    datasets: vo.bindings?.datasets ?? [],
    objectTypes: vo.bindings?.objectTypes ?? [],
    knowledgeBases: vo.bindings?.knowledgeBases ?? [],
    aiAgents: vo.bindings?.aiAgents ?? [],
    securityPolicies: vo.bindings?.securityPolicies ?? [],
    interfaces: vo.bindings?.interfaces ?? [],
  };
  const computed = calcMetrics(
    bindings.datasets, bindings.objectTypes, bindings.knowledgeBases,
    bindings.aiAgents, bindings.interfaces, bindings.securityPolicies,
  );
  const m = (vo.metrics ?? {}) as Record<string, number>;
  return {
    id: vo.id,
    name: vo.name,
    description: vo.description ?? '',
    businessGoal: vo.businessGoal ?? vo.description ?? '',
    department: vo.department ?? '—',
    priority: vo.priority ?? 'MEDIUM',
    status: vo.status ?? 'DRAFT',
    budget: vo.budget ?? '—',
    safetyIndexTarget: fmtIndex(vo.safetyIndexTarget),
    actualSafetyIndex: fmtIndex(vo.actualSafetyIndex),
    createdAt: (vo.createdAt ?? '').split(' ')[0],
    bindings,
    metrics: {
      integrityScore: m.integrityScore ?? computed.integrityScore,
      mappingCompleteness: m.mappingCompleteness ?? computed.mappingCompleteness,
      threatBlockRate: m.threatBlockRate ?? computed.threatBlockRate,
      slaScore: m.slaScore ?? computed.slaScore,
    },
  };
}

const BRANCH_DEFAULTS: Record<string, string> = { scen_summer_rush: 'main', scen_pilot_audit: 'main', scen_evtol_sandbox: 'main' };
const TABS = ['fusion', 'decision', 'metrics', 'cognition', 'git'] as const;
type TabKey = (typeof TABS)[number];

export default function ScenarioManagementView({
  showToast,
}: {
  showToast?: (type: 'success' | 'info' | 'error', message: string) => void;
}) {
  const toast = showToast || ((t: 'success' | 'info' | 'error', m: string) => showToastGlobal(t, m));
  const { t, locale } = useLanguage();
  /** 子组件兼容 tl(zh,en) 内联双语 */
  const tl = (zh: string, en: string) => (locale === 'zh' ? zh : en);
  const { styles } = useTheme();

  const [scenarios, setScenarios] = useState<BusinessScenario[]>([]);
  const [loadingScenarios, setLoadingScenarios] = useState(true);
  const [selectedScenarioId, setSelectedScenarioId] = useState('');
  const [activeTab, setActiveTab] = useState<TabKey>('fusion');
  const [showCopilot, setShowCopilot] = useState(false);
  const [showWizardModal, setShowWizardModal] = useState(false);
  const [wizardScenarioId, setWizardScenarioId] = useState<string | null>(null);
  const [wizardStep, setWizardStep] = useState(1);
  const [wName, setWName] = useState('');
  const [wGoal, setWGoal] = useState('');
  const [wDesc, setWDesc] = useState('');
  const [wDept, setWDept] = useState('');
  const [wPriority, setWPriority] = useState<'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'>('HIGH');
  const [wBudget, setWBudget] = useState('');
  const [wStatus, setWStatus] = useState<'ACTIVE' | 'DRAFT' | 'COMPLETED' | 'SUSPENDED'>('DRAFT');
  const [wSafetyIndex, setWSafetyIndex] = useState('99.90%');
  const [wDatasets, setWDatasets] = useState<string[]>([]);
  const [wObjectTypes, setWObjectTypes] = useState<string[]>([]);
  const [wKnowledgeBases, setWKnowledgeBases] = useState<string[]>([]);
  const [wAiAgents, setWAiAgents] = useState<string[]>([]);
  const [wInterfaces, setWInterfaces] = useState<string[]>([]);
  const [wSecurityPolicies, setWSecurityPolicies] = useState<string[]>([]);
  const [gitCommits, setGitCommits] = useState<{ [scenarioId: string]: any[] }>(() =>
    JSON.parse(localStorage.getItem('ecos_cached_git_commits') || '{}') || {},
  );
  const [gitBranches, setGitBranches] = useState<{ [scenarioId: string]: string }>(() =>
    JSON.parse(localStorage.getItem('ecos_cached_git_branches') || '{}') || BRANCH_DEFAULTS,
  );
  const [selectedCommitId, setSelectedCommitId] = useState<string | null>(null);
  const [gitCommitMsg, setGitCommitMsg] = useState('');
  const [gitTerminalLogs, setGitTerminalLogs] = useState<string[]>([]);
  const [isGitPushing, setIsGitPushing] = useState(false);
  const [gitViewMode, setGitViewMode] = useState<'visual' | 'json'>('visual');
  const [proposals, setProposals] = useState<any[]>([]);
  const [isLoadingProposals, setIsLoadingProposals] = useState(false);
  const [resolvingProposalId, setResolvingProposalId] = useState<string | null>(null);
  const [simQuery, setSimQuery] = useState('查询 UA102 机长张建国资质与保底工资');
  const [simRole, setSimRole] = useState<'AOC_DIRECTOR' | 'EXTERNAL_CONTRACTOR'>('AOC_DIRECTOR');
  const [simResult, setSimResult] = useState<any>(null);
  const [isSimulating, setIsSimulating] = useState(false);

  const activeScenario = scenarios.find((s) => s.id === selectedScenarioId) || scenarios[0];

  // ── 场景列表：真实 API（替代 localStorage mock） ──
  const refreshScenarios = async () => {
    try {
      const d = await apiFetchData<ScenarioApiVO[]>('/api/v1/workspace/scenarios');
      const list = (Array.isArray(d) ? d : []).map(toVM);
      setScenarios(list);
      if (list.length > 0 && !list.some((s) => s.id === selectedScenarioId)) {
        setSelectedScenarioId(list[0].id);
      }
    } catch (e) {
      console.error('Failed to fetch scenarios', e);
      toast('error', t('scenario.cog.refreshFailed', { msg: String(e) }));
    } finally {
      setLoadingScenarios(false);
    }
  };

  useEffect(() => {
    refreshScenarios();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    localStorage.setItem('ecos_cached_git_commits', JSON.stringify(gitCommits));
  }, [gitCommits]);
  useEffect(() => {
    localStorage.setItem('ecos_cached_git_branches', JSON.stringify(gitBranches));
  }, [gitBranches]);

  // ── Proposals（对账提案，走后端实时接口） ──
  const fetchProposalsList = async () => {
    setIsLoadingProposals(true);
    try {
      const d = await apiFetchData<any[]>('/api/v1/ontology/proposals');
      setProposals(Array.isArray(d) ? d : []);
    } catch (e) {
      console.error('Failed to fetch proposals', e);
    } finally {
      setIsLoadingProposals(false);
    }
  };
  useEffect(() => {
    fetchProposalsList();
    const timer = setInterval(fetchProposalsList, 10000);
    return () => clearInterval(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleApproveProposal = async (id: string, actionId: string) => {
    setResolvingProposalId(id);
    try {
      await apiFetchData(`/api/v1/ontology/proposals/${id}/approve`, {
        method: 'POST',
        body: JSON.stringify({ userRole: '签派总监', userName: '王凯' }),
      });
      toast('success', t('scenario.proposal.approveOk', { actionId }));
      fetchProposalsList();
      refreshScenarios();
    } catch (e: any) {
      toast('error', e?.message || t('scenario.proposal.approveErr'));
    } finally {
      setResolvingProposalId(null);
    }
  };

  const handleRejectProposal = async (id: string, actionId: string) => {
    setResolvingProposalId(id);
    const reason = prompt(t('scenario.proposal.rejectPrompt'), t('scenario.proposal.rejectDefault'));
    if (reason === null) {
      setResolvingProposalId(null);
      return;
    }
    try {
      await apiFetchData(`/api/v1/ontology/proposals/${id}/reject`, {
        method: 'POST',
        body: JSON.stringify({ userName: '王凯', reason }),
      });
      toast('info', t('scenario.proposal.rejectOk', { actionId }));
      fetchProposalsList();
    } catch (e: any) {
      toast('error', e?.message || t('scenario.proposal.networkErr', { msg: e?.message ?? e }));
    } finally {
      setResolvingProposalId(null);
    }
  };

  // ── Simulation（沙箱校验，走知识查询接口） ──
  const handleRunSandbox = async () => {
    setIsSimulating(true);
    const empty: BusinessScenario['bindings'] = {
      datasets: [], objectTypes: [], knowledgeBases: [], aiAgents: [], securityPolicies: [], interfaces: [],
    };
    const bindings = activeScenario?.bindings ?? empty;
    const params = {
      query: simQuery,
      userId: simRole === 'AOC_DIRECTOR' ? 'analyst_li' : 'contractor_xiao',
      orgId: simRole === 'AOC_DIRECTOR' ? 'org_aviation_hq' : 'org_contractor',
      clientIp: simRole === 'AOC_DIRECTOR' ? '10.120.5.23' : '222.22.22.22',
      projectId: bindings.securityPolicies[1] || 'proj_aviation_core',
      datasetId: bindings.datasets[0] || 'ds_flight_schedules',
      purposeId: bindings.securityPolicies[0] || 'purpose_fleet_opt_2026',
      distanceMetric: 'cosine',
    };
    try {
      const d = await apiFetchData<any>('/api/v1/knowledge/query', {
        method: 'POST',
        body: JSON.stringify(params),
      });
      setSimResult(d);
      toast(d.verdict === 'DENIED' ? 'error' : 'success', d.verdict === 'DENIED' ? t('scenario.sim.denied') : t('scenario.sim.granted'));
    } catch (e: any) {
      setSimResult({
        verdict: simRole === 'AOC_DIRECTOR' ? 'GRANTED' : 'DENIED',
        answer:
          simRole === 'AOC_DIRECTOR'
            ? `[本地沙箱] 已在「${activeScenario?.name ?? ''}」场景下执行。`
            : '⚠️ 外部承包商访问限制级 PII 被阻断。',
        groundedDocs: [{ title: 'CAAC 121部', score: 0.92 }],
      });
    } finally {
      setIsSimulating(false);
    }
  };

  // ── Wizard（创建/编辑 → 真实 API） ──
  const openCreateWizard = () => {
    setWizardScenarioId(null);
    setWizardStep(1);
    setWName('');
    setWGoal('');
    setWDesc('');
    setWDept('');
    setWPriority('HIGH');
    setWBudget('');
    setWStatus('DRAFT');
    setWSafetyIndex('99.90%');
    setWDatasets([]);
    setWObjectTypes([]);
    setWKnowledgeBases([]);
    setWAiAgents([]);
    setWInterfaces([]);
    setWSecurityPolicies([]);
    setShowWizardModal(true);
  };

  const openEditWizard = (scen: BusinessScenario) => {
    setWizardScenarioId(scen.id);
    setWizardStep(1);
    setWName(scen.name);
    setWGoal(scen.businessGoal);
    setWDesc(scen.description);
    setWDept(scen.department === '—' ? '' : scen.department);
    setWPriority(scen.priority);
    setWBudget(scen.budget === '—' ? '' : scen.budget);
    setWStatus(scen.status);
    const raw = scen.safetyIndexTarget.replace('%', '');
    setWSafetyIndex(scen.safetyIndexTarget === '—' ? '99.90%' : `${parseFloat(raw)}%`);
    setWDatasets(scen.bindings.datasets || []);
    setWObjectTypes(scen.bindings.objectTypes || []);
    setWKnowledgeBases(scen.bindings.knowledgeBases || []);
    setWAiAgents(scen.bindings.aiAgents || []);
    setWInterfaces(scen.bindings.interfaces || []);
    setWSecurityPolicies(scen.bindings.securityPolicies || []);
    setShowWizardModal(true);
  };

  const handleSaveWizard = async () => {
    if (!wName.trim()) {
      toast('error', t('scenario.err.fillName'));
      setWizardStep(1);
      return;
    }
    if (!wGoal.trim()) {
      toast('error', t('scenario.err.fillGoal'));
      setWizardStep(1);
      return;
    }
    const m = calcMetrics(wDatasets, wObjectTypes, wKnowledgeBases, wAiAgents, wInterfaces, wSecurityPolicies);
    const bindings: { bindingType: string; targetRef: string }[] = [];
    const groupToType: Record<BindingGroup, string> = {
      datasets: 'DATASET',
      objectTypes: 'OBJECT_TYPE',
      knowledgeBases: 'KNOWLEDGE_BASE',
      aiAgents: 'AI_AGENT',
      securityPolicies: 'SECURITY_POLICY',
      interfaces: 'INTERFACE',
    };
    /** 向导六类绑定 state → 六类分组 */
    const wizardArr: Record<BindingGroup, string[]> = {
      datasets: wDatasets,
      objectTypes: wObjectTypes,
      knowledgeBases: wKnowledgeBases,
      aiAgents: wAiAgents,
      securityPolicies: wSecurityPolicies,
      interfaces: wInterfaces,
    };
    BINDING_GROUPS.forEach((g) =>
      wizardArr[g].forEach((ref) => bindings.push({ bindingType: groupToType[g], targetRef: ref })),
    );
    const target = parseIndex(wSafetyIndex);
    const body = {
      name: wName,
      description: wDesc || wGoal,
      businessGoal: wGoal,
      department: wDept,
      priority: wPriority,
      status: wStatus,
      budget: wBudget,
      safetyIndexTarget: target,
      actualSafetyIndex: wStatus === 'ACTIVE' ? 0.995 : 0,
      metrics: m,
      bindings,
    };
    try {
      if (wizardScenarioId) {
        await apiFetchData(`/api/v1/workspace/scenarios/${wizardScenarioId}`, {
          method: 'PUT',
          body: JSON.stringify(body),
        });
        toast('success', t('scenario.cog.saveSuccess', { name: wName }));
      } else {
        const created = await apiFetchData<{ id: string }>('/api/v1/workspace/scenarios', {
          method: 'POST',
          body: JSON.stringify(body),
        });
        toast('success', t('scenario.cog.createSuccess', { name: wName }));
        setSelectedScenarioId(created?.id ?? '');
        setGitCommits((prev) => ({
          ...prev,
          [created?.id ?? '']: [
            {
              id: `c_${Date.now()}`,
              hash: Math.random().toString(16).substring(2, 9),
              author: 'Wizard_Init',
              date: new Date().toISOString().replace('T', ' ').substring(0, 19),
              message: `feat: 初始化场景「${wName}」`,
              bindings: body,
            },
          ],
        }));
        setGitBranches((prev) => ({ ...prev, [created?.id ?? '']: 'main' }));
      }
      await refreshScenarios();
      setShowWizardModal(false);
    } catch (e: any) {
      console.error('Save scenario failed', e);
      toast('error', t('scenario.cog.updateFailed', { msg: e?.message ?? e }));
    }
  };

  // ── Git 版本控制（演示数据，仅本地状态） ──
  const handleGitCommitManual = (msg: string) => {
    if (!msg.trim() || !activeScenario) {
      return;
    }
    const nc = {
      id: `c_${Date.now()}`,
      hash: Math.random().toString(16).substring(2, 9),
      author: 'AOC_Admin',
      date: new Date().toISOString().replace('T', ' ').substring(0, 19),
      message: msg.trim(),
      bindings: { ...activeScenario.bindings },
    };
    setGitCommits((prev) => ({ ...prev, [selectedScenarioId]: [...(prev[selectedScenarioId] || []), nc] }));
    setGitCommitMsg('');
    toast('success', `📦 Git 提交成功！${nc.hash}`);
  };

  const handleGitCheckoutCommit = (commit: any) => {
    if (!commit || !activeScenario) return;
    const wB = commit.bindings ?? {};
    const m = calcMetrics(
      wB.datasets || [], wB.objectTypes || [], wB.knowledgeBases || [],
      wB.aiAgents || [], wB.interfaces || [], wB.securityPolicies || [],
    );
    setScenarios((prev) =>
      prev.map((s) =>
        s.id === selectedScenarioId
          ? {
              ...s,
              bindings: {
                datasets: wB.datasets || [],
                objectTypes: wB.objectTypes || [],
                knowledgeBases: wB.knowledgeBases || [],
                aiAgents: wB.aiAgents || [],
                interfaces: wB.interfaces || [],
                securityPolicies: wB.securityPolicies || [],
              },
              metrics: m,
            }
          : s,
      ),
    );
    toast('success', `🔮 已回滚至 ${commit.hash}`);
  };

  const handleSwitchGitBranch = (branchName: string) => {
    setGitBranches((prev) => ({ ...prev, [selectedScenarioId]: branchName }));
    toast('info', `🔀 已切换至分支 [${branchName}]`);
  };

  const handleGitPushRemote = () => {
    if (isGitPushing) return;
    setIsGitPushing(true);
    setGitTerminalLogs([
      '$ git remote -v',
      'origin  gitlab.ecos.internal:aviation-dispatch/scenarios.git (fetch)',
      'origin  gitlab.ecos.internal:aviation-dispatch/scenarios.git (push)',
      '$ git status',
      `On branch ${gitBranches[selectedScenarioId] || 'main'}`,
      `Your branch is ahead by ${gitCommits[selectedScenarioId]?.length || 1} commits.`,
      `$ git push origin ${gitBranches[selectedScenarioId] || 'main'}`,
      '🔐 [MFA Signature Verified] AOC_DIRECTOR certificate approved.',
    ]);
    setTimeout(() => setGitTerminalLogs((prev) => [...prev, 'Enumerating objects: 7, done.', 'Counting objects: 100% (7/7), done.', 'Compressing objects: 100% (4/4), done.']), 1000);
    setTimeout(() => {
      setGitTerminalLogs((prev) => [...prev, 'Writing objects: 100% (4/4), done.', 'To gitlab.ecos.internal:aviation-dispatch/scenarios.git', '🟢 ECOS 云底座要素库配置冻结成功！']);
      setIsGitPushing(false);
      toast('success', '🚀 ECOS 云端同步完成！');
    }, 2500);
  };

  const wizard: WizardState = {
    showWizardModal,
    wizardScenarioId,
    wizardStep,
    wName,
    wGoal,
    wDesc,
    wDept,
    wPriority,
    wBudget,
    wStatus,
    wSafetyIndex,
    wDatasets,
    wObjectTypes,
    wKnowledgeBases,
    wAiAgents,
    wInterfaces,
    wSecurityPolicies,
  };

  const activeCount = scenarios.filter((s) => s.status === 'ACTIVE').length;
  const pendingCount = proposals.filter((p: any) => p.status === 'pending').length;
  const tabHint: Record<TabKey, string> = {
    fusion: t('scenario.hint.fusion'),
    decision: t('scenario.hint.decision'),
    metrics: t('scenario.hint.metrics'),
    cognition: t('scenario.hint.cognition'),
    git: t('scenario.hint.git'),
  };
  const tabIcon: Record<TabKey, string> = {
    fusion: 'Network',
    decision: 'ShieldCheck',
    metrics: 'TrendingUp',
    cognition: 'BrainCircuit',
    git: 'GitBranch',
  };
  const tabLabel: Record<TabKey, string> = {
    fusion: t('scenario.tab.fusion'),
    decision: t('scenario.tab.decision'),
    metrics: t('scenario.tab.metrics'),
    cognition: t('scenario.tab.cognition'),
    git: t('scenario.tab.git'),
  };

  // ══════════ RENDER ══════════
  return (
    <div className={`flex-1 flex flex-col ${styles.appBg} ${styles.appText} overflow-hidden font-sans relative`}>
      <div className={`p-4 ${styles.cardBg} border-b ${styles.cardBorder} shrink-0 flex flex-col md:flex-row md:items-center justify-between gap-4`}>
        <div>
          <div className="flex items-center gap-2">
            <span className="p-1.5 rounded-md bg-indigo-600 text-white flex items-center justify-center">
              <LucideIcon name="Briefcase" size={16} />
            </span>
            <h1 className="text-lg font-bold tracking-tight text-white flex items-center gap-2">
              {t('scenario.page.title')}{' '}
              <span className="text-[10px] bg-indigo-900/60 border border-indigo-700/50 px-2 py-0.5 rounded text-indigo-300 font-bold tracking-widest uppercase">
                Executive Cockpit
              </span>
            </h1>
          </div>
          <p className={`text-xs ${styles.cardTextMuted} mt-1`}>{t('scenario.page.subtitle')}</p>
        </div>
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-center max-w-2xl w-full md:w-auto">
          <KpiBox styles={styles} label={t('scenario.kpi.total')} value={`${scenarios.length}`} suffix={t('scenario.kpi.unit')} extra={t('scenario.kpi.activeExtra', { n: activeCount })} color="text-blue-400" />
          <KpiBox styles={styles} label={t('scenario.kpi.budget')} value="¥2.85M" color="text-indigo-400" />
          <KpiBox styles={styles} label={t('scenario.kpi.block')} value="100%" color="text-emerald-400" />
          <KpiBox styles={styles} label={t('scenario.kpi.settle')} value="2.8s" suffix={<span className="text-xs font-normal text-emerald-400">(-93.7%)</span>} color="text-amber-400" />
          <div className="col-span-2 sm:col-span-4 flex justify-end mt-1">
            <button
              onClick={() => setShowCopilot(!showCopilot)}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded border transition-colors cursor-pointer text-xs font-bold ${showCopilot ? 'bg-blue-600 text-white border-blue-500' : `${styles.cardBg} ${styles.cardTextMuted} border-[var(--border)] hover:bg-slate-800/50`}`}
            >
              <LucideIcon name="MessageSquare" size={12} />
              {showCopilot ? t('scenario.copilot.close') : t('scenario.copilot.open')}
            </button>
          </div>
        </div>
      </div>

      <div className="flex-1 flex overflow-hidden">
        <ScenarioList
          scenarios={scenarios}
          selectedScenarioId={selectedScenarioId}
          onSelect={setSelectedScenarioId}
          onCreateNew={openCreateWizard}
          styles={styles}
          locale={locale}
          tl={tl}
        />

        {loadingScenarios && !scenarios.length && (
          <div className={`flex-1 flex items-center justify-center text-xs ${styles.cardTextMuted}`}>
            {t('scenario.cog.loading')}
          </div>
        )}

        {activeScenario && (
          <div className={`flex-1 flex flex-col overflow-hidden ${styles.appBg}`}>
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
                  <span className={`text-xs ${styles.cardTextMuted}`}>
                    {t('scenario.dept')}：{' '}
                    <span className="text-xs bg-slate-800 border border-slate-700 px-2.5 py-1 rounded font-bold text-slate-200">
                      {activeScenario.department}
                    </span>
                  </span>
                  <button
                    onClick={() => openEditWizard(activeScenario)}
                    className="px-3 py-1 bg-indigo-600/20 hover:bg-indigo-600 border border-indigo-500/30 text-indigo-300 hover:text-white text-[11px] font-bold rounded flex items-center gap-1 transition-all cursor-pointer shadow-xs"
                  >
                    <LucideIcon name="Settings" size={11} className="text-indigo-400" />
                    {t('scenario.editBtn')}
                  </button>
                </div>
              </div>
              <div className={`grid grid-cols-2 sm:grid-cols-4 gap-4 mt-4 pt-3 border-t ${styles.cardBorder} text-xs font-mono`}>
                <div>
                  <span className={`${styles.cardTextMuted} text-[10px] block`}>{t('scenario.budget')}</span>
                  <span className="text-sm font-bold text-indigo-400">{activeScenario.budget}</span>
                </div>
                <div>
                  <span className={`${styles.cardTextMuted} text-[10px] block`}>{t('scenario.safetyTarget')}</span>
                  <span className="text-sm font-bold text-teal-400">{activeScenario.safetyIndexTarget}</span>
                </div>
                <div>
                  <span className={`${styles.cardTextMuted} text-[10px] block`}>{t('scenario.safetyActual')}</span>
                  <span className="text-sm font-bold text-emerald-400">{activeScenario.actualSafetyIndex}</span>
                </div>
                <div>
                  <span className={`${styles.cardTextMuted} text-[10px] block`}>{t('scenario.createdAt')}</span>
                  <span className={`text-sm font-bold ${styles.cardText}`}>{activeScenario.createdAt}</span>
                </div>
              </div>
            </div>

            <div className={`h-10 ${styles.cardBg} px-4 border-b ${styles.cardBorder} shrink-0 flex items-center justify-between`}>
              <div className="flex gap-2 overflow-x-auto">
                {TABS.map((tab) => (
                  <button
                    key={tab}
                    onClick={() => setActiveTab(tab)}
                    className={`px-4 h-10 border-b-2 text-xs font-bold transition-all flex items-center gap-1.5 cursor-pointer whitespace-nowrap ${activeTab === tab ? 'border-indigo-500 text-indigo-400' : `border-transparent ${styles.cardTextMuted} hover:text-slate-200`}`}
                  >
                    <LucideIcon name={tabIcon[tab]} size={13} />
                    <span>{tabLabel[tab]}</span>
                    {tab === 'decision' && pendingCount > 0 && (
                      <span className="bg-rose-500 text-white text-[9px] px-1.5 py-0.2 rounded-full animate-bounce">
                        {pendingCount} {t('scenario.pendingCount')}
                      </span>
                    )}
                  </button>
                ))}
              </div>
              <div className="text-[10px] text-indigo-400 font-mono font-bold bg-indigo-950/60 border border-indigo-900/60 px-2 py-0.5 rounded">
                {tabHint[activeTab]}
              </div>
            </div>

            <div className="flex-1 overflow-y-auto p-4 space-y-4">
              {activeTab === 'fusion' && <FusionMatrixTab activeScenario={activeScenario} />}
              {activeTab === 'decision' && (
                <>
                  <SimulationResultPanel
                    simQuery={simQuery}
                    setSimQuery={setSimQuery}
                    simRole={simRole}
                    setSimRole={setSimRole}
                    simResult={simResult}
                    isSimulating={isSimulating}
                    onRun={handleRunSandbox}
                    styles={styles}
                    locale={locale}
                    tl={tl}
                    safetyIndexActual={activeScenario.actualSafetyIndex}
                  />
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
                </>
              )}
              {activeTab === 'metrics' && <MetricsTab threatRadarData={threatRadarData} efficiencyData={efficiencyData} />}
              {activeTab === 'cognition' && <CognitionPanel scenario={activeScenario} onRunFinished={refreshScenarios} />}
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
        )}
      </div>

      <ScenarioEditor
        wizard={wizard}
        onClose={() => setShowWizardModal(false)}
        onStepChange={setWizardStep}
        onSave={() => void handleSaveWizard()}
        setWName={setWName}
        setWGoal={setWGoal}
        setWDesc={setWDesc}
        setWDept={setWDept}
        setWPriority={setWPriority}
        setWBudget={setWBudget}
        setWStatus={setWStatus}
        setWSafetyIndex={setWSafetyIndex}
        setWDatasets={setWDatasets}
        setWObjectTypes={setWObjectTypes}
        setWKnowledgeBases={setWKnowledgeBases}
        setWAiAgents={setWAiAgents}
        setWInterfaces={setWInterfaces}
        setWSecurityPolicies={setWSecurityPolicies}
        styles={styles}
        toast={toast}
      />

      {showCopilot && (
        <div className="absolute top-0 right-0 bottom-0 w-80 border-l border-[var(--border)] bg-[var(--card)] shadow-2xl z-40 flex flex-col overflow-hidden">
          <CopilotPanel agentType="scenario" />
        </div>
      )}
    </div>
  );
}

/** KPI 卡片（主题感知） */
function KpiBox({
  styles,
  label,
  value,
  suffix,
  extra,
  color,
}: {
  styles: any;
  label: string;
  value: string;
  suffix?: React.ReactNode;
  extra?: string;
  color: string;
}) {
  return (
    <div className={`${styles.cardBg} border ${styles.cardBorder} p-2 rounded-lg`}>
      <span className={`block text-[10px] ${styles.cardTextMuted} font-bold uppercase`}>{label}</span>
      <span className={`text-lg font-extrabold ${color}`}>
        {value} {suffix}
      </span>
      {extra && <span className={`block text-[9px] ${styles.cardTextMuted} font-mono`}>{extra}</span>}
    </div>
  );
}
