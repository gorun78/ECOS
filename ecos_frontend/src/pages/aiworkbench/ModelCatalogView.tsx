/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from 'react';
import { Layers, Zap, Shield, Scale, Link2, Lock } from 'lucide-react';
import { AIPModel, AIPAgent, AIPGuardrail } from '../../types/aiworkbench';
import { useTheme } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';

interface ModelCatalogViewProps {
  models: AIPModel[];
  agents: AIPAgent[];
  guardrails: AIPGuardrail[];
  onUpdateModels: (updated: AIPModel[]) => void;
  showToast?: (type: 'success' | 'info' | 'error', msg: string) => void;
}

type SubTabId = 'catalog' | 'compare' | 'guardrails';

interface ComparativeResult {
  modelId: string;
  displayName: string;
  response: string;
  latencyMs: number;
  tokensUsed: number;
  cost: string;
}

/**
 * GuardrailBindingPanel — 护栏绑定只读视图（同文件内联，单文件 < 800 行合规）。
 *
 * 职责边界：
 *   安全中心(security-engine / 安全中心 Tab) ＝ 策略定义 / 执行 / RLS/CLS/脱敏 / 审计日志
 *   AI 工作台(Models Tab 此子 Tab)        ＝ Agent 与护栏的绑定关系 (Read-Only)
 *   执行入口: 安全中心 → 护栏策略 Tab 编辑；拦截事件: 安全中心 → 审计日志 Tab
 *
 * 不做任何写操作、不渲染在线编辑控件，仅展示绑定矩阵与策略汇总。
 */
