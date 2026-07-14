/* Extracted from DataWorkbenchLayout.tsx */
import React from 'react';
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
}) => (
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
          showToast('success', `管道 [${pipeline.name}] 已更新`);
        } else {
          await createPipeline(pipeline.name, pipeline.description);
          showToast('success', `管道 [${pipeline.name}] 已创建`);
        }
      } catch (e: any) {
        showToast('error', `保存失败: ${e.message}`);
      }
    }}
    onExecute={async (pipelineId: string) => {
      try {
        const { executePipeline } = await import('../api');
        const result = await executePipeline(pipelineId);
        showToast('success', result?.status === 'success' ? `管道执行成功` : `管道执行已触发`);
      } catch (e: any) {
        showToast('error', `执行失败: ${e.message}`);
      }
    }}
  />
</div>
);

export default PipelineBuilderTab;
