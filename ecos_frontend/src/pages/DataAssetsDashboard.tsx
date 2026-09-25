/**
 * DataAssetsDashboard — 数据资产 + 分级分类（PMO-data10）。
 *
 * 消费 data-engine 资产 CRUD + security-tag REST：
 *   GET    /api/v1/datanet/assets                (列表 — 已登记资产;空则物理层 pd_ son_td_data_resource 占位视图)
 *   GET    /api/v1/datanet/assets/{assetId}/fields        (字段级敏感度)
 *   POST   /api/v1/datanet/assets/{assetId}/security-tag  (人工确认打标)
 *   GET    /api/v1/datanet/levels                  (4 级字典)
 *   GET    /api/v1/datanet/categories              (业务分类树)
 *
 * 国际化： 全部 label 走 t('dw.assets.*')；下拉 option text 已国际化；
 *         字段级数据维度（L1/L2/L3/L4 等级 code、data_type 枚举）保持流转原入。
 *
 * 768px 不溢出：grid-cols-1 md:grid-cols-2（桌面双栏， 平板以下单列），
 * 卡片 min-w-0+overflow:hidden 防表格溢出， 字段名 truncate。
 */
import React, { useCallback, useEffect, useState } from 'react';
import { useTheme } from '../components/ThemeContext';
import type { DataAssetVO, DataAssetFieldVO, DataLevelDef, DataCategoryTreeItem } from '../types/dataAssets';

interface Props {
  showToast: (type: 'success' | 'info' | 'error', msg: string) => void;
  t: (key: string) => string;
  locale?: string;
}

const API = '/api/v1/datanet';

