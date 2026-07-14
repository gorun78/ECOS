import React, { useState, useEffect, useCallback } from 'react';
import {
  Brain, BookOpen, Users, Shield, Zap,
  Activity, Settings, RefreshCw, CheckCircle2, XCircle,
  Loader2, AlertTriangle, Play, Square,
} from 'lucide-react';
import { useLanguage } from '../components/LanguageContext';
import { useTheme } from '../components/ThemeContext';
import {
  fetchEngineHealth, fetchEngineStatus, startEngine, stopEngine,
  fetchEngineSettings, fetchEngineSettingsDefaults, updateEngineSettings, refreshEngineSettings,
  compileContext, fetchIndexStatus,
  routeIntent, fetchMeshAgents, fetchMeshMissions,
  validateGuardrails, fetchGuardrailPolicies, createGuardrailPolicy, deleteGuardrailPolicy,
  executeAction, fetchAvailableActions,
} from '../services/cognitiveEngineApi';

const SUB_ENGINES = [
  { key: 'prompt', label: 'PromptCompiler', labelZh: '提示编译器', icon: BookOpen, color: 'text-sky-400', desc: 'Federal RAG + Context', descZh: '联邦RAG+上下文编译' },
  { key: 'mesh', label: 'AgentMesh', labelZh: 'Agent协同', icon: Users, color: 'text-violet-400', desc: 'Multi-Agent Orchestration', descZh: '多Agent协同+意图路由' },
  { key: 'guard', label: 'Guardrails', labelZh: '零信任护栏', icon: Shield, color: 'text-rose-400', desc: 'PII / Hallucination / Compliance', descZh: 'PII/幻觉/合规检测' },
  { key: 'action', label: 'ActionBridge', labelZh: '行动桥接', icon: Zap, color: 'text-amber-400', desc: 'LLM Output → Ontology Action', descZh: 'LLM输出→本体Action→执行' },
] as const;

type SubEngineKey = typeof SUB_ENGINES[number]['key'];

