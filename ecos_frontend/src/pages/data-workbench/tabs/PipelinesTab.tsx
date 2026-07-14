/* Extracted from DataWorkbenchLayout.tsx */
import React from 'react';
import type { DataPipeline, DataConnection } from '../types';
import PipelineFlowEditor from '../PipelineFlowEditor';

interface PipelinesTabProps {
  pipelines: DataPipeline[];
  editingPipelineId: string|null;
  setEditingPipelineId: (v: string|null) => void;
  setPipelines: (v: DataPipeline[]) => void;
  showToast: (type: string, message: string) => void;
  connections: DataConnection[];
  computeEngine: 'doris' | 'memory';
  setComputeEngine: (v: 'doris' | 'memory') => void;
  t: (key: string) => string;
}

const PipelinesTab: React.FC<PipelinesTabProps> = ({ pipelines, editingPipelineId, setEditingPipelineId, setPipelines, showToast, connections, computeEngine, setComputeEngine, t }) => (
  <>
    {editingPipelineId ? (
      <div className="flex-1 flex flex-col min-h-0 overflow-hidden">
        <PipelineFlowEditor
          connections={connections}
          pipelines={pipelines}
          editingPipeline={pipelines.find(p => p.id === editingPipelineId) || null}
          computeEngine={computeEngine}
          onEngineChange={setComputeEngine}
          showToast={showToast}
          onBack={() => setEditingPipelineId(null)}
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
    ) : (
      <div className="flex-1 flex flex-col overflow-hidden bg-white">
        <div className="px-6 py-3 border-b border-slate-200 flex items-center justify-between shrink-0">
          <div>
            <h3 className="text-sm font-bold text-slate-800">{t("dw.txt.fdcb6f")}</h3>
            <p className="text-xs text-slate-500 mt-0.5">
              共 {pipelines.length} 条定义 · 后端实时数据
            </p>
          </div>
          <button
            onClick={async () => {
              try {
                const { createPipeline } = await import('../api');
                const result = await createPipeline('新管道', '');
                showToast('success', '新管道已创建');
                if (result?.id) {
                  setEditingPipelineId(result.id);
                  const { fetchDataPipelines } = await import('../api');
                  const fresh = await fetchDataPipelines();
                  setPipelines(fresh);
                }
              } catch (e: any) {
                showToast('error', `创建失败: ${e.message}`);
              }
            }}
            className="px-3 py-1.5 bg-indigo-600 hover:bg-indigo-700 text-white font-bold rounded-lg text-xs flex items-center gap-1.5 shadow-xs transition-colors cursor-pointer"
          >
            <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4"/></svg>
            <span>新建管道</span>
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-6">
          <div className="bg-white border border-slate-200 rounded-xl overflow-hidden shadow-xs">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-slate-50 text-slate-400 text-[10px] font-extrabold uppercase tracking-wider border-b border-slate-200">
                  <th className="p-3">{t("dw.txt.5aab17")}</th>
                  <th className="p-3">{t("dw.txt.6f3f00")}</th>
                  <th className="p-3">{t("dw.txt.d21ba8")}</th>
                  <th className="p-3">{t("dw.txt.3bdd08")}</th>
                  <th className="p-3">{t("dw.txt.2bbb9a")}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-150">
                {pipelines.map((p, idx) => (
                  <tr key={p.id || idx} className="hover:bg-slate-50/50 transition-colors text-xs">
                    <td className="p-3 font-bold text-slate-800">{p.name}</td>
                    <td className="p-3 text-slate-500">{p.description || '-'}</td>
                    <td className="p-3">
                      <span className={`px-2 py-0.5 rounded-full font-bold text-[10px] ${
                        p.status === 'active' ? 'bg-emerald-50 text-emerald-700 border border-emerald-200' :
                        p.status === 'draft' ? 'bg-amber-50 text-amber-700 border border-amber-200' :
                        'bg-slate-100 text-slate-600'
                      }`}>
                        {p.status === 'active' ? '运行中' : p.status === 'draft' ? '草稿' : p.status}
                      </span>
                    </td>
                    <td className="p-3 text-slate-500 font-mono text-[10px]">{p.lastExecuted || '-'}</td>
                    <td className="p-3">
                      <div className="flex items-center gap-1.5">
                        <button
                          onClick={() => setEditingPipelineId(p.id)}
                          className="p-1.5 rounded-md hover:bg-indigo-50 text-indigo-600 font-bold text-[10px] cursor-pointer"
                        >
                          编辑
                        </button>
                        <button
                          onClick={async () => {
                            try {
                              const { executePipeline } = await import('../api');
                              await executePipeline(p.id);
                              showToast('success', `管道 [${p.name}] 已触发执行`);
                            } catch (e: any) {
                              showToast('error', `执行失败: ${e.message}`);
                            }
                          }}
                          className="p-1.5 rounded-md hover:bg-emerald-50 text-emerald-600 font-bold text-[10px] cursor-pointer"
                        >
                          执行
                        </button>
                        <button
                          onClick={async () => {
                            if (!confirm('确认删除？')) return;
                            try {
                              const { deletePipeline } = await import('../api');
                              await deletePipeline(p.id);
                              showToast('success', '已删除');
                              const { fetchDataPipelines } = await import('../api');
                              const fresh = await fetchDataPipelines();
                              setPipelines(fresh);
                            } catch (e: any) {
                              showToast('error', `删除失败: ${e.message}`);
                            }
                          }}
                          className="p-1.5 rounded-md hover:bg-rose-50 text-rose-500 font-bold text-[10px] cursor-pointer"
                        >
                          删除
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    )}
  </>
);

export default PipelinesTab;
