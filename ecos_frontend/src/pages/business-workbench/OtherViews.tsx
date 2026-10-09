/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from 'react';
import { InterfaceType, SharedProperty, Dataset, ObjectType } from '../../types/ontology';
import LucideIcon from './LucideIcon';
import { useTheme } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';

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
  const { styles } = useTheme();
  const { t } = useLanguage();
  const implementingObjects = objectTypes.filter(ot => ot.interfaces?.includes(intf.id));

  return (
    <div className={`flex flex-col h-full ${styles.cardBg}`}>
      <div className={`px-6 py-4 border-b ${styles.appBorder} flex justify-between items-center ${styles.appBg}`}>
        <div className="flex items-center gap-3">
          <div className="p-2.5 rounded-lg border border-indigo-200 bg-indigo-50 text-indigo-700 flex items-center justify-center">
            <LucideIcon name="Layers" size={20} />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className={`text-lg font-semibold ${styles.cardText}`}>{intf.displayName}</h2>
              <span className="text-xs font-mono bg-indigo-100 text-indigo-700 px-1.5 py-0.5 rounded font-bold">
                {intf.apiName}
              </span>
            </div>
            <p className={`text-xs ${styles.cardTextMuted} mt-0.5`}>{intf.description}</p>
          </div>
        </div>
        <button
          type="button"
          onClick={() => onDelete(intf.id)}
          className={`text-xs ${styles.dangerText} hover:bg-red-50 px-2.5 py-1.5 rounded border ${styles.dangerBorder}`}
        >
          {t('ow.ip.delete')}
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-6 space-y-6">
        <div className="space-y-3">
          <h3 className={`text-xs font-semibold ${styles.cardText}`}>{t('ow.ip.propSection')}</h3>
          <p className={`text-[11px] ${styles.cardTextMuted}`}>
            {t('ow.ip.propDesc')}
          </p>

          <div className={`border ${styles.appBorder} rounded-lg overflow-hidden`}>
            <table className="w-full text-left border-collapse text-xs">
              <thead>
                <tr className={`${styles.appBg} border-b ${styles.appBorder} ${styles.cardText} font-medium`}>
                  <th className="py-2.5 px-4">{t('ow.ip.col.required')}</th>
                  <th className="py-2.5 px-4">{t('ow.ip.col.displayName')}</th>
                  <th className="py-2.5 px-4">{t('ow.ip.col.apiName')}</th>
                  <th className="py-2.5 px-4">{t('ow.ip.col.dataType')}</th>
                  <th className="py-2.5 px-4">{t('ow.ip.col.description')}</th>
                </tr>
              </thead>
              <tbody className={`divide-y divide-gray-100 ${styles.cardText}`}>
                {intf.properties.map(p => (
                  <tr key={p.id} className={`${styles.sidebarHoverBg}`}>
                    <td className="py-2.5 px-4">
                      <span className={`px-1.5 py-0.5 rounded text-[10px] font-bold ${p.isRequired ? 'bg-red-100 text-red-800' : `${styles.sidebarBg} ${styles.cardTextMuted}`}`}>
                        {p.isRequired ? 'REQUIRED' : 'OPTIONAL'}
                      </span>
                    </td>
                    <td className={`py-2.5 px-4 font-semibold ${styles.cardText}`}>{p.displayName}</td>
                    <td className={`py-2.5 px-4 font-mono ${styles.cardTextMuted}`}>{p.apiName}</td>
                    <td className={`py-2.5 px-4 font-mono ${styles.cardText}`}>{p.dataType}</td>
                    <td className={`py-2.5 px-4 ${styles.cardTextMuted}`}>{p.description}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <div className={`space-y-3 border-t ${styles.divider} pt-6`}>
          <h3 className={`text-xs font-semibold ${styles.cardText}`}>{t('ow.ip.implSection', { count: implementingObjects.length })}</h3>
          {implementingObjects.length === 0 ? (
            <div className={`text-center py-6 border border-dashed ${styles.appBorder} rounded-lg ${styles.cardTextMuted} text-xs`}>
              {t('ow.ip.implEmpty')}
            </div>
          ) : (
            <div className="grid grid-cols-3 gap-4">
              {implementingObjects.map(ot => (
                <div
                  key={ot.id}
                  onClick={() => onNavigateToObject(ot.id)}
                  className={`p-3 rounded-lg border-2 ${styles.cardBg} flex items-center gap-3 cursor-pointer hover:shadow-xs transition-shadow ${ot.color}`}
                >
                  <LucideIcon name={ot.icon} size={16} />
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
  const { styles } = useTheme();
  const { t } = useLanguage();
  const referencedObjects = objectTypes.filter(ot =>
    ot.properties.some(prop => prop.sharedPropertyId === sp.id)
  );

  return (
    <div className={`flex flex-col h-full ${styles.cardBg}`}>
      <div className={`px-6 py-4 border-b ${styles.appBorder} flex justify-between items-center ${styles.appBg}`}>
        <div className="flex items-center gap-3">
          <div className="p-2.5 rounded-lg border border-teal-200 bg-teal-50 text-teal-700 flex items-center justify-center">
            <LucideIcon name="Tag" size={20} />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className={`text-lg font-semibold ${styles.cardText}`}>{sp.displayName}</h2>
              <span className="text-xs font-mono bg-teal-100 text-teal-700 px-1.5 py-0.5 rounded font-bold">
                {sp.apiName}
              </span>
            </div>
            <p className={`text-xs ${styles.cardTextMuted} mt-0.5`}>{sp.description}</p>
          </div>
        </div>
        <button
          type="button"
          onClick={() => onDelete(sp.id)}
          className={`text-xs ${styles.dangerText} bg-red-50 px-2.5 py-1.5 rounded border ${styles.dangerBorder}`}
        >
          {t('ow.sprop.delete')}
        </button>
      </div>

      <div className="flex-1 overflow-y-auto p-6 space-y-6">
        <div className={`${styles.appBg} border ${styles.appBorder} rounded-xl p-4 space-y-2`}>
          <h4 className={`text-xs font-semibold ${styles.cardText}`}>{t('ow.sprop.mechTitle')}</h4>
          <p className={`text-[11px] ${styles.cardText} leading-relaxed`}>
            {t('ow.sprop.mechDesc')}
          </p>
        </div>

        <div className="space-y-3">
          <h3 className={`text-xs font-semibold ${styles.cardText}`}>{t('ow.sprop.spec')}</h3>
          <div className="grid grid-cols-2 gap-4">
            <div className={`p-3 border ${styles.appBorder} rounded-lg`}>
              <span className={`text-[10px] ${styles.cardTextMuted} uppercase block`}>{t('ow.sprop.apiNameLabel')}</span>
              <span className={`font-mono text-xs font-semibold ${styles.cardText} mt-1 block`}>{sp.apiName}</span>
            </div>
            <div className={`p-3 border ${styles.appBorder} rounded-lg`}>
              <span className={`text-[10px] ${styles.cardTextMuted} uppercase block`}>{t('ow.sprop.dataTypeLabel')}</span>
              <span className="font-mono text-xs font-semibold text-teal-600 mt-1 block uppercase">{sp.dataType}</span>
            </div>
          </div>
        </div>

        <div className={`space-y-3 border-t ${styles.divider} pt-6`}>
          <h3 className={`text-xs font-semibold ${styles.cardText}`}>{t('ow.sprop.bindSection', { count: referencedObjects.length })}</h3>
          {referencedObjects.length === 0 ? (
            <div className={`text-center py-6 border border-dashed ${styles.appBorder} rounded-lg ${styles.cardTextMuted} text-xs`}>
              {t('ow.sprop.bindEmpty')}
            </div>
          ) : (
            <div className="grid grid-cols-2 gap-4">
              {referencedObjects.map(ot => {
                const boundLocalProps = ot.properties.filter(p => p.sharedPropertyId === sp.id);
                return (
                  <div
                    key={ot.id}
                    onClick={() => onNavigateToObject(ot.id)}
                    className={`p-4 border ${styles.appBorder} rounded-xl hover:border-teal-400 hover:shadow-xs transition-all cursor-pointer ${styles.cardBg} group`}
                  >
                    <div className="flex items-center gap-2 mb-2">
                      <span className={`p-1.5 rounded-md border ${ot.color}`}>
                        <LucideIcon name={ot.icon} size={14} />
                      </span>
                      <span className={`text-xs font-semibold ${styles.cardText} group-hover:text-teal-600`}>{ot.displayName}</span>
                    </div>
                    <div className={`text-[10px] ${styles.cardTextMuted} space-y-1`}>
                      {boundLocalProps.map(lp => (
                        <div key={lp.id} className={`flex justify-between font-mono ${styles.appBg} p-1.5 rounded`}>
                          <span>{t('ow.sprop.localProp', { lpName: lp.displayName, lpId: lp.id })}</span>
                          <span className={styles.cardTextMuted}>{lp.dataType}</span>
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
  const { styles } = useTheme();
  const { t } = useLanguage();
  const mappedObject = objectTypes.find(ot => ot.mapping.datasetId === dataset.id);

  return (
    <div className={`flex flex-col h-full ${styles.cardBg}`}>
      <div className={`px-6 py-4 border-b ${styles.appBorder} flex justify-between items-center ${styles.appBg}`}>
        <div className="flex items-center gap-3">
          <div className={`p-2.5 rounded-lg border ${styles.appBorder} ${styles.cardBg} ${styles.cardText} flex items-center justify-center shadow-3xs`}>
            <LucideIcon name="Database" size={20} className={styles.cardTextMuted} />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className={`text-sm font-semibold font-mono ${styles.cardText}`}>{dataset.name}</h2>
              <span className={`text-[10px] font-bold ${styles.sidebarBg} ${styles.cardText} px-1.5 py-0.5 rounded font-mono uppercase`}>
                {t('ow.dsv.raw')}
              </span>
            </div>
            <p className={`text-[10px] ${styles.cardTextMuted} font-mono mt-0.5`}>{t('ow.dsv.pathLabel')}: {dataset.path}</p>
          </div>
        </div>

        {mappedObject && (
          <div
            onClick={() => onNavigateToObject(mappedObject.id)}
            className={`px-3 py-1.5 rounded-lg border text-xs cursor-pointer hover:shadow-xs transition-shadow flex items-center gap-1.5 ${mappedObject.color}`}
          >
            <LucideIcon name="Layers" size={13} />
            <span>{t('ow.dsv.mappedTo')}：<strong>{mappedObject.displayName}</strong></span>
          </div>
        )}
      </div>

      <div className="flex-1 overflow-hidden flex flex-col p-6 space-y-6">
        {/* Schema Summary */}
        <div className="space-y-2">
          <h3 className={`text-xs font-semibold ${styles.cardText}`}>{t('ow.dsv.schema')}</h3>
          <div className="flex flex-wrap gap-2">
            {dataset.columns.map(col => (
              <div key={col.name} className={`flex items-center gap-1.5 ${styles.appBg} border ${styles.appBorder} px-2 py-1 rounded font-mono text-[10px]`}>
                <span className={`${styles.cardText} font-medium`}>{col.name}</span>
                <span className={`${styles.cardTextMuted} italic`}>({col.type})</span>
              </div>
            ))}
          </div>
        </div>

        {/* Tabular Preview */}
        <div className={`flex-1 flex flex-col space-y-2 overflow-hidden border ${styles.appBorder} rounded-lg`}>
          <div className={`${styles.appBg} px-4 py-2 text-xs font-semibold ${styles.cardText} border-b ${styles.appBorder} flex items-center gap-1.5`}>
            <LucideIcon name="Table" size={14} className={styles.cardTextMuted} />
            {t('ow.dsv.preview')}
          </div>
          <div className={`flex-1 overflow-auto ${styles.cardBg}`}>
            <table className="w-full text-left border-collapse font-mono text-[11px]">
              <thead>
                <tr className={`${styles.appBg} border-b ${styles.appBorder} ${styles.cardTextMuted} font-medium sticky top-0 ${styles.cardBg}`}>
                  {dataset.columns.map(col => (
                    <th key={col.name} className="py-2 px-3">{col.name}</th>
                  ))}
                </tr>
              </thead>
              <tbody className={`divide-y ${styles.divider} ${styles.cardText}`}>
                {dataset.sampleData.map((row, idx) => (
                  <tr key={idx} className={styles.sidebarHoverBg}>
                    {dataset.columns.map(col => (
                      <td key={col.name} className="py-2.5 px-3 truncate max-w-[150px]" title={String(row[col.name] ?? '')}>
                        {row[col.name] === undefined ? <span className={styles.muted}>null</span> : String(row[col.name])}
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
