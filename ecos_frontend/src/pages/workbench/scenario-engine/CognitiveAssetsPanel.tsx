/**
 * 场景引擎配置 · 认知资产（信念 / 假设 / 证据）分类维护（2026-11-27 用户裁决）。
 *
 * <p>口径：只读分类视图 + 既有写动作。后端 {@code /api/v1/cognitive/*} 只有列表/详情/
 * 登记与状态类写动作，无 category/tags 与更新/删除接口 ⇒ 分类维度只做展示与筛选，
 * 已有记录的分类不可改（不伪造可编辑能力）。</p>
 *
 * <p>通道：一律走 {@code services/cognitiveEngineApi.ts} 既有包装，页面零 `/api` 字面量、
 * 不新增 service 常量（认知族单源门禁）。</p>
 */
import React, { useCallback, useEffect, useState } from 'react';
import { AlertTriangle, Plus, ShieldQuestion } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import {
  createBelief,
  createEvidence,
  createHypothesis,
  fetchBeliefs,
  fetchEvidence,
  fetchHypotheses,
  invalidateHypothesis,
  overrideBelief,
  updateBeliefByEvidence,
} from '../../../services/cognitiveEngineApi';
import { showToastGlobal } from '../../../components/common/Toast';
import logger from '../../../services/logger';

type CogType = 'belief' | 'hypothesis' | 'evidence';

interface OutcomeProbVO { outcome?: string; prob?: number }

interface BeliefVO {
  id?: string;
  variableName?: string;
  domain?: string;
  distribution?: OutcomeProbVO[];
  version?: number;
  manualOverride?: boolean;
  status?: string;
}

interface HypothesisVO {
  id?: string;
  statement?: string;
  domain?: string;
  metricRef?: string;
  evidenceIds?: string[];
  status?: string;
}

interface EvidenceVO {
  id?: string;
  sourceType?: string;
  sourceRef?: string;
  confidence?: number;
  conflict?: boolean;
  status?: string;
}

/** 把后端返回的未知形态窄化为数组（列表端点返回 data: []）。 */
function asRows<T>(value: unknown): T[] {
  return Array.isArray(value) ? (value as T[]) : [];
}

/** 概率分布 → "none:0.70 / mild:0.30" 摘要 */
function distText(list?: OutcomeProbVO[]): string {
  if (!Array.isArray(list) || list.length === 0) return '—';
  return list.map((p) => `${p.outcome ?? '?'}:${Number(p.prob ?? 0).toFixed(2)}`).join(' / ');
}

/** "none:0.7, mild:0.3" → 分布数组；解析失败返回 null */
function parseDist(text: string): Array<{ outcome: string; prob: number }> | null {
  const parts = text.split(/[,，]/).map((s) => s.trim()).filter(Boolean);
  if (parts.length === 0) return null;
  const out: Array<{ outcome: string; prob: number }> = [];
  for (const part of parts) {
    const [outcome, probRaw] = part.split(':').map((s) => s.trim());
    const prob = Number(probRaw);
    if (!outcome || !Number.isFinite(prob)) return null;
    out.push({ outcome, prob });
  }
  return out;
}

export default function CognitiveAssetsPanel() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [type, setType] = useState<CogType>('belief');

  const types: Array<{ id: CogType; key: string }> = [
    { id: 'belief', key: 'workspace.enginecfg.cog.type.belief' },
    { id: 'hypothesis', key: 'workspace.enginecfg.cog.type.hypothesis' },
    { id: 'evidence', key: 'workspace.enginecfg.cog.type.evidence' },
  ];

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-1.5 flex-wrap">
        {types.map((item) => {
          const active = type === item.id;
          const cls = active
            ? `${styles.accentBg} ${styles.accentSolidText} ${styles.accentBorder}`
            : `${styles.cardBg} ${styles.cardTextMuted} ${styles.cardBorder}`;
          return (
            <button
              key={item.id}
              type="button"
              onClick={() => setType(item.id)}
              aria-pressed={active}
              className={`text-xs font-bold px-2.5 py-1 rounded border cursor-pointer ${cls}`}
            >
              {t(item.key)}
            </button>
          );
        })}
        <span className={`text-[10px] ${styles.cardTextMuted} ml-auto`}>
          {t('workspace.enginecfg.cog.readonlyHint')}
        </span>
      </div>

      {type === 'belief' && <BeliefsSection />}
      {type === 'hypothesis' && <HypothesesSection />}
      {type === 'evidence' && <EvidenceSection />}
    </div>
  );
}

