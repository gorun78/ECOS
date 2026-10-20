/**
 * 本体映射 / DW 数据源 / 操作类型 API（ontologyApi 拆分产物 · W197/R-56 C 批）
 *
 * 域：Ontology Mapping（T2.1）、DW 层数据对象取数、Action Types。
 * 端点前缀：/api/v1/ontology/mappings、/api/v1/engine/data/layers、
 * /api/v1/datanet/metadata/fields、/api/v1/ontology/action-types。
 * 对外唯一导入入口仍是 `src/services/ontologyApi.ts`（纯 re-export 兼容入口）。
 */

import { apiFetchData } from "../../api";
import { API_BASE } from "../httpClient";
import type {
  OntologyMappingRecord,
  CreateMappingDTO,
  UpdateMappingDTO,
  MappingValidationReport,
  DocAnchor,
  DocAnchorType,
  Dataset,
  DatasetColumn,
  ActionType,
  ActionRule,
  ActionValidationRule,
} from "../../types/ontology";

// ================================================================
// 本体映射 (Ontology Mapping) — T2.1
// ================================================================

/** `/v1/ontology/mappings` 族基址单源（#145/B6-4）：全仓构造该族根的唯一书写点，消费者一律 import。 */
export const ONTOLOGY_MAPPINGS_BASE = `${API_BASE}/v1/ontology/mappings`;

export async function fetchMappings(params?: { objectId?: string; sourceType?: string }) {
  const qs = new URLSearchParams();
  if (params?.objectId) qs.set("objectId", params.objectId);
  if (params?.sourceType) qs.set("sourceType", params.sourceType);
  const query = qs.toString() ? `?${qs.toString()}` : "";
  return apiFetchData<OntologyMappingRecord[]>(`${ONTOLOGY_MAPPINGS_BASE}${query}`);
}

export async function fetchMapping(id: string) {
  return apiFetchData<OntologyMappingRecord>(`${ONTOLOGY_MAPPINGS_BASE}/${id}`);
}

