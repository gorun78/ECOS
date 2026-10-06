/**
 * FactImportWizard — 业务事实导入向导（详细设计-02 B.5，场景批次 A 主入口）。
 * 让业务把经营事实导入并通过 DQ 门禁（DQ-F01~F11）。
 *
 * 步骤条：选类型 → 下载模板 → 上传 → 逐行错误表 → 确认发布（+ 批次列表/轮询）。
 *
 * 消费 BusinessFactController /api/v1/datanet/facts：
 *   GET  /{factType}/template · POST /{factType}/import · GET /batches/{batchId} · POST /{factType}/publish
 *
 * 关键验收点（PRD-02 §1.5/§三）：
 *  - 错误表列 = 行号 / 字段 / 规则 ID / 消息 / 修复建议，可下载（原行 + error_rule/error_message 两列）；
 *  - 逐行改后重传（幂等）；发布仅对 PASSED 行；全拒不入库直接展示错误；
 *  - 关账期行灰显不可提交；批次进行中轮询 batches/{batchId}。
 */
import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  getFactTemplate, importFacts, getFactBatch, publishFactBatch,
  FACT_TYPES,
  type FactTemplate, type FactImportResult, type FactBatchStatus, type RejectedRow, type FactType,
} from '../apiFacts';
import { useTheme } from '../../../components/ThemeContext';

type TFn = (key: string, paramsOrFallback?: Record<string, string | number> | string) => string;
type Styles = ReturnType<typeof useTheme>['styles'];

interface Props {
  showToast: (type: 'success' | 'info' | 'error', msg: string) => void;
  t: TFn;
  locale?: string;
}

const STEPS = ['type', 'template', 'upload', 'errors', 'publish'] as const;
type StepName = (typeof STEPS)[number];
const STEP_KEY: Record<StepName, string> = {
  type: 'dw.facts.stepType', template: 'dw.facts.stepTemplate', upload: 'dw.facts.stepUpload',
  errors: 'dw.facts.stepErrors', publish: 'dw.facts.stepPublish',
};
const PERIOD_KEY: Record<FactType, string> = {
  attribution: 'effective_from', stage: 'period', resource: 'period', cost: 'period',
};
const CLOSED_YEAR = '2099';

