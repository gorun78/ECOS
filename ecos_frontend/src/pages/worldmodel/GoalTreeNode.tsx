import React from "react";
import { ChevronRight, ChevronDown, Edit3, Trash2 } from "lucide-react";
import type { Goal } from "../../types";
import {
  computeProgress,
  trafficBg,
  trafficColor,
  getGoalTypeConfig,
} from "./helpers";

export interface GoalTreeNodeProps {
  node: Goal;
  depth?: number;
  expandedIds: Set<number | string>;
  toggleExpand: (id: number | string) => void;
  onSelect: (g: Goal) => void;
  selectedId: number | string | null;
  styles: any;
  key?: React.Key;
  onEdit?: (g: Goal) => void;
  onDelete?: (id: number | string) => void;
}

// ── GoalTreeNode (recursive, expandable) ───────
export default function GoalTreeNode({
  node, depth = 0, expandedIds, toggleExpand, onSelect, selectedId, styles, key: _key, onEdit, onDelete, ..._rest
}: GoalTreeNodeProps) {
  const hasChildren = node.children && node.children.length > 0;
  const isExpanded = expandedIds.has(node.id);
  const pct = computeProgress(node);
  const gt = getGoalTypeConfig(node.goal_type);
  const GTI = gt.icon;
  const isSelected = selectedId === node.id;

  return (
    <div className="select-none">
      {/* Node row */}
      <div
        onClick={(e) => { e.stopPropagation(); onSelect(node); }}
        className={`flex items-center gap-2 py-2 px-2 rounded cursor-pointer transition-colors group
          ${isSelected
            ? "bg-indigo-500/10 border border-indigo-500/30"
            : "hover:bg-white/5 border border-transparent"
          }`}
        style={{ marginLeft: depth * 20 }}
      >
        {/* Expand/collapse toggle */}
        <button
          onClick={(e) => { e.stopPropagation(); if (hasChildren) toggleExpand(node.id); }}
          className={`w-5 h-5 flex items-center justify-center rounded hover:brightness-125 flex-shrink-0
            ${hasChildren ? "cursor-pointer opacity-60 hover:opacity-100" : "opacity-20 cursor-default"}`}
        >
          {hasChildren ? (
            isExpanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />
          ) : (
            <span className="text-[10px]">·</span>
          )}
        </button>

        {/* Traffic light */}
        <span className={`w-2.5 h-2.5 rounded-full flex-shrink-0 ${trafficBg(pct)}`}
          title={`${Math.round(pct)}%`} />

        {/* Goal type badge */}
        <span className={`text-[10px] font-medium px-1.5 py-0.5 rounded border flex items-center gap-1 flex-shrink-0 ${gt.bg} ${gt.text} ${gt.border}`}>
          <GTI className="w-3 h-3" />
          {gt.label}
        </span>

        {/* Name */}
        <span className="text-sm font-medium truncate flex-1 min-w-0">{node.name}</span>

        {/* Progress bar + value */}
        <div className="flex items-center gap-2 flex-shrink-0">
          <span className="text-[11px] font-mono opacity-60 w-16 text-right">
            {typeof node.current_value === "number" ? node.current_value
              : typeof node.currentValue === "number" ? node.currentValue : node.currentValue || 0}
            /
            {typeof node.target_value === "number" ? node.target_value
              : typeof node.targetValue === "number" ? node.targetValue : node.targetValue}
            {node.unit || ""}
          </span>
          <div className="w-20 h-1.5 rounded-full bg-white/10 overflow-hidden">
            <div className={`h-full rounded-full transition-all duration-300 ${trafficBg(pct)}`}
              style={{ width: `${pct}%` }} />
          </div>
          <span className={`text-[11px] font-mono w-9 text-right ${trafficColor(pct)}`}>
            {Math.round(pct)}%
          </span>
        </div>

        {/* Edit/Delete actions */}
        <div className="flex items-center gap-0.5 flex-shrink-0 opacity-0 group-hover:opacity-100 transition-opacity ml-1">
          {onEdit && (
            <button
              onClick={(e) => { e.stopPropagation(); onEdit(node); }}
              className="p-1 rounded hover:bg-white/10 transition-colors"
              title="Edit"
            >
              <Edit3 className="w-3 h-3 opacity-60 hover:opacity-100" />
            </button>
          )}
          {onDelete && (
            <button
              onClick={(e) => { e.stopPropagation(); onDelete(node.id); }}
              className="p-1 rounded hover:bg-red-500/20 transition-colors"
              title="Delete"
            >
              <Trash2 className="w-3 h-3 text-red-400 opacity-60 hover:opacity-100" />
            </button>
          )}
        </div>
      </div>

      {/* Children */}
      {hasChildren && isExpanded && (
        <div className="border-l border-white/5 ml-4">
          {node.children!.map((child) => (
            <GoalTreeNode
              key={child.id}
              node={child}
              depth={depth + 1}
              expandedIds={expandedIds}
              toggleExpand={toggleExpand}
              onSelect={onSelect}
              selectedId={selectedId}
              styles={styles}
              onEdit={onEdit}
              onDelete={onDelete}
            />
          ))}
        </div>
      )}
    </div>
  );
}
