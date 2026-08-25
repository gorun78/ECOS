/* Extracted from DataWorkbenchLayout.tsx — PMO-3I: list+editor layout */
import React, { useState, useMemo } from 'react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { Plus, List, Loader2 } from 'lucide-react';
import type { DataConnection, DataPipeline, DataSyncTask } from '../types';
import PipelineFlowEditor from '../PipelineFlowEditor';

interface PipelineBuilderTabProps {
  connections: DataConnection[];
  pipelines: DataPipeline[];
  syncTasks: DataSyncTask[];
  computeEngine: 'doris' | 'memory';
  setComputeEngine: (v: 'doris' | 'memory') => void;
  showToast: (type: string, message: string) => void;
  pipelineBuilderOutput: any;
  setPipelineBuilderOutput: (v: any) => void;
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

  const handleNew = () => {
    setEditingPipelineId(null);
  };

  const handleSelect = (item: ListItem) => {
    if (item.type === 'pipeline') {
      setEditingPipelineId(item.id);
    } else {
      // For sync tasks, trigger sync execution
      triggerSync(item.id);
    }
  };

  return (
    <div className={`flex-1 flex min-h-0 overflow-hidden ${styles.appBg}`}>
      {/* Left: Pipeline list */}
      <div className={`w-60 border-r ${styles.cardBorder} ${styles.sidebarBg} flex flex-col shrink-0`}>
        <div className={`flex items-center justify-between px-3 py-2.5 border-b ${styles.cardBorder}`}>
          <span className={`text-[11px] font-bold uppercase tracking-wider ${styles.cardText}`}>
            {t('dw.pipeline.listTitle')}
          </span>
          <button onClick={handleNew}
            className={`flex items-center gap-1 px-2 py-1 text-[10px] rounded ${styles.accentBg} ${styles.accentText} hover:${styles.accentHover} transition-colors`}>
            <Plus size={11} /> {t('dw.pipeline.new')}
          </button>
        </div>
        {/* Filter tabs */}
        <div className={`flex border-b ${styles.cardBorder}`}>
          {(['all', 'pipeline', 'sync'] as const).map(f => (
            <button key={f} onClick={() => setFilter(f)}
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
            mergedList.map(item => (
              <button key={`${item.type}-${item.id}`} onClick={() => handleSelect(item)}
                className={`w-full flex items-center gap-2 px-3 py-2 text-left border-b ${styles.cardBorder} hover:${styles.sidebarHoverBg} transition-colors ${
                  editingPipelineId === item.id ? styles.sidebarActiveBg : ''
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
            ))
          )}
        </div>
      </div>

      {/* Right: Editor */}
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
        <PipelineFlowEditor
          connections={connections}
          pipelines={selectedPipeline ? [selectedPipeline] : pipelines}
          editingPipeline={selectedPipeline}
          computeEngine={computeEngine}
          onEngineChange={setComputeEngine}
          showToast={showToast}
          onSave={async (pipeline: any) => {
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
              if (pipeline.id) {
                await updatePipeline(pipeline.id, payload);
                showToast('success', t('dw.pipeline.updated', { name: pipeline.name }));
              } else {
                await createPipeline(payload);
                showToast('success', t('dw.pipeline.created', { name: pipeline.name }));
              }
              // Refresh the list so the new/updated pipeline appears.
              setPipelineBuilderOutput((prev: any) => ({ ...prev, refreshTick: (prev?.refreshTick || 0) + 1 }));
            } catch (e: any) {
              showToast('error', t('dw.pipeline.saveFailed', { error: e.message }));
            }
          }}
          onExecute={async (pipelineId: string) => {
            try {
              const { executePipeline } = await import('../api');
              const result = await executePipeline(pipelineId);
              showToast('success', result?.status === 'success' ? t('dw.pipeline.executeSuccess') : t('dw.pipeline.executeTriggered'));
            } catch (e: any) {
              showToast('error', t('dw.pipeline.executeFailed', { error: e.message }));
            }
          }}
        />
      </div>
    </div>
  );
};

export default PipelineBuilderTab;
