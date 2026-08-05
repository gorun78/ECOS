/**
 * SecurityCenterLayout — 安全中心统一入口（三Tab布局）
 * 事前(Prevent) / 事中(Detect) / 事后(Audit)
 * @license Apache-2.0
 */

import React, { useState } from 'react';
import { Shield, Scan, FileText } from 'lucide-react';
import { useLanguage } from '../../components/LanguageContext';
import { useTheme } from '../../components/ThemeContext';
import PreventTab from './PreventTab';
import DetectTab from './DetectTab';
import AuditTab from './AuditTab';

type SecurityTab = 'prevent' | 'detect' | 'audit';

interface TabDef {
  id: SecurityTab;
  labelKey: string;
  icon: typeof Shield;
}

const TABS: TabDef[] = [
  { id: 'prevent', labelKey: 'security.prevent', icon: Shield },
  { id: 'detect', labelKey: 'security.detect', icon: Scan },
  { id: 'audit', labelKey: 'security.audit', icon: FileText },
];

export default function SecurityCenterLayout() {
  const { t, locale } = useLanguage();
  const { styles } = useTheme();
  const [activeTab, setActiveTab] = useState<SecurityTab>('prevent');

  return (
    <div className={`h-full flex flex-col ${styles.appBg} ${styles.appText}`}>
      {/* ── Top Tab Bar ── */}
      <div className={`flex items-center border-b ${styles.appBorder} px-4 py-0 shrink-0`}>
        <div className="flex items-center gap-1">
          {TABS.map(tab => {
            const Icon = tab.icon;
            const isActive = activeTab === tab.id;
            return (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id)}
                className={`flex items-center gap-2 px-4 py-2.5 text-sm font-medium rounded-t-md border-b-2 transition-all duration-150 cursor-pointer
                  ${isActive
                    ? `${styles.accentText} border-b-current`
                    : `${styles.muted} border-b-transparent hover:${styles.sidebarHoverBg}`
                  }`}
              >
                <Icon size={18} />
                <span>{t(tab.labelKey)}</span>
              </button>
            );
          })}
        </div>
        {/* Right-side spacer */}
        <div className="flex-1" />
        <span className={`text-xs ${styles.muted} font-mono`}>
          ECOS Security Center
        </span>
      </div>

      {/* ── Tab Content ── */}
      <div className="flex-1 overflow-hidden">
        {activeTab === 'prevent' && <PreventTab />}
        {activeTab === 'detect' && <DetectTab />}
        {activeTab === 'audit' && <AuditTab />}
      </div>
    </div>
  );
}
