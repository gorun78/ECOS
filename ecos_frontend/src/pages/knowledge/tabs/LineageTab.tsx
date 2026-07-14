import React, { useState, useEffect } from 'react';
import { Code, Network, FileInput, ShieldCheck, ShieldAlert } from 'lucide-react';
import { useLanguage } from '../../../components/LanguageContext';
import { useTheme } from '../../../components/ThemeContext';
import { knowledgeApi } from '../services/knowledgeApi';

const DEFAULT_PAYLOAD_OPENLINEAGE = JSON.stringify({
  "eventType": "COMPLETE",
  "eventTime": "2026-07-04T12:00:00Z",
  "producer": "https://github.com/OpenLineage/OpenLineage",
  "job": { "namespace": "ds_scheduler", "name": "spark_clean_flight_acars_job" },
  "inputs": [{ "namespace": "postgresql_raw_sched", "name": "flights_raw" }],
  "outputs": [{ "namespace": "doris_production_olap", "name": "ds_flights_clean" }]
}, null, 2);

const DEFAULT_PAYLOAD_ATLAS = JSON.stringify([{
  "typeName": "spark_process",
  "attributes": {
    "name": "spark_process_pilots_biography_sync",
    "qualifiedName": "spark_process_pilots_biography_sync@cluster",
    "inputs": [{ "typeName": "hive_table", "uniqueAttributes": { "qualifiedName": "postgresql_raw_sched.pilots_raw@cluster" } }],
    "outputs": [{ "typeName": "hive_table", "uniqueAttributes": { "qualifiedName": "doris_production_olap.ds_pilots_biography@cluster" } }]
  }
}], null, 2);

