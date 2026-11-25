/**
 * ConfigValueCell — 单条配置「当前值」的只读渲染单元。
 *
 * 按 valueKind 分派四种形态，全部只读（本页无写 UI，写入属系统配置管理页）：
 *   boolean → 语义徽标「启用/关闭」（不渲染假开关，避免误以为可点）
 *   enum    → 取值胶囊 + 候选集（后端 config_options 有才列）
 *   secret  → 默认打码，单行可点「显示/隐藏」，不写入任何持久化
 *   number/text → 等宽文本 + 空值破折号
 *
 * 颜色一律 useTheme() 语义 token；文案一律 t()；图标仅 lucide-react。
 *
 * @license SPDX-License-Identifier: Apache-2.0
 */
import React, { useState } from "react";
import { Eye, EyeOff } from "lucide-react";
import { useTheme } from "../../components/ThemeContext";
import { useLanguage } from "../../components/LanguageContext";
import { boolValue, maskSecret, type SysConfigItem, type ValueKind } from "./engineConfigModel";

interface ConfigValueCellProps {
  item: SysConfigItem;
  kind: ValueKind;
}

export default function ConfigValueCell({ item, kind }: ConfigValueCellProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [revealed, setRevealed] = useState(false);

  const raw = item.value ?? "";

  if (!raw) {
    return (
      <span className={`text-[11px] italic ${styles.cardTextMuted}`}>
        {t("platform.governance.config.emptyValue")}
      </span>
    );
  }

  if (kind === "boolean") {
    const on = boolValue(raw);
    return (
      <span
        className={`inline-flex items-center rounded border px-1.5 py-0.5 text-[10px] font-mono font-bold ${on ? `${styles.successBg} ${styles.successText} ${styles.successBorder}` : `${styles.badgeBg} ${styles.badgeText} ${styles.cardBorder}`}`}
      >
        {raw}
      </span>
    );
  }

  if (kind === "secret") {
    const masked = maskSecret(raw);
    return (
      <span className="inline-flex items-center gap-1.5 min-w-0">
        <span className="font-mono break-all">{revealed ? raw : masked}</span>
        <button
          type="button"
          onClick={() => setRevealed((v) => !v)}
          className={`shrink-0 inline-flex items-center gap-0.5 rounded border px-1 py-0.5 text-[10px] cursor-pointer hover:opacity-80 ${styles.cardBorder} ${styles.cardTextMuted}`}
          aria-label={t(revealed ? "platform.governance.config.hide" : "platform.governance.config.show")}
          title={t(revealed ? "platform.governance.config.hide" : "platform.governance.config.show")}
        >
          {revealed ? <EyeOff size={10} /> : <Eye size={10} />}
          {t(revealed ? "platform.governance.config.hide" : "platform.governance.config.show")}
        </button>
      </span>
    );
  }

  if (kind === "enum") {
    return (
      <span className="inline-flex flex-wrap items-center gap-1.5 min-w-0">
        <span
          className={`inline-flex items-center rounded border px-1.5 py-0.5 text-[10px] font-mono font-bold ${styles.badgeBg} ${styles.badgeText} ${styles.cardBorder}`}
        >
          {raw}
        </span>
        {item.options && item.options.length > 0 && (
          <span className={`text-[10px] font-mono ${styles.cardTextMuted}`}>
            {item.options.join(" / ")}
          </span>
        )}
      </span>
    );
  }

  return (
    <span className={`font-mono break-all ${kind === "number" ? "tabular-nums" : ""}`}>{raw}</span>
  );
}
