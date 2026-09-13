/**
 * ExportTasksView — 本体导出任务列表视图（T10）
 *
 * 嵌入 OntologyWorkbenchLayout 的导出 Modal，闭环后端
 * OntologyExportController 既有端点（T16-4 强类型）：
 *   GET    /api/v1/ontology/export            → listExports   任务列表（摘要）
 *   GET    /api/v1/ontology/export/{id}       → getExport     任务详情（轮询状态用）
 *   GET    /api/v1/ontology/export/{id}/download → download   取 payload 落地
 *   DELETE /api/v1/ontology/export/{id}       → deleteExport  删除已结束任务
 *
 * 任务状态枚举与后端 OntologyExportTaskVO.status 同源：
 *   PENDING / RUNNING / COMPLETED / FAILED
 * 存在进行中任务时以 setInterval 轮询 getExport，组件不可见时清除定时器。
 *
 * @license Apache-2.0
 */
import React, { useCallback, useEffect, useState } from 'react';
import { Clock, Loader2, Trash2, RefreshCw, AlertTriangle, CheckCircle2 } from 'lucide-react';
import { useLanguage } from '../../components/LanguageContext';
import { useTheme } from '../../components/ThemeContext';
import {
  fetchExportTasks,
  fetchExportTask,
  deleteExportTask,
  downloadExportTask,
  DEFAULT_ONTOLOGY_ID,
} from '../../services/ontologyApi';
import type { ExportTaskSummary } from '../../types/ontology';

interface ExportTasksViewProps {
  /** 父 Modal 是否可见：不可见时停止轮询 */
  visible: boolean;
  /** 新建导出任务后自增，触发列表刷新 */
  refreshSignal: number;
  /** 复用父级 toast */
  onToast: (type: 'success' | 'info' | 'error', message: string) => void;
}

/** 进行中状态（需要轮询） */
const ACTIVE_STATUSES: ReadonlySet<string> = new Set(['PENDING', 'RUNNING']);

/** 轮询间隔（毫秒） */
const POLL_INTERVAL_MS = 3000;

