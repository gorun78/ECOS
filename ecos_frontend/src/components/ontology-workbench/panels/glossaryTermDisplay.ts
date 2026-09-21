/**
 * glossaryTermDisplay — 术语（词条）徽标展示口径的唯一出口。
 *
 * 本体工作台右栏「术语关联」面板（GlossaryBindingPanel）与对象类型详情「术语」Tab
 * （GlossaryTab）共用，避免两处重复维护状态 / 类型的标签映射与配色。
 * 标签一律返回 i18n key，由调用方用 useLanguage() 的 t() 渲染；
 * 配色一律走 useTheme() 的 styles 令牌，禁止硬编码颜色。
 *
 * @license Apache-2.0
 */

import type { ThemeStyles } from "../../ThemeContext";

/** 词条状态 → i18n key（未知状态返回空串，由调用方回退原始值） */
const TERM_STATUS_LABEL_KEYS: Record<string, string> = {
  active: "ow.glossaryBinding.status.published",
  PUBLISHED: "ow.glossaryBinding.status.published",
  DRAFT: "ow.glossaryBinding.status.draft",
  DEPRECATED: "ow.glossaryBinding.status.deprecated",
  ARCHIVED: "ow.glossaryBinding.status.archived",
};

/** 词条类型 → i18n key（未知类型返回空串，由调用方回退原始值） */
const TERM_TYPE_LABEL_KEYS: Record<string, string> = {
  ENTITY: "ow.glossaryBinding.termType.entity",
  RELATION: "ow.glossaryBinding.termType.relation",
  METRIC: "ow.glossaryBinding.termType.metric",
  FUNCTION: "ow.glossaryBinding.termType.function",
  CONCEPT: "ow.glossaryBinding.termType.concept",
};

/** 词条状态徽标的 i18n key（未命中返回空串） */
export function termStatusLabelKey(status: string): string {
  return TERM_STATUS_LABEL_KEYS[status] ?? "";
}

/** 词条类型徽标的 i18n key（未命中返回空串） */
export function termTypeLabelKey(termType: string): string {
  return TERM_TYPE_LABEL_KEYS[termType] ?? "";
}

/** 词条状态徽标配色（未命中按「已归档」口径降级为中性色） */
export function termStatusClass(status: string, styles: ThemeStyles): string {
  if (status === "active" || status === "PUBLISHED") {
    return `${styles.successBg} ${styles.successText} ${styles.successBorder}`;
  }
  if (status === "DRAFT") {
    return `${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`;
  }
  if (status === "DEPRECATED") {
    return `${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`;
  }
  return `${styles.badgeBg} ${styles.cardTextMuted} ${styles.cardBorder}`;
}
