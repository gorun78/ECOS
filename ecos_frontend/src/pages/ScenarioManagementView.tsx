/**
 * ECOS 场景与项目综合调度中心（PMO-53 T1/T2：真实 API + scenario i18n namespace）。
 *
 * <p>场景数据来自 {@code /api/v1/workspace/scenarios}（ecos_business_scenario 表），
 * 不再使用 localStorage mock。§3.8 R1~R2（2026-10-11 双结构收敛）：本页收编为
 * 「高级视图」，仅保留融合/决策/Git 三 Tab；原「认知」Tab（CognitionPanel 运行编排 +
 * CognitiveRightPanel 认知四卡）整体迁至 L2 场景详情「高级/调试」子面
 * （{@code pages/workbench/[scenarioId]/advanced/AdvancedDebugPanel.tsx}，迁一删一去重），
 * 原「指标」Tab（MetricsTab 静态演示图表数据 = 假真理）随收敛清除。
 * Git 版本 UI 一律走 {@code services/gitService} 单一通道（经 GitPanel，只传 repositoryId），
 * 原假 git push 动画与 {@code ecos_cached_git_*} localStorage 伪持久化已删除（F05-17/B.9-5）。</p>
 */
import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import LucideIcon from '../components/LucideIcon';
import { useLanguage } from '../components/LanguageContext';
import { showToastGlobal } from '../components/common/Toast';
import { useTheme, type ThemeStyles } from '../components/ThemeContext';
import { CopilotPanel } from '../components/CopilotPanel';
import GitPanel from '../components/data-workbench/GitPanel';
import { apiFetchData } from '../api';
import logger from '../services/logger';
import type { BusinessScenario } from './project-workbench/types';
import { calcMetrics } from './project-workbench/helpers';
import FusionMatrixTab from './project-workbench/tabs/FusionMatrixTab';
import DecisionDeskTab from './project-workbench/tabs/DecisionDeskTab';
import ScenarioList from './scenario/ScenarioList';
import ScenarioEditor, { type WizardState } from './scenario/ScenarioEditor';
import SimulationResultPanel from './scenario/SimulationResultPanel';
import { COGNITION_POLL_INTERVAL_MS } from '../services/cognitiveClient';

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

const TABS = ['fusion', 'decision', 'git'] as const;
type TabKey = (typeof TABS)[number];

