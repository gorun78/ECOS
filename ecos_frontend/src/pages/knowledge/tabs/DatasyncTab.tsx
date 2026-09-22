/**
 * Wave 3 C2 — 数据同步 Tab（本体选取 + 定时/立即抽取 + 实时进度 + 日志导出 + tier banner）
 *
 * 自上而下 4 个板块：
 *   §  顶部 tier banner（T12）—— TIER='enterprise' 占位，下一 wave 从后端读
 *   §  本体选取（T7）—— 调用 C1 的 OntologyTreePicker
 *   §  抽取方式（T5+T8）—— 立即 / 定时 二选一；立即走 triggerStructuredExtract，
 *                          定时走 C1 的 createScheduledExtractRow / updateScheduledExtractRow
 *   §  抽取监控（T10）—— 复用 ./components/MonitorPanel（2s 轮询 + cleanup）
 *   ∧  审计占位 hint
 */
import { useCallback, useEffect, useState } from 'react';
import type { FormEvent, ReactNode } from 'react';
import { Calendar, Database, Layers, Pencil, Plus, ShieldCheck, Trash2 } from 'lucide-react';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
import {
  knowledgeApi,
  fetchScheduledExtracts,
  createScheduledExtractRow,
  updateScheduledExtractRow,
  deleteScheduledExtract,
  triggerStructuredExtract,
  type ScheduledExtractVo,
  type EntityMappingItem,
} from '../services/knowledgeApi';
import { showToastGlobal } from '../../../components/common/Toast';
import OntologyTreePicker from './components/OntologyTreePicker';
import MonitorPanel from './components/MonitorPanel';

type ExtractMode = 'now' | 'scheduled';
type SyncMode = 'FULL' | 'INCREMENTAL';
type TickPeriod = 'DAILY' | 'WEEKLY' | 'MONTHLY';

const TIER_PLACEHOLDER = 'enterprise';
const STORAGE_OF: Record<string, string> = {
  standard: 'PostgreSQL',
  enterprise: 'Neo4j',
  ultimate: 'Doris',
};

const PERIOD_LABEL_KEY: Record<TickPeriod, string> = {
  DAILY: 'knowledge.tick_mode.period_daily',
  WEEKLY: 'knowledge.tick_mode.period_weekly',
  MONTHLY: 'knowledge.tick_mode.period_monthly',
};

/** 后端 ScheduledExtractVo.period 是 string 形态，安全取出 i18n key（fallback 到 daily） */
function periodLabelKey(period: string): string {
  return PERIOD_LABEL_KEY[period as TickPeriod] ?? PERIOD_LABEL_KEY.DAILY;
}

/** Toggle 行使用的 mode 联合类型安全取值 */
function modeOf(row: { mode: string }): SyncMode {
  return row.mode === 'FULL' ? 'FULL' : 'INCREMENTAL';
}

interface SchedulerDraft {
  id: number | null;
  name: string;
  period: TickPeriod;
  timeOfDay: string;
  mode: SyncMode;
}

const EMPTY_DRAFT: SchedulerDraft = {
  id: null,
  name: '',
  period: 'DAILY',
  timeOfDay: '02:00',
  mode: 'INCREMENTAL',
};

