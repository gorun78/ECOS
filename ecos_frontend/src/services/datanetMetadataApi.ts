/**
 * datanet 元数据 REST 收口 (H6-T2) — 自 src/stores/useWorkbenchStore.ts 迁入，
 * 原语义（无鉴权头、非 2xx 抛 `HTTP ${status}`、返回整包 JSON）不变。
 *
 * <p>**传输合流（#143/B5-11e）**：两条腿改走单源匿名通道 `rawAnonUrlFetch`
 * （C2′「匿名腿不算豁免」判据，见台账 §13.2.4 C2′ 注⑬），基址由 `API_BASE` 派生，
 * 两条 URL 逐字不变；同时从 `api-base-single-source` 基线剪除本文件。
 * 抛错文案保持域内自造的 `HTTP ${status}`（单源 `apiFetchEnvelope` 会先解包并透出
 * 信封字段，但改前这两条腿读的是**整包 JSON** 且没有信封概念 ⇒ 换通道即换解包语义，
 * 超出传输收口授权）。</p>
 *
 * <p>**D-B5-36 同族扩展（本批源级 + 配置级取证，未起服 curl、未 live 复验）**：
 * 后端映射**实存**——data-engine-impl `MetadataController` 双路径声明
 * `{"/api/datanet/metadata", "/api/v1/datanet/metadata"}`，其下
 * `@GetMapping("/resources/all")` 与 `@GetMapping("/preview/{resourceId}")` 各自在位。
 * `AnonymousEndpointRegistry` 零条 `/api/v1/datanet` ⇒ 不带 `Authorization` 的两条腿
 * 走 gateway（dev 下 `/api/v1/datanet` 亦无 directInDev 条目，经 `/api` 兜底进 gateway）
 * 必 401。修法与 D-B5-36 同批待裁决。</p>
 *
 * <p>**同批观察（原语义保持，未改动）**：`fetchDatanetPreview` 的 `resourceId`
 * 未做百分号编码 ⇒ 含 `/` 或 `..` 的 id 会改写请求路径本身。是否收口为
 * `encodeURIComponent` 属接口行为变化，交 #108/DW 域队列裁决，本批不动。</p>
 */
import { API_BASE, rawAnonUrlFetch } from './httpClient';

export async function fetchDatanetResourcesAll(): Promise<any> {
  const resp = await rawAnonUrlFetch(`${API_BASE}/v1/datanet/metadata/resources/all`);
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
  return resp.json();
}

export async function fetchDatanetPreview(resourceId: string, limit = 50): Promise<any> {
  const resp = await rawAnonUrlFetch(`${API_BASE}/v1/datanet/metadata/preview/${resourceId}?limit=${limit}`);
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
  return resp.json();
}
