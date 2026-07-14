/**
 * EntityNode — ReactFlow 自定义实体节点组件
 *
 * 用于在 DomainGraphCanvas 画布中渲染本体实体节点。
 * 显示实体名称、类型图标(MASTER/TRANSACTION/EVENT/REFERENCE)、属性预览。
 * 支持选中高亮、拖拽、source/target handles。
 *
 * 注册为 ReactFlow nodeTypes.entityNode
 *
 * @license Apache-2.0
 */

import React, { memo } from "react";
import { Handle, Position, type NodeProps, type Node } from "@xyflow/react";
import {
  Database,
  ArrowRightLeft,
  CalendarClock,
  BookOpen,
  Hash,
  Type,
  Calendar,
  ToggleLeft,
} from "lucide-react";
import type { EntityNodeData } from "../../../adapters/flowAdapter";

// ── 实体类型图标映射 ────────────────────────────────────────

/** 实体类型 → 图标组件 + 颜色类名 */
const ENTITY_TYPE_CONFIG: Record<
  string,
  { icon: React.FC<{ size?: number; className?: string }>; color: string; bg: string; label: string }
> = {
  MASTER: {
    icon: Database,
    color: "text-indigo-400",
    bg: "bg-indigo-500/15",
    label: "主数据",
  },
  TRANSACTION: {
    icon: ArrowRightLeft,
    color: "text-emerald-400",
    bg: "bg-emerald-500/15",
    label: "事务",
  },
  EVENT: {
    icon: CalendarClock,
    color: "text-amber-400",
    bg: "bg-amber-500/15",
    label: "事件",
  },
  REFERENCE: {
    icon: BookOpen,
    color: "text-purple-400",
    bg: "bg-purple-500/15",
    label: "引用",
  },
};

/** 默认/未知类型配置 */
const DEFAULT_TYPE_CONFIG = {
  icon: Database,
  color: "text-slate-400",
  bg: "bg-slate-500/15",
  label: "实体",
};

/** 属性类型 → 图标短映射 */
const PROP_TYPE_ICON: Record<string, React.FC<{ size?: number; className?: string }>> = {
  STRING: Type,
  VARCHAR: Type,
  TEXT: Type,
  INTEGER: Hash,
  BIGINT: Hash,
  DECIMAL: Hash,
  DOUBLE: Hash,
  FLOAT: Hash,
  DATE: Calendar,
  DATETIME: Calendar,
  TIMESTAMP: Calendar,
  BOOLEAN: ToggleLeft,
  BOOL: ToggleLeft,
};

// ── 子组件：属性预览行 ──────────────────────────────────────

/** 单行属性预览 Props */
interface PropertyPreviewRowProps {
  /** 属性编码 */
  code: string;
  /** 属性名称 */
  name: string;
  /** 属性类型 */
  propertyType: string;
}

/** 属性预览行 — 展示 code:type 格式 */
const PropertyPreviewRow: React.FC<PropertyPreviewRowProps> = memo(
  ({ code, name, propertyType }) => {
    const TypeIcon = PROP_TYPE_ICON[propertyType?.toUpperCase()] || Type;
    const displayLabel = name || code;

    return (
      <div
        className="flex items-center gap-1.5 text-[10px] text-slate-400 leading-none truncate"
        title={`${displayLabel}: ${propertyType}`}
      >
        <TypeIcon size={10} className="text-slate-500 shrink-0" />
        <span className="font-mono text-slate-500 truncate">{code}</span>
        <span className="text-slate-600">:</span>
        <span className="text-slate-500 text-[9px] uppercase">{propertyType}</span>
      </div>
    );
  }
);

PropertyPreviewRow.displayName = "PropertyPreviewRow";

// ── 主组件 ──────────────────────────────────────────────────

/**
 * EntityNode — 本体实体画布节点
 *
 * @param props - ReactFlow NodeProps<EntityNodeData>
 */
