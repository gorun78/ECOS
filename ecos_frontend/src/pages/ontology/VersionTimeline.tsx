/**
 * VersionTimeline — 版本历史时间线 + diff 展开面板
 *
 * 右侧滑出面板，展示域版本列表，点击卡片展开 diff 详情。
 * ≤300 行
 *
 * @license Apache-2.0
 */

import React, { useEffect, useState, useCallback } from 'react';
import {
  X, Clock, User, GitBranch, Plus, Minus, Edit3,
  ChevronDown, ChevronRight, Loader2, AlertCircle, History,
} from 'lucide-react';
import { useLanguage } from '../../components/LanguageContext';
import { useTheme } from '../../components/ThemeContext';
import {
  fetchVersions, fetchVersionDiff,
} from '../../services/ontologyApi';
import type { VersionItem, VersionDiff, VersionDiffEntity } from '../../services/ontologyApi';

// ── 类型 ──────────────────────────────────────────────────────

interface VersionTimelineProps {
  domainCode: string;
  onClose: () => void;
}

// ── i18n 插值辅助 ─────────────────────────────────────────────

function ti(t: (k: string) => string, key: string, vars: Record<string, string> = {}): string {
  let s = t(key);
  for (const [k, v] of Object.entries(vars)) s = s.replace(`{${k}}`, v);
  return s;
}

function fmtDate(iso: string): string {
  try {
    return new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: '2-digit', day: '2-digit' });
  } catch { return iso.slice(0, 10); }
}

// ── Diff 实体行 ───────────────────────────────────────────────

function DiffEntityRow({ entity, expanded, onToggle }: {
  entity: VersionDiffEntity;
  expanded: boolean;
  onToggle: () => void;
}) {
  const icons: Record<string, { Icon: React.ComponentType<{ size?: number; className?: string }>; cls: string }> = {
    added:   { Icon: Plus,  cls: 'text-emerald-400' },
    modified:{ Icon: Edit3, cls: 'text-amber-400' },
    deleted: { Icon: Minus, cls: 'text-red-400' },
  };
  const { Icon, cls } = icons[entity.changeType] || icons.modified;

  return (
    <div className="mb-1">
      <button onClick={onToggle}
        className="w-full flex items-center gap-1.5 px-2 py-1.5 rounded text-left hover:bg-slate-700/30 transition">
        {expanded ? <ChevronDown size={12} className="text-slate-500" /> : <ChevronRight size={12} className="text-slate-500" />}
        <Icon size={12} className={cls} />
        <span className="text-xs text-slate-300 truncate">{entity.entityName}</span>
        <span className="text-[10px] text-slate-500 ml-auto shrink-0">{entity.entityCode}</span>
      </button>
      {expanded && (
        <div className="ml-7 mt-0.5 mb-1 space-y-0.5">
          {entity.propertiesAdded?.map((p, i) => (
            <div key={`a-${i}`} className="flex items-center gap-1 pl-2 text-[10px]">
              <Plus size={10} className="text-emerald-500 shrink-0" />
              <span className="text-emerald-400">{p.name}</span>
              <span className="text-slate-600">{p.type}</span>
            </div>
          ))}
          {entity.propertiesModified?.map((p, i) => (
            <div key={`m-${i}`} className="flex items-center gap-1 pl-2 text-[10px]">
              <Edit3 size={10} className="text-amber-500 shrink-0" />
              <span className="text-amber-400">{p.name}</span>
              <span className="text-slate-600">{p.oldType} → {p.newType}</span>
            </div>
          ))}
          {entity.propertiesDeleted?.map((name, i) => (
            <div key={`d-${i}`} className="flex items-center gap-1 pl-2 text-[10px]">
              <Minus size={10} className="text-red-500 shrink-0" />
              <span className="text-red-400">{name}</span>
            </div>
          ))}
          {!entity.propertiesAdded?.length && !entity.propertiesModified?.length && !entity.propertiesDeleted?.length && (
            <div className="text-[10px] text-slate-600 pl-2">—</div>
          )}
        </div>
      )}
    </div>
  );
}

