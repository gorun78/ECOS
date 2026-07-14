/**
 * DataResourcePickerModal — 数据资源选择器弹窗
 *
 * 用于从数据底座中选择物理表/视图并映射到本体实体。
 * 功能：
 *   - 加载全部资源列表
 *   - 搜索（按资源名称/数据源名称模糊匹配）
 *   - 筛选（按数据源 / 资源类型 TABLE | VIEW）
 *   - 网格展示，支持多选
 *   - 选中确认回调
 *
 * Props:
 *   - open: boolean        是否显示
 *   - onClose: () => void  关闭回调
 *   - onSelect: (resourceIds: string[]) => void  选中确认回调
 *   - excludeIds?: string[]  已映射的资源 ID，选择器中不可选
 *
 * 深色主题，中文界面。
 * 数据底座不可用时降级展示 "数据底座暂不可用"。
 *
 * @license Apache-2.0
 */

import React, { useState, useEffect, useMemo, useCallback } from 'react';
import {
  X,
  Search,
  Database,
  Loader2,
  AlertTriangle,
  Filter,
  Check,
  Table,
} from 'lucide-react';
import type { BulkResource } from '../../../types/workbench';
import {
  fetchAllResources,
  DATA_CATALOG_UNAVAILABLE,
} from '../../../services/dataCatalogClient';

// ── 组件接口 ────────────────────────────────────────────────

interface DataResourcePickerModalProps {
  open: boolean;
  onClose: () => void;
  onSelect: (resourceIds: string[]) => void;
  /** 已选/已映射的资源 ID 列表（这些资源将不可选） */
  excludeIds?: string[];
}

// ── 资源类型常量 ────────────────────────────────────────────

const RESOURCE_TYPES = [
  { value: '', label: '全部类型' },
  { value: 'TABLE', label: '表 (TABLE)' },
  { value: 'VIEW', label: '视图 (VIEW)' },
];

// ── 主组件 ──────────────────────────────────────────────────

