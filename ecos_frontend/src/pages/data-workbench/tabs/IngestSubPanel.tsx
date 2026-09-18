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
import LucideIcon from '../LucideIcon';
import type { DataConnection } from '../types';
import {
  fetchDatalakeStatus, initDatalake,
  runDataIngest, fetchIngestStatus,
  saveIngestSchedule, fetchIngestSchedule,
} from '../api';

interface Props {
  conn: DataConnection;
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

const IngestSubPanel: React.FC<Props> = ({ conn, showToast }) => {
  const { styles } = useTheme();
  const { t } = useLanguage();

  // ── 数据湖状态 ──
  const [datalake, setDatalake] = useState<{ endpoint?: string; bucket?: string; status?: string; initialized?: boolean } | null>(null);
  const [initLoading, setInitLoading] = useState(false);

  // ── 表选择 ──
  const tables = useMemo(() => conn.tablesAvailable ?? [], [conn.tablesAvailable]);
  const [selectedTables, setSelectedTables] = useState<Set<string>>(new Set());

  // ── 即时采集 ──
  const [running, setRunning] = useState(false);
  const [tasks, setTasks] = useState<IngestTaskState[]>([]);
  const pollTimer = useRef<ReturnType<typeof setInterval> | null>(null);

  // ── 定时策略 ──
  const [scheduleEnabled, setScheduleEnabled] = useState(false);
  const [cron, setCron] = useState('0 0 * * *');
  const [savingSchedule, setSavingSchedule] = useState(false);

  // 加载数据湖状态 + 定时策略
  useEffect(() => {
    let cancelled = false;
    fetchDatalakeStatus().then(d => { if (!cancelled) setDatalake(d); });
    fetchIngestSchedule(conn.id).then(s => {
      if (cancelled || !s) return;
      setScheduleEnabled(Boolean(s.enabled));
      if (s.cron) setCron(s.cron);
      if (Array.isArray(s.tables) && s.tables.length > 0) {
        setSelectedTables(new Set(s.tables));
      }
    });
    return () => { cancelled = true; };
  }, [conn.id]);

  // 卸载时清理轮询
  useEffect(() => () => { if (pollTimer.current) clearInterval(pollTimer.current); }, []);

  const toggleTable = (name: string) => {
    setSelectedTables(prev => {
      const next = new Set(prev);
      if (next.has(name)) next.delete(name); else next.add(name);
      return next;
    });
  };

  const toggleAll = () => {
    setSelectedTables(prev => (prev.size === tables.length ? new Set() : new Set(tables.map(tb => (typeof tb === 'string' ? tb : (tb.name ?? ''))))));
  };

  const pollTask = (taskId: string, table: string) => {
    // 每 2s 轮询一次任务状态，SUCCEEDED/FAILED/error 停止
    const poll = async () => {
      const st = await fetchIngestStatus(taskId);
      if (!st) return;
      const terminal = st.status === 'SUCCEEDED' || st.status === 'FAILED' || st.status === 'error' || st.status === 'COMPLETED';
      setTasks(prev => prev.map(x => (x.taskId === taskId ? {
        ...x, status: st.status || x.status, progress: st.progress ?? x.progress, errorMessage: st.errorMessage ?? x.errorMessage,
      } : x)));
      if (terminal && pollTimer.current) clearInterval(pollTimer.current);
    };
    if (pollTimer.current) clearInterval(pollTimer.current);
    pollTimer.current = setInterval(poll, 2000);
  };

  const handleRun = async () => {
    const list = Array.from(selectedTables);
    if (list.length === 0) {
      showToast('warning', t('dw.ingest.noTableSelected') || '请先勾选要采集的表');
      return;
    }
    setRunning(true);
    setTasks([]);
    try {
      const r = await runDataIngest(conn.id, list);
      if (!r || !r.submitted) {
        showToast('error', t('dw.ingest.runFailed') || '采集任务提交失败');
        return;
      }
      const initial = r.tasks.map(x => ({ table: x.table, taskId: x.taskId, status: x.status || 'SUBMITTED' }));
      setTasks(initial);
      showToast('info', t('dw.ingest.started') || `已提交 ${initial.length} 个采集任务`);
      // 轮询第一个任务的状态（同批次同步执行，代表整体）
      if (initial.length > 0) pollTask(initial[0].taskId, initial[0].table);
    } catch (e) {
      showToast('error', (e as Error).message || '采集任务提交失败');
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
        showToast('success', t('dw.ingest.datalakeInitOk') || '数据湖初始化成功');
      } else {
        showToast('error', t('dw.ingest.datalakeInitFailed') || '数据湖初始化失败');
      }
    } finally {
      setInitLoading(false);
    }
  };

  const handleSaveSchedule = async () => {
    const list = Array.from(selectedTables);
    if (list.length === 0) {
      showToast('warning', t('dw.ingest.noTableSelected') || '请先勾选要采集的表');
      return;
    }
    setSavingSchedule(true);
    try {
      const target = scheduleEnabled ? cron : '';
      const r = await saveIngestSchedule(conn.id, list, target);
      if (r) {
        setScheduleEnabled(Boolean(r.enabled));
        showToast('success', target ? t('dw.ingest.scheduleSaved') || '定时采集已启用' : t('dw.ingest.scheduleDisabled') || '定时采集已停用');
      } else {
        showToast('error', t('dw.ingest.scheduleFailed') || '定时策略保存失败');
      }
    } finally {
      setSavingSchedule(false);
    }
  };

