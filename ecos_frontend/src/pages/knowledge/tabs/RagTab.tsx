/**
 * PMO-26 T3: RAG Retrieval Enhancement
 * 来源标注 · 多轮追问 · 置信度badge · 总置信度展示
 */

import React, { useState } from 'react';
import {
  Keyboard, Flame, Layers, RefreshCw, Bot,
  FileText, Table2, BarChart3, AlertTriangle,
  MessageSquare, ChevronRight, Zap
} from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { knowledgeApi } from '../services/knowledgeApi';

interface RagSource {
  title: string; type?: string; snippet: string; score: number; page?: number;
}
interface QARound {
  question: string; answer: string; sources: RagSource[]; overallConfidence: number;
}

const MAX_HISTORY = 3;

function confStyle(s: number) {
  if (s > 0.8) return { bg: 'bg-emerald-50', txt: 'text-emerald-700', border: 'border-emerald-200', dot: 'bg-emerald-500' };
  if (s > 0.6) return { bg: 'bg-amber-50', txt: 'text-amber-700', border: 'border-amber-200', dot: 'bg-amber-500' };
  return { bg: 'bg-red-50', txt: 'text-red-700', border: 'border-red-200', dot: 'bg-red-500' };
}

function srcIcon(type?: string) {
  if (type === 'table') return Table2;
  if (type === 'chart' || type === 'barChart') return BarChart3;
  return FileText;
}

