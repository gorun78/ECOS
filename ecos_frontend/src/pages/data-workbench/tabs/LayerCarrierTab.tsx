/**
 * LayerCarrierTab — 五层载体浏览器（详细设计-02 B.4）。
 * 让"数据湖五层"从口号变成可核对的载体清单。
 *
 * 消费 GET /api/v1/engine/data/layers(概览) / layers/{layer}(载体) /
 *       layers/{layer}/resources/{id}/rows(增量) | sample(抽样) /
 *       POST layers/{layer}/carriers(登记·管理员)。
 *
 * 关键验收点：
 *  - `declared=false` 的层显示"该层暂无登记载体"，而非"0 条数据"（区分"未登记"与"无行"）；
 *  - 采样前已 security decide（Bearer + RLS/CLS/mask 列裁剪，见 decideBound 提示）；
 *  - 采样为空但表存在 → 明确提示"无行"（非"无权"），区分无权限（decide 拒绝）。
 *
 * 图标：lucide-react 语义色通过 useTheme() token，禁硬编码 Tailwind 颜色 / 中文字面量（全走 t()）。
 */
import React, { useCallback, useEffect, useState } from 'react';
import {
  getLayerSummary, getResourcesByLayer, readLayerRows, sampleLayerRows, registerLayerCarrier,
  LAYER_ORDER,
  type LayerSummary, type LayerCarrier, type DataLayerRows,
} from '../apiLayers';
import { useTheme } from '../../../components/ThemeContext';

type TFn = (key: string, paramsOrFallback?: Record<string, string | number> | string) => string;

interface Props {
  showToast: (type: 'success' | 'info' | 'error', msg: string) => void;
  t: TFn;
  locale?: string;
}

interface DrawerState {
  open: boolean;
  loading: boolean;
  error: string | null;
  layer: string;
  resourceId: string;
  title: string;
  mode: 'increment' | 'sample';
  columns: string[];
  rows: Record<string, unknown>[];
  watermark: string | null;
  hasMore: boolean;
  emptyReason: 'none' | 'noperm' | null;
}

function DrawerClosed(): DrawerState {
  return {
    open: false, loading: false, error: null, layer: '', resourceId: '',
    title: '', mode: 'sample', columns: [], rows: [], watermark: null, hasMore: false, emptyReason: null,
  };
}

function cellStr(v: unknown): string {
  if (v === null || v === undefined) return 'NULL';
  if (v instanceof Date) return v.toISOString();
  if (typeof v === 'object') return JSON.stringify(v);
  return String(v);
}

/** decide 拒绝 / 权限不足 → 区分"无权"（RLS/mask 拒绝，非"无行"）。 */
function looksLikeDeny(msg: string): boolean {
  return /decide|denied|permission|无权|403|401|reject/i.test(msg);
}

const STORAGE_KINDS = ['PG', 'NEO4J', 'DORIS', 'CLICKHOUSE', 'MINIO', 'GIT'];

