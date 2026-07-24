/**
 * ObjectTypeDetail — 对象类型详情视图
 *
 * 本体工作台核心详情页，展示单个 ObjectType 的属性/元数据/映射/关联链接/操作。
 * Tab 内容已拆分为独立组件（见 ./object/ 目录）。
 *
 * @license Apache-2.0
 */

import React, { useState, useEffect } from 'react';
import { useLanguage } from '../../components/LanguageContext';
import { useTheme } from '../../components/ThemeContext';
import { Compass, Trash2, AlertTriangle } from 'lucide-react';
import DynamicIcon from '../../components/ontology/DynamicIcon';
import { fetchMappings, createMapping, updateMapping, fetchLineageImpact } from '../../services/ontologyApi';

import type { ObjectType, PropertyType, Dataset, LinkType, ActionType, SharedProperty, InterfaceType, OntologyDomain, OntologyMappingRecord, LineageImpactResult } from '../../types/ontology';
import PropertiesTab from './object/PropertiesTab';
import MetadataTab from './object/MetadataTab';
import MappingTab from './object/MappingTab';
import LinksTab from './object/LinksTab';
import ActionsTab from './object/ActionsTab';

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
  const [activeTab, setActiveTab] = useState<'metadata' | 'properties' | 'mapping' | 'links' | 'actions' | 'lineage'>('properties');
  const [newPropName, setNewPropName] = useState('');
  const [newPropType, setNewPropType] = useState<'string' | 'integer' | 'decimal' | 'boolean' | 'date' | 'timestamp' | 'geopoint'>('string');
  const [mappingRecord, setMappingRecord] = useState<OntologyMappingRecord | null>(null);
  const [mappingDirty, setMappingDirty] = useState(false);
  const [impactResult, setImpactResult] = useState<LineageImpactResult | null>(null);
  const [impactLoading, setImpactLoading] = useState(false);

  const mapping = objectType.mapping || { datasetId: '', propertyMappings: {} };
  const selectedDataset = datasets.find(d => d.id === mapping.datasetId) || datasets[0];

  const { t } = useLanguage();
  const { styles } = useTheme();

  useEffect(() => {
    let cancelled = false;
    fetchMappings({ objectId: objectType.id })
      .then(records => {
        if (cancelled) return;
        if (records.length > 0) {
          const rec = records[0];
          setMappingRecord(rec);
          setMappingDirty(false);
          onUpdate({
            ...objectType,
            mapping: { datasetId: rec.datasetId, propertyMappings: rec.propertyMappings }
          });
        }
      })
      .catch(() => {});
    return () => { cancelled = true; };
  }, [objectType.id]);

  const handleSaveMapping = async () => {
    try {
      const propMappings = mapping.propertyMappings;
      const fieldMappings = Object.entries(propMappings).map(([source, target]) => ({ source, target }));
      if (mappingRecord) {
        const updated = await updateMapping(mappingRecord.id, {
          fieldMappings,
          propertyMappings: propMappings,
          description: mappingRecord.description,
        });
        setMappingRecord(updated);
      } else {
        const created = await createMapping({
          objectTypeId: objectType.id,
          datasetId: mapping.datasetId,
          propertyMappings: propMappings
        });
        setMappingRecord(created);
      }
      setMappingDirty(false);
    } catch (err: any) {
      alert(err?.message || t('ow.label.mappingSaveError'));
    }
  };

  const handleImpactAnalysis = async () => {
    setImpactLoading(true);
    try {
      const result = await fetchLineageImpact({ objectId: objectType.id });
      setImpactResult(result);
    } catch {
      setImpactResult(null);
    } finally {
      setImpactLoading(false);
    }
  };

  // ── Handlers ──
  const handleMetaChange = (key: keyof ObjectType, value: any) => {
    onUpdate({ ...objectType, [key]: value });
  };

  const handleDatasetChange = (datasetId: string) => {
    setMappingDirty(true);
    onUpdate({ ...objectType, mapping: { datasetId, propertyMappings: {} } });
  };

  const handlePropMappingChange = (propId: string, colName: string) => {
    setMappingDirty(true);
    onUpdate({
      ...objectType,
      mapping: {
        ...mapping,
        propertyMappings: { ...mapping.propertyMappings, [propId]: colName }
      }
    });
  };

  const handleAutoMap = () => {
    if (!selectedDataset) return;
    const newMappings: Record<string, string> = {};
    objectType.properties.forEach(prop => {
      const matchedCol = selectedDataset.columns.find(col => {
        const cNorm = col.name.toLowerCase().replace(/_/g, '');
        const pNormName = prop.displayName.toLowerCase().replace(/_/g, '');
        const pNormApi = prop.apiName.toLowerCase().replace(/_/g, '');
        const pNormId = prop.id.toLowerCase().replace(/_/g, '');
        return cNorm === pNormName || cNorm === pNormApi || cNorm === pNormId;
      });
      if (matchedCol) newMappings[prop.id] = matchedCol.name;
    });
    setMappingDirty(true);
    onUpdate({
      ...objectType,
      mapping: { ...mapping, propertyMappings: { ...mapping.propertyMappings, ...newMappings } }
    });
  };

  const handleAddProperty = () => {
    if (!newPropName.trim()) return;
    const propId = newPropName.trim().replace(/\s+/g, '');
    const newProp: PropertyType = {
      id: propId, displayName: newPropName,
      apiName: propId.charAt(0).toLowerCase() + propId.slice(1),
      dataType: newPropType, isPrimaryKey: false,
      description: t('ow.prop.defaultDescription')
    };
    onUpdate({ ...objectType, properties: [...objectType.properties, newProp] });
    setNewPropName('');
  };

  const handleRemoveProperty = (propId: string) => {
    if (propId === objectType.primaryKey) { alert(t('ontology.cannot_delete_pk')); return; }
    const updatedProps = objectType.properties.filter(p => p.id !== propId);
    const updatedMappings = { ...mapping.propertyMappings };
    delete updatedMappings[propId];
    onUpdate({ ...objectType, properties: updatedProps, mapping: { ...mapping, propertyMappings: updatedMappings } });
  };

  const handleTogglePrimaryKey = (propId: string) => {
    onUpdate({
      ...objectType, primaryKey: propId,
      properties: objectType.properties.map(p => ({ ...p, isPrimaryKey: p.id === propId }))
    });
  };

  const handlePropertyFieldChange = (propId: string, field: keyof PropertyType, value: any) => {
    onUpdate({
      ...objectType,
      properties: objectType.properties.map(p => p.id === propId ? { ...p, [field]: value } : p)
    });
  };

  const relatedLinks = linkTypes.filter(
    l => l.sourceObjectType === objectType.id || l.targetObjectType === objectType.id
  );
  const relatedActions = actionTypes.filter(action =>
    action.parameters.some(param => param.dataType === 'object' && param.objectTypeId === objectType.id)
  );

  return (
    <div className={`flex flex-col h-full ${styles.cardBg}`}>
      {/* Detail Header */}
      <div className={`px-6 py-4 border-b ${styles.cardBorder} flex justify-between items-center ${styles.appBg}`}>
        <div className="flex items-center gap-3">
          <div className={`p-2 rounded-lg border-2 ${objectType.color} flex items-center justify-center`}>
            <DynamicIcon name={objectType.icon} size={20} />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className={`text-lg font-semibold ${styles.cardText}`}>{objectType.displayName}</h2>
              <span className={`text-xs font-mono ${styles.sidebarBg} ${styles.cardTextMuted} px-1.5 py-0.5 rounded`}>{objectType.apiName}</span>
              <span className={`text-[10px] font-bold px-1.5 py-0.5 rounded-full ${
                objectType.status === 'ACTIVE' ? 'bg-emerald-100 text-emerald-800' : 'bg-amber-100 text-amber-800'
              }`}>{objectType.status === 'ACTIVE' ? t('ow.label.published') : t('ow.label.draft')}</span>
            </div>
            <p className={`text-xs ${styles.cardTextMuted} mt-0.5`}>{objectType.description || t('ow.empty.noDescription')}</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {onExploreData && (
            <button onClick={() => onExploreData(objectType.id)}
              className={`text-xs ${styles.accentText} hover:bg-blue-50 px-2.5 py-1.5 rounded border ${styles.accentBorder} transition-colors flex items-center gap-1.5 font-semibold`}>
              <Compass size={13} />{t('ow.btn.exploreData')}
            </button>
          )}
          <button onClick={() => onDelete(objectType.id)}
            className="text-xs text-red-500 hover:bg-red-50 px-2.5 py-1.5 rounded border border-red-200 transition-colors flex items-center gap-1.5">
            <Trash2 size={13} />{t('ow.btn.deleteObject')}
          </button>
        </div>
      </div>

      {/* Detail Tabs */}
      <div className={`flex px-6 border-b ${styles.cardBorder} ${styles.cardBg}`}>
        {(['properties', 'mapping', 'metadata', 'links', 'actions', 'lineage'] as const).map(tab => {
          const tabLabels: Record<string, string> = {
            properties: t('ow.tab.properties'), mapping: t('ow.tab.mapping'), metadata: t('ow.tab.metadata'),
            links: t('ow.tab.links'), actions: t('ow.tab.actions'), lineage: t('ow.btn.viewLineage')
          };
          return (
            <button key={tab} onClick={() => setActiveTab(tab)}
              className={`py-3 px-4 text-xs font-medium border-b-2 -mb-px transition-colors ${
                activeTab === tab ? `${styles.accentBorder} ${styles.accentText}` : `border-transparent ${styles.cardTextMuted} hover:text-slate-800`
              }`}>{tabLabels[tab]}</button>
          );
        })}
      </div>

      {/* Active Tab Panel */}
      <div className="flex-1 overflow-y-auto p-6">
        {activeTab === 'properties' && (
          <PropertiesTab
            objectType={objectType}
            onUpdate={onUpdate}
            newPropName={newPropName} setNewPropName={setNewPropName}
            newPropType={newPropType} setNewPropType={setNewPropType}
            handleAddProperty={handleAddProperty}
            handleTogglePrimaryKey={handleTogglePrimaryKey}
            handlePropertyFieldChange={handlePropertyFieldChange}
            handleRemoveProperty={handleRemoveProperty}
            sharedProperties={sharedProperties}
          />
        )}
        {activeTab === 'metadata' && (
          <MetadataTab objectType={objectType} onUpdate={onUpdate}
            handleMetaChange={handleMetaChange} domains={domains} interfaces={interfaces} />
        )}
        {activeTab === 'mapping' && (
          <MappingTab objectType={objectType} onUpdate={onUpdate}
            datasets={datasets} selectedDataset={selectedDataset}
            handleDatasetChange={handleDatasetChange} handleAutoMap={handleAutoMap}
            handlePropMappingChange={handlePropMappingChange}
            mappingDirty={mappingDirty} onSaveMapping={handleSaveMapping} />
        )}
        {activeTab === 'links' && (
          <LinksTab objectType={objectType} relatedLinks={relatedLinks}
            onNavigateToLink={onNavigateToLink} />
        )}
        {activeTab === 'actions' && (
          <ActionsTab objectType={objectType} relatedActions={relatedActions}
            onNavigateToAction={onNavigateToAction} />
        )}
        {activeTab === 'lineage' && (
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <h3 className={`text-sm font-semibold ${styles.cardText}`}>{t('ow.section.impactAnalysis')}</h3>
              <button onClick={handleImpactAnalysis} disabled={impactLoading}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded text-[10px] font-semibold bg-amber-500 text-white hover:bg-amber-600 disabled:opacity-50`}>
                <AlertTriangle size={12} />{impactLoading ? '...' : t('ow.btn.impactAnalysis')}
              </button>
            </div>
            {impactResult ? (
              <div>
                <div className={`text-[10px] font-semibold ${styles.muted} mb-2`}>
                  {t('ow.label.impactedObjects')}: {impactResult.impactedObjects?.length ?? 0}
                </div>
                {(impactResult.impactedObjects?.length ?? 0) === 0 ? (
                  <p className={`text-xs ${styles.muted}`}>{t('ow.empty.noImpactedObjects')}</p>
                ) : (
                  <div className="space-y-1">
                    {impactResult.impactedObjects?.map((node, idx: number) => (
                      <div key={idx} className={`flex items-center gap-2 px-3 py-2 rounded border ${styles.cardBorder} ${styles.cardBg}`}>
                        <AlertTriangle size={12} className="text-amber-500" />
                        <span className="text-xs font-medium">{node.id}</span>
                        <span className={`text-[10px] ${styles.muted}`}>{node.type}</span>
                        {node.path.length > 0 && <span className={`text-[10px] ${styles.muted}`}>{node.path.join(' → ')}</span>}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            ) : (
              <p className={`text-xs ${styles.muted}`}>{t('ow.btn.impactAnalysis')}</p>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