export default function RagTab() {
  const { t, locale } = useLanguage();
  const defaultQ = t("knowledge.ragtab.查询欧盟gdpr合规隔离下_飞行员张建国的执勤及ssn脱敏状态");

  const [queryInput, setQueryInput] = useState(defaultQ);
  const [followUpInput, setFollowUpInput] = useState('');
  const [isRetrieving, setIsRetrieving] = useState(false);
  const [llmOutput, setLlmOutput] = useState('');
  const [sources, setSources] = useState<RagSource[]>([]);
  const [overallConfidence, setOverallConfidence] = useState<number | null>(null);
  const [qaHistory, setQaHistory] = useState<QARound[]>([]);

  const runQuery = async (q: string) => {
    setIsRetrieving(true);
    setLlmOutput(''); setSources([]); setOverallConfidence(null);
    try {
      const result = await knowledgeApi.runRAGQuery({ query: q, topK: 5 });
      const srcs: RagSource[] = (result.sources || []).map((s: any) => ({
        title: s.title || 'Unknown', type: s.type || 'document',
        snippet: s.snippet || '', score: s.score ?? 0, page: s.page,
      }));
      const avg = srcs.length > 0 ? srcs.reduce((a: number, b: RagSource) => a + b.score, 0) / srcs.length : 0;

      setLlmOutput(result.answer || '');
      setSources(srcs);
      setOverallConfidence(avg);

      if (!result.answer) {
        const legacy = await knowledgeApi.runKnowledgeQuery(q) as any;
        if (legacy?.llmOutput) setLlmOutput(legacy.llmOutput);
        if (!srcs.length && legacy?.groundedDocs) {
          const ls: RagSource[] = (legacy.groundedDocs || []).map((d: any) => ({
            title: d.title || d.name || 'Unknown', type: 'document',
            snippet: d.snippet || d.description || '', score: d.score ?? 0,
          }));
          setSources(ls);
          if (ls.length > 0) setOverallConfidence(ls.reduce((a: number, b: RagSource) => a + b.score, 0) / ls.length);
        }
      }

      // Record in history
      const ans = result.answer || '';
      setQaHistory(prev => {
        const updated = [...prev, { question: q, answer: ans, sources: srcs, overallConfidence: avg }];
        return updated.slice(-MAX_HISTORY);
      });
    } catch {
      setLlmOutput(t("knowledge.ragtab.检索失败_请检查后端连接"));
    } finally { setIsRetrieving(false); }
  };

  const handleRun = () => { if (queryInput.trim()) runQuery(queryInput); };
  const handleFollowUp = () => {
    if (!followUpInput.trim()) return;
    const q = followUpInput;
    setFollowUpInput('');
    runQuery(q);
  };

  const c = overallConfidence !== null ? confStyle(overallConfidence) : null;
  const pct = overallConfidence !== null ? (overallConfidence * 100).toFixed(0) : '0';

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-3 space-y-1">
        <h2 className="text-sm font-black text-slate-800">{t("knowledge.ragtab.联邦知识库检索与大模型推理仿真沙箱")}</h2>
        <p className="text-xs text-slate-500">{t("knowledge.ragtab.模拟_aip_copilot_多轮检索问答_来源标注_置信度")}</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        {/* Left — Query + History */}
        <div className="lg:col-span-5 space-y-4">
          <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-xs space-y-4">
            <div className="border-b border-slate-100 pb-2 flex items-center gap-2">
              <span className="p-1.5 rounded bg-blue-50 text-blue-600"><Keyboard size={13} /></span>
              <h3 className="font-bold text-slate-800 text-xs">{t("knowledge.ragtab.输入自然语言查询")}</h3>
            </div>
            <textarea value={queryInput} onChange={e => setQueryInput(e.target.value)} rows={3}
              className="w-full px-3 py-2 border border-slate-200 rounded-lg text-xs text-slate-700 focus:outline-hidden focus:border-blue-500" />
            <div className="space-y-1">
              <span className="text-[9px] text-slate-400 font-extrabold uppercase block">{t("knowledge.ragtab.推荐问题")}</span>
              {[defaultQ,
                t("knowledge.ragtab.评估_ds_ticket_sales_被高危扫描的风险"),
                t("knowledge.ragtab.查询_ua102_航班上游链路与时效影响"),
              ].map((p, i) => (
                <button key={i} onClick={() => setQueryInput(p)}
                  className="text-left w-full px-2 py-1 bg-slate-50 border border-slate-200 hover:bg-blue-50 rounded-lg text-[10px] text-slate-600 truncate cursor-pointer block">💡 {p}</button>
              ))}
            </div>
            <button onClick={handleRun} disabled={isRetrieving || !queryInput.trim()}
              className={`w-full py-2 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-lg flex items-center justify-center gap-1.5 shadow-sm cursor-pointer ${isRetrieving ? 'opacity-70' : ''}`}>
              {isRetrieving ? <><span className="w-3.5 h-3.5 border-2 border-slate-100 border-t-transparent rounded-full animate-spin" />{t("knowledge.ragtab.检索中")}</>
                : <><Flame size={13} />{t("knowledge.ragtab.开始检索推理")}</>}
            </button>
          </div>

          {/* Multi-turn history */}
          {qaHistory.length > 0 && (
            <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-xs space-y-3">
              <div className="flex items-center justify-between border-b border-slate-100 pb-2">
                <h3 className="font-bold text-slate-800 text-xs flex items-center gap-1.5"><MessageSquare size={12} className="text-indigo-500" />{t("knowledge.ragtab.对话历史")}</h3>
                <span className="text-[9px] text-slate-400">{t("knowledge.ragtab.最近_n_轮").replace("{n}", String(MAX_HISTORY))}</span>
              </div>
              <div className="space-y-2 max-h-48 overflow-y-auto">
                {qaHistory.map((r, i) => (
                  <div key={i} className="p-2 bg-slate-50 border border-slate-150 rounded-lg space-y-1">
                    <p className="text-[10px] font-bold text-slate-700"><span className="text-blue-500">Q{i + 1}:</span> {r.question}</p>
                    {r.answer && <p className="text-[9px] text-slate-500"><span className="text-emerald-500 font-bold">A{i + 1}:</span> {r.answer.slice(0, 150)}{r.answer.length > 150 ? '...' : ''}</p>}
                  </div>
                ))}
              </div>
              <div className="flex gap-1.5 pt-2 border-t border-slate-100">
                <input type="text" value={followUpInput} onChange={e => setFollowUpInput(e.target.value)}
                  onKeyDown={e => e.key === 'Enter' && handleFollowUp()}
                  placeholder={t("knowledge.ragtab.追问")}
                  className="flex-1 px-2.5 py-1.5 text-[11px] bg-slate-50 border border-slate-200 rounded-lg text-slate-700 outline-none focus:border-indigo-500" />
                <button onClick={handleFollowUp} disabled={isRetrieving || !followUpInput.trim()}
                  className="px-3 py-1.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-lg text-[10px] font-bold cursor-pointer disabled:opacity-50 flex items-center gap-1"><ChevronRight size={12} /></button>
              </div>
            </div>
          )}
        </div>

        {/* Right — Answer + Sources */}
        <div className="lg:col-span-7 space-y-4">
          <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-sm space-y-3">
            <div className="flex items-center justify-between border-b border-slate-100 pb-2">
              <h3 className="font-bold text-slate-800 text-xs flex items-center gap-1.5 text-emerald-600"><Bot size={14} />{t("knowledge.ragtab.ai_合规输出")}</h3>
              {overallConfidence !== null && (
                <div className={`flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-bold border ${c?.bg} ${c?.txt} ${c?.border}`}>
                  <Zap size={10} />{t("knowledge.ragtab.综合置信度")}: {pct}%
                </div>
              )}
            </div>
            {isRetrieving ? (
              <div className="py-8 text-center text-slate-400"><RefreshCw size={24} className="animate-spin mx-auto" /><p className="text-xs mt-2">{t("knowledge.ragtab.推理中")}</p></div>
            ) : llmOutput ? (
              <div className="bg-slate-50 border border-slate-150 p-4 rounded-xl text-slate-700 text-[11px] whitespace-pre-wrap leading-relaxed">{llmOutput}</div>
            ) : (
              <div className="py-8 text-center text-slate-400"><Bot size={24} className="mx-auto" /><p className="mt-1">{t("knowledge.ragtab.等待_rag_仿真")}</p></div>
            )}
          </div>

          {sources.length > 0 && (
            <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-xs space-y-3">
              <div className="flex items-center justify-between border-b border-slate-100 pb-2">
                <div className="flex items-center gap-1.5 font-bold text-slate-800 text-xs"><Layers size={12} className="text-emerald-500" />{t("knowledge.ragtab.检索来源")}</div>
                <span className="text-[9px] text-slate-400">{sources.length} {t("knowledge.ragtab.个来源")}</span>
              </div>
              <div className="space-y-2 max-h-72 overflow-y-auto">
                {sources.map((doc, idx) => {
                  const cs = confStyle(doc.score);
                  const spct = (doc.score * 100).toFixed(0);
                  const Icon = srcIcon(doc.type);
                  return (
                    <div key={idx} className="p-3 bg-slate-50 border border-slate-150 rounded-lg space-y-2">
                      <div className="flex items-center gap-2">
                        <span className={`p-1 rounded ${cs.bg} ${cs.txt}`}><Icon size={13} /></span>
                        <span className="text-[11px] font-bold text-slate-800 flex-1 truncate">{doc.title}</span>
                        {doc.page != null && <span className="text-[9px] text-slate-400 bg-slate-200 px-1.5 py-0.5 rounded">p.{doc.page}</span>}
                        <span className={`flex items-center gap-0.5 px-1.5 py-0.5 rounded-full text-[9px] font-bold border ${cs.bg} ${cs.txt} ${cs.border}`}>
                          <span className={`w-1.5 h-1.5 rounded-full ${cs.dot}`} />{spct}%
                        </span>
                      </div>
                      <div className="w-full h-1 bg-slate-200 rounded-full overflow-hidden">
                        <div className={`h-full rounded-full transition-all duration-500 ${cs.dot}`} style={{ width: `${spct}%` }} />
                      </div>
                      <p className="text-[10px] text-slate-500 leading-relaxed">{doc.snippet}</p>
                      {doc.score < 0.6 && (
                        <div className="flex items-center gap-1 text-[10px] text-red-600 font-bold"><AlertTriangle size={11} />{t("knowledge.ragtab.待人工复核")}</div>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
