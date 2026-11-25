/**
 * dictConfigClient.ts — src/api.ts 按域拆分产物（design-05 B.9-1 / X-50 / F05-17）。
 * 经 src/api.ts re-export shim 保持既有 from "../api" 导入面零破坏。
 */
import { API_BASE, apiFetch, apiFetchData } from "../services/httpClient";

// ── System Config Manager ────────────────────────────────────

export interface SysConfigItem {
  key: string;
  value: string;
  label: string;
  labelZh: string;
  description: string;
  descriptionZh: string;
  group: string;
  type: 'string' | 'number' | 'boolean' | 'json';
  options?: string[];
  impactScope?: string;
  edition?: string;
  default_value?: string;
  isConsumed?: boolean;
  consumedBy?: string;
}

export interface SysConfigGrouped {
  group: string;
  groupLabel: string;
  groupLabelZh: string;
  items: SysConfigItem[];
}

/** GET /api/v1/system/config — fetch all system configs, optionally filtered by group */
export async function fetchSysConfigs(group?: string): Promise<SysConfigGrouped[]> {
  try {
    const qs = group ? `?group=${encodeURIComponent(group)}` : '';
    const resp = await apiFetch<{ success: boolean; data: { data: any[]; total: number } }>(`/v1/system/config${qs}`);
    const items: any[] = resp?.data?.data || [];
    // Map backend flat items → SysConfigItem
    const mapped: SysConfigItem[] = items.map((it: any) => ({
      key: it.config_key || it.key,
      value: String(it.config_value ?? it.value ?? ''),
      label: it.config_label || it.label || it.config_key,
      labelZh: it.config_label || it.labelZh || it.config_key,
      description: it.description || '',
      descriptionZh: it.description || '',
      group: it.config_group || it.group || 'global',
      type: (it.config_type || it.type || 'string') as SysConfigItem['type'],
      options: it.config_options || it.options || undefined,
      impactScope: it.impact_scope || it.impactScope || '',
      edition: it.edition || 'all',
      default_value: it.default_value || '',
      isConsumed: it.is_consumed || it.isConsumed || false,
      consumedBy: it.consumed_by || it.consumedBy || '',
    }));
    // Group by config_group
    const groupMap: Record<string, SysConfigItem[]> = {};
    for (const item of mapped) {
      const g = item.group;
      if (!groupMap[g]) groupMap[g] = [];
      groupMap[g].push(item);
    }
    return Object.entries(groupMap).map(([g, items]) => ({
      group: g,
      groupLabel: g,
      groupLabelZh: g,
      items,
    }));
  } catch (e) {
    console.warn('fetchSysConfigs: backend unavailable', e);
    return [];
  }
}

/**
 * 后端 /api/v1/system/config 的原始行（2026-11-25 live 实测字段）。
 * 与 `SysConfigItem` 的区别：这里是**传输形态**，字段名/类型都按后端原样（snake_case、
 * type 大小写混写、default_value 可为 null），归一交给页面侧纯函数做。
 */
export interface SysConfigRow {
  id?: string;
  config_group?: string;
  config_key?: string;
  config_value?: string | number | boolean | null;
  config_type?: string;
  config_label?: string;
  config_label_en?: string;
  description?: string;
  sort_order?: number;
  config_options?: unknown;
  impact_scope?: string | null;
  edition?: string | null;
  default_value?: string | number | boolean | null;
}

/** 审计口单条消费态（只有这个接口给 is_consumed / consumed_by）。 */
export interface ConfigConsumptionEntry {
  config_key: string;
  config_group?: string;
  is_consumed?: boolean;
  consumed_by?: string | null;
  suggestion?: string | null;
}

/**
 * GET /api/v1/system/config — **fail-loud** 版读取（与 fetchSysConfigs 的吞错返空相对）。
 *
 * 为什么不复用 fetchSysConfigs：那个函数 `catch → return []`，会把「后端 500 / 断网」
 * 伪装成「平台没有任何配置」（前端规范 §五 fail-open 禁令）。平台治理页要让运维看见
 * 故障本身，故单独走一条上抛腿；fetchSysConfigs 保持原状不动（其消费方在别处）。
 */
export async function fetchSysConfigRowsStrict(group?: string): Promise<SysConfigRow[]> {
  const qs = group ? `?group=${encodeURIComponent(group)}` : '';
  const resp = await apiFetch<{ data?: { data?: SysConfigRow[] } }>(`/v1/system/config${qs}`);
  const rows = resp?.data?.data;
  if (!Array.isArray(rows)) {
    // 信封形态不符也是故障：宁可报错，也别让页面渲染成「空配置」
    throw new Error('GET /v1/system/config: unexpected payload shape');
  }
  return rows;
}

/**
 * GET /api/v1/system/config/audit — 配置消费态（fail-loud）。
 * 返回 Map<config_key, entry>，key 与配置行同源，页面据此外显「已消费 / 未检测到消费」。
 */
