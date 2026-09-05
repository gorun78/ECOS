/**
 * AI Workbench — Dashboard (Overview + Agent Evaluation panels)
 *
 * Wave-2A T2: all display strings via `dashboard.*` namespace (~98 keys).
 * RadarChart extracted to independent component file.
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useCallback, useRef, useEffect } from 'react';
import {
  LayoutDashboard, Cpu, Bot, Layers, ShieldCheck, TrendingUp,
  Activity, ChevronRight, BarChart3, Play, RotateCcw, CheckCircle2,
  XCircle, Clock, Brain, Eye, Zap, ChevronDown
} from 'lucide-react';
import {
  AIPLogicPipeline, AIPAgent, AIPModel, AIPAuditLog,
  EvaluationQuestionSet, EvaluationResult, EvaluationSession
} from '../../types/aiworkbench';
import { useTheme } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';
import RadarChart from './RadarChart';

interface DashboardViewProps {
  pipelines: AIPLogicPipeline[];
  agents: AIPAgent[];
  models: AIPModel[];
  auditLogs: AIPAuditLog[];
  onNavigateToView: (view: 'logic' | 'agent' | 'model' | 'guardrails') => void;
  showToast?: (type: 'success' | 'info' | 'error', msg: string) => void;
}

/**
 * Build the question list for one mock question set from i18n keys.
 * Each question set's name/desc/questions come from `dashboard.qset.qsN.*`.
 */
function useQuestionSets(): EvaluationQuestionSet[] {
  const { t } = useLanguage();
  return [
    {
      id: 'qs-1',
      name: t('dashboard.qset.qs1.name'),
      description: t('dashboard.qset.qs1.desc'),
      questions: [
        { id: 'q1', question: t('dashboard.qset.qs1.q1'), category: t('dashboard.cat.compliance') },
        { id: 'q2', question: t('dashboard.qset.qs1.q2'), category: t('dashboard.cat.safety') },
        { id: 'q3', question: t('dashboard.qset.qs1.q3'), category: t('dashboard.cat.operation') },
        { id: 'q4', question: t('dashboard.qset.qs1.q4'), category: t('dashboard.cat.efficiency') },
        { id: 'q5', question: t('dashboard.qset.qs1.q5'), category: t('dashboard.cat.compliance') },
        { id: 'q6', question: t('dashboard.qset.qs1.q6'), category: t('dashboard.cat.tech') },
      ],
    },
    {
      id: 'qs-2',
      name: t('dashboard.qset.qs2.name'),
      description: t('dashboard.qset.qs2.desc'),
      questions: [
        { id: 'q1', question: t('dashboard.qset.qs2.q1'), category: t('dashboard.cat.rebooking') },
        { id: 'q2', question: t('dashboard.qset.qs2.q2'), category: t('dashboard.cat.specialService') },
        { id: 'q3', question: t('dashboard.qset.qs2.q3'), category: t('dashboard.cat.baggage') },
        { id: 'q4', question: t('dashboard.qset.qs2.q4'), category: t('dashboard.cat.specialService') },
        { id: 'q5', question: t('dashboard.qset.qs2.q5'), category: t('dashboard.cat.rebooking') },
      ],
    },
    {
      id: 'qs-3',
      name: t('dashboard.qset.qs3.name'),
      description: t('dashboard.qset.qs3.desc'),
      questions: [
        { id: 'q1', question: t('dashboard.qset.qs3.q1'), category: t('dashboard.cat.analysis') },
        { id: 'q2', question: t('dashboard.qset.qs3.q2'), category: t('dashboard.cat.revenue') },
        { id: 'q3', question: t('dashboard.qset.qs3.q3'), category: t('dashboard.cat.operation') },
        { id: 'q4', question: t('dashboard.qset.qs3.q4'), category: t('dashboard.cat.tech') },
      ],
    },
  ];
}

/**
 * Map category labels (locale-dependent text) back to canonical radar-dim keys.
 * Used to aggregate per-quest results into radarScores
 * since the dashboard's radar chart keys are English (accuracy, relevance, etc).
 */