// ── 主组件 ────────────────────────────────────────────────────

export default function VersionTimeline({ domainCode, onClose }: VersionTimelineProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const [versions, setVersions] = useState<VersionItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // diff 状态
  const [selectedVersion, setSelectedVersion] = useState<VersionItem | null>(null);
  const [diffData, setDiffData] = useState<VersionDiff | null>(null);
  const [diffLoading, setDiffLoading] = useState(false);
  const [diffError, setDiffError] = useState<string | null>(null);

  // 展开的实体 ID 集合
  const [expandedEntities, setExpandedEntities] = useState<Set<string>>(new Set());

  // 加载版本列表
  useEffect(() => {
    setLoading(true); setError(null);
    fetchVersions(domainCode)
      .then((list) => { setVersions(list || []); setLoading(false); })
      .catch((err) => { setError(err?.message || t('ontology.version.loadFailed')); setLoading(false); });
  }, [domainCode, t]);

  // 点击版本卡片 → 加载 diff
  const handleSelectVersion = useCallback(async (item: VersionItem) => {
    setSelectedVersion(item);
    setDiffData(null);
    setDiffError(null);
    setExpandedEntities(new Set());

    const idx = versions.indexOf(item);
    if (idx <= 0) { setDiffError(t('ontology.version.clickToCompare')); return; }

    const prev = versions[idx - 1];
    setDiffLoading(true);
    try {
      const diff = await fetchVersionDiff(prev.version, item.version);
      setDiffData(diff);
    } catch (err: any) {
      setDiffError(err?.message || t('ontology.version.loadFailed'));
    } finally { setDiffLoading(false); }
  }, [versions, t]);

  const toggleEntity = useCallback((code: string) => {
    setExpandedEntities((prev) => {
      const next = new Set(prev);
      next.has(code) ? next.delete(code) : next.add(code);
      return next;
    });
  }, []);

  // ── 渲染 ────────────────────────────────────────────────────

  return (
    <div className="fixed inset-0 z-50">
      {/* 半透明遮罩 */}
      <div className="absolute inset-0 bg-black/50 backdrop-blur-sm" onClick={onClose} />

      {/* 右侧滑出面板 */}
      <div className={`absolute right-0 top-0 h-full w-[480px] max-w-[90vw] border-l ${styles.cardBorder} ${styles.cardBg} shadow-2xl flex flex-col overflow-hidden animate-in slide-in-from-right`}>
        {/* 标题栏 */}
        <div className={`flex items-center justify-between px-4 py-3 border-b ${styles.cardBorder} shrink-0`}>
          <div className="flex items-center gap-2">
            <History size={16} className="text-indigo-400" />
            <span className="text-sm font-semibold text-slate-200">{t('ontology.version.title')}</span>
            {versions.length > 0 && (
              <span className="text-[10px] px-1.5 py-0.5 rounded bg-slate-700/40 text-slate-500">{versions.length}</span>
            )}
          </div>
          <button onClick={onClose}
            className="p-1 rounded hover:bg-slate-700/40 text-slate-400 hover:text-slate-300 transition">
            <X size={16} />
          </button>
        </div>

        {/* 内容区 */}
        <div className="flex-1 overflow-y-auto">
          {loading && (
            <div className="flex items-center justify-center py-16">
              <Loader2 size={24} className="animate-spin text-indigo-400" />
            </div>
          )}

          {error && (
            <div className="flex flex-col items-center gap-3 py-16 px-8">
              <AlertCircle size={28} className="text-red-400" />
              <p className="text-xs text-red-400 text-center">{error}</p>
              <button onClick={() => { setLoading(true); setError(null); fetchVersions(domainCode).then((l) => { setVersions(l || []); setLoading(false); }).catch((e) => { setError(e?.message || t('ontology.version.loadFailed')); setLoading(false); }); }}
                className="px-3 py-1.5 rounded-lg text-xs bg-slate-700 text-slate-300 hover:bg-slate-600 transition">
                {t('ontology.version.retry')}
              </button>
            </div>
          )}

          {!loading && !error && versions.length === 0 && (
            <div className="flex flex-col items-center gap-3 py-16">
              <Clock size={28} className="text-slate-600" />
              <p className="text-xs text-slate-500">{t('ontology.version.noVersions')}</p>
            </div>
          )}

          {!loading && versions.length > 0 && (
            <div className="p-3 space-y-2">
              {versions.map((item) => {
                const isSelected = selectedVersion?.version === item.version;
                const s = item.summary;
                return (
                  <button key={item.version}
                    onClick={() => handleSelectVersion(item)}
                    className={`w-full text-left px-3 py-2.5 rounded-lg border transition ${
                      isSelected
                        ? 'border-indigo-500/40 bg-indigo-500/10'
                        : `${styles.cardBorder} hover:bg-slate-700/20`
                    }`}>
                    <div className="flex items-center gap-2 mb-1">
                      <GitBranch size={13} className={isSelected ? 'text-indigo-400' : 'text-slate-500'} />
                      <span className={`text-xs font-semibold ${isSelected ? 'text-indigo-300' : 'text-slate-300'}`}>
                        v{item.version}
                      </span>
                    </div>
                    <div className="flex items-center gap-3 text-[10px] text-slate-500 mb-1.5">
                      <span className="flex items-center gap-1">
                        <Clock size={10} />{fmtDate(item.createdAt)}
                      </span>
                      <span className="flex items-center gap-1">
                        <User size={10} />{item.author}
                      </span>
                    </div>
                    {s && (s.addedEntities > 0 || s.modifiedEntities > 0 || s.deletedEntities > 0) && (
                      <div className="flex items-center gap-2 text-[10px]">
                        {s.addedEntities > 0 && (
                          <span className="flex items-center gap-0.5 text-emerald-400"><Plus size={10} />{s.addedEntities}</span>
                        )}
                        {s.modifiedEntities > 0 && (
                          <span className="flex items-center gap-0.5 text-amber-400"><Edit3 size={10} />{s.modifiedEntities}</span>
                        )}
                        {s.deletedEntities > 0 && (
                          <span className="flex items-center gap-0.5 text-red-400"><Minus size={10} />{s.deletedEntities}</span>
                        )}
                      </div>
                    )}
                  </button>
                );
              })}
            </div>
          )}
        </div>

        {/* diff 面板 */}
        {selectedVersion && (
          <div className={`border-t ${styles.cardBorder} shrink-0 max-h-[45%] overflow-y-auto`}>
            <div className={`flex items-center justify-between px-4 py-2.5 border-b ${styles.cardBorder} bg-slate-800/60`}>
              <span className="text-xs font-medium text-slate-300">
                {ti(t, 'ontology.version.compareWith', { version: `v${selectedVersion.version}` })}
              </span>
            </div>

            <div className="p-3">
              {diffLoading && (
                <div className="flex items-center justify-center py-8">
                  <Loader2 size={20} className="animate-spin text-indigo-400" />
                </div>
              )}

              {diffError && (
                <div className="flex items-start gap-2 px-3 py-2.5 rounded-lg bg-red-500/10 border border-red-500/20">
                  <AlertCircle size={13} className="text-red-400 mt-0.5 shrink-0" />
                  <p className="text-xs text-red-400">{diffError}</p>
                </div>
              )}

              {!diffLoading && !diffError && diffData && (
                <div className="space-y-1">
                  {diffData.entities.length === 0 && (
                    <p className="text-xs text-slate-500 text-center py-4">{t('ontology.version.noDiff')}</p>
                  )}
                  {diffData.entities.map((entity) => (
                    <DiffEntityRow key={entity.entityCode} entity={entity}
                      expanded={expandedEntities.has(entity.entityCode)}
                      onToggle={() => toggleEntity(entity.entityCode)} />
                  ))}
                </div>
              )}

              {!diffLoading && !diffError && !diffData && (
                <p className="text-xs text-slate-500 text-center py-4">{t('ontology.version.noDiff')}</p>
              )}
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