export async function createMapping(data: CreateMappingDTO) {
  return apiFetchData<OntologyMappingRecord>(ONTOLOGY_MAPPINGS_BASE, {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function updateMapping(id: string, data: UpdateMappingDTO) {
  return apiFetchData<OntologyMappingRecord>(`${ONTOLOGY_MAPPINGS_BASE}/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
}

export async function deleteMapping(id: string) {
  await apiFetchData(`${ONTOLOGY_MAPPINGS_BASE}/${id}`, { method: "DELETE" });
}

export async function fetchMappableObjects() {
  return apiFetchData(`${ONTOLOGY_MAPPINGS_BASE}/objects`);
}

/**
 * 校验当前对象类型的映射一致性（C4 映射有效性，W2 前端入口）。
 *
 * POST /api/v1/ontology/mappings/validate
 * 入参 entityCode = 当前对象类型 id（与 ecos_entity_table_mapping.entity_code 对齐，
 * 见 OntologyMappingValidateDTO）；按该对象类型的全部已存映射批量校验。
 * 失败不抛错（后端始终返回 200 + valid=true/false），由调用方消费 report 渲染。
 */
export async function validateEntityMappings(objectTypeId: string): Promise<MappingValidationReport> {
  return apiFetchData<MappingValidationReport>(`${ONTOLOGY_MAPPINGS_BASE}/validate`, {
    method: "POST",
    body: JSON.stringify({ entityCode: objectTypeId }),
  });
}

/**
 * 序列化非结构化文档锚点（W2 新增）。
 *
 * 规则：
 * - anchor 为 null / 空对象 / 全字段空 → 返回 null（不落库锚点）
 * - type 缺省 TABLE；type = TABLE 时不允许写锚点 JSON
 * - DOC_ONLY / MIXED 时 docId 必填（本函数只做形式序列化，业务校验由调用方负责）
 */
export function buildDocAnchorPayload(
  type: DocAnchorType | undefined,
  anchor: DocAnchor | null
): { type?: DocAnchorType; json?: string } {
  if (!type || type === "TABLE" || !anchor) {
    // 默认 / 结构化表 / 无锚点：不写锚点列
    return { type: type || undefined, json: undefined };
  }
  const docId = String(anchor.docId || "").trim();
  const source = String(anchor.source || "").trim();
  if (!docId) {
    // docId 缺失视为无锚点（与"请填入 docId"校验语义对齐）
    return { type, json: undefined };
  }
  const payload: DocAnchor = { docId, source: source || "kb" };
  if (typeof anchor.docChunkCount === "number" && anchor.docChunkCount >= 0) {
    payload.docChunkCount = anchor.docChunkCount;
  }
  return { type, json: JSON.stringify(payload) };
}

// ================================================================
// DW 层数据对象（数据映射取数来源）
//
// 依据《数据湖存储分层规范》§四「各工作台读写边界」：本体工作台对近源层（RAW，MinIO）
// 禁止直读、对 DW 层（CURATED）只读。故数据映射的数据源必须是 layer=CURATED 的
// td_data_resource 记录，不得列近源层资源，更不得直连外部源系统。
//   GET /api/v1/engine/data/layers/CURATED      → DW 层对象列表（含 resource_id 等）
//   GET /api/v1/datanet/metadata/fields/{id}    → 该对象的列定义（懒加载）
// ================================================================

const DATA_LAYER_BASE = `${API_BASE}/v1/engine/data/layers`;
const DATA_FIELDS_BASE = `${API_BASE}/v1/datanet/metadata/fields`;

/**
 * 获取 DW 层（CURATED）数据对象列表，映射为工作台 Dataset（列定义由
 * {@link fetchDwDatasetColumns} 按需加载，避免对全部对象做 N+1 列查询）。
 */
export async function fetchDwDatasets(): Promise<Dataset[]> {
  // 后端返回包装体 {layer, resources, total}（非裸数组），兼容裸数组形态
  const resp: any = await apiFetchData<any>(`${DATA_LAYER_BASE}/CURATED`);
  const raw: any[] = Array.isArray(resp) ? resp : (resp?.resources ?? []);
  return (raw || [])
    .filter(r => r && r.resource_id)
    .map((r): Dataset => ({
      id: String(r.resource_id),
      name: String(r.resource_name || r.resource_id),
      path: String(r.source_path || `${r.datasource_id || ''}.${r.resource_name || ''}`),
      columns: [],
      sampleData: [],
    }));
}

/** 获取指定 DW 层数据对象的列定义（name/type）。 */
export async function fetchDwDatasetColumns(resourceId: string): Promise<DatasetColumn[]> {
  const fields = await apiFetchData<any[]>(`${DATA_FIELDS_BASE}/${encodeURIComponent(resourceId)}`);
  return (fields || [])
    .filter(f => f && f.fieldName)
    .map(f => ({ name: String(f.fieldName), type: String(f.dataType || '') }));
}

// ================================================================
// 操作类型 (Action Types) — 真实后端 /api/v1/ontology/action-types
//
// 后端载体：ecos_action_type（id/name/description/object_type_id/preconditions/post_actions/
// audit_required/enabled）。前端 ActionType 为富模型（参数/规则/校验），故在此做一次映射：
//   object_type_id → 注入一个 dataType='object' 的参数（对象详情「操作」Tab 依赖该参数匹配）
//   preconditions  → validationRules（前置条件即校验规则）
//   post_actions   → rules（写回动作映射为 modify_object）
// ================================================================

const ACTION_TYPE_BASE = `${API_BASE}/v1/ontology/action-types`;

/** 安全解析后端 JSON 数组文本；非法/空值返回空数组（不抛错，避免整页失败） */
function parseJsonArray(raw: unknown): any[] {
  if (typeof raw !== "string" || raw.trim() === "") {
    return [];
  }
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

/**
 * 获取全部操作类型（后端枚举 → 前端富模型）。
 */
export async function fetchActionTypes(): Promise<ActionType[]> {
  const raw = await apiFetchData<any[]>(ACTION_TYPE_BASE);
  return (raw || []).map((r): ActionType => {
    const objectTypeId = r.objectTypeId ? String(r.objectTypeId) : "";
    const preconditions = parseJsonArray(r.preconditions);
    const postActions = parseJsonArray(r.postActions);
    return {
      id: String(r.id),
      displayName: String(r.name || r.id),
      apiName: String(r.name || r.id),
      description: r.description ? String(r.description) : "",
      parameters: objectTypeId
        ? [{
            id: `${r.id}_target`,
            displayName: "目标对象",
            dataType: "object",
            objectTypeId,
            isRequired: true,
            description: "该操作作用的实例对象",
          }]
        : [],
      validationRules: preconditions.map((p: any, idx: number): ActionValidationRule => {
        const expression = `${p?.field ?? ""} ${p?.op ?? ""} ${p?.value ?? ""}`.trim();
        return {
          id: `${r.id}_pre_${idx}`,
          displayName: expression,
          expression,
          errorMessage: expression,
        };
      }),
      rules: postActions.map((a: any, idx: number): ActionRule => ({
        id: `${r.id}_post_${idx}`,
        type: "modify_object",
        targetParameterId: `${r.id}_target`,
        propertyEdits: a?.field
          ? [{ propertyId: String(a.field), valueExpression: String(a.value ?? "") }]
          : [],
      })),
    };
  });
}
