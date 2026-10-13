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
import type { GraphStats } from '../services/knowledgeApi';
import { kbFetchGraphStats, kbFetchGraphJobs } from '../services/overview';
import { kbFetchStructuredJobs, fetchExtractCandidateFiles } from '../services/extract';
import { KB_STATS_REFRESH_EVENT } from '../typesAndConstants';
import { fetchNavProducts, type NavProductItemVO } from '../../../services/knowledgeNavApi';

/**
 * KPI 6 指标（PRD F2）
 * - 知识资产（articleCount） / 图谱实体（graphNodeCount） / 图谱关系（graphEdgeCount）
 * - 向量索引（embeddingCount） / 待审核（pendingFilesCount） / 抽取任务（runningJobsCount）
 * 数据源：kbFetchGraphStats 提供文章数/节点/边/向量；pending/running 从对应端点独立拉，
 * 每路失败以「—」+ 降级标记呈现（杜绝失败=0=一切正常的假真理）。
 */

/** 流水线 5 步（PRD F2「知识生产流水线」）— 主题主导色 chip */
const PIPELINE_STEPS = [
  { id: 'ontology_mapping', labelKey: 'knowledge.overview.pipeline_ontology', hintKey: 'knowledge.overview.pipeline_ontology_hint' },
  { id: 'data_extraction',  labelKey: 'knowledge.overview.pipeline_extraction', hintKey: 'knowledge.overview.pipeline_extraction_hint' },
  { id: 'knowledge_candidate', labelKey: 'knowledge.overview.pipeline_candidate', hintKey: 'knowledge.overview.pipeline_candidate_hint' },
  { id: 'review_publish',     labelKey: 'knowledge.overview.pipeline_publish', hintKey: 'knowledge.overview.pipeline_publish_hint' },
  { id: 'kg_vector',          labelKey: 'knowledge.overview.pipeline_kg_vector', hintKey: 'knowledge.overview.pipeline_kg_vector_hint' },
] as const;

/** 待处理事项 3 行（PRD F2「待处理事项」）
 *  value=null → 不可用（无真实端点）；error=true → 拉取失败（区别于真实 0）。
 *  target 为该事项在知识工作台的真实承接页（深链 ?page=&gsub=）。 */
interface ToDoRow {
  id: 'ai_candidates' | 'wiki_pending' | 'index_failed';
  labelKey: string;
  value: number | null;
  error: boolean;
  available: boolean;
  /** 承接路由 hash；available=false 时忽略 */
  target: string;
  /** 不可用原因（available=false 时展示） */
  reasonKey?: string;
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

  // ── 四路降级标记（各路由独立，失败≠0=正常） ──
  const [statsErr, setStatsErr] = useState<boolean>(false);
  const [filesErr, setFilesErr] = useState<boolean>(false);
  const [jobsErr, setJobsErr] = useState<boolean>(false);
  const [indexErr, setIndexErr] = useState<boolean>(false);

  // ── 待审核 / 抽取任务（KPI 4-6） ──
  const [pendingFiles, setPendingFiles] = useState<number>(0);
  const [runningJobs, setRunningJobs] = useState<number>(0);

  // ── 待办：索引/构建失败（KG 构建作业 FAILED 真实计数） ──
  const [indexFailed, setIndexFailed] = useState<number>(0);

  // ── 最近资产表 ──
  const [assets, setAssets] = useState<AssetRow[]>([]);
  const [assetsLoading, setAssetsLoading] = useState<boolean>(true);
  const [assetsErr, setAssetsErr] = useState<boolean>(false);

  // ── 资产表 loading（用于顶栏遮罩） ──
  const [refreshing, setRefreshing] = useState<boolean>(false);

