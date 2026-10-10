/**
 * ReadinessCard — 就绪度评估卡（详设-08 §2.2，消费 `GET /api/v1/wagent/readiness?mission=` 结果）。
 *
 * 真值纪律：当前/目标等级与差距明细只从 props 传入（引擎端点消费结果）；
 * 403 时由调用方渲染 DeniedPanel（禁止降级为默认值，§5.2），等级缺失显示「无数据」。
 * Readiness 低于目标等级的降级提示由 DegradationBanner 承担（本卡只呈现数据）。
 * @license Apache-2.0
 */

import React from "react";
import { useTheme } from "../../ThemeContext";
import { useLanguage } from "../../LanguageContext";
import StructuredCard from "./StructuredCard";
import type { GradeLevel } from "./GradeBadge";

/** 单条就绪度差距（引擎返回，禁止前端补造） */
export interface ReadinessGap {
  id: string;
  /** 差距描述（业务语言，来自引擎） */
  description: string;
}

export interface ReadinessCardProps {
  /** 当前就绪等级（A/B/C/D 或引擎给出的等级原文）；null/undefined 显示「无数据」 */
  currentLevel?: GradeLevel | string | null;
  /** 目标就绪等级 */
  targetLevel?: GradeLevel | string | null;
  /** 差距明细列表 */
  gaps?: ReadinessGap[] | null;
  /** 是否已有证据（来自引擎结果，禁止组件内推断） */
  evidenceAvailable?: boolean;
  /** 打开证据抽屉回调 */
  onOpenEvidence?: () => void;
}

export default function ReadinessCard({
  currentLevel,
  targetLevel,
  gaps,
  evidenceAvailable,
  onOpenEvidence,
}: ReadinessCardProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const gapList = gaps ?? [];

  return (
    <StructuredCard
      title={t("common.readiness.title")}
      fields={[
        { key: "current", label: t("common.readiness.current_level"), value: currentLevel ?? null },
        { key: "target", label: t("common.readiness.target_level"), value: targetLevel ?? null },
      ]}
      evidenceAvailable={evidenceAvailable}
      onOpenEvidence={onOpenEvidence}
    >
      {gapList.length > 0 && (
        <div className="space-y-2">
          <p className={`text-xs font-semibold ${styles.cardTextMuted}`}>{t("common.readiness.gaps")}</p>
          <ul className={`space-y-2 text-xs ${styles.cardText}`}>
            {gapList.map((gap) => (
              <li key={gap.id} className={`rounded-lg border p-2 ${styles.cardBorder}`}>
                {gap.description}
              </li>
            ))}
          </ul>
        </div>
      )}
    </StructuredCard>
  );
}
