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
import { Compass, Trash2, AlertTriangle, GitPullRequest } from 'lucide-react';
import DynamicIcon from '../../components/ontology/DynamicIcon';
import { fetchMappings, createMapping, updateMapping, fetchLineageImpact, updateEntity, reassignObjectDomain, fetchDwDatasetColumns } from '../../services/ontologyApi';

import type { ObjectType, PropertyType, Dataset, DatasetColumn, LinkType, ActionType, SharedProperty, InterfaceType, OntologyDomain, OntologyMappingRecord, LineageImpactResult } from '../../types/ontology';
import PropertiesTab from './object/PropertiesTab';
import MetadataTab from './object/MetadataTab';
import MappingTab from './object/MappingTab';
import LinksTab from './object/LinksTab';
import ActionsTab from './object/ActionsTab';
import GlossaryTab from './object/GlossaryTab';

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
  /** 基于当前对象类型发起变更提案（由工作台聚焦提案面板并展开表单） */
  onCreateProposal?: (id: string) => void;
  /** 全局轻提示（保存结果反馈）；未注入时静默不提示（不影响功能） */
  onToast?: (type: 'success' | 'info' | 'error', message: string) => void;
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
  onExploreData,
  onCreateProposal,
  onToast
}: ObjectTypeViewProps) {
  const [activeTab, setActiveTab] = useState<'metadata' | 'properties' | 'mapping' | 'links' | 'actions' | 'lineage' | 'glossary'>('properties');
  const [newPropName, setNewPropName] = useState('');
  const [newPropType, setNewPropType] = useState<'string' | 'integer' | 'decimal' | 'boolean' | 'date' | 'timestamp' | 'geopoint'>('string');
  const [mappingRecord, setMappingRecord] = useState<OntologyMappingRecord | null>(null);
  const [mappingDirty, setMappingDirty] = useState(false);
  /** 基础信息 Tab 未保存标记（改动后启用保存按钮） */
  const [metaDirty, setMetaDirty] = useState(false);
  const [metaSaving, setMetaSaving] = useState(false);
  /** 基础信息保存基线：进入该对象时的域归属，用于判断是否需调用域变更端点 */
  const [metaBaseline, setMetaBaseline] = useState<{ domainId?: string }>({});
  const [impactResult, setImpactResult] = useState<LineageImpactResult | null>(null);
  const [impactLoading, setImpactLoading] = useState(false);

  const mapping = objectType.mapping || { datasetId: '', propertyMappings: {} };
  /** DW 层数据对象的列定义缓存（选中时懒加载，避免对全部对象做 N+1 列查询） */
  const [datasetColumns, setDatasetColumns] = useState<Record<string, DatasetColumn[]>>({});
  const baseDataset = datasets.find(d => d.id === mapping.datasetId) || datasets[0];
  const selectedDataset = baseDataset
    ? { ...baseDataset, columns: datasetColumns[baseDataset.id] ?? baseDataset.columns }
    : undefined;

  const { t } = useLanguage();
  const { styles } = useTheme();

  useEffect(() => {
    let cancelled = false;
    // 切换对象：重置基础信息编辑态，并以当前域归属作为保存基线
    setMetaDirty(false);
    setMetaBaseline({ domainId: objectType.domainId });
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

  // DW 层数据对象列定义懒加载：选中对象后才拉取其列，避免对全部 DW 对象做 N+1 查询
  useEffect(() => {
    const datasetId = baseDataset?.id;
    if (!datasetId || datasetColumns[datasetId]) {
      return;
    }
    let cancelled = false;
    fetchDwDatasetColumns(datasetId)
      .then(cols => {
        if (!cancelled) {
          setDatasetColumns(prev => ({ ...prev, [datasetId]: cols }));
        }
      })
      .catch((e: any) => {
        // 列定义拉取失败不阻断页面，但必须显式提示（左栏将显示 0 列，不得静默空白）
        onToast?.('error', t('ow.msg.datasetColumnsFailed').replace('{error}', String(e?.message || e)));
      });
    return () => { cancelled = true; };
  }, [baseDataset?.id, datasetColumns]);

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
    setMetaDirty(true);
    onUpdate({ ...objectType, [key]: value });
  };

  /**
   * 保存基础信息：基础字段与域归属走两个端点。
   *
   * <p>基础字段 → PUT /api/v1/ecos/ontologies/{ontologyId}/entities/{id}
   * （后端 updateEntity SQL 不含 domain_id，故域变更必须走专用端点）；
   * 域归属 → PUT /api/v1/ontology/objects/{id}/domain（后端按 code 或 id 解析后落域主键）。
   */
  const handleSaveMetadata = async () => {
    if (metaSaving) {
      return;
    }
    const domainChanged = (objectType.domainId || '') !== (metaBaseline.domainId || '');
    setMetaSaving(true);
    try {
      // 基础字段与域归属是两个端点：先落基础字段（不可因域校验失败而一起丢失），
      // 再处理域变更；域置空不受后端支持，单独提示且不影响基础字段已保存的结果。
      await updateEntity(objectType.id, {
        code: objectType.apiName,
        name: objectType.displayName,
        description: objectType.description,
      });
      if (domainChanged && !objectType.domainId) {
        onToast?.('error', t('ow.msg.domainClearUnsupported'));
        setMetaDirty(false);
        return;
      }
      if (domainChanged) {
        // 传域主键（ecos_ontology_entity.domain_id 的外键目标），后端按 id 落库
        await reassignObjectDomain(objectType.id, { domainId: objectType.domainId as string });
      }
      setMetaBaseline({ domainId: objectType.domainId });
      setMetaDirty(false);
      onToast?.('success', t('ow.msg.metaSaved').replace('{name}', objectType.displayName));
    } catch (err: any) {
      onToast?.('error', t('ow.msg.metaSaveFailed').replace('{error}', String(err?.message || err)));
    } finally {
      setMetaSaving(false);
    }
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

  /**
   * 切换属性标志位（本地草稿态）
   * - isPrimaryKey → 同时更新 objectType.primaryKey（全表唯一）
   * - required     → 仅更新该属性的 required 标记
   * 变更需经提案审批后由后端落库生效（本体模型变更必须走版本发布流程）
   */
  const handleTogglePropertyFlag = (propId: string, field: 'isPrimaryKey' | 'required') => {
    if (field === 'isPrimaryKey') {
      const nextPrimary = objectType.primaryKey === propId ? '' : propId;
      onUpdate({
        ...objectType,
        primaryKey: nextPrimary,
        properties: objectType.properties.map(p => ({ ...p, isPrimaryKey: p.id === nextPrimary }))
      });
      return;
    }
    onUpdate({
      ...objectType,
      properties: objectType.properties.map(p =>
        p.id === propId ? { ...p, required: !p.required } : p
      )
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
          {onCreateProposal && (
            <button onClick={() => onCreateProposal(objectType.id)}
              className={`text-xs ${styles.accentText} hover:bg-blue-50 px-2.5 py-1.5 rounded border ${styles.accentBorder} transition-colors flex items-center gap-1.5 font-semibold`}>
              <GitPullRequest size={13} />{t('ow.btn.createProposal')}
            </button>
          )}
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
        {(['properties', 'mapping', 'metadata', 'links', 'actions', 'lineage', 'glossary'] as const).map(tab => {
          const tabLabels: Record<string, string> = {
            properties: t('ow.tab.properties'), mapping: t('ow.tab.mapping'), metadata: t('ow.tab.metadata'),
            links: t('ow.tab.links'), actions: t('ow.tab.actions'), lineage: t('ow.btn.viewLineage'),
            glossary: t('ow.tab.glossary')
          };
          return (
            <button key={tab} onClick={() => setActiveTab(tab)}
              className={`py-3 px-4 text-xs font-medium border-b-2 -mb-px transition-colors ${
                activeTab === tab ? `${styles.accentBorder} ${styles.accentText}` : `border-transparent ${styles.cardTextMuted} hover:opacity-100 opacity-80`
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
            handleTogglePropertyFlag={handleTogglePropertyFlag}
            handlePropertyFieldChange={handlePropertyFieldChange}
            handleRemoveProperty={handleRemoveProperty}
            sharedProperties={sharedProperties}
          />
        )}
        {activeTab === 'metadata' && (
          <MetadataTab objectType={objectType}
            handleMetaChange={handleMetaChange} domains={domains} interfaces={interfaces}
            metaDirty={metaDirty} metaSaving={metaSaving} onSaveMetadata={handleSaveMetadata} />
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
        {activeTab === 'glossary' && (
          <GlossaryTab objectType={objectType} onToast={onToast} />
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
