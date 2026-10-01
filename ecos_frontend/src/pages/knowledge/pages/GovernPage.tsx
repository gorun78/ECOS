/**
 * PMO-D Batch 2 — F7 GovernPage（B.0 信息架构归一 / 分册 04 B.3 govern 子 Tab）。
 *
 * B.0 目标 IA：govern 页 3 个子 Tab
 *   ├─ profiles      历史画像（生成任务、退化链、置信度、发布）   → ProfilesTab
 *   ├─ assumptions   假设库（情景、状态机、审批、unverified）    → AssumptionsTab
 *   └─ retrieval     RAG 调试（命中来源、CLS 掩蔽、vectorDegraded）→ RetrievalTab
 *
 * 注：本 Wave 前 GovernPage 渲染 4 子组件（AuditFeedCard + QualityMetricsTable +
 *   RuleRepoTable + EvalRunPanel）作为 3 区 grid。按 B.0 / F04-17，主结构改为
 *   3 子 Tab（profiles / assumptions / retrieval）；原 4 组件保留在
 *   `components/govern/` 供 deep-link 与后续 Wave 复用（概览质量指标上提 overview）。
 *
 * 主题 §4.1：0 硬编码色值（useTheme().styles token + tailwind 语义类）
 * i18n §4.3：0 硬编码中文（knowledge.govern.* / knowledge.profiles.assumptions.retrieval.*）
 */
import { useMemo, useState } from 'react';
import type { LucideIcon } from 'lucide-react';
import { Fan, GitFork, ScanSearch, ShieldCheck } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import ProfilesTab from './components/govern/ProfilesTab';
import AssumptionsTab from './components/govern/AssumptionsTab';
import RetrievalTab from './components/govern/RetrievalTab';

const SUB_TABS = [
  { id: 'profiles', icon: Fan, i18nKey: 'knowledge.govern.sub_profiles' },
  { id: 'assumptions', icon: GitFork, i18nKey: 'knowledge.govern.sub_assumptions' },
  { id: 'retrieval', icon: ScanSearch, i18nKey: 'knowledge.govern.sub_retrieval' },
] as const;

type SubTabId = (typeof SUB_TABS)[number]['id'];

export default function GovernPage() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [active, setActive] = useState<SubTabId>('profiles');

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

      {/* 3 子 Tab 切换条（B.0） */}
      <div className="flex items-center gap-1 border-b" style={{ borderColor: styles.cardBorder }}>
        {SUB_TABS.map((tab) => {
          const Icon: LucideIcon = tab.icon;
          const isActive = active === tab.id;
          return (
            <button
              key={tab.id}
              type="button"
              role="tab"
              aria-selected={isActive}
              aria-controls={`govern-panel-${tab.id}`}
              onClick={() => setActive(tab.id)}
              className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-t-md text-[11px] font-medium border-b-2 -mb-px cursor-pointer transition ${
                isActive ? 'font-bold border-transparent' : 'opacity-70 hover:opacity-100 border-transparent'
              }`}
              style={{
                color: isActive ? styles.accentText : styles.cardTextMuted,
                borderColor: isActive ? styles.accentText : 'transparent',
              }}
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
        aria-labelledby={`govern-tab-${active}`}
        className="flex-1 min-h-0"
      >
        {active === 'profiles' && <ProfilesTab />}
        {active === 'assumptions' && <AssumptionsTab />}
        {active === 'retrieval' && <RetrievalTab />}
      </div>
      {/* 保留 activeMeta 引用以便 aria 关联（未来可加 aria-labelledby 到 <button id>） */}
      <span className="hidden" aria-hidden>{activeMeta?.id}</span>
    </div>
  );
}
