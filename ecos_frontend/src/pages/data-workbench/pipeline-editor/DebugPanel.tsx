/**
 * DebugPanel — breakpoint + debug-session control panel for the pipeline
 * canvas (Wave 3 lower T3). Reuses the existing Monaco-based
 * `ExpressionEditor` for the condition editor so the rule syntax stays
 * consistent across the workbench.
 * @license Apache-2.0
 */
import React, { Suspense } from 'react';
import {
  Play, SkipForward, Play as PlayMini, Square, RotateCcw,
  CircleDot, Circle, Trash2, Loader2, FunctionSquare,
} from 'lucide-react';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
import type { Breakpoint } from '../pipelineDebugApi';

/** Lazy-load Monaco-based ExpressionEditor so Monaco is only parsed when
 *  a breakpoint list is opened (Wave3 T3). */
const ExpressionEditor = React.lazy(() =>
  import('./ExpressionEditor').then((m) => ({ default: m.default }))
);

export type DebugState = 'idle' | 'running' | 'paused';

interface DebugPanelProps {
  enabled: boolean;
  breakpoints: Breakpoint[];
  state: DebugState;
  currentNodeId: string | null;
  onAddBreakpoint: (nodeId: string) => void;
  onRemoveBreakpoint: (id: string) => void;
  onToggleBreakpoint: (id: string, enabled: boolean) => void;
  onUpdateCondition: (id: string, condition: string) => void;
  onStart: () => void;
  onStepOver: () => void;
  onContinue: () => void;
  onStop: () => void;
  onReset: () => void;
}

