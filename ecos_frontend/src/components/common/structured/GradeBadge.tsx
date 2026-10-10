/**
 * GradeBadge — 等级徽章（详设-08 §2.2「等级徽章」总则）。
 *
 * 无障碍硬约束：颜色 + 文字双重标识 —— 徽章正文恒为等级字母（A/B/C/D），
 * 不依赖纯色区分等级；色值一律取 useTheme() 语义 token（§七.1/§七.3），
 * 组件内禁止 if(theme===...) 分支与字面色阶。
 * 等级数据来自 props（引擎端点消费结果），组件不做任何推断。
 * @license Apache-2.0
 */

import React from "react";
import { useTheme } from "../../ThemeContext";
import { useLanguage } from "../../LanguageContext";

/** 四档等级（与后端 DQ 等级/就绪度等级对齐；F 档不在本组件契约内，出现时由调用方按原文渲染） */
export type GradeLevel = "A" | "B" | "C" | "D";

export interface GradeBadgeProps {
  level: GradeLevel;
  /** 可选分值（引擎原值，如 0.93），仅作补充展示，缺失不补 0 */
  score?: number | null;
  className?: string;
}

export default function GradeBadge({ level, score, className = "" }: GradeBadgeProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  // 语义色映射：A=success B=info C=warning D=danger；文字字母始终在，满足非纯色依赖
  const toneClasses: Record<GradeLevel, string> = {
    A: `${styles.successBg} ${styles.successText} ${styles.successBorder}`,
    B: `${styles.infoBg} ${styles.infoText} ${styles.infoBorder}`,
    C: `${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`,
    D: `${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`,
  };

  return (
    <span
      role="img"
      aria-label={t("common.grade.aria", { level })}
      className={`inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs font-bold ${toneClasses[level]} ${className}`}
    >
      {level}
      {score !== undefined && score !== null && !Number.isNaN(score) && (
        <span className="font-mono text-[10px]">{score}</span>
      )}
    </span>
  );
}
