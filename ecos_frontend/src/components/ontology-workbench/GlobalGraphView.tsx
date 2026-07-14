/**
 * GlobalGraphView — 全局知识图谱嵌入式视图
 */
import React, { useState, useEffect } from 'react';
import { Loader2, Network } from 'lucide-react';

interface KGNode {
  id: string; code?: string; name?: string; label?: string;
  entityCode?: string; entityType?: string;
  domain?: string; domainName?: string;
}
interface KGEdge {
  id?: string; source: string; target: string;
  relationshipType?: string; label?: string; code?: string; name?: string;
}
interface KGData { nodes: KGNode[]; edges: KGEdge[]; stats?: any; }

function layoutNodes(nodes: KGNode[]): Map<string, { x: number; y: number }> {
  const positions = new Map<string, { x: number; y: number }>();
  const domainGroups = new Map<string, KGNode[]>();
  for (const n of nodes) {
    const d = n.domain || 'default';
    if (!domainGroups.has(d)) domainGroups.set(d, []);
    domainGroups.get(d)!.push(n);
  }
  let groupX = 80;
  for (const [domain, groupNodes] of domainGroups) {
    const cols = Math.ceil(Math.sqrt(groupNodes.length));
    groupNodes.forEach((n, i) => {
      const col = i % cols;
      const row = Math.floor(i / cols);
      positions.set(n.id, { x: groupX + col * 180, y: 80 + row * 100 });
    });
    groupX += 400;
  }
  return positions;
}

export default function GlobalGraphView() {
  const [kgData, setKgData] = useState<KGData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    const t = localStorage.getItem('token') || '';
    const headers: Record<string,string> = {};
    if (t) headers['Authorization'] = 'Bearer ' + t;
    fetch('/api/v1/ecos/knowledge-graph', { headers })
      .then(r => { if (!r.ok) throw new Error(String(r.status)); return r.json(); })
      .then((data: any) => setKgData(data?.data ?? data))
      .catch((e: any) => setError(e.message))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <div className="flex-1 flex items-center justify-center bg-[#0f1117]"><Loader2 className="w-5 h-5 text-slate-500 animate-spin" /></div>;
  if (error) return <div className="flex-1 flex items-center justify-center bg-[#0f1117] text-[11px] text-red-400">{error}</div>;
  if (!kgData || !kgData.nodes?.length) return <div className="flex-1 flex items-center justify-center bg-[#0f1117]"><div className="text-center text-slate-600"><Network className="w-8 h-8 mx-auto mb-2 opacity-30" /><p className="text-[11px]">no data</p></div></div>;

  const positions = layoutNodes(kgData.nodes);
  const colors: Record<string, string> = { '采购域': '#f59e0b', '项目域': '#3b82f6', '资产域': '#10b981', '财务域': '#8b5cf6' };

  return (
    <div className="flex-1 bg-[#0f1117] overflow-auto relative">
      <svg width="100%" height="100%" style={{ minWidth: '1200px', minHeight: '600px' }}>
        <defs><marker id="arrow" markerWidth="8" markerHeight="6" refX="8" refY="3" orient="auto"><polygon points="0 0, 8 3, 0 6" fill="#64748b" /></marker></defs>
        {kgData.edges.map((e, i) => {
          const sp = positions.get(e.source), tp = positions.get(e.target);
          if (!sp || !tp) return null;
          return <g key={e.id || i}><line x1={sp.x+75} y1={sp.y+25} x2={tp.x+75} y2={tp.y+25} stroke="#475569" strokeWidth={1.5} markerEnd="url(#arrow)" />{e.relationshipType && <text x={(sp.x+tp.x)/2+75} y={(sp.y+tp.y)/2+20} fill="#64748b" fontSize="8" textAnchor="middle">{e.relationshipType}</text>}</g>;
        })}
        {(() => { const seen=new Set<string>(); return kgData.nodes.map(n=>{const d=n.domain||'default';if(seen.has(d))return null;seen.add(d);const p=positions.get(n.id);if(!p)return null;return <text key={'dl-'+d} x={p.x+75} y={p.y-15} fill={colors[d]||'#94a3b8'} fontSize="10" fontWeight="600" textAnchor="middle">{n.domainName||d}</text>;});})()}
        {kgData.nodes.map(n=>{const p=positions.get(n.id);if(!p)return null;const c=colors[n.domain||'default']||'#64748b';return <g key={n.id}><rect x={p.x} y={p.y} width={150} height={50} rx={8} fill="#1e293b" stroke={c} strokeWidth={1.5} /><text x={p.x+75} y={p.y+18} textAnchor="middle" fill="#e2e8f0" fontSize="11" fontWeight="600">{n.code||n.entityCode||n.id}</text><text x={p.x+75} y={p.y+36} textAnchor="middle" fill="#94a3b8" fontSize="9">{n.name||n.label||''}</text></g>;})}
      </svg>
      <div className="absolute bottom-3 right-3 flex gap-2"><span className="text-[10px] text-slate-600 bg-[#1e293b]/80 px-2 py-1 rounded font-mono">{kgData.nodes.length}N</span><span className="text-[10px] text-slate-600 bg-[#1e293b]/80 px-2 py-1 rounded font-mono">{kgData.edges.length}E</span></div>
    </div>
  );
}
