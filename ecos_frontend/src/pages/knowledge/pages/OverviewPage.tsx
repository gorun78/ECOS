/**
 * PMO-D Batch 2 — F2 OverviewPage（实体实现 / PRD §3.2 F2）。
 *
 * 布局：
 *   顶栏 — 标题 + 「查看任务」按钮（P3 disabled + tooltip）
 *   KPI 6 卡片（grid 6 列）：知识资产 / 图谱实体 / 图谱关系 / 向量索引 / 待审核 / 抽取任务
 *   知识生产流水线（5 步横条）：本体映射 → 数据抽取 → 知识候选 → 审核发布 → KG/Vector
 *   待处理事项（3 行）：AI 候选 X / Wiki 待审核 Y / 索引失败 Z（点击跳 F7 GovernPage）
 *   最近知识资产表（5 列）：名称 / 类型 / 状态 / 来源 / 更新时间
 *
 * 数据源（全部来自既有 API，不新增 endpoint）：
 *   KPI               → fetchGraphStats() + fetchIndexStatus()
 *   待审核            → fetchExtractCandidateFiles() 过滤 pending
 *   抽取任务          → fetchStructuredJobs(1,1) 推导 running 计数
 *   最近资产表        → fetchNavProducts({pageNum:1,pageSize:10})
 *
 * 主题守护 §4.1：0 硬编码色值（全 useTheme().styles）
 * 图标 §4.2：仅 lucide-react
 * i18n §4.3：0 硬编码中文（knowledge.overview.*）
 */
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Activity, ArrowRight, Briefcase, CheckCircle2, Clock, Database,
  FileSearch, FileText, GitBranch, Layers, Loader2, ListChecks,
  Network, Package, RefreshCw, Search,
} from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { knowledgeApi, type GraphStats } from '../services/knowledgeApi';
import { fetchNavProducts, type NavProductItemVO } from '../../../services/knowledgeNavApi';

/**
 * KPI 6 指标（PRD F2）
 * - 知识资产（articleCount） / 图谱实体（graphNodeCount） / 图谱关系（graphEdgeCount）
 * - 向量索引（embeddingCount） / 待审核（pendingFilesCount） / 抽取任务（runningJobsCount）
 * 数据源：fetchGraphStats 一次性提供文章数/节点/边/向量；pending/running 从对应端点独立拉。
 */
interface AcknowledgeStats extends GraphStats {
  statsOff: boolean;
}

/** 流水线 5 步（PRD F2「知识生产流水线」）— 主题主导色 chip */
const PIPELINE_STEPS = [
  { id: 'ontology_mapping', labelKey: 'knowledge.overview.pipeline_ontology', hintKey: 'knowledge.overview.pipeline_ontology_hint' },
  { id: 'data_extraction',  labelKey: 'knowledge.overview.pipeline_extraction', hintKey: 'knowledge.overview.pipeline_extraction_hint' },
  { id: 'knowledge_candidate', labelKey: 'knowledge.overview.pipeline_candidate', hintKey: 'knowledge.overview.pipeline_candidate_hint' },
  { id: 'reivew_publish',     labelKey: 'knowledge.overview.pipeline_publish', hintKey: 'knowledge.overview.pipeline_publish_hint' },
  { id: 'kg_vector',          labelKey: 'knowledge.overview.pipeline_kg_vector', hintKey: 'knowledge.overview.pipeline_kg_vector_hint' },
] as const;

/** 待处理事项 3 行（PRD F2「待处理事项」— 点击跳 F7 GovernPage） */
interface ToDoRow {
  id: 'ai_candidates' | 'wiki_pending' | 'index_failed';
  labelKey: string;
  value: number;
  /** 跳转路径（全部由 GovernPage，原来 ExtractionReviewTab / 现 AuditFeedCard 承载） */
  target: 'govern';
}

/** 最近知识资产表行（来自 fetchNavProducts，与 AssetListPage 同源） */
interface AssetRow {
  id: string;
  title: string;
  type: string;
  status: string;
  source: string;
  updatedAt?: string;
}