function categoryToRadarKey(t: (k: string) => string, cat: string): string {
  if (cat === t('dashboard.cat.compliance') || cat === t('dashboard.cat.safety')) return 'accuracy';
  if (cat === t('dashboard.cat.operation') || cat === t('dashboard.cat.rebooking')) return 'relevance';
  if (cat === t('dashboard.cat.tech') || cat === t('dashboard.cat.analysis')) return 'completeness';
  if (cat === t('dashboard.cat.safety') || cat === t('dashboard.cat.specialService')) return 'safety';
  if (cat === t('dashboard.cat.efficiency') || cat === t('dashboard.cat.baggage')) return 'efficiency';
  if (cat === t('dashboard.cat.revenue')) return 'creativity';
  return 'accuracy';
}

export default function DashboardView({
  pipelines,
  agents,
  models,
  auditLogs,
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
  const questionSets = useQuestionSets();
  const [selectedAgentId, setSelectedAgentId] = useState<string>('');
  const [selectedQSetId, setSelectedQSetId] = useState<string>('');
  const [session, setSession] = useState<EvaluationSession | null>(null);
  const [isRunning, setIsRunning] = useState(false);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const selectedQSet = questionSets.find((qs) => qs.id === selectedQSetId);
  const selectedAgent = agents.find((a) => a.id === selectedAgentId);

  // ── Start evaluation ──────────────────────────────────────────
  const startEvaluation = useCallback(() => {
    if (!selectedAgentId || !selectedQSetId || !selectedQSet) return;
    const sessionId = `eval-${Date.now()}`;
    const newSession: EvaluationSession = {
      id: sessionId,
      agentId: selectedAgentId,
      questionSetId: selectedQSetId,
      status: 'running',
      progress: 0,
      results: selectedQSet.questions.map((q) => ({
        questionId: q.id,
        question: q.question,
        category: q.category,
        score: 0,
        status: 'pending',
      })),
      radarScores: {},
    };
    setSession(newSession);
    setIsRunning(true);
  }, [selectedAgentId, selectedQSetId, selectedQSet]);

  // ── Simulation timer ──────────────────────────────────────────
  useEffect(() => {
    if (!isRunning || !session) return;

    timerRef.current = setInterval(() => {
      setSession((prev) => {
        if (!prev || prev.status !== 'running') return prev;
        const pendingIdx = prev.results.findIndex((r) => r.status === 'pending');
        if (pendingIdx === -1) {
          // All done — compute radar scores by category
          const categoryScores: Record<string, number[]> = {};
          for (const r of prev.results) {
            if (!categoryScores[r.category]) categoryScores[r.category] = [];
            categoryScores[r.category].push(r.score);
          }
          const radarScores: Record<string, number> = {};
          for (const [cat, scores] of Object.entries(categoryScores)) {
            const radarKey = categoryToRadarKey(t, cat);
            radarScores[radarKey] = (radarScores[radarKey] ?? 0) +
              Math.round(scores.reduce((a, b) => a + b, 0) / scores.length);
          }
          return {
            ...prev,
            status: 'completed',
            progress: 100,
            radarScores: {
              accuracy: radarScores.accuracy ?? 75,
              relevance: radarScores.relevance ?? 70,
              completeness: radarScores.completeness ?? 80,
              safety: radarScores.safety ?? 85,
              efficiency: radarScores.efficiency ?? 65,
              creativity: radarScores.creativity ?? 60,
            },
          };
        }

        const updatedResults = [...prev.results];
        const score = Math.floor(Math.random() * 30) + 60; // 60-90
        const latency = Math.floor(Math.random() * 800) + 200;
        updatedResults[pendingIdx] = {
          ...updatedResults[pendingIdx],
          status: 'completed',
          score,
          response: t('dashboard.eval.responseTemplate', {
            q: updatedResults[pendingIdx].question.slice(0, 15),
            score,
          }),
          latency,
        };

        const completedCount = updatedResults.filter((r) => r.status === 'completed').length;
        const totalCount = updatedResults.length;
        const progress = Math.round((completedCount / totalCount) * 100);

        return { ...prev, results: updatedResults, progress };
      });
    }, 1200);

    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [isRunning, session?.id, t]);

  // ── Reset evaluation ──────────────────────────────────────────
  const resetEvaluation = useCallback(() => {
    if (timerRef.current) clearInterval(timerRef.current);
    setIsRunning(false);
    setSession(null);
  }, []);

  // ── Cleanup on unmount ────────────────────────────────────────
  useEffect(() => {
    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, []);

  // ── Weekday labels via i18n ───────────────────────────────────
  const weekdayLabels = [
    t('dashboard.trend.monday'), t('dashboard.trend.tuesday'), t('dashboard.trend.wednesday'),
    t('dashboard.trend.thursday'), t('dashboard.trend.friday'), t('dashboard.trend.saturday'),
    t('dashboard.trend.sunday'),
  ];

  // KPI cards — defined here so we keep the array declarative (single sub-block)
  const kpiCards = [
    { title: t('dashboard.kpi.pipeline.title'), value: `${activePipelinesCount} / ${pipelines.length}`, sub: t('dashboard.kpi.pipeline.sub'), icon: Cpu, color: 'text-blue-600 bg-blue-50' },
    { title: t('dashboard.kpi.agent.title'), value: `${activeAgentsCount} / ${agents.length}`, sub: t('dashboard.kpi.agent.sub'), icon: Bot, color: 'text-indigo-600 bg-indigo-50' },
    { title: t('dashboard.kpi.model.title'), value: `${models.filter((m) => m.status === 'connected').length} ${t('dashboard.kpi.model.unit')}`, sub: t('dashboard.kpi.model.sub'), icon: Layers, color: 'text-emerald-600 bg-emerald-50' },
    { title: t('dashboard.kpi.guardrail.title'), value: '100.00%', sub: t('dashboard.kpi.guardrail.sub'), icon: ShieldCheck, color: 'text-rose-600 bg-rose-50' },
  ] as const;

  // ── Render: Overview Dashboard ────────────────────────────────
  const renderOverview = () => (
    <div className="space-y-6 overflow-y-auto h-full p-6">
      {/* Welcome Banner */}
      <div className={`${styles.appBg} text-white rounded-xl p-6 shadow-sm relative overflow-hidden`}>
        <div className="absolute top-0 right-0 w-96 h-96 bg-gradient-to-br from-blue-600/20 to-indigo-600/10 rounded-full blur-3xl pointer-events-none" />
        <div className="relative z-10 flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div className="space-y-1.5">
            <div className="flex items-center gap-2 text-blue-400 text-xs font-semibold tracking-wider uppercase">
              <span className="w-2 h-2 rounded-full bg-blue-500 animate-pulse" />
              <span>{t('dashboard.welcome.badge')}</span>
            </div>
            <h2 className="text-xl font-extrabold text-white">{t('dashboard.welcome.title')}</h2>
            <p className={`text-xs ${styles.cardTextMuted} max-w-2xl leading-relaxed`}>
              {t('dashboard.welcome.desc')}
            </p>
          </div>
          <div className="flex gap-2.5 shrink-0">
            <button
              onClick={() => onNavigateToView('agent')}
              className="px-3.5 py-1.5 bg-blue-600 hover:bg-blue-500 text-white font-bold rounded-lg text-xs shadow-sm transition-all cursor-pointer flex items-center gap-1.5"
            >
              <Bot size={13} />
              <span>{t('dashboard.welcome.btnAgent')}</span>
            </button>
            <button
              onClick={() => onNavigateToView('logic')}
              className={`px-3.5 py-1.5 bg-slate-800 hover:bg-slate-700 ${styles.cardText} border ${styles.inputBorder} font-semibold rounded-lg text-xs transition-all cursor-pointer flex items-center gap-1.5`}
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
          <div key={i} className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 flex items-center gap-4 hover:shadow-md transition-shadow`}>
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
              <span className="p-1 rounded-md bg-blue-50 text-blue-600">
                <TrendingUp size={13} />
              </span>
              <h3 className={`text-xs font-bold ${styles.cardText}`}>{t('dashboard.trend.title')}</h3>
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
                    <span className="absolute -top-6 bg-slate-800 text-white text-[9px] font-semibold px-1.5 py-0.5 rounded-sm opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none z-10 whitespace-nowrap">
                      {t('dashboard.trend.calls', { n: val })}
                    </span>
                    <div
                      className="w-full bg-blue-500 hover:bg-blue-600 rounded-t-sm transition-all"
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
              <span className="p-1 rounded-md bg-indigo-50 text-indigo-600">
                <Layers size={13} />
              </span>
              <h3 className={`text-xs font-bold ${styles.cardText}`}>{t('dashboard.model.title')}</h3>
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
            <span className="p-1 rounded bg-rose-50 text-rose-600">
              <Activity size={13} />
            </span>
            <h3 className={`text-xs font-bold ${styles.cardText}`}>{t('dashboard.audit.title')}</h3>
          </div>
          <button
            onClick={() => onNavigateToView('guardrails')}
            className="text-[10px] text-blue-600 hover:text-blue-700 font-bold flex items-center gap-0.5 cursor-pointer"
          >
            <span>{t('dashboard.audit.manage')}</span>
            <ChevronRight size={11} />
          </button>
        </div>
        <div className={`divide-y ${styles.cardBorder} max-h-60 overflow-y-auto`}>
          {auditLogs.map((log) => {
            const statusLabelKey =
              log.status === 'blocked' ? 'dashboard.audit.blocked'
              : log.status === 'flagged' ? 'dashboard.audit.flagged'
              : log.status === 'pending_approval' ? 'dashboard.audit.pending'
              : 'dashboard.audit.passed';
            return (
              <div key={log.id} className={`p-3 hover:bg-slate-900/5 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 text-xs`}>
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
                  {log.status === 'blocked' ? (
                    <span className="px-2 py-0.5 rounded-full bg-red-100 text-red-700 font-bold text-[9px] flex items-center gap-1 border border-red-200">
                      <span className="w-1 h-1 rounded-full bg-red-600" />{t(statusLabelKey)}
                    </span>
                  ) : log.status === 'flagged' ? (
                    <span className="px-2 py-0.5 rounded-full bg-amber-100 text-amber-700 font-bold text-[9px] flex items-center gap-1 border border-amber-200">
                      <span className="w-1 h-1 rounded-full bg-amber-600 animate-pulse" />{t(statusLabelKey)}
                    </span>
                  ) : log.status === 'pending_approval' ? (
                    <span className="px-2 py-0.5 rounded-full bg-indigo-100 text-indigo-700 font-bold text-[9px] flex items-center gap-1 border border-indigo-200">
                      <span className="w-1.5 h-1.5 rounded-full bg-indigo-600 animate-ping" />{t(statusLabelKey)}
                    </span>
                  ) : (
                    <span className="px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-700 font-bold text-[9px] flex items-center gap-1 border border-emerald-200">
                      <span className="w-1 h-1 rounded-full bg-emerald-600" />{t(statusLabelKey)}
                    </span>
                  )}
                  {log.actionTaken && (
                    <span className="text-[9px] text-rose-500 font-mono font-bold">{log.actionTaken}</span>
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
          <div className="p-2 rounded-lg bg-blue-500/10 text-blue-400">
            <BarChart3 size={18} />
          </div>
          <div>
            <h2 className={`text-sm font-extrabold ${styles.cardText}`}>{t('dashboard.eval.title')}</h2>
            <p className={`text-[10px] ${styles.cardTextMuted}`}>{t('dashboard.eval.sub')}</p>
          </div>
        </div>
      </div>

      {/* Selection Panel */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-5`}>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {/* Agent Selector */}
          <div className="space-y-1.5">
            <label className={`text-[10px] font-bold uppercase tracking-wider ${styles.cardTextMuted}`}>
              <Bot size={11} className="inline mr-1" />
              {t('dashboard.eval.agentLabel')}
            </label>
            <div className="relative">
              <select
                value={selectedAgentId}
                onChange={(e) => { setSelectedAgentId(e.target.value); resetEvaluation(); }}
                disabled={isRunning}
                className={`w-full appearance-none px-3 py-2 text-xs rounded-lg border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} font-medium cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed focus:outline-none focus:ring-2 focus:ring-blue-500/30`}
              >
                <option value="">{t('dashboard.eval.agentPlaceholder')}</option>
                {agents.map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.name} {a.status === 'active' ? t('dashboard.eval.agentActive') : t('dashboard.eval.agentDev')}
                  </option>
                ))}
              </select>
              <ChevronDown size={13} className={`absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none ${styles.cardTextMuted}`} />
            </div>
            {selectedAgent && (
              <p className={`text-[10px] ${styles.cardTextMuted} pl-1`}>
                {selectedAgent.role} · {t('dashboard.eval.agentModel')}: {selectedAgent.modelId || t('dashboard.eval.modelUnbound')}
              </p>
            )}
          </div>

          {/* Question Set Selector */}
          <div className="space-y-1.5">
            <label className={`text-[10px] font-bold uppercase tracking-wider ${styles.cardTextMuted}`}>
              <Brain size={11} className="inline mr-1" />
              {t('dashboard.eval.qSetLabel')}
            </label>
            <div className="relative">
              <select
                value={selectedQSetId}
                onChange={(e) => { setSelectedQSetId(e.target.value); resetEvaluation(); }}
                disabled={isRunning}
                className={`w-full appearance-none px-3 py-2 text-xs rounded-lg border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} font-medium cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed focus:outline-none focus:ring-2 focus:ring-blue-500/30`}
              >
                <option value="">{t('dashboard.eval.qSetPlaceholder')}</option>
                {questionSets.map((qs) => (
                  <option key={qs.id} value={qs.id}>
                    {qs.name} {t('dashboard.eval.qSetCount', { count: qs.questions.length })}
                  </option>
                ))}
              </select>
              <ChevronDown size={13} className={`absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none ${styles.cardTextMuted}`} />
            </div>
            {selectedQSet && (
              <p className={`text-[10px] ${styles.cardTextMuted} pl-1`}>{selectedQSet.description}</p>
            )}
          </div>
        </div>

        {/* Action Buttons */}
        <div className="flex items-center gap-2.5 mt-4 pt-4 border-t border-slate-700/50">
          <button
            onClick={startEvaluation}
            disabled={!selectedAgentId || !selectedQSetId || isRunning}
            className="px-4 py-2 bg-blue-600 hover:bg-blue-500 disabled:bg-slate-700 disabled:text-slate-500 text-white font-bold rounded-lg text-xs transition-all cursor-pointer disabled:cursor-not-allowed flex items-center gap-1.5"
          >
            <Play size={13} />
            <span>{t('dashboard.eval.start')}</span>
          </button>
          <button
            onClick={resetEvaluation}
            disabled={!session}
            className="px-4 py-2 bg-slate-700 hover:bg-slate-600 disabled:bg-slate-800 disabled:text-slate-600 text-slate-300 font-semibold rounded-lg text-xs transition-all cursor-pointer disabled:cursor-not-allowed flex items-center gap-1.5"
          >
            <RotateCcw size={13} />
            <span>{t('dashboard.eval.reset')}</span>
          </button>
          {session && (
            <span className={`text-[10px] font-mono ml-auto ${styles.cardTextMuted}`}>
              Session: {session.id}
            </span>
          )}
        </div>
      </div>

      {/* Progress */}
      {session && (
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-5`}>
          <div className="flex items-center justify-between mb-3">
            <div className="flex items-center gap-2">
              <span className={`p-1.5 rounded-md ${session.status === 'completed' ? 'bg-emerald-500/10 text-emerald-400' : 'bg-blue-500/10 text-blue-400'}`}>
                <Zap size={14} />
              </span>
              <span className={`text-xs font-bold ${styles.cardText}`}>
                {session.status === 'idle' && t('dashboard.eval.idle')}
                {session.status === 'running' && t('dashboard.eval.running')}
                {session.status === 'completed' && t('dashboard.eval.completed')}
              </span>
            </div>
            <span className={`text-[10px] font-mono font-bold ${session.status === 'completed' ? 'text-emerald-400' : 'text-blue-400'}`}>
              {session.progress}%
            </span>
          </div>
          <div className="w-full bg-slate-700/50 rounded-full h-2 overflow-hidden">
            <div
              className={`h-full rounded-full transition-all duration-500 ${
                session.status === 'completed' ? 'bg-emerald-500' : 'bg-blue-500'
              }`}
              style={{ width: `${session.progress}%` }}
            />
          </div>
          <div className="flex flex-wrap gap-1.5 mt-3">
            {session.results.map((r, i) => {
              const iconCls = r.status === 'completed' ? 'text-emerald-400' :
                r.status === 'running' ? 'text-blue-400 animate-pulse' :
                r.status === 'failed' ? 'text-red-400' : 'text-slate-600';
              return (
                <span key={r.questionId} className={`text-[9px] font-mono px-2 py-1 rounded border ${
                  r.status === 'completed' ? 'border-emerald-500/30 bg-emerald-500/5 text-emerald-400' :
                  r.status === 'running' ? 'border-blue-500/30 bg-blue-500/5 text-blue-400' :
                  r.status === 'failed' ? 'border-red-500/30 bg-red-500/5 text-red-400' :
                  'border-slate-700 bg-slate-800 text-slate-500'
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
              <span className="p-1.5 rounded-md bg-purple-500/10 text-purple-400">
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
            {/* Legend */}
            <div className="grid grid-cols-3 gap-x-3 gap-y-1.5 mt-3 w-full max-w-xs">
              {RADAR_DIM_LEGEND.map((dim) => (
                <div key={dim.key} className="flex items-center gap-1.5 text-[10px]">
                  <span className="w-2 h-2 rounded-full shrink-0" style={{ backgroundColor: dim.color }} />
                  <span className={styles.cardTextMuted}>{t(`dashboard.radar.${dim.key}`)}</span>
                  <span className="font-bold text-slate-300 ml-auto">{session.radarScores[dim.key] ?? '-'}</span>
                </div>
              ))}
            </div>
          </div>

          {/* Right: Per-Question Details */}
          <div className={`lg:col-span-3 ${styles.cardBg} border ${styles.cardBorder} rounded-xl overflow-hidden`}>
            <div className={`px-4 py-3 border-b ${styles.cardBorder} ${styles.inputBg} flex items-center gap-2`}>
              <span className="p-1 rounded bg-blue-500/10 text-blue-400">
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
            <div className="max-h-[420px] overflow-y-auto divide-y divide-slate-700/50">
              {session.results.map((r, i) => (
                <div key={r.questionId} className="p-4 space-y-2">
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex items-start gap-2.5 min-w-0 flex-1">
                      <span className="mt-0.5 shrink-0">
                        {r.status === 'completed' ? (
                          <CheckCircle2 size={14} className="text-emerald-400" />
                        ) : r.status === 'running' ? (
                          <Clock size={14} className="text-blue-400 animate-pulse" />
                        ) : r.status === 'failed' ? (
                          <XCircle size={14} className="text-red-400" />
                        ) : (
                          <Clock size={14} className="text-slate-600" />
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
                      </div>
                    </div>
                    {r.status === 'completed' && (
                      <div className={`shrink-0 px-2.5 py-1.5 rounded-lg text-center ${
                        r.score >= 80 ? 'bg-emerald-500/10 border border-emerald-500/30' :
                        r.score >= 60 ? 'bg-amber-500/10 border border-amber-500/30' :
                        'bg-red-500/10 border border-red-500/30'
                      }`}>
                        <p className={`text-lg font-black ${
                          r.score >= 80 ? 'text-emerald-400' :
                          r.score >= 60 ? 'text-amber-400' : 'text-red-400'
                        }`}>{r.score}</p>
                        <p className="text-[8px] text-slate-500 font-bold uppercase">{t('dashboard.eval.scoreLabel')}</p>
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
        <button
          onClick={() => setSubView('overview')}
          className={`px-3.5 py-1.5 rounded-lg text-[11px] font-bold transition-all cursor-pointer flex items-center gap-1.5 ${
            subView === 'overview'
              ? 'bg-blue-600 text-white shadow-sm'
              : `${styles.cardTextMuted} hover:${styles.cardText} hover:bg-slate-700/50`
          }`}
        >
          <LayoutDashboard size={13} />
          <span>{t('dashboard.tab.overview')}</span>
        </button>
        <button
          onClick={() => setSubView('evaluation')}
          className={`px-3.5 py-1.5 rounded-lg text-[11px] font-bold transition-all cursor-pointer flex items-center gap-1.5 ${
            subView === 'evaluation'
              ? 'bg-blue-600 text-white shadow-sm'
              : `${styles.cardTextMuted} hover:${styles.cardText} hover:bg-slate-700/50`
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

/** Static radar legend (paired with the 6 radar dims in the canvas). */
const RADAR_DIM_LEGEND: Array<{ key: string; color: string }> = [
  { key: 'accuracy', color: '#3b82f6' },
  { key: 'relevance', color: '#8b5cf6' },
  { key: 'completeness', color: '#10b981' },
  { key: 'safety', color: '#ef4444' },
  { key: 'efficiency', color: '#f59e0b' },
  { key: 'creativity', color: '#ec4899' },
];

