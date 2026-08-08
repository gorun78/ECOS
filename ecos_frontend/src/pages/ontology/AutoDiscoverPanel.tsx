/**
 * AutoDiscoverPanel — 自动发现三步向导面板
 *
 * Step 1: 选择数据源 + Schema → 下一步
 * Step 2: 预览候选实体 → 勾选 → 确认生成
 * Step 3: 生成结果 → 进入设计器
 *
 * ≤400 行
 * @license Apache-2.0
 */

import React, { useState, useEffect, useCallback, useMemo } from 'react';
import {
  X, Sparkles, Database, Check,
  ChevronLeft, ChevronRight, Loader2, AlertCircle, Search,
} from 'lucide-react';
import { useLanguage } from '../../components/LanguageContext';
import { useTheme } from '../../components/ThemeContext';
import {
  previewEntities, autoDiscover, fetchOntologySources,
} from '../../services/ontologyApi';
import type { AutoDiscoverEntityPreview, AutoDiscoverResult } from '../../services/ontologyApi';
import AutoDiscoverPreview from './AutoDiscoverPreview';

type Step = 1 | 2 | 3;

interface SourceItem {
  datasourceId: string;
  datasourceName: string;
  datasourceType: string;
  schemas?: string[];
}

interface AutoDiscoverPanelProps {
  domainCode: string;
  onClose: () => void;
  onEnterDesigner?: () => void;
}

/** i18n 插值辅助 */
function ti(t: (k: string) => string, key: string, vars: Record<string, string> = {}): string {
  let s = t(key);
  for (const [k, v] of Object.entries(vars)) s = s.replace(`{${k}}`, v);
  return s;
}

// ── 步骤指示器（内联） ──
function StepDot({ num, current, done, label }: { num: number; current: boolean; done: boolean; label: string }) {
  return (
    <div className="flex flex-col items-center gap-1">
      <div className={`w-7 h-7 rounded-full flex items-center justify-center text-[11px] font-semibold transition ${
        current ? 'bg-indigo-500 text-white' : done ? 'bg-indigo-500/30 text-indigo-300' : 'bg-slate-800 text-slate-600'
      }`}>
        {done ? <Check size={13} /> : num}
      </div>
      <span className={`text-[10px] ${current ? 'text-indigo-400' : 'text-slate-500'}`}>{label}</span>
    </div>
  );
}

// ── 通用样式 helpers ──
const inputCls = (styles: ReturnType<typeof useTheme>['styles']) =>
  `w-full px-3 py-2 rounded-lg border text-sm ${styles.inputBorder} ${styles.inputBg} ${styles.cardText} focus:outline-none focus:border-indigo-500/40 transition`;

const errBox = (msg: string) => (
  <div className="flex items-start gap-2 px-3 py-2.5 rounded-lg bg-red-500/10 border border-red-500/20">
    <AlertCircle size={13} className="text-red-400 mt-0.5 shrink-0" />
    <p className="text-xs text-red-400">{msg}</p>
  </div>
);

// ── 主组件 ──────────────────────────────────────────────────

