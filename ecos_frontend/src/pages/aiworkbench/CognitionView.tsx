/**
 * PMO-60 v2.0 P2b: AI Workbench — 7th tab (cognition). Scene-cognition 4-endpoint execution hub.
 *
 * <p>This is the AI-workbench-side entry of the cognitive 4-endpoints (diagnose / forecast /
 * simulate / policy). It shares the same workspace REST surface as the project-workbench
 * scene-detail Tab ({@code src/pages/scenario/CognitionPanel.tsx}) but renders independently
 * inside the AI workbench. Data sources:
 * <ul>
 *   <li>{@code GET /api/v1/workspace/scenarios}</li>
 *   <li>{@code GET /api/v1/workspace/scenarios/{id}/minds}</li>
 *   <li>{@code GET /api/v1/workspace/scenarios/{id}/cognitive/{ep}?mind=}</li>
 * </ul></p>
 *
 * <p>Structure (top to bottom):
 * <ol>
 *   <li>Top nav — scenario selector (active + draft only) + hub summary badge</li>
 *   <li>Middle — 4 cognitive cards in parallel (each has a Run button and a JSON result pane)</li>
 *   <li>Bottom — mind switch chips (active mark + selected id propagated to query param)</li>
 * </ol></p>
 *
 * <p>Key constraint: P2b only scaffolds the UI. The P3b phase fills in the real cognitive
 * endpoints. On fetch failure (404/503) we show a red InlineWarning — no throw, matching the
 * aiworkbench error-state pattern (cloud + retry).</p>
 */
import React, { useCallback, useEffect, useState } from 'react';
import {
  AlertTriangle,
  BrainCircuit,
  Compass,
  GitCompare,
  Loader2,
  Microscope,
  RefreshCw,
  TrendingUp,
  Workflow,
} from 'lucide-react';
import { apiFetchData } from '../../api';
import { useTheme } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';

/** Business scenario shape (GET /api/v1/workspace/scenarios) */
interface WorkspaceScenario {
  id: string;
  name: string;
  description?: string;
  priority?: string;
  status?: string;
  [key: string]: unknown;
}

/** Scenario mind (GET /api/v1/workspace/scenarios/{id}/minds) */
interface ScenarioMind {
  id: number;
  mindLabel: string;
  activeMind?: boolean;
  initialConfidence?: number;
}

/** 4 cognitive endpoints */
type CognitiveEp = 'diagnose' | 'forecast' | 'simulate' | 'policy';

/** Per-endpoint result state machine: idle → running → done | errorNotEnabled | error */
type EpStatus = 'idle' | 'running' | 'done' | 'errorNotEnabled' | 'error';

interface EpState {
  status: EpStatus;
  data: unknown;
  errorMsg?: string;
}

/** 4-endpoint metadata: ep key / lucide icon / i18n keys */
const EP_CARDS: Array<{
  ep: CognitiveEp;
  icon: React.ComponentType<{ size?: number | string; className?: string }>;
  labelKey: string;
  descKey: string;
}> = [
  { ep: 'diagnose', icon: Microscope, labelKey: 'cognition.ep.diagnose', descKey: 'cognition.epDesc.diagnose' },
  { ep: 'forecast', icon: TrendingUp, labelKey: 'cognition.ep.forecast', descKey: 'cognition.epDesc.forecast' },
  { ep: 'simulate', icon: GitCompare, labelKey: 'cognition.ep.simulate', descKey: 'cognition.epDesc.simulate' },
  { ep: 'policy', icon: Compass, labelKey: 'cognition.ep.policy', descKey: 'cognition.epDesc.policy' },
];

/** Initial state — 4 endpoints independent, no cross-contamination */
const INITIAL_EP_STATE: Record<CognitiveEp, EpState> = {
  diagnose: { status: 'idle', data: null },
  forecast: { status: 'idle', data: null },
  simulate: { status: 'idle', data: null },
  policy: { status: 'idle', data: null },
};