export default function LayerCarrierTab({ showToast, t }: Props) {
  const { styles } = useTheme();

  const [summary, setSummary] = useState<Record<string, LayerSummary>>({});
  const [total, setTotal] = useState(0);
  const [carriersByLayer, setCarriersByLayer] = useState<Record<string, LayerCarrier[]>>({});
  const [layerLoading, setLayerLoading] = useState<Record<string, boolean>>({});
  const [loadingSummary, setLoadingSummary] = useState(true);
  const [offline, setOffline] = useState(false);
  const [selectedLayer, setSelectedLayer] = useState<string>('CURATED');
  const [drawer, setDrawer] = useState<DrawerState>(DrawerClosed());

  const [showRegister, setShowRegister] = useState(false);
  const [regRef, setRegRef] = useState('');
  const [regKind, setRegKind] = useState('PG');

  const loadSummary = useCallback(async () => {
    setLoadingSummary(true);
    setOffline(false);
    try {
      const m = await getLayerSummary();
      const s: Record<string, LayerSummary> = {};
      for (const layer of LAYER_ORDER) {
        const raw = (m as unknown as Record<string, LayerSummary>)[layer];
        s[layer] = raw && typeof raw === 'object' ? raw : { count: 0, declared: false };
      }
      setSummary(s);
      setTotal(Number(m.total ?? 0));
    } catch (e) {
      setOffline(true);
      showToast('error', t('dw.layers.loadFailed').replace('{msg}', (e as Error).message));
    } finally {
      setLoadingSummary(false);
    }
  }, [showToast, t]);

  useEffect(() => { loadSummary(); }, [loadSummary]);

  // 拉某层载体（缓存），供选中层载体表 + 卡片"最近更新"回填。
  const loadCarriers = useCallback(async (layer: string, force = false) => {
    if (!force && carriersByLayer[layer]) return;
    setLayerLoading(p => ({ ...p, [layer]: true }));
    try {
      const resp = await getResourcesByLayer(layer);
      setCarriersByLayer(p => ({ ...p, [layer]: resp.resources ?? [] }));
    } catch (e) {
      setCarriersByLayer(p => ({ ...p, [layer]: p[layer] ?? [] }));
      showToast('error', t('dw.layers.loadFailedRows').replace('{msg}', (e as Error).message));
    } finally {
      setLayerLoading(p => ({ ...p, [layer]: false }));
    }
  }, [carriersByLayer, showToast, t]);

  useEffect(() => { loadCarriers(selectedLayer); /* eslint-disable-line react-hooks/exhaustive-deps */ }, [selectedLayer]);

  const openSample = useCallback(async (layer: string, carrier: LayerCarrier) => {
    const ref = (carrier.carrier_ref as string) || (carrier.resource_name as string) || carrier.resource_id;
    const rk = (carrier.resource_id as string) || carrier.resource_id;
    setDrawer({ ...DrawerClosed(), open: true, loading: true, layer, resourceId: rk, title: ref, mode: 'sample' });
    try {
      const vo: DataLayerRows = await sampleLayerRows(layer, rk, 100);
      setDrawer({
        ...DrawerClosed(), open: true, loading: false, layer, resourceId: rk, title: ref, mode: 'sample',
        columns: columnsFromRows(vo.rows), rows: vo.rows ?? [], watermark: vo.nextWatermark,
        hasMore: vo.hasMore, emptyReason: (vo.rows ?? []).length === 0 ? 'none' : null,
      });
    } catch (e) {
      const msg = (e as Error).message;
      setDrawer({
        ...DrawerClosed(), open: true, loading: false, layer, resourceId: rk, title: ref, mode: 'sample',
        error: msg, emptyReason: looksLikeDeny(msg) ? 'noperm' : null,
      });
    }
  }, []);

  const openIncrement = useCallback(async (layer: string, carrier: LayerCarrier) => {
    const ref = (carrier.carrier_ref as string) || (carrier.resource_name as string) || carrier.resource_id;
    const rk = (carrier.resource_id as string) || carrier.resource_id;
    setDrawer({ ...DrawerClosed(), open: true, loading: true, layer, resourceId: rk, title: ref, mode: 'increment' });
    try {
      const vo: DataLayerRows = await readLayerRows(layer, rk, undefined, 100);
      setDrawer({
        ...DrawerClosed(), open: true, loading: false, layer, resourceId: rk, title: ref, mode: 'increment',
        columns: columnsFromRows(vo.rows), rows: vo.rows ?? [], watermark: vo.nextWatermark,
        hasMore: vo.hasMore, emptyReason: (vo.rows ?? []).length === 0 ? 'none' : null,
      });
    } catch (e) {
      const msg = (e as Error).message;
      setDrawer({
        ...DrawerClosed(), open: true, loading: false, layer, resourceId: rk, title: ref, mode: 'increment',
        error: msg, emptyReason: looksLikeDeny(msg) ? 'noperm' : null,
      });
    }
  }, []);

  // 增量"加载更多"：以上次 nextWatermark 续读。
  const loadMore = useCallback(async () => {
    if (!drawer.watermark) return;
    setDrawer(d => ({ ...d, loading: true }));
    try {
      const vo = await readLayerRows(drawer.layer, drawer.resourceId, drawer.watermark, 100);
      setDrawer(d => ({
        ...d, loading: false, rows: [...d.rows, ...(vo.rows ?? [])],
        columns: d.columns.length ? d.columns : columnsFromRows(vo.rows),
        watermark: vo.nextWatermark, hasMore: vo.hasMore,
      }));
    } catch (e) {
      setDrawer(d => ({ ...d, loading: false, error: (e as Error).message }));
    }
  }, [drawer]);

  const doRegister = useCallback(async () => {
    if (!regRef.trim()) { showToast('error', t('dw.layers.registerRef')); return; }
    try {
      const r = await registerLayerCarrier(selectedLayer, regRef.trim(), regKind);
      showToast('success', t('dw.layers.registered', { ref: r.carrierRef }));
      setRegRef('');
      setShowRegister(false);
      setCarriersByLayer(p => ({ ...p, [selectedLayer]: [] }));
      loadCarriers(selectedLayer, true);
      loadSummary();
    } catch (e) {
      showToast('error', t('dw.layers.registerFailed').replace('{msg}', (e as Error).message));
    }
  }, [regRef, regKind, selectedLayer, showToast, t, loadCarriers, loadSummary]);

  const selectedCarriers = carriersByLayer[selectedLayer] ?? [];
  const selectedSummary = summary[selectedLayer] ?? { count: 0, declared: false };

  return (
    <div className={`flex-1 flex flex-col p-4 gap-3 overflow-hidden ${styles.cardText}`}>
      {/* 顶栏 */}
      <div className="flex items-center gap-2 flex-wrap">
        <span className="text-sm font-bold shrink-0">{t('dw.layers.title')}</span>
        <span className="text-xs opacity-70 shrink-0">{total} {t('dw.layers.total')}</span>
        <div className="flex-1 min-w-4" />
        <button type="button" onClick={loadSummary}
            className={`text-xs px-2.5 py-1 rounded font-semibold ${styles.cardBorder} border bg-black/5 hover:bg-black/10`}>
          {t('dw.layers.refresh')}
        </button>
        <button type="button" onClick={() => setShowRegister(v => !v)}
            className={`text-xs px-2.5 py-1 rounded font-semibold ${styles.accentText} ${styles.cardBorder} border`}>
          {t('dw.layers.registerTitle')}
        </button>
      </div>

      {offline && (
        <div className={`text-xs px-3 py-2 rounded border ${styles.cardBorder} ${styles.dangerText}`}>
          {t('dw.layers.offline')}
        </div>
      )}

      {/* 管理员：登记载体（D.2 registerCarrier） */}
      {showRegister && (
        <div className={`rounded border p-3 text-xs space-y-2 ${styles.cardBg} ${styles.cardBorder}`}>
          <div className="flex flex-wrap items-end gap-3">
            <label className="flex flex-col gap-1">
              <span className="text-[10px] opacity-60">{t('dw.layers.registerLayer')}</span>
              <select className={selectCls(styles)} value={selectedLayer}
                  onChange={e => setSelectedLayer(e.target.value)}>
                {LAYER_ORDER.map(l => <option key={l} value={l}>{l}</option>)}
              </select>
            </label>
            <label className="flex flex-col gap-1 min-w-[220px]">
              <span className="text-[10px] opacity-60">{t('dw.layers.registerRef')}</span>
              <input className={`text-xs border rounded px-1.5 py-1 ${styles.cardBorder} ${styles.cardBg}`}
                  value={regRef} onChange={e => setRegRef(e.target.value)}
                  placeholder="ecos_dw.ecos_biz_stage_fact" />
            </label>
            <label className="flex flex-col gap-1">
              <span className="text-[10px] opacity-60">{t('dw.layers.registerKind')}</span>
              <select className={selectCls(styles)} value={regKind} onChange={e => setRegKind(e.target.value)}>
                {STORAGE_KINDS.map(k => <option key={k} value={k}>{k}</option>)}
              </select>
            </label>
            <button type="button" onClick={doRegister}
                className={`px-3 py-1 rounded font-semibold ${styles.accentText} ${styles.cardBorder} border`}>
              {t('dw.layers.register')}
            </button>
          </div>
        </div>
      )}

      {loadingSummary ? (
        <div className="flex-1 flex items-center justify-center">
          <div className="h-6 w-6 animate-spin rounded-full border-2 border-current border-t-transparent opacity-60" />
        </div>
      ) : (
        <div className="flex-1 min-h-0 grid grid-rows-[auto_1fr] gap-3 overflow-hidden">
          {/* 5 层卡片 */}
          <div className="grid grid-cols-2 md:grid-cols-5 gap-2">
            {LAYER_ORDER.map(layer => {
              const ls = summary[layer] ?? { count: 0, declared: false };
              const active = selectedLayer === layer;
              const undeclared = !ls.declared;
              const carriers = carriersByLayer[layer];
              const lastUpdated = carriers && carriers.length ? maxUpdated(carriers) : undefined;
              return (
                <button key={layer} type="button" onClick={() => setSelectedLayer(layer)}
                    className={`text-left rounded-md p-2.5 border min-w-0 transition-all ${undeclared ? 'opacity-60' : ''} ${active ? `${styles.sidebarActiveBg} border-l-2` : styles.cardBorder} ${styles.cardBg}`}>
                  <div className="flex items-center justify-between gap-1">
                    <span className="font-bold text-xs truncate">{layer}</span>
                    <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded ${ls.declared ? `${styles.successText} bg-black/10` : `${styles.cardTextMuted} bg-black/5`}`}>
                      {ls.declared ? t('dw.layers.declared') : t('dw.layers.undeclared')}
                    </span>
                  </div>
                  <div className="mt-2 grid grid-cols-2 gap-x-2 gap-y-1 text-[10px]">
                    <div>
                      <div className="opacity-60">{t('dw.layers.carrierCount')}</div>
                      <div className="font-mono font-semibold text-xs">{ls.count}</div>
                    </div>
                    <div>
                      <div className="opacity-60">{t('dw.layers.lastUpdated')}</div>
                      <div className="font-mono text-xs truncate" title={lastUpdated ?? ''}>{lastUpdated ?? '—'}</div>
                    </div>
                  </div>
                  {layerLoading[layer] && (
                    <div className="mt-1 flex items-center gap-1 text-[10px] opacity-60">
                      <div className="h-3 w-3 animate-spin rounded-full border-2 border-current border-t-transparent" />
                    </div>
                  )}
                </button>
              );
            })}
          </div>

          {/* 选中层：载体表 或 未声明说明 */}
          <div className={`min-h-0 rounded-md border p-3 ${styles.cardBorder} ${styles.cardBg} overflow-hidden flex flex-col`}>
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-bold">{selectedLayer} · {t('dw.layers.carriersTitle')} ({selectedCarriers.length})</span>
              <span className="text-[10px] opacity-60">{t('dw.layers.decideBound')}</span>
            </div>

            {selectedSummary.count === 0 ? (
              <div className="flex-1 flex flex-col items-center justify-center text-center gap-1 py-6">
                <span className={`text-xs font-semibold ${styles.cardTextMuted}`}>{t('dw.layers.undeclared')}</span>
                <span className="text-[11px] opacity-70 max-w-md">{t('dw.layers.undeclaredHint')}</span>
              </div>
            ) : selectedCarriers.length === 0 && !layerLoading[selectedLayer] ? (
              <div className={`flex-1 flex items-center justify-center text-xs opacity-60`}>{t('dw.layers.carriersEmpty')}</div>
            ) : (
              <div className="flex-1 min-h-0 overflow-auto">
                <CarrierTable
                  carriers={selectedCarriers}
                  layer={selectedLayer}
                  t={t}
                  onSample={c => openSample(selectedLayer, c)}
                  onIncrement={c => openIncrement(selectedLayer, c)}
                />
              </div>
            )}
          </div>
        </div>
      )}

      {/* 行采样 / 增量读取抽屉 */}
      {drawer.open && (
        <RowDrawer drawer={drawer} t={t} onClose={() => setDrawer(DrawerClosed())} onLoadMore={loadMore} />
      )}
    </div>
  );
}

function selectCls(styles: ReturnType<typeof useTheme>['styles']): string {
  return `text-xs border rounded px-1.5 py-1 ${styles.cardBorder} ${styles.cardBg}`;
}

/** 载体表（storage_kind / carrier_ref / 记录数 / 更新时间 / 采样·增量操作）。 */
function CarrierTable({ carriers, layer, t, onSample, onIncrement }: {
  carriers: LayerCarrier[];
  layer: string;
  t: TFn;
  onSample: (c: LayerCarrier) => void;
  onIncrement: (c: LayerCarrier) => void;
}) {
  const { styles } = useTheme();
  return (
    <table className="w-full text-left text-xs">
      <thead className="sticky top-0">
        <tr className={`border-b ${styles.cardBorder}`}>
          <th className="px-2 py-1.5">{t('dw.layers.colCarrier')}</th>
          <th className="px-2 py-1.5">{t('dw.layers.colStorageKind')}</th>
          <th className="px-2 py-1.5">{t('dw.layers.colRef')}</th>
          <th className="px-2 py-1.5 text-right">{t('dw.layers.colRecords')}</th>
          <th className="px-2 py-1.5">{t('dw.layers.colUpdated')}</th>
          <th className="px-2 py-1.5 text-right">{t('dw.layers.actionSample')}</th>
        </tr>
      </thead>
      <tbody>
        {carriers.map(c => {
          const ref = (c.carrier_ref as string) || (c.resource_name as string) || c.resource_id;
          return (
            <tr key={c.resource_id} className={`border-b ${styles.cardBorder}`}>
              <td className="px-2 py-1.5 font-mono truncate max-w-[28ch]" title={c.resource_name || c.resource_id}>
                {c.resource_name || c.resource_id}
              </td>
              <td className="px-2 py-1.5">
                <span className={`px-1.5 py-0.5 rounded text-[10px] font-mono ${styles.cardTextMuted} bg-black/5`}>{c.storage_kind || 'PG'}</span>
              </td>
              <td className="px-2 py-1.5 font-mono text-[11px] truncate max-w-[32ch]" title={c.carrier_ref ?? ''}>{c.carrier_ref || '—'}</td>
              <td className="px-2 py-1.5 text-right font-mono">{Number(c.record_count ?? 0).toLocaleString()}</td>
              <td className="px-2 py-1.5 font-mono text-[11px] truncate max-w-[24ch]" title={c.last_sync_time ?? c.update_time ?? ''}>
                {shortTs(c.last_sync_time ?? c.update_time)}
              </td>
              <td className="px-2 py-1.5 text-right whitespace-nowrap">
                <button type="button" onClick={() => onSample(c)} title={ref}
                    className={`text-[11px] underline hover:opacity-70 mr-2 ${styles.accentText}`}>
                  {t('dw.layers.actionSampleShort')}
                </button>
                <button type="button" onClick={() => onIncrement(c)}
                    className="text-[11px] underline hover:opacity-70">
                  {t('dw.layers.actionIncrementShort')}
                </button>
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}

/** 行采样 / 增量读取抽屉（decide 已生效：Bearer + RLS/CLS/mask 列裁剪）。 */
function RowDrawer({ drawer, t, onClose, onLoadMore }: {
  drawer: DrawerState;
  t: TFn;
  onClose: () => void;
  onLoadMore: () => void;
}) {
  const { styles } = useTheme();
  const denied = drawer.emptyReason === 'noperm';
  return (
    <div className="absolute inset-0 z-20 flex justify-end bg-black/40" onClick={onClose}>
      <div onClick={e => e.stopPropagation()}
          className={`h-full w-full max-w-2xl flex flex-col ${styles.cardBg} border-l text-xs ${styles.cardBorder}`}>
        <div className={`flex items-center justify-between px-3 py-2.5 border-b shrink-0 ${styles.cardBorder}`}>
          <div className="min-w-0">
            <div className="font-bold truncate">{drawer.title}</div>
            <div className="text-[10px] opacity-70">
              {drawer.mode === 'increment' ? t('dw.layers.modeIncrement') : t('dw.layers.modeSample')} · {drawer.layer}
            </div>
          </div>
          <button type="button" onClick={onClose} className={`text-lg leading-none ${styles.cardTextMuted} hover:opacity-70`}>×</button>
        </div>
        <div className={`px-3 py-1.5 text-[10px] opacity-70 border-b shrink-0 ${styles.cardBorder}`}>
          {t('dw.layers.decideBound')}
        </div>

        <div className="flex-1 min-h-0 overflow-auto p-3">
          {drawer.error && drawer.rows.length === 0 ? (
            <div className={`rounded border p-3 ${styles.cardBorder} ${styles.dangerText}`}>
              {denied
                ? t('dw.layers.noPerm').replace('{msg}', drawer.error)
                : t('dw.layers.loadFailedRows').replace('{msg}', drawer.error)}
            </div>
          ) : drawer.loading ? (
            <div className="h-6 w-6 animate-spin rounded-full border-2 border-current border-t-transparent opacity-60 mx-auto my-6" />
          ) : drawer.rows.length === 0 ? (
            <div className="text-center opacity-70 py-8">
              {denied
                ? t('dw.layers.noPerm').replace('{msg}', drawer.error ?? '')
                : t('dw.layers.rowsEmpty')}
            </div>
          ) : (
            <>
              <div className="text-[10px] opacity-60 mb-2">
                {drawer.rows.length} × {drawer.columns.length} {t('dw.layers.col')}
                {drawer.watermark ? ` · watermark=${drawer.watermark}` : ''}
              </div>
              <div className="overflow-x-auto">
                <table className="w-full text-left text-[11px]">
                  <thead>
                    <tr className={`border-b ${styles.cardBorder}`}>
                      {drawer.columns.map(col => <th key={col} className="px-2 py-1 font-mono whitespace-nowrap">{col}</th>)}
                    </tr>
                  </thead>
                  <tbody>
                    {drawer.rows.map((r, i) => (
                      <tr key={i} className={`border-b ${styles.cardBorder}`}>
                        {drawer.columns.map(col => (
                          <td key={col} className="px-2 py-1 font-mono max-w-[240px] truncate" title={cellStr(r[col])}>
                            {cellStr(r[col])}
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {drawer.hasMore && !denied && (
                <button type="button" onClick={onLoadMore} disabled={drawer.loading}
                    className={`mt-2 text-xs px-3 py-1 rounded ${styles.accentText} ${styles.cardBorder} border disabled:opacity-40`}>
                  {t('dw.layers.loadMore')}
                </button>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
}

// ─── 纯函数工具 ────────────────────────────────────────────

/** 从行集合推导列组（保持首行键序，合并后续行新增键）。 */
function columnsFromRows(rows: Record<string, unknown>[]): string[] {
  if (!rows || rows.length === 0) return [];
  const cols: string[] = [];
  const seen = new Set<string>();
  for (const r of rows) {
    for (const k of Object.keys(r)) {
      if (!seen.has(k)) { seen.add(k); cols.push(k); }
    }
  }
  return cols;
}

function maxUpdated(carriers: LayerCarrier[]): string | undefined {
  let max: string | undefined;
  for (const c of carriers) {
    const ts = c.last_sync_time ?? c.update_time;
    if (ts && (!max || String(ts) > String(max))) max = String(ts);
  }
  return max ? shortTs(max) : undefined;
}

function shortTs(ts: unknown): string {
  if (!ts) return '—';
  const s = String(ts);
  const m = s.match(/\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}/);
  return m ? m[0].replace('T', ' ') : s.slice(0, 16);
}