export async function fetchConfigConsumptionStrict(): Promise<Map<string, ConfigConsumptionEntry>> {
  const resp = await apiFetch<{ data?: { items?: ConfigConsumptionEntry[] } }>('/v1/system/config/audit');
  const items = resp?.data?.items;
  if (!Array.isArray(items)) {
    throw new Error('GET /v1/system/config/audit: unexpected payload shape');
  }
  const map = new Map<string, ConfigConsumptionEntry>();
  for (const it of items) {
    if (it && typeof it.config_key === 'string' && it.config_key) map.set(it.config_key, it);
  }
  return map;
}

/** PUT /api/v1/system/config/{key} — update a single config value */
export async function updateSysConfig(key: string, value: string): Promise<SysConfigItem> {
  return apiFetch<SysConfigItem>(`/v1/system/config/${encodeURIComponent(key)}`, {
    method: 'PUT',
    body: JSON.stringify({ value }),
  });
}

/** PUT /api/v1/sysconfig/{key}/reset — reset a config to its default value */
export async function resetSysConfig(key: string): Promise<SysConfigItem> {
  return apiFetch<SysConfigItem>(`/v1/sysconfig/${encodeURIComponent(key)}/reset`, {
    method: 'PUT',
  });
}

/** GET /api/v1/system/config/audit — config consumption audit */
export interface SysConfigAuditItem {
  key: string;
  label: string;
  value: string;
  consumed: boolean;
  consumedBy: string;
  consumedAt: string;
}

export async function fetchConfigAudit(): Promise<SysConfigAuditItem[]> {
  try {
    const resp = await apiFetch<{ success: boolean; data: SysConfigAuditItem[] }>('/v1/system/config/audit');
    return resp?.data || [];
  } catch (e) {
    console.warn('fetchConfigAudit: backend unavailable', e);
    return [];
  }
}


// ── System Dict (字典项管理) ────────────────────────────

export interface DictType {
  dictType: string;
  dictName: string;
  description?: string;
  status?: string;
  subsystem?: string;
  itemCount?: number;
}

export interface DictItem {
  id: number;
  dictType: string;
  dictCode: string;
  dictLabel: string;
  dictLabelEn?: string;
  sortOrder: number;
  status: string;
  parentCode?: string;
  extValue?: string;
  createdAt: string;
  updatedAt: string;
}

/** 基址由 httpClient#API_BASE 派生（W206/C188 基址单源；改前此处私设字面前缀） */
const DICT_BASE = `${API_BASE}/v1/system/dict`;

/** GET /api/v1/system/dict/types — 获取所有字典类型 */
export async function listDictTypes(): Promise<DictType[]> {
  return apiFetchData(`${DICT_BASE}/types`);
}

/** GET /api/v1/system/dict/{type} — 获取某类型下的所有字典项 */
export async function getDictItems(dictType: string): Promise<DictItem[]> {
  return apiFetchData(`${DICT_BASE}/${encodeURIComponent(dictType)}`);
}

/** POST /api/v1/system/dict — 创建字典项 */
export async function createDictItem(body: {
  dictType: string;
  dictCode: string;
  extValue?: string;
  dictLabel: string;
  status?: string;
  sortOrder?: number;
}): Promise<DictItem> {
  return apiFetchData(`${DICT_BASE}`, {
    method: 'POST',
    body: JSON.stringify(body),
  });
}

/** PUT /api/v1/system/dict/{type}/{code} — 更新字典项 */
export async function updateDictItem(
  dictType: string,
  dictCode: string,
  body: {
    extValue?: string;
    dictLabel?: string;
    status?: string;
    sortOrder?: number;
  }
): Promise<DictItem> {
  return apiFetchData(
    `${DICT_BASE}/${encodeURIComponent(dictType)}/${encodeURIComponent(dictCode)}`,
    {
      method: 'PUT',
      body: JSON.stringify(body),
    }
  );
}

/** DELETE /api/v1/system/dict/{type}/{code} — 删除字典项 */
export async function deleteDictItem(
  dictType: string,
  dictCode: string
): Promise<void> {
  await apiFetchData(
    `${DICT_BASE}/${encodeURIComponent(dictType)}/${encodeURIComponent(dictCode)}`,
    { method: 'DELETE' }
  );
}

/** GET /api/v1/system/dict/subsystems — 按G1-G5子系统分组 */
export async function fetchDictSubsystems(): Promise<Record<string, DictType[]>> {
  return apiFetchData(`${DICT_BASE}/subsystems`);
}

/** GET /api/v1/system/dict/{type}/usage — 字典审计：哪些模块使用了该字典 */
export async function fetchDictUsage(dictType: string): Promise<{
  dictType: string;
  itemCount: number;
  usedByModules: { subsystem: string; module: string }[];
  usedByCount: number;
}> {
  return apiFetchData(`${DICT_BASE}/${encodeURIComponent(dictType)}/usage`);
}
