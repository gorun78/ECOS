/**
 * IAM / Tenant 域 API 切片 — E4.1 域拆分第 1 片 (P0 顶层共因)。
 *
 * 源: 原单源 `src/api.ts` L1494-1751 逐行抽出; `src/api.ts` 通过 `export *` re-export
 * 保持 168 个 importer `from "./api"` 契约零改动 (barrel 层)。
 *
 * 本域 = `/api/v1/system/**` 用户/角色/组织/权限/租户 共 30 export (7 interface + 23 function)。
 *
 * 依赖面: 仅 `apiFetchData` (src/services/httpClient) — 与其它 api_* 域共用同一传输原语。
 */
import { apiFetchData } from "./services/httpClient";

// ── IAM: User & Role & Org & Permission Management ──────────

export interface IamUser {
  userId: string; username: string; realName: string;
  email?: string; phone?: string; orgId?: string;
  status: string; locked: string;
  lastLoginTime?: string; createdTime?: string;
}
export interface IamRole {
  roleId: string; roleName: string; roleCode: string;
  description?: string; roleType: string; status: string;
  tenantId?: string; parentRoleId?: string;
  createdTime?: string; updatedTime?: string;
}
export interface IamOrg {
  orgId: string; orgName: string; orgCode: string;
  parentOrgId?: string; orgType: string; description?: string;
  status: string; remark?: string; path?: string;
  createdTime?: string;
  children?: IamOrg[];
}
export interface IamPermission {
  permissionId: string; resource: string; action: string;
  description?: string; conditionExpr?: string;
}

// Users
export async function fetchUsers(keyword?: string, page=1, pageSize=20): Promise<{data:IamUser[], total:number}> {
  try {
    const q = keyword ? `&keyword=${encodeURIComponent(keyword)}` : '';
    return await apiFetchData(`/api/v1/system/users?page=${page}&pageSize=${pageSize}${q}`);
  } catch (e) { console.warn("fetchUsers failed", e); return { data: [], total: 0 }; }
}
export async function createUser(body: Record<string,any>) {
  return apiFetchData('/api/v1/system/users', { method:'POST', body:JSON.stringify(body) });
}
export async function updateUser(id:string, body:Record<string,any>) {
  return apiFetchData(`/api/v1/system/users/${id}`, { method:'PUT', body:JSON.stringify(body) });
}
export async function deleteUser(id:string) {
  return apiFetchData(`/api/v1/system/users/${id}`, { method:'DELETE' });
}
export async function resetPassword(id:string, password:string) {
  return apiFetchData(`/api/v1/system/users/${id}/password`, { method:'PUT', body:JSON.stringify({password}) });
}
export async function toggleUserStatus(id:string, status:string) {
  return apiFetchData(`/api/v1/system/users/${id}/status`, { method:'PUT', body:JSON.stringify({status}) });
}
/** Fetch organizations for a specific user */
export async function fetchUserOrganizations(userId:string): Promise<IamOrg[]> {
  try { return await apiFetchData<IamOrg[]>(`/api/v1/system/users/${userId}/organizations`); }
  catch (e) { console.warn("fetchUserOrganizations failed", e); return []; }
}

// Roles
export async function fetchRoles(): Promise<{data:IamRole[], total:number}> {
  try { return await apiFetchData('/api/v1/system/roles'); }
  catch (e) { console.warn("fetchRoles failed", e); return { data: [], total: 0 }; }
}
export async function createRole(body:Record<string,any>) {
  return apiFetchData('/api/v1/system/roles', { method:'POST', body:JSON.stringify(body) });
}
export async function updateRole(id:string, body:Record<string,any>) {
  return apiFetchData(`/api/v1/system/roles/${id}`, { method:'PUT', body:JSON.stringify(body) });
}
export async function deleteRole(id:string) {
  return apiFetchData(`/api/v1/system/roles/${id}`, { method:'DELETE' });
}

// Organizations
export async function fetchOrgs(): Promise<IamOrg[]> {
  try { return await apiFetchData('/api/v1/system/organizations/all'); }
  catch (e) { console.warn("fetchOrgs failed", e); return []; }
}
/** Fetch organization tree — returns nested children structure.
 *  Backend returns single root node wrapped in ApiResponse.data (Map, not Array).
 *  Normalize to IamOrg[] so callers can safely use .map(). */
export async function fetchOrgTree(): Promise<IamOrg[]> {
  try {
    const data = await apiFetchData<any>('/api/v1/system/organizations/tree');
    if (Array.isArray(data)) return data;
    return data ? [data] : [];
  } catch (e) { console.warn("fetchOrgTree failed", e); return []; }
}
export async function createOrg(body:Record<string,any>) {
  return apiFetchData('/api/v1/system/organizations', { method:'POST', body:JSON.stringify(body) });
}

// Permissions
export async function fetchPermissions(): Promise<IamPermission[]> {
  try { return await apiFetchData<IamPermission[]>('/api/v1/system/permissions'); }
  catch (e) { console.warn("fetchPermissions: backend unavailable", e); return []; }
}
export async function createPermission(body: Record<string,any>) {
  return apiFetchData('/api/v1/system/permissions', { method:'POST', body:JSON.stringify(body) });
}
export async function updatePermission(id:string, body:Record<string,any>) {
  return apiFetchData(`/api/v1/system/permissions/${id}`, { method:'PUT', body:JSON.stringify(body) });
}
export async function deletePermission(id:string) {
  return apiFetchData(`/api/v1/system/permissions/${id}`, { method:'DELETE' });
}

