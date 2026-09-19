/**
 * PropertyPanel — Pipeline node property editor
 * Extracted from PipelineFlowEditor.tsx
 * Aligned with P2-01 node config schema (PMO-3J T2).
 * Wave 5: TRANSFORM_UDF / JOIN / SINK forms (JOIN reuses JoinConditionsEditor;
 * TRANSFORM_UDF wires UdfBuilderPanel via a "New UDF" drawer).
 * @license Apache-2.0
 */

import React, { useState, useCallback, useEffect } from 'react';
import type { Node } from '@xyflow/react';
import { Trash2, X, ChevronDown, Plus, Code2 } from 'lucide-react';
import type { NodeConfig, NodeStatus, PipelineNodeType } from './types';
import type { DataConnection } from '../types';
import { PALETTE_LABELS, buildPaletteItems } from './constants';
import { apiFetchData } from '../../../api';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
import UdfBuilderPanel from './UdfBuilderPanel';

/** UDF list item shape used by TRANSFORM_UDF form. */
interface UdfItem {
  udfId?: string;
  id?: string;
  name?: string;
  language?: string;
}
// ─── Section collapse toggle ──────────────────────────────

const SectionToggle: React.FC<{
  collapsed: boolean;
  onClick: () => void;
  label: string;
  styles: Record<string, string>;
}> = ({ collapsed, onClick, label, styles }) => (
  <button onClick={onClick}
    className={`flex items-center justify-between w-full px-3 py-2 text-xs font-semibold ${styles.muted} hover:${styles.sidebarBg} transition-colors`}
  >
    <span>{label}</span>
    <ChevronDown size={14} className={`transition-transform duration-200 ${collapsed ? '-rotate-90' : 'rotate-0'}`} />
  </button>
);

// ─── Small field primitives ───────────────────────────────

const FieldLabel: React.FC<{ styles: Record<string, string>; children: React.ReactNode }> = ({ styles, children }) => (
  <label className={`text-[11px] ${styles.muted} block mb-1`}>{children}</label>
);

const inputCls = (styles: Record<string, string>) =>
  `w-full px-2 py-1 text-xs border ${styles.cardBorder} rounded focus:${styles.infoBorder} focus:ring-1 focus:${styles.accentBorder} outline-none ${styles.cardBg} ${styles.cardText}`;

// ─── Wave 5 form helpers ──────────────────────────────────

interface FormSharedProps {
  styles: Record<string, string>;
  t: (key: string, params?: Record<string, string | number>) => string;
  nodeConfig: NodeConfig['config'];
  nodeId: string;
  onUpdateNode: (nodeId: string, config: Partial<NodeConfig>) => void;
}

