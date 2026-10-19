/**
 * ontologyClient.ts — src/api.ts 按域拆分产物（design-05 B.9-1 / X-50 / F05-17）。
 * 经 src/api.ts re-export shim 保持既有 from "../api" 导入面零破坏。
 */
import type {
  EntityDefinition,
  EntityInstance,
  RelationshipDefinition,
  ActionDefinition,
} from "../types";
import { API_BASE, apiFetchData } from "../services/httpClient";

// ── Ontology ────────────────────────────────────────────
export async function fetchOntology(): Promise<{
  entities: EntityDefinition[];
  instances: Record<string, EntityInstance[]>;
}> {
  try {
    const resp = await apiFetchData<{ data?: any[]; records?: any[] }>(`${API_BASE}/v1/ecos/ontologies/ont001/entities`);
    const records = resp?.data || resp?.records || [];

    if (records.length > 0) {
      const entities: EntityDefinition[] = records.map((rec: any) => ({
        id: rec.code || rec.id || rec.name,
        name: rec.name || rec.code || "",
        description: rec.description || "",
        properties: (rec.properties || []).map((p: any) => ({
          name: p.code || p.name || "",
          type: (p.propertyType || "string") as any,
          searchable: p.searchableFlag === 1,
          editable: true,
        })),
        relationships: [] as RelationshipDefinition[],
        actions: [] as ActionDefinition[],
      }));
      return { entities, instances: {} };
    }
  } catch (e) {
    console.warn("fetchOntology failed, using mock fallback", e);
  }

  // Mock fallback
  return buildMockOntology();
}

function buildMockOntology(): { entities: EntityDefinition[]; instances: Record<string, EntityInstance[]> } {
  const entities: EntityDefinition[] = [
    { id: "ent_customer", name: "客户 (Customer)", description: "企业客户主数据实体", properties: [
      { name: "customer_id", type: "string", searchable: true, editable: false },
      { name: "name", type: "string", searchable: true, editable: true },
      { name: "region", type: "string", searchable: true, editable: true },
      { name: "revenue", type: "number", searchable: false, editable: true },
    ], relationships: [{ name: "has", targetEntity: "ent_order", cardinality: "many" }], actions: [] },
    { id: "ent_order", name: "订单 (Order)", description: "销售订单实体", properties: [
      { name: "order_id", type: "string", searchable: true, editable: false },
      { name: "amount", type: "number", searchable: false, editable: true },
      { name: "status", type: "string", searchable: true, editable: true },
    ], relationships: [{ name: "belongs_to", targetEntity: "ent_customer", cardinality: "one" }, { name: "contains", targetEntity: "ent_product", cardinality: "many" }], actions: [] },
    { id: "ent_product", name: "产品 (Product)", description: "产品主数据", properties: [
      { name: "product_id", type: "string", searchable: true, editable: false },
      { name: "name", type: "string", searchable: true, editable: true },
      { name: "category", type: "string", searchable: true, editable: true },
      { name: "price", type: "number", searchable: false, editable: true },
    ], relationships: [{ name: "part_of", targetEntity: "ent_order", cardinality: "many" }], actions: [] },
    { id: "ent_machine", name: "设备 (Machine)", description: "生产设备实体", properties: [
      { name: "machine_id", type: "string", searchable: true, editable: false },
      { name: "temperature_c", type: "number", searchable: false, editable: true },
      { name: "has_fault", type: "boolean", searchable: true, editable: true },
    ], relationships: [], actions: [] },
  ];
  return { entities, instances: {} };
}

/** Build a fallback EntityDefinition from instance data */
function buildFallbackEntity(name: string, instances: EntityInstance[]): EntityDefinition {
  // Extract property names from the first instance
  const propNames: string[] = [];
  if (instances.length > 0) {
    Object.keys(instances[0].properties).forEach(k => {
      if (!propNames.includes(k)) propNames.push(k);
    });
  }
  
  return {
    id: name,
    name: `${name} Entity`,
    description: `${name} 实体（动态解析）`,
    properties: propNames.map(pn => ({
      name: pn,
      type: "string" as const,
      searchable: pn === "name" || pn === "code",
      editable: true,
    })),
    relationships: [],
    actions: [],
  };
}


// ── Object Explorer (Object Runtime) ─────────────────────────
const OBJ_BASE = `${API_BASE}/v1/ecos/objects`;

