import React, { useState, useEffect } from "react";
import { X, Loader2, AlertTriangle, CheckCircle2, ArrowRight, Stethoscope } from "lucide-react";
import { useTheme } from "./ThemeContext";

interface DiagnosticPanelProps {
  result: any;
  loading: boolean;
  onClose: () => void;
  onRetry: () => void;
}

export default function DiagnosticPanel({ result, loading, onClose, onRetry }: DiagnosticPanelProps) {
  const { styles } = useTheme();
  return (
    <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
      <div className={`${styles.cardBg} rounded-2xl shadow-2xl max-w-2xl w-full max-h-[80vh] overflow-auto`}>
        {/* Header */}
        <div className={`flex items-center justify-between p-5 border-b ${styles.cardBorder}`}>
          <div className="flex items-center gap-3">
            <Stethoscope className="w-6 h-6 text-[#3B82F6]" />
            <div>
              <h2 className={`text-lg font-bold ${styles.cardText}`}>AI 经营诊断</h2>
              <p className={`text-xs ${styles.cardTextMuted}`}>因果链追溯 · 根因分析 · 应对方案</p>
            </div>
          </div>
          <button onClick={onClose} className={`p-2 rounded-lg hover:bg-black/5 dark:hover:bg-white/5 transition ${styles.cardTextMuted}`}>
            <X className={`w-5 h-5 ${styles.cardTextMuted}`} />
          </button>
        </div>

        {/* Body */}
        <div className="p-5">
          {loading ? (
            <div className="text-center py-12">
              <Loader2 className="w-10 h-10 text-[#3B82F6] animate-spin mx-auto mb-3" />
              <p className={`text-sm ${styles.cardTextMuted}`}>经营诊断Agent正在分析中...</p>
              <p className={`text-xs ${styles.cardTextMuted} mt-1`}>查询目标偏差 → 追溯因果链 → 生成应对方案</p>
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
                      <pre className={`text-sm ${styles.cardText} whitespace-pre-wrap font-sans leading-relaxed`}>
                        {result.answer}
                      </pre>
                    </div>
                  )}

                  {/* Deviations */}
                  {result.deviations && result.deviations.length > 0 && (
                    <div>
                      <h3 className={`text-sm font-semibold ${styles.cardText} mb-2`}>目标偏差详情</h3>
                      <div className="space-y-2">
                        {result.deviations.map((d: any, i: number) => (
                          <div key={i} className={`flex items-center gap-3 p-3 rounded-lg border ${styles.appBorder} ${styles.appBg}`}>
                            <div className={`w-2 h-2 rounded-full ${d.status === 'CRITICAL' ? 'bg-red-500' : 'bg-amber-500'}`} />
                            <div className="flex-1">
                              <p className={`text-sm ${styles.cardText}`}>{d.name}</p>
                              <p className={`text-xs ${styles.cardTextMuted}`}>
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
                      <h3 className={`text-sm font-semibold ${styles.cardText} mb-2`}>因果链传导</h3>
                      <div className="space-y-2">
                        {result.causalChains.map((c: any, i: number) => (
                          <div key={i} className={`flex items-center gap-2 p-2 rounded-lg ${styles.appBg} text-sm`}>
                            <span className={`${styles.cardTextMuted}`}>{c.sourceNode?.name || c.source}</span>
                            <ArrowRight className={`w-4 h-4 ${styles.cardTextMuted}`} />
                            <span className={`${styles.cardTextMuted}`}>{c.targetNode?.name || c.target}</span>
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