/** TRANSFORM_UDF form: udfId + params key-value editor. */
const UdfForm: React.FC<FormSharedProps> = React.memo(({ styles, t, nodeConfig, nodeId, onUpdateNode }) => {
  const [udfs, setUdfs] = useState<UdfItem[]>([]);
  const [builderOpen, setBuilderOpen] = useState(false);
  const [paramRows, setParamRows] = useState<Array<{ key: string; value: string }>>(
    () => {
      const p = nodeConfig.params || {};
      const keys = Object.keys(p);
      return keys.length > 0
        ? keys.map((k) => ({ key: k, value: String(p[k] ?? '') }))
        : [];
    }
  );

  const loadUdfs = useCallback(() => {
    apiFetchData<{ data?: UdfItem[]; list?: UdfItem[] } | UdfItem[]>('/api/v1/engine/data/udf/list')
      .then((resp: any) => {
        const list = Array.isArray(resp) ? resp : (resp?.data ?? resp?.list ?? []);
        if (Array.isArray(list)) setUdfs(list);
      })
      .catch(() => {});
  }, []);

  useEffect(() => { loadUdfs(); }, [loadUdfs]);

  const setCfg = (field: string, value: unknown) => {
    onUpdateNode(nodeId, { config: { ...nodeConfig, [field]: value } } as Partial<NodeConfig>);
  };

  // Sync paramRows → config.config.params
  useEffect(() => {
    const next: Record<string, string> = {};
    paramRows.forEach((r) => { if (r.key) next[r.key] = r.value; });
    const cur = nodeConfig.params || {};
    if (JSON.stringify(next) !== JSON.stringify(cur)) {
      onUpdateNode(nodeId, { config: { ...nodeConfig, params: next } } as Partial<NodeConfig>);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [paramRows, nodeId]);

  const closeBuilder = () => {
    setBuilderOpen(false);
    // 关闭后拉一次最新 UDF 列表（注册成功会刷新选中项可选范围）
    loadUdfs();
  };

  return (
    <>
      <div>
        <div className="flex items-center justify-between mb-1 gap-1">
          <FieldLabel styles={styles}>{t('dw.pipeline.prop.udfId')}</FieldLabel>
          <div className="flex items-center gap-1">
            <button
              onClick={loadUdfs}
              className={`text-[10px] ${styles.infoText} hover:${styles.infoBorder} transition-colors px-1`}
              title={t('dw.pipeline.prop.udfRefresh')}
            >
              ↻
            </button>
            <button
              onClick={() => setBuilderOpen(true)}
              className={`flex items-center gap-1 px-1.5 py-0.5 text-[10px] ${styles.infoText} border ${styles.infoBorder} rounded hover:${styles.infoBg} transition-colors`}
              title={t('dw.pipeline.prop.udfCreateNew')}
            >
              <Code2 size={11} /> {t('dw.pipeline.prop.udfCreateNew')}
            </button>
          </div>
        </div>
        <select
          value={nodeConfig.udfId || ''}
          onChange={(e) => {
            const val = e.target.value;
            const udf = udfs.find((u) => (u.udfId || u.id) === val);
            setCfg('udfId', val);
            if (udf?.name) setCfg('udfName', udf.name);
          }}
          className={inputCls(styles)}
        >
          <option value="">{t('dw.pipeline.prop.udfSelectPlaceholder')}</option>
          {udfs.map((u) => (
            <option key={u.udfId || u.id || u.name} value={u.udfId || u.id || ''}>
              {u.name} ({u.language || 'py'})
            </option>
          ))}
        </select>
      </div>
      <div>
        <FieldLabel styles={styles}>{t('dw.pipeline.prop.udfParams')}</FieldLabel>
        <div className="space-y-1">
          {paramRows.map((row, idx) => (
            <div key={idx} className="flex gap-1">
              <input type="text" value={row.key}
                onChange={(e) => {
                  const next = [...paramRows]; next[idx] = { ...next[idx], key: e.target.value };
                  setParamRows([...next, { key: '', value: '' }]);
                }}
                className={`${inputCls(styles)} flex-1`}
                placeholder={t('dw.pipeline.prop.paramKey')} />
              <input type="text" value={row.value}
                onChange={(e) => {
                  const next = [...paramRows]; next[idx] = { ...next[idx], value: e.target.value };
                  setParamRows(next);
                }}
                className={`${inputCls(styles)} flex-1`}
                placeholder={t('dw.pipeline.prop.paramValue')} />
              <button
                onClick={() => setParamRows(paramRows.filter((_, i) => i !== idx))}
                className={`px-1 ${styles.dangerText} hover:${styles.dangerText} transition-colors`}
                title={t('dw.pipeline.prop.removeParam')}
              >
                <X size={12} />
              </button>
            </div>
          ))}
          <button
            onClick={() => setParamRows([...paramRows, { key: '', value: '' }])}
            className={`w-full flex items-center justify-center gap-1 px-2 py-1 text-[11px] ${styles.infoText} border border-dashed ${styles.infoBorder} rounded hover:${styles.infoBg} transition-colors`}
          >
            <Plus size={12} /> {t('dw.pipeline.prop.addParam')}
          </button>
        </div>
      </div>

      {/* UdfBuilderPanel 抽屉：节点配置入口打开/关闭 */}
      {builderOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30" role="dialog" aria-modal="true">
          <div className={`w-[800px] max-w-[95vw] h-[560px] max-h-[85vh] rounded-xl overflow-hidden flex flex-col shadow-2xl ${styles.cardBg} ${styles.cardBorder} border`}>
            <div className={`flex items-center justify-between px-3 py-2 border-b ${styles.cardBorder} ${styles.cardBg} shrink-0`}>
              <span className={`text-xs font-bold ${styles.cardTextMuted} uppercase tracking-wider`}>
                {t('dw.pipeline.prop.udfCreateNew')}
              </span>
              <button onClick={closeBuilder} className={`p-1 ${styles.cardTextMuted} hover:${styles.dangerText}`} title={t('dw.pipeline.prop.closePanel')}>
                <X size={14} />
              </button>
            </div>
            <div className="flex-1 min-h-0">
              <UdfBuilderPanel className="h-full" />
            </div>
          </div>
        </div>
      )}
    </>
  );
});

/** JOIN form: joinType + joinKeys (CSV). */
const JoinForm: React.FC<FormSharedProps> = React.memo(({ styles, t, nodeConfig, nodeId, onUpdateNode }) => {
  const setCfg = (field: string, value: unknown) => {
    onUpdateNode(nodeId, { config: { ...nodeConfig, [field]: value } } as Partial<NodeConfig>);
  };
  const joinKeysCsv = Array.isArray(nodeConfig.joinKeys) ? nodeConfig.joinKeys.join(', ') : '';
  return (
    <>
      <div>
        <FieldLabel styles={styles}>{t('dw.pipeline.prop.joinTypeForm')}</FieldLabel>
        <select
          value={nodeConfig.joinType || 'inner'}
          onChange={(e) => setCfg('joinType', e.target.value as NonNullable<NodeConfig['config']['joinType']>)}
          className={inputCls(styles)}
        >
          <option value="inner">{t('dw.pipeline.prop.joinTypeInner')}</option>
          <option value="left">{t('dw.pipeline.prop.joinTypeLeft')}</option>
          <option value="right">{t('dw.pipeline.prop.joinTypeRight')}</option>
          <option value="full">{t('dw.pipeline.prop.joinTypeFull')}</option>
          <option value="cross">{t('dw.pipeline.prop.joinTypeCross')}</option>
        </select>
      </div>
      <div>
        <FieldLabel styles={styles}>{t('dw.pipeline.prop.joinKeysCsv')}</FieldLabel>
        <input type="text" value={joinKeysCsv}
          onChange={(e) => {
            const s = e.target.value;
            const arr = s.split(/[,，]/).map((x) => x.trim()).filter(Boolean);
            setCfg('joinKeys', arr);
          }}
          className={`${inputCls(styles)} font-mono`}
          placeholder={t('dw.pipeline.prop.joinKeysPlaceholder')} />
      </div>
    </>
  );
});

// ─── Property Panel ───────────────────────────────────────────

interface PropertyPanelProps {
  node: Node | null;
  connections: DataConnection[];
  onUpdateNode: (nodeId: string, config: Partial<NodeConfig>) => void;
  onDeleteNode: (nodeId: string) => void;
  onClose: () => void;
}

const PropertyPanel: React.FC<PropertyPanelProps> = React.memo(
  ({ node, connections, onUpdateNode, onDeleteNode, onClose }) => {
    const { styles } = useTheme();
    const { t } = useLanguage();
    const config: NodeConfig = (node?.data ?? {}) as unknown as NodeConfig;
    const [collapsedSections, setCollapsedSections] = useState<Record<string, boolean>>({});
    const [headerRows, setHeaderRows] = useState<Array<{ key: string; value: string }>>(
      () => {
        const h = config.config?.headers || {};
        return Object.keys(h).length > 0
          ? Object.entries(h).map(([key, value]) => ({ key, value: String(value) }))
          : [{ key: '', value: '' }];
      }
    );

    const toggleSection = useCallback((key: string) => {
      setCollapsedSections((prev) => ({ ...prev, [key]: !prev[key] }));
    }, []);

    // ── Sync headerRows → config.config.headers (SOURCE_REST) ──
    // Replaces a prior render-phase setState (PMO-3J T2 fix): writing to
    // config during render is illegal in React. Debounced via useEffect.
    const nodeId = node?.id;
    useEffect(() => {
      if (!nodeId) return;
      const hdrs: Record<string, string> = {};
      headerRows.forEach((r) => { if (r.key) hdrs[r.key] = r.value; });
      const current = (config.config?.headers) || {};
      if (JSON.stringify(hdrs) !== JSON.stringify(current)) {
        onUpdateNode(nodeId, { config: { ...config.config, headers: hdrs } } as Partial<NodeConfig>);
      }
      // headerRows is the source of truth here; avoid re-triggering on config changes
      // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [headerRows, nodeId]);

    if (!node) {
      return (
        <div className={`w-72 border-l ${styles.cardBorder} ${styles.cardBg} flex flex-col h-full`}>
          <div className={`flex items-center justify-between px-3 py-2 border-b ${styles.cardBorder} ${styles.cardBg}`}>
            <span className={`text-xs font-bold ${styles.muted} uppercase tracking-wider`}>{t('dw.pipeline.prop.title')}</span>
          </div>
          <div className={`flex-1 flex items-center justify-center text-xs ${styles.cardTextMuted} p-4 text-center`}>
            {t('dw.pipeline.prop.emptyHint')}
          </div>
        </div>
      );
    }

    // ── Helpers to read/write the nested `config.config` object ──
    const nodeConfig = config.config || {};
    const setConfigField = (field: string, value: unknown) => {
      onUpdateNode(node.id, { config: { ...nodeConfig, [field]: value } } as Partial<NodeConfig>);
    };

    const nodeType = (config.nodeType || 'TRANSFORM_SQL') as PipelineNodeType;
    const paletteLabel = PALETTE_LABELS[nodeType] || PALETTE_LABELS.TRANSFORM_SQL;

    return (
      <div className={`w-80 border-l ${styles.cardBorder} ${styles.cardBg} flex flex-col h-full overflow-hidden`}>
        {/* Header */}
        <div className={`flex items-center justify-between px-3 py-2 border-b ${styles.cardBorder} ${styles.cardBg} shrink-0`}>
          <span className={`text-xs font-bold ${styles.muted} uppercase tracking-wider`}>
            {paletteLabel} {t('dw.pipeline.prop.properties')}
          </span>
          <div className="flex gap-1">
            <button onClick={() => onDeleteNode(node.id)} className={`p-1 hover:${styles.dangerBg} rounded ${styles.dangerText} transition-colors`} title={t('dw.pipeline.prop.deleteNode')}>
              <Trash2 size={14} />
            </button>
            <button onClick={onClose} className={`p-1 hover:${styles.sidebarBg} rounded ${styles.muted} transition-colors`} title={t('dw.pipeline.prop.closePanel')}>
              <X size={14} />
            </button>
          </div>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto">
          {/* Basic Info */}
          <div className={`border-b ${styles.cardBorder}`}>
            <SectionToggle collapsed={!!collapsedSections['basic']} onClick={() => toggleSection('basic')} label={t('dw.pipeline.prop.basicInfo')} styles={styles} />
            {!collapsedSections['basic'] && (
              <div className="px-3 pb-3 space-y-2">
                <div>
                  <FieldLabel styles={styles}>{t('dw.pipeline.prop.nodeName')}</FieldLabel>
                  <input type="text" value={config.label || ''}
                    onChange={(e) => onUpdateNode(node.id, { label: e.target.value })}
                    className={inputCls(styles)}
                    placeholder={t('dw.pipeline.prop.nodeNamePlaceholder')} />
                </div>
                <div>
                  <FieldLabel styles={styles}>{t('dw.pipeline.prop.nodeType')}</FieldLabel>
                  <select value={config.nodeType || ''}
                    onChange={(e) => onUpdateNode(node.id, { nodeType: e.target.value as PipelineNodeType })}
                    className={inputCls(styles)}
                  >
                    {buildPaletteItems(styles, t).map((item) => (
                      <option key={item.type} value={item.type}>{item.label}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <FieldLabel styles={styles}>{t('dw.pipeline.prop.runStatus')}</FieldLabel>
                  <select value={config.nodeStatus || 'idle'}
                    onChange={(e) => onUpdateNode(node.id, { nodeStatus: e.target.value as NodeStatus })}
                    className={inputCls(styles)}
                  >
                    <option value="idle">{t('dw.pipeline.prop.statusIdle')}</option>
                    <option value="running">{t('dw.pipeline.prop.statusRunning')}</option>
                    <option value="success">{t('dw.pipeline.prop.statusSuccess')}</option>
                    <option value="error">{t('dw.pipeline.prop.statusError')}</option>
                  </select>
                </div>
              </div>
            )}
          </div>

          {/* ── P2-01 config forms (by nodeType) ── */}
          <div className={`border-b ${styles.cardBorder}`}>
            <SectionToggle collapsed={!!collapsedSections['config']} onClick={() => toggleSection('config')} label={t('dw.pipeline.prop.configSection')} styles={styles} />
            {!collapsedSections['config'] && (
              <div className="px-3 pb-3 space-y-2">
                {/* SOURCE_JDBC */}
                {nodeType === 'SOURCE_JDBC' && (
                  <>
                    <div>
                      <FieldLabel styles={styles}>{t('dw.pipeline.prop.datasourceId')}</FieldLabel>
                      <select value={nodeConfig.datasourceId || ''}
                        onChange={(e) => setConfigField('datasourceId', e.target.value)}
                        className={inputCls(styles)}
                      >
                        <option value="">{t('dw.pipeline.prop.selectConnection')}</option>
                        {connections.map((conn) => (
                          <option key={conn.id} value={conn.id}>{conn.name}</option>
                        ))}
                      </select>
                    </div>
                    <div>
                      <FieldLabel styles={styles}>{t('dw.pipeline.prop.sql')}</FieldLabel>
                      <textarea value={nodeConfig.sql || ''} rows={4}
                        onChange={(e) => setConfigField('sql', e.target.value)}
                        className={`${inputCls(styles)} font-mono`}
                        placeholder="SELECT * FROM ..." />
                    </div>
                    <div>
                      <FieldLabel styles={styles}>{t('dw.pipeline.prop.fetchSize')}</FieldLabel>
                      <input type="number" value={nodeConfig.fetchSize ?? 1000}
                        onChange={(e) => setConfigField('fetchSize', Number(e.target.value))}
                        className={inputCls(styles)} />
                    </div>
                    <div>
                      <FieldLabel styles={styles}>{t('dw.pipeline.prop.incrementalColumn')}</FieldLabel>
                      <input type="text" value={nodeConfig.incrementalColumn || ''}
                        onChange={(e) => setConfigField('incrementalColumn', e.target.value)}
                        className={inputCls(styles)}
                        placeholder={t('dw.pipeline.prop.incrementalColumnPlaceholder')} />
                    </div>
                  </>
                )}

                {/* SOURCE_CSV */}
                {nodeType === 'SOURCE_CSV' && (
                  <>
                    <div>
                      <FieldLabel styles={styles}>{t('dw.pipeline.prop.filePath')}</FieldLabel>
                      <input type="text" value={nodeConfig.filePath || ''}
                        onChange={(e) => setConfigField('filePath', e.target.value)}
                        className={inputCls(styles)}
                        placeholder="/data/orders.csv" />
                    </div>
                    <div>
                      <FieldLabel styles={styles}>{t('dw.pipeline.prop.delimiter')}</FieldLabel>
                      <input type="text" value={nodeConfig.delimiter ?? ','}
                        onChange={(e) => setConfigField('delimiter', e.target.value)}
                        className={inputCls(styles)} />
                    </div>
                    <div>
                      <FieldLabel styles={styles}>{t('dw.pipeline.prop.header')}</FieldLabel>
                      <select value={String(nodeConfig.header ?? true)}
                        onChange={(e) => setConfigField('header', e.target.value === 'true')}
                        className={inputCls(styles)}
                      >
                        <option value="true">{t('dw.pipeline.prop.true')}</option>
                        <option value="false">{t('dw.pipeline.prop.false')}</option>
                      </select>
                    </div>
                    <div>
                      <FieldLabel styles={styles}>{t('dw.pipeline.prop.encoding')}</FieldLabel>
                      <input type="text" value={nodeConfig.encoding ?? 'UTF-8'}
                        onChange={(e) => setConfigField('encoding', e.target.value)}
                        className={inputCls(styles)} />
                    </div>
                  </>
                )}

                {/* SOURCE_REST */}
                {nodeType === 'SOURCE_REST' && (
                  <>
                    <div>
                      <FieldLabel styles={styles}>{t('dw.pipeline.prop.url')}</FieldLabel>
                      <input type="text" value={nodeConfig.url || ''}
                        onChange={(e) => setConfigField('url', e.target.value)}
                        className={inputCls(styles)}
                        placeholder="https://api.example.com/data" />
                    </div>
                    <div>
                      <FieldLabel styles={styles}>{t('dw.pipeline.prop.method')}</FieldLabel>
                      <select value={nodeConfig.method ?? 'GET'}
                        onChange={(e) => setConfigField('method', e.target.value)}
                        className={inputCls(styles)}
                      >
                        <option value="GET">GET</option>
                        <option value="POST">POST</option>
                      </select>
                    </div>
                    <div>
                      <FieldLabel styles={styles}>{t('dw.pipeline.prop.headers')}</FieldLabel>
                      <div className="space-y-1">
                        {headerRows.map((row, idx) => (
                          <div key={idx} className="flex gap-1">
                            <input type="text" value={row.key}
                              onChange={(e) => {
                                const next = [...headerRows]; next[idx] = { ...next[idx], key: e.target.value };
                                setHeaderRows(next.length > 0 && next[next.length - 1].key ? [...next, { key: '', value: '' }] : next);
                              }}
                              className={`${inputCls(styles)} flex-1`}
                              placeholder={t('dw.pipeline.prop.headerKey')} />
                            <input type="text" value={row.value}
                              onChange={(e) => {
                                const next = [...headerRows]; next[idx] = { ...next[idx], value: e.target.value };
                                setHeaderRows(next.length > 0 && next[next.length - 1].key ? [...next, { key: '', value: '' }] : next);
                              }}
                              className={`${inputCls(styles)} flex-1`}
                              placeholder={t('dw.pipeline.prop.headerValue')} />
                          </div>
                        ))}
                      </div>
                    </div>
                    <div>
                      <FieldLabel styles={styles}>{t('dw.pipeline.prop.body')}</FieldLabel>
                      <textarea value={nodeConfig.body || ''} rows={3}
                        onChange={(e) => setConfigField('body', e.target.value)}
                        className={`${inputCls(styles)} font-mono`}
                        placeholder={'{"key": "value"}'} />
                    </div>
                    <div>
                      <FieldLabel styles={styles}>{t('dw.pipeline.prop.pagination')}</FieldLabel>
                      <input type="text" value={nodeConfig.pagination || ''}
                        onChange={(e) => setConfigField('pagination', e.target.value)}
                        className={inputCls(styles)}
                        placeholder={t('dw.pipeline.prop.paginationPlaceholder')} />
                    </div>
                  </>
                )}

                {/* SOURCE_CDC — disabled / flagship only */}
                {nodeType === 'SOURCE_CDC' && (
                  <div className={`px-2 py-3 rounded text-xs italic ${styles.cardTextMuted} ${styles.sidebarBg}`}>
                    {t('dw.pipeline.node.cdcFlagshipOnly')}
                  </div>
                )}

                {/* SOURCE_MINIO — 数据湖近源层读取（B6-1） */}
                {nodeType === 'SOURCE_MINIO' && (
                  <>
                    <div>
                      <FieldLabel styles={styles}>{t('dw.pipeline.prop.sourceMinioZone')}</FieldLabel>
                      <select value={nodeConfig.zone ?? 'STRUCTURED'}
                        onChange={(e) => setConfigField('zone', e.target.value as 'STRUCTURED' | 'UNSTRUCTURED')}
                        className={inputCls(styles)}
                      >
                        <option value="STRUCTURED">{t('dw.pipeline.prop.sourceMinioZoneStructured')}</option>
                        <option value="UNSTRUCTURED">{t('dw.pipeline.prop.sourceMinioZoneUnstructured')}</option>
                      </select>
                    </div>
                    <div>
                      <FieldLabel styles={styles}>{t('dw.pipeline.prop.sourceMinioSource')}</FieldLabel>
                      <input type="text" value={nodeConfig.source || ''}
                        onChange={(e) => setConfigField('source', e.target.value)}
                        className={inputCls(styles)}
                        placeholder="ds-1" />
                      <p className={`text-[10px] ${styles.muted} mt-0.5`}>
                        {t('dw.pipeline.prop.sourceMinioSourceHint')}
                      </p>
                    </div>
                    <div>
                      <FieldLabel styles={styles}>{t('dw.pipeline.prop.sourceMinioTable')}</FieldLabel>
                      <input type="text" value={nodeConfig.table || ''}
                        onChange={(e) => setConfigField('table', e.target.value)}
                        className={inputCls(styles)}
                        placeholder="orders" />
                    </div>
                    <div>
                      <FieldLabel styles={styles}>{t('dw.pipeline.prop.sourceMinioDocId')}</FieldLabel>
                      <input type="text" value={nodeConfig.docId || ''}
                        onChange={(e) => setConfigField('docId', e.target.value)}
                        className={inputCls(styles)}
                        placeholder="doc-1" />
                    </div>
                    <div>
                      <FieldLabel styles={styles}>{t('dw.pipeline.prop.sourceMinioFileName')}</FieldLabel>
                      <input type="text" value={nodeConfig.originalFileName || ''}
                        onChange={(e) => setConfigField('originalFileName', e.target.value)}
                        className={inputCls(styles)}
                        placeholder="manual.csv" />
                    </div>
                    <div>
                      <FieldLabel styles={styles}>{t('dw.pipeline.prop.sinkMinioObjectName')}</FieldLabel>
                      <input type="text" value={nodeConfig.objectName || ''}
                        onChange={(e) => setConfigField('objectName', e.target.value)}
                        className={inputCls(styles)}
                        placeholder="raw/structured/ds-1/orders/dt=2026-09-19/orders_20260919103000.csv" />
                      <p className={`text-[10px] ${styles.muted} mt-0.5`}>
                        {t('dw.pipeline.prop.sourceMinioObjectHint')}
                      </p>
                    </div>
                  </>
                )}

                {/* TRANSFORM_SQL */}
                {nodeType === 'TRANSFORM_SQL' && (
                  <>
                    <div>
                      <FieldLabel styles={styles}>{t('dw.pipeline.prop.transformSql')}</FieldLabel>
                      <textarea value={nodeConfig.transformSql || ''} rows={5}
                        onChange={(e) => setConfigField('transformSql', e.target.value)}
                        className={`${inputCls(styles)} font-mono`}
                        placeholder="SELECT id, upper(name) FROM ${input}" />
                    </div>
                    <div>
                      <FieldLabel styles={styles}>{t('dw.pipeline.prop.timeout')}</FieldLabel>
                      <input type="number" value={nodeConfig.timeout ?? 30}
                        onChange={(e) => setConfigField('timeout', Number(e.target.value))}
                        className={inputCls(styles)} />
                    </div>
                  </>
                )}

                {/* TRANSFORM_UDF (Wave 5) — udfId + params key-value */}
                {nodeType === 'TRANSFORM_UDF' && <UdfForm styles={styles} t={t} nodeConfig={nodeConfig} nodeId={node.id} onUpdateNode={onUpdateNode} />}

                {/* JOIN (Wave 5) — joinType + joinKeys */}
                {nodeType === 'JOIN' && <JoinForm styles={styles} t={t} nodeConfig={nodeConfig} nodeId={node.id} onUpdateNode={onUpdateNode} />}

                {/* SINK (Wave 5) — datasourceId + table + mode + batchSize */}
                {nodeType === 'SINK' && (
                  <>
                    <div>
                      <FieldLabel styles={styles}>{t('dw.pipeline.prop.sinkDatasourceId')}</FieldLabel>
                      <select value={nodeConfig.targetDatasourceId || nodeConfig.datasourceId || ''}
                        onChange={(e) => setConfigField('targetDatasourceId', e.target.value)}
                        className={inputCls(styles)}
                      >
                        <option value="">{t('dw.pipeline.prop.selectConnection')}</option>
                        {connections.map((conn) => (
                          <option key={conn.id} value={conn.id}>{conn.name}</option>
                        ))}
                      </select>
                    </div>
                    <div>
                      <FieldLabel styles={styles}>{t('dw.pipeline.prop.sinkTable')}</FieldLabel>
                      <input type="text" value={nodeConfig.targetTable || ''}
                        onChange={(e) => setConfigField('targetTable', e.target.value)}
                        className={inputCls(styles)}
                        placeholder="stg_orders" />
                    </div>
                    <div>
                      <FieldLabel styles={styles}>{t('dw.pipeline.prop.sinkMode')}</FieldLabel>
                      <select value={nodeConfig.mode ?? 'append'}
                        onChange={(e) => setConfigField('mode', e.target.value as 'append' | 'overwrite')}
                        className={inputCls(styles)}
                      >
                        <option value="append">{t('dw.pipeline.prop.modeAppend')}</option>
                        <option value="overwrite">{t('dw.pipeline.prop.modeOverwrite')}</option>
                      </select>
                    </div>
                    <div>
                      <FieldLabel styles={styles}>{t('dw.pipeline.prop.sinkBatchSize')}</FieldLabel>
                      <input type="number" value={nodeConfig.batchSize ?? 1000}
                        onChange={(e) => setConfigField('batchSize', Number(e.target.value))}
                        className={inputCls(styles)} />
                    </div>
                  </>
                )}

                {/* SINK_MINIO — 数据采集 → 数据湖近源库 */}
                {nodeType === 'SINK_MINIO' && (
                  <>
                    <div>
                      <FieldLabel styles={styles}>{t('dw.pipeline.prop.sinkMinioTable')}</FieldLabel>
                      <input type="text" value={nodeConfig.table || ''}
                        onChange={(e) => setConfigField('table', e.target.value)}
                        className={inputCls(styles)}
                        placeholder="orders" />
                    </div>
                    <div>
                      <FieldLabel styles={styles}>{t('dw.pipeline.prop.sinkMinioObjectName')}</FieldLabel>
                      <input type="text" value={nodeConfig.objectName || ''}
                        onChange={(e) => setConfigField('objectName', e.target.value)}
                        className={inputCls(styles)}
                        placeholder="datalake/orders_20260101.csv" />
                      <p className={`text-[10px] ${styles.muted} mt-0.5`}>
                        {t('dw.pipeline.prop.sinkMinioObjectHint')}
                      </p>
                    </div>
                    <div>
                      <FieldLabel styles={styles}>{t('dw.pipeline.prop.sinkMinioBucket')}</FieldLabel>
                      <input type="text" value={nodeConfig.bucket || ''}
                        onChange={(e) => setConfigField('bucket', e.target.value)}
                        className={inputCls(styles)}
                        placeholder="ecos-datalake" />
                    </div>
                    <div>
                      <FieldLabel styles={styles}>{t('dw.pipeline.prop.sinkMinioFormat')}</FieldLabel>
                      <select value={nodeConfig.format ?? 'csv'}
                        onChange={(e) => setConfigField('format', e.target.value as 'csv')}
                        className={inputCls(styles)}
                      >
                        <option value="csv">CSV</option>
                      </select>
                    </div>
                  </>
                )}

                {/* OUTPUT_OBJECT */}
                {nodeType === 'OUTPUT_OBJECT' && (
                  <>
                    <div>
                      <FieldLabel styles={styles}>{t('dw.pipeline.prop.targetTable')}</FieldLabel>
                      <input type="text" value={nodeConfig.targetTable || ''}
                        onChange={(e) => setConfigField('targetTable', e.target.value)}
                        className={inputCls(styles)}
                        placeholder="ods_orders" />
                    </div>
                    <div>
                      <FieldLabel styles={styles}>{t('dw.pipeline.prop.mode')}</FieldLabel>
                      <select value={nodeConfig.mode ?? 'append'}
                        onChange={(e) => setConfigField('mode', e.target.value as 'append' | 'overwrite')}
                        className={inputCls(styles)}
                      >
                        <option value="append">{t('dw.pipeline.prop.modeAppend')}</option>
                        <option value="overwrite">{t('dw.pipeline.prop.modeOverwrite')}</option>
                      </select>
                    </div>
                    <div>
                      <FieldLabel styles={styles}>{t('dw.pipeline.prop.batchSize')}</FieldLabel>
                      <input type="number" value={nodeConfig.batchSize ?? 1000}
                        onChange={(e) => setConfigField('batchSize', Number(e.target.value))}
                        className={inputCls(styles)} />
                    </div>
                  </>
                )}
              </div>
            )}
          </div>
        </div>
      </div>
    );
  }
);

export default PropertyPanel;
