import React, { useState } from 'react';
import { ArrowRight, Loader, GitBranch, Radar } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { cognitiveEngineApi } from '../../../services/cognitiveEngineApi';
import { showToastGlobal } from '../../../components/common/Toast';
import type { ReasoningMode } from './types';

interface CausalNode { id: string; label: string; labelKey: string; pos: string; }

const NODES: CausalNode[] = [
  { id: 'center', label: '项目毛利下降', labelKey: 'causal.node.center', pos: 'left-[38%] top-[40%]' },
  { id: 'cost', label: '采购成本↑', labelKey: 'causal.node.cost', pos: 'left-[10%] top-[10%]' },
  { id: 'change', label: '需求变更', labelKey: 'causal.node.change', pos: 'left-[8%] top-[70%]' },
  { id: 'delay', label: '工期延长', labelKey: 'causal.node.delay', pos: 'left-[60%] top-[12%]' },
  { id: 'labor', label: '人力成本↑', labelKey: 'causal.node.labor', pos: 'left-[60%] top-[72%]' },
];

const EDGE_PAIRS: [string, string][] = [
  ['cost', 'center'], ['change', 'center'], ['delay', 'center'], ['labor', 'center'],
];

const TRACE_NODES = ['D', 'I', 'K', 'Evidence', 'Hypothesis', 'Belief', 'C', 'W'];

interface CausalAnalysisProps {
  /** Switch the workbench to the scenario-simulation sub-page (in-page cross-link). */
  onGoScenario?: () => void;
}

