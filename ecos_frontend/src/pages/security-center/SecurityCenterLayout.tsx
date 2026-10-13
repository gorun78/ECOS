/**
 * SecurityCenterLayout — 安全中心统一入口（五Tab布局）
 * 事前(Prevent) / 事中(Detect) / 策略控制面(Policies) / 事后(Audit) / 审计查询(Audit Query)
 *
 * Tab 态单源于 URL search 参数（HashRouter 下即 `#/security-center?tab=...`，
 * 与 GatewayDiagnosticsPage 的 ?tab= 机制一致；报告 §4.7 行292：刷新不丢态）。
 * SecurityPolicyPage（?tab=policies）与 AuditQueryPage（?tab=audit-query|audit_query）
 * 在此挂载收口（报告 §4.7 行279-280 批次3「挂上」选项，两页为六态完整的控制面页，不删）。
 * @license Apache-2.0
 */

import React, { useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Shield, Scan, FileText, KeyRound, FileSearch } from 'lucide-react';
import { useLanguage } from '../../components/LanguageContext';
import { useTheme } from '../../components/ThemeContext';
import PreventTab from './PreventTab';
import DetectTab from './DetectTab';
import AuditTab from './AuditTab';
import SecurityPolicyPage from './SecurityPolicyPage';
import AuditQueryPage from './AuditQueryPage';

/** 安全中心 Tab id 单源（types.ts SecurityCenterViewProps 的 tab 联合与此对齐） */
type SecurityTab = 'prevent' | 'detect' | 'policies' | 'audit' | 'audit-query';

interface TabDef {
  id: SecurityTab;
  labelKey: string;
  icon: typeof Shield;
}

const TABS: TabDef[] = [
  { id: 'prevent', labelKey: 'security.prevent', icon: Shield },
  { id: 'detect', labelKey: 'security.detect', icon: Scan },
  { id: 'policies', labelKey: 'security.policies', icon: KeyRound },
  { id: 'audit', labelKey: 'security.audit', icon: FileText },
  { id: 'audit-query', labelKey: 'security.audit_query', icon: FileSearch },
];

const TAB_IDS = new Set<string>(TABS.map(t => t.id));

/** 归一化 ?tab= 参数：兼容页面头注释既有的 audit_query 下划线写法 → 'audit-query' */
function normalizeTabParam(raw: string | null): SecurityTab {
  if (raw === 'audit_query') return 'audit-query';
  return raw && TAB_IDS.has(raw) ? (raw as SecurityTab) : 'prevent';
}

export default function SecurityCenterLayout() {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const [searchParams, setSearchParams] = useSearchParams();

  // Tab 态只读自 URL（受控渲染，无本地 useState 副本 → 刷新/分享链接保态）
  const activeTab = normalizeTabParam(searchParams.get('tab'));

  const handleTabChange = useCallback((id: SecurityTab) => {
    const next = new URLSearchParams(searchParams);
    next.set('tab', id);
    setSearchParams(next, { replace: true });
  }, [searchParams, setSearchParams]);

  return (
    <div className={`h-full flex flex-col ${styles.appBg} ${styles.appText}`}>
      {/* ── Top Tab Bar（role=tablist，键盘可达 + aria-selected 同步 URL 态） ── */}
      <div className={`flex items-center border-b ${styles.appBorder} px-4 py-0 shrink-0`}>
        <div role="tablist" aria-label={t('security.center')} className="flex items-center gap-1">
          {TABS.map(tab => {
            const Icon = tab.icon;
            const isActive = activeTab === tab.id;
            return (
              <button type="button"
                key={tab.id}
                role="tab"
                id={`security-tab-${tab.id}`}
                aria-selected={isActive}
                aria-controls="security-tabpanel"
                onClick={() => handleTabChange(tab.id)}
                className={`flex items-center gap-2 px-4 py-2.5 text-sm font-medium rounded-t-md border-b-2 transition-all duration-150 cursor-pointer
                  ${isActive
                    ? `${styles.accentText} border-b-current`
                    : `${styles.muted} border-b-transparent ${styles.sidebarHoverBg}`
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

      {/* ── Tab Content（tabpanel 与 tab 按钮 aria 关联） ── */}
      <div
        role="tabpanel"
        id="security-tabpanel"
        aria-labelledby={`security-tab-${activeTab}`}
        className="flex-1 overflow-hidden"
      >
        {activeTab === 'prevent' && <PreventTab />}
        {activeTab === 'detect' && <DetectTab />}
        {activeTab === 'policies' && <SecurityPolicyPage />}
        {activeTab === 'audit' && <AuditTab />}
        {activeTab === 'audit-query' && <AuditQueryPage />}
      </div>
    </div>
  );
}
