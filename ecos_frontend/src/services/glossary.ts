/**
 * Glossary API 服务层 — 本体工作台术语（Glossary）唯一服务出口。
 *
 * 封装词条 CRUD、词条关系边与词条关系图谱三组接口。
 * 后端统一返回 ApiResponse<T>：{ code: 0 (成功), message, data, timestamp }；
 * 业务错误码在 code 字段（HTTP 可能仍为 200），故此处以 body.message 透出可读错误。
 *
 * 端点：/api/v1/ontology/glossary/**
 */

import type {
  GlossaryFilter,
  GlossaryTerm as WorkbenchGlossaryTerm,
} from "../types/workbench";

const API_BASE = "/api/v1/ontology/glossary";

/** 词条类型枚举：本体实体 / 关系 / 指标 / 函数 / 概念 */
export type GlossaryTermType = "ENTITY" | "RELATION" | "METRIC" | "FUNCTION" | "CONCEPT";

/** 词条关系边类型枚举 */
export type GlossaryRelationType =
  | "ISA"
  | "SYNONYM"
  | "PART_OF"
  | "SEE_ALSO"
  | "CAUSAL"
  | "RELATED";

/** 词条（术语词条）— 完整语义字段 */
export interface GlossaryTerm {
  id: number;
  code: string | null;
  name: string;
  definition: string | null;
  /** 所属领域，对应字典 glossary_domain 的 code */
  domain: string | null;
  owner: string | null;
  /** DRAFT | REVIEW | PUBLISHED | DEPRECATED */
  status: string;
  createdBy: string | null;
  createdAt: string;
  updatedAt: string;
  /** ENTITY | RELATION | METRIC | FUNCTION | CONCEPT */
  termType: string;
  /** 同义词 / 别名 */
  aliases: string[];
  /** 关联本体实体主键，如 "ent001" */
  objectTypeId: string | null;
  /** 是否为该实体下的主术语（同一实体唯一，由后端保证） */
  isPrimary: boolean;
  /** 上位词条 id */
  parentTermId: number | null;
  /** 定义版本号（definition 变更时后端自增） */
  version: number;
  /** 示例值 */
  examples: string[];
  /** 标签 */
  tags: string[];
}

/** 词条保存 DTO（新增 / 编辑共用；编辑时 null 表示不改、parentTermId=0 表示清空、数组 [] 表示清空） */
export interface TermSaveDTO {
  name: string;
  code?: string | null;
  definition?: string | null;
  domain?: string | null;
  owner?: string | null;
  status?: string;
  termType?: string;
  aliases?: string[];
  objectTypeId?: string | null;
  parentTermId?: number | null;
  examples?: string[];
  tags?: string[];
}

/**
 * 词条 ↔ 本体实体绑定 DTO。
 * objectTypeId 为空（null / 空串）= 解绑（后端同时撤下主术语标记）；
 * primary=true = 同时设为主术语（同实体原主术语由后端自动撤下）。
 */
export interface TermBindingDTO {
  objectTypeId: string | null;
  primary: boolean;
}

/** 词条关系边 */
export interface GlossaryRelation {
  id: number;
  fromTermId: number;
  fromTermName: string;
  toTermId: number;
  toTermName: string;
  relationType: string;
  weight: number;
  description: string | null;
  createdBy: string | null;
  createdAt: string;
}

/** 关系边新增 DTO */
export interface RelationSaveDTO {
  fromTermId: number;
  toTermId: number;
  relationType: string;
  weight?: number;
  description?: string;
  createdBy?: string;
}

/** 关系图谱节点 */
export interface GraphNode {
  id: number;
  code: string;
  name: string;
  termType: string;
  domain: string;
  status: string;
  /** 是否为中心词条 */
  center: boolean;
  /** 距中心词条的跳数（中心为 0） */
  hop: number;
}

/** 词条关系图谱 */
export interface GlossaryGraph {
  centerId: number;
  depth: number;
  nodes: GraphNode[];
  edges: GlossaryRelation[];
  nodeCount: number;
  edgeCount: number;
}

/** 词条列表查询参数（均可空） */
export interface GlossaryTermQuery {
  domain?: string;
  status?: string;
  termType?: string;
  objectTypeId?: string;
  keyword?: string;
}

/** 关系列表查询参数（均可空） */
export interface GlossaryRelationQuery {
  fromTermId?: number;
  toTermId?: number;
  relationType?: string;
  /** 任一端命中 */
  termId?: number;
}

/** 兼容旧调用方的列表返回结构 */
export interface GlossaryListResult {
  items: GlossaryTerm[];
  total: number;
}

interface ApiResponse<T> {
  code: number;
  message?: string;
  data?: T;
  timestamp?: number;
}

/**
 * 统一请求封装：带 Bearer token，解 ApiResponse 包装，业务错误透出后端 message。
 */
async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const token = localStorage.getItem("token") || "";
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }
  const res = await fetch(`${API_BASE}${path}`, { headers, ...options });

  // 优先读取响应体，保证业务错误（HTTP 可能仍为 200）能拿到后端 message
  let body: ApiResponse<T> | null = null;
  try {
    body = (await res.json()) as ApiResponse<T>;
  } catch {
    body = null;
  }

  if (body && typeof body.code === "number" && body.code !== 0) {
    throw new Error(body.message || `API error code=${body.code}`);
  }
  if (!res.ok) {
    throw new Error(body?.message || `Glossary API ${path} returned HTTP ${res.status}`);
  }

  return body?.data as T;
}

