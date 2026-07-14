/**
 * RelationshipEdge — ReactFlow 自定义关系边组件
 *
 * 用于在 DomainGraphCanvas 画布中渲染本体关系边。
 * 显示关系标签、关系类型(ONE_TO_ONE/ONE_TO_MANY/MANY_TO_MANY)，支持选中高亮和删除按钮。
 *
 * 注册为 ReactFlow edgeTypes.relationshipEdge
 *
 * @license Apache-2.0
 */

import React from "react";
import {
  BaseEdge,
  EdgeLabelRenderer,
  getBezierPath,
  type EdgeProps,
  type Edge,
} from "@xyflow/react";
import { X } from "lucide-react";
import type { RelationshipEdgeData } from "../../../adapters/flowAdapter";

// ── 关系类型配置 ────────────────────────────────────────────

/** 关系类型 → 显示标签 + 颜色 */
const REL_TYPE_CONFIG: Record<
  string,
  { label: string; color: string; bg: string }
> = {
  ONE_TO_ONE: {
    label: "1:1",
    color: "text-cyan-400",
    bg: "bg-cyan-500/15",
  },
  ONE_TO_MANY: {
    label: "1:N",
    color: "text-blue-400",
    bg: "bg-blue-500/15",
  },
  MANY_TO_ONE: {
    label: "N:1",
    color: "text-blue-400",
    bg: "bg-blue-500/15",
  },
  MANY_TO_MANY: {
    label: "N:M",
    color: "text-violet-400",
    bg: "bg-violet-500/15",
  },
};

const DEFAULT_TYPE_CONFIG = {
  label: "→",
  color: "text-slate-400",
  bg: "bg-slate-500/15",
};

// ── 主组件 ──────────────────────────────────────────────────

/**
 * RelationshipEdge — 本体关系画布边
 *
 * 使用 ReactFlow 的 BaseEdge + EdgeLabelRenderer 渲染贝塞尔曲线边，
 * 并在边的中间位置显示关系标签和删除按钮。
 *
 * @param props - ReactFlow EdgeProps<RelationshipEdgeData>
 */
function RelationshipEdge({
  id,
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  data,
  selected,
  markerEnd,
}: EdgeProps<Edge<RelationshipEdgeData>>) {
  // ── 计算贝塞尔路径 ──
  const [edgePath, labelX, labelY] = getBezierPath({
    sourceX,
    sourceY,
    sourcePosition,
    targetX,
    targetY,
    targetPosition,
  });

  // 关系类型配置
  const relType = data?.relationshipType || "RELATED_TO";
  const typeConfig = REL_TYPE_CONFIG[relType] || DEFAULT_TYPE_CONFIG;

  // 选中态颜色
  const strokeColor = selected ? "#6366f1" : "#334155";
  const strokeWidth = selected ? 2.5 : 1.5;

  return (
    <>
      {/* ── 基础边路径 ── */}
      <BaseEdge
        id={id}
        path={edgePath}
        stroke={strokeColor}
        strokeWidth={strokeWidth}
        markerEnd={markerEnd}
        className="transition-colors duration-150"
      />

      {/* ── 边标签（显示在路径中点） ── */}
      <EdgeLabelRenderer>
        <div
          className="nodrag nopan absolute pointer-events-auto"
          style={{
            transform: `translate(-50%, -50%) translate(${labelX}px, ${labelY}px)`,
          }}
        >
          <div
            className={`
              flex items-center gap-1.5 px-2 py-1 rounded-md
              border border-[#1E293B]
              bg-[#0f1117]/95 backdrop-blur-sm
              text-[10px] font-medium
              shadow-sm shadow-black/30
              transition-all duration-150
              ${selected ? "ring-1 ring-indigo-500" : ""}
            `}
          >
            {/* 关系名称 */}
            <span className="text-slate-300 max-w-[100px] truncate">
              {data?.name || data?.code || "关联"}
            </span>

            {/* 关系类型标签 */}
            <span
              className={`
                text-[8px] px-1 py-0.5 rounded font-mono
                ${typeConfig.bg} ${typeConfig.color}
              `}
            >
              {typeConfig.label}
            </span>

            {/* 删除按钮（仅选中时显示） */}
            {selected && (
              <button
                className="
                  shrink-0 ml-1 p-0.5 rounded
                  text-red-400 hover:text-red-300
                  hover:bg-red-500/10
                  transition-colors duration-100
                  cursor-pointer
                "
                title="删除关系"
                onClick={(e) => {
                  e.stopPropagation();
                  // 通过自定义事件通知画布删除此边
                  const event = new CustomEvent("relationship-edge-delete", {
                    detail: { edgeId: id },
                    bubbles: true,
                  });
                  (e.target as HTMLElement).dispatchEvent(event);
                }}
              >
                <X size={10} />
              </button>
            )}
          </div>
        </div>
      </EdgeLabelRenderer>
    </>
  );
}

export default React.memo(RelationshipEdge);
