/**
 * 本体引擎运维 API（ontologyApi 拆分产物 · W197/R-56 C 批）
 *
 * 域：引擎健康/启停/配置、工作流、图谱查询、Copilot、引擎侧 Git 同步。
 * 端点前缀：/api/v1/engine/ontology（`ONTOLOGY_ENGINE_BASE`）。
 * 对外唯一导入入口仍是 `src/services/ontologyApi.ts`（纯 re-export 兼容入口）。
 *
 * <p>**#145/B6-9 属主上收**：本文件的 `ENGINE_BASE` 原为文件私有，与
 * `pages/MonitoringCenter.tsx` 的 `ENGINES` 行内腿各写一份
 * `` `${API_BASE}/v1/engine/ontology` `` ⇒ `/v1/engine/ontology` 一族两属主（双形态棘轮在册项）。
 * 现把它改名并 `export` 为族根单源，页面侧经门面 `services/ontologyApi.ts` 取值。三点口径：
 * ① **属主就是原属主之一**（与 #145/B6-4 同判据）：本文件处于分层底部，页面引它不成环；
 * 门面 `export * from "./ontology/ontologyEngineApi"` 那行不含 URL 体 ⇒ **门面不是第二属主**
 * （#145/B6-5 ② 的别名重导出同款）。
 * ② **声明位不增不减**：只是私有升导出，同一枚 `` `${API_BASE}/v1/engine/ontology` ``，
 * 族根体在生产面仍恰 1 条 ⇒ 族留在棘轮族索引里、只从 dupBoth 退出，再冒第二个裸面写手即红。
 * ③ **本文件其余 25 枚腿一律走属主常量**（`${ONTOLOGY_ENGINE_BASE}/…`），它们对棘轮
 * `DER_RE` 隐形＝单源的定义，不是漏收。</p>
 */

import { apiFetchData } from "../../api";
import { API_BASE } from "../httpClient";

export const ONTOLOGY_ENGINE_BASE = `${API_BASE}/v1/engine/ontology`;

// ================================================================
// 引擎状态 / 启停
// ================================================================

export async function fetchEngineHealth() {
  return apiFetchData(`${ONTOLOGY_ENGINE_BASE}/health`);
}

/** GET /api/v1/engine/ontology/health — 本体引擎公开健康 VO（fetchEngineHealth 的同端点别名，显式语义命名）。 */
export async function fetchOntologyEngineHealth(): Promise<Record<string, unknown>> {
  return fetchEngineHealth() as Promise<Record<string, unknown>>;
}

export async function fetchEngineStatus() {
  return apiFetchData(`${ONTOLOGY_ENGINE_BASE}/status`);
}

export async function startEngine() {
  return apiFetchData(`${ONTOLOGY_ENGINE_BASE}/start`, { method: "POST" });
}

export async function stopEngine() {
  return apiFetchData(`${ONTOLOGY_ENGINE_BASE}/stop`, { method: "POST" });
}

// ================================================================
// 引擎配置
// ================================================================

export async function fetchEngineSettings() {
  return apiFetchData(`${ONTOLOGY_ENGINE_BASE}/settings`);
}

export async function fetchEngineSettingsDefaults() {
  return apiFetchData(`${ONTOLOGY_ENGINE_BASE}/settings/defaults`);
}

export async function updateEngineSettings(updates: Array<{ config_key: string; config_value: string }>) {
  return apiFetchData(`${ONTOLOGY_ENGINE_BASE}/settings`, {
    method: "PUT",
    body: JSON.stringify(updates),
  });
}

export async function refreshEngineSettings() {
  return apiFetchData(`${ONTOLOGY_ENGINE_BASE}/settings/refresh`, { method: "POST" });
}

// ================================================================
// 工作流（引擎统一入口）
// ================================================================

export async function fetchWorkflowDefinitions(pageSize = 50) {
  return apiFetchData(`${ONTOLOGY_ENGINE_BASE}/workflow/definitions?pageSize=${pageSize}`);
}

