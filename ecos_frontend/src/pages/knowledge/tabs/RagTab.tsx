import React, { useState } from 'react';
import { Keyboard, Flame, Layers, RefreshCw, Bot, Sparkles } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { knowledgeApi } from '../services/knowledgeApi';

export default function RagTab() {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const [queryInput, setQueryInput] = useState(locale === 'zh' ? '查询欧盟GDPR合规隔离下，飞行员张建国的执勤及SSN脱敏状态' : 'Query EU GDPR compliance isolation and pilot SSN masking status');
  const [isRetrieving, setIsRetrieving] = useState(false);
  const [retrievedDocs, setRetrievedDocs] = useState<Array<{ title: string; type: string; snippet: string; score: number }>>([]);
  const [ragPrompt, setRagPrompt] = useState('');
  const [llmOutput, setLlmOutput] = useState('');

  const handleRunRAG = async () => {
    if (!queryInput.trim()) return;
    setIsRetrieving(true);
    setRetrievedDocs([]);
    setRagPrompt('');
    setLlmOutput('');
    try {
      const result = await knowledgeApi.runRAGQuery({ query: queryInput, topK: 5 });
      if (result.answer) setLlmOutput(result.answer);
      if (result.sources) setRetrievedDocs(result.sources);

      const legacyResult = await knowledgeApi.runKnowledgeQuery(queryInput) as any;
      if (!result.answer && legacyResult?.llmOutput) setLlmOutput(legacyResult.llmOutput);
      if (!result.sources?.length && legacyResult?.groundedDocs) {
        setRetrievedDocs((legacyResult.groundedDocs || []).map((doc: any) => ({
          title: doc.title || doc.name || 'Unknown',
          type: doc.title?.includes('Security') ? '安全' : doc.title?.includes('Ontology') ? '本体' : '集成',
          snippet: doc.snippet || doc.description || 'N/A',
          score: doc.score ?? 0,
        })));
      }
      if (!result.answer && legacyResult?.promptGrounded) setRagPrompt(legacyResult.promptGrounded);
    } catch {
      setLlmOutput(locale === 'zh' ? '检索失败，请检查后端连接' : 'Retrieval failed, check backend connection');
    } finally {
      setIsRetrieving(false);
    }
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-3 space-y-1">
        <h2 className="text-sm font-black text-slate-800">{locale === 'zh' ? '联邦知识库检索与大模型推理仿真沙箱' : 'Semantic Grounding Sandbox'}</h2>
        <p className="text-xs text-slate-500">{locale === 'zh' ? '模拟 AIP Copilot 收到用户自然语言提问时，如何在三个数据库中进行检索并生成答案。' : 'Simulate how AIP Copilot retrieves metadata and generates grounded answers.'}</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        <div className="lg:col-span-5 space-y-4">
          <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-xs space-y-4">
            <div className="border-b border-slate-100 pb-2 flex items-center gap-2">
              <span className="p-1.5 rounded bg-blue-50 text-blue-600"><Keyboard size={13} /></span>
              <h3 className="font-bold text-slate-800 text-xs">{locale === 'zh' ? '输入自然语言查询' : 'User Query'}</h3>
            </div>
            <textarea value={queryInput} onChange={e => setQueryInput(e.target.value)} rows={3} className="w-full px-3 py-2 border border-slate-200 rounded-lg text-xs font-sans leading-relaxed text-slate-700 focus:outline-hidden focus:border-blue-500" />
            <div className="space-y-1.5">
              <span className="text-[9px] text-slate-400 font-extrabold uppercase block">{locale === 'zh' ? '推荐测试问题' : 'Test Prompts'}</span>
              {[
                locale === 'zh' ? '查询欧盟GDPR合规隔离下，飞行员张建国的执勤及SSN脱敏状态' : 'Query EU GDPR compliance and pilot SSN masking',
                locale === 'zh' ? '帮我评估客票营收明细表 ds_ticket_sales 被高危扫描的风险' : 'Assess risk of ds_ticket_sales being scanned',
                locale === 'zh' ? '查询 UA102 航班在 Doris 物理库中对应的上游链路与时效影响' : 'Query UA102 upstream lineage and SLA impact in Doris',
              ].map((p, idx) => (
                <button key={idx} onClick={() => setQueryInput(p)} className="text-left w-full px-2 py-1 bg-slate-50 border border-slate-200 hover:bg-blue-50 hover:border-blue-200 rounded-lg text-[10px] text-slate-600 truncate cursor-pointer transition-all">💡 {p}</button>
              ))}
            </div>
            <button onClick={handleRunRAG} disabled={isRetrieving || !queryInput.trim()} className={`w-full py-2 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-lg transition-colors flex items-center justify-center gap-1.5 shadow-sm cursor-pointer ${isRetrieving ? 'opacity-70 cursor-not-allowed' : ''}`}>
              {isRetrieving ? <><span className="w-3.5 h-3.5 border-2 border-slate-100 border-t-transparent rounded-full animate-spin" />{locale === 'zh' ? '检索中...' : 'Retrieving...'}</> : <><Flame size={13} />{locale === 'zh' ? '开始检索并模拟推理' : 'Search & Reason'}</>}
            </button>
          </div>

          {retrievedDocs.length > 0 && (
            <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-xs space-y-3">
              <div className="border-b border-slate-100 pb-2 flex items-center justify-between">
                <div className="flex items-center gap-1.5 font-bold text-slate-800 text-xs"><Layers size={12} className="text-emerald-500" />{locale === 'zh' ? '检索结果' : 'Grounding Results'}</div>
              </div>
              <div className="space-y-2 max-h-56 overflow-y-auto">
                {retrievedDocs.map((doc, idx) => (
                  <div key={idx} className="p-2 bg-slate-50 border border-slate-150 rounded-lg space-y-1">
                    <div className="flex items-center justify-between text-[10px]"><span className="font-bold text-slate-800">{doc.title}</span><span className="px-1.5 bg-emerald-50 text-emerald-600 text-[9px] font-bold rounded-md">{(doc.score * 100).toFixed(0)}%</span></div>
                    <p className="text-[9px] text-slate-500 leading-relaxed font-sans">{doc.snippet}</p>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        <div className="lg:col-span-7 space-y-4">
          {ragPrompt && (
            <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-xs space-y-2">
              <h3 className="font-bold text-slate-800 text-xs flex items-center gap-1.5 text-indigo-600 border-b border-slate-100 pb-2"><Sparkles size={13} />{locale === 'zh' ? '融合提示词' : 'RAG Prompt Context'}</h3>
              <div className="bg-slate-900 text-slate-300 rounded-xl p-3 h-32 overflow-y-auto font-mono text-[9px] leading-relaxed">{ragPrompt}</div>
            </div>
          )}
          <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-sm space-y-3">
            <h3 className="font-bold text-slate-800 text-xs flex items-center gap-1.5 text-emerald-600 border-b border-slate-100 pb-2"><Bot size={14} className="animate-bounce" />{locale === 'zh' ? 'AI 合规输出' : 'AI Output'}</h3>
            {isRetrieving ? (
              <div className="py-8 text-center text-slate-400 space-y-2"><RefreshCw size={24} className="animate-spin text-slate-300 mx-auto" /><p className="text-xs font-medium">{locale === 'zh' ? '推理中...' : 'Reasoning...'}</p></div>
            ) : llmOutput ? (
              <div className="bg-slate-50 border border-slate-150 p-4 rounded-xl text-slate-700 text-[11px] font-sans leading-relaxed whitespace-pre-wrap">{llmOutput}</div>
            ) : (
              <div className="py-8 text-center text-slate-400 space-y-1"><Bot size={24} className="text-slate-300 mx-auto" /><p>{locale === 'zh' ? '等待 RAG 仿真...' : 'Waiting for RAG simulation...'}</p></div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