function EntityNode({ data, selected }: NodeProps<Node<EntityNodeData>>) {
  const {
    code,
    name,
    entityType = "MASTER",
    propertyCount = 0,
    properties: previewProperties,
  } = data;

  // 实体类型配置
  const typeConfig = ENTITY_TYPE_CONFIG[entityType] || DEFAULT_TYPE_CONFIG;
  const TypeIcon = typeConfig.icon;

  // 取前 3 个属性用于预览
  const previewProps: PropertyPreviewRowProps[] = Array.isArray(previewProperties)
    ? previewProperties.slice(0, 3)
    : [];

  // 选中态样式
  const selectedRing = selected
    ? "ring-2 ring-indigo-500 ring-offset-1 ring-offset-[#0f1117]"
    : "";

  return (
    <div
      className={`
        relative w-[220px] rounded-xl border
        bg-[#141924] border-[#1E293B]
        shadow-lg shadow-black/20
        transition-all duration-150
        cursor-grab active:cursor-grabbing
        hover:border-[#334155]
        ${selectedRing}
      `}
    >
      {/* ── 顶部 Handle (Target) ── */}
      <Handle
        type="target"
        position={Position.Top}
        className="!w-3 !h-3 !bg-indigo-500 !border-2 !border-[#0f1117] !top-[-6px]"
      />

      {/* ── 节点头部 ── */}
      <div className="px-3.5 pt-3 pb-2">
        <div className="flex items-start gap-2.5">
          {/* 类型图标 */}
          <div
            className={`
              shrink-0 p-1.5 rounded-lg
              ${typeConfig.bg} ${typeConfig.color}
            `}
          >
            <TypeIcon size={16} />
          </div>

          {/* 名称 + 编码 */}
          <div className="flex-1 min-w-0">
            <h3 className="text-[13px] font-bold text-white leading-tight truncate">
              {name}
            </h3>
            <p className="text-[10px] font-mono text-slate-500 mt-0.5 truncate">
              {code}
            </p>
          </div>
        </div>

        {/* 类型标签 */}
        <div className="flex items-center gap-1.5 mt-2">
          <span
            className={`
              text-[9px] px-1.5 py-0.5 rounded font-medium
              ${typeConfig.bg} ${typeConfig.color}
            `}
          >
            {typeConfig.label}
          </span>
          {propertyCount > 0 && (
            <span className="text-[9px] text-slate-600">
              {propertyCount} 属性
            </span>
          )}
        </div>
      </div>

      {/* ── 属性预览区 ── */}
      {previewProps.length > 0 && (
        <div className="px-3.5 pb-2.5 space-y-1 border-t border-[#1E293B] pt-2">
          {previewProps.map((prop, idx) => (
            <PropertyPreviewRow
              key={`${prop.code}-${idx}`}
              code={prop.code}
              name={prop.name}
              propertyType={prop.propertyType}
            />
          ))}
          {/* 属性溢出提示 */}
          {propertyCount > 3 && (
            <p className="text-[9px] text-slate-600 italic mt-0.5">
              +{propertyCount - 3} 更多属性...
            </p>
          )}
        </div>
      )}

      {/* ── 空属性提示 ── */}
      {previewProps.length === 0 && (
        <div className="px-3.5 pb-3 border-t border-[#1E293B] pt-2">
          <p className="text-[9px] text-slate-600 italic">暂无属性</p>
        </div>
      )}

      {/* ── 底部 Handle (Source) ── */}
      <Handle
        type="source"
        position={Position.Bottom}
        className="!w-3 !h-3 !bg-emerald-500 !border-2 !border-[#0f1117] !bottom-[-6px]"
      />

      {/* ── 左侧 Handle ── */}
      <Handle
        type="source"
        position={Position.Left}
        id="left"
        className="!w-2 !h-2 !bg-slate-500 !border-2 !border-[#0f1117] !left-[-4px]"
      />

      {/* ── 右侧 Handle ── */}
      <Handle
        type="source"
        position={Position.Right}
        id="right"
        className="!w-2 !h-2 !bg-slate-500 !border-2 !border-[#0f1117] !right-[-4px]"
      />
    </div>
  );
}

export default memo(EntityNode);
