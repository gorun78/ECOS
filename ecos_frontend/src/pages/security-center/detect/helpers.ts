// ── Shared styles ────────────────────────────────────────────
import type { ThemeStyles } from "../../../components/ThemeContext";

export function inputClasses(styles: ThemeStyles) {
  return `w-full px-3 py-2 rounded-lg text-sm ${styles.inputBg} ${styles.inputText} border ${styles.inputBorder} focus:outline-none focus:ring-2 focus:ring-blue-500/50`;
}

export function btnPrimary(styles: ThemeStyles) {
  return `px-4 py-2 rounded-lg text-sm font-medium ${styles.accentSolidText} ${styles.accentBg} ${styles.accentHover} transition-colors cursor-pointer disabled:opacity-50`;
}

export function btnSecondary(styles: ThemeStyles) {
  return `px-4 py-2 rounded-lg text-sm font-medium ${styles.cardBg} ${styles.cardText} border ${styles.cardBorder} ${styles.sidebarHoverBg} transition-colors cursor-pointer`;
}

export function badgeClasses(active: boolean, styles: ThemeStyles) {
  return active
    ? `px-2 py-0.5 rounded-full text-xs font-medium ${styles.badgeBg} ${styles.badgeText}`
    : `px-2 py-0.5 rounded-full text-xs font-medium ${styles.cardBg} ${styles.cardTextMuted}`;
}
