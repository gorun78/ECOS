/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useCallback, useRef, useEffect } from 'react';
import {
  LayoutDashboard, Cpu, Bot, Layers, ShieldCheck, TrendingUp,
  Activity, ChevronRight, BarChart3, Play, RotateCcw, CheckCircle2,
  XCircle, Clock, ChevronDown, Zap, Target, Brain, Eye, Shield, Gauge, Sparkles
} from 'lucide-react';
import {
  AIPLogicPipeline, AIPAgent, AIPModel, AIPAuditLog,
  EvaluationQuestionSet, EvaluationResult, EvaluationSession
} from '../../types/aiworkbench';
import { useTheme } from '../../components/ThemeContext';

interface DashboardViewProps {
  pipelines: AIPLogicPipeline[];
  agents: AIPAgent[];
  models: AIPModel[];
  auditLogs: AIPAuditLog[];
  onNavigateToView: (view: 'logic' | 'agent' | 'model' | 'guardrails') => void;
  showToast?: (type: 'success' | 'info' | 'error', msg: string) => void;
}

// ── Mock Question Sets ──────────────────────────────────────────

const MOCK_QUESTION_SETS: EvaluationQuestionSet[] = [
  {
    id: 'qs-1',
    name: '航空合规知识集',
    description: '航空领域合规、安全、运营知识评估',
    questions: [
      { id: 'q1', question: '航班延误超过4小时，航空公司应承担哪些责任？', category: '合规' },
      { id: 'q2', question: '描述飞机起飞前必须完成的5项安全检查流程。', category: '安全' },
      { id: 'q3', question: '在航空领域中，MEL（最低设备清单）的作用是什么？', category: '运营' },
      { id: 'q4', question: '如何定义和计算航空公司的准点率？', category: '效率' },
      { id: 'q5', question: '航空数据治理中，PII数据脱敏的最佳实践有哪些？', category: '合规' },
      { id: 'q6', question: '描述ADS-B技术在航空监控中的应用与局限性。', category: '技术' },
    ],
  },
  {
    id: 'qs-2',
    name: '客服场景知识集',
    description: '机票退改签、行李、特殊旅客服务等客服场景',
    questions: [
      { id: 'q1', question: '旅客要求非自愿退票，客票状态为已使用部分航段，如何处理？', category: '退改签' },
      { id: 'q2', question: '无成人陪伴儿童（UM）服务的申请条件与流程是什么？', category: '特殊服务' },
      { id: 'q3', question: '行李丢失赔偿标准：国内航线与国际航线的区别？', category: '行李' },
      { id: 'q4', question: '旅客因身体原因需申请轮椅服务，有哪些步骤？', category: '特殊服务' },
      { id: 'q5', question: '航班超售时，旅客权益保障及赔偿流程？', category: '退改签' },
    ],
  },
  {
    id: 'qs-3',
    name: '数据分析知识集',
    description: '航班数据、旅客行为、收入分析等数据分析场景',
    questions: [
      { id: 'q1', question: '如何计算某条航线的客座率及同比变化趋势？', category: '分析' },
      { id: 'q2', question: '解释航空公司常旅客计划的积分价值模型。', category: '收入' },
      { id: 'q3', question: '航班时刻资源分配中需要考虑哪些关键因素？', category: '运营' },
      { id: 'q4', question: '利用机器学习预测航班延误，需收集哪些特征数据？', category: '技术' },
    ],
  },
];

// ── Radar Chart Dimensions ──────────────────────────────────────

const RADAR_DIMS = [
  { key: 'accuracy', label: '准确度', color: '#3b82f6', icon: Target },
  { key: 'relevance', label: '相关性', color: '#8b5cf6', icon: Brain },
  { key: 'completeness', label: '完整性', color: '#10b981', icon: Eye },
  { key: 'safety', label: '安全性', color: '#ef4444', icon: Shield },
  { key: 'efficiency', label: '效率', color: '#f59e0b', icon: Gauge },
  { key: 'creativity', label: '创造性', color: '#ec4899', icon: Sparkles },
];

