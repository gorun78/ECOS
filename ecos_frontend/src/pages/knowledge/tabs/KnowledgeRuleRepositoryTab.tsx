import React from 'react';
import { FileText } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';

export default function KnowledgeRuleRepositoryTab() {
  const { locale } = useLanguage();
  const { styles } = useTheme();

  return (
    <div className="space-y-6">
      <div className="flex items-center gap-3">
        <FileText size={24} className="text-amber-400" />
        <div>
          <h2 className={`text-lg font-bold ${styles.cardText}`}>
            {locale === 'zh' ? '规则库' : 'Rule Repository'}
          </h2>
          <p className={`text-xs ${styles.cardTextMuted}`}>
            {locale === 'zh' ? '管理和版本化知识治理规则' : 'Manage and version knowledge governance rules'}
          </p>
        </div>
      </div>
    </div>
  );
}
