/**
 * EvidenceDrawer — 证据抽屉（详设-08 §2.2「证据抽屉」总则：任意数字/断言可点开 来源 · 版本 · 快照）。
 *
 * 真值纪律：证据三要素只从 props 传入（`GET /api/v1/wagent/evidence?claim=` 消费结果）；
 * 无证据 / 单项缺失一律显式「无来源」，不得补零、补占位、伪造快照（§5.2 空结果显「无来源」）。
 * 结构性颜色全部 useTheme() token；文案全部 t()。
 * @license Apache-2.0
 */

import React from "react";
import { X } from "lucide-react";
import { useTheme } from "../../ThemeContext";
import { useLanguage } from "../../LanguageContext";

/** 证据三要素 bundle（引擎返回；单字段缺失按「无来源」渲染） */
export interface EvidenceBundle {
  source?: string | null;
  version?: string | null;
  snapshot?: string | null;
}

export interface EvidenceDrawerProps {
  open: boolean;
  /** 被点开溯源的断言/数字原文（调用方传入） */
  claim?: string | null;
  /** 证据数据；null/undefined/字段全缺 ⇒ 三项均显示「无来源」 */
  evidence?: EvidenceBundle | null;
  /** 关闭抽屉（父级控制 open） */
  onClose: () => void;
}

export default function EvidenceDrawer({ open, claim, evidence, onClose }: EvidenceDrawerProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  if (!open) return null;

  /** 单项要素渲染：缺失显式「无来源」，不渲染空串 */
  const elementRow = (label: string, value?: string | null) => (
    <div className={`rounded-lg border p-3 space-y-1 ${styles.cardBorder}`}>
      <p className={`text-xs font-semibold ${styles.cardTextMuted}`}>{label}</p>
      <p className={`text-xs leading-normal font-mono ${value ? styles.cardText : styles.cardTextMuted}`}>
        {value ? value : t("common.evidence.none")}
      </p>
    </div>
  );

  return (
    <div className="fixed inset-0 z-40" role="dialog" aria-modal="true" aria-label={t("common.evidence.title")}>
      <div className={`absolute inset-0 ${styles.overlayBg}`} onClick={onClose} aria-hidden="true" />
      <aside
        className={`absolute inset-y-0 right-0 w-full max-w-md flex flex-col gap-4 p-6 border-l overflow-y-auto ${styles.cardBg} ${styles.cardBorder}`}
      >
        <header className="flex items-center justify-between">
          <h3 className={`text-sm font-semibold ${styles.cardText}`}>{t("common.evidence.title")}</h3>
          <button
            type="button"
            onClick={onClose}
            aria-label={t("common.close")}
            className={`p-1 rounded cursor-pointer ${styles.cardTextMuted}`}
          >
            <X className="w-4 h-4" aria-hidden="true" />
          </button>
        </header>

        {claim && (
          <p className={`text-xs leading-normal ${styles.cardText}`}>
            <span className={styles.cardTextMuted}>{t("common.evidence.claim")}: </span>
            {claim}
          </p>
        )}

        <div className="space-y-3">
          {elementRow(t("common.evidence.source"), evidence?.source)}
          {elementRow(t("common.evidence.version"), evidence?.version)}
          {elementRow(t("common.evidence.snapshot"), evidence?.snapshot)}
        </div>
      </aside>
    </div>
  );
}
