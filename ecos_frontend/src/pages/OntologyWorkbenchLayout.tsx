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
import ExportTasksView from './ontology/ExportTasksView';
import { InterfaceView, SharedPropertyView, DatasetView } from './ontology/OtherViews';
import GlossaryTab from './knowledge/tabs/GlossaryTab';
import ObjectExplorerView from './ObjectExplorerView';
import ProposalPanel from './ontology/ProposalPanel';

import {
  createEntity,
  deleteEntity,
  fetchEntities,
  fetchProperties,
  createRelationship,
  deleteRelationship,
  fetchRelationships,
  mapEntityToObjectType,
  mapRelationshipToLinkType,
  DEFAULT_ONTOLOGY_ID,
  createExportTask,
  fetchWorkbenchDomains,
  fetchDwDatasets,
  fetchActionTypes,
} from '../services/ontologyApi';
import {
  DEMO_FUNCTION_TYPES,
  DEMO_INTERFACES,
  DEMO_SHARED_PROPERTIES,
  applyEnterpriseDemoBindings,
} from '../data/enterpriseOntologyDemo';
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
  // T10: 新建导出任务后的列表刷新信号（ExportTasksView 监听自增即重拉任务列表）
  const [exportTasksSignal, setExportTasksSignal] = useState(0);
  // 详情页「发起变更提案」信号：自增后 ProposalPanel 自动展开表单并锁定当前对象类型
  const [proposalFormSignal, setProposalFormSignal] = useState(0);

  // ── Load Initial Data via custom hook ──
  // T8: domains 不再由 fetchOntologies(本体表) 映射 — 改由 reloadDomains 拉取
  //     /api/v1/ontology/domains(域表, OntologyDomainApiController) 权威源,
  //     消除"本体表当域源 + 写域表"的双源不一致。
  // useOntologyData 仍负责 objectTypes/linkTypes 种子加载(主逻辑不动)。
  useOntologyData(({ objectTypes: loadedObjects, linkTypes: loadedLinks }) => {
    setObjectTypes(applyEnterpriseDemoBindings(loadedObjects));
    setLinkTypes(loadedLinks);
    // action/interface/shared-prop/function 见下方专门的数据加载 effect
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
      // T10: 创建成功后保持 Modal 打开，刷新右下任务列表并轮询状态闭环
      setExportTasksSignal(v => v + 1);
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

  // ── T8: 域数据加载/重拉（后端 /api/v1/ontology/domains 为权威源）──
  const reloadDomains = async () => {
    try {
      const vos = await fetchWorkbenchDomains();
      const list: OntologyDomain[] = (vos || []).map((v: any) => ({
        // id 必须取域表主键（ecos_domain.id）—— 实体表 domain_id 外键指向它，
        // 用 code 作 id 会导致对象详情「所属域」下拉无法回显、侧边栏分组失配
        id: v.id ?? v.code,
        code: v.code,
        displayName: v.name || v.code || v.id,
        description: v.description || '',
        color: 'slate',
        status: v.status,
      }));
      setDomains(list);
      return list;
    } catch (e: any) {
      // 后端不可达：toast 提示，保留本地 last-good 列表（不 mock/不清空）
      showToast('error', t('ow.domain.load_failed').replace('{error}', String(e.message || e)));
      return domains;
    }
  };

  useEffect(() => {
    reloadDomains();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /**
   * 加载数据映射的数据源 —— DW 层（CURATED）数据对象。
   *
   * 依据《数据湖存储分层规范》§四：本体工作台禁止直读近源层（MinIO RAW），
   * 只能从 DW 层取数。列定义在选中对象时由 ObjectTypeDetail 懒加载。
   */
  useEffect(() => {
    let cancelled = false;
    fetchDwDatasets()
      .then(list => {
        if (!cancelled) {
          setDatasets(list);
        }
      })
      .catch((e: any) => {
        // 后端不可达：保持空列表并显式提示，不静默空白
        if (!cancelled) {
          showToast('error', t('ow.msg.dwDatasetLoadFailed').replace('{error}', String(e?.message || e)));
        }
      });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /**
   * 加载操作类型 —— 真实后端 /api/v1/ontology/action-types（表 ecos_action_type）。
   */
  useEffect(() => {
    let cancelled = false;
    fetchActionTypes()
      .then(list => {
        if (!cancelled) {
          setActionTypes(list);
        }
      })
      .catch((e: any) => {
        if (!cancelled) {
          showToast('error', t('ow.msg.actionTypesLoadFailed').replace('{error}', String(e?.message || e)));
        }
      });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /**
   * 加载逻辑函数 / 接口规范 / 共享属性 —— 后端暂无数据源，使用企业 demo 数据集
   * （见 data/enterpriseOntologyDemo.ts）。
   */
  useEffect(() => {
    setFunctionTypes(DEMO_FUNCTION_TYPES);
    setInterfaces(DEMO_INTERFACES);
    setSharedProperties(DEMO_SHARED_PROPERTIES);
  }, []);

  /**
   * 重拉对象类型及其属性（提案执行完成后的闭环刷新）
   * 与 useOntologyData 的加载路径一致：实体列表 + 逐实体属性 → mapEntityToObjectType，
   * 确保提案落库后的变更（新增/修改/删除属性）立即可见。
   */
  const reloadObjects = async () => {
    try {
      const rawEntities = await fetchEntities(DEFAULT_ONTOLOGY_ID);
      const entities = rawEntities || [];
      const propsResponses = await Promise.all(
        entities.map((entity: any) => fetchProperties(entity.id).catch((): any[] => [])),
      );
      const list: ObjectType[] = entities.map((entity: any, idx: number) =>
        mapEntityToObjectType(entity, propsResponses[idx] || []),
      );
      setObjectTypes(applyEnterpriseDemoBindings(list));
    } catch (e: any) {
      showToast('error', t('ow.msg.dataLoadFailed'));
    }
  };

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
        // Reload from backend（实体列表 + 各实体属性，映射收敛到 mapEntityToObjectType）
        const entities = await fetchEntities(DEFAULT_ONTOLOGY_ID).catch((): any[] => []);
        const propsResponses = await Promise.all(
          (entities || []).map((e: any) => fetchProperties(e.id).catch((): any[] => []))
        );
        const list: ObjectType[] = (entities || []).map((e: any, idx: number) =>
          mapEntityToObjectType(e, propsResponses[idx] || [])
        );
        updateObjectTypes(applyEnterpriseDemoBindings(list));
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
        });
        showToast('success', t('ow.msg.linkCreated'));
        const rels = await fetchRelationships().catch((): any[] => []);
        updateLinkTypes((rels || []).map(mapRelationshipToLinkType));
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
        await deleteRelationship(id);
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
    <div className={`flex h-full flex-col md:flex-row ${styles.appBg} ${styles.appText} overflow-hidden text-xs font-sans`}>
      {/* Left Sidebar — 桌面双栏左侧固定宽度；移动端顶部 */}
      <div className="w-full md:w-60 md:h-full md:shrink-0 overflow-y-auto" style={{ borderColor: 'var(--border, rgba(0,0,0,0.08))' }}>
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
          onToast={showToast}
          selectedCategory={selectedCategory}
          selectedId={selectedId}
          onSelectCategory={(category: ViewCategory, id: string | null) => {
            setSelectedCategory(category);
            setSelectedId(id);
          }}
          onCreateNew={handleCreateNewElement}
        />
      </div>

      {/* Main Content Area — 桌面双栏右侧；移动端落到下方 */}
      <main className="w-full flex-1 min-w-0 overflow-hidden relative flex flex-col">
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

        {/* min-h-0：flex 纵向布局下 overflow-auto 子项的最小高度会退化为 0，
            若不显式声明，详情区会被下方提案面板挤成 0 高度（对象详情与属性表不可见） */}
        <div className="flex-1 min-h-0 overflow-auto">
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
              onCreateProposal={() => setProposalFormSignal(v => v + 1)}
              onToast={showToast}
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

        {/* Wiki */}
        {selectedCategory === 'glossary' && (
          // h-full + min-h-0：GlossaryManager 是 h-full flex 布局，父级不给确定高度会塌成 0
          <div className="p-4 h-full min-h-0">
            <GlossaryTab />
          </div>
        )}
        </div>

        {/* Proposal Panel — 目标锁定当前选中对象类型；执行完成后回刷实体/属性形成闭环。
            max-h + 自身滚动：提案列表会随数量增长，不加约束会把上方详情区挤塌 */}
        {selectedCategory === 'object' && (
          <div className="shrink-0 max-h-[45%] overflow-y-auto">
            <ProposalPanel
              objectTypes={objectTypes}
              selectedObjectType={objectTypes.find(o => o.id === selectedId) ?? null}
              openFormSignal={proposalFormSignal}
              onProposalExecuted={reloadObjects}
            />
          </div>
        )}
      </main>

      {/* Export Modal — T3 主题 token；T10 闭环：格式/范围创建 + 任务列表（轮询/下载/删除） */}
      {showExportModal && (
        <div className={`fixed inset-0 ${styles.overlayBg} z-50 flex items-center justify-center`} onClick={() => setShowExportModal(false)}>
          <div className={`rounded-xl shadow-2xl border ${styles.cardBorder} p-5 w-[34rem] max-w-[92vw] max-h-[85vh] overflow-y-auto ${styles.cardBg} ${styles.cardText}`} onClick={e => e.stopPropagation()}>
            <h3 className={`text-sm font-bold mb-3 ${styles.cardText}`}>{t('ow.section.exportPanel')}</h3>
            <div className="space-y-3">
              <div>
                <label className={`block text-[10px] font-semibold mb-1 ${styles.muted}`}>{t('ow.label.exportFormat')}</label>
                <div className="flex gap-2">
                  {(['JSON', 'CSV', 'DDL'] as const).map(f => (
                    <button key={f} onClick={() => setExportFormat(f)}
                      className={`px-3 py-1.5 rounded text-[10px] font-semibold transition-colors ${exportFormat === f ? `${styles.accentBg} text-white` : `${styles.inputBg} ${styles.inputText} ${styles.sidebarHoverBg}`}`}>
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
                      className={`px-3 py-1.5 rounded text-[10px] font-semibold transition-colors ${exportScope === s ? `${styles.accentBg} text-white` : `${styles.inputBg} ${styles.inputText} ${styles.sidebarHoverBg}`}`}>
                      {t(`ow.export.scope.${s}`)}
                    </button>
                  ))}
                </div>
              </div>
              <div className="flex justify-end gap-2 pt-2">
                <button onClick={() => setShowExportModal(false)} className={`px-3 py-1.5 rounded text-[10px] font-semibold ${styles.inputBg} ${styles.inputText} ${styles.sidebarHoverBg}`}>{t('ow.btn.cancel')}</button>
                <button onClick={handleExportOntology} disabled={exporting}
                  className={`px-3 py-1.5 rounded text-[10px] font-semibold ${styles.accentBg} text-white ${styles.accentHover} disabled:opacity-50`}>
                  {exporting ? t('ow.exportTask.creating') : t('ow.btn.exportOntology')}
                </button>
              </div>
              {/* T10 导出任务闭环：列表 / 状态轮询 / 下载到本地 / 删除已结束任务 */}
              <ExportTasksView visible={showExportModal} refreshSignal={exportTasksSignal} onToast={showToast} />
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
