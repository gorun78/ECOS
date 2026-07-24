/**
 * OntologyWorkbenchLayout — 本体工作台（ceos_new 完整版本）
 *
 * 基于 ceos_new App.tsx 状态管理模式，完整替换旧版简化布局。
 *
 * 结构：
 *   左侧 → ceos_new Sidebar（域筛选+实体树+搜索+新建）
 *   主区域 → OverviewView / ObjectTypeDetail / LinkTypeDetail / ActionTypeDetail /
 *            FunctionTypeDetail / ObjectExplorerView / OtherViews
 *
 * @license Apache-2.0
 */

import React, { useState, useEffect } from 'react';
import { useLanguage } from '../components/LanguageContext';
import { useTheme } from '../components/ThemeContext';
import type {
  ObjectType,
  LinkType,
  ActionType,
  InterfaceType,
  SharedProperty,
  Dataset,
  FunctionType,
  OntologyDomain
} from '../types/ontology';

import Sidebar from '../components/ontology/Sidebar';
import OverviewView from './ontology/OverviewView';
import ObjectTypeDetail from './ontology/ObjectTypeDetail';
import LinkTypeDetail from './ontology/LinkTypeDetail';
import ActionTypeDetail from './ontology/ActionTypeDetail';
import FunctionTypeDetail from './ontology/FunctionTypeDetail';
import { InterfaceView, SharedPropertyView, DatasetView } from './ontology/OtherViews';
import GlossaryTab from './knowledge/tabs/GlossaryTab';
import ObjectExplorerView from './ObjectExplorerView';
import ProposalPanel from './ontology/ProposalPanel';

import {
  createEntity,
  deleteEntity,
  fetchEntities,
  createRelationship,
  deleteRelationship,
  fetchRelationships,
  DEFAULT_ONTOLOGY_ID,
  createExportTask,
} from '../services/ontologyApi';
import type { CreateExportDTO } from '../types/ontology';
import { useOntologyData } from '../hooks/useOntologyData';
import type { Entity } from '../types/workbench';

type ViewCategory = 'overview' | 'explorer' | 'object' | 'link' | 'action' | 'interface' | 'shared_property' | 'dataset' | 'function' | 'glossary';
type CreatableType = 'object' | 'link' | 'action' | 'interface' | 'shared_property' | 'function';

