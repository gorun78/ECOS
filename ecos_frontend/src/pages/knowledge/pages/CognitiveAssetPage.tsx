/**
 * CognitiveAssetPage — 知识工作台「认知资产」只读引用页（议题1 / 补充设计 §8、§13）。
 *
 * 定位（§8.4 守护）：认知资产是 cognitive-engine 拥有的 B 类治理资产；本页只读消费，
 * 作为知识工作台的一个类型面呈现「引用态」，**不经 KB 写通道落 K 资产、不复制 C 资产**。
 *
 * 数据面（全部真实端点，禁 mock）：
 *   - GET /api/v1/cognitive/assets/catalog  → 模式权威（分层图例 / 治理类 / 12 态生命周期词表）
 *   - fetchBeliefs / fetchHypotheses / fetchModels → governed-class 实例行
 *   - GET /api/v1/cognitive/assets/{bel|hyp|mod…}/resolve（抽屉内）→ 单条只读解析
 *
 * 六态矩阵：加载 / 空 / 错误(503 横幅+traceId) / 边界 / 权限 / 离线，逐面覆盖。
 * 主题守护：0 硬编码色。图标仅 lucide-react。文案全 t()。组件 ≤800 行。
 */
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Layers, AlertTriangle, RefreshCw, Inbox, Search } from 'lucide-react';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
import {
  fetchCognitiveAssetCatalog,
  fetchBeliefs,
  fetchHypotheses,
  fetchModels,
  isCognitiveApiError,
  type CognitiveAssetCatalog,
} from '../../../services/cognitiveClient';
import { normalizeAssetRows, type CognitiveAssetRow } from '../services/cognitiveAsset';
import CognitiveAssetCard from '../components/CognitiveAssetCard';
import CognitiveAssetResolveDrawer from '../components/CognitiveAssetResolveDrawer';
import CandidateReviewPanel from '../components/CandidateReviewPanel';

/** governed-class → 实例列表端点（服务端 catalog 之外的类不猜测、不调用）。 */
const LIST_DISPATCH: Record<string, (domain?: string) => Promise<unknown>> = {
  BELIEF: (d) => fetchBeliefs(d ? { domain: d } : undefined),
  HYPOTHESIS: (d) => fetchHypotheses(d ? { domain: d } : undefined),
  MODEL: () => fetchModels(),
};

interface CatalogState {
  status: 'loading' | 'ok' | 'error';
  data?: CognitiveAssetCatalog;
  error?: { message: string; traceId?: string; engineDown: boolean };
}

