/**
 * VersionTimeline — 版本历史时间线 + 快照 diff 对比面板
 *
 * 右侧滑出面板，展示版本列表（GET /api/v1/ecos/versions，后端真实数据）。
 * 单击版本卡 → vs 前一版本；双击版本卡 → 设为对比基线（自选两两对比）。
 *
 * T11 联调策略（前后端契约铁律，快照 raw 动态结构豁免对齐 T16-4）：
 * 1. 版本元数据：两侧版本详情 GET /api/v1/ecos/versions/{id} 拉全量 snapshot
 *    （快照 preview 展示 + 本地降级 diff 数据源）
 * 2. diff 消费源收口（T11-P1）：
 *    - 后端成功：GET /api/v1/ontology/versions/diff?v1=..&v2=..
 *      （VersionDiffController，T16-4 强类型 OntologyVersionDiffVO）
 *      返回有效条目列表 → 直接消费后端 added/removed/modified（标签+明细同源）
 *    - 后端失败/空 → 降级本地 computeSnapshotDiff（对齐后端 diffField/
 *      diffModified 同语义，集合类字段下钻到元素粒度），前端标注来源
 *
 * @license Apache-2.0
 */

import React, { useEffect, useState, useCallback } from 'react';
import {
  X, Clock, User, GitBranch, Plus, Minus, Edit3,
  ChevronDown, ChevronRight, Loader2, AlertCircle, History, GitCompareArrows,
} from 'lucide-react';
import { useLanguage } from '../../components/LanguageContext';
import { useTheme } from '../../components/ThemeContext';
import type { ThemeStyles } from '../../components/ThemeContext';
import {
  fetchVersions, fetchVersionDetail, fetchOntologyVersionDiff,
} from '../../services/ontologyApi';
import type { VersionItem, VersionDiff, VersionDiffEntry } from '../../services/ontologyApi';

// ── 类型 ──────────────────────────────────────────────────────

interface VersionTimelineProps {
  domainCode: string;
  onClose: () => void;
}

/** 快照动态结构 — JSONB 快照 Object 豁免（T16-4 同语义） */
type JsonObj = Record<string, unknown>;

// ── 纯函数 helpers ────────────────────────────────────────────

/** i18n 插值辅助 */
function ti(t: (k: string) => string, key: string, vars: Record<string, string> = {}): string {
  let s = t(key);
  for (const [k, v] of Object.entries(vars)) s = s.replace(`{${k}}`, v);
  return s;
}

/** ISO 日期 → 本地日期显示（非法值降级截断） */
function fmtDate(iso: string): string {
  try {
    return new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: '2-digit', day: '2-digit' });
  } catch { return iso.slice(0, 10); }
}

/** 安全取键（无键返回 undefined，对齐后端 Map.containsKey 语义） */
function safeGet(obj: JsonObj, key: string): unknown {
  return Object.prototype.hasOwnProperty.call(obj, key) ? obj[key] : undefined;
}

function isObj(v: unknown): v is JsonObj {
  return typeof v === 'object' && v !== null && !Array.isArray(v);
}

/** 序列化为可比对字符串（null → ''，其余 JSON/原生） */
function valStr(v: unknown): string {
  if (v === undefined || v === null) return '';
  if (typeof v === 'object') return JSON.stringify(v);
  return String(v);
}

/** diff 条目计数 */
function diffCounts(d: VersionDiff): { added: number; removed: number; modified: number } {
  return {
    added: d.added?.length ?? 0,
    removed: d.removed?.length ?? 0,
    modified: d.modified?.length ?? 0,
  };
}

// ── diff 计算（对齐后端 VersionDiffController 语义） ──────────

/** 集合类字段（后端 generateSnapshot 输出键）下钻到元素粒度 */
const COLLECTION_KEYS = ['entities', 'relationships', 'actions'];

/** 元素身份键（id/code 优先，缺失时用索引），用于集合下钻的元素匹配 */
function identityOf(item: unknown, idx: number): string {
  if (isObj(item)) {
    if (item.id !== undefined) return `id:${String(item.id)}`;
    if (item.code !== undefined) return `code:${String(item.code)}`;
  }
  return `idx:${idx}`;
}

/** 元素展示标签：name/code/id 优先 */
function itemLabel(item: unknown): string {
  if (isObj(item)) {
    if (item.name) return String(item.name);
    if (item.code) return String(item.code);
    if (item.id) return String(item.id);
  }
  if (item === null || item === undefined) return '';
  return valStr(item).slice(0, 40);
}

