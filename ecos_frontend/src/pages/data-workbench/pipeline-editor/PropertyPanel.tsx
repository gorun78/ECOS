/**
 * PropertyPanel — Pipeline node property editor
 * Extracted from PipelineFlowEditor.tsx
 * Aligned with P2-01 node config schema (PMO-3J T2).
 * @license Apache-2.0
 */

import React, { useState, useCallback, useEffect } from 'react';
import type { Node } from '@xyflow/react';
import { Trash2, X, ChevronDown } from 'lucide-react';
import type { NodeConfig, NodeStatus, PipelineNodeType } from './types';
import type { DataConnection } from '../types';
import { PALETTE_LABELS, buildPaletteItems } from './constants';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
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

// ─── Property Panel ───────────────────────────────────────

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
                      {(() => {
                        const hdrs: Record<string, string> = {};
                        headerRows.forEach((r) => { if (r.key) hdrs[r.key] = r.value; });
                        if (JSON.stringify(hdrs) !== JSON.stringify(nodeConfig.headers || {})) {
                          setConfigField('headers', hdrs);
                        }
                        return null;
                      })()}
                      {(() => {
                        const hdrs: Record<string, string> = {};
                        headerRows.forEach((r) => { if (r.key) hdrs[r.key] = r.value; });
                        if (JSON.stringify(hdrs) !== JSON.stringify(nodeConfig.headers || {})) {
                          setConfigField('headers', hdrs);
                        }
                        return null;
                      })()}
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
