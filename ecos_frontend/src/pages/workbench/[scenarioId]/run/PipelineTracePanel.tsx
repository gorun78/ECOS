/**
 * PipelineTracePanel — 场景智能逻辑（PIPELINE）执行轨迹面板。
 *
 * <p>数据全部来自服务端（{@code AnalysisRunVO.result} ← 锚点 simulation_result 槽位，
 * 由 ai-engine 编排执行器真实返回的 nodeTraces/outputs）；本组件只渲染，禁本地合成
 * 节点状态或耗时。无 PIPELINE 记录时不渲染任何内容（不占位、不伪造空态）。</p>
 */
import React, { useMemo, useState } from 'react';
import { ChevronDown, ChevronRight, Workflow } from 'lucide-react';
import { useLanguage } from '../../../../components/LanguageContext';
import { useTheme } from '../../../../components/ThemeContext';
import type { AnalysisRunVO } from '../../../../services/scenarioAnalysisClient';

interface Props {
  analyses: AnalysisRunVO[];
}

interface TraceRow {
  nodeId: string;
  status?: string;
  latencyMs?: number;
  error?: string;
  output?: string;
}

function tracesOf(result: Record<string, unknown> | undefined): TraceRow[] {
  const raw = result?.nodeTraces;
  if (!raw || typeof raw !== 'object') return [];
  return Object.entries(raw as Record<string, unknown>).map(([nodeId, v]) => {
    const t = (v ?? {}) as Record<string, unknown>;
    return {
      nodeId,
      status: t.status == null ? undefined : String(t.status),
      latencyMs: typeof t.latencyMs === 'number' ? t.latencyMs : undefined,
      error: t.error == null ? undefined : String(t.error),
      output: t.output == null ? undefined : String(t.output),
    };
  });
}

export default function PipelineTracePanel({ analyses }: Props) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [expanded, setExpanded] = useState<string | null>(null);

  const pipelineRuns = useMemo(
    () => analyses.filter((a) => String(a.analysisType ?? '').toUpperCase() === 'PIPELINE'),
    [analyses],
  );

  if (pipelineRuns.length === 0) return null;

  return (
    <section className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4`}>
      <h3 className={`text-sm font-bold ${styles.cardText} mb-3 flex items-center gap-1.5`}>
        <Workflow size={14} />
        {t('workspace.run.pipeline.trace.title')}
      </h3>
      <div className="space-y-2">
        {pipelineRuns.map((run) => {
          const id = String(run.id ?? run.analysisId ?? '');
          const result = run.result;
          const rows = tracesOf(result);
          const isOpen = expanded === id;
          const degraded = Boolean(run.degraded);
          return (
            <div key={id} className={`border rounded ${styles.cardBorder}`}>
              <button
                type="button"
                onClick={() => setExpanded(isOpen ? null : id)}
                className={`w-full flex items-center gap-2 px-2.5 py-1.5 text-left cursor-pointer ${styles.cardText}`}
              >
                {isOpen ? <ChevronDown size={12} /> : <ChevronRight size={12} />}
                <span className="font-mono text-[11px]">{id}</span>
                <span className={`text-[11px] ${styles.cardTextMuted}`}>{String(run.sourceId ?? '—')}</span>
                <span className={`ml-auto text-[11px] font-bold ${degraded ? styles.dangerText : styles.successText}`}>
                  {String(run.status ?? '—')}
                </span>
              </button>
              {isOpen && (
                <div className="px-2.5 pb-2.5">
                  {rows.length === 0 ? (
                    <p className={`text-[11px] ${styles.cardTextMuted}`}>{t('workspace.run.pipeline.trace.empty')}</p>
                  ) : (
                    <table className={`w-full text-[11px] ${styles.cardText}`}>
                      <thead>
                        <tr className={`border-b ${styles.cardBorder} ${styles.cardTextMuted}`}>
                          <th className="py-1 pr-2 text-left">{t('workspace.run.pipeline.trace.node')}</th>
                          <th className="py-1 pr-2 text-left">{t('workspace.run.pipeline.trace.status')}</th>
                          <th className="py-1 pr-2 text-left">ms</th>
                          <th className="py-1 pr-2 text-left">{t('workspace.run.pipeline.trace.output')}</th>
                        </tr>
                      </thead>
                      <tbody>
                        {rows.map((r) => (
                          <tr key={r.nodeId} className={`border-b ${styles.cardBorder}`}>
                            <td className="py-1 pr-2 font-mono">{r.nodeId}</td>
                            <td className={`py-1 pr-2 ${r.status === 'failed' ? styles.dangerText : ''}`}>{r.status ?? '—'}</td>
                            <td className="py-1 pr-2">{r.latencyMs ?? '—'}</td>
                            <td className="py-1 pr-2 font-mono break-all">
                              {r.error ? r.error : (r.output ?? '—')}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  )}
                  {result?.message != null && (
                    <p className={`text-[11px] mt-1.5 ${styles.cardTextMuted}`}>{String(result.message)}</p>
                  )}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </section>
  );
}
