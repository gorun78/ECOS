/**
 * 本体导出任务 API（ontologyApi 拆分产物 · W197/R-56 C 批）
 *
 * 域：Ontology Export（T2.2 + T10 落地下载）。
 * 端点前缀：/api/v1/ontology/export。
 * 对外唯一导入入口仍是 `src/services/ontologyApi.ts`（纯 re-export 兼容入口）。
 */

import { apiFetchData } from "../../api";
import { getAuthToken } from "../auth";
import type {
  ExportTaskSummary,
  ExportTask,
  CreateExportDTO,
} from "../../types/ontology";

// ================================================================
// 本体导出 (Ontology Export) — T2.2
// ================================================================

const EXPORT_BASE = "/api/v1/ontology/export";

export async function fetchExportTasks(params?: { ontologyId?: string; format?: string; status?: string }) {
  const qs = new URLSearchParams();
  if (params?.ontologyId) qs.set("ontologyId", params.ontologyId);
  if (params?.format) qs.set("format", params.format);
  if (params?.status) qs.set("status", params.status);
  const query = qs.toString() ? `?${qs.toString()}` : "";
  // Wave C P0-01: 对齐后端 listExports (OntologyExportController L79 GET /api/v1/ontology/export/tasks);
  // 原 URL 命中 exportFull 的直接导出端点 (返回对象, 非任务列表)
  return apiFetchData<ExportTaskSummary[]>(`${EXPORT_BASE}/tasks${query}`);
}

export async function fetchExportTask(id: string) {
  return apiFetchData<ExportTask>(`${EXPORT_BASE}/${id}`);
}

export async function createExportTask(data: CreateExportDTO) {
  return apiFetchData<ExportTask>(EXPORT_BASE, {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function downloadExport(id: string): Promise<void> {
  // auth 单源（X-51）
  const token = getAuthToken();
  const headers: Record<string, string> = {};
  if (token) headers["Authorization"] = `Bearer ${token}`;
  const res = await fetch(`${EXPORT_BASE}/${id}/download`, { headers });
  if (!res.ok) throw new Error(`Download failed: HTTP ${res.status}`);
  const blob = await res.blob();
  const contentDisposition = res.headers.get("content-disposition") || "";
  const match = contentDisposition.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/);
  const filename = match ? match[1].replace(/['"]/g, "") : `ontology-export-${id}`;
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

export async function deleteExportTask(id: string) {
  await apiFetchData(`${EXPORT_BASE}/${id}`, { method: "DELETE" });
}

// ================================================================
// 本体导出任务落地下载 — T10
// 对齐后端 OntologyExportController GET /api/v1/ontology/export/{id}/download
// 响应为统一返回体 ApiResponse<Object>（payload 随 format: JSON Map / CSV 串 / DDL 串），
// 非裸 Blob 流 — 需解包 data 字段后再触发浏览器文件保存。
// ================================================================

/** 导出任务下载文件扩展名（format → 扩展名，unknown 回退 txt） */
const EXT_FOR_FORMAT: Record<string, string> = {
  JSON: "json",
  CSV: "csv",
  DDL: "sql",
};

/**
 * 下载 COMPLETED 导出任务的 payload 并落地为本地文件。
 * GET /api/v1/ontology/export/{id}/download
 * 仅 COMPLETED 任务可下载（后端 ONT-EXP-002 校验），失败由 apiFetchData 统一转抛。
 *
 * @param id      导出任务 ID
 * @param format  导出格式（用于推断文件扩展名）
 * @returns 保存后的文件名
 */
export async function downloadExportTask(id: string, format?: string): Promise<string> {
  const payload = await apiFetchData<unknown>(`${EXPORT_BASE}/${id}/download`);
  const ext = EXT_FOR_FORMAT[String(format || "").toUpperCase()] || "txt";
  const content =
    typeof payload === "string" ? payload : JSON.stringify(payload ?? {}, null, 2);
  const filename = `ontology-export-${id}.${ext}`;

  const blob = new Blob([content], {
    type: format === "CSV" ? "text/csv" : "application/octet-stream",
  });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
  return filename;
}
