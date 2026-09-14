/**
 * OverviewTab — 水平节点链时间线 + KPI 条
 *
 * - 水平时间线: 每个拓扑序节点一枚小圆点 + 标签 + 耗时 + 行数
 * - 顶部 KPI: 已完成/总数 / 已处理行数 / 总耗时
 * - 节点点击 → 触发画布高亮+居中 (CustomEvent 'dw-monitor-navigate', detail={nodeId})
 *
 * @license Apache-2.0
 */
import React, { useEffect, useState } from 'react';
import { Check, Database, FileText, Globe, HardDrive, Loader2, Radio, Settings, X } from 'lucide-react';
import { useTheme } from '../../../../components/ThemeContext';
import { useLanguage } from '../../../../components/LanguageContext';
import { getDebugSession } from '../../pipelineDebugApi';
import type { NodeStepSnapshot } from '../../pipelineDebugApi';

const ICONS: Record<string, React.ComponentType<{ size?: number; className?: string }>> = {
  SOURCE_JDBC: Database,
  SOURCE_CSV: FileText,
  SOURCE_REST: Globe,
  SOURCE_CDC: Radio,
  TRANSFORM_SQL: Settings,
  OUTPUT_OBJECT: HardDrive,
};

interface Props {
  /** 可选 live session id (缺失则显示占位) */
  session?: { sessionId: string; state: string; totalNodes: number } | null;
}

export default function OverviewTab({ session }: Props) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [steps, setSteps] = useState<NodeStepSnapshot[]>([]);
  const [loading, setLoading] = useState<boolean>(false);
  const [paused, setPaused] = useState<boolean>(false);
  const [elapsedMs, setElapsedMs] = useState<number>(0);

  const sessionId = session?.sessionId;
  const totalNodes = session?.totalNodes ?? steps.length;
  const rowsTotal = steps.reduce((acc, s) => acc + (s?.rowsProcessed || 0), 0);
  const failedSteps = steps.filter((s) => s?.status === 'FAILED').length;
  const landedSteps = steps.filter((s) => s && (s.status === 'SUCCEEDED' || s.status === 'FAILED')).length;

  // ── Poll session snapshot every 1.5s (sync with backend state machine) ──
  useEffect(() => {
    if (!sessionId || paused) return;
    let cancelled = false;
    const timer = window.setInterval(async () => {
      try {
        const snap = await getDebugSession(sessionId);
        if (cancelled || !snap) return;
        setSteps(snap.steps || []);
        if (snap.startedAt) {
          const start = new Date(snap.startedAt).getTime();
          const end = snap.finishedAt ? new Date(snap.finishedAt).getTime() : Date.now();
          setElapsedMs(Math.max(0, (end || 0) - start));
        }
      } catch {
        // silently ignore — UI 静默降级到上一步快照
      }
    }, 1500);
    // 首次立即拉一次
    (async () => {
      setLoading(true);
      try {
        const snap = await getDebugSession(sessionId);
        if (!cancelled && snap) {
          setSteps(snap.steps || []);
          if (snap.startedAt) {
            const start = new Date(snap.startedAt).getTime();
            const end = snap.finishedAt ? new Date(snap.finishedAt).getTime() : Date.now();
            setElapsedMs(Math.max(0, (end || 0) - start));
          }
        }
      } catch {
        // ignore
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [sessionId, paused]);

  const emitNavigate = (nodeId: string) => {
    window.dispatchEvent(new CustomEvent('dw-monitor-navigate', { detail: { nodeId } }));
  };

  if (!sessionId) {
    return (
      <div className={`h-full flex items-center justify-center text-xs ${styles.cardTextMuted}`}>
        {t('dw.monitor.overview.noSession')}
      </div>
    );
  }

  return (
    <div className="h-full flex flex-col gap-2 pr-1">
      {/* KPI bar */}
      <div className={`flex items-center gap-2 text-[10px] ${styles.cardTextMuted} shrink-0`}>
        <span className={`px-2 py-0.5 rounded ${styles.sidebarBg} ${styles.cardText} font-mono`}>
          {t('dw.monitor.kpi.progress', { x: landedSteps, y: totalNodes })}
            {loading && <Loader2 size={10} className="inline ml-1 animate-spin" />}
        </span>
        <span className={`px-2 py-0.5 rounded ${styles.sidebarBg} ${styles.cardText} font-mono`}>
          {t('dw.monitor.kpi.rows', { n: rowsTotal })}
        </span>
        <span className={`px-2 py-0.5 rounded ${styles.sidebarBg} ${styles.cardText} font-mono`}>
          {t('dw.monitor.kpi.elapsed', { s: (elapsedMs / 1000).toFixed(1) })}
        </span>
        {failedSteps > 0 && (
          <span className={`px-2 py-0.5 rounded ${styles.dangerBg} ${styles.dangerText}`}>
            {t('dw.monitor.kpi.failed', { n: failedSteps })}
          </span>
        )}
      </div>

      {/* Horizontal node chain */}
      <div className="flex-1 overflow-x-auto overflow-y-hidden px-1">
        <div className="flex items-center gap-1 h-full min-w-max">
          {steps.length === 0 ? (
            <span className={`text-[10px] ${styles.cardTextMuted} px-2`}>{t('dw.monitor.overview.empty')}</span>
          ) : (
            steps.map((s, idx) => {
              const NodeIcon = ICONS[s.type || ''] || Settings;
              const isCurrent = session?.sessionId && s.nodeId === (session as { currentNodeId?: string } & typeof session & { currentNodeId?: string })?.['currentNodeId' as keyof typeof session]?.toString();
              const status = s?.status || 'QUEUED';
              const isBreak = status === 'BROKEN';
              return (
                <React.Fragment key={s.nodeId}>
                  <button
                    onClick={() => emitNavigate(s.nodeId)}
                    className={`flex items-center gap-1.5 px-2 py-1 rounded border ${styles.cardBorder} ${styles.sidebarBg} ${
                      status === 'SUCCEEDED'
                        ? `${styles.successBorder}`
                        : status === 'FAILED'
                          ? `${styles.dangerBorder}`
                          : status === 'RUNNING'
                            ? `${styles.infoBorder}`
                            : ''
                    } ${isBreak ? `ring-2 ${styles.warningBorder}` : ''}`}
                    title={`${s.nodeId} — ${status}`}
                  >
                    <NodeIcon
                      size={14}
                      className={
                        status === 'FAILED'
                          ? styles.dangerText
                          : status === 'SUCCEEDED'
                            ? styles.successText
                            : status === 'RUNNING'
                              ? `${styles.accentText} animate-pulse`
                              : styles.cardTextMuted
                      }
                    />
                    <div className="flex flex-col items-start leading-tight">
                      <span className={`text-[10px] font-semibold ${styles.cardText}`}>
                        {s.nodeId.slice(0, 14)}
                      </span>
                      <span className={`text-[9px] font-mono ${styles.cardTextMuted}`}>
                        {status}
                        {typeof s.rowsProcessed === 'number' && s.rowsProcessed > 0 && ` · ${s.rowsProcessed}`}
                      </span>
                    </div>
                    {status === 'SUCCEEDED' && <Check size={10} className={styles.successText} />}
                    {status === 'FAILED' && <X size={10} className={styles.dangerText} />}
                  </button>
                  {idx < steps.length - 1 && (
                    <span className={`inline-block w-2 h-[2px] ${styles.sidebarBg}`} />
                  )}
                </React.Fragment>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
}
