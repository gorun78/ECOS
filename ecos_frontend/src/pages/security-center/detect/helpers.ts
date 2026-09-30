// ── Shared styles ────────────────────────────────────────────
export function inputClasses(styles: any) {
  return `w-full px-3 py-2 rounded-lg text-sm ${styles.inputBg} ${styles.inputText} border ${styles.inputBorder} focus:outline-none focus:ring-2 focus:ring-blue-500/50`;
}

export function btnPrimary(styles: any) {
  return `px-4 py-2 rounded-lg text-sm font-medium text-white ${styles.accentBg} ${styles.accentHover} transition-colors cursor-pointer disabled:opacity-50`;
}

export function btnSecondary(styles: any) {
  return `px-4 py-2 rounded-lg text-sm font-medium ${styles.cardBg} ${styles.cardText} border ${styles.cardBorder} hover:${styles.sidebarHoverBg} transition-colors cursor-pointer`;
}

export function badgeClasses(active: boolean, styles: any) {
  return active
    ? `px-2 py-0.5 rounded-full text-xs font-medium ${styles.badgeBg} ${styles.badgeText}`
    : `px-2 py-0.5 rounded-full text-xs font-medium bg-gray-200/50 text-gray-500 dark:bg-gray-700/50 dark:text-gray-400`;
}