export default function ExportTasksView({ visible, refreshSignal, onToast }: ExportTasksViewProps) {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const [tasks, setTasks] = useState<ExportTaskSummary[]>([]);
  const [loading, setLoading] = useState<boolean>(false);
  const [busyIds, setBusyIds] = useState<Set<string>>(new Set());

  /** 拉取导出任务列表（按 createdAt 倒序） */
  const loadTasks = useCallback(async () => {
    setLoading(true);
    try {
      const list = await fetchExportTasks({ ontologyId: DEFAULT_ONTOLOGY_ID });
      const sorted = [...(list || [])].sort((a, b) =>
        String(b.createdAt || '').localeCompare(String(a.createdAt || ''))
      );
      setTasks(sorted);
    } catch (e: any) {
      onToast('error', t('ow.exportTask.loadError'));
    } finally {
      setLoading(false);
    }
  }, [onToast, t]);

  // 打开 Modal / 新建任务后刷新列表
  useEffect(() => {
    if (visible) loadTasks();
  }, [visible, refreshSignal, loadTasks]);

  /** 进行中任务 id 集合（轮询依据；无进行中任务则不建定时器） */
  const activeIds = tasks
    .filter(tk => ACTIVE_STATUSES.has(tk.status))
    .map(tk => tk.id)
    .join(',');

  // 有进行中任务 → setInterval 轮询 getExport 状态；离开/完成/不可见即 cleanup
  useEffect(() => {
    if (!visible || activeIds === '') return;
    const pollingIds = activeIds.split(',');
    const timer = setInterval(async () => {
      try {
        const details: ExportTaskSummary[] = await Promise.all(
          pollingIds.map(id => fetchExportTask(id))
        );
        setTasks(prev =>
          prev.map(tk => {
            const d = details.find((x: ExportTaskSummary) => x && x.id === tk.id);
            return d && d.status !== tk.status
              ? { ...tk, status: d.status, objectCount: d.objectCount ?? tk.objectCount }
              : tk;
          })
        );
      } catch {
        // 单次轮询失败不中断，下一周期重试
      }
    }, POLL_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [visible, activeIds]);

  /** 下载 COMPLETED 任务的 payload 落地 */
  const handleDownload = async (task: ExportTaskSummary) => {
    setBusyIds(prev => new Set(prev).add(task.id));
    try {
      const name = await downloadExportTask(task.id, task.format);
      onToast('success', t('ow.exportTask.downloadSuccess').replace('{name}', name));
    } catch (e: any) {
      onToast('error', t('ow.exportTask.downloadError'));
    } finally {
      setBusyIds(prev => {
        const next = new Set(prev);
        next.delete(task.id);
        return next;
      });
    }
  };

  /** 删除已结束任务 */
  const handleDelete = async (task: ExportTaskSummary) => {
    setBusyIds(prev => new Set(prev).add(task.id));
    try {
      await deleteExportTask(task.id);
      setTasks(prev => prev.filter(x => x.id !== task.id));
      onToast('success', t('ow.exportTask.deleteSuccess'));
    } catch (e: any) {
      onToast('error', t('ow.exportTask.deleteError'));
    } finally {
      setBusyIds(prev => {
        const next = new Set(prev);
        next.delete(task.id);
        return next;
      });
    }
  };

  /** 状态徽章 — 颜色走主题语义 token，文案走 i18n */
  const renderStatusBadge = (task: ExportTaskSummary) => {
    const isCompleted = task.status === 'COMPLETED';
    const isFailed = task.status === 'FAILED';
    const tone = isCompleted
      ? { bg: styles.successBg, text: styles.successText, Icon: CheckCircle2 }
      : isFailed
        ? { bg: styles.dangerBg, text: styles.dangerText, Icon: AlertTriangle }
        : { bg: styles.infoBg, text: styles.infoText, Icon: task.status === 'RUNNING' ? Loader2 : Clock };
    return (
      <span className={`inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-semibold ${tone.bg} ${tone.text}`}>
        <tone.Icon size={11} className={task.status === 'RUNNING' ? 'animate-spin' : ''} />
        {t(`ow.exportTask.status.${task.status}`)}
      </span>
    );
  };

  if (!visible) return null;

  return (
    <div className="mt-3 pt-3 border-t border-dashed border-current">
      <div className={`flex items-center justify-between mb-2 ${styles.cardText}`}>
        <h4 className="text-[11px] font-bold">{t('ow.exportTask.title')}</h4>
        <button
          onClick={loadTasks}
          className={`flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-semibold ${styles.inputBg} ${styles.inputText} ${styles.sidebarHoverBg}`}
        >
          <RefreshCw size={10} />
          {t('ow.exportTask.refresh')}
        </button>
      </div>

      {loading && tasks.length === 0 ? (
        <div className={`flex items-center justify-center gap-2 py-5 text-[11px] ${styles.muted}`}>
          <Loader2 size={14} className="animate-spin" />
          {t('ow.exportTask.loading')}
        </div>
      ) : tasks.length === 0 ? (
        <div className={`flex flex-col items-center py-5 text-[11px] ${styles.muted}`}>
          <Clock size={18} className="mb-1" />
          {t('ow.exportTask.empty')}
        </div>
      ) : (
        <div className="space-y-1.5 max-h-52 overflow-y-auto pr-1">
          {tasks.map(task => {
            const busy = busyIds.has(task.id);
            const isCompleted = task.status === 'COMPLETED';
            const isEnded = isCompleted || task.status === 'FAILED';
            return (
              <div
                key={task.id}
                className={`flex items-center justify-between gap-2 p-2 rounded border ${styles.cardBorder} ${styles.cardBg}`}
              >
                <div className="flex items-center gap-2 min-w-0 flex-1">
                  {renderStatusBadge(task)}
                  <div className="min-w-0">
                    <div className={`text-[10px] font-mono truncate ${styles.cardTextMuted}`}>{task.id}</div>
                    <div className={`text-[10px] ${styles.muted}`}>
                      {task.format} · {t(`ow.export.scope.${task.scope}`)} · {task.createdAt?.slice(0, 19).replace('T', ' ')}
                      {task.objectCount !== undefined && (
                        <span className="ml-1">
                          · {t('ow.exportTask.objectCount')}: {task.objectCount}
                        </span>
                      )}
                    </div>
                  </div>
                </div>
                <div className="flex items-center gap-1 shrink-0">
                  {isCompleted && (
                    <button
                      onClick={() => handleDownload(task)}
                      disabled={busy}
                      className={`px-2 py-0.5 rounded text-[10px] font-semibold ${styles.accentBg} text-white ${styles.accentHover} disabled:opacity-50`}
                    >
                      {t('ow.exportTask.download')}
                    </button>
                  )}
                  {isEnded && (
                    <button
                      onClick={() => handleDelete(task)}
                      disabled={busy}
                      className={`px-2 py-0.5 rounded text-[10px] font-semibold ${styles.dangerBg} ${styles.dangerText} disabled:opacity-50`}
                    >
                      <Trash2 size={10} />
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
