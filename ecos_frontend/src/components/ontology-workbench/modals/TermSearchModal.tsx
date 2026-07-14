/**
 * TermSearchModal — 术语搜索与关联弹窗
 *
 * 用于在本体工作台中搜索术语库并关联到实体/属性。
 * 功能:
 *   - 搜索框: 按术语名称/编码模糊搜索
 *   - 域筛选: 下拉选择域
 *   - 状态筛选: 下拉选择状态
 *   - 术语候选列表: 勾选待关联的术语
 *   - 确认关联: 调用 onSelect(termIds)
 *
 * Props: { open, onClose, onSelect(termIds), alreadyBoundIds? }
 *
 * @license Apache-2.0
 */

import React, { useState, useEffect, useCallback, useMemo } from "react";
import { X, Search, Filter, BookOpen, Loader2, Check } from "lucide-react";
import { fetchTerms } from "../../../services/glossaryClient";
import type { GlossaryTerm, GlossaryFilter } from "../../../types/workbench";

// ── 状态选项 ──────────────────────────────────────────────────

const STATUS_OPTIONS: { value: string; label: string }[] = [
  { value: "", label: "全部状态" },
  { value: "PUBLISHED", label: "已发布" },
  { value: "DRAFT", label: "草稿" },
  { value: "DEPRECATED", label: "已废弃" },
  { value: "ARCHIVED", label: "已归档" },
];

const STATUS_COLORS: Record<string, string> = {
  active: "bg-emerald-500/15 text-emerald-400 border-emerald-500/20",
  PUBLISHED: "bg-emerald-500/15 text-emerald-400 border-emerald-500/20",
  DRAFT: "bg-amber-500/15 text-amber-400 border-amber-500/20",
  DEPRECATED: "bg-red-500/15 text-red-400 border-red-500/20",
  ARCHIVED: "bg-slate-500/15 text-slate-400 border-slate-500/20",
};

function getStatusLabel(status: string): string {
  const map: Record<string, string> = {
    active: "已发布",
    PUBLISHED: "已发布",
    DRAFT: "草稿",
    DEPRECATED: "已废弃",
    ARCHIVED: "已归档",
  };
  return map[status] || status;
}

// ── 组件接口 ──────────────────────────────────────────────────

interface TermSearchModalProps {
  /** 是否打开 */
  open: boolean;
  /** 关闭回调 */
  onClose: () => void;
  /** 确认选择回调，返回选中的术语 ID 列表 */
  onSelect: (termIds: string[]) => void;
  /** 已绑定的术语 ID 集合（用于默认勾选和禁用） */
  alreadyBoundIds?: Set<string>;
}

// ════════════════════════════════════════════════════════════════
// 主组件
// ════════════════════════════════════════════════════════════════

