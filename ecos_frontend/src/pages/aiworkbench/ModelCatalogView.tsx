/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from 'react';
import { Layers, Zap } from 'lucide-react';
import { AIPModel } from '../../types/aiworkbench';
import { useTheme } from '../../components/ThemeContext';

interface ModelCatalogViewProps {
  models: AIPModel[];
  onUpdateModels: (updated: AIPModel[]) => void;
  showToast?: (type: 'success' | 'info' | 'error', msg: string) => void;
}

interface ComparativeResult {
  modelId: string;
  displayName: string;
  response: string;
  latencyMs: number;
  tokensUsed: number;
  cost: string;
}

export default function ModelCatalogView({
  models,
  onUpdateModels,
  showToast
}: ModelCatalogViewProps) {
  const { styles } = useTheme();
  const [selectedModelId, setSelectedModelId] = useState<string>(models[0]?.id || '');
  const [testPrompt, setTestPrompt] = useState('分析：航班DL440执飞机型为B737neo，由于高压油泵误报机械故障延误。拟定一份针对旅客和民航局的安全通报文案。');
  const [evalResults, setEvalResults] = useState<ComparativeResult[]>([]);
  const [isEvaluating, setIsEvaluating] = useState(false);

  const selectedModel = models.find(m => m.id === selectedModelId);

  // Handle Temp sliders
  const handleTempChange = (modelId: string, temp: number) => {
    const updated = models.map(m => {
      if (m.id === modelId) {
        return { ...m, temperature: temp };
      }
      return m;
    });
    onUpdateModels(updated);
  };

  // Run Side-by-Side comparative evaluations
  const handleRunEvaluation = async () => {
    if (!testPrompt.trim()) return;
    setIsEvaluating(true);
    setEvalResults([]);

    await new Promise(resolve => setTimeout(resolve, 1200));

    const results: ComparativeResult[] = [
      {
        modelId: 'gemini-1.5-pro',
        displayName: 'Gemini 1.5 Pro',
        latencyMs: 125,
        tokensUsed: 620,
        cost: '$0.004',
        response: `【民航运行控制中心 - 官方安全审计报告】\n\n一、事件概要：\n今日执飞 DL440 航班的 B737neo 客机（机身尾号 N204DL）于出发前触发高压油泵温感偏离警报。经地面整修，证实为电子温感传感器信号漂移误报，液压物理件完好。\n\n二、通告方案（旅客端）：\n「重要航务通知」今日 DL440 航班因计划外地面安全检查，起飞延迟。现已排除故障，预计12:45恢复。感谢各位旅客的理解，安全始终是我们的最高准则。\n\n三、局方报备信息：\n符合 CAAC 121.483 条技术规范，温感排故完毕，不影响后续适航评级。`
      },
      {
        modelId: 'claude-3-5-sonnet',
        displayName: 'Claude 3.5 Sonnet',
        latencyMs: 185,
        tokensUsed: 710,
        cost: '$0.010',
        response: `【关于 DL440 航班高压油泵异常告警的技术通报】\n\n致中南航监局及相关签派单位：\n\n根据《民用航空器运行适航管理规定》，现就今日 DL440 航班延迟原因做如下披露：\n1. 故障描述：离港准备阶段，机载 QAR 监测系统捕获高压油泵物理循环压力异常报警。\n2. 排故详情：经签派与机械师两轮核对，确认为单组温敏电阻在高负荷滑行时发生阻抗漂移，从而向飞控发出冗余告警。已更换温阻物理备件，系统压力恢复黄金标准。\n3. 旅客安抚：已提供机场等值午餐券及航延积分，运行井然有序。`
      },
      {
        modelId: 'gpt-4o',
        displayName: 'GPT-4o (Azure VPC)',
        latencyMs: 155,
        tokensUsed: 590,
        cost: '$0.005',
        response: `「航班 DL440 故障排查及安全公示文案」\n\n● 官方通告：\nDL440 航班（机型 B737neo）由于出发前滑行段突发油回路传感器数据偏离，为确保万无一失，AOC 签派下达就地复检指令。现已更换温控单元，航班重新起飞。保障绝对飞行安全是我们的核心，特此公示。\n\n● 性能统计：\n* 排故时间: 45分钟\n* 资质审核: 机长执照完全符合\n* CAAC规范: 零违规扣分。`
      }
    ];

    setEvalResults(results);
    setIsEvaluating(false);
    showToast?.('success', '多大模型 side-by-side 对齐评测完成');
  };

  return (
    <div className={`flex h-full overflow-hidden select-none ${styles.appBg} text-xs`}>
      
      {/* 1. Left Models List */}
      <div className={`w-56 ${styles.cardBg} border-r ${styles.cardBorder} flex flex-col h-full shrink-0`}>
        <div className={`p-3 border-b ${styles.cardBorder} ${styles.inputBg} flex items-center justify-between`}>
          <span className={`font-bold ${styles.cardText}`}>模型目录与服务池 ({models.length})</span>
          <span className="px-1.5 py-0.5 bg-emerald-100 text-emerald-700 font-bold rounded text-[9px] uppercase">SECURE</span>
        </div>

        <div className="flex-1 overflow-y-auto p-1.5 space-y-1">
          {models.map(m => {
            const isSelected = selectedModelId === m.id;
            return (
              <div
                key={m.id}
                onClick={() => setSelectedModelId(m.id)}
                className={`p-2.5 rounded-lg cursor-pointer transition-all flex flex-col gap-1.5 ${
                  isSelected
                    ? `${styles.accentBg} text-white shadow-xs`
                    : 'text-slate-600 hover:bg-slate-50'
                }`}
              >
                <div className="flex items-center justify-between font-bold">
                  <span className="truncate text-xs">{(m.displayName || m.name || m.id || '').split(' ')[0]}</span>
                  <span className={`w-2 h-2 rounded-full ${m.status === 'connected' ? 'bg-emerald-400' : 'bg-rose-400'}`} />
                </div>
                <div className={`flex items-center justify-between text-[10px] ${styles.cardTextMuted}`}>
                  <span>{m.provider}</span>
                  <span className="font-mono">{m.healthRate}%</span>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* 2. Central Model telemetry dashboard */}
      {selectedModel ? (
        <div className="flex-1 flex flex-col h-full overflow-hidden">
          
          <div className="flex-1 overflow-y-auto p-5 space-y-5">
            
            {/* Model Profile Panel */}
            <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs flex flex-col md:flex-row md:items-center justify-between gap-4`}>
              <div className="space-y-1">
                <div className="flex items-center gap-2">
                  <h2 className={`text-sm font-black ${styles.cardText}`}>{selectedModel.displayName || selectedModel.name || selectedModel.id}</h2>
                  <span className="px-1.5 py-0.5 bg-blue-50 text-blue-600 border border-blue-200 text-[9px] font-bold rounded">模型资产级</span>
                </div>
                <p className={`text-[11px] ${styles.cardTextMuted}`}>托管方式: <span className={`font-bold ${styles.cardTextMuted}`}>{selectedModel.provider} 安全私有边界镜像</span> | 类型: {selectedModel.type.toUpperCase()}</p>
              </div>

              {/* Slider for Temperature */}
              <div className={`flex items-center gap-4 ${styles.inputBg} border ${styles.cardBorder} p-2.5 rounded-xl shrink-0`}>
                <div className="space-y-0.5">
                  <span className={`${styles.cardTextMuted} font-bold text-[8px] uppercase block`}>温度调节 (Temp):</span>
                  <span className={`font-mono font-bold ${styles.cardText} text-xs`}>{selectedModel.temperature}</span>
                </div>
                <input
                  type="range"
                  min="0.0"
                  max="1.0"
                  step="0.1"
                  value={selectedModel.temperature}
                  onChange={e => handleTempChange(selectedModel.id, parseFloat(e.target.value))}
                  className="w-24 h-1 bg-slate-200 rounded-lg appearance-none cursor-pointer accent-blue-600"
                />
              </div>
            </div>

            {/* Performance Grid metrics */}
            <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
              {[
                { label: '支持最大上下文', val: selectedModel.maxContext, desc: '支持长维保手册级输入' },
                { label: '平均响应延迟', val: `${selectedModel.latencyMs} ms`, desc: '低于大厅调度延时红线' },
                { label: '算力单价 /M tokens', val: selectedModel.costPerMillion, desc: '包含企业私有折扣折算' },
                { label: 'API 可用性绿线', val: `${selectedModel.healthRate}%`, desc: '近30天持续无间断心跳' }
              ].map((x, idx) => (
                <div key={idx} className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-3 shadow-xs space-y-1`}>
                  <span className={`text-[9px] ${styles.cardTextMuted} font-bold uppercase tracking-wider block`}>{x.label}</span>
                  <p className={`text-sm font-black ${styles.cardText}`}>{x.val}</p>
                  <p className={`text-[9px] ${styles.cardTextMuted} leading-relaxed`}>{x.desc}</p>
                </div>
              ))}
            </div>

            {/* Prompt Side-by-Side evaluation Suite */}
            <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs space-y-4`}>
              
              <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-3`}>
                <div className="flex items-center gap-2">
                  <span className="p-1 rounded bg-indigo-50 text-indigo-600">
                    <Layers size={13} />
                  </span>
                  <h3 className={`text-xs font-bold ${styles.cardText}`}>多模型对齐评测沙箱 (Side-by-Side Prompt Evaluation)</h3>
                </div>
                <span className={`text-[9px] ${styles.cardTextMuted} font-bold ${styles.appBg} px-2 py-0.5 rounded uppercase`}>PROMPT DEBUGGER</span>
              </div>

              {/* Prompt Textarea */}
              <div className="space-y-1.5">
                <label className={`block ${styles.cardTextMuted} font-bold text-[10px] uppercase`}>输入测试提示词 (Enter Test Prompt)</label>
                <textarea
                  value={testPrompt}
                  onChange={e => setTestPrompt(e.target.value)}
                  rows={2}
                  className={`w-full px-3 py-2 border ${styles.cardBorder} rounded-lg text-xs focus:outline-hidden focus:border-blue-500 font-sans leading-relaxed`}
                  placeholder="请输入用于对比测试的提示词..."
                />
              </div>

              <button
                onClick={handleRunEvaluation}
                disabled={isEvaluating || !testPrompt.trim()}
                className={`w-full py-2 bg-indigo-600 hover:bg-indigo-700 text-white font-bold rounded-lg transition-colors flex items-center justify-center gap-1.5 shadow-sm cursor-pointer ${
                  isEvaluating ? 'opacity-70 cursor-not-allowed' : ''
                }`}
              >
                {isEvaluating ? (
                  <>
                    <span className={`w-3.5 h-3.5 border-2 ${styles.cardBorder} border-t-transparent rounded-full animate-spin`} />
                    <span>正在调度多机并行评测中...</span>
                  </>
                ) : (
                  <>
                    <Zap size={13} />
                    <span>执行多模型对齐评测 (Run Side-by-Side Evaluation)</span>
                  </>
                )}
              </button>

              {/* Evaluation side-by-side grids */}
              {evalResults.length > 0 && (
                <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 pt-2">
                  {evalResults.map(res => (
                    <div key={res.modelId} className={`border ${styles.cardBorder} rounded-xl overflow-hidden shadow-xs ${styles.inputBg} flex flex-col`}>
                      
                      {/* Grid header */}
                      <div className={`px-3 py-2 border-b ${styles.cardBorder} ${styles.appBg} flex items-center justify-between`}>
                        <span className={`font-bold ${styles.cardText} text-[11px]`}>{res.displayName}</span>
                        <span className="px-1.5 py-0.5 bg-blue-50 text-blue-600 border border-blue-200 rounded text-[9px] font-mono font-bold">ALLOW</span>
                      </div>

                      {/* Performance metrics tag */}
                      <div className={`p-2 border-b ${styles.cardBorder} ${styles.appBg} flex items-center justify-between font-mono text-[9px] ${styles.cardTextMuted}`}>
                        <span>响应: {res.latencyMs}ms</span>
                        <span>Token: {res.tokensUsed}T</span>
                        <span className="text-emerald-600 font-bold">{res.cost}</span>
                      </div>

                      {/* Content representation */}
                      <div className={`p-3 ${styles.cardBg} text-[11px] ${styles.cardTextMuted} leading-relaxed font-sans flex-1 h-64 overflow-y-auto whitespace-pre-line`}>
                        {res.response}
                      </div>

                    </div>
                  ))}
                </div>
              )}

            </div>

          </div>

        </div>
      ) : (
        <div className={`flex-1 flex flex-col items-center justify-center ${styles.cardTextMuted}`}>
          <Layers size={32} className={`${styles.cardTextMuted} animate-bounce mb-2`} />
          <span>请选择大模型查看详情</span>
        </div>
      )}

    </div>
  );
}