export interface ObjectData {
  id: string;
  entityCode: string;
  status: string;
  createdAt: string;
  updatedAt?: string;
  [key: string]: any;
}

export interface SchemaProperty {
  code: string;
  name: string;
  type: string;
  required: boolean;
  searchable: boolean;
}

export async function fetchObjects(entityCode: string, keyword?: string, page = 1, size = 50) {
  const params = new URLSearchParams({ page: String(page), pageSize: String(size) });
  if (keyword) params.set("keyword", keyword);
  return apiFetchData<{ data: ObjectData[]; total: number; page: number; pageSize: number }>(`${OBJ_BASE}/${entityCode}?${params}`);
}

export async function searchObjects(q: string, entityCode?: string) {
  const params = new URLSearchParams({ q, pageSize: "50" });
  if (entityCode) params.set("entityCode", entityCode);
  return apiFetchData<{ data: ObjectData[]; total: number }>(`${OBJ_BASE}/search?${params}`);
}

export async function fetchObjectDetail(entityCode: string, id: string) {
  return apiFetchData<ObjectData & { relations: any[]; timeline: any[] }>(`${OBJ_BASE}/${entityCode}/${id}`);
}

export async function fetchObjectSchema(entityCode: string) {
  return apiFetchData<{ entityCode: string; entityName: string; properties: SchemaProperty[] }>(`${OBJ_BASE}/${entityCode}/schema`);
}

export async function createObject(entityCode: string, properties: Record<string, any>) {
  return apiFetchData<ObjectData>(`${OBJ_BASE}/${entityCode}`, {
    method: "POST",
    body: JSON.stringify(properties),
  });
}

export async function updateObject(entityCode: string, id: string, properties: Record<string, any>) {
  return apiFetchData<ObjectData>(`${OBJ_BASE}/${entityCode}/${id}`, {
    method: "PUT",
    body: JSON.stringify(properties),
  });
}

export async function changeObjectStatus(entityCode: string, id: string, status: string, override = false) {
  return apiFetchData<ObjectData>(`${OBJ_BASE}/${entityCode}/${id}/status`, {
    method: "PATCH",
    body: JSON.stringify({ status, override: String(override) }),
  });
}

export async function deleteObject(entityCode: string, id: string) {
  return apiFetchData<null>(`${OBJ_BASE}/${entityCode}/${id}`, { method: "DELETE" });
}

// ── Gap 1: Status transitions ──
export interface ObjectTransition {
  transitionCode: string;
  toStatus: string;
  transitionName: string;
  requireRole: string | null;
}

export interface AvailableTransitions {
  currentStatus: string;
  availableTransitions: ObjectTransition[];
}

export async function fetchAvailableTransitions(entityCode: string, id: string) {
  return apiFetchData<AvailableTransitions>(`${OBJ_BASE}/${entityCode}/${id}/transitions`);
}

export async function executeTransition(entityCode: string, id: string, transition: string, actor: string, comment?: string) {
  return apiFetchData<{ newStatus: string; [key: string]: any }>(`${OBJ_BASE}/${entityCode}/${id}/transition`, {
    method: "POST",
    body: JSON.stringify({ transition, actor, comment: comment || "" }),
  });
}

// ── Gap 2: Object relationships ──
export async function createObjectRelationship(
  entityCode: string,
  id: string,
  body: {
    targetObjectId: string;
    targetEntityCode: string;
    relationshipCode: string;
    relationshipType: string;
    properties?: Record<string, any>;
  }
) {
  return apiFetchData<any>(`${OBJ_BASE}/${entityCode}/${id}/relationships`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

// ── Gap 3: Timeline pagination ──
export interface TimelineEvent {
  id: string;
  eventType: string;
  eventDetail: any;
  operator: string;
  createdAt: string;
}

export interface TimelinePage {
  data: TimelineEvent[];
  total: number;
  page: number;
  size: number;
}

export async function fetchObjectTimeline(entityCode: string, id: string, page = 1, size = 20) {
  return apiFetchData<TimelinePage>(`${OBJ_BASE}/${entityCode}/${id}/timeline?page=${page}&size=${size}`);
}


// ── Entity Instances (Operational Apps) ──────────────────
export async function fetchEntityInstances(entityType: string): Promise<any[]> {
  try {
    return await apiFetchData<any[]>(`${API_BASE}/v1/ontology/entities/${entityType}/instances`);
  } catch {
    return [];
  }
}
