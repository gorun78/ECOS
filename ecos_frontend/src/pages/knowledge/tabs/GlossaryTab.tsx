// 术语库 Tab —— PMO-55 批次 D：从 KnowledgeView 移出，保留在 OntologyWorkbenchLayout 的 glossary 分类槽位
// 此处仅为向后兼容 layout 引用（deleted 原 KnowledgeView 中的 GlossaryTab 13→15 重构）
// 实际术语管理 migration 后作为 buszhi 工作台的外挂最近逻辑存在
import { Library } from "lucide-react";
import { useTheme } from "../../../components/ThemeContext";
import { useLanguage } from "../../../components/LanguageContext";

export default function GlossaryTab() {
  const { styles } = useTheme();
  const { t, locale } = useLanguage();
  const title = locale === "zh"
    ? "术语库 (Glossary) — 已迁移至 buszhi 本体工作台"
    : "Glossary — migrated to buszhi ontology workbench";
  const hint = locale === "zh"
    ? "词汇管理已统一到 buszhi 的 ObjectType/Property 定义；本 Tab 仅作占位，导航至 buszhi 工作台。"
    : "Vocabulary management has been unified into buszhi's ObjectType/Property definitions. This tab is a placeholder; navigate to the buszhi workbench.";
  return (
    <div className={`border rounded-lg p-6 ${styles.cardBg} ${styles.cardBorder} ${styles.cardText}`}>
      <div className="flex items-center gap-2 text-sm font-semibold">
        <Library className="w-4 h-4" />
        <span>{title}</span>
      </div>
      <p className="mt-2 text-xs opacity-70">{hint}</p>
      <div className="mt-4 text-[11px] font-mono opacity-50">
        {t('knowledge.nav.updated_tag')}
      </div>
    </div>
  );
}
