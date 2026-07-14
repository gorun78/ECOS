/**
 * Business Workbench Layout — 业务工作台主框架
 *
 * 从 ceos_new App.tsx 移植，整合本体建模(Sidebar + 各类型视图) + 安全中心 + 数据浏览器。
 * Tab 切换: ontology(本体建模) | security(安全中心) | explorer(数据浏览器)
 */
import React, { useState, useEffect, useCallback } from 'react';
import { useLanguage } from '../../components/LanguageContext';
import { apiFetch } from '../../api';

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
import ObjectExplorerView from './ObjectExplorerView';
import SecurityCenterView, {
  SecurityOrg, ProjectDAC, SecurityMarking, PurposePBAC, RowColPolicy, SecurityAuditLog,
  mockSecurityOrgs, mockProjectDACs, mockSecurityMarkings, mockPurposes,
} from './SecurityCenterView';

type ViewMode = 'ontology' | 'security' | 'explorer';
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

  // --- Security State ---
  const [securityOrgs, setSecurityOrgs] = useState<SecurityOrg[]>(mockSecurityOrgs);
  const [securityProjects, setSecurityProjects] = useState<ProjectDAC[]>(mockProjectDACs);
  const [securityMarkings, setSecurityMarkings] = useState<SecurityMarking[]>(mockSecurityMarkings);
  const [securityPurposes, setSecurityPurposes] = useState<PurposePBAC[]>(mockPurposes);
  const [securityRowColPolicies, setSecurityRowColPolicies] = useState<RowColPolicy[]>([]);
  const [securityAuditLogs, setSecurityAuditLogs] = useState<SecurityAuditLog[]>([]);
  const [securitySimUser, setSecuritySimUser] = useState('analyst_li');
  const [securitySimDataset, setSecuritySimDataset] = useState('ds_flight_schedules');
  const [securitySimPurpose, setSecuritySimPurpose] = useState('operational_analytics');
  const [securitySimResult, setSecuritySimResult] = useState<{ verdict: 'GRANTED' | 'DENIED'; traces: string[] } | null>(null);
  const [securitySelectedRowColDs, setSecuritySelectedRowColDs] = useState('');

  // --- Toast ---
  const showToast = useCallback((type: 'success' | 'info' | 'error', message: string) => {
    if (propShowToast) {
      propShowToast(type, message);
    } else {
      console.log(`[${type.toUpperCase()}] ${message}`);
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
        displayName: `未命名对象_${defaultNum}`,
        apiName: `CustomObject${defaultNum}`,
        description: '新建自定义业务实体。请输入详细业务描述。',
        icon: 'Box',
        color: 'border-slate-500 bg-slate-50 text-slate-700',
        primaryKey: 'id',
        titleProperty: 'name',
        status: 'DRAFT',
        properties: [
          { id: 'id', displayName: '唯一ID', apiName: 'id', dataType: 'string', isPrimaryKey: true, description: '唯一主键标识。' },
          { id: 'name', displayName: '显示名称', apiName: 'name', dataType: 'string', isPrimaryKey: false, description: '实体的主展示信息。' }
        ],
        mapping: {
          datasetId: datasets[0]?.id || '',
          propertyMappings: { id: 'id', name: 'name' }
        }
      };
      updateObjectTypes([...objectTypes, newObj]);
      setSelectedCategory('object');
      setSelectedId(newObjId);
      showToast('info', `创建了对象类型: ${newObj.displayName}`);
    } else if (type === 'link') {
      if (objectTypes.length < 2) {
        showToast('error', '建立关联至少需要 2 个对象类型！');
        return;
      }
      const newLinkId = `custom_link_${defaultNum}`;
      const newLink: LinkType = {
        id: newLinkId,
        displayName: `新关联链接_${defaultNum}`,
        apiName: `customLink${defaultNum}`,
        description: '新建实体间的多维逻辑关联关系。',
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
      showToast('info', `创建了链接关系: ${newLink.displayName}`);
    } else if (type === 'action') {
      const newActionId = `custom_action_${defaultNum}`;
      const newAction: ActionType = {
        id: newActionId,
        displayName: `新操作行为_${defaultNum}`,
        apiName: `customAction${defaultNum}`,
        description: '配置业务修改端点。通过传入参数，改变指定的本体对象。',
        parameters: [],
        rules: [],
        validationRules: []
      };
      updateActionTypes([...actionTypes, newAction]);
      setSelectedCategory('action');
      setSelectedId(newActionId);
      showToast('info', `创建了操作类型: ${newAction.displayName}`);
    } else if (type === 'interface') {
      const newIntfId = `custom_interface_${defaultNum}`;
      const newIntf: InterfaceType = {
        id: newIntfId,
        displayName: `新契约接口_${defaultNum}`,
        apiName: `CustomInterface${defaultNum}`,
        description: '声明一个公共行为或特征的抽象接口规范。',
        properties: [
          { id: 'uuid', displayName: '核心编码', apiName: 'uuid', dataType: 'string', isRequired: true, description: '唯一硬件/实体识别符。' }
        ]
      };
      setInterfaces([...interfaces, newIntf]);
      setSelectedCategory('interface');
      setSelectedId(newIntfId);
      showToast('info', `创建了接口类型: ${newIntf.displayName}`);
    } else if (type === 'shared_property') {
      const newSpId = `custom_sp_${defaultNum}`;
      const newSp: SharedProperty = {
        id: newSpId,
        displayName: `标准公共属性_${defaultNum}`,
        apiName: `customSharedProp${defaultNum}`,
        dataType: 'string',
        description: '标准化全局指标，可在各类业务对象中复用。'
      };
      setSharedProperties([...sharedProperties, newSp]);
      setSelectedCategory('shared_property');
      setSelectedId(newSpId);
      showToast('info', `创建了共享属性: ${newSp.displayName}`);
    } else if (type === 'function') {
      const newFuncId = `custom_function_${defaultNum}`;
      const newFunc: FunctionType = {
        id: newFuncId,
        displayName: `新逻辑函数_${defaultNum}`,
        apiName: `customFunction${defaultNum}`,
        description: '配置 TypeScript 逻辑函数。',
        returnType: 'string',
        parameters: [],
        code: `import { Function } from "@foundry/functions-api";\n\nexport class CustomFunctionClass_${defaultNum} {\n    @Function()\n    public async customFunction${defaultNum}(): Promise<string> {\n        return "Hello World";\n    }\n}`,
      };
      updateFunctionTypes([...functionTypes, newFunc]);
      setSelectedCategory('function');
      setSelectedId(newFuncId);
      showToast('info', `创建了逻辑函数: ${newFunc.displayName}`);
    }
  }, [objectTypes, linkTypes, actionTypes, interfaces, sharedProperties, functionTypes, datasets, updateObjectTypes, updateLinkTypes, updateActionTypes, updateFunctionTypes, showToast]);

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
    showToast('success', '已删除元素');
  }, [objectTypes, linkTypes, actionTypes, interfaces, sharedProperties, functionTypes, updateObjectTypes, updateLinkTypes, updateActionTypes, updateFunctionTypes, showToast]);

  // --- Filtered lists for Sidebar ---
  const filteredObjects = objectTypes.filter(o => o.displayName.includes(searchQuery));
  const filteredLinks = linkTypes.filter(l => l.displayName.includes(searchQuery));
  const filteredActions = actionTypes.filter(a => a.displayName.includes(searchQuery));
  const filteredFunctions = functionTypes.filter(f => f.displayName.includes(searchQuery));

  // --- Tab bar ---
  const tabs: { id: ViewMode; label: string; icon: string }[] = [
    { id: 'ontology', label: '本体建模', icon: 'Boxes' },
    { id: 'security', label: '安全中心', icon: 'ShieldCheck' },
    { id: 'explorer', label: '数据浏览器', icon: 'Compass' },
  ];

  return (
    <div className="h-full flex flex-col bg-slate-900 text-slate-100 font-sans">
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
          placeholder="搜索..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="px-3 py-1 text-xs border border-slate-200 rounded-md w-48 focus:outline-none focus:border-slate-400"
        />
      </div>

      {/* Content */}
      <div className="flex-1 flex overflow-hidden">
        {viewMode === 'security' ? (
          <SecurityCenterView
            showToast={showToast}
            orgs={securityOrgs}
            setOrgs={setSecurityOrgs}
            projects={securityProjects}
            setProjects={setSecurityProjects}
            markings={securityMarkings}
            setMarkings={setSecurityMarkings}
            purposes={securityPurposes}
            setPurposes={setSecurityPurposes}
            rowColPolicies={securityRowColPolicies}
            setRowColPolicies={setSecurityRowColPolicies}
            auditLogs={securityAuditLogs}
            setAuditLogs={setSecurityAuditLogs}
            simUser={securitySimUser}
            setSimUser={setSecuritySimUser}
            simDataset={securitySimDataset}
            setSimDataset={setSecuritySimDataset}
            simPurpose={securitySimPurpose}
            setSimPurpose={setSecuritySimPurpose}
            simResult={securitySimResult}
            setSimResult={setSecuritySimResult}
            selectedRowColDs={securitySelectedRowColDs}
            setSelectedRowColDs={setSecuritySelectedRowColDs}
          />
        ) : viewMode === 'explorer' ? (
          <div className="flex-1 flex overflow-hidden">
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
                if (!ot) return <div className="p-6 text-slate-400 text-xs">未找到该对象类型</div>;
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
                if (!lt) return <div className="p-6 text-slate-400 text-xs">未找到该链接关系</div>;
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
                if (!at) return <div className="p-6 text-slate-400 text-xs">未找到该操作类型</div>;
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
                if (!fn) return <div className="p-6 text-slate-400 text-xs">未找到该逻辑函数</div>;
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
                if (!it) return <div className="p-6 text-slate-400 text-xs">未找到该接口类型</div>;
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
                if (!sp) return <div className="p-6 text-slate-400 text-xs">未找到该共享属性</div>;
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
                if (!ds) return <div className="p-6 text-slate-400 text-xs">未找到该数据集</div>;
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
    </div>
  );
}

// Standalone wrapper for full-page business workbench
export function BusinessWorkbenchLayoutStandalone() {
  return (
    <div className="h-screen flex flex-col bg-slate-900 text-slate-100 font-sans">
      <BusinessWorkbenchLayout />
    </div>
  );
}