/** 组装查询串（自动忽略空值） */
function toQueryString(params: Record<string, string | number | undefined>): string {
  const qs = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      qs.set(key, String(value));
    }
  });
  return qs.toString();
}

// ================================================================
// 词条
// ================================================================

/**
 * 查询词条列表（支持 domain/status/termType/objectTypeId/keyword 过滤）。
 */
export async function listGlossaryTerms(params?: GlossaryTermQuery): Promise<GlossaryTerm[]> {
  const query = toQueryString({
    domain: params?.domain,
    status: params?.status,
    termType: params?.termType,
    objectTypeId: params?.objectTypeId,
    keyword: params?.keyword,
  });
  const data = await request<GlossaryTerm[]>(query ? `/terms?${query}` : "/terms");
  return data ?? [];
}

/**
 * 兼容旧调用方的列表接口（返回 { items, total }）。
 */
export async function getGlossaryTerms(params?: GlossaryTermQuery): Promise<GlossaryListResult> {
  const items = await listGlossaryTerms(params);
  return { items, total: items.length };
}

/**
 * 查询指定本体实体已绑定的词条列表。
 *
 * @param entityId 本体实体主键，如 "ent004"
 */
export async function listTermsByEntity(entityId: string): Promise<GlossaryTerm[]> {
  return await listGlossaryTerms({ objectTypeId: entityId });
}

/**
 * 本体工作台术语集成接口（供 GlossaryBindingPanel / TermSearchModal 复用）。
 * 将后端强类型词条映射为 workbench 类型（id 归一为字符串）。
 */
export async function fetchTerms(filters?: GlossaryFilter): Promise<WorkbenchGlossaryTerm[]> {
  const terms = await listGlossaryTerms({
    domain: filters?.domain,
    status: filters?.status,
    keyword: filters?.keyword,
  });
  return terms.map(toWorkbenchTerm);
}

/** 后端词条 → workbench 词条（id 归一为字符串，null 归一为 undefined） */
function toWorkbenchTerm(term: GlossaryTerm): WorkbenchGlossaryTerm {
  return {
    ...term,
    id: String(term.id),
    code: term.code ?? undefined,
    domain: term.domain ?? undefined,
    definition: term.definition ?? undefined,
  };
}

/** 新建词条（后端强制初始状态 DRAFT） */
export async function createGlossaryTerm(data: TermSaveDTO): Promise<GlossaryTerm> {
  return await request<GlossaryTerm>("/terms", {
    method: "POST",
    body: JSON.stringify(data),
  });
}

/** 编辑词条（字段 null 表示不改、parentTermId=0 表示清空、数组 [] 表示清空） */
export async function updateGlossaryTerm(
  id: number,
  data: Partial<TermSaveDTO>,
): Promise<GlossaryTerm> {
  return await request<GlossaryTerm>(`/terms/${id}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
}

/** 删除词条 */
export async function deleteGlossaryTerm(id: number): Promise<void> {
  await request<string>(`/terms/${id}`, { method: "DELETE" });
}

/**
 * 绑定 / 解绑词条到本体实体，并可同时设为主术语。
 *
 * @param termId 词条 id
 * @param objectTypeId 目标实体主键；null / 空串 = 解绑（后端同时撤下主术语标记）
 * @param primary 是否同时设为主术语（同实体原主术语由后端自动撤下）
 */
export async function bindTermToEntity(
  termId: number,
  objectTypeId: string | null,
  primary = false,
): Promise<GlossaryTerm> {
  const body: TermBindingDTO = { objectTypeId, primary };
  return await request<GlossaryTerm>(`/terms/${termId}/binding`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

// ================================================================
// 关系边
// ================================================================

/** 查询词条关系边（termId = 任一端命中） */
export async function listGlossaryRelations(
  params?: GlossaryRelationQuery,
): Promise<GlossaryRelation[]> {
  const query = toQueryString({
    fromTermId: params?.fromTermId,
    toTermId: params?.toTermId,
    relationType: params?.relationType,
    termId: params?.termId,
  });
  const data = await request<GlossaryRelation[]>(query ? `/relations?${query}` : "/relations");
  return data ?? [];
}

/** 新增关系边 */
export async function createGlossaryRelation(dto: RelationSaveDTO): Promise<GlossaryRelation> {
  return await request<GlossaryRelation>("/relations", {
    method: "POST",
    body: JSON.stringify(dto),
  });
}

/** 删除关系边 */
export async function deleteGlossaryRelation(id: number): Promise<void> {
  await request<string>(`/relations/${id}`, { method: "DELETE" });
}

// ================================================================
// 图谱
// ================================================================

/**
 * 拉取词条关系图谱。
 *
 * @param termId 中心词条 id
 * @param depth 展开深度（1~3，默认 1）
 */
export async function fetchGlossaryGraph(termId: number, depth = 1): Promise<GlossaryGraph> {
  const query = toQueryString({ depth });
  return await request<GlossaryGraph>(`/terms/${termId}/graph?${query}`);
}