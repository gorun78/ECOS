import { useState, useEffect, useCallback, useRef } from 'react';

export interface DictItem {
  dictCode: string;
  dictLabel: string;
  dictLabelEn: string;
  extValue?: string;
  sortOrder: number;
}

// 全局缓存
const globalCache = new Map<string, DictItem[]>();
const pendingRequests = new Map<string, Promise<DictItem[]>>();

export async function fetchDictItems(dictType: string): Promise<DictItem[]> {
  const cached = globalCache.get(dictType);
  if (cached) return cached;
  const pending = pendingRequests.get(dictType);
  if (pending) return pending;

  const promise = (async () => {
    const token = localStorage.getItem('token') || '';
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
  })();

  pendingRequests.set(dictType, promise);
  promise.finally(() => pendingRequests.delete(dictType));
  return promise;
}

export function useDict(dictType: string, locale?: string) {
  const [items, setItems] = useState<DictItem[]>(() => globalCache.get(dictType) || []);
  const [loading, setLoading] = useState(!globalCache.has(dictType));
  const [error, setError] = useState<string | null>(null);
  const mountedRef = useRef(true);

  useEffect(() => { mountedRef.current = true; return () => { mountedRef.current = false; }; }, []);

  useEffect(() => {
    if (globalCache.has(dictType)) { setItems(globalCache.get(dictType)!); setLoading(false); return; }
    setLoading(true);
    fetchDictItems(dictType).then(data => {
      if (mountedRef.current) { setItems(data); setLoading(false); }
    }).catch(e => {
      if (mountedRef.current) { setError(e.message); setLoading(false); }
    });
  }, [dictType]);

  const isEn = locale === 'en';

  const getLabel = useCallback((code: string, fallback?: string) => {
    const item = items.find(i => i.dictCode === code);
    if (!item) return fallback || code;
    return isEn ? item.dictLabelEn : item.dictLabel;
  }, [items, isEn]);

  const getColor = useCallback((code: string, fallback?: string) => {
    const item = items.find(i => i.dictCode === code);
    return item?.extValue || fallback || '#6b7280';
  }, [items]);

  const getOptions = useCallback(() => {
    return [...items].sort((a, b) => a.sortOrder - b.sortOrder).map(i => ({
      value: i.dictCode,
      label: isEn ? i.dictLabelEn : i.dictLabel,
    }));
  }, [items, isEn]);

  return { items, loading, error, getLabel, getColor, getOptions };
}

export function preloadDicts(...types: string[]) {
  types.forEach(t => fetchDictItems(t).catch(() => {}));
}