  return (
    <div className={`mt-3 pt-3 border-t border-dashed ${styles.cardBorder} space-y-2`}>
      {/* 区块标题 */}
      <div className={`flex items-center justify-between`}>
        <span className={`text-[10px] font-bold uppercase tracking-wider ${styles.accentText} flex items-center gap-1`}>
          <LucideIcon name="Database" size={12} />
          {t('dw.ingest.title') || '数据采集'}
        </span>
        {/* 数据湖状态 */}
        <div className={`flex items-center gap-1.5 text-[10px] ${styles.cardTextMuted}`}>
          <span className={`inline-block w-1.5 h-1.5 rounded-full ${
            datalake?.status === 'UP' ? 'bg-emerald-500' : datalake?.status === 'DOWN' ? 'bg-red-500' : 'bg-amber-400'
          }`} />
          <span className="font-mono">
            {datalake?.bucket || 'ecos-datalake'}
          </span>
          <span>{datalake?.status || '...'}</span>
          <button
            onClick={handleInitDatalake}
            disabled={initLoading || datalake?.status === 'UP'}
            className={`px-1.5 py-0.5 rounded border text-[9px] transition disabled:opacity-40 ${styles.inputBorder} ${styles.cardTextMuted} hover:${styles.accentText}`}
          >
            {initLoading ? '...' : (t('dw.ingest.initDatalake') || '初始化')}
          </button>
        </div>
      </div>

      {/* 目标提示（MinIO 近源库默认） */}
      <p className={`text-[9px] ${styles.cardTextMuted}`}>
        {t('dw.ingest.targetHint') || '默认写入数据湖 MinIO 近源库（datalake/数据源/表_时间戳.csv），目标可通过管道编辑器自定义。'}
      </p>

      {/* 表多选 */}
      <div className={`space-y-1`}>
        <div className={`flex items-center justify-between`}>
          <span className={`text-[10px] ${styles.cardText}`}>
            {t('dw.ingest.selectTables') || '选择采集表'} ({selectedTables.size}/{tables.length})
          </span>
          <button onClick={toggleAll} className={`text-[10px] ${styles.accentText} hover:underline cursor-pointer`}>
            {selectedTables.size === tables.length && tables.length > 0
              ? (t('dw.ingest.clearAll') || '清空')
              : (t('dw.ingest.selectAll') || '全选')}
          </button>
        </div>
        {tables.length === 0 ? (
          <div className={`text-[10px] ${styles.cardTextMuted} p-2 rounded border border-dashed ${styles.cardBorder}`}>
            {t('dw.ingest.noTablesHint') || '暂无表目录：请先执行上方「元数据采集」，或点击「立即采集」拉取后选择。'}
          </div>
        ) : (
          <div className={`max-h-32 overflow-y-auto space-y-0.5 p-1 rounded border ${styles.cardBorder}`}>
            {tables.slice(0, 50).map((tb, i) => {
              const name = typeof tb === 'string' ? tb : ((tb as { name?: string }).name ?? `table_${i}`);
              return (
                <label key={name} className={`flex items-center gap-1.5 text-[10px] cursor-pointer hover:${styles.appBg} rounded px-1 py-0.5`}>
                  <input type="checkbox" checked={selectedTables.has(name)} onChange={() => toggleTable(name)} className="accent-indigo-500" />
                  <span className="font-mono truncate">{name}</span>
                </label>
              );
            })}
          </div>
        )}
      </div>

      {/* 操作按钮 */}
      <div className="flex items-center gap-2 flex-wrap">
        <button
          onClick={handleRun}
          disabled={running || selectedTables.size === 0}
          className={`px-2 py-1 text-[10px] font-semibold rounded transition flex items-center gap-1 ${styles.accentBg} ${styles.accentHover} ${styles.cardText} disabled:opacity-40`}
        >
          <LucideIcon name="Play" size={11} className={running ? 'animate-pulse' : ''} />
          {running ? (t('dw.ingest.running') || '采集中...') : (t('dw.ingest.runNow') || '立即采集')}
        </button>

        {/* 定时开关 + cron */}
        <label className={`flex items-center gap-1 text-[10px] cursor-pointer ${styles.cardTextMuted}`}>
          <input type="checkbox" checked={scheduleEnabled} onChange={e => setScheduleEnabled(e.target.checked)} className="accent-indigo-500" />
          {t('dw.ingest.schedule') || '定时采集'}
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
          <button
            onClick={handleSaveSchedule}
            disabled={savingSchedule}
            className={`px-2 py-1 text-[10px] rounded border ${styles.cardBorder} ${styles.cardTextMuted} hover:${styles.accentText} transition disabled:opacity-40`}
          >
            {savingSchedule ? '...' : (t('dw.ingest.saveSchedule') || '保存定时')}
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