export default function DatasyncTab() {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [activeMode, setActiveMode] = useState<ExtractMode>('now');
  const [syncMode, setSyncMode] = useState<SyncMode>('INCREMENTAL');
  const [running, setRunning] = useState(false);
  const [dryRunning, setDryRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [active, setActive] = useState<{ taskId: string; dryRun: boolean } | null>(null);
  const [activeTrigger, setActiveTrigger] = useState<'MANUAL' | 'SCHEDULED'>('MANUAL');

  // 定时任务
  const [schedules, setSchedules] = useState<ScheduledExtractVo[]>([]);
  const [schedulesLoading, setSchedulesLoading] = useState(false);
  const [formOpen, setFormOpen] = useState(false);
  const [draft, setDraft] = useState<SchedulerDraft>(EMPTY_DRAFT);
  const [savingDraft, setSavingDraft] = useState(false);

  /** 加载映射契约表（只读，本体工作台契约 §0.5） */
  const [mappings, setMappings] = useState<EntityMappingItem[]>([]);

  useEffect(() => {
    void (async () => {
      try { setMappings(await knowledgeApi.fetchEntityMappings()); } catch { setMappings([]); }
    })();
  }, []);

  /** 加载定时任务列表 */
  const loadSchedules = useCallback(async () => {
    setSchedulesLoading(true);
    try { setSchedules(await fetchScheduledExtracts()); }
    catch { setSchedules([]); }
    finally { setSchedulesLoading(false); }
  }, []);

  useEffect(() => { void loadSchedules(); }, [loadSchedules]);

  /** 通知 Overview 总览重拉 KB 统计（萃取落地后图谱/向量计数变化） */
  const notifyStatsRefresh = useCallback(() => {
    window.dispatchEvent(new CustomEvent('kb:stats:refresh'));
  }, []);

  /** 立即抽取（执行或 dry-run）
   *  W3：消除「点了没反应」歧义 ——
   *   1) dry-run（同步 report）回显实体映射数与节点创建/更新数（不再静默）
   *   2) 真实执行后端未返回 taskId 时 warn 提示（如无映射可萃取）
   *   3) 提交成功后发 kb:stats:refresh 让总览重拉
   */
  const trigger = useCallback(async (runDryAndReturnOnly: boolean) => {
    setError(null);
    if (runDryAndReturnOnly) {
      setDryRunning(true);
    } else {
      setRunning(true);
    }
    try {
      // 即使用户未选本体，也允许「全部本体」语义触发
      const resp = await triggerStructuredExtract({ dryRun: runDryAndReturnOnly, mode: syncMode });
      if (runDryAndReturnOnly) {
        // 同步路径回显 EntityInstanceExtractionReportVO 关键计数
        showToastGlobal('info', t('knowledge.datasync.extract.dryrun_result', {
          count: resp.entityCount ?? 0,
          created: resp.nodeCreated ?? 0,
          updated: resp.nodeUpdated ?? 0,
        }));
        return;
      }
      // 异步路径：taskId truthy → 进监控面板 + 通知总览重拉
      if (resp.taskId) {
        setActive({ taskId: String(resp.taskId), dryRun: false });
        setActiveTrigger('MANUAL');
        showToastGlobal('success', t('knowledge.datasync.extract.submitted'));
        notifyStatsRefresh();
        return;
      }
      // 无 taskId（拒绝/无目标）→ warn 而非静默
      showToastGlobal('info', t('knowledge.datasync.extract.no_target', {
        count: resp.entityCount ?? 0,
        skipped: resp.nodeSkipped ?? 0,
      }));
    } catch (e) {
      console.warn('trigger structured extract failed', e);
      setError(String(e));
      showToastGlobal('error', String(e));
    } finally {
      setRunning(false);
      setDryRunning(false);
    }
  }, [syncMode, t, notifyStatsRefresh]);

  /** 定时任务 CRUD（callback 入参用 ScheduledExtractVo；mode/period string 形态 → 联合类型） */
  const onToggleEnabled = useCallback(async (row: ScheduledExtractVo) => {
    try {
      await updateScheduledExtractRow(row.id, {
        name: row.name,
        ontologyIds: row.ontologyIds,
        mode: modeOf(row),
        period: row.period as TickPeriod,
        timeOfDay: row.timeOfDay || '02:00',
        enabled: !row.enabled,
      });
      void loadSchedules();
    } catch (e) {
      console.warn('toggle schedule failed', e);
      setError(String(e));
    }
  }, [loadSchedules]);

  const onDelete = useCallback(async (row: ScheduledExtractVo) => {
    if (!window.confirm(t('knowledge.datasync.sched.del_confirm'))) return;
    try {
      await deleteScheduledExtract(row.id);
      void loadSchedules();
    } catch (e) {
      console.warn('delete schedule failed', e);
      setError(String(e));
    }
  }, [loadSchedules, t]);

  const onEditStart = useCallback((row: ScheduledExtractVo) => {
    setDraft({
      id: row.id,
      name: row.name,
      period: row.period as TickPeriod,
      timeOfDay: row.timeOfDay || '02:00',
      mode: modeOf(row),
    });
    setFormOpen(true);
  }, []);

  const onCreateStart = useCallback(() => {
    setDraft(EMPTY_DRAFT);
    setFormOpen(true);
  }, []);

  const onFormSubmit = useCallback(async (e: FormEvent) => {
    e.preventDefault();
    if (!draft.name.trim()) return;
    if (selectedIds.length === 0) {
      setError(t('knowledge.datasync.monitor.select_hint'));
      return;
    }
    setSavingDraft(true);
    setError(null);
    try {
      const body = {
        name: draft.name,
        ontologyIds: selectedIds,
        mode: draft.mode,
        period: draft.period,
        timeOfDay: draft.timeOfDay,
      };
      if (draft.id === null) {
        await createScheduledExtractRow(body);
      } else {
        await updateScheduledExtractRow(draft.id, { ...body, enabled: true });
      }
      setFormOpen(false);
      setDraft(EMPTY_DRAFT);
      void loadSchedules();
    } catch (er) {
      console.warn('save scheduled extract failed', er);
      setError(String(er));
    } finally {
      setSavingDraft(false);
    }
  }, [draft, selectedIds, loadSchedules, t]);

  const onPickExec = useCallback(() => { void trigger(false); }, [trigger]);
  const onPickDryRun = useCallback(() => { void trigger(true); }, [trigger]);

  return (
    <div className="h-full overflow-y-auto p-6 font-sans">
      <div className="max-w-7xl mx-auto space-y-6">

        {/* §1 标题 + hero */}
        <div className="space-y-1">
          <h2 className="font-bold text-xl tracking-tight flex items-center gap-2" style={{ color: styles.cardText }}>
            <Database className="w-5 h-5" style={{ color: styles.accentText }} />
            {t('knowledge.datasync.title')}
          </h2>
          <p className="text-xs" style={{ color: styles.cardTextMuted }}>{t('knowledge.datasync.subtitle')}</p>
          <p className="text-xs leading-normal mt-2 pl-3 border-l-2" style={{ color: styles.cardText, borderColor: styles.accentBg }}>
            {t('knowledge.datasync.hero')}
          </p>
        </div>

        {/* §1b tier banner（T12） */}
        <div
          className={`flex items-center justify-between px-4 py-2.5 rounded-xl border ${styles.warningBg}`}
          style={{ borderColor: styles.warningBorder }}
        >
          <span className="text-xs font-mono tracking-wide" style={{ color: styles.warningText }}>
            {t('knowledge.tier.storage_tier_banner', {
              tier: TIER_PLACEHOLDER,
              storage: STORAGE_OF[TIER_PLACEHOLDER] || 'PostgreSQL',
            })}
          </span>
          <span className={`text-[10px] font-mono uppercase tracking-wider ${styles.muted}`}>
            {t('knowledge.tier.enterprise_pg_neo4j')}
          </span>
        </div>

        {/* §2 本体选取（T7） */}
        <div className={`border rounded-xl p-4 space-y-3 ${styles.cardBg} ${styles.cardBorder}`}>
          <div className="flex items-start gap-2">
            <ShieldCheck className="w-4 h-4 mt-0.5 shrink-0" style={{ color: styles.accentText }} />
            <div className="flex-1 space-y-0.5">
              <h3 className="font-semibold text-sm" style={{ color: styles.cardText }}>
                {t('knowledge.datasync.section.ontology_title')}
              </h3>
              <p className="text-[11px] leading-normal" style={{ color: styles.cardTextMuted }}>
                {t('knowledge.datasync.section.ontology_desc')}
              </p>
            </div>
          </div>
          <OntologyTreePicker
            selectedOntologyIds={selectedIds}
            onChange={setSelectedIds}
          />
        </div>

        {/* §2b 映射契约摘要（Wave 0 保留 · 本体工作台契约只读） */}
        <div className={`flex items-center justify-between border rounded-xl px-4 py-2 ${styles.inputBg}`} style={{ borderColor: styles.cardBorder }}>
          <span className="text-[10px] font-mono uppercase tracking-wider" style={{ color: styles.muted }}>
            {t('knowledge.datasync.mappings_title')}
          </span>
          <span className="text-xs font-mono" style={{ color: styles.cardText }}>
            {mappings.length}
          </span>
        </div>

        {/* §3 抽取方式（T5 + T8） */}
        <div className={`border rounded-xl p-4 space-y-4 ${styles.cardBg} ${styles.cardBorder}`}>
          <div className="space-y-0.5">
            <h3 className="font-semibold text-sm" style={{ color: styles.cardText }}>
              {t('knowledge.datasync.section.extract_mode')}
            </h3>
            <p className="text-[11px]" style={{ color: styles.cardTextMuted }}>
              {t('knowledge.datasync.section.extract_mode_desc')}
            </p>
          </div>

          {/* 单选 pill：立即 / 定时 */}
          <div className="flex gap-1.5">
            <ModePill
              active={activeMode === 'now'}
              onClick={() => { setActiveMode('now'); }}
              styles={styles}
              label={t('knowledge.tick_mode.now')}
            />
            <ModePill
              active={activeMode === 'scheduled'}
              onClick={() => { setActiveMode('scheduled'); }}
              styles={styles}
              label={t('knowledge.tick_mode.scheduled')}
            />
          </div>

          {/* 错误横幅 */}
          {error && (
            <div className={`px-3 py-2 rounded-lg border text-xs ${styles.dangerBg}`} style={{ borderColor: styles.dangerBorder, color: styles.dangerText }}>
              {error}
            </div>
          )}

          {activeMode === 'now' ? (
            <NowMode
              running={running} dryRunning={dryRunning}
              syncMode={syncMode} onSyncMode={setSyncMode}
              onPickExec={onPickExec} onPickDryRun={onPickDryRun}
              t={t} styles={styles}
              showAllHint={selectedIds.length === 0}
            />
          ) : (
            <ScheduledMode
              schedules={schedules}
              loading={schedulesLoading}
              formOpen={formOpen}
              onOpenNew={onCreateStart}
              onCloseForm={() => { setFormOpen(false); setDraft(EMPTY_DRAFT); }}
              draft={draft} onDraftChange={setDraft}
              onSubmit={onFormSubmit}
              saving={savingDraft}
              onEdit={onEditStart}
              onToggleEnabled={onToggleEnabled}
              onDelete={onDelete}
              selectedCount={selectedIds.length}
              t={t} styles={styles}
            />
          )}
        </div>

        {/* §4 抽取监控（T10） */}
        <MonitorPanel
          active={active}
          trigger={activeTrigger}
        />

        {/* §5 落库审计占位（K1 已落；此处只给 hint） */}
        <div className={`flex items-start gap-2 border rounded-xl p-3 ${styles.badgeBg}`} style={{ borderColor: styles.cardBorder }}>
          <Calendar className="w-3.5 h-3.5 mt-0.5 shrink-0" style={{ color: styles.muted }} />
          <p className="text-[11px] leading-normal" style={{ color: styles.muted }}>
            <span className="font-bold">{t('knowledge.datasync.section.audit')}:</span>{' '}
            {t('knowledge.datasync.audit_hint')}
          </p>
        </div>
      </div>
    </div>
  );
}

/* ── 立即抽取子面板 ───────────────────────────────────────────────────────────── */

function NowMode(props: {
  running: boolean; dryRunning: boolean;
  syncMode: SyncMode; onSyncMode: (m: SyncMode) => void;
  onPickExec: () => void; onPickDryRun: () => void;
  t: (k: string, p?: Record<string, string | number>) => string;
  styles: ReturnType<typeof useTheme>['styles'];
  showAllHint: boolean;
}) {
  const { running, dryRunning, syncMode, onSyncMode, onPickExec, onPickDryRun, t, styles, showAllHint } = props;
  const busy = running || dryRunning;
  return (
    <div className="space-y-3">
      {/* 未选本体时的前置提示（仅提示，不软性禁用） */}
      {showAllHint && (
        <p className="text-[11px] leading-normal" style={{ color: styles.muted }}>
          {t('knowledge.datasync.precondition.hint')}
        </p>
      )}
      {/* mode 单选（全量覆盖 / 增量更新） */}
      <div className="flex items-center gap-2">
        <span className="text-[11px]" style={{ color: styles.muted }}>{t('knowledge.datasync.mode.label')}:</span>
        <RadioPill
          active={syncMode === 'FULL'}
          onClick={() => onSyncMode('FULL')}
          styles={styles}
          label={t('knowledge.sync_mode.full')}
        />
        <RadioPill
          active={syncMode === 'INCREMENTAL'}
          onClick={() => onSyncMode('INCREMENTAL')}
          styles={styles}
          label={t('knowledge.sync_mode.incremental')}
        />
      </div>
      {showAllHint && (
        <p className="text-[10px] font-mono" style={{ color: styles.muted }}>
          {t('knowledge.datasync.monitor.select_hint')}
        </p>
      )}
      {/* 按钮链：dry-run（统计 only）+ 真的立即执行 */}
      <div className="flex flex-wrap items-center gap-2 pt-1">
        <button
          type="button"
          disabled={busy}
          onClick={onPickDryRun}
          className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold border cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed transition hover:opacity-80 ${styles.badgeBg}`}
          style={{ borderColor: styles.cardBorder, color: styles.cardText }}
          title={t('knowledge.datasync.extract.erase_hint')}
        >
          {dryRunning ? <Layers size={12} className="animate-pulse" /> : <Layers size={12} />}
          {t('knowledge.datasync.monitor.pick_dry_run')}
        </button>
        <button
          type="button"
          disabled={busy}
          onClick={onPickExec}
          className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed transition hover:opacity-90`}
          style={{ background: styles.accentBg, color: '#fff' }}
        >
          {running ? <Database className="animate-pulse" size={12} /> : <Database size={12} />}
          {t('knowledge.datasync.monitor.pick_exec')}
        </button>
      </div>
      {/* 提交后 round-trip 说明（消除"点了没反应"歧义：执行走监控面板 / dry-run 回显 toast） */}
      <p className="text-[10px] font-mono" style={{ color: styles.muted }}>
        {t('knowledge.datasync.extract.submit_feedback')}
      </p>
    </div>
  );
}

