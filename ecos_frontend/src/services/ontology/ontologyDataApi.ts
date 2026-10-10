/**
 * 本体数据对象 + 函数沙盒 API（ontologyApi 拆分产物 · W197/R-56 C 批）
 *
 * 域：Ontology Data（T2.3）+ Function Sandbox（T9）。
 * 端点前缀：/api/v1/ontology/data、/api/v1/ontology/functions。
 * 对外唯一导入入口仍是 `src/services/ontologyApi.ts`（纯 re-export 兼容入口）。
 */

import { apiFetchData } from "../../api";
import type {
  PaginatedDataResponse,
  DataRecord,
  CreateDataDTO,
  UpdateDataDTO,
} from "../../types/ontology";

// ================================================================
// 本体数据 (Ontology Data) — T2.3
// ================================================================

const DATA_BASE = "/api/v1/ontology/data";

export async function fetchOntologyData(params?: { type?: string; objectTypeId?: string; page?: number; size?: number }) {
  const qs = new URLSearchParams();
  if (params?.type) qs.set("type", params.type);
  if (params?.objectTypeId) qs.set("objectTypeId", params.objectTypeId);
  if (params?.page) qs.set("page", String(params.page));
  if (params?.size) qs.set("size", String(params.size));
  const query = qs.toString() ? `?${qs.toString()}` : "";
  return apiFetchData<PaginatedDataResponse>(`${DATA_BASE}${query}`);
}

export async function fetchOntologyDataObjects() {
  return apiFetchData(`${DATA_BASE}/objects`);
}

export async function fetchOntologyDataRecord(id: string) {
  return apiFetchData<DataRecord>(`${DATA_BASE}/${id}`);
}

export async function createOntologyData(data: CreateDataDTO) {
  return apiFetchData<DataRecord>(DATA_BASE, {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function updateOntologyData(id: string, data: UpdateDataDTO) {
  return apiFetchData<DataRecord>(`${DATA_BASE}/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
}

export async function deleteOntologyData(id: string) {
  await apiFetchData(`${DATA_BASE}/${id}`, { method: "DELETE" });
}

export async function clearOntologyDataByType(objectTypeId: string) {
  await apiFetchData(`${DATA_BASE}?objectTypeId=${encodeURIComponent(objectTypeId)}`, { method: "DELETE" });
}

// ================================================================
// 函数沙盒 (Function Sandbox) — T9
// 对齐后端 FunctionController (@RequestMapping("/api/v1/ontology/functions"))
//   POST /api/v1/ontology/functions/test     → 沙盒执行（expression + entityName）
//   POST /api/v1/ontology/functions/compile  → 仅编译（返回生成的参数化 SQL）
// 后端 T16-3 强类型契约：入参 OntologyFunctionSaveDTO / 返回 FunctionResult
// ================================================================

const FUNCTION_BASE = "/api/v1/ontology/functions";

/** 函数沙盒执行结果 — 对齐后端 FunctionResult POJO（@JsonInclude NON_NULL） */
export interface FunctionTestResultVO {
  /** 计算结果单值（无数据行时为 null） */
  value: number | string | boolean | null;
  /** SQL 类型：LONG / DOUBLE / NUMERIC 等（inferSqlType 推断） */
  sqlType?: string;
  /** 执行耗时（毫秒） */
  executionTimeMs: number;
  /** 编译生成的参数化 SQL（调试/预览） */
  compiledSql?: string;
  /** 是否命中结果缓存 */
  fromCache?: boolean;
  /** 缓存键 */
  cacheKey?: string;
}

/** 函数沙盒执行请求 — 对齐后端 OntologyFunctionSaveDTO */
export interface FunctionTestParams {
  /** SQL 表达式（必填），如 "COUNT(id) FROM ecos_ontology_data WHERE object_type='ot1'" */
  expression: string;
  /** 目标实体名（必填，与 FROM 子句表名一致；按实体名=表名映射执行） */
  entityName: string;
  /** 调用方 id（可选，默认 anonymous；写入函数执行审计日志） */
  callerId?: string;
}

/** 函数表达式编译结果 — 对齐后端 OntologyFunctionCompileVO */
export interface FunctionCompileVO {
  /** 编译生成的参数化 SQL */
  sql?: string;
  /** SQL 占位参数（动态 list，沙盒运行时 payload） */
  params?: unknown[];
  /** 解析出的目标实体名 */
  entityName?: string;
}

/**
 * 函数沙盒真实执行（替代前端 mock TS 执行）。
 * POST /api/v1/ontology/functions/test
 * 业务错误（白名单拒绝/执行失败）由 apiFetchData 统一转抛。
 */
export async function testFunction(params: FunctionTestParams): Promise<FunctionTestResultVO> {
  return apiFetchData<FunctionTestResultVO>(`${FUNCTION_BASE}/test`, {
    method: "POST",
    body: JSON.stringify(params),
  });
}

/**
 * 函数表达式仅编译（不执行）— 返回真实生成的参数化 SQL，供沙盒控制台日志预览。
 * POST /api/v1/ontology/functions/compile
 */
export async function compileFunction(
  expression: string,
  entityName?: string
): Promise<FunctionCompileVO> {
  return apiFetchData<FunctionCompileVO>(`${FUNCTION_BASE}/compile`, {
    method: "POST",
    body: JSON.stringify({ expression, entityName }),
  });
}
