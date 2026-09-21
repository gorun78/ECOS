/**
 * SdMindCard.tsx — 心智变体卡片节点
 *
 * 视觉：脑 icon + label + 激活 radio（点切换，调 props.onPatch）+ 三要素摘要
 * Handles：top target（继承入 / hub bind），bottom source（继承出 → 子心智）
 *
 * 注：截获 ReactFlow 的 node 化 repack，props 以 `{ data, selected }` 形态下发；
 * onPatch 通过 data.mindId 重置触发 ReactFlow 内部 ref 调用（见 SandboxCanvas）。
 *
 * @license Apache-2.0
 */

import React, { useCallback, memo } from "react";
import { Handle, Position, type NodeProps, type Node } from "@xyflow/react";
import { Brain, Check, Sparkles } from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import type { SdBMindData } from "./types";

export interface SdMindCardExtras {
  /** 切换激活心智 */
  onPatch?: (mindId: string, partial: { active: boolean }) => void;
  /** 删除当前心智 */
  onRemove?: (mindId: string) => void;
  /** 复制为变体（inherit） */
  onCopy?: (mindId: string) => void;
}

/** 内部 props 形态（外层 SandboxCanvas wrap 注入） */
type InternalProps = NodeProps<Node<SdBMindData>> & SdMindCardExtras;

function SdMindCardComponent({ data, selected, onPatch, onRemove, onCopy }: InternalProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const selectedRing = selected ? "ring-2 ring-offset-1 ring-offset-black/30" : "";
  const activeCls = data.active ? styles.successBorder : "border-l-transparent";
  const btnCls = `shrink-0 p-1 rounded-md cursor-pointer ${styles.cardBorder}`;

  const handleRadio = useCallback(() => {
    if (onPatch) onPatch(data.mindId, { active: true });
  }, [data.mindId, onPatch]);

  const handleCopy = useCallback(
    (e: React.MouseEvent<HTMLElement>) => {
      e.stopPropagation();
      if (onCopy) onCopy(data.mindId);
    },
    [data.mindId, onCopy]
  );

  const handleDel = useCallback(
    (e: React.MouseEvent<HTMLElement>) => {
      e.stopPropagation();
      if (onRemove) onRemove(data.mindId);
    },
    [data.mindId, onRemove]
  );

  return (
    <div
      className={`
        relative w-[200px] rounded-lg border-l-4
        ${styles.cardBg} ${styles.cardBorder} ${activeCls}
        shadow-md transition-all duration-150
        cursor-grab active:cursor-grabbing
        ${selectedRing}
      `}
    >
      <Handle
        type="target"
        position={Position.Top}
        className="!w-2.5 !h-2.5 !bg-black/30 !border-2 !border-white/60 !top-[-5px]"
      />

      {/* header: icon + label + active radio */}
      <div className="px-3 py-2.5 flex items-start gap-2">
        <div className={`shrink-0 p-1.5 rounded-md ${styles.badgeBg}`}>
          <Brain size={14} className={styles.accentText} />
        </div>
        <div className="min-w-0 flex-1">
          <h3
            className={`text-[12px] font-bold leading-tight truncate ${
              data.active ? styles.successText : styles.cardText
            }`}
          >
            {data.label || t("scenario.sandbox.mind.empty")}
          </h3>
          <p className={`text-[10px] font-mono mt-0.5 truncate ${styles.muted}`}>
            {data.mindId}
          </p>
        </div>
        <button
          type="button"
          onClick={handleRadio}
          className={btnCls}
          title={t("scenario.sandbox.mind.active")}
        >
          {data.active ? (
            <Check size={14} className={styles.successText} />
          ) : (
            <Sparkles size={14} className={styles.muted} />
          )}
        </button>
        <button
          type="button"
          onClick={handleCopy}
          className={btnCls}
          title={t("scenario.sandbox.action.copy")}
        >
          <Brain size={12} className={styles.muted} />
        </button>
        <button
          type="button"
          onClick={handleDel}
          className={btnCls}
          title={t("scenario.sandbox.action.delete")}
        >
          <span className={`text-[11px] font-bold line-through ${styles.dangerText}`}>x</span>
        </button>
      </div>

      {/* 三要素摘要 */}
      {data.threeFactorSummary && (
        <div
          className={`px-3 pb-2.5 text-[10px] leading-normal line-clamp-3 ${styles.cardTextMuted}`}
        >
          {data.threeFactorSummary}
        </div>
      )}

      <Handle
        type="source"
        position={Position.Bottom}
        className="!w-2.5 !h-2.5 !bg-black/30 !border-2 !border-white/60 !bottom-[-5px]"
      />
    </div>
  );
}

export default memo(SdMindCardComponent);
