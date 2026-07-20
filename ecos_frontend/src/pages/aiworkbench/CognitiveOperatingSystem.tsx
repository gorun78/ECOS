import React, { useState, useEffect } from 'react';
import { Play, CheckCircle, XCircle, Clock, Loader2, RotateCcw, Zap, ChevronRight, RefreshCw } from 'lucide-react';
import { useLanguage } from '../../components/LanguageContext';

const STAGES = [
  { id: 'DIAGNOSIS', labelZh: '诊断', label: 'Diagnosis', agent: 'agent-data', color: '#6366f1', descZh: '分析系统现状，识别数据质量与架构瓶颈', descEn: 'Analyze system state, identify data quality & architecture bottlenecks' },
  { id: 'ONTOLOGY_EVOLVE', labelZh: '本体进化', label: 'Ontology Evolve', agent: 'agent-ontology', color: '#8b5cf6', descZh: '基于诊断结果进化本体模型与语义层', descEn: 'Evolve ontology model & semantic layer based on diagnosis' },
  { id: 'KNOWLEDGE_REBUILD', labelZh: '知识重建', label: 'Knowledge Rebuild', agent: 'agent-knowledge', color: '#3b82f6', descZh: '重建知识图谱、索引与RAG管道', descEn: 'Rebuild knowledge graph, index & RAG pipeline' },
  { id: 'SECURITY_HEAL', labelZh: '安全修复', label: 'Security Heal', agent: 'agent-security', color: '#10b981', descZh: '自动修复权限缺口与合规违规', descEn: 'Auto-heal permission gaps & compliance violations' },
  { id: 'DEPLOYMENT', labelZh: '部署', label: 'Deployment', agent: 'agent-scenario', color: '#f59e0b', descZh: '验证变更并部署至生产环境', descEn: 'Validate changes & deploy to production' },
];

const RING_RADIUS = 100;
const RING_CENTER = 130;
const STAGE_ANGLES = STAGES.map((_, i) => (i * 2 * Math.PI / STAGES.length) - Math.PI / 2);

