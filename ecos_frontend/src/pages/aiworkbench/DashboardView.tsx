/**
 * AI Workbench — Dashboard (Overview + Agent Evaluation panels)
 *
 * Wave-2A T2: all display strings via `dashboard.*` namespace (~98 keys).
 * RadarChart extracted to independent component file.
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useCallback, useRef } from 'react';
import {
  LayoutDashboard, Cpu, Bot, Layers, ShieldCheck, TrendingUp,
  Activity, ChevronRight, BarChart3, Play, RotateCcw, CheckCircle2,
  XCircle, Clock, Brain, Eye, Zap, ChevronDown
} from 'lucide-react';
import {
  AIPLogicPipeline, AIPAgent, AIPModel, AIPAuditLog,
  EvaluationSession
} from '../../types/aiworkbench';
import { useTheme } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';
import { startAipEval, fetchAipEvalReport, type AipEvalReport } from './api';
import DemoBadge from '../../components/aiworkbench/DemoBadge';
import RadarChart from './RadarChart';

interface DashboardViewProps {
  pipelines: AIPLogicPipeline[];
  agents: AIPAgent[];
  models: AIPModel[];
  auditLogs: AIPAuditLog[];
  /** 审计流加载失败时的错误文案（错误态 + 重试，禁 mock 播种） */
  auditError?: string;
  onRetryAudit?: () => void;
  onNavigateToView: (view: 'logic' | 'agent' | 'model' | 'guardrails') => void;
  showToast?: (type: 'success' | 'info' | 'error', msg: string) => void;
}

/**
 * §4.5 P0：评估改为真实引擎通道。
 * POST /api/v1/aip/eval/run {questionSet} → evalId；GET /api/v1/aip/eval/{id}/report 轮询。
 * 端点仅接受 questionSet（default | finance_20 | legal_20 | compliance_20），
 * 不支持指定智能体 —— 原「选择评估智能体」下拉已移除，避免形成假的关联语义。
 */
const EVAL_QUESTION_SETS = [
  { id: 'default', nameKey: 'aiworkbench.dashboard.qset.default', descKey: 'aiworkbench.dashboard.qset.default.desc' },
  { id: 'finance_20', nameKey: 'aiworkbench.dashboard.qset.finance20', descKey: 'aiworkbench.dashboard.qset.engine20.desc' },
  { id: 'legal_20', nameKey: 'aiworkbench.dashboard.qset.legal20', descKey: 'aiworkbench.dashboard.qset.engine20.desc' },
  { id: 'compliance_20', nameKey: 'aiworkbench.dashboard.qset.compliance20', descKey: 'aiworkbench.dashboard.qset.engine20.desc' },
] as const;

type EvalQSetId = (typeof EVAL_QUESTION_SETS)[number]['id'];

/**
 * W204/C186：原 categoryToRadarKey（把本地合成题目类别聚合为合成雷达分）已删除——
 * 雷达分只呈现引擎真实返回字段（见 startEvaluation）。
 */

