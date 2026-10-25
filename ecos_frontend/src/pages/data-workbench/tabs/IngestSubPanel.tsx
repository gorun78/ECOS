/**
 * IngestSubPanel — 数据采集子面板（与元数据采集同一卡片）
 * 采集型管道 SOURCE_JDBC → SINK_MINIO，默认写入数据湖 MinIO 近源库。
 *
 * 能力：
 *  - 数据湖（MinIO 近源库）健康状态 + 初始化（ensure bucket）
 *  - 从元数据目录勾选表（多选批量采集）
 *  - 即时采集：POST /api/v1/datanet/ingest/run → 轮询各 taskId 状态
 *  - 定时采集：保存 cron 策略（metadata_config.ingest），由后端 DataIngestScheduler 到点触发
 *
 * @license Apache-2.0
 */
import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useTheme } from '../../../components/ThemeContext';
import { useLanguage } from '../../../components/LanguageContext';
import logger from '../../../services/logger';
import {
  Database,
  Play
} from 'lucide-react';
import { getErrorMessage } from '../helpers';
import type { DataConnection } from '../types';
import {
  fetchDatalakeStatus, initDatalake,
  runDataIngest, fetchIngestStatus,
  saveIngestSchedule, fetchIngestSchedule,
  collectFolderFiles,
} from '../api';

interface Props {
  conn: DataConnection;
  selectedNames: Set<string>;
  setSelectedNames: (v: Set<string>) => void;
  /** 文件夹数据源（fs）：采集调用 collectFolderFiles 而非 runDataIngest */
  isFs?: boolean;
  showToast: (type: string, message: string) => void;
}

interface IngestTaskState {
  table: string;
  taskId: string;
  status: string;
  progress?: number;
  errorMessage?: string;
}

const CRON_OPTIONS: { value: string; labelKey: string }[] = [
  { value: '0 0 * * *', labelKey: 'dw.strategy.cron.daily' },
  { value: '0 */6 * * *', labelKey: 'dw.strategy.cron.sixHourly' },
  { value: '0 0 */2 * *', labelKey: 'dw.strategy.cron.twoDays' },
  { value: '0 0 * * 1', labelKey: 'dw.strategy.cron.weekly' },
];

