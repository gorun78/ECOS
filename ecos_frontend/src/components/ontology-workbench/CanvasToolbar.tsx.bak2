/**
 * CanvasToolbar — 画布工具栏组件
 *
 * 提供本体画布的操作按钮：添加实体、自动布局(力导向/层级/圆形)、缩放控制、自适应视图。
 * 同时显示当前域名称、实体数和关系数统计。
 *
 * 对应设计文档第5章组件树中的 CanvasToolbar。
 *
 * @license Apache-2.0
 */

import React, { useCallback } from "react";
import {
  Plus,
  Network,
  LayoutList,
  Circle,
  ZoomIn,
  ZoomOut,
  Maximize2,
  Box,
  GitBranch,
  Layers,
} from "lucide-react";

// ── Props ────────────────────────────────────────────────────

export interface CanvasToolbarProps {
  /** 当前域名称（如 "销售域"） */
  domainName?: string;
  /** 实体总数 */
  entityCount: number;
  /** 关系总数 */
  relationshipCount: number;
  /** 回调：点击"添加实体"按钮 */
  onAddEntity?: () => void;
  /** 回调：选择布局算法 @param layout 'force' | 'hierarchical' | 'circular' */
  onLayoutChange?: (layout: "force" | "hierarchical" | "circular") => void;
  /** 回调：放大画布 */
  onZoomIn?: () => void;
  /** 回调：缩小画布 */
  onZoomOut?: () => void;
  /** 回调：自适应视图（fit view） */
  onFitView?: () => void;
  /** 当前缩略级别（用于显示） */
  currentZoom?: number;
  /** 是否可编辑（非只读模式） */
  editable?: boolean;
}

// ── 布局按钮配置 ─────────────────────────────────────────────

const LAYOUT_OPTIONS: {
  id: "force" | "hierarchical" | "circular";
  icon: React.FC<{ size?: number; className?: string }>;
  label: string;
}[] = [
  { id: "force", icon: Network, label: "力导向" },
  { id: "hierarchical", icon: LayoutList, label: "层级" },
  { id: "circular", icon: Circle, label: "圆形" },
];

// ── 主组件 ──────────────────────────────────────────────────

export default function CanvasToolbar({
  domainName,
  entityCount,
  relationshipCount,
  onAddEntity,
  onLayoutChange,
  onZoomIn,
  onZoomOut,
  onFitView,
  currentZoom = 100,
  editable = true,
}: CanvasToolbarProps) {
  // 布局按钮选中态
  const [activeLayout, setActiveLayout] = React.useState<
    "force" | "hierarchical" | "circular" | null
  >(null);

  const handleLayoutClick = useCallback(
    (layout: "force" | "hierarchical" | "circular") => {
      setActiveLayout(layout);
      onLayoutChange?.(layout);
      // 短暂高亮后清除
      setTimeout(() => setActiveLayout(null), 800);
    },
    [onLayoutChange]
  );

  return (
    <div
      className="
        flex items-center gap-3 px-4 py-2.5
        bg-[#0f1117]/90 backdrop-blur-md
        border border-[#1E293B] rounded-xl
        shadow-lg shadow-black/20
        select-none
      "
    >
      {/* ── 域名称 + 统计信息 ── */}
      <div className="flex items-center gap-3 pr-3 border-r border-[#1E293B]">
        {/* 域名称 */}
        {domainName && (
          <div className="flex items-center gap-1.5">
            <Layers size={14} className="text-indigo-400" />
            <span className="text-[12px] font-semibold text-white">{domainName}</span>
          </div>
        )}

        {/* 统计 */}
        <div className="flex items-center gap-2.5 text-[10px]">
          <span className="flex items-center gap-1 text-slate-400">
            <Box size={11} className="text-indigo-400" />
            <span className="font-mono text-indigo-300">{entityCount}</span>
            <span className="text-slate-500">实体</span>
          </span>
          <span className="flex items-center gap-1 text-slate-400">
            <GitBranch size={11} className="text-emerald-400" />
            <span className="font-mono text-emerald-300">
              {relationshipCount}
            </span>
            <span className="text-slate-500">关系</span>
          </span>
        </div>
      </div>

      {/* ── 操作按钮组 ── */}

      {/* 添加实体按钮（仅可编辑模式） */}
      {editable && (
        <button
          onClick={onAddEntity}
          className="
            flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg
            bg-indigo-600 hover:bg-indigo-500
            text-white text-[11px] font-medium
            transition-colors duration-150
            cursor-pointer
          "
          title="添加实体"
        >
          <Plus size={13} />
          <span>添加实体</span>
        </button>
      )}

      {/* 布局选择按钮组 */}
      <div className="flex items-center gap-0.5 bg-[#1A1F2E] rounded-lg p-0.5 border border-[#1E293B]">
        {LAYOUT_OPTIONS.map(({ id, icon: Icon, label }) => (
          <button
            key={id}
            onClick={() => handleLayoutClick(id)}
            className={`
              flex items-center gap-1 px-2 py-1 rounded-md
              text-[10px] font-medium
              transition-colors duration-150
              cursor-pointer
              ${
                activeLayout === id
                  ? "bg-indigo-600 text-white"
                  : "text-slate-400 hover:text-white hover:bg-[#2A3040]"
              }
            `}
            title={`${label}布局`}
          >
            <Icon size={12} />
            <span>{label}</span>
          </button>
        ))}
      </div>

      {/* 缩放控制 */}
      <div className="flex items-center gap-0.5 bg-[#1A1F2E] rounded-lg p-0.5 border border-[#1E293B]">
        <button
          onClick={onZoomOut}
          className="
            p-1.5 rounded-md
            text-slate-400 hover:text-white hover:bg-[#2A3040]
            transition-colors duration-150
            cursor-pointer
          "
          title="缩小"
        >
          <ZoomOut size={13} />
        </button>

        {/* 缩放比例显示 */}
        <span className="text-[10px] font-mono text-slate-400 px-1 min-w-[36px] text-center">
          {Math.round(currentZoom)}%
        </span>

        <button
          onClick={onZoomIn}
          className="
            p-1.5 rounded-md
            text-slate-400 hover:text-white hover:bg-[#2A3040]
            transition-colors duration-150
            cursor-pointer
          "
          title="放大"
        >
          <ZoomIn size={13} />
        </button>
      </div>

      {/* 自适应视图 */}
      <button
        onClick={onFitView}
        className="
          p-1.5 rounded-lg
          text-slate-400 hover:text-white hover:bg-[#1A1F2E]
          border border-transparent hover:border-[#1E293B]
          transition-all duration-150
          cursor-pointer
        "
        title="自适应视图"
      >
        <Maximize2 size={14} />
      </button>
    </div>
  );
}
