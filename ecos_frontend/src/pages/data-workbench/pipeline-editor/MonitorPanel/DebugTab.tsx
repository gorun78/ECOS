/**
 * DebugTab — 调试控制条 + 断点列表 + 命中记录 (变量树)
 *
 * 数据流：
 *   - 由 PipelineFlowEditor.handleDebugStart 在画布上方 DebugPanel 创建会话
 *   - 本 Tab 监听 `dw-monitor-start-breakpoints` 事件，接到自己的 debugPayload
 *   - 1.5s 轮询 getDebugSession(sessionId) 同步 snapshot 与折叠状态
 *   - 命中记录 + 断点条件由 snapshot 字段直接渲染，无独立断点列表 API
 *
 * @license Apache-2.0
 */
import React, { useCallback, useEffect, useState } from 'react';
import {
  Bug,
  Eraser,
  ListRestart,
  Play,
  Radio,
  SkipForward,
  Square,
} from 'lucide-react';
import { useTheme } from '../../../../components/ThemeContext';
import { useLanguage } from '../../../../components/LanguageContext';
import {
  continueDebugSession,
  deleteDebugSession,
  getDebugSession,
  startDebugSession,
  stepDebugSession,
  stopDebugSession,
} from '../../pipelineDebugApi';
import type { DebugSession } from '../../pipelineDebugApi';
import type { MonitorCollapsedState } from './types';

interface Props {
  /** live session summary (由 MonitorPanel 透传) */
  session?: { sessionId: string; state: string } | null;
  /** 调试会话入参 — 节点+依赖 (MonitorPanel 注入) */
  debugPayload?:
    | {
        definitionId?: string;
        nodes: { nodeId: string; type: string; config?: Record<string, unknown>; dependsOn?: string[] }[];
        breakpoints: { nodeId: string; condition?: string }[];
      }
    | null;
}

/** 从 PipelineFlowEditor 注入的 payload 事件。 */
interface MonitorStartBreakpointsDetail {
  definitionId?: string;
  nodes: { nodeId: string; type: string; config?: Record<string, unknown>; dependsOn?: string[] }[];
  breakpoints: { nodeId: string; condition?: string }[];
}

const SAVE_KEY = 'dw-pipeline-debug-session';

