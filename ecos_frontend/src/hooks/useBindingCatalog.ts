/**
 * useBindingCatalog — 六类三层绑定词表唯一消费通道（详细设计-07 F07-05 / F07-15）。
 *
 * <p>端点：{@code GET /api/v1/workspace/binding-catalog}（新增，N2 / W169），后端单源下发
 * {@code [{type,tier,labelKey,iconKey,linkableTo[]}]}。前端删除本地 6 常量与向导映射（X-68），
 * 类型 / 三层归属 / 邻接关系一律来自本通道，禁本地兜底常量。</p>
 *
 * <p>auth 单源：经 {@link apiFetchData}（内部只走 {@code getAuthToken()}），禁直读 localStorage。</p>
 *
 * @license Apache-2.0
 */

import { useCallback, useEffect, useState } from 'react';
import { apiFetchData } from '../api';
import { API_BASE } from '../services/httpClient';

/** 三层归属：子图 / 横切 / 出口（铁律 §0.6.1）。 */
export type BindingTier = 'SUBGRAPH' | 'CROSS' | 'OUTPUT';

/** 后端 binding-catalog 单项（契约字段，前端不重命名）。 */
export interface BindingCatalogItem {
  type: string;
  tier: BindingTier;
  /** i18n key（渲染经 t(labelKey)，禁硬编码） */
  labelKey: string;
  /** lucide-react 图标名（前端铁律：图标仅 lucide-react） */
  iconKey: string;
  /** 邻接可达类型集合（与后端 RequiredEdgePolicy 同源） */
  linkableTo: string[];
}

export interface BindingCatalogState {
  items: BindingCatalogItem[];
  loading: boolean;
  error: string | null;
  reload: () => void;
}

const BINDING_CATALOG_URL = `${API_BASE}/v1/workspace/binding-catalog`;

export function useBindingCatalog(opts: { enabled?: boolean } = {}): BindingCatalogState {
  const enabled = opts.enabled ?? true;
  const [items, setItems] = useState<BindingCatalogItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [nonce, setNonce] = useState(0);

  useEffect(() => {
    if (!enabled) return;
    let cancelled = false;
    setLoading(true);
    setError(null);
    apiFetchData<BindingCatalogItem[]>(BINDING_CATALOG_URL)
      .then((d) => {
        if (cancelled) return;
        setItems(Array.isArray(d) ? d : []);
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        setError(e instanceof Error ? e.message : String(e));
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [enabled, nonce]);

  const reload = useCallback(() => setNonce((n) => n + 1), []);
  return { items, loading, error, reload };
}

export default useBindingCatalog;
