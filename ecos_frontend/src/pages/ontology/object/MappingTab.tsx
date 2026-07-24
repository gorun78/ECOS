/**
 * MappingTab — 数据源映射 Tab
 * @license Apache-2.0
 */
import React from 'react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { Database, Table, Layers, Wand2, Save } from 'lucide-react';
import DynamicIcon from '../../../components/ontology/DynamicIcon';
import type { MappingTabProps } from './types';

export default function MappingTab({
  objectType,
  datasets,
  selectedDataset,
  handleDatasetChange,
  handleAutoMap,
  handlePropMappingChange,
  mappingDirty,
  onSaveMapping,
}: MappingTabProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  const mapping = objectType.mapping || { datasetId: '', propertyMappings: {} };
  return (
    <div className="space-y-6">
      <div className={`${styles.appBg} border ${styles.cardBorder} rounded-lg p-4 flex justify-between items-center`}>
        <div className="flex items-center gap-3">
          <Database className={styles.cardTextMuted} size={18} />
          <div>
            <div className={`text-xs font-semibold ${styles.cardText}`}>{t('ow.label.boundDataset')}</div>
            <div className={`text-[10px] ${styles.cardTextMuted} font-mono mt-0.5`}>{selectedDataset?.path}</div>
          </div>
        </div>
        <div className="flex gap-2">
          {mappingDirty && (
            <span className={`text-[10px] ${styles.accentText} font-semibold flex items-center gap-1 self-center`}>
              {t('ow.label.mappingDirty')}
            </span>
          )}
          <button
            onClick={onSaveMapping}
            disabled={!mappingDirty}
            className={`${mappingDirty ? styles.accentText : styles.cardTextMuted} ${
              mappingDirty ? 'border-emerald-300 bg-emerald-50' : styles.cardBorder
            } text-xs px-3 py-1.5 rounded font-medium transition-colors flex items-center gap-1 border`}
          >
            <Save size={13} />
            {t('ow.btn.saveMapping')}
          </button>
          <select
            value={mapping.datasetId}
            onChange={e => handleDatasetChange(e.target.value)}
            className={`px-3 py-1.5 text-xs border border-slate-300 rounded ${styles.cardBg} focus:outline-hidden`}
          >
            {datasets.map(ds => (
              <option key={ds.id} value={ds.id}>{ds.name}</option>
            ))}
          </select>
          <button
            onClick={handleAutoMap}
            className={`${styles.sidebarBg} hover:bg-slate-300 ${styles.sidebarText} text-xs px-3 py-1.5 rounded font-medium transition-colors flex items-center gap-1`}
          >
            <Wand2 size={13} />
            {t('ow.btn.smartMap')}
          </button>
        </div>
      </div>

      {/* Split Screen Mapping Grid */}
      <div className="grid grid-cols-2 gap-8 relative">
        {/* Left Column: Raw Datasource */}
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <div className={`text-xs font-semibold ${styles.cardText} flex items-center gap-1`}>
              <Table size={14} />
              {t('ow.label.rawSchema')}
            </div>
            <span className={`text-[10px] ${styles.sidebarBg} ${styles.cardTextMuted} px-1.5 py-0.5 rounded font-mono`}>
              {selectedDataset?.columns.length} {t('ow.label.columns')}
            </span>
          </div>
          <div className={`border ${styles.cardBorder} rounded-lg ${styles.cardBg} overflow-hidden divide-y divide-slate-100`}>
            {selectedDataset?.columns.map(col => (
              <div key={col.name} className={`flex justify-between items-center px-4 py-2.5 hover:bg-slate-50/50`}>
                <div className={`font-mono text-xs font-medium ${styles.sidebarText}`}>{col.name}</div>
                <div className={`text-[10px] ${styles.muted} font-mono italic uppercase ${styles.sidebarBg} px-1.5 py-0.5 rounded`}>
                  {col.type}
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* Right Column: Object Properties Mapping */}
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <div className={`text-xs font-semibold ${styles.cardText} flex items-center gap-1`}>
              <Layers size={14} />
              {t('ow.label.propertyMappings')}
            </div>
            <span className={`text-[10px] ${styles.badgeBg} ${styles.accentText} px-1.5 py-0.5 rounded font-mono`}>
              {Object.keys(mapping.propertyMappings).length} / {objectType.properties.length} {t('ow.label.mapped')}
            </span>
          </div>
          <div className={`border ${styles.cardBorder} rounded-lg ${styles.cardBg} overflow-hidden divide-y divide-slate-100`}>
            {objectType.properties.map(prop => {
              const mappedCol = mapping.propertyMappings[prop.id] || '';
              return (
                <div key={prop.id} className={`flex justify-between items-center px-4 py-2.5 ${styles.cardBg} hover:bg-slate-50/50`}>
                  <div className="flex items-center gap-2">
                    <span className={objectType.primaryKey === prop.id ? 'text-amber-500' : styles.muted}>
                      <DynamicIcon name={objectType.primaryKey === prop.id ? 'Key' : 'CircleDot'} size={12} />
                    </span>
                    <div>
                      <div className={`text-xs font-medium ${styles.cardText}`}>{prop.displayName}</div>
                      <div className={`text-[10px] ${styles.muted} font-mono mt-0.5`}>{prop.id} · {prop.dataType}</div>
                    </div>
                  </div>

                  {/* Mapped Selector */}
                  <div className="flex items-center gap-2">
                    <span className={`${styles.muted} text-[10px]`}>←</span>
                    <select
                      value={mappedCol}
                      onChange={e => handlePropMappingChange(prop.id, e.target.value)}
                      className={`px-2 py-1 text-xs border rounded ${styles.cardBg} focus:outline-hidden font-mono ${styles.sidebarText} ${
                        mappedCol ? 'border-emerald-300 bg-emerald-50/30' : 'border-amber-300 bg-amber-50/10'
                      }`}
                    >
                      <option value="">{t('ow.placeholder.unmapped')}</option>
                      {selectedDataset?.columns.map(col => (
                        <option key={col.name} value={col.name}>{col.name}</option>
                      ))}
                    </select>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
}
