/**
 * system dict REST 收口 (H6-T2) — /api/v1/system/dict/{type}
 * 自 src/hooks/useDict.ts 迁入，解析与归一语义不变。
 */
import { getAuthToken } from "./auth";

export interface DictItem {
  dictCode: string;
  dictLabel: string;
  dictLabelEn: string;
  extValue?: string;
  sortOrder: number;
}

export async function fetchSystemDictItems(dictType: string): Promise<DictItem[]> {
  const token = getAuthToken();
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = token.startsWith('Bearer ') ? token : `Bearer ${token}`;
  const res = await fetch(`/api/v1/system/dict/${dictType}`, { headers });
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
