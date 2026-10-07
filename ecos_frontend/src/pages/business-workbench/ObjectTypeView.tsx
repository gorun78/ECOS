/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from 'react';
import { ObjectType, PropertyType, Dataset, LinkType, ActionType, SharedProperty, InterfaceType, OntologyDomain } from '../../types/ontology';
import LucideIcon from './LucideIcon';
import { useTheme } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';

interface ObjectTypeViewProps {
  objectType: ObjectType;
  datasets: Dataset[];
  linkTypes: LinkType[];
  actionTypes: ActionType[];
  sharedProperties: SharedProperty[];
  interfaces: InterfaceType[];
  domains: OntologyDomain[];
  onUpdate: (updated: ObjectType) => void;
  onDelete: (id: string) => void;
  onNavigateToLink: (linkId: string) => void;
  onNavigateToAction: (actionId: string) => void;
  onExploreData?: (id: string) => void;
}

export default function ObjectTypeView({
  objectType,
  datasets,
  linkTypes,
  actionTypes,
  sharedProperties,
  interfaces,
  domains,
  onUpdate,
  onDelete,
  onNavigateToLink,
  onNavigateToAction,
  onExploreData
}: ObjectTypeViewProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [activeTab, setActiveTab] = useState<'metadata' | 'properties' | 'mapping' | 'links' | 'actions'>('properties');
  const [newPropName, setNewPropName] = useState('');
  const [newPropType, setNewPropType] = useState<'string' | 'integer' | 'decimal' | 'boolean' | 'date' | 'timestamp' | 'geopoint'>('string');

  const selectedDataset = datasets.find(d => d.id === objectType.mapping.datasetId) || datasets[0];

  // Handler for metadata changes
  const handleMetaChange = (key: keyof ObjectType, value: any) => {
    onUpdate({
      ...objectType,
      [key]: value
    });
  };

  // Handler for mapping dataset changes
  const handleDatasetChange = (datasetId: string) => {
    onUpdate({
      ...objectType,
      mapping: {
        datasetId,
        propertyMappings: {} // Reset or keep empty to allow manually mapping
      }
    });
  };

  // Update specific property mapping
  const handlePropMappingChange = (propId: string, colName: string) => {
    onUpdate({
      ...objectType,
      mapping: {
        ...objectType.mapping,
        propertyMappings: {
          ...objectType.mapping.propertyMappings,
          [propId]: colName
        }
      }
    });
  };

  // Auto-map based on name similarity
  const handleAutoMap = () => {
    if (!selectedDataset) return;
    const newMappings: Record<string, string> = {};
    objectType.properties.forEach(prop => {
      // Find a column that matches by lowercase and replacing underscores
      const matchedCol = selectedDataset.columns.find(col => {
        const cNorm = col.name.toLowerCase().replace(/_/g, '');
        const pNormName = prop.displayName.toLowerCase().replace(/_/g, '');
        const pNormApi = prop.apiName.toLowerCase().replace(/_/g, '');
        const pNormId = prop.id.toLowerCase().replace(/_/g, '');
        return cNorm === pNormName || cNorm === pNormApi || cNorm === pNormId;
      });

      if (matchedCol) {
        newMappings[prop.id] = matchedCol.name;
      }
    });

    onUpdate({
      ...objectType,
      mapping: {
        ...objectType.mapping,
        propertyMappings: {
          ...objectType.mapping.propertyMappings,
          ...newMappings
        }
      }
    });
  };

  // Add new property
  const handleAddProperty = () => {
    if (!newPropName.trim()) return;
    const propId = newPropName.trim().replace(/\s+/g, '');
    const newProp: PropertyType = {
      id: propId,
      displayName: newPropName,
      apiName: propId.charAt(0).toLowerCase() + propId.slice(1),
      dataType: newPropType,
      isPrimaryKey: false,
      description: t('ow.object.newPropDesc', { name: newPropName })
    };

    onUpdate({
      ...objectType,
      properties: [...objectType.properties, newProp]
    });
    setNewPropName('');
  };

  // Remove property
  const handleRemoveProperty = (propId: string) => {
    if (propId === objectType.primaryKey) {
      alert(t('ow.object.cannotDeletePk'));
      return;
    }
    const updatedProps = objectType.properties.filter(p => p.id !== propId);
    const updatedMappings = { ...objectType.mapping.propertyMappings };
    delete updatedMappings[propId];

    onUpdate({
      ...objectType,
      properties: updatedProps,
      mapping: {
        ...objectType.mapping,
        propertyMappings: updatedMappings
      }
    });
  };

  // Toggle PK
  const handleTogglePrimaryKey = (propId: string) => {
    onUpdate({
      ...objectType,
      primaryKey: propId,
      properties: objectType.properties.map(p => ({
        ...p,
        isPrimaryKey: p.id === propId
      }))
    });
  };

  // Change individual property fields
  const handlePropertyFieldChange = (propId: string, field: keyof PropertyType, value: any) => {
    onUpdate({
      ...objectType,
      properties: objectType.properties.map(p =>
        p.id === propId ? { ...p, [field]: value } : p
      )
    });
  };

  // Filter linked and actions
  const relatedLinks = linkTypes.filter(
    l => l.sourceObjectType === objectType.id || l.targetObjectType === objectType.id
  );

  const relatedActions = actionTypes.filter(action =>
    action.parameters.some(param => param.dataType === 'object' && param.objectTypeId === objectType.id)
  );

  return (
    <div className={`flex flex-col h-full ${styles.cardBg}`}>
      {/* Detail Header */}
      <div className={`px-6 py-4 border-b ${styles.appBorder} flex justify-between items-center ${styles.appBg}`}>
        <div className="flex items-center gap-3">
          <div className={`p-2 rounded-lg border-2 ${objectType.color} flex items-center justify-center`}>
            <LucideIcon name={objectType.icon} size={20} />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className={`text-lg font-semibold ${styles.cardText}`}>{objectType.displayName}</h2>
              <span className={`text-xs font-mono ${styles.sidebarBg} ${styles.cardTextMuted} px-1.5 py-0.5 rounded`}>
                {objectType.apiName}
              </span>
              <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded-full ${
                objectType.status === 'ACTIVE' ? `${styles.successBg} ${styles.successText}` : `${styles.warningBg} ${styles.warningText}`
              }`}>
                {objectType.status === 'ACTIVE' ? t('ow.object.status_active') : t('ow.object.status_draft')}
              </span>
            </div>
            <p className={`text-xs ${styles.cardTextMuted} mt-0.5`}>{objectType.description || t('ow.object.noDescription')}</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {onExploreData && (
            <button
              onClick={() => onExploreData(objectType.id)}
              className={`text-xs ${styles.successText} hover:bg-blue-50 px-2.5 py-1.5 rounded border ${styles.infoBorder} transition-colors flex items-center gap-1.5 font-semibold`}
            >
              <LucideIcon name="Compass" size={13} />
              {t('ow.object.exploreData')}
            </button>
          )}
          <button
            onClick={() => onDelete(objectType.id)}
            className={`text-xs ${styles.dangerText} hover:bg-red-50 px-2.5 py-1.5 rounded border ${styles.dangerBorder} transition-colors flex items-center gap-1.5`}
          >
            <LucideIcon name="Trash2" size={13} />
            {t('ow.object.deleteObject')}
          </button>
        </div>
      </div>

      {/* Detail Tabs */}
      <div className={`flex px-6 border-b ${styles.appBorder} ${styles.cardBg}`}>
        {(['properties', 'mapping', 'metadata', 'links', 'actions'] as const).map(tab => {
          const tabLabels: Record<typeof tab, string> = {
            properties: t('ow.object.tab_properties'),
            mapping: t('ow.object.tab_mapping'),
            metadata: t('ow.object.tab_metadata'),
            links: t('ow.object.tab_links'),
            actions: t('ow.object.tab_actions')
          };
          return (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              className={`py-3 px-4 text-xs font-medium border-b-2 -mb-px transition-colors ${
                activeTab === tab
                  ? 'border-blue-600 text-blue-600'
                  : `border-transparent ${styles.cardTextMuted} hover:opacity-80`
              }`}
            >
              {tabLabels[tab]}
            </button>
          );
        })}
      </div>

      {/* Active Tab Panel */}
      <div className="flex-1 overflow-y-auto p-6">
        {/* PROPERTIES TAB */}
        {activeTab === 'properties' && (
          <div className="space-y-6">
            <div className="flex justify-between items-center">
              <div className={`text-xs ${styles.cardTextMuted}`}>
                {t('ow.object.props_hint')}
              </div>
              <div className="flex items-center gap-2">
                <input
                  type="text"
                  placeholder={t('ow.object.new_prop_placeholder')}
                  value={newPropName}
                  onChange={e => setNewPropName(e.target.value)}
                  className="px-3 py-1 text-xs border border-gray-300 rounded focus:border-blue-500 focus:outline-hidden"
                />
                <select
                  value={newPropType}
                  onChange={e => setNewPropType(e.target.value as any)}
                  className={`px-2 py-1 text-xs border border-gray-300 rounded ${styles.inputBg} focus:border-blue-500 focus:outline-hidden`}
                >
                  <option value="string">{t('ow.object.dt_string')}</option>
                  <option value="integer">{t('ow.object.dt_integer')}</option>
                  <option value="decimal">{t('ow.object.dt_decimal')}</option>
                  <option value="boolean">{t('ow.object.dt_boolean')}</option>
                  <option value="date">{t('ow.object.dt_date')}</option>
                  <option value="timestamp">{t('ow.object.dt_timestamp')}</option>
                  <option value="geopoint">{t('ow.object.dt_geopoint')}</option>
                </select>
                <button
                  onClick={handleAddProperty}
                  className="bg-blue-600 hover:bg-blue-700 text-white text-xs px-3 py-1 rounded transition-colors flex items-center gap-1"
                >
                  <LucideIcon name="Plus" size={13} />
                  {t('ow.object.add_property')}
                </button>
              </div>
            </div>

            <div className={`overflow-x-auto border ${styles.appBorder} rounded-lg`}>
              <table className="w-full text-left border-collapse text-xs">
                <thead>
                  <tr className={`${styles.appBg} border-b ${styles.appBorder} ${styles.cardText} font-medium`}>
                    <th className="py-2.5 px-4 w-12 text-center">{t('ow.object.th_pk')}</th>
                    <th className="py-2.5 px-4">{t('ow.object.th_display')}</th>
                    <th className="py-2.5 px-4">{t('ow.object.th_api')}</th>
                    <th className="py-2.5 px-4">{t('ow.object.th_type')}</th>
                    <th className="py-2.5 px-4">{t('ow.object.th_desc')}</th>
                    <th className="py-2.5 px-4">{t('ow.object.th_shared_bind')}</th>
                    <th className="py-2.5 px-4 text-center">{t('ow.object.th_actions')}</th>
                  </tr>
                </thead>
                <tbody className={`divide-y divide-gray-100 ${styles.cardText}`}>
                  {objectType.properties.map(prop => (
                    <tr key={prop.id} className={`${styles.sidebarHoverBg} transition-colors`}>
                      <td className="py-2.5 px-4 text-center">
                        <button
                          onClick={() => handleTogglePrimaryKey(prop.id)}
                          className={`p-1.5 rounded-full transition-colors ${
                            objectType.primaryKey === prop.id
                              ? 'text-amber-500 hover:bg-amber-50'
                              : `${styles.cardTextMuted} hover:opacity-70`
                          }`}
                          title={objectType.primaryKey === prop.id ? t('ow.object.pk_current') : t('ow.object.pk_set')}
                        >
                          <LucideIcon name="Key" size={14} className={objectType.primaryKey === prop.id ? 'fill-amber-500' : ''} />
                        </button>
                      </td>
                      <td className="py-2.5 px-4">
                        <input
                          type="text"
                          value={prop.displayName}
                          onChange={e => handlePropertyFieldChange(prop.id, 'displayName', e.target.value)}
                          className={`font-medium ${styles.cardText} border-b border-transparent hover:opacity-70 focus:border-blue-500 focus:outline-hidden py-0.5 px-1`}
                        />
                      </td>
                      <td className={`py-2.5 px-4 font-mono ${styles.cardTextMuted}`}>
                        <input
                          type="text"
                          value={prop.apiName}
                          onChange={e => handlePropertyFieldChange(prop.id, 'apiName', e.target.value)}
                          className="border-b border-transparent hover:opacity-70 focus:border-blue-500 focus:outline-hidden py-0.5 px-1 w-full"
                        />
                      </td>
                      <td className="py-2.5 px-4">
                        <select
                          value={prop.dataType}
                          onChange={e => handlePropertyFieldChange(prop.id, 'dataType', e.target.value)}
                          className={`bg-transparent border ${styles.appBorder} rounded px-1.5 py-0.5 focus:border-blue-500 focus:outline-hidden font-mono`}
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
                          className={`text-xs ${styles.cardTextMuted} border-b border-transparent focus:border-blue-500 focus:outline-hidden py-0.5 px-1 w-full`}
                          placeholder={t('ow.object.no_desc_ph')}
                        />
                      </td>
                      <td className="py-2.5 px-4">
                        <select
                          value={prop.sharedPropertyId || ''}
                          onChange={e => handlePropertyFieldChange(prop.id, 'sharedPropertyId', e.target.value || undefined)}
                          className={`bg-transparent border ${styles.appBorder} rounded px-1.5 py-0.5 focus:border-blue-500 focus:outline-hidden ${styles.cardText}`}
                        >
                          <option value="">{t('ow.object.bind_none')}</option>
                          {sharedProperties.map(sp => (
                            <option key={sp.id} value={sp.id}>{sp.displayName} ({sp.apiName})</option>
                          ))}
                        </select>
                      </td>
                      <td className="py-2.5 px-4 text-center">
                        <button
                          onClick={() => handleRemoveProperty(prop.id)}
                          className={`text-xs ${styles.cardTextMuted} hover:text-red-500 p-1 rounded hover:bg-blue-50/20 transition-colors`}
                          title={t('ow.object.delete_prop')}
                        >
                          <LucideIcon name="X" size={14} />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        )}

        {/* METADATA CONFIG TAB */}
        {activeTab === 'metadata' && (
          <div className="space-y-6 max-w-2xl">
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-1.5">
                <label className={`text-xs font-semibold ${styles.cardText}`}>{t('ow.object.meta_display')}</label>
                <input
                  type="text"
                  value={objectType.displayName}
                  onChange={e => handleMetaChange('displayName', e.target.value)}
                  className="w-full px-3 py-1.5 text-xs border border-gray-300 rounded focus:border-blue-500 focus:outline-hidden"
                />
              </div>
              <div className="space-y-1.5">
                <label className={`text-xs font-semibold ${styles.cardText}`}>{t('ow.object.meta_api')}</label>
                <input
                  type="text"
                  value={objectType.apiName}
                  onChange={e => handleMetaChange('apiName', e.target.value)}
                  className="w-full px-3 py-1.5 text-xs border border-gray-300 rounded focus:border-blue-500 focus:outline-hidden"
                />
              </div>
            </div>

            <div className="space-y-1.5">
              <label className={`text-xs font-semibold ${styles.cardText}`}>{t('ow.object.meta_description')}</label>
              <textarea
                value={objectType.description}
                onChange={e => handleMetaChange('description', e.target.value)}
                className="w-full h-20 px-3 py-1.5 text-xs border border-gray-300 rounded focus:border-blue-500 focus:outline-hidden"
                placeholder={t('ow.object.meta_description_ph')}
              />
            </div>

            <div className="space-y-1.5 border-t border-gray-100 pt-4">
              <label className={`text-xs font-semibold ${styles.cardText}`}>{t('ow.object.meta_domain')}</label>
              <select
                value={objectType.domainId || ''}
                onChange={e => handleMetaChange('domainId', e.target.value || undefined)}
                className={`w-full px-3 py-1.5 text-xs border border-gray-300 rounded ${styles.inputBg} focus:border-blue-500 focus:outline-hidden`}
              >
                <option value="">{t('ow.object.meta_domain_none')}</option>
                {domains.map(d => (
                  <option key={d.id} value={d.id}>{d.displayName}</option>
                ))}
              </select>
              <p className={`text-[10px] ${styles.cardTextMuted}`}>{t('ow.object.meta_domain_hint')}</p>
            </div>

            <div className="grid grid-cols-2 gap-4 border-t border-gray-100 pt-4">
              <div className="space-y-1.5">
                <label className={`text-xs font-semibold ${styles.cardText}`}>{t('ow.object.meta_title_prop')}</label>
                <select
                  value={objectType.titleProperty}
                  onChange={e => handleMetaChange('titleProperty', e.target.value)}
                  className={`w-full px-3 py-1.5 text-xs border border-gray-300 rounded ${styles.inputBg} focus:border-blue-500 focus:outline-hidden`}
                >
                  {objectType.properties.map(p => (
                    <option key={p.id} value={p.id}>{p.displayName} ({p.apiName})</option>
                  ))}
                </select>
                <p className={`text-[10px] ${styles.cardTextMuted}`}>{t('ow.object.meta_title_prop_hint')}</p>
              </div>

              <div className="space-y-1.5">
                <label className={`text-xs font-semibold ${styles.cardText}`}>{t('ow.object.meta_status')}</label>
                <select
                  value={objectType.status}
                  onChange={e => handleMetaChange('status', e.target.value)}
                  className={`w-full px-3 py-1.5 text-xs border border-gray-300 rounded ${styles.inputBg} focus:border-blue-500 focus:outline-hidden`}
                >
                  <option value="DRAFT">{t('ow.object.status_draft')}</option>
                  <option value="ACTIVE">{t('ow.object.status_active')}</option>
                  <option value="DEPRECATED">{t('ow.object.status_deprecated')}</option>
                </select>
              </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-1.5">
                <label className={`text-xs font-semibold ${styles.cardText}`}>{t('ow.object.meta_icon')}</label>
                <select
                  value={objectType.icon}
                  onChange={e => handleMetaChange('icon', e.target.value)}
                  className="w-full px-3 py-1.5 text-xs border border-gray-300 rounded bg-white focus:border-blue-500 focus:outline-hidden"
                >
                  <option value="Plane">Plane ({t('ow.object.icon_plane')})</option>
                  <option value="Building2">Building2 ({t('ow.object.icon_building')})</option>
                  <option value="Navigation">Navigation ({t('ow.object.icon_navigation')})</option>
                  <option value="UserSquare2">UserSquare2 ({t('ow.object.icon_user')})</option>
                  <option value="Database">Database ({t('ow.object.icon_database')})</option>
                  <option value="ShieldAlert">ShieldAlert ({t('ow.object.icon_shield')})</option>
                  <option value="FileText">FileText ({t('ow.object.icon_file')})</option>
                  <option value="Heart">Heart ({t('ow.object.icon_heart')})</option>
                </select>
              </div>

              <div className="space-y-1.5">
                <label className={`text-xs font-semibold ${styles.cardText}`}>{t('ow.object.meta_color')}</label>
                <select
                  value={objectType.color}
                  onChange={e => handleMetaChange('color', e.target.value)}
                  className="w-full px-3 py-1.5 text-xs border border-gray-300 rounded bg-white focus:border-blue-500 focus:outline-hidden"
                >
                  <option value="border-blue-500 bg-blue-50 text-blue-700">{t('ow.object.color_blue')}</option>
                  <option value="border-emerald-500 bg-emerald-50 text-emerald-700">{t('ow.object.color_emerald')}</option>
                  <option value="border-purple-500 bg-purple-50 text-purple-700">{t('ow.object.color_purple')}</option>
                  <option value="border-orange-500 bg-orange-50 text-orange-700">{t('ow.object.color_orange')}</option>
                  <option value="border-red-500 bg-red-50 text-red-700">{t('ow.object.color_red')}</option>
                  <option value="border-slate-500 bg-slate-50 text-slate-700">{t('ow.object.color_slate')}</option>
                </select>
              </div>
            </div>

            {/* Implements Interfaces */}
            <div className="space-y-2 border-t border-gray-100 pt-4">
              <label className={`text-xs font-semibold ${styles.cardText} block`}>{t('ow.object.meta_interfaces')}</label>
              <div className="flex flex-wrap gap-2">
                {interfaces.map(intf => {
                  const isChecked = (objectType.interfaces || []).includes(intf.id);
                  return (
                    <label key={intf.id} className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg border text-xs cursor-pointer select-none transition-colors ${
                      isChecked ? 'bg-blue-50 border-blue-300 text-blue-700' : `${styles.cardBg} border ${styles.inputBorder} ${styles.cardTextMuted} hover:bg-blue-50/20`
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
                      <LucideIcon name="Layers" size={13} />
                      <span>{intf.displayName}</span>
                    </label>
                  );
                })}
              </div>
              <p className={`text-[10px] ${styles.cardTextMuted}`}>{t('ow.object.meta_interfaces_hint')}</p>
            </div>
          </div>
        )}

        {/* DATA SOURCE MAPPING TAB */}
        {activeTab === 'mapping' && (
          <div className="space-y-6">
            <div className={`${styles.appBg} border ${styles.appBorder} rounded-lg p-4 flex justify-between items-center`}>
              <div className="flex items-center gap-3">
                <LucideIcon name="Database" className={styles.cardTextMuted} size={18} />
                <div>
                  <div className={`text-xs font-semibold ${styles.cardText}`}>{t('ow.object.map_bound_dataset')}</div>
                  <div className={`text-[10px] ${styles.cardTextMuted} font-mono mt-0.5`}>{selectedDataset?.path}</div>
                </div>
              </div>
              <div className="flex gap-2">
                <select
                  value={objectType.mapping.datasetId}
                  onChange={e => handleDatasetChange(e.target.value)}
                  className={`px-3 py-1.5 text-xs border ${styles.inputBorder} rounded ${styles.inputBg} focus:outline-hidden`}
                >
                  {datasets.map(ds => (
                    <option key={ds.id} value={ds.id}>{ds.name}</option>
                  ))}
                </select>
                <button
                  onClick={handleAutoMap}
                  className={`bg-[var(--card,#94A3B8)] hover:bg-[var(--card,#64748B)] ${styles.cardText} text-xs px-3 py-1.5 rounded font-medium transition-colors flex items-center gap-1`}
                >
                  <LucideIcon name="Wand2" size={13} />
                  {t('ow.object.map_auto')}
                </button>
              </div>
            </div>

            {/* Split Screen Mapping Grid */}
            <div className="grid grid-cols-2 gap-8 relative">
              {/* Left Column: Raw Datasource */}
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <div className={`text-xs font-semibold ${styles.cardText} flex items-center gap-1`}>
                    <LucideIcon name="Table" size={14} />
                    {t('ow.object.map_schema')}
                  </div>
                  <span className={`text-[10px] ${styles.sidebarBg} ${styles.cardTextMuted} px-1.5 py-0.5 rounded font-mono`}>
                    {selectedDataset?.columns.length} {t('ow.object.map_columns')}
                  </span>
                </div>
                <div className={`border ${styles.appBorder} rounded-lg ${styles.cardBg} overflow-hidden divide-y ${styles.divider}`}>
                  {selectedDataset?.columns.map(col => (
                    <div key={col.name} className="flex justify-between items-center px-4 py-2.5 hover:bg-blue-50/20">
                      <div className={`font-mono text-xs font-medium ${styles.cardTextMuted}`}>{col.name}</div>
                      <div className={`text-[10px] ${styles.cardTextMuted} font-mono italic uppercase ${styles.sidebarBg} px-1.5 py-0.5 rounded`}>
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
                    <LucideIcon name="Layers" size={14} />
                    {t('ow.object.map_props')}
                  </div>
                  <span className="text-[10px] bg-blue-50 text-blue-600 px-1.5 py-0.5 rounded font-mono">
                    {Object.keys(objectType.mapping.propertyMappings).length} / {objectType.properties.length} {t('ow.object.map_mapped')}
                  </span>
                </div>
                <div className={`border ${styles.appBorder} rounded-lg ${styles.cardBg} overflow-hidden divide-y ${styles.divider}`}>
                  {objectType.properties.map(prop => {
                    const mappedCol = objectType.mapping.propertyMappings[prop.id] || '';
                    return (
                      <div key={prop.id} className={`flex justify-between items-center px-4 py-2.5 ${styles.cardBg} hover:bg-blue-50/20`}>
                        <div className="flex items-center gap-2">
                          <span className={objectType.primaryKey === prop.id ? 'text-amber-500' : styles.cardTextMuted}>
                            <LucideIcon name={objectType.primaryKey === prop.id ? 'Key' : 'CircleDot'} size={12} />
                          </span>
                          <div>
                            <div className={`text-xs font-medium ${styles.text}`}>{prop.displayName}</div>
                            <div className={`text-[10px] ${styles.cardTextMuted} font-mono mt-0.5`}>{prop.id} · {prop.dataType}</div>
                          </div>
                        </div>

                        {/* Mapped Selector */}
                        <div className="flex items-center gap-2">
                          <span className={`${styles.cardTextMuted} text-[10px]`}>←</span>
                          <select
                            value={mappedCol}
                            onChange={e => handlePropMappingChange(prop.id, e.target.value)}
                            className={`px-2 py-1 text-xs border rounded ${styles.cardBg} focus:outline-hidden font-mono ${styles.inputText} ${
                              mappedCol ? 'border-emerald-300 bg-emerald-50/30' : 'border-amber-300 bg-amber-50/10'
                            }`}
                          >
                            <option value="">{t('ow.object.map_unmapped')}</option>
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
        )}

        {/* RELATED LINKS TAB */}
        {activeTab === 'links' && (
          <div className="space-y-4">
            <div className={`text-xs ${styles.cardTextMuted}`}>
              {t('ow.object.links_hint', { name: objectType.displayName })}
            </div>
            {relatedLinks.length === 0 ? (
              <div className={`text-center py-8 border border-dashed ${styles.sidebarBorder} rounded-lg ${styles.cardTextMuted} text-xs`}>
                {t('ow.object.links_empty')}
              </div>
            ) : (
              <div className="grid grid-cols-2 gap-4">
                {relatedLinks.map(link => {
                  const isSource = link.sourceObjectType === objectType.id;
                  const otherObjId = isSource ? link.targetObjectType : link.sourceObjectType;

                  return (
                    <div
                      key={link.id}
                      onClick={() => onNavigateToLink(link.id)}
                      className={`p-4 border ${styles.appBorder} rounded-xl hover:border-blue-400 hover:shadow-xs transition-all cursor-pointer ${styles.cardBg} group flex items-start justify-between`}
                    >
                      <div className="space-y-2">
                        <div className="flex items-center gap-2">
                          <span className={`p-1 rounded-md ${styles.sidebarBg} ${styles.cardTextMuted} group-hover:bg-blue-50 group-hover:text-blue-600 transition-colors`}>
                            <LucideIcon name="GitMerge" size={14} />
                          </span>
                          <div className={`font-semibold text-xs ${styles.cardText} group-hover:text-blue-600`}>
                            {link.displayName}
                          </div>
                          <span className={`text-[10px] font-mono ${styles.cardTextMuted} ${styles.appBg} px-1 rounded`}>
                            {link.cardinality}
                          </span>
                        </div>
                        <p className={`text-[10px] ${styles.cardTextMuted}`}>{link.description}</p>
                        <div className={`flex items-center gap-1.5 text-[10px] ${styles.sidebarText} pt-1`}>
                          <span className={isSource ? 'font-semibold text-blue-600' : ''}>
                            {isSource ? t('ow.object.links_source') : t('ow.object.links_source_id', { id: link.sourceObjectType })}
                          </span>
                          <span>→</span>
                          <span className={!isSource ? 'font-semibold text-blue-600' : ''}>
                            {!isSource ? t('ow.object.links_target') : t('ow.object.links_target_id', { id: link.targetObjectType })}
                          </span>
                        </div>
                      </div>
                      <div className="flex items-center gap-1 text-[10px] text-blue-500 group-hover:translate-x-0.5 transition-transform">
                        <span>{t('ow.object.jump_config')}</span>
                        <LucideIcon name="ChevronRight" size={12} />
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}

        {/* RELATED ACTIONS TAB */}
        {activeTab === 'actions' && (
          <div className="space-y-4">
            <div className={`text-xs ${styles.cardTextMuted}`}>
              {t('ow.object.actions_hint', { name: objectType.displayName })}
            </div>
            {relatedActions.length === 0 ? (
              <div className={`text-center py-8 border border-dashed ${styles.sidebarBorder} rounded-lg ${styles.cardTextMuted} text-xs`}>
                {t('ow.object.actions_empty')}
              </div>
            ) : (
              <div className="grid grid-cols-2 gap-4">
                {relatedActions.map(action => (
                  <div
                    key={action.id}
                    onClick={() => onNavigateToAction(action.id)}
                    className={`p-4 border ${styles.appBorder} rounded-xl hover:border-blue-400 hover:shadow-xs transition-all cursor-pointer ${styles.cardBg} group flex items-start justify-between`}
                  >
                    <div className="space-y-2">
                      <div className="flex items-center gap-2">
                        <span className="p-1.5 rounded-full bg-amber-50 text-amber-600 group-hover:bg-amber-100 transition-colors">
                          <LucideIcon name="Zap" size={13} className="fill-amber-500" />
                        </span>
                        <div className={`font-semibold text-xs ${styles.cardText} group-hover:text-blue-600`}>
                          {action.displayName}
                        </div>
                      </div>
                      <p className={`text-[10px] ${styles.cardTextMuted}`}>{action.description}</p>
                      <div className={`flex items-center gap-2 font-mono text-[9px] ${styles.cardTextMuted} ${styles.appBg} p-1.5 rounded border ${styles.divider}`}>
                        <div>
                          <strong>{t('ow.object.action_params')}:</strong> {action.parameters.length} | <strong>{t('ow.object.action_side_effects')}:</strong> {action.rules.length} {t('ow.object.action_rules_unit')}
                        </div>
                      </div>
                    </div>
                    <div className="flex items-center gap-1 text-[10px] text-blue-500 group-hover:translate-x-0.5 transition-transform">
                      <span>{t('ow.object.jump_config')}</span>
                      <LucideIcon name="ChevronRight" size={12} />
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