/** 从 localStorage 读取 Bearer Token（与 data-workbench/api.ts 同一套逻辑） */
function authHeaders(): Record<string, string> {
  const token = typeof localStorage !== 'undefined' ? (localStorage.getItem('token') || localStorage.getItem('accessToken') || '') : '';
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function api<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, {
    headers: { 'Content-Type': 'application/json', ...authHeaders(), ...(init?.headers ?? {}) },
    ...init,
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

function CardBlock({ children, className = '' }: { children: React.ReactNode; className?: string }) {
  const { styles } = useTheme();
  return (
    <div className={`rounded-md p-3 text-xs min-w-0 ${styles.cardBg} ${styles.cardBorder} border ${className}`}>
      {children}
    </div>
  );
}

export default function DataAssetsDashboard({ showToast, t }: Props) {
  const { styles } = useTheme();

  const [assets, setAssets] = useState<DataAssetVO[]>([]);
  const [selectedAssetId, setSelectedAssetId] = useState<string | null>(null);
  const [fields, setFields] = useState<DataAssetFieldVO[]>([]);
  const [levels, setLevels] = useState<DataLevelDef[]>([]);
  const [categories, setCategories] = useState<DataCategoryTreeItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [filter, setFilter] = useState<{ level: string; category: string; keyword: string }>({ level: '', category: '', keyword: '' });

  // 拉 4 级字典 + 分类树（降级：后端未就绪不阻塞列表）
  useEffect(() => {
    (async () => {
      try {
        const [lv, ct] = await Promise.all([
          api<{ data: DataLevelDef[] }>(`${API}/levels`),
          api<{ data: DataCategoryTreeItem[] }>(`${API}/categories`),
        ]);
        setLevels(lv.data ?? []);
        setCategories(ct.data ?? []);
      } catch (e) {
        console.warn('levels/categories 加载失败（后端未重启或网络异常）', e);
      }
    })();
  }, []);

  const loadAssets = useCallback(async (f: typeof filter) => {
    setLoading(true);
    try {
      const params = new URLSearchParams();
      if (f.level) params.append('sensitivityLevel', f.level);
      if (f.category) params.append('categoryId', f.category);
      if (f.keyword) params.append('keyword', f.keyword);
      params.append('page', '1');
      params.append('pageSize', '100');
      const resp = await api<{ data: DataAssetVO[] }>(`${API}/assets?${params.toString()}`);
      setAssets(resp.data ?? []);
    } catch (e) {
      showToast('error', t('dw.assets.loadFailed').replace('{msg}', (e as Error).message));
    } finally {
      setLoading(false);
    }
  }, [showToast, t]);

  useEffect(() => {
    loadAssets(filter);
  }, [loadAssets, filter]);

  // 选中资产后拉字段级敏感度
  useEffect(() => {
    if (!selectedAssetId) { setFields([]); return; }
    (async () => {
      try {
        const resp = await api<{ data: DataAssetFieldVO[] }>(
          `${API}/assets/${encodeURIComponent(selectedAssetId)}/fields?page=1&pageSize=200`,
        );
        // 后端可能直接返回数组；同时兼容包一层 { items: [...] } 形式（防御性处理）
        const arr = Array.isArray(resp.data)
          ? resp.data
          : (resp.data as unknown as { items?: DataAssetFieldVO[] })?.items ?? [];
        setFields(arr);
      } catch {
        setFields([]);
      }
    })();
  }, [selectedAssetId]);

  const levelBadge = (code: string, name?: string) => {
    const map: Record<string, string> = {
      L1: 'bg-emerald-100 text-emerald-800',
      L2: 'bg-sky-100 text-sky-800',
      L3: 'bg-amber-100 text-amber-800',
      L4: 'bg-rose-100 text-rose-800',
    };
    return (
      <span className={`inline-block px-1.5 py-0.5 rounded text-[10px] font-bold ${map[code] || map.L1}`}>
        {code}{name ? ` ${name}` : ''}
      </span>
    );
  };

  return (
    <div className="flex-1 flex flex-col min-h-0 p-4 gap-3 overflow-hidden">
      {/* 顶栏 —— 横滑 filter（768px 适配 flex-wrap）—— 全国际化 */}
      <div className={`flex flex-wrap items-center gap-2 ${styles.cardTextMuted}`}>
        <span className="font-bold text-sm shrink-0">{t('dw.assets.title')}</span>
        <span className="text-xs shrink-0">{assets.length} {t('dw.assets.itemsUnit')}</span>
        <div className="flex-1 min-w-4" />
        <select
          className={`text-xs border rounded px-1.5 py-1 shrink-0 ${styles.cardBorder} ${styles.cardBg}`}
          value={filter.level}
          onChange={e => setFilter({ ...filter, level: e.target.value })}
        >
          <option value="">{t('dw.assets.allLevels')}</option>
          {(levels.length ? levels : [
            { levelCode: 'L1', levelName: t('dw.assets.level.L1') },
            { levelCode: 'L2', levelName: t('dw.assets.level.L2') },
            { levelCode: 'L3', levelName: t('dw.assets.level.L3') },
            { levelCode: 'L4', levelName: t('dw.assets.level.L4') },
          ] as DataLevelDef[]).map(l => <option key={l.levelCode} value={l.levelCode}>{l.levelCode} {l.levelName}</option>)}
        </select>
        <select
          className={`text-xs border rounded px-1.5 py-1 shrink-0 ${styles.cardBorder} ${styles.cardBg}`}
          value={filter.category}
          onChange={e => setFilter({ ...filter, category: e.target.value })}
        >
          <option value="">{t('dw.assets.allCategories')}</option>
          {categories.filter(c => c.level === 2).map(c => <option key={c.categoryId} value={c.categoryId}>{c.name}</option>)}
        </select>
        <input
          placeholder={t('dw.assets.keywords')}
          className="text-xs border rounded px-2 py-1 min-w-[140px] md:min-w-[200px]"
          value={filter.keyword}
          onChange={e => setFilter({ ...filter, keyword: e.target.value })}
        />
        {/* 核心 "分级分类" 操作按钮：点击后聚焦到详情面板的字段表 */}
        <button
          type="button"
          disabled={!selectedAssetId}
          title={!selectedAssetId ? t('dw.assets.selectHint') : undefined}
          className={`text-xs px-3 py-1 rounded font-semibold transition-colors shrink-0 disabled:opacity-40 disabled:cursor-not-allowed
            ${selectedAssetId ? 'bg-black/10 hover:bg-black/20' : ''} ${styles.cardBorder} border`}
          onClick={() => {
            if (!selectedAssetId) return;
            // 触发字段表滚动到视口（详情面板）
            requestAnimationFrame(() => {
              document.querySelectorAll('[data-asset-detail]').forEach(el => (el as HTMLElement).scrollIntoView({ behavior: 'smooth', block: 'nearest' }));
            });
          }}
        >
          {t('dw.assets.classifyBtn')}
        </button>
      </div>

      {/* 主体 —— 桌面 2 栏（列表 | 详情）， 平板以下上下堆叠 */}
      <div className="flex-1 min-h-0 grid grid-rows-[auto_1fr] md:grid-cols-2 md:grid-rows-1 gap-3 overflow-hidden">
        <div className="min-h-0 flex flex-col gap-2">
          {/* 资产列表 */}
          <CardBlock className="flex-1 overflow-hidden">
            <div className="flex flex-col h-full min-h-0">
              <div className={`flex items-center justify-between text-xs font-bold ${styles.cardText}`}>
                <span>{assets.length} {t('dw.assets.count')}</span>
              </div>
              <div className="flex-1 overflow-y-auto min-h-0 space-y-2 pr-1">
                {loading ? (
                  <div className="h-10 w-10 animate-spin rounded-full border-2 border-current opacity-60 mx-auto" />
                ) : assets.length === 0 ? (
                  <div className="text-xs opacity-60 py-4 text-center">{t('dw.assets.empty')}</div>
                ) : (
                  assets.map(a => (
                    <button
                      key={a.assetId}
                      onClick={() => setSelectedAssetId(a.assetId === selectedAssetId ? null : a.assetId)}
                      className={`w-full text-left rounded-md p-2 border transition-colors ${
                        a.assetId === selectedAssetId
                          ? `${styles.accentBorder} border-l-2 ${styles.sidebarActiveBg}`
                          : styles.cardBorder
                      } ${styles.cardBg}`}
                    >
                      <div className="flex items-center gap-2 min-w-0">
                        <span className="truncate font-semibold text-xs">{a.assetName}</span>
                        <span className="font-mono text-[10px] opacity-60 shrink-0">{a.resourceType}</span>
                      </div>
                      <div className="flex flex-wrap gap-1 mt-1 text-[10px]">
                        {levelBadge(a.sensitivityLevel || 'L1', a.levelName)}
                        {a.categoryName ? (
                          <span className={`px-1.5 py-0.5 rounded ${styles.cardTextMuted} bg-black/5`}>{a.categoryName}</span>
                        ) : null}
                        <span className="px-1.5 py-0.5 rounded bg-black/5">{a.layer}</span>
                        {a.zone ? <span className="px-1.5 py-0.5 rounded bg-black/5">{a.zone}</span> : null}
                      </div>
                      <div className="mt-1 flex justify-between text-[10px] opacity-70">
                        <span>{a.owner || '—'}</span>
                        <span>{(a.confirmedFieldCount ?? 0)} {t('dw.assets.optionalFields')}</span>
                      </div>
                    </button>
                  ))
                )}
              </div>
            </div>
          </CardBlock>
        </div>

        {/* 详情 + 字段级敏感度 */}
        <div className="min-h-0" data-asset-detail>
          <CardBlock className="max-h-full overflow-y-auto bg-black/[0.02]">
            {!selectedAssetId ? (
              <div className={`flex items-center justify-center text-xs opacity-60 h-full py-8`}>
                {t('dw.assets.selectHint')}
              </div>
            ) : (
              <AssetDetail
                asset={assets.find(a => a.assetId === selectedAssetId)!}
                fields={fields}
                levels={levels}
                showToast={showToast}
                t={t}
                onChanged={loadAssets}
                filter={filter}
              />
            )}
          </CardBlock>
        </div>
      </div>
    </div>
  );
}

/** 资产详情 —— 基础信息 + 字段级敏感度表 + 打标表单。全部 i18n。 */
function AssetDetail({
  asset, fields, levels, showToast, t, onChanged, filter,
}: {
  asset: DataAssetVO;
  fields: DataAssetFieldVO[];
  levels: DataLevelDef[];
  showToast: (type: 'success' | 'info' | 'error', msg: string) => void;
  t: (key: string) => string;
  onChanged: (f: { level: string; category: string; keyword: string }) => Promise<void>;
  filter: { level: string; category: string; keyword: string };
}) {
  const { styles } = useTheme();
  const [editingFieldId, setEditingFieldId] = useState<string | null>(null);
  const [editing, setEditing] = useState<{ level: string; dataType: string; maskStrategy: string; confirmed: boolean }>({
    level: 'L1', dataType: 'GENERAL', maskStrategy: 'none', confirmed: true,
  });

  const confirmTag = async () => {
    if (!asset?.assetId || !editingFieldId) return;
    try {
      const target = fields.find(f => f.fieldId === editingFieldId);
      await api(`${API}/assets/${asset.assetId}/security-tag`, {
        method: 'POST',
        body: JSON.stringify({
          confirmed: editing.confirmed,
          operator: 'current_user',
          fields: [{
            fieldId: editingFieldId,
            fieldName: target?.fieldName || editingFieldId,
            fieldSensitivity: editing.level,
            dataType: editing.dataType,
            maskStrategy: editing.maskStrategy,
            recommendLevel: null,
            recommendSource: editing.confirmed ? 'LLM' : 'MANUAL',
          }],
        }),
      });
      showToast('success', t('dw.assets.tagSaved'));
      setEditingFieldId(null);
      // 同步刷新资产列表与当前资产字段
      await onChanged(filter);
      const resp = await api<{ data: DataAssetFieldVO[] }>(
        `${API}/assets/${asset.assetId}/fields?page=1&pageSize=200`,
      );
      void resp;
    } catch (e) {
      showToast('error', t('dw.assets.tagFailed').replace('{msg}', (e as Error).message));
    }
  };

  const levelOptions = levels.length ? levels : [
    { levelCode: 'L1', levelName: t('dw.assets.level.L1') },
    { levelCode: 'L2', levelName: t('dw.assets.level.L2') },
    { levelCode: 'L3', levelName: t('dw.assets.level.L3') },
    { levelCode: 'L4', levelName: t('dw.assets.level.L4') },
  ];

  return (
    <div className="grid gap-3 max-h-full overflow-y-auto">
      {/* 资产基础信息 */}
      <div>
        <div className="font-bold text-sm">{asset.assetName}</div>
        <div className="grid grid-cols-2 text-[11px] opacity-80 mt-0.5">
          <span className="truncate">{asset.resourceName}</span>
          <span>{t('dw.assets.layer')}: {asset.layer} {asset.zone ? `· ${asset.zone}` : ''}</span>
        </div>
        <div className="flex items-center gap-1.5 mt-1.5 flex-wrap">
          <span className={`px-1.5 py-0.5 rounded text-[10px] font-bold ${
            (asset.sensitivityLevel || 'L1') === 'L1' ? 'bg-emerald-100 text-emerald-800'
            : (asset.sensitivityLevel || 'L1') === 'L2' ? 'bg-sky-100 text-sky-800'
            : (asset.sensitivityLevel || 'L1') === 'L3' ? 'bg-amber-100 text-amber-800'
            : 'bg-rose-100 text-rose-800'
          }`}>
            {asset.sensitivityLevel || 'L1'} {asset.levelName}
          </span>
          {asset.categoryStatus && (
            <span className={`text-[10px] ${styles.cardTextMuted}`}>
              {t('dw.assets.status')}={asset.categoryStatus}
            </span>
          )}
        </div>
        <div className="grid grid-cols-2 gap-x-2 gap-y-1 text-[11px] mt-2">
          <div><span className="opacity-60">{t('dw.assets.owner')}</span>: {asset.owner || '—'}</div>
          <div><span className="opacity-60">{t('dw.assets.organization')}</span>: {asset.ownerOrg || '—'}</div>
          <div className="col-span-2">
            <span className="opacity-60">{t('dw.assets.description')}</span>: {asset.businessDesc || '—'}
          </div>
        </div>
      </div>

      {/* 字段级敏感度表 */}
      <div>
        <div className={`text-xs font-bold ${styles.cardText} mb-1.5`}>
          {fields.length} {t('dw.assets.fieldsTitle')}
        </div>
        <div className={`overflow-x-auto ${styles.cardBg} border rounded text-xs`}>
          <table className="w-full text-left">
            <thead>
              <tr className={`border-b ${styles.cardBorder}`}>
                <th className="px-2 py-1 text-right w-5">{t('dw.assets.colStatus')}</th>
                <th className="px-2 py-1">{t('dw.assets.colField')}</th>
                <th className="px-2 py-1">{t('dw.assets.colDataType')}</th>
                <th className="px-2 py-1">{t('dw.assets.colSensitivity')}</th>
                <th className="px-2 py-1">{t('dw.assets.colMask')}</th>
                <th className="px-2 py-1 text-right">{t('dw.assets.colActions')}</th>
              </tr>
            </thead>
            <tbody>
              {fields.length === 0 ? (
                <tr>
                  <td colSpan={6} className="h-12 text-center opacity-60">{t('dw.assets.fieldsEmpty')}</td>
                </tr>
              ) : fields.map((f) => (
                <tr key={f.fieldAssetId || f.fieldId} className={`border-b ${styles.cardBorder}`}>
                  <td className="px-2 py-1 text-right">
                    {f.confirmed ? <span className="text-emerald-600">✓</span> : <span className="text-amber-600">✎</span>}
                  </td>
                  <td className="px-2 py-1 truncate max-w-[12ch]">{f.fieldName}</td>
                  <td className="px-2 py-1">{t(`dw.assets.dataType.${f.dataType}`) || f.dataType}</td>
                  <td className="px-2 py-1">{f.fieldSensitivity}</td>
                  <td className="px-2 py-1">{t(`dw.assets.maskStrategy.${f.maskStrategy}`) || f.maskStrategy}</td>
                  <td className="px-2 py-1 text-right">
                    <button
                      className="text-xs underline hover:text-rose-600"
                      onClick={() => {
                        setEditingFieldId(f.fieldId);
                        setEditing({
                          level: f.fieldSensitivity || 'L1',
                          dataType: f.dataType || 'GENERAL',
                          maskStrategy: f.maskStrategy || 'none',
                          confirmed: f.confirmed,
                        });
                      }}
                    >
                      {t('dw.assets.editBtn')}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* 打标表单（选中字段后展示） */}
      {editingFieldId && (
        <div className={`rounded border p-3 text-xs space-y-3 ${styles.cardBg} ${styles.cardBorder}`}>
          <div className="grid grid-cols-3 gap-2">
            <label>
              <span className="opacity-60 block text-[10px] mb-1">{t('dw.assets.sensitive')}</span>
              <select
                className={`w-full border rounded px-1.5 py-1 ${styles.cardBorder}`}
                value={editing.level}
                onChange={e => setEditing({ ...editing, level: e.target.value })}
              >
                {levelOptions.map(l => <option key={l.levelCode} value={l.levelCode}>{l.levelCode} {l.levelName}</option>)}
              </select>
            </label>
            <label>
              <span className="opacity-60 block text-[10px] mb-1">{t('dw.assets.dataType')}</span>
              <select
                className={`w-full border rounded px-1.5 py-1 ${styles.cardBorder}`}
                value={editing.dataType}
                onChange={e => setEditing({ ...editing, dataType: e.target.value })}
              >
                {['GENERAL', 'ID_CARD', 'PHONE', 'EMAIL', 'BANK_CARD', 'AMOUNT', 'ADDRESS']
                  .map(v => <option key={v} value={v}>{t(`dw.assets.dataType.${v}`) || v}</option>)}
              </select>
            </label>
            <label>
              <span className="opacity-60 block text-[10px] mb-1">{t('dw.assets.maskStrategy')}</span>
              <select
                className={`w-full border rounded px-1.5 py-1 ${styles.cardBorder}`}
                value={editing.maskStrategy}
                onChange={e => setEditing({ ...editing, maskStrategy: e.target.value })}
              >
                {['none', 'prefix3', 'suffix4', 'middle4', 'full'].map(v =>
                  <option key={v} value={v}>{t(`dw.assets.maskStrategy.${v}`) || v}</option>)}
              </select>
            </label>
          </div>
          <div className="flex items-center gap-3 flex-wrap">
            <label className="flex items-center gap-1.5">
              <input type="checkbox" checked={editing.confirmed}
                onChange={e => setEditing({ ...editing, confirmed: e.target.checked })}/>
              <span className="text-[11px]">{t('dw.assets.confirmed')}</span>
            </label>
            <div className="flex-1" />
            <button
              className="px-2.5 py-1 text-xs rounded bg-black/5 hover:bg-black/10"
              onClick={() => setEditingFieldId(null)}
            >
              {t('dw.assets.cancelBtn')}
            </button>
            <button
              className="px-2.5 py-1 text-xs rounded bg-black/10 hover:bg-black/20 font-semibold"
              onClick={confirmTag}
            >
              {t('dw.assets.savePublish')}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