export default function DashboardView({
  pipelines,
  agents,
  models,
  auditLogs,
  auditError,
  onRetryAudit,
  onNavigateToView,
}: DashboardViewProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  // ── Sub-tab state ─────────────────────────────────────────────
  const [subView, setSubView] = useState<'overview' | 'evaluation'>('overview');

  // ── Dashboard metrics ─────────────────────────────────────────
  const activePipelinesCount = pipelines.filter((p) => p.status === 'active').length;
  const activeAgentsCount = agents.filter((a) => a.status === 'active').length;
  const weeklyUsage = [120, 185, 240, 310, 290, 420, 385];
  const maxUsage = Math.max(...weeklyUsage);

  // ── Evaluation state ──────────────────────────────────────────
  const [selectedQSetId, setSelectedQSetId] = useState<EvalQSetId | ''>('');
  const [session, setSession] = useState<EvaluationSession | null>(null);
  const [isRunning, setIsRunning] = useState(false);
  const [evalError, setEvalError] = useState(false);

  const selectedQSet = EVAL_QUESTION_SETS.find((qs) => qs.id === selectedQSetId);

  // 轮询序号：reset/重启后旧轮询结果作废，避免过期写入 UI
  const evalRunSeq = useRef(0);

  /** 轮询引擎报告：running → 继续；completed/failed → 返回；连续网络错误 ≥5 → 抛出 */
  const pollAipEvalReport = useCallback(async (
    evalId: string,
    isStale: () => boolean,
    onTick: (r: AipEvalReport) => void,
  ): Promise<AipEvalReport> => {
    const maxAttempts = 150; // 2s 间隔，约 5 分钟上限
    let transient = 0;
    for (let i = 0; i < maxAttempts; i++) {
      await new Promise((resolve) => setTimeout(resolve, 2000));
      if (isStale()) throw new Error('eval-stale');
      try {
        const rep = await fetchAipEvalReport(evalId);
        transient = 0;
        if (rep.status !== 'running') return rep;
        onTick(rep);
      } catch (e) {
        transient += 1;
        if (transient >= 5) throw e;
      }
    }
    throw new Error('eval-timeout');
  }, []);

  // ── Start evaluation (§4.5 P0：真实引擎通道，不合成评分) ───────
  // POST /api/v1/aip/eval/run → evalId；GET /api/v1/aip/eval/{id}/report 轮询。
  // 失败/超时 → 错误态横幅，不展示任何合成数据。
  const startEvaluation = useCallback(async () => {
    if (!selectedQSetId || isRunning) return;
    const seq = evalRunSeq.current + 1;
    evalRunSeq.current = seq;
    const isStale = () => evalRunSeq.current !== seq;
    setEvalError(false);
    setSession({
      id: '',
      agentId: '',
      questionSetId: selectedQSetId,
      status: 'running',
      progress: 0,
      results: [],
      radarScores: {},
    });
    setIsRunning(true);
    try {
      const started = await startAipEval(selectedQSetId);
      if (isStale()) return;
      setSession((prev) => (prev ? { ...prev, id: started.evalId } : prev));
      const report = await pollAipEvalReport(started.evalId, isStale, (r) => {
        const done = r.items?.length ?? 0;
        const total = r.totalQuestions ?? 0;
        setSession((prev) => (prev ? { ...prev, progress: total > 0 ? Math.min(99, Math.round((done / total) * 100)) : prev.progress } : prev));
      });
      if (isStale()) return;
      setIsRunning(false);
      if (report.status !== 'completed') {
        setSession(null);
        setEvalError(true);
        return;
      }
      const dims = report.dimensionAvgs;
      setSession((prev) => (prev ? {
        ...prev,
        status: 'completed',
        progress: 100,
        // 明细只来自引擎报告 items（1-5 分制，原样呈现）
        results: (report.items ?? []).map((it) => ({
          questionId: it.questionId,
          question: it.question,
          category: it.category,
          score: it.score,
          status: 'completed' as const,
          response: it.agentAnswer,
          latency: it.durationMs,
          judgeReason: it.judgeReason,
        })),
        // 雷达：引擎真实五维（1-5 制）×20 归一到 0-100，未上报维度不画
        radarScores: dims ? {
          toolAccuracy: (dims.toolAccuracy ?? 0) * 20,
          keywordCoverage: (dims.keywordCoverage ?? 0) * 20,
          answerQuality: (dims.answerQuality ?? 0) * 20,
          executionEfficiency: (dims.executionEfficiency ?? 0) * 20,
          hallucinationResist: (dims.hallucinationResist ?? 0) * 20,
        } : {},
      } : prev));
    } catch {
      if (isStale()) return;
      setIsRunning(false);
      setSession(null);
      setEvalError(true);
    }
  }, [selectedQSetId, isRunning, pollAipEvalReport]);

  // ── Reset evaluation ──────────────────────────────────────────
  const resetEvaluation = useCallback(() => {
    evalRunSeq.current += 1;
    setIsRunning(false);
    setSession(null);
    setEvalError(false);
  }, []);

  // ── Weekday labels via i18n ───────────────────────────────────
  const weekdayLabels = [
    t('dashboard.trend.monday'), t('dashboard.trend.tuesday'), t('dashboard.trend.wednesday'),
    t('dashboard.trend.thursday'), t('dashboard.trend.friday'), t('dashboard.trend.saturday'),
    t('dashboard.trend.sunday'),
  ];

  // KPI cards — defined here so we keep the array declarative (single sub-block)
  // 护栏拦截率无统计端点 → 显示「未测量」，禁写死 100.00%（无假真理）
  const kpiCards = [
    { title: t('dashboard.kpi.pipeline.title'), value: `${activePipelinesCount} / ${pipelines.length}`, sub: t('dashboard.kpi.pipeline.sub'), icon: Cpu, color: `${styles.infoBg} ${styles.infoText}` },
    { title: t('dashboard.kpi.agent.title'), value: `${activeAgentsCount} / ${agents.length}`, sub: t('dashboard.kpi.agent.sub'), icon: Bot, color: `${styles.badgeBg} ${styles.badgeText}` },
    { title: t('dashboard.kpi.model.title'), value: `${models.filter((m) => m.status === 'connected').length} ${t('dashboard.kpi.model.unit')}`, sub: t('dashboard.kpi.model.sub'), icon: Layers, color: `${styles.successBg} ${styles.successText}` },
    { title: t('dashboard.kpi.guardrail.title'), value: t('aiworkbench.dashboard.kpi.guardrail.unmeasured'), sub: t('aiworkbench.dashboard.kpi.guardrail.subOffline'), icon: ShieldCheck, color: `${styles.dangerBg} ${styles.dangerText}` },
  ];

  // ── Render: Overview Dashboard ────────────────────────────────
  const renderOverview = () => (
    <div className="space-y-6 overflow-y-auto h-full p-6">
      {/* Welcome Banner — accentBg（深色 accent）+ text-white，4 主题下均保证白字可读（slate-light 不再使用 appBg 亮底） */}
      <div className={`${styles.accentBg} text-white rounded-xl p-6 shadow-sm relative overflow-hidden`}>
        <div className="absolute top-0 right-0 w-96 h-96 bg-gradient-to-br from-white/10 to-white/5 rounded-full blur-3xl pointer-events-none" />
        <div className="relative z-10 flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div className="space-y-1.5">
            <div className="flex items-center gap-2 text-white/80 text-xs font-semibold tracking-wider uppercase">
              <span className="w-2 h-2 rounded-full bg-white/80 animate-pulse" />
              <span>{t('dashboard.welcome.badge')}</span>
            </div>
            <h2 className="text-xl font-extrabold text-white">{t('dashboard.welcome.title')}</h2>
            <p className="text-xs text-white/80 max-w-2xl leading-relaxed">
              {t('dashboard.welcome.desc')}
            </p>
          </div>
          <div className="flex gap-2.5 shrink-0">
            <button type="button"
              onClick={() => onNavigateToView('agent')}
              className="px-3.5 py-1.5 bg-white/15 hover:bg-white/25 text-white border border-white/30 font-bold rounded-lg text-xs shadow-sm transition-all cursor-pointer flex items-center gap-1.5"
            >
              <Bot size={13} />
              <span>{t('dashboard.welcome.btnAgent')}</span>
            </button>
            <button type="button"
              onClick={() => onNavigateToView('logic')}
              className={`px-3.5 py-1.5 bg-[var(--muted,#1E293B)] hover:bg-[var(--card,#334155)] text-[var(--text-primary,#E2E8F0)] border ${styles.inputBorder} font-semibold rounded-lg text-xs transition-all cursor-pointer flex items-center gap-1.5`}
            >
              <Cpu size={13} />
              <span>{t('dashboard.welcome.btnLogic')}</span>
            </button>
          </div>
        </div>
      </div>

      {/* Key Metric Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {kpiCards.map((m, i) => (
          <div key={m.title} className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 flex items-center gap-4 hover:shadow-md transition-shadow`}>
            <div className={`p-3 rounded-xl shrink-0 ${m.color}`}>
              <m.icon size={18} />
            </div>
            <div className="space-y-0.5">
              <p className={`text-[10px] ${styles.cardTextMuted} font-bold uppercase tracking-wider`}>{m.title}</p>
              <p className={`text-lg font-bold ${styles.cardText}`}>{m.value}</p>
              <p className={`text-[10px] ${styles.cardTextMuted}`}>{m.sub}</p>
            </div>
          </div>
        ))}
      </div>

      {/* Visual Analytics Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left Chart: Token Requests Trend */}
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 lg:col-span-2 flex flex-col justify-between space-y-4`}>
          <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-3`}>
            <div className="flex items-center gap-2">
              <span className={`p-1 rounded-md ${styles.infoBg} ${styles.infoText}`}>
                <TrendingUp size={13} />
              </span>
              <h3 className={`text-xs font-bold ${styles.cardText}`}>{t('dashboard.trend.title')}</h3>
              <DemoBadge />
            </div>
            <span className={`text-[10px] ${styles.cardTextMuted} font-mono`}>{t('dashboard.trend.updated')}</span>
          </div>

          <div className="h-40 w-full relative pt-2">
            <div className={`absolute left-0 bottom-0 top-0 w-8 flex flex-col justify-between text-[9px] ${styles.cardTextMuted} font-mono pr-2 border-r ${styles.cardBorder}`}>
              <span>500</span>
              <span>250</span>
              <span>0</span>
            </div>
            <div className="ml-10 h-full relative flex items-end justify-between">
              <div className={`absolute left-0 right-0 top-0 border-t ${styles.cardBorder} border-dashed`} />
              <div className={`absolute left-0 right-0 top-1/2 border-t ${styles.cardBorder} border-dashed`} />
              <div className={`absolute left-0 right-0 bottom-0 border-b ${styles.cardBorder}`} />
              {weeklyUsage.map((val, idx) => {
                const heightPercent = (val / maxUsage) * 100;
                return (
                  <div key={idx} className="flex-1 flex flex-col items-center justify-end h-full px-1.5 group relative">
                    <span className="absolute -top-6 bg-[var(--muted,#1E293B)] text-white text-[9px] font-semibold px-1.5 py-0.5 rounded-sm opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none z-10 whitespace-nowrap">
                      {t('dashboard.trend.calls', { n: val })}
                    </span>
                    <div
                      className={`w-full ${styles.accentBg} rounded-t-sm transition-all`}
                      style={{ height: `${heightPercent * 0.8}%` }}
                    />
                    <span className={`text-[9px] ${styles.cardTextMuted} font-mono mt-1.5`}>
                      {weekdayLabels[idx]}
                    </span>
                  </div>
                );
              })}
            </div>
          </div>
        </div>

        {/* Right Chart: Model Distribution */}
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 flex flex-col justify-between`}>
          <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-3`}>
            <div className="flex items-center gap-2">
              <span className={`p-1 rounded-md ${styles.badgeBg} ${styles.badgeText}`}>
                <Layers size={13} />
              </span>
              <h3 className={`text-xs font-bold ${styles.cardText}`}>{t('dashboard.model.title')}</h3>
              <DemoBadge />
            </div>
            <span className={`text-[10px] ${styles.cardTextMuted} font-mono`}>{t('dashboard.model.byToken')}</span>
          </div>
          <div className="flex items-center justify-around py-4">
            <div className="relative w-24 h-24 flex items-center justify-center">
              <svg className="w-full h-full transform -rotate-90" viewBox="0 0 36 36">
                <circle cx="18" cy="18" r="15.915" fill="none" stroke="#f1f5f9" strokeWidth="3" />
                <circle cx="18" cy="18" r="15.915" fill="none" stroke="#3b82f6" strokeWidth="3" strokeDasharray="50 50" strokeDashoffset="0" />
                <circle cx="18" cy="18" r="15.915" fill="none" stroke="#8b5cf6" strokeWidth="3" strokeDasharray="30 70" strokeDashoffset="-50" />
                <circle cx="18" cy="18" r="15.915" fill="none" stroke="#10b981" strokeWidth="3" strokeDasharray="20 80" strokeDashoffset="-80" />
              </svg>
              <div className="absolute text-center">
                <p className={`text-xs font-black ${styles.cardText}`}>14.2M</p>
                <p className={`text-[8px] ${styles.cardTextMuted} uppercase font-bold`}>{t('dashboard.model.total')}</p>
              </div>
            </div>
            <div className="space-y-2 text-[10px]">
              <div className="flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-blue-500" />
                <span className={`font-semibold ${styles.cardTextMuted}`}>Gemini (50%)</span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-purple-500" />
                <span className={`font-semibold ${styles.cardTextMuted}`}>Claude (30%)</span>
              </div>
              <div className="flex items-center gap-1.5">
                <span className="w-2 h-2 rounded-full bg-emerald-500" />
                <span className={`font-semibold ${styles.cardTextMuted}`}>GPT-4o (20%)</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* AIP Security Guardrail Alerts */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl overflow-hidden shadow-xs`}>
        <div className={`px-4 py-3 border-b ${styles.cardBorder} ${styles.inputBg} flex items-center justify-between`}>
          <div className="flex items-center gap-2">
            <span className={`p-1 rounded ${styles.dangerBg} ${styles.dangerText}`}>
              <Activity size={13} />
            </span>
            <h3 className={`text-xs font-bold ${styles.cardText}`}>{t('dashboard.audit.title')}</h3>
          </div>
          <button type="button"
            onClick={() => onNavigateToView('guardrails')}
            className={`text-[10px] ${styles.accentText} font-bold flex items-center gap-0.5 cursor-pointer`}
          >
            <span>{t('dashboard.audit.manage')}</span>
            <ChevronRight size={11} />
          </button>
        </div>
        <div className={`divide-y ${styles.cardBorder} max-h-60 overflow-y-auto`}>
          {auditError ? (
            <div className={`p-4 flex items-center justify-between gap-3 text-xs ${styles.dangerText}`}>
              <span className="min-w-0">{auditError}</span>
              {onRetryAudit && (
                <button type="button" onClick={onRetryAudit} className={`px-2 py-1 rounded border ${styles.dangerBorder} font-bold cursor-pointer shrink-0`}>
                  {t('network.retry')}
                </button>
              )}
            </div>
          ) : auditLogs.length === 0 ? (
            <p className={`p-4 text-[11px] italic ${styles.cardTextMuted}`}>{t('aiworkbench.dashboard.audit.empty')}</p>
          ) : null}
          {auditLogs.map((log) => {
            const statusLabelKey =
              log.status === 'blocked' ? 'dashboard.audit.blocked'
              : log.status === 'flagged' ? 'dashboard.audit.flagged'
              : log.status === 'pending_approval' ? 'dashboard.audit.pending'
              : 'dashboard.audit.passed';
            return (
              <div key={log.id} className={`p-3 hover:bg-[var(--muted,rgba(0,0,0,0.04))] flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 text-xs`}>
                <div className="space-y-1">
                  <div className="flex flex-wrap items-center gap-1.5">
                    <span className={`font-mono text-[10px] ${styles.cardTextMuted} ${styles.appBg} px-1 py-0.5 rounded`}>{log.timestamp}</span>
                    <span className={`font-bold ${styles.cardTextMuted}`}>{log.assetName}</span>
                    <span className={`text-[10px] ${styles.cardTextMuted} font-semibold ${styles.inputBg} px-1.5 py-0.5 border ${styles.cardBorder} rounded`}>{log.source}</span>
                    <span className={`${styles.cardTextMuted} font-medium`}>{t('dashboard.audit.operator', { user: log.user })}</span>
                  </div>
                  <p className={`${styles.cardTextMuted} text-[11px] leading-relaxed`}>{log.details}</p>
                </div>
                <div className="flex flex-col items-end gap-1.5 shrink-0 self-end sm:self-center">
                  {log.localOrigin && (
                    <span className={`px-1.5 py-0.5 rounded text-[8px] font-bold ${styles.warningBg} ${styles.warningText}`}>
                      {t('aiworkbench.dashboard.audit.localChip')}
                    </span>
                  )}
                  {log.status === 'blocked' ? (
                    <span className={`px-2 py-0.5 rounded-full ${styles.dangerBg} ${styles.dangerText} font-bold text-[9px] flex items-center gap-1 border ${styles.dangerBorder}`}>
                      <span className="w-1 h-1 rounded-full bg-current" />{t(statusLabelKey)}
                    </span>
                  ) : log.status === 'flagged' ? (
                    <span className={`px-2 py-0.5 rounded-full ${styles.warningBg} ${styles.warningText} font-bold text-[9px] flex items-center gap-1 border ${styles.warningBorder}`}>
                      <span className="w-1 h-1 rounded-full bg-current animate-pulse" />{t(statusLabelKey)}
                    </span>
                  ) : log.status === 'pending_approval' ? (
                    <span className={`px-2 py-0.5 rounded-full ${styles.badgeBg} ${styles.badgeText} font-bold text-[9px] flex items-center gap-1 border ${styles.accentBorder}`}>
                      <span className="w-1.5 h-1.5 rounded-full bg-current animate-ping" />{t(statusLabelKey)}
                    </span>
                  ) : (
                    <span className={`px-2 py-0.5 rounded-full ${styles.successBg} ${styles.successText} font-bold text-[9px] flex items-center gap-1 border ${styles.successBorder}`}>
                      <span className="w-1 h-1 rounded-full bg-current" />{t(statusLabelKey)}
                    </span>
                  )}
                  {log.actionTaken && (
                    <span className={`text-[9px] ${styles.dangerText} font-mono font-bold`}>{log.actionTaken}</span>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );

  // ── Render: Evaluation Panel ──────────────────────────────────
  const renderEvaluation = () => (
    <div className="overflow-y-auto h-full p-6 space-y-5">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className={`p-2 rounded-lg ${styles.badgeBg} ${styles.badgeText}`}>
            <BarChart3 size={18} />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className={`text-sm font-extrabold ${styles.cardText}`}>{t('dashboard.eval.title')}</h2>
              <span className={`text-[9px] font-mono uppercase tracking-wider px-1.5 py-0.5 rounded border ${styles.accentBorder} ${styles.accentText}`}>
                {t('aiworkbench.dashboard.eval.realChannel')}
              </span>
            </div>
            <p className={`text-[10px] ${styles.cardTextMuted}`}>{t('aiworkbench.dashboard.eval.sub')}</p>
          </div>
        </div>
      </div>

      {/* Selection Panel — 引擎端点仅接受 questionSet（无 agentId），故不提供智能体选择 */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-5`}>
        <div className="space-y-1.5">
          <label className={`text-[10px] font-bold uppercase tracking-wider ${styles.cardTextMuted}`}>
            <Brain size={11} className="inline mr-1" />
            {t('dashboard.eval.qSetLabel')}
          </label>
          <div className="relative max-w-md">
            <select
              value={selectedQSetId}
              onChange={(e) => { setSelectedQSetId(e.target.value as EvalQSetId); resetEvaluation(); }}
              disabled={isRunning}
              className={`w-full appearance-none px-3 py-2 text-xs rounded-lg border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} font-medium cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed focus:outline-none`}
            >
              <option value="">{t('dashboard.eval.qSetPlaceholder')}</option>
              {EVAL_QUESTION_SETS.map((qs) => (
                <option key={qs.id} value={qs.id}>
                  {t(qs.nameKey)} · {qs.id}
                </option>
              ))}
            </select>
            <ChevronDown size={13} className={`absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none ${styles.cardTextMuted}`} />
          </div>
          {selectedQSet && (
            <p className={`text-[10px] ${styles.cardTextMuted} pl-1`}>{t(selectedQSet.descKey)}</p>
          )}
          <p className={`text-[10px] ${styles.cardTextMuted} pl-1`}>{t('aiworkbench.dashboard.eval.serverRunHint')}</p>
        </div>

        {/* Action Buttons */}
        <div className={`flex items-center gap-2.5 mt-4 pt-4 border-t ${styles.cardBorder}`}>
          <button type="button"
            onClick={startEvaluation}
            disabled={!selectedQSetId || isRunning}
            className={`px-4 py-2 ${styles.accentBg} ${styles.accentHover} text-white font-bold rounded-lg text-xs transition-all cursor-pointer flex items-center gap-1.5 disabled:opacity-50 disabled:cursor-not-allowed`}
          >
            <Play size={13} />
            <span>{t('dashboard.eval.start')}</span>
          </button>
          <button type="button"
            onClick={resetEvaluation}
            disabled={!session}
            className={`px-4 py-2 ${styles.appBg} hover:opacity-80 disabled:opacity-50 ${styles.cardTextMuted} font-semibold rounded-lg text-xs transition-all cursor-pointer flex items-center gap-1.5 disabled:cursor-not-allowed`}
          >
            <RotateCcw size={13} />
            <span>{t('dashboard.eval.reset')}</span>
          </button>
          {session && session.id && (
            <span className={`text-[10px] font-mono ml-auto ${styles.cardTextMuted}`}>
              evalId: {session.id}
            </span>
          )}
        </div>
      </div>

      {/* §2.3 诚实态：引擎失败/超时 → 错误横幅 + 重试，不展示任何合成评分 */}
      {evalError && (
        <div className={`flex items-center justify-between gap-3 border ${styles.dangerBorder} ${styles.dangerBg} ${styles.dangerText} rounded-xl p-4 text-xs`}>
          <span>{t('dashboard.eval.engineFail')}</span>
          <button
            type="button"
            onClick={startEvaluation}
            disabled={!selectedQSetId || isRunning}
            className={`px-2 py-1 rounded border ${styles.dangerBorder} font-bold cursor-pointer shrink-0 disabled:opacity-50`}
          >
            {t('network.retry')}
          </button>
        </div>
      )}

      {/* Progress */}
      {session && (
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-5`}>
          <div className="flex items-center justify-between mb-3">
            <div className="flex items-center gap-2">
              <span className={`p-1.5 rounded-md ${session.status === 'completed' ? `${styles.successBg} ${styles.successText}` : `${styles.badgeBg} ${styles.badgeText}`}`}>
                <Zap size={14} />
              </span>
              <span className={`text-xs font-bold ${styles.cardText}`}>
                {session.status === 'idle' && t('dashboard.eval.idle')}
                {session.status === 'running' && t('dashboard.eval.running')}
                {session.status === 'completed' && t('dashboard.eval.completed')}
              </span>
            </div>
            <span className={`text-[10px] font-mono font-bold ${session.status === 'completed' ? styles.successText : styles.infoText}`}>
              {session.progress}%
            </span>
          </div>
          <div className="w-full rounded-full h-2 overflow-hidden" style={{ backgroundColor: "color-mix(in srgb, var(--muted-foreground, #64748B) 25%, transparent)" }}>
            <div
              className={`h-full rounded-full transition-all duration-500 ${session.status === 'completed' ? `${styles.successBg}` : `${styles.accentBg}`}`}
              style={{ width: `${session.progress}%` }}
            />
          </div>
          <div className="flex flex-wrap gap-1.5 mt-3">
            {session.results.map((r, i) => {
              return (
                <span key={r.questionId} className={`text-[9px] font-mono px-2 py-1 rounded border ${
                  r.status === 'completed' ? `${styles.successBg} ${styles.successBorder} ${styles.successText}` :
                  r.status === 'running' ? `${styles.infoBg} ${styles.infoBorder} ${styles.infoText}` :
                  r.status === 'failed' ? `${styles.dangerBg} ${styles.dangerBorder} ${styles.dangerText}` :
                  `${styles.appBg} ${styles.cardBorder} ${styles.cardTextMuted}`
                }`}>
                  Q{i + 1}
                </span>
              );
            })}
          </div>
        </div>
      )}

      {/* Radar Chart + Details Grid — only when session exists */}
      {session && (
        <div className="grid grid-cols-1 lg:grid-cols-5 gap-5">
          <div className={`lg:col-span-2 ${styles.cardBg} border ${styles.cardBorder} rounded-xl p-5 flex flex-col items-center`}>
            <div className="flex items-center gap-2 self-start mb-4">
              <span className={`p-1.5 rounded-md ${styles.badgeBg} ${styles.badgeText}`}>
                <BarChart3 size={14} />
              </span>
              <span className={`text-xs font-bold ${styles.cardText}`}>{t('dashboard.eval.radarTitle')}</span>
            </div>
            {Object.keys(session.radarScores).length > 0 ? (
              <RadarChart scores={session.radarScores} />
            ) : (
              <div className="flex-1 flex items-center justify-center">
                <p className={`text-xs ${styles.cardTextMuted}`}>
                  {session.status === 'completed' ? t('dashboard.eval.radarComputing') : t('dashboard.eval.radarIdle')}
                </p>
              </div>
            )}
            {/* Legend — 引擎真实五维 */}
            <div className="grid grid-cols-3 gap-x-3 gap-y-1.5 mt-3 w-full max-w-xs">
              {RADAR_DIM_LEGEND.map((dim) => (
                <div key={dim.key} className="flex items-center gap-1.5 text-[10px]">
                  <span className={`w-2 h-2 rounded-full shrink-0 bg-current ${styles[dim.token]}`} />
                  <span className={styles.cardTextMuted}>{t(`aiworkbench.dashboard.radar.${dim.key}`)}</span>
                  <span className={`font-bold ${styles.cardText} ml-auto`}>{session.radarScores[dim.key] ?? '-'}</span>
                </div>
              ))}
            </div>
          </div>

          {/* Right: Per-Question Details */}
          <div className={`lg:col-span-3 ${styles.cardBg} border ${styles.cardBorder} rounded-xl overflow-hidden`}>
            <div className={`px-4 py-3 border-b ${styles.cardBorder} ${styles.inputBg} flex items-center gap-2`}>
              <span className={`p-1 rounded ${styles.infoBg} ${styles.infoText}`}>
                <Eye size={12} />
              </span>
              <h3 className={`text-xs font-bold ${styles.cardText}`}>{t('dashboard.eval.detailTitle')}</h3>
              <span className={`text-[10px] ${styles.cardTextMuted} ml-auto`}>
                {t('dashboard.eval.detailCount', {
                  done: session.results.filter((r) => r.status === 'completed').length,
                  total: session.results.length,
                })}
              </span>
            </div>
            <div className={`max-h-[420px] overflow-y-auto divide-y divide-[var(--card-border,#E2E8F0)]`}>
              {session.results.map((r, i) => (
                <div key={r.questionId} className="p-4 space-y-2">
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex items-start gap-2.5 min-w-0 flex-1">
                      <span className="mt-0.5 shrink-0">
                        {r.status === 'completed' ? (
                          <CheckCircle2 size={14} className={styles.successText} />
                        ) : r.status === 'running' ? (
                          <Clock size={14} className={`${styles.infoText} animate-pulse`} />
                        ) : r.status === 'failed' ? (
                          <XCircle size={14} className={styles.dangerText} />
                        ) : (
                          <Clock size={14} className={`${styles.cardTextMuted} opacity-50`} />
                        )}
                      </span>
                      <div className="min-w-0">
                        <p className={`text-[11px] font-semibold ${styles.cardText} leading-snug`}>
                          <span className={`text-[10px] ${styles.cardTextMuted} font-mono mr-1.5`}>Q{i + 1}</span>
                          {r.question}
                        </p>
                        {r.response && (
                          <p className={`text-[10px] ${styles.cardTextMuted} mt-1 leading-relaxed line-clamp-2`}>
                            {r.response}
                          </p>
                        )}
                        {r.judgeReason && (
                          <p className={`text-[10px] ${styles.cardTextMuted} mt-1 italic leading-relaxed line-clamp-2`}>
                            {r.judgeReason}
                          </p>
                        )}
                      </div>
                    </div>
                    {r.status === 'completed' && (
                      <div className={`shrink-0 px-2.5 py-1.5 rounded-lg text-center border ${
                        r.score >= 4 ? `${styles.successBg} ${styles.successBorder}` :
                        r.score >= 3 ? `${styles.warningBg} ${styles.warningBorder}` :
                        `${styles.dangerBg} ${styles.dangerBorder}`
                      }`}>
                        <p className={`text-lg font-black ${
                          r.score >= 4 ? styles.successText :
                          r.score >= 3 ? styles.warningText : styles.dangerText
                        }`}>{r.score}<span className={`text-[9px] ${styles.cardTextMuted}`}>/5</span></p>
                        <p className={`text-[8px] ${styles.cardTextMuted} font-bold uppercase`}>{t('dashboard.eval.scoreLabel')}</p>
                      </div>
                    )}
                  </div>
                  <div className="flex items-center gap-3 text-[9px]">
                    <span className={`px-1.5 py-0.5 rounded font-medium ${styles.inputBg} ${styles.cardTextMuted}`}>
                      {r.category}
                    </span>
                    {r.latency !== undefined && (
                      <span className={styles.cardTextMuted}>
                        {t('dashboard.eval.latency', { ms: r.latency })}
                      </span>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );

  // ── Main Return ────────────────────────────────────────────────
  return (
    <div className={`flex flex-col h-full ${styles.appBg}`}>
      <div className={`flex items-center gap-1 px-6 py-2 border-b ${styles.cardBorder} ${styles.inputBg} shrink-0`}>
        <button type="button"
          onClick={() => setSubView('overview')}
          className={`px-3.5 py-1.5 rounded-lg text-[11px] font-bold transition-all cursor-pointer flex items-center gap-1.5 ${
            subView === 'overview'
              ? `${styles.accentBg} text-white shadow-sm`
              : `${styles.cardTextMuted} hover:opacity-80`
          }`}
        >
          <LayoutDashboard size={13} />
          <span>{t('dashboard.tab.overview')}</span>
        </button>
        <button type="button"
          onClick={() => setSubView('evaluation')}
          className={`px-3.5 py-1.5 rounded-lg text-[11px] font-bold transition-all cursor-pointer flex items-center gap-1.5 ${
            subView === 'evaluation'
              ? `${styles.accentBg} text-white shadow-sm`
              : `${styles.cardTextMuted} hover:opacity-80`
          }`}
        >
          <BarChart3 size={13} />
          <span>{t('dashboard.tab.evaluation')}</span>
        </button>
      </div>

      <div className="flex-1 overflow-hidden">
        {subView === 'overview' ? renderOverview() : renderEvaluation()}
      </div>
    </div>
  );
}

/** Static radar legend — 与引擎 dimensionAvgs 五维一一对应（无假维度）；色点用主题 token。 */
const RADAR_DIM_LEGEND: Array<{ key: string; token: 'infoText' | 'accentText' | 'successText' | 'warningText' | 'dangerText' }> = [
  { key: 'toolAccuracy', token: 'infoText' },
  { key: 'keywordCoverage', token: 'accentText' },
  { key: 'answerQuality', token: 'successText' },
  { key: 'executionEfficiency', token: 'warningText' },
  { key: 'hallucinationResist', token: 'dangerText' },
];