export default function TermSearchModal({
  open,
  onClose,
  onSelect,
  alreadyBoundIds,
}: TermSearchModalProps) {
  // ── 数据 ────────────────────────────────────────────────────
  const [allTerms, setAllTerms] = useState<GlossaryTerm[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  // ── 筛选状态 ─────────────────────────────────────────────────
  const [searchKeyword, setSearchKeyword] = useState("");
  const [domainFilter, setDomainFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState("PUBLISHED");
  const [showFilters, setShowFilters] = useState(false);

  // ── 选中状态 ─────────────────────────────────────────────────
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());

  // ── 域选项（从术语中动态提取） ──────────────────────────────────
  const domainOptions: string[] = useMemo(() => {
    const domains = new Set<string>();
    allTerms.forEach((t) => {
      if (t.domain) domains.add(t.domain);
    });
    return Array.from(domains).sort();
  }, [allTerms]);

  // ── 加载术语 ──────────────────────────────────────────────────
  const loadTerms = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const filters: GlossaryFilter = {};
      if (domainFilter) filters.domain = domainFilter;
      if (statusFilter) filters.status = statusFilter;

      const terms = await fetchTerms(filters);
      setAllTerms(terms);
    } catch (err: any) {
      setError(err?.message || "获取术语列表失败");
    } finally {
      setLoading(false);
    }
  }, [domainFilter, statusFilter]);

  // 打开弹窗 / 筛选变化时加载
  useEffect(() => {
    if (open) {
      loadTerms();
    }
  }, [open, loadTerms]);

  // 打开时重置本地状态
  useEffect(() => {
    if (open) {
      setSearchKeyword("");
      setDomainFilter("");
      setStatusFilter("PUBLISHED");
      setShowFilters(false);
      setError("");

      // 初始化已选为已绑定的术语
      if (alreadyBoundIds && alreadyBoundIds.size > 0) {
        setSelectedIds(new Set(alreadyBoundIds));
      } else {
        setSelectedIds(new Set());
      }
    }
  }, [open, alreadyBoundIds]);

  // ── 前端搜索过滤 ─────────────────────────────────────────────
  const filteredTerms: GlossaryTerm[] = useMemo(() => {
    if (!searchKeyword.trim()) return allTerms;

    const kw = searchKeyword.toLowerCase();
    return allTerms.filter(
      (t) =>
        t.name.toLowerCase().includes(kw) ||
        (t.code && t.code.toLowerCase().includes(kw))
    );
  }, [allTerms, searchKeyword]);

  // ── 勾选/取消勾选 ────────────────────────────────────────────
  const toggleTerm = useCallback(
    (termId: string) => {
      // 已绑定的术语不允许取消勾选
      if (alreadyBoundIds?.has(termId)) return;

      setSelectedIds((prev) => {
        const next = new Set(prev);
        if (next.has(termId)) {
          next.delete(termId);
        } else {
          next.add(termId);
        }
        return next;
      });
    },
    [alreadyBoundIds]
  );

  // ── 确认 ────────────────────────────────────────────────────
  const handleConfirm = useCallback(() => {
    // 只传递新增的术语 ID（排除已绑定的）
    const boundSet = alreadyBoundIds || new Set<string>();
    const newIds = Array.from(selectedIds).filter((id) => !boundSet.has(id));
    if (newIds.length > 0) {
      onSelect(newIds);
    } else {
      onClose();
    }
  }, [selectedIds, alreadyBoundIds, onSelect, onClose]);

  // ── 键盘支持 ─────────────────────────────────────────────────
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Escape") onClose();
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleConfirm();
    }
  };

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
        className="bg-[#1a1f2e] rounded-xl shadow-2xl w-full max-w-[520px] mx-4 border border-[#2a3040]
        animate-in zoom-in-95 duration-200 max-h-[85vh] flex flex-col"
      >
        {/* ── 标题栏 ── */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-[#2a3040] shrink-0">
          <div className="flex items-center gap-2.5">
            <div className="p-1.5 rounded-lg bg-indigo-500/10">
              <BookOpen size={16} className="text-indigo-400" />
            </div>
            <div>
              <h3 className="text-sm font-semibold text-white">关联术语</h3>
              <p className="text-[10px] text-slate-500">
                从术语库中选择术语进行关联
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

        {/* ── 搜索与筛选栏 ── */}
        <div className="px-5 py-3 border-b border-[#2a3040] shrink-0 space-y-2">
          {/* 搜索框 */}
          <div className="flex items-center gap-2">
            <div className="relative flex-1">
              <Search
                size={13}
                className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500"
              />
              <input
                value={searchKeyword}
                onChange={(e) => setSearchKeyword(e.target.value)}
                placeholder="搜索术语名称或编码..."
                className="w-full bg-[#0b0e14] border border-[#2a3040] rounded-lg pl-9 pr-3 py-2
                  text-xs text-white placeholder:text-slate-600
                  focus:outline-none focus:border-indigo-500/50 focus:ring-1 focus:ring-indigo-500/30
                  transition"
              />
            </div>
            <button
              onClick={() => setShowFilters(!showFilters)}
              className={`p-2 rounded-lg border transition ${
                showFilters
                  ? "bg-indigo-500/15 border-indigo-500/30 text-indigo-400"
                  : "bg-[#0b0e14] border-[#2a3040] text-slate-500 hover:text-slate-400"
              }`}
              title="筛选"
            >
              <Filter size={13} />
            </button>
          </div>

          {/* 筛选面板 */}
          {showFilters && (
            <div className="flex items-center gap-3 pt-1 animate-in slide-in-from-top-1 duration-150">
              {/* 域筛选 */}
              <div className="flex-1">
                <label className="block text-[10px] text-slate-500 mb-1">
                  域
                </label>
                <select
                  value={domainFilter}
                  onChange={(e) => setDomainFilter(e.target.value)}
                  className="w-full bg-[#0b0e14] border border-[#2a3040] rounded-lg px-2.5 py-1.5
                    text-xs text-slate-300
                    focus:outline-none focus:border-indigo-500/50 transition"
                >
                  <option value="">全部域</option>
                  {domainOptions.map((d) => (
                    <option key={d} value={d}>
                      {d}
                    </option>
                  ))}
                </select>
              </div>

              {/* 状态筛选 */}
              <div className="flex-1">
                <label className="block text-[10px] text-slate-500 mb-1">
                  状态
                </label>
                <select
                  value={statusFilter}
                  onChange={(e) => setStatusFilter(e.target.value)}
                  className="w-full bg-[#0b0e14] border border-[#2a3040] rounded-lg px-2.5 py-1.5
                    text-xs text-slate-300
                    focus:outline-none focus:border-indigo-500/50 transition"
                >
                  {STATUS_OPTIONS.map((s) => (
                    <option key={s.value} value={s.value}>
                      {s.label}
                    </option>
                  ))}
                </select>
              </div>
            </div>
          )}
        </div>

        {/* ── 术语列表 ── */}
        <div className="flex-1 overflow-y-auto px-5 py-3 min-h-[200px]">
          {loading ? (
            <div className="flex items-center justify-center py-12 text-slate-500">
              <Loader2 size={18} className="animate-spin mr-2" />
              <span className="text-xs">加载术语中...</span>
            </div>
          ) : error ? (
            <div className="flex flex-col items-center justify-center py-12 text-slate-500">
              <p className="text-xs text-red-400">{error}</p>
              <button
                onClick={loadTerms}
                className="mt-2 text-[10px] text-indigo-400 hover:text-indigo-300 transition"
              >
                点击重试
              </button>
            </div>
          ) : filteredTerms.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-12 text-slate-500">
              <BookOpen size={28} className="mb-2 opacity-20" />
              <p className="text-xs">
                {searchKeyword ? "无匹配结果" : "暂无术语"}
              </p>
              <p className="text-[10px] mt-0.5 opacity-50">
                {searchKeyword
                  ? "尝试其他关键词"
                  : "请在术语管理页面添加术语"}
              </p>
            </div>
          ) : (
            <div className="space-y-1">
              {filteredTerms.map((term) => {
                const isSelected = selectedIds.has(term.id);
                const isBound = alreadyBoundIds?.has(term.id);
                const statusColor =
                  STATUS_COLORS[term.status] ||
                  "bg-slate-500/15 text-slate-400 border-slate-500/20";

                return (
                  <label
                    key={term.id}
                    onClick={() => toggleTerm(term.id)}
                    className={`flex items-center gap-3 px-3 py-2.5 rounded-lg border cursor-pointer transition ${
                      isSelected
                        ? "border-indigo-500/40 bg-indigo-500/10"
                        : "border-[#1E293B] hover:border-[#2a3040] bg-transparent hover:bg-white/[0.02]"
                    } ${isBound ? "opacity-60" : ""}`}
                  >
                    {/* 复选框 */}
                    <div
                      className={`w-4 h-4 rounded border-2 flex items-center justify-center shrink-0 transition ${
                        isSelected || isBound
                          ? "bg-indigo-600 border-indigo-600"
                          : "border-[#2a3040] bg-[#0b0e14]"
                      }`}
                    >
                      {(isSelected || isBound) && (
                        <Check size={10} className="text-white" />
                      )}
                    </div>

                    {/* 术语信息 */}
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2">
                        <span className="text-xs text-slate-200 truncate">
                          {term.name}
                        </span>
                        <span
                          className={`text-[9px] px-1.5 py-0.5 rounded border ${statusColor} shrink-0`}
                        >
                          {getStatusLabel(term.status)}
                        </span>
                      </div>
                      {term.domain && (
                        <p className="text-[10px] text-slate-500 mt-0.5 truncate">
                          {term.domain}
                        </p>
                      )}
                    </div>

                    {/* 已绑定标识 */}
                    {isBound && (
                      <span className="text-[9px] text-slate-500 shrink-0">
                        已关联
                      </span>
                    )}
                  </label>
                );
              })}
            </div>
          )}
        </div>

        {/* ── 底部操作栏 ── */}
        <div className="flex items-center justify-between px-5 py-3 border-t border-[#2a3040] shrink-0">
          {/* 选中计数 */}
          <span className="text-[10px] text-slate-500">
            {alreadyBoundIds
              ? `已关联 ${alreadyBoundIds.size} 个 · 新增 ${
                  Array.from(selectedIds).filter(
                    (id) => !alreadyBoundIds.has(id)
                  ).length
                } 个`
              : `已选 ${selectedIds.size} 个`}
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
              disabled={loading}
              className="px-5 py-2 rounded-lg text-xs font-semibold text-white
                bg-indigo-600 hover:bg-indigo-500
                disabled:opacity-40 disabled:cursor-not-allowed
                transition flex items-center gap-2"
            >
              {loading && <Loader2 size={13} className="animate-spin" />}
              确认关联
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