export default function CognitiveOperatingSystem() {
  const { locale } = useLanguage();
  const [missionId, setMissionId] = useState<string | null>(null);
  const [logEntries, setLogEntries] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [recentMissions, setRecentMissions] = useState<string[]>([]);
  const [hoveredStage, setHoveredStage] = useState<string | null>(null);

  const tl = (zh: string, en: string) => locale === 'zh' ? zh : en;

  const triggerEvolution = async () => {
    setLoading(true);
    try {
      const res = await fetch('/api/v1/evolution/trigger', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ trigger: 'MANUAL', context: {} })
      });
      if (res.ok) {
        const wrapped = await res.json();
        const data = wrapped.data || wrapped;
        setMissionId(data.missionId);
        setRecentMissions(prev => [data.missionId, ...prev].slice(0, 10));
        fetchLog(data.missionId);
      }
    } catch { /* ignore */ }
    setLoading(false);
  };

  const fetchLog = async (mid: string) => {
    try {
      const res = await fetch(`/api/v1/evolution/log/${mid}`);
      if (res.ok) {
        const wrapped = await res.json();
        setLogEntries(wrapped.data || wrapped);
      }
    } catch { /* ignore */ }
  };

  useEffect(() => {
    if (missionId) {
      const interval = setInterval(() => fetchLog(missionId), 3000);
      return () => clearInterval(interval);
    }
  }, [missionId]);

  const stageStatus = (stageId: string): string => {
    const entry = logEntries.find((e: any) => e.stage === stageId);
    return entry ? (entry.status || 'STARTED') : 'PENDING';
  };

  const getStageIcon = (status: string) => {
    switch (status) {
      case 'COMPLETED': return <CheckCircle className="w-5 h-5 text-emerald-500" />;
      case 'FAILED': return <XCircle className="w-5 h-5 text-red-500" />;
      case 'STARTED': return <Loader2 className="w-5 h-5 text-blue-500 animate-spin" />;
      default: return <Clock className="w-5 h-5 text-slate-400" />;
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'COMPLETED': return '#10b981';
      case 'FAILED': return '#ef4444';
      case 'STARTED': return '#3b82f6';
      default: return '#475569';
    }
  };

  const activeStageIdx = STAGES.findIndex(s => stageStatus(s.id) === 'STARTED');
  const completedCount = STAGES.filter(s => stageStatus(s.id) === 'COMPLETED').length;
  const allComplete = completedCount === STAGES.length;
  const hasFailure = STAGES.some(s => stageStatus(s.id) === 'FAILED');

  return (
    <div className="flex flex-col h-full p-4 space-y-4 overflow-hidden">
      <div className="flex items-center justify-between shrink-0">
        <div className="flex items-center gap-2">
          <div className="p-1.5 rounded-lg bg-gradient-to-br from-indigo-600 to-purple-600 text-white">
            <Zap className="w-4 h-4" />
          </div>
          <div>
            <h2 className="text-sm font-bold">{tl('自治进化引擎', 'Autonomous Evolution Engine')}</h2>
            <div className="text-[10px] text-[var(--muted-foreground)]">
              {allComplete ? tl('进化周期已完成', 'Evolution cycle completed') :
               hasFailure ? tl('进化周期异常中断', 'Evolution cycle interrupted') :
               missionId ? tl(`阶段 ${completedCount + 1}/${STAGES.length} 执行中`, `Stage ${completedCount + 1}/${STAGES.length} in progress`) :
               tl('就绪 — 等待触发', 'Ready — awaiting trigger')}
            </div>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {missionId && (
            <button
              onClick={() => { setMissionId(null); setLogEntries([]); }}
              className="flex items-center gap-1 px-2 py-1 text-[10px] rounded border border-[var(--border)] hover:bg-[var(--muted)]"
            >
              <RotateCcw className="w-3 h-3" />
              {tl('重置', 'Reset')}
            </button>
          )}
          <button
            onClick={triggerEvolution}
            disabled={loading}
            className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-bold rounded bg-gradient-to-r from-indigo-600 to-purple-600 text-white hover:opacity-90 disabled:opacity-50 shadow-lg shadow-indigo-500/20"
          >
            {loading ? <Loader2 className="w-3 h-3 animate-spin" /> : <Play className="w-3 h-3" />}
            {tl('触发进化', 'Trigger Evolution')}
          </button>
        </div>
      </div>

      <div className="flex gap-4 flex-1 min-h-0 overflow-hidden">
        <div className="flex-1 flex items-center justify-center bg-[var(--card)] rounded-xl border border-[var(--border)] relative overflow-hidden">
          <svg width={RING_CENTER * 2} height={RING_CENTER * 2} viewBox={`0 0 ${RING_CENTER * 2} ${RING_CENTER * 2}`}>
            <defs>
              <filter id="glow">
                <feGaussianBlur stdDeviation="3" result="coloredBlur"/>
                <feMerge>
                  <feMergeNode in="coloredBlur"/>
                  <feMergeNode in="SourceGraphic"/>
                </feMerge>
              </filter>
            </defs>

            <circle cx={RING_CENTER} cy={RING_CENTER} r={RING_RADIUS} fill="none" stroke="var(--border)" strokeWidth="1" strokeDasharray="4 4" />

            {STAGE_ANGLES.map((angle, i) => {
              const nextAngle = STAGE_ANGLES[(i + 1) % STAGES.length];
              const x1 = RING_CENTER + RING_RADIUS * Math.cos(angle);
              const y1 = RING_CENTER + RING_RADIUS * Math.sin(angle);
              const x2 = RING_CENTER + RING_RADIUS * Math.cos(nextAngle);
              const y2 = RING_CENTER + RING_RADIUS * Math.sin(nextAngle);
              const status = stageStatus(STAGES[i].id);
              const nextStatus = stageStatus(STAGES[(i + 1) % STAGES.length].id);
              const isActive = status === 'STARTED' || nextStatus !== 'PENDING';
              return (
                <line key={`line-${i}`} x1={x1} y1={y1} x2={x2} y2={y2}
                  stroke={isActive ? getStatusColor(status) : '#334155'}
                  strokeWidth={2} opacity={isActive ? 0.6 : 0.3}
                />
              );
            })}

            {STAGES.map((stage, i) => {
              const angle = STAGE_ANGLES[i];
              const x = RING_CENTER + RING_RADIUS * Math.cos(angle);
              const y = RING_CENTER + RING_RADIUS * Math.sin(angle);
              const status = stageStatus(stage.id);
              const isHovered = hoveredStage === stage.id;
              const isActive = status !== 'PENDING';
              const r = isHovered ? 22 : 18;
              return (
                <g key={stage.id}
                  onMouseEnter={() => setHoveredStage(stage.id)}
                  onMouseLeave={() => setHoveredStage(null)}
                  className="cursor-pointer"
                >
                  <circle cx={x} cy={y} r={r}
                    fill={isActive ? stage.color + '20' : '#0f172a'}
                    stroke={isActive ? stage.color : '#334155'}
                    strokeWidth={isActive ? 2.5 : 1.5}
                    filter={status === 'STARTED' ? 'url(#glow)' : undefined}
                  />
                  {status === 'STARTED' && (
                    <circle cx={x} cy={y} r={r + 4} fill="none" stroke={stage.color} strokeWidth="1" opacity="0.4">
                      <animate attributeName="r" from={r} to={r + 8} dur="1.5s" repeatCount="indefinite" />
                      <animate attributeName="opacity" from="0.4" to="0" dur="1.5s" repeatCount="indefinite" />
                    </circle>
                  )}
                  <text x={x} y={y + 4} textAnchor="middle" fill="white" fontSize="10" fontWeight="bold">
                    {i + 1}
                  </text>
                </g>
              );
            })}

            <text x={RING_CENTER} y={RING_CENTER - 8} textAnchor="middle" fill="white" fontSize="20" fontWeight="black">
              {completedCount}/{STAGES.length}
            </text>
            <text x={RING_CENTER} y={RING_CENTER + 10} textAnchor="middle" fill="#94a3b8" fontSize="9">
              {tl('阶段完成', 'Stages Done')}
            </text>
          </svg>

          {hoveredStage && (() => {
            const stage = STAGES.find(s => s.id === hoveredStage)!;
            const status = stageStatus(stage.id);
            return (
              <div className="absolute bottom-3 left-3 right-3 bg-slate-950/90 border border-slate-800 rounded-lg p-3 text-xs">
                <div className="flex items-center gap-2 mb-1">
                  <div className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: stage.color }} />
                  <span className="font-bold">{tl(stage.labelZh, stage.label)}</span>
                  <span className={`ml-auto px-1.5 py-0.5 rounded text-[10px] font-bold ${
                    status === 'COMPLETED' ? 'bg-emerald-900/50 text-emerald-400' :
                    status === 'FAILED' ? 'bg-red-900/50 text-red-400' :
                    status === 'STARTED' ? 'bg-blue-900/50 text-blue-400' :
                    'bg-slate-800 text-slate-400'
                  }`}>{status}</span>
                </div>
                <div className="text-[var(--muted-foreground)] text-[10px]">{tl(stage.descZh, stage.descEn)}</div>
                <div className="text-[var(--muted-foreground)] text-[10px] mt-0.5">Agent: {stage.agent}</div>
              </div>
            );
          })()}
        </div>

        <div className="w-72 flex flex-col bg-[var(--card)] rounded-xl border border-[var(--border)] overflow-hidden shrink-0">
          <div className="px-3 py-2 border-b border-[var(--border)] flex items-center justify-between shrink-0">
            <div className="text-[10px] font-bold uppercase tracking-wider text-[var(--muted-foreground)]">
              {tl('进化日志', 'Evolution Log')}
            </div>
            {missionId && (
              <button onClick={() => fetchLog(missionId)} className="p-1 hover:bg-[var(--muted)] rounded">
                <RefreshCw className="w-3 h-3" />
              </button>
            )}
          </div>

          <div className="flex-1 overflow-y-auto p-2 space-y-1">
            {missionId && (
              <div className="text-[10px] text-[var(--muted-foreground)] px-1 pb-1">
                {tl('当前任务', 'Mission')}: <span className="font-mono">{missionId.slice(0, 16)}</span>
              </div>
            )}
            {logEntries.length === 0 && (
              <div className="text-xs text-[var(--muted-foreground)] py-8 text-center">
                {tl('暂无进化记录', 'No evolution records')}
              </div>
            )}
            {logEntries.map((entry: any, i: number) => {
              const stage = STAGES.find(s => s.id === entry.stage);
              return (
                <div key={i} className={`flex items-start gap-2 p-2 rounded text-xs ${
                  entry.status === 'COMPLETED' ? 'bg-emerald-500/5' :
                  entry.status === 'FAILED' ? 'bg-red-500/5' :
                  'bg-blue-500/5'
                }`}>
                  <div className="mt-0.5">{getStageIcon(entry.status)}</div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-1">
                      <span className="font-medium">{stage ? tl(stage.labelZh, stage.label) : entry.stage}</span>
                    </div>
                    <div className="text-[10px] text-[var(--muted-foreground)] font-mono">{entry.agent_id}</div>
                    {entry.completed_at && (
                      <div className="text-[10px] text-[var(--muted-foreground)] mt-0.5">{entry.completed_at}</div>
                    )}
                  </div>
                </div>
              );
            })}
          </div>

          {recentMissions.length > 1 && (
            <div className="border-t border-[var(--border)] p-2 space-y-1 shrink-0 max-h-32 overflow-y-auto">
              <div className="text-[10px] font-bold uppercase tracking-wider text-[var(--muted-foreground)]">
                {tl('历史任务', 'Recent Missions')}
              </div>
              {recentMissions.filter(m => m !== missionId).slice(0, 5).map(mid => (
                <button key={mid} onClick={() => { setMissionId(mid); fetchLog(mid); }}
                  className="w-full text-left text-[10px] px-2 py-1 rounded hover:bg-[var(--muted)] font-mono truncate flex items-center gap-1">
                  <ChevronRight className="w-2 h-2" />
                  {mid.slice(0, 20)}
                </button>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
