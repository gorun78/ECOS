/* Extracted from DataWorkbenchLayout.tsx */
import React from 'react';
import { useLanguage } from '../../../components/LanguageContext';
import type { DataConnection, DataPipeline } from '../types';
import PipelineFlowEditor from '../PipelineFlowEditor';

interface PipelineBuilderTabProps {
  connections: DataConnection[];
  pipelines: DataPipeline[];
  computeEngine: 'doris' | 'memory';
  setComputeEngine: (v: 'doris' | 'memory') => void;
  showToast: (type: string, message: string) => void;
  pipelineBuilderOutput: any;
  setPipelineBuilderOutput: (v: any) => void;
}

const PipelineBuilderTab: React.FC<PipelineBuilderTabProps> = ({
  connections, pipelines, computeEngine, setComputeEngine, showToast,
  pipelineBuilderOutput, setPipelineBuilderOutput
}) => {
  const { t } = useLanguage();
  return (
  <div className="flex-1 flex flex-col min-h-0 overflow-hidden">
  <PipelineFlowEditor
    connections={connections}
    pipelines={pipelines}
    computeEngine={computeEngine}
    onEngineChange={setComputeEngine}
    showToast={showToast}
    onSave={async (pipeline: any) => {
      try {
        const { createPipeline, updatePipeline } = await import('../api');
        if (pipeline.id) {
          await updatePipeline(pipeline.id, { name: pipeline.name, description: pipeline.description });
          showToast('success', t('dw.pipeline.updated', { name: pipeline.name }));
        } else {
          await createPipeline(pipeline.name, pipeline.description);
          showToast('success', t('dw.pipeline.created', { name: pipeline.name }));
        }
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
  );
};

export default PipelineBuilderTab;