/** Selected scenario: drives right-side hub summary + cognitive requests */
interface SelectedScenario {
  id: string;
  name: string;
  priority?: string;
  status?: string;
  description?: string;
}

export default function CognitionView() {
  const { styles } = useTheme();
  const { t } = useLanguage();

  // Scenario list (active + draft only)
  const [scenarios, setScenarios] = useState<WorkspaceScenario[]>([]);
  const [scenarioLoading, setScenarioLoading] = useState(false);
  const [scenarioError, setScenarioError] = useState<string | null>(null);

  // Currently selected scenario (null = none; cards show placeholder)
  const [selectedScenarioId, setSelectedScenarioId] = useState<string>('');
  const [selectedScenario, setSelectedScenario] = useState<SelectedScenario | null>(null);

  // Minds list per scenario
  const [minds, setMinds] = useState<ScenarioMind[]>([]);
  const [mindsLoading, setMindsLoading] = useState(false);
  const [mindsError, setMindsError] = useState<string | null>(null);

  // Currently selected mind (null = use backend default active, no explicit mind param)
  const [selectedMind, setSelectedMind] = useState<number | null>(null);

  // 4-endpoint execution states
  const [epStates, setEpStates] = useState<Record<CognitiveEp, EpState>>(INITIAL_EP_STATE);

  // ── Scenario list load (mount + manual retry) ──
  const fetchScenarios = useCallback(async () => {
    setScenarioLoading(true);
    setScenarioError(null);
    try {
      const list = await apiFetchData<WorkspaceScenario[]>('/api/v1/workspace/scenarios');
      const visible = Array.isArray(list)
        ? list.filter((s) => s && (s.status === 'ACTIVE' || s.status === 'DRAFT'))
        : [];
      setScenarios(visible);
      // Auto-select first scenario if none currently selected
      setSelectedScenarioId((prev) => (prev && visible.some((s) => s.id === prev) ? prev : visible[0]?.id ?? ''));
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : String(e);
      setScenarioError(msg);
      setScenarios([]);
    } finally {
      setScenarioLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchScenarios();
  }, [fetchScenarios]);

  // Keep selectedScenario in sync when the list or id changes
  useEffect(() => {
    if (!selectedScenarioId) {
      setSelectedScenario(null);
      return;
    }
    const s = scenarios.find((x) => x.id === selectedScenarioId);
    if (s) {
      setSelectedScenario({ id: s.id, name: s.name, priority: s.priority, status: s.status, description: s.description });
    } else {
      setSelectedScenario(null);
    }
  }, [selectedScenarioId, scenarios]);

  // ── Minds list load (re-fetch when scenario changes) ──
  const fetchMinds = useCallback(async (scenarioId: string) => {
    if (!scenarioId) {
      setMinds([]);
      return;
    }
    setMindsLoading(true);
    setMindsError(null);
    try {
      const list = await apiFetchData<ScenarioMind[]>(`/api/v1/workspace/scenarios/${scenarioId}/minds`);
      const arr = Array.isArray(list) ? list : [];
      setMinds(arr);
      // Default to first active mind; null if no active (don't pass mind param)
      const activeMind = arr.find((m) => m.activeMind);
      setSelectedMind(activeMind ? activeMind.id : null);
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : String(e);
      setMindsError(msg);
      setMinds([]);
      setSelectedMind(null);
    } finally {
      setMindsLoading(false);
    }
  }, []);

  // Reset 4-endpoint state on scenario switch (no cross-scenario result leakage)
  useEffect(() => {
    if (!selectedScenarioId) {
      setMinds([]);
      setSelectedMind(null);
      return;
    }
    setEpStates(INITIAL_EP_STATE);
    fetchMinds(selectedScenarioId);
  }, [selectedScenarioId, fetchMinds]);

  // ── 4-endpoint execution ─────────────────────────────────────
  const runEp = useCallback(
    async (ep: CognitiveEp) => {
      if (!selectedScenarioId) return;
      setEpStates((prev) => ({ ...prev, [ep]: { status: 'running', data: null, errorMsg: undefined } }));
      const qs = selectedMind != null ? `?mind=${encodeURIComponent(String(selectedMind))}` : '';
      const url = `/api/v1/workspace/scenarios/${selectedScenarioId}/cognitive/${ep}${qs}`;
      try {
        const data = await apiFetchData<unknown>(url);
        setEpStates((prev) => ({ ...prev, [ep]: { status: 'done', data } }));
      } catch (e: unknown) {
        // P3b endpoints not delivered yet (404 / 503 most likely); graceful, no throw
        const msg = e instanceof Error ? e.message : String(e);
        const isNotEnabled =
          /HTTP (404|503)/i.test(msg) ||
          /Not Found|Service Unavailable/i.test(msg) ||
          /\b(404|503)\b/.test(msg);
        setEpStates((prev) => ({
          ...prev,
          [ep]: { status: isNotEnabled ? 'errorNotEnabled' : 'error', data: null, errorMsg: msg },
        }));
      }
    },
    [selectedScenarioId, selectedMind],
  );

  // ── Render helpers ────────────────────────────────────
  const handleRetryScenario = () => {
    setScenarioError(null);
    fetchScenarios();
  };

  const handleRetryMinds = () => {
    setMindsError(null);
    if (selectedScenarioId) {
      fetchMinds(selectedScenarioId);
    }
  };

  const renderScenarioSelector = () => (
    <div className="flex flex-wrap items-center gap-3">
      <div className="flex items-center gap-2">
        <span className={`text-[10px] font-bold uppercase tracking-wider ${styles.muted}`}>
          {t('cognition.selector.label')}
        </span>
        <select
          value={selectedScenarioId}
          onChange={(e) => setSelectedScenarioId(e.target.value)}
          disabled={scenarioLoading || scenarios.length === 0}
          className={`px-2.5 py-1.5 text-[11px] ${styles.inputBg} border ${styles.inputBorder} rounded-md ${styles.inputText} outline-none focus:border-indigo-500 transition-colors cursor-pointer disabled:opacity-50 min-w-[200px]`}
          aria-label={t('cognition.selector.placeholder')}
        >
          {scenarioLoading && <option value="">{t('cognition.selector.loading')}</option>}
          {!scenarioLoading && scenarios.length === 0 && <option value="">{t('cognition.selector.empty')}</option>}
          {!scenarioLoading &&
            scenarios.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name} ({s.status})
              </option>
            ))}
        </select>
      </div>
      {selectedScenario && (
        <div className={`flex items-center gap-2 px-2.5 py-1 rounded-md ${styles.badgeBg}`}>
          <span className="flex items-center gap-1.5 text-[10px] font-bold">
            <BrainCircuit size={11} className={styles.accentText} />
            <span className={styles.cardText}>{selectedScenario.name}</span>
          </span>
          {selectedScenario.priority && (
            <span className={`text-[9px] font-mono font-bold px-1.5 py-0.5 rounded ${styles.badgeBg} ${styles.muted}`}>
              {selectedScenario.priority}
            </span>
          )}
          {selectedScenario.status && (
            <span
              className={`text-[9px] font-mono font-bold px-1.5 py-0.5 rounded ${
                selectedScenario.status === 'ACTIVE'
                  ? `${styles.successBg} ${styles.successText}`
                  : `${styles.badgeBg} ${styles.muted}`
              }`}
            >
              {selectedScenario.status}
            </span>
          )}
        </div>
      )}
    </div>
  );

  const renderInlineWarning = (message: string, onRetry?: () => void) => (
    <div
      role="alert"
      className={`flex items-start gap-2 p-3 rounded-md border ${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder} text-[11px]`}
    >
      <AlertTriangle size={13} className="shrink-0 mt-0.5" />
      <div className="flex-1 min-w-0">
        <p className="font-bold leading-tight">{t('cognition.notEnabled')}</p>
        {message && <p className="mt-1 font-mono text-[10px] opacity-80 break-all">{message}</p>}
        {onRetry && (
          <button
            type="button"
            onClick={onRetry}
            className={`mt-2 flex items-center gap-1 text-[10px] font-bold px-2 py-1 rounded border ${styles.dangerBorder} cursor-pointer hover:opacity-80 transition-opacity`}
          >
            <RefreshCw size={11} />
            {t('network.retry')}
          </button>
        )}
      </div>
    </div>
  );

  const renderPendingState = () => (
    <div className={`h-full flex flex-col items-center justify-center gap-3 ${styles.muted} text-xs`}>
      <Loader2 size={22} className="animate-spin opacity-60" />
      <p className="opacity-70">{t('cognition.pending')}</p>
    </div>
  );

  const renderIdleState = () => (
    <div className={`h-full flex items-center justify-center ${styles.muted} text-[10px] font-mono uppercase tracking-wider`}>
      {t('cognition.resultPendingShort')}
    </div>
  );

  const renderResult = (ep: CognitiveEp) => {
    const state = epStates[ep];
    if (state.status === 'idle') return renderIdleState();
    if (state.status === 'running') return renderPendingState();
    if (state.status === 'errorNotEnabled') return renderInlineWarning(state.errorMsg ?? '');
    if (state.status === 'error') return renderInlineWarning(state.errorMsg ?? '');
    // done: JSON pretty
    let pretty: string;
    try {
      pretty = JSON.stringify(state.data, null, 2);
    } catch {
      pretty = String(state.data ?? '');
    }
    return (
      <pre
        className={`p-3 text-[10px] font-mono ${styles.inputBg} ${styles.inputText} rounded-md max-h-48 overflow-y-auto whitespace-pre-wrap break-words leading-relaxed`}
      >
        {pretty || t('cognition.noData')}
      </pre>
    );
  };

  const renderEpCard = (ep: CognitiveEp) => {
    const meta = EP_CARDS.find((c) => c.ep === ep);
    if (!meta) return null;
    const IconComp = meta.icon;
    const state = epStates[ep];
    const disabled = !selectedScenarioId || state.status === 'running';
    return (
      <div key={ep} className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4 flex flex-col gap-3 min-h-[220px]`}>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className={`p-1.5 rounded-md ${styles.accentBg} ${styles.accentText}`}>
              <IconComp size={13} />
            </span>
            <div className="flex flex-col min-w-0">
              <span className={`text-xs font-bold ${styles.cardText}`}>{t(meta.labelKey)}</span>
              <span className={`text-[10px] ${styles.muted} truncate max-w-[180px]`}>{t(meta.descKey)}</span>
            </div>
          </div>
          <button
            type="button"
            onClick={() => runEp(ep)}
            disabled={disabled}
            className={`flex items-center gap-1.5 text-[10px] font-bold px-2.5 py-1 rounded border transition-colors cursor-pointer disabled:cursor-not-allowed disabled:opacity-40 ${
              state.status === 'running'
                ? `${styles.badgeBg} ${styles.muted}`
                : `${styles.accentBg} ${styles.accentText} hover:opacity-80`
            }`}
          >
            {state.status === 'running' ? (
              <>
                <Loader2 size={11} className="animate-spin" />
                {t('cognition.running')}
              </>
            ) : (
              <>
                <Workflow size={11} />
                {t('cognition.run')}
              </>
            )}
          </button>
        </div>
        {/* Result pane */}
        <div className="flex-1 rounded-md border overflow-hidden flex flex-col" style={{ borderColor: 'transparent' }}>
          <span className={`px-2.5 py-1 text-[9px] font-bold uppercase tracking-wider border-b ${styles.cardBorder} ${styles.muted}`}>
            {t('cognition.result')}
          </span>
          <div className="flex-1 overflow-auto">{renderResult(ep)}</div>
        </div>
      </div>
    );
  };

  const renderMindChips = () => {
    if (!selectedScenarioId) {
      return (
        <p className={`text-[11px] ${styles.muted}`}>{t('cognition.mind.noScenario')}</p>
      );
    }
    if (mindsLoading) {
      return (
        <div className="flex items-center gap-2">
          <Loader2 size={12} className={`animate-spin ${styles.muted}`} />
          <span className={`text-[11px] ${styles.muted}`}>{t('cognition.mind.loading')}</span>
        </div>
      );
    }
    if (mindsError) {
      return renderInlineWarning(t('cognition.mind.loadFailed'), selectedScenarioId ? handleRetryMinds : undefined);
    }
    if (minds.length === 0) {
      return <p className={`text-[11px] ${styles.muted}`}>{t('cognition.mind.empty')}</p>;
    }
    return (
      <div className="flex flex-wrap gap-2">
        {minds.map((m) => {
          const active = m.activeMind;
          const selected = selectedMind === m.id;
          return (
            <button
              key={m.id}
              type="button"
              disabled={!active}
              onClick={() => setSelectedMind(m.id)}
              className={`flex items-center gap-1.5 text-[10px] font-bold px-2.5 py-1 rounded border transition-colors cursor-pointer disabled:cursor-not-allowed disabled:opacity-50 ${
                selected
                  ? `${styles.accentBg} ${styles.accentText}`
                  : `${styles.inputBg} ${styles.inputBorder} ${styles.cardTextMuted} hover:border-indigo-500/50`
              }`}
              title={active ? t('cognition.mind.switch.active') : t('cognition.mind.switch.inactive')}
            >
              <span className={`h-1.5 w-1.5 rounded-full ${active ? styles.successText : styles.muted}`} />
              <span>{m.mindLabel}</span>
              {active && (
                <span className={`text-[8px] font-mono px-1 py-0.5 rounded ${styles.badgeBg} ${styles.muted}`}>
                  {t('cognition.mind.active')}
                </span>
              )}
            </button>
          );
        })}
      </div>
    );
  };

  return (
    <div className={`h-full w-full overflow-y-auto p-6 ${styles.appBg} ${styles.appText} font-sans`}>
      <div className="max-w-7xl mx-auto space-y-6">
        {/* ── Top nav: scenario selector + hub summary ── */}
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4 space-y-3`}>
          <div className="flex items-center gap-2">
            <span className={`p-1 rounded ${styles.accentBg} ${styles.accentText}`}>
              <BrainCircuit size={13} />
            </span>
            <span className="font-bold text-sm">{t('aiworkbench.viewTitle.cognition')}</span>
          </div>
          <p className={`text-xs leading-normal ${styles.cardTextMuted}`}>{t('cognition.hub.desc')}</p>

          {scenarioError ? (
            renderInlineWarning(t('cognition.selector.loadFailed', { message: scenarioError }), handleRetryScenario)
          ) : (
            renderScenarioSelector()
          )}
        </div>

        {/* ── Middle: 4 cognitive cards in parallel ── */}
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4">
          {EP_CARDS.map((c) => renderEpCard(c.ep))}
        </div>

        {/* ── Bottom: mind switch chips ── */}
        <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4 space-y-3`}>
          <div className="flex items-center gap-2">
            <span className="text-[10px] font-bold uppercase tracking-wider">{t('cognition.mind.label')}</span>
            <span className={`text-[10px] font-mono px-1.5 py-0.5 rounded ${styles.badgeBg} ${styles.muted}`}>
              {minds.filter((m) => m.activeMind).length}/{minds.length}
            </span>
          </div>
          {renderMindChips()}
        </div>
      </div>
    </div>
  );
}
