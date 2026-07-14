import React, { useState, useEffect } from "react";
import { X, Loader2, AlertTriangle, CheckCircle2, ArrowRight, Stethoscope } from "lucide-react";

interface DiagnosticPanelProps {
  result: any;
  loading: boolean;
  onClose: () => void;
  onRetry: () => void;
}

export default function DiagnosticPanel({ result, loading, onClose, onRetry }: DiagnosticPanelProps) {
  return (
    <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
      <div className="bg-white dark:bg-slate-800 rounded-2xl shadow-2xl max-w-2xl w-full max-h-[80vh] overflow-auto">
        {/* Header */}
        <div className="flex items-center justify-between p-5 border-b border-slate-200 dark:border-slate-700">
          <div className="flex items-center gap-3">
            <Stethoscope className="w-6 h-6 text-[#3B82F6]" />
            <div>
              <h2 className="text-lg font-bold text-slate-800 dark:text-slate-100">AI 经营诊断</h2>
              <p className="text-xs text-slate-400">因果链追溯 · 根因分析 · 应对方案</p>
            </div>
          </div>
          <button onClick={onClose} className="p-2 rounded-lg hover:bg-slate-100 dark:hover:bg-slate-700 transition">
            <X className="w-5 h-5 text-slate-400" />
          </button>
        </div>

        {/* Body */}
        <div className="p-5">
          {loading ? (
            <div className="text-center py-12">
              <Loader2 className="w-10 h-10 text-[#3B82F6] animate-spin mx-auto mb-3" />
              <p className="text-sm text-slate-400">经营诊断Agent正在分析中...</p>
              <p className="text-xs text-slate-300 mt-1">查询目标偏差 → 追溯因果链 → 生成应对方案</p>
            </div>
          ) : result ? (
            <div className="space-y-4">
              {result.error ? (
                <div className="p-4 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-xl flex items-start gap-3">
                  <AlertTriangle className="w-5 h-5 text-red-500 mt-0.5" />
                  <div>
                    <p className="text-sm font-semibold text-red-700 dark:text-red-400">诊断异常</p>
                    <p className="text-xs text-red-600 dark:text-red-300 mt-1">{result.error}</p>
                    <button onClick={onRetry} className="mt-2 text-xs font-medium text-red-600 underline">重试</button>
                  </div>
                </div>
              ) : (
                <>
                  {/* Answer */}
                  {result.answer && (
                    <div className="p-4 bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-xl">
                      <pre className="text-sm text-slate-700 dark:text-slate-300 whitespace-pre-wrap font-sans leading-relaxed">
                        {result.answer}
                      </pre>
                    </div>
                  )}

                  {/* Deviations */}
                  {result.deviations && result.deviations.length > 0 && (
                    <div>
                      <h3 className="text-sm font-semibold text-slate-700 dark:text-slate-300 mb-2">目标偏差详情</h3>
                      <div className="space-y-2">
                        {result.deviations.map((d: any, i: number) => (
                          <div key={i} className="flex items-center gap-3 p-3 rounded-lg border border-slate-100 dark:border-slate-700 bg-slate-50 dark:bg-slate-900/50">
                            <div className={`w-2 h-2 rounded-full ${d.status === 'CRITICAL' ? 'bg-red-500' : 'bg-amber-500'}`} />
                            <div className="flex-1">
                              <p className="text-sm text-slate-700 dark:text-slate-300">{d.name}</p>
                              <p className="text-xs text-slate-400">
                                目标 {d.targetValue?.toLocaleString()} / 实际 {d.currentValue?.toLocaleString()} (偏差 {d.deviationPct?.toFixed(1)}%)
                              </p>
                            </div>
                            <span className={`text-xs font-mono px-2 py-0.5 rounded ${d.status === 'CRITICAL' ? 'bg-red-100 dark:bg-red-900/30 text-red-600' : 'bg-amber-100 dark:bg-amber-900/30 text-amber-600'}`}>
                              {d.status}
                            </span>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Causal Chains */}
                  {result.causalChains && result.causalChains.length > 0 && (
                    <div>
                      <h3 className="text-sm font-semibold text-slate-700 dark:text-slate-300 mb-2">因果链传导</h3>
                      <div className="space-y-2">
                        {result.causalChains.map((c: any, i: number) => (
                          <div key={i} className="flex items-center gap-2 p-2 rounded-lg bg-slate-50 dark:bg-slate-900/50 text-sm">
                            <span className="text-slate-600 dark:text-slate-400">{c.sourceNode?.name || c.source}</span>
                            <ArrowRight className="w-4 h-4 text-slate-300" />
                            <span className="text-slate-600 dark:text-slate-400">{c.targetNode?.name || c.target}</span>
                            {c.relationshipType && (
                              <span className={`text-xs ml-1 px-1.5 py-0.5 rounded ${c.relationshipType === 'NEGATIVE' ? 'bg-red-100 text-red-600' : 'bg-green-100 text-green-600'}`}>
                                {c.relationshipType === 'NEGATIVE' ? '↓' : '↑'}
                              </span>
                            )}
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </>
              )}
            </div>
          ) : null}
        </div>
      </div>
    </div>
  );
}