export default function DataResourcePickerModal({
  open,
  onClose,
  onSelect,
  excludeIds = [],
}: DataResourcePickerModalProps) {
  // 资源列表
  const [resources, setResources] = useState<BulkResource[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 搜索 & 筛选
  const [search, setSearch] = useState('');
  const [typeFilter, setTypeFilter] = useState('');
  const [sourceFilter, setSourceFilter] = useState('');

  // 选中
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());

  // 加载资源
  const loadResources = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await fetchAllResources();
      setResources(data);
    } catch (err: any) {
      setError(err?.message || DATA_CATALOG_UNAVAILABLE);
      setResources([]);
    } finally {
      setLoading(false);
    }
  }, []);

  // 打开时加载资源 & 重置状态
  useEffect(() => {
    if (open) {
      loadResources();
      setSearch('');
      setTypeFilter('');
      setSourceFilter('');
      setSelectedIds(new Set());
    }
  }, [open, loadResources]);

  // 提取所有数据源名称（用于筛选下拉）
  const datasources = useMemo(() => {
    const names = new Set<string>();
    resources.forEach((r) => {
      if (r.datasourceName) names.add(r.datasourceName);
    });
    return Array.from(names).sort();
  }, [resources]);

  // 过滤后的资源列表
  const filtered = useMemo(() => {
    return resources.filter((r) => {
      // 排除已映射
      if (excludeIds.includes(r.resourceId)) return false;

      // 搜索
      if (search.trim()) {
        const q = search.toLowerCase();
        const matchName = r.resourceName?.toLowerCase().includes(q);
        const matchSource = r.datasourceName?.toLowerCase().includes(q);
        const matchPath = r.sourcePath?.toLowerCase().includes(q);
        if (!matchName && !matchSource && !matchPath) return false;
      }

      // 类型筛选
      if (typeFilter && r.resourceType !== typeFilter) return false;

      // 数据源筛选
      if (sourceFilter && r.datasourceName !== sourceFilter) return false;

      return true;
    });
  }, [resources, search, typeFilter, sourceFilter, excludeIds]);

  // 切换选中
  const toggleSelect = useCallback((id: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }, []);

  // 全选/取消全选
  const toggleSelectAll = useCallback(() => {
    if (filtered.length === 0) return;
    const allIds = new Set(filtered.map((r) => r.resourceId));
    // 如果已经全选了当前筛选结果，则取消全选
    const allSelected = filtered.every((r) => selectedIds.has(r.resourceId));
    if (allSelected) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds((prev) => {
        const next = new Set(prev);
        allIds.forEach((id) => next.add(id));
        return next;
      });
    }
  }, [filtered, selectedIds]);

  // 确认
  const handleConfirm = useCallback(() => {
    if (selectedIds.size === 0) return;
    onSelect(Array.from(selectedIds));
  }, [selectedIds, onSelect]);

  // 键盘
  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    },
    [onClose]
  );

  if (!open) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm"
      onKeyDown={handleKeyDown}
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        className="bg-[#1a1f2e] rounded-xl shadow-2xl w-full max-w-[680px] mx-4
          border border-[#2a3040] animate-in zoom-in-95 duration-200
          max-h-[85vh] flex flex-col"
      >
        {/* ── 标题栏 ── */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-[#2a3040] shrink-0">
          <div className="flex items-center gap-2.5">
            <div className="p-1.5 rounded-lg bg-indigo-500/10">
              <Database size={16} className="text-indigo-400" />
            </div>
            <div>
              <h3 className="text-sm font-semibold text-white">选择物理表</h3>
              <p className="text-[10px] text-slate-500">
                从数据底座中选择要映射的物理表或视图
              </p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-lg hover:bg-white/5 text-slate-400 hover:text-slate-300 transition"
          >
            <X size={16} />
          </button>
        </div>

        {/* ── 搜索 & 筛选栏 ── */}
        <div className="px-5 py-3 border-b border-[#1E293B] space-y-2.5 shrink-0">
          {/* 搜索框 */}
          <div className="relative">
            <Search
              size={13}
              className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500"
            />
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="搜索资源名称、数据源或路径..."
              className="w-full bg-[#0b0e14] border border-[#2a3040] rounded-lg pl-9 pr-3 py-2
                text-xs text-white placeholder:text-slate-600
                focus:outline-none focus:border-indigo-500/50 transition"
            />
          </div>

          {/* 筛选下拉 */}
          <div className="flex items-center gap-2">
            <Filter size={11} className="text-slate-500 shrink-0" />

            {/* 数据源筛选 */}
            <select
              value={sourceFilter}
              onChange={(e) => setSourceFilter(e.target.value)}
              className="bg-[#0b0e14] border border-[#2a3040] rounded-lg px-2.5 py-1.5
                text-[10px] text-slate-300 appearance-none cursor-pointer
                focus:outline-none focus:border-indigo-500/50 transition
                min-w-0 max-w-[140px]"
            >
              <option value="">全部数据源</option>
              {datasources.map((ds) => (
                <option key={ds} value={ds}>
                  {ds}
                </option>
              ))}
            </select>

            {/* 类型筛选 */}
            <select
              value={typeFilter}
              onChange={(e) => setTypeFilter(e.target.value)}
              className="bg-[#0b0e14] border border-[#2a3040] rounded-lg px-2.5 py-1.5
                text-[10px] text-slate-300 appearance-none cursor-pointer
                focus:outline-none focus:border-indigo-500/50 transition
                min-w-0"
            >
              {RESOURCE_TYPES.map((rt) => (
                <option key={rt.value} value={rt.value}>
                  {rt.label}
                </option>
              ))}
            </select>

            {/* 结果计数 */}
            <span className="text-[10px] text-slate-500 ml-auto">
              {filtered.length} 个结果
            </span>
          </div>
        </div>

        {/* ── 资源列表 ── */}
        <div className="flex-1 overflow-y-auto px-5 py-3 min-h-[200px]">
          {/* 加载态 */}
          {loading && (
            <div className="flex items-center justify-center py-16">
              <Loader2 size={22} className="animate-spin text-indigo-400" />
              <span className="ml-3 text-xs text-slate-400">加载数据资源...</span>
            </div>
          )}

          {/* 错误态 */}
          {!loading && error && (
            <div className="flex flex-col items-center justify-center py-12 space-y-4">
              <div className="flex items-start gap-2.5 px-4 py-3 rounded-lg bg-amber-500/10 border border-amber-500/20">
                <AlertTriangle size={15} className="text-amber-400 mt-0.5 shrink-0" />
                <div>
                  <p className="text-xs text-amber-300 font-medium">数据底座暂不可用</p>
                  <p className="text-[10px] text-amber-500/70 mt-1">{error}</p>
                </div>
              </div>
              <button
                onClick={loadResources}
                className="px-4 py-2 rounded-lg text-xs text-indigo-400
                  border border-indigo-500/20 hover:bg-indigo-500/10 transition"
              >
                重试
              </button>
            </div>
          )}

          {/* 空结果 */}
          {!loading && !error && filtered.length === 0 && (
            <div className="flex flex-col items-center justify-center py-12 text-slate-500">
              <Search size={28} className="mb-2 opacity-20" />
              <p className="text-xs">未找到匹配的资源</p>
              <p className="text-[10px] mt-1 opacity-60">
                {search || typeFilter || sourceFilter
                  ? '请尝试调整搜索或筛选条件'
                  : '数据底座中暂无可用资源'}
              </p>
            </div>
          )}

          {/* 资源网格 */}
          {!loading && !error && filtered.length > 0 && (
            <div className="space-y-1">
              {/* 全选操作 */}
              <div className="flex items-center justify-between mb-1 px-1">
                <button
                  onClick={toggleSelectAll}
                  className="text-[10px] text-indigo-400 hover:text-indigo-300 transition"
                >
                  {filtered.every((r) => selectedIds.has(r.resourceId))
                    ? '取消全选'
                    : '全选当前结果'}
                </button>
                <span className="text-[10px] text-slate-500">
                  已选 {selectedIds.size} 项
                </span>
              </div>

              {filtered.map((res) => {
                const isSelected = selectedIds.has(res.resourceId);
                const typeLabel = res.resourceType === 'VIEW' ? '视图' : '表';

                return (
                  <button
                    key={res.resourceId}
                    onClick={() => toggleSelect(res.resourceId)}
                    className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-lg border text-left transition ${
                      isSelected
                        ? 'border-indigo-500/30 bg-indigo-500/10'
                        : 'border-[#1E293B] bg-[#0b0e14] hover:border-[#2a3040]'
                    }`}
                  >
                    {/* 选中标记 */}
                    <div
                      className={`w-4 h-4 rounded border flex items-center justify-center shrink-0 transition ${
                        isSelected
                          ? 'bg-indigo-500 border-indigo-500'
                          : 'border-[#3a4050]'
                      }`}
                    >
                      {isSelected && <Check size={10} className="text-white" />}
                    </div>

                    {/* 表图标 */}
                    <div className="p-1 rounded bg-indigo-500/10 shrink-0">
                      <Table size={14} className="text-indigo-400" />
                    </div>

                    {/* 资源信息 */}
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-1.5">
                        <span className="text-xs font-medium text-slate-200 truncate">
                          {res.resourceName}
                        </span>
                        <span className="text-[9px] px-1.5 py-0.5 rounded bg-[#2a3040] text-slate-400 shrink-0">
                          {typeLabel}
                        </span>
                      </div>
                      <div className="flex items-center gap-2 mt-0.5 text-[10px] text-slate-500">
                        <span className="truncate">{res.datasourceName}</span>
                        <span>·</span>
                        <span className="font-mono text-[9px] truncate">{res.sourcePath}</span>
                        <span>·</span>
                        <span>{res.fieldCount} 字段</span>
                      </div>
                    </div>
                  </button>
                );
              })}
            </div>
          )}
        </div>

        {/* ── 底部操作 ── */}
        <div className="flex items-center justify-between px-5 py-4 border-t border-[#2a3040] shrink-0">
          <span className="text-[10px] text-slate-500">
            已选择 {selectedIds.size} 个资源
          </span>
          <div className="flex items-center gap-2.5">
            <button
              onClick={onClose}
              className="px-4 py-2 rounded-lg text-xs font-medium text-slate-300
                bg-[#2a3040] hover:bg-[#3a4050] transition"
            >
              取消
            </button>
            <button
              onClick={handleConfirm}
              disabled={selectedIds.size === 0}
              className="px-5 py-2 rounded-lg text-xs font-semibold text-white
                bg-indigo-600 hover:bg-indigo-500
                disabled:opacity-40 disabled:cursor-not-allowed
                transition flex items-center gap-2"
            >
              确认映射 ({selectedIds.size})
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
