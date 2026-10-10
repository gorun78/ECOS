/**
 * 工作台域管理 API（ontologyApi 拆分产物 · W197/R-56 C 批）
 *
 * 域：Workbench Domain CRUD（T8）。
 * 对齐后端 OntologyDomainApiController (@RequestMapping("/api/v1/ontology"))：
 *   GET    /api/v1/ontology/domains         → 域列表
 *   POST   /api/v1/ontology/domains         → 创建域
 *   PUT    /api/v1/ontology/domains/{code}  → 更新域(id 即 domainCode)
 *   DELETE /api/v1/ontology/domains/{code}  → 删除域
 *   PUT    /api/v1/ontology/objects/{id}/domain → 对象归属域变更
 * 后端 T16-2 强类型契约: OntologyDomainVO / OntologyDomainSaveDTO
 * 注: 后端无独立 publish/deprecate 端点; 域状态变更经 PUT status 字段实现
 *     (OntologyDomainVO.status: Draft / Published / Deprecated)
 * 对外唯一导入入口仍是 `src/services/ontologyApi.ts`（纯 re-export 兼容入口）。
 */

import { apiFetchData } from "../../api";

/**
 * 工作台域 VO — 对齐后端 OntologyDomainVO (T16-2)。
 * id 格式 "dom"+序号, code 为唯一业务键。
 */
export interface WorkbenchDomainVO {
  id: string;
  code: string;
  name: string;
  owner?: string;
  description?: string;
  status?: string;
  sortOrder?: number;
  createdAt?: string;
  updatedAt?: string;
}

/** 工作台域新增/编辑 DTO — 对齐后端 OntologyDomainSaveDTO */
export interface WorkbenchDomainSaveDTO {
  code: string;
  name: string;
  owner?: string;
  description?: string;
  status?: string;
}

/** 对象归属域变更结果 — 对齐后端 OntologyDomainReassignVO */
export interface WorkbenchDomainReassignVO {
  entityId: string;
  domainId: string;
  domainCode: string;
  domainName: string;
}

/** 对象归属变更 DTO — 兼容 domainCode / domainId 双字段 */
export interface WorkbenchDomainReassignDTO {
  domainCode?: string;
  domainId?: string;
}

/**
 * 获取工作台域列表
 * GET /api/v1/ontology/domains
 */
export async function fetchWorkbenchDomains(): Promise<WorkbenchDomainVO[]> {
  return apiFetchData<WorkbenchDomainVO[]>("/api/v1/ontology/domains");
}

/**
 * 创建工作台域
 * POST /api/v1/ontology/domains
 */
export async function createWorkbenchDomain(
  dto: WorkbenchDomainSaveDTO
): Promise<WorkbenchDomainVO> {
  return apiFetchData<WorkbenchDomainVO>("/api/v1/ontology/domains", {
    method: "POST",
    body: JSON.stringify(dto),
  });
}

/**
 * 更新工作台域
 * PUT /api/v1/ontology/domains/{code} (id 即 domainCode)
 */
export async function updateWorkbenchDomain(
  code: string,
  dto: WorkbenchDomainSaveDTO
): Promise<WorkbenchDomainVO> {
  return apiFetchData<WorkbenchDomainVO>(
    `/api/v1/ontology/domains/${encodeURIComponent(code)}`,
    { method: "PUT", body: JSON.stringify(dto) }
  );
}

/**
 * 删除工作台域 (含实体时后端拒绝)
 * DELETE /api/v1/ontology/domains/{code}
 */
export async function deleteWorkbenchDomain(code: string): Promise<void> {
  await apiFetchData(`/api/v1/ontology/domains/${encodeURIComponent(code)}`, {
    method: "DELETE",
  });
}

/**
 * 发布域 (状态 → Published)
 * 后端无独立 publish 端点: 经 "拉当前 VO → 合并 status → PUT 回写" 实现。
 * 读-改-写而非直传 status, 避免后端 update 全字段覆盖把 name/description 清掉。
 */
export async function publishWorkbenchDomain(code: string): Promise<WorkbenchDomainVO> {
  return changeWorkbenchDomainStatus(code, "Published");
}

/**
 * 废弃域 (状态 → Deprecated)
 * 后端无独立 deprecate 端点: 实现同 publishWorkbenchDomain。
 */
export async function deprecateWorkbenchDomain(code: string): Promise<WorkbenchDomainVO> {
  return changeWorkbenchDomainStatus(code, "Deprecated");
}

/**
 * 内部: 拉取当前域 VO → 仅改 status 回写 (name/description/owner 原值保留;
 * 空值字段以 undefined 传递 → JSON 省略 → 后端 DTO 反序列化为 null = 不动)。
 */
async function changeWorkbenchDomainStatus(code: string, status: string): Promise<WorkbenchDomainVO> {
  const vos = await listWorkbenchDomains();
  const vo = (vos || []).find((v) => v.code === code || v.id === code);
  if (!vo) {
    throw new Error(`Workbench domain not found: ${code}`);
  }
  const dto: WorkbenchDomainSaveDTO = {
    code: vo.code,
    name: vo.name,
    status,
  };
  if (vo.description) {
    dto.description = vo.description;
  }
  if (vo.owner) {
    dto.owner = vo.owner;
  }
  return updateWorkbenchDomain(vo.code, dto);
}

/** 域列表别名 — 与 fetchWorkbenchDomains 同源 (status 读改写的内部取词) */
function listWorkbenchDomains(): Promise<WorkbenchDomainVO[]> {
  return fetchWorkbenchDomains();
}

/**
 * 变更对象归属域
 * PUT /api/v1/ontology/objects/{id}/domain
 */
export async function reassignObjectDomain(
  objectId: string,
  dto: WorkbenchDomainReassignDTO
): Promise<WorkbenchDomainReassignVO> {
  return apiFetchData<WorkbenchDomainReassignVO>(
    `/api/v1/ontology/objects/${encodeURIComponent(objectId)}/domain`,
    { method: "PUT", body: JSON.stringify(dto) }
  );
}