export default function LineageTab() {
  const { locale } = useLanguage();
  const { styles } = useTheme();
  const [parserFormat, setParserFormat] = useState<'openlineage' | 'atlas'>('openlineage');
  const [rawPayload, setRawPayload] = useState(DEFAULT_PAYLOAD_OPENLINEAGE);
  const [isParsing, setIsParsing] = useState(false);
  const [lineageNodes, setLineageNodes] = useState<any[]>([]);
  const [lineageLinks, setLineageLinks] = useState<any[]>([]);
  const [selectedStartNode, setSelectedStartNode] = useState('postgresql_raw_sched.flights_raw');
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [impactResult, setImpactResult] = useState<any>(null);

  const handleFormatSwitch = (format: 'openlineage' | 'atlas') => {
    setParserFormat(format);
    setRawPayload(format === 'openlineage' ? DEFAULT_PAYLOAD_OPENLINEAGE : DEFAULT_PAYLOAD_ATLAS);
  };

  const handleParseLineage = async () => {
    setIsParsing(true);
    try {
      let parsedObj;
      try { parsedObj = JSON.parse(rawPayload); } catch { alert(locale === 'zh' ? 'JSON格式错误' : 'Invalid JSON'); setIsParsing(false); return; }
      const result = await knowledgeApi.parseLineage(parserFormat, parsedObj) as any;
      if (result?.lineage) {
        setLineageNodes(result.lineage.nodes || []);
        setLineageLinks(result.lineage.links || []);
      }
    } catch { /* fallback: keep current state */ }
    setIsParsing(false);
  };

  const handleImpactAnalysis = async (nodeId: string) => {
    setIsAnalyzing(true);
    try {
      const result = await knowledgeApi.fetchLineageImpact(nodeId);
      setImpactResult(result);
    } catch { setImpactResult(null); }
    setIsAnalyzing(false);
  };

  useEffect(() => { if (selectedStartNode) handleImpactAnalysis(selectedStartNode); }, [selectedStartNode]);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between border-b border-slate-200 pb-3">
        <div className="space-y-1">
          <h2 className="text-sm font-black text-slate-800">{locale === 'zh' ? '元数据血缘解析器与下游影响分析' : 'Lineage Parser & Impact Analysis'}</h2>
          <p className="text-xs text-slate-500">{locale === 'zh' ? '支持 OpenLineage 及 Apache Atlas 统一血缘解析，智能评估级联影响。' : 'Parse OpenLineage/Atlas lineage, assess cascade impact.'}</p>
        </div>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-2 gap-6">
        <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-xs flex flex-col space-y-4">
          <div className="flex items-center justify-between border-b border-slate-100 pb-2.5">
            <h3 className="font-extrabold text-slate-800 text-xs flex items-center gap-1.5"><Code size={13} className="text-indigo-600" /><span>{locale === 'zh' ? '血缘解析器' : 'Lineage Parser'}</span></h3>
            <div className="flex bg-slate-100 p-0.5 rounded-lg border border-slate-200 text-[10px]">
              <button onClick={() => handleFormatSwitch('openlineage')} className={`px-2.5 py-1 rounded-md font-bold transition-all cursor-pointer ${parserFormat === 'openlineage' ? 'bg-white text-slate-900 shadow-xs' : 'text-slate-500'}`}>OpenLineage</button>
              <button onClick={() => handleFormatSwitch('atlas')} className={`px-2.5 py-1 rounded-md font-bold transition-all cursor-pointer ${parserFormat === 'atlas' ? 'bg-white text-slate-900 shadow-xs' : 'text-slate-500'}`}>Apache Atlas</button>
            </div>
          </div>
          <textarea value={rawPayload} onChange={e => setRawPayload(e.target.value)} className="flex-1 min-h-[160px] p-3 font-mono text-[10px] bg-slate-900 text-slate-200 rounded-lg border border-slate-800 focus:outline-none focus:ring-1 focus:ring-indigo-500 leading-relaxed resize-none" />
          <button onClick={handleParseLineage} disabled={isParsing || !rawPayload.trim()} className="w-full py-2 bg-indigo-600 hover:bg-indigo-700 text-white font-extrabold rounded-lg shadow-sm transition-all flex items-center justify-center gap-1.5 cursor-pointer text-xs disabled:opacity-60">
            {isParsing ? <><span className="w-3 h-3 border-2 border-white border-t-transparent rounded-full animate-spin" /><span>{locale === 'zh' ? '解析中...' : 'Parsing...'}</span></> : <><FileInput size={12} /><span>{locale === 'zh' ? '解析并合并血缘 DAG' : 'Parse Lineage'}</span></>}
          </button>
        </div>

        <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-xs flex flex-col space-y-4">
          <h3 className="font-extrabold text-slate-800 text-xs border-b border-slate-100 pb-2.5 flex items-center gap-1.5"><Network size={13} className="text-emerald-600" /><span>{locale === 'zh' ? '多维级联血缘拓扑 DAG' : 'Live Lineage Map'} ({lineageNodes.length} nodes)</span></h3>
          <div className="flex-1 bg-slate-50 border border-slate-200/60 rounded-xl p-4 overflow-y-auto max-h-[300px] space-y-3">
            {['physical_table', 'etl_job', 'olap_table', 'ontology_object', 'dashboard'].map((type, i) => {
              const nodes = lineageNodes.filter(n => n.type === type);
              const labels = [locale === 'zh' ? '物理数据源' : 'Raw Sources', locale === 'zh' ? 'ETL管道' : 'ETL Pipelines', locale === 'zh' ? 'OLAP宽表' : 'OLAP Tables', locale === 'zh' ? '本体对象' : 'Ontology Objects', locale === 'zh' ? '下游报表' : 'Reports'];
              const colors = ['bg-white border-slate-200', 'bg-blue-50/50 border-blue-200', 'bg-emerald-50/50 border-emerald-200', 'bg-indigo-50/50 border-indigo-200', 'bg-rose-50/50 border-rose-200'];
              if (nodes.length === 0) return null;
              return (
                <div key={type} className="space-y-1.5">
                  <div className="text-[8px] font-extrabold text-slate-400 tracking-wider uppercase">{i+1}. {labels[i]}</div>
                  <div className="flex flex-wrap gap-2">
                    {nodes.map(node => (
                      <div key={node.id} className={`p-2 ${colors[i]} rounded-lg flex items-center gap-2 hover:border-slate-400 transition-all cursor-pointer shadow-xs`}>
                        <span className="w-2 h-2 rounded-full bg-slate-400" />
                        <div><div className="font-mono text-[9px] font-bold text-slate-700">{node.id}</div><div className="text-[8px] text-slate-400 font-sans">{node.label}</div></div>
                      </div>
                    ))}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </div>

      <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-xs space-y-4">
        <div className="flex flex-col md:flex-row md:items-center justify-between border-b border-slate-100 pb-3 gap-3">
          <h3 className="font-extrabold text-slate-800 text-xs flex items-center gap-1.5"><ShieldCheck size={13} className="text-rose-600" /><span>{locale === 'zh' ? '级联影响分析' : 'Impact Analysis'}</span></h3>
          <div className="flex items-center gap-2">
            <span className="text-[10px] font-bold text-slate-500">{locale === 'zh' ? '选择分析对象:' : 'Select node:'}</span>
            <select value={selectedStartNode} onChange={e => setSelectedStartNode(e.target.value)} className="px-3 py-1.5 border border-slate-200 rounded-lg text-xs font-mono bg-white text-slate-800 font-bold cursor-pointer">
              {lineageNodes.filter(n => n.type === 'physical_table' || n.type === 'olap_table').map(n => (
                <option key={n.id} value={n.id}>{n.id}</option>
              ))}
            </select>
          </div>
        </div>
        {isAnalyzing ? (
          <div className="py-12 flex flex-col items-center justify-center space-y-2"><span className="w-6 h-6 border-2 border-indigo-600 border-t-transparent rounded-full animate-spin" /><span className="text-slate-500 font-bold text-[10px]">{locale === 'zh' ? '推导中...' : 'Analyzing...'}</span></div>
        ) : impactResult ? (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <div className="bg-slate-50 border border-slate-200 rounded-xl p-4 flex flex-col items-center justify-center text-center space-y-3 relative overflow-hidden">
              <div className={`absolute top-0 left-0 right-0 h-1.5 ${impactResult.severity === 'CRITICAL' ? 'bg-rose-500 animate-pulse' : impactResult.severity === 'HIGH' ? 'bg-amber-500' : 'bg-emerald-500'}`} />
              <span className="text-[9px] font-extrabold text-slate-400 uppercase tracking-widest font-mono">{locale === 'zh' ? '风险评分' : 'RISK SCORE'}</span>
              <div className={`text-4xl font-black ${impactResult.severity === 'CRITICAL' ? 'text-rose-600' : impactResult.severity === 'HIGH' ? 'text-amber-600' : 'text-emerald-600'}`}>{impactResult.totalRisk}</div>
              <div className={`text-[10px] font-extrabold uppercase px-2 py-0.5 rounded-full inline-block ${impactResult.severity === 'CRITICAL' ? 'bg-rose-100 text-rose-700' : impactResult.severity === 'HIGH' ? 'bg-amber-100 text-amber-700' : 'bg-emerald-100 text-emerald-700'}`}>{impactResult.severity}</div>
            </div>
            <div className="lg:col-span-2 bg-white border border-slate-200 rounded-xl p-4 space-y-3">
              <h4 className="font-extrabold text-slate-700 text-[10px] uppercase tracking-wider border-b border-slate-100 pb-2">{locale === 'zh' ? '影响组件' : 'Impacted Nodes'} ({impactResult.impactedNodes?.length || 0})</h4>
              {impactResult.impactedNodes?.length > 0 ? (
                <div className="space-y-2.5 max-h-48 overflow-y-auto">
                  {impactResult.impactedNodes.map((node: any, i: number) => (
                    <div key={i} className="flex items-center justify-between p-2 rounded-lg bg-slate-50 border border-slate-150 text-[10px]">
                      <div className="space-y-0.5">
                        <div className="flex items-center gap-1.5">
                          <span className="font-bold text-slate-800 font-mono">{node.id}</span>
                          <span className={`px-1.5 py-0.2 rounded-xs font-bold text-[8px] uppercase bg-blue-100 text-blue-700`}>{node.type}</span>
                        </div>
                        <p className="text-[9px] text-slate-400 font-sans">{node.path?.join(' ➔ ')}</p>
                      </div>
                      <div className="text-right shrink-0">
                        <div className="font-mono font-bold text-slate-600">{node.hopCount} Hops</div>
                        <div className={`font-mono font-extrabold text-[11px] ${node.riskScore > 80 ? 'text-rose-600' : 'text-emerald-600'}`}>{node.riskScore}</div>
                      </div>
                    </div>
                  ))}
                </div>
              ) : <div className="py-6 text-center text-slate-400 text-[10px]"><ShieldAlert size={18} className="mx-auto text-slate-300 mb-1" />{locale === 'zh' ? '无下游依赖' : 'No downstream dependencies'}</div>}
            </div>
          </div>
        ) : <div className="py-6 text-center text-slate-400 text-[10px]">{locale === 'zh' ? '请选择分析对象' : 'Select a node to analyze'}</div>}
      </div>
    </div>
  );
}
