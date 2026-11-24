/**
 * 工作台 L1 —「我的场景」首屏（F07-06 三层 IA 第一层）。
 *
 * <p>首屏只暴露场景语义（场景卡片 / 快捷入口），不出现 Mind / Goal Tree / Causal 等技术概念
 * 或后端原始字段名。</p>
 *
 * <p>2026-11-23 界面精简：按用户裁决摘除三处入口——「场景与认知闭环」按钮、
 * 「高级视图」下拉面板、「预测与推演」快捷入口（预测/推演均依赖具体场景，
 * 不得在 L1 以「取第一个场景」代跳，那会把别的场景的数据当成当前上下文）。</p>
 *
 * 六态（§3.3）实装口径：加载（spinner）/ 错误+重试（含权限失败的后端
 * message 直排，未单拆 403 分支）/ 空与边界（无场景 → 引导空态）/
 * 离线（加载失败且 navigator.onLine=false 时横幅）。
 * 颜色全部走 useTheme 语义 token，文案全部走 t('workspace.*')。</p>
 */
import React, { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  AlertTriangle,
  ArrowRight,
  Building,
  Inbox,
  LayoutDashboard,
  Plus,
  WifiOff
} from 'lucide-react';
import { Icon } from '../../components/LucideIcon';
import { useTheme, type ThemeStyles } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';
import { useMediaQuery } from '../../hooks/useMediaQuery';
import { apiFetchData } from '../../api';
// #145/B6-2：场景集合基址单源（页面不再自拼 /v1/workspace/scenarios）
import { WORKSPACE_SCENARIOS_BASE } from '../../services/scenarioActionClient';
import logger from '../../services/logger';
import NewScenarioModal from './NewScenarioModal';

interface ScenarioSummaryVO {
  id: string;
  name: string;
  businessGoal?: string;
  description?: string;
  department?: string;
  status?: string;
}

