/**
 * 场景运行 L3 · 研究循环面板（RD-Agent 模式接入首切片，2026-11-25）。
 *
 * <p><b>与 L1 的分工</b>：L1「问题驱动问答」是问一次答一次；本面板是「多轮研究」——
 * 准备（看上下文与预算）→ 发起 → 逐轮谱系（假设/轨迹/评估/建议）→ SOTA 与停止原因 → 两轮对比。
 * 默认折叠，不打扰既有 L1/L2 使用路径。</p>
 *
 * <p><b>如实宣称</b>：面板直接展示后端 {@code executor.kind/codeExecution/network}，
 * 不美化执行器能力；候选标记 {@code candidateOnly} 原样透出，避免用户误以为结论已生效。</p>
 */
import React, { useCallback, useEffect, useState } from 'react';
import { BadgeCheck, FlaskConical, GitCompare, Play, Search, XCircle } from 'lucide-react';
import { useLanguage } from '../../../../components/LanguageContext';
import { useTheme } from '../../../../components/ThemeContext';
import { showToastGlobal } from '../../../../components/common/Toast';
import logger from '../../../../services/logger';
import {
  approveResearchRun,
  compareResearchRuns,
  executeResearchRun,
  listResearchApprovals,
  listResearchRuns,
  prepareResearchRun,
  type ResearchApprovalRecord,
  type ResearchPrepare,
  type ResearchRunRecord,
  type ResearchRunResult,
} from '../../../../services/scenarioResearchRunClient';

interface Props {
  scenarioId: string;
}

const COLLAPSE_KEY = 'ecos.research.panel.open';

