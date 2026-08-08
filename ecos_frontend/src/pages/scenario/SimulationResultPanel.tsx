/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */
import React from 'react';
import LucideIcon from '../../components/LucideIcon';
import type { ThemeStyles } from '../../components/ThemeContext';

export interface SimulationResultPanelProps {
  simQuery: string;
  setSimQuery: (v: string) => void;
  simRole: 'AOC_DIRECTOR' | 'EXTERNAL_CONTRACTOR';
  setSimRole: (v: 'AOC_DIRECTOR' | 'EXTERNAL_CONTRACTOR') => void;
  simResult: any;
  isSimulating: boolean;
  onRun: () => void;
  styles: ThemeStyles;
  locale: string;
  tl: (zh: string, en: string) => string;
  safetyIndexActual: string;
}

export default function SimulationResultPanel({
  simQuery,
  setSimQuery,
  simRole,
  setSimRole,
  simResult,
  isSimulating,
  onRun,
  styles,
  locale,
  tl,
  safetyIndexActual,
}: SimulationResultPanelProps) {
  return (
    <div className="space-y-4">
      {/* Query Input Area */}
      <div className={`p-3 ${styles.cardBg} border ${styles.cardBorder} rounded-lg space-y-3`}>
        <div className="flex items-center gap-2">
          <LucideIcon name="FlaskConical" size={14} className="text-amber-400" />
          <span className="text-xs font-bold text-amber-300 flex items-center gap-1.5">
            {tl('零信任对账仿真推演沙箱', 'Zero-Trust Reconciliation Sandbox')}
          </span>
        </div>

        {/* Query Input */}
        <div className="flex gap-2">
          <input
            type="text"
            placeholder={tl('输入要推演的查询，如：查询机长资质与薪资', 'Enter query to simulate...')}
            value={simQuery}
            onChange={(e) => setSimQuery(e.target.value)}
            className={`flex-1 p-2 text-xs ${styles.inputBg} border ${styles.inputBorder} rounded ${styles.inputText} outline-none focus:border-indigo-500`}
          />
          <button
            onClick={onRun}
            disabled={isSimulating}
            className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white text-xs font-bold rounded cursor-pointer transition-all flex items-center gap-1.5"
          >
            {isSimulating ? (
              <LucideIcon name="Loader" size={12} className="animate-spin" />
            ) : (
              <LucideIcon name="Play" size={12} />
            )}
            {isSimulating ? tl('推演中...', 'Simulating...') : tl('运行推演', 'Run')}
          </button>
        </div>

        {/* Role Selector */}
        <div className="flex items-center gap-3">
          <span className={`text-[10px] ${styles.cardTextMuted} font-bold`}>
            {tl('模拟角色', 'Role')}:
          </span>
          <label className={`flex items-center gap-1.5 text-[11px] cursor-pointer ${simRole === 'AOC_DIRECTOR' ? 'text-emerald-400 font-bold' : styles.cardTextMuted}`}>
            <input type="radio" name="simRole" checked={simRole === 'AOC_DIRECTOR'}
              onChange={() => setSimRole('AOC_DIRECTOR')} className="accent-indigo-500" />
            {tl('签派总监 (王凯)', 'AOC Director')}
          </label>
          <label className={`flex items-center gap-1.5 text-[11px] cursor-pointer ${simRole === 'EXTERNAL_CONTRACTOR' ? 'text-rose-400 font-bold' : styles.cardTextMuted}`}>
            <input type="radio" name="simRole" checked={simRole === 'EXTERNAL_CONTRACTOR'}
              onChange={() => setSimRole('EXTERNAL_CONTRACTOR')} className="accent-indigo-500" />
            {tl('外部承包商', 'External Contractor')}
          </label>
        </div>
      </div>

      {/* Results Area */}
      {simResult && (
        <div className={`p-4 ${styles.cardBg} border ${styles.cardBorder} rounded-lg space-y-3`}>
          {/* Verdict */}
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold text-white">
              {tl('推演结果', 'Simulation Result')}
            </span>
            <span
              className={`text-[10px] font-bold px-2 py-0.5 rounded uppercase ${
                simResult.verdict === 'GRANTED'
                  ? 'bg-emerald-950 text-emerald-400 border border-emerald-900/50'
                  : 'bg-rose-950 text-rose-400 border border-rose-900/50'
              }`}
            >
              {simResult.verdict === 'GRANTED' ? '✅ GRANTED' : '🛡️ DENIED'}
            </span>
          </div>

          {/* Answer */}
          <div className={`p-3 ${styles.inputBg} border ${styles.inputBorder} rounded`}>
            <pre className="text-xs text-slate-200 font-mono whitespace-pre-wrap leading-relaxed">
              {simResult.answer || simResult.message}
            </pre>
          </div>

          {/* Grounded docs */}
          {simResult.groundedDocs && simResult.groundedDocs.length > 0 && (
            <div className="space-y-1.5">
              <span className={`text-[10px] ${styles.cardTextMuted} font-bold`}>
                {tl('关联知识库文档', 'Grounded Documents')}
              </span>
              {simResult.groundedDocs.map((doc: any, i: number) => (
                <div key={i} className={`flex items-center justify-between p-2 ${styles.cardBg} border ${styles.cardBorder} rounded text-[10px]`}>
                  <span className="text-slate-300 font-mono">{doc.title}</span>
                  <span className="text-emerald-400 font-bold">
                    {(doc.score * 100).toFixed(0)}%
                  </span>
                </div>
              ))}
            </div>
          )}

          {/* Metrics Comparison: Baseline vs Prediction */}
          <div className={`p-3 ${styles.cardBg} border ${styles.cardBorder} rounded-lg space-y-2`}>
            <span className={`text-[10px] ${styles.cardTextMuted} font-bold flex items-center gap-1`}>
              <LucideIcon name="BarChart3" size={11} className="text-indigo-400" />
              {tl('基线 vs 预测对比', 'Baseline vs Prediction')}
            </span>
            <div className="grid grid-cols-2 gap-3 text-[10px]">
              <div className={`p-2 ${styles.inputBg} rounded text-center`}>
                <span className={`block ${styles.cardTextMuted}`}>{tl('安全适航基线', 'Safety Baseline')}</span>
                <span className="text-sm font-bold text-indigo-400">{safetyIndexActual}</span>
              </div>
              <div className={`p-2 ${simResult.verdict === 'GRANTED' ? 'bg-emerald-950/30' : 'bg-rose-950/30'} rounded text-center`}>
                <span className={`block ${styles.cardTextMuted}`}>{tl('推演后预测', 'Post-Simulation')}</span>
                <span className={`text-sm font-bold ${simResult.verdict === 'GRANTED' ? 'text-emerald-400' : 'text-rose-400'}`}>
                  {simResult.verdict === 'GRANTED' ? '100.00%' : safetyIndexActual}
                </span>
              </div>
            </div>
            {/* Delta indicator */}
            <div className={`text-center text-[10px] font-mono font-bold ${simResult.verdict === 'GRANTED' ? 'text-emerald-400' : 'text-rose-400'}`}>
              Δ = {simResult.verdict === 'GRANTED' ? '+' : ''}
              {simResult.verdict === 'GRANTED'
                ? (100 - parseFloat(safetyIndexActual)).toFixed(1)
                : '0.00'}
              %
            </div>
          </div>
        </div>
      )}

      {/* Empty state */}
      {!simResult && !isSimulating && (
        <div className={`p-8 ${styles.cardBg} border ${styles.cardBorder} rounded-lg text-center`}>
          <LucideIcon name="FlaskConical" size={24} className={`mx-auto mb-2 ${styles.cardTextMuted}`} />
          <p className={`text-xs ${styles.cardTextMuted}`}>
            {tl('点击「运行推演」开始零信任对账仿真', 'Click "Run" to start zero-trust reconciliation simulation')}
          </p>
        </div>
      )}
    </div>
  );
}
