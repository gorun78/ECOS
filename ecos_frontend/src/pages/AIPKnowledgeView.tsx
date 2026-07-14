/**
 * AIPKnowledgeView — 独立AIP Knowledge知识库页面
 * 从 ceos_new AIPWorkbench/KnowledgeView 中提取RAG检索子Tab，
 * 适配ECOS：lucide-react图标、Bearer认证、/api/v1/端点前缀。
 * @license Apache-2.0
 */

import React, { useState } from 'react';
import {
  Keyboard, Flame, Layers, Sparkles, Bot, RefreshCw,
  Search, Database, Shield, Network, Cpu
} from 'lucide-react';

// ═══════════════════ API helpers ═══════════════════

function authHeaders(): HeadersInit {
  const token = localStorage.getItem('token') || '';
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
}

function showToast(type: 'success' | 'info' | 'error', msg: string) {
  console.log(`[AIPKnowledge ${type}] ${msg}`);
}

// ═══════════════════ Component ═══════════════════

export default function AIPKnowledgeView() {
  const [queryInput, setQueryInput] = useState('帮我分析企业当前的数据资产健康状况，包括各业务域数据覆盖率、数据质量风险和合规性');
  const [isRetrieving, setIsRetrieving] = useState(false);
  const [retrievedDocs, setRetrievedDocs] = useState<Array<{ title: string; type: string; snippet: string; score: number }>>([]);
  const [ragPrompt, setRagPrompt] = useState<string>('');
  const [llmOutput, setLlmOutput] = useState<string>('');

  const handleRunRAG = async () => {
    if (!queryInput.trim()) return;
    setIsRetrieving(true);
    setRetrievedDocs([]);
    setRagPrompt('');
    setLlmOutput('');

    try {
      const response = await fetch('/api/v1/knowledge/query', {
        method: 'POST',
        headers: authHeaders(),
        body: JSON.stringify({ query: queryInput })
      });
      const data = await response.json();
      
      if (data.success || data.code === 0 || data.code === 200) {
        const payload = data.data || data;
        setRetrievedDocs((payload.groundedDocs || []).map((doc: any) => ({
          title: doc.title,
          type: doc.title?.includes('Security') ? '安全元数据' : doc.title?.includes('Ontology') ? '本体元数据' : '集成元数据',
          snippet: doc.snippet || 'N/A',
          score: doc.score
        })));
        setRagPrompt(payload.promptGrounded || '');
        setLlmOutput(payload.answer || '');
      } else {
        showToast('error', data.error || '元数据知识召回失败');
      }
    } catch (e) {
      showToast('error', '与大模型交互失败，请检查网络');
    } finally {
      setIsRetrieving(false);
    }
  };

  return (
    <div className="flex h-full select-none text-xs overflow-hidden bg-slate-50">

      {/* Main Panel — 全宽RAG检索页面 */}
      <div className="flex-1 p-6 overflow-y-auto h-full">

        {/* Header */}
        <div className="mb-6 border-b border-slate-200 pb-4 space-y-1">
          <div className="flex items-center gap-2">
            <span className="p-1.5 rounded-lg bg-indigo-500 text-white">
              <Search size={15} />
            </span>
            <h1 className="text-base font-black text-slate-800">AIP Knowledge · 知识检索</h1>
          </div>
          <p className="text-xs text-slate-500 max-w-2xl leading-relaxed">
            基于企业知识库的语义检索与RAG推理——输入自然语言查询，系统自动从数据资产、本体模型、安全策略中检索相关元数据，
            融合为强上下文提示词，驱动大模型生成精准答案。
          </p>
        </div>

        {/* Four-step flow */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          
          {/* Left column: Step 1 + Step 2 */}
          <div className="lg:col-span-5 space-y-4">
            
            {/* Step 1: Query Input */}
            <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-xs space-y-4">
              <div className="border-b border-slate-100 pb-2 flex items-center gap-2">
                <span className="p-1.5 rounded bg-blue-50 text-blue-600">
                  <Keyboard size={13} />
                </span>
                <h3 className="font-bold text-slate-800 text-xs">第1步: 输入自然语言查询</h3>
              </div>

              <div className="space-y-2">
                <textarea
                  value={queryInput}
                  onChange={e => setQueryInput(e.target.value)}
                  rows={3}
                  className="w-full px-3 py-2 border border-slate-200 rounded-lg text-xs font-sans leading-relaxed text-slate-700 focus:outline-none focus:border-blue-500"
                  placeholder="请输入企业数据资产、业务合规或安全权限相关的提问..."
                />
                
                <div className="space-y-1.5">
                  <span className="text-[9px] text-slate-400 font-extrabold uppercase block">推荐测试问题:</span>
                  <div className="flex flex-col gap-1">
                    {[
                      '帮我分析企业当前的数据资产健康状况，包括各业务域数据覆盖率、数据质量风险和合规性',
                      '查询销售域下所有客户主数据的血缘链路与下游影响范围',
                      '从安全合规角度，评估财务域敏感字段的脱敏策略是否完备'
                    ].map((p, idx) => (
                      <button
                        key={idx}
                        onClick={() => setQueryInput(p)}
                        className="text-left px-2 py-1 bg-slate-50 border border-slate-200 hover:bg-blue-50 hover:border-blue-200 rounded-lg text-[10px] text-slate-600 truncate cursor-pointer transition-all"
                      >
                        💡 {p}
                      </button>
                    ))}
                  </div>
                </div>
              </div>

              <button
                onClick={handleRunRAG}
                disabled={isRetrieving || !queryInput.trim()}
                className={`w-full py-2 bg-blue-600 hover:bg-blue-700 text-white font-bold rounded-lg transition-colors flex items-center justify-center gap-1.5 shadow-sm cursor-pointer ${
                  isRetrieving ? 'opacity-70 cursor-not-allowed' : ''
                }`}
              >
                {isRetrieving ? (
                  <>
                    <span className="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin" />
                    <span>向量检索与元数据对齐中...</span>
                  </>
                ) : (
                  <>
                    <Flame size={13} />
                    <span>开始检索并模拟 AI 推理</span>
                  </>
                )}
              </button>
            </div>

            {/* Step 2: Retrieved Documents */}
            {retrievedDocs.length > 0 && (
              <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-xs space-y-3">
                <div className="border-b border-slate-100 pb-2 flex items-center justify-between">
                  <div className="flex items-center gap-1.5 font-bold text-slate-800 text-xs">
                    <Layers size={12} className="text-emerald-500" />
                    <span>第2步: 向量相关度检索结果</span>
                  </div>
                  <span className="text-[9px] text-slate-400 font-mono">VECTOR MATCH</span>
                </div>

                <div className="space-y-2 max-h-56 overflow-y-auto">
                  {retrievedDocs.map((doc, idx) => (
                    <div key={idx} className="p-2 bg-slate-50 border border-slate-150 rounded-lg space-y-1">
                      <div className="flex items-center justify-between text-[10px]">
                        <span className="font-bold text-slate-800 flex items-center gap-1">
                          {doc.type === '安全元数据' ? <Shield size={10} className="text-rose-500" /> :
                           doc.type === '本体元数据' ? <Network size={10} className="text-indigo-500" /> :
                           <Database size={10} className="text-emerald-500" />}
                          {doc.title}
                        </span>
                        <span className="px-1.5 bg-emerald-50 text-emerald-600 text-[9px] font-bold rounded-md">
                          {(doc.score * 100).toFixed(0)}%
                        </span>
                      </div>
                      <p className="text-[9px] text-slate-500 leading-relaxed font-sans">{doc.snippet}</p>
                    </div>
                  ))}
                </div>
              </div>
            )}

          </div>

          {/* Right column: Step 3 + Step 4 */}
          <div className="lg:col-span-7 space-y-4">
            
            {/* Step 3: RAG Prompt */}
            {ragPrompt && (
              <div className="bg-white border border-slate-200 rounded-xl p-4 shadow-xs space-y-2">
                <h3 className="font-bold text-slate-800 text-xs flex items-center gap-1.5 text-indigo-600 border-b border-slate-100 pb-2">
                  <Sparkles size={13} />
                  <span>第3步: 融合生成的 AI 提示词 (RAG Prompt Context)</span>
                </h3>
                <div className="bg-slate-900 text-slate-300 rounded-xl p-3 h-32 overflow-y-auto font-mono text-[9px] leading-relaxed">
                  {ragPrompt}
                </div>
              </div>
            )}

            {/* Step 4: LLM Output */}
            <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-sm space-y-3">
              <h3 className="font-bold text-slate-800 text-xs flex items-center gap-1.5 text-emerald-600 border-b border-slate-100 pb-2">
                <Bot size={14} className="animate-bounce" />
                <span>第4步: AIP Copilot 合规输出</span>
              </h3>

              {isRetrieving ? (
                <div className="py-8 text-center text-slate-400 space-y-2">
                  <RefreshCw size={24} className="animate-spin text-slate-300 mx-auto" />
                  <p className="text-xs font-medium">模型正在依据知识库元数据进行合规校正推理，请稍候...</p>
                </div>
              ) : llmOutput ? (
                <div className="bg-slate-50 border border-slate-150 p-4 rounded-xl text-slate-700 text-[11px] font-sans leading-relaxed whitespace-pre-wrap">
                  {llmOutput}
                </div>
              ) : (
                <div className="py-8 text-center text-slate-400 space-y-1">
                  <Bot size={24} className="text-slate-300 mx-auto" />
                  <p>等待运行 RAG 仿真推理...</p>
                  <p className="text-[10px] text-slate-400">左侧输入查询并点击按钮，即可一键查看知识检索与AI推理结果</p>
                </div>
              )}
            </div>

          </div>

        </div>

      </div>
    </div>
  );
}
