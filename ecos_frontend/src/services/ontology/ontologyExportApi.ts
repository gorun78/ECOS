/**
 * 本体导出任务 API（ontologyApi 拆分产物 · W197/R-56 C 批）
 *
 * 域：Ontology Export（T2.2 + T10 落地下载）。
 * 端点前缀：/api/v1/ontology/export。
 * 对外唯一导入入口仍是 `src/services/ontologyApi.ts`（纯 re-export 兼容入口）。
 *
 * 基址单源 + 传输单源（#143/B5-11f）：族根常量由 `API_BASE` 派生（取值与
 * 改前字面量逐字相同），唯一一条私设 `fetch` 腿（`downloadExport`，blob 形态必须
 * 拿未消费的响应体）改经 `rawAuthedUrlFetch` ⇒ 从 `api-base-single-source` 与
 * `privateTransportRatchet` 两份基线同批剪除本文件。
 *
 * #145/B6-12：`EXPORT_BASE` 改名并 **export** 为 `ONTOLOGY_EXPORT_BASE`（同一声明位、
 * 面值逐字不变），作为 `/v1/ontology/export` 族根单源。另一生产方
 * `pages/knowledge/services/overview.ts` 改为取值导入 ⇒ 该族退出双写重复面。
 * 改名而非新增：属主枚数不变，避免历史快照里凭空多一枚未登记的声明。
 */

import { apiFetchData } from "../../api";
import { API_BASE, rawAuthedUrlFetch } from "../httpClient";
import type {
  ExportTaskSummary,
  ExportTask,
  CreateExportDTO,
} from "../../types/ontology";

// ================================================================
// 本体导出 (Ontology Export) — T2.2
// ================================================================

export const ONTOLOGY_EXPORT_BASE = `${API_BASE}/v1/ontology/export`;

export async function fetchExportTasks(params?: { ontologyId?: string; format?: string; status?: string }) {
  const qs = new URLSearchParams();
  if (params?.ontologyId) qs.set("ontologyId", params.ontologyId);
  if (params?.format) qs.set("format", params.format);
  if (params?.status) qs.set("status", params.status);
  const query = qs.toString() ? `?${qs.toString()}` : "";
  // Wave C P0-01: 对齐后端 listExports (OntologyExportController L79 GET /api/v1/ontology/export/tasks);
  // 原 URL 命中 exportFull 的直接导出端点 (返回对象, 非任务列表)
  return apiFetchData<ExportTaskSummary[]>(`${ONTOLOGY_EXPORT_BASE}/tasks${query}`);
}

export async function fetchExportTask(id: string) {
  return apiFetchData<ExportTask>(`${ONTOLOGY_EXPORT_BASE}/${id}`);
}

export async function createExportTask(data: CreateExportDTO) {
  return apiFetchData<ExportTask>(ONTOLOGY_EXPORT_BASE, {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function downloadExport(id: string): Promise<void> {
  // auth 单源（X-51）+ 私设传输收口（#143/B5-11f）：改经 `rawAuthedUrlFetch`，
  // 该通道不预读响应体 ⇒ `res.blob()` 与 `content-disposition` 取名的既有形状逐字保留；
  // 也不设 Content-Type 默认值（原腿同样只带 Authorization）。
  const res = await rawAuthedUrlFetch(`${ONTOLOGY_EXPORT_BASE}/${id}/download`);
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
  await apiFetchData(`${ONTOLOGY_EXPORT_BASE}/${id}`, { method: "DELETE" });
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
  const payload = await apiFetchData<unknown>(`${ONTOLOGY_EXPORT_BASE}/${id}/download`);
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
