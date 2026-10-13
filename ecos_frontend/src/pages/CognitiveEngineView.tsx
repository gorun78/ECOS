import React, { useState, useEffect, useCallback } from 'react';
import {
  Brain, BookOpen, Users, Shield, Zap,
  RefreshCw, CheckCircle2, XCircle, Play, Square,
  Loader2, ChevronDown,
} from 'lucide-react';
import { useLanguage } from '../components/LanguageContext';
import { useTheme } from '../components/ThemeContext';
import { useToast } from '../components/common/Toast';
import ConfirmDialog from '../components/common/ConfirmDialog';
import { isNoAccessError } from '../api';
import {
  fetchEngineHealth, fetchEngineStatus, startEngine, stopEngine,
  fetchEngineSettings,
  compileContext, fetchIndexStatus,
  routeIntent, fetchMeshAgents, fetchMeshMissions,
  validateGuardrails, fetchGuardrailPolicies,
  executeAction, fetchAvailableActions,
} from '../services/cognitiveEngineApi';

const SUB_ENGINES = [
  { key: 'prompt', icon: BookOpen, labelKey: 'cognition.engine.sub.prompt', colorToken: 'infoText' },
  { key: 'mesh', icon: Users, labelKey: 'cognition.engine.sub.mesh', colorToken: 'accentText' },
  { key: 'guard', icon: Shield, labelKey: 'cognition.engine.sub.guard', colorToken: 'dangerText' },
  { key: 'action', icon: Zap, labelKey: 'cognition.engine.sub.action', colorToken: 'warningText' },
] as const;

type SubEngineKey = typeof SUB_ENGINES[number]['key'];

/** 待确认动作：启停与行动桥接执行前必须过确认门 */
type PendingAction =
  | { kind: 'start' }
  | { kind: 'stop' }
  | { kind: 'executeAction'; input: string }
  | null;

