/**
 * Business Workbench Layout — 业务工作台主框架
 *
 * 从 ceos_new App.tsx 移植，整合本体建模(Sidebar + 各类型视图) + 安全中心 + 数据浏览器。
 * Tab 切换: ontology(本体建模) | security(安全中心) | explorer(数据浏览器)
 */
import React, { useState, useEffect, useCallback } from 'react';
import { useLanguage } from '../../components/LanguageContext';
import { showToastGlobal } from '../../components/common/Toast';
import { apiFetch } from '../../api';
import { CopilotPanel } from '../../components/CopilotPanel';

// Types
import {
  ObjectType, LinkType, ActionType, InterfaceType, SharedProperty,
  Dataset, FunctionType, OntologyDomain, PropertyType,
  ActionParamDataType, ActionRuleType, ActionParameter, ActionRule, ActionValidationRule,
  FunctionParameter,
} from '../../types/ontology';

// Seed data (fallback when API unavailable)
import {
  mockObjectTypes, mockLinkTypes, mockActionTypes, mockInterfaces,
  mockSharedProperties, mockDatasets, mockFunctionTypes, mockDomains,
} from './seedData';

// View components
import Sidebar from './Sidebar';
import OverviewView from './OverviewView';
import ObjectTypeView from './ObjectTypeView';
import LinkTypeView from './LinkTypeView';
import ActionTypeView from './ActionTypeView';
import FunctionTypeView from './FunctionTypeView';
import { InterfaceView, SharedPropertyView, DatasetView } from './OtherViews';
import BusinessObjectExplorer from './BusinessObjectExplorer';

type ViewMode = 'ontology' | 'explorer';
type SelectedCategory = 'overview' | 'explorer' | 'object' | 'link' | 'action' | 'interface' | 'shared_property' | 'dataset' | 'function';

interface BusinessWorkbenchLayoutProps {
  showToast?: (type: 'success' | 'info' | 'error', message: string) => void;
  activeTab?: ViewMode;
  onActiveTabChange?: (tab: ViewMode) => void;
}

