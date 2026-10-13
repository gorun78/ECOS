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
import { AlertTriangle, Check, X, Info } from 'lucide-react';
import { useLanguage } from '../components/LanguageContext';
import { useTheme } from '../components/ThemeContext';
import Badge from '../components/common/Badge';
import ConfirmDialog from '../components/common/ConfirmDialog';
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
import OntologyExportModal from './ontology/OntologyExportModal';
import { InterfaceView, SharedPropertyView, DatasetView } from './ontology/OtherViews';
import GlossaryTab from './knowledge/tabs/GlossaryTab';
import ObjectExplorerView from './ObjectExplorerView';
import ProposalPanel from './ontology/ProposalPanel';
import CaliberPage from './ontology/caliber';
import FunctionAuditPage from './ontology/function-audit';

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
import { useOntologyData } from '../hooks/useOntologyData';
import type { Entity } from '../types/workbench';

type ViewCategory = 'overview' | 'explorer' | 'object' | 'link' | 'action' | 'interface' | 'shared_property' | 'dataset' | 'function' | 'glossary' | 'caliber' | 'function_audit';
type CreatableType = 'object' | 'link' | 'action' | 'interface' | 'shared_property' | 'function';

/**
 * 无后端持久化能力的元素类型（架构铁律·无假真理）：
 *   - 新建/删除仅内存操作 → 侧栏「新建」菜单禁用 + 详情页删除按钮禁用，文案如实标注；
 *   - action 虽由真实端点 /api/v1/ontology/action-types 只读加载，但无 DELETE 端点，
 *     此前「删除=本地过滤」属假成功，一并禁用。
 * 后端出现对应 CRUD 端点后，把类型从本数组移除即可恢复。
 */
const UNSUPPORTED_PERSISTENCE_TYPES: ReadonlyArray<CreatableType> = ['action', 'interface', 'shared_property', 'function'];

/** 三块 demo 面板（逻辑函数/接口规范/共享属性）——单一数据源 data/enterpriseOntologyDemo.ts */
const DEMO_SOURCE_CATEGORIES: ReadonlyArray<ViewCategory> = ['function', 'interface', 'shared_property'];