// ── Radar Chart Canvas Subcomponent ─────────────────────────────

function RadarChart({ scores }: { scores: Record<string, number> }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const size = 280;
  const center = size / 2;
  const radius = 110;
  const levels = 5;

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const dpr = window.devicePixelRatio || 1;
    canvas.width = size * dpr;
    canvas.height = size * dpr;
    canvas.style.width = `${size}px`;
    canvas.style.height = `${size}px`;
    ctx.scale(dpr, dpr);

    ctx.clearRect(0, 0, size, size);

    const count = RADAR_DIMS.length;
    const step = (Math.PI * 2) / count;

    // Draw concentric polygons
    for (let level = 1; level <= levels; level++) {
      ctx.beginPath();
      for (let i = 0; i < count; i++) {
        const angle = step * i - Math.PI / 2;
        const r = (radius / levels) * level;
        const x = center + r * Math.cos(angle);
        const y = center + r * Math.sin(angle);
        if (i === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      }
      ctx.closePath();
      ctx.strokeStyle = 'rgba(148, 163, 184, 0.2)';
      ctx.lineWidth = 1;
      ctx.stroke();
    }

    // Draw axes
    for (let i = 0; i < count; i++) {
      const angle = step * i - Math.PI / 2;
      ctx.beginPath();
      ctx.moveTo(center, center);
      ctx.lineTo(center + radius * Math.cos(angle), center + radius * Math.sin(angle));
      ctx.strokeStyle = 'rgba(148, 163, 184, 0.25)';
      ctx.lineWidth = 1;
      ctx.stroke();
    }

    // Draw data polygon
    ctx.beginPath();
    for (let i = 0; i < count; i++) {
      const dim = RADAR_DIMS[i];
      const val = scores[dim.key] || 0;
      const r = (val / 100) * radius;
      const angle = step * i - Math.PI / 2;
      const x = center + r * Math.cos(angle);
      const y = center + r * Math.sin(angle);
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.closePath();
    ctx.fillStyle = 'rgba(59, 130, 246, 0.15)';
    ctx.fill();
    ctx.strokeStyle = '#3b82f6';
    ctx.lineWidth = 2;
    ctx.stroke();

    // Draw data points
    for (let i = 0; i < count; i++) {
      const dim = RADAR_DIMS[i];
      const val = scores[dim.key] || 0;
      const r = (val / 100) * radius;
      const angle = step * i - Math.PI / 2;
      const x = center + r * Math.cos(angle);
      const y = center + r * Math.sin(angle);
      ctx.beginPath();
      ctx.arc(x, y, 4, 0, Math.PI * 2);
      ctx.fillStyle = dim.color;
      ctx.fill();
    }

    // Draw labels
    ctx.font = 'bold 11px Inter, system-ui, sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    for (let i = 0; i < count; i++) {
      const angle = step * i - Math.PI / 2;
      const labelR = radius + 22;
      const x = center + labelR * Math.cos(angle);
      const y = center + labelR * Math.sin(angle);
      ctx.fillStyle = '#94a3b8';
      ctx.fillText(RADAR_DIMS[i].label, x, y);
    }

    // Center score
    const avg = Math.round(
      RADAR_DIMS.reduce((sum, d) => sum + (scores[d.key] || 0), 0) / count
    );
    ctx.font = 'bold 28px Inter, system-ui, sans-serif';
    ctx.fillStyle = '#e2e8f0';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(`${avg}`, center, center - 6);
    ctx.font = '10px Inter, system-ui, sans-serif';
    ctx.fillStyle = '#64748b';
    ctx.fillText('综合评分', center, center + 18);
  }, [scores, size]);

  return (
    <div className="flex flex-col items-center">
      <canvas ref={canvasRef} className="rounded-lg" />
    </div>
  );
}

// ── Main DashboardView ──────────────────────────────────────────

