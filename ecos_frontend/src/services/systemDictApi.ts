/**
 * system dict REST 收口 (H6-T2) — `/api/v1/system/dict/{type}`
 * 自 src/hooks/useDict.ts 迁入，解析与归一语义不变。
 *
 * 私设传输收口（#143/B5-11f）：唯一一条腿改经单源 `rawAuthedUrlFetch`（带 Bearer、
 * 不解析响应体、无 401/403 会话副作用）⇒ 「不判 `res.ok` 直接 `res.json()`」和
 * 「`code !== 0 || !data` 折叠成空数组」的既有降级形状逐字保留；基址由 `API_BASE`
 * 派生（api-base 单源门禁）⇒ 从 `api-base-single-source` 与 `privateTransportRatchet`
 * 两份基线同批剪除本文件。
 *
 * 合流等价性证据（Bearer 归一守卫为何可以消失）：原腿写
 * `token.startsWith('Bearer ') ? token : \`Bearer ${token}\``，而单源与
 * `services/auth#authHeaders` 一律无条件拼 `Bearer ${token}`。全仓 token 写入点只有
 * `pages/Login.tsx` 两处（写 `data.accessToken` 原值 / `token.trim()`），实测零处
 * `setItem(..., 'Bearer ...')` ⇒ 该守卫在本应用没有生产者，属死分支，合流后逐字等价。
 *
 * 同批观察（未改动，避免夹带语义变更）：`dictType` 直接插值未做百分号编码，与
 * `services/datanetMetadataApi.ts` 的 `resourceId` 同型，并入 #108 队列。
 */
import { API_BASE, rawAuthedUrlFetch } from "./httpClient";

export interface DictItem {
  dictCode: string;
  dictLabel: string;
  dictLabelEn: string;
  extValue?: string;
  sortOrder: number;
}

export async function fetchSystemDictItems(dictType: string): Promise<DictItem[]> {
  const res = await rawAuthedUrlFetch(`${API_BASE}/v1/system/dict/${dictType}`, {
    headers: { 'Content-Type': 'application/json' },
  });
  const json = await res.json();
  if (json.code !== 0 || !json.data) return [];
  return (json.data || []).map((d: any) => ({
    dictCode: d.dictCode,
    dictLabel: d.dictLabel,
    dictLabelEn: d.dictLabelEn || d.dictLabel,
    extValue: d.extValue || undefined,
    sortOrder: d.sortOrder || 0,
  }));
}