function GuardrailBindingPanel({ agents, guardrails }: { agents: AIPAgent[]; guardrails: AIPGuardrail[] }) {
  const { styles } = useTheme();
  const { t } = useLanguage();

  const boundCount = (agent: AIPAgent) => agent.guardrailIds?.length ?? 0;
  const severityDotClass: Record<AIPGuardrail['severity'], string> = {
    block: 'bg-rose-500',
    warn: 'bg-amber-500',
    audit_only: 'bg-emerald-500',
  };
  const severityLabelClass: Record<AIPGuardrail['severity'], string> = {
    block: styles.dangerText,
    warn: styles.warningText,
    audit_only: styles.successText,
  };

  const dutyLines = [
    { icon: Shield, scope: t('aiworkbench.guardrail.dutySecurity'), body: t('aiworkbench.guardrail.dutySecurityBody') },
    { icon: Scale, scope: t('aiworkbench.guardrail.dutyAIP'), body: t('aiworkbench.guardrail.dutyAIPBody') },
    { icon: Link2, scope: t('aiworkbench.guardrail.dutyExec'), body: t('aiworkbench.guardrail.dutyExecBody') },
  ];

  return (
    <div className="flex-1 overflow-y-auto p-5 space-y-4 select-none">
      {/* Duty Boundary Card — 顶部三行职责说明 */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 space-y-3 shadow-xs`}>
        {dutyLines.map((line, idx) => {
          const Icon = line.icon;
          const barColor = ['bg-rose-500', 'bg-emerald-500', 'bg-blue-500'][idx] ?? styles.cardBorder;
          return (
            <div key={idx} className={`flex items-start gap-3 ${idx > 0 ? `border-t ${styles.cardBorder} pt-3` : ''}`}>
              <span className={`w-1 self-stretch rounded ${barColor}`} />
              <Icon size={13} className={`mt-0.5 shrink-0 ${idx === 0 ? styles.dangerText : idx === 1 ? styles.successText : styles.infoText}`} />
              <div className="min-w-0">
                <span className={`text-[10px] font-bold uppercase tracking-wider ${styles.cardText}`}>{line.scope}</span>
                <p className={`text-[11px] leading-relaxed ${styles.cardTextMuted}`}>{line.body}</p>
              </div>
            </div>
          );
        })}
        {/* 只读提示 Badge — 明示本 Tab 不负修改权 */}
        <div className={`flex items-center gap-2 ${styles.inputBg} border ${styles.cardBorder} rounded-lg p-2`}>
          <Lock size={11} className={`${styles.cardTextMuted} shrink-0`} />
          <span className={`text-[10px] font-mono font-bold ${styles.cardTextMuted}`}>{t('aiworkbench.guardrail.readOnlyBadger')}</span>
          <span className={`text-[10px] ${styles.cardTextMuted}`}>{t('aiworkbench.guardrail.readOnlyHint')}</span>
        </div>
      </div>

      {/* 绑定矩阵：左 Agent，右 badge 数量 */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 space-y-2 shadow-xs`}>
        <h3 className={`text-xs font-bold ${styles.cardText}`}>{t('aiworkbench.guardrail.bindingMatrixTitle')}</h3>
        <div className="space-y-1.5 mt-2">
          {agents.length === 0 && (
            <p className={`text-[11px] ${styles.cardTextMuted} italic`}>—</p>
          )}
          {agents.map(agent => {
            const n = boundCount(agent);
            return (
              <div key={agent.id} className={`flex items-center justify-between ${styles.inputBg} border ${styles.cardBorder} rounded-lg px-2.5 py-1.5`}>
                <div className="flex items-center gap-2 min-w-0">
                  <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${agent.status === 'active' ? 'bg-emerald-500' : 'bg-amber-500'}`} />
                  <span className={`text-[11px] font-semibold truncate ${styles.cardText}`}>{agent.name}</span>
                  <span className={`text-[9px] font-mono ${styles.cardTextMuted} shrink-0`}>id={agent.id}</span>
                </div>
                <div className="flex items-center gap-1 shrink-0">
                  {Array.from({ length: Math.min(n, 8) }).map((_, i) => (
                    <span key={i} className={`px-1.5 py-0.5 ${styles.badgeBg} ${styles.accentText} ${styles.accentBorder} border rounded text-[9px] font-bold`}>
                      {t('aiworkbench.guardrail.boundBadge')}
                    </span>
                  ))}
                  {n > 8 && (
                    <span className={`text-[9px] font-mono ${styles.cardTextMuted}`}>+{n - 8}</span>
                  )}
                  {n === 0 && (
                    <span className={`text-[9px] font-mono italic ${styles.cardTextMuted}`}>{t('aiworkbench.guardrail.unboundHint')}</span>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* 策略汇总：只读 */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 space-y-2 shadow-xs`}>
        <h3 className={`text-xs font-bold ${styles.cardText}`}>{t('aiworkbench.guardrail.policySummaryTitle')}</h3>
        <div className="space-y-1.5 mt-2">
          {guardrails.length === 0 && (
            <p className={`text-[11px] ${styles.cardTextMuted} italic`}>—</p>
          )}
          {guardrails.map(g => (
            <div key={g.id} className={`flex items-center justify-between ${styles.inputBg} border ${styles.cardBorder} rounded-lg px-2.5 py-1.5`}>
              <div className="flex items-center gap-2 min-w-0">
                <span className={`w-2 h-2 rounded-full shrink-0 ${severityDotClass[g.severity]}`} />
                <span className={`text-[11px] font-semibold truncate ${styles.cardText}`}>{g.name}</span>
                <span className={`text-[9px] font-mono ${styles.cardTextMuted}`}>{g.type}</span>
              </div>
              <div className="flex items-center gap-1.5 shrink-0">
                <span className={`px-1.5 py-0.5 rounded border text-[9px] font-mono font-bold ${styles.cardBorder} ${severityLabelClass[g.severity]}`}>
                  {g.severity}
                </span>
                <span className={`px-1.5 py-0.5 rounded text-[9px] font-bold ${g.isEnabled ? `bg-emerald-500/15 ${styles.successText}` : `${styles.inputBg} ${styles.cardTextMuted}`}`}>
                  {g.isEnabled ? t('aiworkbench.guardrail.enabled') : t('aiworkbench.guardrail.disabled')}
                </span>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

export default function ModelCatalogView({
  models,
  agents,
  guardrails,
  onUpdateModels,
  showToast
}: ModelCatalogViewProps) {
  const { styles } = useTheme();
  const { t } = useLanguage();
  const [subTab, setSubTab] = useState<SubTabId>('catalog');
  const [selectedModelId, setSelectedModelId] = useState<string>(models[0]?.id || '');
  const [testPrompt, setTestPrompt] = useState('分析：航班DL440执飞机型为B737neo，由于高压油泵误报机械故障延误。拟定一份针对旅客和民航局的安全通报文案。');
  const [evalResults, setEvalResults] = useState<ComparativeResult[]>([]);
  const [isEvaluating, setIsEvaluating] = useState(false);

  const selectedModel = models.find(m => m.id === selectedModelId);

  const handleTempChange = (modelId: string, temp: number) => {
    const updated = models.map(m => {
      if (m.id === modelId) {
        return { ...m, temperature: temp };
      }
      return m;
    });
    onUpdateModels(updated);
  };

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

  const SUB_TABS: Array<{ id: SubTabId; labelKey: string }> = [
    { id: 'catalog', labelKey: 'aiworkbench.model.subtab.catalog' },
    { id: 'compare', labelKey: 'aiworkbench.model.subtab.compare' },
    { id: 'guardrails', labelKey: 'aiworkbench.model.subtab.guardrails' },
  ];

  return (
    <div className={`flex-1 flex flex-col h-full ${styles.appBg} ${styles.appText} text-xs select-none`}>
      {/* Sub-Tab Bar */}
      <div className={`px-5 pt-3 pb-0 border-b ${styles.cardBorder} flex items-center gap-1 shrink-0`}>
        {SUB_TABS.map(tab => (
          <button
            key={tab.id}
            type="button"
            onClick={() => setSubTab(tab.id)}
            className={`px-3 py-1.5 text-[11px] font-semibold rounded-md transition-colors ${
              subTab === tab.id
                ? `${styles.accentBg} text-white`
                : `${styles.cardTextMuted} hover:${styles.inputBg}`
            }`}
          >
            {t(tab.labelKey)}
          </button>
        ))}
      </div>

      <div className="flex-1 flex overflow-hidden">
        {/* catalog + compare 共用同一布局（模型选择驱动） */}
        {subTab !== 'guardrails' && (
          <>
            {/* 1. Left Models List */}
            <div className={`w-56 ${styles.cardBg} border-r ${styles.cardBorder} flex flex-col h-full shrink-0`}>
              <div className={`p-3 border-b ${styles.cardBorder} ${styles.inputBg} flex items-center justify-between`}>
                <span className={`font-bold ${styles.cardText}`}>{t('aiworkbench.model.catalogTitle')} ({models.length})</span>
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
                          : `${styles.cardTextMuted} hover:${styles.inputBg}`
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
                        <span className={`px-1.5 py-0.5 ${styles.badgeBg} ${styles.accentText} ${styles.accentBorder} border text-[9px] font-bold rounded`}>{t('aiworkbench.model.modelAssetLevel')}</span>
                      </div>
                      <p className={`text-[11px] ${styles.cardTextMuted}`}>{t('aiworkbench.model.hostingMode')}: <span className={`font-bold ${styles.cardTextMuted}`}>{selectedModel.provider} {t('aiworkbench.model.privateBoundary')}</span> | {t('aiworkbench.model.typeLabel')}: {selectedModel.type.toUpperCase()}</p>
                    </div>

                    {/* Slider for Temperature */}
                    <div className={`flex items-center gap-4 ${styles.inputBg} border ${styles.cardBorder} p-2.5 rounded-xl shrink-0`}>
                      <div className="space-y-0.5">
                        <span className={`${styles.cardTextMuted} font-bold text-[8px] uppercase block`}>{t('aiworkbench.model.temperature')}</span>
                        <span className={`font-mono font-bold ${styles.cardText} text-xs`}>{selectedModel.temperature}</span>
                      </div>
                      <input
                        type="range"
                        min="0.0"
                        max="1.0"
                        step="0.1"
                        value={selectedModel.temperature}
                        onChange={e => handleTempChange(selectedModel.id, parseFloat(e.target.value))}
                        className={`w-24 h-1 ${styles.cardBorder} rounded-lg appearance-none cursor-pointer accent-blue-600`}
                      />
                    </div>
                  </div>

                  {/* Performance Grid metrics */}
                  <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                    {[
                      { label: t('aiworkbench.model.maxContext'), val: selectedModel.maxContext, desc: t('aiworkbench.model.maxContextDesc') },
                      { label: t('aiworkbench.model.avgLatency'), val: `${selectedModel.latencyMs} ms`, desc: t('aiworkbench.model.avgLatencyDesc') },
                      { label: t('aiworkbench.model.costPerMillion'), val: selectedModel.costPerMillion, desc: t('aiworkbench.model.costDesc') },
                      { label: t('aiworkbench.model.healthRate'), val: `${selectedModel.healthRate}%`, desc: t('aiworkbench.model.healthRateDesc') }
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
                        <span className={`p-1 rounded ${styles.badgeBg} ${styles.accentText}`}>
                          <Layers size={13} />
                        </span>
                        <h3 className={`text-xs font-bold ${styles.cardText}`}>{t('aiworkbench.model.evaluationSandbox')}</h3>
                      </div>
                      <span className={`text-[9px] ${styles.cardTextMuted} font-bold ${styles.appBg} px-2 py-0.5 rounded uppercase`}>PROMPT DEBUGGER</span>
                    </div>

                    {/* Prompt Textarea */}
                    <div className="space-y-1.5">
                      <label className={`block ${styles.cardTextMuted} font-bold text-[10px] uppercase`}>{t('aiworkbench.model.testPromptLabel')}</label>
                      <textarea
                        value={testPrompt}
                        onChange={e => setTestPrompt(e.target.value)}
                        rows={2}
                        className={`w-full px-3 py-2 border ${styles.cardBorder} rounded-lg text-xs focus:outline-hidden focus:border-blue-500 font-sans leading-relaxed ${styles.cardBg} ${styles.cardText}`}
                        placeholder={t('aiworkbench.model.testPromptPlaceholder')}
                      />
                    </div>

                    <button
                      onClick={handleRunEvaluation}
                      disabled={isEvaluating || !testPrompt.trim()}
                      className={`w-full py-2 ${styles.accentBg} ${styles.accentHover} text-white font-bold rounded-lg transition-colors flex items-center justify-center gap-1.5 shadow-sm cursor-pointer ${
                        isEvaluating ? 'opacity-70 cursor-not-allowed' : ''
                      }`}
                    >
                      {isEvaluating ? (
                        <>
                          <span className={`w-3.5 h-3.5 border-2 ${styles.cardBorder} border-t-transparent rounded-full animate-spin`} />
                          <span>{t('aiworkbench.model.evaluating')}</span>
                        </>
                      ) : (
                        <>
                          <Zap size={13} />
                          <span>{t('aiworkbench.model.runEvaluation')}</span>
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
                              <span className={`px-1.5 py-0.5 ${styles.badgeBg} ${styles.accentText} ${styles.accentBorder} border rounded text-[9px] font-mono font-bold`}>ALLOW</span>
                            </div>

                            {/* Performance metrics tag */}
                            <div className={`p-2 border-b ${styles.cardBorder} ${styles.appBg} flex items-center justify-between font-mono text-[9px] ${styles.cardTextMuted}`}>
                              <span>{t('aiworkbench.model.responseLabel')}: {res.latencyMs}ms</span>
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
                <span>{t('aiworkbench.model.selectModelHint')}</span>
              </div>
            )}
          </>
        )}

        {/* guardrails 子 Tab — 绑定只读视图 */}
        {subTab === 'guardrails' && (
          <GuardrailBindingPanel agents={agents} guardrails={guardrails} />
        )}
      </div>
    </div>
  );
}
