/**
 * 本体工作台 Zustand 状态管理 Store
 *
 * 集中管理本体工作台的全部状态：域、实体、关系、画布、选中、加载态等。
 * 通过 Actions 统一处理数据获取和 CRUD 操作，自动同步画布状态。
 *
 * 设计参考: 设计文档第9章 状态管理设计
 */

import { create } from "zustand";
import { devtools } from "zustand/middleware";
import type { Node, Edge, XYPosition } from "@xyflow/react";
import type {
  Domain,
  KnowledgeGraphResponse,
  Entity,
  Property,
  Relationship,
  GlossaryTerm,
  GlossaryFilter,
  BulkResource,
  DataResourceSummary,
  EntityTermBinding,
  PropertyTermBinding,
  EntityTableMapping,
  FieldMapping,
  CreateEntityDTO,
  UpdateEntityDTO,
  CreatePropertyDTO,
  UpdatePropertyDTO,
  CreateRelationshipDTO,
  CreateFieldMappingDTO,
} from "../types/workbench";
import {
  fetchKnowledgeGraph,
  fetchEntities,
  createEntity as apiCreateEntity,
  updateEntity as apiUpdateEntity,
  deleteEntity as apiDeleteEntity,
  fetchProperties,
  createProperty as apiCreateProperty,
  updateProperty as apiUpdateProperty,
  deleteProperty as apiDeleteProperty,
  fetchRelationships,
  createRelationship as apiCreateRelationship,
  deleteRelationship as apiDeleteRelationship,
} from "../services/ontologyApi";
import { extractDomainsFromKG } from "../adapters/domainAdapter";
import { mapEntitiesToFlow } from "../adapters/flowAdapter";
import { commit as gitCommit } from "../services/gitService";

// ── Ontology name mapping ──
const ONTOLOGY_NAMES: Record<string, string> = {
  ont001: '供应链本体',
  ont002: '财务本体',
};

function getOntologyName(ontologyId: string): string {
  return ONTOLOGY_NAMES[ontologyId] || ontologyId;
}

/** Fire-and-forget git auto-commit after ontology CRUD operations */
function autoCommit(ontologyId: string, summary: string) {
  gitCommit('data-workbench', {
    message: `ontology: ${getOntologyName(ontologyId)} — ${summary}`,
    authorName: 'system',
    authorEmail: 'system@ecos.local'
  }).catch(() => {});
}

// ================================================================
// Store 状态接口
// ================================================================

/** 实体节点画布数据 */
export interface EntityNodeData {
  code: string;
  name: string;
  entityType: string;
  propertyCount: number;
  [key: string]: unknown;
}

/** 关系边画布数据 */
export interface RelationshipEdgeData {
  code: string;
  name: string;
  relationshipType: string;
  [key: string]: unknown;
}

export interface WorkbenchState {
  // ===== 域/本体状态 =====
  /** 从知识图谱提取的域列表 */
  domains: Domain[];
  /** 当前选中的域编码 */
  currentDomainCode: string | null;
  /** 当前选中的本体 ID */
  currentOntologyId: string;

  // ===== 数据 =====
  /** 全局知识图谱数据 */
  kgData: KnowledgeGraphResponse | null;
  /** 当前域下的实体列表 */
  entities: Entity[];
  /** 当前域下的关系列表 */
  relationships: Relationship[];
  /** 按 entityId 索引的属性映射 */
  properties: Record<string, Property[]>;

  // ===== 画布 =====
  /** ReactFlow 画布节点 */
  canvasNodes: Node<EntityNodeData>[];
  /** ReactFlow 画布边 */
  canvasEdges: Edge<RelationshipEdgeData>[];

  // ===== 选中状态 =====
  /** 当前选中的实体 ID */
  selectedEntityId: string | null;

  // ===== UI 状态 =====
  /** 左侧面板是否折叠 */
  leftPanelCollapsed: boolean;
  /** 右侧面板是否折叠 */
  rightPanelCollapsed: boolean;
  /** 画布视口状态 */
  canvasViewport: { x: number; y: number; zoom: number };

  // ===== 加载态 =====
  /** 知识图谱加载中 */
  kgLoading: boolean;
  /** 实体列表加载中 */
  entitiesLoading: boolean;
  /** 关系列表加载中 */
  relationshipsLoading: boolean;
  /** 实体保存中 */
  savingEntity: boolean;
  /** 错误信息 */
  error: string | null;