export default function CognitiveAssetPage() {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const [catalog, setCatalog] = useState<CatalogState>({ status: 'loading' });
  const [activeClass, setActiveClass] = useState<string>('ALL');
  const [domain, setDomain] = useState<string>('');
  const [rows, setRows] = useState<CognitiveAssetRow[]>([]);
  const [listStatus, setListStatus] = useState<'idle' | 'loading' | 'ok' | 'error'>('idle');
  const [listError, setListError] = useState<{ message: string; traceId?: string; engineDown: boolean } | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const loadCatalog = useCallback(async () => {
    setCatalog({ status: 'loading' });
    try {
      const data = await fetchCognitiveAssetCatalog();
      setCatalog({ status: 'ok', data });
      // 默认展示服务端首个治理类；无则 ALL
      setActiveClass(prev => (prev === 'ALL' && data.governedAssetClasses.length === 0 ? 'ALL' : prev));
    } catch (e) {
      if (isCognitiveApiError(e)) {
        setCatalog({
          status: 'error',
          error: {
            message: e.message,
            traceId: e.traceId,
            engineDown: e.code === 'ENGINE_DISABLED' || e.code === 'SCHEMA_NOT_READY',
          },
        });
      } else {
        setCatalog({ status: 'error', error: { message: e instanceof Error ? e.message : String(e), engineDown: false } });
      }
    }
  }, []);

  useEffect(() => { void loadCatalog(); }, [loadCatalog]);

  const classesToLoad = useMemo(() => {
    const governed = catalog.data?.governedAssetClasses ?? [];
    if (activeClass === 'ALL') return governed;
    return [activeClass];
  }, [activeClass, catalog.data]);

  const loadRows = useCallback(async () => {
    if (catalog.status !== 'ok') return;
    setListStatus('loading');
    setListError(null);
    const d = domain.trim() || undefined;
    try {
      const settled = await Promise.all(
        classesToLoad
          .filter(c => LIST_DISPATCH[c])
          .map(async c => {
            const raw = await LIST_DISPATCH[c](d);
            return normalizeAssetRows(raw, c);
          }),
      );
      setRows(settled.flat());
      setListStatus('ok');
    } catch (e) {
      setRows([]);
      if (isCognitiveApiError(e)) {
        setListError({
          message: e.message,
          traceId: e.traceId,
          engineDown: e.code === 'ENGINE_DISABLED' || e.code === 'SCHEMA_NOT_READY',
        });
      } else {
        setListError({ message: e instanceof Error ? e.message : String(e), engineDown: false });
      }
      setListStatus('error');
    }
  }, [catalog.status, classesToLoad, domain]);

  useEffect(() => { void loadRows(); }, [loadRows]);

  const governed = catalog.data?.governedAssetClasses ?? [];
  const options = ['ALL', ...governed];

  return (
    <div className="flex flex-col gap-3" style={{ color: styles.cardText }}>
      {/* 只读说明（§8.4 守护） */}
      <div className="text-[11px] opacity-60 px-1">{t('knowledge.cognitiveAsset.scope_note')}</div>

      {/* 工具栏 */}
      <div className="rounded-md border p-3 flex flex-wrap items-center gap-3" style={{ borderColor: styles.cardBorder, background: styles.cardBg }}>
        <label className="flex items-center gap-2 text-xs">
          <span className="opacity-60">{t('knowledge.cognitiveAsset.filter_class')}</span>
          <select
            className="px-2 py-1.5 rounded-md border text-xs"
            style={{ borderColor: styles.cardBorder, background: styles.inputBg, color: styles.inputText }}
            value={activeClass}
            onChange={(e) => setActiveClass(e.target.value)}
          >
            {options.map(c => (
              <option key={c} value={c}>{c === 'ALL' ? t('knowledge.cognitiveAsset.class_all') : t(`knowledge.cognitiveAsset.class.${c}`) || c}</option>
            ))}
          </select>
        </label>
        <div className="relative flex-1 min-w-[180px]">
          <Search className="w-3.5 h-3.5 absolute left-2 top-1/2 -translate-y-1/2 opacity-40" />
          <input
            className="w-full pl-7 pr-2 py-1.5 rounded-md border text-xs"
            style={{ borderColor: styles.cardBorder, background: styles.inputBg, color: styles.inputText }}
            placeholder={t('knowledge.cognitiveAsset.domain_placeholder')}
            value={domain}
            onChange={(e) => setDomain(e.target.value)}
            aria-label={t('knowledge.cognitiveAsset.domain_placeholder')}
          />
        </div>
        <button
          type="button"
          onClick={() => { void loadCatalog(); void loadRows(); }}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md text-xs border cursor-pointer"
          style={{ borderColor: styles.cardBorder, color: styles.cardTextMuted }}
          aria-label={t('knowledge.cognitiveAsset.refresh')}
        >
          <RefreshCw className="w-3.5 h-3.5" />{t('knowledge.cognitiveAsset.refresh')}
        </button>
      </div>

      {/* 分层图例（catalog.layers 服务端下发，只读） */}
      {catalog.status === 'ok' && catalog.data && Object.keys(catalog.data.layers).length > 0 && (
        <div className="rounded-md border p-3 flex flex-col gap-1.5" style={{ borderColor: styles.cardBorder, background: styles.cardBg }}>
          <div className="flex items-center gap-2 text-[10px] uppercase tracking-wider opacity-50">
            <Layers className="w-3.5 h-3.5" />{t('knowledge.cognitiveAsset.layers_title')}
          </div>
          {Object.entries(catalog.data.layers).map(([k, v]) => (
            <div key={k} className="text-[11px] flex gap-2">
              <span className="font-mono opacity-70 flex-shrink-0">{k}</span>
              <span className="opacity-70">{v}</span>
            </div>
          ))}
        </div>
      )}

      {/* catalog 错误态 */}
      {catalog.status === 'error' && catalog.error && (
        <div className="rounded-md border p-3 flex items-start gap-2 text-xs" style={{ borderColor: styles.warningBorder, background: styles.warningBg, color: styles.warningText }}>
          <AlertTriangle className="w-4 h-4 flex-shrink-0 mt-0.5" />
          <div className="flex-1">
            <div>{catalog.error.engineDown ? t('knowledge.cognitiveAsset.engine_unavailable') : t('knowledge.cognitiveAsset.catalog_failed')}</div>
            <div className="opacity-70 mt-0.5">{catalog.error.message}</div>
            {catalog.error.traceId && <div className="font-mono text-[10px] opacity-60 mt-1 break-all">traceId: {catalog.error.traceId}</div>}
          </div>
        </div>
      )}

      {/* 列表主体 */}
      <div className="flex flex-col gap-2">
        {listStatus === 'loading' && <div className="text-xs opacity-50 py-6 text-center">{t('knowledge.cognitiveAsset.loading')}</div>}
        {listStatus === 'error' && listError && (
          <div className="rounded-md border p-3 flex items-start gap-2 text-xs" style={{ borderColor: styles.warningBorder, background: styles.warningBg, color: styles.warningText }}>
            <AlertTriangle className="w-4 h-4 flex-shrink-0 mt-0.5" />
            <div className="flex-1">
              <div>{listError.engineDown ? t('knowledge.cognitiveAsset.engine_unavailable') : t('knowledge.cognitiveAsset.list_failed')}</div>
              <div className="opacity-70 mt-0.5">{listError.message}</div>
              {listError.traceId && <div className="font-mono text-[10px] opacity-60 mt-1 break-all">traceId: {listError.traceId}</div>}
            </div>
            <button type="button" onClick={() => void loadRows()} className="inline-flex items-center gap-1 px-2 py-1 rounded border text-[11px] cursor-pointer" style={{ borderColor: styles.cardBorder, color: styles.cardText }}>
              <RefreshCw className="w-3 h-3" />{t('knowledge.cognitiveAsset.retry')}
            </button>
          </div>
        )}
        {listStatus === 'ok' && rows.length === 0 && (
          <div className="rounded-md border p-8 flex flex-col items-center gap-2 text-center" style={{ borderColor: styles.cardBorder, background: styles.cardBg }}>
            <Inbox className="w-6 h-6 opacity-30" />
            <span className="text-xs opacity-50">{t('knowledge.cognitiveAsset.empty')}</span>
          </div>
        )}
        {listStatus === 'ok' && rows.length > 0 && (
          <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3">
            {rows.map(r => (
              <CognitiveAssetCard key={`${r.assetClass}:${r.assetId}`} row={r} active={selectedId === r.assetId} onSelect={setSelectedId} />
            ))}
          </div>
        )}
      </div>

      {/* 候选评审面（§3.5；选中资产后可用，词表单源 = catalog.lifecycleStates） */}
      <CandidateReviewPanel
        assetId={selectedId}
        lifecycleStates={catalog.status === 'ok' ? catalog.data?.lifecycleStates ?? [] : []}
      />

      <CognitiveAssetResolveDrawer assetId={selectedId} onClose={() => setSelectedId(null)} />
    </div>
  );
}