  /**
   * 一次性并发拉 4 路（每路独立错误标记，失败绝不静默显示 0=一切正常）：
   *  1) kbFetchGraphStats（KPI 1-4 主源；失败 statsErr）
   *  2) fetchExtractCandidateFiles → 过滤 pending（KPI 待审核 + AI 候选待办；失败 filesErr）
   *  3) kbFetchStructuredJobs → status=RUNNING 计数（KPI 抽取任务；失败 jobsErr）
   *  4) kbFetchGraphJobs → status=FAILED 计数（索引/构建失败待办；失败 indexErr）
   * Wiki 待审核无对应真实端点 → 待办标注「不可用」（见 toDoRows）。
   */
  const loadAll = useCallback(async (): Promise<void> => {
    setRefreshing(true);
    const [statsR, filesR, jobsR, graphJobsR] = await Promise.all([
      kbFetchGraphStats().then((v) => ({ ok: true as const, v })).catch(() => ({ ok: false as const, v: null as GraphStats | null })),
      fetchExtractCandidateFiles().then((v) => ({ ok: true as const, v })).catch(() => ({ ok: false as const, v: null as Awaited<ReturnType<typeof fetchExtractCandidateFiles>> | null })),
      kbFetchStructuredJobs(1, 5).then((v) => ({ ok: true as const, v })).catch(() => ({ ok: false as const, v: null as unknown[] | null })),
      kbFetchGraphJobs().then((v) => ({ ok: true as const, v })).catch(() => ({ ok: false as const, v: null as Awaited<ReturnType<typeof kbFetchGraphJobs>> | null })),
    ]);

    // 1) KPI 1-4（Article / Nodes / Edges / Embedding）
    setStatsErr(!statsR.ok);
    if (statsR.ok && statsR.v) setGraphStats(statsR.v);
    else setGraphStats({ graphNodeCount: 0, graphEdgeCount: 0, embeddingCount: 0, ruleCount: 0 });

    // 2) KPI 待审核数 + AI 候选待办
    setFilesErr(!filesR.ok);
    if (filesR.ok && filesR.v) {
      const pendingList = filesR.v.filter((f) => {
        const s = (f.status || '').trim().toUpperCase();
        return ['PENDING', 'QUEUED', ''].includes(s);
      });
      setPendingFiles(pendingList.length);
    } else {
      setPendingFiles(0);
    }

    // 3) KPI 抽取任务 running 数（后端无显式 running 字段，从 status 推导）
    setJobsErr(!jobsR.ok);
    if (jobsR.ok && jobsR.v) {
      const runningCount = jobsR.v.filter((j) => {
        const st = String((j as { status?: string }).status || '').toLowerCase();
        return ['running', 'in_progress'].includes(st);
      }).length;
      setRunningJobs(runningCount);
    } else {
      setRunningJobs(0);
    }

    // 4) 索引/构建失败待办 — KG/向量构建作业 FAILED 真实计数
    setIndexErr(!graphJobsR.ok);
    if (graphJobsR.ok && graphJobsR.v) {
      setIndexFailed(graphJobsR.v.filter((j) => String(j.status || '').toUpperCase() === 'FAILED').length);
    } else {
      setIndexFailed(0);
    }

    setRefreshing(false);
  }, []);

  useEffect(() => { void loadAll(); }, [loadAll]);
  // 监听萃取成功事件（与 Batch 1 既有 OverviewDashboard 同名事件保一致）
  useEffect(() => {
    const handler = () => { void loadAll(); };
    window.addEventListener(KB_STATS_REFRESH_EVENT, handler);
    return () => { window.removeEventListener(KB_STATS_REFRESH_EVENT, handler); };
  }, [loadAll]);

  /** 最近知识资产表（fetchNavProducts top 10） */
  const loadAssets = useCallback(async () => {
    setAssetsLoading(true);
    try {
      const res = await fetchNavProducts({ pageNum: 1, pageSize: 10 });
      const list: NavProductItemVO[] = res?.list || [];
      setAssetsErr(false);
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
      setAssetsErr(true);
    } finally {
      setAssetsLoading(false);
    }
  }, []);

