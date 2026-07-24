/* Extracted from DataWorkbenchLayout.tsx */
import React from 'react';
import type { DataPipeline, DataConnection } from '../types';
import PipelineFlowEditor from '../PipelineFlowEditor';
import { useTheme } from "../../../components/ThemeContext";


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

const PipelinesTab: React.FC<PipelinesTabProps> = ({ pipelines, editingPipelineId, setEditingPipelineId, setPipelines, showToast, connections, computeEngine, setComputeEngine, t }) => {
  const { styles } = useTheme();
  return (
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
                showToast('success', t("dw.pipelineUpdated").replace('{name}', pipeline.name));
              } else {
                await createPipeline(pipeline.name, pipeline.description);
                showToast('success', t("dw.pipelineCreated").replace('{name}', pipeline.name));
              }
            } catch (e: any) {
              showToast('error', t("dw.saveFailed").replace('{msg}', e.message));
            }
          }}
          onExecute={async (pipelineId: string) => {
            try {
              const { executePipeline } = await import('../api');
              const result = await executePipeline(pipelineId);
              showToast('success', result?.status === 'success' ? t("dw.pipelineExecSuccess") : t("dw.pipelineExecTriggered"));
            } catch (e: any) {
              showToast('error', `执行失败: ${e.message}`);
            }
          }}
        />
      </div>
    ) : (
      <div className={`flex-1 flex flex-col overflow-hidden ${styles.cardBg}`}>
        <div className={`px-6 py-3 border-b ${styles.cardBorder} flex items-center justify-between shrink-0`}>
          <div>
            <h3 className={`text-sm font-bold ${styles.cardText}`}>{t("dw.txt.fdcb6f")}</h3>
            <p className={`text-xs ${styles.cardTextMuted} mt-0.5`}>
              {t("dw.pipelineCount").replace('{count}', String(pipelines.length))}
            </p>
          </div>
          <button
            onClick={async () => {
              try {
                const { createPipeline } = await import('../api');
                const result = await createPipeline(t("dw.newPipeline"), '');
                showToast('success', t("dw.newPipelineCreated"));
                if (result?.id) {
                  setEditingPipelineId(result.id);
                  const { fetchDataPipelines } = await import('../api');
                  const fresh = await fetchDataPipelines();
                  setPipelines(fresh);
                }
              } catch (e: any) {
                showToast('error', t("dw.createFailed").replace('{msg}', e.message));
              }
            }}
            className={`px-3 py-1.5 ${styles.accentBg} ${styles.accentHover} text-white font-bold rounded-lg text-xs flex items-center gap-1.5 shadow-xs transition-colors cursor-pointer`}
          >
            <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4"/></svg>
            <span>{t("dw.newPipelineBtn")}</span>
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-6">
          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl overflow-hidden shadow-xs`}>
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className={`${styles.appBg} ${styles.cardTextMuted} text-[10px] font-extrabold uppercase tracking-wider border-b ${styles.cardBorder}`}>
                  <th className="p-3">{t("dw.txt.5aab17")}</th>
                  <th className="p-3">{t("dw.txt.6f3f00")}</th>
                  <th className="p-3">{t("dw.txt.d21ba8")}</th>
                  <th className="p-3">{t("dw.txt.3bdd08")}</th>
                  <th className="p-3">{t("dw.txt.2bbb9a")}</th>
                </tr>
              </thead>
              <tbody className={`divide-y ${styles.cardBorder}`}>
                {pipelines.map((p, idx) => (
                  <tr key={p.id || idx} className={`hover:${styles.appBg}/50 transition-colors text-xs`}>
                    <td className={`p-3 font-bold ${styles.cardText}`}>{p.name}</td>
                    <td className={`p-3 ${styles.cardTextMuted}`}>{p.description || '-'}</td>
                    <td className="p-3">
                      <span className={`px-2 py-0.5 rounded-full font-bold text-[10px] ${
                        p.status === 'active' ? 'bg-emerald-50 text-emerald-700 border border-emerald-200' :
                        p.status === 'draft' ? 'bg-amber-50 text-amber-700 border border-amber-200' :
                        '${styles.sidebarBg} ${styles.cardTextMuted}'
                      }`}>
                        {p.status === 'active' ? t("dw.statusActive") : p.status === 'draft' ? t("dw.statusDraft") : p.status}
                      </span>
                    </td>
                    <td className={`p-3 ${styles.cardTextMuted} font-mono text-[10px]`}>{p.lastExecuted || '-'}</td>
                    <td className="p-3">
                      <div className="flex items-center gap-1.5">
                        <button
                          onClick={() => setEditingPipelineId(p.id)}
                          className={`p-1.5 rounded-md hover:${styles.appBg} ${styles.accentText} font-bold text-[10px] cursor-pointer`}
                        >
                          {t("dw.editBtn")}
                        </button>
                        <button
                          onClick={async () => {
                            try {
                              const { executePipeline } = await import('../api');
                              await executePipeline(p.id);
                              showToast('success', t("dw.pipelineTriggered").replace('{name}', p.name));
                            } catch (e: any) {
                              showToast('error', t("dw.execFailedMsg").replace('{msg}', e.message));
                            }
                          }}
                          className={`p-1.5 rounded-md hover:${styles.appBg} font-bold text-[10px] cursor-pointer ${styles.accentText}`}
                        >
                          {t("dw.runBtn")}
                        </button>
                        <button
                          onClick={async () => {
                            if (!confirm(t("dw.confirmDelete"))) return;
                            try {
                              const { deletePipeline } = await import('../api');
                              await deletePipeline(p.id);
                              showToast('success', t("dw.deleted"));
                              const { fetchDataPipelines } = await import('../api');
                              const fresh = await fetchDataPipelines();
                              setPipelines(fresh);
                            } catch (e: any) {
                              showToast('error', t("dw.deleteFailed").replace('{msg}', e.message));
                            }
                          }}
                          className={`p-1.5 rounded-md hover:${styles.appBg} text-rose-500 font-bold text-[10px] cursor-pointer`}
                        >
                          {t("dw.deleteBtn")}
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
};

export default PipelinesTab;
