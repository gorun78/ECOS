/**
 * PropertiesTab — 对象属性定义 Tab
 * @license Apache-2.0
 */
import React from 'react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { Plus, Key, X } from 'lucide-react';
import type { PropertiesTabProps } from './types';

export default function PropertiesTab({
  objectType,
  newPropName, setNewPropName,
  newPropType, setNewPropType,
  handleAddProperty,
  handleTogglePrimaryKey,
  handlePropertyFieldChange,
  handleRemoveProperty,
  sharedProperties,
}: PropertiesTabProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div className={`text-xs ${styles.cardTextMuted}`}>
          {t('ow.tab.propertiesDesc')}
        </div>
        <div className="flex items-center gap-2">
          <input
            type="text"
            placeholder={t('ow.placeholder.newPropertyName')}
            value={newPropName}
            onChange={e => setNewPropName(e.target.value)}
            className={`px-3 py-1 text-xs border border-gray-300 rounded focus:border-blue-500 focus:outline-hidden`}
          />
          <select
            value={newPropType}
            onChange={e => setNewPropType(e.target.value as any)}
            className={`px-2 py-1 text-xs border border-gray-300 rounded ${styles.cardBg} focus:border-blue-500 focus:outline-hidden`}
          >
            <option value="string">{t('ow.prop.typeString')}</option>
            <option value="integer">{t('ow.prop.typeInteger')}</option>
            <option value="decimal">{t('ow.prop.typeDecimal')}</option>
            <option value="boolean">{t('ow.prop.typeBoolean')}</option>
            <option value="date">{t('ow.prop.typeDate')}</option>
            <option value="timestamp">{t('ow.prop.typeTimestamp')}</option>
            <option value="geopoint">{t('ow.prop.typeGeopoint')}</option>
          </select>
          <button
            onClick={handleAddProperty}
            className={`${styles.accentBg} hover:bg-blue-700 text-white text-xs px-3 py-1 rounded transition-colors flex items-center gap-1`}
          >
            <Plus size={13} />
            {t('ow.btn.addProperty')}
          </button>
        </div>
      </div>

      <div className={`overflow-x-auto border ${styles.cardBorder} rounded-lg`}>
        <table className="w-full text-left border-collapse text-xs">
          <thead>
            <tr className={`${styles.appBg} border-b ${styles.cardBorder} ${styles.sidebarText} font-medium`}>
              <th className="py-2.5 px-4 w-12 text-center">{t('ow.label.primaryKey')}</th>
              <th className="py-2.5 px-4">{t('ow.label.displayName')}</th>
              <th className="py-2.5 px-4">{t('ow.label.apiFieldName')}</th>
              <th className="py-2.5 px-4">{t('ow.label.dataType')}</th>
              <th className="py-2.5 px-4">{t('ow.label.descriptionNote')}</th>
              <th className="py-2.5 px-4">{t('ow.label.sharedPropertyBinding')}</th>
              <th className="py-2.5 px-4 text-center">{t('ow.label.actions')}</th>
            </tr>
          </thead>
          <tbody className={`divide-y divide-gray-100 ${styles.cardTextMuted}`}>
            {objectType.properties.map(prop => (
              <tr key={prop.id} className={`hover:bg-slate-50/50 transition-colors`}>
                <td className="py-2.5 px-4 text-center">
                  <button
                    onClick={() => handleTogglePrimaryKey(prop.id)}
                    className={`p-1.5 rounded-full transition-colors ${
                      objectType.primaryKey === prop.id
                        ? 'text-amber-500 hover:bg-amber-50'
                        : `text-slate-300 hover:text-slate-400 ${styles.sidebarHoverBg}`
                    }`}
                    title={objectType.primaryKey === prop.id ? t('ow.btn.currentPrimaryKey') : t('ow.btn.setPrimaryKey')}
                  >
                    <Key size={14} className={objectType.primaryKey === prop.id ? 'fill-amber-500' : ''} />
                  </button>
                </td>
                <td className="py-2.5 px-4">
                  <input
                    type="text"
                    value={prop.displayName}
                    onChange={e => handlePropertyFieldChange(prop.id, 'displayName', e.target.value)}
                    className={`font-medium ${styles.cardText} border-b border-transparent hover:border-slate-300 focus:border-blue-500 focus:outline-hidden py-0.5 px-1`}
                  />
                </td>
                <td className={`py-2.5 px-4 font-mono ${styles.cardTextMuted}`}>
                  <input
                    type="text"
                    value={prop.apiName}
                    onChange={e => handlePropertyFieldChange(prop.id, 'apiName', e.target.value)}
                    className="border-b border-transparent hover:border-slate-300 focus:border-blue-500 focus:outline-hidden py-0.5 px-1 w-full"
                  />
                </td>
                <td className="py-2.5 px-4">
                  <select
                    value={prop.dataType}
                    onChange={e => handlePropertyFieldChange(prop.id, 'dataType', e.target.value)}
                    className={`bg-transparent border ${styles.cardBorder} rounded px-1.5 py-0.5 focus:border-blue-500 focus:outline-hidden font-mono`}
                  >
                    <option value="string">string</option>
                    <option value="integer">integer</option>
                    <option value="decimal">decimal</option>
                    <option value="boolean">boolean</option>
                    <option value="date">date</option>
                    <option value="timestamp">timestamp</option>
                    <option value="geopoint">geopoint</option>
                  </select>
                </td>
                <td className="py-2.5 px-4">
                  <input
                    type="text"
                    value={prop.description}
                    onChange={e => handlePropertyFieldChange(prop.id, 'description', e.target.value)}
                    className={`${styles.cardTextMuted} border-b border-transparent hover:border-slate-300 focus:border-blue-500 focus:outline-hidden py-0.5 px-1 w-full`}
                    placeholder={t('ow.placeholder.noDescription')}
                  />
                </td>
                <td className="py-2.5 px-4">
                  <select
                    value={prop.sharedPropertyId || ''}
                    onChange={e => handlePropertyFieldChange(prop.id, 'sharedPropertyId', e.target.value || undefined)}
                    className={`bg-transparent border ${styles.cardBorder} rounded px-1.5 py-0.5 focus:border-blue-500 focus:outline-hidden ${styles.sidebarText}`}
                  >
                    <option value="">{t('ow.prop.notBound')}</option>
                    {sharedProperties.map(sp => (
                      <option key={sp.id} value={sp.id}>{sp.displayName} ({sp.apiName})</option>
                    ))}
                  </select>
                </td>
                <td className="py-2.5 px-4 text-center">
                  <button
                    onClick={() => handleRemoveProperty(prop.id)}
                    className={`${styles.muted} hover:text-red-500 p-1 rounded ${styles.sidebarHoverBg} transition-colors`}
                    title={t('ow.btn.deleteProperty')}
                  >
                    <X size={14} />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
