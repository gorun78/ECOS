/**
 * DesignerNode — Custom React Flow node renderer for WorkflowDesigner.
 * @license Apache-2.0
 */

import { Handle, Position, NodeProps } from "@xyflow/react";
import { useLanguage } from "../../components/LanguageContext";
import { NODE_TYPES, BoxIcon } from "./helpers";

export default function DesignerNode({ data, type, selected }: NodeProps) {
  const { t } = useLanguage();
  const def = NODE_TYPES[type as keyof typeof NODE_TYPES] || { icon: BoxIcon, color: "border-gray-300 bg-white", dot: "bg-gray-400" };
  const Icon = def.icon || BoxIcon;
  const label = (data.label as string) || t(`wf.node_type.${type}`);
  const nodeTypeLabel = t(`wf.node_type.${type}`);
  const isStart = type === "start";
  const isEnd = type === "end";

  return (
    <div className={`relative min-w-[140px] rounded-xl border-2 shadow-sm ${def.color} ${selected ? "ring-2 ring-indigo-400" : ""}`}>
      {!isStart && (
        <Handle type="target" position={Position.Top} className="!w-3 !h-3 !bg-slate-400 !border-2 !border-white" />
      )}
      <div className="px-3 py-2.5 flex items-center gap-2">
        <div className={`p-1 rounded-lg ${selected ? "bg-white" : "bg-white/70"}`}>
          <Icon className="w-4 h-4 text-slate-600" />
        </div>
        <div className="min-w-0">
          <div className="text-xs font-bold text-slate-700 truncate">{label}</div>
          <div className="text-[9px] text-slate-400">{nodeTypeLabel}</div>
        </div>
      </div>
      {!isEnd && (
        <Handle type="source" position={Position.Bottom} className="!w-3 !h-3 !bg-slate-400 !border-2 !border-white" />
      )}
      <div className={`absolute -top-1.5 -right-1.5 w-3 h-3 rounded-full border-2 border-white ${def.dot}`} />
    </div>
  );
}