  useEffect(() => { void loadAssets(); }, [loadAssets]);

  const statusChipClass = (status: AssetRow['status']) => {
    if (status === 'published') return `${styles.successBg} ${styles.successText}`;
    if (status === 'review') return `${styles.warningBg} ${styles.warningText}`;
    return `${styles.badgeBg} ${styles.badgeText}`;
  };

  const statusLabelKey = (status: AssetRow['status']) =>
    status === 'published' ? 'knowledge.asset.status_published'
    : status === 'review' ? 'knowledge.asset.status_review'
    : 'knowledge.asset.status_draft';

  const toDoRows: ToDoRow[] = useMemo<ToDoRow[]>(() => [
    {
      id: 'ai_candidates',
      labelKey: 'knowledge.overview.todo_ai_candidates',
      value: filesErr ? null : pendingFiles,
      error: filesErr,
      available: !filesErr,
      target: '#/knowledge_view?page=govern&gsub=audit',
    },
    {
      // Wiki 待审核：无对应真实端点 → 明确标注不可用（不臆造计数）
      id: 'wiki_pending',
      labelKey: 'knowledge.overview.todo_wiki_pending',
      value: null,
      error: false,
      available: false,
      target: '',
      reasonKey: 'knowledge.overview.todo_wiki_unavailable',
    },
    {
      // 索引/构建失败：KG/向量构建作业 FAILED 真实计数，承接抽取作业页
      id: 'index_failed',
      labelKey: 'knowledge.overview.todo_index_failed',
      value: indexErr ? null : indexFailed,
      error: indexErr,
      available: !indexErr,
      target: '#/knowledge_view?page=extract',
    },
  ], [pendingFiles, filesErr, indexFailed, indexErr]);

