/**
 * govern/assumptions — 假设库子 Tab（REQ-KB-03 / F04-07 / 分册 04 B.3）。
 *
 * 主流程（B.2）：建 DRAFT → 提交 → 审批 → ACTIVE → 到期 EXPIRED
 * 数据字段（B.3）：scenario / metric / dimScope / value / semantic / base / reason /
 *                evidenceRef / expireAt / version / unverified / status
 * 交互态（B.4）：状态机徽标；DELTA 显示相对基准（base）方向箭头
 * 空/错误态（B.5）：改已批准项 → 409/ECOS-KB-071 提示「建新版本」；
 *                 过期引用 → 410/ECOS-KB-072
 * 权限（B.6）：审批需角色；unverified 必须在 UI 呈现
 * 错误态分治（F04-03）：走 kbFetchData；KbApiError → 显式提示
 * 主题 i18n：knowledge.assumptions.*
 */
import { useCallback, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import {
  AlertTriangle, GitFork, Hourglass, Loader2, Plus, RefreshCw, Send, ShieldCheck,
} from 'lucide-react';
import { useLanguage } from '../../../../../components/LanguageContext';
import { useTheme } from '../../../../../components/ThemeContext';
import { knowledgeApi, KbApiError } from '../../../services/knowledgeApi';
import type { KbAssumptionRow } from '../../../services/govern';

interface BizError { code?: string; message: string }

const STATUS_ORDER = ['DRAFT', 'SUBMITTED', 'APPROVED', 'ACTIVE', 'EXPIRED', 'REJECTED'];

export default function AssumptionsTab() {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const [rows, setRows] = useState<KbAssumptionRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [bizError, setBizError] = useState<BizError | null>(null);

  // 新建表单
  const [showForm, setShowForm] = useState(false);
  const [fScenario, setFScenario] = useState('');
  const [fMetric, setFMetric] = useState('');
  const [fValue, setFValue] = useState('');
  const [fSemantic, setFSemantic] = useState('');
  const [fReason, setFReason] = useState('');
  const [creating, setCreating] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setBizError(null);
    try {
      const list = await knowledgeApi.kbFetchAssumptions({});
      setRows(Array.isArray(list) ? list : []);
    } catch (e) {
      if (e instanceof KbApiError && (e.status >= 500 || (e.code && e.code.startsWith('ECOS-KB-')))) {
        setBizError({ code: e.code, message: e.message });
      }
      setRows([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const handleCreate = useCallback(async () => {
    if (!fScenario.trim() || !fMetric.trim() || !fValue.trim()) return;
    setCreating(true);
    setBizError(null);
    try {
      await knowledgeApi.kbCreateAssumption({
        scenarioType: fScenario.trim(),
        metric: fMetric.trim(),
        value: fValue.trim(),
        valueSemantic: fSemantic.trim() || undefined,
        reason: fReason.trim() || undefined,
      });
      setShowForm(false);
      setFScenario(''); setFMetric(''); setFValue(''); setFSemantic(''); setFReason('');
      void load();
    } catch (e) {
      setBizError(codeOf(e));
    } finally {
      setCreating(false);
    }
  }, [fScenario, fMetric, fValue, fSemantic, fReason, load]);

  const act = useCallback(async (id: string, kind: 'submit' | 'approve') => {
    setBizError(null);
    try {
      if (kind === 'submit') await knowledgeApi.kbSubmitAssumption(id);
      else await knowledgeApi.kbApproveAssumption(id);
      void load();
    } catch (e) {
      // 409/ECOS-KB-071（APPROVED 不可改）→ 提示「建新版本」
      // 410/ECOS-KB-072 → 已过期
      setBizError(codeOf(e));
    }
  }, [load]);

  return (
    <div className="flex flex-col gap-3 h-full min-h-0">
      {bizError && (
        <div className="flex items-start gap-2 px-3 py-2 rounded-md border text-xs"
             style={{ background: styles.dangerBg, borderColor: styles.dangerBorder, color: styles.dangerText }}>
          <AlertTriangle className="w-3.5 h-3.5 shrink-0 mt-0.5" />
          <div className="flex-1 min-w-0">
            <div className="font-bold flex items-center gap-1.5">
              {t('knowledge.assumptions.error')}
              {bizError.code && <span className="font-mono text-[10px]">{bizError.code}</span>}
            </div>
            <p className="opacity-90 mt-0.5">
              {bizError.code === 'ECOS-KB-071'
                ? t('knowledge.assumptions.conflict_409')
                : bizError.code === 'ECOS-KB-072'
                  ? t('knowledge.assumptions.expired_410')
                  : bizError.message}
            </p>
          </div>
          <button type="button" onClick={() => void load()}
                  className="px-2 py-1 rounded border text-[10px] font-bold cursor-pointer shrink-0"
                  style={{ borderColor: styles.dangerBorder, color: styles.dangerText }}>
            <RefreshCw className="w-3 h-3 inline mr-1" />{t('knowledge.common.retry')}
          </button>
        </div>
      )}

      {/* 头 + 新建 */}
      <div className="flex items-center justify-between px-1">
        <span className="text-[11px] font-bold flex items-center gap-1.5" style={{ color: styles.cardText }}>
          <GitFork className="w-3.5 h-3.5" />{t('knowledge.assumptions.title')}
        </span>
        <button type="button" onClick={() => setShowForm((s) => !s)}
                className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded text-[10px] font-bold cursor-pointer"
                style={{ background: styles.accentBg, color: 'rgba(255,255,255,0.95)' }}>
          <Plus className="w-3 h-3" />{t('knowledge.assumptions.new')}
        </button>
      </div>

      {showForm && (
        <div className="rounded-md border p-3 space-y-2" style={{ borderColor: styles.cardBorder, background: styles.cardBg }}>
          <div className="grid grid-cols-2 gap-2">
            <input value={fScenario} onChange={(e) => setFScenario(e.target.value)}
                   placeholder={t('knowledge.assumptions.field_scenario')}
                   className="px-2 py-1.5 rounded text-[11px] outline-none"
                   style={{ background: styles.inputBg, color: styles.inputText, border: `1px solid ${styles.inputBorder}` }} />
            <input value={fMetric} onChange={(e) => setFMetric(e.target.value)}
                   placeholder={t('knowledge.assumptions.field_metric')}
                   className="px-2 py-1.5 rounded text-[11px] outline-none"
                   style={{ background: styles.inputBg, color: styles.inputText, border: `1px solid ${styles.inputBorder}` }} />
            <input value={fValue} onChange={(e) => setFValue(e.target.value)}
                   placeholder={t('knowledge.assumptions.field_value')}
                   className="px-2 py-1.5 rounded text-[11px] outline-none"
                   style={{ background: styles.inputBg, color: styles.inputText, border: `1px solid ${styles.inputBorder}` }} />
            <input value={fSemantic} onChange={(e) => setFSemantic(e.target.value)}
                   placeholder={t('knowledge.assumptions.field_semantic')}
                   className="px-2 py-1.5 rounded text-[11px] outline-none"
                   style={{ background: styles.inputBg, color: styles.inputText, border: `1px solid ${styles.inputBorder}` }} />
          </div>
          <input value={fReason} onChange={(e) => setFReason(e.target.value)}
                 placeholder={t('knowledge.assumptions.field_reason')}
                 className="w-full px-2 py-1.5 rounded text-[11px] outline-none"
                 style={{ background: styles.inputBg, color: styles.inputText, border: `1px solid ${styles.inputBorder}` }} />
          <button type="button" onClick={() => void handleCreate()}
                  disabled={creating || !fScenario.trim() || !fMetric.trim() || !fValue.trim()}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded text-[11px] font-bold cursor-pointer disabled:opacity-50"
                  style={{ background: styles.accentBg, color: 'rgba(255,255,255,0.95)' }}>
            {creating ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Plus className="w-3.5 h-3.5" />}
            {t('knowledge.assumptions.create')}
          </button>
        </div>
      )}

      {/* 列表 */}
      <div className="rounded-md border flex-1 min-h-0 flex flex-col overflow-hidden" style={{ borderColor: styles.cardBorder, background: styles.cardBg }}>
        <div className="flex-1 overflow-y-auto">
          {loading && rows.length === 0 ? (
            <div className="p-6 flex items-center justify-center text-[11px]" style={{ color: styles.muted }}>
              <Loader2 className="w-3.5 h-3.5 animate-spin mr-1.5" />{t('knowledge.assumptions.loading')}
            </div>
          ) : rows.length === 0 ? (
            <div className="p-6 flex items-center justify-center text-[11px]" style={{ color: styles.muted }}>
              {t('knowledge.assumptions.empty')}
            </div>
          ) : (
            <table className="w-full text-[10px] font-mono">
              <thead className="sticky top-0" style={{ background: styles.sidebarBg, color: styles.cardTextMuted }}>
                <tr>
                  <Th>{t('knowledge.assumptions.col_scenario')}</Th>
                  <Th>{t('knowledge.assumptions.col_metric')}</Th>
                  <Th className="text-right">{t('knowledge.assumptions.col_value')}</Th>
                  <Th>{t('knowledge.assumptions.col_semantic')}</Th>
                  <Th>{t('knowledge.assumptions.col_version')}</Th>
                  <Th>{t('knowledge.assumptions.col_status')}</Th>
                  <Th className="text-right">{t('knowledge.assumptions.col_actions')}</Th>
                </tr>
              </thead>
              <tbody style={{ color: styles.cardText }}>
                {rows.map((a) => {
                  const hasBase = !!a.baseAssumptionId;
                  return (
                    <tr key={a.id} className="border-t" style={{ borderColor: styles.cardBorder }}>
                      <td className="px-2 py-1.5 truncate max-w-[120px]">{a.scenarioType ?? a.assumptionKey}</td>
                      <td className="px-2 py-1.5">{a.metric ?? '—'}</td>
                      <td className="px-2 py-1.5 text-right">
                        <span className="inline-flex items-center gap-1">
                          {typeof a.value === 'string' ? a.value : String(a.value ?? '—')}
                          {hasBase && <DeltaArrow up={0} />}
                        </span>
                      </td>
                      <td className="px-2 py-1.5">{a.valueSemantic ?? '—'}</td>
                      <td className="px-2 py-1.5">{a.assumptionVersion ?? '—'}</td>
                      <td className="px-2 py-1.5">
                        <div className="flex items-center gap-1">
                          <StatusBadge status={a.status ?? 'DRAFT'} />
                          {/* unverified 必须在 UI 出现（B.6） */}
                          {a.unverified && (
                            <span className="inline-flex items-center gap-0.5 px-1 py-0.5 rounded text-[8px] font-bold bg-rose-100 text-rose-700 dark:bg-rose-900/30 dark:text-rose-300"
                                  title={t('knowledge.assumptions.unverified_hint')}>
                              <Hourglass className="w-2.5 h-2.5" />
                              {t('knowledge.assumptions.unverified')}
                            </span>
                          )}
                        </div>
                      </td>
                      <td className="px-2 py-1.5 text-right">
                        <div className="inline-flex gap-1">
                          {canStatus(a.status ?? 'DRAFT', 'DRAFT') && (
                            <ActionBtn title={t('knowledge.assumptions.submit')}
                                       onClick={() => void act(a.id, 'submit')}>
                              <Send className="w-2.5 h-2.5" />
                            </ActionBtn>
                          )}
                          {canStatus(a.status ?? 'DRAFT', 'SUBMITTED') && (
                            <ActionBtn title={t('knowledge.assumptions.approve')}
                                       onClick={() => void act(a.id, 'approve')}>
                              <ShieldCheck className="w-2.5 h-2.5" />
                            </ActionBtn>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>
      </div>
    </div>
  );
}

function canStatus(cur: string, from: string): boolean {
  return (cur || '').toUpperCase() === from;
}

function Th({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <th className={`px-2 py-1.5 text-left font-bold uppercase tracking-wider ${className}`}>{children}</th>;
}

function DeltaArrow({ up }: { up: number }) {
  // up > 0 相对基准上涨；up < 0 下跌（token 化色：默认继承卡片文本色）
  return <span className="text-[9px] font-bold">{up >= 0 ? '▲' : '▼'}</span>;
}

function ActionBtn({ title, onClick, children }: { title: string; onClick: () => void; children: ReactNode }) {
  const { styles } = useTheme();
  return (
    <button type="button" title={title} onClick={onClick}
            className="p-1 rounded border cursor-pointer hover:opacity-70"
            style={{ borderColor: styles.inputBorder, color: styles.cardText }}>
      {children}
    </button>
  );
}

function StatusBadge({ status }: { status: string }) {
  const up = status.toUpperCase();
  const cls =
    up === 'ACTIVE' || up === 'APPROVED' ? 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-300'
    : up === 'EXPIRED' ? 'bg-slate-100 text-slate-500 dark:bg-slate-800/40 dark:text-slate-400'
    : up === 'REJECTED' ? 'bg-rose-100 text-rose-700 dark:bg-rose-900/30 dark:text-rose-300'
    : up === 'SUBMITTED' ? 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-300'
    : 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-300';
  return <span className={`inline-block px-1.5 py-0.5 rounded text-[9px] font-bold ${cls}`}>{status}</span>;
}

function codeOf(e: unknown): BizError {
  if (e instanceof KbApiError) return { code: e.code, message: e.message };
  return { message: e instanceof Error ? e.message : String(e) };
}

