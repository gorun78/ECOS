/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from 'react';
import { Database, Layers, Table, Tag } from 'lucide-react';
import DynamicIcon from '../../components/ontology/DynamicIcon';
import { useLanguage } from '../../components/LanguageContext';
import { useTheme } from '../../components/ThemeContext';

import { InterfaceType, SharedProperty, Dataset, ObjectType } from '../../types/ontology';
// ==========================================
// 1. Interface View Component
// ==========================================
interface InterfaceViewProps {
  intf: InterfaceType;
  objectTypes: ObjectType[];
  onDelete: (id: string) => void;
  onNavigateToObject: (objectId: string) => void;
}

export function InterfaceView({ intf, objectTypes, onDelete, onNavigateToObject }: InterfaceViewProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const implementingObjects = objectTypes.filter(ot => ot.interfaces?.includes(intf.id));

  return (
    <div className={`flex flex-col h-full ${styles.cardBg}`}>
      <div className={`px-6 py-4 border-b ${styles.cardBorder} flex justify-between items-center ${styles.appBg}`}>
        <div className="flex items-center gap-3">
          <div className={`p-2.5 rounded-lg border ${styles.accentBorder} ${styles.badgeBg} ${styles.badgeText} flex items-center justify-center`}>
            <Layers size={20} />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className={`text-lg font-semibold ${styles.cardText}`}>{intf.displayName}</h2>
              <span className={`text-xs font-mono ${styles.badgeBg} ${styles.badgeText} px-1.5 py-0.5 rounded font-bold`}>
                {intf.apiName}
              </span>
            </div>
            <p className={`text-xs ${styles.muted} mt-0.5`}>{intf.description}</p>
          </div>
        </div>
        <button
          onClick={() => onDelete(intf.id)}
          className="text-xs text-red-500 hover:bg-red-50 px-2.5 py-1.5 rounded border border-red-200"
        >
          {t('ow.btn.deleteInterface')}
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-6 space-y-6">
        <div className="space-y-3">
          <h3 className={`text-xs font-semibold ${styles.cardText}`}>{t('ow.section.interfaceProperties')}</h3>
          <p className={`text-[11px] ${styles.muted}`}>
            {t('ow.section.interfacePropertiesDesc')}
          </p>

          <div className={`border ${styles.cardBorder} rounded-lg overflow-hidden`}>
            <table className="w-full text-left border-collapse text-xs">
              <thead>
                <tr className={`${styles.appBg} border-b ${styles.cardBorder} ${styles.cardTextMuted} font-medium`}>
                  <th className="py-2.5 px-4">{t('ow.label.required')}</th>
                  <th className="py-2.5 px-4">{t('ow.label.displayName')}</th>
                  <th className="py-2.5 px-4">{t('ow.label.apiId')}</th>
                  <th className="py-2.5 px-4">{t('ow.label.prescribedDataType')}</th>
                  <th className="py-2.5 px-4">{t('ow.label.specDescription')}</th>
                </tr>
              </thead>
              <tbody className={`divide-y divide-gray-100 ${styles.cardTextMuted}`}>
                {intf.properties.map(p => (
                  <tr key={p.id} className={`hover:bg-slate-50/50`}>
                    <td className="py-2.5 px-4">
                      <span className={`px-1.5 py-0.5 rounded text-[10px] font-bold ${p.isRequired ? 'bg-red-100 text-red-800' : 'bg-slate-100 text-slate-500'}`}>
                        {p.isRequired ? 'REQUIRED' : 'OPTIONAL'}
                      </span>
                    </td>
                    <td className={`py-2.5 px-4 font-semibold ${styles.cardText}`}>{p.displayName}</td>
                    <td className={`py-2.5 px-4 font-mono ${styles.muted}`}>{p.apiName}</td>
                    <td className={`py-2.5 px-4 font-mono ${styles.cardTextMuted}`}>{p.dataType}</td>
                    <td className={`py-2.5 px-4 ${styles.muted}`}>{p.description}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <div className="space-y-3 border-t border-gray-100 pt-6">
          <h3 className={`text-xs font-semibold ${styles.cardText}`}>{t('ow.section.implementingObjects')} ({implementingObjects.length})</h3>
          {implementingObjects.length === 0 ? (
            <div className={`text-center py-6 border border-dashed ${styles.cardBorder} rounded-lg ${styles.muted} text-xs`}>
              {t('ow.empty.noImplementingObjects')}
            </div>
          ) : (
            <div className="grid grid-cols-3 gap-4">
              {implementingObjects.map(ot => (
                <div
                  key={ot.id}
                  onClick={() => onNavigateToObject(ot.id)}
                  className={`p-3 rounded-lg border-2 ${styles.cardBg} flex items-center gap-3 cursor-pointer hover:shadow-xs transition-shadow ${ot.color}`}
                >
                  <DynamicIcon name={ot.icon} size={16} />
                  <div>
                    <div className="text-xs font-semibold">{ot.displayName}</div>
                    <div className="text-[10px] opacity-80 mt-0.5 font-mono">{ot.apiName}</div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

// ==========================================
// 2. Shared Property View Component
// ==========================================
interface SharedPropertyViewProps {
  sp: SharedProperty;
  objectTypes: ObjectType[];
  onDelete: (id: string) => void;
  onNavigateToObject: (objectId: string) => void;
}

export function SharedPropertyView({ sp, objectTypes, onDelete, onNavigateToObject }: SharedPropertyViewProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const referencedObjects = objectTypes.filter(ot =>
    ot.properties.some(prop => prop.sharedPropertyId === sp.id)
  );

  return (
    <div className={`flex flex-col h-full ${styles.cardBg}`}>
      <div className={`px-6 py-4 border-b ${styles.cardBorder} flex justify-between items-center ${styles.appBg}`}>
        <div className="flex items-center gap-3">
          <div className={`p-2.5 rounded-lg border ${styles.accentBorder} ${styles.badgeBg} ${styles.badgeText} flex items-center justify-center`}>
            <Tag size={20} />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className={`text-lg font-semibold ${styles.cardText}`}>{sp.displayName}</h2>
              <span className={`text-xs font-mono ${styles.badgeBg} ${styles.badgeText} px-1.5 py-0.5 rounded font-bold`}>
                {sp.apiName}
              </span>
            </div>
            <p className={`text-xs ${styles.muted} mt-0.5`}>{sp.description}</p>
          </div>
        </div>
        <button
          onClick={() => onDelete(sp.id)}
          className="text-xs text-red-500 hover:bg-red-50 px-2.5 py-1.5 rounded border border-red-200"
        >
          {t('ow.btn.deleteSharedProperty')}
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-6 space-y-6">
        <div className={`${styles.appBg} border ${styles.cardBorder} rounded-xl p-4 space-y-2`}>
          <h4 className={`text-xs font-semibold ${styles.cardText}`}>{t('ow.section.sharedPropertyMechanism')}</h4>
          <p className={`text-[11px] ${styles.cardTextMuted} leading-relaxed`}>
            {t('ow.section.sharedPropertyDesc1')}
          </p>
          <p className={`text-[11px] ${styles.cardTextMuted} leading-relaxed`}>
            {t('ow.section.sharedPropertyDesc2')}
          </p>
        </div>

        <div className="space-y-3">
          <h3 className={`text-xs font-semibold ${styles.cardText}`}>{t('ow.section.dataSpec')}</h3>
          <div className="grid grid-cols-2 gap-4">
            <div className={`p-3 border ${styles.cardBorder} rounded-lg`}>
              <span className={`text-[10px] ${styles.muted} uppercase block`}>{t('ow.label.sharedPropertyApiId')}</span>
              <span className={`font-mono text-xs font-semibold ${styles.cardText} mt-1 block`}>{sp.apiName}</span>
            </div>
            <div className={`p-3 border ${styles.cardBorder} rounded-lg`}>
              <span className={`text-[10px] ${styles.muted} uppercase block`}>{t('ow.label.standardDataType')}</span>
              <span className={`font-mono text-xs font-semibold ${styles.badgeText} mt-1 block uppercase`}>{sp.dataType}</span>
            </div>
          </div>
        </div>

        <div className="space-y-3 border-t border-gray-100 pt-6">
          <h3 className={`text-xs font-semibold ${styles.cardText}`}>{t('ow.section.boundObjectTypes')} ({referencedObjects.length})</h3>
          {referencedObjects.length === 0 ? (
            <div className={`text-center py-6 border border-dashed ${styles.cardBorder} rounded-lg ${styles.muted} text-xs`}>
              {t('ow.empty.noBoundObjectTypes')}
            </div>
          ) : (
            <div className="grid grid-cols-2 gap-4">
              {referencedObjects.map(ot => {
                const boundLocalProps = ot.properties.filter(p => p.sharedPropertyId === sp.id);
                return (
                  <div
                    key={ot.id}
                    onClick={() => onNavigateToObject(ot.id)}
                    className={`p-4 border ${styles.cardBorder} rounded-xl hover:border-teal-400 hover:shadow-xs transition-all cursor-pointer ${styles.cardBg} group`}
                  >
                    <div className="flex items-center gap-2 mb-2">
                      <span className={`p-1.5 rounded-md border ${ot.color}`}>
                        <DynamicIcon name={ot.icon} size={14} />
                      </span>
                      <span className={`text-xs font-semibold ${styles.cardText} group-hover:text-teal-600`}>{ot.displayName}</span>
                    </div>
                    <div className={`text-[10px] ${styles.muted} space-y-1`}>
                      {boundLocalProps.map(lp => (
                        <div key={lp.id} className={`flex justify-between font-mono ${styles.appBg} p-1.5 rounded`}>
                          <span>{t('ow.label.localProperty')}{lp.displayName} ({lp.id})</span>
                          <span className={styles.muted}>{lp.dataType}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

// ==========================================
// 3. Tabular Dataset Preview View Component
// ==========================================
interface DatasetViewProps {
  dataset: Dataset;
  objectTypes: ObjectType[];
  onNavigateToObject: (objectId: string) => void;
}

export function DatasetView({ dataset, objectTypes, onNavigateToObject }: DatasetViewProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const mappedObject = objectTypes.find(ot => ot.mapping?.datasetId === dataset.id);

  return (
    <div className={`flex flex-col h-full ${styles.cardBg}`}>
      <div className={`px-6 py-4 border-b ${styles.cardBorder} flex justify-between items-center ${styles.appBg}`}>
        <div className="flex items-center gap-3">
          <div className={`p-2.5 rounded-lg border ${styles.cardBorder} ${styles.cardBg} ${styles.cardTextMuted} flex items-center justify-center shadow-3xs`}>
            <Database size={20} className={styles.muted} />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className={`text-sm font-semibold font-mono ${styles.cardText}`}>{dataset.name}</h2>
              <span className={`text-[10px] font-bold ${styles.badgeBg} ${styles.cardTextMuted} px-1.5 py-0.5 rounded font-mono uppercase`}>
                {t('ow.ds.rawDataTable')}
              </span>
            </div>
            <p className={`text-[10px] ${styles.muted} font-mono mt-0.5`}>ECOS 路径: {dataset.path}</p>
          </div>
        </div>

        {mappedObject && (
          <div
            onClick={() => onNavigateToObject(mappedObject.id)}
            className={`px-3 py-1.5 rounded-lg border text-xs cursor-pointer hover:shadow-xs transition-shadow flex items-center gap-1.5 ${mappedObject.color}`}
          >
            <Layers size={13} />
            <span>{t('ow.ds.mappedToObject')}<strong>{mappedObject.displayName}</strong></span>
          </div>
        )}
      </div>

      <div className="flex-1 overflow-hidden flex flex-col p-6 space-y-6">
        {/* Schema Summary */}
        <div className="space-y-2">
          <h3 className={`text-xs font-semibold ${styles.cardText}`}>{t('ow.ds.schemaTitle')}</h3>
          <div className="flex flex-wrap gap-2">
            {dataset.columns.map(col => (
              <div key={col.name} className={`flex items-center gap-1.5 ${styles.appBg} border ${styles.cardBorder} px-2 py-1 rounded font-mono text-[10px]`}>
                <span className={`${styles.cardTextMuted} font-medium`}>{col.name}</span>
                <span className={`${styles.muted} italic`}>({col.type})</span>
              </div>
            ))}
          </div>
        </div>

        {/* Tabular Preview */}
        <div className={`flex-1 flex flex-col space-y-2 overflow-hidden border ${styles.cardBorder} rounded-lg`}>
          <div className={`${styles.appBg} px-4 py-2 text-xs font-semibold ${styles.cardTextMuted} border-b ${styles.cardBorder} flex items-center gap-1.5`}>
            <Table size={14} className={styles.muted} />
            {t('ow.ds.dataPreview')}
          </div>
          <div className={`flex-1 overflow-auto ${styles.cardBg}`}>
            <table className="w-full text-left border-collapse font-mono text-[11px]">
              <thead>
                <tr className={`${styles.appBg} border-b ${styles.cardBorder} ${styles.muted} font-medium sticky top-0 ${styles.cardBg}`}>
                  {dataset.columns.map(col => (
                    <th key={col.name} className="py-2 px-3">{col.name}</th>
                  ))}
                </tr>
              </thead>
              <tbody className={`divide-y divide-slate-100 ${styles.cardTextMuted}`}>
                {dataset.sampleData.map((row, idx) => (
                  <tr key={idx} className="hover:bg-slate-50/30">
                    {dataset.columns.map(col => (
                      <td key={col.name} className="py-2.5 px-3 truncate max-w-[150px]" title={String(row[col.name] ?? '')}>
                        {row[col.name] === undefined ? <span className="text-slate-300">null</span> : String(row[col.name])}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
}
