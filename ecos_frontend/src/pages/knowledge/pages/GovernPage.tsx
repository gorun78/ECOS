/**
 * PMO-D Batch 2 — F7 GovernPage（B.0 信息架构归一 / 分册 04 B.3 govern 子 Tab）。
 *
 * 目标 IA（检视报告行 180-181 / 191 订正后）：govern 页 7 个子 Tab
 *   ├─ profiles    历史画像（生成任务、退化链、置信度、发布）      → ProfilesTab
 *   ├─ assumptions 假设库（情景、状态机、审批、unverified）        → AssumptionsTab
 *   ├─ retrieval   RAG 调试（命中来源、CLS 掩蔽、vectorDegraded）  → RetrievalTab
 *   ├─ audit       治理审计流（K-57 追加：原 components/govern 孤儿组件挂载）→ AuditFeedCard
 *   ├─ quality     质量指标（同上）                                → QualityMetricsTable
 *   ├─ rules       规则库（同上）                                  → RuleRepoTable
 *   └─ eval        评测运行（同上）                                → EvalRunPanel
 *
 * 行 180-181 订正说明：AuditFeedCard / QualityMetricsTable / RuleRepoTable /
 * EvalRunPanel 四组件此前 ~1340 行零 importer（治理域功能面页内实际消失），
 * 现全部挂为子 Tab；子 Tab 状态入 URL（?gsub=<id>，行 191：刷新/深链不再回 profiles）。
 *
 * 主题 §4.1：0 硬编码色值（useTheme().styles token + tailwind 语义类）
 * i18n §4.3：0 硬编码中文（knowledge.govern.* / knowledge.profiles.assumptions.retrieval.*）
 */
import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import type { LucideIcon } from 'lucide-react';
import { Activity, Fan, FlaskConical, GitFork, ListChecks, ScanSearch, ScrollText, ShieldCheck } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import ProfilesTab from './components/govern/ProfilesTab';
import AssumptionsTab from './components/govern/AssumptionsTab';
import RetrievalTab from './components/govern/RetrievalTab';
import AuditFeedCard from './components/govern/AuditFeedCard';
import QualityMetricsTable from './components/govern/QualityMetricsTable';
import RuleRepoTable from './components/govern/RuleRepoTable';
import EvalRunPanel from './components/govern/EvalRunPanel';

const SUB_TABS = [
  { id: 'profiles', icon: Fan, i18nKey: 'knowledge.govern.sub_profiles' },
  { id: 'assumptions', icon: GitFork, i18nKey: 'knowledge.govern.sub_assumptions' },
  { id: 'retrieval', icon: ScanSearch, i18nKey: 'knowledge.govern.sub_retrieval' },
  { id: 'audit', icon: ScrollText, i18nKey: 'knowledge.govern.sub_audit' },
  { id: 'quality', icon: Activity, i18nKey: 'knowledge.govern.sub_quality' },
  { id: 'rules', icon: ListChecks, i18nKey: 'knowledge.govern.sub_rules' },
  { id: 'eval', icon: FlaskConical, i18nKey: 'knowledge.govern.sub_eval' },
] as const;

type SubTabId = (typeof SUB_TABS)[number]['id'];

function isSubTabId(v: string | null): v is SubTabId {
  return !!v && (SUB_TABS as readonly { id: string }[]).some((x) => x.id === v);
}

export default function GovernPage() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  // 行 191 订正：子 Tab 入 URL（?gsub=），深链 / 刷新保持所选面板；非法值回落 profiles
  const [searchParams, setSearchParams] = useSearchParams();
  const urlSub = searchParams.get('gsub');
  const active: SubTabId = isSubTabId(urlSub) ? urlSub : 'profiles';

  const setActive = useCallback((id: SubTabId) => {
    setSearchParams((params) => {
      params.set('page', 'govern');
      params.set('gsub', id);
      params.delete('tab');
      return params;
    }, { replace: false });
  }, [setSearchParams]);

  const activeMeta = useMemo(() => SUB_TABS.find((x) => x.id === active) ?? SUB_TABS[0], [active]);

  return (
    <div className="p-2 flex flex-col gap-3 h-full min-h-0" style={{ color: styles.cardText }}>
      {/* 顶栏：标题 */}
      <div className="flex items-center gap-2 px-3 py-2 rounded-md border"
           style={{ borderColor: styles.cardBorder, background: styles.cardBg }}>
        <ShieldCheck className="w-4 h-4 flex-shrink-0" style={{ color: styles.accentText }} />
        <span className="text-base font-bold">{t('knowledge.nav.page_govern')}</span>
        <span className="text-[10px] font-mono" style={{ color: styles.muted }}>
          {t('knowledge.govern.page_subtitle')}
        </span>
      </div>

      {/* 7 子 Tab 切换条（B.0 + 孤儿组件回收） */}
      <div className="flex items-center gap-1 border-b overflow-x-auto" style={{ borderColor: styles.cardBorder }}>
        {SUB_TABS.map((tab) => {
          const Icon: LucideIcon = tab.icon;
          const isActive = active === tab.id;
          return (
            <button
              key={tab.id}
              id={`govern-tab-${tab.id}`}
              type="button"
              role="tab"
              aria-selected={isActive}
              aria-controls={`govern-panel-${tab.id}`}
              onClick={() => setActive(tab.id)}
              className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-t-md text-[11px] font-medium border-b-2 -mb-px cursor-pointer transition whitespace-nowrap ${
                isActive ? 'font-bold' : 'opacity-70 hover:opacity-100'
              } border-transparent`}
              style={{ color: isActive ? styles.accentText : styles.cardTextMuted }}
            >
              <Icon className="w-3.5 h-3.5" />
              {t(tab.i18nKey)}
            </button>
          );
        })}
      </div>

      {/* 子 Tab 内容（活跃面板） */}
      <div
        id={`govern-panel-${active}`}
        role="tabpanel"
        aria-labelledby={`govern-tab-${activeMeta.id}`}
        className="flex-1 min-h-0 overflow-y-auto"
      >
        {active === 'profiles' && <ProfilesTab />}
        {active === 'assumptions' && <AssumptionsTab />}
        {active === 'retrieval' && <RetrievalTab />}
        {active === 'audit' && <AuditFeedCard />}
        {active === 'quality' && <QualityMetricsTable />}
        {active === 'rules' && <RuleRepoTable />}
        {active === 'eval' && <EvalRunPanel />}
      </div>
    </div>
  );
}