export default function OntologyWorkbenchLayout() {
  // ── Ontology States ──
  const [objectTypes, setObjectTypes] = useState<ObjectType[]>([]);
  const [linkTypes, setLinkTypes] = useState<LinkType[]>([]);
  const [actionTypes, setActionTypes] = useState<ActionType[]>([]);
  const [interfaces, setInterfaces] = useState<InterfaceType[]>([]);
  const [sharedProperties, setSharedProperties] = useState<SharedProperty[]>([]);
  const [datasets, setDatasets] = useState<Dataset[]>([]);
  const [functionTypes, setFunctionTypes] = useState<FunctionType[]>([]);
  const [domains, setDomains] = useState<OntologyDomain[]>([]);
  const [selectedDomainId, setSelectedDomainId] = useState<string | null>(null);

  // ── View State ──
  const { t } = useLanguage();
  const { styles } = useTheme();
  const [selectedCategory, setSelectedCategory] = useState<ViewCategory>('overview');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [explorerActiveObjectTypeId, setExplorerActiveObjectTypeId] = useState<string | null>(null);
  const [toast, setToast] = useState<{ type: 'success' | 'info' | 'error'; message: string } | null>(null);
  const [showExportModal, setShowExportModal] = useState(false);
  const [exportFormat, setExportFormat] = useState<'JSON' | 'CSV' | 'DDL'>('JSON');
  const [exportScope, setExportScope] = useState<'FULL' | 'ENTITIES' | 'RELATIONSHIPS'>('FULL');
  const [exporting, setExporting] = useState(false);

  // ── Load Initial Data via custom hook ──
  useOntologyData(({ domains: loadedDomains, objectTypes: loadedObjects, linkTypes: loadedLinks }) => {
    setDomains(loadedDomains);
    setObjectTypes(loadedObjects);
    setLinkTypes(loadedLinks);
    // Keep action/interface/shared-prop/function/dataset as empty (no backend yet)
  });

  // ── Toast Helper ──
  const showToast = (type: 'success' | 'info' | 'error', message: string) => {
    setToast({ type, message });
    setTimeout(() => setToast(null), 4000);
  };

  const handleExportOntology = async () => {
    setExporting(true);
    try {
      const dto: CreateExportDTO = {
        ontologyId: DEFAULT_ONTOLOGY_ID,
        format: exportFormat,
        scope: exportScope,
      };
      await createExportTask(dto);
      showToast('success', t('ow.msg.exportStarted'));
      setShowExportModal(false);
    } catch (e: any) {
      showToast('error', t('ow.msg.exportFailed'));
    } finally {
      setExporting(false);
    }
  };

  // ── Mutation Helpers ──
  const updateObjectTypes = (updated: ObjectType[]) => setObjectTypes(updated);
  const updateLinkTypes = (updated: LinkType[]) => setLinkTypes(updated);
  const updateActionTypes = (updated: ActionType[]) => setActionTypes(updated);
  const updateInterfaces = (updated: InterfaceType[]) => setInterfaces(updated);
  const updateSharedProperties = (updated: SharedProperty[]) => setSharedProperties(updated);
  const updateFunctionTypes = (updated: FunctionType[]) => setFunctionTypes(updated);
  const updateDomains = (updated: OntologyDomain[]) => setDomains(updated);

  // ── CREATE ──
  const handleCreateNewElement = async (type: CreatableType) => {
    const defaultNum = Date.now().toString().slice(-4);

    if (type === 'object') {
      try {
        const created = await createEntity({
          name: t('ow.obj.defaultName').replace('{n}', defaultNum),
          code: `custom_obj_${defaultNum}`,
          description: t('ow.obj.defaultDescription'),
          entityType: 'MASTER',
        });
        showToast('success', t('ow.msg.objectCreated').replace('{name}', created.name));
        // Reload from backend
        const entities = await fetchEntities(DEFAULT_ONTOLOGY_ID).catch(() => []);
        const list: ObjectType[] = (entities || []).map((e: any) => ({
          id: e.id,
          displayName: e.name || e.code,
          apiName: e.code || e.name,
          description: e.description || '',
          icon: 'Database',
          color: 'border-indigo-500 bg-indigo-50 text-indigo-700',
          primaryKey: 'id',
          titleProperty: 'id',
          status: 'PUBLISHED',
          properties: [],
          mapping: e.mapping || { datasetId: '', propertyMappings: {} },
          domainId: e.domainId || null,
        }));
        updateObjectTypes(list);
      } catch (e: any) {
        showToast('error', t('ow.msg.createFailed').replace('{error}', String(e.message)));
      }
    } else if (type === 'link') {
      if (objectTypes.length < 2) {
        showToast('error', t('ow.msg.needTwoObjectTypes'));
        return;
      }
      try {
        await createRelationship({
          sourceEntityId: objectTypes[0].id,
          targetEntityId: objectTypes[1].id,
          name: t('ow.link.defaultName').replace('{n}', defaultNum),
          code: `custom_link_${defaultNum}`,
          relationshipType: 'ONE_TO_MANY',
        }, DEFAULT_ONTOLOGY_ID);
        showToast('success', t('ow.msg.linkCreated'));
        const rels = await fetchRelationships(DEFAULT_ONTOLOGY_ID).catch(() => []);
        const list: LinkType[] = (rels || []).map((r: any) => ({
          id: r.id,
          displayName: r.name || '',
          apiName: r.code || '',
          description: '',
          sourceObjectType: r.source_entity_id,
          targetObjectType: r.target_entity_id,
          cardinality: (r.relationship_type === 'ONE_TO_ONE' ? '1:1' : r.relationship_type === 'MANY_TO_MANY' ? 'N:N' : '1:N') as any,
          mapping: { type: 'foreign_key' as const },
        }));
        updateLinkTypes(list);
      } catch (e: any) {
        showToast('error', t('ow.msg.linkCreateFailed').replace('{error}', String(e.message)));
      }
    } else if (type === 'action') {
      const newActionId = `custom_action_${defaultNum}`;
      const newAction: ActionType = {
        id: newActionId,
        displayName: t('ow.action.defaultName').replace('{n}', defaultNum),
        apiName: `customAction${defaultNum}`,
        description: t('ow.action.defaultDescription'),
        parameters: [],
        rules: [],
        validationRules: []
      };
      updateActionTypes([...actionTypes, newAction]);
      setSelectedCategory('action');
      setSelectedId(newActionId);
      showToast('info', t('ow.msg.actionCreated').replace('{name}', newAction.displayName));
    } else if (type === 'interface') {
      const newIntfId = `custom_interface_${defaultNum}`;
      const newIntf: InterfaceType = {
        id: newIntfId,
        displayName: t('ow.intf.defaultName').replace('{n}', defaultNum),
        apiName: `CustomInterface${defaultNum}`,
        description: t('ow.intf.defaultDescription'),
        properties: [
          { id: 'uuid', displayName: t('ow.intf.defaultPropertyName'), apiName: 'uuid', dataType: 'string', isRequired: true, description: t('ow.intf.defaultPropertyDescription') }
        ]
      };
      updateInterfaces([...interfaces, newIntf]);
      setSelectedCategory('interface');
      setSelectedId(newIntfId);
      showToast('info', t('ow.msg.interfaceCreated').replace('{name}', newIntf.displayName));
    } else if (type === 'shared_property') {
      const newSpId = `custom_sp_${defaultNum}`;
      const newSp: SharedProperty = {
        id: newSpId,
        displayName: t('ow.prop.defaultName').replace('{n}', defaultNum),
        apiName: `customSharedProp${defaultNum}`,
        dataType: 'string',
        description: t('ow.prop.defaultDescription')
      };
      updateSharedProperties([...sharedProperties, newSp]);
      setSelectedCategory('shared_property');
      setSelectedId(newSpId);
      showToast('info', t('ow.msg.sharedPropertyCreated').replace('{name}', newSp.displayName));
    } else if (type === 'function') {
      const newFuncId = `custom_function_${defaultNum}`;
      const newFunc: FunctionType = {
        id: newFuncId,
        displayName: t('ow.func.defaultName').replace('{n}', defaultNum),
        apiName: `customFunction${defaultNum}`,
        description: t('ow.func.defaultDescription'),
        returnType: 'string',
        parameters: [],
        code: `import { Function } from "@ecos/functions-api";\n\nexport class CustomFunctionClass_${defaultNum} {\n    @Function()\n    public async customFunction${defaultNum}(): Promise<string> {\n        return "Hello World";\n    }\n}`
      };
      updateFunctionTypes([...functionTypes, newFunc]);
      setSelectedCategory('function');
      setSelectedId(newFuncId);
      showToast('info', t('ow.msg.functionCreated').replace('{name}', newFunc.displayName));
    }
  };

  // ── DELETE ──
  const handleDeleteElement = async (category: string, id: string) => {
    const confirmDelete = window.confirm(t('ontology.confirm_delete_element'));
    if (!confirmDelete) return;

    if (category === 'object') {
      try {
        await deleteEntity(id, DEFAULT_ONTOLOGY_ID);
        showToast('success', t('ow.msg.entityDeleted'));
        updateObjectTypes(objectTypes.filter(ot => ot.id !== id));
        updateLinkTypes(linkTypes.filter(lt => lt.sourceObjectType !== id && lt.targetObjectType !== id));
      } catch (e: any) {
        showToast('error', t('ow.msg.deleteFailed').replace('{error}', String(e.message)));
      }
    } else if (category === 'link') {
      try {
        await deleteRelationship(id, DEFAULT_ONTOLOGY_ID);
        showToast('success', t('ow.msg.linkDeleted'));
        updateLinkTypes(linkTypes.filter(lt => lt.id !== id));
      } catch (e: any) {
        showToast('error', t('ow.msg.deleteFailed').replace('{error}', String(e.message)));
      }
    } else if (category === 'action') {
      updateActionTypes(actionTypes.filter(at => at.id !== id));
    } else if (category === 'interface') {
      updateInterfaces(interfaces.filter(it => it.id !== id));
    } else if (category === 'shared_property') {
      updateSharedProperties(sharedProperties.filter(sp => sp.id !== id));
    } else if (category === 'function') {
      updateFunctionTypes(functionTypes.filter(fn => fn.id !== id));
    }

    setSelectedCategory('overview');
    setSelectedId(null);
    showToast('info', t('ow.msg.elementRemoved'));
  };

  // ── Domain-aware filtering ──
  const filteredObjects = objectTypes.filter(
    ot => !selectedDomainId || ot.domainId === selectedDomainId
  );
  const filteredLinks = linkTypes.filter(
    lt => !selectedDomainId ||
      (objectTypes.find(o => o.id === lt.sourceObjectType)?.domainId === selectedDomainId ||
       objectTypes.find(o => o.id === lt.targetObjectType)?.domainId === selectedDomainId)
  );

  // ── Render ──
  return (
    <div className={`flex h-full ${styles.appBg} ${styles.appText} overflow-hidden text-xs font-sans`}>
      {/* Left Sidebar */}
      <Sidebar
        objectTypes={filteredObjects}
        allObjectTypes={objectTypes}
        linkTypes={filteredLinks}
        actionTypes={actionTypes}
        interfaces={interfaces}
        sharedProperties={sharedProperties}
        datasets={datasets}
        functionTypes={functionTypes}
        domains={domains}
        selectedDomainId={selectedDomainId}
        onSelectDomainId={setSelectedDomainId}
        onUpdateDomains={updateDomains}
        onUpdateObjectTypes={updateObjectTypes}
        selectedCategory={selectedCategory}
        selectedId={selectedId}
        onSelectCategory={(category: ViewCategory, id: string | null) => {
          setSelectedCategory(category);
          setSelectedId(id);
        }}
        onCreateNew={handleCreateNewElement}
      />

      {/* Main Content Area */}
      <main className="flex-1 overflow-hidden relative flex flex-col">
        {/* Top toolbar */}
        <div className={`flex items-center justify-between px-3 py-1.5 border-b ${styles.cardBorder} ${styles.cardBg}`}>
          <span className={`text-[10px] font-semibold uppercase tracking-wider ${styles.muted}`}>
            {selectedCategory === 'overview' ? t('ow.nav.overview') :
             selectedCategory === 'explorer' ? t('ow.nav.explorer') :
             selectedCategory === 'object' ? t('ow.nav.object') :
             selectedCategory === 'link' ? t('ow.nav.link') :
             selectedCategory === 'action' ? t('ow.nav.action') :
             selectedCategory === 'function' ? t('ow.nav.function') :
             selectedCategory === 'interface' ? t('ow.nav.interface') :
             selectedCategory === 'shared_property' ? t('ow.nav.shared_property') :
             selectedCategory === 'dataset' ? t('ow.nav.dataset') :
             selectedCategory === 'glossary' ? t('ow.nav.glossary') : ''}
          </span>
          <div className="flex items-center gap-1.5">
            <button
              onClick={() => setShowExportModal(true)}
              className={`flex items-center gap-1 px-2.5 py-1 rounded text-[10px] font-semibold ${styles.accentBg} text-white hover:opacity-90 transition-opacity`}
            >
              <svg xmlns="http://www.w3.org/2000/svg" width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
              {t('ow.btn.exportOntology')}
            </button>
          </div>
        </div>

        <div className="flex-1 overflow-auto">
        {selectedCategory === 'overview' && (
          <OverviewView
            objectTypes={objectTypes}
            linkTypes={linkTypes}
            actionTypes={actionTypes}
            interfaces={interfaces}
            sharedProperties={sharedProperties}
            datasets={datasets}
            domains={domains}
            selectedDomainFilter={selectedDomainId}
            onSelectDomainFilter={setSelectedDomainId}
            onUpdateDomains={updateDomains}
            onUpdateObjectTypes={updateObjectTypes}
            onSelectNode={(nodeId: string) => {
              setSelectedCategory('object');
              setSelectedId(nodeId);
            }}
            onSelectEdge={(edgeId: string) => {
              setSelectedCategory('link');
              setSelectedId(edgeId);
            }}
            onQuickNavigate={(category: ViewCategory, id: string) => {
              setSelectedCategory(category);
              setSelectedId(id);
            }}
            onViewModeChange={() => {}}
          />
        )}

        {selectedCategory === 'explorer' && (
          <ObjectExplorerView
            objectTypes={objectTypes}
            linkTypes={linkTypes}
            actionTypes={actionTypes}
            datasets={datasets}
            onUpdateDatasets={setDatasets}
            showToast={showToast}
            initialActiveObjectTypeId={explorerActiveObjectTypeId}
            onActiveObjectTypeIdChange={setExplorerActiveObjectTypeId}
          />
        )}

        {selectedCategory === 'object' && selectedId && (() => {
          const ot = objectTypes.find(o => o.id === selectedId);
          if (!ot) return <div className={`p-6 ${styles.muted} text-xs`}>{t('ow.empty.objectTypeNotFound')}</div>;
          return (
            <ObjectTypeDetail
              objectType={ot}
              datasets={datasets}
              linkTypes={linkTypes}
              actionTypes={actionTypes}
              sharedProperties={sharedProperties}
              interfaces={interfaces}
              domains={domains}
              onUpdate={(updated: ObjectType) => {
                updateObjectTypes(objectTypes.map(o => o.id === selectedId ? updated : o));
              }}
              onDelete={(id: string) => handleDeleteElement('object', id)}
              onNavigateToLink={(linkId: string) => {
                setSelectedCategory('link');
                setSelectedId(linkId);
              }}
              onNavigateToAction={(actionId: string) => {
                setSelectedCategory('action');
                setSelectedId(actionId);
              }}
              onExploreData={(objId: string) => {
                setExplorerActiveObjectTypeId(objId);
                setSelectedCategory('explorer');
                setSelectedId(null);
              }}
            />
          );
        })()}

        {selectedCategory === 'link' && selectedId && (() => {
          const lt = linkTypes.find(l => l.id === selectedId);
          if (!lt) return <div className={`p-6 ${styles.muted} text-xs`}>{t('ow.empty.linkTypeNotFound')}</div>;
          return (
            <LinkTypeDetail
              linkType={lt}
              objectTypes={objectTypes}
              datasets={datasets}
              onUpdate={(updated: LinkType) => {
                updateLinkTypes(linkTypes.map(l => l.id === selectedId ? updated : l));
              }}
              onDelete={(id: string) => handleDeleteElement('link', id)}
              onNavigateToObject={(objId: string) => {
                setSelectedCategory('object');
                setSelectedId(objId);
              }}
            />
          );
        })()}

        {selectedCategory === 'action' && selectedId && (() => {
          const at = actionTypes.find(a => a.id === selectedId);
          if (!at) return <div className={`p-6 ${styles.muted} text-xs`}>{t('ow.empty.actionTypeNotFound')}</div>;
          return (
            <ActionTypeDetail
              actionType={at}
              objectTypes={objectTypes}
              onUpdate={(updated: ActionType) => {
                updateActionTypes(actionTypes.map(a => a.id === selectedId ? updated : a));
              }}
              onDelete={(id: string) => handleDeleteElement('action', id)}
              onNavigateToObject={(objId: string) => {
                setSelectedCategory('object');
                setSelectedId(objId);
              }}
            />
          );
        })()}

        {selectedCategory === 'function' && selectedId && (() => {
          const fn = functionTypes.find(f => f.id === selectedId);
          if (!fn) return <div className={`p-6 ${styles.muted} text-xs`}>{t('ow.empty.functionNotFound')}</div>;
          return (
            <FunctionTypeDetail
              func={fn}
              objectTypes={objectTypes}
              onUpdate={(updated: FunctionType) => {
                updateFunctionTypes(functionTypes.map(f => f.id === selectedId ? updated : f));
              }}
              onDelete={(id: string) => handleDeleteElement('function', id)}
            />
          );
        })()}

        {selectedCategory === 'interface' && selectedId && (() => {
          const it = interfaces.find(i => i.id === selectedId);
          if (!it) return <div className={`p-6 ${styles.muted} text-xs`}>{t('ow.empty.interfaceNotFound')}</div>;
          return (
            <InterfaceView
              intf={it}
              objectTypes={objectTypes}
              onDelete={(id: string) => handleDeleteElement('interface', id)}
              onNavigateToObject={(objId: string) => {
                setSelectedCategory('object');
                setSelectedId(objId);
              }}
            />
          );
        })()}

        {selectedCategory === 'shared_property' && selectedId && (() => {
          const sp = sharedProperties.find(s => s.id === selectedId);
          if (!sp) return <div className={`p-6 ${styles.muted} text-xs`}>{t('ow.empty.sharedPropertyNotFound')}</div>;
          return (
            <SharedPropertyView
              sp={sp}
              objectTypes={objectTypes}
              onDelete={(id: string) => handleDeleteElement('shared_property', id)}
              onNavigateToObject={(objId: string) => {
                setSelectedCategory('object');
                setSelectedId(objId);
              }}
            />
          );
        })()}

        {selectedCategory === 'dataset' && selectedId && (() => {
          const ds = datasets.find(d => d.id === selectedId);
          if (!ds) return <div className={`p-6 ${styles.muted} text-xs`}>{t('ow.empty.datasetNotFound')}</div>;
          return (
            <DatasetView
              dataset={ds}
              objectTypes={objectTypes}
              onNavigateToObject={(objId: string) => {
                setSelectedCategory('object');
                setSelectedId(objId);
              }}
            />
          );
        })()}

        {/* 术语库 */}
        {selectedCategory === 'glossary' && (
          <div className="p-4">
            <GlossaryTab />
          </div>
        )}
        </div>

        {/* Proposal Panel — collapsible bottom drawer */}
        {selectedCategory === 'object' && (
          <ProposalPanel objectTypes={objectTypes} />
        )}
      </main>

      {/* Export Modal */}
      {showExportModal && (
        <div className="fixed inset-0 bg-black/40 z-50 flex items-center justify-center" onClick={() => setShowExportModal(false)}>
          <div className={`bg-white rounded-xl shadow-2xl border ${styles.cardBorder} p-5 w-96`} onClick={e => e.stopPropagation()}>
            <h3 className="text-sm font-bold mb-3">{t('ow.section.exportPanel')}</h3>
            <div className="space-y-3">
              <div>
                <label className={`block text-[10px] font-semibold mb-1 ${styles.muted}`}>{t('ow.label.exportFormat')}</label>
                <div className="flex gap-2">
                  {(['JSON', 'CSV', 'DDL'] as const).map(f => (
                    <button key={f} onClick={() => setExportFormat(f)}
                      className={`px-3 py-1.5 rounded text-[10px] font-semibold transition-colors ${exportFormat === f ? 'bg-indigo-600 text-white' : `bg-slate-100 text-slate-600 hover:bg-slate-200`}`}>
                      {t(`ow.export.format.${f}`)}
                    </button>
                  ))}
                </div>
              </div>
              <div>
                <label className={`block text-[10px] font-semibold mb-1 ${styles.muted}`}>{t('ow.label.exportScope')}</label>
                <div className="flex gap-2">
                  {(['FULL', 'ENTITIES', 'RELATIONSHIPS'] as const).map(s => (
                    <button key={s} onClick={() => setExportScope(s)}
                      className={`px-3 py-1.5 rounded text-[10px] font-semibold transition-colors ${exportScope === s ? 'bg-indigo-600 text-white' : `bg-slate-100 text-slate-600 hover:bg-slate-200`}`}>
                      {t(`ow.export.scope.${s}`)}
                    </button>
                  ))}
                </div>
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <button onClick={() => setShowExportModal(false)} className="px-3 py-1.5 rounded text-[10px] font-semibold bg-slate-100 text-slate-600 hover:bg-slate-200">Cancel</button>
                <button onClick={handleExportOntology} disabled={exporting}
                  className={`px-3 py-1.5 rounded text-[10px] font-semibold bg-indigo-600 text-white hover:bg-indigo-700 disabled:opacity-50`}>
                  {exporting ? '...' : t('ow.btn.exportOntology')}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Toast Notification */}
      {toast && (
        <div className={`fixed bottom-6 right-6 flex items-center gap-2 px-4 py-3 rounded-lg shadow-xl text-xs font-semibold text-white ${styles.accentBg} border ${styles.accentBorder} z-50 animate-bounce`}>
          <span className={toast.type === 'success' ? 'text-emerald-400' : toast.type === 'error' ? 'text-red-400' : 'text-blue-400'}>
            {toast.type === 'success' ? '✓' : toast.type === 'error' ? '✗' : 'ℹ'}
          </span>
          <span>{toast.message}</span>
        </div>
      )}
    </div>
  );
}

// Standalone export for routing
export function OntologyWorkbenchLayoutStandalone() {
  return <OntologyWorkbenchLayout />;
}