  // ===== 术语库状态 (v1.1) =====
  /** 术语库术语列表 */
  glossaryTerms: GlossaryTerm[];
  /** 术语加载中 */
  glossaryTermsLoading: boolean;
  /** 实体 → 术语关联 (entityId → termId[]) */
  entityTermBindings: Record<string, string[]>;
  /** 属性 → 术语关联 (propertyId → termId) */
  propertyTermBindings: Record<string, string>;

  // ===== 数据映射状态 (v1.1) =====
  /** 数据底座全部资源 */
  dataResources: BulkResource[];
  /** 数据资源加载中 */
  dataResourcesLoading: boolean;
  /** 实体 → 物理表映射 (entityId → resourceId[]) */
  entityTableMappings: Record<string, string[]>;
  /** 实体 → 字段映射列表 (entityId → FieldMapping[]) */
  fieldMappings: Record<string, FieldMapping[]>;
  /** 当前选中实体的数据预览 */
  dataPreview: { rows: Record<string, any>[]; loading: boolean };

  // ===== 计算属性 =====
  /** 获取当前选中的域对象 */
  currentDomain: () => Domain | undefined;
  /** 获取当前选中的实体对象 */
  selectedEntity: () => Entity | undefined;
  /** 获取当前选中实体的属性列表 */
  selectedEntityProperties: () => Property[];
  /** 获取当前选中实体的关系列表 */
  selectedEntityRelationships: () => Relationship[];

  // ===== Actions: 核心流程 =====
  /** 加载知识图谱并提取域信息 */
  fetchKGAndDomains: () => Promise<void>;
  /** 切换到指定域 */
  setCurrentDomain: (code: string | null) => void;
  /** 切换本体并重载数据 */
  setOntologyId: (ontologyId: string) => Promise<void>;
  fetchDomainData: () => Promise<void>;
  /** 根据 entities + relationships 同步画布 nodes/edges */
  syncCanvasFromData: () => void;

  // ===== Actions: 选中 =====
  /** 选中实体（加载对应属性） */
  selectEntity: (id: string | null) => Promise<void>;
  /** 设置错误 */
  setError: (error: string | null) => void;
  /** 切换左侧面板 */
  toggleLeftPanel: () => void;
  /** 切换右侧面板 */
  toggleRightPanel: () => void;

  // ===== Actions: CRUD =====
  /** 创建实体 */
  createEntity: (data: CreateEntityDTO) => Promise<Entity>;
  /** 更新实体 */
  updateEntity: (id: string, data: UpdateEntityDTO) => Promise<void>;
  /** 删除实体 */
  deleteEntity: (id: string) => Promise<void>;
  /** 创建属性 */
  createProperty: (entityId: string, data: CreatePropertyDTO) => Promise<void>;
  /** 更新属性 */
  updateProperty: (
    entityId: string,
    propId: string,
    data: UpdatePropertyDTO
  ) => Promise<void>;
  /** 删除属性 */
  deleteProperty: (entityId: string, propId: string) => Promise<void>;
  /** 创建关系 */
  createRelationship: (data: CreateRelationshipDTO) => Promise<void>;
  /** 删除关系 */
  deleteRelationship: (relId: string) => Promise<void>;

  // ===== Actions: 画布交互 =====
  /** 添加画布节点 */
  addNode: (entity: Partial<Entity>) => void;
  /** 更新节点位置 */
  updateNodePosition: (nodeId: string, position: XYPosition) => void;
  /** 连线连接 */
  connectNodes: (source: string, target: string) => void;

  // ===== Actions: 术语库 (v1.1) =====
  /** 获取术语库术语 */
  fetchGlossaryTerms: (filters?: { domain?: string; status?: string }) => Promise<void>;
  /** 绑定实体与术语 */
  bindEntityToTerm: (entityId: string, termId: string) => void;
  /** 解绑实体与术语 */
  unbindEntityFromTerm: (entityId: string, termId: string) => void;
  /** 绑定属性与术语 */
  bindPropertyToTerm: (propertyId: string, termId: string) => void;
  /** 解绑属性与术语 */
  unbindPropertyFromTerm: (propertyId: string) => void;

  // ===== Actions: 数据映射 (v1.1) =====
  /** 获取数据底座资源 */
  fetchDataResources: () => Promise<void>;
  /** 绑定实体与物理表 */
  bindEntityToTable: (entityId: string, resourceId: string) => Promise<void>;
  /** 解绑实体与物理表 */
  unbindEntityFromTable: (entityId: string, resourceId: string) => void;
  /** 保存字段映射 */
  saveFieldMappings: (entityId: string, mappings: FieldMapping[]) => void;
  /** 获取数据预览 */
  fetchDataPreview: (entityId: string) => Promise<void>;
}

