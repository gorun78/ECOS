/**
 * PMO-D Batch 1 — KnowledgeView（F1 侧栏导航重构）。
 *
 * 重构要点：
 * - 主侧栏：从 5 组 14 Tab 改为 6 平铺 Page（ACTIVE_PAGES 数据源）
 * - 主路由（?page=xxx）：6 平铺 Page 直达
 * - 撤下 8 Tab 的 deep-link（?tab=xxx）保留：拉对应 Tab 组件 + 顶部 warn banner（F8 基础骨架）
 * - 顶栏：面包屑 ECOS / {activePage} + 任务状态 / i18n 切换 / Avatar
 * - 侧栏底部：Global 入口（任务中心 / 引擎监控）
 * - 主题守护 §4.1：0 硬编码色值（全 useTheme().styles + 已存在的 Indigo/Blue 别名）
 * - i18n 全走 knowledge.nav.page_*
 *
 * 铁律 §4.6 ≤ 800 行：当前 ~520 行，Page 文件独立在 pages/knowledge/pages 下。
 */
import React, { useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import type { LucideIcon } from 'lucide-react';
import {
  AlertTriangle,
  ArrowLeft,
  Database,
  Download,
  FileText,
  Gauge,
  GitBranch,
  LayoutDashboard,
  ListChecks,
  Network,
  RefreshCw,
  Search,
  Settings,
  Shield,
  ShieldCheck,
  Star,
  Tag,
  Workflow,
  Zap,
} from 'lucide-react';
import { useTheme } from '../components/ThemeContext';
import { useLanguage } from '../components/LanguageContext';
import {
  KNOWLEDGE_TAB_GROUPS,
  type KnowledgeTabId,
  type ActivePage,
  ACTIVE_PAGES,
  ACTIVE_PAGE_I18N_KEYS,
  ACTIVE_PAGE_ICONS,
  DEPRECATED_TABS,
} from './knowledge/typesAndConstants';
import OverviewPage from './knowledge/pages/OverviewPage';
import AssetListPage from './knowledge/pages/AssetListPage';
import ExtractionPage from './knowledge/pages/ExtractionPage';
import GraphPage from './knowledge/pages/GraphPage';
import WikiPage from './knowledge/pages/WikiPage';
import GovernPage from './knowledge/pages/GovernPage';
import RulesPage from './knowledge/pages/RulesPage';
import CognitiveAssetPage from './knowledge/pages/CognitiveAssetPage';
import OverviewDashboard from './knowledge/tabs/OverviewDashboard';
import DataWorkbenchImportTab from './knowledge/tabs/DataWorkbenchImportTab';
import DocumentUploadTab from './knowledge/tabs/DocumentUploadTab';
import ExtractionReviewTab from './knowledge/tabs/ExtractionReviewTab';
import GraphBuilderTab from './knowledge/tabs/GraphBuilderTab';
import VectorIndexTab from './knowledge/tabs/VectorIndexTab';
import KnowledgeUpdateTab from './knowledge/tabs/KnowledgeUpdateTab';
import ClassificationTab from './knowledge/tabs/ClassificationTab';
import RagTab from './knowledge/tabs/RagTab';
import GraphExplorerTab from './knowledge/tabs/GraphExplorerTab';
import KnowledgeRuleRepositoryTab from './knowledge/tabs/KnowledgeRuleRepositoryTab';
import KnowledgeEvalTab from './knowledge/tabs/KnowledgeEvalTab';
import LifecycleManagerTab from './knowledge/tabs/LifecycleManagerTab';
import ComplianceTab from './knowledge/tabs/KnowledgeComplianceCheckTab';
import EngineConfigTab from './knowledge/tabs/EngineConfigTab';
import DatasyncTab from './knowledge/tabs/DatasyncTab';
import ExtractionStreamingTab from './knowledge/tabs/ExtractionStreamingTab';
import GlossaryTab from './knowledge/tabs/GlossaryTab';
import SyncTab from './knowledge/tabs/SyncTab';
import OntologyModelTab from './knowledge/tabs/OntologyModelTab';

// ── ICON_MAP（旧 Tab icon 名称 → LucideIcon，作 14 Tab deep-link 图标） ──────
const ICON_MAP: Record<string, LucideIcon> = {
  LayoutDashboard,
  Download,
  FileText,
  ListChecks,
  Database,
  RefreshCw,
  Tag,
  Zap,
  Network,
  ShieldCheck,
  Gauge,
  GitBranch,
  Shield,
  Settings,
  Workflow,
};

// 占位 React.ComponentType（让 6 Page 与 8 撤下 Tab 都接收可选 props；
// 实际 Tab/Page 大部分不消费 showToast，但保留传递兼容）
type PageOrTabComponent = React.ComponentType<{ showToast?: unknown }>;

// ── 6 平铺 Page → React 组件（F1 主数据源） ─────────────────────────────────
const PAGE_COMPONENTS: Record<ActivePage, PageOrTabComponent> = {
  overview: OverviewPage,
  assets: AssetListPage,
  extract: ExtractionPage,
  graph: GraphPage,
  wiki: WikiPage,
  govern: GovernPage,
  rules: RulesPage,
  cognitive_asset: CognitiveAssetPage,
};

// ── 撤下 8 Tab → React 组件（F8 deep-link 保留） ─────────────────────────────
const DEPRECATED_TAB_COMPONENTS: Record<string, PageOrTabComponent> = {
  rag: RagTab,
  engine_config: EngineConfigTab,
  ontology_model: OntologyModelTab,
  graph_builder: GraphBuilderTab,
  glossary: GlossaryTab,
  sync: SyncTab,
  data_import: DataWorkbenchImportTab,
  vector_index: VectorIndexTab,
};

interface KnowledgeViewProps {
  onBack?: () => void;
  activeTab?: KnowledgeTabId;
}

/** 解析当前页面：page > tab > 默认为 overview page */
function parseRoute(search: string): { kind: 'page' | 'tab'; id: string } {
  const sp = new URLSearchParams(search);
  const page = sp.get('page') || '';
  const tab = sp.get('tab') || '';
  if (ACTIVE_PAGES.includes(page as ActivePage)) return { kind: 'page', id: page };
  if (tab) return { kind: 'tab', id: tab };
  return { kind: 'page', id: 'overview' };
}

function isDeprecatedTabId(id: string): boolean {
  return (DEPRECATED_TABS as readonly string[]).includes(id);
}

export default function KnowledgeView({ onBack, activeTab: controlledTab }: KnowledgeViewProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [searchParams, setSearchParams] = useSearchParams();
  const route = parseRoute(searchParams.toString());

  const activePage: ActivePage = (route.kind === 'page' ? route.id : 'overview') as ActivePage;
  const activeTab = controlledTab ?? (route.kind === 'tab' ? (route.id as KnowledgeTabId) : 'overview');

  const groups = KNOWLEDGE_TAB_GROUPS;
  const activeGroup = groups.find(g => (g.tabs as readonly { id: string }[]).some(x => x.id === activeTab));
  const activeTabMeta = activeGroup?.tabs.find((tb: { id: string; icon?: string }) => tb.id === activeTab) as
    | { id: string; icon?: string }
    | undefined;
  const TabIcon = ICON_MAP[activeTabMeta?.icon || 'LayoutDashboard'] ?? LayoutDashboard;

  /** 主侧栏页切换：?page=<id> 替换 search（保留其它 param） */
  const navigateToPage = useCallback((id: ActivePage) => {
    setSearchParams(params => {
      params.set('page', id);
      params.delete('tab');
      return params;
    }, { replace: false });
  }, [setSearchParams]);

  /** 顶部 warn banner — 从 topbar 关闭 deep-link 路由 → 回到 6 页 overview */
  const dismissBanner = useCallback(() => {
    setSearchParams(params => {
      params.delete('tab');
      params.set('page', 'overview');
      return params;
    });
  }, [setSearchParams]);

  // ── 主区渲染 ───────────────────────────────────────────────────────────────
  let mainContent: React.ReactNode;
  if (route.kind === 'page') {
    const Comp = PAGE_COMPONENTS[activePage] ?? (OverviewPage as PageOrTabComponent);
    mainContent = <Comp />;
  } else {
    const Comp = DEPRECATED_TAB_COMPONENTS[route.id];
    mainContent = Comp ? <Comp /> : <OverviewDashboard />;
  }

  const PageIcon = ACTIVE_PAGE_ICONS[activePage];

  /** 侧栏底部——引擎配置（原任务中心/引擎监控已移出知识工作台，全局功能在 Topbar 入口） */
  const navigateToEngineConfig = useCallback(() => {
    setSearchParams(params => {
      params.set('tab', 'engine_config');
      params.delete('page');
      return params;
    });
  }, [setSearchParams]);

  /**
   * 侧栏条目样式（与数据/本体工作台同款）：
   * 圆角 + 12px 字号 + 左 2px 选中条；主题令牌一律进 className
   * （旧实现把它们塞进 style={{ background: styles.sidebarBg }} = 无效 CSS，
   *  侧栏底色/边框实际为透明，故整条工作台观感与其它工作台不一致）。
   */
  const navItemClass = (isActive: boolean) =>
    `w-full flex items-center gap-2.5 px-3 py-2 text-xs rounded-md transition-all font-semibold border-l-2 ${
      isActive
        ? `${styles.sidebarActiveBg} ${styles.sidebarActiveText} ${styles.accentBorder} font-extrabold shadow-sm`
        : `border-transparent ${styles.cardTextMuted} hover:opacity-80`
    }`;

  const sectionLabelClass = `px-3 pt-3 pb-1 text-[10px] font-bold uppercase tracking-wider ${styles.cardTextMuted}`;

  const renderPageNav = (item: ActivePage, isActive: boolean, Icon: LucideIcon) => (
    <button type="button"
      key={item}
      onClick={() => navigateToPage(item)}
      className={navItemClass(isActive)}
    >
      <Icon className={`w-3.5 h-3.5 flex-shrink-0 ${isActive ? styles.accentText : ''}`} />
      <span className="truncate">{t(ACTIVE_PAGE_I18N_KEYS[item])}</span>
    </button>
  );

  return (
    <div className={`flex-1 min-h-0 flex flex-col ${styles.appBg} ${styles.appText}`}>
      {/* ── Desktop: left sidebar (md+) ─────────────────────────────── */}
      <div className="hidden md:flex flex-1 min-h-0">
        <aside className={`w-64 flex-shrink-0 ${styles.sidebarBg} ${styles.sidebarBorder} border-r flex flex-col h-full select-none text-xs`}>
          {/* Header — ◆ ECOS Knowledge（本体/数据工作台同款：p-3 + cardBg 头卡） */}
          <div className={`p-3 flex items-center gap-2 border-b ${styles.sidebarBorder} ${styles.cardBg}`}>
            <button type="button"
              onClick={onBack}
              className={`p-1 rounded hover:opacity-70 cursor-pointer ${styles.cardTextMuted}`}
              aria-label={t('knowledge.back')}
            >
              <ArrowLeft className="w-4 h-4" />
            </button>
            <div className="min-w-0">
              <div className={`font-semibold text-sm leading-tight ${styles.cardText}`}>
                <span className="tracking-[0.2em] uppercase">{t('knowledge.navigation.title')}</span>
              </div>
              <div className={`text-[10px] font-mono tracking-wider uppercase ${styles.cardTextMuted}`}>
                {t('knowledge.navigation.subtitle')}
              </div>
            </div>
          </div>

          {/* [Knowledge Workbench] 6 平铺 Page（F1 主侧栏） */}
          <div className="flex-1 overflow-y-auto py-2 px-2 space-y-1">
            <div className={sectionLabelClass}>
              {t('knowledge.nav.section_workbench')}
            </div>
            {ACTIVE_PAGES.map(page => {
              const isActive = activePage === page && route.kind === 'page';
              const Icon = ACTIVE_PAGE_ICONS[page];
              return renderPageNav(page, isActive, Icon);
            })}
          </div>

          {/* 侧栏底部：引擎配置（与数据工作台引擎配置同款，靠底展示） */}
          <div className={`px-2 pb-2 pt-1 border-t ${styles.cardBorder}`}>
            <div className={sectionLabelClass}>
              {t('knowledge.nav.section_config')}
            </div>
            <button type="button"
              onClick={navigateToEngineConfig}
              className={navItemClass(activeTab === 'engine_config' && route.kind === 'tab')}
            >
              <Settings className="w-3.5 h-3.5 flex-shrink-0" />
              <span className="truncate">{t('knowledge.nav.page_engine_config')}</span>
            </button>
          </div>

          {/* 底部状态（数据工作台同款：cardBg 统计卡 + border-t） */}
          <div className={`p-4 border-t ${styles.cardBorder} ${styles.cardBg} space-y-1 text-[10px] ${styles.cardTextMuted}`}>
            <div className={`flex items-center gap-1.5 font-semibold ${styles.cardText}`}>
              <span className={styles.successText}>●</span>
              <span>{t('knowledge.engine')}</span>
            </div>
            <div className="opacity-70">
              KB · Cogn · Sec · Data
            </div>
          </div>
        </aside>

        <main className="flex-1 flex flex-col overflow-hidden min-w-0">
          {/* Top bar — 面包屑 ECOS / {activePage 或 activeTab}（本体工作台同款细条） */}
          <div className={`flex-shrink-0 flex items-center justify-between px-4 py-1.5 border-b ${styles.cardBorder} ${styles.cardBg}`}>
            <div className={`flex items-center gap-2 text-xs ${styles.cardText}`}>
              {route.kind === 'page' ? (
                <>
                  <PageIcon className={`w-3.5 h-3.5 ${styles.accentText}`} />
                  <span className="font-medium opacity-70">ECOS</span>
                  <span className="text-xs opacity-50">/</span>
                  <span className="font-semibold">{t(ACTIVE_PAGE_I18N_KEYS[activePage])}</span>
                </>
              ) : (
                <>
                  <TabIcon className={`w-3.5 h-3.5 ${styles.accentText}`} />
                  <span className="font-medium opacity-70">ECOS</span>
                  <span className="text-xs opacity-50">/</span>
                  <span className="font-semibold">
                    {t(`knowledge.nav.${activeTab}`) || route.id}
                  </span>
                </>
              )}
            </div>
            <div className={`text-[10px] font-mono tracking-wider uppercase ${styles.cardTextMuted}`}>
              {t('knowledge.nav.updated_tag')}
            </div>
          </div>

          {/* 撤下 Tab 顶部 warn banner（F8 基础骨架，仅当 ?tab=xxx 命中 8 撤下 id） */}
          {route.kind === 'tab' && isDeprecatedTabId(route.id) && (
            <div className={`flex items-center gap-3 px-4 py-2 border-b text-xs flex-shrink-0 ${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`}>
              <AlertTriangle className="w-3.5 h-3.5 flex-shrink-0" />
              <span className="flex-1">
                {t('knowledge.tab.deprecated_banner', { tab: route.id })}
              </span>
              <button
                type="button"
                onClick={dismissBanner}
                className={`px-2 py-0.5 rounded border text-[10px] font-medium cursor-pointer ${styles.warningBorder} ${styles.warningText}`}
              >
                {t('knowledge.tab.deprecated_banner_back')}
              </button>
            </div>
          )}

          {/* Body */}
          <div className="flex-1 flex flex-col min-h-0 overflow-y-auto p-3">
            {mainContent}
          </div>
        </main>
      </div>

      {/* ── Mobile: horizontal scroll tab bar (below md) ───────────────── */}
      <div className="md:hidden flex flex-col h-full min-h-0">
        <div className={`flex-shrink-0 flex items-center gap-2 px-4 py-2 border-b ${styles.cardBorder} ${styles.cardBg}`}>
          <button type="button"
            onClick={onBack}
            className={`p-1 rounded hover:opacity-70 cursor-pointer ${styles.cardTextMuted}`}
            aria-label={t('knowledge.back')}
          >
            <ArrowLeft className="w-4 h-4" />
          </button>
          <div className={`font-semibold text-sm leading-tight ${styles.cardText}`}>
            <span className="tracking-[0.2em] uppercase">{t('knowledge.navigation.title')}</span>
          </div>
        </div>

        <div className={`flex-shrink-0 border-b overflow-x-auto whitespace-nowrap px-1 ${styles.cardBorder} ${styles.cardBg}`}>
          <div className="flex flex-nowrap gap-0.5 items-end pt-2">
            {ACTIVE_PAGES.map(page => {
              const isActive = activePage === page && route.kind === 'page';
              const Icon = ACTIVE_PAGE_ICONS[page];
              return (
                <button type="button"
                  key={page}
                  onClick={() => navigateToPage(page)}
                  className={`h-9 px-3 rounded-t-md border-t-2 flex items-center gap-2 text-xs font-medium whitespace-nowrap shrink-0 cursor-pointer transition ${
                    isActive
                      ? `border-t-current ${styles.sidebarActiveBg} ${styles.sidebarActiveText} font-bold`
                      : `border-transparent opacity-70 hover:opacity-100 ${styles.sidebarText}`
                  }`}
                >
                  <Icon className="w-3.5 h-3.5 shrink-0" />
                  {t(ACTIVE_PAGE_I18N_KEYS[page])}
                </button>
              );
            })}
          </div>
        </div>

        {route.kind === 'tab' && isDeprecatedTabId(route.id) && (
          <div className={`flex items-center gap-2 px-4 py-2 border-b text-[11px] ${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`}>
            <AlertTriangle className="w-3 h-3 flex-shrink-0" />
            <span className="flex-1">{t('knowledge.tab.deprecated_banner', { tab: route.id })}</span>
            <button type="button" onClick={dismissBanner} className={`px-2 py-0.5 rounded border text-[10px] cursor-pointer ${styles.warningBorder}`}>
              {t('knowledge.tab.deprecated_banner_back')}
            </button>
          </div>
        )}

        <main className="flex-1 flex flex-col overflow-hidden w-full min-w-0">
          <div className="flex-1 flex flex-col min-h-0 overflow-y-auto p-3">
            {mainContent}
          </div>
        </main>
      </div>
    </div>
  );
}