const IngestSubPanel: React.FC<Props> = ({ conn, selectedNames, setSelectedNames, isFs, showToast }) => {
  const { styles } = useTheme();
  const { t } = useLanguage();

  // ── 数据湖状态 ──
  const [datalake, setDatalake] = useState<{ endpoint?: string; bucket?: string; status?: string; initialized?: boolean } | null>(null);
  const [datalakeError, setDatalakeError] = useState<string | null>(null);
  const [initLoading, setInitLoading] = useState(false);

  // ── 表选择（由父组件 ConnectionsTab 持有，与目录列表复选框共享） ──
  const tables = useMemo(() => conn.tablesAvailable ?? [], [conn.tablesAvailable]);

  // ── 即时采集 ──
  const [running, setRunning] = useState(false);
  const [tasks, setTasks] = useState<IngestTaskState[]>([]);
  const pollTimer = useRef<ReturnType<typeof setInterval> | null>(null);

  // ── 定时策略 ──
  const [scheduleEnabled, setScheduleEnabled] = useState(false);
  const [cron, setCron] = useState('0 0 * * *');
  const [savingSchedule, setSavingSchedule] = useState(false);

  // 加载数据湖状态 + 定时策略（失败 ⇒ 显式错误态，不永显 '...'）
  const loadDatalakeStatus = React.useCallback(() => {
    setDatalakeError(null);
    fetchDatalakeStatus()
      .then(d => setDatalake(d ?? { status: 'UNKNOWN' }))
      .catch((e: unknown) => {
        logger.warn('[IngestSubPanel] fetchDatalakeStatus failed', e);
        setDatalakeError(getErrorMessage(e) || 'HTTP');
      });
  }, []);

  useEffect(() => {
    let cancelled = false;
    loadDatalakeStatus();
    fetchIngestSchedule(conn.id).then(s => {
      if (cancelled || !s) return;
      setScheduleEnabled(Boolean(s.enabled));
      if (s.cron) setCron(s.cron);
      // 父组件持有 selectedNames；仅在历史侧有表且父侧未选过时载入
      const current = selectedNames;
      if (Array.isArray(s.tables) && s.tables.length > 0 && current.size === 0) {
        setSelectedNames(new Set(s.tables));
      }
    }).catch((e: unknown) => { logger.warn('[IngestSubPanel] fetchIngestSchedule failed', e); });
    return () => { cancelled = true; };
  }, [conn.id]);

  // 卸载时清理轮询
  useEffect(() => () => { if (pollTimer.current) clearInterval(pollTimer.current); }, []);

  const toggleTable = (name: string) => {
    const next = new Set(selectedNames);
    if (next.has(name)) next.delete(name); else next.add(name);
    setSelectedNames(next);
  };

  const toggleAll = () => {
    const allNames = tables.length > 0
      ? tables.map(tb => (typeof tb === 'string' ? tb : (tb.name ?? '')))
      : Array.from(selectedNames);
    setSelectedNames(selectedNames.size === allNames.length ? new Set() : new Set(allNames));
  };

  /** 轮询本批全部任务（此前只轮询第 1 个，其余行永停 SUBMITTED = 假进度）。
   *  每 2s 对各非终态 taskId 查一次状态，全部终态后停表。 */
  const pollTasks = (taskList: IngestTaskState[]) => {
    if (pollTimer.current) clearInterval(pollTimer.current);
    const isTerminal = (s: string) => s === 'SUCCEEDED' || s === 'FAILED' || s === 'error' || s === 'COMPLETED';
    const tracked = taskList.filter(x => !isTerminal(x.status));
    if (tracked.length === 0) return;
    const tick = async () => {
      const results = await Promise.all(tracked.map(async x => {
        try {
          const st = await fetchIngestStatus(x.taskId);
          return st ? { ...x, status: st.status || x.status, progress: st.progress ?? x.progress, errorMessage: st.errorMessage ?? x.errorMessage } : x;
        } catch (e: unknown) {
          logger.warn('[IngestSubPanel] poll status failed', e);
          return x; // 单次查询失败不判死，下轮再查
        }
      }));
      setTasks(prev => prev.map(p => results.find(r => r.taskId === p.taskId && r.table === p.table) ?? p));
      if (results.every(r => isTerminal(r.status)) && pollTimer.current) {
        clearInterval(pollTimer.current);
        pollTimer.current = null;
      }
    };
    tick();
    pollTimer.current = setInterval(tick, 2000);
  };

  const handleRun = async () => {
    const list = Array.from(selectedNames);
    if (list.length === 0) {
      showToast('warning', t('dw.ingest.noTableSelected'));
      return;
    }
    setRunning(true);
    setTasks([]);
    try {
      if (isFs) {
        // 文件夹数据源：逐文件清单采集到近源层（非结构化）
        const docId = `ingest_${conn.id.replace(/[^a-zA-Z0-9_-]/g, '_')}_${Date.now()}`;
        const res = await collectFolderFiles({
          datasourceId: conn.id,
          docId,
          fileNames: list,
        });
        setTasks(list.map(f => ({ table: f, taskId: docId, status: res.failed > 0 && res.collected === 0 ? 'FAILED' : 'SUCCEEDED' })));
        if (res.failed > 0) {
          const firstErr = res.items?.find(i => i.status !== 'SUCCESS')?.error;
          showToast('error', t('dw.folder.collectPartial', { ok: res.collected, fail: res.failed })
            + (firstErr ? `（${firstErr}）` : ''));
        } else {
          showToast('success', t('dw.folder.collectSuccess', { count: res.collected }));
        }
        return;
      }
      // 结构数据源：POST /api/v1/datanet/ingest/run 走采集型管道到近源层
      const r = await runDataIngest(conn.id, list);
      if (!r || !r.submitted) {
        showToast('error', t('dw.ingest.runFailed'));
        return;
      }
      const initial = r.tasks.map(x => ({ table: x.table, taskId: x.taskId, status: x.status || 'SUBMITTED' }));
      setTasks(initial);
      showToast('info', t('dw.ingest.started', { count: initial.length }));
      // 轮询全部任务终态（不再只盯第一个）
      pollTasks(initial);
    } catch (e: unknown) {
      showToast('error', getErrorMessage(e) || t('dw.ingest.runFailed'));
    } finally {
      setRunning(false);
    }
  };

  const handleInitDatalake = async () => {
    setInitLoading(true);
    try {
      const r = await initDatalake();
      if (r) {
        setDatalake(prev => ({ ...prev, bucket: r.bucket ?? prev?.bucket, status: r.status ?? prev?.status, initialized: true }));
        setDatalakeError(null);
        showToast('success', t('dw.ingest.datalakeInitOk'));
      } else {
        showToast('error', t('dw.ingest.datalakeInitFailed'));
      }
    } catch (e: unknown) {
      logger.warn('[IngestSubPanel] initDatalake failed', e);
      showToast('error', getErrorMessage(e) || t('dw.ingest.datalakeInitFailed'));
    } finally {
      setInitLoading(false);
    }
  };

  const handleSaveSchedule = async () => {
    const list = Array.from(selectedNames);
    if (list.length === 0) {
      showToast('warning', t('dw.ingest.noTableSelected'));
      return;
    }
    setSavingSchedule(true);
    try {
      const target = scheduleEnabled ? cron : '';
      const r = await saveIngestSchedule(conn.id, list, target);
      if (r) {
        setScheduleEnabled(Boolean(r.enabled));
        showToast('success', target ? t('dw.ingest.scheduleSaved') : t('dw.ingest.scheduleDisabled'));
      } else {
        showToast('error', t('dw.ingest.scheduleFailed'));
      }
    } catch (e: unknown) {
      logger.warn('[IngestSubPanel] saveSchedule failed', e);
      showToast('error', getErrorMessage(e) || t('dw.ingest.scheduleFailed'));
    } finally {
      setSavingSchedule(false);
    }
  };

  return (
    <div className={`mt-3 pt-3 border-t border-dashed ${styles.cardBorder} space-y-2`}>
      {/* 区块标题 */}
      <div className={`flex items-center justify-between`}>
        <span className={`text-[10px] font-bold uppercase tracking-wider ${styles.accentText} flex items-center gap-1`}>
          <Database size={12} />
          {t('dw.ingest.title')}
        </span>
        {/* 数据湖状态（unknown/失败时明示，不永显 '...'） */}
        <div className={`flex items-center gap-1.5 text-[10px] ${styles.cardTextMuted}`}>
          {datalakeError ? (
            <>
              <span className={`inline-block w-1.5 h-1.5 rounded-full ${styles.dangerText} bg-current`} />
              <span className={styles.dangerText}>{t('dw.ingest.datalakeStatusUnknown')}</span>
              <button type="button" onClick={loadDatalakeStatus}
                className={`px-1.5 py-0.5 rounded border text-[9px] cursor-pointer ${styles.inputBorder} ${styles.cardTextMuted} hover:${styles.accentText}`}>
                {t('dw.ingest.retry')}
              </button>
            </>
          ) : (
            <>
              <span className={`inline-block w-1.5 h-1.5 rounded-full bg-current ${
                datalake?.status === 'UP' ? styles.successText : datalake?.status === 'DOWN' ? styles.dangerText : styles.warningText
              }`} />
              <span className="font-mono">
                {datalake?.bucket || 'ecos-datalake'}
              </span>
              <span>{datalake?.status || t('dw.ingest.datalakeStatusChecking')}</span>
            </>
          )}
          <button type="button"
            onClick={handleInitDatalake}
            disabled={initLoading || datalake?.status === 'UP'}
            className={`px-1.5 py-0.5 rounded border text-[9px] transition disabled:opacity-40 ${styles.inputBorder} ${styles.cardTextMuted} hover:${styles.accentText}`}
          >
            {initLoading ? '...' : t('dw.ingest.initDatalake')}
          </button>
        </div>
      </div>

      {/* 目标提示（MinIO 近源库默认） */}
      <p className={`text-[9px] ${styles.cardTextMuted}`}>
        {t('dw.ingest.targetHint')}
      </p>

      {/* 选中数量提示（实际复选框在目录列表区渲染） */}
      <div className={`flex items-center gap-2`}>
        <span className={`text-[10px] ${styles.cardText}`}>
          {t('dw.ingest.selectTables')}
          <span className={`ml-1 ${styles.cardTextMuted} font-mono`}>({selectedNames.size}/{tables.length || selectedNames.size})</span>
        </span>
        <button type="button" onClick={toggleAll} className={`text-[10px] ${styles.accentText} hover:underline cursor-pointer`}>
          {selectedNames.size > 0 && selectedNames.size >= tables.length && tables.length > 0
            ? t('dw.ingest.clearAll')
            : t('dw.ingest.selectAll')}
        </button>
      </div>

      {/* 操作按钮 */}
      <div className="flex items-center gap-2 flex-wrap">
        <button type="button"
          onClick={handleRun}
          disabled={running || selectedNames.size === 0}
          className={`px-2 py-1 text-[10px] font-semibold rounded transition flex items-center gap-1 ${styles.accentBg} ${styles.accentHover} ${styles.cardText} disabled:opacity-40`}
        >
          <Play size={11} className={running ? 'animate-pulse' : ''} />
          {running ? t('dw.ingest.running') : t('dw.ingest.runNow')}
        </button>

        {/* 定时开关 + cron */}
        <label className={`flex items-center gap-1 text-[10px] cursor-pointer ${styles.cardTextMuted}`}>
          <input type="checkbox" checked={scheduleEnabled} onChange={e => setScheduleEnabled(e.target.checked)} className="accent-current" />
          {t('dw.ingest.schedule')}
        </label>
        {scheduleEnabled && (
          <select
            value={cron}
            onChange={e => setCron(e.target.value)}
            className={`text-[10px] p-1 rounded border font-mono ${styles.inputBg} ${styles.inputBorder} ${styles.inputText}`}
          >
            {CRON_OPTIONS.map(o => <option key={o.value} value={o.value}>{t(o.labelKey)}</option>)}
          </select>
        )}
        {scheduleEnabled && (
          <button type="button"
            onClick={handleSaveSchedule}
            disabled={savingSchedule}
            className={`px-2 py-1 text-[10px] rounded border ${styles.cardBorder} ${styles.cardTextMuted} hover:${styles.accentText} transition disabled:opacity-40`}
          >
            {savingSchedule ? '...' : t('dw.ingest.saveSchedule')}
          </button>
        )}
      </div>

      {/* 采集任务进度 */}
      {tasks.length > 0 && (
        <div className={`space-y-1 p-2 rounded-lg ${styles.appBg} border ${styles.cardBorder}`}>
          {tasks.map(task => (
            <div key={task.taskId} className={`text-[10px] font-mono flex items-center gap-2`}>
              <span className={`font-bold ${
                task.status === 'SUCCEEDED' || task.status === 'COMPLETED' ? styles.successText
                  : task.status === 'FAILED' || task.status === 'error' ? styles.dangerText
                  : styles.warningText
              }`}>{task.status}</span>
              <span className={`${styles.cardTextMuted}`}>{task.table}</span>
              <span className="truncate text-[9px] opacity-70">{task.taskId.slice(0, 12)}</span>
              {task.progress != null && <span className="opacity-70">{task.progress}%</span>}
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default IngestSubPanel;
