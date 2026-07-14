/**
 * GlossaryBindingPanel — 术语关联标签页内容
 *
 * 用于 WorkbenchRightPanel 术语关联标签页。
 * 展示当前选中实体已绑定的术语列表:
 *   - TermChip: 术语名称 + 状态标签 + 解绑按钮
 *   - [+ 关联术语] 按钮 → 打开 TermSearchModal
 *
 * 数据来源: store.entityTermBindings, store.glossaryTerms
 * 操作: store.bindEntityToTerm, store.unbindEntityFromTerm
 *
 * @license Apache-2.0
 */

import React, { useState, useCallback, useMemo } from "react";
import { BookOpen, X, Plus, Loader2, Tag } from "lucide-react";
import { useWorkbenchStore } from "../../../stores/useWorkbenchStore";
import type { GlossaryTerm } from "../../../types/workbench";
import TermSearchModal from "../modals/TermSearchModal";

// ── 状态颜色映射 ────────────────────────────────────────────────

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

// ── 术语标签组件 (TermChip) ─────────────────────────────────────

interface TermChipProps {
  term: GlossaryTerm;
  onUnbind: () => void;
}

function TermChip({ term, onUnbind }: TermChipProps) {
  const statusColor =
    STATUS_COLORS[term.status] ||
    "bg-slate-500/15 text-slate-400 border-slate-500/20";

  return (
    <div
      className="group flex items-center gap-2 px-2.5 py-1.5 rounded-lg
        bg-white/[0.03] border border-[#1E293B] hover:border-[#2a3040] transition"
    >
      {/* 术语名称 */}
      <Tag size={11} className="text-indigo-400 shrink-0" />
      <span className="text-xs text-slate-300 truncate flex-1 min-w-0">
        {term.name}
      </span>

      {/* 状态标签 */}
      <span
        className={`text-[9px] px-1.5 py-0.5 rounded border ${statusColor} shrink-0`}
      >
        {getStatusLabel(term.status)}
      </span>

      {/* 解绑按钮 */}
      <button
        onClick={onUnbind}
        className="p-0.5 rounded opacity-0 group-hover:opacity-100
          hover:bg-red-500/10 text-slate-600 hover:text-red-400
          transition-opacity shrink-0"
        title="解绑术语"
      >
        <X size={10} />
      </button>
    </div>
  );
}

// ════════════════════════════════════════════════════════════════
// 主组件
// ════════════════════════════════════════════════════════════════

interface GlossaryBindingPanelProps {
  entityId: string;
}

export default function GlossaryBindingPanel({
  entityId,
}: GlossaryBindingPanelProps) {
  const store = useWorkbenchStore();
  const {
    glossaryTerms,
    entityTermBindings,
    glossaryTermsLoading,
    bindEntityToTerm,
    unbindEntityFromTerm,
    fetchGlossaryTerms,
  } = store;

  const [searchModalOpen, setSearchModalOpen] = useState(false);

  // ── 当前实体绑定的术语 ID 列表 ──────────────────────────────────
  const boundTermIds: string[] = useMemo(
    () => entityTermBindings[entityId] || [],
    [entityId, entityTermBindings]
  );

  // ── 已绑定术语详情（从 glossaryTerms 中匹配） ─────────────────────
  const boundTerms: GlossaryTerm[] = useMemo(() => {
    return boundTermIds
      .map((tid) => glossaryTerms.find((t) => t.id === tid))
      .filter((t): t is GlossaryTerm => t != null);
  }, [boundTermIds, glossaryTerms]);

  // ── 已绑定的术语 ID Set（用于在搜索弹窗中标记已选） ─────────────────
  const boundTermIdSet: Set<string> = useMemo(
    () => new Set(boundTermIds),
    [boundTermIds]
  );

  // ── 加载术语 ───────────────────────────────────────────────────
  const loadTerms = useCallback(() => {
    if (glossaryTerms.length === 0) {
      fetchGlossaryTerms();
    }
  }, [glossaryTerms.length, fetchGlossaryTerms]);

  // 首次渲染时加载术语
  React.useEffect(() => {
    loadTerms();
  }, [loadTerms]);

  // ── 解绑 ──────────────────────────────────────────────────────
  const handleUnbind = useCallback(
    (termId: string) => {
      unbindEntityFromTerm(entityId, termId);
    },
    [entityId, unbindEntityFromTerm]
  );

  // ── 确认关联（来自弹窗） ────────────────────────────────────────
  const handleSelectTerms = useCallback(
    (termIds: string[]) => {
      // 绑定新选中的术语（去重已在 store 中处理）
      termIds.forEach((tid) => {
        bindEntityToTerm(entityId, tid);
      });
      setSearchModalOpen(false);
    },
    [entityId, bindEntityToTerm]
  );

  return (
    <div>
      {/* 标题栏 */}
      <div className="flex items-center justify-between mb-3">
        <h4 className="text-[11px] font-semibold text-slate-300 flex items-center gap-1.5">
          <BookOpen size={11} className="text-slate-500" />
          术语关联
          <span className="text-[10px] font-normal text-slate-500 ml-1">
            ({boundTerms.length})
          </span>
        </h4>

        <button
          onClick={() => setSearchModalOpen(true)}
          className="flex items-center gap-1 px-2.5 py-1 rounded-lg text-[10px] font-medium
            bg-indigo-600/20 text-indigo-400 border border-indigo-500/30
            hover:bg-indigo-600/30 transition"
        >
          <Plus size={10} />
          关联术语
        </button>
      </div>

      {/* 术语列表 */}
      {glossaryTermsLoading && boundTerms.length === 0 ? (
        <div className="flex items-center justify-center py-8 text-slate-500">
          <Loader2 size={16} className="animate-spin mr-2" />
          <span className="text-[11px]">加载术语中...</span>
        </div>
      ) : boundTerms.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-10 text-slate-500">
          <BookOpen size={24} className="mb-2 opacity-20" />
          <p className="text-[11px]">暂无关联术语</p>
          <p className="text-[9px] mt-0.5 opacity-50">
            点击「关联术语」从术语库中选择
          </p>
        </div>
      ) : (
        <div className="space-y-1.5">
          {boundTerms.map((term) => (
            <TermChip
              key={term.id}
              term={term}
              onUnbind={() => handleUnbind(term.id)}
            />
          ))}
        </div>
      )}

      {/* 术语搜索弹窗 */}
      <TermSearchModal
        open={searchModalOpen}
        onClose={() => setSearchModalOpen(false)}
        onSelect={handleSelectTerms}
        alreadyBoundIds={boundTermIdSet}
      />
    </div>
  );
}
