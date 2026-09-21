/**
 * SdResourceNode.tsx — 沙盘资源节点
 *
 * 6 类资源（DATASOURCE / ONTOLOGY_ENTITY / KNOWLEDGE_ARTICLE /
 * AGENT_PROFILE / SECURITY_POLICY / INTERFACE_REF）统一节点。
 *
 * 视觉：图标 + 双字 label + 状态点（ok=success, warn=warning, error=danger）
 * Handles：top = target（被 hub bind 入），bottom/right = source（向 hub 发出 / 向 mind feed）
 *
 * @license Apache-2.0
 */

import React, { memo } from "react";
import { Handle, Position, type NodeProps, type Node } from "@xyflow/react";
import {
  Database,
  Network,
  BookOpen,
  Cpu,
  ShieldCheck,
  Plug,
} from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import type { SdBResourceData, ResourceCategory } from "./types";

// ── 资源 → 图标 + t() key 映射 ─────────────────────────────────

const RESOURCE_CONFIG: Record<
  ResourceCategory,
  {
    icon: React.ComponentType<{ size?: number; className?: string }>;
    i18nKey: `scenario.sandbox.resource.${ResourceCategory}`;
  }
> = {
  DATASOURCE: { icon: Database, i18nKey: "scenario.sandbox.resource.DATASOURCE" },
  ONTOLOGY_ENTITY: { icon: Network, i18nKey: "scenario.sandbox.resource.ONTOLOGY_ENTITY" },
  KNOWLEDGE_ARTICLE: { icon: BookOpen, i18nKey: "scenario.sandbox.resource.KNOWLEDGE_ARTICLE" },
  AGENT_PROFILE: { icon: Cpu, i18nKey: "scenario.sandbox.resource.AGENT_PROFILE" },
  SECURITY_POLICY: { icon: ShieldCheck, i18nKey: "scenario.sandbox.resource.SECURITY_POLICY" },
  INTERFACE_REF: { icon: Plug, i18nKey: "scenario.sandbox.resource.INTERFACE_REF" },
};

function SdResourceNodeComponent({ data, selected }: NodeProps<Node<SdBResourceData>>) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const category = data.category ?? "DATASOURCE";
  const cfg = RESOURCE_CONFIG[category] ?? {
    icon: Database,
    i18nKey: "scenario.sandbox.resource.DATASOURCE" as const,
  };
  const CategoryIcon = cfg.icon;

  const statusLabel =
    data.status === "warn" ? t("scenario.sandbox.status.SUSPENDED") :
    data.status === "error" ? t("scenario.sandbox.status.SUSPENDED") :
    t("scenario.sandbox.status.ACTIVE");
  const statusTextClass =
    data.status === "warn" ? styles.warningText :
    data.status === "error" ? styles.dangerText :
    styles.successText;

  const selectedRing = selected ? "ring-2 ring-offset-1 ring-offset-black/30" : "";

  return (
    <div
      className={`
        relative w-[180px] rounded-lg border
        ${styles.cardBg} ${styles.cardBorder}
        shadow-md transition-all duration-150
        cursor-grab active:cursor-grabbing
        ${selectedRing}
      `}
    >
      {/* top target handle（资源 → 入 hub）*/}
      <Handle
        type="target"
        position={Position.Top}
        className="!w-2.5 !h-2.5 !bg-black/30 !border-2 !border-white/60 !top-[-5px]"
      />

      {/* body */}
      <div className="px-3 py-2.5">
        <div className="flex items-center gap-2">
          <div className={`shrink-0 p-1.5 rounded-md ${styles.badgeBg}`}>
            <CategoryIcon size={14} className={styles.accentText} />
          </div>
          <div className="min-w-0 flex-1">
            <h3 className={`text-[12px] font-bold leading-tight truncate ${styles.cardText}`}>
              {data.targetName || t(cfg.i18nKey)}
            </h3>
            <p className={`text-[10px] font-mono mt-0.5 truncate ${styles.cardTextMuted}`}>
              {data.targetId}
            </p>
          </div>
        </div>

        {/* status + children */}
        <div className="flex items-center gap-1.5 mt-2 text-[10px]">
          <span className={`font-semibold ${statusTextClass}`}>{statusLabel}</span>
          {typeof data.childCount === "number" && data.childCount > 0 && (
            <span className={styles.muted}>· {data.childCount}</span>
          )}
        </div>
      </div>

      {/* bottom source handle（资源 → 出 hub bind）*/}
      <Handle
        type="source"
        position={Position.Bottom}
        className="!w-2.5 !h-2.5 !bg-black/30 !border-2 !border-white/60 !bottom-[-5px]"
      />
    </div>
  );
}

export default memo(SdResourceNodeComponent);