/** 通用：加载条 / 错误横幅 / 空态 */
function LoadState({ loading, error, empty, onRetry }: {
  loading: boolean; error: string | null; empty: boolean; onRetry: () => void;
}) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  if (loading) return <p className={`text-xs ${styles.cardTextMuted}`}>{t('workspace.config.loading')}</p>;
  if (error) {
    return (
      <div className={`px-3 py-2 rounded border text-xs flex items-center gap-2 ${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`}>
        <AlertTriangle size={12} />
        {t('workspace.enginecfg.cog.loadError', { msg: error })}
        <button type="button" onClick={onRetry} className="ml-auto underline cursor-pointer">
          {t('workspace.l1.retry')}
        </button>
      </div>
    );
  }
  if (empty) {
    return <p className={`text-xs ${styles.cardTextMuted} inline-flex items-center gap-1`}>
      <ShieldQuestion size={12} /> {t('workspace.enginecfg.cog.empty')}
    </p>;
  }
  return null;
}

function BeliefsSection() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [domain, setDomain] = useState('');
  const [rows, setRows] = useState<BeliefVO[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [queried, setQueried] = useState(false);

  // 登记表单
  const [varName, setVarName] = useState('');
  const [newDomain, setNewDomain] = useState('');
  const [distInput, setDistInput] = useState('');
  // 每行动作
  const [overrideFor, setOverrideFor] = useState<string | null>(null);
  const [overrideDist, setOverrideDist] = useState('');
  const [overrideReason, setOverrideReason] = useState('');
  const [evidenceFor, setEvidenceFor] = useState<string | null>(null);
  const [evidenceId, setEvidenceId] = useState('');

  const load = useCallback(async () => {
    if (!domain.trim()) {
      showToastGlobal('info', t('workspace.enginecfg.cog.domainRequired'));
      return;
    }
    setLoading(true);
    setError(null);
    setQueried(true);
    try {
      setRows(asRows<BeliefVO>(await fetchBeliefs({ domain: domain.trim() })));
    } catch (e) {
      logger.error('enginecfg-beliefs-load', e);
      setError(e instanceof Error ? e.message : String(e));
      setRows([]);
    } finally {
      setLoading(false);
    }
  }, [domain, t]);

  const submitCreate = async () => {
    const distribution = parseDist(distInput);
    if (!varName.trim() || !newDomain.trim() || !distribution) {
      showToastGlobal('info', t('workspace.enginecfg.cog.formInvalid'));
      return;
    }
    try {
      await createBelief({ variableName: varName.trim(), domain: newDomain.trim(), distribution });
      showToastGlobal('success', t('workspace.config.saved'));
      setVarName(''); setDistInput('');
      if (domain.trim()) await load();
    } catch (e) {
      showToastGlobal('error', e instanceof Error ? e.message : String(e));
    }
  };

  const submitOverride = async (variable?: string) => {
    if (!variable) return;
    const distribution = parseDist(overrideDist);
    if (!distribution || !overrideReason.trim()) {
      showToastGlobal('info', t('workspace.enginecfg.cog.formInvalid'));
      return;
    }
    try {
      await overrideBelief(variable, { distribution, reason: overrideReason.trim() });
      showToastGlobal('success', t('workspace.config.saved'));
      setOverrideFor(null); setOverrideDist(''); setOverrideReason('');
      await load();
    } catch (e) {
      showToastGlobal('error', e instanceof Error ? e.message : String(e));
    }
  };

  const submitEvidenceUpdate = async (variable?: string) => {
    if (!variable || !evidenceId.trim()) {
      showToastGlobal('info', t('workspace.enginecfg.cog.formInvalid'));
      return;
    }
    try {
      await updateBeliefByEvidence(variable, { evidenceId: evidenceId.trim() });
      showToastGlobal('success', t('workspace.config.saved'));
      setEvidenceFor(null); setEvidenceId('');
      await load();
    } catch (e) {
      showToastGlobal('error', e instanceof Error ? e.message : String(e));
    }
  };

  return (
    <div className="space-y-4">
      <section className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4`}>
        <h3 className={`text-sm font-bold ${styles.cardText} mb-3`}>{t('workspace.enginecfg.cog.belief.list')}</h3>
        <div className="flex items-center gap-2 flex-wrap">
          <input
            value={domain}
            onChange={(e) => setDomain(e.target.value)}
            placeholder={t('workspace.enginecfg.cog.domainPh')}
            className={`text-xs rounded border px-2.5 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
          />
          <button
            type="button"
            onClick={() => void load()}
            className={`px-3 py-1.5 rounded border text-xs font-bold cursor-pointer ${styles.accentBg} ${styles.accentSolidText} ${styles.accentBorder}`}
          >
            {t('workspace.enginecfg.cog.query')}
          </button>
          <span className={`text-[10px] ${styles.cardTextMuted}`}>{t('workspace.enginecfg.cog.domainRequired')}</span>
        </div>
        <div className="mt-3">
          <LoadState loading={loading} error={error} empty={queried && !error && rows.length === 0} onRetry={() => void load()} />
          {rows.length > 0 && (
            <ul className="space-y-2">
              {rows.map((b) => (
                <li key={b.id} className={`border ${styles.cardBorder} rounded p-3 text-xs ${styles.cardText}`}>
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-bold font-mono">{b.variableName}</span>
                    <span className={`text-[10px] ${styles.cardTextMuted}`}>
                      {b.domain} · v{b.version ?? 1} · {b.status ?? '—'}{b.manualOverride ? ' · 人工覆写' : ''}
                    </span>
                  </div>
                  <p className={`mt-1 font-mono ${styles.cardTextMuted}`}>{distText(b.distribution)}</p>
                  <div className="mt-2 flex gap-2 flex-wrap">
                    <button
                      type="button"
                      onClick={() => { setOverrideFor(b.variableName ?? null); setEvidenceFor(null); }}
                      className={`text-xs ${styles.accentText} cursor-pointer`}
                    >
                      {t('workspace.enginecfg.cog.belief.override')}
                    </button>
                    <button
                      type="button"
                      onClick={() => { setEvidenceFor(b.variableName ?? null); setOverrideFor(null); }}
                      className={`text-xs ${styles.accentText} cursor-pointer`}
                    >
                      {t('workspace.enginecfg.cog.belief.updateByEvidence')}
                    </button>
                  </div>
                  {overrideFor === b.variableName && (
                    <div className="mt-2 flex items-center gap-2 flex-wrap">
                      <input
                        value={overrideDist}
                        onChange={(e) => setOverrideDist(e.target.value)}
                        placeholder={t('workspace.enginecfg.cog.distPh')}
                        className={`text-xs rounded border px-2 py-1 w-64 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
                      />
                      <input
                        value={overrideReason}
                        onChange={(e) => setOverrideReason(e.target.value)}
                        placeholder={t('workspace.enginecfg.cog.reasonPh')}
                        className={`text-xs rounded border px-2 py-1 w-48 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
                      />
                      <button type="button" onClick={() => void submitOverride(b.variableName)} className={`text-xs px-2 py-1 rounded border cursor-pointer ${styles.accentBg} ${styles.accentSolidText} ${styles.accentBorder}`}>
                        {t('workspace.config.edit')}
                      </button>
                    </div>
                  )}
                  {evidenceFor === b.variableName && (
                    <div className="mt-2 flex items-center gap-2 flex-wrap">
                      <input
                        value={evidenceId}
                        onChange={(e) => setEvidenceId(e.target.value)}
                        placeholder={t('workspace.enginecfg.cog.evidenceIdPh')}
                        className={`text-xs rounded border px-2 py-1 w-64 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
                      />
                      <button type="button" onClick={() => void submitEvidenceUpdate(b.variableName)} className={`text-xs px-2 py-1 rounded border cursor-pointer ${styles.accentBg} ${styles.accentSolidText} ${styles.accentBorder}`}>
                        {t('workspace.enginecfg.cog.belief.updateByEvidence')}
                      </button>
                    </div>
                  )}
                </li>
              ))}
            </ul>
          )}
        </div>
      </section>

      <section className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4`}>
        <h3 className={`text-sm font-bold ${styles.cardText} mb-3`}>{t('workspace.enginecfg.cog.belief.create')}</h3>
        <div className="grid gap-3 grid-cols-1 sm:grid-cols-2">
          <input value={varName} onChange={(e) => setVarName(e.target.value)} placeholder={t('workspace.enginecfg.cog.variablePh')} className={`text-xs rounded border px-2.5 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`} />
          <input value={newDomain} onChange={(e) => setNewDomain(e.target.value)} placeholder={t('workspace.enginecfg.cog.domainPh')} className={`text-xs rounded border px-2.5 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`} />
          <input value={distInput} onChange={(e) => setDistInput(e.target.value)} placeholder={t('workspace.enginecfg.cog.distPh')} className={`text-xs rounded border px-2.5 py-1.5 col-span-full ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`} />
        </div>
        <button type="button" onClick={() => void submitCreate()} className={`mt-3 inline-flex items-center gap-1 px-3 py-1.5 rounded border text-xs font-bold cursor-pointer ${styles.accentBg} ${styles.accentSolidText} ${styles.accentBorder}`}>
          <Plus size={12} /> {t('workspace.config.create')}
        </button>
      </section>
    </div>
  );
}

function HypothesesSection() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [domain, setDomain] = useState('');
  const [status, setStatus] = useState('');
  const [rows, setRows] = useState<HypothesisVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [statement, setStatement] = useState('');
  const [metricRef, setMetricRef] = useState('');
  const [evidenceCsv, setEvidenceCsv] = useState('');
  const [invalidateFor, setInvalidateFor] = useState<string | null>(null);
  const [invalidReason, setInvalidReason] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setRows(asRows<HypothesisVO>(await fetchHypotheses({
        domain: domain.trim() || undefined,
        status: status || undefined,
      })));
    } catch (e) {
      logger.error('enginecfg-hypotheses-load', e);
      setError(e instanceof Error ? e.message : String(e));
      setRows([]);
    } finally {
      setLoading(false);
    }
  }, [domain, status]);

  // 仅挂载时自动加载一次；筛选变化由「查询」按钮显式触发（避免输入 domain 逐字符打请求）
  useEffect(() => { void load(); }, []);

  const submitCreate = async () => {
    if (!statement.trim()) {
      showToastGlobal('info', t('workspace.enginecfg.cog.formInvalid'));
      return;
    }
    const evidenceIds = evidenceCsv.split(/[,，]/).map((s) => s.trim()).filter(Boolean);
    try {
      await createHypothesis({
        statement: statement.trim(),
        domain: domain.trim() || undefined,
        metricRef: metricRef.trim() || undefined,
        evidenceIds: evidenceIds.length > 0 ? evidenceIds : undefined,
      });
      showToastGlobal('success', t('workspace.config.saved'));
      setStatement(''); setMetricRef(''); setEvidenceCsv('');
      await load();
    } catch (e) {
      showToastGlobal('error', e instanceof Error ? e.message : String(e));
    }
  };

  const submitInvalidate = async (id?: string) => {
    if (!id) return;
    try {
      await invalidateHypothesis(id, invalidReason.trim() || undefined);
      showToastGlobal('success', t('workspace.config.saved'));
      setInvalidateFor(null); setInvalidReason('');
      await load();
    } catch (e) {
      showToastGlobal('error', e instanceof Error ? e.message : String(e));
    }
  };

  return (
    <div className="space-y-4">
      <section className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4`}>
        <h3 className={`text-sm font-bold ${styles.cardText} mb-3`}>{t('workspace.enginecfg.cog.hypothesis.list')}</h3>
        <div className="flex items-center gap-2 flex-wrap">
          <input value={domain} onChange={(e) => setDomain(e.target.value)} placeholder={t('workspace.enginecfg.cog.domainPh')} className={`text-xs rounded border px-2.5 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`} />
          <select value={status} onChange={(e) => setStatus(e.target.value)} className={`text-xs rounded border px-2 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}>
            <option value="">{t('workspace.enginecfg.cog.statusAll')}</option>
            {['VALID', 'INVALIDATED', 'ARCHIVED'].map((s) => <option key={s} value={s}>{s}</option>)}
          </select>
          <button type="button" onClick={() => void load()} className={`px-3 py-1.5 rounded border text-xs font-bold cursor-pointer ${styles.accentBg} ${styles.accentSolidText} ${styles.accentBorder}`}>
            {t('workspace.enginecfg.cog.query')}
          </button>
        </div>
        <div className="mt-3">
          <LoadState loading={loading} error={error} empty={!error && rows.length === 0} onRetry={() => void load()} />
          {rows.length > 0 && (
            <ul className="space-y-2">
              {rows.map((h) => (
                <li key={h.id} className={`border ${styles.cardBorder} rounded p-3 text-xs ${styles.cardText}`}>
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-bold">{h.statement || '—'}</span>
                    <span className={`text-[10px] ${styles.cardTextMuted}`}>{h.domain || '—'} · {h.status || '—'}</span>
                  </div>
                  <p className={`mt-1 ${styles.cardTextMuted}`}>
                    <span className="font-mono">{(h.id || '').slice(0, 8)}</span>
                    {h.metricRef ? ` · metric: ${h.metricRef}` : ''}
                    {Array.isArray(h.evidenceIds) && h.evidenceIds.length > 0 ? ` · 证据 ×${h.evidenceIds.length}` : ''}
                  </p>
                  <div className="mt-2 flex items-center gap-2 flex-wrap">
                    <button
                      type="button"
                      onClick={() => setInvalidateFor(invalidateFor === h.id ? null : (h.id ?? null))}
                      className={`text-xs ${styles.dangerText} cursor-pointer`}
                    >
                      {t('workspace.enginecfg.cog.hypothesis.invalidate')}
                    </button>
                    {invalidateFor === h.id && (
                      <>
                        <input
                          value={invalidReason}
                          onChange={(e) => setInvalidReason(e.target.value)}
                          placeholder={t('workspace.enginecfg.cog.reasonPh')}
                          className={`text-xs rounded border px-2 py-1 w-56 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
                        />
                        <button type="button" onClick={() => void submitInvalidate(h.id)} className={`text-xs px-2 py-1 rounded border cursor-pointer ${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`}>
                          {t('workspace.config.edit')}
                        </button>
                      </>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      </section>

      <section className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4`}>
        <h3 className={`text-sm font-bold ${styles.cardText} mb-3`}>{t('workspace.enginecfg.cog.hypothesis.create')}</h3>
        <div className="grid gap-3 grid-cols-1 sm:grid-cols-2">
          <input value={statement} onChange={(e) => setStatement(e.target.value)} placeholder={t('workspace.enginecfg.cog.statementPh')} className={`text-xs rounded border px-2.5 py-1.5 col-span-full ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`} />
          <input value={domain} onChange={(e) => setDomain(e.target.value)} placeholder={t('workspace.enginecfg.cog.domainPh')} className={`text-xs rounded border px-2.5 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`} />
          <input value={metricRef} onChange={(e) => setMetricRef(e.target.value)} placeholder={t('workspace.enginecfg.cog.metricPh')} className={`text-xs rounded border px-2.5 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`} />
          <input value={evidenceCsv} onChange={(e) => setEvidenceCsv(e.target.value)} placeholder={t('workspace.enginecfg.cog.evidenceCsvPh')} className={`text-xs rounded border px-2.5 py-1.5 col-span-full ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`} />
        </div>
        <button type="button" onClick={() => void submitCreate()} className={`mt-3 inline-flex items-center gap-1 px-3 py-1.5 rounded border text-xs font-bold cursor-pointer ${styles.accentBg} ${styles.accentSolidText} ${styles.accentBorder}`}>
          <Plus size={12} /> {t('workspace.config.create')}
        </button>
      </section>
    </div>
  );
}

function EvidenceSection() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [status, setStatus] = useState('');
  const [sourceTypeFilter, setSourceTypeFilter] = useState('');
  const [rows, setRows] = useState<EvidenceVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [formSourceType, setFormSourceType] = useState('SYSTEM_DATA');
  const [sourceRef, setSourceRef] = useState('');
  const [confidence, setConfidence] = useState('');
  const [blobText, setBlobText] = useState('{"fact":""}');

  const SOURCE_TYPES = ['SYSTEM_DATA', 'NEWS', 'EXPERT', 'PIPELINE'];

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setRows(asRows<EvidenceVO>(await fetchEvidence({
        status: status || undefined,
        sourceType: sourceTypeFilter || undefined,
      })));
    } catch (e) {
      logger.error('enginecfg-evidence-load', e);
      setError(e instanceof Error ? e.message : String(e));
      setRows([]);
    } finally {
      setLoading(false);
    }
  }, [status, sourceTypeFilter]);

  // 仅挂载时自动加载一次；筛选变化由「查询」按钮显式触发
  useEffect(() => { void load(); }, []);

  const submitCreate = async () => {
    if (!sourceRef.trim()) {
      showToastGlobal('info', t('workspace.enginecfg.cog.formInvalid'));
      return;
    }
    let blob: Record<string, unknown>;
    try {
      const parsed: unknown = JSON.parse(blobText);
      if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error('blob must be object');
      blob = parsed as Record<string, unknown>;
    } catch {
      showToastGlobal('info', t('workspace.enginecfg.cog.blobInvalid'));
      return;
    }
    const conf = confidence.trim() === '' ? undefined : Number(confidence);
    if (conf !== undefined && (!Number.isFinite(conf) || conf < 0 || conf > 1)) {
      showToastGlobal('info', t('workspace.enginecfg.cog.confidenceInvalid'));
      return;
    }
    try {
      await createEvidence({ sourceType: formSourceType, sourceRef: sourceRef.trim(), blob, confidence: conf });
      showToastGlobal('success', t('workspace.config.saved'));
      setSourceRef(''); setConfidence('');
      await load();
    } catch (e) {
      showToastGlobal('error', e instanceof Error ? e.message : String(e));
    }
  };

  return (
    <div className="space-y-4">
      <section className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4`}>
        <h3 className={`text-sm font-bold ${styles.cardText} mb-3`}>{t('workspace.enginecfg.cog.evidence.list')}</h3>
        <div className="flex items-center gap-2 flex-wrap">
          <select value={sourceTypeFilter} onChange={(e) => setSourceTypeFilter(e.target.value)} className={`text-xs rounded border px-2 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}>
            <option value="">{t('workspace.enginecfg.cog.sourceTypeAll')}</option>
            {SOURCE_TYPES.map((s) => <option key={s} value={s}>{s}</option>)}
          </select>
          <select value={status} onChange={(e) => setStatus(e.target.value)} className={`text-xs rounded border px-2 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}>
            <option value="">{t('workspace.enginecfg.cog.statusAll')}</option>
            {['ACTIVE', 'CONFLICTED', 'ARCHIVED'].map((s) => <option key={s} value={s}>{s}</option>)}
          </select>
          <button type="button" onClick={() => void load()} className={`px-3 py-1.5 rounded border text-xs font-bold cursor-pointer ${styles.accentBg} ${styles.accentSolidText} ${styles.accentBorder}`}>
            {t('workspace.enginecfg.cog.query')}
          </button>
          <span className={`text-[10px] ${styles.cardTextMuted}`}>{t('workspace.enginecfg.cog.evidence.domainHint')}</span>
        </div>
        <div className="mt-3">
          <LoadState loading={loading} error={error} empty={!error && rows.length === 0} onRetry={() => void load()} />
          {rows.length > 0 && (
            <ul className="space-y-2">
              {rows.map((ev) => (
                <li key={ev.id} className={`border ${styles.cardBorder} rounded p-3 text-xs ${styles.cardText}`}>
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-bold font-mono">{(ev.id || '').slice(0, 8)}</span>
                    <span className={`text-[10px] ${styles.cardTextMuted}`}>
                      {ev.sourceType} · {ev.status}{typeof ev.confidence === 'number' ? ` · 可信度 ${ev.confidence}` : ''}{ev.conflict ? ' · 冲突' : ''}
                    </span>
                  </div>
                  <p className={`mt-1 font-mono ${styles.cardTextMuted}`}>{ev.sourceRef || '—'}</p>
                </li>
              ))}
            </ul>
          )}
        </div>
      </section>

      <section className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4`}>
        <h3 className={`text-sm font-bold ${styles.cardText} mb-3`}>{t('workspace.enginecfg.cog.evidence.create')}</h3>
        <div className="grid gap-3 grid-cols-1 sm:grid-cols-2">
          <select value={formSourceType} onChange={(e) => setFormSourceType(e.target.value)} className={`text-xs rounded border px-2 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}>
            {SOURCE_TYPES.map((s) => <option key={s} value={s}>{s}</option>)}
          </select>
          <input value={sourceRef} onChange={(e) => setSourceRef(e.target.value)} placeholder={t('workspace.enginecfg.cog.sourceRefPh')} className={`text-xs rounded border px-2.5 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`} />
          <input value={confidence} onChange={(e) => setConfidence(e.target.value)} placeholder={t('workspace.enginecfg.cog.confidencePh')} className={`text-xs rounded border px-2.5 py-1.5 ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`} />
          <textarea
            value={blobText}
            onChange={(e) => setBlobText(e.target.value)}
            placeholder={t('workspace.enginecfg.cog.blobPh')}
            rows={3}
            className={`text-xs rounded border px-2.5 py-1.5 col-span-full font-mono ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
          />
        </div>
        <button type="button" onClick={() => void submitCreate()} className={`mt-3 inline-flex items-center gap-1 px-3 py-1.5 rounded border text-xs font-bold cursor-pointer ${styles.accentBg} ${styles.accentSolidText} ${styles.accentBorder}`}>
          <Plus size={12} /> {t('workspace.config.create')}
        </button>
      </section>
    </div>
  );
}

export { asRows, distText, parseDist };
export type { BeliefVO, HypothesisVO, EvidenceVO };