export default function ScenarioManagementView({
  showToast,
}: {
  showToast?: (type: 'success' | 'info' | 'error', message: string) => void;
}) {
  const toast = showToast || ((t: 'success' | 'info' | 'error', m: string) => showToastGlobal(t, m));
  const { t, locale } = useLanguage();
  /** 兼容旧内联双语助手（仅 ScenarioList 仍依赖；待 ScenarioList 迁移 t() 后删除） */
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
  const [proposals, setProposals] = useState<unknown[]>([]);
  const [isLoadingProposals, setIsLoadingProposals] = useState(false);
  const [resolvingProposalId, setResolvingProposalId] = useState<string | null>(null);

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
      logger.error('scenarios', e);
      toast('error', t('scenario.cog.refreshFailed', { msg: String(e) }));
    } finally {
      setLoadingScenarios(false);
    }
  };

  useEffect(() => {
    refreshScenarios();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // ── Proposals（对账提案，走后端实时接口） ──
  const fetchProposalsList = async () => {
    setIsLoadingProposals(true);
    try {
      const d = await apiFetchData<unknown[]>('/api/v1/ontology/proposals');
      setProposals(Array.isArray(d) ? d : []);
    } catch (e) {
      logger.error('proposals', e);
    } finally {
      setIsLoadingProposals(false);
    }
  };
  useEffect(() => {
    fetchProposalsList();
    const timer = setInterval(fetchProposalsList, COGNITION_POLL_INTERVAL_MS);
    return () => clearInterval(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handleApproveProposal = async (id: string, actionId: string) => {
    setResolvingProposalId(id);
    try {
      // 零信任身份由后端从 SSO 会话解析；前端不再内联伪造 userRole/userName（F07-22）
      await apiFetchData(`/api/v1/ontology/proposals/${id}/approve`, { method: 'POST' });
      toast('success', t('scenario.proposal.approveOk', { actionId }));
      fetchProposalsList();
      refreshScenarios();
    } catch (e) {
      const err = e as Error | undefined;
      toast('error', err?.message || t('scenario.proposal.approveErr'));
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
        body: JSON.stringify({ reason }),
      });
      toast('info', t('scenario.proposal.rejectOk', { actionId }));
      fetchProposalsList();
    } catch (e) {
      const err = e as Error | undefined;
      toast('error', err?.message || t('scenario.proposal.networkErr', { msg: String(err?.message ?? e) }));
    } finally {
      setResolvingProposalId(null);
    }
  };

  // ── 沙箱推演（旧知识查询假真理链路已删除，F07-22）。
  //    现由 SimulationResultPanel 自持 useCognition(scenarioId) 只读订阅 E1~E4，
  //    503 时仅提示不可用、绝不合成 verdict。

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
      }
      await refreshScenarios();
      setShowWizardModal(false);
    } catch (e) {
      const err = e as Error | undefined;
      logger.error('scenario-save', err ?? e);
      toast('error', t('scenario.cog.updateFailed', { msg: String(err?.message ?? e) }));
    }
  };

  // ── Git 版本控制：一律经 services/gitService 单一通道（GitPanel，只传 repositoryId），
  //    原假 push 动画 / 本地伪造 commit / localStorage 缓存已全部删除（design-05 B.9-5）──

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
  const pendingCount = proposals.filter((p) => (p as { status?: string }).status === 'pending').length;
  const tabHint: Record<TabKey, string> = {
    fusion: t('scenario.hint.fusion'),
    decision: t('scenario.hint.decision'),
    git: t('scenario.hint.git'),
  };
  const tabIcon: Record<TabKey, string> = {
    fusion: 'Network',
    decision: 'ShieldCheck',
    git: 'GitBranch',
  };
  const tabLabel: Record<TabKey, string> = {
    fusion: t('scenario.tab.fusion'),
    decision: t('scenario.tab.decision'),
    git: t('scenario.tab.git'),
  };

  // ══════════ RENDER ══════════
  return (
    <div className={`flex-1 flex flex-col ${styles.appBg} ${styles.appText} overflow-hidden font-sans relative`}>
      <div className={`p-4 ${styles.cardBg} border-b ${styles.cardBorder} shrink-0 flex flex-col md:flex-row md:items-center justify-between gap-4`}>
        <div>
          <div className="flex items-center gap-2">
            <span className={`p-1.5 rounded-md ${styles.accentBg} ${styles.cardText} flex items-center justify-center`}>
              <LucideIcon name="Briefcase" size={16} />
            </span>
            <h1 className={`text-lg font-bold tracking-tight ${styles.appText} flex items-center gap-2`}>
              {t('scenario.page.title')}{' '}
              <span className={`text-[10px] border px-2 py-0.5 rounded font-bold tracking-widest uppercase ${styles.badgeBg} ${styles.accentText} ${styles.cardBorder}`}>
                Executive Cockpit
              </span>
            </h1>
          </div>
          <p className={`text-xs ${styles.cardTextMuted} mt-1`}>{t('scenario.page.subtitle')}</p>
        </div>
        {/* 无假真理 P0（§3.8 收敛轮）：头部原三个硬编码演示 KPI（预算/拦截率/结算耗时）
            已删除——后端无对应聚合端点，禁本地合成；只保留真实计数。 */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-center max-w-2xl w-full md:w-auto">
          <KpiBox styles={styles} label={t('scenario.kpi.total')} value={`${scenarios.length}`} suffix={t('scenario.kpi.unit')} extra={t('scenario.kpi.activeExtra', { n: activeCount })} color={styles.infoText} />
          <KpiBox styles={styles} label={t('scenario.kpi.pending')} value={`${pendingCount}`} color={pendingCount > 0 ? styles.warningText : styles.accentText} />
          <div className="col-span-2 sm:col-span-4 flex justify-end mt-1">
            <button type="button"
              onClick={() => setShowCopilot(!showCopilot)}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded border transition-colors cursor-pointer text-xs font-bold ${showCopilot ? `${styles.accentBg} ${styles.cardText} ${styles.accentBorder}` : `${styles.cardBg} ${styles.cardTextMuted} ${styles.cardBorder} hover:opacity-80`}`}
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
                    <span className={`text-[10px] border px-2 py-0.5 rounded font-mono font-bold ${styles.successBg} ${styles.successText} ${styles.successBorder}`}>
                      ACTIVE SCENARIO
                    </span>
                    <span className={`text-xs font-semibold ${styles.cardTextMuted} font-mono`}>ID: {activeScenario.id}</span>
                  </div>
                  <h2 className={`text-base font-extrabold ${styles.cardText} mt-1`}>{activeScenario.name}</h2>
                  <p className={`text-xs ${styles.cardTextMuted} mt-1 max-w-4xl leading-relaxed`}>{activeScenario.description}</p>
                </div>
                <div className="flex flex-col gap-2 items-end self-end sm:self-start shrink-0">
                  {/* §3.8 R1：本页 = 高级视图；L2 场景工作台（Blueprint 中心 + 认知运行面）为唯一主骨架 */}
                  <Link
                    to={`/workbench/${activeScenario.id}`}
                    className={`px-3 py-1 border ${styles.accentBorder} ${styles.accentBg} ${styles.cardText} text-[11px] font-bold rounded flex items-center gap-1 transition-all cursor-pointer hover:opacity-90`}
                  >
                    <LucideIcon name="LayoutDashboard" size={11} />
                    {t('scenario.advanced.openL2')}
                  </Link>
                  <span className={`text-xs ${styles.cardTextMuted}`}>
                    {t('scenario.dept')}：{' '}
                    <span className={`text-xs border px-2.5 py-1 rounded font-bold ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}>
                      {activeScenario.department}
                    </span>
                  </span>
                  <button type="button"
                    onClick={() => openEditWizard(activeScenario)}
                    className={`px-3 py-1 border ${styles.accentBorder} ${styles.accentBg} ${styles.cardText} text-[11px] font-bold rounded flex items-center gap-1 transition-all cursor-pointer shadow-xs hover:opacity-90`}
                  >
                    <LucideIcon name="Settings" size={11} />
                    {t('scenario.editBtn')}
                  </button>
                </div>
              </div>
              <div className={`grid grid-cols-2 sm:grid-cols-4 gap-4 mt-4 pt-3 border-t ${styles.cardBorder} text-xs font-mono`}>
                <div>
                  <span className={`${styles.cardTextMuted} text-[10px] block`}>{t('scenario.budget')}</span>
                  <span className={`text-sm font-bold ${styles.accentText}`}>{activeScenario.budget}</span>
                </div>
                <div>
                  <span className={`${styles.cardTextMuted} text-[10px] block`}>{t('scenario.safetyTarget')}</span>
                  <span className={`text-sm font-bold ${styles.infoText}`}>{activeScenario.safetyIndexTarget}</span>
                </div>
                <div>
                  <span className={`${styles.cardTextMuted} text-[10px] block`}>{t('scenario.safetyActual')}</span>
                  <span className={`text-sm font-bold ${styles.successText}`}>{activeScenario.actualSafetyIndex}</span>
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
                  <button type="button"
                    key={tab}
                    onClick={() => setActiveTab(tab)}
                    className={`px-4 h-10 border-b-2 text-xs font-bold transition-all flex items-center gap-1.5 cursor-pointer whitespace-nowrap ${activeTab === tab ? `${styles.accentBorder} ${styles.accentText}` : `border-transparent ${styles.cardTextMuted} hover:opacity-80`}`}
                  >
                    <LucideIcon name={tabIcon[tab]} size={13} />
                    <span>{tabLabel[tab]}</span>
                    {tab === 'decision' && pendingCount > 0 && (
                      <span className={`${styles.dangerBg} ${styles.dangerText} text-[9px] px-1.5 py-0.2 rounded-full animate-bounce`}>
                        {pendingCount} {t('scenario.pendingCount')}
                      </span>
                    )}
                  </button>
                ))}
              </div>
              <div className={`text-[10px] font-mono font-bold px-2 py-0.5 rounded ${styles.accentText} ${styles.badgeBg} border ${styles.cardBorder}`}>
                {tabHint[activeTab]}
              </div>
            </div>

            <div className="flex-1 overflow-y-auto p-4 space-y-4">
              {activeTab === 'fusion' && <FusionMatrixTab activeScenario={activeScenario} />}
              {activeTab === 'decision' && (
                <>
                  <SimulationResultPanel scenarioId={activeScenario.id} styles={styles} />
                  <DecisionDeskTab
                    activeScenario={activeScenario}
                    proposals={proposals}
                    isLoadingProposals={isLoadingProposals}
                    fetchProposalsList={fetchProposalsList}
                    handleApproveProposal={handleApproveProposal}
                    handleRejectProposal={handleRejectProposal}
                    resolvingProposalId={resolvingProposalId}
                  />
                </>
              )}
              {activeTab === 'git' && (
                // Git UI 单一通道：services/gitService（只传 repositoryId = 场景 ID），
                // 数据/错误态由 GitPanel 内部按真实 API 渲染，禁本地伪造。
                <div className={`h-[560px] rounded-lg border overflow-hidden ${styles.cardBg} ${styles.cardBorder}`}>
                  <GitPanel repoId={activeScenario.id} />
                </div>
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
  styles: ThemeStyles;
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