  const kpiCards: Array<{ id: string; labelKey: string; value: number; icon: React.ReactNode; accent: string; muted: boolean; error: boolean }> = [
    {
      id: 'assets',
      labelKey: 'knowledge.overview.kpi_assets',
      value: graphStats.articleCount ?? 0,
      icon: <FileText className="w-4 h-4" />,
      accent: styles.accentText,
      muted: (graphStats.articleCount ?? 0) === 0,
      error: statsErr,
    },
    {
      id: 'entities',
      labelKey: 'knowledge.overview.kpi_entities',
      value: graphStats.graphNodeCount,
      icon: <Network className="w-4 h-4" />,
      accent: styles.infoText,
      muted: graphStats.graphNodeCount === 0,
      error: statsErr,
    },
    {
      id: 'relations',
      labelKey: 'knowledge.overview.kpi_relations',
      value: graphStats.graphEdgeCount,
      icon: <Layers className="w-4 h-4" />,
      accent: styles.successText,
      muted: graphStats.graphEdgeCount === 0,
      error: statsErr,
    },
    {
      id: 'vector',
      labelKey: 'knowledge.overview.kpi_vector',
      value: graphStats.embeddingCount,
      icon: <Database className="w-4 h-4" />,
      accent: styles.warningText,
      muted: graphStats.embeddingCount === 0,
      error: statsErr,
    },
    {
      id: 'pending',
      labelKey: 'knowledge.overview.kpi_pending',
      value: pendingFiles,
      icon: <FileSearch className="w-4 h-4" />,
      accent: styles.warningText,
      muted: pendingFiles === 0,
      error: filesErr,
    },
    {
      id: 'jobs',
      labelKey: 'knowledge.overview.kpi_jobs',
      value: runningJobs,
      icon: <Activity className="w-4 h-4" />,
      accent: styles.accentText,
      muted: runningJobs === 0,
      error: jobsErr,
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

      {/* ═══ KPI 6 卡片（grid 6 列 · 数字 25px / 标签 12px + 灰色 · PRD F2 验收 2）
          每路独立降级：拉取失败显示「—」而非伪 0，杜绝「失败=一切正常」假真理 ═══ */}
      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3">
        {kpiCards.map((kpi) => (
          <div
            key={kpi.id}
            className="rounded-md border p-3 space-y-1.5 flex flex-col"
            style={{ borderColor: kpi.error ? styles.dangerBorder : styles.cardBorder, background: styles.cardBg }}
          >
            <div className={`flex items-center gap-1.5 ${kpi.error ? styles.dangerText : styles.cardTextMuted}`}>
              <span className={kpi.error ? styles.dangerText : kpi.accent} style={{ opacity: kpi.muted && !kpi.error ? 0.5 : 1 }}>
                {kpi.error ? <Activity className="w-4 h-4" /> : kpi.icon}
              </span>
              <span className="text-[12px] font-bold uppercase tracking-wider">{t(kpi.labelKey)}</span>
            </div>
            <p className={`text-[25px] font-black font-mono leading-none ${kpi.error ? styles.dangerText : kpi.accent}`}>
              {kpi.error ? '—' : kpi.value.toLocaleString()}
            </p>
            <span className="text-[9px] font-mono" style={{ color: styles.muted }}>
              {kpi.error ? t('knowledge.overview.kpi_unavailable') : kpi.muted ? t('knowledge.overview.kpi_zero') : ''}
            </span>
          </div>
        ))}
      </div>

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
          <div className="flex-1 p-2 space-y-2">
            {toDoRows.map((row) => {
              const hasWork = row.value !== null && row.value > 0;
              const badge = row.error
                ? '—'
                : row.value === null
                  ? t('knowledge.overview.todo_na')
                  : String(row.value);
              return (
                <div key={row.id} className="space-y-1">
                  <button
                    type="button"
                    disabled={!row.available}
                    onClick={() => { if (row.available && row.target) window.location.hash = row.target; }}
                    className={`w-full text-left rounded-md px-3 py-2.5 flex items-center justify-between gap-2 border transition ${row.available ? 'cursor-pointer hover:opacity-80' : 'cursor-not-allowed opacity-60'}`}
                    style={{
                      borderColor: hasWork ? styles.warningBorder : styles.cardBorder,
                      background: hasWork ? styles.warningBg : styles.inputBg,
                      color: hasWork ? styles.warningText : styles.cardTextMuted,
                    }}
                  >
                    <span className="text-[11px] font-bold truncate">{t(row.labelKey)}</span>
                    <span
                      className={`inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-mono font-black shrink-0 ${hasWork ? styles.warningText : styles.cardTextMuted}`}
                      style={{
                        background: hasWork ? styles.warningBg : styles.cardBorder,
                        color: hasWork ? 'inherit' : styles.cardText,
                      }}
                    >
                      {badge}
                    </span>
                  </button>
                  {row.error && (
                    <p className="text-[9px] font-mono pl-1" style={{ color: styles.dangerText }}>
                      {t('knowledge.overview.todo_fetch_failed')}
                    </p>
                  )}
                  {!row.available && !row.error && row.reasonKey && (
                    <p className="text-[9px] font-mono pl-1" style={{ color: styles.muted }}>
                      {t(row.reasonKey)}
                    </p>
                  )}
                </div>
              );
            })}
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
            ) : assetsErr && assets.length === 0 ? (
              <div className={`p-4 text-center text-[11px] ${styles.dangerText}`}>
                <Activity className="w-5 h-5 mx-auto mb-1 opacity-60" />
                <p>{t('knowledge.overview.assets_failed')}</p>
                <button
                  type="button"
                  onClick={() => void loadAssets()}
                  className="mt-1 text-[10px] underline cursor-pointer"
                >
                  {t('knowledge.overview.retry')}
                </button>
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
                      className={`inline-flex items-center px-1.5 py-0.5 rounded text-[9px] font-mono font-bold ${statusChipClass(a.status)}`}
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