export default function CognitiveEngineView() {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();

  const [toast, setToast] = useState<{ type: 'success' | 'info' | 'error'; msg: string } | null>(null);
  const showToast = useCallback((type: 'success' | 'info' | 'error', msg: string) => {
    setToast({ type, msg });
    setTimeout(() => setToast(null), 3500);
  }, []);

  const [activeTab, setActiveTab] = useState<SubEngineKey>('prompt');

  // Engine-level state
  const [health, setHealth] = useState<Record<string, unknown> | null>(null);
  const [status, setStatus] = useState<Record<string, unknown> | null>(null);
  const [settings, setSettings] = useState<Record<string, unknown> | null>(null);
  const [loading, setLoading] = useState(true);
  const [engineState, setEngineState] = useState<string>('UNKNOWN');

  // Sub-engine state
  const [indexStatus, setIndexStatus] = useState<Record<string, unknown> | null>(null);
  const [agents, setAgents] = useState<Array<Record<string, unknown>>>([]);
  const [missions, setMissions] = useState<Array<Record<string, unknown>>>([]);
  const [policies, setPolicies] = useState<Array<Record<string, unknown>>>([]);
  const [actions, setActions] = useState<Array<Record<string, unknown>>>([]);
  const [subLoading, setSubLoading] = useState(false);

  // Test inputs
  const [compileInput, setCompileInput] = useState('');
  const [intentInput, setIntentInput] = useState('');
  const [validateInput, setValidateInput] = useState('');
  const [actionInput, setActionInput] = useState('');
  const [testResult, setTestResult] = useState<Record<string, unknown> | null>(null);

  const loadEngine = useCallback(async () => {
    setLoading(true);
    try {
      const [h, s, cfg] = await Promise.allSettled([
        fetchEngineHealth(),
        fetchEngineStatus(),
        fetchEngineSettings(),
      ]);
      if (h.status === 'fulfilled') setHealth(h.value as Record<string, unknown>);
      if (s.status === 'fulfilled') {
        setStatus(s.value as Record<string, unknown>);
        const sv = s.value as Record<string, unknown>;
        setEngineState(String(sv?.status ?? sv?.state ?? 'UNKNOWN'));
      }
      if (cfg.status === 'fulfilled') setSettings(cfg.value as Record<string, unknown>);
    } catch (e: unknown) {
      showToast('error', `Engine load failed: ${e instanceof Error ? e.message : String(e)}`);
    } finally {
      setLoading(false);
    }
  }, [showToast]);

  const loadSubEngine = useCallback(async (key: SubEngineKey) => {
    setSubLoading(true);
    setTestResult(null);
    try {
      if (key === 'prompt') {
        const idx = await fetchIndexStatus();
        setIndexStatus(idx as Record<string, unknown>);
      } else if (key === 'mesh') {
        const [a, m] = await Promise.allSettled([fetchMeshAgents(), fetchMeshMissions()]);
        if (a.status === 'fulfilled') setAgents((a.value as Array<Record<string, unknown>>) || []);
        if (m.status === 'fulfilled') setMissions((m.value as Array<Record<string, unknown>>) || []);
      } else if (key === 'guard') {
        const p = await fetchGuardrailPolicies();
        setPolicies((p as Array<Record<string, unknown>>) || []);
      } else if (key === 'action') {
        const a = await fetchAvailableActions();
        setActions((a as Array<Record<string, unknown>>) || []);
      }
    } catch (e: unknown) {
      showToast('error', `${key} load failed: ${e instanceof Error ? e.message : String(e)}`);
    } finally {
      setSubLoading(false);
    }
  }, [showToast]);

  useEffect(() => { loadEngine(); }, [loadEngine]);
  useEffect(() => { loadSubEngine(activeTab); }, [activeTab, loadSubEngine]);

  const handleStartStop = async () => {
    try {
      if (engineState === 'RUNNING' || engineState === 'running') {
        await stopEngine();
        setEngineState('STOPPED');
        showToast('success', locale === 'zh' ? '引擎已停止' : 'Engine stopped');
      } else {
        await startEngine();
        setEngineState('RUNNING');
        showToast('success', locale === 'zh' ? '引擎已启动' : 'Engine started');
      }
    } catch (e: unknown) {
      showToast('error', `${e instanceof Error ? e.message : String(e)}`);
    }
  };

  const isRunning = engineState === 'RUNNING' || engineState === 'running';

  const handleTest = async () => {
    setSubLoading(true);
    try {
      let result: unknown = null;
      if (activeTab === 'prompt' && compileInput) {
        result = await compileContext({ missionId: compileInput, agentIds: [] });
      } else if (activeTab === 'mesh' && intentInput) {
        result = await routeIntent({ query: intentInput });
      } else if (activeTab === 'guard' && validateInput) {
        result = await validateGuardrails({ content: validateInput });
      } else if (activeTab === 'action' && actionInput) {
        result = await executeAction({ llmOutput: actionInput });
      }
      setTestResult(result as Record<string, unknown>);
      showToast('success', locale === 'zh' ? '执行成功' : 'Executed');
    } catch (e: unknown) {
      showToast('error', `${e instanceof Error ? e.message : String(e)}`);
    } finally {
      setSubLoading(false);
    }
  };

  return (
    <div className={`${styles.appBg} min-h-screen p-6`}>
      <div className="max-w-7xl mx-auto space-y-6">

        {/* Header */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Brain className="w-7 h-7 text-amber-400" />
            <div>
              <h1 className={`text-2xl font-bold ${styles.cardText}`}>
                {locale === 'zh' ? '认知引擎控制台' : 'Cognitive Engine Console'}
              </h1>
              <p className={`text-xs ${styles.muted}`}>
                {locale === 'zh' ? '四子引擎：提示编译 → Agent协同 → 零信任护栏 → 行动桥接' : 'PromptCompiler → AgentMesh → Guardrails → ActionBridge'}
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <button onClick={handleStartStop}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium transition-colors border
                ${isRunning ? 'bg-red-900/40 border-red-700 text-red-300 hover:bg-red-800/60' : 'bg-emerald-900/40 border-emerald-700 text-emerald-300 hover:bg-emerald-800/60'}`}>
              {isRunning ? <><Square className="w-3.5 h-3.5" />{locale === 'zh' ? '停止' : 'Stop'}</> : <><Play className="w-3.5 h-3.5" />{locale === 'zh' ? '启动' : 'Start'}</>}
            </button>
            <button onClick={() => { loadEngine(); loadSubEngine(activeTab); }}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 text-sm border border-slate-700">
              <RefreshCw className="w-3.5 h-3.5" />{locale === 'zh' ? '刷新' : 'Refresh'}
            </button>
          </div>
        </div>

        {/* Engine Status Bar */}
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 flex items-center gap-6`}>
          <div className="flex items-center gap-2">
            {isRunning ? <CheckCircle2 className="w-5 h-5 text-emerald-400" /> : <XCircle className="w-5 h-5 text-red-400" />}
            <span className={`font-bold ${isRunning ? 'text-emerald-400' : 'text-red-400'}`}>{engineState}</span>
          </div>
          {loading ? <Loader2 className="w-4 h-4 animate-spin text-slate-400" /> : (
            <>
              {status?.subEngines && <span className={`text-xs ${styles.muted}`}>Sub-engines: {String(status.subEngines)}</span>}
              {status?.agentCount != null && <span className={`text-xs ${styles.muted}`}>Agents: {String(status.agentCount)}</span>}
              {status?.missionCount != null && <span className={`text-xs ${styles.muted}`}>Missions: {String(status.missionCount)}</span>}
              {status?.guardrailPolicyCount != null && <span className={`text-xs ${styles.muted}`}>Policies: {String(status.guardrailPolicyCount)}</span>}
            </>
          )}
        </div>

        {/* Sub-engine Tabs */}
        <div className="flex gap-1 border-b border-slate-700 pb-0">
          {SUB_ENGINES.map(se => {
            const Icon = se.icon;
            const active = activeTab === se.key;
            return (
              <button key={se.key} onClick={() => setActiveTab(se.key)}
                className={`flex items-center gap-2 px-4 py-2 text-sm font-medium border-b-2 transition-colors
                  ${active ? `${se.color} border-current` : `${styles.muted} border-transparent hover:text-slate-300`}`}>
                <Icon className="w-4 h-4" />
                {locale === 'zh' ? se.labelZh : se.label}
              </button>
            );
          })}
        </div>

        {/* Sub-engine Content */}
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-5`}>
          {subLoading && <div className="flex items-center gap-2 text-slate-400 text-sm mb-4"><Loader2 className="w-4 h-4 animate-spin" />{locale === 'zh' ? '加载中...' : 'Loading...'}</div>}

          {/* PromptCompiler */}
          {activeTab === 'prompt' && (
            <div className="space-y-4">
              <h3 className={`text-sm font-semibold ${styles.cardText}`}>
                {locale === 'zh' ? '联邦RAG上下文编译' : 'Federal RAG Context Compiler'}
              </h3>
              {indexStatus && (
                <div className="space-y-1">
                  {Object.entries(indexStatus).map(([k, v]) => (
                    <div key={k} className="flex items-center justify-between text-xs">
                      <span className={`${styles.muted} font-mono`}>{k}</span>
                      <span className={styles.cardText}>{typeof v === 'object' ? JSON.stringify(v) : String(v)}</span>
                    </div>
                  ))}
                </div>
              )}
              <div className="flex gap-2">
                <input value={compileInput} onChange={e => setCompileInput(e.target.value)} placeholder="Mission ID"
                  className={`flex-1 px-3 py-1.5 rounded-lg text-sm ${styles.cardBg} border ${styles.cardBorder} ${styles.cardText} placeholder:text-slate-500`} />
                <button onClick={handleTest} disabled={!compileInput}
                  className="px-3 py-1.5 rounded-lg bg-sky-700 hover:bg-sky-600 text-white text-sm disabled:opacity-40">
                  {locale === 'zh' ? '编译' : 'Compile'}
                </button>
              </div>
            </div>
          )}

          {/* AgentMesh */}
          {activeTab === 'mesh' && (
            <div className="space-y-4">
              <h3 className={`text-sm font-semibold ${styles.cardText}`}>
                {locale === 'zh' ? '多Agent协同 & 意图路由' : 'Multi-Agent Orchestration & Intent Routing'}
              </h3>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <p className={`text-xs ${styles.muted} mb-1`}>{locale === 'zh' ? '已注册Agents' : 'Registered Agents'} ({agents.length})</p>
                  <div className="space-y-1 max-h-32 overflow-y-auto">
                    {agents.map((a, i) => <div key={i} className={`text-xs ${styles.cardText}`}>{String(a.name || a.id || `agent-${i}`)}</div>)}
                    {agents.length === 0 && <p className={`text-xs ${styles.muted}`}>—</p>}
                  </div>
                </div>
                <div>
                  <p className={`text-xs ${styles.muted} mb-1`}>{locale === 'zh' ? '活跃Missions' : 'Active Missions'} ({missions.length})</p>
                  <div className="space-y-1 max-h-32 overflow-y-auto">
                    {missions.map((m, i) => <div key={i} className={`text-xs ${styles.cardText}`}>{String(m.name || m.id || `mission-${i}`)}</div>)}
                    {missions.length === 0 && <p className={`text-xs ${styles.muted}`}>—</p>}
                  </div>
                </div>
              </div>
              <div className="flex gap-2">
                <input value={intentInput} onChange={e => setIntentInput(e.target.value)} placeholder={locale === 'zh' ? '输入意图...' : 'Enter intent...'}
                  className={`flex-1 px-3 py-1.5 rounded-lg text-sm ${styles.cardBg} border ${styles.cardBorder} ${styles.cardText} placeholder:text-slate-500`} />
                <button onClick={handleTest} disabled={!intentInput}
                  className="px-3 py-1.5 rounded-lg bg-violet-700 hover:bg-violet-600 text-white text-sm disabled:opacity-40">
                  {locale === 'zh' ? '路由' : 'Route'}
                </button>
              </div>
            </div>
          )}

          {/* Guardrails */}
          {activeTab === 'guard' && (
            <div className="space-y-4">
              <h3 className={`text-sm font-semibold ${styles.cardText}`}>
                {locale === 'zh' ? '零信任护栏 — PII/幻觉/合规' : 'Zero-Trust Guardrails — PII / Hallucination / Compliance'}
              </h3>
              <div>
                <p className={`text-xs ${styles.muted} mb-1`}>{locale === 'zh' ? '策略列表' : 'Policies'} ({policies.length})</p>
                <div className="space-y-1 max-h-40 overflow-y-auto">
                  {policies.map((p, i) => (
                    <div key={i} className="flex items-center justify-between text-xs">
                      <span className={styles.cardText}>{String(p.name || p.id || `policy-${i}`)}</span>
                      <span className={String(p.isEnabled) === 'true' ? 'text-emerald-400' : 'text-slate-500'}>{String(p.isEnabled ?? '—')}</span>
                    </div>
                  ))}
                  {policies.length === 0 && <p className={`text-xs ${styles.muted}`}>—</p>}
                </div>
              </div>
              <div className="flex gap-2">
                <input value={validateInput} onChange={e => setValidateInput(e.target.value)} placeholder={locale === 'zh' ? '输入检测文本...' : 'Enter text to validate...'}
                  className={`flex-1 px-3 py-1.5 rounded-lg text-sm ${styles.cardBg} border ${styles.cardBorder} ${styles.cardText} placeholder:text-slate-500`} />
                <button onClick={handleTest} disabled={!validateInput}
                  className="px-3 py-1.5 rounded-lg bg-rose-700 hover:bg-rose-600 text-white text-sm disabled:opacity-40">
                  {locale === 'zh' ? '检测' : 'Validate'}
                </button>
              </div>
            </div>
          )}

          {/* ActionBridge */}
          {activeTab === 'action' && (
            <div className="space-y-4">
              <h3 className={`text-sm font-semibold ${styles.cardText}`}>
                {locale === 'zh' ? '行动桥接 — LLM输出→本体Action' : 'ActionBridge — LLM Output → Ontology Action'}
              </h3>
              <div>
                <p className={`text-xs ${styles.muted} mb-1`}>{locale === 'zh' ? '可用Actions' : 'Available Actions'} ({actions.length})</p>
                <div className="space-y-1 max-h-40 overflow-y-auto">
                  {actions.map((a, i) => (
                    <div key={i} className="flex items-center justify-between text-xs">
                      <span className={styles.cardText}>{String(a.actionCode || a.actionName || `action-${i}`)}</span>
                      <span className={`${styles.muted}`}>{String(a.domain ?? '')}</span>
                    </div>
                  ))}
                  {actions.length === 0 && <p className={`text-xs ${styles.muted}`}>—</p>}
                </div>
              </div>
              <div className="flex gap-2">
                <input value={actionInput} onChange={e => setActionInput(e.target.value)} placeholder={locale === 'zh' ? '输入LLM输出文本...' : 'Enter LLM output text...'}
                  className={`flex-1 px-3 py-1.5 rounded-lg text-sm ${styles.cardBg} border ${styles.cardBorder} ${styles.cardText} placeholder:text-slate-500`} />
                <button onClick={handleTest} disabled={!actionInput}
                  className="px-3 py-1.5 rounded-lg bg-amber-700 hover:bg-amber-600 text-white text-sm disabled:opacity-40">
                  {locale === 'zh' ? '执行' : 'Execute'}
                </button>
              </div>
            </div>
          )}

          {/* Test Result */}
          {testResult && (
            <div className="mt-4 border-t border-slate-700 pt-4">
              <h4 className={`text-xs font-semibold ${styles.muted} mb-2`}>{locale === 'zh' ? '执行结果' : 'Result'}</h4>
              <pre className={`text-[11px] font-mono ${styles.cardText} bg-black/20 dark:bg-black/40 rounded-lg p-3 overflow-x-auto max-h-48`}>
                {JSON.stringify(testResult, null, 2)}
              </pre>
            </div>
          )}
        </div>

        {/* Raw Data Panel */}
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-5`}>
          <h3 className={`text-xs font-semibold uppercase tracking-wider ${styles.muted}`}>
            {locale === 'zh' ? '原始响应数据' : 'Raw API Responses'}
          </h3>
          <div className="grid grid-cols-2 gap-4 mt-3">
            <div className="space-y-1">
              <span className="text-[10px] font-mono text-slate-500">/health</span>
              <pre className={`text-[11px] font-mono ${styles.cardText} bg-black/20 dark:bg-black/40 rounded-lg p-2 overflow-x-auto max-h-32`}>
                {health ? JSON.stringify(health, null, 2) : '—'}
              </pre>
            </div>
            <div className="space-y-1">
              <span className="text-[10px] font-mono text-slate-500">/status</span>
              <pre className={`text-[11px] font-mono ${styles.cardText} bg-black/20 dark:bg-black/40 rounded-lg p-2 overflow-x-auto max-h-32`}>
                {status ? JSON.stringify(status, null, 2) : '—'}
              </pre>
            </div>
          </div>
        </div>

        {/* Toast */}
        {toast && (
          <div className="fixed bottom-6 right-6 z-50 animate-fade-in">
            <div className={`px-4 py-2.5 rounded-lg shadow-lg text-sm font-medium flex items-center gap-2
              ${toast.type === 'success' ? 'bg-emerald-600 text-white' : ''}
              ${toast.type === 'error' ? 'bg-red-600 text-white' : ''}
              ${toast.type === 'info' ? 'bg-slate-700 text-slate-100' : ''}`}>
              {toast.type === 'success' && <CheckCircle2 className="w-4 h-4" />}
              {toast.type === 'error' && <AlertTriangle className="w-4 h-4" />}
              {toast.type === 'info' && <Activity className="w-4 h-4" />}
              {toast.msg}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
