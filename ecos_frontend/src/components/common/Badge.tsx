/**
 * Badge — 公共徽标原语（H6-T5，见《前端开发规范》§七.7 FE-1 整改）
 * 语义状态色（success/warning/danger/info）全部取 useTheme() token，
 * 由 token 层保证明暗两态：light → bg-xx-50，dark → bg-xx-500/10（§七.3）。
 * 组件内禁止硬编码状态色字面量与 if(theme===...) 分支（§七.3 / §七.6）。
 * 徽标不是"品牌填充 + 反色前景"的强调件，故不使用主色反色豁免。
 * 文案由调用方通过 children/props 传入，组件内不做 i18n 硬编码（§六）。
 * @license Apache-2.0
 */

import React from "react";
import { useTheme } from "../ThemeContext";

/** 语义状态：四种状态色 + 中性（走通用 badge token） */
export type BadgeVariant = "success" | "warning" | "danger" | "info" | "neutral";

export interface BadgeProps {
  /** 语义状态，默认 neutral */
  variant?: BadgeVariant;
  /** 额外布局类名（仅排版，不含颜色） */
  className?: string;
  /** 徽标文案，来自调用方（中英皆可，组件不硬编码） */
  children: React.ReactNode;
}

/**
 * Badge — 主题感知的语义状态徽标。
 * 底/字/边三类 token 已按 4 主题预置明暗形态，切换主题自动生效，无需分支。
 */
export default function Badge({ variant = "neutral", className = "", children }: BadgeProps) {
  const { styles } = useTheme();

  // 每个语义色的底/字/边均取自主题 token（§七.3）；neutral 复用通用 badgeBg/badgeText
  const variantClasses: Record<BadgeVariant, string> = {
    success: `${styles.successBg} ${styles.successText} ${styles.successBorder}`,
    warning: `${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`,
    danger: `${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`,
    info: `${styles.infoBg} ${styles.infoText} ${styles.infoBorder}`,
    neutral: `${styles.badgeBg} ${styles.badgeText} ${styles.appBorder}`,
  };

  return (
    <span
      className={[
        "inline-flex items-center gap-1 rounded-full border px-2 py-0.5",
        "text-xs font-medium",
        variantClasses[variant],
        className,
      ].join(" ")}
    >
      {children}
    </span>
  );
}