// ================================================================
// Store 实现
// ================================================================

export const useWorkbenchStore = create<WorkbenchState>()(
  devtools(
    (set, get) => ({
      // ── 初始状态 ──
      domains: [] as Domain[],
      currentDomainCode: null as string | null,
      currentOntologyId: "ont001",
      kgData: null as KnowledgeGraphResponse | null,
      entities: [] as Entity[],
      relationships: [] as Relationship[],
      properties: {},
      canvasNodes: [] as Node<EntityNodeData>[],
      canvasEdges: [] as Edge<RelationshipEdgeData>[],
      selectedEntityId: null as string | null,
      leftPanelCollapsed: false,
      rightPanelCollapsed: false,
      canvasViewport: { x: 0, y: 0, zoom: 1 },
      kgLoading: false,
      entitiesLoading: false,
      relationshipsLoading: false,
      savingEntity: false,
      error: null as string | null,

      // v1.1
      glossaryTerms: [] as GlossaryTerm[],
      glossaryTermsLoading: false,
      entityTermBindings: {},
      propertyTermBindings: {},
      dataResources: [] as BulkResource[],
      dataResourcesLoading: false,
      entityTableMappings: {},
      fieldMappings: {},
      dataPreview: { rows: [] as Record<string, any>[], loading: false },

      // ===== 计算属性
      currentDomain: () => {
        const { domains, currentDomainCode } = get();
        return domains.find((d) => d.code === currentDomainCode);
      },

      selectedEntity: () => {
        const { entities, selectedEntityId } = get();
        return entities.find((e) => e.id === selectedEntityId);
      },

      selectedEntityProperties: () => {
        const { properties, selectedEntityId } = get();
        return selectedEntityId ? properties[selectedEntityId] || [] : [];
      },

      selectedEntityRelationships: () => {
        const { relationships, selectedEntityId } = get();
        if (!selectedEntityId) return [];
        return relationships.filter(
          (r) =>
            r.sourceEntityId === selectedEntityId ||
            r.targetEntityId === selectedEntityId
        );
      },

      // ── 核心 Actions ──

      /** 加载知识图谱并提取域信息 */
      fetchKGAndDomains: async () => {
        set({ kgLoading: true, error: null });
        try {
          const kgData = await fetchKnowledgeGraph();
          const domains = extractDomainsFromKG(kgData);
          set({ kgData, domains, kgLoading: false });
        } catch (err: any) {
          set({
            kgLoading: false,
            error: err?.message || "获取知识图谱失败",
          });
        }
      },

      /** 设置当前域 */
      setCurrentDomain: (code: string | null) => {
        set({ currentDomainCode: code });
      },

      /** 切换本体，触发数据重载 */
      setOntologyId: async (ontologyId: string) => {
        set({ currentOntologyId: ontologyId, entities: [], relationships: [], properties: {}, selectedEntityId: null });
        // 立即加载该本体的数据
        await get().fetchDomainData();
      },

      /** 加载当前本体下的实体、关系和属性数据 */
      fetchDomainData: async () => {
        set({
          entitiesLoading: true,
          relationshipsLoading: true,
          error: null,
        });
        try {
          const ontologyId = get().currentOntologyId;
          // 分别加载，关系失败不影响实体
          const entities = await fetchEntities(ontologyId);
          let relationships: Relationship[] = [];
          try {
            relationships = await fetchRelationships(ontologyId);
          } catch {
            // 关系API可能不存在，忽略
            console.warn('fetchRelationships failed for ontology:', ontologyId);
          }

          set({
            entities,
            relationships,
            entitiesLoading: false,
            relationshipsLoading: false,
          });

          // 同步画布
          get().syncCanvasFromData();
        } catch (err: any) {
          set({
            entitiesLoading: false,
            relationshipsLoading: false,
            error: err?.message || "获取域数据失败",
          });
        }
      },

      /** 将当前 entities + relationships 同步为 ReactFlow 画布格式 */
      syncCanvasFromData: () => {
        const { entities, relationships } = get();
        const { nodes, edges } = mapEntitiesToFlow(entities, relationships);
        set({ canvasNodes: nodes, canvasEdges: edges });
      },

      // ── 选中 ──

      /** 选中实体，同时加载属性 */
      selectEntity: async (id: string | null) => {
        set({ selectedEntityId: id });

        // 选中实体时，如果该实体的属性尚未加载，则加载之
        if (id && !get().properties[id]) {
          try {
            const props = await fetchProperties(id);
            set((state) => ({
              properties: {
                ...state.properties,
                [id]: props,
              },
            }));
          } catch {
            // 属性加载失败不阻塞选中
          }
        }
      },

      setError: (error: string | null) => set({ error }),

      toggleLeftPanel: () =>
        set((s) => ({ leftPanelCollapsed: !s.leftPanelCollapsed })),

      toggleRightPanel: () =>
        set((s) => ({ rightPanelCollapsed: !s.rightPanelCollapsed })),

      // ── CRUD Actions ──

      /** 创建实体 → API 调用 → 更新 entities + canvasNodes → 自动选中 */
      createEntity: async (data: CreateEntityDTO) => {
        set({ savingEntity: true, error: null });
        try {
          const ontologyId = get().currentOntologyId;
          const newEntity = await apiCreateEntity(data, ontologyId);

          set((state) => {
            const entities = [...state.entities, newEntity];
            const { nodes, edges } = mapEntitiesToFlow(
              entities,
              state.relationships
            );
            return {
              entities,
              canvasNodes: nodes,
              canvasEdges: edges,
              selectedEntityId: newEntity.id,
              savingEntity: false,
            };
          });
          autoCommit(ontologyId, `新增实体「${data.name}」`);
          return newEntity;
        } catch (err: any) {
          set({
            savingEntity: false,
            error: err?.message || "创建实体失败",
          });
          throw err;
        }
      },

      /** 更新实体 → API 调用 → 更新 entities + canvasNodes */
      updateEntity: async (id: string, data: UpdateEntityDTO) => {
        set({ savingEntity: true, error: null });
        try {
          const updated = await apiUpdateEntity(id, data);

          set((state) => {
            const entities = state.entities.map((e) =>
              e.id === id ? { ...e, ...updated } : e
            );
            const { nodes, edges } = mapEntitiesToFlow(
              entities,
              state.relationships
            );
            return {
              entities,
              canvasNodes: nodes,
              canvasEdges: edges,
              savingEntity: false,
            };
          });
          autoCommit(get().currentOntologyId, `修改实体「${data.name || updated.name || id}」`);
        } catch (err: any) {
          set({
            savingEntity: false,
            error: err?.message || "更新实体失败",
          });
          throw err;
        }
      },

      /** 删除实体 → API 调用 → 更新 entities + canvasNodes + properties + selectedEntityId */
      deleteEntity: async (id: string) => {
        set({ error: null });
        try {
          const ontologyId = get().currentOntologyId;
          const entityToDelete = get().entities.find((e) => e.id === id);
          const entityName = entityToDelete?.name || id;
          await apiDeleteEntity(id, ontologyId);

          set((state) => {
            const entities = state.entities.filter((e) => e.id !== id);
            const relationships = state.relationships.filter(
              (r) => r.sourceEntityId !== id && r.targetEntityId !== id
            );
            // eslint-disable-next-line @typescript-eslint/no-unused-vars
            const { [id]: _, ...properties } = state.properties;
            const { nodes, edges } = mapEntitiesToFlow(entities, relationships);
            return {
              entities,
              relationships,
              properties,
              canvasNodes: nodes,
              canvasEdges: edges,
              selectedEntityId:
                state.selectedEntityId === id ? null : state.selectedEntityId,
            };
          });
          autoCommit(ontologyId, `删除实体「${entityName}」`);
        } catch (err: any) {
          set({ error: err?.message || "删除实体失败" });
          throw err;
        }
      },

      /** 创建属性 → API 调用 → 更新 properties + 更新画布 propertyCount */
      createProperty: async (
        entityId: string,
        data: CreatePropertyDTO
      ) => {
        set({ error: null });
        try {
          const newProp = await apiCreateProperty(entityId, data);

          set((state) => {
            const existing = state.properties[entityId] || [];
            const properties = {
              ...state.properties,
              [entityId]: [...existing, newProp],
            };

            // 更新画布节点上的 propertyCount
            const canvasNodes = state.canvasNodes.map((node) =>
              node.id === entityId
                ? {
                    ...node,
                    data: {
                      ...node.data,
                      propertyCount: (node.data.propertyCount || 0) + 1,
                    },
                  }
                : node
            );

            return { properties, canvasNodes };
          });
          autoCommit(get().currentOntologyId, `新增属性「${data.name}」`);
        } catch (err: any) {
          set({ error: err?.message || "创建属性失败" });
          throw err;
        }
      },

      /** 更新属性 → API 调用 → 更新 properties */
      updateProperty: async (
        entityId: string,
        propId: string,
        data: UpdatePropertyDTO
      ) => {
        set({ error: null });
        try {
          const updated = await apiUpdateProperty(entityId, propId, data);

          set((state) => {
            const existing = state.properties[entityId] || [];
            return {
              properties: {
                ...state.properties,
                [entityId]: existing.map((p) =>
                  p.id === propId ? { ...p, ...updated } : p
                ),
              },
            };
          });
          autoCommit(get().currentOntologyId, `修改属性「${data.name || updated.name || propId}」`);
        } catch (err: any) {
          set({ error: err?.message || "更新属性失败" });
          throw err;
        }
      },

      /** 删除属性 → API 调用 → 更新 properties + 画布 propertyCount */
      deleteProperty: async (entityId: string, propId: string) => {
        set({ error: null });
        try {
          const propToDelete = get().properties[entityId]?.find((p) => p.id === propId);
          const propName = propToDelete?.name || propId;
          await apiDeleteProperty(entityId, propId);

          set((state) => {
            const existing = state.properties[entityId] || [];
            const properties = {
              ...state.properties,
              [entityId]: existing.filter((p) => p.id !== propId),
            };

            const canvasNodes = state.canvasNodes.map((node) =>
              node.id === entityId
                ? {
                    ...node,
                    data: {
                      ...node.data,
                      propertyCount: Math.max(
                        0,
                        (node.data.propertyCount || 0) - 1
                      ),
                    },
                  }
                : node
            );

            return { properties, canvasNodes };
          });
          autoCommit(get().currentOntologyId, `删除属性「${propName}」`);
        } catch (err: any) {
          set({ error: err?.message || "删除属性失败" });
          throw err;
        }
      },

      /** 创建关系 → API 调用 → 更新 relationships + canvasEdges */
      createRelationship: async (data: CreateRelationshipDTO) => {
        set({ error: null });
        try {
          const ontologyId = get().currentOntologyId;
          const newRel = await apiCreateRelationship(data, ontologyId);

          set((state) => {
            const relationships = [...state.relationships, newRel];

            // 添加画布边
            const newEdge: Edge<RelationshipEdgeData> = {
              id: newRel.id,
              source: newRel.sourceEntityId,
              target: newRel.targetEntityId,
              type: "relationshipEdge",
              data: {
                code: newRel.code,
                name: newRel.name,
                relationshipType: newRel.relationshipType,
              },
            };

            return {
              relationships,
              canvasEdges: [...state.canvasEdges, newEdge],
            };
          });
          autoCommit(ontologyId, `新增关系「${data.name || newRel.name}」`);
        } catch (err: any) {
          set({ error: err?.message || "创建关系失败" });
          throw err;
        }
      },

      /** 删除关系 → API 调用 → 更新 relationships + canvasEdges */
      deleteRelationship: async (relId: string) => {
        set({ error: null });
        try {
          const ontologyId = get().currentOntologyId;
          const relToDelete = get().relationships.find((r) => r.id === relId);
          const relName = relToDelete?.name || relId;
          await apiDeleteRelationship(relId, ontologyId);

          set((state) => ({
            relationships: state.relationships.filter(
              (r) => r.id !== relId
            ),
            canvasEdges: state.canvasEdges.filter((e) => e.id !== relId),
          }));
          autoCommit(ontologyId, `删除关系「${relName}」`);
        } catch (err: any) {
          set({ error: err?.message || "删除关系失败" });
          throw err;
        }
      },

      // ── 画布交互 ──

      addNode: (entity: Partial<Entity>) => {
        // 创建临时实体节点（通常由画布交互触发）
        const { nodes } = mapEntitiesToFlow(
          [...get().entities, entity as Entity],
          get().relationships
        );
        set({ canvasNodes: nodes });
      },

      updateNodePosition: (nodeId: string, position: XYPosition) => {
        set((state) => ({
          canvasNodes: state.canvasNodes.map((node) =>
            node.id === nodeId ? { ...node, position } : node
          ),
        }));
      },

      connectNodes: (source: string, target: string) => {
        // 标记为临时连线，真正创建需通过 createRelationship API
        // 此方法仅用于画布交互反馈，实际创建在 CreateRelationshipModal 中完成
      },

      // ── 术语库 (v1.1) ──

      fetchGlossaryTerms: async (filters) => {
        set({ glossaryTermsLoading: true, error: null });
        try {
          // 使用现有 glossary API 端点
          const params = new URLSearchParams();
          if (filters?.domain) params.set("domain", filters.domain);
          if (filters?.status) params.set("status", filters.status);

          const resp = await fetch(
            `/api/glossary/terms?${params.toString()}`
          );
          if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
          const json = await resp.json();
          const terms: GlossaryTerm[] =
            json.data || json.records || json || [];
          set({
            glossaryTerms: terms,
            glossaryTermsLoading: false,
          });
        } catch (err: any) {
          set({
            glossaryTermsLoading: false,
            error: err?.message || "获取术语列表失败",
          });
        }
      },

      bindEntityToTerm: (entityId: string, termId: string) => {
        set((state) => {
          const current = state.entityTermBindings[entityId] || [];
          if (current.includes(termId)) return state;
          return {
            entityTermBindings: {
              ...state.entityTermBindings,
              [entityId]: [...current, termId],
            },
          };
        });
      },

      unbindEntityFromTerm: (entityId: string, termId: string) => {
        set((state) => ({
          entityTermBindings: {
            ...state.entityTermBindings,
            [entityId]: (state.entityTermBindings[entityId] || []).filter(
              (t) => t !== termId
            ),
          },
        }));
      },

      bindPropertyToTerm: (propertyId: string, termId: string) => {
        set((state) => ({
          propertyTermBindings: {
            ...state.propertyTermBindings,
            [propertyId]: termId,
          },
        }));
      },

      unbindPropertyFromTerm: (propertyId: string) => {
        set((state) => {
          // eslint-disable-next-line @typescript-eslint/no-unused-vars
          const { [propertyId]: _, ...rest } = state.propertyTermBindings;
          return { propertyTermBindings: rest };
        });
      },

      // ── 数据映射 (v1.1) ──

      fetchDataResources: async () => {
        set({ dataResourcesLoading: true, error: null });
        try {
          const resp = await fetch(
            "/api/v1/datanet/metadata/resources/all"
          );
          if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
          const json = await resp.json();
          const resources: BulkResource[] =
            json.data || json || [];
          set({
            dataResources: resources,
            dataResourcesLoading: false,
          });
        } catch (err: any) {
          set({
            dataResourcesLoading: false,
            error: err?.message || "获取数据资源失败",
          });
        }
      },

      bindEntityToTable: async (entityId: string, resourceId: string) => {
        set((state) => {
          const current = state.entityTableMappings[entityId] || [];
          if (current.includes(resourceId)) return state;
          return {
            entityTableMappings: {
              ...state.entityTableMappings,
              [entityId]: [...current, resourceId],
            },
          };
        });
      },

      unbindEntityFromTable: (entityId: string, resourceId: string) => {
        set((state) => ({
          entityTableMappings: {
            ...state.entityTableMappings,
            [entityId]: (state.entityTableMappings[entityId] || []).filter(
              (r) => r !== resourceId
            ),
          },
        }));
      },

      saveFieldMappings: (entityId: string, mappings: FieldMapping[]) => {
        set((state) => ({
          fieldMappings: {
            ...state.fieldMappings,
            [entityId]: mappings,
          },
        }));
      },

      fetchDataPreview: async (entityId: string) => {
        const { entityTableMappings, dataResources } = get();
        const resourceIds = entityTableMappings[entityId];

        if (!resourceIds || resourceIds.length === 0) {
          set({
            dataPreview: { rows: [], loading: false },
          });
          return;
        }

        set({ dataPreview: { rows: [], loading: true } });

        try {
          // 取第一个映射的资源做预览
          const resourceId = resourceIds[0];
          const resp = await fetch(
            `/api/v1/datanet/metadata/preview/${resourceId}?limit=50`
          );
          if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
          const json = await resp.json();
          const rows: Record<string, any>[] =
            json.data?.rows || json.rows || json.data || [];
          set({
            dataPreview: { rows, loading: false },
          });
        } catch (err: any) {
          set({
            dataPreview: { rows: [], loading: false },
          });
        }
      },
    }),
    { name: "workbench-store" }
  )
);