export default function AutoDiscoverPanel({ domainCode, onClose, onEnterDesigner }: AutoDiscoverPanelProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const [step, setStep] = useState<Step>(1);

  // Step 1
  const [sources, setSources] = useState<SourceItem[]>([]);
  const [srcLoading, setSrcLoading] = useState(false);
  const [srcError, setSrcError] = useState<string | null>(null);
  const [selectedSrcId, setSelectedSrcId] = useState('');
  const [selectedSchema, setSelectedSchema] = useState('');

  // Step 2
  const [candidates, setCandidates] = useState<AutoDiscoverEntityPreview[]>([]);
  const [selNames, setSelNames] = useState<Set<string>>(new Set());
  const [prevLoading, setPrevLoading] = useState(false);
  const [prevError, setPrevError] = useState<string | null>(null);

  // Step 3
  const [result, setResult] = useState<AutoDiscoverResult | null>(null);
  const [genLoading, setGenLoading] = useState(false);
  const [genError, setGenError] = useState<string | null>(null);

  const selectedSource = useMemo(() => sources.find((s) => s.datasourceId === selectedSrcId), [sources, selectedSrcId]);

  useEffect(() => {
    setSrcLoading(true);
    fetchOntologySources()
      .then((list) => { setSources(list || []); setSrcLoading(false); })
      .catch((err) => { setSrcError(err?.message || t('ontology.autoDiscover.loading')); setSrcLoading(false); });
  }, []);

  const handleNextToPreview = useCallback(async () => {
    if (!selectedSrcId) return;
    setPrevLoading(true); setPrevError(null); setSelNames(new Set());
    try {
      const list = await previewEntities(domainCode, { datasourceId: selectedSrcId });
      setCandidates(list || []); setStep(2);
    } catch (err: any) {
      setPrevError(err?.message || t('ontology.autoDiscover.loading'));
    } finally { setPrevLoading(false); }
  }, [selectedSrcId, domainCode, t]);

  const handleToggle = useCallback((name: string) => {
    setSelNames((prev) => { const n = new Set(prev); n.has(name) ? n.delete(name) : n.add(name); return n; });
  }, []);

  const handleToggleAll = useCallback((selectAll: boolean) => {
    setSelNames(selectAll ? new Set(candidates.map((c) => c.resourceName)) : new Set());
  }, [candidates]);

  const handleGenerate = useCallback(async () => {
    const names = Array.from(selNames);
    if (!names.length) return;
    setGenLoading(true); setGenError(null);
    try {
      const res = await autoDiscover(domainCode, { datasourceId: selectedSrcId, resourceNames: names });
      setResult(res); setStep(3);
    } catch (err: any) {
      setGenError(err?.message || t('ontology.autoDiscover.generating'));
    } finally { setGenLoading(false); }
  }, [domainCode, selectedSrcId, selNames, t]);

  const steps = [t('ontology.autoDiscover.step1'), t('ontology.autoDiscover.step2'), t('ontology.autoDiscover.step3')];

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm">
      <div className={`w-[640px] max-h-[90vh] rounded-xl border ${styles.cardBorder} ${styles.cardBg} shadow-2xl flex flex-col overflow-hidden`}>

        {/* 标题栏 */}
        <div className={`flex items-center justify-between px-5 py-3.5 border-b ${styles.cardBorder} shrink-0`}>
          <div className="flex items-center gap-2.5">
            <Sparkles size={16} className="text-indigo-400" />
            <h2 className={`text-sm font-semibold ${styles.cardText}`}>{t('ontology.autoDiscover.title')}</h2>
          </div>
          <button onClick={onClose} className="p-1 rounded hover:bg-slate-700/50 text-slate-500 hover:text-slate-300 transition"><X size={16} /></button>
        </div>

        {/* 步骤指示器 */}
        <div className="px-5 pt-4 shrink-0">
          <div className="flex items-center justify-center gap-1 mb-6">
            {steps.map((label, i) => (
              <React.Fragment key={i}>
                {i > 0 && <div className={`h-px w-8 ${i + 1 <= step ? 'bg-indigo-500/50' : 'bg-slate-700'}`} />}
                <StepDot num={i + 1} current={step === i + 1} done={step > i + 1} label={label} />
              </React.Fragment>
            ))}
          </div>
        </div>

        {/* 内容区 */}
        <div className="flex-1 overflow-y-auto px-5 py-2">
          {step === 1 && (
            <div className="space-y-4">
              {srcError && errBox(srcError)}
              {srcLoading && (
                <div className="flex items-center justify-center py-8">
                  <Loader2 size={20} className="animate-spin text-slate-500" />
                  <span className={`ml-2 text-sm ${styles.muted}`}>{t('ontology.autoDiscover.loading')}</span>
                </div>
              )}
              {!srcLoading && !srcError && sources.length === 0 && (
                <div className="flex flex-col items-center justify-center py-10">
                  <Database size={32} className="mb-3 opacity-20 text-slate-500" />
                  <p className={`text-sm ${styles.muted}`}>{t('ontology.autoDiscover.noDataSource')}</p>
                </div>
              )}
              {sources.length > 0 && (
                <>
                  <div className="space-y-2">
                    <label className={`text-[10px] font-medium uppercase tracking-wide ${styles.muted}`}>{t('ontology.autoDiscover.selectSource')}</label>
                    <select value={selectedSrcId} onChange={(e) => { setSelectedSrcId(e.target.value); setSelectedSchema(''); }} className={inputCls(styles)}>
                      <option value="" disabled>{t('ontology.autoDiscover.selectSource')}</option>
                      {sources.map((src) => (
                        <option key={src.datasourceId} value={src.datasourceId}>{src.datasourceName} ({src.datasourceType})</option>
                      ))}
                    </select>
                  </div>
                  {selectedSource?.schemas && selectedSource.schemas.length > 0 && (
                    <div className="space-y-2">
                      <label className={`text-[10px] font-medium uppercase tracking-wide ${styles.muted}`}>{t('ontology.autoDiscover.selectSchema')}</label>
                      <select value={selectedSchema} onChange={(e) => setSelectedSchema(e.target.value)} className={inputCls(styles)}>
                        <option value="">{t('ontology.autoDiscover.allSchemas')}</option>
                        {selectedSource.schemas.map((s) => <option key={s} value={s}>{s}</option>)}
                      </select>
                    </div>
                  )}
                  <p className={`text-[11px] leading-relaxed ${styles.muted}`}>{t('ontology.autoDiscover.tryAutoDiscover')}</p>
                </>
              )}
            </div>
          )}

          {step === 2 && (
            <div className="space-y-4">
              {prevError && errBox(prevError)}
              {selectedSource && (
                <div className="flex items-center gap-2 px-3 py-2 rounded-lg bg-indigo-500/5 border border-indigo-500/10">
                  <Search size={12} className="text-indigo-400 shrink-0" />
                  <span className={`text-[11px] ${styles.cardText}`}>{selectedSource.datasourceName}</span>
                  <span className="text-[10px] px-1.5 py-0.5 rounded bg-slate-700/40 text-slate-500">
                    {ti(t, 'ontology.autoDiscover.resourceCount', { count: String(candidates.length) })}
                  </span>
                </div>
              )}
              <AutoDiscoverPreview candidates={candidates} selectedNames={selNames} onToggle={handleToggle} onToggleAll={handleToggleAll} loading={prevLoading} />
            </div>
          )}

          {step === 3 && (
            <div className="flex flex-col items-center py-8 space-y-5">
              {genError && errBox(genError)}
              <div className="w-16 h-16 rounded-full bg-emerald-500/10 border border-emerald-500/20 flex items-center justify-center">
                <Check size={28} className="text-emerald-400" />
              </div>
              <div className="text-center">
                <h3 className={`text-base font-semibold ${styles.cardText} mb-1`}>{t('ontology.autoDiscover.generateSuccess')}</h3>
                {result && (
                  <p className={`text-xs ${styles.muted}`}>
                    {ti(t, 'ontology.autoDiscover.createdCount', {
                      entities: String(result.entityCount), properties: String(result.propertyCount), mappings: String(result.mappingCount),
                    })}
                  </p>
                )}
              </div>
            </div>
          )}
        </div>

        {/* 底部按钮 */}
        <div className={`flex items-center justify-between px-5 py-3.5 border-t ${styles.cardBorder} shrink-0`}>
          <div>
            {step > 1 && step < 3 && (
              <button onClick={() => setStep((prev) => (prev - 1) as Step)}
                className="flex items-center gap-1 px-3 py-2 rounded-lg text-xs border border-slate-700 text-slate-400 hover:text-slate-300 hover:border-slate-600 transition">
                <ChevronLeft size={13} />{t('ontology.autoDiscover.back')}
              </button>
            )}
          </div>
          <div className="flex items-center gap-2 ml-auto">
            {step === 1 && (
              <button onClick={handleNextToPreview} disabled={!selectedSrcId || prevLoading}
                className="flex items-center gap-1.5 px-4 py-2 rounded-lg text-xs font-medium bg-indigo-600 hover:bg-indigo-500 text-white disabled:opacity-40 disabled:cursor-not-allowed transition">
                {prevLoading ? <Loader2 size={13} className="animate-spin" /> : <ChevronRight size={13} />}
                {t('ontology.autoDiscover.next')}
              </button>
            )}
            {step === 2 && (
              <button onClick={handleGenerate} disabled={selNames.size === 0 || genLoading}
                className="flex items-center gap-1.5 px-4 py-2 rounded-lg text-xs font-medium bg-indigo-600 hover:bg-indigo-500 text-white disabled:opacity-40 disabled:cursor-not-allowed transition">
                {genLoading ? <Loader2 size={13} className="animate-spin" /> : <Sparkles size={13} />}
                {t('ontology.autoDiscover.confirmGenerate')}
              </button>
            )}
            {step === 3 && (
              <button onClick={() => { onEnterDesigner?.(); onClose(); }}
                className="flex items-center gap-1.5 px-4 py-2 rounded-lg text-xs font-medium bg-indigo-600 hover:bg-indigo-500 text-white transition">
                {t('ontology.autoDiscover.enterDesigner')}<ChevronRight size={13} />
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