// ─── CSV 工具 ────────────────────────────────────────────────
function toCsv(rows: Record<string, unknown>[], columns: string[]): string {
  const esc = (v: unknown) => {
    const s = v === null || v === undefined ? '' : String(v);
    return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  const lines = [columns.join(',')];
  for (const r of rows) lines.push(columns.map(c => esc(r[c])).join(','));
  return lines.join('\n');
}

/** 简单 CSV 解析（首行表头，支持引号字段 / 换行 / 转义引号）。 */
function parseCsv(text: string): Record<string, unknown>[] {
  const matrix: string[][] = [];
  let cur: string[] = [];
  let field = '';
  let inQuotes = false;
  for (let i = 0; i < text.length; i++) {
    const ch = text[i];
    if (inQuotes) {
      if (ch === '"') {
        if (text[i + 1] === '"') { field += '"'; i++; }
        else inQuotes = false;
      } else field += ch;
    } else if (ch === '"') {
      inQuotes = true;
    } else if (ch === ',') {
      cur.push(field); field = '';
    } else if (ch === '\n' || ch === '\r') {
      if (ch === '\r' && text[i + 1] === '\n') i++;
      cur.push(field); field = '';
      if (cur.some(x => x !== '')) matrix.push(cur);
      cur = [];
    } else field += ch;
  }
  cur.push(field);
  if (cur.some(x => x !== '')) matrix.push(cur);
  if (matrix.length < 2) return [];
  const header = matrix[0].map(h => h.trim());
  return matrix.slice(1).map(cells => {
    const o: Record<string, unknown> = {};
    header.forEach((h, i) => {
      const v = (cells[i] ?? '').trim();
      if (h) o[h] = v === '' ? null : v;
    });
    return o;
  });
}

/** 触发浏览器下载（Blob）。 */
function downloadText(filename: string, text: string) {
  const blob = new Blob([text], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

/** 关账期判断（与后端 CLOSED_PERIOD_YEAR_PREFIXES 对齐）。 */
function isClosedPeriod(v: unknown): boolean {
  const s = v === null || v === undefined ? '' : String(v);
  return s.length >= 4 && s.startsWith(CLOSED_YEAR);
}

export default function FactImportWizard({ showToast, t }: Props) {
  const { styles } = useTheme();
  const [step, setStep] = useState<StepName>('type');
  const [factType, setFactType] = useState<FactType>('stage');
  const [batchName, setBatchName] = useState('');
  const [template, setTemplate] = useState<FactTemplate | null>(null);
  const [templateLoading, setTemplateLoading] = useState(false);
  const [uploadedRows, setUploadedRows] = useState<Record<string, unknown>[]>([]);
  const [importing, setImporting] = useState(false);
  const [result, setResult] = useState<FactImportResult | null>(null);
  const [batch, setBatch] = useState<FactBatchStatus | null>(null);
  const [published, setPublished] = useState<number | null>(null);
  const [publishing, setPublishing] = useState(false);
  const [pastJson, setPastJson] = useState('');

  const pollRef = useRef<number | null>(null);
  const stopRef = useRef<number | null>(null);
  // §2.4 [P2] 原 `.catch(() => {})` 静默吞轮询错 → 断网/401/500 完全无信号。
  // 3 次连失败触发 stopPolling + toast，避免"以为还在轮询但一直没网络"静默疑卡。
  // startPolling 必先 stopPolling() 重置计数器；任何成功 poll 也 reset（保护瞬时抖动）。
  const pollFailRef = useRef(0);
  const pollToastedRef = useRef(false);

  useEffect(() => () => {
    if (pollRef.current) window.clearInterval(pollRef.current);
    if (stopRef.current) window.clearTimeout(stopRef.current);
  }, []);

  // 选类型 → 拉模板 + 预填批次名
  useEffect(() => {
    if (step !== 'type') return;
    let cancel = false;
    setTemplateLoading(true);
    getFactTemplate(factType)
      .then(tpl => { if (!cancel) { setTemplate(tpl); setBatchName(b => b || tpl.batchNameSuggested); } })
      .catch(e => { if (!cancel) showToast('error', t('dw.facts.loadFailed').replace('{msg}', (e as Error).message)); })
      .finally(() => { if (!cancel) setTemplateLoading(false); });
    return () => { cancel = true; };
  }, [factType, step, showToast, t]);

  const stopPolling = useCallback(() => {
    if (pollRef.current) { window.clearInterval(pollRef.current); pollRef.current = null; }
    if (stopRef.current) { window.clearTimeout(stopRef.current); stopRef.current = null; }
    pollFailRef.current = 0;
    pollToastedRef.current = false;
  }, []);

  const pollBatch = useCallback((fact: FactType, batchId: string) => {
    getFactBatch(fact, batchId)
      .then(b => {
        setBatch(b);
        pollFailRef.current = 0;
        pollToastedRef.current = false;
      })
      .catch((e: unknown) => {
        pollFailRef.current += 1;
        if (pollFailRef.current < 3 || pollToastedRef.current) return;
        pollToastedRef.current = true;
        stopPolling();
        const msg = e instanceof Error ? e.message : String(e);
        showToast('error', t('dw.facts.pollFailed').replace('{msg}', msg));
      });
  }, [showToast, t, stopPolling]);

  const startPolling = useCallback((fact: FactType, batchId: string) => {
    stopPolling();
    pollBatch(fact, batchId);
    pollRef.current = window.setInterval(() => pollBatch(fact, batchId), 2500);
    stopRef.current = window.setTimeout(() => { if (pollRef.current) { window.clearInterval(pollRef.current); pollRef.current = null; } }, 30000);
  }, [pollBatch, stopPolling]);

  const downloadTemplateCsv = useCallback(() => {
    if (!template) return;
    const example = template.rows.find(r => r.__example__) as Record<string, unknown> | undefined;
    const body: Record<string, unknown> = {};
    for (const c of template.columns) body[c] = example ? example[c] ?? '' : '';
    downloadText(`template-${factType}.csv`, toCsv([body], template.columns));
  }, [template, factType]);

  const onFile = useCallback((file: File) => {
    const reader = new FileReader();
    reader.onload = () => {
      const rows = parseCsv(String(reader.result ?? ''));
      setUploadedRows(rows);
      setResult(null); setPublished(null);
      if (rows.length > 0) setStep('errors');
    };
    reader.readAsText(file);
  }, []);

  const onPasteJson = useCallback(() => {
    if (!pastJson.trim()) return;
    let rows: Record<string, unknown>[];
    try {
      const parsed = JSON.parse(pastJson);
      rows = Array.isArray(parsed) ? parsed : [parsed];
    } catch {
      showToast('error', t('dw.facts.invalidJson'));
      return;
    }
    setUploadedRows(rows);
    setResult(null); setPublished(null);
    setStep('errors');
  }, [pastJson, showToast, t]);

  const runImport = useCallback(async () => {
    if (!batchName.trim()) { showToast('error', t('dw.facts.batchName')); return; }
    if (uploadedRows.length === 0 || uploadedRows.length > 100) {
      showToast('error', t('dw.facts.runImport')); return;
    }
    setImporting(true);
    stopPolling();
    try {
      const r = await importFacts(factType, batchName.trim(), uploadedRows);
      setResult(r);
      if (r.accepted > 0) startPolling(factType, r.batchId);
      showToast(r.rejected.length ? 'info' : 'success',
        t('dw.facts.stats', { a: r.accepted, r: r.rejected.length }));
    } catch (e) {
      const msg = (e as Error).message;
      showToast('error', t('dw.facts.importFailed').replace('{msg}', msg));
      // 关账期（ECOS-DATA-022 / 年内 2099）→ 提示对应行
      if (/关账|close|022|2099/i.test(msg)) {
        const badIdx = uploadedRows.findIndex(row => isClosedPeriod(row[PERIOD_KEY[factType]]));
        if (badIdx >= 0) showToast('info', t('dw.facts.closedPeriod', { n: badIdx + 1 }));
      }
    } finally {
      setImporting(false);
    }
  }, [batchName, uploadedRows, factType, showToast, t, startPolling, stopPolling]);

  const runPublish = useCallback(async () => {
    if (!result) return;
    setPublishing(true);
    try {
      const r = await publishFactBatch(factType, result.batchId);
      setPublished(r.published);
      showToast('success', t('dw.facts.published', { n: r.published }));
      stopPolling();
      pollBatch(factType, result.batchId);
    } catch (e) {
      showToast('error', t('dw.facts.publishFailed').replace('{msg}', (e as Error).message));
    } finally {
      setPublishing(false);
    }
  }, [result, factType, showToast, t, pollBatch, stopPolling]);

  const downloadErrors = useCallback(() => {
    if (!result) return;
    const cols = template?.columns ?? [];
    const out = result.rejected.map(rej => {
      const orig = uploadedRows[rej.rowNo - 1] ?? {};
      return { ...orig, error_rule: rej.ruleId, error_message: `${rej.field}: ${rej.message}` };
    });
    const allCols = [...new Set([...cols, 'error_rule', 'error_message'])];
    downloadText(`errors-${factType}-${result.batchId}.csv`, toCsv(out, allCols));
  }, [result, template, uploadedRows, factType]);

  const closedRowNos = new Set(
    uploadedRows.map((r, i) => (isClosedPeriod(r[PERIOD_KEY[factType]]) ? i + 1 : -1)).filter(n => n > 0),
  );

  const canGoPublish = result?.accepted ?? 0 > 0;

  return (
    <div className={`flex-1 flex flex-col p-4 gap-3 overflow-hidden ${styles.cardText}`}>
      {/* 步骤条 */}
      <div className="flex items-center gap-1.5 flex-wrap">
        <span className="text-sm font-bold mr-2 shrink-0">{t('dw.facts.title')}</span>
        {STEPS.map((s, i) => {
          const cur = STEPS.indexOf(step);
          const done = i < cur;
          const active = i === cur;
          return (
            <React.Fragment key={s}>
              <button
                type="button"
                disabled={i > cur + 1}
                onClick={() => (i <= cur || (i === cur + 1 && i !== 3)) && setStep(s)}
                className={`text-xs px-2 py-1 rounded font-semibold transition-all ${
                  active ? `${styles.sidebarActiveBg} ${styles.sidebarActiveText}`
                    : done ? `${styles.successText} ${styles.cardBorder} border`
                      : `${styles.cardTextMuted} ${styles.cardBorder} border opacity-60`
                } disabled:cursor-not-allowed`}>
                {i + 1}. {t(STEP_KEY[s])}
              </button>
              {i < STEPS.length - 1 && <span className={`text-[10px] ${styles.cardTextMuted}`}>›</span>}
            </React.Fragment>
          );
        })}
      </div>

      {/* Step 1 — 选类型 */}
      {step === 'type' && (
        <div className="flex-1 min-h-0 overflow-auto grid md:grid-cols-2 gap-3">
          <Card styles={styles}>
            <div className="text-xs font-bold mb-2">{t('dw.facts.typeLabel')}</div>
            <div className="grid grid-cols-2 gap-2">
              {FACT_TYPES.map(ft => (
                <button key={ft} type="button" onClick={() => { setFactType(ft); setTemplate(null); setBatchName(''); }}
                    className={`text-left rounded-md p-2.5 border text-xs transition-all ${
                      factType === ft ? `${styles.sidebarActiveBg} border-l-2 ${styles.accentBorder}` : styles.cardBorder
                    } ${styles.cardBg}`}>
                  <div className="font-bold">{t(`dw.facts.type.${ft}`)}</div>
                  <div className={`text-[10px] ${styles.cardTextMuted}`}>{ft}</div>
                </button>
              ))}
            </div>
          </Card>
          <Card styles={styles} className="flex flex-col gap-3">
            <label className="flex flex-col gap-1">
              <span className="text-[10px] opacity-60">{t('dw.facts.batchName')}</span>
              <input value={batchName} onChange={e => setBatchName(e.target.value)}
                  className={`text-xs border rounded px-2 py-1.5 ${styles.cardBorder} ${styles.cardBg}`} />
            </label>
            {templateLoading ? (
              <div className="h-5 w-5 animate-spin rounded-full border-2 border-current border-t-transparent opacity-60" />
            ) : template ? (
              <div className="text-[11px] space-y-1 opacity-80">
                <div>{t('dw.facts.templatePreview')}</div>
                <div className="font-mono text-[10px] break-all">{template.columns.join(', ')}</div>
              </div>
            ) : (
              <div className="text-[11px] opacity-60">{t('dw.facts.selectTypeFirst')}</div>
            )}
            <div className="flex justify-end mt-auto">
              <StepBtn styles={styles} variant="primary" onClick={() => setStep('template')} disabled={!template}>
                {t('dw.facts.next')}
              </StepBtn>
            </div>
          </Card>
        </div>
      )}

      {/* Step 2 — 下载模板 */}
      {step === 'template' && template && (
        <div className="flex-1 min-h-0 overflow-auto">
          <Card styles={styles} className="flex flex-col gap-3">
            <div className="flex items-center gap-3 flex-wrap">
              <span className="text-xs font-bold shrink-0">{t('dw.facts.typeLabel')}: {t(`dw.facts.type.${factType}`)}</span>
              <span className={`text-[11px] ${styles.cardTextMuted}`}>{template.columns.length} {t('dw.facts.column')}</span>
              <div className="flex-1" />
              <StepBtn styles={styles} variant="primary" onClick={downloadTemplateCsv}>
                {t('dw.facts.downloadTemplate')}
              </StepBtn>
            </div>
            <div className="text-[11px] opacity-70">{t('dw.facts.templateHint')}</div>
            <div className="overflow-x-auto border rounded">
              <table className="w-full text-left text-[11px]">
                <thead>
                  <tr className={`border-b ${styles.cardBorder}`}>
                    <th className="px-2 py-1">{t('dw.facts.column')}</th>
                    <th className="px-2 py-1">{t('dw.facts.unit')}</th>
                    <th className="px-2 py-1 text-right">{t('dw.facts.required')}</th>
                  </tr>
                </thead>
                <tbody>
                  {template.columns.map(col => {
                    const req = template.required.includes(col);
                    const example = (template.rows.find(r => r.__example__) ?? {}) as Record<string, unknown>;
                    return (
                      <tr key={col} className={`border-b ${styles.cardBorder}`}>
                        <td className="px-2 py-1 font-mono">{col}{req ? ' *' : ''}</td>
                        <td className="px-2 py-1 font-mono opacity-70">{String(example[col] ?? '')}</td>
                        <td className="px-2 py-1 text-right">
                          {req
                            ? <span className={`text-[10px] font-bold ${styles.dangerText}`}>*</span>
                            : <span className={`text-[10px] opacity-50`}>·</span>}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <div className="flex justify-between mt-auto">
              <StepBtn styles={styles} onClick={() => setStep('type')}>{t('dw.facts.back')}</StepBtn>
              <StepBtn styles={styles} variant="primary" onClick={() => setStep('upload')}>{t('dw.facts.next')}</StepBtn>
            </div>
          </Card>
        </div>
      )}

      {/* Step 3 — 上传（CSV / 粘贴 JSON）→ 直接触发导入 */}
      {step === 'upload' && (
        <div className="flex-1 min-h-0 overflow-auto">
          <Card styles={styles} className="flex flex-col gap-3">
            <div className="text-xs">
              {t('dw.facts.selectedRows', { n: uploadedRows.length })}
              {' · '}{t('dw.facts.batchName')}: <span className="font-mono">{batchName || '—'}</span>
            </div>
            <div className="flex items-center gap-3 flex-wrap">
              <label className={`text-xs px-3 py-1.5 rounded cursor-pointer font-semibold ${styles.cardBorder} border bg-black/5 hover:bg-black/10`}>
                {t('dw.facts.uploadCsv')}
                <input type="file" accept=".csv,text/csv" className="hidden"
                    onChange={e => { const f = e.target.files?.[0]; if (f) onFile(f); e.target.value = ''; }} />
              </label>
              <span className={`text-[11px] ${styles.cardTextMuted}`}>{t('dw.facts.pasteJson')}</span>
            </div>
            <textarea
              value={pastJson}
              onChange={e => setPastJson(e.target.value)}
              rows={4}
              placeholder='[{"project_id":"<uuid36>","period":"2025-07", ...}]'
              className={`w-full text-[11px] font-mono border rounded px-2 py-1.5 ${styles.cardBorder} ${styles.cardBg}`}
            />
            <div className="flex justify-between mt-auto">
              <StepBtn styles={styles} onClick={() => setStep('template')}>{t('dw.facts.back')}</StepBtn>
              <div className="flex gap-2">
                <StepBtn styles={styles} variant="ghost" onClick={onPasteJson} disabled={!pastJson.trim()}>
                  {t('dw.facts.pasteJson')}
                </StepBtn>
                <StepBtn styles={styles} variant="primary" onClick={runImport} disabled={uploadedRows.length === 0 || importing}>
                  {t('dw.facts.runImport')}
                </StepBtn>
              </div>
            </div>
          </Card>
        </div>
      )}

      {/* Step 4 — 逐行错误表 + 重传 / 发布 */}
      {step === 'errors' && (
        <div className="flex-1 min-h-0 overflow-auto flex flex-col gap-3">
          {importing && (
            <div className="flex items-center gap-2 text-xs">
              <div className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent opacity-60" />
              {t('dw.facts.batchInProgress', { id: result?.batchId?.slice(0, 8) ?? '…' })}
            </div>
          )}

          {result && (
            <div className="flex items-center gap-4 text-xs flex-wrap">
              <span className={`font-bold ${styles.successText}`}>{t('dw.facts.accepted')}: {result.accepted}</span>
              <span className={`font-bold ${result.rejected.length ? styles.dangerText : styles.cardTextMuted}`}>{t('dw.facts.rejected')}: {result.rejected.length}</span>
              <span className={`text-[10px] ${styles.cardTextMuted}`}>{t('dw.facts.importedHint')}</span>
              <span className={`ml-auto text-[10px] font-mono ${styles.cardTextMuted}`} title={result.batchId}>
                {t('dw.facts.batchId')}: {result.batchId.slice(0, 8)}…
              </span>
            </div>
          )}

          {result && result.rejected.length === 0 && result.accepted === 0 && (
            <Card styles={styles}><div className={`text-xs ${styles.dangerText}`}>{t('dw.facts.allRejected')}</div></Card>
          )}

          {result && (
            <Card styles={styles} className="flex-1 min-h-0 flex flex-col">
              <div className="flex items-center justify-between mb-2 shrink-0">
                <span className="text-xs font-bold">{t('dw.facts.errorsTitle', { n: result.rejected.length })}</span>
                <StepBtn styles={styles} variant="ghost" onClick={downloadErrors} disabled={result.rejected.length === 0}>
                  {t('dw.facts.downloadErrors')}
                </StepBtn>
              </div>
              <div className="flex-1 min-h-0 overflow-auto border rounded">
                <table className="w-full text-left text-[11px]">
                  <thead className="sticky top-0">
                    <tr className={`border-b ${styles.cardBorder}`}>
                      <th className="px-2 py-1.5 text-right">{t('dw.facts.colRow')}</th>
                      <th className="px-2 py-1.5">{t('dw.facts.colField')}</th>
                      <th className="px-2 py-1.5">{t('dw.facts.colRule')}</th>
                      <th className="px-2 py-1.5">{t('dw.facts.colMsg')}</th>
                      <th className="px-2 py-1.5">{t('dw.facts.colSuggest')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {result.rejected.map((rej, i) => (
                      <ErrorRow key={i} rej={rej} closed={closedRowNos.has(rej.rowNo) || /ECOS-DATA-022/.test(rej.ruleId)} styles={styles} />
                    ))}
                    {result.rejected.length === 0 && (
                      <tr><td colSpan={5} className="h-10 text-center opacity-60">✓</td></tr>
                    )}
                  </tbody>
                </table>
              </div>
              <div className="flex justify-between mt-3 shrink-0">
                <div className="flex gap-2">
                  <StepBtn styles={styles} variant="ghost" onClick={downloadErrors} disabled={result.rejected.length === 0}>
                    {t('dw.facts.downloadErrors')}
                  </StepBtn>
                  <StepBtn styles={styles} variant="ghost"
                      onClick={() => { setResult(null); setPublished(null); stopPolling(); setStep('upload'); }}>
                    {t('dw.facts.reupload')}
                  </StepBtn>
                </div>
                <StepBtn styles={styles} variant="primary" onClick={() => setStep('publish')} disabled={!canGoPublish}>
                  {t('dw.facts.next')}
                </StepBtn>
              </div>
            </Card>
          )}

          {!result && !importing && (
            <Card styles={styles}>
              <div className="flex items-center justify-between">
                <span className="text-[11px] opacity-70">{t('dw.facts.selectedRows', { n: uploadedRows.length })}</span>
                <StepBtn styles={styles} variant="primary" onClick={runImport} disabled={uploadedRows.length === 0}>
                  {t('dw.facts.runImport')}
                </StepBtn>
              </div>
            </Card>
          )}
        </div>
      )}

      {/* Step 5 — 确认发布 */}
      {step === 'publish' && result && (
        <div className="flex-1 min-h-0 overflow-auto flex flex-col gap-3">
          <Card styles={styles} className="flex flex-col gap-2">
            <div className="flex items-center justify-between">
              <span className="text-xs font-bold">{t('dw.facts.batchId')}: <span className="font-mono">{result.batchId}</span></span>
              {pollRef.current && <span className={`text-[11px] ${styles.cardTextMuted}`}>{t('dw.facts.batchInProgress', { id: result.batchId.slice(0, 8) })}</span>}
            </div>
            <div className="flex items-center gap-4 text-xs flex-wrap">
              <span className={`font-bold ${styles.successText}`}>{t('dw.facts.accepted')}: {result.accepted}</span>
              <span className={`font-bold ${result.rejected.length ? styles.dangerText : styles.cardTextMuted}`}>{t('dw.facts.rejected')}: {result.rejected.length}</span>
              {batch && (
                <span className={`text-[11px] px-1.5 py-0.5 rounded ${batch.latestStatus === 'PUBLISHED' ? `${styles.successText} bg-black/10` : `${styles.cardTextMuted} bg-black/5`}`}>
                  {t(`dw.facts.${batch.latestStatus === 'PASSED' ? 'passed' : batch.latestStatus === 'PUBLISHED' ? 'publishedStatus' : 'pending'}`)}: {batch.accepted}
                </span>
              )}
            </div>
            <div className="flex justify-end mt-1">
              <StepBtn styles={styles} variant="primary" onClick={runPublish}
                  disabled={!batch?.publishable || published !== null || publishing}>
                {t('dw.facts.publish')}
              </StepBtn>
            </div>
            {published !== null && (
              <div className={`text-xs ${styles.successText}`}>{t('dw.facts.published', { n: published })}</div>
            )}
          </Card>

          <Card styles={styles}>
            <div className="text-xs font-bold mb-1">{t('dw.facts.batchesTitle')}</div>
            {batch ? (
              <div className="flex items-center gap-4 text-[11px] font-mono flex-wrap">
                <span>{t('dw.facts.accepted')}: {batch.accepted}</span>
                <span>{t('dw.facts.latestStatus')} = {batch.latestStatus || '—'}</span>
                {batch.updatedAt && <span>updatedAt = {String(batch.updatedAt).slice(0, 16)}</span>}
              </div>
            ) : (
              <div className="text-[11px] opacity-60">—</div>
            )}
          </Card>
        </div>
      )}
    </div>
  );
}

// ─── 子组件 / 工具 ────────────────────────────────────────────

function ErrorRow({ rej, closed, styles }: {
  rej: RejectedRow;
  closed: boolean;
  styles: Styles;
}) {
  return (
    <tr className={`${styles.cardBorder} border-b ${closed ? 'opacity-40' : ''}`}>
      <td className="px-2 py-1.5 text-right font-mono">{rej.rowNo}</td>
      <td className="px-2 py-1.5 font-mono">{rej.field}</td>
      <td className="px-2 py-1.5">
        <span className={`text-[10px] font-mono px-1.5 py-0.5 rounded ${styles.cardTextMuted} bg-black/5`}>{rej.ruleId}</span>
      </td>
      <td className="px-2 py-1.5">{rej.message}</td>
      <td className="px-2 py-1.5 opacity-80">{rej.suggestion}</td>
    </tr>
  );
}

interface CardProps {
  styles: Styles;
  children: React.ReactNode;
  className?: string;
}
function Card({ styles, children, className = '' }: CardProps) {
  return (
    <div className={`rounded-md p-3 text-xs min-w-0 ${styles.cardBg} ${styles.cardBorder} border ${className}`}>
      {children}
    </div>
  );
}

interface StepBtnProps {
  styles: Styles;
  children: React.ReactNode;
  onClick?: () => void;
  disabled?: boolean;
  variant?: 'default' | 'primary' | 'ghost';
}
function StepBtn({ styles, children, onClick, disabled, variant = 'default' }: StepBtnProps) {
  const base = 'text-xs px-3 py-1.5 rounded font-semibold transition-colors';
  const v = variant === 'primary'
    ? `${styles.accentText} ${styles.cardBorder} border`
    : variant === 'ghost'
      ? `${styles.cardBorder} border bg-black/5 hover:bg-black/10`
      : `${styles.cardBorder} border bg-black/5 hover:bg-black/10`;
  return (
    <button type="button" onClick={onClick} disabled={disabled}
        className={`${base} ${v} disabled:opacity-40 disabled:cursor-not-allowed`}>
      {children}
    </button>
  );
}