export async function createWorkflowDefinition(data: Record<string, unknown>) {
  return apiFetchData(`${ONTOLOGY_ENGINE_BASE}/workflow/definitions`, {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function fetchWorkflowInstances(limit = 50) {
  return apiFetchData(`${ONTOLOGY_ENGINE_BASE}/workflow/instances?limit=${limit}`);
}

export async function startWorkflowInstance(data: { workflowId: string; [k: string]: unknown }) {
  return apiFetchData(`${ONTOLOGY_ENGINE_BASE}/workflow/instances`, {
    method: "POST",
    body: JSON.stringify(data),
  });
}

export async function fetchWorkflowInstance(id: string) {
  return apiFetchData(`${ONTOLOGY_ENGINE_BASE}/workflow/instances/${id}`);
}

export async function approveWorkflow(id: string, body: Record<string, unknown> = {}) {
  return apiFetchData(`${ONTOLOGY_ENGINE_BASE}/workflow/instances/${id}/approve`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export async function rejectWorkflow(id: string, body: Record<string, unknown> = {}) {
  return apiFetchData(`${ONTOLOGY_ENGINE_BASE}/workflow/instances/${id}/reject`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

// ================================================================
// 图谱查询（引擎统一入口）
// ================================================================

export async function fetchOntologyGraph(ontologyId: string) {
  return apiFetchData(`${ONTOLOGY_ENGINE_BASE}/graph/${ontologyId}`);
}

export async function fetchFullGraph() {
  return apiFetchData(`${ONTOLOGY_ENGINE_BASE}/graph/full`);
}

export async function fetchNodeTrace(nodeId: string) {
  return apiFetchData(`${ONTOLOGY_ENGINE_BASE}/graph/trace/${nodeId}`);
}

// ================================================================
// Copilot（AI辅助建模）
// ================================================================

export async function copilotSuggestEntity(prompt: string, schemaInfo = "") {
  return apiFetchData(`${ONTOLOGY_ENGINE_BASE}/copilot/entity`, {
    method: "POST",
    body: JSON.stringify({ prompt, schemaInfo }),
  });
}

export async function copilotSuggestRelation(prompt: string, schemaInfo = "") {
  return apiFetchData(`${ONTOLOGY_ENGINE_BASE}/copilot/relation`, {
    method: "POST",
    body: JSON.stringify({ prompt, schemaInfo }),
  });
}

export async function copilotValidateConsistency(schemaInfo: string) {
  return apiFetchData(`${ONTOLOGY_ENGINE_BASE}/copilot/validate`, {
    method: "POST",
    body: JSON.stringify({ schemaInfo }),
  });
}

export async function copilotReverseImport(schemaInfo: string) {
  return apiFetchData(`${ONTOLOGY_ENGINE_BASE}/copilot/import`, {
    method: "POST",
    body: JSON.stringify({ schemaInfo }),
  });
}

// ================================================================
// Git 版本管理（引擎侧同步端点；平台级 Git 单通道见 services/gitService.ts，
// 规范 §十一.1 —— 本组端点属 ontology-engine 自有 /engine/ontology/git/*，非
// /api/v1/ecos/git 面，不违反单通道纪律）
// ================================================================

export async function commitToGit(ontologyId: string, message: string) {
  return apiFetchData(`${ONTOLOGY_ENGINE_BASE}/git/commit/${ontologyId}`, {
    method: "POST",
    body: JSON.stringify({ message }),
  });
}

export async function pullFromGit(ontologyId: string) {
  return apiFetchData(`${ONTOLOGY_ENGINE_BASE}/git/pull/${ontologyId}`, {
    method: "POST",
    body: JSON.stringify({}),
  });
}

export async function loadFromGit(url: string) {
  return apiFetchData(`${ONTOLOGY_ENGINE_BASE}/git/load`, {
    method: "POST",
    body: JSON.stringify({ url }),
  });
}
