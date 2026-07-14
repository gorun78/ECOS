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
import ObjectExplorerView from './ObjectExplorerView';

import {
  createEntity,
  deleteEntity,
  fetchEntities,
  createRelationship,
  deleteRelationship,
  fetchRelationships,
  DEFAULT_ONTOLOGY_ID,
} from '../services/ontologyApi';
import { useOntologyData } from '../hooks/useOntologyData';
import type { Entity } from '../types/workbench';

type ViewCategory = 'overview' | 'explorer' | 'object' | 'link' | 'action' | 'interface' | 'shared_property' | 'dataset' | 'function';
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
          name: `未命名对象_${defaultNum}`,
          code: `custom_obj_${defaultNum}`,
          description: '新建自定义业务实体',
          entityType: 'MASTER',
        });
        showToast('success', `创建了对象类型: ${created.name}`);
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
          mapping: e.mapping || null,
          domainId: e.domainId || null,
        }));
        updateObjectTypes(list);
      } catch (e: any) {
        showToast('error', `创建失败: ${e.message}`);
      }
    } else if (type === 'link') {
      if (objectTypes.length < 2) {
        showToast('error', '建立关联至少需要 2 个对象类型！');
        return;
      }
      try {
        await createRelationship(DEFAULT_ONTOLOGY_ID, {
          sourceEntityId: objectTypes[0].id,
          targetEntityId: objectTypes[1].id,
          name: `新关联_${defaultNum}`,
          relationshipType: 'ONE_TO_MANY',
        });
        showToast('success', '关联关系创建成功');
        const rels = await fetchRelationships(DEFAULT_ONTOLOGY_ID).catch(() => []);
        const list: LinkType[] = (rels || []).map((r: any) => ({
          id: r.id,
          displayName: r.name || '',
          apiName: r.code || '',
          description: '',
          sourceObjectType: r.source_entity_id,
          targetObjectType: r.target_entity_id,
          cardinality: (r.relationship_type === 'ONE_TO_ONE' ? '1:1' : r.relationship_type === 'MANY_TO_MANY' ? 'N:N' : '1:N') as any,
          mapping: { type: 'foreign_key' as const, foreignKeyMapping: {} },
        }));
        updateLinkTypes(list);
      } catch (e: any) {
        showToast('error', `创建关联失败: ${e.message}`);
      }
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
      updateInterfaces([...interfaces, newIntf]);
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
      updateSharedProperties([...sharedProperties, newSp]);
      setSelectedCategory('shared_property');
      setSelectedId(newSpId);
      showToast('info', `创建了共享属性: ${newSp.displayName}`);
    } else if (type === 'function') {
      const newFuncId = `custom_function_${defaultNum}`;
      const newFunc: FunctionType = {
        id: newFuncId,
        displayName: `新逻辑函数_${defaultNum}`,
        apiName: `customFunction${defaultNum}`,
        description: '配置 TypeScript 逻辑函数。可通过 @Function() 装饰器向本体应用公开计算规则。',
        returnType: 'string',
        parameters: [],
        code: `import { Function } from "@ecos/functions-api";\n\nexport class CustomFunctionClass_${defaultNum} {\n    @Function()\n    public async customFunction${defaultNum}(): Promise<string> {\n        return "Hello World";\n    }\n}`
      };
      updateFunctionTypes([...functionTypes, newFunc]);
      setSelectedCategory('function');
      setSelectedId(newFuncId);
      showToast('info', `创建了逻辑函数: ${newFunc.displayName}`);
    }
  };

  // ── DELETE ──
  const handleDeleteElement = async (category: string, id: string) => {
    const confirmDelete = window.confirm(t('ontology.confirm_delete_element'));
    if (!confirmDelete) return;

    if (category === 'object') {
      try {
        await deleteEntity(id, DEFAULT_ONTOLOGY_ID);
        showToast('success', '实体已删除');
        updateObjectTypes(objectTypes.filter(ot => ot.id !== id));
        updateLinkTypes(linkTypes.filter(lt => lt.sourceObjectType !== id && lt.targetObjectType !== id));
      } catch (e: any) {
        showToast('error', `删除失败: ${e.message}`);
      }
    } else if (category === 'link') {
      try {
        await deleteRelationship(id, DEFAULT_ONTOLOGY_ID);
        showToast('success', '关联已删除');
        updateLinkTypes(linkTypes.filter(lt => lt.id !== id));
      } catch (e: any) {
        showToast('error', `删除失败: ${e.message}`);
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
    showToast('info', '元素已成功从本体草稿中抹除。');
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
          if (!ot) return <div className="p-6 text-slate-400 text-xs">未找到该对象类型</div>;
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
          if (!lt) return <div className="p-6 text-slate-400 text-xs">未找到该链接关系</div>;
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
          if (!at) return <div className="p-6 text-slate-400 text-xs">未找到该操作类型</div>;
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
          if (!fn) return <div className="p-6 text-slate-400 text-xs">未找到该逻辑函数</div>;
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
          if (!it) return <div className="p-6 text-slate-400 text-xs">未找到该接口类型</div>;
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
          if (!sp) return <div className="p-6 text-slate-400 text-xs">未找到该共享属性</div>;
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
          if (!ds) return <div className="p-6 text-slate-400 text-xs">未找到该数据集</div>;
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
      </main>

      {/* Toast Notification */}
      {toast && (
        <div className="fixed bottom-6 right-6 flex items-center gap-2 px-4 py-3 rounded-lg shadow-xl text-xs font-semibold text-white bg-slate-900 border border-slate-700/80 z-50 animate-bounce">
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