export default function CognitiveEngineView() {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const { showToast } = useToast();

  const [activeTab, setActiveTab] = useState<SubEngineKey>('prompt');
  const [pendingAction, setPendingAction] = useState<PendingAction>(null);

  // Engine-level state
  const [health, setHealth] = useState<Record<string, unknown> | null>(null);
  const [status, setStatus] = useState<Record<string, unknown> | null>(null);
  const [, setSettings] = useState<Record<string, unknown> | null>(null);
  const [loading, setLoading] = useState(true);
  const [engineState, setEngineState] = useState<string>('UNKNOWN');
  const [engineOffline, setEngineOffline] = useState(false);
  const [engineNoAccess, setEngineNoAccess] = useState(false);

  // Raw 面板默认折叠
  const [showRaw, setShowRaw] = useState(false);

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
    const [h, s, cfg] = await Promise.allSettled([
      fetchEngineHealth(),
      fetchEngineStatus(),
      fetchEngineSettings(),
    ]);
    // 分类离线/403 态：不再静默吞掉 rejection
    const reasons = [h, s, cfg].filter(r => r.status === 'rejected').map(r => (r as PromiseRejectedResult).reason);
    setEngineOffline(reasons.length > 0 && !reasons.every(r => isNoAccessError(r)));
    setEngineNoAccess(reasons.length > 0 && reasons.every(r => isNoAccessError(r)));

    if (h.status === 'fulfilled') setHealth(h.value as Record<string, unknown>);
    if (s.status === 'fulfilled') {
      setStatus(s.value as Record<string, unknown>);
      const sv = s.value as Record<string, unknown>;
      setEngineState(String(sv?.status ?? sv?.state ?? 'UNKNOWN'));
    } else {
      setEngineState('UNKNOWN');
    }
    if (cfg.status === 'fulfilled') setSettings(cfg.value as Record<string, unknown>);
    setLoading(false);
  }, []);

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
      showToast('error', isNoAccessError(e) ? t('common.noPermission') : `${key} load failed: ${e instanceof Error ? e.message : String(e)}`);
    } finally {
      setSubLoading(false);
    }
  }, [showToast, t]);

  useEffect(() => { loadEngine(); }, [loadEngine]);
  useEffect(() => { loadSubEngine(activeTab); }, [activeTab, loadSubEngine]);

  const runStartStop = async (mode: 'start' | 'stop') => {
    try {
      if (mode === 'stop') {
        await stopEngine();
        setEngineState('STOPPED');
        showToast('success', t('cognition.engine.stopped'));
      } else {
        await startEngine();
        setEngineState('RUNNING');
        showToast('success', t('cognition.engine.started'));
      }
    } catch (e: unknown) {
      showToast('error', isNoAccessError(e) ? t('common.noPermission') : `${e instanceof Error ? e.message : String(e)}`);
    }
  };

  const isRunning = engineState === 'RUNNING' || engineState === 'running';

  /** 实际执行子引擎测试调用（由确认门或直发路径进入） */
  const runTest = useCallback(async (tab: SubEngineKey) => {
    setSubLoading(true);
    try {
      let result: unknown = null;
      if (tab === 'prompt' && compileInput) {
        result = await compileContext({ missionId: compileInput, agentIds: [] });
      } else if (tab === 'mesh' && intentInput) {
        result = await routeIntent({ query: intentInput });
      } else if (tab === 'guard' && validateInput) {
        result = await validateGuardrails({ content: validateInput });
      } else if (tab === 'action' && actionInput) {
        result = await executeAction({ llmOutput: actionInput });
      }
      setTestResult((result as Record<string, unknown>) ?? null);
      // 无假真理：不抛错 ≠ 成功产出，空结果如实呈现
      const isEmpty = result == null
        || (typeof result === 'object' && Object.keys(result as object).length === 0)
        || (Array.isArray(result) && result.length === 0);
      if (isEmpty) {
        showToast('info', t('cognition.engine.emptyResult'));
      } else {
        showToast('success', t('cognition.engine.executed'));
      }
    } catch (e: unknown) {
      showToast('error', isNoAccessError(e) ? t('common.noPermission') : `${e instanceof Error ? e.message : String(e)}`);
    } finally {
      setSubLoading(false);
    }
  }, [compileInput, intentInput, validateInput, actionInput, showToast, t]);

  const handleTest = () => {
    if (activeTab === 'action') {
      // 行动桥接可能触发真实本体动作 → 确认门
      setPendingAction({ kind: 'executeAction', input: actionInput });
      return;
    }
    runTest(activeTab);
  };

  const confirmPending = async () => {
    const pa = pendingAction;
    setPendingAction(null);
    if (!pa) return;
    if (pa.kind === 'start' || pa.kind === 'stop') {
      await runStartStop(pa.kind);
    } else if (pa.kind === 'executeAction') {
      await runTest('action');
    }
  };

  const confirmCopy = pendingAction?.kind === 'stop'
    ? { title: t('cognition.engine.confirm.stop.title'), message: t('cognition.engine.confirm.stop.message'), danger: true }
    : pendingAction?.kind === 'start'
      ? { title: t('cognition.engine.confirm.start.title'), message: t('cognition.engine.confirm.start.message'), danger: false }
      : { title: t('cognition.engine.confirm.action.title'), message: t('cognition.engine.confirm.action.message'), danger: true };

  return (
    <div className={`${styles.appBg} min-h-screen p-6`}>
      <div className="max-w-7xl mx-auto space-y-6">

        {/* Header */}
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Brain className={`w-7 h-7 ${styles.accentText}`} />
            <div>
              <h1 className={`text-2xl font-bold ${styles.cardText}`}>
                {t('cognition.engine.title')}
              </h1>
              <p className={`text-xs ${styles.muted}`}>
                {t('cognition.engine.subtitle')}
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <button type="button" onClick={() => setPendingAction({ kind: isRunning ? 'stop' : 'start' })}
              disabled={engineNoAccess}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium transition-colors border cursor-pointer
                ${isRunning ? `${styles.dangerBg} ${styles.dangerBorder} ${styles.dangerText}` : `${styles.successBg} ${styles.successBorder} ${styles.successText}`} disabled:opacity-40`}>
              {isRunning ? <><Square className="w-3.5 h-3.5" />{t('cognition.engine.stop')}</> : <><Play className="w-3.5 h-3.5" />{t('cognition.engine.start')}</>}
            </button>
            <button type="button" onClick={() => { loadEngine(); loadSubEngine(activeTab); }}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg ${styles.inputBg} ${styles.inputText} text-sm border ${styles.inputBorder} ${styles.sidebarHoverBg} cursor-pointer`}>
              <RefreshCw className="w-3.5 h-3.5" />{t('cognition.engine.refresh')}
            </button>
          </div>
        </div>

        {/* 离线/403 状态横幅 */}
        {engineOffline && (
          <div className={`flex items-center gap-2 border rounded-lg px-3 py-2 text-xs ${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`}>
            <XCircle className="w-4 h-4 shrink-0" />
            <span>{t('cognition.engine.offlineBanner')}</span>
          </div>
        )}
        {engineNoAccess && (
          <div className={`flex items-center gap-2 border rounded-lg px-3 py-2 text-xs ${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`}>
            <Shield className="w-4 h-4 shrink-0" />
            <span>{t('cognition.engine.noAccess')}</span>
          </div>
        )}

        {/* Engine Status Bar */}
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 flex items-center gap-6`}>
          <div className="flex items-center gap-2">
            {isRunning ? <CheckCircle2 className={`w-5 h-5 ${styles.successText}`} /> : <XCircle className={`w-5 h-5 ${styles.dangerText}`} />}
            <span className={`font-bold ${engineOffline || engineNoAccess ? styles.muted : isRunning ? styles.successText : styles.dangerText}`}>
              {engineState === 'UNKNOWN' && (engineOffline || engineNoAccess) ? t('cognition.engine.stateUnknown') : engineState}
            </span>
          </div>
          {loading ? <Loader2 className={`w-4 h-4 animate-spin ${styles.muted}`} /> : (
            <>
              {status?.subEngines != null && <span className={`text-xs ${styles.muted}`}>{t('cognition.engine.stat.subEngines')}: {String(status.subEngines)}</span>}
              {status?.agentCount != null && <span className={`text-xs ${styles.muted}`}>{t('cognition.engine.stat.agents')}: {String(status.agentCount)}</span>}
              {status?.missionCount != null && <span className={`text-xs ${styles.muted}`}>{t('cognition.engine.stat.missions')}: {String(status.missionCount)}</span>}
              {status?.guardrailPolicyCount != null && <span className={`text-xs ${styles.muted}`}>{t('cognition.engine.stat.policies')}: {String(status.guardrailPolicyCount)}</span>}
            </>
          )}
        </div>

        {/* Sub-engine Tabs */}
        <div className={`flex gap-1 border-b ${styles.cardBorder} pb-0`}>
          {SUB_ENGINES.map(se => {
            const Icon = se.icon;
            const active = activeTab === se.key;
            return (
              <button type="button" key={se.key} onClick={() => setActiveTab(se.key)}
                title={t(`${se.labelKey}.desc`)}
                className={`flex items-center gap-2 px-4 py-2 text-sm font-medium border-b-2 transition-colors cursor-pointer
                  ${active ? `${(styles as Record<string, string>)[se.colorToken]} border-current` : `${styles.muted} border-transparent hover:opacity-80`}`}>
                <Icon className="w-4 h-4" />
                {t(`${se.labelKey}.label`)}
              </button>
            );
          })}
        </div>

        {/* Sub-engine Content */}
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-5`}>
          {subLoading && <div className={`flex items-center gap-2 ${styles.muted} text-sm mb-4`}><Loader2 className="w-4 h-4 animate-spin" />{t('cognition.engine.loading')}</div>}

          {/* PromptCompiler */}
          {activeTab === 'prompt' && (
            <div className="space-y-4">
              <h3 className={`text-sm font-semibold ${styles.cardText}`}>
                {t('cognition.engine.promptTitle')}
              </h3>
              <p className={`text-xs ${styles.muted}`}>{t('cognition.engine.sub.prompt.desc')}</p>
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
                <input value={compileInput} onChange={e => setCompileInput(e.target.value)} placeholder={t('cognition.engine.missionPh')}
                  className={`flex-1 px-3 py-1.5 rounded-lg text-sm ${styles.inputBg} border ${styles.inputBorder} ${styles.inputText} ${styles.placeholderText}`} />
                <button type="button" onClick={handleTest} disabled={!compileInput}
                  className={`px-3 py-1.5 rounded-lg ${styles.accentBg} ${styles.accentHover} text-white text-sm disabled:opacity-40 cursor-pointer`}>
                  {t('cognition.engine.promptBtn')}
                </button>
              </div>
            </div>
          )}

          {/* AgentMesh */}
          {activeTab === 'mesh' && (
            <div className="space-y-4">
              <h3 className={`text-sm font-semibold ${styles.cardText}`}>
                {t('cognition.engine.meshTitle')}
              </h3>
              <p className={`text-xs ${styles.muted}`}>{t('cognition.engine.sub.mesh.desc')}</p>
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <p className={`text-xs ${styles.muted} mb-1`}>{t('cognition.engine.agentsLabel')} ({agents.length})</p>
                  <div className="space-y-1 max-h-32 overflow-y-auto">
                    {agents.map((a, i) => <div key={i} className={`text-xs ${styles.cardText}`}>{String(a.name || a.id || `agent-${i}`)}</div>)}
                    {agents.length === 0 && <p className={`text-xs ${styles.muted}`}>—</p>}
                  </div>
                </div>
                <div>
                  <p className={`text-xs ${styles.muted} mb-1`}>{t('cognition.engine.missionsLabel')} ({missions.length})</p>
                  <div className="space-y-1 max-h-32 overflow-y-auto">
                    {missions.map((m, i) => <div key={i} className={`text-xs ${styles.cardText}`}>{String(m.name || m.id || `mission-${i}`)}</div>)}
                    {missions.length === 0 && <p className={`text-xs ${styles.muted}`}>—</p>}
                  </div>
                </div>
              </div>
              <div className="flex gap-2">
                <input value={intentInput} onChange={e => setIntentInput(e.target.value)} placeholder={t('cognition.engine.intentPh')}
                  className={`flex-1 px-3 py-1.5 rounded-lg text-sm ${styles.inputBg} border ${styles.inputBorder} ${styles.inputText} ${styles.placeholderText}`} />
                <button type="button" onClick={handleTest} disabled={!intentInput}
                  className={`px-3 py-1.5 rounded-lg ${styles.accentBg} ${styles.accentHover} text-white text-sm disabled:opacity-40 cursor-pointer`}>
                  {t('cognition.engine.routeBtn')}
                </button>
              </div>
            </div>
          )}

          {/* Guardrails */}
          {activeTab === 'guard' && (
            <div className="space-y-4">
              <h3 className={`text-sm font-semibold ${styles.cardText}`}>
                {t('cognition.engine.guardTitle')}
              </h3>
              <p className={`text-xs ${styles.muted}`}>{t('cognition.engine.sub.guard.desc')}</p>
              <div>
                <p className={`text-xs ${styles.muted} mb-1`}>{t('cognition.engine.policiesLabel')} ({policies.length})</p>
                <div className="space-y-1 max-h-40 overflow-y-auto">
                  {policies.map((p, i) => (
                    <div key={i} className="flex items-center justify-between text-xs">
                      <span className={styles.cardText}>{String(p.name || p.id || `policy-${i}`)}</span>
                      <span className={String(p.isEnabled) === 'true' ? styles.successText : styles.muted}>{String(p.isEnabled ?? '—')}</span>
                    </div>
                  ))}
                  {policies.length === 0 && <p className={`text-xs ${styles.muted}`}>—</p>}
                </div>
              </div>
              <div className="flex gap-2">
                <input value={validateInput} onChange={e => setValidateInput(e.target.value)} placeholder={t('cognition.engine.validatePh')}
                  className={`flex-1 px-3 py-1.5 rounded-lg text-sm ${styles.inputBg} border ${styles.inputBorder} ${styles.inputText} ${styles.placeholderText}`} />
                <button type="button" onClick={handleTest} disabled={!validateInput}
                  className={`px-3 py-1.5 rounded-lg ${styles.accentBg} ${styles.accentHover} text-white text-sm disabled:opacity-40 cursor-pointer`}>
                  {t('cognition.engine.validateBtn')}
                </button>
              </div>
            </div>
          )}

          {/* ActionBridge */}
          {activeTab === 'action' && (
            <div className="space-y-4">
              <h3 className={`text-sm font-semibold ${styles.cardText}`}>
                {t('cognition.engine.actionTitle')}
              </h3>
              <p className={`text-xs ${styles.muted}`}>{t('cognition.engine.sub.action.desc')}</p>
              <div>
                <p className={`text-xs ${styles.muted} mb-1`}>{t('cognition.engine.actionsLabel')} ({actions.length})</p>
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
                <input value={actionInput} onChange={e => setActionInput(e.target.value)} placeholder={t('cognition.engine.actionInputPh')}
                  className={`flex-1 px-3 py-1.5 rounded-lg text-sm ${styles.inputBg} border ${styles.inputBorder} ${styles.inputText} ${styles.placeholderText}`} />
                <button type="button" onClick={handleTest} disabled={!actionInput}
                  className={`px-3 py-1.5 rounded-lg ${styles.warningBg} ${styles.warningText} border ${styles.warningBorder} text-sm font-semibold disabled:opacity-40 cursor-pointer`}>
                  {t('cognition.engine.executeBtn')}
                </button>
              </div>
            </div>
          )}

          {/* Test Result（空结果如实呈现「空结果」） */}
          {testResult !== null && (
            <div className={`mt-4 border-t ${styles.cardBorder} pt-4`}>
              <h4 className={`text-xs font-semibold ${styles.muted} mb-2`}>
                {t('cognition.engine.resultTitle')}
                {Object.keys(testResult).length === 0 && <span className={`ml-2 ${styles.warningText}`}>（{t('cognition.engine.resultEmpty')}）</span>}
              </h4>
              <pre className={`text-[11px] font-mono ${styles.islandBg} ${styles.islandText} rounded-lg p-3 overflow-x-auto max-h-48`}>
                {Object.keys(testResult).length === 0 ? '{}' : JSON.stringify(testResult, null, 2)}
              </pre>
            </div>
          )}
        </div>

        {/* Raw Data Panel（默认折叠） */}
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-5`}>
          <button
            type="button"
            onClick={() => setShowRaw(v => !v)}
            aria-expanded={showRaw}
            className={`w-full flex items-center justify-between cursor-pointer ${styles.muted}`}
          >
            <h3 className={`text-xs font-semibold uppercase tracking-wider`}>
              {t('cognition.engine.rawTitle')}
            </h3>
            <span className="flex items-center gap-1 text-[10px] font-mono">
              <ChevronDown className={`w-3.5 h-3.5 transition-transform ${showRaw ? 'rotate-180' : ''}`} />
              {showRaw ? t('cognition.engine.rawHide') : t('cognition.engine.rawShow')}
            </span>
          </button>
          {showRaw && (
            <div className="grid grid-cols-2 gap-4 mt-3">
              <div className="space-y-1">
                <span className={`text-[10px] font-mono ${styles.muted}`}>/health</span>
                <pre className={`text-[11px] font-mono ${styles.islandBg} ${styles.islandText} rounded-lg p-2 overflow-x-auto max-h-32`}>
                  {health ? JSON.stringify(health, null, 2) : '—'}
                </pre>
              </div>
              <div className="space-y-1">
                <span className={`text-[10px] font-mono ${styles.muted}`}>/status</span>
                <pre className={`text-[11px] font-mono ${styles.islandBg} ${styles.islandText} rounded-lg p-2 overflow-x-auto max-h-32`}>
                  {status ? JSON.stringify(status, null, 2) : '—'}
                </pre>
              </div>
            </div>
          )}
        </div>

        {/* 启停 / 行动桥接执行确认门 */}
        <ConfirmDialog
          visible={pendingAction != null}
          title={confirmCopy.title}
          message={pendingAction?.kind === 'executeAction' && pendingAction.input
            ? `${confirmCopy.message}\n${pendingAction.input}`
            : confirmCopy.message}
          danger={confirmCopy.danger}
          onConfirm={confirmPending}
          onCancel={() => setPendingAction(null)}
        />
      </div>
    </div>
  );
}