/**
 * 从两个快照计算 added/removed/modified（与后端 diffField/diffModified 同语义）
 * 顶层字段逐 key 比对；集合类字段下钻：元素整体增/删，元素内叶子属性增/删/改。
 */
function computeSnapshotDiff(s1Obj: JsonObj, s2Obj: JsonObj): VersionDiff {
  const added: VersionDiffEntry[] = [];
  const removed: VersionDiffEntry[] = [];
  const modified: VersionDiffEntry[] = [];

  const allKeys = new Set([...Object.keys(s1Obj), ...Object.keys(s2Obj)]);
  for (const key of allKeys) {
    const v1 = safeGet(s1Obj, key);
    const v2 = safeGet(s2Obj, key);
    if (v1 === undefined && v2 !== undefined) { added.push({ field: key, value: v2 }); continue; }
    if (v2 === undefined && v1 !== undefined) { removed.push({ field: key, value: v1 }); continue; }
    if (valStr(v1) === valStr(v2)) continue;

    // 集合类字段下钻：双方都是数组 → 元素粒度比对，否则整字段计为修改
    if (COLLECTION_KEYS.includes(key) && Array.isArray(v1) && Array.isArray(v2)) {
      const map1 = new Map<string, unknown>();
      (v1 as unknown[]).forEach((it, i) => map1.set(identityOf(it, i), it));
      const map2 = new Map<string, unknown>();
      (v2 as unknown[]).forEach((it, i) => map2.set(identityOf(it, i), it));
      // removed: v1 有 v2 无（元素整体）
      for (const [k, it] of map1) {
        if (!map2.has(k)) removed.push({ field: `${key} · ${itemLabel(it) || k}`, value: it });
      }
      // added: v2 有 v1 无（元素整体）
      for (const [k, it] of map2) {
        if (!map1.has(k)) added.push({ field: `${key} · ${itemLabel(it)}`, value: it });
      }
      // modified: 元素叶子属性逐键比对
      for (const [k, it1] of map1) {
        const it2 = map2.has(k) ? map2.get(k) : undefined;
        if (it2 === undefined) continue;
        const leaf1: JsonObj = isObj(it1) ? it1 : {};
        const leaf2: JsonObj = isObj(it2) ? it2 : {};
        for (const lk of new Set([...Object.keys(leaf1), ...Object.keys(leaf2)])) {
          const lv1 = safeGet(leaf1, lk);
          const lv2 = safeGet(leaf2, lk);
          if (valStr(lv1) === valStr(lv2)) continue;
          const label = itemLabel(it1);
          modified.push({ field: `${key} · ${label} → ${lk}`, value: lv1, newValue: lv2 });
        }
      }
    } else {
      modified.push({ field: key, value: v1, newValue: v2 });
    }
  }
  return { added, removed, modified };
}

// ── 展示组件（动态 JSON 值用 Object 映射 + 截断，T16-4 raw 豁免） ──

/** 单个值展示（对象 → 键值摘要，数组 → 计数，标量 → 文本截断） */
function DiffValue({ v, styles }: { v: unknown; styles: ThemeStyles }) {
  if (v === undefined || v === null) {
    return <span className={`opacity-50 ${styles.muted}`}>null</span>;
  }
  if (typeof v === 'object') {
    if (Array.isArray(v)) {
      return <span className="font-mono">[{v.length}]</span>;
    }
    const entries = Object.entries(v).slice(0, 3);
    return (
      <span className="font-mono break-all">
        {entries.map(([k, val]) => `${k}=${typeof val === 'object' ? '{…}' : String(val)} `)}
      </span>
    );
  }
  const text = String(v);
  return <span className="font-mono break-all">{text.length > 200 ? text.slice(0, 200) + ' …' : text}</span>;
}

