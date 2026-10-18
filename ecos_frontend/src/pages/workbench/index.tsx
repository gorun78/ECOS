/**
 * 工作台 L1 —「我的场景」首屏（F07-06 三层 IA 第一层）。
 *
 * <p>首屏只暴露业务语义（场景卡片 / 快捷入口 / 高级视图折叠区），
 * 不出现 Mind / Goal Tree / Causal 等技术概念或后端原始字段名。
 * 六态（§3.3）实装口径：加载（spinner）/ 错误+重试（含权限失败的后端
 * message 直排，未单拆 403 分支）/ 空与边界（无场景 → 引导空态）/
 * 离线（加载失败且 navigator.onLine=false 时横幅）。
 * 颜色全部走 useTheme 语义 token，文案全部走 t('workspace.*')。</p>
 */
import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import LucideIcon from '../../components/LucideIcon';
import { useTheme, type ThemeStyles } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';
import { useMediaQuery } from '../../hooks/useMediaQuery';
import { apiFetchData } from '../../api';
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
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);

  const load = async () => {
    setLoading(true);
    setError(null);
    try {
      const d = await apiFetchData<ScenarioSummaryVO[]>('/api/v1/workspace/scenarios');
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
  };

  useEffect(() => {
    void load();
  }, []);

  // 快捷入口真实目标（2026-10-14 裁决订正）：
  // - 「新建场景」：L1 直接弹新建模态（原型 v2.0：模板 chips 预填 + 真实
  //   POST /api/v1/workspace/scenarios，创建即 DRAFT，成功跳 L2 详情）；
  // - 「待我决策」「预测与推演」：目标页需 scenarioId ⇒ 取场景列表首项（最近场景）直达，无场景时不渲染（禁自环）；
  // - 「版本历史」：无独立真实路由/端点可指 ⇒ 已摘除（蓝图版本在 L2 内呈现，不在 L1 造入口）。
  const first = scenarios[0];
  type ResolvedEntry = { icon: string; labelKey: string; to: string; accent: (s: ThemeStyles) => string };
  const navEntries: ResolvedEntry[] = [
    first
      ? { icon: 'ShieldCheck', labelKey: 'workspace.l1.entry.pending', to: `/workbench/${first.id}/decisions`, accent: (s: ThemeStyles) => s.warningText }
      : null,
    first
      ? { icon: 'TrendingUp', labelKey: 'workspace.l1.entry.forecast', to: `/workbench/${first.id}/forecast`, accent: (s: ThemeStyles) => s.infoText }
      : null,
  ].filter((e): e is ResolvedEntry => e !== null);

  return (
    <div className={`flex-1 flex flex-col overflow-y-auto ${styles.appBg} ${styles.appText}`}>
      {/* Header */}
      <div className={`p-4 ${styles.cardBg} border-b ${styles.cardBorder} shrink-0`}>
        <div className="flex items-center gap-2">
          <span className={`p-1.5 rounded-md ${styles.accentBg} ${styles.cardText} flex items-center justify-center`}>
            <LucideIcon name="LayoutDashboard" size={16} />
          </span>
          <h1 className={`text-lg font-bold tracking-tight ${styles.appText}`}>{t('workspace.l1.title')}</h1>
        </div>
        <p className={`text-xs ${styles.cardTextMuted} mt-1`}>{t('workspace.l1.subtitle')}</p>
      </div>

      {/* 离线 banner（离线态） */}
      {offline && (
        <div className={`mx-4 mt-4 px-3 py-2 rounded border text-xs ${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`}>
          <LucideIcon name="WifiOff" size={12} className="inline mr-1" />
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
            <LucideIcon name="Plus" size={16} />
            <span className="text-xs font-bold">{t('workspace.l1.entry.new_scenario')}</span>
          </button>
          {navEntries.map((e) => (
            <Link
              key={e.labelKey}
              to={e.to}
              className={`${styles.cardBg} border ${styles.cardBorder} rounded-lg p-3 flex items-center gap-2 transition-all hover:opacity-90 cursor-pointer`}
            >
              <span className={e.accent(styles)}>
                <LucideIcon name={e.icon} size={16} />
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
            <LucideIcon name="Plus" size={12} />
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
            <LucideIcon name="AlertTriangle" size={12} className="inline mr-1" />
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
            <LucideIcon name="Inbox" size={22} className={`${styles.cardTextMuted} mb-2`} />
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
                    <LucideIcon name="Building" size={11} className="inline mr-1" />
                    {s.department ?? '—'}
                  </span>
                </div>
                <Link
                  to={`/workbench/${s.id}`}
                  className={`mt-1 inline-flex items-center gap-1 text-xs font-bold px-3 py-1.5 rounded border ${styles.accentBg} ${styles.accentBorder} ${styles.cardText} cursor-pointer w-fit`}
                >
                  {t('workspace.l1.card.open')}
                  <LucideIcon name="ArrowRight" size={12} />
                </Link>
              </article>
            ))}
          </div>
        )}
      </section>

      {/* 高级视图折叠区（默认折叠，面向治理/工程角色） */}
      <section className="px-4 pb-6">
        <button
          onClick={() => setShowAdvanced((v) => !v)}
          className={`w-full flex items-center justify-between px-3 py-2 rounded border text-xs font-bold cursor-pointer ${styles.cardBg} ${styles.cardBorder} ${styles.cardText}`}
        >
          <span className="flex items-center gap-2">
            <LucideIcon name="SlidersHorizontal" size={13} />
            {t('workspace.l1.advanced')}
          </span>
          <LucideIcon name={showAdvanced ? 'ChevronUp' : 'ChevronDown'} size={14} />
        </button>
        {showAdvanced && (
          <div className={`mt-2 px-3 py-3 rounded border text-xs ${styles.cardBg} ${styles.cardBorder} ${styles.cardTextMuted}`}>
            <p className="mb-2">{t('workspace.l1.advanced_hint')}</p>
            <div className="flex flex-wrap gap-2">
              <Link to="/project_workbench" className={`px-2.5 py-1 rounded border ${styles.inputBorder} ${styles.inputText} cursor-pointer`}>
                {t('workspace.l1.advanced_toggle')}
              </Link>
            </div>
          </div>
        )}
      </section>

      {/* 新建场景模态（L1 直接创建，真实 POST，创建即 DRAFT，成功后跳 L2 并刷新列表） */}
      <NewScenarioModal isOpen={createOpen} onClose={() => setCreateOpen(false)} onCreated={() => void load()} />
    </div>
  );
}