export default function DebugTab({ session, debugPayload }: Props) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [sessionId, setSessionId] = useState<string | null>(
    () => session?.sessionId || null,
  );
  const [snapshot, setSnapshot] = useState<DebugSession | null>(null);

  const emitState = useCallback((s: MonitorCollapsedState) => {
    window.dispatchEvent(new CustomEvent('dw-monitor-state', { detail: s }));
  }, []);

  // 监听事件 — PipelineFlowEditor 启动调试后发出 payload
  useEffect(() => {
    const onBreakpoints = (e: Event) => {
      const detail = (e as CustomEvent<MonitorStartBreakpointsDetail>).detail;
      if (detail) {
        setSessionId(null);
        setSnapshot(null);
        void (async () => {
          const snap = await startDebugSession({
            definitionId: detail.definitionId,
            definition: !detail.definitionId
              ? { name: 'pipeline', nodes: detail.nodes }
              : undefined,
            breakpoints: detail.breakpoints,
          });
          if (snap?.sessionId) {
            setSessionId(snap.sessionId);
            setSnapshot(snap);
          }
        })();
      }
    };
    window.addEventListener('dw-monitor-start-breakpoints', onBreakpoints);
    return () => {
      window.removeEventListener('dw-monitor-start-breakpoints', onBreakpoints);
    };
  }, []);

  // ── On initial mount / session change: refresh snapshot ──
  useEffect(() => {
    if (!sessionId) {
      setSnapshot(null);
      return;
    }
    let cancelled = false;
    const tick = async () => {
      try {
        const snap = await getDebugSession(sessionId);
        if (!cancelled && snap) {
          setSnapshot(snap);
          const startedMs = snap.startedAt ? new Date(snap.startedAt).getTime() : 0;
          const endedMs = snap.finishedAt ? new Date(snap.finishedAt).getTime() : Date.now();
          const elapsed = Math.max(0, endedMs - (startedMs || 0));
          const st =
            snap.state === 'running'
              ? 'running'
              : snap.state === 'broken'
                ? 'broken'
                : snap.state === 'completed'
                  ? 'success'
                  : snap.state === 'failed'
                    ? 'failed'
                    : snap.state === 'stopped'
                      ? 'stopped'
                      : snap.state === 'created'
                        ? 'created'
                        : 'idle';
          emitState({
            nodeId: snap.completedNodes,
            total: snap.totalNodes,
            rows: snap.rowsProcessed,
            elapsedMs: elapsed,
            status: st as MonitorCollapsedState['status'],
          });
        }
      } catch {
        // ignore
      }
    };
    tick();
    const timer = window.setInterval(tick, 1500);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [sessionId, emitState]);

  // ── Persist session id to localStorage ──
  useEffect(() => {
    try {
      if (sessionId) localStorage.setItem(SAVE_KEY, sessionId);
      else localStorage.removeItem(SAVE_KEY);
    } catch {
      // ignore
    }
  }, [sessionId]);

  // ── Hydrate session from localStorage on mount ──
  useEffect(() => {
    if (session?.sessionId) return;
    try {
      const saved = localStorage.getItem(SAVE_KEY);
      if (saved) setSessionId(saved);
    } catch {
      // ignore
    }
  }, [session?.sessionId]);

  // ── Control bar handlers ──
  const state =
    snapshot?.state ||
    (session?.state ? (session.state as DebugSession['state']) : 'created');
  const isTerminal = state === 'completed' || state === 'failed' || state === 'stopped';
  const isBroken = state === 'broken';
  const isBusy = state === 'running' || state === 'created';

  const handleContinue = useCallback(async () => {
    if (!sessionId) return;
    try {
      const snap = await continueDebugSession(sessionId);
      setSnapshot(snap || null);
    } catch {
      // ignore
    }
  }, [sessionId]);

  const handleStep = useCallback(async () => {
    if (!sessionId) return;
    try {
      const snap = await stepDebugSession(sessionId);
      setSnapshot(snap || null);
    } catch {
      // ignore
    }
  }, [sessionId]);

  const handleStop = useCallback(async () => {
    if (!sessionId) return;
    try {
      await stopDebugSession(sessionId);
    } catch {
      // ignore
    }
  }, [sessionId]);

  const handleReset = useCallback(async () => {
    if (!sessionId) return;
    try {
      await deleteDebugSession(sessionId);
    } catch {
      // ignore
    }
    setSessionId(null);
    setSnapshot(null);
  }, [sessionId]);

  const canStep = isBroken || state === 'paused';
  const canContinue = isBroken || state === 'paused' || state === 'created';

  return (
    <div className="h-full flex flex-col gap-1.5 pr-1">
      {/* Control bar */}
      <div
        className={`flex items-center gap-2 px-2 py-1.5 border ${styles.cardBorder} ${styles.sidebarBg}`}
      >
        <button
          onClick={handleContinue}
          disabled={!canContinue}
          className={`flex items-center gap-1 px-3 py-1 rounded text-xs font-semibold ${
            canContinue
              ? `${styles.successBg} ${styles.successText}`
              : `${styles.sidebarBg} ${styles.cardTextMuted} cursor-not-allowed`
          }`}
          title={t('dw.pipeline.debug.continue')}
        >
          <SkipForward size={14} />
          {t('dw.monitor.debug.continue')}
        </button>
        <button
          onClick={handleStep}
          disabled={!canStep}
          className={`flex items-center gap-1 px-3 py-1 rounded text-xs font-semibold ${
            canStep
              ? `${styles.infoBg} ${styles.infoText}`
              : `${styles.sidebarBg} ${styles.cardTextMuted} cursor-not-allowed`
          }`}
          title={t('dw.pipeline.debug.stepOver')}
        >
          <SkipForward size={14} className="rotate-180" />
          {t('dw.monitor.debug.step')}
        </button>
        <button
          onClick={handleStop}
          disabled={isTerminal || !sessionId}
          className={`flex items-center gap-1 px-3 py-1 rounded text-xs font-semibold ${
            isTerminal || !sessionId
              ? `${styles.sidebarBg} ${styles.cardTextMuted} cursor-not-allowed`
              : `${styles.dangerBg} ${styles.dangerText}`
          }`}
          title={t('dw.pipeline.debug.stop')}
        >
          <Square size={14} />
          {t('dw.monitor.debug.stop')}
        </button>
        <button
          onClick={handleReset}
          disabled={isTerminal && !sessionId}
          className={`flex items-center gap-1 px-3 py-1 rounded text-xs ${
            isTerminal && !sessionId
              ? `${styles.sidebarBg} ${styles.cardTextMuted} cursor-not-allowed`
              : `${styles.cardTextMuted} ${styles.cardText}`
          }`}
          title={t('dw.pipeline.debug.reset')}
        >
          <ListRestart size={14} />
          {t('dw.monitor.debug.reset')}
        </button>
        <span className="ml-auto flex items-center gap-1.5">
          <Play size={12} className={state === 'running' ? `${styles.accentText} animate-pulse` : styles.cardTextMuted} />
          <Radio
            size={12}
            className={state === 'running' ? `${styles.accentText} animate-pulse` : styles.cardTextMuted}
          />
          <span className={`text-[10px] font-mono ${styles.cardTextMuted}`}>{sessionId || '—'}</span>
          <span className={`text-[9px] ${isBusy ? styles.warningText : styles.cardTextMuted}`}>
            {state}
          </span>
        </span>
      </div>

      {/* Status line */}
      {snapshot && (
        <div className={`flex items-center gap-2 text-[10px] ${styles.cardTextMuted}`}>
          {snapshot.error && (
            <span className={`px-1.5 py-0.5 rounded ${styles.dangerBorder} ${styles.dangerText}`}>
              {snapshot.error}
            </span>
          )}
          {snapshot.hitRecords?.length > 0 && (
            <span className={`text-[10px] ${styles.warningText}`}>
              {t('dw.monitor.debug.hits', { n: snapshot.hitRecords.length })}
            </span>
          )}
        </div>
      )}

      {/* Breakpoints list */}
      <div className="flex-1 overflow-y-auto px-1">
        {!snapshot || snapshot.breakpoints.length === 0 ? (
          <div
            className={`flex items-center justify-center h-full text-[10px] ${styles.cardTextMuted}`}
          >
            <Bug size={11} className="inline mr-1" />
            {t('dw.monitor.debug.noBp')}
          </div>
        ) : (
          <div className="flex flex-col gap-1">
            {snapshot.breakpoints.map((bp) => (
              <BreakpointItem key={bp.nodeId} nodeId={bp.nodeId} condition={bp.condition} />
            ))}
          </div>
        )}
      </div>

      {/* Hit records (variable tree) */}
      {snapshot && snapshot.hitRecords.length > 0 && (
        <div
          className="shrink-0 border-t pt-1 max-h-32 overflow-y-auto"
          style={{ borderTop: `1px solid ${styles.sidebarBorder}` }}
        >
          <div className={`text-[10px] font-semibold ${styles.cardTextMuted} mb-1`}>
            {t('dw.monitor.debug.hitRecords')}
          </div>
          <div className="flex flex-col gap-1">
            {snapshot.hitRecords.slice(0, 5).map((h, i) => (
              <div
                key={`${h.nodeId}-${i}`}
                className={`flex items-start gap-1 text-[10px] font-mono ${styles.sidebarBg} px-1 py-0.5 rounded`}
              >
                <span className={styles.warningText}>●</span>
                <div className="flex-1">
                  <div>
                    <span className={styles.cardText}>{h.nodeId}</span>
                    {h.condition && (
                      <span className={`ml-1 ${styles.cardTextMuted}`}>[{h.condition}]</span>
                    )}
                  </div>
                  {h.snapshot && <VariableTree vars={h.snapshot} depth={0} />}
                </div>
                <span className={`text-[9px] ${styles.cardTextMuted}`}>
                  {new Date(h.at).toLocaleTimeString()}
                </span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

// ─────────────────────────────────────────────────────────────
// Sub-components

const BreakpointItem: React.FC<{ nodeId: string; condition?: string }> = ({
  nodeId,
  condition,
}) => {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [cond, setCond] = useState<string>(condition ?? '');

  // Sync when backend state updates
  useEffect(() => {
    setCond(condition ?? '');
  }, [condition]);

  return (
    <div className={`flex items-center gap-2 py-1 border-b ${styles.cardBorder}`}>
      <span
        className={`inline-block w-1.5 h-1.5 rounded-full bg-red-500`}
        title={t('dw.pipeline.debug.badge')}
      />
      <span className={`text-[10px] font-mono ${styles.cardTextMuted}`}>
        {nodeId.slice(0, 16)}…
      </span>
      <input
        value={cond}
        onChange={(e) => setCond(e.target.value)}
        placeholder={t('dw.pipeline.debug.breakpoints.conditionPlaceholder')}
        className={`flex-1 px-1.5 py-0.5 text-[10px] font-mono rounded ${styles.cardBg} ${styles.cardBorder} ${styles.cardText}`}
      />
      <span className={`text-[9px] ${styles.cardTextMuted} shrink-0`}>{cond || '—'}</span>
      <Eraser size={11} className={`ml-auto ${styles.cardTextMuted}`} />
    </div>
  );
};

const VariableTree: React.FC<{
  vars: Record<string, unknown>;
  depth: number;
}> = ({ vars, depth }) => {
  const { styles } = useTheme();
  const [open, setOpen] = useState<boolean>(depth === 0);
  if (!open) {
    return (
      <span
        className={`inline-block w-3 text-center ${styles.cardTextMuted} cursor-pointer`}
        onClick={() => setOpen(true)}
      >
        {'>'}
      </span>
    );
  }
  const entries = Object.entries(vars).slice(0, 8);
  return (
    <div className={`pl-2 ${styles.cardTextMuted}`}>
      <div
        className={`inline-block w-3 text-center cursor-pointer`}
        onClick={() => setOpen(false)}
      >
        {'v'}
      </div>
      {entries.map(([k, v]) => (
        <div key={k} className="flex items-start gap-1">
          <span
            className="font-semibold shrink-0 max-w-[120px] truncate"
            style={{ color: styles.cardText }}
          >
            {k}
          </span>
          <span className="text-[9px]">
            {typeof v === 'object' && v !== null ? (
              <VariableTree vars={v as Record<string, unknown>} depth={depth + 1} />
            ) : (
              <span>{String(v ?? 'null')}</span>
            )}
          </span>
        </div>
      ))}
    </div>
  );
};
