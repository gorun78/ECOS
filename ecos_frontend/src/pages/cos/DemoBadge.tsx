/**
 * DemoBadge — 「示意数据」徽标。
 * components/common/DemoBadge 在本批次独占文件约束下不可新建/引用，
 * 按规范以本页内联等价徽标实现（主题走 useTheme warning tokens）。
 * @license Apache-2.0
 */
import { useLanguage } from "../../components/LanguageContext";
import { useTheme } from "../../components/ThemeContext";

export default function DemoBadge() {
  const { t } = useLanguage();
  const { styles } = useTheme();
  return (
    <span
      className={`shrink-0 text-[8.5px] px-1.5 py-0.5 rounded-sm font-mono font-bold uppercase leading-none border ${styles.warningBorder} ${styles.warningBg} ${styles.warningText}`}
    >
      {t("cognition.cos.demobadge")}
    </span>
  );
}