export default function ResearchRunPanel({ scenarioId }: Props) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const [open, setOpen] = useState<boolean>(() => {
    try {
      return localStorage.getItem(COLLAPSE_KEY) === '1';
    } catch {
      return false;
    }
  });
  const [question, setQuestion] = useState('');
  const [prep, setPrep] = useState<ResearchPrepare | null>(null);
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<ResearchRunResult | null>(null);
  const [history, setHistory] = useState<ResearchRunRecord[]>([]);
  const [baseA, setBaseA] = useState('');
  const [baseB, setBaseB] = useState('');
  const [diff, setDiff] = useState<Record<string, { left: unknown; right: unknown; same: boolean }> | null>(null);
  const [approvals, setApprovals] = useState<Record<string, ResearchApprovalRecord[]>>({});
  const [refInput, setRefInput] = useState('');
  const [approving, setApproving] = useState<string | null>(null);

  const toggle = (next: boolean) => {
    setOpen(next);
    try {
      localStorage.setItem(COLLAPSE_KEY, next ? '1' : '0');
    } catch {
      // 隐私模式/存储禁用：仅内存态，不影响功能
    }
  };

  const loadHistory = useCallback(async () => {
    try {
      const rows = await listResearchRuns(scenarioId, 50);
      setHistory(rows);
      // 审批历史按轮取，失败不阻断谱系展示（审批是附加信息）
      const entries = await Promise.all(
        rows.map(async (r) => {
          try {
            return [r.id, await listResearchApprovals(r.id)] as const;
          } catch {
            return [r.id, []] as const;
          }
        }),
      );
      setApprovals(Object.fromEntries(entries));
    } catch (e) {
      logger.error('research-history', e);
    }
  }, [scenarioId]);

  useEffect(() => {
    if (open) void loadHistory();
  }, [open, loadHistory]);

  const prepare = async () => {
    const q = question.trim();
    if (!q) {
      showToastGlobal('info', t('workspace.research.question_required'));
      return;
    }
    setBusy(true);
    setResult(null);
    setDiff(null);
    try {
      setPrep(await prepareResearchRun(scenarioId, q));
    } catch (e) {
      logger.error('research-prepare', e);
      showToastGlobal('error', e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const run = async () => {
    if (!prep) return;
    setBusy(true);
    try {
      const r = await executeResearchRun(scenarioId, { question: prep.question });
      setResult(r);
      await loadHistory();
    } catch (e) {
      logger.error('research-run', e);
      showToastGlobal('error', e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const compare = async () => {
    if (!baseA || !baseB) {
      showToastGlobal('info', t('workspace.research.compare_need_two'));
      return;
    }
    try {
      const r = await compareResearchRuns(baseA, baseB);
      setDiff(r.diff);
    } catch (e) {
      logger.error('research-compare', e);
      showToastGlobal('error', e instanceof Error ? e.message : String(e));
      setDiff(null);
    }
  };

  const decide = async (runId: string, decision: 'APPROVED' | 'REJECTED') => {
    if (decision === 'APPROVED' && !refInput.trim()) {
      showToastGlobal('info', t('workspace.research.ref_required'));
      return;
    }
    setApproving(runId);
    try {
      await approveResearchRun(runId, {
        decision,
        approvalRef: decision === 'APPROVED' ? refInput.trim() : undefined,
        reason: decision === 'REJECTED' ? t('workspace.research.rejected_default') : undefined,
      });
      showToastGlobal('success', t('workspace.research.approved_done'));
      await loadHistory();
    } catch (e) {
      logger.error('research-approve', e);
      showToastGlobal('error', e instanceof Error ? e.message : String(e));
    } finally {
      setApproving(null);
    }
  };

  /** 谱系树缩进：按 parent_run_id 链深度推算（无父=0）。 */
  const depthOf = (id?: string): number => {
    let depth = 0;
    let cursor = id;
    const byId = new Map(history.map((h) => [h.id, h]));
    const guard = new Set<string>();
    while (cursor && depth < 12) {
      if (guard.has(cursor)) break;
      guard.add(cursor);
      const row = byId.get(cursor);
      if (!row || !row.parent_run_id) break;
      cursor = row.parent_run_id;
      depth += 1;
    }
    return depth;
  };

  return (
    <section className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg`}>
      <button
        type="button"
        onClick={() => toggle(!open)}
        className={`w-full flex items-center justify-between px-4 py-2.5 text-left cursor-pointer ${styles.cardText}`}
      >
        <span className="flex items-center gap-1.5 text-sm font-bold">
          <FlaskConical size={14} />
          {t('workspace.research.title')}
          <span className={`text-[10px] font-normal ${styles.cardTextMuted}`}>
            {t('workspace.research.level')}
          </span>
        </span>
        <span className={`text-[11px] ${styles.cardTextMuted}`}>
          {open ? t('workspace.research.collapse') : t('workspace.research.expand')}
        </span>
      </button>

      {open && (
        <div className="px-4 pb-4 space-y-3">
          <p className={`text-[11px] ${styles.cardTextMuted} leading-relaxed`}>
            {t('workspace.research.hint')}
          </p>

          <textarea
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            placeholder={t('workspace.research.question_ph')}
            rows={2}
            disabled={busy}
            className={`w-full text-xs rounded border px-2.5 py-2 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText} resize-y disabled:opacity-60`}
          />
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => void prepare()}
              disabled={busy}
              className={`inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-bold rounded border cursor-pointer disabled:opacity-50 ${styles.cardBg} ${styles.cardText} ${styles.cardBorder}`}
            >
              <Search size={12} />
              {t('workspace.research.prepare')}
            </button>
            <button
              type="button"
              onClick={() => void run()}
              disabled={busy || !prep}
              className={`inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-bold rounded border cursor-pointer disabled:opacity-50 ${styles.accentBg} ${styles.accentSolidText} ${styles.accentBorder}`}
            >
              <Play size={12} />
              {busy ? t('workspace.research.running') : t('workspace.research.run')}
            </button>
          </div>

          {prep && (
            <div className={`text-[11px] ${styles.cardTextMuted} space-y-1`}>
              <div>
                {t('workspace.research.executor')}: <span className="font-mono">{prep.executor.kind}</span>
                {' · '}
                {t('workspace.research.code_exec')}: {String(prep.executor.codeExecution)}
                {' · '}
                {t('workspace.research.network')}: {String(prep.executor.network)}
              </div>
              <div>
                {t('workspace.research.budget')}: {prep.budget.maxLoops} × {prep.budget.maxSecondsPerLoop}s
                {' / '}
                {prep.budget.maxTotalSeconds}s
              </div>
              <div>
                {t('workspace.research.bindings')}: {prep.context.bindingCount ?? 0}
              </div>
            </div>
          )}

          {result && (
            <div className="space-y-2">
              <div className={`text-[11px] ${styles.cardTextMuted} flex flex-wrap gap-2`}>
                <span className="font-mono">{t('workspace.research.stop')}: {result.stopReason}</span>
                {result.sotaRunId && <span className="font-mono">SOTA: {result.sotaRunId}</span>}
                {result.candidateOnly && (
                  <span className={`px-1.5 py-0.5 rounded border ${styles.accentBorder} ${styles.accentText}`}>
                    {t('workspace.research.candidate_only')}
                  </span>
                )}
              </div>
              <ol className="space-y-2">
                {result.rounds.map((r) => (
                  <li key={r.runId ?? r.loopIndex} className={`border ${styles.cardBorder} rounded p-2`}>
                    <div className={`text-[11px] font-bold ${styles.cardText} flex flex-wrap gap-2`}>
                      <span>#{r.loopIndex}</span>
                      {r.runId && <span className="font-mono">{r.runId}</span>}
                      {r.accepted && <span className={styles.accentText}>ACCEPTED</span>}
                      {r.degraded && <span className={styles.cardTextMuted}>DEGRADED</span>}
                      {r.detail && <span className={styles.cardTextMuted}>{r.detail}</span>}
                    </div>
                    {r.evaluation?.evidence && (
                      <div className={`text-[10px] ${styles.cardTextMuted} mt-1`}>{r.evaluation.evidence}</div>
                    )}
                    {r.advice?.summary && (
                      <div className={`text-[10px] ${styles.cardTextMuted} mt-1`}>{r.advice.summary}</div>
                    )}
                  </li>
                ))}
              </ol>
            </div>
          )}

          {history.length > 0 && (
            <div className={`text-[11px] ${styles.cardTextMuted} space-y-1`}>
              <div className="font-bold">{t('workspace.research.history')}</div>

              {/* 谱系树：按 parent_run_id 缩进，SOTA 行高亮 */}
              <ol className="space-y-1">
                {history.map((row) => {
                  const depth = depthOf(row.parent_run_id ?? undefined);
                  const isSota = !!result?.sotaRunId && row.id === result.sotaRunId;
                  const hist = approvals[row.id] ?? [];
                  const latest = hist[0];
                  return (
                    <li
                      key={row.id}
                      style={{ marginLeft: `${depth * 14}px` }}
                      className={`border rounded px-2 py-1 ${styles.cardBorder} ${
                        isSota ? `border-l-2 ${styles.accentBorder}` : ''
                      }`}
                    >
                      <div className="flex flex-wrap items-center gap-1.5">
                        <span className="font-mono">#{row.loop_index ?? '-'}</span>
                        <span className="font-mono break-all">{row.id}</span>
                        {isSota && (
                          <span className={`px-1 rounded border ${styles.accentBorder} ${styles.accentText}`}>
                            SOTA
                          </span>
                        )}
                        <span>{row.status}</span>
                        {row.stop_reason && (
                          <span className="font-mono">· {row.stop_reason}</span>
                        )}
                        {latest && (
                          <span className={latest.decision === 'APPROVED' ? styles.accentText : undefined}>
                            · {latest.decision}@{latest.approver}
                          </span>
                        )}
                      </div>
                      <div className="flex flex-wrap items-center gap-1 mt-0.5">
                        <button
                          type="button"
                          disabled={approving === row.id}
                          onClick={() => void decide(row.id, 'APPROVED')}
                          className={`inline-flex items-center gap-1 px-1.5 py-0.5 text-[10px] rounded border cursor-pointer disabled:opacity-50 ${styles.cardBg} ${styles.cardBorder}`}
                        >
                          <BadgeCheck size={10} />
                          {t('workspace.research.approve')}
                        </button>
                        <button
                          type="button"
                          disabled={approving === row.id}
                          onClick={() => void decide(row.id, 'REJECTED')}
                          className={`inline-flex items-center gap-1 px-1.5 py-0.5 text-[10px] rounded border cursor-pointer disabled:opacity-50 ${styles.cardBg} ${styles.cardBorder}`}
                        >
                          <XCircle size={10} />
                          {t('workspace.research.reject')}
                        </button>
                        {hist.length > 0 && (
                          <span className="font-mono text-[10px]">
                            {t('workspace.research.approval_count')}: {hist.length}
                          </span>
                        )}
                      </div>
                    </li>
                  );
                })}
              </ol>

              <div className="flex flex-wrap items-center gap-1.5">
                <input
                  value={refInput}
                  onChange={(e) => setRefInput(e.target.value)}
                  placeholder={t('workspace.research.ref_ph')}
                  className={`text-[10px] rounded border px-1.5 py-0.5 w-48 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
                />
                <span className="text-[10px]">{t('workspace.research.ref_hint')}</span>
              </div>

              <div className="flex flex-wrap items-center gap-1.5">
                <select
                  value={baseA}
                  onChange={(e) => setBaseA(e.target.value)}
                  className={`text-[10px] rounded border px-1 py-0.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
                >
                  <option value="">A</option>
                  {history.map((h) => (
                    <option key={h.id} value={h.id}>{h.loop_index} · {h.id}</option>
                  ))}
                </select>
                <select
                  value={baseB}
                  onChange={(e) => setBaseB(e.target.value)}
                  className={`text-[10px] rounded border px-1 py-0.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
                >
                  <option value="">B</option>
                  {history.map((h) => (
                    <option key={h.id} value={h.id}>{h.loop_index} · {h.id}</option>
                  ))}
                </select>
                <button
                  type="button"
                  onClick={() => void compare()}
                  className={`inline-flex items-center gap-1 px-2 py-0.5 text-[10px] font-bold rounded border cursor-pointer ${styles.cardBg} ${styles.cardBorder}`}
                >
                  <GitCompare size={10} />
                  {t('workspace.research.compare')}
                </button>
              </div>
              {diff && (
                <table className="w-full text-[10px]">
                  <thead>
                    <tr>
                      <th className="text-left">{t('workspace.research.field')}</th>
                      <th className="text-left">A</th>
                      <th className="text-left">B</th>
                    </tr>
                  </thead>
                  <tbody>
                    {Object.entries(diff).map(([field, cell]) => (
                      <tr key={field}>
                        <td className="font-mono">{field}</td>
                        <td className="break-all">{fmt(cell.left)}</td>
                        <td className="break-all">{fmt(cell.right)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>
          )}
        </div>
      )}
    </section>
  );
}

function fmt(v: unknown): string {
  if (v == null) return '—';
  if (typeof v === 'object') {
    try {
      return JSON.stringify(v);
    } catch {
      return String(v);
    }
  }
  return String(v);
}
