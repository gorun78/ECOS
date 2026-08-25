/**
 * PipelineBuilderTab — merged list + editor dual-pane.
 *
 * Replaces the old separate `syncs` / `pipelines` / `pipeline-builder` tabs.
 * Left panel: unified list of pipelines + sync tasks (filterable by taskType).
 * Right panel: PipelineFlowEditor for the selected pipeline, or an empty hint.
 *
 * Backend API paths are NOT changed — sync tasks are fetched via the existing
 * fetchDataSyncTasks() and pipelines via fetchDataPipelines(); we only merge
 * the frontend presentation, distinguishing entries with a `taskType` badge.
 *
 * @license Apache-2.0
 */
import React, { useState, useMemo } from 'react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import type { DataConnection, DataPipeline, DataSyncTask } from '../types';
import PipelineFlowEditor from '../PipelineFlowEditor';
import LucideIcon from '../LucideIcon';

interface PipelineBuilderTabProps {
  connections: DataConnection[];
  pipelines: DataPipeline[];
  syncTasks: DataSyncTask[];
  computeEngine: 'doris' | 'memory';
  setComputeEngine: (v: 'doris' | 'memory') => void;
  showToast: (type: string, message: string) => void;
  /** callback to refresh pipelines after create/delete */
  onPipelinesChange: (p: DataPipeline[]) => void;
  /** callback to refresh sync tasks after create/trigger */
  onSyncTasksChange: (s: DataSyncTask[]) => void;
  /** trigger a sync task run */
  onTriggerSync: (taskId: string) => void;
}

type Filter = 'all' | 'sync' | 'transform';

/** A unified list item — either a real pipeline or a sync task surfaced as a pseudo-pipeline row. */
interface UnifiedRow {
  id: string;
  name: string;
  description: string;
  status: string;
  lastRun: string;
  taskType: 'SYNC' | 'TRANSFORM';
  /** underlying pipeline (undefined for sync-only rows) */
  pipeline?: DataPipeline;
  /** underlying sync task (undefined for pure pipeline rows) */
  syncTask?: DataSyncTask;
}

