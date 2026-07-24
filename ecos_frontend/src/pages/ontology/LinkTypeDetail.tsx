/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { Eye, GitMerge, Trash2 } from 'lucide-react';
import * as LucideIcons from 'lucide-react';
import { useLanguage } from '../../components/LanguageContext';
import { useTheme } from '../../components/ThemeContext';
function DynamicIcon({ name, size = 14, className }: { name: string; size?: number; className?: string }) {
  const IconComponent = (LucideIcons as any)[name] || LucideIcons.HelpCircle;
  return <IconComponent size={size} className={className} />;
}


import { LinkType, ObjectType, Dataset } from '../../types/ontology';
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
  const { t } = useLanguage();
  const { styles } = useTheme();
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
      <div className={`px-6 py-4 border-b ${styles.cardBorder} flex justify-between items-center ${styles.appBg}`}>
        <div className="flex items-center gap-3">
          <div className={`p-2.5 rounded-lg border ${styles.cardBorder} ${styles.cardBg} ${styles.cardTextMuted} flex items-center justify-center`}>
            <GitMerge size={20} />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className={`text-lg font-semibold ${styles.cardText}`}>{linkType.displayName}</h2>
              <span className={`text-xs font-mono ${styles.badgeBg} ${styles.cardTextMuted} px-1.5 py-0.5 rounded`}>
                {linkType.apiName}
              </span>
              <span className="text-xs bg-blue-50 text-blue-700 px-2 py-0.5 rounded-full font-semibold">
                {linkType.cardinality} {t('ow.label.association')}
              </span>
            </div>
            <p className={`text-xs ${styles.muted} mt-0.5`}>{linkType.description || t('ow.empty.noDescription')}</p>
          </div>
        </div>
        <button
          onClick={() => onDelete(linkType.id)}
          className="text-xs text-red-500 hover:bg-red-50 px-2.5 py-1.5 rounded border border-red-200 transition-colors flex items-center gap-1.5"
        >
          <Trash2 size={13} />
          {t('ow.btn.deleteLink')}
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-6 space-y-8">
        {/* Visual Link Diagram */}
        <div className={`${styles.appBg} border ${styles.cardBorder} rounded-xl p-6`}>
          <h3 className={`text-xs font-semibold ${styles.cardText} mb-4 flex items-center gap-1.5`}>
            <Eye size={14} className={styles.muted} />
            {t('ow.section.linkVisualization')}
          </h3>
          <div className="flex items-center justify-center gap-6">
            {/* Source Object */}
            {sourceObj ? (
              <div
                onClick={() => onNavigateToObject(sourceObj.id)}
                className={`w-36 p-3 rounded-lg border-2 ${styles.cardBg} flex flex-col items-center justify-center cursor-pointer hover:shadow-xs transition-shadow ${sourceObj.color}`}
              >
                <DynamicIcon name={sourceObj.icon} size={18} />
                <span className="text-xs font-semibold mt-1">{sourceObj.displayName}</span>
                <span className={`text-[10px] ${styles.muted} font-mono mt-0.5`}>{sourceObj.id}</span>
              </div>
            ) : (
              <div className="w-36 p-3 rounded-lg border-2 border-dashed border-red-300 bg-red-50 flex flex-col items-center justify-center text-red-600">
                <span>{t('ow.empty.sourceNotConfigured')}</span>
              </div>
            )}

            {/* Link line with arrows and cardinality */}
            <div className="flex-1 max-w-[120px] flex flex-col items-center justify-center relative">
              <div className={`text-[10px] font-bold ${styles.muted} bg-slate-200 px-2 py-0.5 rounded-full font-mono mb-2`}>
                {linkType.cardinality}
              </div>
              <div className="w-full h-0.5 bg-slate-300 relative flex items-center justify-center">
                <div className="absolute right-0 w-1.5 h-1.5 border-t-2 border-r-2 border-slate-400 transform rotate-45" />
              </div>
              <div className={`text-[10px] ${styles.muted} font-medium mt-1 truncate max-w-full`}>
                {linkType.displayName}
              </div>
            </div>

            {/* Target Object */}
            {targetObj ? (
              <div
                onClick={() => onNavigateToObject(targetObj.id)}
                className={`w-36 p-3 rounded-lg border-2 ${styles.cardBg} flex flex-col items-center justify-center cursor-pointer hover:shadow-xs transition-shadow ${targetObj.color}`}
              >
                <DynamicIcon name={targetObj.icon} size={18} />
                <span className="text-xs font-semibold mt-1">{targetObj.displayName}</span>
                <span className={`text-[10px] ${styles.muted} font-mono mt-0.5`}>{targetObj.id}</span>
              </div>
            ) : (
              <div className="w-36 p-3 rounded-lg border-2 border-dashed border-red-300 bg-red-50 flex flex-col items-center justify-center text-red-600">
                <span>{t('ow.empty.targetNotConfigured')}</span>
              </div>
            )}
          </div>
        </div>

        {/* Configurations */}
        <div className="grid grid-cols-2 gap-6">
          <div className="space-y-4">
            <h4 className={`text-xs font-semibold ${styles.cardText} border-b border-gray-100 pb-2`}>{t('ow.section.basicInfo')}</h4>
            <div className="space-y-3">
              <div className="space-y-1">
                <label className={`text-xs ${styles.cardTextMuted} font-medium`}>{t('ow.label.displayName')}</label>
                <input
                  type="text"
                  value={linkType.displayName}
                  onChange={e => handleFieldChange('displayName', e.target.value)}
                  className={`w-full px-3 py-1.5 text-xs border ${styles.inputBorder} rounded focus:border-blue-500 focus:outline-hidden`}
                />
              </div>
              <div className="space-y-1">
                <label className={`text-xs ${styles.cardTextMuted} font-medium`}>{t('ow.label.apiName')}</label>
                <input
                  type="text"
                  value={linkType.apiName}
                  onChange={e => handleFieldChange('apiName', e.target.value)}
                  className={`w-full px-3 py-1.5 text-xs border ${styles.inputBorder} rounded focus:border-blue-500 focus:outline-hidden`}
                />
              </div>
              <div className="space-y-1">
                <label className={`text-xs ${styles.cardTextMuted} font-medium`}>{t('ow.label.cardinality')}</label>
                <select
                  value={linkType.cardinality}
                  onChange={e => handleFieldChange('cardinality', e.target.value as any)}
                  className={`w-full px-3 py-1.5 text-xs border ${styles.inputBorder} rounded ${styles.cardBg} focus:outline-hidden`}
                >
                  <option value="1:1">{t('ow.link.cardinality11')}</option>
                  <option value="1:N">{t('ow.link.cardinality1N')}</option>
                  <option value="N:1">{t('ow.link.cardinalityN1')}</option>
                  <option value="M:N">{t('ow.link.cardinalityMN')}</option>
                </select>
              </div>
              <div className="space-y-1">
                <label className={`text-xs ${styles.cardTextMuted} font-medium`}>{t('ow.label.description')}</label>
                <textarea
                  value={linkType.description}
                  onChange={e => handleFieldChange('description', e.target.value)}
                  className={`w-full h-16 px-3 py-1.5 text-xs border ${styles.inputBorder} rounded focus:border-blue-500 focus:outline-hidden`}
                />
              </div>
            </div>
          </div>

          {/* Mapping settings */}
          <div className="space-y-4">
            <h4 className={`text-xs font-semibold ${styles.cardText} border-b border-gray-100 pb-2`}>{t('ow.section.dbMapping')}</h4>
            
            <div className="space-y-3">
              <div className="space-y-1">
                <label className={`text-xs ${styles.cardTextMuted} font-medium`}>{t('ow.label.mappingStrategy')}</label>
                <select
                  value={linkType.mapping.type}
                  onChange={e => handleMappingFieldChange('type', e.target.value as any)}
                  className={`w-full px-3 py-1.5 text-xs border ${styles.inputBorder} rounded ${styles.cardBg} focus:outline-hidden`}
                >
                  <option value="foreign_key">{t('ow.link.foreignKeyMapping')}</option>
                  <option value="join_table">{t('ow.link.joinTableMapping')}</option>
                </select>
              </div>

              {/* FOREIGN KEY CONFIG */}
              {linkType.mapping.type === 'foreign_key' && (
                <div className={`${styles.appBg} p-4 rounded-lg border ${styles.cardBorder} space-y-3 text-xs`}>
                  <div className={`font-semibold ${styles.cardText} text-[11px] mb-1`}>{t('ow.section.fkConfig')}</div>
                  <div className="grid grid-cols-2 gap-3">
                    <div className="space-y-1">
                      <label className={`text-[10px] ${styles.muted}`}>{t('ow.label.sourceForeignKey')} ({sourceObj?.displayName})</label>
                      <select
                        value={linkType.mapping.foreignKeyMapping?.sourceKey || ''}
                        onChange={e => handleFkChange('sourceKey', e.target.value)}
                        className={`w-full px-2 py-1 text-xs border ${styles.inputBorder} rounded ${styles.cardBg}`}
                      >
                        <option value="">{t('ow.placeholder.selectProperty')}</option>
                        {sourceObj?.properties.map(p => (
                          <option key={p.id} value={p.id}>{p.displayName} ({p.id})</option>
                        ))}
                      </select>
                    </div>

                    <div className="space-y-1">
                      <label className={`text-[10px] ${styles.muted}`}>{t('ow.label.targetForeignKey')} ({targetObj?.displayName})</label>
                      <select
                        value={linkType.mapping.foreignKeyMapping?.targetKey || ''}
                        onChange={e => handleFkChange('targetKey', e.target.value)}
                        className={`w-full px-2 py-1 text-xs border ${styles.inputBorder} rounded ${styles.cardBg}`}
                      >
                        <option value="">{t('ow.placeholder.selectProperty')}</option>
                        {targetObj?.properties.map(p => (
                          <option key={p.id} value={p.id}>{p.displayName} ({p.id})</option>
                        ))}
                      </select>
                    </div>
                  </div>
                  <p className={`text-[9px] ${styles.muted} mt-1 leading-relaxed`}>
                    {t('ow.label.fkDesc')}
                  </p>
                </div>
              )}

              {/* JOIN TABLE CONFIG */}
              {linkType.mapping.type === 'join_table' && (
                <div className={`${styles.appBg} p-4 rounded-lg border ${styles.cardBorder} space-y-3 text-xs`}>
                  <div className={`font-semibold ${styles.cardText} text-[11px] mb-1`}>{t('ow.section.joinTableConfig')}</div>
                  
                  <div className="space-y-1">
                    <label className={`text-[10px] ${styles.muted}`}>{t('ow.label.selectJoinDataset')}</label>
                    <select
                      value={linkType.mapping.datasetId || ''}
                      onChange={e => handleMappingFieldChange('datasetId', e.target.value)}
                      className={`w-full px-2 py-1 text-xs border ${styles.inputBorder} rounded ${styles.cardBg}`}
                    >
                      <option value="">{t('ow.placeholder.selectJoinDataset')}</option>
                      {datasets.map(ds => (
                        <option key={ds.id} value={ds.id}>{ds.name} ({ds.id})</option>
                      ))}
                    </select>
                  </div>

                  <div className={`grid grid-cols-2 gap-3 border-t ${styles.cardBorder} pt-2.5`}>
                    <div className="space-y-1">
                      <label className={`text-[10px] ${styles.muted}`}>{t('ow.label.sourcePrimaryKey')} ({sourceObj?.displayName})</label>
                      <select
                        value={linkType.mapping.joinTableMapping?.sourceKey || ''}
                        onChange={e => handleJoinChange('sourceKey', e.target.value)}
                        className={`w-full px-2 py-1 text-xs border ${styles.inputBorder} rounded ${styles.cardBg}`}
                      >
                        <option value="">{t('ow.placeholder.selectSourceProperty')}</option>
                        {sourceObj?.properties.map(p => (
                          <option key={p.id} value={p.id}>{p.displayName} ({p.id})</option>
                        ))}
                      </select>
                    </div>

                    <div className="space-y-1">
                      <label className={`text-[10px] ${styles.muted}`}>{t('ow.label.joinSourceKey')}</label>
                      <select
                        value={linkType.mapping.joinTableMapping?.joinSourceKey || ''}
                        onChange={e => handleJoinChange('joinSourceKey', e.target.value)}
                        className={`w-full px-2 py-1 text-xs border ${styles.inputBorder} rounded ${styles.cardBg} font-mono`}
                      >
                        <option value="">{t('ow.placeholder.selectJoinColumn')}</option>
                        {selectedDataset?.columns.map(col => (
                          <option key={col.name} value={col.name}>{col.name}</option>
                        ))}
                      </select>
                    </div>

                    <div className="space-y-1">
                      <label className={`text-[10px] ${styles.muted}`}>{t('ow.label.targetPrimaryKey')} ({targetObj?.displayName})</label>
                      <select
                        value={linkType.mapping.joinTableMapping?.targetKey || ''}
                        onChange={e => handleJoinChange('targetKey', e.target.value)}
                        className={`w-full px-2 py-1 text-xs border ${styles.inputBorder} rounded ${styles.cardBg}`}
                      >
                        <option value="">{t('ow.placeholder.selectTargetProperty')}</option>
                        {targetObj?.properties.map(p => (
                          <option key={p.id} value={p.id}>{p.displayName} ({p.id})</option>
                        ))}
                      </select>
                    </div>

                    <div className="space-y-1">
                      <label className={`text-[10px] ${styles.muted}`}>{t('ow.label.joinTargetKey')}</label>
                      <select
                        value={linkType.mapping.joinTableMapping?.joinTargetKey || ''}
                        onChange={e => handleJoinChange('joinTargetKey', e.target.value)}
                        className={`w-full px-2 py-1 text-xs border ${styles.inputBorder} rounded ${styles.cardBg} font-mono`}
                      >
                        <option value="">{t('ow.placeholder.selectJoinColumn2')}</option>
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
