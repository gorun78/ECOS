/**
 * datanet 元数据 REST 收口 (H6-T2) — 自 src/stores/useWorkbenchStore.ts 迁入，
 * 原语义（无鉴权头、非 2xx 抛 `HTTP ${status}`、返回整包 JSON）不变。
 */
export async function fetchDatanetResourcesAll(): Promise<any> {
  const resp = await fetch("/api/v1/datanet/metadata/resources/all");
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
  return resp.json();
}

export async function fetchDatanetPreview(resourceId: string, limit = 50): Promise<any> {
  const resp = await fetch(`/api/v1/datanet/metadata/preview/${resourceId}?limit=${limit}`);
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
  return resp.json();
}