export default function BusinessWorkbenchLayout({
  showToast: propShowToast,
  activeTab: propActiveTab,
  onActiveTabChange,
}: BusinessWorkbenchLayoutProps = {}) {
  const { t } = useLanguage();

  // --- View Mode ---
  const [viewMode, setViewMode] = useState<ViewMode>(propActiveTab || 'ontology');
  useEffect(() => {
    if (propActiveTab && propActiveTab !== viewMode) setViewMode(propActiveTab);
  }, [propActiveTab]);

  // --- Ontology State ---
  const [objectTypes, setObjectTypes] = useState<ObjectType[]>([]);
  const [linkTypes, setLinkTypes] = useState<LinkType[]>([]);
  const [actionTypes, setActionTypes] = useState<ActionType[]>([]);
  const [interfaces, setInterfaces] = useState<InterfaceType[]>([]);
  const [sharedProperties, setSharedProperties] = useState<SharedProperty[]>([]);
  const [datasets, setDatasets] = useState<Dataset[]>([]);
  const [functionTypes, setFunctionTypes] = useState<FunctionType[]>([]);
  const [domains, setDomains] = useState<OntologyDomain[]>([]);
  const [selectedDomainId, setSelectedDomainId] = useState<string | null>(null);

  // --- Active Selections ---
  const [selectedCategory, setSelectedCategory] = useState<SelectedCategory>('overview');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [explorerActiveObjectTypeId, setExplorerActiveObjectTypeId] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [showCopilot, setShowCopilot] = useState(false);

  // --- Security State ---
  const showToast = useCallback((type: 'success' | 'info' | 'error', message: string) => {
    if (propShowToast) {
      propShowToast(type, message);
    } else {
      showToastGlobal(type, message);
    }
  }, [propShowToast]);

  // --- Load ontology data from API on mount ---
  useEffect(() => {
    loadOntologyData();
  }, []);

  const loadOntologyData = async () => {
    try {
      // Try loading from backend API
      const [objectsResp, linksResp, actionsResp, domainsResp] = await Promise.allSettled([
        apiFetch('/api/v1/ecos/ontologies') as Promise<any>,
        apiFetch('/api/v1/ecos/relationships') as Promise<any>,
        apiFetch('/api/v1/ecos/actions') as Promise<any>,
        apiFetch('/api/v1/ecos/domains') as Promise<any>,
      ]);

      // Use seed data as fallback, merge API data if available
      let loadedObjects = mockObjectTypes;
      let loadedLinks = mockLinkTypes;
      let loadedActions = mockActionTypes;
      let loadedDomains = mockDomains;

      if (objectsResp.status === 'fulfilled' && objectsResp.value?.data) {
        const apiData = objectsResp.value.data;
        if (Array.isArray(apiData) && apiData.length > 0) {
          loadedObjects = apiData as ObjectType[];
        }
      }
      if (linksResp.status === 'fulfilled' && linksResp.value?.data) {
        const apiData = linksResp.value.data;
        if (Array.isArray(apiData) && apiData.length > 0) {
          loadedLinks = apiData as LinkType[];
        }
      }
      if (actionsResp.status === 'fulfilled' && actionsResp.value?.data) {
        const apiData = actionsResp.value.data;
        if (Array.isArray(apiData) && apiData.length > 0) {
          loadedActions = apiData as ActionType[];
        }
      }
      if (domainsResp.status === 'fulfilled' && domainsResp.value?.data) {
        const apiData = domainsResp.value.data;
        if (Array.isArray(apiData) && apiData.length > 0) {
          loadedDomains = apiData as OntologyDomain[];
        }
      }

      setObjectTypes(loadedObjects);
      setLinkTypes(loadedLinks);
      setActionTypes(loadedActions);
      setInterfaces(mockInterfaces);
      setSharedProperties(mockSharedProperties);
      setDatasets(mockDatasets);
      setFunctionTypes(mockFunctionTypes);
      setDomains(loadedDomains);
    } catch (err) {
      console.error('Failed to load ontology data, using seed data:', err);
      setObjectTypes(mockObjectTypes);
      setLinkTypes(mockLinkTypes);
      setActionTypes(mockActionTypes);
      setInterfaces(mockInterfaces);
      setSharedProperties(mockSharedProperties);
      setDatasets(mockDatasets);
      setFunctionTypes(mockFunctionTypes);
      setDomains(mockDomains);
    }
  };

  // --- Update helpers ---
  const updateObjectTypes = useCallback((updated: ObjectType[]) => {
    setObjectTypes(updated);
    localStorage.setItem('ecos_cached_objects', JSON.stringify(updated));
  }, []);

  const updateLinkTypes = useCallback((updated: LinkType[]) => {
    setLinkTypes(updated);
  }, []);

  const updateActionTypes = useCallback((updated: ActionType[]) => {
    setActionTypes(updated);
  }, []);

  const updateFunctionTypes = useCallback((updated: FunctionType[]) => {
    setFunctionTypes(updated);
  }, []);

  const updateDomains = useCallback((updated: OntologyDomain[]) => {
    setDomains(updated);
  }, []);

  // --- Create new element ---
  const handleCreateNewElement = useCallback((type: 'object' | 'link' | 'action' | 'interface' | 'shared_property' | 'function') => {
    const defaultNum = Date.now().toString().slice(-4);

    if (type === 'object') {
      const newObjId = `custom_object_${defaultNum}`;
      const newObj: ObjectType = {
        id: newObjId,
        displayName: t('ow.biz.unnamedObject', { n: defaultNum }),
        apiName: `CustomObject${defaultNum}`,
        description: t('ow.biz.objectDescription'),
        icon: 'Box',
        color: 'border-slate-500 bg-slate-50 text-slate-700',
        primaryKey: 'id',
        titleProperty: 'name',
        status: 'DRAFT',
        properties: [
          { id: 'id', displayName: t('ow.biz.objectUuid'), apiName: 'id', dataType: 'string', isPrimaryKey: true, description: t('ow.biz.objectUuidDescription') },
          { id: 'name', displayName: t('ow.biz.objectName'), apiName: 'name', dataType: 'string', isPrimaryKey: false, description: t('ow.biz.objectNameDescription') }
        ],
        mapping: {
          datasetId: datasets[0]?.id || '',
          propertyMappings: { id: 'id', name: 'name' }
        }
      };
      updateObjectTypes([...objectTypes, newObj]);
      setSelectedCategory('object');
      setSelectedId(newObjId);
      showToast('info', t('ow.biz.createdObject', { name: newObj.displayName }));
    } else if (type === 'link') {
      if (objectTypes.length < 2) {
        showToast('error', t('ow.biz.linkNeedTwo'));
        return;
      }
      const newLinkId = `custom_link_${defaultNum}`;
      const newLink: LinkType = {
        id: newLinkId,
        displayName: t('ow.biz.newLink', { n: defaultNum }),
        apiName: `customLink${defaultNum}`,
        description: t('ow.biz.newLinkDescription'),
        sourceObjectType: objectTypes[0].id,
        targetObjectType: objectTypes[1].id,
        cardinality: '1:N',
        mapping: {
          type: 'foreign_key',
          foreignKeyMapping: {
            sourceKey: objectTypes[0].primaryKey,
            targetKey: objectTypes[1].primaryKey
          }
        }
      };
      updateLinkTypes([...linkTypes, newLink]);
      setSelectedCategory('link');
      setSelectedId(newLinkId);
      showToast('info', t('ow.biz.createdLink', { name: newLink.displayName }));
    } else if (type === 'action') {
      const newActionId = `custom_action_${defaultNum}`;
      const newAction: ActionType = {
        id: newActionId,
        displayName: t('ow.biz.newAction', { n: defaultNum }),
        apiName: `customAction${defaultNum}`,
        description: t('ow.biz.newActionDescription'),
        parameters: [],
        rules: [],
        validationRules: []
      };
      updateActionTypes([...actionTypes, newAction]);
      setSelectedCategory('action');
      setSelectedId(newActionId);
      showToast('info', t('ow.biz.createdAction', { name: newAction.displayName }));
    } else if (type === 'interface') {
      const newIntfId = `custom_interface_${defaultNum}`;
      const newIntf: InterfaceType = {
        id: newIntfId,
        displayName: t('ow.biz.newInterface', { n: defaultNum }),
        apiName: `CustomInterface${defaultNum}`,
        description: t('ow.biz.newInterfaceDescription'),
        properties: [
          { id: 'uuid', displayName: t('ow.biz.objectUuid'), apiName: 'uuid', dataType: 'string', isRequired: true, description: t('ow.biz.objectUuidDescription') }
        ]
      };
      setInterfaces([...interfaces, newIntf]);
      setSelectedCategory('interface');
      setSelectedId(newIntfId);
      showToast('info', t('ow.biz.createdInterface', { name: newIntf.displayName }));
    } else if (type === 'shared_property') {
      const newSpId = `custom_sp_${defaultNum}`;
      const newSp: SharedProperty = {
        id: newSpId,
        displayName: t('ow.biz.newSharedProperty', { n: defaultNum }),
        apiName: `customSharedProp${defaultNum}`,
        dataType: 'string',
        description: t('ow.biz.newSharedPropertyDescription')
      };
      setSharedProperties([...sharedProperties, newSp]);
      setSelectedCategory('shared_property');
      setSelectedId(newSpId);
      showToast('info', t('ow.biz.createdSharedProperty', { name: newSp.displayName }));
    } else if (type === 'function') {
      const newFuncId = `custom_function_${defaultNum}`;
      const newFunc: FunctionType = {
        id: newFuncId,
        displayName: t('ow.biz.newFunction', { n: defaultNum }),
        apiName: `customFunction${defaultNum}`,
        description: t('ow.biz.newFunctionDescription'),
        returnType: 'string',
        parameters: [],
        code: `import { Function } from "@foundry/functions-api";\n\nexport class CustomFunctionClass_${defaultNum} {\n    @Function()\n    public async customFunction${defaultNum}(): Promise<string> {\n        return "Hello World";\n    }\n}`,
      };
      updateFunctionTypes([...functionTypes, newFunc]);
      setSelectedCategory('function');
      setSelectedId(newFuncId);
      showToast('info', t('ow.biz.createdFunction', { name: newFunc.displayName }));
    }
  }, [objectTypes, linkTypes, actionTypes, interfaces, sharedProperties, functionTypes, datasets, updateObjectTypes, updateLinkTypes, updateActionTypes, updateFunctionTypes, showToast, t]);

  // --- Delete element ---
  const handleDeleteElement = useCallback((type: string, id: string) => {
    if (type === 'object') {
      updateObjectTypes(objectTypes.filter(o => o.id !== id));
    } else if (type === 'link') {
      updateLinkTypes(linkTypes.filter(l => l.id !== id));
    } else if (type === 'action') {
      updateActionTypes(actionTypes.filter(a => a.id !== id));
    } else if (type === 'interface') {
      setInterfaces(interfaces.filter(i => i.id !== id));
    } else if (type === 'shared_property') {
      setSharedProperties(sharedProperties.filter(sp => sp.id !== id));
    } else if (type === 'function') {
      updateFunctionTypes(functionTypes.filter(f => f.id !== id));
    }
    setSelectedCategory('overview');
    setSelectedId(null);
    showToast('success', t('ow.biz.deleted'));
  }, [objectTypes, linkTypes, actionTypes, interfaces, sharedProperties, functionTypes, updateObjectTypes, updateLinkTypes, updateActionTypes, updateFunctionTypes, showToast, t]);

  // --- Filtered lists for Sidebar ---
  const filteredObjects = objectTypes.filter(o => o.displayName.includes(searchQuery));
  const filteredLinks = linkTypes.filter(l => l.displayName.includes(searchQuery));
  const filteredActions = actionTypes.filter(a => a.displayName.includes(searchQuery));
  const filteredFunctions = functionTypes.filter(f => f.displayName.includes(searchQuery));

  // --- Tab bar ---
  const tabs: { id: ViewMode; label: string; icon: string }[] = [
    { id: 'ontology', label: t('ow.biz.tabOntology'), icon: 'Boxes' },
    { id: 'explorer', label: t('ow.biz.tabExplorer'), icon: 'Compass' },
  ];

  return (
    <div className="h-full flex flex-col bg-slate-900 text-slate-100 font-sans relative">
      {/* Tab Bar */}
      <div className="flex items-center gap-1 px-4 py-2 bg-slate-950 border-b border-slate-800">
        {tabs.map(tab => (
          <button
            key={tab.id}
            onClick={() => {
              setViewMode(tab.id);
              onActiveTabChange?.(tab.id);
            }}
            className={`px-4 py-1.5 rounded-md text-xs font-medium transition-colors ${
              viewMode === tab.id
                ? 'bg-slate-800 text-white'
                : 'text-slate-600 hover:bg-slate-100'
            }`}
          >
            {tab.label}
          </button>
        ))}
        <div className="flex-1" />
        <input
          type="text"
          placeholder={t('ow.biz.searchPlaceholder')}
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="px-3 py-1 text-xs border border-slate-200 rounded-md w-48 focus:outline-none focus:border-slate-400"
        />
        <button
          onClick={() => setShowCopilot(!showCopilot)}
          className={`flex items-center gap-1.5 px-2.5 py-1 rounded border transition-colors cursor-pointer text-xs ${
            showCopilot
              ? 'bg-blue-600 text-white border-blue-500'
              : 'bg-slate-800 hover:bg-slate-700 text-slate-200 border-slate-700'
          }`}
        >
          {t('ow.biz.copilot')}
        </button>
      </div>

      {/* Content */}
      <div className="flex-1 flex overflow-hidden">
        {viewMode === 'explorer' ? (
          <div className="flex-1 flex overflow-hidden">
            <BusinessObjectExplorer
              objectTypes={objectTypes}
              linkTypes={linkTypes}
              actionTypes={actionTypes}
              datasets={datasets}
              onUpdateDatasets={setDatasets}
              showToast={showToast}
              initialActiveObjectTypeId={explorerActiveObjectTypeId}
              onActiveObjectTypeIdChange={setExplorerActiveObjectTypeId}
            />
          </div>
        ) : (
          <>
            {/* Sidebar */}
            <Sidebar
              objectTypes={filteredObjects}
              allObjectTypes={objectTypes}
              linkTypes={filteredLinks}
              actionTypes={filteredActions}
              interfaces={interfaces.filter(i => i.displayName.includes(searchQuery))}
              sharedProperties={sharedProperties.filter(sp => sp.displayName.includes(searchQuery))}
              datasets={datasets.filter(ds => ds.name.includes(searchQuery))}
              functionTypes={filteredFunctions}
              domains={domains}
              selectedDomainId={selectedDomainId}
              onSelectDomainId={setSelectedDomainId}
              onUpdateDomains={updateDomains}
              onUpdateObjectTypes={updateObjectTypes}
              selectedCategory={selectedCategory}
              selectedId={selectedId}
              onSelectCategory={(category, id) => {
                setSelectedCategory(category);
                setSelectedId(id);
              }}
              onCreateNew={handleCreateNewElement}
            />

            {/* Central Editor */}
            <main className="flex-1 overflow-hidden relative">
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
                  onSelectNode={(nodeId) => {
                    setSelectedCategory('object');
                    setSelectedId(nodeId);
                  }}
                  onSelectEdge={(edgeId) => {
                    setSelectedCategory('link');
                    setSelectedId(edgeId);
                  }}
                  onQuickNavigate={(category, id) => {
                    setSelectedCategory(category as SelectedCategory);
                    setSelectedId(id);
                  }}
                  onViewModeChange={(mode) => setViewMode(mode as ViewMode)}
                />
              )}

              {selectedCategory === 'explorer' && (
                <BusinessObjectExplorer
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
                if (!ot) return <div className="p-6 text-slate-400 text-xs">{t('ow.biz.notFoundObject')}</div>;
                return (
                  <ObjectTypeView
                    objectType={ot}
                    datasets={datasets}
                    linkTypes={linkTypes}
                    actionTypes={actionTypes}
                    sharedProperties={sharedProperties}
                    interfaces={interfaces}
                    domains={domains}
                    onUpdate={(updated) => {
                      updateObjectTypes(objectTypes.map(o => o.id === selectedId ? updated : o));
                    }}
                    onDelete={(id) => handleDeleteElement('object', id)}
                    onNavigateToLink={(linkId) => {
                      setSelectedCategory('link');
                      setSelectedId(linkId);
                    }}
                    onNavigateToAction={(actionId) => {
                      setSelectedCategory('action');
                      setSelectedId(actionId);
                    }}
                    onExploreData={(objId) => {
                      setExplorerActiveObjectTypeId(objId);
                      setSelectedCategory('explorer');
                      setSelectedId(null);
                    }}
                  />
                );
              })()}

              {selectedCategory === 'link' && selectedId && (() => {
                const lt = linkTypes.find(l => l.id === selectedId);
                if (!lt) return <div className="p-6 text-slate-400 text-xs">{t('ow.biz.notFoundLink')}</div>;
                return (
                  <LinkTypeView
                    linkType={lt}
                    objectTypes={objectTypes}
                    datasets={datasets}
                    onUpdate={(updated) => {
                      updateLinkTypes(linkTypes.map(l => l.id === selectedId ? updated : l));
                    }}
                    onDelete={(id) => handleDeleteElement('link', id)}
                    onNavigateToObject={(objId) => {
                      setSelectedCategory('object');
                      setSelectedId(objId);
                    }}
                  />
                );
              })()}

              {selectedCategory === 'action' && selectedId && (() => {
                const at = actionTypes.find(a => a.id === selectedId);
                if (!at) return <div className="p-6 text-slate-400 text-xs">{t('ow.biz.notFoundAction')}</div>;
                return (
                  <ActionTypeView
                    actionType={at}
                    objectTypes={objectTypes}
                    onUpdate={(updated) => {
                      updateActionTypes(actionTypes.map(a => a.id === selectedId ? updated : a));
                    }}
                    onDelete={(id) => handleDeleteElement('action', id)}
                    onNavigateToObject={(objId) => {
                      setSelectedCategory('object');
                      setSelectedId(objId);
                    }}
                  />
                );
              })()}

              {selectedCategory === 'function' && selectedId && (() => {
                const fn = functionTypes.find(f => f.id === selectedId);
                if (!fn) return <div className="p-6 text-slate-400 text-xs">{t('ow.biz.notFoundFunction')}</div>;
                return (
                  <FunctionTypeView
                    func={fn}
                    objectTypes={objectTypes}
                    onUpdate={(updated) => {
                      updateFunctionTypes(functionTypes.map(f => f.id === selectedId ? updated : f));
                    }}
                    onDelete={(id) => handleDeleteElement('function', id)}
                  />
                );
              })()}

              {selectedCategory === 'interface' && selectedId && (() => {
                const it = interfaces.find(i => i.id === selectedId);
                if (!it) return <div className="p-6 text-slate-400 text-xs">{t('ow.biz.notFoundInterface')}</div>;
                return (
                  <InterfaceView
                    intf={it}
                    objectTypes={objectTypes}
                    onDelete={(id) => handleDeleteElement('interface', id)}
                    onNavigateToObject={(objId) => {
                      setSelectedCategory('object');
                      setSelectedId(objId);
                    }}
                  />
                );
              })()}

              {selectedCategory === 'shared_property' && selectedId && (() => {
                const sp = sharedProperties.find(s => s.id === selectedId);
                if (!sp) return <div className="p-6 text-slate-400 text-xs">{t('ow.biz.notFoundSharedProperty')}</div>;
                return (
                  <SharedPropertyView
                    sp={sp}
                    objectTypes={objectTypes}
                    onDelete={(id) => handleDeleteElement('shared_property', id)}
                    onNavigateToObject={(objId) => {
                      setSelectedCategory('object');
                      setSelectedId(objId);
                    }}
                  />
                );
              })()}

              {selectedCategory === 'dataset' && selectedId && (() => {
                const ds = datasets.find(d => d.id === selectedId);
                if (!ds) return <div className="p-6 text-slate-400 text-xs">{t('ow.biz.notFoundDataset')}</div>;
                return (
                  <DatasetView
                    dataset={ds}
                    objectTypes={objectTypes}
                    onNavigateToObject={(objId) => {
                      setSelectedCategory('object');
                      setSelectedId(objId);
                    }}
                  />
                );
              })()}
            </main>
          </>
        )}
      </div>

      {showCopilot && (
        <div className="absolute top-12 right-0 bottom-0 w-80 border-l border-[var(--border)] bg-[var(--card)] shadow-2xl z-40 flex flex-col overflow-hidden">
          <CopilotPanel agentType="ontology" />
        </div>
      )}
    </div>
  );
}

// Standalone wrapper for full-page business workbench
// 仅透传子组件；子组件内部已有主题/语言 context 消费。
export function BusinessWorkbenchLayoutStandalone() {
  return <BusinessWorkbenchLayout />;
}