const DebugPanel: React.FC<DebugPanelProps> = ({
  enabled,
  breakpoints,
  state,
  currentNodeId,
  onAddBreakpoint,
  onRemoveBreakpoint,
  onToggleBreakpoint,
  onUpdateCondition,
  onStart,
  onStepOver,
  onContinue,
  onStop,
  onReset,
}) => {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const hasBreakpoints = breakpoints.length > 0;
  const running = state === 'running';
  const paused = state === 'paused';

  const canStepOver = paused;
  const canContinue = paused;
  const canStop = running || paused;
  const canReset = hasBreakpoints || running || paused;
  const canStart = running === false && paused === false;

  const addBreakpointForSelected = () => {
    if (currentNodeId) onAddBreakpoint(currentNodeId);
  };

  const row = `border ${styles.cardBorder} px-2 py-1.5 rounded text-[11px] ${styles.cardBg}`;
  const label = `text-[10px] ${styles.cardTextMuted} uppercase tracking-wider`;
  const iconBtn = (active: boolean, title: string) =>
    `flex items-center gap-1 px-2 py-1 text-[11px] rounded transition-colors cursor-pointer ${
      active ? `${styles.accentBg} ${styles.accentText}` : `${styles.sidebarBg} ${styles.cardTextMuted} hover:${styles.infoBg}`
    } disabled:opacity-50 disabled:cursor-not-allowed`;

  return (
    <div className={`flex flex-col h-full ${styles.cardBg} text-xs`}>
      {/* Debug session control bar — one row, the five primary actions */}
      <div className={`border-b ${styles.cardBorder} px-2 py-2 flex items-center gap-1`}>
        <span className={`flex items-center gap-1 text-[11px] font-bold ${styles.cardText} mr-1`}>
          {state === 'idle'
            ? <Circle size={10} className={`${styles.cardTextMuted}`} />
            : state === 'running'
              ? <Loader2 size={10} className={`animate-spin ${styles.infoText}`} />
              : <CircleDot size={10} className={`${styles.dangerText}`} />}
          {t(`dw.pipeline.debug.${state === 'idle' ? 'idle' : 'active'}`)}
        </span>
        <button onClick={onStart} disabled={!canStart}
          className={iconBtn(canStart, t('dw.pipeline.debug.tooltip.start'))}
          title={t('dw.pipeline.debug.tooltip.start')}
        >
          <Play size={12} /> {t('dw.pipeline.debug.start')}
        </button>
        <button onClick={onStepOver} disabled={!canStepOver}
          className={iconBtn(canStepOver, t('dw.pipeline.debug.tooltip.stepOver'))}
          title={t('dw.pipeline.debug.tooltip.stepOver')}
        >
          <SkipForward size={12} /> {t('dw.pipeline.debug.stepOver')}
        </button>
        <button onClick={onContinue} disabled={!canContinue}
          className={iconBtn(canContinue, t('dw.pipeline.debug.tooltip.continue'))}
          title={t('dw.pipeline.debug.tooltip.continue')}
        >
          <PlayMini size={12} /> {t('dw.pipeline.debug.continue')}
        </button>
        <button onClick={onStop} disabled={!canStop}
          className={iconBtn(canStop, t('dw.pipeline.debug.tooltip.stop'))}
          title={t('dw.pipeline.debug.tooltip.stop')}
        >
          <Square size={12} /> {t('dw.pipeline.debug.stop')}
        </button>
        <button onClick={onReset} disabled={!canReset}
          className={`flex items-center gap-1 ml-auto px-2 py-1 text-[11px] rounded transition-colors ${
            canReset ? `${styles.cardTextMuted} hover:${styles.dangerBg} hover:${styles.dangerText}` : 'opacity-50 cursor-not-allowed'
          }`} title={t('dw.pipeline.debug.tooltip.reset')}
        >
          <RotateCcw size={12} /> {t('dw.pipeline.debug.reset')}
        </button>
      </div>

      {/* Breakpoint list */}
      <div className="flex-1 overflow-y-auto">
        <div className={`px-3 py-2 flex items-center justify-between border-b ${styles.cardBorder}`}>
          <span className={`text-[11px] font-bold ${styles.cardText} uppercase tracking-wider`}>
            {t('dw.pipeline.debug.breakpoints.list')}
          </span>
          <span className={`text-[10px] font-mono ${styles.cardTextMuted}`}>{breakpoints.length}</span>
        </div>

        <div className="px-2 pt-2">
          <button onClick={addBreakpointForSelected} disabled={!currentNodeId}
            className={`w-full flex items-center justify-center gap-1 px-2 py-1 text-[11px] rounded transition-colors ${
              currentNodeId
                ? `${styles.dangerBg} ${styles.dangerText} hover:opacity-80 border ${styles.dangerBorder}`
                : `${styles.sidebarBg} ${styles.cardTextMuted} opacity-50 cursor-not-allowed`
            }`}
            title={currentNodeId ? '' : t('dw.pipeline.debug.panel.savePlaceholder')}
          >
            <CircleDot size={12} /> {t('dw.pipeline.debug.breakpoints.add', { label: t('dw.pipeline.debug.badge') })}
          </button>
        </div>

        {breakpoints.length === 0 ? (
          <div className={`px-3 py-3 text-[11px] ${styles.cardTextMuted} italic`}>
            {t('dw.pipeline.debug.breakpoints.empty')}
          </div>
        ) : (
          <ul className={`px-2 py-2 space-y-1.5`}>
            {breakpoints.map((bp) => (
              <li key={bp.id} className={row}>
                <div className={`flex items-center gap-2 ${bp.enabled ? '' : 'opacity-50'}`}>
                  <button onClick={() => onToggleBreakpoint(bp.id, !bp.enabled)}
                    className={iconBtn(bp.enabled, t('dw.pipeline.debug.breakpoints.enabled'))}
                    title={`${bp.enabled ? 'Disable' : 'Enable'} ${t('dw.pipeline.debug.breakpoints.enabled')}`}
                  >
                    {bp.enabled ? (
                      <CircleDot size={11} className={`${styles.dangerText}`} />
                    ) : (
                      <Circle size={11} />
                    )}
                  </button>
                  <div className="flex-1 min-w-0">
                    <div className={`flex items-center gap-1 ${label}`}>
                      <span title={bp.nodeId}>{t('dw.pipeline.debug.breakpoints.node')}</span>
                      <span className={`font-mono text-[10px] ${styles.cardText} truncate`} title={bp.nodeId}>
                        {bp.nodeId.slice(-12)}
                      </span>
                    </div>
                    {bp.condition && (
                      <div className={`mt-0.5 font-mono text-[10px] ${styles.infoText} truncate`}
                        title={bp.condition}>{bp.condition}</div>
                    )}
                  </div>
                  <button onClick={() => onRemoveBreakpoint(bp.id)}
                    className={iconBtn(false, t('dw.pipeline.debug.breakpoints.remove'))}
                    title={t('dw.pipeline.debug.breakpoints.remove')}
                  >
                    <Trash2 size={11} />
                  </button>
                </div>
                {/* Condition editor reuses the existing Monaco-based
                    ExpressionEditor (lazy-loaded so Monaco is only parsed
                    when a breakpoint row is rendered). */}
                <div className="mt-1.5">
                  <Suspense fallback={
                    <div className={`h-8 border ${styles.cardBorder} rounded flex items-center justify-center text-[11px] ${styles.cardTextMuted}`}>
                      <Loader2 size={12} className={`mr-1.5 animate-spin`} />
                      <FunctionSquare size={12} />
                    </div>
                  }>
                    <ExpressionEditor
                      value={bp.condition || ''}
                      onChange={(v) => onUpdateCondition(bp.id, v)}
                      placeholder={t('dw.pipeline.debug.breakpoints.conditionPlaceholder')}
                      className="w-full"
                      disabled={!bp.enabled}
                    />
                  </Suspense>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>

      {/* Footer status */}
      {hasBreakpoints && !canStart && (
        <div className={`border-t ${styles.cardBorder} px-2 py-1.5 text-[10px] ${styles.cardTextMuted} italic`}>
          {t('dw.pipeline.debug.backendPending')}
        </div>
      )}
    </div>
  );
};

export default DebugPanel;