export default function DashboardView({
  pipelines,
  agents,
  models,
  auditLogs,
  onNavigateToView
}: DashboardViewProps) {
  const { styles } = useTheme();

  // ── Sub-tab state ─────────────────────────────────────────────
  const [subView, setSubView] = useState<'overview' | 'evaluation'>('overview');

  // ── Dashboard metrics ─────────────────────────────────────────
  const activePipelinesCount = pipelines.filter(p => p.status === 'active').length;
  const activeAgentsCount = agents.filter(a => a.status === 'active').length;
  const weeklyUsage = [120, 185, 240, 310, 290, 420, 385];
  const maxUsage = Math.max(...weeklyUsage);

  // ── Evaluation state ──────────────────────────────────────────
  const [selectedAgentId, setSelectedAgentId] = useState<string>('');
  const [selectedQSetId, setSelectedQSetId] = useState<string>('');
  const [session, setSession] = useState<EvaluationSession | null>(null);
  const [isRunning, setIsRunning] = useState(false);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const selectedQSet = MOCK_QUESTION_SETS.find(qs => qs.id === selectedQSetId);
  const selectedAgent = agents.find(a => a.id === selectedAgentId);

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
      results: selectedQSet.questions.map(q => ({
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
      setSession(prev => {
        if (!prev || prev.status !== 'running') return prev;
        const pendingIdx = prev.results.findIndex(r => r.status === 'pending');
        if (pendingIdx === -1) {
          // All done — compute radar scores
          const categoryScores: Record<string, number[]> = {};
          for (const r of prev.results) {
            if (!categoryScores[r.category]) categoryScores[r.category] = [];
            categoryScores[r.category].push(r.score);
          }
          const radarScores: Record<string, number> = {};
          for (const [cat, scores] of Object.entries(categoryScores)) {
            radarScores[cat] = Math.round(scores.reduce((a, b) => a + b, 0) / scores.length);
          }
          // Map categories to radar dim keys
          return {
            ...prev,
            status: 'completed',
            progress: 100,
            radarScores: {
              accuracy: radarScores['合规'] ?? radarScores['安全'] ?? 75,
              relevance: radarScores['运营'] ?? radarScores['退改签'] ?? 70,
              completeness: radarScores['技术'] ?? radarScores['分析'] ?? 80,
              safety: radarScores['安全'] ?? radarScores['特殊服务'] ?? 85,
              efficiency: radarScores['效率'] ?? radarScores['行李'] ?? 65,
              creativity: radarScores['收入'] ?? 60,
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
          response: `模拟回答：关于 "${updatedResults[pendingIdx].question.slice(0, 15)}..." 的评估结果。得分: ${score}/100`,
          latency,
        };

        const completedCount = updatedResults.filter(r => r.status === 'completed').length;
        const totalCount = updatedResults.length;
        const progress = Math.round((completedCount / totalCount) * 100);

        return {
          ...prev,
          results: updatedResults,
          progress,
        };
      });
    }, 1200);

    return () => {
      if (timerRef.current) clearInterval(timerRef.current);
    };
  }, [isRunning, session?.id]);

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
              <span>AIP | ECOS 智能平台</span>
            </div>
            <h2 className="text-xl font-extrabold text-white">欢迎来到 AI工作台 (AI Workbench)</h2>
            <p className={`text-xs ${styles.cardTextMuted} max-w-2xl leading-relaxed`}>
              将大规模语言模型(LLM)无缝绑定于航空核心本体(Aviation Core)。在此配置决策逻辑流(AIP Logic)、部署会话交互智能体(AIP Agents)，并受到企业级私有边界与数据安全护栏(Guardrails)的完全实时审计和守护。
            </p>
          </div>
          <div className="flex gap-2.5 shrink-0">
            <button
              onClick={() => onNavigateToView('agent')}
              className="px-3.5 py-1.5 bg-blue-600 hover:bg-blue-500 text-white font-bold rounded-lg text-xs shadow-sm transition-all cursor-pointer flex items-center gap-1.5"
            >
              <Bot size={13} />
              <span>定制智能体</span>
            </button>
            <button
              onClick={() => onNavigateToView('logic')}
              className={`px-3.5 py-1.5 bg-slate-800 hover:bg-slate-700 ${styles.cardText} border ${styles.inputBorder} font-semibold rounded-lg text-xs transition-all cursor-pointer flex items-center gap-1.5`}
            >
              <Cpu size={13} />
              <span>编排逻辑流</span>
            </button>
          </div>
        </div>
      </div>

      {/* Key Metric Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {([
          {
            title: '激活逻辑编排 (Pipelines)',
            value: `${activePipelinesCount} / ${pipelines.length}`,
            subText: '已绑定为 Ontology 函数',
            icon: Cpu,
            color: 'text-blue-600 bg-blue-50'
          },
          {
            title: '在线智能体 (AIP Agents)',
            value: `${activeAgentsCount} / ${agents.length}`,
            subText: '已授权触发动作规则',
            icon: Bot,
            color: 'text-indigo-600 bg-indigo-50'
          },
          {
            title: '可调配 LLM 资产数',
            value: `${models.filter(m => m.status === 'connected').length} 台`,
            subText: '安全合规托管边界内',
            icon: Layers,
            color: 'text-emerald-600 bg-emerald-50'
          },
          {
            title: '安全护栏拦截率 (PII/Hallu)',
            value: '100.00%',
            subText: '近 24H 自动防御审计 142 次',
            icon: ShieldCheck,
            color: 'text-rose-600 bg-rose-50'
          }
        ] as const).map((m, i) => (
          <div key={i} className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 flex items-center gap-4 hover:shadow-md transition-shadow`}>
            <div className={`p-3 rounded-xl shrink-0 ${m.color}`}>
              <m.icon size={18} />
            </div>
            <div className="space-y-0.5">
              <p className={`text-[10px] ${styles.cardTextMuted} font-bold uppercase tracking-wider`}>{m.title}</p>
              <p className={`text-lg font-bold ${styles.cardText}`}>{m.value}</p>
              <p className={`text-[10px] ${styles.cardTextMuted}`}>{m.subText}</p>
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
              <h3 className={`text-xs font-bold ${styles.cardText}`}>AIP API 运行负载趋势 (24小时批次)</h3>
            </div>
            <span className={`text-[10px] ${styles.cardTextMuted} font-mono`}>2026-07-03 最新汇总</span>
          </div>

          {/* SVG Line Chart / Bar Graph */}
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
                      {val} 次调用
                    </span>
                    <div
                      className="w-full bg-blue-500 hover:bg-blue-600 rounded-t-sm transition-all"
                      style={{ height: `${heightPercent * 0.8}%` }}
                    />
                    <span className={`text-[9px] ${styles.cardTextMuted} font-mono mt-1.5`}>
                      {['周一', '周二', '周三', '周四', '周五', '周六', '周日'][idx]}
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
              <h3 className={`text-xs font-bold ${styles.cardText}`}>大模型(LLM)算力消耗占比</h3>
            </div>
            <span className={`text-[10px] ${styles.cardTextMuted} font-mono`}>按 Token 计算</span>
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
                <p className={`text-[8px] ${styles.cardTextMuted} uppercase font-bold`}>总 Token</p>
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
            <h3 className={`text-xs font-bold ${styles.cardText}`}>AIP 平台运行审计与安全阻断日志</h3>
          </div>
          <button
            onClick={() => onNavigateToView('guardrails')}
            className="text-[10px] text-blue-600 hover:text-blue-700 font-bold flex items-center gap-0.5 cursor-pointer"
          >
            <span>管理安全护栏</span>
            <ChevronRight size={11} />
          </button>
        </div>
        <div className={`divide-y ${styles.cardBorder} max-h-60 overflow-y-auto`}>
          {auditLogs.map((log) => {
            const isBlocked = log.status === 'blocked';
            const isFlagged = log.status === 'flagged';
            const isPending = log.status === 'pending_approval';
            return (
              <div key={log.id} className={`p-3 hover:${styles.appBg} flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 text-xs`}>
                <div className="space-y-1">
                  <div className="flex flex-wrap items-center gap-1.5">
                    <span className={`font-mono text-[10px] ${styles.cardTextMuted} ${styles.appBg} px-1 py-0.5 rounded`}>{log.timestamp}</span>
                    <span className={`font-bold ${styles.cardTextMuted}`}>{log.assetName}</span>
                    <span className={`text-[10px] ${styles.cardTextMuted} font-semibold ${styles.inputBg} px-1.5 py-0.5 border ${styles.cardBorder} rounded`}>{log.source}</span>
                    <span className={`${styles.cardTextMuted} font-medium`}>| 操作人: {log.user}</span>
                  </div>
                  <p className={`${styles.cardTextMuted} text-[11px] leading-relaxed`}>{log.details}</p>
                </div>
                <div className="flex flex-col items-end gap-1.5 shrink-0 self-end sm:self-center">
                  {isBlocked ? (
                    <span className="px-2 py-0.5 rounded-full bg-red-100 text-red-700 font-bold text-[9px] flex items-center gap-1 border border-red-200">
                      <span className="w-1 h-1 rounded-full bg-red-600" />已安全阻断 (Blocked)
                    </span>
                  ) : isFlagged ? (
                    <span className="px-2 py-0.5 rounded-full bg-amber-100 text-amber-700 font-bold text-[9px] flex items-center gap-1 border border-amber-200">
                      <span className="w-1 h-1 rounded-full bg-amber-600 animate-pulse" />待人工校对 (Flagged)
                    </span>
                  ) : isPending ? (
                    <span className="px-2 py-0.5 rounded-full bg-indigo-100 text-indigo-700 font-bold text-[9px] flex items-center gap-1 border border-indigo-200">
                      <span className="w-1.5 h-1.5 rounded-full bg-indigo-600 animate-ping" />待签授权 (Pending)
                    </span>
                  ) : (
                    <span className="px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-700 font-bold text-[9px] flex items-center gap-1 border border-emerald-200">
                      <span className="w-1 h-1 rounded-full bg-emerald-600" />安全放行 (Passed)
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
            <h2 className={`text-sm font-extrabold ${styles.cardText}`}>智能体评估面板</h2>
            <p className={`text-[10px] ${styles.cardTextMuted}`}>选择智能体与问题集，评估回答质量与能力雷达</p>
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
              选择评估智能体
            </label>
            <div className="relative">
              <select
                value={selectedAgentId}
                onChange={e => { setSelectedAgentId(e.target.value); resetEvaluation(); }}
                disabled={isRunning}
                className={`w-full appearance-none px-3 py-2 text-xs rounded-lg border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} font-medium cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed focus:outline-none focus:ring-2 focus:ring-blue-500/30`}
              >
                <option value="">-- 请选择智能体 --</option>
                {agents.map(a => (
                  <option key={a.id} value={a.id}>
                    {a.name} {a.status === 'active' ? '(在线)' : '(开发中)'}
                  </option>
                ))}
              </select>
              <ChevronDown size={13} className={`absolute right-3 top-1/2 -translate-y-1/2 pointer-events-none ${styles.cardTextMuted}`} />
            </div>
            {selectedAgent && (
              <p className={`text-[10px] ${styles.cardTextMuted} pl-1`}>
                {selectedAgent.role} · 模型: {selectedAgent.modelId || '未绑定'}
              </p>
            )}
          </div>

          {/* Question Set Selector */}
          <div className="space-y-1.5">
            <label className={`text-[10px] font-bold uppercase tracking-wider ${styles.cardTextMuted}`}>
              <Brain size={11} className="inline mr-1" />
              选择问题集
            </label>
            <div className="relative">
              <select
                value={selectedQSetId}
                onChange={e => { setSelectedQSetId(e.target.value); resetEvaluation(); }}
                disabled={isRunning}
                className={`w-full appearance-none px-3 py-2 text-xs rounded-lg border ${styles.inputBorder} ${styles.inputBg} ${styles.inputText} font-medium cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed focus:outline-none focus:ring-2 focus:ring-blue-500/30`}
              >
                <option value="">-- 请选择问题集 --</option>
                {MOCK_QUESTION_SETS.map(qs => (
                  <option key={qs.id} value={qs.id}>
                    {qs.name} ({qs.questions.length}题)
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
            <span>开始评估</span>
          </button>
          <button
            onClick={resetEvaluation}
            disabled={!session}
            className="px-4 py-2 bg-slate-700 hover:bg-slate-600 disabled:bg-slate-800 disabled:text-slate-600 text-slate-300 font-semibold rounded-lg text-xs transition-all cursor-pointer disabled:cursor-not-allowed flex items-center gap-1.5"
          >
            <RotateCcw size={13} />
            <span>重置</span>
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
                {session.status === 'idle' && '等待开始'}
                {session.status === 'running' && '评估进行中...'}
                {session.status === 'completed' && '评估完成'}
              </span>
            </div>
            <span className={`text-[10px] font-mono font-bold ${session.status === 'completed' ? 'text-emerald-400' : 'text-blue-400'}`}>
              {session.progress}%
            </span>
          </div>
          {/* Progress Bar */}
          <div className="w-full bg-slate-700/50 rounded-full h-2 overflow-hidden">
            <div
              className={`h-full rounded-full transition-all duration-500 ${
                session.status === 'completed' ? 'bg-emerald-500' : 'bg-blue-500'
              }`}
              style={{ width: `${session.progress}%` }}
            />
          </div>
          {/* Question Progress Pills */}
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
          {/* Left: Radar Chart */}
          <div className={`lg:col-span-2 ${styles.cardBg} border ${styles.cardBorder} rounded-xl p-5 flex flex-col items-center`}>
            <div className="flex items-center gap-2 self-start mb-4">
              <span className="p-1.5 rounded-md bg-purple-500/10 text-purple-400">
                <Target size={14} />
              </span>
              <span className={`text-xs font-bold ${styles.cardText}`}>能力雷达图</span>
            </div>
            {Object.keys(session.radarScores).length > 0 ? (
              <RadarChart scores={session.radarScores} />
            ) : (
              <div className="flex-1 flex items-center justify-center">
                <p className={`text-xs ${styles.cardTextMuted}`}>
                  {session.status === 'completed' ? '正在计算评分...' : '评估完成后展示雷达图'}
                </p>
              </div>
            )}
            {/* Legend */}
            <div className="grid grid-cols-3 gap-x-3 gap-y-1.5 mt-3 w-full max-w-xs">
              {RADAR_DIMS.map(dim => (
                <div key={dim.key} className="flex items-center gap-1.5 text-[10px]">
                  <span className="w-2 h-2 rounded-full shrink-0" style={{ backgroundColor: dim.color }} />
                  <span className={styles.cardTextMuted}>{dim.label}</span>
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
              <h3 className={`text-xs font-bold ${styles.cardText}`}>逐题评估详情</h3>
              <span className={`text-[10px] ${styles.cardTextMuted} ml-auto`}>
                {session.results.filter(r => r.status === 'completed').length}/{session.results.length} 题
              </span>
            </div>
            <div className="max-h-[420px] overflow-y-auto divide-y divide-slate-700/50">
              {session.results.map((r, i) => (
                <div key={r.questionId} className="p-4 space-y-2">
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex items-start gap-2.5 min-w-0 flex-1">
                      {/* Status icon */}
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
                    {/* Score badge */}
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
                        <p className="text-[8px] text-slate-500 font-bold uppercase">分</p>
                      </div>
                    )}
                  </div>
                  {/* Meta row */}
                  <div className="flex items-center gap-3 text-[9px]">
                    <span className={`px-1.5 py-0.5 rounded font-medium ${styles.inputBg} ${styles.cardTextMuted}`}>
                      {r.category}
                    </span>
                    {r.latency !== undefined && (
                      <span className={styles.cardTextMuted}>
                        ⏱ {r.latency}ms
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
      {/* Internal Sub-Tabs */}
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
          <span>总览仪表盘</span>
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
          <span>评估面板</span>
        </button>
      </div>

      {/* Sub-View Content */}
      <div className="flex-1 overflow-hidden">
        {subView === 'overview' ? renderOverview() : renderEvaluation()}
      </div>
    </div>
  );
}
