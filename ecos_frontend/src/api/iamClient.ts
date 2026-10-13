/**
 * iamClient.ts — src/api.ts 按域拆分产物（design-05 B.9-1 / X-50 / F05-17）。
 * 经 src/api.ts re-export shim 保持既有 from "../api" 导入面零破坏。
 */
import { apiFetchData } from "../services/httpClient";

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
  const q = keyword ? `&keyword=${encodeURIComponent(keyword)}` : '';
  // 禁吞错返回空数组（fail-open 假空态）——错误上抛，由调用方错误分支呈现
  return apiFetchData(`/api/v1/system/users?page=${page}&pageSize=${pageSize}${q}`);
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
  return apiFetchData<IamOrg[]>(`/api/v1/system/users/${userId}/organizations`);
}

// Roles
export async function fetchRoles(): Promise<{data:IamRole[], total:number}> {
  return apiFetchData('/api/v1/system/roles');
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
  return apiFetchData('/api/v1/system/organizations/all');
}
/** Fetch organization tree — returns nested children structure.
 *  Backend returns single root node wrapped in ApiResponse.data (Map, not Array).
 *  Normalize to IamOrg[] so callers can safely use .map(). */
export async function fetchOrgTree(): Promise<IamOrg[]> {
  const data = await apiFetchData<any>('/api/v1/system/organizations/tree');
  if (Array.isArray(data)) return data;
  return data ? [data] : [];
}
export async function createOrg(body:Record<string,any>) {
  return apiFetchData('/api/v1/system/organizations', { method:'POST', body:JSON.stringify(body) });
}

// Permissions
export async function fetchPermissions(): Promise<IamPermission[]> {
  return apiFetchData<IamPermission[]>('/api/v1/system/permissions');
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
  const data = await apiFetchData<any[]>(`/api/v1/system/users/${userId}/roles`);
  // API returns [{roleId, roleName, roleCode}], extract roleId strings
  if (Array.isArray(data) && data.length > 0 && typeof data[0] === 'object') {
    return data.map(r => r.roleId).filter(Boolean);
  }
  return data as string[];
}
export async function assignUserRoles(userId:string, roleIds:string[]): Promise<void> {
  return apiFetchData(`/api/v1/system/users/${userId}/roles`, { method:'PUT', body:JSON.stringify({roleIds}) });
}

// Role-Permission assignments
export async function fetchRolePermissions(roleId:string): Promise<string[]> {
  return apiFetchData<string[]>(`/api/v1/system/roles/${roleId}/permissions`);
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

/** GET /api/v1/system/tenants — 分页查询租户列表（status 为可选精确过滤，由后端 SQL WHERE 分支承接） */
export async function fetchTenants(
  keyword?: string,
  page = 1,
  pageSize = 20,
  status?: string
): Promise<{ data: TenantInfo[]; total: number }> {
  const params = new URLSearchParams();
  if (keyword) params.set("keyword", keyword);
  if (status) params.set("status", status);
  params.set("page", String(page));
  params.set("pageSize", String(pageSize));
  // 禁吞错返回空列表——错误上抛，由 TenantManager 既有 try/catch 错误分支呈现
  return apiFetchData<{ data: TenantInfo[]; total: number }>(
    `/api/v1/system/tenants?${params.toString()}`
  );
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
  return apiFetchData<{ data: TenantQuota[]; total: number }>(
    `/api/v1/system/tenants/${tenantId}/quota`
  );
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

