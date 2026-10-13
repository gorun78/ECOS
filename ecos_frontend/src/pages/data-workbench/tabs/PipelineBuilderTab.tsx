/* Extracted from DataWorkbenchLayout.tsx — PMO-3I: list+editor layout */
import React, { useState, useMemo, useEffect, useCallback } from 'react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import ConfirmDialog from '../../../components/common/ConfirmDialog';
import { Plus, List, Loader2, Play, RefreshCw } from 'lucide-react';
import type { DataConnection, DataPipeline, DataSyncTask } from '../types';
import type { PipelineData } from '../pipeline-editor/types';
import type { PbOutput } from '../hooks/useDataWorkbench';
import type { SetStateAction } from 'react';
import PipelineFlowEditor from '../PipelineFlowEditor';
import { getPipelineDefinition } from '../api';
import { getErrorMessage } from '../helpers';

interface PipelineBuilderTabProps {
  connections: DataConnection[];
  pipelines: DataPipeline[];
  syncTasks: DataSyncTask[];
  computeEngine: 'doris' | 'memory';
  setComputeEngine: (v: 'doris' | 'memory') => void;
  showToast: (type: string, message: string) => void;
  pipelineBuilderOutput: PbOutput | null;
  setPipelineBuilderOutput: (v: SetStateAction<PbOutput | null>) => void;
  editingPipelineId: string | null;
  setEditingPipelineId: (v: string | null) => void;
  triggerSync: (taskId: string) => void;
  t: (key: string, params?: Record<string, unknown>) => string;
}

type ListItem = {
  id: string;
  name: string;
  type: 'pipeline' | 'sync';
  status?: string;
};