// User-Role assignments
export async function fetchUserRoles(userId:string): Promise<string[]> {
  try {
    const data = await apiFetchData<any[]>(`/api/v1/system/users/${userId}/roles`);
    // API returns [{roleId, roleName, roleCode}], extract roleId strings
    if (Array.isArray(data) && data.length > 0 && typeof data[0] === 'object') {
      return data.map(r => r.roleId).filter(Boolean);
    }
    return data as string[];
  }
  catch (e) { console.warn("fetchUserRoles failed", e); return []; }
}
export async function assignUserRoles(userId:string, roleIds:string[]): Promise<void> {
  return apiFetchData(`/api/v1/system/users/${userId}/roles`, { method:'PUT', body:JSON.stringify({roleIds}) });
}

// Role-Permission assignments
export async function fetchRolePermissions(roleId:string): Promise<string[]> {
  try { return await apiFetchData<string[]>(`/api/v1/system/roles/${roleId}/permissions`); }
  catch (e) { console.warn("fetchRolePermissions failed", e); return []; }
}
export async function assignRolePermissions(roleId:string, permissionIds:string[]): Promise<void> {
  return apiFetchData(`/api/v1/system/roles/${roleId}/permissions`, { method:'PUT', body:JSON.stringify({permissionIds}) });
}

// Organization update/delete
export async function updateOrg(id:string, body:Record<string,any>) {
  return apiFetchData(`/api/v1/system/organizations/${id}`, { method:'PUT', body:JSON.stringify(body) });
}
export async function deleteOrg(id:string) {
  return apiFetchData(`/api/v1/system/organizations/${id}`, { method:'DELETE' });
}

// ── User Management Enhanced APIs (T3a) ──────────────────────

/** POST /api/v1/system/users/{id}/force-logout — 强制用户下线 */
export async function forceLogoutUser(id: string) {
  return apiFetchData(`/api/v1/system/users/${id}/force-logout`, { method: "POST" });
}

/** POST /api/v1/system/users/{id}/reset-password — 重置密码 (生成临时密码) */
export async function resetPasswordGenerate(id: string): Promise<{ tempPassword: string }> {
  return apiFetchData(`/api/v1/system/users/${id}/reset-password`, { method: "POST" });
}

/** POST /api/v1/system/users/batch — CSV批量导入用户 */
export async function batchCreateUsers(users: Record<string, any>[]) {
  return apiFetchData("/api/v1/system/users/batch", {
    method: "POST",
    body: JSON.stringify({ users }),
  });
}

// ── Tenant Management (对接 TenantController) ─────────────────
// 后端端点: /api/v1/system/tenants

export interface TenantInfo {
  tenantId: string;
  tenantName: string;
  contactName?: string;
  contactEmail?: string;
  contactPhone?: string;
  status: string;
  quotaTypes?: number;
  todayUsage?: number;
  createdTime?: string;
  updatedTime?: string;
}

export interface TenantQuota {
  quotaType: string;
  dailyLimit: number;
  monthlyLimit: number;
  usedToday?: number;
}

export interface TenantQuotaUpdateRequest {
  quota_type: string;
  daily_limit: number;
  monthly_limit: number;
}

/** GET /api/v1/system/tenants — 分页查询租户列表 */
export async function fetchTenants(
  keyword?: string,
  page = 1,
  pageSize = 20
): Promise<{ data: TenantInfo[]; total: number }> {
  try {
    const params = new URLSearchParams();
    if (keyword) params.set("keyword", keyword);
    params.set("page", String(page));
    params.set("pageSize", String(pageSize));
    return await apiFetchData<{ data: TenantInfo[]; total: number }>(
      `/api/v1/system/tenants?${params.toString()}`
    );
  } catch (e) {
    console.warn("fetchTenants: backend unavailable", e);
    return { data: [], total: 0 };
  }
}

/** POST /api/v1/system/tenants — 创建租户 */
export async function createTenant(body: {
  tenantName: string;
  contactName?: string;
  contactEmail?: string;
  contactPhone?: string;
}): Promise<TenantInfo> {
  return apiFetchData<TenantInfo>("/api/v1/system/tenants", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

/** PUT /api/v1/system/tenants/{tenantId} — 更新租户 */
export async function updateTenant(
  tenantId: string,
  body: Partial<TenantInfo>
): Promise<TenantInfo> {
  return apiFetchData<TenantInfo>(`/api/v1/system/tenants/${tenantId}`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}

/** DELETE /api/v1/system/tenants/{tenantId} — 删除租户 */
export async function deleteTenant(tenantId: string): Promise<void> {
  await apiFetchData(`/api/v1/system/tenants/${tenantId}`, { method: "DELETE" });
}

/** GET /api/v1/system/tenants/{tenantId}/quota — 获取租户配额 */
export async function fetchTenantQuota(
  tenantId: string
): Promise<{ data: TenantQuota[]; total: number }> {
  try {
    return await apiFetchData<{ data: TenantQuota[]; total: number }>(
      `/api/v1/system/tenants/${tenantId}/quota`
    );
  } catch (e) {
    console.warn("fetchTenantQuota: backend unavailable", e);
    return { data: [], total: 0 };
  }
}

/** PUT /api/v1/system/tenants/{tenantId}/quota — 更新租户配额 */
export async function updateTenantQuota(
  tenantId: string,
  body: TenantQuotaUpdateRequest
): Promise<TenantQuota> {
  return apiFetchData<TenantQuota>(`/api/v1/system/tenants/${tenantId}/quota`, {
    method: "PUT",
    body: JSON.stringify(body),
  });
}
