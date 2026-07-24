/**
 * MetadataTab — 对象元数据配置 Tab
 * @license Apache-2.0
 */
import React from 'react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { Layers } from 'lucide-react';
import type { MetadataTabProps } from './types';

export default function MetadataTab({
  objectType,
  handleMetaChange,
  domains,
  interfaces,
}: MetadataTabProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();

  return (
    <div className="space-y-6 max-w-2xl">
      <div className="grid grid-cols-2 gap-4">
        <div className="space-y-1.5">
          <label className={`text-xs font-semibold ${styles.sidebarText}`}>{t('ow.label.objectDisplayName')}</label>
          <input
            type="text"
            value={objectType.displayName}
            onChange={e => handleMetaChange('displayName', e.target.value)}
            className={`w-full px-3 py-1.5 text-xs border border-gray-300 rounded focus:border-blue-500 focus:outline-hidden`}
          />
        </div>
        <div className="space-y-1.5">
          <label className={`text-xs font-semibold ${styles.sidebarText}`}>{t('ow.label.objectApiName')}</label>
          <input
            type="text"
            value={objectType.apiName}
            onChange={e => handleMetaChange('apiName', e.target.value)}
            className="w-full px-3 py-1.5 text-xs border border-gray-300 rounded focus:border-blue-500 focus:outline-hidden"
          />
        </div>
      </div>

      <div className="space-y-1.5">
        <label className={`text-xs font-semibold ${styles.sidebarText}`}>{t('ow.label.objectDescription')}</label>
        <textarea
          value={objectType.description}
          onChange={e => handleMetaChange('description', e.target.value)}
          className="w-full h-20 px-3 py-1.5 text-xs border border-gray-300 rounded focus:border-blue-500 focus:outline-hidden"
          placeholder={t('ow.placeholder.objectDescription')}
        />
      </div>

      <div className={`space-y-1.5 border-t border-gray-100 pt-4`}>
        <label className={`text-xs font-semibold ${styles.sidebarText}`}>{t('ow.label.domainHierarchy')}</label>
        <select
          value={objectType.domainId || ''}
          onChange={e => handleMetaChange('domainId', e.target.value || undefined)}
          className={`w-full px-3 py-1.5 text-xs border border-gray-300 rounded ${styles.cardBg} focus:border-blue-500 focus:outline-hidden`}
        >
          <option value="">{t('ow.placeholder.unclassified')}</option>
          {domains.map(d => (
            <option key={d.id} value={d.id}>{d.displayName}</option>
          ))}
        </select>
        <p className={`text-[10px] ${styles.muted}`}>{t('ow.label.domainHint')}</p>
      </div>

      <div className="grid grid-cols-2 gap-4 border-t border-gray-100 pt-4">
        <div className="space-y-1.5">
          <label className={`text-xs font-semibold ${styles.sidebarText}`}>{t('ow.label.titleProperty')}</label>
          <select
            value={objectType.titleProperty}
            onChange={e => handleMetaChange('titleProperty', e.target.value)}
            className={`w-full px-3 py-1.5 text-xs border border-gray-300 rounded ${styles.cardBg} focus:border-blue-500 focus:outline-hidden`}
          >
            {objectType.properties.map(p => (
              <option key={p.id} value={p.id}>{p.displayName} ({p.apiName})</option>
            ))}
          </select>
          <p className={`text-[10px] ${styles.muted}`}>{t('ow.label.titlePropertyHint')}</p>
        </div>

        <div className="space-y-1.5">
          <label className={`text-xs font-semibold ${styles.sidebarText}`}>{t('ow.label.operatingStatus')}</label>
          <select
            value={objectType.status}
            onChange={e => handleMetaChange('status', e.target.value)}
            className={`w-full px-3 py-1.5 text-xs border border-gray-300 rounded ${styles.cardBg} focus:border-blue-500 focus:outline-hidden`}
          >
            <option value="DRAFT">{t('ow.status.draft')}</option>
            <option value="ACTIVE">{t('ow.status.active')}</option>
            <option value="DEPRECATED">{t('ow.status.deprecated')}</option>
          </select>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div className="space-y-1.5">
          <label className={`text-xs font-semibold ${styles.sidebarText}`}>{t('ow.label.displayIcon')}</label>
          <select
            value={objectType.icon}
            onChange={e => handleMetaChange('icon', e.target.value)}
            className={`w-full px-3 py-1.5 text-xs border border-gray-300 rounded ${styles.cardBg} focus:border-blue-500 focus:outline-hidden`}
          >
            <option value="Plane">{t('ow.icon.plane')}</option>
            <option value="Building2">{t('ow.icon.building')}</option>
            <option value="Navigation">{t('ow.icon.navigation')}</option>
            <option value="UserSquare2">{t('ow.icon.user')}</option>
            <option value="Database">{t('ow.icon.database')}</option>
            <option value="ShieldAlert">{t('ow.icon.shield')}</option>
            <option value="FileText">{t('ow.icon.file')}</option>
            <option value="Heart">{t('ow.icon.heart')}</option>
          </select>
        </div>

        <div className="space-y-1.5">
          <label className={`text-xs font-semibold ${styles.sidebarText}`}>{t('ow.label.colorTheme')}</label>
          <select
            value={objectType.color}
            onChange={e => handleMetaChange('color', e.target.value)}
            className={`w-full px-3 py-1.5 text-xs border border-gray-300 rounded ${styles.cardBg} focus:border-blue-500 focus:outline-hidden`}
          >
            <option value="border-blue-500 bg-blue-50 text-blue-700">{t('ow.color.blue')}</option>
            <option value="border-emerald-500 bg-emerald-50 text-emerald-700">{t('ow.color.emerald')}</option>
            <option value="border-purple-500 bg-purple-50 text-purple-700">{t('ow.color.purple')}</option>
            <option value="border-orange-500 bg-orange-50 text-orange-700">{t('ow.color.orange')}</option>
            <option value="border-red-500 bg-red-50 text-red-700">{t('ow.color.red')}</option>
            <option value="border-slate-500 bg-slate-50 text-slate-700">{t('ow.color.slate')}</option>
          </select>
        </div>
      </div>

      {/* Implements Interfaces */}
      <div className={`space-y-2 border-t border-gray-100 pt-4`}>
        <label className={`text-xs font-semibold ${styles.sidebarText} block`}>{t('ow.label.implementsInterfaces')}</label>
        <div className="flex flex-wrap gap-2">
          {interfaces.map(intf => {
            const isChecked = (objectType.interfaces || []).includes(intf.id);
            return (
              <label key={intf.id} className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg border text-xs cursor-pointer select-none transition-colors ${
                isChecked ? `${styles.badgeBg} ${styles.accentBorder} ${styles.badgeText}` : `${styles.cardBg} ${styles.cardBorder} ${styles.sidebarText} hover:bg-gray-50`
              }`}>
                <input
                  type="checkbox"
                  checked={isChecked}
                  onChange={(e) => {
                    const current = objectType.interfaces || [];
                    const updated = e.target.checked
                      ? [...current, intf.id]
                      : current.filter(id => id !== intf.id);
                    handleMetaChange('interfaces', updated);
                  }}
                  className="sr-only"
                />
                <Layers size={13} />
                <span>{intf.displayName}</span>
              </label>
            );
          })}
        </div>
        <p className={`text-[10px] ${styles.muted}`}>{t('ow.label.interfacesHint')}</p>
      </div>
    </div>
  );
}