const PipelineBuilderTab: React.FC<PipelineBuilderTabProps> = ({
  connections, pipelines, syncTasks, computeEngine, setComputeEngine, showToast,
  onPipelinesChange, onSyncTasksChange, onTriggerSync,
}) => {
  const { t } = useLanguage();
  const { styles } = useTheme();
  const [filter, setFilter] = useState<Filter>('all');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);

  // ── Build the unified list ──
  const rows: UnifiedRow[] = useMemo(() => {
    const syncRows: UnifiedRow[] = syncTasks.map(s => ({
      id: s.id,
      name: s.name,
      description: s.sourceTable || '',
      status: s.status,
      lastRun: s.lastRunTime || s.lastRun || '',
      taskType: 'SYNC',
      syncTask: s,
    }));
    const pipeRows: UnifiedRow[] = pipelines.map(p => ({
      id: p.id,
      name: p.name,
      description: p.description || '',
      status: p.status,
      lastRun: p.lastExecuted || '',
      taskType: 'TRANSFORM',
      pipeline: p,
    }));
    const combined = [...pipeRows, ...syncRows];
    if (filter === 'sync') return combined.filter(r => r.taskType === 'SYNC');
    if (filter === 'transform') return combined.filter(r => r.taskType === 'TRANSFORM');
    return combined;
  }, [pipelines, syncTasks, filter]);

  const selectedPipeline = editing
    ? pipelines.find(p => p.id === selectedId) || null
    : null;

  // ── New pipeline ──
  const handleNewPipeline = async () => {
    try {
      const { createPipeline, fetchDataPipelines } = await import('../api');
      const result = await createPipeline(t('dw.newPipeline'), '');
      showToast('success', t('dw.newPipelineCreated'));
      if (result?.id) {
        const fresh = await fetchDataPipelines();
        onPipelinesChange(fresh);
        setSelectedId(result.id);
        setEditing(true);
      }
    } catch (e: any) {
      showToast('error', t('dw.createFailed', { msg: e.message }));
    }
  };

  // ── New sync (opens the existing AddSyncModal via parent) ──
  // Parent wires this through showToast + onSyncTasksChange; we reuse createSyncTask directly.
  const handleNewSync = async () => {
    // Minimal inline creation: prompt-style would violate i18n; instead emit a toast
    // guiding the user. Real form lives in the AddSyncModal owned by the Layout.
    showToast('info', t('dw.txt.5da13f'));
  };

  // ── Save pipeline ──
  const handleSave = async (pipeline: any) => {
    try {
      const { createPipeline, updatePipeline } = await import('../api');
      if (pipeline.id) {
        await updatePipeline(pipeline.id, { name: pipeline.name, description: pipeline.description });
        showToast('success', t('pipeline.updated', { name: pipeline.name }));
      } else {
        await createPipeline(pipeline.name, pipeline.description);
        showToast('success', t('pipeline.created', { name: pipeline.name }));
      }
    } catch (e: any) {
      showToast('error', t('pipeline.saveFailed', { error: e.message }));
    }
  };

  // ── Execute ──
  const handleExecute = async (pipelineId: string) => {
    try {
      const { executePipeline } = await import('../api');
      const result = await executePipeline(pipelineId);
      showToast('success', result?.status === 'success' ? t('pipeline.executeSuccess') : t('pipeline.executeTriggered'));
    } catch (e: any) {
      showToast('error', t('pipeline.executeFailed', { error: e.message }));
    }
  };

  // ── Delete pipeline ──
  const handleDelete = async (pipelineId: string) => {
    if (!confirm(t('dw.pb.confirmDelete'))) return;
    try {
      const { deletePipeline, fetchDataPipelines } = await import('../api');
      await deletePipeline(pipelineId);
      showToast('success', t('dw.pb.deleted'));
      const fresh = await fetchDataPipelines();
      onPipelinesChange(fresh);
      if (selectedId === pipelineId) { setSelectedId(null); setEditing(false); }
    } catch (e: any) {
      showToast('error', t('dw.pb.deleteFailed', { msg: e.message }));
    }
  };

  const FILTERS: { id: Filter; label: string }[] = [
    { id: 'all', label: t('dw.pb.filterAll') },
    { id: 'sync', label: t('dw.pb.filterSync') },
    { id: 'transform', label: t('dw.pb.filterTransform') },
  ];

  return (
    <div className="flex-1 flex overflow-hidden">
      {/* ── Left: unified list ── */}
      <div className={`w-96 ${styles.cardBg} border-r ${styles.cardBorder} flex flex-col overflow-hidden shrink-0`}>
        {/* Header */}
        <div className={`p-4 border-b ${styles.cardBorder} ${styles.appBg}/40`}>
          <div className="flex justify-between items-center mb-2">
            <h3 className={`text-xs font-bold ${styles.cardText}`}>{t('dw.pb.listTitle')}</h3>
            <div className="flex gap-1">
              <button onClick={handleNewSync}
                className={`p-1 rounded ${styles.cardBg} ${styles.cardBorder} ${styles.cardText} text-xs flex items-center gap-1 cursor-pointer font-medium`}
                title={t('dw.pb.newSync')}>
                <LucideIcon name="Import" size={12} />
              </button>
              <button onClick={handleNewPipeline}
                className={`p-1 rounded ${styles.accentBg} ${styles.cardText} ${styles.accentHover} text-xs flex items-center gap-1 cursor-pointer font-medium`}
                title={t('dw.pb.newPipeline')}>
                <LucideIcon name="Plus" size={12} />
              </button>
            </div>
          </div>
          <p className={`text-[10px] ${styles.cardTextMuted} mb-2`}>
            {t('dw.pb.listDesc', { count: rows.length })}
          </p>
          {/* Filter pills */}
          <div className="flex gap-1">
            {FILTERS.map(f => (
              <button key={f.id} onClick={() => setFilter(f.id)}
                className={`px-2 py-0.5 rounded-full text-[10px] font-semibold transition-colors cursor-pointer ${
                  filter === f.id
                    ? `${styles.accentBg} ${styles.cardText}`
                    : `${styles.cardBg} ${styles.cardBorder} ${styles.cardTextMuted} hover:opacity-80`
                }`}>
                {f.label}
              </button>
            ))}
          </div>
        </div>

        {/* List */}
        <div className="flex-1 overflow-y-auto p-2 space-y-1.5">
          {rows.length === 0 && (
            <div className={`p-4 text-xs ${styles.cardTextMuted} text-center`}>{t('dw.pb.emptyEditor')}</div>
          )}
          {rows.map(row => {
            const isSelected = selectedId === row.id;
            const isSync = row.taskType === 'SYNC';
            return (
              <button key={row.id} onClick={() => { setSelectedId(row.id); setEditing(!isSync); }}
                className={`w-full text-left p-3 rounded-lg border transition-all text-xs flex flex-col gap-1.5 cursor-pointer ${
                  isSelected
                    ? `${styles.badgeBg} ${styles.accentBorder} shadow-2xs`
                    : `${styles.cardBorder} hover:opacity-80`
                }`}>
                <div className="flex justify-between items-start gap-2">
                  <span className={`font-semibold ${styles.cardText} truncate`}>{row.name}</span>
                  <span className={`text-[9px] px-1.5 py-0.5 rounded-full font-semibold uppercase shrink-0 border ${
                    isSync
                      ? `${styles.infoBg} ${styles.accentText} ${styles.accentBorder}`
                      : `${styles.warningBg} ${styles.warningText} ${styles.warningBorder}`
                  }`}>
                    {isSync ? t('dw.pb.badgeSync') : t('dw.pb.badgeTransform')}
                  </span>
                </div>
                {row.description && (
                  <div className={`text-[10px] ${styles.cardTextMuted} font-mono truncate`}>{row.description}</div>
                )}
                <div className="flex justify-between items-center">
                  <span className={`text-[9px] px-1.5 py-0.5 rounded-full font-semibold ${
                    row.status === 'active' || row.status === 'success' ? `${styles.successBg} ${styles.successText}` :
                    row.status === 'failed' || row.status === 'error' ? `${styles.dangerBg} ${styles.dangerText}` :
                    row.status === 'running' ? `${styles.infoBg} ${styles.accentText} animate-pulse` :
                    `${styles.sidebarBg} ${styles.cardTextMuted}`
                  }`}>{row.status}</span>
                  {row.lastRun && (
                    <span className={`text-[9px] ${styles.cardTextMuted} font-mono`}>{row.lastRun}</span>
                  )}
                </div>
              </button>
            );
          })}
        </div>
      </div>

      {/* ── Right: editor or empty hint ── */}
      <div className="flex-1 flex flex-col min-h-0 overflow-hidden">
        {editing && selectedPipeline ? (
          <PipelineFlowEditor
            connections={connections}
            pipelines={pipelines}
            editingPipeline={selectedPipeline}
            computeEngine={computeEngine}
            onEngineChange={setComputeEngine}
            showToast={showToast}
            onBack={() => { setEditing(false); setSelectedId(null); }}
            onSave={handleSave}
            onExecute={handleExecute}
          />
        ) : (
          <div className={`flex-1 flex flex-col items-center justify-center p-6 ${styles.cardTextMuted}`}>
            <LucideIcon name="Workflow" size={32} className="mb-3 opacity-50" />
            <p className="text-xs text-center max-w-xs">{t('dw.pb.emptyEditor')}</p>
          </div>
        )}
      </div>
    </div>
  );
};

export default PipelineBuilderTab;
