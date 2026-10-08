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
import { useTheme } from '../../components/ThemeContext';
import { BUSINESS_FUNCTIONS_API_PKG } from '../../data/functionsApiPkg';

// Types
import {
  ObjectType, LinkType, ActionType, InterfaceType, SharedProperty,
  Dataset, FunctionType, OntologyDomain, PropertyType,
  ActionParamDataType, ActionRuleType, ActionParameter, ActionRule, ActionValidationRule,
  FunctionParameter, ForeignKeyMapping,
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

// ================================================================
// W3-1: per-item 形状归一化 ingestion
// 后端 /ecs/* 返回摘要（id/name/code/…），与前端 UI 模型不同形。
// 逐行 sniff + 兜底缺省下游必需字段（空字符串/空数组），绝不直接 cast 导致
// 下游 .displayName / .properties.length / getDomainColorClasses(undefined) 抛 TypeError。
// 仅当保留率 < 20%（丢 80%）才整列回退 seed；否则用可保留的行。
// ================================================================
const OBJ_STATUS_SET = new Set(['DRAFT', 'ACTIVE', 'PUBLISHED', 'DEPRECATED']);

function normStr(r: any, ...keys: string[]): string {
  for (const k of keys) {
    const v = r?.[k];
    if (typeof v === 'string' && v.trim() !== '') return v;
  }
  return '';
}

function normalizeObjectItem(r: any): ObjectType | null {
  if (!r || typeof r !== 'object') return null;
  const id = normStr(r, 'id', 'code', 'name');
  if (!id) return null;
  const displayName = normStr(r, 'displayName', 'name', 'code') || id;
  const apiName = normStr(r, 'apiName', 'code', 'name') || id;
  const properties: PropertyType[] = Array.isArray(r.properties) ? r.properties : [];
  const primaryKey = properties.find((p: any) => p?.isPrimaryKey)?.id || normStr(r, 'primaryKey') || properties[0]?.id || '';
  const status = (typeof r.status === 'string' && OBJ_STATUS_SET.has(r.status)) ? r.status : 'ACTIVE';
  return {
    id,
    displayName,
    apiName,
    description: normStr(r, 'description'),
    icon: r.icon || 'Circle',
    color: r.color || 'border-slate-500 bg-slate-50 text-slate-700',
    primaryKey,
    titleProperty: normStr(r, 'titleProperty') || primaryKey,
    properties,
    mapping: r.mapping && typeof r.mapping === 'object' ? r.mapping : { datasetId: '', propertyMappings: {} },
    status,
    interfaces: Array.isArray(r.interfaces) ? r.interfaces : undefined,
    domainId: normStr(r, 'domainId') || undefined,
  };
}

function normalizeLinkItem(r: any): LinkType | null {
  if (!r || typeof r !== 'object') return null;
  const id = normStr(r, 'id', 'code', 'name');
  if (!id) return null;
  // 后端 relationships 返 sourceEntityId/relationshipType；seed/UI 用 sourceObjectType/displays。
  const sourceObjectType = normStr(r, 'sourceObjectType', 'sourceEntityId', 'sourceEntity', 'sourceId');
  const targetObjectType = normStr(r, 'targetObjectType', 'targetEntityId', 'targetEntity', 'targetId');
  // sourceEntityId 正常应指向关系里的实体 id；若后端只给到对象类型 id 也能进图（find 不中则端点为 undefined，不崩）。
  if (!sourceObjectType && !targetObjectType && !normStr(r, 'relationshipType', 'type', 'kind')) return null;
  const relationshipType = normStr(r, 'relationshipType', 'kind', 'type');
  const displayName = normStr(r, 'displayName', 'name') || relationshipType || id;
  const apiName = normStr(r, 'apiName') || relationshipType || id;
  const CARDINALITIES = ['1:1', '1:N', 'N:1', 'M:N'];
  const cardinality: LinkType['cardinality'] = CARDINALITIES.includes(r.cardinality) ? (r.cardinality as LinkType['cardinality']) : 'N:1';
  const fk: ForeignKeyMapping = {
    sourceKey: normStr(r, 'sourceKey', 'sourceKeyColumn'),
    targetKey: normStr(r, 'targetKey', 'targetKeyColumn'),
  };
  const mapping: LinkType['mapping'] = { type: 'foreign_key', foreignKeyMapping: fk };
  return { id, displayName, apiName, description: normStr(r, 'description'), sourceObjectType, targetObjectType, cardinality, mapping };
}

function normalizeActionItem(r: any): ActionType | null {
  if (!r || typeof r !== 'object') return null;
  const id = normStr(r, 'id', 'code', 'name');
  if (!id) return null;
  return {
    id,
    displayName: normStr(r, 'displayName', 'name', 'code') || id,
    apiName: normStr(r, 'apiName', 'code') || id,
    description: normStr(r, 'description'),
    parameters: Array.isArray(r.parameters) ? r.parameters : [],
    rules: Array.isArray(r.rules) ? r.rules : [],
    validationRules: Array.isArray(r.validationRules) ? r.validationRules : [],
    formLayout: r.formLayout && typeof r.formLayout === 'object' ? r.formLayout : undefined,
  };
}

function normalizeDomainItem(r: any): OntologyDomain | null {
  if (!r || typeof r !== 'object') return null;
  const id = normStr(r, 'id', 'code', 'name');
  if (!id) return null;
  return {
    id,
    displayName: normStr(r, 'displayName', 'name', 'code') || id,
    description: normStr(r, 'description'),
    color: normStr(r, 'color') || 'slate',
    code: r.code ? String(r.code) : undefined,
    status: r.status ? String(r.status) : undefined,
  };
}

// 对候选集逐项归一化：返回 { kept, dropped }。kept.length/total < 0.2 → 调用方回退 seed。
function ingest<Item>(raw: any, norm: (r: any) => Item | null): { kept: Item[]; dropped: number } {
  if (!Array.isArray(raw) || raw.length === 0) return { kept: [], dropped: 0 };
  const kept: Item[] = [];
  for (const r of raw) {
    const n = norm(r);
    if (n) kept.push(n);
  }
  return { kept, dropped: raw.length - kept.length };
}

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
  const { styles } = useTheme();

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
  const [dataSource, setDataSource] = useState<'api' | 'seed' | 'empty'>('seed');

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
    // W3-2：seed 属"回退种"非"初始态"，仅 dev 环境允许兜底；prod 后端不可用时呈空态而非假演示数据。
    // 提升到 try 外：catch 回退路径同样要读 isDev / emptyOntology（原写法在 try 内声明导致 catch 不可见）。
    const isDev = import.meta.env.DEV;
    const emptyOntology = () => ({
      objects: [] as ObjectType[],
      links: [] as LinkType[],
      actions: [] as ActionType[],
      domains: [] as OntologyDomain[],
      interfaces: [] as InterfaceType[],
      sharedProperties: [] as SharedProperty[],
      datasets: [] as Dataset[],
      functionTypes: [] as FunctionType[],
    });
    try {
      // Try loading from backend API
      const [objectsResp, linksResp, actionsResp, domainsResp] = await Promise.allSettled([
        apiFetch('/v1/ecos/ontologies') as Promise<any>,
        apiFetch('/v1/ecos/relationships') as Promise<any>,
        apiFetch('/v1/ecos/actions') as Promise<any>,
        apiFetch('/v1/ecos/domains') as Promise<any>,
      ]);

      const seedOrEmpty = <T,>(seed: T[]): T[] => (isDev ? seed : []);
      const seedObjects = seedOrEmpty(mockObjectTypes);
      const seedLinks = seedOrEmpty(mockLinkTypes);
      const seedActions = seedOrEmpty(mockActionTypes);
      const seedDomains = seedOrEmpty(mockDomains);

      let loadedObjects = seedObjects;
      let loadedLinks = seedLinks;
      let loadedActions = seedActions;
      let loadedDomains = seedDomains;
      let adoptedAny = false;

      // 后端 /ecs/* 返回 ontology 摘要（id/name/code/version/status），
      // 与前端 ObjectType/LinkType/ActionType UI 模型不完全同形（缺 properties/apiName/icon 等）。
      // W3-1：逐项归一化 + 兜底缺省下游必需字段，不再「整列 every-门控 → 任一不合规就全回退 seed」。
      // 仅当保留率 < 20%（丢 80%）时该列才回退 seed-or-empty，避免直接 cast 让下游
      // .properties.length / .displayName / getDomainColorClasses(undefined) 抛 TypeError 崩整页。200 端点仍算成功。
      // 仅当该端点 fulfilled 且带非空 data 数组时才尝试接管，否则整列回退 seed-or-empty。
      const dataOf = (res: PromiseSettledResult<any>): any[] =>
        res.status === 'fulfilled' && Array.isArray(res.value?.data) ? res.value.data : [];
      const adoptIfUsable = <T,>(res: PromiseSettledResult<any>, norm: (r: any) => T | null, fallback: T[]): T[] => {
        const raw = dataOf(res);
        const { kept, dropped } = ingest(raw, norm);
        if (raw.length > 0) console.info(`[BusinessWorkbench] ingestion: kept ${kept.length}/${raw.length} (dropped ${dropped})`);
        const total = raw.length;
        const adopted = total > 0 && kept.length / total >= 0.2;
        if (adopted) adoptedAny = true;
        return adopted ? kept : fallback;
      };

      loadedObjects = adoptIfUsable(objectsResp, normalizeObjectItem, seedObjects);
      loadedLinks = adoptIfUsable(linksResp, normalizeLinkItem, seedLinks);
      loadedActions = adoptIfUsable(actionsResp, normalizeActionItem, seedActions);
      loadedDomains = adoptIfUsable(domainsResp, normalizeDomainItem, seedDomains);

      setObjectTypes(loadedObjects);
      setLinkTypes(loadedLinks);
      setActionTypes(loadedActions);
      setInterfaces(seedOrEmpty(mockInterfaces));
      setSharedProperties(seedOrEmpty(mockSharedProperties));
      setDatasets(seedOrEmpty(mockDatasets));
      setFunctionTypes(seedOrEmpty(mockFunctionTypes));
      setDomains(loadedDomains);
      setDataSource(adoptedAny ? 'api' : (isDev ? 'seed' : 'empty'));
    } catch (err) {
      console.error('Failed to load ontology data:', err);
      const empty = isDev ? {
        objects: mockObjectTypes,
        links: mockLinkTypes,
        actions: mockActionTypes,
        domains: mockDomains,
        interfaces: mockInterfaces,
        sharedProperties: mockSharedProperties,
        datasets: mockDatasets,
        functionTypes: mockFunctionTypes,
      } : emptyOntology();
      setObjectTypes(empty.objects);
      setLinkTypes(empty.links);
      setActionTypes(empty.actions);
      setInterfaces(empty.interfaces);
      setSharedProperties(empty.sharedProperties);
      setDatasets(empty.datasets);
      setFunctionTypes(empty.functionTypes);
      setDomains(empty.domains);
      setDataSource(isDev ? 'seed' : 'empty');
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
        sourceObjectType: objectTypes[0]?.id ?? '',
        targetObjectType: objectTypes[1]?.id ?? '',
        cardinality: '1:N',
        mapping: {
          type: 'foreign_key',
          foreignKeyMapping: {
            sourceKey: objectTypes[0]?.primaryKey ?? '',
            targetKey: objectTypes[1]?.primaryKey ?? ''
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
        code: `import { Function } from "${BUSINESS_FUNCTIONS_API_PKG}";\n\nexport class CustomFunctionClass_${defaultNum} {\n    @Function()\n    public async customFunction${defaultNum}(): Promise<string> {\n        return "Hello World";\n    }\n}`,
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
  const filteredObjects = objectTypes.filter(o => (o.displayName ?? '').includes(searchQuery));
  const filteredLinks = linkTypes.filter(l => (l.displayName ?? '').includes(searchQuery));
  const filteredActions = actionTypes.filter(a => (a.displayName ?? '').includes(searchQuery));
  const filteredFunctions = functionTypes.filter(f => (f.displayName ?? '').includes(searchQuery));

  // --- Tab bar ---
  const tabs: { id: ViewMode; label: string; icon: string }[] = [
    { id: 'ontology', label: t('ow.biz.tabOntology'), icon: 'Boxes' },
    { id: 'explorer', label: t('ow.biz.tabExplorer'), icon: 'Compass' },
  ];

  return (
    <div className={`h-full flex flex-col ${styles.appBg} ${styles.appText} font-sans relative`}>
      {/* Tab Bar */}
      <div className={`flex items-center gap-1 px-4 py-2 ${styles.sidebarBg} border-b ${styles.sidebarBorder}`}>
        {tabs.map(tab => (
          <button
            key={tab.id}
            type="button"
            onClick={() => {
              setViewMode(tab.id);
              onActiveTabChange?.(tab.id);
            }}
            className={`px-4 py-1.5 rounded-md text-xs font-medium transition-colors ${
              viewMode === tab.id
                ? `${styles.sidebarActiveBg} ${styles.sidebarActiveText}`
                : `${styles.sidebarText} ${styles.sidebarHoverBg}`
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
          className={`px-3 py-1 text-xs border ${styles.inputBorder} rounded-md w-48 focus:outline-none focus:ring-1 ${styles.accentBorder}`}
        />
        <button
          type="button"
          onClick={() => setShowCopilot(!showCopilot)}
          className={`flex items-center gap-1.5 px-2.5 py-1 rounded border transition-colors cursor-pointer text-xs ${
            showCopilot
              ? `${styles.accentBg} ${styles.inputText} ${styles.accentBorder}`
              : `${styles.inputBg} hover:opacity-90 ${styles.cardTextMuted} ${styles.inputBorder}`
          }`}
        >
          {t('ow.biz.copilot')}
        </button>
      </div>

      {viewMode === 'ontology' && dataSource !== 'api' && (
        <div
          className="flex-0 px-4 py-1.5 text-[11px] min-w-0"
          style={{
            background: dataSource === 'empty' ? styles.dangerBg : styles.warningBg,
            color: dataSource === 'empty' ? styles.dangerText : styles.warningText,
          }}
          role="note"
        >
          <span className="break-words">{dataSource === 'empty' ? t('ow.biz.noOntology') : t('ow.biz.seedDemo')}</span>
        </div>
      )}

      {/* Content — 桌面 flex-row；移动端 flex-col（Sidebar 落顶部，内容落下方） */}
      <div className="flex-1 flex flex-col md:flex-row overflow-hidden">
        {viewMode === 'explorer' ? (
          <div className="flex-1 w-full min-w-0 flex overflow-hidden">
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
            {/* Sidebar — 桌面双栏左侧；移动端顶部 */}
            <div className="w-full md:w-56 md:h-full md:shrink-0 border-b md:border-b-0 md:border-r border-t-0 md:border-t-0" style={{ borderColor: 'var(--border, rgba(0,0,0,0.08))' }}>
              <Sidebar
                objectTypes={filteredObjects}
              allObjectTypes={objectTypes}
              linkTypes={filteredLinks}
              actionTypes={filteredActions}
              interfaces={interfaces.filter(i => (i.displayName ?? '').includes(searchQuery))}
              sharedProperties={sharedProperties.filter(sp => (sp.displayName ?? '').includes(searchQuery))}
              datasets={datasets.filter(ds => (ds.name ?? '').includes(searchQuery))}
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
            </div>

            {/* Central Editor — 移动端下方；桌面侧占满剩余宽度 */}
            <main className="w-full flex-1 min-w-0 overflow-hidden relative">
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
                if (!ot) return <div className={`p-6 ${styles.cardTextMuted} text-xs`}>{t('ow.biz.notFoundObject')}</div>;
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
                if (!lt) return <div className={`p-6 ${styles.cardTextMuted} text-xs`}>{t('ow.biz.notFoundLink')}</div>;
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
                if (!at) return <div className={`p-6 ${styles.cardTextMuted} text-xs`}>{t('ow.biz.notFoundAction')}</div>;
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
                if (!fn) return <div className={`p-6 ${styles.cardTextMuted} text-xs`}>{t('ow.biz.notFoundFunction')}</div>;
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
                if (!it) return <div className={`p-6 ${styles.cardTextMuted} text-xs`}>{t('ow.biz.notFoundInterface')}</div>;
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
                if (!sp) return <div className={`p-6 ${styles.cardTextMuted} text-xs`}>{t('ow.biz.notFoundSharedProperty')}</div>;
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
                if (!ds) return <div className={`p-6 ${styles.cardTextMuted} text-xs`}>{t('ow.biz.notFoundDataset')}</div>;
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
