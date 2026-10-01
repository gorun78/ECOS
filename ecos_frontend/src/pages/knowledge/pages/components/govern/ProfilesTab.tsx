/**
 * govern/profiles — 历史画像子 Tab（REQ-KB-02 / F04-06 / 分册 04 B.3）。
 *
 * 主流程（B.2）：选指标 + 维度 + 窗口 → 提交生成任务 → 任务进度 → DRAFT 列表 → 发布
 * 数据字段（B.3）：profile_key / metric / dims / window / p10 / p50 / p90 / ci /
 *                sample / missing / confidence / degrade_from / version / status
 * 交互态（B.4）：退化链树形可视化；置信度色阶
 * 空/错误态（B.5）：任务失败显示 taskId + 错误；样本不足不报错只降置信度
 * 权限（B.6）：发布需知识管理员角色 → 403/ECOS-KB-062 → KbApiError 提示
 * 错误态分治（F04-03）：`kbFetchProfiles` / `kbGenerateProfiles` / `kbPublishProfile`
 *                       走 kbFetchData；5xx / ECOS-KB-* → KbApiError → 显式故障态
 * 主题 §4.1：useTheme token（class）
 * i18n §4.3：0 硬编码中文（knowledge.profiles.*）
 */
import { useCallback, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import {
  AlertTriangle, Building2, ChevronDown, ChevronRight, Loader2,
  Play, RefreshCw, Send, TrendingUp,
} from 'lucide-react';
import { useLanguage } from '../../../../../components/LanguageContext';
import { useTheme } from '../../../../../components/ThemeContext';
import { knowledgeApi, KbApiError } from '../../../services/knowledgeApi';
import type { KbProfileRow } from '../../../services/govern';

interface BizError { code?: string; message: string }

export default function ProfilesTab() {
  const { styles } = useTheme();
  const { t } = useLanguage();

  // ── 生成表单 ──
  const [metric, setMetric] = useState('');
  const [dims, setDims] = useState('');
  const [windowFrom, setWindowFrom] = useState('');
  const [windowTo, setWindowTo] = useState('');
  const [generating, setGenerating] = useState(false);
  const [lastTask, setLastTask] = useState<{ taskId: string; traceId?: string } | null>(null);

  // ── 列表 ──
  const [rows, setRows] = useState<KbProfileRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [bizError, setBizError] = useState<BizError | null>(null);
  // 退化链展开
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});

  const loadProfiles = useCallback(async () => {
    setLoading(true);
    setBizError(null);
    try {
      const list = await knowledgeApi.kbFetchProfiles({ page: 1, size: 20 });
      setRows(Array.isArray(list) ? list : []);
    } catch (e) {
      if (e instanceof KbApiError && (e.status >= 500 || (e.code && e.code.startsWith('ECOS-KB-')))) {
        setBizError({ code: e.code, message: e.message });
      }
      // 其余错误：空态
      setRows([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void loadProfiles(); }, [loadProfiles]);

  const handleGenerate = useCallback(async () => {
    if (!metric.trim() || !windowFrom.trim() || !windowTo.trim()) return;
    setGenerating(true);
    setBizError(null);
    try {
      const resp = await knowledgeApi.kbGenerateProfiles({
        metricCodes: metric.trim().split(',').map(s => s.trim()).filter(Boolean),
        dims: parseDims(dims),
        windowFrom: windowFrom.trim(),
        windowTo: windowTo.trim(),
        dryRun: false,
      });
      setLastTask({ taskId: resp.taskId, traceId: resp.traceId });
      // 任务提交后刷新列表（DRAFT 行出现）
      void loadProfiles();
    } catch (e) {
      if (e instanceof KbApiError) {
        // 503/ECOS-KB-021（runtime-task 不可用）→ 显式 taskId 缺失故障
        setBizError({ code: e.code, message: e.message });
      } else {
        setBizError({ message: e instanceof Error ? e.message : String(e) });
      }
    } finally {
      setGenerating(false);
    }
  }, [metric, dims, windowFrom, windowTo, loadProfiles]);

  const handlePublish = useCallback(async (id: string) => {
    setBizError(null);
    try {
      await knowledgeApi.kbPublishProfile(id);
      void loadProfiles();
    } catch (e) {
      if (e instanceof KbApiError) {
        // 403/ECOS-KB-062（非知识管理员）/ 409 冲突
        setBizError({ code: e.code, message: e.message });
      } else {
        setBizError({ message: e instanceof Error ? e.message : String(e) });
      }
    }
  }, [loadProfiles]);

  return (
    <div className="flex flex-col gap-3 h-full min-h-0">
      {/* 错误态分治 banner */}
      {bizError && (
        <div className="flex items-start gap-2 px-3 py-2 rounded-md border text-xs"
             style={{ background: styles.dangerBg, borderColor: styles.dangerBorder, color: styles.dangerText }}>
          <AlertTriangle className="w-3.5 h-3.5 shrink-0 mt-0.5" />
          <div className="flex-1 min-w-0">
            <div className="font-bold flex items-center gap-1.5">
              {t('knowledge.profiles.error')}
              {bizError.code && <span className="font-mono text-[10px]">{bizError.code}</span>}
            </div>
            <p className="opacity-80 break-all mt-0.5">{bizError.message}</p>
          </div>
          <button type="button" onClick={() => void loadProfiles()}
                  className="px-2 py-1 rounded border text-[10px] font-bold cursor-pointer shrink-0"
                  style={{ borderColor: styles.dangerBorder, color: styles.dangerText }}>
            <RefreshCw className="w-3 h-3 inline mr-1" />
            {t('knowledge.common.retry')}
          </button>
        </div>
      )}

      {/* 生成表单 */}
      <div className="rounded-md border p-3 space-y-2" style={{ borderColor: styles.cardBorder, background: styles.cardBg }}>
        <div className="text-[11px] font-bold flex items-center gap-1.5" style={{ color: styles.cardText }}>
          <TrendingUp className="w-3.5 h-3.5" />
          {t('knowledge.profiles.generate')}
        </div>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
          <input value={metric} onChange={(e) => setMetric(e.target.value)}
                 placeholder={t('knowledge.profiles.field_metric')}
                 className="px-2 py-1.5 rounded text-[11px] outline-none"
                 style={{ background: styles.inputBg, color: styles.inputText, border: `1px solid ${styles.inputBorder}` }} />
          <input value={dims} onChange={(e) => setDims(e.target.value)}
                 placeholder={t('knowledge.profiles.field_dims')}
                 className="px-2 py-1.5 rounded text-[11px] outline-none"
                 style={{ background: styles.inputBg, color: styles.inputText, border: `1px solid ${styles.inputBorder}` }} />
          <input type="month" value={windowFrom} onChange={(e) => setWindowFrom(e.target.value)}
                 aria-label={t('knowledge.profiles.field_window_from')}
                 className="px-2 py-1.5 rounded text-[11px] outline-none"
                 style={{ background: styles.inputBg, color: styles.inputText, border: `1px solid ${styles.inputBorder}` }} />
          <input type="month" value={windowTo} onChange={(e) => setWindowTo(e.target.value)}
                 aria-label={t('knowledge.profiles.field_window_to')}
                 className="px-2 py-1.5 rounded text-[11px] outline-none"
                 style={{ background: styles.inputBg, color: styles.inputText, border: `1px solid ${styles.inputBorder}` }} />
        </div>
        <div className="flex items-center gap-2">
          <button type="button" onClick={() => void handleGenerate()}
                  disabled={generating || !metric.trim() || !windowFrom.trim() || !windowTo.trim()}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded text-[11px] font-bold cursor-pointer disabled:opacity-50"
                  style={{ background: styles.accentBg, color: 'rgba(255,255,255,0.95)' }}>
            {generating ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Play className="w-3.5 h-3.5" />}
            {t('knowledge.profiles.submit_task')}
          </button>
          {lastTask && (
            <span className="text-[10px] font-mono" style={{ color: styles.muted }}>
              {t('knowledge.profiles.task_submitted')} {lastTask.taskId}
              {lastTask.traceId ? ` · ${lastTask.traceId}` : ''}
            </span>
          )}
        </div>
      </div>

      {/* DRAFT / PUBLISHED 列表 */}
      <div className="rounded-md border flex-1 min-h-0 flex flex-col overflow-hidden" style={{ borderColor: styles.cardBorder, background: styles.cardBg }}>
        <div className="flex items-center justify-between px-3 py-2 border-b" style={{ borderColor: styles.cardBorder }}>
          <span className="text-[11px] font-bold" style={{ color: styles.cardText }}>{t('knowledge.profiles.list')}</span>
          <span className="text-[10px] font-mono" style={{ color: styles.muted }}>
            {loading ? <Loader2 className="w-3 h-3 inline animate-spin" /> : `${rows.length} ${t('knowledge.profiles.unit')}`}
          </span>
        </div>

        {loading && rows.length === 0 ? (
          <div className="flex-1 flex items-center justify-center text-[11px]" style={{ color: styles.muted }}>
            <Loader2 className="w-3.5 h-3.5 animate-spin mr-1.5" />{t('knowledge.profiles.loading')}
          </div>
        ) : rows.length === 0 ? (
          <div className="flex-1 flex items-center justify-center text-[11px]" style={{ color: styles.muted }}>
            {t('knowledge.profiles.empty')}
          </div>
        ) : (
          <div className="flex-1 overflow-y-auto">
            <table className="w-full text-[10px] font-mono">
              <thead className="sticky top-0" style={{ background: styles.sidebarBg }}>
                <tr style={{ color: styles.cardTextMuted }}>
                  <Th>{t('knowledge.profiles.col_key')}</Th>
                  <Th>{t('knowledge.profiles.col_metric')}</Th>
                  <Th>{t('knowledge.profiles.col_window')}</Th>
                  <Th className="text-right">{t('knowledge.profiles.col_p10')}</Th>
                  <Th className="text-right">{t('knowledge.profiles.col_p50')}</Th>
                  <Th className="text-right">{t('knowledge.profiles.col_p90')}</Th>
                  <Th className="text-right">{t('knowledge.profiles.col_sample')}</Th>
                  <Th className="text-right">{t('knowledge.profiles.col_confidence')}</Th>
                  <Th>{t('knowledge.profiles.col_status')}</Th>
                  <Th className="text-right">{t('knowledge.profiles.col_actions')}</Th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <ProfileRow key={r.profileId} row={r}
                    expanded={!!expanded[r.profileId]}
                    onToggleExpand={() => setExpanded((p) => ({ ...p, [r.profileId]: !p[r.profileId] }))}
                    onPublish={() => void handlePublish(r.profileId)} />
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}

function Th({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <th className={`px-2 py-1.5 text-left font-bold uppercase tracking-wider ${className}`}>{children}</th>;
}

function ProfileRow({ row, expanded, onToggleExpand, onPublish }: {
  row: KbProfileRow; expanded: boolean; onToggleExpand: () => void; onPublish: () => void;
}) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const st = row.stats ?? ({} as KbProfileRow['stats']);
  const conf = row.confidence ?? 0;
  const confColor = conf >= 0.8 ? styles.successText : conf >= 0.5 ? styles.warningText : styles.dangerText;
  const isDraft = (row.status || '').toUpperCase() === 'DRAFT';

  return (
    <>
      <tr className="border-t" style={{ borderColor: styles.cardBorder, color: styles.cardText }}
          title={expanded ? t('knowledge.profiles.collapse_degrade') : t('knowledge.profiles.expand_degrade')}>
        <td className="px-2 py-1.5">
          <button type="button" onClick={onToggleExpand}
                  className="cursor-pointer flex items-center gap-1 hover:opacity-70" style={{ color: styles.cardText }}>
            {expanded ? <ChevronDown className="w-3 h-3" /> : <ChevronRight className="w-3 h-3" />}
            <span className="truncate max-w-[140px]">{row.profileKey}</span>
          </button>
        </td>
        <td className="px-2 py-1.5">{row.metric ?? '—'}</td>
        <td className="px-2 py-1.5">{fmtDims(row.dims)}</td>
        <td className="px-2 py-1.5 text-right num">{num(st?.p10)}</td>
        <td className="px-2 py-1.5 text-right num">{num(st?.p50)}</td>
        <td className="px-2 py-1.5 text-right num">{num(st?.p90)}</td>
        <td className="px-2 py-1.5 text-right num">{st?.sampleCount ?? 0}
          {st?.missingRate != null && st.missingRate > 0 ? (
            <span className="ml-1 text-[9px]" style={{ color: styles.warningText }}
                  title={t('knowledge.profiles.missing_rate')}>missing {Math.round(st.missingRate * 100)}%</span>
          ) : null}
        </td>
        <td className="px-2 py-1.5 text-right">
          <span style={{ color: confColor }}>{(conf * 100).toFixed(0)}%</span>
        </td>
        <td className="px-2 py-1.5">
          <StatusChip status={row.status ?? 'DRAFT'} />
        </td>
        <td className="px-2 py-1.5 text-right">
          <button type="button" onClick={onPublish} disabled={!isDraft}
                  title={t('knowledge.profiles.publish')}
                  className="inline-flex items-center gap-1 px-1.5 py-1 rounded border text-[9px] font-bold cursor-pointer disabled:opacity-40"
                  style={{ borderColor: styles.inputBorder, color: styles.accentText }}>
            <Send className="w-2.5 h-2.5" />{t('knowledge.profiles.publish')}
          </button>
        </td>
      </tr>
      {expanded && (
        <tr style={{ background: styles.sidebarBg }}>
          <td colSpan={10} className="px-4 py-2">
            <div className="text-[9px] font-bold uppercase tracking-wider mb-1.5" style={{ color: styles.cardTextMuted }}>
              {t('knowledge.profiles.degrade_chain')}
              {row.degradeFrom && <span className="ml-1 normal-case" style={{ color: styles.muted }}>← {row.degradeFrom}</span>}
            </div>
            {row.degradeFrom ? (
              <div className="flex items-center gap-1 flex-wrap text-[10px]">
                <ChainNode label={`${row.metric ?? row.profileKey} · ${fmtDims(row.dims)}`} highlight />
                <span style={{ color: styles.muted }}>→</span>
                <ChainNode label={row.degradeFrom} />
              </div>
            ) : (
              <p className="text-[10px]" style={{ color: styles.muted }}>{t('knowledge.profiles.no_degrade')}</p>
            )}
          </td>
        </tr>
      )}
    </>
  );
}

function ChainNode({ label, highlight = false }: { label: string; highlight?: boolean }) {
  const { styles } = useTheme();
  return (
    <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded border font-mono"
          style={{
            borderColor: highlight ? styles.accentText : styles.inputBorder,
            color: highlight ? styles.accentText : styles.cardTextMuted,
          }}>
      <Building2 className="w-2.5 h-2.5" />
      {label}
    </span>
  );
}

function StatusChip({ status }: { status: string }) {
  const up = status.toUpperCase();
  const cls =
    up === 'PUBLISHED' ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-300'
    : up === 'DRAFT' ? 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300'
    : up === 'DEPRECATED' ? 'bg-slate-100 text-slate-500 dark:bg-slate-800/40 dark:text-slate-400'
    : 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300';
  return <span className={`inline-block px-1.5 py-0.5 rounded text-[9px] font-bold ${cls}`}>{status}</span>;
}

function fmtDims(dims?: Record<string, unknown>): string {
  if (!dims) return '—';
  const e = Object.entries(dims);
  return e.length ? e.map(([k, v]) => `${k}=${v}`).join(', ') : '—';
}
function num(v?: number): string {
  return v != null ? String(v) : '—';
}

/** dims 字段：`k1=v1,k2=v2` 解析为对象 */
function parseDims(raw: string): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  raw.split(',').map(s => s.trim()).filter(Boolean).forEach((kv) => {
    const i = kv.indexOf('=');
    if (i >= 0) out[kv.slice(0, i).trim()] = kv.slice(i + 1).trim();
  });
  return out;
}