/** diff 条目行：field 头 + 值（modified 带 old/new 两行） */
function DiffEntryRow({ entry, t, styles }: { entry: VersionDiffEntry; t: (k: string) => string; styles: ThemeStyles }) {
  return (
    <div className={`px-2 py-1 rounded ${styles.sidebarHoverBg}`}>
      <div className={`text-[10px] font-medium ${styles.cardText}`}>{entry.field}</div>
      {entry.newValue !== undefined ? (
        <div className="mt-0.5 space-y-0.5">
          <div className="flex items-baseline gap-1 min-w-0">
            <span className={`text-[9px] font-mono uppercase tracking-wider opacity-50 shrink-0 ${styles.muted}`}>{t('ow.version.diff.old')}</span>
            <span className="min-w-0 truncate"><DiffValue v={entry.value} styles={styles} /></span>
          </div>
          <div className="flex items-baseline gap-1 min-w-0">
            <span className={`text-[9px] font-mono uppercase tracking-wider opacity-50 shrink-0 ${styles.muted}`}>{t('ow.version.diff.new')}</span>
            <span className="min-w-0 truncate"><DiffValue v={entry.newValue} styles={styles} /></span>
          </div>
        </div>
      ) : (
        <div className="mt-0.5 truncate"><DiffValue v={entry.value} styles={styles} /></div>
      )}
    </div>
  );
}

/** diff 三组小节（空组不渲染） */
function DiffSection({ title, count, entries, icon, iconCls, t, styles }: {
  title: string;
  count: number;
  entries: VersionDiffEntry[];
  icon: React.ReactNode;
  iconCls: string;
  t: (k: string) => string;
  styles: ThemeStyles;
}) {
  if (count === 0) return null;
  return (
    <div className="space-y-1">
      <div className="flex items-center gap-1.5">
        <span className={iconCls}>{icon}</span>
        <span className={`text-xs font-semibold ${styles.cardText}`}>{title}</span>
        <span className={`text-[9px] px-1.5 py-0.5 rounded font-mono ${iconCls}`}>{count}</span>
      </div>
      <div className="space-y-1 pl-1">
        {entries.map((e, i) => <DiffEntryRow key={i} entry={e} t={t} styles={styles} />)}
      </div>
    </div>
  );
}

/** 快照 JSON preview（动态 Object 映射，超长按钮展开） */
function SnapshotPreview({ label, data, styles, expanded, onToggle }: {
  label: string;
  data: unknown;
  styles: ThemeStyles;
  expanded: boolean;
  onToggle: () => void;
}) {
  const text = data === undefined || data === null ? '—' : JSON.stringify(data, null, 2);
  const display = expanded || text.length <= 600 ? text : text.slice(0, 600) + '\n…';
  return (
    <div className={`rounded border ${styles.cardBorder} overflow-hidden`}>
      <button onClick={onToggle}
        className={`w-full flex items-center gap-1.5 px-2 py-1 text-left transition ${styles.sidebarHoverBg}`}>
        {expanded ? <ChevronDown size={12} className={styles.muted} /> : <ChevronRight size={12} className={styles.muted} />}
        <span className={`text-[10px] font-mono uppercase tracking-wider ${styles.muted}`}>{label}</span>
        <span className={`ml-auto font-mono text-[9px] ${styles.muted}`}>{text.length}B</span>
      </button>
      <pre className={`px-2 pb-2 font-mono text-[10px] whitespace-pre-wrap break-all ${styles.muted}`}>
        {display}
      </pre>
    </div>
  );
}

// ── 主组件 ────────────────────────────────────────────────────

