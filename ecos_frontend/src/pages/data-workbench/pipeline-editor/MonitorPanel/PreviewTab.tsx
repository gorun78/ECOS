/**
 * PreviewTab — 节点选择器 + 输入/输出 schema 对比 + 前 100 行数据表格
 *
 * - 选择器 = 当前调试会话 steps (拓扑序, 含 QUEUED 占位)
 * - schema 对比: columnsIn (左, 输入) / columnsOut (右, 输出) 双列表
 *   + 高亮差异 (上下游字段名差异)
 * - 数据表格: 前 100 行 (后端 NodeResult.sampleRows 留存)
 *
 * @license Apache-2.0
 */
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { ChevronRight, Database } from 'lucide-react';
import { useTheme } from '../../../../components/ThemeContext';
import { useLanguage } from '../../../../components/LanguageContext';
import { getDebugSession, getNodePreview } from '../../pipelineDebugApi';
import type { NodeStepSnapshot } from '../../pipelineDebugApi';

interface Props {
  /** live session (MonitorPanel 注入) */
  session?: { sessionId: string } | null;
}

interface PreviewData {
  columnsIn?: string[];
  columnsOut?: string[];
  sampleRows?: Record<string, unknown>[];
}

export default function PreviewTab({ session }: Props) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const sessionId = session?.sessionId ?? null;
  const [steps, setSteps] = useState<NodeStepSnapshot[]>([]);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [preview, setPreview] = useState<PreviewData | null>(null);

  // ── Sync steps when session starts / changes ──
  useEffect(() => {
    if (!sessionId) {
      setSteps([]);
      return;
    }
    let cancelled = false;
    const tick = async () => {
      try {
        const snap = await getDebugSession(sessionId);
        if (cancelled || !snap) return;
        setSteps(snap.steps || []);
        if (!selectedNodeId && snap.currentNodeId) {
          setSelectedNodeId(snap.currentNodeId);
        }
      } catch {
        // ignore
      }
    };
    tick();
    const timer = window.setInterval(tick, 2000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [sessionId, selectedNodeId]);

  // ── Pull preview for the selected node ──
  const loadPreview = useCallback(
    async (nodeId: string | null) => {
      if (!sessionId || !nodeId) {
        setPreview(null);
        return;
      }
      try {
        const p = await getNodePreview(sessionId, nodeId);
        if (p) setPreview(p as PreviewData);
      } catch {
        // ignore
      }
    },
    [sessionId]
  );

  useEffect(() => {
    loadPreview(selectedNodeId);
  }, [selectedNodeId, loadPreview]);

  // ── Column diff: in-only / out-only ──
  const inOnly = useMemo(() => {
    const inSet = new Set(preview?.columnsIn || []);
    const outSet = new Set(preview?.columnsOut || []);
    return (preview?.columnsIn || []).filter((c) => !outSet.has(c));
  }, [preview]);
  const outOnly = useMemo(() => {
    const inSet = new Set(preview?.columnsIn || []);
    const outSet = new Set(preview?.columnsOut || []);
    return (preview?.columnsOut || []).filter((c) => !inSet.has(c));
  }, [preview]);

  const tableColumns = useMemo(() => {
    const cols = preview?.columnsOut?.length ? preview.columnsOut : preview?.columnsIn || [];
    return cols.slice(0, 12);
  }, [preview]);

  const tableRows = useMemo(() => preview?.sampleRows?.slice(0, 100) || [], [preview]);

  if (!sessionId) {
    return (
      <div className={`h-full flex items-center justify-center text-xs ${styles.cardTextMuted}`}>
        {t('dw.monitor.preview.noSession')}
      </div>
    );
  }

  return (
    <div className="h-full flex gap-2 pr-1">
      {/* Node selector (left, 200px) */}
      <div className={`w-48 shrink-0 border-r pr-2 flex flex-col`}>
        <div className={`text-[10px] font-semibold ${styles.cardTextMuted} mb-1`}>
          {t('dw.monitor.preview.nodeSelect')}
        </div>
        <div className="flex-1 overflow-y-auto flex flex-col">
          {steps.length === 0 ? (
            <div className={`text-[10px] ${styles.cardTextMuted} px-1 py-2`}>—</div>
          ) : (
            steps.map((s) => {
              const active = s.nodeId === selectedNodeId;
              return (
                <button
                  key={s.nodeId}
                  onClick={() => setSelectedNodeId(s.nodeId)}
                  className={`flex items-center gap-1.5 px-1.5 py-1 text-left text-[10px] rounded ${
                    active ? `${styles.accentBg} ${styles.accentText}` : `${styles.sidebarBg} ${styles.cardTextMuted}`
                  }`}
                >
                  <Database size={11} />
                  <span className={`truncate ${active ? styles.accentText : styles.cardText}`}>{s.nodeId}</span>
                  <span className={`ml-auto flex items-center gap-0.5 ${styles.cardTextMuted}`}>
                    <ChevronRight size={10} />
                  </span>
                </button>
              );
            })
          )}
        </div>
      </div>

      {/* Schema diff (top) + data table (bottom) */}
      <div className="flex-1 flex flex-col gap-2 pr-1">
        {/* schema diff */}
        <div className="shrink-0 grid grid-cols-2 gap-2 text-[10px]">
          <div className={`border rounded px-2 py-1 ${styles.sidebarBg}`}>
            <div className={`flex items-center justify-between mb-1`}>
              <span className={`font-semibold ${styles.cardText}`}>{t('dw.monitor.preview.input')}</span>
              <span className={`${styles.cardTextMuted}`}>{(preview?.columnsIn || []).length}</span>
            </div>
            <div className="flex flex-wrap gap-1">
              {(preview?.columnsIn || []).length === 0 && (
                <span className={`${styles.cardTextMuted}`}>—</span>
              )}
              {(preview?.columnsIn || []).map((c) => (
                <span
                  key={c}
                  className={`px-1.5 py-0.5 rounded bone-dc text-[9px] ${
                    inOnly.length > 0 && inOnly.includes(c)
                      ? `${styles.warningBg} ${styles.warningText}`
                      : `${styles.sidebarBg} ${styles.cardTextMuted}`
                  }`}
                >
                  {c}
                </span>
              ))}
            </div>
          </div>
          <div className={`border rounded px-2 py-1 ${styles.sidebarBg}`}>
            <div className={`flex items-center justify-between mb-1`}>
              <span className={`font-semibold ${styles.cardText}`}>{t('dw.monitor.preview.output')}</span>
              <span className={`${styles.cardTextMuted}`}>{(preview?.columnsOut || []).length}</span>
            </div>
            <div className="flex flex-wrap gap-1">
              {(preview?.columnsOut || []).length === 0 && (
                <span className={`${styles.cardTextMuted}`}>—</span>
              )}
              {(preview?.columnsOut || []).map((c) => (
                <span
                  key={c}
                  className={`px-1.5 py-0.5 rounded text-[9px] ${
                    outOnly.length > 0 && outOnly.includes(c)
                      ? `${styles.successBg} ${styles.successText}`
                      : `${styles.sidebarBg} ${styles.cardTextMuted}`
                  }`}
                >
                  {c}
                </span>
              ))}
            </div>
          </div>
        </div>

        {/* data table */}
        <div className="flex-1 overflow-auto border rounded" style={{ background: styles.sidebarBg }}>
          {tableRows.length === 0 ? (
            <div className={`h-full flex items-center justify-center text-[10px] ${styles.cardTextMuted}`}>
              {t('dw.monitor.preview.empty')}
            </div>
          ) : (
            <table className="w-full text-[10px] font-mono">
              <thead className={`sticky top-0 ${styles.sidebarBg} ${styles.cardTextMuted}`}>
                <tr>
                  <th className="px-2 py-1 text-left w-8">#</th>
                  {tableColumns.map((c) => (
                    <th key={c} className="px-2 py-1 text-left whitespace-nowrap">
                      {c}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {tableRows.map((row, i) => (
                  <tr key={i} className={`border-t ${styles.cardBorder}`}>
                    <td className={`px-2 py-1 ${styles.cardTextMuted}`}>{i + 1}</td>
                    {tableColumns.map((c) => (
                      <td key={c} className={`px-2 py-1 ${styles.cardText} whitespace-nowrap`}>
                        {JSON.stringify(row[c] ?? '')}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  );
}