export default function WorkbenchHome() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const isMobile = useMediaQuery('(max-width: 767px)');

  const [scenarios, setScenarios] = useState<ScenarioSummaryVO[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [offline, setOffline] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const d = await apiFetchData<ScenarioSummaryVO[]>(WORKSPACE_SCENARIOS_BASE);
      setScenarios(Array.isArray(d) ? d : []);
      setOffline(false);
    } catch (e) {
      logger.error('workbench-scenarios', e);
      const msg = e instanceof Error ? e.message : String(e);
      setError(msg);
      setOffline(!navigator.onLine);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  // 浏览器离线/恢复事件：离线立即呈现横幅并尝试一次真实加载（失败如实入 error），
  // 恢复在线后自动重载。此前只在首挂载 load 里判 offline，用户停留在页面时断网不显示。
  useEffect(() => {
    const onOffline = () => {
      setOffline(true);
      void load();
    };
    const onOnline = () => {
      setOffline(false);
      void load();
    };
    window.addEventListener('offline', onOffline);
    window.addEventListener('online', onOnline);
    // 兜底轮询：部分内嵌/仿真环境不派发 offline 事件，按 navigator.onLine 翻转检测。
    let wasOnline = navigator.onLine;
    const timer = window.setInterval(() => {
      const now = navigator.onLine;
      if (wasOnline && !now) onOffline();
      else if (!wasOnline && now) onOnline();
      wasOnline = now;
    }, 2000);
    return () => {
      window.removeEventListener('offline', onOffline);
      window.removeEventListener('online', onOnline);
      window.clearInterval(timer);
    };
  }, [load]);

  // 快捷入口真实目标（2026-10-14 裁决订正）：
  // - 「新建场景」：L1 直接弹新建模态（原型 v2.0：模板 chips 预填 + 真实
  //   POST /api/v1/workspace/scenarios，创建即 DRAFT，成功跳 L2 详情）；
  // - 「待我决策」：目标页需 scenarioId ⇒ 取场景列表首项（最近场景）直达，无场景时不渲染（禁自环）；
  // - 「版本历史」：无独立真实路由/端点可指 ⇒ 已摘除（蓝图版本在 L2 内呈现，不在 L1 造入口）。
  // - 「预测与推演」（2026-11-23 摘除）：预测/推演都是场景级动作，L1 代跳首个场景
  //   会让用户误以为在看「自己的」预测；改由 L2「场景运行」页在明确场景下发起。
  const first = scenarios[0];
  type ResolvedEntry = { icon: string; labelKey: string; to: string; accent: (s: ThemeStyles) => string };
  const navEntries: ResolvedEntry[] = [
    first
      ? { icon: 'ShieldCheck', labelKey: 'workspace.l1.entry.pending', to: `/workbench/${first.id}/decisions`, accent: (s: ThemeStyles) => s.warningText }
      : null,
  ].filter((e): e is ResolvedEntry => e !== null);

  return (
    <div className={`flex-1 flex flex-col overflow-y-auto ${styles.appBg} ${styles.appText}`}>
      {/* Header */}
      <div className={`p-4 ${styles.cardBg} border-b ${styles.cardBorder} shrink-0`}>
        <div className="flex items-center gap-2">
          <span className={`p-1.5 rounded-md ${styles.accentBg} ${styles.cardText} flex items-center justify-center`}>
            <LayoutDashboard size={16} />
          </span>
          <h1 className={`text-lg font-bold tracking-tight ${styles.appText}`}>{t('workspace.l1.title')}</h1>
        </div>
        <p className={`text-xs ${styles.cardTextMuted} mt-1`}>{t('workspace.l1.subtitle')}</p>
      </div>

      {/* 离线 banner（离线态） */}
      {offline && (
        <div className={`mx-4 mt-4 px-3 py-2 rounded border text-xs ${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`}>
          <WifiOff size={12} className="inline mr-1" />
          {t('workspace.l1.offline')}
        </div>
      )}

      {/* 快捷入口：首格固定「新建场景」（直接弹模态，勿做成跳转占位） */}
      <section className="p-4">
        <h2 className={`text-xs font-bold uppercase ${styles.cardTextMuted} mb-2`}>{t('workspace.l1.quick_entries')}</h2>
        <div className={`grid gap-2 ${isMobile ? 'grid-cols-2' : 'grid-cols-2 sm:grid-cols-4'}`}>
          <button
            type="button"
            onClick={() => setCreateOpen(true)}
            className={`${styles.accentBg} ${styles.accentSolidText} rounded-lg p-3 flex items-center gap-2 transition-all hover:opacity-90 cursor-pointer`}
            aria-label={t('workspace.l1.entry.new_scenario')}
          >
            <Plus size={16} />
            <span className="text-xs font-bold">{t('workspace.l1.entry.new_scenario')}</span>
          </button>
          {navEntries.map((e) => (
            <Link
              key={e.labelKey}
              to={e.to}
              className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3 flex items-center gap-2 transition-all hover:opacity-90 cursor-pointer`}
            >
              <span className={e.accent(styles)}>
                <Icon name={e.icon} size={16} />
              </span>
              <span className={`text-xs font-semibold ${styles.cardText}`}>{t(e.labelKey)}</span>
            </Link>
          ))}
        </div>
      </section>

      {/* 我的场景 */}
      <section className="px-4 pb-4">
        <div className="flex items-center justify-between mb-2">
          <h2 className={`text-xs font-bold uppercase ${styles.cardTextMuted}`}>{t('workspace.l1.my_scenarios')}</h2>
          <button
            type="button"
            onClick={() => setCreateOpen(true)}
            className={`inline-flex items-center gap-1 px-2.5 py-1 text-[11px] font-bold rounded border ${styles.accentBg} ${styles.accentBorder} ${styles.accentText} cursor-pointer hover:opacity-90 transition`}
          >
            <Plus size={12} />
            {t('workspace.l1.entry.new_scenario')}
          </button>
        </div>

        {/* 加载态 */}
        {loading && (
          <div className={`flex items-center gap-2 text-xs ${styles.cardTextMuted} py-6 justify-center`}>
            <span className="h-4 w-4 animate-spin rounded-full border-2 border-current border-t-transparent opacity-60" />
            {t('workspace.l1.loading')}
          </div>
        )}

        {/* 错误态（权限/网络） */}
        {!loading && error && (
          <div className={`px-3 py-3 rounded border text-xs ${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}`}>
            <AlertTriangle size={12} className="inline mr-1" />
            {t('workspace.l1.refresh_failed', { msg: error })}
            <button
              onClick={() => void load()}
              className={`ml-2 px-2 py-0.5 rounded border ${styles.inputBorder} ${styles.inputText} cursor-pointer`}
            >
              {t('workspace.l1.retry')}
            </button>
          </div>
        )}

        {/* 空态 / 边界态（无场景） */}
        {!loading && !error && scenarios.length === 0 && (
          <div className={`px-4 py-8 rounded border text-center ${styles.cardBorder} ${styles.cardBg}`}>
            <Inbox size={22} className={`${styles.cardTextMuted} mb-2`} />
            <p className={`text-sm font-semibold ${styles.cardText}`}>{t('workspace.l1.no_scenarios')}</p>
            <p className={`text-xs ${styles.cardTextMuted} mt-1`}>{t('workspace.l1.no_scenarios_hint')}</p>
          </div>
        )}

        {/* 数据态：卡片网格 */}
        {!loading && !error && scenarios.length > 0 && (
          <div className={`grid gap-3 ${isMobile ? 'grid-cols-1' : 'grid-cols-1 sm:grid-cols-2 lg:grid-cols-3'}`}>
            {scenarios.map((s) => (
              <article
                key={s.id}
                className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3 flex flex-col gap-2`}
              >
                <div className="flex items-start justify-between gap-2">
                  <h3 className={`text-sm font-bold ${styles.cardText} leading-snug`}>{s.name}</h3>
                  {/* 行275 订正：status 缺字段不再兜底成 'DRAFT' 真实徽章（禁假状态），缺省即不渲染 */}
                  {s.status ? (
                    <span className={`text-[10px] border px-1.5 py-0.5 rounded font-mono shrink-0 ${styles.accentText} ${styles.accentBorder} ${styles.badgeBg}`}>
                      {s.status}
                    </span>
                  ) : null}
                </div>
                <p className={`text-xs ${styles.cardTextMuted} line-clamp-2`}>{s.businessGoal || s.description || '—'}</p>
                <div className="flex items-center gap-3 text-[11px] font-mono">
                  <span className={styles.cardTextMuted}>
                    <Building size={11} className="inline mr-1" />
                    {s.department ?? '—'}
                  </span>
                </div>
                <Link
                  to={`/workbench/${s.id}`}
                  className={`mt-1 inline-flex items-center gap-1 text-xs font-bold px-3 py-1.5 rounded border ${styles.accentBg} ${styles.accentBorder} ${styles.cardText} cursor-pointer w-fit`}
                >
                  {t('workspace.l1.card.open')}
                  <ArrowRight size={12} />
                </Link>
              </article>
            ))}
          </div>
        )}
      </section>

      {/* 新建场景模态（L1 直接创建，真实 POST，创建即 DRAFT，成功后跳 L2 并刷新列表） */}
      <NewScenarioModal isOpen={createOpen} onClose={() => setCreateOpen(false)} onCreated={() => void load()} />
    </div>
  );
}
