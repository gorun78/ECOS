/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { LinkType, ObjectType, Dataset } from '../../types/ontology';
import LucideIcon from './LucideIcon';
import { useTheme } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';

interface LinkTypeViewProps {
  linkType: LinkType;
  objectTypes: ObjectType[];
  datasets: Dataset[];
  onUpdate: (updated: LinkType) => void;
  onDelete: (id: string) => void;
  onNavigateToObject: (objectId: string) => void;
}

export default function LinkTypeView({
  linkType,
  objectTypes,
  datasets,
  onUpdate,
  onDelete,
  onNavigateToObject
}: LinkTypeViewProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const sourceObj = objectTypes.find(o => o.id === linkType.sourceObjectType);
  const targetObj = objectTypes.find(o => o.id === linkType.targetObjectType);

  const selectedDataset = datasets.find(d => d.id === linkType.mapping.datasetId) || datasets[0];

  const handleFieldChange = (key: keyof LinkType, value: any) => {
    onUpdate({
      ...linkType,
      [key]: value
    });
  };

  const handleMappingFieldChange = (key: string, value: any) => {
    onUpdate({
      ...linkType,
      mapping: {
        ...linkType.mapping,
        [key]: value
      }
    });
  };

  const handleFkChange = (key: 'sourceKey' | 'targetKey', value: string) => {
    onUpdate({
      ...linkType,
      mapping: {
        ...linkType.mapping,
        foreignKeyMapping: {
          sourceKey: linkType.mapping.foreignKeyMapping?.sourceKey || '',
          targetKey: linkType.mapping.foreignKeyMapping?.targetKey || '',
          [key]: value
        }
      }
    });
  };

  const handleJoinChange = (key: 'sourceKey' | 'joinSourceKey' | 'joinTargetKey' | 'targetKey', value: string) => {
    onUpdate({
      ...linkType,
      mapping: {
        ...linkType.mapping,
        joinTableMapping: {
          sourceKey: linkType.mapping.joinTableMapping?.sourceKey || '',
          joinSourceKey: linkType.mapping.joinTableMapping?.joinSourceKey || '',
          joinTargetKey: linkType.mapping.joinTableMapping?.joinTargetKey || '',
          targetKey: linkType.mapping.joinTableMapping?.targetKey || '',
          [key]: value
        }
      }
    });
  };

  return (
    <div className={`flex flex-col h-full ${styles.cardBg}`}>
      {/* Detail Header */}
      <div className={`px-6 py-4 border-b ${styles.appBorder} flex justify-between items-center ${styles.appBg}`}>
        <div className="flex items-center gap-3">
          <div className={`p-2.5 rounded-lg border ${styles.appBorder} ${styles.cardBg} ${styles.cardText} flex items-center justify-center`}>
            <LucideIcon name="GitMerge" size={20} />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className={`text-lg font-semibold ${styles.cardText}`}>{linkType.displayName}</h2>
              <span className={`text-xs font-mono ${styles.sidebarBg} ${styles.cardTextMuted} px-1.5 py-0.5 rounded`}>
                {linkType.apiName}
              </span>
              <span className="text-xs bg-blue-50 text-blue-700 px-2 py-0.5 rounded-full font-semibold">
                {linkType.cardinality} {t('ow.link.badge')}
              </span>
            </div>
            <p className={`text-xs ${styles.cardTextMuted} mt-0.5`}>{linkType.description || t('ow.link.noDesc')}</p>
          </div>
        </div>
        <button
          type="button"
          onClick={() => onDelete(linkType.id)}
          className={`text-xs ${styles.dangerText} hover:opacity-80 px-2.5 py-1.5 rounded border ${styles.dangerBorder} transition-colors flex items-center gap-1.5`}
        >
          <LucideIcon name="Trash2" size={13} />
          {t('ow.link.delete')}
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-6 space-y-8">
        {/* Visual Link Diagram */}
        <div className={`${styles.appBg} border ${styles.appBorder} rounded-xl p-6`}>
          <h3 className={`text-xs font-semibold ${styles.cardText} mb-4 flex items-center gap-1.5`}>
            <LucideIcon name="Eye" size={14} className={styles.cardTextMuted} />
            {t('ow.link.topology')}
          </h3>
          <div className="flex items-center justify-center gap-6">
            {/* Source Object */}
            {sourceObj ? (
              <div
                onClick={() => onNavigateToObject(sourceObj.id)}
                className={`w-36 p-3 rounded-lg border-2 ${styles.cardBg} flex flex-col items-center justify-center cursor-pointer hover:shadow-xs transition-shadow ${sourceObj.color}`}
              >
                <LucideIcon name={sourceObj.icon} size={18} />
                <span className="text-xs font-semibold mt-1">{sourceObj.displayName}</span>
                <span className={`text-[10px] ${styles.cardTextMuted} font-mono mt-0.5`}>{sourceObj.id}</span>
              </div>
            ) : (
              <div className="w-36 p-3 rounded-lg border-2 border-dashed border-red-300 bg-red-50 flex flex-col items-center justify-center text-red-600">
                <span>{t('ow.link.sourceUnset')}</span>
              </div>
            )}

            {/* Link line with arrows and cardinality */}
            <div className="flex-1 max-w-[120px] flex flex-col items-center justify-center relative">
              <div className={`text-[10px] font-bold ${styles.cardTextMuted} ${styles.sidebarBg} px-2 py-0.5 rounded-full font-mono mb-2`}>
                {linkType.cardinality}
              </div>
              <div className={`w-full h-0.5 ${styles.appBorder} relative flex items-center justify-center`}>
                <div className={`absolute right-0 w-1.5 h-1.5 border-t-2 border-r-2 ${styles.cardTextMuted.replace('text-', 'border-')} transform rotate-45`} />
              </div>
              <div className={`text-[10px] ${styles.cardTextMuted} font-medium mt-1 truncate max-w-full`}>
                {linkType.displayName}
              </div>
            </div>

            {/* Target Object */}
            {targetObj ? (
              <div
                onClick={() => onNavigateToObject(targetObj.id)}
                className={`w-36 p-3 rounded-lg border-2 ${styles.cardBg} flex flex-col items-center justify-center cursor-pointer hover:shadow-xs transition-shadow ${targetObj.color}`}
              >
                <LucideIcon name={targetObj.icon} size={18} />
                <span className="text-xs font-semibold mt-1">{targetObj.displayName}</span>
                <span className={`text-[10px] ${styles.cardTextMuted} font-mono mt-0.5`}>{targetObj.id}</span>
              </div>
            ) : (
              <div className="w-36 p-3 rounded-lg border-2 border-dashed border-red-300 bg-red-50 flex flex-col items-center justify-center text-red-600">
                <span>{t('ow.link.targetUnset')}</span>
              </div>
            )}
          </div>
        </div>

        {/* Configurations */}
        <div className="grid grid-cols-2 gap-6">
          <div className="space-y-4">
            <h4 className={`text-xs font-semibold ${styles.cardText} border-b border-gray-100 pb-2`}>{t('ow.link.basicInfo')}</h4>
            <div className="space-y-3">
              <div className="space-y-1">
                <label className={`text-xs ${styles.cardTextMuted} font-medium`}>{t('ow.link.label.displayName')}</label>
                <input
                  type="text"
                  value={linkType.displayName}
                  onChange={e => handleFieldChange('displayName', e.target.value)}
                  className="w-full px-3 py-1.5 text-xs border border-gray-300 rounded focus:border-blue-500 focus:outline-hidden"
                />
              </div>
              <div className="space-y-1">
                <label className={`text-xs ${styles.cardTextMuted} font-medium`}>{t('ow.link.label.apiName')}</label>
                <input
                  type="text"
                  value={linkType.apiName}
                  onChange={e => handleFieldChange('apiName', e.target.value)}
                  className="w-full px-3 py-1.5 text-xs border border-gray-300 rounded focus:border-blue-500 focus:outline-hidden"
                />
              </div>
              <div className="space-y-1">
                <label className={`text-xs ${styles.cardTextMuted} font-medium`}>{t('ow.link.label.cardinality')}</label>
                <select
                  value={linkType.cardinality}
                  onChange={e => handleFieldChange('cardinality', e.target.value as any)}
                  className="w-full px-3 py-1.5 text-xs border border-gray-300 rounded bg-white focus:outline-hidden"
                >
                  <option value="1:1">1:1 {t('ow.link.card.11')}</option>
                  <option value="1:N">1:N {t('ow.link.card.1n')}</option>
                  <option value="N:1">N:1 {t('ow.link.card.n1')}</option>
                  <option value="M:N">M:N {t('ow.link.card.mn')}</option>
                </select>
              </div>
              <div className="space-y-1">
                <label className={`text-xs ${styles.cardTextMuted} font-medium`}>{t('ow.link.label.description')}</label>
                <textarea
                  value={linkType.description}
                  onChange={e => handleFieldChange('description', e.target.value)}
                  className="w-full h-16 px-3 py-1.5 text-xs border border-gray-300 rounded focus:border-blue-500 focus:outline-hidden"
                />
              </div>
            </div>
          </div>

          {/* Mapping settings */}
          <div className="space-y-4">
            <h4 className={`text-xs font-semibold ${styles.cardText} border-b ${styles.divider} pb-2`}>{t('ow.link.section.mapping')}</h4>
            <div className="space-y-3">
              <div className="space-y-1">
                <label className={`text-xs ${styles.cardText} font-medium`}>{t('ow.link.label.mappingStrategy')}</label>
                <select
                  value={linkType.mapping.type}
                  onChange={e => handleMappingFieldChange('type', e.target.value as any)}
                  className="w-full px-3 py-1.5 text-xs border border-gray-300 rounded bg-white focus:outline-hidden"
                >
                  <option value="foreign_key">{t('ow.link.strategy.foreignKey')}</option>
                  <option value="join_table">{t('ow.link.strategy.joinTable')}</option>
                </select>
              </div>

              {/* FOREIGN KEY CONFIG */}
              {linkType.mapping.type === 'foreign_key' && (
                <div className={`${styles.appBg} p-4 rounded-lg border ${styles.appBorder} space-y-3 text-xs`}>
                  <div className={`font-semibold ${styles.cardText} text-[11px] mb-1`}>{t('ow.link.fk.configTitle')}</div>
                  <div className="grid grid-cols-2 gap-3">
                    <div className="space-y-1">
                      <label className={`text-[10px] ${styles.cardTextMuted}`}>{t('ow.link.fk.sourceKey', { name: sourceObj?.displayName })}</label>
                      <select
                        value={linkType.mapping.foreignKeyMapping?.sourceKey || ''}
                        onChange={e => handleFkChange('sourceKey', e.target.value)}
                        className="w-full px-2 py-1 text-xs border border-gray-300 rounded bg-white"
                      >
                        <option value="">{t('ow.link.selectProp')}</option>
                        {sourceObj?.properties.map(p => (
                          <option key={p.id} value={p.id}>{p.displayName} ({p.id})</option>
                        ))}
                      </select>
                    </div>

                    <div className="space-y-1">
                      <label className={`text-[10px] ${styles.cardTextMuted}`}>{t('ow.link.fk.targetKey', { name: targetObj?.displayName })}</label>
                      <select
                        value={linkType.mapping.foreignKeyMapping?.targetKey || ''}
                        onChange={e => handleFkChange('targetKey', e.target.value)}
                        className="w-full px-2 py-1 text-xs border border-gray-300 rounded bg-white"
                      >
                        <option value="">{t('ow.link.selectProp')}</option>
                        {targetObj?.properties.map(p => (
                          <option key={p.id} value={p.id}>{p.displayName} ({p.id})</option>
                        ))}
                      </select>
                    </div>
                  </div>
                  <p className={`text-[9px] ${styles.cardTextMuted} mt-1 leading-relaxed`}>
                    {t('ow.link.fk.hint')}
                  </p>
                </div>
              )}

              {/* JOIN TABLE CONFIG */}
              {linkType.mapping.type === 'join_table' && (
                <div className={`${styles.appBg} p-4 rounded-lg border ${styles.appBorder} space-y-3 text-xs`}>
                  <div className={`font-semibold ${styles.cardText} text-[11px] mb-1`}>{t('ow.link.jt.configTitle')}</div>
                  
                  <div className="space-y-1">
                    <label className={`text-[10px] ${styles.cardTextMuted}`}>{t('ow.link.jt.datasetLabel')}</label>
                    <select
                      value={linkType.mapping.datasetId || ''}
                      onChange={e => handleMappingFieldChange('datasetId', e.target.value)}
                      className="w-full px-2 py-1 text-xs border border-gray-300 rounded bg-white"
                    >
                      <option value="">{t('ow.link.jt.datasetPlaceholder')}</option>
                      {datasets.map(ds => (
                        <option key={ds.id} value={ds.id}>{ds.name} ({ds.id})</option>
                      ))}
                    </select>
                  </div>

                  <div className={`grid grid-cols-2 gap-3 border-t ${styles.divider} pt-2.5`}>
                    <div className="space-y-1">
                      <label className={`text-[10px] ${styles.cardTextMuted}`}>{t('ow.link.jt.sourceKey', { name: sourceObj?.displayName })}</label>
                      <select
                        value={linkType.mapping.joinTableMapping?.sourceKey || ''}
                        onChange={e => handleJoinChange('sourceKey', e.target.value)}
                        className="w-full px-2 py-1 text-xs border border-gray-300 rounded bg-white"
                      >
                        <option value="">{t('ow.link.jt.sourcePropPlaceholder')}</option>
                        {sourceObj?.properties.map(p => (
                          <option key={p.id} value={p.id}>{p.displayName} ({p.id})</option>
                        ))}
                      </select>
                    </div>

                    <div className="space-y-1">
                      <label className={`text-[10px] ${styles.cardTextMuted}`}>{t('ow.link.jt.joinSourceKey')}</label>
                      <select
                        value={linkType.mapping.joinTableMapping?.joinSourceKey || ''}
                        onChange={e => handleJoinChange('joinSourceKey', e.target.value)}
                        className="w-full px-2 py-1 text-xs border border-gray-300 rounded bg-white font-mono"
                      >
                        <option value="">{t('ow.link.jt.columnPlaceholder')}</option>
                        {selectedDataset?.columns.map(col => (
                          <option key={col.name} value={col.name}>{col.name}</option>
                        ))}
                      </select>
                    </div>

                    <div className="space-y-1">
                      <label className={`text-[10px] ${styles.cardTextMuted}`}>{t('ow.link.jt.targetKey', { name: targetObj?.displayName })}</label>
                      <select
                        value={linkType.mapping.joinTableMapping?.targetKey || ''}
                        onChange={e => handleJoinChange('targetKey', e.target.value)}
                        className="w-full px-2 py-1 text-xs border border-gray-300 rounded bg-white"
                      >
                        <option value="">{t('ow.link.jt.targetPropPlaceholder')}</option>
                        {targetObj?.properties.map(p => (
                          <option key={p.id} value={p.id}>{p.displayName} ({p.id})</option>
                        ))}
                      </select>
                    </div>

                    <div className="space-y-1">
                      <label className={`text-[10px] ${styles.cardTextMuted}`}>{t('ow.link.jt.joinTargetKey')}</label>
                      <select
                        value={linkType.mapping.joinTableMapping?.joinTargetKey || ''}
                        onChange={e => handleJoinChange('joinTargetKey', e.target.value)}
                        className="w-full px-2 py-1 text-xs border border-gray-300 rounded bg-white font-mono"
                      >
                        <option value="">{t('ow.link.jt.columnPlaceholder')}</option>
                        {selectedDataset?.columns.map(col => (
                          <option key={col.name} value={col.name}>{col.name}</option>
                        ))}
                      </select>
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