const PipelineBuilderTab: React.FC<PipelineBuilderTabProps> = ({
  connections, pipelines, syncTasks, computeEngine, setComputeEngine, showToast,
  pipelineBuilderOutput, setPipelineBuilderOutput,
  editingPipelineId, setEditingPipelineId, triggerSync, t
}) => {
  const { styles } = useTheme();
  const [filter, setFilter] = useState<'all' | 'pipeline' | 'sync'>('all');
  // P0: 选中 ≠ 执行 —— 同步任务点击行只选中并展示详情，执行必须走显式按钮 + 确认。
  const [selectedSyncId, setSelectedSyncId] = useState<string | null>(null);
  const [runConfirmTask, setRunConfirmTask] = useState<DataSyncTask | null>(null);
  // 编辑器脏状态 + 切换挽留确认
  const [editorDirty, setEditorDirty] = useState(false);
  const [pendingAction, setPendingAction] = useState<{ action: () => void; title: string; message: string } | null>(null);

  // Wave 3 (lower) 缺陷①: list API only returns summaries (always nodes=[]);
  // the editor must fetch the full definition via the detail API and feed
  // the canvas with the real nodes/edges (list/detail pairing contract).
  const [detail, setDetail] = useState<DataPipeline | null>(null);
  // P1: 详情加载失败不再静默退回摘要 —— 显式错误态 + 重试。
  const [detailError, setDetailError] = useState<string | null>(null);
  const [detailRetryTick, setDetailRetryTick] = useState(0);
  const [detailLoading, setDetailLoading] = useState(false);

  useEffect(() => {
    if (!editingPipelineId) {
      setDetail(null);
      setDetailError(null);
      return;
    }
    let cancelled = false;
    setDetail(null);
    setDetailError(null);
    setDetailLoading(true);
    getPipelineDefinition(editingPipelineId)
      .then((d) => {
        if (cancelled) return;
        setDetailLoading(false);
        if (d) setDetail(d);
        else setDetailError(t('dw.pipeline.detailLoadFailed'));
      })
      .catch((e) => {
        if (cancelled) return;
        setDetailLoading(false);
        setDetailError(getErrorMessage(e) || t('dw.pipeline.detailLoadFailed'));
      });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editingPipelineId, detailRetryTick]);

  // Merge pipelines + syncTasks into a unified list
  const mergedList = useMemo<ListItem[]>(() => {
    const pipeItems: ListItem[] = pipelines.map(p => ({
      id: p.id, name: p.name, type: 'pipeline' as const, status: p.status,
    }));
    const syncItems: ListItem[] = syncTasks.map(s => ({
      id: s.id, name: s.name, type: 'sync' as const, status: s.status,
    }));
    const combined = [...pipeItems, ...syncItems];
    if (filter === 'pipeline') return combined.filter(i => i.type === 'pipeline');
    if (filter === 'sync') return combined.filter(i => i.type === 'sync');
    return combined;
  }, [pipelines, syncTasks, filter]);

  const selectedPipeline = pipelines.find(p => p.id === editingPipelineId) || null;
  // Prefer the freshly fetched detail (full nodes/edges) over the list summary.
  const canvasPipeline = detail ?? selectedPipeline;
  const selectedSyncTask = syncTasks.find(s => s.id === selectedSyncId) || null;

  // 脏挽留：有未保存修改时，切换/新建前先确认。
  const guardDirty = (action: () => void, labelForNew = false) => {
    if (!editorDirty) { action(); return; }
    setPendingAction({
      action,
      title: t('dw.pipeline.switchConfirmTitle'),
      message: labelForNew ? t('dw.pipeline.switchConfirmNew') : t('dw.pipeline.switchConfirmMessage'),
    });
  };

  const doSelect = (item: ListItem) => {
    guardDirty(() => {
      if (item.type === 'pipeline') {
        setSelectedSyncId(null);
        // Selecting the same item is a no-op (avoid re-fetching on click).
        setEditingPipelineId(item.id);
      } else {
        // 选中同步任务 = 仅展示详情，绝不触发执行。
        setEditingPipelineId(null);
        setSelectedSyncId(item.id);
      }
    });
  };

  const handleNew = () => {
    guardDirty(() => {
      setSelectedSyncId(null);
      setEditingPipelineId(null);
      setEditorDirty(false);
    }, true);
  };

  const confirmRunSync = () => {
    if (!runConfirmTask) return;
    const task = runConfirmTask;
    setRunConfirmTask(null);
    triggerSync(task.id);
  };

  return (
    <div className={`flex-1 flex min-h-0 overflow-hidden ${styles.appBg}`}>
      {/* Left: Pipeline list */}
      <div className={`w-60 border-r ${styles.cardBorder} ${styles.sidebarBg} flex flex-col shrink-0`}>
        <div className={`flex items-center justify-between px-3 py-2.5 border-b ${styles.cardBorder}`}>
          <span className={`text-[11px] font-bold uppercase tracking-wider ${styles.cardText}`}>
            {t('dw.pipeline.listTitle')}
          </span>
          <button type="button" onClick={handleNew}
            className={`flex items-center gap-1 px-2 py-1 text-[10px] rounded ${styles.accentBg} ${styles.accentText} hover:opacity-90 transition-colors`}>
            <Plus size={11} /> {t('dw.pipeline.new')}
          </button>
        </div>
        {/* Filter tabs */}
        <div className={`flex border-b ${styles.cardBorder}`}>
          {(['all', 'pipeline', 'sync'] as const).map(f => (
            <button type="button" key={f} onClick={() => setFilter(f)}
              className={`flex-1 px-2 py-1.5 text-[10px] font-medium transition-colors ${
                filter === f
                  ? `${styles.accentText} border-b-2 ${styles.accentBorder}`
                  : `${styles.cardTextMuted} hover:opacity-80`
              }`}>
              {t(`dw.pipeline.filter.${f}`)}
            </button>
          ))}
        </div>
        {/* List items */}
        <div className="flex-1 overflow-y-auto">
          {mergedList.length === 0 ? (
            <div className={`p-4 text-center text-[11px] ${styles.cardTextMuted}`}>
              {t('dw.pipeline.empty')}
            </div>
          ) : (
            mergedList.map(item => {
              const isSelected = item.type === 'pipeline'
                ? editingPipelineId === item.id
                : selectedSyncId === item.id;
              return (
                <button type="button" key={`${item.type}-${item.id}`} onClick={() => doSelect(item)}
                  className={`w-full flex items-center gap-2 px-3 py-2 text-left border-b ${styles.cardBorder} hover:opacity-90 transition-colors ${
                    isSelected ? styles.sidebarActiveBg : ''
                  }`}>
                  <span className={`inline-flex items-center justify-center w-5 h-5 rounded text-[9px] font-bold ${
                    item.type === 'sync' ? styles.infoBg + ' ' + styles.infoText : styles.successBg + ' ' + styles.successText
                  }`}>
                    {item.type === 'sync' ? 'S' : 'P'}
                  </span>
                  <span className={`flex-1 text-[11px] truncate ${styles.cardText}`}>{item.name}</span>
                  {item.status && (
                    <span className={`text-[9px] ${styles.cardTextMuted}`}>{item.status}</span>
                  )}
                </button>
              );
            })
          )}
        </div>
      </div>

      {/* Right: sync detail pane OR editor */}
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        {selectedSyncTask ? (
          <div className={`flex-1 overflow-y-auto p-4 ${styles.appBg}`}>
            <div className={`max-w-xl space-y-3 ${styles.cardBg} border ${styles.cardBorder} rounded-lg p-4`}>
              <div className="flex items-center justify-between">
                <h3 className={`text-sm font-bold ${styles.cardText}`}>{selectedSyncTask.name}</h3>
                <span className={`text-[10px] px-1.5 py-0.5 rounded-full ${styles.infoBg} ${styles.infoText}`}>
                  {selectedSyncTask.status}
                </span>
              </div>
              <dl className={`grid grid-cols-[120px_1fr] gap-x-3 gap-y-1.5 text-[11px] ${styles.cardText}`}>
                <dt className={styles.cardTextMuted}>{t('dw.sync.detail.id')}</dt><dd className="font-mono">{selectedSyncTask.id}</dd>
                <dt className={styles.cardTextMuted}>{t('dw.sync.detail.sourceTable')}</dt><dd>{selectedSyncTask.sourceTable || '—'}</dd>
                <dt className={styles.cardTextMuted}>{t('dw.sync.detail.targetTable')}</dt><dd>{selectedSyncTask.targetTable || '—'}</dd>
                <dt className={styles.cardTextMuted}>{t('dw.sync.detail.mode')}</dt><dd>{selectedSyncTask.syncMode || '—'}</dd>
                <dt className={styles.cardTextMuted}>{t('dw.sync.detail.schedule')}</dt><dd>{selectedSyncTask.schedule || selectedSyncTask.cronExpression || '—'}</dd>
                <dt className={styles.cardTextMuted}>{t('dw.sync.detail.lastRun')}</dt><dd>{selectedSyncTask.lastRunTime || selectedSyncTask.lastRun || '—'}</dd>
              </dl>
              {selectedSyncTask.errorMessage && (
                <div className={`text-[10px] rounded p-2 ${styles.dangerBg} ${styles.dangerText}`}>
                  {t('dw.sync.detail.lastError')}: {selectedSyncTask.errorMessage}
                </div>
              )}
              <div className={`flex items-center gap-2 pt-2 border-t ${styles.cardBorder}`}>
                <button
                  type="button"
                  disabled={selectedSyncTask.status === 'running'}
                  onClick={() => setRunConfirmTask(selectedSyncTask)}
                  className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium ${styles.accentBg} ${styles.accentText} hover:opacity-90 disabled:opacity-50 disabled:cursor-not-allowed transition-colors`}>
                  {selectedSyncTask.status === 'running'
                    ? <Loader2 size={13} className="animate-spin" />
                    : <Play size={13} />}
                  {selectedSyncTask.status === 'running' ? t('dw.sync.detail.running') : t('dw.sync.detail.run')}
                </button>
                <span className={`text-[10px] ${styles.cardTextMuted}`}>{t('dw.sync.detail.runHint')}</span>
              </div>
            </div>
          </div>
        ) : (
          <>
            {(detailLoading || detailError) && editingPipelineId && (
              <div className={`flex items-center gap-2 px-3 py-2 text-[11px] border-b ${
                detailError ? `${styles.dangerBg} ${styles.dangerText} ${styles.dangerBorder}` : `${styles.cardBorder} ${styles.cardTextMuted}`
              }`}>
                {detailLoading && <Loader2 size={12} className="animate-spin" />}
                {detailLoading && <span>{t('dw.pipeline.detailLoading')}</span>}
                {detailError && !detailLoading && (
                  <>
                    <span className="flex-1">{detailError}</span>
                    <button
                      type="button"
                      onClick={() => setDetailRetryTick(v => v + 1)}
                      className={`flex items-center gap-1 px-2 py-0.5 rounded border ${styles.cardBorder} hover:opacity-80`}
                    >
                      <RefreshCw size={11} /> {t('dw.pipeline.detailRetry')}
                    </button>
                  </>
                )}
              </div>
            )}
            <PipelineFlowEditor
              connections={connections}
              pipelines={canvasPipeline ? [canvasPipeline] : pipelines}
              editingPipeline={canvasPipeline}
              computeEngine={computeEngine}
              onEngineChange={setComputeEngine}
              showToast={showToast}
              onDirtyChange={setEditorDirty}
              onSave={async (pipeline: PipelineData): Promise<boolean> => {
                try {
                  const { createPipeline, updatePipeline } = await import('../api');
                  // PMO-3J T3: forward the full { name, nodes, edges } graph so the
                  // backend persists ecos_pipeline_node rows + dependency edges.
                  const payload = {
                    name: pipeline.name,
                    description: pipeline.description,
                    nodes: pipeline.nodes,
                    edges: pipeline.edges,
                  };
                  // api 层失败返回 null（内部吞错）—— 必须据此判定真实结果，
                  // 成功与否的 toast 由编辑器 await 本返回值后统一发出。
                  const saved = pipeline.id
                    ? await updatePipeline(pipeline.id, payload)
                    : await createPipeline(payload);
                  if (!saved) {
                    showToast('error', t('dw.pipeline.saveFailed', { error: t('dw.pipeline.saveRejected') }));
                    return false;
                  }
                  // Refresh the list so the new/updated pipeline appears.
                  setPipelineBuilderOutput((prev) => ({
                    ...(prev ?? { datasetPath: '', columns: [], rowCount: 0, lastCompiled: '', expressionsCount: 0 }),
                    refreshTick: (prev?.refreshTick || 0) + 1,
                  }));
                  return true;
                } catch (e) {
                  showToast('error', t('dw.pipeline.saveFailed', { error: getErrorMessage(e) }));
                  return false;
                }
              }}
              onExecute={async (pipelineId: string) => {
                // 返回真实触发结果；null ⇒ 后端不可达（编辑器按失败处理）。
                const { executePipeline } = await import('../api');
                return await executePipeline(pipelineId);
              }}
            />
          </>
        )}
      </div>

      {/* 执行同步确认（危险/写操作统一走 ConfirmDialog，禁 window.confirm） */}
      <ConfirmDialog
        visible={!!runConfirmTask}
        variant="warning"
        title={t('dw.sync.runConfirmTitle')}
        message={t('dw.sync.runConfirmMessage', { name: runConfirmTask?.name ?? '' })}
        confirmText={t('dw.sync.detail.run')}
        cancelText={t('common.cancel')}
        onConfirm={confirmRunSync}
        onCancel={() => setRunConfirmTask(null)}
      />

      {/* 切换/新建前的未保存挽留 */}
      <ConfirmDialog
        visible={!!pendingAction}
        variant="warning"
        title={pendingAction?.title ?? ''}
        message={pendingAction?.message ?? ''}
        confirmText={t('dw.pipeline.switchConfirmGo')}
        cancelText={t('common.cancel')}
        onConfirm={() => { const p = pendingAction; setPendingAction(null); p?.action(); }}
        onCancel={() => setPendingAction(null)}
      />
    </div>
  );
};

export default PipelineBuilderTab;