export default function VersionTimeline({ domainCode, onClose }: VersionTimelineProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const [versions, setVersions] = useState<VersionItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [listError, setListError] = useState<string | null>(null);

  // diff 状态
  const [selected, setSelected] = useState<VersionItem | null>(null);
  const [baseId, setBaseId] = useState<string | null>(null);
  const [diffData, setDiffData] = useState<VersionDiff | null>(null);
  /** diff 消费来源：backend=后端 diff 端点成功直用 / local=本地降级计算 */
  const [diffSource, setDiffSource] = useState<'backend' | 'local'>('local');
  const [diffLoading, setDiffLoading] = useState(false);
  const [diffError, setDiffError] = useState<string | null>(null);
  const [snapExpanded, setSnapExpanded] = useState(false);

  /** 加载版本列表（后端跨全部 ontology，本地按创建时间倒序） */
  const loadVersions = useCallback(() => {
    setLoading(true);
    setListError(null);
    fetchVersions(domainCode)
      .then((list) => {
        const arr = [...(list || [])].sort((a, b) => (b.createdAt || '').localeCompare(a.createdAt || ''));
        setVersions(arr);
      })
      .catch((err) => setListError(err instanceof Error ? err.message : t('ontology.version.loadFailed')))
      .finally(() => setLoading(false));
  }, [domainCode, t]);

  useEffect(() => { loadVersions(); }, [loadVersions]);

  /** 解析对比对：显式 baseId 优先，否则取列表中的前一版本（时间序） */
  const resolveBase = useCallback((target: VersionItem, explicitBaseId: string | null): VersionItem | null => {
    const idx = versions.findIndex((v) => v.id === target.id);
    const auto = idx > 0 ? versions[idx - 1] : null;
    if (explicitBaseId) {
      const found = versions.find((v) => v.id === explicitBaseId);
      if (found && found.id !== target.id) return found;
    }
    return auto;
  }, [versions]);

  /**
   * 拉取两侧版本详情并产出 diff；消费源优先级（T11-P1）：
   * 后端 diff 端点成功且有有效条目 → 直用后端三组列表；
   * 后端失败/空列表 → 本地 computeSnapshotDiff 降级（含集合下钻）。
   */
  const runDiff = useCallback(async (target: VersionItem, base: VersionItem | null) => {
    setDiffLoading(true);
    setDiffError(null);
    setDiffData(null);
    setDiffSource('local');
    setSnapExpanded(false);
    try {
      const details = await Promise.all(
        (base ? [target.id, base.id] : [target.id]).map((id) => fetchVersionDetail(id))
      );
      const tgt = details[0];
      const s1: JsonObj = base ? (isObj(details[1].snapshot) ? details[1].snapshot : {}) : {};
      const s2: JsonObj = isObj(tgt.snapshot) ? tgt.snapshot : {};

      // 后端 diff 端点：成功且有有效条目 → 后端直用（标签+明细同源，消除语义偏差）
      let backendDiff: VersionDiff | null = null;
      if (base) {
        try {
          const bres = await fetchOntologyVersionDiff(base.id, target.id);
          const hasEntries =
            (bres.added?.length ?? 0) + (bres.removed?.length ?? 0) + (bres.modified?.length ?? 0) > 0;
          if (hasEntries) {
            backendDiff = {
              version1: bres.version1,
              version2: bres.version2,
              added: bres.added ?? [],
              removed: bres.removed ?? [],
              modified: bres.modified ?? [],
            };
          }
        } catch (e) {
          // 4xx/网络异常留痕，降级本地快照对比（详情接口成功已证明快照可获取）
          console.warn('T11: backend diff failed, fallback to local compute', e);
        }
      }

      if (backendDiff) {
        // 后端粒度直用：条目即后端 VO 语义（整字段粒度直接使用，不做前端二次下钻）
        setDiffSource('backend');
        setDiffData({
          ...backendDiff,
          snapshot1: s1,
          snapshot2: s2,
        });
      } else {
        // 本地降级：computeSnapshotDiff（null-value key 边界 + 集合下钻与后端存在已知偏差）
        setDiffSource('local');
        setDiffData({
          version1: base?.versionNo,
          version2: target.versionNo,
          snapshot1: s1,
          snapshot2: s2,
          ...computeSnapshotDiff(s1, s2),
        });
      }
    } catch (err) {
      setDiffError(err instanceof Error ? err.message : t('ow.version.diff.loading_failed'));
    } finally {
      setDiffLoading(false);
    }
  }, [t]);

  /** 单击版本卡 → 与基线对比（无自定义基线时 = 时间序前一版本） */
  const handleSelect = useCallback((item: VersionItem) => {
    const idx = versions.findIndex((v) => v.id === item.id);
    const prev = idx > 0 ? versions[idx - 1] : null;
    const nextBaseId = baseId && baseId !== item.id ? baseId : prev?.id ?? null;
    setSelected(item);
    setBaseId(nextBaseId);
    runDiff(item, nextBaseId ? versions.find((v) => v.id === nextBaseId) ?? prev : prev);
  }, [baseId, versions, runDiff]);

  /** 双击版本卡 → 设为对比基线（再次双击同一卡取消）并重跑对比 */
  const handleSetBase = useCallback((item: VersionItem) => {
    if (!selected || selected.id === item.id) return;
    const nextBaseId = baseId === item.id ? null : item.id;
    setBaseId(nextBaseId);
    runDiff(selected, nextBaseId ? item : resolveBase(selected, null));
  }, [baseId, selected, runDiff, resolveBase]);

  /** 面板 diff 标题（含计数徽标） */
  const counts = diffData ? diffCounts(diffData) : null;

  return (
    <div className="fixed inset-0 z-50">
      {/* 半透明遮罩 */}
      <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" onClick={onClose} />

      {/* 右侧滑出面板 */}
      <div className={`absolute right-0 top-0 h-full w-[480px] max-w-[90vw] border-l ${styles.cardBorder} ${styles.cardBg} shadow-2xl flex flex-col overflow-hidden animate-in slide-in-from-right`}>
        {/* 标题栏 */}
        <div className={`flex items-center justify-between px-4 py-3 border-b ${styles.cardBorder} shrink-0`}>
          <div className="flex items-center gap-2">
            <History size={16} className={styles.accentText} />
            <span className={`text-sm font-semibold ${styles.cardText}`}>{t('ontology.version.title')}</span>
            {versions.length > 0 && (
              <span className={`text-[10px] px-1.5 py-0.5 rounded ${styles.badgeBg} ${styles.badgeText}`}>{versions.length}</span>
            )}
          </div>
          <button onClick={onClose} title={t('ontology.version.close')}
            className={`p-1 rounded transition ${styles.sidebarHoverBg} ${styles.muted}`}>
            <X size={16} />
          </button>
        </div>

        {/* 版本列表区 */}
        <div className="flex-1 overflow-y-auto">
          {loading && (
            <div className="flex items-center justify-center py-16">
              <Loader2 size={24} className={`animate-spin ${styles.accentText}`} />
            </div>
          )}

          {!loading && listError && (
            <div className="flex flex-col items-center gap-3 py-16 px-8">
              <AlertCircle size={28} className={styles.dangerText} />
              <p className={`text-xs text-center ${styles.dangerText}`}>{listError}</p>
              <button onClick={loadVersions}
                className={`px-3 py-1.5 rounded-lg text-xs transition ${styles.sidebarHoverBg} ${styles.cardText}`}>
                {t('ontology.version.retry')}
              </button>
            </div>
          )}

          {!loading && !listError && versions.length === 0 && (
            <div className="flex flex-col items-center gap-3 py-16">
              <Clock size={28} className={styles.muted} />
              <p className={`text-xs ${styles.muted}`}>{t('ontology.version.noVersions')}</p>
            </div>
          )}

          {!loading && !listError && versions.length > 0 && (
            <div className="p-3 space-y-2">
              <div className={`px-1 text-[10px] ${styles.muted}`}>{t('ow.version.hint')}</div>
              {versions.map((item) => {
                const isSelected = selected?.id === item.id;
                const isBase = baseId === item.id && selected?.id !== item.id;
                return (
                  <button key={item.id}
                    onClick={() => handleSelect(item)}
                    onDoubleClick={() => handleSetBase(item)}
                    className={`w-full text-left px-3 py-2.5 rounded-lg border transition ${
                      isSelected
                        ? `border-l-2 ${styles.accentBorder} ${styles.sidebarActiveBg}`
                        : isBase
                          ? `${styles.cardBorder} ${styles.sidebarActiveBg} opacity-90`
                          : `${styles.cardBorder} ${styles.sidebarHoverBg}`
                    }`}>
                    <div className="flex items-center gap-2 mb-1">
                      <GitBranch size={13} className={isSelected || isBase ? styles.accentText : styles.muted} />
                      <span className={`text-xs font-semibold ${isSelected ? styles.accentText : styles.cardText}`}>
                        v{item.versionNo}
                      </span>
                      {isBase && (
                        <span className={`flex items-center gap-0.5 text-[9px] ${styles.muted}`}>
                          <GitCompareArrows size={10} />
                          {t('ow.version.base')}
                        </span>
                      )}
                      {item.status && (
                        <span className={`ml-auto text-[9px] uppercase tracking-wider px-1.5 py-0.5 rounded ${
                          item.status === 'Published' ? `${styles.successBg} ${styles.successText}`
                            : item.status === 'Deprecated' ? `${styles.dangerBg} ${styles.dangerText}`
                              : `${styles.warningBg} ${styles.warningText}`
                        }`}>
                          {item.status}
                        </span>
                      )}
                    </div>
                    <div className={`flex items-center gap-3 text-[10px] ${styles.muted}`}>
                      <span className="flex items-center gap-1">
                        <Clock size={10} />{fmtDate(item.createdAt)}
                      </span>
                      {item.publisher && (
                        <span className="flex items-center gap-1">
                          <User size={10} />{item.publisher}
                        </span>
                      )}
                    </div>
                  </button>
                );
              })}
            </div>
          )}
        </div>

        {/* diff 面板 */}
        {selected && (
          <div className={`border-t ${styles.cardBorder} shrink-0 max-h-[45%] overflow-y-auto`}>
            <div className={`flex items-center justify-between px-4 py-2.5 border-b ${styles.cardBorder} ${styles.sidebarBg}`}>
              <span className={`text-xs font-medium ${styles.cardText}`}>
                {baseId && baseId !== selected.id
                  ? ti(t, 'ow.version.diff.baseExplicit', { version: `v${versions.find((v) => v.id === baseId)?.versionNo ?? baseId}` })
                  : ti(t, 'ontology.version.compareWith', { version: `v${selected.versionNo}` })}
              </span>
              <GitCompareArrows size={13} className={styles.muted} />
            </div>

            <div className="p-3">
              {diffLoading && (
                <div className="flex items-center gap-2 py-6 justify-center">
                  <Loader2 size={16} className={`animate-spin ${styles.accentText}`} />
                  <span className={`text-xs ${styles.muted}`}>{t('ow.version.diff.loading')}</span>
                </div>
              )}

              {diffError && (
                <div className={`flex items-start gap-2 px-3 py-2.5 rounded-lg ${styles.dangerBg} border ${styles.dangerBorder}`}>
                  <AlertCircle size={13} className={`${styles.dangerText} mt-0.5 shrink-0`} />
                  <p className={`text-xs ${styles.dangerText}`}>{diffError}</p>
                </div>
              )}

              {!diffLoading && !diffError && diffData && (
                <div className="space-y-3">
                  {counts && counts.added === 0 && counts.removed === 0 && counts.modified === 0 ? (
                    <p className={`text-xs ${styles.muted} text-center py-4`}>{t('ow.version.diff.no_diff')}</p>
                  ) : (
                    <>
                      {counts && (
                        <div className={`flex items-center justify-between gap-2 text-[10px] font-mono uppercase tracking-wider ${styles.muted}`}>
                          <span className="min-w-0 truncate">
                            {ti(t, 'ow.version.diff.countSuffix',
                              { added: String(counts.added), removed: String(counts.removed), modified: String(counts.modified) })}
                          </span>
                          <span className={`shrink-0 px-1.5 py-0.5 rounded ${styles.sidebarHoverBg}`}>
                            {diffSource === 'backend' ? t('ow.version.diff.source_backend') : t('ow.version.diff.source_local')}
                          </span>
                        </div>
                      )}
                      <DiffSection
                        title={t('ow.version.diff.added')} count={counts.added}
                        entries={diffData.added ?? []}
                        icon={<Plus size={12} />} iconCls={styles.successText} t={t} styles={styles}
                      />
                      <DiffSection
                        title={t('ow.version.diff.removed')} count={counts.removed}
                        entries={diffData.removed ?? []}
                        icon={<Minus size={12} />} iconCls={styles.dangerText} t={t} styles={styles}
                      />
                      <DiffSection
                        title={t('ow.version.diff.modified')} count={counts.modified}
                        entries={diffData.modified ?? []}
                        icon={<Edit3 size={12} />} iconCls={styles.warningText} t={t} styles={styles}
                      />
                      <div className="space-y-2 pt-1">
                        <SnapshotPreview
                          label={diffData.version1
                            ? ti(t, 'ow.version.snapshot', { v: String(diffData.version1) })
                            : t('ow.version.label.base')}
                          data={diffData.snapshot1}
                          styles={styles} expanded={snapExpanded}
                          onToggle={() => setSnapExpanded((p) => !p)}
                        />
                        <SnapshotPreview
                          label={ti(t, 'ow.version.snapshot', { v: String(diffData.version2) })}
                          data={diffData.snapshot2}
                          styles={styles} expanded={snapExpanded}
                          onToggle={() => setSnapExpanded((p) => !p)}
                        />
                      </div>
                    </>
                  )}
                </div>
              )}

              {!diffLoading && !diffError && !diffData && (
                <p className={`text-xs ${styles.muted} text-center py-4`}>
                  {t('ow.version.diff.no_diff')}
                </p>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