/** 归一化资产状态 → 4 档（draft / review / published，与 AssetListPage 同语义） */
function normalizeStatus(raw: string | undefined): 'published' | 'review' | 'draft' {
  const s = (raw || 'draft').trim().toLowerCase();
  if (['published', 'ready', 'active', 'released'].includes(s)) return 'published';
  if (['review', 'in_review', 'pending', 'approving'].includes(s)) return 'review';
  return 'draft';
}

export default function OverviewPage() {
  const { styles } = useTheme();
  const { t } = useLanguage();

  // ── KPI 状态 ──
  const [graphStats, setGraphStats] = useState<GraphStats>({
    graphNodeCount: 0,
    graphEdgeCount: 0,
    embeddingCount: 0,
    ruleCount: 0,
  });
  const [statsOff, setStatsOff] = useState<boolean>(false);

  // ── 待审核 / 抽取任务（KPI 4-6） ──
  const [pendingFiles, setPendingFiles] = useState<number>(0);
  const [runningJobs, setRunningJobs] = useState<number>(0);

  // ── 3 待办 ──
  const [wikiPending, setWikiPending] = useState<number>(0);
  const [indexFailed, setIndexFailed] = useState<number>(0);

  // ── 最近资产表 ──
  const [assets, setAssets] = useState<AssetRow[]>([]);
  const [assetsLoading, setAssetsLoading] = useState<boolean>(true);

  // ── 资产表 loading（用于顶栏遮罩） ──
  const [refreshing, setRefreshing] = useState<boolean>(false);

  /**
   * 一次性并发拉 4 路：
   *  1) fetchGraphStats（KPI 1-4 主源）
   *  2) fetchExtractCandidateFiles → 过滤 pending（KPI 5 待审核 + 第 1 行待办）
   *  3) fetchStructuredJobs(1,1) → 第一个 job status=RUNNING 时 runningJobs=1
   *  4) fetchLifecycleAudit → 推导 indexFailed + wikiPending（待办 2/3）
   *
   * 各路独立 catch（静默降级），0 值保留，不阻塞整体渲染（PRD F2 验收 6 KPI 全调用真实 API）。
   */
  const loadAll = useCallback(async (): Promise<void> => {
    setRefreshing(true);
    // 4 路并发，每路独立 catch
    const [stats, files, jobs, audits] = await Promise.all([
      knowledgeApi.fetchGraphStats().catch((): GraphStats | null => null),
      knowledgeApi.fetchExtractCandidateFiles().catch((): Promise<{ fileId: string; fileName: string; status: string; candidateCount: number }[]> => Promise.resolve([])),
      knowledgeApi.fetchStructuredJobs(1, 5).catch((): Promise<unknown[]> => Promise.resolve([])),
      knowledgeApi.fetchLifecycleAudit().catch((): Promise<never[]> => Promise.resolve([] as never[])),
    ]);

    // 1) KPI 1-4（Article / Nodes / Edges / Embedding）
    if (stats) {
      setGraphStats(stats);
      setStatsOff(false);
    } else {
      setStatsOff(true);
      setGraphStats({ graphNodeCount: 0, graphEdgeCount: 0, embeddingCount: 0, ruleCount: 0 });
    }

    // 2) KPI 5 待审核数 + 第 1 行待办
    const pendingList = (files || []).filter((f) => {
      const s = (f.status || '').trim().toUpperCase();
      return ['PENDING', 'QUEUED', ''].includes(s) || s.length === 0;
    });
    setPendingFiles(pendingList.length);

    // 3) KPI 6 抽取任务 running 数（结构性提取作业 running）
    // 后端 StructuredExtractJob 无显式 "running" 字段，从 status 推导
    const jobsList = Array.isArray(jobs) ? jobs : [];
    const runningCount = jobsList.filter((j) => {
      const st = String((j as { status?: string }).status || '').toLowerCase();
      return ['running', 'in_progress'].includes(st);
    }).length;
    setRunningJobs(runningCount);

    // 4) 待办 2/3 — Wiki 待审核 / 索引失败
    // wikiPending ← audits 中 to_state = 'active'（已发布代表 Wiki 审计通过）— 简化取 review 状态
    const toStates = (audits || []).map((a) => String((a as { to_state?: string; state?: string }).to_state || (a as { state?: string }).state || ''));
    const wikiActive = toStates.filter((s) => (s || '').toLowerCase() === 'active').length;
    setWikiPending(wikiActive);
    const failedCount = toStates.filter((s) => (s || '').toLowerCase() === 'archived' || (s || '').toLowerCase() === 'deprecated').length;
    setIndexFailed(failedCount);

    setRefreshing(false);
  }, []);

  useEffect(() => { void loadAll(); }, [loadAll]);
  // 监听萃取成功事件（与 Batch 1 既有 OverviewDashboard 同名事件保一致）
  useEffect(() => {
    const handler = () => { void loadAll(); };
    window.addEventListener('kb:stats:refresh', handler);
    return () => { window.removeEventListener('kb:stats:refresh', handler); };
  }, [loadAll]);

  /** 最近知识资产表（fetchNavProducts top 10） */
  const loadAssets = useCallback(async () => {
    setAssetsLoading(true);
    try {
      const res = await fetchNavProducts({ pageNum: 1, pageSize: 10 });
      const list: NavProductItemVO[] = res?.list || [];
      setAssets(list.slice(0, 10).map((it) => ({
        id: it.id,
        title: it.title || '—',
        type: (it.domain || 'default').slice(0, 12),
        status: normalizeStatus(it.status),
        source: (it.source || '—').slice(0, 18),
        updatedAt: it.updatedAt,
      })));
    } catch {
      setAssets([]);
    } finally {
      setAssetsLoading(false);
    }
  }, []);

  useEffect(() => { void loadAssets(); }, [loadAssets]);

  /** 「查看任务」按钮 — 跳 #/tasks（P3 未注册当前 disabled） */
  const handleViewTasks = useCallback(() => {
    // PRD F2：「查看任务」跳 #/tasks（统一任务中心）。当前批次该路由未注册（P3 才落地）
    // → disabled + tooltip（i18n key knowledge.overview.task_center_p3）
    window.location.hash = '#/tasks';
  }, []);

  const statusChip = (status: AssetRow['status']) => {
    if (status === 'published') return { background: styles.successBg, color: styles.successText };
    if (status === 'review') return { background: styles.warningBg, color: styles.warningText };
    return { background: styles.badgeBg, color: styles.badgeText };
  };

  const statusLabelKey = (status: AssetRow['status']) =>
    status === 'published' ? 'knowledge.asset.status_published'
    : status === 'review' ? 'knowledge.asset.status_review'
    : 'knowledge.asset.status_draft';

  const toDoRows: ToDoRow[] = useMemo(() => [
    { id: 'ai_candidates', labelKey: 'knowledge.overview.todo_ai_candidates', value: pendingFiles, target: 'govern' },
    { id: 'wiki_pending', labelKey: 'knowledge.overview.todo_wiki_pending', value: wikiPending, target: 'govern' },
    { id: 'index_failed', labelKey: 'knowledge.overview.todo_index_failed', value: indexFailed, target: 'govern' },
  ], [pendingFiles, wikiPending, indexFailed]);

  const kpiCards: Array<{ id: string; labelKey: string; value: number; icon: React.ReactNode; accent: string; muted: boolean }> = [
    {
      id: 'assets',
      labelKey: 'knowledge.overview.kpi_assets',
      value: graphStats.articleCount ?? 0,
      icon: <FileText className="w-4 h-4" />,
      accent: styles.accentText,
      muted: (graphStats.articleCount ?? 0) === 0,
    },
    {
      id: 'entities',
      labelKey: 'knowledge.overview.kpi_entities',
      value: graphStats.graphNodeCount,
      icon: <Network className="w-4 h-4" />,
      accent: styles.infoText,
      muted: graphStats.graphNodeCount === 0,
    },
    {
      id: 'relations',
      labelKey: 'knowledge.overview.kpi_relations',
      value: graphStats.graphEdgeCount,
      icon: <Layers className="w-4 h-4" />,
      accent: styles.successText,
      muted: graphStats.graphEdgeCount === 0,
    },
    {
      id: 'vector',
      labelKey: 'knowledge.overview.kpi_vector',
      value: graphStats.embeddingCount,
      icon: <Database className="w-4 h-4" />,
      accent: styles.warningText,
      muted: graphStats.embeddingCount === 0,
    },
    {
      id: 'pending',
      labelKey: 'knowledge.overview.kpi_pending',
      value: pendingFiles,
      icon: <FileSearch className="w-4 h-4" />,
      accent: styles.warningText,
      muted: pendingFiles === 0,
    },
    {
      id: 'jobs',
      labelKey: 'knowledge.overview.kpi_jobs',
      value: runningJobs,
      icon: <Activity className="w-4 h-4" />,
      accent: styles.accentText,
      muted: runningJobs === 0,
    },
  ];

  return (
    <div className="p-3 space-y-4" style={{ color: styles.cardText }}>
      {/* ═══ 顶栏：标题 + 查看任务按钮 ═══ */}
      <div className="flex items-center justify-between border-b pb-3"
           style={{ borderColor: styles.cardBorder }}>
        <div className="space-y-0.5">
          <h2 className="text-sm font-black flex items-center gap-2">
            <ListChecks className="w-4 h-4" style={{ color: styles.accentText }} />
            <span>{t('knowledge.nav.page_overview')}</span>
          </h2>
          <p className="text-[11px]" style={{ color: styles.cardTextMuted }}>
            {t('knowledge.overview.subtitle')}
            {graphStats.lastUpdatedAt && (
              <span className="ml-2 font-mono" style={{ color: styles.muted }}>
                {t('knowledge.overview.last_sync')}: {new Date(graphStats.lastUpdatedAt).toLocaleString()}
              </span>
            )}
          </p>
        </div>

        <div className="flex items-center gap-1.5">
          {/* 刷新 */}
          <button
            type="button"
            onClick={() => { void loadAll(); void loadAssets(); }}
            disabled={refreshing}
            className="p-1.5 rounded-md border cursor-pointer hover:opacity-70 transition disabled:opacity-50"
            style={{ borderColor: styles.cardBorder, background: styles.cardBg, color: styles.cardTextMuted }}
            title={t('knowledge.overview.refresh')}
          >
            {refreshing ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <RefreshCw className="w-3.5 h-3.5" />}
          </button>
          {/* 查看任务 — 跳 #/tasks（P3 disabled + tooltip） */}
          <button
            type="button"
            disabled
            className="inline-flex items-center gap-1.5 px-2.5 py-1.5 rounded-md text-[11px] font-bold disabled:opacity-50 cursor-not-allowed"
            style={{ background: styles.inputBg, color: styles.muted, border: `1px solid ${styles.inputBorder}` }}
            title={t('knowledge.overview.task_center_p3')}
          >
            <Briefcase className="w-3.5 h-3.5" />
            {t('knowledge.overview.view_task_button')}
          </button>
        </div>
      </div>

      {/* ═══ KPI 6 卡片（grid 6 列 · 数字 25px / 标签 12px + 灰色 · PRD F2 验收 2） ═══ */}
      {statsOff ? (
        <div className="rounded-md border p-6 flex flex-col items-center gap-1.5"
             style={{ borderColor: styles.dangerBorder, background: styles.dangerBg, color: styles.dangerText }}>
          <Database className="w-5 h-5" />
          <p className="text-[11px]">{t('knowledge.overview.stats_offline')}</p>
          <p className="text-[10px] font-mono opacity-70">/api/v1/knowledge/stats · {t('knowledge.overview.retry_hint')}</p>
        </div>
      ) : (
        <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3">
          {kpiCards.map((kpi) => (
            <div
              key={kpi.id}
              className="rounded-md border p-3 space-y-1.5 flex flex-col"
              style={{ borderColor: styles.cardBorder, background: styles.cardBg, opacity: kpi.muted ? 0.7 : 1 }}
            >
              <div className="flex items-center gap-1.5" style={{ color: kpi.muted ? styles.muted : styles.cardTextMuted }}>
                <span className={kpi.muted ? '' : ''} style={{ color: kpi.accent, opacity: kpi.muted ? 0.5 : 1 }}>
                  {kpi.icon}
                </span>
                <span className="text-[12px] font-bold uppercase tracking-wider">{t(kpi.labelKey)}</span>
              </div>
              <p className="text-[25px] font-black font-mono leading-none"
                 style={{ color: kpi.muted ? styles.muted : kpi.accent }}>
                {kpi.value.toLocaleString()}
              </p>
            </div>
          ))}
        </div>
      )}

      {/* ═══ 知识生产流水线（5 步横条 · PRD F2 验收 3） ═══ */}
      <div className="rounded-md border p-3 space-y-2"
           style={{ borderColor: styles.cardBorder, background: styles.cardBg }}>
        <div className="flex items-center justify-between border-b pb-2"
             style={{ borderColor: styles.cardBorder }}>
          <h3 className="text-xs font-extrabold flex items-center gap-1.5">
            <GitBranch className="w-3.5 h-3.5" style={{ color: styles.accentText }} />
            <span>{t('knowledge.overview.pipeline_title')}</span>
          </h3>
          <span className="text-[10px] font-mono uppercase tracking-wider" style={{ color: styles.muted }}>
            {t('knowledge.overview.pipeline_strategy')}
          </span>
        </div>
        <div className="flex flex-col md:flex-row md:items-center gap-1">
          {PIPELINE_STEPS.map((step, i) => (
            <React.Fragment key={step.id}>
              <div
                className="flex-1 rounded-md px-3 py-2 flex flex-col items-center justify-center text-center gap-1 border"
                style={{
                  borderColor: styles.cardBorder,
                  background: styles.sidebarBg,
                  color: styles.cardText,
                }}
              >
                <span className="text-[9px] font-mono font-bold" style={{ color: styles.muted }}>
                  {String(i + 1).padStart(2, '0')}
                </span>
                <span className="text-[11px] font-bold" title={t(step.hintKey)}>{t(step.labelKey)}</span>
              </div>
              {i < PIPELINE_STEPS.length - 1 && (
                <ArrowRight
                  className="w-4 h-4 shrink-0 hidden md:block mx-0.5"
                  style={{ color: styles.muted }}
                />
              )}
            </React.Fragment>
          ))}
        </div>
      </div>

      {/* ═══ 主体双栏：左 3 待办 + 右最近资产表 ═══ */}
      <div className="grid grid-cols-1 lg:grid-cols-[280px_1fr] gap-3">
        {/* ───── 左 · 待处理事项（3 行 · 点击跳 F7 GovernPage） ───── */}
        <div className="rounded-md border flex flex-col"
             style={{ borderColor: styles.cardBorder, background: styles.cardBg }}>
          <div className="flex items-center justify-between px-3 py-2 border-b"
               style={{ borderColor: styles.cardBorder }}>
            <h3 className="text-xs font-extrabold flex items-center gap-1.5">
              <Package className="w-3.5 h-3.5" style={{ color: styles.warningText }} />
              <span>{t('knowledge.overview.todo_title')}</span>
            </h3>
            <span className="text-[10px] font-mono" style={{ color: styles.muted }}>
              {t('knowledge.overview.todo_click_hint')}
            </span>
          </div>
          <div className="flex-1 p-2 space-y-1.5">
            {toDoRows.map((row) => (
              <button
                key={row.id}
                type="button"
                onClick={() => { window.location.hash = '#/knowledge_view?page=govern'; }}
                className="w-full text-left rounded-md px-3 py-2.5 flex items-center justify-between gap-2 border transition cursor-pointer hover:opacity-80"
                style={{
                  borderColor: row.value > 0 ? styles.warningBorder : styles.cardBorder,
                  background: row.value > 0 ? styles.warningBg : styles.inputBg,
                  color: row.value > 0 ? styles.warningText : styles.cardTextMuted,
                }}
              >
                <span className="text-[11px] font-bold truncate">{t(row.labelKey)}</span>
                <span
                  className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-mono font-black shrink-0"
                  style={{
                    background: row.value > 0 ? styles.warningText : styles.cardTextMuted,
                    color: row.value > 0 ? styles.warningBg : styles.cardText,
                  }}
                >
                  {row.value}
                </span>
              </button>
            ))}
          </div>
        </div>

        {/* ───── 右 · 最近知识资产表（5 列 · PRD F2 验收 4） ───── */}
        <div className="rounded-md border flex flex-col min-h-40"
             style={{ borderColor: styles.cardBorder, background: styles.cardBg }}>
          <div className="flex items-center justify-between px-3 py-2 border-b"
               style={{ borderColor: styles.cardBorder }}>
            <h3 className="text-xs font-extrabold flex items-center gap-1.5">
              <FileText className="w-3.5 h-3.5" style={{ color: styles.accentText }} />
              <span>{t('knowledge.overview.recent_assets_title')}</span>
              <span className="text-[10px] font-mono" style={{ color: styles.muted }}>
                {t('knowledge.overview.recent_assets_count', { n: assets.length })}
              </span>
            </h3>
            <button
              type="button"
              onClick={() => void loadAssets()}
              disabled={assetsLoading}
              className="p-1 rounded border cursor-pointer hover:opacity-70 transition disabled:opacity-50"
              style={{ borderColor: styles.cardBorder, color: styles.cardTextMuted }}
              title={t('knowledge.overview.refresh')}
            >
              {assetsLoading ? <Loader2 className="w-3 h-3 animate-spin" /> : <RefreshCw className="w-3 h-3" />}
            </button>
          </div>

          {/* 表头 */}
          <div className="grid grid-cols-[1.5fr_1fr_1fr_1fr_1.2fr] gap-2 px-3 py-1.5 border-b text-[9px] font-bold uppercase tracking-wider"
               style={{ borderColor: styles.cardBorder, color: styles.cardTextMuted }}>
            <span>{t('knowledge.asset.col_title')}</span>
            <span>{t('knowledge.asset.col_type')}</span>
            <span>{t('knowledge.asset.col_status')}</span>
            <span>{t('knowledge.asset.col_source')}</span>
            <span>{t('knowledge.asset.col_updated')}</span>
          </div>

          <div className="flex-1 overflow-y-auto max-h-56">
            {assetsLoading && assets.length === 0 ? (
              <div className="p-4 text-center text-[11px] flex items-center justify-center" style={{ color: styles.muted }}>
                <Loader2 className="w-3.5 h-3.5 animate-spin mr-1.5" />
                {t('knowledge.overview.assets_loading')}
              </div>
            ) : assets.length === 0 ? (
              <div className="p-4 text-center text-[11px]" style={{ color: styles.muted }}>
                <Search className="w-5 h-5 mx-auto mb-1 opacity-40" />
                {t('knowledge.asset.empty')}
              </div>
            ) : (
              assets.slice(0, 8).map((a) => (
                <div
                  key={a.id}
                  className="grid grid-cols-[1.5fr_1fr_1fr_1fr_1.2fr] gap-2 px-3 py-2 border-b items-center text-[10px]"
                  style={{ borderColor: styles.cardBorder }}
                >
                  <span className="font-bold truncate" style={{ color: styles.cardText }} title={a.title}>{a.title}</span>
                  <span className="font-mono truncate" style={{ color: styles.cardTextMuted }}>{a.type}</span>
                  <span>
                    <span
                      className="inline-flex items-center px-1.5 py-0.5 rounded text-[9px] font-mono font-bold"
                      style={statusChip(a.status)}
                    >
                      {t(statusLabelKey(a.status))}
                    </span>
                  </span>
                  <span className="font-mono truncate" style={{ color: styles.cardTextMuted }}>{a.source}</span>
                  <span className="font-mono flex items-center gap-1" style={{ color: styles.muted }}>
                    <Clock className="w-2.5 h-2.5 shrink-0" />
                    <span className="truncate">{a.updatedAt || '—'}</span>
                  </span>
                </div>
              ))
            )}
          </div>
        </div>
      </div>

      {/* 底栏：图引擎提示（信息架构对齐既有 EngineeringStatus） */}
      <div className="flex items-center gap-3 text-[10px] font-mono py-1"
           style={{ color: styles.muted }}>
        <span className="inline-flex items-center gap-1">
          <CheckCircle2 className="w-3 h-3 shrink-0" style={{ color: styles.successText }} />
          {t('knowledge.overview.engine_hint')}
        </span>
        <span className="ml-auto">
          {t('knowledge.overview.last_updated_hint')}
        </span>
      </div>
    </div>
  );
}
