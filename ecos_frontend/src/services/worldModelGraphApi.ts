/**
 * 世界模型 REST 收口 (H6-T2，#143/B5-11a/b 改单源) — 原语义保持：调用方可传
 * `errorMessage`（缺省时透出单源错误信息），消费面 `json.data || json` 兼容
 * （`apiFetchData` 已解一层 `data`，解包后对象无 `data` 键 ⇒ 回落取整包）。
 *
 * 承流现状（2026-10-19 实测，登记见差距分析台账 D-B5-33，勿据此宣布功能可用）：
 * 网关匿名面 `AnonymousEndpointRegistry.permitAllPatterns()` 零条 world-model 登记
 * + `anyRequest().authenticated()` ⇒ 迁移前本文件不带 `Authorization`，五腿全部
 * 401；改走单源后补上 Bearer，`/causal-graph`（sysman `WorldModelGraphController:296`
 * 活映射）方为可达，其余四腿（`/state`、GET `/scenarios`、POST `/scenarios`、
 * POST `/strategy/recommend`）在本前缀下零映射 ⇒ 转为显式 404 未承流。
 * cognitive 侧存在同名但不同前缀的显式 501 stub（`WorldModelController`
 * @ `/api/v1/world-model`，`data.stub=true`），是否把腿改挂过去属入口定档，
 * 触 R-52① ⇒ 待产品裁决，本批不动 URL。
 */
import { apiFetchData } from "./httpClient";
import { ECOS_WORLD_MODEL_GRAPH_BASE } from "../api/monitorClient";

async function getJson(url: string, errorMessage?: string): Promise<any> {
  try {
    return await apiFetchData(url);
  } catch (e) {
    if (errorMessage) throw new Error(errorMessage, { cause: e });
    throw e;
  }
}

async function postJson(url: string, errorMessage?: string, body?: unknown): Promise<any> {
  try {
    // 不传 headers：单源 `{ headers, ...options }` 形态下自定义头会整组替换掉
    // Authorization（D-B5-34），Content-Type 单源已默认设置。
    return await apiFetchData(url, body === undefined
      ? { method: 'POST' }
      : { method: 'POST', body: JSON.stringify(body) });
  } catch (e) {
    if (errorMessage) throw new Error(errorMessage, { cause: e });
    throw e;
  }
}

export function fetchWorldStateJson(errorMessage?: string): Promise<any> {
  return getJson(`${ECOS_WORLD_MODEL_GRAPH_BASE}/state`, errorMessage);
}

export function fetchScenariosJson(errorMessage?: string): Promise<any> {
  return getJson(`${ECOS_WORLD_MODEL_GRAPH_BASE}/scenarios`, errorMessage);
}

export function fetchCausalGraphJson(errorMessage?: string): Promise<any> {
  return getJson(`${ECOS_WORLD_MODEL_GRAPH_BASE}/causal-graph`, errorMessage);
}

export function postSimulationJson(scenario: Record<string, unknown>, errorMessage?: string): Promise<any> {
  return postJson(`${ECOS_WORLD_MODEL_GRAPH_BASE}/scenarios`, errorMessage, scenario);
}

export function postStrategyRecommendJson(goal: string, errorMessage?: string): Promise<any> {
  return postJson(`${ECOS_WORLD_MODEL_GRAPH_BASE}/strategy/recommend?goal=${encodeURIComponent(goal)}`, errorMessage);
}
