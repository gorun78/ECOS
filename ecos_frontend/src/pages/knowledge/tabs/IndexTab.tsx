import React, { useState, useEffect } from 'react';
import { Binary, Settings, Cpu, Layers, CheckCircle2, FolderClosed, Database, Copy } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { knowledgeApi } from '../services/knowledgeApi';
import { CHUNK_SIZE_OPTIONS, VECTOR_MODELS } from '../typesAndConstants';

export default function IndexTab() {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const [embeddingModel, setEmbeddingModel] = useState('text-embedding-004');
  const [chunkSize, setChunkSize] = useState(512);
  const [overlap, setOverlap] = useState(50);
  const [isSyncingAll, setIsSyncingAll] = useState(false);
  const [syncLogs, setSyncLogs] = useState<string[]>([]);
  const [vectorChunks, setVectorChunks] = useState<any[]>([]);
  const [pgvectorSql, setPgvectorSql] = useState('');
  const [milvusCode, setMilvusCode] = useState('');
  const [persistenceTab, setPersistenceTab] = useState<'pgvector' | 'milvus'>('pgvector');

  useEffect(() => {
    knowledgeApi.fetchIndexStatus().then((data: any) => {
      setVectorChunks(data?.chunks || []);
      setPgvectorSql(data?.pgvectorSql || '');
      setMilvusCode(data?.milvusCode || '');
    }).catch(() => {});
  }, []);

  const handleSyncAll = async () => {
    setIsSyncingAll(true);
    setSyncLogs(['🔄 [0.0s] 启动 AIP 元数据提取与向量化索引计算管道...']);
    try {
      const result = await knowledgeApi.syncVectors({ embeddingModel, chunkSize, overlap }) as any;
      const logs = result?.logs || [];
      setSyncLogs([]);
      for (let i = 0; i < logs.length; i++) {
        await new Promise(r => setTimeout(r, 150));
        setSyncLogs(prev => [...prev, logs[i]]);
      }
      setVectorChunks(result?.chunks || []);
      setPgvectorSql(result?.pgvectorSql || '');
      setMilvusCode(result?.milvusCode || '');
    } catch (e: any) {
      setSyncLogs(prev => [...prev, `❌ ${e.message}`]);
    } finally {
      setIsSyncingAll(false);
    }
  };

  const handleCopy = (text: string) => { if (text) navigator.clipboard.writeText(text); };

  return (
    <div className="space-y-6">
      <div className={`border-b ${styles.cardBorder} pb-3 space-y-1`}>
        <h2 className={`text-sm font-black ${styles.cardText} flex items-center gap-2`}><span className="p-1.5 rounded-lg bg-indigo-50 text-indigo-600"><Binary size={16} /></span>{locale === 'zh' ? '联邦知识库切块向量化索引引擎' : 'Vector Index Engine'}</h2>
        <p className={`text-xs ${styles.cardTextMuted}`}>{locale === 'zh' ? '统一序列化切块，调用嵌入模型转换为特征向量，提供 PGVector / Milvus 持久化演示。' : 'Unified chunked serialization, embedding model vectorization, PGVector/Milvus persistence demo.'}</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
        <div className="lg:col-span-5 space-y-4">
          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs space-y-4`}>
            <h3 className={`font-extrabold ${styles.cardText} text-xs border-b ${styles.cardBorder} pb-2 flex items-center gap-1.5`}><Settings size={13} className={`${styles.cardTextMuted}`} />{locale === 'zh' ? '向量分片参数配置' : 'Chunking Config'}</h3>
            <div className="space-y-3">
              <div className="space-y-1">
                <label className={`block ${styles.cardTextMuted} font-bold text-[10px] uppercase`}>{locale === 'zh' ? '向量模型' : 'Embedding Model'}</label>
                <select value={embeddingModel} onChange={e => setEmbeddingModel(e.target.value)} className={`w-full px-2.5 py-1.5 border ${styles.cardBorder} rounded-lg text-xs font-mono ${styles.cardBg} ${styles.cardText}`}>
                  {VECTOR_MODELS.map(m => <option key={m.id} value={m.id}>{m.label} ({m.dim}d)</option>)}
                </select>
              </div>
              <div className="space-y-1">
                <div className="flex justify-between items-center"><label className={`block ${styles.cardTextMuted} font-bold text-[10px] uppercase`}>{locale === 'zh' ? '切块大小' : 'Chunk Size'}</label><span className={`font-mono text-[10px] font-bold ${styles.cardTextMuted} ${styles.sidebarBg} px-1.5 py-0.5 rounded`}>{chunkSize}</span></div>
                <input type="range" min={128} max={2048} step={64} value={chunkSize} onChange={e => setChunkSize(Number(e.target.value))} className="w-full h-1.5 bg-slate-150 rounded-lg appearance-none cursor-pointer accent-indigo-600" />
              </div>
              <div className="space-y-1">
                <div className="flex justify-between items-center"><label className={`block ${styles.cardTextMuted} font-bold text-[10px] uppercase`}>{locale === 'zh' ? '重叠度' : 'Overlap'}</label><span className={`font-mono text-[10px] font-bold ${styles.cardTextMuted} ${styles.sidebarBg} px-1.5 py-0.5 rounded`}>{overlap}</span></div>
                <input type="range" min={10} max={200} step={10} value={overlap} onChange={e => setOverlap(Number(e.target.value))} className="w-full h-1.5 bg-slate-150 rounded-lg appearance-none cursor-pointer accent-indigo-600" />
              </div>
            </div>
            <button onClick={handleSyncAll} disabled={isSyncingAll} className={`w-full py-2 bg-indigo-600 hover:bg-indigo-700 text-white font-bold rounded-lg transition-colors flex items-center justify-center gap-1.5 shadow-sm cursor-pointer ${isSyncingAll ? 'opacity-75 cursor-not-allowed' : ''}`}>
              {isSyncingAll ? <><span className="w-3.5 h-3.5 border-2 border-white border-t-transparent rounded-full animate-spin" />{locale === 'zh' ? '向量对齐中...' : 'Syncing...'}</> : <><Cpu size={13} />{locale === 'zh' ? '构建闭环元数据向量库' : 'Build Vector Index'}</>}
            </button>
          </div>
          <div className="bg-slate-950 rounded-xl p-4 border border-slate-800 space-y-2 shadow-inner">
            <div className="flex items-center justify-between border-b border-slate-900 pb-2">
              <span className={`text-[10px] font-extrabold ${styles.cardTextMuted} font-mono tracking-wider flex items-center gap-1.5`}><span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />SYSTEM PIPELINE MONITOR</span>
            </div>
            <div className="font-mono text-[9.5px] leading-relaxed h-52 overflow-y-auto space-y-1 scrollbar-thin scrollbar-thumb-slate-800">
              {syncLogs.length > 0 ? syncLogs.map((log, idx) => <div key={idx} className={log.includes('✅') ? 'text-emerald-400' : log.includes('❌') ? 'text-rose-400 font-bold' : 'text-slate-300'}>{log}</div>) : <div className={`${styles.cardTextMuted} italic`}>{locale === 'zh' ? '等待启动...' : 'Waiting...'}</div>}
            </div>
          </div>
        </div>

        <div className="lg:col-span-7 space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            <div className={`${styles.cardBg} border ${styles.cardBorder} p-3 rounded-xl shadow-xs flex items-center justify-between`}><div><span className={`${styles.cardTextMuted} font-mono text-[9px] block`}>CUMULATIVE CHUNKS</span><span className={`text-base font-black ${styles.cardText} font-mono`}>{vectorChunks.length}</span></div><span className="p-2 rounded-lg bg-blue-50 text-blue-600"><Layers size={14} /></span></div>
            <div className={`${styles.cardBg} border ${styles.cardBorder} p-3 rounded-xl shadow-xs flex items-center justify-between`}><div><span className={`${styles.cardTextMuted} font-mono text-[9px] block`}>VECTOR DIMENSION</span><span className={`text-base font-black ${styles.cardText} font-mono`}>768-Dim</span></div><span className="p-2 rounded-lg bg-indigo-50 text-indigo-600"><Binary size={14} /></span></div>
            <div className={`${styles.cardBg} border ${styles.cardBorder} p-3 rounded-xl shadow-xs flex items-center justify-between`}><div><span className={`${styles.cardTextMuted} font-mono text-[9px] block`}>INDEX INTEGRITY</span><span className="text-base font-black text-emerald-600 font-mono">100% HEALTH</span></div><span className="p-2 rounded-lg bg-emerald-50 text-emerald-600"><CheckCircle2 size={14} /></span></div>
          </div>

          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs space-y-3`}>
            <div className={`flex items-center justify-between border-b ${styles.cardBorder} pb-2`}>
              <h3 className={`font-bold ${styles.cardText} text-xs`}>{locale === 'zh' ? '分片浏览器' : 'Vector Chunk Browser'} ({vectorChunks.length})</h3>
            </div>
            <div className="space-y-2.5 max-h-60 overflow-y-auto">
              {vectorChunks.length > 0 ? vectorChunks.map((chunk, idx) => (
                <div key={idx} className={`border border-slate-150 rounded-lg p-2.5 ${styles.appBg} space-y-1.5`}>
                  <div className="flex items-center justify-between text-[10px]">
                    <div className="flex items-center gap-1.5"><span className="px-1.5 py-0.5 bg-indigo-50 text-indigo-700 rounded-md font-bold text-[9px]">{chunk.source || 'integration'}</span><span className={`font-bold ${styles.cardText} truncate max-w-[200px]`}>{chunk.title}</span></div>
                    <span className={`${styles.cardTextMuted} font-mono text-[9px]`}>#{idx+1} ({chunk.chunkSize} chars)</span>
                  </div>
                  <p className={`text-[10px] ${styles.cardTextMuted} leading-relaxed font-sans ${styles.cardBg} border ${styles.cardBorder} p-2 rounded-md`}>{chunk.text}</p>
                  {chunk.vectorPreview && <div className="text-[8.5px] text-indigo-600 bg-indigo-50/50 p-1 rounded font-mono truncate">{chunk.vectorPreview}</div>}
                </div>
              )) : <div className={`py-8 text-center ${styles.cardTextMuted} space-y-1`}><FolderClosed size={24} className="text-slate-300 mx-auto" /><p className="text-xs">{locale === 'zh' ? '暂无切块向量' : 'No chunks yet'}</p></div>}
            </div>
          </div>

          <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs space-y-3`}>
            <div className={`flex flex-col md:flex-row md:items-center justify-between border-b ${styles.cardBorder} pb-2 gap-2`}>
              <h3 className={`font-bold ${styles.cardText} text-xs flex items-center gap-1.5`}><Database size={13} className="text-indigo-600" />{locale === 'zh' ? '持久化演示' : 'Persistence Demo'}</h3>
              <div className={`flex ${styles.sidebarBg} p-0.5 rounded-lg border ${styles.cardBorder}/60 shrink-0`}>
                <button onClick={() => setPersistenceTab('pgvector')} className={`px-2 py-0.5 rounded-md text-[9px] font-bold transition-all cursor-pointer ${persistenceTab === 'pgvector' ? '${styles.cardBg} text-indigo-700 shadow-xs' : '${styles.cardTextMuted}'}`}>PGVector</button>
                <button onClick={() => setPersistenceTab('milvus')} className={`px-2 py-0.5 rounded-md text-[9px] font-bold transition-all cursor-pointer ${persistenceTab === 'milvus' ? '${styles.cardBg} text-indigo-700 shadow-xs' : '${styles.cardTextMuted}'}`}>Milvus</button>
              </div>
            </div>
            <div className="space-y-2">
              <div className={`flex justify-between items-center text-[10px] ${styles.cardTextMuted}`}>
                <span>{persistenceTab === 'pgvector' ? '🐘 PGVector SQL' : '⚡ Milvus Node.js'}</span>
                <button onClick={() => handleCopy(persistenceTab === 'pgvector' ? pgvectorSql : milvusCode)} className="text-indigo-600 hover:text-indigo-800 font-bold flex items-center gap-1 cursor-pointer"><Copy size={11} />{locale === 'zh' ? '复制' : 'Copy'}</button>
              </div>
              <div className="bg-slate-900 text-slate-300 rounded-xl p-3 h-48 overflow-y-auto font-mono text-[9.5px] leading-relaxed border border-slate-850">
                {(persistenceTab === 'pgvector' ? pgvectorSql : milvusCode) ? <pre className="whitespace-pre">{persistenceTab === 'pgvector' ? pgvectorSql : milvusCode}</pre> : <div className={`${styles.cardTextMuted} italic py-12 text-center`}>{locale === 'zh' ? '暂无代码' : 'No code yet'}</div>}
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* 全局配置（原工作台配置，融入向量索引） */}
      <div className={`${styles.cardBg} border ${styles.cardBorder} rounded-xl p-4 shadow-xs space-y-3`}>
        <h3 className={`font-extrabold ${styles.cardText} text-xs border-b ${styles.cardBorder} pb-2 flex items-center gap-1.5`}>
          <Settings size={13} className={`${styles.cardTextMuted}`} />
          {locale === 'zh' ? '全局配置 (Knowledge Settings)' : 'Global Settings'}
        </h3>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          <label className={`flex items-center gap-2 text-xs ${styles.cardText} font-medium`}>
            <input type="checkbox" className="accent-indigo-600" />
            <span>{locale === 'zh' ? '启用 Neo4j 图谱' : 'Neo4j Graph'}</span>
          </label>
          <label className={`flex items-center gap-2 text-xs ${styles.cardText} font-medium`}>
            <input type="checkbox" className="accent-indigo-600" defaultChecked />
            <span>{locale === 'zh' ? '自动同步' : 'Auto Sync'}</span>
          </label>
          <div className="space-y-1">
            <label className={`${styles.cardTextMuted} font-bold text-[10px] uppercase`}>
              {locale === 'zh' ? '最大检索数' : 'Max Retrieval Results'}
            </label>
            <input type="number" defaultValue={5} min={1} max={50}
              className={`w-full px-2 py-1 border ${styles.cardBorder} rounded text-xs font-mono`} />
          </div>
        </div>
      </div>
    </div>
  );
}
