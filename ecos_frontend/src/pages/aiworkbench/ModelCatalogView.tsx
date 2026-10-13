/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useEffect, useRef, useState } from 'react';
import { Layers, Zap, Shield, Scale, Link2, Lock } from 'lucide-react';
import { AIPModel, AIPAgent, AIPGuardrail } from '../../types/aiworkbench';
import { useTheme } from '../../components/ThemeContext';
import { useLanguage } from '../../components/LanguageContext';
import { oagPlaygroundStream } from './api';

interface ModelCatalogViewProps {
  models: AIPModel[];
  agents: AIPAgent[];
  guardrails: AIPGuardrail[];
  onUpdateModels: (updated: AIPModel[]) => void;
  showToast?: (type: 'success' | 'info' | 'error', msg: string) => void;
}

type SubTabId = 'catalog' | 'compare' | 'guardrails';

/**
 * §4.5 P0：模型对比评测改为真实引擎通道（POST /api/v1/oag/chat/stream）。
 * 字段只承载引擎真实上报值：response=content、latencyMs=done.elapsedMs、
 * traceId=done/response.traceId；tokens/cost 引擎未上报 → 不再展示（禁合成）。
 */
interface ComparativeResult {
  modelId: string;
  displayName: string;
  status: 'ok' | 'error' | 'blocked';
  response: string;
  latencyMs?: number;
  traceId?: string;
  errorMsg?: string;
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
  // 状态点：bg-current + 文本色 token，避免裸色 class
  const severityDotClass: Record<AIPGuardrail['severity'], string> = {
    block: `${styles.dangerText} bg-current`,
    warn: `${styles.warningText} bg-current`,
    audit_only: `${styles.successText} bg-current`,
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
    <div className="flex-1 overflow-y-auto p-5 space-y-4">
      {/* Duty Boundary Card — 顶部三行职责说明 */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 space-y-3 shadow-xs`}>
        {dutyLines.map((line, idx) => {
          const Icon = line.icon;
          const barColor = [`${styles.dangerText} bg-current`, `${styles.successText} bg-current`, `${styles.infoText} bg-current`][idx] ?? styles.cardBorder;
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
                  <span className={`w-1.5 h-1.5 rounded-full shrink-0 ${agent.status === 'active' ? `${styles.successText} bg-current` : `${styles.warningText} bg-current`}`} />
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
                <span className={`px-1.5 py-0.5 rounded text-[9px] font-bold ${g.isEnabled ? `${styles.successBg} ${styles.successText}` : `${styles.inputBg} ${styles.cardTextMuted}`}`}>
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
  // §4.5 P1：models 异步到达前不定型 → 空串起步 + 列表到达后回填
  const [selectedModelId, setSelectedModelId] = useState<string>('');
  useEffect(() => {
    if (!selectedModelId && models.length > 0) setSelectedModelId(models[0].id);
  }, [models, selectedModelId]);
  const [testPrompt, setTestPrompt] = useState('分析：航班DL440执飞机型为B737neo，由于高压油泵误报机械故障延误。拟定一份针对旅客和民航局的安全通报文案。');
  const [evalResults, setEvalResults] = useState<ComparativeResult[]>([]);
  const [isEvaluating, setIsEvaluating] = useState(false);
  const [evalError, setEvalError] = useState<string | null>(null);
  const evalAbortRef = useRef<AbortController | null>(null);

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

  /**
   * 真实评测：对每个已连接语言模型逐一调用 OAG SSE（POST /api/v1/oag/chat/stream），
   * 只收集引擎实报的 content/elapsedMs/traceId；失败/拦截 → 错误态呈现，不合成结果。
   */
  const handleRunEvaluation = async () => {
    if (!testPrompt.trim() || isEvaluating) return;
    const targets = models.filter(m => m.status === 'connected' && m.type === 'language').slice(0, 3);
    if (targets.length === 0) {
      setEvalError(t('aiworkbench.model.evalNoTargets'));
      setEvalResults([]);
      return;
    }
    setIsEvaluating(true);
    setEvalError(null);
    setEvalResults([]);
    const controller = new AbortController();
    evalAbortRef.current = controller;
    const collected: ComparativeResult[] = [];
    for (const m of targets) {
      const base: ComparativeResult = { modelId: m.id, displayName: m.displayName || m.name || m.id, status: 'ok', response: '' };
      try {
        await new Promise<void>((resolve) => {
          let content = '';
          let settled = false;
          const finish = (r: ComparativeResult) => {
            if (settled) return;
            settled = true;
            collected.push(r);
            setEvalResults([...collected]);
            resolve();
          };
          oagPlaygroundStream({
            message: testPrompt,
            model: m.id,
            abortSignal: controller.signal,
            handlers: {
              onStep: () => { /* 节点进度不演出，仅真事件到达时更新结果 */ },
              onResponse: (d) => { content = d.content; },
              onDone: (d) => {
                finish({ ...base, status: 'ok', response: content, latencyMs: d.elapsedMs ?? undefined, traceId: d.traceId || undefined });
              },
              onBlocked: (d) => {
                finish({ ...base, status: 'blocked', response: '', errorMsg: d.reason || t('aiworkbench.model.evalBlocked'), traceId: d.traceId || undefined });
              },
              onError: (d) => {
                finish({ ...base, status: 'error', response: '', errorMsg: d.message || t('aiworkbench.model.evalEngineError') });
              },
            },
          }).catch((e: unknown) => {
            const msg = e instanceof Error && e.message ? e.message : String(e);
            finish({ ...base, status: 'error', response: '', errorMsg: msg });
          });
        });
        if (controller.signal.aborted) break;
      } catch (e: unknown) {
        const msg = e instanceof Error && e.message ? e.message : String(e);
        collected.push({ ...base, status: 'error', errorMsg: msg });
        setEvalResults([...collected]);
      }
    }
    setIsEvaluating(false);
    evalAbortRef.current = null;
    if (collected.every(r => r.status !== 'ok')) {
      setEvalError(t('aiworkbench.model.evalAllFailed'));
    } else {
      showToast?.('success', t('aiworkbench.model.evalDone'));
    }
  };

  const SUB_TABS: Array<{ id: SubTabId; labelKey: string }> = [
    { id: 'catalog', labelKey: 'aiworkbench.model.subtab.catalog' },
    { id: 'compare', labelKey: 'aiworkbench.model.subtab.compare' },
    { id: 'guardrails', labelKey: 'aiworkbench.model.subtab.guardrails' },
  ];

  return (
    <div className={`flex-1 flex flex-col h-full ${styles.appBg} ${styles.appText} text-xs`}>
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
                : `${styles.cardTextMuted} hover:opacity-80`
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
                <span className={`px-1.5 py-0.5 ${styles.successBg} ${styles.successText} font-bold rounded text-[9px] uppercase`}>{t('aiworkbench.model.secureBadge')}</span>
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
                          : `${styles.cardTextMuted} hover:opacity-80`
                      }`}
                    >
                      <div className="flex items-center justify-between font-bold">
                        <span className="truncate text-xs">{(m.displayName || m.name || m.id || '').split(' ')[0]}</span>
                        <span className={`w-2 h-2 rounded-full ${m.status === 'connected' ? `${styles.successText} bg-current` : `${styles.dangerText} bg-current`}`} />
                      </div>
                      <div className={`flex items-center justify-between text-[10px] ${styles.cardTextMuted}`}>
                        <span>{m.provider}</span>
                        <span className="font-mono">{typeof m.healthRate === 'number' ? `${m.healthRate}%` : t('aiworkbench.model.notReported')}</span>
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
                        <span className={`font-mono font-bold ${styles.cardText} text-xs`}>{typeof selectedModel.temperature === 'number' ? selectedModel.temperature : t('aiworkbench.model.notReported')}</span>
                      </div>
                      <input
                        type="range"
                        aria-label={t('aiworkbench.model.temperature')}
                        min="0.0"
                        max="1.0"
                        step="0.1"
                        value={typeof selectedModel.temperature === 'number' ? selectedModel.temperature : 0}
                        onChange={e => handleTempChange(selectedModel.id, parseFloat(e.target.value))}
                        className={`w-24 h-1 ${styles.cardBorder} rounded-lg appearance-none cursor-pointer`}
                      />
                    </div>
                  </div>

                  {/* Performance Grid metrics — 后端未上报的指标显示「未上报」，禁合成 */}
                  <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                    {[
                      { label: t('aiworkbench.model.maxContext'), val: selectedModel.maxContext ?? t('aiworkbench.model.notReported'), desc: t('aiworkbench.model.maxContextDesc') },
                      { label: t('aiworkbench.model.avgLatency'), val: typeof selectedModel.latencyMs === 'number' ? `${selectedModel.latencyMs} ms` : t('aiworkbench.model.notReported'), desc: t('aiworkbench.model.avgLatencyDesc') },
                      { label: t('aiworkbench.model.costPerMillion'), val: selectedModel.costPerMillion ?? t('aiworkbench.model.notReported'), desc: t('aiworkbench.model.costDesc') },
                      { label: t('aiworkbench.model.healthRate'), val: typeof selectedModel.healthRate === 'number' ? `${selectedModel.healthRate}%` : t('aiworkbench.model.notReported'), desc: t('aiworkbench.model.healthRateDesc') }
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
                      <span className={`text-[9px] ${styles.cardTextMuted} font-bold ${styles.appBg} px-2 py-0.5 rounded uppercase`}>{t('aiworkbench.model.promptDebugger')}</span>
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

                    <button type="button"
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

                    {/* 评测失败横幅 — 错误态 + 重试，不静默 */}
                    {evalError && !isEvaluating && (
                      <div className={`flex items-center justify-between gap-2 ${styles.dangerBg} ${styles.dangerText} border ${styles.dangerBorder} rounded-lg px-3 py-2 text-[10px] font-bold`}>
                        <span>{evalError}</span>
                        <button
                          type="button"
                          onClick={handleRunEvaluation}
                          className={`px-2 py-0.5 rounded border ${styles.dangerBorder} cursor-pointer shrink-0`}
                        >
                          {t('aiworkbench.model.evalRetry')}
                        </button>
                      </div>
                    )}

                    {/* Evaluation side-by-side grids — 真实 OAG 引擎逐模型运行结果 */}
                    {evalResults.length > 0 && (
                      <>
                        <div className={`flex items-center gap-2 text-[10px] mt-2 ${styles.cardTextMuted}`}>
                          <span className={`px-1.5 py-0.5 rounded font-bold uppercase ${styles.badgeBg} ${styles.badgeText}`}>{t('aiworkbench.model.evalRealChannel')}</span>
                          <span>{t('aiworkbench.model.evalRealHint')}</span>
                        </div>
                        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 pt-2">
                        {evalResults.map(res => {
                          const badge = res.status === 'ok'
                            ? `${styles.successBg} ${styles.successText}`
                            : res.status === 'blocked'
                              ? `${styles.warningBg} ${styles.warningText}`
                              : `${styles.dangerBg} ${styles.dangerText}`;
                          const statusLabel = res.status === 'ok'
                            ? t('aiworkbench.model.evalStatusPass')
                            : res.status === 'blocked'
                              ? t('aiworkbench.model.evalStatusBlocked')
                              : t('aiworkbench.model.evalStatusError');
                          return (
                          <div key={res.modelId} className={`border ${styles.cardBorder} rounded-xl overflow-hidden shadow-xs ${styles.inputBg} flex flex-col`}>

                            {/* Grid header */}
                            <div className={`px-3 py-2 border-b ${styles.cardBorder} ${styles.appBg} flex items-center justify-between`}>
                              <span className={`font-bold ${styles.cardText} text-[11px]`}>{res.displayName}</span>
                              <span className={`px-1.5 py-0.5 rounded text-[9px] font-mono font-bold ${badge}`}>{statusLabel}</span>
                            </div>

                            {/* Performance metrics tag — 仅展示引擎实报值 */}
                            <div className={`p-2 border-b ${styles.cardBorder} ${styles.appBg} flex items-center justify-between font-mono text-[9px] ${styles.cardTextMuted}`}>
                              <span>{t('aiworkbench.model.responseLabel')}: {typeof res.latencyMs === 'number' ? `${res.latencyMs}ms` : t('aiworkbench.model.notReported')}</span>
                              <span>traceId: {res.traceId || t('aiworkbench.model.notReported')}</span>
                            </div>

                            {/* Content representation */}
                            <div className={`p-3 ${styles.cardBg} text-[11px] leading-relaxed font-sans flex-1 h-64 overflow-y-auto whitespace-pre-line ${res.status === 'ok' ? styles.cardTextMuted : styles.dangerText}`}>
                              {res.status === 'ok'
                                ? (res.response || t('aiworkbench.model.evalEmptyResponse'))
                                : (res.errorMsg || statusLabel)}
                            </div>

                          </div>
                          );
                        })}
                        </div>
                      </>
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