/* ── 定时抽取子面板 ───────────────────────────────────────────────────────────── */

function ScheduledMode(props: {
  schedules: ScheduledExtractVo[];
  loading: boolean;
  formOpen: boolean;
  onOpenNew: () => void;
  onCloseForm: () => void;
  draft: SchedulerDraft;
  onDraftChange: (d: SchedulerDraft) => void;
  onSubmit: (e: FormEvent) => void;
  saving: boolean;
  onEdit: (row: ScheduledExtractVo) => void;
  onToggleEnabled: (row: ScheduledExtractVo) => void;
  onDelete: (row: ScheduledExtractVo) => void;
  selectedCount: number;
  t: (k: string, p?: Record<string, string | number>) => string;
  styles: ReturnType<typeof useTheme>['styles'];
}) {
  const {
    schedules, loading, formOpen, onOpenNew, onCloseForm,
    draft, onDraftChange, onSubmit, saving,
    onEdit, onToggleEnabled, onDelete, selectedCount, t, styles,
  } = props;

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <p className={`text-[10px] font-mono uppercase tracking-wider ${styles.muted}`}>
          {t('knowledge.tick_mode.scheduled')} · {schedules.length}
        </p>
        <button
          type="button"
          onClick={onOpenNew}
          disabled={saving}
          className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-lg text-xs font-semibold cursor-pointer disabled:opacity-40 transition hover:opacity-90`}
          style={{ background: styles.accentBg, color: '#fff' }}
        >
          <Plus size={12} />
          {t('knowledge.datasync.sched.new')}
        </button>
      </div>

      {/* 表单（折叠） */}
      {formOpen && (
        <form
          onSubmit={onSubmit}
          className={`border rounded-lg p-3 space-y-3 ${styles.badgeBg}`}
          style={{ borderColor: styles.cardBorder }}
        >
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold" style={{ color: styles.cardText }}>
              {draft.id === null
                ? t('knowledge.datasync.sched.new_title')
                : t('knowledge.datasync.sched.edit_title', { name: draft.name || '…' })}
            </span>
            <button type="button" onClick={onCloseForm} className={`cursor-pointer ${styles.muted}`}>✕</button>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-2">
            <Field label={t('knowledge.datasync.sched.field.name')} styles={styles}>
              <input
                type="text"
                value={draft.name}
                onChange={e => onDraftChange({ ...draft, name: e.target.value })}
                className={`w-full px-2 py-1 rounded-md text-xs outline-none ${styles.inputBg} ${styles.inputText}`}
                style={{ border: `1px solid ${styles.inputBorder}` }}
                required
              />
            </Field>
            <Field label={t('knowledge.datasync.sched.field.period')} styles={styles}>
              <select
                value={draft.period}
                onChange={e => onDraftChange({ ...draft, period: e.target.value as TickPeriod })}
                className={`w-full px-2 py-1 rounded-md text-xs outline-none ${styles.inputBg} ${styles.inputText}`}
                style={{ border: `1px solid ${styles.inputBorder}` }}
              >
                {(['DAILY', 'WEEKLY', 'MONTHLY'] as TickPeriod[]).map(p => (
                  <option key={p} value={p}>{t(PERIOD_LABEL_KEY[p])}</option>
                ))}
              </select>
            </Field>
            <Field label={t('knowledge.datasync.sched.field.time')} styles={styles}>
              <input
                type="time"
                value={draft.timeOfDay}
                onChange={e => onDraftChange({ ...draft, timeOfDay: e.target.value })}
                className={`w-full px-2 py-1 rounded-md text-xs outline-none ${styles.inputBg} ${styles.inputText}`}
                style={{ border: `1px solid ${styles.inputBorder}` }}
              />
            </Field>
          </div>
          {/* mode 单选（与立即抽取联动） */}
          <div className="flex items-center gap-2">
            <span className="text-[11px]" style={{ color: styles.muted }}>{t('knowledge.datasync.mode.label')}:</span>
            <RadioPill active={draft.mode === 'FULL'} onClick={() => onDraftChange({ ...draft, mode: 'FULL' })} styles={styles} label={t('knowledge.sync_mode.full')} />
            <RadioPill active={draft.mode === 'INCREMENTAL'} onClick={() => onDraftChange({ ...draft, mode: 'INCREMENTAL' })} styles={styles} label={t('knowledge.sync_mode.incremental')} />
          </div>
          <p className="text-[10px] font-mono" style={{ color: styles.muted }}>
            {t('knowledge.datasync.sched.use_ontology_hint')} · {selectedCount}
          </p>
          <div className="flex flex-wrap gap-2 pt-1">
            <button
              type="submit"
              disabled={saving || !draft.name.trim() || selectedCount === 0}
              className={`inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-semibold cursor-pointer disabled:opacity-40 disabled:cursor-not-allowed transition`}
              style={{ background: styles.accentBg, color: '#fff' }}
            >
              {t('knowledge.tick_mode.save')}
            </button>
            <button
              type="button"
              onClick={onCloseForm}
              className={`inline-flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-semibold border cursor-pointer transition hover:opacity-80 ${styles.badgeBg}`}
              style={{ borderColor: styles.cardBorder, color: styles.cardText }}
            >
              ✕
            </button>
          </div>
        </form>
      )}

      {/* 列表 */}
      {loading ? (
        <div className="py-8 text-center text-xs" style={{ color: styles.muted }}>
          {t('knowledge.tree_picker.loading')}
        </div>
      ) : schedules.length === 0 ? (
        <p className="text-xs py-4 text-center" style={{ color: styles.muted }}>
          {t('knowledge.datasync.monitor.empty')}
        </p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-[11px]">
            <thead>
              <tr className="text-left font-mono text-[9px] uppercase tracking-wider" style={{ color: styles.muted }}>
                <th className="py-1 pr-2">{t('knowledge.datasync.sched.col.name')}</th>
                <th className="py-1 pr-2">{t('knowledge.datasync.sched.col.period')}</th>
                <th className="py-1 pr-2">{t('knowledge.datasync.sched.col.timeOfDay')}</th>
                <th className="py-1 pr-2">{t('knowledge.datasync.sched.col.ontologyCount')}</th>
                <th className="py-1 pr-2">{t('knowledge.tick_mode.enabled')}</th>
                <th className="py-1 pr-2">{t('knowledge.datasync.sched.col.lastRunAt')}</th>
                <th className="py-1 pr-2">{t('knowledge.datasync.sched.col.lastStatus')}</th>
                <th className="py-1" />
              </tr>
            </thead>
            <tbody>
              {schedules.map(row => (
                <tr key={row.id} className="border-t" style={{ borderColor: styles.cardBorder }}>
                  <td className="py-1.5 pr-2 font-mono" style={{ color: styles.cardText }}>{row.name}</td>
                  <td className="py-1.5 pr-2" style={{ color: styles.cardText }}>{t(periodLabelKey(row.period))}</td>
                  <td className="py-1.5 pr-2 font-mono" style={{ color: styles.cardText }}>{row.timeOfDay || '—'}</td>
                  <td className="py-1.5 pr-2" style={{ color: styles.cardText }}>{row.ontologyIds?.length ?? 0}</td>
                  <td className="py-1.5 pr-2">
                    <button
                      type="button"
                      onClick={() => void onToggleEnabled(row)}
                      className={`inline-flex items-center px-2 py-0.5 rounded-md text-[10px] font-mono font-bold border cursor-pointer transition ${row.enabled ? `${styles.successBg} ${styles.successText} ${styles.successBorder}` : `${styles.badgeBg} ${styles.muted} ${styles.cardBorder}`}`}
                      title={row.enabled ? t('knowledge.tick_mode.disabled') : t('knowledge.tick_mode.enabled')}
                    >
                      {row.enabled ? t('knowledge.tick_mode.enabled') : t('knowledge.tick_mode.disabled')}
                    </button>
                  </td>
                  <td className="py-1.5 pr-2 font-mono text-[10px]" style={{ color: styles.muted }}>{row.lastRunAt || '—'}</td>
                  <td className="py-1.5 pr-2 font-mono text-[10px]" style={{ color: row.lastStatus === 'FAILED' ? styles.dangerText : styles.muted }}>{row.lastStatus || '—'}</td>
                  <td className="py-1.5">
                    <div className="flex items-center gap-1 justify-end">
                      <button
                        type="button"
                        onClick={() => onEdit(row)}
                        className={`p-1 rounded-md cursor-pointer ${styles.sidebarHoverBg} ${styles.muted} hover:opacity-70`}
                        title={t('knowledge.datasync.sched.edit')}
                      >
                        <Pencil size={11} />
                      </button>
                      <button
                        type="button"
                        onClick={() => void onDelete(row)}
                        className={`p-1 rounded-md cursor-pointer hover:opacity-70`}
                        style={{ color: styles.dangerText }}
                        title={t('knowledge.tick_mode.delete')}
                      >
                        <Trash2 size={11} />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

/* ── 行内组件（局部共享，避免重复逻辑） ────────────────────────────────────────── */

function ModePill({ active, onClick, styles, label }: {
  active: boolean; onClick: () => void;
  styles: ReturnType<typeof useTheme>['styles']; label: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex-1 flex items-center justify-center gap-1 px-3 py-1.5 rounded-lg text-xs font-semibold border cursor-pointer transition ${
        active
          ? `${styles.sidebarActiveBg} ${styles.cardText}`
          : `${styles.badgeBg} ${styles.sidebarHoverBg} ${styles.appBorder} ${styles.sidebarText}`
      }`}
    >
      {label}
    </button>
  );
}

function RadioPill({ active, onClick, styles, label }: {
  active: boolean; onClick: () => void;
  styles: ReturnType<typeof useTheme>['styles']; label: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex items-center gap-1 px-2.5 py-1 rounded-lg text-[11px] font-bold border cursor-pointer transition ${
        active
          ? `${styles.sidebarActiveBg} ${styles.cardText}`
          : `${styles.badgeBg} ${styles.sidebarHoverBg} ${styles.appBorder} ${styles.sidebarText}`
      }`}
    >
      {active ? '●' : '○'}
      {label}
    </button>
  );
}

function Field({ label, styles, children }: {
  label: string;
  styles: ReturnType<typeof useTheme>['styles'];
  children: ReactNode;
}) {
  return (
    <label className="block">
      <span className="block text-[10px] font-mono uppercase tracking-wider mb-1" style={{ color: styles.muted }}>{label}</span>
      {children}
    </label>
  );
}