export default function OntologyWorkbenchLayout() {
  // ── Ontology States ──
  const [objectTypes, setObjectTypes] = useState<ObjectType[]>([]);
  // 服务器权威数据（未叠加 demo 绑定）。提案面板必须以此为准：
  // demo 注入的 interfaces/sharedPropertyId 是演示资产，绝不允许进入 changeSet 被提交回后端。
  const [serverObjectTypes, setServerObjectTypes] = useState<ObjectType[]>([]);
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
  // 危险操作确认门（替代 window.confirm）：pendingDelete 非空即弹 ConfirmDialog
  const [pendingDelete, setPendingDelete] = useState<{ category: 'object' | 'link'; id: string; name: string } | null>(null);
  const [showExportModal, setShowExportModal] = useState(false);
  // 详情页「发起变更提案」信号：自增后 ProposalPanel 自动展开表单并锁定当前对象类型
  const [proposalFormSignal, setProposalFormSignal] = useState(0);

  // ── Load Initial Data via custom hook ──
  // T8: domains 不再由 fetchOntologies(本体表) 映射 — 改由 reloadDomains 拉取
  //     /api/v1/ontology/domains(域表, OntologyDomainApiController) 权威源,
  //     消除"本体表当域源 + 写域表"的双源不一致。
  // useOntologyData 仍负责 objectTypes/linkTypes 种子加载(主逻辑不动)。
  const ontologyData = useOntologyData(({ objectTypes: loadedObjects, linkTypes: loadedLinks }) => {
    setServerObjectTypes(loadedObjects);
    setObjectTypes(applyEnterpriseDemoBindings(loadedObjects));
    setLinkTypes(loadedLinks);
    // action/interface/shared-prop/function 见下方专门的数据加载 effect
  }, (failedCount) => {
    // 部分实体属性加载失败 ⇒ 如实提示（数据不完整 ≠ 完整）
    showToast('error', t('ontology.partialPropsLoadFailed').replace('{count}', String(failedCount)));
  });

  // ── Toast Helper ──
  const showToast = (type: 'success' | 'info' | 'error', message: string) => {
    setToast({ type, message });
    setTimeout(() => setToast(null), 4000);
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
   * （单一来源 data/enterpriseOntologyDemo.ts）。
   * 无假真理：这三块内容全部是演示资产 —— 侧栏分节挂 DEMO 徽标、进入对应面板时
   * 主区顶部渲染降级横幅（见 demoBanner），且 demo 绑定不进入提案 changeSet。
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
      // 逐实体属性失败不再静默吞为 []：计数并在结果里如实提示（数据不完整 ≠ 完整）
      let failedProps = 0;
      const propsResponses = await Promise.all(
        entities.map((entity: any) =>
          fetchProperties(entity.id).catch((): null => { failedProps += 1; return null; }),
        ),
      );
      if (failedProps > 0) {
        showToast('error', t('ontology.partialPropsLoadFailed').replace('{count}', String(failedProps)));
      }
      const list: ObjectType[] = entities.map((entity: any, idx: number) =>
        mapEntityToObjectType(entity, propsResponses[idx] || []),
      );
      setServerObjectTypes(list);
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
        setServerObjectTypes(list);
        updateObjectTypes(applyEnterpriseDemoBindings(list));
      } catch (e: any) {
        showToast('error', t('ow.msg.createFailed').replace('{error}', String(e.message)));
      }
      return;
    }
    if (type === 'link') {
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
      return;
    }

    // 无假真理：action/interface/shared_property/function 均无后端创建端点（已核实
    // services/ontology/* 全表面：action-types 仅 GET，interfaces/shared-properties/functions
    // 无任何端点）。侧栏按钮已禁用，此处为双保险 —— 不落内存假数据、不弹成功 toast。
    if ((UNSUPPORTED_PERSISTENCE_TYPES as ReadonlyArray<string>).includes(type)) {
      showToast('error', t('ontology.create_unsupported'));
    }
  };

  // ── DELETE ──
  /** 请求删除：object/link 走 ConfirmDialog 确认门后写真后端；其余类型无端点一律拒绝 */
  const handleDeleteElement = (category: string, id: string, name?: string) => {
    if (category === 'object' || category === 'link') {
      setPendingDelete({ category, id, name: name || '' });
      return;
    }
    // action（后端只读加载）/ interface / shared_property / function（demo 数据）：
    // 无 DELETE 端点 ⇒ 不执行本地过滤假删除、不弹「已移除」假 toast
    showToast('error', t('ontology.delete_unsupported'));
  };

  /** ConfirmDialog 确认后真正执行删除 */
  const executePendingDelete = async () => {
    if (!pendingDelete) return;
    const { category, id } = pendingDelete;
    setPendingDelete(null);

    if (category === 'object') {
      try {
        await deleteEntity(id, DEFAULT_ONTOLOGY_ID);
        showToast('success', t('ow.msg.entityDeleted'));
        updateObjectTypes(objectTypes.filter(ot => ot.id !== id));
        setServerObjectTypes(serverObjectTypes.filter(ot => ot.id !== id));
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
    }

    setSelectedCategory('overview');
    setSelectedId(null);
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
          unsupportedCreateTypes={UNSUPPORTED_PERSISTENCE_TYPES}
          demoSectionKeys={DEMO_SOURCE_CATEGORIES}
        />
      </div>

      {/* Main Content Area — 桌面双栏右侧；移动端落到下方 */}
      <main className="w-full flex-1 min-w-0 overflow-hidden relative flex flex-col">
        {/* Top toolbar */}
        <div className={`flex items-center justify-between px-3 py-1.5 border-b ${styles.cardBorder} ${styles.cardBg}`}>
          <span className={`flex items-center gap-2 text-[10px] font-semibold uppercase tracking-wider ${styles.muted}`}>
            <span>
            {selectedCategory === 'overview' ? t('ow.nav.overview') :
             selectedCategory === 'explorer' ? t('ow.nav.explorer') :
             selectedCategory === 'object' ? t('ow.nav.object') :
             selectedCategory === 'link' ? t('ow.nav.link') :
             selectedCategory === 'action' ? t('ow.nav.action') :
             selectedCategory === 'function' ? t('ow.nav.function') :
             selectedCategory === 'interface' ? t('ow.nav.interface') :
             selectedCategory === 'shared_property' ? t('ow.nav.shared_property') :
             selectedCategory === 'dataset' ? t('ow.nav.dataset') :
             selectedCategory === 'glossary' ? t('ow.nav.glossary') :
             selectedCategory === 'caliber' ? t('ow.nav.caliber') :
             selectedCategory === 'function_audit' ? t('ow.nav.functionAudit') : ''}
            </span>
            {DEMO_SOURCE_CATEGORIES.includes(selectedCategory) && (
              <Badge variant="warning">{t('ontology.demo_data_badge')}</Badge>
            )}
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
        {/* 六态覆盖·错误态：本体基础数据加载失败 ⇒ 显式错误横幅 + 重试（不再静默空列表） */}
        {ontologyData.error && (
          <div className={`m-3 flex items-center justify-between gap-3 rounded-lg border px-3 py-2 ${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`}>
            <span className="flex items-center gap-1.5 min-w-0">
              <AlertTriangle size={13} className="shrink-0" />
              <span className="truncate">{t('ontology.data_load_failed').replace('{error}', String(ontologyData.error))}</span>
            </span>
            <button
              type="button"
              onClick={() => ontologyData.retry()}
              className={`shrink-0 rounded border px-2 py-0.5 text-xs font-semibold ${styles.dangerBorder}`}
            >
              {t('ontology.retry')}
            </button>
          </div>
        )}
        {ontologyData.loading && objectTypes.length === 0 && (
          <div className={`m-3 rounded-lg border px-3 py-2 text-xs ${styles.infoBg} ${styles.infoText} ${styles.infoBorder}`}>
            {t('ontology.data_loading')}
          </div>
        )}
        {/* 无假真理·降级横幅：demo 面板与只读面板的显式标识 */}
        {DEMO_SOURCE_CATEGORIES.includes(selectedCategory) && (
          <div className={`m-3 flex items-center gap-2 rounded-lg border px-3 py-2 text-xs ${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`}>
            <AlertTriangle size={13} className="shrink-0" />
            <span>{t('ontology.demo_data_banner')}</span>
          </div>
        )}
        {selectedCategory === 'action' && (
          <div className={`m-3 flex items-center gap-2 rounded-lg border px-3 py-2 text-xs ${styles.infoBg} ${styles.infoText} ${styles.infoBorder}`}>
            <Info size={13} className="shrink-0" />
            <span>{t('ontology.action_readonly_banner')}</span>
          </div>
        )}
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
            onToast={showToast}
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
              onDelete={(id: string) => handleDeleteElement('object', id, ot.displayName)}
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
              onDelete={(id: string) => handleDeleteElement('link', id, lt.displayName)}
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
              deleteUnsupported
              deleteUnsupportedReason={t('ontology.delete_unsupported')}
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
              deleteUnsupported
              deleteUnsupportedReason={t('ontology.demo_data_badge')}
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
              deleteUnsupported
              deleteUnsupportedReason={t('ontology.demo_data_badge')}
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
              deleteUnsupported
              deleteUnsupportedReason={t('ontology.demo_data_badge')}
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

        {/* 术语（Glossary） */}
        {selectedCategory === 'glossary' && (
          // h-full + min-h-0：GlossaryManager 是 h-full flex 布局，父级不给确定高度会塌成 0
          <div className="p-4 h-full min-h-0">
            <GlossaryTab />
          </div>
        )}

        {/* 口径与指标治理（F03-16 / B.1） */}
        {selectedCategory === 'caliber' && (
          <div className="h-full min-h-0">
            <CaliberPage onToast={showToast} />
          </div>
        )}

        {/* Function 审计（F03-16 / B.5） */}
        {selectedCategory === 'function_audit' && (
          <div className="h-full min-h-0">
            <FunctionAuditPage onToast={showToast} />
          </div>
        )}
        </div>

        {/* Proposal Panel — 目标锁定当前选中对象类型；执行完成后回刷实体/属性形成闭环。
            max-h + 自身滚动：提案列表会随数量增长，不加约束会把上方详情区挤塌。
            changeSet 隔离（无假真理）：这里必须传 serverObjectTypes（未叠加 demo 绑定的后端权威数据），
            否则 demo 注入的 sharedPropertyId/interfaces 会被 diff 进提案、被提交回后端 = 假数据落库 */}
        {selectedCategory === 'object' && (
          <div className="shrink-0 max-h-[45%] overflow-y-auto">
            <ProposalPanel
              objectTypes={serverObjectTypes}
              selectedObjectType={serverObjectTypes.find(o => o.id === selectedId) ?? null}
              openFormSignal={proposalFormSignal}
              onProposalExecuted={reloadObjects}
            />
          </div>
        )}
      </main>

      {/* Export Modal — 自本文件拆分至 ontology/OntologyExportModal.tsx（§十 ≤800 行门禁，仅搬移不改逻辑） */}
      <OntologyExportModal
        visible={showExportModal}
        onClose={() => setShowExportModal(false)}
        onToast={showToast}
      />

      {/* Toast Notification — 语义 token 三色（禁裸色），去掉 animate-bounce 干扰动画 */}
      {toast && (
        <div
          role="status"
          aria-live="polite"
          className={`fixed bottom-6 right-6 flex items-center gap-2 px-4 py-3 rounded-lg shadow-md text-xs font-semibold border z-50 ${
            toast.type === 'success'
              ? `${styles.successBg} ${styles.successText} ${styles.successBorder}`
              : toast.type === 'error'
                ? `${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`
                : `${styles.infoBg} ${styles.infoText} ${styles.infoBorder}`
          }`}
        >
          {toast.type === 'success' ? <Check size={14} /> : toast.type === 'error' ? <X size={14} /> : <Info size={14} />}
          <span>{toast.message}</span>
        </div>
      )}

      {/* 危险操作确认门（架构铁律：禁 window.confirm） */}
      <ConfirmDialog
        visible={!!pendingDelete}
        danger
        title={t('ontology.confirm_delete_element')}
        message={pendingDelete?.category === 'link'
          ? t('ontology.confirm_delete_link').replace('{name}', pendingDelete?.name || '')
          : t('ontology.confirm_delete_object').replace('{name}', pendingDelete?.name || '')}
        confirmText={t('ontology.btn_confirm_delete')}
        cancelText={t('ow.btn.cancel')}
        onConfirm={executePendingDelete}
        onCancel={() => setPendingDelete(null)}
      />
    </div>
  );
}

// Standalone export for routing
export function OntologyWorkbenchLayoutStandalone() {
  return <OntologyWorkbenchLayout />;
}
