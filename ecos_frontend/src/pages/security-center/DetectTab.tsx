/**
 * DetectTab — 安全策略管理中心
 * 4个子Tab: ABAC策略评估 / RLS策略CRUD / CLS策略CRUD / 脱敏规则管理
 * @license Apache-2.0
 */

import { useState } from 'react';
import { Shield, Lock, Columns3, EyeOff } from 'lucide-react';
import { useLanguage } from '../../components/LanguageContext';
import { useTheme } from '../../components/ThemeContext';
import AbacEvaluator from './detect/AbacEvaluator';
import RlsPolicyManager from './detect/RlsPolicyManager';
import ClsPolicyManager from './detect/ClsPolicyManager';
import MaskingRuleManager from './detect/MaskingRuleManager';
import AbacPolicyManager from './detect/AbacPolicyManager';

// ── Sub-tab definitions ──────────────────────────────────────
type SubTabId = 'abac-crud' | 'abac' | 'rls' | 'cls' | 'masking';

interface SubTabDef {
  id: SubTabId;
  labelKey: string;
  icon: typeof Shield;
}

const SUB_TABS: SubTabDef[] = [
  { id: 'abac-crud', labelKey: 'sec.abac.crud.title', icon: Shield },
  { id: 'abac', labelKey: 'sec.abac.evaluate', icon: Shield },
  { id: 'rls', labelKey: 'sec.rls.title', icon: Lock },
  { id: 'cls', labelKey: 'sec.cls.title', icon: Columns3 },
  { id: 'masking', labelKey: 'sec.mask.title', icon: EyeOff },
];

// ── Main DetectTab ───────────────────────────────────────────
export default function DetectTab() {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();
  const [activeSubTab, setActiveSubTab] = useState<SubTabId>('abac-crud');

  return (
    <div className="h-full flex flex-col">
      {/* Sub-tab navigation */}
      <div className={`flex items-center border-b ${styles.appBorder} px-4 shrink-0`}>
        {SUB_TABS.map(st => {
          const Icon = st.icon;
          const isActive = activeSubTab === st.id;
          return (
            <button
              key={st.id}
              onClick={() => setActiveSubTab(st.id)}
              className={`flex items-center gap-2 px-4 py-2.5 text-sm font-medium border-b-2 transition-all duration-150 cursor-pointer
                ${isActive
                  ? `${styles.accentText} border-b-current`
                  : `${styles.muted} border-b-transparent hover:${styles.sidebarHoverBg}`
                }`}
            >
              <Icon size={16} />
              <span>{t(st.labelKey)}</span>
            </button>
          );
        })}
      </div>

      {/* Sub-tab content */}
      <div className="flex-1 overflow-hidden">
        {activeSubTab === 'abac-crud' && <AbacPolicyManager t={t} locale={locale} styles={styles} />}
        {activeSubTab === 'abac' && <AbacEvaluator t={t} locale={locale} styles={styles} />}
        {activeSubTab === 'rls' && <RlsPolicyManager t={t} locale={locale} styles={styles} />}
        {activeSubTab === 'cls' && <ClsPolicyManager t={t} locale={locale} styles={styles} />}
        {activeSubTab === 'masking' && <MaskingRuleManager t={t} locale={locale} styles={styles} />}
      </div>
    </div>
  );
}