export default function causalAnalysis({ onGoScenario }: CausalAnalysisProps) {
  const { t } = useLanguage();
  const [selectedNode, setSelectedNode] = useState<string | null>(null);
  const [reRunning, setReRunning] = useState(false);
  const [reasoningMode] = useState<ReasoningMode>('HYBRID');

  const handleReRun = async () => {
    if (reRunning) return;
    setReRunning(true);
    try {
      await cognitiveEngineApi.runDiagnosis({ objectId: 'project-margin', question: '项目毛利下降原因', reasoningMode });
      showToastGlobal('success', t('causal.reRunOk', '重新推理完成'));
    } catch (e) {
      showToastGlobal('error', `${t('causal.reRunFailed', '重新推理失败')}: ${(e as Error)?.message ?? e}`);
    } finally {
      setReRunning(false);
    }
  };

  const selectedNodeData = NODES.find(n => n.id === selectedNode);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-lg font-semibold text-slate-900 dark:text-slate-100">{t('causal.title', '因果分析')}</h1>
        <div className="flex gap-2">
          <button onClick={handleReRun} disabled={reRunning}
            className="px-3 py-1.5 rounded-md text-sm font-medium bg-blue-600 text-white hover:bg-blue-700 dark:bg-blue-500 dark:hover:bg-blue-600 disabled:opacity-50">
            {reRunning ? <Loader className="w-3.5 h-3.5 inline mr-1 animate-spin" /> : null}
            {t('causal.reRun', '重新推理')}
          </button>
          <button onClick={() => onGoScenario?.()}
            className="px-3 py-1.5 rounded-md text-sm font-medium bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-slate-700 dark:text-slate-200 hover:bg-slate-50 dark:hover:bg-slate-700">
            {t('causal.toScenario', '进入情景推演 →')}
          </button>
        </div>
      </div>

      {/* Notice bar */}
      <div className="flex items-center gap-3 bg-blue-50 dark:bg-blue-950 border border-blue-200 dark:border-blue-800 rounded-lg px-4 py-2.5">
        <Radar className="w-4 h-4 text-blue-500 flex-shrink-0" />
        <span className="text-sm text-blue-800 dark:text-blue-200">{t('causal.notice', '当前问题')}: 项目毛利为何下降？</span>
        <span className="ml-auto text-xs px-2 py-0.5 rounded-full bg-blue-600 text-white">{reasoningMode}</span>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* Causal graph */}
        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg p-4 relative">
          <h2 className="text-sm font-medium text-slate-900 dark:text-slate-100 mb-2">{t('causal.graph', '因果图')}</h2>
          <div className="relative h-[400px]">
            {/* Edges */}
            {EDGE_PAIRS.map(([from, to], i) => (
              <div key={i} className="absolute w-px h-full bg-slate-300 dark:bg-slate-600"
                style={{
                  left: from === 'cost' ? '30%' : from === 'change' ? '28%' : from === 'delay' ? '70%' : '72%',
                  top: from === 'cost' || from === 'delay' ? '22%' : '55%',
                  height: from === 'cost' || from === 'delay' ? '38%' : '45%',
                }} />
            ))}
            {/* Nodes */}
            {NODES.map(n => (
              <button key={n.id}
                onClick={() => setSelectedNode(n.id)}
                className={`absolute px-3 py-1.5 rounded-md text-xs font-medium border transition ${n.pos} ${
                  selectedNode === n.id
                    ? 'bg-blue-600 text-white border-blue-500'
                    : 'bg-white dark:bg-slate-800 text-slate-700 dark:text-slate-200 border-slate-300 dark:border-slate-600 hover:border-blue-400'
                }`}>
                {t(n.labelKey, n.label)}
              </button>
            ))}
          </div>

          {/* Drawer */}
          {selectedNodeData && (
            <div className="absolute right-0 top-0 h-full w-[300px] bg-slate-50 dark:bg-slate-800 border-l border-slate-200 dark:border-slate-700 p-4 shadow-lg">
              <div className="flex items-center justify-between mb-3">
                <h3 className="text-sm font-semibold text-slate-900 dark:text-slate-100">{t(selectedNodeData.labelKey, selectedNodeData.label)}</h3>
                <button onClick={() => setSelectedNode(null)} className="text-slate-400 hover:text-slate-600 text-xs">✕</button>
              </div>
              <div className="space-y-3">
                <div>
                  <p className="text-xs font-medium text-slate-500 dark:text-slate-400 mb-1">{t('causal.evidence', '证据')}</p>
                  <ul className="space-y-1">
                    {[1, 2, 3].map(i => (
                      <li key={i} className="text-xs text-slate-700 dark:text-slate-300 px-2 py-1 bg-white dark:bg-slate-900 rounded border border-slate-200 dark:border-slate-700">
                        E-{String(i).padStart(3, '0')} · {50 + i * 15}%
                      </li>
                    ))}
                  </ul>
                </div>
                <div>
                  <p className="text-xs font-medium text-slate-500 dark:text-slate-400 mb-1">{t('causal.linkedHypotheses', '关联假设')}</p>
                  <span className="text-xs px-2 py-1 inline-block bg-amber-50 dark:bg-amber-950 text-amber-700 dark:text-amber-300 rounded border border-amber-200 dark:border-amber-800">
                    H-003 · VALID
                  </span>
                </div>
                <div>
                  <p className="text-xs font-medium text-slate-500 dark:text-slate-400 mb-1">{t('causal.dataSource', '数据来源追溯链')}</p>
                  <div className="flex items-center gap-1 flex-wrap">
                    {['D', 'I', 'K', 'E', 'H', 'B', 'C'].map((n, i) => (
                      <React.Fragment key={n}>
                        <span className="text-xs px-1.5 py-0.5 rounded bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300">{n}</span>
                        {i < 6 && <ArrowRight className="w-3 h-3 text-slate-400" />}
                      </React.Fragment>
                    ))}
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Reasoning conclusion */}
        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg p-4 space-y-4">
          <h2 className="text-sm font-medium text-slate-900 dark:text-slate-100">{t('causal.conclusion', '推理结论')}</h2>
          <div>
            <p className="text-xs font-medium text-slate-500 dark:text-slate-400 mb-1">{t('causal.mainPath', '主要解释路径')}</p>
            <p className="text-sm text-slate-700 dark:text-slate-300">
              采购成本↑ → 项目毛利下降（权重大，置信度 0.82）
            </p>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="bg-slate-50 dark:bg-slate-800 rounded-lg p-3">
              <p className="text-xs text-slate-500 dark:text-slate-400">{t('causal.confidence', '置信度')}</p>
              <p className="text-xl font-bold text-slate-900 dark:text-slate-100">0.82</p>
            </div>
            <div className="bg-slate-50 dark:bg-slate-800 rounded-lg p-3">
              <p className="text-xs text-slate-500 dark:text-slate-400">{t('causal.evidenceCoverage', '证据覆盖')}</p>
              <p className="text-xl font-bold text-slate-900 dark:text-slate-100">87%</p>
            </div>
          </div>
          <div>
            <p className="text-xs font-medium text-slate-500 dark:text-slate-400 mb-1">{t('causal.alternative', '替代解释')}</p>
            <p className="text-sm text-slate-700 dark:text-slate-300">需求变更 → 工期延长 → 人力成本↑（置信度 0.54）</p>
          </div>
          <div>
            <p className="text-xs font-medium text-slate-500 dark:text-slate-400 mb-1">{t('causal.counterfactual', '反事实检查')}</p>
            <p className="text-sm text-slate-700 dark:text-slate-300">若采购成本不上涨，毛利预计维持在 12.3% 水平。</p>
          </div>
        </div>
      </div>

      {/* Trace bar */}
      <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-lg px-4 py-3">
        <div className="flex items-center gap-2 overflow-x-auto pb-1">
          <GitBranch className="w-4 h-4 text-slate-400 flex-shrink-0" />
          <span className="text-xs text-slate-400 flex-shrink-0 mr-2">{t('causal.trace', '可追溯链')}</span>
          {TRACE_NODES.map((n, i) => (
            <React.Fragment key={n}>
              <span className="text-xs px-2 py-0.5 rounded bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 whitespace-nowrap font-medium">{n}</span>
              {i < TRACE_NODES.length - 1 && <ArrowRight className="w-3 h-3 text-slate-300 dark:text-slate-600 flex-shrink-0" />}
            </React.Fragment>
          ))}
        </div>
      </div>
    </div>
  );
}
